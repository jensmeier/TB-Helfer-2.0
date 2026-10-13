from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
server = (ROOT / "server/tbserver/worksites.py").read_text(encoding="utf-8")
delete_api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteDeletionApi.kt").read_text(encoding="utf-8")
create_api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteProvisioningApi.kt").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")
schema_worker = (ROOT / "app/src/main/java/de/tbhelfer/server/Schema13Worker.kt").read_text(encoding="utf-8")

ast.parse(server, filename="server/tbserver/worksites.py")

for token in (
    '@router.post("", status_code=201)',
    '@router.get("/deletions")',
    '@router.delete("/{worksite_id}", status_code=204)',
    'context.get("worksite_role") != "RESPONSIBLE_TB"',
    '"WORKSITE_DELETE_LEAD_REQUIRED"',
    '"VERSION_CONFLICT"',
    "tb.deletion_tombstones",
    "entity_type = 'WORKSITE'",
    'status = "TRASHED"' if False else "status = 'TRASHED'",
    'receipt_type="WORKSITE_DELETE"',
):
    assert token in server, token

for token in (
    "/api/v1/worksites/",
    "/api/v1/worksites/deletions",
    "X-Client-Change-Id",
    "loadCurrentVersion",
    "legacyDelete",
    "legacyList",
    "/api/v1/collab/worksites/",
    "/api/v1/collab/worksite-deletions",
):
    assert token in delete_api, token

for token in (
    '"/api/v1/worksites"',
    "X-Client-Change-Id",
    "stableLocalKey",
    "TeamManagementApi",
    "legacyCreate",
    '"/api/v1/collab/worksites"',
):
    assert token in create_api, token

assert '"worksite_provisioning_v1": True' in caps
assert '"worksite_delete_v1": True' in caps
assert "WorksiteDeletionApi().list(token)" in schema_worker

print("OK: Server-1.0 worksite create/delete uses idempotency, soft delete and canonical tombstones")
