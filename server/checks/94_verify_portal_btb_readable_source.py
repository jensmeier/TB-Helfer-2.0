from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

# Grundstruktur der lesbaren Portalansicht.
for token in (
    "1. Berichtszeit",
    "2. Wetter",
    "3. Personal / Geräte",
    "4. Ausgeführte Arbeiten",
    "5. Abnahmen",
    "6. Einweisung / Unterweisung",
    "7. Besonderheiten",
    "8. Mängel / Schäden",
    "Geräte / Maschinen",
    "renderHeaderSummary",
    "renderWeather",
    "renderEntries",
    "btb-readable-entry",
    "btb-section-head",
):
    assert token in portal, token

# Android-interne Daten bleiben intern; die normale Portaloberfläche bietet
# keine Roh-JSON-Bearbeitung mehr an.
for forbidden in (
    "Technischer Inhalt (JSON)",
    "Technische Kopfdaten (JSON)",
    "JSON (nur zur Diagnose)",
    "Inhalt als JSON",
):
    assert forbidden not in portal, forbidden

# Bestehende Rollen-/Schichtregeln dürfen durch die Darstellungsänderung
# nicht verschwinden.
for token in (
    "Schicht Ende",
    "Schicht fortsetzen",
    "BTB endgültig abschließen",
    "status==='FINALIZED'&&finalPdf&&isTeamLead()",
):
    assert token in portal, token

print("OK: portal BTB renders the readable app-like 1-8 structure without raw JSON controls")
