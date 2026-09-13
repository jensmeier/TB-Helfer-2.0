# TB-Helfer – aktueller Projektstand

Stand: 2026-09-13

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
- aktueller Android-Entwicklungsstand: **versionCode 200033**, `versionName 1.3`.
- Android 200033 ist vorbereitet, aber noch **nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, Schema 10.
- in der Android-200025-bis-200033-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen.

## Praktisch bestätigter Android-Stand bis 200032
- Debug-APK wurde lokal gebaut/signiert und per ADB installiert; bekannte 7 Kotlin/Java-Warnungen bleiben eigener Aufräumpunkt.
- bloßes Öffnen erzeugt keinen fachlichen BTB; `Noch kein BTB` funktioniert.
- grünes `+` legt erst nach ausdrücklichem `Neuer BTB` eine neue stabile `btbId` an.
- mehrere BTBs am selben Kalendertag funktionieren grundsätzlich.
- erster erfolgreicher `BTB Abschluss` bleibt unveränderliches Original.
- spätere fachliche Textänderungen bleiben gegenüber diesem ersten Original rot.
- gelöschter Originaltext erscheint rot als `(entfernt) ...`.
- App-interner PDF-Viewer funktioniert inkl. Safe-Areas/Seitennavigation.
- `PDF Entwurf` öffnet den internen PDF-Viewer.
- lokales Nutzerprofil funktioniert: Name, persönliche Telefonnummer und E-Mail bleiben erhalten.
- Foto-Zuordnung über `btbId` funktioniert grundsätzlich; getestete Zähler zeigten u. a. `3/3`, `4/4`, `5/5`, `7/7`.
- neue Fotos nach erstem Abschluss werden im Fotoanhang rot mit `ÄNDERUNG ZUM ORIGINAL` markiert.
- Fotoänderung invalidiert das aktuelle Abschluss-PDF; Status wird `Nachbearbeitung · Abschluss-PDF ungültig`.
- `BTB löschen` im Verlauf ist vorgesehen/umgesetzt; echte Baustellenfotos bleiben erhalten und werden nur von der BTB-Zuordnung gelöst.
- `ZIP teilen` im Verlauf verwendet den bestehenden Tages-/Baustellenexport mit BTB, Bildern, Mängeln, Abnahmen und Gesamtlisten.
- kompakter BTB-Kopf sowie `Aktuellen BTB bearbeiten` funktionieren grundsätzlich.
- `Details anzeigen` ist in 200032 funktional schreibgeschützt.
- Arbeitszeitänderung bleibt an derselben `btbId`; Anzeige-/Reloadpfad wurde modernisiert.
- Foto-Eingabemaske und Foto-Speicherung nutzen für moderne BTBs den kanonischen aktiven `btbId`-Kontext; der frühere unabhängige Zeit-/Schicht-Leseweg war Ursache für fehlende Firma trotz korrekter Foto-Zuordnung.

## Gerätetest 200032 – neu bestätigte Fehler/Entscheidungen
### Nachtmodus
- `BTB-Details` war im Nachtmodus teilweise kaum lesbar, weil Teile noch feste Schwarz/Dunkelgrau-Farben verwendeten.
- im Editor `4. Ausgeführte Arbeiten` waren insbesondere Foto-Pills sowie einzelne Unterbaustellen-/Ortslabels zu kontrastarm.
- PDF selbst bleibt bewusst weiß; nur die App-Oberfläche muss Day/Night-lesbar sein.

### Abschluss-Lebenszyklus – neue verbindliche Entscheidung
- Nach einem **erfolgreichen `BTB Abschluss` darf kein BTB mehr aktiv sein**.
- der abgeschlossene BTB wird nicht gelöscht: `btbId`, aktueller Datensatz, unveränderliches Original, Abschlussmetadaten und PDF bleiben im Verlauf gespeichert.
- der aktive Pointer wird nach erfolgreichem Abschluss gelöst; die BTB-Seite zeigt danach `Noch kein BTB`.
- spätere Nachbearbeitung erfolgt bewusst über `BTB-Verlauf -> BTB bearbeiten`; dort wird exakt diese `btbId` wieder aktiviert.
- nach dem nächsten erfolgreichen Abschluss wird der aktive Pointer erneut gelöst.
- damit können spätere Texte/Fotos nicht versehentlich in einen bereits abgeschlossenen BTB laufen.

### Kamera ohne aktiven BTB – neue verbindliche Entscheidung
Wenn `Noch kein BTB` aktiv ist und die Kamera aufgerufen wird, erscheint eine Auswahl:
1. `Nur Baustellenfoto` – Foto wird ohne BTB-Zuordnung gespeichert.
2. `BTB starten/öffnen` – danach Auswahl `Neuen BTB starten` oder `Vorhandenen BTB öffnen`.

### Fotos / PDF / BTB-Ansicht
- nachträglich hinzugefügte Bilder sind im PDF-Anhang vorhanden und dort bereits rot markiert.
- im Hauptteil von Punkt 4 werden Bildreferenzen wie `[Bild 3]`, `[Bild 4]` noch **nicht zuverlässig rot** dargestellt; fachlich müssen nach dem Originalabschluss hinzugefügte Bildreferenzen rot sein.
- im Abschnitt-4-Editor fehlten in einem Gerätetest zwei nachträglich hinzugefügte Bilder, obwohl PDF-Anhang und Fotozähler sie kannten. Der historische Abschnittseditor besitzt noch eigene Foto-/Zeitfensterlogik und muss vollständig auf `btbId` umgestellt werden.
- mindestens ein vermeintlicher Datenverlust war nur ein Darstellungsproblem: Einträge waren im PDF vorhanden, im Nachtmodus aber praktisch unsichtbar.

## Android 200033 – aktueller vorbereiteter Patch
### Neue/angepasste Dateien
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActivePointerControl.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPatch200033Installer.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt`
- `app/src/main/java/de/tbhelfer/ui/HauptActivity.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values-night/colors.xml`
- `app/src/main/res/drawable/bg_pill.xml`
- `app/src/main/res/drawable-night/bg_section_editor_card.xml` – neu
- `app/build.gradle.kts`
- diese Projektstand-MD

### 1. Abschluss löst aktiven BTB
- 200033 übernimmt den Abschluss-Button nach den älteren Installern.
- PDF/Original-/Foto-Abschlusslogik bleibt gleich.
- erst nach erfolgreichem PDF-Schreiben und erfolgreicher `abschlussBestaetigen(...)`-Bestätigung wird `.active_btb.json` gelöst.
- zusätzlich wird die zuletzt abgeschlossene `btbId` lokal als inaktiv markiert, damit historische Datum/Schicht-Ladewege denselben Abschluss nicht beim nächsten Öffnen ungefragt wieder aktivieren.
- wenn derselbe BTB ausdrücklich aus dem Verlauf mit `BTB bearbeiten` geöffnet wird, ist die Reaktivierung erlaubt und der Inaktiv-Marker wird aufgehoben.
- nach Abschluss werden BTB-Kopf/Details/PDF-Aktionen auf `Noch kein BTB` gestellt; Abschnittsvorschauen sind ohne aktiven BTB nicht bearbeitbar.

### 2. Kamera-Guard ohne aktiven BTB
- roter Kamera-Schnellbutton prüft zuerst `BtbActiveContext`.
- mit aktivem BTB: normale Kamera.
- ohne aktiven BTB: Dialog `Nur Baustellenfoto` / `BTB starten/öffnen`.
- `Nur Baustellenfoto` navigiert zur Kamera ohne aktiven BTB; `FotoRepository` weist deshalb keine `btbId` zu.
- `BTB starten/öffnen` erlaubt bewusste Neuanlage oder Öffnen des BTB-Verlaufs.

### 3. Nachtmodus lesbarer
- `BTB-Details` verwendet in 200033 Theme-Farben `tb_on_surface` / `tb_text_secondary` statt festem Schwarz/Dunkelgrau.
- `tb_text_secondary` ist im Nachtmodus hell definiert.
- Foto-Pills verwenden `tb_pill_bg` mit eigener Day/Night-Farbe.
- der Abschnittseditor erhält im Nachtmodus einen kontrastreicheren Kartenhintergrund, damit sowohl historische dunkelgraue Labels als auch Theme-helle Texte lesbar bleiben.
- kein Redesign; nur gezielte Lesbarkeitskorrektur.

### Statischer Prüfstand 200033
- Branch/Projekt-MDs/Draft-PR/letzter Commit vor Änderung geprüft.
- beim Schreiben wurde ein Syntaxfehler in `HauptActivity.openActivation()` sofort gefunden und korrigiert.
- `BtbActiveContext` um sicheren Aufruf für die aktuell gesetzte Baustelle ergänzt.
- Flow-Lesewege im 200033-Installer auf vorhandenes `.first()` vereinfacht.
- **noch kein lokaler Gradle-Build/ADB-Test**; 200033 nicht als kompiliert bestätigen.
- NAS/DB nicht verändert.

## BTB – verbindliches Kernmodell
### Identität
- ein fachlicher BTB = eine dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie technische Identität.
- mehrere BTBs desselben Teams am selben Kalendertag sind erlaubt.
- Kamera/Foto, BTB-Kopf, Abschluss und Verlauf müssen denselben kanonischen aktiven `btbId`-Kontext verwenden.
- mehrere Baustellen können lokal vorhanden sein; genau eine Baustelle ist jeweils aktuell, BTB-Daten verschiedener Baustellen dürfen sich nie vermischen.

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
- moderne normale BTB-Fotos erhalten die beim Speichern aktive `btbId`.
- explizite Foto-`btbId` ist stärker als Arbeitszeitfenster.
- Foto-ID-Menge des ersten Abschlusses ist Teil des unveränderlichen Originals.
- nachträglich hinzugefügte Fotos invalidieren den Abschluss und werden im späteren Anhang rot markiert.
- Bildreferenzen im Haupttext müssen ebenfalls Original vs. nachträglich unterscheiden; Renderer-Feinschliff noch offen.

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
Diese Punkte sind bewusst noch nicht der aktuelle BTB-Fix, dürfen aber nicht verloren gehen:
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
- Android 200033 lokal bauen/installieren/testen.
- Abschnitt-4-Fotoeditor vollständig von historischem Zeitfenster auf moderne `btbId`-Fotoauswahl umstellen; in einem Test fehlten dort zwei nachträgliche Bilder trotz korrektem PDF-Anhang.
- `BautagebuchPdf.kt`: nachträgliche Bildreferenzen im Haupttext rot darstellen; Fotoanhang ist bereits rot markiert.
- `BautagebuchPdf.kt`: Strukturüberschriften werden teilweise noch zu breit rot; Überschriften sollen schwarz bleiben.
- großer PDF-Renderer besitzt weiterhin historischen Zeitfenster-Vorfilter; vollständige btbId-Bereinigung prüfen.
- Einzel-BTB-DOCX besitzt noch nicht dieselbe Rotformatierung wie PDF.
- BTB 1 ↔ BTB 2 und Baustelle A ↔ Baustelle B weiter praktisch testen.
- 7 bekannte Compilerwarnungen separat beseitigen.
- Schema 11 erst nach frischem verifiziertem Backup produktiv migrieren.

## Nächster Test für Android 200033
1. `git pull`
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
3. nur bei erfolgreichem Build per ADB als Update installieren; App-Daten nicht löschen.
4. Nachtmodus: `Details anzeigen` sowie Abschnitt-4-Editor/Pills müssen lesbar sein.
5. frischen BTB anlegen, Text + Fotos erfassen, `BTB Abschluss`.
6. nach erfolgreichem Abschluss muss PDF öffnen; danach BTB-Seite = `Noch kein BTB`, alter BTB darf nicht automatisch wieder aktiv werden.
7. Kamera bei `Noch kein BTB`: Dialog `Nur Baustellenfoto` / `BTB starten/öffnen` prüfen.
8. `Nur Baustellenfoto` aufnehmen -> darf keine `btbId` des abgeschlossenen BTB bekommen.
9. im BTB-Verlauf den abgeschlossenen BTB bewusst `BTB bearbeiten` -> exakt dieselbe `btbId` wird wieder aktiv.
10. Nachbearbeitung + neuer Abschluss -> danach wieder `Noch kein BTB`.
11. prüfen, ob nachträgliche Bilder im Abschnitt-4-Editor noch fehlen; wenn ja, nächster Patch gezielt auf historischen Fotoeditor/Renderer.
12. produktiven NAS weiterhin nicht anfassen.
