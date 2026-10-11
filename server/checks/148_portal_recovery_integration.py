"""Isolated PostgreSQL integration gate. Never run against a populated database."""
import os
import sys
from pathlib import Path
from uuid import uuid4

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import psycopg
from psycopg.rows import dict_row
from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
from starlette.requests import Request
from jinja2 import Environment, FileSystemLoader, select_autoescape

assert os.environ.get('PORTAL_TEST_ISOLATED')=='YES', 'isolated test database required'
conn=psycopg.connect(host=os.environ['DB_HOST'],dbname=os.environ['DB_NAME'],user=os.environ['DB_USER'],password=os.environ['DB_PASSWORD'],autocommit=True,row_factory=dict_row)
with conn.cursor() as cur:
    cur.execute("SELECT to_regclass('tb.users') AS existing")
    assert cur.fetchone()['existing'] is None, 'Refusing nonempty schema'
    for path in sorted((Path(__file__).resolve().parents[1]/'v1_migrations').glob('*.sql')):
        cur.execute(path.read_text())

from tbserver import portal_recovery as r
from tbserver.portal import require_csrf,current_portal_session_user
from tbserver.security import hash_password, hash_token, verify_password
from tbserver.btb_participants import set_own_status, set_lead_managed_status, ensure_participants
from tbserver.public_overview import aggregate_segments,public_counts

company,lead,helper,site,team,btb=[uuid4() for _ in range(6)]
password='OriginalPass12345'
with conn.cursor() as c:
    c.execute("INSERT INTO tb.companies(id,name) VALUES (%s,'Test company')",(company,))
    for uid,name in [(lead,'lead'),(helper,'helper')]:
        c.execute("INSERT INTO tb.users(id,company_id,login_name,display_name,company_role,personal_email) VALUES (%s,%s,%s,%s,'USER',%s)",(uid,company,name,name,name+'@example.invalid'))
        c.execute('INSERT INTO tb.portal_credentials(user_id,password_hash,activated_at) VALUES (%s,%s,now())',(uid,hash_password(password)))
    c.execute("INSERT INTO tb.worksites(id,company_id,name) VALUES (%s,%s,'Testbaustelle')",(site,company))
    c.execute("INSERT INTO tb.teams(id,worksite_id,lead_user_id) VALUES (%s,%s,%s)",(team,site,lead))
    for uid,role in [(lead,'LEAD'),(helper,'HELPER')]:
        c.execute('INSERT INTO tb.team_members(team_id,user_id,role) VALUES (%s,%s,%s)',(team,uid,role))
    c.execute("INSERT INTO tb.btbs(id,worksite_id,team_id,work_date,created_by) VALUES (%s,%s,%s,CURRENT_DATE,%s)",(btb,site,team,lead))
    c.execute('UPDATE tb.teams SET active_btb_id=%s WHERE id=%s',(btb,team))
    ensure_participants(c,btb_id=btb,team_id=team)
    set_lead_managed_status(c,btb_id=btb,team_id=team,target_user_id=helper,actor_user_id=lead,status='NOT_PARTICIPATING')
    for status in ['WORKING','FINISHED']:
        try: set_own_status(c,btb_id=btb,team_id=team,user_id=helper,status=status)
        except HTTPException as e: assert e.status_code==409
        else: raise AssertionError('Excluded helper regained access')
    set_lead_managed_status(c,btb_id=btb,team_id=team,target_user_id=helper,actor_user_id=lead,status='WORKING')
    assert set_own_status(c,btb_id=btb,team_id=team,user_id=helper,status='FINISHED')['status']=='FINISHED'
    c.execute("UPDATE tb.btbs SET status='FINALIZED' WHERE id=%s",(btb,))
    c.execute('DELETE FROM tb.btb_participant_states WHERE btb_id=%s AND user_id=%s',(btb,helper))
    ensure_participants(c,btb_id=btb,team_id=team)
    c.execute('SELECT 1 FROM tb.btb_participant_states WHERE btb_id=%s AND user_id=%s',(btb,helper))
    assert c.fetchone() is None, 'Historical report acquired a new participant'
    c.execute("SELECT permissions FROM tb.permission_profiles WHERE profile_key='RESPONSIBLE_TB'")
    assert c.fetchone()['permissions']['files.view'] is True

user={'user_id':lead,'company_id':company,'company_role':'USER','auth_kind':'PORTAL','personal_email':'lead@example.invalid','display_name':'lead','login_name':'lead','personal_phone':None}
app=FastAPI();app.include_router(r.router)
app.dependency_overrides[require_csrf]=lambda:user
app.dependency_overrides[current_portal_session_user]=lambda:user
mail=[];r._mail=lambda email,link,purpose:mail.append((email,link,purpose))
client=TestClient(app)
assert client.post('/api/v1/portal/password/forgot',json={'login':'unknown'}).status_code==200
assert not mail
assert client.post('/api/v1/portal/password/forgot',json={'login':'lead'}).status_code==200
assert not mail, 'Unverified address received reset'
assert client.post('/api/v1/portal/email/verify-request').status_code==200
verify=mail[-1][1].split('=')[-1]
assert client.post('/api/v1/portal/email/verify',json={'token':verify}).status_code==200
assert client.post('/api/v1/portal/email/verify',json={'token':verify}).status_code==400
assert client.get('/api/v1/portal/email/status').json()['verified']
assert client.post('/api/v1/portal/password/forgot',json={'login':'lead'}).status_code==200
reset=mail[-1][1].split('=')[-1]
with conn.cursor() as c:
    c.execute("INSERT INTO tb.portal_sessions(user_id,session_token_hash,csrf_secret_hash,expires_at) VALUES (%s,'session','csrf',now()+interval '1 hour')",(lead,))
assert client.post('/api/v1/portal/password/reset',json={'token':reset,'password':'NewPassword123456'}).status_code==200
assert client.post('/api/v1/portal/password/reset',json={'token':reset,'password':'AnotherPass1234'}).status_code==400
with conn.cursor() as c:
    c.execute('SELECT password_hash FROM tb.portal_credentials WHERE user_id=%s',(lead,));assert verify_password('NewPassword123456',c.fetchone()['password_hash'])
    c.execute('SELECT count(*) AS n FROM tb.portal_sessions WHERE user_id=%s AND revoked_at IS NULL',(lead,));assert c.fetchone()['n']==0
    raw=r._issue(c,lead,'RESET_PASSWORD','lead@example.invalid')
    c.execute("UPDATE tb.portal_account_tokens SET expires_at=now()-interval '1 minute' WHERE token_hash=%s",(hash_token(raw),))
assert client.post('/api/v1/portal/password/reset',json={'token':raw,'password':'AnotherPass1234'}).status_code==400
with conn.cursor() as c:
    raw=r._issue(c,lead,'RESET_PASSWORD','lead@example.invalid')
    c.execute("UPDATE tb.users SET personal_email='changed@example.invalid' WHERE id=%s",(lead,))
    c.execute('SELECT verified_email FROM tb.portal_credentials WHERE user_id=%s',(lead,));assert c.fetchone()['verified_email'] is None
assert client.post('/api/v1/portal/password/reset',json={'token':raw,'password':'AnotherPass1234'}).status_code==400
with conn.cursor() as c:
    assert [r._limit(c,'test-limit',2) for _ in range(3)]==[True,True,False]
    raw=r._issue(c,helper,'RESET_PASSWORD')
    c.execute("UPDATE tb.users SET status='DISABLED' WHERE id=%s",(helper,))
assert client.post('/api/v1/portal/password/reset',json={'token':raw,'password':'AnotherPass1234'}).status_code==400
assert public_counts()['worksites']==1, 'Test site excluded'
assert aggregate_segments([{'route_number':'1','km_from_m':0.,'km_to_m':1000.},{'route_number':'1','km_from_m':500.,'km_to_m':2000.},{'route_number':'1','km_from_m':3000.,'km_to_m':4000.}])['kilometres']==3

root=Path(__file__).resolve().parents[1]/'templates'
env=Environment(loader=FileSystemLoader(str(root)),autoescape=select_autoescape())
request=Request({'type':'http','method':'GET','path':'/portal/v1/worksites','headers':[],'scheme':'https','server':('example.invalid',443),'query_string':b''})
for name in ['v1_base.html','v1_worksites.html','v1_worksite.html','v1_btb.html','v1_portal_home.html','v1_portal_login.html','v1_portal_account.html','v1_portal_pictures.html','v1_public_home.html']:
    html=env.get_template(name).render(request=request,user=user,worksite_id=str(site),btb_id=str(btb),counts=public_counts())
    assert html
    out=Path('/tmp/portal-rendered');out.mkdir(exist_ok=True);(out/name).write_text(html)
print('PASS: migration, participant guard, historical participants, email verification, one-use/expired/invalidated tokens, password/session reset, disabled account, rate limit, public counts, Jinja rendering')
