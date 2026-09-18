# TB-Server 1.0 – Admin-Systemstatus / Speicher

Stand: 2026-09-18

Dieser Baustein liefert den **read-only** Systemstatus für TB-Server 1.0. Er führt keine Backups, Restores, produktiven Datenänderungen oder NAS-Migrationen aus.

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

Die Seite ist absichtlich nur lesend. Backup-/Restore-Buttons werden erst ergänzt, wenn der zugehörige Server-1.0-Backup-/Restore-Vertrag inklusive Sicherheits- und Testregeln implementiert ist.

## Warum read-only zuerst

Produktive NAS-Migrationen und größere Datenänderungen dürfen laut Projektregeln nur nach geprüftem Backup erfolgen. Ein Status-Dashboard darf deshalb nicht nebenbei unausgereifte Backup-/Restore-Aktionen auslösen.

Dieser Baustein trennt sauber:

1. Beobachten / Diagnose;
2. Background-Job-Mechanik;
3. Backup erzeugen;
4. Restore-Test;
5. später produktiver Cutover.

## Capability

`admin_system_status_v1=true`

## Tests

`server/checks/39_verify_tb_server_1_system_status_source.py` prüft:

- Syntax des neuen Systemstatus-Moduls;
- ADMIN-Schutz;
- Migrations-/DB-/Storage-/Backup-/Job-Datenquellen;
- Webroute und Template;
- Capability-/Workflow-Vertrag;
- dass keine bekannten Geheimnis-/Payloadfelder in die Statusantwort eingebaut werden.

## Noch offen

- Background-Job-Worker;
- serverseitiger Datenbank-/Datei-/FULL-Backup-Lauf;
- Backup-Aufbewahrung;
- Restore-Test;
- kontrollierter Restore;
- Systemmetriken sammeln/aggregieren;
- E-Mail-Berichte;
- produktive NAS-Backup-/Cutover-Abnahme.

Produktiv-NAS und produktive Daten werden durch diesen Statusbaustein nicht verändert.
