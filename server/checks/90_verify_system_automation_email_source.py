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
    settings = read("server/tbserver/system_settings.py")
    automation = read("server/tbserver/automation.py")
    notifications = read("server/tbserver/notifications.py")
    jobs = read("server/tbserver/jobs.py")
    system = read("server/tbserver/system_admin.py")
    config = read("server/tbserver/config.py")
    backup = read("server/tbserver/backup.py")
    caps = read("server/tbserver/capabilities.py")
    template = read("server/templates/v1_system_status.html")
    migration = read("server/v1_migrations/012_system_automation.sql")
    compose = read("server/docker-compose.v1.yml")
    staging = read("server/docker-compose.synology-staging.v1.yml")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("admin_automation", settings),
        ("backup_retention_days", settings),
        ("smtp_status", settings),
        ("system_schedule_runs", migration),
        ("schedule_due_automation", automation),
        ("AUTO_BACKUP", automation),
        ("AUTO_RETENTION", automation),
        ("EMAIL_REPORT_", automation),
        ("SYSTEM_EMAIL_REPORT", jobs),
        ("run_system_email_report", jobs),
        ("smtplib", notifications),
        ("EmailMessage", notifications),
        ("SYSTEM_EMAIL_TEST_SENT", notifications),
        ('@router.put("/automation")', system),
        ('@router.post("/email/test"', system),
        ('"expiring_users"', system),
        ('"audit_events"', system),
        ("retention_policy()", backup),
        ('"system_automation_v1": True', caps),
        ('"system_email_reports_v1": True', caps),
        ("TB_SMTP_PASSWORD", config),
        ("TB_SMTP_PASSWORD", compose),
        ("TB_SMTP_PASSWORD", staging),
        ("Automatik & E-Mail-Berichte", template),
        ("Bald ablaufende Nutzer", template),
        ("Sicherheits-/Audit-Nachweis anzeigen", template),
        ("90_verify_system_automation_email_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Automatik-/E-Mail-Marker: " + ", ".join(missing))

    if "smtp_password" in migration.lower():
        raise SystemExit("SMTP-Passwort darf niemals in der Datenbankmigration gespeichert werden")
    if "password:" in template.lower() or "tb_v1_smtp_password:" in template.lower():
        raise SystemExit("Admin-Template darf kein SMTP-Passwort enthalten")
    if "settings.smtp_password" in system:
        raise SystemExit("Admin-API darf das SMTP-Passwort nicht ausgeben")

    print("OK: Server 1.0 system automation, email reports and admin monitoring")


if __name__ == "__main__":
    main()
