import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

server_file = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FileApi.kt").read_text(encoding="utf-8")
sync_fragment = (ROOT / "app/src/main/java/de/tbhelfer/ui/sync/BaustellenSyncFragment.kt").read_text(encoding="utf-8")
entities = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncEntities.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

required = [
    ("Server1FileApi JSONArray import", "import org.json.JSONArray", server_file),
    ("Server1FileApi JSONArray use", 'root.optJSONArray("files") ?: JSONArray()', server_file),
    (
        "BaustellenSyncFragment SyncWorksiteContextEntity import",
        "import de.tbhelfer.datenbank.SyncWorksiteContextEntity",
        sync_fragment,
    ),
    ("BaustellenSyncFragment role helper", "private fun roleLabel(context: SyncWorksiteContextEntity)", sync_fragment),
    ("BaustellenSyncFragment display helper", "private fun displayName(context: SyncWorksiteContextEntity)", sync_fragment),
    ("Sync entity memberRole field", "val memberRole: String", entities),
    ("Sync entity name field", "val name: String? = null", entities),
    ("Sync entity designation field", "val designation: String? = null", entities),
]

missing = [label for label, needle, haystack in required if needle not in haystack]
if missing:
    raise SystemExit("Android 200052 compile import check failed: " + ", ".join(missing))

print("OK Android 200052 compile imports")

assert int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)) >= 200052
