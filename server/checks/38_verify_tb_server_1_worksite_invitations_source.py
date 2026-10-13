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
    worksites = read("server/tbserver/worksites.py")
    web = read("server/tbserver/admin_web.py")
    worksite_ui = read("server/templates/v1_worksite.html")
    redeem_ui = read("server/templates/v1_invitation_redeem.html")
    home = read("server/templates/v1_portal_home.html")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("class WorksiteInvitationCreate", worksites),
        ("class WorksiteInvitationRedeem", worksites),
        ('@router.get("/{worksite_id}/invite-candidates")', worksites),
        ('@router.get("/{worksite_id}/invitations")', worksites),
        ('@router.post("/{worksite_id}/invitations", status_code=201)', worksites),
        ('@router.post("/{worksite_id}/invitations/{invitation_id}/revoke")', worksites),
        ('@router.post("/invitations/redeem")', worksites),
        ('hash_token(raw_token)', worksites),
        ('random_token(32)', worksites),
        ('"HELPER_INVITER_TEAM_REQUIRED"', worksites),
        ('"WORKSITE_INVITATION_WRONG_USER"', worksites),
        ('"WORKSITE_INVITATION_COMPANY_MISMATCH"', worksites),
        ('"WORKSITE_INVITATION_TEAM_NO_LONGER_AVAILABLE"', worksites),
        ('"invite_token": None', worksites),
        ('"token_replay_unavailable": True', worksites),
        ('response_payload=receipt_response', worksites),
        ('action="WORKSITE_INVITATION_CREATE"', worksites),
        ('action="WORKSITE_INVITATION_REDEEM"', worksites),
        ('@router.get("/portal/v1/invitations/redeem")', web),
        ("/invite-candidates", worksite_ui),
        ("/invitations", worksite_ui),
        ("Einladung – Code/Link/QR nur jetzt verfügbar", worksite_ui),
        ("copyInviteToken()", worksite_ui),
        ("copyInviteLink()", worksite_ui),
        ("/api/v1/worksites/invitations/redeem", redeem_ui),
        ('href="/portal/v1/invitations/redeem"', home),
        ('"worksite_invitations_v1": True', caps),
        ("38_verify_tb_server_1_worksite_invitations_source.py", workflow),
        ('"worksite_invitations_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Baustelleneinladungs-Marker: " + ", ".join(missing)
        )

    # The raw one-time token may be returned once to the creator, but must not be
    # persisted in mutation receipts/audit payloads.
    if 'response_payload=response,' in worksites[
        worksites.index("def create_worksite_invitation"):
        worksites.index("def revoke_worksite_invitation")
    ]:
        raise SystemExit("Einladungstoken darf nicht im Mutation Receipt gespeichert werden")

    if "token_hash" not in worksites:
        raise SystemExit("Einladungscode muss serverseitig gehasht gespeichert werden")

    print("OK: TB-Server 1.0 targeted worksite invitation contract")


if __name__ == "__main__":
    main()
