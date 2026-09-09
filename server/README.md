# TB-Helfer Server – Stand 0.7.0 Vorbereitung

Der produktive Server läuft auf der Synology als Docker-Projekt `tb-helfer`. Android greift über `https://api.tb-helfer.de` per Cloudflare Tunnel zu; die Admin-Oberfläche bleibt getrennt unter `https://admin.tb-helfer.de` hinter Cloudflare Access.

## Produktiver Ist-Stand

- Server/API: 0.6.0
- Datenbankschema: 7 (`007_admin_crud_sync_delete`)
- Android 200018 ist lokal erfolgreich gebaut, als Update installiert und gegen 0.6.0 auf Grundfunktionen geprüft.
- `main` bleibt stabil; die Arbeiten liegen im Draft-Branch `feature/admin-offline-sync`.

## 0.6.1 – Offline-Sync

Die nächste Serverstufe ergänzt zur Admin-Verwaltung aus 0.6.0:

- `GET /api/v1/sync/changes?after=<sequence>` – inkrementeller Änderungsfeed
- `POST /api/v1/sync/ack` – Gerät bestätigt erfolgreich übernommene Sequenz
- `POST /api/v1/sync/mutations` – Android lädt lokale Änderungen hoch
- Versionsprüfung mit HTTP 409 statt stiller Überschreibung
- idempotente Wiederholung durch `sync_mutation_receipts`
- `current_version` auch für Strecken und Bahnhöfe
- Abschnittshistorie über `route_section_versions`
- DELETE-Ereignisse werden aus `change_log` als Tombstones an Geräte verteilt
- mobile Schreibzugriffe zunächst nur für `SECTION` und `OBJECT`; Strecke/Bahnhof bleiben Admin-Verwaltung

Dazu gehört Migration `migrations/008_incremental_sync_mutations.sql`.

## 0.7.0 – Baustellen-Zusammenarbeit

Migration `migrations/009_worksite_collaboration.sql` bildet die verbindlichen Projektregeln ab:

- Teams nur innerhalb derselben Firma
- TB-Gruppen A/B/C/D usw. mit Hilfsnutzern
- BTB bleibt je TB-Gruppe getrennt
- Mängel und Abnahmen werden baustellenweit innerhalb des berechtigten Bereichs synchronisiert
- Bilder/Anlagen werden als Attachments mitsynchronisiert
- endgültig erledigte Mängel/Abnahmen werden nicht wieder geöffnet
- Benutzer-Speicheroptionen steuern die dauerhafte Serveraufbewahrung; temporäres Spooling für die Synchronisation bleibt möglich

## Wichtige Voraussetzung vor Server-0.7-Code

Der produktive API-Quellstand 0.6.0 aus `/volume1/docker/tb-helfer/server/` ist aktuell **noch nicht vollständig in diesem Repository**. Im GitHub-Ordner `server/` liegen derzeit nur Dokumentation, Bahnhofsdaten und Migrationen. Deshalb darf die API nicht aus Annahmen neu geschrieben werden.

Vor der 0.7.0-Implementierung muss der tatsächliche produktive Server-Quellstand aus der Synology in diesen Feature-Branch übernommen werden – **ohne** `.env`, Passwörter, Cloudflare-Tokens, SMTP-Zugangsdaten oder sonstige Secrets. Die `.gitignore` blockiert `.env`/`*.env` und typische Schlüsseldateien zusätzlich.

## Sicherheitsregeln

- `.env`, SMTP-Passwort, Cloudflare-Tunnel-Token und Datenbankpasswort gehören nicht ins Repository.
- Originaldateien (`TRA`, `KF`, `GRA`, `KOR` usw.) werden durch den Sync nicht überschrieben.
- Bei Versionskonflikten antwortet der Server mit 409 und der aktuellen Serverversion des Objekts.
- Ein wiederholter Upload derselben lokalen Änderung darf nicht doppelt angewendet werden.
- Produktive Migrationen nur nach einem unmittelbar vorher erzeugten und geprüften DB-Backup.

## Installation auf dem NAS

Vor einer neuen Servermigration immer zuerst ein manuelles DB-Backup erstellen. Migrationen mit `psql -v ON_ERROR_STOP=1` ausführen, Schema-Version prüfen und erst danach das API-Image neu bauen.
