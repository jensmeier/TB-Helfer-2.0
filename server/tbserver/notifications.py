from __future__ import annotations

import smtplib
import ssl
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from typing import Any
from uuid import UUID
from zoneinfo import ZoneInfo

from .config import settings
from .database import transaction
from .events import audit
from .system_settings import load_automation_policy, smtp_status


class NotificationError(RuntimeError):
    pass


def _bytes(value: int | None) -> str:
    if value is None:
        return "unbekannt"
    amount = float(value)
    units = ["B", "KiB", "MiB", "GiB", "TiB"]
    index = 0
    while amount >= 1024 and index < len(units) - 1:
        amount /= 1024
        index += 1
    digits = 0 if index == 0 or amount >= 100 else 1 if amount >= 10 else 2
    return f"{amount:.{digits}f} {units[index]}"


def _report_data(policy: dict[str, Any]) -> dict[str, Any]:
    warning_until = datetime.now(timezone.utc) + timedelta(
        days=int(policy["user_expiry_warning_days"])
    )
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT kind, status, byte_size, started_at, finished_at
            FROM tb.backup_runs
            WHERE kind IN ('DATABASE','FILES','FULL')
            ORDER BY coalesce(finished_at, started_at) DESC
            LIMIT 1
            """
        )
        last_backup = cur.fetchone()
        cur.execute(
            "SELECT count(*) AS value FROM tb.background_jobs WHERE status='FAILED'"
        )
        failed_jobs = int(cur.fetchone()["value"] or 0)
        cur.execute(
            """
            SELECT display_name, login_name, access_valid_until
            FROM tb.users
            WHERE deleted_at IS NULL
              AND status='ACTIVE'
              AND access_valid_until IS NOT NULL
              AND access_valid_until <= %s
            ORDER BY access_valid_until, lower(display_name)
            LIMIT 50
            """,
            (warning_until,),
        )
        expiring_users = [dict(row) for row in cur.fetchall()]
    storage = settings.storage_root
    if storage.exists():
        import shutil

        usage = shutil.disk_usage(storage)
        storage_info = {
            "used": usage.used,
            "free": usage.free,
            "total": usage.total,
            "percent": round((usage.used / usage.total) * 100.0, 2) if usage.total else 0.0,
        }
    else:
        storage_info = None
    try:
        from .monitor import read_monitor_snapshot

        monitor = read_monitor_snapshot(event_limit=10)
    except Exception:
        monitor = {"available": False, "overall": "UNKNOWN", "events_24h": {}}
    return {
        "last_backup": dict(last_backup) if last_backup else None,
        "failed_jobs": failed_jobs,
        "expiring_users": expiring_users,
        "storage": storage_info,
        "monitor": monitor,
    }


def _build_report(policy: dict[str, Any], *, test: bool) -> tuple[str, str]:
    data = _report_data(policy)
    local_now = datetime.now(timezone.utc).astimezone(ZoneInfo(policy["timezone"]))
    subject = "TB-Helfer Server 1.0 – Testbericht" if test else "TB-Helfer Server 1.0 – Systembericht"
    lines = [
        "TB-Helfer Server 1.0",
        f"Bericht: {local_now.strftime('%d.%m.%Y %H:%M %Z')}",
        "",
    ]
    backup = data["last_backup"]
    if backup:
        stamp = backup.get("finished_at") or backup.get("started_at")
        lines.append(
            f"Letztes Backup: {backup['kind']} / {backup['status']} / {_bytes(backup.get('byte_size'))} / {stamp}"
        )
    else:
        lines.append("Letztes Backup: keines")
    storage = data["storage"]
    if storage:
        lines.append(
            f"Speicher: {storage['percent']:.2f} % belegt / {_bytes(storage['free'])} frei / {_bytes(storage['total'])} gesamt"
        )
    else:
        lines.append("Speicher: nicht verfügbar")
    lines.append(f"Fehlgeschlagene Background Jobs: {data['failed_jobs']}")
    monitor = data.get("monitor") or {}
    if monitor.get("available"):
        events = monitor.get("events_24h") or {}
        lines.append(
            "Servermonitor: "
            + str(monitor.get("overall") or "UNKNOWN")
            + f" / letzte 24 h {int(events.get('error') or 0)} Fehler, {int(events.get('warn') or 0)} Warnungen"
        )
    else:
        lines.append("Servermonitor: noch keine Daten verfügbar")
    lines.append("")
    users = data["expiring_users"]
    lines.append(
        f"Nutzer mit Ablauf innerhalb {policy['user_expiry_warning_days']} Tagen: {len(users)}"
    )
    for user in users:
        lines.append(
            f"- {user['display_name']} ({user['login_name']}): {user['access_valid_until']}"
        )
    if test:
        lines.extend(["", "Dies ist ein manueller SMTP-Testbericht aus der Server-1.0-Administration."])
    lines.extend(["", "Dieser Bericht enthält keine Passwörter, Tokens oder Aktivierungs-Keys."])
    return subject, "\n".join(lines)


def _send(subject: str, body: str, recipients: list[str]) -> None:
    status = smtp_status()
    if not status["configured"]:
        raise NotificationError("SMTP_NOT_CONFIGURED")
    if not recipients:
        raise NotificationError("EMAIL_RECIPIENTS_EMPTY")

    message = EmailMessage()
    message["Subject"] = subject
    message["From"] = str(settings.smtp_sender)
    message["To"] = ", ".join(recipients)
    message.set_content(body)

    try:
        if settings.smtp_ssl:
            client = smtplib.SMTP_SSL(
                str(settings.smtp_host), settings.smtp_port, timeout=30, context=ssl.create_default_context()
            )
        else:
            client = smtplib.SMTP(str(settings.smtp_host), settings.smtp_port, timeout=30)
        with client:
            client.ehlo()
            if settings.smtp_starttls and not settings.smtp_ssl:
                client.starttls(context=ssl.create_default_context())
                client.ehlo()
            if settings.smtp_user:
                if not settings.smtp_password:
                    raise NotificationError("SMTP_PASSWORD_MISSING")
                client.login(settings.smtp_user, settings.smtp_password)
            client.send_message(message)
    except NotificationError:
        raise
    except Exception as exc:
        raise NotificationError(f"SMTP_SEND_FAILED_{type(exc).__name__}") from exc


def send_system_message(subject: str, body: str, recipients: list[str]) -> None:
    _send(subject, body, recipients)


def run_system_email_report(*, test: bool = False) -> dict[str, Any]:
    policy = load_automation_policy()
    recipients = list(policy["email_recipients"])
    if not test and not policy["email_enabled"]:
        return {"sent": False, "skipped": "EMAIL_REPORTS_DISABLED", "test": False}
    if not test and not recipients:
        return {"sent": False, "skipped": "EMAIL_RECIPIENTS_EMPTY", "test": False}
    subject, body = _build_report(policy, test=test)
    _send(subject, body, recipients)
    with transaction() as conn, conn.cursor() as cur:
        audit(
            cur,
            action="SYSTEM_EMAIL_TEST_SENT" if test else "SYSTEM_EMAIL_REPORT_SENT",
            actor_user_id=None,
            entity_type="SYSTEM",
            details={"recipient_count": len(recipients), "test": test},
        )
    return {"sent": True, "recipient_count": len(recipients), "test": test}


def run_survey_conflict_email(conflict_id: UUID) -> dict[str, Any]:
    policy = load_automation_policy()
    if not policy.get("alert_email_enabled"):
        return {"sent": False, "skipped": "ALERT_EMAIL_DISABLED", "conflict_id": str(conflict_id)}
    recipients = list(policy.get("email_recipients") or [])
    if not recipients:
        return {"sent": False, "skipped": "EMAIL_RECIPIENTS_EMPTY", "conflict_id": str(conflict_id)}

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT c.id, c.status, c.conflict_type, c.details, c.created_at, c.notified_at,
                   a.route_number, a.station_code, a.station_name, a.track_key,
                   a.direction, a.axis_type, a.display_name
            FROM tb.survey_catalog_conflicts c
            JOIN tb.survey_catalog_axes a ON a.id = c.axis_id
            WHERE c.id = %s
            """,
            (conflict_id,),
        )
        row = cur.fetchone()
        if not row:
            return {"sent": False, "skipped": "SURVEY_CONFLICT_NOT_FOUND", "conflict_id": str(conflict_id)}
        conflict = dict(row)
        if conflict["status"] != "OPEN":
            return {"sent": False, "skipped": "SURVEY_CONFLICT_NOT_OPEN", "conflict_id": str(conflict_id)}
        if conflict["notified_at"] is not None:
            return {"sent": False, "skipped": "SURVEY_CONFLICT_ALREADY_NOTIFIED", "conflict_id": str(conflict_id)}

    location = " / ".join(
        value
        for value in (
            f"Str {conflict['route_number']}" if conflict.get("route_number") else None,
            (
                f"{conflict.get('station_code')} – {conflict.get('station_name')}"
                if conflict.get("station_code") and conflict.get("station_name")
                else conflict.get("station_name") or conflict.get("station_code")
            ),
            f"Gl {conflict['track_key']}" if conflict.get("track_key") else None,
        )
        if value
    ) or "ohne eindeutige Strecken-/Betriebsstellenzuordnung"
    subject = f"TB-Helfer: Trassendaten-Prüffall {location}"[:240]
    body = "\n".join(
        [
            "TB-Helfer hat einen Trassendaten-Prüffall erkannt.",
            "",
            f"Bereich: {location}",
            f"Achse: {conflict.get('display_name') or '-'}",
            f"Konfliktart: {conflict.get('conflict_type') or '-'}",
            f"Prüffall-ID: {conflict_id}",
            f"Erstellt: {conflict.get('created_at')}",
            "",
            "Die bestehende aktive Geometrie wurde nicht still überschrieben.",
            "Bitte den Prüffall später in der Admin-Trassendatenübersicht entscheiden.",
            "",
            "Diese Nachricht enthält keine Passwörter, Tokens oder Aktivierungs-Keys.",
        ]
    )
    _send(subject, body, recipients)

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            UPDATE tb.survey_catalog_conflicts
            SET notified_at = now()
            WHERE id = %s AND status = 'OPEN' AND notified_at IS NULL
            """,
            (conflict_id,),
        )
        audit(
            cur,
            action="SURVEY_CATALOG_CONFLICT_EMAIL_SENT",
            actor_user_id=None,
            entity_type="SURVEY_CATALOG_CONFLICT",
            entity_id=conflict_id,
            details={"recipient_count": len(recipients)},
        )
    return {"sent": True, "recipient_count": len(recipients), "conflict_id": str(conflict_id)}
