from __future__ import annotations

from .btb_photo_meta import effective_catalog_photo_metadata

import base64
import hashlib
import json
import os
import re
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .btb_documents import FINAL_PDF_PURPOSE, prepare_btb_photo_change, remove_storage_keys
from .btb_photo_meta import canonical_photo_metadata
from .btb_participants import require_participant_writable
from .config import settings
from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .image_renditions import (
    RENDITION_SPECS,
    RENDITION_VERSION,
    image_rendition_urls,
    render_image_rendition,
    rendition_cache_path,
)
from .permissions import area_scope_allows, has_permission, is_team_lead, require_permission, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["files"])

_MIB = 1024 * 1024
_DEFAULT_CHUNK_SIZE = 8 * _MIB
_MAX_CHUNK_SIZE = 16 * _MIB
_SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9._ -]+")
_ALLOWED_LINK_TYPES = {"WORKSITE", "BTB", "DEFECT", "ACCEPTANCE"}


PHOTO_MAX_BYTES = 2_000_000


def _is_photo_upload(upload):
    return (str(upload.get('target_kind','')).upper() in {'PHOTO','DEFECT_PHOTO','ACCEPTANCE_PHOTO'}
        or str(upload.get('mime_type','')).lower().startswith('image/')
        or Path(str(upload.get('original_name') or '')).suffix.lower() in
            {'.jpg','.jpeg','.png','.webp','.avif','.heic','.heif','.gif','.bmp','.tif','.tiff'})


def _require_photo_size(upload, size, path=None):
    if size is None or size <= PHOTO_MAX_BYTES:
        return
    photo = _is_photo_upload(upload)
    if not photo and path is not None:
        from PIL import Image, UnidentifiedImageError
        try:
            with Image.open(path) as im:
                photo = im.format is not None
        except (UnidentifiedImageError, OSError, ValueError):
            pass
    if photo:
        raise HTTPException(413, {'code':'PHOTO_TOO_LARGE','max_bytes':PHOTO_MAX_BYTES,
            'message':'Neue Bilder dürfen höchstens 2 MB groß sein. Bitte vor dem Upload verkleinern.'})


class UploadCreate(BaseModel):
    target_kind: str = Field(default="ATTACHMENT", min_length=1, max_length=80)
    original_name: str | None = Field(default=None, max_length=255)
    mime_type: str = Field(min_length=1, max_length=255)
    expected_bytes: int | None = Field(default=None, ge=0)
    expected_sha256: str | None = Field(default=None, min_length=64, max_length=64)
    chunk_size: int = Field(default=_DEFAULT_CHUNK_SIZE, ge=_MIB, le=_MAX_CHUNK_SIZE)


class UploadComplete(BaseModel):
    entity_type: str | None = Field(default=None, max_length=80)
    entity_id: UUID | None = None
    purpose: str = Field(default="ATTACHMENT", min_length=1, max_length=80)
    captured_at: datetime | None = None
    gps_lat: float | None = Field(default=None, ge=-90, le=90)
    gps_lon: float | None = Field(default=None, ge=-180, le=180)
    gps_accuracy_m: float | None = Field(default=None, ge=0)
    photo_metadata: dict[str, Any] | None = None
    btb_entry_id: UUID | None = None


def _safe_name(name: str | None) -> str:
    raw = Path(name or "file.bin").name.strip() or "file.bin"
    safe = _SAFE_NAME_RE.sub("_", raw).strip(" .") or "file.bin"
    return safe[:180]


def _absolute_storage_path(storage_key: str) -> Path:
    root = settings.storage_root.resolve()
    path = (root / storage_key).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise HTTPException(500, "INVALID_STORAGE_KEY") from exc
    return path


def _load_upload(cur, upload_id: UUID, *, for_update: bool = False) -> dict:
    suffix = " FOR UPDATE" if for_update else ""
    cur.execute(
        f"""
        SELECT id, company_id, worksite_id, user_id, target_kind, original_name,
               mime_type, expected_bytes, expected_sha256, chunk_size,
               temp_storage_key, received_bytes, status, created_at, updated_at, expires_at
        FROM tb.upload_sessions
        WHERE id = %s{suffix}
        """,
        (upload_id,),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "UPLOAD_NOT_FOUND")
    return dict(row)


def _authorize_upload(cur, user: dict[str, Any], upload: dict, *, write: bool) -> dict:
    if upload["worksite_id"] is None:
        raise HTTPException(409, "UPLOAD_WITHOUT_WORKSITE_NOT_SUPPORTED")
    context = worksite_context(cur, user, upload["worksite_id"])
    if context["company_id"] != upload["company_id"]:
        raise HTTPException(403, "UPLOAD_COMPANY_MISMATCH")
    if upload.get("user_id") != user["user_id"]:
        raise HTTPException(403, "UPLOAD_OWNER_REQUIRED")
    if write:
        require_permission(context, "files.add")
    return context


def _entity_link_visible(
    cur,
    user: dict[str, Any],
    context: dict[str, Any],
    entity_type: str,
    entity_id: UUID,
    purpose: str | None = None,
) -> bool:
    normalized = entity_type.strip().upper()
    if normalized == "WORKSITE":
        return str(entity_id) == str(context["id"])

    if normalized == "BTB":
        if not has_permission(context, "btb.view"):
            return False
        cur.execute(
            "SELECT team_id, worksite_id, status FROM tb.btbs WHERE id = %s",
            (entity_id,),
        )
        row = cur.fetchone()
        if not row or row["worksite_id"] != context["id"] or row["status"] == "DELETED":
            return False
        if str(purpose or "").upper() == FINAL_PDF_PURPOSE:
            return is_team_lead(cur, user, row["team_id"])
        if user["company_role"] in {"ADMIN", "CHEF"}:
            return True
        cur.execute(
            """
            SELECT 1
            FROM tb.team_members
            WHERE team_id = %s AND user_id = %s AND removed_at IS NULL
            """,
            (row["team_id"], user["user_id"]),
        )
        return cur.fetchone() is not None

    if normalized == "DEFECT":
        if not has_permission(context, "defect.view"):
            return False
        cur.execute(
            """
            SELECT worksite_id, area_scope, status, deleted_at
            FROM tb.defects
            WHERE id = %s
            """,
            (entity_id,),
        )
        row = cur.fetchone()
        return bool(
            row
            and row["worksite_id"] == context["id"]
            and row["status"] != "DELETED"
            and row["deleted_at"] is None
            and area_scope_allows(context["effective_area_scope"], row["area_scope"])
        )

    if normalized == "ACCEPTANCE":
        if not has_permission(context, "acceptance.view"):
            return False
        cur.execute(
            """
            SELECT worksite_id, area_scope, status, deleted_at
            FROM tb.acceptances
            WHERE id = %s
            """,
            (entity_id,),
        )
        row = cur.fetchone()
        return bool(
            row
            and row["worksite_id"] == context["id"]
            and row["status"] != "DELETED"
            and row["deleted_at"] is None
            and area_scope_allows(context["effective_area_scope"], row["area_scope"])
        )

    return False


def _validate_link(
    cur,
    user: dict[str, Any],
    context: dict[str, Any],
    worksite_id: UUID,
    entity_type: str | None,
    entity_id: UUID | None,
) -> tuple[str, UUID] | None:
    if entity_type is None and entity_id is None:
        return None
    if entity_type is None or entity_id is None:
        raise HTTPException(400, "FILE_LINK_INCOMPLETE")
    normalized = entity_type.strip().upper()
    if normalized not in _ALLOWED_LINK_TYPES:
        raise HTTPException(400, "FILE_LINK_TYPE_NOT_SUPPORTED")
    if normalized == "WORKSITE" and entity_id != worksite_id:
        raise HTTPException(400, "FILE_LINK_WORKSITE_MISMATCH")
    if not _entity_link_visible(cur, user, context, normalized, entity_id):
        raise HTTPException(403, "FILE_LINK_TARGET_NOT_VISIBLE")
    return normalized, entity_id


def _responsible_tb_btb_image_visible(
    cur,
    user: dict[str, Any],
    context: dict[str, Any],
    entity_id: UUID,
) -> bool:
    if user["company_role"] in {"ADMIN", "CHEF"}:
        privileged = True
    else:
        privileged = context.get("worksite_role") == "RESPONSIBLE_TB"
    if not privileged:
        return False

    cur.execute(
        """
        SELECT 1
        FROM tb.btbs
        WHERE id = %s
          AND worksite_id = %s
          AND status <> 'DELETED'
          AND deleted_at IS NULL
        """,
        (entity_id, context["id"]),
    )
    return cur.fetchone() is not None


def _file_visible_for_context(
    cur,
    user: dict[str, Any],
    context: dict[str, Any],
    file_id: UUID,
    *,
    mime_type: str | None = None,
    allow_responsible_btb_images: bool = False,
) -> bool:
    cur.execute(
        """
        SELECT entity_type, entity_id, purpose
        FROM tb.file_links
        WHERE file_id = %s
        ORDER BY created_at
        """,
        (file_id,),
    )
    links = cur.fetchall()
    if not links:
        return True

    for link in links:
        if _entity_link_visible(
            cur,
            user,
            context,
            link["entity_type"],
            link["entity_id"],
            link.get("purpose"),
        ):
            return True
        if (
            allow_responsible_btb_images
            and str(mime_type or "").lower().startswith("image/")
            and str(link["entity_type"] or "").upper() == "BTB"
            and _responsible_tb_btb_image_visible(
                cur,
                user,
                context,
                link["entity_id"],
            )
        ):
            return True
    return False


def _media_uploader_team(
    cur,
    worksite_id: UUID,
    user_id: UUID | None,
) -> tuple[UUID | None, str | None]:
    if user_id is None:
        return None, None
    cur.execute(
        """
        SELECT t.id AS team_id,
               lead.display_name AS responsible_tb
        FROM tb.team_members tm
        JOIN tb.teams t
          ON t.id = tm.team_id
         AND t.worksite_id = %s
         AND t.status = 'ACTIVE'
        LEFT JOIN tb.users lead ON lead.id = t.lead_user_id
        WHERE tm.user_id = %s
          AND tm.removed_at IS NULL
        ORDER BY
          CASE WHEN t.lead_user_id = %s THEN 0 ELSE 1 END,
          t.created_at,
          t.id
        LIMIT 1
        """,
        (worksite_id, user_id, user_id),
    )
    row = cur.fetchone()
    if not row:
        return None, None
    return row["team_id"], row.get("responsible_tb")


def _legacy_defect_status(status: str | None) -> str | None:
    normalized = str(status or "").upper()
    return {
        "OPEN": "OFFEN",
        "IN_PROGRESS": "IN_ARBEIT",
        "REJECTED": "ABGELEHNT",
        "CLOSED": "ERLEDIGT",
    }.get(normalized, normalized or None)


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as src:
        for block in iter(lambda: src.read(_MIB), b""):
            digest.update(block)
    return digest.hexdigest()


def _encode_file_cursor(created_at: datetime, file_id: UUID) -> str:
    payload = json.dumps(
        {"created_at": created_at.isoformat(), "id": str(file_id)},
        separators=(",", ":"),
    ).encode("utf-8")
    return base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")


def _decode_file_cursor(cursor: str | None) -> tuple[datetime, UUID] | None:
    if cursor is None or not cursor.strip():
        return None
    try:
        raw = cursor.strip()
        raw += "=" * (-len(raw) % 4)
        payload = json.loads(base64.urlsafe_b64decode(raw.encode("ascii")).decode("utf-8"))
        created_at = datetime.fromisoformat(str(payload["created_at"]))
        file_id = UUID(str(payload["id"]))
        return created_at, file_id
    except Exception as exc:
        raise HTTPException(400, "INVALID_FILE_CURSOR") from exc


def _serialize_upload(upload: dict) -> dict:
    return {
        "id": upload["id"],
        "worksite_id": upload["worksite_id"],
        "target_kind": upload["target_kind"],
        "original_name": upload["original_name"],
        "mime_type": upload["mime_type"],
        "expected_bytes": upload["expected_bytes"],
        "expected_sha256": upload["expected_sha256"],
        "chunk_size": upload["chunk_size"],
        "received_bytes": upload["received_bytes"],
        "status": upload["status"],
        "expires_at": upload["expires_at"],
    }


def _load_file_for_user(
    cur,
    user: dict[str, Any],
    file_id: UUID,
    *,
    allow_responsible_btb_images: bool = False,
) -> dict:
    cur.execute(
        """
        SELECT id, company_id, worksite_id, storage_key, original_name, mime_type,
               byte_size, sha256, source, captured_at, gps_lat, gps_lon,
               gps_accuracy_m, photo_metadata, created_by, created_at, deleted_at
        FROM tb.files WHERE id = %s
        """,
        (file_id,),
    )
    row = cur.fetchone()
    if not row or row["deleted_at"] is not None:
        raise HTTPException(404, "FILE_NOT_FOUND")
    result = dict(row)
    canonical = canonical_photo_metadata(
        filename=result.get("original_name"),
        captured_at=result.get("captured_at"),
        gps_lat=result.get("gps_lat"),
        gps_lon=result.get("gps_lon"),
        gps_accuracy_m=result.get("gps_accuracy_m"),
        photo_metadata=result.get("photo_metadata"),
    )
    result["photo_metadata"] = canonical["photo_metadata"]
    if result["worksite_id"] is None:
        if user["company_role"] != "ADMIN" and user["company_id"] != result["company_id"]:
            raise HTTPException(403, "FILE_ACCESS_DENIED")
    else:
        context = worksite_context(cur, user, result["worksite_id"])
        require_permission(context, "files.view")
        if not _file_visible_for_context(
            cur,
            user,
            context,
            result["id"],
            mime_type=result["mime_type"],
            allow_responsible_btb_images=allow_responsible_btb_images,
        ):
            raise HTTPException(403, "FILE_ACCESS_DENIED")
    return result


@router.post("/worksites/{worksite_id}/uploads", status_code=201)
def create_upload(
    worksite_id: UUID,
    payload: UploadCreate,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    expected_sha256 = payload.expected_sha256.lower() if payload.expected_sha256 else None
    if expected_sha256 and any(ch not in "0123456789abcdef" for ch in expected_sha256):
        raise HTTPException(400, "INVALID_SHA256")
    target_kind = payload.target_kind.strip().upper()
    if not target_kind:
        raise HTTPException(400, "INVALID_TARGET_KIND")

    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="UPLOAD_CREATE")
        if receipt:
            return replay_payload(receipt)
        context = worksite_context(cur, user, worksite_id)
        _require_photo_size(payload.model_dump(), payload.expected_bytes)
        require_permission(context, "files.add")
        upload_id = uuid4()
        temp_key = f"companies/{context['company_id']}/worksites/{worksite_id}/.uploads/{upload_id}.part"
        cur.execute(
            """
            INSERT INTO tb.upload_sessions(
                id, company_id, worksite_id, user_id, target_kind, original_name,
                mime_type, expected_bytes, expected_sha256, chunk_size,
                temp_storage_key, expires_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now() + interval '24 hours')
            RETURNING *
            """,
            (
                upload_id,
                context["company_id"],
                worksite_id,
                user["user_id"],
                target_kind,
                payload.original_name,
                payload.mime_type,
                payload.expected_bytes,
                expected_sha256,
                payload.chunk_size,
                temp_key,
            ),
        )
        row = dict(cur.fetchone())
        response = _serialize_upload(row)
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="UPLOAD_CREATE",
            entity_id=upload_id,
            result_version=None,
            response_payload=response,
        )
    return response


@router.get("/uploads/{upload_id}")
def get_upload(
    upload_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        upload = _load_upload(cur, upload_id)
        _authorize_upload(cur, user, upload, write=False)
    return _serialize_upload(upload)


@router.put("/uploads/{upload_id}/chunk")
async def upload_chunk(
    upload_id: UUID,
    request: Request,
    offset: int = Query(ge=0),
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    body = await request.body()
    if not body:
        raise HTTPException(400, "EMPTY_CHUNK")
    if len(body) > _MAX_CHUNK_SIZE:
        raise HTTPException(413, "CHUNK_TOO_LARGE")

    path: Path | None = None
    previous_size = 0
    wrote = False
    try:
        with transaction() as conn, conn.cursor() as cur:
            upload = _load_upload(cur, upload_id, for_update=True)
            _authorize_upload(cur, user, upload, write=True)
            if upload["status"] != "OPEN":
                raise HTTPException(409, "UPLOAD_NOT_OPEN")
            cur.execute("SELECT now() >= %s AS expired", (upload["expires_at"],))
            if cur.fetchone()["expired"]:
                cur.execute("UPDATE tb.upload_sessions SET status = 'EXPIRED', updated_at = now() WHERE id = %s", (upload_id,))
                raise HTTPException(410, "UPLOAD_EXPIRED")
            if offset != upload["received_bytes"]:
                raise HTTPException(409, {"code": "UPLOAD_OFFSET_MISMATCH", "expected_offset": upload["received_bytes"]})
            _require_photo_size(upload, offset + len(body))
            if len(body) > upload["chunk_size"]:
                raise HTTPException(413, {"code": "CHUNK_EXCEEDS_SESSION_LIMIT", "chunk_size": upload["chunk_size"]})
            if upload["expected_bytes"] is not None and offset + len(body) > upload["expected_bytes"]:
                raise HTTPException(413, "UPLOAD_EXCEEDS_EXPECTED_SIZE")

            path = _absolute_storage_path(upload["temp_storage_key"])
            path.parent.mkdir(parents=True, exist_ok=True)
            previous_size = path.stat().st_size if path.exists() else 0
            if previous_size != upload["received_bytes"]:
                raise HTTPException(409, {"code": "UPLOAD_STORAGE_MISMATCH", "stored_bytes": previous_size, "expected_bytes": upload["received_bytes"]})
            with path.open("ab") as out:
                out.write(body)
                out.flush()
                os.fsync(out.fileno())
            wrote = True
            new_size = offset + len(body)
            cur.execute(
                "UPDATE tb.upload_sessions SET received_bytes = %s, updated_at = now() WHERE id = %s RETURNING *",
                (new_size, upload_id),
            )
            row = dict(cur.fetchone())
        return _serialize_upload(row)
    except Exception:
        if wrote and path is not None and path.exists():
            with path.open("r+b") as out:
                out.truncate(previous_size)
        raise


@router.post("/uploads/{upload_id}/complete")
def complete_upload(
    upload_id: UUID,
    payload: UploadComplete,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
) -> dict:
    final_path: Path | None = None
    temp_path: Path | None = None
    moved = False
    invalidated_pdf_keys: list[str] = []
    try:
        with transaction() as conn, conn.cursor() as cur:
            receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="UPLOAD_COMPLETE")
            if receipt:
                return replay_payload(receipt)
            upload = _load_upload(cur, upload_id, for_update=True)
            context = _authorize_upload(cur, user, upload, write=True)
            if upload["status"] != "OPEN":
                raise HTTPException(409, "UPLOAD_NOT_OPEN")
            temp_path = _absolute_storage_path(upload["temp_storage_key"])
            if not temp_path.exists():
                if upload["received_bytes"] == 0:
                    temp_path.parent.mkdir(parents=True, exist_ok=True)
                    temp_path.touch()
                else:
                    raise HTTPException(409, "UPLOAD_TEMP_FILE_MISSING")
            actual_size = temp_path.stat().st_size
            _require_photo_size(upload, actual_size, temp_path)
            if actual_size != upload["received_bytes"]:
                raise HTTPException(409, "UPLOAD_STORAGE_MISMATCH")
            if upload["expected_bytes"] is not None and actual_size != upload["expected_bytes"]:
                raise HTTPException(409, {"code": "UPLOAD_SIZE_MISMATCH", "actual": actual_size, "expected": upload["expected_bytes"]})

            actual_sha256 = _hash_file(temp_path)
            if upload["expected_sha256"] and actual_sha256 != upload["expected_sha256"].lower():
                raise HTTPException(409, {"code": "UPLOAD_SHA256_MISMATCH", "actual": actual_sha256})
            link = _validate_link(
                cur,
                user,
                context,
                upload["worksite_id"],
                payload.entity_type,
                payload.entity_id,
            )

            entry_metadata = {}
            if payload.btb_entry_id is not None:
                require_permission(context, "btb.edit")
                if not link or link[0] != 'BTB' or not upload['mime_type'].lower().startswith('image/'):
                    raise HTTPException(400, 'BTB_ENTRY_INVALID')
                # Parent before entry: same lock order as entry edits/finalization.
                cur.execute("SELECT id FROM tb.btbs WHERE id=%s AND worksite_id=%s AND status <> 'DELETED' FOR UPDATE", (link[1], upload['worksite_id']))
                if not cur.fetchone():
                    raise HTTPException(404, 'BTB_NOT_FOUND')
                from .btb_entry_photos import entry_photo_context
                entry_metadata = entry_photo_context(cur, link[1], payload.btb_entry_id)
            file_id = uuid4()
            category = {
                "PHOTO": "photos",
                "DEFECT_PHOTO": "defects",
                "ACCEPTANCE_PHOTO": "acceptances",
                "BTB_PDF": "btb",
                "EXPORT": "exports",
            }.get(upload["target_kind"], "files")
            safe_name = _safe_name(upload["original_name"])
            final_key = f"companies/{upload['company_id']}/worksites/{upload['worksite_id']}/{category}/{file_id}/{safe_name}"
            final_path = _absolute_storage_path(final_key)
            final_path.parent.mkdir(parents=True, exist_ok=True)
            os.replace(temp_path, final_path)
            moved = True

            file_source = "APP" if user.get("auth_kind") == "DEVICE" else "PORTAL"
            canonical_meta = canonical_photo_metadata(
                filename=upload["original_name"],
                captured_at=payload.captured_at,
                gps_lat=payload.gps_lat,
                gps_lon=payload.gps_lon,
                gps_accuracy_m=payload.gps_accuracy_m,
                photo_metadata=payload.photo_metadata,
            )
            cur.execute(
                """
                INSERT INTO tb.files(
                    id, company_id, worksite_id, storage_key, original_name, mime_type,
                    byte_size, sha256, source, captured_at, gps_lat, gps_lon,
                    gps_accuracy_m, photo_metadata, created_by
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (
                    file_id,
                    upload["company_id"],
                    upload["worksite_id"],
                    final_key,
                    upload["original_name"],
                    upload["mime_type"],
                    actual_size,
                    actual_sha256,
                    file_source,
                    canonical_meta["captured_at"],
                    canonical_meta["gps_lat"],
                    canonical_meta["gps_lon"],
                    canonical_meta["gps_accuracy_m"],
                    Jsonb(canonical_meta["photo_metadata"] or {}),
                    user["user_id"],
                ),
            )
            file_row = dict(cur.fetchone())
            link_payload = None
            if link:
                purpose = payload.purpose.strip().upper()
                if purpose == FINAL_PDF_PURPOSE:
                    raise HTTPException(400, "FILE_PURPOSE_RESERVED")
                if link[0] == "BTB" and upload["mime_type"].lower().startswith("image/"):
                    cur.execute(
                        "SELECT team_id FROM tb.btbs WHERE id = %s AND worksite_id = %s AND status <> 'DELETED'",
                        (link[1], upload["worksite_id"]),
                    )
                    btb_row = cur.fetchone()
                    if not btb_row:
                        raise HTTPException(404, "BTB_NOT_FOUND")
                    require_participant_writable(
                        cur,
                        user=user,
                        btb_id=link[1],
                        team_id=btb_row["team_id"],
                    )
                    invalidated_pdf_keys.extend(
                        prepare_btb_photo_change(
                            cur,
                            btb_id=link[1],
                            worksite_id=upload["worksite_id"],
                        )
                    )
                if link[0] == "BTB" and not entry_metadata and payload.photo_metadata:
                    from .btb_entry_photos import matching_btb_entry_context
                    entry_metadata = matching_btb_entry_context(cur, link[1], payload.photo_metadata) or {}
                cur.execute(
                    "INSERT INTO tb.file_links(file_id, entity_type, entity_id, purpose, link_metadata) VALUES (%s,%s,%s,%s,%s)",
                    (file_id, link[0], link[1], purpose, Jsonb(entry_metadata)),
                )
                link_payload = {"entity_type": link[0], "entity_id": link[1], "purpose": purpose}
            cur.execute("UPDATE tb.upload_sessions SET status = 'COMPLETED', updated_at = now() WHERE id = %s", (upload_id,))
            emit_change(
                cur,
                company_id=context["company_id"],
                worksite_id=upload["worksite_id"],
                entity_type="FILE",
                entity_id=file_id,
                operation="CREATE",
                entity_version=None,
                actor_user_id=user["user_id"],
            )
            audit(
                cur,
                action="FILE_UPLOAD_COMPLETE",
                actor_user_id=user["user_id"],
                actor_device_id=user.get("device_id"),
                company_id=context["company_id"],
                worksite_id=upload["worksite_id"],
                entity_type="FILE",
                entity_id=file_id,
                details={"byte_size": actual_size, "sha256": actual_sha256, "target_kind": upload["target_kind"], "source": file_source},
            )
            response = {"file": file_row, "link": link_payload}
            finish_mutation(
                cur,
                user=user,
                context=mutation,
                receipt_type="UPLOAD_COMPLETE",
                entity_id=file_id,
                result_version=None,
                response_payload=response,
            )
        remove_storage_keys(invalidated_pdf_keys)
        return response
    except Exception:
        if moved and final_path is not None and final_path.exists() and temp_path is not None:
            temp_path.parent.mkdir(parents=True, exist_ok=True)
            os.replace(final_path, temp_path)
        raise


@router.delete("/uploads/{upload_id}", status_code=204)
def abort_upload(
    upload_id: UUID,
    user: dict[str, Any] = Depends(require_csrf),
    mutation: MutationContext = Depends(mutation_context),
):
    temp_path: Path | None = None
    with transaction() as conn, conn.cursor() as cur:
        receipt = begin_mutation(cur, user=user, context=mutation, receipt_type="UPLOAD_ABORT")
        if receipt:
            return None
        upload = _load_upload(cur, upload_id, for_update=True)
        _authorize_upload(cur, user, upload, write=True)
        if upload["status"] == "COMPLETED":
            raise HTTPException(409, "UPLOAD_ALREADY_COMPLETED")
        cur.execute("UPDATE tb.upload_sessions SET status = 'ABORTED', updated_at = now() WHERE id = %s", (upload_id,))
        temp_path = _absolute_storage_path(upload["temp_storage_key"])
        finish_mutation(
            cur,
            user=user,
            context=mutation,
            receipt_type="UPLOAD_ABORT",
            entity_id=upload_id,
            result_version=None,
            response_payload={"aborted": True},
        )
    if temp_path.exists():
        temp_path.unlink()
    return None


@router.get("/worksites/{worksite_id}/media")
def list_worksite_media(
    worksite_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "files.view")
        cur.execute(
            """
            SELECT f.id,
                   f.original_name,
                   f.mime_type,
                   f.sha256,
                   f.byte_size,
                   f.created_by,
                   f.created_at,
                   f.captured_at,
                   f.gps_lat,
                   f.gps_lon,
                   f.gps_accuracy_m,
                   f.photo_metadata,
                   f.source,
                   uploader.display_name AS uploaded_by_name,
                   l.entity_type,
                   l.entity_id,
                   l.link_metadata,
                   b.team_id AS btb_team_id,
                   b.status AS btb_status,
                   b.deleted_at AS btb_deleted_at,
                   btb_lead.display_name AS btb_responsible_tb,
                   d.area_scope AS defect_area_scope,
                   d.status AS defect_status,
                   d.payload AS defect_payload,
                   d.deleted_at AS defect_deleted_at,
                   a.area_scope AS acceptance_area_scope,
                   a.status AS acceptance_status,
                   a.result AS acceptance_result,
                   a.payload AS acceptance_payload,
                   a.deleted_at AS acceptance_deleted_at
            FROM tb.files f
            JOIN tb.file_links l ON l.file_id = f.id
            LEFT JOIN tb.users uploader ON uploader.id = f.created_by
            LEFT JOIN tb.btbs b
              ON l.entity_type = 'BTB'
             AND b.id = l.entity_id
             AND b.worksite_id = f.worksite_id
            LEFT JOIN tb.teams bt ON bt.id = b.team_id
            LEFT JOIN tb.users btb_lead ON btb_lead.id = bt.lead_user_id
            LEFT JOIN tb.defects d
              ON l.entity_type = 'DEFECT'
             AND d.id = l.entity_id
             AND d.worksite_id = f.worksite_id
            LEFT JOIN tb.acceptances a
              ON l.entity_type = 'ACCEPTANCE'
             AND a.id = l.entity_id
             AND a.worksite_id = f.worksite_id
            WHERE f.worksite_id = %s
              AND f.deleted_at IS NULL
              AND lower(f.mime_type) LIKE 'image/%%'
              AND l.entity_type IN ('BTB','DEFECT','ACCEPTANCE')
            ORDER BY f.created_at, f.id, l.entity_type, l.entity_id
            """,
            (worksite_id,),
        )

        media: list[dict[str, Any]] = []
        for raw in cur.fetchall():
            row = dict(raw)
            entity_type = str(row["entity_type"] or "").upper()
            entity_id = row["entity_id"]

            normal_visible = _entity_link_visible(
                cur,
                user,
                context,
                entity_type,
                entity_id,
            )
            cross_team_btb = (
                entity_type == "BTB"
                and _responsible_tb_btb_image_visible(
                    cur,
                    user,
                    context,
                    entity_id,
                )
            )
            if not normal_visible and not cross_team_btb:
                continue

            if entity_type == "BTB":
                if (
                    row.get("btb_deleted_at") is not None
                    or str(row.get("btb_status") or "").upper() == "DELETED"
                ):
                    continue
                responsible_group_id = row.get("btb_team_id")
                responsible_tb = row.get("btb_responsible_tb")
                parent_status = row.get("btb_status")
                area_scope = {"all": True}
                # Absichtlich kein fremder BTB-Text im baustellenweiten Medienkatalog.
                parent_payload = {}
            elif entity_type == "DEFECT":
                if row.get("defect_deleted_at") is not None:
                    continue
                responsible_group_id, responsible_tb = _media_uploader_team(
                    cur,
                    worksite_id,
                    row.get("created_by"),
                )
                parent_status = _legacy_defect_status(row.get("defect_status"))
                area_scope = row.get("defect_area_scope") or {}
                parent_payload = row.get("defect_payload") or {}
            else:
                if row.get("acceptance_deleted_at") is not None:
                    continue
                responsible_group_id, responsible_tb = _media_uploader_team(
                    cur,
                    worksite_id,
                    row.get("created_by"),
                )
                parent_status = row.get("acceptance_result") or row.get("acceptance_status")
                area_scope = row.get("acceptance_area_scope") or {}
                parent_payload = row.get("acceptance_payload") or {}

            media.append(
                {
                    "attachment_id": str(row["id"]),
                    "entity_type": entity_type,
                    "parent_entity_id": str(entity_id),
                    "filename": row.get("original_name"),
                    "mime_type": row.get("mime_type"),
                    "sha256": row.get("sha256"),
                    "size_bytes": int(row.get("byte_size") or 0),
                    "renditions": image_rendition_urls(
                        row["id"],
                        row.get("mime_type"),
                    ),
                    "created_at": row.get("created_at"),
                    "captured_at": row.get("captured_at"),
                    "gps_lat": row.get("gps_lat"),
                    "gps_lon": row.get("gps_lon"),
                    "gps_accuracy_m": row.get("gps_accuracy_m"),
                    "source": row.get("source"),
                    "photo_metadata": effective_catalog_photo_metadata(row, entity_type == "BTB"),
                    "uploaded_by_user_id": (
                        str(row["created_by"]) if row.get("created_by") else None
                    ),
                    "uploaded_by_name": row.get("uploaded_by_name"),
                    "responsible_group_id": (
                        str(responsible_group_id) if responsible_group_id else None
                    ),
                    "responsible_tb": responsible_tb,
                    "parent_status": parent_status,
                    "area_scope": area_scope,
                    "parent_payload": parent_payload,
                }
            )

    return {
        "status": "ok",
        "worksite_id": str(worksite_id),
        "worksite_role": context.get("worksite_role"),
        "count": len(media),
        "media": media,
    }


@router.get("/worksites/{worksite_id}/files")
def list_files(
    worksite_id: UUID,
    limit: int = Query(default=200, ge=1, le=1000),
    cursor: str | None = Query(default=None, max_length=512),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    after = _decode_file_cursor(cursor)
    visible_rows: list[dict[str, Any]] = []
    last_returned: dict[str, Any] | None = None
    has_more_visible = False

    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        scan_after = after
        require_permission(context, "files.view")
        scan_limit = min(max(limit + 1, 200), 1001)

        while True:
            params: list[Any] = [worksite_id]
            cursor_clause = ""
            if scan_after is not None:
                cursor_clause = " AND (created_at, id) < (%s, %s)"
                params.extend([scan_after[0], scan_after[1]])
            params.append(scan_limit)

            cur.execute(
                f"""
                SELECT id, company_id, worksite_id, original_name, mime_type, byte_size,
                       sha256, source, captured_at, gps_lat, gps_lon, gps_accuracy_m,
                       photo_metadata, created_by, created_at
                FROM tb.files
                WHERE worksite_id = %s
                  AND deleted_at IS NULL
                  {cursor_clause}
                ORDER BY created_at DESC, id DESC
                LIMIT %s
                """,
                tuple(params),
            )
            raw_rows = [dict(row) for row in cur.fetchall()]
            if not raw_rows:
                break

            exhausted = len(raw_rows) < scan_limit
            for row in raw_rows:
                scan_after = (row["created_at"], row["id"])
                if not _file_visible_for_context(cur, user, context, row["id"]):
                    continue
                visible_rows.append(row)
                if len(visible_rows) == limit:
                    last_returned = row
                elif len(visible_rows) > limit:
                    has_more_visible = True
                    break

            if has_more_visible or exhausted:
                break

    rows = visible_rows[:limit]
    for row in rows:
        row["photo_metadata"] = canonical_photo_metadata(
            filename=row.get("original_name"),
            captured_at=row.get("captured_at"),
            gps_lat=row.get("gps_lat"),
            gps_lon=row.get("gps_lon"),
            gps_accuracy_m=row.get("gps_accuracy_m"),
            photo_metadata=row.get("photo_metadata"),
        )["photo_metadata"]
        row["renditions"] = image_rendition_urls(row["id"], row.get("mime_type"))
    next_cursor = (
        _encode_file_cursor(last_returned["created_at"], last_returned["id"])
        if has_more_visible and last_returned is not None
        else None
    )
    return {
        "files": rows,
        "next_cursor": next_cursor,
        "snapshot_complete": next_cursor is None,
    }


@router.get("/files/{file_id}")
def get_file_metadata(
    file_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _load_file_for_user(cur, user, file_id)
        context = (
            worksite_context(cur, user, row["worksite_id"])
            if row["worksite_id"] is not None
            else None
        )
        cur.execute(
            "SELECT entity_type, entity_id, purpose, created_at FROM tb.file_links WHERE file_id = %s ORDER BY created_at",
            (file_id,),
        )
        raw_links = cur.fetchall()
        links = [
            dict(link)
            for link in raw_links
            if context is None
            or _entity_link_visible(
                cur,
                user,
                context,
                link["entity_type"],
                link["entity_id"],
                link.get("purpose"),
            )
        ]
    row.pop("storage_key", None)
    row["renditions"] = image_rendition_urls(row["id"], row.get("mime_type"))
    return {"file": row, "links": links}


@router.get("/files/{file_id}/content")
def download_file(
    file_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
):
    with transaction() as conn, conn.cursor() as cur:
        row = _load_file_for_user(
            cur,
            user,
            file_id,
            allow_responsible_btb_images=True,
        )
    path = _absolute_storage_path(row["storage_key"])
    if not path.is_file():
        raise HTTPException(404, "FILE_CONTENT_MISSING")
    return FileResponse(
        path,
        media_type=row["mime_type"],
        filename=_safe_name(row["original_name"]),
    )


@router.get("/files/{file_id}/rendition/{kind}")
def download_image_rendition(
    file_id: UUID,
    kind: str,
    user: dict[str, Any] = Depends(current_portal_user),
):
    if kind not in RENDITION_SPECS:
        raise HTTPException(404, "IMAGE_RENDITION_NOT_FOUND")

    with transaction() as conn, conn.cursor() as cur:
        row = _load_file_for_user(
            cur,
            user,
            file_id,
            allow_responsible_btb_images=True,
        )

    if not str(row.get("mime_type") or "").lower().startswith("image/"):
        raise HTTPException(415, "IMAGE_RENDITION_REQUIRES_IMAGE")

    source_path = _absolute_storage_path(row["storage_key"])
    if not source_path.is_file():
        raise HTTPException(404, "FILE_CONTENT_MISSING")

    target_path = rendition_cache_path(source_path, kind)
    if not target_path.is_file():
        try:
            render_image_rendition(source_path, target_path, kind)
        except (OSError, ValueError) as exc:
            raise HTTPException(422, "IMAGE_RENDITION_FAILED") from exc

    return FileResponse(
        target_path,
        media_type="image/jpeg",
        headers={
            "Cache-Control": "private, max-age=31536000, immutable",
            "ETag": f'"{row["sha256"]}-{kind}-{RENDITION_VERSION}"',
        },
    )


# Portal pagination is separate from the full Android/export catalog above.
from .media_gallery import router as gallery_router
router.include_router(gallery_router)


