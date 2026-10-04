"""Production network functions with transaction/storage doubles; no live DB/HTTP claim."""
import ast, hashlib, json, math, unittest
from copy import deepcopy
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import Literal
from types import SimpleNamespace
from uuid import UUID,uuid4
from pydantic import BaseModel,ConfigDict,Field,model_validator,ValidationError
ROOT=Path(__file__).resolve().parents[1]
class Error(Exception):
 def __init__(self,status,detail):self.status_code=status;self.detail=detail
class DB:
 def __init__(self):self.entities={};self.receipts={};self.changes=[];self.rows=[];self.sources={};self.source_writes=0;self.catalog={}
 def cursor(self):return self
 def __enter__(self):return self
 def __exit__(self,*a):pass
 def execute(self,sql,p=()):
  q=' '.join(sql.split());self.rows=[]
  if q.startswith('SELECT pg_advisory'):return
  if q.startswith('SELECT data FROM tb.survey_network_entities WHERE id=ANY'):self.rows=[dict(data=deepcopy(v)) for k,v in self.entities.items() if UUID(k) in p[0]]
  elif q.startswith('SELECT data FROM tb.survey_network_entities WHERE entity_type='):
   ids=set(p[0]) if isinstance(p[0],list) else {p[0]}
   self.rows=[dict(data=deepcopy(v)) for v in self.entities.values() if v['entity_type']=='OBJECT' and v['active'] and any(e['node_id'] in ids for e in v['endpoints'])]
  elif q.startswith('SELECT data FROM tb.survey_network_entities WHERE (%s::text'):
   vals=sorted((v for v in self.entities.values() if (p[0] is None or v['station_code']==p[1]) and (p[2] is None or v['id']>str(p[2]))),key=lambda x:x['id']);self.rows=[dict(data=deepcopy(v)) for v in vals[:p[4]]]
  elif q.startswith('SELECT data FROM tb.survey_network_entities WHERE id='):self.rows=[dict(data=deepcopy(self.entities[str(p[0])]))] if str(p[0]) in self.entities else []
  elif q.startswith('SELECT fingerprint,response'):self.rows=[self.receipts[(p[0],p[1])]] if (p[0],p[1]) in self.receipts else []
  elif q.startswith('INSERT INTO tb.survey_network_entities'):self.entities[str(p[0])]=deepcopy(p[-1])
  elif q.startswith('INSERT INTO tb.survey_network_changes'):
   row=dict(change_id=len(self.changes)+1,data=deepcopy(p[1]),station_codes=p[0]);self.changes.append(row);self.rows=[row]
  elif q.startswith('INSERT INTO tb.survey_network_receipts'):self.receipts[(p[0],p[1])]=dict(fingerprint=p[2],response=deepcopy(p[3]))
  elif q.startswith('SELECT change_id,data'):self.rows=[deepcopy(r) for r in self.changes if r['change_id']>p[0] and p[1][0] in r['station_codes']][:p[2]]
  elif q.startswith('SELECT coalesce(max(change_id)'):self.rows=[dict(latest=len(self.changes))]
  elif q.startswith('SELECT s.current_version'):
   row=self.sources.get(str(p[0]));self.rows=[dict(current_version=row['current_version'])] if row and row.get('active',True) and row.get('scope','GLOBAL')=='GLOBAL' else []
  elif q.startswith('SELECT s.*,a.current_version'):
   row=self.sources.get(str(p[0]));self.rows=[deepcopy(row)] if row and str(row['axis_id'])==str(p[1]) and row.get('active',True) and row.get('scope','GLOBAL')=='GLOBAL' else []
  elif q.startswith('SELECT o.id,o.current_version'):
   ref=self.catalog.get(str(p[0]));self.rows=[deepcopy(ref)] if ref else []
  elif q.startswith("SELECT id FROM tb.survey_network_entities WHERE data->'catalog_link'"):
   self.rows=[dict(id=v['id']) for v in self.entities.values() if (v.get('catalog_link') or {}).get('id')==str(p[0]) and v['id']!=str(p[1])] if any(v.get('catalog_link') for v in self.entities.values()) else []
  elif q.startswith('SELECT id FROM tb.survey_catalog_axes'):self.rows=[dict(id=p[0])]
  else:raise AssertionError(q)
 def fetchall(self):return deepcopy(self.rows)
 def fetchone(self):return deepcopy(self.rows[0]) if self.rows else None

def env(db):
 @contextmanager
 def tx():
  before=deepcopy(db.__dict__)
  try:yield db
  except Exception:db.__dict__.update(before);raise
 e=dict(BaseModel=BaseModel,ConfigDict=ConfigDict,Field=Field,model_validator=model_validator,Literal=Literal,UUID=UUID,
        datetime=datetime,hashlib=hashlib,json=json,math=math,HTTPException=Error,Jsonb=lambda x:x,transaction=tx,
        Depends=lambda x:None,Query=lambda default=None,**kw:default,current_portal_user=lambda:None,require_csrf=lambda:None)
 for file,names in [('survey_workspace.py',{'Anchor','_point','_branch_start','_lock'}),('survey_network.py',None)]:
  nodes=[deepcopy(n) for n in ast.parse((ROOT/'tbserver'/file).read_text()).body if isinstance(n,(ast.ClassDef,ast.FunctionDef)) and (names is None or n.name in names) and n.name!='_catalog']
  for n in nodes:
   if isinstance(n,ast.FunctionDef):n.decorator_list=[]
  exec(compile(ast.Module(body=nodes,type_ignores=[]),file,'exec'),e)
 def clip(points,lo,hi):return [e['_point'](points,lo),e['_point'](points,hi)] if hi>lo else []
 c=SimpleNamespace(_segment_points=lambda cur,row:row['points'],_postgis_transform_points=lambda cur,xy:xy,_decimate=lambda x,n:x,_clip_station_points=clip)
 e['_catalog']=lambda:c
 return e

class NetworkTests(unittest.TestCase):
 def setUp(self):self.db=DB();self.e=env(self.db);self.user={'user_id':uuid4(),'auth_kind':'DEVICE'}
 def node(self,x=10,**kw):
  v=dict(id=uuid4(),base_version=0,station_code='MGHN',position=dict(lon=x,lat=48,method='GPS',accuracy_m=1.2,altitude_m=550.5));v.update(kw);return v
 def obj(self,nodes,kind='TRACK',**kw):
  roles=['WA','WE','WEZ'] if kind=='SWITCH' else ['START','END']
  v=dict(id=uuid4(),base_version=0,station_code='MGHN',kind=kind,name='4',endpoints=[dict(role=r,node_id=n['id'],node_version=n.get('base_version',0)+1) for r,n in zip(roles,nodes)]);v.update(kw);return v
 def payload(self,nodes,objects,**kw):return self.e['Mutation'](client_change_id=str(uuid4()),nodes=nodes,objects=objects,**kw)
 def save(self,p):p.preview_token=self.e['preview'](p,self.user)['preview_token'];return self.e['mutate'](p,self.user)
 def test_preview_readonly_and_roundtrip(self):
  n=[self.node(),self.node(10.001)];p=self.payload(n,[self.obj(n)]);r=self.e['preview'](p,self.user)
  self.assertFalse(self.db.entities);self.assertTrue(r['warnings']);saved=self.save(p)
  self.assertEqual(saved['entities'][0]['position']['altitude_m'],550.5)
  self.assertEqual(self.e['mutate'](p,self.user),saved);self.assertEqual(len(self.db.changes),1)
  delta=self.e['changes']('MGHN',0,200,self.user);self.assertEqual([e for r in delta['changes'] for e in r['data']['entities']],saved['entities'])
  self.assertEqual([{k:v for k,v in r.items() if k!='needs_review'} for r in self.e['entities']('MGHN',None,200,self.user)['entities']],sorted(saved['entities'],key=lambda r:r['id']))
 def test_changed_receipt_and_preview_rejected(self):
  n=[self.node(),self.node(10.001)];p=self.payload(n,[self.obj(n)]);self.save(p);p.objects[0].name='45'
  with self.assertRaises(Error):self.e['mutate'](p,self.user)
  p.client_change_id=str(uuid4())
  with self.assertRaises(Error):self.e['mutate'](p,self.user)
  self.assertEqual(len(self.db.changes),1)
 def test_wez_reused_as_next_wa_and_reverse_track(self):
  n=[self.node(),self.node(10.001),self.node(10.002)];o=self.obj(n,'SWITCH',name='108');self.save(self.payload(n,[o]))
  m=[self.node(10.003),self.node(10.004)];next_o=self.obj([n[2],*m],'SWITCH',name='109');self.save(self.payload(m,[next_o]))
  line=self.obj([m[0],n[1]],'LINE',name='5302-1',route_number='5302');self.save(self.payload([],[line]))
  self.assertEqual(self.db.entities[str(o['id'])]['endpoints'][2]['node_id'],self.db.entities[str(next_o['id'])]['endpoints'][0]['node_id'])
  self.assertEqual(len([e for e in self.db.entities.values() if e['entity_type']=='NODE']),5)
 def test_shared_move_requires_all_versions_and_atomic_objects(self):
  n=[self.node(),self.node(10.001)];o=self.obj(n);self.save(self.payload(n,[o]));changed={**n[0],'base_version':1,'position':dict(lon=10.0001,lat=48)}
  with self.assertRaises(Error) as cm:self.e['preview'](self.payload([changed],[]),self.user)
  self.assertEqual(cm.exception.detail['code'],'NETWORK_SHARED_POINT_CONFIRM')
  with self.assertRaises(Error):self.e['preview'](self.payload([changed],[],affected_objects={o['id']:1}),self.user)
  updated={**o,'base_version':1,'endpoints':[dict(role='START',node_id=n[0]['id'],node_version=2),dict(role='END',node_id=n[1]['id'],node_version=1)]}
  self.save(self.payload([changed],[updated],affected_objects={o['id']:1}))
  self.assertEqual(self.db.entities[str(n[0]['id'])]['version'],2);self.assertEqual(self.db.entities[str(o['id'])]['version'],2)
 def test_deactivation_transmitted(self):
  n=[self.node(),self.node(10.001)];o=self.obj(n);self.save(self.payload(n,[o]));o.update(base_version=1,active=False);self.save(self.payload([],[o]));self.assertFalse(self.e['changes']('MGHN',1,200,self.user)['changes'][0]['data']['entities'][0]['active'])
 def test_stale_update_rollback(self):
  n=[self.node(),self.node(10.001)];o=self.obj(n);self.save(self.payload(n,[o]));o['name']='bad'
  with self.assertRaises(Error):self.e['preview'](self.payload([],[o]),self.user)
  self.assertEqual(self.db.entities[str(o['id'])]['name'],'4')
 def test_schema_and_km_injection(self):
  n=self.node()
  for bad in [dict(position=dict(lon=float('nan'),lat=48)),dict(km_meter=16000),dict(binding=dict(axis_id=uuid4(),axis_version=1,segment_id=uuid4(),segment_version=1,station_m=1))]:
   with self.assertRaises(ValidationError):self.e['Node'](**{**n,**bad})
  with self.assertRaises(ValidationError):self.e['NetworkObject'](**self.obj([n,n]))
  with self.assertRaises(ValidationError):self.e['NetworkObject'](**self.obj([n,self.node()],'LINE'))
 def test_source_path_reverse_and_km_untouched(self):
  a,s,original=uuid4(),uuid4(),uuid4();self.db.sources[str(s)]=dict(axis_id=a,axis_version=2,current_version=1,source_original_id=original,points=[dict(station_m=v,x_dbref=10+v*.00001,y_dbref=48) for v in [0,100]])
  n=[self.node(position=None,binding=dict(axis_id=a,axis_version=2,segment_id=s,segment_version=1,station_m=v)) for v in [80,20]]
  before=deepcopy(self.db.sources);r=self.save(self.payload(n,[self.obj(n)]));path=r['entities'][-1]['paths'][0]
  self.assertEqual(path['length_m'],60);self.assertEqual(path['geometry_quality'],'ORIGINAL_AXIS');self.assertGreater(path['coordinates'][0][0],path['coordinates'][1][0]);self.assertEqual(before,self.db.sources)
 def test_cursor_paging_is_atomic_and_station_scoped(self):
  for i in range(3):
   n=[self.node(10+i*.01),self.node(10.001+i*.01)];self.save(self.payload(n,[self.obj(n)]))
  r=self.e['changes']('MGHN',0,2,self.user);self.assertTrue(r['has_more']);self.assertEqual(len(r['changes'][0]['data']['entities']),3)
  r2=self.e['changes']('MGHN',r['next_after'],2,self.user);self.assertFalse(r2['has_more']);self.assertEqual(r2['next_after'],3)
  other=self.e['changes']('MPB',0,2,self.user);self.assertFalse(other['changes']);self.assertEqual(other['next_after'],3)
 def test_station_cannot_disappear_from_filtered_snapshot(self):
  n=[self.node(),self.node(10.001)];o=self.obj(n);self.save(self.payload(n,[o]));o.update(base_version=1,station_code='MPB')
  with self.assertRaises(Error):self.e['preview'](self.payload([],[o]),self.user)
 def test_manual_km_observation_roundtrip_never_calibrates(self):
  n=[self.node(),self.node(10.001)];obs=dict(id=str(uuid4()),route_number='5302',km_m=16123.456,method='MANUAL_READING',observed_at=None);n[0]['km_observations']=[obs]
  result=self.save(self.payload(n,[self.obj(n)]));self.assertEqual(result['entities'][0]['km_observations'],[obs]);self.assertEqual(result['entities'][0]['km_status'],'UNKNOWN');self.assertEqual(self.db.source_writes,0)
 def test_missing_node_batch_rolls_back(self):
  n=[self.node(),self.node(10.001)];p=self.payload(n[:1],[self.obj(n)])
  with self.assertRaises(Error):self.save(p)
  self.assertFalse(self.db.entities);self.assertFalse(self.db.changes)
 def test_source_review_hides_stale_paths(self):
  a,s=uuid4(),uuid4();n=[self.node(),self.node(10.001)];r=self.save(self.payload(n,[self.obj(n)]));node=self.db.entities[str(n[0]['id'])];node['binding']=dict(axis_id=str(a),segment_id=str(s),segment_version=1,axis_version=1,station_m=20)
  result=self.e['entities']('MGHN',None,200,self.user)['entities'];obj=next(e for e in result if e['entity_type']=='OBJECT');self.assertTrue(obj['needs_review']);self.assertFalse(obj['paths'])
 def test_source_version_conflict_no_writes(self):
  a,s=uuid4(),uuid4();n=[self.node(position=None,binding=dict(axis_id=a,axis_version=1,segment_id=s,segment_version=1,station_m=20)),self.node()]
  with self.assertRaises(Error):self.save(self.payload(n,[self.obj(n)]))
  self.assertFalse(self.db.entities)
 def test_workspace_receipt_entity_type(self):
  tree=ast.parse((ROOT/'tbserver/survey_catalog.py').read_text());fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='_save_receipt')
  e=dict(UUID=UUID,Any=object,Jsonb=lambda x:x);exec(compile(ast.Module(body=[fn],type_ignores=[]),'receipt','exec'),e)
  calls=[];cur=SimpleNamespace(execute=lambda sql,p:calls.append(p))
  e['_save_receipt'](cur,self.user,'one',uuid4(),1,{},entity_type='SURVEY_CATALOG_AXIS');self.assertEqual(calls[-1][3],'SURVEY_CATALOG_AXIS')
  e['_save_receipt'](cur,self.user,'two',uuid4(),1,{});self.assertEqual(calls[-1][3],'SURVEY_CATALOG_SEGMENT')
 def test_general_reset_protects_active_network_references(self):
  tree=ast.parse((ROOT/'tbserver/admin_purge.py').read_text());fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='_assert_no_network_source_dependencies')
  e={'HTTPException':Error};exec(compile(ast.Module(body=[fn],type_ignores=[]),'purge','exec'),e)
  sql=[];cur=SimpleNamespace(execute=lambda q:sql.append(q),fetchone=lambda:{'exists':1})
  with self.assertRaises(Error) as cm:e['_assert_no_network_source_dependencies'](cur)
  self.assertEqual(cm.exception.detail,'SURVEY_TEST_RESET_NETWORK_REFERENCES_ACTIVE');self.assertFalse(any('DELETE' in q for q in sql))
  cur.fetchone=lambda:None;e['_assert_no_network_source_dependencies'](cur)
 def test_legacy_receipt_fingerprint_and_explicit_unlink(self):
  n=[self.node(),self.node(10.001)];p=self.payload(n,[self.obj(n)])
  legacy=p.model_dump(mode='json',exclude={'preview_token'})
  for row in legacy['objects']:row.pop('catalog_link')
  self.assertEqual(self.e['_payload_hash'](p),self.e['_hash'](legacy))
  with_null=self.payload(n,[{**self.obj(n),'id':p.objects[0].id,'catalog_link':None}])
  with_null.client_change_id=p.client_change_id
  self.assertNotEqual(self.e['_payload_hash'](p),self.e['_payload_hash'](with_null))
 def test_global_inventory_paginates_network_only_station(self):
  n=[self.node(),self.node(10.001)];self.save(self.payload(n,[self.obj(n,name='999')]))
  first=self.e['entities'](None,None,2,self.user);self.assertTrue(first['has_more'])
  second=self.e['entities'](None,UUID(first['next_id']),2,self.user)
  self.assertEqual(len(first['entities'])+len(second['entities']),3)
 def test_catalog_link_version_identity_and_old_client_preservation(self):
  key=uuid4();self.db.catalog[str(key)]=dict(id=key,current_version=2,object_type='TRACK',station_code='MGHN')
  n=[self.node(),self.node(10.001)];o=self.obj(n,catalog_link=dict(id=key,version=2));self.save(self.payload(n,[o]))
  updated={**o,'base_version':1,'name':'999'};updated.pop('catalog_link')
  self.save(self.payload([],[updated]));self.assertEqual(self.db.entities[str(o['id'])]['catalog_link']['id'],str(key))
  duplicate=self.obj(n,catalog_link=dict(id=key,version=2))
  with self.assertRaises(Error) as cm:self.save(self.payload([],[duplicate]))
  self.assertEqual(cm.exception.detail,'NETWORK_CATALOG_ALREADY_LINKED')
  self.db.catalog[str(key)]['current_version']=3
  with self.assertRaises(Error) as cm:self.save(self.payload([],[{**updated,'base_version':2}]))
  self.assertEqual(cm.exception.detail,'NETWORK_CATALOG_CHANGED')
 def test_archive_restore_preserves_nodes_and_link(self):
  n=[self.node(),self.node(10.001)];o=self.obj(n);self.save(self.payload(n,[o]));before=deepcopy(self.db.entities[str(n[0]['id'])])
  self.save(self.payload([],[{**o,'base_version':1,'active':False}]))
  self.save(self.payload([],[{**o,'base_version':2,'active':True}]))
  self.assertEqual(self.db.entities[str(n[0]['id'])],before);self.assertTrue(self.db.entities[str(o['id'])]['active'])
  self.assertEqual(self.db.source_writes,0)
 def test_route_auth_dependencies(self):
  tree=ast.parse((ROOT/'tbserver/survey_network.py').read_text())
  for n in tree.body:
   if isinstance(n,ast.FunctionDef) and n.name in ['preview','mutate','entities','entity','entity_history','changes']:
    text=ast.unparse(n.args);self.assertIn('require_csrf' if n.name in ['preview','mutate'] else 'current_portal_user',text)

if __name__=='__main__':unittest.main()

