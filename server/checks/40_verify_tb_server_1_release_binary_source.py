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
    releases = read("server/tbserver/releases.py")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    migration = read("server/v1_migrations/001_server_1_0_base.sql")
    web = read("server/tbserver/admin_web.py")
    base = read("server/templates/v1_base.html")
    template = read("server/templates/v1_releases.html")

    assertions = [
        ('@router.get("/releases/{release_id}/download")', releases),
        ('@router.get("/admin/releases/{release_id}/binary")', releases),
        ('@router.put("/admin/releases/{release_id}/binary")', releases),
        ('@router.delete("/admin/releases/{release_id}/binary"', releases),
        ('request.stream()', releases),
        ('_RELEASE_MAX_BYTES = 512 * 1024 * 1024', releases),
        ('os.fsync(out.fileno())', releases),
        ('X-Release-SHA256', releases),
        ('"RELEASE_SHA256_MISMATCH"', releases),
        ('"RELEASE_BINARY_TOO_LARGE"', releases),
        ('"PUBLISHED_RELEASE_IMMUTABLE"', releases),
        ('"RELEASE_DOWNLOAD_REQUIRED"', releases),
        ('"RELEASE_BINARY_FILE_MISSING"', releases),
        ('download_url = (', releases),
        ('f"/api/v1/releases/{row[\'id\']}/download"', releases),
        ('return f"releases/{row[\'platform\'].lower()}/{row[\'id\']}/package.bin"', releases),
        ('action="RELEASE_BINARY_UPLOAD"', releases),
        ('action="RELEASE_BINARY_DELETE"', releases),
        ('Depends(require_admin_csrf)', releases),
        ('storage_key text', migration),
        ('byte_size bigint', migration),
        ('sha256 text', migration),
        ('"release_binary_v1": True', caps),
        ('40_verify_tb_server_1_release_binary_source.py', workflow),
        ('"release_binary_v1"', workflow),
        ('@router.get("/admin/v1/releases")', web),
        ('href="/admin/v1/releases"', base),
        ("/api/v1/admin/releases", template),
        ("/binary", template),
        ("X-Release-SHA256", template),
        ("Veröffentlichen", template),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Release-Binaerupload-Marker: " + ", ".join(missing)
        )

    admin_block = releases[
        releases.index("def _admin_release"):
        releases.index("def _release_storage_key")
    ]
    if '"storage_key":' in admin_block:
        raise SystemExit("Admin-Release-Payload soll keinen internen Storage-Key ausgeben")

    public_block = releases[
        releases.index("def _public_release"):
        releases.index("def _admin_release")
    ]
    if '"storage_key":' in public_block:
        raise SystemExit("Oeffentliche Release-Metadaten duerfen keine internen Storage-Pfade ausgeben")
    if '"sha256": row.get("sha256")' not in public_block or '"byte_size": row.get("byte_size")' not in public_block:
        raise SystemExit("Oeffentliche Release-Metadaten muessen APK-Integritaetsdaten enthalten")

    public_download_block = releases[
        releases.index("def download_release_binary"):
        releases.index("def admin_download_release_binary")
    ]
    if 'row["status"] != "PUBLISHED"' not in public_download_block:
        raise SystemExit("Oeffentlicher Release-Binaerdownload muss auf PUBLISHED begrenzt sein")

    admin_download_block = releases[
        releases.index("def admin_download_release_binary"):
        releases.index("def admin_upload_release_binary")
    ]
    if "Depends(require_admin)" not in admin_download_block:
        raise SystemExit("Draft-Binaerpruefung muss Admin-Authentisierung verlangen")
    if 'row["status"] != "PUBLISHED"' in admin_download_block:
        raise SystemExit("Admin-Draft-Binaerpruefung darf DRAFT nicht ausschliessen")

    print("OK: TB-Server 1.0 release binary upload/download contract")


if __name__ == "__main__":
    main()
