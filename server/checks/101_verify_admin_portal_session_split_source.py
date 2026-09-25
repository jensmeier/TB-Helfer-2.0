from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
admin_session = (ROOT / "server/tbserver/admin_session.py").read_text(encoding="utf-8")
portal = (ROOT / "server/tbserver/portal.py").read_text(encoding="utf-8")
admin = (ROOT / "server/tbserver/admin.py").read_text(encoding="utf-8")
admin_web = (ROOT / "server/tbserver/admin_web.py").read_text(encoding="utf-8")
base = (ROOT / "server/templates/v1_base.html").read_text(encoding="utf-8")
portal_login = (ROOT / "server/templates/v1_portal_login.html").read_text(encoding="utf-8")
admin_login = (ROOT / "server/templates/v1_admin_login.html").read_text(encoding="utf-8")
main = (ROOT / "server/tbserver/main.py").read_text(encoding="utf-8")

for token in (
    'ADMIN_SESSION_COOKIE = "tb_admin_session"',
    'ADMIN_CSRF_COOKIE = "tb_admin_csrf"',
    '@router.post("/login")',
    'current_admin_session_user',
    'require_admin_session_csrf',
):
    assert token in admin_session, token

assert 'ADMIN_USE_ADMIN_LOGIN' in portal
assert 'X-TB-Session-Scope' in portal
assert '"tb_admin_csrf" if user.get("auth_kind") == "ADMIN_PORTAL"' in portal
assert 'Depends(current_admin_session_user)' in admin
assert 'Depends(current_admin_session_user)' in admin_web
assert '@router.get("/admin/v1/login")' in admin_web
for token in (
    '@router.get("/admin/v1/worksites")',
    '@router.get("/admin/v1/worksites/{worksite_id}")',
    '@router.get("/admin/v1/btbs/{btb_id}")',
    '"page_prefix": "/admin/v1"',
):
    assert token in admin_web, token
assert "const tbAdminScope=location.pathname.startsWith('/admin/v1');" in base
assert "tbAdminScope?'tb_admin_csrf':'tb_portal_csrf'" in base
assert "function tbScopedUrl(url)" in base
assert 'request.query_params.get("_tb_session")' in portal
assert "/api/v1/admin/session/logout" in base
assert "/admin/v1/login" in portal_login
worksites = (ROOT / "server/templates/v1_worksites.html").read_text(encoding="utf-8")
worksite = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")
btb = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")
dashboard = (ROOT / "server/templates/v1_admin_dashboard.html").read_text(encoding="utf-8")
assert "pagePrefix" in worksites
assert "pagePrefix" in worksite
assert "pagePrefix" in btb
assert "tbScopedUrl(p.content_url)" in btb
assert "tbScopedUrl('/api/v1/files/'" in worksite
assert 'href="/admin/v1/worksites"' in dashboard
assert "/api/v1/admin/session/login" in admin_login
assert "admin_session_router" in main

print("OK: admin and user portal use independent browser sessions and CSRF cookies")
