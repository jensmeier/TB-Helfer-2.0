"""Actual production functions with Pydantic/storage doubles; not HTTP/DB E2E."""
import ast
import importlib.util
from copy import deepcopy
from pathlib import Path
from uuid import uuid4
import unittest

spec=importlib.util.spec_from_file_location('km',Path(__file__).with_name('123_verify_admin_km_editor.py'))
km=importlib.util.module_from_spec(spec);spec.loader.exec_module(km)
ASSET=uuid4()

def point(x,y,s):return dict(x_dbref=x,y_dbref=y,station_m=s)
class GvpTests(unittest.TestCase):
    def setUp(self):
        self.store=km.Store();self.e=km.env(self.store)
        names={'AdminGvpCalculate','_gvp_projection','_gvp_resolve','_gvp_calibration','_gvp_source','management_gvp_points','management_gvp_calculate'}
        nodes=deepcopy([n for n in km.TREE.body if isinstance(n,(ast.ClassDef,ast.FunctionDef)) and n.name in names])
        for n in nodes:
            if isinstance(n,ast.FunctionDef):n.decorator_list=[]
        self.e.update(require_admin=lambda:None,_GVP_DATA_KINDS={'GVP','KF'})
        exec(compile(ast.Module(body=nodes,type_ignores=[]),str(km.SOURCE),'exec'),self.e)
        self.line=[point(0,0,0),point(50,0,50),point(100,0,100)]
        self.e['_segment_points']=lambda *a:self.line
    def ref(self,i,k):return self.e['AdminGvpAnchor'](asset_id=ASSET,asset_version=1,point_index=i,km_meter=k)
    def resolver(self):
        self.e['_gvp_resolve']=lambda c,r:{**r.model_dump(mode='json'),'x_dbref':20 if r.point_index==0 else 80,'y_dbref':5,'point_name':str(r.point_index),'sha256':'original-hash'}
    def test_lot_is_perpendicular_and_stationed(self):
        foot=self.e['_gvp_projection'](point(35,8,0),self.line)
        self.assertEqual(foot,dict(station_m=35,x_dbref=35,y_dbref=0,distance_m=8))
    def test_no_endpoint_clamping_or_far_points(self):
        for p in [point(-5,2,0),point(105,2,0),point(50,101,0)]:
            with self.assertRaisesRegex(km.HTTPError,'SURVEY_GVP_NO_LOT'):self.e['_gvp_projection'](p,self.line)
    def test_shared_vertex_is_not_ambiguous(self):
        self.assertEqual(self.e['_gvp_projection'](point(50,10,0),self.line)['station_m'],50)
    def test_two_branches_are_ambiguous(self):
        line=[point(0,0,0),point(100,0,100),point(100,10,110),point(0,10,210)]
        with self.assertRaisesRegex(km.HTTPError,'SURVEY_GVP_AMBIGUOUS'):self.e['_gvp_projection'](point(50,5,0),line)
    def test_reverse_selection_and_falling_km(self):
        self.resolver();values,anchors=self.e['_gvp_calibration'](None,[self.ref(1,16000),self.ref(0,16060)],self.line)
        self.assertEqual(values,dict(station_from_m=20,station_to_m=80,km_from_m=16060,km_to_m=16000))
        self.assertEqual(anchors[0]['point_index'],0)
    def test_duplicate_and_implausible_anchors(self):
        self.resolver()
        for refs in [[self.ref(0,0),self.ref(0,60)],[self.ref(0,0),self.ref(1,1)],[self.ref(0,0),self.ref(1,91)]]:
            with self.assertRaises(km.HTTPError):self.e['_gvp_calibration'](None,refs,self.line)
    def test_save_provenance_and_tamper_protection(self):
        self.resolver();refs=[self.ref(0,16020),self.ref(1,16080)]
        p=km.payload(self.e,station_from_m=20,station_to_m=80,km_from_m=16020,km_to_m=16080,gvp_anchors=refs)
        r=self.e['management_preview_km'](km.AXIS,p,km.USER)
        self.assertEqual(r['gvp_anchors'][0]['foot']['station_m'],20)
        self.assertEqual(len(self.store.rows),1)
        bad=p.model_copy(update={'station_from_m':21})
        with self.assertRaisesRegex(km.HTTPError,'SURVEY_GVP_CALCULATION_CHANGED'):self.e['management_save_km'](km.AXIS,bad,km.USER)
        self.e['management_save_km'](km.AXIS,p,km.USER)
        self.assertEqual(self.store.rows[-1]['metadata']['gvp_anchors'][0]['sha256'],'original-hash')
        self.assertEqual(self.store.rows[0],km.BASE)
    def test_resolve_versions_global_scope_and_original_reference(self):
        class Cur:
            def execute(c,sql,params):c.sql=sql;c.params=params
            def fetchone(c):return dict(id=ASSET,current_version=1,original_id=km.ORIGINAL_ID,route_number='5302',original_name='a.KF',sha256='hash',payload={'points':[dict(point_name='GVP1',x_dbref=20,y_dbref=5,km_meter=16020)]})
        cur=Cur();r=self.e['_gvp_resolve'](cur,self.ref(0,16021))
        self.assertEqual(r['original_km_meter'],16020);self.assertEqual(r['km_meter'],16021)
        self.assertIn("x.scope='GLOBAL'",cur.sql);self.assertIn('company_id IS NULL',cur.sql)
        ref=self.ref(0,1).model_copy(update={'asset_version':2})
        with self.assertRaisesRegex(km.HTTPError,'SOURCE_CHANGED'):self.e['_gvp_resolve'](cur,ref)
    def test_list_bounded_and_read_only(self):
        store=self.store
        class Cur:
            def __enter__(c):return c
            def __exit__(c,*a):pass
            def execute(c,sql,params):
                self.assertTrue(sql.lstrip().startswith('SELECT'));c.sql=sql
            def fetchone(c):return store.axis if 'survey_catalog_axes' in c.sql else km.BASE
            def fetchall(c):return [dict(id=ASSET,current_version=1,route_number=None,original_name='a.KF',payload={'points':[dict(x_dbref=20,y_dbref=5,point_name='p')]})]*101
        store.cursor=lambda:Cur()
        r=self.e['management_gvp_points'](km.AXIS,km.SOURCE_ID)
        self.assertTrue(r['truncated']);self.assertEqual(len(r['points']),100)
        self.assertEqual(r['geometry'][0]['station_m'],0)
    def test_admin_csrf_guards(self):
        for name,guard in [('management_gvp_points','require_admin'),('management_gvp_calculate','require_admin_csrf')]:
            n=next(n for n in km.TREE.body if isinstance(n,ast.FunctionDef) and n.name==name)
            self.assertIn(guard,ast.unparse(n.args))

if __name__=='__main__':unittest.main()
