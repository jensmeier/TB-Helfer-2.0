"""Durable catalog/retention filesystem tests; no production data."""
from datetime import datetime, timedelta, timezone
import hashlib
import multiprocessing
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.backup_catalog import ArchiveCatalog, CatalogError


class Tests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.catalog = ArchiveCatalog(self.root)
        self.number = 0

    def row(self, kind='FULL'):
        self.number += 1
        uid = str(uuid4())
        data = ('archive-' + uid).encode()
        key = kind.lower() + '/' + uid + '.tar'
        path = self.root / key
        path.parent.mkdir(exist_ok=True)
        path.write_bytes(data)
        now = (datetime.now(timezone.utc) + timedelta(seconds=self.number)).isoformat()
        return dict(id=uid, kind=kind, status='OK', storage_key=key, byte_size=len(data),
                    sha256=hashlib.sha256(data).hexdigest(), started_at=now, finished_at=now)

    def test_four_per_kind_independent_and_restart_without_database(self):
        for kind in ('USER', 'TRACK', 'FULL', 'SYSTEM'):
            for _ in range(6):
                self.catalog.register(self.row(kind), prune=True)
        rows = ArchiveCatalog(self.root).list()
        self.assertEqual(len(rows), 16)
        for kind in ('USER', 'TRACK', 'FULL', 'SYSTEM'):
            self.assertEqual(sum(r['kind'] == kind for r in rows), 4)
        for row in rows:
            self.assertEqual(self.catalog.get(row['id'])[0]['sha256'], row['sha256'])

    def test_failed_or_damaged_new_backup_never_prunes(self):
        old = [self.row() for _ in range(4)]
        for row in old:
            self.catalog.register(row, prune=True)
        bad = self.row()
        (self.root / bad['storage_key']).write_bytes(b'bad')
        with self.assertRaises(CatalogError):
            self.catalog.register(bad, prune=True)
        with self.assertRaises(CatalogError):
            self.catalog.register(dict(old[0], status='FAILED'), prune=True)
        self.assertEqual(len(self.catalog.list()), 4)
        self.assertTrue(all((self.root / r['storage_key']).exists() for r in old))

    def test_import_and_duplicate_registration_do_not_prune(self):
        rows = [self.row() for _ in range(6)]
        for row in rows:
            self.catalog.register(row)
        self.catalog.register(rows[-1], prune=True)
        self.assertEqual(len(self.catalog.list()), 6)
        self.catalog.register(self.row(), prune=True)
        self.assertEqual(len(self.catalog.list()), 4)

    def test_out_of_order_publication_keeps_newer_archives(self):
        old = self.row()
        for _ in range(4):
            self.catalog.register(self.row(), prune=True)
        self.catalog.register(old, prune=True)
        self.assertEqual(len(self.catalog.list()), 5)

    def test_protected_operation_and_unregistered_archives_untouched(self):
        protected = self.root / '.recovery' / 'operation' / 'safety.tar.gz'
        protected.parent.mkdir(parents=True)
        protected.write_bytes(b'safety')
        unregistered = self.row()
        for _ in range(6):
            self.catalog.register(self.row(), prune=True)
        self.assertEqual(protected.read_bytes(), b'safety')
        self.assertTrue((self.root / unregistered['storage_key']).exists())
        with self.assertRaises(CatalogError):
            self.catalog.register(dict(unregistered, storage_key='.recovery/safety.tar.gz'))

    def test_symlink_path_and_file_cannot_escape(self):
        row = self.row()
        path = self.root / row['storage_key']
        outside = self.root / 'outside'
        outside.write_bytes(path.read_bytes())
        path.unlink()
        path.symlink_to(outside)
        with self.assertRaises(OSError):
            self.catalog.register(row, prune=True)
        self.assertTrue(outside.exists())
        with self.assertRaises(CatalogError):
            self.catalog.register(dict(row, storage_key='full/../outside'))

    def test_delete_failure_keeps_entry_and_file(self):
        rows = [self.row() for _ in range(4)]
        for row in rows:
            self.catalog.register(row)
        newest = self.row()
        import os
        original = os.unlink
        def unlink(path, *args, **kwargs):
            if path == Path(rows[0]['storage_key']).name:
                raise PermissionError('test')
            return original(path, *args, **kwargs)
        with patch('os.unlink', unlink):
            result = self.catalog.register(newest, prune=True)
        self.assertEqual(result['delete_error_run_ids'], [rows[0]['id']])
        self.assertTrue((self.root / rows[0]['storage_key']).exists())
        self.assertEqual(len(self.catalog.list()), 5)

    def test_corrupt_index_blocks_all_retention(self):
        row = self.row()
        self.catalog.register(row)
        (self.root / '.catalog' / (row['id'] + '.json')).write_text('{broken')
        with self.assertRaises(CatalogError):
            self.catalog.register(self.row(), prune=True)
        self.assertTrue((self.root / row['storage_key']).exists())

    def test_copy_is_verified_and_survives_later_retention(self):
        row = self.row()
        self.catalog.register(row)
        target = self.root / 'selected.archive'
        copied = self.catalog.copy(row['id'], target)
        for _ in range(5):
            self.catalog.register(self.row(), prune=True)
        self.assertFalse((self.root / row['storage_key']).exists())
        self.assertEqual(hashlib.sha256(target.read_bytes()).hexdigest(), copied['sha256'])
        with self.assertRaises(CatalogError):
            self.catalog.copy(row['id'], self.root / 'missing.archive')

    def test_hash_change_rejected_even_when_size_matches(self):
        row = self.row()
        self.catalog.register(row)
        (self.root / row['storage_key']).write_bytes(b'x' * row['byte_size'])
        with self.assertRaisesRegex(CatalogError, 'HASH_MISMATCH'):
            self.catalog.get(row['id'])


if __name__ == '__main__':
    unittest.main()
