from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb

from .admin import require_admin, require_admin_csrf
from .btb_pdf import render_company_preview_pdf
from .config import settings
from .database import transaction
from .events import audit
from .portal import current_portal_user, require_csrf


router = APIRouter(prefix="/api/v1", tags=["company"])

COMPANY_LOGO_MAX_BYTES = 5 * 1024 * 1024
COMPANY_LOGO_STORAGE_NAME = "logo"
PDF_LOGO_LAYOUT_DEFAULTS: dict[str, Any] = {
    "logo_width_pt": 90,
    "offset_x_pt": 0,
    "offset_y_pt": 0,
    "show_company_name": True,
    "show_company_email": True,
    "show_company_phone": True,
    "contacts_in_image": False,
    "layout_version": 1,
}


class CompanyProfileUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    address_line1: str | None = Field(default=None, max_length=300)
    address_line2: str | None = Field(default=None, max_length=300)
    postal_code: str | None = Field(default=None, max_length=30)
    city: str | None = Field(default=None, max_length=200)
    phone: str | None = Field(default=None, max_length=100)
    email: str | None = Field(default=None, max_length=320)
    website: str | None = Field(default=None, max_length=500)


class PdfLogoLayoutUpdate(BaseModel):
    logo_width_pt: int = Field(default=90, ge=40, le=250)
    offset_x_pt: int = Field(default=0, ge=-120, le=120)
    offset_y_pt: int = Field(default=0, ge=-60, le=180)
    show_company_name: bool = True
    show_company_email: bool = True
    show_company_phone: bool = True
    contacts_in_image: bool = False


def _clean_optional(value: str | None) -> str | None:
    if value is None:
        return None
    cleaned = value.strip()
    return cleaned or None


def _settings_dict(value: Any) -> dict[str, Any]:
    return dict(value) if isinstance(value, dict) else {}


def normalize_pdf_logo_layout(value: Any) -> dict[str, Any]:
    source = dict(value) if isinstance(value, dict) else {}
    layout = dict(PDF_LOGO_LAYOUT_DEFAULTS)
    layout.update(
        {
            "logo_width_pt": max(40, min(250, int(source.get("logo_width_pt", 90)))),
            "offset_x_pt": max(-120, min(120, int(source.get("offset_x_pt", 0)))),
            "offset_y_pt": max(-60, min(180, int(source.get("offset_y_pt", 0)))),
            "show_company_name": bool(source.get("show_company_name", True)),
            "show_company_email": bool(source.get("show_company_email", True)),
            "show_company_phone": bool(source.get("show_company_phone", True)),
            "contacts_in_image": bool(source.get("contacts_in_image", False)),
            "layout_version": max(1, int(source.get("layout_version", 1))),
        }
    )
    return layout


def _company_row(cur, company_id: UUID, *, for_update: bool = False) -> dict[str, Any]:
    suffix = " FOR UPDATE OF c" if for_update else ""
    cur.execute(
        f"""
        SELECT c.id, c.name, c.street, c.postal_code, c.city, c.country_code,
               c.phone, c.email, c.status, c.settings, c.pdf_layout,
               c.storage_warning_percent, c.logo_file_id, c.created_at, c.updated_at,
               f.storage_key AS logo_storage_key,
               f.mime_type AS logo_mime_type,
               f.sha256 AS logo_sha256,
               f.byte_size AS logo_byte_size,
               f.deleted_at AS logo_deleted_at
        FROM tb.companies c
        LEFT JOIN tb.files f ON f.id = c.logo_file_id
        WHERE c.id = %s AND c.deleted_at IS NULL{suffix}
        """,
        (company_id,),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "COMPANY_NOT_FOUND")
    return dict(row)


def _company_id_for_user(user: dict[str, Any]) -> UUID:
    company_id = user.get("company_id")
    if company_id is None:
        raise HTTPException(404, "COMPANY_NOT_ASSIGNED")
    return UUID(str(company_id))


def _profile_payload(row: dict[str, Any]) -> dict[str, Any]:
    company_settings = _settings_dict(row.get("settings"))
    logo_active = bool(
        row.get("logo_file_id")
        and row.get("logo_storage_key")
        and row.get("logo_deleted_at") is None
    )
    return {
        "company_id": str(row["id"]),
        "name": row.get("name"),
        "address_line1": row.get("street"),
        "address_line2": company_settings.get("address_line2"),
        "postal_code": row.get("postal_code"),
        "city": row.get("city"),
        "phone": row.get("phone"),
        "email": row.get("email"),
        "website": company_settings.get("website"),
        "has_logo": logo_active,
        "logo_mime_type": row.get("logo_mime_type") if logo_active else None,
        "logo_sha256": row.get("logo_sha256") if logo_active else None,
        "logo_version": max(0, int(company_settings.get("logo_version", 0))),
        "profile_version": max(1, int(company_settings.get("profile_version", 1))),
        "updated_at": row.get("updated_at"),
    }


def _absolute_storage_path(storage_key: str) -> Path:
    root = settings.storage_root.resolve()
    path = (root / storage_key).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise HTTPException(500, "INVALID_STORAGE_KEY") from exc
    return path


def _logo_storage_key(company_id: UUID | str) -> str:
    return f"companies/{company_id}/profile/{COMPANY_LOGO_STORAGE_NAME}"


def _logo_kind(body: bytes) -> str:
    if body.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if len(body) >= 3 and body[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if len(body) >= 12 and body[:4] == b"RIFF" and body[8:12] == b"WEBP":
        return "image/webp"
    raise HTTPException(400, "COMPANY_LOGO_TYPE_INVALID")


def _install_file(temp_path: Path, final_path: Path, backup_path: Path) -> bool:
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


def _rollback_file(final_path: Path, backup_path: Path, had_previous: bool) -> None:
    final_path.unlink(missing_ok=True)
    if had_previous and backup_path.exists():
        os.replace(backup_path, final_path)
    else:
        backup_path.unlink(missing_ok=True)


def _cleanup_temp(*paths: Path | None) -> None:
    for path in paths:
        if path is not None:
            path.unlink(missing_ok=True)


def load_company_branding(cur, company_id: UUID | str) -> dict[str, Any]:
    row = _company_row(cur, UUID(str(company_id)))
    profile = _profile_payload(row)
    logo_path = None
    if profile["has_logo"]:
        candidate = _absolute_storage_path(row["logo_storage_key"])
        if candidate.is_file():
            logo_path = candidate
    return {
        **profile,
        "pdf_layout": normalize_pdf_logo_layout(row.get("pdf_layout")),
        "logo_path": logo_path,
    }


@router.get("/company/profile")
def company_profile(
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    company_id = _company_id_for_user(user)
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id)
    return {"status": "ok", "profile": _profile_payload(row)}


@router.get("/company/profile/logo")
def company_logo(
    user: dict[str, Any] = Depends(current_portal_user),
):
    company_id = _company_id_for_user(user)
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id)
    profile = _profile_payload(row)
    if not profile["has_logo"]:
        raise HTTPException(404, "COMPANY_LOGO_NOT_FOUND")
    path = _absolute_storage_path(row["logo_storage_key"])
    if not path.is_file():
        raise HTTPException(404, "COMPANY_LOGO_FILE_MISSING")
    return FileResponse(
        path,
        media_type=row["logo_mime_type"] or "application/octet-stream",
        filename="firmenlogo",
        headers={"ETag": f'"{row["logo_sha256"] or ""}"'},
    )


@router.get("/company/profile/pdf-logo-layout")
def company_pdf_logo_layout(
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    company_id = _company_id_for_user(user)
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id)
    return {"status": "ok", "layout": normalize_pdf_logo_layout(row.get("pdf_layout"))}


@router.put("/company/profile/pdf-logo-layout")
def update_company_pdf_logo_layout(
    payload: PdfLogoLayoutUpdate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict:
    company_id = _company_id_for_user(user)
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id, for_update=True)
        current = normalize_pdf_logo_layout(row.get("pdf_layout"))
        next_layout = {
            **payload.model_dump(),
            "layout_version": int(current["layout_version"]) + 1,
        }
        company_settings = _settings_dict(row.get("settings"))
        company_settings["profile_version"] = max(
            1, int(company_settings.get("profile_version", 1))
        ) + 1
        cur.execute(
            """
            UPDATE tb.companies
            SET pdf_layout = %s, settings = %s
            WHERE id = %s
            RETURNING updated_at
            """,
            (Jsonb(next_layout), Jsonb(company_settings), company_id),
        )
        cur.fetchone()
        audit(
            cur,
            action="COMPANY_PDF_LOGO_LAYOUT_UPDATED",
            actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),
            company_id=company_id,
            entity_type="COMPANY",
            entity_id=company_id,
            details={"layout_version": next_layout["layout_version"]},
        )
    return {"status": "ok", "layout": next_layout}



@router.get("/admin/companies/{company_id}/logo")
def admin_company_logo(
    company_id: UUID,
    _: dict[str, Any] = Depends(require_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id)
    profile = _profile_payload(row)
    if not profile["has_logo"]:
        raise HTTPException(404, "COMPANY_LOGO_NOT_FOUND")
    path = _absolute_storage_path(row["logo_storage_key"])
    if not path.is_file():
        raise HTTPException(404, "COMPANY_LOGO_FILE_MISSING")
    return FileResponse(
        path,
        media_type=row["logo_mime_type"] or "application/octet-stream",
        filename="firmenlogo",
        headers={"ETag": f'"{row["logo_sha256"] or ""}"'},
    )


@router.get("/admin/companies/{company_id}/pdf-preview")
def admin_company_pdf_preview(
    company_id: UUID,
    logo_width_pt: int | None = Query(default=None, ge=40, le=250),
    offset_x_pt: int | None = Query(default=None, ge=-120, le=120),
    offset_y_pt: int | None = Query(default=None, ge=-60, le=180),
    show_company_name: bool | None = Query(default=None),
    show_company_email: bool | None = Query(default=None),
    show_company_phone: bool | None = Query(default=None),
    contacts_in_image: bool | None = Query(default=None),
    _: dict[str, Any] = Depends(require_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        branding = load_company_branding(cur, company_id)

    # Die Admin-Vorschau darf mit noch nicht gespeicherten Reglerwerten
    # gerendert werden. Erst PUT /pdf-logo-layout uebernimmt sie firmenweit.
    preview_layout = dict(branding.get("pdf_layout") or {})
    draft_values = {
        "logo_width_pt": logo_width_pt,
        "offset_x_pt": offset_x_pt,
        "offset_y_pt": offset_y_pt,
        "show_company_name": show_company_name,
        "show_company_email": show_company_email,
        "show_company_phone": show_company_phone,
        "contacts_in_image": contacts_in_image,
    }
    preview_layout.update(
        {key: value for key, value in draft_values.items() if value is not None}
    )
    preview_layout = normalize_pdf_logo_layout(preview_layout)

    content = render_company_preview_pdf(
        context={
            "company_name": branding.get("name"),
            "company_email": branding.get("email"),
            "company_phone": branding.get("phone"),
            "company_logo_path": branding.get("logo_path"),
            "pdf_logo_layout": preview_layout,
        }
    )
    return Response(
        content=content,
        media_type="application/pdf",
        headers={
            "Content-Disposition": 'inline; filename="TB-Helfer_PDF_Vorschau.pdf"',
            "Cache-Control": "no-store",
        },
    )


@router.put("/admin/companies/{company_id}/pdf-logo-layout")
def admin_update_company_pdf_logo_layout(
    company_id: UUID,
    payload: PdfLogoLayoutUpdate,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id, for_update=True)
        current = normalize_pdf_logo_layout(row.get("pdf_layout"))
        next_layout = {
            **payload.model_dump(),
            "layout_version": int(current["layout_version"]) + 1,
        }
        company_settings = _settings_dict(row.get("settings"))
        company_settings["profile_version"] = max(
            1, int(company_settings.get("profile_version", 1))
        ) + 1
        cur.execute(
            """
            UPDATE tb.companies
            SET pdf_layout = %s, settings = %s
            WHERE id = %s
            """,
            (Jsonb(next_layout), Jsonb(company_settings), company_id),
        )
        audit(
            cur,
            action="COMPANY_PDF_LOGO_LAYOUT_UPDATED",
            actor_user_id=user["user_id"],
            company_id=company_id,
            entity_type="COMPANY",
            entity_id=company_id,
            details={
                "layout_version": next_layout["layout_version"],
                "source": "ADMIN",
            },
        )
    return {"status": "ok", "layout": next_layout}


@router.get("/admin/companies/{company_id}/profile")
def admin_company_profile(
    company_id: UUID,
    _: dict[str, Any] = Depends(require_admin),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id)
    return {
        "profile": _profile_payload(row),
        "pdf_logo_layout": normalize_pdf_logo_layout(row.get("pdf_layout")),
    }


@router.put("/admin/companies/{company_id}/profile")
def admin_update_company_profile(
    company_id: UUID,
    payload: CompanyProfileUpdate,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    updates = payload.model_dump(exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    if "email" in updates and updates["email"] is not None:
        email = updates["email"].strip()
        if email and "@" not in email:
            raise HTTPException(400, "COMPANY_EMAIL_INVALID")
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id, for_update=True)
        company_settings = _settings_dict(row.get("settings"))
        if "address_line2" in updates:
            company_settings["address_line2"] = _clean_optional(updates.pop("address_line2"))
        if "website" in updates:
            company_settings["website"] = _clean_optional(updates.pop("website"))
        company_settings["profile_version"] = max(
            1, int(company_settings.get("profile_version", 1))
        ) + 1

        columns: list[str] = ["settings = %s"]
        values: list[Any] = [Jsonb(company_settings)]
        mapping = {
            "name": "name",
            "address_line1": "street",
            "postal_code": "postal_code",
            "city": "city",
            "phone": "phone",
            "email": "email",
        }
        for field, column in mapping.items():
            if field not in updates:
                continue
            value = _clean_optional(updates[field])
            if field == "name" and value is None:
                raise HTTPException(400, "COMPANY_NAME_REQUIRED")
            columns.append(f"{column} = %s")
            values.append(value)
        values.append(company_id)
        cur.execute(
            f"UPDATE tb.companies SET {', '.join(columns)} WHERE id = %s RETURNING *",
            tuple(values),
        )
        updated = dict(cur.fetchone())
        audit(
            cur,
            action="COMPANY_PROFILE_UPDATED",
            actor_user_id=user["user_id"],
            company_id=company_id,
            entity_type="COMPANY",
            entity_id=company_id,
            details={"profile_version": company_settings["profile_version"]},
        )
        row = _company_row(cur, company_id)
    return {"profile": _profile_payload(row)}


@router.put("/admin/companies/{company_id}/logo")
async def admin_upload_company_logo(
    company_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    body = await request.body()
    if not body:
        raise HTTPException(400, "COMPANY_LOGO_EMPTY")
    if len(body) > COMPANY_LOGO_MAX_BYTES:
        raise HTTPException(413, "COMPANY_LOGO_TOO_LARGE")
    mime_type = _logo_kind(body)
    sha256 = hashlib.sha256(body).hexdigest()
    storage_key = _logo_storage_key(company_id)
    final_path = _absolute_storage_path(storage_key)
    final_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = final_path.with_name(f".{final_path.name}.{uuid4().hex}.tmp")
    backup_path = final_path.with_name(f".{final_path.name}.rollback")
    temp_path.write_bytes(body)

    installed = False
    had_previous = False
    try:
        had_previous = _install_file(temp_path, final_path, backup_path)
        installed = True
        with transaction() as conn, conn.cursor() as cur:
            row = _company_row(cur, company_id, for_update=True)
            company_settings = _settings_dict(row.get("settings"))
            company_settings["logo_version"] = max(
                0, int(company_settings.get("logo_version", 0))
            ) + 1
            company_settings["profile_version"] = max(
                1, int(company_settings.get("profile_version", 1))
            ) + 1
            cur.execute(
                "SELECT id FROM tb.files WHERE storage_key = %s FOR UPDATE",
                (storage_key,),
            )
            existing = cur.fetchone()
            if existing:
                cur.execute(
                    """
                    UPDATE tb.files
                    SET company_id = %s, worksite_id = NULL, original_name = %s,
                        mime_type = %s, byte_size = %s, sha256 = %s,
                        source = 'PORTAL', created_by = %s, deleted_at = NULL
                    WHERE id = %s
                    RETURNING *
                    """,
                    (
                        company_id,
                        "firmenlogo",
                        mime_type,
                        len(body),
                        sha256,
                        user["user_id"],
                        existing["id"],
                    ),
                )
            else:
                cur.execute(
                    """
                    INSERT INTO tb.files(
                        company_id, worksite_id, storage_key, original_name,
                        mime_type, byte_size, sha256, source, created_by
                    ) VALUES (%s,NULL,%s,%s,%s,%s,%s,'PORTAL',%s)
                    RETURNING *
                    """,
                    (
                        company_id,
                        storage_key,
                        "firmenlogo",
                        mime_type,
                        len(body),
                        sha256,
                        user["user_id"],
                    ),
                )
            file_row = dict(cur.fetchone())
            cur.execute(
                "UPDATE tb.companies SET logo_file_id = %s, settings = %s WHERE id = %s",
                (file_row["id"], Jsonb(company_settings), company_id),
            )
            audit(
                cur,
                action="COMPANY_LOGO_UPDATED",
                actor_user_id=user["user_id"],
                company_id=company_id,
                entity_type="COMPANY",
                entity_id=company_id,
                details={
                    "file_id": str(file_row["id"]),
                    "sha256": sha256,
                    "byte_size": len(body),
                    "logo_version": company_settings["logo_version"],
                },
            )
        _cleanup_temp(temp_path, backup_path)
    except Exception:
        if installed:
            _rollback_file(final_path, backup_path, had_previous)
        _cleanup_temp(temp_path, backup_path)
        raise

    return {
        "status": "ok",
        "logo": {
            "mime_type": mime_type,
            "sha256": sha256,
            "byte_size": len(body),
        },
    }


@router.delete("/admin/companies/{company_id}/logo", status_code=204)
def admin_delete_company_logo(
    company_id: UUID,
    user: dict[str, Any] = Depends(require_admin_csrf),
):
    storage_key: str | None = None
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id, for_update=True)
        if not row.get("logo_file_id") or row.get("logo_deleted_at") is not None:
            return None
        company_settings = _settings_dict(row.get("settings"))
        company_settings["logo_version"] = max(
            0, int(company_settings.get("logo_version", 0))
        ) + 1
        company_settings["profile_version"] = max(
            1, int(company_settings.get("profile_version", 1))
        ) + 1
        storage_key = row.get("logo_storage_key")
        cur.execute(
            "UPDATE tb.files SET deleted_at = now() WHERE id = %s",
            (row["logo_file_id"],),
        )
        cur.execute(
            "UPDATE tb.companies SET logo_file_id = NULL, settings = %s WHERE id = %s",
            (Jsonb(company_settings), company_id),
        )
        audit(
            cur,
            action="COMPANY_LOGO_DELETED",
            actor_user_id=user["user_id"],
            company_id=company_id,
            entity_type="COMPANY",
            entity_id=company_id,
            details={"logo_version": company_settings["logo_version"]},
        )
    if storage_key:
        _absolute_storage_path(storage_key).unlink(missing_ok=True)
    return None
