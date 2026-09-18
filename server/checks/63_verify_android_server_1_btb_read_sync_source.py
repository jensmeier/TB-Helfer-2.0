from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/worksites/",
    "/teams/",
    "/btbs",
    "/api/v1/btbs/",
    "TB_HELFER_ANDROID_V1",
    "tb_helfer_btb_id",
    'status = if (btb.optString("status").uppercase() == "FINALIZED") "FERTIG" else "ENTWURF"',
):
    assert token in api, token

for token in (
    "replaceServer1BtbSnapshots",
    "TYPE_BTB",
    "it.groupId == teamId",
    "markCollabDeleted",
):
    assert token in repo, token

assert 'capabilities?.supports("btb_live_sync_v1")' in fragment
assert "server1BtbApi.loadTeamBtbs" in fragment
assert "collab.replaceServer1BtbSnapshots" in fragment

assert '@router.get("/worksites/{worksite_id}/teams/{team_id}/btbs")' in server
assert '@router.get("/btbs/{btb_id}")' in server

print("OK: Android Server-1.0 team BTB read/snapshot source contract")
