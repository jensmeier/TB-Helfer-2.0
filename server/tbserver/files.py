from __future__ import annotations

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

from .btb_documents import prepare_btb_photo_change, remove_storage_keys
from .config import settings
from .database import transaction
from .events import audit, emit_change
from .idempotency import MutationContext, begin_mutation, finish_mutation, mutation_context, replay_payload
from .permissions import area_scope_allows, has_permission, require_permission, worksite_context
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


def _entity_link_visible(
    cur,
    user: dict[str, Any],
    context: dict[str, Any],
    entity_type: str,
    entity_id: UUID,
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


def _file_visible_for_context(
    cur,
    user: dict[str, Any],
    context: dict[str, Any],
    file_id: UUID,
) -> bool:
    cur.execute(
        """
        SELECT entity_type, entity_id
        FROM tb.file_links
        WHERE file_id = %s
        ORDER BY created_at
        """,
        (file_id,),
    )
    links = cur.fetchall()
    if not links:
        return True
    return any(
        _entity_link_visible(cur, user, context, link["entity_type"], link["entity_id"])
        for link in links
    )


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
        context = worksite_context(cur, user, result["worksite_id"])
        if not _file_visible_for_context(cur, user, context, result["id"]):
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
                if link[0] == "BTB" and upload["mime_type"].lower().startswith("image/"):
                    invalidated_pdf_keys.extend(
                        prepare_btb_photo_change(
                            cur,
                            btb_id=link[1],
                            worksite_id=upload["worksite_id"],
                        )
                    )
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
                       created_by, created_at
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
            or _entity_link_visible(cur, user, context, link["entity_type"], link["entity_id"])
        ]
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
