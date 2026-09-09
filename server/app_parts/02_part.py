def retention_for_user(cur,user_id,kind,existing_retain=False):
    prefs=storage_preferences_for_user(cur,user_id)
    key={"BTB":"store_btb","DEFECT":"store_defects","ACCEPTANCE":"store_acceptances","ATTACHMENT":"store_images"}.get(kind)
    retain=bool(existing_retain) or bool(prefs.get(key,False))
    expires=None if retain else datetime.now(timezone.utc)+timedelta(days=COLLAB_TEMP_RETENTION_DAYS)
    return retain,expires


def canonical_area_scope(raw):
    scope=raw if isinstance(raw,dict) else {}
    if bool(scope.get("all",False)):
        return {"all":True}
    out={"all":False}
    labels=[]
    source=scope.get("labels")
    if isinstance(source,list):
        labels.extend(str(x).strip() for x in source if str(x).strip())
    label=scope.get("label")
    if isinstance(label,str) and label.strip():
        labels.extend(x.strip() for x in re.split(r"[,;|/]+",label) if x.strip())
    seen=set(); clean=[]
    for label in labels:
        key=_station_normalize(label)
        if key and key not in seen:
            seen.add(key); clean.append(label[:200])
    if clean:
        out["labels"]=clean
        out["label"]=', '.join(clean)
    def number(name):
        try:
            v=scope.get(name)
            return None if v is None else float(v)
        except (TypeError,ValueError):
            return None
    lo=number("km_from_m"); hi=number("km_to_m")
    if lo is not None or hi is not None:
        a=lo if lo is not None else hi
        b=hi if hi is not None else lo
        if a is not None and b is not None:
            if a>b: a,b=b,a
            if -1000000 <= a <= 10000000 and -1000000 <= b <= 10000000:
                out["km_from_m"]=round(a,3); out["km_to_m"]=round(b,3)
    return out


def _scope_label_keys(scope):
    return {_station_normalize(x) for x in canonical_area_scope(scope).get("labels",[]) if _station_normalize(x)}


def _scope_km(scope):
    s=canonical_area_scope(scope)
    if "km_from_m" in s and "km_to_m" in s:
        return float(s["km_from_m"]),float(s["km_to_m"])
    return None


def scope_visible(member_scope, entity_scope):
    member=canonical_area_scope(member_scope)
    entity=canonical_area_scope(entity_scope)
    if member.get("all") is True:
        return True
    if entity.get("all") is True:
        return False
    constrained=False
    ml=_scope_label_keys(member)
    if ml:
        constrained=True
        el=_scope_label_keys(entity)
        if not el or not (ml & el): return False
    mk=_scope_km(member)
    if mk:
        constrained=True
        ek=_scope_km(entity)
        if not ek or ek[1] < mk[0] or ek[0] > mk[1]: return False
    return constrained


def scope_within(member_scope, entity_scope):
    member=canonical_area_scope(member_scope)
    entity=canonical_area_scope(entity_scope)
    if member.get("all") is True:
        return True
    if entity.get("all") is True:
        return False
    constrained=False
    ml=_scope_label_keys(member)
    if ml:
        constrained=True
        el=_scope_label_keys(entity)
        if not el or not el.issubset(ml): return False
    mk=_scope_km(member)
    if mk:
        constrained=True
        ek=_scope_km(entity)
        if not ek or ek[0] < mk[0] or ek[1] > mk[1]: return False
    return constrained


def load_worksite_access(cur,device,worksite_id):
    wsid=parse_uuid_value(worksite_id,"WORKSITE_ID_INVALID")
    cur.execute(
        """SELECT gm.worksite_id,gm.group_id,gm.member_role,gm.area_scope,
                  g.label AS group_label,g.lead_user_id,g.lead_name_snapshot,
                  w.name,w.designation,w.worksite_type,w.status,w.company_id
           FROM tb.worksite_group_members gm
           JOIN tb.worksite_groups g ON g.id=gm.group_id AND g.worksite_id=gm.worksite_id
           JOIN tb.worksites w ON w.id=gm.worksite_id
           WHERE gm.user_id=%s AND gm.worksite_id=%s""",
        (device["user_id"],wsid),
    )
    row=cur.fetchone()
    if not row:
        raise HTTPException(403,"WORKSITE_FORBIDDEN")
    if row.get("company_id") is None or device.get("company_id") is None or row["company_id"] != device["company_id"]:
        raise HTTPException(403,"COMPANY_MISMATCH")
    return row


def collab_table_info(entity_type):
    t=(entity_type or "").upper()
    mapping={
        "BTB":("tb.collab_btb_entries","client_entry_id"),
        "DEFECT":("tb.collab_defects","client_defect_key"),
        "ACCEPTANCE":("tb.collab_acceptances","client_acceptance_id"),
    }
    if t not in mapping:
        raise HTTPException(400,"ENTITY_TYPE_INVALID")
    return t,mapping[t][0],mapping[t][1]


def collab_entity_select_sql(entity_type, lock=False):
    t,table,key=collab_table_info(entity_type)
    group="group_id," if t=="BTB" else "NULL::uuid AS group_id,"
    area="'{\"all\":true}'::jsonb AS area_scope," if t=="BTB" else "area_scope,"
    return f"""SELECT id,worksite_id,{group} {key} AS client_entity_key,status,{area}
                      payload,current_version,created_by,updated_by,created_at,updated_at,deleted_at,
                      retain_permanent,expires_at
               FROM {table} WHERE id=%s""" + (" FOR UPDATE" if lock else "")


def collab_entity_by_id(cur,entity_type,entity_id,lock=False):
    eid=parse_uuid_value(entity_id,"ENTITY_ID_INVALID")
    cur.execute(collab_entity_select_sql(entity_type,lock),(eid,))
    return cur.fetchone()


def collab_server_entity(row):
    if not row: return None
    return {
        "id":row["id"],"worksite_id":row["worksite_id"],"group_id":row.get("group_id"),
        "client_entity_key":row.get("client_entity_key"),"status":row.get("status"),
        "area_scope":row.get("area_scope") or {},"payload":row.get("payload") or {},
        "current_version":row.get("current_version"),"created_at":row.get("created_at"),
        "updated_at":row.get("updated_at"),"deleted_at":row.get("deleted_at"),
    }


def collab_row_accessible(access,row,write=False):
    if not row or row.get("deleted_at") is not None:
        return False
    if row["worksite_id"] != access["worksite_id"]:
        return False
    if row.get("group_id") is not None:
        return row["group_id"] == access["group_id"]
    fn=scope_within if write else scope_visible
    return fn(access.get("area_scope") or {},row.get("area_scope") or {})


def add_collab_change(cur, worksite_id, group_id, entity_type, entity_id, operation, version,
                      changed_by, changed_by_device, area_scope, payload):
    cur.execute(
        """INSERT INTO tb.collab_change_log
           (worksite_id,group_id,entity_type,entity_id,operation,entity_version,changed_by,changed_by_device,area_scope,payload)
           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb)
           RETURNING sequence_no""",
        (worksite_id,group_id,entity_type,entity_id,operation,version,changed_by,changed_by_device,
         json.dumps(area_scope or {},ensure_ascii=False),json.dumps(payload or {},ensure_ascii=False,default=str)),
    )
    return int(cur.fetchone()["sequence_no"])


def all_worksite_devices_acked(cur,worksite_id,sequence_no):
    cur.execute(
        """SELECT count(*) AS blockers
           FROM tb.devices d
           JOIN tb.users u ON u.id=d.user_id
           JOIN tb.worksite_group_members gm ON gm.user_id=u.id AND gm.worksite_id=%s
           LEFT JOIN tb.collab_device_state s ON s.device_id=d.id AND s.worksite_id=gm.worksite_id
           WHERE d.revoked_at IS NULL AND u.role='USER' AND u.status='ACTIVE'
             AND COALESCE(s.last_sequence_no,0) < %s""",
        (worksite_id,sequence_no),
    )
    return int(cur.fetchone()["blockers"]) == 0


def safe_attachment_filename(value):
    name=Path((value or "datei").replace("\\","/")).name
    name=re.sub(r"[^A-Za-z0-9ÄÖÜäöüß._() -]+","_",name).strip(" .")[:160]
    return name or "datei"


def sniff_attachment_mime(data,declared=None):
    if data.startswith(b"\xff\xd8\xff"): return "image/jpeg",".jpg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"): return "image/png",".png"
    if len(data)>=12 and data[:4]==b"RIFF" and data[8:12]==b"WEBP": return "image/webp",".webp"
    if data.startswith(b"%PDF-"): return "application/pdf",".pdf"
    raise HTTPException(415,"ATTACHMENT_TYPE_NOT_ALLOWED")


def collab_attachment_parent(cur,access,entity_type,entity_id,write=False,lock=False):
    row=collab_entity_by_id(cur,entity_type,entity_id,lock=lock)
    if not row:
        raise HTTPException(404,"ATTACHMENT_PARENT_NOT_FOUND")
    if not collab_row_accessible(access,row,write=write):
        raise HTTPException(403,"ENTITY_NOT_VISIBLE")
    return row


def cleanup_collab_temp_data():
    """Konservative Retention: erst Tombstone nach ACK aller aktiven Baustellengeräte,
    später physisch bereinigen. Unsynchronisierte Geräte blockieren eine Löschung."""
    files_to_remove=[]
    try:
        with db() as conn, conn.cursor() as cur:
            # 1) Abgelaufene Attachments tombstonen.
            cur.execute("""SELECT id,worksite_id,entity_type,entity_id,storage_path
                           FROM tb.collab_attachments
                           WHERE retain_permanent=false AND deleted_at IS NULL AND expires_at<=now()
                           ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED""")
            for a in cur.fetchall():
                cur.execute("SELECT COALESCE(max(sequence_no),0) AS seq FROM tb.collab_change_log WHERE entity_type='ATTACHMENT' AND entity_id=%s",(a["id"],))
                seq=int(cur.fetchone()["seq"])
                if seq<=0 or not all_worksite_devices_acked(cur,a["worksite_id"],seq): continue
                # Parent-Metadaten liefern Gruppe/Scope für den DELETE-Tombstone.
                parent=collab_entity_by_id(cur,a["entity_type"],a["entity_id"],lock=False)
                group_id=parent.get("group_id") if parent else None
                area_scope=parent.get("area_scope") if parent else {}
                cur.execute("UPDATE tb.collab_attachments SET deleted_at=now() WHERE id=%s",(a["id"],))
                add_collab_change(cur,a["worksite_id"],group_id,"ATTACHMENT",a["id"],"DELETE",None,None,None,area_scope,{})

            # 2) Abgelaufene Fachdaten tombstonen, permanente Attachments halten den Parent fest.
            for entity_type,table in (("BTB","tb.collab_btb_entries"),("DEFECT","tb.collab_defects"),("ACCEPTANCE","tb.collab_acceptances")):
                cur.execute(f"""SELECT id,worksite_id,current_version FROM {table}
                                WHERE retain_permanent=false AND deleted_at IS NULL AND expires_at<=now()
                                ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED""")
                for e in cur.fetchall():
                    cur.execute("""SELECT count(*) AS n FROM tb.collab_attachments
                                   WHERE entity_type=%s AND entity_id=%s AND deleted_at IS NULL AND retain_permanent=true""",
                                (entity_type,e["id"]))
                    if int(cur.fetchone()["n"])>0: continue
                    cur.execute("SELECT COALESCE(max(sequence_no),0) AS seq FROM tb.collab_change_log WHERE entity_type=%s AND entity_id=%s",(entity_type,e["id"]))
                    seq=int(cur.fetchone()["seq"])
                    if seq<=0 or not all_worksite_devices_acked(cur,e["worksite_id"],seq): continue
                    row=collab_entity_by_id(cur,entity_type,e["id"],lock=False)
                    if not row: continue
                    new_version=int(row["current_version"])+1
                    cur.execute(f"UPDATE {table} SET deleted_at=now(),current_version=%s WHERE id=%s",(new_version,e["id"]))
                    add_collab_change(cur,row["worksite_id"],row.get("group_id"),entity_type,row["id"],"DELETE",new_version,None,None,row.get("area_scope") or {},{})
            conn.commit()

        # 3) Physische Bereinigung erst nach weiterer Schonfrist UND ACK des DELETE-Tombstones.
        with db() as conn, conn.cursor() as cur:
            cur.execute("""SELECT id,worksite_id,storage_path FROM tb.collab_attachments
                           WHERE retain_permanent=false AND deleted_at IS NOT NULL
                             AND deleted_at < now()-make_interval(days => %s)
                           ORDER BY deleted_at LIMIT 100 FOR UPDATE SKIP LOCKED""",(COLLAB_PURGE_GRACE_DAYS,))
            for a in cur.fetchall():
                cur.execute("SELECT COALESCE(max(sequence_no),0) AS seq FROM tb.collab_change_log WHERE entity_type='ATTACHMENT' AND entity_id=%s",(a["id"],))
                seq=int(cur.fetchone()["seq"])
                if seq>0 and all_worksite_devices_acked(cur,a["worksite_id"],seq):
                    cur.execute("DELETE FROM tb.collab_attachments WHERE id=%s",(a["id"],))
                    files_to_remove.append(a["storage_path"])
            for entity_type,table in (("BTB","tb.collab_btb_entries"),("DEFECT","tb.collab_defects"),("ACCEPTANCE","tb.collab_acceptances")):
                cur.execute(f"""SELECT id,worksite_id FROM {table}
                                WHERE retain_permanent=false AND deleted_at IS NOT NULL
                                  AND deleted_at < now()-make_interval(days => %s)
                                ORDER BY deleted_at LIMIT 100 FOR UPDATE SKIP LOCKED""",(COLLAB_PURGE_GRACE_DAYS,))
                for e in cur.fetchall():
                    cur.execute("SELECT count(*) AS n FROM tb.collab_attachments WHERE entity_type=%s AND entity_id=%s",(entity_type,e["id"]))
                    if int(cur.fetchone()["n"])>0: continue
                    cur.execute("SELECT COALESCE(max(sequence_no),0) AS seq FROM tb.collab_change_log WHERE entity_type=%s AND entity_id=%s",(entity_type,e["id"]))
                    seq=int(cur.fetchone()["seq"])
                    if seq>0 and all_worksite_devices_acked(cur,e["worksite_id"],seq):
                        cur.execute(f"DELETE FROM {table} WHERE id=%s",(e["id"],))
            conn.commit()
    except Exception:
        return False
    for path in files_to_remove:
        try:
            Path(path).unlink(missing_ok=True)
        except Exception:
            pass
    return True

def get_system_settings():
    rows=q("SELECT * FROM tb.system_settings WHERE id=1")
    if not rows:
        raise RuntimeError("SYSTEM_SETTINGS_MISSING")
    return rows[0]


def filesystem_usage(path: Path):
    try:
        if not path.exists():
            return {"available":False,"path":str(path),"error":"Pfad nicht vorhanden"}
        usage=shutil.disk_usage(path)
        percent=(usage.used / usage.total * 100.0) if usage.total else 0.0
        return {
            "available":True,"path":str(path),"total":usage.total,"used":usage.used,
            "free":usage.free,"percent":round(percent,1)
        }
    except Exception as exc:
        return {"available":False,"path":str(path),"error":str(exc)}


def human_bytes(value):
    if value is None:
        return "0 B"
    n=float(value)
    for unit in ("B","KB","MB","GB","TB","PB"):
        if abs(n) < 1024.0 or unit == "PB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{int(n)} B"
        n/=1024.0


def company_system_stats():
    return q(
        """SELECT c.id,c.name,c.status,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER') AS user_count,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER' AND u.status='ACTIVE'
                     AND (u.access_valid_until IS NULL OR u.access_valid_until>now())) AS active_user_count,
                  (SELECT count(*) FROM tb.users u WHERE u.company_id=c.id AND u.role='USER' AND u.status='ACTIVE'
                     AND u.access_valid_until IS NOT NULL AND u.access_valid_until<=now()) AS expired_user_count,
                  (SELECT count(*) FROM tb.devices d JOIN tb.users u ON u.id=d.user_id
                     WHERE u.company_id=c.id AND d.revoked_at IS NULL) AS active_device_count,
                  (SELECT count(*) FROM tb.worksites w WHERE w.company_id=c.id) AS worksite_count,
                  (SELECT count(*) FROM tb.source_files sf WHERE sf.company_id=c.id) AS file_count,
                  COALESCE((SELECT sum(sf.file_size_bytes) FROM tb.source_files sf WHERE sf.company_id=c.id),0) AS file_bytes
           FROM tb.companies c
           WHERE c.status='ACTIVE'
           ORDER BY c.name"""
    )


