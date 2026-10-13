from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BTB = (ROOT / "templates" / "v1_btb.html").read_text(encoding="utf-8")
WORKSITE = (ROOT / "templates" / "v1_worksite.html").read_text(encoding="utf-8")
SYNC = (ROOT / "tbserver" / "sync.py").read_text(encoding="utf-8")

assert '@router.get("/events")' in SYNC
assert "_CHANGE_HUB" in SYNC

for name, source in (("BTB", BTB), ("WORKSITE", WORKSITE)):
    assert "new EventSource(" in source, f"{name}: EventSource fehlt"
    assert "/api/v1/sync/events?worksite_id=" in source, f"{name}: Worksite-SSE fehlt"
    assert "addEventListener('change'" in source, f"{name}: change-Listener fehlt"

assert "'FILE'" in BTB and "scheduleBtbLiveReload" in BTB
assert "await loadBtb()" in BTB
assert "loadWorksiteMedia()" in WORKSITE
assert "loadTeams()" in WORKSITE
assert "loadDefects()" in WORKSITE
assert "loadAcceptances()" in WORKSITE

print("PORTAL_LIVE_REFRESH_SOURCE_OK")
