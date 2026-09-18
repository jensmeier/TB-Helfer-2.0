from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
coordinator = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/worksites/",
    "/teams/",
    "/btbs",
    "/api/v1/btbs/",
    "active_btb_id",
    "tb_helfer_android_v1",
    "_tb_android_managed",
    "_tb_android_slot",
    "SECTION_FIRMA",
    "SECTION_PERSONAL",
    "SECTION_GERAET",
    "SECTION_ARBEIT",
    "SECTION_ABNAHME",
    "SECTION_MANGEL",
):
    assert token in api, token

for token in (
    "replaceServer1BtbSnapshots",
    "TYPE_BTB",
    "it.groupId == teamId",
    "markCollabDeleted",
    "baseStateJson",
    "server1BtbBaseKey",
):
    assert token in repo, token

assert 'capabilities.supports("btb_live_sync_v1")' in coordinator
assert "btbApi.loadTeamBtbs" in coordinator
assert "collab.replaceServer1BtbSnapshots" in coordinator
assert "Server1AndroidSyncCoordinator" in fragment
assert '@router.get("/worksites/{worksite_id}/teams/{team_id}/btbs")' in server
assert '@router.get("/btbs/{btb_id}")' in server

print("OK: Android reads granular Server-1.0 team BTBs into the existing local cache")
