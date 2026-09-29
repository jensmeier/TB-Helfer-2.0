from __future__ import annotations

import math
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any, Iterable

from PIL import Image as PILImage, ImageOps
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen import canvas as pdfcanvas


PHOTO_ORDER = (
    "ABNAHMEN",
    "EINWEISUNG_UNTERWEISUNG",
    "BESONDERHEITEN",
    "MAENGEL_SCHAEDEN",
    "FOTODOKUMENTATION",
)
PHOTO_SECTION = {
    "FOTODOKUMENTATION": "ARBEIT",
    "ABNAHMEN": "ABNAHME",
    "EINWEISUNG_UNTERWEISUNG": "EINWEISUNG_UNTERWEISUNG",
    "BESONDERHEITEN": "BESONDERHEIT",
    "MAENGEL_SCHAEDEN": "MANGEL_SCHADEN",
}
SECTION_ALIASES = {
    "4 ARBEITEN": "ARBEIT",
    "4. ARBEITEN": "ARBEIT",
    "4 AUSGEFÜHRTE ARBEITEN": "ARBEIT",
    "4. AUSGEFÜHRTE ARBEITEN": "ARBEIT",
    "5 ABNAHMEN": "ABNAHME",
    "5. ABNAHMEN": "ABNAHME",
    "6 EINWEISUNG/UNTERWEISUNG": "EINWEISUNG_UNTERWEISUNG",
    "6. EINWEISUNG/UNTERWEISUNG": "EINWEISUNG_UNTERWEISUNG",
    "7 BESONDERHEITEN": "BESONDERHEIT",
    "7. BESONDERHEITEN": "BESONDERHEIT",
    "8 MÄNGEL/SCHÄDEN": "MANGEL_SCHADEN",
    "8. MÄNGEL/SCHÄDEN": "MANGEL_SCHADEN",
}
SECTION_TITLES = (
    ("ARBEIT", "4. Ausgeführte Arbeiten", "Keine ausgeführten Arbeiten eingetragen."),
    ("ABNAHME", "5. Abnahmen", "Keine Abnahmen durchgeführt."),
    ("EINWEISUNG_UNTERWEISUNG", "6. Einweisung/Unterweisung", "Keine Einweisung/Unterweisung durchgeführt."),
    ("BESONDERHEIT", "7. Besonderheiten", "Keine Besonderheiten."),
    ("MANGEL_SCHADEN", "8. Mängel/Schäden", "Keine Mängel/Schäden gemeldet."),
)
MANAGED_KEYS = {"_tb_android_managed", "_tb_android_slot"}
PAGE_W, PAGE_H = A4
MARGIN_L = 40.0
MARGIN_R = 40.0
MARGIN_TOP = 44.0
MARGIN_BOTTOM = 52.0
CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R


def _text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def _bool(value: Any, default: bool = True) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() not in {"0", "false", "nein", "no", "off"}


def _number(value: Any) -> float | None:
    try:
        n = float(value)
    except (TypeError, ValueError):
        return None
    return n if math.isfinite(n) else None


def _android_meta(header: Any) -> dict[str, Any]:
    if not isinstance(header, dict):
        return {}
    nested = header.get("tb_helfer_android_v1")
    if isinstance(nested, dict):
        return nested
    return header


def _date_de(value: Any) -> str:
    raw = _text(value)
    if not raw:
        return "-"
    try:
        return datetime.fromisoformat(raw.replace("Z", "+00:00")).strftime("%d.%m.%Y")
    except ValueError:
        try:
            return datetime.strptime(raw[:10], "%Y-%m-%d").strftime("%d.%m.%Y")
        except ValueError:
            return raw


def _time_hm(value: Any) -> str:
    raw = _text(value)
    if not raw:
        return "-"
    try:
        return datetime.fromisoformat(raw.replace("Z", "+00:00")).strftime("%H:%M")
    except ValueError:
        match = re.search(r"(\d{1,2}:\d{2})", raw)
        return match.group(1) if match else raw


def _shift_label(value: Any) -> str:
    raw = _text(value).upper()
    return {"TAG": "Tag", "NACHT": "Nacht", "FRUEH": "Früh", "SPAET": "Spät"}.get(raw, raw.title() or "-")


def _km_value(value: Any) -> str | None:
    n = _number(value)
    if n is None:
        return None
    return f"{n:.3f}".replace(".", ",")


def _km_range(von: Any, bis: Any = None) -> str | None:
    a = _km_value(von)
    b = _km_value(bis)
    if a and b and a != b:
        return f"KM {a}-{b}"
    if a:
        return f"KM {a}"
    if b:
        return f"KM {b}"
    return None


def _clean_marker_text(raw: Any) -> str:
    value = _text(raw)
    value = re.sub(r"@ORT=[^\s]+", " ", value)
    value = value.replace("@ALLGEMEIN", " ")
    value = re.sub(r"^[\s•\-–—]+", "", value)
    return re.sub(r"\s+", " ", value).strip()


def _strip_company_prefix(value: str, known_names: Iterable[str]) -> tuple[str | None, str]:
    clean = _clean_marker_text(value)
    if ":" not in clean:
        return None, clean
    prefix, tail = clean.split(":", 1)
    prefix = prefix.strip()
    tail = tail.strip()
    if not prefix or not tail:
        return None, clean
    for name in known_names:
        if prefix.casefold() == name.casefold():
            return name, tail
    if len(prefix) <= 120 and not re.fullmatch(r"\d{1,2}", prefix):
        return prefix, tail
    return None, clean


def _wrap_text(value: str, font: str, size: float, max_width: float) -> list[str]:
    value = re.sub(r"\s+", " ", _text(value))
    if not value:
        return [""]
    words = value.split(" ")
    lines: list[str] = []
    line = ""
    for word in words:
        candidate = word if not line else f"{line} {word}"
        if stringWidth(candidate, font, size) <= max_width:
            line = candidate
            continue
        if line:
            lines.append(line)
            line = ""
        while word and stringWidth(word, font, size) > max_width:
            cut = len(word)
            while cut > 1 and stringWidth(word[:cut], font, size) > max_width:
                cut -= 1
            lines.append(word[:cut])
            word = word[cut:]
        line = word
    if line:
        lines.append(line)
    return lines or [""]


def _payload(entry: dict[str, Any]) -> dict[str, Any]:
    raw = entry.get("payload")
    return raw if isinstance(raw, dict) else {}


def _entry_signature(entry: dict[str, Any]) -> tuple[Any, Any, Any]:
    return entry.get("section"), entry.get("sort_order"), _payload(entry)


def _changed_entry_ids(snapshot: dict[str, Any], original_snapshot: dict[str, Any] | None) -> set[str]:
    if not original_snapshot:
        return set()
    original = {str(item.get("id")): item for item in original_snapshot.get("entries") or []}
    changed: set[str] = set()
    for item in snapshot.get("entries") or []:
        old = original.get(str(item.get("id")))
        if old is None or _entry_signature(item) != _entry_signature(old):
            changed.add(str(item.get("id")))
    return changed


def _removed_entries(snapshot: dict[str, Any], original_snapshot: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not original_snapshot:
        return []
    current_ids = {str(item.get("id")) for item in snapshot.get("entries") or []}
    return [item for item in original_snapshot.get("entries") or [] if str(item.get("id")) not in current_ids]


def _changed_photo_ids(snapshot: dict[str, Any], original_snapshot: dict[str, Any] | None) -> set[str]:
    if not original_snapshot:
        return set()
    original = {str(item.get("id")): item for item in original_snapshot.get("photos") or []}
    changed: set[str] = set()
    for item in snapshot.get("photos") or []:
        old = original.get(str(item.get("id")))
        if old is None or (item.get("sha256"), item.get("photo_metadata"), item.get("link_version")) != (
            old.get("sha256"), old.get("photo_metadata"), old.get("link_version")
        ):
            changed.add(str(item.get("id")))
    return changed


def _node_maps(context: dict[str, Any]) -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    nodes = context.get("worksite_nodes") or []
    by_key: dict[str, dict[str, Any]] = {}
    parent_key: dict[str, str] = {}
    id_to_key: dict[str, str] = {}
    for raw in nodes:
        if not isinstance(raw, dict):
            continue
        node_id = _text(raw.get("id"))
        external = _text(raw.get("external_key"))
        key = external or (f"server-{node_id}" if node_id else "")
        if not key:
            continue
        id_to_key[node_id] = key
        by_key[key] = raw
        if node_id:
            by_key[node_id] = raw
            by_key[f"server-{node_id}"] = raw
    for raw in nodes:
        if not isinstance(raw, dict):
            continue
        node_id = _text(raw.get("id"))
        external = _text(raw.get("external_key"))
        key = external or (f"server-{node_id}" if node_id else "")
        parent_id = _text(raw.get("parent_id"))
        if key and parent_id and parent_id in id_to_key:
            parent_key[key] = id_to_key[parent_id]
    return by_key, parent_key


def _node_label(node: dict[str, Any] | None) -> str:
    if not node:
        return "Hauptbaustelle"
    payload = node.get("payload") if isinstance(node.get("payload"), dict) else {}
    kind = (_text(payload.get("node_type")) or _text(node.get("node_kind"))).upper()
    node_name = _text(payload.get("node_name")) or _text(node.get("name"))
    route = _text(payload.get("route_key")) or _text(node.get("route_number"))
    station = _text(payload.get("station_name"))
    track = _text(payload.get("track_or_switch")) or _text(node.get("track_key"))
    km = _km_range(payload.get("km_from"), payload.get("km_to"))
    if kind in {"HAUPT", "ROOT", "MAIN"}:
        base = "Hauptbaustelle"
    elif kind in {"FREIE_STRECKE", "ROUTE", "TRACK"}:
        base = node_name or (f"Freie Strecke {route}" if route else "Freie Strecke")
    elif kind in {"BAHNHOF", "STATION"}:
        if node_name:
            base = node_name
        elif station and track:
            base = f"Bahnhof {station} - {track}"
        elif station:
            base = f"Bahnhof {station}"
        else:
            base = "Bahnhof"
    elif kind in {"ALLGEMEIN_OBJEKT", "OBJECT", "OBJEKT"}:
        base = node_name or "Allgemein / Objekt"
    else:
        base = node_name or station or route or "Hauptbaustelle"
    return f"{base} ({km})" if km and "(KM " not in base else base


@dataclass
class SectionLine:
    company: str | None
    node_key: str | None
    text: str
    changed: bool = False


@dataclass
class FirmBlock:
    firm_id: str
    name: str
    personal_from: str | None = None
    personal_to: str | None = None
    machine_from: str | None = None
    machine_to: str | None = None
    visible_personal: bool = True
    visible_machine: bool = True
    deleted: bool = False


def _firms(entries: list[dict[str, Any]]) -> dict[str, FirmBlock]:
    result: dict[str, FirmBlock] = {}
    for entry in entries:
        if _text(entry.get("section")).upper() != "FIRMA":
            continue
        payload = _payload(entry)
        firm_id = _text(payload.get("firmaId")) or _text(entry.get("id"))
        result[firm_id] = FirmBlock(
            firm_id=firm_id,
            name=_text(payload.get("name")) or "Firma",
            personal_from=_text(payload.get("personalVon")) or None,
            personal_to=_text(payload.get("personalBis")) or None,
            machine_from=_text(payload.get("maschinenVon")) or None,
            machine_to=_text(payload.get("maschinenBis")) or None,
            visible_personal=_bool(payload.get("visibleInPersonal"), True),
            visible_machine=_bool(payload.get("visibleInMaschinen"), True),
            deleted=_bool(payload.get("isDeleted"), False),
        )
    return result


def _company_name(payload: dict[str, Any], firms: dict[str, FirmBlock]) -> str | None:
    explicit = _text(payload.get("firmaName"))
    if explicit:
        return explicit
    firm_id = _text(payload.get("firmaId"))
    block = firms.get(firm_id)
    return block.name if block and not block.deleted else None


def _personnel_groups(entries: list[dict[str, Any]], firms: dict[str, FirmBlock], *, machine: bool) -> list[tuple[str, list[str]]]:
    section = "GERAET" if machine else "PERSONAL"
    structured = [
        entry
        for entry in entries
        if _text(entry.get("section")).upper() == section
        and _text(_payload(entry).get("firmaId"))
    ]
    result: list[tuple[str, list[str]]] = []
    if structured:
        by_firm: dict[str, list[dict[str, Any]]] = {}
        for entry in structured:
            by_firm.setdefault(_text(_payload(entry).get("firmaId")), []).append(entry)
        for firm_id, block in firms.items():
            if block.deleted:
                continue
            if machine and not block.visible_machine:
                continue
            if not machine and not block.visible_personal:
                continue
            values = by_firm.get(firm_id, [])
            lines: list[str] = []
            for item in sorted(values, key=lambda entry: (entry.get("sort_order", 0), str(entry.get("id")))):
                payload = _payload(item)
                count = _text(payload.get("anzahl"))
                label = _text(payload.get("bezeichnung" if machine else "rolle"))
                value = " ".join(part for part in (count, label) if part)
                if value:
                    lines.append(value)
            if not lines:
                continue
            start = block.machine_from if machine else block.personal_from
            end = block.machine_to if machine else block.personal_to
            header = block.name
            if start and end:
                header = f"{start} - {end} {header}"
            elif start:
                header = f"{start} - {header}"
            result.append((header, lines))
        return result

    free = []
    for entry in entries:
        if _text(entry.get("section")).upper() != section:
            continue
        value = _clean_marker_text(_payload(entry).get("text"))
        if value:
            free.append(value)
    if free:
        return [("", free)]
    return []


def _legacy_work_lines(raw: str, known_names: list[str], node_lookup: dict[str, dict[str, Any]]) -> list[SectionLine]:
    normalized = raw.replace("\r", "\n")
    chunks = re.split(r"(?=@ORT=)|[\n]+|\s+•\s+", normalized)
    lines: list[SectionLine] = []
    for chunk in chunks:
        if not chunk.strip():
            continue
        marker = re.search(r"@ORT=([^\s]+)", chunk)
        node_key = marker.group(1) if marker else None
        if node_key == "0":
            node_key = None
        company, value = _strip_company_prefix(chunk, known_names)
        if value:
            if node_key and node_key not in node_lookup:
                node_key = None
            lines.append(SectionLine(company, node_key, value))
    return lines


def _photo_numbering(snapshot: dict[str, Any], meta: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, int]]:
    photos = [photo for photo in snapshot.get("photos") or [] if isinstance(photo, dict)]
    max_count = int(_number(meta.get("maxFotosInPdf")) or 60)
    max_count = max(1, max_count)
    ordered: list[dict[str, Any]] = []
    for photo_type in PHOTO_ORDER:
        same = [
            photo
            for photo in photos
            if _text((photo.get("photo_metadata") or {}).get("typ")).upper() == photo_type
        ]
        same.sort(key=lambda photo: (_text(photo.get("captured_at")), str(photo.get("id"))))
        ordered.extend(same)
    known_ids = {str(photo.get("id")) for photo in ordered}
    leftovers = [photo for photo in photos if str(photo.get("id")) not in known_ids]
    leftovers.sort(key=lambda photo: (_text(photo.get("captured_at")), str(photo.get("id"))))
    ordered.extend(leftovers)
    ordered = ordered[:max_count]
    return ordered, {str(photo.get("id")): index for index, photo in enumerate(ordered, 1)}


def _photo_section_lines(
    snapshot: dict[str, Any],
    meta: dict[str, Any],
    firms: dict[str, FirmBlock],
    changed_photo_ids: set[str],
) -> dict[str, list[SectionLine]]:
    ordered, numbers = _photo_numbering(snapshot, meta)
    by_section: dict[str, list[SectionLine]] = {key: [] for key, _, _ in SECTION_TITLES}
    grouped: dict[tuple[str, str, str, str], list[dict[str, Any]]] = {}
    for photo in ordered:
        metadata = photo.get("photo_metadata") if isinstance(photo.get("photo_metadata"), dict) else {}
        photo_type = _text(metadata.get("typ")).upper() or "FOTODOKUMENTATION"
        section = PHOTO_SECTION.get(photo_type, "ARBEIT")
        company = _text(metadata.get("firma"))
        node_key = _text(metadata.get("worksiteNodeKey"))
        short = _text(metadata.get("notiz")) or _text(metadata.get("kurztext")) or "Foto"
        grouped.setdefault((section, company, node_key, short), []).append(photo)

    def epoch(photo: dict[str, Any]) -> float:
        raw = _text(photo.get("captured_at"))
        try:
            return datetime.fromisoformat(raw.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return 0.0

    for (section, company, node_key, short), photos in grouped.items():
        photos.sort(key=epoch)
        clusters: list[list[dict[str, Any]]] = []
        for photo in photos:
            if not clusters or epoch(photo) - epoch(clusters[-1][-1]) > 5 * 60:
                clusters.append([photo])
            else:
                clusters[-1].append(photo)
        for cluster in clusters:
            first, last = cluster[0], cluster[-1]
            first_time = _time_hm(first.get("captured_at"))
            last_time = _time_hm(last.get("captured_at"))
            time_text = first_time if first_time == last_time else f"{first_time}-{last_time}"
            kms = [_number((photo.get("photo_metadata") or {}).get("km")) for photo in cluster]
            kms = [km for km in kms if km is not None]
            km_text = _km_range(min(kms), max(kms)) if kms else None
            nums = sorted(
                numbers[str(photo.get("id"))]
                for photo in cluster
                if str(photo.get("id")) in numbers
            )
            image_label = ""
            if nums:
                segments: list[str] = []
                start = end = nums[0]
                for number in nums[1:]:
                    if number == end + 1:
                        end = number
                    else:
                        segments.append(str(start) if start == end else f"{start}-{end}")
                        start = end = number
                segments.append(str(start) if start == end else f"{start}-{end}")
                image_label = f" [Bild {'+'.join(segments)}]"
            value = " ".join(
                part
                for part in (time_text, f"({km_text})" if km_text else None, short)
                if part
            ) + image_label
            changed = any(str(photo.get("id")) in changed_photo_ids for photo in cluster)
            by_section.setdefault(section, []).append(
                SectionLine(company or None, node_key or None, value, changed)
            )
    return by_section


def _section_lines(
    snapshot: dict[str, Any],
    original_snapshot: dict[str, Any] | None,
    context: dict[str, Any],
) -> tuple[dict[str, list[SectionLine]], list[dict[str, Any]], dict[str, FirmBlock]]:
    entries = [entry for entry in snapshot.get("entries") or [] if isinstance(entry, dict)]
    firms = _firms(entries)
    known_names = [firm.name for firm in firms.values() if not firm.deleted]
    changed_ids = _changed_entry_ids(snapshot, original_snapshot)
    changed_photo_ids = _changed_photo_ids(snapshot, original_snapshot)
    node_lookup, _ = _node_maps(context)
    result: dict[str, list[SectionLine]] = {key: [] for key, _, _ in SECTION_TITLES}

    section_names = {key for key, _, _ in SECTION_TITLES}
    structured_work = False
    for entry in entries:
        section = _text(entry.get("section")).upper()
        section = SECTION_ALIASES.get(section, section)
        if section not in section_names:
            continue
        structured_work = structured_work or section == "ARBEIT"
        payload = _payload(entry)
        company = _company_name(payload, firms)
        prefix_company, clean_text = _strip_company_prefix(payload.get("text"), known_names)
        company = company or prefix_company
        time = _text(payload.get("uhrzeit"))
        km = _km_range(payload.get("kmVon"), payload.get("kmBis"))
        value = " ".join(
            part
            for part in (time, f"({km})" if km else None, clean_text)
            if part
        ).strip()
        if not value:
            visible = []
            for key, item in payload.items():
                if key in MANAGED_KEYS or key in {"firmaId", "firmaName", "worksiteNodeKey", "clientEntryKey"}:
                    continue
                if item in (None, "", [], {}):
                    continue
                visible.append(f"{key}: {item}")
            value = " · ".join(visible)
        if value:
            result[section].append(
                SectionLine(
                    company=company,
                    node_key=_text(payload.get("worksiteNodeKey")) or None,
                    text=value,
                    changed=str(entry.get("id")) in changed_ids,
                )
            )

    if not structured_work:
        for entry in entries:
            if _text(entry.get("section")).upper() != "NOTIZEN":
                continue
            raw = _text(_payload(entry).get("text"))
            for line in _legacy_work_lines(raw, known_names, node_lookup):
                line.changed = str(entry.get("id")) in changed_ids
                result["ARBEIT"].append(line)

    photo_lines = _photo_section_lines(
        snapshot,
        _android_meta((snapshot.get("btb") or {}).get("header")),
        firms,
        changed_photo_ids,
    )
    for section, lines in photo_lines.items():
        result.setdefault(section, []).extend(lines)

    return result, _removed_entries(snapshot, original_snapshot), firms


class AndroidStyleWriter:
    def __init__(self, target: str | Path, *, context: dict[str, Any], work_date: str):
        self.target = str(target)
        self.c = pdfcanvas.Canvas(self.target, pagesize=A4, pageCompression=1)
        self.context = context
        self.work_date = work_date
        self.page_no = 1
        self.y = MARGIN_TOP
        self.c.setTitle("Bautagesbericht")
        self.c.setAuthor(_text(context.get("company_name")) or "TB-Helfer")

    @staticmethod
    def pdf_y(top_y: float) -> float:
        return PAGE_H - top_y

    def ensure(self, height: float, *, new_page_if_needed: bool = True) -> bool:
        if self.y + height <= PAGE_H - MARGIN_BOTTOM:
            return True
        if not new_page_if_needed:
            return False
        self.new_page()
        return True

    def footer(self) -> None:
        self.c.setFillColor(colors.HexColor("#555555"))
        self.c.setFont("Helvetica", 8.2)
        creator = _text(self.context.get("lead_name")) or "TB"
        left = f"Erstellt von: {creator} · {_date_de(self.work_date)}"
        self.c.drawString(MARGIN_L, 22.0, left[:90])
        self.c.drawRightString(PAGE_W - MARGIN_R, 22.0, f"Seite {self.page_no}")
        self.c.setFillColor(colors.black)

    def new_page(self) -> None:
        self.footer()
        self.c.showPage()
        self.page_no += 1
        self.y = MARGIN_TOP

    def finish(self) -> None:
        self.footer()
        self.c.save()

    def draw_text(
        self,
        value: str,
        *,
        x: float = MARGIN_L,
        width: float = CONTENT_W,
        font: str = "Helvetica",
        size: float = 9.0,
        leading: float | None = None,
        color=colors.black,
        indent: float = 0.0,
        bullet: bool = False,
        allow_page_break: bool = True,
    ) -> float:
        leading = leading or size * 1.22
        actual_x = x + indent
        actual_width = width - indent
        bullet_indent = 10.0 if bullet else 0.0
        lines = _wrap_text(value, font, size, max(20.0, actual_width - bullet_indent))
        total = max(1, len(lines)) * leading
        if allow_page_break:
            self.ensure(total)
        self.c.setFillColor(color)
        self.c.setFont(font, size)
        for index, line in enumerate(lines):
            if bullet and index == 0:
                self.c.drawString(actual_x, self.pdf_y(self.y), "•")
            self.c.drawString(actual_x + bullet_indent, self.pdf_y(self.y), line)
            self.y += leading
        self.c.setFillColor(colors.black)
        return total

    def draw_box_header(
        self,
        title: str,
        *,
        x: float,
        width: float,
        top: float,
        height: float = 22.0,
        title_color=colors.black,
    ) -> None:
        y_bottom = self.pdf_y(top + height)
        self.c.setFillColor(colors.HexColor("#F1F1F1"))
        self.c.rect(x, y_bottom, width, height, fill=1, stroke=0)
        self.c.setFillColor(title_color)
        self.c.setFont("Helvetica-Bold", 10.5)
        self.c.drawString(x + 7.0, self.pdf_y(top + 15.0), title)
        self.c.setFillColor(colors.black)

    def draw_frame(self, x: float, top: float, width: float, bottom_top_y: float) -> None:
        height = max(20.0, bottom_top_y - top)
        self.c.setLineWidth(0.8)
        self.c.setStrokeColor(colors.HexColor("#444444"))
        self.c.rect(x, self.pdf_y(top + height), width, height, fill=0, stroke=1)
        self.c.setStrokeColor(colors.black)


def _draw_company_header(writer: AndroidStyleWriter, context: dict[str, Any]) -> None:
    canvas = writer.c
    canvas.setFillColor(colors.black)
    canvas.setFont("Helvetica-Bold", 18)
    canvas.drawString(MARGIN_L, writer.pdf_y(MARGIN_TOP + 10), "BAUTAGEBUCH")

    layout = context.get("pdf_logo_layout") if isinstance(context.get("pdf_logo_layout"), dict) else {}
    width_pt = max(40.0, min(400.0, _number(layout.get("logo_width_pt")) or 90.0))
    offset_x = max(-120.0, min(120.0, _number(layout.get("offset_x_pt")) or 0.0))
    offset_y = max(-60.0, min(180.0, _number(layout.get("offset_y_pt")) or 0.0))
    show_name = _bool(layout.get("show_company_name"), True)
    show_email = _bool(layout.get("show_company_email"), True)
    show_phone = _bool(layout.get("show_company_phone"), True)
    contacts_in_image = _bool(layout.get("contacts_in_image"), False)

    # Der Anker bleibt im rechten Kopfbereich, große Logos dürfen aber bis 400 pt
    # in die Seite hineinragen. Nur die tatsächlichen A4-Seitenränder begrenzen
    # die Position. So bleibt 400 pt ein echter Regelbereich statt nur ein UI-Wert.
    left = MARGIN_L + CONTENT_W * 0.53
    right = PAGE_W - MARGIN_R
    box_width = right - left
    page_left = 8.0
    page_right = PAGE_W - 8.0
    logo_path = context.get("company_logo_path")
    text_top = MARGIN_TOP + 12.0

    if isinstance(logo_path, Path) and logo_path.is_file():
        try:
            image = ImageReader(str(logo_path))
            image_width, image_height = image.getSize()
            draw_width = min(width_pt, page_right - page_left)
            draw_height = draw_width * image_height / image_width

            # A4-Sicherheitsgrenze: das Logo darf nie die Seite verlassen.
            max_height = PAGE_H - 16.0
            if draw_height > max_height:
                scale = max_height / draw_height
                draw_width *= scale
                draw_height *= scale

            center_x = left + box_width / 2.0 + offset_x
            x = center_x - draw_width / 2.0
            x = min(max(page_left, x), page_right - draw_width)

            # Y ist bewusst ein echter Versatz vom bisherigen Kopfanker.
            # Der alte Code klemmte bei hohen/quadratischen Logos beide Grenzen
            # auf denselben Wert und machte den Regler dadurch wirkungslos.
            base_top = MARGIN_TOP + 4.0
            top_y = base_top + offset_y
            top_y = min(max(8.0, top_y), PAGE_H - 8.0 - draw_height)

            canvas.setFillColor(colors.white)
            canvas.rect(
                x,
                writer.pdf_y(top_y + draw_height),
                draw_width,
                draw_height,
                fill=1,
                stroke=0,
            )
            canvas.drawImage(
                image,
                x,
                writer.pdf_y(top_y + draw_height),
                width=draw_width,
                height=draw_height,
                preserveAspectRatio=True,
                mask="auto",
            )
            text_top = top_y + draw_height + 8.0
        except Exception:
            pass

    lines: list[str] = []
    if show_name and _text(context.get("company_name")):
        lines.append(_text(context.get("company_name")))
    if not contacts_in_image:
        if show_email and _text(context.get("company_email")):
            lines.append(_text(context.get("company_email")))
        if show_phone and _text(context.get("company_phone")):
            lines.append(_text(context.get("company_phone")))

    canvas.setFont("Helvetica", 8.2)
    text_center_x = min(
        max(page_left + 20.0, left + box_width / 2.0 + offset_x),
        page_right - 20.0,
    )
    for line in lines[:3]:
        canvas.drawCentredString(
            text_center_x,
            writer.pdf_y(text_top),
            line[:80],
        )
        text_top += 9.5



def _draw_document_header(
    writer: AndroidStyleWriter,
    snapshot: dict[str, Any],
    context: dict[str, Any],
) -> None:
    # Firmenlogo und Fachlayout sind absichtlich entkoppelt:
    # Breite/X/Y des Logos dürfen Wetter/Personal/Fachpunkte nicht verschieben.
    _draw_company_header(writer, context)
    btb = snapshot.get("btb") or {}
    meta = _android_meta(btb.get("header"))
    writer.y = MARGIN_TOP + 48.0
    writer.draw_text(
        f"Baustelle: {_text(context.get('worksite_name')) or '-'}",
        font="Helvetica-Bold",
        size=9.0,
    )
    writer.draw_text(
        f"Verantwortlicher TB: {_text(context.get('lead_name')) or '-'}",
        size=9.0,
    )
    if _text(context.get("lead_email")):
        writer.draw_text(f"E-Mail: {_text(context.get('lead_email'))}", size=8.5)
    if _text(context.get("lead_phone")):
        writer.draw_text(f"Mobil: {_text(context.get('lead_phone'))}", size=8.5)
    writer.draw_text(f"Datum: {_date_de(btb.get('work_date'))}", size=9.0)
    writer.draw_text(f"Schicht: {_shift_label(meta.get('schicht'))}", size=9.0)
    writer.draw_text(
        f"Zeit: {_time_hm(btb.get('started_at'))} - {_time_hm(btb.get('ended_at'))}",
        size=9.0,
    )
    writer.y += 6.0


def _weather_rows(meta: dict[str, Any]) -> tuple[str, list[tuple[str, str, str, str]]]:
    weather = meta.get("weather") if isinstance(meta.get("weather"), dict) else {}
    slots = weather.get("slots") if isinstance(weather.get("slots"), list) else []
    if not slots and isinstance(meta.get("weatherSlots"), list):
        slots = meta.get("weatherSlots")

    source_bits: list[str] = []
    location = _text(weather.get("locationName"))
    station = _text(weather.get("stationName"))
    if location:
        source_bits.append(f"Ort: {location}")
    elif station:
        source_bits.append(f"Station: {station}")

    stand = weather.get("standEpochMs")
    if stand is not None:
        try:
            source_bits.append(
                "Stand: " + datetime.fromtimestamp(float(stand) / 1000.0).strftime("%H:%M")
            )
        except (TypeError, ValueError, OSError, OverflowError):
            pass

    source = _text(weather.get("source")) or _text(meta.get("wetter"))
    if source:
        source_bits.append(f"Quelle: {source}")

    rows: list[tuple[str, str, str, str]] = []
    for raw in slots:
        if not isinstance(raw, dict):
            continue
        time = _text(raw.get("uhrzeit"))[:5]
        temp = _text(raw.get("tempC"))
        temp = f"{temp}°C" if temp else "-"
        cloud = _text(raw.get("bewoelkung")) or "-"
        rain = _text(raw.get("niederschlag")) or "-"
        rain = rain.replace("trocken (kein Niederschlag)", "trocken")
        rows.append((time or "-", temp, cloud, rain))
    return "   ".join(source_bits), rows


def _draw_weather(writer: AndroidStyleWriter, meta: dict[str, Any]) -> None:
    source, rows = _weather_rows(meta)
    box_width = (CONTENT_W - 12.0) / 2.0
    gap_x = 4.0
    columns = (
        31.0,
        30.0,
        66.0,
        box_width - 12.0 - 31.0 - 30.0 - 66.0 - 3 * gap_x,
    )
    source_lines = (
        _wrap_text(source, "Helvetica", 7.5, box_width - 12.0)
        if source
        else []
    )
    height = (
        22.0
        + (len(source_lines) * 9.0 if source_lines else 0.0)
        + (14.0 if rows else 0.0)
        + max(1, len(rows)) * 12.0
        + 9.0
    )
    writer.ensure(height)
    top = writer.y
    x = MARGIN_L
    writer.draw_box_header("Wetter", x=x, width=box_width, top=top)
    y = top + 31.0

    writer.c.setFont("Helvetica", 7.5)
    writer.c.setFillColor(colors.HexColor("#555555"))
    for line in source_lines:
        writer.c.drawString(x + 6.0, writer.pdf_y(y), line)
        y += 9.0
    writer.c.setFillColor(colors.black)

    if rows:
        xs = [x + 6.0]
        for width in columns[:-1]:
            xs.append(xs[-1] + width + gap_x)
        writer.c.setFont("Helvetica", 7.2)
        for xpos, label in zip(xs, ("Zeit", "Temp", "Bewölkung", "Niederschlag")):
            writer.c.drawString(xpos, writer.pdf_y(y), label)
        y += 11.0

        for row in rows:
            writer.c.setFont("Helvetica", 7.4)
            for index, value in enumerate(row):
                clipped = value
                max_width = columns[index] - 3.0
                while (
                    len(clipped) > 1
                    and stringWidth(clipped, "Helvetica", 7.4) > max_width
                ):
                    clipped = clipped[:-1]
                if clipped != value:
                    clipped = clipped[:-1] + "…" if len(clipped) > 1 else clipped
                writer.c.drawString(xs[index], writer.pdf_y(y), clipped)
            y += 12.0
    else:
        writer.c.setFont("Helvetica", 8.5)
        writer.c.drawString(x + 6.0, writer.pdf_y(y), "-")
        y += 12.0

    bottom = top + height
    writer.draw_frame(x, top, box_width, bottom)
    writer.y = bottom + 10.0


def _measure_group_lines(groups: list[tuple[str, list[str]]], width: float) -> float:
    height = 30.0
    if not groups:
        return height + 16.0
    for header, lines in groups:
        if header:
            height += len(
                _wrap_text(header, "Helvetica-Bold", 8.3, width - 12.0)
            ) * 9.4
        for line in lines:
            height += len(
                _wrap_text(line, "Helvetica", 8.2, width - 22.0)
            ) * 9.2
        height += 2.0
    return height + 6.0


def _draw_people_machines(
    writer: AndroidStyleWriter,
    entries: list[dict[str, Any]],
    firms: dict[str, FirmBlock],
) -> None:
    gap = 12.0
    column_width = (CONTENT_W - gap) / 2.0
    personal = _personnel_groups(entries, firms, machine=False)
    machines = _personnel_groups(entries, firms, machine=True)
    personal_height = _measure_group_lines(personal, column_width)
    machine_height = _measure_group_lines(machines, column_width)
    max_height = max(personal_height, machine_height)
    writer.ensure(max_height)
    top = writer.y

    def draw_box(
        title: str,
        x: float,
        groups: list[tuple[str, list[str]]],
        height: float,
    ) -> None:
        writer.draw_box_header(title, x=x, width=column_width, top=top)
        y = top + 31.0
        if not groups:
            writer.c.setFont("Helvetica", 8.4)
            writer.c.drawString(x + 6.0, writer.pdf_y(y), "-")
        for header, lines in groups:
            if header:
                writer.c.setFont("Helvetica-Bold", 8.3)
                for line in _wrap_text(
                    header,
                    "Helvetica-Bold",
                    8.3,
                    column_width - 12.0,
                ):
                    writer.c.drawString(x + 6.0, writer.pdf_y(y), line)
                    y += 9.4
            writer.c.setFont("Helvetica", 8.2)
            for value in lines:
                wrapped = _wrap_text(
                    value,
                    "Helvetica",
                    8.2,
                    column_width - 22.0,
                )
                for index, line in enumerate(wrapped):
                    if index == 0:
                        writer.c.drawString(x + 6.0, writer.pdf_y(y), "•")
                    writer.c.drawString(x + 15.0, writer.pdf_y(y), line)
                    y += 9.2
            y += 2.0
        writer.draw_frame(x, top, column_width, top + height)

    draw_box("Personal", MARGIN_L, personal, personal_height)
    draw_box(
        "Maschinen/Geräte",
        MARGIN_L + column_width + gap,
        machines,
        machine_height,
    )
    writer.y = top + max_height + 10.0


def _section_render_rows(
    lines: list[SectionLine],
    context: dict[str, Any],
) -> list[tuple[str, str, Any]]:
    node_lookup, _ = _node_maps(context)
    rows: list[tuple[str, str, Any]] = []
    if not lines:
        return rows

    grouped: dict[tuple[str | None, str | None], list[SectionLine]] = {}
    order: list[tuple[str | None, str | None]] = []
    for line in lines:
        key = (line.company, line.node_key)
        if key not in grouped:
            grouped[key] = []
            order.append(key)
        grouped[key].append(line)

    last_company: str | None = object()  # type: ignore[assignment]
    for company, node_key in order:
        items = grouped[(company, node_key)]
        group_color = colors.red if any(item.changed for item in items) else colors.black
        if company != last_company:
            rows.append(("company", company or "Allgemein", group_color))
            last_company = company
        node = node_lookup.get(node_key or "") if node_key else None
        rows.append(("node", _node_label(node), group_color))
        for item in items:
            rows.append(
                ("bullet", item.text, colors.red if item.changed else colors.black)
            )
    return rows


def _row_height(kind: str, value: str, width: float) -> float:
    if kind == "company":
        return len(
            _wrap_text(value, "Helvetica-Bold", 8.6, width - 16.0)
        ) * 10.2 + 2.0
    if kind == "node":
        return len(
            _wrap_text(value, "Helvetica-Bold", 8.1, width - 28.0)
        ) * 9.5 + 1.0
    return len(
        _wrap_text(value, "Helvetica", 8.2, width - 36.0)
    ) * 9.6 + 1.0


def _draw_section(
    writer: AndroidStyleWriter,
    title: str,
    lines: list[SectionLine],
    empty_text: str,
    context: dict[str, Any],
) -> None:
    rows = _section_render_rows(lines, context)
    section_changed = any(item.changed for item in lines)
    if not rows:
        rows = [("plain", empty_text, colors.black)]

    index = 0
    continuation = False
    while index < len(rows):
        if PAGE_H - MARGIN_BOTTOM - writer.y < 90.0:
            writer.new_page()

        top = writer.y
        y = top + 31.0
        start_index = index
        while index < len(rows):
            kind, value, _ = rows[index]
            height = _row_height(kind, value, CONTENT_W)
            if y + height + 9.0 > PAGE_H - MARGIN_BOTTOM and index > start_index:
                break
            y += height
            index += 1

        bottom = y + 6.0
        label = f"{title} (Fortsetzung)" if continuation else title
        writer.draw_box_header(
            label,
            x=MARGIN_L,
            width=CONTENT_W,
            top=top,
            title_color=colors.red if section_changed else colors.black,
        )

        y_draw = top + 31.0
        for kind, value, color in rows[start_index:index]:
            if kind == "company":
                font, size, x = "Helvetica-Bold", 8.6, MARGIN_L + 8.0
                max_width = CONTENT_W - 16.0
                bullet = False
            elif kind == "node":
                font, size, x = "Helvetica-Bold", 8.1, MARGIN_L + 18.0
                max_width = CONTENT_W - 28.0
                bullet = False
            elif kind == "plain":
                font, size, x = "Helvetica", 8.2, MARGIN_L + 8.0
                max_width = CONTENT_W - 16.0
                bullet = False
            else:
                font, size, x = "Helvetica", 8.2, MARGIN_L + 18.0
                max_width = CONTENT_W - 36.0
                bullet = True

            wrapped = _wrap_text(
                value,
                font,
                size,
                max_width - (10.0 if bullet else 0.0),
            )
            writer.c.setFillColor(color)
            writer.c.setFont(font, size)
            for line_index, part in enumerate(wrapped):
                if bullet and line_index == 0:
                    writer.c.drawString(x, writer.pdf_y(y_draw), "•")
                writer.c.drawString(
                    x + (10.0 if bullet else 0.0),
                    writer.pdf_y(y_draw),
                    part,
                )
                y_draw += (
                    9.6
                    if kind in {"bullet", "plain"}
                    else (10.2 if kind == "company" else 9.5)
                )
            y_draw += 1.0

        writer.c.setFillColor(colors.black)
        writer.draw_frame(MARGIN_L, top, CONTENT_W, bottom)
        writer.y = bottom + 10.0
        continuation = True
        if index < len(rows):
            writer.new_page()


def _draw_signature(
    writer: AndroidStyleWriter,
    context: dict[str, Any],
    work_date: Any,
) -> None:
    writer.ensure(78.0)
    address = (
        _text(context.get("company_address_line1"))
        or _text(context.get("company_address"))
    )
    date = _date_de(work_date)
    writer.draw_text(f"{address}, {date}" if address else date, size=9.0)
    if _text(context.get("company_name")):
        writer.draw_text(_text(context.get("company_name")), size=9.0)
    writer.y += 18.0
    y = writer.pdf_y(writer.y)
    writer.c.setLineWidth(0.8)
    writer.c.line(MARGIN_L, y, MARGIN_L + 240.0, y)
    writer.y += 16.0
    writer.draw_text(
        "Unterschrift",
        size=8.0,
        color=colors.HexColor("#555555"),
    )


def _prepare_photo(source_path: Path, target_path: Path) -> Path:
    target_path.parent.mkdir(parents=True, exist_ok=True)
    with PILImage.open(source_path) as source:
        try:
            source.draft("RGB", (1600, 1200))
        except Exception:
            pass

        normalized = ImageOps.exif_transpose(source)
        prepared = None
        try:
            normalized.thumbnail(
                (1600, 1200),
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
                "JPEG",
                quality=82,
                optimize=True,
                progressive=True,
            )
        finally:
            if prepared is not None and prepared is not normalized:
                prepared.close()
            if normalized is not source:
                normalized.close()
    return target_path


def _draw_photo_appendix(
    writer: AndroidStyleWriter,
    snapshot: dict[str, Any],
    context: dict[str, Any],
    photo_files: list[dict[str, Any]],
) -> None:
    meta = _android_meta((snapshot.get("btb") or {}).get("header"))
    ordered, numbers = _photo_numbering(snapshot, meta)
    if not ordered:
        return

    file_by_id = {str(item.get("id")): item for item in photo_files}
    writer.new_page()

    with TemporaryDirectory(prefix=".tb-btb-pdf-android-style-") as temp_dir:
        temp_root = Path(temp_dir)
        for photo_type in PHOTO_ORDER + ("SONSTIGES",):
            group = []
            for photo in ordered:
                metadata = (
                    photo.get("photo_metadata")
                    if isinstance(photo.get("photo_metadata"), dict)
                    else {}
                )
                current_type = _text(metadata.get("typ")).upper() or "SONSTIGES"
                if current_type == photo_type:
                    group.append(photo)
            if not group:
                continue

            title = {
                "ABNAHMEN": "Abnahmen",
                "EINWEISUNG_UNTERWEISUNG": "Einweisung/Unterweisung",
                "BESONDERHEITEN": "Besonderheiten",
                "MAENGEL_SCHAEDEN": "Mängel/Schäden",
                "FOTODOKUMENTATION": "Fotodokumentation",
                "SONSTIGES": "Fotos",
            }[photo_type]

            writer.ensure(30.0)
            writer.draw_text(
                title,
                font="Helvetica-Bold",
                size=12.0,
                leading=14.0,
            )
            writer.y += 3.0

            columns = 2
            gap = 10.0
            cell_width = (CONTENT_W - gap) / columns
            cell_height = 238.0
            index = 0

            while index < len(group):
                if writer.y + cell_height > PAGE_H - MARGIN_BOTTOM:
                    writer.new_page()
                    writer.draw_text(
                        title + " (Fortsetzung)",
                        font="Helvetica-Bold",
                        size=12.0,
                        leading=14.0,
                    )
                    writer.y += 3.0

                row = group[index:index + columns]
                top = writer.y
                for column_index, photo in enumerate(row):
                    x = MARGIN_L + column_index * (cell_width + gap)
                    metadata = (
                        photo.get("photo_metadata")
                        if isinstance(photo.get("photo_metadata"), dict)
                        else {}
                    )
                    number = numbers.get(str(photo.get("id")), 0)

                    writer.c.setStrokeColor(colors.HexColor("#777777"))
                    writer.c.rect(
                        x,
                        writer.pdf_y(top + cell_height),
                        cell_width,
                        cell_height,
                        fill=0,
                        stroke=1,
                    )
                    writer.c.setStrokeColor(colors.black)
                    writer.c.setFont("Helvetica-Bold", 9.0)
                    writer.c.drawString(
                        x + 7.0,
                        writer.pdf_y(top + 16.0),
                        f"Bild {number}",
                    )

                    image_top = top + 24.0
                    image_height = 142.0
                    file_info = file_by_id.get(str(photo.get("id")))
                    source = file_info.get("path") if isinstance(file_info, dict) else None
                    if isinstance(source, Path) and source.is_file():
                        try:
                            prepared = _prepare_photo(
                                source,
                                temp_root / f"{number:04d}.jpg",
                            )
                            image = ImageReader(str(prepared))
                            image_width, image_height_px = image.getSize()
                            max_width = cell_width - 14.0
                            max_height = image_height - 6.0
                            scale = min(
                                max_width / image_width,
                                max_height / image_height_px,
                            )
                            draw_width = image_width * scale
                            draw_height = image_height_px * scale
                            image_x = x + (cell_width - draw_width) / 2.0
                            image_y_top = (
                                image_top
                                + (image_height - draw_height) / 2.0
                            )
                            writer.c.drawImage(
                                image,
                                image_x,
                                writer.pdf_y(image_y_top + draw_height),
                                width=draw_width,
                                height=draw_height,
                                preserveAspectRatio=True,
                                mask="auto",
                            )
                        except Exception:
                            writer.c.setFont("Helvetica", 8.0)
                            writer.c.drawString(
                                x + 8.0,
                                writer.pdf_y(image_top + 30.0),
                                "(Bild konnte nicht gerendert werden)",
                            )
                    else:
                        writer.c.setFont("Helvetica", 8.0)
                        writer.c.drawString(
                            x + 8.0,
                            writer.pdf_y(image_top + 30.0),
                            "(Bild nicht lokal verfügbar)",
                        )

                    meta_y = image_top + image_height + 6.0
                    short = (
                        _text(metadata.get("notiz"))
                        or _text(metadata.get("kurztext"))
                        or _text(photo.get("original_name"))
                    )
                    details = []
                    if short:
                        details.append(short)
                    company = _text(metadata.get("firma"))
                    if company:
                        details.append(f"Firma: {company}")
                    km = _km_value(metadata.get("km"))
                    if km:
                        details.append(f"KM: {km}")
                    captured = _time_hm(photo.get("captured_at"))
                    if captured != "-":
                        details.append(f"Zeit: {captured}")
                    lat = _number(
                        photo.get("gps_lat")
                        if photo.get("gps_lat") is not None
                        else metadata.get("lat")
                    )
                    lon = _number(
                        photo.get("gps_lon")
                        if photo.get("gps_lon") is not None
                        else metadata.get("lon")
                    )
                    if lat is not None and lon is not None:
                        details.append(f"GPS: {lat:.6f}, {lon:.6f}")

                    writer.c.setFont("Helvetica", 7.4)
                    for detail in details[:5]:
                        for line in _wrap_text(
                            detail,
                            "Helvetica",
                            7.4,
                            cell_width - 14.0,
                        ):
                            if meta_y > top + cell_height - 8.0:
                                break
                            writer.c.drawString(
                                x + 7.0,
                                writer.pdf_y(meta_y),
                                line,
                            )
                            meta_y += 8.7

                writer.y = top + cell_height + 10.0
                index += columns


def render_android_style_btb_pdf(
    output_path: Path,
    *,
    snapshot: dict[str, Any],
    original_snapshot: dict[str, Any] | None,
    context: dict[str, Any],
    photo_files: list[dict[str, Any]],
    renderer_version: str,
) -> None:
    """Render the Server BTB PDF in the established Android/Skia visual structure."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    btb = snapshot.get("btb") or {}
    meta = _android_meta(btb.get("header"))
    entries = [
        entry
        for entry in snapshot.get("entries") or []
        if isinstance(entry, dict)
    ]
    section_lines, removed, firms = _section_lines(
        snapshot,
        original_snapshot,
        context,
    )

    writer = AndroidStyleWriter(
        output_path,
        context=context,
        work_date=_text(btb.get("work_date")),
    )
    _draw_document_header(writer, snapshot, context)
    _draw_weather(writer, meta)
    _draw_people_machines(writer, entries, firms)

    removed_by_section: dict[str, list[SectionLine]] = {}
    if original_snapshot:
        current_photo_ids = {
            str(item.get("id"))
            for item in snapshot.get("photos") or []
        }
        for photo in original_snapshot.get("photos") or []:
            if str(photo.get("id")) in current_photo_ids:
                continue
            metadata = (
                photo.get("photo_metadata")
                if isinstance(photo.get("photo_metadata"), dict)
                else {}
            )
            section = PHOTO_SECTION.get(
                _text(metadata.get("typ")).upper(),
                "ARBEIT",
            )
            short = (
                _text(metadata.get("notiz"))
                or _text(metadata.get("kurztext"))
                or _text(photo.get("original_name"))
                or "Foto"
            )
            company = _text(metadata.get("firma")) or None
            node_key = _text(metadata.get("worksiteNodeKey")) or None
            removed_by_section.setdefault(section, []).append(
                SectionLine(
                    company,
                    node_key,
                    f"(entfernt) Foto: {short}",
                    True,
                )
            )

    for entry in removed:
        section = _text(entry.get("section")).upper()
        section = SECTION_ALIASES.get(section, section)
        if section not in {key for key, _, _ in SECTION_TITLES}:
            continue
        payload = _payload(entry)
        visible = _clean_marker_text(payload.get("text"))
        if not visible:
            visible = " · ".join(
                f"{key}: {value}"
                for key, value in payload.items()
                if key not in MANAGED_KEYS
                and key not in {
                    "firmaId",
                    "firmaName",
                    "worksiteNodeKey",
                    "clientEntryKey",
                }
                and value not in (None, "", [], {})
            )
        if visible:
            removed_by_section.setdefault(section, []).append(
                SectionLine(
                    None,
                    None,
                    f"(entfernt) {visible}",
                    True,
                )
            )

    for section, title, empty_text in SECTION_TITLES:
        lines = list(section_lines.get(section, []))
        lines.extend(removed_by_section.get(section, []))
        _draw_section(
            writer,
            title,
            lines,
            empty_text,
            context,
        )

    _draw_signature(writer, context, btb.get("work_date"))
    _draw_photo_appendix(
        writer,
        snapshot,
        context,
        photo_files,
    )

    writer.c.setSubject(f"PDF-Renderer: {renderer_version}")
    writer.finish()
