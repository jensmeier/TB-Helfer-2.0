from __future__ import annotations

import hashlib
import os
import re
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from .config import settings
from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .permissions import require_permission, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["files"])

_MIB = 1024 * 1024
_DEFAULT_CHUNK_SIZE = 8 * _MIB
_MAX_CHUNK_SIZE = 16 * _MIB
_SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9._ -]+")
_ALLOWED_LINK_TYPES = {"WORKSITE", "BTB", "DEFECT", "ACCEPTANCE"}


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
    if write:
        require_permission(context, "files.add")
    return context


def _validate_link(cur, worksite_id: UUID, entity_type: str | None, entity_id: UUID | None) -> tuple[str, UUID] | None:
    if entity_type is None and entity_id is None:
        return None
    if entity_type is None or entity_id is None:
        raise HTTPException(400, "FILE_LINK_INCOMPLETE")
    normalized = entity_type.strip().upper()
    if normalized not in _ALLOWED_LINK_TYPES:
        raise HTTPException(400, "FILE_LINK_TYPE_NOT_SUPPORTED")
    if normalized == "WORKSITE":
        if entity_id != worksite_id:
            raise HTTPException(400, "FILE_LINK_WORKSITE_MISMATCH")
        return normalized, entity_id

    table = {
        "BTB": "btbs",
        "DEFECT": "defects",
        "ACCEPTANCE": "acceptances",
    }[normalized]
    cur.execute(f"SELECT 1 FROM tb.{table} WHERE id = %s AND worksite_id = %s", (entity_id, worksite_id))
    if not cur.fetchone():
        raise HTTPException(404, "FILE_LINK_TARGET_NOT_FOUND")
    return normalized, entity_id


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as src:
        for block in iter(lambda: src.read(_MIB), b""):
            digest.update(block)
    return digest.hexdigest()


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


def _load_file_for_user(cur, user: dict[str, Any], file_id: UUID) -> dict:
    cur.execute(
        """
        SELECT id, company_id, worksite_id, storage_key, original_name, mime_type,
               byte_size, sha256, source, captured_at, gps_lat, gps_lon,
               gps_accuracy_m, created_by, created_at, deleted_at
        FROM tb.files WHERE id = %s
        """,
        (file_id,),
    )
    row = cur.fetchone()
    if not row or row["deleted_at"] is not None:
        raise HTTPException(404, "FILE_NOT_FOUND")
    result = dict(row)
    if result["worksite_id"] is None:
        if user["company_role"] != "ADMIN" and user["company_id"] != result["company_id"]:
            raise HTTPException(403, "FILE_ACCESS_DENIED")
    else:
        worksite_context(cur, user, result["worksite_id"])
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
            if actual_size != upload["received_bytes"]:
                raise HTTPException(409, "UPLOAD_STORAGE_MISMATCH")
            if upload["expected_bytes"] is not None and actual_size != upload["expected_bytes"]:
                raise HTTPException(409, {"code": "UPLOAD_SIZE_MISMATCH", "actual": actual_size, "expected": upload["expected_bytes"]})

            actual_sha256 = _hash_file(temp_path)
            if upload["expected_sha256"] and actual_sha256 != upload["expected_sha256"].lower():
                raise HTTPException(409, {"code": "UPLOAD_SHA256_MISMATCH", "actual": actual_sha256})
            link = _validate_link(cur, upload["worksite_id"], payload.entity_type, payload.entity_id)

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
            cur.execute(
                """
                INSERT INTO tb.files(
                    id, company_id, worksite_id, storage_key, original_name, mime_type,
                    byte_size, sha256, source, captured_at, gps_lat, gps_lon,
                    gps_accuracy_m, created_by
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
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
                    payload.captured_at,
                    payload.gps_lat,
                    payload.gps_lon,
                    payload.gps_accuracy_m,
                    user["user_id"],
                ),
            )
            file_row = dict(cur.fetchone())
            link_payload = None
            if link:
                purpose = payload.purpose.strip().upper()
                cur.execute(
                    "INSERT INTO tb.file_links(file_id, entity_type, entity_id, purpose) VALUES (%s,%s,%s,%s)",
                    (file_id, link[0], link[1], purpose),
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


@router.get("/worksites/{worksite_id}/files")
def list_files(
    worksite_id: UUID,
    limit: int = Query(default=200, ge=1, le=1000),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        worksite_context(cur, user, worksite_id)
        cur.execute(
            """
            SELECT id, company_id, worksite_id, original_name, mime_type, byte_size,
                   sha256, source, captured_at, gps_lat, gps_lon, gps_accuracy_m,
                   created_by, created_at
            FROM tb.files
            WHERE worksite_id = %s AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (worksite_id, limit),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {"files": rows}


@router.get("/files/{file_id}")
def get_file_metadata(
    file_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _load_file_for_user(cur, user, file_id)
        cur.execute(
            "SELECT entity_type, entity_id, purpose, created_at FROM tb.file_links WHERE file_id = %s ORDER BY created_at",
            (file_id,),
        )
        links = [dict(link) for link in cur.fetchall()]
    row.pop("storage_key", None)
    return {"file": row, "links": links}


@router.get("/files/{file_id}/content")
def download_file(
    file_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
):
    with transaction() as conn, conn.cursor() as cur:
        row = _load_file_for_user(cur, user, file_id)
    path = _absolute_storage_path(row["storage_key"])
    if not path.is_file():
        raise HTTPException(404, "FILE_CONTENT_MISSING")
    return FileResponse(
        path,
        media_type=row["mime_type"],
        filename=_safe_name(row["original_name"]),
    )
