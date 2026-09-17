from __future__ import annotations

import math
import statistics
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .permissions import require_permission, worksite_context
from .portal import require_csrf
from .survey_projection import PROJECTION_VERSION, _load_existing_points, _load_sources
from .survey_sections import _build_payload, _insert_points, _load_dataset, _section_response

router = APIRouter(prefix="/api/v1", tags=["survey-gra-jumps"])
GRA_JUMP_VERSION = "survey-gra-jump-v2"
GRA_JUMP_PARAMETER_MARKER = 1000.0
GRA_JUMP_MIN_ABS_M = 0.5
GRA_JUMP_MAX_ABS_M = 500.0
GRA_JUMP_HEIGHT_TOLERANCE_M = 0.005
GRA_JUMP_TWO_SIDE_AGREEMENT_M = 2.0
GRA_JUMP_ONE_SIDE_MAX_EXTRAPOLATION_M = 250.0
MIN_LOCAL_ANCHOR_SPAN_M = 20.0


class GraJumpRebuildRequest(BaseModel):
    base_version: int = Field(ge=1)
    # Conservative default: one-sided extrapolation is diagnostic/explicit only.
    allow_one_sided: bool = False


def _number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def gra_jump_candidates(parse_payload: dict[str, Any]) -> list[dict[str, Any]]:
    """Find the recurring real-project GRA 1000->0/equal-height jump marker."""
    points = parse_payload.get("points") if isinstance(parse_payload, dict) else None
    if not isinstance(points, list):
        return []
    result: list[dict[str, Any]] = []
    for index, (left, right) in enumerate(zip(points, points[1:])):
        if not isinstance(left, dict) or not isinstance(right, dict):
            continue
        before, after = _number(left.get("km_meter")), _number(right.get("km_meter"))
        h1, h2 = _number(left.get("height_m")), _number(right.get("height_m"))
        p1, p2 = _number(left.get("parameter_m")), _number(right.get("parameter_m"))
        if None in (before, after, h1, h2, p1, p2):
            continue
        assert before is not None and after is not None and h1 is not None and h2 is not None
        assert p1 is not None and p2 is not None
        delta = after - before
        if not GRA_JUMP_MIN_ABS_M <= abs(delta) <= GRA_JUMP_MAX_ABS_M:
            continue
        if abs(p1 - GRA_JUMP_PARAMETER_MARKER) > 0.001 or abs(p2) > 0.001:
            continue
        if abs(h1 - h2) > GRA_JUMP_HEIGHT_TOLERANCE_M:
            continue
        result.append({
            "point_index_before": index,
            "point_index_after": index + 1,
            "km_before_meter": before,
            "km_after_meter": after,
            "jump_delta_m": delta,
            "height_m": (h1 + h2) * 0.5,
            "marker": "GRA_PARAMETER_1000_TO_0_EQUAL_HEIGHT",
        })
    return result


def _axis_value(source: dict[str, Any], key: str) -> str | None:
    payload = source.get("parse_payload") or {}
    value = payload.get(key) if isinstance(payload, dict) else None
    return None if value in (None, "") else (str(value).strip() or None)


def _axis_compatible(dataset: dict[str, Any], primary: dict[str, Any], gra: dict[str, Any]) -> tuple[bool, dict[str, Any]]:
    route = str(dataset.get("route_number") or _axis_value(primary, "route_from_name") or "").strip() or None
    gra_route = _axis_value(gra, "route_from_name") or (str(gra.get("original_route_number") or "").strip() or None)
    track = str(dataset.get("track_key") or _axis_value(primary, "track_from_name") or "").strip() or None
    gra_track = _axis_value(gra, "track_from_name")
    direction, gra_direction = _axis_value(primary, "direction_from_name"), _axis_value(gra, "direction_from_name")
    reasons: list[str] = []
    if route is None or gra_route is None:
        reasons.append("ROUTE_UNCONFIRMED")
    elif route != gra_route:
        reasons.append("ROUTE_MISMATCH")
    track_ok = track is not None and gra_track is not None and track == gra_track
    direction_ok = direction is not None and gra_direction is not None and direction == gra_direction
    if track is not None and gra_track is not None and track != gra_track:
        reasons.append("TRACK_MISMATCH")
    if direction is not None and gra_direction is not None and direction != gra_direction:
        reasons.append("DIRECTION_MISMATCH")
    if not track_ok and not direction_ok:
        reasons.append("AXIS_IDENTITY_UNCONFIRMED")
    return not reasons, {
        "expected_route": route, "gra_route": gra_route,
        "primary_track": track, "gra_track": gra_track,
        "primary_direction": direction, "gra_direction": gra_direction,
        "track_confirmed": track_ok, "direction_confirmed": direction_ok,
        "mismatch_reasons": reasons,
    }


def _anchor_rows(points: list[dict[str, Any]]) -> list[tuple[float, float]]:
    rows: list[tuple[float, float]] = []
    for point in points:
        if str(point.get("point_type") or "").upper() != "ORIGINAL_ANCHOR":
            continue
        station, km = _number(point.get("station_m")), _number(point.get("km_meter"))
        if station is not None and km is not None:
            rows.append((station, km))
    return sorted(rows)


def _baseline_factor(anchors: list[tuple[float, float]]) -> float | None:
    factors = []
    for (s1, k1), (s2, k2) in zip(anchors, anchors[1:]):
        ds = s2 - s1
        if abs(ds) < MIN_LOCAL_ANCHOR_SPAN_M:
            continue
        factor = (k2 - k1) / ds
        if 0.5 <= abs(factor) <= 1.5:
            factors.append(factor)
    return float(statistics.median(factors)) if len(factors) >= 2 else None


def _pair_before(anchors: list[tuple[float, float]], threshold: float, factor: float):
    eligible = [row for row in anchors if row[1] <= threshold] if factor > 0 else [row for row in anchors if row[1] >= threshold]
    if len(eligible) < 2:
        return None
    right = eligible[-1]
    return next(((left, right) for left in reversed(eligible[:-1]) if abs(right[0] - left[0]) >= MIN_LOCAL_ANCHOR_SPAN_M), None)


def _pair_after(anchors: list[tuple[float, float]], threshold: float, factor: float):
    eligible = [row for row in anchors if row[1] >= threshold] if factor > 0 else [row for row in anchors if row[1] <= threshold]
    if len(eligible) < 2:
        return None
    left = eligible[0]
    return next(((left, right) for right in eligible[1:] if abs(right[0] - left[0]) >= MIN_LOCAL_ANCHOR_SPAN_M), None)


def _station_from_pair(pair, km: float) -> float | None:
    if pair is None:
        return None
    (s1, k1), (s2, k2) = pair
    return None if abs(k2 - k1) < 0.001 else s1 + (km - k1) * (s2 - s1) / (k2 - k1)


def estimate_jump_station(
    anchors: list[tuple[float, float]], *, km_before_meter: float, km_after_meter: float,
    station_min_m: float, station_max_m: float, allow_one_sided: bool,
) -> dict[str, Any] | None:
    """Place a GRA jump only when projected anchors geometrically support it."""
    factor = _baseline_factor(anchors) if len(anchors) >= 3 else None
    if factor is None:
        return None
    low_km, high_km = sorted((km_before_meter, km_after_meter))
    before_pair = _pair_before(anchors, low_km if factor > 0 else high_km, factor)
    after_pair = _pair_after(anchors, high_km if factor > 0 else low_km, factor)
    before_station = _station_from_pair(before_pair, km_before_meter)
    after_station = _station_from_pair(after_pair, km_after_meter)
    available = [v for v in (before_station, after_station) if v is not None]
    if not available:
        return None
    if len(available) == 2:
        agreement = abs(available[0] - available[1])
        if agreement > GRA_JUMP_TWO_SIDE_AGREEMENT_M:
            return None
        station, method, extrapolation = sum(available) * 0.5, "BOTH_SIDES", 0.0
    else:
        if not allow_one_sided:
            return None
        station = available[0]
        extrapolation = min(abs(station - row[0]) for row in anchors)
        if extrapolation > GRA_JUMP_ONE_SIDE_MAX_EXTRAPOLATION_M:
            return None
        agreement = None
        method = "ONE_SIDE_BEFORE" if before_station is not None else "ONE_SIDE_AFTER"
    lo, hi = sorted((station_min_m, station_max_m))
    if not lo - 1.0 <= station <= hi + 1.0:
        return None
    return {
        "station_m": station, "placement_method": method, "baseline_factor": factor,
        "station_from_before_m": before_station, "station_from_after_m": after_station,
        "two_side_agreement_m": agreement, "one_side_extrapolation_m": extrapolation,
    }


def _manual_equivalent(points: list[dict[str, Any]], station: float, before: float, after: float) -> bool:
    for point in points:
        if str(point.get("point_type") or "").upper() != "KM_JUMP":
            continue
        meta = point.get("payload")
        if isinstance(meta, dict) and str(meta.get("generated_by") or "").startswith("survey-gra-jump-v"):
            continue
        ps, pb, pa = _number(point.get("station_m")), _number(point.get("km_meter")), _number(point.get("km_after_jump_meter"))
        if None not in (ps, pb, pa) and abs(ps - station) <= 5.0 and abs(pb - before) <= 0.01 and abs(pa - after) <= 0.01:
            return True
    return False


def _current_station_context(dataset: dict[str, Any], primary_id: Any) -> tuple[dict[str, Any], float, float, int]:
    current = dataset.get("payload") or {}
    projection = current.get("projection_summary") if isinstance(current, dict) else None
    if not isinstance(projection, dict):
        raise HTTPException(409, "GRA_JUMP_REQUIRES_CURRENT_PROJECTION")
    if projection.get("projection_version") != PROJECTION_VERSION:
        raise HTTPException(409, "GRA_JUMP_REQUIRES_CURRENT_PROJECTION")
    if projection.get("station_basis") != "CONTINUOUS_GEOMETRY_CHAINAGE":
        raise HTTPException(409, "GRA_JUMP_REQUIRES_CONTINUOUS_CHAINAGE")
    station_merge = projection.get("station_merge")
    if not isinstance(station_merge, dict):
        raise HTTPException(409, "GRA_JUMP_REQUIRES_CURRENT_PROJECTION")
    station_min = _number(station_merge.get("merged_station_min_m"))
    station_max = _number(station_merge.get("merged_station_max_m"))
    maps = station_merge.get("maps")
    primary_map = maps.get(str(primary_id)) if isinstance(maps, dict) else None
    if station_min is None or station_max is None or not isinstance(primary_map, dict):
        raise HTTPException(409, "GRA_JUMP_REQUIRES_CURRENT_PROJECTION")
    travel_start = _number(primary_map.get("merged_travel_start_m"))
    travel_end = _number(primary_map.get("merged_travel_end_m"))
    run_sign = -1 if travel_start is not None and travel_end is not None and travel_end < travel_start else 1
    return projection, station_min, station_max, run_sign


def build_gra_jump_points(
    *, dataset: dict[str, Any], sources: list[dict[str, Any]],
    existing_points: list[dict[str, Any]], allow_one_sided: bool,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    geometry = [s for s in sources if s.get("role") == "GEOMETRY" and s.get("detected_format") == "TRA_ER0_BINARY"]
    if not geometry:
        raise HTTPException(409, "SURVEY_SECTION_GEOMETRY_NOT_PROJECTABLE")
    primary = geometry[0]
    if bool(primary.get("reverse_geometry")):
        raise HTTPException(409, "GRA_JUMP_REVERSED_PRIMARY_GEOMETRY_NOT_SUPPORTED")

    projection, station_min, station_max, run_sign = _current_station_context(dataset, primary["original_id"])
    anchors = _anchor_rows(existing_points)

    generated: list[dict[str, Any]] = []
    source_summaries = []
    for source in sources:
        if source.get("role") != "KM" or source.get("detected_format") != "GRA_BINARY" or source.get("parse_status") != "PARSED":
            continue
        compatible, axis = _axis_compatible(dataset, primary, source)
        candidates = gra_jump_candidates(source.get("parse_payload") or {})
        summary = {
            "source_original_id": str(source["original_id"]), "original_name": source.get("original_name"),
            "axis": axis, "candidate_count": len(candidates), "generated_jump_count": 0,
            "unplaced_candidate_count": 0, "manual_equivalent_count": 0,
        }
        if not compatible:
            summary["unplaced_candidate_count"] = len(candidates)
            source_summaries.append(summary)
            continue
        for candidate in candidates:
            placement = estimate_jump_station(
                anchors, km_before_meter=float(candidate["km_before_meter"]), km_after_meter=float(candidate["km_after_meter"]),
                station_min_m=station_min, station_max_m=station_max, allow_one_sided=allow_one_sided,
            )
            if placement is None:
                summary["unplaced_candidate_count"] += 1
                continue
            before, after = float(candidate["km_before_meter"]), float(candidate["km_after_meter"])
            if run_sign < 0:
                before, after = after, before
            station = float(placement["station_m"])
            if _manual_equivalent(existing_points, station, before, after):
                summary["manual_equivalent_count"] += 1
                continue
            point_id = uuid5(NAMESPACE_URL, ":".join([
                GRA_JUMP_VERSION, str(dataset["id"]), str(source["original_id"]),
                f"{candidate['km_before_meter']:.6f}", f"{candidate['km_after_meter']:.6f}",
            ]))
            generated.append({
                "id": point_id, "point_type": "KM_JUMP", "station_m": station,
                "km_meter": before, "km_after_jump_meter": after, "source_original_id": source["original_id"],
                "payload": {
                    "generated_by": GRA_JUMP_VERSION, "gra_marker": candidate["marker"],
                    "gra_point_index_before": candidate["point_index_before"], "gra_point_index_after": candidate["point_index_after"],
                    "gra_jump_delta_m": candidate["jump_delta_m"], "placement_method": placement["placement_method"],
                    "baseline_factor": placement["baseline_factor"], "station_from_before_m": placement["station_from_before_m"],
                    "station_from_after_m": placement["station_from_after_m"], "two_side_agreement_m": placement["two_side_agreement_m"],
                    "one_side_extrapolation_m": placement["one_side_extrapolation_m"],
                    "station_basis": "CONTINUOUS_GEOMETRY_CHAINAGE",
                    "projection_version": projection.get("projection_version"),
                    "station_merge_version": (projection.get("station_merge") or {}).get("merge_version"),
                    "primary_geometry_original_id": str(primary["original_id"]), "axis": axis,
                },
            })
            summary["generated_jump_count"] += 1
        source_summaries.append(summary)
    generated.sort(key=lambda point: (point["station_m"], str(point["id"])))
    return generated, {
        "jump_version": GRA_JUMP_VERSION,
        "station_basis": "CONTINUOUS_GEOMETRY_CHAINAGE",
        "projection_version": projection.get("projection_version"),
        "station_merge_version": (projection.get("station_merge") or {}).get("merge_version"),
        "primary_geometry_original_id": str(primary["original_id"]),
        "original_anchor_count": len(anchors), "generated_jump_count": len(generated),
        "allow_one_sided": allow_one_sided, "sources": source_summaries,
    }


@router.post("/survey-sections/{dataset_id}/rebuild-gra-jumps")
def rebuild_gra_jumps(
    dataset_id: UUID, payload: GraJumpRebuildRequest,
    user: dict[str, Any] = Depends(require_csrf), mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="SURVEY_SECTION_REBUILD_GRA_JUMPS")
        if receipt:
            return replay_payload(receipt)
        dataset = _load_dataset(cur, dataset_id)
        context = worksite_context(cur, user, dataset["worksite_id"])
        require_permission(context, "track.edit")
        sources, existing = _load_sources(cur, dataset_id), _load_existing_points(cur, dataset_id)
        generated, summary = build_gra_jump_points(
            dataset=dataset, sources=sources, existing_points=existing, allow_one_sided=payload.allow_one_sided,
        )
        preserved = [p for p in existing if not isinstance(p.get("payload"), dict) or not str(p["payload"].get("generated_by") or "").startswith("survey-gra-jump-v")]
        current = dataset.get("payload") or {}
        rebuilt = _build_payload(
            station_start_m=current.get("station_start_m"), station_end_m=current.get("station_end_m"),
            km_direction=current.get("km_direction"), metadata=current.get("metadata") or {},
            sources=sources, points=preserved + generated,
        )
        if "projection_summary" in current:
            rebuilt["projection_summary"] = current["projection_summary"]
        rebuilt["gra_jump_summary"] = summary
        cur.execute(
            "UPDATE tb.track_data SET payload=%s, version=version+1, updated_at=now() "
            "WHERE id=%s AND version=%s AND deleted_at IS NULL RETURNING *",
            (Jsonb(rebuilt), dataset_id, payload.base_version),
        )
        updated = cur.fetchone()
        if not updated:
            cur.execute("SELECT version FROM tb.track_data WHERE id=%s AND deleted_at IS NULL", (dataset_id,))
            current_row = cur.fetchone()
            if not current_row:
                raise HTTPException(404, "SURVEY_SECTION_NOT_FOUND")
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": current_row["version"]})
        dataset = dict(updated)
        cur.execute(
            "DELETE FROM tb.track_data_km_points WHERE dataset_id=%s "
            "AND payload->>'generated_by' LIKE 'survey-gra-jump-v%%'",
            (dataset_id,),
        )
        _insert_points(cur, dataset_id, generated, user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=dataset["worksite_id"], entity_type="TRACK_DATA",
                    entity_id=dataset_id, operation="UPDATE", entity_version=dataset["version"], actor_user_id=user["user_id"])
        audit(cur, action="SURVEY_SECTION_REBUILD_GRA_JUMPS", actor_user_id=user["user_id"], company_id=context["company_id"],
              worksite_id=dataset["worksite_id"], entity_type="TRACK_DATA", entity_id=dataset_id,
              details={"jump_version": GRA_JUMP_VERSION, "station_basis": summary["station_basis"],
                       "projection_version": summary["projection_version"],
                       "station_merge_version": summary["station_merge_version"],
                       "generated_jump_count": summary["generated_jump_count"],
                       "original_anchor_count": summary["original_anchor_count"], "allow_one_sided": summary["allow_one_sided"]})
        response = {"section": _section_response(cur, dataset), "gra_jump_summary": summary}
        finish_mutation(cur, user=user, context=mutation, receipt_type="SURVEY_SECTION_REBUILD_GRA_JUMPS",
                        entity_id=dataset_id, result_version=dataset["version"], response_payload=response)
    return response
