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
    metrics = read("server/tbserver/metrics.py")
    main_source = read("server/tbserver/main.py")
    system = read("server/tbserver/system_admin.py")
    web = read("server/tbserver/admin_web.py")
    dashboard = read("server/templates/v1_admin_dashboard.html")
    synology_compose = read("server/docker-compose.synology-staging.v1.yml")
    base = read("server/templates/v1_base.html")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("class SystemMetricsMiddleware", metrics),
        ("INSERT INTO tb.system_metrics_hourly", metrics),
        ("request_count", metrics),
        ("error_count", metrics),
        ("bytes_in", metrics),
        ("bytes_out", metrics),
        ("status_code >= 500", metrics),
        ("Monitoring must never make an otherwise valid API request fail", metrics),
        ("app.add_middleware(SystemMetricsMiddleware)", main_source),
        ("generate_series(", system),
        ('"traffic_24h"', system),
        ('"traffic_series"', system),
        ('"uptime_seconds"', system),
        ('"issues"', system),
        ('"last_successful_backup"', system),
        ('"public_base_url"', system),
        ("def _synology_disk_temperatures", system),
        ('"disk_temperatures"', system),
        ('"releases"', system),
        ('"survey_originals"', system),
        ('"trash_items"', system),
        ('"v1_admin_dashboard.html"', web),
        ("Speicher, Datenverkehr, Laufzeit, Probleme", dashboard),
        ("Upload · 24 h", dashboard),
        ("Download · 24 h", dashboard),
        ("Datenverkehr · letzte 24 Stunden", dashboard),
        ("Probleme & Warnungen", dashboard),
        ("Festplatten-Temperaturen", dashboard),
        ("diskTemperatures", dashboard),
        ("renderChart", dashboard),
        ("/run/synostorage/disks", synology_compose),
        ("/host/synostorage/disks:ro", synology_compose),
        ('href="/admin/v1">Übersicht</a>', base),
        ("CREATE TABLE tb.system_metrics_hourly", baseline),
        ("86_verify_tb_server_1_admin_overview_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Admin-Übersicht-/Metrik-Marker: " + ", ".join(missing)
        )

    forbidden_dashboard = [
        "cdn.jsdelivr.net",
        "cdnjs.cloudflare.com",
        "unpkg.com",
    ]
    if "<span>Trassierungsdaten '+esc(size(c.survey_bytes||0))" in dashboard:
        raise SystemExit("Globale Trassierungsdaten dürfen nicht als Firmen-Speicherkategorie angezeigt werden")

    external = [needle for needle in forbidden_dashboard if needle in dashboard]
    if external:
        raise SystemExit(
            "Admin-Übersicht darf keine externe Chart-Abhängigkeit laden: "
            + ", ".join(external)
        )

    print("OK: Server 1.0 admin overview + hourly traffic metrics")


if __name__ == "__main__":
    main()
