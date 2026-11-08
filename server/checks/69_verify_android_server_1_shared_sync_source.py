from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
coordinator = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
worker = (ROOT / "app/src/main/java/de/tbhelfer/server/SyncWorker.kt").read_text(encoding="utf-8")

for token in (
    "class Server1AndroidSyncCoordinator",
    "WorksiteContextSync(appContext).prepare(token, roots)",
    "capabilitiesApi.load",
    'capabilities.supports("btb_live_sync_v1")',
    'capabilities.supports("defects_v1")',
    'capabilities.supports("acceptances_v1")',
    'capabilities.supports("files_v1")',
    'capabilities.supports("chunk_upload_v1")',
    'capabilities.supports("btb_finalize_with_pdf_v1")',
    "reconcileBtbDeletes",
    "synchronizeServer1FachPending",
    "synchronizeServer1BtbPending",
    "BtbTeamMediaSync",
):
    assert token in coordinator, token

assert "supportsIncrementalSync" not in coordinator
assert "supportsCollabSync" not in coordinator
assert "parseVersion" not in coordinator
assert "val btbEnabled =" in coordinator
assert "val fachEnabled =" in coordinator
assert "filesEnabled && (fachEnabled || btbEnabled)" in coordinator
assert "uploadsEnabled && fachEnabled" in coordinator
assert "uploadsEnabled && btbEnabled" in coordinator

assert "Server1AndroidSyncCoordinator" in fragment
assert "teamApi.loadWorksiteContexts(token)" not in fragment
assert "refreshServer1BtbSnapshots" not in fragment
assert "refreshServer1FachSnapshots" not in fragment
assert "reconcileServer1BtbDeletes" not in fragment

assert "Server1AndroidSyncCoordinator(applicationContext).synchronize(token)" in worker
assert worker.index("Server1AndroidSyncCoordinator(applicationContext).synchronize(token)") < worker.index(
    "if (!supportsIncrementalSync(serverVersion))"
)
assert "if (server1Collab)" in worker
assert worker.index("if (server1Collab)") < worker.index("if (!supportsCollabSync(serverVersion))")

print("OK: UI and worker share capability-driven Server-1.0 sync before legacy version gates")
