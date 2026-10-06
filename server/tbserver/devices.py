from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

from .admin import require_admin, require_admin_csrf
from .config import settings
from .database import transaction
from .permissions import require_active_company
from .device_activation_links import device_activation_app_uri, device_activation_qr_svg_data_url
from .device_activation_pdf import device_activation_pdf_data_url
from .events import audit
from .security import hash_token, random_activation_key, random_token


router = APIRouter(prefix="/api/v1/device", tags=["devices"])
admin_router = APIRouter(prefix="/api/v1/admin", tags=["admin-devices"])


class DeviceLoginRequest(BaseModel):
    key: str = Field(min_length=16, max_length=500)
    device_uid: str = Field(min_length=8, max_length=255)
    device_name: str | None = Field(default=None, max_length=255)
    os_version: str | None = Field(default=None, max_length=255)
    app_version: str | None = Field(default=None, max_length=255)
    app_build: int | None = Field(default=None, ge=0)
    platform: str = Field(default="ANDROID", max_length=20)


class DeviceActivationCreate(BaseModel):
    expires_hours: int = Field(default=30 * 24, ge=1, le=30 * 24)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _bearer_token(request: Request) -> str | None:
    header = (request.headers.get("Authorization") or "").strip()
    if not header:
        return None
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(401, "DEVICE_TOKEN_INVALID")
    return token.strip()


def _reported_app_info(request: Request) -> tuple[str | None, int | None]:
    version = (request.headers.get("X-TB-App-Version") or "").strip()
    if len(version) > 255:
        version = version[:255]
    raw_build = (request.headers.get("X-TB-App-Build") or "").strip()
    build: int | None = None
    if raw_build:
        try:
            candidate = int(raw_build)
            if 0 <= candidate <= 2_147_483_647:
                build = candidate
        except ValueError:
            build = None
    return (version or None, build)


def current_device_user(request: Request) -> dict[str, Any]:
    raw = _bearer_token(request)
    if not raw:
        raise HTTPException(401, "DEVICE_TOKEN_MISSING")
    token_hash = hash_token(raw)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT d.id AS device_id, d.user_id, d.device_uid, d.device_name, d.platform,
                   d.app_version, d.app_build, d.status AS device_status, d.revoked_at,
                   u.company_id, u.display_name, u.login_name, u.personal_email,
                   u.personal_phone, u.company_role, u.status, u.access_valid_until,
                   u.deleted_at, co.status AS company_status, co.deleted_at AS company_deleted_at
            FROM tb.devices d
            JOIN tb.users u ON u.id = d.user_id
            LEFT JOIN tb.companies co ON co.id = u.company_id
            WHERE d.auth_token_hash = %s
            """,
            (token_hash,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(401, "DEVICE_TOKEN_INVALID")
        now = _now()
        if row["revoked_at"] is not None or row["device_status"] != "ACTIVE":
            raise HTTPException(403, "DEVICE_REVOKED")
        if row["deleted_at"] is not None or row["status"] != "ACTIVE":
            raise HTTPException(403, "USER_DISABLED")
        if row["access_valid_until"] is not None and row["access_valid_until"] <= now:
            raise HTTPException(403, "USER_EXPIRED")
        require_active_company(row)
        reported_version, reported_build = _reported_app_info(request)
        cur.execute(
            """
            UPDATE tb.devices
            SET last_seen_at = now(),
                app_version = COALESCE(%s, app_version),
                app_build = COALESCE(%s, app_build),
                updated_at = now()
            WHERE id = %s
            """,
            (reported_version, reported_build, row["device_id"]),
        )
        result = dict(row)
        if reported_version is not None:
            result["app_version"] = reported_version
        if reported_build is not None:
            result["app_build"] = reported_build
        result["auth_kind"] = "DEVICE"
        return result


@router.post("/login")
def device_login(payload: DeviceLoginRequest) -> dict:
    platform = payload.platform.upper().strip()
    if platform not in {"ANDROID", "IOS", "WINDOWS", "OTHER"}:
        raise HTTPException(400, "INVALID_PLATFORM")
    activation_hash = hash_token(payload.key.strip())
    raw_device_token = random_token(32)
    device_token_hash = hash_token(raw_device_token)
    device_token_hint = raw_device_token[-6:]

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT t.id, t.user_id, t.expires_at, t.used_at, t.revoked_at,
                   u.company_id, u.display_name, u.login_name, u.personal_email,
                   u.personal_phone, u.company_role, u.status, u.access_valid_until,
                   u.deleted_at, co.status AS company_status, co.deleted_at AS company_deleted_at
            FROM tb.device_activation_tokens t
            JOIN tb.users u ON u.id = t.user_id
            LEFT JOIN tb.companies co ON co.id = u.company_id
            WHERE t.token_hash = %s
            FOR UPDATE OF t, u
            """,
            (activation_hash,),
        )
        activation = cur.fetchone()
        if not activation or activation["used_at"] is not None or activation["revoked_at"] is not None:
            raise HTTPException(401, "ACTIVATION_INVALID")
        now = _now()
        if activation["expires_at"] <= now:
            raise HTTPException(401, "ACTIVATION_EXPIRED")
        if activation["deleted_at"] is not None or activation["status"] != "ACTIVE":
            raise HTTPException(403, "USER_DISABLED")
        if activation["access_valid_until"] is not None and activation["access_valid_until"] <= now:
            raise HTTPException(403, "USER_EXPIRED")
        require_active_company(activation)

        # Serialize all activations for the same user before checking device ownership.
        cur.execute("SELECT id FROM tb.users WHERE id = %s FOR UPDATE", (activation["user_id"],))
        cur.execute(
            "SELECT id, user_id, status FROM tb.devices WHERE device_uid = %s AND revoked_at IS NULL",
            (payload.device_uid,),
        )
        same_uid = cur.fetchone()
        if same_uid:
            if same_uid["user_id"] == activation["user_id"]:
                raise HTTPException(409, "DEVICE_ALREADY_BOUND")
            raise HTTPException(409, "DEVICE_IN_USE")

        cur.execute(
            "SELECT id FROM tb.devices WHERE user_id = %s AND status = 'ACTIVE' AND revoked_at IS NULL",
            (activation["user_id"],),
        )
        if cur.fetchone():
            raise HTTPException(409, "DEVICE_ALREADY_BOUND")

        cur.execute(
            """
            INSERT INTO tb.devices(
                user_id, device_uid, platform, device_name, os_version,
                app_version, app_build, auth_token_hash, auth_token_hint
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
            RETURNING id, user_id, device_uid, platform, device_name, os_version,
                      app_version, app_build, status, bound_at
            """,
            (
                activation["user_id"], payload.device_uid, platform, payload.device_name,
                payload.os_version, payload.app_version, payload.app_build,
                device_token_hash, device_token_hint,
            ),
        )
        device = dict(cur.fetchone())
        cur.execute("UPDATE tb.device_activation_tokens SET used_at = now() WHERE id = %s", (activation["id"],))
        cur.execute(
            """
            UPDATE tb.device_activation_tokens
            SET revoked_at = now()
            WHERE user_id = %s
              AND id <> %s
              AND used_at IS NULL
              AND revoked_at IS NULL
            """,
            (activation["user_id"], activation["id"]),
        )
        audit(
            cur,
            action="DEVICE_ACTIVATE",
            actor_user_id=activation["user_id"],
            actor_device_id=device["id"],
            company_id=activation["company_id"],
            entity_type="DEVICE",
            entity_id=device["id"],
            details={"platform": platform, "device_uid_hint": payload.device_uid[-8:]},
        )

    return {
        "token": raw_device_token,
        "device": device,
        "user": {
            "id": activation["user_id"],
            "company_id": activation["company_id"],
            "name": activation["display_name"],
            "login_name": activation["login_name"],
            "personal_email": activation["personal_email"],
            "personal_phone": activation["personal_phone"],
            "company_role": activation["company_role"],
        },
    }


@router.get("/me")
def device_me(user: dict[str, Any] = Depends(current_device_user)) -> dict:
    return {
        "device": {
            "id": user["device_id"],
            "uid": user["device_uid"],
            "name": user["device_name"],
            "platform": user["platform"],
            "app_version": user["app_version"],
            "app_build": user["app_build"],
        },
        "user": {
            "id": user["user_id"],
            "company_id": user["company_id"],
            "name": user["display_name"],
            "login_name": user["login_name"],
            "personal_email": user["personal_email"],
            "personal_phone": user["personal_phone"],
            "company_role": user["company_role"],
        },
    }


@admin_router.get("/devices")
def list_devices(_: dict[str, Any] = Depends(require_admin)) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT d.id, d.user_id, u.display_name, u.company_id, c.name AS company_name,
                   d.device_uid, d.platform, d.device_name, d.os_version, d.app_version,
                   d.app_build, d.auth_token_hint, d.status, d.bound_at, d.last_seen_at,
                   d.last_sync_at, d.last_sync_error, d.revoked_at
            FROM tb.devices d
            JOIN tb.users u ON u.id = d.user_id
            LEFT JOIN tb.companies c ON c.id = u.company_id
            ORDER BY d.bound_at DESC
            """
        )
        rows = cur.fetchall()
    return {"devices": rows}


@admin_router.post("/users/{user_id}/device-activation")
def issue_device_activation(
    user_id: UUID,
    payload: DeviceActivationCreate,
    response: Response,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    raw_key = random_activation_key()
    key_hash = hash_token(raw_key)
    key_hint = raw_key[-6:]
    expires_at = _now() + timedelta(hours=payload.expires_hours)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT u.id, u.company_id, u.display_name, u.login_name, u.status, u.deleted_at,
                   c.name AS company_name
            FROM tb.users u
            LEFT JOIN tb.companies c ON c.id = u.company_id
            WHERE u.id = %s
            FOR UPDATE OF u
            """,
            (user_id,),
        )
        target = cur.fetchone()
        if not target:
            raise HTTPException(404, "USER_NOT_FOUND")
        if target["deleted_at"] is not None or target["status"] != "ACTIVE":
            raise HTTPException(409, "USER_NOT_ACTIVE")
        cur.execute(
            """
            INSERT INTO tb.device_activation_tokens(user_id, token_hash, token_hint, created_by, expires_at)
            VALUES (%s,%s,%s,%s,%s)
            RETURNING id, created_at
            """,
            (user_id, key_hash, key_hint, admin["user_id"], expires_at),
        )
        token_row = cur.fetchone()
        audit(
            cur,
            action="DEVICE_ACTIVATION_ISSUE",
            actor_user_id=admin["user_id"],
            company_id=target["company_id"],
            entity_type="USER",
            entity_id=user_id,
            details={"expires_at": expires_at.isoformat(), "token_hint": key_hint},
        )
    public_base_url = settings.public_base_url
    if public_base_url is None:
        raise HTTPException(503, "PUBLIC_BASE_URL_NOT_CONFIGURED")
    try:
        activation_url = device_activation_app_uri(raw_key, public_base_url)
    except ValueError as exc:
        raise HTTPException(503, str(exc)) from exc
    pdf_filename = f"TB-Helfer_Aktivierung_{target['login_name']}.pdf"
    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"
    return {
        "user_id": user_id,
        "user_name": target["display_name"],
        "login_name": target["login_name"],
        "company_name": target["company_name"],
        "activation_key": raw_key,
        "activation_url": activation_url,
        "server_base_url": public_base_url,
        "activation_qr_svg_data_url": device_activation_qr_svg_data_url(raw_key, public_base_url),
        "activation_pdf_data_url": device_activation_pdf_data_url(
            user_name=target["display_name"],
            login_name=target["login_name"],
            company_name=target["company_name"],
            activation_key=raw_key,
            activation_url=activation_url,
            server_base_url=public_base_url,
            expires_at=expires_at,
        ),
        "activation_pdf_filename": pdf_filename,
        "token_hint": key_hint,
        "expires_at": expires_at,
        "created_at": token_row["created_at"],
    }


@admin_router.post("/devices/{device_id}/release", status_code=204)
def release_device(
    device_id: UUID,
    admin: dict[str, Any] = Depends(require_admin_csrf),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT d.id, d.user_id, u.company_id
            FROM tb.devices d JOIN tb.users u ON u.id = d.user_id
            WHERE d.id = %s FOR UPDATE
            """,
            (device_id,),
        )
        device = cur.fetchone()
        if not device:
            raise HTTPException(404, "DEVICE_NOT_FOUND")
        cur.execute(
            """
            UPDATE tb.devices
            SET status = 'REVOKED', revoked_at = now(), auth_token_hash = NULL,
                token_version = token_version + 1, updated_at = now()
            WHERE id = %s
            """,
            (device_id,),
        )
        audit(
            cur,
            action="DEVICE_RELEASE",
            actor_user_id=admin["user_id"],
            company_id=device["company_id"],
            entity_type="DEVICE",
            entity_id=device_id,
            details={"user_id": str(device["user_id"])},
        )
    return None


@admin_router.post("/devices/{device_id}/block", status_code=204)
def block_device(
    device_id: UUID,
    admin: dict[str, Any] = Depends(require_admin_csrf),
):
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT d.id, d.user_id, u.company_id
            FROM tb.devices d JOIN tb.users u ON u.id = d.user_id
            WHERE d.id = %s FOR UPDATE OF u
            """,
            (device_id,),
        )
        device = cur.fetchone()
        if not device:
            raise HTTPException(404, "DEVICE_NOT_FOUND")
        cur.execute(
            """
            UPDATE tb.devices
            SET status = 'BLOCKED', auth_token_hash = NULL,
                token_version = token_version + 1, updated_at = now()
            WHERE id = %s AND revoked_at IS NULL
            """,
            (device_id,),
        )
        audit(
            cur,
            action="DEVICE_BLOCK",
            actor_user_id=admin["user_id"],
            company_id=device["company_id"],
            entity_type="DEVICE",
            entity_id=device_id,
            details={"user_id": str(device["user_id"])},
        )
    return None
