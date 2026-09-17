from __future__ import annotations

from dataclasses import dataclass
from typing import Any
from uuid import UUID

from fastapi import HTTPException, Request
from fastapi.encoders import jsonable_encoder
from psycopg.types.json import Jsonb


_HEADER = "X-Client-Change-Id"
_MAX_ID_LENGTH = 200


@dataclass(frozen=True)
class MutationContext:
    client_change_id: str | None


def mutation_context(request: Request) -> MutationContext:
    raw = request.headers.get(_HEADER)
    if raw is None:
        return MutationContext(client_change_id=None)
    value = raw.strip()
    if not value or len(value) > _MAX_ID_LENGTH:
        raise HTTPException(400, "INVALID_CLIENT_CHANGE_ID")
    return MutationContext(client_change_id=value)


def require_client_change_id(context: MutationContext) -> str:
    if context.client_change_id is None:
        raise HTTPException(400, "CLIENT_CHANGE_ID_REQUIRED")
    return context.client_change_id


def begin_mutation(
    cur,
    *,
    user: dict[str, Any],
    context: MutationContext,
    receipt_type: str,
) -> dict[str, Any] | None:
    change_id = context.client_change_id
    if change_id is None:
        return None

    lock_key = f"{user['user_id']}:{change_id}"
    cur.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))", (lock_key,))
    cur.execute(
        """
        SELECT id, device_id, user_id, client_change_id, entity_type, entity_id,
               result_version, response_payload, created_at
        FROM tb.mutation_receipts
        WHERE user_id = %s AND client_change_id = %s
        """,
        (user["user_id"], change_id),
    )
    row = cur.fetchone()
    if not row:
        return None
    if row["entity_type"] != receipt_type:
        raise HTTPException(
            409,
            {
                "code": "CLIENT_CHANGE_ID_REUSED",
                "existing_type": row["entity_type"],
                "requested_type": receipt_type,
            },
        )
    return dict(row)


def finish_mutation(
    cur,
    *,
    user: dict[str, Any],
    context: MutationContext,
    receipt_type: str,
    entity_id: UUID | str | None,
    result_version: int | None,
    response_payload: Any,
) -> None:
    change_id = context.client_change_id
    if change_id is None:
        return
    cur.execute(
        """
        INSERT INTO tb.mutation_receipts(
            device_id, user_id, client_change_id, entity_type, entity_id,
            result_version, response_payload
        ) VALUES (%s,%s,%s,%s,%s,%s,%s)
        """,
        (
            user.get("device_id"),
            user["user_id"],
            change_id,
            receipt_type,
            entity_id,
            result_version,
            Jsonb(jsonable_encoder(response_payload)),
        ),
    )


def replay_payload(receipt: dict[str, Any]) -> Any:
    return receipt.get("response_payload")
