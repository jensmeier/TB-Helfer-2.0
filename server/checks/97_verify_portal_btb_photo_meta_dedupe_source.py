from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

for token in (
    "decodePhotoMetadata",
    "BTBP1__",
    "getBigInt64",
    "photoSection",
    "FOTODOKUMENTATION",
    "renderPhotoEvents",
    "uniquePhotos",
    "google.com/maps/search/?api=1&query=",
    "GPS: ",
    "dedupeEntries",
    "ensureNoDuplicate",
    "Ein identischer Eintrag ist bereits vorhanden.",
    "const firmEntryId=editingEntry.id",
    "await deleteEntryId(firmEntryId)",
):
    assert token in portal, token

assert "await deleteEntryId(editingEntry.id)" not in portal
assert "PHOTO_TYPES=['ABNAHMEN','EINWEISUNG_UNTERWEISUNG','BESONDERHEITEN','MAENGEL_SCHAEDEN','FOTODOKUMENTATION']" in portal

print("OK: portal decodes BTB photo metadata, links GPS, de-duplicates rows and fixes firm deletion")
