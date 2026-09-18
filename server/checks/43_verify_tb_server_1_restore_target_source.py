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
    target = read("server/tbserver/restore_target.py")
    restore = read("server/tbserver/restore.py")
    migration = read("server/v1_migrations/005_restore_target_run_kind.sql")
    workflow = read(".github/workflows/tb-server-v1.yml")
    caps = read("server/tbserver/capabilities.py")

    assertions = [
        ('TB_ALLOW_RESTORE_TARGET', target),
        ('TB_RESTORE_TARGET_DB_NAME', target),
        ('TB_RESTORE_TARGET_DB_PASSWORD', target),
        ('TB_RESTORE_TARGET_STORAGE_ROOT', target),
        ('RESTORE_TARGET_DATABASE_MUST_DIFFER_FROM_LIVE', target),
        ('RESTORE_TARGET_STORAGE_MUST_DIFFER_FROM_LIVE', target),
        ('RESTORE_TARGET_STORAGE_MUST_DIFFER_FROM_BACKUPS', target),
        ('RESTORE_TARGET_DATABASE_NOT_FRESH', target),
        ('RESTORE_TARGET_STORAGE_NOT_EMPTY', target),
        ('RESTORE_TARGET_REQUIRES_FULL_BACKUP', target),
        ('RESTORE-TARGET:', target),
        ('_run_pg_restore(database_dump, config, clean=False)', target),
        ('_verify_restored_database(config)', target),
        ('shutil.copytree(source_storage, target, symlinks=False)', target),
        ('action="RESTORE_TARGET_START"', target),
        ('action="RESTORE_TARGET_OK"', target),
        ('action="RESTORE_TARGET_FAILED"', target),
        ("VALUES ('RESTORE_TARGET', 'RUNNING', %s)", target),
        ('clean: bool = True', restore),
        ('if clean:', restore),
        ("'RESTORE_TARGET'", migration),
        ('"restore_target_v1": True', caps),
        ('43_verify_tb_server_1_restore_target_source.py', workflow),
        ('"restore_target_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Restore-Target-Marker: " + ", ".join(missing)
        )

    if "@router." in target:
        raise SystemExit(
            "Kontrollierter Restore darf nicht als normale HTTP-API exponiert werden"
        )

    if "settings.db_password" in target:
        raise SystemExit(
            "Restore-Target darf nicht still das Live-DB-Passwort verwenden"
        )

    print("OK: TB-Server 1.0 controlled restore-to-target contract")


if __name__ == "__main__":
    main()
