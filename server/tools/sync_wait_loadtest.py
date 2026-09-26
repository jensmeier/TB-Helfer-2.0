#!/usr/bin/env python3
"""Dependency-free load probe for /api/v1/sync/changes/wait.

The probe is read-only and intended for staging only.

Authentication modes:
- device: Bearer token from TB_DEVICE_TOKEN/--token (mobile contract)
- portal: normal portal session using TB_PORTAL_PASSWORD plus --portal-login

Secrets are never printed.
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


def _portal_headers(base_url: str, login: str, password: str, timeout: float) -> dict[str, str]:
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

    cookie_header = "; ".join(f"{cookie.name}={cookie.value}" for cookie in jar)
    if "tb_portal_session=" not in cookie_header:
        raise RuntimeError("portal login returned no tb_portal_session cookie")
    return {"Cookie": cookie_header, "Accept": "application/json"}


def _device_headers(token: str) -> dict[str, str]:
    if not token:
        raise RuntimeError("TB_DEVICE_TOKEN/--token is required for --auth device")
    return {"Authorization": f"Bearer {token}", "Accept": "application/json"}


def _request_json(url: str, headers: dict[str, str], timeout: float) -> tuple[int, object]:
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
    parser.add_argument("--clients", type=int, default=32)
    parser.add_argument("--after", type=int, default=None)
    parser.add_argument("--wait-seconds", type=float, default=10.0)
    parser.add_argument("--timeout", type=float, default=40.0)
    args = parser.parse_args()

    if not args.base_url:
        parser.error("--base-url/TB_SERVER_BASE_URL is required")
    if args.clients < 1 or args.clients > 255:
        parser.error("--clients must be between 1 and 255")

    try:
        if args.auth == "device":
            headers = _device_headers(args.token)
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
            headers = _portal_headers(
                args.base_url,
                args.portal_login,
                password,
                min(args.timeout, 15.0),
            )

        after = args.after if args.after is not None else fetch_head(
            args.base_url,
            headers,
            min(args.timeout, 15.0),
        )
    except Exception as exc:
        print(f"FAIL could not determine delta head: {exc}")
        return 1

    print(f"auth={args.auth}")
    print(f"long_poll_after={after}")

    results = []
    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.clients) as pool:
        futures = [
            pool.submit(
                run_one,
                args.base_url,
                headers,
                after,
                args.wait_seconds,
                args.timeout,
            )
            for _ in range(args.clients)
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


if __name__ == "__main__":
    raise SystemExit(main())
