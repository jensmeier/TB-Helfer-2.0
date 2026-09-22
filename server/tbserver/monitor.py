from __future__ import annotations

import json
import os
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from .database import connect


DIAGNOSTICS_ROOT = Path(os.getenv("TB_DIAGNOSTICS_ROOT", "/tb/diagnostics-v1"))
STATE_PATH = DIAGNOSTICS_ROOT / "monitor-state.json"
EVENTS_PATH = DIAGNOSTICS_ROOT / "server-monitor.jsonl"
WORKER_HEARTBEAT_PATH = DIAGNOSTICS_ROOT / "worker-heartbeat.json"

MONITOR_INTERVAL_SECONDS = max(2.0, float(os.getenv("TB_MONITOR_INTERVAL_SECONDS", "5")))
DB_WARN_MS = max(100.0, float(os.getenv("TB_MONITOR_DB_WARN_MS", "1000")))
API_WARN_MS = max(100.0, float(os.getenv("TB_MONITOR_API_WARN_MS", "1500")))
WORKER_STALE_SECONDS = max(20.0, float(os.getenv("TB_MONITOR_WORKER_STALE_SECONDS", "35")))
API_URL = os.getenv("TB_MONITOR_API_URL", "http://api:8080/health/ready").strip()
PUBLIC_URL = os.getenv("TB_MONITOR_PUBLIC_URL", "").strip().rstrip("/")
MAX_EVENT_LOG_BYTES = 5 * 1024 * 1024
_WORKER_STARTED_AT = datetime.now(timezone.utc).isoformat()


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
    try:
        request = Request(url, headers={"User-Agent": "TB-Helfer-Monitor/1.0"})
        with urlopen(request, timeout=timeout) as response:
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
            "detail": _safe_message(exc),
        }
    except Exception as exc:
        return {
            "status": "ERROR",
            "latency_ms": round((time.monotonic() - started) * 1000.0, 1),
            "error_type": type(exc).__name__,
            "detail": _safe_message(exc),
        }


def _read_io_pressure() -> dict[str, Any]:
    try:
        raw = Path("/proc/pressure/io").read_text(encoding="utf-8")
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
    }
    prefix = {
        "database": "DB",
        "api": "API",
        "public": "PUBLIC_API",
        "worker": "WORKER",
        "io": "IO_PRESSURE",
        "memory": "SWAP",
    }.get(name, name.upper())
    label = labels.get(name, name)
    if status == "OK":
        return "INFO", prefix + "_RECOVERED", label + " ist wieder unauffällig"
    if status == "WARN":
        return "WARN", prefix + "_WARN", label + " meldet eine Warnung"
    if status == "ERROR":
        return "ERROR", prefix + "_ERROR", label + " meldet einen Fehler"
    return "INFO", prefix + "_STATE", label + " Status geändert"


def _record_transitions(
    checks: dict[str, dict[str, Any]],
    previous: dict[str, Any] | None,
) -> None:
    previous_checks = (previous or {}).get("checks") or {}
    for name, current in checks.items():
        old = previous_checks.get(name) or {}
        old_status = str(old.get("status", "")).upper()
        new_status = str(current.get("status", "UNKNOWN")).upper()
        if old_status and old_status == new_status:
            continue
        level, code, message = _transition_code(name, current)
        _append_event(level, code, message, data=current)

    old_worker = previous_checks.get("worker") or {}
    new_worker = checks.get("worker") or {}
    old_pid = old_worker.get("pid")
    new_pid = new_worker.get("pid")
    if old_pid and new_pid and old_pid != new_pid:
        _append_event(
            "WARN",
            "WORKER_RESTARTED",
            "Worker-Prozess wurde neu gestartet",
            data={
                "previous_pid": old_pid,
                "new_pid": new_pid,
                "started_at": new_worker.get("started_at"),
            },
        )


def _run_once(previous: dict[str, Any] | None) -> dict[str, Any]:
    checks = {
        "database": _check_database(),
        "api": _http_check(API_URL),
        "public": _http_check(PUBLIC_URL + "/health/ready" if PUBLIC_URL else ""),
        "worker": _check_worker(),
        "io": _read_io_pressure(),
        "memory": _read_memory(),
    }
    _record_transitions(checks, previous)
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
    result = dict(state)
    result.update(
        {
            "available": True,
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
