"""Disposable CI test of the actual opt-in Compose topology and gateway.

No NAS paths, host database, cloud tunnel or real credentials are used.
"""
from pathlib import Path
import json
import os
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from uuid import uuid4


def main():
    if os.getenv('CI') != 'true':
        raise RuntimeError('CI_ONLY')
    source = Path(__file__).resolve().parents[1]
    project = 'tb-restore-ci-' + uuid4().hex[:10]
    with tempfile.TemporaryDirectory(prefix=project) as directory:
        root = Path(directory)
        envfile = root / 'compose.env'
        envfile.write_text('TB_V1_DB_PASSWORD=disposable-ci-only\nTB_V1_RESTORE_TEST_DB_PASSWORD=disposable-ci-only\nTB_V1_CLOUDFLARE_TUNNEL_TOKEN=unused-ci-only\n')
        env = {k: v for k, v in os.environ.items() if not k.startswith('TB_V1_')}
        env.update(TB_V1_DB_NAME='tb_gate_compose', TB_V1_ENVIRONMENT='test', TB_V1_API_PORT='18081')
        for key, name in {
            'DB_DATA':'database', 'RESTORE_TEST_DB_DATA':'test-database',
            'STORAGE':'storage', 'BACKUP':'backups', 'DIAGNOSTICS':'diagnostics',
            'RECOVERY_CONTROL':'control', 'RECOVERY_JOURNAL':'journal', 'RECOVERY_QUEUE':'queue',
            'RECOVERY_STORAGE':'targets', 'RECOVERY_ACK':'acks', 'SYNO_DISK_TEMP':'disks',
        }.items():
            folder = root / name
            folder.mkdir()
            env['TB_V1_' + key + '_DIR'] = str(folder)
        for role in ('api', 'worker', 'monitor'):
            (root / 'acks' / role).mkdir()
        for name in ('tbserver', 'templates', 'v1_migrations', 'tools', 'data'):
            key = 'MIGRATIONS' if name == 'v1_migrations' else name.upper()
            env['TB_V1_SOURCE_' + key + '_DIR'] = str(source / name)
        override = root / 'override.json'
        override.write_text(json.dumps({'services': {'system-export': {'environment': {
            'TB_SYSTEM_IMAGE_REFS': '["tb-helfer-server-v1:local"]'}}}}))
        base = ['docker', 'compose', '--project-name', project, '--env-file', str(envfile),
                '-f', str(source / 'docker-compose.synology-automatic.v1.yml'), '-f', str(override)]
        def compose(*args, input=None):
            return subprocess.check_output(base + list(args), input=input, env=env, text=True, stderr=subprocess.STDOUT)
        def python(role, code):
            return compose('exec', '-T', role, 'python', '-c', code)
        def http(path, data=None, headers=None, expected=200):
            headers = dict(headers or {})
            if data is not None:
                data = json.dumps(data).encode()
                headers['Content-Type'] = 'application/json'
            try:
                response = urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:18081'+path, data=data, headers=headers), timeout=10)
            except urllib.error.HTTPError as exc:
                response = exc
            with response:
                body = response.read()
                assert response.status == expected, (path, response.status, body[:200])
                return json.loads(body), response.headers
        def wait(predicate, timeout=150):
            deadline = time.monotonic()+timeout
            while time.monotonic() < deadline:
                try:
                    result = predicate()
                    if result:
                        return result
                except (OSError, AssertionError, ValueError):
                    pass
                time.sleep(.3)
            raise AssertionError('COMPOSE_WAIT_TIMEOUT')
        def ready():
            return http('/health/ready')[0]['status'] == 'ok'
        try:
            subprocess.run(['docker', 'tag', 'tb-scoped-gate', 'tb-helfer-server-v1:local'], check=True)
            compose('up', '-d', '--no-build', 'gateway', 'worker', 'monitor', 'system-export')
            wait(ready)
            seed = '''
import hashlib,json
from uuid import uuid4
from pathlib import Path
from tbserver.database import transaction
from tbserver.security import hash_password
from tbserver.backup import run_backup
from tbserver.config import settings
with transaction() as conn:
 admin=uuid4()
 conn.execute("INSERT INTO tb.users(id,display_name,login_name,company_role) VALUES (%s,'CI admin','compose-admin','ADMIN')",(admin,))
 conn.execute("INSERT INTO tb.portal_credentials(user_id,password_hash,activated_at,must_change_password) VALUES (%s,%s,now(),false)",(admin,hash_password('Disposable-Compose-Password-827!')))
 conn.execute("INSERT INTO tb.companies(name) VALUES ('compose saved')")
print(json.dumps({kind:str(run_backup(kind)['id']) for kind in ('USER','TRACK','FULL','SYSTEM')}))
'''
            # Worker can reach the private image exporter; API cannot.
            selected = json.loads(python('worker', seed).strip().splitlines()[-1])
            print('PASS: actual Compose init/migration, gateway health and four independent backup creators', flush=True)
            for kind in ('USER', 'TRACK', 'FULL'):
                wait(ready)
                _, headers = http('/api/v1/admin/session/login', {'login':'compose-admin','password':'Disposable-Compose-Password-827!'})
                jar = dict(c.split(';')[0].split('=',1) for c in headers.get_all('Set-Cookie'))
                auth = {'Cookie':'; '.join(k+'='+v for k,v in jar.items()), 'X-CSRF-Token':jar['tb_admin_csrf']}
                choices, _ = http('/api/v1/admin/system/recovery/automatic', headers=auth)
                assert any(str(row['id']) == selected[kind] for row in choices['backups'])
                request_id = str(uuid4())
                http('/api/v1/admin/system/recovery/execute', dict(request_id=request_id,backup_run_id=selected[kind],kind=kind,
                     confirmation='WIEDERHERSTELLEN '+selected[kind]),auth,202)
                def finished():
                    progress, _ = http('/recovery-progress/'+request_id)
                    assert set(progress) == {'phase','error','complete'}
                    return progress if progress['complete'] else None
                progress = wait(finished)
                assert progress['phase'] == 'DONE', progress
                wait(ready)
                print('PASS: '+kind+' restore through gateway with separate containers and independent progress', flush=True)
            before = http('/health/ready')[1]['x-tb-runtime-generation']
            compose('restart', 'api', 'worker', 'monitor', 'recovery', 'gateway')
            wait(ready)
            assert http('/health/ready')[1]['x-tb-runtime-generation'] == before
            print('PASS: Compose service restart retains selected restored generation', flush=True)
        except Exception:
            # Isolated test services only; no real credentials/data in this project.
            print(compose('logs', '--tail', '70', 'migrate', 'api', 'worker', 'recovery', 'system-export', 'gateway'))
            raise
        finally:
            compose('down', '--volumes', '--remove-orphans')
            # Containers create root-owned 0700 targets. Return ownership only
            # inside this freshly generated CI directory for normal cleanup.
            if root.parent != Path(tempfile.gettempdir()) or not root.name.startswith(project):
                raise RuntimeError('CI_CLEANUP_PATH_INVALID')
            subprocess.run(['sudo', 'chown', '-R', '--no-dereference',
                            f'{os.getuid()}:{os.getgid()}', str(root)], check=True)


if __name__ == '__main__':
    main()
