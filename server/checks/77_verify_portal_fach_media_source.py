from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt").read_text(encoding="utf-8")

for token in (
    "/api/v1/worksites/'+worksiteId+'/media",
    "renderFachMedia",
    "uploadFachPhoto",
    "sha256Hex",
    "target_kind:'PHOTO'",
    "/uploads/",
    "entity_type:type",
    "DEFECT_PHOTO",
    "ACCEPTANCE_PHOTO",
    "DEFECT__MANGEL__",
    "ACCEPTANCE_FOTO__SONSTIGES__",
    "tbScopedUrl(file.renditions.thumb)",
):
    assert token in portal, token

for token in (
    '@router.get("/worksites/{worksite_id}/media")',
    '"entity_type": entity_type',
    '"parent_entity_id": str(entity_id)',
    '"attachment_id": str(row["id"])',
    '_ALLOWED_LINK_TYPES = {"WORKSITE", "BTB", "DEFECT", "ACCEPTANCE"}',
):
    assert token in files, token

import re
assert re.search(r'semanticFilename\(\s*"DEFECT"', bridge)
assert re.search(r'semanticFilename\(\s*"ACCEPTANCE_FOTO"', bridge)
assert "parseDefectFotoArt" in bridge
assert "parseAcceptanceBildArt" in bridge

print("OK: Portal defect/acceptance photos reuse canonical media catalog and resumable upload contract")
