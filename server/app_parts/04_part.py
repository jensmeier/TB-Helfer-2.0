@app.post("/api/v1/device/login")
def device_login(payload:DeviceLogin):
    raw_key=payload.key.strip()
    normalized_key=raw_key.upper()
    key_id=activation_key_id(normalized_key)

    # Neue automatisch erzeugte TBH-Keys werden in EINER DB-Transaktion
    # geprüft, gesperrt, zur Gerätebindung verwendet und anschließend
    # verbraucht. So kann derselbe QR-Code nicht parallel mehrfach benutzt werden.
    if key_id:
        with db() as conn, conn.cursor() as cur:
            cur.execute(
                """SELECT id,full_name,company,email,role,status,access_key_hash,access_key_id,
                          activation_key_used_at,access_valid_until
                   FROM tb.users
                   WHERE role='USER' AND access_key_id=%s
                   FOR UPDATE""",
                (key_id,),
            )
            user=cur.fetchone()
            if not user or not verify_key(normalized_key,user["access_key_hash"]):
                raise HTTPException(401,"LOGIN_INVALID")
            if user["activation_key_used_at"] is not None:
                raise HTTPException(401,"ACTIVATION_KEY_USED")
            if user["status"]!="ACTIVE":
                raise HTTPException(403,"USER_DISABLED")
            if access_expired(user.get("access_valid_until")):
                raise HTTPException(403,"USER_EXPIRED")

            cur.execute(
                """SELECT id,user_id,device_uid,device_name,platform,os_version,app_version,
                          bound_at,last_seen_at
                   FROM tb.devices
                   WHERE user_id=%s AND revoked_at IS NULL""",
                (user["id"],),
            )
            active=cur.fetchall()

            if active:
                d=active[0]
                if d["device_uid"] != payload.device_uid:
                    raise HTTPException(409,"DEVICE_ALREADY_BOUND")
                cur.execute(
                    """UPDATE tb.devices
                       SET device_name=%s,os_version=%s,app_version=%s,last_seen_at=now()
                       WHERE id=%s""",
                    (payload.device_name,payload.os_version,payload.app_version,d["id"]),
                )
                device_id=d["id"]
            else:
                cur.execute(
                    """SELECT id,user_id,revoked_at
                       FROM tb.devices
                       WHERE device_uid=%s""",
                    (payload.device_uid,),
                )
                existing=cur.fetchall()
                if existing and existing[0]["user_id"] != user["id"]:
                    raise HTTPException(409,"DEVICE_IN_USE")

                if existing:
                    device_id=existing[0]["id"]
                    cur.execute(
                        """UPDATE tb.devices
                           SET revoked_at=NULL,bound_at=now(),last_seen_at=now(),
                               device_name=%s,platform='ANDROID',os_version=%s,app_version=%s
                           WHERE id=%s""",
                        (payload.device_name,payload.os_version,payload.app_version,device_id),
                    )
                else:
                    cur.execute(
                        """INSERT INTO tb.devices
                           (user_id,device_uid,device_name,platform,os_version,app_version,bound_at,last_seen_at)
                           VALUES (%s,%s,%s,'ANDROID',%s,%s,now(),now())
                           RETURNING id""",
                        (user["id"],payload.device_uid,payload.device_name,
                         payload.os_version,payload.app_version),
                    )
                    device_id=cur.fetchone()["id"]

            # Erst NACH erfolgreicher Gerätebindung wird der QR/Key verbraucht.
            cur.execute(
                """UPDATE tb.users
                   SET activation_key_used_at=now(),updated_at=now()
                   WHERE id=%s AND activation_key_used_at IS NULL""",
                (user["id"],),
            )
            if cur.rowcount != 1:
                raise HTTPException(401,"ACTIVATION_KEY_USED")

            conn.commit()

    else:
        # Übergangspfad für alte manuell angelegte Keys.
        # Diese Legacy-Keys bleiben vorerst wie bisher nutzbar.
        user=user_for_activation_key(raw_key)
        if not user:
            raise HTTPException(401,"LOGIN_INVALID")
        if user["status"]!="ACTIVE":
            raise HTTPException(403,"USER_DISABLED")
        if access_expired(user.get("access_valid_until")):
            raise HTTPException(403,"USER_EXPIRED")

        active=q("""SELECT id,user_id,device_uid,device_name,platform,os_version,app_version,bound_at,last_seen_at
                    FROM tb.devices WHERE user_id=%s AND revoked_at IS NULL""",(user["id"],))
        if active:
            d=active[0]
            if d["device_uid"] != payload.device_uid:
                raise HTTPException(409,"DEVICE_ALREADY_BOUND")
            with db() as conn, conn.cursor() as cur:
                cur.execute("""UPDATE tb.devices
                               SET device_name=%s,os_version=%s,app_version=%s,last_seen_at=now()
                               WHERE id=%s""",
                            (payload.device_name,payload.os_version,payload.app_version,d["id"]))
                conn.commit()
            device_id=d["id"]
        else:
            existing=q("""SELECT id,user_id,revoked_at FROM tb.devices WHERE device_uid=%s""",(payload.device_uid,))
            if existing and existing[0]["user_id"] != user["id"]:
                raise HTTPException(409,"DEVICE_IN_USE")

            with db() as conn, conn.cursor() as cur:
                if existing:
                    device_id=existing[0]["id"]
                    cur.execute("""UPDATE tb.devices
                                   SET revoked_at=NULL,bound_at=now(),last_seen_at=now(),
                                       device_name=%s,platform='ANDROID',os_version=%s,app_version=%s
                                   WHERE id=%s""",
                                (payload.device_name,payload.os_version,payload.app_version,device_id))
                else:
                    cur.execute("""INSERT INTO tb.devices
                                   (user_id,device_uid,device_name,platform,os_version,app_version,bound_at,last_seen_at)
                                   VALUES (%s,%s,%s,'ANDROID',%s,%s,now(),now()) RETURNING id""",
                                (user["id"],payload.device_uid,payload.device_name,payload.os_version,payload.app_version))
                    device_id=cur.fetchone()["id"]
                conn.commit()

    token=make_device_token(user["id"],device_id)
    return {
        "status":"ok",
        "token":token,
        "user":{
            "id":str(user["id"]),
            "name":user["full_name"],
            "company":user.get("company"),
            "email":user["email"],
            "role":user["role"],
            "access_valid_until":user.get("access_valid_until")
        },
        "device":{
            "id":str(device_id),
            "uid":payload.device_uid,
            "name":payload.device_name,
            "platform":"ANDROID",
            "os_version":payload.os_version,
            "app_version":payload.app_version
        }
    }

@app.get("/api/v1/device/me")
def device_me(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        cur.execute("UPDATE tb.devices SET last_seen_at=now() WHERE id=%s",(d["device_id"],))
        conn.commit()
    return {
        "status":"ok",
        "user":{
            "id":str(d["user_id"]),
            "name":d["full_name"],
            "company":d.get("company"),
            "email":d["email"],
            "role":d["role"],
            "access_valid_until":d.get("access_valid_until")
        },
        "device":{
            "id":str(d["device_id"]),
            "uid":d["device_uid"],
            "name":d["device_name"],
            "platform":d["platform"],
            "os_version":d["os_version"],
            "app_version":d["app_version"]
        }
    }


# ---------------------------------------------------------------------------
# Sync-Katalog: nur lesender erster Baustein für Handy <-> NAS
# ---------------------------------------------------------------------------

@app.get("/api/v1/sync/catalog")
def sync_catalog(authorization: str | None = Header(default=None)):
    require_device(authorization)

    routes=q(
        """SELECT r.id,r.route_number,r.name,r.description,r.current_version,r.updated_at,
                  count(DISTINCT rs.id) AS section_count,
                  count(DISTINCT io.id) AS object_count
           FROM tb.routes r
           LEFT JOIN tb.route_sections rs ON rs.route_id=r.id AND rs.deleted_at IS NULL
           LEFT JOIN tb.infrastructure_objects io ON io.route_id=r.id AND io.deleted_at IS NULL
           WHERE r.deleted_at IS NULL
           GROUP BY r.id,r.route_number,r.name,r.description,r.current_version,r.updated_at
           ORDER BY r.route_number,r.name"""
    )

    stations=q(
        """SELECT s.id,s.station_code,s.name,s.short_name,s.description,s.updated_at,
                  count(DISTINCT io.id) AS object_count
           FROM tb.stations s
           LEFT JOIN tb.infrastructure_objects io ON io.station_id=s.id AND io.deleted_at IS NULL
           WHERE s.deleted_at IS NULL
           GROUP BY s.id,s.station_code,s.name,s.short_name,s.description,s.current_version,s.updated_at
           ORDER BY s.name,s.station_code"""
    )

    return {
        "status":"ok",
        "server_version":SERVER_VERSION,
        "routes":routes,
        "stations":stations
    }


@app.get("/api/v1/sync/routes/{route_id}/sections")
def sync_route_sections(route_id:str, authorization: str | None = Header(default=None)):
    require_device(authorization)

    route=q(
        """SELECT id,route_number,name,description,current_version,updated_at
           FROM tb.routes WHERE id=%s AND deleted_at IS NULL""",
        (route_id,),
    )
    if not route:
        raise HTTPException(404,"ROUTE_NOT_FOUND")

    sections=q(
        """SELECT id,route_id,designation,km_from_m,km_to_m,km_min_m,km_max_m,
                  data_status,current_version,created_at,updated_at,
                  CASE WHEN geom IS NULL THEN NULL ELSE ST_AsGeoJSON(geom)::jsonb END AS geom
           FROM tb.route_sections
           WHERE route_id=%s AND deleted_at IS NULL
           ORDER BY COALESCE(km_min_m,km_from_m),COALESCE(km_max_m,km_to_m),designation""",
        (route_id,),
    )
    return {"status":"ok","route":route[0],"sections":sections}


@app.get("/api/v1/sync/stations/{station_id}/objects")
def sync_station_objects(
    station_id:str,
    authorization: str | None = Header(default=None),
    route_id:str | None = None,
    object_type:str | None = None,
):
    require_device(authorization)

    station=q(
        """SELECT id,station_code,name,short_name,description,current_version,updated_at
           FROM tb.stations WHERE id=%s AND deleted_at IS NULL""",
        (station_id,),
    )
    if not station:
        raise HTTPException(404,"STATION_NOT_FOUND")

    where=["io.station_id=%s"]
    params=[station_id]

    if route_id:
        where.append("io.route_id=%s")
        params.append(route_id)
    if object_type:
        where.append("upper(io.object_type)=upper(%s)")
        params.append(object_type)

    sql=f"""SELECT io.id,io.route_id,io.station_id,io.object_type,io.designation,
                   io.km_m,io.data_status,io.properties,io.current_version,
                   io.created_at,io.updated_at,
                   CASE WHEN io.geom IS NULL THEN NULL ELSE ST_AsGeoJSON(io.geom)::jsonb END AS geom,
                   r.route_number,r.name AS route_name
            FROM tb.infrastructure_objects io
            LEFT JOIN tb.routes r ON r.id=io.route_id AND r.deleted_at IS NULL
            WHERE {' AND '.join(where)} AND io.deleted_at IS NULL
            ORDER BY io.object_type,io.designation,io.km_m NULLS LAST"""

    objects=q(sql,tuple(params))
    return {"status":"ok","station":station[0],"objects":objects}


@app.get("/api/v1/sync/objects/{object_id}")
def sync_object(object_id:str, authorization: str | None = Header(default=None)):
    require_device(authorization)

    rows=q(
        """SELECT io.id,io.route_id,io.station_id,io.object_type,io.designation,
                  io.km_m,io.data_status,io.properties,io.current_version,
                  io.created_at,io.updated_at,
                  CASE WHEN io.geom IS NULL THEN NULL ELSE ST_AsGeoJSON(io.geom)::jsonb END AS geom,
                  r.route_number,r.name AS route_name,
                  s.station_code,s.name AS station_name,s.short_name AS station_short_name
           FROM tb.infrastructure_objects io
           LEFT JOIN tb.routes r ON r.id=io.route_id AND r.deleted_at IS NULL
           LEFT JOIN tb.stations s ON s.id=io.station_id AND s.deleted_at IS NULL
           WHERE io.id=%s AND io.deleted_at IS NULL""",
        (object_id,),
    )
    if not rows:
        raise HTTPException(404,"OBJECT_NOT_FOUND")
    return {"status":"ok","object":rows[0]}


# ---------------------------------------------------------------------------
# Inkrementeller Sync ab 0.7.0 (Schema 8/9)
# ---------------------------------------------------------------------------

def general_entity_for_sync(cur, entity_type, entity_id):
    t=(entity_type or "").upper()
    eid=parse_uuid_value(entity_id,"ENTITY_ID_INVALID")
    if t in ("SECTION","ROUTE_SECTION"):
        cur.execute(
            """SELECT id,route_id,designation,km_from_m,km_to_m,km_min_m,km_max_m,
                      data_status,current_version,created_at,updated_at,deleted_at,
                      CASE WHEN geom IS NULL THEN NULL ELSE ST_AsGeoJSON(geom)::jsonb END AS geom
               FROM tb.route_sections WHERE id=%s""",(eid,))
    elif t=="OBJECT":
        cur.execute(
            """SELECT id,route_id,station_id,object_type,designation,km_m,data_status,properties,
                      current_version,created_at,updated_at,deleted_at,
                      CASE WHEN geom IS NULL THEN NULL ELSE ST_AsGeoJSON(geom)::jsonb END AS geom
               FROM tb.infrastructure_objects WHERE id=%s""",(eid,))
    elif t=="ROUTE":
        cur.execute("""SELECT id,route_number,name,description,current_version,created_at,updated_at,deleted_at
                       FROM tb.routes WHERE id=%s""",(eid,))
    elif t=="STATION":
        cur.execute("""SELECT id,station_code,name,short_name,description,current_version,created_at,updated_at,deleted_at
                       FROM tb.stations WHERE id=%s""",(eid,))
    else:
        return None
    return cur.fetchone()


def sync_type_for_client(value):
    value=(value or "").upper()
    return "SECTION" if value=="ROUTE_SECTION" else value


@app.get("/api/v1/sync/changes")
def sync_changes(after:int=0, limit:int=100, authorization: str | None = Header(default=None)):
    require_device(authorization)
    after=max(0,int(after)); limit=max(1,min(int(limit),500))
    with db() as conn, conn.cursor() as cur:
        cur.execute("SELECT COALESCE(max(sequence_no),0) AS latest FROM tb.change_log")
        latest=int(cur.fetchone()["latest"])
        cur.execute(
            """SELECT sequence_no,entity_type,entity_id,operation,entity_version,changed_at,payload
               FROM tb.change_log WHERE sequence_no>%s ORDER BY sequence_no LIMIT %s""",
            (after,limit),
        )
        rows=cur.fetchall()
        changes=[]
        for row in rows:
            client_type=sync_type_for_client(row["entity_type"])
            data=row.get("payload") or {}
            if row["operation"] != "DELETE":
                current=general_entity_for_sync(cur,client_type,row["entity_id"])
                if current is not None:
                    data=current
            changes.append({
                "sequence_no":row["sequence_no"],"entity_type":client_type,
                "entity_id":row["entity_id"],"operation":row["operation"],
                "entity_version":row.get("entity_version"),"changed_at":row.get("changed_at"),
                "data":data,
            })
        next_sequence=int(rows[-1]["sequence_no"]) if rows else after
        return {
            "next_sequence_no":next_sequence,
            "latest_sequence_no":latest,
            "has_more":next_sequence < latest,
            "changes":changes,
        }


@app.post("/api/v1/sync/ack")
def sync_ack(payload:SyncAckRequest, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.sync_device_state(device_id,last_sequence_no,last_sync_at)
               VALUES (%s,%s,now())
               ON CONFLICT (device_id) DO UPDATE SET
                 last_sequence_no=GREATEST(tb.sync_device_state.last_sequence_no,EXCLUDED.last_sequence_no),
                 last_sync_at=now()""",
            (d["device_id"],payload.sequence_no),
        )
        cur.execute("UPDATE tb.devices SET last_seen_at=now() WHERE id=%s",(d["device_id"],))
        conn.commit()
    return {"status":"ok","sequence_no":payload.sequence_no}


def store_sync_receipt(cur,device_id,client_change_id,http_status,response):
    cur.execute(
        """INSERT INTO tb.sync_mutation_receipts(device_id,client_change_id,http_status,response)
           VALUES (%s,%s,%s,%s::jsonb)""",
        (device_id,client_change_id,http_status,json.dumps(jsonable_encoder(response),ensure_ascii=False)),
    )


