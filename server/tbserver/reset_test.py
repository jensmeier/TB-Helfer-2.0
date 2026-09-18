from __future__ import annotations

import argparse
import shutil
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import UUID

from .backup import BackupError, backup_path_for_run
from .config import settings
from .database import connect, transaction
from .migrations import migrate, status as migration_status


RESET_ALLOWED_ENVIRONMENTS = {"development", "test", "staging"}
RESET_BACKUP_MAX_AGE_HOURS = 24


class TestResetError(RuntimeError):
    pass


def _assert_environment() -> None:
    if settings.environment not in RESET_ALLOWED_ENVIRONMENTS:
        raise TestResetError("TEST_RESET_FORBIDDEN_IN_THIS_ENVIRONMENT")


def _load_full_backup(run_id: UUID | None) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        if run_id is None:
            cur.execute(
                """
                SELECT id, kind, status, storage_key, byte_size, sha256,
                       started_at, finished_at
                FROM tb.backup_runs
                WHERE kind = 'FULL' AND status = 'OK'
                ORDER BY finished_at DESC NULLS LAST, started_at DESC
                LIMIT 1
                """
            )
        else:
            cur.execute(
                """
                SELECT id, kind, status, storage_key, byte_size, sha256,
                       started_at, finished_at
                FROM tb.backup_runs
                WHERE id = %s
                """,
                (run_id,),
            )
        row = cur.fetchone()
    if not row:
        raise TestResetError("FULL_BACKUP_REQUIRED_BEFORE_RESET")
    backup = dict(row)
    if backup["kind"] != "FULL" or backup["status"] != "OK":
        raise TestResetError("FULL_BACKUP_REQUIRED_BEFORE_RESET")
    if backup["finished_at"] is None:
        raise TestResetError("FULL_BACKUP_NOT_FINISHED")
    finished_at = backup["finished_at"]
    if finished_at.tzinfo is None:
        finished_at = finished_at.replace(tzinfo=timezone.utc)
    if datetime.now(timezone.utc) - finished_at > timedelta(
        hours=RESET_BACKUP_MAX_AGE_HOURS
    ):
        raise TestResetError("FULL_BACKUP_TOO_OLD_FOR_RESET")
    try:
        backup_path_for_run(backup["id"])
    except BackupError as exc:
        raise TestResetError(str(exc)) from exc
    return backup


def _clear_storage_contents(root: Path) -> None:
    resolved = root.resolve()
    if resolved == Path("/"):
        raise TestResetError("REFUSE_TO_CLEAR_FILESYSTEM_ROOT")
    backup_root = settings.backup_root.resolve()
    if resolved == backup_root or backup_root.is_relative_to(resolved):
        raise TestResetError("BACKUP_ROOT_MUST_BE_OUTSIDE_STORAGE")
    root.mkdir(parents=True, exist_ok=True)
    for child in root.iterdir():
        if child.is_symlink() or child.is_file():
            child.unlink()
        elif child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink(missing_ok=True)


def _reset_database() -> list[str]:
    with connect() as conn:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute("DROP SCHEMA IF EXISTS tb CASCADE")
    return migrate()


def run_test_reset(
    *,
    backup_run_id: UUID | None,
    confirm: str,
) -> dict:
    _assert_environment()
    backup = _load_full_backup(backup_run_id)
    expected = f"RESET-TEST-DATA:{backup['id']}"
    if confirm != expected:
        raise TestResetError(
            "RESET_CONFIRMATION_MISMATCH; expected " + expected
        )

    applied = _reset_database()
    _clear_storage_contents(settings.storage_root)
    current = migration_status()
    if current["pending"]:
        raise TestResetError("RESET_MIGRATIONS_PENDING")
    return {
        "environment": settings.environment,
        "verified_backup_run_id": str(backup["id"]),
        "migrations_applied": applied,
        "latest_applied": current["latest_applied"],
        "storage_cleared": True,
        "next_step": "bootstrap initial admin and recreate test data",
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="TB-Server 1.0 test/staging data reset"
    )
    parser.add_argument("command", choices=("reset-test-data",))
    parser.add_argument(
        "--backup-run-id",
        type=UUID,
        default=None,
        help="Optional successful FULL backup run. Defaults to latest FULL backup.",
    )
    parser.add_argument(
        "--confirm",
        required=True,
        help="Must equal RESET-TEST-DATA:<verified-full-backup-run-id>",
    )
    args = parser.parse_args()
    result = run_test_reset(
        backup_run_id=args.backup_run_id,
        confirm=args.confirm,
    )
    print("OK")
    for key, value in result.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
