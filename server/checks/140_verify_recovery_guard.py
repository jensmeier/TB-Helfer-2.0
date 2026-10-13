"""Behavior checks for the real cutover guard without a database dependency."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('recovery_guard',ROOT/'tbserver/recovery_guard.py')
guard=importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class Cursor:
    def __init__(self,state): self.state=state
    def __enter__(self): return self
    def __exit__(self,*args): pass
    def execute(self,*args): pass
    def fetchone(self): return {'value':self.state}
    def cursor(self): return self


class GuardTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        self.state={'recovery_id':str(uuid4()),'phase':'READY','source_sha256':'a'*64}
        self.write(self.state)
        self.env=patch.dict(os.environ,TB_RECOVERY_ID=self.state['recovery_id'])
        self.env.start()
    def tearDown(self): self.env.stop(); self.temp.cleanup()
    def write(self,state): (self.root/guard.MARKER).write_text(json.dumps(state))
    def test_matching_target(self): guard.verify_cutover(Cursor(self.state),self.root)
    def test_wrong_database(self):
        with self.assertRaises(RuntimeError): guard.verify_cutover(Cursor(dict(self.state,recovery_id=str(uuid4()))),self.root)
    def test_wrong_storage(self):
        self.write(dict(self.state,source_sha256='b'*64))
        with self.assertRaises(RuntimeError): guard.verify_cutover(Cursor(self.state),self.root)
    def test_partial_target(self):
        with self.assertRaises(RuntimeError): guard.verify_cutover(Cursor(dict(self.state,phase='RUNNING')),self.root)
    def test_missing_marker(self):
        (self.root/guard.MARKER).unlink()
        with self.assertRaises(RuntimeError): guard.verify_cutover(Cursor(self.state),self.root)
    def test_marker_symlink(self):
        marker=self.root/guard.MARKER
        marker.rename(self.root/'other')
        marker.symlink_to(self.root/'other')
        with self.assertRaises(RuntimeError): guard.verify_cutover(Cursor(self.state),self.root)
    def test_existing_server_unchanged(self):
        os.environ.pop('TB_RECOVERY_ID')
        guard.verify_cutover(None,self.root/'does-not-exist')


if __name__=='__main__': unittest.main()
