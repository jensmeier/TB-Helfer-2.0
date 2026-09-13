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
- aktueller Android-Entwicklungsstand: **versionCode 200028**, `versionName 1.3`.
- Android 200028 ist vorbereitet, aber noch **nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, `DB_TEST=OK`, Schema 10.
- in der gesamten Android-200025 bis 200028-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter produktiver Backup-Stand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen; keine redundanten Backups ohne Anlass.

Wichtig: Der Feature-Branch enthält neueren Multi-BTB-Servercode als der produktive Stage-Ordner. Schema 11 für persönliche Nutzerkontakte ist vorbereitet, aber **nicht produktiv migriert**.

## Praktischer Android-Teststand
### Android 200025
- lokal erfolgreich gebaut; 7 bekannte Kotlin/Java-Deprecation-/Redundanz-Warnungen, keine Buildfehler.
- Debug-APK mit `apksigner`: `Verifies`, v1=true, v2=true.
- direkte Installation per ADB erfolgreich.

### Android 200026 – praktisch bestätigt
Auf dem Testgerät bestätigt:
- ohne realen Bericht steht `Noch kein BTB`; bloßes Öffnen erzeugt keinen fachlichen Entwurf.
- grünes `+` öffnet `Neuen BTB starten`; erst `Neuer BTB` erzeugt den Bericht.
- orange Bearbeiten-Stift ist vorhanden.
- `BTB Abschluss` funktioniert.
- App-interner PDF-Viewer funktioniert; Safe-Area oben/unten sowie `Vorherige – Seite – Nächste` sind nutzbar.
- erster erfolgreicher Abschluss bleibt unveränderliches Original.
- spätere Änderungen bleiben auch nach weiteren Abschlüssen rot, solange sie vom ersten Original abweichen.
- Einzel-BTB-ZIP ist btbId-basiert.
- persönliches Nutzerprofil lokal funktioniert: Name-Fallback sichtbar; persönliche Telefonnummer und E-Mail bleiben nach Speichern/Neustart erhalten.

### Android 200027 – Gerätetest 13.09.2026
Die vom Benutzer gelieferten Screenshots bestätigen folgende Punkte:
- gelöschter Originaltext wird jetzt im Abschluss-PDF sichtbar als **rote `(entfernt) ...`-Änderung** dargestellt.
- persönliches Nutzerprofil bleibt weiterhin in Ordnung.
- Foto-Isolation greift **teilweise korrekt**: bei zwei BTBs derselben Baustelle zeigt der Zähler `3/4` bzw. `1/4`. Der Zähler links vom Schrägstrich folgt damit bereits der jeweiligen BTB-Auswahl; der Nenner `4` stammt aber noch aus allen Fotos im überlappenden Zeitfenster.
- daraus folgt als Soll für genau diese Testdaten: **`3/3` und `1/1`**, nicht `3/4` und `1/4`.
- die Fotoauswahl selbst muss dieselbe stabile `btbId`-Menge verwenden; Fotos eines anderen modernen BTB dürfen dort nicht einmal als abgewählte Fremdfotos erscheinen.
- PDF-Diff ist optisch weiterhin zu breit: Strukturüberschriften wie `Wetter`, `4. Ausgeführte Arbeiten`, `Allgemein` und Orts-/Gruppenüberschriften können rot werden, sobald darunter eine Änderung liegt. Gewünscht bleiben ausschließlich tatsächliche geänderte Inhaltswerte/-zeilen rot.
- die rote Markierung eines **nach dem ersten Abschluss neu hinzugefügten Fotos** ist durch die gezeigten Seiten noch nicht abschließend bestätigt; dieser Test bleibt offen.

## Android 200028 – vorbereiteter Patch
Ziel dieser Runde ist ausschließlich die noch falsche Foto-Menge in Zähler und Fotoauswahl zu korrigieren, ohne den großen historischen `BautagebuchFragment.kt` zu duplizieren oder den ca. 130-KB-PDF-Renderer riskant als Ganzes zu ersetzen.

### 1. BTB-spezifischer Fotozähler
Neue Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoCounterFix.kt`

Verdrahtung:
- `BtbModernLifecycleInstaller.kt`

Verhalten:
- der moderne Lifecycle übersteuert den historischen Fotozähler regelmäßig mit derselben fachlichen Menge, die auch PDF/BTB verwenden sollen.
- explizit einem anderen `btbId` zugeordnete moderne Fotos werden aus Zähler **und Nenner** entfernt.
- historische unzugeordnete Fotos bleiben als Zeitfenster-Legacy-Fallback erlaubt.
- manuell abgewählte Fotos bleiben im Nenner, aber nicht im Zähler.
- für den aktuellen Screenshot-Test ist die Erwartung daher `3/3` für den einen BTB und `1/1` für den anderen.

### 2. Fotoauswahl exakt auf aktuellen BTB filtern
Geändert:
- `BtbPdfAuswahlFragment.kt`

Verhalten:
- die Auswahl löst die aktive stabile `btbId` über `BtbPhotoInstanzKey` auf.
- ein modernes Foto mit anderer `btbId` wird nicht mehr angezeigt und nicht mitgezählt.
- Persistenz bearbeitet nur die tatsächlich zu diesem BTB gehörenden IDs.
- Legacy-Fotos ohne Zuordnung bleiben weiterhin auswählbar, solange sie im Zeitfenster liegen.

### 3. PDF-Strukturfarben – noch offen, bewusst nicht riskant überarbeitet
`BautagebuchPdf.kt` enthält die strukturelle Rotfärbung direkt in der großen historischen Rendererdatei. Der Fehler ist lokalisiert:
- tatsächliche Inhaltszeilen verwenden eigene rote Paints und sollen rot bleiben.
- Struktur-Paints für Abschnitts-, Firmen-/Allgemein- und Ortsüberschriften werden derzeit ebenfalls rot geschaltet.

Soll:
- `Wetter`, `4. Ausgeführte Arbeiten`, `Allgemein`, `Hauptbaustelle` usw. bleiben schwarz.
- nur geänderte Wetterwerte, geänderte Textanteile, neue Zeilen, `(entfernt) ...` und neue Fotos sind rot.

Dieser reine Renderer-Farbpatch ist **noch nicht in 200028 enthalten**. Über den aktuellen Connector soll die sehr große Datei nicht für vier Farbstellen blind komplett ersetzt werden. Erst nach lokal sicherem Zugriff/Build oder einer sicheren gezielten Bearbeitungsmöglichkeit ändern.

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
Vorhanden seit 200027:
- `BtbOriginalFotoSpeicher.kt`
- `BtbFotoRenderMarkierung.kt`
- `BtbRenderOriginalDiff.kt`

Regeln:
- Foto-ID-Menge des ersten erfolgreichen Abschlusses bleibt unveränderlich.
- für ältere 200026-BTBs darf sie einmalig aus dem Zeitpunkt des vorhandenen ersten Originalabschlusses rekonstruiert werden.
- ein später neu hinzugefügtes Foto gilt als Abweichung vom Original.
- PDF-Aufbereitung erzeugt dafür nur eine temporäre markierte Bildkopie; Original-JPG/Room-Datensatz bleiben unverändert.
- neue/gelöschte Fotos eines bereits abgeschlossenen modernen BTB invalidieren das alte Abschlussdokument.

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
Im Branch vorhanden:
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs baustellenweit gemäß Regeln.
- Baustellenkarten können Teamumfang und letzten Sync anzeigen, z. B. `2 TB · 4 Helfer`.
- Sync-Einstellungen: Manuell / stündlich / täglich; nur WLAN / nur Mobilfunk / WLAN + Mobilfunk.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- entfernte/ausgetretene Mitglieder sollen historische lokale Daten nur lesbar behalten; serverseitige Membership-Removal-API bleibt offen.

Produktiver Server unterstützt den neueren Branch-Multi-BTB-Code noch nicht vollständig; deshalb noch kein verbindlicher Pixel↔Poco-Ende-zu-Ende-Test des neuen Multi-BTB-Flows.

## Weitere offene Hauptblöcke
- Android 200028 lokal bauen/installieren/testen.
- PDF-Strukturüberschriften schwarz halten; nur echte Inhaltsabweichungen rot.
- roten Rahmen/Banner eines nach Originalabschluss neu aufgenommenen Fotos praktisch bestätigen.
- 7 bekannte Compilerwarnungen später in eigenem Aufräumpatch beseitigen.
- großer `BautagebuchFragment.kt` enthält noch historische Tag/Nacht-/Altpfade; moderne Installer übersteuern sie derzeit zur Laufzeit.
- großer Baustellen-Gesamtexport noch nicht vollständig auf btbId/Ein-PDF umgestellt.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync (`Immenstadt` / `5362-1`) bleibt offen.
- serverseitiges Entfernen/Verlassen mit lokal nur-lesbar bleibt offen.
- Zwei-Geräte-Tests für BTB, Bilder, Mängel, Abnahmen, 409-Konflikte, Tombstones, Netzabbruch und Benachrichtigungen bleiben offen.
- Schema 11 erst nach frischem Backup/Hashprüfung/read-only Verifikation produktiv migrieren.
- danach Vermessungs-/Kilometrierungsblock weiterführen.

## Nächster sicherer Test
1. lokal `git pull`.
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`.
3. **Nur wenn der Build erfolgreich ist:** Android 200028 per ADB als Update installieren, App-Daten nicht löschen.
4. denselben BTB mit bisher `3/4` öffnen → erwartet **`3/3`**.
5. den anderen BTB mit bisher `1/4` öffnen → erwartet **`1/1`**.
6. jeweils auf den Foto-Chip tippen: in der Auswahl dürfen nur die Fotos dieses BTB plus echte unzugeordnete Legacy-Fotos auftauchen; moderne Fotos des anderen BTB dürfen fehlen.
7. bereits funktionierende rote `(entfernt) ...`-Darstellung darf nicht regressieren.
8. bei einem bereits abgeschlossenen BTB neues Foto aufnehmen und erneut abschließen: roter Änderungsrahmen/Banner im Foto-Anhang prüfen.
9. PDF-Strukturüberschriften bleiben bis zum separaten sicheren Renderer-Farbpatch als bekannter optischer Restpunkt dokumentiert.
10. produktiven NAS weiterhin nicht anfassen.
