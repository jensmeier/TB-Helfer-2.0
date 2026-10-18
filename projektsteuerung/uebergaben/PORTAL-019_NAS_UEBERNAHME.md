# PORTAL-019 – NAS-Übernahme

Branch feature/tb-server-1.0, nach PORTAL-017/018. GitHub Desktop: Fetch origin/Pull origin im richtigen Branch.

## Genau fünf Runtime-Dateien kopieren

| GitHub/PC | NAS-Ziel im bestehenden Server-Quellordner |
| --- | --- |
| server/tbserver/worksites.py | tbserver/worksites.py |
| server/tbserver/catalog.py | tbserver/catalog.py |
| server/templates/v1_worksites.html | templates/v1_worksites.html |
| server/templates/v1_worksite.html | templates/v1_worksite.html |
| server/templates/v1_catalog_lookup.html (neu) | templates/v1_catalog_lookup.html |

Vorhandene Ordnerstruktur beibehalten. Bei Source-Bind-Mount API-Container neu starten, Browser StrgF5; bei Imageeinbindung Image neu bauen. Keine Migration, keine Datei/Datenbank/Storage löschen.

Voraussetzung: vorhandenes tbserver/station_reference.py und beide bisherigen JSON-Dateien im data/-Ordner, im Container /app/data read-only oder STATION_ACTIVE_PATH/STATION_OPERATING_PATH. Gleicher Bestand wie Admin-Autofill/Android. Sie werden hier nicht verändert. Bei Warnung Bahnhofsliste nicht verfügbar zuerst diese vorhandenen Mounts/Dateien prüfen. Unbekannte Strecke darf weiter manuell gespeichert werden.

Vorheriger PORTAL-018-PDF-Fix falls noch offen: server/tbserver/btb_pdf_android_style.py ebenfalls übernehmen. Bereits erzeugte PDFs ändern sich nicht automatisch.

## Verhalten und NAS-Praxistest

1. Baustellenliste als verantwortlicher TB: Löschen sichtbar. Abbrechen entfernt nichts. Nur bewusste Testbaustelle bestätigen; sie verschwindet aus aktiven Ansichten, vorhandener Softdelete/Tombstone meldet Löschung an Clients. Kein physischer Speicher-Purge; Dateien bleiben für administrative endgültige Bereinigung. Andere Nutzer dürfen nicht fremde Baustellen löschen.
2. Bezeichnung ausfüllen, Name leer lassen: Name wird abgeleitet. Nur Strecke oder Bahnhof erlaubt; komplett leere Eingabe wird abgewiesen. Entsprechend Unterbaustelle ohne Zusatznamen anlegen.
3. Bei Haupt- und Unterbaustelle mho/holzkirchen bzw. ingo eingeben und passenden Treffer anklicken: Code+Name übernommen. Keine bestimmte Kürzelzuordnung erfinden, tatsächliche Treffer verwenden.
4. Vorhandene Streckennummer suchen und Treffer auswählen: Hinweis auf Katalog/Vermessungsdaten prüfen. Nicht gefundene Nummer wird als unbekannt gekennzeichnet, nicht als ungültige reale Bahnstrecke ausgegeben.
5. Fehlende Unterbaustellenmetadaten alter Fotos sind ein eigener Restpunkt; keine automatische Altbildreparatur dieses Blocks.

## Tatsächliche Prüfungen

Check154 Python/Node auf Produktionsfunktionen mit DB/Auth/DOM-Doubles PASS (Namen, Rollen, Fremdfirma, Version409, Softdelete/Tombstone, Katalogmerge, Suchfehler, Race/Reset, Auswahl, Löschabbruch/Bestätigung). Check151 Baum/KM PASS, Syntax PASS. Kein echter DB-/Browser-/NAS-E2E, keine Actions/Android-Builds gestartet.
