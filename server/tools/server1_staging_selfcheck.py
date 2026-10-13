#!/usr/bin/env python3
"""Read-only TB-Server 1.0 staging preflight.

No secrets are printed or stored. Optional device token can be passed through
TB_DEVICE_TOKEN or --token to validate the authenticated delta contract.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


REQUIRED_CAPABILITIES = {
    "btb_entries_v1",
    "btb_participant_shift_end_v1",
    "btb_general_work_entries_v1",
    "btb_photo_metadata_v1",
    "file_image_renditions_v1",
    "sync_change_feed_v1",
    "sync_change_wait_v1",
    "admin_portal_session_split_v1",
}


def request_json(url: str, *, token: str | None = None, timeout: float = 15.0) -> tuple[int, dict]:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace")
        try:
            payload = json.loads(body)
        except Exception:
            payload = {"detail": body[:300]}
        return exc.code, payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("TB_SERVER_BASE_URL", ""))
    parser.add_argument("--token", default=os.environ.get("TB_DEVICE_TOKEN", ""))
    parser.add_argument("--timeout", type=float, default=15.0)
    args = parser.parse_args()

    if not args.base_url:
        parser.error("--base-url or TB_SERVER_BASE_URL is required")
    base = args.base_url.rstrip("/")

    failures: list[str] = []

    status, ready = request_json(base + "/health/ready", timeout=args.timeout)
    print(f"health/ready: HTTP {status} status={ready.get('status')}")
    if status != 200 or ready.get("status") != "ok":
        failures.append("health/ready is not OK")

    status, caps = request_json(base + "/api/v1/capabilities", timeout=args.timeout)
    available = caps.get("capabilities") if isinstance(caps, dict) else {}
    missing = sorted(name for name in REQUIRED_CAPABILITIES if not (available or {}).get(name))
    print(
        "capabilities: "
        f"HTTP {status} server={caps.get('server_version') if isinstance(caps, dict) else None} "
        f"missing={','.join(missing) if missing else '-'}"
    )
    if status != 200 or missing:
        failures.append("required capabilities missing")

    if args.token:
        query = urllib.parse.urlencode(
            {
                "after": 0,
                "limit": 1,
                "entity_types": "BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE",
                "wait_seconds": 0,
            }
        )
        status, delta = request_json(
            base + "/api/v1/sync/changes/wait?" + query,
            token=args.token,
            timeout=args.timeout,
        )
        contract_ok = isinstance(delta, dict) and {
            "changes",
            "next_after",
            "head",
            "caught_up",
        } <= set(delta)
        print(f"sync/changes/wait: HTTP {status} contract={'OK' if contract_ok else 'FEHLER'}")
        if status != 200 or not contract_ok:
            failures.append("authenticated delta contract failed")
    else:
        print("sync/changes/wait: skipped (no device token supplied)")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("OK: TB-Server 1.0 staging preflight passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
