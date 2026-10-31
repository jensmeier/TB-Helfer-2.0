from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from fastapi import HTTPException

from .btb_photo_meta import canonical_photo_metadata, merge_btb_photo_metadata
from .config import settings


FINAL_PDF_PURPOSE = "BTB_FINAL_PDF"
FINAL_PDF_MIME = "application/pdf"


def _absolute_storage_path(storage_key: str) -> Path:
    root = settings.storage_root.resolve()
    path = (root / storage_key).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise HTTPException(500, "INVALID_STORAGE_KEY") from exc
    return path


def final_pdf_storage_key(*, company_id: UUID | str, worksite_id: UUID | str, btb_id: UUID | str) -> str:
    return f"companies/{company_id}/worksites/{worksite_id}/btb/{btb_id}/abschluss.pdf"


def final_pdf_paths(
    *,
    company_id: UUID | str,
    worksite_id: UUID | str,
    btb_id: UUID | str,
) -> tuple[Path, Path, Path]:
    final_path = _absolute_storage_path(
        final_pdf_storage_key(
            company_id=company_id,
            worksite_id=worksite_id,
            btb_id=btb_id,
        )
    )
    final_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = final_path.with_name(f".{final_path.name}.{uuid4().hex}.tmp")
    backup_path = final_path.with_name(f".{final_path.name}.rollback")
    return final_path, temp_path, backup_path


def install_pdf_atomically(temp_path: Path, final_path: Path, backup_path: Path) -> bool:
    if not temp_path.is_file():
        raise RuntimeError("BTB_PDF_TEMP_MISSING")
    backup_path.unlink(missing_ok=True)
    had_previous = final_path.exists()
    if had_previous:
        os.replace(final_path, backup_path)
    try:
        os.replace(temp_path, final_path)
    except Exception:
        if had_previous and backup_path.exists():
            os.replace(backup_path, final_path)
        raise
    return had_previous


def rollback_pdf_install(final_path: Path, backup_path: Path, had_previous: bool) -> None:
    final_path.unlink(missing_ok=True)
    if had_previous and backup_path.exists():
        os.replace(backup_path, final_path)
    else:
        backup_path.unlink(missing_ok=True)


def finish_pdf_install(temp_path: Path | None, backup_path: Path | None) -> None:
    if temp_path is not None:
        temp_path.unlink(missing_ok=True)
    if backup_path is not None:
        backup_path.unlink(missing_ok=True)


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as src:
        for block in iter(lambda: src.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_original_snapshot(cur, btb_id: UUID) -> dict[str, Any] | None:
    cur.execute(
        """
        SELECT btb_id, snapshot, snapshot_sha256, created_at, created_by
        FROM tb.btb_original_snapshots
        WHERE btb_id = %s
        """,
        (btb_id,),
    )
    row = cur.fetchone()
    return dict(row) if row else None


def load_btb_photo_files(cur, btb_id: UUID) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT f.id, f.storage_key, f.original_name, f.mime_type, f.byte_size, f.sha256,
               f.captured_at, f.gps_lat, f.gps_lon, f.gps_accuracy_m, f.photo_metadata,
               f.created_at, l.purpose, l.link_metadata, l.version AS link_version
        FROM tb.file_links l
        JOIN tb.files f ON f.id = l.file_id
        WHERE l.entity_type = 'BTB' AND l.entity_id = %s
          AND l.purpose <> %s
          AND f.deleted_at IS NULL
          AND f.mime_type LIKE 'image/%%'
        ORDER BY f.captured_at NULLS LAST, f.created_at, f.id
        """,
        (btb_id, FINAL_PDF_PURPOSE),
    )
    return [dict(row) for row in cur.fetchall()]




def effective_btb_photo_metadata(photo: dict[str, Any]) -> dict[str, Any]:
    base = canonical_photo_metadata(
        filename=photo.get("original_name"),
        captured_at=photo.get("captured_at"),
        gps_lat=photo.get("gps_lat"),
        gps_lon=photo.get("gps_lon"),
        gps_accuracy_m=photo.get("gps_accuracy_m"),
        photo_metadata=photo.get("photo_metadata"),
    )["photo_metadata"] or {}
    return merge_btb_photo_metadata(base, photo.get("link_metadata"))


def snapshot_photo_metadata(photo_files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for photo in photo_files:
        captured_at = photo.get("captured_at")
        effective = effective_btb_photo_metadata(photo)
        result.append(
            {
                "id": str(photo["id"]),
                "sha256": photo.get("sha256"),
                "original_name": photo.get("original_name"),
                "mime_type": photo.get("mime_type"),
                "captured_at": captured_at.isoformat() if captured_at else None,
                "gps_lat": effective.get("lat"),
                "gps_lon": effective.get("lon"),
                "gps_accuracy_m": photo.get("gps_accuracy_m"),
                "photo_metadata": effective,
                "purpose": photo.get("purpose"),
                "link_version": photo.get("link_version", 1),
            }
        )
    return result


def photo_files_for_render(photo_files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for photo in photo_files:
        row = dict(photo)
        row["photo_metadata"] = effective_btb_photo_metadata(row)
        row["gps_lat"] = row["photo_metadata"].get("lat")
        row["gps_lon"] = row["photo_metadata"].get("lon")
        row["path"] = _absolute_storage_path(row["storage_key"])
        result.append(row)
    return result


def load_final_pdf(cur, btb_id: UUID) -> dict[str, Any] | None:
    cur.execute(
        """
        SELECT f.id, f.company_id, f.worksite_id, f.storage_key, f.original_name,
               f.mime_type, f.byte_size, f.sha256, f.source, f.created_at
        FROM tb.file_links l
        JOIN tb.files f ON f.id = l.file_id
        WHERE l.entity_type = 'BTB' AND l.entity_id = %s
          AND l.purpose = %s
          AND f.deleted_at IS NULL
        ORDER BY f.created_at DESC, f.id DESC
        LIMIT 1
        """,
        (btb_id, FINAL_PDF_PURPOSE),
    )
    row = cur.fetchone()
    return dict(row) if row else None


def public_final_pdf(file_row: dict[str, Any], *, btb_id: UUID | str) -> dict[str, Any]:
    return {
        "id": file_row["id"],
        "original_name": file_row["original_name"],
        "mime_type": file_row["mime_type"],
        "byte_size": file_row["byte_size"],
        "sha256": file_row["sha256"],
        "content_url": f"/api/v1/btbs/{btb_id}/final-pdf",
    }


def final_pdf_content_path(file_row: dict[str, Any]) -> Path:
    return _absolute_storage_path(file_row["storage_key"])


def upsert_final_pdf_file(
    cur,
    *,
    btb: dict[str, Any],
    company_id: UUID | str,
    user_id: UUID | str,
    byte_size: int,
    sha256: str,
) -> tuple[dict[str, Any], str]:
    storage_key = final_pdf_storage_key(
        company_id=company_id,
        worksite_id=btb["worksite_id"],
        btb_id=btb["id"],
    )
    original_name = f"BTB_{btb['id']}_Abschluss.pdf"
    cur.execute(
        "SELECT id FROM tb.files WHERE storage_key = %s FOR UPDATE",
        (storage_key,),
    )
    existing = cur.fetchone()
    if existing:
        operation = "UPDATE"
        cur.execute(
            """
            UPDATE tb.files
            SET company_id = %s,
                worksite_id = %s,
                original_name = %s,
                mime_type = %s,
                byte_size = %s,
                sha256 = %s,
                source = 'SERVER',
                created_by = %s,
                deleted_at = NULL
            WHERE id = %s
            RETURNING *
            """,
            (
                company_id,
                btb["worksite_id"],
                original_name,
                FINAL_PDF_MIME,
                byte_size,
                sha256,
                user_id,
                existing["id"],
            ),
        )
        row = dict(cur.fetchone())
    else:
        operation = "CREATE"
        cur.execute(
            """
            INSERT INTO tb.files(
                company_id, worksite_id, storage_key, original_name, mime_type,
                byte_size, sha256, source, created_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,'SERVER',%s)
            RETURNING *
            """,
            (
                company_id,
                btb["worksite_id"],
                storage_key,
                original_name,
                FINAL_PDF_MIME,
                byte_size,
                sha256,
                user_id,
            ),
        )
        row = dict(cur.fetchone())
    cur.execute(
        """
        INSERT INTO tb.file_links(file_id, entity_type, entity_id, purpose)
        VALUES (%s,'BTB',%s,%s)
        ON CONFLICT (file_id, entity_type, entity_id, purpose) DO NOTHING
        """,
        (row["id"], btb["id"], FINAL_PDF_PURPOSE),
    )
    return row, operation


def invalidate_final_pdf(cur, btb_id: UUID) -> list[str]:
    cur.execute(
        """
        SELECT f.id, f.storage_key
        FROM tb.file_links l
        JOIN tb.files f ON f.id = l.file_id
        WHERE l.entity_type = 'BTB'
          AND l.entity_id = %s
          AND l.purpose = %s
          AND f.deleted_at IS NULL
        FOR UPDATE
        """,
        (btb_id, FINAL_PDF_PURPOSE),
    )
    rows = [dict(row) for row in cur.fetchall()]
    if not rows:
        return []
    ids = [row["id"] for row in rows]
    cur.execute(
        """
        DELETE FROM tb.file_links
        WHERE entity_type = 'BTB'
          AND entity_id = %s
          AND purpose = %s
        """,
        (btb_id, FINAL_PDF_PURPOSE),
    )
    cur.execute(
        "UPDATE tb.files SET deleted_at = now() WHERE id = ANY(%s)",
        (ids,),
    )
    return [row["storage_key"] for row in rows]


def _assert_active_for_edit(cur, btb: dict[str, Any]) -> None:
    cur.execute(
        "SELECT active_btb_id FROM tb.teams WHERE id = %s FOR UPDATE",
        (btb["team_id"],),
    )
    team = cur.fetchone()
    if not team:
        raise HTTPException(409, "BTB_TEAM_MISSING")
    if team["active_btb_id"] != btb["id"] and btb["status"] != "REWORK":
        raise HTTPException(409, "BTB_NOT_ACTIVE")


def prepare_btb_content_change(cur, btb: dict[str, Any]) -> list[str]:
    if btb["status"] not in {"ACTIVE", "FINALIZED", "REWORK"}:
        raise HTTPException(409, "BTB_NOT_EDITABLE")
    _assert_active_for_edit(cur, btb)
    if btb["status"] == "REWORK":
        return invalidate_final_pdf(cur, btb["id"])
    if btb["status"] != "FINALIZED":
        return []
    storage_keys = invalidate_final_pdf(cur, btb["id"])
    cur.execute(
        """
        UPDATE tb.btbs
        SET status = 'REWORK',
            finalized_at = NULL,
            updated_at = now()
        WHERE id = %s
        """,
        (btb["id"],),
    )
    return storage_keys


def prepare_btb_photo_change(
    cur,
    *,
    btb_id: UUID,
    worksite_id: UUID,
) -> list[str]:
    cur.execute(
        """
        SELECT id, worksite_id, team_id, status
        FROM tb.btbs
        WHERE id = %s
          AND worksite_id = %s
          AND deleted_at IS NULL
        FOR UPDATE
        """,
        (btb_id, worksite_id),
    )
    btb = cur.fetchone()
    if not btb or btb["status"] == "DELETED":
        raise HTTPException(404, "BTB_NOT_FOUND")
    return prepare_btb_content_change(cur, dict(btb))


def remove_storage_keys(storage_keys: list[str]) -> None:
    for storage_key in dict.fromkeys(storage_keys):
        path = _absolute_storage_path(storage_key)
        path.unlink(missing_ok=True)

