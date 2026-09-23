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


def _env_optional(name: str) -> str | None:
    raw = os.getenv(name)
    if raw is None:
        return None
    value = raw.strip()
    return value or None


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    value = raw.strip().lower()
    if value in {"1", "true", "yes", "on"}:
        return True
    if value in {"0", "false", "no", "off"}:
        return False
    raise RuntimeError(f"{name} ungültig")


@dataclass(frozen=True)
class Settings:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    storage_root: Path
    backup_root: Path
    backup_retention_days: int
    backup_retention_min_per_kind: int
    migrations_root: Path
    environment: str
    log_level: str
    portal_session_hours: int
    public_base_url: str | None
    portal_public_base_url: str | None
    smtp_host: str | None
    smtp_port: int
    smtp_user: str | None
    smtp_password: str | None
    smtp_sender: str | None
    smtp_starttls: bool
    smtp_ssl: bool

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
            backup_retention_days=max(1, _env_int("TB_BACKUP_RETENTION_DAYS", 30)),
            backup_retention_min_per_kind=max(
                1, _env_int("TB_BACKUP_RETENTION_MIN_PER_KIND", 3)
            ),
            migrations_root=Path(
                _env("TB_MIGRATIONS_ROOT", str(server_root / "v1_migrations"))
            ),
            environment=_env("TB_ENVIRONMENT", "development").lower(),
            log_level=_env("LOG_LEVEL", "INFO").upper(),
            portal_session_hours=max(1, _env_int("TB_PORTAL_SESSION_HOURS", 12)),
            public_base_url=_env_optional("TB_PUBLIC_BASE_URL"),
            portal_public_base_url=_env_optional("TB_PORTAL_PUBLIC_BASE_URL"),
            smtp_host=_env_optional("TB_SMTP_HOST"),
            smtp_port=_env_int("TB_SMTP_PORT", 587),
            smtp_user=_env_optional("TB_SMTP_USER"),
            smtp_password=_env_optional("TB_SMTP_PASSWORD"),
            smtp_sender=_env_optional("TB_SMTP_SENDER"),
            smtp_starttls=_env_bool("TB_SMTP_STARTTLS", True),
            smtp_ssl=_env_bool("TB_SMTP_SSL", False),
        )


settings = Settings.from_env()
