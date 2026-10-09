from __future__ import annotations

import json
import os
import shutil
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from .config import settings
from .database import connect
from .notifications import NotificationError, send_system_message
from .system_settings import load_automation_policy, smtp_status


DIAGNOSTICS_ROOT = Path(os.getenv("TB_DIAGNOSTICS_ROOT", "/tb/diagnostics-v1"))
STATE_PATH = DIAGNOSTICS_ROOT / "monitor-state.json"
EVENTS_PATH = DIAGNOSTICS_ROOT / "server-monitor.jsonl"
WORKER_HEARTBEAT_PATH = DIAGNOSTICS_ROOT / "worker-heartbeat.json"

MONITOR_INTERVAL_SECONDS = max(2.0, float(os.getenv("TB_MONITOR_INTERVAL_SECONDS", "5")))
MONITOR_STALE_SECONDS = max(120.0, MONITOR_INTERVAL_SECONDS * 3.0)
DB_WARN_MS = max(100.0, float(os.getenv("TB_MONITOR_DB_WARN_MS", "1000")))
API_WARN_MS = max(100.0, float(os.getenv("TB_MONITOR_API_WARN_MS", "1500")))
WORKER_STALE_SECONDS = max(20.0, float(os.getenv("TB_MONITOR_WORKER_STALE_SECONDS", "35")))
API_URL = os.getenv("TB_MONITOR_API_URL", "http://api:8080/health/ready").strip()
PUBLIC_URL = os.getenv("TB_MONITOR_PUBLIC_URL", "").strip().rstrip("/")
MAX_EVENT_LOG_BYTES = 5 * 1024 * 1024
_WORKER_STARTED_AT = datetime.now(timezone.utc).isoformat()
_cached_policy: dict[str, Any] | None = None
_cached_policy_checked_at = 0.0
_cached_backup: dict[str, Any] | None = None
_cached_backup_checked_at = 0.0
_last_alert_sent: dict[str, datetime] = {}


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _now_iso() -> str:
    return _now().isoformat()


def _safe_message(exc: Exception) -> str:
    value = str(exc).replace("\n", " ").strip()
    if not value:
        return type(exc).__name__
    return value[:300]


def _read_json(path: Path) -> dict[str, Any] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else None
    except Exception:
        return None


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    os.replace(tmp, path)


def _append_event(
    level: str,
    code: str,
    message: str,
    *,
    data: dict[str, Any] | None = None,
) -> None:
    try:
        EVENTS_PATH.parent.mkdir(parents=True, exist_ok=True)
        record = {
            "timestamp": _now_iso(),
            "level": level.upper(),
            "code": code,
            "message": message,
            "data": data or {},
        }
        with EVENTS_PATH.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
        if EVENTS_PATH.stat().st_size > MAX_EVENT_LOG_BYTES:
            lines = EVENTS_PATH.read_text(encoding="utf-8", errors="replace").splitlines()
            EVENTS_PATH.write_text("\n".join(lines[-2500:]) + "\n", encoding="utf-8")
    except Exception:
        pass


def write_worker_heartbeat() -> None:
    try:
        _write_json(
            WORKER_HEARTBEAT_PATH,
            {
                "updated_at": _now_iso(),
                "pid": os.getpid(),
                "started_at": _WORKER_STARTED_AT,
            },
        )
    except Exception:
        pass


def _check_database() -> dict[str, Any]:
    started = time.monotonic()
    try:
        with connect() as conn, conn.cursor() as cur:
            cur.execute("SELECT 1 AS ok")
            row = cur.fetchone()
            if not row or row["ok"] != 1:
                raise RuntimeError("SELECT_1_FAILED")
        latency_ms = round((time.monotonic() - started) * 1000.0, 1)
        return {
            "status": "WARN" if latency_ms >= DB_WARN_MS else "OK",
            "latency_ms": latency_ms,
            "detail": "Datenbank antwortet",
        }
    except Exception as exc:
        latency_ms = round((time.monotonic() - started) * 1000.0, 1)
        return {
            "status": "ERROR",
            "latency_ms": latency_ms,
            "error_type": type(exc).__name__,
            "detail": _safe_message(exc),
        }


def _http_check(url: str, *, timeout: float = 4.5) -> dict[str, Any]:
    if not url:
        return {"status": "SKIP", "detail": "nicht konfiguriert"}
    started = time.monotonic()
    phase = "connect_or_headers"
    try:
        request = Request(url, headers={"User-Agent": "TB-Helfer-Monitor/1.1"})
        with urlopen(request, timeout=timeout) as response:
            phase = "response_body"
            body = response.read(4096)
            status_code = int(getattr(response, "status", 200))
        latency_ms = round((time.monotonic() - started) * 1000.0, 1)
        if not 200 <= status_code < 300:
            return {
                "status": "ERROR",
                "latency_ms": latency_ms,
                "http_status": status_code,
                "detail": "HTTP-Status nicht erfolgreich",
            }
        status = "WARN" if latency_ms >= API_WARN_MS else "OK"
        detail = "HTTP erreichbar"
        try:
            payload = json.loads(body.decode("utf-8"))
            if isinstance(payload, dict) and payload.get("status") not in (None, "ok"):
                status = "ERROR"
                detail = str(payload.get("status"))[:100]
        except Exception:
            pass
        return {
            "status": status,
            "latency_ms": latency_ms,
            "http_status": status_code,
            "detail": detail,
        }
    except HTTPError as exc:
        return {
            "status": "ERROR",
            "latency_ms": round((time.monotonic() - started) * 1000.0, 1),
            "http_status": int(exc.code),
            "error_type": type(exc).__name__,
            "failure_phase": phase,
            "timeout_seconds": timeout,
            "detail": _safe_message(exc),
        }
    except Exception as exc:
        return {
            "status": "ERROR",
            "latency_ms": round((time.monotonic() - started) * 1000.0, 1),
            "error_type": type(exc).__name__,
            "failure_phase": phase,
            "timeout_seconds": timeout,
            "detail": _safe_message(exc),
        }


def _read_io_pressure() -> dict[str, Any]:
    path = Path("/proc/pressure/io")
    if not path.exists():
        return {
            "status": "SKIP",
            "detail": "Auf dieser Synology im Container nicht verfügbar",
        }
    try:
        raw = path.read_text(encoding="utf-8")
        values: dict[str, dict[str, float]] = {}
        for line in raw.splitlines():
            parts = line.split()
            if not parts:
                continue
            bucket: dict[str, float] = {}
            for item in parts[1:]:
                if "=" not in item:
                    continue
                key, value = item.split("=", 1)
                if key.startswith("avg"):
                    bucket[key] = float(value)
            values[parts[0]] = bucket
        some_avg10 = float(values.get("some", {}).get("avg10", 0.0))
        full_avg10 = float(values.get("full", {}).get("avg10", 0.0))
        status = "WARN" if some_avg10 >= 30.0 or full_avg10 >= 10.0 else "OK"
        return {
            "status": status,
            "some_avg10": round(some_avg10, 2),
            "full_avg10": round(full_avg10, 2),
            "detail": "Linux I/O Pressure (10 s)",
        }
    except Exception as exc:
        return {"status": "UNKNOWN", "detail": _safe_message(exc)}


def _read_memory() -> dict[str, Any]:
    try:
        values: dict[str, int] = {}
        for line in Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
            if ":" not in line:
                continue
            key, raw = line.split(":", 1)
            parts = raw.strip().split()
            if parts:
                values[key] = int(parts[0])
        swap_total = int(values.get("SwapTotal", 0))
        swap_free = int(values.get("SwapFree", 0))
        swap_used = max(0, swap_total - swap_free)
        pct = round((swap_used / swap_total) * 100.0, 1) if swap_total else 0.0
        return {
            "status": "WARN" if pct >= 70.0 else "OK",
            "swap_total_kib": swap_total,
            "swap_used_kib": swap_used,
            "swap_used_percent": pct,
            "detail": "Swap-Auslastung",
        }
    except Exception as exc:
        return {"status": "UNKNOWN", "detail": _safe_message(exc)}


def _check_storage(warning_percent: float) -> dict[str, Any]:
    try:
        usage = shutil.disk_usage(settings.storage_root)
        pct = round((usage.used / usage.total) * 100.0, 2) if usage.total else 0.0
        return {
            "status": "WARN" if pct >= warning_percent else "OK",
            "percent_used": pct,
            "free_bytes": usage.free,
            "total_bytes": usage.total,
            "warning_percent": warning_percent,
            "detail": "Datenspeicher",
        }
    except Exception as exc:
        return {
            "status": "ERROR",
            "error_type": type(exc).__name__,
            "detail": _safe_message(exc),
        }


def _check_backup(database: dict[str, Any]) -> dict[str, Any]:
    global _cached_backup, _cached_backup_checked_at
    if database.get("status") == "ERROR":
        return _cached_backup or {"status": "SKIP", "detail": "Datenbank nicht erreichbar"}
    now_mono = time.monotonic()
    if _cached_backup is not None and now_mono - _cached_backup_checked_at < 30.0:
        return _cached_backup
    try:
        with connect() as conn, conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, job_type, status, updated_at, last_error
                FROM tb.background_jobs
                WHERE job_type IN ('BACKUP_DATABASE','BACKUP_FILES','BACKUP_FULL',
                                   'BACKUP_USER','BACKUP_TRACK','BACKUP_SYSTEM','BACKUP_RETENTION')
                  AND NOT (status='FAILED' AND COALESCE(last_error,'')='CANCELLED_AFTER_RECOVERY')
                ORDER BY updated_at DESC
                LIMIT 1
                """
            )
            row = cur.fetchone()
        if not row:
            result = {"status": "OK", "detail": "Noch kein automatischer Backup-Job protokolliert"}
        else:
            item = dict(row)
            status = str(item.get("status") or "")
            updated_at = item.get("updated_at")
            if isinstance(updated_at, datetime):
                updated_at = updated_at.isoformat()
            result = {
                "status": "ERROR" if status == "FAILED" else "OK",
                "job_type": item.get("job_type"),
                "job_status": status,
                "updated_at": updated_at,
                "error_type": item.get("last_error") if status == "FAILED" else None,
                "detail": "Letzter Backup-Job fehlgeschlagen" if status == "FAILED" else "Backup-Jobs unauffällig",
            }
        _cached_backup = result
        _cached_backup_checked_at = now_mono
        return result
    except Exception as exc:
        return _cached_backup or {
            "status": "UNKNOWN",
            "error_type": type(exc).__name__,
            "detail": _safe_message(exc),
        }


def _parse_iso(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _check_worker() -> dict[str, Any]:
    heartbeat = _read_json(WORKER_HEARTBEAT_PATH)
    if not heartbeat:
        return {"status": "ERROR", "detail": "Noch kein Worker-Heartbeat vorhanden"}
    updated = _parse_iso(heartbeat.get("updated_at"))
    if updated is None:
        return {"status": "ERROR", "detail": "Worker-Heartbeat ist ungültig"}
    age = max(0.0, (_now() - updated).total_seconds())
    return {
        "status": "ERROR" if age > WORKER_STALE_SECONDS else "OK",
        "age_seconds": round(age, 1),
        "pid": heartbeat.get("pid"),
        "started_at": heartbeat.get("started_at"),
        "detail": "Worker-Heartbeat",
    }


def _overall(checks: dict[str, dict[str, Any]]) -> str:
    statuses = {str(value.get("status", "UNKNOWN")).upper() for value in checks.values()}
    if "ERROR" in statuses:
        return "ERROR"
    if "WARN" in statuses:
        return "WARN"
    return "OK"


def _transition_code(name: str, current: dict[str, Any]) -> tuple[str, str, str]:
    status = str(current.get("status", "UNKNOWN")).upper()
    if name == "database" and status == "ERROR":
        code = "DB_CONNECTION_TIMEOUT" if current.get("error_type") == "ConnectionTimeout" else "DB_UNAVAILABLE"
        return "ERROR", code, "Datenbank ist nicht erreichbar"
    labels = {
        "database": "Datenbank",
        "api": "Interne API",
        "public": "Öffentliche API/Cloudflare",
        "worker": "Worker",
        "io": "Datenträger-I/O",
        "memory": "Speicher/Swap",
        "storage": "Datenspeicher",
        "backup": "Backup",
    }
    prefix = {
        "database": "DB",
        "api": "API",
        "public": "PUBLIC_API",
        "worker": "WORKER",
        "io": "IO_PRESSURE",
        "memory": "SWAP",
        "storage": "STORAGE",
        "backup": "BACKUP",
    }.get(name, name.upper())
    label = labels.get(name, name)
    if status == "OK":
        return "INFO", prefix + "_RECOVERED", label + " ist wieder unauffällig"
    if status == "WARN":
        return "WARN", prefix + "_WARN", label + " meldet eine Warnung"
    if status == "ERROR":
        return "ERROR", prefix + "_ERROR", label + " meldet einen Fehler"
    return "INFO", prefix + "_STATE", label + " Status geändert"


def _refresh_policy(database: dict[str, Any]) -> dict[str, Any] | None:
    global _cached_policy, _cached_policy_checked_at
    if database.get("status") == "ERROR":
        return _cached_policy
    now_mono = time.monotonic()
    if _cached_policy is not None and now_mono - _cached_policy_checked_at < 60.0:
        return _cached_policy
    try:
        _cached_policy = load_automation_policy()
        _cached_policy_checked_at = now_mono
    except Exception:
        pass
    return _cached_policy


def _alert_type(name: str) -> str | None:
    return {
        "database": "database",
        "api": "internal_api",
        "worker": "worker",
        "backup": "backup",
        "public": "public_api",
        "storage": "storage",
    }.get(name)


def _alert_allowed(policy: dict[str, Any] | None, alert_type: str | None) -> bool:
    if not policy or not alert_type or not policy.get("alert_email_enabled"):
        return False
    return bool((policy.get("alert_types") or {}).get(alert_type, False))


def _send_alert(
    policy: dict[str, Any] | None,
    *,
    alert_type: str | None,
    key: str,
    subject: str,
    body: str,
    recovery: bool = False,
) -> None:
    if not _alert_allowed(policy, alert_type):
        return
    if recovery and not policy.get("alert_recovery_enabled", True):
        return
    recipients = list(policy.get("email_recipients") or [])
    if not recipients or not smtp_status().get("configured"):
        return
    now = _now()
    cooldown = timedelta(minutes=int(policy.get("alert_cooldown_minutes", 30)))
    last = _last_alert_sent.get(key)
    if last is not None and now - last < cooldown:
        return
    try:
        send_system_message(subject, body, recipients)
        _last_alert_sent[key] = now
        _append_event(
            "INFO",
            "ALERT_EMAIL_SENT",
            "Monitor-E-Mail wurde versendet",
            data={"alert_type": alert_type, "subject": subject, "recovery": recovery},
        )
    except NotificationError as exc:
        _append_event(
            "WARN",
            "ALERT_EMAIL_FAILED",
            "Monitor-E-Mail konnte nicht versendet werden",
            data={"alert_type": alert_type, "detail": str(exc)[:200]},
        )
    except Exception as exc:
        _append_event(
            "WARN",
            "ALERT_EMAIL_FAILED",
            "Monitor-E-Mail konnte nicht versendet werden",
            data={"alert_type": alert_type, "detail": _safe_message(exc)},
        )


def _event_body(name: str, current: dict[str, Any], code: str) -> str:
    values = [
        "TB-Helfer Server 1.0 – Serverüberwachung",
        f"Ereignis: {code}",
        f"Zeit: {_now_iso()}",
        f"Bereich: {name}",
        f"Status: {current.get('status', 'UNKNOWN')}",
    ]
    if current.get("latency_ms") is not None:
        values.append(f"Antwortzeit: {current['latency_ms']} ms")
    if current.get("http_status") is not None:
        values.append(f"HTTP: {current['http_status']}")
    if current.get("error_type"):
        values.append(f"Fehlertyp: {current['error_type']}")
    if current.get("detail"):
        values.append(f"Detail: {current['detail']}")
    return "\n".join(values)


def _record_public_check(current: dict, old: dict, checks: dict, policy) -> None:
    """Persist consecutive failures without hiding the raw check status."""
    status = str(current.get("status", "UNKNOWN")).upper()
    old_status = str(old.get("status", "")).upper()
    count = int(old.get("consecutive_errors", 0)) if old_status == "ERROR" else 0
    count = min(3, count + 1) if status == "ERROR" else 0
    confirmed = bool(old.get("incident_confirmed", False))
    current["consecutive_errors"] = count
    current["incident_confirmed"] = confirmed or count >= 3
    if old_status != status and (old_status or status in {"ERROR", "WARN"}):
        current["internal_api_status"] = (checks.get("api") or {}).get("status", "UNKNOWN")
        current["database_status"] = (checks.get("database") or {}).get("status", "UNKNOWN")
        level, code, message = _transition_code("public", current)
        _append_event(level, code, message, data=dict(current))
    newly_confirmed = count >= 3 and not confirmed
    recovered = status == "OK" and confirmed
    if newly_confirmed or recovered:
        code = "PUBLIC_API_RECOVERED" if recovered else "PUBLIC_API_ERROR"
        body = _event_body("public", current, code)
        if newly_confirmed:
            body += "\nBestätigung: 3 aufeinanderfolgende Fehlversuche"
        for name, label in (("api", "Interne API"), ("database", "Datenbank")):
            item = checks.get(name) or {}
            body += f"\n{label}: {item.get('status', 'UNKNOWN')}"
            if item.get("latency_ms") is not None:
                body += f" · {item['latency_ms']} ms"
            if item.get("detail"):
                body += f" · {item['detail']}"
        _send_alert(
            policy, alert_type="public_api",
            key="public_api:recovery" if recovered else "public_api:problem",
            subject=("TB-Helfer wieder OK – Öffentliche API wieder erreichbar" if recovered
                     else "TB-Helfer Serverwarnung – Öffentliche API: 3 Fehlversuche in Folge"),
            body=body, recovery=recovered,
        )
    if status in {"OK", "SKIP"}:
        current["incident_confirmed"] = False


def _record_transitions(
    checks: dict[str, dict[str, Any]],
    previous: dict[str, Any] | None,
    policy: dict[str, Any] | None,
) -> None:
    previous_checks = (previous or {}).get("checks") or {}
    for name, current in checks.items():
        old = previous_checks.get(name) or {}
        if name == "public":
            _record_public_check(current, old, checks, policy)
            continue
        old_status = str(old.get("status", "")).upper()
        new_status = str(current.get("status", "UNKNOWN")).upper()
        if old_status and old_status == new_status:
            continue

        # First monitor sample only records actionable states; healthy startup is not an error history.
        if not old_status and new_status not in {"ERROR", "WARN"}:
            continue

        level, code, message = _transition_code(name, current)
        _append_event(level, code, message, data=current)
        alert_type = _alert_type(name)

        if new_status == "ERROR" or (name == "storage" and new_status == "WARN"):
            _send_alert(
                policy,
                alert_type=alert_type,
                key=f"{alert_type}:problem",
                subject=f"TB-Helfer Serverwarnung – {message}",
                body=_event_body(name, current, code),
            )
        elif new_status == "OK" and old_status in {"ERROR", "WARN"}:
            _send_alert(
                policy,
                alert_type=alert_type,
                key=f"{alert_type}:recovery",
                subject=f"TB-Helfer wieder OK – {message}",
                body=_event_body(name, current, code),
                recovery=True,
            )

    old_worker = previous_checks.get("worker") or {}
    new_worker = checks.get("worker") or {}
    old_pid = old_worker.get("pid")
    new_pid = new_worker.get("pid")
    old_started = _parse_iso(old_worker.get("started_at"))
    new_started = _parse_iso(new_worker.get("started_at"))
    restarted = (
        old_started != new_started
        if old_started is not None and new_started is not None
        else bool(old_pid and new_pid and old_pid != new_pid)
    )
    if restarted:
        data = {
            "previous_pid": old_pid,
            "new_pid": new_pid,
            "previous_started_at": old_worker.get("started_at"),
            "started_at": new_worker.get("started_at"),
        }
        _append_event(
            "WARN",
            "WORKER_RESTARTED",
            "Worker-Prozess wurde neu gestartet",
            data=data,
        )
        _send_alert(
            policy,
            alert_type="worker",
            key="worker:restart",
            subject="TB-Helfer Serverwarnung – Worker neu gestartet",
            body=(
                "TB-Helfer Server 1.0 – Serverüberwachung\n"
                f"Zeit: {_now_iso()}\n"
                f"Alter PID: {old_pid}\nNeuer PID: {new_pid}\n"
                f"Start: {new_worker.get('started_at') or 'unbekannt'}"
            ),
        )


def _run_once(previous: dict[str, Any] | None) -> dict[str, Any]:
    database = _check_database()
    policy = _refresh_policy(database)
    warning_percent = float((policy or {}).get("storage_warning_percent", 85.0))
    checks = {
        "database": database,
        "api": _http_check(API_URL),
        "public": _http_check(PUBLIC_URL + "/health/ready" if PUBLIC_URL else ""),
        "worker": _check_worker(),
        "backup": _check_backup(database),
        "storage": _check_storage(warning_percent),
        "io": _read_io_pressure(),
        "memory": _read_memory(),
    }
    _record_transitions(checks, previous, policy)
    return {
        "updated_at": _now_iso(),
        "overall": _overall(checks),
        "checks": checks,
    }


def read_monitor_snapshot(*, event_limit: int = 30) -> dict[str, Any]:
    state = _read_json(STATE_PATH)
    if not state:
        return {
            "available": False,
            "overall": "UNKNOWN",
            "updated_at": None,
            "checks": {},
            "current": {"error": 0, "warn": 0},
            "events_24h": {"all": 0, "warn": 0, "error": 0},
            "recent_events": [],
        }

    events: list[dict[str, Any]] = []
    try:
        for line in EVENTS_PATH.read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(item, dict):
                events.append(item)
    except Exception:
        pass

    cutoff = _now() - timedelta(hours=24)
    recent_24h = [
        event
        for event in events
        if (_parse_iso(event.get("timestamp")) or datetime.min.replace(tzinfo=timezone.utc))
        >= cutoff
    ]
    # Keep the persisted observations, but never present an old healthy sample
    # as evidence that monitoring is still running.
    updated = _parse_iso(state.get("updated_at"))
    age = (_now() - updated).total_seconds() if updated is not None else None
    stale = age is None or age < -MONITOR_STALE_SECONDS or age > MONITOR_STALE_SECONDS
    checks = dict(state.get("checks") or {})
    checks["monitor"] = {
        "status": "ERROR" if stale else "OK",
        "age_seconds": round(age, 1) if age is not None else None,
        "detail": "Überwachung liefert keinen aktuellen Stand" if stale else "Überwachung aktuell",
    }
    current_statuses = [str(item.get("status", "UNKNOWN")).upper() for item in checks.values()]
    result = dict(state)
    result.update(
        {
            "available": True,
            "stale": stale,
            "checks": checks,
            "overall": "ERROR" if stale else state.get("overall", "UNKNOWN"),
            "current": {
                "error": sum(1 for status in current_statuses if status == "ERROR"),
                "warn": sum(1 for status in current_statuses if status == "WARN"),
            },
            "events_24h": {
                "all": len(recent_24h),
                "warn": sum(1 for e in recent_24h if str(e.get("level")).upper() == "WARN"),
                "error": sum(1 for e in recent_24h if str(e.get("level")).upper() == "ERROR"),
            },
            "recent_events": events[-max(1, min(100, int(event_limit))):][::-1],
        }
    )
    return result


def main() -> None:
    DIAGNOSTICS_ROOT.mkdir(parents=True, exist_ok=True)
    previous = _read_json(STATE_PATH)
    _append_event("INFO", "MONITOR_STARTED", "TB-Server-Monitor wurde gestartet")
    while True:
        try:
            state = _run_once(previous)
            _write_json(STATE_PATH, state)
            previous = state
        except Exception as exc:
            _append_event(
                "ERROR",
                "MONITOR_LOOP_ERROR",
                "Server-Monitor konnte einen Prüflauf nicht abschließen",
                data={"error_type": type(exc).__name__, "detail": _safe_message(exc)},
            )
        time.sleep(MONITOR_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()


