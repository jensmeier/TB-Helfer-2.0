# TB-Server 1.0 – Docker-/NAS-Deployment

Stand: 2026-09-19

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

Der Migration Runner prüft weiterhin die SHA-256 bereits angewendeter Migrationen. API/Worker starten erst nach erfolgreichem Migrationslauf. Zusätzlich wartet der Migrationsprozess bei einem rein transportseitigen PostgreSQL-Startfehler (`OperationalError` ohne SQLSTATE) begrenzt und versucht die Verbindung erneut. Der Compose-Service besitzt ergänzend `restart: on-failure:5`, damit ein transient fehlgeschlagener Einmallauf beim frischen Projektstart automatisch erneut versucht wird. Nach erfolgreichem Lauf endet `migrate` absichtlich mit Exit Code 0 und bleibt gestoppt; das ist der Sollzustand. Bei einem normalen NAS-/Docker-Neustart starten DB, API und Worker über `unless-stopped` selbstständig wieder, während die bereits angewendeten Migrationen persistent in PostgreSQL erhalten bleiben. Echte PostgreSQL-Fehler mit SQLSTATE werden weiterhin sofort abgebrochen.

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
- `TB_V1_SMTP_HOST`
- `TB_V1_SMTP_PORT`
- `TB_V1_SMTP_USER`
- `TB_V1_SMTP_PASSWORD`
- `TB_V1_SMTP_SENDER`
- `TB_V1_SMTP_STARTTLS`
- `TB_V1_SMTP_SSL`
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

Praktischer Synology-Staginglauf am 19.09.2026: frische PostgreSQL-17-Instanz gestartet, Migrationen `001` bis `009` erfolgreich angewendet, API und Worker gestartet und `/health/ready` mit HTTP 200 bestätigt. Beim allerersten Init trat einmal das PostgreSQL-Initialisierungsfenster zwischen temporärem Init-Server und finalem Serverstart auf; der oben beschriebene begrenzte Retry plus Compose-`on-failure`-Absicherung wurde danach ergänzt. Beim ersten Portalaufruf zeigte sich zusätzlich, dass `server/templates/` zwar im Repository und NAS-Buildkontext lag, aber im Dockerfile nicht ins Image kopiert wurde. `Dockerfile.v1` kopiert die Templates jetzt nach `/app/templates`; der Deployment-Check prüft das ausdrücklich.

## Synology-Staging ohne Image-Löschen

Für die laufende Entwicklungs-/E2E-Testphase gibt es zusätzlich:

`server/docker-compose.synology-staging.v1.yml`

Diese Datei ist **nur für Staging/Test auf der Synology** gedacht. Sie hält die persistente Datenhaltung unverändert getrennt, bindet aber die versionierten Laufzeitquellen read-only direkt aus dem Projektordner ein:

- `./tbserver -> /app/tbserver:ro`
- `./templates -> /app/templates:ro`
- `./v1_migrations -> /app/v1_migrations:ro`

Damit gilt im normalen Staging-Alltag:

1. aktuelle Dateien aus GitHub nach `/docker/tb-helfer-v1` übernehmen;
2. bei Python-Codeänderungen den betroffenen Container (`api` bzw. `worker`) neu starten;
3. bei Template-Änderungen Browser neu laden; bei Bedarf `api` kurz neu starten;
4. **kein** Löschen von Containern oder `tb-helfer-server-v1:local` erforderlich.

Ein echter Image-Rebuild bleibt nur nötig, wenn sich die Image-Grundlage ändert, insbesondere:

- `Dockerfile.v1`;
- `requirements.txt`;
- Betriebssystem-/Python-Abhängigkeiten.

Neue oder geänderte DB-Migrationen bleiben ein bewusster Deployment-Schritt: `migrate` muss gegen die aktualisierten read-only gemounteten `v1_migrations` erfolgreich laufen, bevor API/Worker mit dem neuen Schema getestet werden.

Der normale `docker-compose.v1.yml` bleibt absichtlich **ohne** Source-Mounts und ist weiterhin der reproduzierbare image-basierte Vertrag für späteren Cutover/Produktivbetrieb.

Für Synology Container Manager wird während der Stagingphase der Inhalt von `docker-compose.synology-staging.v1.yml` als Projekt-`compose.yml` verwendet. Die bestehenden persistenten Ordner `database-v1`, `storage-v1`, `backups-v1` und die lokale `.env` bleiben unverändert.

Aktueller Laufzeitvertrag nach Entfernung des einmaligen Restore-Targets:
- der normale Synology-Worker mountet **kein** `restore-target-storage-v1` und erhält keine `TB_RESTORE_TARGET_*`-Variablen;
- der getrennte `restore-test-db`-Pfad bleibt erhalten;
- Restore-to-Target existiert nur noch im optionalen `docker-compose.restore-test.v1.yml`-Wartungs-Overlay;
- `cloudflared` startet im normalen Synology-Projekt unabhängig vom API-Health mit `restart: always`. Der Tunnel darf bereits verbunden sein, während die API noch hochfährt; dadurch bleibt der Tunnelstart nicht an einem einmaligen Health-Gate hängen.

## Cloudflare-Staging für Tests außerhalb des Heimnetzes

Für den E2E-Test außerhalb des Heimnetzes wird **nicht** der bestehende Produktivhostname `api.tb-helfer.de` umgeschaltet. Stattdessen wird ein eigener Staging-Hostname verwendet:

`https://api-v1.tb-helfer.de`

Der vorhandene Cloudflare-Tunnel kann eine zusätzliche Published-Application-Route auf denselben NAS-Host bekommen:

- Public hostname: `api-v1.tb-helfer.de`
- Origin/Service URL: `http://192.168.178.3:8081`

Der alte Server und `api.tb-helfer.de` bleiben dabei unverändert. Der Staging-Server muss im LAN weiter auf `0.0.0.0:8081` lauschen. Cloudflare terminiert extern HTTPS; der Tunnel spricht intern HTTP zum getrennten Staging-Port.

Für Android-Debug wird nach erfolgreichem externen Health-Test in `local.properties` statt der LAN-Adresse gesetzt:

```properties
tb.server.base.url=https://api-v1.tb-helfer.de
```

Der Release-Build bleibt unabhängig davon fest auf `https://api.tb-helfer.de`.

Für den gemeinsamen App-API-Hostname wird im Staging **keine interaktive Cloudflare-Access-Anmeldung** vorgeschaltet, weil Android die Geräteaktivierungs-/Bearer-API direkt erreichen muss. Portal und API bleiben durch ihre eigenen Server-1.0-Authentifizierungsmechanismen geschützt. Eine spätere Trennung von Admin-Portal und App-API auf getrennte Hostnamen kann zusätzlich gehärtet werden.

## Android gegen Synology-Staging testen

Der normale Gerätefluss benötigt **keine manuell konfigurierte Serveradresse**. Die Geräte-Aktivierungskarte trägt die kanonische öffentliche HTTPS-Serveradresse im QR/Deep-Link mit. Android speichert diese Adresse erst nach erfolgreicher Aktivierung und verwendet sie anschließend für alle Server-/Sync-Clients.

Für Staging muss der Server deshalb einmalig seine öffentliche Adresse kennen:

```properties
TB_V1_PUBLIC_BASE_URL=https://api-v1.tb-helfer.de
TB_V1_PORTAL_PUBLIC_BASE_URL=https://www.tb-helfer.de
```

`TB_V1_PUBLIC_BASE_URL` bleibt die kanonische App-/API-Adresse. `TB_V1_PORTAL_PUBLIC_BASE_URL` ist die getrennte Browser-/Portaladresse und wird für Portal-Aktivierungslinks sowie Browser-Baustelleneinladungen verwendet.

Für den bestehenden remote verwalteten Tunnel `tb-helfer-nas` wird zusätzlich ein **Public Hostname `www.tb-helfer.de`** angelegt. Als Service/Origin wird **derselbe interne API-Dienst verwendet wie bei der funktionierenden `api-v1.tb-helfer.de`-Route**; keine zweite Serverinstanz anlegen. Nach dem Routing führt `https://www.tb-helfer.de/` auf `/portal/v1/login`.

Diese Variablen gehören in die lokale Synology-`.env` und werden nicht nach GitHub committed. Weil `TB_V1_PORTAL_PUBLIC_BASE_URL` eine Container-Environment-Variable ist, muss der API-Container nach der Compose-/ENV-Änderung **neu erstellt** werden; ein bloßer Prozess-Neustart übernimmt neue Environment-Werte nicht zuverlässig.

Der Android-Debug-Build behält `local.properties`/`tbServerBaseUrl`/`TB_SERVER_BASE_URL` nur als **Entwickler-Fallback**, z. B. für manuelle Key-Tests ohne QR. Für die normale QR-Aktivierung ist das nicht erforderlich.

Sicherheitsregeln:

- Aktivierungs-QRs akzeptieren als Serveridentität ausschließlich HTTPS;
- die Serveradresse wird erst nach erfolgreicher Gerätebindung persistent übernommen;
- der Aktivierungs-Key selbst wird nach erfolgreicher Bindung nicht gespeichert;
- der **Release-Build** bleibt als Fallback fest auf `https://api.tb-helfer.de`;
- `local.properties` bleibt gitignoriert und darf nicht versioniert werden.

## Geräteaktivierungskarte als QR/PDF

Die Server-1.0-Geräteaktivierung erzeugt weiterhin einen kurzlebigen Einmal-Schlüssel. Die App-URI enthält jetzt zusätzlich die kanonische öffentliche Serveridentität: `tbhelfer://activate?server=https%3A...&key=...`.

Die Admin-Seite **Nutzer/Geräte** liefert nach `Gerät aktivieren` zusätzlich:

- QR-Code für den direkten Scan in der Android-App;
- herunterladbare PDF-Aktivierungskarte mit Benutzer, Firma, Ablaufzeit, QR und Fallback-Schlüssel;
- Browser-Aktion `PDF teilen / versenden`; falls Web Share mit Dateien nicht verfügbar ist, wird die PDF zum manuellen Anhängen heruntergeladen.

Die PDF wird nicht als dauerhafte Serverdatei abgelegt. Sie wird nur in der einmaligen Aktivierungsantwort erzeugt. Die Antwort trägt `Cache-Control: no-store`; auf dem Server bleiben Aktivierungsschlüssel weiterhin nur gehasht gespeichert.

Eine neu erzeugte noch unbenutzte Aktivierung widerruft den vorherigen offenen Aktivierungsschlüssel desselben Benutzers. Bereits gebundene Geräte bleiben davon unberührt, bis sie ausdrücklich freigegeben werden.

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


### SMTP für Systemberichte

SMTP wird ausschließlich über die lokale Synology-`.env` bzw. Secret-Verwaltung gesetzt. Insbesondere `TB_V1_SMTP_PASSWORD` darf nie nach GitHub, in Statusdateien, Screenshots oder Logs übernommen werden.

Die Admin-Oberfläche zeigt nur Host/Port/Absender und ob ein Passwort gesetzt ist. Automatische und manuelle Testberichte werden über den Background-Worker versendet.
