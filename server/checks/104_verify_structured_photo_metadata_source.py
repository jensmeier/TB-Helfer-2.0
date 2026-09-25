from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
migration = (ROOT / "server/v1_migrations/015_file_photo_metadata.sql").read_text(encoding="utf-8")
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")
documents = (ROOT / "server/tbserver/btb_documents.py").read_text(encoding="utf-8")
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

assert "ADD COLUMN photo_metadata jsonb" in migration
assert "files_photo_metadata_object_chk" in migration
assert "photo_metadata: dict[str, Any] | None = None" in files
assert "Jsonb(canonical_meta[\"photo_metadata\"] or {})" in files
assert "f.photo_metadata" in files
assert "f.photo_metadata" in documents
assert '"photo_metadata": canonical_photo_metadata(' in documents
assert "photo_metadata=row.get(\"photo_metadata\")" in btb
assert 'id="photoType"' in portal
assert "photo_metadata:photoMetadata" in portal

from tbserver.btb_photo_meta import canonical_photo_metadata, normalize_photo_metadata

normalized = normalize_photo_metadata(
    {
        "typ": "abnahmen",
        "kurztext": "  Abnahme Gleis 2  ",
        "firma": " Firma ",
        "km": "76.215",
        "lat": "48.123",
        "lon": "11.456",
        "ignored": "not persisted",
    }
)
assert normalized["typ"] == "ABNAHMEN"
assert normalized["kurztext"] == "Abnahme Gleis 2"
assert normalized["firma"] == "Firma"
assert normalized["km"] == 76.215
assert "ignored" not in normalized

canonical = canonical_photo_metadata(
    filename="IMG_1234.jpg",
    photo_metadata={
        "typ": "BESONDERHEITEN",
        "kurztext": "Kabel im Gras",
        "km": 76.3,
    },
)
assert canonical["photo_metadata"]["typ"] == "BESONDERHEITEN"
assert canonical["photo_metadata"]["kurztext"] == "Kabel im Gras"
assert canonical["photo_metadata"]["km"] == 76.3

print("OK: structured photo metadata is stored and portal can classify BTB photos")
