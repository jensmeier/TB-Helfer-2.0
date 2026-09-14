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
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.
- aktueller Android-Entwicklungsstand: **versionCode 200034**, `versionName 1.3`.
- Android 200034 ist **vorbereitet, aber noch nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, Schema 10.
- in der Android-200025-bis-200034-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen.

## Praktisch bestätigter BTB-Stand bis Android 200032
- Debug-APK lokal gebaut/signiert und per ADB installiert; bekannte 7 Kotlin/Java-Warnungen bleiben eigener Aufräumpunkt.
- bloßes Öffnen erzeugt keinen fachlichen BTB; `Noch kein BTB` funktioniert.
- grünes `+` legt erst nach ausdrücklichem `Neuer BTB` eine stabile `btbId` an.
- mehrere BTBs am selben Kalendertag funktionieren grundsätzlich.
- erster erfolgreicher `BTB Abschluss` bleibt unveränderliches Original.
- spätere fachliche Textänderungen bleiben gegenüber diesem ersten Original rot.
- gelöschter Originaltext erscheint rot als `(entfernt) ...`.
- App-interner PDF-Viewer funktioniert inkl. Safe-Areas/Seitennavigation.
- `PDF Entwurf` öffnet den internen PDF-Viewer.
- Nutzerprofil: Name, persönliche Telefonnummer und E-Mail bleiben lokal erhalten.
- Foto-Zuordnung über `btbId` funktioniert grundsätzlich; getestete Zähler zeigten u. a. `3/3`, `4/4`, `5/5`, `7/7`.
- neue Fotos nach erstem Abschluss werden im Fotoanhang rot als Änderung zum Original markiert.
- Fotoänderung invalidiert das aktuelle Abschluss-PDF; Status wird `Nachbearbeitung · Abschluss-PDF ungültig`.
- `BTB löschen` im Verlauf ist umgesetzt; echte Baustellenfotos bleiben erhalten und werden nur von der BTB-Zuordnung gelöst.
- `ZIP teilen` im Verlauf verwendet den bestehenden Tages-/Baustellenexport mit BTB, Bildern, Mängeln, Abnahmen und Gesamtlisten.
- kompakter BTB-Kopf sowie `Aktuellen BTB bearbeiten` funktionieren grundsätzlich.
- `Details anzeigen` ist funktional schreibgeschützt.
- Arbeitszeitänderung bleibt an derselben `btbId`.
- Foto-Eingabemaske und Foto-Speicherung nutzen für moderne BTBs den kanonischen aktiven `btbId`-Kontext; der frühere unabhängige Zeit-/Schicht-Leseweg war Ursache für fehlende Firma trotz korrekter Foto-Zuordnung.

## Android 200033 – Gerätetest vom 14.09.2026
### Bestanden
- nach erfolgreichem `BTB Abschluss` wird der aktive BTB gelöst; zurück im Bautagebuch steht `Noch kein BTB`.
- derselbe abgeschlossene BTB bleibt im Verlauf vollständig vorhanden.
- bewusstes `BTB bearbeiten` lädt den bisherigen BTB mit seinen Daten grundsätzlich wieder.
- nach einem weiteren erfolgreichen Abschluss wird der aktive BTB erneut gelöst.
- nachträglich hinzugefügte BTB-Fotos sind im PDF-Anhang vorhanden und dort rot markiert.

### Im Test gefundene Restfehler
1. **Kamera-Guard nur im BTB-Schnellbutton:**
   - im Bautagebuch erschien ohne aktiven BTB korrekt die Auswahl `Nur Baustellenfoto` / `BTB starten/öffnen`.
   - im Startmenü über `Aktuelle Baustelle (...)` wurde die Kamera weiterhin direkt geöffnet.
2. **Allgemeines Baustellenfoto lief später in alten BTB:**
   - Foto wurde ohne aktiven BTB aufgenommen.
   - nach `BTB-Verlauf -> BTB bearbeiten` erschien dieses allgemeine Foto im alten BTB.
   - Ursache: historische Foto-Lesewege akzeptierten unzugeordnete Fotos weiterhin über Datum/Uhrzeit, obwohl der geöffnete BTB bereits eine moderne `btbId` hat.
3. **Wieder geöffneter BTB ließ sich zeitweise nicht erneut abschließen:**
   - Ursache/Risiko: Rennen zwischen bewusst gesetztem Aktiv-Pointer und 200033-Inaktivmarker-Watcher.
4. **Nachtmodus-Einstellung sprang sofort zurück auf `System (Auto)`:**
   - Ursache bestätigt in `AnsichtFragment`: unbedingtes initiales Speichern sowie Activity-Recreation durch `setDefaultNightMode` konnten `SYSTEM` zurückschreiben, bevor DARK/LIGHT persistent war.
5. **PDF-Hauptteil:**
   - neue Fotos sind im Anhang rot, aber Bildreferenzen/Fototext unter Punkt 4 bleiben teilweise schwarz.
   - dieser Renderer-Punkt ist weiterhin offen und nicht Teil des 200034-Isolationsfixes.

## Android 200034 – aktueller vorbereiteter Patch
### Betroffene Dateien
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbFotoQueryScope.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPatch200034Installer.kt` – neu
- `app/src/main/java/de/tbhelfer/datenbank/FotoRepository.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbFotoBtbFilter.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoCounterFix.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/start/StartmenueFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/einstellungen/ansicht/AnsichtFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/HauptActivity.kt`
- `app/build.gradle.kts`
- `docs/TB-HELFER_PROJEKTREGELN.md`
- diese Datei

### 1. Kamera-Guard jetzt auch im Startmenü
- `StartmenueFragment` prüft vor `Aktuelle Baustelle -> Kamera` den kanonischen aktiven BTB.
- mit aktivem BTB: Kamera normal.
- ohne aktiven BTB: Auswahl `Nur Baustellenfoto` / `BTB starten/öffnen`.
- `BTB starten/öffnen` bietet `Neuen BTB starten` oder `Vorhandenen BTB öffnen` und leitet in den BTB-Bildschirm mit einmaliger Aktion weiter.
- spezielle Kamera-Flows wie Mängel-/Abnahmenachweis werden dadurch nicht ersetzt.

### 2. Moderne BTB-Fotos strikt nach `btbId`
- neue `BtbFotoQueryScope` begrenzt historische `fotosFuerBaustelleOnce()`-Lesepfade während eines geöffneten modernen BTB auf exakt dessen `btbId`.
- der Scope gilt nur im BTB-Kontext und wird beim Verlassen wieder entfernt; globale Foto-Flows/Suche bleiben unberührt.
- `BtbFotoBtbFilter` schließt bei modernem BTB jetzt auch **unzugeordnete** allgemeine Baustellenfotos aus, nicht nur Fotos anderer BTBs.
- `BtbPhotoCounterFix` zählt bei modernem BTB nur `assigned == currentBtbId`.
- Zeitfenster-Fallback ist nur noch für echte Legacy-BTBs ohne `btbId` vorgesehen.
- Ziel: Ein allgemeines Baustellenfoto darf nach späterem Öffnen eines alten modernen BTB weder Zähler, Abschnitt-4-Editor noch PDF dieses BTB beeinflussen.

### 3. Bewusstes Wiederöffnen race-frei
- `BTB-Verlauf -> BTB bearbeiten` aktiviert die gewählte `btbId` und löscht **vor der Navigation** den Abschluss-Inaktivmarker.
- `BtbSchnellwechselInstaller` macht dasselbe.
- Navigation übergibt zusätzlich `baustelleId`, damit 200034 den Foto-Scope bereits beim Aufbau des BTB setzen kann.
- 200034-Watcher hält Scope und explizite Reaktivierung zusammen.

### 4. Nachtmodus-Persistenz
- `AnsichtFragment` speichert nicht mehr ungefragt beim Initialisieren.
- Radio-Buttons werden zuerst aus dem gespeicherten Zustand aufgebaut (`controlsReady`-Guard).
- bei Nutzerwahl wird zuerst DataStore + synchroner Startup-Cache gespeichert und **erst danach** `AppCompatDelegate.setDefaultNightMode(...)` aufgerufen.
- Ziel: `Dunkel`/`Hell` darf nach der Activity-Neuerstellung nicht wieder auf `System (Auto)` springen.

### 5. Statischer Prüfstand 200034
- vor Änderung Projektregeln, aktueller Stand, Draft-PR #5, Branch-Head und letzter Commit geprüft.
- `main` blieb unverändert bei `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- Branch-Code auf offensichtliche API-/Typfehler geprüft; neue Komponenten absichtlich klein gehalten, um den großen historischen `BautagebuchFragment` nicht riskant komplett umzuschreiben.
- **kein lokaler Gradle-Build/ADB-Gerätetest für 200034 erfolgt**; nicht als kompiliert bestätigen.
- NAS/DB nicht verändert.

## Verbindliches BTB-Kernmodell
### Identität
- ein fachlicher BTB = dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie Identität.
- mehrere BTBs desselben Teams am selben Kalendertag sind erlaubt.
- Kamera/Foto, BTB-Kopf, Abschluss und Verlauf müssen denselben kanonischen aktiven `btbId`-Kontext verwenden.
- mehrere Baustellen können lokal vorhanden sein; genau eine ist jeweils aktuell; Daten verschiedener Baustellen dürfen sich nie vermischen.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- spätere fachliche Änderungen werden immer gegen dieses erste Original verglichen.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter bleiben im PDF schwarz.
- fachliche neue/geänderte Inhalte rot; entfernte Originalinhalte rot als `(entfernt) ...`.
- neues PDF ersetzt das vorige PDF derselben `btbId`, nicht das Original.
- nach erfolgreichem Abschluss wird die aktive `btbId` gelöst.

### Fotos
- moderne normale BTB-Fotos erhalten beim Speichern die aktive `btbId`.
- ein moderner BTB sieht nur Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` gehören nie nachträglich durch Zeitfenster zu einem modernen BTB.
- Zeitfenster-Fallback nur für historische BTBs ohne `btbId`.
- Foto-ID-Menge des ersten Abschlusses ist Teil des unveränderlichen Originals.
- nachträglich hinzugefügte BTB-Fotos invalidieren den Abschluss und werden im späteren Anhang rot markiert.
- Bildreferenzen im Haupttext müssen ebenfalls Original vs. nachträglich unterscheiden; Renderer-Feinschliff weiterhin offen.

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

## Teams / Baustellen / Sync – offen/teilweise vorbereitet
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs baustellenweit gemäß Regeln.
- Baustellenkarte soll Teamumfang und letzten Sync zeigen, z. B. `2 TB · 4 Helfer` und `Zuletzt synchronisiert ...`.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- Auto-Sync: manuell / stündlich / täglich plus Netzart nur WLAN / nur Mobilfunk / beides.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync bleibt offen.
- serverseitiges Entfernen/Verlassen und Zwei-Geräte-Konflikttests bleiben offen.

## Nicht vergessen – spätere Fachfunktionen aus Benutzerplanung
- SKV-Abnahme inkl. GPS.
- BTB auch unter Windows/Laptop bearbeiten; dort ebenfalls Abnahmen und Mängel bearbeiten.
- Spracheingabe.
- vereinfachte Mangelaufnahme: `Was? -> Wo? (aktueller km / km ansagen) -> Was ist los?`.
- `Mein Ordner`: selbst angelegte Ordner mit anderen Nutzern synchronisieren; pro Freigabe `nur lesen` oder `lesen & bearbeiten`.
- externe Fotos importieren/einfügen.
- Wetterdarstellung um Niederschlagsmenge `l/m²`/mm sinnvoll ergänzen.
- Aktivierung/QR soll vorhandene Benutzer-/Firmenstammdaten automatisch vom Server übernehmen.
- Ablauf-/Lizenzhinweis wie `LÄUFT BALD AB – noch 2 Tage` anzeigen.
- Support-/Gerätekennung und nur **maskierten** Schlüsselhinweis für Supportfälle anzeigen; keine geheimen Keys im Klartext.
- zentrales Firmenlogo für alle Nutzer derselben Firma.
- Streckendaten/Kilometrierung und berechnete Abschnitte weiterführen; Originalvermessungsdaten unverändert lassen.

## Bekannte offene Fehler / nächste technische Blöcke
- Android 200034 lokal bauen/installieren/testen.
- `BautagebuchPdf.kt`: nachträgliche Bildreferenzen/Fototext im Hauptteil rot darstellen; Fotoanhang ist bereits rot markiert.
- `BautagebuchPdf.kt`: Strukturüberschriften werden teilweise noch zu breit rot; Überschriften sollen schwarz bleiben.
- Einzel-BTB-DOCX besitzt noch nicht dieselbe Rotformatierung wie PDF.
- prüfen, ob der begrenzte 200034-Foto-Scope alle Abschnitt-4-Zeitfensterreste im Gerätetest zuverlässig neutralisiert; danach Altcode langfristig direkt auf btbId umbauen.
- BTB 1 ↔ BTB 2 und Baustelle A ↔ Baustelle B weiter praktisch testen.
- 7 bekannte Compilerwarnungen separat beseitigen.
- Schema 11 erst nach frischem verifiziertem Backup produktiv migrieren.

## Nächster Test für Android 200034
1. `git pull`
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
3. nur bei erfolgreichem Build per ADB als Update installieren; App-Daten nicht löschen.
4. Einstellungen -> Ansicht -> `Dunkel`; nach Neuaufbau der Activity muss `Dunkel` gewählt bleiben. Ansicht verlassen/erneut öffnen und nochmals prüfen.
5. ohne aktiven BTB im **Startmenü** `Aktuelle Baustelle (...)` drücken -> Variant-3-Dialog muss erscheinen.
6. `Nur Baustellenfoto` aufnehmen.
7. abgeschlossenen BTB über `BTB-Verlauf -> BTB bearbeiten` öffnen -> allgemeines Foto aus Schritt 6 darf weder Zähler noch Abschnitt-4-Editor noch PDF dieses BTB beeinflussen.
8. alte BTB-Fotos müssen weiterhin vollständig vorhanden sein.
9. echte Änderung oder neues BTB-Foto erfassen und `BTB Abschluss` -> Abschluss muss funktionieren; danach wieder `Noch kein BTB`.
10. denselben BTB erneut aus Verlauf öffnen und erneut abschließen, um Reaktivierungs-Rennen auszuschließen.
11. Night-Mode-Lesbarkeit von `BTB-Details` und Abschnitt-4-Editor/Pills prüfen.
12. bekannte rote Bildreferenz im Haupt-PDF noch nicht als 200034-Erfolg erwarten; dieser Renderer-Fix bleibt Folgepatch.
13. produktiven NAS weiterhin nicht anfassen.
