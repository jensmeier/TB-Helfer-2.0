from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    if rel.endswith(".py"):
        ast.parse(source, filename=rel)
    return source


def main() -> None:
    company = read("server/tbserver/company.py")
    btb = read("server/tbserver/btb.py")
    pdf = read("server/tbserver/btb_pdf.py")
    main_source = read("server/tbserver/main.py")
    capabilities = read("server/tbserver/capabilities.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")
    workflow = read(".github/workflows/tb-server-v1.yml")
    android = read("app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt")
    android_logo = read("app/src/main/java/de/tbhelfer/server/CompanyPdfLogoApi.kt")

    assertions = [
        ('@router.get("/company/profile")', company),
        ('@router.get("/company/profile/logo")', company),
        ('@router.get("/company/profile/pdf-logo-layout")', company),
        ('@router.put("/company/profile/pdf-logo-layout")', company),
        ('@router.get("/admin/companies/{company_id}/profile")', company),
        ('@router.put("/admin/companies/{company_id}/profile")', company),
        ('@router.get("/admin/companies/{company_id}/pdf-preview")', company),
        ('logo_width_pt: int | None = Query(default=None, ge=40, le=250)', company),
        ('offset_x_pt: int | None = Query(default=None, ge=-120, le=120)', company),
        ('preview_layout.update(', company),
        ('@router.put("/admin/companies/{company_id}/logo")', company),
        ('@router.delete("/admin/companies/{company_id}/logo"', company),
        ('COMPANY_LOGO_MAX_BYTES = 5 * 1024 * 1024', company),
        ('body.startswith(b"\\x89PNG\\r\\n\\x1a\\n")', company),
        ('body[:3] == b"\\xff\\xd8\\xff"', company),
        ('body[8:12] == b"WEBP"', company),
        ('return f"companies/{company_id}/profile/{COMPANY_LOGO_STORAGE_NAME}"', company),
        ('"logo_width_pt": 90', company),
        ('"layout_version": 1', company),
        ('ge=40, le=250', company),
        ('ge=-120, le=120', company),
        ('ge=-60, le=180', company),
        ('Depends(current_portal_user)', company),
        ('Depends(require_csrf)', company),
        ('Depends(require_admin)', company),
        ('Depends(require_admin_csrf)', company),
        ('action="COMPANY_LOGO_UPDATED"', company),
        ('action="COMPANY_PDF_LOGO_LAYOUT_UPDATED"', company),
        ('def load_company_branding', company),
        ('from .company import load_company_branding', btb),
        ('"company_logo_path": branding.get("logo_path")', btb),
        ('"pdf_logo_layout": branding.get("pdf_layout")', btb),
        ('class CompanyHeaderFlowable(Flowable):', pdf),
        ('PDF_RENDERER_VERSION = "tb-server-btb-pdf-4"', pdf),
        ('self.height = 62 * mm', pdf),
        ('logo_bottom = max(2.0, self.height - 174)', pdf),
        ('ImageReader(str(logo_path))', pdf),
        ('contacts_in_image', pdf),
        ('company_router', main_source),
        ('"company_profile_v1": True', capabilities),
        ('"company_logo_v1": True', capabilities),
        ('"company_pdf_logo_layout_v1": True', capabilities),
        ('pdf_layout jsonb NOT NULL', baseline),
        ('logo_file_id uuid', baseline),
        ('settings jsonb NOT NULL', baseline),
        ('35_verify_tb_server_1_company_branding_source.py', workflow),
        ('"company_profile_v1"', workflow),
        ('"company_logo_v1"', workflow),
        ('"company_pdf_logo_layout_v1"', workflow),
        ('val companyId: String', android),
        ('companyId = profile.optString("company_id")', android),
        ('/api/v1/company/profile/pdf-logo-layout', android_logo),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Firmenprofil-/Logo-/PDF-Layout-Marker: "
            + ", ".join(missing)
        )

    if 'company_logo_v1": False' in capabilities:
        raise SystemExit("company_logo_v1 darf nach Implementierung nicht false sein")
    if "logo_storage_path" in company:
        raise SystemExit(
            "TB-Server 1.0 darf das Legacy-Logo-Pfadmodell nicht parallel einführen"
        )
    if "COMPANY_LOGO_MAX_BYTES = 5 * 1024 * 1024" not in company:
        raise SystemExit("5-MB-Logo-Limit fehlt")

    print("OK: TB-Server 1.0 company profile/logo/PDF layout contract")


if __name__ == "__main__":
    main()
