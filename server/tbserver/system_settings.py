from __future__ import annotations

from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from psycopg.types.json import Jsonb

from .config import settings
from .database import transaction


AUTOMATION_SETTING_KEY = "admin_automation"
_EMAIL_FREQUENCIES = {"DAILY", "WEEKLY", "MONTHLY"}
_BACKUP_KINDS = {"DATABASE", "FILES", "FULL"}


def default_automation_policy() -> dict[str, Any]:
    return {
        "email_enabled": False,
        "email_frequency": "WEEKLY",
        "email_hour": 8,
        "email_weekday": 0,
        "email_monthday": 1,
        "email_recipients": [],
        "timezone": "Europe/Berlin",
        "user_expiry_warning_days": 30,
        "storage_warning_percent": 85.0,
        "automatic_backup_enabled": False,
        "backup_kind": "DATABASE",
        "backup_hour": 4,
        "automatic_retention_enabled": False,
        "retention_hour": 5,
        "backup_retention_days": settings.backup_retention_days,
        "backup_retention_min_per_kind": settings.backup_retention_min_per_kind,
    }


def _int(value: Any, name: str, minimum: int, maximum: int) -> int:
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{name}_INVALID") from exc
    if result < minimum or result > maximum:
        raise ValueError(f"{name}_OUT_OF_RANGE")
    return result


def _float(value: Any, name: str, minimum: float, maximum: float) -> float:
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{name}_INVALID") from exc
    if result < minimum or result > maximum:
        raise ValueError(f"{name}_OUT_OF_RANGE")
    return round(result, 2)


def _recipients(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        raw_values = value.replace(";", ",").split(",")
    elif isinstance(value, list):
        raw_values = value
    else:
        raise ValueError("EMAIL_RECIPIENTS_INVALID")
    result: list[str] = []
    seen: set[str] = set()
    for raw in raw_values:
        address = str(raw).strip()
        if not address:
            continue
        if len(address) > 320 or "@" not in address or any(ch.isspace() for ch in address):
            raise ValueError("EMAIL_RECIPIENT_INVALID")
        normalized = address.lower()
        if normalized not in seen:
            seen.add(normalized)
            result.append(address)
    if len(result) > 20:
        raise ValueError("EMAIL_RECIPIENTS_TOO_MANY")
    return result


def normalize_automation_policy(raw: dict[str, Any] | None) -> dict[str, Any]:
    merged = default_automation_policy()
    if raw:
        merged.update(raw)

    frequency = str(merged.get("email_frequency") or "WEEKLY").upper()
    if frequency not in _EMAIL_FREQUENCIES:
        raise ValueError("EMAIL_FREQUENCY_INVALID")
    backup_kind = str(merged.get("backup_kind") or "DATABASE").upper()
    if backup_kind not in _BACKUP_KINDS:
        raise ValueError("BACKUP_KIND_NOT_SUPPORTED")
    timezone_name = str(merged.get("timezone") or "Europe/Berlin").strip()
    try:
        ZoneInfo(timezone_name)
    except ZoneInfoNotFoundError as exc:
        raise ValueError("TIMEZONE_INVALID") from exc

    return {
        "email_enabled": bool(merged.get("email_enabled", False)),
        "email_frequency": frequency,
        "email_hour": _int(merged.get("email_hour"), "EMAIL_HOUR", 0, 23),
        "email_weekday": _int(merged.get("email_weekday"), "EMAIL_WEEKDAY", 0, 6),
        "email_monthday": _int(merged.get("email_monthday"), "EMAIL_MONTHDAY", 1, 28),
        "email_recipients": _recipients(merged.get("email_recipients")),
        "timezone": timezone_name,
        "user_expiry_warning_days": _int(
            merged.get("user_expiry_warning_days"), "USER_EXPIRY_WARNING_DAYS", 1, 365
        ),
        "storage_warning_percent": _float(
            merged.get("storage_warning_percent"), "STORAGE_WARNING_PERCENT", 50.0, 99.9
        ),
        "automatic_backup_enabled": bool(merged.get("automatic_backup_enabled", False)),
        "backup_kind": backup_kind,
        "backup_hour": _int(merged.get("backup_hour"), "BACKUP_HOUR", 0, 23),
        "automatic_retention_enabled": bool(
            merged.get("automatic_retention_enabled", False)
        ),
        "retention_hour": _int(merged.get("retention_hour"), "RETENTION_HOUR", 0, 23),
        "backup_retention_days": _int(
            merged.get("backup_retention_days"), "BACKUP_RETENTION_DAYS", 1, 3650
        ),
        "backup_retention_min_per_kind": _int(
            merged.get("backup_retention_min_per_kind"),
            "BACKUP_RETENTION_MIN_PER_KIND",
            1,
            50,
        ),
    }


def load_automation_policy(cur=None) -> dict[str, Any]:
    if cur is not None:
        cur.execute(
            "SELECT value FROM tb.server_settings WHERE setting_key = %s",
            (AUTOMATION_SETTING_KEY,),
        )
        row = cur.fetchone()
        return normalize_automation_policy(row["value"] if row else None)
    with transaction() as conn, conn.cursor() as own_cur:
        return load_automation_policy(own_cur)


def save_automation_policy(cur, raw: dict[str, Any]) -> dict[str, Any]:
    policy = normalize_automation_policy(raw)
    cur.execute(
        """
        INSERT INTO tb.server_settings(setting_key, value, updated_at)
        VALUES (%s, %s, now())
        ON CONFLICT (setting_key) DO UPDATE
        SET value = EXCLUDED.value, updated_at = now()
        """,
        (AUTOMATION_SETTING_KEY, Jsonb(policy)),
    )
    return policy


def retention_policy() -> tuple[int, int]:
    policy = load_automation_policy()
    return (
        int(policy["backup_retention_days"]),
        int(policy["backup_retention_min_per_kind"]),
    )


def smtp_status() -> dict[str, Any]:
    password_set = bool(settings.smtp_password)
    configured = bool(
        settings.smtp_host
        and settings.smtp_sender
        and (not settings.smtp_user or password_set)
    )
    return {
        "configured": configured,
        "host": settings.smtp_host,
        "port": settings.smtp_port,
        "user": settings.smtp_user,
        "sender": settings.smtp_sender,
        "password_set": password_set,
        "starttls": settings.smtp_starttls,
        "ssl": settings.smtp_ssl,
    }
