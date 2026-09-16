from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
migration = (ROOT / "server/migrations/013_worksite_structure_sync.sql").read_text("utf-8")
part = (ROOT / "server/app_parts/20_part.py").read_text("utf-8")

required_migration = [
    "CREATE TABLE IF NOT EXISTS tb.collab_worksite_nodes",
    "UNIQUE(worksite_id,client_node_key)",
    "'WORKSITE_NODE'",
    "013_worksite_structure_sync",
]
for token in required_migration:
    assert token in migration, f"Schema-13-Vertrag fehlt: {token}"

required_source = [
    'SERVER_VERSION = "0.7.1"',
    'COLLAB_STATUSES["WORKSITE_NODE"] = {"ACTIVE"}',
    '"tb.collab_worksite_nodes","client_node_key"',
    'WORKSITE_STRUCTURE_WRITE_FORBIDDEN',
    'req.area_scope={"all":True}',
    '/api/v1/collab/worksites/{worksite_id}/nodes',
    'WORKSITE_NODE_PARENT_CYCLE',
]
for token in required_source:
    assert token in part, f"Server-Quellvertrag fehlt: {token}"

assert "member_role" in part and '=="LEAD"' in part, "Nur LEAD darf Struktur schreiben"
assert "collab_change_visible" in part, "Baustellenweite Sichtbarkeit fehlt"
print("OK: Schema-13-/API-0.7.1-Quellvertrag für Baustellenstruktur ist vorhanden.")
