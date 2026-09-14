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
- letzter Branch-Commit vor dieser Doku-Aktualisierung: `7322114825db0844c4b7425316d1a760c92549a6` (`fix: repair 200036 XML patch continuation`).
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.

## WICHTIG – lokaler PC-Stand Android 200036 erfolgreich gebaut, noch nicht gepusht
Auf dem Windows-PC wurden die lokalen 200035-Änderungen beibehalten und anschließend 200036 angewendet. Der lokale Source-Stand enthält jetzt zusätzlich die 200036-Anpassungen in:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExportPaket.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEinzelZip.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/MergeSafeBaustellenExport.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt`
- `app/src/main/res/layout/dialog_schicht.xml`
- `app/src/main/res/layout/fragment_bautagebuch.xml`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt` (200035-Grundlage)
- `app/build.gradle.kts` (`versionCode = 200036`, `versionName = 1.3`)

Diese lokalen Änderungen **noch nicht verwerfen/resetten und noch nicht committen/pushen**, bis der Gerätetest 200036 abgeschlossen ist.

## Android 200036 – Patch und Build bestätigt
Verwendete Skripte:
- `.github/scripts/patch_200036.ps1`
- `.github/scripts/patch_200036_fix.ps1`
- `.github/scripts/patch_200036_continue.ps1`

Der erste Patchlauf scheiterte an zu strengem Textvergleich im PDF-Finder. Danach wurde `BautagebuchSpeicher.neuestePdfFuerVerlauf()` gezielt repariert. Ein weiterer Fehler im XML-Patch setzte einen Kommentar innerhalb des geöffneten `MaterialCardView`-Tags in `fragment_bautagebuch.xml`; dieser XML-Fehler wurde im Fortsetzungspatch behoben.

Letzter erfolgreicher Patchlauf meldete:
```text
BautagebuchSpeicher PDF-Finder: bereits vorbereitet
BautagebuchPdf.kt: 200035-Basis erkannt
Starte 200036 ab Schritt 2 ...
...
build.gradle.kts: 200036 bereits gesetzt
PATCH_200036_FERTIG
fragment_bautagebuch.xml: XML-Kommentar aus Starttag entfernt
PATCH_200036_XML_FIX_FERTIG
```

Danach direkter Gradle-Build:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat :app:assembleDebug --rerun-tasks
```
Ergebnis:
- `BUILD SUCCESSFUL in 2m 13s`
- 38 Tasks ausgeführt.
- Debug-APK: `app\build\outputs\apk\debug\app-debug.apk`
- bekannte Compiler-/Deprecated-Warnungen bleiben separater Aufräumpunkt; keine davon verhinderte den Build.

## Zweck und Inhalt Android 200036
Anlass waren zwei echte Exportfehler aus dem Test vom 14.09.2026:
- Zeitraum-ZIP und Gesamt-Archiv enthielten keinen BTB-Abschluss-PDF.
- Zeitraum-DOCX enthielt nicht dieselben Kopfdaten wie der Gesamt-Export; Firma, verantwortlicher TB und Schicht fehlten.

200036 ändert:
- moderner Abschluss-PDF-Finder über `BtbAbschlussPdfFinder` statt Suche nach kompletter UUID im Dateinamen;
- Zeitraum-/Gesamtexport mit PDF-Fallback über den kanonischen Finder;
- DOCX-Kopf mit Firma, verantwortlichem TB, Schicht und `BTB-ID`;
- moderne BTB-Fotos im Export strikt nach gleicher `btbId`;
- stabile BTB-Token in PDF-/DOCX-Dateinamen, damit mehrere BTBs derselben Schicht am selben Tag nicht als fachliches `(2)` kollidieren;
- Einzel-BTB-ZIP mit vollständigerem Dokumentkopf;
- MergeSafe-Export behält stabilen BTB-Suffix;
- Start/Ende beim neuen BTB über grafischen `TimePickerDialog` statt Listen-Dropdown;
- Abschlussdialog erklärt erstes unveränderliches Original und rote Nachbearbeitung;
- Karte `Neuen BTB starten` nutzt Theme-Farben für bessere Dunkelmodus-Lesbarkeit;
- Version lokal auf `200036`.

Die 200035-PDF-Original-Diff-Logik bleibt Grundlage:
- neue Fotozeilen rot;
- Original-/Nachtragsgruppen getrennt;
- entfernte Originalinhalte rot `(entfernt)`;
- allgemeines Baustellenfoto ohne `btbId` bleibt außerhalb moderner BTBs.

## Nächster verbindlicher Schritt: Gerätetest Android 200036
APK als **Update** installieren; **App-Daten nicht löschen**.

Prüfen:
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
   - Abschluss-PDF unter `BTB/<Datum>/` vorhanden;
   - DOCX enthält Firma, verantwortlichen TB, Schicht und BTB-ID.
7. Gesamt-Archiv exportieren:
   - Abschluss-PDF bleibt enthalten;
   - keine BTB-Datei verschwindet durch Normalisierung.
8. Wenn möglich zwei BTBs am selben Tag/gleicher Schicht testen: Dateinamen durch stabilen BTB-Token unterscheidbar; kein fachliches `(2)` als Identität.

Erst wenn dieser Gerätetest passt, lokale Sourceänderungen committen/pushen und diesen Stand auf `200036 getestet` aktualisieren.

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
- Kamera-Guard ohne aktiven BTB funktioniert.
- `Nur Baustellenfoto` / `BTB starten/öffnen` funktioniert.
- allgemeines Baustellenfoto ohne `btbId` läuft nicht in einen modernen BTB hinein.
- abgeschlossenen BTB über Verlauf erneut bearbeiten funktioniert.
- Nachtmodus `Dunkel/Hell/System` bleibt gespeichert.
- nach erfolgreichem `BTB Abschluss` wird die aktive `btbId` gelöst.
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

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- spätere fachliche Änderungen immer gegen dieses erste Original vergleichen.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter sind Kopfdaten und bleiben im PDF schwarz.
- fachliche neue/geänderte Inhalte rot; entfernte Originalinhalte rot als `(entfernt) ...`.
- neues PDF ersetzt das vorige PDF derselben `btbId`, nicht das Original.
- nach erfolgreichem Abschluss aktive `btbId` lösen; abgeschlossenen BTB nur bewusst über Verlauf wieder aktivieren.
- nach erstem Abschluss Wetter nicht mehr automatisch aktualisieren; spätere Änderung nur bewusst/manuell.

### Fotos
- moderne BTB-Fotos erhalten beim Speichern die aktive `btbId`.
- moderner BTB sieht nur Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` gehören nie später durch Datum/Uhrzeit zu einem modernen BTB.
- Zeitfenster-Fallback nur für echte Legacy-BTBs ohne `btbId`.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses gehört zum unveränderlichen Original.
- nachträglich hinzugefügte BTB-Fotos werden im Anhang und in den fachlichen Fotozeilen als Nachtrag markiert.

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
- persönliche Telefonnummer und persönliche E-Mail bleiben lokal erhalten.
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
- Wetterdarstellung um Niederschlagsmenge `l/m²`/mm ergänzen.
- Aktivierung/QR übernimmt vorhandene Benutzer-/Firmenstammdaten automatisch vom Server.
- Ablauf-/Lizenzhinweis wie `LÄUFT BALD AB – noch 2 Tage`.
- Support-/Gerätekennung und nur maskierten Schlüsselhinweis anzeigen; keine Geheimwerte.
- zentrales Firmenlogo für alle Nutzer derselben Firma.
- Streckendaten/Kilometrierung weiterführen; Originalvermessungsdaten unverändert lassen.

## Nächster sinnvoller Arbeitsschritt
1. APK 200036 als Update installieren, App-Daten nicht löschen.
2. obige Gerätetestliste vollständig durchführen.
3. Zeitraum-ZIP und Gesamt-Archiv erneut erzeugen und prüfen.
4. Bei Erfolg lokale Sourceänderungen committen/pushen und Projektstand auf `200036 getestet` aktualisieren.
5. Danach offene UI-/Sync-Punkte weiterführen; produktiven NAS weiterhin nicht anfassen.
