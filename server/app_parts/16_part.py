# Schema-10-Ergänzung: eine lokale Hauptbaustelle kann vom ersten verantwortlichen TB
# selbst auf dem Server angelegt werden. Der Admin muss damit nicht bei jeder Baustelle
# Benutzer/Teams manuell zusammenklicken.


@app.post("/api/v1/collab/worksites")
async def collab_create_worksite(request:Request, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    if d.get("company_id") is None:
        raise HTTPException(403,"COMPANY_REQUIRED")
    try:
        payload=await request.json()
    except Exception:
        raise HTTPException(400,"WORKSITE_JSON_INVALID")
    if not isinstance(payload,dict):
        raise HTTPException(400,"WORKSITE_JSON_INVALID")

    client_key=str(payload.get("client_worksite_key") or "").strip()
    name=str(payload.get("name") or "").strip()
    designation=str(payload.get("designation") or "").strip() or None
    route_number=str(payload.get("route_number") or "").strip() or None
    station_name=str(payload.get("station_name") or "").strip() or None
    station_code=str(payload.get("station_code") or "").strip().upper() or None

    if not client_key or len(client_key)>240:
        raise HTTPException(400,"CLIENT_WORKSITE_KEY_INVALID")
    if not name or len(name)>300:
        raise HTTPException(400,"WORKSITE_NAME_INVALID")
    if designation and len(designation)>500:
        designation=designation[:500]

    with db() as conn, conn.cursor() as cur:
        advisory_xact_lock(cur,f"mobile-worksite:{d['user_id']}:{client_key}")

        # Idempotenz bei Netzabbruch: derselbe lokale Baustellenschlüssel liefert immer
        # dieselbe Server-Baustelle zurück und erzeugt niemals eine zweite Kopie.
        cur.execute(
            """SELECT o.worksite_id,w.name,w.designation,w.company_id,
                      gm.group_id,gm.member_role
               FROM tb.worksite_mobile_origins o
               JOIN tb.worksites w ON w.id=o.worksite_id
               LEFT JOIN tb.worksite_group_members gm
                 ON gm.worksite_id=o.worksite_id AND gm.user_id=o.user_id
               WHERE o.user_id=%s AND o.client_worksite_key=%s""",
            (d["user_id"],client_key),
        )
        existing=cur.fetchone()
        if existing:
            if existing.get("company_id")!=d.get("company_id"):
                raise HTTPException(409,"WORKSITE_COMPANY_MISMATCH")
            conn.commit()
            return {
                "status":"ok","created":False,
                "worksite_id":str(existing["worksite_id"]),
                "name":existing.get("name"),"designation":existing.get("designation"),
                "group_id":str(existing["group_id"]) if existing.get("group_id") else None,
                "member_role":existing.get("member_role") or "LEAD",
            }

        route_id=None; station_id=None
        if route_number:
            cur.execute(
                """SELECT id FROM tb.routes
                   WHERE deleted_at IS NULL AND btrim(route_number)=btrim(%s)
                   ORDER BY updated_at DESC NULLS LAST,id LIMIT 1""",
                (route_number,),
            )
            hit=cur.fetchone(); route_id=hit["id"] if hit else None

        if station_code or station_name:
            if station_code:
                cur.execute(
                    """SELECT id FROM tb.stations
                       WHERE deleted_at IS NULL AND upper(btrim(COALESCE(station_code,'')))=upper(btrim(%s))
                       ORDER BY updated_at DESC NULLS LAST,id LIMIT 1""",
                    (station_code,),
                )
                hit=cur.fetchone(); station_id=hit["id"] if hit else None
            if station_id is None and station_name:
                cur.execute(
                    """SELECT id FROM tb.stations
                       WHERE deleted_at IS NULL AND lower(btrim(name))=lower(btrim(%s))
                       ORDER BY updated_at DESC NULLS LAST,id LIMIT 1""",
                    (station_name,),
                )
                hit=cur.fetchone(); station_id=hit["id"] if hit else None

        # Die produktive worksites-Tabelle stammt bereits aus Schema 7. Name/Firma sind die
        # einzigen fachlich zwingenden Werte für die Self-Service-Anlage; bestehende Defaults
        # für Status/Typ bleiben deshalb unangetastet. Route/Bahnhof werden nur auf bekannte
        # vorhandene Server-IDs gesetzt, niemals als neue Bahnhofsliste erfunden.
        cur.execute(
            """INSERT INTO tb.worksites(name,designation,company_id,route_id,station_id)
               VALUES (%s,%s,%s,%s,%s)
               RETURNING id,name,designation,company_id""",
            (name,designation,d["company_id"],route_id,station_id),
        )
        ws=cur.fetchone()
        cur.execute(
            """INSERT INTO tb.worksite_groups(worksite_id,lead_user_id,label)
               VALUES (%s,%s,%s)
               RETURNING id,lead_name_snapshot,lead_name_ordinal""",
            (ws["id"],d["user_id"],"TB "+str(d.get("full_name") or "")),
        )
        group=cur.fetchone()
        cur.execute(
            """INSERT INTO tb.worksite_group_members(worksite_id,group_id,user_id,member_role,area_scope)
               VALUES (%s,%s,%s,'LEAD','{"all":true}'::jsonb)""",
            (ws["id"],group["id"],d["user_id"]),
        )
        cur.execute(
            """INSERT INTO tb.worksite_mobile_origins(user_id,client_worksite_key,worksite_id)
               VALUES (%s,%s,%s)""",
            (d["user_id"],client_key,ws["id"]),
        )
        conn.commit()

    audit_event(
        "WORKSITE_MOBILE_CREATED",d["user_id"],d["device_id"],"WORKSITE",ws["id"],
        {"client_worksite_key":client_key,"route_number":route_number,"station_name":station_name}
    )
    return {
        "status":"ok","created":True,
        "worksite_id":str(ws["id"]),"name":ws.get("name"),"designation":ws.get("designation"),
        "group_id":str(group["id"]),"member_role":"LEAD",
        "lead_name_snapshot":group.get("lead_name_snapshot"),
        "lead_name_ordinal":int(group.get("lead_name_ordinal") or 1),
    }
