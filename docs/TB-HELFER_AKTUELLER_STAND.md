# TB-Helfer – aktueller Projektstand

Stand: 2026-09-16

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. aktiven Feature-Branch, offenen Draft-PR und neuesten relevanten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- letzter vollständig bestätigter Kernstand: Android **200039**.
- Android **200044** wurde nach dem Room/KSP-Fix auf dem Poco wieder gestartet; der BTB-Eintragsdialog und seine Fotoaktionen waren wieder sichtbar/funktionsfähig.
- aktueller Buildkandidat: Android **200045**.

## Normaler Entwicklungsablauf
Source of Truth ist GitHub.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200045 – kompakte Bildauswahl im BTB-Arbeitseintrag
Ziel nach praktischem 200044-Test: Die zwei großen blauen Foto-Schaltflächen im Dialog `Eintrag für <Firma>` waren fachlich funktionsfähig, aber optisch zu groß und unübersichtlich.

Umgesetzt:
- `versionCode = 200045`, `versionName = 1.3`;
- die zwei großen Buttons `📷 → Kamera` und `📷 → Galerie & Dateien` wurden aus diesem Arbeitseintragsdialog entfernt;
- stattdessen gibt es nur noch ein kompaktes Bild-/Galerie-Symbol links im unteren Dialogbereich;
- Tippen auf dieses Symbol startet direkt den vorhandenen Android-Bildauswahlpfad (`image/*`), ohne vorgeschaltetes Kamera/Galerie-Menü;
- der Dialog bietet über dieses Symbol bewusst keine Kamera mehr an;
- externe Bilder werden weiterhin über den vorhandenen BTB-Importweg als App-Kopie übernommen; das externe Original bleibt unverändert;
- die fachliche Zuordnung bleibt erhalten: stabile `btbId`, Firma, Unterbaustelle/Ort, Arbeitstext, optionale Zeit, KM/KM-Bereich;
- normales `Abbrechen` und `Hinzufügen` bleiben unverändert;
- neues Vektor-Icon `ic_image_picker_24.xml`;
- Icon-Farbe wird aus Material `colorOnSurface` abgeleitet, der Tipp-Hintergrund aus dem aktuellen Android-Theme. Keine fest verdrahtete Hell-/Dunkel-Farbe; Tag- und Nachtansicht sollen damit automatisch passen.

Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`
- `app/src/main/res/drawable/ic_image_picker_24.xml`
- `app/build.gradle.kts`
- diese Statusdatei

Code-Commit vor dieser Doku:
- `1e24943f1ea3dfd16fa378b0811f6c0126633b06` – `feat: simplify BTB image picker action`

### Teststatus 200045
- Projektregeln, aktueller Stand, aktiver Branch, Draft-PR #5 und Branch-Head vor Änderung geprüft.
- vorhandener Galerie-/Dateiimport wurde wiederverwendet; keine zweite Foto-/EXIF-/Overlay-Lösung gebaut.
- statisch geprüft: Bildsymbol verwendet Theme-Farbe `colorOnSurface` und `selectableItemBackgroundBorderless`, daher Tag/Nacht ohne feste Symbolfarbe.
- lokaler Android-Studio-Build und Gerätetest 200045 stehen noch aus.
- beim Handtest besonders prüfen: Symbolposition/Größe, Tagansicht, Nachtansicht, Mehrfachauswahl sowie korrekte Zuordnung der ausgewählten Bilder zum Arbeitseintrag.

## Android 200043/200044 – Room/KSP-Regressionsfix
200043 führte den Foto-Hook direkt im Dialog `Eintrag für <Firma>` ein, ersetzte dafür aber den normalen Android-Kotlin-Source-Set durch eine generierte Build-Kopie. Dadurch kompilierte Kotlin, Room/KSP erzeugte jedoch `TBDatenbank_Impl` nicht; die App stürzte erst beim Start ab.

200044 korrigierte das:
- `src/main/java/de/tbhelfer/datenbank` bleibt als kanonischer Kotlin-Source-Ordner für KSP sichtbar;
- der Datenbankordner wird aus der generierten BTB-Quellkopie entfernt, damit keine doppelten Klassen entstehen;
- Guard `verifyRoomGeneratedImplementation` prüft, ob `TBDatenbank_Impl.*` unter `build/generated/ksp` erzeugt wurde;
- der anfängliche Guardfehler `$$implicitReceiver_Project ... this is null` wurde durch Zugriff über `project.layout` behoben.

Praktischer Stand: 200044 startete danach auf dem Poco wieder; der BTB-Eintragsdialog war nutzbar. Dieser Room/KSP-Schutz bleibt in 200045 erhalten.

## Fotoimport / BTB-Zuordnung – Stand 200040–200045
- externe Bilder als eigene App-Kopie; Galerie-/Datei-Original unverändert;
- moderne Fotos strikt über stabile `btbId`;
- EXIF-GPS/Aufnahmezeit soweit vorhanden;
- Fotoverwaltung mit Thumbnail, Auswahl, Bearbeiten und Löschschutz;
- Bearbeitung/Korrektur von Text, KM, Firma, Objekt/Bereich und Unterbaustelle/Ort;
- Originalfotos des ersten BTB-Abschlusses bleiben geschützt;
- `BtbPhotoContextOverrideStore` kann BTB-lokale `ortId` speichern;
- Arbeitseintrags-Import übernimmt Firma, Ort, Text und KM/KM-Bereich;
- 200045 vereinfacht nur die Bedienung des Bildimports; die funktionierende Zuordnungslogik bleibt erhalten.

## Nächstes Problem nach 200045 – KM/Festpunkte
Vom Nutzer gemeldet:
- Vermessungs-/Absteckungsdaten lassen sich einlesen;
- KM-Berechnung funktioniert danach nicht mehr wie zuvor;
- Festpunkte bzw. importierte Punkte mit vorhandener Kilometrierung sind nicht mehr vorhanden/sichtbar.

Dieses Problem wird als nächster eigener Block bearbeitet, sobald 200045 gebaut und der vereinfachte Bildwähler kurz bestätigt ist.

Zuerst prüfen:
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteViewModel.kt`
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/AktiverTrassenKontext.kt`
- `app/src/main/java/de/tbhelfer/datenbank/TrassenRepository.kt`
- `TrassenKmAnkerEntity`
- `TrassenSamplePointEntity`
- `GraKmPunktEntity`
- `GvpPointEntity`
- `AbsteckpunktEntity`
- `app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt`
- KM-/Festpunkt-Import sowie mögliche Reset-/Bereinigungslogik.

Originale Vermessungsdaten bleiben unverändert. Zuerst feststellen, ob Festpunkte beim Import nicht gespeichert, später gelöscht/resetet oder nur nicht mehr angezeigt/verknüpft werden.

## Bestätigter BTB-Kernstand 200035–200039
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- erster erfolgreicher Abschluss = unveränderliches Original;
- spätere fachliche Änderungen gegen dieses Original; neue/geänderte Inhalte rot, entfernte Originalinhalte rot `(entfernt)`;
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter bleiben PDF-Kopfdaten und werden nicht rot markiert;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach derselben `btbId`; Legacy-Zeitfenster nur ohne `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original;
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert werden;
- 24h-TimePicker für BTB-Zeit, Personal und Geräte/Maschinen;
- abgeschlossene BTBs lassen sich bewusst wieder öffnen und danach erneut abschließen;
- Firmenlogo/Kontaktdaten/PDF-Vorschau wurden in 200039 praktisch bestätigt.

## Foto-Overlay
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos Informationen dauerhaft in die App-JPG-Datei ein, u. a.:
- Baustelle/Unterbaustelle;
- Strecke/Bahnhof;
- Gleis/Weiche/Objekt;
- KM;
- GPS;
- Bereich;
- Datum/Uhrzeit.

Noch offen: auf Pixel/Poco praktisch prüfen und nur bei echtem Bedarf optisch nachschärfen. Keine parallele zweite Overlay-Lösung bauen.

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

## Danach geplante Arbeiten
Nach Behebung des KM/Festpunkt-Problems:
1. Foto-Overlay praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene ältere Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
