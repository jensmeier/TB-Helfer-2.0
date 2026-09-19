from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
baseline = (ROOT / "server/v1_migrations/001_server_1_0_base.sql").read_text(encoding="utf-8")
migration = (ROOT / "server/v1_migrations/009_defect_rejected_status.sql").read_text(encoding="utf-8")
defects = (ROOT / "server/tbserver/defects.py").read_text(encoding="utf-8")
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FachSyncApi.kt").read_text(encoding="utf-8")
android_status = (ROOT / "app/src/main/java/de/tbhelfer/ui/baustelle/MangelStatusSpeicher.kt").read_text(encoding="utf-8")
portal = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")

ast.parse(defects, filename="server/tbserver/defects.py")
ast.parse(files, filename="server/tbserver/files.py")

assert "ABGELEHNT" in android_status
assert "REJECTED" in baseline
assert "DROP CONSTRAINT IF EXISTS defects_status_check" in migration
assert "REJECTED" in migration
assert 'pattern="^(OPEN|IN_PROGRESS|REJECTED)$"' in defects
assert '"REJECTED": "ABGELEHNT"' in files

for token in (
    '"ABGELEHNT"',
    '"REJECTED"',
    '"REJECTED" -> "ABGELEHNT"',
    'if (desired == "ABGELEHNT") "REJECTED" else "IN_PROGRESS"',
):
    assert token in api, token

assert 'value="REJECTED">Abgelehnt</option>' in portal
assert "kein kanonisches Server-1.0-Gegenstück" not in api

print("OK: Android ABGELEHNT round-trips canonically as Server-1.0 REJECTED")
