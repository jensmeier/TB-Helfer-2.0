#!/usr/bin/env python3
"""Practical background-worker backup E2E for TB-Server 1.0 staging.

This test intentionally creates one real backup job. Credentials are never printed.
It verifies admin session/CSRF, queued job -> worker completion, progress reporting,
and an OK backup artifact reported by the system status API.
"""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


def _request(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    body: dict | None = None,
    csrf: str | None = None,
    change_id: str | None = None,
) -> tuple[int, object]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if csrf:
        headers["X-CSRF-Token"] = csrf
    if change_id:
        headers["X-Client-Change-Id"] = change_id
    req = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with opener.open(req, timeout=30) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            payload: object = json.loads(raw)
        except Exception:
            payload = raw[:300]
        return exc.code, payload


def _cookie(jar: http.cookiejar.CookieJar, name: str) -> str | None:
    for item in jar:
        if item.name == name:
            return item.value
    return None


def _parse_time(value: object) -> datetime | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("TB_PORTAL_BASE_URL", ""))
    parser.add_argument("--admin-login", default=os.environ.get("TB_ADMIN_LOGIN", ""))
    parser.add_argument("--admin-password", default=os.environ.get("TB_ADMIN_PASSWORD", ""))
    parser.add_argument("--kind", choices=("DATABASE", "FILES", "FULL"), default="DATABASE")
    parser.add_argument("--timeout-seconds", type=int, default=1800)
    args = parser.parse_args()

    missing = [
        label
        for label, value in (
            ("base URL", args.base_url),
            ("admin login", args.admin_login),
            ("admin password", args.admin_password),
        )
        if not value
    ]
    if missing:
        parser.error("missing: " + ", ".join(missing))

    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    status, login = _request(
        opener,
        args.base_url,
        "/api/v1/admin/session/login",
        method="POST",
        body={"login": args.admin_login, "password": args.admin_password},
    )
    if status != 200:
        print(f"FAIL admin login HTTP {status}")
        return 1

    csrf = _cookie(jar, "tb_admin_csrf")
    if not csrf:
        print("FAIL admin CSRF cookie missing")
        return 1

    started = datetime.now(timezone.utc)
    change_id = "backup-e2e-" + str(int(time.time()))
    status, queued = _request(
        opener,
        args.base_url,
        "/api/v1/admin/system/backups",
        method="POST",
        body={"kind": args.kind},
        csrf=csrf,
        change_id=change_id,
    )
    if status != 202 or not isinstance(queued, dict):
        print(f"FAIL backup enqueue HTTP {status}")
        return 1

    job = queued.get("job")
    job_id = str(job.get("id")) if isinstance(job, dict) and job.get("id") else ""
    if not job_id:
        print("FAIL server returned no backup job id")
        return 1

    print(f"backup job queued: kind={args.kind} job_id={job_id}")

    deadline = time.monotonic() + max(30, args.timeout_seconds)
    last_state: tuple[str, int, str] | None = None
    final_status: dict | None = None

    while time.monotonic() < deadline:
        code, payload = _request(opener, args.base_url, "/api/v1/admin/system/status")
        if code != 200 or not isinstance(payload, dict):
            print(f"FAIL system status HTTP {code}")
            return 1

        jobs = payload.get("jobs") if isinstance(payload.get("jobs"), list) else []
        current = next(
            (item for item in jobs if isinstance(item, dict) and str(item.get("id")) == job_id),
            None,
        )
        if current is None:
            print("FAIL queued backup job disappeared from recent job status")
            return 1

        state = str(current.get("status") or "")
        percent = int(current.get("progress_percent") or 0)
        text = str(current.get("progress_text") or "")
        state_tuple = (state, percent, text)
        if state_tuple != last_state:
            print(f"job: status={state} progress={percent}% step={text or '-'}")
            last_state = state_tuple

        if state == "FAILED":
            print("FAIL backup background job ended FAILED")
            return 1
        if state == "DONE":
            final_status = payload
            break
        time.sleep(2)

    if final_status is None:
        print("FAIL backup background job timeout")
        return 1

    backups = final_status.get("backups") if isinstance(final_status.get("backups"), list) else []
    candidates = []
    for item in backups:
        if not isinstance(item, dict):
            continue
        if str(item.get("kind") or "").upper() != args.kind or item.get("status") != "OK":
            continue
        when = _parse_time(item.get("started_at"))
        if when is not None and when >= started:
            candidates.append(item)

    if not candidates:
        print("FAIL job is DONE but no matching OK backup run was reported")
        return 1

    newest = max(candidates, key=lambda item: _parse_time(item.get("started_at")) or started)
    if int(newest.get("byte_size") or 0) <= 0:
        print("FAIL matching backup has no positive byte size")
        return 1
    if not str(newest.get("sha256") or "").strip():
        print("FAIL matching backup has no SHA-256")
        return 1

    status, _ = _request(
        opener,
        args.base_url,
        "/api/v1/admin/session/logout",
        method="POST",
        csrf=csrf,
        change_id="backup-e2e-logout-" + str(int(time.time())),
    )
    if status != 204:
        print(f"FAIL admin logout HTTP {status}")
        return 1

    print(
        "OK: worker completed real backup "
        f"kind={args.kind} bytes={newest.get('byte_size')} sha256=present"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
