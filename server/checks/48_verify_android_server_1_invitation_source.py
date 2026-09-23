from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    parser = read("app/src/main/java/de/tbhelfer/ui/sync/WorksiteInvitationLink.kt")
    team_api = read("app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt")
    activation = read("app/src/main/java/de/tbhelfer/ui/activation/AktivierungActivity.kt")
    sync_ui = read("app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt")
    direct_team_ui = read("app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenTeamDirekt.kt")
    manifest = read("app/src/main/AndroidManifest.xml")
    test = read("app/src/test/java/de/tbhelfer/ui/sync/WorksiteInvitationLinkTest.kt")
    android_ci = read(".github/workflows/android.yml")
    server_links = read("server/tbserver/invitation_links.py")

    assertions = [
        ('INVITE_HOST = "worksite-invite"', server_links),
        ('return f"{INVITE_SCHEME}://{INVITE_HOST}?token={encoded}"', server_links),
        ('SERVER_HOST = "worksite-invite"', parser),
        ('Credential.ServerToken', parser),
        ('Credential.LegacyCode', parser),
        ('BROWSER_REDEEM_PATH = "/portal/v1/invitations/redeem"', parser),
        ('value.startsWith("TBH-"', parser),
        ('fun redeemServerInvitation', team_api),
        ('"/api/v1/worksites/invitations/redeem"', team_api),
        ('"X-Client-Change-Id"', team_api),
        ('JSONObject().put("token", rawToken)', team_api),
        ('WORKSITE_INVITATION_', team_api),
        ('WorksiteInvitationLink.parse(intent?.data?.toString().orEmpty())', activation),
        ('teamApi.redeemServerInvitation(token, invitation.value)', activation),
        ('WorksiteInvitationLink.parse(barcode.rawValue.orEmpty())', sync_ui),
        ('teamApi.redeemServerInvitation(token, invitation.value)', sync_ui),
        ('import de.tbhelfer.ui.sync.WorksiteInvitationLink', direct_team_ui),
        ('WorksiteInvitationLink.parse(barcode.rawValue.orEmpty())', direct_team_ui),
        ('WorksiteInvitationLink.Credential.ServerToken', direct_team_ui),
        ('teamApi.redeemServerInvitation(token, invitation.value)', direct_team_ui),
        ('WorksiteInvitationLink.Credential.LegacyCode', direct_team_ui),
        ('teamApi.redeemInvitation(token, invitation.value)', direct_team_ui),
        ('android:host="worksite-invite"', manifest),
        ('parsesServerAppUriWithoutChangingTokenCase', test),
        ('keepsLegacyInviteCompatibility', test),
        (':app:testDebugUnitTest', android_ci),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Android-/Server-1.0-Einladungsmarker: " + ", ".join(missing)
        )

    if "extractInvitationCode(" in direct_team_ui:
        raise SystemExit(
            "Direkter Baustellen-QR-Scanner darf keinen parallelen Legacy-Parser mehr verwenden"
        )

    if 'uppercase()' in parser[
        parser.index('if (scheme == SERVER_SCHEME && uri.host.equals(SERVER_HOST'):
        parser.index('if (scheme == SERVER_SCHEME && uri.host.equals(LEGACY_HOST')
    ]:
        raise SystemExit("Server-1.0-Einladungstoken darf nicht uppercased werden")

    print("OK: Android handles Server-1.0 worksite invitation URI/token with legacy fallback")


if __name__ == "__main__":
    main()
