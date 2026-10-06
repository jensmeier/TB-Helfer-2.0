"""Real archive/file checks with isolated production code; no live DB or NAS."""
import ast
import hashlib
import sys
import logging
import io
import tarfile
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tbserver.backup_inventory import InventoryError, archive_inventory


def load_module(name, extra):
    tree = ast.parse((ROOT / 'tbserver' / (name + '.py')).read_text())
    tree.body = [n for n in tree.body if not (
        isinstance(n, ast.ImportFrom) and (n.level or n.module.startswith('psycopg'))
        or isinstance(n, ast.Import) and any(a.name == 'psycopg' for a in n.names))]
    env = dict(extra, __name__='isolated_' + name)
    exec(compile(tree, name, 'exec'), env)
    return env


class BackupSafety(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.settings = SimpleNamespace(storage_root=root / 'storage', backup_root=root / 'backups')
        self.settings.storage_root.mkdir()
        self.settings.backup_root.mkdir()
        self.backup = load_module('backup', {'settings': self.settings, 'runtime_identity': lambda: {'format': 'test-provenance'}})
        self.backup.update(InventoryError=InventoryError, archive_inventory=archive_inventory)
        def snapshot(path):
            self.backup['_run_pg_dump'](path)
            return [dict(storage_key=str(p.relative_to(self.settings.storage_root)),
                         byte_size=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())
                    for p in self.settings.storage_root.rglob('*')
                    if p.is_file() and '.renditions' not in p.parts]
        self.backup['_capture_full_database'] = snapshot
        self.restore = load_module('restore', {
            'settings': self.settings, 'BackupError': self.backup['BackupError'],
            'backup_path_for_run': lambda _: None})
        self.failures = []
        self.backup['_begin_run'] = lambda _: uuid4()
        self.backup['_finish_run_failed'] = lambda *args: self.failures.append(args)
        self.backup['_finish_run_ok'] = lambda *args: {'status': 'OK', 'path': args[2]}

    def test_missing_root_fails_instead_of_empty_success(self):
        self.settings.storage_root.rmdir()
        with self.assertRaisesRegex(self.backup['BackupError'], 'ROOT_UNAVAILABLE'):
            self.backup['run_backup']('FILES')
        self.assertEqual(len(self.failures), 1)
        self.assertEqual(list(self.settings.backup_root.rglob('*.tar.gz')), [])

    def test_empty_existing_root_roundtrip(self):
        result = self.backup['run_backup']('FILES')
        counts = self.restore['_extract_storage_archive'](result['path'], Path(self.temp.name) / 'out')
        self.assertEqual(counts['file_count'], 0)

    def test_progress_failure_keeps_completed_backup(self):
        (self.settings.storage_root / 'original.tra').write_bytes(b'original-data')
        def broken(*args):
            raise ConnectionError('progress DB unavailable')
        with self.assertLogs('isolated_backup', level=logging.WARNING):
            result = self.backup['run_backup']('FILES', progress=broken)
        self.assertTrue(result['path'].is_file())
        self.assertEqual(self.failures, [])

    def test_registration_failure_keeps_artifact(self):
        def broken(*args):
            raise ConnectionError('registration failed')
        self.backup['_finish_run_ok'] = broken
        with self.assertRaises(ConnectionError):
            self.backup['run_backup']('FILES')
        self.assertEqual(len(list(self.settings.backup_root.rglob('*.tar.gz'))), 1)
        self.assertEqual(len(self.failures), 1)

    def test_symlink_rejected_and_temporary_removed(self):
        (self.settings.storage_root / 'link').symlink_to('/outside')
        with self.assertRaisesRegex(self.backup['BackupError'], 'ENTRY_UNSUPPORTED'):
            self.backup['run_backup']('FILES')
        self.assertEqual(list(self.settings.backup_root.rglob('*.tmp')), [])
        self.assertEqual(list(self.settings.backup_root.rglob('*.tar.gz')), [])

    def test_full_archive_roundtrip_excludes_only_renditions(self):
        root = self.settings.storage_root
        (root / 'original.tra').write_bytes(b'unchanged source')
        (root / '.renditions').mkdir()
        (root / '.renditions' / 'thumb.jpg').write_bytes(b'cache')
        self.backup['_run_pg_dump'] = lambda path: path.write_bytes(b'simulated DB dump')
        result = self.backup['run_backup']('FULL')
        dump, files, manifest = self.restore['_extract_full_backup'](result['path'], Path(self.temp.name) / 'full')
        self.assertEqual(dump.read_bytes(), b'simulated DB dump')
        target = Path(self.temp.name) / 'restored'
        counts = self.restore['_extract_storage_archive'](files, target)
        self.assertEqual(counts['file_count'], 1)
        self.assertEqual((target / 'storage' / 'original.tra').read_bytes(), b'unchanged source')
        self.assertFalse((target / 'storage' / '.renditions').exists())

    def test_restore_progress_failure_does_not_fail_restore(self):
        result = self.backup['run_backup']('FILES')
        self.restore['backup_path_for_run'] = lambda _: ({'kind': 'FILES'}, result['path'])
        self.restore['_begin_restore_test'] = lambda _: uuid4()
        finished = []
        self.restore['_finish_restore_test'] = lambda *a, **kw: finished.append(kw) or kw
        def broken(*args):
            raise ConnectionError('progress failed')
        with self.assertLogs('isolated_restore', level=logging.WARNING):
            out = self.restore['run_restore_test'](uuid4(), progress=broken)
        self.assertTrue(out['ok'])
        self.assertEqual(len(finished), 1)

    def test_restore_scratch_creation_failure_is_recorded(self):
        result = self.backup['run_backup']('FILES')
        self.restore['backup_path_for_run'] = lambda _: ({'kind': 'FILES'}, result['path'])
        self.restore['_begin_restore_test'] = lambda _: uuid4()
        finished = []
        self.restore['_finish_restore_test'] = lambda *a, **kw: finished.append(kw)
        (self.settings.backup_root / '.restore-tests').write_bytes(b'not a directory')
        with self.assertRaises(OSError):
            self.restore['run_restore_test'](uuid4())
        self.assertEqual(len(finished), 1)
        self.assertFalse(finished[0]['ok'])

    def test_restore_rejects_live_database_before_connect(self):
        self.settings.db_name = 'live'
        self.settings.db_host = 'db'
        self.settings.db_port = 5432
        self.settings.db_user = 'user'
        with patch.dict('os.environ', {'TB_RESTORE_TEST_DB_NAME': 'live',
                                      'TB_RESTORE_TEST_DB_PASSWORD': 'test-only'}, clear=True):
            with self.assertRaisesRegex(self.restore['RestoreTestError'], 'MUST_DIFFER_FROM_LIVE'):
                self.restore['_restore_db_config']()

    def test_restore_rejects_traversal_before_extract(self):
        archive = Path(self.temp.name) / 'bad.tar.gz'
        with tarfile.open(archive, 'w:gz') as out:
            entry = tarfile.TarInfo('storage/../../escaped')
            entry.size = 1
            out.addfile(entry, io.BytesIO(b'x'))
        with self.assertRaisesRegex(self.restore['RestoreTestError'], 'PATH_INVALID'):
            self.restore['_extract_storage_archive'](archive, Path(self.temp.name) / 'out')

    def test_full_checksum_mismatch_rejected(self):
        self.backup['_run_pg_dump'] = lambda path: path.write_bytes(b'dump')
        result = self.backup['run_backup']('FULL')
        damaged = Path(self.temp.name) / 'damaged.tar.gz'
        with tarfile.open(result['path'], 'r:gz') as src, tarfile.open(damaged, 'w:gz') as dst:
            for entry in src.getmembers():
                data = src.extractfile(entry).read()
                if entry.name == 'database.dump':
                    data = b'changed dump'
                    entry.size = len(data)
                dst.addfile(entry, io.BytesIO(data))
        with self.assertRaisesRegex(self.restore['RestoreTestError'], 'DATABASE_SHA256_MISMATCH'):
            self.restore['_extract_full_backup'](damaged, Path(self.temp.name) / 'out')


if __name__ == '__main__':
    unittest.main()

