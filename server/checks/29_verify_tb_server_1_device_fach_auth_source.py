from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    ast.parse(source, filename=rel)
    return source


def main() -> None:
    portal = read("server/tbserver/portal.py")
    devices = read("server/tbserver/devices.py")
    caps = read("server/tbserver/capabilities.py")

    assertions = [
        ("_device_auth_allowed_for_path", portal),
        ('path.startswith("/api/v1/portal")', portal),
        ('path.startswith("/api/v1/admin")', portal),
        ("from .devices import current_device_user", portal),
        ('user.get("auth_kind") == "DEVICE"', portal),
        ("return current_device_user(request)", portal),
        ('result["auth_kind"] = "PORTAL"', portal),
        ('result["auth_kind"] = "DEVICE"', devices),
        ('"device_fach_api_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende kombinierte Portal-/Device-Auth-Marker: " + ", ".join(missing))

    block_start = portal.index("def _device_auth_allowed_for_path")
    block_end = portal.index("def current_portal_user", block_start)
    guard = portal[block_start:block_end]
    if "/api/v1/admin" not in guard or "/api/v1/portal" not in guard:
        raise SystemExit("Admin- und Portal-Pfade müssen von Device-Bearer-Auth ausgeschlossen bleiben")

    print("OK: device bearer auth is enabled only for fach API paths")


if __name__ == "__main__":
    main()
