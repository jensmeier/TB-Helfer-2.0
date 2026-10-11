from __future__ import annotations

from datetime import datetime, timedelta, timezone
from urllib.parse import urlsplit
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from .config import settings
from .database import transaction
from .events import audit
from .portal import require_csrf, current_portal_session_user
from .admin_session import require_admin_session_csrf
from .notifications import send_system_message, NotificationError
from .security import hash_password, hash_token, random_token, validate_password, verify_password

router = APIRouter(tags=['portal-recovery'])
NEUTRAL = 'Wenn ein passendes Konto mit bestätigter E-Mail existiert, erhältst du einen Link.'

class Forgot(BaseModel):
    login: str = Field(min_length=1, max_length=320)

class TokenRequest(BaseModel):
    token: str = Field(min_length=24, max_length=500)

class Reset(TokenRequest):
    password: str = Field(min_length=12, max_length=200)

class PasswordChange(BaseModel):
    current_password: str = Field(min_length=1, max_length=200)
    password: str = Field(min_length=12, max_length=200)


def _base():
    base = (settings.portal_public_base_url or '').rstrip('/')
    parsed = urlsplit(base)
    if parsed.scheme != 'https' or not parsed.netloc or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise HTTPException(503, 'PORTAL_RECOVERY_URL_NOT_CONFIGURED')
    return base


def _limit(cur, key, maximum=5):
    cur.execute('DELETE FROM tb.portal_recovery_limits WHERE window_start < now() - interval \'2 hours\'')
    cur.execute('''INSERT INTO tb.portal_recovery_limits(bucket) VALUES (%s)
      ON CONFLICT(bucket) DO UPDATE SET
      attempts=CASE WHEN tb.portal_recovery_limits.window_start < now()-interval '1 hour' THEN 1 ELSE tb.portal_recovery_limits.attempts+1 END,
      window_start=CASE WHEN tb.portal_recovery_limits.window_start < now()-interval '1 hour' THEN now() ELSE tb.portal_recovery_limits.window_start END
      RETURNING attempts''', (hash_token(key),))
    return cur.fetchone()['attempts'] <= maximum


def _issue(cur, user_id, purpose, email=None):
    raw = random_token(32)
    cur.execute('UPDATE tb.portal_account_tokens SET used_at=now() WHERE user_id=%s AND purpose=%s AND used_at IS NULL', (user_id,purpose))
    cur.execute('''INSERT INTO tb.portal_account_tokens(token_hash,user_id,purpose,email,expires_at)
      VALUES (%s,%s,%s,%s,now()+interval '30 minutes')''', (hash_token(raw),user_id,purpose,email))
    return raw


def _mail(email, link, purpose):
    try:
        send_system_message('TB-Helfer – '+purpose,
            purpose+'\n\n'+link+'\n\nDieser Link gilt 30 Minuten und kann einmal verwendet werden.\nFalls du dies nicht angefordert hast, ignoriere diese Nachricht.', [email])
    except NotificationError:
        # No tokens, email addresses or SMTP exception contents in audit/logs.
        with transaction() as conn, conn.cursor() as cur:
            audit(cur, action='PORTAL_RECOVERY_MAIL_FAILED', actor_user_id=None, entity_type='SYSTEM', details={})


def _active(row):
    return (row and row['status']=='ACTIVE' and row['deleted_at'] is None
        and row['company_role']!='ADMIN' and row['company_status']=='ACTIVE'
        and row['company_deleted_at'] is None
        and (row['access_valid_until'] is None or row['access_valid_until']>datetime.now(timezone.utc)))


@router.post('/api/v1/portal/password/forgot')
def forgot(payload: Forgot, request: Request, tasks: BackgroundTasks):
    base = _base()
    identifier=payload.login.strip().lower()
    with transaction() as conn, conn.cursor() as cur:
        allowed_ip=_limit(cur, 'forgot-ip:'+str(request.client.host if request.client else 'unknown'), 30)
        allowed_id=_limit(cur, 'forgot-id:'+identifier)
        if not allowed_ip or not allowed_id:
            return {'message':NEUTRAL}
        cur.execute('''SELECT u.*, c.verified_email, co.status AS company_status, co.deleted_at AS company_deleted_at
          FROM tb.users u JOIN tb.portal_credentials c ON c.user_id=u.id
          LEFT JOIN tb.companies co ON co.id=u.company_id
          WHERE u.deleted_at IS NULL AND (lower(u.login_name)=%s OR lower(u.personal_email)=%s)
          LIMIT 2''', (identifier,identifier))
        rows=cur.fetchall()
        if len(rows)==1:
            row=rows[0]
            email=(row['personal_email'] or '').strip().lower()
            if _active(row) and email and email==(row['verified_email'] or '').lower():
                raw=_issue(cur,row['id'],'RESET_PASSWORD',email)
                tasks.add_task(_mail,email,base+'/portal/v1/account#reset='+raw,'Passwort zurücksetzen')
    return {'message':NEUTRAL}


@router.get('/api/v1/portal/email/status')
def email_status(user=Depends(current_portal_session_user)):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute('SELECT verified_email FROM tb.portal_credentials WHERE user_id=%s',(user['user_id'],))
        row=cur.fetchone()
    return {'verified':bool(row and row['verified_email'] and row['verified_email'].lower()==(user.get('personal_email') or '').strip().lower())}


@router.post('/api/v1/portal/email/verify-request')
def request_verification(tasks: BackgroundTasks, user=Depends(require_csrf)):
    if user.get('auth_kind')!='PORTAL':
        raise HTTPException(403,'PORTAL_SESSION_REQUIRED')
    base=_base()
    email=(user.get('personal_email') or '').strip().lower()
    if '@' not in email or '\r' in email or '\n' in email:
        raise HTTPException(400,'EMAIL_REQUIRED')
    with transaction() as conn, conn.cursor() as cur:
        if not _limit(cur,'verify:'+str(user['user_id'])):
            return {'message':'Bitte später erneut versuchen.'}
        raw=_issue(cur,user['user_id'],'VERIFY_EMAIL',email)
    tasks.add_task(_mail,email,base+'/portal/v1/account#verify='+raw,'E-Mail bestätigen')
    return {'message':'Bestätigungslink angefordert. Bitte prüfe dein E-Mail-Postfach.'}


def _token_user(cur, raw, purpose):
    cur.execute('''SELECT u.*, t.email AS token_email, co.status AS company_status, co.deleted_at AS company_deleted_at
      FROM tb.portal_account_tokens t JOIN tb.users u ON u.id=t.user_id
      JOIN tb.portal_credentials c ON c.user_id=u.id
      LEFT JOIN tb.companies co ON co.id=u.company_id
      WHERE t.token_hash=%s AND t.purpose=%s AND t.used_at IS NULL AND t.expires_at>now()
      FOR UPDATE OF t,u,c''',(hash_token(raw),purpose))
    row=cur.fetchone()
    if not _active(row) or (row['token_email'] is not None and row['token_email']!=(row['personal_email'] or '').strip().lower()):
        raise HTTPException(400,'LINK_INVALID_OR_EXPIRED')
    return row


@router.post('/api/v1/portal/email/verify')
def verify_email(payload: TokenRequest):
    with transaction() as conn, conn.cursor() as cur:
        row=_token_user(cur,payload.token,'VERIFY_EMAIL')
        cur.execute('UPDATE tb.portal_credentials SET verified_email=%s WHERE user_id=%s',(row['token_email'],row['id']))
        cur.execute('UPDATE tb.portal_account_tokens SET used_at=now() WHERE token_hash=%s',(hash_token(payload.token),))
    return {'message':'E-Mail bestätigt. Du kannst dich jetzt anmelden.'}


def _set_password(cur,user_id,password):
    cur.execute('''UPDATE tb.portal_credentials SET password_hash=%s,failed_login_count=0,locked_until=NULL,
      must_change_password=false,updated_at=now() WHERE user_id=%s''',(hash_password(password),user_id))
    cur.execute('UPDATE tb.portal_sessions SET revoked_at=now() WHERE user_id=%s AND revoked_at IS NULL',(user_id,))
    cur.execute('UPDATE tb.portal_account_tokens SET used_at=now() WHERE user_id=%s AND used_at IS NULL',(user_id,))
    audit(cur, action='PORTAL_PASSWORD_CHANGED', actor_user_id=user_id,entity_type='USER',entity_id=user_id)


def _validate(password):
    try:
        validate_password(password)
    except ValueError as exc:
        raise HTTPException(400,str(exc)) from exc


@router.post('/api/v1/portal/password/reset')
def reset(payload: Reset):
    _validate(payload.password)
    with transaction() as conn, conn.cursor() as cur:
        row=_token_user(cur,payload.token,'RESET_PASSWORD')
        _set_password(cur,row['id'],payload.password)
    return {'message':'Passwort gespeichert. Bitte erneut anmelden.'}


@router.post('/api/v1/portal/password/change')
def change_password(payload: PasswordChange,user=Depends(require_csrf)):
    if user.get('auth_kind')!='PORTAL':
        raise HTTPException(403,'PORTAL_SESSION_REQUIRED')
    _validate(payload.password)
    with transaction() as conn, conn.cursor() as cur:
        if not _limit(cur,'password:'+str(user['user_id'])):
            return {'message':'Bitte später erneut versuchen.'}
        cur.execute('SELECT password_hash FROM tb.portal_credentials WHERE user_id=%s FOR UPDATE',(user['user_id'],))
        row=cur.fetchone()
        if not row or not verify_password(payload.current_password,row['password_hash']):
            return {'message':'Aktuelles Passwort stimmt nicht.'}
        _set_password(cur,user['user_id'],payload.password)
    return {'message':'Passwort gespeichert. Bitte erneut anmelden.','changed':True}


@router.post('/api/v1/admin/users/{user_id}/password-reset')
def admin_reset(user_id: UUID, admin=Depends(require_admin_session_csrf)):
    if admin['company_role']!='ADMIN':
        raise HTTPException(403,'ADMIN_REQUIRED')
    base=_base()
    with transaction() as conn, conn.cursor() as cur:
        cur.execute('''SELECT u.*,co.status AS company_status,co.deleted_at AS company_deleted_at
          FROM tb.users u LEFT JOIN tb.companies co ON co.id=u.company_id WHERE u.id=%s FOR UPDATE OF u''',(user_id,))
        if not _active(cur.fetchone()):
            raise HTTPException(409,'USER_NOT_ACTIVE')
        raw=_issue(cur,user_id,'RESET_PASSWORD')
        audit(cur,action='PORTAL_PASSWORD_RESET_ISSUED',actor_user_id=admin['user_id'],entity_type='USER',entity_id=user_id)
    return {'url':base+'/portal/v1/account#reset='+raw,'expires_in_minutes':30}
