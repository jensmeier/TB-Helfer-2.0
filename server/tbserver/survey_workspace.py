"""Admin-only assignments on immutable survey geometry (REF-DATA-015)."""
from __future__ import annotations
import math
import hashlib
import json
from typing import Literal
from uuid import UUID, uuid4
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb
from .admin import require_admin, require_admin_csrf
from .database import transaction

router = APIRouter()

class Anchor(BaseModel):
    axis_id: UUID
    axis_version: int = Field(ge=1)
    segment_id: UUID
    segment_version: int = Field(ge=1)
    station_m: float = Field(allow_inf_nan=False)

class Assignment(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=160)
    base_version: int = Field(ge=1)
    record_id: UUID | None = None
    record_version: int | None = Field(default=None, ge=1)
    kind: Literal['TRACK', 'SWITCH']
    name: str = Field(min_length=1, max_length=160)
    station_code: str = Field(min_length=1, max_length=50)
    station_name: str = Field(min_length=1, max_length=240)
    anchors: list[Anchor] = Field(min_length=2, max_length=3)
    active: bool = True
    preview_token: str | None = None


def _catalog():
    from . import survey_catalog
    return survey_catalog


def _fingerprint(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()


def _point(points, station):
    if not points or not points[0]['station_m'] <= station <= points[-1]['station_m']:
        raise HTTPException(400, 'WORKSPACE_STATION_OUTSIDE')
    for a,b in zip(points,points[1:]):
        if a['station_m'] <= station <= b['station_m'] and b['station_m'] > a['station_m']:
            t=(station-a['station_m'])/(b['station_m']-a['station_m'])
            return dict(station_m=station, **{k:a[k]+t*(b[k]-a[k]) for k in ('x_dbref','y_dbref')})
    raise HTTPException(400, 'WORKSPACE_GEOMETRY_INVALID')


def _branch_start(point, points):
    hits=[]
    for a,b in zip(points,points[1:]):
        dx,dy=b['x_dbref']-a['x_dbref'],b['y_dbref']-a['y_dbref']
        norm=dx*dx+dy*dy
        if norm <= 1e-12 or b['station_m'] <= a['station_m']: continue
        t=max(0.,min(1.,((point['x_dbref']-a['x_dbref'])*dx+(point['y_dbref']-a['y_dbref'])*dy)/norm))
        station=a['station_m']+t*(b['station_m']-a['station_m'])
        foot=_point([a,b],station)
        hits.append((math.hypot(point['x_dbref']-foot['x_dbref'],point['y_dbref']-foot['y_dbref']),station))
    hits.sort()
    if not hits or hits[0][0] > 2:
        raise HTTPException(400,'WORKSPACE_BRANCH_NOT_CONNECTED')
    if any(d-hits[0][0]<.2 and abs(s-hits[0][1])>2 for d,s in hits[1:]):
        raise HTTPException(400,'WORKSPACE_BRANCH_AMBIGUOUS')
    return hits[0]


def _paths(kind, anchors, geometries):
    expected=2 if kind=='TRACK' else 3
    if len(anchors)!=expected or anchors[0]['segment_id']!=anchors[1]['segment_id']:
        raise HTTPException(400,'WORKSPACE_MAIN_AXIS_REQUIRED')
    start,end=anchors[:2]
    if abs(start['station_m']-end['station_m'])<.1:
        raise HTTPException(400,'WORKSPACE_LENGTH_INVALID')
    c=_catalog()
    bounds=sorted([start['station_m'],end['station_m']])
    paths=[dict(role='TRACK' if kind=='TRACK' else 'MAIN',segment_id=start['segment_id'],
                points=c._clip_station_points(geometries[0],*bounds),length_m=bounds[1]-bounds[0])]
    gap=None
    if kind=='SWITCH':
        if anchors[2]['axis_id']==start['axis_id']:
            raise HTTPException(400,'WORKSPACE_SEPARATE_BRANCH_REQUIRED')
        gap,branch_station=_branch_start(_point(geometries[0],start['station_m']),geometries[2])
        lo,hi=sorted([branch_station,anchors[2]['station_m']])
        if hi-lo<.1: raise HTTPException(400,'WORKSPACE_LENGTH_INVALID')
        paths.append(dict(role='BRANCH',segment_id=anchors[2]['segment_id'],
                          points=c._clip_station_points(geometries[2],lo,hi),length_m=hi-lo,
                          start_station_m=branch_station))
    return paths,gap


def _lock(cur):
    cur.execute("SELECT pg_advisory_xact_lock(751502015)")


def _records(axis):
    return list((axis.get('metadata') or {}).get('workspace_records') or [])


def _prepare(cur, axis_id, payload):
    c=_catalog()
    ids=sorted({axis_id,*(a.axis_id for a in payload.anchors)},key=str)
    cur.execute("SELECT * FROM tb.survey_catalog_axes WHERE id=ANY(%s) AND scope='GLOBAL' ORDER BY id FOR UPDATE",(ids,))
    axes={str(a['id']):dict(a) for a in cur.fetchall()}
    axis=axes.get(str(axis_id))
    if not axis or axis['status']=='ARCHIVED': raise HTTPException(409,'WORKSPACE_AXIS_UNAVAILABLE')
    if axis['current_version']!=payload.base_version: raise HTTPException(409,'VERSION_CONFLICT')
    existing=next((r for r in _records(axis) if r['id']==str(payload.record_id)),None)
    if payload.record_id and (not existing or existing['version']!=payload.record_version):
        raise HTTPException(409,'WORKSPACE_RECORD_CHANGED')
    if existing and existing['kind']!=payload.kind: raise HTTPException(400,'WORKSPACE_KIND_CHANGED')
    if not payload.active:
        if not existing: raise HTTPException(400,'WORKSPACE_RECORD_REQUIRED')
        return axis,{**existing,'active':False},[]
    if payload.anchors[0].axis_id!=axis_id: raise HTTPException(400,'WORKSPACE_OWNER_INVALID')
    for value in [payload.name,payload.station_name,payload.station_code]:
        if not value.strip(): raise HTTPException(400,'WORKSPACE_NAME_REQUIRED')
    geometries=[];anchors=[]
    for ref in payload.anchors:
        a=axes.get(str(ref.axis_id))
        if not a or a['status']=='ARCHIVED' or a['current_version']!=ref.axis_version:
            raise HTTPException(409,'WORKSPACE_SOURCE_CHANGED')
        cur.execute("SELECT * FROM tb.survey_catalog_segments WHERE id=%s AND axis_id=%s FOR SHARE",(ref.segment_id,ref.axis_id))
        row=cur.fetchone()
        if not row or row['source_kind']!='ORIGINAL_TRA' or row['segment_status']!='ACTIVE' or row['current_version']!=ref.segment_version:
            raise HTTPException(409,'WORKSPACE_SOURCE_CHANGED')
        points=c._segment_points(cur,dict(row));p=_point(points,ref.station_m)
        anchors.append({**ref.model_dump(mode='json'),**p,'source_original_id':str(row['source_original_id'])})
        geometries.append(points)
    paths,gap=_paths(payload.kind,anchors,geometries)
    record=dict(id=str(payload.record_id or uuid4()),version=(existing['version'] if existing else 0)+1,
                kind=payload.kind,name=payload.name.strip(),station_code=payload.station_code.strip().upper(),
                station_name=payload.station_name.strip(),active=True,anchors=anchors,
                branch_gap_m=gap,paths=[{k:v for k,v in p.items() if k!='points'} for p in paths],
                boundary_type='USER_ASSIGNMENT',provenance='ADMIN_EXISTING_AXIS')
    # A section cannot silently replace an already assigned overlapping interval.
    if payload.kind=='TRACK':
        lo,hi=sorted(a['station_m'] for a in anchors)
        for r in _records(axis):
            if r.get('active') and r['kind']=='TRACK' and r['id']!=record['id'] and r['anchors'][0]['segment_id']==anchors[0]['segment_id']:
                a,b=sorted(p['station_m'] for p in r['anchors'])
                if min(b,hi)>max(a,lo)+1e-6: raise HTTPException(409,'WORKSPACE_SECTION_OVERLAP')
    return axis,record,paths


def _token(payload):
    d=payload.model_dump(mode='json');d.pop('preview_token',None)
    return _fingerprint(d)


def _response(cur,axis,record,paths):
    c=_catalog();result=[]
    for p in paths:
        pts=c._decimate(p['points'],1500)
        coords=c._postgis_transform_points(cur,[(v['x_dbref'],v['y_dbref']) for v in pts])
        if any(v is None for v in coords): raise HTTPException(400,'WORKSPACE_CRS_UNSUPPORTED')
        result.append({**{k:v for k,v in p.items() if k!='points'},'coordinates':coords})
    return dict(axis_id=str(axis['id']),axis_version=axis['current_version'],record=record,paths=result)


@router.get('/management/workspace/axes/{axis_id}')
def workspace_axis(axis_id: UUID, include_geometry:bool=Query(default=True), _:dict=Depends(require_admin)):
    c=_catalog()
    with transaction() as conn,conn.cursor() as cur:
        cur.execute("SELECT * FROM tb.survey_catalog_axes WHERE id=%s AND scope='GLOBAL' AND status<>'ARCHIVED'",(axis_id,))
        row=cur.fetchone()
        if not row: raise HTTPException(404,'WORKSPACE_AXIS_UNAVAILABLE')
        axis=dict(row)
        cur.execute("""SELECT s.*,o.original_name FROM tb.survey_catalog_segments s
                       JOIN tb.survey_originals o ON o.id=s.source_original_id
                       WHERE s.axis_id=%s AND s.source_kind='ORIGINAL_TRA' AND s.segment_status='ACTIVE' ORDER BY s.id""",(axis_id,))
        rows=[dict(r) for r in cur.fetchall()];sources=[]
        for row in rows:
            points=c._segment_points(cur,row)
            if len(points)<2: continue
            pts=c._decimate(points,5000) if include_geometry else []
            coords=c._postgis_transform_points(cur,[(p['x_dbref'],p['y_dbref']) for p in pts])
            # Never bridge an unsupported coordinate interval.
            if any(p is None for p in coords): continue
            sources.append(dict(segment_id=str(row['id']),segment_version=row['current_version'],
                original_name=row['original_name'],station_from_m=points[0]['station_m'],station_to_m=points[-1]['station_m'],
                coordinates=coords,stations=[p['station_m'] for p in pts]))
        return c._plain(dict(axis=axis,sources=sources,records=_records(axis)))


@router.post('/management/workspace/axes/{axis_id}/preview')
def workspace_preview(axis_id: UUID,payload:Assignment,user:dict=Depends(require_admin_csrf)):
    with transaction() as conn,conn.cursor() as cur:
        _lock(cur);axis,record,paths=_prepare(cur,axis_id,payload)
        return {**_response(cur,axis,record,paths),'preview_token':_token(payload)}


def _update_axis(cur,axis,metadata,user,status=None):
    c=_catalog()
    cur.execute("""UPDATE tb.survey_catalog_axes SET metadata=%s,status=%s,current_version=current_version+1,
                   updated_at=now() WHERE id=%s AND current_version=%s RETURNING *""",
                (Jsonb(metadata),status or axis['status'],axis['id'],axis['current_version']))
    row=cur.fetchone()
    if not row: raise HTTPException(409,'VERSION_CONFLICT')
    result=dict(row)
    c._history(cur,entity_type='AXIS',entity_id=axis['id'],version=result['current_version'],operation='UPDATE',
               before=axis,after=result,actor_user_id=user['user_id'],actor_company_id=None)
    c.audit(cur,action='SURVEY_WORKSPACE_UPDATE',actor_user_id=user['user_id'],company_id=None,
            entity_type='SURVEY_CATALOG_AXIS',entity_id=axis['id'],details={'version':result['current_version']})
    return result


@router.post('/management/workspace/axes/{axis_id}')
def workspace_save(axis_id: UUID,payload:Assignment,user:dict=Depends(require_admin_csrf)):
    c=_catalog();fingerprint=_fingerprint({'axis':str(axis_id),'payload':payload.model_dump(mode='json')})
    with transaction() as conn,conn.cursor() as cur:
        _lock(cur)
        receipt=c._receipt(cur,user,payload.client_change_id)
        if receipt:
            if receipt.get('workspace_fingerprint')!=fingerprint: raise HTTPException(409,'WORKSPACE_REQUEST_CHANGED')
            return receipt
        if payload.preview_token!=_token(payload): raise HTTPException(409,'WORKSPACE_PREVIEW_REQUIRED')
        axis,record,paths=_prepare(cur,axis_id,payload)
        # Deactivating a record is a new version as well.
        if not payload.active: record={**record,'version':record['version']+1}
        response=_response(cur,axis,record,paths)
        records=[r for r in _records(axis) if r['id']!=record['id']]+[record]
        updated=_update_axis(cur,axis,{**(axis.get('metadata') or {}),'workspace_records':records},user)
        response.update(axis_version=updated['current_version'],workspace_fingerprint=fingerprint)
        c._save_receipt(cur,user,payload.client_change_id,axis_id,updated['current_version'],c._plain(response))
        return c._plain(response)

class ResetRequest(BaseModel):
    original_ids: list[UUID] = Field(min_length=1,max_length=100)
    mode: Literal['RESET','RESTORE'] = 'RESET'
    client_change_id: str = Field(min_length=1,max_length=160)
    preview_token: str | None = None


@router.get('/management/workspace/sources')
def reset_sources(q:str=Query(default='',max_length=160),offset:int=Query(default=0,ge=0),_:dict=Depends(require_admin)):
    c=_catalog()
    with transaction() as conn,conn.cursor() as cur:
        cur.execute("""SELECT a.id,a.original_id,a.station_code,a.station_name,a.data_kind,a.data_status,
                         a.current_version,o.original_name,o.sha256,o.metadata->>'import_bundle_name' import_bundle_name,
                         o.metadata->>'import_batch_id' import_batch_id,a.metadata->'workspace_reset' reset_state
                       FROM tb.survey_catalog_assets a JOIN tb.survey_originals o ON o.id=a.original_id
                       WHERE a.scope='GLOBAL' AND a.company_id IS NULL
                         AND concat_ws(' ',o.original_name,o.metadata->>'import_bundle_name',a.station_code,a.station_name,a.route_number) ILIKE %s
                       ORDER BY o.original_name,a.id LIMIT 101 OFFSET %s""",('%'+q.strip()+'%',offset))
        rows=[dict(r) for r in cur.fetchall()]
        return dict(sources=c._plain(rows[:100]),has_more=len(rows)>100)


def _reset_prepare(cur,payload):
    c=_catalog();ids=sorted(set(payload.original_ids),key=str)
    # Existing editing routes lock axes before segments/assets too.
    cur.execute("""SELECT DISTINCT axis_id FROM tb.survey_catalog_segments WHERE source_original_id=ANY(%s)""",(ids,))
    aids=[r['axis_id'] for r in cur.fetchall()]
    cur.execute("SELECT * FROM tb.survey_catalog_axes WHERE id=ANY(%s) ORDER BY id FOR UPDATE",(aids,))
    axes=[dict(r) for r in cur.fetchall()]
    cur.execute("SELECT * FROM tb.survey_catalog_segments WHERE source_original_id=ANY(%s) ORDER BY id FOR UPDATE",(ids,))
    segments=[dict(r) for r in cur.fetchall()]
    cur.execute("SELECT * FROM tb.survey_catalog_assets WHERE original_id=ANY(%s) ORDER BY id FOR UPDATE",(ids,))
    assets=[dict(r) for r in cur.fetchall()]
    if len(assets)!=len(ids) or any(a['scope']!='GLOBAL' or a.get('company_id') for a in assets+axes):
        raise HTTPException(409,'WORKSPACE_SHARED_SOURCE')
    # Even an inactive assignment retains its provenance; active ones block reset.
    cur.execute("SELECT id,metadata FROM tb.survey_catalog_axes WHERE metadata ? 'workspace_records'")
    selected={str(s['id']) for s in segments}; blockers=[]
    for a in cur.fetchall():
        for r in _records(a):
            if payload.mode=='RESET' and r.get('active') and any(p['segment_id'] in selected for p in r['anchors']):
                blockers.append(r['name'])
    if payload.mode=='RESET' and any((a.get('metadata') or {}).get('workspace_reset') for a in assets):
        raise HTTPException(409,'WORKSPACE_ALREADY_RESET')
    if payload.mode=='RESTORE' and any(not (a.get('metadata') or {}).get('workspace_reset') for a in assets):
        raise HTTPException(409,'WORKSPACE_NOT_RESET')
    snapshot=c._plain(dict(mode=payload.mode,assets=assets,segments=segments,axes=axes,blockers=blockers))
    token=_fingerprint(snapshot)
    return assets,segments,axes,blockers,token


def _reset_response(assets,segments,axes,blockers,token):
    return dict(preview_token=token,source_count=len(assets),segment_count=len(segments),
                axis_count=len(axes),axes=[a['display_name'] for a in axes],blockers=blockers,
                originals_preserved=True)


@router.post('/management/workspace/reset/preview')
def reset_preview(payload:ResetRequest,user:dict=Depends(require_admin_csrf)):
    with transaction() as conn,conn.cursor() as cur:
        _lock(cur);return _reset_response(*_reset_prepare(cur,payload))


@router.post('/management/workspace/reset')
def reset_apply(payload:ResetRequest,user:dict=Depends(require_admin_csrf)):
    c=_catalog();fp=_fingerprint(payload.model_dump(mode='json'))
    with transaction() as conn,conn.cursor() as cur:
        _lock(cur)
        receipt=c._receipt(cur,user,payload.client_change_id)
        if receipt:
            if receipt.get('reset_fingerprint')!=fp: raise HTTPException(409,'WORKSPACE_REQUEST_CHANGED')
            return receipt
        assets,segments,axes,blockers,token=_reset_prepare(cur,payload)
        if token!=payload.preview_token: raise HTTPException(409,'WORKSPACE_PREVIEW_CHANGED')
        if blockers: raise HTTPException(409,'WORKSPACE_ACTIVE_ASSIGNMENTS')
        reset=payload.mode=='RESET'
        for table,rows,field,entity in [('survey_catalog_segments',segments,'segment_status','SEGMENT'),
                                       ('survey_catalog_assets',assets,'data_status','ASSET')]:
            for row in rows:
                metadata=dict(row.get('metadata') or {})
                if reset:
                    metadata['workspace_reset']={'previous_status':row[field]}
                    status='INACTIVE'
                else:
                    previous=metadata.pop('workspace_reset',None)
                    if previous is None: continue
                    status=previous['previous_status']
                cur.execute(f"UPDATE tb.{table} SET {field}=%s,metadata=%s,current_version=current_version+1,updated_at=now() WHERE id=%s RETURNING *",(status,Jsonb(metadata),row['id']))
                after=dict(cur.fetchone())
                c._history(cur,entity_type=entity,entity_id=row['id'],version=after['current_version'],operation='STATUS',
                           before=row,after=after,actor_user_id=user['user_id'],actor_company_id=None)
        for axis in axes:
            cur.execute("SELECT count(*) n FROM tb.survey_catalog_segments WHERE axis_id=%s AND segment_status IN ('ACTIVE','REVIEW')",(axis['id'],))
            remaining=cur.fetchone()['n'];metadata=dict(axis.get('metadata') or {});status=axis['status']
            if reset and not remaining and status!='ARCHIVED':
                metadata['workspace_reset_status']=status;status='ARCHIVED'
            elif not reset and remaining and metadata.get('workspace_reset_status'):
                status=metadata.pop('workspace_reset_status')
            _update_axis(cur,axis,metadata,user,status)
            c._axis_extent_refresh(cur,axis['id'],user['user_id'])
        result={**_reset_response(assets,segments,axes,[],token),'mode':payload.mode,'reset_fingerprint':fp}
        c._save_receipt(cur,user,payload.client_change_id,assets[0]['id'],assets[0]['current_version']+1,result)
        return result


def assignment_geometry(cur,axis):
    """Derive active assignment lines for the selected detail axis, never stored copies."""
    c=_catalog();result=[]
    for record in _records(axis):
        if not record.get('active'): continue
        points=[];valid=True
        for anchor in record['anchors']:
            cur.execute("""SELECT s.* FROM tb.survey_catalog_segments s JOIN tb.survey_catalog_axes a ON a.id=s.axis_id
                           WHERE s.id=%s AND s.axis_id=%s AND s.source_kind='ORIGINAL_TRA' AND s.segment_status='ACTIVE'
                           AND a.scope='GLOBAL' AND a.status<>'ARCHIVED'""",(UUID(anchor['segment_id']),UUID(anchor['axis_id'])))
            row=cur.fetchone()
            if not row or row['current_version']!=anchor['segment_version']:
                valid=False;break
            points.append(c._segment_points(cur,dict(row)))
        if not valid:
            result.append(dict(record=record,paths=[],needs_review=True));continue
        try:
            paths,_=_paths(record['kind'],record['anchors'],points)
            result.append(_response(cur,axis,record,paths))
        except HTTPException:
            result.append(dict(record=record,paths=[],needs_review=True))
    return c._plain(result)
