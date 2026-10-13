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
    template = read("server/templates/v1_worksite.html")
    rules = read("docs/TB-HELFER_PROJEKTREGELN.md")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    required = [
        ("Ohne angenommene Einladung keine Baustellen-Zusammenarbeitsdaten.", rules),
        ("class HelperTeamTransfer", worksites),
        ('@router.post("/{worksite_id}/members/{member_user_id}/team")', worksites),
        ('receipt_type="WORKSITE_HELPER_TEAM_TRANSFER"', worksites),
        ('action="WORKSITE_HELPER_TEAM_TRANSFER"', worksites),
        ('"TEAM_TRANSFER_HELPER_ONLY"', worksites),
        ('"TEAM_INVALID"', worksites),
        ("tm.removed_at IS NULL", worksites),
        ("SET removed_at = now()", worksites),
        ("DO UPDATE SET role = 'HELPER', removed_at = NULL", worksites),
        ("permissions_version = permissions_version + 1", worksites),
        ('entity_type="WORKSITE_MEMBERSHIP"', worksites),
        ('@router.post("/{worksite_id}/invitations", status_code=201)', worksites),
        ('@router.post("/invitations/redeem")', worksites),
        ("current_team.team_id", worksites),
        ("current_team.team_name", worksites),
        ("transferHelperTeam", template),
        ("Team wechseln", template),
        ('"worksite_team_transfer_v1": True', caps),
        ("46_verify_tb_server_1_invitation_only_membership_source.py", workflow),
        ('"worksite_team_transfer_v1"', workflow),
    ]
    missing = [needle for needle, source in required if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Einladung-/Teamwechsel-Marker: " + ", ".join(missing)
        )

    forbidden = [
        "class MemberAdd(BaseModel)",
        '@router.post("/{worksite_id}/members", status_code=201)',
        'receipt_type="WORKSITE_MEMBER_ADD"',
        'action="WORKSITE_MEMBER_UPSERT"',
    ]
    present = [needle for needle in forbidden if needle in worksites]
    if present:
        raise SystemExit(
            "Direkte Mitgliedsaufnahme umgeht Einladung: " + ", ".join(present)
        )

    print("OK: TB-Server 1.0 invitation-only collaboration + helper team transfer")


if __name__ == "__main__":
    main()
