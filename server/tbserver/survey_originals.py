from __future__ import annotations

import os
import re
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .files import _absolute_storage_path, _hash_file, _safe_name
from .permissions import require_permission, worksite_context
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["survey-originals"])
_DATA_KIND_RE = re.compile(r"^[A-Z0-9._-]{1,40}$")


class SurveyOriginalFromFile(BaseModel):
    file_id: UUID
    route_number: str | None = Field(default=None, max_length=80)
    track_key: str | None = Field(default=None, max_length=160)
    data_kind: str = Field(min_length=1, max_length=40)
    metadata: dict = Field(default_factory=dict)


def _load_original(cur, original_id: UUID) -> dict:
    cur.execute("SELECT * FROM tb.survey_originals WHERE id = %s", (original_id,))
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "SURVEY_ORIGINAL_NOT_FOUND")
    return dict(row)


def _assert_original_access(user: dict[str, Any], original: dict) -> None:
    if user["company_role"] == "ADMIN":
        return
    if original["company_id"] is None:
        return
    if original["company_id"] != user["company_id"]:
        raise HTTPException(403, "SURVEY_ORIGINAL_ACCESS_DENIED")


@router.get("/survey-originals")
def list_survey_originals(
    route_number: str | None = Query(default=None, max_length=80),
    track_key: str | None = Query(default=None, max_length=160),
    data_kind: str | None = Query(default=None, max_length=40),
    limit: int = Query(default=200, ge=1, le=1000),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    where = ["(company_id IS NULL OR company_id = %s)"]
    params: list[Any] = [user["company_id"]]
    if user["company_role"] == "ADMIN":
        where = ["TRUE"]
        params = []
    if route_number:
        where.append("route_number = %s")
        params.append(route_number)
    if track_key:
        where.append("track_key = %s")
        params.append(track_key)
    if data_kind:
        where.append("data_kind = %s")
        params.append(data_kind.strip().upper())
    params.append(limit)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT id, company_id, route_number, track_key, data_kind, original_name,
                   sha256, byte_size, metadata, imported_by, imported_at
            FROM tb.survey_originals
            WHERE {' AND '.join(where)}
            ORDER BY imported_at DESC
            LIMIT %s
            """,
            tuple(params),
        )
        rows = [dict(row) for row in cur.fetchall()]
    for row in rows:
        row["scope"] = "GLOBAL_REFERENCE" if row["company_id"] is None else "COMPANY_WORKSITE_DATA"
    return {"survey_originals": rows}



@router.get("/reference/survey-originals")
def list_global_reference_originals(
    route_number: str | None = Query(default=None, max_length=80),
    track_key: str | None = Query(default=None, max_length=160),
    data_kind: str | None = Query(default=None, max_length=40),
    limit: int = Query(default=500, ge=1, le=2000),
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    where = ["o.company_id IS NULL"]
    params: list[Any] = []
    if route_number:
        where.append("o.route_number = %s")
        params.append(route_number)
    if track_key:
        where.append("o.track_key = %s")
        params.append(track_key)
    if data_kind:
        where.append("o.data_kind = %s")
        params.append(data_kind.strip().upper())

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT count(*) AS item_count,
                   max(greatest(o.imported_at, coalesce(p.parsed_at, o.imported_at))) AS latest_changed_at
            FROM tb.survey_originals o
            LEFT JOIN tb.survey_parse_catalog p ON p.original_id = o.id
            WHERE {' AND '.join(where)}
            """,
            tuple(params),
        )
        revision = dict(cur.fetchone())
        cur.execute(
            f"""
            SELECT o.id, o.route_number, o.track_key, o.data_kind, o.original_name,
                   o.sha256, o.byte_size, o.metadata, o.imported_at,
                   p.status AS parse_status, p.parser_family, p.parser_version,
                   p.detected_format, p.parsed_at,
                   CASE
                     WHEN p.warnings IS NULL THEN 0
                     WHEN jsonb_typeof(p.warnings) = 'array' THEN jsonb_array_length(p.warnings)
                     ELSE 0
                   END AS warning_count
            FROM tb.survey_originals o
            LEFT JOIN tb.survey_parse_catalog p ON p.original_id = o.id
            WHERE {' AND '.join(where)}
            ORDER BY greatest(o.imported_at, coalesce(p.parsed_at, o.imported_at)) DESC, o.id
            LIMIT %s
            """,
            tuple(params + [limit]),
        )
        rows = [dict(row) for row in cur.fetchall()]

    for row in rows:
        row["scope"] = "GLOBAL_REFERENCE"
    return {
        "scope": "GLOBAL_REFERENCE",
        "item_count": int(revision.get("item_count") or 0),
        "latest_changed_at": revision.get("latest_changed_at"),
        "survey_originals": rows,
    }


@router.get("/reference/survey-originals/{original_id}/parsed")
def get_global_reference_parsed(
    original_id: UUID,
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT o.id, o.route_number, o.track_key, o.data_kind, o.original_name,
                   o.sha256, o.byte_size, o.metadata, o.imported_at,
                   p.status AS parse_status, p.parser_family, p.parser_version,
                   p.detected_format, p.payload, p.warnings, p.error_code,
                   p.error_detail, p.parsed_at
            FROM tb.survey_originals o
            LEFT JOIN tb.survey_parse_catalog p ON p.original_id = o.id
            WHERE o.id = %s AND o.company_id IS NULL
            """,
            (original_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "GLOBAL_SURVEY_REFERENCE_NOT_FOUND")
        result = dict(row)
    result["scope"] = "GLOBAL_REFERENCE"
    return result


@router.post("/survey-originals/from-file", status_code=201)
def import_survey_original_from_file(
    payload: SurveyOriginalFromFile,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    kind = payload.data_kind.strip().upper()
    if not _DATA_KIND_RE.fullmatch(kind):
        raise HTTPException(400, "INVALID_SURVEY_DATA_KIND")

    old_path = None
    new_path = None
    moved = False
    try:
        with transaction() as conn, conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, company_id, worksite_id, storage_key, original_name, mime_type,
                       byte_size, sha256, source, created_by, created_at, deleted_at
                FROM tb.files WHERE id = %s FOR UPDATE
                """,
                (payload.file_id,),
            )
            file_row = cur.fetchone()
            if not file_row or file_row["deleted_at"] is not None:
                raise HTTPException(404, "FILE_NOT_FOUND")
            if file_row["worksite_id"] is None:
                raise HTTPException(409, "SURVEY_IMPORT_REQUIRES_WORKSITE_FILE")

            context = worksite_context(cur, user, file_row["worksite_id"])
            require_permission(context, "track.edit")
            if file_row["company_id"] != context["company_id"]:
                raise HTTPException(403, "FILE_COMPANY_MISMATCH")

            old_path = _absolute_storage_path(file_row["storage_key"])
            if not old_path.is_file():
                raise HTTPException(404, "FILE_CONTENT_MISSING")
            actual_size = old_path.stat().st_size
            actual_sha256 = _hash_file(old_path)
            if actual_size != file_row["byte_size"] or actual_sha256 != file_row["sha256"]:
                raise HTTPException(409, "SOURCE_FILE_INTEGRITY_MISMATCH")

            cur.execute(
                """
                SELECT id FROM tb.survey_originals
                WHERE company_id = %s AND sha256 = %s AND byte_size = %s AND data_kind = %s
                LIMIT 1
                """,
                (context["company_id"], actual_sha256, actual_size, kind),
            )
            duplicate = cur.fetchone()
            if duplicate:
                raise HTTPException(409, {"code": "SURVEY_ORIGINAL_ALREADY_IMPORTED", "original_id": duplicate["id"]})

            original_id = uuid4()
            safe_name = _safe_name(file_row["original_name"])
            storage_key = f"reference/survey-originals/{original_id}/{safe_name}"
            new_path = _absolute_storage_path(storage_key)
            new_path.parent.mkdir(parents=True, exist_ok=True)
            os.replace(old_path, new_path)
            moved = True

            cur.execute(
                "UPDATE tb.files SET storage_key = %s, source = 'IMPORT' WHERE id = %s",
                (storage_key, payload.file_id),
            )
            cur.execute(
                """
                INSERT INTO tb.survey_originals(
                    id, company_id, route_number, track_key, data_kind, storage_key,
                    original_name, sha256, byte_size, metadata, imported_by
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (
                    original_id,
                    context["company_id"],
                    payload.route_number,
                    payload.track_key,
                    kind,
                    storage_key,
                    file_row["original_name"] or safe_name,
                    actual_sha256,
                    actual_size,
                    Jsonb(payload.metadata),
                    user["user_id"],
                ),
            )
            row = dict(cur.fetchone())
            emit_change(
                cur,
                company_id=context["company_id"],
                worksite_id=file_row["worksite_id"],
                entity_type="SURVEY_ORIGINAL",
                entity_id=original_id,
                operation="IMPORT",
                entity_version=None,
                actor_user_id=user["user_id"],
            )
            audit(
                cur,
                action="SURVEY_ORIGINAL_IMPORT",
                actor_user_id=user["user_id"],
                company_id=context["company_id"],
                worksite_id=file_row["worksite_id"],
                entity_type="SURVEY_ORIGINAL",
                entity_id=original_id,
                details={"data_kind": kind, "sha256": actual_sha256, "byte_size": actual_size, "source_file_id": str(payload.file_id)},
            )
        return row
    except Exception:
        if moved and new_path is not None and new_path.exists() and old_path is not None:
            old_path.parent.mkdir(parents=True, exist_ok=True)
            os.replace(new_path, old_path)
        raise


@router.get("/survey-originals/{original_id}")
def get_survey_original(
    original_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _load_original(cur, original_id)
        _assert_original_access(user, row)
    row.pop("storage_key", None)
    return row


@router.get("/survey-originals/{original_id}/content")
def download_survey_original(
    original_id: UUID,
    user: dict[str, Any] = Depends(current_portal_user),
):
    with transaction() as conn, conn.cursor() as cur:
        row = _load_original(cur, original_id)
        _assert_original_access(user, row)
    path = _absolute_storage_path(row["storage_key"])
    if not path.is_file():
        raise HTTPException(404, "SURVEY_ORIGINAL_CONTENT_MISSING")
    return FileResponse(path, filename=_safe_name(row["original_name"]))
