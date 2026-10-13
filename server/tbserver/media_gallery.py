"""Bounded portal-only gallery. The legacy full media catalog stays unchanged."""
from __future__ import annotations

import base64
import hashlib
import json
import re
from datetime import date, datetime, time, timedelta
from typing import Any
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query

from .btb_photo_meta import canonical_photo_metadata, merge_btb_photo_metadata
from .database import transaction
from .image_renditions import image_rendition_urls
from .permissions import area_scope_allows, has_permission, require_permission, worksite_context
from .portal import current_portal_user

router = APIRouter()
_BATCH = 240
_SCAN_BUDGET = 960
GALLERY_VERSION = "PORTAL-012"


def _search_spec(search):
    numeric = re.fullmatch(r"(?:km\s*)?(-?\d+)(?:[.,](\d{3}))?", search, re.IGNORECASE)
    if not numeric:
        return search.split(), None, None
    sign = -1 if numeric[1].startswith('-') else 1
    meter = int(numeric[1])*1000 + sign*int(numeric[2] or '0')
    if abs(meter)>1000000000:
        raise HTTPException(400, "INVALID_MEDIA_KM")
    if meter % 1000 == 0:
        return [], meter, meter+999
    return [], meter-100, meter+99


def _search_sql(file_alias, link_alias, node_alias, title_sql, uploader_sql):
    return f"""tb.gallery_photo_matches(
        tb.gallery_photo_metadata({file_alias}.original_name,{file_alias}.photo_metadata,
            CASE WHEN {link_alias}.entity_type='BTB' THEN {link_alias}.link_metadata ELSE '{{}}'::jsonb END),
        concat_ws(' ',{file_alias}.original_name,{file_alias}.source,{uploader_sql},{title_sql},
            {node_alias}.name,{node_alias}.route_number,{node_alias}.track_key,{node_alias}.payload::text),
        criteria.terms,criteria.km_min,criteria.km_max)"""


def _search_node_sql(file_alias, link_alias):
    return f"""coalesce(sn.external_key,'server-'||sn.id::text) =
        tb.gallery_photo_metadata({file_alias}.original_name,{file_alias}.photo_metadata,
            CASE WHEN {link_alias}.entity_type='BTB' THEN {link_alias}.link_metadata ELSE '{{}}'::jsonb END)->>'worksiteNodeKey'"""

def _fingerprint(worksite_id, user_id, day, kind, search, parent_id):
    return hashlib.sha256(json.dumps(
        [str(worksite_id), str(user_id), str(day), kind, search, str(parent_id)],
        ensure_ascii=False, separators=(",", ":"),
    ).encode()).hexdigest()[:24]


def _cursor(row, fingerprint):
    payload = [1, row["created_at"].isoformat(), str(row["id"]), fingerprint]
    return base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")


def _after(cursor, fingerprint):
    if not cursor:
        return None
    try:
        value = json.loads(base64.b64decode(
            cursor + "=" * (-len(cursor) % 4), altchars=b"-_", validate=True,
        ))
        if not isinstance(value, list) or len(value) != 4 or value[0] != 1 or value[3] != fingerprint:
            raise ValueError("scope")
        stamp = datetime.fromisoformat(value[1])
        if stamp.tzinfo is None:
            raise ValueError("timezone")
        return stamp, UUID(value[2])
    except (ValueError, TypeError, KeyError, IndexError) as exc:
        raise HTTPException(400, "INVALID_MEDIA_CURSOR") from exc


def _visible(link, context, user):
    kind = link["kind"]
    if not link["active"]:
        return False
    if kind == "BTB":
        privileged = user["company_role"] == "CHEF" or context.get("worksite_role") == "RESPONSIBLE_TB"
        return privileged or (has_permission(context, "btb.view") and link["team_member"])
    permission = "defect.view" if kind == "DEFECT" else "acceptance.view"
    return has_permission(context, permission) and area_scope_allows(
        context["effective_area_scope"], link["area_scope"],
    )


@router.get("/worksites/{worksite_id}/media/page")
def gallery_page(
    worksite_id: UUID,
    limit: int = Query(default=60, ge=1, le=100),
    cursor: str | None = Query(default=None, max_length=768),
    day: date | None = Query(default=None),
    kind: str = Query(default="", max_length=16),
    search: str = Query(default="", max_length=160),
    parent_id: UUID | None = Query(default=None),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict:
    kind = kind.strip().upper()
    search = search.strip().lower()
    if kind not in {"", "BTB", "DEFECT", "ACCEPTANCE"}:
        raise HTTPException(400, "INVALID_MEDIA_KIND")
    fingerprint = _fingerprint(worksite_id, user["user_id"], day, kind, search, parent_id)
    after = _after(cursor, fingerprint)
    terms, km_min, km_max = _search_spec(search)
    clauses = ["f.worksite_id = %s", "f.deleted_at IS NULL",
               "lower(f.mime_type) LIKE 'image/%%'"]
    base_params: list[Any] = [worksite_id]
    if day is not None:
        try:
            start = datetime.combine(day, time.min, ZoneInfo("Europe/Berlin"))
            end = datetime.combine(day + timedelta(days=1), time.min, ZoneInfo("Europe/Berlin"))
        except (OverflowError, ValueError) as exc:
            raise HTTPException(400, "INVALID_MEDIA_DAY") from exc
        clauses.append("COALESCE(f.captured_at, f.created_at) >= %s AND COALESCE(f.captured_at, f.created_at) < %s")
        base_params.extend([start, end])
    if search:
        match = _search_sql('f','sl','sn',"CASE sl.entity_type WHEN 'DEFECT' THEN sd.title WHEN 'ACCEPTANCE' THEN sa.title END",'uploader.display_name')
        node = _search_node_sql('f','sl')
        clauses.append(f"""EXISTS (SELECT 1 FROM tb.file_links sl
            LEFT JOIN tb.defects sd ON sl.entity_type='DEFECT' AND sd.id=sl.entity_id AND sd.worksite_id=f.worksite_id
            LEFT JOIN tb.acceptances sa ON sl.entity_type='ACCEPTANCE' AND sa.id=sl.entity_id AND sa.worksite_id=f.worksite_id
            LEFT JOIN tb.worksite_nodes sn ON sn.worksite_id=f.worksite_id AND sn.deleted_at IS NULL AND {node}
            WHERE sl.file_id=f.id AND sl.entity_type IN ('BTB','DEFECT','ACCEPTANCE')
              AND {match})""")
    clauses.append("""EXISTS (SELECT 1 FROM tb.file_links filter_link
        WHERE filter_link.file_id=f.id
          AND filter_link.entity_type IN ('BTB','DEFECT','ACCEPTANCE')
          AND (%s = '' OR filter_link.entity_type = %s))""")
    base_params.extend([kind, kind])

    if parent_id is not None:
        clauses.append("EXISTS (SELECT 1 FROM tb.file_links pl WHERE pl.file_id=f.id AND pl.entity_id=%s AND (%s=\'\' OR pl.entity_type=%s))")
        base_params.extend([parent_id, kind, kind])

    media = []
    scanned = 0
    last = None
    exhausted = False
    with transaction() as conn, conn.cursor() as cur:
        context = worksite_context(cur, user, worksite_id)
        require_permission(context, "files.view")
        while scanned < _SCAN_BUDGET and len(media) < limit:
            where = list(clauses)
            params = list(base_params)
            if after is not None:
                where.append("(f.created_at, f.id) < (%s, %s)")
                params.extend(after)
            size = min(_BATCH, _SCAN_BUDGET - scanned)
            params.extend([size, user["user_id"], kind, kind])
            cur.execute(f"""
                WITH criteria AS (SELECT %s::text[] AS terms,%s::bigint AS km_min,%s::bigint AS km_max),
                page AS MATERIALIZED (
                    SELECT f.id, f.original_name, f.mime_type, f.created_at,
                           f.captured_at, f.source, f.photo_metadata, f.byte_size,
                           f.gps_lat, f.gps_lon, f.gps_accuracy_m, uploader.display_name AS uploader_name
                    FROM tb.files f
                    LEFT JOIN tb.users uploader ON uploader.id=f.created_by CROSS JOIN criteria
                    WHERE {' AND '.join(where)}
                    ORDER BY f.created_at DESC, f.id DESC LIMIT %s
                )
                SELECT page.*, links.items AS links
                FROM page CROSS JOIN criteria
                CROSS JOIN LATERAL (
                    SELECT jsonb_agg(jsonb_build_object(
                        'kind', l.entity_type, 'parent_id', l.entity_id,
                        'matches', {_search_sql('page','l','sn',"CASE l.entity_type WHEN 'DEFECT' THEN d.title WHEN 'ACCEPTANCE' THEN a.title END",'page.uploader_name')},
                        'link_metadata', CASE WHEN l.entity_type='BTB' THEN l.link_metadata ELSE '{{}}'::jsonb END,
                        'btb_date', b.work_date,
                        'title', CASE l.entity_type WHEN 'DEFECT' THEN left(d.title,500) WHEN 'ACCEPTANCE' THEN left(a.title,500) ELSE NULL END,
                        'active', CASE l.entity_type
                            WHEN 'BTB' THEN b.id IS NOT NULL AND b.status <> 'DELETED' AND b.deleted_at IS NULL
                            WHEN 'DEFECT' THEN d.id IS NOT NULL AND d.status <> 'DELETED' AND d.deleted_at IS NULL
                            WHEN 'ACCEPTANCE' THEN a.id IS NOT NULL AND a.status <> 'DELETED' AND a.deleted_at IS NULL
                            ELSE false END,
                        'team_member', EXISTS (SELECT 1 FROM tb.team_members tm
                            WHERE tm.team_id=b.team_id AND tm.user_id=%s AND tm.removed_at IS NULL),
                        'area_scope', CASE l.entity_type WHEN 'DEFECT' THEN d.area_scope
                            WHEN 'ACCEPTANCE' THEN a.area_scope ELSE '{{}}'::jsonb END
                    ) ORDER BY l.entity_type, l.entity_id, l.purpose) AS items
                    FROM tb.file_links l
                    LEFT JOIN tb.btbs b ON l.entity_type='BTB' AND b.id=l.entity_id AND b.worksite_id=%s
                    LEFT JOIN tb.defects d ON l.entity_type='DEFECT' AND d.id=l.entity_id AND d.worksite_id=%s
                    LEFT JOIN tb.acceptances a ON l.entity_type='ACCEPTANCE' AND a.id=l.entity_id AND a.worksite_id=%s
                    LEFT JOIN tb.worksite_nodes sn ON sn.worksite_id=%s AND sn.deleted_at IS NULL AND {_search_node_sql('page','l')}
                    WHERE l.file_id=page.id AND l.entity_type IN ('BTB','DEFECT','ACCEPTANCE')
                      AND (%s='' OR l.entity_type=%s)
                ) links
                ORDER BY page.created_at DESC, page.id DESC
            """, tuple([terms,km_min,km_max] + params[:-2] + [worksite_id, worksite_id, worksite_id,worksite_id] + params[-2:]))
            rows = cur.fetchall()
            if not rows:
                exhausted = True
                break
            for row in rows:
                scanned += 1
                last = row
                after = (row["created_at"], row["id"])
                link = next((link for link in (row["links"] or []) if (parent_id is None or str(link["parent_id"]) == str(parent_id)) and link.get("matches", True) and _visible(link, context, user)), None)
                if link is not None:
                    canonical = canonical_photo_metadata(
                        filename=row["original_name"], captured_at=row["captured_at"],
                        gps_lat=row["gps_lat"], gps_lon=row["gps_lon"],
                        gps_accuracy_m=row["gps_accuracy_m"], photo_metadata=row["photo_metadata"],
                    )
                    meta = canonical["photo_metadata"] or {}
                    if link["kind"] == "BTB":
                        meta = merge_btb_photo_metadata(meta, link.get("link_metadata"))
                    urls = image_rendition_urls(row["id"], row["mime_type"])
                    media.append({
                        "attachment_id": str(row["id"]), "entity_type": link["kind"],
                        "parent_entity_id": link["parent_id"], "filename": row["original_name"],
                        "source": row["source"], "byte_size": row["byte_size"],
                        "btb_date": link.get("btb_date"), "photo_number": None, "created_at": row["created_at"],
                        "captured_at": canonical["captured_at"],
                        "gps_lat": canonical["gps_lat"], "gps_lon": canonical["gps_lon"],
                        "gps_accuracy_m": canonical["gps_accuracy_m"],
                        "context_title": link.get("title"),
                        "photo_metadata": {key: value for key, value in meta.items() if key in {
                            "firma", "firmaId", "worksiteNodeKey", "kurztext", "typ", "indikator", "objektKennung", "objektBereich",
                            "km", "kmSuffix", "notiz", "info1", "info2", "info3", "info4", "info5",
                        }},
                        "renditions": {key: urls[key] for key in ("thumb", "preview") if key in urls},
                    })
                if len(media) == limit:
                    break
            # If every fetched row was consumed and fewer than requested exist, we are done.
            if last is rows[-1] and len(rows) < size:
                exhausted = True
                break
        # Only selected, authorized BTBs participate. Rank the same deduplication
        # key as uniquePhotos(), using the first file in the existing BTB order.
        btbs = list({UUID(str(m["parent_entity_id"])) for m in media if m["entity_type"] == "BTB"})
        if btbs:
            cur.execute("""
                WITH originals AS (
                    SELECT DISTINCT l.entity_id, f.id, f.captured_at, f.created_at,
                        COALESCE(NULLIF(f.sha256,''),NULLIF(f.original_name,''),f.id::text) AS photo_key
                    FROM tb.file_links l JOIN tb.files f ON f.id=l.file_id
                    WHERE l.entity_type='BTB' AND l.entity_id=ANY(%s)
                      AND l.purpose<>'BTB_FINAL_PDF' AND f.deleted_at IS NULL
                      AND f.mime_type LIKE 'image/%%' AND f.worksite_id=%s
                ), representatives AS (
                    SELECT DISTINCT ON (entity_id, photo_key) * FROM originals
                    ORDER BY entity_id, photo_key, captured_at NULLS LAST, created_at, id
                ), numbered AS (
                    SELECT entity_id, photo_key, row_number() OVER (
                        PARTITION BY entity_id ORDER BY captured_at NULLS LAST, created_at, id
                    ) AS photo_number FROM representatives
                )
                SELECT o.entity_id, o.id, n.photo_number FROM originals o
                JOIN numbered n USING(entity_id,photo_key) WHERE o.id=ANY(%s)
            """, (btbs, worksite_id, [UUID(m["attachment_id"]) for m in media if m["entity_type"] == "BTB"]))
            numbers = {(str(r["entity_id"]),str(r["id"])):r["photo_number"] for r in cur.fetchall()}
            for m in media:
                m["photo_number"] = numbers.get((str(m["parent_entity_id"]),m["attachment_id"]))
        keys = list({m["photo_metadata"]["worksiteNodeKey"] for m in media if m["photo_metadata"].get("worksiteNodeKey")})
        nodes = {}
        if keys:
            cur.execute("""SELECT coalesce(external_key, 'server-' || id::text) AS client_node_key, node_kind, name, route_number, track_key, payload
                FROM tb.worksite_nodes WHERE worksite_id=%s AND deleted_at IS NULL
                  AND coalesce(external_key, 'server-' || id::text)=ANY(%s)""", (worksite_id, keys))
            nodes = {r["client_node_key"]: dict(r) for r in cur.fetchall()}
        for m in media:
            key = m["photo_metadata"].get("worksiteNodeKey")
            m["worksite_node"] = nodes.get(key)
    next_cursor = _cursor(last, fingerprint) if last is not None and not exhausted else None
    return {"search_km_min_m": km_min, "search_km_max_m": km_max, "gallery_version": GALLERY_VERSION, "media": media, "count": len(media), "next_cursor": next_cursor,
            "snapshot_complete": next_cursor is None,
            "scan_limited": not exhausted and len(media) < limit}


