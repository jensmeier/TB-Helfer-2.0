from __future__ import annotations

import argparse
import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

from .config import settings
from .database import connect

MIGRATION_NAME_RE = re.compile(r"^(?P<version>\d{3})_[a-z0-9_]+\.sql$")
ADVISORY_LOCK_ID = 731019001


@dataclass(frozen=True)
class Migration:
    version: int
    name: str
    path: Path
    checksum: str
    sql: str


def discover_migrations(root: Path | None = None) -> list[Migration]:
    root = root or settings.migrations_root
    if not root.exists():
        raise RuntimeError(f"Migrationsverzeichnis fehlt: {root}")

    result: list[Migration] = []
    seen: set[int] = set()
    for path in sorted(root.glob("*.sql")):
        match = MIGRATION_NAME_RE.match(path.name)
        if not match:
            raise RuntimeError(f"Ungültiger Migrationsname: {path.name}")
        version = int(match.group("version"))
        if version in seen:
            raise RuntimeError(f"Doppelte Migrationsversion: {version:03d}")
        seen.add(version)
        raw = path.read_bytes()
        result.append(
            Migration(
                version=version,
                name=path.name,
                path=path,
                checksum=hashlib.sha256(raw).hexdigest(),
                sql=raw.decode("utf-8"),
            )
        )
    if not result:
        raise RuntimeError(f"Keine Migrationen gefunden: {root}")
    return result


def _ensure_registry(conn) -> None:
    with conn.cursor() as cur:
        cur.execute("CREATE SCHEMA IF NOT EXISTS tb")
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS tb.schema_migrations (
                version integer PRIMARY KEY,
                name text NOT NULL UNIQUE,
                checksum_sha256 text NOT NULL,
                applied_at timestamptz NOT NULL DEFAULT now()
            )
            """
        )


def verify_migrations(conn, migrations: list[Migration]) -> None:
    _ensure_registry(conn)
    with conn.cursor() as cur:
        cur.execute(
            "SELECT version, name, checksum_sha256 FROM tb.schema_migrations ORDER BY version"
        )
        applied = {row["version"]: row for row in cur.fetchall()}

    known = {m.version: m for m in migrations}
    unknown = sorted(set(applied) - set(known))
    if unknown:
        raise RuntimeError(
            "Datenbank enthält Migrationen, die im Code fehlen: "
            + ", ".join(f"{version:03d}" for version in unknown)
        )

    for version, row in applied.items():
        migration = known[version]
        if row["name"] != migration.name:
            raise RuntimeError(
                f"Migration {version:03d}: Name geändert "
                f"({row['name']} != {migration.name})"
            )
        if row["checksum_sha256"] != migration.checksum:
            raise RuntimeError(
                f"Migration {version:03d}: Prüfsumme stimmt nicht. "
                "Bereits ausgeführte Migrationen dürfen nicht verändert werden."
            )


def migrate() -> list[str]:
    migrations = discover_migrations()
    applied_now: list[str] = []

    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT pg_advisory_lock(%s)", (ADVISORY_LOCK_ID,))
        try:
            verify_migrations(conn, migrations)
            with conn.cursor() as cur:
                cur.execute("SELECT version FROM tb.schema_migrations")
                applied_versions = {row["version"] for row in cur.fetchall()}

            for migration in migrations:
                if migration.version in applied_versions:
                    continue
                with conn.transaction():
                    with conn.cursor() as cur:
                        cur.execute(migration.sql)
                        cur.execute(
                            """
                            INSERT INTO tb.schema_migrations(version, name, checksum_sha256)
                            VALUES (%s, %s, %s)
                            """,
                            (migration.version, migration.name, migration.checksum),
                        )
                applied_now.append(migration.name)

            verify_migrations(conn, migrations)
            return applied_now
        finally:
            with conn.cursor() as cur:
                cur.execute("SELECT pg_advisory_unlock(%s)", (ADVISORY_LOCK_ID,))


def status() -> dict:
    migrations = discover_migrations()
    with connect() as conn:
        verify_migrations(conn, migrations)
        with conn.cursor() as cur:
            cur.execute(
                "SELECT version, name, checksum_sha256, applied_at "
                "FROM tb.schema_migrations ORDER BY version"
            )
            rows = cur.fetchall()
    applied_versions = {row["version"] for row in rows}
    return {
        "known": len(migrations),
        "applied": len(rows),
        "pending": [m.name for m in migrations if m.version not in applied_versions],
        "latest_known": migrations[-1].version,
        "latest_applied": max(applied_versions) if applied_versions else 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="TB-Server 1.0 Migration Runner")
    parser.add_argument("command", choices=("up", "status"))
    args = parser.parse_args()
    if args.command == "up":
        applied = migrate()
        print("OK")
        for name in applied:
            print(f"APPLIED {name}")
    else:
        print(status())


if __name__ == "__main__":
    main()
