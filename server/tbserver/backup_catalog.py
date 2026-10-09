"""Restore-independent, durable archive index. No database or runtime imports.

Only verified successful artifacts enter the index. Import never prunes; a newly
published artifact prunes only its own kind. Operation/safety folders are outside
this namespace. A shared lock also protects a selected archive while copying it.
"""
from contextlib import contextmanager
from datetime import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
from uuid import UUID, uuid4

KINDS = frozenset({'USER', 'TRACK', 'FULL', 'SYSTEM'})


class CatalogError(RuntimeError):
    pass


def canonical_id(value):
    try:
        value = str(value)
        if str(UUID(value)) != value:
            raise ValueError()
        return value
    except (ValueError, TypeError, AttributeError) as exc:
        raise CatalogError('BACKUP_CATALOG_ID_INVALID') from exc


def validate(row):
    try:
        value = {key: row[key] for key in ('id', 'kind', 'storage_key', 'byte_size',
                                          'sha256', 'started_at', 'finished_at')}
        value['id'] = canonical_id(value['id'])
        kind = value['kind']
        if kind not in KINDS or row.get('status', 'OK') != 'OK':
            raise ValueError()
        parts = value['storage_key'].split('/')
        if (len(parts) != 2 or parts[0] != kind.lower()
                or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,200}', parts[1])
                or parts[1].endswith('.tmp')):
            raise ValueError()
        if type(value['byte_size']) is not int or value['byte_size'] <= 0:
            raise ValueError()
        if not re.fullmatch(r'[a-f0-9]{64}', value['sha256']):
            raise ValueError()
        for key in ('started_at', 'finished_at'):
            date = value[key]
            if isinstance(date, str):
                date = datetime.fromisoformat(date)
            if not isinstance(date, datetime) or date.tzinfo is None:
                raise ValueError()
            value[key] = date.isoformat()
        value['status'] = 'OK'
        return value
    except (ValueError, TypeError, KeyError, AttributeError) as exc:
        raise CatalogError('BACKUP_CATALOG_ENTRY_INVALID') from exc


class ArchiveCatalog:
    def __init__(self, root):
        self.root = Path(root)
        self.index = self.root / '.catalog'

    @contextmanager
    def locked(self):
        self.root.mkdir(parents=True, exist_ok=True)
        self.index.mkdir(mode=0o700, exist_ok=True)
        for directory in (self.root, self.index):
            if not stat.S_ISDIR(directory.lstat().st_mode):
                raise CatalogError('BACKUP_CATALOG_DIRECTORY_UNSAFE')
        fd = os.open(self.index / 'lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                raise CatalogError('BACKUP_CATALOG_LOCK_UNSAFE')
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield self
        finally:
            os.close(fd)

    def _sync(self):
        for directory in (self.index, self.root):
            fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)

    def _rows(self):
        rows = []
        for path in self.index.glob('*.json'):
            canonical_id(path.stem)
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            try:
                info = os.fstat(fd)
                if not stat.S_ISREG(info.st_mode) or info.st_size > 16384:
                    raise CatalogError('BACKUP_CATALOG_ENTRY_INVALID')
                with os.fdopen(fd, 'r') as stream:
                    fd = -1
                    row = validate(json.load(stream))
                if row['id'] != path.stem:
                    raise CatalogError('BACKUP_CATALOG_ID_MISMATCH')
                rows.append(row)
            except (ValueError, UnicodeError) as exc:
                raise CatalogError('BACKUP_CATALOG_ENTRY_INVALID') from exc
            finally:
                if fd >= 0:
                    os.close(fd)
        return sorted(rows, key=lambda x: (datetime.fromisoformat(x['finished_at']), x['id']), reverse=True)

    @contextmanager
    def _parent(self, row):
        # Use directory descriptors for the final open/unlink: symlink changes
        # cannot redirect a retention deletion into an operation or live data.
        rootfd = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            fd = os.open(row['kind'].lower(), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=rootfd)
            try:
                yield fd, row['storage_key'].split('/')[1]
            finally:
                os.close(fd)
        finally:
            os.close(rootfd)

    @contextmanager
    def _file(self, row):
        with self._parent(row) as (parent, name):
            fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=parent)
            try:
                info = os.fstat(fd)
                if not stat.S_ISREG(info.st_mode) or info.st_size != row['byte_size']:
                    raise CatalogError('BACKUP_CATALOG_SIZE_MISMATCH')
                with os.fdopen(fd, 'rb') as stream:
                    fd = -1
                    digest = hashlib.sha256()
                    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                        digest.update(chunk)
                    if digest.hexdigest() != row['sha256']:
                        raise CatalogError('BACKUP_CATALOG_HASH_MISMATCH')
                    stream.seek(0)
                    yield stream
            finally:
                if fd >= 0:
                    os.close(fd)

    def _write(self, row):
        temp = self.index / ('.' + str(uuid4()) + '.tmp')
        try:
            with temp.open('x') as stream:
                os.fchmod(stream.fileno(), 0o600)
                json.dump(row, stream, sort_keys=True)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp, self.index / (row['id'] + '.json'))
            self._sync()
        finally:
            temp.unlink(missing_ok=True)

    def register(self, row, *, prune=False, keep=4):
        row = validate(row)
        if type(keep) is not int or not 1 <= keep <= 50:
            raise CatalogError('BACKUP_CATALOG_KEEP_INVALID')
        with self.locked():
            rows = self._rows()
            previous = next((r for r in rows if r['id'] == row['id']), None)
            if previous is not None and previous != row:
                raise CatalogError('BACKUP_CATALOG_ID_CONFLICT')
            if any(r['storage_key'] == row['storage_key'] and r['id'] != row['id'] for r in rows):
                raise CatalogError('BACKUP_CATALOG_PATH_CONFLICT')
            with self._file(row) as stream:
                os.fsync(stream.fileno())
            self._write(row)
            # Reimport/retry cannot trigger destructive retention.
            if not prune or previous is not None:
                return {'purged_run_ids': [], 'delete_error_run_ids': []}
            same = [r for r in self._rows() if r['kind'] == row['kind']]
            # Delayed/out-of-order registrations never delete newer backups.
            if same[0]['id'] != row['id']:
                return {'purged_run_ids': [], 'delete_error_run_ids': []}
            purged, errors = [], []
            for old in same[keep:]:
                try:
                    with self._file(old):
                        pass
                    with self._parent(old) as (fd, name):
                        info = os.stat(name, dir_fd=fd, follow_symlinks=False)
                        if not stat.S_ISREG(info.st_mode):
                            raise CatalogError('BACKUP_CATALOG_FILE_UNSAFE')
                        os.unlink(name, dir_fd=fd)
                        os.fsync(fd)
                    (self.index / (old['id'] + '.json')).unlink()
                    self._sync()
                    purged.append(old['id'])
                except (OSError, CatalogError):
                    errors.append(old['id'])
            return {'purged_run_ids': purged, 'delete_error_run_ids': errors}

    def list(self):
        with self.locked():
            result = []
            for row in self._rows():
                item = dict(row)
                try:
                    with self._parent(row) as (fd, name):
                        info = os.stat(name, dir_fd=fd, follow_symlinks=False)
                        item['available'] = stat.S_ISREG(info.st_mode) and info.st_size == row['byte_size']
                except OSError:
                    item['available'] = False
                result.append(item)
            return result

    def get(self, backup_id):
        with self.locked():
            row = next((r for r in self._rows() if r['id'] == canonical_id(backup_id)), None)
            if row is None:
                return None
            with self._file(row):
                return row, self.root / row['storage_key']

    def copy(self, backup_id, target):
        """Pin selected bytes before writers pause; retention uses the same lock."""
        import shutil
        with self.locked():
            row = next((r for r in self._rows() if r['id'] == canonical_id(backup_id)), None)
            if row is None:
                raise CatalogError('BACKUP_CATALOG_NOT_FOUND')
            with self._file(row) as source, Path(target).open('xb') as output:
                os.fchmod(output.fileno(), 0o600)
                shutil.copyfileobj(source, output, 1024 * 1024)
                output.flush()
                os.fsync(output.fileno())
            return row
