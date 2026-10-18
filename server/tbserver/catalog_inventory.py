"""Read-only, whitelisted inventory of the shared railway reference data."""
from __future__ import annotations

import math
from typing import Any


def _range(start: Any, end: Any) -> dict[str, float] | None:
    if start is None or end is None or isinstance(start, bool) or isinstance(end, bool):
        return None
    try:
        a, b = float(start), float(end)
    except (ValueError, TypeError, OverflowError):
        return None
    if not math.isfinite(a) or not math.isfinite(b):
        return None
    return {"km_from_m": min(a, b), "km_to_m": max(a, b)}


def inventory_rows(axes: list[dict], assets: list[dict]) -> list[dict]:
    rows = []
    for axis in axes:
        quality = axis.get("km_quality") or {}
        segments = quality.get("segments") or []
        parts = []
        for segment in segments:
            status = str(segment.get("status") or "FEHLT")
            bounds = _range(segment.get("km_from_m"), segment.get("km_to_m"))
            # Failed calibration must never become an apparently valid KM range.
            if status not in {"AUTOMATISCH", "MANUELL"}:
                bounds = None
            parts.append({"status": status, **(bounds or {"km_from_m": None, "km_to_m": None})})
        rows.append({
            "id": str(axis["id"]), "kind": axis.get("axis_type") or "UNBEKANNT",
            "label": axis.get("display_name") or axis.get("object_key") or axis.get("track_key") or "Achse",
            "route_number": axis.get("route_number"), "station_code": axis.get("station_code"),
            "station_name": axis.get("station_name"), "track_key": axis.get("track_key"),
            "object_key": axis.get("object_key"), "segments": parts,
            "status": quality.get("status") or ("FEHLT" if segments else "KEINE_GEOMETRIE"),
            "selectable": True,
        })
    for asset in assets:
        bounds = _range(asset.get("km_from_m"), asset.get("km_to_m"))
        rows.append({
            "id": str(asset["id"]), "kind": asset.get("data_kind") or "DATEN",
            "label": asset.get("data_kind") or "Weitere Daten",
            "route_number": asset.get("route_number"), "station_code": asset.get("station_code"),
            "station_name": asset.get("station_name"), "track_key": asset.get("track_key"),
            "object_key": None, "selectable": False,
            "status": "KM_VORHANDEN" if bounds else "OHNE_KM",
            "segments": [{"status": "KM_VORHANDEN" if bounds else "OHNE_KM",
                          **(bounds or {"km_from_m": None, "km_to_m": None})}],
        })
    return rows


def shared_inventory(route: str | None, station: str | None) -> dict:
    from .database import transaction
    from .survey_catalog import availability

    # GLOBAL is the shared pool for every authenticated user, regardless of company.
    # Do not impersonate ADMIN: it would also expose company-private source data.
    common = {"company_role": "USER", "company_id": None}
    available = availability(route_number=route, station_code=station, user=common)
    where, params = ["scope='GLOBAL'", "data_status='ACTIVE'"], []
    if route:
        where.append("route_number=%s"); params.append(route)
    if station:
        where.append("upper(station_code)=%s"); params.append(station.upper())
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id,data_kind,route_number,station_code,station_name,track_key,"
            "km_from_m,km_to_m FROM tb.survey_catalog_assets WHERE " +
            " AND ".join(where) + " ORDER BY data_kind,track_key,id",
            tuple(params),
        )
        assets = [dict(row) for row in cur.fetchall()]
    rows = inventory_rows(available.get("axes") or [], assets)
    counts: dict[str, int] = {}
    for row in rows:
        counts[row["kind"]] = counts.get(row["kind"], 0) + 1
    return {"scope": "GLOBAL", "route_number": route, "station_code": station,
            "rows": rows, "counts": counts, "item_count": len(rows)}
