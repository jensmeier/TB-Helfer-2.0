from __future__ import annotations

from typing import Any
from uuid import UUID

from fastapi import HTTPException


ADMIN_PERMISSIONS = {"*": True}
CHEF_DEFAULT_PERMISSIONS = {
    "worksite.view": True,
    "btb.view": True,
    "files.view": True,
    "acceptance.view": True,
    "defect.view": True,
    "export.create": True,
}


def _merge_permissions(base: dict, override: dict) -> dict[str, bool]:
    merged = {str(k): bool(v) for k, v in (base or {}).items()}
    for key, value in (override or {}).items():
        merged[str(key)] = bool(value)
    return merged


def worksite_context(cur, user: dict[str, Any], worksite_id: UUID | str) -> dict[str, Any]:
    cur.execute(
        """
        SELECT w.id, w.company_id, w.name, w.status, w.deleted_at,
               m.id AS membership_id, m.worksite_role, m.permission_profile,
               m.permissions_override, m.permissions_version, m.area_scope,
               m.status AS membership_status, p.permissions AS profile_permissions
        FROM tb.worksites w
        LEFT JOIN tb.worksite_memberships m
          ON m.worksite_id = w.id AND m.user_id = %s
        LEFT JOIN tb.permission_profiles p ON p.profile_key = m.permission_profile
        WHERE w.id = %s
        """,
        (user["user_id"], worksite_id),
    )
    row = cur.fetchone()
    if not row or row["deleted_at"] is not None:
        raise HTTPException(404, "WORKSITE_NOT_FOUND")

    role = user["company_role"]
    if role == "ADMIN":
        permissions = ADMIN_PERMISSIONS.copy()
        access = True
    elif role == "CHEF" and user["company_id"] == row["company_id"]:
        if row["membership_id"] and row["membership_status"] == "ACTIVE":
            permissions = _merge_permissions(row["profile_permissions"], row["permissions_override"])
        else:
            permissions = CHEF_DEFAULT_PERMISSIONS.copy()
        access = True
    else:
        access = bool(row["membership_id"] and row["membership_status"] == "ACTIVE")
        permissions = (
            _merge_permissions(row["profile_permissions"], row["permissions_override"])
            if access
            else {}
        )

    if not access:
        raise HTTPException(403, "WORKSITE_ACCESS_DENIED")
    result = dict(row)
    result["effective_permissions"] = permissions
    return result


def require_permission(context: dict[str, Any], permission: str) -> None:
    permissions = context.get("effective_permissions") or {}
    if permissions.get("*") is True:
        return
    if permissions.get(permission) is not True:
        raise HTTPException(403, f"PERMISSION_REQUIRED:{permission}")
