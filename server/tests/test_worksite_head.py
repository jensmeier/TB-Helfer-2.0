"""Execute production handlers, permissions and receipt code against isolated SQLite.

This is a local handler/SQL regression, not PostgreSQL/HTTP or device E2E.
"""
import ast
import contextlib
import json
import sqlite3
import unittest
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import UUID
from pydantic import BaseModel, Field, ValidationError

ROOT = Path(__file__).resolve().parents[1]

class HTTPException(Exception):
    def __init__(self, status_code, detail):
        self.status_code, self.detail = status_code, detail
        super().__init__(str(detail))

def load_definitions(module, names, env):
    tree = ast.parse((ROOT / 'tbserver' / module).read_text())
    nodes = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.ClassDef)) and n.name in names]
    for node in nodes:
        node.decorator_list = [d for d in node.decorator_list if (isinstance(d, ast.Name) and d.id == 'dataclass') or (isinstance(d, ast.Call) and isinstance(d.func, ast.Name) and d.func.id == 'dataclass')]
    exec(compile(ast.Module(body=nodes, type_ignores=[]), module, 'exec'), env)

class Cursor:
    def __init__(self, db): self.db, self.cur = db, db.cursor()
    def __enter__(self): return self
    def __exit__(self, *args): self.cur.close()
    def execute(self, sql, args=()):
        if 'pg_advisory_xact_lock' in sql: return  # serial fixture; no concurrency claim
        sql = sql.replace('%s', '?').replace(' FOR UPDATE', '').replace('now()', 'CURRENT_TIMESTAMP')
        self.cur.execute(sql, tuple(str(a) if isinstance(a, UUID) else a for a in args))
    def fetchone(self): return self.cur.fetchone()
    def fetchall(self): return self.cur.fetchall()

class WorksiteTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        def row_factory(cursor, row):
            result = dict(zip([c[0] for c in cursor.description], row))
            for key in ('profile_permissions', 'permissions_override', 'area_scope', 'response_payload'):
                if isinstance(result.get(key), str): result[key] = json.loads(result[key])
            return result
        self.db.row_factory = row_factory
        self.db.execute("ATTACH DATABASE ':memory:' AS tb")
        self.db.executescript('''
            CREATE TABLE tb.worksites(id TEXT PRIMARY KEY,company_id TEXT,name TEXT,status TEXT,deleted_at TEXT,
                version INTEGER,designation TEXT,route_number TEXT,station_code TEXT,station_name TEXT,updated_at TEXT);
            CREATE TABLE tb.worksite_memberships(id TEXT,worksite_id TEXT,user_id TEXT,worksite_role TEXT,
                permission_profile TEXT,permissions_override TEXT,permissions_version INTEGER,area_scope TEXT,status TEXT);
            CREATE TABLE tb.permission_profiles(profile_key TEXT,permissions TEXT);
            CREATE TABLE tb.mutation_receipts(id INTEGER PRIMARY KEY,device_id TEXT,user_id TEXT,client_change_id TEXT,
                entity_type TEXT,entity_id TEXT,result_version INTEGER,response_payload TEXT,created_at TEXT);
        ''')
        self.db.execute("INSERT INTO tb.worksites VALUES('w','c','Alt','ACTIVE',NULL,3,NULL,NULL,NULL,NULL,NULL)")
        self.db.execute("INSERT INTO tb.permission_profiles VALUES('lead',?)", (json.dumps({'worksite.structure.edit': True}),))
        self.db.execute("INSERT INTO tb.worksite_memberships VALUES('m','w','u','RESPONSIBLE_TB','lead','{}',1,'{\"all\":true}','ACTIVE')")
        self.db.commit()
        self.events, self.audit = [], []
        test = self
        class Conn:
            def cursor(self): return Cursor(test.db)
        @contextlib.contextmanager
        def transaction():
            try:
                yield Conn(); test.db.commit()
            except Exception:
                test.db.rollback(); raise
        self.env = dict(Any=Any,UUID=UUID,HTTPException=HTTPException,dataclass=dataclass,
            BaseModel=BaseModel,Field=Field,Depends=lambda _:None,require_csrf=None,mutation_context=None,
            transaction=transaction,Jsonb=lambda x:json.dumps(x),jsonable_encoder=lambda x:x,
            emit_change=lambda cur,**kw:self.events.append(kw),audit=lambda cur,**kw:self.audit.append(kw),
            CHEF_DEFAULT_PERMISSIONS={'worksite.view':True})
        load_definitions('permissions.py', {'_merge_permissions','_scope_labels','_scope_number','normalized_area_scope',
            'worksite_context','has_permission','require_permission'}, self.env)
        import math
        self.env['math'] = math
        load_definitions('idempotency.py', {'MutationContext','begin_mutation','finish_mutation','replay_payload'}, self.env)
        load_definitions('worksites.py', {'WorksiteUpdate','update_worksite'}, self.env)
        self.user = dict(user_id='u', company_id='c', company_role='USER', device_id='d')
    def tearDown(self): self.db.close()
    def update(self, name='Neu', base=3, key='change-1', worksite='w'):
        return self.env['update_worksite'](worksite, self.env['WorksiteUpdate'](name=name,base_version=base),
            self.user, self.env['MutationContext'](key))
    def assert_error(self, status, call):
        with self.assertRaises(HTTPException) as e: call()
        self.assertEqual(status, e.exception.status_code)
    def test_updates_name_and_emits_exact_version(self):
        row = self.update('  Neu  ')
        self.assertEqual(('Neu',4),(row['name'],row['version']))
        self.assertEqual(('WORKSITE','UPDATE',4),(self.events[0]['entity_type'],self.events[0]['operation'],self.events[0]['entity_version']))
        self.assertEqual(1,len(self.audit))
    def test_response_lost_retry_is_replayed_without_duplicate_change(self):
        first = self.update()
        second = self.update()
        self.assertEqual(first, second)
        self.assertEqual(1,len(self.events))
        self.assertEqual(1,self.db.execute('SELECT count(*) AS n FROM tb.mutation_receipts').fetchone()['n'])
    def test_parallel_edit_old_basis_preserved(self):
        self.update('Portal')
        self.assert_error(409,lambda:self.update('Handy',key='different'))
        self.assertEqual('Portal',self.db.execute("SELECT name FROM tb.worksites WHERE id='w'").fetchone()['name'])
        self.assertEqual(1,len(self.events))
    def test_revoked_membership_cannot_replay_receipt(self):
        self.update()
        self.db.execute("UPDATE tb.worksite_memberships SET status='REMOVED'");self.db.commit()
        self.assert_error(403,self.update)
    def test_other_company_denied(self):
        self.user['company_id']='other';self.assert_error(403,self.update)
    def test_helper_without_edit_permission_denied(self):
        self.db.execute("UPDATE tb.permission_profiles SET permissions='{}'");self.db.commit()
        self.assert_error(403,self.update)
    def test_admin_read_only(self):
        self.user['company_role']='ADMIN';self.assert_error(403,self.update)
    def test_wrong_receipt_target_denied(self):
        self.update()
        self.db.execute("UPDATE tb.mutation_receipts SET entity_id='other'");self.db.commit()
        self.assert_error(409,self.update)
    def test_deleted_worksite_denied(self):
        self.db.execute("UPDATE tb.worksites SET deleted_at='today'");self.db.commit()
        self.assert_error(404,self.update)
    def test_whitespace_name_rejected(self): self.assert_error(400,lambda:self.update('   '))
    def test_version_required_and_positive(self):
        for kw in (dict(name='N'),dict(name='N',base_version=0),dict(name='',base_version=3),dict(name='x'*241,base_version=3)):
            with self.assertRaises(ValidationError): self.env['WorksiteUpdate'](**kw)
    def test_new_receipt_allows_next_edit_at_confirmed_basis(self):
        self.update('A');row=self.update('B',base=4,key='next')
        self.assertEqual(('B',5),(row['name'],row['version']))

    def test_unscoped_feed_filters_company_membership_and_types(self):
        self.db.execute('CREATE TABLE tb.change_feed(change_id INTEGER,company_id TEXT,worksite_id TEXT,entity_type TEXT,entity_id TEXT,operation TEXT,entity_version INTEGER,actor_user_id TEXT,created_at TEXT)')
        self.db.executemany('INSERT INTO tb.change_feed VALUES(?,?,?,?,?,?,?,?,?)', [
            (1,'c','w','WORKSITE','w','UPDATE',4,'u','today'),
            (2,'c','hidden','WORKSITE','hidden','CREATE',1,'other','today'),
            (3,'other','w','WORKSITE','w','UPDATE',5,'other','today'),
            (4,'c','w','FILE','photo','UPDATE',1,'u','today')])
        self.db.commit()
        self.env['_serialize_change'] = lambda row:row
        load_definitions('sync.py', {'_visible_changes','_type_clause'}, self.env)
        result = self.env['_visible_changes'](self.user,after=0,through=4,limit=200,worksite_id=None,entity_types=['WORKSITE'])
        self.assertEqual([1],[row['change_id'] for row in result])
        self.db.execute("UPDATE tb.worksite_memberships SET status='REMOVED'");self.db.commit()
        result = self.env['_visible_changes'](self.user,after=0,through=4,limit=200,worksite_id=None,entity_types=['WORKSITE'])
        self.assertEqual([],result)

    def test_scoped_feed_still_checks_worksite_access(self):
        self.env['_serialize_change'] = lambda row:row
        load_definitions('sync.py', {'_visible_changes','_type_clause'}, self.env)
        self.user['company_id']='other'
        self.assert_error(403,lambda:self.env['_visible_changes'](self.user,after=0,through=4,limit=200,worksite_id='w'))

if __name__=='__main__':unittest.main()
