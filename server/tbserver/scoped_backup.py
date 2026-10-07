"""Versioned USER/TRACK archives; no registration, retention or live mutation.

Caller captures the database in one read-only snapshot. File bytes are copied
and verified against that snapshot. The final archive is published only once.
"""
from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import tarfile
import tempfile

from .backup_domains import TABLES, USER, TRACK, DomainError, DomainSnapshot
from .backup_inventory import read_inventory, archive_inventory, verify_inventory

FORMAT = 'tb-scoped-backup-v1'
MAX_JSON = 64 * 1024 * 1024
ORIGINAL_FIELDS = ('id', 'company_id', 'storage_key', 'byte_size', 'sha256')
ORIGINAL_REFERENCES = {'track_data': 'source_original_id', 'track_data_sources': 'original_id',
    'track_data_km_points': 'source_original_id', 'survey_catalog_assets': 'original_id',
    'survey_catalog_segments': 'source_original_id'}


def inventory(rows):
    # Reuse the exact full-backup normalization and conflict/path checks.
    class Cursor:
        def execute(self, _):
            pass

        def fetchall(self):
            return ([r for r in rows['files'] if r.get('deleted_at') is None]
                    + list(rows['survey_originals'])
                    + [r for r in rows['releases'] if r.get('storage_key') is not None])
    return read_inventory(Cursor())


def _json(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise DomainError('BACKUP_JSON_DUPLICATE_KEY')
        result[key] = value
    return result


def _decode(raw):
    try:
        return json.loads(raw, object_pairs_hook=_unique,
                          parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except (ValueError, TypeError, RecursionError) as exc:
        raise DomainError('BACKUP_JSON_INVALID') from exc


def digest_file(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def make_payload(snapshot, kind, sequences):
    if kind not in {USER, TRACK}:
        raise DomainError('BACKUP_PARTIAL_KIND_INVALID')
    rows = snapshot.partition()[kind]
    external = {}
    for table, field in ORIGINAL_REFERENCES.items():
        for row in rows[table]:
            if row.get(field) is not None:
                original = snapshot._parent('survey_originals', row[field])
                if snapshot.owner('survey_originals', original) != kind:
                    external[str(original['id'])] = {f: original[f] for f in ORIGINAL_FIELDS}
    # Sequence counters are not row contents and can straddle USER/TRACK.
    return {'tables': rows, 'external_originals': list(external.values()), 'sequences': sequences}


def payload_snapshot(payload, kind):
    if kind not in {USER, TRACK} or not isinstance(payload, dict) or set(payload) != {'tables', 'external_originals', 'sequences'}:
        raise DomainError('BACKUP_PAYLOAD_INVALID')
    rows = payload['tables']
    if (not isinstance(rows, dict) or set(rows) != TABLES
            or any(not isinstance(v, list) or any(not isinstance(r, dict) for r in v) for v in rows.values())):
        raise DomainError('BACKUP_PAYLOAD_INVALID')
    if not isinstance(payload['external_originals'], list) or not isinstance(payload['sequences'], dict):
        raise DomainError('BACKUP_PAYLOAD_INVALID')
    extended = {t: list(v) for t, v in rows.items()}
    for original in payload['external_originals']:
        if not isinstance(original, dict) or set(original) != set(ORIGINAL_FIELDS):
            raise DomainError('BACKUP_EXTERNAL_ORIGINAL_INVALID')
        extended['survey_originals'].append(original)
    snapshot = DomainSnapshot(extended)
    for table, records in rows.items():
        if any(snapshot.owner(table, r) != kind for r in records):
            raise DomainError('BACKUP_FOREIGN_DOMAIN_ROW', table)
    for row in payload['external_originals']:
        if snapshot.owner('survey_originals', row) == kind:
            raise DomainError('BACKUP_EXTERNAL_ORIGINAL_INVALID')
    # Require exactly the external dependencies actually referenced; no hidden
    # unrelated company metadata can be smuggled into a TRACK archive.
    expected = make_payload(snapshot, kind, payload['sequences'])['external_originals']
    if sorted(expected, key=lambda r: str(r['id'])) != sorted(payload['external_originals'], key=lambda r: str(r['id'])):
        raise DomainError('BACKUP_EXTERNAL_ORIGINAL_INVALID')
    for table, state in payload['sequences'].items():
        if (table not in TABLES or not isinstance(state, dict) or set(state) != {'column', 'value'}
                or not isinstance(state['column'], str) or type(state['value']) is not int
                or state['value'] < 1 or state['value'] > 9223372036854775807):
            raise DomainError('BACKUP_SEQUENCE_INVALID')
    return snapshot


def write_archive(snapshot, kind, storage, target, runtime, *, sequences=None):
    target = Path(target)
    payload = make_payload(snapshot, kind, sequences or {})
    payload_snapshot(payload, kind)
    raw = _json(payload)
    if len(raw) > MAX_JSON:
        raise DomainError('BACKUP_SNAPSHOT_PLANNING_LIMIT')
    entries = inventory(payload['tables'])
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.scoped-', dir=target.parent) as folder:
        folder = Path(folder)
        (folder / 'domain.json').write_bytes(raw)
        archive_inventory(Path(storage), folder / 'files.tar.gz', entries)
        manifest = {'format': FORMAT, 'kind': kind, 'created_at': datetime.now(timezone.utc).isoformat(),
                    'runtime': runtime, 'members': {name: {'sha256': digest_file(folder / name),
                    'byte_size': (folder / name).stat().st_size} for name in ('domain.json', 'files.tar.gz')}}
        (folder / 'manifest.json').write_bytes(_json(manifest))
        pending = folder / 'archive.tar'
        with tarfile.open(pending, 'w') as archive:
            for name in ('manifest.json', 'domain.json', 'files.tar.gz'):
                archive.add(folder / name, arcname=name, recursive=False)
        os.chmod(pending, 0o600)
        with pending.open('rb') as handle:
            os.fsync(handle.fileno())
        os.link(pending, target)  # Atomic no-clobber publication, same filesystem.
        fd = os.open(target.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    return manifest


def _copy_member(source, target, size):
    remaining = size
    with target.open('xb') as output:
        while remaining:
            block = source.read(min(1024 * 1024, remaining))
            if not block:
                raise DomainError('BACKUP_ARCHIVE_TRUNCATED')
            output.write(block)
            remaining -= len(block)


@contextmanager
def read_archive(path, runtime, *, workspace):
    """Verify and unpack into a private disposable directory, never live storage.

    The outer archive is deliberately uncompressed; member lengths are bounded
    by the physical file. Compressed file bytes are bounded by the inventory.
    """
    with tempfile.TemporaryDirectory(prefix='scoped-read-', dir=workspace) as folder:
        root = Path(folder)
        seen = set()
        physical = Path(path).stat().st_size
        with tarfile.open(path, 'r:') as archive:
            for member in archive:
                if (member.name not in {'manifest.json', 'domain.json', 'files.tar.gz'}
                        or member.name in seen or not member.isfile() or member.issparse()
                        or member.size < 0 or member.size > physical):
                    raise DomainError('BACKUP_ARCHIVE_MEMBERS_INVALID')
                limit = 1024 * 1024 if member.name == 'manifest.json' else MAX_JSON
                if member.name != 'files.tar.gz' and member.size > limit:
                    raise DomainError('BACKUP_ARCHIVE_METADATA_TOO_LARGE')
                seen.add(member.name)
                with archive.extractfile(member) as source:
                    _copy_member(source, root / member.name, member.size)
        if len(seen) != 3:
            raise DomainError('BACKUP_ARCHIVE_MEMBERS_INVALID')
        manifest = _decode((root / 'manifest.json').read_bytes())
        if (not isinstance(manifest, dict) or manifest.get('format') != FORMAT
                or manifest.get('kind') not in {USER, TRACK}
                or manifest.get('runtime') != runtime):
            raise DomainError('BACKUP_ARCHIVE_VERSION_MISMATCH')
        if not isinstance(manifest.get('members'), dict) or set(manifest['members']) != {'domain.json', 'files.tar.gz'}:
            raise DomainError('BACKUP_ARCHIVE_MEMBERS_INVALID')
        for name, info in manifest['members'].items():
            if (not isinstance(info, dict) or type(info.get('byte_size')) is not int
                    or info['byte_size'] != (root / name).stat().st_size
                    or info.get('sha256') != digest_file(root / name)):
                raise DomainError('BACKUP_ARCHIVE_CHECKSUM_MISMATCH')
        payload = _decode((root / 'domain.json').read_bytes())
        snapshot = payload_snapshot(payload, manifest['kind'])
        entries = inventory(payload['tables'])
        expected = {'storage/' + e['storage_key']: e for e in entries}
        storage = root / 'storage'
        storage.mkdir(mode=0o700)
        seen = set()
        # Sequential extraction: never use extractall, follow links or permit
        # unreferenced members. Known size limits stop gzip amplification.
        with tarfile.open(root / 'files.tar.gz', 'r|gz') as archive:
            base_seen = False
            for member in archive:
                if member.name == 'storage' and member.isdir() and not base_seen:
                    base_seen = True
                    continue
                entry = expected.get(member.name)
                if (entry is None or member.name in seen or not member.isfile()
                        or member.issparse() or member.size != entry['byte_size']):
                    raise DomainError('BACKUP_FILE_MEMBERS_INVALID')
                seen.add(member.name)
                target = storage / entry['storage_key']
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(member) as source:
                    _copy_member(source, target, member.size)
        if seen != set(expected):
            raise DomainError('BACKUP_FILE_MEMBERS_MISSING')
        verify_inventory(storage, entries)
        yield {'manifest': manifest, 'payload': payload, 'snapshot': snapshot, 'storage': storage}
