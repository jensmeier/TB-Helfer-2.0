from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4
from urllib.parse import urlparse

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from .admin import require_admin, require_admin_csrf
from .config import settings
from .database import transaction
from .events import audit
from .release_compatibility import compatibility_status, normalize_platform


router = APIRouter(prefix="/api/v1", tags=["releases"])

_RELEASE_MAX_BYTES = 512 * 1024 * 1024
_RELEASE_STREAM_CHUNK = 1024 * 1024


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
    download_url = (
        f"/api/v1/releases/{row['id']}/download"
        if row.get("storage_key")
        else row.get("download_url")
    )
    return {
        "id": row["id"],
        "platform": row["platform"],
        "version_name": row["version_name"],
        "build_number": row["build_number"],
        "minimum_supported_build": row["minimum_supported_build"],
        "mandatory": row["mandatory"],
        "download_url": download_url,
        "release_notes": row["release_notes"],
        "status": row["status"],
        "published_at": row["published_at"],
    }


def _admin_release(row: dict[str, Any]) -> dict[str, Any]:
    return {
        **_public_release(row),
        "external_download_url": row.get("download_url"),
        "binary": {
            "configured": bool(row.get("storage_key")),
            "byte_size": row.get("byte_size"),
            "sha256": row.get("sha256"),
        },
        "created_at": row.get("created_at"),
    }


def _release_storage_key(row: dict[str, Any]) -> str:
    return f"releases/{row['platform'].lower()}/{row['id']}/package.bin"


def _absolute_storage_path(storage_key: str) -> Path:
    root = settings.storage_root.resolve()
    path = (root / storage_key).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise HTTPException(500, "INVALID_STORAGE_KEY") from exc
    return path


def _release_filename(row: dict[str, Any]) -> str:
    version = "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in row["version_name"])
    build = f"-{row['build_number']}" if row.get("build_number") is not None else ""
    extension = {"ANDROID": ".apk", "WINDOWS": ".bin", "IOS": ".bin"}.get(row["platform"], ".bin")
    return f"TB-Helfer-{row['platform'].lower()}-{version}{build}{extension}"


def _install_binary(temp_path: Path, final_path: Path, backup_path: Path) -> bool:
    backup_path.unlink(missing_ok=True)
    had_previous = final_path.exists()
    if had_previous:
        os.replace(final_path, backup_path)
    try:
        os.replace(temp_path, final_path)
    except Exception:
        if had_previous and backup_path.exists():
            os.replace(backup_path, final_path)
        raise
    return had_previous


def _rollback_binary(final_path: Path, backup_path: Path, had_previous: bool) -> None:
    final_path.unlink(missing_ok=True)
    if had_previous and backup_path.exists():
        os.replace(backup_path, final_path)
    else:
        backup_path.unlink(missing_ok=True)


def _finish_binary_files(*paths: Path | None) -> None:
    for path in paths:
        if path is not None:
            path.unlink(missing_ok=True)


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



@router.get("/releases/{release_id}/download")
def download_release_binary(release_id: UUID):
    with transaction() as conn, conn.cursor() as cur:
        row = _load_release(cur, release_id)
    if row["status"] != "PUBLISHED":
        raise HTTPException(404, "RELEASE_NOT_FOUND")
    if not row.get("storage_key"):
        raise HTTPException(404, "RELEASE_BINARY_NOT_CONFIGURED")
    path = _absolute_storage_path(row["storage_key"])
    if not path.is_file():
        raise HTTPException(404, "RELEASE_BINARY_FILE_MISSING")
    return FileResponse(
        path,
        media_type="application/octet-stream",
        filename=_release_filename(row),
        headers={"ETag": f'"{row.get("sha256") or ""}"'},
    )


@router.put("/admin/releases/{release_id}/binary")
async def admin_upload_release_binary(
    release_id: UUID,
    request: Request,
    user: dict[str, Any] = Depends(require_admin_csrf),
) -> dict:
    expected_sha = (request.headers.get("X-Release-SHA256") or "").strip().lower() or None
    if expected_sha is not None:
        if len(expected_sha) != 64 or any(ch not in "0123456789abcdef" for ch in expected_sha):
            raise HTTPException(400, "INVALID_RELEASE_SHA256")
    content_length = request.headers.get("content-length")
    if content_length:
        try:
            declared_bytes = int(content_length)
        except ValueError as exc:
            raise HTTPException(400, "INVALID_CONTENT_LENGTH") from exc
        if declared_bytes <= 0:
            raise HTTPException(400, "RELEASE_BINARY_EMPTY")
        if declared_bytes > _RELEASE_MAX_BYTES:
            raise HTTPException(413, "RELEASE_BINARY_TOO_LARGE")

    with transaction() as conn, conn.cursor() as cur:
        initial = _load_release(cur, release_id)
        if initial["status"] != "DRAFT":
            raise HTTPException(409, "PUBLISHED_RELEASE_IMMUTABLE")

    storage_key = _release_storage_key(initial)
    final_path = _absolute_storage_path(storage_key)
    final_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = final_path.with_name(f".{final_path.name}.{uuid4().hex}.tmp")
    backup_path = final_path.with_name(f".{final_path.name}.{uuid4().hex}.rollback")
    digest = hashlib.sha256()
    total = 0
    try:
        with temp_path.open("wb") as out:
            async for chunk in request.stream():
                if not chunk:
                    continue
                total += len(chunk)
                if total > _RELEASE_MAX_BYTES:
                    raise HTTPException(413, "RELEASE_BINARY_TOO_LARGE")
                digest.update(chunk)
                out.write(chunk)
            out.flush()
            os.fsync(out.fileno())
        if total == 0:
            raise HTTPException(400, "RELEASE_BINARY_EMPTY")
        actual_sha = digest.hexdigest()
        if expected_sha is not None and actual_sha != expected_sha:
            raise HTTPException(
                409,
                {"code": "RELEASE_SHA256_MISMATCH", "actual": actual_sha},
            )

        installed = False
        had_previous = False
        try:
            with transaction() as conn, conn.cursor() as cur:
                current = _load_release(cur, release_id, for_update=True)
                if current["status"] != "DRAFT":
                    raise HTTPException(409, "PUBLISHED_RELEASE_IMMUTABLE")
                if current["platform"] != initial["platform"]:
                    raise HTTPException(409, "RELEASE_CHANGED_DURING_UPLOAD")
                had_previous = _install_binary(temp_path, final_path, backup_path)
                installed = True
                cur.execute(
                    """
                    UPDATE tb.releases
                    SET storage_key = %s, byte_size = %s, sha256 = %s, download_url = NULL
                    WHERE id = %s
                    RETURNING *
                    """,
                    (storage_key, total, actual_sha, release_id),
                )
                row = dict(cur.fetchone())
                audit(
                    cur,
                    action="RELEASE_BINARY_UPLOAD",
                    actor_user_id=user["user_id"],
                    entity_type="RELEASE",
                    entity_id=release_id,
                    details={
                        "platform": row["platform"],
                        "version_name": row["version_name"],
                        "build_number": row["build_number"],
                        "byte_size": total,
                        "sha256": actual_sha,
                    },
                )
            _finish_binary_files(temp_path, backup_path)
        except Exception:
            if installed:
                _rollback_binary(final_path, backup_path, had_previous)
            _finish_binary_files(temp_path, backup_path)
            raise
    except Exception:
        _finish_binary_files(temp_path)
        raise

    return {
        "release": {
            **_public_release(row),
            "binary": {
                "configured": True,
                "byte_size": total,
                "sha256": actual_sha,
            },
        }
    }


@router.delete("/admin/releases/{release_id}/binary", status_code=204)
def admin_delete_release_binary(
    release_id: UUID,
    user: dict[str, Any] = Depends(require_admin_csrf),
):
    storage_key = None
    with transaction() as conn, conn.cursor() as cur:
        current = _load_release(cur, release_id, for_update=True)
        if current["status"] != "DRAFT":
            raise HTTPException(409, "PUBLISHED_RELEASE_IMMUTABLE")
        storage_key = current.get("storage_key")
        if storage_key:
            cur.execute(
                """
                UPDATE tb.releases
                SET storage_key = NULL, byte_size = NULL, sha256 = NULL
                WHERE id = %s
                """,
                (release_id,),
            )
            audit(
                cur,
                action="RELEASE_BINARY_DELETE",
                actor_user_id=user["user_id"],
                entity_type="RELEASE",
                entity_id=release_id,
                details={
                    "platform": current["platform"],
                    "version_name": current["version_name"],
                    "build_number": current["build_number"],
                },
            )
    if storage_key:
        _absolute_storage_path(storage_key).unlink(missing_ok=True)
    return None


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
    return {"releases": [_admin_release(row) for row in rows]}


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
    return _admin_release(row)


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
            if updates["download_url"] is not None and current.get("storage_key"):
                raise HTTPException(409, "RELEASE_BINARY_ALREADY_CONFIGURED")
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
    return _admin_release(row)


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
            return _admin_release(current)
        _validate_build_range(current["build_number"], current["minimum_supported_build"])
        if current["platform"] == "ANDROID" and current["build_number"] is None:
            raise HTTPException(409, "ANDROID_BUILD_NUMBER_REQUIRED")
        if not current.get("storage_key") and not current.get("download_url"):
            raise HTTPException(409, "RELEASE_DOWNLOAD_REQUIRED")
        if current.get("storage_key"):
            path = _absolute_storage_path(current["storage_key"])
            if not path.is_file():
                raise HTTPException(409, "RELEASE_BINARY_FILE_MISSING")
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
    return _admin_release(row)


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
    return _admin_release(row)
