from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    capabilities = read("app/src/main/java/de/tbhelfer/server/ServerCapabilitiesApi.kt")
    team = read("app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt")
    server = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('"/api/v1/capabilities"', capabilities),
        ("CACHE_TTL_MS = 5 * 60 * 1000L", capabilities),
        ("data class Snapshot", capabilities),
        ("fun supports(key: String): Boolean", capabilities),
        ("HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported", capabilities),
        ("private val capabilitiesApi = ServerCapabilitiesApi(baseUrl)", team),
        ('supports("worksite_teams_v1")', team),
        ('supports("worksite_invitations_v1")', team),
        ("ServerCapabilitiesApi.Result.Unsupported -> loadLegacyTeam", team),
        ("ServerCapabilitiesApi.Result.Error -> TeamResult.Error", team),
        ('"worksite_teams_v1": True', server),
        ('"worksite_invitations_v1": True', server),
        ("52_verify_android_server_1_capabilities_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Android-Capability-Cutover-Marker: " + ", ".join(missing)
        )

    load_team = team[team.index("fun loadTeam("):team.index("private fun loadServerTeam")]
    if "server_version" in load_team or "0.7" in load_team or "0.6" in load_team:
        raise SystemExit("Team-Cutover darf nicht anhand einer Serverversionsschwelle entscheiden")

    candidates = team[team.index("fun loadServerInvitationCandidates"):team.index("fun createServerInvitation")]
    if 'supports("worksite_invitations_v1")' not in candidates:
        raise SystemExit("Einladungskandidaten müssen capability-gesteuert sein")

    print("OK: Android Server-1.0 team/invitation cutover is capability-gated")


if __name__ == "__main__":
    main()
