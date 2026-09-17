# TB-Helfer – aktueller Projektstand

Stand: 2026-09-17

Maßgeblich sind der aktuelle GitHub-Code im aktiven Branch, `docs/TB-HELFER_PROJEKTREGELN.md`, diese Datei, `docs/TB-SERVER_1_0_ZIELMODELL.md` und `docs/TB-SERVER_1_0_CUTOVER.md`.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- Cutover-Regel: `64bb798e37f6a220fbd1715859a813a359a32eee`
- Dateien/Chunkupload: `959a53459455062def10608df4ca4c4cd1b0a179`
- Mängel/Abnahmen: `40249785879b82714b7d2df822e9e52fb7fb750c`
- Vermessungsoriginale/Track: `a84c3607512a61145eba85309aa80bc2d6f015f0`
- Idempotenz-Grundlage: `d46650a938ab370c6f3b65d97852b2168ea08287`
- Replay-sicherer Fachkern: `e9f16e335badbd9f036405024f89aa47fcb598fe`
- Geräteaktivierung/Bearer-Grundlage: `2bede07a590521210dc7c351582363565451c4a0`
- Geräte-Bearer auf Fach-API: `19da0daee77ce1f121ba5994fe91a5bd84d0d626`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Strategie

Serverbasis vollständig bauen und testen, danach kontrollierter NAS-Cutover mit vorher geprüftem Backup, anschließend Android systematisch auf den festen `/api/v1`-Vertrag umstellen.

Der derzeitige Server-Fachdatenbestand ist Testbestand. Beim 1.0-Cutover dürfen Testnutzer, Testbaustellen und Test-/Leerdaten für TRA/KF/GRA/KOR/KV/GVP sowie Strecken-/Bahnhofs-/Vermessungskataloge entfernt werden. GitHub-Code, Konfiguration/Secrets und Backups bleiben erhalten. Statische Grunddaten werden aus definierter Quelle neu aufgebaut.

**Ab dem ersten echten produktiven Vermessungsimport sind Originaldateien unveränderlich und dürfen durch spätere Updates/Resets nicht gelöscht oder überschrieben werden.** Bearbeitete, geparste und berechnete Daten liegen getrennt und bleiben neu erzeugbar.

## TB-Server 1.0 – umgesetzt

### Foundation / Auth / Fachkern

Vorhanden sind `server/tbserver/`, separater `Dockerfile.v1`, Migration Runner mit SHA-256-Prüfung/Advisory-Lock, Firmen/Benutzer/Portal, Baustellenbaum, Rechteprofile, Teams, granularer BTB, Live-Sync, Dateien/Chunkupload, Mängel, Abnahmen, Track-Arbeitsdaten, Change Feed, Audit und `X-Client-Change-Id`-Receipts.

Replay-sicher sind der relevante Fachkern für Track, Mängel, Abnahmen, BTB, Baustellenstruktur/Mitglieder und Upload-Lebenszyklus.

### Geräteaktivierung / Android-Bearer

Der getestete Android-Vertrag bleibt kompatibel: `POST /api/v1/device/login`, danach `Authorization: Bearer <token>` und `GET /api/v1/device/me`. Aktivierungs-Key und Geräte-Token werden serverseitig nur gehasht gespeichert; maximal ein aktives Gerät je Benutzer wird per DB-Index erzwungen. Admin kann Geräte freigeben oder blockieren. `/api/v1/admin/*` und `/api/v1/portal/*` bleiben strikt Portal-Session + CSRF; die übrige Fach-API akzeptiert zusätzlich das Geräte-Bearer-Token.

Capabilities: `device_activation_v1`, `device_bearer_auth_v1`, `device_fach_api_v1` = `true`.

### Unveränderliche Vermessungsoriginale / Track-Arbeitsdaten

`server/tbserver/survey_originals.py` und `track_data.py` trennen Original und Arbeitsstand:
- Originalimport prüft Größe + SHA-256 und legt atomar unter `reference/survey-originals/<uuid>/...` ab;
- keine PATCH-/PUT-/DELETE-Endpunkte und kein SQL-Update/Delete für Originale;
- identische Originalbytes werden als Duplikat erkannt;
- veränderbare/versionierte Arbeitsdaten liegen in `tb.track_data` mit optionalem `source_original_id`;
- Optimistic Locking + Tombstones.

Capabilities: `survey_originals_v1=true`, `track_data_v1=true`.

### Vermessungsparser / Parse-Katalog – aktueller Block

Serverseitiger Parservertrag basiert auf den vorhandenen Android-Parsern und erfindet keine neuen Dateiformate:
- TRA: binäre ER0-Struktur, 0x48-Header, 78 Byte pro Knoten, Elementtyp 0/1/2;
- GRA: 36-Byte-Header und 36-Byte-Punkte;
- GVP/Festpunkte als gemeinsame Parserfamilie für GVP-Text, **rohe KF 7.0 / 7.0 R mit 102-Byte-Datensätzen**, KF.TXT und KV;
- KOR wird nur für die belegte lesbare `.kor.txt`-Variante fachlich geparst; rohe/unbekannte `.kor` werden ausdrücklich `UNSUPPORTED` statt heuristisch interpretiert;
- eine komplett `0.00000`-Stationsspalte wird wie im Android-Parser nicht als belastbare KM-Quelle verwendet;
- DB_REF-/GK-Streifen-3-Punkte bleiben als Quellkoordinaten erhalten; GPS-Umrechnung ist nicht Teil dieses Server-Katalogschritts.

Migration `003_survey_parse_catalog.sql` legt `tb.survey_parse_catalog` als **abgeleiteten, neu aufbaubaren** aktuellen Parse-Stand an. Gespeichert werden Parserfamilie/-version, Quell-SHA-256, Status `PARSED|UNSUPPORTED|ERROR`, erkanntes Format, normalisiertes JSON, Warnungen und Fehlerdiagnose. Die Originaldatei selbst wird nicht verändert.

Vor jedem Parse prüft der Server die gespeicherte Originaldatei erneut gegen `byte_size` und SHA-256. Schreiben darf nur ein Benutzer mit `track.edit` im angegebenen Baustellenkontext. Parse-Ergebnisse erzeugen Change Feed und Audit.

Capability: `survey_parser_catalog_v1=true`.

## Tests / Checks

Server-Checks 20–29 bleiben bestehen. Neu:
- `30_verify_tb_server_1_survey_parser_source.py`: AST-/Vertragscheck für Migration 003, Parserfamilien, Original-Integritätsprüfung, Router/Capability und Verbot jeder Mutation von `tb.survey_originals` im Parser.

Workflow-Zielstand:
- Migrationen 001 + 002 + 003;
- `latest_known=3`, `latest_applied=3`;
- `survey_parse_catalog` als erforderliche Kerntabelle;
- `survey_parser_catalog_v1=true` im Capability-Test.

### Bekanntes GitHub-Actions-Problem

Die zuletzt geprüften TB-Server-Workflow-Läufe wurden bereits vor dem ersten Step beendet: `runner_id=0`, keine Steps, keine Job-Logs. Diese roten Läufe sind **keine ausgeführten Python-/PostgreSQL-Tests**. Das Runner-/Actions-Problem muss separat geklärt werden. Bis dahin dürfen die Source-Checks nicht als tatsächlich im Runner ausgeführt bezeichnet werden.

## Bekannte/offene Punkte

- Parser-Resultate müssen mit echten repräsentativen Projektdateien als Integrationstest gegen die Android-Ergebnisse verglichen werden; derzeit ist der Vertrag aus dem vorhandenen Android-Quellcode übertragen.
- TRA-2-m-Verdichtung und DB_REF→WGS84 bleiben weiterhin abgeleitete Rechenschritte und gehören nicht in das unveränderliche Original.
- Abschnittszusammenführung/KM-Kalibrierung auf Basis der geparsten Daten;
- Datei-Metadaten `source` beim gemeinsamen Uploadpfad nach Geräte-/Portal-Auth auf `APP`/`PORTAL` unterscheiden;
- Android-Fachclient auf alle neuen Server-1.0-Fachendpunkte umstellen;
- echtes BTB-Abschluss-PDF + Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout, vollständige Portal-Fachoberfläche, Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität, Background Jobs, Backup/Restore/Reset-Werkzeuge;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Vermessungsparser mit echten TRA/GRA/KF/KF.TXT/KV/GVP/KOR.TXT-Projektdateien gegen die bestehenden Android-Ergebnisse verifizieren und erst dann Abschnittszusammenführung/KM-Kalibrierung aufbauen.
2. Upload-`source` für Geräte/Portal trennen und Android-Fachvertrag bei der späteren Client-Anbindung praktisch testen.
3. Portal/PDF/Admin/Releases schließen.
4. Backup/Restore/Reset + E2E testen.
5. Erst danach NAS-Cutover und Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
