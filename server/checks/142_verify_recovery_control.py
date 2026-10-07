"""Real journal/lock I/O; simulated runtime adapter. NOT a NAS/DB restore test."""
from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile
import unittest
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.recovery_control import Journal, JournalError, RecoveryController, RecoveryControlError


class Runtime:
    def __init__(self, path, fail=None, crash=None):
        self.path, self.fail, self.crash = Path(path), fail, crash
        if not self.path.exists():
            self.save({"active": "initial", "maintenance": False, "events": [], "data": "before"})

    def read(self):
        return json.loads(self.path.read_text())

    def save(self, value):
        self.path.write_text(json.dumps(value))

    def event(self, name, **updates):
        value = self.read()
        value.update(updates)
        value["events"].append(name)
        self.save(value)
        if name == self.crash:
            os._exit(77)
        if name == self.fail:
            self.fail = None
            raise RuntimeError("secret database connection string must not be persisted")

    def active_generation(self):
        return self.read()["active"]

    def preflight(self, state):
        self.event("preflight")

    def quiesce(self, state):
        self.event("pause", maintenance=True)

    def safety(self, state):
        assert self.read()["maintenance"]
        self.event("safety")

    def prepare(self, state):
        assert self.read()["maintenance"]
        assert self.read()["active"] == state["previous"]
        self.event("prepare")

    def verify(self, state):
        assert self.read()["maintenance"]
        self.event("verify")

    def select(self, state, generation):
        assert self.read()["maintenance"]
        self.event("select-old" if generation == state["previous"] else "select-new", active=generation)

    def probe(self, state, generation):
        assert self.read()["maintenance"]
        assert self.read()["active"] == generation
        self.event("probe-old" if generation == state["previous"] else "probe-new")

    def release(self, state, generation):
        assert self.read()["active"] == generation
        self.event("release", maintenance=False)


class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.journal = Journal(self.root / "journal")
        self.backend = Runtime(self.root / "runtime.json")
        self.controller = RecoveryController(self.journal, self.backend)
        self.request = dict(backup_id=str(uuid4()), admin_id=str(uuid4()), kind="USER")

    def run_request(self):
        return self.controller.run(**self.request)

    def test_success_all_three_types_and_repeat_uses_latest_generation(self):
        previous = "initial"
        for kind in ("USER", "TRACK", "FULL"):
            self.request["kind"] = kind
            state = self.run_request()
            self.assertEqual(state["phase"], "DONE")
            self.assertEqual(state["previous"], previous)
            previous = state["target"]
            self.assertEqual(self.backend.read()["active"], previous)
            self.assertFalse(self.backend.read()["maintenance"])
        self.assertEqual(self.backend.read()["events"].count("safety"), 3)

    def test_failures_leave_original_and_do_not_persist_exception_secrets(self):
        for failure in ("preflight", "pause", "safety", "prepare", "verify", "select-new", "probe-new"):
            with self.subTest(failure=failure):
                self.backend.fail = failure
                state = self.run_request()
                self.assertEqual(state["phase"], "ROLLED_BACK")
                self.assertEqual(self.backend.read()["active"], "initial")
                self.assertFalse(self.backend.read()["maintenance"])
                self.assertEqual(self.backend.read()["data"], "before")
                self.assertNotIn("secret", (self.journal.root / "state.json").read_text())

    def test_release_failure_after_accepting_writes_never_rolls_back(self):
        self.backend.fail = "release"
        state = self.run_request()
        self.assertEqual(state["phase"], "COMMITTED")
        self.assertEqual(state["error"], "RELEASE_FAILED")
        value = self.backend.read()
        value["data"] = "new accepted write"
        self.backend.save(value)
        with self.assertRaises(RecoveryControlError):
            self.run_request()
        recovered = self.controller.recover()
        self.assertEqual(recovered["phase"], "DONE")
        self.assertEqual(self.backend.read()["active"], state["target"])
        self.assertEqual(self.backend.read()["data"], "new accepted write")
        self.assertNotIn("select-old", self.backend.read()["events"])

    def test_failed_rollback_stays_in_maintenance_and_blocks_new_request(self):
        original = self.backend.verify
        def fail_verify(state):
            self.backend.fail = "probe-old"
            raise RuntimeError("failure")
        self.backend.verify = fail_verify
        state = self.run_request()
        self.assertEqual(state["phase"], "NEEDS_ATTENTION")
        self.assertTrue(self.backend.read()["maintenance"])
        with self.assertRaises(RecoveryControlError):
            self.run_request()
        self.backend.verify = original
        self.assertEqual(self.controller.recover()["phase"], "ROLLED_BACK")

    def test_real_process_death_before_commit_rolls_back(self):
        for crash in ("pause", "safety", "prepare", "select-new", "probe-new"):
            with self.subTest(crash=crash):
                result = subprocess.run([sys.executable, __file__, "child", str(self.root), crash], check=False)
                self.assertEqual(result.returncode, 77)
                state = self.controller.recover()
                self.assertEqual(state["phase"], "ROLLED_BACK")
                self.assertEqual(self.backend.read()["active"], "initial")
                self.assertFalse(self.backend.read()["maintenance"])

    def test_real_process_death_after_release_preserves_target(self):
        result = subprocess.run([sys.executable, __file__, "child", str(self.root), "release"], check=False)
        self.assertEqual(result.returncode, 77)
        target = self.backend.read()["active"]
        self.assertNotEqual(target, "initial")
        self.assertEqual(self.controller.recover()["phase"], "DONE")
        self.assertEqual(self.backend.read()["active"], target)

    def test_second_process_cannot_start_under_held_lock(self):
        with self.journal.locked():
            result = subprocess.run([sys.executable, __file__, "lock", str(self.root)], check=False)
            self.assertEqual(result.returncode, 31)
        self.assertIsNone(self.journal.read())

    def test_invalid_journal_and_symlink_fail_closed(self):
        with self.journal.locked():
            (self.journal.root / "state.json").write_text("{broken")
        with self.assertRaises(JournalError):
            self.controller.recover()
        (self.journal.root / "state.json").unlink()
        (self.journal.root / "state.json").symlink_to(self.root / "runtime.json")
        with self.assertRaises(JournalError):
            self.controller.recover()
        self.assertEqual(self.backend.read()["events"], [])

    def test_system_backup_cannot_be_restored_and_bad_ids_rejected(self):
        self.request["kind"] = "SYSTEM"
        with self.assertRaises(RecoveryControlError):
            self.run_request()
        self.request.update(kind="FULL", backup_id="../../etc/passwd")
        with self.assertRaises(RecoveryControlError):
            self.run_request()
        self.assertEqual(self.backend.read()["events"], [])

    def test_uncertain_commit_journal_write_keeps_maintenance(self):
        original = self.journal.write
        def uncertain_write(state):
            result = original(state)
            if state["phase"] == "COMMITTED":
                raise JournalError("simulated directory fsync failure after replace")
            return result
        self.journal.write = uncertain_write
        with self.assertRaises(JournalError):
            self.run_request()
        self.assertTrue(self.backend.read()["maintenance"])
        self.assertNotIn("release", self.backend.read()["events"])
        self.assertNotIn("select-old", self.backend.read()["events"])
        self.journal.write = original
        self.assertEqual(self.controller.recover()["phase"], "DONE")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in {"child", "lock"}:
        root = Path(sys.argv[2])
        journal = Journal(root / "journal")
        if sys.argv[1] == "lock":
            try:
                with journal.locked():
                    pass
            except RecoveryControlError:
                sys.exit(31)
            sys.exit(0)
        backend = Runtime(root / "runtime.json", crash=sys.argv[3])
        RecoveryController(journal, backend).run(backup_id=str(uuid4()), admin_id=str(uuid4()), kind="FULL")
    else:
        unittest.main()
