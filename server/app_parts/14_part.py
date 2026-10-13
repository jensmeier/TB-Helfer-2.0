# Schema-10-Erweiterung: selbstverwaltete Baustellen-Einladungen,
# gleichnamige verantwortliche TBs und zentrales Firmenprofil/Logo.
# Dieser Teil wird erst nach erfolgreicher Migration 010 produktiv aktiviert.

COMPANY_PROFILE_STORAGE_ROOT = STORAGE_ROOT / "company_profiles"
COMPANY_LOGO_MAX_BYTES = 5 * 1024 * 1024
WORKSITE_INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
WORKSITE_INVITE_DEFAULT_DAYS = 7


def worksite_invite_generate():
    code_id="".join(secrets.choice(WORKSITE_INVITE_ALPHABET) for _ in range(8))
    secret="".join(secrets.choice(WORKSITE_INVITE_ALPHABET) for _ in range(24))
    groups=[secret[i:i+4] for i in range(0,len(secret),4)]
    code="TBI-"+code_id+"-"+"-".join(groups)
    return code_id,code


def worksite_invite_code_id(raw):
    code=(raw or "").strip().upper()
    parts=code.split("-")
    if len(parts)>=4 and parts[0]=="TBI" and len(parts[1])==8:
        return parts[1]
    return None


def worksite_lead_display_sql(alias="g"):
    # Die Nummer ist stabil in worksite_groups gespeichert. Angezeigt wird sie nur,
    # wenn derselbe Namens-Snapshot auf derselben Baustelle mehrfach vorkommt.
    return f"""CASE WHEN (
        SELECT count(*) FROM tb.worksite_groups gx
        WHERE gx.worksite_id={alias}.worksite_id
          AND gx.lead_user_id IS NOT NULL
          AND lower(btrim(COALESCE(gx.lead_name_snapshot,'')))=lower(btrim(COALESCE({alias}.lead_name_snapshot,'')))
    ) > 1
    THEN btrim(COALESCE({alias}.lead_name_snapshot,'TB')) || ' ' || {alias}.lead_name_ordinal::text
    ELSE btrim(COALESCE({alias}.lead_name_snapshot,'TB')) END"""


def company_profile_row(cur,company_id):
    cur.execute(
        """SELECT c.id AS company_id,c.name,c.status,
                  p.address_line1,p.address_line2,p.postal_code,p.city,p.phone,p.email,p.website,
                  p.logo_storage_path,p.logo_mime_type,p.logo_sha256,
                  COALESCE(p.logo_version,0) AS logo_version,
                  COALESCE(p.profile_version,1) AS profile_version,
                  p.updated_at
           FROM tb.companies c
           LEFT JOIN tb.company_profiles p ON p.company_id=c.id
           WHERE c.id=%s""",
        (company_id,),
    )
    return cur.fetchone()


def company_profile_payload(row):
    if not row:
        return None
    return {
        "company_id":row["company_id"],
        "name":row.get("name"),
        "address_line1":row.get("address_line1"),
        "address_line2":row.get("address_line2"),
        "postal_code":row.get("postal_code"),
        "city":row.get("city"),
        "phone":row.get("phone"),
        "email":row.get("email"),
        "website":row.get("website"),
        "has_logo":bool(row.get("logo_storage_path")),
        "logo_mime_type":row.get("logo_mime_type"),
        "logo_sha256":row.get("logo_sha256"),
        "logo_version":int(row.get("logo_version") or 0),
        "profile_version":int(row.get("profile_version") or 1),
        "updated_at":row.get("updated_at"),
    }


def company_logo_kind(body,declared=None):
    if body.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png",".png"
    if len(body)>=3 and body[:3]==b"\xff\xd8\xff":
        return "image/jpeg",".jpg"
    if len(body)>=12 and body[:4]==b"RIFF" and body[8:12]==b"WEBP":
        return "image/webp",".webp"
    raise HTTPException(400,"COMPANY_LOGO_TYPE_INVALID")


def company_logo_safe_path(raw_path):
    if not raw_path:
        return None
    root=COMPANY_PROFILE_STORAGE_ROOT.resolve()
    path=Path(raw_path).resolve()
    if path != root and root not in path.parents:
        raise HTTPException(500,"COMPANY_LOGO_PATH_INVALID")
    return path


# ---------------------------------------------------------------------------
# Collab-Kontext um die stabile Namensnummer/Anzeige erweitern.
# Alte Android-Versionen ignorieren die zusaetzlichen Felder problemlos.
# ---------------------------------------------------------------------------
_tb_collab_context_routes = [
    route for route in list(app.router.routes)
    if getattr(route,"path",None)=="/api/v1/collab/context"
    and "GET" in (getattr(route,"methods",set()) or set())
]
if len(_tb_collab_context_routes)!=1:
    raise RuntimeError(f"COLLAB_CONTEXT_ROUTE_COUNT:{len(_tb_collab_context_routes)}")
for _tb_route in _tb_collab_context_routes:
    app.router.routes.remove(_tb_route)


@app.get("/api/v1/collab/context")
def collab_context_schema10(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    display_sql=worksite_lead_display_sql("g")
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            f"""SELECT w.id AS worksite_id,w.name,w.designation,w.worksite_type,w.status,w.company_id,
                       gm.group_id,gm.member_role,gm.area_scope,g.label AS group_label,
                       g.lead_user_id,g.lead_name_snapshot,g.lead_name_ordinal,
                       {display_sql} AS lead_display_name
                FROM tb.worksite_group_members gm
                JOIN tb.worksite_groups g ON g.id=gm.group_id AND g.worksite_id=gm.worksite_id
                JOIN tb.worksites w ON w.id=gm.worksite_id
                WHERE gm.user_id=%s AND w.company_id=%s
                ORDER BY w.name,g.label""",
            (d["user_id"],d.get("company_id")),
        )
        return {"worksites":cur.fetchall()}


del _tb_route, _tb_collab_context_routes


# ---------------------------------------------------------------------------
# Mobile Teamansicht
# ---------------------------------------------------------------------------
@app.get("/api/v1/collab/worksites/{worksite_id}/team")
def collab_worksite_team(worksite_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,worksite_id)
        display_sql=worksite_lead_display_sql("g")
        cur.execute(
            f"""SELECT g.id,g.label,g.lead_user_id,g.lead_name_snapshot,g.lead_name_ordinal,
                       {display_sql} AS lead_display_name,g.created_at
                FROM tb.worksite_groups g
                WHERE g.worksite_id=%s
                ORDER BY lower(COALESCE(g.lead_name_snapshot,g.label,'')),g.lead_name_ordinal,g.created_at,g.id""",
            (access["worksite_id"],),
        )
        groups=cur.fetchall()
        cur.execute(
            """SELECT gm.group_id,gm.user_id,gm.member_role,gm.area_scope,
                      u.full_name,u.email
               FROM tb.worksite_group_members gm
               JOIN tb.users u ON u.id=gm.user_id
               WHERE gm.worksite_id=%s
               ORDER BY gm.group_id,CASE WHEN gm.member_role='LEAD' THEN 0 ELSE 1 END,lower(u.full_name),u.id""",
            (access["worksite_id"],),
        )
        members_by_group={}
        for m in cur.fetchall():
            members_by_group.setdefault(m["group_id"],[]).append({
                "user_id":str(m["user_id"]),
                "name":m.get("full_name"),
                "email":m.get("email"),
                "member_role":m["member_role"],
                "area_scope":m.get("area_scope") or {},
            })
        cur.execute("SELECT id,name,designation FROM tb.worksites WHERE id=%s",(access["worksite_id"],))
        ws=cur.fetchone()
        result=[]
        for g in groups:
            result.append({
                "group_id":str(g["id"]),
                "label":g.get("label"),
                "lead_user_id":str(g["lead_user_id"]) if g.get("lead_user_id") else None,
                "lead_name_snapshot":g.get("lead_name_snapshot"),
                "lead_name_ordinal":int(g.get("lead_name_ordinal") or 1),
                "lead_display_name":g.get("lead_display_name"),
                "members":members_by_group.get(g["id"],[]),
            })
        return {
            "worksite_id":str(access["worksite_id"]),
            "worksite_name":(ws or {}).get("name"),
            "worksite_designation":(ws or {}).get("designation"),
            "current_group_id":str(access["group_id"]),
            "current_member_role":access["member_role"],
            "current_area_scope":access.get("area_scope") or {},
            "groups":result,
        }


@app.post("/api/v1/collab/worksites/{worksite_id}/invitations")
async def collab_create_invitation(worksite_id:str, request:Request,
                                   authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    try:
        payload=await request.json()
    except Exception:
        raise HTTPException(400,"INVITATION_JSON_INVALID")
    if not isinstance(payload,dict):
        raise HTTPException(400,"INVITATION_JSON_INVALID")
    invitation_type=str(payload.get("invitation_type") or "HELPER").strip().upper()
    if invitation_type not in ("LEAD","HELPER"):
        raise HTTPException(400,"INVITATION_TYPE_INVALID")
    try:
        expires_days=max(1,min(int(payload.get("expires_days") or WORKSITE_INVITE_DEFAULT_DAYS),30))
    except (TypeError,ValueError):
        raise HTTPException(400,"INVITATION_EXPIRY_INVALID")

    for _ in range(8):
        code_id,raw_code=worksite_invite_generate()
        try:
            with db() as conn, conn.cursor() as cur:
                access=load_worksite_access(cur,d,worksite_id)
                if str(access.get("member_role") or "").upper()!="LEAD":
                    raise HTTPException(403,"INVITATION_LEAD_REQUIRED")

                if invitation_type=="HELPER":
                    requested_scope=canonical_area_scope(payload.get("area_scope") or access.get("area_scope") or {"all":True})
                    if not scope_within(access.get("area_scope") or {},requested_scope):
                        raise HTTPException(403,"AREA_SCOPE_FORBIDDEN")
                    target_group=access["group_id"]
                else:
                    requested_scope={"all":True}
                    target_group=None

                expires_at=datetime.now(timezone.utc)+timedelta(days=expires_days)
                cur.execute(
                    """INSERT INTO tb.worksite_invitations
                       (worksite_id,invited_by,invitation_type,target_group_id,area_scope,
                        code_id,code_hash,code_hint,expires_at,max_uses)
                       VALUES (%s,%s,%s,%s,%s::jsonb,%s,%s,%s,%s,1)
                       RETURNING id,created_at,expires_at""",
                    (access["worksite_id"],d["user_id"],invitation_type,target_group,
                     json.dumps(requested_scope,ensure_ascii=False),code_id,hash_key(raw_code),
                     code_id+"…"+raw_code[-4:],expires_at),
                )
                invitation=cur.fetchone()
                conn.commit()
            audit_event("WORKSITE_INVITATION_CREATED",d["user_id"],d["device_id"],"WORKSITE_INVITATION",invitation["id"],{
                "worksite_id":str(access["worksite_id"]),"invitation_type":invitation_type,
                "target_group_id":str(target_group) if target_group else None,
            })
            return {
                "invitation_id":str(invitation["id"]),
                "invitation_type":invitation_type,
                "code":raw_code,
                "deep_link":"tbhelfer://invite?code="+quote(raw_code,safe=""),
                "expires_at":invitation["expires_at"],
                "max_uses":1,
            }
        except UniqueViolation:
            continue
    raise HTTPException(500,"INVITATION_CODE_GENERATION_FAILED")


@app.post("/api/v1/collab/invitations/redeem")
async def collab_redeem_invitation(request:Request, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    try:
        payload=await request.json()
    except Exception:
        raise HTTPException(400,"INVITATION_JSON_INVALID")
    raw_code=str((payload or {}).get("code") or "").strip().upper() if isinstance(payload,dict) else ""
    code_id=worksite_invite_code_id(raw_code)
    if not code_id:
        raise HTTPException(400,"INVITATION_CODE_INVALID")

    try:
        with db() as conn, conn.cursor() as cur:
            advisory_xact_lock(cur,"worksite-invitation:"+code_id)
            cur.execute(
                """SELECT i.id,i.worksite_id,i.invited_by,i.invitation_type,i.target_group_id,i.area_scope,
                          i.code_hash,i.expires_at,i.max_uses,i.uses_count,i.revoked_at,
                          w.company_id,w.name AS worksite_name
                   FROM tb.worksite_invitations i
                   JOIN tb.worksites w ON w.id=i.worksite_id
                   WHERE i.code_id=%s
                   FOR UPDATE""",
                (code_id,),
            )
            invitation=cur.fetchone()
            if not invitation or not verify_key(raw_code,invitation["code_hash"]):
                raise HTTPException(404,"INVITATION_NOT_FOUND")
            if invitation.get("revoked_at") is not None:
                raise HTTPException(410,"INVITATION_REVOKED")
            if invitation["expires_at"]<=datetime.now(timezone.utc):
                raise HTTPException(410,"INVITATION_EXPIRED")
            if int(invitation["uses_count"])>=int(invitation["max_uses"]):
                raise HTTPException(410,"INVITATION_ALREADY_USED")
            if d.get("company_id") is None or int(d["company_id"])!=int(invitation["company_id"]):
                raise HTTPException(403,"COMPANY_MISMATCH")

            cur.execute(
                """SELECT group_id,member_role FROM tb.worksite_group_members
                   WHERE worksite_id=%s AND user_id=%s FOR UPDATE""",
                (invitation["worksite_id"],d["user_id"]),
            )
            if cur.fetchone():
                raise HTTPException(409,"ALREADY_WORKSITE_MEMBER")

            invitation_type=invitation["invitation_type"]
            if invitation_type=="LEAD":
                cur.execute(
                    """INSERT INTO tb.worksite_groups(worksite_id,lead_user_id,label)
                       VALUES (%s,%s,%s) RETURNING id,lead_name_snapshot,lead_name_ordinal""",
                    (invitation["worksite_id"],d["user_id"],"TB "+str(d.get("full_name") or "")),
                )
                group=cur.fetchone(); joined_group=group["id"]
                cur.execute(
                    """INSERT INTO tb.worksite_group_members(worksite_id,group_id,user_id,member_role,area_scope)
                       VALUES (%s,%s,%s,'LEAD','{"all":true}'::jsonb)""",
                    (invitation["worksite_id"],joined_group,d["user_id"]),
                )
            else:
                joined_group=invitation["target_group_id"]
                cur.execute("SELECT worksite_id FROM tb.worksite_groups WHERE id=%s FOR UPDATE",(joined_group,))
                target=cur.fetchone()
                if not target or target["worksite_id"]!=invitation["worksite_id"]:
                    raise HTTPException(409,"INVITATION_GROUP_INVALID")
                cur.execute(
                    """INSERT INTO tb.worksite_group_members(worksite_id,group_id,user_id,member_role,area_scope)
                       VALUES (%s,%s,%s,'HELPER',%s::jsonb)""",
                    (invitation["worksite_id"],joined_group,d["user_id"],
                     json.dumps(invitation.get("area_scope") or {"all":False},ensure_ascii=False)),
                )

            cur.execute(
                """UPDATE tb.worksite_invitations
                   SET uses_count=uses_count+1,last_used_at=now(),updated_at=now()
                   WHERE id=%s""",
                (invitation["id"],),
            )
            cur.execute(
                """INSERT INTO tb.worksite_invitation_redemptions(invitation_id,user_id,joined_group_id)
                   VALUES (%s,%s,%s)""",
                (invitation["id"],d["user_id"],joined_group),
            )
            conn.commit()
        audit_event("WORKSITE_INVITATION_REDEEMED",d["user_id"],d["device_id"],"WORKSITE_INVITATION",invitation["id"],{
            "worksite_id":str(invitation["worksite_id"]),"group_id":str(joined_group),"member_role":invitation_type,
        })
        return {
            "status":"ok",
            "worksite_id":str(invitation["worksite_id"]),
            "worksite_name":invitation.get("worksite_name"),
            "group_id":str(joined_group),
            "member_role":"LEAD" if invitation_type=="LEAD" else "HELPER",
        }
    except UniqueViolation:
        raise HTTPException(409,"ALREADY_WORKSITE_MEMBER")


@app.post("/api/v1/collab/invitations/{invitation_id}/revoke")
def collab_revoke_invitation(invitation_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization); iid=parse_uuid_value(invitation_id,"INVITATION_ID_INVALID")
    with db() as conn, conn.cursor() as cur:
        cur.execute("SELECT id,worksite_id,invited_by,revoked_at FROM tb.worksite_invitations WHERE id=%s FOR UPDATE",(iid,))
        invitation=cur.fetchone()
        if not invitation: raise HTTPException(404,"INVITATION_NOT_FOUND")
        access=load_worksite_access(cur,d,invitation["worksite_id"])
        if str(access.get("member_role") or "").upper()!="LEAD" or invitation["invited_by"]!=d["user_id"]:
            raise HTTPException(403,"INVITATION_REVOKE_FORBIDDEN")
        if invitation.get("revoked_at") is None:
            cur.execute("UPDATE tb.worksite_invitations SET revoked_at=now(),updated_at=now() WHERE id=%s",(iid,))
        conn.commit()
    return {"status":"ok"}


# ---------------------------------------------------------------------------
# Mobiles Firmenprofil
# ---------------------------------------------------------------------------
@app.get("/api/v1/company/profile")
def mobile_company_profile(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    if d.get("company_id") is None:
        raise HTTPException(404,"COMPANY_NOT_ASSIGNED")
    with db() as conn, conn.cursor() as cur:
        row=company_profile_row(cur,d["company_id"])
    if not row: raise HTTPException(404,"COMPANY_NOT_FOUND")
    return {"status":"ok","profile":company_profile_payload(row)}


@app.get("/api/v1/company/profile/logo")
def mobile_company_logo(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    if d.get("company_id") is None:
        raise HTTPException(404,"COMPANY_NOT_ASSIGNED")
    with db() as conn, conn.cursor() as cur:
        row=company_profile_row(cur,d["company_id"])
    if not row or not row.get("logo_storage_path"):
        raise HTTPException(404,"COMPANY_LOGO_NOT_FOUND")
    path=company_logo_safe_path(row["logo_storage_path"])
    if not path or not path.exists() or not path.is_file():
        raise HTTPException(404,"COMPANY_LOGO_FILE_MISSING")
    return Response(
        content=path.read_bytes(),
        media_type=row.get("logo_mime_type") or "application/octet-stream",
        headers={"ETag":'"'+str(row.get("logo_sha256") or "")+'"'},
    )


# ---------------------------------------------------------------------------
# Admin: Firmenprofil und Logo pflegen
# ---------------------------------------------------------------------------
@app.get("/admin/companies/{company_id}/profile")
def admin_company_profile_get(company_id:int, request:Request):
    a=require_admin(request)
    with db() as conn, conn.cursor() as cur:
        row=company_profile_row(cur,company_id)
    if not row: raise HTTPException(404,"COMPANY_NOT_FOUND")
    return templates.TemplateResponse(
        request,"company_profile.html",
        ctx(request,a,company=row,notice=request.query_params.get("notice"),error=None)
    )


@app.post("/admin/companies/{company_id}/profile")
async def admin_company_profile_post(company_id:int, request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        current=company_profile_row(cur,company_id)
    if not current: raise HTTPException(404,"COMPANY_NOT_FOUND")

    def clean(name,max_len):
        value=(d.get(name) or "").strip()
        return value[:max_len] or None

    address_line1=clean("address_line1",250)
    address_line2=clean("address_line2",250)
    postal_code=clean("postal_code",40)
    city=clean("city",160)
    phone=clean("phone",120)
    email=clean("email",250)
    website=clean("website",300)
    if email and "@" not in email:
        return templates.TemplateResponse(request,"company_profile.html",ctx(request,a,company={**current,**d},error="Die E-Mail-Adresse ist ungültig.",notice=None),status_code=400)

    logo_data=(d.get("logo_data") or "").strip()
    remove_logo=(d.get("remove_logo") or "") in ("1","true","on")
    new_logo_path=None; new_logo_mime=None; new_logo_sha=None; logo_changed=False
    old_logo_path=current.get("logo_storage_path")

    if logo_data:
        try:
            if not logo_data.startswith("data:") or ";base64," not in logo_data:
                raise ValueError("format")
            declared,encoded=logo_data.split(";base64,",1)
            body=base64.b64decode(encoded,validate=True)
        except Exception:
            return templates.TemplateResponse(request,"company_profile.html",ctx(request,a,company={**current,**d},error="Logo konnte nicht gelesen werden.",notice=None),status_code=400)
        if not body or len(body)>COMPANY_LOGO_MAX_BYTES:
            return templates.TemplateResponse(request,"company_profile.html",ctx(request,a,company={**current,**d},error="Logo darf höchstens 5 MB groß sein.",notice=None),status_code=400)
        try:
            new_logo_mime,ext=company_logo_kind(body,declared)
        except HTTPException:
            return templates.TemplateResponse(request,"company_profile.html",ctx(request,a,company={**current,**d},error="Logo muss PNG, JPG oder WEBP sein.",notice=None),status_code=400)
        directory=COMPANY_PROFILE_STORAGE_ROOT/str(company_id)
        directory.mkdir(parents=True,exist_ok=True)
        new_logo_path=directory/("logo"+ext)
        temp=new_logo_path.with_suffix(new_logo_path.suffix+".tmp")
        temp.write_bytes(body); os.replace(temp,new_logo_path)
        new_logo_sha=hashlib.sha256(body).hexdigest(); logo_changed=True
    elif remove_logo:
        logo_changed=bool(old_logo_path)
    else:
        new_logo_path=Path(old_logo_path) if old_logo_path else None
        new_logo_mime=current.get("logo_mime_type")
        new_logo_sha=current.get("logo_sha256")

    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.company_profiles
               (company_id,address_line1,address_line2,postal_code,city,phone,email,website,
                logo_storage_path,logo_mime_type,logo_sha256,logo_version,profile_version)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,1)
               ON CONFLICT (company_id) DO UPDATE SET
                 address_line1=EXCLUDED.address_line1,address_line2=EXCLUDED.address_line2,
                 postal_code=EXCLUDED.postal_code,city=EXCLUDED.city,phone=EXCLUDED.phone,
                 email=EXCLUDED.email,website=EXCLUDED.website,
                 logo_storage_path=EXCLUDED.logo_storage_path,logo_mime_type=EXCLUDED.logo_mime_type,
                 logo_sha256=EXCLUDED.logo_sha256,
                 logo_version=CASE WHEN %s THEN tb.company_profiles.logo_version+1 ELSE tb.company_profiles.logo_version END,
                 profile_version=tb.company_profiles.profile_version+1,updated_at=now()""",
            (company_id,address_line1,address_line2,postal_code,city,phone,email,website,
             str(new_logo_path) if new_logo_path and not remove_logo else None,
             new_logo_mime if not remove_logo else None,new_logo_sha if not remove_logo else None,
             1 if logo_changed and not remove_logo else 0,logo_changed),
        )
        conn.commit()

    if (remove_logo or (logo_changed and new_logo_path)) and old_logo_path:
        try:
            old=company_logo_safe_path(old_logo_path)
            if old and old.exists() and (remove_logo or not new_logo_path or old.resolve()!=new_logo_path.resolve()):
                old.unlink(missing_ok=True)
        except Exception:
            pass
    audit_event("COMPANY_PROFILE_UPDATED",a.get("id"),None,"COMPANY",str(company_id),{"logo_changed":logo_changed})
    return RedirectResponse(f"/admin/companies/{company_id}/profile?notice=Firmenprofil gespeichert",303)
