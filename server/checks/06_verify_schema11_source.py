#!/usr/bin/env python3
"""Static contract check for Schema 11 personal user contacts."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
ANDROID = ROOT / "app" / "src" / "main"


def require(path: Path, needles: list[str]) -> None:
    if not path.is_file():
        raise AssertionError(f"MISSING_FILE:{path.relative_to(ROOT)}")
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"MISSING_CONTRACT:{path.relative_to(ROOT)}:{needle}")


def main() -> int:
    part = SERVER / "app_parts" / "18_part.py"
    compile(part.read_bytes(), str(part), "exec")

    require(
        SERVER / "migrations" / "011_user_contact_profile.sql",
        ["ADD COLUMN IF NOT EXISTS phone", "profile_version", "011_user_contact_profile"],
    )
    require(
        part,
        ["/api/v1/user/profile", "USER_PROFILE_SCHEMA_REQUIRED", "USER_EMAIL_ALREADY_USED"],
    )
    require(
        ANDROID / "java" / "de" / "tbhelfer" / "server" / "UserProfileApi.kt",
        ["/api/v1/user/profile", "SchemaRequired", "profileVersion"],
    )
    require(
        ANDROID / "java" / "de" / "tbhelfer" / "ui" / "einstellungen" / "nutzer" / "NutzerFragment.kt",
        ["etPersTelefon", "etPersEmail", "Persönliche Daten speichern", "Firmendaten vom Server aktualisieren"],
    )
    require(
        ANDROID / "res" / "layout" / "fragment_nutzer.xml",
        ["Mein Profil", "Persönliche Telefonnummer", "Firmentelefon", "Firmen-E-Mail"],
    )

    print("SCHEMA11_SOURCE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
