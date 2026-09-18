from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    base = read("server/docker-compose.v1.yml")
    overlay = read("server/docker-compose.restore-test.v1.yml")
    deployment = read("docs/TB-SERVER_1_0_DEPLOYMENT.md")
    restore = read("server/tbserver/restore.py")
    restore_target = read("server/tbserver/restore_target.py")

    assertions = [
        ("restore-test-db:", overlay),
        ("restore-target-db:", overlay),
        ("image: postgis/postgis:17-3.5", overlay),
        ("TB_V1_RESTORE_TEST_DB_PASSWORD", overlay),
        ("TB_V1_RESTORE_TARGET_DB_PASSWORD", overlay),
        ("TB_RESTORE_TEST_DB_HOST: restore-test-db", overlay),
        ("TB_RESTORE_TARGET_DB_HOST: restore-target-db", overlay),
        ("TB_RESTORE_TARGET_STORAGE_ROOT: /tb/restore-target-v1", overlay),
        ("TB_ALLOW_RESTORE_TARGET: ${TB_V1_ALLOW_RESTORE_TARGET:-NO}", overlay),
        ("restore-test-database-v1", overlay),
        ("restore-target-database-v1", overlay),
        ("restore-target-storage-v1", overlay),
        ("condition: service_healthy", overlay),
        ("TB_RESTORE_TEST_DB_NAME", restore),
        ("TB_RESTORE_TARGET_DB_NAME", restore_target),
        ("TB_ALLOW_RESTORE_TARGET", restore_target),
        ("docker-compose.restore-test.v1.yml", deployment),
        ("TB_V1_ALLOW_RESTORE_TARGET=NO", deployment),
        ("RESTORE-TARGET:<uuid>:<target-db-name>", deployment),
        ("docker-compose.v1.yml", base),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Restore-Compose-Staging-Marker: " + ", ".join(missing)
        )

    forbidden = [
        "ports:\n      -",
        "TB_ALLOW_RESTORE_TARGET: YES",
        "POSTGRES_PASSWORD: tb_helfer",
        "DB_PASSWORD: tb_helfer",
    ]
    bad = [needle for needle in forbidden if needle in overlay]
    if bad:
        raise SystemExit(
            "Restore-Staging-Overlay ist zu offen oder enthält feste Geheimwerte: "
            + ", ".join(bad)
        )

    if "./database-v1" in overlay or "./storage-v1" in overlay or "./backups-v1" in overlay:
        raise SystemExit(
            "Restore-Staging-Ziele dürfen keine normalen Live-/Backup-Pfade wiederverwenden"
        )

    if "TB_RESTORE_TARGET_" in base or "restore-target-db" in base:
        raise SystemExit(
            "Restore-to-Target darf nicht in den normalen Compose-Start eingebaut sein"
        )

    print("OK: TB-Server 1.0 optional restore staging compose overlay")


if __name__ == "__main__":
    main()
