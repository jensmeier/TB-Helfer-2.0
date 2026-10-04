from __future__ import annotations

from datetime import date, datetime
import math
import re
import statistics
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from psycopg.types.json import Jsonb
from psycopg.errors import UniqueViolation
from pydantic import BaseModel, Field

from .admin import require_admin, require_admin_csrf
from .database import transaction
from .events import audit
from .jobs import enqueue_in_tx
from .portal import current_portal_user, require_csrf
from .survey_tra_chainage import densify_tra_chainage_payload


router = APIRouter(prefix="/api/v1/survey-catalog", tags=["survey-catalog"])

_SCOPE_GLOBAL = "GLOBAL"
_SCOPE_COMPANY = "COMPANY"
_ALLOWED_SCOPES = {_SCOPE_GLOBAL, _SCOPE_COMPANY}
_MANUAL_KINDS = {"MANUAL_TRA_SECTION", "MANUAL_GPS"}
_AXIS_TYPES = {"STRECKE", "GLEIS", "WEICHE", "WEICHENVERBINDUNG", "UNBEKANNT"}
_CONFLICT_RESOLUTIONS = {"KEEP_EXISTING", "ACCEPT_CANDIDATE", "KEEP_BOTH", "CONNECT"}
OVERLAP_DISTANCE_M = 1.0
OVERLAP_NEAR_DISTANCE_M = 20.0
ENDPOINT_GAP_M = 5.0
ENDPOINT_HEADING_DEG = 20.0
SPATIAL_TRACK_CLUSTER_M = 1000.0
GVP_MAX_AXIS_DISTANCE_M = 120.0
GVP_OFFSET_OUTLIER_M = 80.0
GVP_MIN_ROBUST_MATCHES = 4
_EXPLICIT_KM_GRA_RE = re.compile(r"(?:^|[_-])KM\d{4}(?:[_\.-]|$)", re.IGNORECASE)
_GVP_DATA_KINDS = {"GVP", "KF", "KV", "KF.TXT", "FESTPUNKT"}


class ManualPoint(BaseModel):
    x_dbref: float
    y_dbref: float
    station_m: float | None = None
    km_meter: float | None = None
    direction_deg: float | None = None


class ManualSegmentCreate(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=160)
    id: UUID | None = None
    scope: str = Field(default=_SCOPE_GLOBAL, max_length=20)
    source_kind: str = Field(default="MANUAL_TRA_SECTION", max_length=40)
    route_number: str | None = Field(default=None, max_length=80)
    station_code: str | None = Field(default=None, max_length=50)
    station_name: str | None = Field(default=None, max_length=240)
    track_key: str | None = Field(default=None, max_length=160)
    object_key: str | None = Field(default=None, max_length=160)
    direction: str | None = Field(default=None, max_length=40)
    axis_type: str = Field(default="GLEIS", max_length=40)
    display_name: str = Field(min_length=1, max_length=300)
    source_original_id: UUID | None = None
    source_signature: str | None = Field(default=None, max_length=128)
    station_from_m: float | None = None
    station_to_m: float | None = None
    km_from_m: float | None = None
    km_to_m: float | None = None
    geometry: list[ManualPoint] = Field(default_factory=list, max_length=20000)
    metadata: dict = Field(default_factory=dict)


class ManualSegmentUpdate(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=160)
    base_version: int = Field(ge=1)
    route_number: str | None = Field(default=None, max_length=80)
    station_code: str | None = Field(default=None, max_length=50)
    station_name: str | None = Field(default=None, max_length=240)
    track_key: str | None = Field(default=None, max_length=160)
    object_key: str | None = Field(default=None, max_length=160)
    direction: str | None = Field(default=None, max_length=40)
    axis_type: str | None = Field(default=None, max_length=40)
    display_name: str | None = Field(default=None, min_length=1, max_length=300)
    station_from_m: float | None = None
    station_to_m: float | None = None
    km_from_m: float | None = None
    km_to_m: float | None = None
    geometry: list[ManualPoint] | None = Field(default=None, max_length=20000)
    metadata: dict | None = None


class ManualSegmentDeactivate(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=160)
    base_version: int = Field(ge=1)


class ConflictResolution(BaseModel):
    resolution: str = Field(min_length=1, max_length=40)
    note: str | None = Field(default=None, max_length=1000)


class AxisAdminUpdate(BaseModel):
    base_version: int = Field(ge=1)
    route_number: str | None = Field(default=None, max_length=80)
    station_code: str | None = Field(default=None, max_length=50)
    station_name: str | None = Field(default=None, max_length=240)
    track_key: str | None = Field(default=None, max_length=160)
    object_key: str | None = Field(default=None, max_length=160)
    direction: str | None = Field(default=None, max_length=40)
    axis_type: str | None = Field(default=None, max_length=40)
    display_name: str | None = Field(default=None, min_length=1, max_length=300)


def _clean(value: Any) -> str | None:
    if value is None:
        return None
    result = str(value).strip()
    return result or None


def _num(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def _plain(value: Any) -> Any:
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, dict):
        return {str(k): _plain(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_plain(v) for v in value]
    return value


_PRIVATE_CATALOG_RESPONSE_KEYS = {
    "created_by_user_id",
    "created_by_company_id",
    "changed_by_user_id",
    "changed_by_company_id",
    "resolved_by_user_id",
    "actor_user_id",
    "actor_device_id",
    "imported_by",
    "resolved_by",
}


def _sanitize_catalog_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            str(key): _sanitize_catalog_value(item)
            for key, item in value.items()
            if str(key) not in _PRIVATE_CATALOG_RESPONSE_KEYS
        }
    if isinstance(value, list):
        return [_sanitize_catalog_value(item) for item in value]
    return value


def _catalog_response(user: dict[str, Any], value: Any) -> Any:
    plain = _plain(value)
    if user.get("company_role") == "ADMIN":
        return plain
    return _sanitize_catalog_value(plain)


def _source_date_from_name(name: str) -> date | None:
    text = str(name or "")
    patterns = (
        r"(?<!\d)((?:19|20)\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?!\d)",
        r"(?<!\d)(\d{2})[-_.](\d{2})[-_.]((?:19|20)\d{2})(?!\d)",
    )
    for index, pattern in enumerate(patterns):
        match = re.search(pattern, text)
        if not match:
            continue
        try:
            if index == 0:
                return date(int(match.group(1)), int(match.group(2)), int(match.group(3)))
            return date(int(match.group(3)), int(match.group(2)), int(match.group(1)))
        except ValueError:
            continue
    return None


def _source_date(original: dict[str, Any]) -> date | None:
    metadata = original.get("metadata") if isinstance(original.get("metadata"), dict) else {}
    for key in ("source_date", "survey_date", "bestandsdatum", "datum"):
        value = metadata.get(key)
        if isinstance(value, date):
            return value
        if isinstance(value, str):
            try:
                return date.fromisoformat(value[:10])
            except ValueError:
                pass
    return _source_date_from_name(str(original.get("original_name") or ""))


def _station_meta(original: dict[str, Any]) -> tuple[str | None, str | None]:
    metadata = original.get("metadata") if isinstance(original.get("metadata"), dict) else {}
    code = _clean(
        metadata.get("station_code")
        or metadata.get("betriebsstelle_code")
        or metadata.get("rl100")
    )
    name = _clean(
        metadata.get("station_name")
        or metadata.get("betriebsstelle")
        or metadata.get("bahnhof")
    )
    return (code.upper() if code else None, name)


def _scope_for_original(original: dict[str, Any]) -> tuple[str, UUID | None]:
    company_id = original.get("company_id")
    metadata = original.get("metadata") if isinstance(original.get("metadata"), dict) else {}
    requested = str(metadata.get("catalog_scope") or "").strip().upper()
    if requested == _SCOPE_GLOBAL:
        # The raw original can remain company-private while its derived/reference
        # geometry is deliberately published into the shared catalog.
        return _SCOPE_GLOBAL, None
    if requested == _SCOPE_COMPANY and company_id is not None:
        return _SCOPE_COMPANY, company_id
    return (_SCOPE_COMPANY, company_id) if company_id is not None else (_SCOPE_GLOBAL, None)


def _normalize_scope(scope: str, user: dict[str, Any]) -> tuple[str, UUID | None]:
    normalized = str(scope or _SCOPE_GLOBAL).strip().upper()
    if normalized not in _ALLOWED_SCOPES:
        raise HTTPException(400, "SURVEY_CATALOG_SCOPE_INVALID")
    if normalized == _SCOPE_GLOBAL:
        return normalized, None
    company_id = user.get("company_id")
    if company_id is None:
        raise HTTPException(400, "SURVEY_CATALOG_COMPANY_SCOPE_REQUIRES_COMPANY")
    return normalized, company_id


def _assert_segment_write_access(user: dict[str, Any], segment: dict[str, Any]) -> None:
    if user.get("company_role") == "ADMIN":
        return
    scope = str(segment.get("scope") or "").upper()
    if scope == _SCOPE_GLOBAL:
        return
    if scope == _SCOPE_COMPANY and segment.get("company_id") == user.get("company_id"):
        return
    raise HTTPException(403, "SURVEY_CATALOG_COMPANY_SCOPE_FORBIDDEN")


def _visible_sql(user: dict[str, Any], alias: str = "") -> tuple[str, list[Any]]:
    prefix = f"{alias}." if alias else ""
    if user.get("company_role") == "ADMIN":
        return "TRUE", []
    company_id = user.get("company_id")
    if company_id is None:
        return f"{prefix}scope = 'GLOBAL'", []
    return (
        f"({prefix}scope = 'GLOBAL' OR ({prefix}scope = 'COMPANY' AND {prefix}company_id = %s))",
        [company_id],
    )


def _bbox(points: list[dict[str, Any]]) -> tuple[float, float, float, float] | None:
    xy: list[tuple[float, float]] = []
    for point in points:
        x, y = _num(point.get("x_dbref")), _num(point.get("y_dbref"))
        if x is not None and y is not None:
            xy.append((x, y))
    if not xy:
        return None
    return (
        min(p[0] for p in xy),
        min(p[1] for p in xy),
        max(p[0] for p in xy),
        max(p[1] for p in xy),
    )


def _bbox_distance(left: tuple[float, float, float, float] | None, right: tuple[float, float, float, float] | None) -> float | None:
    if left is None or right is None:
        return None
    dx = max(left[0] - right[2], right[0] - left[2], 0.0)
    dy = max(left[1] - right[3], right[1] - left[3], 0.0)
    return math.hypot(dx, dy)


def _payload_points(payload: dict[str, Any]) -> list[dict[str, Any]]:
    nodes = payload.get("nodes")
    if isinstance(nodes, list):
        return [row for row in nodes if isinstance(row, dict)]
    points = payload.get("points")
    if isinstance(points, list):
        return [row for row in points if isinstance(row, dict)]
    return []


def _km_extent(payload: dict[str, Any]) -> tuple[float | None, float | None]:
    values: list[float] = []
    for row in _payload_points(payload):
        value = _num(row.get("km_meter"))
        if value is not None:
            values.append(value)
    if not values:
        return None, None
    return min(values), max(values)


def _station_extent(payload: dict[str, Any]) -> tuple[float | None, float | None]:
    a = _num(payload.get("station_from_m"))
    b = _num(payload.get("station_to_m"))
    if a is not None or b is not None:
        return a, b
    values = [_num(row.get("station_m")) for row in _payload_points(payload)]
    values = [value for value in values if value is not None]
    return (min(values), max(values)) if values else (None, None)


def _axis_fields(original: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    station_code, station_name = _station_meta(original)
    metadata = original.get("metadata") if isinstance(original.get("metadata"), dict) else {}
    route = _clean(original.get("route_number")) or _clean(payload.get("route_from_name"))
    track = _clean(original.get("track_key")) or _clean(payload.get("track_from_name"))
    object_key = _clean(metadata.get("object_key")) or _clean(payload.get("object_key_from_name"))
    object_key = object_key.upper() if object_key else None
    direction = _clean(payload.get("direction_from_name"))
    axis_type = (_clean(payload.get("axis_type_from_name")) or "UNBEKANNT").upper()
    if axis_type not in _AXIS_TYPES:
        axis_type = "UNBEKANNT"
    if axis_type == "WEICHENVERBINDUNG" and object_key and object_key.startswith("WVB:"):
        display = "WVB " + object_key.split(":", 1)[1]
        if station_name:
            display += f" · {station_name}"
    elif axis_type == "WEICHE" and object_key and object_key.startswith("W:"):
        display = "W " + object_key.split(":", 1)[1]
        if station_name:
            display += f" · {station_name}"
    elif axis_type == "GLEIS" and track:
        display = f"Gl {track}" + (f" · {station_name}" if station_name else f" · Str {route}" if route else "")
    elif route and direction:
        display = f"Str {route}-{direction}"
    elif route:
        display = f"Str {route}"
    else:
        display = _clean(original.get("original_name")) or "Trassenachse"
    return {
        "route_number": route,
        "station_code": station_code,
        "station_name": station_name,
        "track_key": track,
        "object_key": object_key,
        "direction": direction,
        "axis_type": axis_type,
        "display_name": display,
    }


def _identity_key(scope: str, company_id: UUID | None, fields: dict[str, Any], fallback: str) -> str:
    prefix = "G" if scope == _SCOPE_GLOBAL else f"C:{company_id}"
    station = _clean(fields.get("station_code"))
    route = _clean(fields.get("route_number"))
    track = _clean(fields.get("track_key"))
    object_key = _clean(fields.get("object_key"))
    direction = _clean(fields.get("direction"))
    axis_type = str(fields.get("axis_type") or "UNBEKANNT").upper()
    if station and object_key:
        return f"{prefix}:S:{station.upper()}:O:{object_key.upper()}"
    if route and object_key:
        return f"{prefix}:R:{route}:O:{object_key.upper()}"
    if station and track:
        return f"{prefix}:S:{station.upper()}:T:{track.upper()}"
    if axis_type == "STRECKE" and route and direction:
        return f"{prefix}:R:{route}:D:{direction}"
    if axis_type == "STRECKE" and route:
        return f"{prefix}:R:{route}:A:STRECKE"
    return f"{prefix}:P:{fallback}"


def _history(
    cur,
    *,
    entity_type: str,
    entity_id: UUID,
    version: int,
    operation: str,
    before: dict[str, Any] | None,
    after: dict[str, Any],
    actor_user_id: UUID | None,
    actor_company_id: UUID | None,
) -> int:
    cur.execute(
        """
        INSERT INTO tb.survey_catalog_history(
            entity_type, entity_id, entity_version, operation,
            before_data, after_data, changed_by_user_id, changed_by_company_id
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
        RETURNING change_id
        """,
        (
            entity_type,
            entity_id,
            version,
            operation,
            Jsonb(_plain(before)) if before is not None else None,
            Jsonb(_plain(after)),
            actor_user_id,
            actor_company_id,
        ),
    )
    return int(cur.fetchone()["change_id"])


def _row(cur, table: str, entity_id: UUID) -> dict[str, Any]:
    lock = " FOR UPDATE" if table == "survey_catalog_axes" else ""
    cur.execute(f"SELECT * FROM tb.{table} WHERE id = %s{lock}", (entity_id,))
    found = cur.fetchone()
    if not found:
        raise HTTPException(404, "SURVEY_CATALOG_ENTITY_NOT_FOUND")
    return dict(found)


def _asset_upsert(cur, original: dict[str, Any], parsed: dict[str, Any], actor_user_id: UUID | None) -> dict[str, Any]:
    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    scope, company_id = _scope_for_original(original)
    station_code, station_name = _station_meta(original)
    route = _clean(original.get("route_number")) or _clean(payload.get("route_from_name"))
    track = _clean(original.get("track_key")) or _clean(payload.get("track_from_name"))
    points = _payload_points(payload)
    bbox = _bbox(points)
    km_from, km_to = _km_extent(payload)
    parse_status = str(parsed.get("status") or "ERROR").upper()
    data_status = "ACTIVE" if parse_status == "PARSED" else "ERROR"
    metadata = {
        "detected_format": parsed.get("detected_format"),
        "parser_family": parsed.get("parser_family"),
        "parser_version": parsed.get("parser_version"),
        "original_name": original.get("original_name"),
        "sha256": original.get("sha256"),
        "route_from_name": payload.get("route_from_name"),
        "track_from_name": payload.get("track_from_name"),
        "direction_from_name": payload.get("direction_from_name"),
        "source_direction_from_name": payload.get("source_direction_from_name"),
        "axis_type_from_name": payload.get("axis_type_from_name"),
    }
    cur.execute("SELECT * FROM tb.survey_catalog_assets WHERE original_id = %s FOR UPDATE", (original["id"],))
    existing = cur.fetchone()
    values = (
        scope, company_id, route, station_code, station_name, track,
        str(original.get("data_kind") or "UNKNOWN").upper(), _source_date(original),
        parse_status, data_status,
        bbox[0] if bbox else None, bbox[1] if bbox else None,
        bbox[2] if bbox else None, bbox[3] if bbox else None,
        km_from, km_to, Jsonb(metadata),
    )
    if existing:
        before = dict(existing)
        version = int(existing["current_version"]) + 1
        cur.execute(
            """
            UPDATE tb.survey_catalog_assets
            SET scope=%s,company_id=%s,route_number=%s,station_code=%s,station_name=%s,
                track_key=%s,data_kind=%s,source_date=%s,parse_status=%s,data_status=%s,
                bbox_min_x=%s,bbox_min_y=%s,bbox_max_x=%s,bbox_max_y=%s,
                km_from_m=%s,km_to_m=%s,metadata=%s,current_version=%s
            WHERE id=%s RETURNING *
            """,
            (*values, version, existing["id"]),
        )
        asset = dict(cur.fetchone())
        _history(cur, entity_type="ASSET", entity_id=asset["id"], version=version,
                 operation="UPDATE", before=before, after=asset,
                 actor_user_id=actor_user_id, actor_company_id=company_id)
        return asset
    cur.execute(
        """
        INSERT INTO tb.survey_catalog_assets(
            original_id,scope,company_id,route_number,station_code,station_name,track_key,
            data_kind,source_date,parse_status,data_status,bbox_min_x,bbox_min_y,bbox_max_x,
            bbox_max_y,km_from_m,km_to_m,metadata,created_by_user_id
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        RETURNING *
        """,
        (original["id"], *values, actor_user_id),
    )
    asset = dict(cur.fetchone())
    _history(cur, entity_type="ASSET", entity_id=asset["id"], version=1,
             operation="CREATE", before=None, after=asset,
             actor_user_id=actor_user_id, actor_company_id=company_id)
    return asset


def _clip_station_points(points: list[dict[str, Any]], start: float, end: float) -> list[dict[str, float]]:
    """Clip the densified source; never substitute the whole axis for an empty cut."""
    if not math.isfinite(start) or not math.isfinite(end) or start == end:
        return []
    low, high = min(start,end), max(start,end)
    rows = []
    for point in points:
        values = {k:_num(point.get(k)) for k in ('station_m','x_dbref','y_dbref')}
        if any(v is None for v in values.values()):
            return []
        if rows and values['station_m'] < rows[-1]['station_m']:
            return []
        if rows and values['station_m'] == rows[-1]['station_m']:
            continue
        rows.append({**point, **values})
    if len(rows)<2 or low<rows[0]['station_m'] or high>rows[-1]['station_m']:
        return []
    def boundary(station):
        for a,b in zip(rows,rows[1:]):
            if a['station_m'] <= station <= b['station_m']:
                if station == a['station_m']:
                    return dict(a)
                if station == b['station_m']:
                    return dict(b)
                t=(station-a['station_m'])/(b['station_m']-a['station_m'])
                result={'station_m':station}
                for k in ('x_dbref','y_dbref','km_meter','direction_deg'):
                    av,bv=_num(a.get(k)),_num(b.get(k))
                    if av is not None and bv is not None:
                        delta=(bv-av+180)%360-180 if k=='direction_deg' else bv-av
                        result[k]=(av+t*delta)%360 if k=='direction_deg' else av+t*delta
                return result
        return None
    return [boundary(low), *[p for p in rows if low<p['station_m']<high], boundary(high)]


def _dense_payload(payload: dict[str, Any], station_from: float | None = None, station_to: float | None = None) -> list[dict[str, float]]:
    dense = densify_tra_chainage_payload(payload)
    if station_from is not None and station_to is not None:
        return _clip_station_points(dense,station_from,station_to)
    if station_from is None and station_to is None:
        return dense
    values = [value for value in (station_from, station_to) if value is not None]
    if not values:
        return dense
    low, high = min(values), max(values)
    result = [p for p in dense if low - 0.001 <= float(p["station_m"]) <= high + 0.001]
    return result or dense


def _manual_geometry_points(geometry: list[ManualPoint] | list[dict[str, Any]]) -> list[dict[str, Any]]:
    result = []
    for raw in geometry:
        item = raw.model_dump() if isinstance(raw, ManualPoint) else dict(raw)
        x, y = _num(item.get("x_dbref")), _num(item.get("y_dbref"))
        if x is None or y is None:
            raise HTTPException(400, "SURVEY_MANUAL_GEOMETRY_INVALID")
        result.append({
            "x_dbref": x, "y_dbref": y,
            "station_m": _num(item.get("station_m")),
            "km_meter": _num(item.get("km_meter")),
            "direction_deg": _num(item.get("direction_deg")),
        })
    return result


def _project_xy(x: float, y: float, samples: list[dict[str, Any]]) -> float | None:
    best: float | None = None
    for first, second in zip(samples, samples[1:]):
        ax, ay = float(first["x_dbref"]), float(first["y_dbref"])
        bx, by = float(second["x_dbref"]), float(second["y_dbref"])
        vx, vy = bx - ax, by - ay
        length2 = vx * vx + vy * vy
        if length2 <= 1e-9:
            continue
        t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / length2))
        px, py = ax + t * vx, ay + t * vy
        distance = math.hypot(x - px, y - py)
        best = distance if best is None else min(best, distance)
    return best


def _vector_angle(ax: float, ay: float, bx: float, by: float) -> float:
    al, bl = math.hypot(ax, ay), math.hypot(bx, by)
    if al <= 1e-9 or bl <= 1e-9:
        return 180.0
    dot = max(-1.0, min(1.0, (ax * bx + ay * by) / (al * bl)))
    return math.degrees(math.acos(dot))


def _endpoint_candidates(samples: list[dict[str, Any]]) -> list[tuple[str, float, float, float, float]]:
    if len(samples) < 2:
        return []
    first, second = samples[0], samples[1]
    before_last, last = samples[-2], samples[-1]
    return [
        ("START", float(first["x_dbref"]), float(first["y_dbref"]),
         float(first["x_dbref"]) - float(second["x_dbref"]),
         float(first["y_dbref"]) - float(second["y_dbref"])),
        ("END", float(last["x_dbref"]), float(last["y_dbref"]),
         float(last["x_dbref"]) - float(before_last["x_dbref"]),
         float(last["y_dbref"]) - float(before_last["y_dbref"])),
    ]


def _relation(existing: list[dict[str, Any]], candidate: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    if len(existing) < 2 or len(candidate) < 2:
        return "REVIEW_REQUIRED", {"reason": "GEOMETRY_TOO_SHORT"}

    step = max(1, len(candidate) // 250)
    matches: list[tuple[float, float]] = []
    near_distances: list[float] = []
    for index, point in enumerate(candidate[::step]):
        distance = _project_xy(float(point["x_dbref"]), float(point["y_dbref"]), existing)
        if distance is None:
            continue
        near_distances.append(distance)
        if distance <= OVERLAP_DISTANCE_M:
            station = _num(point.get("station_m"))
            matches.append((station if station is not None else float(index * step), distance))
    if matches:
        stations = [row[0] for row in matches]
        span = max(stations) - min(stations) if len(stations) > 1 else 0.0
        existing_length = abs((_num(existing[-1].get("station_m")) or len(existing)) - (_num(existing[0].get("station_m")) or 0.0))
        candidate_length = abs((_num(candidate[-1].get("station_m")) or len(candidate)) - (_num(candidate[0].get("station_m")) or 0.0))
        required_span = min(30.0, max(4.0, 0.5 * min(existing_length, candidate_length)))
        if len(matches) >= 3 and span >= required_span:
            distances = sorted(row[1] for row in matches)
            return "OVERLAP_CONFIRMED", {
                "match_count": len(matches),
                "overlap_span_m": span,
                "median_distance_m": distances[len(distances) // 2],
                "max_distance_m": max(distances),
            }

    best_endpoint: dict[str, Any] | None = None
    for left_name, lx, ly, lvx, lvy in _endpoint_candidates(existing):
        for right_name, rx, ry, rvx, rvy in _endpoint_candidates(candidate):
            gap = math.hypot(lx - rx, ly - ry)
            if gap > ENDPOINT_GAP_M:
                continue
            heading = abs(180.0 - _vector_angle(lvx, lvy, rvx, rvy))
            item = {
                "existing_endpoint": left_name, "candidate_endpoint": right_name,
                "gap_m": gap, "heading_error_deg": heading,
            }
            if best_endpoint is None or (gap, heading) < (
                best_endpoint["gap_m"], best_endpoint["heading_error_deg"]
            ):
                best_endpoint = item
    if best_endpoint is not None:
        if best_endpoint["heading_error_deg"] <= ENDPOINT_HEADING_DEG:
            return "ENDPOINT_CONFIRMED", best_endpoint
        return "REVIEW_REQUIRED", {**best_endpoint, "reason": "ENDPOINT_HEADING_MISMATCH"}

    bbox_gap = _bbox_distance(_bbox(existing), _bbox(candidate))
    minimum = min(near_distances) if near_distances else None
    if (bbox_gap is not None and bbox_gap <= OVERLAP_NEAR_DISTANCE_M) or (
        minimum is not None and minimum <= OVERLAP_NEAR_DISTANCE_M
    ):
        return "REVIEW_REQUIRED", {
            "reason": "NEARBY_GEOMETRY_NOT_MATCHING",
            "bbox_gap_m": bbox_gap,
            "nearest_distance_m": minimum,
        }
    return "INDEPENDENT", {"bbox_gap_m": bbox_gap}



def _project_xy_station(
    x: float,
    y: float,
    samples: list[dict[str, Any]],
) -> dict[str, float] | None:
    best: dict[str, float] | None = None
    for first, second in zip(samples, samples[1:]):
        ax, ay = _num(first.get("x_dbref")), _num(first.get("y_dbref"))
        bx, by = _num(second.get("x_dbref")), _num(second.get("y_dbref"))
        sa, sb = _num(first.get("station_m")), _num(second.get("station_m"))
        if None in (ax, ay, bx, by, sa, sb):
            continue
        assert ax is not None and ay is not None and bx is not None and by is not None
        assert sa is not None and sb is not None
        vx, vy = bx - ax, by - ay
        length2 = vx * vx + vy * vy
        if length2 <= 1e-9:
            continue
        t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / length2))
        px, py = ax + t * vx, ay + t * vy
        distance = math.hypot(x - px, y - py)
        if best is None or distance < best["distance_m"]:
            best = {"distance_m": distance, "station_m": sa + (sb - sa) * t}
    return best


class _GvpProjectionIndex:
    """Exact projection within the GVP radius, indexed once per catalog segment.

    The grid only prunes impossible candidates; it does not simplify geometry.
    Segment order is retained so equal-distance hits use the same first segment
    as _project_xy_station. Long segment boxes use a bounded-memory fallback.
    This index is request-local: edited geometry cannot leave a stale cache.
    """

    def __init__(self, samples: list[dict[str, Any]]) -> None:
        self._cell_size = GVP_MAX_AXIS_DISTANCE_M
        self._segments: list[tuple[float, ...]] = []
        self._cells: dict[tuple[int, int], list[int]] = {}
        self._wide_segments: list[int] = []
        vertices = [
            (_num(p.get("x_dbref")), _num(p.get("y_dbref")), _num(p.get("station_m")))
            for p in samples
        ]
        for first, second in zip(vertices, vertices[1:]):
            ax, ay, sa = first
            bx, by, sb = second
            if None in (ax, ay, bx, by, sa, sb):
                continue
            assert ax is not None and ay is not None and sa is not None
            assert bx is not None and by is not None and sb is not None
            vx, vy = bx - ax, by - ay
            length2 = vx * vx + vy * vy
            if length2 <= 1e-9:
                continue
            segment_id = len(self._segments)
            self._segments.append((ax, ay, vx, vy, length2, sa, sb))
            ix0 = math.floor(min(ax, bx) / self._cell_size)
            ix1 = math.floor(max(ax, bx) / self._cell_size)
            iy0 = math.floor(min(ay, by) / self._cell_size)
            iy1 = math.floor(max(ay, by) / self._cell_size)
            # A very long or diagonal edge must not allocate a huge grid.
            if (ix1 - ix0 + 1) * (iy1 - iy0 + 1) > 256:
                self._wide_segments.append(segment_id)
                continue
            for ix in range(ix0, ix1 + 1):
                for iy in range(iy0, iy1 + 1):
                    self._cells.setdefault((ix, iy), []).append(segment_id)

    def project(self, x: float, y: float) -> dict[str, float] | None:
        # Tiny padding is only for cell lookup at floating-point boundaries.
        # The actual 120 m acceptance test below remains exact and unchanged.
        radius = GVP_MAX_AXIS_DISTANCE_M
        padding = 1e-7
        ix0 = math.floor((x - radius - padding) / self._cell_size)
        ix1 = math.floor((x + radius + padding) / self._cell_size)
        iy0 = math.floor((y - radius - padding) / self._cell_size)
        iy1 = math.floor((y + radius + padding) / self._cell_size)
        candidates = set(self._wide_segments)
        for ix in range(ix0, ix1 + 1):
            for iy in range(iy0, iy1 + 1):
                candidates.update(self._cells.get((ix, iy), ()))
        best: dict[str, float] | None = None
        for segment_id in sorted(candidates):
            ax, ay, vx, vy, length2, sa, sb = self._segments[segment_id]
            t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / length2))
            px, py = ax + t * vx, ay + t * vy
            distance = math.hypot(x - px, y - py)
            if distance > radius:
                continue
            if best is None or distance < best["distance_m"]:
                best = {"distance_m": distance, "station_m": sa + (sb - sa) * t}
        return best


def _explicit_km_gra(name: str) -> bool:
    upper = str(name or "").replace("\\\\", "/").rsplit("/", 1)[-1].upper()
    return bool(_EXPLICIT_KM_GRA_RE.search(upper) or "KMLINIE" in upper or "KM_LINIE" in upper)


def _asset_identity_matches_axis(axis: dict[str, Any], asset: dict[str, Any], payload: dict[str, Any]) -> bool:
    axis_route = _clean(axis.get("route_number"))
    asset_route = _clean(asset.get("route_number")) or _clean(payload.get("route_from_name"))
    if axis_route and asset_route and axis_route != asset_route:
        return False
    axis_track = _clean(axis.get("track_key"))
    asset_track = _clean(asset.get("track_key")) or _clean(payload.get("track_from_name"))
    if axis_track and asset_track:
        return axis_track.casefold() == asset_track.casefold()
    axis_direction = _clean(axis.get("direction"))
    asset_direction = _clean(payload.get("direction_from_name"))
    if axis_direction and asset_direction:
        return axis_direction.casefold() == asset_direction.casefold()
    return str(axis.get("axis_type") or "") == "STRECKE" and bool(axis_route and asset_route)


def _axis_km_context(cur, axis: dict[str, Any]) -> dict[str, Any]:
    route = _clean(axis.get("route_number"))
    if not route:
        return {
            "data_kinds": [],
            "explicit_gra": [],
            "gvp_points": [],
            "active_segment_count": 0,
        }
    params: list[Any] = [axis["scope"]]
    company_sql = "x.company_id IS NULL"
    if axis["scope"] == _SCOPE_COMPANY:
        company_sql = "x.company_id = %s"
        params.append(axis.get("company_id"))
    params.append(route)
    cur.execute(
        f"""
        SELECT x.data_kind,x.route_number,x.track_key,x.source_date,x.metadata,
               o.original_name,p.payload,p.status AS parse_status
        FROM tb.survey_catalog_assets x
        JOIN tb.survey_originals o ON o.id=x.original_id
        JOIN tb.survey_parse_catalog p ON p.original_id=x.original_id
        WHERE x.scope=%s AND {company_sql}
          AND x.route_number=%s AND x.data_status='ACTIVE' AND p.status='PARSED'
        ORDER BY x.source_date DESC NULLS LAST,x.created_at DESC
        """,
        tuple(params),
    )
    rows = [dict(row) for row in cur.fetchall()]
    kinds: set[str] = set()
    explicit_gra: list[dict[str, Any]] = []
    gvp_points: list[dict[str, Any]] = []
    for asset in rows:
        kind = str(asset.get("data_kind") or "").upper()
        kinds.add(kind)
        payload = asset.get("payload") if isinstance(asset.get("payload"), dict) else {}
        if kind == "GRA" and _explicit_km_gra(str(asset.get("original_name") or "")):
            if _asset_identity_matches_axis(axis, asset, payload):
                values = [
                    _num(point.get("km_meter"))
                    for point in (payload.get("points") or [])
                    if isinstance(point, dict)
                ]
                values = [value for value in values if value is not None]
                if len(values) >= 2:
                    explicit_gra.append(
                        {
                            "original_name": asset.get("original_name"),
                            "source_date": asset.get("source_date"),
                            "km_from_m": min(values),
                            "km_to_m": max(values),
                        }
                    )
        if kind in _GVP_DATA_KINDS:
            for point in payload.get("points") or []:
                if not isinstance(point, dict):
                    continue
                point_route = _clean(point.get("route")) or _clean(asset.get("route_number"))
                if point_route != route:
                    continue
                x, y = _num(point.get("x_dbref")), _num(point.get("y_dbref"))
                km = _num(point.get("km_meter"))
                if None not in (x, y, km):
                    gvp_points.append({"x_dbref": x, "y_dbref": y, "km_meter": km})
    cur.execute(
        "SELECT count(*) AS item_count FROM tb.survey_catalog_segments "
        "WHERE axis_id=%s AND segment_status='ACTIVE' "
        "AND source_kind <> 'MANUAL_TRA_SECTION'",
        (axis["id"],),
    )
    active_segment_count = int(cur.fetchone()["item_count"])
    return {
        "data_kinds": sorted(kinds),
        "explicit_gra": explicit_gra,
        "gvp_points": gvp_points,
        "active_segment_count": active_segment_count,
    }


def _segment_km_quality(
    cur,
    axis: dict[str, Any],
    segment: dict[str, Any],
    context: dict[str, Any],
    points: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    if segment.get("source_kind") in _MANUAL_KINDS:
        km_a, km_b = _num(segment.get("km_from_m")), _num(segment.get("km_to_m"))
        if km_a is not None and km_b is not None:
            return {
                "status": "MANUELL",
                "color": "ORANGE",
                "source": "MANUAL",
                "km_from_m": min(km_a, km_b),
                "km_to_m": max(km_a, km_b),
                "reason": None,
            }

    segment_source_date = segment.get("source_date")
    gra_candidates = [
        gra
        for gra in (context.get("explicit_gra") or [])
        if context.get("active_segment_count") == 1
        or (
            segment_source_date is not None
            and gra.get("source_date") is not None
            and gra.get("source_date") == segment_source_date
        )
    ]
    if gra_candidates:
        gra = gra_candidates[0]
        return {
            "status": "AUTOMATISCH",
            "color": "GRUEN",
            "source": "GRA_KM",
            "km_from_m": gra["km_from_m"],
            "km_to_m": gra["km_to_m"],
            "reason": None,
        }

    gvp = context.get("gvp_points") or []
    # Fewer than four reference points cannot calibrate an axis. Avoid loading
    # and densifying its geometry when the established result cannot use it.
    samples = (
        points if points is not None else _segment_points(cur, segment)
    ) if len(gvp) >= GVP_MIN_ROBUST_MATCHES else []
    if len(samples) >= 4 and len(gvp) >= GVP_MIN_ROBUST_MATCHES:
        projection_index = _GvpProjectionIndex(samples)
        offsets: list[float] = []
        for point in gvp:
            projection = projection_index.project(
                float(point["x_dbref"]),
                float(point["y_dbref"]),
            )
            if projection is None or projection["distance_m"] > GVP_MAX_AXIS_DISTANCE_M:
                continue
            offsets.append(float(point["km_meter"]) - projection["station_m"])
        if len(offsets) >= GVP_MIN_ROBUST_MATCHES:
            median_offset = float(statistics.median(offsets))
            robust = [
                value
                for value in offsets
                if abs(value - median_offset) <= GVP_OFFSET_OUTLIER_M
            ]
            if len(robust) >= GVP_MIN_ROBUST_MATCHES:
                offset = float(statistics.median(robust))
                stations = [_num(point.get("station_m")) for point in samples]
                stations = [value for value in stations if value is not None]
                if stations:
                    return {
                        "status": "AUTOMATISCH",
                        "color": "GRUEN",
                        "source": "GVP",
                        "km_from_m": min(stations) + offset,
                        "km_to_m": max(stations) + offset,
                        "matched_gvp": len(robust),
                        "median_offset_m": offset,
                        "reason": None,
                    }
    if gvp:
        return {
            "status": "UNPLAUSIBEL",
            "color": "GRAU",
            "source": "GVP",
            "km_from_m": None,
            "km_to_m": None,
            "matched_gvp": 0,
            "reason": "GVP vorhanden, aber keine robuste Kalibrierung (>=4 Treffer / <=120 m / +/-80 m Offsetfilter).",
        }
    return {
        "status": "FEHLT",
        "color": "ROT",
        "source": None,
        "km_from_m": None,
        "km_to_m": None,
        "reason": "Keine belastbare automatische oder manuelle KM-Quelle für dieses Segment.",
    }


def _axis_km_summary(cur, axis: dict[str, Any], segments: list[dict[str, Any]]) -> dict[str, Any]:
    context = _axis_km_context(cur, axis)
    qualities = [
        {"segment_id": str(segment["id"]), **_segment_km_quality(cur, axis, segment, context)}
        for segment in segments
    ]
    if not qualities:
        return {
            "status": "KEINE_GEOMETRIE",
            "color": "GRAU",
            "data_kinds": context["data_kinds"],
            "segments": [],
        }
    statuses = [row["status"] for row in qualities]
    if all(value == "AUTOMATISCH" for value in statuses):
        status, color = "AUTOMATISCH", "GRUEN"
    elif all(value == "MANUELL" for value in statuses):
        status, color = "MANUELL", "ORANGE"
    elif all(value in {"AUTOMATISCH", "MANUELL"} for value in statuses):
        status, color = "GEMISCHT", "GRAU"
    elif any(value in {"AUTOMATISCH", "MANUELL"} for value in statuses):
        status, color = "TEILWEISE", "GRAU"
    elif any(value == "UNPLAUSIBEL" for value in statuses):
        status, color = "UNPLAUSIBEL", "GRAU"
    else:
        status, color = "FEHLT", "ROT"
    return {
        "status": status,
        "color": color,
        "data_kinds": context["data_kinds"],
        "segments": qualities,
    }


def _load_original_with_parse(cur, original_id: UUID) -> tuple[dict[str, Any], dict[str, Any]]:
    cur.execute("SELECT * FROM tb.survey_originals WHERE id=%s", (original_id,))
    original = cur.fetchone()
    if not original:
        raise HTTPException(404, "SURVEY_ORIGINAL_NOT_FOUND")
    cur.execute("SELECT * FROM tb.survey_parse_catalog WHERE original_id=%s", (original_id,))
    parsed = cur.fetchone()
    if not parsed:
        raise HTTPException(409, "SURVEY_ORIGINAL_NOT_PARSED")
    return dict(original), dict(parsed)


def _segment_points(cur, segment: dict[str, Any]) -> list[dict[str, Any]]:
    geometry = segment.get("geometry")
    if isinstance(geometry, list) and len(geometry) >= 2:
        return [dict(row) for row in geometry if isinstance(row, dict)]
    original_id = segment.get("source_original_id")
    if original_id is None:
        return []
    _, parsed = _load_original_with_parse(cur, original_id)
    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    return _dense_payload(payload, segment.get("station_from_m"), segment.get("station_to_m"))


def _axis_compatible(axis: dict[str, Any], fields: dict[str, Any], candidate_bbox: tuple[float, float, float, float] | None) -> bool:
    for key in ("route_number", "station_code", "track_key", "direction"):
        left, right = _clean(axis.get(key)), _clean(fields.get(key))
        if left and right and left.casefold() != right.casefold():
            return False
    left_type = str(axis.get("axis_type") or "UNBEKANNT")
    right_type = str(fields.get("axis_type") or "UNBEKANNT")
    if left_type != "UNBEKANNT" and right_type != "UNBEKANNT" and left_type != right_type:
        return False
    object_key = _clean(fields.get("object_key"))
    axis_object_key = _clean(axis.get("object_key"))
    if object_key and axis_object_key and object_key.casefold() != axis_object_key.casefold():
        return False

    station = _clean(fields.get("station_code"))
    track = _clean(fields.get("track_key"))
    route = _clean(fields.get("route_number"))
    direction = _clean(fields.get("direction"))
    axis_station = _clean(axis.get("station_code"))
    axis_route = _clean(axis.get("route_number"))
    axis_bbox = None
    if all(axis.get(key) is not None for key in ("bbox_min_x","bbox_min_y","bbox_max_x","bbox_max_y")):
        axis_bbox = (
            float(axis["bbox_min_x"]),
            float(axis["bbox_min_y"]),
            float(axis["bbox_max_x"]),
            float(axis["bbox_max_y"]),
        )
    bbox_distance = _bbox_distance(axis_bbox, candidate_bbox)

    if object_key and axis_object_key and object_key.casefold() == axis_object_key.casefold():
        same_station = not station or not axis_station or station.casefold() == axis_station.casefold()
        same_route = not route or not axis_route or route.casefold() == axis_route.casefold()
        return same_station and same_route
    if station and track:
        return True
    if right_type == "STRECKE" and route and (direction or _clean(axis.get("direction"))):
        return True
    if route and track:
        return bbox_distance is not None and bbox_distance <= SPATIAL_TRACK_CLUSTER_M

    # Weak source identity is allowed to enter the expensive geometry comparison
    # only when route/station already gives a non-conflicting fachlicher hint and
    # the candidate lies spatially close. _relation() then decides overlap,
    # endpoint continuation or REVIEW_REQUIRED; this function never merges alone.
    same_route_hint = bool(route and axis_route and route.casefold() == axis_route.casefold())
    same_station_hint = bool(
        station and axis_station and station.casefold() == axis_station.casefold()
    )
    if (same_route_hint or same_station_hint) and bbox_distance is not None:
        return bbox_distance <= OVERLAP_NEAR_DISTANCE_M
    return False


def _candidate_axes(cur, *, scope: str, company_id: UUID | None,
                    fields: dict[str, Any],
                    candidate_bbox: tuple[float,float,float,float] | None) -> list[dict[str, Any]]:
    params: list[Any] = [scope]
    company_sql = "company_id IS NULL"
    if scope == _SCOPE_COMPANY:
        company_sql = "company_id = %s"
        params.append(company_id)
    route = _clean(fields.get("route_number"))
    station = _clean(fields.get("station_code"))
    track = _clean(fields.get("track_key"))
    object_key = _clean(fields.get("object_key"))
    filters: list[str] = []
    if station:
        filters.append("station_code = %s"); params.append(station)
    if route:
        filters.append("route_number = %s"); params.append(route)
    if track:
        filters.append("track_key = %s"); params.append(track)
    if object_key:
        filters.append("object_key = %s"); params.append(object_key)
    if not filters:
        return []
    cur.execute(
        f"""
        SELECT * FROM tb.survey_catalog_axes
        WHERE scope=%s AND {company_sql}
          AND status <> 'ARCHIVED'
          AND ({' OR '.join(filters)})
        ORDER BY updated_at DESC,id
        LIMIT 200
        """,
        tuple(params),
    )
    result = []
    for row in cur.fetchall():
        item = dict(row)
        if _axis_compatible(item, fields, candidate_bbox):
            result.append(item)
    return result


def _strong_identity(axis: dict[str, Any], fields: dict[str, Any]) -> bool:
    station, track = _clean(fields.get("station_code")), _clean(fields.get("track_key"))
    route, object_key = _clean(fields.get("route_number")), _clean(fields.get("object_key"))
    if object_key:
        axis_object_key = _clean(axis.get("object_key"))
        same_object = bool(axis_object_key and axis_object_key.casefold() == object_key.casefold())
        if same_object and station:
            return _clean(axis.get("station_code")) == station
        if same_object and route:
            return _clean(axis.get("route_number")) == route
    if station and track:
        return _clean(axis.get("station_code")) == station and _clean(axis.get("track_key")) == track
    direction = _clean(fields.get("direction"))
    if str(fields.get("axis_type") or "") == "STRECKE" and route:
        return _clean(axis.get("route_number")) == route and (
            not direction or not _clean(axis.get("direction")) or _clean(axis.get("direction")) == direction
        )
    return False


def _axis_create(cur, *, scope: str, company_id: UUID | None, fields: dict[str, Any],
                 actor_user_id: UUID | None, fallback_key: str) -> dict[str, Any]:
    identity = _identity_key(scope, company_id, fields, fallback_key)
    cur.execute("SELECT * FROM tb.survey_catalog_axes WHERE identity_key=%s FOR UPDATE", (identity,))
    existing = cur.fetchone()
    if existing:
        return dict(existing)
    cur.execute(
        """
        INSERT INTO tb.survey_catalog_axes(
            scope,company_id,identity_key,route_number,station_code,station_name,
            track_key,object_key,direction,axis_type,display_name,created_by_user_id,created_by_company_id
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        RETURNING *
        """,
        (scope,company_id,identity,fields.get("route_number"),fields.get("station_code"),
         fields.get("station_name"),fields.get("track_key"),fields.get("object_key"),fields.get("direction"),
         fields.get("axis_type"),fields.get("display_name"),actor_user_id,company_id),
    )
    axis = dict(cur.fetchone())
    _history(cur, entity_type="AXIS", entity_id=axis["id"], version=1,
             operation="CREATE", before=None, after=axis,
             actor_user_id=actor_user_id, actor_company_id=company_id)
    return axis


def _axis_extent_refresh(cur, axis_id: UUID, actor_user_id: UUID | None = None) -> dict[str, Any]:
    axis = _row(cur, "survey_catalog_axes", axis_id)
    cur.execute(
        """
        SELECT min(bbox_min_x) min_x,min(bbox_min_y) min_y,
               max(bbox_max_x) max_x,max(bbox_max_y) max_y,
               min(least(km_from_m,km_to_m)) km_from,
               max(greatest(km_from_m,km_to_m)) km_to
        FROM tb.survey_catalog_segments
        WHERE axis_id=%s AND segment_status='ACTIVE'
        """,
        (axis_id,),
    )
    ext = dict(cur.fetchone())
    values = (ext.get("min_x"),ext.get("min_y"),ext.get("max_x"),
              ext.get("max_y"),ext.get("km_from"),ext.get("km_to"))
    old = (axis.get("bbox_min_x"),axis.get("bbox_min_y"),axis.get("bbox_max_x"),
           axis.get("bbox_max_y"),axis.get("km_from_m"),axis.get("km_to_m"))
    if values == old:
        return axis
    before = dict(axis)
    version = int(axis["current_version"]) + 1
    cur.execute(
        """
        UPDATE tb.survey_catalog_axes
        SET bbox_min_x=%s,bbox_min_y=%s,bbox_max_x=%s,bbox_max_y=%s,
            km_from_m=%s,km_to_m=%s,current_version=%s
        WHERE id=%s RETURNING *
        """,
        (*values,version,axis_id),
    )
    updated = dict(cur.fetchone())
    _history(cur, entity_type="AXIS", entity_id=axis_id, version=version,
             operation="UPDATE", before=before, after=updated,
             actor_user_id=actor_user_id, actor_company_id=updated.get("company_id"))
    return updated


def _source_is_newer(candidate: dict[str, Any], existing: dict[str, Any]) -> bool:
    left, right = candidate.get("source_date"), existing.get("source_date")
    return left is not None and (right is None or left > right)


def _geometry_fully_covers(
    existing: list[dict[str, Any]],
    candidate: list[dict[str, Any]],
    *,
    max_distance_m: float = OVERLAP_DISTANCE_M,
) -> tuple[bool, dict[str, Any]]:
    if len(existing) < 2 or len(candidate) < 2:
        return False, {"reason": "GEOMETRY_TOO_SHORT"}

    step = max(1, len(existing) // 500)
    sampled = list(existing[::step])
    if sampled[-1] is not existing[-1]:
        sampled.append(existing[-1])

    distances: list[float] = []
    for point in sampled:
        x, y = _num(point.get("x_dbref")), _num(point.get("y_dbref"))
        if x is None or y is None:
            return False, {"reason": "EXISTING_GEOMETRY_INVALID"}
        distance = _project_xy(x, y, candidate)
        if distance is None:
            return False, {"reason": "CANDIDATE_GEOMETRY_INVALID"}
        distances.append(distance)

    max_distance = max(distances)
    covered_count = sum(1 for distance in distances if distance <= max_distance_m)
    fully_covered = covered_count == len(distances)
    return fully_covered, {
        "sample_count": len(distances),
        "covered_sample_count": covered_count,
        "coverage_ratio": covered_count / len(distances),
        "max_distance_m": max_distance,
        "distance_limit_m": max_distance_m,
    }


def _conflict_create(cur, *, axis_id: UUID, candidate_segment_id: UUID,
                     existing_segment_id: UUID | None, conflict_type: str,
                     details: dict[str, Any], actor_user_id: UUID | None,
                     actor_company_id: UUID | None) -> dict[str, Any]:
    cur.execute(
        """
        SELECT * FROM tb.survey_catalog_conflicts
        WHERE candidate_segment_id=%s
          AND existing_segment_id IS NOT DISTINCT FROM %s
          AND conflict_type=%s AND status='OPEN'
        LIMIT 1
        """,
        (candidate_segment_id,existing_segment_id,conflict_type),
    )
    found = cur.fetchone()
    if found:
        return dict(found)
    cur.execute(
        """
        INSERT INTO tb.survey_catalog_conflicts(
            axis_id,candidate_segment_id,existing_segment_id,conflict_type,details
        ) VALUES (%s,%s,%s,%s,%s) RETURNING *
        """,
        (axis_id,candidate_segment_id,existing_segment_id,conflict_type,Jsonb(details)),
    )
    conflict = dict(cur.fetchone())
    _history(cur, entity_type="CONFLICT", entity_id=conflict["id"], version=1,
             operation="CREATE", before=None, after=conflict,
             actor_user_id=actor_user_id, actor_company_id=actor_company_id)
    enqueue_in_tx(cur, "SURVEY_CONFLICT_EMAIL", {"conflict_id": str(conflict["id"])})
    return conflict


def _choose_axis_and_relation(cur, *, scope: str, company_id: UUID | None,
                              fields: dict[str, Any], points: list[dict[str, Any]],
                              actor_user_id: UUID | None, fallback_key: str
                              ) -> tuple[dict[str,Any],str,dict[str,Any],dict[str,Any] | None]:
    candidate_bbox = _bbox(points)
    axes = _candidate_axes(cur, scope=scope, company_id=company_id,
                           fields=fields, candidate_bbox=candidate_bbox)
    if not axes:
        axis = _axis_create(cur, scope=scope, company_id=company_id, fields=fields,
                            actor_user_id=actor_user_id, fallback_key=fallback_key)
        return axis, "PRIMARY", {}, None

    ranked: list[tuple[int,dict[str,Any],str,dict[str,Any],dict[str,Any] | None]] = []
    for axis in axes:
        cur.execute(
            """
            SELECT * FROM tb.survey_catalog_segments
            WHERE axis_id=%s AND segment_status IN ('ACTIVE','SUPERSEDED')
            ORDER BY CASE segment_status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
                     source_date DESC NULLS LAST,created_at DESC
            """,
            (axis["id"],),
        )
        segments = [dict(row) for row in cur.fetchall()]
        if not segments and _strong_identity(axis, fields):
            ranked.append((2,axis,"INDEPENDENT",{},None))
            continue
        best = None
        for existing_segment in segments:
            relation, diagnostics = _relation(_segment_points(cur, existing_segment), points)
            score = {"OVERLAP_CONFIRMED":0,"ENDPOINT_CONFIRMED":1,
                     "INDEPENDENT":3,"REVIEW_REQUIRED":4}[relation]
            candidate = (score,relation,diagnostics,existing_segment)
            if best is None or candidate[0] < best[0]:
                best = candidate
        if best is not None:
            ranked.append((best[0],axis,best[1],best[2],best[3]))

    if not ranked:
        axis = _axis_create(cur, scope=scope, company_id=company_id, fields=fields,
                            actor_user_id=actor_user_id, fallback_key=fallback_key)
        return axis, "PRIMARY", {}, None

    ranked.sort(key=lambda item: (item[0],0 if _strong_identity(item[1],fields) else 1,str(item[1]["id"])))
    best = ranked[0]
    equally_good = [row for row in ranked if row[0] == best[0] and row[0] <= 1]
    if len(equally_good) > 1:
        return best[1],"REVIEW_REQUIRED",{
            "reason":"MULTIPLE_AXIS_CANDIDATES",
            "candidate_axis_ids":[str(row[1]["id"]) for row in equally_good],
        },best[4]
    if best[2] == "INDEPENDENT" and not _strong_identity(best[1], fields):
        axis = _axis_create(cur, scope=scope, company_id=company_id, fields=fields,
                            actor_user_id=actor_user_id, fallback_key=fallback_key)
        return axis, "PRIMARY", {}, None
    return best[1],best[2],best[3],best[4]


def _segment_insert(cur, *, axis: dict[str,Any], asset_id: UUID | None,
                    source_kind: str, source_original_id: UUID | None,
                    source_signature: str | None, source_date: date | None,
                    station_from_m: float | None, station_to_m: float | None,
                    km_from_m: float | None, km_to_m: float | None,
                    points: list[dict[str,Any]], relation: str,
                    diagnostics: dict[str,Any], actor_user_id: UUID | None,
                    metadata: dict[str,Any] | None = None,
                    segment_id: UUID | None = None) -> dict[str,Any]:
    bbox = _bbox(points)
    status = "REVIEW" if relation == "REVIEW_REQUIRED" else "ACTIVE"
    merge_status = relation if relation in {
        "PRIMARY","OVERLAP_CONFIRMED","ENDPOINT_CONFIRMED","INDEPENDENT"
    } else "REVIEW_REQUIRED"
    if source_kind in _MANUAL_KINDS and relation == "PRIMARY":
        merge_status = "MANUAL"
    source_rank = 100 if source_kind == "ORIGINAL_TRA" else 50
    data = {"merge_diagnostics": diagnostics, **(metadata or {})}
    cur.execute(
        """
        INSERT INTO tb.survey_catalog_segments(
            id,axis_id,asset_id,source_kind,source_original_id,source_signature,
            source_date,source_rank,segment_status,merge_status,station_from_m,station_to_m,
            km_from_m,km_to_m,bbox_min_x,bbox_min_y,bbox_max_x,bbox_max_y,
            geometry,metadata,created_by_user_id,created_by_company_id
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        RETURNING *
        """,
        (segment_id or uuid4(),axis["id"],asset_id,source_kind,source_original_id,
         source_signature,source_date,source_rank,status,merge_status,
         station_from_m,station_to_m,km_from_m,km_to_m,
         bbox[0] if bbox else None,bbox[1] if bbox else None,
         bbox[2] if bbox else None,bbox[3] if bbox else None,
         Jsonb(points) if source_kind == "MANUAL_GPS" else None,
         Jsonb(data),actor_user_id,axis.get("company_id")),
    )
    segment = dict(cur.fetchone())
    _history(cur, entity_type="SEGMENT", entity_id=segment["id"], version=1,
             operation="CREATE", before=None, after=segment,
             actor_user_id=actor_user_id, actor_company_id=axis.get("company_id"))
    return segment


def _mark_segment_status(cur, segment: dict[str,Any], status: str,
                         actor_user_id: UUID | None) -> dict[str,Any]:
    if segment.get("segment_status") == status:
        return segment
    before = dict(segment)
    version = int(segment["current_version"]) + 1
    cur.execute(
        "UPDATE tb.survey_catalog_segments SET segment_status=%s,current_version=%s WHERE id=%s RETURNING *",
        (status,version,segment["id"]),
    )
    updated = dict(cur.fetchone())
    _history(cur, entity_type="SEGMENT", entity_id=updated["id"], version=version,
             operation="STATUS", before=before, after=updated,
             actor_user_id=actor_user_id, actor_company_id=updated.get("created_by_company_id"))
    return updated


def _axis_review(cur, axis: dict[str,Any], actor_user_id: UUID | None) -> dict[str,Any]:
    axis = _row(cur, "survey_catalog_axes", axis["id"])
    if axis["status"] in {"REVIEW", "ARCHIVED"}:
        return axis
    before = dict(axis)
    version = int(axis["current_version"]) + 1
    cur.execute(
        "UPDATE tb.survey_catalog_axes SET status='REVIEW',current_version=%s WHERE id=%s RETURNING *",
        (version,axis["id"]),
    )
    updated = dict(cur.fetchone())
    _history(cur, entity_type="AXIS", entity_id=axis["id"], version=version,
             operation="STATUS", before=before, after=updated,
             actor_user_id=actor_user_id, actor_company_id=axis.get("company_id"))
    return updated




def _axis_identity_enrichment_patch(
    axis: dict[str, Any],
    fields: dict[str, Any],
) -> dict[str, Any]:
    """Return fill-only identity fields inferred from the same already-indexed original."""
    inferred_object_key = _clean(fields.get("object_key"))
    if not inferred_object_key or _clean(axis.get("object_key")):
        return {}
    axis_type = str(axis.get("axis_type") or "UNBEKANNT").upper()
    inferred_type = str(fields.get("axis_type") or "UNBEKANNT").upper()
    if axis_type != "UNBEKANNT" and inferred_type != "UNBEKANNT" and axis_type != inferred_type:
        return {}
    return {"object_key": inferred_object_key.upper()}


def _should_reassign_existing_segment_identity(
    axis: dict[str, Any],
    fields: dict[str, Any],
    sibling_object_keys: list[str],
) -> bool:
    """Conservatively detect a legacy mixed axis that can be split by strong object identity."""
    inferred_object_key = _clean(fields.get("object_key"))
    axis_object_key = _clean(axis.get("object_key"))
    if not inferred_object_key or not axis_object_key:
        return False
    if inferred_object_key.casefold() == axis_object_key.casefold():
        return False
    axis_type = str(axis.get("axis_type") or "UNBEKANNT").upper()
    inferred_type = str(fields.get("axis_type") or "UNBEKANNT").upper()
    if axis_type != "UNBEKANNT" and inferred_type != "UNBEKANNT" and axis_type != inferred_type:
        return False
    normalized_siblings = {
        value.casefold()
        for value in (_clean(item) for item in sibling_object_keys)
        if value
    }
    # Only split automatically when another ORIGINAL_TRA on the current axis
    # independently proves that the current axis object_key belongs to that sibling.
    # This protects deliberate/admin-corrected object_key values from automatic moves.
    return axis_object_key.casefold() in normalized_siblings


def _sibling_original_object_keys(
    cur,
    *,
    axis_id: UUID,
    segment_id: UUID,
) -> list[str]:
    cur.execute(
        """
        SELECT source_original_id
        FROM tb.survey_catalog_segments
        WHERE axis_id=%s
          AND id<>%s
          AND source_kind='ORIGINAL_TRA'
          AND source_original_id IS NOT NULL
        """,
        (axis_id, segment_id),
    )
    keys: list[str] = []
    for row in cur.fetchall():
        original, parsed = _load_original_with_parse(cur, row["source_original_id"])
        payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
        sibling_fields = _axis_fields(original, payload)
        object_key = _clean(sibling_fields.get("object_key"))
        if object_key:
            keys.append(object_key.upper())
    return keys


def _reassign_existing_segment_identity(
    cur,
    *,
    existing_index: dict[str, Any],
    original: dict[str, Any],
    payload: dict[str, Any],
    fields: dict[str, Any],
    actor_user_id: UUID | None,
) -> dict[str, Any] | None:
    old_axis = _row(cur, "survey_catalog_axes", existing_index["axis_id"])
    sibling_keys = _sibling_original_object_keys(
        cur,
        axis_id=old_axis["id"],
        segment_id=existing_index["id"],
    )
    if not _should_reassign_existing_segment_identity(old_axis, fields, sibling_keys):
        return None

    # Do not silently rewrite a segment that is already part of an unresolved
    # human-review case. Such a case must remain explicit and auditable.
    cur.execute(
        """
        SELECT 1
        FROM tb.survey_catalog_conflicts
        WHERE status='OPEN'
          AND (candidate_segment_id=%s OR existing_segment_id=%s)
        LIMIT 1
        """,
        (existing_index["id"], existing_index["id"]),
    )
    if cur.fetchone():
        return None

    points = _dense_payload(payload)
    if len(points) < 2:
        return None

    scope, company_id = _scope_for_original(original)
    target_axis, relation, diagnostics, target_existing = _choose_axis_and_relation(
        cur,
        scope=scope,
        company_id=company_id,
        fields=fields,
        points=points,
        actor_user_id=actor_user_id,
        fallback_key=str(original["id"]),
    )
    if target_axis["id"] == old_axis["id"]:
        return None

    before = dict(existing_index)
    version = int(existing_index["current_version"]) + 1
    merge_status = relation if relation in {
        "PRIMARY", "OVERLAP_CONFIRMED", "ENDPOINT_CONFIRMED", "INDEPENDENT"
    } else "REVIEW_REQUIRED"
    old_status = str(existing_index.get("segment_status") or "ACTIVE").upper()
    if old_status in {"INACTIVE", "SUPERSEDED"}:
        segment_status = old_status
    else:
        segment_status = "REVIEW" if relation == "REVIEW_REQUIRED" else "ACTIVE"

    metadata = dict(existing_index.get("metadata") or {})
    metadata["merge_diagnostics"] = diagnostics
    metadata["identity_repair"] = {
        "reason": "LEGACY_OBJECT_KEY_MISMATCH",
        "from_axis_id": str(old_axis["id"]),
        "from_object_key": old_axis.get("object_key"),
        "to_axis_id": str(target_axis["id"]),
        "to_object_key": target_axis.get("object_key"),
        "source_original_id": str(original["id"]),
    }
    cur.execute(
        """
        UPDATE tb.survey_catalog_segments
        SET axis_id=%s,segment_status=%s,merge_status=%s,metadata=%s,current_version=%s
        WHERE id=%s
        RETURNING *
        """,
        (
            target_axis["id"],
            segment_status,
            merge_status,
            Jsonb(metadata),
            version,
            existing_index["id"],
        ),
    )
    updated_segment = dict(cur.fetchone())
    _history(
        cur,
        entity_type="SEGMENT",
        entity_id=updated_segment["id"],
        version=version,
        operation="UPDATE",
        before=before,
        after=updated_segment,
        actor_user_id=actor_user_id,
        actor_company_id=company_id,
    )

    conflict = None
    if relation == "REVIEW_REQUIRED":
        target_axis = _axis_review(cur, target_axis, actor_user_id)
        conflict = _conflict_create(
            cur,
            axis_id=target_axis["id"],
            candidate_segment_id=updated_segment["id"],
            existing_segment_id=target_existing["id"] if target_existing else None,
            conflict_type=str(diagnostics.get("reason") or "GEOMETRY_CONFLICT"),
            details=diagnostics,
            actor_user_id=actor_user_id,
            actor_company_id=company_id,
        )

    _axis_extent_refresh(cur, old_axis["id"], actor_user_id)
    target_axis = _axis_extent_refresh(cur, target_axis["id"], actor_user_id)
    audit(
        cur,
        action="SURVEY_CATALOG_SEGMENT_IDENTITY_REASSIGN",
        actor_user_id=actor_user_id,
        company_id=company_id,
        entity_type="SURVEY_CATALOG_SEGMENT",
        entity_id=updated_segment["id"],
        details={
            "original_id": str(original["id"]),
            "from_axis_id": str(old_axis["id"]),
            "to_axis_id": str(target_axis["id"]),
            "from_object_key": old_axis.get("object_key"),
            "to_object_key": target_axis.get("object_key"),
            "merge_status": merge_status,
            "conflict_id": str(conflict["id"]) if conflict else None,
        },
    )
    return {
        "axis": target_axis,
        "segment": updated_segment,
        "relation": relation,
        "conflict": conflict,
    }


def _enrich_existing_axis_identity(
    cur,
    *,
    axis_id: UUID,
    fields: dict[str, Any],
    actor_user_id: UUID | None,
) -> tuple[dict[str, Any], bool]:
    axis = _row(cur, "survey_catalog_axes", axis_id)
    patch = _axis_identity_enrichment_patch(axis, fields)
    if not patch:
        return axis, False
    before = dict(axis)
    version = int(axis["current_version"]) + 1
    cur.execute(
        """
        UPDATE tb.survey_catalog_axes
        SET object_key=%s,current_version=%s
        WHERE id=%s
        RETURNING *
        """,
        (patch["object_key"], version, axis_id),
    )
    updated = dict(cur.fetchone())
    _history(
        cur,
        entity_type="AXIS",
        entity_id=axis_id,
        version=version,
        operation="ENRICH",
        before=before,
        after=updated,
        actor_user_id=actor_user_id,
        actor_company_id=updated.get("company_id"),
    )
    audit(
        cur,
        action="SURVEY_CATALOG_AXIS_IDENTITY_ENRICH",
        actor_user_id=actor_user_id,
        company_id=updated.get("company_id"),
        entity_type="SURVEY_CATALOG_AXIS",
        entity_id=axis_id,
        details={
            "version": version,
            "filled_fields": sorted(patch),
            "object_key": patch.get("object_key"),
        },
    )
    return updated, True

def refresh_original_assignment_in_tx(
    cur,
    original_id: UUID,
    actor_user_id: UUID | None = None,
) -> dict[str, Any]:
    """Refresh derived catalog identity after an explicit admin assignment correction.

    The raw original and its geometry stay unchanged. Existing TRA segments keep
    their segment id; only the catalog asset/axis identity is updated.
    """
    original, parsed = _load_original_with_parse(cur, original_id)
    asset = _asset_upsert(cur, original, parsed, actor_user_id)
    if str(parsed.get("status") or "").upper() != "PARSED":
        return {"asset_id": str(asset["id"]), "status": "ERROR"}
    if str(parsed.get("detected_format") or "") != "TRA_ER0_BINARY":
        return {"asset_id": str(asset["id"]), "status": "ASSIGNMENT_UPDATED_ASSET"}

    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    fields = _axis_fields(original, payload)
    cur.execute(
        """
        SELECT *
        FROM tb.survey_catalog_segments
        WHERE source_original_id=%s AND source_kind='ORIGINAL_TRA'
        ORDER BY created_at,id
        LIMIT 2
        """,
        (original_id,),
    )
    rows = [dict(row) for row in cur.fetchall()]
    if not rows:
        return index_original_in_tx(cur, original_id, actor_user_id)
    if len(rows) != 1:
        raise HTTPException(
            409,
            {"code": "SURVEY_ASSIGNMENT_UPDATE_MULTIPLE_SOURCE_SEGMENTS", "count": len(rows)},
        )
    segment = rows[0]

    cur.execute(
        """
        SELECT id
        FROM tb.survey_catalog_conflicts
        WHERE status='OPEN'
          AND (candidate_segment_id=%s OR existing_segment_id=%s)
        LIMIT 1
        """,
        (segment["id"], segment["id"]),
    )
    conflict = cur.fetchone()
    if conflict:
        raise HTTPException(
            409,
            {
                "code": "SURVEY_ASSIGNMENT_UPDATE_OPEN_CONFLICT",
                "conflict_id": str(conflict["id"]),
            },
        )

    axis = _row(cur, "survey_catalog_axes", segment["axis_id"])
    scope, company_id = _scope_for_original(original)
    if str(axis.get("scope") or "") != scope or axis.get("company_id") != company_id:
        raise HTTPException(409, "SURVEY_ASSIGNMENT_UPDATE_SCOPE_CHANGE_NOT_SUPPORTED")

    cur.execute(
        """
        SELECT count(*)::int AS item_count
        FROM tb.survey_catalog_segments
        WHERE axis_id=%s AND id<>%s AND segment_status<>'INACTIVE'
        """,
        (axis["id"], segment["id"]),
    )
    other_segment_count = int(cur.fetchone()["item_count"])

    identity_keys = (
        "route_number",
        "station_code",
        "track_key",
        "object_key",
        "direction",
        "axis_type",
    )
    destructive_identity_change = False
    for key in identity_keys:
        before_value = _clean(axis.get(key))
        after_value = _clean(fields.get(key))
        if key == "axis_type":
            before_value = str(axis.get(key) or "UNBEKANNT").upper()
            after_value = str(fields.get(key) or "UNBEKANNT").upper()
        if before_value and after_value != before_value:
            destructive_identity_change = True
            break
    if other_segment_count and destructive_identity_change:
        raise HTTPException(
            409,
            {
                "code": "SURVEY_ASSIGNMENT_UPDATE_SHARED_AXIS_REQUIRES_REVIEW",
                "axis_id": str(axis["id"]),
                "other_segment_count": other_segment_count,
            },
        )

    target_identity = _identity_key(scope, company_id, fields, str(original_id))
    if target_identity != str(axis.get("identity_key") or ""):
        cur.execute(
            """
            SELECT id
            FROM tb.survey_catalog_axes
            WHERE identity_key=%s AND id<>%s AND status<>'ARCHIVED'
            LIMIT 1
            """,
            (target_identity, axis["id"]),
        )
        collision = cur.fetchone()
        if collision:
            raise HTTPException(
                409,
                {
                    "code": "SURVEY_ASSIGNMENT_UPDATE_AXIS_COLLISION",
                    "axis_id": str(axis["id"]),
                    "target_axis_id": str(collision["id"]),
                },
            )

    desired = {
        "identity_key": target_identity,
        "route_number": fields.get("route_number"),
        "station_code": fields.get("station_code"),
        "station_name": fields.get("station_name"),
        "track_key": fields.get("track_key"),
        "object_key": fields.get("object_key"),
        "direction": fields.get("direction"),
        "axis_type": fields.get("axis_type"),
        "display_name": fields.get("display_name"),
    }
    changed = any(axis.get(key) != value for key, value in desired.items())
    if not changed:
        return {
            "asset_id": str(asset["id"]),
            "segment_id": str(segment["id"]),
            "axis_id": str(axis["id"]),
            "status": "ASSIGNMENT_UNCHANGED",
            "object_key": axis.get("object_key"),
        }

    before = dict(axis)
    version = int(axis["current_version"]) + 1
    cur.execute(
        """
        UPDATE tb.survey_catalog_axes
        SET identity_key=%s,route_number=%s,station_code=%s,station_name=%s,
            track_key=%s,object_key=%s,direction=%s,axis_type=%s,display_name=%s,
            current_version=%s
        WHERE id=%s
        RETURNING *
        """,
        (
            desired["identity_key"],
            desired["route_number"],
            desired["station_code"],
            desired["station_name"],
            desired["track_key"],
            desired["object_key"],
            desired["direction"],
            desired["axis_type"],
            desired["display_name"],
            version,
            axis["id"],
        ),
    )
    updated = dict(cur.fetchone())
    _history(
        cur,
        entity_type="AXIS",
        entity_id=updated["id"],
        version=version,
        operation="UPDATE",
        before=before,
        after=updated,
        actor_user_id=actor_user_id,
        actor_company_id=company_id,
    )
    audit(
        cur,
        action="SURVEY_CATALOG_ASSIGNMENT_UPDATE",
        actor_user_id=actor_user_id,
        company_id=company_id,
        entity_type="SURVEY_CATALOG_AXIS",
        entity_id=updated["id"],
        details={
            "original_id": str(original_id),
            "segment_id": str(segment["id"]),
            "other_segment_count": other_segment_count,
            "before": {
                key: before.get(key)
                for key in (
                    "route_number",
                    "station_code",
                    "station_name",
                    "track_key",
                    "object_key",
                    "direction",
                    "axis_type",
                    "display_name",
                )
            },
            "after": {
                key: updated.get(key)
                for key in (
                    "route_number",
                    "station_code",
                    "station_name",
                    "track_key",
                    "object_key",
                    "direction",
                    "axis_type",
                    "display_name",
                )
            },
        },
    )
    return {
        "asset_id": str(asset["id"]),
        "segment_id": str(segment["id"]),
        "axis_id": str(updated["id"]),
        "status": "ASSIGNMENT_UPDATED",
        "axis_version": version,
        "object_key": updated.get("object_key"),
    }


def index_original_in_tx(cur, original_id: UUID, actor_user_id: UUID | None = None) -> dict[str,Any]:
    original, parsed = _load_original_with_parse(cur, original_id)
    asset = _asset_upsert(cur, original, parsed, actor_user_id)
    if str(parsed.get("status") or "").upper() != "PARSED":
        return {"asset_id":str(asset["id"]),"status":"ERROR"}
    if str(parsed.get("detected_format") or "") != "TRA_ER0_BINARY":
        return {"asset_id":str(asset["id"]),"status":"INDEXED_ASSET"}

    payload = parsed.get("payload") if isinstance(parsed.get("payload"), dict) else {}
    fields = _axis_fields(original, payload)
    cur.execute(
        "SELECT * FROM tb.survey_catalog_segments WHERE source_original_id=%s AND source_kind='ORIGINAL_TRA'",
        (original_id,),
    )
    existing_index = cur.fetchone()
    if existing_index:
        existing_index = dict(existing_index)
        reassigned = _reassign_existing_segment_identity(
            cur,
            existing_index=existing_index,
            original=original,
            payload=payload,
            fields=fields,
            actor_user_id=actor_user_id,
        )
        if reassigned is not None:
            axis = reassigned["axis"]
            segment = reassigned["segment"]
            return {
                "asset_id":str(asset["id"]),
                "segment_id":str(segment["id"]),
                "axis_id":str(axis["id"]),
                "status":"ALREADY_INDEXED_REASSIGNED",
                "axis_enriched":False,
                "axis_reassigned":True,
                "merge_status":segment.get("merge_status"),
                "object_key":axis.get("object_key"),
            }
        axis, enriched = _enrich_existing_axis_identity(
            cur,
            axis_id=existing_index["axis_id"],
            fields=fields,
            actor_user_id=actor_user_id,
        )
        return {
            "asset_id":str(asset["id"]),
            "segment_id":str(existing_index["id"]),
            "axis_id":str(axis["id"]),
            "status":"ALREADY_INDEXED_ENRICHED" if enriched else "ALREADY_INDEXED",
            "axis_enriched":enriched,
            "axis_reassigned":False,
            "object_key":axis.get("object_key"),
        }

    points = _dense_payload(payload)
    if len(points) < 2:
        return {"asset_id":str(asset["id"]),"status":"GEOMETRY_UNUSABLE"}
    scope, company_id = _scope_for_original(original)
    axis, relation, diagnostics, existing_segment = _choose_axis_and_relation(
        cur,scope=scope,company_id=company_id,fields=fields,points=points,
        actor_user_id=actor_user_id,fallback_key=str(original_id))
    station_from, station_to = _station_extent(payload)
    km_from, km_to = _km_extent(payload)
    segment = _segment_insert(
        cur,axis=axis,asset_id=asset["id"],source_kind="ORIGINAL_TRA",
        source_original_id=original_id,source_signature=str(original.get("sha256") or "")[:24] or None,
        source_date=_source_date(original),station_from_m=station_from,station_to_m=station_to,
        km_from_m=km_from,km_to_m=km_to,points=points,relation=relation,
        diagnostics=diagnostics,actor_user_id=actor_user_id,
        metadata={"original_name":original.get("original_name")})
    conflict = None
    if relation == "REVIEW_REQUIRED":
        axis = _axis_review(cur, axis, actor_user_id)
        conflict = _conflict_create(
            cur,axis_id=axis["id"],candidate_segment_id=segment["id"],
            existing_segment_id=existing_segment["id"] if existing_segment else None,
            conflict_type=str(diagnostics.get("reason") or "GEOMETRY_CONFLICT"),
            details=diagnostics,actor_user_id=actor_user_id,actor_company_id=company_id)
    elif relation == "OVERLAP_CONFIRMED" and existing_segment is not None:
        existing_points = _segment_points(cur, existing_segment)
        fully_covered, coverage_details = _geometry_fully_covers(existing_points, points)
        metadata = dict(segment.get("metadata") or {})
        metadata["existing_full_coverage"] = {
            "confirmed": fully_covered,
            **coverage_details,
        }
        cur.execute(
            "UPDATE tb.survey_catalog_segments SET metadata=%s WHERE id=%s",
            (Jsonb(metadata), segment["id"]),
        )
        segment["metadata"] = metadata
        original_beats_manual = (
            # A TRA section contributes KM anchors, not replacement geometry.
            # Importing an original must not discard that independent overlay.
            existing_segment.get("source_kind") == "MANUAL_GPS"
            and fully_covered
        )
        newer_full = (
            existing_segment.get("source_kind") == "ORIGINAL_TRA"
            and _source_is_newer(segment, existing_segment)
            and fully_covered
        )
        if original_beats_manual or newer_full:
            _mark_segment_status(cur, existing_segment, "SUPERSEDED", actor_user_id)

    _axis_extent_refresh(cur, axis["id"], actor_user_id)
    audit(
        cur,action="SURVEY_CATALOG_ORIGINAL_INDEX",actor_user_id=actor_user_id,
        company_id=company_id,entity_type="SURVEY_CATALOG_SEGMENT",entity_id=segment["id"],
        details={"original_id":str(original_id),"axis_id":str(axis["id"]),
                 "merge_status":relation,"conflict_id":str(conflict["id"]) if conflict else None},
    )
    return {
        "asset_id":str(asset["id"]),"axis_id":str(axis["id"]),
        "segment_id":str(segment["id"]),
        "status":"REVIEW_REQUIRED" if conflict else (
            "MERGED" if relation in {"OVERLAP_CONFIRMED","ENDPOINT_CONFIRMED"} else "ADDED"
        ),
        "merge_status":relation,
        "conflict_id":str(conflict["id"]) if conflict else None,
    }


def _receipt(cur, user: dict[str,Any], client_change_id: str) -> dict[str,Any] | None:
    cur.execute(
        "SELECT response_payload FROM tb.mutation_receipts WHERE user_id=%s AND client_change_id=%s",
        (user["user_id"],client_change_id),
    )
    row = cur.fetchone()
    return dict(row["response_payload"]) if row else None


def _save_receipt(cur, user: dict[str,Any], client_change_id: str,
                  entity_id: UUID, version: int, response: dict[str,Any]) -> None:
    cur.execute(
        """
        INSERT INTO tb.mutation_receipts(
            device_id,user_id,client_change_id,entity_type,entity_id,result_version,response_payload
        ) VALUES (%s,%s,%s,'SURVEY_CATALOG_SEGMENT',%s,%s,%s)
        """,
        (user.get("device_id"),user["user_id"],client_change_id,entity_id,version,Jsonb(response)),
    )


def _resolve_source_original(cur, user: dict[str,Any], source_original_id: UUID | None,
                             source_signature: str | None
                             ) -> tuple[dict[str,Any],dict[str,Any]] | None:
    if source_original_id is not None:
        original, parsed = _load_original_with_parse(cur, source_original_id)
    elif _clean(source_signature):
        signature = _clean(source_signature)
        assert signature is not None
        if user.get("company_role") == "ADMIN":
            cur.execute(
                "SELECT id FROM tb.survey_originals WHERE sha256 LIKE %s ORDER BY (company_id IS NULL) DESC,imported_at DESC LIMIT 1",
                (signature+"%",),
            )
        else:
            cur.execute(
                """
                SELECT id FROM tb.survey_originals
                WHERE sha256 LIKE %s AND (company_id IS NULL OR company_id=%s)
                ORDER BY (company_id IS NULL) DESC,imported_at DESC LIMIT 1
                """,
                (signature+"%",user.get("company_id")),
            )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "SURVEY_SOURCE_SIGNATURE_NOT_FOUND")
        original, parsed = _load_original_with_parse(cur, row["id"])
    else:
        return None
    if original.get("company_id") is not None and user.get("company_role") != "ADMIN":
        if original.get("company_id") != user.get("company_id"):
            raise HTTPException(403, "SURVEY_ORIGINAL_ACCESS_DENIED")
    if str(parsed.get("detected_format") or "") != "TRA_ER0_BINARY":
        raise HTTPException(400, "SURVEY_MANUAL_TRA_SOURCE_NOT_TRA")
    return original, parsed


def _manual_fields(payload: ManualSegmentCreate | ManualSegmentUpdate,
                   existing_axis: dict[str,Any] | None = None) -> dict[str,Any]:
    def value(name: str, default: Any = None) -> Any:
        raw = getattr(payload,name,None)
        return raw if raw is not None else (existing_axis.get(name) if existing_axis else default)
    axis_type = str(value("axis_type","GLEIS") or "GLEIS").upper()
    if axis_type not in _AXIS_TYPES:
        raise HTTPException(400, "SURVEY_AXIS_TYPE_INVALID")
    display_name = _clean(value("display_name"))
    if not display_name:
        raise HTTPException(400, "SURVEY_AXIS_DISPLAY_NAME_REQUIRED")
    station_code = _clean(value("station_code"))
    return {
        "route_number":_clean(value("route_number")),
        "station_code":station_code.upper() if station_code else None,
        "station_name":_clean(value("station_name")),
        "track_key":_clean(value("track_key")),
        "object_key":(_clean(value("object_key")) or "").upper() or None,
        "direction":_clean(value("direction")),
        "axis_type":axis_type,
        "display_name":display_name,
    }


def _manual_points(payload: ManualSegmentCreate, user: dict[str,Any], cur
                   ) -> tuple[list[dict[str,Any]],UUID | None,str | None]:
    source_kind = payload.source_kind.strip().upper()
    if source_kind not in _MANUAL_KINDS:
        raise HTTPException(400, "SURVEY_MANUAL_SOURCE_KIND_INVALID")
    if source_kind == "MANUAL_GPS":
        points = _manual_geometry_points(payload.geometry)
        if len(points) < 2:
            raise HTTPException(400, "SURVEY_MANUAL_GPS_REQUIRES_GEOMETRY")
        return points,None,payload.source_signature
    source = _resolve_source_original(cur,user,payload.source_original_id,payload.source_signature)
    if source is None:
        raise HTTPException(400, "SURVEY_MANUAL_TRA_SOURCE_REQUIRED")
    original, parsed = source
    parsed_payload = parsed.get("payload") if isinstance(parsed.get("payload"),dict) else {}
    points = _dense_payload(parsed_payload,payload.station_from_m,payload.station_to_m)
    if len(points) < 2:
        raise HTTPException(400, "SURVEY_MANUAL_TRA_SECTION_EMPTY")
    return points,original["id"],str(original.get("sha256") or "")[:24] or None


@router.post("/manual-segments", status_code=201)
def create_manual_segment(payload: ManualSegmentCreate,
                          user: dict[str,Any] = Depends(require_csrf)) -> dict[str,Any]:
    source_kind = payload.source_kind.strip().upper()
    scope, company_id = _normalize_scope(payload.scope,user)
    fields = _manual_fields(payload)
    with transaction() as conn, conn.cursor() as cur:
        replay = _receipt(cur,user,payload.client_change_id)
        if replay is not None:
            return replay
        points, source_original_id, source_signature = _manual_points(payload,user,cur)
        axis, relation, diagnostics, existing_segment = _choose_axis_and_relation(
            cur,scope=scope,company_id=company_id,fields=fields,points=points,
            actor_user_id=user["user_id"],fallback_key=str(payload.id or uuid4()))
        segment = _segment_insert(
            cur,axis=axis,asset_id=None,source_kind=source_kind,
            source_original_id=source_original_id,source_signature=source_signature,
            source_date=None,station_from_m=payload.station_from_m,station_to_m=payload.station_to_m,
            km_from_m=payload.km_from_m,km_to_m=payload.km_to_m,points=points,
            relation=relation,diagnostics=diagnostics,actor_user_id=user["user_id"],
            metadata=dict(payload.metadata),segment_id=payload.id)
        conflict = None
        if relation == "REVIEW_REQUIRED":
            axis = _axis_review(cur,axis,user["user_id"])
            conflict = _conflict_create(
                cur,axis_id=axis["id"],candidate_segment_id=segment["id"],
                existing_segment_id=existing_segment["id"] if existing_segment else None,
                conflict_type=str(diagnostics.get("reason") or "MANUAL_GEOMETRY_CONFLICT"),
                details=diagnostics,actor_user_id=user["user_id"],
                actor_company_id=user.get("company_id"))
        elif (source_kind == "MANUAL_GPS" and relation == "OVERLAP_CONFIRMED"
              and existing_segment is not None and existing_segment.get("source_kind") == "ORIGINAL_TRA"):
            segment = _mark_segment_status(cur,segment,"SUPERSEDED",user["user_id"])
        _axis_extent_refresh(cur,axis["id"],user["user_id"])
        response = {
            "segment_id":str(segment["id"]),"axis_id":str(axis["id"]),
            "entity_version":int(segment["current_version"]),
            "status":"REVIEW_REQUIRED" if conflict else (
                "MERGED" if relation in {"OVERLAP_CONFIRMED","ENDPOINT_CONFIRMED"} else "ADDED"
            ),
            "merge_status":relation,
            "conflict_id":str(conflict["id"]) if conflict else None,
        }
        _save_receipt(cur,user,payload.client_change_id,segment["id"],
                      int(segment["current_version"]),response)
        audit(
            cur,action="SURVEY_CATALOG_MANUAL_CREATE",actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),company_id=user.get("company_id"),
            entity_type="SURVEY_CATALOG_SEGMENT",entity_id=segment["id"],
            details={"axis_id":str(axis["id"]),"source_kind":source_kind,"status":response["status"]},
        )
        return response


@router.patch("/manual-segments/{segment_id}")
def update_manual_segment(segment_id: UUID, payload: ManualSegmentUpdate,
                          user: dict[str,Any] = Depends(require_csrf)) -> dict[str,Any]:
    with transaction() as conn, conn.cursor() as cur:
        replay = _receipt(cur,user,payload.client_change_id)
        if replay is not None:
            return replay
        cur.execute(
            """
            SELECT s.*,a.scope,a.company_id,a.route_number AS axis_route_number,
                   a.station_code AS axis_station_code,a.station_name AS axis_station_name,
                   a.track_key AS axis_track_key,a.direction AS axis_direction,
                   a.axis_type AS axis_axis_type,a.display_name AS axis_display_name
            FROM tb.survey_catalog_segments s
            JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
            WHERE s.id=%s FOR UPDATE
            """,
            (segment_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "SURVEY_MANUAL_SEGMENT_NOT_FOUND")
        segment = dict(row)
        _assert_segment_write_access(user, segment)
        if segment["source_kind"] not in _MANUAL_KINDS:
            raise HTTPException(400, "SURVEY_SEGMENT_NOT_MANUAL")
        if int(segment["current_version"]) != payload.base_version:
            raise HTTPException(409, {"code":"VERSION_CONFLICT","current_version":segment["current_version"]})
        axis_existing = {
            "route_number":segment.get("axis_route_number"),
            "station_code":segment.get("axis_station_code"),
            "station_name":segment.get("axis_station_name"),
            "track_key":segment.get("axis_track_key"),
            "direction":segment.get("axis_direction"),
            "axis_type":segment.get("axis_axis_type"),
            "display_name":segment.get("axis_display_name"),
        }
        fields = _manual_fields(payload,axis_existing)
        points = _manual_geometry_points(payload.geometry) if payload.geometry is not None else _segment_points(cur,segment)
        if len(points) < 2:
            raise HTTPException(400, "SURVEY_MANUAL_GEOMETRY_INVALID")
        axis, relation, diagnostics, existing_segment = _choose_axis_and_relation(
            cur,scope=segment["scope"],company_id=segment["company_id"],fields=fields,
            points=points,actor_user_id=user["user_id"],fallback_key=str(segment_id))
        before = {k:v for k,v in segment.items() if not k.startswith("axis_")}
        bbox = _bbox(points)
        version = int(segment["current_version"]) + 1
        geometry_value = points if segment["source_kind"] == "MANUAL_GPS" or payload.geometry is not None else segment.get("geometry")
        metadata = payload.metadata if payload.metadata is not None else (segment.get("metadata") or {})
        status = "REVIEW" if relation == "REVIEW_REQUIRED" else "ACTIVE"
        merge_status = "MANUAL" if relation == "PRIMARY" else relation
        cur.execute(
            """
            UPDATE tb.survey_catalog_segments
            SET axis_id=%s,segment_status=%s,merge_status=%s,
                station_from_m=%s,station_to_m=%s,km_from_m=%s,km_to_m=%s,
                bbox_min_x=%s,bbox_min_y=%s,bbox_max_x=%s,bbox_max_y=%s,
                geometry=%s,metadata=%s,current_version=%s
            WHERE id=%s AND current_version=%s RETURNING *
            """,
            (axis["id"],status,merge_status,
             payload.station_from_m if payload.station_from_m is not None else segment.get("station_from_m"),
             payload.station_to_m if payload.station_to_m is not None else segment.get("station_to_m"),
             payload.km_from_m if payload.km_from_m is not None else segment.get("km_from_m"),
             payload.km_to_m if payload.km_to_m is not None else segment.get("km_to_m"),
             bbox[0] if bbox else None,bbox[1] if bbox else None,
             bbox[2] if bbox else None,bbox[3] if bbox else None,
             Jsonb(geometry_value) if geometry_value is not None else None,
             Jsonb(metadata),version,segment_id,payload.base_version),
        )
        updated = cur.fetchone()
        if not updated:
            raise HTTPException(409, "VERSION_CONFLICT")
        segment = dict(updated)
        _history(cur,entity_type="SEGMENT",entity_id=segment_id,version=version,
                 operation="UPDATE",before=before,after=segment,
                 actor_user_id=user["user_id"],actor_company_id=user.get("company_id"))
        conflict = None
        if relation == "REVIEW_REQUIRED":
            _axis_review(cur,axis,user["user_id"])
            conflict = _conflict_create(
                cur,axis_id=axis["id"],candidate_segment_id=segment_id,
                existing_segment_id=existing_segment["id"] if existing_segment else None,
                conflict_type=str(diagnostics.get("reason") or "MANUAL_GEOMETRY_CONFLICT"),
                details=diagnostics,actor_user_id=user["user_id"],
                actor_company_id=user.get("company_id"))
        _axis_extent_refresh(cur,axis["id"],user["user_id"])
        if before["axis_id"] != axis["id"]:
            _axis_extent_refresh(cur,before["axis_id"],user["user_id"])
        response = {
            "segment_id":str(segment_id),"axis_id":str(axis["id"]),"entity_version":version,
            "status":"REVIEW_REQUIRED" if conflict else "MERGED",
            "merge_status":relation,"conflict_id":str(conflict["id"]) if conflict else None,
        }
        _save_receipt(cur,user,payload.client_change_id,segment_id,version,response)
        audit(
            cur,action="SURVEY_CATALOG_MANUAL_UPDATE",actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),company_id=user.get("company_id"),
            entity_type="SURVEY_CATALOG_SEGMENT",entity_id=segment_id,
            details={"axis_id":str(axis["id"]),"status":response["status"]},
        )
        return response



@router.delete("/manual-segments/{segment_id}")
def deactivate_manual_segment(
    segment_id: UUID,
    payload: ManualSegmentDeactivate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        replay = _receipt(cur, user, payload.client_change_id)
        if replay is not None:
            return replay
        cur.execute(
            """
            SELECT s.*,a.scope,a.company_id
            FROM tb.survey_catalog_segments s
            JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
            WHERE s.id=%s FOR UPDATE
            """,
            (segment_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "SURVEY_MANUAL_SEGMENT_NOT_FOUND")
        segment = dict(row)
        _assert_segment_write_access(user, segment)
        if segment["source_kind"] not in _MANUAL_KINDS:
            raise HTTPException(400, "SURVEY_SEGMENT_NOT_MANUAL")
        if int(segment["current_version"]) != payload.base_version:
            raise HTTPException(
                409,
                {"code": "VERSION_CONFLICT", "current_version": segment["current_version"]},
            )
        updated = _mark_segment_status(cur, segment, "INACTIVE", user["user_id"])
        cur.execute(
            """
            SELECT * FROM tb.survey_catalog_conflicts
            WHERE candidate_segment_id=%s AND status='OPEN'
            FOR UPDATE
            """,
            (segment_id,),
        )
        for raw_conflict in cur.fetchall():
            conflict = dict(raw_conflict)
            before_conflict = dict(conflict)
            conflict_version = int(conflict["current_version"]) + 1
            cur.execute(
                """
                UPDATE tb.survey_catalog_conflicts
                SET status='RESOLVED',resolution='USER_WITHDREW',
                    resolution_note='Manueller Beitrag wurde vom Nutzer deaktiviert.',
                    current_version=%s,resolved_by_user_id=%s,resolved_at=now()
                WHERE id=%s RETURNING *
                """,
                (conflict_version, user["user_id"], conflict["id"]),
            )
            resolved = dict(cur.fetchone())
            _history(
                cur,
                entity_type="CONFLICT",
                entity_id=conflict["id"],
                version=conflict_version,
                operation="RESOLVE",
                before=before_conflict,
                after=resolved,
                actor_user_id=user["user_id"],
                actor_company_id=user.get("company_id"),
            )
        _axis_extent_refresh(cur, segment["axis_id"], user["user_id"])
        response = {
            "segment_id": str(segment_id),
            "axis_id": str(segment["axis_id"]),
            "entity_version": int(updated["current_version"]),
            "status": "DEACTIVATED",
        }
        _save_receipt(
            cur,
            user,
            payload.client_change_id,
            segment_id,
            int(updated["current_version"]),
            response,
        )
        audit(
            cur,
            action="SURVEY_CATALOG_MANUAL_DEACTIVATE",
            actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),
            company_id=user.get("company_id"),
            entity_type="SURVEY_CATALOG_SEGMENT",
            entity_id=segment_id,
            details={"axis_id": str(segment["axis_id"])},
        )
        return response


@router.get("/search")
def search_catalog(q: str = Query(default="", max_length=160),
                   limit: int = Query(default=30, ge=1, le=100),
                   user: dict[str,Any] = Depends(current_portal_user)) -> dict[str,Any]:
    visible, params = _visible_sql(user,"a")
    query = q.strip()
    like = f"%{query}%"
    where = [visible,"a.status <> 'ARCHIVED'"]
    if query:
        where.append(
            "(a.route_number ILIKE %s OR a.station_code ILIKE %s OR a.station_name ILIKE %s "
            "OR a.track_key ILIKE %s OR a.display_name ILIKE %s)"
        )
        params.extend([like,like,like,like,like])
    params.append(limit)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT a.*,
                   (SELECT count(*) FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') active_segment_count,
                   (SELECT count(*) FROM tb.survey_catalog_conflicts c
                    WHERE c.axis_id=a.id AND c.status='OPEN') open_conflict_count
            FROM tb.survey_catalog_axes a
            WHERE {' AND '.join(where)}
            ORDER BY CASE WHEN a.route_number=%s THEN 0 ELSE 1 END,
                     lower(a.display_name),a.id
            LIMIT %s
            """,
            tuple(params[:-1]+[query,params[-1]]),
        )
        axes = [_catalog_response(user, dict(row)) for row in cur.fetchall()]
        asset_visible, asset_params = _visible_sql(user,"x")
        cur.execute(
            f"""
            SELECT route_number,array_agg(DISTINCT data_kind ORDER BY data_kind) data_kinds,
                   count(*) asset_count,min(km_from_m) km_from_m,max(km_to_m) km_to_m
            FROM tb.survey_catalog_assets x
            WHERE {asset_visible} AND data_status='ACTIVE' AND route_number IS NOT NULL
              AND (%s='' OR route_number ILIKE %s)
            GROUP BY route_number ORDER BY route_number LIMIT %s
            """,
            tuple(asset_params+[query,like,limit]),
        )
        routes = [_plain(dict(row)) for row in cur.fetchall()]
        cur.execute(
            f"""
            SELECT station_code,max(station_name) station_name,count(*) asset_count,
                   array_agg(DISTINCT data_kind ORDER BY data_kind) data_kinds
            FROM tb.survey_catalog_assets x
            WHERE {asset_visible} AND data_status='ACTIVE' AND station_code IS NOT NULL
              AND (%s='' OR station_code ILIKE %s OR station_name ILIKE %s)
            GROUP BY station_code ORDER BY lower(max(station_name)),station_code LIMIT %s
            """,
            tuple(asset_params+[query,like,like,limit]),
        )
        stations = [_plain(dict(row)) for row in cur.fetchall()]
    return {"query":query,"routes":routes,"stations":stations,"axes":axes}


@router.get("/availability")
def availability(route_number: str | None = Query(default=None,max_length=80),
                 station_code: str | None = Query(default=None,max_length=50),
                 user: dict[str,Any] = Depends(current_portal_user)) -> dict[str,Any]:
    route, station = _clean(route_number), _clean(station_code)
    if not route and not station:
        raise HTTPException(400, "SURVEY_CATALOG_ROUTE_OR_STATION_REQUIRED")
    visible, params = _visible_sql(user,"a")
    where = [visible,"a.status <> 'ARCHIVED'"]
    if route:
        where.append("a.route_number=%s"); params.append(route)
    if station:
        where.append("a.station_code=%s"); params.append(station.upper())
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT a.*,
                   (SELECT count(*) FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') active_segment_count,
                   (SELECT count(*) FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='REVIEW') review_segment_count
            FROM tb.survey_catalog_axes a
            WHERE {' AND '.join(where)}
            ORDER BY lower(a.display_name),a.id
            """,
            tuple(params),
        )
        axes = [dict(row) for row in cur.fetchall()]
        all_quality_rows: list[dict[str, Any]] = []
        for axis in axes:
            cur.execute(
                """
                SELECT * FROM tb.survey_catalog_segments
                WHERE axis_id=%s AND segment_status='ACTIVE'
                ORDER BY created_at,id
                """,
                (axis["id"],),
            )
            active_segments = [dict(row) for row in cur.fetchall()]
            quality = _axis_km_summary(cur, axis, active_segments)
            axis["km_quality"] = quality
            all_quality_rows.extend(quality["segments"])
        asset_visible, asset_params = _visible_sql(user,"x")
        asset_where = [asset_visible,"x.data_status='ACTIVE'"]
        if route:
            asset_where.append("x.route_number=%s"); asset_params.append(route)
        if station:
            asset_where.append("x.station_code=%s"); asset_params.append(station.upper())
        cur.execute(
            f"""
            SELECT data_kind,count(*) item_count,
                   min(least(km_from_m,km_to_m)) km_from_m,
                   max(greatest(km_from_m,km_to_m)) km_to_m,
                   max(source_date) newest_source_date
            FROM tb.survey_catalog_assets x
            WHERE {' AND '.join(asset_where)}
            GROUP BY data_kind ORDER BY data_kind
            """,
            tuple(asset_params),
        )
        data_kinds = [_plain(dict(row)) for row in cur.fetchall()]
        segment_visible, segment_params = _visible_sql(user,"a")
        segment_where = [segment_visible,"s.segment_status='ACTIVE'"]
        if route:
            segment_where.append("a.route_number=%s"); segment_params.append(route)
        if station:
            segment_where.append("a.station_code=%s"); segment_params.append(station.upper())
        cur.execute(
            f"""
            SELECT s.source_kind,count(*) item_count
            FROM tb.survey_catalog_segments s
            JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
            WHERE {' AND '.join(segment_where)}
            GROUP BY s.source_kind ORDER BY s.source_kind
            """,
            tuple(segment_params),
        )
        source_kinds = [_plain(dict(row)) for row in cur.fetchall()]
    active = sum(int(row["active_segment_count"]) for row in axes)
    review = sum(int(row["review_segment_count"]) for row in axes)
    status = "AVAILABLE" if active else "PARTIAL" if axes or data_kinds else "MISSING"
    if review:
        status = "PARTIAL"
    quality_counts: dict[str, int] = {}
    for item in all_quality_rows:
        key = str(item.get("status") or "UNBEKANNT")
        quality_counts[key] = quality_counts.get(key, 0) + 1
    return {
        "status":status,"route_number":route,
        "station_code":station.upper() if station else None,
        "axis_count":len(axes),"active_segment_count":active,
        "review_segment_count":review,"data_kinds":data_kinds,
        "source_kinds":source_kinds,"km_quality_counts":quality_counts,
        "axes":[_catalog_response(user, row) for row in axes],
    }


def _decimate(points: list[dict[str,Any]], max_points: int) -> list[dict[str,Any]]:
    if len(points) <= max_points:
        return points
    stride = max(1,math.ceil((len(points)-1)/(max_points-1)))
    result = points[::stride]
    if result[-1] is not points[-1]:
        result.append(points[-1])
    return result[:max_points]


_DBREF_ZONE_SRIDS = {2: 5682, 3: 5683, 4: 5684, 5: 5685}


def _dbref_srid_for_x(value: Any) -> int | None:
    x = _num(value)
    if x is None:
        return None
    zone = int(abs(x) // 1_000_000)
    return _DBREF_ZONE_SRIDS.get(zone)


def _postgis_transform_points(
    cur,
    points: list[tuple[float, float]],
) -> list[tuple[float, float] | None]:
    prepared: list[tuple[int, float, float, int]] = []
    for index, (x, y) in enumerate(points):
        srid = _dbref_srid_for_x(x)
        if srid is not None:
            prepared.append((index, float(x), float(y), srid))
    result: list[tuple[float, float] | None] = [None] * len(points)
    if not prepared:
        return result

    placeholders = ",".join(["(%s,%s,%s,%s)"] * len(prepared))
    params: list[Any] = []
    for row in prepared:
        params.extend(row)
    cur.execute(
        f"""
        SELECT ord,
               ST_X(wgs84) AS lon,
               ST_Y(wgs84) AS lat
        FROM (
            SELECT ord,
                   ST_Transform(
                       ST_SetSRID(ST_MakePoint(x_dbref,y_dbref),source_srid),
                       4326
                   ) AS wgs84
            FROM (VALUES {placeholders}) AS v(ord,x_dbref,y_dbref,source_srid)
        ) transformed
        ORDER BY ord
        """,
        tuple(params),
    )
    for row in cur.fetchall():
        lon, lat = _num(row["lon"]), _num(row["lat"])
        if lon is None or lat is None:
            continue
        if not (5.0 <= lon <= 16.5 and 46.5 <= lat <= 56.0):
            continue
        result[int(row["ord"])] = (lon, lat)
    return result


def _postgis_transform_bbox(
    cur,
    bbox: tuple[float, float, float, float] | None,
) -> dict[str, float] | None:
    if bbox is None:
        return None
    min_x, min_y, max_x, max_y = bbox
    transformed = _postgis_transform_points(
        cur,
        [
            (min_x, min_y),
            (min_x, max_y),
            (max_x, min_y),
            (max_x, max_y),
            ((min_x + max_x) / 2.0, (min_y + max_y) / 2.0),
        ],
    )
    corners = [row for row in transformed[:4] if row is not None]
    center = transformed[4]
    if not corners or center is None:
        return None
    return {
        "min_lon": min(row[0] for row in corners),
        "min_lat": min(row[1] for row in corners),
        "max_lon": max(row[0] for row in corners),
        "max_lat": max(row[1] for row in corners),
        "center_lon": center[0],
        "center_lat": center[1],
    }


def _segment_physical_length_m(
    cur,
    segment: dict[str, Any],
    points: list[dict[str, Any]] | None = None,
) -> float:
    """Return the best available physical segment length in metres."""
    start = _num(segment.get("station_from_m"))
    end = _num(segment.get("station_to_m"))
    if start is not None and end is not None and abs(end - start) > 0.0001:
        return abs(end - start)

    rows = points if points is not None else _segment_points(cur, segment)
    total = 0.0
    previous: tuple[float, float] | None = None
    for row in rows:
        x = _num(row.get("x_dbref"))
        y = _num(row.get("y_dbref"))
        if x is None or y is None:
            continue
        current = (x, y)
        if previous is not None:
            total += math.hypot(current[0] - previous[0], current[1] - previous[1])
        previous = current
    return total


def _coverage_interval(segment: dict[str, Any]) -> tuple[float, float] | None:
    start, end = _num(segment.get("station_from_m")), _num(segment.get("station_to_m"))
    if start is None or end is None or start == end:
        return None
    return min(start, end), max(start, end)


def _claim_coverage_interval(covered: list[tuple[float, float]],
                             start: float, end: float) -> float:
    """Claim a station interval, returning only previously unclaimed metres."""
    fresh = end - start
    for left, right in covered:
        fresh -= max(0.0, min(end, right) - max(start, left))
    merged: list[tuple[float, float]] = []
    for left, right in sorted([*covered, (start, end)]):
        if merged and left <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(right, merged[-1][1]))
        else:
            merged.append((left, right))
    covered[:] = merged
    return max(0.0, fresh)


def _coverage_summary_data() -> dict[str, Any]:
    """Global admin summary without WGS84 overview geometry."""
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT a.*
            FROM tb.survey_catalog_axes a
            WHERE a.scope='GLOBAL'
              AND a.status <> 'ARCHIVED'
              AND EXISTS (
                  SELECT 1
                  FROM tb.survey_catalog_segments s
                  WHERE s.axis_id=a.id AND s.segment_status='ACTIVE'
              )
            ORDER BY a.id
            """
        )
        axes = [dict(row) for row in cur.fetchall()]

        station_keys: set[str] = set()
        route_keys: set[str] = set()
        track_count = 0
        switch_count = 0
        wvb_count = 0
        active_segment_count = 0
        quality_counts: dict[str, int] = {}
        lengths = {
            "AUTOMATISCH": 0.0,
            "MANUELL": 0.0,
            "FEHLT": 0.0,
            "UNPLAUSIBEL": 0.0,
            "SONSTIG": 0.0,
        }
        manual_mobile_length_m = 0.0
        total_length_m = 0.0

        for axis in axes:
            station_code = _clean(axis.get("station_code"))
            station_name = _clean(axis.get("station_name"))
            route_number = _clean(axis.get("route_number"))
            axis_type = str(axis.get("axis_type") or "").upper()
            if station_code or station_name:
                station_keys.add(f"{station_code or ''}|{station_name or ''}")
            if route_number:
                route_keys.add(route_number)
            if axis_type == "GLEIS":
                track_count += 1
            elif axis_type == "WEICHE":
                switch_count += 1
            elif axis_type == "WEICHENVERBINDUNG":
                wvb_count += 1

            cur.execute(
                """
                SELECT *
                FROM tb.survey_catalog_segments
                WHERE axis_id=%s AND segment_status='ACTIVE'
                ORDER BY created_at,id
                """,
                (axis["id"],),
            )
            segments = [dict(row) for row in cur.fetchall()]
            active_segment_count += len(segments)
            if not segments:
                continue

            context = _axis_km_context(cur, axis)
            rows: list[dict[str, Any]] = []
            for segment in segments:
                length_m = _segment_physical_length_m(cur, segment)
                quality = _segment_km_quality(cur, axis, segment, context)
                status = str(quality.get("status") or "SONSTIG").upper()
                rows.append(
                    {
                        "segment": segment,
                        "length_m": max(0.0, float(length_m)),
                        "status": status,
                    }
                )
                quality_counts[status] = quality_counts.get(status, 0) + 1

            base_rows = [
                row for row in rows
                if row["segment"].get("source_kind") != "MANUAL_TRA_SECTION"
            ]
            manual_rows = [
                row for row in rows
                if row["segment"].get("source_kind") == "MANUAL_TRA_SECTION"
            ]
            remaining_by_segment: dict[str, float] = {}
            status_by_segment: dict[str, str] = {}
            claimed_by_segment: dict[str, list[tuple[float, float]]] = {}

            for row in base_rows:
                segment = row["segment"]
                key = str(segment["id"])
                length_m = row["length_m"]
                status = row["status"] if row["status"] in lengths else "SONSTIG"
                total_length_m += length_m
                lengths[status] += length_m
                remaining_by_segment[key] = length_m
                status_by_segment[key] = status

            for row in manual_rows:
                segment = row["segment"]
                manual_length = row["length_m"]
                matched_key: str | None = None
                matched_base: dict[str, Any] | None = None
                source_original_id = segment.get("source_original_id")
                if source_original_id is not None:
                    for base in base_rows:
                        if base["segment"].get("source_original_id") == source_original_id:
                            matched_key = str(base["segment"]["id"])
                            matched_base = base["segment"]
                            break

                overlay_length = manual_length
                if matched_key is not None:
                    # An uncalibrated TRA overlay adds neither geometry nor KM.
                    if row["status"] != "MANUELL":
                        continue
                    bounds = _coverage_interval(segment)
                    base_bounds = _coverage_interval(matched_base)
                    if bounds is None or base_bounds is None:
                        continue  # No defensible station overlap to reclassify.
                    start, end = max(bounds[0], base_bounds[0]), min(bounds[1], base_bounds[1])
                    if end <= start:
                        continue
                    overlay_length = _claim_coverage_interval(
                        claimed_by_segment.setdefault(matched_key, []), start, end
                    )
                    available = remaining_by_segment.get(matched_key, 0.0)
                    overlay_length = min(overlay_length, available)
                    previous_status = status_by_segment.get(matched_key, "SONSTIG")
                    lengths[previous_status] = max(
                        0.0, lengths[previous_status] - overlay_length
                    )
                    remaining_by_segment[matched_key] = max(0.0, available - overlay_length)
                else:
                    total_length_m += manual_length

                overlay_status = row["status"] if row["status"] in lengths else "SONSTIG"
                lengths[overlay_status] += overlay_length
                metadata = (
                    segment.get("metadata")
                    if isinstance(segment.get("metadata"), dict)
                    else {}
                )
                if overlay_status == "MANUELL" and str(metadata.get("origin") or "").upper() == "ANDROID":
                    manual_mobile_length_m += overlay_length

        known_length_m = lengths["AUTOMATISCH"] + lengths["MANUELL"]
        without_km_length_m = (
            lengths["FEHLT"] + lengths["UNPLAUSIBEL"] + lengths["SONSTIG"]
        )
        return {
            "axis_count": len(axes),
            "active_segment_count": active_segment_count,
            "station_count": len(station_keys),
            "route_count": len(route_keys),
            "track_count": track_count,
            "switch_count": switch_count,
            "wvb_count": wvb_count,
            "total_length_m": round(total_length_m, 3),
            "known_km_length_m": round(known_length_m, 3),
            "automatic_km_length_m": round(lengths["AUTOMATISCH"], 3),
            "manual_km_length_m": round(lengths["MANUELL"], 3),
            "manual_mobile_length_m": round(manual_mobile_length_m, 3),
            "without_km_length_m": round(without_km_length_m, 3),
            "unplausible_length_m": round(lengths["UNPLAUSIBEL"], 3),
            "quality_counts": quality_counts,
            "length_method": "ACTIVE_SEGMENT_STATION_OR_GEOMETRY",
            "parallel_tracks_count_separately": True,
        }


@router.get("/coverage/summary")
def coverage_summary(
    _: dict[str, Any] = Depends(require_admin),
) -> dict[str, Any]:
    return _coverage_summary_data()


@router.get("/coverage")
def coverage(
    include_overview: bool = Query(default=False),
    include_quality: bool = Query(default=False),
    fast_overview: bool = Query(default=False),
    _: dict[str, Any] = Depends(require_admin),
) -> dict[str, Any]:
    """Read-only Germany-wide summary for the admin survey overview.

    DB_REF coordinates stay untouched in the catalog. PostGIS transforms only
    the returned overview coordinates to WGS84.
    """
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT a.*,
                   (SELECT count(*)
                    FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') AS active_segment_count,
                   (SELECT min(s.bbox_min_x)
                    FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') AS seg_min_x,
                   (SELECT min(s.bbox_min_y)
                    FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') AS seg_min_y,
                   (SELECT max(s.bbox_max_x)
                    FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') AS seg_max_x,
                   (SELECT max(s.bbox_max_y)
                    FROM tb.survey_catalog_segments s
                    WHERE s.axis_id=a.id AND s.segment_status='ACTIVE') AS seg_max_y
            FROM tb.survey_catalog_axes a
            WHERE a.scope='GLOBAL'
              AND a.status <> 'ARCHIVED'
              AND EXISTS (
                  SELECT 1
                  FROM tb.survey_catalog_segments s
                  WHERE s.axis_id=a.id AND s.segment_status='ACTIVE'
              )
            ORDER BY lower(a.station_name) NULLS LAST,
                     a.route_number NULLS LAST,
                     lower(a.display_name),
                     a.id
            """
        )
        axes = [dict(row) for row in cur.fetchall()]
        items: list[dict[str, Any]] = []
        quality_counts: dict[str, int] = {}
        station_keys: set[str] = set()
        route_keys: set[str] = set()
        unsupported = 0

        for axis in axes:
            cur.execute(
                """
                SELECT *
                FROM tb.survey_catalog_segments
                WHERE axis_id=%s AND segment_status='ACTIVE'
                ORDER BY created_at,id
                """,
                (axis["id"],),
            )
            segments = [dict(row) for row in cur.fetchall()]
            if include_quality or (include_overview and not fast_overview):
                quality = _axis_km_summary(cur, axis, segments)
                status = str(quality.get("status") or "UNBEKANNT")
                quality_counts[status] = quality_counts.get(status, 0) + 1
            else:
                quality = {
                    "status": "DETAIL_BEI_AUSWAHL",
                    "color": "GRAU",
                    "data_kinds": [],
                    "segments": [],
                }
                status = "DETAIL_BEI_AUSWAHL"

            # Default coverage stays light. Simplified lines are optional;
            # complete geometry is always loaded only after an axis selection.
            overview_segments: list[dict[str, Any]] = []
            if include_overview:
                segment_quality = {
                    str(row.get("segment_id")): row
                    for row in (quality.get("segments") or [])
                    if row.get("segment_id")
                }
                for segment in segments:
                    full_points = _segment_points(cur, segment)
                    simplified = _decimate(full_points, 28)
                    xy: list[tuple[float, float]] = []
                    for point in simplified:
                        x = _num(point.get("x_dbref"))
                        y = _num(point.get("y_dbref"))
                        if x is not None and y is not None:
                            xy.append((x, y))
                    transformed = _postgis_transform_points(cur, xy)
                    coords = [
                        [round(point[0], 7), round(point[1], 7)]
                        for point in transformed
                        if point is not None
                    ]
                    if len(coords) >= 2:
                        overview_segments.append(
                            {
                                "segment_id": str(segment["id"]),
                                "coordinates": coords,
                                "km_quality": _plain(
                                    segment_quality.get(str(segment["id"]))
                                    or {"status": status, "color": quality.get("color")}
                                ),
                            }
                        )

            bbox_values = (
                _num(axis.get("seg_min_x")),
                _num(axis.get("seg_min_y")),
                _num(axis.get("seg_max_x")),
                _num(axis.get("seg_max_y")),
            )
            bbox = (
                tuple(float(value) for value in bbox_values)
                if all(value is not None for value in bbox_values)
                else None
            )
            wgs84 = _postgis_transform_bbox(cur, bbox)
            if wgs84 is None:
                unsupported += 1

            station_code = _clean(axis.get("station_code"))
            station_name = _clean(axis.get("station_name"))
            route_number = _clean(axis.get("route_number"))
            if station_code or station_name:
                station_keys.add(f"{station_code or ''}|{station_name or ''}")
            if route_number:
                route_keys.add(route_number)

            items.append(
                {
                    "axis_id": str(axis["id"]),
                    "display_name": axis.get("display_name"),
                    "route_number": route_number,
                    "station_code": station_code,
                    "station_name": station_name,
                    "track_key": _clean(axis.get("track_key")),
                    "object_key": _clean(axis.get("object_key")),
                    "axis_type": axis.get("axis_type"),
                    "active_segment_count": int(axis.get("active_segment_count") or 0),
                    "km_quality": _plain(quality),
                    "wgs84": wgs84,
                    "overview_segments": overview_segments,
                }
            )

    return {
        "crs": "EPSG:4326",
        "source_crs": "DB_REF / 3-degree Gauss-Kruger zones 2-5",
        "axis_count": len(items),
        "station_count": len(station_keys),
        "route_count": len(route_keys),
        "unsupported_axis_count": unsupported,
        "quality_counts": quality_counts,
        "overview_included": include_overview,
        "quality_included": include_quality or (include_overview and not fast_overview),
        "axes": items,
    }


class AxisStatusUpdate(BaseModel):
    base_version: int = Field(ge=1)


@router.get("/management/axes")
def management_axes(
    archived: bool = Query(default=True),
    q: str = Query(default="", max_length=160),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
    _: dict[str, Any] = Depends(require_admin),
) -> dict[str, Any]:
    query = q.strip()
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT a.id,a.display_name,a.station_code,a.station_name,a.route_number,
                   a.track_key,a.object_key,a.direction,a.axis_type,a.status,a.current_version,
                   (SELECT count(*) FROM tb.survey_catalog_segments s WHERE s.axis_id=a.id) segment_count
            FROM tb.survey_catalog_axes a
            WHERE a.scope='GLOBAL' AND (a.status='ARCHIVED')=%s
              AND (%s='' OR concat_ws(' ',a.display_name,a.station_code,a.station_name,
                   a.route_number,a.track_key,a.object_key) ILIKE %s)
            ORDER BY lower(a.display_name),a.id LIMIT %s OFFSET %s
            """, (archived,query,f"%{query}%",limit+1,offset),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {"axes":_plain(rows[:limit]),"has_more":len(rows)>limit,"offset":offset,"limit":limit}


def _management_axis(cur, axis_id: UUID, base_version: int) -> dict[str, Any]:
    cur.execute("SELECT * FROM tb.survey_catalog_axes WHERE id=%s AND scope='GLOBAL' FOR UPDATE", (axis_id,))
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "SURVEY_COVERAGE_AXIS_NOT_FOUND")
    axis = dict(row)
    if int(axis["current_version"]) != base_version:
        raise HTTPException(409, {"code":"VERSION_CONFLICT","current_version":axis["current_version"]})
    return axis


@router.patch("/management/axes/{axis_id}")
def management_update_axis(axis_id: UUID, payload: AxisAdminUpdate,
                           user: dict[str, Any] = Depends(require_admin_csrf)) -> dict[str, Any]:
    changes = payload.model_dump(exclude={"base_version"}, exclude_unset=True)
    if not changes:
        raise HTTPException(400, "NO_CHANGES")
    try:
        with transaction() as conn, conn.cursor() as cur:
            axis = _management_axis(cur, axis_id, payload.base_version)
            if axis["status"] == "ARCHIVED":
                raise HTTPException(409, "SURVEY_AXIS_ARCHIVED")
            fields = {key:_clean(changes.get(key,axis.get(key))) for key in (
                "display_name","station_code","station_name","route_number","track_key",
                "object_key","direction","axis_type")}
            for key in ("station_code","object_key","axis_type"):
                if fields[key]:
                    fields[key] = fields[key].upper()
            if not fields["display_name"]:
                raise HTTPException(400, "SURVEY_AXIS_NAME_REQUIRED")
            if fields["axis_type"] not in _AXIS_TYPES:
                raise HTTPException(400, "SURVEY_AXIS_TYPE_INVALID")
            identity_fields = ("station_code","route_number","track_key","object_key","direction","axis_type")
            identity = axis["identity_key"]
            if any(fields[key] != axis.get(key) for key in identity_fields):
                identity = _identity_key(axis["scope"],axis.get("company_id"),fields,str(axis_id))
            cur.execute("SELECT id FROM tb.survey_catalog_axes WHERE identity_key=%s AND id<>%s", (identity,axis_id))
            if cur.fetchone():
                raise HTTPException(409, "SURVEY_AXIS_IDENTITY_EXISTS")
            version = int(axis["current_version"]) + 1
            cur.execute(
                """UPDATE tb.survey_catalog_axes
                SET display_name=%s,station_code=%s,station_name=%s,route_number=%s,track_key=%s,
                    object_key=%s,direction=%s,axis_type=%s,identity_key=%s,current_version=%s
                WHERE id=%s AND current_version=%s RETURNING *""",
                tuple(fields[key] for key in ("display_name","station_code","station_name","route_number",
                    "track_key","object_key","direction","axis_type"))+(identity,version,axis_id,payload.base_version),
            )
            updated = cur.fetchone()
            if not updated:
                raise HTTPException(409, "VERSION_CONFLICT")
            updated = dict(updated)
            _history(cur,entity_type="AXIS",entity_id=axis_id,version=version,operation="UPDATE",
                     before=axis,after=updated,actor_user_id=user["user_id"],actor_company_id=axis.get("company_id"))
            audit(cur,action="SURVEY_CATALOG_AXIS_UPDATE",actor_user_id=user["user_id"],
                  company_id=user.get("company_id"),entity_type="SURVEY_CATALOG_AXIS",entity_id=axis_id,
                  details={"version":version,"fields":list(changes)})
            return _plain(updated)
    except UniqueViolation as exc:
        raise HTTPException(409, "SURVEY_AXIS_IDENTITY_EXISTS") from exc


def _management_axis_status(axis_id: UUID, payload: AxisStatusUpdate,
                            user: dict[str, Any], archive: bool) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        axis = _management_axis(cur,axis_id,payload.base_version)
        if (axis["status"] == "ARCHIVED") == archive:
            return _plain(axis)
        metadata = dict(axis.get("metadata") or {})
        if archive:
            metadata["admin_archive_previous_status"] = axis["status"]
            status = "ARCHIVED"
        else:
            cur.execute("SELECT count(*) n FROM tb.survey_catalog_conflicts WHERE axis_id=%s AND status='OPEN'", (axis_id,))
            needs_review = int(cur.fetchone()["n"]) > 0 or metadata.get("admin_archive_previous_status") == "REVIEW"
            status = "REVIEW" if needs_review else "ACTIVE"
            metadata.pop("admin_archive_previous_status",None)
        version = int(axis["current_version"]) + 1
        cur.execute("""UPDATE tb.survey_catalog_axes SET status=%s,metadata=%s,current_version=%s
                       WHERE id=%s AND current_version=%s RETURNING *""",
                    (status,Jsonb(metadata),version,axis_id,payload.base_version))
        row = cur.fetchone()
        if not row:
            raise HTTPException(409, "VERSION_CONFLICT")
        updated = dict(row)
        _history(cur,entity_type="AXIS",entity_id=axis_id,version=version,operation="STATUS",
                 before=axis,after=updated,actor_user_id=user["user_id"],actor_company_id=axis.get("company_id"))
        audit(cur,action="SURVEY_CATALOG_AXIS_ARCHIVE" if archive else "SURVEY_CATALOG_AXIS_RESTORE",
              actor_user_id=user["user_id"],company_id=user.get("company_id"),
              entity_type="SURVEY_CATALOG_AXIS",entity_id=axis_id,details={"version":version})
        return _plain(updated)


@router.post("/management/axes/{axis_id}/archive")
def management_archive_axis(axis_id: UUID,payload: AxisStatusUpdate,
                            user: dict[str, Any] = Depends(require_admin_csrf)) -> dict[str, Any]:
    return _management_axis_status(axis_id,payload,user,True)


@router.post("/management/axes/{axis_id}/restore")
def management_restore_axis(axis_id: UUID,payload: AxisStatusUpdate,
                            user: dict[str, Any] = Depends(require_admin_csrf)) -> dict[str, Any]:
    return _management_axis_status(axis_id,payload,user,False)


class AdminKmSection(BaseModel):
    base_version: int = Field(ge=1)
    source_segment_id: UUID
    source_version: int = Field(ge=1)
    station_from_m: float = Field(allow_inf_nan=False)
    station_to_m: float = Field(allow_inf_nan=False)
    km_from_m: float = Field(allow_inf_nan=False)
    km_to_m: float = Field(allow_inf_nan=False)
    segment_id: UUID | None = None
    segment_version: int | None = Field(default=None, ge=1)
    note: str = Field(default='', max_length=1000)
    client_change_id: str = Field(min_length=1, max_length=160)
    confirm_length_difference: bool = False


class AdminKmDeactivate(BaseModel):
    base_version: int = Field(ge=1)
    segment_version: int = Field(ge=1)
    client_change_id: str = Field(min_length=1, max_length=160)


def _km_editor_replay(cur, user, change_id, fingerprint):
    replay = _receipt(cur, user, change_id)
    if replay is not None and replay.get('request_fingerprint') != fingerprint:
        raise HTTPException(409, 'SURVEY_KM_MUTATION_REUSED')
    return replay


def _km_editor_fingerprint(axis_id, payload, action):
    import hashlib
    import json
    value = [str(axis_id), action, payload.model_dump(mode='json')]
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def _km_editor_rows(cur, axis_id):
    cur.execute('SELECT * FROM tb.survey_catalog_segments WHERE axis_id=%s ORDER BY id FOR UPDATE', (axis_id,))
    return [dict(row) for row in cur.fetchall()]


def _km_editor_validate(payload, rows):
    source = next((s for s in rows if s['id'] == payload.source_segment_id), None)
    if not source or source['source_kind'] != 'ORIGINAL_TRA' or source['segment_status'] != 'ACTIVE':
        raise HTTPException(400, 'SURVEY_KM_SOURCE_NOT_ACTIVE')
    if int(source['current_version']) != payload.source_version:
        raise HTTPException(409, 'VERSION_CONFLICT')
    bounds = _coverage_interval(source)
    start, end = payload.station_from_m, payload.station_to_m
    if (not all(math.isfinite(v) for v in (start, end, payload.km_from_m, payload.km_to_m))
            or not bounds or end <= start or start < bounds[0] or end > bounds[1]):
        raise HTTPException(400, 'SURVEY_KM_SECTION_OUTSIDE_SOURCE')
    if payload.km_from_m == payload.km_to_m:
        raise HTTPException(400, 'SURVEY_KM_VALUES_IDENTICAL')
    existing = None
    if payload.segment_id is not None:
        existing = next((s for s in rows if s['id'] == payload.segment_id), None)
        if (not existing or existing['source_kind'] != 'MANUAL_TRA_SECTION'
                or existing['source_original_id'] != source['source_original_id']
                or existing['segment_status'] != 'ACTIVE'):
            raise HTTPException(400, 'SURVEY_KM_SECTION_NOT_EDITABLE')
        if int(existing['current_version']) != payload.segment_version:
            raise HTTPException(409, 'VERSION_CONFLICT')
    def at(s, pos):
        a, b = float(s['station_from_m']), float(s['station_to_m'])
        return float(s['km_from_m']) + (pos-a)/(b-a)*(float(s['km_to_m'])-float(s['km_from_m']))
    candidate = payload.model_dump()
    for other in rows:
        if (other['id'] == payload.segment_id or other['source_kind'] != 'MANUAL_TRA_SECTION'
                or other['segment_status'] != 'ACTIVE'
                or other['source_original_id'] != source['source_original_id']):
            continue
        span = _coverage_interval(other)
        if not span or max(start,span[0]) > min(end,span[1]):
            continue
        # Incomplete existing anchors cannot be silently reconciled.
        if any(_num(other.get(k)) is None for k in ('km_from_m','km_to_m')):
            raise HTTPException(409, 'SURVEY_KM_OVERLAP_CONFLICT')
        if any(abs(at(candidate,p)-at(other,p)) > 0.01 for p in (max(start,span[0]),min(end,span[1]))):
            raise HTTPException(409, 'SURVEY_KM_OVERLAP_CONFLICT')
    length = end-start
    km_span = abs(payload.km_to_m-payload.km_from_m)
    warning = abs(length-km_span) > max(5.0, length*0.05)
    return source, existing, {'length_m':length, 'km_difference_m':km_span,
                              'length_warning':warning, 'direction':'FALLEND' if payload.km_to_m < payload.km_from_m else 'STEIGEND'}


def _km_editor_prepare(cur, axis_id, payload):
    axis = _management_axis(cur, axis_id, payload.base_version)
    if axis['status'] == 'ARCHIVED':
        raise HTTPException(409, 'SURVEY_AXIS_ARCHIVED')
    source, existing, info = _km_editor_validate(payload, _km_editor_rows(cur, axis_id))
    points = _clip_station_points(_segment_points(cur, source), payload.station_from_m, payload.station_to_m)
    if len(points) < 2:
        raise HTTPException(400, 'SURVEY_KM_GEOMETRY_INVALID')
    return axis, source, existing, points, info


@router.post('/management/axes/{axis_id}/manual-km/preview')
def management_preview_km(axis_id: UUID, payload: AdminKmSection,
                          user: dict[str, Any] = Depends(require_admin_csrf)) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        _, source, _, points, info = _km_editor_prepare(cur, axis_id, payload)
    return {**info, 'source_name':str(source.get('source_signature') or ''),
            'points': [[p['x_dbref'],p['y_dbref']] for p in _decimate(points,300)]}


@router.post('/management/axes/{axis_id}/manual-km')
def management_save_km(axis_id: UUID, payload: AdminKmSection,
                       user: dict[str, Any] = Depends(require_admin_csrf)) -> dict[str, Any]:
    fingerprint = _km_editor_fingerprint(axis_id, payload, 'SAVE')
    try:
        with transaction() as conn, conn.cursor() as cur:
            # Serialize repeated admin writes on this axis before receipt lookup.
            _row(cur, 'survey_catalog_axes', axis_id)
            replay = _km_editor_replay(cur,user,payload.client_change_id,fingerprint)
            if replay is not None:
                return replay
            axis, source, existing, points, info = _km_editor_prepare(cur,axis_id,payload)
            if info['length_warning'] and not payload.confirm_length_difference:
                raise HTTPException(400, 'SURVEY_KM_LENGTH_CONFIRM_REQUIRED')
            metadata = dict((existing or {}).get('metadata') or {})
            metadata.setdefault('origin', 'ADMIN')
            metadata.update(last_edited_via='ADMIN', note=payload.note.strip(), km_source='MANUELL_ABSCHNITT',
                            length_difference_confirmed=payload.confirm_length_difference,
                            source_segment_id=str(source['id']))
            if existing is None:
                segment = _segment_insert(cur,axis=axis,asset_id=None,source_kind='MANUAL_TRA_SECTION',
                    source_original_id=source['source_original_id'],source_signature=source.get('source_signature'),
                    source_date=None,station_from_m=payload.station_from_m,station_to_m=payload.station_to_m,
                    km_from_m=payload.km_from_m,km_to_m=payload.km_to_m,points=points,
                    relation='OVERLAP_CONFIRMED',diagnostics={'reason':'ADMIN_SOURCE_SECTION'},
                    actor_user_id=user['user_id'],metadata=metadata)
            else:
                bbox = _bbox(points)
                cur.execute('''UPDATE tb.survey_catalog_segments
                    SET station_from_m=%s,station_to_m=%s,km_from_m=%s,km_to_m=%s,
                        bbox_min_x=%s,bbox_min_y=%s,bbox_max_x=%s,bbox_max_y=%s,
                        geometry=NULL,metadata=%s,current_version=current_version+1
                    WHERE id=%s AND current_version=%s RETURNING *''',
                    (payload.station_from_m,payload.station_to_m,payload.km_from_m,payload.km_to_m,
                     *bbox,Jsonb(metadata),existing['id'],payload.segment_version))
                row = cur.fetchone()
                if not row:
                    raise HTTPException(409,'VERSION_CONFLICT')
                segment = dict(row)
                _history(cur,entity_type='SEGMENT',entity_id=segment['id'],version=segment['current_version'],
                    operation='UPDATE',before=existing,after=segment,actor_user_id=user['user_id'],actor_company_id=None)
            _axis_extent_refresh(cur,axis_id,user['user_id'])
            response = {'axis_id':str(axis_id),'segment_id':str(segment['id']),
                        'entity_version':int(segment['current_version']),'status':'ACTIVE',
                        'request_fingerprint':fingerprint}
            _save_receipt(cur,user,payload.client_change_id,segment['id'],int(segment['current_version']),response)
            audit(cur,action='SURVEY_CATALOG_ADMIN_KM_SAVE',actor_user_id=user['user_id'],
                  entity_type='SURVEY_CATALOG_SEGMENT',entity_id=segment['id'],details={'axis_id':str(axis_id)})
            return response
    except UniqueViolation as exc:
        raise HTTPException(409,'SURVEY_KM_MUTATION_REUSED') from exc


@router.post('/management/axes/{axis_id}/manual-km/{segment_id}/deactivate')
def management_deactivate_km(axis_id: UUID, segment_id: UUID, payload: AdminKmDeactivate,
                             user: dict[str, Any] = Depends(require_admin_csrf)) -> dict[str, Any]:
    fingerprint = _km_editor_fingerprint(axis_id,payload,'DEACTIVATE:'+str(segment_id))
    try:
        with transaction() as conn, conn.cursor() as cur:
            _row(cur,'survey_catalog_axes',axis_id)
            replay = _km_editor_replay(cur,user,payload.client_change_id,fingerprint)
            if replay is not None:
                return replay
            axis = _management_axis(cur,axis_id,payload.base_version)
            if axis['status'] == 'ARCHIVED':
                raise HTTPException(409,'SURVEY_AXIS_ARCHIVED')
            segment = next((s for s in _km_editor_rows(cur,axis_id) if s['id']==segment_id),None)
            if not segment or segment['source_kind'] != 'MANUAL_TRA_SECTION' or segment['segment_status'] != 'ACTIVE':
                raise HTTPException(400,'SURVEY_KM_SECTION_NOT_EDITABLE')
            if int(segment['current_version']) != payload.segment_version:
                raise HTTPException(409,'VERSION_CONFLICT')
            segment = _mark_segment_status(cur,segment,'INACTIVE',user['user_id'])
            _axis_extent_refresh(cur,axis_id,user['user_id'])
            response = {'axis_id':str(axis_id),'segment_id':str(segment_id),'status':'INACTIVE',
                        'entity_version':int(segment['current_version']),'request_fingerprint':fingerprint}
            _save_receipt(cur,user,payload.client_change_id,segment_id,int(segment['current_version']),response)
            audit(cur,action='SURVEY_CATALOG_ADMIN_KM_DEACTIVATE',actor_user_id=user['user_id'],
                  entity_type='SURVEY_CATALOG_SEGMENT',entity_id=segment_id,details={'axis_id':str(axis_id)})
            return response
    except UniqueViolation as exc:
        raise HTTPException(409,'SURVEY_KM_MUTATION_REUSED') from exc


@router.get("/coverage/{axis_id}/details")
def coverage_axis_details(
    axis_id: UUID,
    history_before: int | None = Query(default=None, ge=1),
    _: dict[str, Any] = Depends(require_admin),
) -> dict[str, Any]:
    """Small, read-only admin detail payload; no geometry/parse files loaded."""
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id,display_name,route_number,station_code,station_name,
                   track_key,object_key,axis_type,direction,status,current_version,
                   created_at,updated_at
            FROM tb.survey_catalog_axes
            WHERE id=%s AND scope='GLOBAL'
            """, (axis_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "SURVEY_COVERAGE_AXIS_NOT_FOUND")
        axis = dict(row)
        cur.execute(
            """
            SELECT s.id,s.source_kind,s.segment_status,s.merge_status,
                   s.station_from_m,s.station_to_m,s.km_from_m,s.km_to_m,
                   s.source_original_id,s.current_version,s.updated_at,
                   s.metadata->>'note' AS note,
                   o.original_name,o.data_kind,o.byte_size,o.sha256
            FROM tb.survey_catalog_segments s
            LEFT JOIN tb.survey_originals o ON o.id=s.source_original_id
            WHERE s.axis_id=%s
            ORDER BY (s.segment_status='ACTIVE') DESC,s.created_at,s.id
            """, (axis_id,),
        )
        segments = [dict(row) for row in cur.fetchall()]
        cur.execute(
            """
            WITH entities AS (
                SELECT 'AXIS'::text AS kind,%s::uuid AS id
                UNION
                SELECT 'SEGMENT',id FROM tb.survey_catalog_segments WHERE axis_id=%s
                UNION
                SELECT 'ASSET',asset_id FROM tb.survey_catalog_segments
                    WHERE axis_id=%s AND asset_id IS NOT NULL
                UNION
                SELECT 'CONFLICT',id FROM tb.survey_catalog_conflicts WHERE axis_id=%s
            )
            SELECT h.change_id,h.entity_type,h.entity_id,h.entity_version,
                   h.operation,h.changed_at,h.before_data,h.after_data
            FROM entities e
            JOIN tb.survey_catalog_history h ON h.entity_type=e.kind AND h.entity_id=e.id
            WHERE (%s::bigint IS NULL OR h.change_id<%s)
            ORDER BY h.change_id DESC LIMIT 51
            """, (axis_id,axis_id,axis_id,axis_id,history_before,history_before),
        )
        raw_history = [dict(row) for row in cur.fetchall()]
        # Never send geometry, raw payloads, storage paths or user metadata to
        # this display. A small field diff is enough for a readable history.
        fields = ("display_name","station_code","station_name","route_number",
                  "track_key","object_key","direction","status","segment_status",
                  "merge_status","km_from_m","km_to_m","station_from_m",
                  "station_to_m","resolution","resolution_note")
        history = []
        for row in raw_history[:50]:
            before = row.pop("before_data") or {}
            after = row.pop("after_data") or {}
            row["fields"] = [
                {"field": key, "before": before.get(key), "after": after.get(key)}
                for key in fields if before.get(key) != after.get(key)
            ]
            history.append(row)
        cur.execute(
            """
            SELECT id,conflict_type,status,created_at
            FROM tb.survey_catalog_conflicts
            WHERE axis_id=%s AND status='OPEN' ORDER BY created_at,id
            """, (axis_id,),
        )
        conflicts = [dict(row) for row in cur.fetchall()]
    return _plain({
        "axis": axis, "segments": segments, "history": history,
        "history_next_before": history[-1]["change_id"] if len(raw_history)>50 else None,
        "conflicts": conflicts,
    })


@router.get("/coverage/{axis_id}/geometry")
def coverage_axis_geometry(
    axis_id: UUID,
    max_points_per_segment: int = Query(default=320, ge=40, le=1500),
    _: dict[str, Any] = Depends(require_admin),
) -> dict[str, Any]:
    """Return one selected GLOBAL axis as WGS84 detail geometry."""
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT *
            FROM tb.survey_catalog_axes
            WHERE id=%s AND scope='GLOBAL' AND status <> 'ARCHIVED'
            """,
            (axis_id,),
        )
        found = cur.fetchone()
        if not found:
            raise HTTPException(404, "SURVEY_COVERAGE_AXIS_NOT_FOUND")
        axis = dict(found)

        cur.execute(
            """
            SELECT *
            FROM tb.survey_catalog_segments
            WHERE axis_id=%s AND segment_status='ACTIVE'
            ORDER BY created_at,id
            """,
            (axis_id,),
        )
        segments = [dict(row) for row in cur.fetchall()]
        context = _axis_km_context(cur, axis)
        result: list[dict[str, Any]] = []
        unsupported_points = 0

        for segment in segments:
            full_points = _segment_points(cur, segment)
            decimated = _decimate(full_points, max_points_per_segment)
            xy: list[tuple[float, float]] = []
            for point in decimated:
                x, y = _num(point.get("x_dbref")), _num(point.get("y_dbref"))
                if x is not None and y is not None:
                    xy.append((x, y))
            transformed = _postgis_transform_points(cur, xy)
            coords = [
                [round(row[0], 7), round(row[1], 7)]
                for row in transformed
                if row is not None
            ]
            unsupported_points += len(transformed) - len(coords)
            if len(coords) < 2:
                continue
            result.append(
                {
                    "segment_id": str(segment["id"]),
                    "source_kind": segment.get("source_kind"),
                    "km_quality": _plain(
                        _segment_km_quality(cur, axis, segment, context, full_points)
                    ),
                    "coordinates": coords,
                }
            )

    return {
        "axis": {
            "axis_id": str(axis["id"]),
            "display_name": axis.get("display_name"),
            "route_number": _clean(axis.get("route_number")),
            "station_code": _clean(axis.get("station_code")),
            "station_name": _clean(axis.get("station_name")),
            "track_key": _clean(axis.get("track_key")),
            "object_key": _clean(axis.get("object_key")),
            "axis_type": axis.get("axis_type"),
        },
        "segments": result,
        "segment_count": len(result),
        "unsupported_point_count": unsupported_points,
        "crs": "EPSG:4326",
    }


@router.get("/geometry")
def geometry(route_number: str | None = Query(default=None,max_length=80),
             station_code: str | None = Query(default=None,max_length=50),
             min_x: float | None = None,min_y: float | None = None,
             max_x: float | None = None,max_y: float | None = None,
             max_points_per_segment: int = Query(default=1500,ge=50,le=5000),
             segment_limit: int = Query(default=200,ge=1,le=500),
             user: dict[str,Any] = Depends(current_portal_user)) -> dict[str,Any]:
    route, station = _clean(route_number), _clean(station_code)
    visible, params = _visible_sql(user,"a")
    where = [visible,"a.status <> 'ARCHIVED'","s.segment_status='ACTIVE'"]
    if route:
        where.append("a.route_number=%s"); params.append(route)
    if station:
        where.append("a.station_code=%s"); params.append(station.upper())
    if None not in (min_x,min_y,max_x,max_y):
        where.extend(["s.bbox_max_x >= %s","s.bbox_min_x <= %s",
                      "s.bbox_max_y >= %s","s.bbox_min_y <= %s"])
        params.extend([min_x,max_x,min_y,max_y])
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT s.*,a.scope,a.company_id,a.route_number,a.station_code,a.station_name,a.track_key,a.object_key,
                   a.direction,a.axis_type,a.display_name,a.current_version axis_version
            FROM tb.survey_catalog_segments s
            JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
            WHERE {' AND '.join(where)}
            ORDER BY a.display_name,s.created_at,s.id
            LIMIT %s
            """,
            tuple(params+[segment_limit]),
        )
        rows = [dict(row) for row in cur.fetchall()]
        result = []
        context_cache: dict[str, dict[str, Any]] = {}
        for row in rows:
            axis_key = str(row["axis_id"])
            axis = {
                "id": row["axis_id"],
                "scope": row["scope"],
                "company_id": row["company_id"],
                "route_number": row.get("route_number"),
                "station_code": row.get("station_code"),
                "station_name": row.get("station_name"),
                "track_key": row.get("track_key"),
                "object_key": row.get("object_key"),
                "direction": row.get("direction"),
                "axis_type": row.get("axis_type"),
                "display_name": row.get("display_name"),
            }
            if axis_key not in context_cache:
                context_cache[axis_key] = _axis_km_context(cur, axis)
            full_points = _segment_points(cur,row)
            item = _plain(row)
            item["km_quality"] = _plain(
                _segment_km_quality(cur, axis, row, context_cache[axis_key], full_points)
            )
            item["points"] = _plain(_decimate(full_points,max_points_per_segment))
            item.pop("geometry",None)
            result.append(_catalog_response(user, item))
    return {"segments":result,"count":len(result)}


@router.get("/changes")
def changes(after: int = Query(default=0,ge=0),
            limit: int = Query(default=200,ge=1,le=1000),
            user: dict[str,Any] = Depends(current_portal_user)) -> dict[str,Any]:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT change_id,entity_type,entity_id,entity_version,operation,after_data,changed_at
            FROM tb.survey_catalog_history
            WHERE change_id>%s ORDER BY change_id LIMIT %s
            """,
            (after,limit*4),
        )
        raw = [dict(row) for row in cur.fetchall()]
        visible_changes = []
        next_after = after
        for row in raw:
            next_after = int(row["change_id"])
            data = row.get("after_data") or {}
            scope = data.get("scope")
            company_id = data.get("company_id") or data.get("created_by_company_id")
            if user.get("company_role") != "ADMIN":
                if scope == _SCOPE_COMPANY and str(company_id or "") != str(user.get("company_id") or ""):
                    continue
                if row["entity_type"] in {"SEGMENT","CONFLICT"}:
                    table = "survey_catalog_segments" if row["entity_type"] == "SEGMENT" else "survey_catalog_conflicts"
                    cur.execute(
                        f"""
                        SELECT a.scope,a.company_id
                        FROM tb.{table} s
                        JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
                        WHERE s.id=%s
                        """,
                        (row["entity_id"],),
                    )
                    vis = cur.fetchone()
                    if vis and vis["scope"] == _SCOPE_COMPANY and vis["company_id"] != user.get("company_id"):
                        continue
            visible_changes.append(_catalog_response(user, row))
            if len(visible_changes) >= limit:
                break
        cur.execute("SELECT coalesce(max(change_id),0) latest FROM tb.survey_catalog_history")
        latest = int(cur.fetchone()["latest"])
    return {
        "after":after,
        "next_after":next_after,
        "latest":latest,
        "has_more":next_after < latest,
        "changes":visible_changes,
    }



_CONFLICT_EXPLANATIONS = {
    "NEARBY_GEOMETRY_NOT_MATCHING": (
        "Geometrien liegen nahe beieinander, passen aber nicht ausreichend zusammen.",
        "Der Server hat eine vorhandene Achse in der Nähe gefunden. Überlappung oder Anschluss reichen nicht für eine automatische Verbindung."
    ),
    "ENDPOINT_HEADING_MISMATCH": (
        "Endpunkte liegen nahe zusammen, aber die Richtungen passen nicht.",
        "Eine automatische Fortsetzung wäre geometrisch unsicher. Bitte beide Achsen im Detail vergleichen."
    ),
    "MULTIPLE_AXIS_CANDIDATES": (
        "Mehrere vorhandene Achsen kommen als Zuordnung infrage.",
        "Der Server kann nicht eindeutig entscheiden, zu welcher Achse die neue Quelle gehört."
    ),
    "GEOMETRY_TOO_SHORT": (
        "Die Geometrie ist für einen sicheren Vergleich zu kurz.",
        "Es liegen zu wenige Geometriepunkte für eine automatische Entscheidung vor."
    ),
    "GEOMETRY_CONFLICT": (
        "Die neue Geometrie konnte nicht automatisch zugeordnet werden.",
        "Bitte Quellen und Geometrien vergleichen und die fachliche Zuordnung festlegen."
    ),
}


def _conflict_explanation(conflict_type: str) -> dict[str, str]:
    title, text = _CONFLICT_EXPLANATIONS.get(
        str(conflict_type or "GEOMETRY_CONFLICT").upper(),
        _CONFLICT_EXPLANATIONS["GEOMETRY_CONFLICT"],
    )
    return {"title": title, "text": text}


def _conflict_segment_detail(cur, segment: dict[str, Any] | None) -> dict[str, Any] | None:
    if segment is None:
        return None
    source_name = None
    original_id = segment.get("source_original_id")
    if original_id is not None:
        cur.execute("SELECT original_name FROM tb.survey_originals WHERE id=%s", (original_id,))
        source = cur.fetchone()
        if source:
            source_name = source["original_name"]
    points = _segment_points(cur, segment)
    item = _plain(segment)
    item["source_name"] = source_name or (segment.get("metadata") or {}).get("original_name")
    item["points"] = _plain(_decimate(points, 1800))
    item["point_count"] = len(points)
    item.pop("geometry", None)
    return item


@router.get("/conflicts")
def list_conflicts(status: str = Query(default="OPEN",max_length=20),
                   limit: int = Query(default=200,ge=1,le=1000),
                   _: dict[str,Any] = Depends(require_admin)) -> dict[str,Any]:
    normalized = status.strip().upper()
    if normalized not in {"OPEN","RESOLVED"}:
        raise HTTPException(400, "SURVEY_CONFLICT_STATUS_INVALID")
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT c.*,a.route_number,a.station_code,a.station_name,a.track_key,a.object_key,
                   a.direction,a.axis_type,a.display_name
            FROM tb.survey_catalog_conflicts c
            JOIN tb.survey_catalog_axes a ON a.id=c.axis_id
            WHERE c.status=%s ORDER BY c.created_at DESC LIMIT %s
            """,
            (normalized,limit),
        )
        rows = [_plain(dict(row)) for row in cur.fetchall()]
    return {"conflicts":rows,"count":len(rows)}



@router.get("/conflicts/{conflict_id}")
def conflict_detail(conflict_id: UUID,
                    _: dict[str,Any] = Depends(require_admin)) -> dict[str,Any]:
    with transaction() as conn, conn.cursor() as cur:
        conflict = _row(cur, "survey_catalog_conflicts", conflict_id)
        axis = _row(cur, "survey_catalog_axes", conflict["axis_id"])
        candidate = _row(cur, "survey_catalog_segments", conflict["candidate_segment_id"])
        existing = (
            _row(cur, "survey_catalog_segments", conflict["existing_segment_id"])
            if conflict.get("existing_segment_id") else None
        )
        return {
            "conflict": _plain(conflict),
            "explanation": _conflict_explanation(conflict["conflict_type"]),
            "axis": _plain(axis),
            "candidate": _conflict_segment_detail(cur, candidate),
            "existing": _conflict_segment_detail(cur, existing),
        }


@router.post("/conflicts/{conflict_id}/resolve")
def resolve_conflict(conflict_id: UUID,payload: ConflictResolution,
                     user: dict[str,Any] = Depends(require_admin_csrf)) -> dict[str,Any]:
    resolution = payload.resolution.strip().upper()
    if resolution not in _CONFLICT_RESOLUTIONS:
        raise HTTPException(400, "SURVEY_CONFLICT_RESOLUTION_INVALID")
    with transaction() as conn, conn.cursor() as cur:
        conflict = _row(cur,"survey_catalog_conflicts",conflict_id)
        if conflict["status"] != "OPEN":
            raise HTTPException(409, "SURVEY_CONFLICT_ALREADY_RESOLVED")
        candidate = _row(cur,"survey_catalog_segments",conflict["candidate_segment_id"])
        existing = _row(cur,"survey_catalog_segments",conflict["existing_segment_id"]) if conflict.get("existing_segment_id") else None
        if resolution == "KEEP_EXISTING":
            _mark_segment_status(cur,candidate,"INACTIVE",user["user_id"])
        elif resolution == "ACCEPT_CANDIDATE":
            _mark_segment_status(cur,candidate,"ACTIVE",user["user_id"])
            if existing is not None:
                _mark_segment_status(cur,existing,"SUPERSEDED",user["user_id"])
        elif resolution in {"KEEP_BOTH","CONNECT"}:
            candidate = _mark_segment_status(cur,candidate,"ACTIVE",user["user_id"])
            if resolution == "CONNECT":
                meta = dict(candidate.get("metadata") or {})
                meta["admin_connect"] = {
                    "existing_segment_id":str(existing["id"]) if existing else None,
                    "resolved_by":str(user["user_id"]),
                }
                cur.execute("UPDATE tb.survey_catalog_segments SET metadata=%s WHERE id=%s",
                            (Jsonb(meta),candidate["id"]))
        before = dict(conflict)
        version = int(conflict["current_version"]) + 1
        cur.execute(
            """
            UPDATE tb.survey_catalog_conflicts
            SET status='RESOLVED',resolution=%s,resolution_note=%s,current_version=%s,
                resolved_by_user_id=%s,resolved_at=now()
            WHERE id=%s RETURNING *
            """,
            (resolution,payload.note,version,user["user_id"],conflict_id),
        )
        updated = dict(cur.fetchone())
        _history(cur,entity_type="CONFLICT",entity_id=conflict_id,version=version,
                 operation="RESOLVE",before=before,after=updated,
                 actor_user_id=user["user_id"],actor_company_id=user.get("company_id"))
        cur.execute("SELECT count(*) open_count FROM tb.survey_catalog_conflicts WHERE axis_id=%s AND status='OPEN'",
                    (conflict["axis_id"],))
        if int(cur.fetchone()["open_count"]) == 0:
            axis = _row(cur,"survey_catalog_axes",conflict["axis_id"])
            if axis["status"] == "REVIEW":
                before_axis = dict(axis)
                axis_version = int(axis["current_version"]) + 1
                cur.execute("UPDATE tb.survey_catalog_axes SET status='ACTIVE',current_version=%s WHERE id=%s RETURNING *",
                            (axis_version,axis["id"]))
                axis_updated = dict(cur.fetchone())
                _history(cur,entity_type="AXIS",entity_id=axis["id"],version=axis_version,
                         operation="STATUS",before=before_axis,after=axis_updated,
                         actor_user_id=user["user_id"],actor_company_id=axis.get("company_id"))
        _axis_extent_refresh(cur,conflict["axis_id"],user["user_id"])
        audit(cur,action="SURVEY_CATALOG_CONFLICT_RESOLVE",actor_user_id=user["user_id"],
              company_id=user.get("company_id"),entity_type="SURVEY_CATALOG_CONFLICT",
              entity_id=conflict_id,details={"resolution":resolution})
        return _plain(updated)


@router.patch("/axes/{axis_id}")
def update_axis(axis_id: UUID,payload: AxisAdminUpdate,
                user: dict[str,Any] = Depends(require_csrf)) -> dict[str,Any]:
    changes = payload.model_dump(exclude={"base_version"},exclude_unset=True)
    if not changes:
        raise HTTPException(400, "NO_CHANGES")
    with transaction() as conn, conn.cursor() as cur:
        axis = _row(cur,"survey_catalog_axes",axis_id)
        _assert_segment_write_access(user, axis)
        if int(axis["current_version"]) != payload.base_version:
            raise HTTPException(409, {"code":"VERSION_CONFLICT","current_version":axis["current_version"]})
        fields = {
            "route_number":changes.get("route_number",axis.get("route_number")),
            "station_code":changes.get("station_code",axis.get("station_code")),
            "station_name":changes.get("station_name",axis.get("station_name")),
            "track_key":changes.get("track_key",axis.get("track_key")),
            "object_key":changes.get("object_key",axis.get("object_key")),
            "direction":changes.get("direction",axis.get("direction")),
            "axis_type":changes.get("axis_type",axis.get("axis_type")),
            "display_name":changes.get("display_name",axis.get("display_name")),
        }
        fields["station_code"] = _clean(fields["station_code"])
        if fields["station_code"]:
            fields["station_code"] = fields["station_code"].upper()
        fields["object_key"] = _clean(fields["object_key"])
        if fields["object_key"]:
            fields["object_key"] = fields["object_key"].upper()
        fields["axis_type"] = str(fields["axis_type"] or "UNBEKANNT").upper()
        if fields["axis_type"] not in _AXIS_TYPES:
            raise HTTPException(400, "SURVEY_AXIS_TYPE_INVALID")
        before = dict(axis)
        version = int(axis["current_version"]) + 1
        cur.execute(
            """
            UPDATE tb.survey_catalog_axes
            SET route_number=%s,station_code=%s,station_name=%s,track_key=%s,object_key=%s,direction=%s,
                axis_type=%s,display_name=%s,current_version=%s
            WHERE id=%s RETURNING *
            """,
            (_clean(fields["route_number"]),fields["station_code"],_clean(fields["station_name"]),
             _clean(fields["track_key"]),fields["object_key"],_clean(fields["direction"]),fields["axis_type"],
             _clean(fields["display_name"]) or axis["display_name"],version,axis_id),
        )
        updated = dict(cur.fetchone())
        _history(cur,entity_type="AXIS",entity_id=axis_id,version=version,
                 operation="UPDATE",before=before,after=updated,
                 actor_user_id=user["user_id"],actor_company_id=axis.get("company_id"))
        audit(
            cur,
            action="SURVEY_CATALOG_AXIS_UPDATE",
            actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),
            company_id=user.get("company_id"),
            entity_type="SURVEY_CATALOG_AXIS",
            entity_id=axis_id,
            details={"version":version},
        )
        return _catalog_response(user, updated)


def reindex_all_originals() -> dict[str,Any]:
    # Local import avoids the survey_parser -> survey_catalog module cycle.
    from .survey_parser import PARSER_VERSION, reparse_original_for_catalog

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT o.id,o.sha256,p.parser_version,p.source_sha256
            FROM tb.survey_originals o
            LEFT JOIN tb.survey_parse_catalog p ON p.original_id=o.id
            ORDER BY o.imported_at,o.id
            """
        )
        originals = [dict(row) for row in cur.fetchall()]
    counts: dict[str,int] = {}
    failures = 0
    reparsed = 0
    for item in originals:
        original_id = item["id"]
        try:
            parser_stale = (
                str(item.get("parser_version") or "") != PARSER_VERSION
                or str(item.get("source_sha256") or "") != str(item.get("sha256") or "")
            )
            if parser_stale:
                reparse_original_for_catalog(original_id)
                reparsed += 1
            with transaction() as conn, conn.cursor() as cur:
                result = index_original_in_tx(cur,original_id,None)
            key = str(result.get("status") or "UNKNOWN")
            counts[key] = counts.get(key,0) + 1
        except Exception:
            failures += 1
    return {
        "original_count":len(originals),
        "parser_version":PARSER_VERSION,
        "reparsed":reparsed,
        "statuses":counts,
        "failures":failures,
    }


@router.post("/reindex")
def enqueue_reindex(user: dict[str,Any] = Depends(require_admin_csrf)) -> dict[str,Any]:
    with transaction() as conn, conn.cursor() as cur:
        job = enqueue_in_tx(cur,"SURVEY_CATALOG_REINDEX",{})
        audit(cur,action="SURVEY_CATALOG_REINDEX_ENQUEUE",actor_user_id=user["user_id"],
              entity_type="SYSTEM",details={"job_id":str(job["id"])})
    return {"job":_plain(job)}
