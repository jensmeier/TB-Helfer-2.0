# TB-Server 1.0 – Docker-/NAS-Deployment

Stand: 2026-09-18

Dieser Stand ergänzt eine reproduzierbare Compose-Basis für **Staging/Test und den später kontrollierten NAS-Cutover**. Er führt selbst keinen produktiven Cutover aus.

Datei:

`server/docker-compose.v1.yml`

## Dienste

### db

- `postgis/postgis:17-3.5`;
- persistenter PostgreSQL-Datenpfad;
- Healthcheck über `pg_isready`;
- Datenbankname/Benutzer aus nicht geheimen bzw. extern gesetzten Compose-Variablen;
- Passwort ausschließlich über `TB_V1_DB_PASSWORD`, niemals im Repository.

### migrate

Verwendet dasselbe `Dockerfile.v1` wie API/Worker und führt vor deren Start aus:

`python -m tbserver.migrations up`

Der Migration Runner prüft weiterhin die SHA-256 bereits angewendeter Migrationen. API/Worker starten erst nach erfolgreichem Migrationslauf.

### api

- FastAPI/uvicorn aus `Dockerfile.v1`;
- persistenter `STORAGE_ROOT=/tb/storage-v1`;
- separater persistenter `TB_BACKUP_ROOT=/tb/backups-v1`;
- Ready-Healthcheck auf `/health/ready`;
- Default-Bindung nur an `127.0.0.1:8081`.

Für den späteren Cloudflare-Tunnel kann Bindung/Port bewusst angepasst oder ein gemeinsames Docker-Netz verwendet werden. Cloudflare bleibt Transportweg und wird nicht in die Server-Fachlogik eingebaut.

### worker

Dasselbe Image, aber eigener Prozess:

`python -m tbserver.jobs worker`

API und Worker teilen Datenbank, Fach-Storage und Backup-Root. Dadurch laufen Backup-/Restore-Test-Jobs außerhalb des HTTP-Prozesses.

## Verzeichnisse

Compose verwendet standardmäßig relativ zum `server/`-Verzeichnis:

- `./database-v1`
- `./storage-v1`
- `./backups-v1`

Auf Synology sollen diese drei Pfade bewusst auf persistente NAS-Verzeichnisse gelegt werden.

Wichtig:

- Datenbankdaten getrennt;
- Fach-/Dateispeicher getrennt;
- Backup-Root getrennt;
- Backup-Root darf nicht innerhalb des Fach-Storage liegen;
- produktive Vermessungsoriginale liegen nach dem Cutover im persistenten Fach-Storage und werden bei Updates nicht zurückgesetzt.

## Erforderliche externe Variablen

Mindestens:

- `TB_V1_DB_PASSWORD`

Empfohlen explizit:

- `TB_V1_DB_DATA_DIR`
- `TB_V1_STORAGE_DIR`
- `TB_V1_BACKUP_DIR`
- `TB_V1_ENVIRONMENT`
- `TB_V1_API_BIND`
- `TB_V1_API_PORT`

Optional:

- `TB_V1_DB_NAME`
- `TB_V1_DB_USER`
- `TB_V1_PORTAL_SESSION_HOURS`
- `TB_V1_LOG_LEVEL`
- `TB_V1_BACKUP_RETENTION_DAYS`
- `TB_V1_BACKUP_RETENTION_MIN_PER_KIND`
- `TZ`

**Keine echten Werte dieser Geheimnisse/Pfade mit Zugangsdaten nach GitHub committen.**

## Staging-Start

Vom Verzeichnis `server/` aus sinngemäß:

`docker compose -f docker-compose.v1.yml up -d --build`

Vor einem späteren NAS-Einsatz müssen die Variablen/Pfade in Synology Container Manager bzw. der dortigen Compose-Umgebung gesetzt werden.

Die Startreihenfolge ist:

1. PostgreSQL gesund;
2. nummerierte Migrationen erfolgreich;
3. API + Worker starten;
4. API-Ready-Healthcheck grün.

## Initialer Admin

Nach einer frischen Server-1.0-Baseline wird der initiale Administrator separat über den vorhandenen Bootstrap-Pfad angelegt. Bootstrap-Zugangsdaten bleiben externe Einmal-/Umgebungswerte und gehören nicht in Compose/GitHub.

## Backup / Restore

API und Worker mounten denselben getrennten Backup-Root. DATABASE-/FILES-/FULL-Backups sind dadurch persistent.

Restore-Test-Datenbank und Restore-to-Target werden **nicht** automatisch in den normalen Compose-Start aufgenommen. Sie sind bewusste Wartungsaktionen mit eigenen `TB_RESTORE_TEST_*` bzw. `TB_RESTORE_TARGET_*`-Variablen und Bestätigungssperren.

Ein Restore-to-Target soll einen getrennten PostgreSQL-/Storage-Zielstand erzeugen. Erst nach dessen Prüfung wird ein eigener Cutover geplant; die laufende Instanz wird nicht automatisch überschrieben.

## Optionaler Restore-Staging-Overlay

Für den praktischen Backup-/Restore-Test gibt es zusätzlich:

`server/docker-compose.restore-test.v1.yml`

Der Overlay wird **nicht** vom normalen Serverstart verwendet. Er wird nur bewusst zusammen mit der Basiskonfiguration geladen:

`docker compose -f docker-compose.v1.yml -f docker-compose.restore-test.v1.yml up -d --build`

Zusätzlich zum normalen `TB_V1_DB_PASSWORD` müssen für diesen Testlauf separat gesetzt sein:

- `TB_V1_RESTORE_TEST_DB_PASSWORD`;
- `TB_V1_RESTORE_TARGET_DB_PASSWORD`.

Der Overlay erzeugt zwei getrennte PostgreSQL-17-Ziele:

- `restore-test-db` für zerstörbare Restore-Tests;
- `restore-target-db` für den kontrollierten Restore-to-Target.

Standardmäßig verwenden sie eigene persistente Pfade:

- `./restore-test-database-v1`;
- `./restore-target-database-v1`;
- `./restore-target-storage-v1`.

Der Worker erhält nur in diesem Overlay die `TB_RESTORE_TEST_DB_*`- und `TB_RESTORE_TARGET_DB_*`-Zielparameter.

### Restore-Test

Nach einem erfolgreichen DATABASE-/FILES-/FULL-Backup kann der Restore-Test wie bisher über die Admin-Systemseite bzw. den API-Job eingereiht werden. DATABASE/FULL verwenden dabei `restore-test-db`; FILES/FULL extrahieren nur in die gesicherte temporäre Restore-Test-Ablage.

### Restore-to-Target

Restore-to-Target bleibt eine explizite CLI-Wartungsaktion und ist auch im Overlay standardmäßig gesperrt:

`TB_V1_ALLOW_RESTORE_TARGET=NO`

Für einen bewusst gestarteten Staging-Test muss die Variable nur für diesen Testlauf auf `YES` gesetzt werden. Danach wird im Worker-Container mit der dynamischen Bestätigung gearbeitet:

`python -m tbserver.restore_target restore-target --backup-run-id <uuid> --confirm RESTORE-TARGET:<uuid>:<target-db-name>`

Der Ziel-Datenbankname muss vom Live-Namen abweichen. Ziel-Datenbank und Ziel-Storage müssen frisch/leer sein. Ein bereits benutztes Restore-Ziel wird nicht still überschrieben.

Der Overlay ist damit eine reproduzierbare Testhilfe, **kein** automatischer Produktiv-Restore und kein Cutover-Mechanismus.

## Produktiver Cutover

Verbindliche Reihenfolge:

1. bestehenden NAS-/DB-Stand vollständig sichern;
2. Backup verifizieren;
3. TB-Server 1.0 mit **neuen** Datenbank-/Storage-Pfaden starten;
4. Migrationen/Health/Capabilities prüfen;
5. Server-1.0-End-to-End-Test durchführen;
6. Testdaten bei Bedarf nur über den gesicherten Test-/Staging-Reset neu initialisieren;
7. Administrator/Firmen/Testnutzer neu anlegen;
8. Portal/Fachfunktionen/Backup/Restore-Test praktisch prüfen;
9. erst danach Cloudflare/Android auf den neuen Server umschalten;
10. alten Serverstand zunächst als Rückfallmöglichkeit behalten.

Ab dem ersten echten produktiven Vermessungsimport sind Originaldateien unveränderlich und spätere Updates verwenden **keinen** Test-Reset mehr.

## Source-Check

`server/checks/44_verify_tb_server_1_deployment_source.py` prüft insbesondere:

- PostgreSQL/PostGIS 17;
- getrennte DB-/Storage-/Backup-Mounts;
- DB-Passwort nur als externe Compose-Variable;
- Migration-Service vor API/Worker;
- API-Ready-Healthcheck;
- separaten Worker-Prozess;
- keine Restore-to-Target-Automatik im normalen Start.

`server/checks/49_verify_tb_server_1_restore_compose_overlay_source.py` prüft zusätzlich den optionalen Restore-Staging-Overlay, getrennte Ziel-Datenbanken/-Pfade, externe Restore-Passwörter und die standardmäßig deaktivierte Restore-to-Target-Freigabe.

