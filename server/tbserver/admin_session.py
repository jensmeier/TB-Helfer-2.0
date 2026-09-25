from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

from .config import settings
from .database import transaction
from .portal import _find_login, _now
from .security import hash_token, random_token, verify_password


router = APIRouter(prefix="/api/v1/admin/session", tags=["admin-session"])
ADMIN_SESSION_COOKIE = "tb_admin_session"
ADMIN_CSRF_COOKIE = "tb_admin_csrf"


class AdminLoginRequest(BaseModel):
    login: str = Field(min_length=1, max_length=320)
    password: str = Field(min_length=1, max_length=200)


def _set_admin_cookies(response: Response, session_token: str, csrf_token: str) -> None:
    secure = settings.environment == "production"
    response.set_cookie(
        ADMIN_SESSION_COOKIE,
        session_token,
        max_age=settings.portal_session_hours * 3600,
        httponly=True,
        secure=secure,
        samesite="lax",
        path="/",
    )
    response.set_cookie(
        ADMIN_CSRF_COOKIE,
        csrf_token,
        max_age=settings.portal_session_hours * 3600,
        httponly=False,
        secure=secure,
        samesite="lax",
        path="/",
    )


def _clear_admin_cookies(response: Response) -> None:
    response.delete_cookie(ADMIN_SESSION_COOKIE, path="/")
    response.delete_cookie(ADMIN_CSRF_COOKIE, path="/")


def current_admin_session_user(request: Request) -> dict[str, Any]:
    raw = request.cookies.get(ADMIN_SESSION_COOKIE)
    if not raw:
        raise HTTPException(401, "ADMIN_SESSION_MISSING")
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
            raise HTTPException(401, "ADMIN_SESSION_INVALID")
        now = _now()
        if row["expires_at"] <= now:
            raise HTTPException(401, "ADMIN_SESSION_EXPIRED")
        if row["deleted_at"] is not None or row["status"] != "ACTIVE":
            raise HTTPException(403, "USER_DISABLED")
        if row["access_valid_until"] is not None and row["access_valid_until"] <= now:
            raise HTTPException(403, "USER_EXPIRED")
        if row["company_role"] != "ADMIN":
            raise HTTPException(403, "ADMIN_REQUIRED")
        cur.execute("UPDATE tb.portal_sessions SET last_seen_at = now() WHERE id = %s", (row["session_id"],))
        result = dict(row)
        result["auth_kind"] = "ADMIN_PORTAL"
        result["session_scope"] = "ADMIN"
        return result


def require_admin_session_csrf(
    request: Request,
    user: dict[str, Any] = Depends(current_admin_session_user),
) -> dict[str, Any]:
    cookie = request.cookies.get(ADMIN_CSRF_COOKIE)
    header = request.headers.get("X-CSRF-Token")
    if not cookie or not header or cookie != header:
        raise HTTPException(403, "CSRF_INVALID")
    if hash_token(header) != user["csrf_secret_hash"]:
        raise HTTPException(403, "CSRF_INVALID")
    return user


@router.post("/login")
def admin_login(payload: AdminLoginRequest, request: Request, response: Response) -> dict[str, Any]:
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
        elif user["company_role"] != "ADMIN":
            login_error = HTTPException(403, "ADMIN_REQUIRED")
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
    _set_admin_cookies(response, session_token, csrf_token)
    return {
        "user": {
            "id": str(user["id"]),
            "display_name": user["display_name"],
            "login_name": user["login_name"],
            "company_role": user["company_role"],
        },
        "expires_at": expires_at,
    }


@router.get("/me")
def admin_me(user: dict[str, Any] = Depends(current_admin_session_user)) -> dict[str, Any]:
    return {
        "id": str(user["user_id"]),
        "display_name": user["display_name"],
        "login_name": user["login_name"],
        "company_role": user["company_role"],
    }


@router.post("/logout", status_code=204)
def admin_logout(
    response: Response,
    user: dict[str, Any] = Depends(require_admin_session_csrf),
) -> Response:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("UPDATE tb.portal_sessions SET revoked_at = now() WHERE id = %s", (user["session_id"],))
    _clear_admin_cookies(response)
    return Response(status_code=204)
