"""Small, strictly read-only and redacted bridge to the existing server monitor."""
from __future__ import annotations

import http.client
import json
import math
import os
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit


CHECK_NAMES = ("database", "api", "public", "worker", "backup", "storage", "io", "memory")
ALLOWED_STATUSES = frozenset(("OK", "WARN", "ERROR", "SKIP", "UNKNOWN"))
NUMERIC_FIELDS = ("latency_ms", "age_seconds", "http_status", "usage_percent", "used_percent", "percent")
MAX_SNAPSHOT_BYTES = 256 * 1024
MAX_RESPONSE_BYTES = 8192


def _health(path: str) -> dict:
    """Only two fixed paths and a fixed, operator-configured local hostname."""
    try:
        target = urlsplit(os.getenv("TB_MCP_API_BASE_URL", "http://api:8080"))
        port = target.port
    except ValueError:
        return {"status": "UNKNOWN", "reason": "ungueltige_interne_api_konfiguration"}
    if (target.scheme != "http" or target.hostname not in {"api", "localhost", "127.0.0.1"}
            or target.username or target.password or target.path not in {"", "/"}
            or target.query or target.fragment):
        return {"status": "UNKNOWN", "reason": "ungueltige_interne_api_konfiguration"}
    if path not in {"/health/live", "/health/ready"}:
        return {"status": "UNKNOWN", "reason": "ungueltiger_pruefpfad"}

    connection = None
    try:
        connection = http.client.HTTPConnection(target.hostname, port or 80, timeout=2.5)
        connection.request("GET", path, headers={"Accept": "application/json"})
        response = connection.getresponse()
        code = response.status
        payload = response.read(MAX_RESPONSE_BYTES + 1)
        if len(payload) > MAX_RESPONSE_BYTES:
            return {"status": "ERROR", "http_status": code, "reason": "antwort_zu_gross"}
        data = json.loads(payload)
        if not isinstance(data, dict):
            return {"status": "ERROR", "http_status": code, "reason": "ungueltige_antwort"}
        result = {"status": "OK" if code == 200 and data.get("status") == "ok" else "ERROR",
                  "http_status": code}
        version = data.get("server_version")
        if isinstance(version, str) and len(version) <= 50 and all(c.isalnum() or c in ".-_" for c in version):
            result["server_version"] = version
        if path == "/health/ready":
            if isinstance(data.get("database"), str) and data["database"] in {"up", "down"}:
                result["database"] = data["database"]
            if isinstance(data.get("migrations"), str) and data["migrations"] in {"ok", "pending", "invalid"}:
                result["migrations"] = data["migrations"]
            pending = data.get("pending")
            if isinstance(pending, list):
                result["pending_migrations_count"] = min(len(pending), 10000)
        return result
    except (OSError, ValueError, json.JSONDecodeError, http.client.HTTPException):
        return {"status": "ERROR", "reason": "api_nicht_erreichbar_oder_antwort_ungueltig"}
    finally:
        if connection is not None:
            connection.close()


def server_gesundheit() -> dict:
    """Check the already existing API liveness and readiness endpoints."""
    live = _health("/health/live")
    ready = _health("/health/ready")
    overall = "OK" if live["status"] == "OK" and ready["status"] == "OK" else "ERROR"
    if "UNKNOWN" in (live["status"], ready["status"]):
        overall = "UNKNOWN"
    return {"overall": overall, "live": live, "ready": ready}


def _timestamp(value: object) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return dt.astimezone(timezone.utc) if dt.tzinfo else None
    except ValueError:
        return None


def monitor_uebersicht() -> dict:
    """Read one fixed snapshot file, whitelist fields and flag stale information."""
    root = Path(os.getenv("TB_MCP_DIAGNOSTICS_ROOT", "/tb/diagnostics-v1"))
    path = root / "monitor-state.json"
    try:
        if path.stat().st_size > MAX_SNAPSHOT_BYTES:
            return {"overall": "UNKNOWN", "reason": "snapshot_zu_gross", "checks": {}}
        with path.open("rb") as stream:
            raw = stream.read(MAX_SNAPSHOT_BYTES + 1)
        if len(raw) > MAX_SNAPSHOT_BYTES:
            return {"overall": "UNKNOWN", "reason": "snapshot_zu_gross", "checks": {}}
        data = json.loads(raw)
    except (OSError, UnicodeError, ValueError):
        return {"overall": "UNKNOWN", "reason": "snapshot_fehlt_oder_ungueltig", "checks": {}}
    if not isinstance(data, dict) or not isinstance(data.get("checks"), dict):
        return {"overall": "UNKNOWN", "reason": "snapshot_ungueltig", "checks": {}}

    timestamp = _timestamp(data.get("updated_at"))
    age = (datetime.now(timezone.utc) - timestamp).total_seconds() if timestamp else None
    try:
        allowed_age = max(30, min(3600, int(os.getenv("TB_MCP_MAX_AGE_SECONDS", "120"))))
    except ValueError:
        allowed_age = 120
    stale = age is None or age < -30 or age > allowed_age
    checks = {}
    for name in CHECK_NAMES:
        source = data["checks"].get(name)
        if not isinstance(source, dict):
            checks[name] = {"status": "UNKNOWN"}
            continue
        status = source.get("status")
        check = {"status": status if isinstance(status, str) and status in ALLOWED_STATUSES else "UNKNOWN"}
        for key in NUMERIC_FIELDS:
            number = source.get(key)
            if isinstance(number, (int, float)) and not isinstance(number, bool) and math.isfinite(number):
                if 0 <= number <= 10**15:
                    check[key] = round(number, 2)
        checks[name] = check
    overall = str(data.get("overall") or "UNKNOWN").upper()
    if overall not in ALLOWED_STATUSES:
        overall = "UNKNOWN"
    if stale:
        overall = "UNKNOWN"
    elif any(v["status"] == "ERROR" for v in checks.values()):
        overall = "ERROR"
    elif any(v["status"] == "WARN" for v in checks.values()) and overall == "OK":
        overall = "WARN"
    elif any(v["status"] == "UNKNOWN" for v in checks.values()) and overall == "OK":
        overall = "UNKNOWN"
    return {
        "overall": overall,
        "stale": stale,
        "age_seconds": round(age, 1) if age is not None else None,
        "updated_at": timestamp.isoformat() if timestamp else None,
        "checks": checks,
    }


def server_probleme() -> dict:
    """Summarize health and monitor anomalies without exposing raw log content."""
    health = server_gesundheit()
    monitor = monitor_uebersicht()
    issues = []
    for name in ("live", "ready"):
        state = health[name]["status"]
        if state != "OK":
            issues.append({"component": "health_" + name, "status": state})
    if monitor.get("stale"):
        issues.append({"component": "monitor", "status": "UNKNOWN", "reason": "veraltet"})
    elif monitor.get("reason"):
        issues.append({"component": "monitor", "status": "UNKNOWN", "reason": monitor["reason"]})
    for name, check in monitor["checks"].items():
        if check["status"] not in {"OK", "SKIP"}:
            issues.append({"component": name, "status": check["status"]})
    return {
        "status": "OK" if not issues and health["overall"] == "OK" and monitor["overall"] == "OK" else "PRUEFEN",
        "issues": issues,
        "health_overall": health["overall"],
        "monitor_overall": monitor["overall"],
    }
