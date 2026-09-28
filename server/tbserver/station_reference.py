from __future__ import annotations

import json
import os
import re
from functools import lru_cache
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query

from .admin import require_admin


router = APIRouter(prefix="/api/v1/admin/station-reference", tags=["admin-station-reference"])
_NON_ALNUM = re.compile(r"[^\w]+", flags=re.UNICODE)


def _normalize(value: Any) -> str:
    return _NON_ALNUM.sub("", str(value or "").strip().casefold())


def _data_path(env_name: str, filename: str) -> Path:
    configured = os.getenv(env_name, "").strip()
    if configured:
        return Path(configured)
    return Path(__file__).resolve().parent.parent / "data" / filename


def _read_json(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8-sig") as source:
            value = json.load(source)
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"BAHNHOF_REFERENZDATEN_UNLESBAR:{path.name}") from exc
    return value if isinstance(value, dict) else {}


@lru_cache(maxsize=1)
def _entries() -> tuple[dict[str, str], ...]:
    active_path = _data_path(
        "STATION_ACTIVE_PATH",
        "TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json",
    )
    operating_path = _data_path(
        "STATION_OPERATING_PATH",
        "TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json",
    )
    by_code: dict[str, dict[str, str]] = {}

    active = _read_json(active_path)
    for raw in active.get("eintraege") or []:
        if not isinstance(raw, dict):
            continue
        code = str(raw.get("code") or "").strip().upper()
        name = str(raw.get("name") or "").strip()
        if not code or not name:
            continue
        by_code[code] = {
            "code": code,
            "name": name,
            "display": f"{code} – {name}",
            "source": "Aktive Bahnhofsliste",
        }

    operating = _read_json(operating_path)
    for raw in operating.get("betriebsstellen") or []:
        if not isinstance(raw, dict):
            continue
        code = str(raw.get("rl100Code") or "").strip().upper()
        name = str(raw.get("rl100Langname") or "").strip()
        if not code or not name or code in by_code:
            continue
        by_code[code] = {
            "code": code,
            "name": name,
            "display": f"{code} – {name}",
            "source": "Betriebsstellenliste",
        }

    return tuple(
        sorted(
            by_code.values(),
            key=lambda item: (_normalize(item["name"]), item["code"]),
        )
    )


@router.get("")
def search_station_reference(
    q: str = Query(default="", max_length=100),
    limit: int = Query(default=20, ge=1, le=50),
    _: dict[str, Any] = Depends(require_admin),
) -> dict:
    query = _normalize(q)
    try:
        entries = _entries()
    except RuntimeError as exc:
        raise HTTPException(503, str(exc)) from exc

    if not query:
        matches = list(entries[:limit])
    else:
        matches = [
            item
            for item in entries
            if query in _normalize(item["code"])
            or query in _normalize(item["name"])
            or query in _normalize(item["display"])
        ][:limit]
    return {"results": matches, "count": len(matches)}
