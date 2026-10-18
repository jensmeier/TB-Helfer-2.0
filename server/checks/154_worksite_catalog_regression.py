"""Production functions with controlled DB/auth doubles; no real DB/NAS E2E."""
import ast
import sys
import types
from pathlib import Path
from contextlib import contextmanager
from typing import Any
from uuid import UUID

ROOT=Path(__file__).resolve().parents[1]
class HTTPException(Exception):
    def __init__(self,status_code,detail): self.status_code=status_code;self.detail=detail

def functions(path,names,env):
    tree=ast.parse((ROOT/path).read_text())
    nodes=[ast.ImportFrom(module="__future__",names=[ast.alias(name="annotations")],level=0)]
    for node in tree.body:
        if isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef)) and node.name in names:
            node.decorator_list=[]
            # FastAPI defaults are provided explicitly in tests.
            node.args.defaults=[ast.Constant(None) for _ in node.args.defaults]
            nodes.append(node)
    exec(compile(ast.fix_missing_locations(ast.Module(body=nodes,type_ignores=[])),str(path),'exec'),env)

class Cursor:
    def __init__(self,rows=()): self.rows=list(rows);self.executed=[]
    def __enter__(self): return self
    def __exit__(self,*_): pass
    def execute(self,sql,params=()):
        assert sql.count('%s')==len(params),(sql,params)
        self.executed.append((sql,params))
    def fetchall(self): return self.rows.pop(0)
    def fetchone(self): return self.rows.pop(0)
class Conn:
    def cursor(self): return cursor
@contextmanager
def transaction(): yield Conn()

env=dict(Any=Any,UUID=UUID,HTTPException=HTTPException,transaction=transaction)
functions(Path('tbserver/worksites.py'),{'_worksite_name','_node_name','list_worksites','delete_worksite'},env)
P=types.SimpleNamespace
for kwargs,expected in [({'designation':'GE Heideck'},'GE Heideck'),({'route_number':'5501'},'Strecke 5501'),({'station_name':'Holzkirchen'},'Holzkirchen'),({'name':' Alt '},'Alt')]:
    data=dict(name='',designation=None,route_number=None,station_name=None,station_code=None);data.update(kwargs)
    assert env['_worksite_name'](P(**data))==expected
try: env['_worksite_name'](P(name='',designation=None,route_number=None,station_name=None,station_code=None));raise AssertionError()
except HTTPException as e: assert e.status_code==400
assert env['_node_name'](P(name='',payload={'station_name':'Holzkirchen'},route_number=None,track_key=None))=='Holzkirchen'
for role in ['CHEF','USER']:
    cursor=Cursor([[]]);env['list_worksites']({'company_role':role,'user_id':'u','company_id':'c'})
    sql,params=cursor.executed[0];assert 'can_delete' in sql and 'w.version' in sql and "RESPONSIBLE_TB" in sql
    assert params==('u','c')
# Existing deletion guard/version conflict still enforced before UPDATE.
env.update(begin_mutation=lambda *a,**k:None,worksite_context=lambda *a:{'worksite_role':'HELPER'})
cursor=Cursor()
try: env['delete_worksite']('w',1,{},None);raise AssertionError()
except HTTPException as e: assert e.status_code==403
assert not cursor.executed
env['worksite_context']=lambda *a:{'worksite_role':'RESPONSIBLE_TB'}
cursor=Cursor([{'id':'w','company_id':'c','name':'Test','version':2}])
try: env['delete_worksite']('w',1,{},None);raise AssertionError()
except HTTPException as e: assert e.status_code==409
assert len(cursor.executed)==1

# Valid delete uses existing tombstone/changefeed path; never physical DELETE.
env.update(emit_change=lambda *a,**k:None,audit=lambda *a,**k:None,
           finish_mutation=lambda *a,**k:None,Response=lambda **k:k)
cursor=Cursor([{'id':'w','company_id':'c','name':'Test','version':1},
               {'version':2,'deleted_at':'now'}])
assert env['delete_worksite']('w',1,{'user_id':'u'},None)=={'status_code':204}
assert any('TRASHED' in sql for sql,_ in cursor.executed)
assert any('INSERT INTO tb.deletion_tombstones' in sql for sql,_ in cursor.executed)
assert not any('DELETE FROM' in sql for sql,_ in cursor.executed)
# Actual context rejects another company before granting worksite access.
functions(Path('tbserver/permissions.py'),{'worksite_context'},env)
cursor=Cursor([{'company_id':'other','deleted_at':None}])
try: env['worksite_context'](cursor,{'company_role':'USER','company_id':'c','user_id':'u'},'w');raise AssertionError()
except HTTPException as e: assert e.status_code==403

# Real lookup combining station reference with permitted survey results.
pkg=types.ModuleType('tbtest');pkg.__path__=[];sys.modules['tbtest']=pkg
ref=types.ModuleType('tbtest.station_reference');survey=types.ModuleType('tbtest.survey_catalog')
ref._normalize=lambda s:s.strip().casefold()
ref._entries=lambda:({'code':'MHO','name':'Holzkirchen','display':'MHO – Holzkirchen'},)
ref._match_rank=lambda q,i:(0,i['name'],i['code']) if q in (i['code']+' '+i['name']).casefold() else None
seen=[]
def search_catalog(**kwargs):
    seen.append(kwargs)
    return {'routes':[{'route_number':'5501'}], 'stations':[], 'axes':[]}
survey.search_catalog=search_catalog
sys.modules[ref.__name__]=ref;sys.modules[survey.__name__]=survey
env['__package__']='tbtest'
functions(Path('tbserver/catalog.py'),{'lookup_catalog'},env)
cursor=Cursor([[],[]]);user={'user_id':'u'};result=env['lookup_catalog']('mho',user)
assert result['stations'][0]=={'code':'MHO','name':'Holzkirchen','data_available':False}
assert seen[-1]['user']=={'company_role':'USER','company_id':None}
assert result['routes'][0]['data_available'] is True
ref._entries=lambda:(_ for _ in ()).throw(RuntimeError('missing'))
cursor=Cursor([[],[]]);assert env['lookup_catalog']('5501',user)['warnings']
print('154 Python PASS: names, list SQL, delete rights/conflict, lookup merge/auth/reference failure')
