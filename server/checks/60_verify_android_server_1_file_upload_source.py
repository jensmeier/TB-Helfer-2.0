from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt").read_text(encoding="utf-8")
coordinator = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")

for token in (
    "fun uploadLinkedFile",
    "X-Client-Change-Id",
    "/uploads",
    "/chunk?offset=",
    "/complete",
    "loadUploadSession",
    "receivedBytes",
    "expected_sha256",
    "mutationId",
):
    assert token in api, token

for token in (
    "server1Api: Server1FileApi?",
    "server1Api.uploadLinkedFile",
    "SyncCollabAttachmentEntity",
):
    assert token in repo, token

assert "server1Api: Server1FileApi?" in bridge
assert "api, server1Api" in bridge
assert 'capabilities.supports("chunk_upload_v1")' in coordinator
assert "server1Api = fileApi" in coordinator
assert "Server1AndroidSyncCoordinator" in fragment

assert '@router.post("/worksites/{worksite_id}/uploads"' in server
assert '@router.put("/uploads/{upload_id}/chunk")' in server
assert '@router.post("/uploads/{upload_id}/complete")' in server

print("OK: Android Server-1.0 resumable attachment upload source contract")
