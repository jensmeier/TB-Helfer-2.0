"""PORTAL-017 focused production-function tests; no live DB/browser claim."""
import ast
from contextlib import nullcontext
from copy import deepcopy
import importlib.util
import math
from pathlib import Path
import subprocess
import sys
from uuid import UUID
ROOT=Path(__file__).resolve().parents[1]
def module(name):
 spec=importlib.util.spec_from_file_location(name,ROOT/'tbserver'/f'{name}.py');m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
meta=module('btb_photo_meta');pdf=module('btb_pdf_android_style')
id1='11111111-1111-4111-8111-111111111111';id2='22222222-2222-4222-8222-222222222222'
assert meta.merge_btb_photo_metadata({'lat':47,'lon':10},{'lat':48,'lon':11})['lat']==48
assert 'lat' not in meta.merge_btb_photo_metadata({'lat':47,'lon':10},{'lat':None,'lon':None})
assert meta.normalize_btb_photo_override({'btbEntryId':id1})['btbEntryId']==id1
assert 'btbEntryId' not in meta.normalize_photo_metadata({'btbEntryId':id1})  # binding is never global file metadata
assert pdf._report_date_label({'started_at':'2026-10-18T17:00:00Z','ended_at':'2026-10-19T05:00:00Z'})=='18/19.10.26'
assert pdf._report_date_label({'started_at':'2026-12-31T18:00:00Z','ended_at':'2027-01-01T06:00:00Z'})=='31.12.26/01.01.27'
assert pdf._time_hm('2026-10-18T17:00:00Z')=='19:00'
class HttpError(Exception):
 def __init__(self,status,detail):self.status=status;self.detail=detail

def functions(path,names,namespace):
 tree=ast.parse(path.read_text());nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names]
 for n in nodes:n.decorator_list=[]
 code=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),*nodes],type_ignores=[])
 exec(compile(ast.fix_missing_locations(code),str(path),'exec'),namespace)

# Canonical binding resolver validates the entry *within this BTB*, not by caption.
class EntryCursor:
 def execute(self,sql,args):self.sql=sql;self.args=args
 def fetchone(self):
  if 'section=\'FIRMA\'' in self.sql:return {'name':'Schwerbau'}
  if self.args[1]!='btb1' or str(self.args[0])!=id1:return None
  return {'id':id1,'section':'ARBEIT','payload':{'firmaId':'firm','text':'Strail defekt','worksiteNodeKey':'node'}}
ns={'UUID':UUID,'HTTPException':HttpError}
functions(ROOT/'tbserver'/'btb_entry_photos.py',{'entry_photo_context'},ns)
ns['PHOTO_TYPES']={'ARBEIT':'FOTODOKUMENTATION'}
cur=EntryCursor();binding=ns['entry_photo_context'](cur,'btb1',id1)
assert binding['btbEntryId']==id1 and binding['firma']=='Schwerbau' and binding['worksiteNodeKey']=='node'
for target_btb,target_id in [('other-btb',id1),('btb1',id2),('btb1','invalid')]:
 try:ns['entry_photo_context'](cur,target_btb,target_id)
 except HttpError as e:assert e.status==400
 else:raise AssertionError('foreign/missing entry accepted')

# Snapshot and PDF transport must not resurrect cleared base GPS values.
ns_docs={'canonical_photo_metadata':meta.canonical_photo_metadata,'merge_btb_photo_metadata':meta.merge_btb_photo_metadata,'_absolute_storage_path':lambda value:value}
functions(ROOT/'tbserver'/'btb_documents.py',{'effective_btb_photo_metadata','snapshot_photo_metadata','photo_files_for_render'},ns_docs)
photo={'id':'file','gps_lat':47,'gps_lon':10,'photo_metadata':{'lat':47,'lon':10},'link_metadata':{'lat':None,'lon':None,'btbEntryId':id1},'storage_key':'image.jpg'}
snap=ns_docs['snapshot_photo_metadata']([photo])[0];rendered=ns_docs['photo_files_for_render']([photo])[0]
assert snap['gps_lat'] is None and rendered['gps_lon'] is None and snap['photo_metadata']['btbEntryId']==id1
# Bound images reference the real work line instead of creating a duplicate event.
line_snapshot={'btb':{},'entries':[{'id':id1,'section':'ARBEIT','payload':{'text':'Strail defekt'}}], 'photos':[{'id':'p','sha256':'x','photo_metadata':{'btbEntryId':id1,'typ':'FOTODOKUMENTATION','kurztext':'Strail defekt'}}]}
lines=pdf._section_lines(line_snapshot,None,{})[0]['ARBEIT']
assert len(lines)==1 and '[Bild 1]' in lines[0].text

# Actual versioned photo PATCH: move, clear GPS, preserve other link fields, conflicts.
class PhotoCursor:
 def __enter__(self):return self
 def __exit__(self,*args):return False
 def execute(self,sql,args):
  self.sql=sql
  if sql.lstrip().startswith('SELECT l.link_metadata'):self.one=deepcopy(current)
  elif sql.lstrip().startswith('UPDATE tb.file_links'):
   self.written=deepcopy(args[0]);self.one={'version':4,'link_metadata':self.written}
  elif sql.startswith('UPDATE tb.btbs'):self.one={'version':8}
 def fetchone(self):return self.one
cur=PhotoCursor()
class Conn:
 def cursor(self):return cur
current={'link_version':3,'link_metadata':{'btbEntryId':id1,'objektBereich':'preserve'},'original_name':'photo.jpg','captured_at':None,'gps_lat':47.0,'gps_lon':10.0,'gps_accuracy_m':None,'photo_metadata':{'lat':47,'lon':10,'kurztext':'old'}}
ns={'Depends':lambda x:None,'require_csrf':None,'mutation_context':None,'math':math,'UUID':UUID,'HTTPException':HttpError,
 'normalize_btb_photo_override':meta.normalize_btb_photo_override,'canonical_photo_metadata':meta.canonical_photo_metadata,'merge_btb_photo_metadata':meta.merge_btb_photo_metadata,
 'transaction':lambda:nullcontext(Conn()),'begin_mutation':lambda *a,**k:None,'_load_btb':lambda *a,**k:{'worksite_id':'ws','team_id':'team'},'worksite_context':lambda *a:{'company_id':'company'},
 'require_permission':lambda *a:None,'_assert_team_member':lambda *a:None,'require_participant_writable':lambda *a,**k:None,
 'entry_photo_context':lambda c,b,e:{**binding,'btbEntryId':str(e),'typ':'ABNAHMEN'},'_validate_worksite_node_key':lambda *a:None,
 'prepare_btb_content_change':lambda *a:[],'Jsonb':lambda x:x,'emit_change':lambda *a,**k:None,'audit':lambda *a,**k:None,
 'finish_mutation':lambda *a,**k:None,'remove_storage_keys':lambda *a:None}
functions(ROOT/'tbserver'/'btb.py',{'update_btb_photo'},ns)
class Patch:
 base_version=3
 photo_metadata={'btbEntryId':id2,'lat':48,'lon':11,'km':78.577}
r=ns['update_btb_photo']('btb1','file',Patch(),{'user_id':'actor'},None)
assert r['link_version']==4 and r['photo_metadata']['btbEntryId']==id2 and r['photo_metadata']['typ']=='ABNAHMEN'
assert r['photo_metadata']['km']==78.577 and r['photo_metadata']['lat']==48 and cur.written['objektBereich']=='preserve'
assert current['gps_lat']==47 and current['link_metadata']['btbEntryId']==id1  # global file untouched
Patch.photo_metadata={'btbEntryId':None,'lat':None,'lon':None}
r=ns['update_btb_photo']('btb1','file',Patch(),{'user_id':'actor'},None)
assert 'btbEntryId' not in r['photo_metadata'] and 'lat' not in r['photo_metadata'] and 'lon' not in r['photo_metadata']
for gps in [{'lat':91,'lon':10},{'lat':float('nan'),'lon':10},{'lat':True,'lon':10},{'lat':None,'lon':10}]:
 Patch.photo_metadata=gps
 try:ns['update_btb_photo']('btb1','file',Patch(),{'user_id':'actor'},None)
 except HttpError as e:assert e.status==400
 else:raise AssertionError('invalid GPS accepted')
Patch.photo_metadata={'km':78};Patch.base_version=2
try:ns['update_btb_photo']('btb1','file',Patch(),{'user_id':'actor'},None)
except HttpError as e:assert e.status==409
else:raise AssertionError('stale photo patch accepted')
for name in ('btb','btb_documents','btb_entry_photos','btb_photo_meta','files','media_gallery','btb_pdf_android_style'):
 ast.parse((ROOT/'tbserver'/f'{name}.py').read_text())
subprocess.run(['node',str(ROOT/'checks'/'153_entry_photo_shift_regression.js')],check=True)
print('PORTAL-017 Python binding validation/versioned photo move/GPS clear/isolation/PDF labels PASS (controlled doubles; no DB/NAS E2E)')
