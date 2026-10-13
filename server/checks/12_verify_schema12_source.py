#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
part = (ROOT / "server/app_parts/19_part.py").read_text(encoding="utf-8")
migration = (ROOT / "server/migrations/012_company_pdf_logo_layout.sql").read_text(encoding="utf-8")
android_api = (ROOT / "app/src/main/java/de/tbhelfer/server/CompanyPdfLogoApi.kt").read_text(encoding="utf-8")
android_store = (ROOT / "app/src/main/java/de/tbhelfer/einstellungen/BtbPdfLogoLayoutSpeicher.kt").read_text(encoding="utf-8")
android_preview = (ROOT / "app/src/main/java/de/tbhelfer/ui/einstellungen/btbpdf/BtbPdfLayoutVorschau.kt").read_text(encoding="utf-8")
android_pdf = (ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt").read_text(encoding="utf-8")

compile(part, "19_part.py", "exec")

required = [
    "/api/v1/company/profile/pdf-logo-layout",
    "COMPANY_PDF_LAYOUT_SCHEMA_REQUIRED",
    "pdf_logo_width_pt",
    "pdf_logo_offset_x_pt",
    "pdf_logo_offset_y_pt",
    "pdf_logo_contacts_in_image",
    "pdf_logo_layout_version",
]
for token in required:
    assert token in part or token in migration, token

assert "VALUES (12,'012_company_pdf_logo_layout')" in migration
assert "CompanyPdfLogoApi" in android_api
assert "logoWidthPt: Int = 90" in android_store
assert "contactsInImage: Boolean = false" in android_store
assert "BtbPdfHeaderRenderer.drawCompanyBlock" in android_preview
assert "BtbPdfHeaderRenderer.drawCompanyBlock" in android_pdf
assert "BtbPdfHeaderRenderer.personalHeaderLines" in android_pdf
assert "BtbPdfHeaderRenderer.creatorFooter" in android_pdf

print("SCHEMA12_SOURCE_OK")
