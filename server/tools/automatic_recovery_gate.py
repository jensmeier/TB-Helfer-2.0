"""Disposable CI: real PostgreSQL, real API/worker/monitor supervisors and cutover."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.request
from uuid import uuid4

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))


def main():
    if (os.getenv('CI')!='true' or os.getenv('TB_ENVIRONMENT')!='test'
            or os.getenv('DB_NAME')!='tb_gate_auto' or os.getenv('DB_HOST')!='127.0.0.1'):
        raise RuntimeError('DISPOSABLE_AUTOMATIC_RECOVERY_DATABASE_REQUIRED')
    import psycopg
    from psycopg import sql
    from psycopg.rows import dict_row
    with tempfile.TemporaryDirectory(prefix='tb-auto-gate-') as directory:
        root=Path(directory)
        source=root/'source'
        backups=root/'backups'
        control=root/'control'
        acks=root/'acks'
        targets=root/'targets'
        for path in (source,backups,control,acks,targets):
            path.mkdir(mode=0o700)
        os.environ.update(STORAGE_ROOT=str(source),TB_BACKUP_ROOT=str(backups),
                          TB_RECOVERY_STORAGE_ROOT=str(targets),TB_DIAGNOSTICS_ROOT=str(root/'diagnostics'))
        from tbserver.config import settings
        from tbserver.migrations import migrate
        from tbserver.backup import run_backup
        from tbserver.security import hash_password
        from tbserver.recovery_runtime import Deployment,generation_environment
        from tbserver.recovery_backend import ProcessBackend
        from tbserver.recovery_control import RecoveryController,Journal
        config=dict(host=settings.db_host,port=settings.db_port,dbname=settings.db_name,
                    user=settings.db_user,password=settings.db_password)
        def connection(db=None):
            return psycopg.connect(**dict(config,dbname=db or settings.db_name),autocommit=True,row_factory=dict_row)
        with connection('postgres') as conn:
            conn.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(settings.db_name)))
        migrate()
        admin,company,route=uuid4(),uuid4(),uuid4()
        (source/'fixture.bin').write_bytes(b'backup bytes')
        with connection() as conn:
            conn.execute("INSERT INTO tb.companies(id,name) VALUES (%s,'saved company')",(company,))
            conn.execute("INSERT INTO tb.users(id,display_name,login_name,company_role) VALUES (%s,'CI admin','auto-admin','ADMIN')",(admin,))
            conn.execute("INSERT INTO tb.portal_credentials(user_id,password_hash,activated_at,must_change_password) VALUES (%s,%s,now(),false)",
                         (admin,hash_password('Disposable-Test-Password-827!')))
            conn.execute("INSERT INTO tb.files(storage_key,mime_type,byte_size,sha256,source) VALUES ('fixture.bin','application/octet-stream',12,%s,'SERVER')",(hashlib.sha256(b'backup bytes').hexdigest(),))
            conn.execute("INSERT INTO tb.catalog_routes(id,route_number,name) VALUES (%s,'4321','saved route')",(route,))
        selected={kind:run_backup(kind) for kind in ('USER','TRACK','FULL')}
        with connection() as conn:
            conn.execute("UPDATE tb.companies SET name='current company' WHERE id=%s",(company,))
            conn.execute("UPDATE tb.catalog_routes SET name='current route' WHERE id=%s",(route,))
        deployment=Deployment(control)
        deployment.select('initial','OPEN')
        processes=[]
        def start(role):
            ack=acks/role
            ack.mkdir(mode=0o700,exist_ok=True)
            env=dict(os.environ,TB_RECOVERY_CONTROL_ROOT=str(control),TB_RUNTIME_ACK_ROOT=str(ack),
                     TB_MONITOR_API_URL='http://127.0.0.1:8080/health/ready')
            process=subprocess.Popen([sys.executable,'-m','tbserver.recovery_runtime',role],env=env,
                                     stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            processes.append(process)
            return process
        def health():
            try:
                with urllib.request.urlopen('http://127.0.0.1:8080/health/ready',timeout=2) as response:
                    return response.status==200
            except OSError:
                return False
        def wait(predicate):
            deadline=time.monotonic()+45
            while time.monotonic()<deadline:
                if predicate():
                    return
                time.sleep(0.1)
            raise AssertionError('RUNTIME_WAIT_TIMEOUT')
        try:
            for role in ('api','worker','monitor'):
                start(role)
            wait(health)
            backend=ProcessBackend(control,acks,timeout=45,probe_url='http://127.0.0.1:8080/health/ready')
            controller=RecoveryController(Journal(control),backend)
            previous='initial'
            for kind in ('USER','TRACK','FULL'):
                result=controller.run(backup_id=str(selected[kind]['id']),admin_id=str(admin),kind=kind)
                assert result['phase']=='DONE',result
                assert deployment.read()['generation']==result['target'] and deployment.read()['mode']=='OPEN'
                env=generation_environment(result['target'])
                with connection(env['DB_NAME']) as conn:
                    company_name=conn.execute('SELECT name FROM tb.companies WHERE id=%s',(company,)).fetchone()['name']
                    route_name=conn.execute('SELECT name FROM tb.catalog_routes WHERE id=%s',(route,)).fetchone()['name']
                    assert company_name=='saved company',company_name
                    assert route_name==('current route' if kind=='USER' else 'saved route'),route_name
                    assert conn.execute("SELECT value FROM tb.server_settings WHERE setting_key='server_recovery'").fetchone()['value']['recovery_id']==result['target']
                assert (backups/'.recovery'/result['operation_id']/'safety.tar.gz').is_file()
                assert (Path(env['STORAGE_ROOT'])/'fixture.bin').read_bytes()==b'backup bytes'
                # Previous source remains independently available, including the
                # last changes made there before this operation.
                prior=generation_environment(previous)
                with connection(prior['DB_NAME']) as conn:
                    assert conn.execute('SELECT count(*) AS n FROM tb.users').fetchone()['n']==1
                previous=result['target']
                print('PASS: automatic '+kind+' safety/restore/probe/cutover with real API, worker and monitor',flush=True)
            # A failed probe after actual selection must reactivate the previous
            # DB/storage pair and preserve a write accepted before the attempt.
            env=generation_environment(previous)
            with connection(env['DB_NAME']) as conn:
                conn.execute("UPDATE tb.companies SET name='keep after failed restore' WHERE id=%s",(company,))
            class FailTargetProbe(ProcessBackend):
                def probe(self,state,generation):
                    if generation==state['target']:
                        super().probe(state,generation)
                        raise RuntimeError('INJECTED_AFTER_REAL_TARGET_PROBE')
                    return super().probe(state,generation)
            failed=RecoveryController(Journal(control),FailTargetProbe(control,acks,timeout=45,probe_url='http://127.0.0.1:8080/health/ready'))
            result=failed.run(backup_id=str(selected['USER']['id']),admin_id=str(admin),kind='USER')
            assert result['phase']=='ROLLED_BACK',result
            assert deployment.read()['generation']==previous and deployment.read()['mode']=='OPEN'
            with connection(env['DB_NAME']) as conn:
                assert conn.execute('SELECT name FROM tb.companies WHERE id=%s',(company,)).fetchone()['name']=='keep after failed restore'
            # Restart supervised services and controller recovery: keep the
            # committed/rolled-back generation, never fall back to initial.
            for process in processes:
                process.terminate()
            for process in processes:
                process.wait(timeout=40)
            processes.clear()
            for role in ('api','worker','monitor'):
                start(role)
            controller.recover()
            wait(health)
            assert deployment.read()['generation']==previous
            print('PASS: real target-probe failure reactivates previous data; service/controller restart preserves selection',flush=True)
        finally:
            for process in processes:
                if process.poll() is None:
                    process.terminate()
            for process in processes:
                try:
                    process.wait(timeout=40)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__=='__main__':
    main()
