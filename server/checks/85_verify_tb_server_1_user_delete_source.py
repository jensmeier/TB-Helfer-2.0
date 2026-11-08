from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    if rel.endswith(".py"):
        ast.parse(source, filename=rel)
    return source


def main() -> None:
    admin = read("server/tbserver/admin.py")
    portal = read("server/tbserver/portal.py")
    users = read("server/templates/v1_admin_users.html")
    migration = read("server/v1_migrations/010_user_soft_delete_login.sql")
    rules = read("docs/TB-HELFER_PROJEKTREGELN.md")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('@router.post("/companies", status_code=201)', admin),
        ('action="COMPANY_CREATE"', admin),
        ('@router.post("/users", status_code=201)', admin),
        ('action="USER_CREATE"', admin),
        ('"PORTAL_ALREADY_ACTIVATED"', admin),
        ('action="PORTAL_ACTIVATION_ISSUE"', admin),
        ('@router.delete("/users/{user_id}", status_code=204)', admin),
        ('"ADMIN_USER_CANNOT_BE_DELETED"', admin),
        ('"CANNOT_DELETE_SELF"', admin),
        ('"USER_HAS_ACTIVE_BTB"', admin),
        ('str(admin["user_id"]) == str(user_id)', admin),
        ("UPDATE tb.portal_sessions", admin),
        ("UPDATE tb.portal_credentials", admin),
        ("UPDATE tb.device_activation_tokens", admin),
        ("UPDATE tb.devices", admin),
        ("auth_token_hash = NULL", admin),
        ("auth_token_hint = NULL", admin),
        ("token_version = token_version + 1", admin),
        ("UPDATE tb.worksite_memberships", admin),
        ("UPDATE tb.team_members", admin),
        ("UPDATE tb.teams", admin),
        ("invited_user_id = %s OR invited_by = %s", admin),
        ("status = 'DISABLED'", admin),
        ("deleted_at = now()", admin),
        ('action="USER_SOFT_DELETE"', admin),
        ('AND u.deleted_at IS NULL', portal),
        ('{% if not u.activated_at %}', users),
        ('class="secondary delete-user"', users),
        ("method:'DELETE'", users),
        ("Gerät wird freigegeben", users),
        ("WHERE deleted_at IS NULL", migration),
        ("CREATE UNIQUE INDEX users_login_name_uq", migration),
        ("Benutzer löschen", rules),
        ("soft-deleted", rules),
        ("85_verify_tb_server_1_user_delete_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if 'admin["id"]' in admin:
        raise SystemExit(
            'Admin-API verwendet veraltetes admin["id"] statt admin["user_id"]'
        )
    if "WHERE lower(u.login_name) = lower(%s)\n        \"\"\"" in portal:
        raise SystemExit(
            "Portal-Loginname-Suche filtert soft-geloeschte Benutzer nicht aus"
        )
    if missing:
        raise SystemExit(
            "Fehlende Admin-/Benutzer-Loeschmarker: " + ", ".join(missing)
        )

    print("OK: Server 1.0 admin core + safe user delete + login reuse")


if __name__ == "__main__":
    main()
