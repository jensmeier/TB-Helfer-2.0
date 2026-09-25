#!/usr/bin/env python3
"""Small dependency-free load probe for /api/v1/sync/changes/wait.

No token is stored. Pass it via TB_DEVICE_TOKEN or --token. The script performs
read-only requests and is intended for staging only.
"""
from __future__ import annotations

import argparse
import json
import os
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def fetch_head(base_url: str, token: str, timeout: float) -> int:
    query = urllib.parse.urlencode(
        {
            "after": 0,
            "limit": 1,
            "entity_types": "BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE",
            "wait_seconds": 0,
        }
    )
    url = base_url.rstrip("/") + "/api/v1/sync/changes/wait?" + query
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    head = int(payload.get("head", -1))
    if head < 0:
        raise RuntimeError("Server returned no valid delta head")
    return head


def run_one(base_url: str, token: str, after: int, wait_seconds: float, timeout: float) -> tuple[int, float, str]:
    query = urllib.parse.urlencode(
        {
            "after": after,
            "limit": 50,
            "entity_types": "BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE",
            "wait_seconds": wait_seconds,
        }
    )
    url = base_url.rstrip("/") + "/api/v1/sync/changes/wait?" + query
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
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
        return exc.code, time.monotonic() - started, exc.read().decode("utf-8", "replace")[:240]
    except Exception as exc:
        return 0, time.monotonic() - started, str(exc)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("TB_SERVER_BASE_URL", ""))
    parser.add_argument("--token", default=os.environ.get("TB_DEVICE_TOKEN", ""))
    parser.add_argument("--clients", type=int, default=32)
    parser.add_argument("--after", type=int, default=None)
    parser.add_argument("--wait-seconds", type=float, default=10.0)
    parser.add_argument("--timeout", type=float, default=40.0)
    args = parser.parse_args()

    if not args.base_url or not args.token:
        parser.error("--base-url/TB_SERVER_BASE_URL and --token/TB_DEVICE_TOKEN are required")
    if args.clients < 1 or args.clients > 255:
        parser.error("--clients must be between 1 and 255")

    try:
        after = args.after if args.after is not None else fetch_head(
            args.base_url,
            args.token,
            min(args.timeout, 15.0),
        )
    except Exception as exc:
        print(f"FAIL could not determine delta head: {exc}")
        return 1
    print(f"long_poll_after={after}")

    results = []
    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.clients) as pool:
        futures = [
            pool.submit(run_one, args.base_url, args.token, after, args.wait_seconds, args.timeout)
            for _ in range(args.clients)
        ]
        for future in as_completed(futures):
            results.append(future.result())

    elapsed = time.monotonic() - started
    latencies = [item[1] for item in results]
    failures = [item for item in results if item[0] != 200]
    print(f"clients={args.clients} wall={elapsed:.2f}s success={len(results)-len(failures)} failure={len(failures)}")
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
