from __future__ import annotations

from typing import Any
from uuid import UUID

from psycopg.types.json import Jsonb


# One transaction-level lock across every feed producer/API process. It must be
# acquired BEFORE bigserial allocates an ID and retained until commit/rollback.
# A later visible ID can then never overtake an earlier uncommitted feed row.
CHANGE_FEED_PUBLISH_LOCK = 607178392341001


def emit_change(
    cur,
    *,
    company_id: UUID | str,
    worksite_id: UUID | str | None,
    entity_type: str,
    entity_id: UUID | str,
    operation: str,
    entity_version: int | None,
    actor_user_id: UUID | str | None,
) -> int:
    if getattr(getattr(cur, "connection", None), "autocommit", False):
        raise RuntimeError("change_feed publication requires an explicit transaction")
    cur.execute("SELECT pg_advisory_xact_lock(%s)", (CHANGE_FEED_PUBLISH_LOCK,))
    cur.execute(
        """
        INSERT INTO tb.change_feed(
            company_id, worksite_id, entity_type, entity_id, operation,
            entity_version, actor_user_id
        ) VALUES (%s,%s,%s,%s,%s,%s,%s)
        RETURNING change_id
        """,
        (
            company_id,
            worksite_id,
            entity_type,
            entity_id,
            operation,
            entity_version,
            actor_user_id,
        ),
    )
    return int(cur.fetchone()["change_id"])


def audit(
    cur,
    *,
    action: str,
    actor_user_id: UUID | str | None,
    actor_device_id: UUID | str | None = None,
    company_id: UUID | str | None = None,
    worksite_id: UUID | str | None = None,
    entity_type: str | None = None,
    entity_id: UUID | str | None = None,
    details: dict[str, Any] | None = None,
) -> None:
    cur.execute(
        """
        INSERT INTO tb.audit_log(
            company_id, worksite_id, actor_user_id, actor_device_id,
            action, entity_type, entity_id, details
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
        """,
        (
            company_id,
            worksite_id,
            actor_user_id,
            actor_device_id,
            action,
            entity_type,
            entity_id,
            Jsonb(details or {}),
        ),
    )

