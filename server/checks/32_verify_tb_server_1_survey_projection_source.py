from __future__ import annotations

import ast
import sys
from pathlib import Path
from uuid import UUID


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


def verify_tra_chainage_runtime() -> None:
    from tbserver.survey_tra_chainage import (
        densify_tra_chainage_payload,
        normalize_tra_station_payload,
        tra_station_discontinuities,
    )

    # Distilled from real 5302S: a positive 73.660-m station jump occurs at
    # exactly the same DB_REF position (raw type 6). It is not 73.660 m of rail.
    positive = {
        "nodes": [
            {
                "station_m": 17600.0, "x_dbref": 4_500_000.0, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 0, "length_m": 26.34,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
            {
                "station_m": 17626.34, "x_dbref": 4_500_026.34, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 6, "length_m": 73.66,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
            {
                "station_m": 17700.0, "x_dbref": 4_500_026.34, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 0, "length_m": 100.0,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
            {
                "station_m": 17800.0, "x_dbref": 4_500_126.34, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 0, "length_m": 0.0,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
        ]
    }
    jumps = tra_station_discontinuities(positive)
    assert len(jumps) == 1, jumps
    assert abs(float(jumps[0]["station_jump_m"]) - 73.66) < 0.0001, jumps
    normalized = normalize_tra_station_payload(positive)
    stations = [float(node["station_m"]) for node in normalized["nodes"]]
    assert abs(stations[1] - stations[2]) < 0.000001, stations
    assert abs(stations[3] - 17726.34) < 0.000001, stations
    samples = densify_tra_chainage_payload(positive)
    assert all(samples[index + 1]["station_m"] >= samples[index]["station_m"] for index in range(len(samples) - 1)), samples
    # Without normalization the Android-parity densifier would manufacture about
    # 37 same-coordinate samples across the 73.66-m source-station jump.
    at_jump = [sample for sample in samples if abs(sample["x_dbref"] - 4_500_026.34) < 0.001]
    assert len(at_jump) <= 2, at_jump

    # Distilled from real 8771/5300S: negative station equation at zero distance.
    negative = {
        "nodes": [
            {
                "station_m": 55606.095653, "x_dbref": 4_500_000.0, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 6, "length_m": -6.09559,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
            {
                "station_m": 55600.0000635, "x_dbref": 4_500_000.0000005, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 0, "length_m": 100.0,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
            {
                "station_m": 55700.0000635, "x_dbref": 4_500_100.0000005, "y_dbref": 5_300_000.0,
                "direction_deg": 90.0, "raw_type": 0, "length_m": 0.0,
                "radius_from_m": 0.0, "radius_to_m": 0.0,
            },
        ]
    }
    jumps = tra_station_discontinuities(negative)
    assert len(jumps) == 1 and abs(float(jumps[0]["station_jump_m"]) + 6.0955895) < 0.0001, jumps
    normalized = normalize_tra_station_payload(negative)
    assert abs(float(normalized["nodes"][0]["station_m"]) - float(normalized["nodes"][1]["station_m"])) < 0.00001, normalized
    assert float(normalized["nodes"][2]["station_m"]) > float(normalized["nodes"][1]["station_m"]), normalized


def verify_gra_jump_runtime() -> None:
    from tbserver.survey_gra_jumps import GraJumpRebuildRequest, estimate_jump_station, gra_jump_candidates

    payload = {
        "points": [
            {"km_meter": 17626.340, "height_m": 480.83693038, "parameter_m": 1000.0},
            {"km_meter": 17700.000, "height_m": 480.83693038, "parameter_m": 0.0},
        ]
    }
    candidates = gra_jump_candidates(payload)
    assert len(candidates) == 1, candidates
    jump = candidates[0]
    assert abs(jump["jump_delta_m"] - 73.66) < 0.001, jump

    anchors = [
        (12353.70325874, 16993.222),
        (12944.37757154, 17583.805),
        (13006.49940416, 17719.587),
        (13222.19666568, 17935.284),
    ]
    placement = estimate_jump_station(
        anchors,
        km_before_meter=17626.340,
        km_after_meter=17700.000,
        station_min_m=7518.07,
        station_max_m=14433.718,
        allow_one_sided=False,
    )
    assert placement is not None, placement
    assert placement["placement_method"] == "BOTH_SIDES", placement
    assert abs(placement["station_m"] - 12986.913) < 0.01, placement
    assert placement["two_side_agreement_m"] < 0.01, placement
    assert GraJumpRebuildRequest(base_version=1).allow_one_sided is False


def _tra_source(
    original_id: UUID,
    *,
    order: int,
    station_start_m: float,
    station_end_m: float,
    x_start: float,
    x_end: float,
) -> dict:
    return {
        "original_id": original_id,
        "role": "GEOMETRY",
        "order": order,
        "reverse_geometry": False,
        "parse_status": "PARSED",
        "detected_format": "TRA_ER0_BINARY",
        "original_route_number": "5300",
        "parse_payload": {
            "route_from_name": "5300",
            "track_from_name": "R",
            "nodes": [
                {
                    "station_m": station_start_m,
                    "x_dbref": x_start,
                    "y_dbref": 5_300_000.0,
                    "direction_deg": 90.0,
                    "raw_type": 0,
                    "length_m": abs(station_end_m - station_start_m),
                    "radius_from_m": 0.0,
                    "radius_to_m": 0.0,
                },
                {
                    "station_m": station_end_m,
                    "x_dbref": x_end,
                    "y_dbref": 5_300_000.0,
                    "direction_deg": 90.0,
                    "raw_type": 0,
                    "length_m": 0.0,
                    "radius_from_m": 0.0,
                    "radius_to_m": 0.0,
                },
            ],
        },
    }


def verify_station_merge_runtime() -> None:
    from tbserver.survey_projection import (
        _is_projection_generated,
        _is_station_dependent_generated,
        build_projection_points,
    )
    from tbserver.survey_station_merge import build_geometry_station_maps, merged_station_for

    primary_id = UUID("00000000-0000-0000-0000-000000000101")
    overlap_id = UUID("00000000-0000-0000-0000-000000000102")
    endpoint_id = UUID("00000000-0000-0000-0000-000000000103")
    km_id = UUID("00000000-0000-0000-0000-000000000201")
    dataset_id = UUID("00000000-0000-0000-0000-000000000301")

    primary = _tra_source(
        primary_id,
        order=0,
        station_start_m=1000.0,
        station_end_m=1200.0,
        x_start=4_500_000.0,
        x_end=4_500_200.0,
    )
    overlap = _tra_source(
        overlap_id,
        order=1,
        station_start_m=0.0,
        station_end_m=200.0,
        x_start=4_500_100.0,
        x_end=4_500_300.0,
    )
    endpoint = _tra_source(
        endpoint_id,
        order=2,
        station_start_m=0.0,
        station_end_m=100.0,
        x_start=4_500_300.0,
        x_end=4_500_400.0,
    )

    station_maps = build_geometry_station_maps([primary, overlap, endpoint])
    assert station_maps["station_basis"] == "CONTINUOUS_GEOMETRY_CHAINAGE", station_maps
    overlap_map = station_maps["maps"][str(overlap_id)]
    assert overlap_map["status"] == "OVERLAP_CONFIRMED", overlap_map
    assert overlap_map["reliable_for_auto_anchor"] is True, overlap_map
    assert abs(float(overlap_map["station_offset_m"]) - 1100.0) < 0.001, overlap_map
    mapped = merged_station_for(station_maps, overlap_id, 150.0)
    assert mapped is not None and abs(mapped[0] - 1250.0) < 0.001, mapped

    endpoint_map = station_maps["maps"][str(endpoint_id)]
    assert endpoint_map["status"] == "ENDPOINT_CONFIRMED", endpoint_map
    assert endpoint_map["reliable_for_auto_anchor"] is False, endpoint_map

    km_source = {
        "original_id": km_id,
        "role": "KM",
        "order": 0,
        "reverse_geometry": False,
        "parse_status": "PARSED",
        "detected_format": "KF_7_BINARY",
        "original_route_number": "5300",
        "parse_payload": {
            "points": [
                {
                    "record_index": 1,
                    "route": "5300",
                    "point_name": "OVERLAP-KM",
                    "km_meter": 55_250.0,
                    "x_dbref": 4_500_250.0,
                    "y_dbref": 5_300_000.0,
                    "coordinate_system": "DB_REF2016_4",
                },
                {
                    "record_index": 2,
                    "route": "5300",
                    "point_name": "ENDPOINT-ONLY-KM",
                    "km_meter": 55_350.0,
                    "x_dbref": 4_500_350.0,
                    "y_dbref": 5_300_000.0,
                    "coordinate_system": "DB_REF2016_4",
                },
            ]
        },
    }
    generated, summary = build_projection_points(
        dataset={"id": dataset_id, "route_number": "5300"},
        sources=[primary, overlap, endpoint, km_source],
        max_distance_m=15.0,
        reliable_distance_m=5.0,
    )
    assert summary["projection_version"] == "survey-projection-v3", summary
    assert summary["station_basis"] == "CONTINUOUS_GEOMETRY_CHAINAGE", summary
    assert summary["reliable_mapped_geometry_source_count"] == 2, summary
    assert summary["generated_original_anchors"] == 1, summary
    assert summary["generated_control_anchors"] == 1, summary
    original = next(point for point in generated if point["point_type"] == "ORIGINAL_ANCHOR")
    control = next(point for point in generated if point["point_type"] == "CONTROL_ANCHOR")
    assert abs(float(original["station_m"]) - 1250.0) < 0.2, original
    assert original["payload"]["geometry_station_basis"] == "CONTINUOUS_GEOMETRY_CHAINAGE", original
    assert original["payload"]["geometry_station_map_status"] == "OVERLAP_CONFIRMED", original
    assert original["payload"]["primary_geometry"] is False, original
    assert control["payload"]["geometry_station_map_status"] == "ENDPOINT_CONFIRMED", control
    assert control["payload"]["geometry_station_map_reliable"] is False, control

    legacy_projection = {"payload": {"generated_by": "survey-projection-v1"}}
    previous_projection = {"payload": {"generated_by": "survey-projection-v2"}}
    current_projection = {"payload": {"generated_by": "survey-projection-v3"}}
    auto_gra_jump = {"payload": {"generated_by": "survey-gra-jump-v2"}}
    manual_jump = {"point_type": "KM_JUMP", "payload": {}}
    assert _is_projection_generated(legacy_projection)
    assert _is_projection_generated(previous_projection)
    assert _is_projection_generated(current_projection)
    assert not _is_projection_generated(auto_gra_jump)
    assert _is_station_dependent_generated(legacy_projection)
    assert _is_station_dependent_generated(previous_projection)
    assert _is_station_dependent_generated(current_projection)
    assert _is_station_dependent_generated(auto_gra_jump)
    assert not _is_station_dependent_generated(manual_jump)


def main() -> None:
    geometry = read("server/tbserver/survey_geometry.py")
    chainage = read("server/tbserver/survey_tra_chainage.py")
    projection = read("server/tbserver/survey_projection.py")
    station_merge = read("server/tbserver/survey_station_merge.py")
    jumps = read("server/tbserver/survey_gra_jumps.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    android_tra = read("app/src/main/java/de/tbhelfer/vermessung/TraDateiParser.kt")
    android_converter = read("app/src/main/java/de/tbhelfer/vermessung/DbRefKoordinatenwandler.kt")

    ast.parse(geometry, filename="server/tbserver/survey_geometry.py")
    ast.parse(chainage, filename="server/tbserver/survey_tra_chainage.py")
    ast.parse(projection, filename="server/tbserver/survey_projection.py")
    ast.parse(station_merge, filename="server/tbserver/survey_station_merge.py")
    ast.parse(jumps, filename="server/tbserver/survey_gra_jumps.py")

    assertions = [
        ("SAMPLE_DISTANCE_M = 2.0", geometry),
        ("TRANSITION_STEP_M = 0.5", geometry),
        ("def densify_tra_payload", geometry),
        ("def dbref_to_wgs84", geometry),
        ("TX = 584.9636", geometry),
        ("TY = 107.7175", geometry),
        ("TZ = 413.8067", geometry),
        ("TRA_STATION_JUMP_RAW_TYPE = 6", chainage),
        ("TRA_STATION_JUMP_MAX_CHORD_M = 0.01", chainage),
        ("def tra_station_discontinuities", chainage),
        ("def normalize_tra_station_payload", chainage),
        ("def densify_tra_chainage_payload", chainage),
        ('PROJECTION_VERSION = "survey-projection-v3"', projection),
        ('PROJECTION_GENERATOR_PREFIX = "survey-projection-v"', projection),
        ('GRA_JUMP_GENERATOR_PREFIX = "survey-gra-jump-v"', projection),
        ('/survey-sections/{dataset_id}/rebuild-auto-anchors', projection),
        ("SURVEY_SECTION_REBUILD_AUTO_ANCHORS", projection),
        ('source["role"] == "KM"', projection),
        ("densify_tra_chainage_payload", projection),
        ("build_geometry_station_maps", projection),
        ("merged_station_for", projection),
        ("station_mapping_reliable", projection),
        ("skipped_unmapped_geometry", projection),
        ("geometry_station_basis", projection),
        ("geometry_station_map_status", projection),
        ("_is_station_dependent_generated", projection),
        ("invalidated_generated_gra_jump_count", projection),
        ("payload->>'generated_by' LIKE %s OR payload->>'generated_by' LIKE %s", projection),
        ('MERGE_VERSION = "survey-station-merge-v2"', station_merge),
        ("densify_tra_chainage_payload", station_merge),
        ("source_station_discontinuity_count", station_merge),
        ('"station_basis": "CONTINUOUS_GEOMETRY_CHAINAGE"', station_merge),
        ("OVERLAP_MAX_DISTANCE_M = 1.0", station_merge),
        ("OVERLAP_MIN_SPAN_M = 50.0", station_merge),
        ("OVERLAP_MAX_STATION_RESIDUAL_M = 0.5", station_merge),
        ('"status": "OVERLAP_CONFIRMED"', station_merge),
        ('"status": "ENDPOINT_CONFIRMED"', station_merge),
        ('"reliable_for_auto_anchor": False', station_merge),
        ('GRA_JUMP_VERSION = "survey-gra-jump-v2"', jumps),
        ("GRA_JUMP_PARAMETER_MARKER = 1000.0", jumps),
        ("GRA_JUMP_TWO_SIDE_AGREEMENT_M = 2.0", jumps),
        ("GRA_JUMP_REQUIRES_CURRENT_PROJECTION", jumps),
        ("GRA_JUMP_REQUIRES_CONTINUOUS_CHAINAGE", jumps),
        ("allow_one_sided: bool = False", jumps),
        ("AXIS_IDENTITY_UNCONFIRMED", jumps),
        ('/survey-sections/{dataset_id}/rebuild-gra-jumps', jumps),
        ("SURVEY_SECTION_REBUILD_GRA_JUMPS", jumps),
        ("GRA_JUMP_REVERSED_PRIMARY_GEOMETRY_NOT_SUPPORTED", jumps),
        ("survey_projection_router", main_source),
        ("survey_gra_jumps_router", main_source),
        ('"survey_projection_v1": True', caps),
        ('"survey_station_merge_v1": True', caps),
        ('"survey_gra_jump_detection_v1": True', caps),
        ("32_verify_tb_server_1_survey_projection_source.py", workflow),
        ('"survey_station_merge_v1"', workflow),
        ('"survey_gra_jump_detection_v1"', workflow),
        ("private const val SAMPLE_ABSTAND_M = 2.0", android_tra),
        ("private const val UEBERGANG_NUMERIK_SCHRITT_M = 0.5", android_tra),
        ("private const val TX = 584.9636", android_converter),
        ("private const val SCALE_PPM = 7.9922", android_converter),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Survey-Projektions-/Chainage-/Stationsmerge-/GRA-Sprung-Marker: " + ", ".join(missing))

    verify_geometry_runtime()
    verify_tra_chainage_runtime()
    verify_gra_jump_runtime()
    verify_station_merge_runtime()
    print("OK: TB-Server 1.0 TRA chainage + projection + station-space merge + conservative GRA KM-jump placement")


if __name__ == "__main__":
    main()
