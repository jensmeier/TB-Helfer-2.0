from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    ast.parse(source, filename=rel) if rel.endswith(".py") else None
    return source


def main() -> None:
    devices = read("server/tbserver/devices.py")
    client_auth = read("server/tbserver/client_auth.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    migration = read("server/v1_migrations/002_device_credentials.sql")

    assertions = [
        ('/api/v1/device', devices),
        ('@router.post("/login")', devices),
        ('@router.get("/me")', devices),
        ('Authorization', devices),
        ('scheme.lower() != "bearer"', devices),
        ('hash_token(raw_device_token)', devices),
        ('DEVICE_ALREADY_BOUND', devices),
        ('DEVICE_IN_USE', devices),
        ('device_activation_tokens', devices),
        ('auth_token_hash = NULL', devices),
        ('/users/{user_id}/device-activation', devices),
        ('/devices/{device_id}/release', devices),
        ('/devices/{device_id}/block', devices),
        ('current_device_user', client_auth),
        ('current_portal_user', client_auth),
        ('CSRF_INVALID', client_auth),
        ('devices_router', main_source),
        ('admin_devices_router', main_source),
        ('"device_activation_v1": True', caps),
        ('"device_bearer_auth_v1": True', caps),
        ('auth_token_hash text', migration),
        ('devices_one_active_per_user_uq', migration),
        ("WHERE status = 'ACTIVE' AND revoked_at IS NULL", migration),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Device-Auth-Vertragsmarker: " + ", ".join(missing))

    forbidden = [
        '"token": device_token_hash',
        '"activation_key": key_hash',
    ]
    found_forbidden = [needle for needle in forbidden if needle in devices]
    if found_forbidden:
        raise SystemExit("Gehashte interne Geheimwerte werden falsch als Clienttoken ausgegeben")

    print("OK: TB-Server 1.0 device activation/auth foundation")


if __name__ == "__main__":
    main()

