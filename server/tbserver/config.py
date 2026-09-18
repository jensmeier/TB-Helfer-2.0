from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _env(name: str, default: str | None = None) -> str:
    value = os.getenv(name, default)
    if value is None or not value.strip():
        raise RuntimeError(f"{name} fehlt")
    return value.strip()


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    return default if raw is None or not raw.strip() else int(raw)


@dataclass(frozen=True)
class Settings:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    storage_root: Path
    backup_root: Path
    migrations_root: Path
    environment: str
    log_level: str
    portal_session_hours: int

    @classmethod
    def from_env(cls) -> "Settings":
        package_root = Path(__file__).resolve().parent
        server_root = package_root.parent
        return cls(
            db_host=_env("DB_HOST", "db"),
            db_port=_env_int("DB_PORT", 5432),
            db_name=_env("DB_NAME", "tb_helfer_v1"),
            db_user=_env("DB_USER", "tb_helfer"),
            db_password=_env("DB_PASSWORD"),
            storage_root=Path(_env("STORAGE_ROOT", "/tb/storage-v1")),
            backup_root=Path(_env("TB_BACKUP_ROOT", "/tb/backups-v1")),
            migrations_root=Path(
                _env("TB_MIGRATIONS_ROOT", str(server_root / "v1_migrations"))
            ),
            environment=_env("TB_ENVIRONMENT", "development").lower(),
            log_level=_env("LOG_LEVEL", "INFO").upper(),
            portal_session_hours=max(1, _env_int("TB_PORTAL_SESSION_HOURS", 12)),
        )


settings = Settings.from_env()
