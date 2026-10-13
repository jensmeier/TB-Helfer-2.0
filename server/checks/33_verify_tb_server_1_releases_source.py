from __future__ import annotations

import ast
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    if rel.endswith(".py"):
        ast.parse(source, filename=rel)
    return source


def verify_runtime() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.release_compatibility import compatibility_status, normalize_platform

    assert normalize_platform("android") == "ANDROID"
    try:
        normalize_platform("symbian")
    except ValueError as exc:
        assert str(exc) == "INVALID_PLATFORM"
    else:
        raise AssertionError("unknown platform must fail")

    assert compatibility_status(
        current_build=None,
        latest_build=200100,
        minimum_supported_build=200050,
        latest_mandatory=False,
    ) == "UNKNOWN_BUILD"
    assert compatibility_status(
        current_build=200049,
        latest_build=200100,
        minimum_supported_build=200050,
        latest_mandatory=False,
    ) == "UNSUPPORTED_BUILD"
    assert compatibility_status(
        current_build=200050,
        latest_build=200100,
        minimum_supported_build=200050,
        latest_mandatory=False,
    ) == "SUPPORTED_UPDATE_AVAILABLE"
    assert compatibility_status(
        current_build=200050,
        latest_build=200100,
        minimum_supported_build=200050,
        latest_mandatory=True,
    ) == "UPDATE_REQUIRED"
    assert compatibility_status(
        current_build=200100,
        latest_build=200100,
        minimum_supported_build=200050,
        latest_mandatory=True,
    ) == "SUPPORTED_CURRENT"


def main() -> None:
    compatibility = read("server/tbserver/release_compatibility.py")
    releases = read("server/tbserver/releases.py")
    main_source = read("server/tbserver/main.py")
    capabilities = read("server/tbserver/capabilities.py")
    migration = read("server/v1_migrations/001_server_1_0_base.sql")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('PLATFORMS = {"ANDROID", "IOS", "WINDOWS"}', compatibility),
        ('return "UNSUPPORTED_BUILD"', compatibility),
        ('return "UPDATE_REQUIRED"', compatibility),
        ('return "SUPPORTED_UPDATE_AVAILABLE"', compatibility),
        ('@router.get("/client-compatibility")', releases),
        ('@router.get("/releases/{platform}/latest")', releases),
        ('@router.get("/admin/releases")', releases),
        ('@router.post("/admin/releases", status_code=201)', releases),
        ('@router.patch("/admin/releases/{release_id}")', releases),
        ('@router.post("/admin/releases/{release_id}/publish")', releases),
        ('@router.post("/admin/releases/{release_id}/retire")', releases),
        ('PUBLISHED_RELEASE_IMMUTABLE', releases),
        ('ANDROID_BUILD_NUMBER_REQUIRED', releases),
        ('MINIMUM_BUILD_EXCEEDS_RELEASE_BUILD', releases),
        ('INVALID_DOWNLOAD_URL', releases),
        ('INVALID_VERSION_NAME', releases),
        ('Depends(require_admin)', releases),
        ('Depends(require_admin_csrf)', releases),
        ('action="RELEASE_PUBLISH"', releases),
        ('releases_router', main_source),
        ('"release_catalog_v1": True', capabilities),
        ('"client_compatibility_v1": True', capabilities),
        ('CREATE TABLE tb.releases', migration),
        ('33_verify_tb_server_1_releases_source.py', workflow),
        ('"release_catalog_v1"', workflow),
        ('"client_compatibility_v1"', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Release-/Kompatibilitaetsmarker: " + ", ".join(missing))

    public_block = releases[releases.index("def _public_release"):releases.index("def _load_release")]
    # SHA-256 und Dateigroesse sind oeffentliche Download-Integritaetsdaten.
    # Der interne storage_key darf dagegen nur zur Erzeugung der API-Download-URL
    # gelesen, niemals als Feld im oeffentlichen JSON ausgegeben werden.
    if '"storage_key":' in public_block or '"storage_key", ' in public_block:
        raise SystemExit("Oeffentliche Release-Metadaten duerfen interne Storage-Pfade nicht leaken")
    if 'f"/api/v1/releases/{row[\'id\']}/download"' not in public_block:
        raise SystemExit("Oeffentlicher Release-Download muss ueber die API laufen")
    if '"sha256": row.get("sha256")' not in public_block or '"byte_size": row.get("byte_size")' not in public_block:
        raise SystemExit("Download-Integritaetsmetadaten fehlen")

    verify_runtime()
    print("OK: TB-Server 1.0 release catalog + client compatibility contract")


if __name__ == "__main__":
    main()
