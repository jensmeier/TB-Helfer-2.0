"""Production sync generators with auth/DB doubles; no live HTTP server.

Explicit worksite.view authorization is independently covered by
server/tests/test_sync_1121_scope.py; this fixture isolates reauthentication.
"""
import ast, asyncio, json, unittest
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

class Denied(Exception):
    def __init__(self,status_code,detail='denied'):self.status_code=status_code

class SyncChecks(unittest.IsolatedAsyncioTestCase):
    def setup_env(self, head=1, failure=None, fail_after=1):
        state={'checks':0,'reads':0,'waited':False}
        class Hub:
            async def head(self):return 0
            async def wait_for_head(self,*args):state['waited']=True;return head
        def refresh(request,worksite):
            state['checks']+=1
            if failure and state['checks']>=fail_after:raise Denied(failure)
            return {'user_id':'fresh'}
        def visible(user,**kwargs):
            self.assertEqual(user,{'user_id':'fresh'})
            state['reads']+=1
            return [{'change_id':1}]
        async def query(fn,*args,**kwargs):return fn(*args,**kwargs)
        class Tx:
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def cursor(self):return self
        env=dict(Any=Any,UUID=UUID,Request=Any,Query=lambda **kw:kw.get('default'),
                 Depends=lambda x:None,current_portal_user=None,HTTPException=Denied,
                 _MAX_WAIT_SECONDS=30,_CHANGE_HUB=Hub(),_run_sync_query=query,
                 _refresh_sync_user=refresh,_visible_changes=visible,_entity_types=lambda x:[],
                 transaction=Tx,worksite_context=lambda *args:None,
                 require_permission=lambda *args:None,json=json,
                 StreamingResponse=lambda stream,**kw:stream)
        tree=ast.parse((Path(__file__).parents[1]/'tbserver/sync.py').read_text())
        tree.body=[n for n in tree.body if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef)) and n.name in {'wait_changes','live_events'}]
        for n in tree.body:n.decorator_list=[]
        exec(compile(tree,'sync','exec'),env)
        return env,state

    async def test_refresh_uses_credentials_and_current_worksite(self):
        calls=[]
        class Tx:
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def cursor(self):return self
        request=object(); worksite=uuid4(); fresh={'user_id':'fresh'}
        def auth(req):
            self.assertIs(req,request); calls.append('auth'); return fresh
        def context(cur,user,target):
            self.assertIs(user,fresh); self.assertEqual(target,worksite)
            calls.append('context'); raise Denied(403)
        env=dict(Request=Any,UUID=UUID,Any=Any,current_portal_user=auth,
                 transaction=Tx,worksite_context=context,
                 require_permission=lambda *args:None)
        tree=ast.parse((Path(__file__).parents[1]/'tbserver/sync.py').read_text())
        tree.body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='_refresh_sync_user']
        exec(compile(tree,'sync','exec'),env)
        with self.assertRaises(Denied):env['_refresh_sync_user'](request,worksite)
        self.assertEqual(calls,['auth','context'])
        self.assertIs(env['_refresh_sync_user'](request,None),fresh)

    async def test_stream_denied_before_data_or_keepalive(self):
        for head in (0,1):
            for status in (401,403,404):
                env,state=self.setup_env(head,status)
                stream=env['live_events'](object(),uuid4(),0,{})
                with self.assertRaises(StopAsyncIteration):await anext(stream)
                self.assertEqual(state['reads'],0)

    async def test_stream_revoked_during_query(self):
        env,state=self.setup_env(1,403,2)
        stream=env['live_events'](object(),uuid4(),0,{})
        with self.assertRaises(StopAsyncIteration):await anext(stream)
        self.assertEqual(state['reads'],1)

    async def test_stream_valid_delivery_then_revoked(self):
        env,state=self.setup_env(1,403,3)
        stream=env['live_events'](object(),uuid4(),0,{})
        self.assertIn('event: change',await anext(stream))
        with self.assertRaises(StopAsyncIteration):await anext(stream)

    async def test_longpoll_denied_even_on_empty_timeout(self):
        for head in (0,1):
            env,state=self.setup_env(head,403)
            with self.assertRaises(Denied):await env['wait_changes'](object(),0,200,None,None,25,{})
            self.assertTrue(state['waited']);self.assertEqual(state['reads'],0)

    async def test_longpoll_query_revocation(self):
        env,state=self.setup_env(1,403,2)
        with self.assertRaises(Denied):await env['wait_changes'](object(),0,200,None,None,25,{})

    async def test_longpoll_valid_empty_and_data(self):
        for head in (0,1):
            env,state=self.setup_env(head)
            result=await env['wait_changes'](object(),0,200,None,None,25,{})
            self.assertEqual(result['next_after'],head)
            self.assertEqual(len(result['changes']),head)

if __name__=='__main__':unittest.main()
