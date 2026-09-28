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


def _edit_distance_at_most_one(left: str, right: str) -> bool:
    if left == right:
        return True
    if abs(len(left) - len(right)) > 1:
        return False
    if len(left) > len(right):
        left, right = right, left
    i = j = differences = 0
    while i < len(left) and j < len(right):
        if left[i] == right[j]:
            i += 1
            j += 1
            continue
        differences += 1
        if differences > 1:
            return False
        if len(left) == len(right):
            i += 1
        j += 1
    if j < len(right) or i < len(left):
        differences += 1
    return differences <= 1


def _match_rank(query: str, item: dict[str, str]) -> tuple[int, str, str] | None:
    code = _normalize(item["code"])
    name = _normalize(item["name"])
    display = _normalize(item["display"])
    if code == query:
        return (0, name, code)
    if code.startswith(query):
        return (1, name, code)
    if (
        len(query) >= 3
        and len(code) == len(query)
        and code[:2] == query[:2]
        and _edit_distance_at_most_one(query, code)
    ):
        return (2, name, code)
    if query in code:
        return (3, name, code)
    if name.startswith(query):
        return (4, name, code)
    if query in name or query in display:
        return (5, name, code)
    if len(query) >= 3 and len(code) == len(query) and _edit_distance_at_most_one(query, code):
        return (6, name, code)
    return None


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
        ranked: list[tuple[tuple[int, str, str], dict[str, str]]] = []
        for item in entries:
            rank = _match_rank(query, item)
            if rank is not None:
                ranked.append((rank, item))
        ranked.sort(key=lambda pair: pair[0])
        matches = []
        for rank, item in ranked[:limit]:
            result = dict(item)
            result["match_type"] = (
                "EXACT"
                if rank[0] == 0
                else "SIMILAR_CODE"
                if rank[0] == 2
                else "SEARCH"
            )
            matches.append(result)
    return {"results": matches, "count": len(matches)}
