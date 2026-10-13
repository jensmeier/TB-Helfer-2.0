"""Real child process supervision and ASGI gate tests (no Docker/NAS claim)."""
import asyncio
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.recovery_runtime import (Deployment, RuntimeControlError, RuntimeMaintenanceMiddleware,
    generation_environment, read_json, validate_selection)


class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.control = self.root / 'control'
        self.control.mkdir(mode=0o700)
        self.deployment = Deployment(self.control)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.targets = self.root / 'targets'
        self.targets.mkdir()
        self.children = []
        self.addCleanup(self.stop_all)

    def stop_all(self):
        for process in self.children:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)

    def supervisor(self, role):
        child = ("import os,time,json; from pathlib import Path; "
                 "p=Path(os.environ['STORAGE_ROOT'])/'" + role + ".json'; "
                 "p.write_text(json.dumps({k:os.environ.get(k) for k in ['DB_NAME','STORAGE_ROOT','TB_RECOVERY_ID','TB_RUNTIME_MODE','PGAPPNAME']})); "
                 "time.sleep(120)")
        runner = ('from tbserver.recovery_runtime import *; '
                  f'supervise({role!r},Deployment({str(self.control)!r}),{str(self.root / role)!r},'
                  f'command=[sys.executable,"-c",{child!r}],interval=0.02,stop_timeout=0.2)')
        env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parents[1]), DB_NAME='initial_database',
                   STORAGE_ROOT=str(self.source), TB_RECOVERY_STORAGE_ROOT=str(self.targets))
        process = subprocess.Popen([sys.executable,'-c',runner],env=env)
        self.children.append(process)
        return process

    def wait(self, predicate):
        end = time.monotonic() + 5
        while time.monotonic() < end:
            if predicate():
                return
            time.sleep(0.02)
        self.fail('supervisor condition timed out')

    def status(self, role):
        try:
            return read_json(self.root / role / 'status.json')
        except FileNotFoundError:
            return {}

    def test_stop_probe_open_switches_all_runtime_settings_and_restart_keeps_target(self):
        self.deployment.select('initial','OPEN')
        for role in ('api','worker','monitor'):
            self.supervisor(role)
        self.wait(lambda: all((self.source / (r+'.json')).exists() for r in ('api','worker','monitor')))
        stopped = self.deployment.select('initial','STOPPED')
        self.wait(lambda: all(self.status(r).get('state') == 'STOPPED' and self.status(r).get('revision') == stopped['revision'] for r in ('api','worker','monitor')))
        generation = str(uuid4())
        target = self.targets / generation
        target.mkdir()
        probe = self.deployment.select(generation,'PROBE')
        self.wait(lambda: (target / 'api.json').exists())
        self.wait(lambda: self.status('worker').get('revision') == probe['revision'])
        self.assertFalse((target / 'worker.json').exists())
        self.assertFalse((target / 'monitor.json').exists())
        data = json.loads((target / 'api.json').read_text())
        self.assertEqual(data['DB_NAME'],'tb_recovery_'+generation.replace('-',''))
        self.assertEqual(data['STORAGE_ROOT'],str(target))
        self.assertEqual(data['TB_RECOVERY_ID'],generation)
        self.assertEqual(data['TB_RUNTIME_MODE'],'PROBE')
        self.deployment.select(generation,'OPEN')
        self.wait(lambda: all((target / (r+'.json')).exists() for r in ('api','worker','monitor')))
        self.stop_all()
        (target / 'api.json').unlink()
        self.supervisor('api')
        self.wait(lambda: (target / 'api.json').exists())
        self.assertEqual(json.loads((target / 'api.json').read_text())['TB_RECOVERY_ID'],generation)

    def test_missing_and_corrupt_selection_never_start_or_keep_writer(self):
        self.supervisor('worker')
        self.wait(lambda: self.status('worker').get('state') == 'BLOCKED')
        self.assertFalse((self.source/'worker.json').exists())
        self.deployment.select('initial','OPEN')
        self.wait(lambda: (self.source/'worker.json').exists())
        (self.control/'selection.json').write_text('{broken')
        self.wait(lambda: self.status('worker').get('state') == 'BLOCKED')
        self.assertIsNone(self.status('worker')['revision'])

    def test_scope_and_path_validation(self):
        for generation in ('../db','initial/other','TB_DATABASE',None):
            with self.assertRaises((ValueError,RuntimeControlError,TypeError)):
                self.deployment.select(generation,'OPEN')
        with self.assertRaises(RuntimeControlError):
            generation_environment(str(uuid4()),{'TB_RECOVERY_STORAGE_ROOT':'relative'})
        self.deployment.select('initial','OPEN')
        target = self.control/'copy.json'
        (self.control/'selection.json').rename(target)
        (self.control/'selection.json').symlink_to(target)
        with self.assertRaises(OSError):
            self.deployment.read()

    def test_asgi_gate_blocks_wrong_generation_write_probe_websocket_and_corruption(self):
        calls = []
        async def app(scope,receive,send):
            calls.append(scope['path'])
            await send({'type':'http.response.start','status':200,'headers':[]})
            await send({'type':'http.response.body','body':b'OK'})
        async def request(middleware,path,method='GET',kind='http'):
            messages=[]
            async def send(message):
                messages.append(message)
            await middleware({'type':kind,'path':path,'method':method},None,send)
            return messages
        with patch.dict(os.environ,{'TB_RECOVERY_CONTROL_ROOT':str(self.control),'TB_RUNTIME_GENERATION':'initial','TB_RUNTIME_MODE':'PROBE'}):
            middleware=RuntimeMaintenanceMiddleware(app)
        self.deployment.select('initial','PROBE')
        self.assertEqual(asyncio.run(request(middleware,'/health/ready'))[0]['status'],200)
        self.assertEqual(asyncio.run(request(middleware,'/api/v1/worksites','POST'))[0]['status'],503)
        self.assertEqual(asyncio.run(request(middleware,'/health/ready','POST'))[0]['status'],503)
        self.assertEqual(asyncio.run(request(middleware,'/ws',kind='websocket'))[0]['code'],1013)
        self.deployment.select(str(uuid4()),'PROBE')
        self.assertEqual(asyncio.run(request(middleware,'/health/ready'))[0]['status'],503)
        (self.control/'selection.json').unlink()
        self.assertEqual(asyncio.run(request(middleware,'/health/ready'))[0]['status'],503)
        self.assertEqual(calls,['/health/ready'])

    def test_open_gate_and_legacy_pass_through(self):
        messages=[]
        async def send(message):
            messages.append(message)
        async def app(scope,receive,send):
            await send({'type':'http.response.start','status':200})
        state=self.deployment.select('initial','OPEN')
        with patch.dict(os.environ,{'TB_RECOVERY_CONTROL_ROOT':str(self.control),'TB_RUNTIME_GENERATION':'initial','TB_RUNTIME_MODE':'OPEN'}):
            middleware=RuntimeMaintenanceMiddleware(app)
        asyncio.run(middleware({'type':'http','path':'/api','method':'POST'},None,send))
        self.assertIn((b'x-tb-runtime-revision',state['revision'].encode()),messages[0]['headers'])
        with patch.dict(os.environ,{},clear=True):
            legacy=RuntimeMaintenanceMiddleware(app)
        asyncio.run(legacy({'type':'http','path':'/api','method':'POST'},None,send))
        self.assertEqual(messages[-1]['status'],200)


if __name__=='__main__':
    unittest.main(verbosity=2)
