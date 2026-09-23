from __future__ import annotations

import ast
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlparse


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    if rel.endswith(".py"):
        ast.parse(source, filename=rel)
    return source


def verify_runtime() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.invitation_links import (
        invite_app_uri,
        invite_browser_link,
        invite_qr_svg_data_url,
    )

    token = "abc DEF?ghi&123"
    app_uri = invite_app_uri(token)
    parsed_app = urlparse(app_uri)
    assert parsed_app.scheme == "tbhelfer", app_uri
    assert parsed_app.netloc == "worksite-invite", app_uri
    assert parse_qs(parsed_app.query)["token"] == [token], app_uri

    browser = invite_browser_link(
        "https://portal.example.invalid/portal/v1/invitations/redeem",
        token,
    )
    parsed_browser = urlparse(browser)
    assert parsed_browser.query == "", browser
    assert parse_qs(parsed_browser.fragment)["token"] == [token], browser

    qr = invite_qr_svg_data_url(token)
    assert qr.startswith("data:image/svg+xml;base64,"), qr[:80]


def main() -> None:
    links = read("server/tbserver/invitation_links.py")
    worksites = read("server/tbserver/worksites.py")
    worksite_ui = read("server/templates/v1_worksite.html")
    redeem_ui = read("server/templates/v1_invitation_redeem.html")
    requirements = read("server/requirements.txt")
    caps = read("server/tbserver/capabilities.py")
    main_source = read("server/tbserver/main.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('INVITE_SCHEME = "tbhelfer"', links),
        ('INVITE_HOST = "worksite-invite"', links),
        ("SvgPathImage", links),
        ("invite_qr_svg_data_url", links),
        ("settings.portal_public_base_url", worksites),
        ('f"{portal_base}/portal/v1/invitations/redeem"', worksites),
        ("request.url_for(\"portal_invitation_redeem_page\")", worksites),
        ('"invite_uri": None', worksites),
        ('"invite_link": None', worksites),
        ('"invite_qr_svg_data_url": None', worksites),
        ("response_payload=receipt_response", worksites),
        ('response.headers["Cache-Control"] = "no-store"', worksites),
        ("invite_qr_svg_data_url(raw_token)", worksites),
        ("Einladung – Code/Link/QR nur jetzt verfügbar", worksite_ui),
        ("copyInviteLink", worksite_ui),
        ("invite_qr_svg_data_url", worksite_ui),
        ("location.hash", redeem_ui),
        ("history.replaceState", redeem_ui),
        ('meta name="referrer" content="no-referrer"', redeem_ui),
        ("qrcode==8.2", requirements),
        ('"worksite_invitation_qr_v1": True', caps),
        ('"worksite_open_invitations_v1": True', caps),
        ('invited_user_id: UUID | None = None', worksites),
        ('LEFT JOIN tb.users u ON u.id = i.invited_user_id', worksites),
        ('@legacy_router.post("/worksites/{worksite_id}/invitations")', worksites),
        ('created = create_worksite_invitation(', worksites),
        ('"deep_link": created.get("invite_uri")', worksites),
        ('if invitation["invited_user_id"] is not None and invitation["invited_user_id"] != user["user_id"]', worksites),
        ('if invitation["invited_user_id"] is None:', worksites),
        ('raise HTTPException(409, "ALREADY_WORKSITE_MEMBER")', worksites),
        ('app.include_router(legacy_worksites_router)', main_source),
        ("47_verify_tb_server_1_invitation_qr_source.py", workflow),
        ('"worksite_invitation_qr_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende QR-/Deep-Link-Einladungsmarker: " + ", ".join(missing)
        )

    legacy_block = worksites[
        worksites.index("def create_legacy_worksite_invitation"):
        worksites.index("def revoke_worksite_invitation")
    ]
    if 'INSERT INTO tb.worksite_invitations' in legacy_block:
        raise SystemExit(
            "Legacy-Android-Adapter darf keine zweite Einladungs-Fachlogik/Datenbanklogik besitzen"
        )
    if "create_worksite_invitation(" not in legacy_block:
        raise SystemExit(
            "Legacy-Android-Adapter muss den kanonischen Server-1.0-Einladungsweg verwenden"
        )

    create_block = worksites[
        worksites.index("def create_worksite_invitation"):
        worksites.index("def revoke_worksite_invitation")
    ]
    if "response_payload=response," in create_block:
        raise SystemExit(
            "Rohcode/QR/Deep-Link darf nicht im Mutation Receipt gespeichert werden"
        )

    verify_runtime()
    print("OK: TB-Server 1.0 invitation Code/Link/QR contract")


if __name__ == "__main__":
    main()
