"""Execute production workspace handlers with Pydantic and transactional storage doubles.
No claims of live Postgres/PostGIS or browser/NAS E2E.
"""
import ast
import hashlib
import json
import math
from datetime import date, datetime
from copy import deepcopy
from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
from typing import Literal
from uuid import UUID, uuid4
import unittest
from pydantic import BaseModel, Field, ValidationError

ROOT=Path(__file__).resolve().parents[1]
TREE=ast.parse((ROOT/'tbserver/survey_workspace.py').read_text())
CAT=ast.parse((ROOT/'tbserver/survey_catalog.py').read_text())
A,B,S,T,O,P=tuple(uuid4() for _ in range(6));USER={'user_id':uuid4()}
class Error(Exception):
 def __init__(self,status,detail):self.status_code=status;self.detail=detail
class DB:
 def __init__(self):
  self.axes={A:dict(id=A,scope='GLOBAL',status='ACTIVE',current_version=3,metadata={},display_name='Main'),B:dict(id=B,scope='GLOBAL',status='ACTIVE',current_version=2,metadata={},display_name='Branch')}
  self.segments={S:dict(id=S,axis_id=A,source_original_id=O,source_kind='ORIGINAL_TRA',segment_status='ACTIVE',current_version=1,metadata={}),T:dict(id=T,axis_id=B,source_original_id=P,source_kind='ORIGINAL_TRA',segment_status='ACTIVE',current_version=1,metadata={})}
  self.assets={O:dict(id=O,original_id=O,scope='GLOBAL',data_status='ACTIVE',metadata={},current_version=1),P:dict(id=P,original_id=P,scope='GLOBAL',data_status='ACTIVE',metadata={},current_version=1)}
  self.points={S:[dict(station_m=s,x_dbref=s,y_dbref=0.) for s in [0.,50.,100.]],T:[dict(station_m=0.,x_dbref=0.,y_dbref=0.),dict(station_m=60.,x_dbref=50.,y_dbref=30.)]}
  self.receipts={};self.history=[];self.audit=[];self.writes=[];self.result=None
 def __enter__(self):return self
 def __exit__(self,*a):pass
 def cursor(self):return self
 def execute(self,sql,params=()):
  q=' '.join(sql.split());self.result=[]
  if q.startswith('SELECT pg_advisory'):pass
  elif q.startswith('SELECT DISTINCT o.data FROM tb.survey_network_entities'):self.result=[]
  elif q.startswith('SELECT DISTINCT axis_id'):self.result=[dict(axis_id=s['axis_id']) for s in self.segments.values() if s['source_original_id'] in params[0]]
  elif q.startswith('SELECT * FROM tb.survey_catalog_axes WHERE id=ANY'):self.result=[a for a in self.axes.values() if a['id'] in params[0] and ("scope='GLOBAL'" not in q or a['scope']=='GLOBAL')]
  elif q.startswith('SELECT * FROM tb.survey_catalog_segments WHERE id='):self.result=next((s for s in self.segments.values() if s['id']==params[0] and s['axis_id']==params[1]),None)
  elif q.startswith('SELECT * FROM tb.survey_catalog_segments WHERE source_original_id'):self.result=[s for s in self.segments.values() if s['source_original_id'] in params[0]]
  elif q.startswith('SELECT * FROM tb.survey_catalog_assets WHERE original_id'):self.result=[a for a in self.assets.values() if a['original_id'] in params[0]]
  elif q.startswith('SELECT id,metadata FROM tb.survey_catalog_axes'):self.result=list(self.axes.values())
  elif q.startswith('SELECT count(*) n'):self.result=dict(n=sum(s['axis_id']==params[0] and s['segment_status']=='ACTIVE' for s in self.segments.values()))
  elif q.startswith('UPDATE tb.survey_catalog_axes'):
   metadata,status,id,version=params;row=self.axes[id];assert row['current_version']==version
   row.update(metadata=metadata,status=status,current_version=version+1);self.result=row;self.writes.append(q)
  elif q.startswith('UPDATE tb.survey_catalog_segments') or q.startswith('UPDATE tb.survey_catalog_assets'):
   status,metadata,id=params;segments='segments' in q;row=(self.segments if segments else self.assets)[id]
   row.update({('segment_status' if segments else 'data_status'):status,'metadata':metadata,'current_version':row['current_version']+1});self.result=row;self.writes.append(q)
  else:raise AssertionError(q)
 def fetchone(self):return deepcopy(self.result)
 def fetchall(self):return deepcopy(self.result)

def environment(db):
 @contextmanager
 def transaction():yield db
 e=dict(UUID=UUID,uuid4=uuid4,BaseModel=BaseModel,Field=Field,Literal=Literal,HTTPException=Error,math=math,json=json,hashlib=hashlib,
        Depends=lambda f:None,Query=lambda **kw:kw.get('default'),require_admin=lambda:None,require_admin_csrf=lambda:None,transaction=transaction,Jsonb=lambda x:x)
 nodes=[deepcopy(n) for n in TREE.body if isinstance(n,(ast.ClassDef,ast.FunctionDef)) and n.name!='_catalog']
 for n in nodes:
  if isinstance(n,ast.FunctionDef):n.decorator_list=[]
 exec(compile(ast.Module(body=nodes,type_ignores=[]),str(ROOT/'tbserver/survey_workspace.py'),'exec'),e)
 ce={'Any':object,'math':math,'UUID':UUID,'date':date,'datetime':datetime}
 cn=[deepcopy(n) for n in CAT.body if isinstance(n,ast.FunctionDef) and n.name in ['_num','_clip_station_points','_plain','_decimate']]
 exec(compile(ast.Module(body=cn,type_ignores=[]),'catalog','exec'),ce)
 c=SimpleNamespace(**{k:v for k,v in ce.items() if k.startswith('_') and callable(v)})
 c._segment_points=lambda cur,s:deepcopy(db.points[s['id']])
 c._postgis_transform_points=lambda cur,points:points
 c._history=lambda cur,**kw:db.history.append(kw)
 c.audit=lambda cur,**kw:db.audit.append(kw)
 c._axis_extent_refresh=lambda *a:None
 c._receipt=lambda cur,user,key:db.receipts.get(key)
 c._save_receipt=lambda cur,user,key,id,version,r,**kw:db.receipts.update({key:r})
 e['_catalog']=lambda:c
 return e

def payload(e,**kw):
 values=dict(client_change_id='assignment:1',base_version=3,kind='TRACK',name='303',station_code='MGHN',station_name='Gessertshausen',anchors=[dict(axis_id=A,axis_version=3,segment_id=S,segment_version=1,station_m=s) for s in [10,80]])
 values.update(kw);return e['Assignment'](**values)

class WorkspaceTests(unittest.TestCase):
 def setUp(self):self.db=DB();self.e=environment(self.db)
 def test_point_interpolation_and_outside(self):
  self.assertEqual(self.e['_point'](self.db.points[S],25)['x_dbref'],25)
  for n in [-1,101]:
   with self.assertRaises(Error):self.e['_point'](self.db.points[S],n)
 def test_nonfinite_rejected(self):
  for n in [float('nan'),float('inf')]:
   with self.assertRaises(ValidationError):self.e['Anchor'](axis_id=A,axis_version=3,segment_id=S,segment_version=1,station_m=n)
 def test_preview_is_readonly_and_uses_actual_interval(self):
  r=self.e['workspace_preview'](A,payload(self.e),USER)
  self.assertEqual(r['paths'][0]['length_m'],70);self.assertEqual(r['paths'][0]['coordinates'][0],(10.,0.));self.assertFalse(self.db.writes)
 def test_save_replay_and_record_version(self):
  p=payload(self.e);p.preview_token=self.e['workspace_preview'](A,p,USER)['preview_token']
  r=self.e['workspace_save'](A,p,USER);self.assertEqual(self.db.axes[A]['current_version'],4);self.assertEqual(len(self.db.history),1)
  self.assertEqual(self.e['workspace_save'](A,p,USER),r);self.assertEqual(len(self.db.writes),1)
  p.name='bad'
  with self.assertRaises(Error):self.e['workspace_save'](A,p,USER)
 def test_versions_and_scope_rejected_without_writes(self):
  for field,value in [('axis_version',2),('segment_version',9)]:
   p=payload(self.e);setattr(p.anchors[0],field,value)
   with self.assertRaises(Error):self.e['workspace_preview'](A,p,USER)
  self.db.axes[A]['scope']='COMPANY'
  with self.assertRaises(Error):self.e['workspace_preview'](A,payload(self.e),USER)
  self.assertFalse(self.db.writes)
 def test_modified_input_requires_new_preview(self):
  p=payload(self.e);p.preview_token=self.e['workspace_preview'](A,p,USER)['preview_token'];p.name='4'
  with self.assertRaises(Error):self.e['workspace_save'](A,p,USER)
  self.assertFalse(self.db.writes)
 def test_stale_preview_source_rejected(self):
  p=payload(self.e);p.preview_token=self.e['workspace_preview'](A,p,USER)['preview_token'];self.db.segments[S]['current_version']=2
  with self.assertRaises(Error):self.e['workspace_save'](A,p,USER)
  self.assertFalse(self.db.writes)
 def test_switch_has_independent_lengths_and_source(self):
  p=payload(self.e,kind='SWITCH');p.anchors[0].station_m=0
  p.anchors.append(self.e['Anchor'](axis_id=B,axis_version=2,segment_id=T,segment_version=1,station_m=60))
  r=self.e['workspace_preview'](A,p,USER);self.assertEqual([p['length_m'] for p in r['paths']],[80,60]);self.assertEqual(r['record']['branch_gap_m'],0)
 def test_disconnected_branch_rejected(self):
  p=payload(self.e,kind='SWITCH');p.anchors.append(self.e['Anchor'](axis_id=B,axis_version=2,segment_id=T,segment_version=1,station_m=60))
  with self.assertRaises(Error):self.e['workspace_preview'](A,p,USER)
 def test_overlap_blocked(self):
  p=payload(self.e);axis,record,_=self.e['_prepare'](self.db,A,p);self.db.axes[A]['metadata']['workspace_records']=[record]
  with self.assertRaises(Error):self.e['workspace_preview'](A,p,USER)
 def test_deactivate_keeps_record_and_history(self):
  p=payload(self.e);p.preview_token=self.e['workspace_preview'](A,p,USER)['preview_token'];r=self.e['workspace_save'](A,p,USER)['record']
  p=payload(self.e,base_version=4,record_id=r['id'],record_version=1,active=False,client_change_id='off')
  p.preview_token=self.e['workspace_preview'](A,p,USER)['preview_token'];self.e['workspace_save'](A,p,USER)
  records=self.db.axes[A]['metadata']['workspace_records'];self.assertEqual(len(records),1);self.assertFalse(records[0]['active']);self.assertEqual(records[0]['version'],2)
 def test_reset_preview_changes_nothing(self):
  p=self.e['ResetRequest'](original_ids=[O],client_change_id='reset');r=self.e['reset_preview'](p,USER)
  self.assertEqual(r['source_count'],1);self.assertEqual(r['segment_count'],1);self.assertFalse(self.db.writes)
 def test_reset_restore_replay_and_unrelated_source_preserved(self):
  untouched=deepcopy(self.db.segments[T]);p=self.e['ResetRequest'](original_ids=[O],client_change_id='reset')
  p.preview_token=self.e['reset_preview'](p,USER)['preview_token'];r=self.e['reset_apply'](p,USER)
  self.assertEqual(self.db.axes[A]['status'],'ARCHIVED');self.assertEqual(self.db.assets[O]['data_status'],'INACTIVE');self.assertEqual(self.db.segments[T],untouched)
  self.assertEqual(self.e['reset_apply'](p,USER),r)
  p=self.e['ResetRequest'](original_ids=[O],mode='RESTORE',client_change_id='restore');p.preview_token=self.e['reset_preview'](p,USER)['preview_token'];self.e['reset_apply'](p,USER)
  self.assertEqual(self.db.axes[A]['status'],'ACTIVE');self.assertEqual(self.db.assets[O]['data_status'],'ACTIVE');self.assertEqual(self.db.segments[S]['segment_status'],'ACTIVE')
 def test_reset_fingerprint_and_shared_source_guards(self):
  p=self.e['ResetRequest'](original_ids=[O],client_change_id='reset');p.preview_token=self.e['reset_preview'](p,USER)['preview_token'];self.db.segments[S]['current_version']+=1
  with self.assertRaises(Error):self.e['reset_apply'](p,USER)
  self.assertFalse(self.db.writes);self.db.axes[A]['scope']='COMPANY'
  with self.assertRaises(Error):self.e['reset_preview'](p,USER)
 def test_reset_blocks_active_assignments(self):
  _,r,_=self.e['_prepare'](self.db,A,payload(self.e));self.db.axes[A]['metadata']['workspace_records']=[r]
  p=self.e['ResetRequest'](original_ids=[O],client_change_id='reset');result=self.e['reset_preview'](p,USER);self.assertEqual(result['blockers'],['303']);p.preview_token=result['preview_token']
  with self.assertRaises(Error):self.e['reset_apply'](p,USER)
  self.assertFalse(self.db.writes)
 def test_routes_require_admin_and_csrf(self):
  for n in TREE.body:
   if isinstance(n,ast.FunctionDef) and n.decorator_list:
    source=ast.unparse(n)
    self.assertIn('Depends(require_admin_csrf)' if any('post' in ast.unparse(d) for d in n.decorator_list) else 'Depends(require_admin)',source)

if __name__=='__main__':unittest.main()
