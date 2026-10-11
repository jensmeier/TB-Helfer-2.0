"""Bounded portal-only gallery. The legacy full media catalog stays unchanged."""
from __future__ import annotations

import base64
import hashlib
import json
from datetime import date, datetime, time, timedelta
from typing import Any
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query

from .btb_photo_meta import canonical_photo_metadata
from .database import transaction
from .image_renditions import image_rendition_urls
from .permissions import area_scope_allows, has_permission, require_permission, worksite_context
from .portal import current_portal_user

router = APIRouter()
_BATCH = 240
_SCAN_BUDGET = 960


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
        # Literal substring: % and _ are not wildcard operators.
        clauses.append("""strpos(lower(concat_ws(' ', f.original_name, f.source,
            uploader.display_name, f.photo_metadata::text)), %s) > 0""")
        base_params.append(search)
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
                WITH page AS MATERIALIZED (
                    SELECT f.id, f.original_name, f.mime_type, f.created_at,
                           f.captured_at, f.source, f.photo_metadata,
                           f.gps_lat, f.gps_lon, f.gps_accuracy_m
                    FROM tb.files f
                    LEFT JOIN tb.users uploader ON uploader.id=f.created_by
                    WHERE {' AND '.join(where)}
                    ORDER BY f.created_at DESC, f.id DESC LIMIT %s
                )
                SELECT page.*, links.items AS links
                FROM page
                CROSS JOIN LATERAL (
                    SELECT jsonb_agg(jsonb_build_object(
                        'kind', l.entity_type, 'parent_id', l.entity_id,
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
                    WHERE l.file_id=page.id AND l.entity_type IN ('BTB','DEFECT','ACCEPTANCE')
                      AND (%s='' OR l.entity_type=%s)
                ) links
                ORDER BY page.created_at DESC, page.id DESC
            """, tuple(params[:-2] + [worksite_id, worksite_id, worksite_id] + params[-2:]))
            rows = cur.fetchall()
            if not rows:
                exhausted = True
                break
            for row in rows:
                scanned += 1
                last = row
                after = (row["created_at"], row["id"])
                link = next((link for link in (row["links"] or []) if (parent_id is None or str(link["parent_id"]) == str(parent_id)) and _visible(link, context, user)), None)
                if link is not None:
                    meta = canonical_photo_metadata(
                        filename=row["original_name"], captured_at=row["captured_at"],
                        gps_lat=row["gps_lat"], gps_lon=row["gps_lon"],
                        gps_accuracy_m=row["gps_accuracy_m"], photo_metadata=row["photo_metadata"],
                    )["photo_metadata"] or {}
                    urls = image_rendition_urls(row["id"], row["mime_type"])
                    media.append({
                        "attachment_id": str(row["id"]), "entity_type": link["kind"],
                        "parent_entity_id": link["parent_id"], "filename": row["original_name"],
                        "source": row["source"], "created_at": row["created_at"],
                        "captured_at": row["captured_at"],
                        "photo_metadata": {"short_text": str(meta.get("short_text") or "")[:500]},
                        "renditions": {key: urls[key] for key in ("thumb", "preview") if key in urls},
                    })
                if len(media) == limit:
                    break
            # If every fetched row was consumed and fewer than requested exist, we are done.
            if last is rows[-1] and len(rows) < size:
                exhausted = True
                break
    next_cursor = _cursor(last, fingerprint) if last is not None and not exhausted else None
    return {"media": media, "count": len(media), "next_cursor": next_cursor,
            "snapshot_complete": next_cursor is None,
            "scan_limited": not exhausted and len(media) < limit}
