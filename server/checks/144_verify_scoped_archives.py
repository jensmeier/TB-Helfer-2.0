"""Real archive/file I/O and hostile-input checks; PostgreSQL has its own gate."""
from copy import deepcopy
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.backup_domains import TABLES, DomainSnapshot, DomainError, USER, TRACK
from tbserver.backup_inventory import InventoryError
from tbserver.scoped_backup import write_archive, read_archive, make_payload, payload_snapshot


class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.storage = self.root / 'source'
        self.storage.mkdir()
        self.runtime = {'format': 'test', 'migrations': []}
        self.rows = {t: [] for t in TABLES}
        self.rows['companies'] = [{'id': 'company', 'name': 'PRIVATE NAME'}]
        self.rows['portal_credentials'] = [{'user_id': 'user', 'password_hash': 'PRIVATE HASH'}]
        for identifier, company in [('global', None), ('private', 'company')]:
            raw = identifier.encode()
            (self.storage / identifier).write_bytes(raw)
            self.rows['survey_originals'].append({'id': identifier, 'company_id': company,
                'storage_key': identifier, 'byte_size': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})
        self.rows['track_data'] = [{'id': 'dataset', 'source_original_id': 'global'}]

    def write(self, kind=USER, name='archive.tar'):
        target = self.root / name
        write_archive(DomainSnapshot(self.rows), kind, self.storage, target, self.runtime)
        return target

    def test_roundtrip_both_domains_and_minimal_dependencies(self):
        for kind in (USER, TRACK):
            path = self.write(kind, kind + '.tar')
            with read_archive(path, self.runtime, workspace=self.root) as read:
                self.assertEqual(read['manifest']['kind'], kind)
                payload = read['payload']
                self.assertEqual(len(payload['tables']['survey_originals']), 1)
                if kind == TRACK:
                    self.assertNotIn('PRIVATE', json.dumps(payload))
                    self.assertFalse(payload['external_originals'])
                else:
                    self.assertEqual(payload['external_originals'][0]['id'], 'global')
                    self.assertEqual(len(payload['external_originals'][0]), 5)
                self.assertEqual(len(list(read['storage'].iterdir())), 1)

    def test_corrupt_source_never_publishes_archive(self):
        (self.storage / 'private').write_bytes(b'changed')
        with self.assertRaises(InventoryError):
            self.write()
        self.assertFalse((self.root / 'archive.tar').exists())

    def test_symlink_source_is_rejected(self):
        (self.storage / 'private').unlink()
        (self.storage / 'private').symlink_to(self.storage / 'global')
        with self.assertRaises(InventoryError):
            self.write()

    def test_existing_archive_never_overwritten(self):
        path = self.write()
        before = path.read_bytes()
        with self.assertRaises(FileExistsError):
            self.write()
        self.assertEqual(path.read_bytes(), before)

    def test_version_mismatch(self):
        with self.assertRaisesRegex(DomainError, 'VERSION_MISMATCH'):
            with read_archive(self.write(), {}, workspace=self.root):
                self.fail('accepted')

    def rewrite(self, mutate):
        source = self.write()
        result = self.root / 'changed.tar'
        with tarfile.open(source) as archive:
            members = [(m.name, archive.extractfile(m).read()) for m in archive]
        members = mutate(members)
        with tarfile.open(result, 'w') as archive:
            for name, data in members:
                info = tarfile.TarInfo(name)
                info.size = len(data)
                archive.addfile(info, io.BytesIO(data))
        return result

    def test_tamper_checksum(self):
        path = self.rewrite(lambda members: [(n, b'{}' if n == 'domain.json' else b) for n, b in members])
        with self.assertRaisesRegex(DomainError, 'CHECKSUM'):
            with read_archive(path, self.runtime, workspace=self.root):
                self.fail('accepted')

    def test_duplicate_and_path_traversal_members(self):
        for name in ('domain.json', '../escape'):
            with self.subTest(name=name):
                if (self.root / 'archive.tar').exists():
                    (self.root / 'archive.tar').unlink()
                path = self.rewrite(lambda members: members + [(name, b'{}')])
                with self.assertRaisesRegex(DomainError, 'MEMBERS_INVALID'):
                    with read_archive(path, self.runtime, workspace=self.root):
                        self.fail('accepted')
        self.assertFalse((self.root.parent / 'escape').exists())

    def test_foreign_domain_and_unrelated_dependency_rejected(self):
        payload = make_payload(DomainSnapshot(self.rows), TRACK, {})
        payload['tables']['companies'] = deepcopy(self.rows['companies'])
        with self.assertRaisesRegex(DomainError, 'FOREIGN_DOMAIN'):
            payload_snapshot(payload, TRACK)
        payload['tables']['companies'] = []
        payload['external_originals'] = [deepcopy(self.rows['survey_originals'][1])]
        with self.assertRaisesRegex(DomainError, 'EXTERNAL_ORIGINAL'):
            payload_snapshot(payload, TRACK)

    def test_invalid_paths_and_conflicting_file_metadata(self):
        self.rows['survey_originals'][1]['storage_key'] = '../private'
        with self.assertRaises(InventoryError):
            self.write()
        self.rows['survey_originals'][1]['storage_key'] = 'global'
        # Same domain has two references to one key with conflicting bytes.
        self.rows['survey_originals'][0]['company_id'] = 'company'
        with self.assertRaises(InventoryError):
            self.write()


if __name__ == '__main__':
    unittest.main(verbosity=2)
