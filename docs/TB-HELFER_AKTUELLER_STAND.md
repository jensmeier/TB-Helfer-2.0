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
- Datei-/Chunkupload-Commit: `959a53459455062def10608df4ca4c4cd1b0a179`
- Mängel-/Abnahmen-Commit: `40249785879b82714b7d2df822e9e52fb7fb750c`
- Vermessungs-/Track-Commit: `a84c3607512a61145eba85309aa80bc2d6f015f0`
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

## TB-Server 1.0 – umgesetzt

### 1. Foundation / Migrationen

Vorhanden:
- `server/tbserver/` als neue 1.0-Anwendung;
- `server/Dockerfile.v1` als separater Startpfad;
- `server/v1_migrations/001_server_1_0_base.sql` als frische Baseline;
- Migration Runner mit Nummerierung, SHA-256-Prüfung und PostgreSQL-Advisory-Lock;
- `/health/live`, `/health/ready`, `/api/v1/capabilities`;
- `.github/workflows/tb-server-v1.yml` mit PostGIS-Testservice.

### 2. Auth / Firmen / Benutzer / Portal-Grundlage

Vorhanden:
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
- Baustellen und Unterbaustellen mit stabilen UUIDs;
- Mitgliedschaften;
- Rechteprofile + individuelle Abweichungen;
- Teams mit `active_btb_id`;
- serverseitige Rechteprüfung;
- Change-/Audit-Ereignisse.

Capabilities:
- `worksite_tree_v1=true`
- `worksite_permissions_v1=true`
- `worksite_teams_v1=true`

### 4. Granularer BTB / Live-Sync

Vorhanden:
- stabiler BTB-Kopf und einzelne `btb_entries`;
- UUID + Version je Eintrag;
- Versionskonfliktprüfung statt stillem Last-Write-Wins;
- aktiver BTB je Team;
- erster unveränderlicher Original-Snapshot beim Abschluss;
- Abschlussstatus, Reopen/Nachbearbeitung, Tombstone-Löschen;
- Change Feed `/api/v1/sync/changes`;
- SSE-Live-Signal `/api/v1/sync/events`.

Capabilities:
- `btb_entries_v1=true`
- `btb_live_sync_v1=true`
- `btb_finalize_with_pdf_v1=false` – Abschluss-PDF noch offen.

### 5. Dateien / Bilder / Chunkupload

Commit: `959a53459455062def10608df4ca4c4cd1b0a179`

Vorhanden:
- `server/tbserver/files.py`;
- resumierbare sequenzielle Upload-Sessions;
- Standard-Chunkgröße 8 MiB, Maximum 16 MiB;
- Offset-, Größen- und SHA-256-Prüfung;
- fsync beim Chunk-Schreiben;
- atomarer Abschluss aus Tempbereich in UUID-basierte Storage-Pfade;
- `tb.files` + `tb.file_links`;
- Links zu Baustelle/BTB/Mangel/Abnahme;
- Datei-Metadatenliste und geschützter Dateiabruf;
- Upload-Abbruch;
- Change-Feed + Audit beim erfolgreichen Abschluss.

Capabilities:
- `files_v1=true`
- `chunk_upload_v1=true`
- `company_logo_v1=false`.

### 6. Mängel / Abnahmen

Commit: `40249785879b82714b7d2df822e9e52fb7fb750c`

Vorhanden:
- `server/tbserver/defects.py`;
- `server/tbserver/acceptances.py`;
- Mangel anlegen/listen/lesen/bearbeiten/schließen;
- Abnahme anlegen/listen/lesen/bearbeiten/finalisieren;
- optionale clientseitige UUID beim Anlegen;
- Zuordnung zu Baustellenknoten wird serverseitig validiert;
- Schreibrechte über `defect.*` bzw. `acceptance.*`;
- Optimistic Locking über `base_version`;
- `409 VERSION_CONFLICT` statt stillem Überschreiben;
- Change-Feed und Audit bei Lebenszyklusaktionen.

Capabilities:
- `defects_v1=true`
- `acceptances_v1=true`.

### 7. Vermessungsoriginale / mobile Strecken-Arbeitsdaten

Commit: `a84c3607512a61145eba85309aa80bc2d6f015f0`

Vorhanden:
- `server/tbserver/survey_originals.py`;
- `server/tbserver/track_data.py`;
- Import eines bereits hochgeladenen baustellenbezogenen Files als Vermessungsoriginal;
- vor Import Prüfung von Dateigröße und SHA-256 gegen den gespeicherten File-Datensatz;
- Originalbytes werden nur atomar in `reference/survey-originals/<uuid>/...` verschoben, nicht verändert;
- Originalmetadaten liegen in `tb.survey_originals`;
- für Vermessungsoriginale existieren bewusst **keine** PATCH-/PUT-/DELETE-Endpunkte;
- doppelter Import identischer Originalbytes derselben Firma/Datenart wird erkannt;
- Download des unveränderten Originals bleibt möglich;
- mobile/bearbeitete Daten liegen ausschließlich in `tb.track_data`;
- Track-Datensätze haben stabile UUID, `dataset_key`, Typ, Payload, Version und optionalen `source_original_id`;
- Arbeitsdaten können geändert und soft-gelöscht werden, Originale nicht;
- Optimistic Locking über `base_version` mit `409 VERSION_CONFLICT`;
- Löschungen erzeugen `TRACK_DATA`-Tombstones.

Capabilities:
- `survey_originals_v1=true`
- `track_data_v1=true`.

Damit ist die ab Produktivimport verbindliche Trennung technisch vorbereitet: **Originaldatei unveränderlich, daraus abgeleitete/manuelle Streckendaten separat versioniert.**

### 8. Idempotenz / Offline-Replay – aktueller Block

Neu vorbereitet:
- `server/tbserver/idempotency.py` nutzt die bereits vorhandene Tabelle `tb.mutation_receipts`;
- Client kann `X-Client-Change-Id` mitsenden;
- Receipt-Schlüssel ist Benutzer + `client_change_id`;
- vor der Fachmutation wird ein PostgreSQL-Transaktions-Advisory-Lock auf Benutzer + Change-ID gesetzt;
- dadurch können zwei gleichzeitig wiederholte Requests nicht beide dieselbe Mutation ausführen;
- bereits erfolgreich ausgeführte Mutation liefert die gespeicherte Antwort zurück;
- Wiederverwendung derselben Change-ID für einen anderen Mutationstyp ergibt `409 CLIENT_CHANGE_ID_REUSED`;
- Response wird JSON-sicher als Receipt gespeichert;
- `track_data` nutzt den Mechanismus bereits für Create/Update/Delete;
- Source-Check `26_verify_tb_server_1_idempotency_source.py` prüft Lock, Receipt-Tabelle und Track-Replay-Vertrag.

Wichtig: Die Receipt-Infrastruktur ist damit belastbar angelegt, aber noch **nicht auf alle bestehenden Schreibendpunkte ausgerollt**. BTB, Baustellen, Mängel/Abnahmen und Dateiabschluss werden als nächster Schritt auf denselben Mechanismus umgestellt. Bis dahin darf noch keine Capability „vollständig serverweit idempotent“ gesetzt werden.

## CI / Tests

### Source-/Runtime-Verträge

Vorhanden:
- `20_verify_tb_server_1_foundation.py`
- `21_verify_tb_server_1_auth_source.py`
- `22_verify_tb_server_1_auth_runtime.py`
- `23_verify_tb_server_1_files_source.py`
- `24_verify_tb_server_1_defects_acceptances_source.py`
- `25_verify_tb_server_1_track_survey_source.py`
- `26_verify_tb_server_1_idempotency_source.py`

Der Server-Workflow prüft außerdem Baseline-Migration, Kerntabellen einschließlich `mutation_receipts`, Bootstrap, Health und aktivierte Capabilities.

### Aktuelles GitHub-Actions-Problem

Beim geprüften Lauf zum Branch-Stand `959a534...` wurde der Server-Workflow bereits vor dem ersten Step beendet:
- Jobstatus `failure`;
- `runner_id=0`;
- keine Steps vorhanden;
- keine Job-Logs erzeugt.

Damit ist dieser konkrete rote Lauf **kein ausgeführter Python-/PostgreSQL-Test**. Die Ursache liegt auf GitHub-Actions-/Runner-Ebene und muss separat geklärt werden. Der zuvor veraltete Capability-Test (`worksite_tree_v1 == false`) wurde bereits korrigiert.

### Android CI

Android ist für den Server-1.0-Block vorerst Testclient und nicht dessen Freigabenachweis. Die Android-Workflow-Trigger sind auf Android-/Gradle-Dateien und die alten Android/Schema-Sourcechecks begrenzt. Reine Änderungen unter `server/tbserver/**` sollen keinen vollständigen Android-Build mehr auslösen.

## Bekannte/offene Punkte

Noch nicht fertig:
- Idempotenz/`client_change_id` auf BTB, Baustellen, Mängel/Abnahmen und Dateiabschluss ausrollen;
- Android-Geräteauthentifizierung für die neue `/api/v1`-Serverbasis;
- konkrete Parser/Katalogisierung für TRA/KF/GRA/KOR/KV/GVP und fachliche Abschnittszusammenführung;
- echtes BTB-Abschluss-PDF inklusive Dateispeicherung und Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout;
- vollständige Portal-Fachoberfläche;
- Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität;
- Background Jobs;
- Backup/Restore-Werkzeuge;
- Testdaten-Reset-Werkzeug;
- vollständige Integrations-/End-to-End-Tests;
- produktiver NAS-Cutover.

Der Vermessungsimport ist aktuell ein serverseitiger Grundvertrag auf Basis bereits hochgeladener Dateien. Parser/Katalogisierung für konkrete Formate folgen auf dieser unveränderlichen Originalbasis.

Datei-/Upload-Modul ist zunächst auf baustellenbezogene Uploads mit Portal-Session-Authentifizierung begrenzt. Geräteauthentifizierung für Android und systemweite Dateien wie Firmenlogo/Releases werden in den dafür vorgesehenen Modulen ergänzt.

## Nächster sinnvoller Arbeitsschritt

1. **Idempotenz/`client_change_id` auf die übrigen offline-relevanten Schreibendpunkte ausrollen**;
2. danach Geräteauthentifizierung v1 so anbinden, dass Android denselben Vertrag nutzen kann;
3. konkrete Vermessungsparser/Katalogisierung und Abschnittszuordnung ergänzen;
4. Portal-Fachoberfläche und BTB-PDF-Abschluss schließen;
5. Admin/Systemstatus/Releases;
6. Backup/Restore/Reset und vollständige E2E-Tests;
7. GitHub-Actions-Runnerproblem klären bzw. Tests auf verfügbarer Laufumgebung ausführen;
8. erst danach NAS-Cutover und Android-Anpassung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
