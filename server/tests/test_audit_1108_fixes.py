"""Real production functions; SQL boundary doubles, real temporary PDF files."""
import ast
import contextlib
import hashlib
import json
import logging
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1] / 'tbserver'
sys.path.insert(0, str(ROOT.parent))
from fastapi import HTTPException
from tbserver.permissions import require_permission
from tbserver.track_visibility import track_data_visible


def definitions(file, names, env):
    nodes = [n for n in ast.parse((ROOT/file).read_text()).body
             if isinstance(n, ast.FunctionDef) and n.name in names]
    for n in nodes:
        n.decorator_list = []
    module = ast.Module(body=[ast.ImportFrom(module='__future__', names=[ast.alias(name='annotations')], level=0), *nodes], type_ignores=[])
    exec(compile(ast.fix_missing_locations(module), str(ROOT/file), 'exec'), env)
    return env


class TrackAccessTests(unittest.TestCase):
    def setUp(self):
        self.context = dict(effective_permissions={'worksite.view': True}, effective_area_scope={'km_from_m': 0, 'km_to_m': 100})
        self.row = dict(id='dataset', worksite_id='site', dataset_kind='AXIS', payload={'samples': [{'km_meter': 0}, {'km_meter': 100}]}, deleted_at=None)

    def test_inside_including_zero(self):
        self.assertTrue(track_data_visible(self.context, self.row))

    def test_overlap_is_not_full_authorization(self):
        self.row['payload']['samples'][1]['km_meter'] = 101
        self.assertFalse(track_data_visible(self.context, self.row))

    def test_unknown_km_fails_closed(self):
        self.row['payload']['samples'][0]['km_meter'] = None
        self.assertFalse(track_data_visible(self.context, self.row))

    def test_nonfinite_km_fails_closed(self):
        self.row['payload']['samples'][0]['km_meter'] = float('nan')
        self.assertFalse(track_data_visible(self.context, self.row))

    def test_full_scope_preserves_unmapped_axes(self):
        self.context['effective_area_scope'] = {'all': True}
        self.row['payload']['samples'][0]['km_meter'] = None
        self.assertTrue(track_data_visible(self.context, self.row))

    def test_points_use_kilometers_not_meters(self):
        self.row.update(dataset_kind='STRECKENPUNKTE', payload={'points': [{'km': 0.2}]})
        self.assertFalse(track_data_visible(self.context, self.row))

    def test_permission_denied_and_scope_filtered_in_real_handlers(self):
        test = self
        class Cursor:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, *args): pass
            def fetchall(self): return [test.row]
            def fetchone(self): return test.row
        class Conn:
            def cursor(self): return Cursor()
        env = dict(transaction=lambda: contextlib.nullcontext(Conn()),
                   worksite_context=lambda *args: self.context, require_permission=require_permission,
                   track_data_visible=track_data_visible, HTTPException=HTTPException,
                   Depends=lambda value: None, current_portal_user=None,
                   Query=lambda **kwargs: kwargs.get('default'))
        definitions('track_data.py', {'list_track_data', 'get_track_data', '_load_dataset'}, env)
        self.assertEqual(len(env['list_track_data']('site', {})['track_data']), 1)
        self.context['effective_permissions']['worksite.view'] = False
        for f, args in [(env['list_track_data'], ('site', {})), (env['get_track_data'], ('dataset', {}))]:
            with self.assertRaises(HTTPException) as error: f(*args)
            self.assertEqual(error.exception.status_code, 403)
        self.context['effective_permissions']['worksite.view'] = True
        self.row['payload']['samples'][1]['km_meter'] = 1000
        self.assertEqual(env['list_track_data']('site', {})['track_data'], [])
        with self.assertRaises(HTTPException) as error: env['get_track_data']('dataset', {})
        self.assertEqual(error.exception.status_code, 404)
        definitions('survey_sections.py', {'list_survey_sections', 'get_survey_section'}, env)
        env.update(SECTION_KIND='SURVEY_SECTION', _section_response=lambda cur, row: row)
        self.row.update(dataset_kind='SURVEY_SECTION', payload={'calibration': {'km_at_start_m': 0, 'km_at_end_m': 1000}})
        self.assertEqual(env['list_survey_sections']('site', 200, {})['survey_sections'], [])
        with self.assertRaises(HTTPException): env['get_survey_section']('dataset', {})


class FinalizeTests(unittest.TestCase):
    def run_case(self, *, previous, fail_commit=False, fail_cleanup=False, replay=False, automatic=False):
        with tempfile.TemporaryDirectory() as directory:
            final, temp, backup = [Path(directory)/name for name in ('final.pdf', 'new.tmp', 'backup')]
            if previous: final.write_bytes(b'PREVIOUS')
            state = dict(committed=False, status='ACTIVE')
            btb = dict(id='b', worksite_id='w', team_id='t', status='ACTIVE', version=1, started_at=datetime.now(timezone.utc)-timedelta(hours=17))
            class Cursor:
                def __enter__(self): return self
                def __exit__(self, *args): pass
                def execute(self, sql, args):
                    if sql.startswith("UPDATE tb.btbs SET status = 'FINALIZED'"): state['status']='FINALIZED'
                def fetchone(self): return dict(btb, status=state['status'], version=2)
            class Conn:
                def cursor(self): return Cursor()
            @contextlib.contextmanager
            def transaction():
                yield Conn()
                if fail_commit:
                    state['status'] = 'ACTIVE'
                    raise OSError('commit failed')
                state['committed'] = True
            noop = lambda *args, **kwargs: None
            def upsert(*args, **kwargs):
                state['hash'] = kwargs['sha256']
                return {'id':'pdf'}, 'UPDATE'
            env = dict(datetime=datetime,timedelta=timedelta,timezone=timezone,Path=Path, os=os, hashlib=hashlib, json=json, perf_counter=time.perf_counter,
                Depends=lambda value:None, require_csrf=None, mutation_context=None, transaction=transaction,
                begin_mutation=lambda *a, **k: {'receipt': True} if replay else None, replay_payload=lambda value:value,
                _load_btb=lambda *a, **k:btb, worksite_context=lambda *a:{'company_id':'c'}, require_permission=noop,
                _assert_team_member=noop, require_team_lead=noop, _team=lambda *a:{'active_btb_id':'b'}, mark_lead_finished=noop,
                unfinished_participants=lambda *a, **k:[], load_btb_photo_files=lambda *a:[], _snapshot=lambda *a, **k:{'btb':{key:(value.isoformat() if isinstance(value,datetime) else value) for key,value in btb.items()}},
                load_original_snapshot=lambda *a:None, final_pdf_paths=lambda **k:(final,temp,backup), _pdf_context=lambda *a:{},
                photo_files_for_render=lambda *a:[], render_btb_pdf=lambda path, **k:path.write_bytes(b'NEW'),
                upsert_final_pdf_file=upsert, Jsonb=lambda x:x, emit_change=noop, audit=noop, PDF_RENDERER_VERSION='test',
                public_final_pdf=lambda *a, **k:{}, finish_mutation=noop, logger=logging.getLogger('test'))
            definitions('btb_documents.py', {'install_pdf_atomically','rollback_pdf_install','finish_pdf_install','hash_file'}, env)
            definitions('btb.py', {'finalize_btb', '_finalize_btb'}, env)
            unlink = Path.unlink
            def injected(path, *args, **kwargs):
                if path == backup and state['committed'] and fail_cleanup:
                    raise OSError('cleanup failed')
                return unlink(path, *args, **kwargs)
            with patch.object(Path, 'unlink', injected):
                if fail_commit:
                    with self.assertRaises(OSError): env['finalize_btb']('b', None, {'user_id':'lead'}, None)
                    self.assertEqual(final.read_bytes() if final.exists() else None, b'PREVIOUS' if previous else None)
                else:
                    response = env['_finalize_btb']('b', None, {'user_id':'lead'}, None, automatic=automatic)
                    if replay:
                        self.assertEqual(response, {'receipt': True})
                        self.assertEqual(final.read_bytes(), b'PREVIOUS')
                    else:
                        self.assertEqual(response['status'], 'FINALIZED')
                        self.assertEqual(final.read_bytes(), b'NEW')
                        self.assertEqual(state['hash'], hashlib.sha256(final.read_bytes()).hexdigest())
            self.assertFalse(temp.exists())

    def test_due_btb_creates_real_final_pdf(self): self.run_case(previous=False, automatic=True)
    def test_new_finalize(self): self.run_case(previous=False)
    def test_rework_finalize(self): self.run_case(previous=True)
    def test_cleanup_failure_preserves_committed_new_pdf(self): self.run_case(previous=False, fail_cleanup=True)
    def test_cleanup_failure_preserves_committed_rework_pdf(self): self.run_case(previous=True, fail_cleanup=True)
    def test_commit_failure_restores_previous(self): self.run_case(previous=True, fail_commit=True)
    def test_commit_failure_removes_uncommitted_new(self): self.run_case(previous=False, fail_commit=True)
    def test_receipt_replay_does_not_touch_files(self): self.run_case(previous=True, replay=True)


class DeadlineTests(unittest.TestCase):
    def test_under_lock_rechecks_start_and_status_before_any_write(self):
        from datetime import datetime, timedelta, timezone
        for status, hours, expected_skip in [('ACTIVE', 15, True), ('REWORK', 20, True), ('FINALIZED', 20, True)]:
            writes = []
            class Cursor:
                def __enter__(self): return self
                def __exit__(self, *args): pass
                def execute(self, sql, args): writes.append(sql)
            class Conn:
                def cursor(self): return Cursor()
            env = dict(Depends=lambda x: None, require_csrf=None, mutation_context=None,
                transaction=lambda: contextlib.nullcontext(Conn()), begin_mutation=lambda *a, **k:None,
                _load_btb=lambda *a, **k: {'status':status,'started_at':datetime.now(timezone.utc)-timedelta(hours=hours)},
                perf_counter=time.perf_counter, datetime=datetime,timedelta=timedelta,timezone=timezone)
            definitions('btb.py', {'_finalize_btb'}, env)
            self.assertEqual(env['_finalize_btb']('b',None,{},None,automatic=True), {'skipped': True})
            self.assertEqual(writes, [])


class SourceResolutionTests(unittest.TestCase):
    def test_resolve_requires_exactly_one_source(self):
        rows = []
        class Cursor:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, sql, args):
                self.assert_sql = sql
                assert args == ('a'*24+'%',)
            def fetchall(self): return rows
        class Conn:
            def cursor(self): return Cursor()
        env=dict(transaction=lambda:contextlib.nullcontext(Conn()),Query=lambda **k:None,
            Depends=lambda x:None,current_portal_user=None,HTTPException=HTTPException,
            _catalog_response=lambda user,data:data)
        definitions('survey_catalog.py', {'shared_km_resolve'}, env)
        for count, code in [(0,404),(2,409)]:
            rows[:] = [{'axis_id':str(i)} for i in range(count)]
            with self.assertRaises(HTTPException) as error: env['shared_km_resolve']('a'*24,{})
            self.assertEqual(error.exception.status_code,code)
        rows[:] = [{'axis_id':'only'}]
        self.assertEqual(env['shared_km_resolve']('a'*24,{}),rows[0])


if __name__ == '__main__':
    unittest.main()
