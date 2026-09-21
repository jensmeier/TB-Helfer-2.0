# TB-Server 1.0 – Background Jobs, Backup und Restore

Stand: 2026-09-18

Dieser Stand implementiert zuerst die sichere Grundlage für asynchrone Serverarbeiten und Backups. Restore/Reset werden darauf separat aufgebaut und sind in diesem Abschnitt noch nicht als produktive Aktion freigegeben.

## Background-Job-Worker

Modul: `server/tbserver/jobs.py`

Der Worker nutzt die bereits in der 1.0-Baseline vorhandene Tabelle `tb.background_jobs`.

Unterstützte Jobtypen:

- `BACKUP_DATABASE`
- `BACKUP_FILES`
- `BACKUP_FULL`
- `RESTORE_TEST`

Arbeitsweise:

- Claim mit `FOR UPDATE SKIP LOCKED`, damit mehrere Worker denselben Job nicht doppelt übernehmen;
- Statusfolge `PENDING -> RUNNING -> DONE`;
- bei kontrollierbarem Fehler erneuter Versuch mit Backoff;
- maximal fünf Versuche;
- `RUNNING`-Jobs, die länger als 30 Minuten nicht aktualisiert wurden, werden beim Worker-Start als verwaist erkannt und erneut eingeplant bzw. nach Erreichen des Limits als `FAILED` markiert;
- unbekannte Jobtypen werden dauerhaft abgelehnt;
- Job-Payloads und interne Fehlertexte werden nicht in der normalen Systemstatus-Webseite ausgegeben.

Start:

`python -m tbserver.jobs worker`

Ein einmaliger Lauf für Wartung/Test ist über `python -m tbserver.jobs run-one` möglich.

## Backup-Root

Neue Konfiguration:

`TB_BACKUP_ROOT=/tb/backups-v1`

Der Backup-Root muss außerhalb von `STORAGE_ROOT` liegen. Dadurch kann ein Dateispeicher-Backup nicht versehentlich den eigenen Backup-Ordner rekursiv mit archivieren.

Backups und Anwendungsspeicher sind logisch getrennte Bereiche. Auf dem NAS sollen sie bei der späteren Installation auf getrennte persistente Mounts zeigen.

## Datenbank-Backup

Jobtyp: `BACKUP_DATABASE`

- verwendet PostgreSQL-17-`pg_dump`;
- Custom-Format;
- `--no-owner` und `--no-privileges`;
- Datenbankpasswort wird ausschließlich über die Prozessumgebung `PGPASSWORD` übergeben, nicht als Kommandozeilenargument oder Logtext;
- Ausgabe wird zuerst in eine temporäre Datei geschrieben und anschließend atomar an den finalen Pfad verschoben;
- leere Dumps gelten als Fehler.

Der vorhandene `server/Dockerfile.v1` enthält bereits `postgresql-client-17`.

## Datei-Backup

Jobtyp: `BACKUP_FILES`

Der komplette konfigurierte `STORAGE_ROOT` wird als `.tar.gz` archiviert. Da `TB_BACKUP_ROOT` außerhalb des Storage liegen muss, werden Backups selbst nicht mitgesichert.

Originaldateien werden beim Backup nur gelesen und nicht verändert.

## Vollbackup

Jobtyp: `BACKUP_FULL`

Ein Vollbackup enthält:

- `database.dump`;
- `files.tar.gz`;
- `manifest.json`.

Das Manifest enthält Formatkennung, UTC-Erstellzeit und SHA-256 beider inneren Bestandteile. Anschließend erhält auch das äußere Vollbackup einen SHA-256-Eintrag in `tb.backup_runs`.

Formatkennung:

`tb-server-1.0-full-backup-v1`

## Backup-Protokoll

Die bereits vorhandene Tabelle `tb.backup_runs` protokolliert:

- Art;
- Status;
- internen Storage-Key;
- Dateigröße;
- SHA-256;
- Start-/Endzeit;
- kontrollierten Fehlercode.

Die normale Admin-Statusantwort gibt den internen Backup-Pfad und Detail-/Fehlertext nicht aus.

## Admin-API und Weboberfläche

`POST /api/v1/admin/system/backups`

Payload:

`{"kind":"DATABASE|FILES|FULL"}`

Der Endpunkt:

- ist ausschließlich ADMIN + Portal-CSRF zugänglich;
- nutzt `X-Client-Change-Id`/Mutation Receipt;
- erzeugt nur einen Background Job und führt das Backup nicht im HTTP-Request aus;
- schreibt einen Audit-Eintrag ohne Geheimnisse.

Die Systemstatus-Seite kann Datenbank-, Datei- und Vollbackup-Aufträge einreihen. Sie zeigt zusätzlich die Belegung des Backup-Roots und den Zustand der Background Jobs.

## Restore-Test

Ein Restore-Test verändert niemals die laufende TB-Helfer-Datenbank oder den produktiven Storage.

API:

`POST /api/v1/admin/system/backups/{backup_run_id}/restore-test`

Der Endpunkt stellt lediglich einen Background Job `RESTORE_TEST` ein. Unterstützte Quellen sind erfolgreiche DATABASE-, FILES- und FULL-Backups.

Für Datenbankanteile ist eine **separate** Restore-Test-Datenbank erforderlich. Konfiguration:

- `TB_RESTORE_TEST_DB_NAME` – Pflicht;
- `TB_RESTORE_TEST_DB_PASSWORD` – Pflicht;
- `TB_RESTORE_TEST_DB_HOST` – optional, Default wie Live-DB;
- `TB_RESTORE_TEST_DB_PORT` – optional, Default wie Live-DB;
- `TB_RESTORE_TEST_DB_USER` – optional, Default wie Live-DB.

Der Datenbankname darf niemals dem Namen der Live-Datenbank entsprechen. Vor dem Restore wird ausschließlich in dieser getrennten Testdatenbank das Schema `tb` gelöscht und anschließend `pg_restore --clean --if-exists --exit-on-error` gegen das geprüfte Dump ausgeführt.

Nach dem Restore werden mindestens das Migrationsregister und zentrale TB-Server-1.0-Tabellen geprüft.

Für FILES/FULL gilt zusätzlich:

- Archive dürfen nur unter `storage/` liegen;
- absolute Pfade und `..` werden verworfen;
- Symlinks/Hardlinks/Device-Dateien werden beim Restore-Test nicht zugelassen;
- FULL-Backup muss exakt `database.dump`, `files.tar.gz` und `manifest.json` enthalten;
- die beiden inneren SHA-256-Werte müssen mit dem Manifest übereinstimmen.

Jeder Restore-Test erhält einen eigenen `tb.backup_runs`-Eintrag mit `kind=RESTORE_TEST`. Die normale Admin-Statusansicht zeigt weiterhin keine internen Pfade oder Fehlerdetails.

## Test-/Staging-Reset

CLI:

`python -m tbserver.reset_test reset-test-data --backup-run-id <uuid> --confirm RESET-TEST-DATA:<uuid>`

Sicherheitsgrenzen:

- ausschließlich `TB_ENVIRONMENT=development|test|staging`;
- in jeder anderen Umgebung wird der Reset hart abgelehnt;
- vor dem Reset ist ein erfolgreicher FULL-Backup-Lauf Pflicht;
- das FULL-Backup darf höchstens 24 Stunden alt sein;
- Größe und SHA-256 des Backups werden vor dem Reset erneut geprüft;
- die Bestätigung muss exakt die UUID des verifizierten FULL-Backups enthalten;
- `TB_BACKUP_ROOT` muss außerhalb von `STORAGE_ROOT` liegen.

Erst danach wird das fachliche `tb`-Schema gelöscht, die nummerierte Server-1.0-Migrationsbaseline frisch aufgebaut und anschließend nur der Inhalt von `STORAGE_ROOT` geleert. Backups, GitHub-Code und Konfiguration/Secrets liegen außerhalb und werden nicht gelöscht.

Nach einem Reset muss der initiale Test-Admin bewusst neu gebootstrapped und Testdaten neu angelegt werden.

## Kontrollierter Restore auf getrenntes Ziel

Produktive Recovery wird **nicht** als In-place-HTTP-Restore umgesetzt. Stattdessen existiert ein bewusst separater Wartungsweg, der ein verifiziertes FULL-Backup in eine **frische, vom laufenden Server getrennte** Datenbank und einen getrennten Storage-Root wiederherstellt.

CLI:

`python -m tbserver.restore_target restore-target --backup-run-id <uuid> --confirm RESTORE-TARGET:<uuid>:<target_db_name>`

Zusätzliche Freigabe:

`TB_ALLOW_RESTORE_TARGET=YES`

Zielkonfiguration:

- `TB_RESTORE_TARGET_DB_NAME` – Pflicht und ungleich Live-`DB_NAME`;
- `TB_RESTORE_TARGET_DB_PASSWORD` – Pflicht;
- `TB_RESTORE_TARGET_DB_HOST/PORT/USER` – optional;
- `TB_RESTORE_TARGET_STORAGE_ROOT` – Pflicht, disjunkt von Live-Storage und Backup-Root.

Der Ziel-Datenbankbestand muss frisch sein, also noch kein `tb`-Schema besitzen. Der Ziel-Storage muss leer sein. Es wird ausschließlich ein erfolgreiches FULL-Backup akzeptiert.

Ablauf:

1. äußere Backupgröße/SHA-256 verifizieren;
2. FULL-Layout + innere SHA-256 gegen `manifest.json` verifizieren;
3. `pg_restore` ohne `--clean` in die frische Ziel-Datenbank;
4. Migrationsregister und zentrale Tabellen prüfen;
5. Dateiarchiv mit denselben Pfad-/Link-Sicherheitsregeln in temporäre Ablage extrahieren;
6. Dateien in den getrennten Ziel-Storage kopieren;
7. Restore-Lauf als `RESTORE_TARGET` protokollieren und ohne Benutzergeheimnisse auditieren.

Wenn ein Restore-Schritt scheitert, wird der neu erzeugte Ziel-Storage wieder geleert und das `tb`-Schema der getrennten Ziel-Datenbank entfernt. Die laufende Live-Datenbank und der Live-Storage werden nicht verändert.

Der nächste Schritt nach erfolgreichem Restore ist **nicht** automatisches Umschalten, sondern eine Offline-/Testprüfung des Zielservers. Erst danach darf ein eigener kontrollierter Cutover auf die wiederhergestellte Zielinstanz erfolgen.

Migration `005_restore_target_run_kind.sql` erweitert ausschließlich die erlaubten `tb.backup_runs.kind`-Werte um `RESTORE_TARGET`.

## Backup-Aufbewahrung / Rotation

Konfiguration:

- `TB_BACKUP_RETENTION_DAYS` – Default 30;
- `TB_BACKUP_RETENTION_MIN_PER_KIND` – Default 3.

Die Regel gilt getrennt für DATABASE, FILES und FULL. Ein Backup wird nur dann freigegeben, wenn es älter als die Aufbewahrungsfrist ist **und** für seine Art mehr als die Mindestanzahl jüngerer erfolgreicher Backups vorhanden ist.

Jobtyp: `BACKUP_RETENTION`

Admin-API: `POST /api/v1/admin/system/backups/retention`

Sicherheitsverhalten:

1. Kandidaten werden per Window-Ranking je Backupart bestimmt;
2. der DB-Lauf wird zuerst auf `PURGED` gesetzt und der interne `storage_key` entfernt;
3. erst danach wird die konkrete Backupdatei gelöscht;
4. wenn das Dateilöschen scheitert, bleibt höchstens eine unreferenzierte Sicherheitsdatei zurück – niemals ein `OK`-Datensatz, der auf eine bereits gelöschte Datei zeigt;
5. der gesamte Backup-Root wird niemals rekursiv gelöscht.

Migration `006_backup_retention_status.sql` ergänzt dafür den Status `PURGED`. Die Admin-Systemseite zeigt die aktive Policy und kann die Aufbewahrungsprüfung als Background Job einreihen.

## Capabilities

- `background_jobs_v1=true`
- `backup_v1=true`
- `backup_retention_v1=true`
- `restore_test_v1=true`
- `test_data_reset_v1=true`
- `restore_target_v1=true`

## Sicherheitsgrenzen

Dieser Baustein:

- führt keinen Restore auf die laufende Datenbank aus;
- führt Restore-Tests nur gegen eine ausdrücklich getrennte Testdatenbank/-ablage aus;
- löscht produktive Daten nicht;
- erlaubt einen Datenreset ausschließlich in Development/Test/Staging und nur nach verifiziertem frischem FULL-Backup;
- ändert keine produktiven Vermessungsoriginale;
- enthält keine Geheimwerte im Repository.

Vor einem späteren produktiven Restore/Cutover bleibt ein verifiziertes Backup Pflicht.

## Tests

`server/checks/41_verify_tb_server_1_backup_jobs_source.py` prüft insbesondere:

- Syntax der Backup-/Worker-Module;
- getrennten Backup-Root;
- `PGPASSWORD` statt Passwort im `pg_dump`-Kommando;
- Custom-Dump;
- Datei-/FULL-Backup und Manifest;
- SHA-256;
- atomaren Dateitausch;
- `FOR UPDATE SKIP LOCKED`;
- Retry-/Stale-Job-Regeln;
- idempotenten ADMIN-Backupauftrag;
- Admin-Weboberfläche;
- Capabilities und Workflow;
- vorhandenen PostgreSQL-17-Client im 1.0-Dockerfile.

`server/checks/42_verify_tb_server_1_restore_test_reset_source.py` prüft zusätzlich:

- getrennte Restore-Test-Datenbank;
- kein Restore gegen den Live-Datenbanknamen;
- `pg_restore` mit Passwort nur über `PGPASSWORD`;
- FULL-Manifest-/SHA-Prüfung;
- sichere Archivpfade ohne Symlink-/Hardlink-Wiederherstellung;
- Restore-Test-Job/API;
- harte Reset-Umgebungsgrenze;
- FULL-Backup-Pflicht und 24-Stunden-Grenze;
- dynamische Bestätigung `RESET-TEST-DATA:<backup_uuid>`;
- frische Migrationen und getrennten Backup-Root.

## Noch offen

- Restore-to-Target praktisch gegen eine echte PostgreSQL-/Storage-Testinstanz ausführen und danach den manuellen Cutover prüfen;
- Retention praktisch mit echten Backup-Dateien und abgelaufenen Testläufen prüfen;
- E2E-Test des Workers und aller Backuparten gegen PostgreSQL + persistente Storage-Mounts;
- produktive NAS-Backup- und Restore-Abnahme.


## Automatische Backup- und Retention-Aufträge

Migration `012_system_automation.sql` ergänzt deduplizierte Zeitplan-Slots. Der Background-Worker kann damit nach der im Admin gespeicherten Policy automatisch `BACKUP_DATABASE`, `BACKUP_FILES` oder `BACKUP_FULL` sowie `BACKUP_RETENTION` einreihen.

Die Automatik ruft dieselben vorhandenen, geprüften Backupfunktionen auf wie die manuellen Buttons. Sie baut **keinen zweiten Backupweg** und verändert den Restore-Vertrag nicht.

Die Retention-Werte können im Admin angepasst werden. Sie bleiben weiterhin sicher:
- nur erfolgreiche DATABASE-/FILES-/FULL-Artefakte;
- Mindestbestand je Backupart;
- keine rekursive Löschung des Backup-Roots;
- Restore-Test und Restore-to-Target bleiben getrennte, explizite Vorgänge.

Der aktuell praktisch bestätigte FULL-Backup-Pfad wird durch diese Erweiterung nicht geändert.
