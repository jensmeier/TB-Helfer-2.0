from __future__ import annotations

import ast
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_PY = [
    ROOT / "server/tbserver/__init__.py",
    ROOT / "server/tbserver/config.py",
    ROOT / "server/tbserver/database.py",
    ROOT / "server/tbserver/migrations.py",
    ROOT / "server/tbserver/capabilities.py",
    ROOT / "server/tbserver/main.py",
]
REQUIRED_OTHER = [
    ROOT / "server/v1_migrations/001_server_1_0_base.sql",
    ROOT / "server/Dockerfile.v1",
]


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


for path in REQUIRED_PY + REQUIRED_OTHER:
    if not path.exists():
        fail(f"fehlt: {path.relative_to(ROOT)}")

for path in REQUIRED_PY:
    try:
        ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except SyntaxError as exc:
        fail(f"Python-Syntaxfehler in {path.relative_to(ROOT)}: {exc}")

sql = REQUIRED_OTHER[0].read_text(encoding="utf-8")
required_sql_tokens = [
    "CREATE TABLE tb.companies",
    "CREATE TABLE tb.users",
    "CREATE TABLE tb.portal_sessions",
    "CREATE TABLE tb.device_activation_tokens",
    "CREATE TABLE tb.permission_profiles",
    "CREATE TABLE tb.worksites",
    "CREATE TABLE tb.worksite_nodes",
    "CREATE TABLE tb.worksite_memberships",
    "CREATE TABLE tb.worksite_invitations",
    "CREATE TABLE tb.teams",
    "CREATE TABLE tb.btbs",
    "CREATE TABLE tb.btb_entries",
    "CREATE TABLE tb.files",
    "CREATE TABLE tb.upload_sessions",
    "CREATE TABLE tb.defects",
    "CREATE TABLE tb.acceptances",
    "CREATE TABLE tb.survey_originals",
    "CREATE TABLE tb.track_data",
    "CREATE TABLE tb.mutation_receipts",
    "CREATE TABLE tb.change_feed",
    "CREATE TABLE tb.sync_cursors",
    "CREATE TABLE tb.audit_log",
    "CREATE TABLE tb.releases",
    "CREATE TABLE tb.background_jobs",
    "CREATE TABLE tb.backup_runs",
    "CREATE TABLE tb.system_metrics_hourly",
]
for token in required_sql_tokens:
    if token not in sql:
        fail(f"Baseline-SQL enthält nicht: {token}")

migrations = (ROOT / "server/tbserver/migrations.py").read_text(encoding="utf-8")
for token in ("checksum_sha256", "pg_advisory_lock", "Prüfsumme", "schema_migrations"):
    if token not in migrations:
        fail(f"Migration Runner enthält nicht: {token}")

capabilities = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")
if 'router = APIRouter(prefix="/api/v1"' not in capabilities:
    fail("Capability-API liegt nicht unter /api/v1")
if '"server_1_0_foundation_v1": True' not in capabilities:
    fail("Foundation-Capability fehlt")

main = (ROOT / "server/tbserver/main.py").read_text(encoding="utf-8")
for token in ("/health/live", "/health/ready", "capabilities_router"):
    if token not in main:
        fail(f"Server main enthält nicht: {token}")

print("OK: TB-Server 1.0 Foundation Source-Check")
