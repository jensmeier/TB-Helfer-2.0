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
    config = read("server/tbserver/config.py")
    backup = read("server/tbserver/backup.py")
    jobs = read("server/tbserver/jobs.py")
    system = read("server/tbserver/system_admin.py")
    template = read("server/templates/v1_system_status.html")
    migration = read("server/v1_migrations/006_backup_retention_status.sql")
    compose = read("server/docker-compose.v1.yml")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("TB_BACKUP_RETENTION_DAYS", config),
        ("TB_BACKUP_RETENTION_MIN_PER_KIND", config),
        ("def run_retention()", backup),
        ("row_number() OVER", backup),
        ("minimum_kept_per_kind", backup),
        ("status = 'PURGED'", backup),
        ("previous_storage_key", backup),
        ('"BACKUP_RETENTION"', jobs),
        ("return run_retention()", jobs),
        ('@router.post("/backups/retention", status_code=202)', system),
        ('receipt_type="BACKUP_RETENTION_JOB_CREATE"', system),
        ('action="BACKUP_RETENTION_JOB_CREATE"', system),
        ('"backup_retention": {', system),
        ("startRetention", template),
        ("Aufbewahrung", template),
        ("'PURGED'", migration),
        ("TB_V1_BACKUP_RETENTION_DAYS", compose),
        ("TB_V1_BACKUP_RETENTION_MIN_PER_KIND", compose),
        ('"backup_retention_v1": True', caps),
        ("45_verify_tb_server_1_backup_retention_source.py", workflow),
        ('"backup_retention_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Backup-Retention-Marker: " + ", ".join(missing)
        )

    retention = backup[
        backup.index("def run_retention"):
    ]
    if "rmtree(settings.backup_root" in retention:
        raise SystemExit("Retention darf niemals den gesamten Backup-Root loeschen")

    if "detail" in system[system.index("def admin_system_status"):system.index('@router.post("/backups"')]:
        # The SQL must not select backup_runs.detail. The word may occur in
        # comments/other contexts, so check the exact column marker.
        status_block = system[
            system.index("def admin_system_status"):
            system.index('@router.post("/backups"')
        ]
        if "started_at, finished_at, detail" in status_block:
            raise SystemExit("Retention-Detail darf nicht im normalen Status leaken")

    print("OK: TB-Server 1.0 backup retention contract")


if __name__ == "__main__":
    main()
