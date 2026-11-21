"""S-03 phase 1: scoped sync subscriptions require explicit view permission.

No network or database access: execute the real visibility path against a
transaction double and deny before any change-feed rows are queried.
"""
import contextlib
import unittest
from unittest.mock import patch
from uuid import uuid4

from fastapi import HTTPException

from tbserver import sync


class Cursor:
    def __init__(self):
        self.queries = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, statement, params=None):
        self.queries.append((str(statement), params))

    def fetchall(self):
        return []


class Connection:
    def __init__(self, cursor):
        self._cursor = cursor

    def cursor(self):
        return self._cursor


class SyncWorksitePermissionTests(unittest.TestCase):
    def setUp(self):
        self.site = uuid4()
        self.user = {"user_id": uuid4(), "company_id": uuid4(), "company_role": "USER"}
        self.cursor = Cursor()

    def query(self, permissions):
        context = {"effective_permissions": permissions}
        with patch.object(sync, "transaction", lambda: contextlib.nullcontext(Connection(self.cursor))), \
             patch.object(sync, "worksite_context", return_value=context):
            return sync._visible_changes(
                self.user, after=0, through=None, limit=25,
                worksite_id=self.site
            )

    def test_no_view_permission_refuses_change_ids_and_actor_metadata(self):
        with self.assertRaises(HTTPException) as error:
            self.query({"btb.edit": True})
        self.assertEqual(error.exception.status_code, 403)
        self.assertFalse(any("FROM tb.change_feed" in sql for sql, _ in self.cursor.queries))

    def test_view_permission_retains_scoped_polling(self):
        self.assertEqual(self.query({"worksite.view": True}), [])
        self.assertTrue(any("FROM tb.change_feed" in sql for sql, _ in self.cursor.queries))

    def test_refresh_after_wait_enforces_same_permission(self):
        with patch.object(sync, "transaction", lambda: contextlib.nullcontext(Connection(self.cursor))), \
             patch.object(sync, "worksite_context", return_value={"effective_permissions": {}}), \
             patch.object(sync, "current_portal_user", return_value=self.user):
            with self.assertRaises(HTTPException) as error:
                sync._refresh_sync_user(object(), self.site)
        self.assertEqual(error.exception.status_code, 403)


if __name__ == "__main__":
    unittest.main()
