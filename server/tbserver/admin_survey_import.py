from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import struct
import time
import zipfile
from datetime import date
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from psycopg.types.json import Jsonb
from pydantic import BaseModel, Field

from .admin import require_admin_csrf
from .config import settings
from .database import transaction
from .events import audit, emit_change
from .files import _absolute_storage_path, _safe_name
from .survey_parser import PARSER_VERSION, UnsupportedSurveyFormat, parse_survey_original
from .survey_catalog import index_original_in_tx, refresh_original_assignment_in_tx
from .survey_preview import build_survey_preview


router = APIRouter(prefix="/api/v1/admin/survey-import", tags=["admin-survey-import"])

_MAX_UPLOAD_BYTES = 512 * 1024 * 1024
_MAX_ZIP_ENTRY_BYTES = 512 * 1024 * 1024
_MAX_ZIP_TOTAL_BYTES = 1024 * 1024 * 1024
_MAX_ZIP_ENTRIES = 1000
_KIND_RE = re.compile(r"^[A-Z0-9._-]{1,40}$")
_SCOPE_GLOBAL = "GLOBAL_REFERENCE"
_SCOPE_WORKSITE = "COMPANY_WORKSITE_DATA"
_ALLOWED_SCOPES = {_SCOPE_GLOBAL, _SCOPE_WORKSITE}
_ANALYSIS_TTL_SECONDS = 6 * 60 * 60


class SurveyImportDecision(BaseModel):
    item_id: str = Field(min_length=1, max_length=80)
    import_item: bool = True
    scope: str = Field(default=_SCOPE_GLOBAL, max_length=40)
    worksite_id: UUID | None = None
    data_kind: str | None = Field(default=None, max_length=40)
    route_number: str | None = Field(default=None, max_length=80)
    track_key: str | None = Field(default=None, max_length=160)
    object_key: str | None = Field(default=None, max_length=160)
    station_code: str | None = Field(default=None, max_length=50)
    station_name: str | None = Field(default=None, max_length=240)
    source_date: date | None = None
    overwrite_existing: bool = False


class SurveyImportPlan(BaseModel):
    decisions: list[SurveyImportDecision] = Field(min_length=1, max_length=_MAX_ZIP_ENTRIES)


def _normalize_scope(value: str | None) -> str:
    scope = (value or _SCOPE_WORKSITE).strip().upper()
    if scope not in _ALLOWED_SCOPES:
        raise HTTPException(400, "SURVEY_REFERENCE_SCOPE_INVALID")
    return scope


def _parsed_route_track(parsed: dict[str, Any]) -> tuple[str | None, str | None]:
    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    route = str(payload.get("route_from_name") or "").strip() or None
    track = str(payload.get("track_from_name") or "").strip() or None
    points = payload.get("points")
    if isinstance(points, list):
        for raw in points:
            if not isinstance(raw, dict):
                continue
            if route is None:
                route = str(raw.get("route") or "").strip() or None
            if track is None:
                track = str(raw.get("track") or "").strip() or None
            if route is not None and track is not None:
                break
    return route, track


def _analysis_root() -> Path:
    root = settings.storage_root / ".admin-survey-analysis"
    root.mkdir(parents=True, exist_ok=True)
    return root


def _cleanup_analysis_sessions() -> None:
    root = _analysis_root()
    cutoff = time.time() - _ANALYSIS_TTL_SECONDS
    try:
        entries = list(root.iterdir())
    except OSError:
        return
    for entry in entries:
        try:
            if entry.is_dir() and entry.stat().st_mtime < cutoff:
                shutil.rmtree(entry, ignore_errors=True)
        except OSError:
            continue


def _analysis_dir(analysis_id: UUID) -> Path:
    return _analysis_root() / str(analysis_id)


def _load_analysis_meta(analysis_id: UUID) -> tuple[Path, dict[str, Any]]:
    root = _analysis_dir(analysis_id)
    meta_path = root / "meta.json"
    source_path = root / "source.bin"
    if not meta_path.is_file() or not source_path.is_file():
        raise HTTPException(404, "SURVEY_ANALYSIS_NOT_FOUND")
    try:
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise HTTPException(409, "SURVEY_ANALYSIS_INVALID") from exc
    if time.time() - float(meta.get("created_at_epoch") or 0.0) > _ANALYSIS_TTL_SECONDS:
        shutil.rmtree(root, ignore_errors=True)
        raise HTTPException(410, "SURVEY_ANALYSIS_EXPIRED")
    return source_path, meta


def _analysis_semantics(
    parsed: dict[str, Any],
    original_name: str,
    kind: str,
) -> dict[str, Any]:
    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    route = str(payload.get("route_from_name") or "").strip() or None
    track = str(payload.get("track_from_name") or "").strip() or None
    direction = str(payload.get("direction_from_name") or "").strip() or None
    source_direction = str(payload.get("source_direction_from_name") or "").strip() or None
    axis_type = str(payload.get("axis_type_from_name") or "").strip().upper() or None
    object_key = str(payload.get("object_key_from_name") or "").strip().upper() or None
    switch_a = str(payload.get("switch_a_from_name") or "").strip().upper() or None
    switch_b = str(payload.get("switch_b_from_name") or "").strip().upper() or None
    basename = Path(original_name).name
    upper = basename.upper()

    if axis_type == "WEICHENVERBINDUNG":
        if object_key and object_key.startswith("WVB:"):
            display_label = "WVB " + object_key.split(":", 1)[1]
            semantic_key = object_key
        else:
            display_label = re.sub(r"[_]+", " ", Path(basename).stem).split("_ER0", 1)[0].strip()
            semantic_key = f"WVB:{display_label.upper()}"
        object_group = "WEICHENVERBINDUNG"
    elif axis_type == "WEICHE":
        display_label = f"W {switch_a}" if switch_a else re.sub(r"[_]+", " ", Path(basename).stem).split("_ER0", 1)[0].strip()
        semantic_key = object_key or f"WEICHE:{display_label.upper()}"
        object_group = "WEICHE"
    elif route and source_direction:
        direction_label = direction or source_direction
        display_label = f"Str {route}-{direction_label}"
        if source_direction and source_direction != direction_label:
            display_label += f" · Quelle {source_direction}"
        semantic_key = f"STRECKE:{route}:{source_direction}"
        object_group = "STRECKENACHSE"
    elif axis_type == "GLEIS" and track:
        display_label = f"Gl {track}" + (f" · Str {route}" if route else "")
        semantic_key = f"GLEIS:{route or ''}:{track}"
        object_group = "BAHNHOFS_GLEIS"
    elif route:
        display_label = f"Str {route}"
        semantic_key = f"STRECKE:{route}:{axis_type or 'UNBEKANNT'}"
        object_group = "STRECKENACHSE"
    elif kind == "GVP":
        normalized = basename.lower()
        if normalized.endswith(".kf.txt"):
            normalized = normalized[:-7]
        elif "." in normalized:
            normalized = normalized.rsplit(".", 1)[0]
        display_label = "Festpunkte / KF"
        semantic_key = f"POINT:GVP:{normalized}"
        object_group = "FESTPUNKTE"
    elif kind in {"KOR", "CSV"}:
        display_label = "Absteckpunkte"
        semantic_key = f"POINT:{kind}:{Path(basename).stem.lower()}"
        object_group = "ABSTECKPUNKTE"
    else:
        display_label = Path(basename).stem
        semantic_key = f"FILE:{basename.lower()}"
        object_group = "DATEI"

    return {
        "axis_type": axis_type,
        "direction": direction,
        "source_direction": source_direction,
        "object_group": object_group,
        "display_label": display_label,
        "semantic_key": semantic_key,
        "object_key": object_key,
        "switch_a": switch_a,
        "switch_b": switch_b,
    }


def _analysis_item(
    *,
    item_id: str,
    path: Path,
    original_name: str,
    kind: str,
    archive_index: int | None = None,
) -> dict[str, Any]:
    size, sha256 = _hash_path(path)
    parsed = _parse_result(kind, original_name, path, sha256)
    route, track = _parsed_route_track(parsed)
    semantics = _analysis_semantics(parsed, original_name, kind)
    preview = build_survey_preview(parsed)
    missing: list[str] = []
    if not route:
        missing.append("route_number")
    return {
        "item_id": item_id,
        "archive_index": archive_index,
        "name": original_name,
        "kind": kind,
        "byte_size": size,
        "sha256": sha256,
        "parse_status": parsed["status"],
        "parser_family": parsed["parser_family"],
        "detected_format": parsed["detected_format"],
        "route_number": route,
        "track_key": track,
        **semantics,
        "default_scope": _SCOPE_GLOBAL,
        "missing_fields": missing,
        "warnings": parsed["warnings"],
        "error_code": parsed["error_code"],
        "error_detail": parsed["error_detail"],
        "preview": preview,
        "can_import": parsed["status"] in {"PARSED", "UNSUPPORTED", "ERROR"},
    }


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
        SELECT w.id, w.company_id, w.name, w.route_number, w.station_code, w.station_name,
               c.name AS company_name
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
    worksite_id: UUID | None,
    scope: str,
    route_number: str | None,
    track_key: str | None,
    object_key: str | None,
    station_code: str | None,
    station_name: str | None,
    source_date: date | None,
    overwrite_existing: bool = False,
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
            normalized_scope = _normalize_scope(scope)
            inferred_route, inferred_track = _parsed_route_track(parsed)
            effective_station_code = (station_code or "").strip().upper() or None
            effective_station_name = (station_name or "").strip() or None
            effective_object_key = (
                (object_key or "").strip().upper()
                or str(parsed.get("payload", {}).get("object_key_from_name") or "").strip().upper()
                or None
            )
            worksite: dict[str, Any] | None = None
            if normalized_scope == _SCOPE_WORKSITE:
                if worksite_id is None:
                    raise HTTPException(400, "SURVEY_IMPORT_WORKSITE_REQUIRED")
                worksite = _load_worksite(cur, worksite_id)
                company_id = worksite["company_id"]
                effective_route = (
                    route_number
                    or worksite.get("route_number")
                    or inferred_route
                    or ""
                ).strip() or None
                effective_track = (track_key or inferred_track or "").strip() or None
                effective_station_code = effective_station_code or (str(worksite.get("station_code") or "").strip().upper() or None)
                effective_station_name = effective_station_name or (str(worksite.get("station_name") or "").strip() or None)
                cur.execute(
                    """
                    SELECT id, original_name, imported_at, route_number, track_key, data_kind, metadata
                    FROM tb.survey_originals
                    WHERE company_id = %s
                      AND sha256 = %s
                      AND byte_size = %s
                    LIMIT 1
                    """,
                    (company_id, sha256, size),
                )
                duplicate_message = "Dieses unveränderte Original ist für die Firma bereits vorhanden."
            else:
                company_id = None
                effective_route = (route_number or inferred_route or "").strip() or None
                effective_track = (track_key or inferred_track or "").strip() or None
                cur.execute(
                    """
                    SELECT id, original_name, imported_at, route_number, track_key, data_kind, metadata
                    FROM tb.survey_originals
                    WHERE company_id IS NULL
                      AND sha256 = %s
                      AND byte_size = %s
                    LIMIT 1
                    """,
                    (sha256, size),
                )
                duplicate_message = "Dieses unveränderte Original ist im globalen Referenzbestand bereits vorhanden."

            duplicate = cur.fetchone()
            if duplicate:
                duplicate = dict(duplicate)
                existing_metadata = (
                    dict(duplicate.get("metadata"))
                    if isinstance(duplicate.get("metadata"), dict)
                    else {}
                )
                existing_assignment = {
                    "route_number": (str(duplicate.get("route_number") or "").strip() or None),
                    "track_key": (str(duplicate.get("track_key") or "").strip() or None),
                    "object_key": (str(existing_metadata.get("object_key") or "").strip().upper() or None),
                    "station_code": (str(existing_metadata.get("station_code") or "").strip().upper() or None),
                    "station_name": (str(existing_metadata.get("station_name") or "").strip() or None),
                    "source_date": existing_metadata.get("source_date"),
                }
                requested_assignment = {
                    "route_number": effective_route,
                    "track_key": effective_track,
                    "object_key": effective_object_key,
                    "station_code": effective_station_code,
                    "station_name": effective_station_name,
                    "source_date": (
                        source_date.isoformat()
                        if source_date is not None
                        else existing_assignment.get("source_date")
                    ),
                }
                changes = {
                    key: {"before": existing_assignment.get(key), "after": value}
                    for key, value in requested_assignment.items()
                    if existing_assignment.get(key) != value
                }
                if not changes:
                    return {
                        "name": original_name,
                        "kind": normalized_kind,
                        "scope": normalized_scope,
                        "status": "DUPLIKAT",
                        "message": duplicate_message,
                        "original_id": str(duplicate["id"]),
                        "parse_status": None,
                    }
                if not overwrite_existing:
                    return {
                        "name": original_name,
                        "kind": normalized_kind,
                        "scope": normalized_scope,
                        "status": "VORHANDEN_ABWEICHEND",
                        "message": (
                            "Datei ist bereits vorhanden, aber die fachliche Zuordnung weicht ab. "
                            "Vorhandene Zuordnung nur nach ausdrücklicher Bestätigung aktualisieren."
                        ),
                        "original_id": str(duplicate["id"]),
                        "parse_status": None,
                        "can_overwrite": True,
                        "changes": changes,
                        "station_code": effective_station_code,
                        "station_name": effective_station_name,
                    }

                updated_metadata = dict(existing_metadata)
                updated_metadata["source"] = updated_metadata.get("source") or "ADMIN_WEB"
                updated_metadata["scope"] = normalized_scope
                for key in ("object_key", "station_code", "station_name"):
                    value = requested_assignment.get(key)
                    if value is None:
                        updated_metadata.pop(key, None)
                    else:
                        updated_metadata[key] = value
                if requested_assignment.get("source_date") is None:
                    updated_metadata.pop("source_date", None)
                else:
                    updated_metadata["source_date"] = requested_assignment["source_date"]

                cur.execute(
                    """
                    UPDATE tb.survey_originals
                    SET route_number=%s,track_key=%s,metadata=%s
                    WHERE id=%s
                    RETURNING *
                    """,
                    (
                        requested_assignment["route_number"],
                        requested_assignment["track_key"],
                        Jsonb(updated_metadata),
                        duplicate["id"],
                    ),
                )
                updated_original = dict(cur.fetchone())
                catalog_result = refresh_original_assignment_in_tx(
                    cur,
                    duplicate["id"],
                    user["user_id"],
                )
                audit(
                    cur,
                    action="ADMIN_SURVEY_ORIGINAL_ASSIGNMENT_UPDATE",
                    actor_user_id=user["user_id"],
                    company_id=company_id or user.get("company_id"),
                    worksite_id=worksite_id if worksite is not None else None,
                    entity_type="SURVEY_ORIGINAL",
                    entity_id=duplicate["id"],
                    details={
                        "changes": changes,
                        "sha256": sha256,
                        "byte_size": size,
                        "catalog_status": catalog_result.get("status"),
                        "source": "ADMIN_WEB_DUPLICATE_CORRECTION",
                    },
                )
                return {
                    "name": original_name,
                    "kind": normalized_kind,
                    "scope": normalized_scope,
                    "route_number": updated_original.get("route_number"),
                    "track_key": updated_original.get("track_key"),
                    "object_key": requested_assignment.get("object_key"),
                    "station_code": requested_assignment.get("station_code"),
                    "station_name": requested_assignment.get("station_name"),
                    "source_date": requested_assignment.get("source_date"),
                    "catalog": catalog_result,
                    "status": "AKTUALISIERT",
                    "message": "Vorhandenes Original beibehalten; fachliche Zuordnung wurde aktualisiert.",
                    "original_id": str(duplicate["id"]),
                    "imported_at": duplicate.get("imported_at"),
                    "byte_size": size,
                    "sha256": sha256,
                    "parse_status": parsed["status"],
                    "detected_format": parsed["detected_format"],
                    "warnings": parsed["warnings"],
                    "error_detail": parsed["error_detail"],
                    "changes": changes,
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
                "scope": normalized_scope,
            }
            if effective_object_key:
                metadata["object_key"] = effective_object_key
            if effective_station_code:
                metadata["station_code"] = effective_station_code
            if effective_station_name:
                metadata["station_name"] = effective_station_name
            if source_date is not None:
                metadata["source_date"] = source_date.isoformat()
            if worksite is not None and worksite_id is not None:
                metadata.update(
                    {
                        "worksite_id": str(worksite_id),
                        "worksite_name": worksite["name"],
                    }
                )
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
                    company_id,
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

            catalog_result = index_original_in_tx(cur, original_id, user["user_id"])

            if worksite is not None and worksite_id is not None:
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
                company_id=company_id or user.get("company_id"),
                worksite_id=worksite_id if worksite is not None else None,
                entity_type="SURVEY_ORIGINAL",
                entity_id=original_id,
                details={
                    "scope": normalized_scope,
                    "data_kind": normalized_kind,
                    "sha256": sha256,
                    "byte_size": size,
                    "parse_status": parsed["status"],
                    "route_number": effective_route,
                    "track_key": effective_track,
                    "object_key": effective_object_key,
                    "station_code": effective_station_code,
                    "station_name": effective_station_name,
                    "source_date": metadata.get("source_date"),
                    "catalog_status": catalog_result.get("status"),
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
            "scope": normalized_scope,
            "route_number": effective_route,
            "track_key": effective_track,
            "object_key": effective_object_key,
            "station_code": effective_station_code,
            "station_name": effective_station_name,
            "source_date": metadata.get("source_date"),
            "catalog": catalog_result,
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


@router.post("/analyze")
async def analyze_survey_file(
    request: Request,
    filename: str = Query(..., min_length=1, max_length=255),
    _: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    _cleanup_analysis_sessions()
    temp_path = await _stream_request_to_temp(request)
    analysis_id = uuid4()
    session_dir = _analysis_dir(analysis_id)
    session_dir.mkdir(parents=True, exist_ok=False)
    source_path = session_dir / "source.bin"
    safe_name = _safe_name(filename)
    try:
        os.replace(temp_path, source_path)
        meta = {
            "filename": safe_name,
            "created_at_epoch": time.time(),
            "is_zip": safe_name.lower().endswith(".zip"),
        }
        (session_dir / "meta.json").write_text(
            json.dumps(meta, ensure_ascii=False),
            encoding="utf-8",
        )
        items: list[dict[str, Any]] = []
        if meta["is_zip"]:
            try:
                with zipfile.ZipFile(source_path, "r") as archive:
                    infos = [info for info in archive.infolist() if not info.is_dir()]
                    if len(infos) > _MAX_ZIP_ENTRIES:
                        raise HTTPException(413, "SURVEY_IMPORT_ZIP_TOO_MANY_FILES")
                    if sum(max(0, info.file_size) for info in infos) > _MAX_ZIP_TOTAL_BYTES:
                        raise HTTPException(413, "SURVEY_IMPORT_ZIP_UNPACKED_TOO_LARGE")
                    for index, info in enumerate(infos):
                        entry_name = _safe_name(Path(info.filename).name)
                        kind = _infer_kind(entry_name)
                        if kind is None:
                            reason = _skip_reason(entry_name)
                            if reason:
                                items.append(
                                    {
                                        "item_id": f"zip:{index}",
                                        "archive_index": index,
                                        "name": entry_name,
                                        "kind": None,
                                        "can_import": False,
                                        "parse_status": "SKIPPED",
                                        "message": reason,
                                        "default_scope": _SCOPE_GLOBAL,
                                        "preview": {},
                                    }
                                )
                            continue
                        entry_path = _extract_zip_entry(archive, info)
                        try:
                            items.append(
                                _analysis_item(
                                    item_id=f"zip:{index}",
                                    path=entry_path,
                                    original_name=entry_name,
                                    kind=kind,
                                    archive_index=index,
                                )
                            )
                        finally:
                            entry_path.unlink(missing_ok=True)
            except zipfile.BadZipFile as exc:
                raise HTTPException(400, "SURVEY_IMPORT_ZIP_INVALID") from exc
        else:
            kind = _infer_kind(safe_name)
            if kind is None:
                reason = _skip_reason(safe_name)
                if reason:
                    items.append(
                        {
                            "item_id": "file",
                            "name": safe_name,
                            "kind": None,
                            "can_import": False,
                            "parse_status": "SKIPPED",
                            "message": reason,
                            "default_scope": _SCOPE_GLOBAL,
                            "preview": {},
                        }
                    )
                else:
                    raise HTTPException(400, "SURVEY_IMPORT_FILE_TYPE_NOT_SUPPORTED")
            else:
                items.append(
                    _analysis_item(
                        item_id="file",
                        path=source_path,
                        original_name=safe_name,
                        kind=kind,
                    )
                )
        if not items:
            raise HTTPException(400, "SURVEY_ANALYSIS_NO_RECOGNIZED_FILES")
        return {
            "analysis_id": str(analysis_id),
            "source_name": safe_name,
            "expires_in_seconds": _ANALYSIS_TTL_SECONDS,
            "items": items,
        }
    except Exception:
        shutil.rmtree(session_dir, ignore_errors=True)
        raise
    finally:
        temp_path.unlink(missing_ok=True)


@router.post("/analysis/{analysis_id}/import")
def import_analyzed_survey(
    analysis_id: UUID,
    plan: SurveyImportPlan,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    _cleanup_analysis_sessions()
    source_path, meta = _load_analysis_meta(analysis_id)
    session_dir = source_path.parent
    results: list[dict[str, Any]] = []
    seen: set[str] = set()
    decisions = []
    for decision in plan.decisions:
        if decision.item_id in seen:
            raise HTTPException(400, "SURVEY_IMPORT_DUPLICATE_DECISION")
        seen.add(decision.item_id)
        decisions.append(decision)

    try:
        if bool(meta.get("is_zip")):
            try:
                with zipfile.ZipFile(source_path, "r") as archive:
                    infos = [info for info in archive.infolist() if not info.is_dir()]
                    for decision in decisions:
                        if not decision.import_item:
                            results.append(
                                {
                                    "name": decision.item_id,
                                    "status": "ÜBERSPRUNGEN",
                                    "message": "Nicht zur Übernahme ausgewählt.",
                                }
                            )
                            continue
                        match = re.fullmatch(r"zip:(\d+)", decision.item_id)
                        if not match:
                            raise HTTPException(400, "SURVEY_IMPORT_DECISION_ITEM_INVALID")
                        index = int(match.group(1))
                        if index < 0 or index >= len(infos):
                            raise HTTPException(400, "SURVEY_IMPORT_DECISION_ITEM_INVALID")
                        info = infos[index]
                        entry_name = _safe_name(Path(info.filename).name)
                        kind = (decision.data_kind or _infer_kind(entry_name) or "").strip().upper()
                        if not kind:
                            raise HTTPException(400, "SURVEY_IMPORT_FILE_TYPE_NOT_SUPPORTED")
                        normalized_scope = _normalize_scope(decision.scope)
                        if normalized_scope == _SCOPE_WORKSITE and decision.worksite_id is None:
                            raise HTTPException(400, "SURVEY_IMPORT_WORKSITE_REQUIRED")
                        entry_path = _extract_zip_entry(archive, info)
                        try:
                            results.append(
                                _install_original(
                                    path=entry_path,
                                    original_name=entry_name,
                                    kind=kind,
                                    worksite_id=decision.worksite_id,
                                    scope=normalized_scope,
                                    route_number=decision.route_number,
                                    track_key=decision.track_key,
                                    object_key=decision.object_key,
                                    station_code=decision.station_code,
                                    station_name=decision.station_name,
                                    source_date=decision.source_date,
                                    overwrite_existing=decision.overwrite_existing,
                                    user=user,
                                )
                            )
                        finally:
                            entry_path.unlink(missing_ok=True)
            except zipfile.BadZipFile as exc:
                raise HTTPException(400, "SURVEY_IMPORT_ZIP_INVALID") from exc
        else:
            decision = next(
                (item for item in decisions if item.item_id == "file"),
                None,
            )
            if decision is None:
                raise HTTPException(400, "SURVEY_IMPORT_FILE_DECISION_REQUIRED")
            if not decision.import_item:
                results.append(
                    {
                        "name": str(meta.get("filename") or "Datei"),
                        "status": "ÜBERSPRUNGEN",
                        "message": "Nicht zur Übernahme ausgewählt.",
                    }
                )
            else:
                name = _safe_name(str(meta.get("filename") or "survey.dat"))
                kind = (decision.data_kind or _infer_kind(name) or "").strip().upper()
                if not kind:
                    raise HTTPException(400, "SURVEY_IMPORT_FILE_TYPE_NOT_SUPPORTED")
                normalized_scope = _normalize_scope(decision.scope)
                if normalized_scope == _SCOPE_WORKSITE and decision.worksite_id is None:
                    raise HTTPException(400, "SURVEY_IMPORT_WORKSITE_REQUIRED")
                results.append(
                    _install_original(
                        path=source_path,
                        original_name=name,
                        kind=kind,
                        worksite_id=decision.worksite_id,
                        scope=normalized_scope,
                        route_number=decision.route_number,
                        track_key=decision.track_key,
                        object_key=decision.object_key,
                        station_code=decision.station_code,
                        station_name=decision.station_name,
                        source_date=decision.source_date,
                        overwrite_existing=decision.overwrite_existing,
                        user=user,
                    )
                )
        needs_confirmation = any(
            str(item.get("status") or "") == "VORHANDEN_ABWEICHEND"
            for item in results
        )
        if not needs_confirmation:
            shutil.rmtree(session_dir, ignore_errors=True)
        return {
            "analysis_id": str(analysis_id),
            "source_name": meta.get("filename"),
            "results": results,
            "needs_confirmation": needs_confirmation,
        }
    except Exception:
        try:
            os.utime(session_dir, None)
        except OSError:
            pass
        raise


@router.post("/file")
async def import_survey_file(
    request: Request,
    worksite_id: UUID | None = Query(default=None),
    scope: str = Query(default=_SCOPE_WORKSITE, max_length=40),
    filename: str = Query(..., min_length=1, max_length=255),
    data_kind: str | None = Query(default=None, max_length=40),
    route_number: str | None = Query(default=None, max_length=80),
    track_key: str | None = Query(default=None, max_length=160),
    object_key: str | None = Query(default=None, max_length=160),
    station_code: str | None = Query(default=None, max_length=50),
    station_name: str | None = Query(default=None, max_length=240),
    source_date: date | None = Query(default=None),
    overwrite_existing: bool = Query(default=False),
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    normalized_scope = _normalize_scope(scope)
    if normalized_scope == _SCOPE_WORKSITE and worksite_id is None:
        raise HTTPException(400, "SURVEY_IMPORT_WORKSITE_REQUIRED")
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
                                scope=normalized_scope,
                                route_number=route_number,
                                track_key=track_key,
                                object_key=object_key,
                                station_code=station_code,
                                station_name=station_name,
                                source_date=source_date,
                                overwrite_existing=overwrite_existing,
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
            scope=normalized_scope,
            route_number=route_number,
            track_key=track_key,
            object_key=object_key,
            station_code=station_code,
            station_name=station_name,
            source_date=source_date,
            overwrite_existing=overwrite_existing,
            user=user,
        )
        return {"results": [result]}
    finally:
        temp_path.unlink(missing_ok=True)
