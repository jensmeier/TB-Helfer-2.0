from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")

assert "val completeSnapshot: Boolean" in api
assert "FILE_LIST_LIMIT = 1000" in api
assert "array.length() < FILE_LIST_LIMIT" in api
assert "if (listResult.completeSnapshot)" in repo
assert "possibly incomplete" in repo
assert repo.index("if (listResult.completeSnapshot)") < repo.index("dao.markCollabAttachmentDeleted")

print("OK: Android Server-1.0 file cleanup is guarded against the 1000-item truncation limit")
