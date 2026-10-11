# PORTAL-009 – große Bilderbestände
Stand: 12.10.2026 (Berlin). SERVER-Branch feature/tb-server-1.0, PR6 Draft.

## Verhalten
- Galerie: maximal 60 Kacheln gleichzeitig, vor/zurück, serverseitige Datum-/Bereich-/Textsuche.
- Neue Änderungen werden angekündigt. Ein Klick aktualisiert die Ansicht.
- Mängel/Abnahmen: Fotos erst auf Klick, maximal zwölf Vorschauen; Link zur zugehörigen Galerie.
- Originaldateien werden nicht für die Übersicht geladen. Bestehender vollständiger Medienkatalog für Android/Export bleibt erhalten.
- Keine Migration; Schema23 bleibt. Öffentliche Seite, Backup/Restore und Android unverändert.

## Übernahme per GitHub Desktop und File Station
Voraussetzung: vorheriger Portalstand mit Migration23 installiert.
1. In GitHub Desktop feature/tb-server-1.0 aktualisieren.
2. API im Synology Container Manager stoppen.
3. Unter /volume1/docker/tb-helfer-v1 diese vier Dateien kopieren:
   - server/tbserver/media_gallery.py → tbserver/media_gallery.py (neu)
   - server/tbserver/files.py → tbserver/files.py
   - server/templates/v1_portal_pictures.html → templates/v1_portal_pictures.html
   - server/templates/v1_worksite.html → templates/v1_worksite.html
4. API starten, Browser Strg+F5.
.env, data, storage und backups nicht ersetzen. Kein komplettes Projektverzeichnis überkopieren.

## Sichtprüfung auf dem NAS
- Bilder öffnen, nächste/vorherige Seite und Suche ausprobieren.
- Mangel/Abnahme öffnen: erst „Fotos anzeigen“, dann „Alle zugehörigen Fotos“.
- Mit Helferkonto prüfen, dass nur freigegebene Bilder erscheinen.
Die CI verwendet 10.000 Metadatensätze und einen Browser mit simulierten Antworten. Sie ersetzt keine Laufzeitmessung mit 10.000 echten Fotos auf dem NAS.

## Prüfstand
Commit 06d223581c905ec72cc47581868513923beb9bd5
Portal-CI38181633106 erfolgreich: PostgreSQL mit10.000 Einträgen und Chromium1440/390 einschließlich Fotos auf Abruf. Bestehende Foundation-/Android-CI-Fehler sind damit nicht behoben.
