"""Regression of production write functions using cursor doubles, not HTTP/DB E2E."""
import ast
from copy import deepcopy
from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
from uuid import UUID, uuid4
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'tbserver/survey_catalog.py'
TREE = ast.parse(SOURCE.read_text())
USER = {'user_id': uuid4()}

class Cursor:
    def __init__(self): self.sql = []
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def cursor(self): return self
    def execute(self, sql, params=None): self.sql.append((sql, params))
    def fetchone(self): return None

class Harness:
    def __init__(self, kind='MANUAL_TRA_SECTION', relation='OVERLAP_CONFIRMED', existing_kind='ORIGINAL_TRA'):
        self.cur = Cursor()
        self.axis = {'id': uuid4(), 'status': 'ACTIVE'}
        self.existing = {'id': uuid4(), 'source_kind': existing_kind, 'segment_status': 'ACTIVE'}
        self.inserted = []; self.marks = []; self.receipts = []; self.audits = []; self.histories = []
        self.replay = None
        self.points = [{'station_m': n, 'x_dbref': 4400000+n, 'y_dbref': 5300000} for n in (0, 10, 20)]
        @contextmanager
        def transaction(): yield self.cur
        def insert(cur, **kw):
            s = {**kw, 'id': kw.get('segment_id') or uuid4(), 'current_version': 1,
                 'segment_status': 'REVIEW' if kw['relation'] == 'REVIEW_REQUIRED' else 'ACTIVE'}
            self.inserted.append(s)
            return s
        def mark(cur, segment, status, actor):
            self.marks.append((segment['id'], status))
            segment.update(segment_status=status, current_version=segment.get('current_version', 1)+1)
            return segment
        self.env = dict(Any=object, UUID=UUID, ManualSegmentCreate=object,
            Depends=lambda x: None, require_csrf=lambda: None, uuid4=uuid4,
            transaction=transaction, Jsonb=lambda x:x,
            _normalize_scope=lambda *a: ('GLOBAL', None), _manual_fields=lambda p: {},
            _receipt=lambda *a:self.replay, _manual_points=lambda *a:(self.points, 'original', 'signature'),
            _choose_axis_and_relation=lambda *a,**kw:(self.axis,relation,{},self.existing),
            _segment_insert=insert, _mark_segment_status=mark,
            _axis_review=lambda *a:self.axis, _conflict_create=lambda *a,**k:{'id':uuid4()},
            _axis_extent_refresh=lambda *a:None, _save_receipt=lambda *a:self.receipts.append(a[-1]),
            audit=lambda *a,**kw:self.audits.append(kw),
            _load_original_with_parse=lambda *a:({'sha256':'abc'}, {'status':'PARSED','detected_format':'TRA_ER0_BINARY','payload':{}}),
            _asset_upsert=lambda *a:{'id':uuid4()}, _axis_fields=lambda *a:{},
            _dense_payload=lambda *a:self.points, _scope_for_original=lambda *a:('GLOBAL',None),
            _station_extent=lambda *a:(0,20), _km_extent=lambda *a:(None,None),
            _source_date=lambda *a:None, _segment_points=lambda *a:self.points,
            _geometry_fully_covers=lambda *a:(True,{}), _source_is_newer=lambda *a:True)
        names = {'create_manual_segment','index_original_in_tx'}
        nodes = deepcopy([n for n in TREE.body if isinstance(n,ast.FunctionDef) and n.name in names])
        for n in nodes: n.decorator_list=[]
        exec(compile(ast.Module(body=nodes,type_ignores=[]), str(SOURCE), 'exec'), self.env)
        self.payload = SimpleNamespace(source_kind=kind, scope='GLOBAL', client_change_id='change-1', id=None,
            station_from_m=0, station_to_m=20, km_from_m=1000, km_to_m=1020, metadata={'origin':'ANDROID'})
    def create(self): return self.env['create_manual_segment'](self.payload, USER)
    def index(self): return self.env['index_original_in_tx'](self.cur, uuid4(), USER['user_id'])

class OverlayTests(unittest.TestCase):
    def test_tra_overlay_remains_active_with_km_and_source(self):
        h=Harness(); r=h.create(); s=h.inserted[0]
        self.assertEqual(s['segment_status'],'ACTIVE'); self.assertEqual(r['status'],'MERGED')
        self.assertEqual(s['km_from_m'],1000); self.assertEqual(s['km_to_m'],1020)
        self.assertEqual(s['source_original_id'],'original'); self.assertEqual(s['metadata'],{'origin':'ANDROID'})
        self.assertEqual(h.marks,[]); self.assertEqual(h.existing['segment_status'],'ACTIVE')
        self.assertEqual(h.receipts,[r]); self.assertEqual(len(h.audits),1)
    def test_replay_does_not_insert_or_audit_again(self):
        h=Harness(); r=h.create(); h.replay=r
        self.assertEqual(h.create(),r); self.assertEqual(len(h.inserted),1); self.assertEqual(len(h.audits),1)
    def test_review_is_not_activated(self):
        h=Harness(relation='REVIEW_REQUIRED'); r=h.create()
        self.assertEqual(h.inserted[0]['segment_status'],'REVIEW'); self.assertEqual(r['status'],'REVIEW_REQUIRED')
        self.assertIsNotNone(r['conflict_id']); self.assertEqual(h.marks,[])
    def test_gps_priority_unchanged(self):
        h=Harness(kind='MANUAL_GPS'); h.create()
        self.assertEqual(h.inserted[0]['segment_status'],'SUPERSEDED')
    def test_primary_and_endpoint_still_active(self):
        for relation in ['PRIMARY','ENDPOINT_CONFIRMED']:
            with self.subTest(relation=relation):
                h=Harness(relation=relation); h.create()
                self.assertEqual(h.inserted[0]['segment_status'],'ACTIVE')
    def test_later_original_preserves_tra_km_overlay(self):
        h=Harness(existing_kind='MANUAL_TRA_SECTION'); h.index()
        self.assertEqual(h.existing['segment_status'],'ACTIVE'); self.assertEqual(h.marks,[])
    def test_later_original_still_replaces_fully_covered_gps(self):
        h=Harness(existing_kind='MANUAL_GPS'); h.index()
        self.assertEqual(h.marks,[(h.existing['id'],'SUPERSEDED')])
    def test_newer_original_priority_unchanged(self):
        h=Harness(); h.index()
        self.assertEqual(h.marks,[(h.existing['id'],'SUPERSEDED')])


# Coverage tests run via unittest discovery as well as direct invocation below.
class SummaryTests(unittest.TestCase):
    def summary(self, intervals, base=True):
        original={'id':'original','source_kind':'ORIGINAL_TRA','source_original_id':'o',
                  'station_from_m':0,'station_to_m':1000,'quality':'FEHLT'}
        segments=([original] if base else [])+[
            dict(id=str(i),source_kind='MANUAL_TRA_SECTION',source_original_id='o',
                 station_from_m=a,station_to_m=b,quality=q,metadata={'origin':'ANDROID'})
            for i,(a,b,q) in enumerate(intervals)]
        class Cur(Cursor):
            def execute(self,sql,params=None): self.axes='SELECT a.*' in sql
            def fetchall(self): return [{'id':'a','axis_type':'GLEIS'}] if self.axes else segments
        cur=Cur()
        @contextmanager
        def transaction(): yield cur
        names={'_coverage_summary_data','_coverage_interval','_claim_coverage_interval'}
        nodes=deepcopy([n for n in TREE.body if isinstance(n,ast.FunctionDef) and n.name in names])
        env={'Any':object,'transaction':transaction,'_num':lambda v:float(v) if v is not None else None,
             '_clean':lambda v:v,'_axis_km_context':lambda *a:{},
             '_segment_physical_length_m':lambda c,s:abs(s['station_to_m']-s['station_from_m']),
             '_segment_km_quality':lambda c,a,s,ctx:{'status':s['quality']}}
        exec(compile(ast.Module(body=nodes,type_ignores=[]),str(SOURCE),'exec'),env)
        return env['_coverage_summary_data']()
    def test_overlap_union_and_mobile_count(self):
        r=self.summary([(0,100,'MANUELL'),(50,150,'MANUELL')])
        self.assertEqual(r['manual_km_length_m'],150); self.assertEqual(r['manual_mobile_length_m'],150)
        self.assertEqual(r['total_length_m'],1000); self.assertEqual(r['without_km_length_m'],850)
    def test_duplicate_nested_and_reversed_intervals(self):
        r=self.summary([(0,100,'MANUELL'),(0,100,'MANUELL'),(80,20,'MANUELL')])
        self.assertEqual(r['manual_km_length_m'],100)
    def test_disjoint_intervals(self):
        r=self.summary([(0,100,'MANUELL'),(200,300,'MANUELL')])
        self.assertEqual(r['manual_km_length_m'],200)
    def test_clipped_and_outside_intervals(self):
        r=self.summary([(-50,50,'MANUELL'),(950,1050,'MANUELL'),(1100,1200,'MANUELL')])
        self.assertEqual(r['manual_km_length_m'],100)
    def test_missing_km_does_not_become_manual(self):
        r=self.summary([(0,100,'FEHLT')])
        self.assertEqual(r['manual_km_length_m'],0); self.assertEqual(r['without_km_length_m'],1000)
        self.assertEqual(r['manual_mobile_length_m'],0)
    def test_unmatched_missing_km_remains_missing(self):
        r=self.summary([(0,100,'FEHLT')],base=False)
        self.assertEqual(r['total_length_m'],100); self.assertEqual(r['without_km_length_m'],100)
        self.assertEqual(r['manual_km_length_m'],0)
    def test_untouched_original_total(self):
        r=self.summary([])
        self.assertEqual(r['total_length_m'],1000); self.assertEqual(r['manual_km_length_m'],0)

class GraContextTests(unittest.TestCase):
    def test_km_overlay_does_not_disable_single_original_gra(self):
        class Cur(Cursor):
            def execute(self,sql,params=None): self.last=sql
            def fetchall(self): return []
            def fetchone(self):
                # One original and one KM overlay: only original carries geometry.
                count=1 if "source_kind <> 'MANUAL_TRA_SECTION'" in self.last else 2
                return {'item_count':count}
        names={'_axis_km_context','_segment_km_quality','_num'}
        nodes=deepcopy([n for n in TREE.body if isinstance(n,ast.FunctionDef) and n.name in names])
        import math
        env=dict(Any=object,math=math,_clean=lambda v:v,_SCOPE_COMPANY='COMPANY',
                 _GVP_DATA_KINDS=set(),_MANUAL_KINDS={'MANUAL_TRA_SECTION','MANUAL_GPS'},
                 GVP_MIN_ROBUST_MATCHES=4)
        exec(compile(ast.Module(body=nodes,type_ignores=[]),str(SOURCE),'exec'),env)
        cur=Cur(); axis={'id':'a','scope':'GLOBAL','route_number':'5302'}
        context=env['_axis_km_context'](cur,axis)
        context['explicit_gra']=[{'km_from_m':1000,'km_to_m':2000}]
        result=env['_segment_km_quality'](cur,axis,{'source_kind':'ORIGINAL_TRA'},context)
        self.assertEqual(result['status'],'AUTOMATISCH')
        self.assertEqual(result['source'],'GRA_KM')

if __name__ == '__main__': unittest.main()
