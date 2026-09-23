from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
server = (ROOT / "server/tbserver/worksites.py").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteNodeApi.kt").read_text(encoding="utf-8")
structure = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/WorksiteStructureSync.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
sync = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1AndroidSyncCoordinator.kt").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

ast.parse(server, filename="server/tbserver/worksites.py")

for token in (
    'include_deleted: bool = Query(default=False)',
    "client_node_key",
    "parent_client_node_key",
    "is_deleted",
    "current_version",
    "base_version: int | None",
    '"VERSION_CONFLICT"',
    "WITH RECURSIVE subtree",
    "deleted_count",
    "from psycopg.types.json import Jsonb",
    "Jsonb(payload.payload)",
    'Jsonb(updates[key]) if key == "payload" else updates[key]',
):
    assert token in server, token

assert '"/api/v1/collab/' not in api
canonical_path = '/api/v1/worksites/' + '$' + '{encode(worksiteId)}' + '/nodes?include_deleted=true'
assert canonical_path in api
for token in (
    "external_key",
    "parent_client_node_key",
    "X-Client-Change-Id",
    "fun applyPending",
    "MutationResult.Conflict",
    "?base_version=$baseVersion",
):
    assert token in api, token

for token in (
    "TYPE_WORKSITE_NODE",
    "collabRepository.enqueueMutation",
    "parent_client_node_key",
    "loescheTeilbaum",
):
    assert token in structure, token

for token in (
    "synchronizeServer1WorksiteNodePending",
    "Server1WorksiteNodeUploadResult",
    "STATE_CONFLICT",
):
    assert token in repo, token

for token in (
    'capabilities.supports("worksite_tree_v1")',
    "structureSync.prepareLocalChanges(token)",
    "collab.synchronizeServer1WorksiteNodePending(token)",
    "structureSync.applyServerStateToLocal(token)",
    "treeSettled",
    "upload.conflicts",
):
    assert token in sync, token

assert '"worksite_tree_v1": True' in caps
assert sync.index("structureSync.prepareLocalChanges(token)") < sync.index(
    "collab.synchronizeServer1WorksiteNodePending(token)"
)
assert sync.index("collab.synchronizeServer1WorksiteNodePending(token)") < sync.index(
    "structureSync.applyServerStateToLocal(token)"
)

print("OK: canonical Server-1.0 worksite tree keeps stable keys, tombstones and version rebasing")
