"""Offline checks for the production BTB PDF renderer. Run with pytest -q server/tests/test_btb_pdf_reference.py."""
from pathlib import Path

import fitz
from PIL import Image

from tbserver.btb_pdf_android_style import render_android_style_btb_pdf


def test_reference_photo_cards_and_gps(tmp_path: Path):
    image = tmp_path / "photo.jpg"
    Image.new("RGB", (120, 80), "white").save(image)
    photos = []
    files = []
    for index in range(4):
        photo_id = f"photo-{index}"
        photos.append({
            "id": photo_id,
            "captured_at": f"2026-10-23T18:0{index}:00+02:00",
            "photo_metadata": {
                "typ": "FOTODOKUMENTATION",
                "firma": "Firma A",
                "kurztext": "Beschreibung " + ("lang " * 20),
                "km": 0 if index == 0 else None,
                "lat": 47.613375,
                "lon": 10.272265,
            },
        })
        files.append({"id": photo_id, "path": image})
    snapshot = {
        "btb": {
            "work_date": "2026-10-23",
            "started_at": "2026-10-23T07:00:00+02:00",
            "ended_at": "2026-10-23T19:00:00+02:00",
            "header": {"maxFotosInPdf": 3},
        },
        "entries": [],
        "photos": photos,
    }
    target = tmp_path / "new_comparison_only.pdf"
    render_android_style_btb_pdf(
        target,
        snapshot=snapshot,
        original_snapshot=None,
        context={"worksite_name": "Berchtesgaden", "lead_name": "Test"},
        photo_files=files,
        renderer_version="pdf-reference-test",
    )
    doc = fitz.open(target)
    text = "\n".join(page.get_text() for page in doc)
    links = [link for page in doc for link in page.get_links() if link.get("uri")]
    assert "Bild 1" in text and "Bild 2" in text and "Bild 3" in text
    assert "Bild 4" not in text
    assert "KM: 0,000" in text
    # All three selected photo cards must occupy one physical row.
    page_rectangles = []
    for page in doc:
        headings = []
        for number in (1, 2, 3):
            headings.extend(page.search_for(f"Bild {number}"))
        if len(headings) == 3:
            page_rectangles = sorted(headings, key=lambda rect: rect.x0)
            break
    assert len(page_rectangles) == 3, "Three selected photos must share a row"
    assert page_rectangles[0].x0 < page_rectangles[1].x0 < page_rectangles[2].x0
    assert max(rect.y0 for rect in page_rectangles) - min(rect.y0 for rect in page_rectangles) < 3
    assert text.count("GPS: 47.613375") == 3
    assert len(links) == 3
    assert all("google.com/maps/search" in link["uri"] for link in links)
    assert all("47.613375" in link["uri"] for link in links)
