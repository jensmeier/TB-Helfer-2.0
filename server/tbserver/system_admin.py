from __future__ import annotations

import os
import shutil
import threading
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from . import SERVER_VERSION
from .admin import require_admin, require_admin_csrf
from .backup import backup_storage_snapshot
from .config import settings
from .database import transaction
from .events import audit
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .jobs import JobError, enqueue_in_tx
from .migrations import status as migration_status
from .monitor import read_monitor_snapshot
from .system_settings import load_automation_policy, save_automation_policy, smtp_status


router = APIRouter(prefix="/api/v1/admin/system", tags=["admin-system"])

_PROCESS_STARTED_AT = datetime.now(timezone.utc)
_PROCESS_STARTED_MONOTONIC = time.monotonic()
_CPU_SAMPLE_LOCK = threading.Lock()
_CPU_SAMPLE: tuple[int, int] | None = None
_SAFE_SETTING_KEYS = {
    "schema_baseline",
    "trash_retention_days",
    "capabilities_revision",
}


class BackupJobCreate(BaseModel):
    kind: str = Field(min_length=1, max_length=20)


class RecoveryPrepare(BaseModel):
    mode: Literal["UPDATE", "SECURITY"] = "UPDATE"
    backup_run_id: UUID
    confirmation: str = Field(max_length=100)


@router.get("/recovery")
def recovery_status(_: dict[str, Any] = Depends(require_admin)) -> dict:
    from .recovery import read_state
    with transaction() as conn, conn.cursor() as cur:
        state = read_state(cur)
        cur.execute("""SELECT id,started_at,byte_size FROM tb.backup_runs
                       WHERE kind='FULL' AND status='OK' ORDER BY started_at DESC LIMIT 100""")
        backups = [dict(row) for row in cur.fetchall()]
        if state.get("job_id") and state.get("phase") in {"QUEUED", "RUNNING"}:
            cur.execute("SELECT status,last_error FROM tb.background_jobs WHERE id=%s", (state["job_id"],))
            job = cur.fetchone()
            if not job or job["status"] in {"DONE", "FAILED"}:
                state = dict(state, phase="REVIEW_REQUIRED", error="Vorbereitung unterbrochen; Ziel prüfen.")
    return {"state":state, "backups":backups,
            "active":bool(os.getenv("TB_RECOVERY_ID")) and state.get("recovery_id")==os.getenv("TB_RECOVERY_ID")}


@router.post("/recovery/prepare", status_code=202)
def enqueue_recovery(payload: RecoveryPrepare,
                     user: dict[str, Any] = Depends(require_admin_csrf)) -> dict:
    from uuid import uuid4
    from .recovery import LOCK, read_state, write_state
    if os.getenv("TB_RECOVERY_ID"):
        raise HTTPException(409, "RECOVERY_ALREADY_ON_TARGET")
    if payload.confirmation != f"WIEDERHERSTELLEN {payload.backup_run_id}":
        raise HTTPException(400, "RECOVERY_CONFIRMATION_MISMATCH")
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("SELECT pg_try_advisory_xact_lock(%s) AS locked", (LOCK,))
        if not cur.fetchone()["locked"]:
            raise HTTPException(409, "RECOVERY_BUSY")
        state = read_state(cur)
        if state.get("phase") in {"QUEUED", "RUNNING", "READY"}:
            raise HTTPException(409, "RECOVERY_ALREADY_PREPARED_OR_RUNNING")
        cur.execute("SELECT id,kind,status,started_at FROM tb.backup_runs WHERE id=%s", (payload.backup_run_id,))
        source = cur.fetchone()
        if not source or source["kind"] != "FULL" or source["status"] != "OK":
            raise HTTPException(409, "RECOVERY_REQUIRES_FULL_BACKUP")
        job = enqueue_in_tx(cur, "RECOVERY_PREPARE", {"backup_run_id":str(payload.backup_run_id)})
        state = {"recovery_id":str(uuid4()), "phase":"QUEUED", "job_id":str(job["id"]),
                 "source_backup_id":str(payload.backup_run_id), "admin_id":str(user["user_id"]),
                 "source_date":source["started_at"].isoformat(), "mode":payload.mode}
        write_state(cur, state)
        audit(cur, action="SERVER_RECOVERY_REQUESTED", actor_user_id=user["user_id"],
              entity_type="BACKGROUND_JOB", entity_id=job["id"], details=state)
    return {"job":job, "state":state}


class AutomationSettingsUpdate(BaseModel):
    email_enabled: bool = False
    email_frequency: str = Field(default="WEEKLY", min_length=1, max_length=20)
    email_hour: int = 10
    email_minute: int = 0
    email_weekday: int = 6
    email_monthday: int = 1
    email_recipients: list[str] = Field(default_factory=list, max_length=20)
    alert_email_enabled: bool = False
    alert_recovery_enabled: bool = True
    alert_cooldown_minutes: int = 30
    alert_types: dict[str, bool] = Field(default_factory=dict)
    timezone: str = Field(default="Europe/Berlin", min_length=1, max_length=100)
    user_expiry_warning_days: int = 30
    storage_warning_percent: float = 85.0
    backup_schedules: dict[str, dict[str, Any]] = Field(default_factory=dict)
    retention_schedule: dict[str, Any] = Field(default_factory=dict)
    backup_retention_days: int = 90
    backup_retention_min_per_kind: int = 3
    # Legacy input fields remain accepted while older admin pages are phased out.
    automatic_backup_enabled: bool = False
    backup_kind: str = Field(default="DATABASE", min_length=1, max_length=20)
    backup_hour: int = 4
    automatic_retention_enabled: bool = False
    retention_hour: int = 5


_WARNING_ACK_KEY = "system_warning_acknowledged_at"

_JOB_LABELS = {
    "BACKUP_DATABASE": "Datenbank-Backup",
    "BACKUP_FILES": "Datei-Backup",
    "BACKUP_FULL": "Vollbackup",
    "BACKUP_RETENTION": "Backup-Aufbewahrung",
    "RESTORE_TEST": "Restore-Test",
    "RECOVERY_PREPARE": "Wiederherstellung vorbereiten",
    "SYSTEM_EMAIL_REPORT": "Systembericht per E-Mail",
}
_JOB_STATUS_LABELS = {
    "PENDING": "Wartet",
    "RUNNING": "Läuft",
    "DONE": "Fertig",
    "FAILED": "Fehlgeschlagen",
}
_ERROR_MESSAGES = {
    "STALE_WORKER_RECOVERED": "Der Worker wurde während des Vorgangs unterbrochen. Der Auftrag wurde erneut eingeplant.",
    "TB_RESTORE_TEST_DB_NAME_MISSING": "Die getrennte Restore-Test-Datenbank war im Worker nicht konfiguriert.",
    "TB_RESTORE_TEST_DB_PASSWORD_MISSING": "Das Passwort der Restore-Test-Datenbank fehlt in der Server-Konfiguration.",
    "PG_RESTORE_FAILED_EXIT_1": "PostgreSQL konnte die Wiederherstellung nicht vollständig durchführen.",
    "PG_DUMP_FAILED_EXIT_1": "PostgreSQL konnte das Datenbank-Backup nicht erstellen.",
    "SMTP_NOT_CONFIGURED": "Der E-Mail-Versand ist noch nicht vollständig eingerichtet.",
    "EMAIL_RECIPIENTS_EMPTY": "Für den Systembericht ist noch kein E-Mail-Empfänger hinterlegt.",
    "RESTORE_TEST_DATABASE_PREPARE_FAILED": "Die getrennte Restore-Test-Datenbank konnte nicht vorbereitet werden.",
    "RESTORE_TEST_DATABASE_VERIFY_FAILED": "Die wiederhergestellte Test-Datenbank konnte nicht vollständig geprüft werden.",
    "RESTORE_TEST_CORE_TABLES_MISSING": "Im Restore-Test fehlen erwartete Kerntabellen.",
    "BACKUP_FILE_MISSING": "Die zu diesem Backup gehörende Sicherungsdatei fehlt.",
    "BACKUP_SHA256_MISMATCH": "Die Prüfsumme der Sicherungsdatei stimmt nicht; das Backup wird nicht verwendet.",
    "BACKUP_SIZE_MISMATCH": "Die Größe der Sicherungsdatei stimmt nicht mit dem gespeicherten Backup-Eintrag überein.",
}
_AUDIT_LABELS = {
    "SERVER_RECOVERY_REQUESTED": "Wiederherstellung angefordert",
    "SERVER_RECOVERY_PREPARED": "Wiederherstellungsziel geprüft",
    "RESTORE_TARGET_START": "Restore ins getrennte Ziel gestartet",
    "RESTORE_TARGET_OK": "Restore ins getrennte Ziel erfolgreich",
    "RESTORE_TARGET_FAILED": "Restore ins getrennte Ziel fehlgeschlagen",
    "RESTORE_TEST_JOB_CREATE": "Restore-Test gestartet",
    "BACKUP_JOB_CREATE": "Backup-Auftrag erstellt",
    "BACKUP_RETENTION_JOB_CREATE": "Aufbewahrungsprüfung gestartet",
    "SYSTEM_EMAIL_TEST_JOB_CREATE": "E-Mail-Testbericht gestartet",
    "SYSTEM_WARNINGS_ACKNOWLEDGED": "Systemwarnungen quittiert",
    "SYSTEM_JOB_HISTORY_DELETE": "Job-Verlauf bereinigt",
    "SYSTEM_JOB_DELETE": "Job-Eintrag gelöscht",
}


def _parse_timestamp(value: Any) -> datetime | None:
    if isinstance(value, datetime):
        parsed = value
    elif isinstance(value, str) and value.strip():
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
    else:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _friendly_error(value: Any) -> str | None:
    if value is None:
        return None
    raw = str(value).strip()
    if not raw:
        return None
    if raw in _ERROR_MESSAGES:
        return _ERROR_MESSAGES[raw]
    for code, message in _ERROR_MESSAGES.items():
        if code in raw:
            return message
    return "Der Vorgang ist fehlgeschlagen. Technische Details bleiben im Serverprotokoll."


def _job_view(row: dict[str, Any]) -> dict[str, Any]:
    result = dict(row)
    raw_error = result.pop("last_error", None)
    job_type = str(result.get("job_type") or "").upper()
    status = str(result.get("status") or "").upper()
    result["job_label"] = _JOB_LABELS.get(job_type, job_type.replace("_", " ").title())
    result["status_label"] = _JOB_STATUS_LABELS.get(status, status)
    result["error_description"] = _friendly_error(raw_error)
    return result


def _audit_view(row: dict[str, Any]) -> dict[str, Any]:
    result = dict(row)
    action = str(result.get("action") or "")
    result["action_label"] = _AUDIT_LABELS.get(
        action,
        action.replace("_", " ").title(),
    )
    result["is_failure"] = action.endswith("_FAILED") or "ERROR" in action
    return result


def _storage_snapshot() -> dict[str, Any]:
    root = settings.storage_root
    if not root.exists():
        return {
            "available": False,
            "label": root.name or "storage",
            "total_bytes": None,
            "used_bytes": None,
            "free_bytes": None,
            "percent_used": None,
        }
    usage = shutil.disk_usage(root)
    percent = (
        round((usage.used / usage.total) * 100.0, 2)
        if usage.total > 0
        else 0.0
    )
    return {
        "available": True,
        "label": root.name or "storage",
        "total_bytes": usage.total,
        "used_bytes": usage.used,
        "free_bytes": usage.free,
        "percent_used": percent,
    }


def _station_reference_snapshot() -> dict[str, Any]:
    base = Path(__file__).resolve().parent.parent / "data"
    active = Path(
        os.getenv("STATION_ACTIVE_PATH", "").strip()
        or base / "TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json"
    )
    operating = Path(
        os.getenv("STATION_OPERATING_PATH", "").strip()
        or base / "TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json"
    )
    missing = [
        path.name
        for path in (active, operating)
        if not path.is_file()
    ]
    return {
        "available": not missing,
        "active_path": str(active),
        "operating_path": str(operating),
        "missing": missing,
    }


def _read_cpu_sample() -> tuple[int, int] | None:
    try:
        first = Path("/proc/stat").read_text(encoding="utf-8").splitlines()[0]
        parts = [int(value) for value in first.split()[1:]]
        if len(parts) < 4:
            return None
        idle = parts[3] + (parts[4] if len(parts) > 4 else 0)
        return sum(parts), idle
    except (OSError, ValueError, IndexError):
        return None


def _cpu_percent() -> float | None:
    global _CPU_SAMPLE
    sample = _read_cpu_sample()
    if sample is None:
        return None
    with _CPU_SAMPLE_LOCK:
        previous = _CPU_SAMPLE
        _CPU_SAMPLE = sample
    if previous is not None:
        total_delta = sample[0] - previous[0]
        idle_delta = sample[1] - previous[1]
        if total_delta > 0:
            return round(max(0.0, min(100.0, (1.0 - idle_delta / total_delta) * 100.0)), 1)
    try:
        load_1 = os.getloadavg()[0]
        cpus = max(1, os.cpu_count() or 1)
        return round(max(0.0, min(100.0, load_1 / cpus * 100.0)), 1)
    except (AttributeError, OSError):
        return None


def _memory_snapshot() -> dict[str, Any]:
    values: dict[str, int] = {}
    try:
        for line in Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
            key, _, rest = line.partition(":")
            if key in {"MemTotal", "MemAvailable"}:
                values[key] = int(rest.strip().split()[0]) * 1024
    except (OSError, ValueError, IndexError):
        return {
            "available": False,
            "total_bytes": None,
            "used_bytes": None,
            "free_bytes": None,
            "percent_used": None,
        }
    total = values.get("MemTotal")
    free = values.get("MemAvailable")
    if not total or free is None:
        return {
            "available": False,
            "total_bytes": total,
            "used_bytes": None,
            "free_bytes": free,
            "percent_used": None,
        }
    used = max(0, total - free)
    return {
        "available": True,
        "total_bytes": total,
        "used_bytes": used,
        "free_bytes": free,
        "percent_used": round(used / total * 100.0, 1),
    }


def _system_temperature_c() -> float | None:
    values: list[float] = []
    try:
        for path in Path("/sys/class/thermal").glob("thermal_zone*/temp"):
            try:
                raw = float(path.read_text(encoding="utf-8").strip())
            except (OSError, ValueError):
                continue
            value = raw / 1000.0 if raw > 200 else raw
            if 0.0 < value < 120.0:
                values.append(value)
    except OSError:
        return None
    return round(max(values), 1) if values else None


def _synology_disk_temperatures() -> list[dict[str, Any]]:
    root_value = os.getenv(
        "TB_SYNO_DISK_TEMPERATURE_ROOT",
        "/host/synostorage/disks",
    ).strip()
    root = Path(root_value or "/host/synostorage/disks")
    if not root.is_dir():
        return []

    try:
        disk_dirs = sorted(
            (path for path in root.iterdir() if path.is_dir()),
            key=lambda path: (len(path.name), path.name),
        )
    except OSError:
        return []

    result: list[dict[str, Any]] = []
    for disk_dir in disk_dirs:
        try:
            raw = float((disk_dir / "temperature").read_text(encoding="utf-8").strip())
        except (OSError, ValueError):
            continue
        value = raw / 1000.0 if raw > 200 else raw
        if not 0.0 < value < 120.0:
            continue
        result.append(
            {
                "label": f"Laufwerk {len(result) + 1}",
                "device": disk_dir.name,
                "temperature_c": round(value, 1),
            }
        )
    return result


def _host_snapshot() -> dict[str, Any]:
    memory = _memory_snapshot()
    try:
        load_1, load_5, load_15 = os.getloadavg()
    except (AttributeError, OSError):
        load_1 = load_5 = load_15 = None
    return {
        "cpu_percent": _cpu_percent(),
        "cpu_count": os.cpu_count(),
        "ram": memory,
        "temperature_c": _system_temperature_c(),
        "disk_temperatures": _synology_disk_temperatures(),
        "load_1": round(load_1, 2) if load_1 is not None else None,
        "load_5": round(load_5, 2) if load_5 is not None else None,
        "load_15": round(load_15, 2) if load_15 is not None else None,
    }


@router.get("/status")
def admin_system_status(
    _: dict[str, Any] = Depends(require_admin),
) -> dict:
    migration = migration_status()
    storage = _storage_snapshot()
    backup_storage = backup_storage_snapshot()
    automation = load_automation_policy()
    smtp = smtp_status()
    monitor = read_monitor_snapshot()
    host = _host_snapshot()
    station_reference = _station_reference_snapshot()
    status_now = datetime.now(timezone.utc)
    warning_until = status_now + timedelta(days=int(automation["user_expiry_warning_days"]))

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT value FROM tb.server_settings WHERE setting_key = %s",
            (_WARNING_ACK_KEY,),
        )
        warning_ack_row = cur.fetchone()
        warning_acknowledged_at = _parse_timestamp(
            warning_ack_row["value"] if warning_ack_row else None
        )

        cur.execute(
            """
            SELECT
              (SELECT count(*) FROM tb.companies WHERE deleted_at IS NULL) AS companies,
              (SELECT count(*) FROM tb.users WHERE deleted_at IS NULL) AS users,
              (SELECT count(*) FROM tb.users WHERE deleted_at IS NULL AND status = 'ACTIVE') AS active_users,
              (SELECT count(*) FROM tb.devices WHERE status = 'ACTIVE' AND revoked_at IS NULL) AS active_devices,
              (SELECT count(*) FROM tb.worksites WHERE deleted_at IS NULL) AS worksites,
              (SELECT count(*) FROM tb.btbs WHERE status <> 'DELETED') AS btbs,
              (SELECT count(*) FROM tb.files WHERE deleted_at IS NULL) AS files,
              (SELECT coalesce(sum(byte_size),0) FROM tb.files WHERE deleted_at IS NULL) AS tracked_file_bytes,
              (SELECT coalesce(sum(byte_size),0) FROM tb.survey_originals) AS survey_original_bytes,
              (
                SELECT coalesce(sum(item.byte_size),0)
                FROM (
                  SELECT f.byte_size
                  FROM tb.files f
                  WHERE f.deleted_at IS NULL
                    AND NOT EXISTS (
                      SELECT 1
                      FROM tb.survey_originals s
                      WHERE s.storage_key = f.storage_key
                    )
                  UNION ALL
                  SELECT s.byte_size
                  FROM tb.survey_originals s
                ) item
              ) AS tracked_storage_bytes,
              (SELECT count(*) FROM tb.releases WHERE status <> 'RETIRED') AS releases,
              (SELECT count(*) FROM tb.survey_originals) AS survey_originals,
              (
                (SELECT count(*) FROM tb.companies WHERE deleted_at IS NOT NULL) +
                (SELECT count(*) FROM tb.users WHERE deleted_at IS NOT NULL) +
                (SELECT count(*) FROM tb.worksites WHERE deleted_at IS NOT NULL) +
                (SELECT count(*) FROM tb.files WHERE deleted_at IS NOT NULL) +
                (SELECT count(*) FROM tb.defects WHERE deleted_at IS NOT NULL) +
                (SELECT count(*) FROM tb.acceptances WHERE deleted_at IS NOT NULL)
              ) AS trash_items,
              (SELECT count(*) FROM tb.defects WHERE status NOT IN ('CLOSED','DELETED') AND deleted_at IS NULL) AS open_defects,
              (SELECT count(*) FROM tb.acceptances WHERE status NOT IN ('FINALIZED','DELETED') AND deleted_at IS NULL) AS open_acceptances,
              (SELECT count(*) FROM tb.background_jobs WHERE status = 'PENDING') AS pending_jobs,
              (SELECT count(*) FROM tb.background_jobs WHERE status = 'RUNNING') AS running_jobs,
              (SELECT count(*) FROM tb.background_jobs WHERE status = 'FAILED') AS failed_jobs,
              (SELECT max(created_at) FROM tb.background_jobs WHERE status = 'FAILED') AS last_failed_job_at,
              (SELECT max(last_seen_at) FROM tb.devices WHERE status = 'ACTIVE' AND revoked_at IS NULL) AS last_device_seen,
              (SELECT max(last_sync_at) FROM tb.devices WHERE status = 'ACTIVE' AND revoked_at IS NULL) AS last_sync,
              pg_database_size(current_database()) AS database_bytes
            """
        )
        counts = dict(cur.fetchone())
        for key in ("tracked_file_bytes", "survey_original_bytes", "tracked_storage_bytes"):
            counts[key] = int(counts.get(key) or 0)

        cur.execute(
            """
            WITH hours AS (
                SELECT generate_series(
                    date_trunc('hour', now()) - interval '23 hours',
                    date_trunc('hour', now()),
                    interval '1 hour'
                ) AS bucket_start
            )
            SELECT h.bucket_start,
                   coalesce(m.request_count, 0) AS request_count,
                   coalesce(m.error_count, 0) AS error_count,
                   coalesce(m.bytes_in, 0) AS bytes_in,
                   coalesce(m.bytes_out, 0) AS bytes_out
            FROM hours h
            LEFT JOIN tb.system_metrics_hourly m USING (bucket_start)
            ORDER BY h.bucket_start
            """
        )
        traffic_series = [dict(row) for row in cur.fetchall()]
        traffic_24h = {
            "request_count": sum(int(row["request_count"]) for row in traffic_series),
            "error_count": sum(int(row["error_count"]) for row in traffic_series),
            "bytes_in": sum(int(row["bytes_in"]) for row in traffic_series),
            "bytes_out": sum(int(row["bytes_out"]) for row in traffic_series),
        }
        traffic_24h["unacknowledged_error_count"] = sum(
            int(row["error_count"])
            for row in traffic_series
            if int(row["error_count"]) > 0
            and (
                warning_acknowledged_at is None
                or row["bucket_start"] > warning_acknowledged_at
            )
        )

        cur.execute(
            """
            SELECT id, kind, status, byte_size, sha256, started_at, finished_at
            FROM tb.backup_runs
            ORDER BY started_at DESC
            LIMIT 10
            """
        )
        backups = [dict(row) for row in cur.fetchall()]

        cur.execute(
            """
            SELECT id, kind, status, byte_size, started_at, finished_at
            FROM tb.backup_runs
            WHERE status = 'OK'
              AND kind IN ('DATABASE','FILES','FULL')
            ORDER BY coalesce(finished_at, started_at) DESC
            LIMIT 1
            """
        )
        last_successful_backup = cur.fetchone()
        last_successful_backup = (
            dict(last_successful_backup) if last_successful_backup else None
        )

        cur.execute(
            """
            SELECT id, job_type, status, attempts, run_after, created_at,
                   updated_at, finished_at, last_error,
                   progress_percent, progress_text
            FROM tb.background_jobs
            ORDER BY created_at DESC
            LIMIT 20
            """
        )
        jobs = [_job_view(dict(row)) for row in cur.fetchall()]

        cur.execute(
            """
            SELECT setting_key, value, updated_at
            FROM tb.server_settings
            WHERE setting_key = ANY(%s)
            ORDER BY setting_key
            """,
            (sorted(_SAFE_SETTING_KEYS),),
        )
        server_settings = {
            row["setting_key"]: {
                "value": row["value"],
                "updated_at": row["updated_at"],
            }
            for row in cur.fetchall()
        }

        cur.execute(
            """
            SELECT c.id, c.name,
                   (SELECT count(*) FROM tb.users u
                    WHERE u.company_id = c.id AND u.deleted_at IS NULL) AS user_count,
                   (SELECT count(*) FROM tb.devices d
                    JOIN tb.users u ON u.id = d.user_id
                    WHERE u.company_id = c.id
                      AND u.deleted_at IS NULL
                      AND d.status = 'ACTIVE'
                      AND d.revoked_at IS NULL) AS active_device_count,
                   (SELECT count(*) FROM tb.worksites w
                    WHERE w.company_id = c.id AND w.deleted_at IS NULL) AS worksite_count,
                   (SELECT count(*) FROM tb.files f
                    WHERE f.company_id = c.id AND f.deleted_at IS NULL) AS file_count,
                   (SELECT coalesce(sum(f.byte_size),0) FROM tb.files f
                    WHERE f.company_id = c.id AND f.deleted_at IS NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM tb.survey_originals s
                        WHERE s.storage_key = f.storage_key
                      )) AS file_bytes,
                   (SELECT coalesce(sum(f.byte_size),0) FROM tb.files f
                    WHERE f.company_id = c.id AND f.deleted_at IS NULL
                      AND lower(f.mime_type) LIKE 'image/%'
                      AND NOT EXISTS (
                        SELECT 1 FROM tb.survey_originals s
                        WHERE s.storage_key = f.storage_key
                      )) AS photo_bytes,
                   (SELECT coalesce(sum(f.byte_size),0) FROM tb.files f
                    WHERE f.company_id = c.id AND f.deleted_at IS NULL
                      AND lower(f.mime_type) = 'application/pdf'
                      AND NOT EXISTS (
                        SELECT 1 FROM tb.survey_originals s
                        WHERE s.storage_key = f.storage_key
                      )) AS pdf_bytes,
                   (SELECT coalesce(sum(f.byte_size),0) FROM tb.files f
                    WHERE f.company_id = c.id AND f.deleted_at IS NULL
                      AND lower(f.mime_type) NOT LIKE 'image/%'
                      AND lower(f.mime_type) <> 'application/pdf'
                      AND NOT EXISTS (
                        SELECT 1 FROM tb.survey_originals s
                        WHERE s.storage_key = f.storage_key
                      )) AS other_file_bytes,
                   (SELECT coalesce(sum(s.byte_size),0) FROM tb.survey_originals s
                    WHERE s.company_id = c.id) AS survey_bytes
            FROM tb.companies c
            WHERE c.deleted_at IS NULL
            ORDER BY lower(c.name)
            """
        )
        companies = [dict(row) for row in cur.fetchall()]
        for company in companies:
            for key in ("file_bytes", "photo_bytes", "pdf_bytes", "other_file_bytes", "survey_bytes"):
                company[key] = int(company.get(key) or 0)
            company["total_storage_bytes"] = company["file_bytes"] + company["survey_bytes"]

        cur.execute(
            """
            SELECT id, display_name, login_name, access_valid_until
            FROM tb.users
            WHERE deleted_at IS NULL
              AND status = 'ACTIVE'
              AND access_valid_until IS NOT NULL
              AND access_valid_until > now()
              AND access_valid_until <= %s
            ORDER BY access_valid_until, lower(display_name)
            LIMIT 100
            """,
            (warning_until,),
        )
        expiring_users = [dict(row) for row in cur.fetchall()]

        cur.execute(
            """
            SELECT a.id, a.action, a.entity_type, a.entity_id, a.created_at,
                   u.display_name AS actor_display_name
            FROM tb.audit_log a
            LEFT JOIN tb.users u ON u.id = a.actor_user_id
            ORDER BY a.created_at DESC
            LIMIT 30
            """
        )
        audit_events = [_audit_view(dict(row)) for row in cur.fetchall()]

        cur.execute(
            """
            SELECT id, status, attempts, run_after, created_at, updated_at, finished_at
            FROM tb.background_jobs
            WHERE job_type = 'SYSTEM_EMAIL_REPORT'
            ORDER BY created_at DESC
            LIMIT 1
            """
        )
        last_email_job = cur.fetchone()
        last_email_job = dict(last_email_job) if last_email_job else None

    now = status_now
    last_failed_job_at = _parse_timestamp(counts.get("last_failed_job_at"))
    failed_jobs_unacknowledged = int(counts["failed_jobs"] or 0)
    if (
        warning_acknowledged_at is not None
        and last_failed_job_at is not None
        and last_failed_job_at <= warning_acknowledged_at
    ):
        failed_jobs_unacknowledged = 0
    counts["failed_jobs_unacknowledged"] = failed_jobs_unacknowledged

    issues: list[dict[str, str]] = []

    def add_issue(severity: str, title: str, detail: str) -> None:
        issues.append(
            {
                "severity": severity,
                "title": title,
                "detail": detail,
            }
        )

    if migration["pending"]:
        add_issue(
            "critical",
            "Migrationen ausstehend",
            ", ".join(str(item) for item in migration["pending"]),
        )
    if not storage["available"]:
        add_issue(
            "critical",
            "Datenspeicher nicht verfügbar",
            "Der konfigurierte TB-Helfer-Speicherpfad ist nicht erreichbar.",
        )
    elif float(storage["percent_used"] or 0) >= 95.0:
        add_issue(
            "critical",
            "Speicher fast voll",
            f'{storage["percent_used"]:.2f} % des Datenträgers sind belegt.',
        )
    elif float(storage["percent_used"] or 0) >= float(automation["storage_warning_percent"]):
        add_issue(
            "warning",
            "Speicherbelegung hoch",
            f'{storage["percent_used"]:.2f} % des Datenträgers sind belegt.',
        )

    if failed_jobs_unacknowledged > 0:
        add_issue(
            "critical",
            "Fehlgeschlagene Vorgänge",
            f"{failed_jobs_unacknowledged} fehlgeschlagene Vorgänge sind noch nicht bereinigt oder quittiert.",
        )
    if int(traffic_24h["unacknowledged_error_count"] or 0) > 0:
        add_issue(
            "warning",
            "Serverfehler seit der letzten Quittierung",
            f'{traffic_24h["unacknowledged_error_count"]} HTTP-Antwort(en) mit Status 5xx sind seit der letzten Quittierung hinzugekommen.',
        )
    if not station_reference["available"]:
        add_issue(
            "warning",
            "Bahnhof-Referenzdaten fehlen",
            "Im API-Container fehlen: "
            + ", ".join(station_reference["missing"])
            + ". Die Kürzel-/Namenssuche ist dadurch nicht vollständig verfügbar.",
        )

    if not settings.public_base_url:
        add_issue(
            "warning",
            "Öffentliche Serveradresse fehlt",
            "Neue Geräte-Aktivierungskarten können ohne öffentliche HTTPS-Adresse nicht vollständig erzeugt werden.",
        )

    if (automation["email_enabled"] or automation.get("alert_email_enabled")) and not smtp["configured"]:
        add_issue(
            "warning",
            "E-Mail-Berichte nicht versandbereit",
            "E-Mail-Berichte sind aktiviert, aber die SMTP-Konfiguration ist unvollständig.",
        )
    if (automation["email_enabled"] or automation.get("alert_email_enabled")) and not automation["email_recipients"]:
        add_issue(
            "warning",
            "Kein E-Mail-Empfänger",
            "E-Mail-Berichte sind aktiviert, aber es ist kein Empfänger hinterlegt.",
        )
    if expiring_users:
        add_issue(
            "warning",
            "Nutzerzugänge laufen bald ab",
            f"{len(expiring_users)} Nutzerzugang/-zugänge laufen innerhalb des Warnzeitraums ab.",
        )

    if last_successful_backup is None:
        add_issue(
            "warning",
            "Noch kein erfolgreiches Backup",
            "Es wurde noch kein erfolgreicher Datenbank-/Datei-/Vollbackup-Lauf protokolliert.",
        )
    else:
        backup_time = (
            last_successful_backup.get("finished_at")
            or last_successful_backup.get("started_at")
        )
        if backup_time and (now - backup_time).total_seconds() > 36 * 3600:
            add_issue(
                "warning",
                "Backup älter als 36 Stunden",
                "Der letzte erfolgreiche Backup-Lauf ist älter als der vorgesehene Tagesrhythmus.",
            )

    if not issues:
        add_issue(
            "ok",
            "Keine aktuellen Probleme erkannt",
            "Datenbank, Migrationen, Speicher, Jobs und letzter Backup-Stand sind unauffällig.",
        )

    return {
        "server_version": SERVER_VERSION,
        "environment": settings.environment,
        "process": {
            "started_at": _PROCESS_STARTED_AT,
            "uptime_seconds": max(0, int(time.monotonic() - _PROCESS_STARTED_MONOTONIC)),
        },
        "public_base_url": settings.public_base_url,
        "migration": migration,
        "storage": storage,
        "backup_storage": backup_storage,
        "backup_retention": {
            "days": automation["backup_retention_days"],
            "minimum_kept_per_kind": automation["backup_retention_min_per_kind"],
        },
        "automation": automation,
        "smtp": smtp,
        "monitor": monitor,
        "host": host,
        "station_reference": station_reference,
        "counts": counts,
        "traffic_24h": traffic_24h,
        "traffic_series": traffic_series,
        "issues": issues,
        "warning_acknowledged_at": warning_acknowledged_at,
        "last_successful_backup": last_successful_backup,
        "backups": backups,
        "jobs": jobs,
        "settings": server_settings,
        "companies": companies,
        "expiring_users": expiring_users,
        "audit_events": audit_events,
        "last_email_job": last_email_job,
    }




@router.post("/warnings/acknowledge")
def acknowledge_system_warnings(
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    acknowledged_at = datetime.now(timezone.utc)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO tb.server_settings(setting_key, value, updated_at)
            VALUES (%s, %s, now())
            ON CONFLICT (setting_key) DO UPDATE
            SET value = EXCLUDED.value, updated_at = now()
            """,
            (_WARNING_ACK_KEY, Jsonb(acknowledged_at.isoformat())),
        )
        audit(
            cur,
            action="SYSTEM_WARNINGS_ACKNOWLEDGED",
            actor_user_id=user["user_id"],
            entity_type="SYSTEM",
            details={"acknowledged_at": acknowledged_at.isoformat()},
        )
    return {"acknowledged_at": acknowledged_at}


@router.delete("/jobs/history", status_code=204)
def delete_terminal_job_history(
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT count(*)::int AS count
            FROM tb.background_jobs
            WHERE status IN ('DONE','FAILED')
            """
        )
        count = int(cur.fetchone()["count"])
        cur.execute(
            "DELETE FROM tb.background_jobs WHERE status IN ('DONE','FAILED')"
        )
        audit(
            cur,
            action="SYSTEM_JOB_HISTORY_DELETE",
            actor_user_id=user["user_id"],
            entity_type="SYSTEM",
            details={"deleted_jobs": count},
        )
    return Response(status_code=204)


@router.delete("/jobs/{job_id}", status_code=204)
def delete_terminal_job(
    job_id: UUID,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, job_type, status
            FROM tb.background_jobs
            WHERE id = %s
            FOR UPDATE
            """,
            (job_id,),
        )
        job = cur.fetchone()
        if not job:
            raise HTTPException(404, "BACKGROUND_JOB_NOT_FOUND")
        if job["status"] not in {"DONE", "FAILED"}:
            raise HTTPException(409, "ACTIVE_BACKGROUND_JOB_CANNOT_BE_DELETED")
        audit(
            cur,
            action="SYSTEM_JOB_DELETE",
            actor_user_id=user["user_id"],
            entity_type="BACKGROUND_JOB",
            entity_id=job_id,
            details={"job_type": job["job_type"], "status": job["status"]},
        )
        cur.execute("DELETE FROM tb.background_jobs WHERE id = %s", (job_id,))
    return Response(status_code=204)


@router.put("/automation")
def update_automation_settings(
    payload: AutomationSettingsUpdate,
    user: dict[str, Any] = Depends(require_admin_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SYSTEM_AUTOMATION_SETTINGS_UPDATE",
        )
        if receipt:
            return replay_payload(receipt)
        try:
            policy = save_automation_policy(cur, payload.model_dump())
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc
        audit(
            cur,
            action="SYSTEM_AUTOMATION_SETTINGS_UPDATE",
            actor_user_id=user["user_id"],
            entity_type="SYSTEM",
            details={
                "email_enabled": policy["email_enabled"],
                "email_frequency": policy["email_frequency"],
                "alert_email_enabled": policy["alert_email_enabled"],
                "recipient_count": len(policy["email_recipients"]),
                "backup_schedules_enabled": [
                    kind for kind, schedule in policy["backup_schedules"].items()
                    if schedule.get("enabled")
                ],
                "automatic_retention_enabled": policy["retention_schedule"]["enabled"],
                "backup_retention_days": policy["backup_retention_days"],
            },
        )
        response = {"automation": policy, "smtp": smtp_status()}
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SYSTEM_AUTOMATION_SETTINGS_UPDATE",
            entity_id=None,
            result_version=None,
            response_payload=response,
        )
    return response


@router.post("/email/test", status_code=202)
def enqueue_test_email(
    user: dict[str, Any] = Depends(require_admin_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    policy = load_automation_policy()
    smtp = smtp_status()
    if not smtp["configured"]:
        raise HTTPException(409, "SMTP_NOT_CONFIGURED")
    if not policy["email_recipients"]:
        raise HTTPException(409, "EMAIL_RECIPIENTS_EMPTY")

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SYSTEM_EMAIL_TEST_JOB_CREATE",
        )
        if receipt:
            return replay_payload(receipt)
        try:
            job = enqueue_in_tx(cur, "SYSTEM_EMAIL_REPORT", {"test": True})
        except JobError as exc:
            raise HTTPException(400, str(exc)) from exc
        audit(
            cur,
            action="SYSTEM_EMAIL_TEST_JOB_CREATE",
            actor_user_id=user["user_id"],
            entity_type="BACKGROUND_JOB",
            entity_id=job["id"],
            details={"recipient_count": len(policy["email_recipients"])},
        )
        response = {"job": job}
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SYSTEM_EMAIL_TEST_JOB_CREATE",
            entity_id=job["id"],
            result_version=None,
            response_payload=response,
        )
    return response


@router.post("/backups", status_code=202)
def enqueue_backup(
    payload: BackupJobCreate,
    user: dict[str, Any] = Depends(require_admin_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    kind = payload.kind.strip().upper()
    mapping = {
        "DATABASE": "BACKUP_DATABASE",
        "FILES": "BACKUP_FILES",
        "FULL": "BACKUP_FULL",
    }
    job_type = mapping.get(kind)
    if job_type is None:
        raise HTTPException(400, "BACKUP_KIND_NOT_SUPPORTED")

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BACKUP_JOB_CREATE",
        )
        if receipt:
            return replay_payload(receipt)
        try:
            job = enqueue_in_tx(cur, job_type, {})
        except JobError as exc:
            raise HTTPException(400, str(exc)) from exc
        audit(
            cur,
            action="BACKUP_JOB_CREATE",
            actor_user_id=user["user_id"],
            entity_type="BACKGROUND_JOB",
            entity_id=job["id"],
            details={"kind": kind, "job_type": job_type},
        )
        response = {"job": job}
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BACKUP_JOB_CREATE",
            entity_id=job["id"],
            result_version=None,
            response_payload=response,
        )
    return response

@router.post("/backups/retention", status_code=202)
def enqueue_backup_retention(
    user: dict[str, Any] = Depends(require_admin_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BACKUP_RETENTION_JOB_CREATE",
        )
        if receipt:
            return replay_payload(receipt)
        try:
            job = enqueue_in_tx(cur, "BACKUP_RETENTION", {})
        except JobError as exc:
            raise HTTPException(400, str(exc)) from exc
        audit(
            cur,
            action="BACKUP_RETENTION_JOB_CREATE",
            actor_user_id=user["user_id"],
            entity_type="BACKGROUND_JOB",
            entity_id=job["id"],
            details={
                "retention_days": settings.backup_retention_days,
                "minimum_kept_per_kind": settings.backup_retention_min_per_kind,
            },
        )
        response = {"job": job}
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BACKUP_RETENTION_JOB_CREATE",
            entity_id=job["id"],
            result_version=None,
            response_payload=response,
        )
    return response


@router.post("/backups/{backup_run_id}/restore-test", status_code=202)
def enqueue_restore_test(
    backup_run_id: UUID,
    user: dict[str, Any] = Depends(require_admin_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="RESTORE_TEST_JOB_CREATE",
        )
        if receipt:
            return replay_payload(receipt)

        cur.execute(
            """
            SELECT id, kind, status
            FROM tb.backup_runs
            WHERE id = %s
            """,
            (backup_run_id,),
        )
        backup = cur.fetchone()
        if not backup:
            raise HTTPException(404, "BACKUP_RUN_NOT_FOUND")
        if backup["status"] != "OK":
            raise HTTPException(409, "BACKUP_RUN_NOT_READY")
        if backup["kind"] not in {"DATABASE", "FILES", "FULL"}:
            raise HTTPException(409, "RESTORE_TEST_SOURCE_KIND_NOT_SUPPORTED")

        try:
            job = enqueue_in_tx(
                cur,
                "RESTORE_TEST",
                {"backup_run_id": str(backup_run_id)},
            )
        except JobError as exc:
            raise HTTPException(400, str(exc)) from exc

        audit(
            cur,
            action="RESTORE_TEST_JOB_CREATE",
            actor_user_id=user["user_id"],
            entity_type="BACKGROUND_JOB",
            entity_id=job["id"],
            details={
                "source_backup_run_id": str(backup_run_id),
                "source_kind": backup["kind"],
            },
        )
        response = {"job": job}
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="RESTORE_TEST_JOB_CREATE",
            entity_id=job["id"],
            result_version=None,
            response_payload=response,
        )
    return response

