# Schema-10-Ergänzung: Ein gemeinsamer BTB pro TB-Team/Datum/Schicht.
#
# Mehrere Helfer dürfen gleichzeitig in denselben fachlichen BTB schreiben. Der normale
# versionsgeschützte 0.7-Endpunkt bleibt für Mängel/Abnahmen unverändert. BTB-UPSERTs werden
# bei tatsächlich veraltetem Stand gegen den aktuellsten Serverstand gemerged und bei einem
# Rennen kontrolliert erneut versucht. Ein normaler Edit auf exakt der gelesenen Version bleibt
# dagegen ein autoritativer Snapshot, damit bewusst gelöschte/korrigierte Einträge nicht durch
# eine pauschale Listen-Union wieder auftauchen.


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
        line=line.rstrip(); key=line.strip().lower()
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
    # Stammdaten/Zeitfenster: der zuletzt synchronisierte Snapshot ist maßgeblich.
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
            merged=_btb_merge_firma(by_key[key],item); by_key[key]=merged
            idx=next((i for i,x in enumerate(result) if x[0]==key),None)
            if idx is not None: result[idx]=(key,merged)
        else:
            by_key[key]=dict(item); result.append((key,dict(item)))
    return [value for _,value in result]


def merge_team_btb_payload(old_payload, new_payload, device):
    old=old_payload if isinstance(old_payload,dict) else {}
    new=new_payload if isinstance(new_payload,dict) else {}
    out=dict(old)

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
    contributor={"user_id":str(device.get("user_id") or ""),"name":device.get("full_name")}
    contributors=[x for x in contributors if not (
        isinstance(x,dict) and str(x.get("user_id") or "")==contributor["user_id"]
    )]
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


def _btb_is_version_conflict(response):
    if getattr(response,"status_code",None)!=409:
        return False
    try:
        body=getattr(response,"body",b"")
        if isinstance(body,bytes): body=body.decode("utf-8","replace")
        payload=json.loads(body or "{}")
        return payload.get("detail")=="VERSION_CONFLICT"
    except Exception:
        return False


@app.post("/api/v1/collab/mutations")
def collab_mutations_team_btb_merge(req:CollabMutationRequest, authorization: str | None = Header(default=None)):
    if req.operation.upper()!="UPSERT" or req.entity_type.upper()!="BTB":
        return _btb_mutations_original(req,authorization)

    d=require_device(authorization)
    contribution=json.loads(json.dumps(req.payload or {},ensure_ascii=False,default=str))
    client_base_version=req.base_version
    force_merge=False
    last_response=None

    # Der Originalendpunkt sperrt den stabilen Client-Key/Datensatz in seiner eigenen DB-
    # Transaktion. Zwischen unserem Vorlesen und dieser Sperre kann trotzdem ein anderer Helfer
    # gewinnen. In diesem seltenen Rennen lesen/mergen wir bis zu fünfmal neu.
    for _attempt in range(5):
        with db() as conn, conn.cursor() as cur:
            access=load_worksite_access(cur,d,req.worksite_id)
            group_id=access["group_id"]
            if req.group_id and parse_uuid_value(req.group_id,"GROUP_ID_INVALID")!=group_id:
                raise HTTPException(403,"GROUP_WORKSITE_MISMATCH")

            row=None
            if req.entity_id:
                row=collab_entity_by_id(cur,"BTB",req.entity_id,lock=False)
                if row and (row["worksite_id"]!=access["worksite_id"] or row.get("group_id")!=group_id):
                    raise HTTPException(403,"ENTITY_NOT_VISIBLE")
            if row is None and req.client_entity_key:
                row=collab_lookup_by_client_key(
                    cur,"BTB",access["worksite_id"],group_id,req.client_entity_key,lock=False
                )
            if row and row.get("deleted_at") is not None:
                raise HTTPException(409,"ENTITY_ALREADY_DELETED")

            if row:
                current_version=int(row["current_version"])
                req.entity_id=str(row["id"])
                req.base_version=current_version

                # Exakt die Version bearbeitet, die der Client gelesen hatte: sein Snapshot darf
                # auch Einträge entfernen/korrigieren. Nur ein wirklich veralteter/paralleler
                # Snapshot wird additiv mit dem inzwischen neueren Team-BTB zusammengeführt.
                exact_base=(
                    not force_merge
                    and client_base_version is not None
                    and int(client_base_version)==current_version
                )
                req.payload=merge_team_btb_payload(
                    {} if exact_base else (row.get("payload") or {}),
                    contribution,
                    d,
                )
            else:
                req.entity_id=None
                req.base_version=None
                req.payload=merge_team_btb_payload({},contribution,d)
            req.group_id=str(group_id)
            conn.commit()

        last_response=_btb_mutations_original(req,authorization)
        if not _btb_is_version_conflict(last_response):
            return last_response

        # Nach einem CREATE-/UPDATE-Rennen erneut gegen den jetzt aktuellen Serverstand mergen.
        # Die konfliktbehaftete Entity-ID/Basis darf die Suche über den stabilen Client-Key nicht
        # blockieren. Ab hier ist additive Merge-Strategie zwingend, weil Paralleländerung vorliegt.
        force_merge=True
        req.entity_id=None
        req.base_version=None

    return last_response


del _btb_route, _btb_mutation_routes
