"""Boundary and schedule migration checks for the opt-in deployment."""
import ast
from pathlib import Path
import sys
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

class Tests(unittest.TestCase):
    def test_mount_and_network_privilege_boundaries(self):
        config = yaml.safe_load((ROOT/'docker-compose.synology-automatic.v1.yml').read_text())
        services = config['services']
        for role in ('api','worker','monitor'):
            mounts = services[role]['volumes']
            self.assertTrue(any(x.endswith(':/tb/recovery-control:ro') for x in mounts))
            self.assertTrue(any(x.endswith(':/app/templates:ro') for x in mounts))
            self.assertFalse(any('docker.sock' in x for x in mounts))
        self.assertFalse(any('docker.sock' in x for x in services['recovery']['volumes']))
        self.assertNotIn('ports', services['system-export'])
        self.assertEqual(services['system-export']['networks'], ['system-export'])
        self.assertTrue(config['networks']['system-export']['internal'])
        self.assertNotIn('ports', services['api'])
        self.assertIn('ports', services['gateway'])
        self.assertEqual(services['migrate']['command'][-1], 'migrate')

    def test_policy_migration_preserves_schedule_and_email(self):
        tree = ast.parse((ROOT/'tbserver/system_settings.py').read_text())
        tree.body = [n for n in tree.body if not (isinstance(n,ast.ImportFrom) and (n.level or n.module.startswith('psycopg')))]
        env = {}
        exec(compile(tree,'policy','exec'),env)
        normalize = env['normalize_automation_policy']
        value = normalize({'policy_revision':2,'email_hour':13,'backup_schedules':{
            'DATABASE':{'enabled':False,'hour':2,'minute':17},
            'FILES':{'enabled':True,'frequency':'MONTHLY','monthday':12,'hour':4},
            'FULL':{'enabled':False,'hour':8}}})
        schedules = value['backup_schedules']
        self.assertEqual(set(schedules), {'USER','TRACK','FULL','SYSTEM'})
        self.assertFalse(schedules['USER']['enabled'])
        self.assertEqual(schedules['USER']['minute'],17)
        self.assertEqual(schedules['TRACK']['monthday'],12)
        self.assertFalse(schedules['SYSTEM']['enabled'])
        self.assertEqual(value['email_hour'],13)
        self.assertEqual(normalize(value),value)

if __name__=='__main__':
    unittest.main()
