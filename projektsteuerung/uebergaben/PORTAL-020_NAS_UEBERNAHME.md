# PORTAL-020 – Gemeinsamer Bestand je Strecke/Bahnhof

Voraussetzung: vorheriger PORTAL-019-Stand. GitHub Desktop im Branch feature/tb-server-1.0: Fetch origin, Pull origin.

## Genau vier Runtime-Dateien

| PC/GitHub | Vorhandener NAS-Serverquellordner |
| --- | --- |
| server/tbserver/catalog.py | tbserver/catalog.py |
| server/tbserver/catalog_inventory.py (neu) | tbserver/catalog_inventory.py |
| server/templates/v1_catalog_lookup.html | templates/v1_catalog_lookup.html |
| server/templates/v1_worksite.html | templates/v1_worksite.html |

Nach Kopie API-Container neu starten und Browser StrgF5. Bei Image statt Source-Bind-Mount Image neu bauen. Keine Migration, nichts löschen, Daten/Compose/.env unverändert.

## Bedienung

Strecke oder Bahnhof suchen, Treffer anklicken, dann Vorhandene Daten anzeigen aufklappen. Gemeinsamer GLOBAL-Bestand für jeden angemeldeten Nutzer firmenübergreifend, Firmeninhalte bleiben geschützt. Gleise, Weichen, WVB und weitere Datenarten mit Filter und Weitere anzeigen. Bei Unterbaustelle Gleis/Weiche übernehmen bzw. KM übernehmen getrennt wählen. KM werden nicht automatisch überschrieben.

Echte Achssegmente mit getrennten KM-Bereichen; ohne belastbare KM kenntlich, ohne aktive Geometrie kenntlich. Dateidaten wie GVP/KF/KV mit eigenen gespeicherten Grenzen sind zusätzliche Bestände, kein Nachweis lückenloser Achsgeometrie. Keine erfundenen KM aus Stationswerten. Nicht vorhandener gemeinsamer Bestand erscheint als leer, kein Live-NAS-Bestand dieses Blocks behauptet.

## Praxistest auf NAS noch offen

1. Vorhandene Strecke auswählen: getrennte Abschnitte und vorhandene Datentypen prüfen.
2. Pasing auswählen: tatsächliche Gleise/Weichen/WVB im Bestand prüfen; Objektfilter verwenden.
3. Unterbaustelle: Objekt übernehmen, KM-Felder bleiben zunächst unverändert; einzelnen Abschnitt per KM übernehmen einsetzen.
4. Daten ohne KM und Objekte ohne Geometrie prüfen. Gleiche Auswahl mit zweitem Nutzer anderer Firma soll denselben gemeinsamen Bestand zeigen.
5. Nach Wechsel/Reset darf keine alte Antwort in die neue Auswahl laufen.

## Tatsächlicher lokaler Teststand

155 Python/Node PASS auf Produktionsfunktionen mit kontrollierten DB/Auth/DOM-Doubles. Bestehende154 Python/Node und151 PASS, Python/JS Syntax PASS. Kein echter PostgreSQL-/Browser-/NAS-E2E, keine neue Actions-Ausführung, kein Android-Code/APK. Fachliche Bestände auf NAS nicht aus Beispieltestdaten ableiten.
