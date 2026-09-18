from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

ast.parse(files, filename="server/tbserver/files.py")

for token in (
    "def _encode_file_cursor",
    "def _decode_file_cursor",
    "INVALID_FILE_CURSOR",
    "cursor: str | None = Query",
    "(created_at, id) < (%s, %s)",
    "ORDER BY created_at DESC, id DESC",
    "has_more_visible",
    '"next_cursor": next_cursor',
    '"snapshot_complete": next_cursor is None',
):
    assert token in files, token

for token in (
    'root.has("snapshot_complete")',
    'root.has("next_cursor")',
    "seenCursors",
    'append("&cursor=")',
    "explicitPagination",
    "explicitComplete",
    "legacyComplete = array.length() < FILE_LIST_LIMIT",
):
    assert token in api, token

assert '"file_cursor_pagination_v1": True' in caps

# Full cursor pages may now be safely tombstoned only after the server reports completion.
assert "completeSnapshot = if (explicitPagination) explicitComplete else legacyComplete" in api

print("OK: Server-1.0 file list uses stable visible-file cursor pagination with legacy guard")
