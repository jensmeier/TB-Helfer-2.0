"""Actual parser/densifier/catalog functions; synthetic jump fixture and optional private ZIP."""
import ast
from pathlib import Path
import math,re,struct,hashlib,zipfile,sys
from copy import deepcopy
import unittest
ROOT=Path(__file__).resolve().parents[1]
E=dict(math=math,re=re,struct=struct,Any=object)
for filename in ['survey_parser.py','survey_geometry.py','survey_tra_chainage.py','survey_catalog.py']:
 tree=ast.parse((ROOT/'tbserver'/filename).read_text());nodes=[]
 for n in tree.body:
  if isinstance(n,ast.FunctionDef) and not n.decorator_list:
   if filename!='survey_catalog.py' or n.name in ['_num','_clip_station_points','_dense_payload','_segment_points','_segment_physical_length_m','index_original_in_tx','refresh_original_assignment_in_tx']:nodes.append(n)
  elif isinstance(n,ast.Assign) and filename!='survey_catalog.py' and all(isinstance(v,ast.Name) and v.id.isupper() for v in n.targets):nodes.append(n)
 exec('from __future__ import annotations\n'+ast.unparse(ast.Module(body=nodes,type_ignores=[])),E)

class OriginalBoundsTests(unittest.TestCase):
 def setUp(self):
  self.original_dense=E['densify_tra_chainage_payload']
  self.payload=dict(station_from_m=0.,station_to_m=110.)
  self.points=[dict(station_m=s,x_dbref=s,y_dbref=0.) for s in [0.,50.,100.]]
  E['densify_tra_chainage_payload']=lambda p:self.points
  E['_load_original_with_parse']=lambda *a:({},dict(payload=self.payload))
 def tearDown(self):E['densify_tra_chainage_payload']=self.original_dense
 def segment(self,kind='ORIGINAL_TRA',end=110.):return dict(source_kind=kind,source_original_id='source',station_from_m=0.,station_to_m=end)
 def test_complete_original_preserves_normalized_geometry(self):
  before=deepcopy(self.payload);points=E['_segment_points'](None,self.segment())
  self.assertEqual(points[-1]['station_m'],100);self.assertEqual(self.payload,before)
 def test_original_length_excludes_station_jumps(self):
  self.assertEqual(E['_segment_physical_length_m'](None,self.segment()),100)
 def test_manual_outside_does_not_get_full_original(self):
  self.assertEqual(E['_segment_points'](None,self.segment('MANUAL_TRA_SECTION')),[])
 def test_partial_original_still_clipped(self):
  points=E['_segment_points'](None,self.segment(end=25))
  self.assertEqual(points[-1]['station_m'],25)
 def test_reindex_and_assignment_refresh_do_not_resurrect_reset_sources(self):
  class Cur:
   def execute(self,sql,params=None): self.last=sql;assert sql.startswith('SELECT')
   def fetchone(self): return {'id':'asset','metadata':{'workspace_reset':{'previous_status':'ACTIVE'}}}
  for name in ['index_original_in_tx','refresh_original_assignment_in_tx']:
   result=E[name](Cur(),'original')
   self.assertEqual(result['status'],'INACTIVE_RESET')
 def test_effective_editor_bounds_keep_original_identity(self):
  segment=self.segment(end=100);segment['_original_bounds']=(0,110)
  self.assertEqual(E['_segment_points'](None,segment)[-1]['station_m'],100)

if __name__=='__main__':
 if len(sys.argv)>1:
  with zipfile.ZipFile(sys.argv[1]) as z:
   for name in z.namelist():
    if name.endswith('.TRA') and any(key in name for key in ['5302S','5302L','5302R','5340S']):
     _,payload,_=E['_parse_tra'](Path(name).name,z.read(name),'test')
     E['_load_original_with_parse']=lambda *a:({},dict(payload=payload))
     segment=dict(source_kind='ORIGINAL_TRA',source_original_id='test',station_from_m=payload['station_from_m'],station_to_m=payload['station_to_m'])
     points=E['_segment_points'](None,segment)
     assert len(points)>1,name
     print(Path(name).name,len(points),points[0]['station_m'],points[-1]['station_m'])
  sys.argv=sys.argv[:1]
 unittest.main()
