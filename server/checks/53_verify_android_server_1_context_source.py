from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    api = read("app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt")
    repo = read("app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt")
    fragment = read("app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt")
    strings = read("app/src/main/res/values/collab_strings.xml")
    capabilities = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ("fun loadWorksiteContexts(token: String): WorksiteContextResult", api),
        ('supports("worksite_tree_v1")', api),
        ('supports("worksite_permissions_v1")', api),
        ('getAuthorized(token, "/api/v1/worksites")', api),
        ('"/api/v1/device/me"', api),
        ('"/api/v1/worksites/${encode(worksiteId)}/members"', api),
        ("suspend fun replaceWorksiteContexts", repo),
        ("teamApi.loadWorksiteContexts(token)", fragment),
        ("SyncUiResult.ContextOnly", fragment),
        ("collab.replaceWorksiteContexts(contexts)", fragment),
        ("R.string.collab_server1_context_only", fragment),
        ('name="collab_server1_context_only"', strings),
        ('"worksite_tree_v1": True', capabilities),
        ('"worksite_permissions_v1": True', capabilities),
        ("53_verify_android_server_1_context_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Android-Server-1.0-Kontextmarker: " + ", ".join(missing)
        )

    modern_call = fragment.index("teamApi.loadWorksiteContexts(token)")
    legacy_call = fragment.index("collab.synchronizeAll(token)")
    if modern_call >= legacy_call:
        raise SystemExit(
            "Server-1.0-Kontext muss vor dem Legacy-Collab-Vollsync geprüft werden"
        )

    context_block = fragment[
        fragment.index("private suspend fun synchronizeInternal"):
        fragment.index("val notifications = mutableListOf")
    ]
    if "return SyncUiResult.ContextOnly" not in context_block:
        raise SystemExit("Server-1.0-Kontextmodus muss den Legacy-Fachsync überspringen")

    api_block = api[
        api.index("fun loadWorksiteContexts"):
        api.index("fun loadTeam")
    ]
    if "server_version" in api_block or '"0.7"' in api_block or '"0.6"' in api_block:
        raise SystemExit("Kontext-Cutover darf nicht anhand einer Versionsschwelle entscheiden")

    print("OK: Android loads Server-1.0 worksite context before legacy fach sync")


if __name__ == "__main__":
    main()
