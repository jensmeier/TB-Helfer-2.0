from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
sync = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
delete_coordinator = (ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDeleteSyncCoordinator.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    "MutationResult.Deleted",
    "return deletePending(token, change)",
    "private fun deletePending",
    'change.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE',
    '"DELETE"',
    '"VERSION_CONFLICT"',
    'subChangeId(change.clientChangeId, "delete")',
):
    assert token in api, token

for token in (
    "is Server1BtbApi.MutationResult.Deleted",
    "dao.markCollabDeleted",
    "hasPendingServer1BtbDelete",
    "OP_DELETE",
):
    assert token in repo, token

for token in (
    "BtbDeleteSyncCoordinator.reconcileAfterSuccessfulPull",
    "reconcileBtbDeletes",
    "collab.hasPendingServer1BtbDelete",
    "collab.synchronizeServer1BtbPending",
    "refreshBtbSnapshots",
):
    assert token in sync, token

assert sync.index("reconcileBtbDeletes(token, remoteContexts)") < sync.index(
    "bridge.applyServerStateToLocal()"
)
assert "operation = SyncCollabPendingChangeEntity.OP_DELETE" in delete_coordinator
assert "Server1AndroidSyncCoordinator" in fragment
assert '@router.delete("/btbs/{btb_id}", status_code=204)' in server
assert "deletion_tombstones" in server
assert "invalidate_final_pdf" in server

print("OK: Android Server-1.0 BTB delete reuses offline intent/outbox before normal BTB import")
