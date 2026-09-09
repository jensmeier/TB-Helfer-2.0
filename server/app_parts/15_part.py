# Schema-10-Ergänzung: Ein gemeinsamer BTB pro TB-Team/Datum/Schicht.
#
# Mehrere Helfer dürfen gleichzeitig in denselben fachlichen BTB schreiben. Der bisherige
# versionsgeschützte 0.7-Endpunkt schützt zwar vor Datenverlust, würde bei 20/30 Helfern aber
# regelmäßig 409-Konflikte erzeugen. Für BTB-UPSERTs derselben Gruppe und desselben stabilen
# Client-Keys werden deshalb ergänzende Listen/Abschnitte serverseitig zusammengeführt.
# Mängel/Abnahmen behalten weiterhin die normale 409-Logik.


def _btb_json_key(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",",":"), default=str)


def _btb_unique_list(old, new):
    result=[]; seen=set()
    for value in list(old or []) + list(new or []):
        key=_btb_json_key(value)
        if key in seen: continue
        seen.add(key); result.append(value)
    return result


def _btb_merge_lines(old, new):
    a=str(old or "").strip(); b=str(new or "").strip()
    if not a: return b
    if not b: return a
    if a==b: return a
    result=[]; seen=set()
    for line in (a+"\n"+b).splitlines():
        line=line.rstrip()
        key=line.strip().lower()
        if not key or key in seen: continue
        seen.add(key); result.append(line)
    return "\n".join(result)


def _btb_merge_items(old, new):
    old_items=(old or {}).get("items") if isinstance(old,dict) else []
    new_items=(new or {}).get("items") if isinstance(new,dict) else []
    return {"items":_btb_unique_list(old_items,new_items)}


def _btb_merge_firma(old, new):
    if not isinstance(old,dict): old={}
    if not isinstance(new,dict): new={}
    out=dict(old)
    # Stammdaten/Zeitfenster: der zuletzt synchronisierte Stand ist maßgeblich.
    for key,value in new.items():
        if key not in ("personal","geraete","leistungen"):
            out[key]=value
    out["personal"]=_btb_unique_list(old.get("personal",[]),new.get("personal",[]))
    out["geraete"]=_btb_unique_list(old.get("geraete",[]),new.get("geraete",[]))
    out["leistungen"]=_btb_unique_list(old.get("leistungen",[]),new.get("leistungen",[]))
    return out


def _btb_merge_firmen(old, new):
    result=[]; by_key={}
    for item in list(old or []) + list(new or []):
        if not isinstance(item,dict): continue
        key=str(item.get("firmaId") or "").strip()
        if not key:
            key="name:"+str(item.get("name") or "").strip().lower()
        if not key:
            key="anon:"+_btb_json_key(item)
        if key in by_key:
            merged=_btb_merge_firma(by_key[key],item)
            by_key[key]=merged
            idx=next((i for i,x in enumerate(result) if x[0]==key),None)
            if idx is not None: result[idx]=(key,merged)
        else:
            by_key[key]=dict(item); result.append((key,dict(item)))
    return [value for _,value in result]


def merge_team_btb_payload(old_payload, new_payload, device):
    old=old_payload if isinstance(old_payload,dict) else {}
    new=new_payload if isinstance(new_payload,dict) else {}
    out=dict(old)

    # Normale Einzelwerte: eingehender Snapshot gewinnt, wenn das Feld geliefert wird.
    for key,value in new.items():
        if key in (
            "firmen","personal","geraete","notizen","abnahmen","einweisungUnterweisung",
            "besonderheiten","maengelSchaeden","_syncContributors"
        ):
            continue
        out[key]=value

    out["firmen"]=_btb_merge_firmen(old.get("firmen",[]),new.get("firmen",[]))
    out["personal"]=_btb_unique_list(old.get("personal",[]),new.get("personal",[]))
    out["geraete"]=_btb_unique_list(old.get("geraete",[]),new.get("geraete",[]))
    out["notizen"]=_btb_merge_lines(old.get("notizen"),new.get("notizen"))
    for key in ("abnahmen","einweisungUnterweisung","besonderheiten","maengelSchaeden"):
        out[key]=_btb_merge_items(old.get(key),new.get(key))

    contributors=_btb_unique_list(old.get("_syncContributors",[]),new.get("_syncContributors",[]))
    contributor={
        "user_id":str(device.get("user_id") or ""),
        "name":device.get("full_name"),
    }
    # Je Nutzer nur einen aktuellen Eintrag halten; die Information ist rein audit-/anzeigefähig
    # und wird von älteren Android-Versionen einfach ignoriert.
    contributors=[x for x in contributors if not (isinstance(x,dict) and str(x.get("user_id") or "")==contributor["user_id"])]
    contributors.append(contributor)
    out["_syncContributors"]=contributors
    return out


_btb_mutation_routes=[
    route for route in list(app.router.routes)
    if getattr(route,"path",None)=="/api/v1/collab/mutations"
    and "POST" in (getattr(route,"methods",set()) or set())
]
if len(_btb_mutation_routes)!=1:
    raise RuntimeError(f"COLLAB_MUTATION_ROUTE_COUNT_SCHEMA10:{len(_btb_mutation_routes)}")
_btb_mutations_original=_btb_mutation_routes[0].endpoint
for _btb_route in _btb_mutation_routes:
    app.router.routes.remove(_btb_route)


@app.post("/api/v1/collab/mutations")
def collab_mutations_team_btb_merge(req:CollabMutationRequest, authorization: str | None = Header(default=None)):
    if req.operation.upper()=="UPSERT" and req.entity_type.upper()=="BTB":
        d=require_device(authorization)
        with db() as conn, conn.cursor() as cur:
            access=load_worksite_access(cur,d,req.worksite_id)
            group_id=access["group_id"]
            if req.group_id and parse_uuid_value(req.group_id,"GROUP_ID_INVALID")!=group_id:
                raise HTTPException(403,"GROUP_WORKSITE_MISMATCH")

            row=None
            if req.entity_id:
                row=collab_entity_by_id(cur,"BTB",req.entity_id,lock=False)
            elif req.client_entity_key:
                row=collab_lookup_by_client_key(
                    cur,"BTB",access["worksite_id"],group_id,req.client_entity_key,lock=False
                )
            if row:
                if row["worksite_id"]!=access["worksite_id"] or row.get("group_id")!=group_id:
                    raise HTTPException(403,"ENTITY_NOT_VISIBLE")
                if row.get("deleted_at") is not None:
                    raise HTTPException(409,"ENTITY_ALREADY_DELETED")
                req.payload=merge_team_btb_payload(row.get("payload") or {},req.payload or {},d)
                # BTB ist bewusst gemeinschaftlich. Wir haben direkt gegen den aktuellen Serverstand
                # gemerged und können deshalb genau diese aktuelle Version als Basis verwenden.
                req.entity_id=str(row["id"])
                req.base_version=int(row["current_version"])
                req.group_id=str(group_id)
            else:
                req.group_id=str(group_id)
                req.payload=merge_team_btb_payload({},req.payload or {},d)
            conn.commit()
    return _btb_mutations_original(req,authorization)


del _btb_route, _btb_mutation_routes
