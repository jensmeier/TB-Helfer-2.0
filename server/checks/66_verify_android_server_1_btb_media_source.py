from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
dao = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/SyncDao.kt").read_text(encoding="utf-8")
media = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt").read_text(encoding="utf-8")
sync = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
btb_docs = (ROOT / "server/tbserver/btb_documents.py").read_text(encoding="utf-8")

for token in (
    'type != "DEFECT" && type != "ACCEPTANCE" && type != "BTB"',
    'parentType != "DEFECT" && parentType != "ACCEPTANCE" && parentType != "BTB"',
    'parentType == "BTB" && mimeType.startsWith("image/"',
    '"PHOTO"',
    '"BTB_FINAL_PDF"',
):
    assert token in api, token

assert "parentEntityType IN ('DEFECT','ACCEPTANCE','BTB')" in dao
assert "server1Api: Server1FileApi? = null" in media
assert "server1Api = server1Api" in media
assert "uploadedForBtb" in media
assert 'status = "FERTIG"' in media
assert "media.synchronize" in sync
assert "server1Api = fileApi" in sync
assert "BTB-Abschluss nach Foto-Sync" in sync
assert "Server1AndroidSyncCoordinator" in fragment
assert "prepare_btb_photo_change" in files
assert "def prepare_btb_photo_change" in btb_docs
assert "status = 'REWORK'" in btb_docs
assert "invalidate_final_pdf" in btb_docs

print("OK: Android Server-1.0 BTB photos use canonical resumable files and re-finalize lifecycle")
