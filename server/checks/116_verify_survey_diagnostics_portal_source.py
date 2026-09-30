from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "server" / "templates" / "v1_admin_survey_import.html"
CATALOG = ROOT / "server" / "tbserver" / "survey_catalog.py"
ADMIN_IMPORT = ROOT / "server" / "tbserver" / "admin_survey_import.py"
MIGRATION = ROOT / "server" / "v1_migrations" / "018_survey_object_identity.sql"

template = TEMPLATE.read_text(encoding="utf-8")
catalog = CATALOG.read_text(encoding="utf-8")
admin_import = ADMIN_IMPORT.read_text(encoding="utf-8")
migration = MIGRATION.read_text(encoding="utf-8")

for token in (
    "data-inferred-route",
    "data-inferred-track",
    "data-inferred-object",
    "decision-object",
    "object_key:settings.object_key",
    "let axisHitCandidates=[]",
    "function nearbyAxisCandidates(",
    "function showAxisChoices(",
    "Achsen im Trefferbereich",
    'id="conflictCard"',
    'id="conflictBackdrop"',
    "Problem ansehen",
    "function renderConflictGeometry(",
    "data-conflict-resolution",
    "/api/v1/survey-catalog/conflicts/",
):
    assert token in template, token

assert "object_key: str | None = Field(default=None, max_length=160)" in admin_import
assert 'metadata["object_key"] = effective_object_key' in admin_import
assert '@router.get("/conflicts/{conflict_id}")' in catalog
assert "_CONFLICT_EXPLANATIONS" in catalog
assert "_conflict_segment_detail" in catalog
assert "ADD COLUMN object_key text" in migration

# Protected REF-DATA-002 interaction remains present.
for token in ("axis-hit", "toggleFullscreen", "calibrateAxisFromGvp", "zoomAt("):
    assert token in template, token

print("PASS survey diagnostics portal source")
