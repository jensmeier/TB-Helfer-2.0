from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "server" / "tbserver" / "survey_catalog.py"
MIGRATION = ROOT / "server" / "v1_migrations" / "017_survey_reference_catalog.sql"
MAIN = ROOT / "server" / "tbserver" / "main.py"
JOBS = ROOT / "server" / "tbserver" / "jobs.py"
NOTIFICATIONS = ROOT / "server" / "tbserver" / "notifications.py"
ADMIN_IMPORT = ROOT / "server" / "tbserver" / "admin_survey_import.py"
PARSER = ROOT / "server" / "tbserver" / "survey_parser.py"

for path in (CATALOG, MAIN, JOBS, NOTIFICATIONS, ADMIN_IMPORT, PARSER):
    ast.parse(path.read_text(encoding="utf-8"), filename=str(path))

catalog = CATALOG.read_text(encoding="utf-8")
migration = MIGRATION.read_text(encoding="utf-8")
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

print("PASS survey reference catalog source")
