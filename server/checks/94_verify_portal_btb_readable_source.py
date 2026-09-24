from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

for token in (
    "BTB-Inhalte",
    "Lesbare Darstellung wie in der App",
    "Geräte / Maschinen",
    "Ausgeführte Arbeiten",
    "Abnahmen",
    "Einweisung / Unterweisung",
    "Besonderheiten",
    "Mängel / Schäden",
    "Notizen",
    "Technischer Inhalt (JSON)",
    "toggleEntryEditor",
    "cleanDisplayText",
    "renderHeaderSummary",
    "btb-readable-entry",
    "btb-section-head",
):
    assert token in portal, token

# Roh-JSON bleibt als Bearbeitungsweg erhalten, ist aber nicht mehr die
# Standarddarstellung jedes Eintrags.
assert "renderPayload(e.payload||{})" in portal
assert "btb-entry-editor" in portal
assert "style.display=canEditCurrentBtb()" in portal

# Bestehende Rollen-/Schichtregeln dürfen durch die Darstellungsänderung
# nicht verschwinden.
for token in (
    "Schicht Ende",
    "Schicht fortsetzen",
    "BTB endgültig abschließen",
    "status==='FINALIZED'&&finalPdf&&isTeamLead()",
):
    assert token in portal, token

print("OK: portal BTB renders readable app-like sections and keeps technical editing behind an editor")
