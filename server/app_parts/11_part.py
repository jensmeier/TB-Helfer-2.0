@app.post("/admin/worksite-groups/{group_id}/delete")
async def worksite_group_delete(group_id:str,request:Request):
    a=require_admin(request); d=await form(request); check_csrf(a,d)
    with db() as conn, conn.cursor() as cur:
        cur.execute("SELECT worksite_id FROM tb.worksite_groups WHERE id=%s FOR UPDATE",(group_id,))
        g=cur.fetchone()
        if not g: raise HTTPException(404,"GROUP_NOT_FOUND")
        cur.execute("SELECT count(*) AS n FROM tb.collab_btb_entries WHERE group_id=%s",(group_id,))
        if int(cur.fetchone()["n"])>0:
            return RedirectResponse(f"/admin/worksites/{g['worksite_id']}?error=TB-Gruppe enthält bereits BTB-Daten und kann nicht gelöscht werden",303)
        cur.execute("DELETE FROM tb.worksite_groups WHERE id=%s",(group_id,))
        conn.commit()
    return RedirectResponse(f"/admin/worksites/{g['worksite_id']}?notice=Leere TB-Gruppe gelöscht",303)


def generic(request,title,sql):
    a=require_admin(request)
    return templates.TemplateResponse(request,"generic.html",ctx(request,a,title=title,rows=q(sql)))

@app.get("/admin/files")
def files(request:Request): return generic(request,"Originaldateien","SELECT original_filename,file_type,file_size_bytes,source_date,imported_at,immutable_original FROM tb.source_files ORDER BY imported_at DESC LIMIT 500")
@app.get("/admin/releases")
def releases(request:Request): return generic(request,"App-Versionen","SELECT platform,version_name,version_code,channel,mandatory,published_at,created_at FROM tb.app_releases ORDER BY version_code DESC")
@app.get("/admin/trash")
def trash(request:Request): return generic(request,"Papierkorb","SELECT entity_type,entity_id,deleted_at,purge_after,reason,restored_at FROM tb.trash ORDER BY deleted_at DESC")
