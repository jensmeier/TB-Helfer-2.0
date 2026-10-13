"""10k metadata integration test; isolated CI database only, no photo uploads."""
import base64
import struct
import json
import os
import sys
import time
from contextlib import contextmanager
from pathlib import Path
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from fastapi import FastAPI
from fastapi.testclient import TestClient
from tbserver import files, media_gallery
from tbserver.portal import current_portal_user

assert os.environ.get("PORTAL_TEST_ISOLATED") == "YES"
assert os.environ["DB_NAME"] == "portal_test", "Only the disposable CI database"
conn = psycopg.connect(host=os.environ["DB_HOST"], dbname=os.environ["DB_NAME"],
    user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"],
    autocommit=True, row_factory=dict_row)
company, lead, helper, other, site, team, other_team, btb, other_btb, defect, acceptance = [uuid4() for _ in range(11)]
with conn.cursor() as c:
    c.execute("INSERT INTO tb.companies(id,name) VALUES (%s,'Gallery test')", (company,))
    for uid in (lead, helper, other):
        c.execute("INSERT INTO tb.users(id,company_id,login_name,display_name,company_role) VALUES (%s,%s,%s,'Gallery user','USER')",
            (uid,company,str(uid)))
    c.execute("INSERT INTO tb.worksites(id,company_id,name) VALUES (%s,%s,'10000 photos')",(site,company))
    for tid, uid in ((team,lead),(other_team,other)):
        c.execute("INSERT INTO tb.teams(id,worksite_id,lead_user_id) VALUES (%s,%s,%s)",(tid,site,uid))
        c.execute("INSERT INTO tb.team_members(team_id,user_id,role) VALUES (%s,%s,'LEAD')",(tid,uid))
    c.execute("INSERT INTO tb.team_members(team_id,user_id,role) VALUES (%s,%s,'HELPER')",(team,helper))
    for uid, role, profile in ((lead,"RESPONSIBLE_TB","RESPONSIBLE_TB"),(helper,"HELPER","HELPER_STANDARD")):
        c.execute("""INSERT INTO tb.worksite_memberships(worksite_id,user_id,worksite_role,permission_profile,permissions_override,area_scope)
            VALUES (%s,%s,%s,%s,%s,%s)""",
            (site,uid,role,profile,Jsonb({"files.view":True,"btb.view":True,"defect.view":True,"acceptance.view":True}),
             Jsonb({"all":True} if uid==lead else {"labels":["gleis 1"]})))
    for bid,tid,uid in ((btb,team,lead),(other_btb,other_team,other)):
        c.execute("INSERT INTO tb.btbs(id,worksite_id,team_id,work_date,created_by) VALUES (%s,%s,%s,'2026-10-06',%s)",(bid,site,tid,uid))
    c.execute("INSERT INTO tb.defects(id,worksite_id,title,area_scope) VALUES (%s,%s,'Visible',%s)",(defect,site,Jsonb({"labels":["gleis 1"]})))
    c.execute("INSERT INTO tb.acceptances(id,worksite_id,title,area_scope) VALUES (%s,%s,'Hidden',%s)",(acceptance,site,Jsonb({"labels":["gleis 2"]})))
    c.execute("""
        WITH inserted AS (
            INSERT INTO tb.files(company_id,worksite_id,storage_key,original_name,mime_type,byte_size,sha256,created_by,created_at,captured_at,photo_metadata)
            SELECT %s,%s,'gallery-test/'||gen_random_uuid(),'Foto_'||g||'.jpg','image/jpeg',12000,
                   md5(g::text)||md5(g::text),%s,'2026-10-06 00:00:00+00'::timestamptz+g*interval '1 second',
                   '2026-10-06 00:00:00+00'::timestamptz+g*interval '1 second',
                   jsonb_build_object('short_text','needle-'||lpad(g::text,5,'0'))
            FROM generate_series(1,10000) g RETURNING id
        )
        INSERT INTO tb.file_links(file_id,entity_type,entity_id)
        SELECT id,'BTB',%s FROM inserted
    """,(company,site,lead,btb))
    c.execute("SELECT id FROM tb.files WHERE worksite_id=%s AND original_name='Foto_10000.jpg'",(site,))
    newest=c.fetchone()["id"]
    # Multiple links and multiple purposes must not duplicate or skip a file.
    c.execute("INSERT INTO tb.file_links(file_id,entity_type,entity_id,purpose) VALUES (%s,'BTB',%s,'SECOND'),(%s,'DEFECT',%s,'PHOTO')",(newest,btb,newest,defect))

user={"user_id":lead,"company_id":company,"company_role":"USER","company_status":"ACTIVE","company_deleted_at":None}
app=FastAPI();app.include_router(files.router)
app.dependency_overrides[current_portal_user]=lambda:user
client=TestClient(app)
url=f"/api/v1/worksites/{site}/media/page"
query_count=0
original_transaction=media_gallery.transaction
class CountCursor:
    def __init__(self,cur):self.cur=cur
    def __enter__(self):self.cur.__enter__();return self
    def __exit__(self,*args):return self.cur.__exit__(*args)
    def execute(self,*args,**kwargs):
        global query_count
        query_count+=1
        return self.cur.execute(*args,**kwargs)
    def __getattr__(self,name):return getattr(self.cur,name)
class CountConnection:
    def __init__(self,c):self.c=c
    def cursor(self):return CountCursor(self.c.cursor())
@contextmanager
def counted():
    with original_transaction() as c:yield CountConnection(c)
media_gallery.transaction=counted

started=time.perf_counter()
r=client.get(url);assert r.status_code==200,r.text
first_seconds=time.perf_counter()-started
first=r.json()
assert first["gallery_version"]=="PORTAL-011"
assert all(m["photo_number"]==int(m["filename"][5:-4]) and m["byte_size"]==12000 and m["btb_date"]=="2026-10-06" for m in first["media"])
assert len(first["media"])==60 and first["media"][0]["attachment_id"]==str(newest)
assert query_count==3, query_count
assert len(r.content)<100000,len(r.content)
assert all(set(m["renditions"])=={"thumb","preview"} and "parent_payload" not in m for m in first["media"])
seen={m["attachment_id"] for m in first["media"]}
cursor=first["next_cursor"]
pages=1;max_queries=0
started=time.perf_counter()
while cursor:
    query_count=0
    response=client.get(url,params={"cursor":cursor})
    assert response.status_code==200,response.text
    data=response.json();pages+=1;max_queries=max(max_queries,query_count)
    assert len(data["media"])<=60
    ids=[m["attachment_id"] for m in data["media"]]
    assert all(m["photo_number"]==int(m["filename"][5:-4]) for m in data["media"])
    assert len(set(ids))==len(ids) and not (set(ids)&seen)
    seen.update(ids)
    cursor=data["next_cursor"]
assert len(seen)==10000,len(seen)
assert max_queries<=3,max_queries
traversal_seconds=time.perf_counter()-started
assert client.get(url,params={"kind":"DEFECT","parent_id":str(defect)}).json()["count"]==1
assert client.get(url,params={"kind":"DEFECT","parent_id":str(btb)}).json()["count"]==0
# Server filter finds a record far beyond the first page.
found=client.get(url,params={"search":"needle-00001"}).json()
assert len(found["media"])==1 and found["media"][0]["filename"]=="Foto_1.jpg"
assert len(client.get(url,params={"kind":"DEFECT"}).json()["media"])==1
assert client.get(url,params={"day":"2026-10-07"}).json()["media"]==[]
assert client.get(url,params={"day":"2026-10-06"}).json()["count"]==60
assert client.get(url,params={"search":"%_"}).json()["media"]==[]
assert client.get(url,params={"cursor":"broken"}).status_code==400
assert client.get(url,params={"cursor":first["next_cursor"],"kind":"DEFECT"}).status_code==400
assert client.get(url,params={"limit":101}).status_code==422
assert client.get(url,params={"kind":"OTHER"}).status_code==400
# Fresh additions do not duplicate older records on continuation.
with conn.cursor() as c:
    def photo(name,kind,parent,stamp="2026-10-10 10:00:00+00",captured=None):
        fid=uuid4()
        c.execute("""INSERT INTO tb.files(id,company_id,worksite_id,storage_key,original_name,mime_type,byte_size,sha256,created_by,created_at,captured_at)
            VALUES (%s,%s,%s,%s,%s,'image/jpeg',12000,%s,%s,%s,%s)""",
            (fid,company,site,"gallery-test/"+str(fid),name,"b"*64,lead,stamp,captured))
        c.execute("INSERT INTO tb.file_links(file_id,entity_type,entity_id) VALUES (%s,%s,%s)",(fid,kind,parent))
        return fid
    foreign=photo("foreign.jpg","BTB",other_btb)
    allowed=photo("defect.jpg","DEFECT",defect)
    hidden=photo("acceptance.jpg","ACCEPTANCE",acceptance)
    # Berlin day boundary: Oct 10 22:30 UTC is Oct 11 in Germany.
    boundary=photo("midnight.jpg","BTB",btb,captured="2026-10-10 22:30:00+00")
    deleted=photo("deleted.jpg","BTB",btb)
    c.execute("UPDATE tb.files SET deleted_at=now() WHERE id=%s",(deleted,))
    orphan=photo("orphan.jpg","BTB",uuid4())
ids={m["attachment_id"] for m in client.get(url).json()["media"]}
assert str(foreign) in ids and str(hidden) in ids and str(deleted) not in ids and str(orphan) not in ids
boundary_result=client.get(url,params={"day":"2026-10-11"}).json()["media"]
assert [m["attachment_id"] for m in boundary_result]==[str(boundary)]
second=client.get(url,params={"cursor":first["next_cursor"]}).json()["media"]
assert not {m["attachment_id"] for m in second}&{m["attachment_id"] for m in first["media"]}
# Helper sees own team and allowed defect, not foreign team/acceptance scope.
user["user_id"]=helper
ids={m["attachment_id"] for m in client.get(url).json()["media"]}
assert str(foreign) not in ids and str(hidden) not in ids and str(allowed) in ids
assert client.get(url,params={"cursor":first["next_cursor"]}).status_code==400
with conn.cursor() as c:
    c.execute("UPDATE tb.worksite_memberships SET permissions_override=%s WHERE worksite_id=%s AND user_id=%s",
        (Jsonb({"files.view":False}),site,helper))
assert client.get(url).status_code==403
with conn.cursor() as c:
    c.execute("UPDATE tb.worksite_memberships SET status='REMOVED' WHERE worksite_id=%s AND user_id=%s",(site,helper))
assert client.get(url).status_code==403
user["company_id"]=uuid4()
assert client.get(url).status_code==403
user.update(user_id=lead,company_id=company,company_role="ADMIN")
assert client.get(url).status_code==403
user["company_role"]="USER"
# Real packed Android filename, canonical overrides, optional GPS and readable context.
raw=struct.pack(">qfffffhBBB",1791288000000,48.1,11.5,500.0,75.765,1.5,0,0,4,0)
for text in ("Firma","Schotter prüfen","Gleis 2","Schwerbau"):
    encoded=text.encode("utf-8");raw+=bytes([len(encoded)])+encoded
packed="BTBP1__"+base64.urlsafe_b64encode(raw).decode().rstrip("=")+"__foto.jpg"
with conn.cursor() as c:
    packed_id=photo(packed,"BTB",btb)
    c.execute("UPDATE tb.files SET photo_metadata=%s WHERE id=%s",(Jsonb({"kurztext":"Strukturierte Beschriftung","notiz":"Prüfung vor Ort","info1":"Zusatzinfo"}),packed_id))
data=client.get(url).json()["media"]
item=next(m for m in data if m["attachment_id"]==str(packed_id))
assert item["photo_metadata"]["kurztext"]=="Strukturierte Beschriftung"
assert item["photo_metadata"]["objektKennung"]=="Gleis 2"
assert item["photo_metadata"]["notiz"]=="Prüfung vor Ort"
assert abs(item["gps_lat"]-48.1)<0.00001 and item["gps_lon"]==11.5
assert item["captured_at"] is not None
plain=next(m for m in data if m["attachment_id"]==str(allowed))
assert plain["context_title"]=="Visible" and plain["gps_lat"] is None
with conn.cursor() as c:
    c.execute("UPDATE tb.files SET photo_metadata='{}'::jsonb WHERE id=%s",(packed_id,))
item=next(m for m in client.get(url).json()["media"] if m["attachment_id"]==str(packed_id))
assert item["photo_metadata"]["kurztext"]=="Schotter prüfen"

# BTB overrides replace base values and explicit clears do not resurrect them.
with conn.cursor() as c:
    c.execute("""INSERT INTO tb.worksite_nodes(worksite_id,node_kind,name,client_node_key,payload)
        VALUES (%s,'BAHNHOF','Pasing','node-pasing',%s)""", (site,Jsonb({"node_name":"Pasing","km_from":10,"km_to":12})))
    c.execute("UPDATE tb.file_links SET link_metadata=%s WHERE file_id=%s AND entity_type='BTB'",
        (Jsonb({"kurztext":"BTB geändert","firma":"Baufirma","km":12.345,"indikator":"ROT","worksiteNodeKey":"node-pasing","objektKennung":None}),packed_id))
query_count=0
item=next(m for m in client.get(url).json()["media"] if m["attachment_id"]==str(packed_id))
assert query_count<=4,query_count
assert item["photo_metadata"]["kurztext"]=="BTB geändert"
assert item["photo_metadata"]["firma"]=="Baufirma" and item["photo_metadata"]["km"]==12.345
assert item["photo_metadata"]["indikator"]=="ROT" and "objektKennung" not in item["photo_metadata"]
assert item["worksite_node"]["name"]=="Pasing"
# Compare every selected number with the existing BTB loader and JS key rule.
from tbserver.btb_documents import load_btb_photo_files
with conn.cursor() as c:
    ordered=load_btb_photo_files(c,btb)
keys={};numbers={}
for f in ordered:
    key=f["sha256"] or f["original_name"] or str(f["id"])
    if key not in keys:keys[key]=len(keys)+1
    numbers[str(f["id"])]=keys[key]
for m in client.get(url,params={"kind":"BTB","parent_id":str(btb)}).json()["media"]:
    assert m["photo_number"]==numbers[m["attachment_id"]]
with conn.cursor() as c:
    c.execute("UPDATE tb.file_links SET link_metadata=%s WHERE file_id=%s AND entity_type='BTB'",
        (Jsonb({"kurztext":None,"firma":None,"km":None,"indikator":None,"worksiteNodeKey":None}),packed_id))
item=next(m for m in client.get(url).json()["media"] if m["attachment_id"]==str(packed_id))
assert not set(item["photo_metadata"]) & {"kurztext","firma","km","indikator","worksiteNodeKey"}
assert item["worksite_node"] is None

# Bounded sparse-area scan: no runaway scan or N+1 when almost nothing is visible.
with conn.cursor() as c:
    c.execute("UPDATE tb.worksite_memberships SET worksite_role='HELPER', permissions_override=%s,area_scope=%s WHERE worksite_id=%s AND user_id=%s",
        (Jsonb({"files.view":True,"btb.view":False,"defect.view":False,"acceptance.view":False}),Jsonb({"labels":["none"]}),site,lead))
query_count=0
sparse=client.get(url).json()
assert sparse["media"]==[] and sparse["scan_limited"] and sparse["next_cursor"]
assert query_count==5,query_count
assert client.get(url,params={"cursor":sparse["next_cursor"]}).json()["next_cursor"]!=sparse["next_cursor"]
metrics={"photos":10000,"pages":pages,"first_page_seconds":round(first_seconds,4),
         "first_page_bytes":len(r.content),"first_page_sql_queries":3,
         "traversal_seconds":round(traversal_seconds,3),"max_page_queries":max_queries}
out=Path("/tmp/portal-rendered");out.mkdir(exist_ok=True)
(out/"gallery-10000.json").write_text(json.dumps(metrics,indent=2))
print("PASS: 10000 photos, full cursor traversal, bounded payload/queries, filters, duplicate links, permissions, sparse scopes",json.dumps(metrics))

