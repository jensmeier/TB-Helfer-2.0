from pathlib import Path
import base64
import struct

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

assert "targetSection==='ARBEIT'" in portal
assert "(firm?'firma:'+firm+':arbeit:':'arbeit-frei:')" in portal
assert "await postManagedEntry(targetSection,prefix+index,index,payload)" in portal
assert "await postManagedEntry('NOTIZEN','notizen:0',0,{text:line});" not in portal
assert 'from .btb_photo_meta import canonical_photo_metadata' in files
assert 'row["photo_metadata"] = canonical_photo_metadata(' in files
assert '"photo_metadata": effective_catalog_photo_metadata(row, entity_type == "BTB")' in files
assert '"photo_metadata": effective_btb_photo_metadata(row)' in btb

from tbserver.btb_photo_meta import canonical_photo_metadata, decode_btb_photo_metadata

payload = struct.pack(
    ">qfffffhBBB",
    1780000000000,
    48.1234,
    11.5678,
    510.0,
    76.215,
    4.5,
    3,
    1,
    0,
    2,
)
for value in ("Firma", "Stopfen", "BÜ 76.215", "Gleis"):
    raw = value.encode("utf-8")
    payload += bytes([len(raw)]) + raw
encoded = base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
meta = decode_btb_photo_metadata(f"BTBP1__{encoded}__bild.jpg")
assert meta is not None
assert meta["typ"] == "ABNAHMEN"
assert meta["indikator"] == "ROT"
assert meta["firma"] == "Firma"
assert meta["kurztext"] == "Stopfen"
assert decode_btb_photo_metadata("BTBP1__%%%__broken.jpg") is None
canonical = canonical_photo_metadata(filename=f"BTBP1__{encoded}__bild.jpg")
assert canonical["captured_at"] is not None
assert abs(canonical["gps_lat"] - 48.1234) < 0.001
assert abs(canonical["gps_lon"] - 11.5678) < 0.001

print("OK: portal writes general work as ARBEIT and server exposes canonical BTB photo metadata")
