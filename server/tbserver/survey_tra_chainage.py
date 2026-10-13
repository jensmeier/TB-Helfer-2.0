from __future__ import annotations

import math
from typing import Any

from .survey_geometry import SAMPLE_DISTANCE_M, densify_tra_payload

# Real project fixtures contain zero-distance TRA elements with raw type 6 whose
# station delta equals the stored element length. The same deltas recur as KM
# jumps in paired GRA data. They are source-station discontinuities, not 2-m
# geometry to walk through.
TRA_STATION_JUMP_RAW_TYPE = 6
TRA_STATION_JUMP_MAX_CHORD_M = 0.01
TRA_STATION_JUMP_LENGTH_TOLERANCE_M = 0.01
TRA_STATION_JUMP_MIN_ABS_M = 0.001


def _number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def _jump_between(start: dict[str, Any], end: dict[str, Any]) -> dict[str, float | int] | None:
    try:
        raw_type = int(start.get("raw_type") or 0)
    except (TypeError, ValueError):
        return None
    if raw_type != TRA_STATION_JUMP_RAW_TYPE:
        return None

    station_before = _number(start.get("station_m"))
    station_after = _number(end.get("station_m"))
    x1, y1 = _number(start.get("x_dbref")), _number(start.get("y_dbref"))
    x2, y2 = _number(end.get("x_dbref")), _number(end.get("y_dbref"))
    raw_length = _number(start.get("length_m"))
    if None in (station_before, station_after, x1, y1, x2, y2, raw_length):
        return None
    assert station_before is not None and station_after is not None
    assert x1 is not None and y1 is not None and x2 is not None and y2 is not None
    assert raw_length is not None

    station_jump = station_after - station_before
    chord = math.hypot(x2 - x1, y2 - y1)
    if abs(station_jump) < TRA_STATION_JUMP_MIN_ABS_M:
        return None
    if chord > TRA_STATION_JUMP_MAX_CHORD_M:
        return None
    if abs(station_jump - raw_length) > TRA_STATION_JUMP_LENGTH_TOLERANCE_M:
        return None

    return {
        "station_before_m": station_before,
        "station_after_m": station_after,
        "station_jump_m": station_jump,
        "raw_length_m": raw_length,
        "chord_m": chord,
    }


def tra_station_discontinuities(payload: dict[str, Any]) -> list[dict[str, Any]]:
    nodes = payload.get("nodes") if isinstance(payload, dict) else None
    if not isinstance(nodes, list):
        return []
    result: list[dict[str, Any]] = []
    for index, (start, end) in enumerate(zip(nodes, nodes[1:])):
        if not isinstance(start, dict) or not isinstance(end, dict):
            continue
        jump = _jump_between(start, end)
        if jump is not None:
            result.append(
                {
                    "element_index": index,
                    "raw_type": TRA_STATION_JUMP_RAW_TYPE,
                    **jump,
                }
            )
    return result


def normalize_tra_station_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """Return a derived TRA payload with continuous geometric stationing.

    Raw parser values remain untouched. At a verified zero-distance type-6 source
    station discontinuity the cumulative jump is removed from all following node
    stations. The jump element itself is forced to zero geometric length, so the
    Android-parity densifier does not create fictitious stationary 2-m samples.
    """
    if not isinstance(payload, dict):
        return {}
    raw_nodes = payload.get("nodes")
    if not isinstance(raw_nodes, list) or not raw_nodes:
        return dict(payload)
    if not all(isinstance(node, dict) for node in raw_nodes):
        return dict(payload)

    nodes = [dict(node) for node in raw_nodes]
    jumps_by_index = {
        int(item["element_index"]): item
        for item in tra_station_discontinuities(payload)
    }
    cumulative_jump = 0.0
    normalized: list[dict[str, Any]] = []
    for index, raw in enumerate(nodes):
        if index > 0 and index - 1 in jumps_by_index:
            cumulative_jump += float(jumps_by_index[index - 1]["station_jump_m"])
        station = _number(raw.get("station_m"))
        if station is None:
            return dict(payload)
        node = dict(raw)
        node["source_station_m"] = station
        node["station_m"] = station - cumulative_jump
        normalized.append(node)

    for element_index in jumps_by_index:
        normalized[element_index]["length_m"] = 0.0

    result = dict(payload)
    result["nodes"] = normalized
    result["station_discontinuities"] = list(jumps_by_index.values())
    result["station_discontinuity_total_m"] = sum(
        float(item["station_jump_m"]) for item in jumps_by_index.values()
    )
    return result


def densify_tra_chainage_payload(
    payload: dict[str, Any],
    sample_distance_m: float = SAMPLE_DISTANCE_M,
) -> list[dict[str, float]]:
    """Densify TRA on continuous geometry chainage, not discontinuous raw station."""
    return densify_tra_payload(
        normalize_tra_station_payload(payload),
        sample_distance_m=sample_distance_m,
    )
