# TB-Helfer – aktueller Projektstand

Stand: 2026-09-15

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
- letzter vollständig bestätigter Android-Gerätestand: **200039**.
- aktueller Entwicklungsstand im Branch: **Android 200042 – kompakte Fotoaktion pro Arbeitseintrag + größere/antippbare Logo-Vorschau**.
- 200040/200041 bilden die technische Grundlage für externen Fotoimport und fachliche Zuordnung zu einem Arbeitseintrag; 200042 räumt die Bedienung entsprechend dem Gerätefeedback auf.

## Normaler Entwicklungsablauf
Der Source of Truth liegt vollständig in GitHub. Neue Änderungen werden direkt im Feature-Branch versioniert.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200042 – aktueller Entwicklungsstand
`app/build.gradle.kts` steht auf `versionCode = 200042`, `versionName = 1.3`.

### Kompakte Fotoaktion pro Arbeitseintrag
Das Gerätefeedback zu 200041 war eindeutig: nicht noch einen großen Importbereich aufbauen, sondern Platz sparen und direkt am vorhandenen Arbeitseintrag arbeiten.

Umgesetzt:
- der große globale Button `Allgemeine Fotos aus Galerie / Dateien` samt Erklärung wurde aus der BTB-Fotoauswahl entfernt;
- die vorhandene Foto-Liste mit Thumbnail, Metadaten, Häkchen, Bearbeiten sowie Entfernen/Löschen bleibt unverändert erhalten;
- bei den erkannten Arbeitseinträgen steht nur noch eine kompakte **`📷`-Aktion**;
- Tap auf `📷` öffnet genau zwei Quellen:
  - `Kamera`
  - `Galerie & Dateien`.

`Galerie & Dateien` verwendet weiterhin den bereits vorhandenen Importpfad aus 200041:
- eigene App-Kopie;
- externe Originaldatei bleibt unverändert;
- stabile `btbId`;
- Firma;
- Ort/Unterbaustelle;
- Arbeitstext;
- KM/KM-Bereich;
- Eintragszeit als BTB-Zeit-Override;
- EXIF-GPS/Aufnahmezeit soweit vorhanden.

### Kamera direkt aus dem Arbeitseintrag
Für `📷 → Kamera` wird **keine zweite Kamera-Lösung** gebaut. Die bestehende TB-Helfer-Kamera wird wiederverwendet.

Vor dem Wechsel zur Kamera:
- aktuelle Fotoauswahl wird gespeichert;
- der Arbeitseintrag ist bereits über den gespeicherten BTB/Markerbestand definiert;
- Firma und Kurztext werden in den vorhandenen Kamera-/Foto-Vorschlagswerten vorbelegt;
- die zugehörige Unterbaustelle wird vorübergehend als aktueller Kamerakontext gesetzt;
- die bereits vorhandenen Foto-IDs des BTB werden gemerkt.

Nach Rückkehr aus der Kamera:
- nur neu entstandene Fotos derselben stabilen `btbId` **und des Arbeitseintrag-Bildtyps `FOTODOKUMENTATION`** werden betrachtet; vorhandene Fotos anderer BTB-Abschnitte werden ausdrücklich nicht angefasst;
- diese neuen Fotos werden nochmals eindeutig auf den ausgewählten Arbeitseintrag korrigiert:
  - Firma;
  - Ort/Unterbaustelle;
  - Arbeitstext;
  - KM bzw. KM-Bereich;
- die vorherige globale Unterbaustellen-Auswahl wird wiederhergestellt;
- neu entstandene Fotos werden bis zum PDF-Fotolimit automatisch ausgewählt.

Wichtig: Die eigentliche Kameraaufnahme behält ihre echte GPS-/Aufnahmezeit und den vorhandenen eingebrannten Kamera-Overlay-Pfad. Für die fachliche BTB/PDF-Zuordnung werden vorhandene Override-Speicher wiederverwendet; die Original-JPG-Datei wird dafür nicht nachträglich umgeschrieben.

### Logo / PDF 200042
Das Firmenlogo darf jetzt deutlich größer eingestellt werden:
- lokaler Bereich in `BtbPdfLogoLayoutSpeicher`: **40–250 pt** statt bisher maximal 180 pt;
- rechter PDF-Kopfbereich wurde vergrößert und das Logo wird proportional und standardmäßig mittig eingepasst;
- ohne Logo bleibt der reservierte Bereich im fertigen PDF weiß, es wird kein Platzhalter gedruckt;
- Schema-12-Migration und vorbereitete Server-API akzeptieren ebenfalls bis 250 pt, damit lokal/serverseitig dieselbe Grenze gilt;
- **Schema 12 ist weiterhin nicht produktiv migriert**.

Unter `Einstellungen → BTB & PDF` gibt es jetzt zusätzlich eine antippbare Logo-Karte:
- vorhandenes Firmenlogo wird direkt angezeigt;
- Tap auf die Logo-Fläche öffnet dieselbe echte PDF-Vorschau wie der bisherige Vorschau-Button;
- ohne hinterlegtes Logo wird erklärt, dass die PDF-Vorschau einen weißen Logobereich zeigt.

### Betroffene Dateien 200042
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt`
- `app/src/main/res/layout/fragment_btb_pdf_auswahl.xml`
- `app/src/main/java/de/tbhelfer/ui/einstellungen/btbpdf/BtbPdfEinstellungenFragment.kt`
- `app/src/main/res/layout/fragment_btb_pdf_einstellungen.xml`
- `app/src/main/java/de/tbhelfer/einstellungen/BtbPdfLogoLayoutSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfHeaderRenderer.kt`
- `server/migrations/012_company_pdf_logo_layout.sql`
- `server/app_parts/19_part.py`
- diese Datei.

### Tests / Prüfung 200042
- GitHub-Stand, Projektregeln, aktueller Stand, Draft-PR #5 und letzter relevanter Commit wurden vor der Änderung geprüft.
- Quellcodepfade für `btbId`, Fotoimport, Foto-Overrides, bestehende Kamera/Foto-Maske sowie Logo/PDF-Vorschau wurden vor der Änderung nachvollzogen.
- statisch geprüft: der alte Layout-Verweis auf `btnImportPhotos` wurde zusammen mit dem Button entfernt; der Importpfad wird jetzt nur noch vom konkreten Arbeitseintrag aus gestartet.
- statisch geprüft: Kameraweg nutzt den vorhandenen `FotoRepository.einfuegen(...)`-Pfad, der bei aktivem modernen BTB bereits die stabile aktive `btbId` zuordnet; nach Rückkehr wird zusätzlich nur innerhalb derselben `btbId` und des Bildtyps `FOTODOKUMENTATION` auf den Arbeitseintrag korrigiert.
- ein vollständiger Android-Gradle-Build konnte in dieser Sitzung nicht ausgeführt werden; **Android-Studio-Build und Gerätetest 200042 stehen aus**.
- GitHub Actions wurde für den 200042-Dokumentationsstand erneut geprüft: Workflow `Android CI` endete weiterhin als `failure` ohne ausführbare Job-Schritte/Runner und ist daher kein Buildindikator.

### Nächster Gerätetest 200042
1. GitHub Desktop `Fetch origin` → `Pull origin`.
2. Android Studio Debug-Build 200042 starten und als Update installieren; App-Daten nicht löschen.
3. BTB → Abschnitt 4 → Fotoauswahl öffnen.
4. prüfen: kein großer allgemeiner Importblock mehr; Arbeitseinträge zeigen kompakt `📷`.
5. `📷` drücken → Dialog muss `Kamera` / `Galerie & Dateien` zeigen.
6. `Galerie & Dateien`: Bild auswählen und Firma, Unterbaustelle, KM, Text sowie Zuordnung zum richtigen BTB prüfen.
7. `Kamera`: Foto aufnehmen/speichern, zur BTB-Fotoauswahl zurückgehen und prüfen, dass dasselbe Foto dem gewählten Arbeitseintrag zugeordnet ist.
8. PDF-Entwurf erzeugen und Gruppierung/Firma/Ort/KM prüfen.
9. `Einstellungen → BTB & PDF`: Logo bis über die bisherige Grenze vergrößern; Logo-Karte antippen und echte PDF-Vorschau prüfen.
10. ohne Logo bzw. testweise fehlendem Logo: rechter PDF-Bereich muss weiß bleiben.
11. Originalfoto-/Nachtrag-/Löschschutz kurz gegenprüfen.

## Android 200039 – gebaut, installiert, Gerätetest bestätigt
`versionCode = 200039`, `versionName = 1.3` war der letzte vollständig praktisch bestätigte Gerätestand.

Bestätigt:
- Firmenlogo im realen BTB-PDF proportional.
- Firmenname, Firmen-E-Mail und Firmentelefon unter dem Logo konfigurierbar.
- `Kontaktdaten sind bereits im Logo enthalten` unterdrückt doppelte E-Mail-/Telefonzeilen.
- verantwortlicher TB mit persönlicher E-Mail und Mobilnummer im PDF-Kopf.
- Fußzeile `Erstellt von: <Nutzer> · <Datum>` plus Seitenzahl.
- Logo unter `Einstellungen → BTB & PDF` links/rechts/hoch/runter sowie kleiner/größer und zurücksetzen.
- echte A4-PDF-Vorschau im internen PDF-Viewer.
- Foto-Raster wird in der Vorschau angezeigt.
- Vorschau und reales BTB-PDF wurden auf dem Handy geprüft; Benutzer meldete `passt alles`.

### Server-Vorbereitung Logo
Schema 12 ist im Branch vorbereitet für ein firmenweit gespeichertes PDF-Logo-Profil:
- `server/migrations/012_company_pdf_logo_layout.sql`
- `server/app_parts/19_part.py`
- `server/checks/11_verify_schema12.sql`
- `server/checks/12_verify_schema12_source.py`
- `GET/PUT /api/v1/company/profile/pdf-logo-layout`

**Nicht produktiv migriert.** Produktiver NAS/DB-Stand bleibt Schema 10.

## Android 200040/200041 – Fotoimport-Grundlage
200040 führte externen Import und Fotoverwaltung ein:
- Android-Systemauswahl `OpenMultipleDocuments`;
- externe Bilder als eigene App-Kopie, Galerie-/Datei-Original unverändert;
- Zuordnung zur stabilen `btbId` statt über Datum/Uhrzeit;
- EXIF-GPS/Aufnahmezeit soweit vorhanden;
- große Fotoansicht und BTB/PDF-Korrekturen für Text, KM, Firma, Objekt/Bereich;
- Schutz des Originalfotos aus dem ersten Abschluss;
- Nicht-Original: `Nur aus BTB/PDF` oder sichere vollständige App-Löschung;
- Kontextkorrekturen über `BtbPhotoContextOverrideStore`.

200041 ergänzte die konkrete fachliche Zielzuordnung pro Arbeitseintrag:
- Parser für bestehende Marker `@ORT`, `@ALLGEMEIN`, `@KM`, Firma, Zeit und Arbeitstext;
- `BtbExternalPhotoImporter` kann Firma, `baustellenKnotenId`, Text und KM des Ziel-Arbeitseintrags übernehmen;
- Eintragszeit und KM-Bereich über vorhandene BTB-Override-Speicher;
- bereits falsch unter Allgemein gelandete Fotos können über `Bearbeiten → Arbeitseintrag / Ort` korrigiert werden;
- `BtbPhotoContextOverrideStore` Version 2 kann einen BTB-lokalen `ortId` speichern und liest ältere Version-1-Dateien weiter.

Der erste 200041-Build fand 7 Kotlin-Compilerfehler durch die lokale Variable `text` in `BtbPdfAuswahlFragment.kt`; dieser Shadowing-Fehler wurde im Branch durch `etText` behoben. Ein erneuter vollständiger Android-Studio-Build war vor Beginn von 200042 noch offen.

## Foto-Overlay
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos bereits Informationen dauerhaft in die App-JPG-Datei ein. Vorhandene Felder umfassen u. a.:
- Baustelle/Unterbaustelle;
- Strecke/Bahnhof;
- Gleis/Weiche/Objekt;
- KM;
- GPS;
- Bereich;
- Datum/Uhrzeit.

Noch offen:
- auf Pixel/Poco praktisch prüfen, ob Overlay bei normalen BTB-Kamera-Fotos sichtbar und lesbar eingebrannt wird;
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
Nach erfolgreichem 200042-Test:
1. Foto-Overlay auf echten Kamera-Fotos praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss weiterhin keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene Abschlussprüfungen älterer BTB-Runde
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren.
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren.
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
