# Bahnhof-Autofill im Server-Admin

Der Server verwendet **keine neue Bahnhofsliste**. Für die Autovervollständigung werden dieselben beiden Datenbestände verwendet, die bereits in der Android-App liegen:

- `TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`

## Datenformat

Die aktive Bahnhofsliste wird zuerst gelesen:

- Root-Array: `eintraege`
- Bahnhofscode: `code`
- Name: `name`

Die Betriebsstellenliste ergänzt fehlende Codes:

- Root-Array: `betriebsstellen`
- Bahnhofscode: `rl100Code`
- Name: `rl100Langname`

Bei gleichem Code hat die aktive Bahnhofsliste Vorrang.

## Suchlogik

Die Suche orientiert sich an `BahnhofSucheUtil.kt` der Android-App:

- trimmen
- kleinschreiben
- für die Suche Sonder-/Trennzeichen entfernen
- Treffer über Code, Name oder Anzeige `Name (CODE)`

Damit funktionieren z. B. sowohl Code-Eingaben als auch Namensfragmente.

## Serverpfade

Standardmäßig erwartet der Server die Dateien unter `server/data/` bzw. im Container relativ zum App-Arbeitsverzeichnis. Alternativ können die Pfade über Umgebungsvariablen gesetzt werden:

- `STATION_ACTIVE_PATH`
- `STATION_OPERATING_PATH`

Die Dateien selbst bleiben fachlich identisch mit den Android-Assets; es wird kein externer Datenbestand dazugemischt.
