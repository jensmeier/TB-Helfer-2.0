from __future__ import annotations

from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field

from .admin import require_admin, require_admin_csrf
from .btb_documents import remove_storage_keys
from .config import settings
from .database import transaction
from .events import audit


router = APIRouter(prefix="/api/v1/admin/purge", tags=["admin-purge"])

_CONFIRM_TEXT = "ENDGUELTIG LOESCHEN"
_PRODUCTION_ENVIRONMENTS = {"prod", "production"}
_SURVEY_TEST_RESET_ENVIRONMENTS = {"development", "dev", "test", "testing", "staging"}
_SURVEY_TEST_RESET_TEXT = "VERMESSUNG TESTDATEN LOESCHEN"


class PurgeConfirmation(BaseModel):
    confirm_id: UUID
    confirm_text: str = Field(min_length=1, max_length=80)


class SurveyTestResetConfirmation(BaseModel):
    confirm_text: str = Field(min_length=1, max_length=100)


def _confirm(payload: PurgeConfirmation, entity_id: UUID) -> None:
    if payload.confirm_id != entity_id or payload.confirm_text.strip().upper() != _CONFIRM_TEXT:
        raise HTTPException(400, "PURGE_CONFIRMATION_REQUIRED")


def _storage_keys(cur, query: str, params: tuple[Any, ...]) -> list[str]:
    cur.execute(query, params)
    return [str(row["storage_key"]) for row in cur.fetchall() if row.get("storage_key")]


def _remove_keys_after_commit(storage_keys: list[str]) -> None:
    if storage_keys:
        remove_storage_keys(storage_keys)


def _require_survey_test_reset_environment() -> None:
    if settings.environment not in _SURVEY_TEST_RESET_ENVIRONMENTS:
        raise HTTPException(
            409,
            {
                "code": "SURVEY_TEST_RESET_ENVIRONMENT_FORBIDDEN",
                "environment": settings.environment,
            },
        )


def _survey_test_reset_counts(cur) -> dict[str, int]:
    queries = {
        "survey_originals": "SELECT count(*)::int AS count FROM tb.survey_originals",
        "survey_parse_catalog": "SELECT count(*)::int AS count FROM tb.survey_parse_catalog",
        "catalog_assets": "SELECT count(*)::int AS count FROM tb.survey_catalog_assets",
        "catalog_axes": "SELECT count(*)::int AS count FROM tb.survey_catalog_axes",
        "catalog_segments": "SELECT count(*)::int AS count FROM tb.survey_catalog_segments",
        "catalog_conflicts": "SELECT count(*)::int AS count FROM tb.survey_catalog_conflicts",
        "catalog_history": "SELECT count(*)::int AS count FROM tb.survey_catalog_history",
        "survey_sections": (
            "SELECT count(*)::int AS count FROM tb.track_data "
            "WHERE dataset_kind = 'SURVEY_SECTION'"
        ),
        "track_source_links": "SELECT count(*)::int AS count FROM tb.track_data_sources",
        "import_file_rows": (
            "SELECT count(*)::int AS count FROM tb.files f "
            "WHERE f.source = 'IMPORT' "
            "AND EXISTS (SELECT 1 FROM tb.survey_originals o WHERE o.storage_key = f.storage_key)"
        ),
    }
    result: dict[str, int] = {}
    for key, query in queries.items():
        cur.execute(query)
        result[key] = int(cur.fetchone()["count"])
    return result


@router.get("/survey-test-data")
def survey_test_data_preview(
    _: dict[str, Any] = Depends(require_admin),
) -> dict[str, Any]:
    _require_survey_test_reset_environment()
    with transaction() as conn, conn.cursor() as cur:
        _assert_no_network_source_dependencies(cur)
        counts = _survey_test_reset_counts(cur)
    return {
        "environment": settings.environment,
        "allowed": True,
        "confirm_text": _SURVEY_TEST_RESET_TEXT,
        "counts": counts,
    }


def survey_test_data_preview_from_terminal() -> dict[str, Any]:
    """Read-only survey reset preview for the API-container terminal."""
    _require_survey_test_reset_environment()
    with transaction() as conn, conn.cursor() as cur:
        _assert_no_network_source_dependencies(cur)
        counts = _survey_test_reset_counts(cur)
    return {
        "environment": settings.environment,
        "allowed": True,
        "confirm_text": _SURVEY_TEST_RESET_TEXT,
        "counts": counts,
    }


def _assert_no_network_source_dependencies(cur) -> None:
    # The general test reset must not erase originals used by an active working network.
    cur.execute("""SELECT 1 FROM tb.survey_network_entities o
        WHERE o.entity_type='OBJECT' AND o.active AND (
          EXISTS (SELECT 1 FROM tb.survey_network_entities n WHERE n.entity_type='NODE'
            AND EXISTS (SELECT 1 FROM jsonb_array_elements(o.data->'endpoints') p WHERE p->>'node_id'=n.id::text)
            AND n.data->'binding'->>'segment_id' IS NOT NULL)
          OR o.data->'km_reference'->>'segment_id' IS NOT NULL
          OR jsonb_array_length(coalesce(o.data->'path_sections','[]'::jsonb))>0) LIMIT 1""")
    if cur.fetchone():
        raise HTTPException(409, 'SURVEY_TEST_RESET_NETWORK_REFERENCES_ACTIVE')


def _reset_survey_test_data(
    *,
    confirm_text: str,
    actor_user_id: UUID | str | None,
    source: str,
) -> dict[str, Any]:
    _require_survey_test_reset_environment()
    if confirm_text.strip().upper() != _SURVEY_TEST_RESET_TEXT:
        raise HTTPException(400, "SURVEY_TEST_RESET_CONFIRMATION_REQUIRED")

    storage_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("SELECT pg_advisory_xact_lock(751502015)")
        _assert_no_network_source_dependencies(cur)
        before = _survey_test_reset_counts(cur)
        storage_keys.extend(
            _storage_keys(cur, "SELECT storage_key FROM tb.survey_originals", ())
        )

        audit(
            cur,
            action="SURVEY_TEST_DATA_RESET",
            actor_user_id=actor_user_id,
            entity_type="SYSTEM",
            details={
                "environment": settings.environment,
                "before": before,
                "source": source,
            },
        )

        # The catalog is derived from survey originals; remove it in FK-safe order.
        cur.execute("DELETE FROM tb.survey_catalog_conflicts")
        cur.execute("DELETE FROM tb.survey_catalog_segments")
        cur.execute("DELETE FROM tb.survey_catalog_assets")
        cur.execute("DELETE FROM tb.survey_catalog_axes")
        cur.execute("DELETE FROM tb.survey_catalog_history")

        # Dedicated survey sections are derived survey data and are reset as a unit.
        # Other track_data rows are preserved; their optional direct original link
        # is detached before the originals disappear.
        cur.execute("DELETE FROM tb.track_data WHERE dataset_kind = 'SURVEY_SECTION'")
        cur.execute("DELETE FROM tb.track_data_sources")
        cur.execute(
            "UPDATE tb.track_data SET source_original_id = NULL "
            "WHERE source_original_id IS NOT NULL"
        )
        cur.execute(
            "UPDATE tb.track_data_km_points SET source_original_id = NULL "
            "WHERE source_original_id IS NOT NULL"
        )

        # Mobile imports can have a tb.files row pointing to the same moved raw file.
        # Remove only those import rows whose storage key belongs to a survey original.
        cur.execute(
            """
            DELETE FROM tb.files f
            WHERE f.source = 'IMPORT'
              AND EXISTS (
                    SELECT 1 FROM tb.survey_originals o
                    WHERE o.storage_key = f.storage_key
              )
            """
        )

        cur.execute("DELETE FROM tb.survey_parse_catalog")
        cur.execute("DELETE FROM tb.survey_originals")
        cur.execute(
            "DELETE FROM tb.mutation_receipts "
            "WHERE entity_type = 'SURVEY_CATALOG_SEGMENT'"
        )

        after = _survey_test_reset_counts(cur)

    _remove_keys_after_commit(sorted(set(storage_keys)))
    return {
        "environment": settings.environment,
        "status": "RESET",
        "removed_storage_keys": len(set(storage_keys)),
        "before": before,
        "after": after,
    }


def reset_survey_test_data_from_terminal(confirm_text: str) -> dict[str, Any]:
    """Explicit staging/dev/test reset for an interactive API-container terminal."""
    return _reset_survey_test_data(
        confirm_text=confirm_text,
        actor_user_id=None,
        source="API_CONTAINER_TERMINAL",
    )


@router.delete("/survey-test-data")
def purge_survey_test_data(
    payload: SurveyTestResetConfirmation,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> dict[str, Any]:
    return _reset_survey_test_data(
        confirm_text=payload.confirm_text,
        actor_user_id=admin["user_id"],
        source="ADMIN_API",
    )


@router.delete("/releases/{release_id}", status_code=204)
def purge_release_draft(
    release_id: UUID,
    payload: PurgeConfirmation,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    _confirm(payload, release_id)
    storage_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, platform, version_name, build_number, storage_key, status
            FROM tb.releases
            WHERE id = %s
            FOR UPDATE
            """,
            (release_id,),
        )
        release = cur.fetchone()
        if not release:
            raise HTTPException(404, "RELEASE_NOT_FOUND")
        if release["status"] != "DRAFT":
            raise HTTPException(409, "ONLY_DRAFT_RELEASE_CAN_BE_PURGED")
        if release.get("storage_key"):
            storage_keys.append(str(release["storage_key"]))
        audit(
            cur,
            action="RELEASE_DRAFT_PURGE",
            actor_user_id=admin["user_id"],
            entity_type="RELEASE",
            entity_id=release_id,
            details={
                "platform": release["platform"],
                "version_name": release["version_name"],
                "build_number": release["build_number"],
            },
        )
        cur.execute("DELETE FROM tb.releases WHERE id = %s", (release_id,))
    _remove_keys_after_commit(storage_keys)
    return Response(status_code=204)


@router.delete("/devices/{device_id}", status_code=204)
def purge_device(
    device_id: UUID,
    payload: PurgeConfirmation,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    _confirm(payload, device_id)
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT d.id, d.user_id, d.device_uid, d.device_name, d.status,
                   u.company_id, u.display_name
            FROM tb.devices d
            JOIN tb.users u ON u.id = d.user_id
            WHERE d.id = %s
            FOR UPDATE OF d
            """,
            (device_id,),
        )
        device = cur.fetchone()
        if not device:
            raise HTTPException(404, "DEVICE_NOT_FOUND")
        audit(
            cur,
            action="DEVICE_PURGE",
            actor_user_id=admin["user_id"],
            company_id=device["company_id"],
            entity_type="DEVICE",
            entity_id=device_id,
            details={
                "user_id": str(device["user_id"]),
                "device_uid_hint": str(device["device_uid"])[-8:],
                "previous_status": device["status"],
            },
        )
        cur.execute("DELETE FROM tb.devices WHERE id = %s", (device_id,))
    return Response(status_code=204)


@router.delete("/worksites/{worksite_id}", status_code=204)
def purge_worksite(
    worksite_id: UUID,
    payload: PurgeConfirmation,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    _confirm(payload, worksite_id)
    storage_keys: list[str] = []
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, company_id, name, status
            FROM tb.worksites
            WHERE id = %s
            FOR UPDATE
            """,
            (worksite_id,),
        )
        worksite = cur.fetchone()
        if not worksite:
            raise HTTPException(404, "WORKSITE_NOT_FOUND")

        storage_keys.extend(
            _storage_keys(
                cur,
                """SELECT f.storage_key FROM tb.files f
                   WHERE f.worksite_id = %s
                     AND NOT EXISTS (
                         SELECT 1 FROM tb.survey_originals o
                         WHERE o.storage_key = f.storage_key
                     )""",
                (worksite_id,),
            )
        )
        cur.execute(
            "SELECT temp_storage_key AS storage_key FROM tb.upload_sessions WHERE worksite_id = %s",
            (worksite_id,),
        )
        storage_keys.extend(
            str(row["storage_key"]) for row in cur.fetchall() if row.get("storage_key")
        )

        audit(
            cur,
            action="WORKSITE_PURGE",
            actor_user_id=admin["user_id"],
            company_id=worksite["company_id"],
            worksite_id=worksite_id,
            entity_type="WORKSITE",
            entity_id=worksite_id,
            details={"name": worksite["name"], "previous_status": worksite["status"]},
        )
        cur.execute("DELETE FROM tb.deletion_tombstones WHERE worksite_id = %s", (worksite_id,))
        cur.execute("DELETE FROM tb.worksites WHERE id = %s", (worksite_id,))
    _remove_keys_after_commit(storage_keys)
    return Response(status_code=204)


@router.delete("/users/{user_id}", status_code=204)
def purge_user(
    user_id: UUID,
    payload: PurgeConfirmation,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    _confirm(payload, user_id)
    if str(admin["user_id"]) == str(user_id):
        raise HTTPException(409, "CANNOT_PURGE_SELF")

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, company_id, display_name, login_name, company_role, status, deleted_at
            FROM tb.users
            WHERE id = %s
            FOR UPDATE
            """,
            (user_id,),
        )
        user = cur.fetchone()
        if not user:
            raise HTTPException(404, "USER_NOT_FOUND")
        if user["company_role"] == "ADMIN":
            raise HTTPException(409, "ADMIN_USER_CANNOT_BE_PURGED")

        cur.execute(
            """
            SELECT t.worksite_id, w.name AS worksite_name, count(b.id)::int AS btb_count
            FROM tb.teams t
            JOIN tb.worksites w ON w.id = t.worksite_id
            LEFT JOIN tb.btbs b ON b.team_id = t.id
            WHERE t.lead_user_id = %s
            GROUP BY t.worksite_id, w.name
            HAVING count(b.id) > 0
            ORDER BY lower(w.name)
            LIMIT 20
            """,
            (user_id,),
        )
        history = [dict(row) for row in cur.fetchall()]
        if history:
            raise HTTPException(
                409,
                {
                    "code": "USER_PURGE_WORKSITE_HISTORY_EXISTS",
                    "message": "Benutzer leitet Teams mit BTB-Historie. Betroffene Baustelle zuerst komplett löschen oder Benutzer nur sicher löschen.",
                    "worksites": history,
                },
            )

        audit(
            cur,
            action="USER_PURGE",
            actor_user_id=admin["user_id"],
            company_id=user["company_id"],
            entity_type="USER",
            entity_id=user_id,
            details={
                "display_name": user["display_name"],
                "login_name": user["login_name"],
                "previous_status": user["status"],
                "was_soft_deleted": user["deleted_at"] is not None,
            },
        )

        cur.execute(
            """
            DELETE FROM tb.teams t
            WHERE t.lead_user_id = %s
              AND NOT EXISTS (SELECT 1 FROM tb.btbs b WHERE b.team_id = t.id)
            """,
            (user_id,),
        )
        cur.execute("DELETE FROM tb.team_members WHERE user_id = %s", (user_id,))
        cur.execute("DELETE FROM tb.worksite_memberships WHERE user_id = %s", (user_id,))
        cur.execute(
            "DELETE FROM tb.worksite_invitations WHERE invited_by = %s OR invited_user_id = %s",
            (user_id, user_id),
        )
        cur.execute("DELETE FROM tb.mutation_receipts WHERE user_id = %s", (user_id,))
        cur.execute("DELETE FROM tb.devices WHERE user_id = %s", (user_id,))
        cur.execute("DELETE FROM tb.users WHERE id = %s", (user_id,))
    return Response(status_code=204)


@router.delete("/companies/{company_id}", status_code=204)
def purge_company(
    company_id: UUID,
    payload: PurgeConfirmation,
    admin: dict[str, Any] = Depends(require_admin_csrf),
) -> Response:
    _confirm(payload, company_id)
    storage_keys: list[str] = []

    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, name, status
            FROM tb.companies
            WHERE id = %s
            FOR UPDATE
            """,
            (company_id,),
        )
        company = cur.fetchone()
        if not company:
            raise HTTPException(404, "COMPANY_NOT_FOUND")

        cur.execute("SELECT count(*)::int AS count FROM tb.survey_originals WHERE company_id = %s", (company_id,))
        survey_original_count = int(cur.fetchone()["count"])
        if settings.environment in _PRODUCTION_ENVIRONMENTS and survey_original_count > 0:
            raise HTTPException(
                409,
                {
                    "code": "COMPANY_PURGE_ORIGINALS_PROTECTED",
                    "message": "Produktive Vermessungsoriginale sind geschützt und dürfen nicht über den normalen Firmen-Purge gelöscht werden.",
                    "survey_original_count": survey_original_count,
                },
            )

        cur.execute(
            """
            SELECT 1
            FROM tb.teams t
            JOIN tb.worksites w ON w.id = t.worksite_id
            JOIN tb.users u ON u.id = t.lead_user_id
            WHERE u.company_id = %s AND w.company_id <> %s
            LIMIT 1
            """,
            (company_id, company_id),
        )
        if cur.fetchone():
            raise HTTPException(409, "COMPANY_PURGE_CROSS_COMPANY_TEAM_REFERENCE")

        cur.execute(
            """
            SELECT 1
            FROM tb.track_data_sources s
            JOIN tb.survey_originals o ON o.id = s.original_id
            JOIN tb.track_data d ON d.id = s.dataset_id
            JOIN tb.worksites w ON w.id = d.worksite_id
            WHERE o.company_id = %s AND w.company_id <> %s
            LIMIT 1
            """,
            (company_id, company_id),
        )
        if cur.fetchone():
            raise HTTPException(409, "COMPANY_PURGE_CROSS_COMPANY_SURVEY_REFERENCE")

        # A globally published segment is not private company data. Even a
        # staging company purge must not silently erase other users' shared
        # reference geometry when an original or catalog asset came from it.
        cur.execute(
            """
            SELECT 1
            FROM tb.survey_catalog_segments s
            JOIN tb.survey_catalog_axes ax ON ax.id = s.axis_id
            LEFT JOIN tb.survey_catalog_assets asset ON asset.id = s.asset_id
            WHERE ax.scope = 'GLOBAL'
              AND (
                  s.source_original_id IN (
                      SELECT id FROM tb.survey_originals WHERE company_id = %s
                  )
                  OR asset.original_id IN (
                      SELECT id FROM tb.survey_originals WHERE company_id = %s
                  )
              )
            LIMIT 1
            """,
            (company_id, company_id),
        )
        if cur.fetchone():
            raise HTTPException(409, "COMPANY_PURGE_GLOBAL_CATALOG_REFERENCES")

        storage_keys.extend(
            _storage_keys(cur, "SELECT storage_key FROM tb.files WHERE company_id = %s", (company_id,))
        )
        storage_keys.extend(
            _storage_keys(cur, "SELECT storage_key FROM tb.survey_originals WHERE company_id = %s", (company_id,))
        )
        cur.execute(
            "SELECT temp_storage_key AS storage_key FROM tb.upload_sessions WHERE company_id = %s",
            (company_id,),
        )
        storage_keys.extend(
            str(row["storage_key"]) for row in cur.fetchall() if row.get("storage_key")
        )

        cur.execute("SELECT count(*)::int AS count FROM tb.users WHERE company_id = %s", (company_id,))
        user_count = int(cur.fetchone()["count"])
        cur.execute("SELECT count(*)::int AS count FROM tb.devices d JOIN tb.users u ON u.id = d.user_id WHERE u.company_id = %s", (company_id,))
        device_count = int(cur.fetchone()["count"])
        cur.execute("SELECT count(*)::int AS count FROM tb.worksites WHERE company_id = %s", (company_id,))
        worksite_count = int(cur.fetchone()["count"])
        cur.execute("SELECT count(*)::int AS count FROM tb.files WHERE company_id = %s", (company_id,))
        file_count = int(cur.fetchone()["count"])

        audit(
            cur,
            action="COMPANY_PURGE",
            actor_user_id=admin["user_id"],
            company_id=company_id,
            entity_type="COMPANY",
            entity_id=company_id,
            details={
                "name": company["name"],
                "previous_status": company["status"],
                "users": user_count,
                "devices": device_count,
                "worksites": worksite_count,
                "files": file_count,
                "survey_originals": survey_original_count,
                "environment": settings.environment,
            },
        )

        cur.execute("DELETE FROM tb.worksites WHERE company_id = %s", (company_id,))
        cur.execute(
            "DELETE FROM tb.mutation_receipts WHERE user_id IN (SELECT id FROM tb.users WHERE company_id = %s)",
            (company_id,),
        )
        cur.execute(
            "DELETE FROM tb.team_members WHERE user_id IN (SELECT id FROM tb.users WHERE company_id = %s)",
            (company_id,),
        )
        cur.execute(
            "DELETE FROM tb.worksite_memberships WHERE user_id IN (SELECT id FROM tb.users WHERE company_id = %s)",
            (company_id,),
        )
        cur.execute(
            """
            DELETE FROM tb.worksite_invitations
            WHERE invited_by IN (SELECT id FROM tb.users WHERE company_id = %s)
               OR invited_user_id IN (SELECT id FROM tb.users WHERE company_id = %s)
            """,
            (company_id, company_id),
        )
        cur.execute(
            "DELETE FROM tb.devices WHERE user_id IN (SELECT id FROM tb.users WHERE company_id = %s)",
            (company_id,),
        )

        # Migration 017 lifecycle cleanup. Production companies with survey
        # originals are rejected above; this path therefore only changes allowed
        # staging/test purges or catalog-only company data.
        cur.execute(
            """
            DELETE FROM tb.survey_catalog_conflicts c
            WHERE c.axis_id IN (
                SELECT a.id FROM tb.survey_catalog_axes a
                WHERE a.scope = 'COMPANY' AND a.company_id = %s
            )
               OR c.candidate_segment_id IN (
                    SELECT s.id
                    FROM tb.survey_catalog_segments s
                    LEFT JOIN tb.survey_catalog_assets a ON a.id = s.asset_id
                    WHERE s.axis_id IN (
                              SELECT ax.id FROM tb.survey_catalog_axes ax
                              WHERE ax.scope = 'COMPANY' AND ax.company_id = %s
                          )
                       OR s.source_original_id IN (
                              SELECT o.id FROM tb.survey_originals o WHERE o.company_id = %s
                          )
                       OR a.original_id IN (
                              SELECT o.id FROM tb.survey_originals o WHERE o.company_id = %s
                          )
               )
               OR c.existing_segment_id IN (
                    SELECT s.id
                    FROM tb.survey_catalog_segments s
                    LEFT JOIN tb.survey_catalog_assets a ON a.id = s.asset_id
                    WHERE s.axis_id IN (
                              SELECT ax.id FROM tb.survey_catalog_axes ax
                              WHERE ax.scope = 'COMPANY' AND ax.company_id = %s
                          )
                       OR s.source_original_id IN (
                              SELECT o.id FROM tb.survey_originals o WHERE o.company_id = %s
                          )
                       OR a.original_id IN (
                              SELECT o.id FROM tb.survey_originals o WHERE o.company_id = %s
                          )
               )
            """,
            (
                company_id,
                company_id, company_id, company_id,
                company_id, company_id, company_id,
            ),
        )
        cur.execute(
            """
            DELETE FROM tb.survey_catalog_segments s
            USING tb.survey_catalog_axes ax
            WHERE s.axis_id = ax.id
              AND (
                    (ax.scope = 'COMPANY' AND ax.company_id = %s)
                 OR s.source_original_id IN (
                        SELECT o.id FROM tb.survey_originals o WHERE o.company_id = %s
                    )
                 OR s.asset_id IN (
                        SELECT a.id
                        FROM tb.survey_catalog_assets a
                        JOIN tb.survey_originals o ON o.id = a.original_id
                        WHERE o.company_id = %s
                    )
              )
            """,
            (company_id, company_id, company_id),
        )
        cur.execute(
            """
            DELETE FROM tb.survey_catalog_assets a
            WHERE a.company_id = %s
               OR a.original_id IN (
                    SELECT o.id FROM tb.survey_originals o WHERE o.company_id = %s
               )
            """,
            (company_id, company_id),
        )
        cur.execute(
            """
            -- A company's purge must never remove an unrelated empty
            -- GLOBAL or other company's axis, even if it has no segments.
            DELETE FROM tb.survey_catalog_axes a
            WHERE a.scope = 'COMPANY' AND a.company_id = %s
            """,
            (company_id,),
        )

        cur.execute(
            "DELETE FROM tb.track_data_sources WHERE original_id IN (SELECT id FROM tb.survey_originals WHERE company_id = %s)",
            (company_id,),
        )
        cur.execute(
            """
            UPDATE tb.track_data_km_points
            SET source_original_id = NULL
            WHERE source_original_id IN (SELECT id FROM tb.survey_originals WHERE company_id = %s)
            """,
            (company_id,),
        )
        cur.execute(
            "DELETE FROM tb.survey_parse_catalog WHERE original_id IN (SELECT id FROM tb.survey_originals WHERE company_id = %s)",
            (company_id,),
        )
        cur.execute("DELETE FROM tb.survey_originals WHERE company_id = %s", (company_id,))
        cur.execute("DELETE FROM tb.files WHERE company_id = %s", (company_id,))
        cur.execute("DELETE FROM tb.users WHERE company_id = %s", (company_id,))
        cur.execute("DELETE FROM tb.companies WHERE id = %s", (company_id,))

    _remove_keys_after_commit(storage_keys)
    return Response(status_code=204)
