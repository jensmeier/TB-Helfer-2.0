from __future__ import annotations

import asyncio
import json
import time
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse

from .database import connect, transaction
from .permissions import worksite_context
from .portal import current_portal_user


router = APIRouter(prefix="/api/v1/sync", tags=["sync"])
_MAX_WAIT_SECONDS = 30.0
_HUB_POLL_SECONDS = 1.0


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


def _latest_change_id() -> int:
    with connect() as conn, conn.cursor() as cur:
        cur.execute("SELECT coalesce(max(change_id), 0) AS change_id FROM tb.change_feed")
        row = cur.fetchone()
        return int(row["change_id"] if row else 0)


class _ChangeHub:
    """One lightweight DB poller per API process, shared by all long-poll/SSE clients."""

    def __init__(self) -> None:
        self._head = 0
        self._condition = asyncio.Condition()
        self._task: asyncio.Task | None = None
        self._start_lock = asyncio.Lock()

    async def _ensure_started(self) -> None:
        if self._task is not None and not self._task.done():
            return
        async with self._start_lock:
            if self._task is not None and not self._task.done():
                return
            self._head = await asyncio.to_thread(_latest_change_id)
            self._task = asyncio.create_task(self._poll_loop())

    async def _poll_loop(self) -> None:
        while True:
            try:
                latest = await asyncio.to_thread(_latest_change_id)
                if latest > self._head:
                    async with self._condition:
                        self._head = latest
                        self._condition.notify_all()
                await asyncio.sleep(_HUB_POLL_SECONDS)
            except asyncio.CancelledError:
                raise
            except Exception:
                # A temporary DB outage must not kill the notifier task.
                await asyncio.sleep(2.0)

    async def head(self) -> int:
        await self._ensure_started()
        return self._head

    async def wait_for_head(self, after: int, timeout_seconds: float) -> int:
        await self._ensure_started()
        if self._head > after:
            return self._head
        deadline = time.monotonic() + max(0.0, timeout_seconds)
        async with self._condition:
            while self._head <= after:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    break
                try:
                    await asyncio.wait_for(self._condition.wait(), timeout=remaining)
                except asyncio.TimeoutError:
                    break
        return self._head


_CHANGE_HUB = _ChangeHub()


def _entity_types(raw: str | None) -> list[str]:
    if not raw:
        return []
    out: list[str] = []
    for value in raw.split(","):
        normalized = value.strip().upper()
        if not normalized or normalized in out:
            continue
        if len(normalized) > 80 or len(out) >= 32:
            continue
        out.append(normalized)
    return out


def _type_clause(column: str, entity_types: list[str], params: list[Any]) -> str:
    if not entity_types:
        return ""
    params.extend(entity_types)
    placeholders = ",".join(["%s"] * len(entity_types))
    return f" AND {column} IN ({placeholders})"


def _visible_changes(
    user: dict[str, Any],
    *,
    after: int,
    through: int | None,
    limit: int,
    worksite_id: UUID | None,
    entity_types: list[str] | None = None,
) -> list[dict]:
    entity_types = entity_types or []
    with transaction() as conn, conn.cursor() as cur:
        through_clause = ""
        base_params: list[Any] = []
        if through is not None:
            through_clause = " AND change_id <= %s"
            base_params.append(through)

        if worksite_id is not None:
            worksite_context(cur, user, worksite_id)
            params: list[Any] = [worksite_id, after, *base_params]
            type_clause = _type_clause("entity_type", entity_types, params)
            params.append(limit)
            cur.execute(
                f"""
                SELECT change_id, company_id, worksite_id, entity_type, entity_id, operation,
                       entity_version, actor_user_id, created_at
                FROM tb.change_feed
                WHERE worksite_id = %s AND change_id > %s
                  {through_clause}
                  {type_clause}
                ORDER BY change_id
                LIMIT %s
                """,
                tuple(params),
            )
        elif user["company_role"] == "CHEF":
            params = [user["company_id"], after, *base_params]
            type_clause = _type_clause("entity_type", entity_types, params)
            params.append(limit)
            cur.execute(
                f"""
                SELECT change_id, company_id, worksite_id, entity_type, entity_id, operation,
                       entity_version, actor_user_id, created_at
                FROM tb.change_feed
                WHERE company_id = %s AND change_id > %s
                  {through_clause}
                  {type_clause}
                ORDER BY change_id
                LIMIT %s
                """,
                tuple(params),
            )
        elif user["company_role"] == "USER":
            params = [user["company_id"], after, *base_params, user["user_id"]]
            type_clause = _type_clause("f.entity_type", entity_types, params)
            params.append(limit)
            cur.execute(
                f"""
                SELECT f.change_id, f.company_id, f.worksite_id, f.entity_type, f.entity_id,
                       f.operation, f.entity_version, f.actor_user_id, f.created_at
                FROM tb.change_feed f
                WHERE f.company_id = %s AND f.change_id > %s
                  {through_clause.replace("change_id", "f.change_id")}
                  {type_clause}
                  AND (
                    f.worksite_id IS NULL OR EXISTS (
                      SELECT 1 FROM tb.worksite_memberships m
                      WHERE m.worksite_id = f.worksite_id AND m.user_id = %s AND m.status = 'ACTIVE'
                    )
                  )
                ORDER BY f.change_id
                LIMIT %s
                """,
                tuple(params),
            )
        else:
            return []
        return [_serialize_change(dict(row)) for row in cur.fetchall()]


def _changes_for_worksite(worksite_id: UUID, after: int, through: int, limit: int) -> list[dict]:
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT change_id, company_id, worksite_id, entity_type, entity_id, operation,
                   entity_version, actor_user_id, created_at
            FROM tb.change_feed
            WHERE worksite_id = %s
              AND change_id > %s
              AND change_id <= %s
            ORDER BY change_id
            LIMIT %s
            """,
            (worksite_id, after, through, limit),
        )
        return [_serialize_change(dict(row)) for row in cur.fetchall()]


@router.get("/changes")
def changes(
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=200, ge=1, le=1000),
    worksite_id: UUID | None = None,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    rows = _visible_changes(
        user,
        after=after,
        through=None,
        limit=limit,
        worksite_id=worksite_id,
    )
    return {"changes": rows, "next_after": rows[-1]["change_id"] if rows else after}


@router.get("/changes/wait")
async def wait_changes(
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=200, ge=1, le=1000),
    worksite_id: UUID | None = None,
    entity_types: str | None = Query(default=None, max_length=1000),
    wait_seconds: float = Query(default=25.0, ge=0.0, le=_MAX_WAIT_SECONDS),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    """Efficient delta long-poll for mobile clients.

    The global head is observed before the scoped query, so next_after may safely advance
    over changes that are irrelevant to the caller without skipping a visible change.
    """

    types = _entity_types(entity_types)
    observed_head = await _CHANGE_HUB.head()
    if observed_head <= after and wait_seconds > 0:
        observed_head = await _CHANGE_HUB.wait_for_head(after, wait_seconds)

    rows = await asyncio.to_thread(
        _visible_changes,
        user,
        after=after,
        through=observed_head,
        limit=limit + 1,
        worksite_id=worksite_id,
        entity_types=types,
    )
    has_more = len(rows) > limit
    visible = rows[:limit]
    next_after = visible[-1]["change_id"] if has_more and visible else observed_head
    if not has_more:
        next_after = max(after, observed_head)
    return {
        "changes": visible,
        "next_after": next_after,
        "head": observed_head,
        "caught_up": not has_more,
    }


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
        while True:
            head = await _CHANGE_HUB.wait_for_head(cursor, 15.0)
            if head <= cursor:
                yield ": keepalive\n\n"
                continue
            rows = await asyncio.to_thread(_changes_for_worksite, worksite_id, cursor, head, 200)
            if rows:
                for row in rows:
                    cursor = row["change_id"]
                    payload = json.dumps(row, ensure_ascii=False, separators=(",", ":"))
                    yield f"id: {cursor}\nevent: change\ndata: {payload}\n\n"
                if len(rows) < 200:
                    cursor = max(cursor, head)
            else:
                cursor = head
                yield ": keepalive\n\n"

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
