@app.post("/admin/devices/{device_id}/revoke")
async def device_revoke(device_id:str, request:Request):
    a=require_admin(request)
    d=await form(request)
    check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("UPDATE tb.devices SET revoked_at=COALESCE(revoked_at,now()) WHERE id=%s",(device_id,))
        conn.commit()
    return RedirectResponse("/admin/devices",303)


@app.get("/admin/system")
def system_status(request:Request):
    a=require_admin(request)
    settings=get_system_settings()
    snap=system_snapshot(settings)
    notice=None; error=None
    qp=request.query_params
    if qp.get("saved")=="1": notice="Systemeinstellungen wurden gespeichert."
    elif qp.get("backup")=="ok": notice="Datenbankbackup wurde erfolgreich erstellt."
    elif qp.get("backup")=="fail": error="Datenbankbackup ist fehlgeschlagen. Details stehen beim letzten Backuplauf."
    elif qp.get("mail")=="ok": notice="Testbericht wurde erfolgreich gesendet."
    elif qp.get("mail")=="fail": error="Testbericht konnte nicht gesendet werden. Details stehen beim letzten E-Mail-Lauf."
    return templates.TemplateResponse(
        request,"system_status.html",
        ctx(request,a,snap=snap,settings=settings,smtp=smtp_status(),notice=notice,error=error,human_bytes=human_bytes,server_version=SERVER_VERSION)
    )


@app.post("/admin/system/settings")
async def system_settings_post(request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    try:
        frequency=(d.get("report_frequency") or "WEEKLY").upper()
        if frequency not in ("DAILY","WEEKLY","MONTHLY"):
            raise ValueError("Ungültige Berichtshäufigkeit")
        report_hour=int(d.get("report_hour") or 8)
        weekday=int(d.get("report_weekday") or 0)
        monthday=int(d.get("report_monthday") or 1)
        warning_days=int(d.get("expiry_warning_days") or 30)
        storage_warning=int(d.get("storage_warning_percent") or 85)
        backup_hour=int(d.get("backup_hour") or 3)
        retention=int(d.get("backup_retention_days") or 30)
        if not 0<=report_hour<=23 or not 0<=backup_hour<=23 or not 0<=weekday<=6 or not 1<=monthday<=28:
            raise ValueError("Uhrzeit/Wochentag/Monatstag ist ungültig")
        if not 1<=warning_days<=365 or not 50<=storage_warning<=99 or not 1<=retention<=3650:
            raise ValueError("Warn- oder Aufbewahrungswert ist ungültig")
        timezone_name=(d.get("report_timezone") or "Europe/Berlin").strip()
        ZoneInfo(timezone_name)
    except Exception as exc:
        settings=get_system_settings(); snap=system_snapshot(settings)
        return templates.TemplateResponse(
            request,"system_status.html",
            ctx(request,a,snap=snap,settings=settings,smtp=smtp_status(),error=f"Einstellungen nicht gespeichert: {exc}",human_bytes=human_bytes,server_version=SERVER_VERSION),
            status_code=400
        )
    recipients=(d.get("report_recipients") or "").strip()
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """UPDATE tb.system_settings SET
                 report_enabled=%s,report_frequency=%s,report_recipients=%s,report_hour=%s,
                 report_weekday=%s,report_monthday=%s,report_timezone=%s,expiry_warning_days=%s,
                 storage_warning_percent=%s,backup_enabled=%s,backup_hour=%s,backup_retention_days=%s,
                 updated_at=now() WHERE id=1""",
            (
                d.get("report_enabled")=="1",frequency,recipients or None,report_hour,
                weekday,monthday,timezone_name,warning_days,storage_warning,
                d.get("backup_enabled")=="1",backup_hour,retention,
            ),
        )
        conn.commit()
    audit_event("SYSTEM_SETTINGS_UPDATED",user_id=a["id"],entity_type="SYSTEM")
    return RedirectResponse("/admin/system?saved=1",303)


@app.post("/admin/system/backup")
async def system_backup_now(request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    ok,detail=run_database_backup("MANUAL",a["id"])
    audit_event("DATABASE_BACKUP_SUCCESS" if ok else "DATABASE_BACKUP_FAILED",user_id=a["id"],entity_type="BACKUP",details={"detail":detail})
    return RedirectResponse("/admin/system?backup="+("ok" if ok else "fail"),303)


@app.post("/admin/system/test-email")
async def system_test_email(request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    ok,detail=send_system_report("TEST","MANUAL",a["id"])
    audit_event("SYSTEM_EMAIL_TEST_SUCCESS" if ok else "SYSTEM_EMAIL_TEST_FAILED",user_id=a["id"],entity_type="SYSTEM",details={"detail":detail})
    return RedirectResponse("/admin/system?mail="+("ok" if ok else "fail"),303)



def parse_km_m(raw):
    value=(raw or "").strip()
    if not value:
        return None
    value=value.replace(" ","").replace(",",".")
    if "+" in value:
        parts=value.split("+",1)
        try:
            return round(float(parts[0])*1000.0 + float(parts[1]),3)
        except ValueError:
            raise ValueError("Kilometer bitte z. B. als 70+123,456 oder 70123,456 eingeben.")
    try:
        return round(float(value),3)
    except ValueError:
        raise ValueError("Kilometer bitte z. B. als 70+123,456 oder 70123,456 eingeben.")


def format_km_m(value):
    if value is None:
        return ""
    n=float(value)
    km=int(n//1000)
    rest=n-km*1000
    return f"{km}+{rest:07.3f}".replace(".",",")


def add_trash_row(cur, entity_type, entity_id, deleted_by, snapshot, reason=None):
    cur.execute(
        """INSERT INTO tb.trash(entity_type,entity_id,deleted_by,reason,snapshot)
           VALUES (%s,%s,%s,%s,%s::jsonb)""",
        (entity_type,entity_id,deleted_by,reason,json.dumps(snapshot,ensure_ascii=False,default=str)),
    )


def add_change_row(cur, entity_type, entity_id, operation, version, changed_by, payload=None, changed_by_device=None):
    cur.execute(
        """INSERT INTO tb.change_log(entity_type,entity_id,operation,entity_version,changed_by,changed_by_device,payload)
           VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb)
           RETURNING sequence_no""",
        (entity_type,entity_id,operation,version,changed_by,changed_by_device,
         json.dumps(payload or {},ensure_ascii=False,default=str)),
    )
    row=cur.fetchone()
    return int(row["sequence_no"]) if row else None


def active_routes():
    return q("""SELECT id,route_number,name FROM tb.routes
                WHERE deleted_at IS NULL ORDER BY route_number,name""")


def route_row(route_id):
    rows=q("""SELECT id,route_number,name,description,current_version,created_at,updated_at
              FROM tb.routes WHERE id=%s AND deleted_at IS NULL""",(route_id,))
    return rows[0] if rows else None


def station_row(station_id):
    rows=q("""SELECT id,station_code,name,short_name,description,current_version,created_at,updated_at
              FROM tb.stations WHERE id=%s AND deleted_at IS NULL""",(station_id,))
    return rows[0] if rows else None


def route_form_values(d):
    return {
        "route_number":(d.get("route_number") or "").strip(),
        "name":(d.get("name") or "").strip(),
        "description":(d.get("description") or "").strip(),
    }


def station_form_values(d):
    return {
        "station_code":(d.get("station_code") or "").strip().upper(),
        "name":(d.get("name") or "").strip(),
        "short_name":(d.get("short_name") or "").strip(),
        "description":(d.get("description") or "").strip(),
    }


@app.get("/admin/routes")
def routes(request:Request):
    a=require_admin(request)
    rows=q(
        """SELECT r.id,r.route_number,r.name,r.description,r.created_at,r.updated_at,
                  (SELECT count(*) FROM tb.route_sections rs WHERE rs.route_id=r.id AND rs.deleted_at IS NULL) AS section_count,
                  (SELECT count(*) FROM tb.infrastructure_objects io WHERE io.route_id=r.id AND io.deleted_at IS NULL) AS object_count,
                  (SELECT count(*) FROM tb.station_routes sr JOIN tb.stations s ON s.id=sr.station_id
                     WHERE sr.route_id=r.id AND s.deleted_at IS NULL) AS station_count
           FROM tb.routes r
           WHERE r.deleted_at IS NULL
           ORDER BY r.route_number,r.name"""
    )
    notice=None; error=None
    qp=request.query_params
    if qp.get("saved")=="1": notice="Strecke wurde gespeichert."
    elif qp.get("deleted")=="1": notice="Strecke wurde in den Papierkorb verschoben."
    elif qp.get("blocked")=="1": error="Strecke kann noch nicht gelöscht werden, weil verknüpfte Abschnitte, Objekte, Bahnhöfe, Originaldateien oder Baustellen vorhanden sind."
    return templates.TemplateResponse(request,"routes.html",ctx(request,a,rows=rows,notice=notice,error=error))


@app.get("/admin/routes/new")
def route_new_get(request:Request):
    a=require_admin(request)
    return templates.TemplateResponse(request,"route_form.html",ctx(request,a,mode="new",route={}))


@app.post("/admin/routes/new")
async def route_new_post(request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    values=route_form_values(d)
    if not values["route_number"]:
        return templates.TemplateResponse(request,"route_form.html",ctx(request,a,mode="new",route=values,error="Streckennummer ist erforderlich."),status_code=400)
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute(
                """INSERT INTO tb.routes(route_number,name,description)
                   VALUES (%s,%s,%s) RETURNING id,current_version,current_timestamp AS created_at""",
                (values["route_number"],values["name"] or None,values["description"] or None),
            )
            row=cur.fetchone()
            add_change_row(cur,"ROUTE",row["id"],"CREATE",row["current_version"],a["id"],values)
            conn.commit()
    except UniqueViolation:
        return templates.TemplateResponse(request,"route_form.html",ctx(request,a,mode="new",route=values,error="Diese Streckennummer ist bereits vorhanden."),status_code=400)
    return RedirectResponse("/admin/routes?saved=1",303)


@app.get("/admin/routes/{route_id}")
def route_detail(route_id:str, request:Request):
    a=require_admin(request)
    route=route_row(route_id)
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    sections=q(
        """SELECT id,designation,km_from_m,km_to_m,data_status,current_version,created_at,updated_at
           FROM tb.route_sections
           WHERE route_id=%s AND deleted_at IS NULL
           ORDER BY COALESCE(km_min_m,km_from_m),COALESCE(km_max_m,km_to_m),designation""",
        (route_id,),
    )
    for s in sections:
        s["km_from_text"]=format_km_m(s.get("km_from_m"))
        s["km_to_text"]=format_km_m(s.get("km_to_m"))
    stations=q(
        """SELECT s.id,s.station_code,s.name,s.short_name
           FROM tb.station_routes sr JOIN tb.stations s ON s.id=sr.station_id
           WHERE sr.route_id=%s AND s.deleted_at IS NULL ORDER BY s.name""",(route_id,)
    )
    objects=q(
        """SELECT io.id,io.object_type,io.designation,io.km_m,io.data_status,s.name AS station_name
           FROM tb.infrastructure_objects io
           LEFT JOIN tb.stations s ON s.id=io.station_id AND s.deleted_at IS NULL
           WHERE io.route_id=%s AND io.deleted_at IS NULL
           ORDER BY io.object_type,io.designation""",(route_id,)
    )
    for o in objects:
        o["km_text"]=format_km_m(o.get("km_m"))
    return templates.TemplateResponse(request,"route_detail.html",ctx(request,a,route=route,sections=sections,stations=stations,objects=objects))


@app.get("/admin/routes/{route_id}/edit")
def route_edit_get(route_id:str, request:Request):
    a=require_admin(request); route=route_row(route_id)
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    return templates.TemplateResponse(request,"route_form.html",ctx(request,a,mode="edit",route=route))


@app.post("/admin/routes/{route_id}/edit")
async def route_edit_post(route_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    route=route_row(route_id)
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    values=route_form_values(d)
    if not values["route_number"]:
        values["id"]=route_id
        return templates.TemplateResponse(request,"route_form.html",ctx(request,a,mode="edit",route=values,error="Streckennummer ist erforderlich."),status_code=400)
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute("SELECT current_version FROM tb.routes WHERE id=%s AND deleted_at IS NULL FOR UPDATE",(route_id,))
            locked=cur.fetchone()
            if not locked: raise HTTPException(404,"ROUTE_NOT_FOUND")
            new_version=int(locked["current_version"])+1
            cur.execute(
                """UPDATE tb.routes SET route_number=%s,name=%s,description=%s,current_version=%s
                   WHERE id=%s AND deleted_at IS NULL""",
                (values["route_number"],values["name"] or None,values["description"] or None,new_version,route_id),
            )
            add_change_row(cur,"ROUTE",route_id,"UPDATE",new_version,a["id"],values)
            conn.commit()
    except UniqueViolation:
        values["id"]=route_id
        return templates.TemplateResponse(request,"route_form.html",ctx(request,a,mode="edit",route=values,error="Diese Streckennummer ist bereits vorhanden."),status_code=400)
    return RedirectResponse("/admin/routes?saved=1",303)


@app.post("/admin/routes/{route_id}/delete")
async def route_delete(route_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("""SELECT id,route_number,name,description,current_version FROM tb.routes
                       WHERE id=%s AND deleted_at IS NULL FOR UPDATE""",(route_id,))
        route=cur.fetchone()
        if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
        cur.execute(
            """SELECT
               (SELECT count(*) FROM tb.route_sections WHERE route_id=%s AND deleted_at IS NULL) sections,
               (SELECT count(*) FROM tb.infrastructure_objects WHERE route_id=%s AND deleted_at IS NULL) objects,
               (SELECT count(*) FROM tb.station_routes WHERE route_id=%s) stations,
               (SELECT count(*) FROM tb.source_files WHERE route_id=%s) files,
               (SELECT count(*) FROM tb.worksites WHERE route_id=%s) worksites""",
            (route_id,route_id,route_id,route_id,route_id),
        )
        dep=cur.fetchone()
        if any(int(dep[k])>0 for k in ("sections","objects","stations","files","worksites")):
            return RedirectResponse("/admin/routes?blocked=1",303)
        new_version=int(route["current_version"])+1
        cur.execute("UPDATE tb.routes SET deleted_at=now(),current_version=%s WHERE id=%s",(new_version,route_id))
        add_trash_row(cur,"ROUTE",route_id,a["id"],route,"Admin: Strecke gelöscht")
        add_change_row(cur,"ROUTE",route_id,"DELETE",new_version,a["id"],{"route_number":route["route_number"]})
        conn.commit()
    return RedirectResponse("/admin/routes?deleted=1",303)


@app.get("/admin/routes/{route_id}/sections/new")
def route_section_new_get(route_id:str, request:Request):
    a=require_admin(request); route=route_row(route_id)
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    return templates.TemplateResponse(request,"section_form.html",ctx(request,a,mode="new",route=route,section={}))


@app.post("/admin/routes/{route_id}/sections/new")
async def route_section_new_post(route_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    route=route_row(route_id)
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    designation=(d.get("designation") or "").strip()
    data_status=(d.get("data_status") or "WITHOUT_GEOMETRY").upper()
    if data_status not in ("VERIFIED_ORIGINAL","MANUAL","GEOMETRY_WITHOUT_KM","WITHOUT_GEOMETRY"):
        data_status="WITHOUT_GEOMETRY"
    try:
        km_from=parse_km_m(d.get("km_from"))
        km_to=parse_km_m(d.get("km_to"))
    except ValueError as exc:
        return templates.TemplateResponse(request,"section_form.html",ctx(request,a,mode="new",route=route,section=d,error=str(exc)),status_code=400)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.route_sections(route_id,designation,km_from_m,km_to_m,data_status)
               VALUES (%s,%s,%s,%s,%s) RETURNING id,current_version""",
            (route_id,designation or None,km_from,km_to,data_status),
        )
        row=cur.fetchone()
        add_change_row(cur,"ROUTE_SECTION",row["id"],"CREATE",row["current_version"],a["id"],
                       {"route_id":route_id,"designation":designation,"km_from_m":km_from,"km_to_m":km_to,"data_status":data_status})
        conn.commit()
    return RedirectResponse(f"/admin/routes/{route_id}",303)


@app.get("/admin/sections/{section_id}/edit")
def section_edit_get(section_id:str, request:Request):
    a=require_admin(request)
    rows=q("""SELECT id,route_id,designation,km_from_m,km_to_m,data_status,current_version
              FROM tb.route_sections WHERE id=%s AND deleted_at IS NULL""",(section_id,))
    if not rows: raise HTTPException(404,"SECTION_NOT_FOUND")
    section=rows[0]; route=route_row(str(section["route_id"]))
    if not route: raise HTTPException(404,"ROUTE_NOT_FOUND")
    section["km_from"]=format_km_m(section.get("km_from_m")); section["km_to"]=format_km_m(section.get("km_to_m"))
    return templates.TemplateResponse(request,"section_form.html",ctx(request,a,mode="edit",route=route,section=section))

