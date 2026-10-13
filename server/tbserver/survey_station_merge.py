from __future__ import annotations

import math
import statistics
from typing import Any

from .survey_tra_chainage import densify_tra_chainage_payload, tra_station_discontinuities

MERGE_VERSION = "survey-station-merge-v2"
OVERLAP_MAX_DISTANCE_M = 1.0
OVERLAP_MIN_MATCHES = 5
OVERLAP_MIN_SPAN_M = 50.0
OVERLAP_MAX_STATION_RESIDUAL_M = 0.5
ENDPOINT_MAX_GAP_M = 5.0


def _number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def _axis_value(source: dict[str, Any], key: str) -> str | None:
    payload = source.get("parse_payload") or {}
    value = payload.get(key) if isinstance(payload, dict) else None
    if value in (None, ""):
        return None
    return str(value).strip() or None


def geometry_axis_compatible(left: dict[str, Any], right: dict[str, Any]) -> tuple[bool, dict[str, Any]]:
    left_route = _axis_value(left, "route_from_name") or (str(left.get("original_route_number") or "").strip() or None)
    right_route = _axis_value(right, "route_from_name") or (str(right.get("original_route_number") or "").strip() or None)
    left_track, right_track = _axis_value(left, "track_from_name"), _axis_value(right, "track_from_name")
    left_direction, right_direction = _axis_value(left, "direction_from_name"), _axis_value(right, "direction_from_name")
    reasons: list[str] = []
    if left_route is None or right_route is None:
        reasons.append("ROUTE_UNCONFIRMED")
    elif left_route != right_route:
        reasons.append("ROUTE_MISMATCH")
    track_ok = left_track is not None and right_track is not None and left_track == right_track
    direction_ok = left_direction is not None and right_direction is not None and left_direction == right_direction
    if left_track is not None and right_track is not None and left_track != right_track:
        reasons.append("TRACK_MISMATCH")
    if left_direction is not None and right_direction is not None and left_direction != right_direction:
        reasons.append("DIRECTION_MISMATCH")
    if not track_ok and not direction_ok:
        reasons.append("AXIS_IDENTITY_UNCONFIRMED")
    return not reasons, {
        "left_route": left_route,
        "right_route": right_route,
        "left_track": left_track,
        "right_track": right_track,
        "left_direction": left_direction,
        "right_direction": right_direction,
        "track_confirmed": track_ok,
        "direction_confirmed": direction_ok,
        "mismatch_reasons": reasons,
    }


def _samples(source: dict[str, Any]) -> list[dict[str, float]]:
    dense = densify_tra_chainage_payload(source.get("parse_payload") or {})
    return list(reversed(dense)) if source.get("reverse_geometry") else dense


def _project_xy(x: float, y: float, samples: list[dict[str, float]]) -> dict[str, float] | None:
    best = None
    for first, second in zip(samples, samples[1:]):
        ax, ay = first["x_dbref"], first["y_dbref"]
        bx, by = second["x_dbref"], second["y_dbref"]
        vx, vy = bx - ax, by - ay
        length2 = vx * vx + vy * vy
        if length2 <= 1e-9:
            continue
        t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / length2))
        px, py = ax + t * vx, ay + t * vy
        distance = math.hypot(x - px, y - py)
        if best is None or distance < best["distance_m"]:
            best = {
                "distance_m": distance,
                "station_m": first["station_m"] + (second["station_m"] - first["station_m"]) * t,
            }
    return best


def _mapped_station(mapping: dict[str, Any], station_m: float) -> float:
    return float(mapping["station_sign"]) * station_m + float(mapping["station_offset_m"])


def _fit_overlap(
    previous: dict[str, Any],
    previous_map: dict[str, Any],
    current: dict[str, Any],
) -> dict[str, Any] | None:
    compatible, axis = geometry_axis_compatible(previous, current)
    if not compatible:
        return None
    previous_samples, current_samples = _samples(previous), _samples(current)
    if len(previous_samples) < 2 or len(current_samples) < 2:
        return None

    matches: list[tuple[float, float, float]] = []
    # 2-m source samples; every fifth point gives a 10-m fitting grid.
    for sample in current_samples[::5]:
        projection = _project_xy(sample["x_dbref"], sample["y_dbref"], previous_samples)
        if projection is None or projection["distance_m"] > OVERLAP_MAX_DISTANCE_M:
            continue
        previous_merged = _mapped_station(previous_map, projection["station_m"])
        matches.append((sample["station_m"], previous_merged, projection["distance_m"]))
    if len(matches) < OVERLAP_MIN_MATCHES:
        return None

    span = max(row[0] for row in matches) - min(row[0] for row in matches)
    if abs(span) < OVERLAP_MIN_SPAN_M:
        return None

    best = None
    for station_sign in (1.0, -1.0):
        offsets = [mapped - station_sign * local for local, mapped, _ in matches]
        offset = float(statistics.median(offsets))
        residuals = [mapped - (station_sign * local + offset) for local, mapped, _ in matches]
        max_residual = max(abs(value) for value in residuals)
        median_residual = float(statistics.median(abs(value) for value in residuals))
        candidate = (max_residual, median_residual, station_sign, offset)
        if best is None or candidate < best:
            best = candidate
    assert best is not None
    max_residual, median_residual, station_sign, offset = best
    if max_residual > OVERLAP_MAX_STATION_RESIDUAL_M:
        return None

    return {
        "status": "OVERLAP_CONFIRMED",
        "station_sign": station_sign,
        "station_offset_m": offset,
        "axis": axis,
        "overlap_match_count": len(matches),
        "overlap_span_m": abs(span),
        "median_distance_m": float(statistics.median(row[2] for row in matches)),
        "max_distance_m": max(row[2] for row in matches),
        "median_station_residual_m": median_residual,
        "max_station_residual_m": max_residual,
        "reliable_for_auto_anchor": bool(previous_map.get("reliable_for_auto_anchor")),
    }


def _fit_endpoint(
    previous: dict[str, Any],
    previous_map: dict[str, Any],
    current: dict[str, Any],
) -> dict[str, Any] | None:
    compatible, axis = geometry_axis_compatible(previous, current)
    if not compatible:
        return None
    previous_samples, current_samples = _samples(previous), _samples(current)
    if len(previous_samples) < 2 or len(current_samples) < 2:
        return None

    prev_end, current_start = previous_samples[-1], current_samples[0]
    gap = math.hypot(
        prev_end["x_dbref"] - current_start["x_dbref"],
        prev_end["y_dbref"] - current_start["y_dbref"],
    )
    if gap > ENDPOINT_MAX_GAP_M:
        return None

    prev_start_merged = _mapped_station(previous_map, previous_samples[0]["station_m"])
    prev_end_merged = _mapped_station(previous_map, prev_end["station_m"])
    travel_sign = 1.0 if prev_end_merged >= prev_start_merged else -1.0
    current_local_delta = current_samples[-1]["station_m"] - current_start["station_m"]
    if abs(current_local_delta) < 0.001:
        return None
    station_sign = travel_sign * (1.0 if current_local_delta > 0.0 else -1.0)
    target_start = prev_end_merged + travel_sign * gap
    offset = target_start - station_sign * current_start["station_m"]
    return {
        "status": "ENDPOINT_CONFIRMED",
        "station_sign": station_sign,
        "station_offset_m": offset,
        "axis": axis,
        "endpoint_gap_m": gap,
        "reliable_for_auto_anchor": False,
    }


def _decorate_extent(source: dict[str, Any], mapping: dict[str, Any]) -> None:
    samples = _samples(source)
    if not samples or mapping.get("status") == "UNMAPPED":
        return
    jumps = tra_station_discontinuities(source.get("parse_payload") or {})
    mapping["local_travel_start_m"] = samples[0]["station_m"]
    mapping["local_travel_end_m"] = samples[-1]["station_m"]
    mapping["merged_travel_start_m"] = _mapped_station(mapping, samples[0]["station_m"])
    mapping["merged_travel_end_m"] = _mapped_station(mapping, samples[-1]["station_m"])
    mapping["source_station_discontinuity_count"] = len(jumps)
    mapping["source_station_discontinuity_total_m"] = sum(float(item["station_jump_m"]) for item in jumps)
    mapping["source_station_discontinuities"] = jumps


def _overlap_sort_key(mapping: dict[str, Any]) -> tuple[float, float, float, int]:
    return (
        0.0 if mapping.get("reliable_for_auto_anchor") else 1.0,
        float(mapping.get("max_station_residual_m") or 0.0),
        -float(mapping.get("overlap_span_m") or 0.0),
        -int(mapping.get("overlap_match_count") or 0),
    )


def build_geometry_station_maps(geometry_sources: list[dict[str, Any]]) -> dict[str, Any]:
    ordered = sorted(
        geometry_sources,
        key=lambda item: (item.get("order", 0), str(item.get("original_id"))),
    )
    usable = [
        source
        for source in ordered
        if source.get("parse_status") == "PARSED" and source.get("detected_format") == "TRA_ER0_BINARY"
    ]
    if not usable:
        return {
            "merge_version": MERGE_VERSION,
            "mapped_count": 0,
            "reliable_mapped_count": 0,
            "source_count": len(ordered),
            "maps": {},
        }

    primary = usable[0]
    primary_id = str(primary["original_id"])
    maps: dict[str, dict[str, Any]] = {
        primary_id: {
            "status": "PRIMARY",
            "station_sign": 1.0,
            "station_offset_m": 0.0,
            "source_original_id": primary_id,
            "previous_original_id": None,
            "reliable_for_auto_anchor": True,
        }
    }
    _decorate_extent(primary, maps[primary_id])
    mapped_chain: list[tuple[dict[str, Any], dict[str, Any]]] = [(primary, maps[primary_id])]

    for current in usable[1:]:
        overlap_candidates: list[dict[str, Any]] = []
        for previous, previous_map in reversed(mapped_chain):
            candidate = _fit_overlap(previous, previous_map, current)
            if candidate is None:
                continue
            candidate["previous_original_id"] = str(previous["original_id"])
            overlap_candidates.append(candidate)

        mapping = min(overlap_candidates, key=_overlap_sort_key) if overlap_candidates else None
        if mapping is None:
            previous, previous_map = mapped_chain[-1]
            mapping = _fit_endpoint(previous, previous_map, current)
            if mapping is not None:
                mapping["previous_original_id"] = str(previous["original_id"])

        current_id = str(current["original_id"])
        if mapping is None:
            maps[current_id] = {
                "status": "UNMAPPED",
                "source_original_id": current_id,
                "previous_original_id": str(mapped_chain[-1][0]["original_id"]),
                "reliable_for_auto_anchor": False,
            }
            continue

        mapping["source_original_id"] = current_id
        _decorate_extent(current, mapping)
        maps[current_id] = mapping
        mapped_chain.append((current, mapping))

    merged_values = [
        float(value[key])
        for value in maps.values()
        if value.get("status") != "UNMAPPED"
        for key in ("merged_travel_start_m", "merged_travel_end_m")
        if value.get(key) is not None
    ]
    return {
        "merge_version": MERGE_VERSION,
        "station_basis": "CONTINUOUS_GEOMETRY_CHAINAGE",
        "source_count": len(ordered),
        "mapped_count": sum(1 for value in maps.values() if value["status"] != "UNMAPPED"),
        "reliable_mapped_count": sum(1 for value in maps.values() if value.get("reliable_for_auto_anchor")),
        "merged_station_min_m": min(merged_values) if merged_values else None,
        "merged_station_max_m": max(merged_values) if merged_values else None,
        "maps": maps,
    }


def merged_station_for(
    station_map: dict[str, Any],
    original_id: Any,
    local_station_m: float,
) -> tuple[float, dict[str, Any]] | None:
    mapping = (station_map.get("maps") or {}).get(str(original_id))
    if not isinstance(mapping, dict) or mapping.get("status") == "UNMAPPED":
        return None
    return _mapped_station(mapping, float(local_station_m)), mapping
