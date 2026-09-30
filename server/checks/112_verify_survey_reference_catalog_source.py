from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "server" / "tbserver" / "survey_catalog.py"
MIGRATION = ROOT / "server" / "v1_migrations" / "017_survey_reference_catalog.sql"
OBJECT_MIGRATION = ROOT / "server" / "v1_migrations" / "018_survey_object_identity.sql"
HISTORY_MIGRATION = ROOT / "server" / "v1_migrations" / "019_survey_catalog_history_enrich.sql"
MAIN = ROOT / "server" / "tbserver" / "main.py"
JOBS = ROOT / "server" / "tbserver" / "jobs.py"
NOTIFICATIONS = ROOT / "server" / "tbserver" / "notifications.py"
ADMIN_IMPORT = ROOT / "server" / "tbserver" / "admin_survey_import.py"
PARSER = ROOT / "server" / "tbserver" / "survey_parser.py"
SURVEY_ORIGINALS = ROOT / "server" / "tbserver" / "survey_originals.py"
ADMIN_PURGE = ROOT / "server" / "tbserver" / "admin_purge.py"
CAPABILITIES = ROOT / "server" / "tbserver" / "capabilities.py"

for path in (CATALOG, MAIN, JOBS, NOTIFICATIONS, ADMIN_IMPORT, PARSER, SURVEY_ORIGINALS, ADMIN_PURGE, CAPABILITIES):
    ast.parse(path.read_text(encoding="utf-8"), filename=str(path))

catalog = CATALOG.read_text(encoding="utf-8")
migration = MIGRATION.read_text(encoding="utf-8")
object_migration = OBJECT_MIGRATION.read_text(encoding="utf-8")
history_migration = HISTORY_MIGRATION.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
jobs = JOBS.read_text(encoding="utf-8")
notifications = NOTIFICATIONS.read_text(encoding="utf-8")
admin_import = ADMIN_IMPORT.read_text(encoding="utf-8")
parser = PARSER.read_text(encoding="utf-8")

required_tables = (
    "survey_catalog_assets",
    "survey_catalog_axes",
    "survey_catalog_segments",
    "survey_catalog_conflicts",
    "survey_catalog_history",
)
for table in required_tables:
    assert f"CREATE TABLE tb.{table}" in migration, table

for token in (
    'OVERLAP_CONFIRMED',
    'ENDPOINT_CONFIRMED',
    'REVIEW_REQUIRED',
    '@router.get("/search")',
    '@router.get("/availability")',
    '@router.get("/geometry")',
    '@router.get("/changes")',
    '@router.post("/manual-segments"',
    '@router.patch("/manual-segments/{segment_id}")',
    '@router.get("/conflicts/{conflict_id}")',
    '@router.post("/conflicts/{conflict_id}/resolve")',
    'index_original_in_tx',
):
    assert token in catalog, token

assert "survey_catalog_router" in main
assert "SURVEY_CONFLICT_EMAIL" in jobs
assert "SURVEY_CATALOG_REINDEX" in jobs
assert "run_survey_conflict_email" in notifications
assert "index_original_in_tx(cur, original_id" in admin_import
assert "index_original_in_tx(cur, original_id" in parser
assert "station_code" in admin_import and "station_name" in admin_import
assert 'SURVEY_CATALOG_COMPANY_SCOPE_FORBIDDEN' in catalog
assert 'station_code: str | None,\n    station_name: str | None,\n    source_date: date | None,\n    overwrite_existing: bool = False,\n    user: dict[str, Any],' in admin_import
assert '@router.delete("/manual-segments/{segment_id}")' in catalog
assert 'GVP_MAX_AXIS_DISTANCE_M = 120.0' in catalog
assert 'GVP_OFFSET_OUTLIER_M = 80.0' in catalog
survey_originals = (ROOT / "server" / "tbserver" / "survey_originals.py").read_text(encoding="utf-8")
assert '"catalog_scope"' in survey_originals
assert "station_code: str | None" in survey_originals
assert "station_name: str | None" in survey_originals
assert "source_date: date | None" in survey_originals
assert '"active_segment_count"' in catalog
assert 'gra.get("source_date") == segment_source_date' in catalog
assert 'def update_axis(' in catalog
assert 'user: dict[str,Any] = Depends(require_csrf)' in catalog
assert '_assert_segment_write_access(user, axis)' in catalog
survey_originals = SURVEY_ORIGINALS.read_text(encoding="utf-8")
assert 'Active worksite membership is sufficient for survey contribution.' in survey_originals
assert 'Every active worksite member may contribute/re-parse survey data.' in parser
assert 'def _geometry_fully_covers(' in catalog
assert 'coverage >= 0.8' not in catalog
assert 'coverage >= 0.9' not in catalog
assert 'def _sanitize_catalog_value(' in catalog
assert '_catalog_response(user, item)' in catalog
assert '_catalog_response(user, row)' in catalog
assert '"next_after":next_after' in catalog
assert 'next_after = int(row["change_id"])' in catalog
assert 'SURVEY_ORIGINAL_GLOBAL_ALREADY_AVAILABLE' in survey_originals
assert 'SELECT route_number, station_code, station_name' in survey_originals
assert 'effective_route' in survey_originals
assert 'source_date: date | None = None' in admin_import
assert 'object_key: str | None = Field(default=None, max_length=160)' in admin_import
assert 'metadata["object_key"] = effective_object_key' in admin_import
assert 'object_key=decision.object_key' in admin_import
assert 'source_date=decision.source_date' in admin_import
assert 'source_date=source_date' in admin_import
admin_purge = ADMIN_PURGE.read_text(encoding="utf-8")
assert "Migration 017 lifecycle cleanup" in admin_purge
assert '@router.get("/survey-test-data")' in admin_purge
assert '@router.delete("/survey-test-data")' in admin_purge
assert 'SURVEY_TEST_RESET_ENVIRONMENT_FORBIDDEN' in admin_purge
assert '_SURVEY_TEST_RESET_TEXT = "VERMESSUNG TESTDATEN LOESCHEN"' in admin_purge
assert "DELETE FROM tb.track_data WHERE dataset_kind = 'SURVEY_SECTION'" in admin_purge
assert "DELETE FROM tb.survey_parse_catalog" in admin_purge
assert "DELETE FROM tb.survey_originals" in admin_purge
conflict_delete = admin_purge.index("DELETE FROM tb.survey_catalog_conflicts")
segment_delete = admin_purge.index("DELETE FROM tb.survey_catalog_segments")
asset_delete = admin_purge.index("DELETE FROM tb.survey_catalog_assets")
original_delete = admin_purge.index("DELETE FROM tb.survey_originals WHERE company_id")
assert conflict_delete < segment_delete < asset_delete < original_delete
capabilities = CAPABILITIES.read_text(encoding="utf-8")
for capability in (
    "survey_reference_catalog_v1",
    "survey_reference_contributions_v1",
    "survey_reference_change_feed_v1",
):
    assert f'"{capability}": True' in capabilities, capability
assert "same_route_hint" in catalog
assert "same_station_hint" in catalog
assert "bbox_distance <= OVERLAP_NEAR_DISTANCE_M" in catalog
assert "ADD COLUMN object_key text" in object_migration
assert "survey_catalog_axes_object_idx" in object_migration
assert "survey_catalog_history_operation_check" in history_migration
assert "'ENRICH'" in history_migration
assert "object_key_from_name" in parser
assert 'fields.get("object_key")' in catalog
assert 'filters.append("object_key = %s")' in catalog
assert 'def _axis_identity_enrichment_patch(' in catalog
assert 'SURVEY_CATALOG_AXIS_IDENTITY_ENRICH' in catalog
assert '"status":"ALREADY_INDEXED_ENRICHED" if enriched else "ALREADY_INDEXED"' in catalog
assert '"status":"ALREADY_INDEXED_REASSIGNED"' in catalog
assert 'SURVEY_CATALOG_SEGMENT_IDENTITY_REASSIGN' in catalog
assert 'def _should_reassign_existing_segment_identity(' in catalog
assert 'from .survey_parser import PARSER_VERSION, reparse_original_for_catalog' in catalog
assert 'LEFT JOIN tb.survey_parse_catalog p ON p.original_id=o.id' in catalog
assert 'parser_stale = (' in catalog
assert '"reparsed":reparsed' in catalog
assert 'def refresh_original_assignment_in_tx(' in catalog
assert 'SURVEY_CATALOG_ASSIGNMENT_UPDATE' in catalog
assert 'SURVEY_ASSIGNMENT_UPDATE_SHARED_AXIS_REQUIRES_REVIEW' in catalog
assert 'overwrite_existing: bool = False' in admin_import
assert '"status": "VORHANDEN_ABWEICHEND"' in admin_import
assert '"status": "AKTUALISIERT"' in admin_import
assert 'ADMIN_SURVEY_ORIGINAL_ASSIGNMENT_UPDATE' in admin_import
assert 'refresh_original_assignment_in_tx(' in admin_import

print("PASS survey reference catalog source")
