"""Production editor functions + real Pydantic, isolated storage doubles; no DB E2E."""
import ast
from copy import deepcopy
from contextlib import contextmanager
from pathlib import Path
from uuid import UUID, uuid4
import math
import unittest
from pydantic import BaseModel, Field, ValidationError

SOURCE=Path(__file__).resolve().parents[1]/'tbserver/survey_catalog.py'
TREE=ast.parse(SOURCE.read_text())
AXIS,SOURCE_ID,ORIGINAL_ID=uuid4(),uuid4(),uuid4()
BASE=dict(id=SOURCE_ID,axis_id=AXIS,source_kind='ORIGINAL_TRA',source_original_id=ORIGINAL_ID,
          source_signature='abc',segment_status='ACTIVE',current_version=1,station_from_m=0.,station_to_m=100.)
USER={'user_id':uuid4()}
class HTTPError(Exception):
    def __init__(self,status,detail):self.status_code=status;self.detail=detail
class UniqueViolation(Exception):pass
class Store:
    def __init__(self):
        self.axis=dict(id=AXIS,scope='GLOBAL',status='ACTIVE',current_version=3)
        self.rows=[deepcopy(BASE)];self.receipts={};self.history=[];self.audit=[];self.sql=[]
    def __enter__(self):return self
    def __exit__(self,*a):pass
    def cursor(self):return self
    def execute(self,sql,params=None):
        self.sql.append((sql,params));self.result=None
        if sql.startswith('SELECT * FROM tb.survey_catalog_axes'):self.result=self.axis if params[0]==AXIS and self.axis['scope']=='GLOBAL' else None
        elif sql.startswith('SELECT * FROM tb.survey_catalog_segments'):self.result=self.rows
        elif 'UPDATE tb.survey_catalog_segments' in sql:
            row=next(s for s in self.rows if s['id']==params[-2]);assert row['current_version']==params[-1]
            row.update(zip(['station_from_m','station_to_m','km_from_m','km_to_m'],params[:4]))
            row.update(metadata=params[8],current_version=row['current_version']+1,geometry=None)
            self.result=row
        else:raise AssertionError(sql)
    def fetchone(self):return deepcopy(self.result)
    def fetchall(self):return deepcopy(self.result)

def env(store):
    @contextmanager
    def transaction():yield store
    def insert(cur,**kw):
        row={**kw,'id':uuid4(),'current_version':1,'axis_id':AXIS,'segment_status':'ACTIVE'}
        store.rows.append(row);store.history.append({'operation':'CREATE'});return row
    def mark(cur,row,status,actor):
        target=next(s for s in store.rows if s['id']==row['id']);target.update(segment_status=status,current_version=row['current_version']+1)
        store.history.append({'operation':'STATUS'});return target
    names={'AdminKmSection','AdminKmDeactivate','_km_editor_replay','_km_editor_fingerprint','_km_editor_rows',
           '_km_editor_validate','_km_editor_prepare','management_preview_km','management_save_km',
           'management_deactivate_km','_management_axis','_coverage_interval','_clip_station_points','_num','_bbox','_decimate','_dense_payload'}
    nodes=deepcopy([n for n in TREE.body if isinstance(n,(ast.ClassDef,ast.FunctionDef)) and n.name in names])
    for n in nodes:
        if isinstance(n,ast.FunctionDef):n.decorator_list=[]
    points=[{'station_m':i,'x_dbref':4400000+i,'y_dbref':5300000+i/10} for i in range(0,101,10)]
    e=dict(Any=object,UUID=UUID,BaseModel=BaseModel,Field=Field,math=math,HTTPException=HTTPError,
           Depends=lambda f:None,require_admin_csrf=lambda:None,UniqueViolation=UniqueViolation,Jsonb=lambda v:v,
           transaction=transaction,_row=lambda *a:deepcopy(store.axis),_segment_points=lambda *a:points,
           densify_tra_chainage_payload=lambda *a:points,
           _receipt=lambda c,u,k:store.receipts.get(k),_save_receipt=lambda c,u,k,i,v,r:store.receipts.update({k:r}),
           _segment_insert=insert,_mark_segment_status=mark,_axis_extent_refresh=lambda *a:None,
           _history=lambda *a,**k:store.history.append(k),audit=lambda *a,**k:store.audit.append(k))
    exec(compile(ast.Module(body=nodes,type_ignores=[]),str(SOURCE),'exec'),e)
    return e

def payload(e,**kw):
    return e['AdminKmSection'](**(dict(base_version=3,source_segment_id=SOURCE_ID,source_version=1,
        station_from_m=5.,station_to_m=25.,km_from_m=16005.,km_to_m=16025.,client_change_id='admin-km:1')|kw))

class EditorTests(unittest.TestCase):
    def setUp(self):self.db=Store();self.e=env(self.db)
    def test_preview_is_read_only_and_clips_endpoints(self):
        r=self.e['management_preview_km'](AXIS,payload(self.e),USER)
        self.assertEqual(r['length_m'],20);self.assertFalse(r['length_warning'])
        self.assertEqual(r['points'][0],[4400005.,5300000.5]);self.assertEqual(r['points'][-1],[4400025.,5300002.5])
        self.assertEqual(len(self.db.rows),1);self.assertEqual(self.db.history,[]);self.assertEqual(self.db.receipts,{})
    def test_narrow_clip_and_outside_do_not_return_whole_axis(self):
        r=self.e['_dense_payload']({},5,5.1);self.assertEqual(len(r),2)
        self.assertEqual(r[0]['station_m'],5);self.assertEqual(r[-1]['station_m'],5.1)
        self.assertEqual(self.e['_dense_payload']({},200,210),[])
    def test_nonfinite_rejected(self):
        for k in ['station_from_m','station_to_m','km_from_m','km_to_m']:
            for v in [float('nan'),float('inf')]:
                with self.assertRaises(ValidationError):payload(self.e,**{k:v})
    def test_invalid_bounds_and_equal_km_rejected_before_write(self):
        for kw in [dict(station_from_m=-1),dict(station_to_m=101),dict(station_to_m=5),dict(km_to_m=16005)]:
            with self.assertRaises(HTTPError):self.e['management_save_km'](AXIS,payload(self.e,**kw),USER)
        self.assertEqual(len(self.db.rows),1);self.assertEqual(self.db.history,[])
    def test_stale_versions_and_wrong_source_rejected(self):
        for kw in [dict(base_version=2),dict(source_version=2),dict(source_segment_id=uuid4())]:
            with self.assertRaises(HTTPError):self.e['management_save_km'](AXIS,payload(self.e,**kw),USER)
        self.assertEqual(len(self.db.rows),1)
    def test_archived_axis_is_readonly(self):
        self.db.axis['status']='ARCHIVED'
        with self.assertRaises(HTTPError):self.e['management_save_km'](AXIS,payload(self.e),USER)
    def test_save_preserves_source_and_replay_is_idempotent(self):
        p=payload(self.e);r=self.e['management_save_km'](AXIS,p,USER)
        self.assertEqual(self.e['management_save_km'](AXIS,p,USER),r)
        self.assertEqual(len(self.db.rows),2);self.assertEqual(self.db.rows[0],BASE)
        self.assertEqual(self.db.rows[1]['source_kind'],'MANUAL_TRA_SECTION')
        self.assertEqual(self.db.rows[1]['metadata']['origin'],'ADMIN');self.assertEqual(len(self.db.audit),1)
    def test_mutation_id_cannot_be_reused_for_changed_values(self):
        self.e['management_save_km'](AXIS,payload(self.e),USER)
        with self.assertRaises(HTTPError) as err:self.e['management_save_km'](AXIS,payload(self.e,note='other'),USER)
        self.assertEqual(err.exception.status_code,409)
    def test_falling_km_and_explicit_length_confirmation(self):
        p=payload(self.e,km_from_m=17000,km_to_m=16000)
        r=self.e['management_preview_km'](AXIS,p,USER);self.assertTrue(r['length_warning']);self.assertEqual(r['direction'],'FALLEND')
        with self.assertRaises(HTTPError):self.e['management_save_km'](AXIS,p,USER)
        self.e['management_save_km'](AXIS,p.model_copy(update={'confirm_length_difference':True}),USER)
        self.assertEqual(len(self.db.rows),2)
    def test_conflicting_overlap_blocked_and_consistent_allowed(self):
        self.e['management_save_km'](AXIS,payload(self.e),USER)
        with self.assertRaises(HTTPError) as err:
            self.e['management_save_km'](AXIS,payload(self.e,client_change_id='2',km_from_m=16006,km_to_m=16026),USER)
        self.assertEqual(err.exception.detail,'SURVEY_KM_OVERLAP_CONFLICT')
        self.e['management_save_km'](AXIS,payload(self.e,client_change_id='3',station_from_m=15,station_to_m=35,km_from_m=16015,km_to_m=16035),USER)
        self.assertEqual(len(self.db.rows),3)
    def test_update_and_deactivate_only_manual_record(self):
        self.e['management_save_km'](AXIS,payload(self.e),USER);s=self.db.rows[-1]
        s['metadata']['origin']='ANDROID'
        p=payload(self.e,segment_id=s['id'],segment_version=1,client_change_id='edit',note='corrected')
        r=self.e['management_save_km'](AXIS,p,USER);self.assertEqual(r['entity_version'],2)
        self.assertEqual(len(self.db.rows),2);self.assertEqual(s['metadata']['note'],'corrected')
        self.assertEqual(s['metadata']['origin'],'ANDROID');self.assertEqual(s['metadata']['last_edited_via'],'ADMIN')
        p=self.e['AdminKmDeactivate'](base_version=3,segment_version=2,client_change_id='delete')
        self.e['management_deactivate_km'](AXIS,s['id'],p,USER)
        self.assertEqual(s['segment_status'],'INACTIVE');self.assertEqual(self.db.rows[0],BASE)
    def test_original_cannot_be_deactivated(self):
        p=self.e['AdminKmDeactivate'](base_version=3,segment_version=1,client_change_id='delete')
        with self.assertRaises(HTTPError):self.e['management_deactivate_km'](AXIS,SOURCE_ID,p,USER)
        self.assertEqual(self.db.rows[0],BASE)
    def test_edit_stale_segment_or_other_source_blocked(self):
        self.e['management_save_km'](AXIS,payload(self.e),USER);s=self.db.rows[-1]
        with self.assertRaises(HTTPError):self.e['management_save_km'](AXIS,payload(self.e,segment_id=s['id'],segment_version=99,client_change_id='edit'),USER)
    def test_all_routes_require_admin_csrf(self):
        nodes=[n for n in TREE.body if isinstance(n,ast.FunctionDef) and n.name in {'management_preview_km','management_save_km','management_deactivate_km'}]
        self.assertEqual(len(nodes),3)
        for n in nodes:self.assertIn('Depends(require_admin_csrf)',ast.unparse(n.args))

if __name__=='__main__':unittest.main()
