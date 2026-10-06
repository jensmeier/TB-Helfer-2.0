#!/usr/bin/env python3
"""CI-only real FastAPI/PostGIS rights and 255-client sync gate.

Creates disposable fixtures only in the empty tb_gate_http CI database.
No production endpoint is accepted; API is started by this process on loopback.
"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.config import settings
from tbserver.database import transaction
from tbserver.security import hash_password, hash_token
from psycopg.types.json import Jsonb
from sync_wait_loadtest import _portal_session, _logout_portal_session

BASE = 'http://127.0.0.1:18081'


def request(path, headers=None, *, method='GET', payload=None, raw=None, expected=200):
    headers = dict(headers or {})
    if payload is not None:
        raw = json.dumps(payload).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, headers=headers, data=raw, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            status, body = response.status, response.read()
    except urllib.error.HTTPError as exc:
        status, body = exc.code, exc.read()
    if status != expected:
        raise AssertionError(f'{method} {path.split("?")[0]}: expected {expected}, got {status}')
    try: return json.loads(body) if body else None
    except ValueError: return body


def execute(sql, params=()):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(sql, params)


def current_head():
    with transaction() as conn, conn.cursor() as cur:
        cur.execute('SELECT coalesce(max(change_id),0) AS head FROM tb.change_feed')
        return cur.fetchone()['head']


def seed():
    if os.environ.get('CI') != 'true' or settings.environment != 'test' or settings.db_name != 'tb_gate_http':
        raise RuntimeError('CI_EMPTY_TEST_DATABASE_ONLY')
    devices, portals, manifest, sites = [], [], [], []
    company, other_company = uuid4(), uuid4()
    with transaction() as conn, conn.cursor() as cur:
        cur.execute('SELECT count(*) AS n FROM tb.users')
        if cur.fetchone()['n']: raise RuntimeError('EMPTY_USER_DATABASE_REQUIRED')
        cur.execute('INSERT INTO tb.companies(id,name) VALUES (%s,%s),(%s,%s)',
                    (company, 'CI Company', other_company, 'CI Other Company'))
        permissions = {key: True for key in ('worksite.view', 'files.view', 'files.add', 'btb.view',
            'btb.create', 'btb.edit', 'defect.view', 'defect.create', 'defect.edit',
            'acceptance.view', 'acceptance.create', 'acceptance.edit', 'track.edit')}
        cur.execute("INSERT INTO tb.permission_profiles(profile_key,label,permissions) VALUES ('CI_GATE','CI',%s)",
                    (Jsonb(permissions),))
        def user(login, role='USER', co=company, device=True):
            uid = uuid4()
            cur.execute('INSERT INTO tb.users(id,company_id,display_name,login_name,company_role) VALUES (%s,%s,%s,%s,%s)',
                        (uid, co, login, login, role))
            info = dict(id=uid, login=login)
            if device:
                token = secrets.token_urlsafe(32)
                cur.execute("INSERT INTO tb.devices(user_id,device_uid,platform,auth_token_hash) VALUES (%s,%s,'ANDROID',%s)",
                            (uid, str(uuid4()), hash_token(token)))
                info['headers'] = {'Authorization': 'Bearer ' + token}
                env = 'TB_GATE_DEVICE_' + str(len(devices))
                os.environ[env] = token
                info['token_env'] = env
            return info
        for group in range(15):
            tb = user(f'ci-tb-{group}')
            devices.append(tb)
            manifest.append(dict(role='TB', token_env=tb['token_env']))
            site, team = uuid4(), uuid4()
            sites.append(site)
            cur.execute('INSERT INTO tb.worksites(id,company_id,name) VALUES (%s,%s,%s)', (site, company, f'CI Site {group}'))
            cur.execute('INSERT INTO tb.teams(id,worksite_id,lead_user_id) VALUES (%s,%s,%s)', (team, site, tb['id']))
            members = [tb]
            for helper in range(15):
                member = user(f'ci-helper-{group}-{helper}')
                devices.append(member); members.append(member)
                manifest.append(dict(role='HELPER', token_env=member['token_env']))
            for index, member in enumerate(members):
                cur.execute("""INSERT INTO tb.worksite_memberships(worksite_id,user_id,worksite_role,permission_profile,area_scope)
                               VALUES (%s,%s,%s,'CI_GATE',%s)""",
                            (site, member['id'], 'TB' if index == 0 else 'HELPER', Jsonb({'labels': ['A']})))
                cur.execute('INSERT INTO tb.team_members(team_id,user_id,role) VALUES (%s,%s,%s)',
                            (team, member['id'], 'LEAD' if index == 0 else 'HELPER'))
            portal = user(f'ci-portal-{group}', 'CHEF', device=False)
            password = secrets.token_urlsafe(24) + 'Aa1'
            env = f'TB_GATE_PORTAL_{group}'
            os.environ[env] = password
            portal['password_env'] = env
            cur.execute('INSERT INTO tb.portal_credentials(user_id,password_hash,activated_at,must_change_password) VALUES (%s,%s,now(),false)',
                        (portal['id'], hash_password(password)))
            portals.append(portal)
            manifest.append(dict(role='PORTAL', login=portal['login'], password_env=env))
        outsider = user('ci-outsider', co=other_company)
        # Do not overwrite the outsider token in the load manifest.
        admin = user('ci-admin', role='ADMIN', co=None)
        body, file_id = b'CI fixture', uuid4()
        path = settings.storage_root / 'gate/fixture.bin'
        path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(body)
        cur.execute("INSERT INTO tb.files(id,company_id,worksite_id,storage_key,mime_type,byte_size,sha256) VALUES (%s,%s,%s,'gate/fixture.bin','application/octet-stream',%s,%s)",
                    (file_id, company, sites[0], len(body), hashlib.sha256(body).hexdigest()))
        scoped = {}
        for table in ('defects', 'acceptances'):
            ids = []
            for label in ('A', 'B'):
                eid = uuid4(); ids.append(eid)
                cur.execute(f'INSERT INTO tb.{table}(id,worksite_id,title,area_scope) VALUES (%s,%s,%s,%s)',
                            (eid, sites[0], f'CI {label}', Jsonb({'labels': [label]})))
            scoped[table] = ids
    return dict(company=company, devices=devices, portals=portals, manifest=manifest, sites=sites,
                outsider=outsider, admin=admin, file=file_id, scoped=scoped)


def rights_gate(fixture):
    helper, other = fixture['devices'][1:3]
    headers, site = helper['headers'], fixture['sites'][0]
    request(f'/api/v1/worksites/{site}', headers)
    for actor in ('outsider', 'admin'):
        for path in (f'/api/v1/worksites/{site}', f'/api/v1/files/{fixture["file"]}',
                     f'/api/v1/worksites/{site}/track-data', f'/api/v1/worksites/{site}/defects',
                     f'/api/v1/worksites/{site}/acceptances'):
            request(path, fixture[actor]['headers'], expected=403)
    for table, ids in fixture['scoped'].items():
        rows = request(f'/api/v1/worksites/{site}/{table}', headers)[table]
        if [r['id'] for r in rows] != [str(ids[0])]: raise AssertionError('AREA_LIST_LEAK')
        request(f'/api/v1/{table}/{ids[0]}', headers)
        request(f'/api/v1/{table}/{ids[1]}', headers, expected=403)
    request(f'/api/v1/files/{fixture["file"]}/content', headers)
    execute('UPDATE tb.worksite_memberships SET permissions_override=%s WHERE user_id=%s',
            (Jsonb({'files.view': False}), helper['id']))
    for path in (f'/api/v1/files/{fixture["file"]}', f'/api/v1/files/{fixture["file"]}/content',
                 f'/api/v1/worksites/{site}/files', f'/api/v1/worksites/{site}/media'):
        request(path, headers, expected=403)
    execute('UPDATE tb.worksite_memberships SET permissions_override=%s WHERE user_id=%s', (Jsonb({}), helper['id']))
    upload = request(f'/api/v1/worksites/{site}/uploads', headers, method='POST', expected=201,
                     payload=dict(mime_type='application/octet-stream', expected_bytes=3, original_name='ci.bin'))
    uid = upload['id']
    request(f'/api/v1/uploads/{uid}', other['headers'], expected=403)
    request(f'/api/v1/uploads/{uid}/chunk?offset=0', other['headers'], method='PUT', raw=b'abc', expected=403)
    request(f'/api/v1/uploads/{uid}/complete', other['headers'], method='POST', payload={}, expected=403)
    request(f'/api/v1/uploads/{uid}', other['headers'], method='DELETE', expected=403)
    request(f'/api/v1/uploads/{uid}/chunk?offset=0', headers, method='PUT', raw=b'abc')
    request(f'/api/v1/uploads/{uid}/complete', headers, method='POST', payload={})
    portal = fixture['portals'][0]
    session = _portal_session(BASE, portal['login'], os.environ[portal['password_env']], 10)
    try:
        request(f'/api/v1/worksites/{site}', session.headers)
        # Browser writes without CSRF fail even for a permitted account.
        request(f'/api/v1/worksites/{site}/uploads', session.headers, method='POST',
                payload={'mime_type': 'application/octet-stream'}, expected=403)
        execute("UPDATE tb.companies SET status='DISABLED' WHERE id=%s", (fixture['company'],))
        for auth in (headers, session.headers):
            result = request(f'/api/v1/worksites/{site}', auth, expected=403)
            if result['detail'] != 'COMPANY_DISABLED': raise AssertionError('COMPANY_GUARD_MISSING')
        request('/api/v1/portal/login', method='POST', expected=403,
                payload={'login': portal['login'], 'password': os.environ[portal['password_env']]})
    finally:
        execute("UPDATE tb.companies SET status='ACTIVE' WHERE id=%s", (fixture['company'],))
        if not _logout_portal_session(BASE, session, 10): raise AssertionError('PORTAL_CLEANUP_FAILED')

    # A real in-flight longpoll must re-check each mutable access boundary.
    with ThreadPoolExecutor(max_workers=1) as pool:
        for table, column, denied, active, target in (
            ('users', 'status', 'DISABLED', 'ACTIVE', helper['id']),
            ('companies', 'status', 'DISABLED', 'ACTIVE', fixture['company']),
            ('worksite_memberships', 'status', 'REMOVED', 'ACTIVE', helper['id']),
            ('devices', 'status', 'BLOCKED', 'ACTIVE', helper['id'])):
            head = current_head()
            path = f'/api/v1/sync/changes/wait?after={head}&wait_seconds=2&worksite_id={site}'
            future = pool.submit(request, path, headers, expected=403)
            time.sleep(.25)
            if future.done(): raise AssertionError('LONGPOLL_NOT_WAITING')
            key = 'user_id' if table in ('devices', 'worksite_memberships') else 'id'
            try:
                execute(f'UPDATE tb.{table} SET {column}=%s WHERE {key}=%s', (denied, target))
                future.result(timeout=10)
            finally:
                execute(f'UPDATE tb.{table} SET {column}=%s WHERE {key}=%s', (active, target))
        head = current_head()
        opened = threading.Event()
        def stream():
            req = urllib.request.Request(BASE + f'/api/v1/sync/events?worksite_id={site}&after={head}', headers=headers)
            with urllib.request.urlopen(req, timeout=10) as response:
                opened.set()
                return response.read()
        future = pool.submit(stream)
        if not opened.wait(5): raise AssertionError('SSE_NOT_OPEN')
        try:
            execute("UPDATE tb.worksite_memberships SET status='REMOVED' WHERE user_id=%s", (helper['id'],))
            execute("INSERT INTO tb.change_feed(company_id,worksite_id,entity_type,entity_id,operation) VALUES (%s,%s,'FILE',%s,'UPDATE')",
                    (fixture['company'], site, fixture['file']))
            if future.result(timeout=10): raise AssertionError('SSE_DATA_AFTER_REVOCATION')
        finally:
            execute("UPDATE tb.worksite_memberships SET status='ACTIVE' WHERE user_id=%s", (helper['id'],))
    print(json.dumps({'rights_http_passed': True, 'longpoll_revocation_cases': 4, 'sse_revocation_passed': True}), flush=True)


def main():
    fixture = seed()
    with tempfile.TemporaryDirectory(prefix='tb-http-gate-') as tmp:
        root = Path(tmp)
        with (root / 'api.log').open('w') as log:
            process = subprocess.Popen([sys.executable, '-m', 'uvicorn', 'tbserver.main:app', '--host', '127.0.0.1',
                                        '--port', '18081', '--no-access-log'], stdout=log, stderr=log)
            try:
                for _ in range(60):
                    try:
                        request('/health/ready'); break
                    except Exception:
                        if process.poll() is not None: raise RuntimeError('API_START_FAILED')
                        time.sleep(.25)
                else: raise RuntimeError('API_NOT_READY')
                rights_gate(fixture)
                manifest = root / 'clients.json'
                manifest.write_text(json.dumps(fixture['manifest']))
                subprocess.run([sys.executable, str(Path(__file__).with_name('sync_mixed_loadtest.py')),
                    '--base-url', BASE, '--confirm-staging', BASE, '--clients-file', str(manifest),
                    '--rounds', '3', '--wait-seconds', '2', '--timeout', '40', '--max-overhead', '10'], check=True)
                with transaction() as conn, conn.cursor() as cur:
                    cur.execute('SELECT count(*) AS n FROM tb.portal_sessions WHERE revoked_at IS NULL')
                    if cur.fetchone()['n']: raise AssertionError('UNREVOKED_TEST_SESSIONS')
            except Exception:
                log.flush()
                print((root / 'api.log').read_text()[-12000:], flush=True)
                raise
            finally:
                process.terminate()
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: process.kill(); process.wait()


if __name__ == '__main__': main()
