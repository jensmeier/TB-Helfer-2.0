"""Fault injection against production finalize and real filesystem helpers; DB double."""
import ast,contextlib,hashlib,json,logging,os,tempfile,time
from pathlib import Path
from unittest.mock import patch
import sys
root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[3]/'server/tbserver'
def load(file,names,env):
 t=ast.parse((root/file).read_text());nodes=[n for n in t.body if isinstance(n,ast.FunctionDef) and n.name in names]
 for n in nodes:n.decorator_list=[]
 module=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),*nodes],type_ignores=[])
 exec(compile(ast.fix_missing_locations(module),str(root/file),'exec'),env);return env
with tempfile.TemporaryDirectory() as d:
 final,temp,backup=[Path(d)/n for n in ('final.pdf','new.tmp','rollback.pdf')]
 final.write_bytes(b'PREVIOUS_PDF')
 state={'committed':False,'failed_cleanup':False,'db_status':'ACTIVE'}
 btb=dict(id='b',worksite_id='w',team_id='t',status='ACTIVE',version=1)
 class Cursor:
  def __enter__(self):return self
  def __exit__(self,*a):pass
  def execute(self,sql,args):
   if sql.startswith("UPDATE tb.btbs SET status = 'FINALIZED'"):state['db_status']='FINALIZED'
  def fetchone(self):return dict(btb,status=state['db_status'],version=2)
 class Conn:
  def cursor(self):return Cursor()
 @contextlib.contextmanager
 def transaction():
  yield Conn()
  state['committed']=True
 noop=lambda *a,**k:None
 def render(path,**k):path.write_bytes(b'NEW_PDF')
 def upsert(*a,**k):state['db_pdf_hash']=k['sha256'];return {'id':'pdf'},'UPDATE'
 env=dict(Path=Path,os=os,hashlib=hashlib,json=json,perf_counter=time.perf_counter,Depends=lambda x:None,
 require_csrf=None,mutation_context=None,transaction=transaction,begin_mutation=noop,replay_payload=lambda x:x,
 _load_btb=lambda *a,**k:btb,worksite_context=lambda *a:{'company_id':'c'},require_permission=noop,
 _assert_team_member=noop,require_team_lead=noop,_team=lambda *a:{'active_btb_id':'b'},mark_lead_finished=noop,
 unfinished_participants=lambda *a,**k:[],load_btb_photo_files=lambda *a:[],_snapshot=lambda *a,**k:{'btb':btb},
 load_original_snapshot=lambda *a:None,final_pdf_paths=lambda **k:(final,temp,backup),_pdf_context=lambda *a:{},
 photo_files_for_render=lambda *a:[],render_btb_pdf=render,upsert_final_pdf_file=upsert,Jsonb=lambda x:x,
 emit_change=noop,audit=noop,PDF_RENDERER_VERSION='test',public_final_pdf=lambda *a,**k:{},finish_mutation=noop,
 logger=logging.getLogger('audit'))
 load('btb_documents.py',{'install_pdf_atomically','rollback_pdf_install','finish_pdf_install','hash_file'},env)
 load('btb.py',{'finalize_btb'},env)
 original_unlink=Path.unlink
 def unlink(path,*a,**k):
  if path==backup and state['committed'] and not state['failed_cleanup']:
   state['failed_cleanup']=True;raise OSError('injected post-commit cleanup failure')
  return original_unlink(path,*a,**k)
 with patch.object(Path,'unlink',unlink):
  try:env['finalize_btb']('b',None,{'user_id':'lead'},None)
  except OSError:pass
  else:raise AssertionError('expected cleanup failure')
 assert state['committed'] and state['db_status']=='FINALIZED'
 assert final.read_bytes()==b'PREVIOUS_PDF'
 assert state['db_pdf_hash']!=hashlib.sha256(final.read_bytes()).hexdigest()
 print(json.dumps({'reproduced':True,'db_commit_completed':True,'db_status':state['db_status'],
  'filesystem_reverted_to_previous_pdf':True,'db_file_hash_mismatch':True,
  'production_finalize_and_filesystem_helpers':True,'database_double':True,'nas_e2e':False}))
