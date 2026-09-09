# TB-Helfer – aktueller Projektstand

Stand: 2026-09-09

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Sie ergänzt `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. den aktuell offenen Draft-PR und dessen neuesten Commit

Nach jeder wesentlichen Änderung/Entscheidung ist diese Datei im aktiven Feature-Branch zu aktualisieren. `main` bleibt stabil.

## Repository / aktiver Entwicklungsstand
- Repository: `jensmeier/TB-Helfer-2.0`
- stabiler Branch: `main`
- aktiver Feature-Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- tatsächlicher Server-0.6.0-Quellstand wurde am 2026-09-09 aus der vom NAS gesicherten `server.zip` übernommen und auf dieser Basis zu 0.7.0 erweitert.
- `main` wurde durch diese Arbeiten nicht verändert.

## Produktiver Stand auf der Synology
- NAS/Synology bleibt Source of Truth.
- produktive API läuft weiterhin als **0.6.0**.
- produktive PostgreSQL-Datenbank wurde am 2026-09-09 kontrolliert von Schema 7 auf **Schema 9** migriert.
- Schema-Versionen 8 und 9 sind eingetragen:
  - `008_incremental_sync_mutations`
  - `009_worksite_collaboration`
- Server/API 0.7.0 ist **noch nicht** produktiv gestartet.
- `main` ist weiterhin unverändert/stabil.

### Produktives Backup vor Migration 008/009
Unmittelbar vor der Migration wurde ein PostgreSQL-Custom-Dump erzeugt und mit `pg_restore -l` erfolgreich geprüft:
- Datei: `tb_helfer_pre_0.7_20260909_201605.dump`
- ursprüngliche Kopie im persistenten PostgreSQL-Volume
- zweite Kopie außerhalb des Datenbankordners unter `/volume1/docker/tb-helfer/backup/`
- Prüfung: `BACKUP_PRUEFUNG=OK`

### Preflight vor Migration
Der aktuelle read-only Preflight aus dem Feature-Branch wurde auf dem echten produktiven Schema 7 ausgeführt.
Ergebnis vollständig grün:
- Schema 7 vorhanden
- alle 12 Pflichtobjekte vorhanden
- beide Pflichtfunktionen vorhanden
- alle 34 Pflichtspalten vorhanden
- PostGIS-Geometrietyp vorhanden
- kein `false`

### Migration 008
`008_incremental_sync_mutations.sql` wurde mit `psql -v ON_ERROR_STOP=1` produktiv ausgeführt.
Ergebnis:
- kein `ERROR`
- Schema 8 eingetragen
- `tb.sync_device_state`, `tb.route_section_versions`, `tb.sync_mutation_receipts` vorhanden
- `routes.current_version` und `stations.current_version` vorhanden

Die NAS-Datei unter `/volume1/docker/tb-helfer/scripts/008_incremental_sync_mutations.sql` wurde bytegenau gegen GitHub geprüft.
Git-Blob-SHA: `062d827ade9f5f9578b816ee5ae86bcdc627dc66`.

### Migration 009
`009_worksite_collaboration.sql` wurde mit `psql -v ON_ERROR_STOP=1 -f /tb/scripts/009_worksite_collaboration.sql` produktiv ausgeführt.
Ergebnis:
- kein `ERROR`
- `INSERT 0 1`
- `COMMIT`
- Schema 9 eingetragen

Die NAS-Datei wurde vor Ausführung bytegenau gegen GitHub geprüft.
Git-Blob-SHA: `1e33aa95af8e20f6368f351f3687c174c0e9fd46`.

### Read-only Nachkontrolle nach Schema 9
`server/checks/01_verify_schema9.sql` wurde auf der produktiven DB ausgeführt.
Ergebnis vollständig sauber:
- Schema 8 und 9 vorhanden
- alle **14 Pflichtobjekte**: `ok=t`
- alle **12 Pflichtspalten**: `ok=t`
- alle **12 Pflichttrigger**: `ok=t`
- alle **13 Integritätsprüfungen**: `violations=0`
- Attachment-Parent-Prüfung: `violations=0`
- Abschluss: `COMMIT`

Damit ist die produktive Datenbank fachlich und strukturell auf **Schema 9** bereit für den Server 0.7.0.

## Server-0.7-Quellstand
Im Feature-Branch liegen:
- echter FastAPI-/psycopg-Runtime auf Basis des produktiven 0.6.0-Servers
- `server/app.py` als Loader
- `server/app_parts/01_part.py` ff. als verlustfrei zerlegte Runtime im selben Modul-Namespace
- Dockerfile und Python-Abhängigkeiten
- 25 Admin-Jinja-Templates
- vorhandene Bahnhofsdaten unter `server/data/`
- Migrationen 008/009
- read-only Preflight/Nachkontrolle
- verbindlicher Android-200018/Server-0.7-API-Vertrag

Der Server wurde nicht als fremder Unterbau neu erfunden.

## Server-0.7 – implementierte API
### Allgemeiner Offline-Sync
- `GET /api/v1/sync/catalog`
- selektive Strecken-/Bahnhofsdetails
- `GET /api/v1/sync/changes`
- `POST /api/v1/sync/ack`
- `POST /api/v1/sync/mutations`
- Basisversion/HTTP 409
- idempotente Mutation-Receipts über `client_change_id`
- Tombstones aus dem Change-Log
- mobile Schreibrechte nur für SECTION/OBJECT
- eigener ACK-Cursor `tb.sync_device_state`; vorhandenes `tb.device_sync_state` bleibt unangetastet
- Strecken und Bahnhöfe liefern `current_version`
- mobiler SECTION-/OBJECT-UPSERT erzwingt serverseitig `data_status=MANUAL`; manuelle Änderungen können nicht als `VERIFIED_ORIGINAL`/grün hochgeladen werden

### Baustellen-Zusammenarbeit
- `GET /api/v1/collab/context`
- `GET /api/v1/collab/changes`
- `POST /api/v1/collab/ack`
- `POST /api/v1/collab/mutations`
- Attachment Upload/Download
- Firmenbindung auf Server- und DB-Ebene
- TB-Gruppen A/B/C/D/...
- BTB strikt gruppenbezogen
- Mängel/Abnahmen baustellenweit im berechtigten Bereich
- Bilder/Abnahme-Anlagen als Attachments
- endgültige Status gegen Wiederöffnung geschützt
- Cursor schreitet auch über unsichtbare Änderungen fort, damit TB A nicht an BTB B hängen bleibt
- idempotente Collab-Mutationen und Schutz gegen parallele Doppelanlagen
- Attachment-SHA-Deduplizierung
- Benutzer-Aufbewahrungsoptionen und temporäres Spooling

### Admin
- bestehende 0.6-Admin-Funktionen erhalten
- Firmen-/Benutzer-/Geräte-/Strecken-/Bahnhofs-/Baustellenverwaltung erhalten
- TB-Gruppen und Hilfsnutzer-Bereichszuordnung ergänzt
- Teams nur innerhalb derselben Firma
- Server-Speicheroptionen pro Benutzer: Bilder, BTB, Mängel, Abnahmen; „Alles“ wird über alle Einzeloptionen abgebildet
- Bahnhof-Autofill ausschließlich aus den zwei vorhandenen TB-Helfer-Datenbeständen, keine neue Bahnhofsliste

## Technische Prüfung des Server-0.7-Stands
Am 2026-09-09 erfolgreich geprüft:
- Python `py_compile` für Loader und alle Runtime-Teile
- FastAPI-App importiert als Version `0.7.0`
- **79 Routen**, keine doppelten Method/Path-Kombinationen
- alle erforderlichen allgemeinen Sync- und Collab-Endpunkte registriert
- **25 Jinja-Templates** syntaktisch parsebar
- Katalogtest bestätigt `current_version` für Bahnhöfe
- Mutationstest bestätigt: Clientwert `VERIFIED_ORIGINAL` wird bei mobilem UPSERT auf `MANUAL` überschrieben
- DELETE-Pfad bleibt davon unberührt

## Android-Stand
- Android `versionCode 200018` liegt im Draft-Branch.
- 200018 wurde vor den letzten Härtungen lokal erfolgreich kompiliert, als Update installiert und gegen API 0.6.0 auf Grundfunktionen geprüft.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Room-Migrationspfad 1 -> 2 -> 3 vorhanden.
- Offline-Katalog, selektiver Download, Upload-Queue, Basisversion, 409, Tombstones und Hintergrund-Sync vorbereitet.
- `UPLOADING`-Einträge werden nach App-/Netzabbruch erneut versucht; `client_change_id` schützt vor Doppelanwendung.
- BTB-Import erhält gerätelokale `fotos`-/`pdfAuswahl`-Referenzen und erzeugt keinen Medien-Sync-Echo.
- Mängelfoto-KM versteht zusätzlich `70+698`.
- Hilfsnutzer-Scope wird niemals erweitert; Haupt-TB-Scope wird für einzelne Mängel/Abnahmen soweit möglich aus Gleis/Weiche/KM eingegrenzt.
- manueller und Hintergrund-Sync übertragen Attachments.
- Benachrichtigungen pro Gerät: `Text + Ton` / `nur Ton` / `aus`.

Die nach dem letzten lokalen APK-Build hinzugekommenen Android-Härtungen müssen vor dem Zwei-Geräte-Ende-zu-Ende-Test erneut lokal gebaut/installiert werden. Dieser Test folgt erst nach erfolgreicher Bereitstellung des 0.7-Servers.

## Verbindliches Team-/Rechtemodell
- Baustellen-Teams ausschließlich innerhalb derselben Firma.
- Eine Baustelle kann TB A/B/C/D/... haben.
- Jeder TB kann Hilfsnutzer haben.
- BTB A bleibt bei A + seinen Hilfsnutzern; BTB B bei B + seinen Hilfsnutzern usw.
- Mängel/Abnahmen werden baustellenweit zwischen A/B/C/D synchronisiert, Hilfsnutzer nur im berechtigten Bereich.
- Bilder werden mitsynchronisiert.
- endgültige Status bleiben endgültig.

## Nächster sicherer Schritt – API 0.7 auf dem NAS
Die DB-Migration ist abgeschlossen und geprüft. Als Nächstes wird **nicht geraten**, wie der Container neu gebaut wird.

Zuerst read-only ermitteln:
1. aktuell produktive Compose-/Container-Konfiguration des API-Containers
2. tatsächliche Bind-Mounts/Volumes für Servercode, Daten und Storage
3. aktuell verwendeter Build-Kontext/Dockerfile und Image-Name
4. bestehende `.env`/Secrets nur als Pfade/Variablennamen prüfen, niemals Inhalte ausgeben oder nach GitHub kopieren
5. produktiven 0.6.0-Serverordner unmittelbar vor Austausch zusätzlich sichern

Danach:
6. 0.7-Quellstand aus dem Feature-Branch/gesicherten Paket in den bestehenden NAS-Serverpfad übernehmen, ohne Secrets zu überschreiben
7. API-Image kontrolliert neu bauen/starten
8. Version/Login/Katalog/Admin smoke-testen
9. Cloudflare/API-Erreichbarkeit prüfen
10. Android 200018 mit letzten Härtungen neu bauen/installieren
11. echter Zwei-Geräte-Test: BTB A/B-Trennung, Mängel/Abnahmen, Bilder, 409, Tombstones, Netzabbruch/Wiederaufnahme
12. PR #5 bis zum erfolgreichen Ende-zu-Ende-Test Draft lassen; `main` vorher nicht ändern

## Bahnhofsdaten – nicht neu erfinden
Vorhandene Datenbasis:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`

## Vermessungs-/Kilometrierungsfarben
- Grün = belastbare Originaldaten/KM
- Orange = manuell geändert/hergestellt
- Rot = TRA vorhanden, aber keine belastbare KM
- Grau = ohne Vermessungsgeometrie
- Blau = Auswahl/temporäre Markierung

## Nicht erneut entscheiden / nicht zurückbauen
- NAS ist Source of Truth.
- Originale Vermessungsdaten bleiben unverändert.
- nur benötigte Daten offline aufs Handy.
- Cloudflare normale App-Synchronisation; Tailscale Wartung.
- keine firmenübergreifenden Teams.
- BTB bleibt TB-gruppenbezogen.
- Mängel/Abnahmen baustellenweit im berechtigten Bereich.
- Server-Speicheroptionen bleiben vorgesehen.
- temporäres Server-Spooling bleibt erlaubt.
- `main` stabil halten; große Änderungen über Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus alten Chat-Zusammenfassungen allein weiterarbeiten. Zuerst Projektregeln, diese Datei, Draft-PR und neuesten Commit in GitHub prüfen. Bei Widersprüchen gilt der produktive NAS-Stand für tatsächlich laufenden Server/DB-Stand, die Projektregeln für fachliche Entscheidungen und der Feature-Branch für noch nicht produktive Entwicklung.
