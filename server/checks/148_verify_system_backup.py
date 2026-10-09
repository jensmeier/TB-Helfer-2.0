"""System archive source capture with a bounded fake Docker reader."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.system_backup import export_system, SystemBackupError


class Images:
    def inspect(self, reference):
        return 'sha256:' + 'a' * 64
    def save(self, images, destination):
        with tarfile.open(destination, 'w') as out:
            data = json.dumps([{'Config': 'a'*64+'.json', 'RepoTags': list(images), 'Layers': []}]).encode()
            info = tarfile.TarInfo('manifest.json');info.size = len(data)
            out.addfile(info, io.BytesIO(data))


class Tests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.source = self.root / 'source';self.source.mkdir()
        (self.source / 'tbserver').mkdir()
        (self.source / 'tbserver/main.py').write_text('print("server")')
        (self.source / '.env').write_text('NOT_TO_BE_SAVED')
        (self.source / 'storage-v1').mkdir()
        (self.source / 'storage-v1/private.bin').write_bytes(b'BUSINESS_DATA')
        self.backups = self.root / 'system'

    def test_archive_contains_images_source_manifest_but_no_business_or_env(self):
        path = export_system(str(uuid4()), self.backups, self.source, ['server:local'], Images())
        with tarfile.open(path) as out:
            self.assertEqual(out.getnames(), ['images.tar', 'source.tar.gz', 'manifest.json'])
            manifest = json.load(out.extractfile('manifest.json'))
            self.assertFalse(manifest['contains_business_data'])
            self.assertEqual(manifest['images'], {'server:local': 'sha256:'+'a'*64})
            data = out.extractfile('source.tar.gz').read()
            self.assertEqual(hashlib.sha256(data).hexdigest(), manifest['source_sha256'])
            with tarfile.open(fileobj=io.BytesIO(data)) as source:
                self.assertEqual(source.getnames(), ['tbserver/main.py'])
        self.assertFalse((self.root / 'full').exists())

    def test_export_failure_never_publishes_or_deletes_old_archive(self):
        first = export_system(str(uuid4()), self.backups, self.source, ['server:local'], Images())
        class Broken(Images):
            def save(self, *args):
                raise RuntimeError('image failed')
        with self.assertRaises(RuntimeError):
            export_system(str(uuid4()), self.backups, self.source, ['server:local'], Broken())
        self.assertEqual(list(self.backups.glob('*.tar')), [first])

    def test_existing_archive_and_symlink_source_rejected(self):
        run = str(uuid4())
        export_system(run, self.backups, self.source, ['server:local'], Images())
        with self.assertRaises(SystemBackupError):
            export_system(run, self.backups, self.source, ['server:local'], Images())
        (self.source / 'tbserver/link').symlink_to(self.source / '.env')
        with self.assertRaisesRegex(SystemBackupError, 'SYMLINK'):
            export_system(str(uuid4()), self.backups, self.source, ['server:local'], Images())

    def test_invalid_id_or_reference_rejected(self):
        for run, refs in (('../escape', ['server:local']), (str(uuid4()), ['x;rm -rf /']), (str(uuid4()), [])):
            with self.assertRaises(RuntimeError):
                export_system(run, self.backups, self.source, refs, Images())


if __name__ == '__main__':
    unittest.main()
