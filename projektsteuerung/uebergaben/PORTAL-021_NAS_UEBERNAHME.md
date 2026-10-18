# PORTAL-021 – Freie Strecke / Gleis oder Weiche

Voraussetzung PORTAL-020. GitHub Desktop im Branch feature/tb-server-1.0: Fetch/Pull.

## Drei Runtime-Dateien

| GitHub/PC | Im vorhandenen NAS-Serverordner |
| --- | --- |
| server/tbserver/worksites.py | tbserver/worksites.py |
| server/templates/v1_worksite.html | templates/v1_worksite.html |
| server/templates/v1_catalog_lookup.html | templates/v1_catalog_lookup.html |

API-Container neu starten, StrgF5. Bei Imageeinbindung Image neu bauen. Keine Migration, nichts löschen.

## Verhalten/Praxistest

- Freie Strecke: Bahnhof ausgeblendet und geleert, nicht gespeichert; Streckensuche/Bestandsübersicht weiterhin vorhanden.
- Bahnhof: Bahnhofsfeld/Suche verfügbar. Gleis, Weiche oder Weichenverbindung auswählen, Nummer eingeben. Bestand setzt Objektart automatisch.
- Von Bahnhof auf Freie Strecke wechseln: vorheriger Bahnhof, Objekt und KM-Felder verworfen; alte Suchantworten dürfen nicht wieder erscheinen.
- Alte gespeicherte freie Strecke mit falschem Bahnhof: beim Bearbeiten/Speichern werden Bahnhofscode/-name bereinigt. Kein automatischer Eingriff in den Altbestand.
- Namensableitung/Nummern: Gleis19 bleibt19, Weiche117 wird W117 mit lesbarem Leerzeichen, WVB entsprechend. Bereits vorhandene Felder bleiben kompatibel, object_type kommt im Payload hinzu.

## Tatsächliche Tests

156 Python/Node auf echten Produktionsfunktionen mit DB/Auth/DOM-Doubles PASS.154/155 Python/Node sowie151 PASS, Syntax PASS. Kein echter PostgreSQL-/Browser-/NAS-E2E. Keine neue Actions-Ausführung, kein Android-Code/APK.
