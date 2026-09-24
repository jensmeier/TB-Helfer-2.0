from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

# Sichtbare Fachbedienung zum Hinzufügen/Löschen.
for token in (
    "+ Firma",
    "openEntryCreator('FIRMA')",
    "sectionAddButton(key)",
    "deleteEntryDirect",
    "deleteLegacyWorkLine",
    "Firma und zugehörige Einträge gelöscht",
):
    assert token in portal, token

# Portal-Neueinträge verwenden exakt den Android-Granularvertrag.
for token in (
    "_tb_android_managed",
    "_tb_android_slot",
    "firma:'+id",
    "firma:'+firmaId+':personal:",
    "firma:'+firmaId+':geraet:",
    "firma:'+firmaId+':arbeit:",
    "abnahme:",
    "einweisung:",
    "besonderheit:",
    "mangel:",
    "notizen:0",
):
    assert token in portal, token

# Der technische JSON-Editor darf nicht zurückkehren.
for forbidden in (
    "entryAddBox",
    "Inhalt als JSON",
    "JSON (nur zur Diagnose)",
):
    assert forbidden not in portal, forbidden

# Firmenlöschung muss abhängige Firmenzeilen bewusst mitbehandeln.
assert "children=entries.filter(e=>firmIdOf(e)===firmaId" in portal
assert "Zugehöriges Personal, Geräte und Arbeiten" in portal

print("OK: portal BTB supports app-compatible create/delete without raw JSON UI")
