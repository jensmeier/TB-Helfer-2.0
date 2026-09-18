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
- Release-Katalog / Client-Kompatibilitätsvertrag: öffentliche Statusabfrage + ADMIN-Verwaltung; serverseitiger streamender Binärupload/-download mit SHA-256, atomarem Dateitausch und Admin-Weboberfläche ist jetzt ebenfalls umgesetzt
- BTB-Abschluss-PDF / Ein-PDF-/Original-/Nachbearbeitungsmodell: in diesem Stand serverseitig umgesetzt; stabiler PDF-Pfad pro `btbId`, unveränderlicher erster Original-Snapshot inkl. Foto-Basis, Reaktivierung ohne automatische Ungültigkeit
- Zentrales Firmenprofil / Firmenlogo / PDF-Logo-Layout: in diesem Stand auf die vorhandenen Server-1.0-Firmen-/Dateifelder umgesetzt; Android-UUID-Kompatibilität korrigiert, PDF-Renderer auf v2 mit zentralem Branding angehoben
- erste native Server-1.0-Portal-/Admin-Weboberfläche: Login/Portal-Start, Firmenliste/-anlage sowie Firmenprofil/Logo/PDF-Layout mit CSRF-geschützter API-Anbindung und Browser-Vorschau
- Portal-Fachoberfläche v1: Baustellenübersicht/-detail, Struktur-CRUD, Mitgliederrechte/Teamansicht, Team-BTBs, BTB-Bearbeitung/Abschluss-PDF/Foto-Chunkupload sowie Mängel-/Abnahme-Bearbeitung auf denselben `/api/v1`-Verträgen; Browser-Mutationen senden CSRF + `X-Client-Change-Id`
- gezielte Server-1.0-Baustelleneinladungen: gleiches Unternehmen, eingeladener Zielbenutzer, gehashter Einmalcode, Widerruf/Ablauf/Einmalannahme; Helfer werden dem Team des einladenden verantwortlichen TB zugeordnet
- Einladung Code/Link/QR: App-URI `tbhelfer://worksite-invite`, Browser-Link mit Token nur im URL-Fragment, SVG-QR in der einmaligen Create-Antwort, `Cache-Control: no-store`; Mutation Receipt/Audit bleiben tokenfrei
- Android Server-1.0-Einladungseinlösung: stabiler `worksite-invite`-Deep-Link, QR-/Copy-Paste-Parser mit case-sensitivem Server-Token, automatisches Einlösen nach Geräteaktivierung und Legacy-`TBI`-Fallback
- Einladung-only-Mitgliedschaft gehärtet: generischer direkter Member-Add-Pfad entfernt; bestehende Helfer können über einen eigenen replay-sicheren Teamwechselvertrag zwischen aktiven TB-Teams verschoben werden, ohne neue Mitgliedschaft oder historische BTBs umzuschreiben
- Admin-Systemstatus/Speicher v1: API/Webseite für Migrationen, DB-Größe, Storage-Belegung, Firmen-/Nutzer-/Geräte-/Baustellen-/Dateizahlen, Backup-Läufe und Background Jobs ohne Geheimnisse/interne Payloads
- Background-Job-/Backup-Basis: `FOR UPDATE SKIP LOCKED`, Retry/Stale-Recovery, getrennte DATABASE/FILES/FULL-Backups, SHA-256, atomare Dateien und ADMIN-Backupaufträge
- Restore-Test + sicherer Test-Reset: Restore nur gegen getrennte Testdatenbank/Testablage; Test-/Staging-Reset nur nach verifiziertem höchstens 24h altem FULL-Backup und harter Environment-/Bestätigungssperre

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

### Release-Binärupload / Download

Der vorhandene Release-Katalog kann jetzt neben externen Download-URLs auch serverseitige Binärdateien verwalten.

- `PUT /api/v1/admin/releases/{release_id}/binary` streamt einen Draft in den Server-Storage, maximal 512 MiB;
- optionales `X-Release-SHA256` wird serverseitig gegen die während des Streams berechnete Prüfsumme verifiziert;
- stabiler interner Storage-Key: `releases/<platform>/<release_uuid>/package.bin`;
- Dateitausch nutzt Tempdatei + atomaren Replace + Rollback;
- veröffentlichen ist nur mit internem Binary oder externer Download-URL zulässig;
- veröffentlichtes Binary ist über `GET /api/v1/releases/{release_id}/download` verfügbar;
- Draft-Binaries können entfernt/ersetzt werden, veröffentlichte Releases bleiben unveränderlich;
- öffentliche und bereinigte Admin-Payloads geben keinen internen Storage-Key aus.

Admin-Webseite: `GET /admin/v1/releases`.

Capability: `release_binary_v1=true`.

Details: `docs/TB-SERVER_1_0_RELEASES.md`.

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

### Portal-Fachoberfläche v1

Die native Server-1.0-Weboberfläche deckt jetzt auch den ersten Fachclient-Bereich ab:

- `GET /portal/v1/worksites` – rollen-/mitgliedschaftsgesteuerte Baustellenliste plus Neuanlage;
- `GET /portal/v1/worksites/{worksite_id}` – Baustellendetail mit Struktur, Mitgliedern, Teams, Team-BTBs, Mängeln und Abnahmen;
- `GET /portal/v1/btbs/{btb_id}` – Kopfdaten/Facheinträge, Reaktivierung, Abschluss, Abschluss-PDF und Löschen.

Die Oberfläche ruft ausschließlich die bestehenden Fachendpunkte auf. Rechte bleiben serverseitig in `worksite_context`/den Fachroutern verbindlich; das Web blendet nicht erlaubte Aktionen zusätzlich aus.

Die gemeinsame Browser-API-Hilfe setzt bei Nicht-GET-Aufrufen automatisch `X-CSRF-Token` sowie `X-Client-Change-Id`. Bearer-/Gerätegeheimnisse werden nicht in Browser-Templates eingebaut.

Aktuell direkt möglich: Strukturknoten anlegen/bearbeiten/löschen, Rechteprofil/Bereich bestehender Mitglieder ändern, gezielte Baustelleneinladungen erzeugen/widerrufen/annehmen, Team-BTB starten, BTB-Kopf/Facheinträge bearbeiten, BTB-Fotos per bestehendem SHA-256-/Chunkupload verknüpfen/lösen, BTB reaktivieren/finalisieren/PDF öffnen/löschen sowie Mängel und Abnahmen anlegen/bearbeiten/abschließen.

Capability: `portal_worksite_fach_ui_v1=true`.

Details: `docs/TB-SERVER_1_0_PORTAL_FACH_UI.md`.

### Android-Einladung Server 1.0

Android versteht jetzt denselben Einladungsvertrag wie Server/Portal:

- `tbhelfer://worksite-invite?token=...` ist im Manifest registriert;
- moderner Server-Token bleibt case-sensitive und wird niemals uppercased;
- Portal-Link `/portal/v1/invitations/redeem#token=...` und roher Token können ebenfalls eingefügt/eingescannt werden;
- Legacy `TBI-...` und `tbhelfer://invite?code=...` bleiben während der Umstellung kompatibel;
- `TeamManagementApi.redeemServerInvitation()` nutzt `POST /api/v1/worksites/invitations/redeem` mit Geräte-Bearer und eigener `X-Client-Change-Id`;
- fachliche 403-Einladungsfehler werden nicht fälschlich als ungültige Gerätesession behandelt;
- bei noch nicht aktiviertem Gerät bleibt die Einladung bis nach erfolgreicher Aktivierung erhalten und wird anschließend automatisch eingelöst;
- Baustellen-Sync-QR-Scanner und manuelle Eingabe verwenden denselben zentralen Parser.

Unit-Test: `WorksiteInvitationLinkTest`. Android CI führt künftig `:app:testDebugUnitTest` vor den Builds aus.

Zusätzlicher Vertragscheck: `server/checks/48_verify_android_server_1_invitation_source.py`.

Details: `docs/TB-ANDROID_SERVER_1_0_INVITATIONS.md`.

### Admin-Systemstatus / Speicher v1

`GET /api/v1/admin/system/status` liefert ausschließlich für Portal-ADMIN den Systemzustand ohne Geheimnisse oder interne Job-Payloads:

- Server-/Migrationsstand;
- PostgreSQL-Datenbankgröße;
- Firmen/Nutzer/aktive Geräte/Baustellen/BTBs/Dateien;
- offene Mängel und nicht finalisierte Abnahmen;
- erfasste Dateigröße;
- Storage- und Backup-Storage-Belegung;
- letzte Backup-Läufe ohne internen Storage-Key;
- letzte Background Jobs ohne Payload/`last_error`;
- whitelisted, nicht geheime `server_settings`.

Webseite: `GET /admin/v1/system`.

Neben der read-only Statusanzeige können ADMINs DATABASE-/FILES-/FULL-Backup-Jobs und Restore-Tests einreihen. Die eigentliche Ausführung erfolgt im separaten Background-Worker.

Capability: `admin_system_status_v1=true`.

Details: `docs/TB-SERVER_1_0_SYSTEM_STATUS.md`.

### Background Jobs / Backup v1

Der DB-gestützte Worker verwendet die bereits vorhandene Tabelle `tb.background_jobs`.

- Claim über `FOR UPDATE SKIP LOCKED`;
- maximal fünf Versuche mit Backoff;
- verwaiste `RUNNING`-Jobs werden nach 30 Minuten wiederhergestellt;
- Jobs: `BACKUP_DATABASE`, `BACKUP_FILES`, `BACKUP_FULL`, `RESTORE_TEST`;
- Workerstart: `python -m tbserver.jobs worker`.

`TB_BACKUP_ROOT` muss außerhalb von `STORAGE_ROOT` liegen.

Backuparten:

- DATABASE: PostgreSQL-17-`pg_dump` im Custom-Format, Passwort nur über `PGPASSWORD`;
- FILES: kompletter TB-Storage als `.tar.gz`;
- FULL: `database.dump` + `files.tar.gz` + Manifest mit inneren SHA-256-Werten.

Alle finalen Backupdateien erhalten Größe + SHA-256 in `tb.backup_runs`. `POST /api/v1/admin/system/backups` reiht idempotent einen Backup-Job ein.

Capabilities: `background_jobs_v1=true`, `backup_v1=true`.

### Restore-Test / Test-Reset

`POST /api/v1/admin/system/backups/{backup_run_id}/restore-test` stellt einen `RESTORE_TEST`-Job ein.

Für DATABASE/FULL ist eine getrennte Restore-Test-Datenbank über `TB_RESTORE_TEST_DB_*` Pflicht. Ihr Datenbankname darf nicht dem Live-`DB_NAME` entsprechen. Der Worker löscht ausschließlich dort das `tb`-Schema, führt `pg_restore` aus und verifiziert danach Migrationsregister + Kerntabellen.

FILES/FULL werden nur in eine temporäre Restore-Test-Ablage unterhalb des Backup-Roots extrahiert. Archive mit absoluten/`..`-Pfaden, Symlinks/Hardlinks oder Device-Dateien werden abgelehnt; FULL prüft zusätzlich Layout und beide inneren SHA-256-Werte gegen `manifest.json`.

Test-/Staging-Reset ist ausschließlich als CLI vorhanden:

`python -m tbserver.reset_test reset-test-data --backup-run-id <uuid> --confirm RESET-TEST-DATA:<uuid>`

Schutz:

- nur `development|test|staging`;
- erfolgreicher FULL-Backup-Lauf Pflicht;
- Backup höchstens 24 Stunden alt;
- Backupgröße + SHA-256 erneut verifiziert;
- dynamische Bestätigung mit Backup-UUID;
- Backup-Root außerhalb Storage;
- danach frische Server-1.0-Migrationen und leerer Test-Storage.

Capabilities: `restore_test_v1=true`, `test_data_reset_v1=true`.

Ein produktiver Restore wird damit **nicht** freigegeben.

Details: `docs/TB-SERVER_1_0_BACKUP_RESTORE.md`.

### Kontrollierter Restore-to-Target v1

Produktiver Restore wird nicht als In-place-Webaktion gebaut. `server/tbserver/restore_target.py` stellt ausschließlich ein verifiziertes FULL-Backup in eine getrennte, frische Ziel-Datenbank und einen getrennten leeren Ziel-Storage wieder her.

Sicherheitsgrenzen:

- zusätzliche ENV-Freigabe `TB_ALLOW_RESTORE_TARGET=YES`;
- dynamische CLI-Bestätigung `RESTORE-TARGET:<backup_uuid>:<target_db_name>`;
- Ziel-DB-Name ungleich Live-`DB_NAME`;
- Ziel-DB ohne vorhandenes `tb`-Schema;
- Ziel-Storage disjunkt von Live-Storage und Backup-Root sowie leer;
- FULL-Manifest + innere/äußere SHA-256 werden geprüft;
- `pg_restore` läuft ohne `--clean` gegen das frische Ziel;
- Fehler räumen nur den getrennten Zielzustand wieder auf;
- Live-DB und Live-Storage werden nicht verändert.

Migration 005 erlaubt `RESTORE_TARGET` im Backup-/Restore-Laufprotokoll. Capability: `restore_target_v1=true`.

Der eigentliche Produktionswechsel bleibt ein separater Cutover nach Prüfung der wiederhergestellten Zielinstanz.

### Releases / Client-Kompatibilität

Der Release-Katalog liefert die stabile Client-Kompatibilität und kann neben externen Download-URLs auch serverseitige Binärdateien verwalten.

Öffentliche Endpunkte:

- `GET /api/v1/client-compatibility`;
- `GET /api/v1/releases/{platform}/latest`;
- `GET /api/v1/releases/{release_id}/download` für veröffentlichte interne Binärdateien.

ADMIN:

- Release-Draft anlegen/ändern;
- internes Binary per `PUT /api/v1/admin/releases/{release_id}/binary` streamen;
- Draft-Binary entfernen;
- veröffentlichen/zurückziehen;
- Admin-Webseite `GET /admin/v1/releases`.

Der Binärupload ist auf 512 MiB begrenzt, kann `X-Release-SHA256` prüfen und verwendet Tempdatei + atomaren Replace + Rollback. Ein Release kann nur mit internem Binary oder externer Download-URL veröffentlicht werden. Veröffentlichte Release-Metadaten/Binaries werden nicht still überschrieben. Öffentliche und bereinigte Admin-Payloads geben keinen internen Storage-Key aus.

Capabilities: `release_catalog_v1=true`, `release_binary_v1=true`, `client_compatibility_v1=true`.

Details: `docs/TB-SERVER_1_0_RELEASES.md`.

### Docker-/NAS-Staging-Basis v1

`server/docker-compose.v1.yml` beschreibt jetzt den reproduzierbaren Server-1.0-Stack:

- PostGIS/PostgreSQL 17 mit persistentem DB-Pfad;
- einmaliger `migrate`-Dienst vor API/Worker;
- API mit getrenntem persistentem `STORAGE_ROOT` und `TB_BACKUP_ROOT`;
- eigener Background-Worker auf demselben Image;
- Ready-/DB-Healthchecks;
- Default-API-Bindung `127.0.0.1:8081`;
- DB-Passwort nur über externe Compose-Variable `TB_V1_DB_PASSWORD`.

Standardpfade für lokale/Staging-Nutzung: `database-v1`, `storage-v1`, `backups-v1`. Für Synology werden diese bewusst auf getrennte persistente NAS-Pfade gesetzt.

Restore-Test/Restore-to-Target sind keine automatischen Compose-Startdienste, sondern bleiben explizite Wartungsaktionen.

Details: `docs/TB-SERVER_1_0_DEPLOYMENT.md`.

### Backup-Retention v1

Backups besitzen jetzt eine sichere Aufbewahrungsregel:

- `TB_BACKUP_RETENTION_DAYS`, Default 30;
- `TB_BACKUP_RETENTION_MIN_PER_KIND`, Default 3;
- Ranking getrennt für DATABASE/FILES/FULL;
- nur alte Backups oberhalb der Mindestanzahl werden Kandidaten;
- `BACKUP_RETENTION` läuft im Background Worker;
- ADMIN kann die Prüfung über `POST /api/v1/admin/system/backups/retention` einreihen;
- Migration 006 ergänzt `PURGED`, damit entfernte Artefakte nicht als fehlgeschlagene Backups erscheinen;
- DB-Referenz wird vor dem physischen Löschen entfernt, sodass ein Löschfehler höchstens eine unreferenzierte Datei zurücklässt.

Capability: `backup_retention_v1=true`.

### Einladung Code / Link / QR v1

Der vorhandene gezielte Einladungsvertrag besitzt jetzt drei Darstellungsformen derselben kurzlebigen Einladung:

- Rohcode für Copy/Paste;
- App-URI `tbhelfer://worksite-invite?token=...`;
- Browser-Link `/portal/v1/invitations/redeem#token=...`;
- SVG-QR-Code auf Basis der App-URI.

Sicherheitsgrenzen:

- Rohcode, Link und QR werden nur in der ersten erfolgreichen Create-Antwort erzeugt;
- der Browser-Link trägt den Token ausschließlich im Fragment, nicht in Query/Path;
- die Portal-Einlöseseite liest das Fragment clientseitig und entfernt es anschließend aus der sichtbaren URL;
- Annahme bleibt eine bewusste Benutzeraktion;
- Create-Antwort setzt `Cache-Control: no-store`;
- Mutation Receipt und Audit speichern weder Rohcode noch Link/QR;
- ein idempotenter Replay gibt diese Geheimdarstellungen nicht erneut aus.

Die reine URI-/Fragment-/QR-Helferlogik wurde lokal gegen einen Token mit Leerzeichen/Sonderzeichen geprüft; URL-Decoding ergibt wieder exakt den Originaltoken und der QR-Helfer erzeugt ein SVG-Data-URL.

Capability: `worksite_invitation_qr_v1=true`.

Android-`QR scannen` wird später gegen exakt diese App-URI angebunden; es entsteht kein paralleler Einladungsvertrag.

### Einladung-only-Mitgliedschaft / Helfer-Teamwechsel

Die verbindliche Projektregel „ohne angenommene Einladung keine Baustellen-Zusammenarbeitsdaten“ wird jetzt auch im API-Vertrag selbst durchgesetzt.

- der alte direkte `POST /api/v1/worksites/{worksite_id}/members`-Pfad wurde entfernt;
- der Baustellenersteller bleibt weiterhin korrekt der erste verantwortliche TB;
- weitere Mitglieder entstehen nur über den sicheren Einladungs-/Redeem-Vertrag;
- `GET /api/v1/worksites/{worksite_id}/members` liefert zusätzlich die aktuelle Team-/TB-Zuordnung;
- `POST /api/v1/worksites/{worksite_id}/members/{member_user_id}/team` verschiebt ausschließlich bestehende `HELPER`;
- Zielteam muss aktiv und derselben Baustelle zugeordnet sein;
- alte Teamzuordnung erhält `removed_at`, die neue Zuordnung wird aktiviert;
- die Mitgliedschaft bleibt dieselbe UUID; ihre Sync-Version wird erhöht und ein Change-Event/Audit geschrieben;
- historische BTBs des bisherigen Teams bleiben unverändert.

Portal: vorhandene Helfer zeigen ihr aktuelles Team und können bei `members.manage` über „Team wechseln“ einem anderen aktiven TB-Team zugeordnet werden.

Capability: `worksite_team_transfer_v1=true`.

## Tests / Checks

Workflow-Zielstand:

- Migrationen 001 + 002 + 003 + 004 + 005 + 006;
- `latest_known=6`, `latest_applied=6`;
- Kerntabellen u. a. `survey_parse_catalog`, `track_data_sources`, `track_data_km_points`;
- Source-/Vertragschecks 20–48;
- Health/Capabilities/Auth-Checks;
- erwartete Capabilities enthalten zusätzlich `survey_station_merge_v1`, `survey_gra_jump_detection_v1`, `release_catalog_v1`, `release_binary_v1`, `client_compatibility_v1`, `btb_finalize_with_pdf_v1`, `company_profile_v1`, `company_logo_v1` und `company_pdf_logo_layout_v1`, `company_branding_admin_ui_v1`, `portal_worksite_fach_ui_v1`, `worksite_invitations_v1`, `worksite_invitation_qr_v1`, `worksite_team_transfer_v1`, `admin_system_status_v1`, `background_jobs_v1`, `backup_v1`, `backup_retention_v1`, `restore_test_v1`, `test_data_reset_v1` und `restore_target_v1`.

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
- Source-Check 37 ist als Vertragsprüfung für Baustellen-/Team-/BTB-/Mängel-/Abnahme-Portaloberfläche, CSRF, Browser-Idempotenz und BTB-Foto-Chunkupload vorbereitet.
- Source-Check 38 ist als Sicherheits-/Vertragsprüfung für gezielte Baustelleneinladungen inkl. gehashtem Einmalcode und tokenfreiem Mutation Receipt vorbereitet.
- Source-Check 39 ist als Syntax-/Vertragsprüfung für read-only Admin-Systemstatus/Storage inkl. Geheimnis-/Payload-Ausschluss vorbereitet.
- Source-Check 40 ist als Syntax-/Vertragsprüfung für Release-Binärupload/-download, SHA-256, atomaren Storage, Veröffentlichungsgrenzen und Admin-Weboberfläche vorbereitet.
- Source-Check 41 ist als Syntax-/Vertragsprüfung für Background Worker, Backup-Root-Trennung, DATABASE/FILES/FULL-Backup, SHA-256, Retry/Stale-Recovery und Admin-Backupauftrag vorbereitet.
- Source-Check 42 ist als Sicherheits-/Vertragsprüfung für getrennten Restore-Test, FULL-Manifest/SHA/Archivschutz und ausschließlich Development/Test/Staging erlaubten Backup-gesicherten Reset vorbereitet.
- Source-Check 43 ist als Sicherheits-/Vertragsprüfung für Restore-to-Target, frische getrennte Ziel-DB/Storage, explizite Freigabe/Bestätigung und fehlende HTTP-Exponierung vorbereitet.
- Source-Check 44 ist als Vertragsprüfung für Compose-Staging-Basis mit PostGIS 17, Migration-Service, getrennten Storage-/Backup-Mounts, API-Healthcheck und separatem Worker vorbereitet.
- Source-Check 45 ist als Vertragsprüfung für Backup-Retention, Mindestbestand je Art, PURGED-Status, idempotenten Admin-Auftrag und Compose-Policy vorbereitet.
- Source-Check 46 ist als Sicherheits-/Vertragsprüfung für Einladung-only-Mitgliedschaft, entfernten Direkt-Add-Pfad und replay-sicheren Helfer-Teamwechsel vorbereitet.
- Source-Check 47 ist als Syntax-/Vertrags-/Runtimeprüfung für App-URI, Fragment-Link, SVG-QR, `no-store`, tokenfreien Mutation Receipt und Portal-Fragmentübernahme vorbereitet.
- Source-Check 48 prüft den gemeinsamen Server-/Android-Vertrag für `worksite-invite`, case-sensitive Tokens, modernen Redeem-Endpunkt, Manifest-Deep-Link, Legacy-Fallback und Android-Unit-Test.

**Noch nicht ausgeführt:** Source-Checks 34–48 gegen den vollständigen neuen Branding-/Portal-/System-/Release-/Backup-/Restore-/Deployment-/Retention-/Teamwechsel-/QR-Stand (CI-Runner führt weiterhin keine Steps aus) sowie die vollständige PostgreSQL-/FastAPI-End-to-End-Kette für Import -> Parse -> Section -> Projection v3 -> GRA-Sprung v2 -> Kalibrierung. In der aktuellen Arbeitsumgebung stehen weder `postgres`/`psql` noch Docker/Podman zur Verfügung.

### Bekanntes GitHub-Actions-Problem

Für den aktuellen Release-Binary-Commit `4abd52b82d12f153874f07a2bbaa1a78edb2f661` endete der TB-Server-Lauf **#151 / ID 35295263798** mit `foundation` = failure; der Job meldet weiterhin `steps = null`. Auch Android CI **#932 / ID 35295263849** endete ohne ausgeführte Steps. Damit wurden für diesen Stand weder Source-Checks 34–40 noch Python-, PostgreSQL-, API- oder Android-Build-Schritte tatsächlich ausgeführt. Das ist weiterhin das bekannte Runner-/Actions-Infrastrukturproblem und kein festgestellter fachlicher Server-/Android-Testfehler.

## Bekannte/offene Punkte

- vollständige Import -> Parse -> Survey-Section -> Punktprojektion v3 -> GRA-Sprung v2 -> Kalibrierung gegen eine echte PostgreSQL-/FastAPI-Testinstanz ausführen;
- weitere reale Mehr-TRA-Fälle prüfen, besonders `reverse_geometry`, echte rückläufige Stationierung und nicht überlappende Anschlussstücke;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz automatisieren; Default bleibt `false`;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne Originale zu verändern;
- rohe/unbekannte KOR nur bei belastbar belegtem Format ergänzen;
- Android-Fachclient auf Server-1.0-Endpunkte umstellen und Upload/Sync praktisch testen;
- BTB-Abschluss-PDF-Lebenszyklus noch gegen echte PostgreSQL-/FastAPI-Testinstanz end-to-end prüfen;
- Android-QR-Scanner/Intent-Handling für die stabile `tbhelfer://worksite-invite`-URI ist umgesetzt; Android-Erzeugen gezielter Server-1.0-Einladungen sowie komfortablere Portal-Fachformulare bleiben offen;
- Compose-Staging-Stack praktisch auf Docker/Synology starten; Backup-Retention mit echten Alt-Backups prüfen; Restore-to-Target gegen echte PostgreSQL-/Storage-Ziele prüfen, danach separaten NAS-Cutover abnehmen;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Den neuen Compose-Staging-Stack auf einer echten Docker/PostgreSQL-Umgebung starten und dort Backup -> Retention -> Restore-Test -> Restore-to-Target end-to-end prüfen; in der aktuellen Arbeitsumgebung fehlen Docker/PostgreSQL.
2. Als nächsten Android-Schritt nach dem Server-Staging die gezielte Server-1.0-Einladungserzeugung und danach die übrigen Fachendpunkte capability-gesteuert umstellen; spezialisierte Portal-Fachformulare additiv verbessern.
3. Sobald eine PostgreSQL-Testinstanz verfügbar ist, den neuen BTB-Abschluss-PDF-Lebenszyklus inklusive erstem Abschluss, unverändertem Reaktivieren, echter Nachbearbeitung, Fotoänderung, erneutem Abschluss und Löschen end-to-end ausführen.
4. Danach die vollständige Vermessungs- und Fach-API-Kette end-to-end ausführen; bis dahin Source-/Fixture-Regressionen weiter absichern.
5. Anschließend den Android-Fachclient capability-gesteuert auf die fertigen `/api/v1`-Verträge umstellen; die bisherigen 0.6-/0.7-Versionsschwellen dürfen nicht die Server-1.0-Fähigkeiten bestimmen.
6. Vollständige Integrations-/Akzeptanztests inklusive Backup/Restore ausführen.
7. Erst danach NAS-Cutover und produktive Android-Umstellung.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**