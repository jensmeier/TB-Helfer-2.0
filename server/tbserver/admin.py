from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
import re
import secrets
import unicodedata
from typing import Any
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Response
from psycopg.errors import UniqueViolation
from pydantic import BaseModel, Field

from .config import settings
from .database import transaction
from .events import audit, emit_change
from .admin_session import current_admin_session_user, require_admin_session_csrf
from .security import hash_token, random_token


router = APIRouter(prefix="/api/v1/admin", tags=["admin"])

_ALLOWED_COMPANY_ROLES = {"CHEF", "USER"}


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
    first_name: str = Field(min_length=1, max_length=120)
    last_name: str = Field(min_length=1, max_length=120)
    company_role: str = Field(default="USER")
    access_valid_until: datetime | None = None
    access_valid_through: date | None = None


class UserAdminUpdate(BaseModel):
    company_id: UUID | None = None
    first_name: str | None = Field(default=None, min_length=1, max_length=120)
    last_name: str | None = Field(default=None, min_length=1, max_length=120)
    company_role: str | None = None
    access_valid_until: datetime | None = None
    access_valid_through: date | None = None

class AdminWorksiteCreate(BaseModel):
    company_id: UUID
    responsible_tb_user_id: UUID
    name: str = Field(min_length=1, max_length=240)
    designation: str | None = Field(default=None, max_length=300)
    route_number: str | None = Field(default=None, max_length=40)
    station_code: str | None = Field(default=None, max_length=40)
    station_name: str | None = Field(default=None, max_length=240)


_BERLIN_TZ = ZoneInfo("Europe/Berlin")


def _valid_until_from_through(day: date | None) -> datetime | None:
    if day is None:
        return None
    local_expiry = datetime.combine(
        day + timedelta(days=1),
        time(hour=0, minute=1),
        tzinfo=_BERLIN_TZ,
    )
    return local_expiry.astimezone(timezone.utc)


def _required_text(value: str, error_code: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise HTTPException(400, error_code)
    return normalized


def _optional_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None



def _display_name(first_name: str, last_name: str) -> str:
    return f"{first_name.strip()} {last_name.strip()}".strip()


def _login_part(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_text = "".join(ch for ch in normalized if not unicodedata.combining(ch))
    cleaned = re.sub(r"[^a-zA-Z0-9]+", ".", ascii_text).strip(".").lower()
    return cleaned or "nutzer"


def _provisional_login(first_name: str, last_name: str) -> str:
    stem = f"{_login_part(first_name)}.{_login_part(last_name)}".strip(".")
    stem = stem[:110].rstrip(".")
    return f"{stem}-{secrets.randbelow(10000):04d}"


def _assert_company(cur, company_id: UUID) -> None:
    cur.execute(
        """
        SELECT id
        FROM tb.companies
        WHERE id = %s
          AND deleted_at IS NULL
          AND status = 'ACTIVE'
        FOR SHARE
        """,
        (company_id,),
    )
    if not cur.fetchone():
        raise HTTPException(404, "COMPANY_NOT_FOUND")


def _user_has_worksite_or_team_history(cur, user_id: UUID) -> bool:
    cur.execute(
        """
        SELECT EXISTS(SELECT 1 FROM tb.worksites WHERE created_by = %s)
            OR EXISTS(SELECT 1 FROM tb.worksite_nodes WHERE created_by = %s)
            OR EXISTS(SELECT 1 FROM tb.worksite_memberships WHERE user_id = %s)
            OR EXISTS(
                SELECT 1 FROM tb.worksite_invitations
                WHERE invited_user_id = %s OR invited_by = %s
            )
            OR EXISTS(SELECT 1 FROM tb.teams WHERE lead_user_id = %s)
            OR EXISTS(SELECT 1 FROM tb.team_members WHERE user_id = %s)
            OR EXISTS(SELECT 1 FROM tb.btbs WHERE created_by = %s)
            AS locked
        """,
        (user_id, user_id, user_id, user_id, user_id, user_id, user_id, user_id),
    )
    return bool(cur.fetchone()["locked"])

def _assert_admin(user: dict[str, Any]) -> dict[str, Any]:
    if user.get("company_role") != "ADMIN":
        raise HTTPException(403, "ADMIN_REQUIRED")
    if not user.get("user_id"):
        raise HTTPException(401, "ADMIN_SESSION_INVALID")
    return user


def require_admin(
    user: dict[str, Any] = Depends(current_admin_session_user),
) -> dict[str, Any]:
    return _assert_admin(user)


def require_admin_csrf(
    user: dict[str, Any] = Depends(require_admin_session_csrf),
) -> dict[str, Any]:
    return _assert_admin(user)


@router.get("/companies")
def list_companies(_: dict[str, Any] = Depends(require_admin)) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT c.id, c.name, c.street, c.postal_code, c.city, c.country_code,
                   c.phone, c.email, c.status, c.created_at,
                   count(u.id) FILTER (
                       WHERE u.deleted_at IS NULL
                         AND u.company_role <> 'ADMIN'
                   ) AS user_count
            FROM tb.companies c
            LEFT JOIN tb.users u ON u.company_id = c.id
            WHERE c.deleted_at IS NULL
            GROUP BY c.id
            ORDER BY lower(c.name)
            """
        )
        companies = [dict(row) for row in cur.fetchall()]
    return {"companies": companies}


@router.post("/companies", status_code=201)
def create_company(
    payload: CompanyCreate,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict[str, Any]:
    name = _required_text(payload.name, "COMPANY_NAME_REQUIRED")
    country_code = payload.country_code.strip().upper()
    if len(country_code) != 2:
        raise HTTPException(400, "INVALID_COUNTRY_CODE")

    with transaction() as conn, conn.cursor() as cur:
        try:
            cur.execute(
                """
                INSERT INTO tb.companies(
                    name, street, postal_code, city, country_code, phone, email
                )
                VALUES (%s,%s,%s,%s,%s,%s,%s)
                RETURNING id, name, street, postal_code, city, country_code,
                          phone, email, status, created_at
                """,
                (
                    name,
                    _optional_text(payload.street),
                    _optional_text(payload.postal_code),
                    _optional_text(payload.city),
                    country_code,
                    _optional_text(payload.phone),
                    _optional_text(payload.email),
                ),
            )
        except UniqueViolation as exc:
            raise HTTPException(409, "COMPANY_EXISTS") from exc

        row = dict(cur.fetchone())
        audit(
            cur,
            action="COMPANY_CREATE",
            actor_user_id=admin["user_id"],
            company_id=row["id"],
            entity_type="COMPANY",
            entity_id=row["id"],
        )
    return row


@router.post("/worksites", status_code=201)
def admin_create_worksite(
    payload: AdminWorksiteCreate,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict[str, Any]:
    name = _required_text(payload.name, "WORKSITE_NAME_REQUIRED")
    with transaction() as conn, conn.cursor() as cur:
        _assert_company(cur, payload.company_id)
        cur.execute(
            """
            SELECT id, company_id, display_name, company_role, status, deleted_at
            FROM tb.users
            WHERE id = %s
            FOR SHARE
            """,
            (payload.responsible_tb_user_id,),
        )
        responsible = cur.fetchone()
        if (
            not responsible
            or responsible["deleted_at"] is not None
            or responsible["status"] != "ACTIVE"
            or responsible["company_role"] == "ADMIN"
        ):
            raise HTTPException(404, "RESPONSIBLE_TB_USER_NOT_FOUND")
        if responsible["company_id"] != payload.company_id:
            raise HTTPException(409, "RESPONSIBLE_TB_COMPANY_MISMATCH")

        cur.execute(
            """
            INSERT INTO tb.worksites(
                company_id, name, designation, route_number, station_code, station_name, created_by
            ) VALUES (%s,%s,%s,%s,%s,%s,%s)
            RETURNING id, company_id, name, designation, route_number, station_code, station_name,
                      status, version, created_at, updated_at
            """,
            (
                payload.company_id,
                name,
                _optional_text(payload.designation),
                _optional_text(payload.route_number),
                _optional_text(payload.station_code),
                _optional_text(payload.station_name),
                admin["user_id"],
            ),
        )
        worksite = dict(cur.fetchone())

        cur.execute(
            """
            INSERT INTO tb.worksite_memberships(
                worksite_id, user_id, worksite_role, permission_profile, area_scope, invited_by
            ) VALUES (%s,%s,'RESPONSIBLE_TB','RESPONSIBLE_TB','{"all":true}'::jsonb,%s)
            """,
            (worksite["id"], responsible["id"], admin["user_id"]),
        )
        cur.execute(
            """
            INSERT INTO tb.teams(worksite_id, lead_user_id, name)
            VALUES (%s,%s,%s)
            RETURNING id
            """,
            (worksite["id"], responsible["id"], responsible["display_name"]),
        )
        team = cur.fetchone()
        cur.execute(
            "INSERT INTO tb.team_members(team_id, user_id, role) VALUES (%s,%s,'LEAD')",
            (team["id"], responsible["id"]),
        )
        emit_change(
            cur,
            company_id=payload.company_id,
            worksite_id=worksite["id"],
            entity_type="WORKSITE",
            entity_id=worksite["id"],
            operation="CREATE",
            entity_version=worksite["version"],
            actor_user_id=admin["user_id"],
        )
        audit(
            cur,
            action="ADMIN_WORKSITE_CREATE",
            actor_user_id=admin["user_id"],
            company_id=payload.company_id,
            worksite_id=worksite["id"],
            entity_type="WORKSITE",
            entity_id=worksite["id"],
            details={"responsible_tb_user_id": str(responsible["id"])},
        )
    return worksite


@router.get("/users")
def list_users(_: dict[str, Any] = Depends(require_admin)) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.company_id, c.name AS company_name, u.display_name,
                   u.first_name, u.last_name, u.login_name, u.personal_email,
                   u.personal_phone, u.company_role, u.status,
                   u.access_valid_until, u.created_at, u.profile_version,
                   pc.activated_at, pc.last_login_at
            FROM tb.users u
            LEFT JOIN tb.companies c ON c.id = u.company_id
            LEFT JOIN tb.portal_credentials pc ON pc.user_id = u.id
            WHERE u.deleted_at IS NULL
              AND u.company_role <> 'ADMIN'
            ORDER BY lower(coalesce(c.name,'')), lower(u.display_name)
            """
        )
        users = [dict(row) for row in cur.fetchall()]
    return {"users": users}


@router.post("/users", status_code=201)
def create_user(
    payload: UserCreate,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict[str, Any]:
    first_name = _required_text(payload.first_name, "FIRST_NAME_REQUIRED")
    last_name = _required_text(payload.last_name, "LAST_NAME_REQUIRED")
    display_name = _display_name(first_name, last_name)
    role = payload.company_role.strip().upper()
    if role not in _ALLOWED_COMPANY_ROLES:
        raise HTTPException(400, "INVALID_COMPANY_ROLE")

    with transaction() as conn, conn.cursor() as cur:
        _assert_company(cur, payload.company_id)
        row = None
        for attempt in range(50):
            login_name = _provisional_login(first_name, last_name)
            savepoint = f"provisional_login_{attempt}"
            cur.execute(f"SAVEPOINT {savepoint}")
            try:
                cur.execute(
                    """
                    INSERT INTO tb.users(
                        company_id, first_name, last_name, display_name, login_name,
                        personal_email, personal_phone, company_role, access_valid_until
                    )
                    VALUES (%s,%s,%s,%s,%s,NULL,NULL,%s,%s)
                    RETURNING id, company_id, display_name, first_name, last_name,
                              login_name, personal_email, personal_phone,
                              company_role, status, access_valid_until, profile_version, created_at
                    """,
                    (
                        payload.company_id,
                        first_name,
                        last_name,
                        display_name,
                        login_name,
                        role,
                        (
                            _valid_until_from_through(payload.access_valid_through)
                            if "access_valid_through" in payload.model_fields_set
                            else payload.access_valid_until
                        ),
                    ),
                )
                row = dict(cur.fetchone())
                cur.execute(f"RELEASE SAVEPOINT {savepoint}")
                break
            except UniqueViolation:
                cur.execute(f"ROLLBACK TO SAVEPOINT {savepoint}")
                cur.execute(f"RELEASE SAVEPOINT {savepoint}")
        if row is None:
            raise HTTPException(503, "PROVISIONAL_LOGIN_GENERATION_FAILED")

        cur.execute(
            """
            INSERT INTO tb.portal_credentials(user_id)
            VALUES (%s)
            ON CONFLICT (user_id) DO NOTHING
            """,
            (row["id"],),
        )
        audit(
            cur,
            action="USER_CREATE",
            actor_user_id=admin["user_id"],
            company_id=row["company_id"],
            entity_type="USER",
            entity_id=row["id"],
            details={"company_role": row["company_role"], "provisional_login": True},
        )
    return row


@router.patch("/users/{user_id}")
def update_user(
    user_id: UUID,
    payload: UserAdminUpdate,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.company_id, u.first_name, u.last_name, u.display_name,
                   u.login_name, u.company_role, u.status, u.access_valid_until,
                   u.deleted_at, pc.activated_at
            FROM tb.users u
            LEFT JOIN tb.portal_credentials pc ON pc.user_id = u.id
            WHERE u.id = %s
            FOR UPDATE OF u
            """,
            (user_id,),
        )
        current = cur.fetchone()
        if not current or current["deleted_at"] is not None:
            raise HTTPException(404, "USER_NOT_FOUND")
        if current["company_role"] == "ADMIN":
            raise HTTPException(409, "ADMIN_USER_CANNOT_BE_EDITED")

        next_company = (
            payload.company_id
            if "company_id" in payload.model_fields_set and payload.company_id is not None
            else current["company_id"]
        )
        if next_company != current["company_id"]:
            _assert_company(cur, next_company)
            if _user_has_worksite_or_team_history(cur, user_id):
                raise HTTPException(409, "USER_COMPANY_CHANGE_BLOCKED_BY_HISTORY")

        first_name = (
            _required_text(payload.first_name, "FIRST_NAME_REQUIRED")
            if "first_name" in payload.model_fields_set and payload.first_name is not None
            else current["first_name"]
        )
        last_name = (
            _required_text(payload.last_name, "LAST_NAME_REQUIRED")
            if "last_name" in payload.model_fields_set and payload.last_name is not None
            else current["last_name"]
        )
        if not first_name or not last_name:
            raise HTTPException(400, "USER_NAME_REQUIRED")

        role = current["company_role"]
        if "company_role" in payload.model_fields_set and payload.company_role is not None:
            role = payload.company_role.strip().upper()
            if role not in _ALLOWED_COMPANY_ROLES:
                raise HTTPException(400, "INVALID_COMPANY_ROLE")

        if "access_valid_through" in payload.model_fields_set:
            valid_until = _valid_until_from_through(payload.access_valid_through)
        elif "access_valid_until" in payload.model_fields_set:
            valid_until = payload.access_valid_until
        else:
            valid_until = current["access_valid_until"]
        display_name = (
            _display_name(first_name, last_name)
            if current["activated_at"] is None
            else current["display_name"]
        )

        cur.execute(
            """
            UPDATE tb.users
            SET company_id=%s, first_name=%s, last_name=%s, display_name=%s,
                company_role=%s, access_valid_until=%s,
                profile_version=profile_version+1, updated_at=now()
            WHERE id=%s
            RETURNING id, company_id, first_name, last_name, display_name, login_name,
                      personal_email, personal_phone, company_role, status,
                      access_valid_until, profile_version, created_at
            """,
            (next_company, first_name, last_name, display_name, role, valid_until, user_id),
        )
        updated = dict(cur.fetchone())
        audit(
            cur,
            action="USER_ADMIN_UPDATE",
            actor_user_id=admin["user_id"],
            company_id=updated["company_id"],
            entity_type="USER",
            entity_id=user_id,
            details={
                "company_changed": next_company != current["company_id"],
                "role_changed": role != current["company_role"],
                "name_changed": first_name != current["first_name"] or last_name != current["last_name"],
                "validity_changed": valid_until != current["access_valid_until"],
            },
        )
    return updated


@router.post("/users/{user_id}/portal-activation")
def issue_portal_activation(
    user_id: UUID,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict[str, Any]:
    raw_token = random_token(32)
    expires_at = datetime.now(timezone.utc) + timedelta(hours=24)

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.company_id, u.display_name, u.login_name,
                   u.company_role, u.status, u.deleted_at,
                   pc.activated_at
            FROM tb.users u
            LEFT JOIN tb.portal_credentials pc ON pc.user_id = u.id
            WHERE u.id = %s
            FOR UPDATE OF u
            """,
            (user_id,),
        )
        user = cur.fetchone()
        if not user:
            raise HTTPException(404, "USER_NOT_FOUND")
        if user["company_role"] == "ADMIN":
            raise HTTPException(409, "ADMIN_PORTAL_ACTIVATION_NOT_ALLOWED")
        if user["deleted_at"] is not None or user["status"] != "ACTIVE":
            raise HTTPException(409, "USER_NOT_ACTIVE")
        if user["activated_at"] is not None:
            raise HTTPException(409, "PORTAL_ALREADY_ACTIVATED")

        cur.execute(
            """
            INSERT INTO tb.portal_credentials(
                user_id, activation_token_hash, activation_expires_at,
                must_change_password, updated_at
            )
            VALUES (%s,%s,%s,true,now())
            ON CONFLICT (user_id) DO UPDATE
            SET activation_token_hash = excluded.activation_token_hash,
                activation_expires_at = excluded.activation_expires_at,
                must_change_password = true,
                failed_login_count = 0,
                locked_until = NULL,
                updated_at = now()
            """,
            (user_id, hash_token(raw_token), expires_at),
        )
        audit(
            cur,
            action="PORTAL_ACTIVATION_ISSUE",
            actor_user_id=admin["user_id"],
            company_id=user["company_id"],
            entity_type="USER",
            entity_id=user_id,
        )

    portal_base = (settings.portal_public_base_url or settings.public_base_url or "").rstrip("/")
    activation_url = (
        f"{portal_base}/portal/v1/activate?token={raw_token}&login={user['login_name']}"
        if portal_base
        else None
    )
    return {
        "user_id": str(user_id),
        "login_name": user["login_name"],
        "activation_token": raw_token,
        "activation_url": activation_url,
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
            SELECT id, company_id, display_name, login_name,
                   company_role, status, deleted_at
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
              AND active_btb_id IS NOT NULL
            LIMIT 1
            """,
            (user_id,),
        )
        if cur.fetchone():
            raise HTTPException(409, "USER_HAS_ACTIVE_BTB")

        # Browserzugang und offene Portal-Aktivierung sofort ungültig machen.
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

        # Noch nicht verwendete Geräte-Aktivierungskarten und alle Gerätezugänge sperren.
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
                auth_token_hash = NULL,
                auth_token_hint = NULL,
                token_version = token_version + 1,
                updated_at = now()
            WHERE user_id = %s
              AND revoked_at IS NULL
            """,
            (user_id,),
        )

        # Nur laufende Zuordnungen beenden; historische Fachreferenzen bleiben erhalten.
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
            WHERE (invited_user_id = %s OR invited_by = %s)
              AND accepted_at IS NULL
              AND revoked_at IS NULL
            """,
            (user_id, user_id),
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
        audit(
            cur,
            action="USER_SOFT_DELETE",
            actor_user_id=admin["user_id"],
            company_id=target["company_id"],
            entity_type="USER",
            entity_id=user_id,
            details={"previous_status": target["status"]},
        )

    return Response(status_code=204)
