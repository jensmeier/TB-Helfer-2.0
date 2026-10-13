def sync_conflict_response(cur,d,req,current):
    # Ein 409 verändert keine Fachdaten und wird absichtlich NICHT als Idempotenz-Receipt
    # festgeschrieben. Nach bewusster Konfliktauflösung darf Android dieselbe Queue-Zeile mit
    # neuer base_version erneut senden. Erfolgreich angewendete Mutationen bleiben dagegen
    # strikt über client_change_id idempotent.
    return {"detail":"VERSION_CONFLICT","server_version":int(current.get("current_version") or 0),
            "server_entity":current}


@app.post("/api/v1/sync/mutations")
def sync_mutations(req:SyncMutationRequest, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    t=sync_type_for_client(req.entity_type)
    operation=req.operation.upper()
    if t not in ("SECTION","OBJECT"):
        raise HTTPException(400,"ENTITY_TYPE_NOT_WRITABLE")
    if operation not in ("UPSERT","DELETE"):
        raise HTTPException(400,"OPERATION_INVALID")
    if not req.entity_id:
        raise HTTPException(400,"ENTITY_ID_REQUIRED")
    enforce_json_size(req.payload)
    eid=parse_uuid_value(req.entity_id,"ENTITY_ID_INVALID")

    with db() as conn, conn.cursor() as cur:
        advisory_xact_lock(cur,f"sync-receipt:{d['device_id']}:{req.client_change_id}")
        cur.execute("SELECT http_status,response FROM tb.sync_mutation_receipts WHERE device_id=%s AND client_change_id=%s",
                    (d["device_id"],req.client_change_id))
        receipt=cur.fetchone()
        if receipt:
            conn.commit()
            return JSONResponse(jsonable_encoder(receipt["response"]),status_code=int(receipt["http_status"]))

        current=general_entity_for_sync(cur,t,eid)
        # Sperre nach dem normalen Lesen explizit für die Mutation.
        if t=="SECTION":
            cur.execute("""SELECT id,route_id,designation,km_from_m,km_to_m,km_min_m,km_max_m,data_status,
                                  current_version,created_at,updated_at,deleted_at,
                                  CASE WHEN geom IS NULL THEN NULL ELSE ST_AsGeoJSON(geom)::jsonb END AS geom
                           FROM tb.route_sections WHERE id=%s FOR UPDATE""",(eid,))
        else:
            cur.execute("""SELECT id,route_id,station_id,object_type,designation,km_m,data_status,properties,
                                  current_version,created_at,updated_at,deleted_at,
                                  CASE WHEN geom IS NULL THEN NULL ELSE ST_AsGeoJSON(geom)::jsonb END AS geom
                           FROM tb.infrastructure_objects WHERE id=%s FOR UPDATE""",(eid,))
        current=cur.fetchone()
        if not current or current.get("deleted_at") is not None:
            raise HTTPException(404,"ENTITY_NOT_FOUND")
        if req.base_version is None or int(req.base_version) != int(current["current_version"]):
            response=sync_conflict_response(cur,d,req,current)
            conn.commit()
            return JSONResponse(jsonable_encoder(response),status_code=409)

        new_version=int(current["current_version"])+1
        if operation=="DELETE":
            table="tb.route_sections" if t=="SECTION" else "tb.infrastructure_objects"
            cur.execute(f"UPDATE {table} SET deleted_at=now(),current_version=%s WHERE id=%s",(new_version,eid))
            payload={"route_id":str(current["route_id"]) if current.get("route_id") else None,
                     "station_id":str(current["station_id"]) if current.get("station_id") else None}
            seq=add_change_row(cur,"ROUTE_SECTION" if t=="SECTION" else "OBJECT",eid,"DELETE",new_version,
                           d["user_id"],payload,changed_by_device=d["device_id"])
            response={"entity_id":str(eid),"entity_version":new_version,"sequence_no":seq,"server_entity":payload}
        elif t=="SECTION":
            route_id=req.payload.get("route_id")
            if route_id and str(current["route_id"]) != str(route_id):
                raise HTTPException(400,"ROUTE_MOVE_NOT_ALLOWED")
            data_status=(req.payload.get("data_status") or current.get("data_status") or "MANUAL").upper()
            if data_status not in ("VERIFIED_ORIGINAL","MANUAL","GEOMETRY_WITHOUT_KM","WITHOUT_GEOMETRY"):
                raise HTTPException(400,"DATA_STATUS_INVALID")
            designation=req.payload.get("designation")
            km_from=req.payload.get("km_from_m"); km_to=req.payload.get("km_to_m")
            for v in (km_from,km_to):
                if v is not None:
                    try: float(v)
                    except (TypeError,ValueError): raise HTTPException(400,"KM_INVALID")
            snapshot={k:current.get(k) for k in ("route_id","designation","km_from_m","km_to_m","km_min_m","km_max_m","data_status")}
            cur.execute(
                """INSERT INTO tb.route_section_versions(route_section_id,version_no,changed_by,changed_by_device,change_reason,snapshot,geom)
                   SELECT id,current_version,%s,%s,'Android Offline-Sync',%s::jsonb,geom
                   FROM tb.route_sections WHERE id=%s ON CONFLICT (route_section_id,version_no) DO NOTHING""",
                (d["user_id"],d["device_id"],json.dumps(snapshot,ensure_ascii=False,default=str),eid),
            )
            cur.execute(
                """UPDATE tb.route_sections SET designation=%s,km_from_m=%s,km_to_m=%s,data_status=%s,current_version=%s
                   WHERE id=%s""",
                ((str(designation).strip() or None) if designation is not None else None,km_from,km_to,data_status,new_version,eid),
            )
            current=general_entity_for_sync(cur,"SECTION",eid)
            seq=add_change_row(cur,"ROUTE_SECTION",eid,"UPDATE",new_version,d["user_id"],current,changed_by_device=d["device_id"])
            response={"entity_id":str(eid),"entity_version":new_version,"sequence_no":seq,"server_entity":current}
        else:
            if req.payload.get("route_id") is not None and str(req.payload.get("route_id")) != str(current.get("route_id")):
                raise HTTPException(400,"OBJECT_ROUTE_MOVE_NOT_ALLOWED")
            if req.payload.get("station_id") is not None and str(req.payload.get("station_id")) != str(current.get("station_id")):
                raise HTTPException(400,"OBJECT_STATION_MOVE_NOT_ALLOWED")
            if req.payload.get("object_type") and str(req.payload.get("object_type")).upper()!=str(current.get("object_type")).upper():
                raise HTTPException(400,"OBJECT_TYPE_CHANGE_NOT_ALLOWED")
            designation=str(req.payload.get("designation") or "").strip()
            if not designation: raise HTTPException(400,"DESIGNATION_REQUIRED")
            km=req.payload.get("km_m")
            if km is not None:
                try: float(km)
                except (TypeError,ValueError): raise HTTPException(400,"KM_INVALID")
            data_status=(req.payload.get("data_status") or current.get("data_status") or "MANUAL").upper()
            if data_status not in ("VERIFIED_ORIGINAL","MANUAL","GEOMETRY_WITHOUT_KM","WITHOUT_GEOMETRY"):
                raise HTTPException(400,"DATA_STATUS_INVALID")
            properties=req.payload.get("properties",current.get("properties") or {})
            if not isinstance(properties,dict): raise HTTPException(400,"PROPERTIES_INVALID")
            snapshot={k:current.get(k) for k in ("route_id","station_id","object_type","designation","km_m","data_status","properties")}
            cur.execute(
                """INSERT INTO tb.object_versions(object_id,version_no,changed_by,changed_by_device,change_reason,snapshot,geom)
                   SELECT id,current_version,%s,%s,'Android Offline-Sync',%s::jsonb,geom
                   FROM tb.infrastructure_objects WHERE id=%s ON CONFLICT (object_id,version_no) DO NOTHING""",
                (d["user_id"],d["device_id"],json.dumps(snapshot,ensure_ascii=False,default=str),eid),
            )
            cur.execute("""UPDATE tb.infrastructure_objects
                           SET designation=%s,km_m=%s,data_status=%s,properties=%s::jsonb,current_version=%s
                           WHERE id=%s""",
                        (designation,km,data_status,json.dumps(properties,ensure_ascii=False),new_version,eid))
            current=general_entity_for_sync(cur,"OBJECT",eid)
            seq=add_change_row(cur,"OBJECT",eid,"UPDATE",new_version,d["user_id"],current,changed_by_device=d["device_id"])
            response={"entity_id":str(eid),"entity_version":new_version,"sequence_no":seq,"server_entity":current}

        store_sync_receipt(cur,d["device_id"],req.client_change_id,200,response)
        conn.commit()
        return JSONResponse(jsonable_encoder(response),status_code=200)


# ---------------------------------------------------------------------------
# Baustellen-Kollaboration ab 0.7.0
# ---------------------------------------------------------------------------

COLLAB_STATUSES={
    "BTB":{"ENTWURF","FERTIG"},
    "DEFECT":{"OFFEN","IN_ARBEIT","ERLEDIGT","ABGELEHNT"},
    "ACCEPTANCE":{"ENTWURF","NOCH_ZU_PRUEFEN","ABGENOMMEN_MIT_RESTPUNKTEN","ABGENOMMEN","NICHT_ABGENOMMEN"},
}


def store_collab_receipt(cur,d,req,status,response,entity_id=None,entity_version=None):
    cur.execute(
        """INSERT INTO tb.collab_mutation_receipts
           (device_id,client_change_id,worksite_id,entity_type,entity_id,entity_version,http_status,response_payload)
           VALUES (%s,%s,%s,%s,%s,%s,%s,%s::jsonb)""",
        (d["device_id"],req.client_change_id,parse_uuid_value(req.worksite_id,"WORKSITE_ID_INVALID"),
         req.entity_type.upper(),entity_id,entity_version,status,json.dumps(jsonable_encoder(response),ensure_ascii=False)),
    )


def collab_lookup_by_client_key(cur,entity_type,worksite_id,group_id,client_key,lock=False):
    t,table,keycol=collab_table_info(entity_type)
    if t=="BTB":
        sql=f"SELECT id FROM {table} WHERE group_id=%s AND {keycol}=%s"
        params=(group_id,client_key)
    else:
        sql=f"SELECT id FROM {table} WHERE worksite_id=%s AND {keycol}=%s"
        params=(worksite_id,client_key)
    if lock: sql += " FOR UPDATE"
    cur.execute(sql,params)
    hit=cur.fetchone()
    return collab_entity_by_id(cur,t,hit["id"],lock=lock) if hit else None


@app.get("/api/v1/collab/context")
def collab_context(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """SELECT w.id AS worksite_id,w.name,w.designation,w.worksite_type,w.status,w.company_id,
                      gm.group_id,gm.member_role,gm.area_scope,g.label AS group_label,
                      g.lead_user_id,g.lead_name_snapshot
               FROM tb.worksite_group_members gm
               JOIN tb.worksite_groups g ON g.id=gm.group_id AND g.worksite_id=gm.worksite_id
               JOIN tb.worksites w ON w.id=gm.worksite_id
               WHERE gm.user_id=%s AND w.company_id=%s
               ORDER BY w.name,g.label""",
            (d["user_id"],d.get("company_id")),
        )
        return {"worksites":cur.fetchall()}


def collab_change_visible(access,row):
    t=row["entity_type"]
    if t=="BTB": return row.get("group_id")==access["group_id"]
    if t=="ATTACHMENT" and row.get("group_id") is not None:
        return row.get("group_id")==access["group_id"]
    return scope_visible(access.get("area_scope") or {},row.get("area_scope") or {})


@app.get("/api/v1/collab/changes")
def collab_changes(worksite_id:str, after:int=0, limit:int=100, authorization: str | None = Header(default=None)):
    d=require_device(authorization); after=max(0,int(after)); limit=max(1,min(int(limit),250))
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,worksite_id)
        cur.execute("SELECT COALESCE(max(sequence_no),0) AS latest FROM tb.collab_change_log WHERE worksite_id=%s",(access["worksite_id"],))
        latest=int(cur.fetchone()["latest"])
        cursor=after; visible=[]
        while cursor < latest and len(visible)<limit:
            batch_size=max(100,min(500,(limit-len(visible))*4))
            cur.execute(
                """SELECT l.sequence_no,l.worksite_id,l.group_id,l.entity_type,l.entity_id,l.operation,
                          l.entity_version,l.changed_by_device,l.area_scope,l.payload,l.changed_at,u.full_name AS changed_by_name
                   FROM tb.collab_change_log l
                   LEFT JOIN tb.users u ON u.id=l.changed_by
                   WHERE l.worksite_id=%s AND l.sequence_no>%s
                   ORDER BY l.sequence_no LIMIT %s""",
                (access["worksite_id"],cursor,batch_size),
            )
            rows=cur.fetchall()
            if not rows:
                cursor=latest; break
            for row in rows:
                cursor=int(row["sequence_no"])
                if collab_change_visible(access,row):
                    visible.append({
                        "sequence_no":row["sequence_no"],"entity_type":row["entity_type"],
                        "entity_id":row["entity_id"],"operation":row["operation"],
                        "entity_version":row.get("entity_version"),"changed_at":row.get("changed_at"),
                        "changed_by_name":row.get("changed_by_name"),
                        "from_this_device":row.get("changed_by_device")==d["device_id"],
                        "group_id":row.get("group_id"),"area_scope":row.get("area_scope") or {},
                        "data":row.get("payload") or {},
                    })
                    if len(visible)>=limit: break
        return {"next_sequence_no":cursor,"latest_sequence_no":latest,"has_more":cursor<latest,"changes":visible}


@app.post("/api/v1/collab/ack")
def collab_ack(req:CollabAckRequest, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,req.worksite_id)
        cur.execute(
            """INSERT INTO tb.collab_device_state(device_id,worksite_id,last_sequence_no,last_sync_at)
               VALUES (%s,%s,%s,now())
               ON CONFLICT (device_id,worksite_id) DO UPDATE SET
                 last_sequence_no=GREATEST(tb.collab_device_state.last_sequence_no,EXCLUDED.last_sequence_no),
                 last_sync_at=now()""",
            (d["device_id"],access["worksite_id"],req.sequence_no),
        )
        cur.execute("UPDATE tb.devices SET last_seen_at=now() WHERE id=%s",(d["device_id"],))
        conn.commit()
    return {"status":"ok","sequence_no":req.sequence_no}


