# API 0.7.2 / Schema 13: mobile Streckendaten-Snapshots und baustellenweites Loeschen.
#
# WICHTIG: In mobile_track_data landen niemals originale TRA/GRA/KF/KOR-Dateien. Android sendet
# ausschliesslich den abgeleiteten mobilen Arbeitsstand (Achsmetadaten, Samplepunkte/KM und
# Streckenpunkte). Die Originaldateien auf dem NAS bleiben unveraendert.

SERVER_VERSION = "0.7.2"
app.version = SERVER_VERSION


def _schema13_extended_ready(cur):
    cur.execute(
        """SELECT
             to_regclass('tb.mobile_track_data') IS NOT NULL AS has_track,
             to_regclass('tb.worksite_delete_tombstones') IS NOT NULL AS has_delete,
             EXISTS (
               SELECT 1 FROM information_schema.columns
               WHERE table_schema='tb' AND table_name='worksites' AND column_name='deleted_at'
             ) AS has_worksite_deleted_at"""
    )
    row=cur.fetchone() or {}
    return bool(row.get("has_track")) and bool(row.get("has_delete")) and bool(row.get("has_worksite_deleted_at"))


def _require_schema13_extended(cur):
    if not _schema13_extended_ready(cur):
        raise HTTPException(404,"SCHEMA13_REQUIRED")


def _track_dataset_payload(row):
    return {
        "dataset_key":row.get("dataset_key"),
        "dataset_kind":row.get("dataset_kind"),
        "route_key":row.get("route_key"),
        "quelle_datei":row.get("quelle_datei"),
        "achse_name":row.get("achse_name"),
        "payload":row.get("payload") or {},
        "current_version":int(row.get("current_version") or 1),
        "updated_at":row.get("updated_at"),
    }


@app.get("/api/v1/collab/worksites/{worksite_id}/track-data")
def collab_worksite_track_data(worksite_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        _require_schema13_extended(cur)
        access=load_worksite_access(cur,d,worksite_id)
        cur.execute(
            """SELECT dataset_key,dataset_kind,route_key,quelle_datei,achse_name,payload,
                      current_version,updated_at
               FROM tb.mobile_track_data
               WHERE company_id=%s AND source_worksite_id=%s AND deleted_at IS NULL
               ORDER BY dataset_kind,route_key,quelle_datei,achse_name,dataset_key""",
            (d["company_id"],access["worksite_id"]),
        )
        rows=cur.fetchall()
        return {
            "status":"ok",
            "worksite_id":str(access["worksite_id"]),
            "datasets":[_track_dataset_payload(row) for row in rows],
        }


@app.put("/api/v1/collab/worksites/{worksite_id}/track-data")
async def collab_worksite_track_data_put(worksite_id:str, request:Request,
                                          authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    try:
        root=await request.json()
    except Exception:
        raise HTTPException(400,"TRACK_DATA_JSON_INVALID")
    if not isinstance(root,dict) or not isinstance(root.get("datasets",[]),list):
        raise HTTPException(400,"TRACK_DATA_JSON_INVALID")
    datasets=root.get("datasets",[])
    if len(datasets)>1000:
        raise HTTPException(413,"TRACK_DATA_TOO_MANY_DATASETS")

    normalized=[]; seen=set()
    for raw in datasets:
        if not isinstance(raw,dict):
            raise HTTPException(400,"TRACK_DATA_DATASET_INVALID")
        key=str(raw.get("dataset_key") or "").strip()
        kind=str(raw.get("dataset_kind") or "AXIS").strip().upper()
        route_key=str(raw.get("route_key") or "").strip() or None
        quelle=str(raw.get("quelle_datei") or "").strip()
        achse=str(raw.get("achse_name") or "").strip()
        payload=raw.get("payload") or {}
        if not key or len(key)>300 or key in seen:
            raise HTTPException(400,"TRACK_DATA_KEY_INVALID")
        if kind not in ("AXIS","STRECKENPUNKTE"):
            raise HTTPException(400,"TRACK_DATA_KIND_INVALID")
        if not isinstance(payload,dict):
            raise HTTPException(400,"TRACK_DATA_PAYLOAD_INVALID")
        if len(json.dumps(payload,ensure_ascii=False,default=str).encode("utf-8"))>20*1024*1024:
            raise HTTPException(413,"TRACK_DATA_DATASET_TOO_LARGE")
        if kind=="AXIS" and (not quelle or not achse):
            raise HTTPException(400,"TRACK_DATA_AXIS_IDENTITY_REQUIRED")
        if kind=="STRECKENPUNKTE":
            quelle=quelle or "ANDROID_STRECKENPUNKTE"
            achse=achse or "STRECKENPUNKTE"
        seen.add(key)
        normalized.append((key,kind,route_key,quelle,achse,payload))

    with db() as conn, conn.cursor() as cur:
        _require_schema13_extended(cur)
        access=load_worksite_access(cur,d,worksite_id)
        if str(access.get("member_role") or "").upper()!="LEAD":
            raise HTTPException(403,"TRACK_DATA_WRITE_FORBIDDEN")

        active_keys=[]
        for key,kind,route_key,quelle,achse,payload in normalized:
            active_keys.append(key)
            cur.execute(
                """INSERT INTO tb.mobile_track_data(
                       company_id,source_worksite_id,dataset_key,dataset_kind,route_key,
                       quelle_datei,achse_name,payload,created_by,updated_by
                   ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s::jsonb,%s,%s)
                   ON CONFLICT (company_id,dataset_key) DO UPDATE SET
                       source_worksite_id=EXCLUDED.source_worksite_id,
                       dataset_kind=EXCLUDED.dataset_kind,
                       route_key=EXCLUDED.route_key,
                       quelle_datei=EXCLUDED.quelle_datei,
                       achse_name=EXCLUDED.achse_name,
                       payload=EXCLUDED.payload,
                       current_version=tb.mobile_track_data.current_version+1,
                       updated_by=EXCLUDED.updated_by,
                       updated_at=now(),
                       deleted_at=NULL""",
                (
                    d["company_id"],access["worksite_id"],key,kind,route_key,quelle,achse,
                    json.dumps(payload,ensure_ascii=False,default=str),d["user_id"],d["user_id"]
                ),
            )

        if active_keys:
            cur.execute(
                """UPDATE tb.mobile_track_data
                   SET deleted_at=now(),updated_by=%s,current_version=current_version+1,updated_at=now()
                   WHERE company_id=%s AND source_worksite_id=%s AND deleted_at IS NULL
                     AND NOT (dataset_key = ANY(%s))""",
                (d["user_id"],d["company_id"],access["worksite_id"],active_keys),
            )
        else:
            cur.execute(
                """UPDATE tb.mobile_track_data
                   SET deleted_at=now(),updated_by=%s,current_version=current_version+1,updated_at=now()
                   WHERE company_id=%s AND source_worksite_id=%s AND deleted_at IS NULL""",
                (d["user_id"],d["company_id"],access["worksite_id"]),
            )
        conn.commit()

    audit_event(
        "WORKSITE_TRACK_DATA_SYNCED",d["user_id"],d["device_id"],"WORKSITE",access["worksite_id"],
        {"dataset_count":len(normalized)}
    )
    return {"status":"ok","worksite_id":str(access["worksite_id"]),"dataset_count":len(normalized)}


@app.delete("/api/v1/collab/worksites/{worksite_id}")
def collab_delete_worksite_schema13(worksite_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        _require_schema13_extended(cur)
        access=load_worksite_access(cur,d,worksite_id)
        if str(access.get("member_role") or "").upper()!="LEAD":
            raise HTTPException(403,"WORKSITE_DELETE_LEAD_REQUIRED")
        cur.execute("SELECT id,name,company_id,deleted_at FROM tb.worksites WHERE id=%s FOR UPDATE",(access["worksite_id"],))
        ws=cur.fetchone()
        if not ws:
            raise HTTPException(404,"WORKSITE_NOT_FOUND")
        if ws.get("deleted_at") is None:
            now=datetime.now(timezone.utc)
            cur.execute(
                """INSERT INTO tb.worksite_delete_tombstones(worksite_id,company_id,worksite_name,deleted_by,deleted_at)
                   VALUES (%s,%s,%s,%s,%s)
                   ON CONFLICT (worksite_id) DO UPDATE SET
                       company_id=EXCLUDED.company_id,worksite_name=EXCLUDED.worksite_name,
                       deleted_by=EXCLUDED.deleted_by,deleted_at=EXCLUDED.deleted_at""",
                (ws["id"],ws["company_id"],ws.get("name"),d["user_id"],now),
            )
            cur.execute("UPDATE tb.worksites SET deleted_at=%s,updated_at=now() WHERE id=%s",(now,ws["id"]))
            # Ein bewusst geloeschtes mobiles Projekt darf bei einer spaeteren Neuanlage mit dem
            # gleichen lokalen Android-Schluessel eine neue Server-Baustelle erzeugen.
            cur.execute("DELETE FROM tb.worksite_mobile_origins WHERE worksite_id=%s",(ws["id"],))
        conn.commit()

    audit_event(
        "WORKSITE_DELETED",d["user_id"],d["device_id"],"WORKSITE",access["worksite_id"],{}
    )
    return {"status":"ok","worksite_id":str(access["worksite_id"]),"deleted":True}


@app.get("/api/v1/collab/worksite-deletions")
def collab_worksite_deletions(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    if d.get("company_id") is None:
        raise HTTPException(403,"COMPANY_REQUIRED")
    with db() as conn, conn.cursor() as cur:
        _require_schema13_extended(cur)
        cur.execute(
            """SELECT worksite_id,worksite_name,deleted_at
               FROM tb.worksite_delete_tombstones
               WHERE company_id=%s ORDER BY deleted_at,worksite_id""",
            (d["company_id"],),
        )
        return {
            "status":"ok",
            "deletions":[{
                "worksite_id":str(row["worksite_id"]),
                "worksite_name":row.get("worksite_name"),
                "deleted_at":row.get("deleted_at"),
            } for row in cur.fetchall()]
        }


# Nach Schema 13 duerfen geloeschte Baustellen nicht mehr in /collab/context auftauchen. Vor der
# Migration bleibt der bisherige Context-Endpunkt kompatibel aktiv.
_tb_schema13_context_routes=[
    route for route in list(app.router.routes)
    if getattr(route,"path",None)=="/api/v1/collab/context"
    and "GET" in (getattr(route,"methods",set()) or set())
]
if len(_tb_schema13_context_routes)!=1:
    raise RuntimeError(f"COLLAB_CONTEXT_ROUTE_COUNT_SCHEMA13:{len(_tb_schema13_context_routes)}")
_tb_schema13_context_original=_tb_schema13_context_routes[0].endpoint
for _tb_schema13_context_route in _tb_schema13_context_routes:
    app.router.routes.remove(_tb_schema13_context_route)


@app.get("/api/v1/collab/context")
def collab_context_schema13(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        if not _schema13_extended_ready(cur):
            conn.commit()
            return _tb_schema13_context_original(authorization)
        display_sql=worksite_lead_display_sql("g")
        cur.execute(
            f"""SELECT w.id AS worksite_id,w.name,w.designation,w.worksite_type,w.status,w.company_id,
                       gm.group_id,gm.member_role,gm.area_scope,g.label AS group_label,
                       g.lead_user_id,g.lead_name_snapshot,g.lead_name_ordinal,
                       {display_sql} AS lead_display_name
                FROM tb.worksite_group_members gm
                JOIN tb.worksite_groups g ON g.id=gm.group_id AND g.worksite_id=gm.worksite_id
                JOIN tb.worksites w ON w.id=gm.worksite_id
                WHERE gm.user_id=%s AND w.company_id=%s AND w.deleted_at IS NULL
                ORDER BY w.name,g.label""",
            (d["user_id"],d.get("company_id")),
        )
        return {"worksites":cur.fetchall()}


del _tb_schema13_context_route, _tb_schema13_context_routes
