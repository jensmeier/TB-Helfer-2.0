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
    base = read("server/templates/v1_base.html")
    home = read("server/templates/v1_portal_home.html")
    worksites = read("server/templates/v1_worksites.html")
    worksite = read("server/templates/v1_worksite.html")
    btb = read("server/templates/v1_btb.html")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('@router.get("/portal/v1/worksites")', web),
        ('@router.get("/portal/v1/worksites/{worksite_id}")', web),
        ('@router.get("/portal/v1/btbs/{btb_id}")', web),
        ('Depends(current_portal_session_user)', web),
        ('href="/portal/v1/worksites"', base),
        ("X-Client-Change-Id", base),
        ("crypto.randomUUID", base),
        ('href="/portal/v1/worksites"', home),
        ("/api/v1/worksites", worksites),
        ("company_name", worksites),
        ("renderAdminWorksites", worksites),
        ("refreshCompanyFilter", worksites),
        ("/nodes", worksite),
        ("/members", worksite),
        ("/teams", worksite),
        ("/defects", worksite),
        ("/acceptances", worksite),
        ("/btbs", worksite),
        ("createBtb", worksite),
        ("closeDefect", worksite),
        ("finalizeAcceptance", worksite),
        ("editNode", worksite),
        ("deleteNode", worksite),
        ("editMemberPermissions", worksite),
        ("editDefect", worksite),
        ("editAcceptance", worksite),
        ("/api/v1/btbs/", btb),
        ("/header", btb),
        ("/entries", btb),
        ("/finalize", btb),
        ("/reopen", btb),
        ("Abschluss-PDF öffnen", btb),
        ("BTB löschen", btb),
        ("<summary>Fotos</summary>", btb),
        ("/uploads/", btb),
        ("sha256Hex", btb),
        ("BTB_PHOTO", btb),
        ("detachPhoto", btb),
        ('"photos": photos', read("server/tbserver/btb.py")),
        ('"portal_worksite_fach_ui_v1": True', caps),
        ("37_verify_tb_server_1_portal_fach_ui_source.py", workflow),
        ('"portal_worksite_fach_ui_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Portal-Fachoberflächen-Marker: " + ", ".join(missing)
        )

    combined = "\n".join((base, home, worksites, worksite, btb))
    if "Authorization" in combined:
        raise SystemExit(
            "Portal-Web darf keine Geräte-/Bearer-Tokens im Browser-Quelltext verwenden"
        )
    if "tb_portal_csrf" not in base or "X-CSRF-Token" not in base:
        raise SystemExit("Portal-Web muss den bestehenden CSRF-Vertrag verwenden")

    print("OK: TB-Server 1.0 portal worksite/team/BTB/defect/acceptance UI")


if __name__ == "__main__":
    main()
