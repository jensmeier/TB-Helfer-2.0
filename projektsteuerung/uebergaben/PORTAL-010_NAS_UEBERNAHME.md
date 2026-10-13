# PORTAL-010 – Bildbeschriftungen und GPS
Voraussetzung: PORTAL-009 bereits installiert (vom Nutzer bestätigt).
Branch: feature/tb-server-1.0.

## Übernahme
1. GitHub Desktop: Branch aktualisieren.
2. Im Synology Container Manager API stoppen.
3. Über File Station unter /volume1/docker/tb-helfer-v1 zwei Dateien ersetzen:
   - server/tbserver/media_gallery.py → tbserver/media_gallery.py
   - server/templates/v1_portal_pictures.html → templates/v1_portal_pictures.html
4. API starten; Browser Strg+F5.

Keine Migration und keine Löschung. .env, data, storage und backups nicht ersetzen.

## Sichtbares Verhalten
Beschriftung statt codiertem technischem Namen. Ein Klick auf die Beschriftung klappt vorhandene Angaben zu Objekt, Bereich, Kilometer, Notiz und Infos auf. Der Dateiname bleibt dort zur Zuordnung erhalten.
Gültiges GPS: „GPS auf Karte öffnen“ führt zu OpenStreetMap; Koordinaten stehen darunter. Ohne gespeicherte/decodierbare Position erscheint „Keine GPS-Daten vorhanden“.
Strukturierte Metadaten haben Vorrang vor alten BTBP1-/BTBPHOTO-Dateinamen. Mangel-/Abnahmetitel dient bei fehlender Bildbeschriftung als Fallback, nur aus sichtbarer Verknüpfung.
Die Galerie lädt weiterhin maximal60 Kacheln pro Seite und nur Vorschaubilder.

## Grenzen
Fehlende Metadaten lassen sich nicht aus dem Bildmotiv zuverlässig rekonstruieren. Bestehende Such-/Datumsfilter verwenden weiterhin die gespeicherten SQL-Felder; ausschließlich im Altdateinamen codierte Angaben sind damit nicht automatisch als Klartext durchsuchbar. Keine Änderung an Android, ZIP, Originaldateien oder dem vollständigen Exportkatalog.

Prüfstand: 7b3c5be991a2d7220885616439f614531fe7d404. Portal-CI38249400190 erfolgreich.
