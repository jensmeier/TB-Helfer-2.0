from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
template = (ROOT / "server/templates/v1_releases.html").read_text(encoding="utf-8")
release_doc = (ROOT / "docs/TB-SERVER_1_0_RELEASES.md").read_text(encoding="utf-8")

checks = [
    ("manifest file picker", 'id="releaseManifestFile"', template),
    ("binary file picker", 'id="releaseBinaryFile"', template),
    ("manifest parser", "function parseReleaseManifest", template),
    ("manifest template download", "Vorlage herunterladen", template),
    ("new notes section", 'id="notesNew"', template),
    ("improved notes section", 'id="notesImproved"', template),
    ("fixed notes section", 'id="notesFixed"', template),
    ("draft editor", "Draft bearbeiten / Dateien", template),
    ("draft patch", "method:'PATCH'", template),
    ("draft manifest import", "readManifestIntoDraft", template),
    ("draft manifest export", "downloadReleaseManifest", template),
    ("visible APK upload", "APK / Binärdatei auswählen", template),
    ("create plus upload flow", "await uploadBinaryFile(created.id,binary,createProgress)", template),
    ("draft binary verification route", "'/api/v1/admin/releases/'+encodeURIComponent(r.id)+'/binary'", template),
    ("release package documentation", "TB-HELFER-RELEASE", release_doc),
]

missing = [name for name, needle, haystack in checks if needle not in haystack]
if missing:
    raise SystemExit("Release admin package UI source check failed: " + ", ".join(missing))

print("OK release admin package UI")
