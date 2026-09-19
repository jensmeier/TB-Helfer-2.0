from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    if rel.endswith(".py"):
        ast.parse(source, filename=rel)
    return source


def main() -> None:
    web = read("server/tbserver/admin_web.py")
    company = read("server/tbserver/company.py")
    portal = read("server/tbserver/portal.py")
    main_source = read("server/tbserver/main.py")
    capabilities = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    dockerfile = read("server/Dockerfile.v1")
    base = read("server/templates/v1_base.html")
    login = read("server/templates/v1_portal_login.html")
    home = read("server/templates/v1_portal_home.html")
    companies = read("server/templates/v1_admin_companies.html")
    branding = read("server/templates/v1_company_branding.html")

    assertions = [
        ('@router.get("/portal/v1/login")', web),
        ('@router.get("/portal/v1")', web),
        ('@router.get("/admin/v1")', web),
        ('@router.get("/admin/v1/companies")', web),
        ('@router.get("/admin/v1/companies/{company_id}/branding")', web),
        ('Depends(current_portal_session_user)', web),
        ('Depends(require_web_admin)', web),
        ('def current_portal_session_user', portal),
        ('Jinja2Templates(', web),
        ('@router.get("/admin/companies/{company_id}/logo")', company),
        ('@router.put("/admin/companies/{company_id}/pdf-logo-layout")', company),
        ('Depends(require_admin_csrf)', company),
        ('admin_web_router', main_source),
        ('"company_branding_admin_ui_v1": True', capabilities),
        ('36_verify_tb_server_1_admin_web_source.py', workflow),
        ('"company_branding_admin_ui_v1"', workflow),
        ("COPY templates ./templates", dockerfile),
        ("tb_portal_csrf", base),
        ("X-CSRF-Token", base),
        ("/api/v1/portal/logout", base),
        ("/api/v1/portal/login", login),
        ("/admin/v1/companies", home),
        ("/api/v1/admin/companies", companies),
        ("Profil / Logo / PDF", companies),
        ("/pdf-logo-layout", branding),
        ("/logo", branding),
        ("Vorschau Firmenkopf", branding),
        ("contactsInImage", branding),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Server-1.0-Admin-Web-Marker: " + ", ".join(missing)
        )

    if "password" in base.lower():
        raise SystemExit("Basislayout darf kein Passwort/Geheimnis rendern")
    if "Authorization" in branding:
        raise SystemExit(
            "Admin-Web darf keine Geräte-/Bearer-Tokens in Browser-JavaScript einbauen"
        )

    print("OK: TB-Server 1.0 portal/admin company-branding web surface")


if __name__ == "__main__":
    main()
