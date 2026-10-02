"""Actual production functions, Pydantic models and cursor doubles. No HTTP/DB/E2E claim."""
import ast
from contextlib import contextmanager
from copy import deepcopy
from datetime import date, datetime
from pathlib import Path
from uuid import UUID
import unittest
from pydantic import BaseModel, Field

SOURCE=Path(__file__).resolve().parents[1]/'tbserver/survey_catalog.py'
TREE=ast.parse(SOURCE.read_text())
ID=UUID('00000000-0000-0000-0000-000000000001')
USER={'user_id':ID,'company_role':'ADMIN','company_id':None}
BASE=dict(id=ID,scope='GLOBAL',company_id=None,status='ACTIVE',current_version=3,
          identity_key='G:S:MGHN:O:W:107W',display_name='W 107W',station_code='MGHN',
          station_name='Gessertshausen',route_number=None,track_key=None,object_key='W:107W',
          direction=None,axis_type='WEICHE',metadata={'preserve':'value'})
class HTTPError(Exception):
    def __init__(self,status,detail): self.status_code=status;self.detail=detail
class UniqueViolation(Exception): pass
class Cursor:
    def __init__(self,results): self.results=list(results);self.statements=[]
    def __enter__(self): return self
    def __exit__(self,*args): pass
    def cursor(self): return self
    def execute(self,sql,params=None):
        self.statements.append((sql,params))
        assert not sql.lstrip().startswith(('DELETE','INSERT')), 'no dependent mutation'
        if sql.lstrip().startswith('UPDATE'): assert 'UPDATE tb.survey_catalog_axes' in sql
        self.result=self.results.pop(0)
        if isinstance(self.result,Exception): raise self.result
    def fetchone(self): return self.result
    def fetchall(self): return self.result

def env(cur):
    @contextmanager
    def transaction(): yield cur
    names={'AxisAdminUpdate','AxisStatusUpdate','_clean','_plain','_identity_key','_management_axis',
           'management_axes','management_update_axis','_management_axis_status',
           'management_archive_axis','management_restore_axis','_row','_axis_review'}
    nodes=deepcopy([n for n in TREE.body if isinstance(n,(ast.FunctionDef,ast.ClassDef)) and n.name in names])
    for n in nodes:
        if isinstance(n,ast.FunctionDef): n.decorator_list=[]
    history=[];audits=[]
    e=dict(BaseModel=BaseModel,Field=Field,Any=object,UUID=UUID,date=date,datetime=datetime,
           Depends=lambda f:None,Query=lambda default=None,**kw:default,require_admin=lambda:None,
           require_admin_csrf=lambda:None,HTTPException=HTTPError,transaction=transaction,
           Jsonb=lambda x:x,UniqueViolation=UniqueViolation,_SCOPE_GLOBAL='GLOBAL',
           _AXIS_TYPES={'GLEIS','STRECKE','WEICHE','WEICHENVERBINDUNG','UNBEKANNT'},
           _history=lambda *args,**kw:history.append(kw),audit=lambda *args,**kw:audits.append(kw))
    exec(compile(ast.Module(body=nodes,type_ignores=[]),str(SOURCE),'exec'),e)
    return e,history,audits

class ManagementTests(unittest.TestCase):
    def test_archive_preserves_children_and_metadata(self):
        updated={**BASE,'status':'ARCHIVED','current_version':4}
        cur=Cursor([BASE,updated]);e,h,a=env(cur)
        result=e['management_archive_axis'](ID,e['AxisStatusUpdate'](base_version=3),USER)
        self.assertEqual(result['status'],'ARCHIVED')
        self.assertIn('FOR UPDATE',cur.statements[0][0]);self.assertIn("scope='GLOBAL'",cur.statements[0][0])
        params=cur.statements[1][1]
        self.assertEqual(params[1],{'preserve':'value','admin_archive_previous_status':'ACTIVE'})
        self.assertEqual(params[-1],3);self.assertEqual(h[0]['operation'],'STATUS');self.assertEqual(len(a),1)
    def test_stale_version_writes_nothing(self):
        cur=Cursor([BASE]);e,h,a=env(cur)
        with self.assertRaises(HTTPError) as ex:e['management_archive_axis'](ID,e['AxisStatusUpdate'](base_version=2),USER)
        self.assertEqual(ex.exception.status_code,409);self.assertEqual(len(cur.statements),1);self.assertEqual(h,[])
    def test_restore_review_and_metadata(self):
        for prior,open_count,expected in [('ACTIVE',0,'ACTIVE'),('REVIEW',0,'REVIEW'),('ACTIVE',1,'REVIEW')]:
            with self.subTest(prior=prior,open_count=open_count):
                archived={**BASE,'status':'ARCHIVED','metadata':{'preserve':'value','admin_archive_previous_status':prior}}
                cur=Cursor([archived,{'n':open_count},{**BASE,'status':expected,'current_version':4}]);e,h,a=env(cur)
                e['management_restore_axis'](ID,e['AxisStatusUpdate'](base_version=3),USER)
                self.assertEqual(cur.statements[-1][1][0],expected);self.assertEqual(cur.statements[-1][1][1],{'preserve':'value'})
    def test_repeated_same_status_is_noop(self):
        cur=Cursor([{**BASE,'status':'ARCHIVED'}]);e,h,a=env(cur)
        e['management_archive_axis'](ID,e['AxisStatusUpdate'](base_version=3),USER)
        self.assertEqual(len(cur.statements),1);self.assertFalse(h)
    def test_unknown_or_private_not_returned(self):
        cur=Cursor([None]);e,h,a=env(cur)
        with self.assertRaises(HTTPError) as ex:e['_management_axis'](cur,ID,3)
        self.assertEqual(ex.exception.status_code,404)
    def test_name_edit_keeps_identity(self):
        cur=Cursor([BASE,None,{**BASE,'display_name':'Weiche 107W','current_version':4}]);e,h,a=env(cur)
        e['management_update_axis'](ID,e['AxisAdminUpdate'](base_version=3,display_name='Weiche 107W'),USER)
        self.assertEqual(cur.statements[-1][1][8],BASE['identity_key']);self.assertEqual(h[0]['operation'],'UPDATE')
    def test_assignment_updates_identity(self):
        cur=Cursor([BASE,None,{**BASE,'object_key':'W:108W','current_version':4}]);e,h,a=env(cur)
        e['management_update_axis'](ID,e['AxisAdminUpdate'](base_version=3,object_key='w:108w'),USER)
        self.assertEqual(cur.statements[-1][1][8],'G:S:MGHN:O:W:108W')
    def test_collision_does_not_merge(self):
        for collision in [{'id':ID},None]:
            results=[BASE,collision] if collision else [BASE,None,UniqueViolation()]
            cur=Cursor(results);e,h,a=env(cur)
            with self.assertRaises(HTTPError) as ex:e['management_update_axis'](ID,e['AxisAdminUpdate'](base_version=3,object_key='W:108W'),USER)
            self.assertEqual(ex.exception.detail,'SURVEY_AXIS_IDENTITY_EXISTS');self.assertFalse(h)
    def test_archived_or_blank_name_not_editable(self):
        for axis,kwargs in [({**BASE,'status':'ARCHIVED'},{'display_name':'new'}),(BASE,{'display_name':'   '})]:
            cur=Cursor([axis]);e,h,a=env(cur)
            with self.assertRaises(HTTPError):e['management_update_axis'](ID,e['AxisAdminUpdate'](base_version=3,**kwargs),USER)
            self.assertEqual(len(cur.statements),1)
    def test_review_cannot_unarchive_stale_axis(self):
        cur=Cursor([{**BASE,'status':'ARCHIVED','current_version':4}]);e,h,a=env(cur)
        result=e['_axis_review'](cur,BASE,ID)
        self.assertEqual(result['status'],'ARCHIVED');self.assertFalse(h)
    def test_list_is_bounded(self):
        cur=Cursor([[BASE]*3]);e,h,a=env(cur)
        result=e['management_axes'](archived=True,q='107W',limit=2,offset=4)
        self.assertEqual(len(result['axes']),2);self.assertTrue(result['has_more']);self.assertEqual(cur.statements[0][1],(True,'107W','%107W%',3,4))
    def test_routes_require_admin_and_csrf(self):
        funcs={n.name:n for n in TREE.body if isinstance(n,ast.FunctionDef)}
        for name in ['management_update_axis','management_archive_axis','management_restore_axis']:
            self.assertIn('Depends(require_admin_csrf)',ast.unparse(funcs[name].args))
        self.assertIn('Depends(require_admin)',ast.unparse(funcs['management_axes'].args))

if __name__=='__main__': unittest.main(verbosity=2)
