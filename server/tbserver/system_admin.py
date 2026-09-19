from __future__ import annotations

import shutil
import time
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from . import SERVER_VERSION
from .admin import require_admin, require_admin_csrf
from .backup import backup_storage_snapshot
from .config import settings
from .database import transaction
from .events import audit
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .jobs import JobError, enqueue_in_tx
from .migrations import status as migration_status


router = APIRouter(prefix="/api/v1/admin/system", tags=["admin-system"])

_PROCESS_STARTED_AT = datetime.now(timezone.utc)
_PROCESS_STARTED_MONOTONIC = time.monotonic()
_STORAGE_WARNING_PERCENT = 85.0

_SAFE_SETTING_KEYS = {
    "schema_baseline",
    "trash_retention_days",
    "capabilities_revision",
}


class BackupJobCreate(BaseModel):
    kind: str = Field(min_length=1, max_length=20)


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

    with transaction() as conn, conn.cursor() as cur:
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
                   updated_at, finished_at
            FROM tb.background_jobs
            ORDER BY created_at DESC
            LIMIT 20
            """
        )
        jobs = [dict(row) for row in cur.fetchall()]

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

    now = datetime.now(timezone.utc)
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
    elif float(storage["percent_used"] or 0) >= _STORAGE_WARNING_PERCENT:
        add_issue(
            "warning",
            "Speicherbelegung hoch",
            f'{storage["percent_used"]:.2f} % des Datenträgers sind belegt.',
        )

    if int(counts["failed_jobs"] or 0) > 0:
        add_issue(
            "critical",
            "Fehlgeschlagene Background Jobs",
            f'{counts["failed_jobs"]} Job(s) stehen auf FAILED.',
        )
    if int(traffic_24h["error_count"] or 0) > 0:
        add_issue(
            "warning",
            "Serverfehler in den letzten 24 Stunden",
            f'{traffic_24h["error_count"]} HTTP-Antwort(en) hatten Status 5xx.',
        )
    if not settings.public_base_url:
        add_issue(
            "warning",
            "Öffentliche Serveradresse fehlt",
            "Neue Geräte-Aktivierungskarten können ohne öffentliche HTTPS-Adresse nicht vollständig erzeugt werden.",
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
            "days": settings.backup_retention_days,
            "minimum_kept_per_kind": settings.backup_retention_min_per_kind,
        },
        "counts": counts,
        "traffic_24h": traffic_24h,
        "traffic_series": traffic_series,
        "issues": issues,
        "last_successful_backup": last_successful_backup,
        "backups": backups,
        "jobs": jobs,
        "settings": server_settings,
        "companies": companies,
    }


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

