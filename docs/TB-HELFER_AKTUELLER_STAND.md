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
- reale KM-Konsistenzprüfung: `011c4e27025800a5bfcc1600bf78a5bbf58adffb`, `3603f1ea907afe1d205f86c27b54288a18adf560`
- reale Fixture-Dokumentation: `f46dec73a4fb2261896dc5f8e5a4aa0b9f917065`
- konservativer GRA-KM-Sprung-Rebuild: `e4d1741caf9661d19faa6beda4493dee37cdaf57`, Router `cd0f6725266cdf3f0bb8a7acb9c2bc6e3a29599b`, Capability `30a4971cd1eff3c4914bfd47f5f49aaa049f6850`, Source-Check `8259ef53cd8676f26ed7a4528d0b8a7c21e4d138`
- Projektdokumentation GRA/Projektion: `2df1e9fd69a0b61f61233f81070a8229e40c80ed`, `e588e03580ed95f702d8c3de194f4d4c4fbdca9b`
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

Migration `003_survey_parse_catalog.sql` enthält `tb.survey_parse_catalog` als neu aufbaubaren Parse-Stand mit Parserfamilie/-version, Quell-SHA-256, `PARSED|UNSUPPORTED|ERROR`, Format, normalisiertem JSON, Warnungen und Fehlerdiagnose. Vor jedem Parse werden Dateigröße und SHA-256 des Originals erneut geprüft.

Parser v2 wurde gegen den vorhandenen realen Absteckungsbestand verifiziert. Nach SHA-256-Deduplizierung wurden 55 TRA, 45 GRA, 4 rohe KF, 1 KV, 2 KF.TXT, 1 GVP-Text und 2 KOR.TXT ohne Parserfehler gelesen; zwei rohe/unbekannte KOR bleiben wie vorgesehen `UNSUPPORTED`. Die rohe KF Strecke 5362 mit 75 Festpunkten wird ebenfalls direkt gelesen, Zone-3-Koordinaten bleiben `DB_REF_GK_3`.

Die Dateinamenmetadaten sind date-safe: vorangestellte ISO-Datumsbestandteile wie `2025-04-25_...` oder `2026-03-25_...` werden vor der Streckensuche maskiert. TRA/GRA übernehmen additiv die bereits vorhandenen Android-Regeln für Strecke, Gleis, Quellrichtung und Achstyp.

Details: `docs/TB-SERVER_1_0_SURVEY_FIXTURE_VERIFICATION.md`.

### Vermessungsabschnitte / KM-Kalibrierung

Migration `004_survey_sections.sql` ergänzt die bestehenden `track_data`-Arbeitsdaten:

- `tb.track_data_sources`: mehrere unveränderliche Originalquellen je `SURVEY_SECTION`, Rolle/Reihenfolge/optional `reverse_geometry`;
- `tb.track_data_km_points`: `ORIGINAL_ANCHOR`, `MANUAL_ANCHOR`, `CONTROL_ANCHOR`, `KM_JUMP`.

Die dedizierte Fach-API nutzt `track.edit`, `X-Client-Change-Id`, Change Feed, Audit und Optimistic Locking. Der generische `track-data`-Create-/Update-Pfad darf `dataset_kind=SURVEY_SECTION` nicht schreiben, damit Quellen-/Anker-/Kalibrierregeln nicht umgangen werden können.

Farbregel:

- belastbare automatische Originalanker -> `AUTOMATISCH/GRUEN`;
- manuelle 2-Punkt-Kalibrierung oder ein Anker plus bekannte Richtung -> `MANUELL/ORANGE`;
- Geometrie ohne belastbare oder mit widersprüchlicher KM-Kalibrierung -> `ROT`;
- keine Geometrie -> `GRAU`.

KM-Sprünge werden diskret berücksichtigt und nicht in den kontinuierlichen Faktor eingerechnet.

#### Neue Konsistenzsperre aus realen Daten

Die bisherige reine Prüfung des äußersten 2-Punkt-Paars war fachlich zu schwach. Reale 5302-Daten zeigen, dass bei einem nicht modellierten +73,660-m-Sprung der äußere Faktor trotzdem noch im groben Bereich 0,5..1,5 liegen kann, während Zwischenanker um mehr als 60 m falsch sind.

Darum gilt jetzt zusätzlich:

- alle für die automatische/manuelle 2-Punkt-Kalibrierung beteiligten Anker werden gegen die resultierende KM-Funktion geprüft;
- maximale zulässige Ankerabweichung: **5 m**;
- darüber -> `UNPLAUSIBEL/ROT` mit Diagnose `max_anchor_residual_m` und schlechtestem Anker;
- mit korrekt modelliertem KM-Sprung bleibt der kontinuierliche Faktor korrekt.

`31_verify_tb_server_1_survey_sections_source.py` enthält den destillierten realen 5302L-Regressionstest: ohne Sprung rot (>60 m Restfehler), mit +73,660 m Sprung grün und <1 m Restfehler.

### TRA-Verdichtung / DB_REF / automatische Punktprojektion

Die serverseitige TRA-Geometrie übernimmt die Android-Logik: 2,0-m-Samples, Gerade/Kreisbogen analytisch, Übergangsbogen mit 0,5-m-Integration, echte TRA-Knoten bleiben exakte Segmentenden.

Die bestehende Android-Transformation DB_REF/GK -> ETRS89/WGS84 wurde mit denselben Helmertparametern portiert und erkennt GK-Streifen 3, 4 und 5.

`POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors` projiziert koordinatenhaltige `KM`-/`CONTROL`-Quellen auf die verdichtete TRA. `ORIGINAL_ANCHOR` entsteht nur bei bestätigter Strecke, primärer TRA und Achsabstand <= 5 m; sonstige Treffer bis 15 m bleiben höchstens Kontrollanker. GRA enthält aktuell kein X/Y und erzeugt über diesen Rebuild keine Originalanker.

### Reale Projektionsverifikation

Die hochgeladenen realen Bestände wurden nicht nur synthetisch, sondern direkt gegen die aktuelle Servergeometrie analysiert:

**Gessertshausen 5302**

- rohe KF: 124 verwertbare Punkte mit Strecke/KM;
- 5302L: 46 Punkte <=5 m zur TRA, Median ca. 3,566 m, Maximum ca. 4,372 m;
- 5302R: 45 Punkte <=5 m, Median ca. 3,489 m, Maximum ca. 3,963 m;
- DB_REF-Direktprojektion vs. WGS84-Projektion: maximale Stationsdifferenz in der Prüfung ca. 0,029 m;
- GRA-Sprung `17626,340 -> 17700,000` = +73,660 m;
- bestätigte Sprungstation L ca. 12986,913 m, beidseitige Schätzung differiert ca. 0,001 m;
- bestätigte Sprungstation R ca. 13025,898 m, Differenz <0,001 m;
- mit Sprung: L max. Ankerabweichung ca. 0,549 m, R ca. 0,396 m.

**8771 / Strecke 5300 R**

- rohe KF: 120 KM-Punkte;
- 119/120 liegen innerhalb 5 m einer der zugehörigen R/S-TRA;
- GRA-Sprung `55606,095653 -> 55600,000063` = ca. -6,095590 m;
- bestätigte Sprungstation R ca. 4305,411 m;
- beidseitige Stationsschätzungen differieren ca. 0,403 m;
- mit Sprung maximale Ankerabweichung ca. 0,517 m.

**Harburg 5300**

- rohe KF: 86 plausible Punkte, aber KM-/Stationsspalte vollständig 0.00000;
- diese Datei bleibt zu Recht keine automatische KM-Quelle.

Details: `docs/TB-SERVER_1_0_SURVEY_REAL_FIXTURE_VERIFICATION.md`.

### Konservativer GRA-KM-Sprung-Rebuild

In mehreren realen GRA-Dateien wurde ein wiederkehrendes Projektmuster an KM-Sprüngen belegt: zwei aufeinanderfolgende KM-Werte bei gleicher Höhe, Parameter ungefähr `1000 -> 0`. Beispiele sind +2,120 m und +73,660 m auf 5302, -6,095590 m auf 5300 und weitere positive/negative Sprünge auf 5504. Das wird nicht als allgemeine externe Formatspezifikation behauptet.

Neu ist:

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Automatisch erzeugt der Rebuild einen `KM_JUMP` nur wenn:

- Quelle als `KM` verknüpft und `GRA_BINARY` geparst ist;
- GRA-Markierung `1000 -> 0` bei praktisch identischer Höhe vorliegt;
- Strecke bestätigt ist;
- zusätzlich Gleis oder Richtung zur primären TRA bestätigt ist;
- projizierte `ORIGINAL_ANCHOR` vorhanden sind;
- Sprungstation aus Ankern **vor und nach** dem Sprung unabhängig bestimmt werden kann;
- beide Stationsschätzungen höchstens 2 m auseinanderliegen.

`allow_one_sided` existiert, ist aber standardmäßig **false**. Dadurch wird z. B. der frühe 5302-Sprung +2,120 m ohne Anker auf beiden Seiten nicht automatisch übernommen. `reverse_geometry=true` der primären Geometrie wird derzeit ausdrücklich nicht automatisch behandelt.

Der Rebuild ersetzt nur eigene `survey-gra-jump-v*`-Punkte, erhält manuelle Sprünge und verhindert einen gleichwertigen automatischen Doppelpunkt. Capability: `survey_gra_jump_detection_v1=true`.

Lokal gegen reale Fixtures geprüft:

- 5302L: 2 Kandidaten, 1 beidseitig bestätigt -> +73,660 m bei ca. 12986,913 m;
- 5302R: 2 Kandidaten, 1 bestätigt -> +73,660 m bei ca. 13025,898 m;
- 8771/5300R: 1 Kandidat -> -6,095590 m bei ca. 4305,411 m.

`32_verify_tb_server_1_survey_projection_source.py` prüft zusätzlich den konservativen Default `allow_one_sided=false`, die reale +73,660-m-Kandidatenlogik und die beidseitige Stationsplatzierung.

## Tests / Checks

Workflow-Zielstand:

- Migrationen 001 + 002 + 003 + 004;
- `latest_known=4`, `latest_applied=4`;
- Kerntabellen u. a. `survey_parse_catalog`, `track_data_sources`, `track_data_km_points`;
- Source-/Vertragschecks 20–32;
- Health/Capabilities/Auth-Checks.

Lokal erfolgreich geprüft:

- Python-Syntax/AST der neuen/angepassten Vermessungsmodule;
- Raw-KF-Parität und Parser-v2-Realbestand;
- TRA-2-m-Verdichtung und DB_REF/GK->WGS84;
- automatische/manuelle KM-Kalibrierung inkl. KM-Sprung;
- neue All-Anchor-Konsistenzprüfung;
- GRA-Kandidatenfindung an realen Dateien;
- beidseitige Sprungstationsschätzung für 5302 L/R und 8771/5300 R;
- vollständiger reine-Logik-Builder gegen diese drei realen Fixture-Kombinationen mit `allow_one_sided=false`.

**Noch nicht ausgeführt:** vollständige PostgreSQL-/FastAPI-End-to-End-Kette für die neuen GRA-Rebuild-Endpunkte. Das ist wegen des weiterhin ausfallenden GitHub-Runners noch lokal/auf einer geeigneten Testinstanz nachzuholen.

### Bekanntes GitHub-Actions-Problem

Auch TB-Server-Lauf **#97** für Commit `8259ef53cd8676f26ed7a4528d0b8a7c21e4d138` wurde **vor dem ersten Step** beendet: Job `foundation`, `steps=[]`, `runner_id=0`, kein Runnername. Es wurden daher weder die Source-Checks noch Python/PostgreSQL/API-Tests ausgeführt. Lokale Prüfungen dürfen weiterhin nicht als GitHub-Runner-Erfolg bezeichnet werden.

## Bekannte/offene Punkte

- vollständige Import -> Parse -> Survey-Section -> Punktprojektion -> GRA-Sprung-Rebuild -> Kalibrierung gegen PostgreSQL/API testen;
- robuste Stationsraum-/Überlappungsverschmelzung mehrerer TRA-Quellen;
- `reverse_geometry` auch für GRA-Sprung-Vorher/Nachher erst nach belastbarer Logik unterstützen;
- einseitig belegte Sprünge nur bei zusätzlicher realer Evidenz automatisieren; Default bleibt `false`;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne Originale zu verändern;
- rohe/unbekannte KOR nur bei belastbar belegtem Format ergänzen;
- Android-Fachclient auf Server-1.0-Endpunkte umstellen und Upload/Sync praktisch testen;
- echtes BTB-Abschluss-PDF + Nachbearbeitungsmodell;
- Firmenlogo/PDF-Layout, vollständige Portal-Fachoberfläche, Admin-Dashboard/Systemstatus/Speicher;
- Releases/Client-Kompatibilität, Background Jobs, Backup/Restore/Reset;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Die neue Vermessungskette auf einer echten PostgreSQL-Testinstanz/API end-to-end ausführen, sobald ein Runner/Testserver verfügbar ist; bis dahin lokale Source-/Fixture-Checks weiter nutzen.
2. Robuste Stationsraum-/Überlappungsverschmelzung mehrerer TRA-Quellen implementieren.
3. Danach Android-Fachvertrag praktisch gegen TB-Server 1.0 testen und die App systematisch auf `/api/v1` umstellen.
4. Portal/PDF/Admin/Releases schließen.
5. Backup/Restore/Reset + E2E testen.
6. Erst danach NAS-Cutover und produktive Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**
