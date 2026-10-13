"""Request persistence/replay and initialization; simulated data backend."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tbserver.recovery_control import Journal, RecoveryController
from tbserver.recovery_runtime import Deployment
from tbserver.recovery_service import RequestQueue, RequestError, initialize, process_one, public_progress
spec = importlib.util.spec_from_file_location('control_tests', ROOT / 'checks/142_verify_recovery_control.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class Tests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.queue = RequestQueue(self.root / 'queue')
        self.journal = Journal(self.root / 'journal')
        self.backend = module.Runtime(self.root / 'backend.json')
        self.controller = RecoveryController(self.journal, self.backend)
        self.request = dict(request_id=str(uuid4()), backup_id=str(uuid4()), admin_id=str(uuid4()),
                            kind='USER', generation='initial')

    def test_persistent_request_busy_and_duplicate(self):
        self.queue.submit(self.request)
        self.assertEqual(RequestQueue(self.queue.root).read(), self.request)
        self.queue.submit(self.request)
        with self.assertRaisesRegex(RequestError, 'BUSY'):
            self.queue.submit(dict(self.request, request_id=str(uuid4())))
        self.assertEqual(public_progress(self.request['request_id'], self.journal, self.queue)['phase'], 'QUEUED')
        state = process_one(self.controller, self.queue)
        self.assertEqual(state['phase'], 'DONE')
        self.assertIsNone(self.queue.read())
        self.assertEqual(public_progress(self.request['request_id'], self.journal, self.queue),
                         {'phase': 'DONE', 'error': None, 'complete': True})
        self.queue.submit(self.request, state)
        self.assertIsNone(self.queue.read())

    def test_crash_after_commit_before_queue_removal_never_repeats(self):
        self.queue.submit(self.request)
        with patch.object(self.queue, 'finish', side_effect=RuntimeError('crash')):
            with self.assertRaises(RuntimeError):
                process_one(self.controller, self.queue)
        data = self.backend.read()
        data['data'] = 'accepted-new-write'
        self.backend.save(data)
        process_one(self.controller, self.queue)
        self.assertEqual(self.backend.read()['events'].count('safety'), 1)
        self.assertEqual(self.backend.read()['data'], 'accepted-new-write')
        self.assertIsNone(self.queue.read())

    def test_stale_generation_never_starts(self):
        self.queue.submit(dict(self.request, generation=str(uuid4())))
        with self.assertRaisesRegex(RequestError, 'GENERATION_CHANGED'):
            process_one(self.controller, self.queue)
        self.assertEqual(self.backend.read()['events'], [])

    def test_rejected_fields_and_system_kind(self):
        for update in ({'kind': 'SYSTEM'}, {'admin_id': '../x'}, {'command': 'anything'}):
            with self.assertRaises(RequestError):
                self.queue.submit(dict(self.request, **update))

    def test_initialization_never_resets_lost_or_corrupt_selection(self):
        control = self.root / 'control'
        initialize(control, self.journal.root)
        self.assertEqual(Deployment(control).read()['generation'], 'initial')
        generation = str(uuid4())
        Deployment(control).select(generation, 'OPEN')
        initialize(control, self.journal.root)
        self.assertEqual(Deployment(control).read()['generation'], generation)
        (control / 'selection.json').unlink()
        with self.assertRaisesRegex(RequestError, 'MISSING_REVIEW'):
            initialize(control, self.journal.root)

    def test_rollback_result_and_unknown_progress(self):
        self.backend.fail = 'prepare'
        self.queue.submit(self.request)
        state = process_one(self.controller, self.queue)
        self.assertEqual(state['phase'], 'ROLLED_BACK')
        self.assertIsNone(self.queue.read())
        self.assertIsNone(public_progress(str(uuid4()), self.journal, self.queue))


if __name__ == '__main__':
    unittest.main()
