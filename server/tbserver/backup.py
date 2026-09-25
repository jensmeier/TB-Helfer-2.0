from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from uuid import UUID

from .config import settings
from .system_settings import retention_policy
from .database import transaction


BACKUP_KINDS = {"DATABASE", "FILES", "FULL"}
ProgressCallback = Callable[[int, str], None]


def _progress(callback: ProgressCallback | None, percent: int, text: str) -> None:
    if callback is not None:
        callback(percent, text)


class BackupError(RuntimeError):
    pass


def _timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _absolute_backup_path(storage_key: str) -> Path:
    root = settings.backup_root.resolve()
    path = (root / storage_key).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise BackupError("INVALID_BACKUP_STORAGE_KEY") from exc
    return path


def _validate_backup_root() -> None:
    storage = settings.storage_root.resolve()
    backup = settings.backup_root.resolve()
    if backup == storage or backup.is_relative_to(storage):
        raise BackupError("BACKUP_ROOT_MUST_BE_OUTSIDE_STORAGE")


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as src:
        for chunk in iter(lambda: src.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _pg_env() -> dict[str, str]:
    env = dict(os.environ)
    env["PGPASSWORD"] = settings.db_password
    return env


def _run_pg_dump(target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "pg_dump",
        "--format=custom",
        "--no-owner",
        "--no-privileges",
        "--host",
        settings.db_host,
        "--port",
        str(settings.db_port),
        "--username",
        settings.db_user,
        "--dbname",
        settings.db_name,
        "--file",
        str(target),
    ]
    try:
        result = subprocess.run(
            command,
            env=_pg_env(),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            timeout=6 * 60 * 60,
            check=False,
        )
    except FileNotFoundError as exc:
        raise BackupError("PG_DUMP_NOT_INSTALLED") from exc
    except subprocess.TimeoutExpired as exc:
        raise BackupError("PG_DUMP_TIMEOUT") from exc
    if result.returncode != 0:
        # stderr can contain infrastructure details, therefore it is deliberately
        # not persisted in backup_runs/audit.
        raise BackupError(f"PG_DUMP_FAILED_EXIT_{result.returncode}")
    if not target.is_file() or target.stat().st_size <= 0:
        raise BackupError("PG_DUMP_EMPTY")


def _storage_backup_filter(info: tarfile.TarInfo) -> tarfile.TarInfo | None:
    # Image renditions are derived, immutable-cache data and are recreated from
    # the protected original file on demand. Keeping them out of FILES/FULL
    # backups avoids duplicating thumbnail/preview caches in every backup.
    if ".renditions" in Path(info.name).parts:
        return None
    return info


def _archive_storage(target: Path) -> None:
    root = settings.storage_root
    target.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(target, "w:gz", dereference=False) as archive:
        if root.exists():
            archive.add(
                root,
                arcname="storage",
                recursive=True,
                filter=_storage_backup_filter,
            )
        else:
            info = tarfile.TarInfo("storage/")
            info.type = tarfile.DIRTYPE
            info.mode = 0o750
            archive.addfile(info)
    if not target.is_file() or target.stat().st_size <= 0:
        raise BackupError("FILES_BACKUP_EMPTY")


def _begin_run(kind: str) -> UUID:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO tb.backup_runs(kind, status)
            VALUES (%s, 'RUNNING')
            RETURNING id
            """,
            (kind,),
        )
        return cur.fetchone()["id"]


def _finish_run_ok(run_id: UUID, storage_key: str, path: Path) -> dict[str, Any]:
    size = path.stat().st_size
    sha256 = _hash_file(path)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.backup_runs
            SET status = 'OK', storage_key = %s, byte_size = %s, sha256 = %s,
                finished_at = now(), detail = NULL
            WHERE id = %s
            RETURNING id, kind, status, storage_key, byte_size, sha256,
                      started_at, finished_at
            """,
            (storage_key, size, sha256, run_id),
        )
        return dict(cur.fetchone())


def _finish_run_failed(run_id: UUID, code: str) -> None:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.backup_runs
            SET status = 'FAILED', detail = %s, finished_at = now()
            WHERE id = %s
            """,
            (code[:500], run_id),
        )


def _database_backup(
    run_id: UUID,
    progress: ProgressCallback | None = None,
) -> tuple[str, Path]:
    key = f"database/{_timestamp()}_{run_id}.dump"
    final_path = _absolute_backup_path(key)
    final_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = final_path.with_suffix(final_path.suffix + ".tmp")
    try:
        _progress(progress, 20, "Datenbank wird gesichert")
        _run_pg_dump(temp_path)
        _progress(progress, 85, "Datenbanksicherung wird abgeschlossen")
        os.replace(temp_path, final_path)
    finally:
        temp_path.unlink(missing_ok=True)
    return key, final_path


def _files_backup(
    run_id: UUID,
    progress: ProgressCallback | None = None,
) -> tuple[str, Path]:
    key = f"files/{_timestamp()}_{run_id}.tar.gz"
    final_path = _absolute_backup_path(key)
    final_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = final_path.with_suffix(final_path.suffix + ".tmp")
    try:
        _progress(progress, 20, "Dateien werden gepackt")
        _archive_storage(temp_path)
        _progress(progress, 85, "Dateisicherung wird abgeschlossen")
        os.replace(temp_path, final_path)
    finally:
        temp_path.unlink(missing_ok=True)
    return key, final_path


def _full_backup(
    run_id: UUID,
    progress: ProgressCallback | None = None,
) -> tuple[str, Path]:
    key = f"full/{_timestamp()}_{run_id}.tar.gz"
    final_path = _absolute_backup_path(key)
    final_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = final_path.with_suffix(final_path.suffix + ".tmp")
    scratch_root = settings.backup_root / ".tmp"
    scratch_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="full-", dir=scratch_root) as temp_dir:
        scratch = Path(temp_dir)
        database_dump = scratch / "database.dump"
        files_archive = scratch / "files.tar.gz"
        manifest = scratch / "manifest.json"
        _progress(progress, 15, "Datenbankteil wird gesichert")
        _run_pg_dump(database_dump)
        _progress(progress, 45, "Dateien werden für das Vollbackup gepackt")
        _archive_storage(files_archive)
        _progress(progress, 75, "Prüfsummen und Manifest werden erstellt")
        manifest.write_text(
            json.dumps(
                {
                    "format": "tb-server-1.0-full-backup-v1",
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "database_file": "database.dump",
                    "files_archive": "files.tar.gz",
                    "database_sha256": _hash_file(database_dump),
                    "files_sha256": _hash_file(files_archive),
                },
                ensure_ascii=False,
                sort_keys=True,
                indent=2,
            ),
            encoding="utf-8",
        )
        _progress(progress, 88, "Vollbackup wird fertig gepackt")
        try:
            with tarfile.open(temp_path, "w:gz") as archive:
                archive.add(database_dump, arcname="database.dump", recursive=False)
                archive.add(files_archive, arcname="files.tar.gz", recursive=False)
                archive.add(manifest, arcname="manifest.json", recursive=False)
            os.replace(temp_path, final_path)
        finally:
            temp_path.unlink(missing_ok=True)
    return key, final_path


def run_backup(
    kind: str,
    progress: ProgressCallback | None = None,
) -> dict[str, Any]:
    normalized = kind.strip().upper()
    if normalized not in BACKUP_KINDS:
        raise BackupError("BACKUP_KIND_NOT_SUPPORTED")
    _validate_backup_root()
    settings.backup_root.mkdir(parents=True, exist_ok=True)
    run_id = _begin_run(normalized)
    final_path: Path | None = None
    try:
        _progress(progress, 10, "Backup wird vorbereitet")
        if normalized == "DATABASE":
            key, final_path = _database_backup(run_id, progress)
        elif normalized == "FILES":
            key, final_path = _files_backup(run_id, progress)
        else:
            key, final_path = _full_backup(run_id, progress)
        _progress(progress, 94, "Prüfsumme wird kontrolliert")
        result = _finish_run_ok(run_id, key, final_path)
        _progress(progress, 98, "Backup ist gespeichert")
        return result
    except Exception as exc:
        if final_path is not None:
            final_path.unlink(missing_ok=True)
        code = str(exc) if isinstance(exc, BackupError) else type(exc).__name__
        _finish_run_failed(run_id, code)
        raise


def backup_path_for_run(run_id: UUID) -> tuple[dict[str, Any], Path]:
    with transaction() as conn, conn.cursor() as cur:
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
        raise BackupError("BACKUP_RUN_NOT_FOUND")
    result = dict(row)
    if result["status"] != "OK" or not result.get("storage_key"):
        raise BackupError("BACKUP_RUN_NOT_READY")
    path = _absolute_backup_path(result["storage_key"])
    if not path.is_file():
        raise BackupError("BACKUP_FILE_MISSING")
    if result.get("byte_size") is not None and path.stat().st_size != result["byte_size"]:
        raise BackupError("BACKUP_SIZE_MISMATCH")
    if result.get("sha256") and _hash_file(path) != result["sha256"]:
        raise BackupError("BACKUP_SHA256_MISMATCH")
    return result, path


def backup_storage_snapshot() -> dict[str, Any]:
    root = settings.backup_root
    if not root.exists():
        return {
            "available": False,
            "label": root.name or "backups",
            "total_bytes": None,
            "used_bytes": None,
            "free_bytes": None,
            "percent_used": None,
        }
    usage = shutil.disk_usage(root)
    return {
        "available": True,
        "label": root.name or "backups",
        "total_bytes": usage.total,
        "used_bytes": usage.used,
        "free_bytes": usage.free,
        "percent_used": round((usage.used / usage.total) * 100.0, 2)
        if usage.total
        else 0.0,
    }

def _remove_empty_backup_parents(path: Path) -> None:
    root = settings.backup_root.resolve()
    current = path.parent
    while current != root:
        try:
            current.rmdir()
        except OSError:
            return
        current = current.parent


def run_retention() -> dict[str, Any]:
    days, min_per_kind = retention_policy()
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            WITH ranked AS (
                SELECT id, kind, storage_key, finished_at, started_at,
                       row_number() OVER (
                           PARTITION BY kind
                           ORDER BY finished_at DESC NULLS LAST, started_at DESC, id DESC
                       ) AS keep_rank
                FROM tb.backup_runs
                WHERE kind IN ('DATABASE','FILES','FULL')
                  AND status = 'OK'
                  AND storage_key IS NOT NULL
            )
            SELECT id, kind, storage_key, finished_at, keep_rank
            FROM ranked
            WHERE keep_rank > %s
              AND finished_at < now() - (%s * interval '1 day')
            ORDER BY finished_at, id
            """,
            (min_per_kind, days),
        )
        candidates = [dict(row) for row in cur.fetchall()]

    purged: list[str] = []
    missing_files: list[str] = []
    delete_errors: list[str] = []

    for candidate in candidates:
        backup_id = candidate["id"]
        key = candidate["storage_key"]
        path = _absolute_backup_path(key)

        with transaction() as conn, conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, kind, status, storage_key
                FROM tb.backup_runs
                WHERE id = %s
                FOR UPDATE
                """,
                (backup_id,),
            )
            current = cur.fetchone()
            if (
                not current
                or current["status"] != "OK"
                or current["storage_key"] != key
            ):
                continue
            cur.execute(
                """
                UPDATE tb.backup_runs
                SET status = 'PURGED',
                    storage_key = NULL,
                    detail = %s
                WHERE id = %s
                """,
                (
                    json.dumps(
                        {
                            "reason": "RETENTION",
                            "retention_days": days,
                            "minimum_kept_per_kind": min_per_kind,
                            "previous_storage_key": key,
                        },
                        sort_keys=True,
                    ),
                    backup_id,
                ),
            )

        purged.append(str(backup_id))
        try:
            if path.exists():
                path.unlink()
                _remove_empty_backup_parents(path)
            else:
                missing_files.append(str(backup_id))
        except OSError:
            # DB no longer references the artifact. Leave any undeleted file as
            # an unreferenced safety orphan for explicit maintenance cleanup.
            delete_errors.append(str(backup_id))

    return {
        "retention_days": days,
        "minimum_kept_per_kind": min_per_kind,
        "candidate_count": len(candidates),
        "purged_count": len(purged),
        "purged_run_ids": purged,
        "missing_file_run_ids": missing_files,
        "delete_error_run_ids": delete_errors,
    }

