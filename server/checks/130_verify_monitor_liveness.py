"""Behavior checks of production monitor functions; no NAS/DB/network access."""
import ast
import copy
from datetime import datetime, timedelta, timezone
from pathlib import Path
import tempfile
import unittest
from typing import Any

SOURCE = Path(__file__).resolve().parents[1] / 'tbserver' / 'monitor.py'
NAMES = {'_parse_iso', '_record_transitions', 'read_monitor_snapshot'}


class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.now = datetime(2026, 10, 5, 12, tzinfo=timezone.utc)
        self.events = []
        self.state = {'updated_at': self.now.isoformat(), 'overall': 'OK',
                      'checks': {'worker': {'status': 'OK'}}}
        self.env = dict(Any=Any, datetime=datetime, timedelta=timedelta,
                        timezone=timezone, MONITOR_STALE_SECONDS=120,
                        STATE_PATH=Path(self.temp.name) / 'state',
                        EVENTS_PATH=Path(self.temp.name) / 'events',
                        _now=lambda: self.now,
                        _now_iso=lambda: self.now.isoformat(),
                        _read_json=lambda _: self.state,
                        _append_event=lambda *a, **kw: self.events.append((a, kw)),
                        _send_alert=lambda *a, **kw: None)
        tree = ast.parse(SOURCE.read_text())
        tree.body = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in NAMES]
        exec(compile(tree, str(SOURCE), 'exec'), self.env)

    def transition(self, old, new):
        self.env['_record_transitions']({'worker': dict(status='OK', **new)},
                                       {'checks': {'worker': dict(status='OK', **old)}}, None)

    def test_restart_same_container_pid(self):
        self.transition({'pid': 1, 'started_at': '2026-10-05T10:00:00Z'},
                        {'pid': 1, 'started_at': '2026-10-05T11:00:00Z'})
        self.assertEqual([e[0][1] for e in self.events], ['WORKER_RESTARTED'])

    def test_same_start_no_duplicate(self):
        self.transition({'pid': 1, 'started_at': '2026-10-05T10:00:00Z'},
                        {'pid': 1, 'started_at': '2026-10-05T12:00:00+02:00'})
        self.assertEqual(self.events, [])

    def test_legacy_pid_fallback(self):
        self.transition({'pid': 1}, {'pid': 2})
        self.assertEqual(len(self.events), 1)

    def test_initial_sample_no_restart(self):
        self.env['_transition_code'] = lambda *args: ('INFO', 'WORKER_STATE', 'state')
        self.env['_alert_type'] = lambda _: 'worker'
        self.transition({}, {'pid': 1, 'started_at': self.now.isoformat()})
        self.assertFalse(any(e[0][1] == 'WORKER_RESTARTED' for e in self.events))

    def test_fresh_snapshot(self):
        result = self.env['read_monitor_snapshot']()
        self.assertFalse(result['stale'])
        self.assertEqual(result['overall'], 'OK')
        self.assertEqual(result['current']['error'], 0)

    def test_stale_snapshot_preserves_observations(self):
        self.state['updated_at'] = (self.now - timedelta(seconds=121)).isoformat()
        original = copy.deepcopy(self.state)
        result = self.env['read_monitor_snapshot']()
        self.assertTrue(result['stale'])
        self.assertEqual(result['overall'], 'ERROR')
        self.assertEqual(result['current']['error'], 1)
        self.assertEqual(result['checks']['worker'], original['checks']['worker'])
        self.assertEqual(self.state, original)

    def test_invalid_and_future_timestamp(self):
        for timestamp in (None, 'invalid', (self.now + timedelta(seconds=121)).isoformat()):
            self.state['updated_at'] = timestamp
            self.assertTrue(self.env['read_monitor_snapshot']()['stale'])

    def test_missing_snapshot_is_unavailable(self):
        self.state = None
        result = self.env['read_monitor_snapshot']()
        self.assertFalse(result['available'])
        self.assertEqual(result['overall'], 'UNKNOWN')


if __name__ == '__main__':
    unittest.main()
