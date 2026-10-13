# API 0.7.1 / Schema 13: gemeinsamer Baustellenbaum (Unterbaustellen) je Server-Baustelle.
#
# Der bestehende Collaboration-Transport bleibt maßgeblich. WORKSITE_NODE nutzt dieselbe
# Idempotenz-/Versionierungslogik wie Mängel/Abnahmen, ist aber fachlich baustellenweit sichtbar.
# Nur verantwortliche TBs (LEAD) dürfen Strukturknoten schreiben; Helfer lesen denselben Baum.

SERVER_VERSION = "0.7.1"
app.version = SERVER_VERSION

COLLAB_STATUSES["WORKSITE_NODE"] = {"ACTIVE"}

_tb_worksite_node_table_info_original = collab_table_info
_tb_worksite_node_retention_original = retention_for_user
_tb_worksite_node_row_accessible_original = collab_row_accessible
_tb_worksite_node_change_visible_original = collab_change_visible


def collab_table_info(entity_type):
    t=(entity_type or "").upper()
    if t=="WORKSITE_NODE":
        return t,"tb.collab_worksite_nodes","client_node_key"
    return _tb_worksite_node_table_info_original(entity_type)


def retention_for_user(cur,user_id,kind,existing_retain=False):
    if (kind or "").upper()=="WORKSITE_NODE":
        # Die Baustellenstruktur ist kein temporäres Medien-/Berichtsobjekt. Sie lebt so lange
        # wie die Baustelle und wird bei deren Löschung über ON DELETE CASCADE entfernt.
        return True,None
    return _tb_worksite_node_retention_original(cur,user_id,kind,existing_retain)


def collab_row_accessible(access,row,write=False):
    if row and row.get("worksite_id")==access.get("worksite_id"):
        # Tabelleninformation ist bei dieser Hilfsfunktion nicht direkt vorhanden. WORKSITE_NODE
        # ist eindeutig an seinem Tabellen-Payload mit node_type erkennbar.
        payload=row.get("payload") or {}
        if isinstance(payload,dict) and payload.get("node_type") in ("FREIE_STRECKE","BAHNHOF","ALLGEMEIN_OBJEKT"):
            if row.get("deleted_at") is not None:
                return False
            return (not write) or str(access.get("member_role") or "").upper()=="LEAD"
    return _tb_worksite_node_row_accessible_original(access,row,write)


def collab_change_visible(access,row):
    if (row.get("entity_type") or "").upper()=="WORKSITE_NODE":
        # load_worksite_access() wurde für exakt diese Baustelle bereits ausgeführt. Die Struktur
        # ist damit für jedes Mitglied sichtbar, unabhängig von BTB-Gruppe/Helferbereich.
        return True
    return _tb_worksite_node_change_visible_original(access,row)


def _tb_node_payload_validate(cur, access, req):
    payload=req.payload if isinstance(req.payload,dict) else {}
    node_type=str(payload.get("node_type") or "").strip().upper()
    if node_type not in ("FREIE_STRECKE","BAHNHOF","ALLGEMEIN_OBJEKT"):
        raise HTTPException(400,"WORKSITE_NODE_TYPE_INVALID")
    payload["node_type"]=node_type

    client_key=str(req.client_entity_key or "").strip()
    if not client_key:
        raise HTTPException(400,"CLIENT_ENTITY_KEY_REQUIRED")
    parent_raw=payload.get("parent_client_node_key")
    parent_key=None if parent_raw is None else str(parent_raw).strip() or None
    if parent_key==client_key:
        raise HTTPException(409,"WORKSITE_NODE_PARENT_SELF")
    payload["parent_client_node_key"]=parent_key

    def optional_number(name):
        value=payload.get(name)
        if value is None: return None
        try: return float(value)
        except (TypeError,ValueError): raise HTTPException(400,"WORKSITE_NODE_KM_INVALID")

    km_from=optional_number("km_from"); km_to=optional_number("km_to")
    if km_from is not None: payload["km_from"]=km_from
    if km_to is not None: payload["km_to"]=km_to
    if km_from is not None and km_to is not None and km_from>km_to:
        raise HTTPException(400,"WORKSITE_NODE_KM_ORDER_INVALID")

    if node_type=="FREIE_STRECKE":
        route_key=str(payload.get("route_key") or "").strip()
        if not re.fullmatch(r"\d{4}-[0-2]",route_key):
            raise HTTPException(400,"WORKSITE_NODE_ROUTE_KEY_INVALID")
        if km_from is None or km_to is None:
            raise HTTPException(400,"WORKSITE_NODE_KM_REQUIRED")
        payload["route_key"]=route_key
    elif node_type=="BAHNHOF":
        station=str(payload.get("station_name") or "").strip()
        track=str(payload.get("track_or_switch") or "").strip()
        if not station or not track:
            raise HTTPException(400,"WORKSITE_NODE_STATION_TRACK_REQUIRED")
        payload["station_name"]=station
        payload["track_or_switch"]=track

    # Parent muss bereits als aktiver Knoten derselben Baustelle existieren. Android stellt beim
    # ersten Upload deshalb Eltern vor Kinder in die vorhandene Queue.
    seen={client_key}
    cursor=parent_key
    while cursor:
        if cursor in seen:
            raise HTTPException(409,"WORKSITE_NODE_PARENT_CYCLE")
        seen.add(cursor)
        cur.execute(
            """SELECT client_node_key,payload->>'parent_client_node_key' AS parent_key
               FROM tb.collab_worksite_nodes
               WHERE worksite_id=%s AND client_node_key=%s AND deleted_at IS NULL""",
            (access["worksite_id"],cursor),
        )
        parent=cur.fetchone()
        if not parent:
            raise HTTPException(409,"WORKSITE_NODE_PARENT_NOT_FOUND")
        cursor=(parent.get("parent_key") or "").strip() or None

    req.payload=payload


# Den nach Schema 10 aktiven /api/v1/collab/mutations-Endpunkt um genau WORKSITE_NODE ergänzen.
# Alle anderen Typen (insbesondere der Team-BTB-Merge) laufen unverändert durch den bisherigen
# finalen Endpoint aus 15_part.py.
_tb_node_mutation_routes=[
    route for route in list(app.router.routes)
    if getattr(route,"path",None)=="/api/v1/collab/mutations"
    and "POST" in (getattr(route,"methods",set()) or set())
]
if len(_tb_node_mutation_routes)!=1:
    raise RuntimeError(f"COLLAB_MUTATION_ROUTE_COUNT_SCHEMA13:{len(_tb_node_mutation_routes)}")
_tb_node_mutations_original=_tb_node_mutation_routes[0].endpoint
for _tb_node_route in _tb_node_mutation_routes:
    app.router.routes.remove(_tb_node_route)


@app.post("/api/v1/collab/mutations")
def collab_mutations_with_worksite_nodes(req:CollabMutationRequest, authorization: str | None = Header(default=None)):
    if req.entity_type.upper()!="WORKSITE_NODE":
        return _tb_node_mutations_original(req,authorization)

    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,req.worksite_id)
        if str(access.get("member_role") or "").upper()!="LEAD":
            raise HTTPException(403,"WORKSITE_STRUCTURE_WRITE_FORBIDDEN")
        if req.operation.upper()=="UPSERT":
            _tb_node_payload_validate(cur,access,req)
        elif req.operation.upper()!="DELETE":
            raise HTTPException(400,"OPERATION_INVALID")
        conn.commit()

    # Struktur ist immer baustellenweit; ein Client darf sie nicht über Helfer-Scope verstecken.
    req.status="ACTIVE"
    req.area_scope={"all":True}
    return _tb_node_mutations_original(req,authorization)


del _tb_node_route, _tb_node_mutation_routes


@app.get("/api/v1/collab/worksites/{worksite_id}/nodes")
def collab_worksite_nodes(worksite_id:str, authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        access=load_worksite_access(cur,d,worksite_id)
        cur.execute(
            """SELECT id,client_node_key,status,payload,current_version,created_at,updated_at,
                      (deleted_at IS NOT NULL) AS is_deleted
               FROM tb.collab_worksite_nodes
               WHERE worksite_id=%s
               ORDER BY created_at,id""",
            (access["worksite_id"],),
        )
        return {"status":"ok","worksite_id":str(access["worksite_id"]),"nodes":cur.fetchall()}
