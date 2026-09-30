from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PURGE = ROOT / "server" / "tbserver" / "admin_purge.py"

source = PURGE.read_text(encoding="utf-8")
ast.parse(source, filename=str(PURGE))

assert '@router.get("/survey-test-data")' in source
assert '@router.delete("/survey-test-data")' in source
assert '_SURVEY_TEST_RESET_ENVIRONMENTS' in source
assert 'SURVEY_TEST_RESET_ENVIRONMENT_FORBIDDEN' in source
assert 'VERMESSUNG TESTDATEN LOESCHEN' in source

required_order = [
    "DELETE FROM tb.survey_catalog_conflicts",
    "DELETE FROM tb.survey_catalog_segments",
    "DELETE FROM tb.survey_catalog_assets",
    "DELETE FROM tb.survey_catalog_axes",
    "DELETE FROM tb.survey_catalog_history",
    "DELETE FROM tb.track_data WHERE dataset_kind = 'SURVEY_SECTION'",
    "DELETE FROM tb.track_data_sources",
    "DELETE FROM tb.survey_parse_catalog",
    "DELETE FROM tb.survey_originals",
]
positions = [source.index(token) for token in required_order]
assert positions == sorted(positions), positions

assert "UPDATE tb.track_data SET source_original_id = NULL" in source
assert "f.source = 'IMPORT'" in source
assert "_remove_keys_after_commit(sorted(set(storage_keys)))" in source

print("PASS survey staging reset source")
