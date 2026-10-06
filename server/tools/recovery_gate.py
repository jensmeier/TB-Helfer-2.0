#!/usr/bin/env python3
"""Real CI-only recovery, access sealing and guarded cutover in disposable DBs."""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.request
import urllib.error
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def main():
    from tbserver.config import settings
    if os.getenv("CI") != "true" or settings.environment != "test" or settings.db_name != "tb_gate_recovery":
        raise RuntimeError("DISPOSABLE_CI_RECOVERY_DATABASE_REQUIRED")
    from tbserver.database import transaction
    from tbserver import recovery
    from tbserver.backup import run_backup
    from tbserver.security import hash_password, hash_token
    from tbserver.restore import _connect_restore_test
    from tbserver.recovery_guard import verify_cutover, MARKER
    from tbserver.jobs import claim_job, _execute, _mark_done
    from tbserver.migrations import migrate
    migrate()
    mode = os.getenv("TB_GATE_RECOVERY_MODE", "UPDATE")
    assert mode in {"UPDATE", "SECURITY"}
    admin_id, user_id, company = uuid4(), uuid4(), uuid4()
    disabled_id = uuid4()
    password = "CI-Recovery-only-Password-927!"
    settings.storage_root.mkdir(parents=True, exist_ok=True)
    (settings.storage_root / "fixture.bin").write_bytes(b"backup bytes")
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("SELECT count(*) AS n FROM tb.users")
        assert cur.fetchone()["n"] == 0, "EMPTY_SOURCE_REQUIRED"
        cur.execute("INSERT INTO tb.companies(id,name) VALUES (%s,'Recovery fixture')", (company,))
        for uid, role, cid, login in ((admin_id,"ADMIN",None,"recover-admin"),(user_id,"USER",company,"recover-user")):
            cur.execute("INSERT INTO tb.users(id,company_id,display_name,login_name,company_role) VALUES (%s,%s,%s,%s,%s)",
                        (uid,cid,login,login,role))
            cur.execute("INSERT INTO tb.portal_credentials(user_id,password_hash,activated_at,must_change_password) VALUES (%s,%s,now(),false)",
                        (uid,hash_password(password)))
        cur.execute("INSERT INTO tb.devices(user_id,device_uid,platform,auth_token_hash) VALUES (%s,'recovery-device','ANDROID',%s)", (user_id,hash_token('old-device-token')))
        cur.execute("INSERT INTO tb.files(storage_key,mime_type,byte_size,sha256,source) VALUES ('fixture.bin','application/octet-stream',12,%s,'SERVER')", (hashlib.sha256(b'backup bytes').hexdigest(),))
        cur.execute("INSERT INTO tb.upload_sessions(target_kind,temp_storage_key,expires_at) VALUES ('FILE','unfinished',now()+interval '1 day')")
        cur.execute("INSERT INTO tb.background_jobs(job_type) VALUES ('BACKUP_FULL')")
        cur.execute("INSERT INTO tb.users(id,company_id,display_name,login_name,company_role,status) VALUES (%s,%s,'blocked','recover-blocked','USER','DISABLED')", (disabled_id,company))
        from tbserver.system_settings import load_automation_policy, save_automation_policy
        policy = load_automation_policy(cur)
        policy['automatic_backup_enabled'] = True
        policy['backup_schedules']['FULL']['enabled'] = True
        save_automation_policy(cur, policy)
    selected = run_backup("FULL")
    # Changed AFTER chosen backup. The safety copy must retain this, target must not.
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("UPDATE tb.companies SET name='newer live value' WHERE id=%s", (company,))
        cur.execute("UPDATE tb.background_jobs SET status='DONE' WHERE status='PENDING'")
    base = "http://127.0.0.1:18082"
    process = subprocess.Popen([sys.executable,"-m","uvicorn","tbserver.main:app","--host","127.0.0.1","--port","18082"], stdout=subprocess.DEVNULL)
    def request(path, payload=None, headers=None, expected=200):
        headers = dict(headers or {})
        data = None if payload is None else json.dumps(payload).encode()
        if data is not None: headers['Content-Type']='application/json'
        req=urllib.request.Request(base+path, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                code, body, cookies=response.status,response.read(),response.headers.get_all('Set-Cookie',[])
        except urllib.error.HTTPError as exc:
            code,body,cookies=exc.code,exc.read(),[]
        assert code == expected, (path,code,body[:300])
        return json.loads(body), cookies
    try:
        for _ in range(100):
            try: request('/health/ready'); break
            except Exception:
                if process.poll() is not None: raise RuntimeError('API_START_FAILED')
                time.sleep(.1)
        else: raise RuntimeError('API_START_TIMEOUT')
        request('/api/v1/admin/system/recovery', expected=401)
        _, cookies=request('/api/v1/admin/session/login', {'login':'recover-admin','password':password})
        jar=dict(c.split(';')[0].split('=',1) for c in cookies)
        auth={'Cookie':'; '.join(k+'='+v for k,v in jar.items())}
        payload={'backup_run_id':str(selected['id']),'confirmation':'WIEDERHERSTELLEN '+str(selected['id'])}
        if mode == 'SECURITY': payload['mode'] = mode
        request('/api/v1/admin/system/recovery/prepare',payload,auth,403)
        auth['X-CSRF-Token']=jar['tb_admin_csrf']
        request('/api/v1/admin/system/recovery/prepare',dict(payload,confirmation='wrong'),auth,400)
        request('/api/v1/admin/system/recovery/prepare',dict(payload,mode='INVALID'),auth,422)
        response,_=request('/api/v1/admin/system/recovery/prepare',payload,auth,202)
        assert response['state']['mode'] == mode
        request('/api/v1/admin/system/recovery/prepare',payload,auth,409)
        job=claim_job()
        assert str(job['id']) == response['state']['job_id']
        ready=_execute(job)
        _mark_done(job['id'])
        status,_=request('/api/v1/admin/system/recovery',headers=auth)
        assert status['state']['phase']=='READY' and status['active'] is False
        assert ready['references']['verified_files']==1
        target=recovery._target_db_config()
        target_root=recovery._target_storage_root()
        from tbserver import restore, restore_target
        # Existing CLI and new UI use the same target lock; no overwrite race.
        with _connect_restore_test(target) as lock_conn, lock_conn.cursor() as lock_cur:
            lock_cur.execute('SELECT pg_advisory_lock(%s)',(recovery.LOCK,))
            try: restore_target.run_restore_target(source_run_id=selected['id'],confirm='not reached')
            except restore_target.RestoreTargetError as exc: assert str(exc)=='RECOVERY_TARGET_BUSY'
            else: raise AssertionError('CONCURRENT_CLI_RESTORE_ACCEPTED')
        os.environ['TB_RESTORE_TEST_DB_NAME']=target['dbname']
        os.environ['TB_RESTORE_TEST_DB_PASSWORD']='ci-test'
        try:
            try: restore._restore_db_config()
            except restore.RestoreTestError as exc: assert str(exc)=='RESTORE_TEST_DATABASE_MUST_DIFFER_FROM_TARGET'
            else: raise AssertionError('TEST_WOULD_ERASE_TARGET')
            try: restore_target._target_db_config()
            except restore_target.RestoreTargetError as exc: assert str(exc)=='RESTORE_TARGET_DATABASE_MUST_DIFFER_FROM_TEST'
            else: raise AssertionError('TARGET_EQUALS_TEST')
        finally:
            os.environ.pop('TB_RESTORE_TEST_DB_NAME')
            os.environ.pop('TB_RESTORE_TEST_DB_PASSWORD')
        try: restore_target._assert_fresh_target_database(target)
        except restore_target.RestoreTargetError: pass
        else: raise AssertionError('OCCUPIED_TARGET_ACCEPTED')
        # Neither an ordinary failure nor stale-worker recovery may replay this job.
        from tbserver.jobs import _mark_failed_or_retry, recover_stale_jobs
        with transaction() as conn, conn.cursor() as cur:
            cur.execute("INSERT INTO tb.background_jobs(job_type,status,attempts,updated_at) VALUES ('RECOVERY_PREPARE','RUNNING',1,now()-interval '1 hour') RETURNING id")
            stale_id=cur.fetchone()['id']
            cur.execute("INSERT INTO tb.background_jobs(job_type,status,attempts) VALUES ('RECOVERY_PREPARE','RUNNING',1) RETURNING id")
            failed_id=cur.fetchone()['id']
        _mark_failed_or_retry({'id':failed_id,'job_type':'RECOVERY_PREPARE','attempts':1},RuntimeError('CI_ONLY'))
        recover_stale_jobs()
        with transaction() as conn, conn.cursor() as cur:
            cur.execute('SELECT status FROM tb.background_jobs WHERE id=ANY(%s)',([failed_id,stale_id],))
            assert [r['status'] for r in cur.fetchall()]==['FAILED','FAILED']
        assert (target_root/'fixture.bin').read_bytes()==b'backup bytes'
        with transaction() as conn, conn.cursor() as cur:
            cur.execute('SELECT name FROM tb.companies WHERE id=%s',(company,))
            assert cur.fetchone()['name']=='newer live value'
            cur.execute('SELECT status FROM tb.users WHERE id=%s',(user_id,))
            assert cur.fetchone()['status']=='ACTIVE'
            cur.execute('SELECT status FROM tb.upload_sessions')
            assert cur.fetchone()['status']=='OPEN'
        with _connect_restore_test(target) as conn, conn.cursor() as cur:
            cur.execute('SELECT name FROM tb.companies WHERE id=%s',(company,))
            assert cur.fetchone()['name']=='Recovery fixture'
            cur.execute('SELECT status FROM tb.users WHERE id=%s',(user_id,))
            assert cur.fetchone()['status']==('DISABLED' if mode=='SECURITY' else 'ACTIVE')
            cur.execute("SELECT count(*) AS n FROM tb.devices WHERE auth_token_hash IS NOT NULL OR status='ACTIVE'")
            assert cur.fetchone()['n']==(0 if mode=='SECURITY' else 1)
            cur.execute("SELECT count(*) AS n FROM tb.background_jobs WHERE status IN ('PENDING','RUNNING')")
            assert cur.fetchone()['n']==0
            cur.execute("SELECT value FROM tb.server_settings WHERE setting_key='admin_automation'")
            row=cur.fetchone()
            policy=row['value']
            if mode=='SECURITY':
                assert not policy['retention_schedule']['enabled'] and not any(s['enabled'] for s in policy['backup_schedules'].values())
            else:
                assert policy['automatic_backup_enabled'] and policy['backup_schedules']['FULL']['enabled']
            cur.execute('SELECT status FROM tb.users WHERE id=%s',(disabled_id,))
            assert cur.fetchone()['status']=='DISABLED'
            cur.execute("SELECT count(*) AS n FROM tb.upload_sessions WHERE status='OPEN'")
            assert cur.fetchone()['n']==0
            os.environ['TB_RECOVERY_ID']=ready['recovery_id']
            verify_cutover(conn,target_root)
            try: verify_cutover(conn,settings.storage_root)
            except RuntimeError: pass
            else: raise AssertionError('MISMATCHED_STORAGE_ACCEPTED')
            os.environ['TB_RECOVERY_ID']=str(uuid4())
            try: verify_cutover(conn,target_root)
            except RuntimeError: pass
            else: raise AssertionError('WRONG_RECOVERY_ID_ACCEPTED')
            os.environ.pop('TB_RECOVERY_ID')
        try: _execute(job)
        except recovery.RecoveryError: pass
        else: raise AssertionError('RECOVERY_REPLAY_OVERWROTE_TARGET')
        if mode == 'SECURITY':
            legacy = dict(ready)
            legacy.pop('mode', None)
            recovery._seal_target(target, legacy, str(admin_id))
            # Persisted pre-004 state stays SECURITY; it is never promoted to UPDATE.
            with _connect_restore_test(target) as legacy_conn, legacy_conn.cursor() as legacy_cur:
                legacy_cur.execute('SELECT status FROM tb.users WHERE id=%s', (user_id,))
                assert legacy_cur.fetchone()['status'] == 'DISABLED'
        bundle=settings.backup_root/'.recovery'/ready['recovery_id']
        assert all((bundle/f).is_file() for f in ('selected.tar.gz','safety.tar.gz','recovery.json'))
        # Actually restart the API against the replacement and exercise the guard.
        process.terminate(); process.wait(timeout=10)
        env=dict(os.environ,DB_HOST=target['host'],DB_PORT=str(target['port']),DB_NAME=target['dbname'],
                 DB_USER=target['user'],DB_PASSWORD=target['password'],STORAGE_ROOT=str(target_root),
                 TB_RECOVERY_ID=ready['recovery_id'])
        process=subprocess.Popen([sys.executable,'-m','uvicorn','tbserver.main:app','--host','127.0.0.1','--port','18082'],env=env,stdout=subprocess.DEVNULL)
        for _ in range(100):
            try: request('/health/ready'); break
            except Exception: time.sleep(.1)
        else: raise RuntimeError('CUTOVER_START_TIMEOUT')
        _,cookies=request('/api/v1/admin/session/login',{'login':'recover-admin','password':password})
        jar=dict(c.split(';')[0].split('=',1) for c in cookies)
        restored_auth={'Cookie':'; '.join(k+'='+v for k,v in jar.items())}
        status,_=request('/api/v1/admin/system/recovery',headers=restored_auth)
        assert status['active'] is True
        request('/api/v1/portal/login',{'login':'recover-user','password':password},expected=403 if mode=='SECURITY' else 200)
        caps,_=request('/api/v1/capabilities')
        assert caps['recovery_id']==ready['recovery_id']
        device={'Authorization':'Bearer old-device-token'}
        request('/api/v1/device/me',headers=device,expected=401 if mode=='SECURITY' else 409)
        if mode=='UPDATE':
            request('/api/v1/device/me',headers=dict(device,**{'X-TB-Recovery-ID':str(uuid4())}),expected=409)
            request('/api/v1/device/me',headers=dict(device,**{'X-TB-Recovery-ID':ready['recovery_id']}))
        print(json.dumps({'recovery_passed':True,'mode':mode,'real_http_admin_csrf':True,'source_preserved':True,
                          'target_access_sealed':True,'cutover_http_ready':True,'mismatched_storage_blocked':True}))
    finally:
        os.environ.pop('TB_RECOVERY_ID',None)
        if process.poll() is None: process.terminate(); process.wait(timeout=10)


if __name__=='__main__': main()

