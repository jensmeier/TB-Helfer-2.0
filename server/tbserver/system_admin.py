from __future__ import annotations

import shutil
import time
from datetime import datetime, timedelta, timezone
from typing import Any
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
from .system_settings import load_automation_policy, save_automation_policy, smtp_status


router = APIRouter(prefix="/api/v1/admin/system", tags=["admin-system"])

_PROCESS_STARTED_AT = datetime.now(timezone.utc)
_PROCESS_STARTED_MONOTONIC = time.monotonic()
_SAFE_SETTING_KEYS = {
    "schema_baseline",
    "trash_retention_days",
    "capabilities_revision",
}


class BackupJobCreate(BaseModel):
    kind: str = Field(min_length=1, max_length=20)


class AutomationSettingsUpdate(BaseModel):
    email_enabled: bool = False
    email_frequency: str = Field(default="WEEKLY", min_length=1, max_length=20)
    email_hour: int = 8
    email_weekday: int = 0
    email_monthday: int = 1
    email_recipients: list[str] = Field(default_factory=list, max_length=20)
    timezone: str = Field(default="Europe/Berlin", min_length=1, max_length=100)
    user_expiry_warning_days: int = 30
    storage_warning_percent: float = 85.0
    automatic_backup_enabled: bool = False
    backup_kind: str = Field(default="DATABASE", min_length=1, max_length=20)
    backup_hour: int = 4
    automatic_retention_enabled: bool = False
    retention_hour: int = 5
    backup_retention_days: int = 30
    backup_retention_min_per_kind: int = 3


_WARNING_ACK_KEY = "system_warning_acknowledged_at"

_JOB_LABELS = {
    "BACKUP_DATABASE": "Datenbank-Backup",
    "BACKUP_FILES": "Datei-Backup",
    "BACKUP_FULL": "Vollbackup",
    "BACKUP_RETENTION": "Backup-Aufbewahrung",
    "RESTORE_TEST": "Restore-Test",
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
}
_AUDIT_LABELS = {
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
    return "Technischer Fehler: " + raw[:240]


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


@router.get("/status")
def admin_system_status(
    _: dict[str, Any] = Depends(require_admin),
) -> dict:
    migration = migration_status()
    storage = _storage_snapshot()
    backup_storage = backup_storage_snapshot()
    automation = load_automation_policy()
    smtp = smtp_status()
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
                    WHERE f.company_id = c.id AND f.deleted_at IS NULL) AS file_bytes
            FROM tb.companies c
            WHERE c.deleted_at IS NULL
            ORDER BY lower(c.name)
            """
        )
        companies = [dict(row) for row in cur.fetchall()]

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
    if not settings.public_base_url:
        add_issue(
            "warning",
            "Öffentliche Serveradresse fehlt",
            "Neue Geräte-Aktivierungskarten können ohne öffentliche HTTPS-Adresse nicht vollständig erzeugt werden.",
        )

    if automation["email_enabled"] and not smtp["configured"]:
        add_issue(
            "warning",
            "E-Mail-Berichte nicht versandbereit",
            "E-Mail-Berichte sind aktiviert, aber die SMTP-Konfiguration ist unvollständig.",
        )
    if automation["email_enabled"] and not automation["email_recipients"]:
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
                "recipient_count": len(policy["email_recipients"]),
                "automatic_backup_enabled": policy["automatic_backup_enabled"],
                "backup_kind": policy["backup_kind"],
                "automatic_retention_enabled": policy["automatic_retention_enabled"],
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

