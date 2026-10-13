#!/usr/bin/env python3
"""255-client staging gate: 15 TB, 225 helpers, 15 independent portal users.

Reads test credentials only via environment-variable names in a local manifest.
Never creates users, changes business data, or prints credentials/response bodies.
Portal login creates temporary sessions, which are logged out in finally.
"""
from __future__ import annotations

import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
import json
import math
import os
from pathlib import Path
import statistics
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

from sync_wait_loadtest import _portal_session, _logout_portal_session


@dataclass
class Client:
    role: str
    headers: dict
    after: int = 0


def validate_manifest(rows: object) -> list[dict]:
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError('MANIFEST_LIST_REQUIRED')
    if Counter(row.get('role') for row in rows) != {'TB': 15, 'HELPER': 225, 'PORTAL': 15}:
        raise ValueError('REQUIRE_15_TB_225_HELPER_15_PORTAL')
    device_secrets, portal_logins = set(), set()
    for row in rows:
        if row['role'] == 'PORTAL':
            login = str(row.get('login', '')).strip().casefold()
            if not login or login in portal_logins:
                raise ValueError('PORTAL_LOGINS_MUST_BE_DISTINCT')
            portal_logins.add(login)
            env = row.get('password_env')
        else:
            env = row.get('token_env')
        if not isinstance(env, str) or not os.environ.get(env):
            raise ValueError('CREDENTIAL_ENV_MISSING')
        if row['role'] != 'PORTAL':
            token = os.environ[env]
            if token in device_secrets:
                raise ValueError('DEVICE_TOKENS_MUST_BE_DISTINCT')
            device_secrets.add(token)
    return rows


def validate_payload(payload: object, after: int) -> int:
    if not isinstance(payload, dict): raise ValueError('RESPONSE_NOT_OBJECT')
    head, next_after = payload.get('head'), payload.get('next_after')
    changes, caught_up = payload.get('changes'), payload.get('caught_up')
    if (type(head) is not int or type(next_after) is not int or head < 0
            or next_after < after or next_after > max(after, head)
            or type(caught_up) is not bool or not isinstance(changes, list) or len(changes) > 50):
        raise ValueError('RESPONSE_CONTRACT_INVALID')
    previous = after
    for change in changes:
        change_id = change.get('change_id') if isinstance(change, dict) else None
        if type(change_id) is not int or not previous < change_id <= head:
            raise ValueError('CHANGE_CURSOR_INVALID')
        previous = change_id
    if next_after < previous or (caught_up and next_after != max(after, head)):
        raise ValueError('NEXT_CURSOR_INVALID')
    if not caught_up and (not changes or next_after != previous):
        raise ValueError('PAGINATION_INVALID')
    return next_after


def probe(base: str, client: Client, wait: float, timeout: float, barrier=None) -> dict:
    if barrier is not None: barrier.wait(timeout=timeout)
    start = time.monotonic()
    status, error, caught_up = 0, '', False
    query = urllib.parse.urlencode(dict(after=client.after, limit=50,
        entity_types='BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE', wait_seconds=wait))
    try:
        request = urllib.request.Request(base + '/api/v1/sync/changes/wait?' + query, headers=client.headers)
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            payload = json.loads(response.read())
            if status != 200: raise ValueError('HTTP_STATUS')
            client.after = validate_payload(payload, client.after)
            caught_up = payload['caught_up']
    except urllib.error.HTTPError as exc:
        status, error = exc.code, 'HTTP_ERROR'
    except Exception as exc:
        error = type(exc).__name__  # no URL, cookies, body or tokens in reports
    return dict(role=client.role, status=status, error=error, caught_up=caught_up,
                seconds=time.monotonic() - start)


def summarize(results: list[dict], wait: float, max_overhead: float) -> dict:
    times = sorted(row['seconds'] for row in results)
    failures = sum(row['status'] != 200 or bool(row['error']) for row in results)
    p95 = times[math.ceil(len(times) * .95) - 1]
    return dict(clients=len(results), failures=failures, p50_s=statistics.median(times),
                p95_s=p95, max_s=max(times), passed=failures == 0 and p95 <= wait + max_overhead,
                errors=dict(Counter(str(row['status']) + ':' + row['error'] for row in results if row['error'])))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--confirm-staging', required=True, help='Repeat exact isolated test base URL')
    parser.add_argument('--clients-file', type=Path, required=True)
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--wait-seconds', type=float, default=10)
    parser.add_argument('--timeout', type=float, default=40)
    parser.add_argument('--max-overhead', type=float, default=5)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    base = args.base_url.rstrip('/')
    url = urllib.parse.urlsplit(base)
    if (args.confirm_staging.rstrip('/') != base or url.scheme not in ('http', 'https')
            or not url.hostname or url.username or url.password or url.query or url.fragment
            or url.path not in ('', '/')):
        parser.error('An isolated origin and matching --confirm-staging are required')
    if (not 1 <= args.rounds <= 20 or not 0 < args.wait_seconds <= 30
            or not args.wait_seconds < args.timeout <= 120
            or not 0 < args.max_overhead <= 60):
        parser.error('Invalid rounds/wait/timeout/overhead')
    sessions, reports, clients = [], [], []
    exit_code = 1
    try:
        rows = validate_manifest(json.loads(args.clients_file.read_text()))
        for row in rows:
            if row['role'] == 'PORTAL':
                session = _portal_session(base, row['login'], os.environ[row['password_env']], min(args.timeout, 15))
                sessions.append(session)
                headers = session.headers
            else:
                headers = {'Authorization': 'Bearer ' + os.environ[row['token_env']], 'Accept': 'application/json'}
            clients.append(Client(row['role'], headers))
        # Advance each independently to a current head before timing long polls.
        for client in clients:
            for _ in range(10000):
                row = probe(base, client, 0, args.timeout)
                if row['error'] or row['status'] != 200: raise RuntimeError('PREFLIGHT_FAILED')
                if row['caught_up']: break
            else: raise RuntimeError('PREFLIGHT_DID_NOT_CATCH_UP')
        with ThreadPoolExecutor(max_workers=255) as pool:
            for number in range(1, args.rounds + 1):
                barrier = threading.Barrier(255)
                futures = [pool.submit(probe, base, client, args.wait_seconds, args.timeout, barrier) for client in clients]
                results = [future.result() for future in futures]
                report = dict(round=number, **summarize(results, args.wait_seconds, args.max_overhead))
                report['roles'] = {role: summarize([r for r in results if r['role'] == role], args.wait_seconds, args.max_overhead)
                                   for role in ('TB', 'HELPER', 'PORTAL')}
                reports.append(report)
                print(json.dumps(report), flush=True)
        exit_code = 0 if all(report['passed'] for report in reports) else 1
    except Exception as exc:
        print(json.dumps({'error': type(exc).__name__, 'passed': False}), flush=True)
    finally:
        cleaned = sum(_logout_portal_session(base, session, min(args.timeout, 15)) for session in sessions)
        if cleaned != len(sessions): exit_code = 1
        summary = dict(passed=exit_code == 0, rounds=reports,
                       portal_sessions_created=len(sessions), portal_sessions_cleaned=cleaned,
                       scope='sync-longpoll-only; no business writes or image workload')
        print(json.dumps(summary), flush=True)
        if args.output: args.output.write_text(json.dumps(summary, indent=2) + '\n')
    return exit_code


if __name__ == '__main__': raise SystemExit(main())
