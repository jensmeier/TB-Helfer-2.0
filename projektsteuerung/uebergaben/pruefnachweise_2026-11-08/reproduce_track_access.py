"""Execute unchanged production handlers with SQL/transaction doubles; no HTTP/DB."""
import ast, json, importlib.util
from pathlib import Path
from contextlib import contextmanager
from uuid import UUID, uuid4
from typing import Any
from fastapi import Depends

import sys
root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[3]/'server/tbserver'
spec=importlib.util.spec_from_file_location('actual_permissions',root/'permissions.py')
permissions=importlib.util.module_from_spec(spec);spec.loader.exec_module(permissions)
company,user_id,worksite_id,dataset_id=uuid4(),uuid4(),uuid4(),uuid4()
user=dict(user_id=user_id,company_id=company,company_role='USER')
context=dict(id=worksite_id,company_id=company,name='Isolated audit',status='ACTIVE',deleted_at=None,
 membership_id=uuid4(),worksite_role='HELPER',permission_profile='HELPER',permissions_override={'worksite.view':False},
 permissions_version=1,area_scope={'km_from_m':0,'km_to_m':100},membership_status='ACTIVE',profile_permissions={})
dataset=dict(id=dataset_id,worksite_id=worksite_id,worksite_node_id=uuid4(),dataset_key='outside',
 dataset_kind='AXIS',payload={'km_from_m':1000,'km_to_m':2000,'audit':'outside helper scope'},deleted_at=None)
class Cursor:
 def __enter__(self):return self
 def __exit__(self,*a):pass
 def execute(self,sql,params=()):self.sql=sql
 def fetchone(self):return context.copy() if 'FROM tb.worksites w' in self.sql else dataset.copy()
 def fetchall(self):return [dataset.copy()]
class Conn:
 def cursor(self):return Cursor()
@contextmanager
def transaction():yield Conn()
tree=ast.parse((root/'track_data.py').read_text())
nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in {'list_track_data','get_track_data','_load_dataset'}]
for n in nodes:n.decorator_list=[]
env=dict(UUID=UUID,Any=Any,Depends=Depends,current_portal_user=lambda:None,
 transaction=transaction,worksite_context=permissions.worksite_context)
exec(compile(ast.Module(body=nodes,type_ignores=[]),str(root/'track_data.py'),'exec'),env)
assert not permissions.area_scope_allows(context['area_scope'],{'km_from_m':1000,'km_to_m':2000})
listed=env['list_track_data'](worksite_id,user)
fetched=env['get_track_data'](dataset_id,user)
assert listed['track_data'][0]['id']==dataset_id
assert fetched['id']==dataset_id
print(json.dumps({'reproduced':True,'list_returns_outside_scope':True,'detail_returns_outside_scope':True,
 'worksite_view_explicitly_false':True,'real_production_handlers_and_worksite_context':True,
 'sql_double':True,'http_postgres_e2e':False}))
