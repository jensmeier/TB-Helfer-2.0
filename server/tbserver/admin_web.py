from __future__ import annotations

from pathlib import Path
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .company import _company_row, _profile_payload, normalize_pdf_logo_layout
from .database import transaction
from .portal import current_portal_session_user


router = APIRouter(tags=["portal-web"])
templates = Jinja2Templates(
    directory=str(Path(__file__).resolve().parent.parent / "templates")
)

def require_web_admin(
    user: dict[str, Any] = Depends(current_portal_session_user),
) -> dict[str, Any]:
    if user["company_role"] != "ADMIN":
        from fastapi import HTTPException

        raise HTTPException(403, "ADMIN_REQUIRED")
    return user



@router.get("/portal/v1/login")
def portal_login_page(request: Request):
    return templates.TemplateResponse(
        "v1_portal_login.html",
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
    _: dict[str, Any] = Depends(require_web_admin),
):
    return RedirectResponse("/admin/v1/companies", status_code=303)


@router.get("/admin/v1/releases")
def admin_releases_page(
    request: Request,
    user: dict[str, Any] = Depends(require_web_admin),
):
    return templates.TemplateResponse(
        "v1_releases.html",
        {"request": request, "user": user},
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
                   count(u.id) FILTER (WHERE u.deleted_at IS NULL) AS user_count
            FROM tb.companies c
            LEFT JOIN tb.users u ON u.company_id = c.id
            WHERE c.deleted_at IS NULL
            GROUP BY c.id
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
        {"request": request, "user": user},
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
    return templates.TemplateResponse(
        "v1_company_branding.html",
        {
            "request": request,
            "user": user,
            "company_id": str(company_id),
            "profile": profile,
            "layout": layout,
        },
    )
