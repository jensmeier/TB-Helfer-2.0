# Server-Gate 001: Backup, Rechte, 255 Clients

Stand: 06.10.2026. Android und Portal-Oberflächen sind nicht Teil dieser Änderung.

## Was sich ändert

Vollbackups verwenden einen exportierten PostgreSQL-Snapshot für Datenbankdump und
Dateireferenzliste. Gesichert werden aktive `tb.files`, sämtliche Originalquellen
und Releases mit gespeicherter Datei. Identische Referenzen werden zusammengeführt.
Dateien werden einzeln in eine temporäre Kopie gelesen, auf Größe/SHA256 geprüft und
genau diese geprüften Bytes archiviert. Eine zwischenzeitlich entfernte oder anders
geschriebene benötigte Datei bricht die Sicherung ab. Ein danach neu angelegtes Objekt
gehört nicht zum früheren Snapshot. Es gibt keine globale Schreibsperre.

FULL-Prüfung und Restore in ein separates Ziel prüfen jetzt die tatsächlichen
Dateireferenzen der wiederhergestellten Datenbank. Auch alte Vollbackups werden so
auf fehlende/falsche Dateien geprüft. Das äußere v1-Archivformat bleibt bestehen.
Neue Vollbackups enthalten `consistency=snapshot-reference-v1`, Dateianzahl und
`open_uploads=restart`. Nicht abgeschlossene Uploads und abgeleitete Vorschaubilder
sind kein gesicherter Fachbestand. Nur im wiederhergestellten Ziel werden offene
Uploadsessions auf EXPIRED gesetzt; diese Uploads müssen neu gestartet werden.
Die Live-Sessions werden nicht verändert. Einzelne DATABASE-/FILES-Backups bleiben
getrennte Sicherungen ohne gemeinsame Konsistenzgarantie.

Ein langsamer/paralleler Import kann somit ein Vollbackup fehlschlagen lassen.
Das ist ein erkennbarer Fehler, keine erfolgreiche inkonsistente Sicherung.
Erneut in einem ruhigeren Zeitfenster sichern. Es wird keine automatische
Endlosschleife gestartet. Zusätzlicher temporärer Platz: bis zur Größe der größten
einzeln geprüften Datei neben Dump/Archiven.

## Rechteprüfung

| Bereich | Schutz / Ergebnis | Nachweis |
| --- | --- | --- |
| Portal-/Gerätelogin und bestehende Zugänge | Gesperrte/gelöschte/fehlende Firma wird zusätzlich zum Benutzerstatus gesperrt; ADMIN-Systemverwaltung bleibt möglich | 132, 134, 138; SQL-JOINs quergelesen |
| Baustellenliste/Details und Fachzugriffe | Firmenbindung, aktive Mitgliedschaft; ADMIN ohne Fachzugriff | 132, 133 |
| Dateien: Liste, Medienübersicht, Metadaten, Download, Vorschau | Zusätzlich `files.view`; Detail-/Downloadpfade nutzen denselben Ladeguard | 138 + Quellprüfung der Aufrufer |
| Laufende Uploads: Status, Chunk, Abschluss, Abbruch | Nur Ersteller; für Schreiben außerdem `files.add` und aktuelle Baustellenrechte | 138 + Quellprüfung der Aufrufer |
| Datei-Verknüpfungen | Zielbaustelle, BTB-Team, Bereich bei Mängeln/Abnahmen; Final-PDF weiter nur Teamleitung | 138 |
| Mängel/Abnahmen | Funktionsrechte und Bereich; Fremdfirma abgewiesen | 132, 138; Schreibpfade quergelesen |
| BTB/Teilnehmer/Finalisieren | Baustellenrecht, Funktion, Teamzugehörigkeit, gegebenenfalls Teamleitung | Quellprüfung; Datei-/Teamprüfung 138 |
| Baustellenstruktur/Mitglieder/Einladungen | Zentrale Baustellengrenze; Schreibrechte `worksite.structure.edit` bzw. `members.manage`; CSRF bei Cookiezugriff | Quellprüfung |
| Streckendaten | Baustellenzugehörigkeit; Schreiben `track.edit`; Originalquellen-Firmenprüfung | Quellprüfung |
| Firmenprofil/Benutzerprofil | Eigene Firma bzw. eigene Benutzer-ID; Adminpfade separat geschützt | Quellprüfung |
| Laufender Sync | Wiederholte Authentifizierung nach Warten und vor Ausgabe | 135, 139; noch keine NAS-Sperrabnahme |
| GLOBAL-Fachkatalog | Gemeinsamer Bestand bleibt absichtlich firmenübergreifend | Vertrag unverändert |

Diese Matrix unterscheidet ausgeführte lokale Tests von Quellprüfung. Sie ist keine
Behauptung, sämtliche Endpunkte einschließlich HTTP, SQL und aller Rollenvarianten
auf der NAS geprüft zu haben. Sync-Ereignisse enthalten Änderungskennungen;
Fachinhalte werden anschließend über ihre geschützten Detailendpunkte geladen.
Idempotenzbelege sind benutzergebunden; bestehende Replay-Antworten bleiben
historische Antworten desselben Benutzers, kein erneuter Schreibvorgang.

## Lokale Tests

Vom Repository-Stamm einzeln ausführen:

```sh
python server/checks/130_verify_monitor_liveness.py
python server/checks/131_verify_backup_failure_safety.py
python server/checks/132_verify_permission_guards.py
python server/checks/133_verify_admin_worksite_boundary.py
python server/checks/134_audit_session_revocation.py
python server/checks/135_verify_sync_reauthentication.py
python server/checks/136_verify_public_alert_confirmation.py
python server/checks/137_verify_snapshot_backup.py
python server/checks/138_verify_file_endpoint_rights.py
python server/checks/139_verify_255_sync_clients.py
```

78 Tests, alle bestanden. Produktionsfunktionen teilweise AST-isoliert mit
SQL/Auth-Doubles; echte lokale Dateien/Tararchive, echte asyncio-Hub-/Semaphorelogik
und ein lokaler HTTP-Testserver für das Lastwerkzeug. Keine echte FastAPI-/PostgreSQL-
oder NAS-Lastmessung. Der 255-Client-Koordinationstest ist kein Durchsatzbenchmark.

## Echter PostgreSQL-Gate (CI bestanden; NAS offen)

`server/tools/backup_snapshot_gate.py` benötigt die installierten Serverabhängigkeiten,
`pg_dump` und `pg_restore`, zwei leere eigens bereitgestellte Testdatenbanken mit
Namen `tb_gate_*` und passende Rechte. Keine bestehende Restore-Test-Datenbank verwenden.
DSNs nur als `TB_GATE_SOURCE_DSN` und `TB_GATE_TARGET_DSN` in der Testumgebung setzen;
keine Passwörter in Git, Befehlszeilen oder Ergebnisdateien schreiben.

Beispiel für bereits bereitgestellte Testdatenbanken:

```sh
python server/tools/backup_snapshot_gate.py --confirm-isolated tb_gate_source:tb_gate_target
```

Der Gate verweigert bestehende Benutzertabellen/tb-Schemata. Er erzeugt ausschließlich
Testdaten, schreibt während des exportierten Snapshots über eine zweite Verbindung,
ruft die echten Produktionsfunktionen für Dump/Archiv/Restore auf und prüft:
neuer Commit ausgeschlossen, alte Datei vorhanden, beschädigte Datei abgewiesen,
fehlende wiederhergestellte Datei abgewiesen, offene Uploads nur im Ziel abgelaufen.
Die eigens angelegten tb-Schemata werden anschließend entfernt. Ein vollständiger
Restore aller realen Migrationen/PostGIS-Daten ist zusätzlich über „Backup prüfen“
in der bestehenden isolierten Restore-Umgebung abzunehmen.

## Echter 255-Client-Gate (CI bestanden; NAS offen)

`server/tools/sync_mixed_loadtest.py` läuft gegen eine ausdrücklich bestätigte
isolierte Testinstanz. Er schreibt keine Fachdaten, erzeugt aber 15 Portal-Sessions
und meldet diese anschließend ab. Benötigt werden 240 unterschiedliche aktive
Gerätetokens für 15 TB und deren 225 Helfer sowie 15 getrennte Portal-Testbenutzer.
Die angegebene Rolle im Manifest ist eine Testzuordnung; passende Firmen-, Team-
und Benutzerrechte müssen in den Testdaten tatsächlich eingerichtet sein.

Lokales Manifest als JSON-Liste, insgesamt genau 255 Einträge. Beispiel einzelner
Einträge (keine echten Tokens/Passwörter im Manifest):

```json
[
  {"role": "TB", "token_env": "TB_GATE_TB_01"},
  {"role": "HELPER", "token_env": "TB_GATE_HELPER_001"},
  {"role": "PORTAL", "login": "test_portal_01", "password_env": "TB_GATE_PORTAL_01_PASSWORD"}
]
```

Die Umgebungsvariablen enthalten die Testzugänge. Das Beispiel oben ist bewusst
unvollständig und wird vom Tool abgewiesen, bis alle 255 unabhängigen Zugänge
eingetragen sind. Kein einzelnes Token 240-mal verwenden.

```sh
python server/tools/sync_mixed_loadtest.py \
  --base-url https://testinstanz.example \
  --confirm-staging https://testinstanz.example \
  --clients-file clients.local.json \
  --rounds 3 --wait-seconds 10 --timeout 40 --max-overhead 5 \
  --output sync-load-report.local.json
```

Jeder Client erhält einen eigenen Cursor. Alle 255 starten pro Runde gemeinsam.
Gate: keine HTTP-/Vertrags-/Cursorfehler, p95 höchstens Wartezeit + 5 Sekunden und
alle Portal-Testsitzungen wieder abgemeldet. Bericht enthält Gesamt-/Rollenwerte
und Fehlerzahlen, keine Zugangsdaten oder Antwortinhalte. Schwellwert ist ein
Abnahmekriterium, kein bereits gemessener NAS-Wert. Zusätzlich NAS-CPU/RAM,
DB-Verbindungen, API-Fehler und Monitorzustand beobachten. Dieser Gate misst nur
Sync-Longpoll; gleichzeitiges Fotoschreiben/Downloads/BTB-Schreiben ist kein Teil
dieser Messung und darf daraus nicht als bestanden abgeleitet werden.

## NAS-Rechteabnahme (noch offen)

Nur isolierte Testfirmen/Testnutzer verwenden. Aktiven eigenen Zugriff zuerst
prüfen; fremde Baustellen-/Dateiadressen müssen abgewiesen werden. `files.view`
entziehen: Liste, Medienübersicht, Metadaten, Download und Vorschau abweisen.
Eine fremde Upload-ID darf nicht gelesen, erweitert, abgeschlossen oder abgebrochen
werden; der Ersteller darf seinen Upload mit gültigen Rechten fortsetzen.

Eine Testfirma sperren: neue Portal-/Geräteanmeldung und bestehende Zugänge erhalten
403 `COMPANY_DISABLED`; Systemadmin kann die Firma weiterhin verwalten. Firma
danach wieder aktivieren. Für Nutzer-/Geräte-/Mitgliedschaftssperren jeweils eine
wartende Longpoll-/SSE-Verbindung öffnen, sperren und prüfen, dass bei der nächsten
Authentifizierung keine weiteren Fachdaten ausgegeben werden. Dies ist kein
garantierter sofortiger Abbruch bereits ausgelieferter/gepufferter Daten.

## Bestätigtes CI-Ergebnis vom 06.10.2026

Codecommit `6d9fc82b6a9dc5fb0b8da55f5f2a37ebdd268ba9`,
[Gate-Job 112386875101](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37497843193/job/112386875101)
erfolgreich: Server-Dockerimage gebaut, alle 78 lokalen Regressionen auch in CI
bestanden, echte Migrationen angewendet, echter PostgreSQL-17-Snapshot/Dump/Restore
mit parallelem Commit und beschädigten/fehlenden Dateireferenzen bestanden.

`server_http_gate.py` hat auf einer selbst gestarteten FastAPI-Instanz und PostGIS
eigene/fremde Firmenzugriffe, Systemadmin-Abweisung, Bereichsfilter bei Mängeln und
Abnahmen, files.view, fremde Uploadsession (Status/Chunk/Abschluss/Abbruch), erlaubten
Upload, Cookie-CSRF und Firmensperre über Portal/Gerät geprüft. Vier echte während
des Wartens gesperrte Longpolls (Nutzer, Firma, Mitgliedschaft, Gerät) wurden abgewiesen;
ein SSE-Stream nach Mitgliedschaftsentzug lieferte keine Daten mehr.

HTTP-Lastmessung: 15 TB +225 Helfer mit eigenen Gerätetokens +15 eigene Portalnutzer,
jeweils drei Runden mit 2 Sekunden Longpoll-Wartezeit:

| Runde | Anfragen | Fehler | Median | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 255 | 0 | 5,05 s | 7,11 s | 7,31 s |
| 2 | 255 | 0 | 5,10 s | 7,21 s | 7,35 s |
| 3 | 255 | 0 | 5,03 s | 7,17 s | 7,35 s |

15/15 Portal-Lasttestsitzungen abgemeldet; nach Testende keine offene Testsitzung.
CI verwendet ausdrücklich 10 Sekunden zulässigen Overhead auf gemeinsam genutzter
Runnerhardware. Das strengere Beispielkriterium von 5 Sekunden ist mit diesen
Messwerten **nicht** bestanden (p95 minus 2 s bis 5,21 s). Keine Aussage zu NAS-/Cloudflare-
Leistung oder gleichzeitigen Foto-/BTB-Schreiblasten. Diese Abnahmen bleiben offen.

Gesamtes CI nicht grün: Der bestehende `foundation`-Job scheitert im älteren Check23
am erwarteten Marker `FILE_LINK_TARGET_NOT_FOUND`; der Runtimecode verwendet bereits
vor diesem Auftrag `FILE_LINK_TARGET_NOT_VISIBLE` (Baseline 4cc7fef geprüft).
Android-CI scheitert separat am Schema10-Quellmarker
`BtbVerlaufFragment.kt:MergeSafeBaustellenExport.normalisieren`.
Keine Android-Datei wurde verändert. Die älteren Prüfungen wurden nicht zur
Erzeugung eines grünen Gesamtergebnisses abgeschaltet oder abgeschwächt.

## Übernahme

Über GitHub Desktop synchronisieren, dann nach `/volume1/docker/tb-helfer-v1/tbserver/`:

- `backup_inventory.py` (neu), `backup.py`, `restore.py`, `restore_target.py`
- `permissions.py`, `portal.py`, `devices.py`, `files.py`

API und Worker neu starten; keine Migration, kein DB-Neustart. Tests/Werkzeuge nur
in die Testumgebung übernehmen. Vorherigen Monitorfix bei noch ausstehender
Übernahme separat berücksichtigen. Originaldaten, `.env`, Storage und Backups
behalten. Neue Dateien vollständig übertragen, bevor Prozesse neu gestartet werden.

Danach neues Vollbackup → „Backup prüfen“. Erwartet werden erfolgreiche
Dateireferenzprüfung und nachvollziehbarer Fehler bei fehlenden Dateien. Eine
produktiv ersetzende Wiederherstellung ist dadurch weder angefordert noch ausgeführt.

Technische Referenzen: [PostgreSQL 17 Snapshot-Synchronisation](https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-SNAPSHOT-SYNCHRONIZATION),
[pg_dump --snapshot](https://www.postgresql.org/docs/17/app-pgdump.html),
[Psycopg ConnectionInfo](https://www.psycopg.org/psycopg3/docs/api/objects.html).
