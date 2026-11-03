"""Production emitter/tombstone checks; DB/auth doubles, not PostgreSQL/HTTP."""
import ast
import threading
import unittest
from pathlib import Path
from types import SimpleNamespace
from typing import Any
from uuid import UUID, uuid4

ROOT = Path(__file__).resolve().parents[1]
def production(path, names, env):
    tree = ast.parse((ROOT / path).read_text())
    tree.body = [n for n in tree.body if
                 isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in names
                 or isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'CHANGE_FEED_PUBLISH_LOCK' for t in n.targets)]
    for n in tree.body:
        if hasattr(n, 'decorator_list'): n.decorator_list = []
    exec(compile(tree, str(path), 'exec'), env)
    return env
class Denied(Exception):
    def __init__(self, status_code, detail):
        self.status_code, self.detail = status_code, detail

class FeedPublication(unittest.TestCase):
    def emitter(self):
        return production('tbserver/events.py', {'emit_change'}, dict(Any=Any, UUID=UUID))['emit_change']
    def test_autocommit_is_rejected_before_any_write(self):
        class Cur:
            connection = SimpleNamespace(autocommit=True)
            def execute(self, *a): raise AssertionError("SQL must not run")
        with self.assertRaises(RuntimeError): self.emit(Cur())
    def emit(self, cur):
        return self.emitter()(cur, company_id=uuid4(), worksite_id=uuid4(),
                              entity_type='BTB', entity_id=uuid4(), operation='UPDATE',
                              entity_version=1, actor_user_id=uuid4())
    def test_publish_lock_precedes_id_and_is_held_until_commit_or_rollback(self):
        for rollback in (False, True):
            lock = threading.Lock()
            state = {'sequence': 0, 'committed': [], 'calls': []}
            waiting, allocated = threading.Event(), threading.Event()
            class Cur:
                connection = SimpleNamespace(autocommit=False)
                def __init__(self): self.held=False; self.row=None
                def execute(self, sql, params):
                    if 'pg_advisory_xact_lock' in sql:
                        waiting.set()
                        if not self.held:
                            lock.acquire(); self.held=True
                        state['calls'].append(('lock', params))
                    else:
                        self.assert_insert(sql)
                        state['sequence'] += 1
                        self.row = state['sequence']
                        allocated.set()
                def assert_insert(self, sql): assert 'INSERT INTO tb.change_feed' in sql
                def fetchone(self): return {'change_id': self.row}
                def end(self, commit):
                    if commit: state['committed'].append(self.row)
                    self.held=False; lock.release()
            a, b = Cur(), Cur()
            self.assertEqual(self.emit(a), 1)
            waiting.clear(); allocated.clear()
            errors=[]
            def second():
                try: self.emit(b); b.end(True)
                except BaseException as e: errors.append(e)
            thread=threading.Thread(target=second)
            thread.start()
            try:
                self.assertTrue(waiting.wait(1))
                self.assertFalse(allocated.wait(.03))
                self.assertEqual(state['sequence'], 1)
            finally:
                a.end(not rollback); thread.join(2)
            self.assertFalse(thread.is_alive())
            self.assertEqual(errors, [])
            self.assertEqual(state['committed'], [2] if rollback else [1,2])
    def test_all_publications_use_same_lock(self):
        calls=[]
        class Cur:
            connection = SimpleNamespace(autocommit=False)
            def execute(self, sql, params): calls.append((sql, params))
            def fetchone(self): return {'change_id': 12}
        self.emit(Cur()); self.emit(Cur())
        self.assertEqual(calls[0], calls[2])
        self.assertIn('pg_advisory_xact_lock', calls[0][0])
        self.assertIn('INSERT INTO tb.change_feed', calls[1][0])

class Tombstones(unittest.TestCase):
    def invoke(self, status='DELETED', deny=None, absent=False):
        calls=[]
        row={'id':uuid4(),'worksite_id':uuid4(),'team_id':uuid4(),'status':status,'version':9}
        class Tx:
            def __enter__(self): return self
            def __exit__(self,*a): pass
            def cursor(self): return self
            def execute(self,*a): pass
            def fetchone(self): return None if absent else row
        def guard(name):
            def run(*a):
                calls.append(name)
                if deny == name: raise Denied(403, name)
                return {}
            return run
        env=production('tbserver/btb.py', {'btb_sync_state'}, dict(UUID=UUID, Any=Any,
            Depends=lambda x:None, current_portal_user=None, transaction=Tx,
            worksite_context=guard('site'), require_permission=guard('permission'),
            _assert_team_member=guard('team'), HTTPException=Denied))
        return env['btb_sync_state'](row['id'], {}), calls
    def test_deleted_response_is_minimal_and_authorized(self):
        response, calls=self.invoke()
        self.assertEqual(set(response), {'id','version','deleted'})
        self.assertTrue(response['deleted']); self.assertEqual(calls,['site','permission','team'])
    def test_active_is_not_tombstone(self):
        response,_=self.invoke(status='ACTIVE'); self.assertFalse(response['deleted'])
    def test_missing_and_denied_never_confirm_deletion(self):
        with self.assertRaises(Denied) as c:self.invoke(absent=True)
        self.assertEqual(c.exception.status_code,404)
        for guard in ('site','permission','team'):
            with self.assertRaises(Denied) as c:self.invoke(deny=guard)
            self.assertEqual(c.exception.status_code,403)

if __name__=='__main__':unittest.main()

