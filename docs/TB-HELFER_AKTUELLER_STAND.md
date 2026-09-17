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
- Raw-KF-Parität: `9378069be8c70fc89696c8c7fbf1b9b051474204`
- Parser-v2-/Fixture-Stand: `31cbd83b02523522f113c97dac412b3de3c829bf`
- Abschnittsquellen/KM-Kalibrierung: `1bf2e36271f54c8945653561bc7b46d95131165d`
- dedizierter Survey-Section-Schreibpfad: `4899fb433ef4a4fd2366691dc4fb088ed38f634d`, `c9f4816e643fbd403fdecfd694caf054458d460c`
- TRA-Verdichtung/Punktprojektion v1: `a89537d2469e8aae893bc2002d3c4c20042cbf17`
- reale KM-Konsistenzprüfung: `011c4e27025800a5bfcc1600bf78a5bbf58adffb`, `3603f1ea907afe1d205f86c27b54288a18adf560`
- reale Fixture-Dokumentation: `f46dec73a4fb2261896dc5f8e5a4aa0b9f917065`
- konservativer GRA-KM-Sprung-Rebuild: `e4d1741caf9661d19faa6beda4493dee37cdaf57`, Router `cd0f6725266cdf3f0bb8a7acb9c2bc6e3a29599b`, Capability `30a4971cd1eff3c4914bfd47f5f49aaa049f6850`, Source-Check `8259ef53cd8676f26ed7a4528d0b8a7c21e4d138`
- erster TRA-Stationsraum-Merge: `827a9a2090a6ae0c2faed77bccfef2264bf6ebab`
- gehärtete Mapping-Vertrauenskette: `f1cd189b0a4afdca0c64ac86a0877c080f3df9d0`
- Punktprojektion v2 über gemappte sekundäre TRA: `25f7a35a3bb9cbe8eecb2069f4f774981c19c8de`
- Capability `survey_station_merge_v1`: `ff903b387bcdaf5e3d7b610d08fa745605d00de4`
- CI-Vertrag Stationsmerge/GRA: `a33669b3f342f0c0d179ee6eb5a22c79cbf0cf31`
- Source-/Regressionstest Multi-TRA: `5be63d57fa854f6d89fc14648d689acc10cb7862`, erweitert `8cb5dd522fe2b65c74995606b127ecb4201f1621`
- Projektion invalidiert abhängige Auto-GRA-Sprünge: `a662c821414885700d7c2e67155175cb22de4914`
- Projektdokumentation Multi-TRA/Abhängigkeiten: `4830d90acdfe8b9f8e132f335fec6f7939fcf8fb`, `aaa17a3b244cb2c844bf9731b710c228b163bd4c`
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

Resumierbarer sequenzieller Upload mit Offset-/Größen-/SHA-256-Prüfung, fsync, atomarem Abschluss, Datei-Verknüpfungen und Replay-Sicherheit ist vorhanden. Geräte-Bearer erzeugt `tb.files.source=APP`, Portal-Session `PORTAL`; bei Geräteauth enthält das Audit `actor_device_id`, aber keine Geheimwerte.

### Unveränderliche Vermessungsoriginale / Track-Arbeitsdaten

Originalimport prüft Größe + SHA-256 und legt atomar unter `reference/survey-originals/<uuid>/...` ab. Es gibt keine PATCH-/PUT-/DELETE-Endpunkte und kein SQL-Update/Delete für Originale. Veränderte Arbeitsdaten liegen separat in `tb.track_data` mit Optimistic Locking und Tombstones.

### Vermessungsparser / Parse-Katalog

Serverseitiger Parservertrag basiert auf den vorhandenen Android-Parsern:

- TRA: binäre ER0-Struktur, 0x48-Header, 78 Byte pro Knoten;
- GRA: 36-Byte-Header, 36 Byte pro Punkt;
- GVP/Festpunkte: GVP-Text, rohe KF 7.0 / 7.0 R mit 102-Byte-Datensätzen, KF.TXT und KV;
- KOR: nur die belegte `.kor.txt`-Variante; rohe/unbekannte `.kor` bleiben `UNSUPPORTED`;
- komplette Null-Stationen werden nicht als belastbare KM-Quelle verwendet;
- DB_REF/GK-Streifen 3 bleibt als Quellkoordinate erhalten; GPS ist nur abgeleitet.

Migration `003_survey_parse_catalog.sql` enthält `tb.survey_parse_catalog` als neu aufbaubaren Parse-Stand. Vor jedem Parse werden Dateigröße und SHA-256 des Originals erneut geprüft.

Parser v2 wurde gegen den vorhandenen realen Absteckungsbestand verifiziert. Nach SHA-256-Deduplizierung wurden 55 TRA, 45 GRA, 4 rohe KF, 1 KV, 2 KF.TXT, 1 GVP-Text und 2 KOR.TXT ohne Parserfehler gelesen; zwei rohe/unbekannte KOR bleiben wie vorgesehen `UNSUPPORTED`. Die rohe KF Strecke 5362 mit 75 Festpunkten wird direkt gelesen, Zone-3-Koordinaten bleiben `DB_REF_GK_3`.

Die Dateinamenmetadaten sind date-safe: vorangestellte ISO-Datumsbestandteile werden vor der Streckensuche maskiert. TRA/GRA übernehmen additiv die vorhandenen Android-Regeln für Strecke, Gleis, Quellrichtung und Achstyp.

Details: `docs/TB-SERVER_1_0_SURVEY_FIXTURE_VERIFICATION.md`.

### Vermessungsabschnitte / KM-Kalibrierung

Migration `004_survey_sections.sql` ergänzt die bestehenden `track_data`-Arbeitsdaten:

- `tb.track_data_sources`: mehrere unveränderliche Originalquellen je `SURVEY_SECTION`, Rolle/Reihenfolge/optional `reverse_geometry`;
- `tb.track_data_km_points`: `ORIGINAL_ANCHOR`, `MANUAL_ANCHOR`, `CONTROL_ANCHOR`, `KM_JUMP`.

Die dedizierte Fach-API nutzt `track.edit`, `X-Client-Change-Id`, Change Feed, Audit und Optimistic Locking. Der generische `track-data`-Create-/Update-Pfad darf `dataset_kind=SURVEY_SECTION` nicht schreiben.

Farbregel:

- belastbare automatische Originalanker -> `AUTOMATISCH/GRUEN`;
- manuelle 2-Punkt-Kalibrierung oder ein Anker plus bekannte Richtung -> `MANUELL/ORANGE`;
- Geometrie ohne belastbare oder mit widersprüchlicher KM-Kalibrierung -> `ROT`;
- keine Geometrie -> `GRAU`.

KM-Sprünge werden diskret berücksichtigt und nicht in den kontinuierlichen Faktor eingerechnet.

#### Konsistenzsperre aus realen Daten

Ein äußerer 2-Punkt-Fit allein reicht nicht. Reale 5302-Daten zeigen, dass bei einem nicht modellierten +73,660-m-Sprung der äußere Faktor noch grob plausibel wirken kann, während Zwischenanker um mehr als 60 m falsch liegen.

Darum werden alle beteiligten Anker gegen die resultierende KM-Funktion geprüft. Maximale zulässige Ankerabweichung: **5 m**. Darüber -> `UNPLAUSIBEL/ROT` mit Diagnose. Mit korrekt modelliertem KM-Sprung bleibt der kontinuierliche Faktor korrekt.

### TRA-Verdichtung / DB_REF / Punktprojektion v2

Die serverseitige TRA-Geometrie übernimmt die Android-Logik: 2,0-m-Samples, Gerade/Kreisbogen analytisch, Übergangsbogen mit 0,5-m-Integration, echte TRA-Knoten bleiben exakte Segmentenden.

Die vorhandene Android-Transformation DB_REF/GK -> ETRS89/WGS84 wurde mit denselben Helmertparametern portiert und erkennt GK-Streifen 3, 4 und 5.

`POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors` projiziert koordinatenhaltige `KM`-/`CONTROL`-Quellen auf die verdichteten TRA-Geometrien. Die Projektionslogik läuft jetzt als `survey-projection-v2`.

Automatischer `ORIGINAL_ANCHOR` erfordert:

- Quelle `KM`;
- bestätigte Strecke;
- Achsabstand <= 5 m;
- und einen **belastbaren gemeinsamen Stationsraum** der getroffenen TRA.

Die primäre TRA ist belastbar. Eine sekundäre TRA kann ebenfalls belastbar werden, wenn ihr Stationsraum über geometrisch bestätigte Überlappung mit zuverlässiger Vorgeschichte abgebildet wurde. Eine nur am Endpunkt fortgeschriebene TRA bleibt Kontrollquelle. Eine unmapped sekundäre TRA liefert gar keine Punktstation in den gemeinsamen Abschnittsstationsraum.

### Mehrere TRA-Quellen / Stationsraum-Merge

Neu ist `survey_station_merge.py` mit Capability `survey_station_merge_v1=true`.

Die lokale Station einer TRA wird ausschließlich als abgeleitete lineare Abbildung in einen gemeinsamen Stationsraum überführt:

`merged_station = station_sign * local_station + station_offset`.

Vor einer Zusammenführung müssen Strecke und zusätzlich Gleis oder Richtung zusammenpassen.

#### `OVERLAP_CONFIRMED`

Belastbare automatische Zusammenführung anhand vieler Geometriepunkte:

- 2-m-TRA-Geometrie, Fit-Raster ca. 10 m;
- Fit-Treffer max. 1,0 m geometrischer Abstand;
- mindestens 5 Treffer;
- mindestens 50 m Überlappung;
- maximal 0,5 m Stations-Fit-Restfehler;
- Vorwärts- und Rückwärtsstationierung werden geprüft.

Die Vertrauenskette wird mitgeführt. Ein Overlap, der nur auf einem zuvor unsicheren Endpunkt-Mapping aufbaut, wird nicht automatisch vertrauenswürdig. Wenn mehrere bereits gemappte Vorgänger passen, wird eine belastbare Überlappung bevorzugt.

#### `ENDPOINT_CONFIRMED`

Bei gleicher Achsidentität darf ein TRA-Anschluss bis 5 m Endpunktabstand diagnostisch in den gemeinsamen Stationsraum fortgeschrieben werden. Dieses Mapping besitzt aber `reliable_for_auto_anchor=false`, weil ein unbekannter Stationssprung am Übergang ohne Überlappung nicht ausgeschlossen werden kann. Treffer darauf werden höchstens Kontrollanker.

#### Reale Harburg -> 8771 Verifikation / Strecke 5300

Die hochgeladenen realen Projektdateien belegen, dass aufeinanderfolgende Bestände geometrisch dieselbe Achse überlappen können, aber unterschiedliche lokale Stationsnullpunkte verwenden.

**R-Achse**

- `2026-03-25_5300_050-053_R_ER0.TRA` -> `5300_054-059_DHDN_R.Tra`;
- 107 Fit-Treffer;
- Überlappung ca. 1058,226 m;
- `station_sign=+1`;
- Offset ca. **+1188,999027 m**;
- maximaler Stations-Restfehler ca. **0,000240 m**.

**S-Achse**

- `2025-04-25_5300_050-053_S_ER0.TRA` -> `5300_054-059_DHDN_S.Tra`;
- 163 Fit-Treffer;
- Überlappung ca. 1615,748 m;
- `station_sign=+1`;
- Offset ca. **-0,035731 m**;
- maximaler Stations-Restfehler ca. **0,000244 m**.

Das Mapping wird damit aus Geometrie bestimmt und nicht aus Dateinamen oder KM-Bereichen geraten.

Details: `docs/TB-SERVER_1_0_SURVEY_PROJECTION.md`.

### Reale Punktprojektions-/KM-Verifikation

**Gessertshausen 5302**

- rohe KF: 124 verwertbare Punkte mit Strecke/KM;
- 5302L: 46 Punkte <=5 m zur TRA, Median ca. 3,566 m, Maximum ca. 4,372 m;
- 5302R: 45 Punkte <=5 m, Median ca. 3,489 m, Maximum ca. 3,963 m;
- DB_REF-Direktprojektion vs. WGS84-Projektion: maximale Stationsdifferenz ca. 0,029 m;
- GRA-Sprung `17626,340 -> 17700,000` = +73,660 m;
- bestätigte Sprungstation L ca. 12986,913 m, R ca. 13025,898 m;
- mit Sprung: maximale Ankerabweichung L ca. 0,549 m, R ca. 0,396 m.

**8771 / Strecke 5300 R**

- rohe KF: 120 KM-Punkte;
- 119/120 liegen innerhalb 5 m einer der R/S-TRA;
- GRA-Sprung `55606,095653 -> 55600,000063` = ca. -6,095590 m;
- bestätigte Sprungstation R ca. 4305,411 m;
- mit Sprung maximale Ankerabweichung ca. 0,517 m.

**Harburg 5300**

- rohe KF: 86 plausible Punkte, KM-/Stationsspalte vollständig 0.00000;
- diese Datei bleibt zu Recht keine automatische KM-Quelle.

Details: `docs/TB-SERVER_1_0_SURVEY_REAL_FIXTURE_VERIFICATION.md`.

### Konservativer GRA-KM-Sprung-Rebuild

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Ein automatisch erzeugter `KM_JUMP` erfordert u. a. die reale GRA-Markierung `1000 -> 0` bei praktisch gleicher Höhe, bestätigte Achsidentität und projizierte Originalanker auf beiden Seiten; beide unabhängig ermittelten Sprungstationen dürfen höchstens 2 m auseinanderliegen. `allow_one_sided=false` bleibt der konservative Default. `reverse_geometry=true` der primären Geometrie wird derzeit nicht automatisch behandelt.

Lokal gegen reale Fixtures geprüft:

- 5302L: +73,660 m bei ca. 12986,913 m;
- 5302R: +73,660 m bei ca. 13025,898 m;
- 8771/5300R: -6,095590 m bei ca. 4305,411 m.

Capability: `survey_gra_jump_detection_v1=true`.

### Abhängige Ableitungen werden korrekt invalidiert

Ein automatisch erzeugter GRA-Sprung besitzt eine aus den aktuellen Ankern berechnete Station. Wenn `rebuild-auto-anchors` die TRA-Zuordnung oder den gemeinsamen Stationsraum ändert, ist diese Sprungstation potenziell veraltet.

Darum gilt jetzt:

- Projektions-Rebuild entfernt alte `survey-projection-v*`-Punkte;
- zusätzlich werden automatisch erzeugte `survey-gra-jump-v*`-Punkte entfernt;
- manuelle Anker und manuelle `KM_JUMP` bleiben erhalten;
- anschließend wird der Section-Payload neu kalibriert;
- danach muss `rebuild-gra-jumps` erneut laufen, bevor ein benötigter automatischer Sprung wieder wirksam wird.

Der sichere Rebuild-Ablauf ist damit: **Punktprojektion -> GRA-Sprung-Rebuild -> resultierenden Kalibrierstatus verwenden**. Ein zwischenzeitliches `ROT/UNPLAUSIBEL` nach der Projektion ist gewollt, wenn der Abschnitt einen noch nicht neu berechneten Sprung benötigt.

## Tests / Checks

Workflow-Zielstand:

- Migrationen 001 + 002 + 003 + 004;
- `latest_known=4`, `latest_applied=4`;
- Kerntabellen u. a. `survey_parse_catalog`, `track_data_sources`, `track_data_km_points`;
- Source-/Vertragschecks 20–32;
- Health/Capabilities/Auth-Checks;
- erwartete Capabilities enthalten jetzt auch `survey_station_merge_v1` und `survey_gra_jump_detection_v1`.

Lokal erfolgreich geprüft:

- Python-Syntax/AST der neuen/angepassten Vermessungsmodule;
- Raw-KF-Parität und Parser-v2-Realbestand;
- TRA-2-m-Verdichtung und DB_REF/GK->WGS84;
- automatische/manuelle KM-Kalibrierung inkl. KM-Sprung;
- All-Anchor-Konsistenzprüfung;
- GRA-Kandidatenfindung und beidseitige Sprungstationsschätzung;
- reale Harburg/8771-Überlappungsfits R/S;
- synthetischer Multi-TRA-Regressionsfall: primäre TRA + zuverlässig überlappende sekundäre TRA + nur per Endpunkt anschließende TRA;
- sekundäre Overlap-TRA kann einen automatischen `ORIGINAL_ANCHOR` im gemeinsamen Stationsraum liefern;
- Endpunkt-Mapping bleibt Kontrollanker;
- alte Projektionsversionen und automatisch erzeugte GRA-Sprünge werden als stationsabhängige Ableitungen erkannt.

**Noch nicht ausgeführt:** vollständige PostgreSQL-/FastAPI-End-to-End-Kette für Import -> Parse -> Section -> Projection v2 -> GRA-Sprung -> Kalibrierung. In der aktuellen Arbeitsumgebung stehen weder `postgres`/`psql` noch Docker/Podman zur Verfügung. Der GitHub-Runner fällt weiterhin vor dem ersten Step aus.

### Bekanntes GitHub-Actions-Problem

Die bisherigen TB-Server-Läufe enden weiterhin vor dem ersten Step mit `steps=[]` und `runner_id=0`. Das bedeutet: keine Source-Checks, keine Python-Läufe, keine PostgreSQL-Migrationen und keine API-Tests wurden dort tatsächlich ausgeführt. Ein solcher Lauf ist kein fachlicher Server-Testfehler.

## Bekannte/offene Punkte

- vollständige Import -> Parse -> Survey-Section -> Punktprojektion v2 -> GRA-Sprung-Rebuild -> Kalibrierung gegen eine echte PostgreSQL-/FastAPI-Testinstanz ausführen;
- weitere reale Mehr-TRA-Fälle prüfen, besonders `reverse_geometry`, echte rückläufige Stationierung und nicht überlappende Anschlussstücke;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz automatisieren; Default bleibt `false`;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne Originale zu verändern;
- rohe/unbekannte KOR nur bei belastbar belegtem Format ergänzen;
- Android-Fachclient auf Server-1.0-Endpunkte umstellen und Upload/Sync praktisch testen;
- echtes BTB-Abschluss-PDF + Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout, vollständige Portal-Fachoberfläche, Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität, Background Jobs, Backup/Restore/Reset;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Wenn eine PostgreSQL-Testinstanz verfügbar ist: gesamte Vermessungskette end-to-end gegen `/api/v1` ausführen; bis dahin Source-/Fixture-Regressionen weiter absichern.
2. Weitere reale Mehr-TRA-Richtungsfälle prüfen und `reverse_geometry`/rückläufige Stationierung nur bei belastbarer Evidenz erweitern.
3. Danach Android-Fachvertrag praktisch gegen TB-Server 1.0 testen und die App systematisch auf `/api/v1` umstellen.
4. Portal/PDF/Admin/Releases schließen.
5. Backup/Restore/Reset + E2E testen.
6. Erst danach NAS-Cutover und produktive Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
