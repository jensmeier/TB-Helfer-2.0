"""Build and validate an isolated partial-restore target. Never activate it.

The recovery controller must stop all writers before capturing current data and
keep them stopped until its durable cutover decision. This module cannot itself
provide that deployment-wide maintenance guarantee.
"""
from __future__ import annotations

import os
from pathlib import Path
import subprocess

from .backup_domains import (TABLES, DomainError, DomainSnapshot, combine,
    validate_foreign_keys, validate_domain_references, validate_original_dependencies)
from .backup_inventory import _copy_verified, verify_inventory
from .scoped_backup import inventory, read_archive

TARGET_LOCK = 731019303  # Same exclusive target lock as existing recovery paths.


def _pg(command, config, extra):
    env = dict(os.environ, PGPASSWORD=config['password'])
    args = [command, '--host', config['host'], '--port', str(config['port']),
            '--username', config['user'], '--dbname', config['dbname'], '--no-password', *extra]
    result = subprocess.run(args, env=env, capture_output=True, timeout=6 * 3600)
    if result.returncode:
        # pg_restore errors can contain complete SQL rows/credentials. Keep them
        # out of API error strings and shared test logs.
        raise DomainError('BACKUP_POSTGRES_TOOL_FAILED', command)


def dump_schema(conn, target, snapshot):
    target = Path(target)
    with target.open('xb'):
        pass
    os.chmod(target, 0o600)
    config = {'host': conn.info.host, 'port': conn.info.port, 'user': conn.info.user,
              'dbname': conn.info.dbname, 'password': conn.info.password}
    _pg('pg_dump', config, ['--format=custom', '--schema-only', '--no-owner', '--no-privileges',
                          '--snapshot', snapshot, '--file', str(target)])


def _paths(target_storage, source_storage, workspace):
    target_storage, source_storage, workspace = map(Path, (target_storage, source_storage, workspace))
    for path in (target_storage, source_storage, workspace):
        if path.is_symlink() or not path.is_dir() or path.absolute() != path.resolve():
            raise DomainError('BACKUP_RESTORE_STORAGE_INVALID')
    target_storage, source_storage, workspace = map(Path.resolve, (target_storage, source_storage, workspace))
    for a, b in ((target_storage, source_storage), (target_storage, workspace)):
        if a == b or a in b.parents or b in a.parents:
            raise DomainError('BACKUP_RESTORE_STORAGE_NOT_SEPARATE')
    if any(target_storage.iterdir()):
        raise DomainError('BACKUP_RESTORE_STORAGE_NOT_EMPTY')
    return target_storage, source_storage


def _sequences(cur):
    cur.execute("""SELECT table_name,column_name,
        pg_get_serial_sequence(format('%I.%I',table_schema,table_name),column_name) AS sequence
        FROM information_schema.columns WHERE table_schema='tb' AND column_default LIKE 'nextval(%'""")
    return {r['table_name']: r for r in cur.fetchall()}


def restore_partial(*, current, foreign_keys, current_sequences, source_dbname,
                    source_storage, schema_dump, archive_path, runtime,
                    target_config, target_storage, workspace):
    """Returns VERIFIED only. Caller owns target allocation and later sealing.

    Credentials/configuration are trusted controller inputs, never archive data.
    Failed targets are left isolated for diagnosis and never emptied for retry.
    """
    import psycopg
    from psycopg import sql
    from psycopg.rows import dict_row
    from psycopg.types.json import Jsonb
    from .backup_domain_snapshot import capture_domains

    if not source_dbname or target_config['dbname'] == source_dbname:
        raise DomainError('BACKUP_RESTORE_SOURCE_TARGET_IDENTICAL')
    storage, source_storage = _paths(target_storage, source_storage, workspace)
    with read_archive(archive_path, runtime, workspace=workspace) as saved:
        kind = saved['manifest']['kind']
        validate_original_dependencies(current, saved['snapshot'], kind)
        combined = combine(current, saved['snapshot'], kind)
        planned = DomainSnapshot(combined)
        validate_foreign_keys(combined, foreign_keys)
        validate_domain_references(planned)
        expected_migrations = [{'version': r['version'], 'name': r['name'], 'sha256': r['checksum_sha256']}
                               for r in sorted(combined['schema_migrations'], key=lambda r: r['version'])]
        if expected_migrations != runtime.get('migrations'):
            raise DomainError('BACKUP_RESTORE_SCHEMA_VERSION_MISMATCH')
        entries = inventory(combined)
        selected_entries = {e['storage_key']: e for e in inventory(saved['payload']['tables'])}
        with psycopg.connect(**target_config, autocommit=True, row_factory=dict_row) as conn, conn.cursor() as cur:
            cur.execute('SELECT current_database() AS name, pg_try_advisory_lock(%s) AS locked', (TARGET_LOCK,))
            identity = cur.fetchone()
            if identity['name'] != target_config['dbname'] or not identity['locked']:
                raise DomainError('BACKUP_RESTORE_TARGET_LOCKED')
            # The session owns this lock until connection close, including every
            # pg_restore subprocess and final data/file verification.
            cur.execute("SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname='tb') AS present")
            if cur.fetchone()['present']:
                raise DomainError('BACKUP_RESTORE_TARGET_NOT_FRESH')
            _pg('pg_restore', target_config, ['--section=pre-data', '--no-owner', '--no-privileges',
                                            '--exit-on-error', str(schema_dump)])
            cur.execute("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='tb'")
            columns = {}
            for row in cur.fetchall():
                columns.setdefault(row['table_name'], set()).add(row['column_name'])
            if set(columns) != TABLES:
                raise DomainError('BACKUP_TABLE_SET_MISMATCH')
            sequences = _sequences(cur)
            saved_sequences = saved['payload']['sequences']
            if set(sequences) != set(current_sequences) or set(sequences) != set(saved_sequences):
                raise DomainError('BACKUP_SEQUENCE_SCHEMA_CHANGED')
            with conn.transaction():
                for table in sorted(TABLES):
                    query = sql.SQL('INSERT INTO tb.{} SELECT * FROM jsonb_populate_record(NULL::tb.{}, %s)').format(
                        sql.Identifier(table), sql.Identifier(table))
                    for row in combined[table]:
                        if set(row) != columns[table]:
                            raise DomainError('BACKUP_ROW_COLUMNS_CHANGED', table)
                        cur.execute(query, (Jsonb(row),))
            # PostgreSQL builds and validates real unique/FK/check constraints.
            _pg('pg_restore', target_config, ['--section=post-data', '--no-owner', '--no-privileges',
                                            '--exit-on-error', str(schema_dump)])
            for table, info in sequences.items():
                column = info['column_name']
                states = (current_sequences[table], saved_sequences[table])
                if any(s['column'] != column or type(s['value']) is not int or s['value'] < 1 for s in states):
                    raise DomainError('BACKUP_SEQUENCE_INVALID', table)
                cur.execute(sql.SQL('SELECT max({}) AS maximum FROM tb.{}').format(
                    sql.Identifier(column), sql.Identifier(table)))
                maximum = max(cur.fetchone()['maximum'] or 1, *(s['value'] for s in states))
                cur.execute('SELECT setval(%s::regclass,%s,true)', (info['sequence'], maximum))
            for entry in entries:
                selected = selected_entries.get(entry['storage_key'])
                if selected is not None and selected != entry:
                    raise DomainError('BACKUP_REFERENCE_CONFLICT')
                root = saved['storage'] if selected is not None else source_storage
                destination = storage / entry['storage_key']
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open('xb') as output:
                    os.chmod(destination, 0o600)
                    _copy_verified(root, entry, output)
                    output.flush()
                    os.fsync(output.fileno())
            restored, restored_fks = capture_domains(conn)
            validate_foreign_keys(restored.rows, restored_fks)
            validate_domain_references(restored)
            if restored.index != planned.index:
                raise DomainError('BACKUP_RESTORE_ROW_VERIFICATION_FAILED')
            result = verify_inventory(storage, entries)
            # Persist newly created directory entries before reporting verified.
            for directory, _, _ in os.walk(storage, topdown=False):
                fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
                try:
                    os.fsync(fd)
                finally:
                    os.close(fd)
            return {'state': 'VERIFIED', 'kind': kind, **result}
