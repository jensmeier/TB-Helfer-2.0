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
        ('BACKUP_KINDS = ', backup),
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
        ('STALE_RECOVERY_CHECK_SECONDS = 60.0', jobs),
        ('WORKER_ERROR_RETRY_SECONDS = 5.0', jobs),
        ('JOB_STATE_UPDATE_ATTEMPTS = 5', jobs),
        ('def _retry_job_state_update', jobs),
        ('worker startup database recovery failed; worker stays alive', jobs),
        ('worker stale-job recovery failed; worker stays alive', jobs),
        ('worker loop iteration failed; worker stays alive and retries', jobs),
        ('next_stale_recovery_check', jobs),
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
    # Neue getrennte Backup-Domaenen duerfen die Basisarten nicht verdraengen.
    tree = ast.parse(backup, filename="server/tbserver/backup.py")
    kinds = next(
        (node.value for node in tree.body if isinstance(node, ast.Assign)
         and any(isinstance(target, ast.Name) and target.id == "BACKUP_KINDS"
                 for target in node.targets)),
        None,
    )
    if kinds is None or not isinstance(kinds, (ast.Set, ast.Constant)):
        raise SystemExit("Backup-Arten muessen als statische Menge definiert sein")
    available_kinds = ast.literal_eval(kinds)
    if not {"DATABASE", "FILES", "FULL", "USER", "TRACK", "SYSTEM"}.issubset(available_kinds):
        raise SystemExit("Die Basis- und Bereichsbackup-Arten muessen erhalten bleiben")

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
    if '"storage_key"' in status_block:
        raise SystemExit("Systemstatus darf Backup-Pfade nicht offenlegen")
    if 'result.pop("last_error", None)' not in system or 'jobs = [_job_view(dict(row))' not in status_block:
        raise SystemExit(
            "Rohe Job-Fehler muessen vor der Admin-Antwort serverseitig entfernt werden"
        )
    if "last_error" in template:
        raise SystemExit("Admin-Template darf rohe Job-Fehler nicht anzeigen")

    print("OK: TB-Server 1.0 background jobs + backup contract")


if __name__ == "__main__":
    main()
