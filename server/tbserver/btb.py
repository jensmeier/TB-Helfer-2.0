from __future__ import annotations

import hashlib
import json
import logging
import math
from datetime import date, datetime
from time import perf_counter
from typing import Any, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .btb_documents import (
    effective_btb_photo_metadata,
    final_pdf_content_path,
    final_pdf_paths,
    finish_pdf_install,
    hash_file,
    install_pdf_atomically,
    invalidate_final_pdf,
    load_btb_photo_files,
    load_final_pdf,
    load_original_snapshot,
    photo_files_for_render,
    prepare_btb_content_change,
    public_final_pdf,
    remove_storage_keys,
    rollback_pdf_install,
    snapshot_photo_metadata,
    upsert_final_pdf_file,
)
from .btb_entry_photos import entry_photo_context, sync_entry_photos, bind_matching_entry_photos
from .btb_times import inherited_firm_times, preserve_entry_time_fields
from .btb_pdf import PDF_RENDERER_VERSION, render_btb_pdf
from .btb_photo_meta import canonical_photo_metadata, merge_btb_photo_metadata, normalize_btb_photo_override
from .btb_participants import (
    FINISHED,
    NOT_PARTICIPATING,
    WORKING,
    ensure_participants,
    mark_lead_finished,
    participant_rows,
    require_participant_writable,
    set_lead_managed_status,
    set_own_status,
    unfinished_participants,
)
from .company import load_company_branding
from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .image_renditions import image_rendition_urls
from .permissions import is_team_lead, require_permission, require_team_lead, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["btb"])
logger = logging.getLogger("uvicorn.error").getChild("tbserver.btb")


BTB_CONTENT_SECTIONS = frozenset({
    "ARBEIT",
    "ABNAHME",
    "EINWEISUNG_UNTERWEISUNG",
    "BESONDERHEIT",
    "MANGEL_SCHADEN",
})


def _validate_worksite_node_key(cur, worksite_id: UUID, node_key: Any) -> None:
    key = str(node_key or "").strip()
    if not key:
        return
    cur.execute(
        """
        SELECT 1
        FROM tb.worksite_nodes
        WHERE worksite_id = %s
          AND deleted_at IS NULL
          AND coalesce(external_key, 'server-' || id::text) = %s
        """,
        (worksite_id, key),
    )
    if not cur.fetchone():
        raise HTTPException(
            400,
            {"code": "WORKSITE_NODE_INVALID", "worksiteNodeKey": key},
        )


def _validate_entry_payload_node(cur, btb: dict[str, Any], payload: Any) -> None:
    if not isinstance(payload, dict):
        return
    _validate_worksite_node_key(cur, btb["worksite_id"], payload.get("worksiteNodeKey"))


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
    section: str | None = Field(default=None, min_length=1, max_length=80)
    sort_order: int | None = None
    payload: dict | None = None


class BtbPhotoUpdate(BaseModel):
    base_version: int = Field(ge=1)
    photo_metadata: dict[str, Any] = Field(default_factory=dict)


class BtbFinalizeRequest(BaseModel):
    allow_unfinished: bool = False


class BtbParticipantLeadUpdate(BaseModel):
    status: Literal["WORKING", "NOT_PARTICIPATING"]


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


def _load_btb(cur, btb_id: UUID, *, for_update: bool = False) -> dict:
    suffix = " FOR UPDATE" if for_update else ""
    cur.execute(
        f"""
        SELECT b.*, w.company_id
        FROM tb.btbs b JOIN tb.worksites w ON w.id = b.worksite_id
        WHERE b.id = %s{suffix}
        """,
        (btb_id,),
    )
    row = cur.fetchone()
    if not row or row["status"] == "DELETED":
        raise HTTPException(404, "BTB_NOT_FOUND")
    return dict(row)




def _snapshot(cur, btb: dict, *, photo_files: list[dict[str, Any]] | None = None) -> dict:
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
    if photo_files is None:
        photo_files = load_btb_photo_files(cur, btb["id"])
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
        "photos": snapshot_photo_metadata(photo_files),
    }


def _pdf_context(cur, btb: dict[str, Any]) -> dict[str, Any]:
    cur.execute(
        """
        SELECT w.name AS worksite_name, w.designation AS worksite_designation,
               w.route_number, t.name AS team_name,
               lead.display_name AS lead_name,
               lead.personal_email AS lead_email,
               lead.personal_phone AS lead_phone
        FROM tb.worksites w
        JOIN tb.teams t ON t.id = %s AND t.worksite_id = w.id
        LEFT JOIN tb.users lead ON lead.id = t.lead_user_id
        WHERE w.id = %s
        """,
        (btb["team_id"], btb["worksite_id"]),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(409, "BTB_PDF_CONTEXT_MISSING")
    row = dict(row)
    branding = load_company_branding(cur, btb["company_id"])
    cur.execute(
        """
        SELECT id, parent_id, node_kind, name, route_number, track_key,
               external_key, sort_order, payload
        FROM tb.worksite_nodes
        WHERE worksite_id = %s AND deleted_at IS NULL
        ORDER BY parent_id NULLS FIRST, sort_order, lower(name), id
        """,
        (btb["worksite_id"],),
    )
    worksite_nodes = [dict(item) for item in cur.fetchall()]
    address = " ".join(
        part
        for part in (
            branding.get("address_line1"),
            branding.get("address_line2"),
            branding.get("postal_code"),
            branding.get("city"),
        )
        if part
    )
    return {
        "company_name": branding.get("name"),
        "company_address": address or None,
        "company_address_line1": branding.get("address_line1"),
        "company_email": branding.get("email"),
        "company_phone": branding.get("phone"),
        "company_website": branding.get("website"),
        "company_logo_path": branding.get("logo_path"),
        "company_logo_mime_type": branding.get("logo_mime_type"),
        "company_logo_sha256": branding.get("logo_sha256"),
        "pdf_logo_layout": branding.get("pdf_layout"),
        "worksite_name": row.get("worksite_name"),
        "worksite_designation": row.get("worksite_designation"),
        "route_number": row.get("route_number"),
        "team_name": row.get("team_name"),
        "lead_name": row.get("lead_name"),
        "lead_email": row.get("lead_email"),
        "lead_phone": row.get("lead_phone"),
        "worksite_nodes": worksite_nodes,
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
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_CREATE")
        if receipt:
            return replay_payload(receipt)
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
        row = dict(cur.fetchone())
        cur.execute("UPDATE tb.teams SET active_btb_id = %s, updated_at = now() WHERE id = %s", (row["id"], payload.team_id))
        ensure_participants(cur, btb_id=row["id"], team_id=payload.team_id)
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
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BTB_CREATE",
            entity_id=row["id"],
            result_version=row["version"],
            response_payload=row,
        )
    return row


@router.get("/btbs/{btb_id}/sync-state")
def btb_sync_state(
    btb_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    """Authorized tombstone confirmation, including already deleted BTBs.

    A missing detail route or an arbitrary 404 is not proof of deletion.
    Deleted content is never returned; current site/team rights still apply.
    """
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id, worksite_id, team_id, status, version FROM tb.btbs WHERE id = %s",
            (btb_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "BTB_NOT_FOUND")
        context = worksite_context(cur, user, row["worksite_id"])
        require_permission(context, "btb.view")
        _assert_team_member(cur, user, row["team_id"])
        return {"id": str(row["id"]), "version": row["version"], "deleted": row["status"] == "DELETED"}


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
        participants = participant_rows(cur, btb_id=btb_id, team_id=btb["team_id"])
        is_lead = is_team_lead(cur, user, btb["team_id"])
        current_participant = next(
            (row for row in participants if str(row["user_id"]) == str(user["user_id"])),
            None,
        )
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
        photo_files = load_btb_photo_files(cur, btb_id)
        photos = [
            {
                "id": row["id"],
                "original_name": row.get("original_name"),
                "mime_type": row.get("mime_type"),
                "byte_size": row.get("byte_size"),
                "sha256": row.get("sha256"),
                "captured_at": row.get("captured_at"),
                "gps_lat": effective_btb_photo_metadata(row).get("lat"),
                "gps_lon": effective_btb_photo_metadata(row).get("lon"),
                "gps_accuracy_m": row.get("gps_accuracy_m"),
                "photo_metadata": effective_btb_photo_metadata(row),
                "purpose": row.get("purpose"),
                "link_version": row.get("link_version", 1),
                "content_url": f"/api/v1/files/{row['id']}/content",
                "renditions": image_rendition_urls(
                    row["id"],
                    row.get("mime_type"),
                ),
            }
            for row in photo_files
        ]
        pdf_file = load_final_pdf(cur, btb_id) if btb["status"] == "FINALIZED" and is_lead else None
        final_pdf = public_final_pdf(pdf_file, btb_id=btb_id) if pdf_file else None
    return {
        "btb": btb,
        "entries": entries,
        "photos": photos,
        "final_pdf": final_pdf,
        "participants": participants,
        "current_user": {
            "is_team_lead": is_lead,
            "participant_status": current_participant["status"] if current_participant else None,
            "can_view_btb_pdf": is_lead,
            "can_finalize_btb": is_lead,
        },
    }


def _weather_btb(btb_id, user):
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.view")
        _assert_team_member(cur, user, btb["team_id"])
        return dict(btb)


@router.get("/btbs/{btb_id}/weather/locations")
def btb_weather_locations(btb_id: UUID, q: str = Query(min_length=2, max_length=120), user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    _weather_btb(btb_id, user)
    if len(q.strip()) < 2:
        raise HTTPException(400, "Bitte mindestens zwei Zeichen des Ortes eingeben.")
    from .btb_weather import locations
    try:
        return {"locations": locations(q.strip())}
    except Exception:
        raise HTTPException(502, "Ortssuche derzeit nicht verfügbar. Wetter kann manuell erfasst werden.")


@router.get("/btbs/{btb_id}/weather/suggestion")
def btb_weather_suggestion(btb_id: UUID, lat: float = Query(ge=-90, le=90), lon: float = Query(ge=-180, le=180), user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    btb = _weather_btb(btb_id, user)
    if not btb.get("started_at") or not btb.get("ended_at"):
        raise HTTPException(400, "Bitte zuerst Beginn und Ende der Berichtszeit speichern.")
    from .btb_weather import weather_suggestion
    try:
        return weather_suggestion(lat, lon, btb["started_at"], btb["ended_at"])
    except ValueError as error:
        raise HTTPException(400, str(error))
    except Exception:
        raise HTTPException(502, "Wetterabruf derzeit nicht verfügbar. Vorhandene Angaben bleiben erhalten.")


@router.patch("/btbs/{btb_id}/header")
def update_btb_header(
    btb_id: UUID,
    payload: BtbHeaderUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    updates = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    invalidated_pdf_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_HEADER_UPDATE")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_participant_writable(cur, user=user, btb_id=btb_id, team_id=btb["team_id"])
        if btb["version"] != payload.base_version:
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": btb["version"]})
        effective = {key: value for key, value in updates.items() if btb.get(key) != value}
        if not effective:
            raise HTTPException(400, "NO_CHANGES")
        invalidated_pdf_keys.extend(prepare_btb_content_change(cur, btb))
        columns = []
        values = []
        for key in ("work_date", "started_at", "ended_at", "header"):
            if key in effective:
                columns.append(f"{key} = %s")
                values.append(Jsonb(effective[key]) if key == "header" else effective[key])
        columns.extend(["version = version + 1", "updated_at = now()"])
        values.extend([btb_id, payload.base_version])
        cur.execute(
            f"UPDATE tb.btbs SET {', '.join(columns)} WHERE id = %s AND version = %s RETURNING *",
            tuple(values),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": btb["version"]})
        row = dict(row)
        if {"started_at", "ended_at"} & effective.keys():
            cur.execute("SELECT id, payload FROM tb.btb_entries WHERE btb_id=%s AND section='FIRMA' AND deleted_at IS NULL FOR UPDATE", (btb_id,))
            for firm in cur.fetchall():
                previous = firm["payload"] or {}
                if previous.get("isDeleted"):
                    continue
                inherited = inherited_firm_times(previous, btb, row)
                if inherited == previous:
                    continue
                cur.execute("UPDATE tb.btb_entries SET payload=%s, version=version+1, updated_by=%s, updated_at=now() WHERE id=%s RETURNING version", (Jsonb(inherited), user["user_id"], firm["id"]))
                changed = cur.fetchone()
                emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_ENTRY", entity_id=firm["id"], operation="UPDATE", entity_version=changed["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=row["version"], actor_user_id=user["user_id"])
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_HEADER_UPDATE", entity_id=btb_id, result_version=row["version"], response_payload=row)
    remove_storage_keys(invalidated_pdf_keys)
    return row


@router.post("/btbs/{btb_id}/entries", status_code=201)
def create_entry(
    btb_id: UUID,
    payload: BtbEntryCreate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    invalidated_pdf_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_ENTRY_CREATE")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_participant_writable(cur, user=user, btb_id=btb_id, team_id=btb["team_id"])
        _validate_entry_payload_node(cur, btb, payload.payload)
        invalidated_pdf_keys.extend(prepare_btb_content_change(cur, btb))
        values = (btb_id, payload.section, payload.sort_order, Jsonb(payload.payload), user["user_id"], user["user_id"])
        if payload.id is None:
            cur.execute("INSERT INTO tb.btb_entries(btb_id, section, sort_order, payload, created_by, updated_by) VALUES (%s,%s,%s,%s,%s,%s) RETURNING *", values)
            row = dict(cur.fetchone())
        else:
            cur.execute(
                """
                SELECT id, btb_id, section, sort_order, payload, version,
                       created_by, updated_by, created_at, updated_at, deleted_at
                FROM tb.btb_entries
                WHERE id = %s
                FOR UPDATE
                """,
                (payload.id,),
            )
            existing = cur.fetchone()
            if existing is None:
                cur.execute(
                    "INSERT INTO tb.btb_entries(id, btb_id, section, sort_order, payload, created_by, updated_by) VALUES (%s,%s,%s,%s,%s,%s,%s) RETURNING *",
                    (payload.id, *values),
                )
                row = dict(cur.fetchone())
            else:
                existing = dict(existing)
                if existing["btb_id"] != btb_id:
                    raise HTTPException(
                        409,
                        {"code": "ENTRY_ID_CONFLICT", "current_version": existing["version"]},
                    )
                same_content = (
                    existing["section"] == payload.section
                    and existing["sort_order"] == payload.sort_order
                    and existing["payload"] == payload.payload
                )
                if existing["deleted_at"] is None:
                    if not same_content:
                        raise HTTPException(
                            409,
                            {"code": "ENTRY_ID_CONFLICT", "current_version": existing["version"]},
                        )
                    finish_mutation(
                        cur,
                        user=user,
                        context=mutation,
                        receipt_type="BTB_ENTRY_CREATE",
                        entity_id=existing["id"],
                        result_version=existing["version"],
                        response_payload=existing,
                    )
                    return existing
                cur.execute(
                    """
                    UPDATE tb.btb_entries
                    SET section = %s,
                        sort_order = %s,
                        payload = %s,
                        deleted_at = NULL,
                        version = version + 1,
                        updated_by = %s,
                        updated_at = now()
                    WHERE id = %s AND btb_id = %s AND deleted_at IS NOT NULL
                    RETURNING *
                    """,
                    (
                        payload.section,
                        payload.sort_order,
                        Jsonb(payload.payload),
                        user["user_id"],
                        payload.id,
                        btb_id,
                    ),
                )
                row = dict(cur.fetchone())
        for photo in bind_matching_entry_photos(cur, btb_id, user['user_id']):
            emit_change(cur, company_id=context['company_id'], worksite_id=btb['worksite_id'], entity_type='FILE', entity_id=photo['file_id'], operation='UPDATE', entity_version=photo['version'], actor_user_id=user['user_id'])
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        parent_version = cur.fetchone()["version"]
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_ENTRY", entity_id=row["id"], operation="CREATE", entity_version=row["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=parent_version, actor_user_id=user["user_id"])
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_ENTRY_CREATE", entity_id=row["id"], result_version=row["version"], response_payload=row)
    remove_storage_keys(invalidated_pdf_keys)
    return row


@router.patch("/btbs/{btb_id}/entries/{entry_id}")
def update_entry(
    btb_id: UUID,
    entry_id: UUID,
    payload: BtbEntryUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    updates = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    invalidated_pdf_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_ENTRY_UPDATE")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_participant_writable(cur, user=user, btb_id=btb_id, team_id=btb["team_id"])
        cur.execute("SELECT id, section, sort_order, payload, version FROM tb.btb_entries WHERE id = %s AND btb_id = %s AND deleted_at IS NULL FOR UPDATE", (entry_id, btb_id))
        current = cur.fetchone()
        if not current:
            raise HTTPException(404, "ENTRY_NOT_FOUND")
        current = dict(current)
        if current["version"] != payload.base_version:
            raise HTTPException(409, {"code": "VERSION_CONFLICT", "current_version": current["version"]})
        if "section" in updates:
            target_section = str(updates["section"] or "").strip().upper()
            current_section = str(current["section"] or "").strip().upper()
            if not target_section:
                raise HTTPException(400, "ENTRY_SECTION_REQUIRED")
            if target_section != current_section and (
                current_section not in BTB_CONTENT_SECTIONS
                or target_section not in BTB_CONTENT_SECTIONS
            ):
                raise HTTPException(
                    400,
                    {
                        "code": "ENTRY_SECTION_MOVE_NOT_ALLOWED",
                        "current_section": current_section,
                        "target_section": target_section,
                    },
                )
            updates["section"] = target_section
        if "payload" in updates:
            updates["payload"] = preserve_entry_time_fields(current["payload"] or {}, updates["payload"], btb)
        next_payload = updates.get("payload", current["payload"])
        _validate_entry_payload_node(cur, btb, next_payload)
        effective = {key: value for key, value in updates.items() if current.get(key) != value}
        if not effective:
            raise HTTPException(400, "NO_CHANGES")
        invalidated_pdf_keys.extend(prepare_btb_content_change(cur, btb))
        columns = []
        values = []
        for key in ("section", "sort_order", "payload"):
            if key in effective:
                columns.append(f"{key} = %s")
                values.append(Jsonb(effective[key]) if key == "payload" else effective[key])
        columns.extend(["version = version + 1", "updated_by = %s", "updated_at = now()"])
        values.extend([user["user_id"], entry_id, btb_id, payload.base_version])
        cur.execute(f"UPDATE tb.btb_entries SET {', '.join(columns)} WHERE id = %s AND btb_id = %s AND version = %s AND deleted_at IS NULL RETURNING *", tuple(values))
        row = dict(cur.fetchone())
        bound_ids = [entry_id] if row['section'] in BTB_CONTENT_SECTIONS else []
        if row['section'] == 'FIRMA':
            firm_id = (row['payload'] or {}).get('firmaId')
            if firm_id:
                cur.execute("SELECT id FROM tb.btb_entries WHERE btb_id=%s AND deleted_at IS NULL AND section=ANY(%s) AND payload->>'firmaId'=%s", (btb_id, list(BTB_CONTENT_SECTIONS), str(firm_id)))
                bound_ids = [item['id'] for item in cur.fetchall()]
        for bound_id in bound_ids:
            for photo in sync_entry_photos(cur, btb_id, bound_id, entry_photo_context(cur, btb_id, bound_id), user['user_id']):
                emit_change(cur, company_id=context['company_id'], worksite_id=btb['worksite_id'], entity_type='FILE', entity_id=photo['file_id'], operation='UPDATE', entity_version=photo['version'], actor_user_id=user['user_id'])
        for photo in bind_matching_entry_photos(cur, btb_id, user['user_id']):
            emit_change(cur, company_id=context['company_id'], worksite_id=btb['worksite_id'], entity_type='FILE', entity_id=photo['file_id'], operation='UPDATE', entity_version=photo['version'], actor_user_id=user['user_id'])
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        parent_version = cur.fetchone()["version"]
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_ENTRY", entity_id=entry_id, operation="UPDATE", entity_version=row["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=parent_version, actor_user_id=user["user_id"])
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_ENTRY_UPDATE", entity_id=entry_id, result_version=row["version"], response_payload=row)
    remove_storage_keys(invalidated_pdf_keys)
    return row


@router.delete("/btbs/{btb_id}/entries/{entry_id}", status_code=204)
def delete_entry(
    btb_id: UUID,
    entry_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
):
    invalidated_pdf_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_ENTRY_DELETE")
        if receipt:
            return None
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_participant_writable(cur, user=user, btb_id=btb_id, team_id=btb["team_id"])
        cur.execute("SELECT id FROM tb.btb_entries WHERE id = %s AND btb_id = %s AND deleted_at IS NULL FOR UPDATE", (entry_id, btb_id))
        if not cur.fetchone():
            raise HTTPException(404, "ENTRY_NOT_FOUND")
        invalidated_pdf_keys.extend(prepare_btb_content_change(cur, btb))
        cur.execute("UPDATE tb.btb_entries SET deleted_at = now(), version = version + 1, updated_by = %s, updated_at = now() WHERE id = %s AND btb_id = %s AND deleted_at IS NULL RETURNING version", (user["user_id"], entry_id, btb_id))
        row = cur.fetchone()
        for photo in sync_entry_photos(cur, btb_id, entry_id, {'btbEntryId':None}, user['user_id']):
            emit_change(cur, company_id=context['company_id'], worksite_id=btb['worksite_id'], entity_type='FILE', entity_id=photo['file_id'], operation='UPDATE', entity_version=photo['version'], actor_user_id=user['user_id'])
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        parent_version = cur.fetchone()["version"]
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_ENTRY", entity_id=entry_id, operation="DELETE", entity_version=row["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=parent_version, actor_user_id=user["user_id"])
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_ENTRY_DELETE", entity_id=entry_id, result_version=row["version"], response_payload={"deleted": True})
    remove_storage_keys(invalidated_pdf_keys)
    return None


@router.get("/btbs/{btb_id}/participants")
def get_btb_participants(
    btb_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.view")
        _assert_team_member(cur, user, btb["team_id"])
        rows = participant_rows(cur, btb_id=btb_id, team_id=btb["team_id"])
        lead = is_team_lead(cur, user, btb["team_id"])
    return {
        "btb_id": btb_id,
        "is_team_lead": lead,
        "participants": rows,
    }


@router.post("/btbs/{btb_id}/shift-finish")
def finish_own_shift(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_SHIFT_FINISH")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        team = _team(cur, btb["worksite_id"], btb["team_id"])
        if team["active_btb_id"] != btb_id or btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_ACTIVE")
        row = set_own_status(
            cur,
            btb_id=btb_id,
            team_id=btb["team_id"],
            user_id=user["user_id"],
            status=FINISHED,
        )
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_PARTICIPANT", entity_id=user["user_id"], operation="UPDATE", entity_version=row["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=btb["version"], actor_user_id=user["user_id"])
        audit(cur, action="BTB_SHIFT_FINISH", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id)
        response = {"btb_id": btb_id, "participant": row}
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_SHIFT_FINISH", entity_id=btb_id, result_version=row["version"], response_payload=response)
    return response


@router.post("/btbs/{btb_id}/shift-resume")
def resume_own_shift(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_SHIFT_RESUME")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        team = _team(cur, btb["worksite_id"], btb["team_id"])
        if team["active_btb_id"] != btb_id or btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_ACTIVE")
        row = set_own_status(
            cur,
            btb_id=btb_id,
            team_id=btb["team_id"],
            user_id=user["user_id"],
            status=WORKING,
        )
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_PARTICIPANT", entity_id=user["user_id"], operation="UPDATE", entity_version=row["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=btb["version"], actor_user_id=user["user_id"])
        audit(cur, action="BTB_SHIFT_RESUME", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id)
        response = {"btb_id": btb_id, "participant": row}
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_SHIFT_RESUME", entity_id=btb_id, result_version=row["version"], response_payload=response)
    return response


@router.patch("/btbs/{btb_id}/participants/{participant_user_id}")
def update_btb_participant_by_lead(
    btb_id: UUID,
    participant_user_id: UUID,
    payload: BtbParticipantLeadUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_PARTICIPANT_LEAD_UPDATE")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.finalize")
        _assert_team_member(cur, user, btb["team_id"])
        require_team_lead(cur, user, btb["team_id"])
        team = _team(cur, btb["worksite_id"], btb["team_id"])
        if team["active_btb_id"] != btb_id or btb["status"] not in {"ACTIVE", "REWORK"}:
            raise HTTPException(409, "BTB_NOT_ACTIVE")
        if str(participant_user_id) == str(team["lead_user_id"]):
            raise HTTPException(400, "BTB_LEAD_PARTICIPANT_STATUS_MANAGED_BY_FINALIZE")
        row = set_lead_managed_status(
            cur,
            btb_id=btb_id,
            team_id=btb["team_id"],
            target_user_id=participant_user_id,
            actor_user_id=user["user_id"],
            status=payload.status,
        )
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB_PARTICIPANT", entity_id=participant_user_id, operation="UPDATE", entity_version=row["version"], actor_user_id=user["user_id"])
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=btb["version"], actor_user_id=user["user_id"])
        audit(cur, action="BTB_PARTICIPANT_LEAD_UPDATE", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, details={"participant_user_id": str(participant_user_id), "status": payload.status})
        response = {"btb_id": btb_id, "participant": row}
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_PARTICIPANT_LEAD_UPDATE", entity_id=btb_id, result_version=row["version"], response_payload=response)
    return response


@router.post("/btbs/{btb_id}/finalize")
def finalize_btb(
    btb_id: UUID,
    payload: BtbFinalizeRequest | None = None,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    started = perf_counter()
    final_path = temp_path = backup_path = None
    installed = False
    had_previous = False
    try:
        with transaction() as conn, conn.cursor() as cur:
            receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_FINALIZE")
            if receipt:
                return replay_payload(receipt)
            lock_started = perf_counter()
            btb = _load_btb(cur, btb_id, for_update=True)
            lock_read_ms = (perf_counter() - lock_started) * 1000
            context = worksite_context(cur, user, btb["worksite_id"])
            require_permission(context, "btb.finalize")
            _assert_team_member(cur, user, btb["team_id"])
            require_team_lead(cur, user, btb["team_id"])
            team = _team(cur, btb["worksite_id"], btb["team_id"])
            mark_lead_finished(
                cur,
                btb_id=btb_id,
                team_id=btb["team_id"],
                lead_user_id=user["user_id"],
            )
            unfinished = unfinished_participants(
                cur,
                btb_id=btb_id,
                team_id=btb["team_id"],
                exclude_user_id=user["user_id"],
            )
            allow_unfinished = bool(payload and payload.allow_unfinished)
            if unfinished and not allow_unfinished:
                raise HTTPException(
                    409,
                    {
                        "code": "BTB_PARTICIPANTS_NOT_FINISHED",
                        "participants": [
                            {
                                "user_id": str(row["user_id"]),
                                "display_name": row.get("display_name"),
                                "status": row["status"],
                            }
                            for row in unfinished
                        ],
                    },
                )
            if btb["status"] == "FINALIZED":
                if team["active_btb_id"] != btb_id:
                    raise HTTPException(409, "BTB_NOT_ACTIVE")
                pdf_file = load_final_pdf(cur, btb_id)
                if not pdf_file:
                    raise HTTPException(409, "BTB_FINAL_PDF_MISSING")
                cur.execute(
                    "UPDATE tb.teams SET active_btb_id = NULL, updated_at = now() WHERE id = %s AND active_btb_id = %s",
                    (btb["team_id"], btb_id),
                )
                response = {**btb, "final_pdf": public_final_pdf(pdf_file, btb_id=btb_id)}
                audit(
                    cur,
                    action="BTB_FINALIZE_NO_CHANGES",
                    actor_user_id=user["user_id"],
                    company_id=context["company_id"],
                    worksite_id=btb["worksite_id"],
                    entity_type="BTB",
                    entity_id=btb_id,
                )
                finish_mutation(
                    cur,
                    user=user,
                    context=mutation,
                    receipt_type="BTB_FINALIZE",
                    entity_id=btb_id,
                    result_version=btb["version"],
                    response_payload=response,
                )
                return response
            if btb["status"] not in {"ACTIVE", "REWORK"}:
                raise HTTPException(409, "BTB_NOT_FINALIZABLE")
            photo_files = load_btb_photo_files(cur, btb_id)
            snapshot = _snapshot(cur, btb, photo_files=photo_files)
            canonical = json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
            digest = hashlib.sha256(canonical).hexdigest()
            original = load_original_snapshot(cur, btb_id)
            original_snapshot = original["snapshot"] if original else None
            final_path, temp_path, backup_path = final_pdf_paths(company_id=context["company_id"], worksite_id=btb["worksite_id"], btb_id=btb_id)
            pdf_context = _pdf_context(cur, btb)
            render_photos = photo_files_for_render(photo_files)
            render_started = perf_counter()
            render_btb_pdf(temp_path, snapshot=snapshot, original_snapshot=original_snapshot, context=pdf_context, photo_files=render_photos)
            render_finished = perf_counter()
            pdf_size = temp_path.stat().st_size
            pdf_sha256 = hash_file(temp_path)
            had_previous = install_pdf_atomically(temp_path, final_path, backup_path)
            installed = True
            pdf_file, pdf_operation = upsert_final_pdf_file(cur, btb=btb, company_id=context["company_id"], user_id=user["user_id"], byte_size=pdf_size, sha256=pdf_sha256)
            if original is None:
                cur.execute("INSERT INTO tb.btb_original_snapshots(btb_id, snapshot, snapshot_sha256, created_by) VALUES (%s,%s,%s,%s)", (btb_id, Jsonb(snapshot), digest, user["user_id"]))
            cur.execute("UPDATE tb.btbs SET status = 'FINALIZED', first_finalized_at = coalesce(first_finalized_at, now()), finalized_at = now(), version = version + 1, updated_at = now() WHERE id = %s RETURNING *", (btb_id,))
            row = dict(cur.fetchone())
            cur.execute("UPDATE tb.teams SET active_btb_id = NULL, updated_at = now() WHERE id = %s AND active_btb_id = %s", (btb["team_id"], btb_id))
            emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="FILE", entity_id=pdf_file["id"], operation=pdf_operation, entity_version=None, actor_user_id=user["user_id"])
            emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="FINALIZE", entity_version=row["version"], actor_user_id=user["user_id"])
            audit(cur, action="BTB_FINALIZE", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, details={"snapshot_sha256": digest, "pdf_sha256": pdf_sha256, "pdf_file_id": str(pdf_file["id"]), "pdf_renderer": PDF_RENDERER_VERSION, "unfinished_override": allow_unfinished, "unfinished_user_ids": [str(item["user_id"]) for item in unfinished]})
            response = {**row, "final_pdf": public_final_pdf(pdf_file, btb_id=btb_id)}
            finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_FINALIZE", entity_id=btb_id, result_version=row["version"], response_payload=response)
        finish_pdf_install(temp_path, backup_path)
    except Exception:
        if installed and final_path is not None and backup_path is not None:
            rollback_pdf_install(final_path, backup_path, had_previous)
        if temp_path is not None and backup_path is not None:
            finish_pdf_install(temp_path, backup_path)
        raise
    finished = perf_counter()
    logger.info(
        "btb_finalize_timing prepare_ms=%.1f lock_read_ms=%.1f render_ms=%.1f "
        "persist_ms=%.1f total_ms=%.1f pdf_bytes=%d",
        (render_started - started) * 1000,
        lock_read_ms,
        (render_finished - render_started) * 1000,
        (finished - render_finished) * 1000,
        (finished - started) * 1000,
        pdf_size,
    )
    return response


@router.get("/btbs/{btb_id}/final-pdf")
def get_final_pdf(
    btb_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
):
    with transaction() as conn, conn.cursor() as cur:
        btb = _load_btb(cur, btb_id)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.view")
        _assert_team_member(cur, user, btb["team_id"])
        require_team_lead(cur, user, btb["team_id"])
        if btb["status"] != "FINALIZED":
            raise HTTPException(404, "BTB_FINAL_PDF_NOT_AVAILABLE")
        pdf_file = load_final_pdf(cur, btb_id)
        if not pdf_file:
            raise HTTPException(404, "BTB_FINAL_PDF_NOT_FOUND")
    path = final_pdf_content_path(pdf_file)
    if not path.is_file():
        raise HTTPException(404, "BTB_FINAL_PDF_CONTENT_MISSING")
    return FileResponse(path, media_type=pdf_file["mime_type"], filename=pdf_file["original_name"])


@router.post("/btbs/{btb_id}/reopen")
def reopen_btb(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_REOPEN")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_team_lead(cur, user, btb["team_id"])
        if btb["status"] == "REWORK":
            return btb
        if btb["status"] != "FINALIZED":
            raise HTTPException(409, "BTB_NOT_FINALIZED")
        # REWORK is an explicit editable report, not the team's current shift.
        # Keep the active pointer of a newer shift untouched.
        cur.execute("UPDATE tb.btbs SET status='REWORK', version=version+1, updated_at=now() WHERE id=%s RETURNING *", (btb_id,))
        btb = dict(cur.fetchone())
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="REACTIVATE", entity_version=btb["version"], actor_user_id=user["user_id"])
        audit(cur, action="BTB_REACTIVATE", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id)
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_REOPEN", entity_id=btb_id, result_version=btb["version"], response_payload=btb)
    return btb



@router.patch("/btbs/{btb_id}/photos/{file_id}")
def update_btb_photo(
    btb_id: UUID,
    file_id: UUID,
    payload: BtbPhotoUpdate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    for key, limit in (("lat", 90), ("lon", 180)):
        raw = payload.photo_metadata.get(key)
        if raw is not None:
            try:
                valid = not isinstance(raw, bool) and math.isfinite(float(raw)) and abs(float(raw)) <= limit
            except (ValueError, TypeError):
                valid = False
            if not valid:
                raise HTTPException(400, "GPS_INVALID")
    if payload.photo_metadata.get('btbEntryId') not in (None, ''):
        try:
            UUID(str(payload.photo_metadata['btbEntryId']))
        except (TypeError, ValueError):
            raise HTTPException(400, 'BTB_ENTRY_INVALID')
    patch = normalize_btb_photo_override(payload.photo_metadata)
    if not patch:
        raise HTTPException(400, "NO_CHANGES")
    invalidated_pdf_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_PHOTO_UPDATE")
        if receipt:
            return replay_payload(receipt)
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_participant_writable(cur, user=user, btb_id=btb_id, team_id=btb["team_id"])
        cur.execute(
            """
            SELECT l.link_metadata, l.version AS link_version,
                   f.original_name, f.captured_at, f.gps_lat, f.gps_lon,
                   f.gps_accuracy_m, f.photo_metadata
            FROM tb.file_links l
            JOIN tb.files f ON f.id = l.file_id
            WHERE l.entity_type = 'BTB'
              AND l.entity_id = %s
              AND l.file_id = %s
              AND l.purpose <> %s
              AND f.deleted_at IS NULL
              AND f.mime_type LIKE 'image/%%'
            FOR UPDATE
            """,
            (btb_id, file_id, "BTB_FINAL_PDF"),
        )
        current = cur.fetchone()
        if not current:
            raise HTTPException(404, "BTB_PHOTO_NOT_FOUND")
        current = dict(current)
        if int(current["link_version"]) != payload.base_version:
            raise HTTPException(
                409,
                {"code": "VERSION_CONFLICT", "current_version": current["link_version"]},
            )
        next_override = dict(current.get("link_metadata") or {})
        next_override.update(patch)
        if next_override.get('btbEntryId'):
            next_override.update(entry_photo_context(cur, btb_id, next_override['btbEntryId']))
        if next_override == dict(current.get("link_metadata") or {}):
            raise HTTPException(400, "NO_CHANGES")
        base_meta = canonical_photo_metadata(
            filename=current.get("original_name"),
            captured_at=current.get("captured_at"),
            gps_lat=current.get("gps_lat"),
            gps_lon=current.get("gps_lon"),
            gps_accuracy_m=current.get("gps_accuracy_m"),
            photo_metadata=current.get("photo_metadata"),
        )["photo_metadata"] or {}
        effective_meta = merge_btb_photo_metadata(base_meta, next_override)
        if {'lat','lon'} & patch.keys() and (effective_meta.get('lat') is None) != (effective_meta.get('lon') is None):
            raise HTTPException(400, "GPS_PAIR_REQUIRED")
        _validate_worksite_node_key(
            cur,
            btb["worksite_id"],
            effective_meta.get("worksiteNodeKey"),
        )
        invalidated_pdf_keys.extend(prepare_btb_content_change(cur, btb))
        cur.execute(
            """
            UPDATE tb.file_links
            SET link_metadata = %s,
                version = version + 1,
                updated_by = %s,
                updated_at = now()
            WHERE file_id = %s
              AND entity_type = 'BTB'
              AND entity_id = %s
              AND version = %s
            RETURNING version, link_metadata
            """,
            (
                Jsonb(next_override),
                user["user_id"],
                file_id,
                btb_id,
                payload.base_version,
            ),
        )
        updated_link = cur.fetchone()
        if not updated_link:
            raise HTTPException(
                409,
                {"code": "VERSION_CONFLICT", "current_version": current["link_version"]},
            )
        updated_link = dict(updated_link)
        cur.execute(
            "UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version",
            (btb_id,),
        )
        parent_version = cur.fetchone()["version"]
        response = {
            "file_id": str(file_id),
            "link_version": updated_link["version"],
            "photo_metadata": merge_btb_photo_metadata(base_meta, updated_link["link_metadata"]),
        }
        emit_change(
            cur,
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="FILE",
            entity_id=file_id,
            operation="UPDATE",
            entity_version=updated_link["version"],
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
        audit(
            cur,
            action="BTB_PHOTO_UPDATE",
            actor_user_id=user["user_id"],
            company_id=context["company_id"],
            worksite_id=btb["worksite_id"],
            entity_type="FILE",
            entity_id=file_id,
            details={"btb_id": str(btb_id), "fields": sorted(patch.keys())},
        )
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BTB_PHOTO_UPDATE",
            entity_id=file_id,
            result_version=updated_link["version"],
            response_payload=response,
        )
    remove_storage_keys(invalidated_pdf_keys)
    return response


@router.delete("/btbs/{btb_id}/photos/{file_id}", status_code=204)
def detach_btb_photo(
    btb_id: UUID,
    file_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
):
    invalidated_pdf_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_PHOTO_DETACH")
        if receipt:
            return None
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.edit")
        _assert_team_member(cur, user, btb["team_id"])
        require_participant_writable(cur, user=user, btb_id=btb_id, team_id=btb["team_id"])
        cur.execute("SELECT f.id FROM tb.file_links l JOIN tb.files f ON f.id = l.file_id WHERE l.entity_type = 'BTB' AND l.entity_id = %s AND l.file_id = %s AND f.deleted_at IS NULL AND f.mime_type LIKE 'image/%%' FOR UPDATE", (btb_id, file_id))
        if not cur.fetchone():
            raise HTTPException(404, "BTB_PHOTO_NOT_FOUND")
        invalidated_pdf_keys.extend(prepare_btb_content_change(cur, btb))
        cur.execute("DELETE FROM tb.file_links WHERE entity_type = 'BTB' AND entity_id = %s AND file_id = %s", (btb_id, file_id))
        cur.execute("UPDATE tb.btbs SET version = version + 1, updated_at = now() WHERE id = %s RETURNING version", (btb_id,))
        version = cur.fetchone()["version"]
        emit_change(cur, company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, operation="UPDATE", entity_version=version, actor_user_id=user["user_id"])
        audit(cur, action="BTB_PHOTO_DETACH", actor_user_id=user["user_id"], company_id=context["company_id"], worksite_id=btb["worksite_id"], entity_type="BTB", entity_id=btb_id, details={"file_id": str(file_id)})
        finish_mutation(cur, user=user, context=mutation, receipt_type="BTB_PHOTO_DETACH", entity_id=btb_id, result_version=version, response_payload={"detached": True})
    remove_storage_keys(invalidated_pdf_keys)
    return None


@router.delete("/btbs/{btb_id}", status_code=204)
def delete_btb(
    btb_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
):
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="BTB_DELETE")
        if receipt:
            return None
        btb = _load_btb(cur, btb_id, for_update=True)
        context = worksite_context(cur, user, btb["worksite_id"])
        require_permission(context, "btb.delete")
        _assert_team_member(cur, user, btb["team_id"])
        invalidated_pdf_keys = invalidate_final_pdf(cur, btb_id)
        cur.execute("DELETE FROM tb.file_links l USING tb.files f WHERE l.file_id = f.id AND l.entity_type = 'BTB' AND l.entity_id = %s AND f.mime_type LIKE 'image/%%'", (btb_id,))
        cur.execute("DELETE FROM tb.btb_original_snapshots WHERE btb_id = %s", (btb_id,))
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
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="BTB_DELETE",
            entity_id=btb_id,
            result_version=version,
            response_payload={"deleted": True},
        )
    remove_storage_keys(invalidated_pdf_keys)
    return None
