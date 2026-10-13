from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
local = (ROOT / "app/src/main/java/de/tbhelfer/ui/abnahme/AbnahmeSpeicher.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt").read_text(encoding="utf-8")
dao = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/SyncDao.kt").read_text(encoding="utf-8")

for token in (
    "fun loeschenVomServer",
    "AbnahmeDeleteIntentStore.bestaetigen",
    "loescheLokal",
):
    assert token in local, token

server_delete_block = local.split("fun loeschenVomServer", 1)[1].split("private fun loescheLokal", 1)[0]
assert "AbnahmeDeleteIntentStore.merken" not in server_delete_block

assert "suspend fun deletedEntities" in repo
assert "dao.deletedCollabEntities" in repo
assert "deletedAcceptanceItems" in bridge
assert "collabRepository.deletedEntities" in bridge
assert "AbnahmeSpeicher.loeschenVomServer" in bridge
assert "collabPendingAnyForClientKey" in bridge
assert "isDeleted = 1" in dao

print("OK: remote Server-1.0 acceptance tombstones remove local acceptances without echo deletes")
