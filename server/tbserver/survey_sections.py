from __future__ import annotations

import math
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .permissions import require_permission, worksite_context
from .portal import current_portal_user, require_csrf
from .track_visibility import track_data_visible
from .survey_calibration import (
    DIRECTION_DOWN,
    DIRECTION_UP,
    POINT_CONTROL,
    POINT_JUMP,
    POINT_MANUAL,
    POINT_ORIGINAL,
    evaluate_calibration,
    km_at_station,
)


router = APIRouter(prefix="/api/v1", tags=["survey-sections"])
SECTION_KIND = "SURVEY_SECTION"
SECTION_SCHEMA = "SURVEY_SECTION_V1"
SOURCE_ROLES = {"GEOMETRY", "KM", "HEIGHT", "CONTROL", "OTHER"}
POINT_TYPES = {POINT_ORIGINAL, POINT_MANUAL, POINT_CONTROL, POINT_JUMP}


class SurveySectionSource(BaseModel):
    original_id: UUID
    role: str = Field(min_length=1, max_length=32)
    order: int = Field(default=0, ge=0, le=100_000)
    reverse_geometry: bool = False
    metadata: dict = Field(default_factory=dict)


class SurveySectionKmPoint(BaseModel):
    id: UUID | None = None
    point_type: str = Field(min_length=1, max_length=32)
    station_m: float
    km_meter: float | None = None
    km_after_jump_meter: float | None = None
    source_original_id: UUID | None = None
    payload: dict = Field(default_factory=dict)


class SurveySectionCreate(BaseModel):
    id: UUID | None = None
    worksite_node_id: UUID | None = None
    dataset_key: str = Field(min_length=1, max_length=200)
    route_number: str | None = Field(default=None, max_length=80)
    track_key: str | None = Field(default=None, max_length=160)
    station_start_m: float | None = None
    station_end_m: float | None = None
    km_direction: str | None = Field(default=None, max_length=16)
    metadata: dict = Field(default_factory=dict)
    sources: list[SurveySectionSource] = Field(min_length=1)
    km_points: list[SurveySectionKmPoint] = Field(default_factory=list)


class SurveySectionUpdate(BaseModel):
    base_version: int = Field(ge=1)
    worksite_node_id: UUID | None = None
    route_number: str | None = Field(default=None, max_length=80)
    track_key: str | None = Field(default=None, max_length=160)
    station_start_m: float | None = None
    station_end_m: float | None = None
    km_direction: str | None = Field(default=None, max_length=16)
    metadata: dict | None = None
    sources: list[SurveySectionSource] | None = None
    km_points: list[SurveySectionKmPoint] | None = None


def _finite_or_none(value: float | None, field: str) -> float | None:
    if value is None:
        return None
    result = float(value)
    if not math.isfinite(result):
        raise HTTPException(400, f"INVALID_{field.upper()}")
    return result


def _normalize_direction(value: str | None) -> str | None:
    if value is None or not value.strip():
        return None
    result = value.strip().upper()
    if result not in {DIRECTION_UP, DIRECTION_DOWN}:
        raise HTTPException(400, "INVALID_KM_DIRECTION")
    return result


def _validate_section_range(start: float | None, end: float | None) -> tuple[float | None, float | None]:
    start = _finite_or_none(start, "station_start_m")
    end = _finite_or_none(end, "station_end_m")
    if start is not None and end is not None and abs(end - start) < 0.001:
        raise HTTPException(400, "SURVEY_SECTION_RANGE_TOO_SMALL")
    return start, end


def _validate_node(cur, worksite_id: UUID, node_id: UUID | None) -> None:
    if node_id is None:
        return
    cur.execute(
        "SELECT 1 FROM tb.worksite_nodes WHERE id = %s AND worksite_id = %s AND deleted_at IS NULL",
        (node_id, worksite_id),
    )
    if not cur.fetchone():
        raise HTTPException(404, "WORKSITE_NODE_NOT_FOUND")


def _normalize_sources(cur, company_id: UUID, sources: list[SurveySectionSource]) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    geometry_count = 0
    seen: set[tuple[UUID, str]] = set()
    for source in sources:
        role = source.role.strip().upper()
        if role not in SOURCE_ROLES:
            raise HTTPException(400, {"code": "INVALID_SURVEY_SECTION_SOURCE_ROLE", "role": role})
        key = (source.original_id, role)
        if key in seen:
            raise HTTPException(400, "DUPLICATE_SURVEY_SECTION_SOURCE")
        seen.add(key)
        cur.execute(
            """
            SELECT o.id, o.company_id, o.data_kind, o.original_name, o.sha256,
                   p.status AS parse_status, p.parser_family, p.parser_version,
                   p.detected_format, p.payload AS parse_payload
            FROM tb.survey_originals o
            LEFT JOIN tb.survey_parse_catalog p ON p.original_id = o.id
            WHERE o.id = %s
            """,
            (source.original_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, {"code": "SURVEY_ORIGINAL_NOT_FOUND", "original_id": str(source.original_id)})
        row = dict(row)
        if row["company_id"] is not None and row["company_id"] != company_id:
            raise HTTPException(403, "SURVEY_ORIGINAL_COMPANY_MISMATCH")
        if role != "OTHER" and row["parse_status"] != "PARSED":
            raise HTTPException(409, {"code": "SURVEY_SOURCE_NOT_PARSED", "original_id": str(source.original_id)})
        if role == "GEOMETRY":
            geometry_count += 1
            if row["detected_format"] != "TRA_ER0_BINARY":
                raise HTTPException(409, {"code": "SURVEY_GEOMETRY_REQUIRES_TRA", "original_id": str(source.original_id)})
        normalized.append(
            {
                "original_id": source.original_id,
                "role": role,
                "order": source.order,
                "reverse_geometry": bool(source.reverse_geometry),
                "metadata": dict(source.metadata),
                "original_name": row["original_name"],
                "sha256": row["sha256"],
                "parser_family": row["parser_family"],
                "parser_version": row["parser_version"],
                "detected_format": row["detected_format"],
                "parse_payload": row["parse_payload"] or {},
            }
        )
    if geometry_count == 0:
        raise HTTPException(400, "SURVEY_SECTION_REQUIRES_GEOMETRY_SOURCE")
    normalized.sort(key=lambda item: (0 if item["role"] == "GEOMETRY" else 1, item["order"], str(item["original_id"])))
    return normalized


def _source_models_from_db(cur, dataset_id: UUID) -> list[SurveySectionSource]:
    cur.execute(
        """
        SELECT original_id, source_role, source_order, reverse_geometry, metadata
        FROM tb.track_data_sources
        WHERE dataset_id = %s
        ORDER BY CASE WHEN source_role='GEOMETRY' THEN 0 ELSE 1 END, source_order, original_id
        """,
        (dataset_id,),
    )
    return [
        SurveySectionSource(
            original_id=row["original_id"],
            role=row["source_role"],
            order=row["source_order"],
            reverse_geometry=row["reverse_geometry"],
            metadata=row["metadata"] or {},
        )
        for row in cur.fetchall()
    ]


def _normalize_points(points: list[SurveySectionKmPoint], linked_original_ids: set[UUID]) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    seen_ids: set[UUID] = set()
    for point in points:
        point_type = point.point_type.strip().upper()
        if point_type not in POINT_TYPES:
            raise HTTPException(400, {"code": "INVALID_SURVEY_KM_POINT_TYPE", "point_type": point_type})
        point_id = point.id or uuid4()
        if point_id in seen_ids:
            raise HTTPException(400, "DUPLICATE_SURVEY_KM_POINT_ID")
        seen_ids.add(point_id)
        station = _finite_or_none(point.station_m, "station_m")
        assert station is not None
        km = _finite_or_none(point.km_meter, "km_meter")
        km_after = _finite_or_none(point.km_after_jump_meter, "km_after_jump_meter")
        if point_type == POINT_JUMP:
            if km is None or km_after is None:
                raise HTTPException(400, "KM_JUMP_REQUIRES_BEFORE_AND_AFTER")
        elif km is None or km_after is not None:
            raise HTTPException(400, "KM_ANCHOR_REQUIRES_ONE_KM_VALUE")
        if point.source_original_id is not None and point.source_original_id not in linked_original_ids:
            raise HTTPException(400, "KM_POINT_SOURCE_MUST_BE_LINKED")
        if point_type == POINT_ORIGINAL and point.source_original_id is None:
            raise HTTPException(400, "ORIGINAL_ANCHOR_REQUIRES_SOURCE")
        normalized.append(
            {
                "id": point_id,
                "point_type": point_type,
                "station_m": station,
                "km_meter": km,
                "km_after_jump_meter": km_after,
                "source_original_id": point.source_original_id,
                "payload": dict(point.payload),
            }
        )
    normalized.sort(key=lambda item: (item["station_m"], item["point_type"], str(item["id"])))
    return normalized


def _point_models_from_db(cur, dataset_id: UUID) -> list[SurveySectionKmPoint]:
    cur.execute(
        """
        SELECT id, point_type, station_m, km_meter, km_after_jump_meter, source_original_id, payload
        FROM tb.track_data_km_points
        WHERE dataset_id = %s
        ORDER BY station_m, point_type, id
        """,
        (dataset_id,),
    )
    return [
        SurveySectionKmPoint(
            id=row["id"],
            point_type=row["point_type"],
            station_m=row["station_m"],
            km_meter=row["km_meter"],
            km_after_jump_meter=row["km_after_jump_meter"],
            source_original_id=row["source_original_id"],
            payload=row["payload"] or {},
        )
        for row in cur.fetchall()
    ]


def _xy_endpoint(payload: dict[str, Any], reverse: bool) -> tuple[tuple[float, float], tuple[float, float]] | None:
    nodes = payload.get("nodes") if isinstance(payload, dict) else None
    if not isinstance(nodes, list) or len(nodes) < 2:
        return None
    try:
        first = (float(nodes[0]["x_dbref"]), float(nodes[0]["y_dbref"]))
        last = (float(nodes[-1]["x_dbref"]), float(nodes[-1]["y_dbref"]))
    except (KeyError, TypeError, ValueError):
        return None
    if not all(math.isfinite(value) for value in (*first, *last)):
        return None
    return (last, first) if reverse else (first, last)


def geometry_merge_diagnostics(sources: list[dict[str, Any]]) -> dict[str, Any]:
    geometry = sorted(
        [source for source in sources if source["role"] == "GEOMETRY"],
        key=lambda item: (item["order"], str(item["original_id"])),
    )
    if not geometry:
        return {"status": "NO_GEOMETRY", "source_count": 0, "joins": [], "max_gap_m": None}
    if len(geometry) == 1:
        return {"status": "SINGLE", "source_count": 1, "joins": [], "max_gap_m": 0.0}

    endpoints = [_xy_endpoint(item.get("parse_payload") or {}, bool(item.get("reverse_geometry"))) for item in geometry]
    joins: list[dict[str, Any]] = []
    max_gap = 0.0
    unverified = False
    open_join = False
    warning = False
    for index in range(len(geometry) - 1):
        left, right = endpoints[index], endpoints[index + 1]
        if left is None or right is None:
            unverified = True
            joins.append(
                {
                    "from_original_id": str(geometry[index]["original_id"]),
                    "to_original_id": str(geometry[index + 1]["original_id"]),
                    "gap_m": None,
                    "status": "UNVERIFIED",
                }
            )
            continue
        gap = math.hypot(left[1][0] - right[0][0], left[1][1] - right[0][1])
        max_gap = max(max_gap, gap)
        join_status = "CONNECTED" if gap <= 5.0 else "WARNING" if gap <= 20.0 else "OPEN"
        warning = warning or join_status == "WARNING"
        open_join = open_join or join_status == "OPEN"
        joins.append(
            {
                "from_original_id": str(geometry[index]["original_id"]),
                "to_original_id": str(geometry[index + 1]["original_id"]),
                "gap_m": round(gap, 4),
                "status": join_status,
            }
        )
    status = "OPEN" if open_join else "UNVERIFIED" if unverified else "WARNING" if warning else "CONNECTED"
    return {"status": status, "source_count": len(geometry), "joins": joins, "max_gap_m": round(max_gap, 4)}


def _build_payload(
    *,
    station_start_m: float | None,
    station_end_m: float | None,
    km_direction: str | None,
    metadata: dict,
    sources: list[dict[str, Any]],
    points: list[dict[str, Any]],
) -> dict[str, Any]:
    merge = geometry_merge_diagnostics(sources)
    calibration = evaluate_calibration(
        points,
        station_start_m=station_start_m,
        station_end_m=station_end_m,
        km_direction=km_direction,
        has_geometry=bool([source for source in sources if source["role"] == "GEOMETRY"]),
    )
    if station_start_m is not None:
        calibration["km_at_start_m"] = km_at_station(calibration, points, station_start_m)
    if station_end_m is not None:
        calibration["km_at_end_m"] = km_at_station(calibration, points, station_end_m)
    return {
        "schema": SECTION_SCHEMA,
        "station_start_m": station_start_m,
        "station_end_m": station_end_m,
        "km_direction": km_direction,
        "metadata": metadata,
        "merge": merge,
        "calibration": calibration,
        "source_parser_versions": sorted(
            {
                str(source["parser_version"])
                for source in sources
                if source.get("parser_version")
            }
        ),
    }


def _insert_sources(cur, dataset_id: UUID, sources: list[dict[str, Any]]) -> None:
    for source in sources:
        cur.execute(
            """
            INSERT INTO tb.track_data_sources(
                dataset_id, original_id, source_role, source_order, reverse_geometry, metadata
            ) VALUES (%s,%s,%s,%s,%s,%s)
            """,
            (
                dataset_id,
                source["original_id"],
                source["role"],
                source["order"],
                source["reverse_geometry"],
                Jsonb(source["metadata"]),
            ),
        )


def _insert_points(cur, dataset_id: UUID, points: list[dict[str, Any]], user_id: UUID) -> None:
    for point in points:
        cur.execute(
            """
            INSERT INTO tb.track_data_km_points(
                id, dataset_id, point_type, station_m, km_meter, km_after_jump_meter,
                source_original_id, payload, created_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (
                point["id"],
                dataset_id,
                point["point_type"],
                point["station_m"],
                point["km_meter"],
                point["km_after_jump_meter"],
                point["source_original_id"],
                Jsonb(point["payload"]),
                user_id,
            ),
        )


def _load_dataset(cur, dataset_id: UUID) -> dict[str, Any]:
    cur.execute("SELECT * FROM tb.track_data WHERE id = %s AND deleted_at IS NULL", (dataset_id,))
    row = cur.fetchone()
    if not row or row["dataset_kind"] != SECTION_KIND:
        raise HTTPException(404, "SURVEY_SECTION_NOT_FOUND")
    return dict(row)


def _section_response(cur, dataset: dict[str, Any]) -> dict[str, Any]:
    dataset_id = dataset["id"]
    cur.execute(
        """
        SELECT s.original_id, s.source_role, s.source_order, s.reverse_geometry, s.metadata,
               o.original_name, o.data_kind, o.sha256,
               p.parser_version, p.detected_format, p.status AS parse_status
        FROM tb.track_data_sources s
        JOIN tb.survey_originals o ON o.id = s.original_id
        LEFT JOIN tb.survey_parse_catalog p ON p.original_id = s.original_id
        WHERE s.dataset_id = %s
        ORDER BY CASE WHEN s.source_role='GEOMETRY' THEN 0 ELSE 1 END, s.source_order, s.original_id
        """,
        (dataset_id,),
    )
    sources = [dict(row) for row in cur.fetchall()]
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
    points = [dict(row) for row in cur.fetchall()]
    return {**dataset, "sources": sources, "km_points": points}


@router.get("/worksites/{worksite_id}/survey-sections")
def list_survey_sections(
    worksite_id: UUID,
    limit: int = Query(default=200, ge=1, le=1000),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "worksite.view")
        cur.execute(
            """
            SELECT id, worksite_id, worksite_node_id, dataset_key, dataset_kind, route_number, track_key,
                   payload, version, source_original_id, created_by, created_at, updated_at
            FROM tb.track_data
            WHERE worksite_id = %s AND dataset_kind = %s AND deleted_at IS NULL
            ORDER BY dataset_key
            LIMIT %s
            """,
            (worksite_id, SECTION_KIND, limit),
        )
        rows = [dict(row) for row in cur.fetchall() if track_data_visible(context, dict(row))]
    return {"survey_sections": rows}


@router.post("/worksites/{worksite_id}/survey-sections", status_code=201)
def create_survey_section(
    worksite_id: UUID,
    payload: SurveySectionCreate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    dataset_key = payload.dataset_key.strip()
    if not dataset_key:
        raise HTTPException(400, "INVALID_TRACK_DATA_KEY")
    station_start, station_end = _validate_section_range(payload.station_start_m, payload.station_end_m)
    direction = _normalize_direction(payload.km_direction)

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="SURVEY_SECTION_CREATE")
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "track.edit")
        _validate_node(cur, worksite_id, payload.worksite_node_id)
        cur.execute(
            "SELECT id FROM tb.track_data WHERE worksite_id = %s AND dataset_key = %s",
            (worksite_id, dataset_key),
        )
        if cur.fetchone():
            raise HTTPException(409, "TRACK_DATA_KEY_EXISTS")

        sources = _normalize_sources(cur, context["company_id"], payload.sources)
        linked_ids = {source["original_id"] for source in sources}
        points = _normalize_points(payload.km_points, linked_ids)
        section_payload = _build_payload(
            station_start_m=station_start,
            station_end_m=station_end,
            km_direction=direction,
            metadata=dict(payload.metadata),
            sources=sources,
            points=points,
        )
        first_geometry = next(source["original_id"] for source in sources if source["role"] == "GEOMETRY")
        dataset_id = payload.id or uuid4()
        cur.execute("SELECT 1 FROM tb.track_data WHERE id = %s", (dataset_id,))
        if cur.fetchone():
            raise HTTPException(409, "TRACK_DATA_ID_EXISTS")
        cur.execute(
            """
            INSERT INTO tb.track_data(
                id, worksite_id, worksite_node_id, dataset_key, dataset_kind,
                route_number, track_key, payload, source_original_id, created_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            RETURNING *
            """,
            (
                dataset_id,
                worksite_id,
                payload.worksite_node_id,
                dataset_key,
                SECTION_KIND,
                payload.route_number,
                payload.track_key,
                Jsonb(section_payload),
                first_geometry,
                user["user_id"],
            ),
        )
        dataset = dict(cur.fetchone())
        _insert_sources(cur, dataset_id, sources)
        _insert_points(cur, dataset_id, points, user["user_id"])
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            operation="CREATE",
            entity_version=dataset["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="SURVEY_SECTION_CREATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            details={"dataset_key": dataset_key, "source_count": len(sources), "km_point_count": len(points)},
        )
        response = _section_response(cur, dataset)
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SURVEY_SECTION_CREATE",
            entity_id=dataset_id,
            result_version=dataset["version"],
            response_payload=response,
        )
    return response


@router.get("/survey-sections/{dataset_id}")
def get_survey_section(dataset_id: UUID, user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        dataset = _load_dataset(cur, dataset_id)
        context = worksite_context(cur, user, dataset["worksite_id"])
        require_permission(context, "worksite.view")
        if not track_data_visible(context, dataset):
            raise HTTPException(404, "SURVEY_SECTION_NOT_FOUND")
        return _section_response(cur, dataset)


@router.patch("/survey-sections/{dataset_id}")
def update_survey_section(
    dataset_id: UUID,
    payload: SurveySectionUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    changes = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not changes:
        raise HTTPException(400, "NO_CHANGES")

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="SURVEY_SECTION_UPDATE")
        if receipt:
            return replay_payload(receipt)
        dataset = _load_dataset(cur, dataset_id)
        context = worksite_context(cur, user, dataset["worksite_id"])
        require_permission(context, "track.edit")

        current_payload = dataset["payload"] or {}
        worksite_node_id = changes.get("worksite_node_id", dataset["worksite_node_id"])
        _validate_node(cur, dataset["worksite_id"], worksite_node_id)
        route_number = changes.get("route_number", dataset["route_number"])
        track_key = changes.get("track_key", dataset["track_key"])
        station_start = changes.get("station_start_m", current_payload.get("station_start_m"))
        station_end = changes.get("station_end_m", current_payload.get("station_end_m"))
        station_start, station_end = _validate_section_range(station_start, station_end)
        direction = _normalize_direction(changes.get("km_direction", current_payload.get("km_direction")))
        metadata = changes.get("metadata") if "metadata" in changes else (current_payload.get("metadata") or {})
        metadata = {} if metadata is None else metadata

        sources_changed = "sources" in changes
        points_changed = "km_points" in changes
        if sources_changed and payload.sources is None:
            raise HTTPException(400, "SURVEY_SECTION_SOURCES_MAY_NOT_BE_NULL")
        if points_changed and payload.km_points is None:
            raise HTTPException(400, "SURVEY_SECTION_KM_POINTS_MAY_NOT_BE_NULL")
        source_models = payload.sources if sources_changed else _source_models_from_db(cur, dataset_id)
        point_models = payload.km_points if points_changed else _point_models_from_db(cur, dataset_id)
        assert source_models is not None and point_models is not None
        sources = _normalize_sources(cur, context["company_id"], source_models)
        linked_ids = {source["original_id"] for source in sources}
        points = _normalize_points(point_models, linked_ids)
        section_payload = _build_payload(
            station_start_m=station_start,
            station_end_m=station_end,
            km_direction=direction,
            metadata=dict(metadata),
            sources=sources,
            points=points,
        )
        first_geometry = next(source["original_id"] for source in sources if source["role"] == "GEOMETRY")

        cur.execute(
            """
            UPDATE tb.track_data
            SET worksite_node_id = %s, route_number = %s, track_key = %s,
                payload = %s, source_original_id = %s,
                version = version + 1, updated_at = now()
            WHERE id = %s AND version = %s AND deleted_at IS NULL
            RETURNING *
            """,
            (
                worksite_node_id,
                route_number,
                track_key,
                Jsonb(section_payload),
                first_geometry,
                dataset_id,
                payload.base_version,
            ),
        )
        updated = cur.fetchone()
        if not updated:
            cur.execute("SELECT version FROM tb.track_data WHERE id = %s AND deleted_at IS NULL", (dataset_id,))
            current = cur.fetchone()
            if not current:
                raise HTTPException(404, "SURVEY_SECTION_NOT_FOUND")
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": current["version"]})
        dataset = dict(updated)

        if sources_changed:
            cur.execute("DELETE FROM tb.track_data_sources WHERE dataset_id = %s", (dataset_id,))
            _insert_sources(cur, dataset_id, sources)
        if points_changed:
            cur.execute("DELETE FROM tb.track_data_km_points WHERE dataset_id = %s", (dataset_id,))
            _insert_points(cur, dataset_id, points, user["user_id"])
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
            action="SURVEY_SECTION_UPDATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=dataset["worksite_id"],
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            details={"source_count": len(sources), "km_point_count": len(points)},
        )
        response = _section_response(cur, dataset)
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="SURVEY_SECTION_UPDATE",
            entity_id=dataset_id,
            result_version=dataset["version"],
            response_payload=response,
        )
    return response
