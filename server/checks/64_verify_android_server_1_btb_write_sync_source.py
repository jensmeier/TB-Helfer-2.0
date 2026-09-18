from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    "fun applyPending",
    "X-Client-Change-Id",
    "desiredEntries(",
    "deterministicEntryId",
    "BTB_ENTRY_CONFLICT",
    "BTB_ENTRY_DELETE_CONFLICT",
    "BTB_HEADER_CONFLICT",
    "/reopen",
    "/header",
    "/entries",
    "/finalize",
    '"DELETE"',
):
    assert token in api, token

for token in (
    "synchronizeServer1BtbPending",
    "TYPE_BTB",
    "STATE_CONFLICT",
    "server1BtbBaseKey",
    "baseStateJson",
    "deleteCollabPendingChange",
):
    assert token in repo, token

assert "refreshServer1BtbSnapshots" in fragment
assert "collab.synchronizeServer1BtbPending" in fragment
assert "bridge.exportLocalChanges()" in fragment
assert fragment.index("bridge.exportLocalChanges()") < fragment.index("collab.synchronizeServer1BtbPending")

for route in (
    '@router.post("/worksites/{worksite_id}/btbs"',
    '@router.patch("/btbs/{btb_id}/header")',
    '@router.post("/btbs/{btb_id}/entries"',
    '@router.patch("/btbs/{btb_id}/entries/{entry_id}")',
    '@router.delete("/btbs/{btb_id}/entries/{entry_id}"',
    '@router.post("/btbs/{btb_id}/finalize")',
    '@router.post("/btbs/{btb_id}/reopen")',
):
    assert route in server, route

print("OK: Android writes granular idempotent Server-1.0 team BTBs with per-entry conflicts")
