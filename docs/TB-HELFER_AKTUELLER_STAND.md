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
- produktive API: **0.6.0**
- produktives DB-Schema: **7** (`007_admin_crud_sync_delete`)
- Migrationen `008` und `009` wurden produktiv **NICHT** ausgeführt.
- Der 0.7-Quellstand ist noch **nicht** auf das NAS ausgerollt.
- Keine produktive Migration ohne unmittelbar vorher erzeugtes und geprüftes Backup.

## Server-0.7-Quellstand – Import abgeschlossen
Der frühere Blocker „produktiver Serverquellcode fehlt in GitHub“ ist erledigt.

Im Feature-Branch liegen jetzt:
- echter FastAPI-/psycopg-Runtime auf Basis des produktiven 0.6.0-Servers
- `server/app.py` als Loader
- `server/app_parts/01_part.py` ff. als verlustfrei zerlegte Runtime
- produktives Dockerfile und Python-Abhängigkeiten
- 25 Admin-Jinja-Templates
- vorhandene Bahnhofsdaten unter `server/data/`
- Migrationen 008/009
- read-only Preflight/Nachkontrolle
- verbindlicher Android-200018/Server-0.7-API-Vertrag

Der Runtime wurde nur für die GitHub-Übernahme in nummerierte Teile zerlegt; alle Teile laufen in derselben Reihenfolge und im selben Modul-Namespace. Der Server wurde nicht als fremder Unterbau neu erfunden.

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
- eigener neuer ACK-Cursor `tb.sync_device_state`; vorhandenes Schema-7-`tb.device_sync_state` wird nicht umgebaut
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
- serverseitige Attachment-SHA-Deduplizierung
- Benutzer-Aufbewahrungsoptionen und temporäres Spooling

### Admin
- bestehende 0.6-Admin-Funktionen erhalten
- Firmen-/Benutzer-/Geräte-/Strecken-/Bahnhofs-/Baustellenverwaltung erhalten
- TB-Gruppen und Hilfsnutzer-Bereichszuordnung ergänzt
- Teams nur innerhalb derselben Firma
- Server-Speicheroptionen pro Benutzer: Bilder, BTB, Mängel, Abnahmen; „Alles“ wird über alle Einzeloptionen abgebildet
- Bahnhof-Autofill ausschließlich aus den zwei vorhandenen TB-Helfer-Datenbeständen, keine neue Bahnhofsliste

## Letzte wichtige Commits
- `2cd5f933a9b06b5482fe7b53b7021e3b56d7f89a`: manueller Android-Baustellen-Sync lädt Mängelbilder und Abnahme-Anlagen sofort mit hoch.
- Runtime-/Template-Import danach vollständig in PR #5 übernommen.
- `ecf5d5ba096494006e52819d6f3dc0d3ce410bb0`: Bahnhofskatalog liefert `current_version` zuverlässig.
- `c1092e483bce85b1c652209f9cf7790d5bbb78cc`: Migration 008 finalisiert, neuer separater `sync_device_state`-Cursor und `changed_by_device`.
- `ff8006285ed798421f6a820c80baba9e58967361`: Schema-7-Preflight an echten 0.6.0-Quellstand angepasst.
- `1091fdeaa2dc003b20cd54d79de2eaf338da950b`: Schema-9-Nachkontrolle erweitert.
- `aea3eb92fc015693472f8e91c8f12c16a92c8cec`: mobile Fachänderungen werden serverseitig zwingend `MANUAL`/orange.
- `52dbc8f1fc5065ebf66d1faac1966ed93b74dffb`: Python-`__pycache__`/`.pyc` zusätzlich aus Git ausgeschlossen.
- `9bd966b48c83b634ac8316a33987c811ab2440c7`: Server-README auf den tatsächlichen 0.7-Stand aktualisiert.

## Lokale technische Prüfung des Server-0.7-Stands
Am 2026-09-09 erfolgreich geprüft:
- Python `py_compile` für Loader und alle Runtime-Teile
- FastAPI-App importiert als Version `0.7.0`
- **79 Routen**, keine doppelten Method/Path-Kombinationen
- alle erforderlichen allgemeinen Sync- und Collab-Endpunkte registriert
- **25 Jinja-Templates** syntaktisch parsebar
- Katalogtest bestätigt `current_version` für Bahnhöfe
- Mutationstest bestätigt: Clientwert `VERIFIED_ORIGINAL` wird bei mobilem UPSERT vor der Mutation auf `MANUAL` überschrieben
- DELETE-Pfad bleibt davon unberührt

Hinweis: Im lokalen Testsystem fehlt nur der echte PostgreSQL-Treiber/Produktiv-DB-Zugriff; der Importtest wurde deshalb mit einem Stub für `psycopg` ausgeführt. Die eigentliche Schema-Kompatibilität wird vor Produktivmigration read-only über den Preflight auf dem NAS geprüft.

## Android-Stand
- Android `versionCode 200018` liegt im Draft-Branch.
- 200018 wurde vor den letzten Härtungen lokal erfolgreich kompiliert, als Update installiert und gegen produktive API 0.6.0 auf Grundfunktionen geprüft.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Room-Migrationspfad 1 -> 2 -> 3 vorhanden.
- Offline-Katalog, selektiver Download, Upload-Queue, Basisversion, 409, Tombstones und Hintergrund-Sync vorbereitet.
- `UPLOADING`-Einträge werden nach App-/Netzabbruch erneut versucht; `client_change_id` schützt vor Doppelanwendung.
- BTB-Import erhält gerätelokale `fotos`-/`pdfAuswahl`-Referenzen und erzeugt keinen Medien-Sync-Echo.
- Mängelfoto-KM versteht zusätzlich `70+698`.
- Hilfsnutzer-Scope wird niemals erweitert; Haupt-TB-Scope wird für einzelne Mängel/Abnahmen soweit möglich aus Gleis/Weiche/KM eingegrenzt.
- manueller und Hintergrund-Sync übertragen auch Attachments.
- Benachrichtigungen pro Gerät: `Text + Ton` / `nur Ton` / `aus`.

Die nach dem letzten lokalen APK-Build hinzugekommenen Android-Härtungen müssen vor dem abschließenden Zwei-Geräte-Ende-zu-Ende-Test erneut lokal gebaut/installiert werden. Dieser Test ist sinnvoll erst nach Bereitstellung des 0.7-Servers möglich.

## Verbindliches Team-/Rechtemodell
- Baustellen-Teams ausschließlich innerhalb derselben Firma.
- Eine Baustelle kann TB A/B/C/D/... haben.
- Jeder TB kann Hilfsnutzer haben.
- BTB A bleibt bei A + seinen Hilfsnutzern; BTB B bei B + seinen Hilfsnutzern usw.
- Mängel/Abnahmen werden baustellenweit zwischen A/B/C/D synchronisiert, Hilfsnutzer nur im berechtigten Bereich.
- Bilder werden mitsynchronisiert.
- endgültige Status bleiben endgültig.

## Migrationen / Prüfskripte
- `server/migrations/008_incremental_sync_mutations.sql`
- `server/migrations/009_worksite_collaboration.sql`
- `server/checks/00_preflight_schema7_for_008_009.sql` – read-only vor Migration
- `server/checks/01_verify_schema9.sql` – read-only nach Migration

Preflight prüft unter anderem die aus dem echten 0.6.0-Runtime erwarteten Tabellen/Spalten, PostGIS und Hilfsfunktionen. Bei irgendeinem Pflichtwert `ok=false` darf die Migration nicht gestartet werden.

Nachkontrolle erwartet Schema 8/9, alle Pflichtobjekte/Trigger und `violations=0`.

## Nächster sicherer Schritt – kontrolliertes NAS-Update
Der Codeblocker ist erledigt. Der nächste Schritt ist nicht mehr Quellcode-Import, sondern die kontrollierte Serveraktualisierung:

1. produktiven Zustand sichern und unmittelbar vorher DB-Backup erzeugen/prüfen,
2. read-only Preflight auf Schema 7 ausführen,
3. nur bei vollständig grünem Preflight Migration 008,
4. Migration 009,
5. read-only Nachkontrolle (`ok=true`, `violations=0`),
6. 0.7-API-Image bauen/starten,
7. Login/Katalog/Admin-Smoke-Test,
8. Android 200018 erneut bauen/installieren,
9. echter Zwei-Geräte-Test: BTB A/B-Trennung, Mängel/Abnahmen, Bilder, 409, Tombstones, Netzabbruch/Wiederaufnahme,
10. PR #5 bis zum erfolgreichen Ende-zu-Ende-Test als Draft lassen; `main` nicht vorher ändern.

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
