import re
from pathlib import Path
import ast
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

release_py = (ROOT / "server/tbserver/releases.py").read_text(encoding="utf-8")
release_html = (ROOT / "server/templates/v1_releases.html").read_text(encoding="utf-8")
update_kt = (ROOT / "app/src/main/java/de/tbhelfer/server/AppUpdateManager.kt").read_text(encoding="utf-8")
start_kt = (ROOT / "app/src/main/java/de/tbhelfer/ui/start/StartmenueFragment.kt").read_text(encoding="utf-8")
wartung_kt = (ROOT / "app/src/main/java/de/tbhelfer/ui/einstellungen/wartung/WartungDiagnoseFragment.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
start_layout_path = ROOT / "app/src/main/res/layout/fragment_startmenue.xml"
wartung_layout_path = ROOT / "app/src/main/res/layout/fragment_wartung_diagnose.xml"
paths_path = ROOT / "app/src/main/res/xml/dateipfade.xml"

ast.parse(release_py)
ET.parse(manifest_path)
ET.parse(start_layout_path)
ET.parse(wartung_layout_path)
ET.parse(paths_path)

checks = [
    ("public sha256", '"sha256": row.get("sha256")', release_py),
    ("public byte_size", '"byte_size": row.get("byte_size")', release_py),
    ("release notes admin", "composeReleaseNotes", release_html),
    ("compatibility endpoint", "/api/v1/client-compatibility", update_kt),
    ("current build", "BuildConfig.VERSION_CODE", update_kt),
    ("sha verification", 'MessageDigest.getInstance("SHA-256")', update_kt),
    ("https download", 'startsWith("https://")', update_kt),
    ("package installer", "application/vnd.android.package-archive", update_kt),
    ("unknown apps settings", "ACTION_MANAGE_UNKNOWN_APP_SOURCES", update_kt),
    ("start update button", "btnUpdateAvailable", start_kt),
    ("start auto check", "AppUpdateManager.check()", start_kt),
    ("manual update button", "btnWartungNachUpdates", wartung_kt),
    ("maintenance notes", "txtWartungUpdateNotes", wartung_kt),
    ("maintenance coroutine import", "import kotlinx.coroutines.launch", wartung_kt),
    ("maintenance default text color", "updateDefaultTextColor", wartung_kt),
]

missing = [name for name, needle, haystack in checks if needle not in haystack]
if missing:
    raise SystemExit("Android updater source check failed: " + ", ".join(missing))

manifest = manifest_path.read_text(encoding="utf-8")
if "android.permission.REQUEST_INSTALL_PACKAGES" not in manifest:
    raise SystemExit("Android updater source check failed: REQUEST_INSTALL_PACKAGES missing")

start_layout = start_layout_path.read_text(encoding="utf-8")
wartung_layout = wartung_layout_path.read_text(encoding="utf-8")
paths = paths_path.read_text(encoding="utf-8")
for needle, haystack in [
    ("@+id/btnUpdateAvailable", start_layout),
    ("@+id/btnWartungNachUpdates", wartung_layout),
    ("@+id/btnWartungUpdateInstallieren", wartung_layout),
    ('path="Download/"', paths),
]:
    if needle not in haystack:
        raise SystemExit("Android updater source check failed: " + needle)

if "android.R.color.primary_text_light" in wartung_kt:
    raise SystemExit("Android updater source check failed: deprecated primary_text_light still used")

print("OK Android 200054 server-driven updater source contract")

assert int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)) >= 200054
