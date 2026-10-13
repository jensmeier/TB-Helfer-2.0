from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    api = read("app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt")
    ui = read("app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt")
    strings = read("app/src/main/res/values/collab_strings.xml")
    server = read("server/tbserver/worksites.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("data class InvitationCandidate", api),
        ("sealed interface InvitationCandidatesResult", api),
        ("fun loadServerInvitationCandidates", api),
        ('"/api/v1/worksites/${encode(worksiteId)}/invite-candidates"', api),
        ("fun createServerInvitation", api),
        ('"/api/v1/worksites/${encode(worksiteId)}/invitations"', api),
        ('"X-Client-Change-Id"', api),
        ('"invited_user_id"', api),
        ('"worksite_role"', api),
        ('"permission_profile"', api),
        ('"invite_token"', api),
        ('"invite_uri"', api),
        ('"invite_link"', api),
        ("shareLink", api),
        ("loadServerInvitationCandidates", ui),
        ("showServerInvitationCandidateDialog", ui),
        ("createServerInvitation(", ui),
        ("createLegacyInvitation(", ui),
        ('"HELPER_STANDARD"', ui),
        ('"RESPONSIBLE_TB"', ui),
        ("invitation.shareLink", ui),
        ("collab_invite_choose_person", strings),
        ("collab_invite_no_candidates", strings),
        ('@router.get("/{worksite_id}/invite-candidates")', server),
        ('@router.post("/{worksite_id}/invitations"', server),
        ("50_verify_android_server_1_invitation_create_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Android-Server-1.0-Einladungserzeugungsmarker: "
            + ", ".join(missing)
        )

    modern_block = api[
        api.index("fun createServerInvitation"):
        api.index("fun createInvitation(", api.index("fun createServerInvitation"))
    ]
    if '"android-worksite-invite-create-${UUID.randomUUID()}"' not in modern_block:
        raise SystemExit("Moderne Einladung braucht eigene Client-Change-ID")

    ui_block = ui[
        ui.index("private fun createInvitation("):
        ui.index("private fun showInvitationQr(")
    ]
    if "InvitationCandidatesResult.Unsupported" not in ui_block:
        raise SystemExit("Legacy-Fallback muss bei fehlendem Server-1.0-Kandidatenpfad bestehen")
    if "createLegacyInvitation" not in ui_block:
        raise SystemExit("Legacy-Einladungspfad fehlt während der Umstellung")

    print("OK: Android can create targeted Server-1.0 invitations with legacy fallback")


if __name__ == "__main__":
    main()
