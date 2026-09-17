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
- Parser-v2-/Fixture-Stand: `31cbd83b02523522f113c97dac412b3de3c829bf`
- Abschnittsquellen/KM-Kalibrierung: `1bf2e36271f54c8945653561bc7b46d95131165d`
- dedizierter Survey-Section-Schreibpfad: `4899fb433ef4a4fd2366691dc4fb088ed38f634d`, `c9f4816e643fbd403fdecfd694caf054458d460c`
- TRA-Verdichtung / Punktprojektion: `a89537d2469e8aae893bc2002d3c4c20042cbf17`
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

`30_verify_tb_server_1_survey_parser_source.py` enthält die vier bestehenden Android-Raw-KF-Testfälle: Zone-3/5362 PS1, `VERSION 7.0 R`, Station `0.0 -> null` und Sentinel `1e40`/Strecke `0000 -> fehlend`.

#### Parser v2 / reale Projektdateien

Der Serverparser wurde zusätzlich gegen den vorhandenen realen Absteckungs-/Vermessungsbestand geprüft. Nach SHA-256-Deduplizierung wurden 55 TRA, 45 GRA, 4 rohe KF, 1 KV, 2 KF.TXT, 1 GVP-Text und 2 KOR.TXT ohne Parserfehler gelesen. Zwei rohe/unbekannte KOR bleiben wie vorgesehen `UNSUPPORTED`. Zusätzlich wurde die rohe KF Strecke 5362 mit 75 Festpunkten erfolgreich gelesen; ihre Zone-3-Koordinaten bleiben `DB_REF_GK_3`.

Dabei wurde eine reale Dateinamenabweichung gefunden: vorangestellte Datumsbestandteile wie `2025-04-25_...` bzw. `2026-03-25_...` konnten bisher fälschlich als Streckennummer 2025/2026 interpretiert werden. In 18 eindeutigen GRA-Dateinamen war die bisherige Metadatenzuordnung dadurch falsch.

Parser v2 maskiert ISO-artige Datumsbestandteile vor der Streckensuche und übernimmt für TRA/GRA additiv die vorhandenen Android-Dateinamenregeln für Strecke, Gleis, Quellrichtung und Achstyp. `30_verify_tb_server_1_survey_parser_source.py` enthält dafür zusätzliche date-safe Metadatentests. Details und Testumfang stehen in `docs/TB-SERVER_1_0_SURVEY_FIXTURE_VERIFICATION.md`.

### Vermessungsabschnitte / KM-Kalibrierung

Migration `004_survey_sections.sql` ergänzt die bestehenden `track_data`-Arbeitsdaten, ohne eine parallele Vermessungsdatenhaltung zu erzeugen:

- `tb.track_data_sources`: mehrere unveränderliche Originalquellen je `SURVEY_SECTION`, mit Rolle, Reihenfolge und optional umgekehrter Geometrielaufrichtung;
- `tb.track_data_km_points`: `ORIGINAL_ANCHOR`, `MANUAL_ANCHOR`, `CONTROL_ANCHOR` und `KM_JUMP`.

Die Fach-API kann Vermessungsabschnitte anlegen, lesen, auflisten und versioniert aktualisieren. Sie nutzt `track.edit`, `X-Client-Change-Id`, Change Feed und Audit. Mehrere TRA-Quellen werden anhand ihrer DB_REF-Endpunkte diagnostisch als `CONNECTED`, `WARNING`, `OPEN` oder `UNVERIFIED` bewertet; Originaldateien bleiben unverändert.

Die KM-Logik übernimmt die bestehende fachliche Richtung aus Android: zwei belastbare Originalanker -> `AUTOMATISCH/GRUEN`; manuelle 2-Punkt-Kalibrierung oder ein Anker plus bekannte Richtung -> `MANUELL/ORANGE`; Geometrie ohne belastbare KM -> `ROT`; fehlende Geometrie -> `GRAU`. KM-Sprünge werden diskret berücksichtigt und verzerren den kontinuierlichen Kalibrierfaktor nicht.

Der generische `track-data`-Create-/Update-Pfad darf `dataset_kind=SURVEY_SECTION` nicht mehr schreiben. Survey-Sections müssen über die dedizierte API laufen, damit Quellen-, Anker- und Kalibrierregeln nicht umgangen werden können. Der bestehende generische Delete-Lebenszyklus mit Tombstone bleibt erhalten.

Details: `docs/TB-SERVER_1_0_SURVEY_SECTIONS.md`.

### TRA-Verdichtung / DB_REF / automatische Punktprojektion

Die serverseitige TRA-Geometrie übernimmt die vorhandene Android-Logik:
- 2,0-m-Verdichtung;
- Gerade und Kreisbogen analytisch;
- Übergangsbogen mit 0,5-m-Integration;
- echte TRA-Knoten bleiben exakte Segmentenden.

Die bestehende Android-Transformation DB_REF/GK -> ETRS89/WGS84 wurde mit denselben Helmertparametern auf den Server portiert und erkennt GK-Streifen 3, 4 und 5 aus dem Rechtswert. Damit können auch vorhandene Zone-3-Roh-KF-Punkte mit TRA-Geometrien verglichen werden, ohne Quellkoordinaten zu verändern.

Neu ist `POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors`. Der Rebuild projiziert koordinatenhaltige Punkte aus verknüpften `KM`-/`CONTROL`-Quellen auf die verdichtete TRA. Ein Punkt wird nur dann automatisch `ORIGINAL_ANCHOR`, wenn Quelle=`KM`, Strecke bestätigt, primäre TRA getroffen und Achsabstand <= 5 m ist. Sonstige Treffer bis 15 m werden höchstens `CONTROL_ANCHOR` und machen die Achse nicht automatisch grün. Explizite Streckenwidersprüche werden verworfen.

GRA wird bewusst nicht geraten: der aktuelle GRA-Parse enthält KM/Höhe, aber keine X/Y-Koordinate. Daher erzeugt GRA allein keine geometrisch belegten Originalanker. Automatisch erzeugte Punkte sind deterministisch und mit `survey-projection-v1` markiert; ein Rebuild ersetzt nur diese erzeugten Punkte und erhält manuelle Anker, KM-Sprünge und andere Fachpunkte.

Bei mehreren TRA-Quellen wird die nächstliegende Geometrie berücksichtigt. Bis die Stationsräume/Überlappungen mehrerer TRA robust verschmolzen sind, können jedoch nur Treffer auf der primären Geometrie `ORIGINAL_ANCHOR` werden; Treffer auf sekundären TRA bleiben Kontrollanker.

Details: `docs/TB-SERVER_1_0_SURVEY_PROJECTION.md`.

Capabilities u. a.: `survey_originals_v1=true`, `track_data_v1=true`, `survey_parser_catalog_v1=true`, `survey_section_calibration_v1=true`, `survey_projection_v1=true`, `device_fach_api_v1=true`.

## Tests / Checks

Workflow-Zielstand:
- Migrationen 001 + 002 + 003 + 004;
- `latest_known=4`, `latest_applied=4`;
- `survey_parse_catalog`, `track_data_sources` und `track_data_km_points` als Kerntabellen;
- Source-/Vertragschecks 20–32, einschließlich Raw-KF-Parität, date-safe Dateinamenmetadaten, Survey-Section-/KM-Kalibrierlogik sowie TRA-Verdichtung/DB_REF-Projektion;
- Health/Capabilities/Auth-Checks.

Lokal erfolgreich geprüft:
- Python-Syntax-/AST-Prüfung der Survey-Section-, Kalibrier-, Geometrie- und Projektionsdateien;
- automatische 2-Punkt-Kalibrierung (`GRUEN`);
- manuelle 2-Punkt-Kalibrierung (`ORANGE`);
- ein Anker + steigende/fallende Richtung;
- diskreter KM-Sprung ohne Verfälschung des kontinuierlichen Faktors;
- fehlende KM (`ROT`) und fehlende Geometrie (`GRAU`);
- synthetische 10-m-TRA wird exakt auf Stationen 1000/1002/1004/1006/1008/1010 verdichtet;
- exakter Projektionspunkt auf der Achse -> Station 1004 / 0 m / `GRUEN`;
- 4,5-m-Seitenabstand -> `GELB`;
- realer Zone-3-KF-Testpunkt wird mit dem portierten DB_REF-Wandler in eine plausible WGS84-Position in Deutschland transformiert.

Für Parser v2 wurden zusätzlich erfolgreich ausgeführt:
- vier Raw-KF-Paritätsfälle;
- date-safe TRA/GRA-Dateinamen-Metadatentests;
- Parse-Lauf über die dokumentierten eindeutigen realen Projektdateien.

### Bekanntes GitHub-Actions-Problem

Auch der TB-Server-Lauf **#83** für Commit `a89537d2469e8aae893bc2002d3c4c20042cbf17` wurde bereits vor dem ersten Step beendet: Job `foundation`, `steps=[]`, `runner_id=0`, keine ausgeführten Job-Schritte. Das sind **keine ausgeführten Python-/PostgreSQL-Tests**. Das Runner-/Actions-Problem muss separat geklärt werden; lokale Source-/Fixture-Checks dürfen bis dahin nicht als im Runner bestanden bezeichnet werden.

## Bekannte/offene Punkte

- neue Punktprojektion mit echten passenden TRA+KF/KV/GVP-Projektdateien end-to-end verifizieren und Abstandsgrenzen nur anhand realer Treffer weiter schärfen;
- GRA nur dann einer TRA-Station zuordnen, wenn aus realen Daten ein belastbarer Zuordnungsmechanismus belegt ist; aktuell kein X/Y im GRA-Parse, daher keine automatische Projektion;
- robuste Stationsraum-/Überlappungsverschmelzung mehrerer TRA-Quellen über die aktuelle Endpunktdiagnose hinaus;
- Verdichtungs-/Projektionsdaten bei Bedarf performant cachen, ohne die unveränderlichen Originale zu verändern;
- rohe/unbekannte KOR fachlich nur dann ergänzen, wenn das Format belastbar belegt ist; bis dahin `UNSUPPORTED`;
- Android-Fachclient auf alle Server-1.0-Endpunkte umstellen und Upload/Sync praktisch testen;
- echtes BTB-Abschluss-PDF + Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout, vollständige Portal-Fachoberfläche, Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität, Background Jobs, Backup/Restore/Reset;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Reale TRA+KF/KV/GVP-Projektdateien durch Import/Parse/Projektion/Kalibrierung testen und die tatsächlichen Achsabstände/Stationswerte dokumentieren.
2. Danach robuste Stationsraum-/Überlappungsverschmelzung mehrerer TRA-Quellen bauen; GRA nur mit belegtem Zuordnungsmechanismus ergänzen.
3. Android-Fachvertrag praktisch gegen TB-Server 1.0 testen.
4. Portal/PDF/Admin/Releases schließen.
5. Backup/Restore/Reset + E2E testen.
6. Erst danach NAS-Cutover und Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
