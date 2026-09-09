import os, base64, hashlib, hmac, secrets, io, calendar, json, shutil, subprocess, smtplib, ssl, threading, re, mimetypes, uuid
from datetime import datetime, timezone, timedelta
from email.message import EmailMessage
from pathlib import Path
from zoneinfo import ZoneInfo
from urllib.parse import parse_qs, quote

import psycopg
from psycopg.errors import UniqueViolation
import qrcode
import qrcode.image.svg
from psycopg.rows import dict_row
from fastapi import FastAPI, Request, HTTPException, Header
from pydantic import BaseModel, Field
from fastapi.responses import RedirectResponse, JSONResponse, Response
from fastapi.templating import Jinja2Templates
from fastapi.encoders import jsonable_encoder
from itsdangerous import URLSafeTimedSerializer, BadSignature, SignatureExpired

SERVER_VERSION = "0.7.0"
app = FastAPI(title="TB-Helfer API", version=SERVER_VERSION)
templates = Jinja2Templates(directory="templates")
COOKIE = "tb_admin"
MAX_AGE = 43200
ITER = 310000


COMPANY_FOLDER_MIN_USERS = max(1, int(os.getenv("COMPANY_FOLDER_MIN_USERS", "5")))
BACKUP_ROOT = Path(os.getenv("BACKUP_ROOT", "/tb/backup"))
STORAGE_ROOT = Path(os.getenv("STORAGE_ROOT", "/tb/storage"))
COLLAB_STORAGE_ROOT = Path(os.getenv("COLLAB_STORAGE_ROOT", str(STORAGE_ROOT / "collab")))
COLLAB_TEMP_RETENTION_DAYS = max(1, int(os.getenv("COLLAB_TEMP_RETENTION_DAYS", "90")))
COLLAB_PURGE_GRACE_DAYS = max(1, int(os.getenv("COLLAB_PURGE_GRACE_DAYS", "14")))
COLLAB_MAX_ATTACHMENT_BYTES = max(1024 * 1024, int(os.getenv("COLLAB_MAX_ATTACHMENT_BYTES", str(50 * 1024 * 1024))))
COLLAB_MAX_JSON_BYTES = max(16 * 1024, int(os.getenv("COLLAB_MAX_JSON_BYTES", str(1024 * 1024))))
STATION_ACTIVE_PATH = Path(os.getenv("STATION_ACTIVE_PATH", "data/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json"))
STATION_OPERATING_PATH = Path(os.getenv("STATION_OPERATING_PATH", "data/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json"))
_station_catalog_cache = None
SCHEDULER_INTERVAL_SECONDS = max(60, int(os.getenv("SYSTEM_SCHEDULER_INTERVAL_SECONDS", "300")))
_scheduler_stop = threading.Event()
_scheduler_thread = None

class DeviceLogin(BaseModel):
    key: str = Field(min_length=16, max_length=500)
    device_uid: str = Field(min_length=8, max_length=300)
    device_name: str | None = Field(default=None, max_length=200)
    os_version: str | None = Field(default=None, max_length=100)
    app_version: str | None = Field(default=None, max_length=100)

class SyncAckRequest(BaseModel):
    sequence_no: int = Field(ge=0)

class SyncMutationRequest(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=200)
    entity_type: str = Field(min_length=1, max_length=40)
    operation: str = Field(min_length=1, max_length=20)
    entity_id: str | None = Field(default=None, max_length=80)
    base_version: int | None = Field(default=None, ge=0)
    payload: dict = Field(default_factory=dict)

class CollabAckRequest(BaseModel):
    worksite_id: str = Field(min_length=1, max_length=80)
    sequence_no: int = Field(ge=0)

class CollabMutationRequest(BaseModel):
    client_change_id: str = Field(min_length=1, max_length=200)
    worksite_id: str = Field(min_length=1, max_length=80)
    entity_type: str = Field(min_length=1, max_length=40)
    operation: str = Field(min_length=1, max_length=20)
    entity_id: str | None = Field(default=None, max_length=80)
    client_entity_key: str | None = Field(default=None, max_length=500)
    base_version: int | None = Field(default=None, ge=0)
    group_id: str | None = Field(default=None, max_length=80)
    status: str | None = Field(default=None, max_length=80)
    area_scope: dict = Field(default_factory=dict)
    payload: dict = Field(default_factory=dict)

def device_ser():
    secret=os.getenv("ADMIN_SESSION_SECRET","").strip()
    return URLSafeTimedSerializer(secret,salt="tb-helfer-device-v1") if len(secret)>=32 else None

def make_device_token(user_id, device_id):
    s=device_ser()
    if not s:
        raise HTTPException(503,"DEVICE_TOKEN_SECRET fehlt")
    return s.dumps({"uid":str(user_id),"did":str(device_id)})

def read_bearer(authorization: str | None):
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401,"TOKEN_MISSING")
    return authorization.split(" ",1)[1].strip()

def access_expired(valid_until):
    return valid_until is not None and valid_until <= datetime.now(timezone.utc)

def add_months(value, months):
    month_index=(value.month - 1) + months
    year=value.year + month_index // 12
    month=month_index % 12 + 1
    day=min(value.day, calendar.monthrange(year, month)[1])
    return value.replace(year=year, month=month, day=day)

def access_until_from_period(period, custom_date=None, base=None):
    period=(period or "UNLIMITED").upper()
    now=datetime.now(timezone.utc)
    base=base or now
    if period == "UNLIMITED":
        return None
    if period == "1M":
        return add_months(base,1)
    if period == "6M":
        return add_months(base,6)
    if period == "1Y":
        return add_months(base,12)
    if period == "CUSTOM":
        raw=(custom_date or "").strip()
        if not raw:
            raise ValueError("Bitte ein Ablaufdatum auswählen.")
        try:
            d=datetime.strptime(raw,"%Y-%m-%d").date()
        except ValueError:
            raise ValueError("Das Ablaufdatum ist ungültig.")
        return datetime(d.year,d.month,d.day,23,59,59,tzinfo=timezone.utc)
    raise ValueError("Unbekannte Laufzeit.")

def resolve_company(cur, company_name):
    name=(company_name or "").strip()
    if not name:
        return None,None
    cur.execute(
        "SELECT id,name FROM tb.companies WHERE lower(btrim(name))=lower(btrim(%s))",
        (name,),
    )
    row=cur.fetchone()
    if not row:
        cur.execute(
            "INSERT INTO tb.companies(name) VALUES (%s) ON CONFLICT DO NOTHING RETURNING id,name",
            (name,),
        )
        row=cur.fetchone()
        if not row:
            cur.execute(
                "SELECT id,name FROM tb.companies WHERE lower(btrim(name))=lower(btrim(%s))",
                (name,),
            )
            row=cur.fetchone()
    return row["id"],row["name"]

def require_device(authorization: str | None):
    token=read_bearer(authorization)
    s=device_ser()
    if not s:
        raise HTTPException(503,"DEVICE_TOKEN_SECRET fehlt")
    try:
        data=s.loads(token)
    except (BadSignature,SignatureExpired):
        raise HTTPException(401,"TOKEN_INVALID")
    rows=q("""SELECT d.id AS device_id,d.user_id,d.device_uid,d.device_name,d.platform,d.os_version,
              d.app_version,d.bound_at,d.last_seen_at,d.revoked_at,
              u.full_name,u.company,u.company_id,u.email,u.role,u.status,u.access_valid_until
              FROM tb.devices d JOIN tb.users u ON u.id=d.user_id
              WHERE d.id=%s AND u.id=%s""",(data.get("did"),data.get("uid")))
    if not rows:
        raise HTTPException(401,"DEVICE_NOT_FOUND")
    row=rows[0]
    if row["revoked_at"] is not None:
        raise HTTPException(401,"DEVICE_REVOKED")
    if row["status"]!="ACTIVE":
        raise HTTPException(403,"USER_DISABLED")
    if access_expired(row.get("access_valid_until")):
        raise HTTPException(403,"USER_EXPIRED")
    return row

def db():
    return psycopg.connect(
        host=os.getenv("DB_HOST","db"),
        port=int(os.getenv("DB_PORT","5432")),
        dbname=os.getenv("DB_NAME","tb_helfer"),
        user=os.getenv("DB_USER","tb_helfer"),
        password=os.environ["DB_PASSWORD"],
        row_factory=dict_row,
        connect_timeout=5,
    )

def ser():
    secret=os.getenv("ADMIN_SESSION_SECRET","").strip()
    return URLSafeTimedSerializer(secret,salt="tb-helfer-admin-v1") if len(secret)>=32 else None

def enc(b): return base64.urlsafe_b64encode(b).decode().rstrip("=")
def dec(s): return base64.urlsafe_b64decode(s+"="*(-len(s)%4))

def hash_key(key):
    salt=secrets.token_bytes(16)
    dig=hashlib.pbkdf2_hmac("sha256",key.encode(),salt,ITER)
    return f"pbkdf2_sha256${ITER}${enc(salt)}${enc(dig)}"

def verify_key(key, stored):
    try:
        _,it,salt,expected=stored.split("$",3)
        actual=hashlib.pbkdf2_hmac("sha256",key.encode(),dec(salt),int(it))
        return hmac.compare_digest(actual,dec(expected))
    except Exception:
        return False


ACTIVATION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

def activation_key_id(raw_key):
    key=raw_key.strip().upper()
    parts=key.split("-")
    if len(parts) >= 4 and parts[0] == "TBH" and len(parts[1]) == 8:
        return parts[1]
    return None

def generate_activation_key():
    key_id="".join(secrets.choice(ACTIVATION_ALPHABET) for _ in range(8))
    secret="".join(secrets.choice(ACTIVATION_ALPHABET) for _ in range(24))
    groups=[secret[i:i+4] for i in range(0,len(secret),4)]
    key="TBH-" + key_id + "-" + "-".join(groups)
    return key_id,key

def qr_svg_for_key(key):
    payload="tbhelfer://activate?key=" + quote(key, safe="")
    image=qrcode.make(
        payload,
        image_factory=qrcode.image.svg.SvgPathImage,
        box_size=8,
        border=2,
    )
    buf=io.BytesIO()
    image.save(buf)
    return buf.getvalue().decode("utf-8")

def issue_activation_key(user_id):
    for _ in range(8):
        key_id,key=generate_activation_key()
        try:
            with db() as conn, conn.cursor() as cur:
                cur.execute(
                    """UPDATE tb.users
                       SET access_key_id=%s,
                           access_key_hash=%s,
                           access_key_hint=%s,
                           key_created_at=now(),
                           activation_key_used_at=NULL,
                           updated_at=now()
                       WHERE id=%s AND role='USER'
                       RETURNING id,full_name,company,email,role,status,access_key_hint,access_key_id,key_created_at,access_valid_until""",
                    (key_id,hash_key(key),key_id+"…"+key[-4:],user_id),
                )
                user=cur.fetchone()
                if not user:
                    raise HTTPException(404,"USER_NOT_FOUND")
                conn.commit()
                return user,key
        except UniqueViolation:
            continue
    raise HTTPException(500,"KEY_GENERATION_FAILED")

def user_for_activation_key(raw_key):
    raw=raw_key.strip()
    normalized=raw.upper()
    key_id=activation_key_id(normalized)

    if key_id:
        rows=q(
            """SELECT id,full_name,company,email,role,status,access_key_hash,access_key_id,activation_key_used_at,access_valid_until
               FROM tb.users
               WHERE role='USER' AND access_key_id=%s""",
            (key_id,),
        )
        if (rows and rows[0]["activation_key_used_at"] is None
                and verify_key(normalized,rows[0]["access_key_hash"])):
            return rows[0]
        return None

    # Übergang für bereits vorhandene manuell angelegte Keys aus älteren TB-Helfer-Versionen.
    rows=q(
        """SELECT id,full_name,company,email,role,status,access_key_hash,access_key_id,access_valid_until
           FROM tb.users
           WHERE role='USER' AND access_key_id IS NULL AND status='ACTIVE'"""
    )
    for row in rows:
        if verify_key(raw,row["access_key_hash"]):
            return row
    return None

async def form(request):
    raw=(await request.body()).decode("utf-8","replace")
    p=parse_qs(raw,keep_blank_values=True)
    return {k:(v[0] if v else "") for k,v in p.items()}

def q(sql, params=None):
    with db() as conn, conn.cursor() as cur:
        # psycopg interpretiert Prozentzeichen in SQL als Platzhalter, sobald
        # ein params-Argument uebergeben wird. Bei Abfragen ohne Parameter
        # deshalb execute(sql) verwenden (z. B. ILIKE '%FAIL%').
        if params is None:
            cur.execute(sql)
        else:
            cur.execute(sql, params)
        return cur.fetchall()


def audit_event(action, user_id=None, device_id=None, entity_type=None, entity_id=None, details=None):
    """Best-effort Audit-Log. Ein Audit-Fehler darf die eigentliche Aktion nicht blockieren."""
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute(
                """INSERT INTO tb.audit_log(user_id,device_id,action,entity_type,entity_id,details)
                   VALUES (%s,%s,%s,%s,%s,%s::jsonb)""",
                (user_id,device_id,action,entity_type,entity_id,json.dumps(details or {},ensure_ascii=False)),
            )
            conn.commit()
    except Exception:
        pass


def parse_uuid_value(value, detail="INVALID_ID"):
    try:
        return uuid.UUID(str(value))
    except (ValueError, TypeError, AttributeError):
        raise HTTPException(400, detail)


def enforce_json_size(value, limit=COLLAB_MAX_JSON_BYTES):
    try:
        size=len(json.dumps(value,ensure_ascii=False,separators=(",",":"),default=str).encode("utf-8"))
    except Exception:
        raise HTTPException(400,"INVALID_JSON")
    if size > limit:
        raise HTTPException(413,"JSON_TOO_LARGE")


def advisory_xact_lock(cur, key):
    # Transaktionslokal und unabhängig von zufällig kollidierenden Client-Retries.
    cur.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",(str(key),))


def _station_normalize(value):
    return re.sub(r"[^0-9a-zäöüß]+","",(value or "").strip().lower())


def load_station_catalog():
    global _station_catalog_cache
    if _station_catalog_cache is not None:
        return _station_catalog_cache
    by_code={}
    try:
        if STATION_ACTIVE_PATH.exists():
            root=json.loads(STATION_ACTIVE_PATH.read_text("utf-8"))
            for item in root.get("eintraege",[]):
                code=(item.get("code") or "").strip().upper()
                name=(item.get("name") or "").strip()
                if code and name:
                    by_code[code]={"code":code,"name":name,"source":"aktiv"}
        if STATION_OPERATING_PATH.exists():
            root=json.loads(STATION_OPERATING_PATH.read_text("utf-8"))
            for item in root.get("betriebsstellen",[]):
                code=(item.get("rl100Code") or "").strip().upper()
                name=(item.get("rl100Langname") or "").strip()
                if code and name and code not in by_code:
                    by_code[code]={"code":code,"name":name,"source":"betriebsstelle"}
    except Exception:
        # Autofill darf die produktive Admin-Oberfläche bei einer beschädigten optionalen
        # Datendatei nicht komplett blockieren. Der Fehler bleibt über leere Treffer sichtbar.
        by_code={}
    items=list(by_code.values())
    for item in items:
        item["search"]=_station_normalize(item["code"]+" "+item["name"])
        item["name_norm"]=_station_normalize(item["name"])
    _station_catalog_cache={"by_code":by_code,"items":items}
    return _station_catalog_cache


def station_autofill_matches(query, limit=20):
    raw=(query or "").strip()
    if not raw:
        return []
    norm=_station_normalize(raw)
    code=raw.upper()
    catalog=load_station_catalog()
    ranked=[]
    for item in catalog["items"]:
        rank=None
        if item["code"] == code: rank=0
        elif item["name_norm"] == norm: rank=1
        elif item["code"].startswith(code): rank=2
        elif item["name_norm"].startswith(norm): rank=3
        elif norm and norm in item["search"]: rank=4
        if rank is not None:
            ranked.append((rank,item["name"].lower(),item["code"],item))
    ranked.sort(key=lambda x:(x[0],x[1],x[2]))
    return [{"code":x[3]["code"],"name":x[3]["name"]} for x in ranked[:max(1,min(int(limit),50))]]


def complete_station_values(values):
    result=dict(values)
    code=(result.get("station_code") or "").strip().upper()
    name=(result.get("name") or "").strip()
    catalog=load_station_catalog()
    if code and not name:
        hit=catalog["by_code"].get(code)
        if hit: result["name"]=hit["name"]
    elif name and not code:
        name_norm=_station_normalize(name)
        matches=[x for x in catalog["items"] if x["name_norm"]==name_norm]
        if len(matches)==1:
            result["station_code"]=matches[0]["code"]
            result["name"]=matches[0]["name"]
    return result


def default_storage_preferences():
    return {"store_images":False,"store_btb":False,"store_defects":False,"store_acceptances":False}


def storage_preferences_for_user(cur,user_id):
    cur.execute("""SELECT store_images,store_btb,store_defects,store_acceptances
                   FROM tb.user_storage_preferences WHERE user_id=%s""",(user_id,))
    return cur.fetchone() or default_storage_preferences()


def storage_preferences_from_form(data):
    all_value=(data.get("store_all") or "") in ("1","on","true","yes")
    prefs={
        "store_images":all_value or (data.get("store_images") or "") in ("1","on","true","yes"),
        "store_btb":all_value or (data.get("store_btb") or "") in ("1","on","true","yes"),
        "store_defects":all_value or (data.get("store_defects") or "") in ("1","on","true","yes"),
        "store_acceptances":all_value or (data.get("store_acceptances") or "") in ("1","on","true","yes"),
    }
    prefs["store_all"]=all(prefs.values())
    return prefs


def save_storage_preferences(cur,user_id,prefs):
    cur.execute(
        """INSERT INTO tb.user_storage_preferences(user_id,store_images,store_btb,store_defects,store_acceptances)
           VALUES (%s,%s,%s,%s,%s)
           ON CONFLICT (user_id) DO UPDATE SET
             store_images=EXCLUDED.store_images,
             store_btb=EXCLUDED.store_btb,
             store_defects=EXCLUDED.store_defects,
             store_acceptances=EXCLUDED.store_acceptances,
             updated_at=now()""",
        (user_id,bool(prefs.get("store_images")),bool(prefs.get("store_btb")),
         bool(prefs.get("store_defects")),bool(prefs.get("store_acceptances"))),
    )


