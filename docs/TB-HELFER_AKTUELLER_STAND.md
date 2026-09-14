# TB-Helfer – aktueller Projektstand

Stand: 2026-09-14

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen zusätzlich in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. offenen Draft-PR und neuesten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR und danach diesen Stand aktualisieren.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable.
- letzter Branch-Commit vor dieser Doku-Aktualisierung: `1b9bb3e7b8ba5f6759ad3b9d865f6260e6a4702d` (`feat: prepare Android 200036 export and BTB UI fixes`).
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.

## WICHTIG – lokaler PC-Stand und sichere Weiterarbeit
Auf dem Windows-PC des Benutzers liegen weiterhin die bewusst noch nicht gepushten Android-200035-Sourceänderungen:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt`
- `app/build.gradle.kts` (`versionCode = 200035`, `versionName = 1.3`)

Der lokale 200035-Stand wurde bereits erfolgreich gebaut:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat :app:assembleDebug --rerun-tasks
```
Ergebnis damals:
- `BUILD SUCCESSFUL in 2m 3s`
- Debug-APK: `app\build\outputs\apk\debug\app-debug.apk`
- APK-Zeitstempel: `14.09.2026 12:48:19`.

Diese beiden lokalen Dateien **nicht verwerfen/resetten**. Der neue Remote-Commit 200036 ändert absichtlich noch keine dieser beiden Source-Dateien, sondern stellt einen versionierten Patch bereit, damit der lokale 200035-Stand konfliktarm weitergeführt werden kann.

## Android 200036 – vorbereitet, noch nicht lokal angewendet/getestet
Neu im Branch:
- `.github/scripts/patch_200036.ps1`

Der Patch erkennt den lokalen 200035-Stand. Falls 200035 noch nicht angewendet ist, wird zuerst `.github/scripts/patch_200035.ps1` ausgeführt. Danach wird auf `versionCode = 200036` weitergepatcht.

### Anlass: zwei echte Exportfehler aus dem Test vom 14.09.2026
Aus den hochgeladenen Test-ZIPs wurde bestätigt:
- Zeitraum-ZIP und Gesamt-Archiv enthielten keinen BTB-Abschluss-PDF.
- Zeitraum-DOCX enthielt nicht dieselben Kopfdaten wie das normalisierte Gesamt-Archiv; insbesondere Firma, verantwortlicher TB und Schicht fehlten.
- Positiv bestätigt: ein allgemeines Baustellenfoto ohne `btbId` blieb außerhalb des modernen BTB; im Test waren 5 Baustellenfotos vorhanden, davon 4 im BTB.
- Positiv bestätigt: Foto-Gruppierung im bearbeitbaren Dokument arbeitete bereits mit Zeitspanne/Bildbereich, z. B. `[Bild 1–3]`.

### Gefundene Ursache für fehlendes Abschluss-PDF
`BautagebuchSpeicher.neuestePdfFuerVerlauf()` suchte bei modernen BTBs im Dateinamen nach der vollständigen `btbId`. Die kanonische Dokumentidentität verwendet im Dateinamen dagegen bewusst nur den gekürzten stabilen `btbToken` (`take(18)`). Dadurch konnte `VerlaufEintrag.pdfDatei` trotz vorhandener Abschlussdatei `null` sein; Zeitraum-/Gesamtexport übersprangen dann das PDF.

200036 verwendet für moderne BTBs zusätzlich den bereits vorhandenen kanonischen `BtbAbschlussPdfFinder` und damit denselben Token wie die tatsächliche Abschlussdatei.

### Inhalt des Patch 200036
Der Patch ändert lokal:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
  - moderne Abschluss-PDFs im Verlauf über `BtbAbschlussPdfFinder` auflösen.
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExportPaket.kt`
  - Zeitraum-/Gesamtexport mit PDF-Fallback über den kanonischen Finder;
  - BTB-DOCX-Kopf um Firma, verantwortlichen TB, Schicht und `BTB-ID` ergänzen;
  - moderne BTB-Fotos im Export strikt nach derselben `btbId` filtern;
  - Zeitraum-PDF/DOCX erhalten einen stabilen BTB-Token im Dateinamen, damit mehrere BTBs derselben Schicht am selben Tag nicht als `(2)` kollidieren.
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEinzelZip.kt`
  - Firma und Schicht ebenfalls in den DOCX-Kopf aufnehmen.
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/MergeSafeBaustellenExport.kt`
  - stabilen BTB-Suffix beim Normalisieren beibehalten; Multi-BTB-Dateien dürfen nicht auf denselben Archivnamen zusammenfallen.
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt`
  - beim neuen BTB Start/Ende über grafischen `TimePickerDialog` statt alter Listenwahl;
  - Abschlussdialog erklärt beim ersten Abschluss ausdrücklich das unveränderliche Original und die rote Nachbearbeitung;
  - Folgeabschluss erklärt, dass nur das aktuelle PDF ersetzt wird und das erste Original erhalten bleibt.
- `app/src/main/res/layout/dialog_schicht.xml`
  - Start-/Ende-Felder als anklickbare Uhrfelder ohne Dropdown-Pfeil.
- `app/src/main/res/layout/fragment_bautagebuch.xml`
  - Karte `Neuen BTB starten` folgt Theme-Farben statt festem hellem Hintergrund; Lesbarkeit im Dunkelmodus verbessert.
- `app/build.gradle.kts`
  - lokal auf `versionCode = 200036`.

Der Patch besitzt abschließende Source-Prüfungen und meldet nur bei vollständig gefundenen Markern `PATCH_200036_FERTIG`.

### Was 200036 bewusst nicht neu erfindet
- Die 200035-PDF-Original-Diff-Korrektur bleibt Grundlage: neue Fotozeilen rot, Original-/Nachtragsgruppen getrennt, entfernte Originalinhalte rot `(entfernt)`.
- Bestehende Foto-Gruppierung im PDF/Export wird weiterverwendet; keine parallele Ersatzlogik.
- Produktiver NAS/Server wird nicht verändert.
- Einzel-BTB-DOCX besitzt weiterhin noch nicht dieselbe vollständige Rotformatierungslogik wie das PDF; das bleibt ein eigener späterer Punkt.
- Direkte Aktion `PDF ansehen / ZIP senden` unmittelbar nach Abschluss bleibt noch ein offener UI-Komfortpunkt.

## Nächster Test: Android 200036
Nach Pull des Branches im Repository-Stamm ausführen:
```powershell
powershell -ExecutionPolicy Bypass -File .\.github\scripts\patch_200036.ps1
```
Erwartete Abschlussmeldung:
```text
PATCH_200036_FERTIG
```

Danach neu bauen:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat :app:assembleDebug --rerun-tasks
```
APK als Update installieren; **App-Daten nicht löschen**.

### Verbindliche Prüfpunkte 200036
1. Neuer BTB: Start-/Endzeit öffnet grafische Uhr; 15-Minuten-Rundung bleibt erhalten.
2. Dunkelmodus: Karte/Dialog `Neuen BTB starten` ist lesbar.
3. Erster `BTB Abschluss`: Hinweis auf unveränderliches Original erscheint.
4. Nach Erstabschluss denselben BTB aus Verlauf öffnen und ändern:
   - neues BTB-Foto im Anhang rot;
   - zugehörige Fotozeile im Hauptteil rot;
   - Originaleinträge schwarz;
   - entfernte Originalinhalte rot `(entfernt) ...`.
5. Allgemeines Baustellenfoto ohne `btbId` bleibt außerhalb des modernen BTB.
6. Zeitraum-ZIP exportieren:
   - Abschluss-PDF muss unter `BTB/<Datum>/` vorhanden sein;
   - DOCX muss Firma, verantwortlichen TB, Schicht und BTB-ID enthalten.
7. Gesamt-Archiv exportieren:
   - Abschluss-PDF muss ebenfalls enthalten bleiben;
   - keine BTB-Datei darf durch Normalisierung verschwinden.
8. Wenn möglich zwei BTBs am selben Tag/gleicher Schicht testen: Dateinamen müssen durch stabilen BTB-Token unterscheidbar bleiben; kein fachliches `(2)` als Identität.

Erst wenn dieser Gerätetest passt, die lokal geänderten Source-Dateien committen/pushen und diesen Stand auf `200036 getestet` aktualisieren.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; 72 OpenAPI-Pfade, Schema 10.
- in der aktuellen Android-BTB-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor produktivem Schema-11-Schritt frisches verifiziertes DB-Backup erstellen.

## Praktisch bestätigte Grundlage Android 200034
- Startmenü `Aktuelle Baustelle` nutzt ohne aktiven BTB den Kamera-Guard.
- `Nur Baustellenfoto` / `BTB starten/öffnen` funktioniert.
- allgemeines Baustellenfoto ohne `btbId` läuft nicht in einen modernen BTB hinein.
- abgeschlossenen BTB über Verlauf erneut bearbeiten funktioniert.
- Nachtmodus `Dunkel/Hell/System` bleibt gespeichert.
- nach erfolgreichem `BTB Abschluss` wird die aktive `btbId` gelöst; Bautagebuch zeigt `Noch kein BTB`.
- bewusstes `BTB bearbeiten` reaktiviert exakt dieselbe `btbId`.
- moderne BTB-Fotos werden strikt über dieselbe `btbId` zugeordnet.
- Nutzerprofil lokal mit persönlicher Telefonnummer/E-Mail funktioniert.
- App-interner PDF-Viewer funktioniert.

## Verbindliches BTB-Kernmodell
### Identität
- ein fachlicher BTB = dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie Identität.
- mehrere BTBs desselben Teams am selben Kalendertag sind erlaubt.
- Kamera/Foto, BTB-Kopf, Abschluss und Verlauf verwenden denselben kanonischen aktiven `btbId`-Kontext.
- mehrere Baustellen können lokal vorhanden sein; genau eine ist jeweils aktuell; Daten dürfen sich nicht vermischen.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- spätere fachliche Änderungen werden immer gegen dieses erste Original verglichen.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter sind Kopfdaten und bleiben im PDF schwarz.
- fachliche neue/geänderte Inhalte rot; entfernte Originalinhalte rot als `(entfernt) ...`.
- neues PDF ersetzt das vorige PDF derselben `btbId`, nicht das Original.
- nach erfolgreichem Abschluss wird die aktive `btbId` gelöst.
- abgeschlossener BTB wird nur über Verlauf bewusst wieder aktiviert.
- nach dem ersten Abschluss darf Wetter nicht mehr automatisch aktualisiert werden; spätere Änderung nur bewusst/manuell.

### Fotos
- normale moderne BTB-Fotos erhalten beim Speichern die aktive `btbId`.
- moderner BTB sieht nur Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` gehören nie später durch Datum/Uhrzeit zu einem modernen BTB.
- Zeitfenster-Fallback nur für echte Legacy-BTBs ohne `btbId`.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses gehört zum unveränderlichen Original.
- nachträglich hinzugefügte BTB-Fotos invalidieren den Abschluss und werden im Anhang rot markiert.
- Fotozeilen/Bildreferenzen im Hauptteil müssen Original/Nachtrag/Entfernt korrekt unterscheiden.

## Teams / Baustellen / Sync – offen/teilweise vorbereitet
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs gemäß Projektregeln baustellenweit.
- Baustellenkarte soll Teamumfang und letzten Sync zeigen.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- Auto-Sync: manuell / stündlich / täglich plus Netzart `nur WLAN` / `nur Mobilfunk` / `beides`.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync bleibt offen.
- serverseitiges Entfernen/Verlassen und Zwei-Geräte-Konflikttests bleiben offen.

## Nutzerprofil / Firma
Praktisch bestätigt:
- Benutzername wird angezeigt.
- persönliche Telefonnummer und persönliche E-Mail können auf Schema 10 lokal gespeichert werden und bleiben nach Neustart erhalten.
- Firmenprofil bleibt getrennt und serverseitig zentral.
- Firmenlogo kommt zentral vom Server und wird für Geräte derselben Firma gecached.

Schema 11 vorbereitet, noch nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`

## Spätere Fachfunktionen aus Benutzerplanung
- SKV-Abnahme inkl. GPS.
- BTB auch unter Windows/Laptop bearbeiten; dort ebenfalls Abnahmen und Mängel.
- Spracheingabe.
- vereinfachte Mangelaufnahme: `Was? -> Wo? (aktueller km / km ansagen) -> Was ist los?`.
- `Mein Ordner`: selbst angelegte Ordner mit anderen Nutzern synchronisieren; `nur lesen` oder `lesen & bearbeiten`.
- externe Fotos importieren/einfügen.
- Wetterdarstellung um Niederschlagsmenge `l/m²`/mm sinnvoll ergänzen.
- Aktivierung/QR übernimmt vorhandene Benutzer-/Firmenstammdaten automatisch vom Server.
- Ablauf-/Lizenzhinweis wie `LÄUFT BALD AB – noch 2 Tage`.
- Support-/Gerätekennung und nur maskierten Schlüsselhinweis anzeigen; keine Geheimwerte.
- zentrales Firmenlogo für alle Nutzer derselben Firma.
- Streckendaten/Kilometrierung weiterführen; Originalvermessungsdaten unverändert lassen.

## Nächster sinnvoller Arbeitsschritt
1. Branch in GitHub Desktop aktualisieren, lokale 200035-Sourceänderungen dabei behalten.
2. `.github/scripts/patch_200036.ps1` ausführen.
3. `PATCH_200036_FERTIG` prüfen.
4. Android 200036 lokal bauen.
5. APK als Update installieren, App-Daten nicht löschen.
6. obige 200036-Testliste durchführen und neue Zeitraum-/Gesamt-ZIPs bereitstellen.
7. Bei Erfolg lokale Sourceänderungen committen/pushen und Projektstand auf `200036 getestet` aktualisieren.
8. Produktiven NAS weiterhin nicht anfassen.
