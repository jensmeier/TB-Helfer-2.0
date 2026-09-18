from __future__ import annotations

import shutil
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
            SELECT id, kind, status, byte_size, sha256, started_at, finished_at
            FROM tb.backup_runs
            ORDER BY started_at DESC
            LIMIT 10
            """
        )
        backups = [dict(row) for row in cur.fetchall()]

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

    return {
        "server_version": SERVER_VERSION,
        "environment": settings.environment,
        "migration": migration,
        "storage": storage,
        "backup_storage": backup_storage,
        "backup_retention": {
            "days": settings.backup_retention_days,
            "minimum_kept_per_kind": settings.backup_retention_min_per_kind,
        },
        "counts": counts,
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

