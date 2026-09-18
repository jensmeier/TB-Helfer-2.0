from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
server = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteMediaApi.kt").read_text(encoding="utf-8")
archive = (ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/GesamtMedienArchiv.kt").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

ast.parse(server, filename="server/tbserver/files.py")

for token in (
    '@router.get("/worksites/{worksite_id}/media")',
    "_responsible_tb_btb_image_visible",
    'context.get("worksite_role") == "RESPONSIBLE_TB"',
    "allow_responsible_btb_images=True",
    "l.entity_type IN ('BTB','DEFECT','ACCEPTANCE')",
    "parent_payload = {}",
    "_legacy_defect_status",
    "acceptance_result",
):
    assert token in server, token

# The image-only override must stay opt-in: normal file list/metadata keep existing visibility.
assert "allow_responsible_btb_images: bool = False" in server
assert server.count("allow_responsible_btb_images=True") == 1

for token in (
    "/api/v1/worksites/",
    "/media",
    "/api/v1/files/",
    "/content",
    "Transport.SERVER1",
    "Transport.LEGACY",
    "/api/v1/collab/worksites/",
    "/api/v1/collab/attachments/",
):
    assert token in api, token

assert "Server1AndroidSyncCoordinator" in archive
assert "Result.Unsupported -> Unit" in archive
assert "collab.synchronizeAll(token)" in archive
assert archive.index("Server1AndroidSyncCoordinator") < archive.index(
    "collab.synchronizeAll(token)"
)

assert '"worksite_media_v1": True' in caps

print("OK: Server-1.0 media catalog preserves cross-team image-only responsible-TB visibility")
