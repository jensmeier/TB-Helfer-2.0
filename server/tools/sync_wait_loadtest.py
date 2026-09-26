#!/usr/bin/env python3
"""Dependency-free load probe for /api/v1/sync/changes/wait.

The probe is read-only and intended for staging only.

Authentication modes:
- device: Bearer token from TB_DEVICE_TOKEN/--token (mobile contract)
- portal: normal portal session using TB_PORTAL_PASSWORD plus --portal-login

For portal auth, unique session mode creates one independent portal session per
simulated client before timing the long-poll requests. This avoids an artificial
row-lock bottleneck on one shared portal_sessions.last_seen_at row.

Secrets and session cookies are never printed.
"""
from __future__ import annotations

import argparse
import getpass
import http.cookiejar
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass


@dataclass(frozen=True)
class PortalSession:
    headers: dict[str, str]
    csrf: str


def _safe_error(payload: str) -> str:
    raw = payload.strip()
    if not raw:
        return "empty_error_body"
    try:
        parsed = json.loads(raw)
    except Exception:
        return " ".join(raw.split())[:240]
    if isinstance(parsed, dict):
        detail = parsed.get("detail")
        if isinstance(detail, str) and detail.strip():
            return detail.strip()[:240]
    return "json_error_without_detail"


def _portal_session(
    base_url: str,
    login: str,
    password: str,
    timeout: float,
) -> PortalSession:
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    body = json.dumps({"login": login, "password": password}).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/v1/portal/login",
        data=body,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with opener.open(request, timeout=timeout) as response:
            response.read()
            if response.status != 200:
                raise RuntimeError(f"portal login HTTP {response.status}")
    except urllib.error.HTTPError as exc:
        detail = _safe_error(exc.read().decode("utf-8", "replace"))
        raise RuntimeError(f"portal login HTTP {exc.code} detail={detail}") from exc

    cookie_values = {cookie.name: cookie.value for cookie in jar}
    session_cookie = cookie_values.get("tb_portal_session", "")
    csrf = cookie_values.get("tb_portal_csrf", "")
    if not session_cookie:
        raise RuntimeError("portal login returned no tb_portal_session cookie")
    if not csrf:
        raise RuntimeError("portal login returned no tb_portal_csrf cookie")

    cookie_header = "; ".join(
        f"{name}={value}" for name, value in cookie_values.items()
    )
    return PortalSession(
        headers={"Cookie": cookie_header, "Accept": "application/json"},
        csrf=csrf,
    )


def _logout_portal_session(
    base_url: str,
    session: PortalSession,
    timeout: float,
) -> bool:
    headers = dict(session.headers)
    headers["X-CSRF-Token"] = session.csrf
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/v1/portal/logout",
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response.read()
            return response.status == 204
    except Exception:
        return False


def _prepare_portal_sessions(
    base_url: str,
    login: str,
    password: str,
    count: int,
    timeout: float,
    mode: str,
) -> list[PortalSession]:
    target = 1 if mode == "shared" else count
    print(f"preparing_portal_sessions={target} mode={mode}", flush=True)
    sessions: list[PortalSession] = []
    for index in range(target):
        sessions.append(_portal_session(base_url, login, password, timeout))
        completed = index + 1
        if target >= 50 and (completed % 50 == 0 or completed == target):
            print(f"portal_sessions_ready={completed}/{target}", flush=True)
    if mode == "shared":
        return [sessions[0]] * count
    return sessions


def _device_headers(token: str) -> dict[str, str]:
    if not token:
        raise RuntimeError("TB_DEVICE_TOKEN/--token is required for --auth device")
    return {"Authorization": f"Bearer {token}", "Accept": "application/json"}


def _request_json(
    url: str,
    headers: dict[str, str],
    timeout: float,
) -> tuple[int, object]:
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        detail = _safe_error(exc.read().decode("utf-8", "replace"))
        raise RuntimeError(f"HTTP {exc.code} detail={detail}") from exc


def fetch_head(base_url: str, headers: dict[str, str], timeout: float) -> int:
    query = urllib.parse.urlencode(
        {
            "after": 0,
            "limit": 1,
            "entity_types": "BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE",
            "wait_seconds": 0,
        }
    )
    url = base_url.rstrip("/") + "/api/v1/sync/changes/wait?" + query
    status, payload = _request_json(url, headers, timeout)
    if status != 200 or not isinstance(payload, dict):
        raise RuntimeError(f"unexpected delta head response HTTP {status}")
    head = int(payload.get("head", -1))
    if head < 0:
        raise RuntimeError("Server returned no valid delta head")
    return head


def run_one(
    base_url: str,
    headers: dict[str, str],
    after: int,
    wait_seconds: float,
    timeout: float,
) -> tuple[int, float, str]:
    query = urllib.parse.urlencode(
        {
            "after": after,
            "limit": 50,
            "entity_types": "BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE",
            "wait_seconds": wait_seconds,
        }
    )
    url = base_url.rstrip("/") + "/api/v1/sync/changes/wait?" + query
    request = urllib.request.Request(url, headers=headers)
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
            elapsed = time.monotonic() - started
            payload = json.loads(body.decode("utf-8"))
            if not {"changes", "next_after", "head", "caught_up"} <= set(payload):
                return response.status, elapsed, "invalid response contract"
            return response.status, elapsed, ""
    except urllib.error.HTTPError as exc:
        detail = _safe_error(exc.read().decode("utf-8", "replace"))
        return exc.code, time.monotonic() - started, detail
    except Exception as exc:
        return 0, time.monotonic() - started, str(exc)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("TB_SERVER_BASE_URL", ""))
    parser.add_argument(
        "--auth",
        choices=("device", "portal"),
        default=os.environ.get("TB_SYNC_TEST_AUTH", "device").strip().lower() or "device",
    )
    parser.add_argument("--token", default=os.environ.get("TB_DEVICE_TOKEN", ""))
    parser.add_argument("--portal-login", default=os.environ.get("TB_PORTAL_LOGIN", ""))
    parser.add_argument(
        "--portal-session-mode",
        choices=("shared", "unique"),
        default="unique",
        help="portal auth only: one shared session or one independent session per client",
    )
    parser.add_argument("--clients", type=int, default=32)
    parser.add_argument("--after", type=int, default=None)
    parser.add_argument("--wait-seconds", type=float, default=10.0)
    parser.add_argument("--timeout", type=float, default=40.0)
    args = parser.parse_args()

    if not args.base_url:
        parser.error("--base-url/TB_SERVER_BASE_URL is required")
    if args.clients < 1 or args.clients > 255:
        parser.error("--clients must be between 1 and 255")

    portal_sessions: list[PortalSession] = []
    unique_sessions_for_cleanup: list[PortalSession] = []
    try:
        if args.auth == "device":
            headers_per_client = [_device_headers(args.token)] * args.clients
        else:
            if not args.portal_login:
                raise RuntimeError("--portal-login/TB_PORTAL_LOGIN is required for --auth portal")
            password = os.environ.get("TB_PORTAL_PASSWORD", "")
            if not password:
                if not sys.stdin.isatty():
                    raise RuntimeError("TB_PORTAL_PASSWORD is required in non-interactive portal mode")
                password = getpass.getpass("Portal-Passwort (wird nicht angezeigt): ")
            if not password:
                raise RuntimeError("TB_PORTAL_PASSWORD is required for --auth portal")
            portal_sessions = _prepare_portal_sessions(
                args.base_url,
                args.portal_login,
                password,
                args.clients,
                min(args.timeout, 15.0),
                args.portal_session_mode,
            )
            headers_per_client = [session.headers for session in portal_sessions]
            unique_sessions_for_cleanup = (
                portal_sessions[:1]
                if args.portal_session_mode == "shared"
                else portal_sessions
            )

        after = args.after if args.after is not None else fetch_head(
            args.base_url,
            headers_per_client[0],
            min(args.timeout, 15.0),
        )

        print(f"auth={args.auth}")
        if args.auth == "portal":
            print(f"portal_session_mode={args.portal_session_mode}")
        print(f"long_poll_after={after}")

        results = []
        started = time.monotonic()
        with ThreadPoolExecutor(max_workers=args.clients) as pool:
            futures = [
                pool.submit(
                    run_one,
                    args.base_url,
                    headers_per_client[index],
                    after,
                    args.wait_seconds,
                    args.timeout,
                )
                for index in range(args.clients)
            ]
            for future in as_completed(futures):
                results.append(future.result())

        elapsed = time.monotonic() - started
        latencies = [item[1] for item in results]
        failures = [item for item in results if item[0] != 200]
        print(
            f"clients={args.clients} wall={elapsed:.2f}s "
            f"success={len(results)-len(failures)} failure={len(failures)}"
        )
        if latencies:
            print(
                "latency_s "
                f"min={min(latencies):.2f} "
                f"median={statistics.median(latencies):.2f} "
                f"max={max(latencies):.2f}"
            )
        for status, latency, detail in failures[:10]:
            print(f"FAIL status={status} latency={latency:.2f}s detail={detail}")
        return 1 if failures else 0
    except Exception as exc:
        print(f"FAIL could not run delta loadtest: {exc}")
        return 1
    finally:
        if unique_sessions_for_cleanup:
            cleanup_timeout = min(max(args.timeout, 5.0), 15.0)
            cleaned = sum(
                1
                for session in unique_sessions_for_cleanup
                if _logout_portal_session(args.base_url, session, cleanup_timeout)
            )
            print(
                f"portal_sessions_cleanup={cleaned}/"
                f"{len(unique_sessions_for_cleanup)}",
                flush=True,
            )


if __name__ == "__main__":
    raise SystemExit(main())
