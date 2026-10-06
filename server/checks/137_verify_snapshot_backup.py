"""Real files/archives and production snapshot orchestration with DB doubles."""
import hashlib
import importlib.util
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tbserver import backup_inventory as inv

spec = importlib.util.spec_from_file_location('backup_checks', ROOT / 'checks/131_verify_backup_failure_safety.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class Cursor:
    def __init__(self, rows): self.rows, self.calls, self.rowcount = rows, [], 2
    def execute(self, sql, *args): self.calls.append(sql)
    def fetchall(self): return self.rows
    def fetchone(self): return {'snapshot': 'snapshot-test'}
    def cursor(self): return self
    def __enter__(self): self.active = True; return self
    def __exit__(self, *args): self.active = False


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name) / 'storage'
        self.root.mkdir()
        self.target = Path(temp.name) / 'backup.tar.gz'
        self.entry = dict(storage_key='photo.jpg', byte_size=3,
                          sha256=hashlib.sha256(b'old').hexdigest())
        (self.root / 'photo.jpg').write_bytes(b'old')

    def test_duplicate_metadata_and_conflict(self):
        self.assertEqual(inv.read_inventory(Cursor([self.entry, self.entry])), [self.entry])
        changed = dict(self.entry, sha256='0' * 64)
        with self.assertRaisesRegex(inv.InventoryError, 'CONFLICT'):
            inv.read_inventory(Cursor([self.entry, changed]))

    def test_unsafe_or_unverifiable_reference_rejected(self):
        for key in ('../x', '/x', '.', 'a/../x', 'a//b', '.uploads/x', '.renditions/x', 'x\\y'):
            with self.subTest(key=key), self.assertRaises(inv.InventoryError):
                inv.read_inventory(Cursor([dict(self.entry, storage_key=key)]))
        for update in ({'byte_size': None}, {'byte_size': True}, {'sha256': None}, {'sha256': 'bad'}):
            with self.assertRaises(inv.InventoryError):
                inv.read_inventory(Cursor([dict(self.entry, **update)]))

    def test_new_files_after_snapshot_excluded(self):
        (self.root / 'new.jpg').write_bytes(b'newer upload')
        inv.archive_inventory(self.root, self.target, [self.entry])
        with tarfile.open(self.target) as archive:
            self.assertEqual(archive.getnames(), ['storage', 'storage/photo.jpg'])
            self.assertEqual(archive.extractfile('storage/photo.jpg').read(), b'old')

    def test_deleted_or_changed_snapshot_file_fails(self):
        for content in (b'new', b'longer', b'', None):
            path = self.root / 'photo.jpg'
            if content is None: path.unlink()
            else: path.write_bytes(content)
            with self.subTest(content=content), self.assertRaises(inv.InventoryError):
                inv.archive_inventory(self.root, self.target, [self.entry])

    def test_replacement_after_verified_copy_uses_verified_bytes(self):
        original = inv._copy_verified
        def racing_copy(root, entry, out=None):
            original(root, entry, out)
            (root / entry['storage_key']).write_bytes(b'new')
        with patch.object(inv, '_copy_verified', racing_copy):
            inv.archive_inventory(self.root, self.target, [self.entry])
        with tarfile.open(self.target) as archive:
            self.assertEqual(archive.extractfile('storage/photo.jpg').read(), b'old')

    def test_symlink_file_and_parent_rejected(self):
        (self.root / 'link').symlink_to(self.root / 'photo.jpg')
        (self.root / 'folder').symlink_to(self.root, target_is_directory=True)
        for key in ('link', 'folder/photo.jpg'):
            with self.assertRaises(inv.InventoryError):
                inv.archive_inventory(self.root, self.target, [dict(self.entry, storage_key=key)])

    def test_snapshot_export_is_alive_during_dump(self):
        cur = Cursor([self.entry])
        def dump(path, *, snapshot):
            self.assertTrue(cur.active)
            self.assertEqual(snapshot, 'snapshot-test')
            self.assertIn('REPEATABLE READ, READ ONLY', cur.calls[0])
            self.assertIn('pg_export_snapshot', cur.calls[1])
        env = checks.load_module('backup', {'connect': lambda: cur, 'read_inventory': inv.read_inventory})
        env['_run_pg_dump'] = dump
        self.assertEqual(env['_capture_full_database'](self.target), [self.entry])
        self.assertFalse(cur.active)

    def test_restore_checks_bytes_before_expiring_uploads(self):
        cur = Cursor([self.entry])
        env = checks.load_module('restore', dict(InventoryError=inv.InventoryError,
            read_inventory=inv.read_inventory, verify_inventory=inv.verify_inventory))
        env['_connect_restore_test'] = lambda config: cur
        manifest = dict(consistency='snapshot-reference-v1', referenced_files=1, open_uploads='restart')
        result = env['_verify_restored_files']({}, self.root, manifest)
        self.assertEqual(result['expired_upload_sessions'], 2)
        self.assertIn("status = 'OPEN'", cur.calls[-1])
        (self.root / 'photo.jpg').write_bytes(b'bad')
        cur.calls.clear()
        with self.assertRaisesRegex(env['RestoreTestError'], 'CONTENT_MISMATCH'):
            env['_verify_restored_files']({}, self.root, manifest)
        self.assertFalse(any('UPDATE' in sql for sql in cur.calls))

    def test_old_full_backup_is_verified_without_upload_mutation(self):
        cur = Cursor([self.entry])
        env = checks.load_module('restore', dict(InventoryError=inv.InventoryError,
            read_inventory=inv.read_inventory, verify_inventory=inv.verify_inventory))
        env['_connect_restore_test'] = lambda config: cur
        result = env['_verify_restored_files']({}, self.root, {})
        self.assertEqual(result['verified_files'], 1)
        self.assertFalse(any('UPDATE' in sql for sql in cur.calls))


if __name__ == '__main__': unittest.main()
