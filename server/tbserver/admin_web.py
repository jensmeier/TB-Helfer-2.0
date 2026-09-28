from __future__ import annotations

from datetime import timedelta
from pathlib import Path
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .company import _company_row, _profile_payload, normalize_pdf_logo_layout
from .database import transaction
from .admin_session import current_admin_session_user
from .portal import current_portal_session_user


router = APIRouter(tags=["portal-web"])
templates = Jinja2Templates(
    directory=str(Path(__file__).resolve().parent.parent / "templates")
)

def require_web_admin(
    user: dict[str, Any] = Depends(current_admin_session_user),
) -> dict[str, Any]:
    if user["company_role"] != "ADMIN":
        from fastapi import HTTPException

        raise HTTPException(403, "ADMIN_REQUIRED")
    return user



@router.get("/admin/v1/login")
def admin_login_page(request: Request):
    return templates.TemplateResponse(
        "v1_admin_login.html",
        {"request": request},
    )


@router.get("/portal/v1/login")
def portal_login_page(request: Request):
    return templates.TemplateResponse(
        "v1_portal_login.html",
        {"request": request},
    )


@router.get("/portal/v1/activate")
def portal_activate_page(request: Request):
    return templates.TemplateResponse(
        "v1_portal_activate.html",
        {"request": request},
    )


@router.get("/portal/v1")
def portal_home(
    request: Request,
    user: dict[str, Any] = Depends(current_portal_session_user),
):
    return templates.TemplateResponse(
        "v1_portal_home.html",
        {
            "request": request,
            "user": user,
        },
    )


@router.get("/admin/v1")
def admin_v1_root(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_admin_dashboard.html",
        {
            "request": request,
            "user": user,
        },
    )


@router.get("/admin/v1/routes")
def admin_routes_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_admin_routes.html",
        {"request": request, "user": user},
    )


@router.get("/admin/v1/stations")
def admin_stations_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_admin_stations.html",
        {"request": request, "user": user},
    )


@router.get("/admin/v1/catalog-history")
def admin_catalog_history_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT h.change_id, h.entity_type, h.entity_id, h.entity_version,
                   h.operation, h.before_data, h.after_data, h.changed_at,
                   u.display_name AS actor_display_name,
                   c.name AS actor_company_name
            FROM tb.catalog_history h
            LEFT JOIN tb.users u ON u.id = h.changed_by_user_id
            LEFT JOIN tb.companies c ON c.id = h.changed_by_company_id
            ORDER BY h.change_id DESC
            LIMIT 500
            """
        )
        history = [dict(row) for row in cur.fetchall()]
    return templates.TemplateResponse(
        "v1_catalog_history.html",
        {"request": request, "user": user, "history": history},
    )


@router.get("/admin/v1/releases")
def admin_releases_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_releases.html",
        {"request": request, "user": user},
    )


@router.get("/admin/v1/survey-import")
def admin_survey_import_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT w.id, w.company_id, c.name AS company_name, w.name, w.designation,
                   w.route_number, w.station_code, w.station_name, w.status
            FROM tb.worksites w
            JOIN tb.companies c ON c.id = w.company_id
            WHERE w.deleted_at IS NULL
              AND c.deleted_at IS NULL
            ORDER BY lower(c.name), lower(w.name)
            """
        )
        worksites = [dict(row) for row in cur.fetchall()]
        cur.execute(
            """
            SELECT s.id, s.company_id, c.name AS company_name, s.route_number,
                   s.track_key, s.data_kind, s.original_name, s.byte_size,
                   s.imported_at, p.status AS parse_status,
                   p.detected_format, p.warnings, p.error_detail
            FROM tb.survey_originals s
            LEFT JOIN tb.companies c ON c.id = s.company_id
            LEFT JOIN tb.survey_parse_catalog p ON p.original_id = s.id
            ORDER BY s.imported_at DESC
            LIMIT 100
            """
        )
        originals = [dict(row) for row in cur.fetchall()]
    return templates.TemplateResponse(
        "v1_admin_survey_import.html",
        {
            "request": request,
            "user": user,
            "worksites": worksites,
            "originals": originals,
        },
    )


@router.get("/admin/v1/system")
def admin_system_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_system_status.html",
        {"request": request, "user": user},
    )


@router.get("/admin/v1/users")
def admin_users_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, name
            FROM tb.companies
            WHERE deleted_at IS NULL AND status = 'ACTIVE'
            ORDER BY lower(name)
            """
        )
        companies = [dict(row) for row in cur.fetchall()]
        cur.execute(
            """
            SELECT u.id, u.company_id, c.name AS company_name, u.display_name,
                   u.first_name, u.last_name, u.login_name, u.personal_email,
                   u.personal_phone, u.company_role, u.status, u.access_valid_until,
                   u.profile_version, pc.activated_at, pc.last_login_at,
                   d.id AS device_id, d.device_name, d.platform, d.status AS device_status,
                   d.bound_at, d.last_seen_at
            FROM tb.users u
            LEFT JOIN tb.companies c ON c.id = u.company_id
            LEFT JOIN tb.portal_credentials pc ON pc.user_id = u.id
            LEFT JOIN tb.devices d
              ON d.user_id = u.id
             AND d.status = 'ACTIVE'
             AND d.revoked_at IS NULL
            WHERE u.deleted_at IS NULL
              AND u.company_role <> 'ADMIN'
            ORDER BY lower(coalesce(c.name,'')), lower(u.display_name)
            """
        )
        users = [dict(row) for row in cur.fetchall()]
        for item in users:
            valid_until = item.get("access_valid_until")
            item["access_valid_through"] = (
                (valid_until - timedelta(minutes=1)).date()
                if valid_until is not None
                else None
            )

    user_groups: list[dict[str, Any]] = []
    for item in users:
        company_name = item.get("company_name") or "Ohne Firma"
        if not user_groups or user_groups[-1]["company_name"] != company_name:
            user_groups.append({"company_name": company_name, "users": []})
        user_groups[-1]["users"].append(item)

    return templates.TemplateResponse(
        "v1_admin_users.html",
        {
            "request": request,
            "user": user,
            "companies": companies,
            "users": users,
            "user_groups": user_groups,
        },
    )


@router.get("/admin/v1/users/{user_id}/activation-card")
def admin_user_activation_card_page(
    user_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.display_name, u.login_name, u.status, u.access_valid_until,
                   c.name AS company_name,
                   EXISTS (
                     SELECT 1 FROM tb.devices d
                     WHERE d.user_id = u.id
                       AND d.status = 'ACTIVE'
                       AND d.revoked_at IS NULL
                   ) AS has_active_device
            FROM tb.users u
            LEFT JOIN tb.companies c ON c.id = u.company_id
            WHERE u.id = %s
              AND u.deleted_at IS NULL
              AND u.company_role <> 'ADMIN'
            """,
            (user_id,),
        )
        target = cur.fetchone()
    if not target:
        from fastapi import HTTPException
        raise HTTPException(404, "USER_NOT_FOUND")
    return templates.TemplateResponse(
        "v1_device_activation_card.html",
        {
            "request": request,
            "user": user,
            "target": dict(target),
        },
    )


@router.get("/admin/v1/devices")
def admin_devices_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT d.id, d.user_id, u.display_name, c.name AS company_name,
                   d.device_uid, d.device_name, d.platform, d.status,
                   d.bound_at, d.last_seen_at, d.revoked_at
            FROM tb.devices d
            JOIN tb.users u ON u.id = d.user_id
            LEFT JOIN tb.companies c ON c.id = u.company_id
            ORDER BY lower(coalesce(c.name,'')), lower(u.display_name), d.bound_at DESC
            """
        )
        devices = [dict(row) for row in cur.fetchall()]
    device_groups: list[dict[str, Any]] = []
    for device in devices:
        company_name = device.get("company_name") or "Ohne Firma"
        if not device_groups or device_groups[-1]["company_name"] != company_name:
            device_groups.append({"company_name": company_name, "devices": []})
        device_groups[-1]["devices"].append(device)
    return templates.TemplateResponse(
        "v1_admin_devices.html",
        {
            "request": request,
            "user": user,
            "devices": devices,
            "device_groups": device_groups,
        },
    )


@router.get("/admin/v1/companies")
def admin_companies_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT c.id, c.name, c.street, c.postal_code, c.city, c.phone, c.email,
                   c.status, c.logo_file_id, c.updated_at,
                   (SELECT count(*) FROM tb.users u
                    WHERE u.company_id = c.id
                      AND u.deleted_at IS NULL
                      AND u.company_role <> 'ADMIN') AS user_count,
                   (SELECT count(*) FROM tb.devices d
                    JOIN tb.users u ON u.id = d.user_id
                    WHERE u.company_id = c.id
                      AND u.deleted_at IS NULL
                      AND d.revoked_at IS NULL) AS device_count,
                   (SELECT count(*) FROM tb.worksites w
                    WHERE w.company_id = c.id
                      AND w.deleted_at IS NULL) AS worksite_count
            FROM tb.companies c
            WHERE c.deleted_at IS NULL
            ORDER BY lower(c.name)
            """
        )
        companies = [dict(row) for row in cur.fetchall()]
    return templates.TemplateResponse(
        "v1_admin_companies.html",
        {
            "request": request,
            "user": user,
            "companies": companies,
        },
    )




@router.get("/portal/v1/worksites")
def portal_worksites_page(
    request: Request,
    user: dict[str, Any] = Depends(current_portal_session_user),
):
    return templates.TemplateResponse(
        "v1_worksites.html",
        {"request": request, "user": user, "page_prefix": "/portal/v1"},
    )


@router.get("/portal/v1/invitations/redeem")
def portal_invitation_redeem_page(
    request: Request,
    user: dict[str, Any] = Depends(current_portal_session_user),
):
    return templates.TemplateResponse(
        "v1_invitation_redeem.html",
        {"request": request, "user": user},
    )


@router.get("/portal/v1/worksites/{worksite_id}")
def portal_worksite_page(
    worksite_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(current_portal_session_user),
):
    return templates.TemplateResponse(
        "v1_worksite.html",
        {
            "request": request,
            "user": user,
            "worksite_id": str(worksite_id),
            "page_prefix": "/portal/v1",
        },
    )


@router.get("/portal/v1/btbs/{btb_id}")
def portal_btb_page(
    btb_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(current_portal_session_user),
):
    return templates.TemplateResponse(
        "v1_btb.html",
        {
            "request": request,
            "user": user,
            "btb_id": str(btb_id),
            "page_prefix": "/portal/v1",
        },
    )




@router.get("/admin/v1/worksites")
def admin_worksites_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_worksites.html",
        {"request": request, "user": user, "page_prefix": "/admin/v1"},
    )


@router.get("/admin/v1/worksites/{worksite_id}")
def admin_worksite_page(
    worksite_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_worksite.html",
        {
            "request": request,
            "user": user,
            "worksite_id": str(worksite_id),
            "page_prefix": "/admin/v1",
        },
    )


@router.get("/admin/v1/btbs/{btb_id}")
def admin_btb_page(
    btb_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_btb.html",
        {
            "request": request,
            "user": user,
            "btb_id": str(btb_id),
            "page_prefix": "/admin/v1",
        },
    )


@router.get("/admin/v1/companies/{company_id}/branding")
def admin_company_branding_page(
    company_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    with transaction() as conn, conn.cursor() as cur:
        row = _company_row(cur, company_id)
        profile = _profile_payload(row)
        layout = normalize_pdf_logo_layout(row.get("pdf_layout"))

        cur.execute(
            """
            SELECT u.id, u.display_name, u.first_name, u.last_name, u.login_name,
                   u.company_role, u.status, u.personal_email, u.personal_phone,
                   u.access_valid_until, u.profile_version,
                   pc.activated_at, pc.last_login_at
            FROM tb.users u
            LEFT JOIN tb.portal_credentials pc ON pc.user_id = u.id
            WHERE u.company_id = %s
              AND u.deleted_at IS NULL
              AND u.company_role <> 'ADMIN'
            ORDER BY lower(u.display_name)
            """,
            (company_id,),
        )
        company_users = [dict(item) for item in cur.fetchall()]
        for item in company_users:
            valid_until = item.get("access_valid_until")
            item["access_valid_through"] = (
                (valid_until - timedelta(minutes=1)).date()
                if valid_until is not None
                else None
            )

        cur.execute(
            """
            SELECT d.id, d.user_id, d.device_uid, d.device_name, d.platform, d.os_version,
                   d.app_version, d.app_build, d.status, d.bound_at, d.last_seen_at,
                   d.last_sync_at, d.last_sync_error, d.revoked_at,
                   u.display_name, u.login_name
            FROM tb.devices d
            JOIN tb.users u ON u.id = d.user_id
            WHERE u.company_id = %s
              AND u.deleted_at IS NULL
              AND d.revoked_at IS NULL
            ORDER BY lower(u.display_name), d.bound_at DESC
            """,
            (company_id,),
        )
        company_devices = [dict(item) for item in cur.fetchall()]

        cur.execute(
            """
            SELECT id, name, designation, route_number, station_code, station_name,
                   status, version, created_at, updated_at
            FROM tb.worksites
            WHERE company_id = %s AND deleted_at IS NULL
            ORDER BY lower(name)
            """,
            (company_id,),
        )
        company_worksites = [dict(item) for item in cur.fetchall()]

        cur.execute(
            """
            SELECT
              coalesce(sum(f.byte_size) FILTER (WHERE left(lower(f.mime_type), 6) = 'image/'),0) AS photo_bytes,
              coalesce(sum(f.byte_size) FILTER (WHERE lower(f.mime_type) = 'application/pdf'),0) AS pdf_bytes,
              coalesce(sum(f.byte_size) FILTER (
                WHERE left(lower(f.mime_type), 6) <> 'image/'
                  AND lower(f.mime_type) <> 'application/pdf'
              ),0) AS other_file_bytes,
              coalesce(sum(f.byte_size),0) AS file_bytes
            FROM tb.files f
            WHERE f.company_id = %s
              AND f.deleted_at IS NULL
              AND NOT EXISTS (
                SELECT 1
                FROM tb.survey_originals s
                WHERE s.storage_key = f.storage_key
              )
            """,
            (company_id,),
        )
        storage = dict(cur.fetchone())
        for key in ("photo_bytes", "pdf_bytes", "other_file_bytes", "file_bytes"):
            storage[key] = int(storage.get(key) or 0)
        cur.execute(
            "SELECT coalesce(sum(byte_size),0) AS survey_bytes FROM tb.survey_originals WHERE company_id = %s",
            (company_id,),
        )
        storage["survey_bytes"] = int(cur.fetchone()["survey_bytes"] or 0)
        storage["total_bytes"] = storage["file_bytes"] + storage["survey_bytes"]

    return templates.TemplateResponse(
        "v1_company_branding.html",
        {
            "request": request,
            "user": user,
            "company_id": str(company_id),
            "profile": profile,
            "layout": layout,
            "company_users": company_users,
            "company_devices": company_devices,
            "company_worksites": company_worksites,
            "storage": storage,
        },
    )
