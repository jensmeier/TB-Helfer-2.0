from __future__ import annotations

import base64
import io
from datetime import datetime

from reportlab.graphics import renderPDF
from reportlab.graphics.barcode import qr
from reportlab.graphics.shapes import Drawing
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas


def _text(value: str | None, fallback: str = "—") -> str:
    value = (value or "").strip()
    return value or fallback


def _draw_qr(pdf: canvas.Canvas, payload: str, x: float, y: float, size: float) -> None:
    widget = qr.QrCodeWidget(payload)
    x1, y1, x2, y2 = widget.getBounds()
    width = x2 - x1
    height = y2 - y1
    scale = min(size / width, size / height)
    drawing = Drawing(
        size,
        size,
        transform=[scale, 0, 0, scale, -x1 * scale, -y1 * scale],
    )
    drawing.add(widget)
    renderPDF.draw(drawing, pdf, x, y)


def device_activation_pdf_bytes(
    *,
    user_name: str,
    login_name: str,
    company_name: str | None,
    activation_key: str,
    activation_url: str,
    expires_at: datetime,
) -> bytes:
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer, pagesize=A4, pageCompression=1)
    page_w, page_h = A4

    left = 22 * mm
    right = page_w - 22 * mm
    top = page_h - 22 * mm

    pdf.setTitle("TB-Helfer Geräteaktivierung")
    pdf.setAuthor("TB-Helfer Server 1.0")

    pdf.setFont("Helvetica-Bold", 18)
    pdf.drawString(left, top, "TB-Helfer · Geräteaktivierung")

    pdf.setFont("Helvetica", 9)
    pdf.drawRightString(right, top + 2, "Einmalige Aktivierungskarte")

    y = top - 14 * mm
    pdf.setFont("Helvetica-Bold", 15)
    pdf.drawString(left, y, _text(user_name))
    y -= 6 * mm
    pdf.setFont("Helvetica", 10)
    pdf.drawString(left, y, f"Login: {_text(login_name)}")
    y -= 5 * mm
    pdf.drawString(left, y, f"Firma: {_text(company_name)}")
    y -= 5 * mm
    pdf.drawString(
        left,
        y,
        "Gültig bis: " + expires_at.astimezone().strftime("%d.%m.%Y %H:%M %Z"),
    )

    qr_size = 62 * mm
    qr_x = left
    qr_y = y - qr_size - 12 * mm
    _draw_qr(pdf, activation_url, qr_x, qr_y, qr_size)

    text_x = qr_x + qr_size + 12 * mm
    text_y = y - 12 * mm
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawString(text_x, text_y, "Aktivierung")
    text_y -= 7 * mm
    pdf.setFont("Helvetica", 9)
    instructions = [
        "1. TB-Helfer auf dem vorgesehenen Gerät öffnen.",
        "2. Geräteaktivierung / QR scannen wählen.",
        "3. Diesen QR-Code scannen.",
        "4. Nach erfolgreicher Bindung ist die Karte nicht mehr nötig.",
    ]
    for line in instructions:
        pdf.drawString(text_x, text_y, line)
        text_y -= 5.5 * mm

    text_y -= 4 * mm
    pdf.setFont("Helvetica-Bold", 9)
    pdf.drawString(text_x, text_y, "Fallback-Schlüssel:")
    text_y -= 5 * mm
    pdf.setFont("Courier", 7.5)
    key = activation_key.strip()
    chunks = [key[i:i + 28] for i in range(0, len(key), 28)] or [key]
    for chunk in chunks:
        pdf.drawString(text_x, text_y, chunk)
        text_y -= 4.5 * mm

    notice_y = qr_y - 16 * mm
    pdf.setFont("Helvetica-Bold", 9)
    pdf.drawString(left, notice_y, "Vertraulich:")
    pdf.setFont("Helvetica", 8.5)
    notice = (
        "QR-Code und Schlüssel erlauben die einmalige Gerätebindung. "
        "Nur an die vorgesehene Person senden. Ein neu erzeugter Aktivierungsschlüssel "
        "macht einen vorher noch unbenutzten Schlüssel ungültig."
    )
    max_chars = 105
    words = notice.split()
    lines: list[str] = []
    line = ""
    for word in words:
        candidate = f"{line} {word}".strip()
        if len(candidate) > max_chars and line:
            lines.append(line)
            line = word
        else:
            line = candidate
    if line:
        lines.append(line)
    notice_y -= 5 * mm
    for line in lines:
        pdf.drawString(left, notice_y, line)
        notice_y -= 4.5 * mm

    pdf.setFont("Helvetica", 7.5)
    pdf.drawString(
        left,
        14 * mm,
        "TB-Helfer Server 1.0 · Aktivierungskarte enthält einen geheimen Einmal-Schlüssel.",
    )

    pdf.showPage()
    pdf.save()
    return buffer.getvalue()


def device_activation_pdf_data_url(**kwargs) -> str:
    raw = device_activation_pdf_bytes(**kwargs)
    encoded = base64.b64encode(raw).decode("ascii")
    return f"data:application/pdf;base64,{encoded}"
