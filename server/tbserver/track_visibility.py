"""Read scopes for complete track datasets: never expose a partially authorized axis."""
from __future__ import annotations

from typing import Any

from .permissions import area_scope_allows, _scope_number


def track_data_scope(dataset: dict[str, Any]) -> dict[str, Any]:
    payload = dataset.get("payload") or {}
    if not isinstance(payload, dict):
        return {"invalid": True}
    explicit = payload.get("area_scope")
    scope = dict(explicit) if isinstance(explicit, dict) else {}
    values: list[float] = []
    kind = str(dataset.get("dataset_kind") or "").upper()
    if kind == "AXIS":
        points, key, factor = payload.get("samples"), "km_meter", 1.0
    elif kind == "STRECKENPUNKTE":
        points, key, factor = payload.get("points"), "km", 1000.0
    else:
        points, key, factor = None, "", 1.0
    if isinstance(points, list) and points:
        for point in points:
            number = _scope_number(point, key) if isinstance(point, dict) else None
            if number is None:
                # Unknown geometry must not become a wildcard for a restricted helper.
                return {"all": True}
            values.append(number * factor)
    elif kind == "SURVEY_SECTION":
        calibration = payload.get("calibration") or {}
        if isinstance(calibration, dict):
            values = [_scope_number(calibration, key) for key in ("km_at_start_m", "km_at_end_m")]
            if any(value is None for value in values):
                return {"all": True}
    if values:
        scope.update(all=False, km_from_m=min(values), km_to_m=max(values))
    elif "km_from_m" in payload or "km_to_m" in payload:
        scope.update({key: payload.get(key) for key in ("km_from_m", "km_to_m")})
    return scope


def track_data_visible(context: dict[str, Any], dataset: dict[str, Any]) -> bool:
    return area_scope_allows(context.get("effective_area_scope"), track_data_scope(dataset))
