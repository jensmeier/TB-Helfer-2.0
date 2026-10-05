"""Admin boundary regression checks with production functions and doubles."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
from uuid import uuid4
import unittest

base = Path(__file__).parent
spec = importlib.util.spec_from_file_location('guards', base / '132_verify_permission_guards.py')
guards = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guards)

class AdminBoundary(unittest.TestCase):
    def test_admin_creation_denied_before_database(self):
        env = guards.load('admin', ['admin_create_worksite'], dict(
            AdminWorksiteCreate=object, require_admin_csrf=None))
        with self.assertRaises(guards.HTTPException) as error:
            env['admin_create_worksite'](object(), {'user_id':uuid4()})
        self.assertEqual(error.exception.status_code, 403)

    def test_admin_list_denied(self):
        cur=guards.Cursor(None)
        env=guards.load('worksites',['list_worksites'],dict(transaction=lambda:cur))
        with self.assertRaises(guards.HTTPException):
            env['list_worksites']({'company_role':'ADMIN'})
        self.assertEqual(cur.calls, [])

    def test_old_admin_pages_redirect_without_content(self):
        env=guards.load('admin_web',['admin_worksite_page','admin_btb_page'],dict(
            require_web_admin=None, RedirectResponse=lambda **kwargs:kwargs))
        for name in ('admin_worksite_page','admin_btb_page'):
            result=env[name](uuid4(),object(),{})
            self.assertEqual(result,dict(url='/admin/v1/companies',status_code=302))

    def test_purge_requires_exact_target_and_confirmation(self):
        env=guards.load('admin_purge',['_confirm'],dict(
            PurgeConfirmation=object,_CONFIRM_TEXT='ENDGUELTIG LOESCHEN'))
        target=uuid4()
        for identifier,text in [(uuid4(),'ENDGUELTIG LOESCHEN'),(target,'ja'),(target,'')]:
            with self.assertRaises(guards.HTTPException):
                env['_confirm'](SimpleNamespace(confirm_id=identifier,confirm_text=text),target)
        env['_confirm'](SimpleNamespace(confirm_id=target,confirm_text='ENDGUELTIG LOESCHEN'),target)

    def test_company_ui_has_only_worksite_purge(self):
        source=(base.parent/'templates/v1_company_branding.html').read_text()
        self.assertNotIn('createWorksite',source)
        self.assertNotIn('/admin/v1/worksites/',source)
        self.assertIn('/api/v1/admin/purge/worksites/',source)
        self.assertIn("if(typed!=='ENDGUELTIG LOESCHEN')return",source)
        self.assertIn('data-worksite-name="{{ w.name }}"',source)

if __name__=='__main__':unittest.main()
