from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    required = [
        "server/tbserver/security.py",
        "server/tbserver/portal.py",
        "server/tbserver/admin.py",
        "server/tbserver/bootstrap.py",
    ]
    for rel in required:
        source = read(rel)
        ast.parse(source, filename=rel)

    portal = read("server/tbserver/portal.py")
    admin = read("server/tbserver/admin.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")

    assertions = [
        ("/api/v1/portal", portal),
        ("/activate", portal),
        ("/login", portal),
        ("LOGIN_LOCKED", portal),
        ("X-CSRF-Token", portal),
        ("portal_sessions", portal),
        ("/api/v1/admin", admin),
        ("portal-activation", admin),
        ("CHEF", admin),
        ("portal_router", main_source),
        ("admin_router", main_source),
        ('"portal_v1": True', caps),
        ('"company_user_admin_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Auth-/Portal-Vertragsmarker: " + ", ".join(missing))
    print("OK: TB-Server 1.0 Auth/Portal foundation")


if __name__ == "__main__":
    main()
