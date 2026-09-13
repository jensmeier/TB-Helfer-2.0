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
- aktueller Android-Entwicklungsstand: **versionCode 200030**, `versionName 1.3`.
- Android 200030 ist vorbereitet, aber noch **nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, `DB_TEST=OK`, Schema 10.
- in der gesamten Android-200025 bis 200030-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter produktiver Backup-Stand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen; keine redundanten Backups ohne Anlass.

Wichtig: Der Feature-Branch enthält neueren Multi-BTB-Servercode als der produktive Stage-Ordner. Schema 11 für persönliche Nutzerkontakte ist vorbereitet, aber **nicht produktiv migriert**.

## Praktischer Android-Teststand
### Android 200025
- lokal erfolgreich gebaut; 7 bekannte Kotlin/Java-Deprecation-/Redundanz-Warnungen, keine Buildfehler.
- Debug-APK mit `apksigner`: `Verifies`, v1=true, v2=true.
- direkte Installation per ADB erfolgreich.

### Android 200026 – praktisch bestätigt
- ohne realen Bericht steht `Noch kein BTB`; bloßes Öffnen erzeugt keinen fachlichen Entwurf.
- grünes `+` erzeugt erst nach `Neuer BTB` einen Bericht.
- orange Bearbeiten-Stift vorhanden.
- `BTB Abschluss` funktioniert.
- App-interner PDF-Viewer inkl. Safe-Areas funktioniert.
- erster erfolgreicher Abschluss bleibt unveränderliches Original.
- spätere Änderungen bleiben gegen dieses erste Original rot.
- persönliches Nutzerprofil lokal funktioniert: Name, persönliche Telefonnummer und E-Mail bleiben erhalten.

### Android 200027 – praktisch bestätigt
- gelöschter Originaltext erscheint sichtbar rot als `(entfernt) ...`.
- Foto-Isolation war zunächst nur teilweise korrekt (`3/4` bzw. `1/4` statt `3/3` / `1/1`).
- PDF-Strukturüberschriften werden teilweise zu breit rot gefärbt.

### Android 200028 – Gerätetest/Screenshots
Umgesetzt:
- Fotozähler und Fotoauswahl sind auf die stabile `btbId` ausgerichtet.
- moderne Fotos anderer BTBs sollen aus Zähler/Nenner/Auswahl verschwinden; Legacy-Fotos ohne Zuordnung bleiben Zeitfenster-Fallback.
- Screenshot eines BTB zeigt einen konsistenten Zähler `4/4`.
- ein nach dem ersten Abschluss hinzugefügtes Foto wird im Fotoanhang sichtbar mit rotem Rahmen/Banner `ÄNDERUNG ZUM ORIGINAL` markiert; diese Funktion ist praktisch bestätigt.
- rote `(entfernt) ...`-Darstellung funktioniert weiterhin.

Noch offen aus dem 200028-Test:
1. Cross-BTB-Test mit getrennten Mengen (`3/3` und `1/1`) noch einmal gezielt bestätigen.
2. Bildverweise im Haupttext müssen dieselbe Original-Diff-Regel verwenden: wenn z. B. Bild 3 und 4 erst nach dem ersten Abschluss entstanden sind, muss auch `[Bild 3–4]` unter `4. Ausgeführte Arbeiten` rot sein. Bei gemischten Referenzen bleibt der Originalteil schwarz und nur der neue Bildanteil rot.
3. Strukturüberschriften wie `Wetter`, `4. Ausgeführte Arbeiten`, `Allgemein`, `Hauptbaustelle`/Ortsüberschriften bleiben schwarz; nur echte Inhaltsabweichungen rot.

### Android 200029 – praktisch per Screenshot weiter bestätigt
- kompakter Kopf funktioniert; `Details anzeigen` liegt direkt unter der Quickbar.
- orange Stift öffnet das Fenster `Aktuellen BTB bearbeiten` korrekt.
- es war **derselbe BTB**, kein BTB-Wechsel und kein Datenverlust.
- nach zwei weiteren Fotos zeigte derselbe BTB korrekt `3/3`; die stabile Fotozuordnung zur `btbId` bleibt damit im gezeigten Fall erhalten.
- gefundenes UI-Problem: nach `Bearbeiten` im Stift-Fenster wurde die alte Vierer-Auswahl `Wetter / Datum Zeit / Personal / Geräte Maschinen` wieder als Zwischenschritt eingeblendet.
- gefundenes Abschlussproblem: derselbe bereits abgeschlossene BTB blieb trotz nachträglich hinzugefügter Fotos optisch `Abgeschlossen`. Laut verbindlicher Foto-Originalregel muss eine geänderte Foto-Menge den bestehenden Abschluss genauso invalidieren wie eine Textänderung.

## Android 200030 – aktueller vorbereiteter Patch
Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbModernLifecycleInstaller.kt`
- `app/build.gradle.kts`
- `docs/TB-HELFER_PROJEKTREGELN.md`
- `docs/TB-HELFER_AKTUELLER_STAND.md`

Änderungen:
- Stift-Fenster bleibt der zentrale Einstieg `Aktuellen BTB bearbeiten`.
- bei `Personal`, `Geräte/Maschinen` und `Wetter` wird nach `Bearbeiten` der vorhandene Editor direkt geöffnet; die alte Vierer-Auswahl wird sofort wieder ausgeblendet und nicht mehr als sichtbarer Zwischenschritt stehen gelassen.
- `Datum/Zeit` öffnet seinen vorhandenen Datum-/Schicht-Popup direkt über einen unsichtbar gelayouteten Anchor, ohne die alte Vierer-Auswahl sichtbar einzublenden.
- wenn **noch kein BTB** existiert, werden zusätzlich der orange Bearbeiten-Stift, sein Abstand sowie `PDF Entwurf` und `BTB Abschluss` ausgeblendet.
- wenn wieder ein realer BTB vorhanden ist, werden diese Aktionen wieder eingeblendet.
- Foto-Originalmenge wird im Lifecycle-Watcher regelmäßig mit der aktuell ausgewählten Foto-Menge desselben `btbId` verglichen.
- neue, entfernte oder nachträglich abgewählte Fotos gegenüber dem ersten Original machen das aktuelle Abschluss-PDF ungültig; das PDF wird entfernt und der vorhandene Status-Finder liefert anschließend `Nachbearbeitung`.
- Status wird bewusst erst **nach** der Foto-Prüfung aktualisiert, damit ein neues Foto am bereits abgeschlossenen BTB nicht weiter als `Abgeschlossen` angezeigt wird.
- Fotozähler bleibt weiterhin über `BtbPhotoCounterFix` auf der konkreten `btbId`.

Tests:
- GitHub-Stand und betroffene Laufzeitpfade statisch geprüft.
- **noch kein lokaler Gradle-Build/ADB-Gerätetest für 200030**; nicht als kompiliert bestätigen.
- NAS/DB nicht verändert.

## BTB – verbindliches Modell
### Identität
- ein fachlicher BTB = eine dauerhafte stabile `btbId`.
- Datum, Uhrzeit und Tag/Nacht sind Attribute/Anzeige, nie technische Identität.
- mehrere BTBs desselben Teams am selben Tag sind erlaubt.
- BTB 1 ↔ BTB 2 wird über konkrete `btbId` gewechselt; aktueller BTB wird vorher tatsächlich gespeichert.
- mehrere Baustellen dürfen lokal vorhanden sein; genau eine ist jeweils aktuell. Daten/BTBs verschiedener Baustellen dürfen sich nie vermischen.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- Originaldatei intern: `btb_<btbId>_original.json`; spätere Abschlüsse überschreiben sie nicht.
- Rotvergleich bleibt dauerhaft gegen dieses erste Original.
- zurück auf Originalwert = wieder schwarz.
- tatsächliche Änderung nach Abschluss invalidiert/löscht das bisherige PDF; Status `Nachbearbeitung`.
- späterer Abschluss ersetzt nur das aktuelle PDF.
- technischer `btb_<btbId>_abschluss_meta.json`-Datensatz hält Hash/Dateiname/Gültigkeit des aktuellen PDFs; keine sichtbare Versionierung.

### Fotos als Teil des Originalstands
- neue normale BTB-Fotos erhalten eine eindeutige `btbId`.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses bleibt unveränderlich.
- ein später neu hinzugefügtes Foto gilt als Abweichung vom Original und invalidiert das aktuelle Abschluss-PDF.
- auch Entfernen/Abwählen eines Originalfotos gilt als Foto-Abweichung und invalidiert den Abschluss.
- PDF-Aufbereitung erzeugt für neue Fotos nur eine temporäre rot markierte Bildkopie; Original-JPG/Room-Datensatz bleiben unverändert.
- Bildreferenzen im Haupttext müssen künftig ebenfalls zwischen Original- und nachträglich hinzugefügten Bildern unterscheiden.

## Nutzerprofil / Name
Praktisch bestätigt:
- Benutzername `jens poco` wird angezeigt.
- persönliche Telefonnummer und persönliche E-Mail können auf Schema 10 lokal gespeichert werden und bleiben nach Neustart erhalten.
- Firmenprofil bleibt getrennt und serverseitig zentral.

Schema 11 vorbereitet, noch nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql` → erwartet `SCHEMA_11_OK`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`

## Teams / Baustellen / Sync
Im Branch vorhanden/beschlossen:
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs baustellenweit gemäß Regeln.
- Baustellenkarte soll Teamumfang und letzten Sync anzeigen, z. B. `2 TB · 4 Helfer`.
- Sync-Einstellungen: Manuell / stündlich / täglich; nur WLAN / nur Mobilfunk / WLAN + Mobilfunk.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- entfernte/ausgetretene Mitglieder behalten historische lokale Daten nur lesbar; serverseitige Membership-Removal-API bleibt offen.

Produktiver Server unterstützt den neueren Branch-Multi-BTB-Code noch nicht vollständig; deshalb noch kein verbindlicher Pixel↔Poco-Ende-zu-Ende-Test des neuen Multi-BTB-Flows.

## Weitere offene Hauptblöcke
- Android 200030 lokal bauen/installieren/testen.
- PDF-Strukturüberschriften schwarz halten; nur echte Inhaltsabweichungen rot.
- Bildnummern/-bereiche im Haupttext differenziert rot markieren, wenn die referenzierten Fotos nach dem Originalabschluss hinzugekommen sind.
- Einzel-BTB-DOCX muss dieselbe Original-/Rotlogik wie das PDF erhalten; der aktuell einfache DOCX-Export schreibt noch ausschließlich schwarzen Text.
- BTB 1 ↔ BTB 2 und Baustelle A ↔ Baustelle B praktisch weiter testen.
- 7 bekannte Compilerwarnungen später in eigenem Aufräumpatch beseitigen.
- großer `BautagebuchFragment.kt` enthält historische Tag/Nacht-/Altpfade; moderne Installer übersteuern sie derzeit zur Laufzeit.
- großer Baustellen-Gesamtexport noch nicht vollständig auf btbId/Ein-PDF umgestellt.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync (`Immenstadt` / `5362-1`) bleibt offen.
- serverseitiges Entfernen/Verlassen mit lokal nur-lesbar bleibt offen.
- Zwei-Geräte-Tests für BTB, Bilder, Mängel, Abnahmen, 409-Konflikte, Tombstones, Netzabbruch und Benachrichtigungen bleiben offen.
- Schema 11 erst nach frischem Backup/Hashprüfung/read-only Verifikation produktiv migrieren.
- danach Vermessungs-/Kilometrierungsblock weiterführen.

## Nächster sicherer Test
1. lokal `git pull`.
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`.
3. **Nur wenn der Build erfolgreich ist:** Android 200030 per ADB als Update installieren, App-Daten nicht löschen.
4. ohne BTB prüfen: `Noch kein BTB`; orange Stift sowie `PDF Entwurf`/`BTB Abschluss` sollen nicht sichtbar sein.
5. bestehenden BTB öffnen und orange Stift drücken.
6. bei `Personal`, `Geräte/Maschinen` und `Wetter` jeweils `Bearbeiten` drücken: der gewählte Editor soll direkt erscheinen; die alte Vierer-Auswahl darf nicht als Zwischenschritt stehen bleiben.
7. `Datum/Zeit → Bearbeiten`: vorhandener Datum-/Zeit-Popup soll direkt erscheinen.
8. bei einem bereits abgeschlossenen BTB ein neues Foto aufnehmen: Fotozähler z. B. `1/1 → 2/2` bzw. `3/3`, Status muss danach automatisch `Nachbearbeitung · Abschluss-PDF ungültig` werden.
9. BTB erneut abschließen und PDF prüfen: neue Fotos bleiben gegenüber dem ersten Original rot markiert.
10. produktiven NAS weiterhin nicht anfassen.
