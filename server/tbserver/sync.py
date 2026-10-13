from __future__ import annotations

import asyncio
import json
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse

from .database import connect, transaction
from .permissions import worksite_context
from .portal import current_portal_user


router = APIRouter(prefix="/api/v1/sync", tags=["sync"])


def _serialize_change(row: dict) -> dict:
    return {
        "change_id": int(row["change_id"]),
        "company_id": str(row["company_id"]),
        "worksite_id": str(row["worksite_id"]) if row["worksite_id"] else None,
        "entity_type": row["entity_type"],
        "entity_id": str(row["entity_id"]),
        "operation": row["operation"],
        "entity_version": row["entity_version"],
        "actor_user_id": str(row["actor_user_id"]) if row["actor_user_id"] else None,
        "created_at": row["created_at"].isoformat(),
    }


def _changes_for_worksite(worksite_id: UUID, after: int, limit: int) -> list[dict]:
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT change_id, company_id, worksite_id, entity_type, entity_id, operation,
                   entity_version, actor_user_id, created_at
            FROM tb.change_feed
            WHERE worksite_id = %s AND change_id > %s
            ORDER BY change_id
            LIMIT %s
            """,
            (worksite_id, after, limit),
        )
        return [_serialize_change(dict(row)) for row in cur.fetchall()]


@router.get("/changes")
def changes(
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=200, ge=1, le=1000),
    worksite_id: UUID | None = None,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        if worksite_id is not None:
            worksite_context(cur, user, worksite_id)
            cur.execute(
                """
                SELECT change_id, company_id, worksite_id, entity_type, entity_id, operation,
                       entity_version, actor_user_id, created_at
                FROM tb.change_feed
                WHERE worksite_id = %s AND change_id > %s
                ORDER BY change_id LIMIT %s
                """,
                (worksite_id, after, limit),
            )
        elif user["company_role"] == "CHEF":
            cur.execute(
                """
                SELECT change_id, company_id, worksite_id, entity_type, entity_id, operation,
                       entity_version, actor_user_id, created_at
                FROM tb.change_feed
                WHERE company_id = %s AND change_id > %s
                ORDER BY change_id LIMIT %s
                """,
                (user["company_id"], after, limit),
            )
        elif user["company_role"] == "USER":
            cur.execute(
                """
                SELECT f.change_id, f.company_id, f.worksite_id, f.entity_type, f.entity_id,
                       f.operation, f.entity_version, f.actor_user_id, f.created_at
                FROM tb.change_feed f
                WHERE f.company_id = %s AND f.change_id > %s
                  AND (
                    f.worksite_id IS NULL OR EXISTS (
                      SELECT 1 FROM tb.worksite_memberships m
                      WHERE m.worksite_id = f.worksite_id AND m.user_id = %s AND m.status = 'ACTIVE'
                    )
                  )
                ORDER BY f.change_id LIMIT %s
                """,
                (user["company_id"], after, user["user_id"], limit),
            )
        else:
            return {"changes": [], "next_after": after}
        rows = [_serialize_change(dict(row)) for row in cur.fetchall()]
    return {"changes": rows, "next_after": rows[-1]["change_id"] if rows else after}


@router.get("/events")
def live_events(
    worksite_id: UUID,
    after: int = Query(default=0, ge=0),
    user: dict[str, Any] = Depends(current_portal_user),
):
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)

    async def stream():
        cursor = after
        idle_ticks = 0
        while True:
            rows = await asyncio.to_thread(_changes_for_worksite, worksite_id, cursor, 200)
            if rows:
                idle_ticks = 0
                for row in rows:
                    cursor = row["change_id"]
                    payload = json.dumps(row, ensure_ascii=False, separators=(",", ":"))
                    yield f"id: {cursor}\nevent: change\ndata: {payload}\n\n"
            else:
                idle_ticks += 1
                if idle_ticks >= 8:
                    idle_ticks = 0
                    yield ": keepalive\n\n"
            await asyncio.sleep(2)

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
