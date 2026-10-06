"""255 coroutine clients through real sync hub/wait/semaphore, fake DB/auth.

This measures local coordination only. It is not a NAS or HTTP capacity claim.
"""
import ast
import asyncio
from collections import Counter
import importlib.util
import json
from pathlib import Path
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import time
from typing import Any
import unittest
from unittest.mock import patch
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import sync_mixed_loadtest as runner
import sync_wait_loadtest as legacy


class Denied(Exception): pass


class Coordination(unittest.IsolatedAsyncioTestCase):
    def environment(self):
        lock = threading.Lock()
        state = dict(head=0, polls=0, active=0, peak=0, checks=0, reads=0, revoked=set())
        def latest():
            with lock: state['polls'] += 1
            return state['head']
        def refresh(request, worksite):
            with lock:
                state['active'] += 1
                state['peak'] = max(state['peak'], state['active'])
                state['checks'] += 1
            try:
                time.sleep(.002)
                if request in state['revoked']: raise Denied()
                return {'user_id': request}
            finally:
                with lock: state['active'] -= 1
        def visible(user, **kwargs):
            with lock: state['reads'] += 1
            return [dict(change_id=1, entity_id=user['user_id'])]
        env = dict(asyncio=asyncio, time=time, Any=Any, UUID=UUID, Request=Any,
                   Depends=lambda x: None, Query=lambda **kw: kw.get('default'),
                   current_portal_user=None, _MAX_WAIT_SECONDS=30, _HUB_POLL_SECONDS=.01,
                   _SYNC_QUERY_SEMAPHORE=asyncio.Semaphore(16), _latest_change_id=latest,
                   _refresh_sync_user=refresh, _visible_changes=visible)
        tree = ast.parse((ROOT / 'tbserver/sync.py').read_text())
        tree.body = [n for n in tree.body if isinstance(n, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef))
                     and n.name in {'_ChangeHub', '_run_sync_query', '_entity_types', 'wait_changes'}]
        for node in tree.body: node.decorator_list = []
        exec(compile(tree, 'sync-coordination', 'exec'), env)
        hub = env['_ChangeHub']()
        env['_CHANGE_HUB'] = hub
        async def cleanup():
            if hub._task:
                hub._task.cancel()
                await asyncio.gather(hub._task, return_exceptions=True)
        self.addAsyncCleanup(cleanup)
        return env, state

    async def test_255_quiet_clients_share_poller_and_limit_auth_concurrency(self):
        env, state = self.environment()
        start = time.monotonic()
        results = await asyncio.gather(*(env['wait_changes'](i, 0, 50, None, None, .05, {}) for i in range(255)))
        self.assertEqual(len(results), 255)
        self.assertTrue(all(r['changes'] == [] and r['caught_up'] for r in results))
        self.assertEqual(state['checks'], 255)
        self.assertLessEqual(state['peak'], 16)
        self.assertLess(state['polls'], 100)  # no per-client database poller
        print(json.dumps(dict(scope='local-doubles-quiet', clients=255,
            wall_s=round(time.monotonic()-start, 3), peak_queries=state['peak'], hub_polls=state['polls'])))

    async def test_255_woken_clients_respect_revocation_and_individual_results(self):
        env, state = self.environment()
        tasks = [asyncio.create_task(env['wait_changes'](i, 0, 50, None, None, 1, {})) for i in range(255)]
        await asyncio.sleep(.03)
        state['revoked'] = set(range(15))
        state['head'] = 1
        results = await asyncio.gather(*tasks, return_exceptions=True)
        self.assertEqual(sum(isinstance(r, Denied) for r in results), 15)
        for i, result in enumerate(results[15:], 15):
            self.assertEqual(result['changes'], [{'change_id': 1, 'entity_id': i}])
            self.assertEqual(result['next_after'], 1)
        self.assertEqual(state['reads'], 240)
        self.assertLessEqual(state['peak'], 16)

    async def test_cancelled_waiter_does_not_stop_other_clients(self):
        env, state = self.environment()
        abandoned = asyncio.create_task(env['_CHANGE_HUB'].wait_for_head(0, 1))
        active = asyncio.create_task(env['_CHANGE_HUB'].wait_for_head(0, 1))
        await asyncio.sleep(.02)
        abandoned.cancel()
        await asyncio.gather(abandoned, return_exceptions=True)
        state['head'] = 7
        self.assertEqual(await active, 7)


class LoadRunner(unittest.TestCase):
    def test_bad_http_200_contract_counts_as_failure(self):
        good = dict(changes=[{'change_id': 4}], next_after=5, head=5, caught_up=True)
        self.assertEqual(runner.validate_payload(good, 3), 5)
        for update in ({'changes': None}, {'next_after': 2}, {'head': True},
                       {'changes': [{'change_id': 3}]}, {'caught_up': 'true'}, {'next_after': 4}):
            with self.assertRaises(ValueError): runner.validate_payload(dict(good, **update), 3)
        report = runner.summarize([dict(status=200, error='ValueError', seconds=.1)], 10, 5)
        self.assertFalse(report['passed'])
        self.assertEqual(report['failures'], 1)

    def test_manifest_rejects_shared_device_tokens(self):
        rows = [dict(role='TB' if i < 15 else 'HELPER', token_env=f'D{i}') for i in range(240)]
        rows += [dict(role='PORTAL', login=f'p{i}', password_env='P') for i in range(15)]
        values = {f'D{i}': f'test-{i}' for i in range(240)} | {'P': 'test-password'}
        with patch.dict('os.environ', values, clear=True):
            self.assertEqual(len(runner.validate_manifest(rows)), 255)
        values['D1'] = values['D0']
        with patch.dict('os.environ', values, clear=True), self.assertRaises(ValueError):
            runner.validate_manifest(rows)

    def test_p95_overhead_fails_gate(self):
        report = runner.summarize([dict(status=200, error='', seconds=16)] * 255, 10, 5)
        self.assertFalse(report['passed'])

    def test_real_http_transport_rejects_200_bad_payload_and_403(self):
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_GET(self):
                status = 403 if self.headers.get('Authorization') == 'Bearer denied' else 200
                good = self.headers.get('Authorization') == 'Bearer allowed'
                payload = dict(changes=[], next_after=7, head=7, caught_up=True) if good else {}
                self.send_response(status); self.end_headers()
                self.wfile.write(json.dumps(payload).encode())
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            base = f'http://127.0.0.1:{server.server_port}'
            for token, valid in [('allowed', True), ('bad-contract', False), ('denied', False)]:
                client = runner.Client('TB', {'Authorization': 'Bearer ' + token})
                result = runner.probe(base, client, 0, 2)
                self.assertEqual(not bool(result['error']), valid)
                self.assertEqual(client.after, 7 if valid else 0)
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_partial_portal_login_failure_cleans_created_sessions(self):
        session = legacy.PortalSession({}, 'test')
        with patch.object(legacy, '_portal_session', side_effect=[session, RuntimeError('failed')]), \
             patch.object(legacy, '_logout_portal_session', return_value=True) as logout:
            with self.assertRaises(RuntimeError):
                legacy._prepare_portal_sessions('http://test', 'test', 'test', 3, 1, 'unique')
            logout.assert_called_once_with('http://test', session, 1)


if __name__ == '__main__': unittest.main()
