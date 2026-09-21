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
    restore = read("server/tbserver/restore.py")
    reset = read("server/tbserver/reset_test.py")
    jobs = read("server/tbserver/jobs.py")
    system = read("server/tbserver/system_admin.py")
    template = read("server/templates/v1_system_status.html")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('FULL_BACKUP_FORMAT = "tb-server-1.0-full-backup-v1"', restore),
        ('TB_RESTORE_TEST_DB_NAME', restore),
        ('TB_RESTORE_TEST_DB_PASSWORD', restore),
        ('RESTORE_TEST_DATABASE_MUST_DIFFER_FROM_LIVE', restore),
        ('DROP SCHEMA IF EXISTS tb CASCADE', restore),
        ('"pg_restore"', restore),
        ('"--clean"', restore),
        ('"--if-exists"', restore),
        ('"--exit-on-error"', restore),
        ('env["PGPASSWORD"] = str(config["password"])', restore),
        ('FULL_BACKUP_DATABASE_SHA256_MISMATCH', restore),
        ('FULL_BACKUP_FILES_SHA256_MISMATCH', restore),
        ('RESTORE_TEST_ARCHIVE_PATH_INVALID', restore),
        ('RESTORE_TEST_ARCHIVE_LINK_NOT_ALLOWED', restore),
        ('archive.extractall(target, members=members, filter="data")', restore),
        ('INSERT INTO tb.backup_runs(kind, status, detail)', restore),
        ('VALUES (\'RESTORE_TEST\', \'RUNNING\', %s)', restore),
        ('"RESTORE_TEST"', jobs),
        ('run_restore_test(source_run_id)', jobs),
        ('from uuid import UUID', system),
        ('@router.post("/backups/{backup_run_id}/restore-test"', system),
        ('receipt_type="RESTORE_TEST_JOB_CREATE"', system),
        ('action="RESTORE_TEST_JOB_CREATE"', system),
        ('startRestoreTest', template),
        ('RESET_ALLOWED_ENVIRONMENTS = {"development", "test", "staging"}', reset),
        ('RESET_BACKUP_MAX_AGE_HOURS = 24', reset),
        ('FULL_BACKUP_REQUIRED_BEFORE_RESET', reset),
        ('FULL_BACKUP_TOO_OLD_FOR_RESET', reset),
        ('RESET-TEST-DATA:', reset),
        ('DROP SCHEMA IF EXISTS tb CASCADE', reset),
        ('_clear_storage_contents(settings.storage_root)', reset),
        ('BACKUP_ROOT_MUST_BE_OUTSIDE_STORAGE', reset),
        ('migrate()', reset),
        ('"restore_test_v1": True', caps),
        ('"test_data_reset_v1": True', caps),
        ('42_verify_tb_server_1_restore_test_reset_source.py', workflow),
        ('"restore_test_v1"', workflow),
        ('"test_data_reset_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Restore-Test-/Reset-Marker: " + ", ".join(missing)
        )

    command_block = restore[
        restore.index("def _run_pg_restore"):
        restore.index("def _verify_restored_database")
    ]
    if "settings.db_password" in command_block:
        raise SystemExit(
            "Live-DB-Passwort darf nicht als pg_restore-Argument verwendet werden"
        )

    reset_guard = reset[
        reset.index("def _assert_environment"):
        reset.index("def _load_full_backup")
    ]
    for required in ("development", "test", "staging"):
        if required not in reset_guard and required not in reset:
            raise SystemExit("Reset-Umgebungsgrenze unvollstaendig")
    if "production" in reset_guard:
        raise SystemExit("Production darf nicht als erlaubte Reset-Umgebung vorkommen")

    print("OK: TB-Server 1.0 restore-test + test reset safety contract")


if __name__ == "__main__":
    main()
