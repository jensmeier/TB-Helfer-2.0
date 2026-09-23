from __future__ import annotations

from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from psycopg.errors import UniqueViolation
from psycopg.types.json import Jsonb

from . import SERVER_VERSION
from .database import transaction
from .events import audit
from .portal import current_portal_user, require_csrf

router = APIRouter(prefix="/api/v1/catalog", tags=["catalog"])

_TYPES = {"ROUTE", "STATION", "SECTION", "OBJECT"}
_TABLE = {
    "ROUTE": "catalog_routes",
    "STATION": "catalog_stations",
    "SECTION": "catalog_sections",
    "OBJECT": "catalog_objects",
}
_OBJECT_TYPES = {"TRACK", "SWITCH", "CROSSING", "PLATFORM", "SIGNAL", "OTHER"}


class Mutation(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=200)
    entity_type: str = Field(min_length=1, max_length=30)
    operation: str = Field(min_length=1, max_length=20)
    entity_id: UUID | None = None
    base_version: int | None = Field(default=None, ge=1)
    payload: dict[str, Any] = Field(default_factory=dict)


class Ack(BaseModel):
    sequence_no: int = Field(ge=0)


def _clean(value: Any, *, required: bool = False, max_length: int = 500) -> str | None:
    if value is None:
        if required:
            raise HTTPException(400, "CATALOG_VALUE_REQUIRED")
        return None
    text = str(value).strip()
    if not text:
        if required:
            raise HTTPException(400, "CATALOG_VALUE_REQUIRED")
        return None
    if len(text) > max_length:
        raise HTTPException(400, "CATALOG_VALUE_TOO_LONG")
    return text


def _num(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError) as exc:
        raise HTTPException(400, "CATALOG_NUMBER_INVALID") from exc


def _plain(value: Any) -> Any:
    if value is None or isinstance(value, (dict, list, str, int, float, bool)):
        return value
    try:
        return dict(value)
    except (TypeError, ValueError):
        return value


def _company(user: dict[str, Any]) -> UUID | None:
    value = user.get("company_id")
    return UUID(str(value)) if value else None


def _payload(kind: str, row: dict[str, Any]) -> dict[str, Any]:
    base = {
        "id": str(row["id"]),
        "current_version": int(row["current_version"]),
    }
    if kind == "ROUTE":
        return {
            **base,
            "route_number": row["route_number"],
            "name": row.get("name"),
            "description": row.get("description"),
        }
    if kind == "STATION":
        return {
            **base,
            "station_code": row.get("station_code"),
            "name": row["name"],
            "short_name": row.get("short_name"),
            "description": row.get("description"),
        }
    if kind == "SECTION":
        a = row.get("km_from_m")
        b = row.get("km_to_m")
        values = [value for value in (a, b) if value is not None]
        return {
            **base,
            "route_id": str(row["route_id"]),
            "designation": row.get("designation"),
            "km_from_m": a,
            "km_to_m": b,
            "km_min_m": min(values) if values else None,
            "km_max_m": max(values) if values else None,
            "data_status": row.get("data_status"),
            "geometry": _plain(row.get("geometry")),
        }
    return {
        **base,
        "route_id": str(row["route_id"]) if row.get("route_id") else None,
        "station_id": str(row["station_id"]) if row.get("station_id") else None,
        "object_type": row["object_type"],
        "designation": row.get("designation"),
        "km_m": row.get("km_m"),
        "data_status": row.get("data_status"),
        "geometry": _plain(row.get("geometry")),
        "properties": _plain(row.get("properties")) or {},
    }


def _load(cur, kind: str, entity_id: UUID, *, lock: bool = False) -> dict[str, Any] | None:
    sql = f"SELECT * FROM tb.{_TABLE[kind]} WHERE id=%s"
    if lock:
        sql += " FOR UPDATE"
    cur.execute(sql, (entity_id,))
    row = cur.fetchone()
    return dict(row) if row else None


def _exists(cur, table: str, entity_id: UUID | None, code: str) -> None:
    if entity_id is None:
        return
    cur.execute(f"SELECT 1 FROM tb.{table} WHERE id=%s", (entity_id,))
    if not cur.fetchone():
        raise HTTPException(404, code)


def _history(
    cur,
    *,
    kind: str,
    entity_id: UUID,
    version: int,
    operation: str,
    before: dict[str, Any] | None,
    after: dict[str, Any],
    user: dict[str, Any],
) -> int:
    cur.execute(
        """
        INSERT INTO tb.catalog_history(
            entity_type, entity_id, entity_version, operation,
            before_data, after_data, changed_by_user_id, changed_by_company_id
        )
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
        RETURNING change_id
        """,
        (
            kind,
            entity_id,
            version,
            operation,
            Jsonb(before) if before is not None else None,
            Jsonb(after),
            user["user_id"],
            _company(user),
        ),
    )
    sequence_no = int(cur.fetchone()["change_id"])
    audit(
        cur,
        action=f"CATALOG_{operation}",
        actor_user_id=user["user_id"],
        actor_device_id=user.get("device_id"),
        company_id=_company(user),
        entity_type=f"CATALOG_{kind}",
        entity_id=entity_id,
        details={"version": version, "change_id": sequence_no},
    )
    return sequence_no


def _replay(cur, user: dict[str, Any], client_change_id: str) -> dict[str, Any] | None:
    lock_key = f"{user['user_id']}:{client_change_id}"
    cur.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", (lock_key,))
    cur.execute(
        """
        SELECT entity_type, response_payload
        FROM tb.mutation_receipts
        WHERE user_id=%s AND client_change_id=%s
        """,
        (user["user_id"], client_change_id),
    )
    row = cur.fetchone()
    if not row:
        return None
    if row["entity_type"] != "CATALOG_MUTATION":
        raise HTTPException(409, "CLIENT_CHANGE_ID_REUSED")
    return _plain(row["response_payload"])


def _receipt(
    cur,
    *,
    user: dict[str, Any],
    client_change_id: str,
    entity_id: UUID,
    version: int,
    response: dict[str, Any],
) -> None:
    cur.execute(
        """
        INSERT INTO tb.mutation_receipts(
            device_id, user_id, client_change_id, entity_type,
            entity_id, result_version, response_payload
        )
        VALUES (%s,%s,%s,'CATALOG_MUTATION',%s,%s,%s)
        """,
        (
            user.get("device_id"),
            user["user_id"],
            client_change_id,
            entity_id,
            version,
            Jsonb(response),
        ),
    )


@router.get("")
def get_catalog(_: dict[str, Any] = Depends(current_portal_user)) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT r.*,
              (SELECT count(*) FROM tb.catalog_sections s WHERE s.route_id=r.id) section_count,
              (SELECT count(*) FROM tb.catalog_objects o WHERE o.route_id=r.id) object_count
            FROM tb.catalog_routes r
            ORDER BY lower(r.route_number)
            """
        )
        routes = []
        for raw in cur.fetchall():
            row = dict(raw)
            item = _payload("ROUTE", row)
            item["section_count"] = int(row["section_count"])
            item["object_count"] = int(row["object_count"])
            routes.append(item)

        cur.execute(
            """
            SELECT s.*,
              (SELECT count(*) FROM tb.catalog_objects o WHERE o.station_id=s.id) object_count
            FROM tb.catalog_stations s
            ORDER BY lower(s.name), lower(coalesce(s.station_code,''))
            """
        )
        stations = []
        for raw in cur.fetchall():
            row = dict(raw)
            item = _payload("STATION", row)
            item["object_count"] = int(row["object_count"])
            stations.append(item)

    return {
        "server_version": SERVER_VERSION,
        "routes": routes,
        "stations": stations,
    }


@router.get("/routes/{route_id}/sections")
def get_sections(
    route_id: UUID,
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        _exists(cur, "catalog_routes", route_id, "CATALOG_ROUTE_NOT_FOUND")
        cur.execute(
            """
            SELECT * FROM tb.catalog_sections
            WHERE route_id=%s
            ORDER BY km_from_m NULLS LAST, lower(coalesce(designation,''))
            """,
            (route_id,),
        )
        rows = [_payload("SECTION", dict(row)) for row in cur.fetchall()]
    return {"sections": rows}


@router.get("/stations/{station_id}/objects")
def get_station_objects(
    station_id: UUID,
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        _exists(cur, "catalog_stations", station_id, "CATALOG_STATION_NOT_FOUND")
        cur.execute(
            """
            SELECT * FROM tb.catalog_objects
            WHERE station_id=%s
            ORDER BY lower(coalesce(designation,'')), object_type
            """,
            (station_id,),
        )
        rows = [_payload("OBJECT", dict(row)) for row in cur.fetchall()]
    return {"objects": rows}


@router.get("/routes/{route_id}/objects")
def get_route_objects(
    route_id: UUID,
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        _exists(cur, "catalog_routes", route_id, "CATALOG_ROUTE_NOT_FOUND")
        cur.execute(
            """
            SELECT * FROM tb.catalog_objects
            WHERE route_id=%s
            ORDER BY lower(coalesce(designation,'')), object_type
            """,
            (route_id,),
        )
        rows = [_payload("OBJECT", dict(row)) for row in cur.fetchall()]
    return {"objects": rows}


@router.get("/changes")
def changes(
    after: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=500),
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("SELECT coalesce(max(change_id),0) value FROM tb.catalog_history")
        latest = int(cur.fetchone()["value"])
        cur.execute(
            """
            SELECT change_id, entity_type, entity_id, entity_version,
                   operation, after_data, changed_at
            FROM tb.catalog_history
            WHERE change_id>%s
            ORDER BY change_id
            LIMIT %s
            """,
            (after, limit + 1),
        )
        rows = [dict(row) for row in cur.fetchall()]

    page = rows[:limit]
    return {
        "next_sequence_no": int(page[-1]["change_id"]) if page else after,
        "latest_sequence_no": latest,
        "has_more": len(rows) > limit,
        "changes": [
            {
                "sequence_no": int(row["change_id"]),
                "entity_type": row["entity_type"],
                "entity_id": str(row["entity_id"]),
                "operation": row["operation"],
                "entity_version": int(row["entity_version"]),
                "changed_at": row["changed_at"],
                "data": _plain(row["after_data"]) or {},
            }
            for row in page
        ],
    }


@router.post("/ack")
def ack(
    payload: Ack,
    _: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    return {"status": "ok", "sequence_no": payload.sequence_no}


@router.get("/history")
def history(
    entity_type: str | None = None,
    entity_id: UUID | None = None,
    limit: int = Query(200, ge=1, le=1000),
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    where: list[str] = []
    params: list[Any] = []
    if entity_type:
        kind = entity_type.strip().upper()
        if kind not in _TYPES:
            raise HTTPException(400, "CATALOG_ENTITY_TYPE_INVALID")
        where.append("h.entity_type=%s")
        params.append(kind)
    if entity_id:
        where.append("h.entity_id=%s")
        params.append(entity_id)
    sql_where = (" WHERE " + " AND ".join(where)) if where else ""

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT h.*, u.display_name actor_display_name, c.name actor_company_name
            FROM tb.catalog_history h
            LEFT JOIN tb.users u ON u.id=h.changed_by_user_id
            LEFT JOIN tb.companies c ON c.id=h.changed_by_company_id
            {sql_where}
            ORDER BY h.change_id DESC
            LIMIT %s
            """,
            tuple(params + [limit]),
        )
        rows = [dict(row) for row in cur.fetchall()]

    admin = user.get("company_role") == "ADMIN"
    own_company = str(user.get("company_id") or "")
    result = []
    for row in rows:
        same_company = bool(
            own_company and own_company == str(row.get("changed_by_company_id") or "")
        )
        identify_actor = admin or same_company
        operation_label = "manuell ergänzt" if row["operation"] == "CREATE" else "manuell geändert"
        result.append(
            {
                "change_id": int(row["change_id"]),
                "entity_type": row["entity_type"],
                "entity_id": str(row["entity_id"]),
                "entity_version": int(row["entity_version"]),
                "operation": row["operation"],
                "before": _plain(row.get("before_data")),
                "after": _plain(row["after_data"]),
                "changed_at": row["changed_at"],
                "actor_user_id": (
                    str(row["changed_by_user_id"])
                    if admin and row.get("changed_by_user_id")
                    else None
                ),
                "actor_company_id": (
                    str(row["changed_by_company_id"])
                    if admin and row.get("changed_by_company_id")
                    else None
                ),
                "actor_display_name": row.get("actor_display_name") if identify_actor else None,
                "actor_company_name": row.get("actor_company_name") if identify_actor else None,
                "summary": f"{operation_label} · {row['changed_at'].date().isoformat()}",
            }
        )
    return {"history": result}


def _write(
    cur,
    *,
    kind: str,
    payload: dict[str, Any],
    entity_id: UUID,
    user: dict[str, Any],
    existing: dict[str, Any] | None,
) -> dict[str, Any]:
    version = int(existing["current_version"]) + 1 if existing else 1
    company_id = _company(user)

    if kind == "ROUTE":
        values = (
            _clean(payload.get("route_number"), required=True, max_length=40),
            _clean(payload.get("name"), max_length=200),
            _clean(payload.get("description"), max_length=1000),
        )
        if existing:
            cur.execute(
                """
                UPDATE tb.catalog_routes
                SET route_number=%s, name=%s, description=%s, current_version=%s
                WHERE id=%s
                RETURNING *
                """,
                (*values, version, entity_id),
            )
        else:
            cur.execute(
                """
                INSERT INTO tb.catalog_routes(
                    id, route_number, name, description,
                    created_by_user_id, created_by_company_id
                )
                VALUES (%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (entity_id, *values, user["user_id"], company_id),
            )

    elif kind == "STATION":
        values = (
            _clean(payload.get("station_code"), max_length=50),
            _clean(payload.get("name"), required=True, max_length=200),
            _clean(payload.get("short_name"), max_length=100),
            _clean(payload.get("description"), max_length=1000),
        )
        if existing:
            cur.execute(
                """
                UPDATE tb.catalog_stations
                SET station_code=%s, name=%s, short_name=%s,
                    description=%s, current_version=%s
                WHERE id=%s
                RETURNING *
                """,
                (*values, version, entity_id),
            )
        else:
            cur.execute(
                """
                INSERT INTO tb.catalog_stations(
                    id, station_code, name, short_name, description,
                    created_by_user_id, created_by_company_id
                )
                VALUES (%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (entity_id, *values, user["user_id"], company_id),
            )

    elif kind == "SECTION":
        try:
            route_id = UUID(str(payload["route_id"])) if payload.get("route_id") else None
        except (TypeError, ValueError) as exc:
            raise HTTPException(400, "CATALOG_ROUTE_INVALID") from exc
        if route_id is None:
            raise HTTPException(400, "CATALOG_ROUTE_REQUIRED")
        _exists(cur, "catalog_routes", route_id, "CATALOG_ROUTE_NOT_FOUND")
        geometry = payload.get("geometry", payload.get("geom"))
        values = (
            route_id,
            _clean(payload.get("designation"), max_length=300),
            _num(payload.get("km_from_m")),
            _num(payload.get("km_to_m")),
            _clean(payload.get("data_status"), max_length=50),
            Jsonb(geometry) if geometry is not None else None,
        )
        if existing:
            cur.execute(
                """
                UPDATE tb.catalog_sections
                SET route_id=%s, designation=%s, km_from_m=%s, km_to_m=%s,
                    data_status=%s, geometry=%s, current_version=%s
                WHERE id=%s
                RETURNING *
                """,
                (*values, version, entity_id),
            )
        else:
            cur.execute(
                """
                INSERT INTO tb.catalog_sections(
                    id, route_id, designation, km_from_m, km_to_m,
                    data_status, geometry, created_by_user_id, created_by_company_id
                )
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (entity_id, *values, user["user_id"], company_id),
            )

    else:
        try:
            route_id = UUID(str(payload["route_id"])) if payload.get("route_id") else None
            station_id = UUID(str(payload["station_id"])) if payload.get("station_id") else None
        except (TypeError, ValueError) as exc:
            raise HTTPException(400, "CATALOG_PARENT_INVALID") from exc
        if route_id is None and station_id is None:
            raise HTTPException(400, "CATALOG_PARENT_REQUIRED")
        _exists(cur, "catalog_routes", route_id, "CATALOG_ROUTE_NOT_FOUND")
        _exists(cur, "catalog_stations", station_id, "CATALOG_STATION_NOT_FOUND")

        object_type = _clean(payload.get("object_type"), required=True, max_length=50).upper()
        if object_type not in _OBJECT_TYPES:
            object_type = "OTHER"
        properties = payload.get("properties")
        if not isinstance(properties, dict):
            properties = {}
        geometry = payload.get("geometry", payload.get("geom"))
        values = (
            route_id,
            station_id,
            object_type,
            _clean(payload.get("designation"), required=True, max_length=300),
            _num(payload.get("km_m")),
            _clean(payload.get("data_status"), max_length=50),
            Jsonb(geometry) if geometry is not None else None,
            Jsonb(properties),
        )
        if existing:
            cur.execute(
                """
                UPDATE tb.catalog_objects
                SET route_id=%s, station_id=%s, object_type=%s, designation=%s,
                    km_m=%s, data_status=%s, geometry=%s, properties=%s,
                    current_version=%s
                WHERE id=%s
                RETURNING *
                """,
                (*values, version, entity_id),
            )
        else:
            cur.execute(
                """
                INSERT INTO tb.catalog_objects(
                    id, route_id, station_id, object_type, designation,
                    km_m, data_status, geometry, properties,
                    created_by_user_id, created_by_company_id
                )
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING *
                """,
                (entity_id, *values, user["user_id"], company_id),
            )

    return dict(cur.fetchone())


@router.post("/mutations")
def mutate(
    payload: Mutation,
    user: dict[str, Any] = Depends(require_csrf),
):
    kind = payload.entity_type.strip().upper()
    operation = payload.operation.strip().upper()
    if kind not in _TYPES:
        raise HTTPException(400, "CATALOG_ENTITY_TYPE_INVALID")
    if operation == "DELETE":
        raise HTTPException(403, "CATALOG_DELETE_NOT_ALLOWED")
    if operation != "UPSERT":
        raise HTTPException(400, "CATALOG_OPERATION_INVALID")

    with transaction() as conn, conn.cursor() as cur:
        replay = _replay(cur, user, payload.client_change_id)
        if replay is not None:
            return replay

        entity_id = payload.entity_id or uuid4()
        existing = _load(cur, kind, entity_id, lock=True)
        if existing is None and payload.base_version is not None:
            raise HTTPException(404, "CATALOG_ENTITY_NOT_FOUND")
        if existing is not None and payload.base_version is None:
            raise HTTPException(409, "BASE_VERSION_REQUIRED")
        if (
            existing is not None
            and payload.base_version is not None
            and payload.base_version != int(existing["current_version"])
        ):
            return JSONResponse(
                status_code=409,
                content={
                    "detail": "VERSION_CONFLICT",
                    "server_version": int(existing["current_version"]),
                    "server_entity": _payload(kind, existing),
                },
            )

        before = _payload(kind, existing) if existing else None
        try:
            row = _write(
                cur,
                kind=kind,
                payload=payload.payload,
                entity_id=entity_id,
                user=user,
                existing=existing,
            )
        except UniqueViolation as exc:
            raise HTTPException(409, "CATALOG_DUPLICATE") from exc

        after = _payload(kind, row)
        version = int(row["current_version"])
        sequence_no = _history(
            cur,
            kind=kind,
            entity_id=entity_id,
            version=version,
            operation="CREATE" if existing is None else "UPDATE",
            before=before,
            after=after,
            user=user,
        )
        response = {
            "entity_id": str(entity_id),
            "entity_version": version,
            "sequence_no": sequence_no,
            "server_entity": after,
        }
        _receipt(
            cur,
            user=user,
            client_change_id=payload.client_change_id,
            entity_id=entity_id,
            version=version,
            response=response,
        )
        return response
