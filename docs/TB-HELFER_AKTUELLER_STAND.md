# TB-Helfer – aktueller Projektstand

Stand: 2026-09-19

Maßgeblich sind aktueller GitHub-Code im aktiven Branch, `docs/TB-HELFER_PROJEKTREGELN.md`, diese Datei, `docs/TB-SERVER_1_0_ZIELMODELL.md` und `docs/TB-SERVER_1_0_CUTOVER.md`.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- PR-Basis: `feature/admin-offline-sync`
- Android bleibt vorerst Testclient: Buildkandidat **200050**

- Android Server-1.0 Fachsync Mängel/Abnahmen: capability-gesteuertes Laden sowie idempotentes Schreiben über die kanonischen `/api/v1/worksites/{id}/defects`, `/defects/{id}`, `/close`, `/acceptances/{id}` und `/finalize`-Verträge. Sichtbare, mit Mängeln/Abnahmen verknüpfte Server-1.0-Dateien werden über `files_v1` aufgelöst und in den vorhandenen Attachment-Cache heruntergeladen. Lokale Mängel-/Abnahme-Attachments werden resumierbar über `chunk_upload_v1` mit deterministischen Idempotenz-IDs hochgeladen; nicht mehr sichtbare/gelöschte Fach-Attachments werden nur bei nachweislich vollständigem Datei-Snapshot lokal tombstoned; bei der 1000-Dateien-Grenze wird konservativ nicht gelöscht. BTB bleibt getrennt offen.
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
- Android Server-1.0-Einladungen: stabiler `worksite-invite`-Deep-Link, QR-/Copy-Paste-Parser mit case-sensitivem Server-Token, automatisches Einlösen nach Geräteaktivierung sowie gezielte Kandidaten-/Create-Anbindung mit Legacy-`TBI`-/Legacy-Create-Fallback nur bei fehlendem Server-1.0-Endpunkt
- Android Teamdarstellung Server 1.0: `loadTeam()` baut die bestehende UI aus `/worksites/{id}`, `/device/me`, `/members` und `/teams`; die Auswahl erfolgt jetzt über `worksite_teams_v1`, Legacy nur bei fehlender Capability
- Android Capability-Client: `/api/v1/capabilities` wird kurzzeitig im Prozess gecacht; `worksite_teams_v1` und `worksite_invitations_v1` steuern den Cutover, Netzwerkfehler werden nicht als Legacy-Server missverstanden
- Einladung-only-Mitgliedschaft gehärtet: generischer direkter Member-Add-Pfad entfernt; bestehende Helfer können über einen eigenen replay-sicheren Teamwechselvertrag zwischen aktiven TB-Teams verschoben werden, ohne neue Mitgliedschaft oder historische BTBs umzuschreiben
- Admin-Systemstatus/Speicher v1: API/Webseite für Migrationen, DB-Größe, Storage-Belegung, Firmen-/Nutzer-/Geräte-/Baustellen-/Dateizahlen, Backup-Läufe und Background Jobs ohne Geheimnisse/interne Payloads
- Background-Job-/Backup-Basis: `FOR UPDATE SKIP LOCKED`, Retry/Stale-Recovery, getrennte DATABASE/FILES/FULL-Backups, SHA-256, atomare Dateien und ADMIN-Backupaufträge
- Restore-Test + sicherer Test-Reset: Restore nur gegen getrennte Testdatenbank/Testablage; Test-/Staging-Reset nur nach verifiziertem höchstens 24h altem FULL-Backup und harter Environment-/Bestätigungssperre
- optionaler Restore-Staging-Compose-Overlay: getrennte Restore-Test-DB, getrennte Restore-Target-DB und eigener Restore-Target-Storage; Restore-to-Target bleibt standardmäßig deaktiviert und außerhalb des normalen Serverstarts

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
- `TeamManagementApi.loadServerInvitationCandidates()` lädt konkrete aktive Firmenbenutzer, die noch kein aktives Baustellenmitglied sind;
- `TeamManagementApi.createServerInvitation()` erzeugt gezielte Einladungen gegen `POST /api/v1/worksites/{worksite_id}/invitations` mit eigener `X-Client-Change-Id`;
- Android wählt vor dem Erzeugen eine konkrete Person aus; Helfer behalten den vorhandenen Bereichsdialog und werden als `HELPER/HELPER_STANDARD` eingeladen, weitere verantwortliche TBs als `RESPONSIBLE_TB/RESPONSIBLE_TB`;
- nur wenn der neue Kandidaten-Endpunkt 404 liefert, wird während der Umstellung auf den bisherigen Legacy-Create-Pfad zurückgefallen; fachliche Server-1.0-Fehler erzeugen keine ungezielte Legacy-Einladung;
- die erste Server-1.0-Create-Antwort liefert Einmalcode/App-/Browser-Link; QR wird lokal aus der App-URI erzeugt, Teilen verwendet bevorzugt den Browser-Link;
- fachliche 403-Einladungsfehler werden nicht fälschlich als ungültige Gerätesession behandelt;
- bei noch nicht aktiviertem Gerät bleibt die Einladung bis nach erfolgreicher Aktivierung erhalten und wird anschließend automatisch eingelöst;
- Baustellen-Sync-QR-Scanner und manuelle Eingabe verwenden denselben zentralen Parser.

Zusätzlich lädt `TeamManagementApi.loadTeam()` den Server-1.0-Teamstand aus Worksite-, Device-Me-, Member- und Team-Endpunkten, wenn `worksite_teams_v1=true` gemeldet wird. `RESPONSIBLE_TB/TB` werden für die bestehende Android-UI als `LEAD` abgebildet; `currentGroupId` und Area-Scope stammen aus der aktuellen Membership. Fehlt der Capability-Endpunkt oder die Capability, bleibt der Legacy-Collab-Teamendpunkt als Übergang erhalten; Netzwerkfehler führen nicht zu einem stillen Legacy-Fallback.

Unit-Test: `WorksiteInvitationLinkTest`. Android CI führt künftig `:app:testDebugUnitTest` vor den Builds aus.

Zusätzliche Vertragschecks: `server/checks/48_verify_android_server_1_invitation_source.py`, `server/checks/50_verify_android_server_1_invitation_create_source.py` und `server/checks/51_verify_android_server_1_team_source.py`.

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

### Restore-Staging-Compose-Overlay

`server/docker-compose.restore-test.v1.yml` ergänzt getrennte Restore-Test-/Restore-Target-Datenbanken und einen eigenen Ziel-Storage nur für bewusst gestartete Staging-Tests. Der normale Compose-Start bleibt frei von Restore-to-Target-Zielen; Freigabe-Default ist `NO`. Source-Check 49 prüft diese Trennung.

## Tests / Checks

Workflow-Zielstand:

- Migrationen 001 + 002 + 003 + 004 + 005 + 006;
- `latest_known=6`, `latest_applied=6`;
- Kerntabellen u. a. `survey_parse_catalog`, `track_data_sources`, `track_data_km_points`;
- Source-/Vertragschecks 20–52;
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
- Source-Check 49 prüft den optionalen Restore-Staging-Compose-Overlay, getrennte DB-/Storage-Ziele, externe Passwörter, fehlende Portfreigabe und standardmäßig deaktiviertes Restore-to-Target.
- Source-Check 50 prüft Android-Kandidatenladen und gezieltes Server-1.0-Einladungserzeugen, Client-Change-ID, Rollen-/Profilabbildung, Share-Link sowie den begrenzten Legacy-Fallback.
- Source-Check 51 prüft den Android-Teamaufbau aus Server-1.0-Worksite/Device-Me/Member/Team-Endpunkten sowie den ausschließlich bei Unsupported verwendeten Legacy-Team-Fallback.
- Source-Check 52 prüft den Android-Capability-Client sowie capability-gesteuerte Team-/Einladungsumschaltung ohne 0.6-/0.7-Versionsheuristik.
- Source-Check 53 prüft den Server-1.0-Kontext-only-Pfad über Worksites/Device-Me/Members.
- Source-Checks 54–56 härten Area-Scope, Datei-Elternsichtbarkeit und kanonisches Abnahme-Ergebnis.
- Source-Check 57 prüft den capability-gesteuerten Android-Lesesync für Mängel/Abnahmen inklusive lokaler Snapshot-Übernahme.
- Source-Check 58 prüft die idempotenten Android-Server-1.0-Schreibpfade für Mängel/Abnahmen, Outbox-Wiederverwendung, Konfliktzustände und endgültige Statusaktionen.
- Source-Check 59 prüft das Auflösen sichtbarer `files_v1`-Links auf Mängel/Abnahmen, den Download über `/files/{id}/content` und die Wiederverwendung des vorhandenen Attachment-Caches.
- Source-Check 60 prüft den resumierbaren Android-Chunk-Upload, Upload-Session-Recovery, `X-Client-Change-Id`, Complete-Link auf Mangel/Abnahme und die Weiterverwendung des Attachment-Caches.
- Source-Check 61 prüft, dass ein vollständiger sichtbarer `files_v1`-Snapshot nicht mehr sichtbare/gelöschte Mängel-/Abnahme-Dateien im lokalen Attachment-Cache tombstoned.
- Source-Check 62 verhindert gefährliches Tombstoning bei der aktuellen 1000-Dateien-Grenze: bei genau 1000 Listeneinträgen gilt der Snapshot als möglicherweise abgeschnitten und alte Cache-Einträge bleiben erhalten.
- Source-Check 63 prüft den capability-gesteuerten Android-Lesepfad für Team-BTB-Historie und BTB-Details sowie die Wiederverwendung des vorhandenen lokalen BTB-Importcaches.
- Source-Check 64 prüft den idempotenten Android-BTB-Schreibpfad mit bestehender Outbox, granularen BTB-Einträgen, Teamzuordnung, Versionskonflikten sowie `finalize`/`reopen`.
- Source-Check 65 erzwingt das granulare Server-1.0-Zielmodell und die automatische Migration des kurzzeitig verwendeten Gesamt-Bridge-Eintrags.
- Source-Check 66 prüft BTB-Foto-Download/-Upload über `files_v1`/`chunk_upload_v1`, BTB-Dateilinks und die erneute Finalisierung nach fotoinduzierter `REWORK`-Umschaltung.
- Source-Check 67 prüft den capability-gesteuerten Android-Download des serverseitigen Abschluss-PDFs, SHA-256, atomare lokale Ablage, lokale Invalidierung bei `REWORK` und dass dabei kein falsches Android-Erstoriginal erzeugt wird.
- Source-Check 68 prüft die vorhandene Offline-Löschabsicht/Outbox gegen den kanonischen `DELETE /api/v1/btbs/{id}`-Vertrag, Versions-Rebase, lokalen Tombstone und die Lösch-Reconciliation vor dem normalen BTB-Import.
- Source-Check 69 prüft den gemeinsamen capability-gesteuerten Server-1.0-Synckern für UI und Hintergrund-Worker, die unabhängige BTB/Fach/Datei-Capability-Auswertung und dass alte 0.6-/0.7-Versionsschwellen nur noch den Legacy-Fallback steuern.
- Source-Checks 57–60 sowie 63–68 wurden nach der Coordinator-Extraktion auf denselben gemeinsamen Synckern umgestellt; ihre fachlichen Vertragsprüfungen bleiben unverändert bestehen und prüfen nicht mehr die alte Codeposition im Fragment.
- Source-Check 70 prüft die stabile Cursor-Pagination der sichtbaren Server-1.0-Dateiliste, explizite Snapshot-Vollständigkeit und den Rolling-Update-Fallback auf die bisherige 1000-Dateien-Sicherheitsregel.
- Source-Check-Dateien 57–60/63–68 wurden nach der Coordinator-Extraktion vollständig neu ausgerichtet; dabei wurden versehentlich wörtlich gespeicherte `\\n`-Sequenzen und ein unvollständiger Check-68-Variablenumbau korrigiert. Check 59 prüft die 1000er-Grenze jetzt über `FILE_LIST_LIMIT` statt über ein nicht existentes hartes URL-Literal.
- Source-Check 71 prüft den kanonischen `worksite_tree_v1`-Cutover: stabile `external_key`/Client-Keys, Snapshot inklusive Tombstones, `base_version`-Konflikte, rekursives Unterbaum-Löschen, bestehende Offline-Outbox sowie begrenztes Konflikt-Rebase im gemeinsamen Server-1.0-Synckern.
- Source-Check 72 prüft den Android-Streckendaten-Cutover auf kanonisches Server-1.0-GET/POST/PATCH/DELETE mit `X-Client-Change-Id`, `base_version`, Reconcile-Schleife und ausdrücklich beibehaltenem 0.7.x-`/collab`-Fallback; Rohvermessungsdateien bleiben ausgeschlossen.
- Source-Check 73 prüft den Server-1.0-Baustellen-Lifecycle: idempotente kanonische Anlage, verantwortlicher-TB-only Soft-Delete mit `base_version`, generischer WORKSITE-Tombstone-Feed sowie explizite Android-0.7.x-Fallbacks.
- Source-Check 74 prüft den kanonischen baustellenweiten Medienkatalog: ausschließlich BTB-/Mangel-/Abnahme-Bilder, Cross-Team-Bildsicht nur für `RESPONSIBLE_TB`/CHEF/Admin, kein fremder BTB-Text, Area-Scope für Mängel/Abnahmen, kanonischer Datei-Download und expliziter 0.7.x-Fallback.
- Source-Check 75 stellt sicher, dass Portal und Android dieselben kanonischen Baustellenknotentypen und Payload-Felder verwenden; Parent-Zuordnung sowie Bearbeiten/Löschen sind im Portal versionsgesichert.
- Source-Check 76 prüft, dass Portal-Mängel und -Abnahmen dieselben fachlichen Payload-Schlüssel wie Android bearbeiten und Änderungen mit `base_version` schützen; JSON bleibt nur als additiver Expertenbereich erhalten.
- Source-Check 77 prüft Portal-Mangel-/Abnahmefotos über den bestehenden `worksite_media_v1`-Katalog und den resumierbaren `files_v1`/`chunk_upload_v1`-Vertrag einschließlich Android-kompatibler semantischer Dateinamen.
- Source-Check 78 prüft die verlustfreie Mangelstatus-Abbildung `ABGELEHNT ↔ REJECTED` inklusive Baseline, Migration 009, Server-API, Medienkatalog, Android-Sync und Portal.
- Source-Check 79 prüft die explizite Offline-Abnahme-Löschabsicht, `acceptance_delete_v1`, versionierten kanonischen DELETE, Finalize-Zusatzrecht, Server-Tombstone und die Bestätigung des lokalen Intents erst nach Servererfolg.
- Source-Check 80 prüft den Gegenweg: fremde `ACCEPTANCE`-Tombstones entfernen die lokale Abnahme ohne Echo-DELETE; vorhandene lokale Pending-Änderungen werden dabei nicht still verworfen.
- Source-Check 81 prüft den Portal-Abnahme-DELETE gegen denselben `acceptance_delete_v1`-Vertrag einschließlich `base_version` und der zusätzlichen Finalize-Berechtigung bei endgültig abgeschlossenen Abnahmen.

**Noch nicht ausgeführt:** Source-Checks 34–81 gegen den vollständigen neuen Branding-/Portal-/System-/Release-/Backup-/Restore-/Deployment-/Retention-/Teamwechsel-/QR-Stand (CI-Runner führt weiterhin keine Steps aus) sowie die vollständige PostgreSQL-/FastAPI-End-to-End-Kette für Import -> Parse -> Section -> Projection v3 -> GRA-Sprung v2 -> Kalibrierung. In der aktuellen Arbeitsumgebung stehen weder `postgres`/`psql` noch Docker/Podman zur Verfügung.

### Alte Admin-Bedienlogik als verbindliche Server-1.0-Referenz übernommen 2026-09-19

- Referenzscreenshots des bestehenden Admin 0.7 bestätigen die bewährten Bereiche: Übersicht, Strecken, Bahnhöfe, Benutzer, Firmen, Geräte, Dateien, Baustellen, App-Versionen, Papierkorb und Systemstatus.
- Die Projektregeln schreiben diese Admin-Bedienlogik jetzt ausdrücklich als Referenz fest. Server 1.0 darf technisch moderner sein, soll diese Fachabläufe aber nicht ohne Grund neu erfinden.
- Benutzer und Geräte werden in Server 1.0 wieder als getrennte Admin-Bereiche geführt. Die vorübergehende kombinierte Seite `Nutzer/Geräte` ist nur Entwicklungszwischenstand und nicht Ziel-UX.
- Die Benutzerseite verwendet wieder die Aktion **Aktivierungskarte**. Dafür gibt es jetzt eine dedizierte Seite `/admin/v1/users/{user_id}/activation-card`, optisch und fachlich am bestehenden Ablauf orientiert.
- Die Aktivierungskarte zeigt Benutzer, Firma, Zugriff, Klartext-Key, QR, Hinweise zur ersten Gerätebindung und bietet **Drucken / als PDF speichern**, direkten PDF-Download und **PDF teilen / versenden**.
- Erneutes Öffnen/Erzeugen der Aktivierungskarte widerruft nur vorherige noch unbenutzte Aktivierungs-Keys. Ein bereits gebundenes Gerät bleibt aktiv, bis es im getrennten Gerätebereich ausdrücklich **Freigeben** wird.
- Die Geräteverwaltung ist jetzt wieder eine eigene Seite `/admin/v1/devices` mit Benutzer, Firma, Geräte-ID, Status, letztem Kontakt und Freigabe-Aktion.
- Aktivierungs-Keys verwenden wieder ein gut lesbares `TBH-...`-Format statt eines technisch zufälligen URL-safe Tokens. Serverseitig bleibt ausschließlich der Hash gespeichert.

### Aktivierungs-QR ist jetzt selbstbeschreibend 2026-09-19

- Nach der Umstellung wurde vor dem Gerätebuild noch eine veraltete `teamApi`-Referenz in `AktivierungActivity` gefunden und behoben; der QR-Aktivierungsweg verwendet jetzt durchgängig neu erzeugte `TeamManagementApi`-Clients mit der nach erfolgreicher Aktivierung gespeicherten Serveridentität.
- Source-Check 84 schlägt jetzt zusätzlich fehl, falls diese veraltete Referenz wieder auftaucht.

- Die zwischenzeitliche Android-Staginglösung über `local.properties` wurde auf den vorgesehenen Gerätefluss zurückgeführt: `local.properties` bleibt nur Entwickler-Fallback und ist keine Benutzerkonfiguration.
- Der Geräte-Aktivierungs-QR/Deep-Link enthält jetzt **öffentliche HTTPS-Serveradresse + Einmal-Schlüssel**. Für das aktuelle Staging ist die Serveridentität `https://api-v1.tb-helfer.de`.
- Server 1.0 erhält die kanonische Adresse aus der lokalen Synology-Variable `TB_V1_PUBLIC_BASE_URL`; ohne konfigurierte öffentliche URL wird keine neue Aktivierungskarte ausgegeben.
- Android validiert die QR-Serveradresse auf HTTPS, aktiviert genau gegen diesen Server und speichert die Serveridentität **erst nach erfolgreicher Gerätebindung** lokal.
- `ServerConfig.BASE_URL` liest danach die gespeicherte Serveridentität; neu erzeugte Sync-/Fachclients verwenden damit automatisch denselben NAS-/Cloudflare-Server. BuildConfig bleibt nur Fallback.
- Eine erneute erfolgreiche Geräteaktivierung kann die gespeicherte Serveridentität bewusst ersetzen; ein ungültiger/manipulierter QR ändert sie nicht.
- Die Projektregeln dokumentieren diesen Ablauf jetzt verbindlich, damit Serveridentität/QR/PDF nicht wieder als Build- oder Chatwissen verloren gehen.

### Aktivierungskarte PDF + externer Cloudflare-Stagingpfad 2026-09-19

- Die Geräteaktivierung erzeugt jetzt zusätzlich zur QR-/Deep-Link-Antwort eine **nicht persistent gespeicherte PDF-Aktivierungskarte** mit Benutzer, Firma, Ablaufzeit, QR und Fallback-Schlüssel.
- Die Nutzer-/Geräte-Seite bietet `PDF herunterladen` und `PDF teilen / versenden`; wenn Web Share mit Dateien nicht verfügbar ist, fällt sie auf Download zum manuellen Versenden zurück.
- Aktivierungsantworten setzen `Cache-Control: no-store`; serverseitig bleibt nur der Hash des Einmal-Schlüssels gespeichert.
- Für Tests außerhalb des Heimnetzes ist der separate Cloudflare-Staginghostname **`api-v1.tb-helfer.de`** jetzt praktisch aktiv. Er läuft über den bestehenden gesunden Tunnel `tb-helfer-nas` auf `http://192.168.178.3:8081`; `GET https://api-v1.tb-helfer.de/health/ready` wurde erfolgreich mit `status=ok`, `database=up`, `migrations=ok`, `server_version=1.0.0-dev` geprüft.
- Der bestehende Produktivhostname `api.tb-helfer.de` und der alte 0.7-Server bleiben ausdrücklich unverändert; dies ist **kein produktiver Cutover**.
- Der externe Health-Test ist bestätigt. Für die normale Geräteaktivierung muss Android **nicht** mehr per `local.properties` auf Staging umgestellt werden; die Aktivierungskarte liefert `https://api-v1.tb-helfer.de` selbst. `local.properties` bleibt nur Entwickler-Fallback.

### Geräteaktivierung per QR im Server-1.0-Portal 2026-09-19

- Der bestehende Android-Aktivierungsweg war bereits QR-/Deep-Link-fähig (`tbhelfer://activate?key=...`), die neue Server-1.0-Nutzer-/Geräteseite hatte zunächst jedoch nur Aktivierungsschlüssel und URI als Text angezeigt.
- Die Server-1.0-Geräteaktivierung liefert jetzt zusätzlich einen einmaligen SVG-QR als Data-URL. Der QR enthält ausschließlich dieselbe einmalige Aktivierungs-URI; der Schlüssel wird serverseitig weiterhin nur gehasht gespeichert.
- Die Portal-Seite **Nutzer/Geräte** zeigt nach **Gerät aktivieren** eine Aktivierungskarte mit QR-Code, Ablaufzeit und Schlüssel nur als Fallback/Kopiermöglichkeit.
- Android scannt den QR weiterhin über den vorhandenen Aktivierungs-Scanner und verarbeitet denselben `tbhelfer://activate`-Deep-Link. Kein zweiter Aktivierungsweg wurde eingeführt.
- Neuer Source-Check `84_verify_device_activation_qr_source.py` prüft Server -> Portal -> Android durchgängig.

### Android-Debug gegen Synology-Staging 2026-09-19

- Der praktische Geräte-E2E wäre sonst gegen den falschen Server gelaufen: `ServerConfig.BASE_URL` war weiterhin fest auf `https://api.tb-helfer.de` gesetzt.
- Android liest den Serverendpunkt jetzt über `BuildConfig.SERVER_BASE_URL`.
- Für **Debug** kann der Endpunkt lokal aus `local.properties` (`tb.server.base.url`), alternativ Gradle-Property `tbServerBaseUrl` oder `TB_SERVER_BASE_URL`, gesetzt werden.
- Der **Release-Build** bleibt unabhängig von lokalen Debugwerten fest auf `https://api.tb-helfer.de`.
- Klartext-HTTP wird nur im Debug-Build und nur bei expliziter `http://`-Debugadresse über einen Manifest-Placeholder erlaubt; Release setzt `usesCleartextTraffic=false`.
- `local.properties` ist bereits gitignoriert und bleibt lokale Maschinenkonfiguration.
- Neuer Source-Check `83_verify_android_staging_server_config_source.py` prüft diese Trennung; TB-Server- und Android-CI referenzieren ihn.
- Für den aktuellen Aktivierungs-/Serveridentitäts-Stand am Head `15c84435a4aad3d5eca7b8205f0c327f11febd78` endeten TB-Server **#404 / ID 35444118778** und Android CI **#1081 / ID 35444118780** erneut ohne ausgeführte Schritte. Beide Jobs liefern `steps = null`, `logs_url = null`; damit wurde weiterhin kein Workflow-Schritt, Source-Check, Unit-Test oder Android-Build gestartet. Das ist das bekannte Runner-/Actions-Infrastrukturproblem und kein festgestellter fachlicher Server-/Android-Testfehler.

### BTB-Granularitätskorrektur

Der kurzzeitig eingeführte Android-Gesamt-Bridge-Eintrag `TB_HELFER_ANDROID_V1` bleibt ausschließlich Migrationsquelle. Neue Server-1.0-Schreibvorgänge zerlegen den BTB in einzelne versionierte Entries; vorhandene Bridge-Einträge werden automatisch zur Migration eingereiht und nach erfolgreicher Granularisierung gelöscht. Per-Entry-Basiszustände liegen nur im lokalen Sync-State und ermöglichen das Zusammenführen unabhängiger Eintragsänderungen.\n\n### Android-BTB-Abschluss-PDF-Übernahme

- `btb_finalize_with_pdf_v1` steuert den neuen Android-PDF-Pfad.
- Finalisierte Server-BTBs tragen ihren echten Serverstatus sowie PDF-Verfügbarkeit/SHA nur als technische Sync-Metadaten.
- Android lädt `GET /api/v1/btbs/{btb_id}/final-pdf`, prüft PDF-Magic und bekannte SHA-256 und schreibt atomar in den vorhandenen `BtbSpeicher`-Ordner.
- Der vorhandene Verlauf/Viewer öffnet damit dasselbe aktuelle PDF, ohne zweite Ablagelogik.
- Ein Server-PDF erzeugt bewusst **keine** lokale Original-Baseline. Das unveränderliche erste Original bleibt serverseitig maßgeblich.
- Bei echtem Server-`ACTIVE/REWORK` wird nur ein zuvor gesetzter Server-Abschlussmarker invalidiert; bloßes Reaktivieren eines weiterhin `FINALIZED`-BTBs behält das gültige PDF.

### Android-BTB-Löschung auf Server 1.0

- Der bestehende `BtbDeleteSyncCoordinator` bleibt alleinige Offline-/Benutzer-Löschlogik.
- Moderne DELETE-Outbox-Einträge werden über `DELETE /api/v1/btbs/{btb_id}` mit derselben `X-Client-Change-Id`-Idempotenz übertragen.
- Vor dem DELETE werden Teamzuordnung und Basisversion gegen den aktuellen Server-BTB geprüft; Versionskonflikte werden über den vorhandenen Rebase des ausdrücklichen Benutzer-Löschwunsches behandelt.
- Bestätigte Löschungen tombstonen den lokalen Sync-Cache sofort.
- Die Reconciliation läuft vor dem normalen BTB-Import und verhindert dadurch ein Wiederauftauchen eines lokal bewusst gelöschten Team-BTBs.

### Gemeinsamer Android-Server-1.0-Synckern

- `Server1AndroidSyncCoordinator` enthält jetzt den vorhandenen modernen Kontext-, Mängel-/Abnahme-, BTB-, Datei-, Foto-, PDF- und Löschablauf genau einmal.
- Baustellen-Sync-UI und `SyncWorker` verwenden denselben Coordinator.
- BTB-, Fach- und Datei-Capabilities werden unabhängig ausgewertet; BTB-Sync hängt nicht mehr davon ab, dass zusätzlich Mängel oder Abnahmen aktiviert sind.
- Der Hintergrund-Worker versucht Server 1.0 vor `supportsIncrementalSync`/`supportsCollabSync`. Diese Versionsprüfungen entscheiden nur noch über den alten Legacy-Fallback.
- Der bestehende Offline-/Vermessungs-Sync bleibt im Worker erhalten und wird bei kompatiblem Legacy-Katalog weiterhin ausgeführt.

### Server-1.0-Dateipagination

- `GET /api/v1/worksites/{worksite_id}/files` unterstützt jetzt einen opaken Cursor über die stabile Sortierung `(created_at DESC, id DESC)`.
- Sichtbarkeitsfilterung wird beim serverseitigen Scannen berücksichtigt; eine kurze sichtbare Seite bedeutet daher nicht mehr fälschlich „Ende“.
- Neue Antworten liefern `next_cursor` und `snapshot_complete`.
- Android folgt allen Cursor-Seiten und tombstoned alte Cache-Einträge erst nach explizit vollständigem Snapshot.
- Bei einem alten Server ohne Cursorfelder bleibt die bisherige 1000er-Grenze als sicherer Rolling-Update-Fallback aktiv.
- Capability `file_cursor_pagination_v1` dokumentiert den neuen Vertrag.

### Android-Baustellenbaum auf Server 1.0

- Die vorhandene `WorksiteStructureSync`-Fach-/Merge-Logik bleibt bestehen; ersetzt wurde nur der alte Collaboration-Transport.
- Android liest `GET /api/v1/worksites/{id}/nodes?include_deleted=true` und verwendet `external_key` bzw. einen stabil aus der Server-UUID abgeleiteten Fallback als geräteübergreifenden Client-Key.
- Der Snapshot enthält aktive Knoten und Tombstones, Parent-Client-Key und aktuelle Version.
- Create/PATCH/DELETE laufen idempotent über `X-Client-Change-Id`; PATCH und DELETE prüfen `base_version` und liefern bei Konkurrenz `VERSION_CONFLICT`.
- Das Löschen eines Elternknotens tombstoned serverseitig rekursiv den kompletten Unterbaum.
- Bei Versionskonflikten wird die ausdrückliche lokale LEAD-Änderung höchstens drei Runden gegen den frischen Server-Snapshot rebased; erst ein konfliktfreier Stand wird lokal angewendet.
- `Server1AndroidSyncCoordinator` führt Prepare -> Outbox-Upload -> autoritativen Snapshot capability-gesteuert über `worksite_tree_v1` aus.
- Der Legacy-Worker behält seinen alten Strukturpfad nur für den dokumentierten alten Collaboration-Fallback.

### Android-Streckendaten auf `track_data_v1`

- `WorksiteTrackDataSync` bleibt die fachliche LEAD/Helfer-Logik und synchronisiert weiterhin ausschließlich bereits geparste/berechnete mobile Datensätze.
- `WorksiteTrackDataApi` nutzt auf Server 1.0 jetzt `GET/POST /api/v1/worksites/{id}/track-data` sowie versioniertes `PATCH/DELETE /api/v1/track-data/{id}`.
- Der gewünschte vollständige LEAD-Snapshot wird clientseitig gegen die kanonischen Datensätze reconciled; unveränderte Datensätze erzeugen keine Mutation.
- Mutationen verwenden deterministische `X-Client-Change-Id`-Werte; Versionskonflikte werden mit frischem Snapshot höchstens drei Runden neu abgeglichen.
- Ein alter 0.7.x-Server ohne kanonischen Endpunkt fällt explizit auf den bisherigen Bulk-`/api/v1/collab/.../track-data`-Vertrag zurück.
- TRA/GRA/KF/KOR-Originaldateien werden von diesem Android-Pfad weiterhin niemals hochgeladen oder verändert.
- `Server1AndroidSyncCoordinator` führt `track_data_v1` jetzt auch beim manuellen Sync aus. `Schema13Worker` überspringt diesen Teil auf Server 1.0 und behält ihn nur für den Legacy-0.7-Fallback; damit verwenden manueller und Hintergrund-Sync denselben `track_data_v1`-Pfad ohne parallele Doppeluploads.

### Server-1.0-Baustellenanlage und -löschung

- Android legt neue Server-1.0-Baustellen jetzt idempotent über `POST /api/v1/worksites` mit stabilem `X-Client-Change-Id` an; der alte `/collab/worksites`-Pfad bleibt nur 0.7.x-Fallback.
- Server 1.0 besitzt jetzt `DELETE /api/v1/worksites/{id}?base_version=...`; nur die Baustellenrolle `RESPONSIBLE_TB` darf diese operative Löschung ausführen.
- Die Löschung ist ein Soft-Delete (`TRASHED` + `deleted_at` + Versionssprung), kein physisches Löschen von Fachdaten oder Vermessungsoriginalen.
- Der bestehende generische `tb.deletion_tombstones`-Mechanismus trägt `WORKSITE`-Tombstones; `GET /api/v1/worksites/deletions` verteilt sie firmenweit an berechtigte Geräte.
- Android prüft vor dem Delete die aktuelle Serverversion, rebased bei Konkurrenz höchstens drei Runden und löscht lokal weiterhin erst nach bestätigtem Servererfolg.
- `Schema13Worker` kann denselben `WorksiteDeletionApi` weiterverwenden; auf Server 1.0 nimmt dieser automatisch den kanonischen Tombstone-Feed, auf 0.7.x den alten Fallback.

### Server-1.0-Gesamtmedienkatalog

- `GET /api/v1/worksites/{id}/media` erzeugt die Bilderliste direkt aus `tb.files`, `tb.file_links` und den kanonischen BTB-/Mangel-/Abnahme-Objekten; keine zweite Medien-Tabelle wurde eingeführt.
- Der Katalog enthält ausschließlich Bilddateien aus BTB, Mängeln und Abnahmen – identisch zur bisherigen fachlichen Gesamtbilderlogik.
- `RESPONSIBLE_TB`, CHEF und ADMIN dürfen BTB-Bilder anderer Teams derselben Baustelle sehen und herunterladen; fremde BTB-Texte bleiben verborgen (`parent_payload={}`).
- Normale TB-/Helfer-Benutzer sehen BTB-Bilder nur ihrer Teams. Mangel-/Abnahmebilder bleiben zusätzlich durch bestehende Fachrechte und `area_scope` begrenzt.
- Die Cross-Team-Ausnahme ist ausschließlich beim Bilddownload opt-in; normale Datei-Liste und Datei-Metadaten erhalten dadurch keine erweiterten Fremdteam-Rechte.
- Android `WorksiteMediaApi` nutzt kanonischen Medienkatalog + `GET /api/v1/files/{id}/content`; alte 0.7.x-Server bleiben über den bisherigen `/collab`-Katalog kompatibel.
- Vor einem Online-Gesamtexport läuft auf Server 1.0 zuerst der gemeinsame `Server1AndroidSyncCoordinator`; nur bei echtem Legacy-Server folgt der alte Collaboration-Sync.

### Portal-/Android-Baustellenbaum vereinheitlicht

- Das Portal erzeugt keine alten freien Typwerte `SECTION/TRACK/SWITCH/STATION/OTHER` mehr.
- Portal und Android verwenden gemeinsam `FREIE_STRECKE`, `BAHNHOF` und `ALLGEMEIN_OBJEKT`.
- Parent-Knoten können im Portal gewählt werden; dadurch lässt sich derselbe echte Unterbaustellenbaum wie auf Android pflegen.
- Strecke, km von/bis, Bahnhof und Gleis/Weiche landen im bereits kanonischen Android-Payload (`route_key`, `km_from`, `km_to`, `station_name`, `track_or_switch`).
- Portal-PATCH/DELETE senden die geladene Knoten-Version als `base_version`; konkurrierende Änderungen werden dadurch nicht still überschrieben.
- Das Löschen weist im Portal jetzt ausdrücklich darauf hin, dass der Server den kompletten Unterbaum tombstoned.

### Portal-Fachformulare für Mängel und Abnahmen

- Mängel werden im Portal jetzt über Beschreibung, Bereich, km/Lage, Firma, Status, Strukturelement und Kommentar bearbeitet.
- Abnahmen verwenden dieselben Android-Felder für Bezeichnung, Objekt, Firmen, Abnehmenden, Anwesende, km von/bis, Kommentar, Restpunkte und Zwischenergebnis.
- Bearbeiten erfolgt im jeweiligen Formular und sendet die geladene `base_version`; die bisherigen vollständigen Roh-JSON-`prompt()`-Dialoge entfallen.
- Unbekannte oder spätere Zusatzfelder bleiben erhalten: ein aufklappbarer JSON-Expertenbereich wird additiv mit den Fachfeldern gemerged.
- Portal-Titel werden aus `beschreibung` (Mangel) bzw. `bezeichnung/objekt` (Abnahme) abgeleitet – identisch zur bestehenden Android-Server-1.0-Abbildung.

### Portal-Fotos für Mängel und Abnahmen

- Der vorhandene `worksite_media_v1`-Katalog liefert Mangel-/Abnahmebilder direkt in die jeweiligen Portal-Karten; kein zweiter Medienendpunkt wurde eingeführt.
- Neue Fotos werden über denselben resumierbaren `POST /uploads -> PUT /chunk -> POST /complete`-Vertrag wie BTB-Fotos übertragen.
- Portal-Mangelfotos tragen `DEFECT__MANGEL__...`, Abnahmefotos `ACCEPTANCE_FOTO__SONSTIGES__...`; Androids vorhandene Importlogik erkennt diese Präfixe bereits.
- Sichtbarkeit und Uploadberechtigung bleiben vollständig beim bestehenden Server-Rechte-/Area-Scope-Vertrag.
- Abnahmen ohne Strukturelement speichern keinen künstlichen `—`-Knotennamen mehr; Portal-Bearbeitung zwingt den Abnahmestatus außerdem nicht mehr unnötig auf `IN_PROGRESS`.

### Mangelstatus ABGELEHNT / REJECTED

- Androids bestehender Status `ABGELEHNT` besitzt jetzt das kanonische Server-1.0-Gegenstück `REJECTED`.
- Fresh-Install-Baseline und Migration `009_defect_rejected_status.sql` erlauben `REJECTED` in `tb.defects.status`.
- Der Status bleibt – wie in Android – normal bearbeitbar und ist **nicht** mit dem endgültigen `ERLEDIGT/CLOSED` gleichgesetzt.
- Android schreibt `ABGELEHNT -> REJECTED` und liest `REJECTED -> ABGELEHNT` zurück.
- Medienkatalog und Portal zeigen denselben Status; das Portal kann einen offenen Mangel direkt auf „Abgelehnt“ setzen.
- Damit entfällt der bisherige explizite Server-1.0-Syncfehler für abgelehnte Mängel.

### Android-Abnahme-Löschung auf Server 1.0

- Der vorhandene Android-Löschbutton erzeugt vor dem lokalen Entfernen einen dauerhaften `AbnahmeDeleteIntentStore`-Eintrag.
- Ein Server-DELETE wird **nur** aus diesem ausdrücklichen Benutzerwunsch abgeleitet; eine neu vom anderen Gerät kommende, lokal noch fehlende Abnahme kann dadurch niemals versehentlich gelöscht werden.
- Nach aktuellem Server-Snapshot wird der Intent mit Server-ID und `base_version` in die bestehende Outbox überführt.
- Server 1.0 bietet capability-gesteuert `acceptance_delete_v1` und `DELETE /api/v1/acceptances/{id}?base_version=...`.
- Normale Abnahmen erfordern `acceptance.edit`; für bereits endgültig abgeschlossene Abnahmen ist zusätzlich `acceptance.finalize` nötig, passend zum stärkeren Eingriff.
- Die Serverlöschung ist ein Soft-Delete mit `DELETED`, Versionssprung, Change-Feed, Audit und `ACCEPTANCE`-Tombstone. Zugeordnete Dateien/Fotos werden nicht physisch vernichtet.
- Erst nach bestätigtem Server-DELETE werden Outbox und lokaler Lösch-Intent entfernt.
- Fremde Server-Tombstones werden ebenfalls lokal angewendet: löscht Pixel eine Abnahme, entfernt Poco sie beim nächsten vollständigen Sync. Der lokale Server-Tombstone-Pfad erzeugt ausdrücklich keinen neuen Lösch-Intent.
- Das Portal nutzt denselben versionsgesicherten DELETE. Der Löschbutton wird nur bei `acceptance.edit` angeboten; endgültige Abnahmen zusätzlich nur mit `acceptance.finalize`.

### Fach-DELETE-Abgrenzung

- Abnahmen besitzen in Android einen echten Benutzer-Löschvorgang; deshalb ist der vollständige Server-1.0-DELETE-/Tombstone-Lifecycle umgesetzt.
- Für Mängel existiert im aktuellen Android-Fachablauf **kein** entsprechender Benutzer-Löschvorgang. `Server1FachSyncApi` erzeugt daher bewusst keinen künstlichen Mangel-DELETE.
- Mangelstatus wie `ERLEDIGT` und `ABGELEHNT` werden stattdessen kanonisch als Statusänderungen synchronisiert.
- Mangel-DELETE wird nicht künstlich ergänzt, solange dafür keine dokumentierte Fachfunktion/Benutzeraktion existiert.

## Bekannte/offene Punkte

- vollständige Import -> Parse -> Survey-Section -> Punktprojektion v3 -> GRA-Sprung v2 -> Kalibrierung gegen eine echte PostgreSQL-/FastAPI-Testinstanz ausführen;
- weitere reale Mehr-TRA-Fälle prüfen, besonders `reverse_geometry`, echte rückläufige Stationierung und nicht überlappende Anschlussstücke;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz automatisieren; Default bleibt `false`;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne Originale zu verändern;
- rohe/unbekannte KOR nur bei belastbar belegtem Format ergänzen;
- Der Android-Server-1.0-Fachcutover ist im Quellstand weitgehend abgeschlossen und muss jetzt praktisch getestet werden: Baustellenkontext/Teams/Einladungen, Unterbaustellenbaum, mobile `track_data_v1`-Arbeitsdaten, Mängel, Abnahmen inklusive expliziter Offline-Löschung/Tombstones, Dateien/Fotos, granularer Team-BTB, BTB-Fotos und serverseitiges Abschluss-PDF laufen capability-gesteuert über kanonische `/api/v1`-Verträge. Dokumentierte 0.7.x-`/collab`-Pfade bleiben ausschließlich Legacy-Fallback. Offen bleibt die praktische Android-/PostgreSQL-E2E-Prüfung;
- BTB-Abschluss-PDF-Lebenszyklus noch gegen echte PostgreSQL-/FastAPI-Testinstanz end-to-end prüfen;
- Android-QR-Scanner/Intent-Handling sowie gezieltes Erzeugen/Einlösen von Server-1.0-Baustelleneinladungen sind im Quellstand umgesetzt; UI und Hintergrund verwenden für den modernen Baustellen-/Fachsync nun denselben capability-gesteuerten Coordinator; die grundlegenden Portal-Fachformulare sind jetzt an die Android-Payloads angeglichen; praktische E2E-Prüfung und weitere Komfortdetails bleiben offen;
- Compose-Staging-Stack läuft praktisch auf der Synology; offen bleiben LAN-/Portal-/Android-E2E sowie Backup-Retention mit echten Alt-Backups und Restore-to-Target gegen getrennte PostgreSQL-/Storage-Ziele, danach separaten NAS-Cutover abnehmen;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. **Synology-Staging aktualisieren:** `TB_V1_PUBLIC_BASE_URL=https://api-v1.tb-helfer.de` in der lokalen `.env` setzen, aktuelle `tbserver/`- und `templates/`-Dateien übernehmen und API mit der aktualisierten Compose-Umgebung neu starten/recreaten.
2. Danach die neue Server-1.0-Benutzerseite und die dedizierte **Aktivierungskarte** praktisch öffnen; für die vorhandenen Pixel-/Poco-Testnutzer je eine Karte erzeugen und QR/PDF prüfen.
3. Aktuellen Android-Debug-Build installieren und Pixel/Poco über die Aktivierungskarten binden; anschließend bei ausgeschaltetem WLAN Server-/Synczugriff über Mobilfunk praktisch bestätigen.
3. Mit **Pixel + Poco** dieselbe Testbaustelle verwenden und mindestens prüfen: Einladung/Team, Unterbaustellenbaum, Mangel erstellen/ändern/`ABGELEHNT`, Abnahme erstellen/ändern/finalisieren/löschen, Cross-Device-Tombstone, Fotos/Anhänge, Team-BTB, BTB-Fotos, Abschluss-PDF und Hintergrund-Sync.
4. Parallel Server-Staging vollständig prüfen: Backup -> Retention -> Restore-Test -> Restore-to-Target.
5. Danach die Vermessungskette mit realen Daten end-to-end ausführen: Import -> Parse -> Survey-Section -> Punktprojektion v3 -> GRA-Sprung v2 -> Kalibrierung; zusätzliche Mehr-TRA-/Reverse-Geometry-Fälle mitprüfen.
6. Gefundene E2E-Fehler im bestehenden Feature-Branch beheben und `docs/TB-HELFER_AKTUELLER_STAND.md` jeweils mit Testergebnis aktualisieren.
7. Erst nach bestandenen Android-/Server-/Backup-/Restore-Akzeptanztests den NAS-Cutover freigeben.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**