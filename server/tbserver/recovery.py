"""Prepare a verified replacement, never overwrite or switch the live server."""
from __future__ import annotations

import json
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

from psycopg.types.json import Jsonb

from .backup import BackupError, backup_path_for_run, run_backup, _hash_file
from .config import settings
from .database import transaction
from .events import audit
from .migrations import discover_migrations
from .recovery_guard import KEY, MARKER
from .restore import (RestoreTestError, _connect_restore_test, _extract_full_backup,
                      _extract_storage_archive, _run_pg_restore,
                      _verify_restored_database, _verify_restored_files)
from .restore_target import (RestoreTargetError, _target_db_config, _target_storage_root,
                             _assert_restore_target_enabled,
                             _assert_fresh_target_database, _assert_fresh_target_storage)

LOCK = 731019303


class RecoveryError(RuntimeError):
    pass


def read_state(cur) -> dict:
    cur.execute("SELECT value FROM tb.server_settings WHERE setting_key = %s", (KEY,))
    row = cur.fetchone()
    return dict(row["value"]) if row else {}


def write_state(cur, state: dict) -> None:
    cur.execute("""INSERT INTO tb.server_settings(setting_key,value) VALUES (%s,%s)
                   ON CONFLICT (setting_key) DO UPDATE
                   SET value=EXCLUDED.value,updated_at=now()""", (KEY, Jsonb(state)))


def _save(state: dict) -> None:
    with transaction() as conn, conn.cursor() as cur:
        write_state(cur, state)


def _pin(run_id: UUID, destination: Path) -> dict:
    source, path = backup_path_for_run(run_id)
    if source["kind"] != "FULL":
        raise RecoveryError("RECOVERY_REQUIRES_FULL_BACKUP")
    if not source.get("sha256"):
        raise RecoveryError("RECOVERY_SOURCE_HASH_MISSING")
    # A private copy remains available even if ordinary retention removes a run.
    with path.open("rb") as src, destination.open("xb") as dst:
        shutil.copyfileobj(src, dst, 1024 * 1024)
        dst.flush()
        os.fsync(dst.fileno())
    if destination.stat().st_size != source["byte_size"] or _hash_file(destination) != source["sha256"]:
        raise RecoveryError("RECOVERY_SOURCE_CHANGED")
    return source


def _seal_target(config: dict, state: dict, admin_id: str) -> None:
    """In one target-only transaction revoke stale access, work and automation."""
    with _connect_restore_test(config) as conn, conn.transaction(), conn.cursor() as cur:
        cur.execute("SELECT version,name,checksum_sha256 FROM tb.schema_migrations ORDER BY version")
        actual = [(r["version"], r["name"], r["checksum_sha256"]) for r in cur.fetchall()]
        expected = [(m.version, m.name, m.checksum) for m in discover_migrations()]
        if actual != expected:
            raise RecoveryError("RECOVERY_SCHEMA_VERSION_MISMATCH")
        cur.execute("""SELECT u.id FROM tb.users u JOIN tb.portal_credentials c ON c.user_id=u.id
                       WHERE u.id=%s AND u.company_role='ADMIN' AND u.status='ACTIVE'
                       AND u.deleted_at IS NULL AND c.password_hash IS NOT NULL
                       AND (u.access_valid_until IS NULL OR u.access_valid_until > now())""", (admin_id,))
        if not cur.fetchone():
            raise RecoveryError("RECOVERY_ADMIN_NOT_IN_BACKUP")
        cur.execute("UPDATE tb.portal_sessions SET revoked_at=now() WHERE revoked_at IS NULL")
        cur.execute("UPDATE tb.portal_account_tokens SET used_at=now() WHERE used_at IS NULL")
        mode = state.get("mode", "SECURITY")
        if mode not in {"UPDATE", "SECURITY"}:
            raise RecoveryError("RECOVERY_MODE_INVALID")
        if mode == "SECURITY":
            cur.execute("UPDATE tb.devices SET status='REVOKED',revoked_at=now(),auth_token_hash=NULL,auth_token_hint=NULL")
            cur.execute("UPDATE tb.device_activation_tokens SET revoked_at=now() WHERE revoked_at IS NULL")
            cur.execute("UPDATE tb.worksite_invitations SET revoked_at=now() WHERE revoked_at IS NULL")
            cur.execute("UPDATE tb.portal_credentials SET activation_token_hash=NULL,activation_expires_at=NULL")
            cur.execute("UPDATE tb.users SET status='DISABLED' WHERE id <> %s AND deleted_at IS NULL", (admin_id,))
        cur.execute("UPDATE tb.upload_sessions SET status='EXPIRED' WHERE status='OPEN'")
        cur.execute("""UPDATE tb.background_jobs SET status='FAILED',last_error='CANCELLED_AFTER_RECOVERY',
                       finished_at=now(),updated_at=now() WHERE status IN ('PENDING','RUNNING')""")
        if mode == "SECURITY":
            from .system_settings import load_automation_policy, save_automation_policy
            policy = load_automation_policy(cur)
            policy.update(email_enabled=False, alert_email_enabled=False, automatic_backup_enabled=False,
                          automatic_retention_enabled=False)
            for schedule in policy["backup_schedules"].values():
                schedule["enabled"] = False
            policy["retention_schedule"]["enabled"] = False
            save_automation_policy(cur, policy)
        write_state(cur, state)
        audit(cur, action="SERVER_RECOVERY_PREPARED", actor_user_id=admin_id,
              entity_type="BACKUP_RUN", entity_id=UUID(state["source_backup_id"]),
              details={"recovery_id":state["recovery_id"], "source_sha256":state["source_sha256"], "mode":mode})


def prepare_recovery(job: dict, progress) -> dict:
    job_id = str(job["id"])
    # Keep the source lock across the complete run. Target lock also excludes
    # another source server attempting to prepare the same target database.
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("SELECT pg_try_advisory_xact_lock(%s) AS locked", (LOCK,))
        if not cur.fetchone()["locked"]:
            raise RecoveryError("RECOVERY_BUSY")
        state = read_state(cur)
        if state.get("job_id") != job_id or state.get("phase") != "QUEUED":
            raise RecoveryError("RECOVERY_REVIEW_REQUIRED")
        state.update(phase="RUNNING", started_at=datetime.now(timezone.utc).isoformat())
        # The lock transaction remains open; publish progress on another connection.
        _save(state)
        try:
            _assert_restore_target_enabled()
            config, target = _target_db_config(), _target_storage_root()
            if target.is_symlink():
                raise RecoveryError("RECOVERY_TARGET_LINK")
            with _connect_restore_test(config) as target_conn, target_conn.cursor() as target_cur:
                target_cur.execute("SELECT pg_try_advisory_lock(%s) AS locked", (LOCK,))
                if not target_cur.fetchone()["locked"]:
                    raise RecoveryError("RECOVERY_TARGET_BUSY")
                _assert_fresh_target_database(config)
                _assert_fresh_target_storage(target)
                state.update(target_db_name=config["dbname"], target_db_host=config["host"],
                             target_storage_root=str(target))
                bundle = settings.backup_root / ".recovery" / state["recovery_id"]
                bundle.mkdir(parents=True, exist_ok=False)
                progress(8, "Gewähltes Vollbackup wird unveränderlich kopiert")
                source = _pin(UUID(state["source_backup_id"]), bundle / "selected.tar.gz")
                state.update(source_sha256=source["sha256"], source_date=source["started_at"].isoformat())
                _save(state)
                progress(15, "Aktueller Serverbestand wird zusätzlich gesichert")
                safety = run_backup("FULL", progress=lambda p,t: progress(15+p//4,t))
                state["safety_backup_id"] = str(safety["id"])
                _pin(UUID(state["safety_backup_id"]), bundle / "safety.tar.gz")
                _save(state)
                progress(45, "Ausgewähltes Backup wird im getrennten Ziel wiederhergestellt")
                scratch = bundle / "unpacked"
                dump, archive, manifest = _extract_full_backup(bundle / "selected.tar.gz", scratch)
                if state.get("mode", "SECURITY") == "UPDATE":
                    from .recovery_version import check_update_version
                    try:
                        state["runtime"] = check_update_version(manifest)
                    except ValueError as exc:
                        raise RecoveryError(str(exc)) from exc
                _run_pg_restore(dump, config, clean=True)
                state["database"] = _verify_restored_database(config)
                progress(65, "Dateien werden wiederhergestellt und vollständig geprüft")
                _extract_storage_archive(archive, scratch / "files")
                target.mkdir(parents=True, exist_ok=True)
                shutil.copytree(scratch / "files" / "storage", target, dirs_exist_ok=True)
                state["references"] = _verify_restored_files(config, target, manifest)
                ready = dict(state, phase="READY", prepared_at=datetime.now(timezone.utc).isoformat())
                _seal_target(config, ready, state["admin_id"])
                marker = target / MARKER
                with marker.open("x", encoding="utf-8") as handle:
                    json.dump(ready, handle, sort_keys=True)
                    handle.flush()
                    os.fsync(handle.fileno())
                (bundle / "recovery.json").write_text(json.dumps(ready, indent=2), encoding="utf-8")
                # Partial targets are deliberately kept for diagnosis on failure.
                shutil.rmtree(scratch)
                _save(ready)
                progress(95, "Ziel geprüft und bereit – Server noch nicht umgeschaltet")
                return ready
        except Exception as exc:
            state.update(phase="FAILED", error=str(exc) if isinstance(exc, (RecoveryError, RestoreTargetError, BackupError, RestoreTestError))
                         else type(exc).__name__)
            _save(state)
            raise


