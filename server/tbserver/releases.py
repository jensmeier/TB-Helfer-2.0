from __future__ import annotations

from typing import Any
from uuid import UUID
from urllib.parse import urlparse

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

from .admin import require_admin, require_admin_csrf
from .database import transaction
from .events import audit
from .release_compatibility import compatibility_status, normalize_platform


router = APIRouter(prefix="/api/v1", tags=["releases"])


class ReleaseCreate(BaseModel):
    platform: str = Field(min_length=1, max_length=20)
    version_name: str = Field(min_length=1, max_length=80)
    build_number: int | None = Field(default=None, ge=1)
    minimum_supported_build: int | None = Field(default=None, ge=1)
    mandatory: bool = False
    download_url: str | None = Field(default=None, max_length=2000)
    release_notes: str | None = Field(default=None, max_length=20_000)


class ReleaseUpdate(BaseModel):
    version_name: str | None = Field(default=None, min_length=1, max_length=80)
    build_number: int | None = Field(default=None, ge=1)
    minimum_supported_build: int | None = Field(default=None, ge=1)
    mandatory: bool | None = None
    download_url: str | None = Field(default=None, max_length=2000)
    release_notes: str | None = Field(default=None, max_length=20_000)


def _validated_url(value: str | None) -> str | None:
    if value is None:
        return None
    result = value.strip()
    if not result:
        return None
    parsed = urlparse(result)
    if parsed.scheme not in {"https", "http"} or not parsed.netloc:
        raise HTTPException(400, "INVALID_DOWNLOAD_URL")
    return result


def _validate_build_range(build_number: int | None, minimum_supported_build: int | None) -> None:
    if (
        build_number is not None
        and minimum_supported_build is not None
        and minimum_supported_build > build_number
    ):
        raise HTTPException(400, "MINIMUM_BUILD_EXCEEDS_RELEASE_BUILD")


def _public_release(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": row["id"],
        "platform": row["platform"],
        "version_name": row["version_name"],
        "build_number": row["build_number"],
        "minimum_supported_build": row["minimum_supported_build"],
        "mandatory": row["mandatory"],
        "download_url": row["download_url"],
        "release_notes": row["release_notes"],
        "status": row["status"],
        "published_at": row["published_at"],
    }


def _load_release(cur, release_id: UUID, *, for_update: bool = False) -> dict[str, Any]:
    suffix = " FOR UPDATE" if for_update else ""
    cur.execute(
        f"""
        SELECT id, platform, version_name, build_number, minimum_supported_build,
               mandatory, storage_key, byte_size, sha256, download_url,
               release_notes, status, published_at, created_at
        FROM tb.releases
        WHERE id = %s{suffix}
        """,
        (release_id,),
    )
    row = cur.fetchone()
    if not row:
        raise HTTPException(404, "RELEASE_NOT_FOUND")
    return dict(row)


def _latest_published(cur, platform: str) -> dict[str, Any] | None:
    cur.execute(
        """
        SELECT id, platform, version_name, build_number, minimum_supported_build,
               mandatory, storage_key, byte_size, sha256, download_url,
               release_notes, status, published_at, created_at
        FROM tb.releases
        WHERE platform = %s AND status = 'PUBLISHED'
        ORDER BY build_number DESC NULLS LAST, published_at DESC NULLS LAST, created_at DESC
        LIMIT 1
        """,
        (platform,),
    )
    row = cur.fetchone()
    return dict(row) if row else None


@router.get("/client-compatibility")
def client_compatibility(
    platform: str = Query(min_length=1, max_length=20),
    build_number: int | None = Query(default=None, ge=1),
    version_name: str | None = Query(default=None, max_length=80),
) -> dict:
    try:
        normalized = normalize_platform(platform)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc

    with transaction() as conn, conn.cursor() as cur:
        latest = _latest_published(cur, normalized)

    if latest is None:
        return {
            "platform": normalized,
            "current_build": build_number,
            "current_version": version_name,
            "status": "NO_PUBLISHED_RELEASE",
            "supported": True,
            "update_available": False,
            "update_required": False,
            "minimum_supported_build": None,
            "latest_build": None,
            "latest_version": None,
            "release": None,
        }

    status = compatibility_status(
        current_build=build_number,
        latest_build=latest["build_number"],
        minimum_supported_build=latest["minimum_supported_build"],
        latest_mandatory=bool(latest["mandatory"]),
    )
    supported = status != "UNSUPPORTED_BUILD"
    update_available = status in {"SUPPORTED_UPDATE_AVAILABLE", "UPDATE_REQUIRED", "UNSUPPORTED_BUILD"}
    update_required = status in {"UPDATE_REQUIRED", "UNSUPPORTED_BUILD"}
    return {
        "platform": normalized,
        "current_build": build_number,
        "current_version": version_name,
        "status": status,
        "supported": supported,
        "update_available": update_available,
        "update_required": update_required,
        "minimum_supported_build": latest["minimum_supported_build"],
        "latest_build": latest["build_number"],
        "latest_version": latest["version_name"],
        "release": _public_release(latest),
    }


@router.get("/releases/{platform}/latest")
def latest_release(platform: str) -> dict:
    try:
        normalized = normalize_platform(platform)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    with transaction() as conn, conn.cursor() as cur:
        latest = _latest_published(cur, normalized)
    if latest is None:
        raise HTTPException(404, "RELEASE_NOT_FOUND")
    return {"release": _public_release(latest)}


@router.get("/admin/releases")
def admin_list_releases(
    platform: str | None = Query(default=None, max_length=20),
    _: dict[str, Any] = Depends(require_admin),
) -> dict:
    normalized = None
    if platform is not None:
        try:
            normalized = normalize_platform(platform)
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc
    with transaction() as conn, conn.cursor() as cur:
        if normalized is None:
            cur.execute(
                """
                SELECT id, platform, version_name, build_number, minimum_supported_build,
                       mandatory, storage_key, byte_size, sha256, download_url,
                       release_notes, status, published_at, created_at
                FROM tb.releases
                ORDER BY platform, build_number DESC NULLS LAST, created_at DESC
                """
            )
        else:
            cur.execute(
                """
                SELECT id, platform, version_name, build_number, minimum_supported_build,
                       mandatory, storage_key, byte_size, sha256, download_url,
                       release_notes, status, published_at, created_at
                FROM tb.releases
                WHERE platform = %s
                ORDER BY build_number DESC NULLS LAST, created_at DESC
                """,
                (normalized,),
            )
        rows = [dict(row) for row in cur.fetchall()]
    return {"releases": rows}


@router.post("/admin/releases", status_code=201)
def admin_create_release(
    payload: ReleaseCreate,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    try:
        platform = normalize_platform(payload.platform)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    version_name = payload.version_name.strip()
    download_url = _validated_url(payload.download_url)
    _validate_build_range(payload.build_number, payload.minimum_supported_build)
    with transaction() as conn, conn.cursor() as cur:
        try:
            cur.execute(
                """
                INSERT INTO tb.releases(
                    platform, version_name, build_number, minimum_supported_build,
                    mandatory, download_url, release_notes, status
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,'DRAFT')
                RETURNING *
                """,
                (
                    platform,
                    version_name,
                    payload.build_number,
                    payload.minimum_supported_build,
                    payload.mandatory,
                    download_url,
                    payload.release_notes,
                ),
            )
        except Exception as exc:
            if getattr(exc, "sqlstate", None) == "23505":
                raise HTTPException(409, "RELEASE_VERSION_EXISTS") from exc
            raise
        row = dict(cur.fetchone())
        audit(cur, action="RELEASE_CREATE", actor_user_id=user["user_id"], entity_type="RELEASE", entity_id=row["id"], details={"platform": row["platform"], "version_name": row["version_name"], "build_number": row["build_number"]})
    return row


@router.patch("/admin/releases/{release_id}")
def admin_update_release(
    release_id: UUID,
    payload: ReleaseUpdate,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    updates = payload.model_dump(exclude_unset=True)
    if not updates:
        raise HTTPException(400, "NO_CHANGES")
    with transaction() as conn, conn.cursor() as cur:
        current = _load_release(cur, release_id, for_update=True)
        if current["status"] != "DRAFT":
            raise HTTPException(409, "PUBLISHED_RELEASE_IMMUTABLE")
        candidate_build = updates.get("build_number", current["build_number"])
        candidate_min = updates.get("minimum_supported_build", current["minimum_supported_build"])
        _validate_build_range(candidate_build, candidate_min)
        if "download_url" in updates:
            updates["download_url"] = _validated_url(updates["download_url"])
        if "version_name" in updates:
            if updates["version_name"] is None:
                raise HTTPException(400, "INVALID_VERSION_NAME")
            updates["version_name"] = updates["version_name"].strip()
        columns, values = [], []
        for key in (
            "version_name", "build_number", "minimum_supported_build",
            "mandatory", "download_url", "release_notes",
        ):
            if key in updates:
                columns.append(f"{key} = %s")
                values.append(updates[key])
        values.append(release_id)
        try:
            cur.execute(
                f"UPDATE tb.releases SET {', '.join(columns)} WHERE id = %s RETURNING *",
                tuple(values),
            )
        except Exception as exc:
            if getattr(exc, "sqlstate", None) == "23505":
                raise HTTPException(409, "RELEASE_VERSION_EXISTS") from exc
            raise
        row = dict(cur.fetchone())
        audit(cur, action="RELEASE_UPDATE", actor_user_id=user["user_id"], entity_type="RELEASE", entity_id=row["id"], details={"platform": row["platform"], "version_name": row["version_name"], "build_number": row["build_number"]})
    return row


@router.post("/admin/releases/{release_id}/publish")
def admin_publish_release(
    release_id: UUID,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        current = _load_release(cur, release_id, for_update=True)
        if current["status"] == "RETIRED":
            raise HTTPException(409, "RETIRED_RELEASE_CANNOT_BE_PUBLISHED")
        if current["status"] == "PUBLISHED":
            return current
        _validate_build_range(current["build_number"], current["minimum_supported_build"])
        if current["platform"] == "ANDROID" and current["build_number"] is None:
            raise HTTPException(409, "ANDROID_BUILD_NUMBER_REQUIRED")
        cur.execute(
            """
            UPDATE tb.releases
            SET status = 'PUBLISHED', published_at = now()
            WHERE id = %s
            RETURNING *
            """,
            (release_id,),
        )
        row = dict(cur.fetchone())
        audit(cur, action="RELEASE_PUBLISH", actor_user_id=user["user_id"], entity_type="RELEASE", entity_id=row["id"], details={"platform": row["platform"], "version_name": row["version_name"], "build_number": row["build_number"], "mandatory": row["mandatory"], "minimum_supported_build": row["minimum_supported_build"]})
    return row


@router.post("/admin/releases/{release_id}/retire")
def admin_retire_release(
    release_id: UUID,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    with transaction() as conn, conn.cursor() as cur:
        _load_release(cur, release_id, for_update=True)
        cur.execute(
            "UPDATE tb.releases SET status = 'RETIRED' WHERE id = %s RETURNING *",
            (release_id,),
        )
        row = dict(cur.fetchone())
        audit(cur, action="RELEASE_RETIRE", actor_user_id=user["user_id"], entity_type="RELEASE", entity_id=row["id"], details={"platform": row["platform"], "version_name": row["version_name"], "build_number": row["build_number"]})
    return row
