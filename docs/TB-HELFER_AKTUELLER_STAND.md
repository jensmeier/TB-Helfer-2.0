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
- aktueller Android-Entwicklungsstand: **versionCode 200027**, `versionName 1.3`.
- Android 200027 ist vorbereitet, aber **noch nicht lokal kompiliert/installiert/getestet**.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, `DB_TEST=OK`, Schema 10.
- in der gesamten Android-200025/200026/200027-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter produktiver Backup-Stand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen; keine redundanten Backups ohne Anlass.

Wichtig: Der Feature-Branch enthält neueren Multi-BTB-Servercode als der produktive Stage-Ordner. Schema 11 für persönliche Nutzerkontakte ist vorbereitet, aber **nicht produktiv migriert**.

## Praktischer Android-Teststand
### Android 200025
- lokal erfolgreich gebaut; 7 bekannte Kotlin/Java-Deprecation-/Redundanz-Warnungen, keine Buildfehler.
- Debug-APK mit `apksigner`: `Verifies`, v1=true, v2=true.
- direkte Installation per ADB erfolgreich.

### Android 200026 – praktisch bestätigt
Auf dem Testgerät am 13.09.2026 bestätigt:
- ohne realen Bericht steht `Noch kein BTB`; bloßes Öffnen erzeugt keinen fachlichen Entwurf.
- grünes `+` öffnet `Neuen BTB starten`; erst `Neuer BTB` erzeugt den Bericht.
- orange Bearbeiten-Stift ist vorhanden.
- `BTB Abschluss` funktioniert.
- App-interner PDF-Viewer funktioniert; Safe-Area oben/unten sowie `Vorherige – Seite – Nächste` sind nutzbar.
- erster erfolgreicher Abschluss bleibt unveränderliches Original.
- spätere Änderungen bleiben auch nach weiteren Abschlüssen rot, solange sie vom ersten Original abweichen.
- Einzel-BTB-ZIP ist btbId-basiert.
- persönliches Nutzerprofil lokal funktioniert: Name-Fallback sichtbar; persönliche Telefonnummer und E-Mail bleiben nach Speichern/Neustart erhalten.

Beim 200026-Gerätetest zusätzlich gefunden:
1. ein gelöschter Original-Text wird fachlich gelöscht, aber die erwartete deutlich rote `(entfernt) ...`-Darstellung war nicht zuverlässig sichtbar.
2. bei zwei BTBs mit je einem Foto zeigte der alte Fotozähler im BTB trotzdem `2/2`; Ursache: alter UI-Pfad verwendete weiterhin nur das überlappende Zeitfenster. Das Abschluss-PDF selbst war bereits auf die btbId gefiltert.
3. ein Foto, das erst nach dem ersten Abschluss zu einem BTB kommt, muss als Änderung zum unveränderlichen Original im späteren PDF rot markiert werden.
4. der bestehende PDF-Renderer färbt bei Abschnittsänderungen teilweise zusätzlich Abschnitts-/Gruppenüberschriften rot. Gewünschtes Endverhalten: nur echte Inhaltsabweichungen rot; dieser optische Restpunkt bleibt noch offen.

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

## Android 200027 – vorbereiteter Patch
Ziel: die drei im 200026-Test gefundenen funktionalen Punkte korrigieren, ohne den großen historischen `BautagebuchFragment.kt` bzw. den ca. 130-KB-PDF-Renderer riskant komplett zu ersetzen.

### 1. Foto-Isolation BTB 1 / BTB 2
Betroffene Dateien:
- `BtbFotoZuordnungSpeicher.kt`
- `BtbPhotoSelectionStore.kt`
- `FotoRepository.kt`

Änderung:
- `BtbFotoZuordnungSpeicher` kann IDs von Fotos liefern, die explizit einem anderen `btbId` gehören.
- `BtbPhotoSelectionStore.loadExcluded()` ergänzt solche Fremd-BTB-Fotos automatisch als Ausschluss für den aktuellen BTB.
- dadurch verwenden auch historische UI-Pfade für Fotozähler/Auswahl die btbId-Isolation, ohne dass der Groß-Fragmentcode dupliziert wird.
- automatische Fremd-BTB-Ausschlüsse werden nicht als manuelle Benutzer-Abwahl dauerhaft gespeichert.
- Erwartung für Test: zwei BTBs mit je einem modernen Foto zeigen jeweils `1/1`, nicht `2/2`.

### 2. Foto-Bestand gehört zum unveränderlichen Original
Neue Dateien:
- `BtbOriginalFotoSpeicher.kt`
- `BtbFotoRenderMarkierung.kt`

Verdrahtung:
- `BtbModernLifecycleInstaller.kt`
- `FotoRepository.kt`

Verhalten:
- beim allerersten erfolgreichen Abschluss wird zusätzlich die tatsächlich zum BTB gehörende Foto-ID-Menge einmalig als Foto-Original gespeichert.
- diese Foto-Basis wird bei späteren Abschlüssen niemals überschrieben.
- für bereits unter 200026 abgeschlossene BTBs wird eine fehlende Foto-Basis einmalig aus dem Zeitpunkt der vorhandenen unveränderlichen BTB-Originaldatei rekonstruiert; moderne Fotos derselben btbId bis zu diesem Zeitpunkt gelten als damaliges Original.
- ein später hinzugekommenes Foto wird beim neuen PDF-Abschluss über eine **temporäre PDF-Kopie** sichtbar rot markiert: roter Rahmen + roter Banner `ÄNDERUNG ZUM ORIGINAL`.
- Original-JPG, Room-Datensatz und Serverbild werden dabei nicht verändert.
- neue oder gelöschte Fotos eines bereits abgeschlossenen modernen BTB invalidieren das bisherige Abschlussdokument.

### 3. Gelöschte Originaltexte sichtbar machen
Neue Datei:
- `BtbRenderOriginalDiff.kt`

Verdrahtung:
- `BtbModernLifecycleInstaller.kt`

Verhalten:
- nur für den PDF-Lauf wird eine temporäre Darstellungsfassung des aktuellen BTB gebaut.
- echte Löschungen werden aus dem unveränderlichen Original als `(entfernt) ...` ergänzt.
- diese Zusatzzeilen werden **nicht** in den aktuellen BTB zurückgespeichert; fachlich bleibt der Inhalt gelöscht.
- normale Textänderungen werden möglichst als Änderung statt zusätzlich als Löschung erkannt.
- die vorhandene Diff-Logik soll den temporären `(entfernt)`-Eintrag rot darstellen.

### Bekannter Renderer-Restpunkt
Der bestehende `BautagebuchPdf.kt` färbt Abschnitts-/Orts-/teilweise Gruppenüberschriften noch rot, sobald darunter eine Änderung liegt. Das ist optisch nicht das gewünschte Endverhalten. Wegen der Größe und Historie dieser Datei wurde sie in 200027 nicht blind komplett ersetzt. Nach Bestätigung der funktionalen 200027-Korrekturen folgt dafür ein kleiner eigener Renderer-Aufräumpatch.

## Nutzerprofil / Name
Praktisch mit 200026 bestätigt:
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
- Android 200027 lokal bauen/installieren/testen.
- nach funktionalem 200027-Test: Renderer-Überschriften nur schwarz/Strukturfarbe, echte Inhaltsabweichungen rot.
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
3. **Nur wenn der Build erfolgreich ist:** Android 200027 per ADB als Update installieren, App-Daten nicht löschen.
4. BTB 1 und BTB 2 mit je genau einem modernen Foto öffnen: jeder Fotozähler soll nur sein eigenes Bild zeigen (`1/1`).
5. bei einem bereits abgeschlossenen BTB ein neues Foto aufnehmen, danach erneut `BTB Abschluss`: neues Foto muss im PDF sichtbar rot als Änderung zum Original markiert sein.
6. Original-Textzeile löschen, erneut abschließen: PDF muss eine rote `(entfernt) ...`-Darstellung enthalten; gelöschter Text darf nicht in den gespeicherten aktuellen BTB zurückkehren.
7. Nutzerprofil muss unverändert erhalten bleiben.
8. Fehler/Screenshots zuerst lokal auswerten; produktiven NAS weiterhin nicht anfassen.