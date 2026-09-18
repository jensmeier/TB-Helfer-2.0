from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
dao = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/SyncDao.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")

assert "suspend fun collabFachAttachments" in dao
assert "parentEntityType IN ('DEFECT','ACCEPTANCE')" in dao
assert "suspend fun markCollabAttachmentDeleted" in dao
assert "SET isDeleted = 1" in dao

for token in (
    "visibleFileIds = files.mapTo",
    "dao.collabFachAttachments(worksiteId)",
    "it.serverId !in visibleFileIds",
    "dao.markCollabAttachmentDeleted(it.serverId)",
):
    assert token in repo, token

assert repo.index("visibleFileIds = files.mapTo") < repo.index("for (item in files)")

print("OK: Android Server-1.0 file snapshot removes stale/hidden fach attachments")
