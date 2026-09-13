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
- aktueller Android-Entwicklungsstand: **versionCode 200029**, `versionName 1.3`.
- Android 200029 ist vorbereitet, aber noch **nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, `DB_TEST=OK`, Schema 10.
- in der gesamten Android-200025 bis 200029-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
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

## Android 200029 – aktueller vorbereiteter UI-Patch
Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbModernLifecycleInstaller.kt`
- `app/build.gradle.kts`
- `docs/TB-HELFER_PROJEKTREGELN.md`

Ziel laut Benutzer-Skizze: mehr nutzbare Höhe auf dem Handy und direkter Bearbeitungsdialog.

Änderungen:
- wenn **noch kein BTB** existiert, wird `topFieldsToggleRow` jetzt ausdrücklich `GONE` gesetzt; damit reserviert der unsichtbare `Details anzeigen`-Bereich keine Höhe mehr.
- Wetter-/Personal-Kopfbereiche bleiben ohne BTB ebenfalls `GONE`.
- Kopfabstände werden kompakter gesetzt: Quickbar unten kleiner, Details-Abstand kleiner, Punkt `4. Ausgeführte Arbeiten` rückt weiter nach oben.
- sobald ein echter BTB vorhanden ist, wird `Details anzeigen` wieder eingeblendet.
- orange Bearbeiten-Stift öffnet jetzt direkt ein kompaktes schwebendes Fenster **`Aktuellen BTB bearbeiten`** statt eines vorgeschalteten Listenmenüs.
- das Fenster zeigt Datum/Zeit, Personal, Geräte/Maschinen und Wetter mit jeweils direkter Aktion `Bearbeiten`.
- die Bearbeitung bleibt immer beim selben BTB/derselben `btbId`.

Tests:
- statisch gegen den aktuellen Branch geprüft.
- **noch kein lokaler Gradle-Build** für 200029; nicht als kompiliert bestätigen.

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
- ein später neu hinzugefügtes Foto gilt als Abweichung vom Original.
- PDF-Aufbereitung erzeugt dafür nur eine temporäre rot markierte Bildkopie; Original-JPG/Room-Datensatz bleiben unverändert.
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
- Android 200029 lokal bauen/installieren/testen.
- PDF-Strukturüberschriften schwarz halten; nur echte Inhaltsabweichungen rot.
- Bildnummern/-bereiche im Haupttext differenziert rot markieren, wenn die referenzierten Fotos nach dem Originalabschluss hinzugekommen sind.
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
3. **Nur wenn der Build erfolgreich ist:** Android 200029 per ADB als Update installieren, App-Daten nicht löschen.
4. ohne BTB prüfen: `Noch kein BTB` und Punkt 4 sollen deutlich dichter zusammenrücken; kein unsichtbarer Details-Leerraum.
5. neuen BTB über `+` anlegen.
6. orange Stift drücken: direktes Fenster `Aktuellen BTB bearbeiten` mit Datum/Zeit, Personal, Geräte/Maschinen, Wetter prüfen.
7. bestehenden BTB-Inhalt/Abschluss/PDF darf der UI-Patch nicht verändern.
8. Fotozähler und rote Foto-Markierung dürfen nicht regressieren.
9. produktiven NAS weiterhin nicht anfassen.
