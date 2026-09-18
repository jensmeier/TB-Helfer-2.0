# TB-Helfer – aktueller Projektstand

Stand: 2026-09-18

Maßgeblich sind aktueller GitHub-Code im aktiven Branch, `docs/TB-HELFER_PROJEKTREGELN.md`, diese Datei, `docs/TB-SERVER_1_0_ZIELMODELL.md` und `docs/TB-SERVER_1_0_CUTOVER.md`.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- PR-Basis: `feature/admin-offline-sync`
- Android bleibt vorerst Testclient: Buildkandidat **200050**
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

Wichtige Server-1.0-Meilensteine:

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
- kontinuierliche TRA-Chainage: `6441b408a5953e603b7078e4d5adb3daf43bae41`
- Stationsraum-Merge v2 auf kontinuierlicher Chainage: `37f4c87f43cbc10e133e4cbc3870ac04a614027d`
- Punktprojektion v3 auf kontinuierlicher Chainage: `a030054e6c0db7c26039270b2030b68de8c31161`
- GRA-Sprung v2 auf aktuellem Chainage-/Projektionsstand: `2f11d2d14276cc91f02bb1bf5ab30cc8f3cb6714`
- Regression positive/negative TRA-Stationsdiskontinuitäten: `69effc49acf55b4b9abc6d4136b089a784853540`
- Detaildokumentation Chainage/Projektion: `e2701359efca8ff50220c452633b44ba7dba7c0d`
- reale Typ-6-/Nullweg-Verifikation dokumentiert: `21c192ed4ad717e3d1f85fca31f85c6b398d2e49`
- Release-Katalog / Client-Kompatibilitätsvertrag: in diesem Stand umgesetzt; öffentliche Statusabfrage + ADMIN-Verwaltung, noch ohne serverseitigen Binär-Upload
- BTB-Abschluss-PDF / Ein-PDF-/Original-/Nachbearbeitungsmodell: in diesem Stand serverseitig umgesetzt; stabiler PDF-Pfad pro `btbId`, unveränderlicher erster Original-Snapshot inkl. Foto-Basis, Reaktivierung ohne automatische Ungültigkeit
- Zentrales Firmenprofil / Firmenlogo / PDF-Logo-Layout: in diesem Stand auf die vorhandenen Server-1.0-Firmen-/Dateifelder umgesetzt; Android-UUID-Kompatibilität korrigiert, PDF-Renderer auf v2 mit zentralem Branding angehoben
- erste native Server-1.0-Portal-/Admin-Weboberfläche: Login/Portal-Start, Firmenliste/-anlage sowie Firmenprofil/Logo/PDF-Layout mit CSRF-geschützter API-Anbindung und Browser-Vorschau

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

KM-Sprünge werden diskret berücksichtigt und nicht in den kontinuierlichen Faktor eingerechnet. Alle beteiligten Anker werden gegen die resultierende KM-Funktion geprüft; maximale zulässige Ankerabweichung ist 5 m. Darüber -> `UNPLAUSIBEL/ROT`.

### TRA-Rohgeometrie / kontinuierliche geometrische Chainage

Die Rohgeometrie bleibt Android-parallel: 2,0-m-Samples, Gerade/Kreisbogen analytisch, Übergangsbogen mit 0,5-m-Integration.

Neu ist eine getrennte **abgeleitete** Chainage-Schicht `survey_tra_chainage.py`. Reale TRA-Dateien enthalten Nullweg-Elemente mit `raw_type=6`, praktisch identischer Start-/Endkoordinate und diskretem Stationssprung. Diese Werte dürfen nicht als zusätzliche geometrische Gleislänge verdichtet werden.

Eine Diskontinuität wird nur erkannt, wenn gleichzeitig gilt:

- `raw_type=6`;
- Sprungbetrag >= 0,001 m;
- geometrische Sehne <= 0,01 m;
- Stationssprung und gespeicherte Elementlänge stimmen bis 0,01 m überein.

Der Parserpayload bleibt unverändert. Nur für Berechnungen wird der kumulierte Sprung aus allen folgenden Stationen entfernt und das Nullwegelement auf abgeleitete Länge 0 gesetzt. Diese Stationsbasis heißt `CONTINUOUS_GEOMETRY_CHAINAGE`.

Reale Gegenprüfung des deduplizierten Bestands:

- 55 unterschiedliche TRA-Dateien;
- 8 belegte Typ-6-/Nullweg-Sprünge in 4 unterschiedlichen Dateien;
- Beispiele: 5302 `+2,120`, `+73,660`, `+19,3963`, `-2,0221 m`; 5300/8771 `-6,09559 m`; 5504 `+6,351` und `-28,948 m`; 5524 `-12,562482 m`;
- mehrere Werte stimmen unabhängig mit realen GRA-Sprungpaaren überein.

Details: `docs/TB-SERVER_1_0_SURVEY_REAL_FIXTURE_VERIFICATION.md` und `docs/TB-SERVER_1_0_SURVEY_PROJECTION.md`.

### DB_REF / Punktprojektion v3

Die vorhandene Android-Transformation DB_REF/GK -> ETRS89/WGS84 wurde mit denselben Helmertparametern portiert und erkennt GK-Streifen 3, 4 und 5.

`POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors` projiziert koordinatenhaltige `KM`-/`CONTROL`-Quellen auf die verdichteten TRA-Geometrien. Die Projektionslogik läuft jetzt als **`survey-projection-v3`** und verwendet ausschließlich `CONTINUOUS_GEOMETRY_CHAINAGE`.

Automatischer `ORIGINAL_ANCHOR` erfordert:

- Quelle `KM`;
- bestätigte Strecke;
- Achsabstand <= 5 m;
- und einen belastbaren gemeinsamen Stationsraum der getroffenen TRA.

Alte `survey-projection-v*`-Punkte und automatisch erzeugte `survey-gra-jump-v*`-Punkte werden beim Projektions-Rebuild entfernt. Manuelle Anker und manuelle KM-Sprünge bleiben erhalten.

### Mehrere TRA-Quellen / Stationsraum-Merge v2

`survey_station_merge.py` läuft jetzt als **`survey-station-merge-v2`** und verwendet kontinuierliche geometrische Chainage.

Die lokale geometrische Chainage einer TRA wird als abgeleitete lineare Abbildung in einen gemeinsamen Stationsraum überführt:

`merged_station = station_sign * local_chainage + station_offset`.

Vor einer Zusammenführung müssen Strecke und zusätzlich Gleis oder Richtung zusammenpassen.

`OVERLAP_CONFIRMED` ist belastbar bei:

- Fit-Treffer max. 1,0 m geometrischer Abstand;
- mindestens 5 Treffer;
- mindestens 50 m Überlappung;
- maximal 0,5 m Stations-Fit-Restfehler;
- geprüfter Vorwärts-/Rückwärtsabbildung;
- belastbarer Mapping-Vorgeschichte.

`ENDPOINT_CONFIRMED` bis 5 m Endpunktabstand ist nur diagnostisch und besitzt `reliable_for_auto_anchor=false`.

Reale Harburg -> 8771 Verifikation / Strecke 5300 bleibt stabil:

- R: 107 Fit-Treffer, ca. 1058,226 m Überlappung, Offset ca. `+1188,999027 m`, max. Restfehler ca. `0,000240 m`;
- S: 163 Fit-Treffer, ca. 1615,748 m Überlappung, Offset ca. `-0,035731 m`, max. Restfehler ca. `0,000244 m`.

Der reale `-6,09559-m`-Typ-6-Sprung im 8771-S-Bestand liegt außerhalb der Harburg-Überlappung. Die Überlappung bleibt numerisch stabil, während die weitere Stationierung nach dem Sprung jetzt korrekt kontinuierlich bleibt.

### Konservativer GRA-KM-Sprung-Rebuild v2

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Das reale GRA-Muster `Parameter 1000 -> 0` bei praktisch gleicher Höhe bleibt nur Kandidat; automatische Übernahme erfordert weiterhin bestätigte Achsidentität und geometrisch projizierte Originalanker auf beiden Seiten. Beide unabhängig ermittelten Sprungstationen dürfen höchstens 2 m auseinanderliegen. `allow_one_sided=false` bleibt Default.

Seit **`survey-gra-jump-v2`** muss die Section zusätzlich eine aktuelle `survey-projection-v3` mit `station_basis=CONTINUOUS_GEOMETRY_CHAINAGE` besitzen. Dadurch wird verhindert, dass ein Sprung gegen eine alte diskontinuierliche TRA-Quellstation oder eine veraltete Projektion berechnet wird.

`reverse_geometry=true` der primären Geometrie wird weiterhin konservativ nicht automatisch behandelt.

### Reale Punktprojektions-/KM-Verifikation

Gessertshausen 5302:

- rohe KF: 124 verwertbare Punkte mit Strecke/KM;
- 5302L: 46 Punkte <=5 m zur TRA, Median ca. 3,566 m, Maximum ca. 4,372 m;
- 5302R: 45 Punkte <=5 m, Median ca. 3,489 m, Maximum ca. 3,963 m;
- DB_REF-Direktprojektion vs. WGS84-Projektion: maximale Stationsdifferenz ca. 0,029 m;
- GRA-Sprung `17626,340 -> 17700,000` = +73,660 m.

8771 / Strecke 5300 R:

- rohe KF: 120 KM-Punkte;
- 119/120 liegen innerhalb 5 m einer der R/S-TRA;
- GRA-Sprung `55606,095653 -> 55600,000063` = ca. -6,095590 m.

Harburg 5300:

- rohe KF: 86 plausible Punkte, KM-/Stationsspalte vollständig 0.00000;
- diese Datei bleibt zu Recht keine automatische KM-Quelle.

Historische Sprungstationszahlen aus der vorherigen Projektionsbasis sind in der Real-Fixture-Dokumentation als solche gekennzeichnet. Nach v3 werden Sprungstationen neu gegen kontinuierliche Chainage berechnet; alte Auto-GRA-Punkte werden invalidiert.

### BTB-Abschluss-PDF / Ein-PDF-/Originalmodell

Der Server setzt jetzt die verbindliche BTB-Regel um, dass eine `btbId` höchstens ein aktuell gültiges Abschluss-PDF besitzt.

- erster erfolgreicher Abschluss speichert den unveränderlichen Original-Snapshot;
- der Original-Snapshot enthält auch den beim ersten Abschluss tatsächlich zugeordneten Foto-Bestand;
- Reaktivieren eines abgeschlossenen BTB setzt nur den Team-Zeiger wieder auf dieselbe `btbId`; Status und PDF bleiben zunächst gültig;
- erst eine tatsächlich gespeicherte Kopf-/Fach-/Fotoänderung setzt den BTB auf `REWORK` und invalidiert das bisherige Abschluss-PDF;
- erneuter Abschluss ersetzt dasselbe stabile PDF derselben `btbId`, niemals das Original;
- fachlich neue/geänderte Einträge und neue/geänderte Fotos werden gegenüber dem ersten Original rot dargestellt; entfernte Originaleinträge/-fotos erscheinen rot als `(entfernt)`;
- Datum/Arbeitszeit und die übrigen Kopfdaten bleiben im PDF schwarz;
- wenn ein reaktivierter abgeschlossener BTB ohne Änderung wieder abgeschlossen wird, wird nur `active_btb_id` gelöst; das bestehende gültige PDF wird nicht neu erzeugt;
- beim vollständigen BTB-Löschen werden Original-Snapshot und BTB-PDF entfernt, echte Baustellenfotos bleiben erhalten und werden lediglich von der `btbId` gelöst;
- neue BTB-Fotos über den bestehenden Uploadpfad invalidieren ein gültiges Abschluss-PDF ebenfalls erst bei der tatsächlichen neuen Bildzuordnung.

Servermodule:

- `server/tbserver/btb_documents.py` – stabiler Storage-Key, Snapshot-/Foto-Helfer, atomarer PDF-Tausch und Rollback;
- `server/tbserver/btb_pdf.py` – ReportLab-Renderer mit Original-Diff;
- `server/tbserver/btb.py` – Abschluss, Reaktivierung, Download und Foto-Lösen;
- `server/tbserver/files.py` – BTB-Fotoänderungen an denselben Rework-Vertrag angebunden.

Capability: `btb_finalize_with_pdf_v1=true`.

Details: `docs/TB-SERVER_1_0_BTB_PDF.md`.

### Firmenprofil / Firmenlogo / PDF-Logo-Layout

TB-Server 1.0 nutzt für die zentralen Firmendaten bewusst die bereits in Migration 001 vorhandenen Felder `tb.companies.settings`, `pdf_layout` und `logo_file_id`; eine parallele Legacy-`company_profiles`-Tabelle oder neue Migration ist dafür nicht nötig.

Mobile/Allgemein:

- `GET /api/v1/company/profile`;
- `GET /api/v1/company/profile/logo`;
- `GET /api/v1/company/profile/pdf-logo-layout`;
- `PUT /api/v1/company/profile/pdf-logo-layout`.

Admin:

- `GET /api/v1/admin/companies/{company_id}/profile`;
- `PUT /api/v1/admin/companies/{company_id}/profile`;
- `PUT /api/v1/admin/companies/{company_id}/logo`;
- `DELETE /api/v1/admin/companies/{company_id}/logo`.

Logo-Regeln:

- PNG/JPEG/WEBP nur per Magic-Byte-Erkennung;
- maximal 5 MiB;
- stabiler Storage-Key `companies/<company_uuid>/profile/logo`;
- SHA-256 und MIME serverseitig ermittelt;
- temporärer Dateitausch + Rollback bei DB-Fehlern;
- Geräte laden das Logo derselben Firma und cachen lokal.

Das bestehende firmenweite Android-PDF-Layout bleibt kompatibel: Breite 40–250 pt, X -120..120, Y -60..180, Anzeigen von Name/E-Mail/Telefon und `contacts_in_image`, jeweils mit monotoner `layout_version`.

Der Server-BTB-PDF-Renderer läuft dafür jetzt als `tb-server-btb-pdf-2` und verwendet Firmenlogo/Layout direkt aus der Firma der Baustelle. Firmenkopf/Kopfdaten bleiben unabhängig vom fachlichen Original-Diff schwarz.

Capabilities:

- `company_profile_v1=true`;
- `company_logo_v1=true`;
- `company_pdf_logo_layout_v1=true`.

Android `TeamManagementApi.CompanyProfile.companyId` wurde auf `String` umgestellt, weil TB-Server 1.0 Firmen-UUIDs liefert; alte numerische IDs bleiben als String lesbar.

Server-1.0-Weboberfläche auf demselben Fachvertrag:

- `GET /portal/v1/login` und `GET /portal/v1`;
- `GET /admin/v1/companies`;
- `GET /admin/v1/companies/{company_id}/branding`.

Die Admin-Seite kann Firmen anlegen, Stammdaten pflegen, Logo hochladen/löschen, PDF-Logo-Layout ändern und den Firmenkopf im Browser vorab positionieren. Schreibzugriffe laufen ausschließlich über Portal-Session + `X-CSRF-Token`; Geräte-/Bearer-Geheimnisse werden nicht ins HTML geschrieben.

Capability zusätzlich: `company_branding_admin_ui_v1=true`.

Details: `docs/TB-SERVER_1_0_COMPANY_BRANDING.md`.

### Releases / Client-Kompatibilität

Der bereits in Migration `001_server_1_0_base.sql` vorhandene Release-Katalog wird jetzt als stabiler API-Vertrag genutzt; bestehende Migrationen bleiben unverändert.

Öffentliche Endpunkte:

- `GET /api/v1/client-compatibility`;
- `GET /api/v1/releases/{platform}/latest`.

Administrative Release-Verwaltung:

- `GET /api/v1/admin/releases`;
- `POST /api/v1/admin/releases`;
- `PATCH /api/v1/admin/releases/{release_id}`;
- `POST /api/v1/admin/releases/{release_id}/publish`;
- `POST /api/v1/admin/releases/{release_id}/retire`.

Unterstützt werden `ANDROID`, `IOS` und `WINDOWS`. Der Kompatibilitätsvertrag unterscheidet `NO_PUBLISHED_RELEASE`, `UNKNOWN_BUILD`, `SUPPORTED_CURRENT`, `SUPPORTED_UPDATE_AVAILABLE`, `UPDATE_REQUIRED` und `UNSUPPORTED_BUILD`. Damit muss ein Client nicht mehr aus Serverversionsstrings oder PostgreSQL-Schemanummern raten.

Veröffentlichte Release-Metadaten sind unveränderlich; Änderungen erfolgen über einen neuen Draft/Release. Für Android ist beim Veröffentlichen eine Buildnummer Pflicht. `minimum_supported_build` darf nicht größer als die Release-Buildnummer sein. Öffentliche Antworten geben keine internen `storage_key`-/SHA-/Serverpfade aus.

Capabilities:

- `release_catalog_v1=true`;
- `client_compatibility_v1=true`.

Der aktuelle Baustein verwaltet Metadaten und externe Downloadziele. Serverseitiger APK-/Binär-Upload und -Download bleiben ein eigener späterer Capability-Schritt.

Details: `docs/TB-SERVER_1_0_RELEASES.md`.

## Tests / Checks

Workflow-Zielstand:

- Migrationen 001 + 002 + 003 + 004;
- `latest_known=4`, `latest_applied=4`;
- Kerntabellen u. a. `survey_parse_catalog`, `track_data_sources`, `track_data_km_points`;
- Source-/Vertragschecks 20–36;
- Health/Capabilities/Auth-Checks;
- erwartete Capabilities enthalten zusätzlich `survey_station_merge_v1`, `survey_gra_jump_detection_v1`, `release_catalog_v1`, `client_compatibility_v1`, `btb_finalize_with_pdf_v1`, `company_profile_v1`, `company_logo_v1` und `company_pdf_logo_layout_v1` und `company_branding_admin_ui_v1`.

Lokal bzw. gegen bereitgestellte Fixtures erfolgreich geprüft:

- Python-Syntax/AST der neuen/angepassten Vermessungsmodule im aufgebauten Source-Check;
- Raw-KF-Parität und Parser-v2-Realbestand;
- TRA-2-m-Rohverdichtung und DB_REF/GK->WGS84;
- automatische/manuelle KM-Kalibrierung inkl. KM-Sprung;
- All-Anchor-Konsistenzprüfung;
- GRA-Kandidatenfindung und beidseitige Sprungstationsschätzung;
- reale Harburg/8771-Überlappungsfits R/S;
- synthetischer Multi-TRA-Regressionsfall: primäre TRA + zuverlässig überlappende sekundäre TRA + nur per Endpunkt anschließende TRA;
- sekundäre Overlap-TRA kann einen automatischen `ORIGINAL_ANCHOR` im gemeinsamen Stationsraum liefern;
- Endpunkt-Mapping bleibt Kontrollanker;
- alte Projektionsversionen und automatisch erzeugte GRA-Sprünge werden als stationsabhängige Ableitungen erkannt;
- positive Typ-6-Diskontinuität `+73,660 m` wird ohne künstliche stationäre 2-m-Samples normalisiert;
- negative Typ-6-Diskontinuität `-6,09559 m` wird korrekt normalisiert;
- kompletter realer TRA-Bestand lokal erneut gescannt: 57 physische TRA-Dateien, 55 unterschiedliche SHA-256; 9 physische Typ-6-Fundstellen / 8 nach Deduplizierung; keine TRA-Parsefehler in dieser Gegenprüfung.

- Source-Check 33 inkl. reiner Statuslogik für aktuellen, optional veralteten, verpflichtend veralteten und nicht mehr unterstützten Client-Build.
- BTB-PDF-Renderer v1 lokal mit ReportLab erzeugt, als PNG gerendert und visuell geprüft: schwarzer Kopf, rote geänderte Fachinhalte und rote `(entfernt)`-Originalinhalte.
- Source-Check 35 ist als Syntax-/Vertragsprüfung für Firmenprofil, Logo, Layout, PDF-Branding, Capabilities und Android-UUID-Vertrag vorbereitet.
- Source-Check 36 ist als Syntax-/Vertragsprüfung für Portal-/Admin-Webrouten, Templates, CSRF und Branding-UI vorbereitet.

**Noch nicht ausgeführt:** Source-Checks 34/35/36 gegen den vollständigen neuen Branding-/Admin-Web-Branchstand (CI-Runner führt weiterhin keine Steps aus) sowie die vollständige PostgreSQL-/FastAPI-End-to-End-Kette für Import -> Parse -> Section -> Projection v3 -> GRA-Sprung v2 -> Kalibrierung. In der aktuellen Arbeitsumgebung stehen weder `postgres`/`psql` noch Docker/Podman zur Verfügung.

### Bekanntes GitHub-Actions-Problem

Für den aktuellen Fachcommit `4b073b6444cd9c2623a1e91944fe374f66c02111` endete der TB-Server-Lauf **#136 / ID 35288425467** mit `foundation` = failure; der Job meldet weiterhin `steps = null`. Auch Android CI **#923 / ID 35288425491** endete ohne ausgeführte Steps. Damit wurden für diesen Commit weder Source-Check 34 noch Python-, PostgreSQL-, API- oder Android-Build-Schritte tatsächlich ausgeführt. Das ist weiterhin das bekannte Runner-/Actions-Infrastrukturproblem und kein festgestellter fachlicher Server-/Android-Testfehler.

## Bekannte/offene Punkte

- vollständige Import -> Parse -> Survey-Section -> Punktprojektion v3 -> GRA-Sprung v2 -> Kalibrierung gegen eine echte PostgreSQL-/FastAPI-Testinstanz ausführen;
- weitere reale Mehr-TRA-Fälle prüfen, besonders `reverse_geometry`, echte rückläufige Stationierung und nicht überlappende Anschlussstücke;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz automatisieren; Default bleibt `false`;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne Originale zu verändern;
- rohe/unbekannte KOR nur bei belastbar belegtem Format ergänzen;
- Android-Fachclient auf Server-1.0-Endpunkte umstellen und Upload/Sync praktisch testen;
- BTB-Abschluss-PDF-Lebenszyklus noch gegen echte PostgreSQL-/FastAPI-Testinstanz end-to-end prüfen;
- weitere Server-1.0-Portal-Fachoberflächen für Baustellen/BTB/Mängel/Abnahmen sowie Admin-Dashboard/Systemstatus/Speicher;
- serverseitiger Release-Binärupload/-download, Background Jobs, Backup/Restore/Reset;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Die übrigen Server-1.0-Portal-/Admin-Fachoberflächen für Baustellen/Teams/BTB/Mängel/Abnahmen sowie Systemstatus/Speicher capability-gesteuert auf die vorhandenen `/api/v1`-Verträge setzen.
2. Serverseitigen Release-Binärupload/-download, Background Jobs sowie Backup/Restore/Reset schließen.
3. Sobald eine PostgreSQL-Testinstanz verfügbar ist, den neuen BTB-Abschluss-PDF-Lebenszyklus inklusive erstem Abschluss, unverändertem Reaktivieren, echter Nachbearbeitung, Fotoänderung, erneutem Abschluss und Löschen end-to-end ausführen.
4. Danach die vollständige Vermessungs- und Fach-API-Kette end-to-end ausführen; bis dahin Source-/Fixture-Regressionen weiter absichern.
5. Anschließend den Android-Fachclient capability-gesteuert auf die fertigen `/api/v1`-Verträge umstellen; die bisherigen 0.6-/0.7-Versionsschwellen dürfen nicht die Server-1.0-Fähigkeiten bestimmen.
6. Vollständige Integrations-/Akzeptanztests inklusive Backup/Restore ausführen.
7. Erst danach NAS-Cutover und produktive Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**