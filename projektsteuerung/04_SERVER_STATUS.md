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

## REF-DATA-002 – Punktdaten/Großansicht
Status: **SOURCE FERTIG / RUNTIME OFFEN**
- Normalansicht auf 520 px erhöht.
- Button `Großansicht` öffnet die Vermessungsgrafik bildschirmfüllend; ESC/Schließen beendet.
- Achsenlabels werden nicht mehr ungefiltert alle gleichzeitig in voller Länge gezeichnet.
- Punktlabels folgen Android-Zoomlogik: bei kleinem Zoom keine Textwolke; ab näherem Zoom begrenzt sichtbar; ab höherem Zoom mit KM.
- Punkt-Klickdetail nutzt vollständigeres normalisiertes Preview-Schema.
- Runtime-Dateien dieses Fixes:
  - `server/tbserver/survey_preview.py`;
  - `server/templates/v1_admin_survey_import.html`.
- API neu starten, keine Migration/kein Recreate.

## REF-DATA-002 – Tiefzoom + belastbare KM-Bereiche
Status: **SOURCE FERTIG / RUNTIME-RETEST OFFEN**
- Weltgeometrie skaliert; Punktmarker/Labels werden dynamisch in Screen-Space-Größe gehalten.
- Tiefzoom bis ca. 30x.
- Sichtbare Punktmarker werden bei starkem Zoom kleiner; Klickfläche bleibt separat erhalten.
- Punkt-/Achsenlabels reduzieren Größe und Dichte bei tiefem Zoom.
- `KM / Station` zeigt keine Nullspanne mehr als gültigen Bereich.
- GRA-basierter KM-Bereich wird bevorzugt; technische Station separat gekennzeichnet.

Synology:
- nur `server/templates/v1_admin_survey_import.html` ersetzen;
- API-Container neu starten;
- `survey_preview.py` und `admin_survey_import.py` bleiben vom vorigen Stand unverändert.

## REF-DATA-002 – Punkt-Hover/Klick + Tiefzoom
Status: **SOURCE FERTIG / RUNTIME-RETEST OFFEN**
- Alte SVG-Punktmarker/-labels entfernt.
- Neue Screen-Space-Ebene hält Punktmarker unabhängig vom Zoom sichtbar.
- Hover zeigt Kurztooltip; Klick öffnet Detaildaten.
- Mehrere nahe Punkte werden auswählbar statt zufällig geöffnet.
- In Großansicht erscheint ausgewählte Punkt-/Achseninfo als schwebende Karte.
- Tiefer Zoom priorisiert Linien; permanente Punktlabels werden ab sehr hohem Zoom praktisch abgeschaltet.

Synology:
- nur `server/templates/v1_admin_survey_import.html` ersetzen;
- API-Container neu starten;
- keine Migration / kein Recreate.

## REF-DATA-002 – GVP-Achskalibrierung + 160x Zoom
Status: **SOURCE FERTIG / RUNTIME-RETEST OFFEN**
- Admin-Preview bildet Android-`kmMitGvpFestpunktenKalibrieren()` fachlich nach.
- GVP/Festpunkte können freie Strecken- und Gleisachsen ohne KM-GRA automatisch grün kalibrieren.
- Kriterien: Strecke gleich, >=4 GVP mit KM, >=4 Achssamples, ≤120 m, robuster Medianoffset ±80 m, >=4 robuste Treffer.
- Grau bei widersprüchlicher GVP-Kalibrierung; rot wenn keine belastbare Quelle.
- Explizite KM-GRA nur bei Android-Namensregel `KM####`/`KMLINIE`/`KM_LINIE`.
- GVP-kalibrierte KM-Bereiche erscheinen in `KM / Station` als `GVP/Festpunkte`.
- Tiefzoom ca. 160x.
- Runtime-Datei: `server/templates/v1_admin_survey_import.html`.
- API neu starten; keine Migration/kein Recreate.
- Benutzerbestätigung 29.09.2026: `okay,passt`.

## REF-DATA-002 – Achseninteraktion + KOR-Parser v3
Status: **BESTÄTIGT**
- Achsen-Hover und Achsen-Klick analog zur Punktinteraktion.
- Unsichtbare 16-px-Trefferzone verbessert Bedienbarkeit ohne optische Linienbreite zu verändern.
- Tiefzoom ca. 2000x.
- `survey_parser.py`: KOR-Metadaten/Header werden erkannt; verdächtige unvollständige Datenzeilen bleiben mit Textausschnitt sichtbar.
- Runtime-Dateien:
  - `server/templates/v1_admin_survey_import.html`;
  - `server/tbserver/survey_parser.py`.
- API neu starten; keine Migration/kein Recreate.

## REF-DATA-003 – SERVER ist jetzt aktiver Bauabschnitt
Status: **UMSETZUNG FREIGEGEBEN / SERVER ZUERST**
- Benutzer hat Reihenfolge festgelegt: SERVER vollständig → Portal → Android.
- Aktueller SERVER-Scope: persistenter Trassendaten-Katalog, Quellen-/Segmentmodell, automatischer achsenweiser Merge, Review-Konflikte, Maileskalation, Such-/Verfügbarkeits-/Geometrie-API und Versionierung.
- Kein Portal-UI-Bau und keine Android-Codeänderung in diesem Block.
- Bestehende bestätigte REF-DATA-002-Achsen/KOR/Tiefzoom-Funktion bleibt geschützt.

## REF-DATA-003 – erster Serverbau: Reviewfehler offen
Status: **SOURCE NOCH NICHT FERTIG**
- Migration 017 + Survey-Katalogdienst + Router/Worker/Mail-/Importverdrahtung sind im Branch vorhanden.
- GitHub-Reread von `0097a24b...` zeigt einen reproduzierbaren Python-Signaturfehler in `admin_survey_import.py`.
- Zusätzlich fehlt im Manual-Segment-PATCH die Fremdfirmen-Scope-Sperre.
- Keine Deployment-/E2E-Freigabe bis beide Fehler korrigiert und Source-Checks nachgewiesen sind.


## REF-DATA-003 – Server-Katalog Quellblock umgesetzt
Status: **QUELLSTAND IMPLEMENTIERT / NICHT DEPLOYT / SYNLOGY-E2E OFFEN**
Code-HEAD des Quellblocks: `a1ef0d991e2ccc06578feced7e9b5a89bdf5f611`.

Umgesetzt:
- Migration `017_survey_reference_catalog.sql`: Assets, Achsen, Segmente, Konflikte, Historie.
- `survey_catalog.py`: Search, Availability, Geometry, Changes, manuelle TRA-/GPS-Segmente, versionierte Achskorrekturen, Konflikte, Reindex.
- Auto-Merge nutzt Fachidentität + X/Y-Überlappung bzw. Endpunkt + Tangente; unsichere Fälle werden `REVIEW_REQUIRED`.
- Grenzwerte: Überlappung <=1 m; Endpunkt <=5 m; Tangentenfehler <=20°; nahe Nichtübereinstimmung bis 20 m wird Prüffall.
- Originale bleiben unverändert/SHA-gebunden. Private Firmen-Rohdatei kann abgeleitete Geometrie ausdrücklich GLOBAL veröffentlichen.
- `source_date`, Betriebsstelle und Katalogscope sind typisiert; „neuer“ bedeutet nicht bloß spätere Uploadzeit.
- Aktive Baustellenmitglieder dürfen Survey-Originalimport/Reparse; Firmen-/Dateizugehörigkeit bleibt geprüft.
- KM-Vertrag: GRÜN automatisch (KM-GRA/GVP), ORANGE manuell, GRAU unplausibel/teilweise, ROT fehlt; GVP >=4 Treffer, <=120 m, Medianoffset, ±80 m.
- Konfliktmail nutzt vorhandenen SMTP-Alertweg; DB-Konflikt bleibt unabhängig vom Mailversand bestehen.
- API-Vertrag: `docs/TB-SERVER_1_0_SURVEY_CATALOG.md`.

Runtime-Dateien mindestens:
- `server/v1_migrations/017_survey_reference_catalog.sql`
- `server/tbserver/survey_catalog.py`
- `server/tbserver/main.py`
- `server/tbserver/jobs.py`
- `server/tbserver/notifications.py`
- `server/tbserver/admin_survey_import.py`
- `server/tbserver/survey_parser.py`
- `server/tbserver/survey_originals.py`

Teststatus:
- Sourcechecks 112/113 vorhanden.
- GitHub Runs `36618626903` und `36618619207`: `steps=[]` → **kein CI-Testnachweis**.
- Migration 017, API/Worker, Reindex, Merge/Review und SMTP sind auf Synology **noch nicht bestätigt**.

Nächster Schritt vor Portal:
1. Serverstand über File Station übernehmen.
2. API/Worker per Container Manager aktualisieren/recreaten; Migration 017 anwenden.
3. Migrationsstand prüfen.
4. `POST /api/v1/survey-catalog/reindex`.
5. Search/Availability/Geometry/Changes gegen die Runtime prüfen.
6. Originalimport + Auto-Merge + `REVIEW_REQUIRED` + Konfliktmail mit Testdaten prüfen.
7. Erst nach Runtime-Bestätigung Portal beginnen.

## REF-DATA-003 – aktueller Source-Audit 7d1441c0
Status: **SERVER-SOURCE WEIT FORTGESCHRITTEN / 2 KORREKTUREN OFFEN**
- Vorherige Syntax-/COMPANY-Scope-Fehler aus `0097a24b...` sind im aktuellen Stand bereits korrigiert.
- Seitdem umgesetzt: Sharing + KM-Qualität, quellensichere GRA-Zuordnung, versionierte gemeinsame Achskorrekturen, Beiträge aller aktiven Baustellenmitglieder und Merge-Check 113.
- GitHub Actions auf HEAD liefern weiterhin `steps=[]`; damit kein CI-Testnachweis.
- Neuer Audit findet:
  1. Teilüberdeckung darf vorhandenen Rest nicht komplett superseden;
  2. normale globale Katalogantworten müssen Bearbeiter/Firma redigieren.
- Vor `SOURCE FERTIG`: beide Punkte korrigieren, Source-Checks nachziehen und Status erneut sichern.

## REF-DATA-003 – Upload-/Cursor-Audit offen
Status: **SERVER-SOURCE NOCH IM ABSCHLUSSAUDIT**
- Teilabdeckung und Antwort-Datenschutz wurden mit `c8910871...` korrigiert.
- Vor Server-Sourceabschluss zusätzlich:
  - Worksite-Fallback für Strecke/Betriebsstelle beim Originalupload;
  - GLOBAL-SHA-Duplikaterkennung ohne Fremdfirmen-Leak;
  - Survey-Change-Feed mit `next_after`, damit unsichtbare COMPANY-Änderungen den Cursor nicht blockieren;
  - optionales `source_date` auch im Admin-Importvertrag.
- Kein Portal-/Android-Code in diesem Block.

## REF-DATA-003 – Lifecycle-Kompatibilität offen
Status: **SOURCE-ABSCHLUSSAUDIT**
- Upload-/Cursorblock ist mit `34c93c1...` umgesetzt.
- Migration 017 muss noch in den bestehenden Firmen-Purge integriert werden, damit erlaubte Staging/Test-Purges nicht an neuen Katalog-Fremdschlüsseln scheitern.
- Produktionsschutz bleibt unverändert.

## REF-DATA-003 – Check 112 Nachlauf
Status: **SOURCE-CHECK-KORREKTUR OFFEN**
- Lifecycle-Code dbcb5be9... ist geschrieben.
- Check 112 enthält noch eine alte Signatur-Assertion vor Einführung von source_date; diese wird jetzt an den aktuellen Vertrag angepasst.

## REF-DATA-003 – letzter Merge-Audit
Status: **SOURCE-ABSCHLUSSKORREKTUR OFFEN**
- Capability-Flags sind mit 065cd1d2 ergänzt.
- Noch zu korrigieren: Geometrievergleich muss bei fehlender Gleis-/Richtungskennung möglich sein, sofern Strecke/Betriebsstelle kompatibel und X/Y räumlich eng sind.
- Mehrdeutigkeit bleibt Reviewfall; keine Lockerung bei widersprüchlichen Identitäten.

## REF-DATA-003 – SERVER-Sourceabschluss 29.09.2026
Status: **SOURCE FERTIG / NICHT DEPLOYT / E2E OFFEN**
Maßgeblicher HEAD: `782e72e18790570066b40da08a80471fa14d759e`

Abgeschlossen:
- Migration `017_survey_reference_catalog.sql`;
- gemeinsamer versionierter Survey-Katalog in `survey_catalog.py`;
- Search / Availability / Geometry / Changes(`next_after`);
- Original- und manuelle Quellsegmente ohne Überschreiben der Rohoriginale;
- Auto-Merge für Überlappung/Endpunkt sowie X/Y-Fallback bei schwacher Achskennung;
- Vollabdeckungs-Guard gegen Verlust von Alt-/Manuell-Restsegmenten;
- `REVIEW_REQUIRED` + Konfliktdatensatz + Background-Mailjob;
- Admin-Konfliktentscheidungen;
- versionierte Nutzerbeiträge und Achskorrekturen;
- GLOBAL/COMPANY-Sicht + Antwort-Redaktion fremder Bearbeiter-/Firmenidentitäten;
- KM-Qualitätsvertrag GRA/GVP/manuell;
- Worksite-Fallback und globale Duplikaterkennung beim mobilen Originalimport;
- `source_date` als fachliches Bestandsdatum;
- Reindex für vorhandene Originale;
- Firmen-Purge/Lifecycle-Kompatibilität für Migration 017 bei unverändertem Produktionsschutz;
- Capability-Flags für späteren Portal-/Android-Consumer.

Source-Nachweis:
- GitHub-Reread/Audit gegen HEAD mit 25/25 Vertrags-/Integrationsmarkern PASS.
- Check 112/113 decken Sourcevertrag, Merge, Teil-/Vollabdeckung, Datenschutz und schwache Achsidentität ab.
- GitHub Actions `36621416472` (PR) und `36621409361` (Push) hatten jeweils `steps=[]`; daher **kein Runner-/CI-Testnachweis**.

Noch offen:
- Migration 017 auf Synology;
- API-/Worker-Runtime;
- Reindex auf echtem Serverbestand;
- Runtimeprüfung Search/Availability/Geometry/Changes;
- echte Imports mit Erweiterung/Überlappung/Review;
- Konfliktmail und Adminentscheidung;
- Postman/Synology-E2E.

Portal/Android bleiben bis zu diesem Runtime-Gate unangetastet.

## REF-DATA-003 – Synology Runtimebasis 30.09.2026
Status: **DEPLOYT / BASIS LAUFEND / FACH-E2E OFFEN**
Screenshot-Nachweis vom Admin-Systemstatus:
- `Migrationen: 17 / 17 · latest 17 / 17`;
- `Ausstehend: keine`;
- Datenbank OK;
- interne API OK;
- Worker OK;
- Cloudflare/öffentliche API OK;
- Umgebung staging, API 1.0.0-dev.

Damit gelten Migration 017 sowie laufende API-/Worker-Basis als praktisch bestätigt. Nicht daraus abgeleitet werden dürfen erfolgreiche Reindex-/Katalog-/Merge-/Mailtests.

Nächste Runtimeprüfungen:
1. Survey-Katalog-Reindex starten und Ergebnis prüfen.
2. Search / Availability / Geometry / Changes gegen die laufende Synology prüfen.
3. Danach Originalimport, Merge/Teilabdeckung, REVIEW_REQUIRED und Konfliktmail mit Testdaten.
4. Erst nach bestandenem Runtime-Gate den Portal-HTML-Block beginnen.

## REF-DATA-003 – Reindex Runtime bestätigt 30.09.2026
Status: **REINDEX BESTÄTIGT / FACH-E2E TEILWEISE**
Synology-API-Container, direkter Runtime-Aufruf:
`python -c "from tbserver.survey_catalog import reindex_all_originals; print(reindex_all_originals())"`

Ergebnis:
- `original_count = 54`
- `ADDED = 31`
- `INDEXED_ASSET = 22`
- `MERGED = 1`
- `failures = 0`

Damit ist bestätigt:
- vorhandene geparste Vermessungsoriginale lassen sich auf der laufenden Synology in Migration-017-Katalogdaten überführen;
- mindestens ein automatischer Merge wurde im echten Bestand erkannt;
- Reindex lief ohne Fehler.

Noch nicht bestätigt:
- fachliche Richtigkeit des einen MERGED-Falls;
- Search / Availability / Geometry / Changes;
- Teilüberdeckung, Weak-ID-X/Y, REVIEW_REQUIRED, Konfliktmail/Adminentscheidung, Upload-Fallback/Duplikat und Lifecycle-Purge.

## REF-DATA-003 – Merge-Runtimefund 30.09.2026
Status: **MERGE-FALL IDENTIFIZIERT / FACHLICHE PLAUSIBILISIERUNG OFFEN**
Read-only-Katalogabfrage auf der Synology:
- erkannte Achsgruppen nach Strecke: 5302 = 3, 5340 = 1, 5524 = 3, 5528 = 11, 5529 = 3, ohne Strecke = 10;
- genau ein automatisch verbundener Segmentfall;
- Achse: `Str 5524-1`, Strecke `5524`, Richtung `1`;
- neues/verbundenes Segment: `ORIGINAL_TRA`, `OVERLAP_CONFIRMED`, `ACTIVE`;
- technische TRA-Station: ca. `1400.0 .. 6121.93415`;
- Quelle: `5524_SL_ER0_Gp2026_02_16.TRA`;
- Gesamtstatus Segmente: `PRIMARY/ACTIVE = 31`, `OVERLAP_CONFIRMED/ACTIVE = 1`.
Bewertung:
- Auto-Merge läuft praktisch;
- fachliche Korrektheit des 5524-1-Merges ist noch nicht bestätigt, weil das zweite Segment derselben Achse und deren Anschluss-/Überlappungsgeometrie noch gegenübergestellt werden müssen.

## REF-DATA-003 – Betriebsstellenzuordnung Runtime-Lücke
Status: **SERVER-NACHARBEIT VOR PORTAL**
- Reindex funktioniert, aber Runtimeabfrage zeigt für alle gelisteten Achsgruppen leere `station_code/station_name`.
- Streckensuche wäre nutzbar; Bahnhofssuche (z. B. Pasing) noch nicht belastbar.
- Ursache/Backfill aus vorhandenen Import-/Referenzdaten jetzt serverseitig prüfen.

## REF-DATA-003 – Betriebsstellenursache bestätigt 30.09.2026
Status: **URSACHE BESTÄTIGT / BACKEND VORBEREITET / ADMIN-UI FEHLT**
- `SurveyImportDecision` besitzt bereits `station_code` und `station_name`.
- `v1_admin_survey_import.html` bietet aktuell nur Strecke + Gleis/Achse und sendet keine Betriebsstelle im Decision-Payload.
- Das erklärt die leeren station-Felder des Pasing-Bestands.
- Nötig: Bahnhofsauswahl über bestehende Station-Reference-Suche, Bulk-Default + Gruppenoverride; freie Strecke darf ohne Bahnhof bleiben.
- Zusätzlich muss ein bestehender Katalogbestand nachträglich einer Betriebsstelle zugeordnet werden können, ohne Rohoriginale zu verändern.

## REF-DATA-003 – Staging-Reset freigegeben
Status: **TESTDATEN-RESET ERLAUBT / NOCH NICHT AUSGEFÜHRT**
- Benutzer bestätigt, dass der aktuelle Vermessungsbestand auf Staging Testdaten ist und erneut eingelesen werden kann.
- Reset erst nach sicherem Löschpfad und nach Ergänzung der Betriebsstellenzuordnung im Import.
- Produktionsschutz bleibt unverändert.

## REF-DATA-003 – Reset-/Importergänzung 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT + RUNTIME OFFEN**
- `GET /api/v1/admin/purge/survey-test-data`: staging-geschützte Vorschau.
- `DELETE /api/v1/admin/purge/survey-test-data`: staging-geschützter Survey-Testreset mit Bestätigungstext.
- Reset entfernt ausschließlich Vermessungs-/Trassendatenabhängigkeiten und zugehörige Raw-Originaldateien; andere Fachbereiche bleiben bestehen.
- `v1_admin_survey_import.html`: Betriebsstellenreferenzsuche ergänzt, Bulk-Default + Gruppenoverride, Payload station_code/name.
- `admin_web.py`: gespeicherte Originale zeigen station_code/name aus Metadaten.
- Check 114/115 ergänzt und im Workflow verdrahtet.
- Source-Nachprüfung: Reset-Schutz/Löschreihenfolge sowie Import-Station-Payload/geschützte Vorschau-Marker PASS.
- Nächster Schritt: neuen Stand auf Synology deployen, Importseite prüfen, Reset-Vorschau ansehen, erst dann ausdrücklich freigegebenen Staging-Reset ausführen und Pasing neu importieren.

## REF-DATA-003 – Importstation + Konfliktmail Runtime 30.09.2026
Status: **BESTÄTIGT TEILBLOCK**
- Admin-Importstation MPB praktisch getestet: Referenzsuche, Bulk-Anwendung und Anzeige je Gruppe funktionieren.
- Neuimport speichert/zeigt MPB; Lauf 6 IMPORTIERT / 34 DUPLIKATE / 0 FEHLER.
- Duplikatverhalten korrekt: bestehende immutable Originale werden nicht wegen neuer Metadaten umgeschrieben.
- Sichtbarer Mail-Eingang zum Trassendaten-Prüffall MPB bestätigt Background-/SMTP-Pfad für mindestens einen echten Konfliktfall.
- Noch offen vor konsistentem Pasing-E2E: Staging-Testbestand resetten und vollständig neu importieren.

## REF-DATA-003 – Survey-Reset-Vorschau Runtime bestätigt 30.09.2026
Status: **PREVIEW BESTÄTIGT / RESET NOCH NICHT AUSGEFÜHRT**
Synology API-Container:
- environment: `staging`
- allowed: `True`
- confirm_text: `VERMESSUNG TESTDATEN LOESCHEN`
- survey_originals: 60
- survey_parse_catalog: 60
- catalog_assets: 60
- catalog_axes: 33
- catalog_segments: 35
- catalog_conflicts: 1
- catalog_history: 164
- survey_sections: 0
- track_source_links: 0
- import_file_rows: 0
Bewertung: der geschützte Resetpfad erkennt den erwarteten Survey-Testbestand und keine abhängigen Survey-Section/Track-Source-Reste. Noch keine Löschung ausgeführt.

## REF-DATA-003 – Änderungs-/Löschbedienung festgelegt
Status: **VERTRAG FESTGELEGT / PORTAL-UI NOCH OFFEN**
- Server kann bereits:
  - manuelle Segmente versioniert ändern;
  - manuelle Segmente auf `INACTIVE` setzen;
  - Achsmetadaten versioniert ändern;
  - Konflikte auflösen;
  - ältere vollständig abgedeckte Segmente auf `SUPERSEDED` setzen.
- Es gibt noch keinen normalen Einzel-Delete-Endpunkt für Vermessungsoriginale; der Staging-Survey-Reset ist bewusst nur Testwerkzeug.
- Nächster Portalblock muss diese Serverfähigkeiten als verständliche Aktionen sichtbar machen und für zulässige Einzel-Löschfälle einen geschützten Adminpfad definieren.

## REF-DATA-003 – Survey-Testreset Runtime BESTÄTIGT
Status: **RESET ERFOLGREICH**
- Staging-Reset ausgeführt.
- 60 gespeicherte Survey-Originaldateien entfernt.
- Survey-Originale, Parserkatalog, Katalog-Assets/Achsen/Segmente/Konflikte/History danach 0.
- Resetpfad damit praktisch bestätigt.
- Nächster Fachschritt: Pasing vollständig mit MPB neu importieren.

## REF-DATA-003 – Strecke/Gleis/WVB/Überlagerung Audit offen
Status: **RUNTIMEBEFUNDE DOKUMENTIERT / URSACHE NOCH ZU PRÜFEN**
- Strecke wird in manchen Gruppen erkannt (Titel/Badge), aber nicht in das Decision-Feld übernommen.
- WVB ist klassifiziert, aber Strecke/Achsidentität teilweise leer; echter Prüffall `NEARBY_GEOMETRY_NOT_MATCHING` auf WVB_14-22 vorhanden.
- Admin-Grafik muss nahe/überlagerte Trassen mindestens so eindeutig vergleichbar machen wie die Android-Grafik.
- Noch kein Code geändert; zuerst Parser/Grouping/Decision-Prefill + Katalog-Merge sowie Android-Suche prüfen.

## REF-DATA-003 – Audit Strecke/Gleis/WVB/Überlagerung abgeschlossen
Status: **URSACHEN EINGEGRENZT / NOCH KEIN CODE**
- Bahnhof+Gleis benötigt serverseitig keine zwingende Strecke; Android-Streckenpunkte-Kontext arbeitet bereits so.
- Leeres Route-Feld bei gleichzeitig erkanntem Route-Badge ist UI-Zustandsfehler, nicht Parser-/Backendverlust.
- WVB-Reviewmails können aktuell durch fehlenden stabilen WVB-Objektschlüssel zwischen unterschiedlichen nahen WVB entstehen.
- Admin-Grafik braucht Multi-Hit/Overlap-Auswahl analog Android.
- Nächster gezielter SERVER-Block: Route-Prefill stabilisieren, WVB/Weiche-Objektidentität im Katalog ergänzen, Admin-Multi-Hit/Overlap-Darstellung.

## REF-DATA-003 – Konflikt-Diagnose-UI gefordert
Status: **ANFORDERUNG DOKUMENTIERT / NOCH KEIN CODE**
- Open-Conflict/Warnmeldungen müssen im Portal anklickbar sein.
- Detailansicht soll Quellen, fachliche Identität, Konfliktgrund, Messwerte und beide Geometrien gemeinsam zeigen.
- Entscheidungen erfolgen aus diesem Prüffall heraus und bleiben versioniert/auditierbar.
- Mail ist nur Hinweis, nicht die eigentliche Diagnoseoberfläche.

## REF-DATA-003 – Bauauftrag aktiv
Status: **IMPLEMENTIERUNG FREIGEGEBEN**
- Reihenfolge: Prefill → Weiche/WVB-Objektidentität → Multi-Hit → Konflikt-Diagnosefenster.
- Danach Synology/Pasing-E2E.
- Trassendatenübersicht erst auf dem bestätigten Unterbau.
- Android bleibt unverändert.

## REF-DATA-003 – Parser-Check-Drift offen
- Abschlussaudit findet veraltete Check-30-Erwartung `tb-survey-parser-2` bei Live-Parser v3.
- Fachcode unverändert; Source-Check wird auf v3 synchronisiert.

## REF-DATA-003 – Prefill/WVB/Multi-Hit/Problemfenster Sourceabschluss
Status: **SOURCE FERTIG / NICHT DEPLOYT / E2E OFFEN**
HEAD: `5604ece1bcedf1c055933cfe11f2f62a084677a3`

Code:
- `survey_parser.py`: Parser v3 mit `object_key_from_name` für WEICHE/WVB;
- `018_survey_object_identity.sql`: object_key-Spalte + Index + konservativer Backfill;
- `survey_catalog.py`: object_key in Identität/Kandidatenvergleich/Strong-Identity, Conflict-Detail-API;
- `admin_survey_import.py`: object_key im Analyse-/Importvertrag;
- `v1_admin_survey_import.html`: stabile Prefills, W/WVB-Objektfeld, Achsen-Multi-Hit, offene Prüffälle + Problemfenster;
- Checks 30/112/113/115 erweitert, Check 116 neu; Workflow auf Migration 18 nachgezogen.

Finaler GitHub-Audit: 25/25 Marker PASS.
GitHub Actions auf aktuellem HEAD: Foundation-Jobs ohne Runner-Steps (`steps=[]`) → kein Testnachweis.

Runtime offen:
- Migration 018;
- Parser/Import auf Synology;
- WVB 10-11/14-22/19-20 als getrennte object_keys;
- Route/Gleis-Prefill im Browser;
- Multi-Hit bei überlagerten Achsen;
- Problemfenster inkl. Geometrievergleich und Resolution;
- Pasing Search/Availability/Geometry danach erneut.

## REF-DATA-003 – Parser v4 vor Deployment erforderlich
Status: **SOURCE-NACHKORREKTUR OFFEN**
- object_key erweitert das Parser-Payload-Schema; v3 darf dafür nicht unverändert weiterverwendet werden.
- Parser-Version wird auf `tb-survey-parser-4` angehoben, damit bereits vorhandene Pasing-v3-Caches nicht fälschlich als aktuell gelten.

## REF-DATA-003 – Existing-index Upgradepfad offen
- v4-Reparse allein reicht nicht: bestehende TRA-Segmente springen aktuell über `ALREADY_INDEXED` aus.
- Reindex wird deshalb um Fill-only-Achsanreicherung für neue object_key-Identität erweitert; Geometrie bleibt unverändert.

## REF-DATA-003 – Parser-v4-/Existing-Index-Upgrade umgesetzt
Status: **SOURCE FERTIG / NICHT RUNTIME-BESTÄTIGT**
Commits:
- `fdba907c...` Parsercache v4 + interner Reparse-Pfad;
- `31c1c6f6...` Reindex stale detection + fill-only Axis-Enrichment.

Schutzregeln:
- Raw-Originale bleiben unverändert.
- Reparse aktualisiert nur den Parserkatalog.
- Vorhandenes ORIGINAL_TRA-Segment bleibt dasselbe Segment.
- `object_key` wird nur ergänzt, wenn die bestehende Achse dort leer ist und kein Achstyp-Widerspruch vorliegt.
- vorhandene Admin-Korrektur bleibt geschützt.

Audit: 23/23 Source-Marker PASS.
Actions: `steps=[]`, kein CI-Nachweis.
Runtime offen: Migration 018, v4-Reparse/Reindex und Pasing-Fach-E2E.

## REF-DATA-003 – Runtimebefund Reindex failures=3
Status: **MIGRATION 18 BESTÄTIGT / PARSER v4 BESTÄTIGT / REINDEX FEHLER OFFEN**
- Synology-Systemstatus: 18/18, pending leer.
- Parser im API-Container: `tb-survey-parser-4`.
- Reindex: 40 Originale, 40 reparsed, 18 ALREADY_INDEXED, 19 INDEXED_ASSET, 3 failures.
- Die drei Exceptions werden im aktuellen Reindex-Ergebnis nur gezählt; Ursache ist noch nicht bekannt.
- Vor Fix: betroffene Originale einzeln identifizieren und Exceptiontext erfassen.

## REF-DATA-003 – Reindexfehler Ursache eindeutig
- WVB 10-11, 14-22 und 19-20 scheitern identisch an `survey_catalog_history_operation_check`.
- `ENRICH` ist im neuen Sourcepfad fachlich vorgesehen, aber in der deployten DB-Constraint noch nicht erlaubt.
- Ziel: neue Migration erweitert erlaubte History-Operationen um `ENRICH`; sonstige Kataloglogik unverändert.

## REF-DATA-003 – Migration 019 Survey-History ENRICH
Status: **SOURCE FERTIG / RUNTIME OFFEN**
- Datei: `server/v1_migrations/019_survey_catalog_history_enrich.sql`.
- Drop/Recreate derselben Constraint mit erlaubten Operationen: CREATE, UPDATE, RESOLVE, STATUS, ENRICH.
- Workflow-Migrationsziel auf 19/19 nachgezogen.
- Check 112 prüft Migration 019 und ENRICH.
- Keine Fachlogik außer DB-Vertrag geändert.

## REF-DATA-003 – Reindex nach ENRICH-Fix runtime-grün 30.09.2026
- Synology/API-Terminal: `reindex_all_originals()` erneut ausgeführt.
- Ergebnis: `original_count=40`, `parser_version=tb-survey-parser-4`, `reparsed=0`.
- Statusverteilung: `ALREADY_INDEXED=19`, `INDEXED_ASSET=19`, `ALREADY_INDEXED_ENRICHED=2`.
- `failures=0`: der zuvor reproduzierte ENRICH-Constraint-Fehler ist im laufenden DB-Pfad behoben.
- Die 40 Originale sind vollständig bilanziert; kein Reindex-Fehler mehr offen.
- Abweichung zur Voraberwartung: nur 2 statt 3 WVB wurden in diesem Lauf als `ALREADY_INDEXED_ENRICHED` gemeldet. Vor weiterer Fachfreigabe read-only feststellen, welcher der drei WVB-Fälle bereits als `ALREADY_INDEXED` läuft und welche `object_key` die drei vorhandenen Achsen tatsächlich tragen.
- Der vorliegende Screenshot zeigt nicht den Systemstatus `19/19`; Migration 019 deshalb in der Dokumentation erst nach separatem Systemstatus-/Migrationstabellen-Nachweis als 19/19 bestätigen.
- Nächster Runtime-Schritt: WVB 10-11 / 14-22 / 19-20 mit Originalname, Achse, `object_key` und Segmentzahl gegenüberstellen; danach Pasing-Prefill, Multi-Hit und „Problem ansehen“ praktisch testen.


## REF-DATA-003 – Migration019 bestätigt, WVB 19-20 Fehlzuordnung reproduziert 30.09.2026
Status: **MIGRATION 19/19 BESTÄTIGT / REINDEX FEHLERFREI / WVB-FACHFEHLER REPRODUZIERT**
- Synology/API-Terminal bestätigt Migrationen: known=19, applied=19, pending=[], latest_known=19, latest_applied=19.
- Reindex bleibt fehlerfrei: 40 Originale vollständig bilanziert, failures=0.
- Read-only-WVB-Abfrage zeigt WVB 10-11 korrekt auf `object_key=WVB:10-11` und WVB 14-22 korrekt auf `object_key=WVB:14-22`.
- Reproduzierter Fehler: `WVB_19-20_ER0_GGP2026_02_16.TRA` ist fälschlich derselben Katalogachse wie WVB 14-22 zugeordnet; die Zeile zeigt `display_name=WVB_14-22_ER0_GGP2026_02_16.TRA`, `object_key=WVB:14-22`, `axis_segment_count=2`.
- Damit erklärt sich die Verteilung 2× ALREADY_INDEXED_ENRICHED + 1× ALREADY_INDEXED: WVB 19-20 besitzt kein getrenntes Achsenobjekt, sondern wurde bereits vor dem Enrichment mit 14-22 zusammengeführt.
- Keine Android-Änderung. Nächster SERVER-Schritt: Merge-/Kandidatenlogik für bestehende WVB-Segmente anhand object_key rückwirkend sicher trennen/reparieren, ohne Raw-Originale zu verändern; danach WVB-Abfrage und Pasing-Prefill/Multi-Hit/Problemfenster erneut runtime-prüfen.

## REF-DATA-003 – Legacy-WVB-Identitätsreparatur Sourcecheckpoint 30.09.2026
Status: **SOURCE KORRIGIERT / NICHT DEPLOYT / E2E OFFEN**
- Reproduzierter Altfehler: WVB 19-20-Segment lag auf WVB 14-22-Achse; keine neue Parser-/Dateiursache.
- `survey_catalog.py` repariert diesen Legacyfall beim Reindex konservativ:
  - nur wenn aktueller Parser-object_key vom Achs-object_key abweicht;
  - nur wenn ein anderes ORIGINAL_TRA auf derselben Achse den bisherigen Achs-object_key bestätigt;
  - kein automatisches Umhängen bei offenem Prüffall;
  - gleiche Segment-ID, gleiche Raw-Datei; nur Katalogzuordnung/Version/History/Audit werden geändert;
  - Zielachse wird weiterhin über die bestehende object_key-kompatible Kandidaten-/Geometrielogik gewählt bzw. neu angelegt.
- Neuer Status `ALREADY_INDEXED_REASSIGNED`; Audit `SURVEY_CATALOG_SEGMENT_IDENTITY_REASSIGN`.
- Checks 112/113 wurden auf Reparaturpfad und Schutzfälle erweitert.
- GitHub-Dateien nach den Commits erneut gelesen: Reparaturpfad + Guard-Assertions vorhanden. Workflow Run 36728982421 hat conclusion=failure, aber Foundation `steps=[]`; **kein CI-Testnachweis**.
- Maßgebliche Code-/Check-Commits: `a7149047790eed6cffe785cc427d81fba74f0b28`, `523450f8c544587e19e083aea0cb89414e2bb0ab`, `fcd2c02423ac42098cdd63465ca4445db5aca627`.
- Keine Migration 020, kein Compose, kein Android-Code.
- Runtime offen: Synology-Deploy dieses Sourcefixes, Reindex mit failures=0 und genau getrennten object_keys/Achsen für WVB 10-11, 14-22, 19-20.

### Source-Check Legacy-WVB-Guard 30.09.2026
- Isolierter Logiktest der aus GitHub gelesenen Guard-Funktion: **4/4 PASS** (Legacy-Mismatch mit bestätigendem Geschwister-Original = reparierbar; ohne Beleg = nein; gleicher object_key = nein; abweichender/manuell wirkender Achs-key = nein).
- Das ist **nur ein Source-/Logikcheck**, kein Test des DB-Umhängens und kein Synology-E2E.

## REF-DATA-003 – Legacy-WVB-Reparatur Runtime BESTÄTIGT 30.09.2026
Status: **RUNTIME BESTÄTIGT**
- Reindex im API-Container: 64 Originale, Parser v4, reparsed=0, 36 ALREADY_INDEXED, 27 INDEXED_ASSET, 1 ALREADY_INDEXED_REASSIGNED, failures=0.
- Der Reparaturfall ist genau einmal angesprungen; das entspricht dem zuvor nachgewiesenen Altfehler WVB 19-20.
- WVB-Zielzustand nach read-only-Abfrage:
  - 10-11 → eigener axis_id, `WVB:10-11`, Segmentzahl 1;
  - 14-22 → eigener axis_id, `WVB:14-22`, Segmentzahl 1;
  - 19-20 → eigener axis_id, `WVB:19-20`, Segmentzahl 1.
- Der alte gemeinsame 14-22/19-20-Achsbestand ist damit fachlich getrennt.
- Keine weitere Serveränderung für diesen Fehler nötig. Nächster Runtimeblock: Pasing-Prefill, Multi-Hit, Konfliktfenster, danach Search/Availability/Geometry.

## REF-DATA-003 – UI-Nacharbeit + erneuter Staging-Neuimport gewünscht 30.09.2026
Status: **ANFORDERUNG / FEHLERFESTSTELLUNG DOKUMENTIERT**
- Begriff `Ziel nach Analyse` ist für den Benutzer nicht verständlich genug; Bezeichnung soll fachlich klarer werden (z. B. `Speicherziel` / `Datenbestand`).
- Bei `Globaler Referenzbestand` soll das deaktivierte Feld `Firma / Baustelle` nicht weiter sichtbar bleiben; es wirkt wie eine weiterhin relevante Auswahl und soll im Global-Fall ausgeblendet werden.
- Admin-Dashboard zeigt pro Firma weiterhin die feste Speicher-Kategorie `Trassierungsdaten` auch bei `0 B`; klären/erläutern, dass dies nur eine Speicher-Kategorie ist und nicht bedeutet, dass globale Referenzdaten der Firma gehören.
- Benutzer möchte den aktuellen Staging-Streckendaten-/Vermessungsbestand erneut vollständig löschen und anschließend dieselben Daten frisch hochladen, um den Gesamtpfad erneut zu testen.
- Löschung ausschließlich über den vorhandenen geschützten Survey-Testdaten-Reset (Preview + Bestätigungstext, nur staging/dev/test); keine manuelle DB-/Dateisystem-Löschung.
- Vor Codeänderung zuerst vorhandene Reset-UI/-Endpunkte und Dashboard-/Import-Template prüfen; anschließend gezielt UI-Bezeichnungen/Sichtbarkeit nacharbeiten.
