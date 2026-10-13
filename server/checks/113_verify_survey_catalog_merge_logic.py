from __future__ import annotations

from datetime import date

from tbserver.survey_catalog import (
    _axis_compatible,
    _axis_identity_enrichment_patch,
    _should_reassign_existing_segment_identity,
    _catalog_response,
    _explicit_km_gra,
    _geometry_fully_covers,
    _project_xy_station,
    _relation,
    _source_date_from_name,
)


def point(x: float, y: float, station: float) -> dict:
    return {"x_dbref": x, "y_dbref": y, "station_m": station}


base = [
    point(4_000_000.0, 5_000_000.0, 0.0),
    point(4_000_020.0, 5_000_000.0, 20.0),
    point(4_000_040.0, 5_000_000.0, 40.0),
    point(4_000_060.0, 5_000_000.0, 60.0),
    point(4_000_080.0, 5_000_000.0, 80.0),
    point(4_000_100.0, 5_000_000.0, 100.0),
]

overlap_and_extend = [
    point(4_000_060.2, 5_000_000.1, 0.0),
    point(4_000_080.2, 5_000_000.1, 20.0),
    point(4_000_100.2, 5_000_000.1, 40.0),
    point(4_000_120.2, 5_000_000.1, 60.0),
    point(4_000_140.2, 5_000_000.1, 80.0),
]
relation, details = _relation(base, overlap_and_extend)
assert relation == "OVERLAP_CONFIRMED", (relation, details)
assert details["overlap_span_m"] >= 30.0

endpoint_continue = [
    point(4_000_100.0, 5_000_000.0, 0.0),
    point(4_000_120.0, 5_000_000.0, 20.0),
    point(4_000_140.0, 5_000_000.0, 40.0),
]
relation, details = _relation(base, endpoint_continue)
assert relation == "ENDPOINT_CONFIRMED", (relation, details)
assert details["gap_m"] <= 0.01
assert details["heading_error_deg"] <= 0.01

wrong_heading = [
    point(4_000_100.0, 5_000_000.0, 0.0),
    point(4_000_100.0, 5_000_020.0, 20.0),
    point(4_000_100.0, 5_000_040.0, 40.0),
]
relation, details = _relation(base, wrong_heading)
assert relation == "REVIEW_REQUIRED", (relation, details)
assert details["reason"] == "ENDPOINT_HEADING_MISMATCH"

independent = [
    point(4_001_000.0, 5_001_000.0, 0.0),
    point(4_001_020.0, 5_001_000.0, 20.0),
]
relation, details = _relation(base, independent)
assert relation == "INDEPENDENT", (relation, details)

projection = _project_xy_station(4_000_050.0, 5_000_003.0, base)
assert projection is not None
assert abs(projection["station_m"] - 50.0) < 0.001
assert abs(projection["distance_m"] - 3.0) < 0.001

assert _source_date_from_name("2026-03-31_5302_L.tra") == date(2026, 3, 31)
assert _source_date_from_name("31.03.2026_5302_L.tra") == date(2026, 3, 31)
assert _explicit_km_gra("5300_KM5300_R.GRA")
assert _explicit_km_gra("Pasing_KMLINIE.GRA")
assert not _explicit_km_gra("Pasing_O00.GRA")

partial_cover = [
    point(4_000_020.0, 5_000_000.0, 20.0),
    point(4_000_040.0, 5_000_000.0, 40.0),
    point(4_000_060.0, 5_000_000.0, 60.0),
    point(4_000_080.0, 5_000_000.0, 80.0),
    point(4_000_100.0, 5_000_000.0, 100.0),
    point(4_000_120.0, 5_000_000.0, 120.0),
]
fully_covered, coverage = _geometry_fully_covers(base, partial_cover)
assert not fully_covered, coverage
assert coverage["coverage_ratio"] < 1.0

full_cover = [
    point(3_999_980.2, 5_000_000.1, -20.0),
    point(4_000_000.2, 5_000_000.1, 0.0),
    point(4_000_020.2, 5_000_000.1, 20.0),
    point(4_000_040.2, 5_000_000.1, 40.0),
    point(4_000_060.2, 5_000_000.1, 60.0),
    point(4_000_080.2, 5_000_000.1, 80.0),
    point(4_000_100.2, 5_000_000.1, 100.0),
    point(4_000_120.2, 5_000_000.1, 120.0),
]
fully_covered, coverage = _geometry_fully_covers(base, full_cover)
assert fully_covered, coverage
assert coverage["coverage_ratio"] == 1.0

private_payload = {
    "display_name": "Gl 28",
    "created_by_user_id": "user-secret-id",
    "created_by_company_id": "company-secret-id",
    "metadata": {"resolved_by": "admin-secret-id", "source": "MANUAL"},
}
public_payload = _catalog_response({"company_role": "USER"}, private_payload)
assert public_payload["display_name"] == "Gl 28"
assert "created_by_user_id" not in public_payload
assert "created_by_company_id" not in public_payload
assert "resolved_by" not in public_payload["metadata"]
admin_payload = _catalog_response({"company_role": "ADMIN"}, private_payload)
assert admin_payload["created_by_user_id"] == "user-secret-id"

wvb_axis = {
    "route_number": None,
    "station_code": "MPB",
    "track_key": None,
    "object_key": "WVB:14-22",
    "direction": None,
    "axis_type": "WEICHENVERBINDUNG",
    "bbox_min_x": 4_000_000.0,
    "bbox_min_y": 5_000_000.0,
    "bbox_max_x": 4_000_100.0,
    "bbox_max_y": 5_000_010.0,
}
assert _axis_compatible(
    wvb_axis,
    {
        "route_number": None,
        "station_code": "MPB",
        "track_key": None,
        "object_key": "WVB:14-22",
        "direction": None,
        "axis_type": "WEICHENVERBINDUNG",
    },
    (4_000_020.0, 5_000_000.0, 4_000_080.0, 5_000_010.0),
)
assert not _axis_compatible(
    wvb_axis,
    {
        "route_number": None,
        "station_code": "MPB",
        "track_key": None,
        "object_key": "WVB:10-11",
        "direction": None,
        "axis_type": "WEICHENVERBINDUNG",
    },
    (4_000_020.0, 5_000_000.0, 4_000_080.0, 5_000_010.0),
)



missing_object_axis = {
    "object_key": None,
    "axis_type": "WEICHENVERBINDUNG",
}
assert _axis_identity_enrichment_patch(
    missing_object_axis,
    {"object_key": "WVB:14-22", "axis_type": "WEICHENVERBINDUNG"},
) == {"object_key": "WVB:14-22"}

admin_corrected_axis = {
    "object_key": "WVB:14-22-MANUELL",
    "axis_type": "WEICHENVERBINDUNG",
}
assert _axis_identity_enrichment_patch(
    admin_corrected_axis,
    {"object_key": "WVB:14-22", "axis_type": "WEICHENVERBINDUNG"},
) == {}

wrong_type_axis = {
    "object_key": None,
    "axis_type": "GLEIS",
}
assert _axis_identity_enrichment_patch(
    wrong_type_axis,
    {"object_key": "WVB:14-22", "axis_type": "WEICHENVERBINDUNG"},
) == {}

assert _should_reassign_existing_segment_identity(
    wvb_axis,
    {
        "object_key": "WVB:19-20",
        "axis_type": "WEICHENVERBINDUNG",
    },
    ["WVB:14-22"],
)
assert not _should_reassign_existing_segment_identity(
    wvb_axis,
    {
        "object_key": "WVB:19-20",
        "axis_type": "WEICHENVERBINDUNG",
    },
    [],
)
assert not _should_reassign_existing_segment_identity(
    wvb_axis,
    {
        "object_key": "WVB:14-22",
        "axis_type": "WEICHENVERBINDUNG",
    },
    ["WVB:14-22"],
)
assert not _should_reassign_existing_segment_identity(
    admin_corrected_axis,
    {
        "object_key": "WVB:14-22",
        "axis_type": "WEICHENVERBINDUNG",
    },
    ["WVB:14-22"],
)

existing_axis = {
    "route_number": "5524",
    "station_code": "MP",
    "track_key": "28",
    "direction": None,
    "axis_type": "GLEIS",
    "bbox_min_x": 4_000_000.0,
    "bbox_min_y": 5_000_000.0,
    "bbox_max_x": 4_000_100.0,
    "bbox_max_y": 5_000_010.0,
}
weak_fields = {
    "route_number": "5524",
    "station_code": "MP",
    "track_key": None,
    "direction": None,
    "axis_type": "GLEIS",
}
assert _axis_compatible(
    existing_axis,
    weak_fields,
    (4_000_050.0, 5_000_001.0, 4_000_140.0, 5_000_011.0),
)
assert not _axis_compatible(
    existing_axis,
    {**weak_fields, "route_number": "9999"},
    (4_000_050.0, 5_000_001.0, 4_000_140.0, 5_000_011.0),
)
assert not _axis_compatible(
    existing_axis,
    weak_fields,
    (4_001_000.0, 5_001_000.0, 4_001_100.0, 5_001_010.0),
)

print("PASS survey catalog merge logic")
