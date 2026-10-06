#!/usr/bin/env python3
"""Real PostgreSQL/pg_dump/pg_restore consistency gate in TWO empty test DBs.

Requires installed server dependencies and PostgreSQL tools. Supply DSNs only via
TB_GATE_SOURCE_DSN and TB_GATE_TARGET_DSN. Both database names must start tb_gate_.
The tool refuses existing user tables; it creates and removes its own tb schema.
Never point this at production, the existing restore-test DB, or a useful backup.
"""
from __future__ import annotations

import argparse
from dataclasses import replace
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--confirm-isolated', required=True,
                        help='source_database:target_database (both tb_gate_*)')
    args = parser.parse_args()
    import psycopg
    from psycopg.rows import dict_row
    from tbserver import backup, restore

    source_dsn = os.environ['TB_GATE_SOURCE_DSN']
    target_dsn = os.environ['TB_GATE_TARGET_DSN']
    with psycopg.connect(source_dsn, autocommit=True, row_factory=dict_row) as source, \
         psycopg.connect(target_dsn, autocommit=True, row_factory=dict_row) as target:
        source_name, target_name = source.info.dbname, target.info.dbname
        if (not source_name.startswith('tb_gate_') or not target_name.startswith('tb_gate_')
                or source_name == target_name or backup.settings.db_name in (source_name, target_name)
                or args.confirm_isolated != f'{source_name}:{target_name}'):
            raise RuntimeError('ISOLATED_DATABASE_NAMES_REQUIRED')
        # Serialize other instances of this gate before inspecting either DB.
        for conn in (source, target):
            row = conn.execute('SELECT pg_try_advisory_lock(871205137) AS acquired').fetchone()
            if not row['acquired']: raise RuntimeError('GATE_ALREADY_RUNNING')
            row = conn.execute("""
                SELECT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                               WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                               AND n.nspname NOT LIKE 'pg_toast%%' AND c.relkind IN ('r','p')) AS occupied,
                       to_regnamespace('tb') IS NOT NULL AS has_tb
            """).fetchone()
            if row['occupied'] or row['has_tb']: raise RuntimeError('EMPTY_TEST_DATABASE_REQUIRED')
        created = restored = False
        try:
            source.execute('CREATE SCHEMA tb')
            created = True
            source.execute("""
                CREATE TABLE tb.files(storage_key text, byte_size bigint, sha256 text, deleted_at timestamptz);
                CREATE TABLE tb.survey_originals(storage_key text, byte_size bigint, sha256 text);
                CREATE TABLE tb.releases(storage_key text, byte_size bigint, sha256 text);
                CREATE TABLE tb.upload_sessions(status text, updated_at timestamptz);
                INSERT INTO tb.upload_sessions VALUES ('OPEN', now());
            """)
            digest = hashlib.sha256(b'old').hexdigest()
            source.execute('INSERT INTO tb.files VALUES (%s,3,%s,NULL)', ('old.bin', digest))
            with tempfile.TemporaryDirectory(prefix='tb-snapshot-gate-') as temp:
                root = Path(temp)
                storage, backups = root / 'storage', root / 'backups'
                storage.mkdir(); backups.mkdir()
                (storage / 'old.bin').write_bytes(b'old')
                backup.settings = replace(backup.settings,
                    db_host=source.info.host, db_port=source.info.port, db_name=source_name,
                    db_user=source.info.user, db_password=source.info.password,
                    storage_root=storage, backup_root=backups)
                backup.connect = lambda: psycopg.connect(source_dsn, row_factory=dict_row)
                real_dump = backup._run_pg_dump
                def concurrent_dump(path, *, snapshot=None):
                    # Commit AFTER inventory/export and BEFORE pg_dump imports it.
                    (storage / 'new.bin').write_bytes(b'new')
                    source.execute('INSERT INTO tb.files VALUES (%s,3,%s,NULL)',
                                   ('new.bin', hashlib.sha256(b'new').hexdigest()))
                    real_dump(path, snapshot=snapshot)
                backup._run_pg_dump = concurrent_dump
                _, archive = backup._full_backup(uuid4())
                dump, files, manifest = restore._extract_full_backup(archive, root / 'unpacked')
                if manifest['referenced_files'] != 1: raise AssertionError('WRONG_SNAPSHOT_INVENTORY')
                config = dict(host=target.info.host, port=target.info.port, dbname=target_name,
                              user=target.info.user, password=target.info.password)
                restored = True
                restore._run_pg_restore(dump, config, clean=False)
                restore._extract_storage_archive(files, root / 'restored')
                report = restore._verify_restored_files(config, root / 'restored/storage', manifest)
                count = target.execute('SELECT count(*) AS n FROM tb.files').fetchone()['n']
                if count != 1 or (root / 'restored/storage/new.bin').exists():
                    raise AssertionError('POST_SNAPSHOT_DATA_INCLUDED')
                if source.execute('SELECT status FROM tb.upload_sessions').fetchone()['status'] != 'OPEN':
                    raise AssertionError('LIVE_UPLOAD_CHANGED')
                if target.execute('SELECT status FROM tb.upload_sessions').fetchone()['status'] != 'EXPIRED':
                    raise AssertionError('RESTORED_UPLOAD_NOT_EXPIRED')
                backup._run_pg_dump = real_dump
                (storage / 'old.bin').write_bytes(b'bad')
                try: backup._full_backup(uuid4())
                except backup.BackupError: pass
                else: raise AssertionError('CORRUPT_REFERENCE_ACCEPTED')
                (root / 'restored/storage/old.bin').unlink()
                try: restore._verify_restored_files(config, root / 'restored/storage', manifest)
                except restore.RestoreTestError: pass
                else: raise AssertionError('MISSING_RESTORED_REFERENCE_ACCEPTED')
                print(json.dumps(dict(passed=True, snapshot_excludes_concurrent_commit=True,
                    corrupt_backup_rejected=True, missing_restore_rejected=True, **report)))
        finally:
            # Only these fresh, explicitly confirmed test schemas were created.
            if restored: target.execute('DROP SCHEMA IF EXISTS tb CASCADE')
            if created: source.execute('DROP SCHEMA IF EXISTS tb CASCADE')
    return 0


if __name__ == '__main__':
    try: raise SystemExit(main())
    except Exception as exc:
        print(json.dumps({'passed': False, 'error_type': type(exc).__name__}))
        raise SystemExit(1)
