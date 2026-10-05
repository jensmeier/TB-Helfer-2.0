"""Audit existing auth and reproduce stream limitation; no live users/DB.
The stream test documents a known defect, not a security acceptance gate.
"""
import ast, asyncio, importlib.util, json, unittest
from pathlib import Path
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from uuid import uuid4
from zoneinfo import ZoneInfo
from datetime import time
base=Path(__file__).parent
spec=importlib.util.spec_from_file_location('guards',base/'132_verify_permission_guards.py')
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)

class Audit(unittest.TestCase):
    def test_auth_validity_boundaries_and_state_changes(self):
        now=datetime(2026,10,5,16,29,tzinfo=timezone.utc)
        request=SimpleNamespace(cookies={'tb_portal_session':'same-token','tb_admin_session':'same-token'})
        for module,fn in [('portal','_load_session'),('admin_session','current_admin_session_user'),('devices','current_device_user')]:
            row=dict(user_id=uuid4(),device_id=uuid4(),session_id=uuid4(),company_role='ADMIN',
                status='ACTIVE',deleted_at=None,access_valid_until=None,expires_at=now+timedelta(hours=1),
                revoked_at=None,device_status='ACTIVE')
            cur=g.Cursor(row)
            env=g.load(module,[fn],dict(transaction=lambda:cur,_now=lambda:now,hash_token=lambda x:x,
                _bearer_token=lambda r:'same-token',_reported_app_info=lambda r:(None,None),
                SESSION_COOKIE='tb_portal_session',ADMIN_SESSION_COOKIE='tb_admin_session'))
            for expiry in (None,now+timedelta(microseconds=1)):
                row['access_valid_until']=expiry
                self.assertIn('auth_kind',env[fn](request))
            for expiry in (now,now-timedelta(microseconds=1)):
                row['access_valid_until']=expiry
                with self.assertRaises(g.HTTPException) as e:env[fn](request)
                self.assertEqual(e.exception.detail,'USER_EXPIRED')
            row['access_valid_until']=None
            row['status']='DISABLED'
            with self.assertRaises(g.HTTPException) as e:env[fn](request)
            self.assertEqual(e.exception.detail,'USER_DISABLED')
            row['status']='ACTIVE';row['deleted_at']=now
            with self.assertRaises(g.HTTPException):env[fn](request)
            row['deleted_at']=None
            if module=='devices':
                for field in ('device_status','revoked_at'):
                    old=row[field];row[field]='REVOKED' if field=='device_status' else now
                    with self.assertRaises(g.HTTPException) as e:env[fn](request)
                    self.assertEqual(e.exception.detail,'DEVICE_REVOKED');row[field]=old
            else:
                row['expires_at']=now
                with self.assertRaises(g.HTTPException) as e:env[fn](request)
                self.assertIn('SESSION_EXPIRED',e.exception.detail)
            cur.row=None
            with self.assertRaises(g.HTTPException) as e:env[fn](request)
            self.assertEqual(e.exception.status_code,401)

    def test_german_valid_through_summer_and_winter(self):
        env=g.load('admin',['_valid_until_from_through'],dict(date=datetime,time=time,datetime=datetime,
            timedelta=timedelta,timezone=timezone,_BERLIN_TZ=ZoneInfo('Europe/Berlin')))
        for day in [datetime(2026,7,1).date(),datetime(2026,12,1).date()]:
            expiry=env['_valid_until_from_through'](day).astimezone(ZoneInfo('Europe/Berlin'))
            self.assertEqual(expiry.date(),day+timedelta(days=1))
            self.assertEqual((expiry.hour,expiry.minute),(0,1))

    def test_reproduce_open_stream_continues_after_revocation(self):
        state={'allowed':True,'checks':0}
        def context(*args):
            state['checks']+=1
            if not state['allowed']:raise g.HTTPException(403,'USER_DISABLED')
        class Hub:
            async def wait_for_head(self,*args):return 1
        async def query(*args):return [{'change_id':1,'entity_id':'example'}]
        env=g.load('sync',['live_events'],dict(Query=lambda **kw:kw.get('default'),
            transaction=lambda:g.Cursor(None),worksite_context=context,_CHANGE_HUB=Hub(),
            _run_sync_query=query,_changes_for_worksite=object(),json=json,
            StreamingResponse=lambda stream,**kw:stream))
        stream=env['live_events'](uuid4(),0,{'company_role':'USER'})
        state['allowed']=False
        async def run():
            event=await anext(stream)
            await stream.aclose()
            return event
        event=asyncio.run(run())
        self.assertIn('event: change',event)
        self.assertEqual(state['checks'],1)

if __name__=='__main__':unittest.main()
