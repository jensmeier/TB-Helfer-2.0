from __future__ import annotations

import math
from typing import Any
from uuid import UUID

from fastapi import HTTPException


ADMIN_PERMISSIONS = {
    "worksite.view": True,
    "btb.view": True,
    "files.view": True,
    "acceptance.view": True,
    "defect.view": True,
}
CHEF_DEFAULT_PERMISSIONS = {
    "worksite.view": True,
    "btb.view": True,
    "files.view": True,
    "acceptance.view": True,
    "defect.view": True,
    "export.create": True,
}


def _merge_permissions(base: dict, override: dict) -> dict[str, bool]:
    merged = {str(k): v is True for k, v in (base or {}).items()}
    for key, value in (override or {}).items():
        merged[str(key)] = value is True
    return merged


def _scope_labels(scope: dict[str, Any]) -> set[str]:
    raw = scope.get("labels")
    values: list[str] = []
    if isinstance(raw, list):
        values.extend(str(value) for value in raw)
    label = scope.get("label")
    if isinstance(label, str):
        values.extend(label.replace(";", ",").replace("|", ",").split(","))
    return {value.strip().casefold() for value in values if value.strip()}


def _scope_number(scope: dict[str, Any], key: str) -> float | None:
    value = scope.get(key)
    if value is None:
        return None
    try:
        number = float(value)
        return number if math.isfinite(number) and not isinstance(value, bool) else None
    except (TypeError, ValueError, OverflowError):
        return None


def normalized_area_scope(scope: Any, *, empty_means_all: bool = False) -> dict[str, Any]:
    """
    Normalisiert den stabilen Server-1.0-Bereichsvertrag.

    Ein leerer Helper-/READ_ONLY-Scope bedeutet absichtlich NICHT "alles". Nur
    explizites {"all": true} oder der vom Aufrufer bewusst gesetzte
    empty_means_all-Fall darf die gesamte Baustelle freigeben.
    """
    source = scope if isinstance(scope, dict) else {}
    if source.get("invalid") is True or any(
        source.get(key) is not None and _scope_number(source, key) is None
        for key in ("km_from_m", "km_to_m")
    ):
        return {"all": False, "invalid": True}
    if source.get("all") is True:
        return {"all": True}

    result: dict[str, Any] = {"all": False}
    labels = sorted(_scope_labels(source))
    if labels:
        result["labels"] = labels

    km_from = _scope_number(source, "km_from_m")
    km_to = _scope_number(source, "km_to_m")
    if km_from is not None or km_to is not None:
        low = km_from if km_from is not None else km_to
        high = km_to if km_to is not None else km_from
        assert low is not None and high is not None
        result["km_from_m"] = min(low, high)
        result["km_to_m"] = max(low, high)

    if len(result) == 1 and empty_means_all:
        return {"all": True}
    return result


def area_scope_allows(user_scope: Any, entity_scope: Any) -> bool:
    """
    True nur dann, wenn der Entity-Scope vollstaendig innerhalb der
    Benutzerfreigabe liegt. Fehlende Dimensionen werden bei eingeschraenkten
    Benutzern nicht als Wildcard interpretiert.
    """
    user = normalized_area_scope(user_scope)
    entity = normalized_area_scope(entity_scope, empty_means_all=True)
    if user.get("invalid") or entity.get("invalid"):
        return False
    if user.get("all") is True:
        return True
    if entity.get("all") is True:
        return False

    user_labels = set(user.get("labels") or [])
    entity_labels = set(entity.get("labels") or [])
    user_from = _scope_number(user, "km_from_m")
    user_to = _scope_number(user, "km_to_m")
    entity_from = _scope_number(entity, "km_from_m")
    entity_to = _scope_number(entity, "km_to_m")

    has_user_constraint = bool(user_labels) or user_from is not None or user_to is not None
    if not has_user_constraint:
        return False

    if user_labels:
        if not entity_labels or not entity_labels.issubset(user_labels):
            return False

    if user_from is not None or user_to is not None:
        if entity_from is None and entity_to is None:
            return False
        low = entity_from if entity_from is not None else entity_to
        high = entity_to if entity_to is not None else entity_from
        assert low is not None and high is not None
        entity_low = min(low, high)
        entity_high = max(low, high)
        if user_from is not None and entity_low < user_from:
            return False
        if user_to is not None and entity_high > user_to:
            return False

    return True


def require_area_scope(context: dict[str, Any], entity_scope: Any) -> None:
    if not area_scope_allows(context.get("effective_area_scope"), entity_scope):
        raise HTTPException(403, "AREA_SCOPE_FORBIDDEN")


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
    if role != "ADMIN" and (
        user.get("company_id") is None
        or str(user["company_id"]) != str(row["company_id"])
    ):
        raise HTTPException(403, "WORKSITE_ACCESS_DENIED")
    if role == "ADMIN":
        permissions = ADMIN_PERMISSIONS.copy()
        access = True
        area_scope = {"all": True}
    elif role == "CHEF" and user["company_id"] == row["company_id"]:
        if row["membership_id"] and row["membership_status"] == "ACTIVE":
            permissions = _merge_permissions(row["profile_permissions"], row["permissions_override"])
        else:
            permissions = CHEF_DEFAULT_PERMISSIONS.copy()
        access = True
        area_scope = {"all": True}
    else:
        access = bool(row["membership_id"] and row["membership_status"] == "ACTIVE")
        permissions = (
            _merge_permissions(row["profile_permissions"], row["permissions_override"])
            if access
            else {}
        )
        empty_means_all = row["worksite_role"] in {"RESPONSIBLE_TB", "TB"}
        area_scope = normalized_area_scope(
            row["area_scope"],
            empty_means_all=empty_means_all,
        )

    if not access:
        raise HTTPException(403, "WORKSITE_ACCESS_DENIED")
    result = dict(row)
    result["effective_permissions"] = permissions
    result["effective_area_scope"] = area_scope
    return result


def has_permission(context: dict[str, Any], permission: str) -> bool:
    permissions = context.get("effective_permissions") or {}
    return permissions.get("*") is True or permissions.get(permission) is True


def require_permission(context: dict[str, Any], permission: str) -> None:
    if not has_permission(context, permission):
        raise HTTPException(403, f"PERMISSION_REQUIRED:{permission}")


def team_lead_user_id(cur, team_id: UUID | str) -> UUID | None:
    cur.execute(
        "SELECT lead_user_id FROM tb.teams WHERE id = %s AND status = 'ACTIVE'",
        (team_id,),
    )
    row = cur.fetchone()
    return row["lead_user_id"] if row else None


def is_team_lead(cur, user: dict[str, Any], team_id: UUID | str) -> bool:
    lead_user_id = team_lead_user_id(cur, team_id)
    return lead_user_id is not None and str(lead_user_id) == str(user["user_id"])


def require_team_lead(cur, user: dict[str, Any], team_id: UUID | str) -> None:
    if not is_team_lead(cur, user, team_id):
        raise HTTPException(403, "BTB_TEAM_LEAD_REQUIRED")
