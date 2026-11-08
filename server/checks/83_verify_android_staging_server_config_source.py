from __future__ import annotations
import re

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    gradle = read("app/build.gradle.kts")
    manifest = read("app/src/main/AndroidManifest.xml")
    config = read("app/src/main/java/de/tbhelfer/server/ServerConfig.kt")
    gitignore = read(".gitignore")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assert int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)) >= 200051
    assertions = [
        ('tb.server.base.url', gradle),
        ('TB_SERVER_BASE_URL', gradle),
        ('getByName("debug")', gradle),
        ('buildConfigField("String", "SERVER_BASE_URL"', gradle),
        ('"https://api-v1.tb-helfer.de"', gradle),
        ('https://api.tb-helfer.de', gradle),
        ('manifestPlaceholders["usesCleartextTraffic"] = "false"', gradle),
        ('debugServerBaseUrl.startsWith("http://")', gradle),
        ('buildFeatures { buildConfig = true }', gradle),
        ('android:usesCleartextTraffic="${usesCleartextTraffic}"', manifest),
        ('BuildConfig.SERVER_BASE_URL', config),
        ('local.properties', gitignore),
        ('83_verify_android_staging_server_config_source.py', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Android-Staging-Serverkonfiguration: " + ", ".join(missing)
        )

    release = gradle[gradle.index("        release {"):gradle.index("    buildFeatures {")]
    if "debugServerBaseUrl" in release or "TB_SERVER_BASE_URL" in release:
        raise SystemExit(
            "Release darf keine lokale/debug Serveradresse uebernehmen."
        )
    if r'buildConfigField("String", "SERVER_BASE_URL", "\"https://api.tb-helfer.de\"")' not in release:
        raise SystemExit(
            "Release muss fest auf https://api.tb-helfer.de zeigen."
        )

    print("OK: Android 200051 debug defaults to Server 1.0 staging; release stays HTTPS production")


if __name__ == "__main__":
    main()
