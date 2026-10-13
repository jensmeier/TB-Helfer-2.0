import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
"""Execute production authorization paths with SQL/request doubles (not HTTP)."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
from datetime import datetime, timedelta, timezone
import unittest
from uuid import uuid4

spec = importlib.util.spec_from_file_location('guards', Path(__file__).with_name('132_verify_permission_guards.py'))
guards = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guards)
Denied = guards.HTTPException


class Cursor(guards.Cursor):
    def __init__(self, context, file=None, entity=None):
        super().__init__(context)
        self.context, self.file, self.entity = context, file, entity
    def fetchone(self):
        sql = self.calls[-1][0]
        if 'FROM tb.files' in sql: return self.file
        if 'FROM tb.worksites w' in sql: return self.context
        return self.entity


class EndpointRights(unittest.TestCase):
    def setUp(self):
        self.user = dict(user_id=uuid4(), company_id=uuid4(), company_role='USER')
        self.context = dict(id=uuid4(), company_id=self.user['company_id'], deleted_at=None,
            membership_id=uuid4(), membership_status='ACTIVE', worksite_role='HELPER',
            profile_permissions={'files.view': True, 'files.add': True, 'btb.view': True,
                                 'acceptance.view': True, 'defect.view': True},
            permissions_override={}, area_scope={'labels': ['A']})
        self.file = dict(id=uuid4(), worksite_id=self.context['id'], company_id=self.user['company_id'],
                         deleted_at=None, mime_type='image/jpeg')
        self.cur = Cursor(self.context, self.file)
        self.permissions = guards.load('permissions')
        extra = dict(self.permissions, transaction=lambda: self.cur,
            canonical_photo_metadata=lambda **kw: {'photo_metadata': {}},
            Query=lambda **kw: kw.get('default'), _decode_file_cursor=lambda x: None,
            FINAL_PDF_PURPOSE='BTB_FINAL_PDF', _ALLOWED_LINK_TYPES={'WORKSITE', 'BTB', 'ACCEPTANCE', 'DEFECT'})
        self.env = guards.load('files', {'_authorize_upload', '_load_file_for_user',
            '_file_visible_for_context', '_entity_link_visible', '_validate_link',
            'list_files', 'list_worksite_media'}, extra)

    def test_same_company_file_and_own_upload_allowed(self):
        self.assertEqual(self.env['_load_file_for_user'](self.cur, self.user, self.file['id'])['id'], self.file['id'])
        upload = dict(self.file, user_id=self.user['user_id'])
        self.assertEqual(self.env['_authorize_upload'](self.cur, self.user, upload, write=True)['id'], self.context['id'])

    def test_admin_foreign_company_removed_membership_block_file_and_upload(self):
        scenarios = [('admin', lambda: self.user.update(company_role='ADMIN')),
                     ('foreign', lambda: self.user.update(company_id=uuid4())),
                     ('removed', lambda: self.context.update(membership_status='REMOVED'))]
        for label, mutate in scenarios:
            self.setUp(); mutate()
            for action in (lambda: self.env['_load_file_for_user'](self.cur, self.user, self.file['id']),
                           lambda: self.env['_authorize_upload'](self.cur, self.user,
                               dict(self.file, user_id=self.user['user_id']), write=True)):
                with self.subTest(label=label), self.assertRaises(Denied) as caught: action()
                self.assertEqual(caught.exception.status_code, 403)

    def test_files_view_denies_detail_list_and_media_before_file_query(self):
        self.context['permissions_override']['files.view'] = False
        for name, args in (('_load_file_for_user', (self.cur, self.user, self.file['id'])),
                           ('list_files', (self.context['id'], 200, None, self.user)),
                           ('list_worksite_media', (self.context['id'], self.user))):
            with self.subTest(endpoint=name), self.assertRaises(Denied) as caught:
                self.env[name](*args)
            self.assertEqual(caught.exception.detail, 'PERMISSION_REQUIRED:files.view')

    def test_foreign_upload_owner_denied_even_for_chef(self):
        upload = dict(self.file, user_id=uuid4())
        for role in ('USER', 'CHEF'):
            self.user['company_role'] = role
            for write in (False, True):
                with self.assertRaises(Denied) as caught:
                    self.env['_authorize_upload'](self.cur, self.user, upload, write=write)
                self.assertEqual(caught.exception.detail, 'UPLOAD_OWNER_REQUIRED')

    def test_own_upload_without_write_permission_denied(self):
        self.context['permissions_override']['files.add'] = False
        with self.assertRaises(Denied) as caught:
            self.env['_authorize_upload'](self.cur, self.user, dict(self.file, user_id=self.user['user_id']), write=True)
        self.assertEqual(caught.exception.detail, 'PERMISSION_REQUIRED:files.add')

    def test_link_scope_and_foreign_worksite_denied(self):
        context = self.permissions['worksite_context'](self.cur, self.user, self.context['id'])
        for kind in ('DEFECT', 'ACCEPTANCE'):
            self.cur.entity = dict(worksite_id=self.context['id'], area_scope={'labels': ['B']},
                                   status='OPEN', deleted_at=None)
            self.assertFalse(self.env['_entity_link_visible'](self.cur, self.user, context, kind, uuid4()))
            self.cur.entity['area_scope'] = {'labels': ['A']}
            self.assertTrue(self.env['_entity_link_visible'](self.cur, self.user, context, kind, uuid4()))
            self.cur.entity['worksite_id'] = uuid4()
            self.assertFalse(self.env['_entity_link_visible'](self.cur, self.user, context, kind, uuid4()))
        with self.assertRaises(Denied):
            self.env['_validate_link'](self.cur, self.user, context, self.context['id'], 'WORKSITE', uuid4())

    def test_unscoped_company_files_are_not_cross_company(self):
        self.file['worksite_id'] = None
        self.file['company_id'] = uuid4()
        with self.assertRaises(Denied):
            self.env['_load_file_for_user'](self.cur, self.user, self.file['id'])

    def test_business_read_endpoints_enforce_company_and_feature_permissions(self):
        for module, name, permission in [('acceptances', 'list_acceptances', 'acceptance.view'),
                                         ('defects', 'list_defects', 'defect.view')]:
            for mode in ('foreign', 'permission', 'valid'):
                self.setUp()
                if mode == 'foreign': self.user['company_id'] = uuid4()
                if mode == 'permission': self.context['permissions_override'][permission] = False
                env = guards.load(module, {name}, dict(self.permissions, transaction=lambda: self.cur))
                if mode == 'valid':
                    self.assertEqual(env[name](self.context['id'], self.user), {module: []})
                else:
                    with self.subTest(module=module, mode=mode), self.assertRaises(Denied):
                        env[name](self.context['id'], self.user)

    def test_btb_team_and_final_pdf_remain_restricted(self):
        context = self.permissions['worksite_context'](self.cur, self.user, self.context['id'])
        btb = dict(worksite_id=self.context['id'], team_id=uuid4(), status='DRAFT')
        class TeamCursor:
            def execute(inner, sql, *args): inner.sql = sql
            def fetchone(inner): return btb if 'FROM tb.btbs' in inner.sql else None
        cur = TeamCursor()
        self.assertFalse(self.env['_entity_link_visible'](cur, self.user, context, 'BTB', uuid4()))
        self.assertFalse(self.env['_entity_link_visible'](cur, self.user, context, 'BTB', uuid4(), 'BTB_FINAL_PDF'))

    def test_company_suspension_blocks_existing_portal_and_device_auth(self):
        now = datetime.now(timezone.utc)
        request = SimpleNamespace(cookies={'tb_portal_session': 'test'})
        for module, function in [('portal', '_load_session'), ('devices', 'current_device_user')]:
            for state in ('ACTIVE', 'DISABLED', 'DELETED', 'MISSING'):
                row = dict(self.user, status='ACTIVE', deleted_at=None, access_valid_until=None,
                    expires_at=now + timedelta(hours=1), revoked_at=None, device_status='ACTIVE',
                    session_id=uuid4(), device_id=uuid4(), company_status=state,
                    company_deleted_at=now if state == 'DELETED' else None)
                cur = guards.Cursor(row)
                env = guards.load(module, {function}, dict(transaction=lambda: cur, _now=lambda: now,
                    hash_token=lambda x: x, _bearer_token=lambda r: 'test',
                    _reported_app_info=lambda r: (None, None), SESSION_COOKIE='tb_portal_session',
                    require_active_company=self.permissions['require_active_company']))
                if state == 'ACTIVE':
                    self.assertEqual(env[function](request)['user_id'], self.user['user_id'])
                    self.assertEqual(len(cur.calls), 2)
                else:
                    with self.subTest(module=module, state=state), self.assertRaises(Denied) as caught:
                        env[function](request)
                    self.assertEqual(caught.exception.detail, 'COMPANY_DISABLED')
                    self.assertEqual(len(cur.calls), 1)

    def test_company_suspension_blocks_new_portal_and_device_login(self):
        now = datetime.now(timezone.utc)
        row = dict(self.user, status='ACTIVE', deleted_at=None, access_valid_until=None,
                   company_status='DISABLED', company_deleted_at=None,
                   used_at=None, revoked_at=None, expires_at=now + timedelta(hours=1))
        payload = SimpleNamespace(login='test', platform='ANDROID', key='test-only')
        for module, function, args in [('portal', 'login', (payload, None, None)),
                                       ('devices', 'device_login', (payload,))]:
            cur = guards.Cursor(row)
            extra = dict(transaction=lambda: cur, _find_login=lambda *a: row, _now=lambda: now,
                LoginRequest=object, DeviceLoginRequest=object, Response=object,
                datetime=datetime, hash_token=lambda x: x, random_token=lambda n: 'test-only',
                require_active_company=self.permissions['require_active_company'])
            env = guards.load(module, {function}, extra)
            with self.subTest(module=module), self.assertRaises(Denied) as caught:
                env[function](*args)
            self.assertEqual(caught.exception.detail, 'COMPANY_DISABLED')
            self.assertFalse(any('INSERT' in call[0] for call in cur.calls))


if __name__ == '__main__': unittest.main()

