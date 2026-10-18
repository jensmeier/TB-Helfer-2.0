"""Production inventory with auth/database doubles, not a DB/NAS E2E test."""
import importlib.util
import sys
import types
from pathlib import Path
from contextlib import contextmanager
ROOT=Path(__file__).resolve().parents[1]
pkg=types.ModuleType('inventorytest');pkg.__path__=[];sys.modules[pkg.__name__]=pkg
spec=importlib.util.spec_from_file_location('inventorytest.catalog_inventory',ROOT/'tbserver/catalog_inventory.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
axes=[{'id':'a','axis_type':'GLEIS','track_key':'19','display_name':'Gl 19','secret':'PRIVATE',
       'km_quality':{'status':'TEILWEISE','segments':[
           {'status':'AUTOMATISCH','km_from_m':1000,'km_to_m':2000},
           {'status':'MANUELL','km_from_m':5000,'km_to_m':4000},
           {'status':'UNPLAUSIBEL','km_from_m':1,'km_to_m':9000}]}},
      {'id':'w','axis_type':'WEICHE','object_key':'W:117','km_quality':{'status':'KEINE_GEOMETRIE','segments':[]}}]
assets=[{'id':'x','data_kind':'GVP','km_from_m':None,'km_to_m':None,'metadata':{'hidden':True}}]
rows=m.inventory_rows(axes,assets)
assert [(p['km_from_m'],p['km_to_m']) for p in rows[0]['segments']]==[(1000,2000),(4000,5000),(None,None)]
assert rows[1]['status']=='KEINE_GEOMETRIE'
assert rows[2]['status']=='OHNE_KM' and not rows[2]['selectable']
assert 'secret' not in str(rows) and 'metadata' not in str(rows)
assert m._range(0,10)=={'km_from_m':0,'km_to_m':10}
assert m._range(float('nan'),4) is None and m._range(True,4) is None
seen=[]
survey=types.ModuleType('inventorytest.survey_catalog')
def availability(**kwargs): seen.append(kwargs);return {'axes':axes}
survey.availability=availability;sys.modules[survey.__name__]=survey
class Cursor:
 def __enter__(self):return self
 def __exit__(self,*a):pass
 def execute(self,sql,params):
  assert "scope='GLOBAL'" in sql and "data_status='ACTIVE'" in sql
  assert sql.count('%s')==len(params) and params==('5501','MHO')
 def fetchall(self):return assets
class Conn:
 def cursor(self):return Cursor()
@contextmanager
def transaction():yield Conn()
db=types.ModuleType('inventorytest.database');db.transaction=transaction;sys.modules[db.__name__]=db
result=m.shared_inventory('5501','MHO')
assert seen[0]['user']=={'company_role':'USER','company_id':None}
assert result['scope']=='GLOBAL' and result['counts']=={'GLEIS':1,'WEICHE':1,'GVP':1}
assert result['item_count']==3
# HTTP endpoint keeps authenticated dependency and rejects an empty request.
import ast
source=ast.parse((ROOT/'tbserver/catalog.py').read_text())
f=next(n for n in source.body if isinstance(n,ast.FunctionDef) and n.name=='catalog_inventory')
assert 'current_portal_user' in ast.unparse(f.args)
print('155 Python PASS: separate ranges, missing/unreliable KM, geometry absence, whitelist, shared scope/counts/auth')
