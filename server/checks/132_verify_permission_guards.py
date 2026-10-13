"""Production permission/auth functions with DB doubles, no live HTTP/DB."""
import ast
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace
from typing import Any
from uuid import UUID, uuid4
import unittest

ROOT = Path(__file__).resolve().parents[1] / 'tbserver'


class HTTPException(Exception):
    def __init__(self, status_code, detail):
        self.status_code, self.detail = status_code, detail


def load(name, functions=None, extra=None):
    tree = ast.parse((ROOT / (name + '.py')).read_text())
    tree.body = [n for n in tree.body if (
        isinstance(n, ast.FunctionDef) and (functions is None or n.name in functions)
        or functions is None and isinstance(n, (ast.Assign, ast.AnnAssign)))]
    for n in tree.body:
        if isinstance(n, ast.FunctionDef):
            n.decorator_list = []
    env = dict(Any=Any, UUID=UUID, math=math, HTTPException=HTTPException,
               Request=Any, Depends=lambda f: None, current_portal_user=None)
    env.update(extra or {})
    exec(compile(tree, name, 'exec'), env)
    return env


class Cursor:
    def __init__(self, row): self.row, self.calls = row, []
    def execute(self, *args): self.calls.append(args)
    def fetchone(self): return self.row
    def fetchall(self): return []
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def cursor(self): return self


class Guards(unittest.TestCase):
    def setUp(self):
        self.env = load('permissions')
        self.company = uuid4()
        self.user = dict(user_id=uuid4(), company_id=self.company, company_role='USER')
        self.row = dict(id=uuid4(), company_id=self.company, deleted_at=None,
                        membership_id=uuid4(), membership_status='ACTIVE',
                        worksite_role='HELPER', profile_permissions={'files.view': True},
                        permissions_override={}, area_scope={'all': True})

    def context(self):
        return self.env['worksite_context'](Cursor(self.row), self.user, self.row['id'])

    def test_same_company_member(self):
        self.assertTrue(self.context()['effective_permissions']['files.view'])

    def test_cross_company_membership_denied_for_user_and_chef(self):
        self.row['company_id'] = uuid4()
        for role in ('USER', 'CHEF'):
            self.user['company_role'] = role
            with self.assertRaises(HTTPException) as error: self.context()
            self.assertEqual(error.exception.status_code, 403)

    def test_user_without_company_denied(self):
        self.user['company_id'] = None
        with self.assertRaises(HTTPException): self.context()

    def test_admin_worksite_access_denied(self):
        self.user.update(company_role='ADMIN', company_id=None)
        with self.assertRaises(HTTPException) as error: self.context()
        self.assertEqual(error.exception.status_code, 403)

    def test_own_company_chef_without_membership(self):
        self.user['company_role'] = 'CHEF'
        self.row['membership_id'] = None
        self.assertTrue(self.context()['effective_permissions']['worksite.view'])

    def test_revoked_membership_denied(self):
        self.row['membership_status'] = 'INACTIVE'
        with self.assertRaises(HTTPException): self.context()

    def test_permission_values_fail_closed(self):
        for value in ('false', 'true', 1, 0, [], {}, None, False):
            merged = self.env['_merge_permissions']({'files.view': True}, {'files.view': value})
            self.assertFalse(merged['files.view'])
        self.assertTrue(self.env['_merge_permissions']({}, {'files.view': True})['files.view'])

    def test_invalid_scope_never_becomes_unrestricted(self):
        for value in (float('nan'), float('inf'), '-Infinity', 'bad', True):
            invalid = {'km_from_m': value, 'km_to_m': value}
            for user, entity in (({'all': True}, invalid), (invalid, {'km_from_m': 3}),
                                 ({'km_from_m': 0, 'km_to_m': 10}, invalid)):
                self.assertFalse(self.env['area_scope_allows'](user, entity))

    def test_valid_scope_and_empty_helper(self):
        allows = self.env['area_scope_allows']
        self.assertTrue(allows({'km_from_m': 10, 'km_to_m': 0}, {'km_from_m': 2, 'km_to_m': 9}))
        self.assertFalse(allows({}, {'km_from_m': 2}))
        self.assertFalse(allows({'labels': ['A']}, {'labels': ['B']}))

    def test_worksite_list_filters_company(self):
        cur = Cursor(None)
        env = load('worksites', {'list_worksites'}, {'transaction': lambda: cur})
        env['list_worksites'](self.user)
        sql, args = cur.calls[0]
        self.assertIn('w.company_id = %s', sql)
        self.assertEqual(args, (self.user['user_id'], self.company))

    def test_portal_device_admin_reject_disabled_and_expired_users(self):
        now = datetime.now(timezone.utc)
        request = SimpleNamespace(cookies={'tb_portal_session': 'test', 'tb_admin_session': 'test'})
        for module, function in [('portal', '_load_session'), ('devices', 'current_device_user'),
                                 ('admin_session', 'current_admin_session_user')]:
            for field, value, code in [('status', 'DISABLED', 'USER_DISABLED'),
                                       ('deleted_at', now, 'USER_DISABLED'),
                                       ('access_valid_until', now, 'USER_EXPIRED')]:
                row = dict(status='ACTIVE', deleted_at=None, access_valid_until=None,
                           expires_at=now+timedelta(hours=1), revoked_at=None, device_status='ACTIVE')
                row[field] = value
                cur = Cursor(row)
                env = load(module, {function}, dict(transaction=lambda: cur, _now=lambda: now,
                           hash_token=lambda x:x, _bearer_token=lambda r:'test',
                           SESSION_COOKIE='tb_portal_session', ADMIN_SESSION_COOKIE='tb_admin_session'))
                with self.assertRaises(HTTPException) as error: env[function](request)
                self.assertEqual(error.exception.detail, code)
                self.assertEqual(len(cur.calls), 1)


if __name__ == '__main__': unittest.main()
