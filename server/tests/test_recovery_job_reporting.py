"""Focused regression tests without importing the database-bound application."""
import ast
import sqlite3
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import Mock
import json
import time
from urllib.error import HTTPError
from urllib.request import Request

ROOT = Path(__file__).resolve().parents[1]

def function(module, name, env):
    tree = ast.parse((ROOT / 'tbserver' / module).read_text())
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    exec(compile(ast.Module(body=[node], type_ignores=[]), module, 'exec'), env)
    return env[name]

class ReportingTests(unittest.TestCase):
    def test_cancelled_job_is_neutral_but_real_failure_stays_failed(self):
        view = function('system_admin.py', '_job_view', dict(Any=Any, _JOB_LABELS={}, _JOB_STATUS_LABELS={'FAILED':'Fehlgeschlagen'}, _friendly_error=lambda x:x))
        cancelled = view(dict(status='FAILED',job_type='BACKUP_FULL',last_error='CANCELLED_AFTER_RECOVERY'))
        self.assertEqual(cancelled['status'], 'FAILED')  # retain storage/API contract
        self.assertEqual(cancelled['status_label'], 'Durch Wiederherstellung beendet')
        self.assertIsNone(cancelled['error_description'])
        failed = view(dict(status='FAILED',job_type='BACKUP_FULL',last_error='DISK_FULL'))
        self.assertFalse(failed['cancelled_after_recovery'])
        self.assertEqual(failed['error_description'], 'DISK_FULL')

    def test_monitor_skips_only_expected_cancellation(self):
        tree=ast.parse((ROOT/'tbserver/monitor.py').read_text())
        node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='_check_backup')
        query=next(n.value for n in ast.walk(node) if isinstance(n,ast.Constant) and isinstance(n.value,str) and 'SELECT id, job_type' in n.value)
        db=sqlite3.connect(':memory:');db.execute("ATTACH DATABASE ':memory:' AS tb")
        db.execute('CREATE TABLE tb.background_jobs(id,job_type,status,updated_at,last_error)')
        db.executemany('INSERT INTO tb.background_jobs VALUES(?,?,?,?,?)',[(1,'BACKUP_FULL','FAILED',1,'DISK_FULL'),(2,'BACKUP_FULL','FAILED',2,'CANCELLED_AFTER_RECOVERY')])
        self.assertEqual(db.execute(query).fetchone()[0],1)
        db.execute("INSERT INTO tb.background_jobs VALUES(3,'BACKUP_FULL','FAILED',3,NULL)")
        self.assertEqual(db.execute(query).fetchone()[0],3)

    def test_timeout_phase_distinguishes_headers_and_body(self):
        opener=Mock(side_effect=TimeoutError('timed out'))
        check=function('monitor.py','_http_check',dict(Any=Any,time=time,Request=Request,urlopen=opener,HTTPError=HTTPError,json=json,API_WARN_MS=1500,_safe_message=str))
        self.assertEqual(check('https://example.test')['failure_phase'],'connect_or_headers')
        opener.side_effect=None
        opener.return_value.__enter__=Mock(return_value=Mock(read=Mock(side_effect=TimeoutError('timed out'))))
        opener.return_value.__exit__=Mock(return_value=False)
        self.assertEqual(check('https://example.test')['failure_phase'],'response_body')

if __name__=='__main__':unittest.main()
