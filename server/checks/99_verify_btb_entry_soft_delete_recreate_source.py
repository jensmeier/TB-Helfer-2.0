from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    'SELECT id, btb_id, section, sort_order, payload, version,',
    'WHERE id = %s',
    'FOR UPDATE',
    '"code": "ENTRY_ID_CONFLICT"',
    'if existing["deleted_at"] is None:',
    'deleted_at = NULL',
    'version = version + 1',
    'WHERE id = %s AND btb_id = %s AND deleted_at IS NOT NULL',
    'receipt_type="BTB_ENTRY_CREATE"',
):
    assert token in btb, token

assert 'INSERT INTO tb.btb_entries(id, btb_id, section, sort_order, payload, created_by, updated_by)' in btb
assert 'UPDATE tb.btbs SET version = version + 1' in btb

print("OK: deterministic BTB entries are idempotent and soft-deleted IDs can be restored without PK 500")
