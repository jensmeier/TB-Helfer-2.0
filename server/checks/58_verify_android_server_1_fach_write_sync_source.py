from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FachSyncApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
coordinator = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
strings = (ROOT / "app/src/main/res/values/collab_strings.xml").read_text(encoding="utf-8")
defects = (ROOT / "server/tbserver/defects.py").read_text(encoding="utf-8")
acceptances = (ROOT / "server/tbserver/acceptances.py").read_text(encoding="utf-8")

for token in (
    "fun applyPending",
    "X-Client-Change-Id",
    "/defects",
    "/close",
    "/acceptances",
    "/finalize",
    '"ABGELEHNT"',
    '"REJECTED"',
    "derivedChangeId",
    "DEVICE_REVOKED",
):
    assert token in api, token

for token in (
    "synchronizeServer1FachPending",
    "TYPE_DEFECT",
    "TYPE_ACCEPTANCE",
    "STATE_CONFLICT",
    "deleteCollabPendingChange",
):
    assert token in repo, token

for token in (
    "refreshFachSnapshots",
    "bridge.exportLocalChanges()",
    "bridge.applyServerStateToLocal()",
    "collab.synchronizeServer1FachPending",
):
    assert token in coordinator, token

assert "Server1AndroidSyncCoordinator" in fragment
assert coordinator.index("refreshFachSnapshots(token, remoteContexts, capabilities)") < coordinator.index(
    "bridge.exportLocalChanges()"
)
assert coordinator.index("bridge.exportLocalChanges()") < coordinator.index(
    "collab.synchronizeServer1FachPending"
)

assert '@router.post("/defects/{defect_id}/close")' in defects
assert '@router.post("/acceptances/{acceptance_id}/finalize")' in acceptances
assert "Mängel/Abnahmen" in strings
assert "Server 1.0" in strings

print("OK: Android Server-1.0 idempotent defect/acceptance write sync source contract")
