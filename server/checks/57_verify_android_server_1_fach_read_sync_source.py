from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FachSyncApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
coordinator = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")\nfragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
strings = (ROOT / "app/src/main/res/values/collab_strings.xml").read_text(encoding="utf-8")

for token in (
    "/api/v1/worksites/",
    "/defects",
    "/acceptances",
    "Authorization",
    "Bearer $token",
    "Result.SessionInvalid",
):
    assert token in api, token

for token in (
    "replaceServer1Snapshots",
    'type == "DEFECT" || type == "ACCEPTANCE"',
    "markCollabDeleted",
    "clientEntityKey = item.clientEntityKey",
):
    assert token in repo, token

for token in (
    'capabilities.supports("defects_v1")',
    'capabilities.supports("acceptances_v1")',
    "fachApi.loadDefects",
    "fachApi.loadAcceptances",
    "bridge.applyServerStateToLocal()",
):
    assert token in coordinator, token

assert "Server1AndroidSyncCoordinator" in fragment
assert "SyncUiResult.ContextOnly" in fragment

assert "Mängel/Abnahmen" in strings
assert "Server 1.0" in strings

print("OK: Android Server-1.0 read-only defects/acceptances sync source contract")
