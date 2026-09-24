from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

# Die nur als Template deployte Portalansicht darf den neuen Schichtvertrag
# nicht vortäuschen, solange das Backend noch keine participants/current_user liefert.
for token in (
    "shiftContractAvailable",
    "participantsCard').style.display=shiftContractAvailable?'block':'none'",
    "Object.prototype.hasOwnProperty.call(result.data,'participants')",
    "Object.prototype.hasOwnProperty.call(result.data,'current_user')",
):
    assert token in portal, token

# Firmen-Metadaten sind Gruppierungsdaten, keine eigene Rohdaten-Sektion.
assert "if(key==='FIRMA')return" in portal
for token in ("isDeleted", "visibleInPersonal", "visibleInMaschinen"):
    assert token in portal  # weiterhin im Datenvertrag bekannt
assert "cleanPayload" in portal

# Android-Legacy-Zusammenfassungen werden bei vorhandenen strukturierten
# Personal-/Gerätezeilen nur für die Anzeige dedupliziert.
for token in (
    "personal-frei:",
    "geraet-frei:",
    "isLegacyDuplicate",
    "normalizedSummary",
):
    assert token in portal, token

# Bearbeiten ist ein fachlicher Dialog; JSON bleibt nur Diagnose-Fallback.
for token in (
    "entryModal",
    "openEntryEditor",
    "Firma bearbeiten",
    "Personal von",
    "Geräte von",
    "Rolle",
    "Bezeichnung",
    "Technische Details",
    "JSON (nur zur Diagnose)",
):
    assert token in portal, token

# Fotos werden als verständliche Karten statt mit dem langen technischen
# Originalnamen als Hauptüberschrift dargestellt.
for token in (
    "btb-photo-grid",
    "Foto '+(index+1)",
    "Dateiinformation",
    "bytesLabel",
):
    assert token in portal, token

# Technische Statuswerte/UUID stehen nicht mehr als Hauptanzeige im Vordergrund.
for token in ("friendlyBtbStatus", "btb-tech-id", "deDate(current.work_date)"):
    assert token in portal, token

print("OK: portal BTB screenshot issues are addressed without changing the BTB data contract")
