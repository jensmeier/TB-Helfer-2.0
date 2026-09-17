from __future__ import annotations

import ast
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def verify_geometry_runtime() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.survey_geometry import dbref_to_wgs84, densify_tra_payload, project_dbref_point_to_tra

    payload = {
        "nodes": [
            {
                "station_m": 1000.0,
                "x_dbref": 4_500_000.0,
                "y_dbref": 5_300_000.0,
                "direction_deg": 90.0,
                "raw_type": 0,
                "length_m": 10.0,
                "radius_from_m": 0.0,
                "radius_to_m": 0.0,
            },
            {
                "station_m": 1010.0,
                "x_dbref": 4_500_010.0,
                "y_dbref": 5_300_000.0,
                "direction_deg": 90.0,
                "raw_type": 0,
                "length_m": 0.0,
                "radius_from_m": 0.0,
                "radius_to_m": 0.0,
            },
        ]
    }
    samples = densify_tra_payload(payload)
    assert [sample["station_m"] for sample in samples] == [1000.0, 1002.0, 1004.0, 1006.0, 1008.0, 1010.0], samples

    exact = project_dbref_point_to_tra(4_500_004.0, 5_300_000.0, payload)
    assert exact is not None, exact
    assert abs(float(exact["station_m"]) - 1004.0) < 0.1, exact
    assert float(exact["distance_m"]) < 0.1, exact
    assert exact["quality"] == "GRUEN", exact

    offset = project_dbref_point_to_tra(4_500_004.0, 5_300_004.5, payload)
    assert offset is not None, offset
    assert 4.0 < float(offset["distance_m"]) < 5.1, offset
    assert offset["quality"] == "GELB", offset

    zone3 = dbref_to_wgs84(3_597_538.6261, 5_280_599.5563)
    assert zone3 is not None, zone3
    assert 45.0 < zone3[0] < 56.0 and 4.0 < zone3[1] < 17.0, zone3


def main() -> None:
    geometry = read("server/tbserver/survey_geometry.py")
    projection = read("server/tbserver/survey_projection.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    android_tra = read("app/src/main/java/de/tbhelfer/vermessung/TraDateiParser.kt")
    android_converter = read("app/src/main/java/de/tbhelfer/vermessung/DbRefKoordinatenwandler.kt")

    ast.parse(geometry, filename="server/tbserver/survey_geometry.py")
    ast.parse(projection, filename="server/tbserver/survey_projection.py")

    assertions = [
        ("SAMPLE_DISTANCE_M = 2.0", geometry),
        ("TRANSITION_STEP_M = 0.5", geometry),
        ("def densify_tra_payload", geometry),
        ("def dbref_to_wgs84", geometry),
        ("TX = 584.9636", geometry),
        ("TY = 107.7175", geometry),
        ("TZ = 413.8067", geometry),
        ('PROJECTION_VERSION = "survey-projection-v1"', projection),
        ('/survey-sections/{dataset_id}/rebuild-auto-anchors', projection),
        ("SURVEY_SECTION_REBUILD_AUTO_ANCHORS", projection),
        ('source["role"] == "KM"', projection),
        ("geometry_id == primary_geometry_id", projection),
        ("route_confirmed", projection),
        ('point_type = "ORIGINAL_ANCHOR" if reliable else "CONTROL_ANCHOR"', projection),
        ("payload->>'generated_by' = %s", projection),
        ("skipped_no_xy", projection),
        ("survey_projection_router", main_source),
        ('"survey_projection_v1": True', caps),
        ("32_verify_tb_server_1_survey_projection_source.py", workflow),
        ("private const val SAMPLE_ABSTAND_M = 2.0", android_tra),
        ("private const val UEBERGANG_NUMERIK_SCHRITT_M = 0.5", android_tra),
        ("private const val TX = 584.9636", android_converter),
        ("private const val SCALE_PPM = 7.9922", android_converter),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Survey-Projektionsmarker: " + ", ".join(missing))

    verify_geometry_runtime()
    print("OK: TB-Server 1.0 Android-parity TRA densification + DB_REF projection")


if __name__ == "__main__":
    main()
