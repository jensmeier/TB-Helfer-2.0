"""Focused contract tests on actual production functions with a read-only cursor.
No Postgres, HTTP server, or Synology runtime claim is made by this test.
"""
import ast
from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path
from uuid import UUID
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'tbserver' / 'survey_catalog.py'
TREE = ast.parse(SOURCE.read_text())
AXIS = UUID('00000000-0000-0000-0000-000000000001')


class HTTPError(Exception):
    def __init__(self, code, detail):
        self.status_code = code
        super().__init__(detail)


class Cursor:
    def __init__(self, results):
        self.results = list(results)
        self.statements = []
    def __enter__(self):
        return self
    def __exit__(self, *args):
        pass
    def execute(self, sql, params=None):
        self.statements.append((sql, params))
        assert sql.strip().startswith(('SELECT', 'WITH')), 'read-only endpoint'
        self.result = self.results.pop(0)
    def fetchone(self):
        return self.result
    def fetchall(self):
        return self.result
    def cursor(self):
        return self


def production_functions(cur, **overrides):
    @contextmanager
    def transaction():
        yield cur
    names = {'_plain', 'coverage_axis_details', 'coverage'}
    nodes = [n for n in TREE.body if isinstance(n, ast.FunctionDef) and n.name in names]
    for n in nodes:
        n.decorator_list = []
    module = ast.Module(body=nodes, type_ignores=[])
    env = dict(Any=object, UUID=UUID, date=date, datetime=datetime,
               Query=lambda default=None, **kw: default, Depends=lambda f: None,
               require_admin=lambda: None, transaction=transaction, HTTPException=HTTPError)
    env.update(overrides)
    exec(compile(module, str(SOURCE), 'exec'), env)
    return env


class CoverageDetailsTests(unittest.TestCase):
    def test_unknown_or_archived_axis_stops_before_related_data(self):
        cur = Cursor([None])
        f = production_functions(cur)['coverage_axis_details']
        with self.assertRaises(HTTPError) as e:
            f(AXIS)
        self.assertEqual(e.exception.status_code, 404)
        self.assertEqual(len(cur.statements), 1)
        self.assertIn("scope='GLOBAL'", cur.statements[0][0])
        self.assertIn("status <> 'ARCHIVED'", cur.statements[0][0])

    def test_history_paging_and_safe_field_diff(self):
        history = [dict(change_id=i, entity_type='AXIS', entity_id=AXIS,
                        entity_version=2, operation='UPDATE', changed_at=date(2026,10,2),
                        before_data={'display_name':'Old', 'geometry':[1,2]},
                        after_data={'display_name':'New', 'geometry':[4,5],
                                    'metadata':{'token':'must-not-leak'}})
                   for i in range(100,49,-1)]
        cur = Cursor([{'id':AXIS,'current_version':2},
                      [{'id':AXIS,'source_original_id':None,'original_name':None}], history, []])
        result = production_functions(cur)['coverage_axis_details'](AXIS, history_before=101)
        self.assertEqual(len(cur.statements), 4)
        self.assertEqual(len(result['history']), 50)
        self.assertEqual(result['history_next_before'], 51)
        self.assertEqual(result['history'][0]['fields'],
                         [{'field':'display_name','before':'Old','after':'New'}])
        self.assertNotIn('must-not-leak', str(result))
        self.assertNotIn('geometry', str(result))
        self.assertEqual(cur.statements[2][1], (AXIS,AXIS,AXIS,AXIS,101,101))
        self.assertIn('h.entity_type=e.kind AND h.entity_id=e.id', cur.statements[2][0])
        self.assertEqual(result['axis']['id'], str(AXIS))

    def test_empty_details_is_a_valid_terminal_page(self):
        cur = Cursor([{'id':AXIS}, [], [], []])
        result = production_functions(cur)['coverage_axis_details'](AXIS)
        self.assertEqual(result['segments'], [])
        self.assertEqual(result['history'], [])
        self.assertIsNone(result['history_next_before'])

    def test_metadata_endpoint_does_not_load_or_modify_original_files(self):
        n = next(n for n in TREE.body if isinstance(n, ast.FunctionDef) and n.name=='coverage_axis_details')
        calls = {n.func.id for n in ast.walk(n) if isinstance(n, ast.Call) and isinstance(n.func, ast.Name)}
        self.assertFalse(calls & {'_segment_points','_axis_km_context','_load_original_with_parse','open','audit','_history'})
        self.assertIn('require_admin', ast.unparse(n.args))

    def test_fast_shapes_skip_quality_and_preserve_legacy_default(self):
        axis={'id':AXIS,'display_name':'A','station_code':'S','station_name':'Station',
              'route_number':'42','axis_type':'GLEIS','active_segment_count':1,
              'seg_min_x':1,'seg_min_y':2,'seg_max_x':3,'seg_max_y':4}
        segment={'id':AXIS}
        for include_overview, include_quality, fast_overview, quality_calls in [
            (False,False,False,0),(True,False,False,1),(True,False,True,0),(True,True,True,1)
        ]:
            with self.subTest(flags=(include_overview,include_quality,fast_overview)):
                calls=[]
                cur=Cursor([[axis],[segment]])
                def quality(*args):
                    calls.append('quality')
                    return {'status':'AUTOMATISCH','color':'GRUEN','segments':[]}
                env=production_functions(cur,
                    _axis_km_summary=quality, _segment_points=lambda *a:[{'x_dbref':1,'y_dbref':2},{'x_dbref':3,'y_dbref':4}],
                    _decimate=lambda p,n:p, _num=lambda v:float(v) if v is not None else None,
                    _clean=lambda v:v, _postgis_transform_points=lambda cur,xy:xy,
                    _postgis_transform_bbox=lambda *a:{'min_lon':1,'max_lon':3,'min_lat':2,'max_lat':4})
                result=env['coverage'](include_overview,include_quality,fast_overview)
                self.assertEqual(len(calls),quality_calls)
                self.assertEqual(result['quality_included'],bool(quality_calls))
                self.assertEqual(len(result['axes'][0]['overview_segments']), int(include_overview))
                self.assertEqual(result['axes'][0]['km_quality']['status'],
                                 'AUTOMATISCH' if quality_calls else 'DETAIL_BEI_AUSWAHL')

if __name__=='__main__':
    unittest.main(verbosity=2)
