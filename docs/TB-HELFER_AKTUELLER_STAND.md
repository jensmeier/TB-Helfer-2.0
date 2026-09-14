# TB-Helfer – aktueller Projektstand

Stand: 2026-09-14

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. offenen Draft-PR und neuesten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- aktueller bestätigter Android-Gerätestand: **200039**.
- aktueller Entwicklungsstand im Branch: **Android 200040 – externer Fotoimport + Fotoverwaltung**, Source zuletzt `eda1b2ce4a0eb31da7fd747ba4b4a933b2b27efe` plus diese Doku, noch nicht lokal gebaut/auf Gerät bestätigt.

## Normaler Entwicklungsablauf
Der Source of Truth liegt vollständig in GitHub. Neue Änderungen werden direkt im Feature-Branch versioniert.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200039 – gebaut, installiert, Gerätetest bestätigt
`versionCode = 200039`, `versionName = 1.3` war der letzte praktisch bestätigte Gerätestand.

Bestätigt:
- Firmenlogo im realen BTB-PDF deutlich kleiner und proportional.
- Firmenname, Firmen-E-Mail und Firmentelefon unter dem Logo konfigurierbar.
- `Kontaktdaten sind bereits im Logo enthalten` unterdrückt doppelte E-Mail-/Telefonzeilen.
- verantwortlicher TB mit persönlicher E-Mail und Mobilnummer im PDF-Kopf.
- Fußzeile `Erstellt von: <Nutzer> · <Datum>` plus Seitenzahl.
- Logo unter `Einstellungen → BTB & PDF` links/rechts/hoch/runter sowie kleiner/größer und zurücksetzen.
- echte A4-PDF-Vorschau im internen PDF-Viewer.
- Foto-Raster wird in der Vorschau angezeigt.
- Vorschau und reales BTB-PDF wurden auf dem Handy geprüft; Benutzer meldete `passt alles`.

### Server-Vorbereitung 200039
Schema 12 ist im Branch vorbereitet für ein firmenweit gespeichertes PDF-Logo-Profil:
- `server/migrations/012_company_pdf_logo_layout.sql`
- `server/app_parts/19_part.py`
- `server/checks/11_verify_schema12.sql`
- `server/checks/12_verify_schema12_source.py`
- `GET/PUT /api/v1/company/profile/pdf-logo-layout`

**Nicht produktiv migriert.** Produktiver NAS/DB-Stand bleibt Schema 10.

## Android 200040 – externer Fotoimport und Fotoverwaltung
`app/build.gradle.kts` steht im Feature-Branch auf `versionCode = 200040`, `versionName = 1.3`.

### Umgesetzt
- In der BTB-Fotoauswahl gibt es `Fotos aus Galerie / Dateien hinzufügen`.
- Android-System-Dateiauswahl (`OpenMultipleDocuments`) wird verwendet; keine breite Speicherberechtigung nötig.
- ausgewählte Bilder werden zuerst als **eigene App-Kopie** in den vorhandenen TB-Helfer-Fotoordner übernommen.
- das Original in Galerie/Dateien wird niemals durch den Import verändert oder gelöscht.
- Import wird ausdrücklich der aktuell geöffneten stabilen `btbId` zugeordnet; Datum/Uhrzeit bestimmen bei modernen BTBs nicht die Zugehörigkeit.
- vorhandene EXIF-GPS-Daten und Aufnahmezeit werden soweit vorhanden übernommen.
- fehlt GPS im externen Bild, wird intern der im Projekt bereits verwendete `0/0 = unbekannt`-Sentinel benutzt und in der Fotoansicht als `GPS: nicht im Bild vorhanden` angezeigt.
- importierte Bilder werden wie andere BTB-Fotos in Auswahl/PDF/Export geführt.
- bei bestehendem erstem Abschluss ist ein importiertes Foto automatisch ein Nachtrag und die aktuelle Abschlussfassung wird ungültig/Nachbearbeitung.
- PDF-Fotolimit bleibt wirksam; zusätzliche importierte Bilder bleiben erhalten, werden über dem Limit aber nicht automatisch ausgewählt.

### Foto-Vorschau
- Thumbnail/Text/Metadaten in der BTB-Fotoauswahl sind antippbar.
- große Bildansicht im Dialog.
- dort sichtbar: BTB/PDF-Text, Uhrzeit, KM/KM-Bereich, Firma, Objekt/Bereich, Originalfoto/Nachtrag und GPS-Status.

### Foto bearbeiten
Direkt aus der großen Ansicht:
- BTB/PDF-Text ändern.
- KM von und optional KM bis ändern.
- Firma korrigieren.
- Objekt/Gleis/Weiche korrigieren.
- Bereich korrigieren.

Text/KM verwenden weiterhin die vorhandenen BTB-spezifischen Override-Stores. Für Firma/Objekt/Bereich wurde `BtbPhotoContextOverrideStore` ergänzt. Die Korrekturen werden BTB-lokal über die stabile `btbId` gespeichert und auf Foto-Kopien angewendet; der ursprüngliche Foto-Datensatz bzw. das bereits eingebrannte Bild wird dadurch nicht überschrieben.

`FotoRepository` wendet die Kontext-Korrekturen bei den relevanten Fotoabfragen an, sodass BTB/PDF und Fotoansichten dieselben korrigierten Werte erhalten.

### Entfernen / vollständig löschen
- Originalfoto des ersten Abschlusses bleibt geschützt: nur `Aus BTB entfernen`.
- andere Fotos: `Nur aus BTB/PDF` oder `Foto vollständig löschen`.
- vollständiges Löschen entfernt den Room-Fotodatensatz, BTB-Zuordnung und Foto-Overrides.
- die zugehörige Bilddatei + Thumbnail werden nur gelöscht, wenn sie im App-eigenen Pictures-Verzeichnis liegen und kein anderer Foto-Datensatz denselben Pfad referenziert.
- ein ursprünglich aus Galerie/Dateien importiertes Original außerhalb des App-Verzeichnisses bleibt immer unverändert.
- Löschen/Import/Fotoauswahländerung invalidiert einen bestehenden Abschluss gemäß Original-/Nachbearbeitungsregel.

### Zusätzlich korrigiert
Die BTB-Fotoauswahl hielt bisher trotz vorhandener `btbId` zunächst am Schicht-Zeitfenster fest. 200040 verwendet bei modernen BTBs nun direkt die exakte `btbId`; Zeitfenster-Fallback bleibt ausschließlich für echte Legacy-BTBs ohne `btbId`.

Beim Laden des BTB-Kontexts verwendet die Fotoauswahl nun auch die kanonische `baustelleId` zusammen mit Name/BTB-ID, damit mehrere BTBs derselben Baustelle stabiler aufgelöst werden.

### Betroffene Dateien 200040
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/datenbank/FotoDao.kt`
- `app/src/main/java/de/tbhelfer/datenbank/FotoRepository.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExternalPhotoImporter.kt` (neu)
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoContextOverrideStore.kt` (neu)
- `app/src/main/res/layout/fragment_btb_pdf_auswahl.xml`

### 200040 Teststatus
Noch **nicht** als Build-/Gerätestand bestätigt.

GitHub Actions ist weiterhin kein belastbarer Buildindikator: auch der Lauf zum aktuellen 200040-Source endet vor dem ersten Workflow-Schritt (`steps=[]`). Checkout, Source-Checks und Gradle starten dort aktuell nicht.

Nächster Test auf Windows/Handy:
1. `Fetch origin` → `Pull origin`.
2. Android Studio Debug-Build 200040.
3. als Update installieren, App-Daten nicht löschen.
4. offenen BTB → Fotoauswahl → 1–2 externe Fotos importieren.
5. prüfen, dass sie sofort im exakt geöffneten BTB erscheinen, auch wenn ihre EXIF-Aufnahmezeit außerhalb der BTB-Schicht liegt.
6. Foto antippen → große Ansicht prüfen.
7. Text, KM/KM-Bereich, Firma, Objekt und Bereich ändern; PDF-Entwurf erzeugen und Korrekturen prüfen.
8. nach erstem Abschluss ein Foto importieren → `Nachtrag` und Abschluss-Invalidierung prüfen.
9. Nachtrag `Nur aus BTB/PDF` und separat `Foto vollständig löschen` prüfen.
10. geschütztes Originalfoto darf weiterhin nicht endgültig gelöscht werden.

## Foto-Overlay – nächster Teil desselben Foto-Blocks
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos bereits Informationen dauerhaft in die App-JPG-Datei ein. Vorhandene Felder umfassen u. a. Baustelle/Unterbaustelle, Strecke/Bahnhof, Gleis/Weiche/Objekt, KM, GPS, Bereich und Datum/Uhrzeit.

Noch offen nach dem 200040-Grundtest:
- auf Pixel/Poco praktisch prüfen, ob Overlay bei normalen BTB-Kamera-Fotos tatsächlich sichtbar und lesbar eingebrannt wird;
- Größe/Position/Kontrast auf echten Baustellenfotos beurteilen;
- nur bei echtem Bedarf Renderer optisch nachschärfen; keine parallele zweite Overlay-Lösung bauen.

## Bestätigter BTB-Kernstand 200035–200039
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität.
- mehrere BTBs desselben Teams am selben Kalendertag möglich.
- erster erfolgreicher Abschluss = unveränderliches Original.
- spätere fachliche Änderungen gegen dieses Original; neue/geänderte Inhalte rot, entfernte Originalinhalte rot `(entfernt)`.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter bleiben PDF-Kopfdaten und werden nicht rot markiert.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- moderne BTB-Fotos strikt nach derselben `btbId`; Legacy-Zeitfenster nur ohne `btbId`.
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original.
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert werden.
- 24h-TimePicker für BTB-Zeit, Personal und Geräte/Maschinen.
- alte abgeschlossene BTBs lassen sich bewusst wieder öffnen und danach erneut abschließen.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- Secrets ausschließlich in `./server/.env`; keine Geheimwerte in GitHub/Doku/Logs.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; 72 OpenAPI-Pfade, Schema 10.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 persönliche Nutzerkontakte: vorbereitet, nicht produktiv migriert.
- Schema 12 PDF-Logo-Layout: vorbereitet, nicht produktiv migriert.
- vor produktiven Schema-11-/Schema-12-Schritten frisches verifiziertes DB-Backup erstellen.

## Teams / Baustellen / Sync – weiter offen
- Zusammenarbeit nur innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs gemäß Projektregeln baustellenweit.
- Zwei-Geräte-/Mehrhelfer-Synchronisation und Konflikttests bleiben offen.
- vollständiger Unterbaustellen-/Streckendaten-Sync bleibt offen.
- serverseitiges Entfernen/Verlassen bleibt offen.

## Danach geplante Handy-Arbeiten
Nach erfolgreichem 200040-Test:
1. Foto-Overlay auf echten Kamera-Fotos praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss weiterhin keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene Abschlussprüfungen älterer BTB-Runde
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren.
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren.
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
