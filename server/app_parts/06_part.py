@app.post("/api/v1/collab/mutations")
def collab_mutations(req:CollabMutationRequest, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    t,table,keycol=collab_table_info(req.entity_type)
    operation=req.operation.upper()
    if operation not in ("UPSERT","DELETE"): raise HTTPException(400,"OPERATION_INVALID")
    status=(req.status or ("ENTWURF" if t in ("BTB","ACCEPTANCE") else "OFFEN")).upper()
    if status not in COLLAB_STATUSES[t]: raise HTTPException(400,"STATUS_INVALID")
    enforce_json_size(req.payload); enforce_json_size(req.area_scope)

    with db() as conn, conn.cursor() as cur:
        advisory_xact_lock(cur,f"collab-receipt:{d['device_id']}:{req.client_change_id}")
        cur.execute("""SELECT http_status,response_payload FROM tb.collab_mutation_receipts
                       WHERE device_id=%s AND client_change_id=%s""",(d["device_id"],req.client_change_id))
        receipt=cur.fetchone()
        if receipt:
            conn.commit()
            return JSONResponse(jsonable_encoder(receipt["response_payload"]),status_code=int(receipt["http_status"]))
        access=load_worksite_access(cur,d,req.worksite_id)
        worksite_id=access["worksite_id"]
        requested_scope={"all":True} if t=="BTB" else canonical_area_scope(req.area_scope)
        if t=="BTB":
            if req.group_id and parse_uuid_value(req.group_id,"GROUP_ID_INVALID") != access["group_id"]:
                raise HTTPException(403,"GROUP_WORKSITE_MISMATCH")
            group_id=access["group_id"]
        else:
            group_id=None
            if not scope_within(access.get("area_scope") or {},requested_scope):
                raise HTTPException(403,"AREA_SCOPE_FORBIDDEN")

        row=None
        if req.entity_id:
            row=collab_entity_by_id(cur,t,req.entity_id,lock=True)
            if not row or row["worksite_id"]!=worksite_id:
                raise HTTPException(404,"ENTITY_NOT_FOUND")
        elif req.client_entity_key:
            lock_key=f"collab-key:{t}:{group_id if t=='BTB' else worksite_id}:{req.client_entity_key}"
            advisory_xact_lock(cur,lock_key)
            row=collab_lookup_by_client_key(cur,t,worksite_id,group_id,req.client_entity_key,lock=True)

        # Ein stabiler Client-Key ist Teil der geräteübergreifenden Identität. Sobald eine
        # Entity existiert, darf ein Client dieselbe Server-ID nicht mit einem anderen lokalen Key
        # umetikettieren. Bei DELETE darf der Key fehlen.
        if row and operation != "DELETE" and req.client_entity_key:
            stored_key=(row.get("client_entity_key") or "").strip()
            supplied_key=req.client_entity_key.strip()
            if stored_key and supplied_key != stored_key:
                raise HTTPException(409,"CLIENT_ENTITY_KEY_MISMATCH")

        if operation=="DELETE":
            if not row: raise HTTPException(404,"ENTITY_NOT_FOUND")
            if not collab_row_accessible(access,row,write=True): raise HTTPException(403,"ENTITY_NOT_VISIBLE")
            if req.base_version is None or int(req.base_version)!=int(row["current_version"]):
                response={"detail":"VERSION_CONFLICT","server_version":int(row["current_version"]),"server_entity":collab_server_entity(row)}
                conn.commit()
                return JSONResponse(jsonable_encoder(response),status_code=409)
            new_version=int(row["current_version"])+1
            cur.execute(f"UPDATE {table} SET deleted_at=now(),current_version=%s,updated_by=%s WHERE id=%s",
                        (new_version,d["user_id"],row["id"]))
            seq=add_collab_change(cur,worksite_id,row.get("group_id"),t,row["id"],"DELETE",new_version,
                                  d["user_id"],d["device_id"],row.get("area_scope") or {},{})
            response={"entity_id":str(row["id"]),"entity_version":new_version,"sequence_no":seq,"server_entity":{}}
            store_collab_receipt(cur,d,req,200,response,row["id"],new_version); conn.commit()
            return JSONResponse(jsonable_encoder(response),status_code=200)

        # UPSERT: vorhandene Zeile -> echte versionsgeschützte Änderung; ohne Zeile -> CREATE.
        if row:
            if row.get("deleted_at") is not None:
                raise HTTPException(409,"ENTITY_ALREADY_DELETED")
            if not collab_row_accessible(access,row,write=True): raise HTTPException(403,"ENTITY_NOT_VISIBLE")
            if req.base_version is None or int(req.base_version)!=int(row["current_version"]):
                response={"detail":"VERSION_CONFLICT","server_version":int(row["current_version"]),"server_entity":collab_server_entity(row)}
                conn.commit()
                return JSONResponse(jsonable_encoder(response),status_code=409)
            if t=="BTB" and row.get("group_id")!=group_id: raise HTTPException(403,"GROUP_WORKSITE_MISMATCH")
            old_status=(row.get("status") or "").upper()
            if t=="DEFECT" and old_status=="ERLEDIGT" and status!="ERLEDIGT": raise HTTPException(409,"FINAL_STATUS")
            if t=="ACCEPTANCE" and old_status in ("ABGENOMMEN","NICHT_ABGENOMMEN") and status!=old_status: raise HTTPException(409,"FINAL_STATUS")
            new_version=int(row["current_version"])+1
            retain,expires=retention_for_user(cur,d["user_id"],t,row.get("retain_permanent"))
            if t=="BTB":
                entry_date=None
                raw_date=req.payload.get("datumIso")
                if raw_date:
                    try: entry_date=datetime.strptime(str(raw_date),"%Y-%m-%d").date()
                    except ValueError: raise HTTPException(400,"BTB_DATE_INVALID")
                shift=str(req.payload.get("schicht") or "TAG")[:40]
                cur.execute(f"""UPDATE {table} SET status=%s,entry_date=%s,shift=%s,payload=%s::jsonb,
                                current_version=%s,updated_by=%s,retain_permanent=%s,expires_at=%s
                                WHERE id=%s""",
                            (status,entry_date,shift,json.dumps(req.payload,ensure_ascii=False),new_version,d["user_id"],retain,expires,row["id"]))
            else:
                cur.execute(f"""UPDATE {table} SET status=%s,area_scope=%s::jsonb,payload=%s::jsonb,
                                current_version=%s,updated_by=%s,retain_permanent=%s,expires_at=%s
                                WHERE id=%s""",
                            (status,json.dumps(requested_scope,ensure_ascii=False),json.dumps(req.payload,ensure_ascii=False),
                             new_version,d["user_id"],retain,expires,row["id"]))
            fresh=collab_entity_by_id(cur,t,row["id"],lock=False)
            seq=add_collab_change(cur,worksite_id,fresh.get("group_id"),t,fresh["id"],"UPDATE",new_version,
                                  d["user_id"],d["device_id"],fresh.get("area_scope") or {},collab_server_entity(fresh))
            response={"entity_id":str(fresh["id"]),"entity_version":new_version,"sequence_no":seq,"server_entity":collab_server_entity(fresh)}
            store_collab_receipt(cur,d,req,200,response,fresh["id"],new_version); conn.commit()
            return JSONResponse(jsonable_encoder(response),status_code=200)

        client_key=(req.client_entity_key or "").strip()
        if not client_key: raise HTTPException(400,"CLIENT_ENTITY_KEY_REQUIRED")
        advisory_xact_lock(cur,f"collab-key:{t}:{group_id if t=='BTB' else worksite_id}:{client_key}")
        existing=collab_lookup_by_client_key(cur,t,worksite_id,group_id,client_key,lock=True)
        if existing:
            response={"detail":"VERSION_CONFLICT","server_version":int(existing["current_version"]),"server_entity":collab_server_entity(existing)}
            conn.commit()
            return JSONResponse(jsonable_encoder(response),status_code=409)
        retain,expires=retention_for_user(cur,d["user_id"],t,False)
        if t=="BTB":
            raw_date=req.payload.get("datumIso"); entry_date=None
            if raw_date:
                try: entry_date=datetime.strptime(str(raw_date),"%Y-%m-%d").date()
                except ValueError: raise HTTPException(400,"BTB_DATE_INVALID")
            shift=str(req.payload.get("schicht") or "TAG")[:40]
            cur.execute(f"""INSERT INTO {table}
                            (worksite_id,group_id,{keycol},status,entry_date,shift,payload,created_by,updated_by,retain_permanent,expires_at)
                            VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb,%s,%s,%s,%s) RETURNING id""",
                        (worksite_id,group_id,client_key,status,entry_date,shift,json.dumps(req.payload,ensure_ascii=False),
                         d["user_id"],d["user_id"],retain,expires))
        else:
            cur.execute(f"""INSERT INTO {table}
                            (worksite_id,{keycol},area_scope,status,payload,created_by,updated_by,retain_permanent,expires_at)
                            VALUES (%s,%s,%s::jsonb,%s,%s::jsonb,%s,%s,%s,%s) RETURNING id""",
                        (worksite_id,client_key,json.dumps(requested_scope,ensure_ascii=False),status,
                         json.dumps(req.payload,ensure_ascii=False),d["user_id"],d["user_id"],retain,expires))
        entity_id=cur.fetchone()["id"]
        fresh=collab_entity_by_id(cur,t,entity_id,lock=False)
        seq=add_collab_change(cur,worksite_id,fresh.get("group_id"),t,entity_id,"CREATE",1,d["user_id"],d["device_id"],
                              fresh.get("area_scope") or {},collab_server_entity(fresh))
        response={"entity_id":str(entity_id),"entity_version":1,"sequence_no":seq,"server_entity":collab_server_entity(fresh)}
        store_collab_receipt(cur,d,req,200,response,entity_id,1); conn.commit()
        return JSONResponse(jsonable_encoder(response),status_code=200)


@app.post("/api/v1/collab/attachments/{entity_type}/{entity_id}")
async def collab_attachment_upload(entity_type:str, entity_id:str, request:Request,
                                   authorization: str | None = Header(default=None),
                                   x_worksite_id: str | None = Header(default=None,alias="X-Worksite-ID"),
                                   x_filename: str | None = Header(default=None,alias="X-Filename")):
    d=require_device(authorization)
    if not x_worksite_id: raise HTTPException(400,"WORKSITE_ID_REQUIRED")
    t,_,_=collab_table_info(entity_type)
    body=await request.body()
    if not body: raise HTTPException(400,"ATTACHMENT_EMPTY")
    if len(body)>COLLAB_MAX_ATTACHMENT_BYTES: raise HTTPException(413,"ATTACHMENT_TOO_LARGE")
    mime,extension=sniff_attachment_mime(body,request.headers.get("content-type"))
    digest=hashlib.sha256(body).hexdigest()
    filename=safe_attachment_filename(x_filename or f"datei{extension}")
    file_path=None
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,x_worksite_id)
        parent=collab_attachment_parent(cur,access,t,entity_id,write=True,lock=True)
        advisory_xact_lock(cur,f"attachment:{t}:{parent['id']}:{digest}")
        cur.execute("""SELECT id,size_bytes,sha256 FROM tb.collab_attachments
                       WHERE entity_type=%s AND entity_id=%s AND sha256=%s AND deleted_at IS NULL""",
                    (t,parent["id"],digest))
        existing=cur.fetchone()
        if existing:
            cur.execute("SELECT max(sequence_no) AS seq FROM tb.collab_change_log WHERE entity_type='ATTACHMENT' AND entity_id=%s",(existing["id"],))
            seq=cur.fetchone()["seq"]
            conn.commit()
            return {"attachment_id":str(existing["id"]),"sequence_no":seq,"sha256":digest,"size_bytes":existing["size_bytes"]}
        retain,expires=retention_for_user(cur,d["user_id"],"ATTACHMENT",False)
        attachment_id=uuid.uuid4()
        directory=COLLAB_STORAGE_ROOT/str(parent["worksite_id"])/t/str(parent["id"])
        directory.mkdir(parents=True,exist_ok=True)
        file_path=directory/(str(attachment_id)+extension)
        temp=file_path.with_suffix(file_path.suffix+".tmp")
        try:
            temp.write_bytes(body); os.replace(temp,file_path)
            cur.execute("""INSERT INTO tb.collab_attachments
                           (id,worksite_id,entity_type,entity_id,original_filename,mime_type,storage_path,sha256,size_bytes,
                            uploaded_by,retain_permanent,expires_at)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                        (attachment_id,parent["worksite_id"],t,parent["id"],filename,mime,str(file_path),digest,len(body),
                         d["user_id"],retain,expires))
            data={"id":attachment_id,"worksite_id":parent["worksite_id"],"entity_type":t,"entity_id":parent["id"],
                  "filename":filename,"mime_type":mime,"size_bytes":len(body),"sha256":digest}
            seq=add_collab_change(cur,parent["worksite_id"],parent.get("group_id"),"ATTACHMENT",attachment_id,"CREATE",None,
                                  d["user_id"],d["device_id"],parent.get("area_scope") or {},data)
            conn.commit()
        except Exception:
            try:
                if file_path: file_path.unlink(missing_ok=True)
                temp.unlink(missing_ok=True)
            except Exception: pass
            raise
    return {"attachment_id":str(attachment_id),"sequence_no":seq,"sha256":digest,"size_bytes":len(body)}


@app.get("/api/v1/collab/attachments/{attachment_id}")
def collab_attachment_download(attachment_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization); aid=parse_uuid_value(attachment_id,"ATTACHMENT_ID_INVALID")
    with db() as conn, conn.cursor() as cur:
        cur.execute("""SELECT id,worksite_id,entity_type,entity_id,original_filename,mime_type,storage_path,size_bytes,deleted_at
                       FROM tb.collab_attachments WHERE id=%s""",(aid,))
        a=cur.fetchone()
        if not a or a.get("deleted_at") is not None: raise HTTPException(404,"ATTACHMENT_NOT_FOUND")
        access=load_worksite_access(cur,d,a["worksite_id"])
        collab_attachment_parent(cur,access,a["entity_type"],a["entity_id"],write=False,lock=False)
    path=Path(a["storage_path"])
    if not path.exists() or not path.is_file(): raise HTTPException(404,"ATTACHMENT_FILE_MISSING")
    body=path.read_bytes()
    filename=safe_attachment_filename(a.get("original_filename") or path.name)
    return Response(content=body,media_type=a.get("mime_type") or "application/octet-stream",
                    headers={"Content-Disposition":f'attachment; filename="{filename}"'})


@app.get("/admin/api/stations/search")
def admin_station_search(request:Request, q:str=""):
    require_admin(request)
    return {"items":station_autofill_matches(q,20)}


@app.get("/admin/setup")
def setup_get(request:Request):
    if admin_count(): return RedirectResponse("/admin/login",303)
    if not ser():
        return templates.TemplateResponse(request,"message.html",ctx(request,title="Server-Schlüssel fehlt",message="ADMIN_SESSION_SECRET fehlt oder ist zu kurz."))
    return templates.TemplateResponse(request,"setup.html",ctx(request))

@app.post("/admin/setup")
async def setup_post(request:Request):
    if admin_count(): return RedirectResponse("/admin/login",303)
    d=await form(request)
    name=d.get("full_name","").strip()
    email=d.get("email","").strip().lower()
    key=d.get("access_key","")
    if not name or "@" not in email or len(key)<16 or key!=d.get("access_key2",""):
        return templates.TemplateResponse(request,"setup.html",ctx(request,error="Bitte Angaben prüfen. Key mindestens 16 Zeichen und zweimal identisch."),status_code=400)
    with db() as conn, conn.cursor() as cur:
        cur.execute("""INSERT INTO tb.users(full_name,email,role,status,access_key_hash,access_key_hint,key_created_at)
                       VALUES (%s,%s,'ADMIN','ACTIVE',%s,%s,now()) RETURNING id""",
                    (name,email,hash_key(key),"…"+key[-4:]))
        uid=cur.fetchone()["id"]; conn.commit()
    return login_response(uid)

@app.get("/admin/login")
def login_get(request:Request):
    if not admin_count(): return RedirectResponse("/admin/setup",303)
    return templates.TemplateResponse(request,"login.html",ctx(request))

@app.post("/admin/login")
async def login_post(request:Request):
    d=await form(request)
    email=(d.get("email","") or "").strip()
    rows=q("SELECT id,access_key_hash FROM tb.users WHERE lower(email)=lower(%s) AND role='ADMIN' AND status='ACTIVE'",(email,))
    if not rows or not verify_key(d.get("access_key",""),rows[0]["access_key_hash"]):
        audit_event("ADMIN_LOGIN_FAILED",details={"email":email[:200]})
        return templates.TemplateResponse(request,"login.html",ctx(request,error="E-Mail oder Key ist nicht korrekt."),status_code=401)
    audit_event("ADMIN_LOGIN_SUCCESS",user_id=rows[0]["id"])
    return login_response(rows[0]["id"])

@app.post("/admin/logout")
async def logout(request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    r=RedirectResponse("/admin/login",303); r.delete_cookie(COOKIE,path="/admin"); return r

@app.get("/admin")
def dashboard(request:Request):
    a=require_admin(request)
    counts={}
    for k,t in [("routes","routes"),("stations","stations"),("objects","infrastructure_objects"),
                ("worksites","worksites"),("files","source_files"),("users","users"),("companies","companies"),("devices","devices"),
                ("releases","app_releases"),("trash","trash")]:
        where = " WHERE deleted_at IS NULL" if t in ("routes","stations","infrastructure_objects") else ""
        counts[k]=q(f"SELECT count(*) n FROM tb.{t}{where}")[0]["n"]
    return templates.TemplateResponse(request,"dashboard.html",ctx(request,a,counts=counts))

