"""Recovery policy/provenance regressions; real SQL/HTTP coverage in recovery_gate."""
import ast
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tbserver import recovery_guard as guard


def functions(module, names, env):
    tree = ast.parse((ROOT / 'tbserver' / (module + '.py')).read_text())
    tree.body = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.ClassDef)) and n.name in names]
    exec(compile(tree, module, 'exec'), env)
    return env


class RecoveryModes(unittest.TestCase):
    def test_runtime_metadata_exact_match_and_legacy_rejected(self):
        identity = {'format': 'tb-runtime-v1', 'server_version': 'same', 'source_sha256': 'a', 'migrations': [1]}
        env = functions('recovery_version', {'check_update_version'}, {'runtime_identity': lambda: identity})
        check = env['check_update_version']
        self.assertEqual(check({'runtime': dict(identity)}), identity)
        for manifest in ({}, {'runtime': None}):
            with self.assertRaisesRegex(ValueError, 'METADATA_MISSING'): check(manifest)
        for key, value in [('source_sha256', 'b'), ('migrations', [2]), ('server_version', 'other')]:
            with self.assertRaisesRegex(ValueError, 'VERSION_MISMATCH'):
                check({'runtime': dict(identity, **{key: value})})

    def test_fingerprint_changes_for_code_but_not_env_or_data(self):
        import hashlib
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root/'tbserver').mkdir()
            code = root/'tbserver'/'example.py'; code.write_text('one')
            env = functions('recovery_version', {'runtime_identity'}, {
                'Path': Path, 'hashlib': hashlib, 'SERVER_VERSION': 'test',
                '__file__': str(root/'tbserver'/'recovery_version.py'), 'discover_migrations': lambda: []})
            identify = env['runtime_identity']
            before = identify()
            (root/'.env').write_text('SECRET=not-in-manifest')
            self.assertEqual(before, identify())
            code.write_text('two')
            self.assertNotEqual(before['source_sha256'], identify()['source_sha256'])

    def test_epoch_requires_matching_header_only_after_cutover(self):
        try:
            from fastapi import HTTPException
        except ImportError:
            class HTTPException(Exception):
                def __init__(self, status_code, detail, headers):
                    self.status_code, self.detail, self.headers = status_code, detail, headers
        with patch.dict(sys.modules, {'fastapi': SimpleNamespace(HTTPException=HTTPException)}):
            with patch.dict(os.environ, {'TB_RECOVERY_ID': ''}):
                guard.require_recovery_epoch(SimpleNamespace(headers={}))
            epoch = str(uuid4())
            with patch.dict(os.environ, {'TB_RECOVERY_ID': epoch}):
                for headers in ({}, {'X-TB-Recovery-ID': str(uuid4())}):
                    with self.assertRaises(HTTPException) as caught:
                        guard.require_recovery_epoch(SimpleNamespace(headers=headers))
                    self.assertEqual(caught.exception.status_code, 409)
                    self.assertEqual(caught.exception.detail['recovery_id'], epoch)
                guard.require_recovery_epoch(SimpleNamespace(headers={'X-TB-Recovery-ID': epoch}))

    def test_cutover_rejects_mode_and_runtime_mismatch(self):
        class Cursor:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, *args): pass
            def fetchone(self): return {'value': state}
        epoch = str(uuid4())
        state = {'recovery_id': epoch, 'phase': 'READY', 'mode': 'UPDATE',
                 'source_sha256': 'archive', 'runtime': {'source_sha256': 'code'}}
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {'TB_RECOVERY_ID': epoch}):
            root = Path(temp); marker = root/guard.MARKER
            conn = SimpleNamespace(cursor=Cursor)
            marker.write_text(json.dumps(state)); guard.verify_cutover(conn, root)
            for changed in (dict(state, mode='SECURITY'), dict(state, runtime={})):
                marker.write_text(json.dumps(changed))
                with self.assertRaisesRegex(RuntimeError, 'MISMATCH'): guard.verify_cutover(conn, root)
            marker.write_text(json.dumps(state))


if __name__ == '__main__': unittest.main()
