from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt").read_text(encoding="utf-8")
repo = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabSyncRepository.kt").read_text(encoding="utf-8")
target = (ROOT / "docs/TB-SERVER_1_0_ZIELMODELL.md").read_text(encoding="utf-8")

assert 'const val LEGACY_ANDROID_SECTION = "TB_HELFER_ANDROID_V1"' in api
assert '.put("section", LEGACY_ANDROID_SECTION)' not in api
assert "legacy-bridge-delete" in api
assert "requiresGranularMigration" in api
assert "requiresGranularMigration" in repo

for section in (
    "SECTION_FIRMA",
    "SECTION_PERSONAL",
    "SECTION_GERAET",
    "SECTION_ARBEIT",
    "SECTION_ABNAHME",
    "SECTION_EINWEISUNG",
    "SECTION_BESONDERHEIT",
    "SECTION_MANGEL",
    "SECTION_NOTIZEN",
):
    assert section in api, section

for token in (
    "baseStateJson",
    "localHeaderChanged",
    "baseEntries",
    "currentEntries",
    "BTB_ENTRY_CREATE_CONFLICT",
    "BTB_ENTRY_DELETE_CONFLICT",
):
    assert token in api, token

assert "server1BtbBaseKey" in repo
assert "api.applyPending(token, change, baseStateJson)" in repo
assert "textValue = item.baseStateJson" in repo
assert "BTB nicht mehr als eine einzige überschreibbare JSON-Datei" in target
assert "Jeder veränderbare Eintrag erhält eigene UUID und Version" in target

print("OK: Android BTB sync honors the Server-1.0 granular-entry model")
