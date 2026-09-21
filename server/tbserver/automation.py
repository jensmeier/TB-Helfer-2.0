from __future__ import annotations

from datetime import datetime, timezone
from typing import Any
from zoneinfo import ZoneInfo

from psycopg.types.json import Jsonb

from .database import transaction
from .system_settings import load_automation_policy


def _enqueue(cur, job_type: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    cur.execute(
        """
        INSERT INTO tb.background_jobs(job_type, payload, status)
        VALUES (%s, %s, 'PENDING')
        RETURNING id, job_type, status, attempts, run_after, created_at
        """,
        (job_type, Jsonb(payload or {})),
    )
    return dict(cur.fetchone())


def _reserve_and_enqueue(
    cur,
    *,
    schedule_key: str,
    slot_key: str,
    scheduled_for: datetime,
    job_type: str,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any] | None:
    cur.execute(
        """
        INSERT INTO tb.system_schedule_runs(schedule_key, slot_key, scheduled_for)
        VALUES (%s, %s, %s)
        ON CONFLICT (schedule_key, slot_key) DO NOTHING
        RETURNING id
        """,
        (schedule_key, slot_key, scheduled_for),
    )
    slot = cur.fetchone()
    if not slot:
        return None
    job = _enqueue(cur, job_type, payload)
    cur.execute(
        "UPDATE tb.system_schedule_runs SET background_job_id=%s WHERE id=%s",
        (job["id"], slot["id"]),
    )
    return job


def _at_hour(local_now: datetime, hour: int) -> datetime:
    local_target = local_now.replace(hour=hour, minute=0, second=0, microsecond=0)
    return local_target.astimezone(timezone.utc)


def schedule_due_automation(now: datetime | None = None) -> dict[str, Any]:
    utc_now = now or datetime.now(timezone.utc)
    created: list[dict[str, Any]] = []
    with transaction() as conn, conn.cursor() as cur:
        policy = load_automation_policy(cur)
        local_now = utc_now.astimezone(ZoneInfo(policy["timezone"]))

        if policy["automatic_backup_enabled"] and local_now.hour >= policy["backup_hour"]:
            job_type = f"BACKUP_{policy['backup_kind']}"
            job = _reserve_and_enqueue(
                cur,
                schedule_key="AUTO_BACKUP",
                slot_key=local_now.strftime("%Y-%m-%d"),
                scheduled_for=_at_hour(local_now, policy["backup_hour"]),
                job_type=job_type,
            )
            if job:
                created.append(job)

        if policy["automatic_retention_enabled"] and local_now.hour >= policy["retention_hour"]:
            job = _reserve_and_enqueue(
                cur,
                schedule_key="AUTO_RETENTION",
                slot_key=local_now.strftime("%Y-%m-%d"),
                scheduled_for=_at_hour(local_now, policy["retention_hour"]),
                job_type="BACKUP_RETENTION",
            )
            if job:
                created.append(job)

        email_due = False
        email_slot = ""
        frequency = policy["email_frequency"]
        if policy["email_enabled"] and local_now.hour >= policy["email_hour"]:
            if frequency == "DAILY":
                email_due = True
                email_slot = local_now.strftime("%Y-%m-%d")
            elif frequency == "WEEKLY" and local_now.weekday() == policy["email_weekday"]:
                iso = local_now.isocalendar()
                email_due = True
                email_slot = f"{iso.year}-W{iso.week:02d}"
            elif frequency == "MONTHLY" and local_now.day == policy["email_monthday"]:
                email_due = True
                email_slot = local_now.strftime("%Y-%m")
        if email_due:
            job = _reserve_and_enqueue(
                cur,
                schedule_key=f"EMAIL_REPORT_{frequency}",
                slot_key=email_slot,
                scheduled_for=_at_hour(local_now, policy["email_hour"]),
                job_type="SYSTEM_EMAIL_REPORT",
                payload={"test": False},
            )
            if job:
                created.append(job)

    return {"created_count": len(created), "jobs": created}
