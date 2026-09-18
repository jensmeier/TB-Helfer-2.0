from __future__ import annotations

import argparse
import time
from typing import Any
from uuid import UUID

from psycopg.types.json import Jsonb

from .backup import run_backup, run_retention
from .database import transaction
from .restore import RestoreTestError, run_restore_test


MAX_JOB_ATTEMPTS = 5
STALE_RUNNING_MINUTES = 30
SUPPORTED_JOB_TYPES = {
    "BACKUP_DATABASE",
    "BACKUP_FILES",
    "BACKUP_FULL",
    "BACKUP_RETENTION",
    "RESTORE_TEST",
}


class JobError(RuntimeError):
    pass


def recover_stale_jobs() -> int:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = CASE WHEN attempts >= %s THEN 'FAILED' ELSE 'PENDING' END,
                run_after = CASE
                    WHEN attempts >= %s THEN run_after
                    ELSE now() + interval '1 minute'
                END,
                last_error = 'STALE_WORKER_RECOVERED',
                finished_at = CASE WHEN attempts >= %s THEN now() ELSE NULL END,
                updated_at = now()
            WHERE status = 'RUNNING'
              AND updated_at < now() - (%s * interval '1 minute')
            RETURNING id
            """,
            (
                MAX_JOB_ATTEMPTS,
                MAX_JOB_ATTEMPTS,
                MAX_JOB_ATTEMPTS,
                STALE_RUNNING_MINUTES,
            ),
        )
        return len(cur.fetchall())


def claim_job() -> dict[str, Any] | None:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id
            FROM tb.background_jobs
            WHERE status = 'PENDING'
              AND run_after <= now()
            ORDER BY run_after, created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """
        )
        candidate = cur.fetchone()
        if not candidate:
            return None
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = 'RUNNING',
                attempts = attempts + 1,
                last_error = NULL,
                finished_at = NULL,
                updated_at = now()
            WHERE id = %s
            RETURNING id, job_type, payload, status, attempts, run_after,
                      created_at, updated_at
            """,
            (candidate["id"],),
        )
        return dict(cur.fetchone())


def _execute(job: dict[str, Any]) -> dict[str, Any]:
    job_type = str(job["job_type"]).upper()
    if job_type == "BACKUP_DATABASE":
        return run_backup("DATABASE")
    if job_type == "BACKUP_FILES":
        return run_backup("FILES")
    if job_type == "BACKUP_FULL":
        return run_backup("FULL")
    if job_type == "BACKUP_RETENTION":
        return run_retention()
    if job_type == "RESTORE_TEST":
        raw_run_id = (job.get("payload") or {}).get("backup_run_id")
        try:
            source_run_id = UUID(str(raw_run_id))
        except (TypeError, ValueError) as exc:
            raise JobError("RESTORE_TEST_BACKUP_RUN_ID_INVALID") from exc
        try:
            return run_restore_test(source_run_id)
        except RestoreTestError as exc:
            raise JobError(str(exc)) from exc
    raise JobError("JOB_TYPE_NOT_SUPPORTED")


def _safe_error(exc: Exception) -> str:
    if isinstance(exc, JobError):
        return str(exc)[:500]
    name = type(exc).__name__
    message = str(exc)
    # BackupError/other controlled exceptions contain code-like text only.
    safe = message if message and len(message) <= 300 and "\n" not in message else name
    return f"{name}: {safe}"[:500]


def _mark_done(job_id: UUID) -> None:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = 'DONE', last_error = NULL, finished_at = now(),
                updated_at = now()
            WHERE id = %s AND status = 'RUNNING'
            """,
            (job_id,),
        )


def _mark_failed_or_retry(job: dict[str, Any], exc: Exception) -> None:
    attempts = int(job["attempts"])
    permanent = (
        attempts >= MAX_JOB_ATTEMPTS
        or isinstance(exc, JobError)
        and str(exc) == "JOB_TYPE_NOT_SUPPORTED"
    )
    backoff_minutes = min(60, 2 ** max(0, attempts - 1))
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = %s,
                run_after = CASE WHEN %s THEN run_after
                                 ELSE now() + (%s * interval '1 minute') END,
                last_error = %s,
                finished_at = CASE WHEN %s THEN now() ELSE NULL END,
                updated_at = now()
            WHERE id = %s AND status = 'RUNNING'
            """,
            (
                "FAILED" if permanent else "PENDING",
                permanent,
                backoff_minutes,
                _safe_error(exc),
                permanent,
                job["id"],
            ),
        )


def run_one() -> bool:
    job = claim_job()
    if job is None:
        return False
    try:
        _execute(job)
    except Exception as exc:
        _mark_failed_or_retry(job, exc)
    else:
        _mark_done(job["id"])
    return True


def worker_loop(*, once: bool = False, idle_seconds: float = 2.0) -> None:
    recover_stale_jobs()
    while True:
        worked = run_one()
        if once:
            return
        if not worked:
            time.sleep(max(0.2, idle_seconds))


def enqueue_in_tx(cur, job_type: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    normalized = job_type.strip().upper()
    if normalized not in SUPPORTED_JOB_TYPES:
        raise JobError("JOB_TYPE_NOT_SUPPORTED")
    cur.execute(
        """
        INSERT INTO tb.background_jobs(job_type, payload, status)
        VALUES (%s, %s, 'PENDING')
        RETURNING id, job_type, status, attempts, run_after, created_at
        """,
        (normalized, Jsonb(payload or {})),
    )
    return dict(cur.fetchone())


def enqueue(job_type: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        return enqueue_in_tx(cur, job_type, payload)


def main() -> None:
    parser = argparse.ArgumentParser(description="TB-Server 1.0 Background Job Worker")
    parser.add_argument("command", choices=("worker", "run-one"))
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--idle-seconds", type=float, default=2.0)
    args = parser.parse_args()
    if args.command == "run-one":
        recover_stale_jobs()
        run_one()
        return
    worker_loop(once=args.once, idle_seconds=args.idle_seconds)


if __name__ == "__main__":
    main()
