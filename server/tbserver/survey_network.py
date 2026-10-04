"""REF-DATA-016: one working-topology contract for admin, portal and device clients.

No endpoint in this module writes original geometry or kilometer calibration.
All writers serialize with the survey workspace lock (including source resets).
"""
from __future__ import annotations
import hashlib
import json
import math
from datetime import datetime
from typing import Literal
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field, model_validator
from psycopg.types.json import Jsonb
from .database import transaction
from .portal import current_portal_user, require_csrf
from .survey_workspace import Anchor, _point, _branch_start, _lock

router = APIRouter()

class StrictModel(BaseModel):
    model_config = ConfigDict(extra='forbid')

class Position(StrictModel):
    lon: float = Field(ge=-180, le=180, allow_inf_nan=False)
    lat: float = Field(ge=-90, le=90, allow_inf_nan=False)
    accuracy_m: float | None = Field(default=None, ge=0, le=100000, allow_inf_nan=False)
    altitude_m: float | None = Field(default=None, allow_inf_nan=False)
    captured_at: datetime | None = None
    method: Literal['MAP', 'GPS'] = 'MAP'

class AxisBinding(Anchor):
    model_config = ConfigDict(extra='forbid')

class KilometerObservation(StrictModel):
    id: UUID
    route_number: str = Field(min_length=1,max_length=80)
    km_m: float = Field(allow_inf_nan=False)
    method: Literal['MANUAL_READING'] = 'MANUAL_READING'
    observed_at: datetime | None = None

class Node(StrictModel):
    id: UUID
    base_version: int = Field(ge=0)
    entity_type: Literal['NODE'] = 'NODE'
    station_code: str = Field(min_length=1,max_length=50)
    active: bool = True
    position: Position | None = None
    binding: AxisBinding | None = None
    km_observations: list[KilometerObservation] = Field(default_factory=list,max_length=8)
    note: str = Field(default='',max_length=1000)

    @model_validator(mode='after')
    def one_position(self):
        if (self.position is None) == (self.binding is None):
            raise ValueError('Exactly one position or axis binding is required')
        return self

class Endpoint(StrictModel):
    role: Literal['START','END','WA','WE','WEZ']
    node_id: UUID
    node_version: int = Field(ge=1)

class CatalogLink(StrictModel):
    id: UUID
    version: int = Field(ge=1)

class NetworkObject(StrictModel):
    id: UUID
    base_version: int = Field(ge=0)
    entity_type: Literal['OBJECT'] = 'OBJECT'
    station_code: str = Field(min_length=1,max_length=50)
    active: bool = True
    kind: Literal['TRACK','LINE','SWITCH']
    name: str = Field(min_length=1,max_length=160)
    route_number: str | None = Field(default=None,max_length=80)
    track_key: str | None = Field(default=None,max_length=80)
    owner_axis_id: UUID | None = None
    catalog_link: CatalogLink | None = None
    endpoints: list[Endpoint] = Field(min_length=2,max_length=3)
    note: str = Field(default='',max_length=1000)

    @model_validator(mode='after')
    def roles(self):
        expected={'WA','WE','WEZ'} if self.kind=='SWITCH' else {'START','END'}
        if len(self.endpoints)!=len(expected) or {p.role for p in self.endpoints}!=expected:
            raise ValueError('Endpoint roles do not match object kind')
        if len({p.node_id for p in self.endpoints})!=len(expected):
            raise ValueError('An object requires distinct endpoints')
        if not self.name.strip() or (self.kind=='LINE' and not (self.route_number or '').strip()):
            raise ValueError('Name and line route number are required')
        return self

class Mutation(StrictModel):
    schema_version: Literal[1] = 1
    client_change_id: str = Field(min_length=1,max_length=160)
    nodes: list[Node] = Field(default_factory=list,max_length=32)
    objects: list[NetworkObject] = Field(default_factory=list,max_length=32)
    # Moving/deactivating a shared point requires acknowledgment of every attached object/version.
    affected_objects: dict[UUID,int] = Field(default_factory=dict,max_length=200)
    preview_token: str | None = None

    @model_validator(mode='after')
    def unique_ids(self):
        ids=[r.id for r in [*self.nodes,*self.objects]]
        if not ids or len(ids)!=len(set(ids)):
            raise ValueError('Nonempty mutation with unique IDs required')
        return self


def _catalog():
    from . import survey_catalog
    return survey_catalog


def _hash(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def _payload_hash(payload):
    data=payload.model_dump(mode='json',exclude={'preview_token'})
    # Preserve receipt fingerprints for retries created before the additive link field.
    # Omitted means preserve; explicit null means unlink and must hash differently.
    for item,row in zip(payload.objects,data['objects']):
        if 'catalog_link' not in item.model_fields_set:
            row.pop('catalog_link',None)
    return _hash(data)


def _load(cur, ids):
    if not ids: return {}
    cur.execute('SELECT data FROM tb.survey_network_entities WHERE id=ANY(%s) ORDER BY id FOR UPDATE',(list(ids),))
    return {r['data']['id']:dict(r['data']) for r in cur.fetchall()}


def _source(cur, binding):
    c=_catalog()
    cur.execute("""SELECT s.*,a.current_version axis_version FROM tb.survey_catalog_segments s
      JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
      WHERE s.id=%s AND a.id=%s AND a.scope='GLOBAL' AND a.status<>'ARCHIVED'
      AND s.segment_status='ACTIVE' AND s.source_kind='ORIGINAL_TRA' FOR SHARE OF a,s""",
      (binding['segment_id'],binding['axis_id']))
    row=cur.fetchone()
    if not row or row['current_version']!=binding['segment_version']:
        raise HTTPException(409,'NETWORK_SOURCE_CHANGED')
    return dict(row),c._segment_points(cur,dict(row))


def _resolve_node(cur, item):
    c=_catalog();data=item.model_dump(mode='json',exclude={'base_version'})
    if not item.station_code.strip(): raise HTTPException(400,'NETWORK_STATION_REQUIRED')
    data.update(version=item.base_version+1,station_code=item.station_code.strip().upper())
    if item.binding:
        binding=item.binding.model_dump(mode='json');row,points=_source(cur,binding)
        if row['axis_version']!=binding['axis_version']: raise HTTPException(409,'NETWORK_SOURCE_CHANGED')
        p=_point(points,binding['station_m'])
        coords=c._postgis_transform_points(cur,[(p['x_dbref'],p['y_dbref'])])
        if not coords or coords[0] is None: raise HTTPException(400,'NETWORK_CRS_UNSUPPORTED')
        data['position']={'lon':coords[0][0],'lat':coords[0][1],'method':'ORIGINAL_AXIS'}
        data['binding']['source_original_id']=str(row['source_original_id'])
    data['km_status']='REFERENCE_ONLY' if item.binding else 'UNKNOWN'
    return data


def _line_distance(a,b):
    lat=math.radians((a['lat']+b['lat'])/2)
    return math.hypot((a['lon']-b['lon'])*111320*math.cos(lat),(a['lat']-b['lat'])*111320)


def _path(cur, start, end, role):
    c=_catalog();a=start.get('binding');b=end.get('binding');points=None
    if a and b and a['segment_id']==b['segment_id']:
        _,full=_source(cur,a);lo,hi=sorted([a['station_m'],b['station_m']])
        points=c._clip_station_points(full,lo,hi)
        if a['station_m']>b['station_m']: points=list(reversed(points))
    elif role=='BRANCH' and a and b:
        _,main=_source(cur,a);_,branch=_source(cur,b)
        try:
            _,station=_branch_start(_point(main,a['station_m']),branch)
            points=c._clip_station_points(branch,*sorted([station,b['station_m']]))
            if station>b['station_m']: points=list(reversed(points))
        except HTTPException:
            points=None  # No claimed curve without a verifiable connection to the selected branch.
    if points and len(points)>=2:
        xy=c._decimate(points,1500)
        coordinates=c._postgis_transform_points(cur,[(p['x_dbref'],p['y_dbref']) for p in xy])
        if any(p is None for p in coordinates): raise HTTPException(400,'NETWORK_CRS_UNSUPPORTED')
        length=abs(points[-1]['station_m']-points[0]['station_m'])
        quality='ORIGINAL_AXIS'
    else:
        coordinates=[[n['position']['lon'],n['position']['lat']] for n in [start,end]]
        length=_line_distance(start['position'],end['position']);quality='MANUAL_APPROXIMATION'
    if length<.1: raise HTTPException(400,'NETWORK_LENGTH_INVALID')
    return dict(role=role,coordinates=coordinates,length_m=length,geometry_quality=quality,
                km_status='UNCHANGED_REFERENCE' if a and b else 'UNKNOWN')


def _paths(cur, obj, nodes):
    byrole={e['role']:nodes[e['node_id']] for e in obj['endpoints']}
    pairs=[('WA','WE','MAIN'),('WA','WEZ','BRANCH')] if obj['kind']=='SWITCH' else [('START','END',obj['kind'])]
    return [_path(cur,byrole[a],byrole[b],role) for a,b,role in pairs]


def _prepare(cur,payload):
    ids={r.id for r in [*payload.nodes,*payload.objects]}
    ids.update(p.node_id for o in payload.objects for p in o.endpoints)
    old=_load(cur,ids);resolved={};affected={}
    for item in [*payload.nodes,*payload.objects]:
        prev=old.get(str(item.id))
        if (prev['version'] if prev else 0)!=item.base_version:
            raise HTTPException(409,{'code':'NETWORK_VERSION_CONFLICT','id':str(item.id)})
        if prev and prev['entity_type']!=item.entity_type: raise HTTPException(409,'NETWORK_ID_TYPE_CONFLICT')
        if prev and prev['station_code']!=item.station_code.strip().upper():
            raise HTTPException(409,'NETWORK_STATION_IMMUTABLE')
    changed_nodes={str(n.id) for n in payload.nodes if n.base_version>0}
    if changed_nodes:
        cur.execute("""SELECT data FROM tb.survey_network_entities WHERE entity_type='OBJECT' AND active
          AND EXISTS (SELECT 1 FROM jsonb_array_elements(data->'endpoints') p WHERE p->>'node_id'=ANY(%s))
          ORDER BY id FOR UPDATE""",(list(changed_nodes),))
        affected={r['data']['id']:r['data'] for r in cur.fetchall()}
        expected={str(k):v for k,v in payload.affected_objects.items()}
        if expected!={k:v['version'] for k,v in affected.items()}:
            raise HTTPException(409,{'code':'NETWORK_SHARED_POINT_CONFIRM','objects':list(affected.values())})
    for item in payload.nodes:
        if not item.active and str(item.id) in old:
            resolved[str(item.id)]={**old[str(item.id)],'active':False,'version':item.base_version+1}
        else:
            resolved[str(item.id)]=_resolve_node(cur,item)
    available={**old,**resolved}
    for item in payload.objects:
        data=item.model_dump(mode='json',exclude={'base_version'})
        data.update(version=item.base_version+1,station_code=item.station_code.strip().upper(),name=item.name.strip())
        if not data['station_code']: raise HTTPException(400,'NETWORK_STATION_REQUIRED')
        # Older clients omit this additive field: preserve an existing explicit identity link.
        if 'catalog_link' not in item.model_fields_set:
            data['catalog_link'] = old.get(str(item.id), {}).get('catalog_link')
        link = data.get('catalog_link')
        if link:
            cur.execute("""SELECT o.id,o.current_version,o.object_type,s.station_code
              FROM tb.catalog_objects o LEFT JOIN tb.catalog_stations s ON s.id=o.station_id
              WHERE o.id=%s FOR SHARE OF o""", (link['id'],))
            ref = cur.fetchone()
            if not ref or ref['current_version'] != link['version']:
                raise HTTPException(409,'NETWORK_CATALOG_CHANGED')
            if ref['object_type'] != {'TRACK':'TRACK','SWITCH':'SWITCH','LINE':'OTHER'}[item.kind]:
                raise HTTPException(400,'NETWORK_CATALOG_TYPE_MISMATCH')
            if ref['station_code'] and ref['station_code'] != data['station_code']:
                raise HTTPException(400,'NETWORK_CATALOG_STATION_MISMATCH')
            cur.execute("""SELECT id FROM tb.survey_network_entities
              WHERE data->'catalog_link'->>'id'=%s AND id<>%s LIMIT 1""", (str(link['id']), item.id))
            if cur.fetchone() or any(r.get('catalog_link',{}).get('id') == link['id'] for r in resolved.values() if r.get('catalog_link')):
                raise HTTPException(409,'NETWORK_CATALOG_ALREADY_LINKED')
        if item.owner_axis_id:
            cur.execute("SELECT id FROM tb.survey_catalog_axes WHERE id=%s AND scope='GLOBAL' AND status<>'ARCHIVED' FOR SHARE",(item.owner_axis_id,))
            if not cur.fetchone(): raise HTTPException(409,'NETWORK_SOURCE_CHANGED')
        for endpoint in item.endpoints:
            node=available.get(str(endpoint.node_id))
            if not node or node['entity_type']!='NODE' or (item.active and not node['active']):
                raise HTTPException(409,'NETWORK_NODE_UNAVAILABLE')
            if endpoint.node_version!=node['version']: raise HTTPException(409,'NETWORK_NODE_CHANGED')
        data['paths']=_paths(cur,data,available) if item.active else []
        resolved[str(item.id)]=data
    # All linked objects must be updated atomically when their point is changed.
    if set(affected)-{str(o.id) for o in payload.objects}:
        raise HTTPException(409,'NETWORK_SHARED_OBJECTS_REQUIRED')
    return resolved,old


def _result(resolved):
    records=list(resolved.values())
    return {'schema_version':1,'entities':records,'warnings':[
        'Manuelle Verbindung: Verlauf und Länge sind nur eine Näherung. Bahnkilometer bleiben unverändert.'
    ] if any(p['geometry_quality']=='MANUAL_APPROXIMATION' for r in records for p in r.get('paths',[])) else []}


@router.post('/network/preview')
def preview(payload:Mutation,user:dict=Depends(require_csrf)):
    with transaction() as conn,conn.cursor() as cur:
        _lock(cur);resolved,_=_prepare(cur,payload)
        return {**_result(resolved),'preview_token':_hash({'input':_payload_hash(payload),'resolved':resolved})}


@router.post('/network/mutations')
def mutate(payload:Mutation,user:dict=Depends(require_csrf)):
    fingerprint=_payload_hash(payload)
    with transaction() as conn,conn.cursor() as cur:
        _lock(cur)
        cur.execute('SELECT fingerprint,response FROM tb.survey_network_receipts WHERE user_id=%s AND client_change_id=%s',(user['user_id'],payload.client_change_id))
        receipt=cur.fetchone()
        if receipt:
            if receipt['fingerprint']!=fingerprint: raise HTTPException(409,'NETWORK_REQUEST_CHANGED')
            return receipt['response']
        resolved,old=_prepare(cur,payload)
        if payload.preview_token!=_hash({'input':fingerprint,'resolved':resolved}):
            raise HTTPException(409,'NETWORK_PREVIEW_REQUIRED')
        for key,data in resolved.items():
            cur.execute("""INSERT INTO tb.survey_network_entities(id,entity_type,version,active,station_code,data)
              VALUES (%s,%s,%s,%s,%s,%s) ON CONFLICT(id) DO UPDATE SET version=EXCLUDED.version,
              active=EXCLUDED.active,station_code=EXCLUDED.station_code,data=EXCLUDED.data,updated_at=now()""",
              (key,data['entity_type'],data['version'],data['active'],data['station_code'],Jsonb(data)))
        # One feed event per atomic mutation, never split shared-node/object updates over pages.
        cur.execute('INSERT INTO tb.survey_network_changes(station_codes,data,actor_user_id,actor_company_id) VALUES (%s,%s,%s,%s) RETURNING change_id',
                    (sorted({d['station_code'] for d in resolved.values()}),Jsonb(_result(resolved)),user['user_id'],user.get('company_id')))
        cursor=cur.fetchone()['change_id']
        response={**_result(resolved),'commit_cursor':cursor}
        cur.execute('INSERT INTO tb.survey_network_receipts(user_id,client_change_id,fingerprint,response) VALUES (%s,%s,%s,%s)',
                    (user['user_id'],payload.client_change_id,fingerprint,Jsonb(response)))
        return response


def _source_review(cur, records):
    c=_catalog();copies=json.loads(json.dumps(records))
    node_ids={e['node_id'] for r in copies if r['entity_type']=='OBJECT' for e in r['endpoints']}
    nodes={r['id']:r for r in copies if r['entity_type']=='NODE'}
    missing=node_ids-set(nodes)
    if missing:
        cur.execute('SELECT data FROM tb.survey_network_entities WHERE id=ANY(%s)',([UUID(k) for k in missing],))
        nodes.update({r['data']['id']:r['data'] for r in cur.fetchall()})
    stale=set()
    for key,node in nodes.items():
        binding=node.get('binding')
        if not binding: continue
        cur.execute("""SELECT s.current_version FROM tb.survey_catalog_segments s
          JOIN tb.survey_catalog_axes a ON a.id=s.axis_id WHERE s.id=%s AND a.id=%s
          AND a.scope='GLOBAL' AND a.status<>'ARCHIVED' AND s.segment_status='ACTIVE'""",
          (binding['segment_id'],binding['axis_id']))
        row=cur.fetchone()
        if not row or row['current_version']!=binding['segment_version']: stale.add(key)
    for record in copies:
        record['needs_review']=record['id'] in stale if record['entity_type']=='NODE' else any(e['node_id'] in stale for e in record['endpoints'])
        if record['entity_type']=='OBJECT' and record['needs_review']: record['paths']=[]
    return copies


@router.get('/network/entities')
def entities(station_code:str|None=Query(default=None,min_length=1,max_length=50),after_id:UUID|None=None,
             limit:int=Query(default=200,ge=1,le=500),user:dict=Depends(current_portal_user)):
    with transaction() as conn,conn.cursor() as cur:
        cur.execute("""SELECT data FROM tb.survey_network_entities WHERE (%s::text IS NULL OR station_code=%s)
          AND (%s::uuid IS NULL OR id>%s::uuid) ORDER BY id LIMIT %s""",
          (station_code.strip().upper() if station_code else None,
           station_code.strip().upper() if station_code else None,after_id,after_id,limit+1))
        rows=[r['data'] for r in cur.fetchall()]
        return dict(schema_version=1,entities=_source_review(cur,rows[:limit]),has_more=len(rows)>limit,
                    next_id=rows[limit-1]['id'] if len(rows)>limit else None)


@router.get('/network/entities/{entity_id}')
def entity(entity_id:UUID,user:dict=Depends(current_portal_user)):
    with transaction() as conn,conn.cursor() as cur:
        cur.execute('SELECT data FROM tb.survey_network_entities WHERE id=%s',(entity_id,))
        row=cur.fetchone()
        if not row: raise HTTPException(404,'NETWORK_NOT_FOUND')
        cur.execute("""SELECT data FROM tb.survey_network_entities WHERE entity_type='OBJECT' AND active
          AND EXISTS (SELECT 1 FROM jsonb_array_elements(data->'endpoints') p WHERE p->>'node_id'=%s) ORDER BY id""",(str(entity_id),))
        attached=[r['data'] for r in cur.fetchall()]
        return dict(schema_version=1,entity=_source_review(cur,[row['data']])[0],attached_objects=attached)


@router.get('/network/entities/{entity_id}/history')
def entity_history(entity_id:UUID,user:dict=Depends(current_portal_user)):
    """Neutral field changes; actor/company details never leak into the shared feed."""
    with transaction() as conn,conn.cursor() as cur:
        cur.execute("SELECT data FROM tb.survey_network_entities WHERE id=%s", (entity_id,))
        current=cur.fetchone()
        if not current: raise HTTPException(404,'NETWORK_NOT_FOUND')
        cur.execute("""WITH snapshots AS (
          SELECT c.change_id,c.changed_at,e.value AS snapshot,
            lag(e.value) OVER (PARTITION BY e.value->>'id' ORDER BY c.change_id) AS previous
          FROM tb.survey_network_changes c
          CROSS JOIN LATERAL jsonb_array_elements(c.data->'entities') e(value)
          WHERE c.station_codes @> %s::text[]
            AND (e.value->>'id'=%s OR e.value->>'id' IN (
              SELECT p.value->>'node_id' FROM tb.survey_network_changes h
              CROSS JOIN LATERAL jsonb_array_elements(h.data->'entities') o(value)
              CROSS JOIN LATERAL jsonb_array_elements(coalesce(o.value->'endpoints','[]'::jsonb)) p(value)
              WHERE h.station_codes @> %s::text[] AND o.value->>'id'=%s)))
          SELECT * FROM snapshots ORDER BY change_id DESC,snapshot->>'id' LIMIT 51""",
          ([current['data']['station_code']],str(entity_id),[current['data']['station_code']],str(entity_id)))
        rows=cur.fetchall();history=[]
        roles={p['node_id']:p['role'] for p in current['data'].get('endpoints',[])}
        for row in rows:
            for endpoint in row['snapshot'].get('endpoints',[]):
                roles.setdefault(endpoint['node_id'],endpoint['role'])
        fields=('name','active','station_code','route_number','track_key','position','binding','endpoints','catalog_link','km_observations','note')
        for row in rows[:50]:
            after=row['snapshot'];before=row['previous'] or {}
            history.append(dict(entity_type=after['entity_type'],point_role=roles.get(after.get('id')),version=after['version'],changed_at=row['changed_at'],
                fields=[dict(field=k,before=before.get(k),after=after.get(k)) for k in fields if before.get(k)!=after.get(k)]))
        return dict(history=history,has_more=len(rows)>50)


@router.get('/network/changes')
def changes(station_code:str=Query(min_length=1,max_length=50),after:int=Query(default=0,ge=0),limit:int=Query(default=50,ge=1,le=200),
            user:dict=Depends(current_portal_user)):
    with transaction() as conn,conn.cursor() as cur:
        # Same transaction advisory lock as writers: no uncommitted lower sequence gap can be skipped.
        _lock(cur)
        cur.execute('SELECT change_id,data FROM tb.survey_network_changes WHERE change_id>%s AND station_codes @> %s::text[] ORDER BY change_id LIMIT %s',(after,[station_code.strip().upper()],limit+1))
        rows=cur.fetchall();page=rows[:limit]
        cur.execute('SELECT coalesce(max(change_id),0) latest FROM tb.survey_network_changes')
        latest=cur.fetchone()['latest']
        return dict(schema_version=1,changes=page,next_after=page[-1]['change_id'] if len(rows)>limit else max(after,latest),
                    latest=latest,has_more=len(rows)>limit)
