"""REF020 production functions, database/CRS doubles. No live DB or NAS claim."""
import importlib.util
import unittest
from copy import deepcopy
from pathlib import Path
from uuid import uuid4
spec=importlib.util.spec_from_file_location('network_checks',Path(__file__).with_name('127_verify_survey_network.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)

class DB(base.DB):
 def __init__(self):super().__init__();self.calibrations=[]
 def execute(self,sql,p=()):
  if 'SELECT * FROM tb.survey_catalog_segments WHERE axis_id=' in sql:
   self.rows=[deepcopy(r) for r in self.calibrations if str(r['axis_id'])==str(p[0]) and str(r['source_original_id'])==str(p[1]) and r.get('active',True)];return
  super().execute(sql,p)

class ChainageTests(unittest.TestCase):
 def setUp(self):
  self.db=DB();self.e=base.env(self.db);self.user={'user_id':uuid4()}
  c=self.e['_catalog']()
  c._segment_km_quality=lambda cur,axis,row,ctx,points:row.get('quality',{})
  self.axis=str(uuid4());self.segment=str(uuid4());self.original=str(uuid4())
  self.ref=dict(axis_id=self.axis,axis_version=1,segment_id=self.segment,segment_version=1)
  self.points=[dict(station_m=v,x_dbref=10+v/74500,y_dbref=48) for v in (0,50,100)]
  self.db.sources[self.segment]=dict(axis_id=self.axis,axis_version=1,current_version=1,source_original_id=self.original,points=self.points)
 def calibration(self,a=0,b=100,ka=10000,kb=10100):
  r=dict(id=str(uuid4()),axis_id=self.axis,source_original_id=self.original,current_version=1,station_from_m=a,station_to_m=b,km_from_m=ka,km_to_m=kb);self.db.calibrations.append(r);return r
 def node(self,s,bind=True):
  n=dict(id=str(uuid4()),base_version=0,station_code='MGHN')
  if bind:n['binding']={**self.ref,'station_m':s}
  else:n['position']=dict(lon=10+s/74500,lat=48,method='MAP')
  return n
 def obj(self,ns,**kw):
  o=dict(id=str(uuid4()),base_version=0,station_code='MGHN',kind='TRACK',name='1',route_number='5340',endpoints=[dict(role=r,node_id=n['id'],node_version=n['base_version']+1) for r,n in zip(['START','END'],ns)]);o.update(kw);return o
 def payload(self,ns,os,**kw):return self.e['Mutation'](client_change_id=str(uuid4()),nodes=ns,objects=os,**kw)
 def save(self,p):p.preview_token=self.e['preview'](p,self.user)['preview_token'];return self.e['mutate'](p,self.user)
 def last(self,p):return self.save(p)['entities'][-1]
 def test_manual_reverse_roundtrip_and_move(self):
  self.calibration(0,100,11000,10000);ns=[self.node(80),self.node(20)];o=self.obj(ns)
  saved=self.last(self.payload(ns,[o]));vals=saved['kilometers']['endpoints'];self.assertEqual([v['km_m'] for v in vals],[10200,10800]);self.assertEqual(vals[0]['method'],'MANUAL_SECTION')
  moved={**ns[0],'base_version':1,'binding':{**self.ref,'station_m':60}};o['base_version']=1;o['endpoints'][0]['node_version']=2
  new=self.last(self.payload([moved],[o],affected_objects={o['id']:1}));self.assertEqual(new['kilometers']['endpoints'][0]['km_m'],10400)
  loaded=self.e['entity'](base.UUID(o['id']),self.user)['entity'];self.assertEqual(loaded['kilometers'],new['kilometers']);self.assertEqual(self.db.source_writes,0)
 def test_explicit_overlap_reference_and_no_extrapolation(self):
  self.calibration();ns=[self.node(20,False),self.node(80,False)];o=self.obj(ns,km_reference=self.ref)
  saved=self.last(self.payload(ns,[o]));self.assertAlmostEqual(saved['kilometers']['endpoints'][0]['km_m'],10020,places=6)
  # Neither spatial coincidence nor route text alone chooses a reference.
  no=self.last(self.payload(ns:=[self.node(20,False),self.node(80,False)],[self.obj(ns)]));self.assertEqual(no['kilometers']['status'],'UNKNOWN')
  out=self.last(self.payload(ns:=[self.node(-10,False),self.node(80,False)],[self.obj(ns,km_reference=self.ref)]))
  self.assertIsNone(out['kilometers']['endpoints'][0]['km_m']);self.assertEqual(out['kilometers']['endpoints'][0]['reason'],'OUTSIDE_REFERENCE')
 def test_partial_gap_conflict_and_gvp(self):
  self.calibration(0,40,10000,10040);ns=[self.node(20),self.node(80)];o=self.obj(ns)
  saved=self.last(self.payload(ns,[o]));self.assertEqual(saved['kilometers']['status'],'PARTIAL');self.assertEqual(saved['kilometers']['endpoints'][1]['reason'],'NO_CALIBRATION')
  self.db.sources[self.segment]['quality']=dict(source='GVP',median_offset_m=10000,matched_gvp=6)
  loaded=self.e['entity'](base.UUID(o['id']),self.user)['entity'];self.assertEqual(loaded['kilometers']['endpoints'][1]['km_m'],10080)
  self.calibration(0,40,20000,20040)
  loaded=self.e['entity'](base.UUID(o['id']),self.user)['entity'];self.assertEqual(loaded['kilometers']['endpoints'][0]['reason'],'AMBIGUOUS_CALIBRATION')
 def test_calibration_change_invalidates_preview_and_refreshes_read(self):
  cal=self.calibration();ns=[self.node(20),self.node(80)];p=self.payload(ns,[self.obj(ns)])
  p.preview_token=self.e['preview'](p,self.user)['preview_token'];cal['km_from_m']=20000;cal['km_to_m']=20100;cal['current_version']=2
  with self.assertRaises(base.Error) as cm:self.e['mutate'](p,self.user)
  self.assertEqual(cm.exception.detail,'NETWORK_PREVIEW_REQUIRED');self.assertFalse(self.db.entities)
  saved=self.last(p);self.db.calibrations[0]['active']=False
  loaded=self.e['entity'](base.UUID(saved['id']),self.user)['entity'];self.assertEqual(loaded['kilometers']['status'],'UNKNOWN')
 def test_changed_source_suppresses_old_km_and_shape(self):
  self.calibration();ns=[self.node(20),self.node(80)];saved=self.last(self.payload(ns,[self.obj(ns)]))
  self.db.sources[self.segment]['axis_version']=2
  loaded=self.e['entity'](base.UUID(saved['id']),self.user)['entity'];self.assertTrue(loaded['needs_review']);self.assertEqual(loaded['paths'],[]);self.assertEqual(loaded['kilometers']['endpoints'][0]['reason'],'SOURCE_CHANGED')
 def test_old_client_preserves_fields_and_explicit_null_clears(self):
  ns=[self.node(20,False),self.node(80,False)];o=self.obj(ns,km_reference=self.ref,position_status='CONFIRMED');self.last(self.payload(ns,[o]))
  old={**o,'base_version':1};old.pop('km_reference');old.pop('position_status');saved=self.last(self.payload([],[old]));self.assertEqual(saved['km_reference'],self.ref);self.assertEqual(saved['position_status'],'CONFIRMED')
  saved=self.last(self.payload([],[{**old,'base_version':2,'km_reference':None}]));self.assertIsNone(saved['km_reference'])
 def test_wrong_route_no_km_and_gra_range_not_exact_mapping(self):
  self.calibration();ns=[self.node(20),self.node(80)];saved=self.last(self.payload(ns,[self.obj(ns,route_number='5302')]));self.assertEqual(saved['kilometers']['endpoints'][0]['reason'],'ROUTE_MISMATCH')
  self.db.calibrations=[];self.db.sources[self.segment]['quality']=dict(source='GRA_KM',km_from_m=0,km_to_m=100)
  saved=self.last(self.payload(ns:=[self.node(20),self.node(80)],[self.obj(ns)]));self.assertEqual(saved['kilometers']['status'],'UNKNOWN')
 def test_projection_rejects_ambiguous_loop_and_far_offset(self):
  coords=[[10,48],[10.001,48],[10,48]];pts=[dict(station_m=s) for s in (0,100,200)]
  self.assertEqual(self.e['_project_reference'](dict(lon=10.0005,lat=48),coords,pts)[1],'AMBIGUOUS_POSITION')
  self.assertEqual(self.e['_project_reference'](dict(lon=10.0005,lat=48.001),coords,pts)[1],'OUTSIDE_REFERENCE')
 def test_multi_source_curve_order_gap_and_endpoints(self):
  # Keep real intermediate geometry in the clipping double for this test.
  original_clip=self.e['_catalog']()._clip_station_points
  self.e['_catalog']()._clip_station_points=lambda pts,a,b:[self.e['_point'](pts,a),*[p for p in pts if a<p['station_m']<b],self.e['_point'](pts,b)]
  second=str(uuid4());ref2={**self.ref,'segment_id':second};self.db.sources[second]={**self.db.sources[self.segment],'points':[dict(station_m=100,x_dbref=10+100/74500,y_dbref=48),dict(station_m=150,x_dbref=10+150/74500,y_dbref=48.0001),dict(station_m=200,x_dbref=10+200/74500,y_dbref=48)]}
  ns=[self.node(20),{**self.node(200),'binding':{**ref2,'station_m':200}}];sections=[{**self.ref,'role':'TRACK','station_from_m':20,'station_to_m':100},{**ref2,'role':'TRACK','station_from_m':100,'station_to_m':200}]
  saved=self.last(self.payload(ns,[self.obj(ns,path_sections=sections)]));self.assertEqual(saved['paths'][0]['length_m'],180);self.assertTrue(any(p[1]>48 for p in saved['paths'][0]['coordinates']))
  sections[1]['station_from_m']=110
  with self.assertRaises(base.Error) as cm:self.e['preview'](self.payload(ns:=[self.node(20),{**self.node(200),'binding':{**ref2,'station_m':200}}],[self.obj(ns,path_sections=sections)]),self.user)
  self.assertEqual(cm.exception.detail,'NETWORK_PATH_GAP')
  sections[1]['station_from_m']=100;sections[0]['station_from_m']=30
  with self.assertRaises(base.Error) as cm:self.e['preview'](self.payload(ns,[self.obj(ns,path_sections=sections)]),self.user)
  self.assertEqual(cm.exception.detail,'NETWORK_PATH_ENDPOINT_MISMATCH')
 def test_shared_move_adjusts_existing_section_boundaries_atomically(self):
  ns=[self.node(0),self.node(80),self.node(100)]
  a=self.obj(ns[:2],path_sections=[{**self.ref,'role':'TRACK','station_from_m':0,'station_to_m':80}])
  b=self.obj(ns[1:],path_sections=[{**self.ref,'role':'TRACK','station_from_m':80,'station_to_m':100}],name='2')
  self.save(self.payload(ns,[a,b]))
  moved={**ns[1],'base_version':1,'binding':{**self.ref,'station_m':70}}
  for o in (a,b):
   o['base_version']=1;o.pop('path_sections')
   for e in o['endpoints']:
    if e['node_id']==moved['id']:e['node_version']=2
  self.save(self.payload([moved],[a,b],affected_objects={a['id']:1,b['id']:1}))
  self.assertEqual(self.db.entities[a['id']]['path_sections'][0]['station_to_m'],70)
  self.assertEqual(self.db.entities[b['id']]['path_sections'][0]['station_from_m'],70)
  self.assertEqual(len(self.db.changes[-1]['data']['entities']),3)
 def test_weiche_envelope_has_three_metre_start_and_broad_end(self):
  paths=[dict(coordinates=[[10,48],[10.001,48]]),dict(coordinates=[[10,48],[10.001,48.0001]])]
  area=self.e['_switch_area'](paths);self.assertEqual(area['half_width_m'],1.5);self.assertEqual(area['coordinates'][0][0],area['coordinates'][0][-1])
  ring=area['coordinates'][0];self.assertGreater((max(p[1] for p in ring)-min(p[1] for p in ring))*111320,13)
  straight=self.e['_switch_area']([paths[0]])['coordinates'][0];self.assertAlmostEqual((max(p[1] for p in straight)-min(p[1] for p in straight))*111320,3,places=6)
  reverse=self.e['_switch_area']([dict(coordinates=list(reversed(p['coordinates']))) for p in paths]);self.assertEqual(len(reverse['coordinates'][0]),len(ring))

if __name__=='__main__':unittest.main()
