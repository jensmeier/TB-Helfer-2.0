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
- **Android 200040** wurde auf dem Handy praktisch begonnen zu testen: externer Fotoimport funktioniert grundsätzlich, dabei wurde aber die fachliche Zuordnungs-Lücke sichtbar (generisch importierte Bilder hatten keine Firma/Unterbaustelle/Arbeitseintrag-Zuordnung und landeten unter Allgemein/Hauptbaustelle).
- aktueller Entwicklungsstand im Branch: **Android 200041 – externer Fotoimport gezielt pro Arbeitseintrag**. Beim ersten lokalen Build wurden 7 Kotlin-Compilerfehler in `BtbPdfAuswahlFragment.kt` gefunden; die Ursache war eine lokale Variable `text`, die die `text`-Property nachfolgender Views überschattete. Fix ist im Branch, erneuter lokaler Build steht aus.

## Normaler Entwicklungsablauf
Der Source of Truth liegt vollständig in GitHub. Neue Änderungen werden direkt im Feature-Branch versioniert.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200039 – gebaut, installiert, Gerätetest bestätigt
`versionCode = 200039`, `versionName = 1.3` war der letzte vollständig praktisch bestätigte Gerätestand.

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

## Android 200040 – externer Fotoimport / Fotoverwaltung
200040 führte den ersten externen Import- und Verwaltungsblock ein:
- Android-Systemauswahl `OpenMultipleDocuments`.
- externe Bilder werden als eigene App-Kopie übernommen; Original in Galerie/Dateien bleibt unverändert.
- Zuordnung zur stabilen `btbId` statt über Datum/Uhrzeit.
- EXIF-GPS/Aufnahmezeit soweit vorhanden übernommen.
- große Fotoansicht und BTB/PDF-Korrekturen für Text, KM, Firma, Objekt/Bereich.
- Originalfoto des ersten Abschlusses geschützt.
- Nicht-Original: `Nur aus BTB/PDF` oder sichere vollständige App-Löschung.
- Kontextkorrekturen über `BtbPhotoContextOverrideStore`.

### Gerätefeedback 200040 / gefundener Fehler
Der Benutzer testete den neuen Import auf dem Handy und lieferte Screenshots. Ergebnis:
- Import selbst funktioniert.
- der generische Import hatte aber keine fachliche Information, **zu welchem konkreten Arbeitseintrag** das Bild gehört.
- importierte `FotoEntity` hatte bisher `baustellenKnotenId = null`, `firma = ""`, `km = null` und als Text nur den Dateinamen.
- deshalb landeten solche Bilder im Abschnitt 4 fachlich unter **Allgemein / Hauptbaustelle**, obwohl z. B. ein Eintrag `bbg → Querformat (KM 5,963) → Test1.0` gemeint war.
- außerdem zeigte die Bedienung, dass ein eindeutiger Foto-Plus-Ablauf **pro Arbeitseintrag** sinnvoller ist als nur ein globaler Importknopf.

Das ist kein Datenbankfehler und keine Serverfrage, sondern eine fehlende fachliche Zielzuordnung im Android-Import.

## Android 200041 – Fotoimport pro Arbeitseintrag
`app/build.gradle.kts` steht auf `versionCode = 200041`, `versionName = 1.3`.

### Fachliche Lösung
Für Abschnitt 4 werden die bereits vorhandenen manuellen BTB-Arbeitseinträge aus der bestehenden Marker-Syntax gelesen:
- `@ORT=<BaustellenKnotenId>`
- `@ALLGEMEIN`
- `@KM=<von>[-<bis>]`
- Firma
- optionale Uhrzeit / Zeitspanne
- Eintragstext.

Daraus werden konkrete Foto-Ziele gebildet. Beispiel:
`bbg · Querformat (KM 5,963) · Test1.0`.

Zu jedem solchen Eintrag zeigt die Fotoauswahl eine sichtbare **`Foto +`-Aktion mit Bildsymbol**. Mehrere Bilder können direkt diesem Eintrag zugeordnet werden.

Beim gezielten Import werden übernommen:
- stabile aktuelle `btbId`;
- Firma des Arbeitseintrags;
- Ort/Unterbaustelle (`baustellenKnotenId`);
- Eintragstext als BTB-Fototext;
- KM bzw. KM-Bereich des Eintrags/Unterbaustellenknotens;
- vorhandene Eintragszeit als BTB-Zeit-Override.

Die externe Quelldatei selbst bleibt unverändert. GPS/EXIF der App-Kopie werden weiterhin soweit vorhanden beibehalten.

### Allgemeiner Import bleibt bewusst möglich
Der obere globale Button ist jetzt eindeutig beschriftet:
`Allgemeine Fotos aus Galerie / Dateien`.

Hinweistext erklärt:
- für einen konkreten Arbeitseintrag `Foto +` verwenden;
- allgemeine Fotos bleiben bewusst unter Hauptbaustelle/Allgemein;
- Galerie-/Datei-Original bleibt unverändert.

Die Fotozeilen/Eintragsaktionen sind im Auswahlblock standardmäßig sichtbar; der Benutzer muss den Block für die `Foto +`-Aktionen nicht erst aufklappen.

Die Abschnittskarte zeigt bei externen Fotos nicht mehr einen potenziell irreführenden Roh-Zeitbereich aus EXIF-Zeiten verschiedener Aufnahmetage, sondern stabil `N Arbeitseinträge · M Fotos` bzw. die Fotoanzahl. Einzelne Fotozeiten bleiben in den Fotozeilen sichtbar.

### Bereits falsch unter Allgemein gelandete Fotos korrigieren
Die Foto-Bearbeitung enthält zusätzlich `Arbeitseintrag / Ort`:
- vorhandenen Arbeitseintrag auswählen;
- dadurch werden Firma, Ort/Unterbaustelle, Text und KM passend vorbelegt/zugeordnet;
- `BtbPhotoContextOverrideStore` kann dafür jetzt zusätzlich einen BTB-lokalen `ortId` speichern.

Damit kann ein bereits unter Allgemein gelandetes Foto nachträglich z. B. `bbg → Querformat (KM 5,963)` zugeordnet werden, ohne die ursprüngliche Bilddatei oder einen unveränderlichen Originalabschluss zu überschreiben.

### Technische Entscheidungen 200041
- **keine Datenbankmigration**: bestehende BTB-Marker und bestehende Foto-Override-Dateien werden erweitert/verwendet.
- `BtbPhotoContextOverrideStore` Version 2 liest ältere Version-1-Dateien weiterhin; fehlender `ortId` bedeutet keine Ortsüberschreibung.
- `ortId = 0` bedeutet Hauptbaustelle; `ortId > 0` ist eine `BaustellenKnotenId`.
- moderne Fotozugehörigkeit bleibt strikt `btbId`-basiert.
- Originalfoto-Schutz und Nachbearbeitungs-/Abschluss-Invalidierung bleiben unverändert wirksam.
- kein NAS-/Server-/Schema-Schritt für 200041.

### Betroffene Dateien 200041
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbManualEntryPhotoTargetParser.kt` (neu)
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExternalPhotoImporter.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoContextOverrideStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt`
- `app/src/main/res/layout/fragment_btb_pdf_auswahl.xml`
- `app/src/main/res/layout/item_btb_pdf_block.xml`
- `app/src/test/java/de/tbhelfer/ui/bautagebuch/BtbManualEntryPhotoTargetParserTest.kt` (neu)

### Tests / Prüfung 200041
- Parser-Helfer wurde separat mit lokalem Kotlin-Compiler gegen die wesentlichen Markerfälle geprüft:
  - Firma + `@ORT` + Uhrzeit + einzelne KM;
  - `@ALLGEMEIN` → Hauptbaustelle;
  - Groß/Kleinschreibung der Firma + Zeitspanne + KM-Bereich.
- derselbe Parser ist zusätzlich als JUnit-Test im Repository dokumentiert.
- **erster Android-Studio-Build am 15.09.2026:** `:app:compileDebugKotlin` scheiterte mit 7 Fehlern in `BtbPdfAuswahlFragment.kt` (`val cannot be reassigned` / `String` statt `EditText`). Ursache war ausschließlich die lokale Variable `val text`, die in nachfolgenden `TextView/MaterialButton.apply { text = ... }`-Blöcken deren `text`-Property überschattete.
- **Fix:** Eingabefeld in `etText` umbenannt und alle zugehörigen Zugriffe angepasst. Die betroffenen 7 Compilerstellen sind damit aufgelöst; erneuter vollständiger Android-Studio-Build steht noch aus.
- GitHub Actions ist weiterhin kein belastbarer Buildindikator, weil Jobs vor dem ersten Workflow-Schritt ohne Runner (`steps=[]`) beendet wurden.

### Nächster Gerätetest 200041
1. GitHub Desktop `Fetch origin` → `Pull origin`.
2. Android Studio Debug-Build 200041 erneut starten; als Update installieren, App-Daten nicht löschen.
3. selben BTB/Abschnitt 4 öffnen.
4. bei `bbg · Querformat (KM 5,963) · Test1.0` **`Foto +`** verwenden und externes Bild auswählen.
5. prüfen: Foto zeigt Firma `bbg`, Ort `Querformat`, KM `5,963`, Text `Test1.0` und landet nicht unter Allgemein.
6. PDF-Entwurf erzeugen und fachliche Gruppierung unter `bbg / Querformat` prüfen.
7. ein bereits unter Allgemein vorhandenes Importfoto öffnen → `Bearbeiten` → `Arbeitseintrag / Ort` → `bbg / Querformat / Test1.0` wählen → speichern → PDF erneut prüfen.
8. oberen Button `Allgemeine Fotos ...` separat testen; nur dieser Import darf bewusst unter Allgemein/Hauptbaustelle bleiben.
9. Originalfoto-/Nachtrag-/Löschschutz nochmals kurz gegenprüfen.

## Foto-Overlay – nächster Teil desselben Foto-Blocks
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos bereits Informationen dauerhaft in die App-JPG-Datei ein. Vorhandene Felder umfassen u. a. Baustelle/Unterbaustelle, Strecke/Bahnhof, Gleis/Weiche/Objekt, KM, GPS, Bereich und Datum/Uhrzeit.

Noch offen nach dem 200041-Grundtest:
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
Nach erfolgreichem 200041-Test:
1. Foto-Overlay auf echten Kamera-Fotos praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss weiterhin keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene Abschlussprüfungen älterer BTB-Runde
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren.
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren.
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
