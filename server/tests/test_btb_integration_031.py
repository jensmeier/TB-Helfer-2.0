"""Production-function regressions with controlled DB boundaries; no NAS/Android E2E."""
import ast
import contextlib
import unittest
from pathlib import Path
from typing import Any
from uuid import UUID
ROOT=Path(__file__).resolve().parents[1]/'tbserver'
class HTTPException(Exception):
 def __init__(self,status_code,detail):self.status_code,self.detail=status_code,detail

def definitions(file,names,env):
 tree=ast.parse((ROOT/file).read_text());nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names]
 for n in nodes:n.decorator_list=[]
 exec(compile(ast.fix_missing_locations(ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),*nodes],type_ignores=[])),file,'exec'),env)
 return env

class MatchingTests(unittest.TestCase):
 def setUp(self):
  self.match=definitions('btb_entry_photos.py',{'matching_entry_context'}, {})['matching_entry_context']
  self.entry=dict(btbEntryId='e1',typ='FOTODOKUMENTATION',firma='Pittrich',worksiteNodeKey='MHH-W550',kurztext='Schwellenwechsel')
  self.meta={k:v for k,v in self.entry.items() if k!='btbEntryId'}
 def test_all_matching_photos_bind_to_the_same_unique_entry(self):
  for _ in range(7):self.assertEqual(self.match([self.entry],self.meta)['btbEntryId'],'e1')
 def test_whitespace_and_case(self):
  self.meta['kurztext']='  SCHWELLENWECHSEL  '
  self.assertIsNotNone(self.match([self.entry],self.meta))
 def test_other_company_is_not_linked(self):
  self.meta['firma']='Lst Werner';self.assertIsNone(self.match([self.entry],self.meta))
 def test_other_node_is_not_linked(self):
  self.meta['worksiteNodeKey']='HOFHAM';self.assertIsNone(self.match([self.entry],self.meta))
 def test_other_section_is_not_linked(self):
  self.meta['typ']='ABNAHMEN';self.assertIsNone(self.match([self.entry],self.meta))
 def test_ambiguous_caption_is_not_linked(self):
  self.assertIsNone(self.match([self.entry,{**self.entry,'btbEntryId':'e2'}],self.meta))
 def test_existing_binding_preserved(self):
  self.meta['btbEntryId']='other';self.assertIsNone(self.match([self.entry],self.meta))
 def test_explicit_unlink_preserved(self):
  self.meta['btbEntryId']=None;self.assertIsNone(self.match([self.entry],self.meta))
 def test_empty_caption_is_not_linked(self):
  self.meta['kurztext']='';self.assertIsNone(self.match([self.entry],self.meta))

class ReopenTests(unittest.TestCase):
 def setUp(self):
  self.row=dict(id='old',worksite_id='site',team_id='team',status='FINALIZED',version=4)
  self.pointer='new-current-shift';self.writes=[];self.events=[];self.lead=True;self.receipt=None
  test=self
  class Cursor:
   def __enter__(self):return self
   def __exit__(self,*a):pass
   def execute(self,sql,args):
    test.writes.append((sql,args))
    if sql.startswith('UPDATE tb.btbs'):test.row={**test.row,'status':'REWORK','version':test.row['version']+1}
   def fetchone(self):return test.row
  class Conn:
   def cursor(self):return Cursor()
  def require_lead(*a):
   if not test.lead:raise HTTPException(403,'BTB_TEAM_LEAD_REQUIRED')
  env=dict(UUID=UUID,Depends=lambda x:None,require_csrf=None,mutation_context=None,transaction=lambda:contextlib.nullcontext(Conn()),begin_mutation=lambda *a,**k:self.receipt,replay_payload=lambda x:x,_load_btb=lambda *a,**k:self.row,worksite_context=lambda *a:{'company_id':'company'},require_permission=lambda *a:None,_assert_team_member=lambda *a:None,require_team_lead=require_lead,HTTPException=HTTPException,emit_change=lambda *a,**kw:self.events.append(kw),audit=lambda *a,**k:None,finish_mutation=lambda *a,**k:None)
  self.reopen=definitions('btb.py',{'reopen_btb'},env)['reopen_btb']
 def test_reopen_keeps_other_active_shift(self):
  row=self.reopen('old',{'user_id':'lead'},None)
  self.assertEqual((row['status'],row['version']),('REWORK',5));self.assertEqual(self.pointer,'new-current-shift')
  self.assertFalse(any('UPDATE tb.teams' in sql for sql,_ in self.writes));self.assertEqual(self.events[0]['entity_version'],5)
 def test_helper_cannot_reopen(self):
  self.lead=False
  with self.assertRaises(HTTPException) as e:self.reopen('old',{'user_id':'helper'},None)
  self.assertEqual(e.exception.status_code,403);self.assertEqual(self.writes,[])
 def test_retry_reuses_receipt(self):
  self.receipt={'status':'REWORK','version':5};self.assertEqual(self.reopen('old',{},None),self.receipt);self.assertEqual(self.writes,[])
 def test_already_rework_is_idempotent(self):
  self.row['status']='REWORK';self.reopen('old',{'user_id':'lead'},None);self.assertEqual(self.writes,[])
 def test_active_report_is_not_reopened(self):
  self.row['status']='ACTIVE'
  with self.assertRaises(HTTPException) as e:self.reopen('old',{'user_id':'lead'},None)
  self.assertEqual(e.exception.status_code,409)

class AdditionalTests(unittest.TestCase):
 def test_unknown_colon_prefix_is_not_a_company(self):
  import sys
  sys.path.insert(0,str(ROOT.parent))
  from tbserver.btb_pdf_android_style import _strip_company_prefix
  self.assertEqual(_strip_company_prefix('1stab 13:02',['Pittrich']),(None,'1stab 13:02'))
  self.assertEqual(_strip_company_prefix('Hinweis: Pflug defekt',['Pittrich']),(None,'Hinweis: Pflug defekt'))
  self.assertEqual(_strip_company_prefix('Pittrich: Schwellenwechsel',['Pittrich']),('Pittrich','Schwellenwechsel'))
 def test_catalogue_link_clears_do_not_restore_old_gps(self):
  import sys
  sys.path.insert(0,str(ROOT.parent))
  from tbserver.btb_photo_meta import effective_catalog_photo_metadata
  row=dict(original_name='x.jpg',gps_lat=47,gps_lon=10,photo_metadata={'firma':'Alt','kurztext':'Alt','lat':47,'lon':10},link_metadata={'firma':'Lst Werner','kurztext':'Schwellenwechsel','lat':None,'lon':None})
  meta=effective_catalog_photo_metadata(row,True)
  self.assertEqual(meta['firma'],'Lst Werner');self.assertEqual(meta['kurztext'],'Schwellenwechsel')
  self.assertNotIn('lat',meta);self.assertNotIn('lon',meta)
 def test_explicit_unlink_is_visible_to_other_devices(self):
  import sys
  sys.path.insert(0,str(ROOT.parent))
  from tbserver.btb_photo_meta import merge_btb_photo_metadata
  meta=merge_btb_photo_metadata({'kurztext':'Schwellenwechsel'},{'btbEntryId':None})
  self.assertTrue(meta['entryBindingCleared']);self.assertNotIn('btbEntryId',meta)
  rebound=merge_btb_photo_metadata(meta,{'btbEntryId':'11111111-1111-4111-8111-111111111111'})
  self.assertNotIn('entryBindingCleared',rebound)
 def test_distinct_company_ids_do_not_match_by_display_name(self):
  match=definitions('btb_entry_photos.py',{'matching_entry_context'}, {})['matching_entry_context']
  entry=dict(btbEntryId='e1',typ='FOTODOKUMENTATION',firma='Firma',firmaId='a',kurztext='Schwellenwechsel')
  self.assertIsNone(match([entry],dict(typ='FOTODOKUMENTATION',firma='Firma',firmaId='b',kurztext='Schwellenwechsel')))
 def test_helper_finished_is_read_only_but_lead_can_edit(self):
  env=dict(HTTPException=HTTPException,is_team_lead=lambda *a:False,participant_status=lambda *a,**k:{'status':'FINISHED'},WORKING='WORKING',FINISHED='FINISHED',NOT_PARTICIPATING='NOT_PARTICIPATING')
  fn=definitions('btb_participants.py',{'require_participant_writable'},env)['require_participant_writable']
  with self.assertRaises(HTTPException):fn(None,user={'user_id':'helper'},btb_id='b',team_id='t')
  env['is_team_lead']=lambda *a:True
  fn(None,user={'user_id':'helper'},btb_id='b',team_id='t')

class ReworkEditingTests(unittest.TestCase):
 def setUp(self):
  self.team={'active_btb_id':'new-shift'}
  test=self
  class Cursor:
   def execute(self,*args):pass
   def fetchone(self):return test.team
  self.cur=Cursor()
  self.env=definitions('btb_documents.py',{'_assert_active_for_edit','prepare_btb_content_change'},dict(HTTPException=HTTPException,invalidate_final_pdf=lambda cur,btb:['old-final.pdf']))
 def test_detached_rework_can_be_edited_and_invalidates_final_pdf(self):
  self.assertEqual(self.env['prepare_btb_content_change'](self.cur,dict(id='old',team_id='t',status='REWORK')),['old-final.pdf'])
  self.assertEqual(self.team['active_btb_id'],'new-shift')
 def test_stale_active_report_cannot_bypass_active_guard(self):
  with self.assertRaises(HTTPException) as error:self.env['prepare_btb_content_change'](self.cur,dict(id='old',team_id='t',status='ACTIVE'))
  self.assertEqual(error.exception.detail,'BTB_NOT_ACTIVE')
 def test_deleted_report_is_not_editable(self):
  with self.assertRaises(HTTPException) as error:self.env['prepare_btb_content_change'](self.cur,dict(id='old',team_id='t',status='DELETED'))
  self.assertEqual(error.exception.detail,'BTB_NOT_EDITABLE')

if __name__=='__main__':unittest.main()
