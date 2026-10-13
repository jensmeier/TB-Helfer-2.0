from __future__ import annotations

from datetime import datetime, timedelta, timezone
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


def _period_schedule(local_now: datetime, schedule: dict[str, Any]) -> tuple[str, datetime] | None:
    if not schedule.get("enabled"):
        return None
    frequency = str(schedule.get("frequency") or "DAILY").upper()
    hour = int(schedule.get("hour", 0))
    minute = int(schedule.get("minute", 0))

    if frequency == "DAILY":
        local_target = local_now.replace(hour=hour, minute=minute, second=0, microsecond=0)
        return local_now.strftime("%Y-%m-%d"), local_target

    if frequency == "WEEKLY":
        weekday = int(schedule.get("weekday", 0))
        start_of_week = local_now - timedelta(days=local_now.weekday())
        local_target = start_of_week.replace(
            hour=hour, minute=minute, second=0, microsecond=0
        ) + timedelta(days=weekday)
        iso = local_now.isocalendar()
        return f"{iso.year}-W{iso.week:02d}", local_target

    if frequency == "MONTHLY":
        monthday = int(schedule.get("monthday", 1))
        local_target = local_now.replace(
            day=monthday, hour=hour, minute=minute, second=0, microsecond=0
        )
        return local_now.strftime("%Y-%m"), local_target

    return None


def _schedule_if_due(
    cur,
    *,
    local_now: datetime,
    schedule_key: str,
    schedule: dict[str, Any],
    job_type: str,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any] | None:
    resolved = _period_schedule(local_now, schedule)
    if resolved is None:
        return None
    slot_key, local_target = resolved
    if local_now < local_target:
        return None
    return _reserve_and_enqueue(
        cur,
        schedule_key=schedule_key,
        slot_key=slot_key,
        scheduled_for=local_target.astimezone(timezone.utc),
        job_type=job_type,
        payload=payload,
    )


def schedule_due_automation(now: datetime | None = None) -> dict[str, Any]:
    utc_now = now or datetime.now(timezone.utc)
    created: list[dict[str, Any]] = []
    with transaction() as conn, conn.cursor() as cur:
        policy = load_automation_policy(cur)
        local_now = utc_now.astimezone(ZoneInfo(policy["timezone"]))

        for kind, schedule in policy["backup_schedules"].items():
            job = _schedule_if_due(
                cur,
                local_now=local_now,
                schedule_key=f"AUTO_BACKUP_{kind}",
                schedule=schedule,
                job_type=f"BACKUP_{kind}",
            )
            if job:
                created.append(job)

        job = _schedule_if_due(
            cur,
            local_now=local_now,
            schedule_key="AUTO_RETENTION",
            schedule=policy["retention_schedule"],
            job_type="BACKUP_RETENTION",
        )
        if job:
            created.append(job)

        email_schedule = {
            "enabled": policy["email_enabled"],
            "frequency": policy["email_frequency"],
            "hour": policy["email_hour"],
            "minute": policy["email_minute"],
            "weekday": policy["email_weekday"],
            "monthday": policy["email_monthday"],
        }
        job = _schedule_if_due(
            cur,
            local_now=local_now,
            schedule_key=f"EMAIL_REPORT_{policy['email_frequency']}",
            schedule=email_schedule,
            job_type="SYSTEM_EMAIL_REPORT",
            payload={"test": False},
        )
        if job:
            created.append(job)

    return {"created_count": len(created), "jobs": created}
