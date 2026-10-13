from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/acceptances.py").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

for token in (
    "async function deleteAcceptance",
    "?base_version=",
    "{method:'DELETE'}",
    "can('acceptance.edit')",
    "can('acceptance.finalize')",
    "Zugeordnete Dateien und Fotos werden nicht physisch vernichtet",
):
    assert token in portal, token

assert '@router.delete("/acceptances/{acceptance_id}", status_code=204)' in server
assert 'require_permission(context, "acceptance.edit")' in server
assert 'require_permission(context, "acceptance.finalize")' in server
assert '"acceptance_delete_v1": True' in caps

print("OK: Portal acceptance delete uses the same versioned Server-1.0 tombstone lifecycle")
