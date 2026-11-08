from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    compose = read("server/docker-compose.v1.yml")
    dockerfile = read("server/Dockerfile.v1")
    docs = read("docs/TB-SERVER_1_0_DEPLOYMENT.md")
    workflow = read(".github/workflows/tb-server-v1.yml")
    migrations = read("server/tbserver/migrations.py")

    assertions = [
        ("postgis/postgis:17-3.5", compose),
        ("TB_V1_DB_PASSWORD", compose),
        ("TB_V1_PUBLIC_BASE_URL", compose),
        ("${TB_V1_DB_PASSWORD:?", compose),
        ("./database-v1", compose),
        ("./storage-v1", compose),
        ("./backups-v1", compose),
        ("TB_BACKUP_ROOT: /tb/backups-v1", compose),
        ("STORAGE_ROOT: /tb/storage-v1", compose),
        ('command: ["python", "-m", "tbserver.migrations", "up"]', compose),
        ('restart: "on-failure:5"', compose),
        ("condition: service_completed_successfully", compose),
        ('command: ["python", "-m", "tbserver.jobs", "worker"]', compose),
        ("http://127.0.0.1:8080/health/ready", compose),
        ("TB_V1_API_BIND:-127.0.0.1", compose),
        ("postgresql-client-17", dockerfile),
        ("COPY templates ./templates", dockerfile),
        ("Restore-to-Target", docs),
        ("TB_V1_PUBLIC_BASE_URL=https://api-v1.tb-helfer.de", docs),
        ("44_verify_tb_server_1_deployment_source.py", workflow),
        ("DB_CONNECT_ATTEMPTS", migrations),
        ("psycopg.OperationalError", migrations),
        ("WAITING_DATABASE", migrations),
        ("time.sleep(DB_CONNECT_RETRY_SECONDS)", migrations),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Server-1.0-Deployment-Marker: " + ", ".join(missing)
        )

    forbidden_literals = [
        "postgres_password: testpass",
        "db_password: testpass",
        "password123",
        "cloudflare-token",
        "tailscale-key",
    ]
    exposed = [needle for needle in forbidden_literals if needle in compose.lower()]
    if exposed:
        raise SystemExit("Compose enthaelt feste Geheimwerte")

    if "restore_target restore-target" in compose:
        raise SystemExit(
            "Restore-to-Target darf nicht automatisch im normalen Compose-Start laufen"
        )

    print("OK: TB-Server 1.0 Docker/NAS staging deployment contract")


if __name__ == "__main__":
    main()
