from __future__ import annotations

from datetime import date

from tbserver.survey_catalog import (
    _explicit_km_gra,
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

print("PASS survey catalog merge logic")
