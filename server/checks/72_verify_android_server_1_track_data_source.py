from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteTrackDataApi.kt").read_text(encoding="utf-8")
sync = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/WorksiteTrackDataSync.kt").read_text(encoding="utf-8")
coordinator = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
schema_worker = (ROOT / "app/src/main/java/de/tbhelfer/server/Schema13Worker.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/track_data.py").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/worksites/",
    "/api/v1/track-data/",
    "loadCanonical",
    "reconcileCanonical",
    "X-Client-Change-Id",
    "base_version",
    "MutationResult.Conflict",
    "mutationId",
    "jsonEquivalent",
):
    assert token in api, token

assert "/api/v1/collab/worksites/" in api
assert "legacyLoad" in api
assert "legacyReplace" not in api
assert "item.currentVersion != server.currentVersion" in api

for token in (
    "buildLocalDatasets",
    "applyServerDatasets",
    'datasetKind = "AXIS"',
    'datasetKind = "STRECKENPUNKTE"',
):
    assert token in sync, token

for route in (
    '@router.get("/worksites/{worksite_id}/track-data")',
    '@router.post("/worksites/{worksite_id}/track-data", status_code=201)',
    '@router.patch("/track-data/{dataset_id}")',
    '@router.delete("/track-data/{dataset_id}", status_code=204)',
):
    assert route in server, route

assert '"track_data_v1": True' in caps

assert 'capabilities.supports("track_data_v1")' in coordinator
assert "trackDataSync.synchronize(token)" in coordinator
assert 'caps.snapshot.supports("track_data_v1")' in schema_worker
assert "if (!server1TrackData)" in schema_worker

for forbidden in ("Multipart", "TRA_UPLOAD", "GRA_UPLOAD", "KF_UPLOAD", "KOR_UPLOAD"):
    assert forbidden not in api + sync, forbidden

print("OK: Android uses canonical Server-1.0 track-data CRUD with explicit 0.7.x fallback")
