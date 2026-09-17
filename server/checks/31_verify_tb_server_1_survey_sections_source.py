from __future__ import annotations

import ast
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def verify_calibration_logic() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.survey_calibration import evaluate_calibration, km_at_station

    original = [
        {"point_type": "ORIGINAL_ANCHOR", "station_m": 1000.0, "km_meter": 70_000.0},
        {"point_type": "ORIGINAL_ANCHOR", "station_m": 2000.0, "km_meter": 71_000.0},
    ]
    result = evaluate_calibration(
        original,
        station_start_m=1000.0,
        station_end_m=2000.0,
        km_direction=None,
    )
    assert result["status"] == "AUTOMATISCH", result
    assert result["color"] == "GRUEN", result
    assert abs((km_at_station(result, original, 1500.0) or 0.0) - 70_500.0) < 0.00001, result

    manual = [
        {"point_type": "MANUAL_ANCHOR", "station_m": 1000.0, "km_meter": 70_000.0},
        {"point_type": "MANUAL_ANCHOR", "station_m": 1100.0, "km_meter": 70_100.0},
    ]
    result = evaluate_calibration(
        manual,
        station_start_m=1000.0,
        station_end_m=1100.0,
        km_direction=None,
    )
    assert result["status"] == "MANUELL" and result["color"] == "ORANGE", result

    one_anchor = [{"point_type": "MANUAL_ANCHOR", "station_m": 1000.0, "km_meter": 70_000.0}]
    result = evaluate_calibration(
        one_anchor,
        station_start_m=1000.0,
        station_end_m=1200.0,
        km_direction="FALLEND",
    )
    assert result["mode"] == "MANUELL_ANKER_RICHTUNG", result
    assert result["factor_km_per_station_m"] == -1.0, result
    assert abs((km_at_station(result, one_anchor, 1100.0) or 0.0) - 69_900.0) < 0.00001, result

    with_jump = [
        {"point_type": "ORIGINAL_ANCHOR", "station_m": 1000.0, "km_meter": 70_000.0},
        {
            "point_type": "KM_JUMP",
            "station_m": 1050.0,
            "km_meter": 70_050.0,
            "km_after_jump_meter": 70_150.0,
        },
        {"point_type": "ORIGINAL_ANCHOR", "station_m": 1100.0, "km_meter": 70_200.0},
    ]
    result = evaluate_calibration(
        with_jump,
        station_start_m=1000.0,
        station_end_m=1100.0,
        km_direction=None,
    )
    assert result["color"] == "GRUEN", result
    assert abs(result["factor_km_per_station_m"] - 1.0) < 0.00001, result
    assert abs((km_at_station(result, with_jump, 1100.0) or 0.0) - 70_200.0) < 0.00001, result

    missing = evaluate_calibration(
        [],
        station_start_m=0.0,
        station_end_m=100.0,
        km_direction=None,
    )
    assert missing["color"] == "ROT" and missing["status"] == "FEHLT", missing

    no_geometry = evaluate_calibration(
        [],
        station_start_m=None,
        station_end_m=None,
        km_direction=None,
        has_geometry=False,
    )
    assert no_geometry["color"] == "GRAU", no_geometry


def main() -> None:
    migration = read("server/v1_migrations/004_survey_sections.sql")
    calibration = read("server/tbserver/survey_calibration.py")
    sections = read("server/tbserver/survey_sections.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    ast.parse(calibration, filename="server/tbserver/survey_calibration.py")
    ast.parse(sections, filename="server/tbserver/survey_sections.py")

    assertions = [
        ("CREATE TABLE tb.track_data_sources", migration),
        ("CREATE TABLE tb.track_data_km_points", migration),
        ("REFERENCES tb.survey_originals(id) ON DELETE RESTRICT", migration),
        ("POINT_ORIGINAL = \"ORIGINAL_ANCHOR\"", calibration),
        ("POINT_JUMP = \"KM_JUMP\"", calibration),
        ("STATUS_AUTOMATISCH", calibration),
        ("COLOR_GRUEN", calibration),
        ("COLOR_ORANGE", calibration),
        ("COLOR_ROT", calibration),
        ("COLOR_GRAU", calibration),
        ('SECTION_KIND = "SURVEY_SECTION"', sections),
        ("SURVEY_SECTION_REQUIRES_GEOMETRY_SOURCE", sections),
        ("SURVEY_GEOMETRY_REQUIRES_TRA", sections),
        ("ORIGINAL_ANCHOR_REQUIRES_SOURCE", sections),
        ("geometry_merge_diagnostics", sections),
        ("SURVEY_SECTION_CREATE", sections),
        ("SURVEY_SECTION_UPDATE", sections),
        ("begin_mutation", sections),
        ("finish_mutation", sections),
        ("survey_sections_router", main_source),
        ('"survey_section_calibration_v1": True', caps),
        ("latest_known\"] == 4", workflow),
        ("31_verify_tb_server_1_survey_sections_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Survey-Section-Vertragsmarker: " + ", ".join(missing))

    verify_calibration_logic()
    print("OK: TB-Server 1.0 survey section links + KM calibration")


if __name__ == "__main__":
    main()
