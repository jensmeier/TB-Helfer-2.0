from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbPdfApi.kt").read_text(encoding="utf-8")
btb_api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt").read_text(encoding="utf-8")
media = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt").read_text(encoding="utf-8")
versioning = (ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/btbs/",
    "/final-pdf",
    "Authorization",
    "application/pdf",
    "PDF_MAGIC",
    "NotAvailable",
):
    assert token in api, token

for token in (
    "SERVER_STATUS_KEY",
    "SERVER_FINAL_PDF_AVAILABLE_KEY",
    "SERVER_FINAL_PDF_SHA256_KEY",
    'root.optJSONObject("final_pdf")',
):
    assert token in btb_api, token

for token in (
    "server1PdfApi: Server1BtbPdfApi? = null",
    "downloadFinalPdf",
    "BtbSpeicher.btbsFuerBaustelle",
    "BtbAbschlussPdfFinder.neueste",
    "serverAbschlussUebernehmen",
    "serverAbschlussInvalidieren",
    "MessageDigest.getInstance",
    "pdfImportMarker",
):
    assert token in media, token

for token in (
    'private const val SERVER_SOURCE = "SERVER_1_0"',
    "fun serverAbschlussUebernehmen",
    "fun serverAbschlussInvalidieren",
    "fun hatServerAbschlussMarker",
    "pflegeServerAbschlussNachAutosave",
):
    assert token in versioning, token

import_block = versioning.split("fun serverAbschlussUebernehmen", 1)[1].split(
    "fun serverAbschlussInvalidieren", 1
)[0]
assert "baselineFile" not in import_block, "Server PDF import must not fabricate Android original baseline"

assert 'capabilities.supports("btb_finalize_with_pdf_v1")' in fragment
assert "server1PdfApi = if" in fragment
assert '@router.get("/btbs/{btb_id}/final-pdf")' in server

print("OK: Android mirrors authoritative Server-1.0 BTB final PDFs without faking the first original")
