"""REF019 production read functions with cursor doubles, not a PostgreSQL/HTTP test."""
import ast
import unittest
from contextlib import contextmanager
from pathlib import Path
from uuid import UUID,uuid4
from typing import Any
ROOT=Path(__file__).resolve().parents[1]
class Cursor:
 def __init__(self,rows):self.rows=rows;self.calls=[]
 def execute(self,sql,args):self.calls.append((sql,args))
 def fetchall(self):return self.rows
 def __enter__(self):return self
 def __exit__(self,*args):pass
 def cursor(self):return self
class ReadTests(unittest.TestCase):
 def test_bounded_overview_preserves_geometry_and_reports_timing(self):
  axis=uuid4();segment=uuid4();rows=[dict(id=segment,axis_id=axis,geometry=[dict(x_dbref=10,y_dbref=48),dict(x_dbref=11,y_dbref=49)])]
  cur=Cursor(rows)
  @contextmanager
  def tx():yield cur
  tree=ast.parse((ROOT/'tbserver/survey_catalog.py').read_text());fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='coverage_overview_lines');fn.decorator_list=[]
  e=dict(UUID=UUID,Any=Any,Query=lambda **kw:None,Depends=lambda x:None,require_admin=lambda:None,transaction=tx,_num=lambda x:x,_segment_points=lambda c,s:s['geometry'],_decimate=lambda p,n:p,_postgis_transform_points=lambda c,p:p)
  exec(compile(ast.Module(body=[fn],type_ignores=[]),'overview','exec'),e)
  result=e['coverage_overview_lines']([axis],{})
  self.assertEqual(result['axes'][0]['overview_segments'][0]['coordinates'],[[10,48],[11,49]])
  self.assertGreaterEqual(result['timing_ms']['total'],0)
  self.assertEqual(cur.calls[0][1],([axis],));self.assertIn("scope='GLOBAL'",cur.calls[0][0]);self.assertIn("status<>'ARCHIVED'",cur.calls[0][0])
  self.assertIn('max_length=8',ast.unparse(fn.args));self.assertFalse(any('UPDATE' in sql for sql,_ in cur.calls))
 def test_history_neutral_field_differences(self):
  current=dict(data=dict(station_code='MGHN'));row=dict(snapshot=dict(entity_type='OBJECT',version=2,name='999',active=True),previous=dict(name='998',active=True),changed_at='2026-10-04T20:00:00Z')
  cur=Cursor([row]);cur.fetchone=lambda:current
  @contextmanager
  def tx():yield cur
  tree=ast.parse((ROOT/'tbserver/survey_network.py').read_text());fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='entity_history');fn.decorator_list=[]
  e=dict(UUID=UUID,Depends=lambda x:None,current_portal_user=lambda:None,transaction=tx,HTTPException=RuntimeError)
  exec(compile(ast.Module(body=[fn],type_ignores=[]),'history','exec'),e)
  result=e['entity_history'](uuid4(),{})
  self.assertEqual(result['history'][0]['fields'],[dict(field='name',before='998',after='999')]);self.assertNotIn('actor',str(result));self.assertFalse(result['has_more'])
if __name__=='__main__':unittest.main()
