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
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    dockerfile = read("server/Dockerfile.v1")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    assertions = [
        ('backup_root: Path', config),
        ('TB_BACKUP_ROOT', config),
        ('BACKUP_KINDS = {"DATABASE", "FILES", "FULL"}', backup),
        ('BACKUP_ROOT_MUST_BE_OUTSIDE_STORAGE', backup),
        ('env["PGPASSWORD"] = settings.db_password', backup),
        ('"pg_dump"', backup),
        ('"--format=custom"', backup),
        ('tarfile.open(target, "w:gz"', backup),
        ('"tb-server-1.0-full-backup-v1"', backup),
        ('"database_sha256"', backup),
        ('"files_sha256"', backup),
        ('os.replace(temp_path, final_path)', backup),
        ('backup_path_for_run', backup),
        ('FOR UPDATE SKIP LOCKED', jobs),
        ('MAX_JOB_ATTEMPTS = 5', jobs),
        ('STALE_RUNNING_MINUTES = 30', jobs),
        ('BACKUP_DATABASE', jobs),
        ('BACKUP_FILES', jobs),
        ('BACKUP_FULL', jobs),
        ('def recover_stale_jobs', jobs),
        ('def worker_loop', jobs),
        ('def enqueue_in_tx', jobs),
        ('@router.post("/backups", status_code=202)', system),
        ('Depends(require_admin_csrf)', system),
        ('receipt_type="BACKUP_JOB_CREATE"', system),
        ('action="BACKUP_JOB_CREATE"', system),
        ('"backup_storage": backup_storage', system),
        ("/api/v1/admin/system/backups", template),
        ("Vollbackup", template),
        ('"background_jobs_v1": True', caps),
        ('"backup_v1": True', caps),
        ('41_verify_tb_server_1_backup_jobs_source.py', workflow),
        ('"background_jobs_v1"', workflow),
        ('"backup_v1"', workflow),
        ('TB_BACKUP_ROOT:', workflow),
        ('postgresql-client-17', dockerfile),
        ('CREATE TABLE tb.background_jobs', baseline),
        ('CREATE TABLE tb.backup_runs', baseline),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Backup-/Background-Job-Marker: " + ", ".join(missing)
        )

    command_block = backup[
        backup.index("def _run_pg_dump"):
        backup.index("def _archive_storage")
    ]
    if "settings.db_password" in command_block:
        raise SystemExit("DB-Passwort darf nicht als pg_dump-Argument verwendet werden")

    status_block = system[
        system.index("def admin_system_status"):
        system.index('@router.post("/backups"')
    ]
    if '"storage_key"' in status_block or '"last_error"' in status_block:
        raise SystemExit(
            "Systemstatus darf Backup-Pfade/Job-Fehlerdetails nicht offenlegen"
        )

    print("OK: TB-Server 1.0 background jobs + backup contract")


if __name__ == "__main__":
    main()
