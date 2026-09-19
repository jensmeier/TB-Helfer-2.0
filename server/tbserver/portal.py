from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

from .config import settings
from .database import transaction
from .security import hash_password, hash_token, random_token, validate_password, verify_password


router = APIRouter(prefix="/api/v1/portal", tags=["portal"])
SESSION_COOKIE = "tb_portal_session"
CSRF_COOKIE = "tb_portal_csrf"


class ActivateRequest(BaseModel):
    token: str = Field(min_length=24, max_length=500)
    password: str = Field(min_length=12, max_length=200)


class LoginRequest(BaseModel):
    login: str = Field(min_length=1, max_length=320)
    password: str = Field(min_length=1, max_length=200)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _set_session_cookies(response: Response, session_token: str, csrf_token: str) -> None:
    secure = settings.environment == "production"
    response.set_cookie(
        SESSION_COOKIE,
        session_token,
        max_age=settings.portal_session_hours * 3600,
        httponly=True,
        secure=secure,
        samesite="lax",
        path="/",
    )
    response.set_cookie(
        CSRF_COOKIE,
        csrf_token,
        max_age=settings.portal_session_hours * 3600,
        httponly=False,
        secure=secure,
        samesite="lax",
        path="/",
    )


def _clear_session_cookies(response: Response) -> None:
    response.delete_cookie(SESSION_COOKIE, path="/")
    response.delete_cookie(CSRF_COOKIE, path="/")


def _load_session(request: Request) -> dict[str, Any]:
    raw = request.cookies.get(SESSION_COOKIE)
    if not raw:
        raise HTTPException(401, "PORTAL_SESSION_MISSING")
    token_hash = hash_token(raw)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT s.id AS session_id, s.user_id, s.csrf_secret_hash, s.expires_at,
                   u.company_id, u.display_name, u.login_name, u.personal_email,
                   u.personal_phone, u.company_role, u.status, u.access_valid_until,
                   u.deleted_at
            FROM tb.portal_sessions s
            JOIN tb.users u ON u.id = s.user_id
            WHERE s.session_token_hash = %s
              AND s.revoked_at IS NULL
            """,
            (token_hash,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(401, "PORTAL_SESSION_INVALID")
        now = _now()
        if row["expires_at"] <= now:
            raise HTTPException(401, "PORTAL_SESSION_EXPIRED")
        if row["deleted_at"] is not None or row["status"] != "ACTIVE":
            raise HTTPException(403, "USER_DISABLED")
        if row["access_valid_until"] is not None and row["access_valid_until"] <= now:
            raise HTTPException(403, "USER_EXPIRED")
        cur.execute(
            "UPDATE tb.portal_sessions SET last_seen_at = now() WHERE id = %s",
            (row["session_id"],),
        )
        result = dict(row)
        result["auth_kind"] = "PORTAL"
        return result


def _device_auth_allowed_for_path(request: Request) -> bool:
    path = request.url.path
    return not (
        path.startswith("/api/v1/portal")
        or path.startswith("/api/v1/admin")
    )


def current_portal_user(request: Request) -> dict[str, Any]:
    authorization = (request.headers.get("Authorization") or "").strip()
    if authorization and _device_auth_allowed_for_path(request):
        # Local import avoids an import cycle: devices imports the admin portal guards.
        from .devices import current_device_user

        return current_device_user(request)
    return _load_session(request)


def current_portal_session_user(request: Request) -> dict[str, Any]:
    """Require a real browser portal session; never fall back to device Bearer auth."""
    return _load_session(request)


def require_csrf(request: Request, user: dict[str, Any] = Depends(current_portal_user)) -> dict[str, Any]:
    if user.get("auth_kind") == "DEVICE":
        # Bearer device tokens are not cookie credentials and therefore are not
        # exposed to browser CSRF. Device authorization is already validated.
        return user
    cookie = request.cookies.get(CSRF_COOKIE)
    header = request.headers.get("X-CSRF-Token")
    if not cookie or not header or cookie != header:
        raise HTTPException(403, "CSRF_INVALID")
    if hash_token(header) != user["csrf_secret_hash"]:
        raise HTTPException(403, "CSRF_INVALID")
    return user


@router.post("/activate", status_code=204)
def activate(payload: ActivateRequest) -> Response:
    try:
        validate_password(payload.password)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    token_hash = hash_token(payload.token)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.status, u.deleted_at, c.activation_expires_at, c.activated_at
            FROM tb.portal_credentials c
            JOIN tb.users u ON u.id = c.user_id
            WHERE c.activation_token_hash = %s
            FOR UPDATE
            """,
            (token_hash,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(400, "ACTIVATION_INVALID")
        if row["status"] != "ACTIVE" or row["deleted_at"] is not None:
            raise HTTPException(403, "USER_DISABLED")
        if row["activated_at"] is not None:
            raise HTTPException(409, "ALREADY_ACTIVATED")
        if row["activation_expires_at"] is None or row["activation_expires_at"] <= _now():
            raise HTTPException(400, "ACTIVATION_EXPIRED")
        cur.execute(
            """
            UPDATE tb.portal_credentials
            SET password_hash = %s,
                activation_token_hash = NULL,
                activation_expires_at = NULL,
                must_change_password = false,
                failed_login_count = 0,
                locked_until = NULL,
                activated_at = now(),
                updated_at = now()
            WHERE user_id = %s
            """,
            (hash_password(payload.password), row["id"]),
        )
    return Response(status_code=204)


def _find_login(cur, login: str):
    identifier = login.strip()
    cur.execute(
        """
        SELECT u.id, u.company_id, u.display_name, u.login_name, u.personal_email,
               u.personal_phone, u.company_role, u.status, u.access_valid_until,
               u.deleted_at, c.password_hash, c.failed_login_count, c.locked_until
        FROM tb.users u
        JOIN tb.portal_credentials c ON c.user_id = u.id
        WHERE lower(u.login_name) = lower(%s)
          AND u.deleted_at IS NULL
        """,
        (identifier,),
    )
    row = cur.fetchone()
    if row or "@" not in identifier:
        return row
    cur.execute(
        """
        SELECT u.id, u.company_id, u.display_name, u.login_name, u.personal_email,
               u.personal_phone, u.company_role, u.status, u.access_valid_until,
               u.deleted_at, c.password_hash, c.failed_login_count, c.locked_until
        FROM tb.users u
        JOIN tb.portal_credentials c ON c.user_id = u.id
        WHERE lower(u.personal_email) = lower(%s)
          AND u.deleted_at IS NULL
        ORDER BY u.created_at
        LIMIT 2
        """,
        (identifier,),
    )
    rows = cur.fetchall()
    if len(rows) > 1:
        raise HTTPException(409, "LOGIN_EMAIL_AMBIGUOUS")
    return rows[0] if rows else None


@router.post("/login")
def login(payload: LoginRequest, request: Request, response: Response) -> dict:
    login_error: HTTPException | None = None
    session_token: str | None = None
    csrf_token: str | None = None
    expires_at: datetime | None = None
    user = None

    with transaction() as conn, conn.cursor() as cur:
        user = _find_login(cur, payload.login)
        if not user:
            raise HTTPException(401, "LOGIN_INVALID")
        now = _now()
        if user["deleted_at"] is not None or user["status"] != "ACTIVE":
            raise HTTPException(403, "USER_DISABLED")
        if user["access_valid_until"] is not None and user["access_valid_until"] <= now:
            raise HTTPException(403, "USER_EXPIRED")
        if user["locked_until"] is not None and user["locked_until"] > now:
            raise HTTPException(429, "LOGIN_LOCKED")

        if not verify_password(payload.password, user["password_hash"]):
            failed = int(user["failed_login_count"] or 0) + 1
            locked_until = now + timedelta(minutes=15) if failed >= 5 else None
            cur.execute(
                """
                UPDATE tb.portal_credentials
                SET failed_login_count = %s, locked_until = %s, updated_at = now()
                WHERE user_id = %s
                """,
                (failed, locked_until, user["id"]),
            )
            login_error = HTTPException(401, "LOGIN_INVALID")
        else:
            session_token = random_token(32)
            csrf_token = random_token(24)
            expires_at = now + timedelta(hours=settings.portal_session_hours)
            cur.execute(
                """
                UPDATE tb.portal_credentials
                SET failed_login_count = 0, locked_until = NULL, last_login_at = now(), updated_at = now()
                WHERE user_id = %s
                """,
                (user["id"],),
            )
            cur.execute(
                """
                INSERT INTO tb.portal_sessions(
                    user_id, session_token_hash, csrf_secret_hash, expires_at, ip_hint, user_agent
                ) VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (
                    user["id"],
                    hash_token(session_token),
                    hash_token(csrf_token),
                    expires_at,
                    request.client.host if request.client else None,
                    (request.headers.get("user-agent") or "")[:500],
                ),
            )

    if login_error is not None:
        raise login_error
    assert user is not None and session_token is not None and csrf_token is not None and expires_at is not None
    _set_session_cookies(response, session_token, csrf_token)
    return {
        "user": {
            "id": str(user["id"]),
            "company_id": str(user["company_id"]) if user["company_id"] else None,
            "display_name": user["display_name"],
            "login_name": user["login_name"],
            "company_role": user["company_role"],
        },
        "expires_at": expires_at,
    }


@router.get("/me")
def me(user: dict[str, Any] = Depends(current_portal_user)) -> dict:
    return {
        "id": str(user["user_id"]),
        "company_id": str(user["company_id"]) if user["company_id"] else None,
        "display_name": user["display_name"],
        "login_name": user["login_name"],
        "personal_email": user["personal_email"],
        "personal_phone": user["personal_phone"],
        "company_role": user["company_role"],
    }


@router.post("/logout", status_code=204)
def logout(
    response: Response,
    user: dict[str, Any] = Depends(require_csrf),
) -> Response:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE tb.portal_sessions SET revoked_at = now() WHERE id = %s",
            (user["session_id"],),
        )
    _clear_session_cookies(response)
    return Response(status_code=204)
