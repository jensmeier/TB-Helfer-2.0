from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    defects = read("server/tbserver/defects.py")
    acceptances = read("server/tbserver/acceptances.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    ast.parse(defects, filename="server/tbserver/defects.py")
    ast.parse(acceptances, filename="server/tbserver/acceptances.py")

    assertions = [
        ("CREATE TABLE tb.defects", baseline),
        ("CREATE TABLE tb.acceptances", baseline),
        ("/worksites/{worksite_id}/defects", defects),
        ("/defects/{defect_id}/close", defects),
        ("defect.create", defects),
        ("defect.edit", defects),
        ("defect.close", defects),
        ("VERSION_CONFLICT", defects),
        ("/worksites/{worksite_id}/acceptances", acceptances),
        ("/acceptances/{acceptance_id}/finalize", acceptances),
        ("acceptance.create", acceptances),
        ("acceptance.edit", acceptances),
        ("acceptance.finalize", acceptances),
        ("VERSION_CONFLICT", acceptances),
        ("defects_router", main_source),
        ("acceptances_router", main_source),
        ('"defects_v1": True', caps),
        ('"acceptances_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Mängel-/Abnahme-Vertragsmarker: " + ", ".join(missing))
    print("OK: TB-Server 1.0 defects/acceptances source contract")


if __name__ == "__main__":
    main()
