from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
strings = (ROOT / "app/src/main/res/values/collab_strings.xml").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/worksites/",
    "/files?limit=1000",
    "/api/v1/files/",
    "/content",
    'type != "DEFECT" && type != "ACCEPTANCE" && type != "BTB"',
    "Authorization",
):
    assert token in api, token

for token in (
    "synchronizeServer1Attachments",
    "SyncCollabAttachmentEntity",
    "markCollabAttachmentDownloaded",
    "server_sync/collab/",
):
    assert token in repo, token

assert 'capabilities.supports("files_v1")' in fragment
assert "collab.synchronizeServer1Attachments" in fragment
assert "Dateien" in strings
assert "Server 1.0" in strings

assert '@router.get("/worksites/{worksite_id}/files")' in server
assert '@router.get("/files/{file_id}")' in server
assert '@router.get("/files/{file_id}/content")' in server

print("OK: Android Server-1.0 linked file read/download source contract")
