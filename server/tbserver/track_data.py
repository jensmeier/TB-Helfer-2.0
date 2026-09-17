from __future__ import annotations

from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .permissions import require_permission, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["track-data"])


class TrackDataCreate(BaseModel):
    id: UUID | None = None
    worksite_node_id: UUID | None = None
    dataset_key: str = Field(min_length=1, max_length=200)
    dataset_kind: str = Field(min_length=1, max_length=80)
    route_number: str | None = Field(default=None, max_length=80)
    track_key: str | None = Field(default=None, max_length=160)
    payload: dict = Field(default_factory=dict)
    source_original_id: UUID | None = None


class TrackDataUpdate(BaseModel):
    base_version: int = Field(ge=1)
    worksite_node_id: UUID | None = None
    dataset_kind: str | None = Field(default=None, min_length=1, max_length=80)
    route_number: str | None = Field(default=None, max_length=80)
    track_key: str | None = Field(default=None, max_length=160)
    payload: dict | None = None
    source_original_id: UUID | None = None


class VersionAction(BaseModel):
    base_version: int = Field(ge=1)


def _validate_node(cur, worksite_id: UUID, node_id: UUID | None) -> None:
    if node_id is None:
        return
    cur.execute(
        "SELECT 1 FROM tb.worksite_nodes WHERE id = %s AND worksite_id = %s AND deleted_at IS NULL",
        (node_id, worksite_id),
    )
    if not cur.fetchone():
        raise HTTPException(404, "WORKSITE_NODE_NOT_FOUND")


def _validate_source_original(cur, company_id: UUID, original_id: UUID | None) -> None:
    if original_id is None:
        return
    cur.execute(
        "SELECT id, company_id FROM tb.survey_originals WHERE id = %s",
        (original_id,),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "SURVEY_ORIGINAL_NOT_FOUND")
    if row["company_id"] is not None and row["company_id"] != company_id:
        raise HTTPException(403, "SURVEY_ORIGINAL_COMPANY_MISMATCH")


def _load_dataset(cur, dataset_id: UUID) -> dict:
    cur.execute("SELECT * FROM tb.track_data WHERE id = %s", (dataset_id,))
    row = cur.fetchone()
    if not row or row["deleted_at"] is not None:
        raise HTTPException(404, "TRACK_DATA_NOT_FOUND")
    return dict(row)


def _conflict_or_missing(cur, dataset_id: UUID) -> None:
    cur.execute("SELECT version, deleted_at FROM tb.track_data WHERE id = %s", (dataset_id,))
    current = cur.fetchone()
    if not current or current["deleted_at"] is not None:
        raise HTTPException(404, "TRACK_DATA_NOT_FOUND")
    raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": current["version"]})


@router.get("/worksites/{worksite_id}/track-data")
def list_track_data(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT id, worksite_id, worksite_node_id, dataset_key, dataset_kind,
                   route_number, track_key, payload, version, source_original_id,
                   created_by, created_at, updated_at
            FROM tb.track_data
            WHERE worksite_id = %s AND deleted_at IS NULL
            ORDER BY dataset_kind, dataset_key
            """,
            (worksite_id,),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {"track_data": rows}


@router.post("/worksites/{worksite_id}/track-data", status_code=201)
def create_track_data(
    worksite_id: UUID,
    payload: TrackDataCreate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    dataset_key = payload.dataset_key.strip()
    dataset_kind = payload.dataset_kind.strip().upper()
    if not dataset_key or not dataset_kind:
        raise HTTPException(400, "INVALID_TRACK_DATA_KEY")

    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "track.edit")
        _validate_node(cur, worksite_id, payload.worksite_node_id)
        _validate_source_original(cur, context["company_id"], payload.source_original_id)
        cur.execute(
            "SELECT id, deleted_at FROM tb.track_data WHERE worksite_id = %s AND dataset_key = %s",
            (worksite_id, dataset_key),
        )
        existing = cur.fetchone()
        if existing:
            raise HTTPException(409, {"code": "TRACK_DATA_KEY_EXISTS", "dataset_id": existing["id"]})

        values = (
            worksite_id,
            payload.worksite_node_id,
            dataset_key,
            dataset_kind,
            payload.route_number,
            payload.track_key,
            Jsonb(payload.payload),
            payload.source_original_id,
            user["user_id"],
        )
        if payload.id is None:
            cur.execute(
                """
                INSERT INTO tb.track_data(
                    worksite_id, worksite_node_id, dataset_key, dataset_kind,
                    route_number, track_key, payload, source_original_id, created_by
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                values,
            )
        else:
            cur.execute("SELECT 1 FROM tb.track_data WHERE id = %s", (payload.id,))
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
                (payload.id, *values),
            )
        row = dict(cur.fetchone())
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="TRACK_DATA",
            entity_id=row["id"],
            operation="CREATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="TRACK_DATA_CREATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="TRACK_DATA",
            entity_id=row["id"],
            details={"dataset_key": dataset_key, "dataset_kind": dataset_kind, "source_original_id": str(payload.source_original_id) if payload.source_original_id else None},
        )
    return row


@router.get("/track-data/{dataset_id}")
def get_track_data(
    dataset_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _load_dataset(cur, dataset_id)
        worksite_context(cur, user, row["worksite_id"])
    return row


@router.patch("/track-data/{dataset_id}")
def update_track_data(
    dataset_id: UUID,
    payload: TrackDataUpdate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    updates = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")

    with transaction() as conn, conn.cursor() as cur:
        dataset = _load_dataset(cur, dataset_id)
        context = worksite_context(cur, user, dataset["worksite_id"])
        require_permission(context, "track.edit")
        if "worksite_node_id" in updates:
            _validate_node(cur, dataset["worksite_id"], updates["worksite_node_id"])
        if "source_original_id" in updates:
            _validate_source_original(cur, context["company_id"], updates["source_original_id"])
        if "dataset_kind" in updates:
            updates["dataset_kind"] = updates["dataset_kind"].strip().upper()
            if not updates["dataset_kind"]:
                raise HTTPException(400, "INVALID_TRACK_DATA_KIND")

        columns = []
        values = []
        for key in ("worksite_node_id", "dataset_kind", "route_number", "track_key", "payload", "source_original_id"):
            if key in updates:
                columns.append(f"{key} = %s")
                values.append(Jsonb(updates[key]) if key == "payload" else updates[key])
        columns.extend(["version = version + 1", "updated_at = now()"])
        values.extend([dataset_id, payload.base_version])
        cur.execute(
            f"UPDATE tb.track_data SET {', '.join(columns)} WHERE id = %s AND version = %s AND deleted_at IS NULL RETURNING *",
            tuple(values),
        )
        row = cur.fetchone()
        if not row:
            _conflict_or_missing(cur, dataset_id)
        row = dict(row)
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=dataset["worksite_id"],
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            operation="UPDATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
    return row


@router.delete("/track-data/{dataset_id}", status_code=204)
def delete_track_data(
    dataset_id: UUID,
    payload: VersionAction,
    user: dict[str, Any] = Depends(require_csrf),
):
    with transaction() as conn, conn.cursor() as cur:
        dataset = _load_dataset(cur, dataset_id)
        context = worksite_context(cur, user, dataset["worksite_id"])
        require_permission(context, "track.edit")
        cur.execute(
            """
            UPDATE tb.track_data
            SET deleted_at = now(), version = version + 1, updated_at = now()
            WHERE id = %s AND version = %s AND deleted_at IS NULL
            RETURNING version
            """,
            (dataset_id, payload.base_version),
        )
        row = cur.fetchone()
        if not row:
            _conflict_or_missing(cur, dataset_id)
        version = row["version"]
        cur.execute(
            """
            INSERT INTO tb.deletion_tombstones(company_id, worksite_id, entity_type, entity_id, deleted_by)
            VALUES (%s,%s,'TRACK_DATA',%s,%s)
            ON CONFLICT (entity_type, entity_id) DO NOTHING
            """,
            (context["company_id"], dataset["worksite_id"], dataset_id, user["user_id"]),
        )
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=dataset["worksite_id"],
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            operation="DELETE",
            entity_version=version,
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="TRACK_DATA_DELETE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=dataset["worksite_id"],
            entity_type="TRACK_DATA",
            entity_id=dataset_id,
            details={"dataset_key": dataset["dataset_key"]},
        )
    return None
