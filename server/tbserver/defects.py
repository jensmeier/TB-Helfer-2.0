from __future__ import annotations

from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .permissions import require_permission, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["defects"])


class DefectCreate(BaseModel):
    id: UUID | None = None
    worksite_node_id: UUID | None = None
    title: str | None = Field(default=None, max_length=500)
    payload: dict = Field(default_factory=dict)


class DefectUpdate(BaseModel):
    base_version: int = Field(ge=1)
    worksite_node_id: UUID | None = None
    title: str | None = Field(default=None, max_length=500)
    payload: dict | None = None
    status: str | None = Field(default=None, pattern="^(OPEN|IN_PROGRESS)$")


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


def _load_defect(cur, defect_id: UUID) -> dict:
    cur.execute("SELECT * FROM tb.defects WHERE id = %s", (defect_id,))
    row = cur.fetchone()
    if not row or row["status"] == "DELETED" or row["deleted_at"] is not None:
        raise HTTPException(404, "DEFECT_NOT_FOUND")
    return dict(row)


def _conflict_or_missing(cur, defect_id: UUID) -> None:
    cur.execute("SELECT version FROM tb.defects WHERE id = %s", (defect_id,))
    current = cur.fetchone()
    if not current:
        raise HTTPException(404, "DEFECT_NOT_FOUND")
    raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": current["version"]})


@router.get("/worksites/{worksite_id}/defects")
def list_defects(worksite_id: UUID, user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT id, worksite_id, worksite_node_id, status, title, payload, version,
                   created_by, closed_by, created_at, updated_at, closed_at
            FROM tb.defects
            WHERE worksite_id = %s AND status <> 'DELETED' AND deleted_at IS NULL
            ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END,
                     updated_at DESC, created_at DESC
            """,
            (worksite_id,),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {"defects": rows}


@router.post("/worksites/{worksite_id}/defects", status_code=201)
def create_defect(
    worksite_id: UUID,
    payload: DefectCreate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="DEFECT_CREATE")
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "defect.create")
        _validate_node(cur, worksite_id, payload.worksite_node_id)
        if payload.id is None:
            cur.execute(
                """
                INSERT INTO tb.defects(worksite_id, worksite_node_id, title, payload, created_by)
                VALUES (%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (worksite_id, payload.worksite_node_id, payload.title, Jsonb(payload.payload), user["user_id"]),
            )
        else:
            cur.execute("SELECT 1 FROM tb.defects WHERE id = %s", (payload.id,))
            if cur.fetchone():
                raise HTTPException(409, "DEFECT_ID_EXISTS")
            cur.execute(
                """
                INSERT INTO tb.defects(id, worksite_id, worksite_node_id, title, payload, created_by)
                VALUES (%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (payload.id, worksite_id, payload.worksite_node_id, payload.title, Jsonb(payload.payload), user["user_id"]),
            )
        row = dict(cur.fetchone())
        emit_change(cur, company_id=context["company_id"], worksite_id=worksite_id, entity_type="DEFECT", entity_id=row["id"], operation="CREATE", entity_version=row["version"], actor_user_id=user["user_id"])
        audit(cur, action="DEFECT_CREATE", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=worksite_id, entity_type="DEFECT", entity_id=row["id"])
        finish_mutation(cur, user=user, context=mutation, receipt_type="DEFECT_CREATE", entity_id=row["id"], result_version=row["version"], response_payload=row)
    return row


@router.get("/defects/{defect_id}")
def get_defect(defect_id: UUID, user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _load_defect(cur, defect_id)
        worksite_context(cur, user, row["worksite_id"])
    return row


@router.patch("/defects/{defect_id}")
def update_defect(
    defect_id: UUID,
    payload: DefectUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    updates = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="DEFECT_UPDATE")
        if receipt:
            return replay_payload(receipt)
        defect = _load_defect(cur, defect_id)
        context = worksite_context(cur, user, defect["worksite_id"])
        require_permission(context, "defect.edit")
        if defect["status"] == "CLOSED":
            raise HTTPException(409, "DEFECT_CLOSED")
        if "worksite_node_id" in updates:
            _validate_node(cur, defect["worksite_id"], updates["worksite_node_id"])

        columns = []
        values = []
        for key in ("worksite_node_id", "title", "payload", "status"):
            if key in updates:
                columns.append(f"{key} = %s")
                values.append(Jsonb(updates[key]) if key == "payload" else updates[key])
        columns.extend(["version = version + 1", "updated_at = now()"])
        values.extend([defect_id, payload.base_version])
        cur.execute(f"UPDATE tb.defects SET {', '.join(columns)} WHERE id = %s AND version = %s AND deleted_at IS NULL RETURNING *", tuple(values))
        row = cur.fetchone()
        if not row:
            _conflict_or_missing(cur, defect_id)
        row = dict(row)
        emit_change(cur, company_id=context["company_id"], worksite_id=defect["worksite_id"], entity_type="DEFECT", entity_id=defect_id, operation="UPDATE", entity_version=row["version"], actor_user_id=user["user_id"])
        finish_mutation(cur, user=user, context=mutation, receipt_type="DEFECT_UPDATE", entity_id=defect_id, result_version=row["version"], response_payload=row)
    return row


@router.post("/defects/{defect_id}/close")
def close_defect(
    defect_id: UUID,
    payload: VersionAction,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="DEFECT_CLOSE")
        if receipt:
            return replay_payload(receipt)
        defect = _load_defect(cur, defect_id)
        context = worksite_context(cur, user, defect["worksite_id"])
        require_permission(context, "defect.close")
        if defect["status"] == "CLOSED":
            row = defect
        else:
            cur.execute(
                """
                UPDATE tb.defects
                SET status = 'CLOSED', closed_by = %s, closed_at = now(),
                    version = version + 1, updated_at = now()
                WHERE id = %s AND version = %s AND deleted_at IS NULL
                RETURNING *
                """,
                (user["user_id"], defect_id, payload.base_version),
            )
            updated = cur.fetchone()
            if not updated:
                _conflict_or_missing(cur, defect_id)
            row = dict(updated)
            emit_change(cur, company_id=context["company_id"], worksite_id=defect["worksite_id"], entity_type="DEFECT", entity_id=defect_id, operation="CLOSE", entity_version=row["version"], actor_user_id=user["user_id"])
            audit(cur, action="DEFECT_CLOSE", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=defect["worksite_id"], entity_type="DEFECT", entity_id=defect_id)
        finish_mutation(cur, user=user, context=mutation, receipt_type="DEFECT_CLOSE", entity_id=defect_id, result_version=row["version"], response_payload=row)
    return row
