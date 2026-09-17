from __future__ import annotations

import math
from typing import Any

STATUS_AUTOMATISCH = "AUTOMATISCH"
STATUS_MANUELL = "MANUELL"
STATUS_FEHLT = "FEHLT"
STATUS_UNPLAUSIBEL = "UNPLAUSIBEL"
STATUS_KEINE_GEOMETRIE = "KEINE_GEOMETRIE"

COLOR_GRUEN = "GRUEN"
COLOR_ORANGE = "ORANGE"
COLOR_ROT = "ROT"
COLOR_GRAU = "GRAU"

POINT_ORIGINAL = "ORIGINAL_ANCHOR"
POINT_MANUAL = "MANUAL_ANCHOR"
POINT_CONTROL = "CONTROL_ANCHOR"
POINT_JUMP = "KM_JUMP"

DIRECTION_UP = "STEIGEND"
DIRECTION_DOWN = "FALLEND"


def _number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def _run_sign(station_start_m: float | None, station_end_m: float | None) -> int | None:
    start, end = _number(station_start_m), _number(station_end_m)
    if start is None or end is None or abs(end - start) < 0.001:
        return None
    return 1 if end > start else -1


def _valid_anchor(point: dict[str, Any], allowed_types: set[str]) -> bool:
    return (
        str(point.get("point_type") or "").upper() in allowed_types
        and _number(point.get("station_m")) is not None
        and _number(point.get("km_meter")) is not None
    )


def _jumps(points: list[dict[str, Any]]) -> list[dict[str, float]]:
    result: list[dict[str, float]] = []
    for point in points:
        if str(point.get("point_type") or "").upper() != POINT_JUMP:
            continue
        station = _number(point.get("station_m"))
        before = _number(point.get("km_meter"))
        after = _number(point.get("km_after_jump_meter"))
        if station is None or before is None or after is None:
            continue
        result.append({"station_m": station, "km_meter": before, "km_after_jump_meter": after})
    return sorted(result, key=lambda item: item["station_m"])


def _jump_delta_in_increasing_station(jump: dict[str, float], run_sign: int) -> float:
    # km_meter/km_after_jump_meter are stored in section travel direction Start -> Ende.
    return (jump["km_after_jump_meter"] - jump["km_meter"]) * run_sign


def _pair_calibration(
    first: dict[str, Any],
    second: dict[str, Any],
    jumps: list[dict[str, float]],
    run_sign: int | None,
) -> tuple[dict[str, Any] | None, str | None]:
    s1, s2 = _number(first.get("station_m")), _number(second.get("station_m"))
    k1, k2 = _number(first.get("km_meter")), _number(second.get("km_meter"))
    if None in (s1, s2, k1, k2):
        return None, "KM-Anker enthalten keine endlichen Werte."
    assert s1 is not None and s2 is not None and k1 is not None and k2 is not None
    if s1 > s2:
        s1, s2, k1, k2 = s2, s1, k2, k1
    station_delta = s2 - s1
    if station_delta < 20.0:
        return None, "KM-Anker liegen weniger als 20 m auseinander."

    jump_delta = 0.0
    jumps_between = [jump for jump in jumps if s1 < jump["station_m"] <= s2]
    if jumps_between:
        if run_sign is None:
            return None, "KM-Spruenge benoetigen bekannten Abschnittsanfang und -ende."
        jump_delta = sum(_jump_delta_in_increasing_station(jump, run_sign) for jump in jumps_between)

    continuous_km_delta = (k2 - k1) - jump_delta
    factor = continuous_km_delta / station_delta
    if abs(continuous_km_delta) < 1.0:
        return None, "KM-Differenz zwischen den Ankern ist zu klein."
    if abs(factor) < 0.5 or abs(factor) > 1.5:
        return None, "KM-Differenz und TRA-Station sind nicht plausibel zueinander."

    return {
        "anchor_station_m": s1,
        "anchor_km_meter": k1,
        "factor_km_per_station_m": factor,
        "station_span_m": station_delta,
        "continuous_km_span_m": continuous_km_delta,
        "jump_delta_m": jump_delta,
        "anchor_count": 2,
    }, None


def _widest_pair(points: list[dict[str, Any]]) -> tuple[dict[str, Any], dict[str, Any]] | None:
    if len(points) < 2:
        return None
    ordered = sorted(points, key=lambda item: float(item["station_m"]))
    return ordered[0], ordered[-1]


def evaluate_calibration(
    points: list[dict[str, Any]],
    *,
    station_start_m: float | None,
    station_end_m: float | None,
    km_direction: str | None,
    has_geometry: bool = True,
) -> dict[str, Any]:
    if not has_geometry:
        return {
            "status": STATUS_KEINE_GEOMETRIE,
            "color": COLOR_GRAU,
            "mode": None,
            "hint": "Keine TRA-Geometrie verknuepft.",
        }

    run_sign = _run_sign(station_start_m, station_end_m)
    jumps = _jumps(points)
    original = [point for point in points if _valid_anchor(point, {POINT_ORIGINAL})]
    manual = [point for point in points if _valid_anchor(point, {POINT_MANUAL})]
    original_problem: str | None = None
    manual_problem: str | None = None

    pair = _widest_pair(original)
    if pair is not None:
        calibration, problem = _pair_calibration(pair[0], pair[1], jumps, run_sign)
        if calibration is not None:
            return {
                "status": STATUS_AUTOMATISCH,
                "color": COLOR_GRUEN,
                "mode": "ORIGINAL_2PUNKT",
                "hint": None,
                "run_station_sign": run_sign,
                **calibration,
            }
        original_problem = problem

    pair = _widest_pair(manual)
    if pair is not None:
        calibration, problem = _pair_calibration(pair[0], pair[1], jumps, run_sign)
        if calibration is not None:
            return {
                "status": STATUS_MANUELL,
                "color": COLOR_ORANGE,
                "mode": "MANUELL_2PUNKT",
                "hint": None,
                "run_station_sign": run_sign,
                **calibration,
            }
        manual_problem = problem

    direction = str(km_direction or "").upper() or None
    direction_anchor = manual[0] if manual else (original[0] if original else None)
    if direction_anchor is not None and direction in {DIRECTION_UP, DIRECTION_DOWN}:
        if run_sign is None:
            manual_problem = "Ein KM-Anker plus Richtung benoetigt Abschnittsanfang und -ende."
        else:
            station_sign = run_sign if direction == DIRECTION_UP else -run_sign
            return {
                "status": STATUS_MANUELL,
                "color": COLOR_ORANGE,
                "mode": "MANUELL_ANKER_RICHTUNG",
                "hint": None,
                "anchor_station_m": _number(direction_anchor.get("station_m")),
                "anchor_km_meter": _number(direction_anchor.get("km_meter")),
                "factor_km_per_station_m": float(station_sign),
                "station_span_m": None,
                "continuous_km_span_m": None,
                "jump_delta_m": None,
                "anchor_count": 1,
                "run_station_sign": run_sign,
            }

    if original_problem or manual_problem:
        return {
            "status": STATUS_UNPLAUSIBEL,
            "color": COLOR_ROT,
            "mode": None,
            "hint": manual_problem or original_problem,
            "run_station_sign": run_sign,
        }

    return {
        "status": STATUS_FEHLT,
        "color": COLOR_ROT,
        "mode": None,
        "hint": "Geometrie vorhanden, aber noch keine belastbare KM-Kalibrierung.",
        "run_station_sign": run_sign,
    }


def km_at_station(
    calibration: dict[str, Any],
    points: list[dict[str, Any]],
    station_m: float,
) -> float | None:
    anchor_station = _number(calibration.get("anchor_station_m"))
    anchor_km = _number(calibration.get("anchor_km_meter"))
    factor = _number(calibration.get("factor_km_per_station_m"))
    station = _number(station_m)
    if None in (anchor_station, anchor_km, factor, station):
        return None
    assert anchor_station is not None and anchor_km is not None and factor is not None and station is not None

    km = anchor_km + factor * (station - anchor_station)
    jumps = _jumps(points)
    if jumps:
        run_sign_raw = calibration.get("run_station_sign")
        run_sign = int(run_sign_raw) if run_sign_raw in (-1, 1) else None
        if run_sign is None:
            return None
        for jump in jumps:
            delta = _jump_delta_in_increasing_station(jump, run_sign)
            if anchor_station < jump["station_m"] <= station:
                km += delta
            elif station < jump["station_m"] <= anchor_station:
                km -= delta
    return km
