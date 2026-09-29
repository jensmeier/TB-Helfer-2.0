from __future__ import annotations

import math
from collections import Counter
from typing import Any, Iterable

from .survey_geometry import densify_tra_payload


_MAX_LINE_POINTS = 900
_MAX_POINT_MARKERS = 1200
_MAX_PROFILE_POINTS = 700


def _finite(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) and abs(number) < 1e30 else None


def _sample(values: list[Any], limit: int) -> list[Any]:
    if len(values) <= limit:
        return values
    if limit <= 2:
        return [values[0], values[-1]]
    last = len(values) - 1
    indexes = {round(index * last / (limit - 1)) for index in range(limit)}
    return [values[index] for index in sorted(indexes)]


def _bounds(xy: Iterable[tuple[float, float]]) -> dict[str, float] | None:
    points = list(xy)
    if not points:
        return None
    xs = [item[0] for item in points]
    ys = [item[1] for item in points]
    return {
        "min_x": min(xs),
        "max_x": max(xs),
        "min_y": min(ys),
        "max_y": max(ys),
    }


def build_survey_preview(parsed: dict[str, Any]) -> dict[str, Any]:
    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    family = str(parsed.get("parser_family") or "").upper()
    all_points = payload.get("points") if isinstance(payload.get("points"), list) else []

    line: list[dict[str, Any]] = []
    if family == "TRA":
        try:
            dense = densify_tra_payload(payload, sample_distance_m=10.0)
        except (TypeError, ValueError):
            dense = []
        line = [
            {
                "x": float(item["x_dbref"]),
                "y": float(item["y_dbref"]),
                "station_m": _finite(item.get("station_m")),
            }
            for item in _sample(dense, _MAX_LINE_POINTS)
            if _finite(item.get("x_dbref")) is not None
            and _finite(item.get("y_dbref")) is not None
        ]

    marker_source = []
    type_counts: Counter[str] = Counter()
    for raw in all_points:
        if not isinstance(raw, dict):
            continue
        point_type = str(raw.get("type") or raw.get("point_code") or "PUNKT").strip() or "PUNKT"
        type_counts[point_type] += 1
        x = _finite(raw.get("x_dbref"))
        y = _finite(raw.get("y_dbref"))
        if x is None or y is None:
            continue
        marker_source.append(
            {
                "x": x,
                "y": y,
                "label": str(raw.get("point_name") or raw.get("class") or "").strip(),
                "type": point_type,
                "km_meter": _finite(raw.get("km_meter")),
                "route": str(raw.get("route") or "").strip() or None,
                "track": str(raw.get("track") or "").strip() or None,
            }
        )
    markers = _sample(marker_source, _MAX_POINT_MARKERS)

    profile_source: list[dict[str, float]] = []
    for raw in all_points:
        if not isinstance(raw, dict):
            continue
        km = _finite(raw.get("km_meter"))
        height = _finite(raw.get("height_m"))
        if km is not None and height is not None:
            profile_source.append({"km_meter": km, "height_m": height})
    profile_source.sort(key=lambda item: item["km_meter"])
    profile = _sample(profile_source, _MAX_PROFILE_POINTS)

    km_values = [
        value
        for raw in all_points
        if isinstance(raw, dict)
        and (value := _finite(raw.get("km_meter"))) is not None
    ]
    station_values = [
        value
        for value in (
            _finite(payload.get("station_from_m")),
            _finite(payload.get("station_to_m")),
        )
        if value is not None
    ]
    if not station_values:
        station_values = [
            value
            for raw in payload.get("nodes") or []
            if isinstance(raw, dict)
            and (value := _finite(raw.get("station_m"))) is not None
        ]

    xy_pairs = [(item["x"], item["y"]) for item in line]
    xy_pairs.extend((item["x"], item["y"]) for item in markers)
    bounds = _bounds(xy_pairs)

    return {
        "line": line,
        "points": markers,
        "profile": profile,
        "bounds": bounds,
        "point_count": len(all_points),
        "shown_point_count": len(markers),
        "line_point_count": len(line),
        "km_from_m": min(km_values) if km_values else None,
        "km_to_m": max(km_values) if km_values else None,
        "station_from_m": min(station_values) if station_values else None,
        "station_to_m": max(station_values) if station_values else None,
        "point_types": [
            {"type": point_type, "count": count}
            for point_type, count in type_counts.most_common(24)
        ],
    }
