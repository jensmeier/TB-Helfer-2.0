from __future__ import annotations

import math
import re
import struct
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from psycopg.types.json import Jsonb

from .database import transaction
from .events import audit, emit_change
from .files import _absolute_storage_path, _hash_file
from .permissions import require_permission, worksite_context
from .portal import current_portal_user
from .survey_originals import _assert_original_access, _load_original


router = APIRouter(prefix="/api/v1", tags=["survey-parser"])
PARSER_VERSION = "tb-survey-parser-1"
KF_RECORD_SIZE = 102


class UnsupportedSurveyFormat(ValueError):
    pass


def _finite(value: float) -> bool:
    return math.isfinite(value) and abs(value) < 1e30


def _plausible_xy(x: float, y: float) -> bool:
    return 3_000_000.0 <= x <= 6_000_000.0 and 4_000_000.0 <= y <= 6_500_000.0


def _double_le(data: bytes, offset: int) -> float:
    return struct.unpack_from("<d", data, offset)[0]


def _float_de(value: str) -> float | None:
    try:
        result = float(value.replace(",", "."))
    except (TypeError, ValueError):
        return None
    return result if _finite(result) else None


def _decode_text(data: bytes) -> str:
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return data.decode("latin-1")


def _text_field(data: bytes, start: int, length: int) -> str:
    if start < 0 or start >= len(data) or length <= 0:
        return ""
    return data[start : min(start + length, len(data))].decode("cp1252", errors="replace").replace("\x00", "").strip()


def _route_from_name(name: str) -> str | None:
    match = re.search(r"(?<![A-Z0-9])([1-9]\d{3})(?!\d)", name.upper())
    if not match or match.group(1) == "3000":
        return None
    return match.group(1)


def _parse_tra(name: str, data: bytes, source_sha256: str) -> tuple[str, dict, list[str]]:
    header_bytes = 0x48
    record_bytes = 78
    if len(data) < header_bytes + record_bytes * 2:
        raise ValueError("TRA_DATEI_ZU_KLEIN")
    count = struct.unpack_from("<i", data, 0x30)[0]
    if count <= 0 or count >= 20_000:
        raise ValueError(f"TRA_ELEMENTANZAHL_UNPLAUSIBEL:{count}")
    if len(data) < header_bytes + (count + 1) * record_bytes:
        raise ValueError(f"TRA_DATEI_UNVOLLSTAENDIG:{count}")

    nodes: list[dict[str, Any]] = []
    warnings: list[str] = []
    for index in range(count + 1):
        offset = header_bytes + index * record_bytes
        radius_from = _double_le(data, offset + 6)
        radius_to = _double_le(data, offset + 14)
        x = _double_le(data, offset + 22)
        y = _double_le(data, offset + 30)
        direction_rad = _double_le(data, offset + 38)
        station = _double_le(data, offset + 46)
        raw_type = struct.unpack_from("<h", data, offset + 54)[0]
        length = _double_le(data, offset + 56)
        parameter = _double_le(data, offset + 64)
        if not _plausible_xy(x, y):
            raise ValueError(f"TRA_DBREF_UNPLAUSIBEL:{index}")
        if not _finite(station) or not _finite(direction_rad):
            raise ValueError(f"TRA_KNOTEN_UNGUELTIG:{index}")
        if index < count and length < 0:
            warnings.append(f"Negative Elementlaenge bei Element {index}.")
        nodes.append({
            "index": index,
            "radius_from_m": radius_from if _finite(radius_from) else None,
            "radius_to_m": radius_to if _finite(radius_to) else None,
            "x_dbref": x,
            "y_dbref": y,
            "direction_deg": math.degrees(direction_rad) % 360.0,
            "station_m": station,
            "raw_type": raw_type,
            "length_m": length if _finite(length) else None,
            "parameter": parameter if _finite(parameter) else None,
        })
    for index, (left, right) in enumerate(zip(nodes, nodes[1:])):
        if right["station_m"] < left["station_m"]:
            warnings.append(f"Station faellt bei Element {index}.")

    type_names = {0: "GERADE", 1: "BOGEN", 2: "UEBERGANG"}
    elements: list[dict[str, Any]] = []
    for index in range(count):
        start, end = nodes[index], nodes[index + 1]
        radius = next((r for r in (start["radius_to_m"], start["radius_from_m"]) if r is not None and abs(r) > 0.0001 and abs(r) < 1_000_000.0), None)
        elements.append({
            "element_index": index,
            "station_start_m": start["station_m"],
            "station_end_m": end["station_m"],
            "x_dbref": start["x_dbref"],
            "y_dbref": start["y_dbref"],
            "direction_deg": start["direction_deg"],
            "element_type": type_names.get(start["raw_type"], "UNBEKANNT"),
            "raw_type": start["raw_type"],
            "length_m": start["length_m"],
            "radius_m": radius,
            "parameter_from": start["parameter"],
            "parameter_to": end["parameter"],
        })
    return "TRA_ER0_BINARY", {
        "source_name": name,
        "source_signature": source_sha256[:24],
        "coordinate_system": "DB_REF2016_4",
        "element_count": count,
        "station_from_m": nodes[0]["station_m"],
        "station_to_m": nodes[-1]["station_m"],
        "nodes": nodes,
        "elements": elements,
    }, warnings


def _parse_gra(name: str, data: bytes, source_sha256: str) -> tuple[str, dict, list[str]]:
    header_bytes, record_bytes = 36, 36
    if len(data) < header_bytes + record_bytes:
        raise ValueError("GRA_DATEI_ZU_KLEIN")
    count_double = _double_le(data, 0)
    count = round(count_double)
    if count <= 0 or count >= 50_000 or abs(count_double - count) >= 0.0001:
        raise ValueError(f"GRA_PUNKTANZAHL_UNPLAUSIBEL:{count_double}")
    if len(data) < header_bytes + count * record_bytes:
        raise ValueError(f"GRA_DATEI_UNVOLLSTAENDIG:{count}")
    warnings: list[str] = []
    points: list[dict[str, Any]] = []
    for index in range(count):
        offset = header_bytes + index * record_bytes
        km_meter = _double_le(data, offset)
        height = _double_le(data, offset + 8)
        radius = _double_le(data, offset + 16)
        parameter = _double_le(data, offset + 24)
        code = struct.unpack_from("<i", data, offset + 32)[0]
        if not _finite(km_meter):
            warnings.append(f"Punkt {index + 1}: ungueltiger km-/Stationswert")
            continue
        if not -10_000.0 <= km_meter <= 500_000.0:
            warnings.append(f"Punkt {index + 1}: unplausibler km-/Stationswert")
        height_value = height if _finite(height) else None
        if height_value is not None and not -500.0 <= height_value <= 4_000.0:
            warnings.append(f"Punkt {index + 1}: unplausible Hoehe")
        points.append({
            "point_index": index,
            "km_meter": km_meter,
            "height_m": height_value,
            "radius_m": radius if _finite(radius) and abs(radius) > 0.0001 else None,
            "parameter_m": parameter if _finite(parameter) and abs(parameter) > 0.0001 else None,
            "point_code": code or None,
            "raw_offset": offset,
        })
    if not points:
        raise ValueError("GRA_KEINE_VERWERTBAREN_PUNKTE")
    return "GRA_BINARY", {
        "source_name": name,
        "source_signature": source_sha256[:24],
        "coordinate_system": "DB_REF2016_4",
        "route_from_name": _route_from_name(name),
        "points": points,
    }, warnings


def _is_raw_kf7(data: bytes) -> bool:
    return len(data) >= KF_RECORD_SIZE * 2 and len(data) % KF_RECORD_SIZE == 0 and "VERSION 7.0" in _text_field(data, 40, 34).upper()


def _parse_raw_kf(name: str, data: bytes) -> tuple[str, dict, list[str]]:
    points: list[dict[str, Any]] = []
    without_route = without_station = without_height = zone3 = 0
    for record_index in range(1, len(data) // KF_RECORD_SIZE):
        base = record_index * KF_RECORD_SIZE
        number, x, y = _double_le(data, base), _double_le(data, base + 8), _double_le(data, base + 16)
        if not (_finite(number) and _finite(x) and _finite(y) and _plausible_xy(x, y)):
            continue
        height_raw, station_raw = _double_le(data, base + 24), _double_le(data, base + 32)
        height = height_raw if _finite(height_raw) else None
        station = station_raw if _finite(station_raw) and abs(station_raw) > 0.001 else None
        location = _text_field(data, base + 40, 16) or None
        part = _text_field(data, base + 70, 4) or None
        route_raw = _text_field(data, base + 97, 4)
        route = route_raw if re.fullmatch(r"[1-9]\d{3}", route_raw) else None
        rikz = _text_field(data, base + 101, 1) or None
        without_route += int(route is None)
        without_station += int(station is None)
        without_height += int(height is None)
        coordinate_system = "DB_REF2016_4" if 4_000_000.0 <= x < 5_000_000.0 else "DB_REF_GK_3" if 3_000_000.0 <= x < 4_000_000.0 else "DB_REF_UNBEKANNT"
        zone3 += int(coordinate_system == "DB_REF_GK_3")
        number_text = f"{number:.5f}".rstrip("0").rstrip(".")
        points.append({
            "record_index": record_index,
            "route": route,
            "point_name": location or number_text or f"KF-{record_index}",
            "km_meter": station,
            "x_dbref": x,
            "y_dbref": y,
            "height_m": height,
            "type": part,
            "class": number_text or None,
            "coordinate_system": coordinate_system,
            "rikz": rikz,
        })
    if not points:
        raise ValueError("KF7_KEINE_PLAUSIBLEN_FESTPUNKTE")
    warnings = [f"Roh-KF 7.0 direkt importiert: {len(points)} Punkt(e)."]
    if without_route: warnings.append(f"{without_route} Roh-KF-Punkt(e) ohne plausible Strecke.")
    if without_station: warnings.append(f"{without_station} Roh-KF-Punkt(e) ohne plausible Station.")
    if without_height: warnings.append(f"{without_height} Roh-KF-Punkt(e) ohne plausible Hoehe.")
    if zone3: warnings.append(f"{zone3} Roh-KF-Punkt(e) im DB_REF/GK-Streifen 3.")
    return "KF_7_BINARY", {"source_name": name, "points": points}, warnings


def _parse_kf_text(name: str, text: str) -> tuple[str, dict, list[str]]:
    points: list[dict[str, Any]] = []
    for raw in text.splitlines():
        if len(raw) < 54:
            continue
        point_no = raw[0:14].strip()
        if not point_no or point_no.lower() == "pkt-nr." or set(point_no) == {"-"}:
            continue
        x, y = _float_de(raw[14:29].strip()), _float_de(raw[29:46].strip())
        if x is None or y is None or not _plausible_xy(x, y):
            continue
        route_raw = raw[90:98].strip()
        type_tokens = raw[104:120].strip().split()
        points.append({
            "route": route_raw if re.fullmatch(r"[1-9]\d{3}", route_raw) else None,
            "point_name": raw[72:90].strip() or point_no,
            "km_meter": _float_de(raw[59:72].strip()),
            "x_dbref": x,
            "y_dbref": y,
            "height_m": _float_de(raw[46:59].strip()),
            "type": next((token for token in type_tokens if any(ch.isalpha() for ch in token)), None),
            "class": point_no,
            "coordinate_system": "DB_REF2016_4",
        })
    if not points:
        raise ValueError("KF_TEXT_KEINE_PLAUSIBLEN_PUNKTE")
    without_route = sum(1 for point in points if point["route"] is None)
    warnings = [f"{without_route} KF.TXT-Punkt(e) ohne Streckenbezug."] if without_route else []
    return "KF_TEXT", {"source_name": name, "points": points}, warnings


def _parse_kv(name: str, text: str) -> tuple[str, dict, list[str]]:
    points: list[dict[str, Any]] = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("//"):
            continue
        tokens = [token for token in re.split(r"\s+", line) if token]
        if len(tokens) < 7:
            continue
        x, y = _float_de(tokens[1]), _float_de(tokens[2])
        if x is None or y is None or not _plausible_xy(x, y):
            continue
        route_index = next((i for i in range(4, len(tokens)) if re.fullmatch(r"[1-9]\d{3}", tokens[i]) and i > 0 and any(ch.isalpha() for ch in tokens[i - 1])), None)
        if route_index is None:
            continue
        type_index = route_index - 1
        station_index = next((i for i in range(3, type_index) if (_float_de(tokens[i]) or 0.0) > 1_000.0), None)
        if station_index is None:
            continue
        station = _float_de(tokens[station_index])
        if station is None:
            continue
        height = next((value for i in range(3, station_index) if (value := _float_de(tokens[i])) is not None and -500.0 <= value <= 4_000.0), None)
        point_name = tokens[station_index + 1] if station_index + 1 < type_index and tokens[station_index + 1] else tokens[0]
        points.append({
            "route": tokens[route_index], "point_name": point_name, "km_meter": station,
            "x_dbref": x, "y_dbref": y, "height_m": height, "type": tokens[type_index],
            "class": tokens[0] or None, "coordinate_system": "DB_REF2016_4",
        })
    if not points:
        raise ValueError("KV_FORMAT_NICHT_ERKANNT")
    return "KV_TEXT", {"source_name": name, "points": points}, []


def _parse_gvp_standard(name: str, text: str) -> tuple[str, dict, list[str]]:
    points: list[dict[str, Any]] = []
    known_types = {"PPB", "GPSC", "HBH", "PS0", "PS1", "PS2", "PS3", "PS4"}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("//"):
            continue
        tokens = [token for token in re.split(r"\s+", line) if token]
        if len(tokens) < 7:
            continue
        x, y = _float_de(tokens[1]), _float_de(tokens[2])
        point_name = tokens[5] if tokens[5] else None
        route = tokens[6] if re.fullmatch(r"[1-9]\d{3}", tokens[6]) else None
        if x is None or y is None or point_name is None or route is None or not _plausible_xy(x, y):
            continue
        point_type = next((token for token in tokens if token.upper().startswith("GVP") or token.upper() in known_types), None)
        points.append({
            "route": route, "point_name": point_name, "km_meter": _float_de(tokens[4]),
            "x_dbref": x, "y_dbref": y, "height_m": _float_de(tokens[3]), "type": point_type,
            "class": tokens[0] or None, "coordinate_system": "DB_REF2016_4",
        })
    if not points:
        raise ValueError("GVP_STANDARD_FORMAT_NICHT_ERKANNT")
    return "GVP_TEXT", {"source_name": name, "points": points}, []


def _parse_gvp_family(name: str, data: bytes) -> tuple[str, dict, list[str]]:
    lower = name.lower()
    if lower.endswith(".kf") and _is_raw_kf7(data):
        return _parse_raw_kf(name, data)
    text = _decode_text(data)
    if lower.endswith(".kv"):
        result = _parse_kv(name, text)
    elif lower.endswith(".kf.txt") or "koordinatenverzeichnis" in text.lower():
        result = _parse_kf_text(name, text)
    else:
        result = _parse_gvp_standard(name, text)
    detected, payload, warnings = result
    km_values = [point["km_meter"] for point in payload["points"] if point.get("km_meter") is not None]
    if km_values and all(abs(value) <= 0.001 for value in km_values):
        for point in payload["points"]:
            point["km_meter"] = None
        warnings.append("KM-/Stationsspalte ist komplett 0.00000 und wird nicht als KM-Quelle verwendet.")
    return detected, payload, warnings


def _parse_kor_text(name: str, data: bytes) -> tuple[str, dict, list[str]]:
    if not name.lower().endswith(".kor.txt"):
        raise UnsupportedSurveyFormat("KOR_RAW_NO_TEXT_PARSER")
    route = _route_from_name(name)
    points: list[dict[str, Any]] = []
    warnings: list[str] = []
    for index, raw in enumerate(_decode_text(data).splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("//"):
            continue
        tokens = [token for token in re.split(r"\s+", line) if token]
        if len(tokens) < 4:
            warnings.append(f"Zeile {index}: zu wenig Spalten")
            continue
        x, y, height = _float_de(tokens[1]), _float_de(tokens[2]), _float_de(tokens[3])
        if not tokens[0] or x is None or y is None:
            warnings.append(f"Zeile {index}: Pflichtwerte fehlen")
            continue
        if not _plausible_xy(x, y):
            warnings.append(f"Zeile {index}: unplausible DB_REF-Koordinate")
            continue
        code = tokens[4] if len(tokens) > 4 and tokens[4] else None
        points.append({
            "point_name": tokens[0], "route": route, "track": None, "km_meter": None,
            "x_dbref": x, "y_dbref": y, "height_m": height,
            "type": f"KOR-{code}" if code else "KOR", "coordinate_system": "DB_REF2016_4",
        })
    if not points:
        raise ValueError("KOR_TEXT_KEINE_ABSTECKPUNKTE")
    return "KOR_TEXT", {"source_name": name, "points": points}, warnings


def parse_survey_original(data_kind: str, name: str, data: bytes, source_sha256: str) -> tuple[str, str, dict, list[str]]:
    kind, lower = (data_kind or "").strip().upper(), name.lower()
    if kind == "TRA" or lower.endswith(".tra"):
        detected, payload, warnings = _parse_tra(name, data, source_sha256)
        return "TRA", detected, payload, warnings
    if kind == "GRA" or lower.endswith(".gra"):
        detected, payload, warnings = _parse_gra(name, data, source_sha256)
        return "GRA", detected, payload, warnings
    if kind in {"GVP", "KF", "KV", "KF.TXT", "FESTPUNKT"} or lower.endswith((".gvp", ".kv", ".kf", ".kf.txt")):
        detected, payload, warnings = _parse_gvp_family(name, data)
        return "GVP_FESTPUNKT", detected, payload, warnings
    if kind in {"KOR", "KOR.TXT"} or lower.endswith((".kor", ".kor.txt")):
        detected, payload, warnings = _parse_kor_text(name, data)
        return "KOR", detected, payload, warnings
    raise UnsupportedSurveyFormat(f"UNSUPPORTED_DATA_KIND:{kind or 'UNKNOWN'}")


def _load_catalog(cur, original_id: UUID) -> dict | None:
    cur.execute("SELECT * FROM tb.survey_parse_catalog WHERE original_id = %s", (original_id,))
    row = cur.fetchone()
    return dict(row) if row else None


@router.get("/survey-originals/{original_id}/parse-catalog")
def get_parse_catalog(original_id: UUID, user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        original = _load_original(cur, original_id)
        _assert_original_access(user, original)
        catalog = _load_catalog(cur, original_id)
    if not catalog:
        raise HTTPException(404, "SURVEY_PARSE_NOT_FOUND")
    return catalog


@router.post("/worksites/{worksite_id}/survey-originals/{original_id}/parse")
def parse_original(worksite_id: UUID, original_id: UUID, user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        original = _load_original(cur, original_id)
        _assert_original_access(user, original)
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "track.edit")
        if original["company_id"] is not None and original["company_id"] != context["company_id"]:
            raise HTTPException(403, "SURVEY_ORIGINAL_COMPANY_MISMATCH")

    path = _absolute_storage_path(original["storage_key"])
    if not path.is_file():
        raise HTTPException(404, "SURVEY_ORIGINAL_CONTENT_MISSING")
    actual_size, actual_sha256 = path.stat().st_size, _hash_file(path)
    if actual_size != original["byte_size"] or actual_sha256 != original["sha256"]:
        raise HTTPException(409, "SURVEY_ORIGINAL_INTEGRITY_MISMATCH")
    data = path.read_bytes()

    parser_family, detected_format, payload, warnings = "UNSUPPORTED", None, {}, []
    error_code = error_detail = None
    try:
        parser_family, detected_format, payload, warnings = parse_survey_original(original["data_kind"], original["original_name"], data, actual_sha256)
        status = "PARSED"
    except UnsupportedSurveyFormat as exc:
        status, error_detail = "UNSUPPORTED", str(exc)
        error_code = error_detail.split(":", 1)[0]
    except (ValueError, struct.error, UnicodeError) as exc:
        status, error_detail = "ERROR", str(exc)[:2000]
        error_code = (str(exc).split(":", 1)[0] or "PARSE_ERROR")[:120]

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO tb.survey_parse_catalog(
                original_id, parser_family, parser_version, source_sha256, status,
                detected_format, payload, warnings, error_code, error_detail, parsed_by, parsed_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now())
            ON CONFLICT (original_id) DO UPDATE SET
                parser_family=EXCLUDED.parser_family, parser_version=EXCLUDED.parser_version,
                source_sha256=EXCLUDED.source_sha256, status=EXCLUDED.status,
                detected_format=EXCLUDED.detected_format, payload=EXCLUDED.payload,
                warnings=EXCLUDED.warnings, error_code=EXCLUDED.error_code,
                error_detail=EXCLUDED.error_detail, parsed_by=EXCLUDED.parsed_by, parsed_at=now()
            RETURNING *
            """,
            (original_id, parser_family, PARSER_VERSION, actual_sha256, status, detected_format,
             Jsonb(payload), Jsonb(warnings), error_code, error_detail, user["user_id"]),
        )
        row = dict(cur.fetchone())
        emit_change(cur, company_id=context["company_id"], worksite_id=worksite_id,
                    entity_type="SURVEY_PARSE_CATALOG", entity_id=original_id, operation="UPSERT",
                    entity_version=None, actor_user_id=user["user_id"])
        audit(cur, action="SURVEY_ORIGINAL_PARSE", actor_user_id=user["user_id"],
              company_id=context["company_id"], worksite_id=worksite_id,
              entity_type="SURVEY_ORIGINAL", entity_id=original_id,
              details={"parser_family": parser_family, "parser_version": PARSER_VERSION,
                       "status": status, "detected_format": detected_format, "source_sha256": actual_sha256})
    return row
