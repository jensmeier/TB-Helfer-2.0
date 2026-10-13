from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
server = (ROOT / "server/tbserver/acceptances.py").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")
store = (ROOT / "app/src/main/java/de/tbhelfer/ui/abnahme/AbnahmeDeleteIntentStore.kt").read_text(encoding="utf-8")
local = (ROOT / "app/src/main/java/de/tbhelfer/ui/abnahme/AbnahmeSpeicher.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FachSyncApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
sync = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")

ast.parse(server, filename="server/tbserver/acceptances.py")

for token in (
    '@router.delete("/acceptances/{acceptance_id}", status_code=204)',
    'receipt_type="ACCEPTANCE_DELETE"',
    'status = \'DELETED\'',
    "deletion_tombstones",
    'operation="DELETE"',
    'require_permission(context, "acceptance.edit")',
    'require_permission(context, "acceptance.finalize")',
):
    assert token in server, token

assert '"acceptance_delete_v1": True' in caps

for token in (
    "AbnahmeDeleteIntentStore.merken",
    "prefs(context).edit().remove(abnahme.id)",
):
    assert token in local, token
assert local.index("AbnahmeDeleteIntentStore.merken") < local.index(
    "prefs(context).edit().remove(abnahme.id)"
)

for token in (
    "queueServer1AcceptanceDeletes",
    "AbnahmeDeleteIntentStore.fuerBaustelle",
    "OP_DELETE",
    "serverEntity.entityVersion",
):
    assert token in bridge, token

for token in (
    "MutationResult.Deleted",
    "deleteAcceptance(token, change)",
    "/api/v1/acceptances/",
    'openConnection(path, "DELETE")',
    "HTTP_NO_CONTENT",
):
    assert token in api, token

assert "is Server1FachSyncApi.MutationResult.Deleted" in repo
assert "AbnahmeDeleteIntentStore.bestaetigen" in repo
assert 'capabilities.supports("acceptance_delete_v1")' in sync
assert "bridge.queueServer1AcceptanceDeletes()" in sync

print("OK: explicit offline acceptance delete intents reach canonical Server-1.0 tombstones")
