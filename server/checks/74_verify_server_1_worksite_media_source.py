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
    "lead.display_name AS responsible_tb",
    "uploader.display_name AS uploaded_by_name",
    "btb_lead.display_name AS btb_responsible_tb",
):
    assert token in server, token

assert ".full_name" not in server

# The image-only override must stay opt-in: normal file list/metadata keep existing visibility.
assert "allow_responsible_btb_images: bool = False" in server
opt_in = {node.name for node in ast.walk(ast.parse(server))
          if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
          and any(isinstance(child, ast.keyword) and child.arg == "allow_responsible_btb_images"
                  and isinstance(child.value, ast.Constant) and child.value.value is True
                  for child in ast.walk(node))}
assert opt_in == {"download_file", "download_image_rendition"}, opt_in

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
