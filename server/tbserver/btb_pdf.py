from __future__ import annotations

import json
from html import escape
from pathlib import Path
from typing import Any

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Image, KeepTogether, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


PDF_RENDERER_VERSION = "tb-server-btb-pdf-1"


def _fmt(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, bool):
        return "Ja" if value else "Nein"
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, sort_keys=True)
    return str(value)


def _flatten(value: Any, prefix: str = "") -> list[tuple[str, str]]:
    if isinstance(value, dict):
        rows: list[tuple[str, str]] = []
        for key in sorted(value, key=lambda item: str(item).lower()):
            child = f"{prefix}.{key}" if prefix else str(key)
            rows.extend(_flatten(value[key], child))
        return rows or [(prefix or "Wert", "-")]
    if isinstance(value, list):
        rows = []
        for index, item in enumerate(value, 1):
            child = f"{prefix}[{index}]" if prefix else f"[{index}]"
            rows.extend(_flatten(item, child))
        return rows or [(prefix or "Wert", "-")]
    return [(prefix or "Wert", _fmt(value))]


def _safe_paragraph(text: Any, style: ParagraphStyle) -> Paragraph:
    return Paragraph(escape(_fmt(text)).replace("\n", "<br/>"), style)


def _entry_text(entry: dict[str, Any]) -> str:
    payload = entry.get("payload") or {}
    flat = _flatten(payload)
    if not flat:
        return "-"
    return "<br/>".join(
        f"<b>{escape(label)}:</b> {escape(value)}" if label else escape(value)
        for label, value in flat
    )


def _entry_changed(entry: dict[str, Any], original: dict[str, Any] | None) -> bool:
    if original is None:
        return True
    return (
        entry.get("section") != original.get("section")
        or entry.get("sort_order") != original.get("sort_order")
        or entry.get("payload") != original.get("payload")
    )


def _photo_identity(photo: dict[str, Any]) -> tuple[str, Any]:
    return str(photo.get("id")), photo.get("sha256")


def render_btb_pdf(
    output_path: Path,
    *,
    snapshot: dict[str, Any],
    original_snapshot: dict[str, Any] | None,
    context: dict[str, Any],
    photo_files: list[dict[str, Any]],
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "BtbTitle",
        parent=styles["Title"],
        alignment=TA_CENTER,
        fontSize=18,
        leading=22,
        spaceAfter=8,
    )
    h2 = ParagraphStyle(
        "BtbH2",
        parent=styles["Heading2"],
        fontSize=12,
        leading=15,
        spaceBefore=8,
        spaceAfter=5,
    )
    body = ParagraphStyle(
        "BtbBody",
        parent=styles["BodyText"],
        fontSize=9,
        leading=12,
        spaceAfter=3,
    )
    changed = ParagraphStyle("BtbChanged", parent=body, textColor=colors.red)
    small = ParagraphStyle("BtbSmall", parent=body, fontSize=8, leading=10)
    small_changed = ParagraphStyle("BtbSmallChanged", parent=small, textColor=colors.red)

    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=A4,
        leftMargin=16 * mm,
        rightMargin=16 * mm,
        topMargin=14 * mm,
        bottomMargin=14 * mm,
        title="Bautagesbericht",
        author=context.get("company_name") or "TB-Helfer",
    )
    story: list[Any] = [Paragraph("Bautagesbericht", title)]

    btb = snapshot.get("btb") or {}
    meta = [
        ("Firma", context.get("company_name")),
        ("Anschrift", context.get("company_address")),
        ("Kontakt", context.get("company_contact")),
        ("Baustelle", context.get("worksite_name")),
        ("Bezeichnung", context.get("worksite_designation")),
        ("Strecke", context.get("route_number")),
        ("Team", context.get("team_name")),
        ("Verantwortlicher TB", context.get("lead_name")),
        ("Datum", btb.get("work_date")),
        ("Arbeitsbeginn", btb.get("started_at")),
        ("Arbeitsende", btb.get("ended_at")),
    ]
    meta_rows = [
        [_safe_paragraph(label, small), _safe_paragraph(value, small)]
        for label, value in meta
        if value not in (None, "")
    ]
    if meta_rows:
        table = Table(meta_rows, colWidths=[42 * mm, 126 * mm], hAlign="LEFT")
        table.setStyle(
            TableStyle(
                [
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LINEBELOW", (0, 0), (-1, -1), 0.15, colors.lightgrey),
                    ("LEFTPADDING", (0, 0), (-1, -1), 2),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 2),
                    ("TOPPADDING", (0, 0), (-1, -1), 2),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
                ]
            )
        )
        story.extend([table, Spacer(1, 5 * mm)])

    story.append(Paragraph("Kopfdaten", h2))
    header_rows = _flatten(btb.get("header") or {})
    if header_rows:
        header_table = Table(
            [
                [_safe_paragraph(label, small), _safe_paragraph(value, small)]
                for label, value in header_rows
            ],
            colWidths=[55 * mm, 113 * mm],
            hAlign="LEFT",
        )
        header_table.setStyle(
            TableStyle(
                [
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("GRID", (0, 0), (-1, -1), 0.15, colors.lightgrey),
                    ("BACKGROUND", (0, 0), (0, -1), colors.whitesmoke),
                    ("LEFTPADDING", (0, 0), (-1, -1), 3),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 3),
                    ("TOPPADDING", (0, 0), (-1, -1), 2),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
                ]
            )
        )
        story.append(header_table)
    else:
        story.append(Paragraph("Keine Kopfdaten.", body))

    entries = snapshot.get("entries") or []
    original_entries = (original_snapshot or {}).get("entries") or []
    original_by_id = {str(item.get("id")): item for item in original_entries}
    current_ids = {str(item.get("id")) for item in entries}
    by_section: dict[str, list[dict[str, Any]]] = {}
    for entry in entries:
        by_section.setdefault(str(entry.get("section") or "Sonstiges"), []).append(entry)

    story.append(Paragraph("Fachliche Einträge", h2))
    if not entries and not original_entries:
        story.append(Paragraph("Keine Facheinträge.", body))
    for section in sorted(by_section, key=str.lower):
        story.append(Paragraph(escape(section), h2))
        for entry in sorted(
            by_section[section],
            key=lambda item: (item.get("sort_order", 0), str(item.get("id"))),
        ):
            original = (
                original_by_id.get(str(entry.get("id")))
                if original_snapshot is not None
                else entry
            )
            style = (
                changed
                if original_snapshot is not None and _entry_changed(entry, original)
                else body
            )
            story.append(Paragraph(_entry_text(entry), style))
            story.append(Spacer(1, 1.5 * mm))

    if original_snapshot is not None:
        removed_entries = [
            item
            for item in original_entries
            if str(item.get("id")) not in current_ids
        ]
        if removed_entries:
            story.append(Paragraph("Entfernte Originaleinträge", h2))
            for entry in removed_entries:
                story.append(
                    Paragraph(
                        f"<b>(entfernt)</b> {_entry_text(entry)}",
                        changed,
                    )
                )

    story.append(Paragraph("Fotos", h2))
    current_photos = snapshot.get("photos") or []
    original_photos = (original_snapshot or {}).get("photos") or []
    original_photo_by_id = {
        str(item.get("id")): item for item in original_photos
    }
    current_photo_ids = {str(item.get("id")) for item in current_photos}
    file_by_id = {str(item.get("id")): item for item in photo_files}

    if not current_photos and not original_photos:
        story.append(Paragraph("Keine BTB-Fotos.", body))
    for index, photo in enumerate(current_photos, 1):
        original = (
            original_photo_by_id.get(str(photo.get("id")))
            if original_snapshot is not None
            else photo
        )
        is_changed = (
            original_snapshot is not None
            and _photo_identity(photo) != _photo_identity(original or {})
        )
        is_new = original_snapshot is not None and original is None
        style = small_changed if is_changed or is_new else small
        label = f"Foto {index}: {photo.get('original_name') or photo.get('id')}"
        if is_changed or is_new:
            label += " (geändert/neu)"
        parts: list[Any] = [Paragraph(escape(label), style)]
        file_info = file_by_id.get(str(photo.get("id")))
        path = file_info.get("path") if file_info else None
        if isinstance(path, Path) and path.is_file():
            try:
                image = Image(str(path))
                max_w, max_h = 165 * mm, 90 * mm
                scale = min(
                    max_w / image.imageWidth,
                    max_h / image.imageHeight,
                    1.0,
                )
                image.drawWidth = image.imageWidth * scale
                image.drawHeight = image.imageHeight * scale
                parts.append(image)
            except Exception:
                parts.append(
                    Paragraph(
                        "Bild konnte nicht gerendert werden.",
                        style,
                    )
                )
        story.append(KeepTogether(parts))
        story.append(Spacer(1, 3 * mm))

    if original_snapshot is not None:
        removed_photos = [
            item
            for item in original_photos
            if str(item.get("id")) not in current_photo_ids
        ]
        for photo in removed_photos:
            label = photo.get("original_name") or photo.get("id")
            story.append(
                Paragraph(
                    f"(entfernt) Foto: {escape(_fmt(label))}",
                    small_changed,
                )
            )

    story.append(Spacer(1, 4 * mm))
    story.append(
        Paragraph(
            f"PDF-Renderer: {PDF_RENDERER_VERSION}",
            ParagraphStyle(
                "Footer",
                parent=small,
                textColor=colors.grey,
            ),
        )
    )
    doc.build(story)
