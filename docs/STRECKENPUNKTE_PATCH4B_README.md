# Streckenpunkte Patch 4B

Enthalten:

- Export-Historie für Streckenpunkte
- Button **„Exporte“** im Streckenpunkte-Screen
- Anzeige **„Letzter Export“** direkt im Screen
- **Erneut exportieren** aus der Export-Liste
- sauberere JSON-Struktur mit:
  - `meta`
  - `zusammenfassung`
  - `streckenpunkte`
- Export-Index unter `app external files/exports/export_index.json`

## Hinweise

- Es ist **keine DB-Migration** nötig.
- Die Export-Liste basiert auf den geschriebenen JSON-Dateien im App-Exportordner.
- Für ältere Exporte ohne neuen `meta`-Block wird die Liste nach Möglichkeit rückwirkend aus der Datei gelesen.
- Der aktuelle Projektstand speichert normale Streckenpunkte. Eigene Track-/Anker-Tabellen sind in diesem Patch nicht enthalten.
