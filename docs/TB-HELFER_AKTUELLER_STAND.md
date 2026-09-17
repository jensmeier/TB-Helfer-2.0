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
- Replay-sicherer Fachkern: `e9f16e335badbd9f036405024f89aa47fcb598fe`
- Geräteaktivierung/Bearer-Grundlage: `2bede07a590521210dc7c351582363565451c4a0`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Strategie

Serverbasis vollständig bauen und testen, danach kontrollierter NAS-Cutover mit vorher geprüftem Backup, anschließend Android systematisch auf den festen `/api/v1`-Vertrag umstellen.

Der derzeitige Server-Fachdatenbestand ist Testbestand. Beim 1.0-Cutover dürfen Testnutzer, Testbaustellen und Test-/Leerdaten für TRA/KF/GRA/KOR/KV/GVP sowie Strecken-/Bahnhofs-/Vermessungskataloge entfernt werden. GitHub-Code, Konfiguration/Secrets und Backups bleiben erhalten. Statische Grunddaten werden aus definierter Quelle neu aufgebaut.

**Ab dem ersten echten produktiven Vermessungsimport sind Originaldateien unveränderlich und dürfen durch spätere Updates/Resets nicht gelöscht oder überschrieben werden.** Bearbeitete/berechnete Daten liegen getrennt.

## TB-Server 1.0 – umgesetzt

### Foundation / Migrationen

Vorhanden sind:
- neues Paket `server/tbserver/` und separater `server/Dockerfile.v1`;
- Baseline `server/v1_migrations/001_server_1_0_base.sql`;
- Migration Runner mit Nummerierung, SHA-256-Prüfung und PostgreSQL-Advisory-Lock;
- `002_device_credentials.sql` als **neue** Migration; die Baseline 001 wurde nicht nachträglich verändert;
- Health/Ready/Capabilities;
- Firmen, Benutzer, Portal-Aktivierung, Login/Sessions, CSRF, Bootstrap/Admin-Grundlage.

### Baustellen / Rechte / Teams

Vorhanden sind Baustellen und Unterbaustellen mit UUIDs, Mitgliedschaften, Rechteprofile/Overrides, Teams mit `active_btb_id` und serverseitige Rechteprüfung.

Replay-sicher über `X-Client-Change-Id` sind:
- `WORKSITE_CREATE`;
- `WORKSITE_NODE_CREATE`, `WORKSITE_NODE_UPDATE`, `WORKSITE_NODE_DELETE`;
- `WORKSITE_MEMBER_ADD`, `WORKSITE_MEMBER_PERMISSIONS_UPDATE`.

Knoten- und Mitgliedschaftsänderungen erzeugen Change-Feed/Audit; Knotenlöschungen erhalten Tombstones.

Capabilities: `worksite_tree_v1`, `worksite_permissions_v1`, `worksite_teams_v1` = `true`.

### Granularer BTB / Live-Sync

Vorhanden sind stabiler BTB-Kopf, einzelne versionierte `btb_entries`, Versionskonflikte, aktiver BTB je Team, erster unveränderlicher Original-Snapshot beim Abschluss, Rework, Tombstone-Löschen, Change Feed und SSE-Live-Signal.

Replay-sicher sind BTB anlegen, Kopf ändern, Einträge anlegen/ändern/löschen, abschließen, zur Nachbearbeitung öffnen und löschen.

Capabilities: `btb_entries_v1=true`, `btb_live_sync_v1=true`, `btb_finalize_with_pdf_v1=false`.

### Dateien / Bilder / Chunkupload

`server/tbserver/files.py` enthält resumierbare sequenzielle Upload-Sessions mit 8 MiB Standard-/16 MiB Maximal-Chunk, Offset-/Größen-/SHA-256-Prüfung, fsync, atomarem Abschluss in UUID-basierte Storage-Pfade, `tb.files`, `tb.file_links`, geschütztem Abruf, Upload-Abbruch sowie Change/Audit.

Replay-sicher sind Session-Erstellung, Dateiabschluss und Upload-Abbruch. Chunk-Schreiben bleibt bewusst upload-ID-/offset-basiert.

Capabilities: `files_v1=true`, `chunk_upload_v1=true`, `company_logo_v1=false`.

### Mängel / Abnahmen

Versionierte UUID-Objekte mit Knotenzuordnung, Rechten, `base_version`, `409 VERSION_CONFLICT`, Change Feed und Audit.

Replay-sicher:
- `DEFECT_CREATE`, `DEFECT_UPDATE`, `DEFECT_CLOSE`;
- `ACCEPTANCE_CREATE`, `ACCEPTANCE_UPDATE`, `ACCEPTANCE_FINALIZE`.

Capabilities: `defects_v1=true`, `acceptances_v1=true`.

### Unveränderliche Vermessungsoriginale / Track-Arbeitsdaten

`server/tbserver/survey_originals.py` und `track_data.py` trennen Original und Arbeitsstand:
- Originalimport prüft Größe + SHA-256;
- atomare Ablage unter `reference/survey-originals/<uuid>/...`;
- Originalmetadaten in `tb.survey_originals`;
- keine PATCH-/PUT-/DELETE-Endpunkte und kein SQL-Update/Delete für Originale;
- identische Originalbytes werden als Duplikat erkannt;
- veränderbare/versionierte Arbeitsdaten liegen in `tb.track_data` mit optionalem `source_original_id`;
- Optimistic Locking + Tombstones.

Capabilities: `survey_originals_v1=true`, `track_data_v1=true`.

### Idempotenz / Offline-Replay

`server/tbserver/idempotency.py` nutzt `tb.mutation_receipts` und `X-Client-Change-Id`.

- Schlüssel = Benutzer + `client_change_id`;
- PostgreSQL-Transaktions-Advisory-Lock serialisiert parallele Wiederholungen;
- erfolgreiche Antwort wird JSON-sicher gespeichert;
- identischer Replay bekommt dieselbe Antwort;
- Wiederverwendung für anderen Mutationstyp -> `409 CLIENT_CHANGE_ID_REUSED`.

Der fachliche Kern ist abgedeckt für Track, Mängel, Abnahmen, BTB, Baustellenstruktur/Mitglieder und Upload-Lebenszyklus. Capability: `idempotent_mutations_v1=true`.

### Geräteaktivierung / Android-Bearer-Auth – aktueller Block

Der bereits praktisch getestete Android-Vertrag wird beibehalten:
- `POST /api/v1/device/login` mit `key`, `device_uid`, `device_name`, `os_version`, `app_version`;
- Antwort enthält einmalig `token` und Benutzerstammdaten;
- `GET /api/v1/device/me` prüft `Authorization: Bearer <token>`;
- Fehlercodes bleiben kompatibel, u. a. `DEVICE_ALREADY_BOUND` und `DEVICE_IN_USE`.

Server-1.0-Erweiterungen:
- Aktivierungs-Key nur gehasht in `tb.device_activation_tokens`;
- Geräte-Bearer-Token nur gehasht (`auth_token_hash`) in `tb.devices`;
- nur maskierter `auth_token_hint` für Administration;
- `002_device_credentials.sql` erzwingt über partiellen Unique-Index maximal ein aktives Gerät pro Benutzer;
- Admin kann einen Einmal-Aktivierungs-Key ausgeben;
- Admin kann ein Gerät `release`n: Token sofort ungültig, Status `REVOKED`, danach neue Aktivierung nötig;
- Admin kann verlorenes Gerät `block`en: Token sofort ungültig, Geräte-ID bleibt gesperrt;
- Aktivierung und Freigabe werden ohne Geheimwerte auditiert;
- Audit unterstützt jetzt optional `actor_device_id`.

Auth-Trennung:
- `/api/v1/admin/*` bleibt strikt Portal-Session + CSRF;
- `/api/v1/portal/*` bleibt strikt Portal-Session + CSRF;
- Fach-API außerhalb dieser beiden Bereiche akzeptiert zusätzlich ein gültiges Geräte-Bearer-Token;
- bei Geräteauthentifizierung ist kein Browser-CSRF nötig, da kein Cookie-Credential verwendet wird;
- derselbe serverseitige Firmen-/Baustellen-/Rechtecheck gilt danach unverändert.

Capabilities:
- `device_activation_v1=true`;
- `device_bearer_auth_v1=true`;
- `device_fach_api_v1=true`.

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
- `28_verify_tb_server_1_device_auth_source.py`
- `29_verify_tb_server_1_device_fach_auth_source.py`

Workflow-Zielstand:
- Migrationen 001 + 002;
- `latest_known=2`, `latest_applied=2`;
- Prüfung der neuen Device-Credential-Spalten und Unique-Indizes;
- Bootstrap/Health/Capabilities/Auth-Checks.

### Bekanntes GitHub-Actions-Problem

Die zuletzt geprüften TB-Server-Workflow-Läufe wurden bereits vor dem ersten Step beendet: `runner_id=0`, keine Steps, keine Job-Logs. Diese roten Läufe sind **keine ausgeführten Python-/PostgreSQL-Tests**. Das Runner-/Actions-Problem muss separat geklärt werden.

Android-CI ist auf Android-/Gradle-relevante Pfade begrenzt; reine Serveränderungen sollen keinen vollständigen Android-Build auslösen.

## Bekannte/offene Punkte

- Android-Fachclient selbst ist noch nicht auf alle neuen Server-1.0-Fachendpunkte umgestellt; der Serververtrag ist dafür jetzt vorbereitet.
- Datei-Metadaten `source` werden beim gemeinsamen Uploadpfad derzeit noch als `PORTAL` angelegt; vor Android-Dateiupload muss dies auf `APP`/`PORTAL` nach Auth-Art differenziert werden.
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

1. Vermessungsparser/Katalogisierung für TRA/KF/GRA/KOR/KV/GVP auf der unveränderlichen Originalablage bauen.
2. Danach den Datei-Upload-Source-Marker für Geräte/Portal trennen und den vollständigen Android-Fachvertrag bei der späteren Client-Anbindung praktisch testen.
3. Portal/PDF/Admin/Releases schließen.
4. Backup/Restore/Reset + E2E testen.
5. Erst danach NAS-Cutover und Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
