from __future__ import annotations

from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from psycopg.types.json import Jsonb

from .config import settings
from .database import transaction


AUTOMATION_SETTING_KEY = "admin_automation"
_EMAIL_FREQUENCIES = {"DAILY", "WEEKLY", "MONTHLY"}
_BACKUP_KINDS = {"DATABASE", "FILES", "FULL"}
POLICY_REVISION = 2


def _schedule_defaults(
    *,
    enabled: bool,
    frequency: str,
    hour: int,
    minute: int = 0,
    weekday: int = 6,
    monthday: int = 1,
) -> dict[str, Any]:
    return {
        "enabled": enabled,
        "frequency": frequency,
        "hour": hour,
        "minute": minute,
        "weekday": weekday,
        "monthday": monthday,
    }


def default_automation_policy() -> dict[str, Any]:
    return {
        "policy_revision": POLICY_REVISION,
        "email_enabled": False,
        "email_frequency": "WEEKLY",
        "email_hour": 10,
        "email_minute": 0,
        "email_weekday": 6,
        "email_monthday": 1,
        "email_recipients": [],
        "alert_email_enabled": False,
        "alert_recovery_enabled": True,
        "alert_cooldown_minutes": 30,
        "alert_types": {
            "database": True,
            "worker": True,
            "backup": True,
            "public_api": True,
            "storage": True,
        },
        "timezone": "Europe/Berlin",
        "user_expiry_warning_days": 30,
        "storage_warning_percent": 85.0,
        "backup_schedules": {
            "DATABASE": _schedule_defaults(
                enabled=True, frequency="DAILY", hour=1, minute=30
            ),
            "FILES": _schedule_defaults(
                enabled=True, frequency="DAILY", hour=3, minute=30
            ),
            "FULL": _schedule_defaults(
                enabled=True, frequency="WEEKLY", weekday=6, hour=5, minute=0
            ),
        },
        "retention_schedule": _schedule_defaults(
            enabled=True, frequency="WEEKLY", weekday=6, hour=6, minute=30
        ),
        "backup_retention_days": 90,
        "backup_retention_min_per_kind": 3,
        # Legacy aliases remain in the response for compatibility with older admin clients.
        "automatic_backup_enabled": True,
        "backup_kind": "DATABASE",
        "backup_hour": 1,
        "automatic_retention_enabled": True,
        "retention_hour": 6,
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


def _frequency(value: Any, name: str) -> str:
    result = str(value or "WEEKLY").upper()
    if result not in _EMAIL_FREQUENCIES:
        raise ValueError(f"{name}_FREQUENCY_INVALID")
    return result


def _normalize_schedule(
    value: Any,
    *,
    name: str,
    default: dict[str, Any],
) -> dict[str, Any]:
    raw = dict(default)
    if isinstance(value, dict):
        raw.update(value)
    elif value is not None:
        raise ValueError(f"{name}_INVALID")
    return {
        "enabled": bool(raw.get("enabled", False)),
        "frequency": _frequency(raw.get("frequency"), name),
        "hour": _int(raw.get("hour"), f"{name}_HOUR", 0, 23),
        "minute": _int(raw.get("minute"), f"{name}_MINUTE", 0, 59),
        "weekday": _int(raw.get("weekday"), f"{name}_WEEKDAY", 0, 6),
        "monthday": _int(raw.get("monthday"), f"{name}_MONTHDAY", 1, 28),
    }


def _alert_types(value: Any) -> dict[str, bool]:
    defaults = default_automation_policy()["alert_types"]
    raw = dict(defaults)
    if isinstance(value, dict):
        raw.update(value)
    elif value is not None:
        raise ValueError("ALERT_TYPES_INVALID")
    return {key: bool(raw.get(key, True)) for key in defaults}


def normalize_automation_policy(raw: dict[str, Any] | None) -> dict[str, Any]:
    defaults = default_automation_policy()
    merged = dict(defaults)
    if raw:
        merged.update(raw)

    previous_revision = 0
    if raw:
        try:
            previous_revision = int(raw.get("policy_revision", 1))
        except (TypeError, ValueError):
            previous_revision = 1
    if raw and previous_revision < POLICY_REVISION:
        # Server-1.0 policy v2 introduces the user-approved split schedule.
        merged["backup_schedules"] = defaults["backup_schedules"]
        merged["retention_schedule"] = defaults["retention_schedule"]
        merged["backup_retention_days"] = 90
        merged["email_hour"] = defaults["email_hour"]
        merged["email_minute"] = defaults["email_minute"]
        merged["email_weekday"] = defaults["email_weekday"]

    timezone_name = str(merged.get("timezone") or "Europe/Berlin").strip()
    try:
        ZoneInfo(timezone_name)
    except ZoneInfoNotFoundError as exc:
        raise ValueError("TIMEZONE_INVALID") from exc

    schedules_raw = merged.get("backup_schedules")
    if not isinstance(schedules_raw, dict):
        schedules_raw = {}
    backup_schedules = {
        kind: _normalize_schedule(
            schedules_raw.get(kind),
            name=f"BACKUP_{kind}",
            default=defaults["backup_schedules"][kind],
        )
        for kind in _BACKUP_KINDS
    }
    retention_schedule = _normalize_schedule(
        merged.get("retention_schedule"),
        name="RETENTION",
        default=defaults["retention_schedule"],
    )

    result = {
        "policy_revision": POLICY_REVISION,
        "email_enabled": bool(merged.get("email_enabled", False)),
        "email_frequency": _frequency(merged.get("email_frequency"), "EMAIL"),
        "email_hour": _int(merged.get("email_hour"), "EMAIL_HOUR", 0, 23),
        "email_minute": _int(merged.get("email_minute", 0), "EMAIL_MINUTE", 0, 59),
        "email_weekday": _int(merged.get("email_weekday"), "EMAIL_WEEKDAY", 0, 6),
        "email_monthday": _int(merged.get("email_monthday"), "EMAIL_MONTHDAY", 1, 28),
        "email_recipients": _recipients(merged.get("email_recipients")),
        "alert_email_enabled": bool(merged.get("alert_email_enabled", False)),
        "alert_recovery_enabled": bool(merged.get("alert_recovery_enabled", True)),
        "alert_cooldown_minutes": _int(
            merged.get("alert_cooldown_minutes", 30), "ALERT_COOLDOWN_MINUTES", 5, 1440
        ),
        "alert_types": _alert_types(merged.get("alert_types")),
        "timezone": timezone_name,
        "user_expiry_warning_days": _int(
            merged.get("user_expiry_warning_days"), "USER_EXPIRY_WARNING_DAYS", 1, 365
        ),
        "storage_warning_percent": _float(
            merged.get("storage_warning_percent"), "STORAGE_WARNING_PERCENT", 50.0, 99.9
        ),
        "backup_schedules": backup_schedules,
        "retention_schedule": retention_schedule,
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

    # Compatibility aliases for existing source checks and older admin clients.
    result.update(
        {
            "automatic_backup_enabled": any(
                schedule["enabled"] for schedule in backup_schedules.values()
            ),
            "backup_kind": "DATABASE",
            "backup_hour": int(backup_schedules["DATABASE"]["hour"]),
            "automatic_retention_enabled": bool(retention_schedule["enabled"]),
            "retention_hour": int(retention_schedule["hour"]),
        }
    )
    return result


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
