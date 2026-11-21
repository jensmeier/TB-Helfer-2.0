"""Bounded image/source exporter. Only this isolated service sees Docker socket.

The HTTP interface accepts one UUID. Images and source paths are administrator
configuration, never request arguments. Docker requests are GET-only. No .env,
DB, user files, runtime credentials or container inspect data are archived.
"""
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, HTTPServer
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import tarfile
import tempfile
from urllib.parse import quote, urlencode
from uuid import UUID

from .backup_catalog import canonical_id


class SystemBackupError(RuntimeError):
    pass


class DockerReadClient:
    def __init__(self, socket_path='/var/run/docker.sock'):
        self.socket_path = socket_path

    def get(self, path):
        class Connection(HTTPConnection):
            def connect(inner):
                inner.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                inner.sock.settimeout(6 * 3600)
                inner.sock.connect(self.socket_path)
        conn = Connection('docker', timeout=6 * 3600)
        conn.request('GET', path)
        response = conn.getresponse()
        if response.status != 200:
            conn.close()
            raise SystemBackupError('SYSTEM_IMAGE_EXPORT_FAILED')
        return conn, response

    def inspect(self, reference):
        conn, response = self.get('/images/' + quote(reference, safe='') + '/json')
        try:
            raw = response.read(2 * 1024 * 1024 + 1)
            if len(raw) > 2 * 1024 * 1024:
                raise SystemBackupError('SYSTEM_IMAGE_METADATA_TOO_LARGE')
            image_id = json.loads(raw)['Id']
            if not re.fullmatch(r'sha256:[a-f0-9]{64}', image_id):
                raise SystemBackupError('SYSTEM_IMAGE_ID_INVALID')
            return image_id
        finally:
            conn.close()

    def save(self, images, destination):
        conn, response = self.get('/images/get?' + urlencode([('names', x) for x in images]))
        try:
            with Path(destination).open('xb') as stream:
                shutil.copyfileobj(response, stream, 1024 * 1024)
                stream.flush()
                os.fsync(stream.fileno())
        finally:
            conn.close()
        with tarfile.open(destination) as archive:
            # A Docker error body/truncated export must never become a backup.
            manifest = archive.getmember('manifest.json')
            if not manifest.isfile() or manifest.size > 2 * 1024 * 1024:
                raise SystemBackupError('SYSTEM_IMAGE_ARCHIVE_INVALID')
            entries = json.load(archive.extractfile(manifest))
            exported = {Path(x['Config']).stem for x in entries}
            if not {x.split(':')[1] for x in images.values()}.issubset(exported):
                raise SystemBackupError('SYSTEM_IMAGE_ARCHIVE_INCOMPLETE')
        if any(self.inspect(reference) != image_id for reference, image_id in images.items()):
            raise SystemBackupError('SYSTEM_IMAGE_CHANGED_DURING_BACKUP')


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def source_files(root):
    root = Path(root)
    files = []
    for name in ('tbserver', 'templates', 'v1_migrations', 'data', 'tools'):
        folder = root / name
        if folder.is_symlink():
            raise SystemBackupError('SYSTEM_SOURCE_SYMLINK')
        if folder.exists():
            for path in folder.rglob('*'):
                if path.is_symlink():
                    raise SystemBackupError('SYSTEM_SOURCE_SYMLINK')
                if (path.is_file() and '__pycache__' not in path.parts
                        and not any(part.startswith('.') for part in path.relative_to(root).parts)):
                    files.append(path)
    for name in ('requirements.txt', 'Dockerfile.v1', 'docker-compose.synology-automatic.v1.yml', 'recovery-gateway.conf'):
        path = root / name
        if path.is_symlink():
            raise SystemBackupError('SYSTEM_SOURCE_SYMLINK')
        if path.is_file():
            files.append(path)
    if not any(p.parent.name == 'tbserver' and p.name == 'main.py' for p in files):
        raise SystemBackupError('SYSTEM_SOURCE_MISSING')
    return sorted(files)


def export_system(run_id, root, source, references, client=None):
    canonical_id(run_id)
    if not references or len(references) > 12 or any(not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_./:@-]{0,250}', x) for x in references):
        raise SystemBackupError('SYSTEM_IMAGE_REFERENCES_INVALID')
    root, source = Path(root), Path(source)
    final = root / (run_id + '.tar')
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    if final.exists():
        raise SystemBackupError('SYSTEM_ARCHIVE_ALREADY_EXISTS')
    client = client or DockerReadClient()
    images = {reference: client.inspect(reference) for reference in references}
    with tempfile.TemporaryDirectory(dir=root, prefix='.export-') as tmp:
        tmp = Path(tmp)
        client.save(images, tmp / 'images.tar')
        files = source_files(source)
        before = {p.relative_to(source).as_posix(): digest(p) for p in files}
        with tarfile.open(tmp / 'source.tar.gz', 'w:gz', dereference=False) as archive:
            for path in files:
                archive.add(path, arcname=path.relative_to(source).as_posix(), recursive=False)
        with tarfile.open(tmp / 'source.tar.gz') as archive:
            for member in archive:
                if not member.isfile() or hashlib.file_digest(archive.extractfile(member), 'sha256').hexdigest() != before[member.name]:
                    raise SystemBackupError('SYSTEM_SOURCE_ARCHIVE_MISMATCH')
        if before != {p.relative_to(source).as_posix(): digest(p) for p in source_files(source)}:
            raise SystemBackupError('SYSTEM_SOURCE_CHANGED_DURING_BACKUP')
        (tmp / 'manifest.json').write_text(json.dumps({
            'format': 'tb-system-backup-v1', 'images': images, 'source_files': before,
            'images_sha256': digest(tmp / 'images.tar'), 'source_sha256': digest(tmp / 'source.tar.gz'),
            'contains_business_data': False, 'secrets_included': False,
        }, sort_keys=True))
        with tarfile.open(tmp / 'complete.tar', 'w') as archive:
            for name in ('images.tar', 'source.tar.gz', 'manifest.json'):
                archive.add(tmp / name, arcname=name, recursive=False)
        os.chmod(tmp / 'complete.tar', 0o600)
        with (tmp / 'complete.tar').open('rb') as stream:
            os.fsync(stream.fileno())
        os.link(tmp / 'complete.tar', final)
        fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    return final


def system_export_authorized(expected: str | None, provided: str | None) -> bool:
    """Deny empty/weak secrets and compare full values without early mismatch."""
    return bool(expected and len(expected) >= 32 and provided and
                secrets.compare_digest(expected, provided))


def main():
    references = json.loads(os.environ['TB_SYSTEM_IMAGE_REFS'])
    token = os.environ.get('TB_SYSTEM_EXPORT_TOKEN')
    if not system_export_authorized(token, token):
        raise SystemBackupError('SYSTEM_EXPORT_TOKEN_NOT_CONFIGURED')
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            if not system_export_authorized(token, self.headers.get('X-TB-System-Export-Token')):
                self.send_error(403, 'SYSTEM_EXPORT_ACCESS_DENIED')
                return
            try:
                if self.path != '/export' or not 0 < int(self.headers.get('Content-Length', 0)) <= 100:
                    raise ValueError()
                payload = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                if set(payload) != {'run_id'}:
                    raise ValueError()
                run_id = canonical_id(payload['run_id'])
                path = export_system(run_id, Path(os.environ['TB_BACKUP_ROOT']) / 'system', '/system-source', references)
                body = json.dumps({'storage_key': 'system/' + path.name, 'sha256': digest(path), 'byte_size': path.stat().st_size}).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except Exception:
                self.send_error(503, 'SYSTEM_EXPORT_FAILED')
        def log_message(self, *_):
            pass
    HTTPServer(('0.0.0.0', 8091), Handler).serve_forever()


if __name__ == '__main__':
    main()
