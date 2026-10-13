"""A cutover must select the matching prepared database AND storage together."""
from __future__ import annotations

import json
import os
from pathlib import Path
from uuid import UUID

KEY = "server_recovery"
MARKER = ".tb-recovery.json"


def verify_cutover(conn, storage_root: Path) -> None:
    expected = os.getenv("TB_RECOVERY_ID", "").strip()
    if not expected:
        return
    try:
        expected = str(UUID(expected))
        marker_path = storage_root / MARKER
        if marker_path.is_symlink():
            raise ValueError("link")
        marker = json.loads(marker_path.read_text(encoding="utf-8"))
        with conn.cursor() as cur:
            cur.execute("SELECT value FROM tb.server_settings WHERE setting_key = %s", (KEY,))
            row = cur.fetchone()
        state = row["value"] if row else {}
        if any(item.get("recovery_id") != expected or item.get("phase") != "READY"
               for item in (marker, state)):
            raise ValueError("mismatch")
        if marker.get("mode", "SECURITY") != state.get("mode", "SECURITY"):
            raise ValueError("mode mismatch")
        if state.get("mode") == "UPDATE" and marker.get("runtime") != state.get("runtime"):
            raise ValueError("runtime mismatch")
        if marker.get("source_sha256") != state.get("source_sha256"):
            raise ValueError("source mismatch")
    except Exception as exc:
        raise RuntimeError("RECOVERY_DATABASE_STORAGE_MISMATCH") from exc



def recovery_epoch() -> str | None:
    value = os.getenv("TB_RECOVERY_ID", "").strip()
    return str(UUID(value)) if value else None


def require_recovery_epoch(request) -> None:
    """Old clients must not silently apply snapshots or replay a pre-restore outbox.

    The header acknowledges the protocol generation, not authorization. A client
    must preserve pending work and rebase before resuming its mutations.
    """
    expected = recovery_epoch()
    if expected and request.headers.get("X-TB-Recovery-ID") != expected:
        from fastapi import HTTPException
        raise HTTPException(409, detail={"code": "SERVER_RECOVERY_REBASE_REQUIRED",
                                        "recovery_id": expected},
                            headers={"X-TB-Recovery-ID": expected})
