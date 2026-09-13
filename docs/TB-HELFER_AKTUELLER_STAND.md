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
- aktueller Android-Entwicklungsstand: **versionCode 200032**, `versionName 1.3`.
- Android 200032 ist vorbereitet, aber noch **nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, Schema 10.
- in der gesamten Android-200025 bis 200032-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter produktiver Backup-Stand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen.

## Praktisch bestätigter Android-Stand bis 200030
- Debug-APK wurde erfolgreich lokal gebaut/signiert und per ADB installiert; bekannte 7 Kotlin/Java-Warnungen bleiben späterer Aufräumpunkt.
- bloßes Öffnen erzeugt keinen fachlichen BTB; `Noch kein BTB` funktioniert.
- grünes `+` legt erst nach ausdrücklichem `Neuer BTB` eine neue stabile `btbId` an.
- mehrere BTBs am selben Tag funktionieren grundsätzlich.
- erster erfolgreicher `BTB Abschluss` bleibt unveränderliches Original.
- spätere Textänderungen bleiben gegenüber diesem ersten Original rot.
- gelöschter Originaltext erscheint rot als `(entfernt) ...`.
- App-interner PDF-Viewer funktioniert inkl. Safe-Areas/Seitennavigation.
- lokales Nutzerprofil funktioniert: Name, persönliche Telefonnummer und E-Mail bleiben erhalten.
- Foto-Isolation über `btbId` funktioniert im getesteten Verlauf; Zähler zeigten u. a. `3/3`, `5/5` und später weitere korrekte Mengen am selben BTB.
- nach dem ersten Abschluss neue Fotos werden im Fotoanhang rot mit `ÄNDERUNG ZUM ORIGINAL` markiert.
- Fotoänderung invalidiert das aktuelle Abschluss-PDF; Status wird korrekt `Nachbearbeitung · Abschluss-PDF ungültig`.
- kompakter BTB-Kopf und Fenster `Aktuellen BTB bearbeiten` funktionieren.

## Android 200031 – umgesetzt und auf Gerät weiter geprüft
### Umgesetzt
- `PDF Entwurf` öffnet nach Erzeugung sofort den App-internen PDF-Viewer.
- `BTB löschen` im Verlauf mit Sicherheitsabfrage; BTB-/Original-/Abschluss-/Dokumentdaten werden gelöscht, echte Baustellenfotos bleiben erhalten und werden nur von der `btbId` gelöst.
- moderne Fotos mit expliziter `btbId` zählen auch außerhalb der ursprünglich eingetragenen Arbeitszeit zum BTB; nur Legacy-Fotos ohne Zuordnung verwenden das Zeitfenster.
- Arbeitszeit, Personal/Firmen, Geräte/Maschinen und Wetter werden in der PDF-Diff-Darstellung als Kopfdaten schwarz gehalten; Abschnitte 4–8 bleiben fachlicher Originalvergleich.
- nach erstem Abschluss ist automatischer Wetter-Refresh gesperrt; manuelle Bearbeitung über Stift bleibt vorgesehen.
- `ZIP teilen` im BTB-Verlauf verwendet den vorhandenen Baustellen-/Tagesabschluss-Export für den gewählten Kalendertag.

### Gerätetest / neu gefundene Probleme
- neuer Test-BTB auf Baustelle `Heideck` startete korrekt mit `Noch kein BTB` und anschließend bewusster Neuanlage.
- Fotozähler zeigte im frischen BTB korrekt z. B. `3/3` und später `4/4`; Foto→BTB-Zuordnung funktioniert damit grundsätzlich.
- Arbeitszeitänderung wird gespeichert, ist aber in 200031 erst nach Verlassen/Wiederöffnen der Seite zuverlässig sichtbar. Ursache: `detach` + `attach` wurden in derselben Fragment-Transaktion ausgeführt und können sich praktisch aufheben.
- `Details anzeigen` öffnet noch die historische editierbare Kopfdatenansicht. Gewünscht ist nur eine kurze **schreibgeschützte** Zusammenfassung; Bearbeitung ausschließlich über den orangefarbenen Stift.
- beim `BTB Abschluss` konnte die alte versteckte UI-Validierung fälschlich `Datum/Zeit` und `Personal` als fehlend melden, obwohl der kanonische btbId-Datensatz diese Daten bereits enthält. Ursache: `BtbUiSaveBridge` drückte bisher den historischen `btnSpeichern`, der private/stale Fragmentfelder validiert.
- in der Foto-Eingabemaske fehlte bei diesem frischen Test die Firma, obwohl das Foto anschließend korrekt zum BTB gezählt wurde. Ursache bestätigt: **Schreiben der Foto-btbId funktioniert**, aber die Foto-Maske liest Firmen noch aus einem alten Datum-/Uhrzeit-Kandidaten statt aus exakt der aktiven `btbId`.

## Android 200032 – aktueller vorbereiteter Patch
### Neue/angepasste Dateien
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPatch200032Installer.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbUiSaveBridge.kt`
- `app/src/main/java/de/tbhelfer/datenbank/FotoRepository.kt`
- `app/src/main/java/de/tbhelfer/ui/HauptActivity.kt`
- `app/build.gradle.kts`
- `docs/TB-HELFER_PROJEKTREGELN.md`
- diese Projektstand-MD

### Kanonischer aktiver BTB
- `BtbActiveContext` ist die gemeinsame Lesequelle für den aktuell aktiven modernen BTB einer Baustelle.
- Quelle ist immer: aktuelle Baustelle → aktiver Pointer → **`btbId` → `BautagebuchSpeicher.ladenById(...)`**.
- Datum/Uhrzeit/Schicht werden dafür nicht als Identität benutzt.
- `BtbUiSaveBridge.aktuell(...)` verwendet nun diesen kanonischen Kontext.

### Abschluss/Vorschau ohne falsche Altvalidierung
- `BtbUiSaveBridge.speichernUndNeuLaden(...)` drückt nicht mehr den historischen versteckten `btnSpeichern`.
- ohne offenes Eingabefeld wird derselbe aktive BTB direkt per `btbId` neu gelesen.
- wenn beim Klick noch ein echtes Text-Eingabefeld Fokus hatte, wird der Focus beendet und gezielt bis zu 4,5 Sekunden auf einen **neuen `geaendertAmEpochMs` desselben `btbId`** gewartet; es gibt keinen festen 1,7-s-Schlaf als Datenintegritätslogik.
- dadurch soll weder die letzte gerade getippte Zeile verloren gehen noch ein korrekt gespeicherter BTB wegen veralteter privater UI-Felder fälschlich `Datum/Zeit fehlt` / `Personal fehlt` melden.

### Details anzeigen = read-only
- Klick auf `Details anzeigen` öffnet in 200032 eine kompakte schreibgeschützte `BTB-Details`-Ansicht.
- angezeigt werden Datum/Zeit, Personal, Geräte/Maschinen und Wetter.
- keine dieser Angaben kann dort geändert werden.
- Bearbeitung dieser Kopfdaten erfolgt ausschließlich über den orangefarbenen Stift `Aktuellen BTB bearbeiten`.

### Arbeitszeit sofort sichtbar
- der 200032-Zeitdialog speichert weiterhin denselben BTB/dieselbe `btbId`.
- nach dem Speichern wird die neue Zeit sofort im Kopf angezeigt.
- anschließend wird das Fragment bewusst in **zwei synchronen Transaktionen** detach/attach neu aufgebaut, damit private Altwerte aus dem historischen Fragment den gespeicherten Wert nicht wieder überschreiben.

### Foto-Maske / Firma / btbId
- `FotoRepository.einfuegen(...)` liest die aktive `btbId` nun ebenfalls über `BtbActiveContext` und weist das neue Foto exakt diesem BTB zu.
- die Foto-Eingabemaske bekommt zusätzlich einen 200032-Lese-Override: Firmenliste kommt aus `BtbActiveContext.entry.firmen` der aktiven `btbId`, nicht aus einem zeitlich passenden Tag-/Nacht-Kandidaten.
- bei genau einer Firma im aktiven BTB wird sie in der Foto-Maske automatisch vorausgewählt.
- dadurch sollen Foto-Zuordnung und angezeigte Firma erstmals dieselbe btbId-Quelle verwenden.

### Tests 200032
- GitHub-Stand, Projektregeln, aktueller Projektstand, Draft-PR #5 und letzter Commit vor Änderung geprüft.
- neue Codepfade statisch auf offensichtliche Typ-/API-Probleme geprüft.
- **noch kein lokaler Gradle-Build/ADB-Gerätetest für 200032**; nicht als kompiliert bestätigen.
- NAS/DB nicht verändert.

## BTB – verbindliches Kernmodell
### Identität
- ein fachlicher BTB = eine dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie technische Identität.
- mehrere BTBs desselben Teams am selben Tag sind erlaubt.
- BTB 1 ↔ BTB 2 wird über konkrete `btbId` gewechselt; aktueller BTB wird vorher tatsächlich gespeichert.
- mehrere Baustellen dürfen lokal vorhanden sein; genau eine ist jeweils aktuell. Daten verschiedener Baustellen dürfen sich nie vermischen.
- Kamera/Foto, BTB-Kopf, Abschluss und Verlauf sollen denselben kanonischen aktiven btbId-Kontext verwenden.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- Originaldatei intern `btb_<btbId>_original.json`; spätere Abschlüsse überschreiben sie nicht.
- fachliche Rotmarkierung bleibt dauerhaft gegen dieses erste Original.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter bleiben bei Änderungen in der PDF-Darstellung schwarz.
- zurück auf Originalwert = wieder schwarz.
- tatsächliche Änderung nach Abschluss invalidiert/löscht das bisherige PDF; Status `Nachbearbeitung`.
- späterer Abschluss ersetzt nur das aktuelle PDF.
- technischer Abschluss-Metadatensatz hält Hash/Dateiname/Gültigkeit des aktuellen PDFs; keine sichtbare Versionierung.

### Fotos
- moderne Fotos erhalten eine eindeutige `btbId`.
- explizite Foto-btbId ist stärker als das Arbeitszeitfenster.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses bleibt unveränderlich.
- neues/entferntes/abgewähltes Foto gegenüber diesem Original invalidiert das Abschluss-PDF.
- neue Fotos werden im PDF-Anhang rot markiert; Original-JPG bleibt unverändert.
- Bildreferenzen im Haupttext sollen ebenfalls zwischen Original- und nachträglich hinzugefügten Bildern unterscheiden.
- Foto-Firma/BTB-Kontext muss aus derselben aktiven `btbId` gelesen werden; keine unabhängige Zeitfensterwahl für moderne BTBs.

## Nutzerprofil / Name
Praktisch bestätigt:
- Benutzername wird angezeigt.
- persönliche Telefonnummer und persönliche E-Mail können auf Schema 10 lokal gespeichert werden und bleiben nach Neustart erhalten.
- Firmenprofil bleibt getrennt und serverseitig zentral.

Schema 11 vorbereitet, noch nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`

## Teams / Baustellen / Sync
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs baustellenweit gemäß Regeln.
- Baustellenkarte soll Teamumfang und letzten Sync anzeigen, z. B. `2 TB · 4 Helfer`.
- Sync-Einstellungen: Manuell / stündlich / täglich; nur WLAN / nur Mobilfunk / WLAN + Mobilfunk.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- entfernte/ausgetretene Mitglieder behalten historische lokale Daten nur lesbar; serverseitige Membership-Removal-API bleibt offen.

## Bekannte offene Punkte
- Android 200032 lokal bauen/installieren/testen.
- große historische `BautagebuchPdf.kt` färbt einzelne Strukturüberschriften noch zu breit rot; Überschriften sollen schwarz bleiben.
- Bildnummern/-bereiche im Haupttext müssen noch differenziert rot werden, wenn die referenzierten Fotos nach dem Originalabschluss hinzukamen.
- Einzel-BTB-DOCX besitzt noch nicht dieselbe Rotformatierung wie das PDF; der Verlauf verwendet ab 200031 allerdings den bestehenden Tagesabschluss-ZIP statt des Einzel-BTB-ZIP.
- der große PDF-Renderer hat intern weiterhin einen alten Zeitfenster-Vorfilter; moderne btbId-Fotos werden in Zähler/BTB-Zuordnung bereits unabhängig von der Zeit behandelt, die vollständige Renderer-Bereinigung bleibt noch zu prüfen.
- Foto-Maske besitzt intern weiterhin historische Zeitfensterlogik; 200032 übersteuert die sichtbare Firmenliste mit dem kanonischen aktiven btbId-Kontext. Langfristig Altcode dort vollständig entfernen.
- BTB 1 ↔ BTB 2 und Baustelle A ↔ Baustelle B praktisch weiter testen.
- 7 bekannte Compilerwarnungen später in eigenem Aufräumpatch beseitigen.
- Baustellen-Gesamtexport noch nicht vollständig in allen Altpfaden auf btbId/Ein-PDF modernisiert.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync (`Immenstadt` / `5362-1`) bleibt offen.
- serverseitiges Entfernen/Verlassen mit lokal nur-lesbar bleibt offen.
- Zwei-Geräte-Tests für BTB, Bilder, Mängel, Abnahmen, 409-Konflikte, Tombstones, Netzabbruch und Benachrichtigungen bleiben offen.
- Schema 11 erst nach frischem Backup/Hashprüfung/read-only Verifikation produktiv migrieren.
- danach Vermessungs-/Kilometrierungsblock weiterführen.

## Nächster sicherer Test
1. lokal `git pull`.
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`.
3. nur bei erfolgreichem Build Android 200032 per ADB als Update installieren; App-Daten nicht löschen.
4. frischen BTB öffnen → `Details anzeigen`: nur schreibgeschützte Kurzansicht, keine Bearbeitung.
5. Stift → Datum/Zeit ändern → nach Speichern muss die neue Zeit **sofort** oben sichtbar sein und auch nach weiterer Texteingabe erhalten bleiben.
6. Kamera aus demselben BTB öffnen → Foto-Maske muss Firma/Firmen aus exakt diesem aktiven BTB anzeigen; bei genau einer Firma automatisch vorauswählen.
7. Foto speichern → Fotozähler muss sofort zur selben `btbId` passen, unabhängig davon, ob die Fotozeit außerhalb der alten Arbeitszeit liegt.
8. Text/Foto ergänzen → `BTB Abschluss`: kein falscher Dialog `Datum/Zeit fehlt` / `Personal fehlt`, sofern die Daten im aktiven btbId-Datensatz vorhanden sind; direkt vor dem Abschluss getippter Text darf nicht verloren gehen.
9. Abschluss-PDF und Fotoanhang prüfen; neue fachliche Inhalte/Fotos bleiben nach Originalregel markiert.
10. `ZIP teilen` und `BTB löschen` anschließend separat prüfen.
11. produktiven NAS weiterhin nicht anfassen.
