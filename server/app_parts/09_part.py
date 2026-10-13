@app.post("/admin/sections/{section_id}/edit")
async def section_edit_post(section_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    rows=q("""SELECT id,route_id,current_version FROM tb.route_sections
              WHERE id=%s AND deleted_at IS NULL""",(section_id,))
    if not rows: raise HTTPException(404,"SECTION_NOT_FOUND")
    current=rows[0]; route=route_row(str(current["route_id"]))
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    designation=(d.get("designation") or "").strip()
    data_status=(d.get("data_status") or "WITHOUT_GEOMETRY").upper()
    if data_status not in ("VERIFIED_ORIGINAL","MANUAL","GEOMETRY_WITHOUT_KM","WITHOUT_GEOMETRY"):
        data_status="WITHOUT_GEOMETRY"
    try:
        km_from=parse_km_m(d.get("km_from")); km_to=parse_km_m(d.get("km_to"))
    except ValueError as exc:
        return templates.TemplateResponse(request,"section_form.html",ctx(request,a,mode="edit",route=route,section=d,error=str(exc)),status_code=400)
    new_version=int(current["current_version"])+1
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """UPDATE tb.route_sections
               SET designation=%s,km_from_m=%s,km_to_m=%s,data_status=%s,current_version=%s
               WHERE id=%s AND deleted_at IS NULL""",
            (designation or None,km_from,km_to,data_status,new_version,section_id),
        )
        add_change_row(cur,"ROUTE_SECTION",section_id,"UPDATE",new_version,a["id"],
                       {"designation":designation,"km_from_m":km_from,"km_to_m":km_to,"data_status":data_status})
        conn.commit()
    return RedirectResponse(f"/admin/routes/{current['route_id']}",303)


@app.post("/admin/sections/{section_id}/delete")
async def section_delete(section_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("""SELECT id,route_id,designation,km_from_m,km_to_m,data_status,current_version
                       FROM tb.route_sections WHERE id=%s AND deleted_at IS NULL FOR UPDATE""",(section_id,))
        row=cur.fetchone()
        if not row: raise HTTPException(404,"SECTION_NOT_FOUND")
        cur.execute("UPDATE tb.route_sections SET deleted_at=now(),current_version=current_version+1 WHERE id=%s",(section_id,))
        add_trash_row(cur,"ROUTE_SECTION",section_id,a["id"],row,"Admin: Streckenabschnitt gelöscht")
        add_change_row(cur,"ROUTE_SECTION",section_id,"DELETE",int(row["current_version"])+1,a["id"],{"route_id":str(row["route_id"])})
        conn.commit()
    return RedirectResponse(f"/admin/routes/{row['route_id']}",303)


@app.get("/admin/stations")
def stations(request:Request):
    a=require_admin(request)
    rows=q(
        """SELECT s.id,s.station_code,s.name,s.short_name,s.description,s.created_at,s.updated_at,
                  (SELECT count(*) FROM tb.infrastructure_objects io WHERE io.station_id=s.id AND io.deleted_at IS NULL) AS object_count,
                  (SELECT count(*) FROM tb.station_routes sr JOIN tb.routes r ON r.id=sr.route_id
                     WHERE sr.station_id=s.id AND r.deleted_at IS NULL) AS route_count
           FROM tb.stations s WHERE s.deleted_at IS NULL
           ORDER BY s.name,s.station_code"""
    )
    notice=None; error=None
    qp=request.query_params
    if qp.get("saved")=="1": notice="Bahnhof wurde gespeichert."
    elif qp.get("deleted")=="1": notice="Bahnhof wurde in den Papierkorb verschoben."
    elif qp.get("blocked")=="1": error="Bahnhof kann noch nicht gelöscht werden, weil verknüpfte Strecken, Objekte, Originaldateien oder Baustellen vorhanden sind."
    return templates.TemplateResponse(request,"stations.html",ctx(request,a,rows=rows,notice=notice,error=error))


@app.get("/admin/stations/new")
def station_new_get(request:Request):
    a=require_admin(request)
    return templates.TemplateResponse(request,"station_form.html",ctx(request,a,mode="new",station={},routes=active_routes(),selected_routes=set()))


@app.post("/admin/stations/new")
async def station_new_post(request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    values=complete_station_values(station_form_values(d)); selected={k[6:] for k,v in d.items() if k.startswith("route_") and v=="1"}
    if not values["name"]:
        return templates.TemplateResponse(request,"station_form.html",ctx(request,a,mode="new",station=values,routes=active_routes(),selected_routes=selected,error="Bahnhofsname ist erforderlich."),status_code=400)
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute(
                """INSERT INTO tb.stations(station_code,name,short_name,description)
                   VALUES (%s,%s,%s,%s) RETURNING id,current_version""",
                (values["station_code"] or None,values["name"],values["short_name"] or None,values["description"] or None),
            )
            created_station=cur.fetchone(); sid=created_station["id"]
            for rid in selected:
                cur.execute("""INSERT INTO tb.station_routes(station_id,route_id)
                               SELECT %s,id FROM tb.routes WHERE id=%s AND deleted_at IS NULL
                               ON CONFLICT DO NOTHING""",(sid,rid))
            add_change_row(cur,"STATION",sid,"CREATE",created_station["current_version"],a["id"],values)
            conn.commit()
    except UniqueViolation:
        return templates.TemplateResponse(request,"station_form.html",ctx(request,a,mode="new",station=values,routes=active_routes(),selected_routes=selected,error="Dieses Bahnhofskürzel wird bereits verwendet."),status_code=400)
    return RedirectResponse("/admin/stations?saved=1",303)


@app.get("/admin/stations/{station_id}")
def station_detail(station_id:str, request:Request):
    a=require_admin(request); station=station_row(station_id)
    if not station: raise HTTPException(404,"STATION_NOT_FOUND")
    routes=q(
        """SELECT r.id,r.route_number,r.name FROM tb.station_routes sr
           JOIN tb.routes r ON r.id=sr.route_id
           WHERE sr.station_id=%s AND r.deleted_at IS NULL ORDER BY r.route_number""",(station_id,)
    )
    objects=q(
        """SELECT io.id,io.object_type,io.designation,io.km_m,io.data_status,io.current_version,
                  r.route_number,r.name AS route_name
           FROM tb.infrastructure_objects io
           LEFT JOIN tb.routes r ON r.id=io.route_id AND r.deleted_at IS NULL
           WHERE io.station_id=%s AND io.deleted_at IS NULL
           ORDER BY io.object_type,io.designation""",(station_id,)
    )
    for o in objects: o["km_text"]=format_km_m(o.get("km_m"))
    return templates.TemplateResponse(request,"station_detail.html",ctx(request,a,station=station,routes=routes,objects=objects))


@app.get("/admin/stations/{station_id}/edit")
def station_edit_get(station_id:str, request:Request):
    a=require_admin(request); station=station_row(station_id)
    if not station: raise HTTPException(404,"STATION_NOT_FOUND")
    selected={str(r["route_id"]) for r in q("SELECT route_id FROM tb.station_routes WHERE station_id=%s",(station_id,))}
    return templates.TemplateResponse(request,"station_form.html",ctx(request,a,mode="edit",station=station,routes=active_routes(),selected_routes=selected))


@app.post("/admin/stations/{station_id}/edit")
async def station_edit_post(station_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    if not station_row(station_id): raise HTTPException(404,"STATION_NOT_FOUND")
    values=complete_station_values(station_form_values(d)); selected={k[6:] for k,v in d.items() if k.startswith("route_") and v=="1"}
    if not values["name"]:
        values["id"]=station_id
        return templates.TemplateResponse(request,"station_form.html",ctx(request,a,mode="edit",station=values,routes=active_routes(),selected_routes=selected,error="Bahnhofsname ist erforderlich."),status_code=400)
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute("SELECT current_version FROM tb.stations WHERE id=%s AND deleted_at IS NULL FOR UPDATE",(station_id,))
            locked=cur.fetchone()
            if not locked: raise HTTPException(404,"STATION_NOT_FOUND")
            new_version=int(locked["current_version"])+1
            cur.execute(
                """UPDATE tb.stations SET station_code=%s,name=%s,short_name=%s,description=%s,current_version=%s
                   WHERE id=%s AND deleted_at IS NULL""",
                (values["station_code"] or None,values["name"],values["short_name"] or None,values["description"] or None,new_version,station_id),
            )
            cur.execute("DELETE FROM tb.station_routes WHERE station_id=%s",(station_id,))
            for rid in selected:
                cur.execute("""INSERT INTO tb.station_routes(station_id,route_id)
                               SELECT %s,id FROM tb.routes WHERE id=%s AND deleted_at IS NULL
                               ON CONFLICT DO NOTHING""",(station_id,rid))
            add_change_row(cur,"STATION",station_id,"UPDATE",new_version,a["id"],values)
            conn.commit()
    except UniqueViolation:
        values["id"]=station_id
        return templates.TemplateResponse(request,"station_form.html",ctx(request,a,mode="edit",station=values,routes=active_routes(),selected_routes=selected,error="Dieses Bahnhofskürzel wird bereits verwendet."),status_code=400)
    return RedirectResponse("/admin/stations?saved=1",303)


@app.post("/admin/stations/{station_id}/delete")
async def station_delete(station_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("""SELECT id,station_code,name,short_name,description,current_version FROM tb.stations
                       WHERE id=%s AND deleted_at IS NULL FOR UPDATE""",(station_id,))
        station=cur.fetchone()
        if not station: raise HTTPException(404,"STATION_NOT_FOUND")
        cur.execute(
            """SELECT
               (SELECT count(*) FROM tb.infrastructure_objects WHERE station_id=%s AND deleted_at IS NULL) objects,
               (SELECT count(*) FROM tb.station_routes WHERE station_id=%s) routes,
               (SELECT count(*) FROM tb.source_files WHERE station_id=%s) files,
               (SELECT count(*) FROM tb.worksites WHERE station_id=%s) worksites""",
            (station_id,station_id,station_id,station_id),
        )
        dep=cur.fetchone()
        if any(int(dep[k])>0 for k in ("objects","routes","files","worksites")):
            return RedirectResponse("/admin/stations?blocked=1",303)
        new_version=int(station["current_version"])+1
        cur.execute("UPDATE tb.stations SET deleted_at=now(),current_version=%s WHERE id=%s",(new_version,station_id))
        add_trash_row(cur,"STATION",station_id,a["id"],station,"Admin: Bahnhof gelöscht")
        add_change_row(cur,"STATION",station_id,"DELETE",new_version,a["id"],{"name":station["name"]})
        conn.commit()
    return RedirectResponse("/admin/stations?deleted=1",303)


@app.get("/admin/stations/{station_id}/objects/new")
def object_new_get(station_id:str, request:Request):
    a=require_admin(request); station=station_row(station_id)
    if not station: raise HTTPException(404,"STATION_NOT_FOUND")
    return templates.TemplateResponse(request,"object_form.html",ctx(request,a,mode="new",station=station,obj={},routes=active_routes(),properties_json="{}"))


@app.post("/admin/stations/{station_id}/objects/new")
async def object_new_post(station_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    station=station_row(station_id)
    if not station: raise HTTPException(404,"STATION_NOT_FOUND")
    object_type=(d.get("object_type") or "TRACK").upper()
    designation=(d.get("designation") or "").strip()
    route_id=(d.get("route_id") or "").strip() or None
    data_status=(d.get("data_status") or "WITHOUT_GEOMETRY").upper()
    if object_type not in ("TRACK","SWITCH","DKW","CROSSING","OBJECT"):
        object_type="OBJECT"
    if data_status not in ("VERIFIED_ORIGINAL","MANUAL","GEOMETRY_WITHOUT_KM","WITHOUT_GEOMETRY"):
        data_status="WITHOUT_GEOMETRY"
    try:
        km_m=parse_km_m(d.get("km_m"))
        properties=json.loads((d.get("properties_json") or "{}").strip() or "{}")
        if not isinstance(properties,dict): raise ValueError("Eigenschaften müssen ein JSON-Objekt sein.")
    except (ValueError,json.JSONDecodeError) as exc:
        return templates.TemplateResponse(request,"object_form.html",ctx(request,a,mode="new",station=station,obj=d,routes=active_routes(),properties_json=d.get("properties_json","{}"),error=str(exc)),status_code=400)
    if not designation:
        return templates.TemplateResponse(request,"object_form.html",ctx(request,a,mode="new",station=station,obj=d,routes=active_routes(),properties_json=d.get("properties_json","{}"),error="Bezeichnung ist erforderlich."),status_code=400)
    if route_id and not route_row(route_id):
        raise HTTPException(400,"ROUTE_NOT_FOUND")
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.infrastructure_objects(route_id,station_id,object_type,designation,km_m,data_status,properties)
               VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb)
               RETURNING id,current_version""",
            (route_id,station_id,object_type,designation,km_m,data_status,json.dumps(properties,ensure_ascii=False)),
        )
        row=cur.fetchone()
        payload={"route_id":route_id,"station_id":station_id,"object_type":object_type,"designation":designation,
                 "km_m":km_m,"data_status":data_status,"properties":properties}
        add_change_row(cur,"OBJECT",row["id"],"CREATE",row["current_version"],a["id"],payload)
        conn.commit()
    return RedirectResponse(f"/admin/stations/{station_id}",303)


@app.get("/admin/objects/{object_id}/edit")
def object_edit_get(object_id:str, request:Request):
    a=require_admin(request)
    rows=q("""SELECT id,route_id,station_id,object_type,designation,km_m,data_status,properties,current_version
              FROM tb.infrastructure_objects WHERE id=%s AND deleted_at IS NULL""",(object_id,))
    if not rows: raise HTTPException(404,"OBJECT_NOT_FOUND")
    obj=rows[0]; station=station_row(str(obj["station_id"])) if obj.get("station_id") else None
    if not station: raise HTTPException(400,"OBJECT_WITHOUT_STATION_NOT_EDITABLE_HERE")
    obj["km_m_text"]=format_km_m(obj.get("km_m"))
    properties_json=json.dumps(obj.get("properties") or {},ensure_ascii=False,indent=2)
    return templates.TemplateResponse(request,"object_form.html",ctx(request,a,mode="edit",station=station,obj=obj,routes=active_routes(),properties_json=properties_json))

