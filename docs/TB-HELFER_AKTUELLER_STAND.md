# TB-Helfer – aktueller Projektstand

Stand: 2026-09-17

Diese Datei ist die technische Übergabe. Fachregeln stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, die Arbeitsweise in `docs/TB-HELFER_PROJEKTANWEISUNG.md`, das Serverziel in `docs/TB-SERVER_1_0_ZIELMODELL.md` und die Reset-/Cutover-Regeln in `docs/TB-SERVER_1_0_CUTOVER.md`.

## Start jedes Chats / jeder Änderung

Zuerst lesen/prüfen:
1. `docs/TB-HELFER_PROJEKTANWEISUNG.md`
2. `docs/TB-HELFER_PROJEKTREGELN.md`
3. diese Datei
4. `docs/TB-SERVER_1_0_ZIELMODELL.md`
5. aktiven Branch
6. offenen Draft-PR
7. neuesten relevanten Commit

GitHub-Code und aktiver Branch sind Source of Truth.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- Vorgänger-Branch: `feature/admin-offline-sync`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- Ausgangspunkt: `d7fbc677b33ad9e3b3780f44223c28913bf0a4a3`
- letzter Stand vor Foundation-Code: `64bb798e37f6a220fbd1715859a813a359a32eee`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
- letzter Benutzer-Test: Android-Studio-Build grün, nur Warnungen
- Produktivserver/NAS weiterhin unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Strategie

**Serverbasis zuerst, Android danach systematisch anbinden.**

Reihenfolge:
1. TB-Server 1.0 vollständig bauen;
2. Server/Portal/Sync-End-to-End testen;
3. vor NAS-Cutover vollständiges Backup erstellen und prüfen;
4. frischen TB-Server-1.0-Datenbestand produktiv aktivieren;
5. danach Android gegen den festen Vertrag anbinden;
6. Poco ↔ Pixel ↔ Portal praktisch testen.

Keine produktive NAS-Migration während der Entwicklungsblöcke.

## Testdaten / Reset

Der aktuelle Server-Fachdatenbestand ist Testbestand und darf beim kontrollierten 1.0-Cutover verworfen werden.

Nach Benutzerklärung vom 17.09.2026 existieren serverseitig noch **keine produktiven Vermessungs-/Streckendatensätze**, die übernommen werden müssen. Deshalb dürfen beim Reset auch serverseitige Test-/Leerbereiche für TRA/KF/GRA/KOR/KV/GVP und Testkataloge entfernt werden.

Die Unveränderlichkeitsregel gilt ab dem ersten echten produktiven Vermessungsimport:
- Originaldateien werden dann niemals durch mobile/Portal-Bearbeitung überschrieben;
- spätere Serverupdates erhalten produktive Originale;
- berechnete/manuelle Daten liegen getrennt als Arbeitsdaten.

Details: `docs/TB-SERVER_1_0_CUTOVER.md`.

## TB-Server 1.0 – Zielentscheidungen

- modularer Monolith, keine unnötigen Microservices;
- PostgreSQL/PostGIS + NAS-Dateispeicher;
- stabile `/api/v1`;
- Capabilities statt Schema-Abfragen im Client;
- unveränderliche nummerierte Migrationen mit SHA-256-Prüfsummen;
- Firma/Benutzer/CHEF/Portal-Login/Geräte getrennt modelliert;
- Baustellenbaum mit UUIDs;
- Rechte serverseitig kanonisch, aber auf Android und Portal bedienbar;
- Rechtevorlagen plus individuelle Abweichungen;
- mehrere TB-Teams je Baustelle;
- `active_btb_id` je TB-Team;
- BTB granular je Eintrag statt ein überschreibbares Gesamt-JSON;
- Client-Outbox + `client_change_id` + Change Feed + Live-Signal;
- große Dateien als Storage-Objekte/Chunks;
- Portal ist vollwertiger Fachclient, inklusive BTB-Abschluss und PC-Bildupload;
- CHEF sieht alle Baustellen der eigenen Firma;
- Admin und Benutzerportal bleiben getrennt;
- Backup umfasst DB + Dateispeicher und Restore-Test.

## Neu umgesetzt – TB-Server-1.0-Foundation

Erster technischer 1.0-Block im Branch:

### Neues Serverpaket

- `server/tbserver/__init__.py`
- `server/tbserver/config.py`
- `server/tbserver/database.py`
- `server/tbserver/migrations.py`
- `server/tbserver/capabilities.py`
- `server/tbserver/main.py`

Der alte 0.7.x-Server wird dabei **noch nicht ersetzt**. `server/Dockerfile.v1` ist ein separater Startpfad für die neue 1.0-Anwendung. Der produktive Docker-/NAS-Stand bleibt unangetastet.

### Migration Runner

Der neue Runner:
- entdeckt nur `server/v1_migrations/*.sql`;
- verlangt eindeutig nummerierte Dateinamen;
- speichert Name + SHA-256 in `tb.schema_migrations`;
- verweigert veränderte bereits ausgeführte Migrationen;
- verwendet einen PostgreSQL-Advisory-Lock gegen parallele Migrationen;
- besitzt `up` und `status`.

### Frische 1.0-Baseline

`server/v1_migrations/001_server_1_0_base.sql` bildet die frische Zielbasis ab und enthält bereits Tabellen für:
- Firmen;
- Benutzer + Portal-Credentials/Sessions;
- Geräte + Aktivierungstokens;
- Rechtevorlagen;
- Baustellen + Baum;
- Baustellenmitgliedschaften/Einladungen/Rechte;
- Teams + `active_btb_id`;
- BTBs + granulare BTB-Einträge + Original-Snapshot;
- Dateien + fachliche Links + Chunk-Upload-Sessions;
- Mängel;
- Abnahmen;
- Vermessungsoriginal-Metadaten;
- versionierte Strecken-Arbeitsdaten;
- Idempotenz-Receipts;
- Change Feed + Sync-Cursor;
- Lösch-Tombstones;
- Audit;
- Releases;
- Hintergrundjobs;
- Backup-Läufe;
- Stundenmetriken für Admin-Traffic/Fehler;
- Servereinstellungen.

### API-Foundation

Vorhanden:
- `GET /health/live`
- `GET /health/ready`
- `GET /api/v1/capabilities`

Capabilities werden zunächst nur für tatsächlich gebaute Features auf `true` gesetzt. Noch nicht implementierte Fachmodule bleiben `false`; Android soll später nach Fähigkeiten statt nach DB-Schemanummern entscheiden.

### Source-Check

`server/checks/20_verify_tb_server_1_foundation.py` prüft:
- Vorhandensein der Foundation-Dateien;
- Python-Syntax über AST;
- zentrale Baseline-Tabellen;
- Prüfsummen-/Lock-Regeln des Migration Runners;
- Health-/Capability-Pfade.

## Tests / aktueller Nachweis

- Android 200050: letzter Nutzer-Build grün, nur Warnungen.
- Foundation-Pythonmodule wurden vor Commit syntaktisch geprüft.
- Baseline-SQL wurde strukturell auf zentrale Tabellen/Beziehungen geprüft.
- `20_verify_tb_server_1_foundation.py` wurde lokal gegen den vorbereiteten Foundation-Dateisatz ausgeführt: **OK**.
- **Noch nicht erfolgt:** echte PostgreSQL-Initialisierung der 1.0-Baseline.
- **Noch nicht erfolgt:** Start von `Dockerfile.v1`.
- **Noch nicht erfolgt:** Integrationstests gegen eine reale frische PostGIS-DB.
- Produktivserver wurde nicht verändert.

## Bekannte/offene Punkte

Foundation ist noch kein fertiger Server. Als nächste Module fehlen insbesondere:
- Auth/Portal-Aktivierung/Passwort/Sessions;
- Firmen-/Benutzer-/CHEF-API;
- Geräteaktivierung v1;
- Baustellen-/Rechte-/Team-API;
- granularer BTB-Service;
- Change-Feed-Mutationsendpunkte und Live-Events;
- Datei-/Chunkupload;
- Mängel/Abnahmen;
- Strecken-/Vermessungsimport;
- Portal-UI;
- Admin 1.0;
- Backup/Restore und Releases.

## Nächster sinnvoller Arbeitsschritt

1. 1.0-Baseline gegen eine frische PostgreSQL/PostGIS-Testdatenbank ausführen;
2. Migration Runner `up/status` und `/health/ready` praktisch testen;
3. danach Auth + Firmen + Benutzer + CHEF + Portal-Login als ersten Fachblock bauen;
4. anschließend Baustellen/Rechte/Teams;
5. danach granularen BTB + Sync-Kern.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
