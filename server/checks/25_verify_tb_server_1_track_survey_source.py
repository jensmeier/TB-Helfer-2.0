from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    survey = read("server/tbserver/survey_originals.py")
    admin_survey = read("server/tbserver/admin_survey_import.py")
    survey_preview = read("server/tbserver/survey_preview.py")
    admin_template = read("server/templates/v1_admin_survey_import.html")
    track = read("server/tbserver/track_data.py")
    sections = read("server/tbserver/survey_sections.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    ast.parse(survey, filename="server/tbserver/survey_originals.py")
    ast.parse(admin_survey, filename="server/tbserver/admin_survey_import.py")
    ast.parse(survey_preview, filename="server/tbserver/survey_preview.py")
    ast.parse(track, filename="server/tbserver/track_data.py")
    ast.parse(sections, filename="server/tbserver/survey_sections.py")

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
        ('"global_survey_reference_v1": True', caps),
        ('"survey_import_preview_v1": True', caps),
        ('/analyze', admin_survey),
        ('/analysis/{analysis_id}/import', admin_survey),
        ('class SurveyImportPlan', admin_survey),
        ('build_survey_preview', survey_preview),
        ('"height_m"', survey_preview),
        ('"point_id"', survey_preview),
        ('"source_name"', survey_preview),
        ('/reference/survey-originals', survey),
        ('GLOBAL_SURVEY_REFERENCE_NOT_FOUND', survey),
        ('GLOBAL_REFERENCE', admin_survey),
        ('COMPANY_WORKSITE_DATA', admin_survey),
        ('company_id IS NULL', admin_survey),
        ('id="startAnalysis"', admin_template),
        ('id="surveyMap"', admin_template),
        ('id="toggleFullscreen"', admin_template),
        ('renderDynamicLabels', admin_template),
        ('pointDetailText', admin_template),
        ('axisLabelCandidates', admin_template),
        ('pointLabelCandidates', admin_template),
        ('pointMarkerCandidates', admin_template),
        ('markerScreenRadius', admin_template),
        ('Math.max(33.333', admin_template),
        ('validRange', admin_template),
        ('Belastbare KM-Bereiche', admin_template),
        ('Achse(n) ohne belastbaren KM-Bereich', admin_template),
        ('decision-scope', admin_template),
        ('/survey-import/analyze', admin_template),
        ('/analysis/', admin_template),
        ('cache_revision', survey),
        ('row["company_id"] is not None and row["company_id"] != company_id', sections),
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

    print("OK: TB-Server 1.0 immutable survey originals + file-first graphical preview + versioned track data")


if __name__ == "__main__":
    main()
