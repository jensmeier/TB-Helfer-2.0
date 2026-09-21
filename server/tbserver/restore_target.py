from __future__ import annotations

import argparse
import json
import os
import shutil
import tempfile
from pathlib import Path
from typing import Any
from uuid import UUID

from .backup import BackupError, backup_path_for_run
from .config import settings
from .database import transaction
from .events import audit
from .restore import (
    _connect_restore_test,
    _extract_full_backup,
    _extract_storage_archive,
    _run_pg_restore,
    _verify_restored_database,
)


class RestoreTargetError(RuntimeError):
    pass


def _target_db_config() -> dict[str, Any]:
    name = (os.getenv("TB_RESTORE_TARGET_DB_NAME") or "").strip()
    password = os.getenv("TB_RESTORE_TARGET_DB_PASSWORD")
    host = (os.getenv("TB_RESTORE_TARGET_DB_HOST") or settings.db_host).strip()
    port_raw = (os.getenv("TB_RESTORE_TARGET_DB_PORT") or str(settings.db_port)).strip()
    user = (os.getenv("TB_RESTORE_TARGET_DB_USER") or settings.db_user).strip()
    if not name:
        raise RestoreTargetError("TB_RESTORE_TARGET_DB_NAME_MISSING")
    if password is None or not password:
        raise RestoreTargetError("TB_RESTORE_TARGET_DB_PASSWORD_MISSING")
    try:
        port = int(port_raw)
    except ValueError as exc:
        raise RestoreTargetError("TB_RESTORE_TARGET_DB_PORT_INVALID") from exc
    if name == settings.db_name:
        raise RestoreTargetError("RESTORE_TARGET_DATABASE_MUST_DIFFER_FROM_LIVE")
    return {
        "host": host,
        "port": port,
        "dbname": name,
        "user": user,
        "password": password,
    }


def _target_storage_root() -> Path:
    raw = (os.getenv("TB_RESTORE_TARGET_STORAGE_ROOT") or "").strip()
    if not raw:
        raise RestoreTargetError("TB_RESTORE_TARGET_STORAGE_ROOT_MISSING")
    target = Path(raw).resolve()
    live = settings.storage_root.resolve()
    backup = settings.backup_root.resolve()
    if target == Path("/"):
        raise RestoreTargetError("RESTORE_TARGET_STORAGE_ROOT_INVALID")
    if target == live or target.is_relative_to(live) or live.is_relative_to(target):
        raise RestoreTargetError("RESTORE_TARGET_STORAGE_MUST_DIFFER_FROM_LIVE")
    if target == backup or target.is_relative_to(backup) or backup.is_relative_to(target):
        raise RestoreTargetError("RESTORE_TARGET_STORAGE_MUST_DIFFER_FROM_BACKUPS")
    return target


def _assert_restore_target_enabled() -> None:
    if os.getenv("TB_ALLOW_RESTORE_TARGET") != "YES":
        raise RestoreTargetError("TB_ALLOW_RESTORE_TARGET_NOT_ENABLED")


def _assert_fresh_target_database(config: dict[str, Any]) -> None:
    try:
        with _connect_restore_test(config) as conn, conn.cursor() as cur:
            cur.execute("SELECT current_database() AS db")
            row = cur.fetchone()
            if not row or row["db"] != config["dbname"]:
                raise RestoreTargetError("RESTORE_TARGET_DATABASE_IDENTITY_MISMATCH")
            cur.execute("SELECT to_regnamespace('tb') AS tb_schema")
            schema = cur.fetchone()
            if schema and schema["tb_schema"] is not None:
                raise RestoreTargetError("RESTORE_TARGET_DATABASE_NOT_FRESH")
    except RestoreTargetError:
        raise
    except Exception as exc:
        raise RestoreTargetError("RESTORE_TARGET_DATABASE_CONNECT_FAILED") from exc


def _assert_fresh_target_storage(target: Path) -> None:
    if target.exists() and any(target.iterdir()):
        raise RestoreTargetError("RESTORE_TARGET_STORAGE_NOT_EMPTY")


def _cleanup_target_database(config: dict[str, Any]) -> None:
    try:
        with _connect_restore_test(config) as conn, conn.cursor() as cur:
            cur.execute("DROP SCHEMA IF EXISTS tb CASCADE")
    except Exception:
        pass


def _cleanup_target_storage(target: Path) -> None:
    try:
        if not target.exists():
            return
        for child in target.iterdir():
            if child.is_symlink() or child.is_file():
                child.unlink()
            elif child.is_dir():
                shutil.rmtree(child)
            else:
                child.unlink(missing_ok=True)
    except Exception:
        pass


def _begin_restore_target(source_run_id: UUID, config: dict[str, Any], target: Path) -> UUID:
    detail = {
        "source_run_id": str(source_run_id),
        "target_db_name": config["dbname"],
        "target_storage_label": target.name,
    }
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO tb.backup_runs(kind, status, detail)
            VALUES ('RESTORE_TARGET', 'RUNNING', %s)
            RETURNING id
            """,
            (json.dumps(detail, sort_keys=True),),
        )
        run_id = cur.fetchone()["id"]
        audit(
            cur,
            action="RESTORE_TARGET_START",
            actor_user_id=None,
            entity_type="BACKUP_RUN",
            entity_id=run_id,
            details=detail,
        )
    return run_id


def _finish_restore_target(
    run_id: UUID,
    *,
    ok: bool,
    detail: dict[str, Any] | str,
) -> dict[str, Any]:
    safe_detail = (
        json.dumps(detail, sort_keys=True)
        if isinstance(detail, dict)
        else str(detail)[:500]
    )
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.backup_runs
            SET status = %s, detail = %s, finished_at = now()
            WHERE id = %s
            RETURNING id, kind, status, started_at, finished_at
            """,
            ("OK" if ok else "FAILED", safe_detail, run_id),
        )
        row = dict(cur.fetchone())
        audit(
            cur,
            action="RESTORE_TARGET_OK" if ok else "RESTORE_TARGET_FAILED",
            actor_user_id=None,
            entity_type="BACKUP_RUN",
            entity_id=run_id,
            details={"status": row["status"]},
        )
    return row


def run_restore_target(
    *,
    source_run_id: UUID,
    confirm: str,
) -> dict[str, Any]:
    _assert_restore_target_enabled()
    try:
        source, source_path = backup_path_for_run(source_run_id)
    except BackupError as exc:
        raise RestoreTargetError(str(exc)) from exc
    if source["kind"] != "FULL":
        raise RestoreTargetError("RESTORE_TARGET_REQUIRES_FULL_BACKUP")

    config = _target_db_config()
    target = _target_storage_root()
    expected = f"RESTORE-TARGET:{source_run_id}:{config['dbname']}"
    if confirm != expected:
        raise RestoreTargetError(
            "RESTORE_TARGET_CONFIRMATION_MISMATCH; expected " + expected
        )
    _assert_fresh_target_database(config)
    _assert_fresh_target_storage(target)

    run_id = _begin_restore_target(source_run_id, config, target)
    scratch_root = settings.backup_root / ".restore-targets"
    scratch_root.mkdir(parents=True, exist_ok=True)
    database_started = False
    storage_started = False
    detail: dict[str, Any] = {
        "source_run_id": str(source_run_id),
        "source_kind": source["kind"],
        "target_db_name": config["dbname"],
        "target_storage_label": target.name,
    }
    try:
        with tempfile.TemporaryDirectory(
            prefix=f"{source_run_id}-",
            dir=scratch_root,
        ) as temp_dir:
            temp = Path(temp_dir)
            database_dump, files_archive, manifest = _extract_full_backup(
                source_path,
                temp / "full",
            )
            detail["manifest_created_at"] = manifest.get("created_at")

            database_started = True
            # The dedicated PostGIS target database is fresh from the TB-Helfer
            # perspective (no tb schema) but the postgis/postgis image already
            # preinstalls extension objects. Use the same safe clean restore as
            # the confirmed restore-test so dump-owned extensions/schemas are
            # replaced before the backup is restored.
            _run_pg_restore(database_dump, config, clean=True)
            detail["database"] = _verify_restored_database(config)

            restored_files_root = temp / "restored-files"
            detail["files"] = _extract_storage_archive(
                files_archive,
                restored_files_root,
            )
            source_storage = restored_files_root / "storage"
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists():
                target.rmdir()
            storage_started = True
            shutil.copytree(source_storage, target, symlinks=False)

        row = _finish_restore_target(run_id, ok=True, detail=detail)
        return {
            "restore_run": row,
            "source_backup_run_id": str(source_run_id),
            "target_db_name": config["dbname"],
            "target_storage_root": str(target),
            "database": detail["database"],
            "files": detail["files"],
            "next_step": "verify target API offline, then perform a separate controlled cutover",
        }
    except Exception as exc:
        if storage_started:
            _cleanup_target_storage(target)
        if database_started:
            _cleanup_target_database(config)
        code = (
            str(exc)
            if isinstance(exc, RestoreTargetError)
            else type(exc).__name__
        )
        _finish_restore_target(run_id, ok=False, detail=code)
        raise


def main() -> None:
    parser = argparse.ArgumentParser(
        description="TB-Server 1.0 controlled restore into a separate target"
    )
    parser.add_argument("command", choices=("restore-target",))
    parser.add_argument("--backup-run-id", required=True, type=UUID)
    parser.add_argument(
        "--confirm",
        required=True,
        help="Must equal RESTORE-TARGET:<backup_uuid>:<target_db_name>",
    )
    args = parser.parse_args()
    result = run_restore_target(
        source_run_id=args.backup_run_id,
        confirm=args.confirm,
    )
    print("OK")
    for key, value in result.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
