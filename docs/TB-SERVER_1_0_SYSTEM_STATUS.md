# TB-Server 1.0 – Admin-Systemstatus / Speicher

Stand: 2026-09-18

Dieser Baustein liefert den Systemstatus für TB-Server 1.0. Der Status selbst bleibt read-only; zusätzlich können ADMINs jetzt sichere Backup-Aufträge in den getrennten Background-Job-Worker einreihen. Restore, produktive Datenänderungen und NAS-Migrationen werden hier weiterhin nicht ausgeführt.

## API

`GET /api/v1/admin/system/status`

Zugriff: ausschließlich Portal-ADMIN.

Die Antwort enthält:

- Serverversion und Umgebung;
- Migrationsstatus inkl. `known/applied/pending/latest_known/latest_applied`;
- Datenbankgröße über PostgreSQL `pg_database_size(current_database())`;
- Anzahl Firmen, Benutzer, aktive Benutzer, aktive Geräte, Baustellen, BTBs und Dateien;
- erfasste Dateigröße;
- offene Mängel und noch nicht finalisierte Abnahmen;
- Anzahl wartender/laufender/fehlgeschlagener Background Jobs;
- letzten Geräte-Kontakt und letzten erfolgreichen/erfassten Sync-Zeitpunkt;
- Dateisystembelegung des konfigurierten Storage-Roots;
- Dateisystembelegung des getrennten Backup-Roots;
- die letzten Backup-Läufe ohne internen Storage-Key;
- die letzten Background Jobs ohne Payload und ohne `last_error`;
- ausschließlich explizit freigegebene, nicht geheime `server_settings`;
- firmenweise Nutzer-/Geräte-/Baustellen-/Dateizahlen.

## Keine Geheimnisse in der Statusantwort

Der Endpunkt gibt ausdrücklich **nicht** aus:

- Datenbankpasswort oder andere ENV-Geheimnisse;
- Aktivierungs-/Bearer-/Einladungstokens oder Token-Hashes;
- Job-Payloads;
- `last_error` von Background Jobs;
- interne Backup-`storage_key`-Pfade;
- private Zertifikate/Keys.

Der Storage wird nur mit Label sowie Gesamt-/Belegt-/Frei-Bytes und Prozentbelegung beschrieben.

## Weboberfläche

`GET /admin/v1/system`

Die Seite zeigt:

- KPI-Kacheln für Firmen/Nutzer/Geräte/Baustellen/BTBs/Mängel/Abnahmen/Jobs;
- API-/Migrations-/Datenbankstatus;
- Storage-Belegung;
- firmenweise Belegung;
- letzte Backup-Läufe;
- letzte Background Jobs.

Die Statusanzeige bleibt lesend. Zusätzlich stehen drei ausdrücklich getrennte Backup-Aktionen zur Verfügung: Datenbank-Backup, Datei-Backup und Vollbackup. Diese Aktionen führen das Backup nicht im HTTP-Request aus, sondern stellen einen idempotenten Background Job ein. Für erfolgreiche Backups kann außerdem ein Restore-Test gegen die ausdrücklich getrennte Testdatenbank/Testablage eingereiht werden. ADMIN kann zusätzlich die konfigurierte Backup-Aufbewahrungsregel als Background Job prüfen lassen. Ein Restore auf die laufende TB-Helfer-Datenbank bleibt weiterhin gesperrt.

## Warum read-only zuerst

Produktive NAS-Migrationen und größere Datenänderungen dürfen laut Projektregeln nur nach geprüftem Backup erfolgen. Ein Status-Dashboard darf deshalb nicht nebenbei unausgereifte Backup-/Restore-Aktionen auslösen.

Dieser Baustein trennt sauber:

1. Beobachten / Diagnose;
2. Background-Job-Mechanik;
3. Backup erzeugen;
4. Restore-Test;
5. kontrollierter Restore;
6. später produktiver Cutover.

## Capabilities

- `admin_system_status_v1=true`
- `background_jobs_v1=true`
- `backup_v1=true`
- `backup_retention_v1=true`
- `restore_test_v1=true`
- `test_data_reset_v1=true`

## Tests

`server/checks/39_verify_tb_server_1_system_status_source.py` prüft:

- Syntax des neuen Systemstatus-Moduls;
- ADMIN-Schutz;
- Migrations-/DB-/Storage-/Backup-/Job-Datenquellen;
- Webroute und Template;
- Capability-/Workflow-Vertrag;
- dass keine bekannten Geheimnis-/Payloadfelder in die Statusantwort eingebaut werden.

`server/checks/41_verify_tb_server_1_backup_jobs_source.py` prüft zusätzlich Background Worker, getrennten Backup-Root, pg_dump-Vertrag, Datei-/FULL-Backup, SHA-256, Retry/Stale-Job-Regeln, Admin-Backupauftrag und Capabilities.

`server/checks/42_verify_tb_server_1_restore_test_reset_source.py` prüft Restore-Test-Trennung, pg_restore-/Archiv-/SHA-Regeln sowie den ausschließlich in Development/Test/Staging erlaubten FULL-Backup-gesicherten Test-Reset.

## Noch offen

- kontrollierter produktiver Restore;
- Retention praktisch gegen echte Backup-Dateien prüfen;
- Systemmetriken sammeln/aggregieren;
- E-Mail-Berichte;
- produktive NAS-Backup-/Cutover-Abnahme.

Produktiv-NAS und produktive Daten werden durch Status und Backup-Auftragscode nicht verändert. Die tatsächliche Backup-Ausführung schreibt ausschließlich in den konfigurierten `TB_BACKUP_ROOT` und liest Datenbank/Storage. Details: `docs/TB-SERVER_1_0_BACKUP_RESTORE.md`.

## Automatik, E-Mail-Berichte und Audit

Server 1.0 erweitert die bestehende Systemstatus-Seite um die bewährte Bedienlogik des bisherigen Servers, verwendet darunter aber ausschließlich die neue Background-Job-/Backup-Infrastruktur.

Konfigurierbar im Admin:
- automatische Backupart `DATABASE`, `FILES` oder `FULL` und Uhrzeit;
- automatische Retention-Prüfung und Uhrzeit;
- Aufbewahrungstage und Mindestanzahl erfolgreicher Backups je Art;
- E-Mail-Berichte täglich, wöchentlich oder monatlich;
- Berichtsuhrzeit, Wochentag/Monatstag und IANA-Zeitzone;
- Empfängerliste;
- Warnzeitraum für auslaufende Benutzerzugänge;
- Speicherwarnschwelle;
- manueller SMTP-Testbericht.

Der Worker prüft Zeitpläne periodisch. `tb.system_schedule_runs` reserviert pro Zeitfenster einen eindeutigen Slot, damit ein Neustart oder mehrere Worker denselben automatischen Auftrag nicht doppelt erzeugen.

Die Admin-Seite zeigt zusätzlich:
- bald ablaufende Benutzer;
- letzte Audit-/Sicherheitsereignisse;
- SMTP-Status ohne Geheimwerte;
- letzten E-Mail-Background-Job.

SMTP wird ausschließlich über Umgebungsvariablen/Secrets konfiguriert. Das Passwort wird weder in PostgreSQL noch im HTML, Audit oder Status-Payload ausgegeben.
