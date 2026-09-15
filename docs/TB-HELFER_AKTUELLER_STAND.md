# TB-Helfer – aktueller Projektstand

Stand: 2026-09-15

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
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- letzter vollständig bestätigter Android-Gerätestand: **200039**.
- **Android 200042:** Android-Studio-Build am 15.09.2026 erfolgreich; 15 Warnungen, keine Buildfehler. Gerätefeedback zeigte jedoch, dass die neuen Fotoquellen nicht im eigentlichen Dialog `Eintrag für <Firma>` sichtbar waren.
- aktueller Branch-Kandidat: **Android 200043 – Fotoaktionen direkt im manuellen Arbeitseintrag-Dialog**.

## Normaler Entwicklungsablauf
Der Source of Truth liegt vollständig in GitHub. Neue Änderungen werden direkt im Feature-Branch versioniert.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200043 – aktueller Entwicklungsstand
`app/build.gradle.kts` steht auf `versionCode = 200043`, `versionName = 1.3`.

### Behobener Bedienfehler
200042 hatte den Ziel-/Empfangspfad für direkte Fotoaktionen bereits in `BtbPdfAuswahlFragment` vorbereitet, die beiden Quellen aber nicht in den tatsächlich vom Nutzer verwendeten Dialog `Eintrag für <Firma>` eingebunden. Dadurch waren auf dem Gerät nur `Abbrechen` und `Hinzufügen` sichtbar.

200043 ergänzt direkt unter dem Arbeitstext zwei kompakte Aktionen:
- `📷 → Kamera`
- `📷 → Galerie & Dateien`

Die Aktionen erscheinen nur bei Abschnitt 4 / Bildtyp `FOTODOKUMENTATION`; der normale Button `Hinzufügen` bleibt unverändert erhalten.

### Fachliche Zuordnung beim Foto-Start
Vor dem Wechsel zur Fotoquelle wird der eingegebene Arbeitseintrag validiert und in den BTB übernommen. Weitergegeben werden:
- stabile `btbId` bzw. nach dem Speichern aufgelöster aktiver BTB-Pointer;
- Baustelle;
- Firma;
- Unterbaustelle/Ort-ID und Ortsbezeichnung;
- Arbeitstext;
- optionale Eintragszeit bzw. Zeitspanne;
- optionaler KM bzw. KM-Bereich.

Danach wird der bereits vorhandene 200042-Zielpfad in `BtbPdfAuswahlFragment` geöffnet:
- `Kamera` verwendet weiterhin die bestehende TB-Helfer-Kamera;
- `Galerie & Dateien` verwendet weiterhin `OpenMultipleDocuments` und den vorhandenen Importpfad;
- keine zweite Kamera-, Foto-, EXIF- oder Overlay-Lösung wurde angelegt;
- vorhandene `BtbPhoto*OverrideStore`-Speicher bleiben für Text/Zeit/KM/Kontext maßgeblich.

### Technische Integration 200043
Neue Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`

Der Binder enthält nur die Dialogaktionen, Eingabeprüfung, Speicherung des aktuellen BTB-Snapshots und Navigation zum vorhandenen Fotoauswahl-/Kameraweg.

`BautagebuchFragment.kt` ist inzwischen ein sehr großer Legacy-Quelltext. Um für den kleinen 200043-Hook keine riskante Komplett-Ersetzung dieses Quellfiles vorzunehmen, wird beim Build über einen AGP-9-Generated-Source-Task eine identische Kopie von `app/src/main/java` unter `build/generated/source/btb200043` erzeugt. Ausschließlich in dieser Build-Kopie wird der Aufruf von `BtbEntryPhotoActionBinder.bind(...)` direkt hinter der Erzeugung des Dialogs `Eintrag für <Firma>` eingesetzt.

Wichtig:
- der Git-Arbeitsbaum wird beim Build nicht verändert;
- GitHub Desktop bleibt nach dem Build sauber;
- AGP 9 Built-in Kotlin wird über `variant.sources.kotlin.addGeneratedSourceDirectory(...)` verwendet;
- der Generator bricht bewusst ab, falls der eindeutige Dialog-Anker in `BautagebuchFragment.kt` nicht mehr vorhanden ist;
- dies ist eine gezielte Integrationsbrücke für den großen Legacy-Fragmentquelltext, keine zweite BTB-Implementierung. Bei einer späteren strukturellen Aufteilung von `BautagebuchFragment.kt` soll der Hook direkt in den aufgeteilten Quellcode übernommen und der Build-Overlay wieder entfernt werden.

### Betroffene Dateien 200043
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`
- bereits aus 200042 vorhanden und weiterverwendet: `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt`
- diese Datei.

### Prüfung 200043
Vor der Änderung wurden Projektregeln, aktueller Stand, Branch, Draft-PR #5 und neueste relevante Commits geprüft.

Statisch geprüft:
- der Dialog-Anker in `BautagebuchFragment.kt` ist im aktuellen Branch exakt vorhanden;
- der 200043-Hook wird unmittelbar nach `.create()` des Dialogs `Eintrag für <Firma>` eingesetzt;
- `BtbPdfAuswahlFragment` wertet `autoPhotoSource = camera|gallery` sowie Firma, Ort, Text, Zeit und KM bereits aus;
- Kameraweg korrigiert nach Rückkehr nur neue Fotos derselben stabilen `btbId` und des Bildtyps `FOTODOKUMENTATION` auf den gewählten Arbeitseintrag;
- der vorhandene Kamera-/Overlay-Pfad bleibt unverändert.

Noch nicht bestätigt:
- vollständiger Android-Studio-Build **200043** auf dem Windows-PC;
- Anzeige beider Buttons auf Pixel/Poco;
- Kamera- und Galerie-Rückweg aus genau diesem neuen Dialog.

GitHub Actions ist derzeit kein verlässlicher Buildindikator: die aktuellen `Android CI`-Runs enden weiterhin innerhalb weniger Sekunden als `failure`, die Jobs liefern keine ausführbaren Schritte/Runner-Logs (`steps = null`). Deshalb muss 200043 lokal in Android Studio gebaut werden.

### Nächster Gerätetest 200043
1. GitHub Desktop `Fetch origin` → `Pull origin`.
2. Android Studio Debug-Build 200043 starten und als Update installieren; App-Daten nicht löschen.
3. BTB → `4. Ausgeführte Arbeiten` → Firma (`bbg` o. ä.) → `+`.
4. Im Dialog `Eintrag für <Firma>` müssen unter dem Arbeitstext sichtbar sein:
   - `📷 → Kamera`
   - `📷 → Galerie & Dateien`.
5. Text sowie optional Uhrzeit/KM eintragen und `📷 → Kamera` testen.
6. Neues Foto speichern; nach Rückkehr Zuordnung zu demselben BTB, Firma, Ort, Text und KM prüfen.
7. Zweiter Durchgang mit `📷 → Galerie & Dateien`; externes Original darf nicht verändert werden.
8. Normalen Button `Hinzufügen` ohne Foto kurz gegenprüfen.

## Nächstes Problem nach Abschluss von 200043 – KM/Festpunkte
Vom Nutzer am 15.09.2026 gemeldet:
- Vermessungs-/Absteckungsdaten lassen sich weiterhin einlesen;
- KM-Berechnung funktioniert danach nicht mehr wie zuvor;
- die Festpunkte bzw. importierten Punkte mit vorhandener Kilometrierung sind in der App nicht mehr vorhanden/sichtbar.

Dieses Problem wird **bewusst erst nach Abschluss des 200043-Foto-Dialogtests** analysiert, damit nicht zwei Fehlerblöcke gleichzeitig verändert werden.

Für die anschließende Bestandsprüfung zuerst untersuchen:
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteViewModel.kt`
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/AktiverTrassenKontext.kt`
- `app/src/main/java/de/tbhelfer/datenbank/TrassenRepository.kt`
- `TrassenKmAnkerEntity`, `TrassenSamplePointEntity`, `GraKmPunktEntity`, `GvpPointEntity`, `AbsteckpunktEntity`
- `app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt`
- KM-/Festpunkt-Import und mögliche Reset-/Bereinigungslogik.

Keine Vermessungsdaten oder originale Importdateien verändern. Zuerst prüfen, ob Daten beim Import nicht gespeichert, nach dem Import gelöscht/resetet oder nur nicht mehr angezeigt/verknüpft werden.

## Android 200042 – letzter lokal gebauter Entwicklungsstand
200042 räumte die 200040/200041-Fotoimportbedienung auf:
- großer globaler Importblock entfernt;
- Foto-Liste mit Thumbnail, Metadaten, Häkchen, Bearbeiten sowie Entfernen/Löschen beibehalten;
- erkannte Arbeitseinträge bekamen eine kompakte `📷`-Aktion in der Fotoauswahl;
- `📷` öffnet dort `Kamera` oder `Galerie & Dateien`;
- bestehende Kamera wird wiederverwendet;
- nach Kamera-Rückkehr werden nur neue Fotos derselben `btbId` und desselben Bildtyps fachlich auf den Arbeitseintrag korrigiert;
- vorherige globale Unterbaustellen-Auswahl wird wiederhergestellt;
- neu entstandene Fotos werden bis zum PDF-Fotolimit automatisch ausgewählt.

Logo/PDF 200042:
- PDF-Logo lokal 40–250 pt;
- rechter Kopfbereich entsprechend vergrößert;
- ohne Logo bleibt der Bereich weiß;
- antippbare Logo-Karte unter `Einstellungen → BTB & PDF` öffnet echte PDF-Vorschau;
- Schema 12 bleibt nur vorbereitet und ist nicht produktiv migriert.

Buildhistorie 200042:
- erster Build fand zwei Compilerfehler `insetLeft` / `insetRight`;
- `MaterialButtonHorizontalInsetCompat.kt` korrigierte diese horizontalen Button-Inset-Zuweisungen;
- erneuter Android-Studio-Build am 15.09.2026 **erfolgreich**;
- Android Studio zeigte 15 Warnungen, unter anderem bestehende Deprecation-/Experimental-Warnungen, aber keine Fehler.

## Android 200039 – gebaut, installiert, Gerätetest bestätigt
`versionCode = 200039`, `versionName = 1.3` ist weiterhin der letzte vollständig praktisch bestätigte Gerätestand.

Bestätigt:
- Firmenlogo im realen BTB-PDF proportional;
- Firmenname, Firmen-E-Mail und Firmentelefon unter dem Logo konfigurierbar;
- `Kontaktdaten sind bereits im Logo enthalten` unterdrückt doppelte Kontaktzeilen;
- verantwortlicher TB mit persönlicher E-Mail und Mobilnummer im PDF-Kopf;
- Fußzeile `Erstellt von: <Nutzer> · <Datum>` plus Seitenzahl;
- Logo unter `Einstellungen → BTB & PDF` verschieb-/skalierbar und rücksetzbar;
- echte A4-PDF-Vorschau im internen PDF-Viewer;
- Foto-Raster wird in der Vorschau angezeigt;
- Vorschau und reales BTB-PDF auf dem Handy geprüft; Nutzer meldete `passt alles`.

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
Nach erfolgreichem 200043-Test und Behebung des gemeldeten KM/Festpunkt-Problems:
1. Foto-Overlay auf echten Kamera-Fotos praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss weiterhin keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene Abschlussprüfungen älterer BTB-Runde
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren.
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren.
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
