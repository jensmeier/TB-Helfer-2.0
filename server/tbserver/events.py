from __future__ import annotations

from typing import Any
from uuid import UUID


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
    company_id: UUID | str | None = None,
    worksite_id: UUID | str | None = None,
    entity_type: str | None = None,
    entity_id: UUID | str | None = None,
    details: dict[str, Any] | None = None,
) -> None:
    cur.execute(
        """
        INSERT INTO tb.audit_log(
            company_id, worksite_id, actor_user_id, action, entity_type, entity_id, details
        ) VALUES (%s,%s,%s,%s,%s,%s,%s)
        """,
        (
            company_id,
            worksite_id,
            actor_user_id,
            action,
            entity_type,
            entity_id,
            details or {},
        ),
    )
