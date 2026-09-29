# TB-HELFER 2.0 – SERVER STATUS

Stand: 2026-09-26
Branch: `feature/tb-server-1.0`
Fach-HEAD vor Projektsteuerungscommit: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
Draft-PR: #6
Version: **1.0.0-dev**
Quellmigrationen: **001–015**

- Migration 014 Teilnehmer-Schichtstatus praktisch bestätigt.
- Migration 015 strukturierte Foto-Metadaten: Quellstand vorhanden, Deployment/Runtime-E2E offen.
- Rollen-/Schichtvertrag, FINALIZED-read-only und Teamlead-Finalize verbindlich.
- Delta-Wait/15×15-Quellstand vorhanden; Last-E2E offen.
- Finalize-OOM/502-Fix praktisch bestätigt und geschützt.

Bildpfad: Mobile Aufnahmen laut 15×15 nur kleine Sync-Version + Thumbnail + Metadaten. Portal-Uploads dürfen serverseitige Quelldatei bleiben. Vor Serveränderung zuerst Android-Upload auditieren.

Nächste Schritte: Hauptgerät↔Server/Portal E2E → HELPER/Poco → IMG-001 Android-Implementierung → Migration 015/Rendition-E2E; Gesamtlast später gemäß E2E-PRIO-001.

Historischer Tracker: `projektsteuerung/archiv/SERVER_STATUS_ALT_2026-09-26.md`

## Praxisnachtrag – Delta-Wait 255 / neue Testreihenfolge

Praktischer Staging-Kapazitätsteiltest für `/api/v1/sync/changes/wait`:
- 255 getrennte Portal-Sessions vorbereitet;
- 255/255 Requests erfolgreich, 0 Fehler;
- Wall 16,43 s, Median 14,28 s, Maximum 15,79 s;
- Testsession-Cleanup meldete zunächst 253/255; über gezielte DB-Kontrolle wurden 7 noch aktive Python-Test-Sessions gefunden und ausschließlich diese Test-Sessions auf `revoked_at` gesetzt;
- anschließende Kontrolle: 0 aktive Python-Test-Sessions.

Status: **KAPAZITÄTSTEILTEST BESTANDEN**, aber **kein vollständiger 15×15-E2E**.

Neue Server-Reihenfolge:
1. verantwortliches TB-Handy ↔ Server/Portal funktional E2E;
2. HELPER/Poco-Syncfehler reproduzierbar eingrenzen und beheben;
3. danach weitere gemeinsame Fach-E2Es;
4. vollständige Lastwiederholung nach 8-GB-RAM-Ausbau bzw. auf neuem NAS.

Keine Server-Performanceänderung aus diesem Test ableiten, solange kein reproduzierbarer funktionaler Engpass vorliegt.


## IMG-001 Quellaudit
Status: **SERVER-RENDITIONS VORHANDEN / ANDROID-NUTZUNG FEHLT**

- Upload-Complete speichert empfangene Bytes unverändert; kein heimlicher Server-Downscale von APP-Uploads.
- APP-Volloriginale auf dem NAS sind aktuell Folge des Android-Uploads.
- `image_renditions.py`: `thumb` 320×320/JPEG72 und `preview` 1280×1280/JPEG82, gecacht und speicherbegrenzt.
- Dateiliste und Metadaten liefern Rendition-URLs.
- Für den Kernfehler ist kein Server-Ersatzumbau vorgesehen. Android soll kleine Sync-Dateien senden und mobile Renditions nutzen.
- Migration 015/Rendition-/Metadaten-Runtime-E2E bleibt offen.

## BTB-SYNC-001 Praxisfehler 26.09
- Pixel hat neuen BTB 26.09 angelegt.
- Portal zeigt weiterhin nur bis 25.09.
- Poco sieht 26.09 kurz, anschließend „kein BTB vorhanden“.
- Serverdiagnose hat Vorrang: Team-`active_btb_id`, BTB-Status/Teamzuordnung, `client_entity_key`, Versionen, Change-Feed und Team-BTB-API prüfen. Keine DB-/API-Änderung auf Verdacht.

### BTB-SYNC-001 Diagnose – kein Serverfix vor Versionsgleichstand
DB-Diagnose zeigt Pixel mit Android-App `1.3.4` und Poco mit `1.3.19`. Change-Feed enthält mehrere Updates desselben BTB bis mindestens Version 19. Server-/DB-Änderungen bleiben gesperrt, bis der Pixel auf dem aktuellen Android-Zielstand getestet wird.

### Geräte-Metadaten app_version sind Aktivierungs-Snapshot
Quellprüfung:
- `/api/v1/device/login` schreibt `app_version` und `app_build` in `tb.devices`.
- `current_device_user` aktualisiert bei normalen Requests nur `last_seen_at`.
- Nach einem normalen APK-Update ohne Neuaktivierung bleibt daher die alte App-Version in `tb.devices`.

Folge: `tb.devices.app_version` darf aktuell nicht als Runtime-Versionsnachweis für E2E verwendet werden. Kein Serverfix daraus ohne separaten Auftrag.

### BTB-SYNC-001 – Server-Existenz bestätigt / Update-Serie
- 26.09-BTB `ba2144b7-9ede-4f53-abdc-1accd172e875` existiert serverseitig.
- Change-Feed zeigt in kurzer Folge Updates bis mindestens Version 19.
- Kein Nachweis eines fehlenden Pixel-Uploads; Ursache liegt nach aktuellem Stand hinter dem Server-Eingang bzw. in Lifecycle/Read-Modell.
- `active_btb_id`, Teamzuordnung, Status und Teilnehmerzustände müssen noch direkt für diesen BTB geprüft werden.

### BTB-SYNC-001 – DB-Lifecycle bestätigt

- Direkter DB-Check für BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`:
  - Baustelle: `GE Immenstadt - Kempten`
  - Team: `pixeljens`
  - Team-Lead: `zinkel.Jens`
  - `teams.active_btb_id` == BTB-ID
  - `active_date=2026-09-26`
  - `active_status=ACTIVE`
  - BTB selbst: `status=ACTIVE`, `version=19`
  - Client-BTB-ID vorhanden
  - Poco = HELPER / participant `WORKING`
  - zinkel.Jens = LEAD / participant `WORKING`
- Damit ist der kanonische Server-Lifecycle für den 26.09-BTB korrekt. Ursache liegt jetzt im Portal-/Android-Lese-/Anwendungspfad, nicht in fehlendem `active_btb_id` oder Teilnehmerstatus.

Kein Server-DB-Fix auf Verdacht.

### BTB-SYNC-001 Portal-Read bestätigt
Portal-Neuladen zeigt den 26.09-BTB korrekt als ACTIVE und verlinkt denselben `active_btb_id`. Kein weiterer Server-/Portal-Fix aus diesem Befund.

## IMG-PORTAL-001 Praxisfehler
Aktiver BTB 26.09: Server/NAS enthält 4 zugehörig wirkende Bilder, Portal zeigt nur 1. Ursache noch offen. Zuerst Attachment-Zuordnung und BTB-API/Portal-Read prüfen; keine Serveränderung auf Verdacht.

## PORTAL-LIVE-001 – F5-Abhängigkeit bestätigt
- Nach manuellem Browser-Reload (F5) erscheinen die zuvor fehlenden BTB-Bilder vollständig im Portal.
- Damit ist die Bildzuordnung/Serverablage grundsätzlich vorhanden; der aktuelle Praxisfehler ist **fehlende Live-Aktualisierung der offenen Portalansicht**.
- Benutzeranforderung: Neue BTBs/Bilder/Fachdaten sollen in einer offenen Portalansicht automatisch sichtbar werden; F5 darf für normalen Betrieb nicht nötig sein.
- Bestehende Architekturregel gilt: offene Portalansichten dürfen live aktualisieren, aber nicht über 2-Sekunden-DB-Polling pro Browser. Vorhandener Change-Feed/SSE-/Long-Poll-Pfad ist zu verwenden.
Server-Seite ist zuständig; Umsetzung im Portal-JavaScript gegen vorhandenen Change-Feed.

## PORTAL-LIVE-001 – QUELLSTAND FERTIG
- Source-Fix Commit: `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- `v1_btb.html` und `v1_worksite.html` abonnieren jetzt den vorhandenen worksite-bezogenen SSE-Change-Feed `/api/v1/sync/events`.
- Relevante Änderungen werden entprellt und gezielt nachgeladen; auf der BTB-Seite insbesondere `BTB`, `BTB_ENTRY`, `BTB_PARTICIPANT` und `FILE`.
- Baustellenseite aktualisiert Teams/BTBs, Medien, Mängel, Abnahmen, Mitglieder und Baum abhängig vom Change-Typ.
- Kein 2-Sekunden-Polling; vorhandener gemeinsamer Change-Hub wird verwendet.
- Source-Assertions für EventSource/SSE-Pfad und gezielte Reload-Funktionen: **PASS**.
- Status bleibt: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.

### BTB-SYNC-002 – Serverdiagnose erforderlich, kein Fix auf Verdacht
Aktueller Praxisbefund ist asymmetrisch: Portal→Pixel funktioniert, Pixel→Portal nicht; Portalstand überschreibt Pixeländerung. Vor Serveränderung müssen Change-Feed, BTB-/Entry-Versionen und tatsächlich eingehende Pixel-Mutationen geprüft werden.


## PORTAL-LIVE-001 – aktueller Deployauftrag
Status: **QUELLSTAND FERTIG / DEPLOY JETZT / E2E DANACH**

- Benutzer bestätigt: Pixel↔Portal synchronisiert fachlich in beide Richtungen.
- Nächster Server-Schritt ist deshalb jetzt ausschließlich der bereits fertige Live-Refresh des Portals.
- Commit `c0355161a2d7ecceaadd1fc79f2bbade346a78cc` bindet BTB- und Baustellenseite an den vorhandenen SSE-Change-Feed.
- Ziel: neue BTBs, BTB-Änderungen und Bilder erscheinen in einer geöffneten Portalansicht automatisch; F5 entfällt.
- Deployment über GitHub Desktop → File Station → Container Manager; kein NAS-SSH.


## PORTAL-LIVE-001 – praktisch bestätigt
Status: **BESTÄTIGT**

- Benutzer bestätigt nach Deployment: offene Portalansichten aktualisieren sich ohne F5.
- Reaktionszeit im Praxistest: ca. 1–2 Sekunden.
- Erstellen und Löschen werden automatisch sichtbar.
- Synchronisation funktioniert in beide Richtungen.
- SSE-/Change-Feed-Pfad gilt damit praktisch als bestätigt und bleibt geschützt.

## PORTAL-BTB-UI-001 – BTB im Portal nacharbeiten
Status: **OFFEN**

- Benutzer meldet ausdrücklich: BTB im Portal muss noch nachgearbeitet werden.
- Das ist ein separater Portal-/Darstellungs-/Facharbeitsblock und kein Live-Sync-Fehler.
- Bestehenden bestätigten PORTAL-LIVE-001-Pfad dabei nicht ohne reproduzierbaren Grund ändern.


## BTB-EDIT-001 – strukturierte Nachbearbeitung
Status: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**

Umgesetzt:
- Migration 016: versionierte `file_links.link_metadata`.
- Entry-PATCH unterstützt Abschnittswechsel 4–8 bei gleicher Entry-ID.
- stabile Unterbaustellenreferenz `worksiteNodeKey` wird serverseitig validiert.
- Foto-PATCH speichert BTB-spezifische Fachzuordnung auf der Verknüpfung; Original/GPS/Aufnahmezeit bleiben unverändert.
- Portal-BTB: Firma → Unterbaustelle → Eintrag, mit nachträglicher Änderung von Punkt/Firma/Unterbaustelle/Zeit/KM/Text.
- Portal-Fotos: nachträgliche Änderung von Punkt/Firma/Unterbaustelle/Kurztext/KM/Objekt.
- Worksite-Node-Auswahl nutzt denselben `client_node_key`/Endpoint wie Android.
- PORTAL-LIVE-001 / EventSource unverändert erhalten.

Quellprüfungen:
- Checks 94–100 fachlich gegen aktuellen Quellstand: PASS.
- kompletter Portal-JS-Block via JS-Parser: PASS.
- GitHub-Actions-Run ohne Runner-Steps wird nicht als Test gewertet.

Deployment:
- neue Migration 016 muss vor Runtime-Nutzung angewendet sein;
- danach Server/API/Portal neu deployen;
- E2E erst zusammen mit passendem Android-Stand.


## INT-001 – Android-Quellstand integriert
Status: **INTEGRIERT / ANDROID-BUILD-RETEST OFFEN**

- Android-Parent `606aa3ad1f8ea438372484b056a0fc1264ae2aac` in den gemeinsamen Integrationsstand übernommen.
- `server/**` blieb inhaltlich unverändert; `app/**` stammt unverändert aus dem Android-Parent.
- Server-Deploy/Migration 016 bleibt separat offen.
- Android 1.3.25/200075 Build-Retest/Installation/E2E bleibt separat offen.


## INT-001 – Integration praktisch abgeschlossen
- gemeinsamer HEAD: `ee8358e0cb403347a17d389dcad562ac6858aff7`;
- Android PR #7 merged/closed;
- nächster SERVER-Schritt: Synology-Deploy des integrierten Serverstands und Migration 016;
- GitHub Actions auf dem Merge-HEAD liefen nicht: beide Jobs 0 Steps, daher kein CI-Testnachweis.


## BTB-EDIT-001 – Synology Upload bestätigt
Status: **UPLOAD BESTÄTIGT / MIGRATION OFFEN**

- Benutzer bestätigt: die fünf benötigten BTB-EDIT-001-Dateien liegen auf der Synology.
- Staging-Compose bindet `tbserver/`, `templates/` und `v1_migrations/` read-only direkt vom NAS ein.
- Deshalb ist kein Image-Rebuild wegen dieser Python-/Template-/SQL-Dateien erforderlich.
- Vor API-/Worker-Runtime mit dem neuen Schema muss der einmalige `migrate`-Service Migration 016 erfolgreich ausführen.


## BTB-EDIT-001 – Migrationsrunner erfolgreich, 016 noch zu verifizieren
- API-Terminal: `python -m tbserver.migrations up` → `OK`.
- Keine sichtbare `APPLIED 016_...`-Zeile.
- Daher noch **nicht** als „Migration 016 bestätigt“ werten.
- Nächster Nachweis: `python -m tbserver.migrations status`; erwartet werden `pending: []`, `latest_known: 16`, `latest_applied: 16`.


## BTB-EDIT-001 – Migration 016 praktisch bestätigt
Status: **MIGRATION BESTÄTIGT**

- Praxisnachweis im API-Terminal: `APPLIED 016_btb_photo_link_metadata.sql`.
- DB-Schema für versionierte BTB-Foto-Link-Metadaten ist damit vorhanden.
- API/Worker liefen beim Anwenden noch als bestehende Prozesse; daher müssen sie jetzt neu gestartet werden, damit der hochgeladene Python-Quellstand sicher aktiv ist.
- Kein DB-/Storage-/Backup-Reset erforderlich.

## ADMIN-FINISH-001 / ADMIN-STORAGE-001 – Admin-Quellblock 28.09.2026
Status: **QUELLSTAND FERTIG / NICHT DEPLOYT / E2E OFFEN**

Neu im Server-Admin:
- moderne automatisch aktualisierte Übersicht mit NAS-Speicher, CPU/RAM, HTTP-Datenverkehr, Problemen und Firmen-Speicher;
- Firmenverwaltung gebündelt für Stammdaten, Benutzer, Geräte, Baustellen, Speicher und Logo/PDF;
- echte PDF-Vorschau über denselben `CompanyHeaderFlowable` wie serverseitige BTB-PDFs;
- Benutzer nach Firma gruppiert;
- Bahnhof/Betriebsstelle mit Android-ausgerichteter Kürzel-/Namenssuche;
- geschützter Trassierungsdaten-Import für TRA/GRA/KF/KF.TXT/KV/GVP/KOR/CSV/ZIP;
- sichtbare Speicherangaben primär in MB/GB;
- keine Änderungen unter `app/**`.

ADMIN-STORAGE-001:
- ältere `survey_originals/from-file`-Importe können denselben `storage_key` zugleich in `tb.files` und `tb.survey_originals` führen;
- Speicherstatistik dedupliziert diesen Altbestand jetzt nach `storage_key`;
- gemeinsam referenzierte Inhalte werden nur als Trassierungs-/Vermessungsoriginal gezählt;
- keine Altbestandsmigration oder Datenlöschung.

Prüfung:
- Source-Assertions der Deduplizierung: PASS.
- GitHub Actions auf aktuellem HEAD ohne Runner-Steps (`steps = null`) → kein CI-Testnachweis.
- Deployment auf Synology und Runtime-/Browser-E2E für diesen Admin-Block offen.

## ADMIN-RUNTIME-002 – Deployment/Praxisbefund 28.09.2026
Status: **DEPLOYT / ÜBERSICHT + IMPORT LAUFEN / TEILFEHLER OFFEN**
- neue Admin-Übersicht ist auf Synology sichtbar und aktualisiert sich automatisch;
- reale Werte für NAS, CPU/RAM, Verkehr und Firmen-Speicher werden angezeigt;
- Trassierungsdaten-Upload hat praktisch Dateien gespeichert und ausgewertet;
- Bahnhof-Autofill `MIB` funktioniert praktisch nicht;
- `Firma verwalten` funktioniert praktisch nicht;
- derzeitiger Trassierungsimport ist technisch firmen-/baustellengebunden; neue Fachanforderung verlangt einen gemeinsamen Referenzdatenbestand für alle Firmen/berechtigten Nutzer;
- Datenverwaltung/Import-UI soll als nächster UX-Block moderner und zweispaltiger werden;
- keine Fehlerbehebung auf Verdacht: MIB-API/Datenpfad und Firmenroute zuerst gezielt prüfen.

### ADMIN-RUNTIME-002 – Firmenverwaltung 500 Source-Fix
- reproduzierter Psycopg-Fehler in `admin_web.py` behoben;
- Ursache: literales `%` in `LIKE 'image/%'` innerhalb einer parametrisierten `%s`-Query;
- Fix: Bildklassifizierung verwendet jetzt `left(lower(mime_type), 6) = 'image/'` bzw. `<> 'image/'`;
- keine Datenbank-/Bestandsänderung;
- Source-Check: alte LIKE-Muster entfernt, neue Prüfung vorhanden;
- Status: **SOURCE-FIX VORHANDEN / SYNLOGY-UPLOAD + RUNTIME-RETEST OFFEN**.

### ADMIN-RUNTIME-002 – Firmenverwaltung Retest 28.09.2026
- Benutzer-Screenshot zeigt `Firma verwalten` erfolgreich geladen.
- Firmenübersicht sowie Reiter Benutzer/Geräte/Baustellen/Speicher/Logo-PDF sind erreichbar.
- Der zuvor reproduzierte HTTP-500-Fehler ist damit praktisch **BEHOBEN**.
- Nächste Nacharbeit: firmenzentrierte Admin-Struktur gemäß `ADMIN-STRUCTURE-004` planen; noch keine Navigation umbauen.


### ADMIN-RUNTIME-002 – Firmenverwaltung Retest 28.09.2026
- Benutzer-Screenshot zeigt `Firma verwalten` erfolgreich geladen.
- Firmenübersicht sowie Reiter Benutzer/Geräte/Baustellen/Speicher/Logo-PDF sind erreichbar.
- Der zuvor reproduzierte HTTP-500-Fehler ist damit praktisch behoben.
- Nächste Nacharbeit: firmenzentrierte Admin-Struktur gemäß `ADMIN-STRUCTURE-004` planen; noch keine Navigation umbauen.

## ADMIN-STRUCTURE-004 – Firmenzentrierte Admin-Verwaltung
Status: **SOURCE FERTIG / DEPLOY OFFEN**
- Hauptnavigation auf Firmen als Organisations-Einstieg verdichtet;
- Benutzer/Geräte/Baustellen aus der globalen Hauptnavigation entfernt;
- Firmenliste als Kartenansicht mit Benutzer-/Geräte-/Baustellen-Kennzahlen;
- Firmenworkspace mit linker Navigation statt horizontaler Tabs;
- Benutzeranlage/-bearbeitung, Portalaktivierung und Löschpfade in der Firma;
- gebundene Geräte inkl. Blockieren/Bindung lösen/Komplett löschen in der Firma;
- Admin-Baustellenanlage innerhalb der Firma mit verantwortlichem TB;
- Speicher und Logo/PDF bleiben im Firmenworkspace;
- Dashboard-Schnellzugriff an die neue Struktur angepasst;
- sichtbarer 5-Sekunden-Auto-Refresh-Hinweis entfernt, Funktion bleibt aktiv;
- keine Android-Dateien geändert.

Source-Checks:
- Template-JavaScript: PASS;
- Admin-Worksite-Route/Team/Membership/Change/Audit: vorhanden;
- CI ohne Runner-Steps: kein Testnachweis.

Offen:
- geänderte Serverdateien auf Synology übernehmen;
- API neu starten;
- Firmenliste, Benutzeranlage, Geräteaktionen, Baustellenanlage und PDF-Bereich praktisch testen;
- MIB/Bahnhof-Autofill bleibt separater offener Fehler.

## ADMIN-COMPANY-005 – Aktivierungskarte/Gültigkeit/Geräteversion
Status: **SOURCE-FIX FERTIG / DEPLOY + RUNTIME-RETEST OFFEN**
- Aktivierungskarte kehrt jetzt direkt zu `Firma verwalten → Benutzer` zurück;
- alte globale Listenpfade `/admin/v1/users`, `/admin/v1/devices`, `/admin/v1/worksites` leiten auf `Firmen` um und öffnen nicht mehr die alte Doppelverwaltung;
- Benutzer-Gültigkeit wird in Europe/Berlin berechnet und als Datum + Restdauer/abgelaufen angezeigt;
- Aktivierungskarte zeigt dieselbe Gültigkeitsinformation;
- Geräteansicht bezeichnet gespeicherte App-Version ausdrücklich als `zuletzt vom Gerät gemeldet`;
- aktuell veröffentlichter Android-Release wird daneben aus `tb.releases` angezeigt;
- Server ist für `X-TB-App-Version` und `X-TB-App-Build` bei authentifizierten Gerätekontakten vorbereitet;
- bestehendes Android sendet diese Angaben nach Aktivierung noch nicht; deshalb ist für dauerhaft aktuelle Geräteversion DEVICE-INFO-001 im Android-Projekt offen;
- keine `app/**`-Änderung durch SERVER.

Source-Checks:
- JavaScript Firmenworkspace + Aktivierungskarte: PASS;
- Rücksprung/Redirects/Gültigkeitshelper/Releaseanzeige/App-Header-Vertrag source-seitig vorhanden;
- GitHub Actions: `failure` mit `steps = null` → kein Testnachweis.

### ADMIN-COMPANY-005 – Benutzer-Gültigkeit beim Bearbeiten
- Benutzer bestätigt die neue Firmen-/Geräteansicht praktisch als passend.
- In `Firma → Benutzer → Bearbeiten` ist jetzt zusätzlich die komfortable Auswahl `Kein Ablauf / 3 Monate / 6 Monate / 1 Jahr / Manuell` vorhanden.
- `Letzter gültiger Tag` wird passend automatisch gesetzt/geleert bzw. manuell gewählt.
- keine API-/DB-/Android-Änderung.
- Source-JavaScript: PASS.
- Status dieses Nachtrags: **SOURCE FERTIG / DEPLOY-RETEST OFFEN**.


## ADMIN-DASH-006 – Übersicht + Bahnhof/MIB
Status: **SOURCE FERTIG / DEPLOY OFFEN**

Umgesetzt:
- Dashboard optisch verdichtet und breitere Bildschirmnutzung verbessert;
- Bestand und Firmenspeicher auf Desktop nebeneinander;
- KPI-/Schnellzugriff-/Speicher-Darstellung verfeinert;
- Warnungen können direkt nach Prüfung quittiert werden;
- Restbeschriftung `automatisch aktualisiert` entfernt, Auto-Refresh bleibt;
- Navigation konsistent `Streckendaten`;
- Synology-API erhält read-only Mount `./data:/app/data`;
- Systemstatus warnt künftig, wenn die Bahnhof-Referenzdateien im API-Container fehlen;
- Bahnhofsuche zeigt Referenzfehler sichtbar;
- offizieller Bahnhofscode geprüft: `MIH = Ingolstadt Hbf`; `MIB` existiert nicht als exakter Referenzcode;
- Tippfehlerhilfe liefert ähnliche offizielle Kürzel, speichert aber keinen erfundenen Alias.

Prüfung:
- Dashboard-JavaScript: PASS;
- Bahnhof-JavaScript: PASS;
- Source-Assertions für Data-Mount, Station-Fuzzy-Ranking und Systemwarnung: PASS;
- keine Android-Dateien geändert.

Deployment-Hinweis:
- Wegen neuem Volume-Mount genügt kein einfacher API-Restart;
- Synology-Projekt/API muss mit aktualisiertem Compose neu erstellt/recreated werden.


## ADMIN-PDF-007 – PDF-Logo-Layout
Status: **SOURCE FERTIG / DEPLOY-RETEST OFFEN**
- Runtime-Fehler bestätigt: Sliderwerte sichtbar, PDF-Vorschau reagierte nicht zuverlässig.
- Ursachen: Preview ignorierte ungespeicherte Werte; Server-Headerhöhe begrenzte quadratische Logos stark.
- Fix: Draft-/Live-Vorschau über aktuelle Reglerwerte; explizites Speichern bleibt firmenweite Persistenz; 62-mm-Firmenkopf / Android-nahe 174-pt-Logo-Zone; Renderer `tb-server-btb-pdf-4`.
- finaler serverseitiger BTB-PDF nutzt denselben korrigierten Header.
- zusätzlicher Synology-Fix: Restore-Test-Healthcheck erzeugt keine falschen POSTGRES_USER/POSTGRES_DB-Interpolation-Warnungen mehr.
- keine Migration, keine Android-Änderung.

## ADMIN-DASH-008 – Festplatten-Temperaturen
Status: **SOURCE FERTIG / DEPLOY-RECREATE + RUNTIME-RETEST OFFEN**
- vorhandene generische `Systemtemperatur` bleibt unverändert;
- zusätzlich echte physische Laufwerkstemperaturen je erkannter Synology-Disk in °C;
- neue Hostquelle: `/run/synostorage/disks/*/temperature`, im API-Container read-only als `/host/synostorage/disks`;
- API liefert `host.disk_temperatures`; Dashboard zeigt getrennte Werte als `Laufwerk 1`, `Laufwerk 2`, …;
- keine statischen Werte; bei fehlender Hostquelle erscheint `Nicht verfügbar`;
- Compose + Sourcecheck angepasst; keine Migration, keine Android-Änderung.

Deployment:
- aktualisierte Serverdateien + Compose auf Synology übernehmen;
- wegen neuem Volume-Mount API/Projekt **recreaten**, Restart allein reicht nicht;
- danach Dashboardwerte gegen DSM-Speicher-Manager vergleichen.

### ADMIN-DASH-008 – CI-Nachtrag
- GitHub Actions auf Code-HEAD `051c7d87b3b5cc849bb4faff54259d9ffee639ca`:
  - TB-Server Runs `36488381133` und `36488374650`: `failure`, Job `foundation`, `runner_name = ""`, `steps = []`;
  - Android Run `36488381240`: `failure`, Job `build`, `runner_name = ""`, `steps = []`.
- Bewertung gemäß Projektregel: **keine Runner-Steps ausgeführt → kein Build-/Testnachweis und kein Sourcefehler-Nachweis**.
- ADMIN-DASH-008 bleibt **SOURCE FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**.

## PDF-RUNTIME-009 – Server-PDF-Renderer
Status: **SOURCE-FIX FERTIG / DEPLOY + VISUAL-RETEST OFFEN**
- Historischer 22.09-Export enthält ein Android/Skia-PDF und ist optisch korrekt.
- 25.09/26.09 enthalten serverseitige ReportLab-Abschluss-PDFs (`tb-server-btb-pdf-3`) und zeigen das abweichende generische Serverlayout.
- Aktueller Android-`PDF Entwurf` bleibt korrekt; deshalb kein Android-Fix.
- Server-`render_btb_pdf()` rendert derzeit bewusst anders als `BautagebuchPdf.kt`: generische Meta-/Kopfdaten-Tabellen statt Android-Boxlayout.
- Nächster Serverblock: Android-Layout als Referenz nehmen und den serverseitigen Abschlussrenderer auf dieselbe visuelle Struktur bringen; danach 22.09 als Referenz visuell gegen Server-PDF testen.

### PDF-RUNTIME-009 – Renderer v5
- Autoritativer Abschluss-PDF-Pfad wurde von der generischen Tabellenansicht auf einen Android-nahen festen A4-Renderer umgestellt.
- Neuer Quellbaustein `btb_pdf_android_style.py`; `btb_pdf.py` delegiert `render_btb_pdf()` an Renderer `tb-server-btb-pdf-5`.
- Serverdaten bleiben die Quelle; es wird kein Android-PDF hochgeladen oder als Fremdartefakt übernommen.
- Worksite-Nodes und Lead-Kontaktdaten werden nur lesend in den PDF-Kontext aufgenommen.
- keine DB-/Compose-/Android-Änderung.
- Für Synology-Staging reicht nach Dateiübernahme ein **API-Neustart**; für diesen PDF-Fix ist kein neues Volume und kein Image-Rebuild erforderlich.
- Bestehende bereits finalisierte PDFs werden nicht still neu geschrieben. Visual-Retest mit neuem Test-/Tagesabschluss durchführen.

## ADMIN-PDF-010 – Admin-Vorschau-Parität
Status: **SOURCE-FIX FERTIG / DEPLOY-RETEST OFFEN**
- alte generische Admin-Vorschau entfernt;
- Vorschau rendert jetzt über `tb-server-btb-pdf-5`-Layoutpfad;
- dieselbe Logo-/Offset-Logik wie im Abschluss-PDF;
- synthetische Vorschau enthält Wetter, Personal, Maschine, Hauptbaustelle, freie Strecke/KM sowie Punkte 4–8;
- Personal/Maschinenboxen folgen dem bestätigten Zielbild mit eigener Höhe.

Deployment:
- `server/tbserver/btb_pdf.py` und `server/tbserver/btb_pdf_android_style.py` ersetzen;
- API-Container neu starten;
- kein Recreate und keine Migration erforderlich.

## ADMIN-PDF-011 – Breite/Y-Regler
Status: **SOURCE-FIX FERTIG / RUNTIME-RETEST OFFEN**
- `logo_width_pt` serverseitig 40..400;
- Admin-UI/Preview/Persistenz auf 400;
- Y-Versatz im v5-Renderer repariert;
- A4-Seitengrenzen bleiben Schutzgrenze;
- nachfolgender Inhalt berücksichtigt tatsächliche Firmenkopfhöhe;
- keine Migration/Compose.

Android-Hinweis:
- aktueller Android-Branch clamp`t weiterhin 40..250;
- notwendige Android-Anpassung ist nur dokumentiert, nicht vom SERVER geändert.

## ADMIN-PDF-012 – Feste Fachlayout-Position
Status: **SOURCE-FIX FERTIG / RUNTIME-RETEST OFFEN**
- Firmenlogo ist nur noch ein unabhängig positionierter Zeichen-Layer.
- Logo-Breite/X/Y verändern nicht mehr `writer.y` des Fachlayouts.
- Wetter, Personal/Maschinen und Punkte 4–8 bleiben auf festen Positionen.
- Admin-Vorschau und finaler BTB-Abschluss teilen dieselbe Logik.
- keine Migration, kein Compose, keine Android-Änderung.

## ADMIN-PDF-013 – Titel-Schutzlayer
Status: **SOURCE-FIX FERTIG / RUNTIME-RETEST OFFEN**
- `BAUTAGEBUCH` bleibt jetzt unabhängig von Logo-Breite/X/Y vollständig sichtbar.
- Titel wird zuletzt gezeichnet und erhält einen eigenen weißen Hintergrundbereich.
- Tabellenpositionen bleiben durch ADMIN-PDF-012 unverändert.
- keine Migration, kein Compose, keine Android-Änderung.

## PDF-ADMIN – Runtime-Abnahme 29.09.2026
Status: **DEPLOYT / BESTÄTIGT / ABGENOMMEN**
- Synology-Runtime durch Benutzer praktisch geprüft.
- Admin-PDF-Vorschau entspricht dem bestätigten Zielbild.
- Logo-Breite bis 400 pt funktioniert.
- X-/Y-Versatz funktioniert.
- Logo verschiebt Wetter/Tabellen/Fachblöcke nicht mehr.
- `BAUTAGEBUCH` bleibt bei großem/verschobenem Logo vollständig sichtbar.
- Benutzerabschluss: **`passt und abgenommen`**.

Geschützt:
- ADMIN-PDF-007, ADMIN-PDF-010, ADMIN-PDF-012, ADMIN-PDF-013 nicht ohne Grund ändern.
- ADMIN-PDF-011 SERVER-Anteil ebenfalls bestätigt; nur Android 400-pt-Parität bleibt offen.

## REF-DATA-001 – Globaler Referenzbestand Phase 1
Status: **SOURCE FERTIG / DEPLOY OFFEN / E2E OFFEN**
- Admin-Importziel `GLOBAL_REFERENCE` vs. `COMPANY_WORKSITE_DATA` umgesetzt.
- Global = `survey_originals.company_id NULL`; Worksite/Firma = bestehende company_id.
- Global-Duplikate: `sha256 + byte_size`, keine Überschreibung.
- Globaler Manifest-/Parsed-Readpfad für Portal/Android ergänzt.
- `survey-sections` können globale Originale bereits als Quellen verwenden.
- Globaler bearbeitbarer Katalog `tb.catalog_*` bleibt getrennt/versioniert; keine Rechteänderung in diesem Block.
- Keine Migration / kein Compose / keine Android-Datei.

Synology-Deployment:
- `server/tbserver/admin_survey_import.py`;
- `server/tbserver/survey_originals.py`;
- `server/tbserver/capabilities.py`;
- `server/templates/v1_admin_survey_import.html`;
- danach nur API-Container neu starten.

## REF-DATA-001/002 – Datei-zuerst-Importvorschau
Status: **SOURCE FERTIG / DEPLOY + RUNTIME-TEST OFFEN**
- Analyse und endgültiger Import sind jetzt getrennte Schritte.
- Voranalyse persistiert noch kein `survey_originals`-Objekt.
- Server normalisiert TRA-Linien und Punktdaten für eine kompakte Vorschau.
- Admin-Seite zeigt erkannte Inhalte grafisch und fragt Ziel/Zuweisung erst danach.
- ZIP-Import ist dateibezogen.
- 6-h-TTL für nicht bestätigte Analyse-Sessions.
- Capability `survey_import_preview_v1`.

Synology-Deployment:
- `server/tbserver/admin_survey_import.py` ersetzen;
- `server/tbserver/survey_preview.py` **neu**;
- `server/tbserver/capabilities.py` ersetzen;
- `server/templates/v1_admin_survey_import.html` ersetzen;
- danach nur API-Container neu starten.

## REF-DATA-002 – Pasing-Grafik Runtime-Korrektur
Status: **SOURCE FERTIG / DEPLOY + RETEST OFFEN**
- Ursache der farbigen Dreiecke: `.geom-*` setzte SVG-`fill` auf dieselben Polylinien; CSS-Reihenfolge übersteuerte `fill:none`.
- Fix: Linien und Punktmarker haben getrennte Semantik/Styles.
- Fachliche Gruppierung aus Android übernommen:
  - Streckenachsen/Richtungen;
  - Bahnhofsgleise;
  - WVB;
  - Fest-/Absteckpunkte.
- TRA/GRA derselben Achse werden in Importauswahl zusammengeführt.
- Pasing-ZIP bekommt logisch dieselben Kerninformationen wie Android: Strecken 5524/5528/5529, 10 Bahnhofsgleise, WVB 10-11/14-22/19-20.
- JavaScript-Syntaxprüfung des finalen Templates: PASS.

Synology für diesen Retest:
- `server/tbserver/admin_survey_import.py` ersetzen;
- `server/templates/v1_admin_survey_import.html` ersetzen;
- `server/tbserver/survey_preview.py` muss weiterhin vorhanden sein;
- API-Container neu starten.
