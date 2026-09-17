from __future__ import annotations

import ast
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    if rel.endswith(".py"):
        ast.parse(source, filename=rel)
    return source


def render_smoke() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.btb_pdf import PDF_RENDERER_VERSION, render_btb_pdf

    snapshot = {
        "btb": {
            "work_date": "2026-09-18",
            "started_at": "2026-09-18T07:00:00+02:00",
            "ended_at": "2026-09-18T17:00:00+02:00",
            "header": {"Wetter": "sonnig", "Personal": {"Firma A": 4}},
        },
        "entries": [
            {
                "id": "entry-a",
                "section": "4 Arbeiten",
                "sort_order": 0,
                "payload": {"Text": "Gleis vermessen"},
                "version": 2,
            }
        ],
        "photos": [],
    }
    original = {
        "btb": snapshot["btb"],
        "entries": [
            {
                "id": "entry-a",
                "section": "4 Arbeiten",
                "sort_order": 0,
                "payload": {"Text": "Gleis vorbereitet"},
                "version": 1,
            },
            {
                "id": "entry-removed",
                "section": "7 Besonderheiten",
                "sort_order": 0,
                "payload": {"Text": "Alt"},
                "version": 1,
            },
        ],
        "photos": [],
    }
    with tempfile.TemporaryDirectory() as temp_dir:
        target = Path(temp_dir) / "btb.pdf"
        render_btb_pdf(
            target,
            snapshot=snapshot,
            original_snapshot=original,
            context={
                "company_name": "CI Test GmbH",
                "worksite_name": "CI Baustelle",
                "route_number": "5362",
                "lead_name": "CI TB",
            },
            photo_files=[],
        )
        data = target.read_bytes()
        assert data.startswith(b"%PDF-"), data[:16]
        assert len(data) > 1000, len(data)
        assert PDF_RENDERER_VERSION == "tb-server-btb-pdf-1"


def main() -> None:
    btb = read("server/tbserver/btb.py")
    documents = read("server/tbserver/btb_documents.py")
    pdf = read("server/tbserver/btb_pdf.py")
    files = read("server/tbserver/files.py")
    caps = read("server/tbserver/capabilities.py")
    requirements = read("server/requirements.txt")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('@router.post("/btbs/{btb_id}/finalize")', btb),
        ('@router.get("/btbs/{btb_id}/final-pdf")', btb),
        ('@router.post("/btbs/{btb_id}/reopen")', btb),
        ('@router.delete("/btbs/{btb_id}/photos/{file_id}"', btb),
        ('action="BTB_FINALIZE_NO_CHANGES"', btb),
        ('operation="REACTIVATE"', btb),
        ('DELETE FROM tb.btb_original_snapshots', btb),
        ('"photos": snapshot_photo_metadata(photo_files)', btb),
        ('FINAL_PDF_PURPOSE = "BTB_FINAL_PDF"', documents),
        ('/btb/{btb_id}/abschluss.pdf', documents),
        ("status = 'REWORK'", documents),
        ('raise HTTPException(409, "BTB_NOT_ACTIVE")', documents),
        ('def install_pdf_atomically', documents),
        ('def rollback_pdf_install', documents),
        ('PDF_RENDERER_VERSION = "tb-server-btb-pdf-1"', pdf),
        ('textColor=colors.red', pdf),
        ('(entfernt)', pdf),
        ('prepare_btb_photo_change', files),
        ('remove_storage_keys(invalidated_pdf_keys)', files),
        ('"btb_finalize_with_pdf_v1": True', caps),
        ('reportlab==', requirements),
        ('34_verify_tb_server_1_btb_pdf_source.py', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende BTB-PDF-/Originalmodell-Marker: " + ", ".join(missing)
        )

    forbidden = ["_V1.pdf", "_V2.pdf", "_V3.pdf"]
    found = [
        needle
        for needle in forbidden
        if needle in documents or needle in btb
    ]
    if found:
        raise SystemExit(
            "Sichtbare PDF-Versionierung ist im Server-1.0-Modell verboten: "
            + ", ".join(found)
        )

    render_smoke()
    print("OK: TB-Server 1.0 BTB one-PDF/original/rework contract")


if __name__ == "__main__":
    main()
