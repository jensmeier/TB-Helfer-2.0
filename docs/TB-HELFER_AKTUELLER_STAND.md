# TB-Helfer – aktueller Projektstand

Stand: 2026-09-17

Maßgeblich sind der aktuelle GitHub-Code im aktiven Branch, `docs/TB-HELFER_PROJEKTREGELN.md`, diese Datei, `docs/TB-SERVER_1_0_ZIELMODELL.md` und `docs/TB-SERVER_1_0_CUTOVER.md`.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- Cutover-Regel: `64bb798e37f6a220fbd1715859a813a359a32eee`
- Dateien/Chunkupload: `959a53459455062def10608df4ca4c4cd1b0a179`
- Mängel/Abnahmen: `40249785879b82714b7d2df822e9e52fb7fb750c`
- Vermessungsoriginale/Track: `a84c3607512a61145eba85309aa80bc2d6f015f0`
- Idempotenz-Grundlage: `d46650a938ab370c6f3b65d97852b2168ea08287`
- Mängel/Abnahmen replay-sicher: `755efa80840fad5b2208f8b42e174b9df4eb6ae4`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Strategie

Serverbasis vollständig bauen und testen, danach kontrollierter NAS-Cutover mit vorher geprüftem Backup, anschließend Android systematisch auf den festen `/api/v1`-Vertrag umstellen.

Der derzeitige Server-Fachdatenbestand ist Testbestand. Beim 1.0-Cutover dürfen Testnutzer, Testbaustellen und Test-/Leerdaten für TRA/KF/GRA/KOR/KV/GVP sowie Strecken-/Bahnhofs-/Vermessungskataloge entfernt werden. GitHub-Code, Konfiguration/Secrets und Backups bleiben erhalten. Statische Grunddaten werden aus definierter Quelle neu aufgebaut.

**Ab dem ersten echten produktiven Vermessungsimport sind Originaldateien unveränderlich und dürfen durch spätere Updates/Resets nicht gelöscht oder überschrieben werden.** Bearbeitete/berechnete Daten liegen getrennt.

## TB-Server 1.0 – umgesetzt

### Foundation / Auth / Portal-Grundlage

Vorhanden sind:
- neues Paket `server/tbserver/` und separater `server/Dockerfile.v1`;
- frische Baseline `server/v1_migrations/001_server_1_0_base.sql`;
- Migration Runner mit nummerierten Migrationen, SHA-256-Prüfung und Advisory-Lock;
- Health/Ready/Capabilities;
- Firmen, Benutzer, Portal-Aktivierung, Login/Sessions, CSRF, Bootstrap/Admin-Grundlage.

### Baustellen / Rechte / Teams

Vorhanden sind Baustellen und Unterbaustellen mit UUIDs, Mitgliedschaften, Rechteprofile/Overrides, Teams mit `active_btb_id` und serverseitige Rechteprüfung.

Neu im aktuellen Block:
- Worksite-, Knoten- und Mitgliedschaftsmutationen verwenden den zentralen Receipt-/Replay-Vertrag;
- `WORKSITE_CREATE`, `WORKSITE_NODE_CREATE`, `WORKSITE_NODE_UPDATE`, `WORKSITE_NODE_DELETE`, `WORKSITE_MEMBER_ADD`, `WORKSITE_MEMBER_PERMISSIONS_UPDATE` sind bei gleicher `X-Client-Change-Id` wiederholbar;
- Knotenänderungen erzeugen Change-Feed- und Audit-Ereignisse;
- Mitgliedschafts-/Rechteänderungen erzeugen Change-Feed- und Audit-Ereignisse;
- gelöschte Worksite-Knoten erhalten Tombstones.

Capabilities: `worksite_tree_v1`, `worksite_permissions_v1`, `worksite_teams_v1` = `true`.

### Granularer BTB / Live-Sync

Vorhanden sind BTB-Kopf, einzelne versionierte `btb_entries`, Versionskonflikte, aktiver BTB je Team, erster unveränderlicher Original-Snapshot beim Abschluss, Rework, Tombstone-Löschen, Change Feed und SSE-Live-Signal.

Neu im aktuellen Block sind replay-sichere Mutationen für:
- BTB anlegen;
- BTB-Kopf ändern;
- Eintrag anlegen/ändern/löschen;
- BTB abschließen;
- BTB zur Nachbearbeitung öffnen;
- BTB löschen.

Capabilities: `btb_entries_v1=true`, `btb_live_sync_v1=true`, `btb_finalize_with_pdf_v1=false`.

### Dateien / Bilder / Chunkupload

`server/tbserver/files.py` enthält resumierbare sequenzielle Upload-Sessions mit 8 MiB Standard-/16 MiB Maximal-Chunk, Offset-/Größen-/SHA-256-Prüfung, fsync, atomarem Abschluss in UUID-basierte Storage-Pfade, `tb.files`, `tb.file_links`, geschütztem Abruf, Upload-Abbruch sowie Change/Audit.

Neu im aktuellen Block:
- Upload-Session-Erstellung replay-sicher;
- Dateiabschluss replay-sicher, sodass ein Netzabbruch nach erfolgreichem Abschluss keine zweite Datei erzeugt;
- Upload-Abbruch replay-sicher;
- Chunk-Schreiben bleibt absichtlich offset-basiert; der Server lehnt falsche/alte Offsets ab und der Client kann den Sessionstand abfragen.

Capabilities: `files_v1=true`, `chunk_upload_v1=true`, `company_logo_v1=false`.

### Mängel / Abnahmen

`server/tbserver/defects.py` und `acceptances.py` unterstützen Anlegen, Lesen, Bearbeiten und Schließen/Finalisieren mit optionaler Client-UUID, Knotenzuordnung, Rechten, `base_version`, `409 VERSION_CONFLICT`, Change Feed und Audit.

Replay-sicher sind:
- `DEFECT_CREATE`, `DEFECT_UPDATE`, `DEFECT_CLOSE`;
- `ACCEPTANCE_CREATE`, `ACCEPTANCE_UPDATE`, `ACCEPTANCE_FINALIZE`.

Capabilities: `defects_v1=true`, `acceptances_v1=true`.

### Unveränderliche Vermessungsoriginale / Track-Arbeitsdaten

`server/tbserver/survey_originals.py` und `track_data.py` trennen Original und Arbeitsstand:
- Import prüft Originaldatei gegen gespeicherte Größe + SHA-256;
- Bytes werden nur atomar nach `reference/survey-originals/<uuid>/...` verschoben;
- Originalmetadaten liegen in `tb.survey_originals`;
- **keine PATCH-/PUT-/DELETE-Endpunkte und kein SQL-Update/Delete für Originale**;
- identische Originalbytes derselben Firma/Datenart werden als Duplikat erkannt;
- `tb.track_data` enthält die veränderbaren/versionierten Arbeitsdaten mit optionalem `source_original_id`;
- Track-Daten nutzen Optimistic Locking und Tombstones.

Capabilities: `survey_originals_v1=true`, `track_data_v1=true`.

### Idempotenz / Offline-Replay – Kernvertrag vollständig

`server/tbserver/idempotency.py` nutzt `tb.mutation_receipts` und `X-Client-Change-Id`.

Technische Regel:
- Schlüssel = Benutzer + `client_change_id`;
- vor Mutation PostgreSQL-Transaktions-Advisory-Lock auf diesen Schlüssel;
- parallele Wiederholungen werden serialisiert;
- erfolgreiche Antwort wird JSON-sicher als Receipt gespeichert;
- erneuter identischer Request bekommt die gespeicherte Antwort;
- Wiederverwendung der Change-ID für einen anderen Mutationstyp ergibt `409 CLIENT_CHANGE_ID_REUSED`.

Der fachliche Kern ist jetzt abgedeckt für Track-Daten, Mängel, Abnahmen, BTB, Baustellenstruktur/Mitglieder und Upload-Lebenszyklus. Capability: **`idempotent_mutations_v1=true`**.

Portal-/Admin-Stammdatenmutationen sind kein Offline-Outbox-Fachpfad und werden separat betrachtet. Binäre Upload-Chunks sind absichtlich über Upload-ID + Offset geschützt statt über Mutation-Receipts.

## Tests / Checks

Server-Checks:
- `20_verify_tb_server_1_foundation.py`
- `21_verify_tb_server_1_auth_source.py`
- `22_verify_tb_server_1_auth_runtime.py`
- `23_verify_tb_server_1_files_source.py`
- `24_verify_tb_server_1_defects_acceptances_source.py`
- `25_verify_tb_server_1_track_survey_source.py`
- `26_verify_tb_server_1_idempotency_source.py`
- `27_verify_tb_server_1_replay_coverage_source.py`

`27` parst BTB-, Worksite- und Datei-Module per AST und prüft die erwarteten Receipt-Typen, Change-/Audit-Marker und die Capability `idempotent_mutations_v1`.

Der Workflow prüft außerdem frische Baseline, Kerntabellen einschließlich `mutation_receipts`, Bootstrap, Health und aktivierte Capabilities.

### Bekanntes GitHub-Actions-Problem

Die zuletzt geprüften TB-Server-Workflow-Läufe wurden bereits vor dem ersten Step beendet: `runner_id=0`, keine Steps, keine Job-Logs. Diese roten Läufe sind daher keine ausgeführten Python-/PostgreSQL-Tests. Das Runner-/Actions-Problem muss separat geklärt werden.

Android-CI ist auf Android-/Gradle-relevante Pfade begrenzt; reine Änderungen unter `server/tbserver/**` sollen keinen vollständigen Android-Build auslösen.

## Bekannte/offene Punkte

- Android-Geräteaktivierung/-authentifizierung v1 für `/api/v1`;
- Parser/Katalogisierung für TRA/KF/GRA/KOR/KV/GVP und Abschnittszusammenführung;
- echtes BTB-Abschluss-PDF + Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout;
- vollständige Portal-Fachoberfläche;
- Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität;
- Background Jobs;
- Backup/Restore/Reset-Werkzeuge;
- vollständige Integrations-/E2E-Tests;
- produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Geräteaktivierung/-authentifizierung v1 für Android gegen die vorhandenen `devices`/`device_activation_tokens`-Tabellen bauen; Standardregel: ein aktives Gerät pro Benutzer.
2. Vermessungsparser/Katalogisierung auf der unveränderlichen Originalablage ergänzen.
3. Portal/PDF/Admin/Releases schließen.
4. Backup/Restore/Reset + E2E testen.
5. Erst danach NAS-Cutover und Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
