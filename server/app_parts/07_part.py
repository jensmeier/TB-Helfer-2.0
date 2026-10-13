@app.get("/admin/users")
def users(request:Request):
    a=require_admin(request)

    search=(request.query_params.get("q") or "").strip()
    status_filter=(request.query_params.get("status") or "ALL").upper()
    sort=(request.query_params.get("sort") or "company").lower()
    direction=(request.query_params.get("dir") or "asc").lower()

    sort_columns={
        "company":"company",
        "name":"full_name",
        "email":"email",
        "role":"role",
        "status":"status",
        "created":"created_at",
        "key":"key_created_at",
        "valid_until":"access_valid_until",
    }
    sort_column=sort_columns.get(sort,"company")
    sort_direction="DESC" if direction=="desc" else "ASC"

    where=[]
    params=[]

    if search:
        where.append("""(
            full_name ILIKE %s OR
            COALESCE(company,'') ILIKE %s OR
            COALESCE(email,'') ILIKE %s OR
            role ILIKE %s OR
            status ILIKE %s OR
            COALESCE(access_key_hint,'') ILIKE %s
        )""")
        needle=f"%{search}%"
        params.extend([needle,needle,needle,needle,needle,needle])

    if status_filter == "ACTIVE":
        where.append("status='ACTIVE' AND (access_valid_until IS NULL OR access_valid_until > now())")
    elif status_filter == "DISABLED":
        where.append("status='DISABLED'")
    elif status_filter == "EXPIRED":
        where.append("status='ACTIVE' AND access_valid_until IS NOT NULL AND access_valid_until <= now()")
    elif status_filter == "EXPIRING":
        where.append("status='ACTIVE' AND access_valid_until > now() AND access_valid_until <= now() + interval '30 days'")

    where_sql=(" WHERE " + " AND ".join(where)) if where else ""

    order_sql=f""" ORDER BY
        CASE WHEN {sort_column} IS NULL OR CAST({sort_column} AS text)='' THEN 1 ELSE 0 END,
        {sort_column} {sort_direction} NULLS LAST,
        full_name ASC"""

    rows=q(
        """SELECT id,full_name,company,email,role,status,access_key_hint,access_key_id,
                  key_created_at,activation_key_used_at,created_at,access_valid_until,company_id,
                  CASE
                    WHEN status <> 'ACTIVE' THEN 'DISABLED'
                    WHEN access_valid_until IS NOT NULL AND access_valid_until <= now() THEN 'EXPIRED'
                    WHEN access_valid_until IS NOT NULL AND access_valid_until <= now() + interval '7 days' THEN 'EXPIRING_7'
                    WHEN access_valid_until IS NOT NULL AND access_valid_until <= now() + interval '30 days' THEN 'EXPIRING_30'
                    ELSE 'ACTIVE'
                  END AS access_state,
                  CASE WHEN access_valid_until IS NULL THEN NULL
                       ELSE GREATEST(0,CEIL(EXTRACT(EPOCH FROM (access_valid_until-now()))/86400.0))::int
                  END AS days_left
           FROM tb.users"""
        + where_sql + order_sql,
        tuple(params),
    )

    notice=None
    if request.query_params.get("saved")=="1":
        notice="Benutzer wurde gespeichert."
    elif request.query_params.get("deleted")=="1":
        notice="Benutzer wurde sicher gelöscht: Zugang deaktiviert, Aktivierungs-Key ungültig und Geräte freigegeben."
    elif request.query_params.get("extended")=="1":
        notice="Benutzerlaufzeit wurde verlängert."

    return templates.TemplateResponse(
        request,"users.html",
        ctx(
            request,a,
            rows=rows,
            notice=notice,
            search=search,
            status_filter=status_filter,
            sort=sort,
            direction=direction,
        )
    )


@app.get("/admin/companies")
def companies(request:Request):
    a=require_admin(request)
    company_rows=q(
        """SELECT c.id,c.name,c.status,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER') AS user_count,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER' AND u.status='ACTIVE'
                    AND (u.access_valid_until IS NULL OR u.access_valid_until > now())) AS active_count,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER' AND u.status='ACTIVE'
                    AND u.access_valid_until IS NOT NULL AND u.access_valid_until <= now()) AS expired_count,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER' AND u.status='ACTIVE'
                    AND u.access_valid_until > now() AND u.access_valid_until <= now()+interval '30 days') AS expiring_count,
                  (SELECT count(*) FROM tb.devices d JOIN tb.users u ON u.id=d.user_id
                    WHERE u.company_id=c.id AND d.revoked_at IS NULL) AS active_device_count,
                  (SELECT count(*) FROM tb.worksites w WHERE w.company_id=c.id) AS worksite_count,
                  (SELECT count(*) FROM tb.source_files sf WHERE sf.company_id=c.id) AS file_count,
                  COALESCE((SELECT sum(sf.file_size_bytes) FROM tb.source_files sf WHERE sf.company_id=c.id),0) AS file_bytes
           FROM tb.companies c
           WHERE (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER') > 0
           ORDER BY user_count DESC,c.name"""
    )
    members=q(
        """SELECT id,full_name,company,email,status,company_id,access_valid_until,
                  CASE
                    WHEN status <> 'ACTIVE' THEN 'DISABLED'
                    WHEN access_valid_until IS NOT NULL AND access_valid_until <= now() THEN 'EXPIRED'
                    WHEN access_valid_until IS NOT NULL AND access_valid_until <= now()+interval '30 days' THEN 'EXPIRING'
                    ELSE 'ACTIVE'
                  END AS access_state
           FROM tb.users
           WHERE role='USER'
           ORDER BY company NULLS LAST,full_name"""
    )
    by_company={}
    unassigned=[]
    for m in members:
        if m.get("company_id") is None:
            unassigned.append(m)
        else:
            by_company.setdefault(m["company_id"],[]).append(m)
    folders=[]
    small=[]
    for c in company_rows:
        item={"company":c,"users":by_company.get(c["id"],[])}
        if c["user_count"] >= COMPANY_FOLDER_MIN_USERS:
            folders.append(item)
        else:
            small.append(item)
    return templates.TemplateResponse(
        request,"companies.html",
        ctx(request,a,folders=folders,small=small,unassigned=unassigned,threshold=COMPANY_FOLDER_MIN_USERS,human_bytes=human_bytes)
    )


@app.get("/admin/users/{user_id}/edit")
def user_edit_get(user_id:str, request:Request):
    a=require_admin(request)
    rows=q(
        """SELECT id,full_name,company,email,role,status,access_valid_until
           FROM tb.users
           WHERE id=%s""",
        (user_id,),
    )
    if not rows:
        raise HTTPException(404,"USER_NOT_FOUND")
    user=rows[0]
    if user["role"]!="USER":
        raise HTTPException(403,"ADMIN_NOT_EDITABLE_HERE")
    companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
    with db() as conn, conn.cursor() as cur:
        storage=storage_preferences_for_user(cur,user_id)
    storage["store_all"]=all(storage.values())
    return templates.TemplateResponse(
        request,"user_edit.html",
        ctx(request,a,user=user,companies=companies,storage=storage)
    )


@app.post("/admin/users/{user_id}/edit")
async def user_edit_post(user_id:str, request:Request):
    a=require_admin(request)
    d=await form(request)
    check_csrf(a,d)

    name=d.get("full_name","").strip()
    company=(d.get("company","").strip() or None)
    email=(d.get("email","").strip().lower() or None)
    valid_until_raw=(d.get("access_valid_until","") or "").strip()
    storage=storage_preferences_from_form(d)

    rows=q(
        """SELECT id,full_name,company,email,role,status,access_valid_until
           FROM tb.users
           WHERE id=%s""",
        (user_id,),
    )
    if not rows:
        raise HTTPException(404,"USER_NOT_FOUND")
    user=rows[0]
    if user["role"]!="USER":
        raise HTTPException(403,"ADMIN_NOT_EDITABLE_HERE")

    try:
        access_valid_until=(
            access_until_from_period("CUSTOM",valid_until_raw) if valid_until_raw else None
        )
    except ValueError as exc:
        user.update({"full_name":name,"company":company,"email":email})
        companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
        return templates.TemplateResponse(
            request,"user_edit.html",
            ctx(request,a,user=user,companies=companies,storage=storage,error=str(exc)),
            status_code=400
        )

    if not name:
        user.update({"full_name":name,"company":company,"email":email,"access_valid_until":access_valid_until})
        companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
        return templates.TemplateResponse(
            request,"user_edit.html",
            ctx(request,a,user=user,companies=companies,storage=storage,error="Name ist erforderlich."),
            status_code=400
        )
    if email is not None and "@" not in email:
        user.update({"full_name":name,"company":company,"email":email,"access_valid_until":access_valid_until})
        companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
        return templates.TemplateResponse(
            request,"user_edit.html",
            ctx(request,a,user=user,companies=companies,storage=storage,error="Die E-Mail-Adresse ist ungültig oder kann leer bleiben."),
            status_code=400
        )

    try:
        with db() as conn, conn.cursor() as cur:
            company_id,company_canonical=resolve_company(cur,company)
            cur.execute(
                """UPDATE tb.users
                   SET full_name=%s,company=%s,company_id=%s,email=%s,access_valid_until=%s,updated_at=now()
                   WHERE id=%s AND role='USER'""",
                (name,company_canonical,company_id,email,access_valid_until,user_id),
            )
            if cur.rowcount != 1:
                raise HTTPException(404,"USER_NOT_FOUND")
            save_storage_preferences(cur,user_id,storage)
            conn.commit()
    except UniqueViolation:
        user.update({"full_name":name,"company":company,"email":email,"access_valid_until":access_valid_until})
        companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
        return templates.TemplateResponse(
            request,"user_edit.html",
            ctx(request,a,user=user,companies=companies,storage=storage,error="Diese E-Mail-Adresse wird bereits verwendet."),
            status_code=400
        )

    return RedirectResponse("/admin/users?saved=1",303)


@app.post("/admin/users/{user_id}/extend")
async def user_extend(user_id:str, request:Request):
    a=require_admin(request)
    d=await form(request)
    check_csrf(a,d)
    period=(d.get("period") or "").upper()
    if period not in ("1M","6M","1Y","UNLIMITED"):
        raise HTTPException(400,"INVALID_PERIOD")

    with db() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id,role,access_valid_until FROM tb.users WHERE id=%s FOR UPDATE",
            (user_id,),
        )
        user=cur.fetchone()
        if not user:
            raise HTTPException(404,"USER_NOT_FOUND")
        if user["role"]!="USER":
            raise HTTPException(403,"ADMIN_NOT_EDITABLE_HERE")

        if period == "UNLIMITED":
            new_until=None
        else:
            now=datetime.now(timezone.utc)
            current=user.get("access_valid_until")
            base=current if current is not None and current > now else now
            new_until=access_until_from_period(period,base=base)

        cur.execute(
            "UPDATE tb.users SET access_valid_until=%s,updated_at=now() WHERE id=%s",
            (new_until,user_id),
        )
        conn.commit()
    return RedirectResponse("/admin/users?extended=1",303)


@app.post("/admin/users/{user_id}/delete")
async def user_delete(user_id:str, request:Request):
    a=require_admin(request)
    d=await form(request)
    check_csrf(a,d)

    replacement_hash=hash_key(secrets.token_urlsafe(48))

    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """SELECT id,role,status
               FROM tb.users
               WHERE id=%s
               FOR UPDATE""",
            (user_id,),
        )
        user=cur.fetchone()
        if not user:
            raise HTTPException(404,"USER_NOT_FOUND")
        if user["role"]!="USER":
            raise HTTPException(403,"ADMIN_CANNOT_BE_DELETED_HERE")

        # Geräte sofort ungültig machen.
        cur.execute(
            """UPDATE tb.devices
               SET revoked_at=COALESCE(revoked_at,now())
               WHERE user_id=%s""",
            (user_id,),
        )

        # Sicheres Löschen:
        # - Nutzer bleibt als Historien-Referenz bestehen
        # - Login ist deaktiviert
        # - alter Auto-/Legacy-Key ist endgültig unbrauchbar
        cur.execute(
            """UPDATE tb.users
               SET status='DISABLED',
                   access_key_hash=%s,
                   access_key_id=NULL,
                   access_key_hint=NULL,
                   activation_key_used_at=COALESCE(activation_key_used_at,now()),
                   updated_at=now()
               WHERE id=%s""",
            (replacement_hash,user_id),
        )
        conn.commit()

    return RedirectResponse("/admin/users?deleted=1",303)


@app.get("/admin/users/new")
def new_user_get(request:Request):
    a=require_admin(request)
    companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")
    return templates.TemplateResponse(request,"user_new.html",ctx(request,a,companies=companies))

@app.post("/admin/users/new")
async def new_user_post(request:Request):
    a=require_admin(request)
    d=await form(request)
    check_csrf(a,d)

    name=d.get("full_name","").strip()
    company=(d.get("company","").strip() or None)
    email=(d.get("email","").strip().lower() or None)
    period=(d.get("access_period") or "UNLIMITED").upper()
    custom_date=(d.get("custom_access_date") or "").strip()
    storage=storage_preferences_from_form(d)
    form_data={"full_name":name,"company":company,"email":email or "","access_period":period,"custom_access_date":custom_date,**storage}
    companies=q("SELECT name FROM tb.companies WHERE status='ACTIVE' ORDER BY name")

    if not name:
        return templates.TemplateResponse(
            request,"user_new.html",
            ctx(request,a,error="Name ist erforderlich.",form_data=form_data,companies=companies),
            status_code=400
        )
    if email is not None and "@" not in email:
        return templates.TemplateResponse(
            request,"user_new.html",
            ctx(request,a,error="Die E-Mail-Adresse ist ungültig oder kann leer bleiben.",form_data=form_data,companies=companies),
            status_code=400
        )
    try:
        access_valid_until=access_until_from_period(period,custom_date)
    except ValueError as exc:
        return templates.TemplateResponse(
            request,"user_new.html",
            ctx(request,a,error=str(exc),form_data=form_data,companies=companies),
            status_code=400
        )

    key_id,key=generate_activation_key()
    try:
        with db() as conn, conn.cursor() as cur:
            company_id,company_canonical=resolve_company(cur,company)
            cur.execute(
                """INSERT INTO tb.users
                   (full_name,company,company_id,email,role,status,access_key_hash,access_key_hint,access_key_id,key_created_at,activation_key_used_at,access_valid_until)
                   VALUES (%s,%s,%s,%s,'USER','ACTIVE',%s,%s,%s,now(),NULL,%s)
                   RETURNING id,full_name,company,email,role,status,access_key_hint,access_key_id,key_created_at,access_valid_until""",
                (name,company_canonical,company_id,email,hash_key(key),key_id+"…"+key[-4:],key_id,access_valid_until),
            )
            user=cur.fetchone()
            save_storage_preferences(cur,user["id"],storage)
            conn.commit()
    except UniqueViolation:
        return templates.TemplateResponse(
            request,"user_new.html",
            ctx(request,a,error="Benutzer konnte nicht angelegt werden. Die E-Mail ist eventuell bereits vorhanden.",form_data=form_data,companies=companies),
            status_code=400
        )

    return templates.TemplateResponse(
        request,"activation_card.html",
        ctx(request,a,user=user,activation_key=key,qr_svg=qr_svg_for_key(key),new_user=True)
    )

@app.post("/admin/users/{user_id}/activation")
async def user_activation_card(user_id:str, request:Request):
    a=require_admin(request)
    d=await form(request)
    check_csrf(a,d)

    rows=q("SELECT access_valid_until,status FROM tb.users WHERE id=%s AND role='USER'",(user_id,))
    if not rows:
        raise HTTPException(404,"USER_NOT_FOUND")
    if rows[0]["status"] != "ACTIVE":
        raise HTTPException(403,"USER_DISABLED")
    if access_expired(rows[0].get("access_valid_until")):
        raise HTTPException(403,"USER_EXPIRED")

    user,key=issue_activation_key(user_id)
    return templates.TemplateResponse(
        request,"activation_card.html",
        ctx(request,a,user=user,activation_key=key,qr_svg=qr_svg_for_key(key),new_user=False)
    )

@app.get("/admin/devices")
def devices(request:Request):
    a=require_admin(request)
    rows=q("""SELECT d.id,u.full_name,u.email,d.device_name,d.platform,d.os_version,d.app_version,
              d.bound_at,d.last_seen_at,d.revoked_at
              FROM tb.devices d JOIN tb.users u ON u.id=d.user_id ORDER BY d.bound_at DESC""")
    return templates.TemplateResponse(request,"devices.html",ctx(request,a,rows=rows))

