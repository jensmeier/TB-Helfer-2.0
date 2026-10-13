from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")
docs = (ROOT / "server/tbserver/btb_documents.py").read_text(encoding="utf-8")
meta = (ROOT / "server/tbserver/btb_photo_meta.py").read_text(encoding="utf-8")
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")
migration = (ROOT / "server/v1_migrations/016_btb_photo_link_metadata.sql").read_text(encoding="utf-8")

for token in (
    "section: str | None",
    "BTB_CONTENT_SECTIONS",
    "ENTRY_SECTION_MOVE_NOT_ALLOWED",
    "worksiteNodeKey",
    "WORKSITE_NODE_INVALID",
    '@router.patch("/btbs/{btb_id}/photos/{file_id}")',
    'receipt_type="BTB_PHOTO_UPDATE"',
    "link_version",
):
    assert token in btb, token

for token in (
    "l.link_metadata",
    "l.version AS link_version",
    "effective_btb_photo_metadata",
    "merge_btb_photo_metadata",
):
    assert token in docs, token

for token in (
    '"firmaId"',
    '"worksiteNodeKey"',
    "BTB_PHOTO_OVERRIDE_KEYS",
    "normalize_btb_photo_override",
    "merge_btb_photo_metadata",
):
    assert token in meta, token

for token in (
    'id="photoNode"',
    'id="photoModal"',
    "function worksiteNodeOptions(",
    "function contentEditorFields(",
    "section:targetSection",
    "function openPhotoEditor(id)",
    "base_version:Number(editingPhoto.link_version||1)",
    "refreshPhotoUploadSelectors",
    "'/nodes'",
    "new EventSource(url)",
):
    assert token in portal, token

for token in (
    "ADD COLUMN link_metadata jsonb",
    "ADD COLUMN version integer",
    "ADD COLUMN updated_by uuid",
    "file_links_link_metadata_object_chk",
):
    assert token in migration, token

# Fachliche Schutzregeln:
assert "lat" not in meta.split("BTB_PHOTO_OVERRIDE_KEYS", 1)[1].split("})", 1)[0]
assert "lon" not in meta.split("BTB_PHOTO_OVERRIDE_KEYS", 1)[1].split("})", 1)[0]
assert "epoch" not in meta.split("BTB_PHOTO_OVERRIDE_KEYS", 1)[1].split("})", 1)[0]
assert "DELETE FROM tb.file_links" in btb  # bestehendes bewusstes Detach bleibt separat
assert "UPDATE tb.file_links" in btb       # Umhängen ändert die Verknüpfungs-Metadaten

print("OK: BTB-EDIT-001 structured entry/photo reassignment source contract is present")
