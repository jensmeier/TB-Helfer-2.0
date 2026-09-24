from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

for token in (
    'id="addPersonalFirmButton"',
    'id="addDeviceFirmButton"',
    "openEntryCreator('FIRMA','PERSONAL')",
    "openEntryCreator('FIRMA','MASCHINEN')",
    "visibleInPersonal:visibleInPersonal",
    "visibleInMaschinen:visibleInMaschinen",
    "function saveWeatherEditor()",
    "Wetter gespeichert.",
    "const grouped=ALWAYS_OPEN_SECTIONS.map",
    "btb-photo-group-title",
    "decodePhotoMetadata",
    "BTBP1__",
    "GPS: ",
    "dedupeEntries",
    "Ein identischer Eintrag ist bereits vorhanden.",
    "const firmEntryId=editingEntry.id",
):
    assert token in portal, token

assert 'id="addFirmButton"' not in portal
assert "PHOTO_TYPES=['ABNAHMEN','EINWEISUNG_UNTERWEISUNG','BESONDERHEITEN','MAENGEL_SCHAEDEN','FOTODOKUMENTATION']" in portal

print("OK: portal separates personnel/machine firms, edits weather and groups photos by BTB points 4-8")
