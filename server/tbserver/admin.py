from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field

from .database import transaction
from .portal import current_portal_user, require_csrf
from .security import hash_token, random_token


router = APIRouter(prefix="/api/v1/admin", tags=["admin"])


class CompanyCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    street: str | None = Field(default=None, max_length=300)
    postal_code: str | None = Field(default=None, max_length=30)
    city: str | None = Field(default=None, max_length=200)
    country_code: str = Field(default="DE", min_length=2, max_length=2)
    phone: str | None = Field(default=None, max_length=100)
    email: str | None = Field(default=None, max_length=320)


class UserCreate(BaseModel):
    company_id: UUID
    display_name: str = Field(min_length=1, max_length=200)
    first_name: str | None = Field(default=None, max_length=120)
    last_name: str | None = Field(default=None, max_length=120)
    login_name: str = Field(min_length=3, max_length=120)
    personal_email: str | None = Field(default=None, max_length=320)
    personal_phone: str | None = Field(default=None, max_length=100)
    company_role: str = Field(default="USER")


def _assert_admin(user: dict[str, Any]) -> dict[str, Any]:
    if user["company_role"] != "ADMIN":
        raise HTTPException(403, "ADMIN_REQUIRED")
    return user


def require_admin(user: dict[str, Any] = Depends(current_portal_user)) -> dict[str, Any]:
    return _assert_admin(user)


def require_admin_csrf(user: dict[str, Any] = Depends(require_csrf)) -> dict[str, Any]:
    return _assert_admin(user)


@router.get("/companies")
def list_companies(_: dict[str, Any] = Depends(require_admin)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT c.id, c.name, c.street, c.postal_code, c.city, c.country_code,
                   c.phone, c.email, c.status, c.created_at,
                   count(u.id) FILTER (WHERE u.deleted_at IS NULL) AS user_count
            FROM tb.companies c
            LEFT JOIN tb.users u ON u.company_id = c.id
            WHERE c.deleted_at IS NULL
            GROUP BY c.id
            ORDER BY lower(c.name)
            """
        )
        rows = cur.fetchall()
    return {"companies": rows}


@router.post("/companies", status_code=201)
def create_company(
    payload: CompanyCreate,
    _: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        try:
            cur.execute(
                """
                INSERT INTO tb.companies(name, street, postal_code, city, country_code, phone, email)
                VALUES (%s,%s,%s,%s,%s,%s,%s)
                RETURNING id, name, street, postal_code, city, country_code, phone, email, status, created_at
                """,
                (
                    payload.name.strip(),
                    payload.street,
                    payload.postal_code,
                    payload.city,
                    payload.country_code.upper(),
                    payload.phone,
                    payload.email,
                ),
            )
        except Exception as exc:
            if getattr(exc, "sqlstate", None) == "23505":
                raise HTTPException(409, "COMPANY_EXISTS") from exc
            raise
        row = cur.fetchone()
    return row


@router.get("/users")
def list_users(_: dict[str, Any] = Depends(require_admin)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.company_id, c.name AS company_name, u.display_name, u.first_name,
                   u.last_name, u.login_name, u.personal_email, u.personal_phone,
                   u.company_role, u.status, u.access_valid_until, u.created_at,
                   pc.activated_at, pc.last_login_at
            FROM tb.users u
            LEFT JOIN tb.companies c ON c.id = u.company_id
            LEFT JOIN tb.portal_credentials pc ON pc.user_id = u.id
            WHERE u.deleted_at IS NULL
            ORDER BY lower(coalesce(c.name,'')), lower(u.display_name)
            """
        )
        rows = cur.fetchall()
    return {"users": rows}


@router.post("/users", status_code=201)
def create_user(
    payload: UserCreate,
    _: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    role = payload.company_role.upper()
    if role not in {"CHEF", "USER"}:
        raise HTTPException(400, "INVALID_COMPANY_ROLE")
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM tb.companies WHERE id = %s AND deleted_at IS NULL AND status = 'ACTIVE'",
            (payload.company_id,),
        )
        if not cur.fetchone():
            raise HTTPException(404, "COMPANY_NOT_FOUND")
        try:
            cur.execute(
                """
                INSERT INTO tb.users(
                    company_id, first_name, last_name, display_name, login_name,
                    personal_email, personal_phone, company_role
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
                RETURNING id, company_id, display_name, first_name, last_name, login_name,
                          personal_email, personal_phone, company_role, status, created_at
                """,
                (
                    payload.company_id,
                    payload.first_name,
                    payload.last_name,
                    payload.display_name.strip(),
                    payload.login_name.strip(),
                    payload.personal_email,
                    payload.personal_phone,
                    role,
                ),
            )
        except Exception as exc:
            if getattr(exc, "sqlstate", None) == "23505":
                raise HTTPException(409, "LOGIN_NAME_EXISTS") from exc
            raise
        row = cur.fetchone()
        cur.execute(
            "INSERT INTO tb.portal_credentials(user_id) VALUES (%s)",
            (row["id"],),
        )
    return row


@router.post("/users/{user_id}/portal-activation")
def issue_portal_activation(
    user_id: UUID,
    _: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    raw_token = random_token(32)
    expires_at = datetime.now(timezone.utc) + timedelta(hours=24)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.display_name, u.login_name, u.status, u.deleted_at
            FROM tb.users u
            WHERE u.id = %s
            FOR UPDATE
            """,
            (user_id,),
        )
        user = cur.fetchone()
        if not user:
            raise HTTPException(404, "USER_NOT_FOUND")
        if user["deleted_at"] is not None or user["status"] != "ACTIVE":
            raise HTTPException(409, "USER_NOT_ACTIVE")
        cur.execute(
            """
            INSERT INTO tb.portal_credentials(
                user_id, activation_token_hash, activation_expires_at, must_change_password, updated_at
            ) VALUES (%s,%s,%s,true,now())
            ON CONFLICT (user_id) DO UPDATE
            SET activation_token_hash = excluded.activation_token_hash,
                activation_expires_at = excluded.activation_expires_at,
                must_change_password = true,
                updated_at = now()
            """,
            (user_id, hash_token(raw_token), expires_at),
        )
    return {
        "user_id": str(user_id),
        "login_name": user["login_name"],
        "activation_token": raw_token,
        "expires_at": expires_at,
    }


@router.delete("/users/{user_id}", status_code=204)
def delete_user(
    user_id: UUID,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, display_name, login_name, company_role, status, deleted_at
            FROM tb.users
            WHERE id = %s
            FOR UPDATE
            """,
            (user_id,),
        )
        target = cur.fetchone()
        if not target or target["deleted_at"] is not None:
            raise HTTPException(404, "USER_NOT_FOUND")
        if target["company_role"] == "ADMIN":
            raise HTTPException(409, "ADMIN_USER_CANNOT_BE_DELETED")
        if str(admin["user_id"]) == str(user_id):
            raise HTTPException(409, "CANNOT_DELETE_SELF")

        cur.execute(
            """
            SELECT id
            FROM tb.teams
            WHERE lead_user_id = %s
              AND status = 'ACTIVE'
              AND active_btb_id IS NOT NULL
            LIMIT 1
            """,
            (user_id,),
        )
        if cur.fetchone():
            raise HTTPException(409, "USER_HAS_ACTIVE_BTB")

        # Alle direkten Zugänge sofort ungültig machen.
        cur.execute(
            """
            UPDATE tb.portal_sessions
            SET revoked_at = COALESCE(revoked_at, now())
            WHERE user_id = %s
              AND revoked_at IS NULL
            """,
            (user_id,),
        )
        cur.execute(
            """
            UPDATE tb.portal_credentials
            SET password_hash = NULL,
                activation_token_hash = NULL,
                activation_expires_at = NULL,
                must_change_password = true,
                failed_login_count = 0,
                locked_until = NULL,
                activated_at = NULL,
                updated_at = now()
            WHERE user_id = %s
            """,
            (user_id,),
        )
        cur.execute(
            """
            UPDATE tb.device_activation_tokens
            SET revoked_at = COALESCE(revoked_at, now())
            WHERE user_id = %s
              AND used_at IS NULL
              AND revoked_at IS NULL
            """,
            (user_id,),
        )
        cur.execute(
            """
            UPDATE tb.devices
            SET status = 'REVOKED',
                revoked_at = COALESCE(revoked_at, now()),
                token_version = token_version + 1,
                updated_at = now()
            WHERE user_id = %s
              AND revoked_at IS NULL
            """,
            (user_id,),
        )

        # Laufende Zuordnungen beenden; historische Referenzen bleiben erhalten.
        cur.execute(
            """
            UPDATE tb.worksite_memberships
            SET status = 'REMOVED',
                removed_at = COALESCE(removed_at, now()),
                updated_at = now()
            WHERE user_id = %s
              AND status = 'ACTIVE'
            """,
            (user_id,),
        )
        cur.execute(
            """
            UPDATE tb.team_members
            SET removed_at = COALESCE(removed_at, now())
            WHERE user_id = %s
              AND removed_at IS NULL
            """,
            (user_id,),
        )
        cur.execute(
            """
            UPDATE tb.teams
            SET status = 'ARCHIVED',
                active_btb_id = NULL,
                updated_at = now()
            WHERE lead_user_id = %s
              AND status = 'ACTIVE'
            """,
            (user_id,),
        )
        cur.execute(
            """
            UPDATE tb.worksite_invitations
            SET revoked_at = COALESCE(revoked_at, now())
            WHERE invited_user_id = %s
              AND accepted_at IS NULL
              AND revoked_at IS NULL
            """,
            (user_id,),
        )

        cur.execute(
            """
            UPDATE tb.users
            SET status = 'DISABLED',
                deleted_at = now(),
                updated_at = now()
            WHERE id = %s
            """,
            (user_id,),
        )

    return Response(status_code=204)
