from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    api = read("app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt")
    server = read("server/tbserver/worksites.py")
    devices = read("server/tbserver/devices.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("fun loadTeam(token: String, worksiteId: String): TeamResult", api),
        ("loadServerTeam(token, worksiteId)", api),
        ("loadLegacyTeam(token, worksiteId)", api),
        ('"/api/v1/worksites/${encode(worksiteId)}"', api),
        ('"/api/v1/worksites/${encode(worksiteId)}/members"', api),
        ('"/api/v1/worksites/${encode(worksiteId)}/teams"', api),
        ('"/api/v1/device/me"', api),
        ("private data class ModernTeamMember", api),
        ('"RESPONSIBLE_TB", "TB" -> "LEAD"', api),
        ("currentMember?.teamId.orEmpty()", api),
        ("TeamGroup(", api),
        ("getAuthorized(token: String, path: String)", api),
        ('@router.get("/{worksite_id}")', server),
        ('@router.get("/{worksite_id}/members")', server),
        ('@router.get("/{worksite_id}/teams")', server),
        ('@router.get("/me")', devices),
        ("51_verify_android_server_1_team_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Android-Server-1.0-Teammarker: " + ", ".join(missing)
        )

    wrapper = api[api.index("fun loadTeam(token: String, worksiteId: String): TeamResult"):api.index("private fun loadServerTeam")]
    if "TeamResult.Unsupported -> loadLegacyTeam" not in wrapper:
        raise SystemExit("Legacy-Team-Fallback darf nur nach Server-1.0-Unsupported erfolgen")

    modern = api[api.index("private fun loadServerTeam"):api.index("private fun loadLegacyTeam")]
    if "/api/v1/collab/" in modern:
        raise SystemExit("Moderner Teamloader darf keine Legacy-Collab-Route verwenden")

    print("OK: Android builds team overview from Server-1.0 worksite/member/team APIs with legacy fallback")


if __name__ == "__main__":
    main()
