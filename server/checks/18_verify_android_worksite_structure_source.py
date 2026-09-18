from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
structure = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/WorksiteStructureSync.kt").read_text("utf-8")
entities = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncEntities.kt").read_text("utf-8")
sync_db = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/SyncDatenbank.kt").read_text("utf-8")
worker = (ROOT / "app/src/main/java/de/tbhelfer/server/SyncWorker.kt").read_text("utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteNodeApi.kt").read_text("utf-8")

for token in [
    'TYPE_WORKSITE_NODE = "WORKSITE_NODE"',
    'tableName = "sync_worksite_node_binding"',
    'clientNodeKey: String',
]:
    assert token in entities, f"Android-Entity-Vertrag fehlt: {token}"

for token in [
    "version = 4",
    "MIGRATION_3_4",
    "sync_worksite_node_binding",
    "worksiteNodeSyncDao",
]:
    assert token in sync_db, f"Sync-DB-Vertrag fehlt: {token}"

for token in [
    "prepareLocalChanges",
    "applyServerStateToLocal",
    '"parent_client_node_key"',
    "memberRole.equals(\"LEAD\"",
    "collabRepository.enqueueMutation",
    "TYPE_WORKSITE_NODE",
    "loescheTeilbaum",
]:
    assert token in structure, f"Struktur-Sync-Vertrag fehlt: {token}"

# Fremde lokale Room-IDs dürfen niemals Teil des Server-Payloads werden.
assert 'put("localKnotenId"' not in structure
assert 'put("parentId"' not in structure
assert 'put("rootId"' not in structure

for token in [
    "ServiceLocator.worksiteStructureSync",
    "structureSync.prepareLocalChanges(token)",
    "structureSync.applyServerStateToLocal(token)",
]:
    assert token in worker, f"SyncWorker-Strukturhook fehlt: {token}"

assert '/api/v1/worksites/${encode(worksiteId)}/nodes?include_deleted=true' in api
assert "external_key" in api
assert "MutationResult" in api
assert "isDeleted" in api
print("OK: Android-Baustellenstruktur verwendet stabile Keys, Offline-Outbox und kanonischen Server-1.0-Transport.")
