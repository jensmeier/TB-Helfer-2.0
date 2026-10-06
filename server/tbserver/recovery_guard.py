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
        if marker.get("source_sha256") != state.get("source_sha256"):
            raise ValueError("source mismatch")
    except Exception as exc:
        raise RuntimeError("RECOVERY_DATABASE_STORAGE_MISMATCH") from exc
