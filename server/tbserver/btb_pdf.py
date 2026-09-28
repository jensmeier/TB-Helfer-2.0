from __future__ import annotations

import json
from io import BytesIO
from html import escape
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any

from PIL import Image as PILImage, ImageOps
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.lib.utils import ImageReader
from reportlab.platypus import Flowable, Image, KeepTogether, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


PDF_RENDERER_VERSION = "tb-server-btb-pdf-3"
PDF_PHOTO_MAX_SIZE_PX = (1600, 1200)
PDF_PHOTO_JPEG_QUALITY = 82



class CompanyHeaderFlowable(Flowable):
    """Server-PDF counterpart of the Android company/logo header block."""

    def __init__(self, context: dict[str, Any]):
        super().__init__()
        self.context = context
        self.height = 48 * mm

    def wrap(self, availWidth, availHeight):
        self.width = availWidth
        return availWidth, self.height

    def draw(self):
        canvas = self.canv
        layout = self.context.get("pdf_logo_layout") or {}
        width_pt = max(40, min(250, int(layout.get("logo_width_pt", 90))))
        offset_x = max(-120, min(120, int(layout.get("offset_x_pt", 0))))
        offset_y = max(-60, min(180, int(layout.get("offset_y_pt", 0))))
        show_name = bool(layout.get("show_company_name", True))
        show_email = bool(layout.get("show_company_email", True))
        show_phone = bool(layout.get("show_company_phone", True))
        contacts_in_image = bool(layout.get("contacts_in_image", False))

        canvas.saveState()
        try:
            canvas.setFillColor(colors.black)
            canvas.setFont("Helvetica-Bold", 18)
            canvas.drawString(0, self.height - 20, "Bautagesbericht")

            box_left = max(0.51 * self.width, 84 * mm)
            box_right = self.width
            box_width = max(80, box_right - box_left)
            logo_top = self.height - 3 * mm
            logo_bottom = 17 * mm
            logo_height_available = max(28 * mm, logo_top - logo_bottom)
            logo_bottom_drawn = logo_top

            logo_path = self.context.get("company_logo_path")
            if isinstance(logo_path, Path) and logo_path.is_file():
                try:
                    image = ImageReader(str(logo_path))
                    image_width, image_height = image.getSize()
                    draw_width = min(float(width_pt), box_width)
                    draw_height = draw_width * float(image_height) / float(image_width)
                    if draw_height > logo_height_available:
                        factor = logo_height_available / draw_height
                        draw_width *= factor
                        draw_height *= factor

                    center_x = box_left + box_width / 2.0
                    desired_left = center_x - draw_width / 2.0 + offset_x
                    left = max(box_left, min(box_right - draw_width, desired_left))
                    centered_bottom = logo_bottom + (logo_height_available - draw_height) / 2.0
                    desired_bottom = centered_bottom - offset_y
                    bottom = max(2 * mm, min(logo_top - draw_height, desired_bottom))

                    canvas.setFillColor(colors.white)
                    canvas.rect(left, bottom, draw_width, draw_height, fill=1, stroke=0)
                    canvas.drawImage(
                        image,
                        left,
                        bottom,
                        width=draw_width,
                        height=draw_height,
                        preserveAspectRatio=True,
                        mask="auto",
                    )
                    logo_bottom_drawn = bottom
                except Exception:
                    logo_path = None

            lines: list[str] = []
            if show_name and self.context.get("company_name"):
                lines.append(str(self.context["company_name"]))
            if not contacts_in_image:
                if show_email and self.context.get("company_email"):
                    lines.append(str(self.context["company_email"]))
                if show_phone and self.context.get("company_phone"):
                    lines.append(str(self.context["company_phone"]))

            canvas.setFillColor(colors.black)
            canvas.setFont("Helvetica", 8.4)
            if isinstance(logo_path, Path) and logo_path.is_file():
                text_y = max(4 * mm, logo_bottom_drawn - 10)
            else:
                text_y = min(self.height - 24, self.height - 12 - offset_y)
            center_x = box_left + box_width / 2.0 + offset_x
            center_x = max(box_left + 20, min(box_right - 20, center_x))
            for line in lines:
                value = line if len(line) <= 70 else line[:67] + "…"
                canvas.drawCentredString(center_x, text_y, value)
                text_y -= 10
        finally:
            canvas.restoreState()

def render_company_preview_pdf(*, context: dict[str, Any]) -> bytes:
    """Render an A4 admin preview with the exact company header flowable used by BTB PDFs."""
    buffer = BytesIO()
    styles = getSampleStyleSheet()
    body = ParagraphStyle(
        "CompanyPreviewBody",
        parent=styles["BodyText"],
        fontSize=9,
        leading=12,
        spaceAfter=4,
    )
    heading = ParagraphStyle(
        "CompanyPreviewHeading",
        parent=styles["Heading2"],
        fontSize=12,
        leading=15,
        spaceBefore=6,
        spaceAfter=5,
    )
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=16 * mm,
        rightMargin=16 * mm,
        topMargin=14 * mm,
        bottomMargin=14 * mm,
        title="TB-Helfer PDF-Vorschau",
        author=context.get("company_name") or "TB-Helfer",
    )
    story: list[Any] = [
        CompanyHeaderFlowable(context),
        Spacer(1, 5 * mm),
        Paragraph("PDF-Vorschau", heading),
        Paragraph("Baustelle: Beispielbaustelle", body),
        Paragraph("Strecke: 5362 · km 70,000 – 81,500", body),
        Paragraph("Verantwortlicher TB: Beispiel", body),
        Paragraph("Datum: 28.09.2026 · Schicht: Tag", body),
        Spacer(1, 5 * mm),
        Paragraph(
            "Diese Vorschau verwendet denselben Firmenkopf, dasselbe Firmenlogo "
            "und dieselben PDF-Logo-Layoutwerte wie der serverseitige BTB-Abschluss.",
            body,
        ),
        Spacer(1, 4 * mm),
        Table(
            [
                ["Punkt", "Beispielinhalt"],
                ["4. Ausgeführte Arbeiten", "Beispieltext für die PDF-Darstellung"],
                ["5. Abnahmen", "Keine Einträge"],
            ],
            colWidths=[55 * mm, 110 * mm],
            style=[
                ("GRID", (0, 0), (-1, -1), 0.5, colors.lightgrey),
                ("BACKGROUND", (0, 0), (-1, 0), colors.whitesmoke),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTSIZE", (0, 0), (-1, -1), 8.5),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ],
        ),
    ]
    doc.build(story)
    return buffer.getvalue()


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


def _prepare_pdf_photo(source_path: Path, target_path: Path) -> Path:
    """Create a bounded JPEG render copy so large phone photos cannot exhaust NAS RAM."""
    target_path.parent.mkdir(parents=True, exist_ok=True)
    with PILImage.open(source_path) as source:
        # JPEG decoders can often avoid a full-resolution decode when draft() is used.
        try:
            source.draft("RGB", PDF_PHOTO_MAX_SIZE_PX)
        except Exception:
            pass

        normalized = ImageOps.exif_transpose(source)
        prepared = None
        try:
            normalized.thumbnail(
                PDF_PHOTO_MAX_SIZE_PX,
                resample=PILImage.Resampling.LANCZOS,
            )
            if normalized.mode == "RGB":
                prepared = normalized
            elif normalized.mode in {"RGBA", "LA"} or "transparency" in normalized.info:
                rgba = normalized.convert("RGBA")
                try:
                    prepared = PILImage.new("RGB", rgba.size, "white")
                    prepared.paste(rgba, mask=rgba.getchannel("A"))
                finally:
                    rgba.close()
            else:
                prepared = normalized.convert("RGB")

            prepared.save(
                target_path,
                format="JPEG",
                quality=PDF_PHOTO_JPEG_QUALITY,
                optimize=True,
                progressive=True,
            )
        finally:
            if prepared is not None and prepared is not normalized:
                prepared.close()
            if normalized is not source:
                normalized.close()
    return target_path


def render_btb_pdf(
    output_path: Path,
    *,
    snapshot: dict[str, Any],
    original_snapshot: dict[str, Any] | None,
    context: dict[str, Any],
    photo_files: list[dict[str, Any]],
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    photo_temp_dir = TemporaryDirectory(
        prefix=".tb-btb-pdf-photos-",
        dir=str(output_path.parent),
    )
    photo_render_dir = Path(photo_temp_dir.name)
    styles = getSampleStyleSheet()
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
    story: list[Any] = [CompanyHeaderFlowable(context)]

    btb = snapshot.get("btb") or {}
    meta = [
        ("Anschrift", context.get("company_address")),
        ("Webseite", context.get("company_website")),
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
                render_path = _prepare_pdf_photo(
                    path,
                    photo_render_dir / f"{index:04d}.jpg",
                )
                image = Image(str(render_path))
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
    try:
        doc.build(story)
    finally:
        photo_temp_dir.cleanup()
