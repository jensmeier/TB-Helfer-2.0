from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    survey = read("server/tbserver/survey_originals.py")
    track = read("server/tbserver/track_data.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    ast.parse(survey, filename="server/tbserver/survey_originals.py")
    ast.parse(track, filename="server/tbserver/track_data.py")

    assertions = [
        ("CREATE TABLE tb.survey_originals", baseline),
        ("CREATE TABLE tb.track_data", baseline),
        ("source_original_id uuid REFERENCES tb.survey_originals", baseline),
        ("/survey-originals/from-file", survey),
        ("SOURCE_FILE_INTEGRITY_MISMATCH", survey),
        ("SURVEY_ORIGINAL_ALREADY_IMPORTED", survey),
        ("reference/survey-originals", survey),
        ("os.replace", survey),
        ("_hash_file", survey),
        ("/worksites/{worksite_id}/track-data", track),
        ("base_version", track),
        ("VERSION_CONFLICT", track),
        ("source_original_id", track),
        ("SURVEY_ORIGINAL_COMPANY_MISMATCH", track),
        ("deletion_tombstones", track),
        ("survey_originals_router", main_source),
        ("track_data_router", main_source),
        ('"track_data_v1": True', caps),
        ('"survey_originals_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Strecken-/Vermessungsvertragsmarker: " + ", ".join(missing))

    forbidden_original_mutations = [
        '@router.patch("/survey-originals/',
        '@router.put("/survey-originals/',
        '@router.delete("/survey-originals/',
        "UPDATE tb.survey_originals",
        "DELETE FROM tb.survey_originals",
    ]
    present = [needle for needle in forbidden_original_mutations if needle in survey]
    if present:
        raise SystemExit("Vermessungsoriginale duerfen nicht mutierbar sein: " + ", ".join(present))

    print("OK: TB-Server 1.0 immutable survey originals + versioned track data")


if __name__ == "__main__":
    main()
