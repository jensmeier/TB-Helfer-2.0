# TB-Helfer Server – 0.7.0 Entwicklungsstand

Der produktive Server läuft auf der Synology als Docker-Projekt `tb-helfer`. Android greift über `https://api.tb-helfer.de` per Cloudflare Tunnel zu; die Admin-Oberfläche bleibt getrennt unter `https://admin.tb-helfer.de` hinter Cloudflare Access.

## Produktiver Ist-Stand

- Server/API: **0.6.0**
- Datenbankschema: **7** (`007_admin_crud_sync_delete`)
- Synology/NAS bleibt Source of Truth.
- Migrationen 008/009 sind produktiv **noch nicht** ausgeführt.
- `main` bleibt stabil; 0.7 liegt im Draft-Branch `feature/admin-offline-sync` / PR #5.

## Quellbasis für 0.7

Der tatsächlich produktiv verwendete 0.6.0-Serverquellstand aus `/volume1/docker/tb-helfer/server/` wurde am 2026-09-09 aus der gesicherten NAS-Kopie übernommen und gegen die vorbereiteten Android-/DB-Verträge abgeglichen. Die 0.7-API ist damit **nicht aus Annahmen neu geschrieben**, sondern direkt aus dem realen FastAPI-0.6.0-Server weiterentwickelt.

Zur sicheren Übernahme über die GitHub-Schnittstelle ist der geprüfte Python-Runtime verlustfrei in `app_parts/01_part.py` ff. zerlegt. `app.py` lädt diese nummerierten Teile in derselben Reihenfolge und im selben Modul-Namespace. Dockerfile, Python-Abhängigkeiten und die produktiven Admin-Templates liegen ebenfalls im Branch.

Geheimnisse bleiben außerhalb des Repositories. DB-Passwort, Admin-/Geräte-Token-Secret und SMTP-Zugangsdaten werden weiterhin über Umgebungsvariablen bezogen. `.env`, Schlüsseldateien und Python-Caches sind über `.gitignore` ausgeschlossen.

## Schema 8 – inkrementeller Offline-Sync

`migrations/008_incremental_sync_mutations.sql` ergänzt:

- `current_version` für Strecken und Bahnhöfe
- `route_section_versions`
- `sync_mutation_receipts` für idempotente Uploads
- separaten neuen ACK-Cursor `sync_device_state`
- `changed_by_device` für Change-Log/Objektversionen
- Indizes für inkrementelle Änderungsabfragen

Die bereits im produktiven Schema 7 vorhandene Tabelle `device_sync_state` wird absichtlich **nicht** umgebaut. Ihr historischer vollständiger Tabellenaufbau ist nicht Bestandteil des gesicherten 0.6.0-Quellarchivs; der neue Cursor ist deshalb sauber getrennt.

Server-Endpunkte:

- `GET /api/v1/sync/catalog`
- `GET /api/v1/sync/routes/{route_id}/sections`
- `GET /api/v1/sync/stations/{station_id}/objects`
- `GET /api/v1/sync/objects/{object_id}`
- `GET /api/v1/sync/changes?after=<sequence>`
- `POST /api/v1/sync/ack`
- `POST /api/v1/sync/mutations`

Mobile Schreibzugriffe bleiben auf `SECTION` und `OBJECT` begrenzt. Basisversionen schützen mit HTTP 409 vor stillem Überschreiben; DELETE wird als Tombstone verteilt. Erfolgreiche Wiederholungen derselben `client_change_id` sind idempotent.

**Farbregel:** Jeder mobile SECTION-/OBJECT-UPSERT wird serverseitig zwingend auf `data_status=MANUAL` gesetzt. Ein manipulierter Client kann eine manuelle Änderung daher nicht als `VERIFIED_ORIGINAL`/grün ausgeben. Original-/Admin-Daten werden dadurch nicht umklassifiziert.

## Schema 9 – Baustellen-Zusammenarbeit

`migrations/009_worksite_collaboration.sql` bildet die verbindlichen Projektregeln ab:

- Teams ausschließlich innerhalb derselben Firma
- TB-Gruppen A/B/C/D/... mit verantwortlichem TB und Hilfsnutzern
- BTB strikt je TB-Gruppe getrennt
- Mängel/Abnahmen baustellenweit, bei Hilfsnutzern nur innerhalb des freigegebenen Bereichs
- Bilder/Abnahme-Anlagen als Attachments
- endgültige Mangel-/Abnahmestatus können nicht wieder geöffnet werden
- Benutzer-Speicheroptionen für Bilder, BTB, Mängel und Abnahmen
- temporäres Server-Spooling bei deaktivierter Daueraufbewahrung
- eigener Collab-Change-Log, ACK-Cursor und idempotente Mutation-Receipts

Der Change-Cursor schreitet auch über für einen Benutzer unsichtbare Änderungen fort. Dadurch kann z. B. TB A nicht an einem nur für TB B sichtbaren BTB-Eintrag hängen bleiben.

## Bahnhof-Autofill

Es wird keine neue Bahnhofsliste verwendet. Server-Admin und Android nutzen dieselbe Datenbasis:

- `data/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `data/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`

Die Suchlogik orientiert sich an `BahnhofSucheUtil.kt`. Der 0.7-Katalog liefert für Strecken **und** Bahnhöfe `current_version`.

## Vorbereitete Sicherheits-/Integritätsprüfungen

- `checks/00_preflight_schema7_for_008_009.sql` – read-only Prüfung des produktiven Schema-7-Stands **vor** Migration
- `checks/01_verify_schema9.sql` – read-only Nachkontrolle von Schema 8/9, Triggern und Integritätsverletzungen

Lokal geprüft wurden:

- Python-Syntax aller Runtime-Teile
- Import der FastAPI-App mit 0.7.0
- 79 registrierte Routen, keine doppelten Method/Path-Kombinationen
- alle benötigten Sync-/Collab-Endpunkte vorhanden
- 25 Jinja-Templates parsebar
- Bahnhofskatalog enthält `current_version`
- mobiler UPSERT kann `VERIFIED_ORIGINAL` nicht durchreichen, sondern wird auf `MANUAL` gesetzt

Diese Prüfungen ersetzen **nicht** den Test gegen eine echte PostgreSQL-Kopie des produktiven Schema 7.

## Sichere Reihenfolge für das NAS-Update

1. Aktuellen produktiven Server-/DB-Stand unverändert sichern.
2. Unmittelbar vor Migration ein geprüftes Datenbank-Backup erstellen.
3. `checks/00_preflight_schema7_for_008_009.sql` read-only ausführen. Bei irgendeinem `false` **abbrechen**.
4. Migration 008 mit `psql -v ON_ERROR_STOP=1` ausführen.
5. Migration 009 mit `psql -v ON_ERROR_STOP=1` ausführen.
6. `checks/01_verify_schema9.sql` ausführen; Pflichtobjekte müssen `ok=true`, alle `violations=0` liefern.
7. Erst danach das API-Image aus dem 0.7-Quellstand neu bauen/starten.
8. Geräte-Login, Katalog und Admin-Oberfläche prüfen.
9. Zwei-Geräte-Test für BTB-Gruppentrennung, Mängel/Abnahmen, Bilder, 409, Tombstones und unterbrochene Uploads durchführen.

Bis Schritt 1 ausdrücklich begonnen wird, bleibt der produktive NAS-Server unverändert auf 0.6.0 / Schema 7.
