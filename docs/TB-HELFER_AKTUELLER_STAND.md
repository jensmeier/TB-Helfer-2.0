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
- Android 200042 wurde lokal erfolgreich gebaut; 15 Warnungen, keine Buildfehler.
- Android 200043 wurde am 15.09.2026 lokal gebaut; Build endete mit nur 3 Warnungen, aber die installierte App startete auf dem Poco nicht mehr wegen fehlender Room-Implementierung `TBDatenbank_Impl`.
- aktueller Branch-Kandidat: **Android 200044 – Room/KSP-Regressionsfix für die 200043-Build-Source-Brücke**.

## Normaler Entwicklungsablauf
Source of Truth ist GitHub.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200044 – aktueller Entwicklungsstand
`app/build.gradle.kts` steht auf `versionCode = 200044`, `versionName = 1.3`.

### Fehlerbild 200043
Der 200043-Build kompilierte in Android Studio, die App stürzte beim Start auf dem Poco jedoch sofort ab:

`java.lang.RuntimeException: Cannot find implementation for de.tbhelfer.datenbank.TBDatenbank. TBDatenbank_Impl does not exist.`

Stacktrace führte über `RoomDatabase.Builder.build()` zu `TBDatenbank.erstellen()` und `ServiceLocator`.

### Ursache
200043 hatte für den kleinen BTB-Dialog-Hook den normalen Kotlin-Quellbaum in `app/build.gradle.kts` aus dem Android-Source-Set entfernt und stattdessen eine identische, beim Build erzeugte Kopie unter `build/generated/source/btb200043` als generierte Kotlin-Quelle registriert.

Damit konnte `compileDebugKotlin` den App-Code kompilieren, Room/KSP sah aber die kanonischen Room-Quellen nicht mehr als normalen Android-Kotlin-Source-Set. Dadurch wurde `TBDatenbank_Impl` nicht erzeugt. Weil Room die Implementierung zur Laufzeit per Namenskonvention lädt, konnte der Build trotzdem grün enden und erst beim App-Start scheitern.

### Fix 200044
Die 200043-BTBFoto-Integration bleibt fachlich erhalten, die Datenbankquellen werden aber wieder explizit und kanonisch für KSP eingebunden:
- `src/main/java/de/tbhelfer/datenbank` bleibt als statischer Android-Kotlin-Source-Ordner sichtbar;
- die generierte 200043-Build-Kopie entfernt ihren eigenen `de/tbhelfer/datenbank`-Ordner, damit keine doppelten Datenbankklassen entstehen;
- Room/KSP kann damit `TBDatenbank`, Entities und DAOs wieder aus dem echten Quellbaum verarbeiten;
- zusätzlich gibt es den Gradle-Check `verifyRoomGeneratedImplementation`;
- nach `compileDebugKotlin`/`compileReleaseKotlin` wird geprüft, ob unter `build/generated/ksp` tatsächlich eine Datei `TBDatenbank_Impl.*` erzeugt wurde;
- wenn nicht, schlägt der Build jetzt bewusst fehl mit `Room hat TBDatenbank_Impl nicht erzeugt. APK darf nicht installiert werden.` statt erst auf dem Handy abzustürzen.

### Betroffene Dateien 200044
- `app/build.gradle.kts`
- `docs/TB-HELFER_AKTUELLER_STAND.md`

Code-Fix-Commit vor dieser Doku: `fb05c443f422442f11628ccb4ec76c4846198e9e`.

### Prüfung / Teststatus 200044
- Projektregeln, aktueller Stand, Branch, Draft-PR #5 und letzter Commit vor Änderung geprüft.
- Stacktrace des Poco eindeutig auf fehlendes `TBDatenbank_Impl` zurückgeführt.
- `TBDatenbank.kt` ist weiterhin korrekt mit `@Database(...)` annotiert und verwendet Room/KSP.
- statisch geprüft: die 200043-generierte Quellkopie hatte den gesamten normalen Source-Set ersetzt; dies erklärt, warum Kotlin kompilieren konnte, KSP aber keine Room-Implementierung lieferte.
- statisch geprüft: 200044 bindet den kanonischen Datenbankordner wieder als Android-Kotlin-Source ein und entfernt denselben Ordner aus der generierten Kopie, daher keine absichtlichen Doppelklassen.
- zusätzlicher Build-Guard gegen erneut fehlendes `TBDatenbank_Impl` eingebaut.
- vollständiger lokaler Android-Studio-Clean-Build 200044 und Gerätetest stehen noch aus.
- GitHub Actions bleibt derzeit kein belastbarer Buildindikator; die PR-Runs enden weiterhin ohne verwertbare Runner-Schritte/Logs.

### Nächster Test 200044
1. GitHub Desktop `Fetch origin` → `Pull origin`.
2. Android Studio öffnen.
3. **Einmal `Build → Clean Project`**, damit kein altes 200043-KSP-/Generated-Source-Artefakt übrig bleibt.
4. danach Debug-Build/Run 200044.
5. Wenn der Build grün endet, muss der neue Room-Guard `TBDatenbank_Impl` gefunden haben.
6. als Update installieren; App-Daten nicht löschen.
7. zuerst nur prüfen: Poco startet normal und Startmenü/Baustellenzugriff funktionieren.
8. danach den ursprünglichen 200043-Test fortsetzen: BTB → Abschnitt 4 → Firma → `+`; im Dialog müssen `📷 → Kamera` und `📷 → Galerie & Dateien` sichtbar sein.
9. Kamera/Galerie und normalen `Hinzufügen`-Pfad kurz prüfen.

## Android 200043 – BTB-Fotoaktion, fachlicher Stand bleibt erhalten
Ziel von 200043:
- direkt im Dialog `Eintrag für <Firma>` zwei Aktionen:
  - `📷 → Kamera`
  - `📷 → Galerie & Dateien`;
- normaler Button `Hinzufügen` bleibt erhalten;
- vor Foto-Start Text, optionale Zeit und KM validieren;
- stabilen aktuellen BTB speichern und dieselbe `btbId` verwenden;
- Firma, Unterbaustelle/Ort, Arbeitstext, Zeit und KM/KM-Bereich an den vorhandenen Fotopfad weitergeben;
- bestehende TB-Helfer-Kamera und bestehenden Galerie-/Dateiimport wiederverwenden;
- keine zweite Kamera-, Foto-, EXIF- oder Overlay-Lösung.

Neue Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`

Empfänger bleibt:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt`

Der Empfänger unterstützt `autoPhotoSource = camera|gallery` sowie Firma, Ort, Text, Zeit und KM. Der Kamera-Rückweg korrigiert nur neu entstandene Fotos derselben stabilen `btbId` und des Bildtyps `FOTODOKUMENTATION` auf den ausgewählten Arbeitseintrag.

## Nächstes Problem nach erfolgreichem 200044/200043-Test – KM/Festpunkte
Vom Nutzer am 15.09.2026 gemeldet:
- Vermessungs-/Absteckungsdaten lassen sich weiterhin einlesen;
- KM-Berechnung funktioniert danach nicht mehr wie zuvor;
- Festpunkte bzw. importierte Punkte mit vorhandener Kilometrierung sind nicht mehr vorhanden/sichtbar.

Dieses Problem wird bewusst **erst nach dem Room-Startfix und dem 200043-Fotodialogtest** bearbeitet, damit nicht mehrere Fehlerblöcke gleichzeitig geändert werden.

Dann zuerst prüfen:
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

Originale Vermessungsdaten werden nicht verändert. Zuerst klären, ob die Punkte beim Import nicht gespeichert, nach dem Import gelöscht/resetet oder nur nicht mehr angezeigt/verknüpft werden.

## Android 200042 / Fotoimport-Grundlage 200040–200042
200040 führte externen Import und Fotoverwaltung ein:
- `OpenMultipleDocuments`;
- externe Bilder als eigene App-Kopie, externe Originaldatei unverändert;
- Zuordnung moderner Fotos über stabile `btbId`;
- EXIF-GPS/Aufnahmezeit soweit vorhanden;
- Bearbeitung von Text, KM, Firma und Objekt/Bereich;
- Originalfoto-Schutz nach erstem BTB-Abschluss.

200041 ergänzte fachliche Zielzuordnung pro Arbeitseintrag:
- Parser für `@ORT`, `@ALLGEMEIN`, `@KM`, Firma, Zeit und Text;
- Import übernimmt Firma, `baustellenKnotenId`, Text und KM/KM-Bereich;
- Eintragszeit/KM über vorhandene BTB-Override-Speicher;
- `BtbPhotoContextOverrideStore` Version 2 speichert BTB-lokalen `ortId` und liest V1 weiter.

200042 räumte die Bedienung auf:
- großer globaler Importblock entfernt;
- Foto-Liste mit Thumbnail, Metadaten, Auswahl, Bearbeiten und Löschschutz beibehalten;
- erkannte Arbeitseinträge bekamen kompakte `📷`-Aktion;
- Kamera nutzt den bestehenden TB-Helfer-Kamerapfad;
- nach Rückkehr werden nur neue Fotos derselben `btbId`/des passenden Bildtyps korrigiert;
- Logo/PDF: Größenbereich bis 250 pt, proportionaler rechter Kopfbereich, antippbare Logo-Karte mit echter PDF-Vorschau;
- Schema 12 bleibt nur vorbereitet, nicht produktiv migriert.

## Android 200039 – letzter vollständig praktisch bestätigter Gerätestand
Bestätigt:
- stabile fachliche `btbId`;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- erster erfolgreicher Abschluss = unveränderliches Original;
- spätere fachliche Änderungen gegen dieses Original; neue/geänderte Inhalte rot, entfernte Originalinhalte rot `(entfernt)`;
- Kopfdaten bleiben im PDF schwarz;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach exakt derselben `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum Original;
- 24h-TimePicker für BTB-Zeit, Personal und Geräte/Maschinen;
- abgeschlossene BTBs können bewusst wieder geöffnet und erneut abgeschlossen werden;
- Firmenlogo im realen PDF proportional;
- Firmenname/Firmen-E-Mail/Firmentelefon konfigurierbar;
- persönliche TB-E-Mail/Mobilnummer im PDF-Kopf;
- Fußzeile `Erstellt von: <Nutzer> · <Datum>`;
- Logo verschieb-/skalierbar;
- echte A4-PDF-Vorschau im internen Viewer und Foto-Raster geprüft.

## Foto-Overlay
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos bereits Informationen in die App-JPG-Datei ein, u. a.:
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
Nach erfolgreichem 200044/200043-Test und Behebung des KM/Festpunkt-Problems:
1. Foto-Overlay praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene ältere Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
