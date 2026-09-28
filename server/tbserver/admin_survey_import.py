from __future__ import annotations

import hashlib
import os
import re
import struct
import zipfile
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from psycopg.types.json import Jsonb

from .admin import require_admin_csrf
from .config import settings
from .database import transaction
from .events import audit, emit_change
from .files import _absolute_storage_path, _safe_name
from .survey_parser import PARSER_VERSION, UnsupportedSurveyFormat, parse_survey_original


router = APIRouter(prefix="/api/v1/admin/survey-import", tags=["admin-survey-import"])

_MAX_UPLOAD_BYTES = 512 * 1024 * 1024
_MAX_ZIP_ENTRY_BYTES = 512 * 1024 * 1024
_MAX_ZIP_TOTAL_BYTES = 1024 * 1024 * 1024
_MAX_ZIP_ENTRIES = 1000
_KIND_RE = re.compile(r"^[A-Z0-9._-]{1,40}$")


def _infer_kind(name: str) -> str | None:
    lower = name.lower()
    if lower.endswith(".tra"):
        return "TRA"
    if lower.endswith(".gra"):
        return "GRA"
    if lower.endswith((".kf.txt", ".kf", ".kv", ".gvp")):
        return "GVP"
    if lower.endswith((".kor.txt", ".kor")):
        return "KOR"
    if lower.endswith(".csv"):
        return "CSV"
    return None


def _skip_reason(name: str) -> str | None:
    lower = name.lower()
    if lower.endswith(".trg"):
        return "TRG erkannt; wird wie in Android derzeit nur gemeldet, noch nicht fachlich importiert."
    if lower.endswith(".pdf"):
        return "PDF erkannt; ist keine Trassierungs-Quelldatei und wird nicht importiert."
    if lower.endswith(".txt") and not lower.endswith((".kf.txt", ".kor.txt")):
        return "TXT erkannt; Inhalt ist keinem unterstützten Trassierungsformat eindeutig zugeordnet."
    return None


def _load_worksite(cur, worksite_id: UUID) -> dict[str, Any]:
    cur.execute(
        """
        SELECT w.id, w.company_id, w.name, w.route_number, c.name AS company_name
        FROM tb.worksites w
        JOIN tb.companies c ON c.id = w.company_id
        WHERE w.id = %s
          AND w.deleted_at IS NULL
          AND c.deleted_at IS NULL
        """,
        (worksite_id,),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "WORKSITE_NOT_FOUND")
    return dict(row)


def _hash_path(path: Path) -> tuple[int, str]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            size += len(block)
            digest.update(block)
    return size, digest.hexdigest()


def _parse_result(kind: str, name: str, path: Path, sha256: str) -> dict[str, Any]:
    data = path.read_bytes()
    try:
        parser_family, detected_format, payload, warnings = parse_survey_original(
            kind, name, data, sha256
        )
        return {
            "status": "PARSED",
            "parser_family": parser_family,
            "detected_format": detected_format,
            "payload": payload,
            "warnings": warnings,
            "error_code": None,
            "error_detail": None,
        }
    except UnsupportedSurveyFormat as exc:
        detail = str(exc)
        return {
            "status": "UNSUPPORTED",
            "parser_family": "UNSUPPORTED",
            "detected_format": None,
            "payload": {},
            "warnings": [],
            "error_code": detail.split(":", 1)[0][:120],
            "error_detail": detail[:2000],
        }
    except (ValueError, struct.error, UnicodeError) as exc:
        detail = str(exc)
        return {
            "status": "ERROR",
            "parser_family": "ERROR",
            "detected_format": None,
            "payload": {},
            "warnings": [],
            "error_code": (detail.split(":", 1)[0] or "PARSE_ERROR")[:120],
            "error_detail": detail[:2000],
        }


def _install_original(
    *,
    path: Path,
    original_name: str,
    kind: str,
    worksite_id: UUID,
    route_number: str | None,
    track_key: str | None,
    user: dict[str, Any],
) -> dict[str, Any]:
    normalized_kind = kind.strip().upper()
    if not _KIND_RE.fullmatch(normalized_kind):
        raise HTTPException(400, "INVALID_SURVEY_DATA_KIND")
    size, sha256 = _hash_path(path)
    if size <= 0:
        return {
            "name": original_name,
            "kind": normalized_kind,
            "status": "FEHLER",
            "message": "Datei ist leer.",
        }

    parsed = _parse_result(normalized_kind, original_name, path, sha256)
    final_path: Path | None = None
    moved = False
    try:
        with transaction() as conn, conn.cursor() as cur:
            worksite = _load_worksite(cur, worksite_id)
            effective_route = (route_number or worksite.get("route_number") or "").strip() or None
            effective_track = (track_key or "").strip() or None

            cur.execute(
                """
                SELECT id, original_name, imported_at
                FROM tb.survey_originals
                WHERE company_id = %s
                  AND sha256 = %s
                  AND byte_size = %s
                LIMIT 1
                """,
                (worksite["company_id"], sha256, size),
            )
            duplicate = cur.fetchone()
            if duplicate:
                return {
                    "name": original_name,
                    "kind": normalized_kind,
                    "status": "DUPLIKAT",
                    "message": "Dieses unveränderte Original ist für die Firma bereits vorhanden.",
                    "original_id": str(duplicate["id"]),
                    "parse_status": None,
                }

            original_id = uuid4()
            safe_name = _safe_name(original_name)
            storage_key = f"reference/survey-originals/{original_id}/{safe_name}"
            final_path = _absolute_storage_path(storage_key)
            final_path.parent.mkdir(parents=True, exist_ok=True)
            os.replace(path, final_path)
            moved = True

            metadata = {
                "source": "ADMIN_WEB",
                "worksite_id": str(worksite_id),
                "worksite_name": worksite["name"],
            }
            cur.execute(
                """
                INSERT INTO tb.survey_originals(
                    id, company_id, route_number, track_key, data_kind, storage_key,
                    original_name, sha256, byte_size, metadata, imported_by
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING imported_at
                """,
                (
                    original_id,
                    worksite["company_id"],
                    effective_route,
                    effective_track,
                    normalized_kind,
                    storage_key,
                    original_name,
                    sha256,
                    size,
                    Jsonb(metadata),
                    user["user_id"],
                ),
            )
            imported_at = cur.fetchone()["imported_at"]

            cur.execute(
                """
                INSERT INTO tb.survey_parse_catalog(
                    original_id, parser_family, parser_version, source_sha256, status,
                    detected_format, payload, warnings, error_code, error_detail,
                    parsed_by, parsed_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now())
                ON CONFLICT (original_id) DO UPDATE SET
                    parser_family=EXCLUDED.parser_family,
                    parser_version=EXCLUDED.parser_version,
                    source_sha256=EXCLUDED.source_sha256,
                    status=EXCLUDED.status,
                    detected_format=EXCLUDED.detected_format,
                    payload=EXCLUDED.payload,
                    warnings=EXCLUDED.warnings,
                    error_code=EXCLUDED.error_code,
                    error_detail=EXCLUDED.error_detail,
                    parsed_by=EXCLUDED.parsed_by,
                    parsed_at=now()
                """,
                (
                    original_id,
                    parsed["parser_family"],
                    PARSER_VERSION,
                    sha256,
                    parsed["status"],
                    parsed["detected_format"],
                    Jsonb(parsed["payload"]),
                    Jsonb(parsed["warnings"]),
                    parsed["error_code"],
                    parsed["error_detail"],
                    user["user_id"],
                ),
            )

            emit_change(
                cur,
                company_id=worksite["company_id"],
                worksite_id=worksite_id,
                entity_type="SURVEY_ORIGINAL",
                entity_id=original_id,
                operation="IMPORT",
                entity_version=None,
                actor_user_id=user["user_id"],
            )
            emit_change(
                cur,
                company_id=worksite["company_id"],
                worksite_id=worksite_id,
                entity_type="SURVEY_PARSE_CATALOG",
                entity_id=original_id,
                operation="UPSERT",
                entity_version=None,
                actor_user_id=user["user_id"],
            )
            audit(
                cur,
                action="ADMIN_SURVEY_ORIGINAL_IMPORT",
                actor_user_id=user["user_id"],
                company_id=worksite["company_id"],
                worksite_id=worksite_id,
                entity_type="SURVEY_ORIGINAL",
                entity_id=original_id,
                details={
                    "data_kind": normalized_kind,
                    "sha256": sha256,
                    "byte_size": size,
                    "parse_status": parsed["status"],
                    "source": "ADMIN_WEB",
                },
            )

        moved = False
        message = {
            "PARSED": "Original gespeichert und fachlich ausgewertet.",
            "UNSUPPORTED": "Original gespeichert; Format erkannt, aber noch nicht fachlich auswertbar.",
            "ERROR": "Original gespeichert; bei der fachlichen Auswertung ist ein Fehler aufgetreten.",
        }.get(parsed["status"], "Original gespeichert.")
        return {
            "name": original_name,
            "kind": normalized_kind,
            "status": "IMPORTIERT",
            "message": message,
            "original_id": str(original_id),
            "imported_at": imported_at,
            "byte_size": size,
            "sha256": sha256,
            "parse_status": parsed["status"],
            "detected_format": parsed["detected_format"],
            "warnings": parsed["warnings"],
            "error_detail": parsed["error_detail"],
        }
    except Exception:
        if moved and final_path is not None and final_path.exists():
            path.parent.mkdir(parents=True, exist_ok=True)
            os.replace(final_path, path)
        raise


async def _stream_request_to_temp(request: Request) -> Path:
    temp_dir = settings.storage_root / ".admin-survey-import"
    temp_dir.mkdir(parents=True, exist_ok=True)
    temp_path = temp_dir / f"{uuid4()}.part"
    size = 0
    try:
        with temp_path.open("wb") as target:
            async for chunk in request.stream():
                if not chunk:
                    continue
                size += len(chunk)
                if size > _MAX_UPLOAD_BYTES:
                    raise HTTPException(413, "SURVEY_IMPORT_FILE_TOO_LARGE")
                target.write(chunk)
            target.flush()
            os.fsync(target.fileno())
        if size == 0:
            raise HTTPException(400, "SURVEY_IMPORT_FILE_EMPTY")
        return temp_path
    except Exception:
        temp_path.unlink(missing_ok=True)
        raise


def _extract_zip_entry(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> Path:
    if info.file_size > _MAX_ZIP_ENTRY_BYTES:
        raise HTTPException(413, "SURVEY_IMPORT_ZIP_ENTRY_TOO_LARGE")
    temp_dir = settings.storage_root / ".admin-survey-import"
    temp_dir.mkdir(parents=True, exist_ok=True)
    temp_path = temp_dir / f"{uuid4()}.entry"
    size = 0
    try:
        with archive.open(info, "r") as source, temp_path.open("wb") as target:
            while True:
                block = source.read(1024 * 1024)
                if not block:
                    break
                size += len(block)
                if size > _MAX_ZIP_ENTRY_BYTES:
                    raise HTTPException(413, "SURVEY_IMPORT_ZIP_ENTRY_TOO_LARGE")
                target.write(block)
            target.flush()
            os.fsync(target.fileno())
        return temp_path
    except Exception:
        temp_path.unlink(missing_ok=True)
        raise


@router.post("/file")
async def import_survey_file(
    request: Request,
    worksite_id: UUID = Query(...),
    filename: str = Query(..., min_length=1, max_length=255),
    data_kind: str | None = Query(default=None, max_length=40),
    route_number: str | None = Query(default=None, max_length=80),
    track_key: str | None = Query(default=None, max_length=160),
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    temp_path = await _stream_request_to_temp(request)
    try:
        safe_name = _safe_name(filename)
        if safe_name.lower().endswith(".zip"):
            results: list[dict[str, Any]] = []
            try:
                with zipfile.ZipFile(temp_path, "r") as archive:
                    infos = [info for info in archive.infolist() if not info.is_dir()]
                    if len(infos) > _MAX_ZIP_ENTRIES:
                        raise HTTPException(413, "SURVEY_IMPORT_ZIP_TOO_MANY_FILES")
                    if sum(max(0, info.file_size) for info in infos) > _MAX_ZIP_TOTAL_BYTES:
                        raise HTTPException(413, "SURVEY_IMPORT_ZIP_UNPACKED_TOO_LARGE")
                    for info in infos:
                        entry_name = _safe_name(Path(info.filename).name)
                        kind = _infer_kind(entry_name)
                        if kind is None:
                            reason = _skip_reason(entry_name)
                            if reason:
                                results.append({
                                    "name": entry_name,
                                    "kind": None,
                                    "status": "ÜBERSPRUNGEN",
                                    "message": reason,
                                })
                            continue
                        entry_path = _extract_zip_entry(archive, info)
                        try:
                            result = _install_original(
                                path=entry_path,
                                original_name=entry_name,
                                kind=kind,
                                worksite_id=worksite_id,
                                route_number=route_number,
                                track_key=track_key,
                                user=user,
                            )
                            results.append(result)
                        finally:
                            entry_path.unlink(missing_ok=True)
            except zipfile.BadZipFile as exc:
                raise HTTPException(400, "SURVEY_IMPORT_ZIP_INVALID") from exc
            if not results:
                raise HTTPException(400, "SURVEY_IMPORT_ZIP_NO_RECOGNIZED_FILES")
            return {"bundle": safe_name, "results": results}

        kind = (data_kind or _infer_kind(safe_name) or "").strip().upper()
        if not kind:
            reason = _skip_reason(safe_name)
            raise HTTPException(400, reason or "SURVEY_IMPORT_FILE_TYPE_NOT_SUPPORTED")
        result = _install_original(
            path=temp_path,
            original_name=safe_name,
            kind=kind,
            worksite_id=worksite_id,
            route_number=route_number,
            track_key=track_key,
            user=user,
        )
        return {"results": [result]}
    finally:
        temp_path.unlink(missing_ok=True)
