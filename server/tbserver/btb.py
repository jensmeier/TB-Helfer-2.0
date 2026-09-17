from __future__ import annotations

import hashlib
import json
from datetime import date, datetime
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .permissions import require_permission, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["btb"])


class BtbCreate(BaseModel):
    team_id: UUID
    work_date: date | None = None
    started_at: datetime | None = None
    ended_at: datetime | None = None
    header: dict = Field(default_factory=dict)


class BtbHeaderUpdate(BaseModel):
    base_version: int = Field(ge=1)
    work_date: date | None = None
    started_at: datetime | None = None
    ended_at: datetime | None = None
    header: dict | None = None


class BtbEntryCreate(BaseModel):
    id: UUID | None = None
    section: str = Field(min_length=1, max_length=80)
    sort_order: int = 0
    payload: dict = Field(default_factory=dict)


class BtbEntryUpdate(BaseModel):
    base_version: int = Field(ge=1)
    sort_order: int | None = None
    payload: dict | None = None


def _team(cur, worksite_id: UUID, team_id: UUID) -> dict:
    cur.execute(
        "SELECT id, worksite_id, lead_user_id, active_btb_id, status FROM tb.teams WHERE id = %s AND worksite_id = %s",
        (team_id, worksite_id),
    )
    row = cur.fetchone()
    if not row or row["status"] != "ACTIVE":
        raise HTTPException(404, "TEAM_NOT_FOUND")
    return dict(row)


def _assert_team_member(cur, user: dict[str, Any], team_id: UUID) -> None:
    if user["company_role"] in {"ADMIN", "CHEF"}:
        return
    cur.execute(
        "SELECT 1 FROM tb.team_members WHERE team_id = %s AND user_id = %s AND removed_at IS NULL",
        (team_id, user["user_id"]),
    )
    if not cur.fetchone():
        raise HTTPException(403, "TEAM_ACCESS_DENIED")


def _load_btb(cur, btb_id: UUID) -> dict:
    cur.execute(
        """
        SELECT b.*, w.company_id
        FROM tb.btbs b JOIN tb.worksites w ON w.id = b.worksite_id
        WHERE b.id = %s
        """,
        (btb_id,),
    )
    row = cur.fetchone()
    if not row or row["status"] == "DELETED":
        raise HTTPException(404, "BTB_NOT_FOUND")
    return dict(row)


def _snapshot(cur, btb: dict) -> dict:
    cur.execute(
        """
        SELECT id, section, sort_order, payload, version, created_at, updated_at
        FROM tb.btb_entries
        WHERE btb_id = %s AND deleted_at IS NULL
        ORDER BY section, sort_order, id
        """,
        (btb["id"],),
    )
    entries = [dict(row) for row in cur.fetchall()]
    return {
        "btb": {
            "id": str(btb["id"]),
            "worksite_id": str(btb["worksite_id"]),
            "team_id": str(btb["team_id"]),
            "work_date": btb["work_date"].isoformat() if btb["work_date"] else None,
            "started_at": btb["started_at"].isoformat() if btb["started_at"] else None,
            "ended_at": btb["ended_at"].isoformat() if btb["ended_at"] else None,
            "header": btb["header"],
        },
        "entries": [
            {
                "id": str(row["id"]),
                "section": row["section"],
                "sort_order": row["sort_order"],
                "payload": row["payload"],
                "version": row["version"],
            }
            for row in entries
        ],
    }


@router.get("/worksites/{worksite_id}/teams/{team_id}/btbs")
def list_btbs(
    worksite_id: UUID,
    team_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "btb.view")
        _team(cur, worksite_id, team_id)
        _assert_team_member(cur, user, team_id)
        cur.execute(
            """
            SELECT id, status, work_date, started_at, ended_at, version,
                   first_finalized_at, finalized_at, created_at, updated_at
            FROM tb.btbs
            WHERE worksite_id = %s AND team_id = %s AND status <> 'DELETED'
            ORDER BY coalesce(work_date, created_at::date) DESC, created_at DESC
            """,
            (worksite_id, team_id),
        )
        rows = cur.fetchall()
        cur.execute("SELECT active_btb_id FROM tb.teams WHERE id = %s", (team_id,))
        active = cur.fetchone()["active_btb_id"]
    return {"active_btb_id": active, "btbs": rows}


@router.post("/worksites/{worksite_id}/btbs", status_code=201)
def create_btb(
    worksite_id: UUID,
    payload: BtbCreate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "btb.create")
        team = _team(cur, worksite_id, payload.team_id)
        _assert_team_member(cur, user, payload.team_id)
        if team["active_btb_id"] is not None:
            raise HTTPException(409, "TEAM_ALREADY_HAS_ACTIVE_BTB")
        cur.execute(
            """
            INSERT INTO tb.btbs(worksite_id, team_id, work_date, started_at, ended_at, header, created_by)
            VALUES (%s,%s,%s,%s,%s,%s,%s)
            RETURNING *
            """,
            (
                worksite_id,
                payload.team_id,
                payload.work_date,
                payload.started_at,
                payload.ended_at,
                Jsonb(payload.header),
                user["user_id"],
            ),
        )
        row = cur.fetchone()
        cur.execute("UPDATE tb.teams SET active_btb_id = %s, updated_at = now() WHERE id = %s", (row["id"], payload.team_id))
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="BTB",
            entity_id=row["id"],
            operation="CREATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="BTB_CREATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="BTB",
            entity_id=row["id"],
        )
    return row


@router.get("/btbs/{btb_id}")
def get_btb(
    btb_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.view")
        _assert_team_member(cur, user, btb["team_id"])
        cur.execute(
            """
            SELECT id, section, sort_order, payload, version, created_by, updated_by, created_at, updated_at
            FROM tb.btb_entries
            WHERE btb_id = %s AND deleted_at IS NULL
            ORDER BY section, sort_order, created_at
            """,
            (btb_id,),
        )
        entries = cur.fetchall()
    return {"btb": btb, "entries": entries}


@router.patch("/btbs/{btb_id}/header")
def update_btb_header(
    btb_id: UUID,
    payload: BtbHeaderUpdate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    updates = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        if btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_EDITABLE")
        columns = []
        values = []
        for key in ("work_date", "started_at", "ended_at", "header"):
            if key in updates:
                columns.append(f"{key} = %s")
                values.append(Jsonb(updates[key]) if key == "header" else updates[key])
        columns.extend(["version = version + 1", "updated_at = now()"])
        values.extend([btb_id, payload.base_version])
        cur.execute(
            f"UPDATE tb.btbs SET {', '.join(columns)} WHERE id = %s AND version = %s RETURNING *",
            tuple(values),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": btb["version"]})
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="UPDATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
    return row


@router.post("/btbs/{btb_id}/entries", status_code=201)
def create_entry(
    btb_id: UUID,
    payload: BtbEntryCreate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        if btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_EDITABLE")
        if payload.id is None:
            cur.execute(
                """
                INSERT INTO tb.btb_entries(btb_id, section, sort_order, payload, created_by, updated_by)
                VALUES (%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (btb_id, payload.section, payload.sort_order, Jsonb(payload.payload), user["user_id"], user["user_id"]),
            )
        else:
            cur.execute(
                """
                INSERT INTO tb.btb_entries(id, btb_id, section, sort_order, payload, created_by, updated_by)
                VALUES (%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (payload.id, btb_id, payload.section, payload.sort_order, Jsonb(payload.payload), user["user_id"], user["user_id"]),
            )
        row = cur.fetchone()
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        parent_version = cur.fetchone()["version"]
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB_ENTRY",
            entity_id=row["id"],
            operation="CREATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="UPDATE",
            entity_version=parent_version,
            actor_user_id=user["user_id"],
        )
    return row


@router.patch("/btbs/{btb_id}/entries/{entry_id}")
def update_entry(
    btb_id: UUID,
    entry_id: UUID,
    payload: BtbEntryUpdate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    updates = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        if btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_EDITABLE")
        columns = []
        values = []
        for key in ("sort_order", "payload"):
            if key in updates:
                columns.append(f"{key} = %s")
                values.append(Jsonb(updates[key]) if key == "payload" else updates[key])
        columns.extend(["version = version + 1", "updated_by = %s", "updated_at = now()"])
        values.extend([user["user_id"], entry_id, btb_id, payload.base_version])
        cur.execute(
            f"UPDATE tb.btb_entries SET {', '.join(columns)} WHERE id = %s AND btb_id = %s AND version = %s AND deleted_at IS NULL RETURNING *",
            tuple(values),
        )
        row = cur.fetchone()
        if not row:
            cur.execute("SELECT version FROM tb.btb_entries WHERE id = %s AND btb_id = %s", (entry_id, btb_id))
            current = cur.fetchone()
            raise HTTPException(409 if current else 404, {"code": "VERSION_CONFLICT" if current else "ENTRY_NOT_FOUND", "current_version": current["version"] if current else None})
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        parent_version = cur.fetchone()["version"]
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB_ENTRY",
            entity_id=entry_id,
            operation="UPDATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="UPDATE",
            entity_version=parent_version,
            actor_user_id=user["user_id"],
        )
    return row


@router.delete("/btbs/{btb_id}/entries/{entry_id}", status_code=204)
def delete_entry(
    btb_id: UUID,
    entry_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
):
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        if btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_EDITABLE")
        cur.execute(
            "UPDATE tb.btb_entries SET deleted_at = now(), version = version + 1, updated_by = %s, updated_at = now() WHERE id = %s AND btb_id = %s AND deleted_at IS NULL RETURNING version",
            (user["user_id"], entry_id, btb_id),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "ENTRY_NOT_FOUND")
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        parent_version = cur.fetchone()["version"]
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB_ENTRY",
            entity_id=entry_id,
            operation="DELETE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="UPDATE",
            entity_version=parent_version,
            actor_user_id=user["user_id"],
        )
    return None


@router.post("/btbs/{btb_id}/finalize")
def finalize_btb(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.finalize")
        _assert_team_member(cur, user, btb["team_id"])
        if btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_FINALIZABLE")
        snapshot = _snapshot(cur, btb)
        canonical = json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        digest = hashlib.sha256(canonical).hexdigest()
        cur.execute("SELECT btb_id FROM tb.btb_original_snapshots WHERE btb_id = %s", (btb_id,))
        if not cur.fetchone():
            cur.execute(
                "INSERT INTO tb.btb_original_snapshots(btb_id, snapshot, snapshot_sha256, created_by) VALUES (%s,%s,%s,%s)",
                (btb_id, Jsonb(snapshot), digest, user["user_id"]),
            )
        cur.execute(
            """
            UPDATE tb.btbs
            SET status = 'FINALIZED',
                first_finalized_at = coalesce(first_finalized_at, now()),
                finalized_at = now(), version = version + 1, updated_at = now()
            WHERE id = %s
            RETURNING *
            """,
            (btb_id,),
        )
        row = cur.fetchone()
        cur.execute("UPDATE tb.teams SET active_btb_id = NULL, updated_at = now() WHERE id = %s AND active_btb_id = %s", (btb["team_id"], btb_id))
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="FINALIZE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="BTB_FINALIZE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            details={"snapshot_sha256": digest},
        )
    return row


@router.post("/btbs/{btb_id}/reopen")
def reopen_btb(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        if btb["status"] != "FINALIZED":
            raise HTTPException(409, "BTB_NOT_FINALIZED")
        team = _team(cur, btb["worksite_id"], btb["team_id"])
        if team["active_btb_id"] is not None and team["active_btb_id"] != btb_id:
            raise HTTPException(409, "TEAM_ALREADY_HAS_ACTIVE_BTB")
        cur.execute("UPDATE tb.btbs SET status = 'REWORK', version = version + 1, updated_at = now() WHERE id = %s RETURNING *", (btb_id,))
        row = cur.fetchone()
        cur.execute("UPDATE tb.teams SET active_btb_id = %s, updated_at = now() WHERE id = %s", (btb_id, btb["team_id"]))
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="REOPEN",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="BTB_REOPEN",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
        )
    return row


@router.delete("/btbs/{btb_id}", status_code=204)
def delete_btb(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
):
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.delete")
        _assert_team_member(cur, user, btb["team_id"])
        cur.execute("UPDATE tb.btbs SET status = 'DELETED', deleted_at = now(), version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        version = cur.fetchone()["version"]
        cur.execute("UPDATE tb.teams SET active_btb_id = NULL, updated_at = now() WHERE id = %s AND active_btb_id = %s", (btb["team_id"], btb_id))
        cur.execute(
            """
            INSERT INTO tb.deletion_tombstones(company_id, worksite_id, entity_type, entity_id, deleted_by)
            VALUES (%s,%s,'BTB',%s,%s)
            ON CONFLICT (entity_type, entity_id) DO NOTHING
            """,
            (context["company_id"], btb["worksite_id"], btb_id, user["user_id"]),
        )
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
            operation="DELETE",
            entity_version=version,
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="BTB_DELETE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="BTB",
            entity_id=btb_id,
        )
    return None
