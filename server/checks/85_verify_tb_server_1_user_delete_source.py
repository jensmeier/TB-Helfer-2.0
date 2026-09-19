from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    admin = read("server/tbserver/admin.py")
    users = read("server/templates/v1_admin_users.html")
    migration = read("server/v1_migrations/010_user_soft_delete_login.sql")
    rules = read("docs/TB-HELFER_PROJEKTREGELN.md")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('@router.delete("/users/{user_id}", status_code=204)', admin),
        ('"ADMIN_USER_CANNOT_BE_DELETED"', admin),
        ('"CANNOT_DELETE_SELF"', admin),
        ('str(admin["user_id"]) == str(user_id)', admin),
        ('"USER_HAS_ACTIVE_BTB"', admin),
        ("UPDATE tb.portal_sessions", admin),
        ("UPDATE tb.portal_credentials", admin),
        ("UPDATE tb.device_activation_tokens", admin),
        ("UPDATE tb.devices", admin),
        ("token_version = token_version + 1", admin),
        ("UPDATE tb.worksite_memberships", admin),
        ("UPDATE tb.team_members", admin),
        ("UPDATE tb.teams", admin),
        ("UPDATE tb.worksite_invitations", admin),
        ("status = 'DISABLED'", admin),
        ("deleted_at = now()", admin),
        ('class="danger delete-user"', users),
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
        raise SystemExit("Benutzer-Loeschpfad verwendet veraltetes admin[id] statt admin[user_id]")
    if missing:
        raise SystemExit(
            "Fehlende sichere Benutzer-Loeschmarker: " + ", ".join(missing)
        )

    print("OK: Server 1.0 safe user delete + login reuse")


if __name__ == "__main__":
    main()
