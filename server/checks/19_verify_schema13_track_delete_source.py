from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

migration = (ROOT / "server/migrations/013_worksite_structure_sync.sql").read_text(encoding="utf-8")
server = (ROOT / "server/app_parts/21_part.py").read_text(encoding="utf-8")
track_api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteTrackDataApi.kt").read_text(encoding="utf-8")
delete_api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteDeletionApi.kt").read_text(encoding="utf-8")
track_sync = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/WorksiteTrackDataSync.kt").read_text(encoding="utf-8")
local_delete = (ROOT / "app/src/main/java/de/tbhelfer/ui/baustelle/WorksiteLocalDeleteService.kt").read_text(encoding="utf-8")
worksite_ui = (ROOT / "app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenFragment.kt").read_text(encoding="utf-8")
schema_worker = (ROOT / "app/src/main/java/de/tbhelfer/server/Schema13Worker.kt").read_text(encoding="utf-8")
instant = (ROOT / "app/src/main/java/de/tbhelfer/server/SofortSync.kt").read_text(encoding="utf-8")

required_migration = [
    "CREATE TABLE IF NOT EXISTS tb.mobile_track_data",
    "source_worksite_id uuid REFERENCES tb.worksites(id) ON DELETE SET NULL",
    "dataset_kind text NOT NULL DEFAULT 'AXIS'",
    "CREATE TABLE IF NOT EXISTS tb.worksite_delete_tombstones",
    "ADD COLUMN IF NOT EXISTS deleted_at timestamptz",
]
for needle in required_migration:
    assert needle in migration, needle

required_server = [
    'SERVER_VERSION = "0.7.2"',
    '@app.get("/api/v1/collab/worksites/{worksite_id}/track-data")',
    '@app.put("/api/v1/collab/worksites/{worksite_id}/track-data")',
    'TRACK_DATA_WRITE_FORBIDDEN',
    '@app.delete("/api/v1/collab/worksites/{worksite_id}")',
    'WORKSITE_DELETE_LEAD_REQUIRED',
    '@app.get("/api/v1/collab/worksite-deletions")',
    'w.deleted_at IS NULL',
]
for needle in required_server:
    assert needle in server, needle

# Der neue Pfad synchronisiert nur bereits geparste/berechnete Datensaetze. Server 1.0 nutzt
# den kanonischen track_data_v1-Vertrag; /collab bleibt nur expliziter 0.7.x-Fallback.
assert "/api/v1/worksites/" in track_api and "/track-data" in track_api
assert "/api/v1/track-data/" in track_api
assert '"X-Client-Change-Id"' in track_api
assert "base_version" in track_api
assert "legacyReplace" in track_api
assert "/api/v1/collab/worksites/" in track_api

# Rohdatei-Uploads oder NAS-Dateischreibpfade bleiben verboten.
for forbidden in ["Multipart", "readBytes()", "TRA_UPLOAD", "GRA_UPLOAD", "KF_UPLOAD", "KOR_UPLOAD"]:
    assert forbidden not in track_api + track_sync, forbidden

for needle in [
    'datasetKind = "AXIS"',
    'datasetKind = "STRECKENPUNKTE"',
    'trassenDao.alleAchsen()',
    'trassenDao.alleSamplepunkte()',
    'streckenpunktDao.ladeAlleOnce()',
    'streckenpunktDao.loeschenFuerBaustelle(baustelleId)',
]:
    assert needle in track_sync, needle

for needle in [
    'requestMethod = "DELETE"',
    '"/api/v1/collab/worksite-deletions"',
]:
    assert needle in delete_api, needle

for needle in [
    'BautagebuchSpeicher.loescheAlleBtbsFuerBaustelle',
    'ServiceLocator.baustellenBaumRepository.loescheTeilbaum(root.id)',
    'ServiceLocator.worksiteBindingRepository.unbindLocal(root.id)',
]:
    assert needle in local_delete, needle

# Gebundene Baustellen: Server zuerst, lokale Bereinigung erst nach Success.
server_call = 'WorksiteDeletionApi().delete(token, binding.worksiteServerId)'
local_call = 'WorksiteLocalDeleteService.delete(appContext, haupt.id)'
assert server_call in worksite_ui
assert local_call in worksite_ui
assert worksite_ui.index(server_call) < worksite_ui.rindex(local_call)
assert 'Server unterstützt gemeinsames Baustellen-Löschen noch nicht' in worksite_ui

for needle in [
    'ServiceLocator.worksiteTrackDataSync.synchronize(token)',
    'WorksiteDeletionApi().list(token)',
    'WorksiteLocalDeleteService.delete(applicationContext, localRoot)',
]:
    assert needle in schema_worker, needle

assert 'Schema13Worker.enqueueNow(appContext)' in instant

print("OK: Schema 13 mobile track snapshots and shared worksite deletion source contract verified")
