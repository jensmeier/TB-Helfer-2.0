# TB-Server 1.0 – Staging-Deploy nach Integrationsbau 25.09.2026

## Zweck

Dieser Deploy bringt den SERVER-Quellstand nach der vollständigen Handy/Server/Portal-Querprüfung
auf die Synology-Staging-Instanz. Er enthält eine neue DB-Migration und mehrere zusammengehörige
Server-/Portal-Verträge. Deshalb **keine Einzeldatei dieses Blocks isoliert deployen**.

Repository: `jensmeier/TB-Helfer-2.0`  
Branch: `feature/tb-server-1.0`

## Enthalten

- Portal Punkt 4: allgemeine Arbeiten als echte `ARBEIT`-Einträge
- getrennte Admin-/Portal-Browser-Sessions
- skalierbarer Delta-Long-Poll `/api/v1/sync/changes/wait`
- kanonische, dauerhaft gespeicherte Foto-Metadaten
- Bild-Renditions `thumb` / `preview`
- Portal nutzt kleinere Vorschaubilder statt Originaldateien
- API-Speicherlimit als zweite Schutzschicht nach dem bestätigten NAS-OOM
- Migration **015_file_photo_metadata.sql**
- Staging-Selfcheck und 255-Client-Delta-Probe

## Vor dem Deploy

1. Aktuellen SERVER-Branch/HEAD auf GitHub prüfen.
2. Android-Branch prüfen; keine `app/**`-Dateien aus diesem Deploy übernehmen.
3. FULL-Backup erstellen und nur bei Status `OK` fortfahren.
4. Aktuellen Migrationsstand prüfen.

Die neuen `.renditions`-Verzeichnisse sind **nur abgeleitete Thumbnail-/Preview-Caches**.
FILES/FULL-Backups schließen diese Caches bewusst aus; Originaldateien bleiben vollständig im Backup.
Nach Restore werden Renditions bei Bedarf automatisch neu erzeugt.

Vor dem Einspielen von Migration 015 wird auf der aktuell laufenden Synology erwartet:

```
known: 15
applied: 14
pending: ['015_file_photo_metadata.sql']
latest_known: 15
latest_applied: 14
```

Falls der Status davon abweicht: **nicht raten, Deploy stoppen und Ursache prüfen.**

## Quellstand auf NAS synchronisieren

Zusammengehörig aktualisieren:

- `server/tbserver/**`
- `server/templates/**`
- `server/v1_migrations/015_file_photo_metadata.sql`
- `server/docker-compose.synology-staging.v1.yml`
- optional Diagnose-/Testwerkzeuge aus `server/tools/**`

Keine Änderung an:

- Datenbank-Volume
- Storage-/Foto-Originalen
- Backup-Verzeichnis
- Secrets/.env
- Android `app/**`

Für diesen Block wurden weder `requirements.txt` noch `Dockerfile.v1` geändert.
Ein Image-Rebuild ist daher fachlich nicht erforderlich; die Synology-Staging-Compose bindet
`tbserver`, `templates` und `v1_migrations` als Quellverzeichnisse ein.

## Migration 015

Nach dem vollständigen Quell-Sync, aber **vor dem Neustart/Recreate der API**:

```bash
python -m tbserver.migrations status
python -m tbserver.migrations up
python -m tbserver.migrations status
```

Ziel:

```
known: 15
applied: 15
pending: []
latest_known: 15
latest_applied: 15
```

## Container/Compose

Die API-Compose enthält neu ein Memory-Limit:

```
TB_V1_API_MEMORY_LIMIT=512m
```

bzw. den Compose-Default `512m`.

Damit dieses cgroup-Limit wirksam wird, reicht ein normaler Prozess-Restart nicht:
der API-Service muss über das Synology-Projekt/Compose **recreated** werden.
Dabei niemals das Datenbank-Volume löschen.

Nach dem Recreate müssen mindestens grün/gesund sein:

- `tb-helfer-v1-db-1`
- `tb-helfer-v1-api-1`
- `tb-helfer-v1-worker-1`
- `tb-helfer-v1-cloudflared-1`

## In-Container Runtime-Probe

Nach Migration + API-Recreate im API-Container:

```bash
python /app/tools/server1_runtime_probe.py
```

Der Probe ändert keine Live-Fachdaten. Erwartet werden:
- DB erreichbar
- Migrationen 15/15, keine Pending-Migration
- `tb.files.photo_metadata` = NOT NULL jsonb
- benötigte Integrations-Capabilities vorhanden
- synthetische Preview-Rendition max. 1280×1280
- API-cgroup-Memory-Limit aktiv

Erst bei `"status": "OK"` mit Browser-/Delta-E2E fortfahren.

## Read-only Preflight

Von einer Umgebung mit Zugriff auf die Staging-URL:

```bash
python server/tools/server1_staging_selfcheck.py \
  --base-url https://api-v1.tb-helfer.de
```

Ohne Device-Token werden Health + Capabilities geprüft.
Mit sicher lokal gesetztem `TB_DEVICE_TOKEN` wird zusätzlich der authentifizierte Delta-Vertrag geprüft.
Token niemals in Chat, GitHub, Screenshot oder Log kopieren.

## Browser-E2E

### Admin/Portal-Trennung

Im **selben Browser**:

1. Benutzerportal als normaler Nutzer anmelden.
2. zweiten Tab öffnen, Admin-Einstieg verwenden und als Admin anmelden.
3. zwischen Tabs wechseln.
4. Portal-Baustelle/BTB/Fotos öffnen.
5. Admin-Baustelle/BTB/Fotos öffnen.
6. Portal abmelden: Admin muss angemeldet bleiben.
7. Admin abmelden: Portal-Session darf dadurch nicht rückwirkend überschrieben worden sein.

Erst dann PRB-020 praktisch bestätigen.

Optional kann derselbe Vertrag ohne Passwortausgabe automatisiert geprüft werden.
Credentials nur lokal als Umgebungsvariablen setzen:

```bash
python server/tools/session_split_e2e.py \
  --base-url https://www.tb-helfer.de
```

Das Werkzeug verwendet absichtlich **einen** Cookie-Jar und erwartet gleichzeitig
`tb_portal_*` und `tb_admin_*`.

### BTB Punkt 4

Im Portal eine allgemeine Arbeit ohne Firma anlegen.
Sie muss als Punkt **4. Ausgeführte Arbeiten** erscheinen und nach Reload dort bleiben.
Serverseitig muss sie ein `ARBEIT`-Entry mit stabilem `arbeit-frei:<n>`-Slot sein.

### Portal-Foto

Im aktiven BTB ein normales Foto mit normalem Dateinamen hochladen und im Portal erfassen:

- Punkt 4–8
- Kurztext
- optional Firma
- optional KM
- optional Objekt/Bereich

Nach Reload müssen Zuordnung und Angaben erhalten bleiben.
Die Galerie soll die `preview`-Rendition verwenden; Baustellen-Fachbilder die `thumb`-Rendition.
Originaldateien bleiben unverändert.

## Delta-Lastprobe

Erst mit einem **lokal gesetzten** gültigen Device-Token:

```bash
python server/tools/sync_wait_loadtest.py \
  --base-url https://api-v1.tb-helfer.de \
  --clients 32 \
  --wait-seconds 10
```

Wenn 32/32 stabil sind, anschließend:

```bash
python server/tools/sync_wait_loadtest.py \
  --base-url https://api-v1.tb-helfer.de \
  --clients 255 \
  --wait-seconds 10
```

Das ist ein Staging-Lasttest, kein Produktivtest.

## Gemeinsamer Abschluss mit ANDROID

Der Clientvertrag steht in:

`docs/TB-SERVER_ANDROID_SYNC_VERTRAG_2026-09-25.md`

Gesamt-Sync wird erst BESTÄTIGT, wenn der dort beschriebene Pixel ↔ Server ↔ Poco ↔ Portal-E2E
inklusive Schicht Ende, Finalize, Foto-Sync, Foto-Lösen, automatischem Delta und Offline-Retry
praktisch bestanden ist.


## Bevorzugter Synology-Apply

Nach dem vollständigen Upload/Synchronisieren des aktuellen SERVER-Quellstands kann der
Integrationsblock als **ein bewachter Ablauf** über den DSM-Aufgabenplaner ausgeführt werden.

Benutzer: `root`

```bash
sh /volume1/docker/tb-helfer-v1/tools/synology_integration_apply.sh
```

Das Skript führt absichtlich nur diese Reihenfolge aus:

1. laufende Kerncontainer prüfen,
2. FULL-Backup erstellen und `OK` verlangen,
3. Migrationsstand auf genau Source 15 / Staging 14 oder bereits 15 prüfen,
4. Migration 015 anwenden und 15/15 verlangen,
5. **nur** API/Worker/Monitor recreaten – DB-/Restore-Volumes werden nicht angefasst,
6. API-Health abwarten,
7. `server1_runtime_probe.py` ausführen.

Es gibt im Skript kein `compose down`, kein `-v`, kein Volume-Remove, kein Prune und kein
rekursives Löschen. Bei unerwartetem Migrationsstand oder fehlerhaftem Backup stoppt es vor
der Migration.

Wichtig: Das Skript ersetzt **nicht** den vollständigen Quell-Sync. Es wird erst gestartet,
wenn `tbserver/**`, `templates/**`, `v1_migrations/**`, `tools/**` und die aktuelle
Compose-Datei auf dem NAS auf demselben SERVER-HEAD liegen.


## Read-only Server-Abnahme

Für die Serverfunktionen, die ohne Fachdatenmutation geprüft werden können, liegt zusätzlich
`server/tools/server1_readonly_acceptance.py` vor.

Es verwendet absichtlich **einen** Cookie-Jar und prüft damit zugleich die Trennung von
Benutzerportal und Admin-Sitzung. Geprüft werden read-only:

- Portal-Login und `/api/v1/portal/me`
- Benutzerprofil und Firmenverzeichnis
- globaler Katalog + Historie
- Portal-Start + Baustellenübersicht
- Admin-Login parallel zur Portal-Session
- Admin-Systemstatus, Firmen- und Benutzerliste
- Admin-Seiten Übersicht / Strecken / Bahnhöfe / System
- Portal-Logout ohne Verlust der Admin-Sitzung
- Admin-Logout

Credentials nur lokal als Umgebungsvariablen setzen; niemals in GitHub, Chat oder Logs schreiben.

Beispiel von einem Rechner mit Zugriff auf die Portal-URL:

```bash
python server/tools/server1_readonly_acceptance.py \
  --base-url https://www.tb-helfer.de
```

Wenn Login/Passwörter über Umgebungsvariablen gesetzt sind, müssen sie nicht als Kommandozeilenargument
auftauchen. Das Tool führt keine fachlichen CREATE/UPDATE/DELETE-Aufrufe aus.

Die mutierenden Fach-E2Es (z. B. Katalog CREATE/UPDATE, automatischer Backupjob, BTB-/Foto-Sync)
bleiben bewusst separate praktische Tests.


## Worker-/Backup-E2E

Nach erfolgreichem Deploy + Runtime-Probe kann der echte Background-Worker mit einem realen
Backupjob geprüft werden:

`server/tools/server1_backup_job_e2e.py`

Das Tool:

1. meldet sich über die getrennte Admin-Session an,
2. legt **einen echten Backup-Background-Job** an,
3. pollt den Admin-Systemstatus,
4. protokolliert Status/Prozent/Arbeitsschritt,
5. verlangt `DONE`,
6. verlangt anschließend einen neuen passenden Backup-Run mit `status=OK`,
   positiver Dateigröße und vorhandener SHA-256,
7. meldet die Admin-Sitzung wieder ab.

Standard ist `DATABASE`, damit der Worker-Test klein bleibt. Für den vollständigen
Backup-Pfad kann bewusst `--kind FULL` verwendet werden.

Credentials ausschließlich lokal als Umgebungsvariablen oder interaktiv bereitstellen;
niemals in Chat, GitHub oder gespeicherte Logs kopieren.

Dieser Test deckt insbesondere den offenen praktischen Teil von PRB-019 sowie
SRV-020/SRV-021 ab. Ein bestandener Source-Check ersetzt diesen echten Worker-Lauf nicht.


## Gebündelte Post-Deploy-Abnahme

Nach erfolgreichem `=== DEPLOY_OK ===` kann die vorbereitete Abnahme gebündelt im **API-Container-Terminal** ausgeführt werden:

```bash
python /app/tools/server1_post_deploy_e2e.py \
  --portal-base-url http://127.0.0.1:8080 \
  --server-base-url http://127.0.0.1:8080 \
  --portal-login <normaler-testnutzer> \
  --admin-login <admin-testnutzer>
```

Fehlende Geheimwerte werden interaktiv verdeckt abgefragt:
- Portal-Passwort
- Admin-Passwort
- Geräte-Token für den Delta-Test

Die Geheimwerte werden nicht ausgegeben und nicht als Child-Process-Argumente weitergereicht.
Für nicht-interaktive Ausführung müssen sie ausschließlich als lokale Environment-Werte
`TB_PORTAL_PASSWORD`, `TB_ADMIN_PASSWORD` und `TB_DEVICE_TOKEN` vorhanden sein.

Reihenfolge:
1. In-Container Runtime-Probe
2. Same-Browser Session-Split
3. Read-only Portal/Admin/Profile/Katalog/System-Abnahme
4. echter Worker-`DATABASE`-Backupjob
5. Delta-Long-Poll mit standardmäßig 32 Clients

Optional:
- `--backup-kind FULL` für bewussten FULL-Backup-E2E
- `--clients 255` erst nach bestandenem 32-Client-Lauf
- `--skip-backup` bzw. `--skip-loadtest` nur wenn der jeweilige praktische Test bewusst separat erfolgt

Erfolg ist ausschließlich:

```
=== POST_DEPLOY_E2E_OK ===
```

Das Werkzeug ersetzt **nicht** den anschließenden Pixel/Poco/Portal-Fach-E2E. Es bündelt nur
die bereits vorhandenen Server-Abnahmen.


### Cloudflare-Hinweis für automatisierte Browser/API-Tests

Der praktische Lauf am 26.09.2026 zeigte, dass Cloudflare den Python-`urllib`-Client
auf `https://www.tb-helfer.de` mit HTTP 403 und
`The site owner has blocked access based on your browser's signature.` blockiert.

Das ist **kein TB-Portal-Loginfehler** und kein Grund, Browser-/Bot-Schutzregeln abzuschalten.
Für die automatisierten Session-/Read-only-/Worker-/Delta-Abnahmen **innerhalb des API-Containers**
wird deshalb die interne echte API verwendet:

```
http://127.0.0.1:8080
```

Der öffentliche Cloudflare-/Browserweg bleibt ein separater praktischer E2E mit einem echten Browser:
normalen Portalnutzer und Admin in zwei Tabs anmelden, zwischen beiden wechseln und die unabhängigen
Sessions/Logouts bestätigen.
