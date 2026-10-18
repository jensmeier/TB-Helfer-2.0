# PORTAL-017 – Eintragsfotos, GPS und automatische Nachtschicht

18.10.2026. Voraussetzung: PORTAL-016 auf der NAS; Serverbranch `feature/tb-server-1.0`. Android unverändert.

## Genaue NAS-Dateiliste

GitHub Desktop aktualisieren. Über File Station unter `/volume1/docker/tb-helfer-v1` in die passenden Unterordner kopieren:

| Repository | NAS-Unterpfad | Hinweis |
| --- | --- | --- |
| server/tbserver/btb.py | tbserver/btb.py | ersetzen |
| server/tbserver/btb_entry_photos.py | tbserver/btb_entry_photos.py | neu |
| server/tbserver/btb_photo_meta.py | tbserver/btb_photo_meta.py | ersetzen |
| server/tbserver/btb_documents.py | tbserver/btb_documents.py | ersetzen |
| server/tbserver/files.py | tbserver/files.py | ersetzen |
| server/tbserver/media_gallery.py | tbserver/media_gallery.py | ersetzen |
| server/tbserver/btb_pdf_android_style.py | tbserver/btb_pdf_android_style.py | ersetzen |
| server/templates/v1_btb.html | templates/v1_btb.html | ersetzen |
| server/templates/v1_photo_upload.html | templates/v1_photo_upload.html | ersetzen |
| server/templates/v1_photo_exif.html | templates/v1_photo_exif.html | neu |
| server/templates/v1_worksite.html | templates/v1_worksite.html | ersetzen |

Alle elf Dateien gemeinsam übernehmen. API-Container im Container Manager neu starten, Browser Strg+F5. Bei reinem Imagebetrieb ohne Quellordnereinbindung das Image aus diesem Quellstand neu erstellen. Keine Migration, nichts löschen; `.env`, Compose, bestehende Bilder/Backups und bestätigte PORTAL-015-Funktionen erhalten. `btb_times.py` und `v1_btb_times.html` aus PORTAL-016 bleiben erforderlich und wurden in PORTAL-017 nicht geändert.

## Praxistest

1. Arbeitstag 18.10.2026, Beginn19:00/Ende07:00, speichern. Keine zwei zusätzlichen Datumsfelder. Anzeige18/19.10.26; Personal/Maschinen erhalten19:00–07:00 nach bestehender Vererbungsregel. Ebenso31.10.→01.11. und31.12.→01.01. prüfen. Gleiche Uhrzeiten werden nicht ungefragt als24h interpretiert. Historische Zeiträume länger24h werden beim Speichern unverändert bewahrt; Änderung über die vereinfachte Oberfläche wird zum Schutz abgewiesen.
2. Arbeit mit Firma/Unterbaustelle anlegen und Foto direkt hinzufügen. Vorschaubild unter dem konkreten Eintrag, nach erneutem Bearbeiten dasselbe Bild im Dialog. Weitere Fotos möglich. Zusätzliche Zuordnungsfelder sind dort nicht nötig. Der optionale Upload ohne Arbeitseintrag unten ist eingeklappt.
3. Foto bearbeiten: Kilometer ändern; GPS als Breiten-/Längengrad oder gemeinsam `47.59699, 10.253655` / `47,59699;10,253655` eingeben. Vollständige Google-Maps-URLs mit Koordinaten in `@`, `q` oder `query` werden erkannt; verkürzte Links ohne Koordinaten nicht aufgelöst. Beide GPS-Felder leeren und speichern entfernt die BTB-lokalen Koordinaten auch aus Galerie/PDF; keine alten Datei-GPS nachladen.
4. Alternativ lokal ein Originalbild bei „GPS aus Originalbild lesen“ auswählen. Nur Metadaten lesen, kein Originalupload. Fehlen GPS, bestehende Werte erhalten und Hinweis anzeigen. Bei neuen Uploads GPS/Aufnahmezeit automatisch vor Verkleinerung lesen; Bild bleibt maximal2.000.000Bytes. JPEG/TIFF-EXIF, PNG-eXIf und WebP-EXIF werden unterstützt, andere/unlesbare Metadaten bleiben leer. Ohne EXIF-Zeitzonenoffset wird Aufnahmezeit als Europe/Berlin interpretiert; Dateibearbeitungszeit wird nicht als Aufnahmezeit ausgegeben.
5. „Bearbeiten / Verschieben“ am Foto: anderen Eintrag wählen und speichern. Foto dort anzeigen. Abschnitt/Firma/Unterbaustelle/Beschriftung kommen vom Ziel. KM/GPS bleiben fotoeigene Angaben. Eintragsabschnitt oder Firma/Unterbaustelle/Text ändern: gebundene Fotos folgen. Firmenname ändern: gebundene Fotometadaten folgen. Bestehende Foto-Originaldatei und andere BTB-Verknüpfungen werden nicht umgeschrieben.
6. „Aus Eintrag entfernen“ löst nur die Eintragszuordnung, das Bild bleibt im BTB und seiner Bilderübersicht. Der bestehende separate „Entfernen“-Knopf in der BTB-Fotogesamtansicht löst nach Bestätigung das Foto aus dem BTB und löscht nicht die Originaldatei. Eintrag löschen löst seine Fotozuordnungen ebenfalls, Bilder bleiben im BTB. Keine automatische Massen-/Dateilöschung.
7. Alte Fotos ohne feste Referenz bleiben ungebunden. Manuell im Fotoeditor Ziel-Eintrag wählen; keine automatische Zuordnung allein anhand gleicher Beschriftungen/GPS. PDF neu erzeugen:18/19.10.26,19:00–07:00, Bildnummer am gebundenen Arbeitseintrag und bearbeitete GPS im Fotoanhang. Kein zweiter künstlicher Arbeitseintrag für dasselbe gebundene Bild. Bestehende Abschluss-PDFs werden nicht pauschal neu gerendert.

## Vertrag und Tests

`UploadComplete.btb_entry_id` validiert Eintrag desselben BTB, BTB-Bearbeitungsrecht und bestehenden Upload-/Teilnehmerschutz. Referenz ausschließlich in BTB-file_links.link_metadata.btbEntryId; Foto-PATCH nutzt bestehende link_version. Abschnitt/Firma/Ort/Text-Folgeänderung und Entkopplung beim Eintragslöschen in bestehender BTB-Transaktion, versionierte FILE-Events. GPS-Overrides ausschließlich pro BTB-Link, endliche Werte/Bereiche/Paar validiert; effektive Werte für BTB/Galerie/PDF. Kein Schema-/Migrationsumbau und keine automatische Altdatenreparatur.

Lokal tatsächlich ausgeführt: neuer Check153(Python+Node), bestehende Checks152/151. Produktionsfunktionen mit kontrollierten Cursor-/API-/DOM-Doubles; fremde/fehlende Eintragsreferenzen, Link-Versionkonflikt, Verschieben/Entkoppeln, GPS-Korrektur/Löschen/Isolation, Snapshot-/PDF-GPS ohne alten Fallback, GPS-Paar/Maps-Parsing, EXIF Little/Big Endian/JPEG/PNG/WebP/beschädigte Daten, Nacht-/Monats-/Jahreswechsel. Geänderte Python- und JavaScript-Syntax geprüft. Zweitseitige PDF tatsächlich gerendert, Text/Datum/Bildreferenz/GPS geprüft und PNGs visuell kontrolliert.

Kein echter PostgreSQL-/FastAPI-/Chromium-/NAS-E2E dieses Blocks. Lokale Pakete und Browserbinary fehlen wie dokumentiert; keine teuren Actions ausgelöst ([skip ci], CI-COST-001). Android-Modell/Anzeige/Speichern/PDF der neuen Referenz und GPS-Overrides später separat prüfen; kein APK erstellt. Die schon bekannten fehlenden Unterbaustellen alter Fotos werden durch dieses Update nicht automatisch repariert.
