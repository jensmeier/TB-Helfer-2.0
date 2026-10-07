"""Real dump/archive/restore gate, restricted to newly created disposable CI DBs."""
from copy import deepcopy
import hashlib
import os
from pathlib import Path
import sys
import tempfile
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def main():
    from tbserver.config import settings
    if (os.getenv('CI') != 'true' or settings.environment != 'test'
            or settings.db_name != 'tb_gate_scoped' or settings.db_host != '127.0.0.1'):
        raise RuntimeError('DISPOSABLE_CI_SCOPED_DATABASE_REQUIRED')
    import psycopg
    from psycopg import sql
    from psycopg.rows import dict_row
    from tbserver.migrations import migrate
    from tbserver.backup_domains import DomainError, DomainSnapshot, USER, TRACK
    from tbserver.backup_domain_snapshot import capture_domains
    from tbserver.recovery_version import runtime_identity
    from tbserver.scoped_backup import write_archive, read_archive
    from tbserver.scoped_restore import restore_partial
    config = dict(host=settings.db_host, port=settings.db_port, user=settings.db_user,
                  password=settings.db_password, dbname=settings.db_name)
    def connection(db=None):
        return psycopg.connect(**dict(config, dbname=db or settings.db_name), autocommit=True, row_factory=dict_row)
    def new_db(name):
        assert name.startswith('tb_gate_scoped')
        with connection('postgres') as conn:
            conn.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(name)))
        return dict(config, dbname=name)
    new_db(settings.db_name)  # Never reuse or clear a preexisting database.
    migrate()
    with tempfile.TemporaryDirectory(prefix='tb-scoped-gate-') as temp, connection() as conn:
        root = Path(temp)
        storage, workspace = root / 'source', root / 'work'
        storage.mkdir()
        workspace.mkdir()
        company, site, global_id, private_id, dataset, route = [uuid4() for _ in range(6)]
        conn.execute("INSERT INTO tb.companies(id,name) VALUES (%s,'saved company')", (company,))
        conn.execute("INSERT INTO tb.worksites(id,company_id,name) VALUES (%s,%s,'site')", (site, company))
        for identifier, owner, key in ((global_id,None,'global.dat'), (private_id,company,'private.dat')):
            data = ('original-' + key).encode()
            (storage / key).write_bytes(data)
            conn.execute("""INSERT INTO tb.survey_originals(id,company_id,data_kind,storage_key,original_name,sha256,byte_size)
                            VALUES (%s,%s,'TRA',%s,%s,%s,%s)""",
                         (identifier,owner,key,key,hashlib.sha256(data).hexdigest(),len(data)))
        conn.execute("""INSERT INTO tb.track_data(id,worksite_id,dataset_key,dataset_kind,source_original_id)
                        VALUES (%s,%s,'test','TRA',%s)""", (dataset,site,global_id))
        conn.execute("INSERT INTO tb.catalog_routes(id,route_number,name) VALUES (%s,'1234','saved route')", (route,))
        conn.execute("INSERT INTO tb.audit_log(action) VALUES ('SAVED')")
        conn.execute("SELECT setval('tb.audit_log_id_seq',1000,true)")
        saved, fk, saved_seq = capture_domains(conn, include_sequences=True)
        runtime = runtime_identity()
        paths = {}
        for kind in (USER, TRACK):
            paths[kind] = root / (kind + '.tar')
            write_archive(saved,kind,storage,paths[kind],runtime,sequences=saved_seq)
            with read_archive(paths[kind],runtime,workspace=workspace) as verified:
                if kind == TRACK:
                    assert not verified['payload']['tables']['companies']
                    assert not verified['payload']['tables']['portal_credentials']
        conn.execute("UPDATE tb.companies SET name='current company' WHERE id=%s", (company,))
        conn.execute("UPDATE tb.catalog_routes SET name='current route' WHERE id=%s", (route,))
        conn.execute("UPDATE tb.server_settings SET value='{}'::jsonb WHERE setting_key='admin_automation'")
        conn.execute("INSERT INTO tb.audit_log(action) VALUES ('CURRENT')")
        schema = root / 'schema.dump'
        current, fk, current_seq = capture_domains(conn, include_sequences=True, schema_path=schema)
        before = deepcopy(current.index)
        def restore(kind, suffix, *, snapshot=None, archive=None, target=None):
            target = target or new_db('tb_gate_scoped_' + suffix)
            destination = root / suffix
            destination.mkdir()
            result = restore_partial(current=snapshot or current, foreign_keys=fk,
                current_sequences=current_seq, source_dbname=settings.db_name, source_storage=storage,
                schema_dump=schema, archive_path=archive or paths[kind], runtime=runtime,
                target_config=target, target_storage=destination, workspace=workspace)
            return result, target, destination
        for kind in (USER,TRACK):
            result, target, destination = restore(kind,kind.lower())
            assert result['state'] == 'VERIFIED' and result['verified_files'] == 2, result
            with connection(target['dbname']) as check:
                state, _ = capture_domains(check)
                assert state.index['companies'][(str(company),)]['name'] == ('saved company' if kind == USER else 'current company')
                assert state.index['catalog_routes'][(str(route),)]['name'] == ('saved route' if kind == TRACK else 'current route')
                assert state.rows['server_settings'] == current.rows['server_settings']
                value = check.execute("INSERT INTO tb.audit_log(action) VALUES ('AFTER') RETURNING id").fetchone()['id']
                assert value > current_seq['audit_log']['value'], value
            assert (destination / 'global.dat').read_bytes() == (storage / 'global.dat').read_bytes()
            assert (destination / 'private.dat').read_bytes() == (storage / 'private.dat').read_bytes()
            try:
                restore(kind,kind.lower() + '_retry',target=target)
            except DomainError as exc:
                assert exc.code == 'BACKUP_RESTORE_TARGET_NOT_FRESH', exc
            else:
                raise AssertionError('EXISTING_TARGET_ACCEPTED')
        print('PASS: USER/TRACK real pg_dump/pg_restore, counterpart/control/files preserved, sequences, fresh-target guard')

        changed = deepcopy(current.rows)
        next(r for r in changed['survey_originals'] if r['id'] == str(global_id))['sha256'] = '0'*64
        try:
            restore(USER,'wrong_original',snapshot=DomainSnapshot(changed))
        except DomainError as exc:
            assert exc.code == 'BACKUP_ORIGINAL_IDENTITY_CHANGED', exc
        else:
            raise AssertionError('CHANGED_ORIGINAL_ACCEPTED')
        # A valid archive can still violate a unique constraint when combined
        # with current data. PostgreSQL must reject it in the isolated target.
        duplicate = deepcopy(saved.rows)
        row = deepcopy(duplicate['companies'][0])
        # Companies have no unique name: use real route_number unique index.
        row = deepcopy(duplicate['catalog_routes'][0])
        row['id'] = str(uuid4())
        duplicate['catalog_routes'].append(row)
        bad = root / 'unique.tar'
        write_archive(DomainSnapshot(duplicate),TRACK,storage,bad,runtime,sequences=saved_seq)
        try:
            restore(TRACK,'unique',archive=bad)
        except DomainError as exc:
            assert exc.code == 'BACKUP_POSTGRES_TOOL_FAILED', exc
        else:
            raise AssertionError('UNIQUE_CONSTRAINT_NOT_VALIDATED')
        with connection('tb_gate_scoped_unique') as failed:
            assert failed.execute('SELECT count(*) AS n FROM tb.catalog_routes').fetchone()['n'] == 2
        # Missing current-side file causes target failure, never source writes.
        private_bytes = (storage / 'private.dat').read_bytes()
        (storage / 'private.dat').unlink()
        from tbserver.backup_inventory import InventoryError
        try:
            restore(TRACK,'missing_file')
        except InventoryError:
            pass
        else:
            raise AssertionError('MISSING_CURRENT_FILE_ACCEPTED')
        (storage / 'private.dat').write_bytes(private_bytes)
        restore(USER,'repeat')
        after, _ = capture_domains(conn)
        assert after.index == before, 'SOURCE_DATABASE_CHANGED'
        print('PASS: original-identity conflict, real PostgreSQL unique failure, missing-file failure, repeat into fresh target, source unchanged')


if __name__ == '__main__':
    main()
