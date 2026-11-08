from __future__ import annotations

import argparse
import logging
import threading
import time
from typing import Any
from uuid import UUID

from psycopg.types.json import Jsonb

from .automation import schedule_due_automation
from .backup import run_backup, run_retention
from .database import transaction
from .notifications import NotificationError, run_survey_conflict_email, run_system_email_report
from .restore import RestoreTestError, run_restore_test
from .monitor import write_worker_heartbeat


MAX_JOB_ATTEMPTS = 5
STALE_RUNNING_MINUTES = 30
SCHEDULE_CHECK_SECONDS = 30.0
STALE_RECOVERY_CHECK_SECONDS = 60.0
WORKER_ERROR_RETRY_SECONDS = 5.0
JOB_STATE_UPDATE_ATTEMPTS = 5
logger = logging.getLogger(__name__)


def _set_progress(job_id: UUID, percent: int, text: str) -> None:
    safe_percent = max(0, min(100, int(percent)))
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET progress_percent = %s,
                progress_text = %s,
                updated_at = now()
            WHERE id = %s AND status = 'RUNNING'
            """,
            (safe_percent, str(text)[:200], job_id),
        )


SUPPORTED_JOB_TYPES = {
    "BACKUP_DATABASE",
    "BACKUP_FILES",
    "BACKUP_FULL",
    "BACKUP_USER",
    "BACKUP_TRACK",
    "BACKUP_SYSTEM",
    "BACKUP_RETENTION",
    "RESTORE_TEST",
    "RECOVERY_PREPARE",
    "SYSTEM_EMAIL_REPORT",
    "SURVEY_CONFLICT_EMAIL",
    "SURVEY_CATALOG_REINDEX",
}


class JobError(RuntimeError):
    pass


def recover_stale_jobs() -> int:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = CASE WHEN attempts >= %s OR job_type='RECOVERY_PREPARE' THEN 'FAILED' ELSE 'PENDING' END,
                progress_percent = CASE WHEN attempts >= %s OR job_type='RECOVERY_PREPARE' THEN progress_percent ELSE 0 END,
                progress_text = CASE WHEN attempts >= %s OR job_type='RECOVERY_PREPARE' THEN 'Fehlgeschlagen' ELSE 'Neuer Versuch geplant' END,
                run_after = CASE
                    WHEN attempts >= %s OR job_type='RECOVERY_PREPARE' THEN run_after
                    ELSE now() + interval '1 minute'
                END,
                last_error = 'STALE_WORKER_RECOVERED',
                finished_at = CASE WHEN attempts >= %s OR job_type='RECOVERY_PREPARE' THEN now() ELSE NULL END,
                updated_at = now()
            WHERE status = 'RUNNING'
              AND updated_at < now() - (%s * interval '1 minute')
            RETURNING id
            """,
            (
                MAX_JOB_ATTEMPTS,
                MAX_JOB_ATTEMPTS,
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
                progress_percent = 5,
                progress_text = 'Wird gestartet',
                last_error = NULL,
                finished_at = NULL,
                updated_at = now()
            WHERE id = %s
            RETURNING id, job_type, payload, status, attempts, run_after,
                      progress_percent, progress_text, created_at, updated_at
            """,
            (candidate["id"],),
        )
        return dict(cur.fetchone())


def _execute(job: dict[str, Any]) -> dict[str, Any]:
    job_type = str(job["job_type"]).upper()
    job_id = UUID(str(job["id"]))
    progress = lambda percent, text: _set_progress(job_id, percent, text)
    if job_type == "RECOVERY_PREPARE":
        from .recovery import prepare_recovery
        return prepare_recovery(job, progress)
    if job_type == "BACKUP_DATABASE":
        return run_backup("DATABASE", progress=progress)
    if job_type == "BACKUP_FILES":
        return run_backup("FILES", progress=progress)
    if job_type == "BACKUP_FULL":
        return run_backup("FULL", progress=progress)
    if job_type in {"BACKUP_USER", "BACKUP_TRACK", "BACKUP_SYSTEM"}:
        return run_backup(job_type.removeprefix('BACKUP_'), progress=progress)
    if job_type == "BACKUP_RETENTION":
        progress(35, "Backup-Aufbewahrung wird geprüft")
        return run_retention()
    if job_type == "SYSTEM_EMAIL_REPORT":
        test = bool((job.get("payload") or {}).get("test", False))
        progress(30, "Systembericht wird erstellt")
        try:
            result = run_system_email_report(test=test)
            progress(90, "E-Mail-Bericht wurde versendet")
            return result
        except NotificationError as exc:
            raise JobError(str(exc)) from exc
    if job_type == "SURVEY_CONFLICT_EMAIL":
        raw_conflict_id = (job.get("payload") or {}).get("conflict_id")
        try:
            conflict_id = UUID(str(raw_conflict_id))
        except (TypeError, ValueError) as exc:
            raise JobError("SURVEY_CONFLICT_ID_INVALID") from exc
        progress(30, "Trassendaten-Prüffall wird für E-Mail vorbereitet")
        try:
            result = run_survey_conflict_email(conflict_id)
            progress(90, "Trassendaten-Prüffall wurde verarbeitet")
            return result
        except NotificationError as exc:
            raise JobError(str(exc)) from exc
    if job_type == "SURVEY_CATALOG_REINDEX":
        progress(20, "Trassendaten-Katalog wird neu aufgebaut")
        from .survey_catalog import reindex_all_originals

        result = reindex_all_originals()
        progress(95, "Trassendaten-Katalog wurde neu aufgebaut")
        return result
    if job_type == "RESTORE_TEST":
        raw_run_id = (job.get("payload") or {}).get("backup_run_id")
        try:
            source_run_id = UUID(str(raw_run_id))
        except (TypeError, ValueError) as exc:
            raise JobError("RESTORE_TEST_BACKUP_RUN_ID_INVALID") from exc
        try:
            return run_restore_test(source_run_id, progress=progress)
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


def _retry_job_state_update(label: str, action) -> None:
    """
    Jobstatus-Aktualisierungen dürfen den dauerhaften Worker nicht bei einem kurzen
    PostgreSQL-Aussetzer beenden. Nach wenigen lokalen Retries übernimmt die äußere
    Worker-Schleife; ein liegen gebliebener RUNNING-Job wird anschließend über die
    periodische Stale-Recovery wieder eingeordnet.
    """
    last_error: Exception | None = None
    for attempt in range(1, JOB_STATE_UPDATE_ATTEMPTS + 1):
        try:
            action()
            return
        except Exception as exc:
            last_error = exc
            if attempt >= JOB_STATE_UPDATE_ATTEMPTS:
                break
            delay = min(
                WORKER_ERROR_RETRY_SECONDS,
                max(0.5, float(attempt)),
            )
            logger.warning(
                "worker %s failed (%s), retry %s/%s in %.1fs",
                label,
                type(exc).__name__,
                attempt + 1,
                JOB_STATE_UPDATE_ATTEMPTS,
                delay,
            )
            time.sleep(delay)
    assert last_error is not None
    raise last_error


def _mark_done(job_id: UUID) -> None:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = 'DONE', last_error = NULL, finished_at = now(),
                progress_percent = 100, progress_text = 'Fertig',
                updated_at = now()
            WHERE id = %s AND status = 'RUNNING'
            """,
            (job_id,),
        )


def _mark_failed_or_retry(job: dict[str, Any], exc: Exception) -> None:
    attempts = int(job["attempts"])
    permanent = (
        attempts >= MAX_JOB_ATTEMPTS
        or job.get("job_type") == "RECOVERY_PREPARE"
        or isinstance(exc, JobError)
        and str(exc) == "JOB_TYPE_NOT_SUPPORTED"
    )
    backoff_minutes = min(60, 2 ** max(0, attempts - 1))
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.background_jobs
            SET status = %s,
                progress_percent = CASE WHEN %s THEN progress_percent ELSE 0 END,
                progress_text = CASE WHEN %s THEN 'Fehlgeschlagen' ELSE 'Neuer Versuch geplant' END,
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
                permanent,
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
        _retry_job_state_update(
            "job failure state update",
            lambda: _mark_failed_or_retry(job, exc),
        )
    else:
        _retry_job_state_update(
            "job completion state update",
            lambda: _mark_done(job["id"]),
        )
    return True


def _worker_heartbeat_loop() -> None:
    while True:
        write_worker_heartbeat()
        time.sleep(10.0)


def worker_loop(*, once: bool = False, idle_seconds: float = 2.0) -> None:
    threading.Thread(
        target=_worker_heartbeat_loop,
        name="tb-worker-heartbeat",
        daemon=True,
    ).start()

    # depends_on/DB-health reduziert Startprobleme, schützt aber nicht vor einem kurzen
    # PostgreSQL-Neustart direkt danach. Der Langzeit-Worker wartet deshalb selbst, statt
    # wegen eines einzelnen Verbindungsfehlers den kompletten Containerprozess zu beenden.
    while True:
        try:
            recover_stale_jobs()
            break
        except Exception as exc:
            logger.exception(
                "worker startup database recovery failed; worker stays alive: %s",
                type(exc).__name__,
            )
            if once:
                raise
            time.sleep(WORKER_ERROR_RETRY_SECONDS)

    next_schedule_check = 0.0
    next_stale_recovery_check = time.monotonic() + STALE_RECOVERY_CHECK_SECONDS
    while True:
        now = time.monotonic()
        if now >= next_schedule_check:
            try:
                schedule_due_automation()
                from .btb_deadlines import finalize_due_btbs
                finalize_due_btbs()
            except Exception as exc:
                logger.exception(
                    "system automation scheduler failed; worker stays alive: %s",
                    type(exc).__name__,
                )
            next_schedule_check = now + SCHEDULE_CHECK_SECONDS

        if now >= next_stale_recovery_check:
            try:
                recovered = recover_stale_jobs()
                if recovered:
                    logger.warning("worker recovered %s stale background job(s)", recovered)
            except Exception as exc:
                logger.exception(
                    "worker stale-job recovery failed; worker stays alive: %s",
                    type(exc).__name__,
                )
            next_stale_recovery_check = now + STALE_RECOVERY_CHECK_SECONDS

        try:
            worked = run_one()
        except Exception as exc:
            logger.exception(
                "worker loop iteration failed; worker stays alive and retries: %s",
                type(exc).__name__,
            )
            if once:
                raise
            time.sleep(WORKER_ERROR_RETRY_SECONDS)
            continue

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
        INSERT INTO tb.background_jobs(
            job_type, payload, status, progress_percent, progress_text
        )
        VALUES (%s, %s, 'PENDING', 0, 'Wartet')
        RETURNING id, job_type, status, attempts, run_after,
                  progress_percent, progress_text, created_at
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
