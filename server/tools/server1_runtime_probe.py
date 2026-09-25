#!/usr/bin/env python3
"""Safe in-container runtime probe for Synology staging.

Read-only against the live DB. It creates only temporary local files outside TB storage
for the rendition smoke test and never prints credentials/secrets.
"""
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from PIL import Image as PILImage

from tbserver import SERVER_VERSION
from tbserver.capabilities import CAPABILITIES
from tbserver.database import connect, ping
from tbserver.image_renditions import render_image_rendition
from tbserver.migrations import status as migration_status


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


def _memory_limit_bytes() -> int | None:
    candidates = (
        Path("/sys/fs/cgroup/memory.max"),
        Path("/sys/fs/cgroup/memory/memory.limit_in_bytes"),
    )
    for path in candidates:
        try:
            raw = path.read_text(encoding="utf-8").strip()
        except OSError:
            continue
        if not raw or raw == "max":
            return None
        try:
            value = int(raw)
        except ValueError:
            continue
        # Some cgroup-v1 installations expose a giant sentinel for "unlimited".
        if value >= (1 << 60):
            return None
        return value
    return None


def main() -> int:
    failures: list[str] = []

    database_up = ping()
    if not database_up:
        failures.append("database ping failed")

    migrations = migration_status()
    if (
        migrations.get("latest_known") != 15
        or migrations.get("latest_applied") != 15
        or migrations.get("pending") != []
    ):
        failures.append(f"migration status unexpected: {migrations}")

    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema='tb'
              AND table_name='files'
              AND column_name='photo_metadata'
            """
        )
        column = cur.fetchone()
        cur.execute(
            """
            SELECT count(*) AS invalid_count
            FROM tb.files
            WHERE photo_metadata IS NULL
               OR jsonb_typeof(photo_metadata) <> 'object'
            """
        )
        invalid_count = int(cur.fetchone()["invalid_count"])

    if not column or column["data_type"] != "jsonb" or column["is_nullable"] != "NO":
        failures.append(f"tb.files.photo_metadata invalid: {dict(column) if column else None}")
    if invalid_count:
        failures.append(f"invalid photo_metadata rows: {invalid_count}")

    missing_caps = sorted(name for name in REQUIRED_CAPABILITIES if CAPABILITIES.get(name) is not True)
    if missing_caps:
        failures.append("missing capabilities: " + ",".join(missing_caps))

    with tempfile.TemporaryDirectory(prefix="tb-server-runtime-probe-") as temp:
        root = Path(temp)
        source = root / "phone.jpg"
        preview = root / "preview.jpg"
        with PILImage.new("RGB", (3072, 4080), "white") as image:
            image.save(source, "JPEG", quality=90)
        render_image_rendition(source, preview, "preview")
        with PILImage.open(preview) as image:
            dimensions = [image.width, image.height]
            if image.width > 1280 or image.height > 1280:
                failures.append(f"preview rendition too large: {dimensions}")

    memory_limit = _memory_limit_bytes()
    if memory_limit is None:
        failures.append("API cgroup memory limit is not active")

    result = {
        "server_version": SERVER_VERSION,
        "database": "up" if database_up else "down",
        "migrations": {
            "known": migrations.get("known"),
            "applied": migrations.get("applied"),
            "pending": migrations.get("pending"),
            "latest_known": migrations.get("latest_known"),
            "latest_applied": migrations.get("latest_applied"),
        },
        "photo_metadata_column": dict(column) if column else None,
        "photo_metadata_invalid_rows": invalid_count,
        "required_capabilities_missing": missing_caps,
        "rendition_preview_px": dimensions,
        "cgroup_memory_limit_bytes": memory_limit,
        "status": "OK" if not failures else "FAILED",
        "failures": failures,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
