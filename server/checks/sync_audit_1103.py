"""SYNC-AUDIT-1103: exercise unchanged production functions with explicit doubles.

Four audit scenarios, not a passing correctness gate: late commit test deliberately
asserts observed omission. No PostgreSQL, FastAPI HTTP, Android, NAS, or real writes.
Run from a full repository: python server/checks/sync_audit_1103.py
"""
import ast
import asyncio
from pathlib import Path
from typing import Any
from uuid import UUID
import unittest

ROOT = Path(__file__).resolve().parents[1]

def production(path, names, env):
    tree = ast.parse((ROOT / path).read_text())
    tree.body = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in names]
    for n in tree.body:
        n.decorator_list = []
    exec(compile(tree, str(path), "exec"), env)
    return env

class Denied(Exception):
    def __init__(self, status_code, detail):
        self.status_code, self.detail = status_code, detail

class Audit(unittest.IsolatedAsyncioTestCase):
    def environment(self, committed):
        state = {"committed": committed, "seen": []}
        class Hub:
            async def head(self):
                return max([0] + [r["change_id"] for r in state["committed"]])
            async def wait_for_head(self, after, timeout):
                return await self.head()
        async def query(fn, *args, **kwargs):
            return fn(*args, **kwargs)
        def visible(user, **kw):
            rows = [r for r in state["committed"]
                    if kw["after"] < r["change_id"] <= kw["through"] and r.get("visible", True)]
            state["seen"].append(kw.copy())
            return sorted(rows, key=lambda r: r["change_id"])[:kw["limit"]]
        env = dict(Any=Any, UUID=UUID, Request=Any,
                   Query=lambda **kw: kw.get("default"), Depends=lambda fn: None,
                   current_portal_user=None, _MAX_WAIT_SECONDS=30,
                   _CHANGE_HUB=Hub(), _run_sync_query=query,
                   _refresh_sync_user=lambda *a: {"user_id": "test"},
                   _visible_changes=visible, _entity_types=lambda raw: [])
        production("tbserver/sync.py", {"wait_changes"}, env)
        async def fetch(after):
            return await env["wait_changes"](object(), after, 200, None, None, 0, {})
        return state, fetch

    async def test_normal_committed_event_delivered(self):
        _, fetch = self.environment([{"change_id": 10}])
        page = await fetch(9)
        self.assertEqual(page["changes"], [{"change_id": 10}])
        self.assertEqual(page["next_after"], 10)

    async def test_later_commit_of_lower_sequence_is_missed(self):
        # Models PG: A allocated 10 but has not committed; independent B commits 11.
        state, fetch = self.environment([{"change_id": 11}])
        first = await fetch(9)
        self.assertEqual(first["next_after"], 11)
        state["committed"].append({"change_id": 10})  # A now commits.
        second = await fetch(first["next_after"])
        self.assertEqual(second["changes"], [])
        self.assertEqual(second["next_after"], 11)
        self.assertNotIn(10, [r["change_id"] for r in first["changes"] + second["changes"]])
        print("REPRODUCED: lower-ID late commit 10 is omitted after cursor 11 (MVCC double).")

    async def test_unrelated_change_advances_head_without_exposing_data(self):
        _, fetch = self.environment([{"change_id": 11, "visible": False}])
        page = await fetch(9)
        self.assertEqual(page["changes"], [])
        self.assertEqual(page["next_after"], 11)

    async def test_deleted_btb_returns_404(self):
        class Cursor:
            def execute(self, *a):
                pass
            def fetchone(self):
                return {"status": "DELETED"}
        env = production("tbserver/btb.py", {"_load_btb"}, dict(UUID=UUID, HTTPException=Denied))
        with self.assertRaises(Denied) as caught:
            env["_load_btb"](Cursor(), UUID(int=1))
        self.assertEqual(caught.exception.status_code, 404)
        self.assertEqual(caught.exception.detail, "BTB_NOT_FOUND")
        print("CONFIRMED: deleted BTB detail returns 404 BTB_NOT_FOUND; Android mapping inspected separately.")

if __name__ == "__main__":
    unittest.main()
