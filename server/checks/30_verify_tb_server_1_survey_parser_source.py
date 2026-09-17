from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    parser = read("server/tbserver/survey_parser.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    migration = read("server/v1_migrations/003_survey_parse_catalog.sql")

    ast.parse(parser, filename="server/tbserver/survey_parser.py")

    assertions = [
        ("CREATE TABLE tb.survey_parse_catalog", migration),
        ("REFERENCES tb.survey_originals(id) ON DELETE RESTRICT", migration),
        ("PARSER_VERSION = \"tb-survey-parser-1\"", parser),
        ("TRA_ER0_BINARY", parser),
        ("GRA_BINARY", parser),
        ("KF_7_BINARY", parser),
        ("KF_TEXT", parser),
        ("KV_TEXT", parser),
        ("GVP_TEXT", parser),
        ("KOR_RAW_NO_TEXT_PARSER", parser),
        ("SURVEY_ORIGINAL_INTEGRITY_MISMATCH", parser),
        ("_hash_file(path)", parser),
        ("ON CONFLICT (original_id) DO UPDATE", parser),
        ("survey_parser_router", main_source),
        ('"survey_parser_catalog_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Vermessungsparser-Vertragsmarker: " + ", ".join(missing))

    forbidden = [
        "UPDATE tb.survey_originals",
        "DELETE FROM tb.survey_originals",
        "INSERT INTO tb.survey_originals",
        "os.replace",
    ]
    present = [needle for needle in forbidden if needle in parser]
    if present:
        raise SystemExit("Parser darf Vermessungsoriginale nicht mutieren: " + ", ".join(present))

    if 'kind in {"GVP", "KF", "KV", "KF.TXT", "FESTPUNKT"}' not in parser:
        raise SystemExit("KF/KV muessen Teil der gemeinsamen GVP/Festpunkt-Parserfamilie bleiben")

    print("OK: TB-Server 1.0 immutable survey parser catalog")


if __name__ == "__main__":
    main()
