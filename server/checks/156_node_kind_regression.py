"""Production node normalization and PATCH under controlled DB/auth doubles."""
import ast
from pathlib import Path
from contextlib import contextmanager
from typing import Any
from uuid import UUID
source=Path(__file__).resolve().parents[1]/'tbserver/worksites.py'
tree=ast.parse(source.read_text());nodes=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0)]
for n in tree.body:
 if isinstance(n,ast.FunctionDef) and n.name in {'_node_payload','_node_name','update_node'}:
  n.decorator_list=[];n.args.defaults=[ast.Constant(None) for _ in n.args.defaults];nodes.append(n)
class HTTPException(Exception):
 def __init__(self,status,detail):self.status_code=status
class Cursor:
 def __init__(self):self.calls=[];self.rows=[{'id':'n','version':1,'node_kind':'FREIE_STRECKE','payload':{'station_name':'Pasing','station_code':'MPB','km_from':70}}, {'id':'n','version':2}]
 def __enter__(self):return self
 def __exit__(self,*a):pass
 def execute(self,sql,params):assert sql.count('%s')==len(params);self.calls.append((sql,params))
 def fetchone(self):return self.rows.pop(0)
cursor=Cursor()
class Conn:
 def cursor(self):return cursor
@contextmanager
def transaction():yield Conn()
env=dict(Any=Any,UUID=UUID,HTTPException=HTTPException,transaction=transaction,
         begin_mutation=lambda *a,**k:None,worksite_context=lambda *a:{'company_id':'c'},
         require_permission=lambda *a:None,emit_change=lambda *a,**k:None,audit=lambda *a,**k:None,
         finish_mutation=lambda *a,**k:None,Jsonb=lambda x:x)
exec(compile(ast.fix_missing_locations(ast.Module(body=nodes,type_ignores=[])),str(source),'exec'),env)
original={'station_name':'Pasing','station_code':'MPB','km_from':70}
clean=env['_node_payload']('FREIE_STRECKE',original)
assert clean=={'station_name':None,'station_code':None,'km_from':70} and original['station_name']=='Pasing'
assert env['_node_payload']('BAHNHOF',original)==original
class Patch:
 def model_dump(self,**k):return {'base_version':1,'name':'Neue Bezeichnung'}
env['update_node']('w','n',Patch(),{'user_id':'u'},None)
sql,params=cursor.calls[1]
assert 'payload = %s' in sql and clean in params
print('156 Python PASS: free-route sanitation, preserves other metadata/station mode, real name-only PATCH cleans stale station')
