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

## Capabilities

- `background_jobs_v1=true`
- `backup_v1=true`

## Sicherheitsgrenzen

Dieser Baustein:

- führt keinen Restore auf die laufende Datenbank aus;
- löscht keine produktiven Daten;
- setzt keine Datenbank zurück;
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

## Noch offen

- Restore-Test gegen eine ausdrücklich getrennte Testdatenbank;
- kontrollierter Restore;
- Test-/Staging-Reset mit harter Production-Sperre;
- Backup-Aufbewahrung/Rotation;
- E2E-Test des Workers und aller Backuparten gegen PostgreSQL + persistente Storage-Mounts;
- produktive NAS-Backup- und Restore-Abnahme.

