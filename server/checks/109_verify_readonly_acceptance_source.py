from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
tool = (ROOT / "server/tools/server1_readonly_acceptance.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/user/profile",
    "/api/v1/user/directory",
    "/api/v1/catalog",
    "/api/v1/catalog/history?limit=1",
    "/api/v1/admin/system/status",
    "/admin/v1/routes",
    "/admin/v1/stations",
    "tb_portal_session",
    "tb_admin_session",
    "admin survives portal logout",
):
    assert token in tool, token

for forbidden in (
    '/mutations',
    'method="PUT"',
    'method="DELETE"',
    '/backups"',
    '/warnings/acknowledge',
):
    assert forbidden not in tool, forbidden

print("OK: read-only staging acceptance covers portal/admin/profile/catalog/system without fachliche mutation")
