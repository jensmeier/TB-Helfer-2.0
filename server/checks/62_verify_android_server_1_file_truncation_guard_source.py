from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")

assert "val completeSnapshot: Boolean" in api
assert "FILE_LIST_LIMIT = 1000" in api
assert 'root.has("snapshot_complete")' in api
assert 'root.has("next_cursor")' in api
assert "explicitPagination" in api
assert "legacyComplete = array.length() < FILE_LIST_LIMIT" in api
assert "seenCursors" in api
assert "if (listResult.completeSnapshot)" in repo
assert "possibly incomplete" in repo
assert repo.index("if (listResult.completeSnapshot)") < repo.index("dao.markCollabAttachmentDeleted")

print("OK: Android file cleanup supports cursor snapshots and keeps the legacy 1000-item guard")
