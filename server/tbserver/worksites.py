from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .invitation_links import invite_app_uri, invite_browser_link, invite_qr_svg_data_url
from .permissions import normalized_area_scope, require_permission, worksite_context
from .portal import current_portal_user, require_csrf
from .security import hash_token, random_token


router = APIRouter(prefix="/api/v1/worksites", tags=["worksites"])


class WorksiteCreate(BaseModel):
    name: str = Field(min_length=1, max_length=240)
    designation: str | None = Field(default=None, max_length=300)
    route_number: str | None = Field(default=None, max_length=40)
    station_code: str | None = Field(default=None, max_length=40)
    station_name: str | None = Field(default=None, max_length=240)
    company_id: UUID | None = None


class NodeCreate(BaseModel):
    parent_id: UUID | None = None
    node_kind: str = Field(min_length=1, max_length=60)
    name: str = Field(min_length=1, max_length=300)
    route_number: str | None = Field(default=None, max_length=40)
    track_key: str | None = Field(default=None, max_length=200)
    external_key: str | None = Field(default=None, max_length=300)
    sort_order: int = 0
    payload: dict = Field(default_factory=dict)


class NodeUpdate(BaseModel):
    parent_id: UUID | None = None
    name: str | None = Field(default=None, min_length=1, max_length=300)
    route_number: str | None = Field(default=None, max_length=40)
    track_key: str | None = Field(default=None, max_length=200)
    sort_order: int | None = None
    payload: dict | None = None


class HelperTeamTransfer(BaseModel):
    team_id: UUID


class MemberPermissionsUpdate(BaseModel):
    permission_profile: str
    permissions_override: dict = Field(default_factory=dict)
    area_scope: dict | None = None


class WorksiteInvitationCreate(BaseModel):
    invited_user_id: UUID
    worksite_role: str = Field(default="HELPER")
    permission_profile: str = Field(default="HELPER_STANDARD")
    permissions_override: dict = Field(default_factory=dict)
    area_scope: dict = Field(default_factory=dict)
    expires_days: int = Field(default=7, ge=1, le=30)


class WorksiteInvitationRedeem(BaseModel):
    token: str = Field(min_length=24, max_length=500)


def _company_for_create(cur, user: dict[str, Any], requested: UUID | None) -> UUID:
    if user["company_role"] == "ADMIN":
        if requested is None:
            raise HTTPException(400, "COMPANY_REQUIRED")
        company_id = requested
    else:
        if user["company_id"] is None:
            raise HTTPException(403, "COMPANY_REQUIRED")
        if requested is not None and requested != user["company_id"]:
            raise HTTPException(403, "COMPANY_MISMATCH")
        company_id = user["company_id"]
    cur.execute(
        "SELECT id FROM tb.companies WHERE id = %s AND status = 'ACTIVE' AND deleted_at IS NULL",
        (company_id,),
    )
    if not cur.fetchone():
        raise HTTPException(404, "COMPANY_NOT_FOUND")
    return company_id


@router.get("")
def list_worksites(user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        if user["company_role"] == "ADMIN":
            cur.execute(
                """
                SELECT w.id, w.company_id, c.name AS company_name, w.name, w.designation,
                       w.route_number, w.station_code, w.station_name, w.status, w.updated_at
                FROM tb.worksites w JOIN tb.companies c ON c.id = w.company_id
                WHERE w.deleted_at IS NULL
                ORDER BY w.updated_at DESC
                """
            )
        elif user["company_role"] == "CHEF":
            cur.execute(
                """
                SELECT w.id, w.company_id, c.name AS company_name, w.name, w.designation,
                       w.route_number, w.station_code, w.station_name, w.status, w.updated_at
                FROM tb.worksites w JOIN tb.companies c ON c.id = w.company_id
                WHERE w.company_id = %s AND w.deleted_at IS NULL
                ORDER BY w.updated_at DESC
                """,
                (user["company_id"],),
            )
        else:
            cur.execute(
                """
                SELECT w.id, w.company_id, c.name AS company_name, w.name, w.designation,
                       w.route_number, w.station_code, w.station_name, w.status, w.updated_at
                FROM tb.worksites w
                JOIN tb.companies c ON c.id = w.company_id
                JOIN tb.worksite_memberships m ON m.worksite_id = w.id
                WHERE m.user_id = %s AND m.status = 'ACTIVE' AND w.deleted_at IS NULL
                ORDER BY w.updated_at DESC
                """,
                (user["user_id"],),
            )
        rows = cur.fetchall()
    return {"worksites": rows}


@router.post("", status_code=201)
def create_worksite(
    payload: WorksiteCreate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="WORKSITE_CREATE")
        if receipt:
            return replay_payload(receipt)
        company_id = _company_for_create(cur, user, payload.company_id)
        cur.execute(
            """
            INSERT INTO tb.worksites(
                company_id, name, designation, route_number, station_code, station_name, created_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s)
            RETURNING id, company_id, name, designation, route_number, station_code, station_name,
                      status, version, created_at
            """,
            (
                company_id,
                payload.name.strip(),
                payload.designation,
                payload.route_number,
                payload.station_code,
                payload.station_name,
                user["user_id"],
            ),
        )
        worksite = dict(cur.fetchone())
        if user["company_role"] != "ADMIN":
            cur.execute(
                """
                INSERT INTO tb.worksite_memberships(
                    worksite_id, user_id, worksite_role, permission_profile, area_scope, invited_by
                ) VALUES (%s,%s,'RESPONSIBLE_TB','RESPONSIBLE_TB','{"all":true}'::jsonb,%s)
                """,
                (worksite["id"], user["user_id"], user["user_id"]),
            )
            cur.execute(
                """
                INSERT INTO tb.teams(worksite_id, lead_user_id, name)
                VALUES (%s,%s,%s)
                RETURNING id
                """,
                (worksite["id"], user["user_id"], user["display_name"]),
            )
            team = cur.fetchone()
            cur.execute(
                "INSERT INTO tb.team_members(team_id, user_id, role) VALUES (%s,%s,'LEAD')",
                (team["id"], user["user_id"]),
            )
        emit_change(
            cur,
            company_id=company_id,
            worksite_id=worksite["id"],
            entity_type="WORKSITE",
            entity_id=worksite["id"],
            operation="CREATE",
            entity_version=worksite["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_CREATE",
            actor_user_id=user["user_id"],
            company_id=company_id,
            worksite_id=worksite["id"],
            entity_type="WORKSITE",
            entity_id=worksite["id"],
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_CREATE",
            entity_id=worksite["id"],
            result_version=worksite["version"],
            response_payload=worksite,
        )
    return worksite


@router.get("/{worksite_id}")
def get_worksite(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT w.id, w.company_id, c.name AS company_name, w.name, w.designation,
                   w.route_number, w.station_code, w.station_name, w.status, w.version,
                   w.created_at, w.updated_at
            FROM tb.worksites w JOIN tb.companies c ON c.id = w.company_id
            WHERE w.id = %s
            """,
            (worksite_id,),
        )
        row = cur.fetchone()
    return {"worksite": row, "permissions": context["effective_permissions"]}


@router.get("/{worksite_id}/nodes")
def list_nodes(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT id, parent_id, node_kind, name, route_number, track_key, external_key,
                   sort_order, payload, version, created_at, updated_at
            FROM tb.worksite_nodes
            WHERE worksite_id = %s AND deleted_at IS NULL
            ORDER BY parent_id NULLS FIRST, sort_order, lower(name)
            """,
            (worksite_id,),
        )
        rows = cur.fetchall()
    return {"nodes": rows}


@router.post("/{worksite_id}/nodes", status_code=201)
def create_node(
    worksite_id: UUID,
    payload: NodeCreate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="WORKSITE_NODE_CREATE")
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "worksite.structure.edit")
        if payload.parent_id is not None:
            cur.execute(
                "SELECT id FROM tb.worksite_nodes WHERE id = %s AND worksite_id = %s AND deleted_at IS NULL",
                (payload.parent_id, worksite_id),
            )
            if not cur.fetchone():
                raise HTTPException(400, "PARENT_NODE_INVALID")
        cur.execute(
            """
            INSERT INTO tb.worksite_nodes(
                worksite_id, parent_id, node_kind, name, route_number, track_key,
                external_key, sort_order, payload, created_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            RETURNING id, parent_id, node_kind, name, route_number, track_key, external_key,
                      sort_order, payload, version, created_at, updated_at
            """,
            (
                worksite_id,
                payload.parent_id,
                payload.node_kind,
                payload.name.strip(),
                payload.route_number,
                payload.track_key,
                payload.external_key,
                payload.sort_order,
                payload.payload,
                user["user_id"],
            ),
        )
        row = dict(cur.fetchone())
        cur.execute("UPDATE tb.worksites SET version = version + 1, updated_at = now() WHERE id = %s", (worksite_id,))
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_NODE",
            entity_id=row["id"],
            operation="CREATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_NODE_CREATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_NODE",
            entity_id=row["id"],
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_NODE_CREATE",
            entity_id=row["id"],
            result_version=row["version"],
            response_payload=row,
        )
    return row


@router.patch("/{worksite_id}/nodes/{node_id}")
def update_node(
    worksite_id: UUID,
    node_id: UUID,
    payload: NodeUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    updates = payload.model_dump(exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="WORKSITE_NODE_UPDATE")
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "worksite.structure.edit")
        if "parent_id" in updates and updates["parent_id"] is not None:
            if updates["parent_id"] == node_id:
                raise HTTPException(400, "NODE_CYCLE")
            cur.execute(
                "SELECT id FROM tb.worksite_nodes WHERE id = %s AND worksite_id = %s AND deleted_at IS NULL",
                (updates["parent_id"], worksite_id),
            )
            if not cur.fetchone():
                raise HTTPException(400, "PARENT_NODE_INVALID")
        columns = []
        values = []
        for key in ("parent_id", "name", "route_number", "track_key", "sort_order", "payload"):
            if key in updates:
                columns.append(f"{key} = %s")
                values.append(updates[key])
        columns.extend(["version = version + 1", "updated_at = now()"])
        values.extend([node_id, worksite_id])
        cur.execute(
            f"UPDATE tb.worksite_nodes SET {', '.join(columns)} WHERE id = %s AND worksite_id = %s AND deleted_at IS NULL RETURNING *",
            tuple(values),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "NODE_NOT_FOUND")
        row = dict(row)
        cur.execute("UPDATE tb.worksites SET version = version + 1, updated_at = now() WHERE id = %s", (worksite_id,))
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_NODE",
            entity_id=node_id,
            operation="UPDATE",
            entity_version=row["version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_NODE_UPDATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_NODE",
            entity_id=node_id,
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_NODE_UPDATE",
            entity_id=node_id,
            result_version=row["version"],
            response_payload=row,
        )
    return row


@router.delete("/{worksite_id}/nodes/{node_id}", status_code=204)
def delete_node(
    worksite_id: UUID,
    node_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
):
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="WORKSITE_NODE_DELETE")
        if receipt:
            return None
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "worksite.structure.edit")
        cur.execute(
            "UPDATE tb.worksite_nodes SET deleted_at = now(), version = version + 1, updated_at = now() WHERE id = %s AND worksite_id = %s AND deleted_at IS NULL RETURNING version",
            (node_id, worksite_id),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "NODE_NOT_FOUND")
        version = row["version"]
        cur.execute("UPDATE tb.worksites SET version = version + 1, updated_at = now() WHERE id = %s", (worksite_id,))
        cur.execute(
            """
            INSERT INTO tb.deletion_tombstones(company_id, worksite_id, entity_type, entity_id, deleted_by)
            VALUES (%s,%s,'WORKSITE_NODE',%s,%s)
            ON CONFLICT (entity_type, entity_id) DO NOTHING
            """,
            (context["company_id"], worksite_id, node_id, user["user_id"]),
        )
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_NODE",
            entity_id=node_id,
            operation="DELETE",
            entity_version=version,
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_NODE_DELETE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_NODE",
            entity_id=node_id,
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_NODE_DELETE",
            entity_id=node_id,
            result_version=version,
            response_payload={"deleted": True},
        )
    return None


@router.get("/{worksite_id}/members")
def list_members(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT m.id, m.user_id, u.display_name, u.personal_email, u.personal_phone,
                   m.worksite_role, m.permission_profile, m.permissions_override,
                   m.permissions_version, m.area_scope, m.status, m.joined_at,
                   current_team.team_id, current_team.team_name,
                   current_team.lead_user_id, current_team.lead_name
            FROM tb.worksite_memberships m
            JOIN tb.users u ON u.id = m.user_id
            LEFT JOIN LATERAL (
                SELECT t.id AS team_id,
                       coalesce(t.name, lead.display_name) AS team_name,
                       t.lead_user_id,
                       lead.display_name AS lead_name
                FROM tb.team_members tm
                JOIN tb.teams t
                  ON t.id = tm.team_id
                 AND t.worksite_id = m.worksite_id
                 AND t.status = 'ACTIVE'
                JOIN tb.users lead ON lead.id = t.lead_user_id
                WHERE tm.user_id = m.user_id
                  AND tm.removed_at IS NULL
                ORDER BY CASE WHEN tm.role = 'LEAD' THEN 0 ELSE 1 END,
                         tm.created_at DESC
                LIMIT 1
            ) AS current_team ON true
            WHERE m.worksite_id = %s AND m.status = 'ACTIVE' AND u.deleted_at IS NULL
            ORDER BY lower(u.display_name)
            """,
            (worksite_id,),
        )
        rows = cur.fetchall()
    return {"members": rows}


@router.post("/{worksite_id}/members/{member_user_id}/team")
def transfer_helper_team(
    worksite_id: UUID,
    member_user_id: UUID,
    payload: HelperTeamTransfer,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_HELPER_TEAM_TRANSFER",
        )
        if receipt:
            return replay_payload(receipt)

        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "members.manage")

        cur.execute(
            """
            SELECT id, user_id, worksite_role, permissions_version
            FROM tb.worksite_memberships
            WHERE worksite_id = %s
              AND user_id = %s
              AND status = 'ACTIVE'
            FOR UPDATE
            """,
            (worksite_id, member_user_id),
        )
        membership = cur.fetchone()
        if not membership:
            raise HTTPException(404, "MEMBERSHIP_NOT_FOUND")
        membership = dict(membership)
        if membership["worksite_role"] != "HELPER":
            raise HTTPException(409, "TEAM_TRANSFER_HELPER_ONLY")

        cur.execute(
            """
            SELECT t.id, t.lead_user_id,
                   coalesce(t.name, lead.display_name) AS team_name,
                   lead.display_name AS lead_name
            FROM tb.teams t
            JOIN tb.users lead ON lead.id = t.lead_user_id
            WHERE t.id = %s
              AND t.worksite_id = %s
              AND t.status = 'ACTIVE'
            """,
            (payload.team_id, worksite_id),
        )
        target_team = cur.fetchone()
        if not target_team:
            raise HTTPException(400, "TEAM_INVALID")
        target_team = dict(target_team)

        cur.execute(
            """
            SELECT t.id AS team_id
            FROM tb.team_members tm
            JOIN tb.teams t ON t.id = tm.team_id
            WHERE t.worksite_id = %s
              AND tm.user_id = %s
              AND tm.removed_at IS NULL
              AND tm.role = 'HELPER'
            ORDER BY tm.created_at DESC
            LIMIT 1
            """,
            (worksite_id, member_user_id),
        )
        current_team = cur.fetchone()
        if current_team and current_team["team_id"] == payload.team_id:
            response = {
                "changed": False,
                "member_user_id": member_user_id,
                "team_id": target_team["id"],
                "team_name": target_team["team_name"],
                "lead_user_id": target_team["lead_user_id"],
                "lead_name": target_team["lead_name"],
                "membership_version": membership["permissions_version"],
            }
            finish_mutation(
                cur,
                user=user,
                context=mutation,
                receipt_type="WORKSITE_HELPER_TEAM_TRANSFER",
                entity_id=membership["id"],
                result_version=membership["permissions_version"],
                response_payload=response,
            )
            return response

        cur.execute(
            """
            UPDATE tb.team_members tm
            SET removed_at = now()
            FROM tb.teams t
            WHERE tm.team_id = t.id
              AND t.worksite_id = %s
              AND tm.user_id = %s
              AND tm.removed_at IS NULL
            """,
            (worksite_id, member_user_id),
        )
        cur.execute(
            """
            INSERT INTO tb.team_members(team_id, user_id, role)
            VALUES (%s,%s,'HELPER')
            ON CONFLICT (team_id, user_id)
            DO UPDATE SET role = 'HELPER', removed_at = NULL
            """,
            (payload.team_id, member_user_id),
        )
        cur.execute(
            """
            UPDATE tb.worksite_memberships
            SET permissions_version = permissions_version + 1,
                updated_at = now()
            WHERE id = %s
            RETURNING permissions_version
            """,
            (membership["id"],),
        )
        version = int(cur.fetchone()["permissions_version"])

        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_MEMBERSHIP",
            entity_id=membership["id"],
            operation="UPDATE",
            entity_version=version,
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_HELPER_TEAM_TRANSFER",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_MEMBERSHIP",
            entity_id=membership["id"],
            details={
                "member_user_id": str(member_user_id),
                "target_team_id": str(target_team["id"]),
                "target_lead_user_id": str(target_team["lead_user_id"]),
            },
        )
        response = {
            "changed": True,
            "member_user_id": member_user_id,
            "team_id": target_team["id"],
            "team_name": target_team["team_name"],
            "lead_user_id": target_team["lead_user_id"],
            "lead_name": target_team["lead_name"],
            "membership_version": version,
        }
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_HELPER_TEAM_TRANSFER",
            entity_id=membership["id"],
            result_version=version,
            response_payload=response,
        )
    return response


@router.patch("/{worksite_id}/members/{member_user_id}/permissions")
def update_member_permissions(
    worksite_id: UUID,
    member_user_id: UUID,
    payload: MemberPermissionsUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="WORKSITE_MEMBER_PERMISSIONS_UPDATE")
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "members.manage")
        cur.execute("SELECT profile_key FROM tb.permission_profiles WHERE profile_key = %s", (payload.permission_profile,))
        if not cur.fetchone():
            raise HTTPException(400, "PERMISSION_PROFILE_INVALID")
        if payload.area_scope is None:
            cur.execute(
                """
                UPDATE tb.worksite_memberships
                SET permission_profile = %s, permissions_override = %s,
                    permissions_version = permissions_version + 1, updated_at = now()
                WHERE worksite_id = %s AND user_id = %s AND status = 'ACTIVE'
                RETURNING *
                """,
                (payload.permission_profile, payload.permissions_override, worksite_id, member_user_id),
            )
        else:
            cur.execute(
                """
                UPDATE tb.worksite_memberships
                SET permission_profile = %s, permissions_override = %s, area_scope = %s,
                    permissions_version = permissions_version + 1, updated_at = now()
                WHERE worksite_id = %s AND user_id = %s AND status = 'ACTIVE'
                RETURNING *
                """,
                (payload.permission_profile, payload.permissions_override, payload.area_scope, worksite_id, member_user_id),
            )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "MEMBERSHIP_NOT_FOUND")
        row = dict(row)
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_MEMBERSHIP",
            entity_id=row["id"],
            operation="UPDATE",
            entity_version=row["permissions_version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_MEMBER_PERMISSIONS_UPDATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_MEMBERSHIP",
            entity_id=row["id"],
            details={"member_user_id": str(member_user_id)},
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_MEMBER_PERMISSIONS_UPDATE",
            entity_id=row["id"],
            result_version=row["permissions_version"],
            response_payload=row,
        )
    return row


@router.get("/{worksite_id}/teams")
def list_teams(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT t.id, t.lead_user_id, u.display_name AS lead_name, t.name, t.active_btb_id,
                   t.status, count(tm.user_id) FILTER (WHERE tm.removed_at IS NULL) AS member_count
            FROM tb.teams t
            JOIN tb.users u ON u.id = t.lead_user_id
            LEFT JOIN tb.team_members tm ON tm.team_id = t.id
            WHERE t.worksite_id = %s AND t.status = 'ACTIVE'
            GROUP BY t.id, u.display_name
            ORDER BY lower(coalesce(t.name, u.display_name))
            """,
            (worksite_id,),
        )
        rows = cur.fetchall()
    return {"teams": rows}


@router.get("/{worksite_id}/invite-candidates")
def list_invite_candidates(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "members.manage")
        cur.execute(
            """
            SELECT u.id, u.display_name, u.personal_email, u.personal_phone, u.company_role
            FROM tb.users u
            WHERE u.company_id = %s
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM tb.worksite_memberships m
                  WHERE m.worksite_id = %s
                    AND m.user_id = u.id
                    AND m.status = 'ACTIVE'
              )
            ORDER BY lower(u.display_name), u.id
            """,
            (context["company_id"], worksite_id),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {"candidates": rows}


@router.get("/{worksite_id}/invitations")
def list_worksite_invitations(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "members.manage")
        cur.execute(
            """
            SELECT i.id, i.invited_user_id, u.display_name AS invited_name,
                   i.worksite_role, i.permission_profile, i.permissions_override,
                   i.area_scope, i.invited_by, inviter.display_name AS invited_by_name,
                   i.created_at, i.expires_at, i.accepted_at, i.revoked_at,
                   CASE
                     WHEN i.accepted_at IS NOT NULL THEN 'ACCEPTED'
                     WHEN i.revoked_at IS NOT NULL THEN 'REVOKED'
                     WHEN i.expires_at <= now() THEN 'EXPIRED'
                     ELSE 'OPEN'
                   END AS status
            FROM tb.worksite_invitations i
            JOIN tb.users u ON u.id = i.invited_user_id
            JOIN tb.users inviter ON inviter.id = i.invited_by
            WHERE i.worksite_id = %s
            ORDER BY i.created_at DESC
            LIMIT 200
            """,
            (worksite_id,),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {"invitations": rows}


@router.post("/{worksite_id}/invitations", status_code=201)
def create_worksite_invitation(
    worksite_id: UUID,
    payload: WorksiteInvitationCreate,
    request: Request,
    response: Response,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    response.headers["Cache-Control"] = "no-store"
    role = payload.worksite_role.strip().upper()
    if role not in {"RESPONSIBLE_TB", "TB", "HELPER", "READ_ONLY"}:
        raise HTTPException(400, "INVALID_WORKSITE_ROLE")
    invitation_scope = payload.area_scope
    normalized_scope = normalized_area_scope(
        invitation_scope,
        empty_means_all=role in {"RESPONSIBLE_TB", "TB"},
    )
    if role in {"HELPER", "READ_ONLY"} and normalized_scope == {"all": False}:
        raise HTTPException(400, "AREA_SCOPE_REQUIRED")
    if not invitation_scope and normalized_scope.get("all") is True:
        invitation_scope = {"all": True}
    raw_token = random_token(32)
    token_hash = hash_token(raw_token)
    expires_at = datetime.now(timezone.utc) + timedelta(days=payload.expires_days)

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_INVITATION_CREATE",
        )
        if receipt:
            replay = replay_payload(receipt) or {}
            return {
                **replay,
                "invite_token": None,
                "invite_uri": None,
                "invite_link": None,
                "invite_qr_svg_data_url": None,
                "token_replay_unavailable": True,
            }
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "members.manage")

        cur.execute(
            """
            SELECT id, company_id, display_name, status, deleted_at
            FROM tb.users
            WHERE id = %s
            """,
            (payload.invited_user_id,),
        )
        target = cur.fetchone()
        if (
            not target
            or target["deleted_at"] is not None
            or target["status"] != "ACTIVE"
            or target["company_id"] != context["company_id"]
        ):
            raise HTTPException(400, "INVITEE_COMPANY_MISMATCH")

        cur.execute(
            """
            SELECT 1
            FROM tb.worksite_memberships
            WHERE worksite_id = %s AND user_id = %s AND status = 'ACTIVE'
            """,
            (worksite_id, payload.invited_user_id),
        )
        if cur.fetchone():
            raise HTTPException(409, "ALREADY_WORKSITE_MEMBER")

        cur.execute(
            "SELECT profile_key FROM tb.permission_profiles WHERE profile_key = %s",
            (payload.permission_profile,),
        )
        if not cur.fetchone():
            raise HTTPException(400, "PERMISSION_PROFILE_INVALID")

        if role == "HELPER":
            cur.execute(
                """
                SELECT id
                FROM tb.teams
                WHERE worksite_id = %s
                  AND lead_user_id = %s
                  AND status = 'ACTIVE'
                """,
                (worksite_id, user["user_id"]),
            )
            if not cur.fetchone():
                raise HTTPException(409, "HELPER_INVITER_TEAM_REQUIRED")

        cur.execute(
            """
            INSERT INTO tb.worksite_invitations(
                worksite_id, invited_user_id, token_hash, worksite_role,
                permission_profile, permissions_override, area_scope,
                invited_by, expires_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
            RETURNING id, worksite_id, invited_user_id, worksite_role,
                      permission_profile, area_scope, created_at, expires_at
            """,
            (
                worksite_id,
                payload.invited_user_id,
                token_hash,
                role,
                payload.permission_profile,
                payload.permissions_override,
                invitation_scope,
                user["user_id"],
                expires_at,
            ),
        )
        row = dict(cur.fetchone())
        audit(
            cur,
            action="WORKSITE_INVITATION_CREATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_INVITATION",
            entity_id=row["id"],
            details={
                "invited_user_id": str(payload.invited_user_id),
                "worksite_role": role,
                "expires_at": expires_at.isoformat(),
            },
        )
        receipt_response = {
            **row,
            "invite_token": None,
            "token_replay_unavailable": True,
        }
        response = {
            **row,
            "invite_token": raw_token,
            "token_replay_unavailable": False,
        }
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_INVITATION_CREATE",
            entity_id=row["id"],
            result_version=None,
            response_payload=receipt_response,
        )
    redeem_url = str(request.url_for("portal_invitation_redeem_page"))
    return {
        **response,
        "invite_uri": invite_app_uri(raw_token),
        "invite_link": invite_browser_link(redeem_url, raw_token),
        "invite_qr_svg_data_url": invite_qr_svg_data_url(raw_token),
    }


@router.post("/{worksite_id}/invitations/{invitation_id}/revoke")
def revoke_worksite_invitation(
    worksite_id: UUID,
    invitation_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_INVITATION_REVOKE",
        )
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "members.manage")
        cur.execute(
            """
            SELECT id, accepted_at, revoked_at
            FROM tb.worksite_invitations
            WHERE id = %s AND worksite_id = %s
            FOR UPDATE
            """,
            (invitation_id, worksite_id),
        )
        invitation = cur.fetchone()
        if not invitation:
            raise HTTPException(404, "WORKSITE_INVITATION_NOT_FOUND")
        if invitation["accepted_at"] is not None:
            raise HTTPException(409, "WORKSITE_INVITATION_ALREADY_ACCEPTED")
        if invitation["revoked_at"] is None:
            cur.execute(
                "UPDATE tb.worksite_invitations SET revoked_at = now() WHERE id = %s",
                (invitation_id,),
            )
        response = {"id": invitation_id, "status": "REVOKED"}
        audit(
            cur,
            action="WORKSITE_INVITATION_REVOKE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE_INVITATION",
            entity_id=invitation_id,
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_INVITATION_REVOKE",
            entity_id=invitation_id,
            result_version=None,
            response_payload=response,
        )
    return response


@router.post("/invitations/redeem")
def redeem_worksite_invitation(
    payload: WorksiteInvitationRedeem,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    token_hash = hash_token(payload.token.strip())
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_INVITATION_REDEEM",
        )
        if receipt:
            return replay_payload(receipt)
        cur.execute(
            """
            SELECT i.*, w.company_id, w.status AS worksite_status, w.deleted_at AS worksite_deleted_at
            FROM tb.worksite_invitations i
            JOIN tb.worksites w ON w.id = i.worksite_id
            WHERE i.token_hash = %s
            FOR UPDATE OF i
            """,
            (token_hash,),
        )
        invitation = cur.fetchone()
        if not invitation:
            raise HTTPException(400, "WORKSITE_INVITATION_INVALID")
        invitation = dict(invitation)
        if invitation["revoked_at"] is not None:
            raise HTTPException(409, "WORKSITE_INVITATION_REVOKED")
        if invitation["accepted_at"] is not None:
            raise HTTPException(409, "WORKSITE_INVITATION_ALREADY_ACCEPTED")
        if invitation["expires_at"] <= datetime.now(timezone.utc):
            raise HTTPException(410, "WORKSITE_INVITATION_EXPIRED")
        if invitation["worksite_deleted_at"] is not None or invitation["worksite_status"] != "ACTIVE":
            raise HTTPException(409, "WORKSITE_NOT_ACTIVE")
        if invitation["invited_user_id"] != user["user_id"]:
            raise HTTPException(403, "WORKSITE_INVITATION_WRONG_USER")
        if invitation["company_id"] != user["company_id"]:
            raise HTTPException(403, "WORKSITE_INVITATION_COMPANY_MISMATCH")

        cur.execute(
            """
            INSERT INTO tb.worksite_memberships(
                worksite_id, user_id, worksite_role, permission_profile,
                permissions_override, area_scope, invited_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s)
            ON CONFLICT (worksite_id, user_id) DO UPDATE
            SET worksite_role = excluded.worksite_role,
                permission_profile = excluded.permission_profile,
                permissions_override = excluded.permissions_override,
                area_scope = excluded.area_scope,
                permissions_version = tb.worksite_memberships.permissions_version + 1,
                status = 'ACTIVE',
                removed_at = NULL,
                updated_at = now()
            RETURNING *
            """,
            (
                invitation["worksite_id"],
                user["user_id"],
                invitation["worksite_role"],
                invitation["permission_profile"],
                invitation["permissions_override"],
                invitation["area_scope"],
                invitation["invited_by"],
            ),
        )
        membership = dict(cur.fetchone())
        role = invitation["worksite_role"]
        team_id = None

        if role in {"RESPONSIBLE_TB", "TB"}:
            cur.execute(
                """
                INSERT INTO tb.teams(worksite_id, lead_user_id, name)
                VALUES (%s,%s,%s)
                ON CONFLICT (worksite_id, lead_user_id) DO UPDATE
                SET status = 'ACTIVE', updated_at = now()
                RETURNING id
                """,
                (
                    invitation["worksite_id"],
                    user["user_id"],
                    user["display_name"],
                ),
            )
            team_id = cur.fetchone()["id"]
            cur.execute(
                """
                INSERT INTO tb.team_members(team_id, user_id, role)
                VALUES (%s,%s,'LEAD')
                ON CONFLICT (team_id, user_id)
                DO UPDATE SET role = 'LEAD', removed_at = NULL
                """,
                (team_id, user["user_id"]),
            )
        elif role == "HELPER":
            cur.execute(
                """
                SELECT id
                FROM tb.teams
                WHERE worksite_id = %s
                  AND lead_user_id = %s
                  AND status = 'ACTIVE'
                """,
                (invitation["worksite_id"], invitation["invited_by"]),
            )
            team = cur.fetchone()
            if not team:
                raise HTTPException(409, "WORKSITE_INVITATION_TEAM_NO_LONGER_AVAILABLE")
            team_id = team["id"]
            cur.execute(
                """
                UPDATE tb.team_members tm
                SET removed_at = now()
                FROM tb.teams t
                WHERE tm.team_id = t.id
                  AND t.worksite_id = %s
                  AND tm.user_id = %s
                  AND tm.removed_at IS NULL
                """,
                (invitation["worksite_id"], user["user_id"]),
            )
            cur.execute(
                """
                INSERT INTO tb.team_members(team_id, user_id, role)
                VALUES (%s,%s,'HELPER')
                ON CONFLICT (team_id, user_id)
                DO UPDATE SET role = 'HELPER', removed_at = NULL
                """,
                (team_id, user["user_id"]),
            )

        cur.execute(
            "UPDATE tb.worksite_invitations SET accepted_at = now() WHERE id = %s",
            (invitation["id"],),
        )
        emit_change(
            cur,
            company_id=invitation["company_id"],
            worksite_id=invitation["worksite_id"],
            entity_type="WORKSITE_MEMBERSHIP",
            entity_id=membership["id"],
            operation="UPSERT",
            entity_version=membership["permissions_version"],
            actor_user_id=user["user_id"],
        )
        audit(
            cur,
            action="WORKSITE_INVITATION_REDEEM",
            actor_user_id=user["user_id"],
            company_id=invitation["company_id"],
            worksite_id=invitation["worksite_id"],
            entity_type="WORKSITE_INVITATION",
            entity_id=invitation["id"],
            details={
                "worksite_role": role,
                "team_id": str(team_id) if team_id else None,
            },
        )
        response = {
            "worksite_id": invitation["worksite_id"],
            "membership": membership,
            "team_id": team_id,
        }
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="WORKSITE_INVITATION_REDEEM",
            entity_id=membership["id"],
            result_version=membership["permissions_version"],
            response_payload=response,
        )
    return response

