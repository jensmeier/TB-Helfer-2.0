# Schema-10-Ergaenzung ohne neue DB-Tabelle: zentraler Medienkatalog je Baustelle.
#
# Die eigentlichen Bilder bleiben weiterhin in tb.collab_attachments. Dieser Endpunkt stellt
# daraus eine fachliche Gesamtansicht her. Verantwortliche TBs duerfen Bild-Metadaten und
# Bilddateien aller TB-Teams derselben Baustelle sehen; BTB-Texte anderer Teams werden dadurch
# NICHT freigegeben. Helfer bleiben auf ihre eigene TB-Gruppe bzw. ihren area_scope begrenzt.


def _media_group_display_map(cur, worksite_id):
    display_sql=worksite_lead_display_sql("g")
    cur.execute(
        f"""SELECT g.id,{display_sql} AS lead_display_name
            FROM tb.worksite_groups g
            WHERE g.worksite_id=%s""",
        (worksite_id,),
    )
    return {row["id"]:row.get("lead_display_name") for row in cur.fetchall()}


def _media_row_visible(access, row):
    entity_type=str(row.get("entity_type") or "").upper()
    if entity_type=="BTB":
        # Neue fachliche Regel: Ein verantwortlicher TB darf die BAUSTELLENWEITE Bilderliste
        # inklusive Bilder anderer TB-Teams aufbauen, ohne deren BTB-Text zu erhalten.
        if str(access.get("member_role") or "").upper()=="LEAD":
            return True
        return row.get("btb_group_id")==access.get("group_id")

    if entity_type=="DEFECT":
        return scope_visible(access.get("area_scope") or {},row.get("defect_area_scope") or {})
    if entity_type=="ACCEPTANCE":
        return scope_visible(access.get("area_scope") or {},row.get("acceptance_area_scope") or {})
    return False


@app.get("/api/v1/collab/worksites/{worksite_id}/media")
def collab_worksite_media(worksite_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,worksite_id)
        group_display=_media_group_display_map(cur,access["worksite_id"])
        cur.execute(
            """SELECT a.id,a.worksite_id,a.entity_type,a.entity_id,a.original_filename,a.mime_type,
                      a.sha256,a.size_bytes,a.uploaded_by,a.created_at,
                      u.full_name AS uploaded_by_name,
                      gm.group_id AS uploader_group_id,
                      b.group_id AS btb_group_id,b.status AS btb_status,
                      dfx.area_scope AS defect_area_scope,dfx.status AS defect_status,dfx.payload AS defect_payload,
                      ac.area_scope AS acceptance_area_scope,ac.status AS acceptance_status,ac.payload AS acceptance_payload
               FROM tb.collab_attachments a
               LEFT JOIN tb.users u ON u.id=a.uploaded_by
               LEFT JOIN tb.worksite_group_members gm
                 ON gm.worksite_id=a.worksite_id AND gm.user_id=a.uploaded_by
               LEFT JOIN tb.collab_btb_entries b
                 ON a.entity_type='BTB' AND b.id=a.entity_id AND b.worksite_id=a.worksite_id
               LEFT JOIN tb.collab_defects dfx
                 ON a.entity_type='DEFECT' AND dfx.id=a.entity_id AND dfx.worksite_id=a.worksite_id
               LEFT JOIN tb.collab_acceptances ac
                 ON a.entity_type='ACCEPTANCE' AND ac.id=a.entity_id AND ac.worksite_id=a.worksite_id
               WHERE a.worksite_id=%s
                 AND a.deleted_at IS NULL
                 AND COALESCE(a.mime_type,'') LIKE 'image/%%'
               ORDER BY a.created_at,a.id""",
            (access["worksite_id"],),
        )
        result=[]
        for row in cur.fetchall():
            if not _media_row_visible(access,row):
                continue
            entity_type=str(row.get("entity_type") or "").upper()
            if entity_type=="BTB":
                group_id=row.get("btb_group_id")
                area_scope={"all":True}
                parent_status=row.get("btb_status")
                # BTB-Payload bleibt absichtlich verborgen: Gesamtbilder ja, fremder BTB-Text nein.
                parent_payload={}
            elif entity_type=="DEFECT":
                group_id=row.get("uploader_group_id")
                area_scope=row.get("defect_area_scope") or {}
                parent_status=row.get("defect_status")
                parent_payload=row.get("defect_payload") or {}
            else:
                group_id=row.get("uploader_group_id")
                area_scope=row.get("acceptance_area_scope") or {}
                parent_status=row.get("acceptance_status")
                parent_payload=row.get("acceptance_payload") or {}

            result.append({
                "attachment_id":str(row["id"]),
                "entity_type":entity_type,
                "parent_entity_id":str(row["entity_id"]),
                "filename":row.get("original_filename"),
                "mime_type":row.get("mime_type"),
                "sha256":row.get("sha256"),
                "size_bytes":int(row.get("size_bytes") or 0),
                "created_at":row.get("created_at"),
                "uploaded_by_user_id":str(row["uploaded_by"]) if row.get("uploaded_by") else None,
                "uploaded_by_name":row.get("uploaded_by_name"),
                "responsible_group_id":str(group_id) if group_id else None,
                "responsible_tb":group_display.get(group_id),
                "parent_status":parent_status,
                "area_scope":area_scope,
                "parent_payload":parent_payload,
            })
        conn.commit()
        return {
            "status":"ok",
            "worksite_id":str(access["worksite_id"]),
            "member_role":access.get("member_role"),
            "count":len(result),
            "media":result,
        }


# Der bestehende Download-Endpunkt bleibt fuer normale Sichtbarkeit unveraendert. Nur fuer
# Bilddateien wird einem LEAD derselben Baustelle der teamuebergreifende Download erlaubt, damit
# ein vollstaendiger Laptop-Gesamtexport entstehen kann.
_tb_attachment_download_routes=[
    route for route in list(app.router.routes)
    if getattr(route,"path",None)=="/api/v1/collab/attachments/{attachment_id}"
    and "GET" in (getattr(route,"methods",set()) or set())
]
if len(_tb_attachment_download_routes)!=1:
    raise RuntimeError(f"COLLAB_ATTACHMENT_DOWNLOAD_ROUTE_COUNT_MEDIA:{len(_tb_attachment_download_routes)}")
for _tb_media_route in _tb_attachment_download_routes:
    app.router.routes.remove(_tb_media_route)


@app.get("/api/v1/collab/attachments/{attachment_id}")
def collab_attachment_download_media_lead(attachment_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    aid=parse_uuid_value(attachment_id,"ATTACHMENT_ID_INVALID")
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """SELECT id,worksite_id,entity_type,entity_id,original_filename,mime_type,storage_path,
                      size_bytes,deleted_at
               FROM tb.collab_attachments WHERE id=%s""",
            (aid,),
        )
        a=cur.fetchone()
        if not a or a.get("deleted_at") is not None:
            raise HTTPException(404,"ATTACHMENT_NOT_FOUND")
        access=load_worksite_access(cur,d,a["worksite_id"])
        lead_image=(
            str(access.get("member_role") or "").upper()=="LEAD"
            and str(a.get("mime_type") or "").lower().startswith("image/")
        )
        if not lead_image:
            collab_attachment_parent(cur,access,a["entity_type"],a["entity_id"],write=False,lock=False)
        conn.commit()

    path=Path(a["storage_path"])
    if not path.exists() or not path.is_file():
        raise HTTPException(404,"ATTACHMENT_FILE_MISSING")
    body=path.read_bytes()
    filename=safe_attachment_filename(a.get("original_filename") or path.name)
    return Response(
        content=body,
        media_type=a.get("mime_type") or "application/octet-stream",
        headers={"Content-Disposition":f'attachment; filename="{filename}"'},
    )


del _tb_media_route, _tb_attachment_download_routes
