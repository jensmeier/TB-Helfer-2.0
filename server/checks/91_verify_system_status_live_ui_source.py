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
    jobs = read("server/tbserver/jobs.py")
    backup = read("server/tbserver/backup.py")
    restore = read("server/tbserver/restore.py")
    system = read("server/tbserver/system_admin.py")
    system_ui = read("server/templates/v1_system_status.html")
    release_ui = read("server/templates/v1_releases.html")
    migration = read("server/v1_migrations/013_job_progress.sql")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("progress_percent", migration),
        ("progress_text", migration),
        ("def _set_progress", jobs),
        ("run_backup("FULL", progress=progress)", jobs),
        ("run_restore_test(source_run_id, progress=progress)", jobs),
        ("ProgressCallback", backup),
        ("ProgressCallback", restore),
        ('@router.post("/warnings/acknowledge")', system),
        ('@router.delete("/jobs/history"', system),
        ('@router.delete("/jobs/{job_id}"', system),
        ("ACTIVE_BACKGROUND_JOB_CANNOT_BE_DELETED", system),
        ("Fehlgeschlagene Vorgänge", system),
        ("job_label", system),
        ("error_description", system),
        ("Live-Aktualisierung", system_ui),
        ("progress_percent", system_ui),
        ("Erledigten Job-Verlauf aufräumen", system_ui),
        ("Aktuelle Warnungen quittieren", system_ui),
        ("Sicherheits-/Audit-Nachweis anzeigen", system_ui),
        ("showArchived", release_ui),
        ("Archivierte anzeigen", release_ui),
        ("013_job_progress.sql", workflow),
        ("91_verify_system_status_live_ui_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Live-Systemstatus-/Job-Marker: " + ", ".join(missing)
        )

    if "DELETE FROM tb.audit_log" in system:
        raise SystemExit("Audit-Nachweis darf durch Systemstatus-Aufraeumen nicht geloescht werden")
    if "status IN ('PENDING','RUNNING')" in system and "DELETE FROM tb.background_jobs" in system:
        raise SystemExit("Aktive Background Jobs duerfen nicht geloescht werden")

    print("OK: live system status, German job errors, safe history cleanup and release archive")


if __name__ == "__main__":
    main()
