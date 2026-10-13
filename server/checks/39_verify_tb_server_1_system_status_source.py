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
    system = read("server/tbserver/system_admin.py")
    main_source = read("server/tbserver/main.py")
    web = read("server/tbserver/admin_web.py")
    template = read("server/templates/v1_system_status.html")
    base = read("server/templates/v1_base.html")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    assertions = [
        ('APIRouter(prefix="/api/v1/admin/system"', system),
        ('@router.get("/status")', system),
        ('Depends(require_admin)', system),
        ("shutil.disk_usage", system),
        ("pg_database_size(current_database())", system),
        ("FROM tb.backup_runs", system),
        ("FROM tb.background_jobs", system),
        ("FROM tb.server_settings", system),
        ("_SAFE_SETTING_KEYS", system),
        ("last_device_seen", system),
        ("last_sync", system),
        ('result.pop("last_error", None)', system),
        ('"error_description"', system),
        ("system_admin_router", main_source),
        ('@router.get("/admin/v1/system")', web),
        ('/api/v1/admin/system/status', template),
        ('href="/admin/v1/system"', base),
        ('"admin_system_status_v1": True', caps),
        ("39_verify_tb_server_1_system_status_source.py", workflow),
        ('"admin_system_status_v1"', workflow),
        ("CREATE TABLE tb.background_jobs", baseline),
        ("CREATE TABLE tb.backup_runs", baseline),
        ("CREATE TABLE tb.server_settings", baseline),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Systemstatus-/Speicher-Marker: " + ", ".join(missing)
        )

    forbidden = [
        "db_password",
        '"DB_PASSWORD":',
        'os.environ.get("DB_PASSWORD")',
        "smtp_password",
        "token_hash",
        "storage_key AS",
        "payload jsonb",
    ]
    exposed = [needle for needle in forbidden if needle in system]
    if exposed:
        raise SystemExit(
            "Systemstatus darf keine Geheimnisse/interne Jobpayloads ausgeben: "
            + ", ".join(exposed)
        )

    print("OK: TB-Server 1.0 admin system status/storage contract")


if __name__ == "__main__":
    main()
