from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    compose = read("server/docker-compose.synology-staging.v1.yml")
    base_compose = read("server/docker-compose.v1.yml")
    docs = read("docs/TB-SERVER_1_0_DEPLOYMENT.md")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("name: tb-helfer-v1", compose),
        ("TB_V1_SOURCE_TBSERVER_DIR", compose),
        ("TB_V1_SOURCE_TEMPLATES_DIR", compose),
        ("TB_V1_SOURCE_MIGRATIONS_DIR", compose),
        ("/app/tbserver:ro", compose),
        ("/app/templates:ro", compose),
        ("/app/v1_migrations:ro", compose),
        ("./database-v1", compose),
        ("./storage-v1", compose),
        ("./backups-v1", compose),
        ("restore-test-db:", compose),
        ("restore-test-database-v1", compose),
        ("TB_RESTORE_TEST_DB_NAME", compose),
        ("TB_RESTORE_TEST_DB_PASSWORD", compose),
        ("cloudflared:", compose),
        ("restart: always", compose),
        ('restart: "on-failure:5"', compose),
        ("condition: service_completed_successfully", compose),
        ("docker-compose.synology-staging.v1.yml", docs),
        ("82_verify_tb_server_1_synology_staging_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Synology-Staging-Marker: " + ", ".join(missing)
        )

    # Production/cutover compose must stay image-based and must not bind-mount source.
    forbidden_in_base = [
        "TB_V1_SOURCE_TBSERVER_DIR",
        "TB_V1_SOURCE_TEMPLATES_DIR",
        "TB_V1_SOURCE_MIGRATIONS_DIR",
    ]
    leaked = [needle for needle in forbidden_in_base if needle in base_compose]
    if leaked:
        raise SystemExit(
            "Produktions-Compose darf keine Staging-Source-Mounts enthalten: "
            + ", ".join(leaked)
        )

    forbidden_in_synology = [
        "TB_RESTORE_TARGET_",
        "TB_ALLOW_RESTORE_TARGET",
        "restore-target-storage-v1",
        "/tb/restore-target-v1",
    ]
    leaked_synology = [needle for needle in forbidden_in_synology if needle in compose]
    if leaked_synology:
        raise SystemExit(
            "Normaler Synology-Start darf kein entferntes Restore-Target mounten: "
            + ", ".join(leaked_synology)
        )

    cloudflared_section = compose.split("  cloudflared:", 1)[1]
    if "depends_on:" in cloudflared_section:
        raise SystemExit(
            "cloudflared muss auf Synology unabhängig von API-Health starten können"
        )

    print("OK: TB-Server 1.0 Synology staging source-mount deployment")


if __name__ == "__main__":
    main()
