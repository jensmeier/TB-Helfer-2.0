from __future__ import annotations

from typing import Any

from fastapi import HTTPException, Request

from .devices import current_device_user
from .portal import CSRF_COOKIE, current_portal_user
from .security import hash_token


def _has_authorization(request: Request) -> bool:
    return bool((request.headers.get("Authorization") or "").strip())


def current_api_user(request: Request) -> dict[str, Any]:
    if _has_authorization(request):
        return current_device_user(request)
    user = current_portal_user(request)
    result = dict(user)
    result["auth_kind"] = "PORTAL"
    return result


def require_api_write(request: Request) -> dict[str, Any]:
    if _has_authorization(request):
        return current_device_user(request)
    user = current_portal_user(request)
    cookie = request.cookies.get(CSRF_COOKIE)
    header = request.headers.get("X-CSRF-Token")
    if not cookie or not header or cookie != header:
        raise HTTPException(403, "CSRF_INVALID")
    if hash_token(header) != user["csrf_secret_hash"]:
        raise HTTPException(403, "CSRF_INVALID")
    result = dict(user)
    result["auth_kind"] = "PORTAL"
    return result
