from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/Server1FachSyncApi.kt").read_text(encoding="utf-8")

defect_keys = ("beschreibung", "bereich", "km", "firma", "kommentar")
acceptance_keys = (
    "bezeichnung", "objekt", "firma", "weitereFirmen", "abnehmender", "anwesende",
    "kmVon", "kmBis", "kommentar", "restpunkte", "ergebnis",
)

for key in defect_keys:
    assert f'payload.{key}' in portal or f'payload["{key}"]' in portal, key
    assert f'.put("{key}"' in bridge, key

for key in acceptance_keys:
    assert f'payload.{key}' in portal or f'payload["{key}"]' in portal, key
    assert f'.put("{key}"' in bridge, key

for token in (
    "defectEditVersion",
    "acceptanceEditVersion",
    "defectPayloadFromForm",
    "acceptancePayloadFromForm",
    "resetDefectForm",
    "resetAcceptanceForm",
    "Zusatzdaten (JSON)",
    "body.base_version",
):
    assert token in portal, token

assert 'fachPayload.optString("beschreibung")' in api
assert 'fachPayload.optString("bezeichnung")' in api
assert "Details als JSON" not in portal

print("OK: Portal fach forms edit the same Android defect/acceptance payload keys with version protection")
