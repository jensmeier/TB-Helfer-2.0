from __future__ import annotations

import math
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
from .survey_geometry import dbref_to_wgs84, densify_tra_payload, project_wgs84_to_samples, samples_to_wgs84
from .survey_sections import _build_payload, _insert_points, _load_dataset, _section_response


router = APIRouter(prefix="/api/v1", tags=["survey-projection"])
PROJECTION_VERSION = "survey-projection-v1"


class ProjectionRebuildRequest(BaseModel):
    base_version: int = Field(ge=1)
    max_distance_m: float = Field(default=15.0, ge=0.25, le=100.0)
    reliable_distance_m: float = Field(default=5.0, ge=0.25, le=50.0)


def _load_sources(cur, dataset_id: UUID) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT s.original_id, s.source_role AS role, s.source_order AS "order",
               s.reverse_geometry, s.metadata,
               o.route_number AS original_route_number, o.track_key AS original_track_key,
               o.data_kind, o.original_name, o.sha256,
               p.status AS parse_status, p.parser_family, p.parser_version,
               p.detected_format, p.payload AS parse_payload
        FROM tb.track_data_sources s
        JOIN tb.survey_originals o ON o.id = s.original_id
        LEFT JOIN tb.survey_parse_catalog p ON p.original_id = s.original_id
        WHERE s.dataset_id = %s
        ORDER BY CASE WHEN s.source_role='GEOMETRY' THEN 0 ELSE 1 END,
                 s.source_order, s.original_id
        """,
        (dataset_id,),
    )
    return [dict(row) for row in cur.fetchall()]


def _load_existing_points(cur, dataset_id: UUID) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT id, point_type, station_m, km_meter, km_after_jump_meter,
               source_original_id, payload, created_by, created_at
        FROM tb.track_data_km_points
        WHERE dataset_id = %s
        ORDER BY station_m, point_type, id
        """,
        (dataset_id,),
    )
    return [dict(row) for row in cur.fetchall()]


def _expected_route(dataset: dict[str, Any], geometry_sources: list[dict[str, Any]]) -> str | None:
    if dataset.get("route_number"):
        return str(dataset["route_number"]).strip() or None
    routes = {
        str((source.get("parse_payload") or {}).get("route_from_name") or source.get("original_route_number") or "").strip()
        for source in geometry_sources
    }
    routes.discard("")
    return next(iter(routes)) if len(routes) == 1 else None


def _point_route(point: dict[str, Any], source: dict[str, Any]) -> str | None:
    value = point.get("route") or source.get("original_route_number")
    if value is None:
        return None
    result = str(value).strip()
    return result or None


def _point_key(point: dict[str, Any], index: int) -> str:
    for key in ("record_index", "point_index", "class", "point_name"):
        value = point.get(key)
        if value not in (None, ""):
            return f"{key}:{value}"
    return f"index:{index}"


def _geometry_cache(source: dict[str, Any]) -> dict[str, Any] | None:
    if source.get("parse_status") != "PARSED" or source.get("detected_format") != "TRA_ER0_BINARY":
        return None
    payload = source.get("parse_payload") or {}
    dense = densify_tra_payload(payload)
    samples = samples_to_wgs84(dense)
    if len(samples) < 2:
        return None
    return {"source": source, "samples": samples, "sample_count": len(samples)}


def _project_source_points(
    *,
    dataset_id: UUID,
    source: dict[str, Any],
    geometry: list[dict[str, Any]],
    primary_geometry_id: UUID,
    expected_route: str | None,
    max_distance_m: float,
    reliable_distance_m: float,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    payload = source.get("parse_payload") or {}
    raw_points = payload.get("points") if isinstance(payload, dict) else None
    summary = {
        "source_original_id": str(source["original_id"]),
        "source_role": source["role"],
        "detected_format": source.get("detected_format"),
        "point_count": len(raw_points) if isinstance(raw_points, list) else 0,
        "generated_original_anchors": 0,
        "generated_control_anchors": 0,
        "skipped_no_km": 0,
        "skipped_no_xy": 0,
        "skipped_conversion": 0,
        "skipped_route_mismatch": 0,
        "skipped_too_far": 0,
        "skipped_no_geometry": 0,
    }
    if source.get("parse_status") != "PARSED" or not isinstance(raw_points, list):
        return [], summary

    generated: list[dict[str, Any]] = []
    for index, point in enumerate(raw_points):
        if not isinstance(point, dict):
            continue
        km_raw = point.get("km_meter")
        try:
            km_meter = float(km_raw) if km_raw is not None else None
        except (TypeError, ValueError):
            km_meter = None
        if km_meter is None or not math.isfinite(km_meter):
            summary["skipped_no_km"] += 1
            continue
        x_raw, y_raw = point.get("x_dbref"), point.get("y_dbref")
        if x_raw is None or y_raw is None:
            summary["skipped_no_xy"] += 1
            continue
        gps = dbref_to_wgs84(x_raw, y_raw)
        if gps is None:
            summary["skipped_conversion"] += 1
            continue

        route = _point_route(point, source)
        if expected_route is not None and route is not None and route != expected_route:
            summary["skipped_route_mismatch"] += 1
            continue

        best: tuple[dict[str, Any], dict[str, Any]] | None = None
        for geometry_item in geometry:
            projection = project_wgs84_to_samples(gps[0], gps[1], geometry_item["samples"])
            if projection is None:
                continue
            if best is None or float(projection["distance_m"]) < float(best[1]["distance_m"]):
                best = (geometry_item, projection)
        if best is None:
            summary["skipped_no_geometry"] += 1
            continue
        geometry_item, projection = best
        distance_m = float(projection["distance_m"])
        if distance_m > max_distance_m:
            summary["skipped_too_far"] += 1
            continue

        geometry_id = geometry_item["source"]["original_id"]
        route_confirmed = expected_route is not None and route == expected_route
        reliable = (
            source["role"] == "KM"
            and geometry_id == primary_geometry_id
            and route_confirmed
            and distance_m <= reliable_distance_m
        )
        point_type = "ORIGINAL_ANCHOR" if reliable else "CONTROL_ANCHOR"
        summary["generated_original_anchors" if reliable else "generated_control_anchors"] += 1
        deterministic_id = uuid5(
            NAMESPACE_URL,
            ":".join(
                [
                    PROJECTION_VERSION,
                    str(dataset_id),
                    str(source["original_id"]),
                    str(geometry_id),
                    _point_key(point, index),
                    f"{km_meter:.6f}",
                ]
            ),
        )
        generated.append(
            {
                "id": deterministic_id,
                "point_type": point_type,
                "station_m": float(projection["station_m"]),
                "km_meter": km_meter,
                "km_after_jump_meter": None,
                "source_original_id": source["original_id"],
                "payload": {
                    "generated_by": PROJECTION_VERSION,
                    "geometry_original_id": str(geometry_id),
                    "source_point_key": _point_key(point, index),
                    "source_point_name": point.get("point_name"),
                    "source_point_type": point.get("type"),
                    "source_coordinate_system": point.get("coordinate_system"),
                    "source_route": route,
                    "expected_route": expected_route,
                    "route_confirmed": route_confirmed,
                    "projection_distance_m": round(distance_m, 4),
                    "projection_quality": projection["quality"],
                    "projected_lat": projection["projected_lat"],
                    "projected_lon": projection["projected_lon"],
                    "primary_geometry": geometry_id == primary_geometry_id,
                    "reliable_distance_m": reliable_distance_m,
                },
            }
        )
    return generated, summary


def build_projection_points(
    *,
    dataset: dict[str, Any],
    sources: list[dict[str, Any]],
    max_distance_m: float,
    reliable_distance_m: float,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    geometry_sources = [source for source in sources if source["role"] == "GEOMETRY"]
    geometry = [item for source in geometry_sources if (item := _geometry_cache(source)) is not None]
    if not geometry:
        raise HTTPException(409, "SURVEY_SECTION_GEOMETRY_NOT_PROJECTABLE")
    primary_geometry_id = geometry[0]["source"]["original_id"]
    expected_route = _expected_route(dataset, geometry_sources)
    generated: list[dict[str, Any]] = []
    source_summaries: list[dict[str, Any]] = []
    for source in sources:
        if source["role"] not in {"KM", "CONTROL"}:
            continue
        points, source_summary = _project_source_points(
            dataset_id=dataset["id"],
            source=source,
            geometry=geometry,
            primary_geometry_id=primary_geometry_id,
            expected_route=expected_route,
            max_distance_m=max_distance_m,
            reliable_distance_m=reliable_distance_m,
        )
        generated.extend(points)
        source_summaries.append(source_summary)
    generated.sort(key=lambda point: (point["station_m"], point["point_type"], str(point["id"])))
    return generated, {
        "projection_version": PROJECTION_VERSION,
        "expected_route": expected_route,
        "primary_geometry_original_id": str(primary_geometry_id),
        "geometry_source_count": len(geometry_sources),
        "projectable_geometry_source_count": len(geometry),
        "dense_sample_count": sum(item["sample_count"] for item in geometry),
        "generated_original_anchors": sum(1 for point in generated if point["point_type"] == "ORIGINAL_ANCHOR"),
        "generated_control_anchors": sum(1 for point in generated if point["point_type"] == "CONTROL_ANCHOR"),
        "max_distance_m": max_distance_m,
        "reliable_distance_m": reliable_distance_m,
        "sources": source_summaries,
    }


@router.post("/survey-sections/{dataset_id}/rebuild-auto-anchors")
def rebuild_auto_anchors(
    dataset_id: UUID,
    payload: ProjectionRebuildRequest,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    if payload.reliable_distance_m > payload.max_distance_m:
        raise HTTPException(400, "RELIABLE_DISTANCE_EXCEEDS_MAX_DISTANCE")

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="SURVEY_SECTION_REBUILD_AUTO_ANCHORS")
        if receipt:
            return replay_payload(receipt)
        dataset = _load_dataset(cur, dataset_id)
        context = worksite_context(cur, user, dataset["worksite_id"])
        require_permission(context, "track.edit")
        sources = _load_sources(cur, dataset_id)
        generated, summary = build_projection_points(
            dataset=dataset,
            sources=sources,
            max_distance_m=payload.max_distance_m,
            reliable_distance_m=payload.reliable_distance_m,
        )
        existing = _load_existing_points(cur, dataset_id)
        preserved = [
            point
            for point in existing
            if not isinstance(point.get("payload"), dict)
            or point["payload"].get("generated_by") != PROJECTION_VERSION
        ]
        all_points = preserved + generated
        current_payload = dataset.get("payload") or {}
        rebuilt_payload = _build_payload(
            station_start_m=current_payload.get("station_start_m"),
            station_end_m=current_payload.get("station_end_m"),
            km_direction=current_payload.get("km_direction"),
            metadata=current_payload.get("metadata") or {},
            sources=sources,
            points=all_points,
        )
        rebuilt_payload["projection_summary"] = summary

        cur.execute(
            """
            UPDATE tb.track_data
            SET payload = %s, version = version + 1, updated_at = now()
            WHERE id = %s AND version = %s AND deleted_at IS NULL
            RETURNING *
            """,
            (Jsonb(rebuilt_payload), dataset_id, payload.base_version),
        )
        updated = cur.fetchone()
        if not updated:
            cur.execute("SELECT version FROM tb.track_data WHERE id = %s AND deleted_at IS NULL", (dataset_id,))
            current = cur.fetchone()
            if not current:
                raise HTTPException(404, "SURVEY_SECTION_NOT_FOUND")
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": current["version"]})
        dataset = dict(updated)

        cur.execute(
            """
            DELETE FROM tb.track_data_km_points
            WHERE dataset_id = %s AND payload->>'generated_by' = %s
            """,
            (dataset_id, PROJECTION_VERSION),
        )
        _insert_points(cur, dataset_id, generated, user["user_id"])
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=dataset["worksite_id"],
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            operation="UPDATE",
            entity_version=dataset["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="SURVEY_SECTION_REBUILD_AUTO_ANCHORS",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=dataset["worksite_id"],
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            details={
                "projection_version": PROJECTION_VERSION,
                "generated_original_anchors": summary["generated_original_anchors"],
                "generated_control_anchors": summary["generated_control_anchors"],
                "dense_sample_count": summary["dense_sample_count"],
            },
        )
        response = {"section": _section_response(cur, dataset), "projection_summary": summary}
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SURVEY_SECTION_REBUILD_AUTO_ANCHORS",
            entity_id=dataset_id,
            result_version=dataset["version"],
            response_payload=response,
        )
    return response
