# TB-Helfer Server – Stand 0.6.1

Der produktive Server läuft auf der Synology als Docker-Projekt `tb-helfer`. Android greift über `https://api.tb-helfer.de` per Cloudflare Tunnel zu; die Admin-Oberfläche bleibt getrennt unter `https://admin.tb-helfer.de` hinter Cloudflare Access.

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

## Sicherheitsregeln

- `.env`, SMTP-Passwort, Cloudflare-Tunnel-Token und Datenbankpasswort gehören nicht ins Repository.
- Originaldateien (`TRA`, `KF`, `GRA`, `KOR` usw.) werden durch den Sync nicht überschrieben.
- Bei Versionskonflikten antwortet der Server mit 409 und der aktuellen Serverversion des Objekts.
- Ein wiederholter Upload derselben lokalen Änderung darf nicht doppelt angewendet werden.

## Installation auf dem NAS

Vor einer neuen Servermigration immer zuerst ein manuelles DB-Backup erstellen. Migrationen mit `psql -v ON_ERROR_STOP=1` ausführen, Schema-Version prüfen und erst danach das API-Image neu bauen.
