"""PORTAL-016 targeted local tests. No production DB or upstream weather calls."""
import ast
import importlib.util
from datetime import datetime, timedelta
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT/'tbserver'/f'{name}.py')
    result = importlib.util.module_from_spec(spec)
    sys.modules[name] = result
    spec.loader.exec_module(result)
    return result

times = module('btb_times')
before={'started_at':'2026-10-18T05:00:00+00:00','ended_at':'2026-10-18T15:00:00+00:00'}
after={'started_at':'2026-10-18T06:00:00+00:00','ended_at':'2026-10-18T16:00:00+00:00'}
p={'personalVon':'07:00','personalBis':'17:00','maschinenVon':'08:15','maschinenBis':'16:30','name':'Firma','unknown':{'keep':True}}
r=times.inherited_firm_times(p,before,after)
assert (r['personalVon'],r['personalBis'])==('08:00','18:00')
assert (r['maschinenVon'],r['maschinenBis'])==('08:15','16:30')
assert p['personalVon']=='07:00' and r['unknown']==p['unknown']
assert times.inherited_firm_times({'personalZeitbindung':'MANUAL','personalVon':'07:00'},before,after)['personalVon']=='07:00'
assert times.inherited_firm_times({'personalZeitbindung':'REPORT'},before,after)['personalVon']=='08:00'
assert times.inherited_firm_times({},before,after)['maschinenBis']=='18:00'
assert times.clock('2026-12-01T06:00:00Z')=='07:00'
assert times.clock('2026-10-18T07:00:00')=='07:00'
assert times.inherited_firm_times(r,after,{'started_at':None,'ended_at':None})['personalVon'] is None

assert times.preserve_entry_time_fields({'uhrzeitBis':'01:00'}, {'text':'edited'}, before)['uhrzeitBis']=='01:00'
assert times.preserve_entry_time_fields({'uhrzeitBis':'01:00'}, {'uhrzeitBis':None}, before)['uhrzeitBis'] is None
assert times.preserve_entry_time_fields({'personalZeitbindung':'REPORT','personalVon':'07:00','personalBis':'17:00'}, {'personalVon':'08:15','personalBis':'17:00'}, before)['personalZeitbindung']=='MANUAL'
weather = module('btb_weather')
assert [p.strftime('%H:%M') for p in weather.sample_times('2026-10-18T22:00','2026-10-19T06:00')]==['23:30','02:00','04:45']
assert weather.sample_times('2026-10-18T22:00','2026-10-19T06:00')[1].day==19
for end in ['2026-10-18T21:00','2026-10-21T22:00']:
    try: weather.sample_times('2026-10-18T22:00',end)
    except ValueError: pass
    else: raise AssertionError('invalid report span accepted')
calls=[]
def fake_get(service,params):
    calls.append((service,params))
    if service=='locations':
        return {'results':[{'name':'Heideck','admin1':'Bayern','country':'Deutschland','latitude':49.1,'longitude':11.1}]}
    day=datetime.fromisoformat(params['start_date']+'T00:00').replace(tzinfo=weather._BERLIN)
    dates=[day+timedelta(hours=i) for i in range(48)]
    return {'hourly':{'time':[d.timestamp() for d in dates],'temperature_2m':[12.5]*48,'cloud_cover':[85]*48,'precipitation':[0.3]*48,'weather_code':[61]*48}}
weather._get=fake_get
assert weather.locations('Heideck')[0]['label']=='Heideck, Bayern, Deutschland'
r=weather.weather_suggestion(49.1,11.1,'2020-01-01T22:00','2020-01-02T06:00')
assert calls[-1][0]=='archive' and calls[-1][1]['end_date']=='2020-01-02'
assert r['slots'][1]['uhrzeit']=='02:00' and r['slots'][0]['niederschlag']=='Regen (leicht)'
assert r['slots'][0]['bewoelkung']=='stark bewölkt' and r['slots'][0]['niederschlagMm']==0.3
assert r['source']=='AUTO'
assert weather.rain_label(None,None) is None  # only used when code/amount exist

# Execute production header mutation against a cursor double: inheritance is inside
# the existing version-checked transaction and emits versioned entry changes.
from contextlib import nullcontext
from copy import deepcopy
class HttpError(Exception):
    def __init__(self,status,detail): self.status=status;self.detail=detail
class Cursor:
    def __init__(self): self.one=None;self.sql=[];self.written=[]
    def __enter__(self): return self
    def __exit__(self,*args): return False
    def execute(self,sql,args):
        self.sql.append(sql)
        if sql.startswith('UPDATE tb.btbs SET'):
            self.one={**before,**after,'version':4,'id':'btb'}
        elif sql.startswith('UPDATE tb.btb_entries SET'):
            self.written.append(args);self.one={'version':8}
    def fetchone(self): return self.one
    def fetchall(self): return [{'id':'firm','payload':deepcopy(p)}]
cur=Cursor()
class Conn:
    def cursor(self): return cur
btb={**before,'id':'btb','version':3,'worksite_id':'ws','team_id':'team'}
events=[];guards=[]
namespace={'Depends':lambda x:None,'require_csrf':None,'mutation_context':None,
 'transaction':lambda:nullcontext(Conn()),'begin_mutation':lambda *a,**k:None,
 '_load_btb':lambda *a,**k:btb,'worksite_context':lambda *a:{'company_id':'company'},
 'require_permission':lambda *a:guards.append('permission'),'_assert_team_member':lambda *a:guards.append('team'),
 'require_participant_writable':lambda *a,**k:guards.append('participant'),
 'HTTPException':HttpError,'prepare_btb_content_change':lambda *a:[],
 'Jsonb':lambda x:x,'inherited_firm_times':times.inherited_firm_times,
 'emit_change':lambda *a,**k:events.append(k),'finish_mutation':lambda *a,**k:None,
 'remove_storage_keys':lambda *a:None}
tree=ast.parse((ROOT/'tbserver'/'btb.py').read_text())
header=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='update_btb_header');header.decorator_list=[]
code=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),header],type_ignores=[])
exec(compile(ast.fix_missing_locations(code),'production_header','exec'),namespace)
class Header:
    base_version=3
    def model_dump(self,**kw): return after
namespace['update_btb_header']('btb',Header(),{'user_id':'actor'},None)
assert guards==['permission','team','participant'] and len(cur.written)==1
assert cur.written[0][0]['personalVon']=='08:00' and cur.written[0][0]['maschinenVon']=='08:15'
assert events[0]['entity_type']=='BTB_ENTRY' and events[0]['entity_version']==8
assert events[1]['entity_type']=='BTB' and events[1]['entity_version']==4
Header.base_version=2
try: namespace['update_btb_header']('btb',Header(),{'user_id':'actor'},None)
except HttpError as error: assert error.status==409
else: raise AssertionError('stale header accepted')

# Run actual production PDF section conversion (not a copied formatter).
pdf=module('btb_pdf_android_style')
snapshot={'btb':{},'entries':[{'id':'a','section':'ARBEIT','payload':{'uhrzeit':'22:00','uhrzeitBis':'01:00','text':'Nachtschicht','kmVon':45.021}}]}
lines,_,_=pdf._section_lines(snapshot,None,{})
assert '22:00–01:00' in lines['ARBEIT'][0].text
snapshot['entries'][0]['payload'].pop('uhrzeitBis')
assert '22:00' in pdf._section_lines(snapshot,None,{})[0]['ARBEIT'][0].text

# Syntax checks cover all runtime Python files touched by the change.
for name in ('btb.py','btb_times.py','btb_weather.py','btb_pdf_android_style.py'):
    ast.parse((ROOT/'tbserver'/name).read_text())
subprocess.run(['node',str(ROOT/'checks'/'152_portal_btb_input_regression.js')],check=True)
print('PORTAL-016 Python inheritance/weather/PDF + JavaScript workflow checks PASS (no NAS/DB/browser E2E)')
