"""Controller-only data tasks; deliberately no HTTP router or arbitrary SQL API."""
from __future__ import annotations

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import sys
import time
from uuid import UUID

from .recovery_control import validate_state
from .recovery_runtime import generation_environment, read_json, write_json


def execute(action, state):
    import psycopg
    from psycopg import sql
    from psycopg.rows import dict_row
    from .config import settings
    from .backup import backup_path_for_run, _full_backup, _hash_file
    from .backup_catalog import ArchiveCatalog
    from .backup_domain_snapshot import capture_domains
    from .backup_domains import validate_foreign_keys, validate_domain_references
    from .backup_inventory import read_inventory, verify_inventory
    from .recovery_version import runtime_identity, check_update_version
    from .scoped_backup import read_archive
    from .scoped_restore import restore_partial
    from .restore import (_extract_full_backup, _run_pg_restore, _extract_storage_archive,
                          _verify_restored_database, _verify_restored_files)
    from .recovery import _seal_target
    from .recovery_guard import MARKER, verify_cutover
    state = validate_state(state)
    runtime = runtime_identity()
    root = settings.backup_root / '.recovery'
    bundle = root / state['operation_id']
    config = dict(host=settings.db_host,port=settings.db_port,dbname=settings.db_name,
                  user=settings.db_user,password=settings.db_password)
    target_env = generation_environment(state['target'])
    target = dict(config,dbname=target_env['DB_NAME'])
    storage = Path(target_env['STORAGE_ROOT'])
    if target['dbname'] == config['dbname'] or storage.resolve() == settings.storage_root.resolve():
        raise RuntimeError('RECOVERY_SOURCE_TARGET_IDENTICAL')
    def connection(db=config):
        return psycopg.connect(**db,autocommit=True,row_factory=dict_row,application_name='tb-recovery-data')
    if action == 'drained':
        with connection(dict(config,dbname='postgres')) as conn:
            deadline=time.monotonic()+15
            while True:
                count=conn.execute("SELECT count(*) AS n FROM pg_stat_activity WHERE datname IN (%s,%s) AND backend_type='client backend'",
                                   (config['dbname'], target['dbname'])).fetchone()['n']
                if count == 0:
                    return
                if time.monotonic() >= deadline:
                    raise RuntimeError('RECOVERY_DATABASE_SESSIONS_NOT_DRAINED')
                time.sleep(0.2)
    if action == 'preflight':
        root.mkdir(mode=0o700,parents=True,exist_ok=True)
        bundle.mkdir(mode=0o700)
        write_json(bundle/'operation.json', state)
        row, path = backup_path_for_run(UUID(state['backup_id']))
        if row['kind'] != state['kind']:
            raise RuntimeError('RECOVERY_BACKUP_KIND_MISMATCH')
        # Lookup imports old successful records without retention. Copy under
        # the catalog lock so a concurrent new backup cannot remove selection.
        row = ArchiveCatalog(settings.backup_root).copy(state['backup_id'], bundle/'selected.archive')
        if ((bundle/'selected.archive').stat().st_size != row['byte_size']
                or _hash_file(bundle/'selected.archive') != row['sha256']):
            raise RuntimeError('RECOVERY_SELECTED_BACKUP_CHANGED')
        if state['kind'] == 'FULL':
            _,_,manifest = _extract_full_backup(bundle/'selected.archive',bundle/'selected')
            check_update_version(manifest)
        else:
            with read_archive(bundle/'selected.archive', runtime, workspace=bundle) as archive:
                if archive['manifest']['kind'] != state['kind']:
                    raise RuntimeError('RECOVERY_BACKUP_KIND_MISMATCH')
        with connection() as conn:
            verify_cutover(conn, settings.storage_root)
            admin=conn.execute("SELECT id FROM tb.users WHERE id=%s AND company_role='ADMIN' AND status='ACTIVE' AND deleted_at IS NULL AND (access_valid_until IS NULL OR access_valid_until>now())",(state['admin_id'],)).fetchone()
            if not admin:
                raise RuntimeError('RECOVERY_ADMIN_NOT_ACTIVE')
        write_json(bundle/'source.json', {'sha256':row['sha256'],'runtime':runtime})
        return
    metadata=read_json(bundle/'source.json')
    if metadata['runtime'] != runtime:
        raise RuntimeError('RECOVERY_RUNTIME_CHANGED_DURING_OPERATION')
    if action == 'safety':
        _full_backup(UUID(state['operation_id']),destination=bundle/'safety.tar.gz')
        _extract_full_backup(bundle/'safety.tar.gz',bundle/'safety-check')
        write_json(bundle/'safety.json',{'sha256':_hash_file(bundle/'safety.tar.gz'),'complete':True})
        return
    if action == 'prepare':
        if read_json(bundle/'safety.json')['complete'] is not True:
            raise RuntimeError('RECOVERY_SAFETY_NOT_COMPLETE')
        storage.parent.mkdir(parents=True,mode=0o700,exist_ok=True)
        storage.mkdir(mode=0o700)  # Existing targets are never reused or emptied.
        with connection(dict(config,dbname='postgres')) as admin:
            admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(target['dbname'])))
        if state['kind'] == 'FULL':
            dump,files,manifest=_extract_full_backup(bundle/'selected.archive',bundle/'full-import')
            check_update_version(manifest)
            _run_pg_restore(dump,target,clean=False)
            _verify_restored_database(target)
            _extract_storage_archive(files,bundle/'full-files')
            shutil.copytree(bundle/'full-files'/'storage',storage,dirs_exist_ok=True)
            _verify_restored_files(target,storage,manifest)
        else:
            with connection() as conn:
                verify_cutover(conn,settings.storage_root)
                current,foreign_keys,sequences=capture_domains(conn,include_sequences=True,schema_path=bundle/'schema.dump')
            restore_partial(current=current,foreign_keys=foreign_keys,current_sequences=sequences,
                source_dbname=config['dbname'],source_storage=settings.storage_root,schema_dump=bundle/'schema.dump',
                archive_path=bundle/'selected.archive',runtime=runtime,target_config=target,
                target_storage=storage,workspace=bundle)
        ready={'recovery_id':state['target'],'phase':'READY','mode':'UPDATE','source_backup_id':state['backup_id'],
               'source_sha256':metadata['sha256'],'runtime':runtime,'prepared_at':datetime.now(timezone.utc).isoformat()}
        _seal_target(target,ready,state['admin_id'])
        with connection(target) as conn:
            # Receipts tied to the old topology must not replay an old response.
            conn.execute('DELETE FROM tb.survey_network_receipts')
        write_json(storage/MARKER,ready)
        return
    if action == 'verify':
        with connection(target) as conn:
            current,foreign_keys=capture_domains(conn)
            validate_foreign_keys(current.rows,foreign_keys)
            validate_domain_references(current)
            with conn.cursor() as cur:
                entries=read_inventory(cur)
            verify_inventory(storage,entries)
            marker=read_json(storage/MARKER)
            row=conn.execute("SELECT value FROM tb.server_settings WHERE setting_key='server_recovery'").fetchone()
            if marker != row['value'] or marker['recovery_id'] != state['target']:
                raise RuntimeError('RECOVERY_TARGET_MARKER_MISMATCH')
        # Full restores also need durable extracted file and directory entries.
        for directory,_,files in os.walk(storage,topdown=False):
            for name in files:
                with (Path(directory)/name).open('rb') as handle:
                    os.fsync(handle.fileno())
            fd=os.open(directory,os.O_RDONLY|os.O_DIRECTORY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)
        return
    raise RuntimeError('RECOVERY_DATA_ACTION_INVALID')


def main():
    if len(sys.argv)!=2 or sys.argv[1] not in {'preflight','drained','safety','prepare','verify'}:
        raise RuntimeError('RECOVERY_DATA_ACTION_INVALID')
    raw=sys.stdin.buffer.read(16385)
    if len(raw)>16384:
        raise RuntimeError('RECOVERY_DATA_REQUEST_TOO_LARGE')
    try:
        execute(sys.argv[1],json.loads(raw))
    except Exception as exc:
        # Keep SQL values, credentials and tracebacks out of shared job/API
        # output. Known uppercase error codes and SQLSTATE are sufficient to
        # locate a failing phase without leaking restored row contents.
        import re
        detail=str(exc)
        code=detail if re.fullmatch(r'[A-Z][A-Z0-9_]{1,120}',detail) else type(exc).__name__
        print(json.dumps({'error':code,'sqlstate':getattr(exc,'sqlstate',None)}))
        raise SystemExit(1)


if __name__=='__main__':
    main()
