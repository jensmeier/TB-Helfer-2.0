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
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- Cutover-Regel-Commit: `64bb798e37f6a220fbd1715859a813a359a32eee`
- zuletzt vor diesem Datei-/Upload-Block geprüfter Branch-Head: `7680d68e3593f685a72ccfc9a11e2f3fbedf1044`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
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

Nicht gelöscht werden GitHub-Code, Konfiguration/Secrets und Backups. Statische Grunddaten werden aus der definierten Quelle neu aufgebaut.

Ab dem ersten echten produktiven Vermessungsimport gilt dauerhaft:
- Originaldateien bleiben unverändert;
- spätere Serverupdates erhalten produktive Originale;
- berechnete/manuelle Daten liegen getrennt als Arbeitsdaten.

Details: `docs/TB-SERVER_1_0_CUTOVER.md`.

## TB-Server 1.0 – bisher umgesetzt

### 1. Foundation / Migrationen

Vorhanden:
- neues Paket `server/tbserver/`;
- separater Startpfad `server/Dockerfile.v1`;
- frische Baseline `server/v1_migrations/001_server_1_0_base.sql`;
- Migration Runner mit nummerierten Migrationen, SHA-256-Prüfung und PostgreSQL-Advisory-Lock;
- `/health/live`, `/health/ready`, `/api/v1/capabilities`;
- GitHub-Workflow `.github/workflows/tb-server-v1.yml` mit PostGIS-Service.

### 2. Auth / Firmen / Benutzer / Portal-Grundlage

Vorhanden sind Servermodule für:
- Security/Passworthash;
- Portal-Aktivierung;
- Portal-Login/Sessions;
- CSRF-Prüfung;
- Admin-Grundfunktionen;
- Bootstrap des ersten Administrators;
- Firmen-/Benutzer-Grundverwaltung.

Relevante Dateien:
- `server/tbserver/security.py`
- `server/tbserver/portal.py`
- `server/tbserver/admin.py`
- `server/tbserver/bootstrap.py`

### 3. Baustellen / Rechte / Teams

Vorhanden:
- Baustellen mit stabilen UUIDs;
- Unterbaustellen-/Baumstruktur;
- Mitgliedschaften;
- Rechteprofile + individuelle Abweichungen;
- Teams mit `active_btb_id`;
- serverseitige Rechteprüfung;
- Change-/Audit-Ereignisse für zentrale Mutationen.

Relevante Dateien:
- `server/tbserver/worksites.py`
- `server/tbserver/permissions.py`
- `server/tbserver/events.py`

Capabilities dafür stehen auf `true`:
- `worksite_tree_v1`
- `worksite_permissions_v1`
- `worksite_teams_v1`

### 4. Granularer BTB / Live-Sync

Vorhanden:
- BTB-Kopf mit stabiler `btbId`;
- einzelne `btb_entries` mit eigener UUID/Version;
- Versionskonfliktprüfung statt stillem Last-Write-Wins;
- aktiver BTB je Team;
- erster unveränderlicher Original-Snapshot beim Abschluss;
- Abschlussstatus, Reopen/Nachbearbeitung, Tombstone-Löschen;
- Change Feed;
- `/api/v1/sync/changes`;
- SSE-Live-Signal `/api/v1/sync/events`.

Relevante Dateien:
- `server/tbserver/btb.py`
- `server/tbserver/sync.py`

Capabilities:
- `btb_entries_v1=true`
- `btb_live_sync_v1=true`
- `btb_finalize_with_pdf_v1=false` – PDF-Erzeugung ist bewusst noch nicht fertig.

### 5. Dateien / Bilder / Chunkupload – aktueller Block

Neu ergänzt:
- `server/tbserver/files.py`;
- resumierbare, sequenzielle Upload-Sessions;
- Standard-Chunkgröße 8 MiB, maximal 16 MiB;
- Offset-Prüfung gegen `received_bytes`;
- Größenprüfung und optionale erwartete SHA-256-Prüfung;
- fsync beim Chunk-Schreiben;
- Abschluss verschiebt die Datei atomar aus dem temporären Bereich in UUID-basierte Storage-Pfade;
- Dateimetadaten in `tb.files`;
- fachliche Verknüpfungen über `tb.file_links` zu Baustelle/BTB/Mangel/Abnahme;
- Datei-Metadatenliste und geschützter Dateiabruf;
- Abbruch offener Upload-Sessions;
- Change-Feed- und Audit-Eintrag bei erfolgreichem Abschluss;
- neuer Source-Check `server/checks/23_verify_tb_server_1_files_source.py`.

Capabilities:
- `files_v1=true`
- `chunk_upload_v1=true`
- `company_logo_v1=false` – Firmenlogo-Workflow folgt separat.

## CI / Tests

### Bisherige Nachweise

- Foundation-Pythonmodule syntaktisch geprüft.
- Baseline-SQL strukturell geprüft.
- Migration Runner wurde im Server-Workflow gegen PostGIS vorgesehen und bereits in vorherigen Läufen ausgeführt.
- Auth-Source-Check vorhanden: `21_verify_tb_server_1_auth_source.py`.
- Auth-Runtime-Check vorhanden: `22_verify_tb_server_1_auth_runtime.py`.

### Gefundener CI-Fehler am Branch-Head `7680d68...`

Der TB-Server-Workflow war rot, weil der Capability-Test noch den alten Zustand `worksite_tree_v1 == false` verlangte, obwohl Baustellenbaum/Rechte/Teams inzwischen implementiert und aktiviert waren.

Im aktuellen Block wird dieser veraltete CI-Vertrag korrigiert. Der Workflow prüft jetzt die tatsächlich aktivierten Capabilities und zusätzlich Tabellen/Source-Vertrag für Dateien und Chunkupload.

Der neue GitHub-Actions-Lauf muss nach dem Commit erneut geprüft werden.

### Android CI

Android ist für diesen Serverblock nicht der Freigabenachweis. Der produktive Android-Stand wird erst nach festem Serververtrag systematisch angepasst. Ein roter Android-Workflow auf reinen Server-Commits ist separat zu bewerten und darf nicht mit bestandenem Server-1.0-Test verwechselt werden.

## Bekannte/offene Punkte

Noch nicht fertig:
- echtes BTB-Abschluss-PDF inklusive unveränderlichem Original-/Nachbearbeitungsmodell auf Dateiebene;
- Mängel-/Abnahme-API;
- mobile Streckendaten;
- Vermessungsoriginal-Import/-Katalog;
- Firmenlogo/PDF-Layout;
- vollständige Portal-Fachoberfläche;
- Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität;
- Background Jobs;
- Backup/Restore-Werkzeuge;
- Testdaten-Reset-Werkzeug;
- vollständige Integrations-/End-to-End-Tests;
- produktiver NAS-Cutover.

Datei-/Upload-Modul ist zunächst auf baustellenbezogene Uploads mit Portal-Session-Authentifizierung begrenzt. Geräteauthentifizierung für Android und systemweite Dateien wie Firmenlogo/Releases werden in den dafür vorgesehenen Modulen ergänzt.

## Nächster sinnvoller Arbeitsschritt

1. aktuellen TB-Server-GitHub-Workflow grün bekommen und Ergebnis prüfen;
2. danach **Mängel + Abnahmen** als nächsten Fachblock auf dieselbe UUID/Version/Change-Feed-Struktur setzen;
3. anschließend mobile Streckendaten + unveränderliche Vermessungsoriginale;
4. danach Portal-Fachoberfläche und BTB-PDF-Abschluss vollständig schließen;
5. Backup/Restore/Reset und vollständige E2E-Tests;
6. erst danach NAS-Cutover und Android-Anpassung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
