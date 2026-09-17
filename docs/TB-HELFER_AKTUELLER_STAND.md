# TB-Helfer – aktueller Projektstand

Stand: 2026-09-17

Maßgeblich sind aktueller GitHub-Code im aktiven Branch, `docs/TB-HELFER_PROJEKTREGELN.md`, diese Datei, `docs/TB-SERVER_1_0_ZIELMODELL.md` und `docs/TB-SERVER_1_0_CUTOVER.md`.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- Cutover-Regel: `64bb798e37f6a220fbd1715859a813a359a32eee`
- Dateien/Chunkupload: `959a53459455062def10608df4ca4c4cd1b0a179`
- Mängel/Abnahmen: `40249785879b82714b7d2df822e9e52fb7fb750c`
- Vermessungsoriginale/Track: `a84c3607512a61145eba85309aa80bc2d6f015f0`
- Replay-sicherer Fachkern: `e9f16e335badbd9f036405024f89aa47fcb598fe`
- Geräteaktivierung/Bearer: `2bede07a590521210dc7c351582363565451c4a0`, `19da0daee77ce1f121ba5994fe91a5bd84d0d626`
- Vermessungsparser/Katalog: `3e974e7bd61c0241b69951e763d8eca9e2660e15`
- Raw-KF-Paritätstest: `9378069be8c70fc89696c8c7fbf1b9b051474204`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Strategie

Serverbasis vollständig bauen und testen, danach kontrollierter NAS-Cutover mit vorher geprüftem Backup, anschließend Android systematisch auf `/api/v1` umstellen. Der derzeitige Server-Fachdatenbestand ist Testbestand und darf beim 1.0-Cutover zurückgesetzt werden. GitHub-Code, Konfiguration/Secrets und Backups bleiben erhalten.

**Ab dem ersten echten produktiven Vermessungsimport sind Originaldateien unveränderlich und dürfen durch spätere Updates/Resets nicht gelöscht oder überschrieben werden.** Bearbeitete, geparste und berechnete Daten liegen getrennt und bleiben neu erzeugbar.

## TB-Server 1.0 – umgesetzt

### Foundation / Fachkern

Vorhanden sind Migration Runner mit SHA-256-Prüfung/Advisory-Lock, Firmen/Benutzer/Portal, Baustellenbaum, Rechteprofile, Teams, granularer BTB, Live-Sync, Dateien/Chunkupload, Mängel, Abnahmen, Track-Arbeitsdaten, Change Feed, Audit und `X-Client-Change-Id`-Receipts. Replay-sicher sind die relevanten Fachmutationen für Track, Mängel, Abnahmen, BTB, Baustellenstruktur/Mitglieder und Upload-Lebenszyklus.

### Geräteaktivierung / Android-Bearer

Der getestete Android-Vertrag bleibt kompatibel: `POST /api/v1/device/login`, danach `Authorization: Bearer <token>` und `GET /api/v1/device/me`. Aktivierungs-Key und Geräte-Token werden nur gehasht gespeichert; maximal ein aktives Gerät je Benutzer wird per DB-Index erzwungen. Admin kann Geräte freigeben oder blockieren. `/api/v1/admin/*` und `/api/v1/portal/*` bleiben Portal-Session + CSRF; die Fach-API akzeptiert zusätzlich Geräte-Bearer.

### Dateien / Bilder / Chunkupload

Resumierbarer sequenzieller Upload mit Offset-/Größen-/SHA-256-Prüfung, fsync, atomarem Abschluss, Datei-Verknüpfungen und Replay-Sicherheit ist vorhanden.

Der gemeinsame Abschluss klassifiziert die Herkunft jetzt nach Auth-Art:
- Geräte-Bearer (`auth_kind=DEVICE`) -> `tb.files.source=APP`;
- Portal-Session -> `tb.files.source=PORTAL`.

Das Upload-Audit enthält bei Geräteauth zusätzlich `actor_device_id`; Tokens/Geheimwerte werden nicht protokolliert. `23_verify_tb_server_1_files_source.py` prüft diese Trennung und verbietet wieder fest verdrahtetes `PORTAL` im gemeinsamen INSERT.

### Unveränderliche Vermessungsoriginale / Track-Arbeitsdaten

Originalimport prüft Größe + SHA-256 und legt atomar unter `reference/survey-originals/<uuid>/...` ab. Es gibt keine PATCH-/PUT-/DELETE-Endpunkte und kein SQL-Update/Delete für Originale. Veränderte Arbeitsdaten liegen separat in `tb.track_data` mit Optimistic Locking und Tombstones.

### Vermessungsparser / Parse-Katalog

Serverseitiger Parservertrag basiert auf den vorhandenen Android-Parsern:
- TRA: binäre ER0-Struktur, 0x48-Header, 78 Byte pro Knoten;
- GRA: 36-Byte-Header, 36 Byte pro Punkt;
- GVP/Festpunkte: GVP-Text, rohe KF 7.0 / 7.0 R mit 102-Byte-Datensätzen, KF.TXT und KV;
- KOR: nur die belegte `.kor.txt`-Variante; rohe/unbekannte `.kor` werden `UNSUPPORTED`, nicht geraten;
- komplette Null-Stationen werden nicht als belastbare KM-Quelle verwendet;
- DB_REF/GK-Streifen 3 bleibt als Quellkoordinate erhalten; GPS-Umrechnung bleibt abgeleiteter Schritt.

Migration `003_survey_parse_catalog.sql` enthält `tb.survey_parse_catalog` als neu aufbaubaren Parse-Stand mit Parserfamilie/-version, Quell-SHA-256, `PARSED|UNSUPPORTED|ERROR`, Format, normalisiertem JSON, Warnungen und Fehlerdiagnose. Vor jedem Parse werden Dateigröße und SHA-256 des unveränderlichen Originals erneut geprüft.

`30_verify_tb_server_1_survey_parser_source.py` enthält zusätzlich die vier bestehenden Android-Raw-KF-Testfälle: Zone-3/5362 PS1, `VERSION 7.0 R`, Station `0.0 -> null` und Sentinel `1e40`/Strecke `0000 -> fehlend`.

Capabilities u. a.: `survey_originals_v1=true`, `track_data_v1=true`, `survey_parser_catalog_v1=true`, `device_fach_api_v1=true`.

## Tests / Checks

Workflow-Zielstand:
- Migrationen 001 + 002 + 003;
- `latest_known=3`, `latest_applied=3`;
- `survey_parse_catalog` als Kerntabelle;
- Source-/Vertragschecks 20–30, einschließlich Raw-KF-Parität;
- Health/Capabilities/Auth-Checks.

### Bekanntes GitHub-Actions-Problem

Die zuletzt geprüften TB-Server-Läufe werden weiterhin bereits vor dem ersten Step beendet (`steps=null`, keine Job-Logs). Das sind **keine ausgeführten Python-/PostgreSQL-Tests**. Das Runner-/Actions-Problem muss separat geklärt werden; Source-Checks dürfen bis dahin nicht als im Runner bestanden bezeichnet werden.

## Bekannte/offene Punkte

- Parser mit echten repräsentativen TRA/GRA/KF/KF.TXT/KV/GVP/KOR.TXT-Projektdateien gegen Android-Ergebnisse verifizieren;
- TRA-2-m-Verdichtung, DB_REF->WGS84 sowie Abschnittszusammenführung/KM-Kalibrierung als abgeleitete Rechenschritte;
- Android-Fachclient auf alle Server-1.0-Endpunkte umstellen und Upload/Sync praktisch testen;
- echtes BTB-Abschluss-PDF + Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout, vollständige Portal-Fachoberfläche, Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität, Background Jobs, Backup/Restore/Reset;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Parser mit echten Projektdateien verifizieren; danach Abschnittszusammenführung/KM-Kalibrierung bauen.
2. Android-Fachvertrag praktisch gegen TB-Server 1.0 testen.
3. Portal/PDF/Admin/Releases schließen.
4. Backup/Restore/Reset + E2E testen.
5. Erst danach NAS-Cutover und Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
