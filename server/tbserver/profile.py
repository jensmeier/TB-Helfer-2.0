from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from .database import transaction
from .events import audit
from .portal import current_portal_user, require_csrf

router = APIRouter(prefix="/api/v1/user", tags=["user-profile"])


class ContactProfileUpdate(BaseModel):
    email: str | None = Field(default=None, max_length=320)
    phone: str | None = Field(default=None, max_length=100)


class FullProfileUpdate(ContactProfileUpdate):
    display_name: str | None = Field(default=None, min_length=1, max_length=200)
    login_name: str | None = Field(default=None, min_length=3, max_length=120)


def _optional(value: str | None) -> str | None:
    if value is None:
        return None
    text = value.strip()
    return text or None


def _profile_payload(row: dict[str, Any]) -> dict[str, Any]:
    full_name = " ".join(
        part for part in (row.get("first_name"), row.get("last_name"))
        if part and str(part).strip()
    ).strip()
    return {
        "user_id": str(row["id"]),
        "full_name": full_name or row.get("display_name"),
        "display_name": row.get("display_name"),
        "login_name": row.get("login_name"),
        "email": row.get("personal_email"),
        "phone": row.get("personal_phone"),
        "profile_version": int(row.get("profile_version") or 1),
    }


def _load_user(cur, user_id, *, for_update: bool = False) -> dict[str, Any]:
    suffix = " FOR UPDATE" if for_update else ""
    cur.execute(
        f"""
        SELECT id, company_id, first_name, last_name, display_name, login_name,
               personal_email, personal_phone, company_role, status,
               access_valid_until, profile_version, deleted_at
        FROM tb.users
        WHERE id = %s{suffix}
        """,
        (user_id,),
    )
    row = cur.fetchone()
    if not row or row["deleted_at"] is not None:
        raise HTTPException(404, "USER_NOT_FOUND")
    return dict(row)


def _assert_login_available(cur, login_name: str, user_id) -> None:
    cur.execute(
        """
        SELECT id FROM tb.users
        WHERE lower(login_name) = lower(%s)
          AND deleted_at IS NULL
          AND id <> %s
        LIMIT 1
        """,
        (login_name, user_id),
    )
    if cur.fetchone():
        raise HTTPException(409, "LOGIN_NAME_EXISTS")


@router.get("/profile")
def get_profile(user: dict[str, Any] = Depends(current_portal_user)) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        row = _load_user(cur, user["user_id"])
    return {"profile": _profile_payload(row)}


@router.put("/profile")
def update_contacts(
    payload: ContactProfileUpdate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict[str, Any]:
    with transaction() as conn, conn.cursor() as cur:
        before = _load_user(cur, user["user_id"], for_update=True)
        cur.execute(
            """
            UPDATE tb.users
            SET personal_email = %s,
                personal_phone = %s,
                profile_version = profile_version + 1,
                updated_at = now()
            WHERE id = %s
            RETURNING id, company_id, first_name, last_name, display_name, login_name,
                      personal_email, personal_phone, company_role, status,
                      access_valid_until, profile_version, deleted_at
            """,
            (_optional(payload.email), _optional(payload.phone), user["user_id"]),
        )
        after = dict(cur.fetchone())
        audit(
            cur,
            action="USER_PROFILE_CONTACTS_UPDATE",
            actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),
            company_id=after["company_id"],
            entity_type="USER",
            entity_id=after["id"],
            details={
                "profile_version": after["profile_version"],
                "email_changed": before["personal_email"] != after["personal_email"],
                "phone_changed": before["personal_phone"] != after["personal_phone"],
            },
        )
    return {"profile": _profile_payload(after)}


@router.patch("/profile")
def update_own_profile(
    payload: FullProfileUpdate,
    user: dict[str, Any] = Depends(require_csrf),
) -> dict[str, Any]:
    display_name = _optional(payload.display_name)
    login_name = _optional(payload.login_name)
    if payload.display_name is not None and display_name is None:
        raise HTTPException(400, "DISPLAY_NAME_REQUIRED")
    if payload.login_name is not None and login_name is None:
        raise HTTPException(400, "LOGIN_NAME_REQUIRED")

    with transaction() as conn, conn.cursor() as cur:
        before = _load_user(cur, user["user_id"], for_update=True)
        next_display = display_name if payload.display_name is not None else before["display_name"]
        next_login = login_name if payload.login_name is not None else before["login_name"]
        _assert_login_available(cur, next_login, user["user_id"])
        next_email = _optional(payload.email) if "email" in payload.model_fields_set else before["personal_email"]
        next_phone = _optional(payload.phone) if "phone" in payload.model_fields_set else before["personal_phone"]
        cur.execute(
            """
            UPDATE tb.users
            SET display_name = %s,
                login_name = %s,
                personal_email = %s,
                personal_phone = %s,
                profile_version = profile_version + 1,
                updated_at = now()
            WHERE id = %s
            RETURNING id, company_id, first_name, last_name, display_name, login_name,
                      personal_email, personal_phone, company_role, status,
                      access_valid_until, profile_version, deleted_at
            """,
            (next_display, next_login, next_email, next_phone, user["user_id"]),
        )
        after = dict(cur.fetchone())
        audit(
            cur,
            action="USER_PROFILE_SELF_UPDATE",
            actor_user_id=user["user_id"],
            actor_device_id=user.get("device_id"),
            company_id=after["company_id"],
            entity_type="USER",
            entity_id=after["id"],
            details={"profile_version": after["profile_version"]},
        )
    return {"profile": _profile_payload(after)}


@router.get("/directory")
def company_directory(
    user: dict[str, Any] = Depends(current_portal_user),
) -> dict[str, Any]:
    company_id = user.get("company_id")
    if not company_id:
        if user.get("company_role") == "ADMIN":
            return {"users": []}
        raise HTTPException(404, "COMPANY_NOT_ASSIGNED")
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, display_name, first_name, last_name, personal_email,
                   personal_phone, company_role
            FROM tb.users
            WHERE company_id = %s
              AND deleted_at IS NULL
              AND status = 'ACTIVE'
              AND company_role <> 'ADMIN'
            ORDER BY lower(display_name), id
            """,
            (company_id,),
        )
        rows = [dict(row) for row in cur.fetchall()]
    return {
        "users": [
            {
                "id": str(row["id"]),
                "display_name": row["display_name"],
                "first_name": row["first_name"],
                "last_name": row["last_name"],
                "personal_email": row["personal_email"],
                "personal_phone": row["personal_phone"],
                "company_role": row["company_role"],
            }
            for row in rows
        ]
    }
