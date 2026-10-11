from __future__ import annotations

from typing import Any
from uuid import UUID

from fastapi import HTTPException

from .permissions import is_team_lead


WORKING = "WORKING"
FINISHED = "FINISHED"
NOT_PARTICIPATING = "NOT_PARTICIPATING"
ALLOWED_STATUSES = {WORKING, FINISHED, NOT_PARTICIPATING}


def ensure_participants(cur, *, btb_id: UUID, team_id: UUID) -> None:
    """Ensure the current active team is represented in the BTB shift state."""
    cur.execute(
        """
        INSERT INTO tb.btb_participant_states(btb_id, user_id)
        SELECT %s, tm.user_id
        FROM tb.team_members tm
        WHERE tm.team_id = %s
          AND tm.removed_at IS NULL
          AND EXISTS (SELECT 1 FROM tb.btbs b WHERE b.id = %s AND b.status IN ('ACTIVE','REWORK'))
        ON CONFLICT (btb_id, user_id) DO NOTHING
        """,
        (btb_id, team_id, btb_id),
    )
    # Defensive: the lead should normally also be in team_members, but the BTB
    # contract must not depend on that historical invariant.
    cur.execute(
        """
        INSERT INTO tb.btb_participant_states(btb_id, user_id)
        SELECT %s, t.lead_user_id
        FROM tb.teams t
        WHERE t.id = %s
          AND t.status = 'ACTIVE'
          AND EXISTS (SELECT 1 FROM tb.btbs b WHERE b.id = %s AND b.status IN ('ACTIVE','REWORK'))
        ON CONFLICT (btb_id, user_id) DO NOTHING
        """,
        (btb_id, team_id, btb_id),
    )


def participant_rows(cur, *, btb_id: UUID, team_id: UUID) -> list[dict[str, Any]]:
    ensure_participants(cur, btb_id=btb_id, team_id=team_id)
    cur.execute(
        """
        SELECT p.user_id, u.display_name, p.status, p.version, p.finished_at,
               p.created_at, p.updated_at,
               (t.lead_user_id = p.user_id) AS is_team_lead
        FROM tb.btb_participant_states p
        JOIN tb.users u ON u.id = p.user_id
        JOIN tb.teams t ON t.id = %s
        WHERE p.btb_id = %s
        ORDER BY
          CASE WHEN t.lead_user_id = p.user_id THEN 0 ELSE 1 END,
          lower(coalesce(u.display_name, '')),
          p.user_id
        """,
        (team_id, btb_id),
    )
    return [dict(row) for row in cur.fetchall()]


def participant_status(
    cur,
    *,
    btb_id: UUID,
    team_id: UUID,
    user_id: UUID,
    for_update: bool = False,
) -> dict[str, Any] | None:
    ensure_participants(cur, btb_id=btb_id, team_id=team_id)
    suffix = " FOR UPDATE" if for_update else ""
    cur.execute(
        f"""
        SELECT btb_id, user_id, status, version, finished_at, created_at, updated_at
        FROM tb.btb_participant_states
        WHERE btb_id = %s AND user_id = %s{suffix}
        """,
        (btb_id, user_id),
    )
    row = cur.fetchone()
    return dict(row) if row else None


def require_participant_writable(
    cur,
    *,
    user: dict[str, Any],
    btb_id: UUID,
    team_id: UUID,
) -> None:
    if is_team_lead(cur, user, team_id):
        return
    row = participant_status(
        cur,
        btb_id=btb_id,
        team_id=team_id,
        user_id=user["user_id"],
        for_update=True,
    )
    if row is None:
        raise HTTPException(403, "TEAM_ACCESS_DENIED")
    if row["status"] == FINISHED:
        raise HTTPException(409, "BTB_PARTICIPANT_FINISHED")
    if row["status"] == NOT_PARTICIPATING:
        raise HTTPException(409, "BTB_PARTICIPANT_NOT_ACTIVE")


def set_own_status(
    cur,
    *,
    btb_id: UUID,
    team_id: UUID,
    user_id: UUID,
    status: str,
) -> dict[str, Any]:
    if status not in {WORKING, FINISHED}:
        raise ValueError("unsupported own participant status")
    current = participant_status(
        cur, btb_id=btb_id, team_id=team_id, user_id=user_id, for_update=True
    )
    if current is None:
        raise HTTPException(403, "TEAM_ACCESS_DENIED")
    if current["status"] == NOT_PARTICIPATING:
        raise HTTPException(409, "BTB_PARTICIPANT_NOT_ACTIVE")
    cur.execute(
        """
        UPDATE tb.btb_participant_states
        SET status = %s,
            version = version + 1,
            finished_at = CASE WHEN %s = 'FINISHED' THEN now() ELSE NULL END,
            updated_by = %s,
            updated_at = now()
        WHERE btb_id = %s AND user_id = %s
        RETURNING *
        """,
        (status, status, user_id, btb_id, user_id),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(403, "TEAM_ACCESS_DENIED")
    return dict(row)


def set_lead_managed_status(
    cur,
    *,
    btb_id: UUID,
    team_id: UUID,
    target_user_id: UUID,
    actor_user_id: UUID,
    status: str,
) -> dict[str, Any]:
    if status not in {WORKING, NOT_PARTICIPATING}:
        raise HTTPException(400, "BTB_PARTICIPANT_STATUS_NOT_ALLOWED")
    ensure_participants(cur, btb_id=btb_id, team_id=team_id)
    cur.execute(
        """
        UPDATE tb.btb_participant_states
        SET status = %s,
            version = version + 1,
            finished_at = NULL,
            updated_by = %s,
            updated_at = now()
        WHERE btb_id = %s AND user_id = %s
        RETURNING *
        """,
        (status, actor_user_id, btb_id, target_user_id),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "BTB_PARTICIPANT_NOT_FOUND")
    return dict(row)


def mark_lead_finished(
    cur,
    *,
    btb_id: UUID,
    team_id: UUID,
    lead_user_id: UUID,
) -> None:
    ensure_participants(cur, btb_id=btb_id, team_id=team_id)
    cur.execute(
        """
        UPDATE tb.btb_participant_states
        SET status = 'FINISHED',
            version = version + 1,
            finished_at = coalesce(finished_at, now()),
            updated_by = %s,
            updated_at = now()
        WHERE btb_id = %s
          AND user_id = %s
          AND status <> 'FINISHED'
        """,
        (lead_user_id, btb_id, lead_user_id),
    )


def unfinished_participants(
    cur,
    *,
    btb_id: UUID,
    team_id: UUID,
    exclude_user_id: UUID | None = None,
) -> list[dict[str, Any]]:
    rows = participant_rows(cur, btb_id=btb_id, team_id=team_id)
    return [
        row
        for row in rows
        if row["status"] == WORKING
        and (exclude_user_id is None or row["user_id"] != exclude_user_id)
    ]

