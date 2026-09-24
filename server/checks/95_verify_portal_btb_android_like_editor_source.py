from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")

# Nur als Template deployt: Schichtstatus darf erst erscheinen, wenn das
# Backend den participants/current_user-Vertrag wirklich liefert.
for token in (
    "shiftContractAvailable",
    "participantsCard').style.display=shiftContractAvailable?'block':'none'",
    "Object.prototype.hasOwnProperty.call(result.data,'participants')",
    "Object.prototype.hasOwnProperty.call(result.data,'current_user')",
):
    assert token in portal, token

# Der sichtbare Portal-BTB folgt der App-Struktur. Punkte 4-8 sind fest
# vorhanden, auch wenn ein Abschnitt aktuell keine Einträge enthält.
for token in (
    "1. Berichtszeit",
    "2. Wetter",
    "3. Personal / Geräte",
    "4. Ausgeführte Arbeiten",
    "5. Abnahmen",
    "6. Einweisung / Unterweisung",
    "7. Besonderheiten",
    "8. Mängel / Schäden",
    "ALWAYS_OPEN_SECTIONS",
    "EINWEISUNG_UNTERWEISUNG",
    "MANGEL_SCHADEN",
):
    assert token in portal, token

# Wetter kommt aus dem bereits vorhandenen Android-Headervertrag.
for token in (
    "tb_helfer_android_v1",
    "renderWeather",
    "weatherSlots",
    "wetter",
    "weatherSlots",
):
    assert token in portal, token

# Personal und Geräte sind getrennt nebeneinander einklappbar und nutzen
# weiterhin die strukturierte Firmen-/Legacy-Deduplizierung.
for token in (
    "btb-overview-grid",
    "personalEntries",
    "deviceEntries",
    "personal-frei:",
    "geraet-frei:",
    "isLegacyDuplicate",
    "normalizedSummary",
):
    assert token in portal, token

# Firmen-Metadaten sind nur Gruppierungsdaten, keine eigene Rohdaten-Sektion.
assert "if(key==='FIRMA')return" in portal

# Androids internes Feld "notizen" ist fachlich Bestandteil von
# "4. Ausgeführte Arbeiten" und darf im Portal keinen eigenen Abschnitt bilden.
for token in (
    "legacyWorkLines",
    "groups.NOTIZEN",
    "groups.ALLGEMEIN",
    "renderWorkGrouped(list,firms,legacyWork)",
):
    assert token in portal, token
assert "Weitere Angaben / Notizen" not in portal

# Bearbeiten ist ausschließlich fachlich. Kein Benutzer darf im normalen
# Portal Roh-JSON, Sortierung oder Versionsfelder editieren.
for token in (
    "entryModal",
    "openEntryEditor",
    "Firma bearbeiten",
    "Personal von",
    "Geräte von",
    "Rolle",
    "Bezeichnung",
):
    assert token in portal, token

for forbidden in (
    "Technische Details",
    "Technische Kopfdaten",
    "Inhalt als JSON",
    "JSON (nur zur Diagnose)",
    "Dateiinformation",
    "headerJson",
    "entryAddBox",
    "entryForm",
    "btb-section-count",
):
    assert forbidden not in portal, forbidden

# Fotos bleiben als verständliche Karten; technischer Originalname ist nicht
# mehr als sichtbare Portal-Information vorgesehen.
for token in (
    "btb-photo-grid",
    "Foto '+(index+1)",
    "bytesLabel",
):
    assert token in portal, token

# Status wird freundlich übersetzt, technische BTB-ID nicht als Hauptanzeige gezeigt.
assert "friendlyBtbStatus" in portal
assert "btb-tech-id" not in portal

print("OK: portal BTB matches the requested app-like 1-8 layout without JSON controls")
