"""Production KM handlers/models with transactional SQL doubles, not HTTP/PG E2E."""
import ast
from copy import deepcopy
from contextlib import contextmanager
from datetime import date, datetime
import hashlib
import json
import math
from pathlib import Path
import re
from typing import Any
import unittest
from uuid import UUID, uuid4
from pydantic import BaseModel, Field, ValidationError

ROOT = Path(__file__).resolve().parents[1]
TREE = ast.parse((ROOT/'tbserver/survey_catalog.py').read_text())
A,S,O = [uuid4() for _ in range(3)]
SHA = 'a'*64
USER = dict(user_id=uuid4(), company_id=uuid4(), auth_kind='DEVICE')
OTHER = dict(user_id=uuid4(), company_id=uuid4(), auth_kind='DEVICE')
class Error(Exception):
    def __init__(self,status,detail): self.status_code=status;self.detail=detail
class UniqueViolation(Exception): pass

class DB:
    def __init__(self):
        self.axis = dict(id=A,scope='GLOBAL',company_id=None,status='ACTIVE',current_version=3,
                         bbox_min_x=0.,bbox_min_y=0.,bbox_max_x=100.,bbox_max_y=0.,km_from_m=None,km_to_m=None)
        self.segments = {S:dict(id=S,axis_id=A,source_original_id=O,source_kind='ORIGINAL_TRA',
            segment_status='ACTIVE',current_version=1,station_from_m=0.,station_to_m=100.,
            bbox_min_x=0.,bbox_min_y=0.,bbox_max_x=100.,bbox_max_y=0.,km_from_m=None,km_to_m=None,
            metadata={},geometry=None)}
        self.points = [dict(station_m=n,x_dbref=n,y_dbref=0.) for n in [0.,25.,50.,75.,100.]]
        self.sha=SHA;self.receipts={};self.history=[];self.sql=[];self.result=[]
    def __enter__(self): return self
    def __exit__(self,*a): pass
    def cursor(self): return self
    def fetchone(self): return deepcopy(self.result[0]) if self.result else None
    def fetchall(self): return deepcopy(self.result)
    def execute(self,sql,params=()):
        q=' '.join(sql.split());self.sql.append(q);self.result=[]
        if q.startswith('LOCK TABLE'): return
        if q.startswith('SELECT ST_X(p)'):self.result=[dict(x_dbref=4500000.,y_dbref=5300000.)];return
        if q.startswith('SELECT response_payload'):
            r=self.receipts.get((params[0],params[1]));self.result=[dict(response_payload=r)] if r else []
        elif q.startswith('INSERT INTO tb.mutation_receipts'):
            self.receipts[(params[1],params[2])]=deepcopy(params[-1])
        elif q.startswith('SELECT * FROM tb.survey_catalog_axes'):
            if params[0]==A and ("scope='GLOBAL'" not in q or self.axis['scope']=='GLOBAL'):self.result=[self.axis]
        elif q.startswith('SELECT * FROM tb.survey_catalog_segments WHERE axis_id='):
            self.result=[s for s in self.segments.values() if s['axis_id']==params[0]]
        elif q.startswith('SELECT * FROM tb.survey_catalog_segments WHERE id='):
            s=self.segments.get(params[0]);self.result=[s] if s and s['axis_id']==params[1] else []
        elif q.startswith('SELECT sha256'):
            self.result=[dict(sha256=self.sha)] if params[0]==O else []
        elif q.startswith('SELECT id FROM tb.survey_catalog_segments'):
            self.result=[dict(id=params[0])] if params[0] in self.segments else []
        elif q.startswith('INSERT INTO tb.survey_catalog_segments'):
            columns=re.search(r'survey_catalog_segments\((.*?)\) VALUES',q).group(1).split(',')
            row=dict(zip(map(str.strip,columns),params));row['current_version']=1
            if row['id'] in self.segments:raise UniqueViolation()
            self.segments[row['id']]=row;self.result=[row]
        elif q.startswith('INSERT INTO tb.survey_catalog_history'):
            row=dict(change_id=len(self.history)+1,entity_type=params[0],entity_id=params[1],
                     entity_version=params[2],operation=params[3],after_data=params[5],changed_at=None)
            self.history.append(deepcopy(row));self.result=[row]
        elif q.startswith('SELECT min(bbox_min_x)'):
            active=[s for s in self.segments.values() if s['axis_id']==A and s['segment_status']=='ACTIVE']
            def ext(key,fn):
                values=[s.get(key) for s in active if s.get(key) is not None];return fn(values) if values else None
            kms=[s[k] for s in active for k in ['km_from_m','km_to_m'] if s.get(k) is not None]
            self.result=[dict(min_x=ext('bbox_min_x',min),min_y=ext('bbox_min_y',min),max_x=ext('bbox_max_x',max),
                max_y=ext('bbox_max_y',max),km_from=min(kms) if kms else None,km_to=max(kms) if kms else None)]
        elif q.startswith('UPDATE tb.survey_catalog_axes'):
            for k,v in zip(['bbox_min_x','bbox_min_y','bbox_max_x','bbox_max_y','km_from_m','km_to_m','current_version'],params):self.axis[k]=v
            self.result=[self.axis]
        elif q.startswith('UPDATE tb.survey_catalog_segments SET segment_status'):
            status,version,id=params;self.segments[id].update(segment_status=status,current_version=version);self.result=[self.segments[id]]
        elif q.startswith('UPDATE tb.survey_catalog_segments'):
            *values,id,axis,version=params;s=self.segments[id]
            if s['axis_id']==axis and s['current_version']==version:
                for k,v in zip(['station_from_m','station_to_m','km_from_m','km_to_m','bbox_min_x','bbox_min_y','bbox_max_x','bbox_max_y','metadata'],values):s[k]=v
                s.update(geometry=None,current_version=version+1);self.result=[s]
        elif q.startswith('SELECT s.id,s.current_version'):
            self.result=[dict(id=s['id'],current_version=s['current_version'],source_original_id=O,sha256=self.sha)
                         for s in self.segments.values() if s['axis_id']==params[0] and s['source_kind']=='ORIGINAL_TRA' and s['segment_status']=='ACTIVE']
        elif q.startswith('SELECT id,current_version,source_original_id'):
            keys=['id','current_version','source_original_id','segment_status','station_from_m','station_to_m','km_from_m','km_to_m','metadata']
            self.result=[{k:s.get(k) for k in keys} for s in self.segments.values() if s['axis_id']==params[0] and s['source_kind']=='MANUAL_TRA_SECTION']
        elif q.startswith('SELECT change_id,entity_type'):
            self.result=[r for r in self.history if r['change_id']>params[0]][:params[1]]
        elif q.startswith('SELECT a.scope,a.company_id'): self.result=[dict(scope=self.axis['scope'],company_id=self.axis['company_id'])]
        elif q.startswith('SELECT coalesce(max(change_id)'):self.result=[dict(latest=len(self.history))]
        else:raise AssertionError(q)


def environment(db):
    @contextmanager
    def transaction():
        saved=deepcopy((db.axis,db.segments,db.receipts,db.history))
        try:yield db
        except Exception:
            db.axis,db.segments,db.receipts,db.history=saved
            raise
    e=dict(Any=Any,UUID=UUID,uuid4=uuid4,date=date,datetime=datetime,BaseModel=BaseModel,Field=Field,
           HTTPException=Error,UniqueViolation=UniqueViolation,math=math,Jsonb=lambda x:x,transaction=transaction,
           Depends=lambda f:None,Query=lambda **k:k.get('default'),require_csrf=lambda:None,current_portal_user=lambda:None,
           audit=lambda *a,**k:None,_segment_points=lambda cur,s:deepcopy(db.points),_MANUAL_KINDS={'MANUAL_TRA_SECTION','MANUAL_GPS'},
           _PRIVATE_CATALOG_RESPONSE_KEYS={'created_by_user_id','created_by_company_id'},_SCOPE_COMPANY='COMPANY',
           _DBREF_ZONE_SRIDS={2:5682,3:5683,4:5684,5:5685})
    names={'AdminKmSection','AdminKmDeactivate','AdminGvpAnchor','SharedKmAnchor','SharedKmSave','SharedKmDeactivate',
           '_dbref_srid_for_x','_shared_km_gps_point','_shared_km_axis','_shared_km_anchors','_shared_km_prepare','shared_km_state','shared_km_save','shared_km_deactivate',
           '_km_editor_prepare','_km_editor_validate','_km_editor_rows','_km_editor_replay','_km_editor_fingerprint',
           '_management_axis','_row','_receipt','_save_receipt','_clip_station_points','_num','_coverage_interval',
           '_bbox','_segment_insert','_history','_axis_extent_refresh','_mark_segment_status','_gvp_source','_gvp_projection',
           '_plain','_catalog_response','_sanitize_catalog_value','changes'}
    nodes=deepcopy([n for n in TREE.body if isinstance(n,(ast.ClassDef,ast.FunctionDef)) and n.name in names])
    for n in nodes:
        if isinstance(n,ast.FunctionDef):n.decorator_list=[]
    exec(compile(ast.Module(body=nodes,type_ignores=[]),'production_catalog','exec'),e)
    return e


def payload(e, **kw):
    values=dict(base_version=3,source_segment_id=S,source_version=1,source_original_id=O,source_sha256=SHA,
        contribution_id=uuid4(),client_change_id='km:1',station_from_m=0.,station_to_m=100.,km_from_m=0.,km_to_m=100.,
        anchors=[dict(id=uuid4(),kind='MANUAL',station_m=0.,km_meter=0.),dict(id=uuid4(),kind='MANUAL',station_m=100.,km_meter=100.)])
    values.update(kw);return e['SharedKmSave'](**values)

class Tests(unittest.TestCase):
    def setUp(self):self.db=DB();self.e=environment(self.db)
    def save(self,p,user=USER):return self.e['shared_km_save'](A,p,user)
    def error(self,call,code):
        with self.assertRaises(Error) as ctx:call()
        self.assertEqual(ctx.exception.status_code,code)
    def test_create_global_same_axis_and_raw_original_unchanged(self):
        before=deepcopy(self.db.segments[S]);p=payload(self.e);r=self.save(p)
        self.assertEqual(r['segment_id'],str(p.contribution_id));self.assertEqual(r['axis_id'],str(A));self.assertEqual(r['scope'],'GLOBAL')
        self.assertEqual(before,self.db.segments[S]);s=self.db.segments[p.contribution_id]
        self.assertEqual(s['km_from_m'],0.);self.assertEqual(s['metadata']['shared_km_anchors'][0]['km_meter'],0.)
        self.assertEqual(s['source_original_id'],O);self.assertIsNone(s['geometry']);self.assertEqual(self.db.sha,SHA)
    def test_retry_exact_and_mutated_request(self):
        p=payload(self.e);r=self.save(p);count=len(self.db.history)
        self.assertEqual(self.save(p),r);self.assertEqual(len(self.db.history),count)
        self.error(lambda:self.save(p.model_copy(update={'note':'different'})),409)
    def test_other_company_reads_same_shared_values_without_author_metadata(self):
        p=payload(self.e);self.save(p);self.db.segments[p.contribution_id]['metadata']['secret']='not published'
        a=self.e['shared_km_state'](A,USER);b=self.e['shared_km_state'](A,OTHER)
        self.assertEqual(a,b);self.assertEqual(a['sources'][0]['sha256'],SHA)
        self.assertNotIn('created_by',json.dumps(a));self.assertNotIn('secret',json.dumps(a))
    def test_private_axis_rejected_for_read_and_write_even_own_company(self):
        self.db.axis.update(scope='COMPANY',company_id=USER['company_id'])
        self.error(lambda:self.e['shared_km_state'](A,USER),404);self.error(lambda:self.save(payload(self.e)),404)
    def test_changed_axis_source_hash_uuid_and_version(self):
        for patch in [{'base_version':2},{'source_version':2},{'source_original_id':uuid4()},{'source_sha256':'b'*64}]:
            self.error(lambda:self.save(payload(self.e,**patch)),409)
        self.assertEqual(len(self.db.segments),1)
    def test_update_same_id_and_stale_second_device_conflicts(self):
        p=payload(self.e);self.save(p)
        update=p.model_copy(update=dict(base_version=self.db.axis['current_version'],segment_id=p.contribution_id,
                                      segment_version=1,client_change_id='km:2',note='changed'))
        r=self.save(update,OTHER);self.assertEqual(r['entity_version'],2);self.assertEqual(len(self.db.segments),2)
        self.error(lambda:self.save(update.model_copy(update={'client_change_id':'stale'})),409)
    def test_uuid_cannot_be_reused_or_switched(self):
        p=payload(self.e);self.save(p)
        self.error(lambda:self.save(p.model_copy(update={'base_version':self.db.axis['current_version'],'client_change_id':'new'})),409)
        self.error(lambda:self.save(payload(self.e,segment_id=uuid4(),client_change_id='changed-id')),400)
    def test_conflicting_overlap_from_another_device_is_not_overwritten(self):
        p=payload(self.e);self.save(p)
        anchors=[dict(id=uuid4(),kind='MANUAL',station_m=s,km_meter=1000.+s) for s in [0.,100.]]
        self.error(lambda:self.save(payload(self.e,base_version=self.db.axis['current_version'],client_change_id='other',
            anchors=anchors,km_from_m=1000.,km_to_m=1100.)),409)
        self.assertEqual(self.db.segments[p.contribution_id]['km_from_m'],0.)
    def test_deactivate_versioned_idempotent_and_other_company_receives_tombstone(self):
        p=payload(self.e);self.save(p)
        d=self.e['SharedKmDeactivate'](base_version=self.db.axis['current_version'],segment_version=1,client_change_id='delete:1',
            source_segment_id=S,source_version=1,source_original_id=O,source_sha256=SHA)
        r=self.e['shared_km_deactivate'](A,p.contribution_id,d,USER)
        self.assertEqual(r['status'],'INACTIVE');self.assertEqual(self.e['shared_km_deactivate'](A,p.contribution_id,d,USER),r)
        state=self.e['shared_km_state'](A,OTHER);self.assertEqual(state['sections'][0]['segment_status'],'INACTIVE')
        self.assertEqual(self.db.segments[S]['segment_status'],'ACTIVE')
    def test_measurements_reject_null_bool_nan_infinity_and_strings(self):
        for v in [None,True,float('nan'),float('inf'),'0']:
            with self.assertRaises(ValidationError):payload(self.e,km_from_m=v)
        with self.assertRaises(ValidationError):payload(self.e,scope='COMPANY')
    def test_anchors_outside_inconsistent_and_extrapolation_rejected(self):
        for a,b in [(-1.,100.),(1.,99.)]:
            p=payload(self.e,anchors=[dict(id=uuid4(),kind='MANUAL',station_m=s,km_meter=s) for s in [a,b]])
            self.error(lambda:self.save(p),400)
        p=payload(self.e);p.anchors.append(self.e['SharedKmAnchor'](id=uuid4(),kind='MANUAL',station_m=50.,km_meter=70.))
        self.error(lambda:self.save(p),400)
    def test_gps_perpendicular_projection_accuracy_and_wrong_axis_station(self):
        p=payload(self.e)
        for a in p.anchors:a.kind='GPS';a.x_dbref=a.station_m;a.y_dbref=3.;a.accuracy_m=2.
        self.save(p);data=self.db.segments[p.contribution_id]['metadata']['shared_km_anchors']
        self.assertEqual(data[0]['foot']['distance_m'],3.);self.assertEqual(data[0]['accuracy_m'],2.)
        q=payload(self.e,base_version=self.db.axis['current_version'],client_change_id='mast');q.anchors[0].kind='MAST';q.anchors[0].x_dbref=20.;q.anchors[0].y_dbref=3.
        self.error(lambda:self.save(q),400)
    def test_incomplete_gps_and_ambiguous_projection_fail(self):
        p=payload(self.e);p.anchors[0].kind='GPS'
        self.error(lambda:self.save(p),400)
        p.anchors[0].x_dbref=0.;p.anchors[0].y_dbref=0.
        self.error(lambda:self.save(p),400)
        pts=[dict(x_dbref=x,y_dbref=y,station_m=s) for x,y,s in [(0.,0.,0.),(100.,0.,100.),(100.,.1,200.),(0.,.1,300.)]]
        self.error(lambda:self.e['_gvp_projection'](dict(x_dbref=50.,y_dbref=.05),pts),400)
    def test_falling_km_and_anchor_identity(self):
        p=payload(self.e,km_from_m=100.,km_to_m=0.,anchors=[dict(id=uuid4(),kind='MANUAL',station_m=s,km_meter=100.-s) for s in [0.,100.]])
        self.save(p);self.assertEqual(self.db.segments[p.contribution_id]['km_to_m'],0.)
        q=p.model_copy(deep=True,update={'base_version':self.db.axis['current_version'],'client_change_id':'duplicate','contribution_id':uuid4()});q.anchors[1].id=q.anchors[0].id;self.error(lambda:self.save(q),400)
    def test_global_history_feed_reads_after_writer_barrier(self):
        self.save(payload(self.e));self.db.sql=[]
        r=self.e['changes'](0,200,OTHER)
        self.assertEqual(self.db.sql[0],'LOCK TABLE tb.survey_catalog_history IN SHARE MODE')
        self.assertTrue(r['changes']);self.assertFalse(r['has_more'])
        self.assertEqual(self.e['changes'](r['next_after'],200,OTHER)['changes'],[])
    def test_rollback_when_receipt_fails_keeps_no_partial_contribution(self):
        def fail(*a,**k):raise RuntimeError('disk/transaction failure')
        self.e['_save_receipt']=fail;p=payload(self.e)
        with self.assertRaises(RuntimeError):self.save(p)
        self.assertEqual(len(self.db.segments),1);self.assertEqual(self.db.history,[])
    def test_raw_wgs84_uses_bound_source_crs_and_keeps_precision(self):
        anchor=self.e['SharedKmAnchor'](id=uuid4(),kind='GPS',station_m=0.,km_meter=0.,
            gps_lat=47.6,gps_lon=13.,accuracy_m=2.,gps_spread_m=1.)
        pts=[dict(x_dbref=4500000.,y_dbref=5300000.,station_m=0.)]
        point=self.e['_shared_km_gps_point'](self.db,anchor,pts)
        self.assertEqual(point['x_dbref'],4500000.)
        self.assertIn('ST_Transform',self.db.sql[-1])
        anchor.x_dbref=4500010.
        self.error(lambda:self.e['_shared_km_gps_point'](self.db,anchor,pts),400)
        anchor.x_dbref=None;anchor.gps_lon=None
        self.error(lambda:self.e['_shared_km_gps_point'](self.db,anchor,pts),400)
    def test_gvp_references_are_resolved_and_projected_on_explicit_axis(self):
        p=payload(self.e)
        for i,a in enumerate(p.anchors):
            a.kind='GVP'
            a.gvp_ref=self.e['AdminGvpAnchor'](asset_id=uuid4(),asset_version=1,point_index=i,km_meter=a.km_meter)
        self.e['_gvp_resolve']=lambda cur,ref:dict(x_dbref=float(ref.point_index*100),y_dbref=4.,original_id=str(uuid4()))
        self.save(p)
        data=self.db.segments[p.contribution_id]['metadata']['shared_km_anchors']
        self.assertEqual(data[1]['foot']['station_m'],100.)
        self.assertIn('reference',data[0])
    def test_routes_keep_session_and_bearer_security_dependencies(self):
        for name,guard in [('shared_km_state','current_portal_user'),('shared_km_save','require_csrf'),('shared_km_deactivate','require_csrf')]:
            node=next(n for n in TREE.body if isinstance(n,ast.FunctionDef) and n.name==name)
            self.assertIn('Depends('+guard+')',ast.unparse(node.args))

if __name__=='__main__':unittest.main()
