@app.post("/admin/objects/{object_id}/edit")
async def object_edit_post(object_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    rows=q("""SELECT id,route_id,station_id,object_type,designation,km_m,data_status,properties,current_version,
                     CASE WHEN geom IS NULL THEN NULL ELSE ST_AsGeoJSON(geom)::jsonb END AS geom_json
              FROM tb.infrastructure_objects WHERE id=%s AND deleted_at IS NULL""",(object_id,))
    if not rows: raise HTTPException(404,"OBJECT_NOT_FOUND")
    old=rows[0]; station=station_row(str(old["station_id"])) if old.get("station_id") else None
    if not station: raise HTTPException(400,"OBJECT_WITHOUT_STATION_NOT_EDITABLE_HERE")
    object_type=(d.get("object_type") or old["object_type"]).upper()
    designation=(d.get("designation") or "").strip()
    route_id=(d.get("route_id") or "").strip() or None
    data_status=(d.get("data_status") or old["data_status"]).upper()
    if object_type not in ("TRACK","SWITCH","DKW","CROSSING","OBJECT"): object_type="OBJECT"
    if data_status not in ("VERIFIED_ORIGINAL","MANUAL","GEOMETRY_WITHOUT_KM","WITHOUT_GEOMETRY"): data_status="WITHOUT_GEOMETRY"
    try:
        km_m=parse_km_m(d.get("km_m"))
        properties=json.loads((d.get("properties_json") or "{}").strip() or "{}")
        if not isinstance(properties,dict): raise ValueError("Eigenschaften müssen ein JSON-Objekt sein.")
    except (ValueError,json.JSONDecodeError) as exc:
        return templates.TemplateResponse(request,"object_form.html",ctx(request,a,mode="edit",station=station,obj=d,routes=active_routes(),properties_json=d.get("properties_json","{}"),error=str(exc)),status_code=400)
    if not designation:
        return templates.TemplateResponse(request,"object_form.html",ctx(request,a,mode="edit",station=station,obj=d,routes=active_routes(),properties_json=d.get("properties_json","{}"),error="Bezeichnung ist erforderlich."),status_code=400)
    if route_id and not route_row(route_id): raise HTTPException(400,"ROUTE_NOT_FOUND")
    new_version=int(old["current_version"])+1
    old_snapshot={"route_id":str(old["route_id"]) if old.get("route_id") else None,
                  "station_id":str(old["station_id"]) if old.get("station_id") else None,
                  "object_type":old["object_type"],"designation":old["designation"],
                  "km_m":old["km_m"],"data_status":old["data_status"],"properties":old["properties"]}
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.object_versions(object_id,version_no,changed_by,change_reason,snapshot,geom)
               SELECT id,current_version,%s,'Admin-Bearbeitung',%s::jsonb,geom
               FROM tb.infrastructure_objects WHERE id=%s
               ON CONFLICT (object_id,version_no) DO NOTHING""",
            (a["id"],json.dumps(old_snapshot,ensure_ascii=False,default=str),object_id),
        )
        cur.execute(
            """UPDATE tb.infrastructure_objects
               SET route_id=%s,object_type=%s,designation=%s,km_m=%s,data_status=%s,properties=%s::jsonb,current_version=%s
               WHERE id=%s AND deleted_at IS NULL""",
            (route_id,object_type,designation,km_m,data_status,json.dumps(properties,ensure_ascii=False),new_version,object_id),
        )
        payload={"route_id":route_id,"station_id":str(old["station_id"]),"object_type":object_type,
                 "designation":designation,"km_m":km_m,"data_status":data_status,"properties":properties}
        add_change_row(cur,"OBJECT",object_id,"UPDATE",new_version,a["id"],payload)
        conn.commit()
    return RedirectResponse(f"/admin/stations/{old['station_id']}",303)


@app.post("/admin/objects/{object_id}/delete")
async def object_delete(object_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("""SELECT id,route_id,station_id,object_type,designation,km_m,data_status,properties,current_version
                       FROM tb.infrastructure_objects WHERE id=%s AND deleted_at IS NULL FOR UPDATE""",(object_id,))
        row=cur.fetchone()
        if not row: raise HTTPException(404,"OBJECT_NOT_FOUND")
        cur.execute("UPDATE tb.infrastructure_objects SET deleted_at=now(),current_version=current_version+1 WHERE id=%s",(object_id,))
        add_trash_row(cur,"OBJECT",object_id,a["id"],row,"Admin: Infrastruktur-Objekt gelöscht")
        add_change_row(cur,"OBJECT",object_id,"DELETE",int(row["current_version"])+1,a["id"],
                       {"station_id":str(row["station_id"]) if row.get("station_id") else None})
        conn.commit()
    target=f"/admin/stations/{row['station_id']}" if row.get("station_id") else "/admin/routes"
    return RedirectResponse(target,303)


@app.get("/admin/users/purge")
def user_purge_list(request:Request):
    a=require_admin(request)
    rows=q("""SELECT id,full_name,company,email,status,created_at
              FROM tb.users WHERE role='USER' AND status='DISABLED'
              ORDER BY full_name""")
    notice="Benutzer wurde endgültig gelöscht." if request.query_params.get("deleted")=="1" else None
    return templates.TemplateResponse(request,"user_purge.html",ctx(request,a,rows=rows,notice=notice))


@app.post("/admin/users/{user_id}/purge")
async def user_purge_post(user_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    confirm=(d.get("confirm_name") or "").strip()
    with db() as conn, conn.cursor() as cur:
        cur.execute("""SELECT id,full_name,role,status FROM tb.users
                       WHERE id=%s FOR UPDATE""",(user_id,))
        user=cur.fetchone()
        if not user: raise HTTPException(404,"USER_NOT_FOUND")
        if user["role"]!="USER": raise HTTPException(403,"ADMIN_CANNOT_BE_PURGED")
        if user["status"]!="DISABLED": raise HTTPException(409,"USER_MUST_BE_DISABLED_FIRST")
        if not confirm or not hmac.compare_digest(confirm,user["full_name"]):
            raise HTTPException(400,"CONFIRMATION_NAME_MISMATCH")

        # Personenbezug aus historischen Audit-Details entfernen, dann greift
        # Migration 007: historische FK-Verweise werden SET NULL, Geräte CASCADE gelöscht.
        cur.execute(
            """UPDATE tb.audit_log
               SET details = '{}'::jsonb
               WHERE user_id=%s OR device_id IN (SELECT id FROM tb.devices WHERE user_id=%s)""",
            (user_id,user_id),
        )
        cur.execute("DELETE FROM tb.users WHERE id=%s",(user_id,))
        # Nur der ausführende Admin bleibt als Audit-Bezug erhalten.
        cur.execute(
            """INSERT INTO tb.audit_log(user_id,action,entity_type,entity_id,details)
               VALUES (%s,'USER_PERMANENTLY_DELETED','USER',%s,'{}'::jsonb)""",
            (a["id"],user_id),
        )
        conn.commit()
    return RedirectResponse("/admin/users/purge?deleted=1",303)



def worksite_admin_row(worksite_id):
    rows=q(
        """SELECT w.id,w.name,w.designation,w.worksite_type,w.status,w.km_from_m,w.km_to_m,
                  w.company_id,w.route_id,w.station_id,w.created_at,
                  c.name AS company_name,r.route_number,s.station_code,s.name AS station_name
           FROM tb.worksites w
           LEFT JOIN tb.companies c ON c.id=w.company_id
           LEFT JOIN tb.routes r ON r.id=w.route_id
           LEFT JOIN tb.stations s ON s.id=w.station_id
           WHERE w.id=%s""",(worksite_id,))
    return rows[0] if rows else None


def scope_from_admin_form(data):
    if (data.get("scope_all") or "") in ("1","on","true","yes"):
        return {"all":True}
    labels=[]
    for value in re.split(r"[,;|/]+",(data.get("scope_labels") or "")):
        value=value.strip()
        if value and value not in labels: labels.append(value[:200])
    try:
        km_from=parse_km_m(data.get("scope_km_from")); km_to=parse_km_m(data.get("scope_km_to"))
    except ValueError as exc:
        raise HTTPException(400,str(exc))
    scope={"all":False}
    if labels:
        scope["labels"]=labels; scope["label"]=', '.join(labels)
    if km_from is not None or km_to is not None:
        a=km_from if km_from is not None else km_to; b=km_to if km_to is not None else km_from
        if a>b: a,b=b,a
        scope["km_from_m"]=a; scope["km_to_m"]=b
    if len(scope)==1:
        raise HTTPException(400,"Für einen Hilfsnutzer bitte Bereich/Gleis/Weiche oder KM-Bereich angeben.")
    return scope


@app.get("/admin/worksites")
def worksites_admin(request:Request):
    a=require_admin(request)
    rows=q(
        """SELECT w.id,w.name,w.designation,w.worksite_type,w.status,w.company_id,
                  c.name AS company_name,r.route_number,s.station_code,s.name AS station_name,
                  (SELECT count(*) FROM tb.worksite_groups g WHERE g.worksite_id=w.id) AS group_count,
                  (SELECT count(*) FROM tb.worksite_group_members gm WHERE gm.worksite_id=w.id) AS member_count
           FROM tb.worksites w
           LEFT JOIN tb.companies c ON c.id=w.company_id
           LEFT JOIN tb.routes r ON r.id=w.route_id
           LEFT JOIN tb.stations s ON s.id=w.station_id
           ORDER BY w.created_at DESC,w.name""")
    notice=None; error=None
    qp=request.query_params
    if qp.get("saved")=="1": notice="Baustellen-Team wurde gespeichert."
    if qp.get("blocked")=="company": error="Firma kann nicht gewechselt werden, solange TB-Gruppen vorhanden sind."
    return templates.TemplateResponse(request,"worksites.html",ctx(request,a,rows=rows,notice=notice,error=error))


@app.get("/admin/worksites/{worksite_id}")
def worksite_team_get(worksite_id:str, request:Request):
    a=require_admin(request); ws=worksite_admin_row(worksite_id)
    if not ws: raise HTTPException(404,"WORKSITE_NOT_FOUND")
    groups=q(
        """SELECT g.id,g.label,g.lead_user_id,g.lead_name_snapshot,g.created_at,
                  (SELECT count(*) FROM tb.collab_btb_entries b WHERE b.group_id=g.id) AS btb_count
           FROM tb.worksite_groups g WHERE g.worksite_id=%s ORDER BY g.label,g.created_at""",(worksite_id,))
    members=q(
        """SELECT gm.group_id,gm.user_id,gm.member_role,gm.area_scope,u.full_name,u.email
           FROM tb.worksite_group_members gm JOIN tb.users u ON u.id=gm.user_id
           WHERE gm.worksite_id=%s ORDER BY gm.group_id,gm.member_role DESC,u.full_name""",(worksite_id,))
    by_group={}
    for m in members: by_group.setdefault(m["group_id"],[]).append(m)
    eligible=[]
    if ws.get("company_id") is not None:
        eligible=q(
            """SELECT u.id,u.full_name,u.email
               FROM tb.users u
               WHERE u.role='USER' AND u.status='ACTIVE' AND u.company_id=%s
                 AND NOT EXISTS (SELECT 1 FROM tb.worksite_group_members gm WHERE gm.worksite_id=%s AND gm.user_id=u.id)
               ORDER BY u.full_name""",(ws["company_id"],worksite_id))
    companies=q("SELECT id,name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
    notice=request.query_params.get("notice"); error=request.query_params.get("error")
    return templates.TemplateResponse(request,"worksite_team.html",
        ctx(request,a,worksite=ws,groups=groups,by_group=by_group,eligible=eligible,companies=companies,
            notice=notice,error=error,format_km_m=format_km_m))


@app.post("/admin/worksites/{worksite_id}/company")
async def worksite_company_post(worksite_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    ws=worksite_admin_row(worksite_id)
    if not ws: raise HTTPException(404,"WORKSITE_NOT_FOUND")
    company_id=(d.get("company_id") or "").strip() or None
    with db() as conn, conn.cursor() as cur:
        cur.execute("SELECT count(*) AS n FROM tb.worksite_groups WHERE worksite_id=%s",(worksite_id,))
        if int(cur.fetchone()["n"])>0 and str(company_id or "") != str(ws.get("company_id") or ""):
            return RedirectResponse("/admin/worksites?blocked=company",303)
        if company_id:
            try: cid=int(company_id)
            except ValueError: raise HTTPException(400,"COMPANY_ID_INVALID")
            cur.execute("SELECT id FROM tb.companies WHERE id=%s AND status='ACTIVE'",(cid,))
            if not cur.fetchone(): raise HTTPException(400,"COMPANY_NOT_FOUND")
        cur.execute("UPDATE tb.worksites SET company_id=%s WHERE id=%s",(cid if company_id else None,worksite_id))
        if cur.rowcount!=1: raise HTTPException(404,"WORKSITE_NOT_FOUND")
        conn.commit()
    return RedirectResponse(f"/admin/worksites/{worksite_id}?notice=Firma gespeichert",303)


@app.post("/admin/worksites/{worksite_id}/groups")
async def worksite_group_create(worksite_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    ws=worksite_admin_row(worksite_id)
    if not ws: raise HTTPException(404,"WORKSITE_NOT_FOUND")
    if ws.get("company_id") is None: raise HTTPException(400,"WORKSITE_COMPANY_REQUIRED")
    label=(d.get("label") or "").strip()[:120]
    lead_id=(d.get("lead_user_id") or "").strip()
    if not label or not lead_id: raise HTTPException(400,"GROUP_LABEL_AND_LEAD_REQUIRED")
    try:
        with db() as conn, conn.cursor() as cur:
            # Gleichzeitige Team-Pflege derselben Baustelle serialisieren. So können weder derselbe
            # Lead noch dieselbe sichtbare Gruppenbezeichnung durch zwei Admin-Requests doppelt
            # angelegt werden, auch wenn die Migration absichtlich keine neuen Annahmen erzwingt.
            cur.execute("SELECT id FROM tb.worksites WHERE id=%s FOR UPDATE",(worksite_id,))
            if not cur.fetchone(): raise HTTPException(404,"WORKSITE_NOT_FOUND")
            cur.execute("SELECT id FROM tb.worksite_groups WHERE worksite_id=%s AND lower(btrim(COALESCE(label,'')))=lower(btrim(%s))",(worksite_id,label))
            if cur.fetchone():
                return RedirectResponse(f"/admin/worksites/{worksite_id}?error=TB-Gruppe mit dieser Bezeichnung existiert bereits",303)
            cur.execute("""SELECT id,full_name FROM tb.users
                           WHERE id=%s AND role='USER' AND status='ACTIVE' AND company_id=%s FOR SHARE""",
                        (lead_id,ws["company_id"]))
            lead=cur.fetchone()
            if not lead: raise HTTPException(400,"LEAD_NOT_ELIGIBLE")
            cur.execute("""INSERT INTO tb.worksite_groups(worksite_id,lead_user_id,lead_name_snapshot,label)
                           VALUES (%s,%s,%s,%s) RETURNING id""",
                        (worksite_id,lead_id,lead["full_name"],label))
            gid=cur.fetchone()["id"]
            cur.execute("""INSERT INTO tb.worksite_group_members(worksite_id,group_id,user_id,member_role,area_scope)
                           VALUES (%s,%s,%s,'LEAD','{\"all\":true}'::jsonb)""",(worksite_id,gid,lead_id))
            conn.commit()
    except UniqueViolation:
        return RedirectResponse(f"/admin/worksites/{worksite_id}?error=TB oder Gruppenbezeichnung ist bereits zugeordnet",303)
    return RedirectResponse(f"/admin/worksites/{worksite_id}?notice=TB-Gruppe angelegt",303)


@app.post("/admin/worksite-groups/{group_id}/members")
async def worksite_group_member_add(group_id:str, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    user_id=(d.get("user_id") or "").strip()
    if not user_id: raise HTTPException(400,"USER_REQUIRED")
    scope=scope_from_admin_form(d)
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute("""SELECT g.id,g.worksite_id,w.company_id FROM tb.worksite_groups g
                           JOIN tb.worksites w ON w.id=g.worksite_id WHERE g.id=%s FOR SHARE""",(group_id,))
            g=cur.fetchone()
            if not g: raise HTTPException(404,"GROUP_NOT_FOUND")
            cur.execute("""SELECT id FROM tb.users WHERE id=%s AND role='USER' AND status='ACTIVE' AND company_id=%s""",
                        (user_id,g["company_id"]))
            if not cur.fetchone(): raise HTTPException(400,"USER_NOT_ELIGIBLE")
            cur.execute("""INSERT INTO tb.worksite_group_members(worksite_id,group_id,user_id,member_role,area_scope)
                           VALUES (%s,%s,%s,'HELPER',%s::jsonb)""",
                        (g["worksite_id"],group_id,user_id,json.dumps(scope,ensure_ascii=False)))
            conn.commit()
        return RedirectResponse(f"/admin/worksites/{g['worksite_id']}?notice=Hilfsnutzer hinzugefügt",303)
    except UniqueViolation:
        with db() as conn, conn.cursor() as cur:
            cur.execute("SELECT worksite_id FROM tb.worksite_groups WHERE id=%s",(group_id,)); row=cur.fetchone()
        target=str(row["worksite_id"]) if row else ""
        return RedirectResponse(f"/admin/worksites/{target}?error=Nutzer ist auf dieser Baustelle bereits einer TB-Gruppe zugeordnet",303)


@app.post("/admin/worksite-groups/{group_id}/members/{user_id}/remove")
async def worksite_group_member_remove(group_id:str,user_id:str,request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("SELECT worksite_id,lead_user_id FROM tb.worksite_groups WHERE id=%s FOR UPDATE",(group_id,))
        g=cur.fetchone()
        if not g: raise HTTPException(404,"GROUP_NOT_FOUND")
        if g.get("lead_user_id") and str(g["lead_user_id"])==str(user_id): raise HTTPException(409,"LEAD_CANNOT_BE_REMOVED")
        cur.execute("DELETE FROM tb.worksite_group_members WHERE group_id=%s AND user_id=%s AND member_role='HELPER'",(group_id,user_id))
        conn.commit()
    return RedirectResponse(f"/admin/worksites/{g['worksite_id']}?notice=Hilfsnutzer entfernt",303)

