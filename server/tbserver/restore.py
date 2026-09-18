from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import tempfile
from pathlib import Path, PurePosixPath
from typing import Any
from uuid import UUID

import psycopg
from psycopg.rows import dict_row

from .backup import BackupError, backup_path_for_run
from .config import settings
from .database import transaction


FULL_BACKUP_FORMAT = "tb-server-1.0-full-backup-v1"


class RestoreTestError(RuntimeError):
    pass


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as src:
        for chunk in iter(lambda: src.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _restore_db_config() -> dict[str, Any]:
    name = (os.getenv("TB_RESTORE_TEST_DB_NAME") or "").strip()
    password = os.getenv("TB_RESTORE_TEST_DB_PASSWORD")
    host = (os.getenv("TB_RESTORE_TEST_DB_HOST") or settings.db_host).strip()
    port_raw = (os.getenv("TB_RESTORE_TEST_DB_PORT") or str(settings.db_port)).strip()
    user = (os.getenv("TB_RESTORE_TEST_DB_USER") or settings.db_user).strip()
    if not name:
        raise RestoreTestError("TB_RESTORE_TEST_DB_NAME_MISSING")
    if password is None or not password:
        raise RestoreTestError("TB_RESTORE_TEST_DB_PASSWORD_MISSING")
    try:
        port = int(port_raw)
    except ValueError as exc:
        raise RestoreTestError("TB_RESTORE_TEST_DB_PORT_INVALID") from exc
    if name == settings.db_name:
        raise RestoreTestError("RESTORE_TEST_DATABASE_MUST_DIFFER_FROM_LIVE")
    return {
        "host": host,
        "port": port,
        "dbname": name,
        "user": user,
        "password": password,
    }


def _restore_env(config: dict[str, Any]) -> dict[str, str]:
    env = dict(os.environ)
    env["PGPASSWORD"] = str(config["password"])
    return env


def _connect_restore_test(config: dict[str, Any]):
    return psycopg.connect(
        host=config["host"],
        port=config["port"],
        dbname=config["dbname"],
        user=config["user"],
        password=config["password"],
        row_factory=dict_row,
        autocommit=True,
        connect_timeout=5,
    )


def _prepare_restore_test_database(config: dict[str, Any]) -> None:
    try:
        with _connect_restore_test(config) as conn, conn.cursor() as cur:
            cur.execute("SELECT current_database() AS db")
            row = cur.fetchone()
            if not row or row["db"] != config["dbname"]:
                raise RestoreTestError("RESTORE_TEST_DATABASE_IDENTITY_MISMATCH")
            cur.execute("DROP SCHEMA IF EXISTS tb CASCADE")
    except RestoreTestError:
        raise
    except Exception as exc:
        raise RestoreTestError("RESTORE_TEST_DATABASE_PREPARE_FAILED") from exc


def _run_pg_restore(dump_path: Path, config: dict[str, Any]) -> None:
    command = [
        "pg_restore",
        "--clean",
        "--if-exists",
        "--no-owner",
        "--no-privileges",
        "--exit-on-error",
        "--host",
        str(config["host"]),
        "--port",
        str(config["port"]),
        "--username",
        str(config["user"]),
        "--dbname",
        str(config["dbname"]),
        str(dump_path),
    ]
    try:
        result = subprocess.run(
            command,
            env=_restore_env(config),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            timeout=6 * 60 * 60,
            check=False,
        )
    except FileNotFoundError as exc:
        raise RestoreTestError("PG_RESTORE_NOT_INSTALLED") from exc
    except subprocess.TimeoutExpired as exc:
        raise RestoreTestError("PG_RESTORE_TIMEOUT") from exc
    if result.returncode != 0:
        raise RestoreTestError(f"PG_RESTORE_FAILED_EXIT_{result.returncode}")


def _verify_restored_database(config: dict[str, Any]) -> dict[str, Any]:
    try:
        with _connect_restore_test(config) as conn, conn.cursor() as cur:
            cur.execute("SELECT to_regclass('tb.schema_migrations') AS registry")
            row = cur.fetchone()
            if not row or row["registry"] is None:
                raise RestoreTestError("RESTORE_TEST_SCHEMA_REGISTRY_MISSING")
            cur.execute(
                """
                SELECT count(*) AS applied, max(version) AS latest
                FROM tb.schema_migrations
                """
            )
            migration = dict(cur.fetchone())
            if int(migration["applied"] or 0) <= 0:
                raise RestoreTestError("RESTORE_TEST_NO_MIGRATIONS")
            cur.execute(
                """
                SELECT count(*) AS present
                FROM information_schema.tables
                WHERE table_schema = 'tb'
                  AND table_name = ANY(%s)
                """,
                (["companies", "users", "worksites", "files", "survey_originals"],),
            )
            required_present = int(cur.fetchone()["present"])
            if required_present != 5:
                raise RestoreTestError("RESTORE_TEST_CORE_TABLES_MISSING")
    except RestoreTestError:
        raise
    except Exception as exc:
        raise RestoreTestError("RESTORE_TEST_DATABASE_VERIFY_FAILED") from exc
    return {
        "applied_migrations": int(migration["applied"]),
        "latest_migration": int(migration["latest"] or 0),
        "core_tables_present": required_present,
    }


def _validate_tar_member(member: tarfile.TarInfo) -> None:
    path = PurePosixPath(member.name)
    if path.is_absolute() or ".." in path.parts:
        raise RestoreTestError("RESTORE_TEST_ARCHIVE_PATH_INVALID")
    if not path.parts or path.parts[0] != "storage":
        raise RestoreTestError("RESTORE_TEST_ARCHIVE_ROOT_INVALID")
    if member.issym() or member.islnk() or member.isdev():
        raise RestoreTestError("RESTORE_TEST_ARCHIVE_LINK_NOT_ALLOWED")


def _extract_storage_archive(archive_path: Path, target: Path) -> dict[str, Any]:
    target.mkdir(parents=True, exist_ok=True)
    file_count = 0
    total_bytes = 0
    try:
        with tarfile.open(archive_path, "r:gz") as archive:
            members = archive.getmembers()
            for member in members:
                _validate_tar_member(member)
                if member.isfile():
                    file_count += 1
                    total_bytes += int(member.size)
            archive.extractall(target, members=members, filter="data")
    except RestoreTestError:
        raise
    except Exception as exc:
        raise RestoreTestError("RESTORE_TEST_FILES_ARCHIVE_INVALID") from exc
    if not (target / "storage").exists():
        raise RestoreTestError("RESTORE_TEST_STORAGE_ROOT_MISSING")
    return {"file_count": file_count, "total_bytes": total_bytes}


def _extract_full_backup(full_path: Path, target: Path) -> tuple[Path, Path, dict[str, Any]]:
    target.mkdir(parents=True, exist_ok=True)
    names = {"database.dump", "files.tar.gz", "manifest.json"}
    try:
        with tarfile.open(full_path, "r:gz") as archive:
            members = archive.getmembers()
            by_name = {member.name: member for member in members}
            if (
                len(members) != len(names)
                or set(by_name) != names
                or any(not by_name[name].isfile() for name in names)
            ):
                raise RestoreTestError("FULL_BACKUP_LAYOUT_INVALID")
            for name in names:
                source = archive.extractfile(by_name[name])
                if source is None:
                    raise RestoreTestError("FULL_BACKUP_MEMBER_MISSING")
                with source, (target / name).open("wb") as out:
                    shutil.copyfileobj(source, out)
    except RestoreTestError:
        raise
    except Exception as exc:
        raise RestoreTestError("FULL_BACKUP_ARCHIVE_INVALID") from exc

    try:
        manifest = json.loads((target / "manifest.json").read_text(encoding="utf-8"))
    except Exception as exc:
        raise RestoreTestError("FULL_BACKUP_MANIFEST_INVALID") from exc
    if manifest.get("format") != FULL_BACKUP_FORMAT:
        raise RestoreTestError("FULL_BACKUP_FORMAT_UNSUPPORTED")
    database_dump = target / "database.dump"
    files_archive = target / "files.tar.gz"
    if _hash_file(database_dump) != manifest.get("database_sha256"):
        raise RestoreTestError("FULL_BACKUP_DATABASE_SHA256_MISMATCH")
    if _hash_file(files_archive) != manifest.get("files_sha256"):
        raise RestoreTestError("FULL_BACKUP_FILES_SHA256_MISMATCH")
    return database_dump, files_archive, manifest


def _begin_restore_test(source_run_id: UUID) -> UUID:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO tb.backup_runs(kind, status, detail)
            VALUES ('RESTORE_TEST', 'RUNNING', %s)
            RETURNING id
            """,
            (json.dumps({"source_run_id": str(source_run_id)}, sort_keys=True),),
        )
        return cur.fetchone()["id"]


def _finish_restore_test(
    restore_run_id: UUID,
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
            ("OK" if ok else "FAILED", safe_detail, restore_run_id),
        )
        return dict(cur.fetchone())


def run_restore_test(source_run_id: UUID) -> dict[str, Any]:
    try:
        source, source_path = backup_path_for_run(source_run_id)
    except BackupError as exc:
        raise RestoreTestError(str(exc)) from exc

    restore_run_id = _begin_restore_test(source_run_id)
    detail: dict[str, Any] = {
        "source_run_id": str(source_run_id),
        "source_kind": source["kind"],
    }
    scratch_root = settings.backup_root / ".restore-tests"
    scratch_root.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(
            prefix=f"{source_run_id}-",
            dir=scratch_root,
        ) as temp_dir:
            temp = Path(temp_dir)
            kind = source["kind"]
            if kind == "DATABASE":
                config = _restore_db_config()
                _prepare_restore_test_database(config)
                _run_pg_restore(source_path, config)
                detail["database"] = _verify_restored_database(config)
            elif kind == "FILES":
                detail["files"] = _extract_storage_archive(
                    source_path,
                    temp / "restored-files",
                )
            elif kind == "FULL":
                database_dump, files_archive, manifest = _extract_full_backup(
                    source_path,
                    temp / "full",
                )
                detail["manifest_created_at"] = manifest.get("created_at")
                config = _restore_db_config()
                _prepare_restore_test_database(config)
                _run_pg_restore(database_dump, config)
                detail["database"] = _verify_restored_database(config)
                detail["files"] = _extract_storage_archive(
                    files_archive,
                    temp / "restored-files",
                )
            else:
                raise RestoreTestError("RESTORE_TEST_SOURCE_KIND_NOT_SUPPORTED")
        return _finish_restore_test(restore_run_id, ok=True, detail=detail)
    except Exception as exc:
        code = (
            str(exc)
            if isinstance(exc, RestoreTestError)
            else type(exc).__name__
        )
        _finish_restore_test(restore_run_id, ok=False, detail=code)
        raise
