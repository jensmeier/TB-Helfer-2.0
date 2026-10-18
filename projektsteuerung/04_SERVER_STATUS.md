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

## REF-DATA-003 – Import-UI-Nacharbeit + Reset-Bedienung SOURCE 30.09.2026
Status: **SOURCE FERTIG / RUNTIME OFFEN**
- Importanalyse: verständliche Bezeichnung `Speicherziel`; GLOBAL = `Gemeinsamer Referenzbestand`.
- `Firma / Baustelle` wird bei GLOBAL nicht mehr grau angezeigt, sondern ausgeblendet; nur bei `Firma / Baustelle` als Speicherziel sichtbar.
- Vorhandener sicherer Survey-Testreset ist nun direkt in der Admin-Trassierungsdaten-Seite bedienbar. Der Backend-Schutz bleibt unverändert: nur dev/test/staging, exakter Bestätigungstext, Admin+CSRF.
- Keine neue Löschlogik und keine neue Migration; UI ruft ausschließlich `/api/v1/admin/purge/survey-test-data` auf.
- Dashboard-Hinweis `Trassierungsdaten 0 B` ist weiterhin nur eine feste Firmen-Speicherkategorie. `0 B` bedeutet: keine firmeneigenen Trassierungsdateien; globale Referenzoriginale werden dort nicht einer Firma zugerechnet.
- Runtimeprüfung nach Template-Deploy: Global-Firma-Feld verborgen, Reset-Preview zeigt aktuellen Bestand, Reset auf staging erfolgreich, danach Neuimport.

## REF-DATA-003 – Speicherziel vereinfacht + Terminal-Reset gewünscht 30.09.2026
Status: **NEUE FACHENTSCHEIDUNG DOKUMENTIERT**
- Benutzerentscheidung: Die Admin-Seite `Trassierungsdaten` dient dem zentralen gemeinsamen Referenzbestand. Dort soll **keine Auswahl GLOBAL vs. Firma/Baustelle** mehr angeboten werden; Admin-Uploads dieser Seite werden immer als `GLOBAL_REFERENCE` importiert.
- Firmen-/Baustellenbeiträge bleiben ein eigener Fachpfad für spätere Benutzer-/Android-Beiträge und werden nicht über diese zentrale Admin-Importseite gewählt.
- Das Feld/Segment `Trassierungsdaten` unter `Speicher nach Firma` soll aus der Dashboard-Firmenaufteilung entfernt werden; globale Referenzdaten gehören keiner Firma.
- Benutzer möchte den aktuellen Survey-/Trassierungs-Testbestand jetzt ausdrücklich **im API-Container-Terminal** vollständig löschen und danach neu hochladen.
- Dafür keinen freien SQL-Löschblock verwenden, sondern den vorhandenen FK-/Storage-sicheren Resetpfad als explizite Terminal-Funktion nutzbar machen; weiterhin nur dev/test/staging und nur mit exaktem Bestätigungstext.

## REF-DATA-003 – Zentralimport/Terminal-Reset Sourceabschluss 30.09.2026
Status: **SOURCE FERTIG / SYNLOGY-RUNTIME OFFEN**
- Importkarte zeigt nur noch fachliche Zuordnungen Bahnhof/Betriebsstelle, Strecke und Gleis/Achse bzw. Objekt/Weiche/WVB. Kein `Speicherziel`, keine `Firma/Baustelle` mehr.
- Der Admin-Import ist fest `GLOBAL_REFERENCE`; damit entfällt die unklare Frage, warum dieselben zentralen Referenzdaten unterschiedlich gespeichert werden sollten.
- Dashboard-Firmenkarte zeigt keine `Trassierungsdaten 0 B`-Kategorie mehr.
- Terminal-Reset nutzt denselben geschützten Kern wie der Admin-API-Reset; keine direkte freie SQL-Löschung nötig.
- Preview-Terminalaufruf und Reset-Terminalaufruf stehen nach Deploy von `admin_purge.py` zur Verfügung.
- Source-Commits: `0dc17765...` Adminimport, `45ed6d90...` Dashboard, `04c6630f...` Terminal-Helper, Checks `a37dcc6f...` / `4bd7fb04...` / `38cb0fa4...`.

## REF-DATA-003 – Terminal-Reset auf Synology BESTÄTIGT 30.09.2026
Status: **DEPLOYT / RESET ERFOLGREICH / NEUIMPORT OFFEN**
- Preview im laufenden API-Container bestätigte `environment=staging` und 64 Survey-Originale.
- Vorher-Zähler: originals=64, parse_catalog=64, assets=64, axes=36, segments=37, conflicts=1, history=325, survey_sections=0, track_source_links=0, import_file_rows=0.
- `reset_survey_test_data_from_terminal('VERMESSUNG TESTDATEN LOESCHEN')` lieferte `status=RESET`, `removed_storage_keys=64`.
- `after` im Reset sowie separate Preview danach: sämtliche Survey-Zähler = 0.
- Der geschützte Terminal-Resetpfad ist damit praktisch bestätigt. Keine weitere Löschaktion nötig.
- Nächster Runtimeblock: frischer Pasing/MPB-Upload über die zentrale Admin-Seite; danach Prefill, W/WVB-Identität, Multi-Hit, Prüffälle und Search/Availability/Geometry prüfen.

## REF-DATA-003 – Zentraler Admin-Import UI RUNTIME BESTÄTIGT 30.09.2026
Status: **UI DEPLOYT / ANALYSE VOR IMPORT GEPRÜFT**
- Nutzer-Screenshots nach Reset/Reload bestätigen: in den Analysegruppen gibt es **kein Speicherziel** und **keine Firma/Baustelle-Auswahl** mehr.
- Zentrale Admin-Seite zeigt nur die fachlichen Felder Bahnhof/Betriebsstelle, Strecke und Gleis/Achse bzw. Objektidentität.
- Prefill ist im frischen Analysezustand sichtbar: z. B. Gl 714 → Strecke 5528 / Gleis 714, Gl 715 → 5528 / 715, Gl 725 → 5528 / 725.
- Tabelle `Zuletzt gespeicherte Originale` ist vor Import leer und zeigt `Noch keine Trassierungsoriginale gespeichert.`; der vorher bestätigte Reset ist damit auch im Portal sichtbar.
- GVP/KF-Gruppe zeigt Strecke 5524 auf Gruppenebene, gleichzeitig Warnungen für 259 Roh-/KF.TXT-Punkte ohne eigenen Strecken-/Stationsbezug. Das ist vorerst als Analysehinweis zu behandeln; noch kein Fehlernachweis für den Import.
- Nächster Schritt: Pasing/MPB-Betriebsstelle setzen und Import starten; danach Importergebnis, Originaltabelle, Katalogachsen/WVB, Multi-Hit und API-Suche prüfen.

## REF-DATA-003 – Frischer Pasing-Import nach Reset BESTÄTIGT 30.09.2026
Status: **RUNTIME IMPORT BESTANDEN**
- Browser-Importergebnis: 40 importiert, 0 Duplikate, 0 übersprungen, 0 Fehler.
- Alle sichtbaren Ergebnisse sind GLOBAL und MPB – München-Pasing Bbf zugeordnet und fachlich ausgewertet.
- WVB 10-11 / 14-22 / 19-20 sind im frischen Import enthalten; aktuell keine offenen Trassendaten-Prüffälle.
- `Zuletzt gespeicherte Originale` war im unmittelbaren Importzustand zunächst noch leer; nach `Liste aktualisieren` zeigt die Tabelle die neuen Datensätze. Das ist aktuell nur ein UI-Aktualisierungsverhalten, kein Datenverlust.
- WVB-Einträge in der Originaltabelle: GLOBAL, MPB, Strecke/Gleis leer bei WVB – passend zum Vertrag, da object_key die Primäridentität ist und route_number fehlen darf.
- Noch nicht als abgeschlossen werten: frischen Katalogzustand read-only prüfen (object_key/axis_id/segment_count), anschließend API Search/Availability/Geometry und Multi-Hit testen.

## REF-DATA-003 – Frischer WVB-Katalog Read-only 30.09.2026
Status: **TEILWEISE BESTÄTIGT / 19-20 NOCH NICHT IM BILD**
- Direkt nach dem sauberen Neuimport, ohne Reindex/Reparatur, zeigt die read-only-Abfrage:
  - `WVB_10-11_ER0_GGP2026_02_16.TRA` → eigene Achse `e91193d2-c26b-4cf5-b367-720774f90bf6`, `object_key=WVB:10-11`, `station_code=MPB`, `axis_segment_count=1`.
  - `WVB_14-22_ER0_GGP2026_02_16.TRA` → eigene Achse `d2a4a7c9-eebc-44d8-8a08-5ad85f67d764`, `object_key=WVB:14-22`, `station_code=MPB`, `axis_segment_count=1`.
- Zugehörige GRA-Dateien haben erwartungsgemäß kein ORIGINAL_TRA-Segment/Achsenobjekt in dieser Abfrage.
- WVB 19-20 ist im übermittelten Screenshot abgeschnitten und daher **noch nicht bestätigt**; keine Schlussfolgerung aus dem nicht sichtbaren Teil.
- Nächster Schritt: gezielte 3-Zeilen-Abfrage nur für WVB-TRA, damit 19-20 sichtbar verifiziert wird; danach Search/Availability/Geometry.

## REF-DATA-003 – WVB-Neuimportfehler GESCHLOSSEN 30.09.2026
Status: **BESTÄTIGT BEHOBEN**
- Frischer Import nach vollständigem Survey-Reset erzeugt WVB 10-11, 14-22 und 19-20 jeweils direkt auf einer eigenen Achse.
- Passende object_keys: `WVB:10-11`, `WVB:14-22`, `WVB:19-20`; alle `station_code=MPB`; jede Achse hat genau 1 Segment.
- 19-20 teilt keine axis_id mehr mit 14-22.
- Kein Reindex-/Legacy-Reassign war nötig. Damit ist sowohl der Upgradefall als auch der saubere Neuimportfall praktisch bestätigt.
- Schutzregel: diesen WVB-Fix nicht ohne neuen reproduzierbaren Fehler verändern.

## REF-DATA-003 – MPB Search/Availability/Geometry Runtime 30.09.2026
Status: **API OK / KM-GATE NOCH OFFEN**
- Search: 20 sichtbare MPB-Achsen, Station MPB mit 40 Assets (GRA/GVP/TRA), WVB-object_keys korrekt enthalten.
- Availability: AVAILABLE, 20 Achsen, 21 aktive Segmente, 0 Review-Segmente; GRA 17 / GVP 2 / TRA 21; ORIGINAL_TRA 21.
- KM-Qualität aus demselben Runtimebestand: `FEHLT=17`, `AUTOMATISCH=4`. Das ist der nächste fachliche Befund und passt zu der noch offenen Anforderung, vorhandene belastbare KM-/GVP-Daten korrekt als grün abzubilden. Keine Änderung auf Verdacht.
- Geometry: 21 Segmente erfolgreich geliefert; Bahnhofsgleise, freie Strecken und WVB 10-11/14-22/19-20 vorhanden.
- Die Differenz 20 Achsen / 21 Segmente kommt sichtbar von mindestens einer Mehrsegment-Achse (`Str 5524-1` erscheint mit identischer axis_id zweimal). Vor Bewertung erst Quellsegmente/KM-Kontext read-only auflösen.

## REF-DATA-003 – Pasing-KM-Einordnung 30.09.2026
Status: **FACHLICH GEKLÄRT / KEIN SERVERFEHLER**
- Nutzer bestätigt: Für Pasing gibt es keine belastbare vollständige Kilometrierung.
- Daher sind die aktuell gemeldeten `km_quality=FEHLT`-Segmente bei Pasing erwartbar und dürfen nicht als Parser-/Import-/API-Fehler behandelt werden.
- Zielablauf: Android markiert auf vorhandener TRA Start/Ende und setzt die KM-Anker manuell; daraus entsteht ein versionierter manueller Abschnitt (ORANGE).
- SERVER-seitig keine künstliche automatische KM-Erzeugung auf Verdacht.
- Nächster Pasing-Test konzentriert sich deshalb auf Geometrie/Multi-Hit und darauf, ob manuelle Abschnitte später korrekt synchronisiert/gespeichert werden.

## REF-DATA-004 – Gessertshausen ohne Betriebsstelle 30.09.2026
Status: **FEHLBEDIENUNG REPRODUZIERT / KORREKTUR OFFEN**
- Gessertshausen wurde bereits importiert, ohne vorher Bahnhof/Betriebsstelle auszuwählen.
- In der Originaltabelle stehen die neuen GLOBAL-Daten ohne Bahnhof/Betriebsstelle; Strecke 5302 ist bei KOR/GVP vorhanden, mehrere TRA ebenfalls ohne Stationszuordnung.
- Pasing/MPB darf bei der Korrektur nicht verändert werden.
- Vor Änderung prüfen, ob eine gezielte nachträgliche Stationszuordnung für diesen Importbestand vorhanden ist. Falls nicht, nur den Gessertshausen-Bestand gezielt bereinigen und neu importieren; kein globaler Reset.

## REF-DATA-004 – Gessertshausen Betriebsstelle in Analyse bestätigt 30.09.2026
Status: **ZUORDNUNG BESTÄTIGT / BESTEHENDER IMPORT NOCH UNKORRIGIERT**
- Nutzer-Screenshot bestätigt die richtige Referenzzuordnung in der Analyse: `MGHN – Gessertshausen`.
- Sichtbare Gruppen: Strecke 5302, Gleis/Achse 302 bzw. 303; Betriebsstelle jeweils korrekt als MGHN zugeordnet.
- Noch **nicht erneut importieren**, weil der bereits gespeicherte Bestand ohne Betriebsstelle über Hash/Größe als Duplikat erkannt würde und dadurch nicht automatisch nachkorrigiert wird.
- Nächster Schritt vor jeder Änderung: read-only feststellen, wie viele aktuell gespeicherte Survey-Originale noch keine station_code-Metadaten haben und welche Dateinamen betroffen sind. Pasing/MPB bleibt geschützt.

## REF-DATA-005 – Vollreset + sichere Korrektur bei vergessenem Importfeld 30.09.2026
Status: **NEUE FACHENTSCHEIDUNG / UMSETZUNG OFFEN**
- Benutzer möchte den aktuellen Survey-/Trassierungs-Testbestand erneut vollständig löschen und anschließend sauber neu importieren.
- Zusätzlich soll der Admin-Import künftig vergessene/falsch gesetzte fachliche Zuordnungen (z. B. Bahnhof/Betriebsstelle) ohne globalen Reset korrigieren können.
- Gewünschtes Bedienprinzip: Wenn dieselbe Originaldatei bereits vorhanden ist, darf nicht nur stumpf `DUPLIKAT` erscheinen. Bei abweichender neuer Zuordnung soll eine klare Warnung erscheinen: **Daten bereits vorhanden – vorhandene Zuordnung aktualisieren/überschreiben?**
- Rohdatei bleibt unverändert; überschrieben werden nur fachliche Zuordnung/Metadaten und daraus abgeleitete Katalogzuordnung. Vorgang muss bewusst bestätigt und auditierbar sein.
- Identischer Reimport mit identischer Zuordnung bleibt ein normales Duplikat und erzeugt keine Änderung.
- Pasing-/WVB-Schutzregeln bleiben fachlich gültig; der jetzt gewünschte Vollreset ist eine ausdrückliche Testdatenentscheidung des Benutzers.

## REF-DATA-005 – Vorhandene Daten korrigieren statt Reset SOURCE 30.09.2026
Status: **SOURCE FERTIG / SYNLOGY-RUNTIME OFFEN**
- Backend `admin_survey_import.py`: `overwrite_existing` ergänzt; abweichende Zuordnung wird erst gewarnt und nur nach ausdrücklicher Bestätigung geändert.
- Backend `survey_catalog.py`: `refresh_original_assignment_in_tx()` aktualisiert abgeleitete Asset-/Achsenidentität ohne Raw-Datei oder Segment-ID neu anzulegen.
- Portal `v1_admin_survey_import.html`: Warnbox/Checkbox + Vorher→Nachher-Darstellung + zweiter Importlauf aus derselben Analyse-Session.
- Schutz: bei OPEN-Conflict, mehrfachen Source-Segmenten, Axis-Identity-Kollision oder destruktiver Änderung einer geteilten Achse wird 409 geliefert statt automatisch umgebaut.
- Sourcechecks 112/115 auf neue Marker erweitert; GitHub Actions liefern weiterhin keinen brauchbaren Runner-Nachweis, daher Runtime-Test auf Synology Pflicht.
- Für den aktuellen Testbestand bleibt der ausdrücklich gewünschte komplette staging-Reset erlaubt; danach Pasing + Gessertshausen frisch importieren und den neuen Korrekturfall absichtlich testen.

## REF-DATA-005 – Überschreib-Warnung optisch stärker hervorheben 02.10.2026
Status: **NEUE UI-ANFORDERUNG DOKUMENTIERT**
- Nutzer bestätigt den neuen Korrekturworkflow, findet die Checkbox `Vorhandene identische Dateien bei abweichender Zuordnung aktualisieren` aber optisch zu unauffällig.
- Die Bestätigungsbox soll deutlich als Warn-/Gefahrenbereich erkennbar sein, bevorzugt mit **rotem Rahmen** und klarer Warnwirkung, damit sie beim Scrollen nicht übersehen wird.
- Funktionalität und Sicherheitslogik bleiben unverändert: standardmäßig aus, zusätzliche Browserbestätigung vor der Aktualisierung.

## REF-DATA-005 – Überschreib-Warnbox optisch hervorgehoben 02.10.2026
Status: **SOURCE FERTIG / RUNTIME OFFEN**
- Checkbox-Bereich `Vorhandene identische Dateien bei abweichender Zuordnung aktualisieren` erhält jetzt einen deutlich roten Warnrahmen, hellroten Hintergrund und rote Checkbox-Akzentfarbe.
- Funktion und Schutzlogik unverändert: standardmäßig aus, zusätzlicher Browser-Bestätigungsdialog bleibt Pflicht.
- Datei: `server/templates/v1_admin_survey_import.html`; Sourcecheck 115 um Warnbox-Styling ergänzt.

## REF-DATA-005 – Duplicate-Korrektur Runtime teilweise BESTÄTIGT 02.10.2026
Status: **WARNUNG + BESTÄTIGTE AKTUALISIERUNG PRAKTISCH NACHGEWIESEN**
- Vorheriger Browserlauf zeigte bei identischen vorhandenen Dateien mit nachgetragener Betriebsstelle korrekt `VORHANDEN_ABWEICHEND` und Vorher→Nachher (`— → MGHN`, `— → Gessertshausen`).
- Nach Setzen des Bestätigungshakens und erneutem Import zeigt der Runtime-Screenshot mehrere Dateien mit `AKTUALISIERT`, u. a. `250919_5302R_ER0.TRA`, `250919_5302R_O00_kmLinie.GRA`, `250919_Gl1_MGHN_ER0.TRA`, `250919_Gl1_MGHN_O00.GRA`.
- Ergebnistext bestätigt: vorhandenes Original wurde beibehalten, fachliche Zuordnung aktualisiert, MGHN – Gessertshausen.
- Gleichzeitig blieb mindestens `5340S_ER0.TRA` als `DUPLIKAT`; damit ist auch der unveränderte-Duplikatpfad im selben Lauf sichtbar.
- Noch offen für vollständigen Gate-Abschluss: Original-ID/SHA vor/nachher unverändert nachweisen, Originaltabelle + Search/Availability/Geometry auf MGHN prüfen und Audit/History read-only verifizieren.

## REF-DATA-005 – Audit/History-Nachweis Runtime 02.10.2026
Status: **AUDIT BESTÄTIGT / ORIGINAL-EINZIGARTIGKEIT NOCH OFFEN**
- Nutzer-Screenshot aus dem API-Terminal bestätigt `ADMIN_SURVEY_ORIGINAL_ASSIGNMENT_UPDATE` mit `source=ADMIN_WEB_DUPLICATE_CORRECTION` und Änderungen `station_code: None → MGHN`, `station_name: None → Gessertshausen`.
- Zusätzlich sind `SURVEY_CATALOG_ASSIGNMENT_UPDATE`-Einträge vorhanden; damit ist die abgeleitete Katalogaktualisierung auditierbar nachgewiesen.
- Noch nicht aus dem sichtbaren Screenshot bestätigt: `same_raw_count=1`, Original-ID/SHA unverändert bzw. keine zweite Originalzeile. Dieser Teil bleibt offen, bis die kompakte Originalabfrage sichtbar vorliegt.

## REF-DATA-005 – Original-ID/SHA/Eindeutigkeit Runtime BESTÄTIGT 02.10.2026
Status: **IN-PLACE-KORREKTUR BESTÄTIGT**
- Kompakte API-Terminalprüfung nach dem bestätigten Korrekturlauf: `updates=16`, `ids_exist=true`, `sha_same=true`, `raw_unique=true`, `station_mghn=true`.
- Damit ist praktisch bestätigt: die aktualisierten Original-IDs existieren weiter, der SHA256 blieb unverändert, es wurde pro Rohdatei keine zweite Originalzeile erzeugt und die korrigierten Originale tragen MGHN.
- Zweiter Runtime-Screenshot zeigt für MGHN Geometry `count=12`; enthalten sind u. a. Gleis 1, Gleis 302/303, Gleis 4 sowie W/WVB-Objekte. Availability-Ausgabe zeigt `review_segment_count=0` und KM-Qualität `FEHLT=10`, `AUTOMATISCH=2`.
- Search-/Availability-Kopfzeilen sind im Screenshot teilweise abgeschnitten; deshalb vollständigen MGHN-API-Gate noch mit einer kompakten Kurzabfrage abschließen, statt Werte zu raten.

## REF-DATA-005 – MGHN Search/Availability Runtime BESTÄTIGT 02.10.2026
Status: **FACHLICHER KORREKTURWORKFLOW BESTÄTIGT**
- Kompakte Runtimeabfrage nach der In-place-Korrektur:
  - Search MGHN: `axes=12`; Station `MGHN – Gessertshausen`; `asset_count=16`; Datenarten `GRA` und `TRA`.
  - Availability MGHN: `status=AVAILABLE`, `axis_count=12`, `active_segment_count=12`, `review_segment_count=0`.
  - KM-Qualität: `FEHLT=10`, `AUTOMATISCH=2`.
- Zusammen mit dem bereits bestätigten Geometry-`count=12`, Audit und In-place-Nachweis ist der fachliche Duplicate-Korrekturworkflow funktional geschlossen.
- Noch rein visuell offen: roten Warnrahmen der Bestätigungsbox nach letztem Template-Deploy einmal im Browser bestätigen. Das ist nur UI-Sichtbarkeit, kein Daten-/API-Restfehler.

## REF-DATA-006 – Deutschlandweite Trassendaten-Gesamtansicht mit Suche 02.10.2026
Status: **NEUE FACHANFORDERUNG DOKUMENTIERT / ANALYSE VOR UMSETZUNG**
- Nach Abschluss der Gessertshausen-KM-Prüfung soll im Admin eine **grafische Gesamtansicht des vorhandenen Referenzbestands für Deutschland** entstehen.
- Ziel: auf einen Blick sehen, für welche Bereiche/Bahnhöfe/Strecken bereits Trassierungs-/Vermessungsdaten vorhanden sind.
- Die Ansicht braucht mindestens eine **Suche** nach Strecke, Bahnhof/Betriebsstelle und vorhandenen Objekt-/Achsnamen; Auswahl soll auf den Treffer fokussieren und in die Detailansicht führen können.
- Darstellung soll vorhandene Datenbestände und deren Qualität unterscheiden (automatisch belastbare KM = grün, manuell ergänzt = orange, Geometrie ohne belastbare KM = rot, Objekt ohne Geometrie = grau; bestehende Projektfarbregeln bleiben verbindlich).
- Vor Implementierung prüfen, welche belastbaren geographischen Koordinaten/Referenzpunkte im aktuellen Serverbestand für eine echte Deutschlandkarte vorhanden sind. Keine scheinpräzise Deutschlandkarte aus lokalen/unklaren Vermessungskoordinaten ableiten.
- Bestehende Import-/Katalog-Detailgrafik bleibt erhalten; die Gesamtansicht ist eine zusätzliche Admin-Übersicht.

## REF-DATA-006 – MGHN KM-Qualität sichtbarer Teil 02.10.2026
Status: **TEILNACHWEIS / AUTOMATISCH-2 NOCH NICHT IM BILD**
- Nutzer-Screenshot der achsweisen Geometry/KM-Ausgabe zeigt für die sichtbaren W/WVB-Achsen in Gessertshausen durchgehend `status=FEHLT`, `color=ROT`, Quelle leer und Begründung `Keine belastbare automatische oder manuelle KM-Quelle für dieses Segment.`.
- Das passt fachlich für W/WVB ohne eigene belastbare KM-Quelle und ist kein Fehler.
- Die zuvor von Availability bestätigten zwei `AUTOMATISCH`-Segmente sind im Screenshot nicht sichtbar; daher ihre Identität noch nicht fest behaupten.
- Nächster Schritt: Ausgabe auf `km_quality.status=AUTOMATISCH` filtern und anschließend Koordinaten-/BBox-Werte MPB vs. MGHN read-only prüfen, bevor die Deutschland-Gesamtansicht kartografisch umgesetzt wird.

## REF-DATA-006 – MGHN KM + Koordinatenbasis bestätigt 02.10.2026
Status: **VORBEDINGUNGEN FÜR GESAMTANSICHT BESTÄTIGT**
- Gefilterter Runtime-Nachweis zeigt exakt die zwei automatischen Gessertshausen-Segmente:
  - `Gl 302 · Gessertshausen`, Strecke 5302, Gleis 302 → `AUTOMATISCH / GRUEN / GRA_KM`, ca. km 14.800–19.057.
  - `Gl 303 · Gessertshausen`, Strecke 5302, Gleis 303 → `AUTOMATISCH / GRUEN / GRA_KM`, ca. km 14.641–19.145.
- Runtime-BBox: MGHN ca. X 4,403,653–4,409,717 / Y 5,355,846–5,358,574 (12 Achsen); MPB ca. X 4,457,812–4,458,912 / Y 5,335,231–5,336,086 (13 Achsen).
- Parservertrag kennzeichnet TRA/GRA als `DB_REF2016_4`. Die X-Werte liegen konsistent im 4-Millionen-Meridianstreifen.
- Öffentliche DB/EPSG-Referenz bestätigt horizontal DB_REF / 3-degree Gauß-Krüger Zone 4 = EPSG:5684; DB_REF2016 compound Zone 4 = EPSG:9934. Damit kann für die Deutschlandübersicht serverseitig belastbar nach WGS84 transformiert werden.
- Umsetzung darf jetzt beginnen; für künftige deutschlandweite Daten müssen DB_REF-Zonen 2–5 unterstützt werden, nicht nur Zone 4.

## REF-DATA-006 – Gesamtansicht Deutschland SOURCE FERTIG 02.10.2026
Status: **SOURCE FERTIG / SYNLOGY-RUNTIME OFFEN**
- `server/tbserver/survey_catalog.py`: `/coverage` + DB_REF→WGS84-Transformation für Zonen 2–5.
- `server/templates/v1_admin_survey_overview.html`: eigenständige Deutschland-Gesamtgrafik mit Suche/Fokus/Qualitätsfarben ohne externen Tile-Dienst.
- `server/tbserver/admin_web.py`: Admin-Seitenroute ergänzt.
- `server/templates/v1_base.html`: Navigation `Trassendatenübersicht` direkt neben Trassierungsdaten.
- `server/templates/v1_admin_dashboard.html`: Schnellzugriff ergänzt.
- `server/requirements.txt`: `pyproj==3.7.2`.
- Sourcecheck 117 ergänzt und die erwarteten Source-Marker gegen GitHub geprüft. Das ist kein Runtime-/Synology-E2E.
- Keine DB-Migration erforderlich.

## REF-DATA-006 – Synology-Startfehler pyproj 02.10.2026
Status: **DEPLOYMENTFEHLER REPRODUZIERT / URSACHE EINGEGRENZT**
- Synology Container Manager/API-Protokoll zeigt beim Start von `tb-helfer-v1-api-1`: `ModuleNotFoundError: No module named 'pyproj'` beim Import von `tbserver.survey_catalog`.
- GitHub-Quellstand enthält `pyproj==3.7.2` in `server/requirements.txt`; `Dockerfile.v1` kopiert `requirements.txt` und führt `pip install --no-cache-dir -r requirements.txt` beim **Image-Build** aus.
- Der Fehler beweist damit: der laufende/gestartete Container verwendet noch ein Image ohne die neue Abhängigkeit. Ein bloßer Container-Neustart kann `pyproj` nicht nachinstallieren.
- Keine Code-/DB-Ursache und keine Migration erforderlich. Nächster Schritt: auf der Synology zuerst prüfen, dass die neue `server/requirements.txt` wirklich liegt, dann das Projekt/Image über Container Manager **neu bauen/erstellen**, nicht nur neu starten.

## REF-DATA-006 – Synology Projekt „Neu erstellen“ verwendet weiterhin altes Image 02.10.2026
Status: **RUNTIMEFEHLER BESTÄTIGT / NÄCHSTER SCHRITT IMAGE ERZWUNGEN NEU BAUEN**
- Nutzer hat `server/requirements.txt` auf der Synology geprüft; `pyproj==3.7.2` ist dort vorhanden.
- Projekt `tb-helfer-v1` wurde gestoppt und über Container Manager erneut **Erstellen** ausgeführt; Compose-Lauf endet mit Exit Code 0 und startet migrate/worker/api.
- API beendet sich danach erneut mit `ModuleNotFoundError: No module named 'pyproj'`.
- Damit ist ausgeschlossen, dass nur die requirements-Datei auf der NAS fehlt. Der Projekt-Neuerstellen-Vorgang hat das vorhandene Image `tb-helfer-server-v1:local` wiederverwendet statt die Dependency-Schicht neu zu bauen.
- Kein Code-/DB-Umbau. Nächster Synology-Schritt: Projekt stoppen, vorhandenes lokales Server-Image im Container Manager entfernen und anschließend Projekt erneut Erstellen, damit Dockerfile.v1 + requirements.txt zwingend neu gebaut werden.

## REF-DATA-006 – Lokales Image noch von gestoppten Containern referenziert 02.10.2026
Status: **DEPLOYMENTBLOCKER WEITER EINGEGRENZT**
- Container Manager verhindert das Löschen von `tb-helfer-server-v1:local`, weil es noch von `tb-helfer-v1-worker-1`, `tb-helfer-v1-api-1`, `tb-helfer-v1-migrate-1` und `tb-helfer-v1-monitor-1` referenziert wird.
- Das Python-Basisimage `python:3.12-slim` ist bereits vorhanden; Python muss auf der Synology **nicht separat installiert** werden.
- Nächster Schritt: die alten Projektcontainer entfernen/bereinigen, ohne DB-Volume/Projektdateien zu löschen; danach lokales Server-Image löschen und Projekt neu erstellen, damit Dockerfile.v1 die neue requirements-Datei wirklich installiert.

## REF-DATA-006 – Exportiertes API-Protokoll bestätigt pyproj-Fehlerloop 02.10.2026
Status: **RUNTIME WEITER BLOCKIERT / KEINE NEUE CODEURSACHE**
- Exportiertes Container-Protokoll `tb-helfer-v1-api-1(3).html` zeigt zwischen 12:29:59 und 12:30:45 wiederholt denselben Startabbruch: `ModuleNotFoundError: No module named 'pyproj'` beim Import von `tbserver.survey_catalog`.
- Fehler tritt in wiederholten Neustartversuchen auf; die API erreicht keinen betriebsbereiten Zustand.
- Befund passt zum bereits eingegrenzten alten lokalen Image. Nächster notwendiger Schritt bleibt: referenzierende Servercontainer löschen, dann `tb-helfer-server-v1:local` entfernen und Projekt neu erstellen, damit das Image aus aktueller `requirements.txt` neu gebaut wird.

## REF-DATA-006 – Nach Image-/Container-Bereinigung fehlen App-Services im Synology-Projekt 02.10.2026
Status: **DEPLOYMENTBLOCKER / URSACHE NOCH ZU PRÜFEN**
- Nach dem Löschen der alten Servercontainer und des lokalen Server-Images zeigt das Synology-Projekt `tb-helfer-v1` nach `Erstellen` nur noch `cloudflared`, `db` und `restore-test`.
- `api`, `worker`, `migrate` und `monitor` sind in der Projektansicht nicht vorhanden.
- GitHub-Compose `server/docker-compose.v1.yml` definiert dagegen `db`, `migrate`, `api`, `worker`, `monitor`; `cloudflared` und `restore-test` kommen dort nicht vor.
- Damit ist jetzt zuerst die tatsächlich auf der Synology verwendete Projekt-YAML zu prüfen. Keine weiteren Container/Image-/DB-Löschungen, bis die YAML-Konfiguration verglichen ist.
- DB-Container ist weiterhin vorhanden; kein Hinweis auf gelöschte Datenbankdaten aus dem Screenshot.

## REF-DATA-006 – Aktive Synology-Projekt-YAML geprüft 02.10.2026
Status: **DEPLOYMENTBLOCKER WEITER EINGEGRENZT / GITHUB↔SYNOLOGY-COMPOSE ABWEICHUNG FESTGESTELLT**
- Nutzer hat die aktive YAML des Synology-Projekts `tb-helfer-v1` vollständig geliefert.
- Diese Synology-YAML enthält zusätzlich zu db/migrate/api/worker/monitor auch `restore-test-db` und `cloudflared`, Live-Source-Mounts (`TB_V1_SOURCE_TBSERVER_DIR`, Templates/Migrations/Tools/Data), API-`mem_limit` und Restore-Test-DB-Variablen.
- Diese Inhalte sind im aktuellen GitHub-Stand `server/docker-compose.v1.yml` nicht enthalten; GitHub-Suche nach `restore-test-db`, `TB_V1_SOURCE_TBSERVER_DIR`, `TB_V1_CLOUDFLARE_TUNNEL_TOKEN` und `Synology-Staging` liefert keine Treffer.
- Damit besteht eine reale Konfigurationsabweichung zwischen GitHub-Compose und der tatsächlich laufenden Synology-Projektdefinition. GitHub bleibt Source of Truth, die Synology-YAML darf aber nicht auf Verdacht überschrieben werden, weil sie produktions-/stagingrelevante Zusatzdienste und Mounts enthält.
- Der aktuelle Zustand (nur cloudflared/db/restore-test-db sichtbar) spricht dafür, dass der Build/Erstellvorgang der gemeinsamen Server-Services vor deren Containeranlage abgebrochen ist. Nächster Schritt ist ausschließlich das Projekt-/Build-Protokoll des letzten Erstellvorgangs auszulesen; keine weitere Container-, Image-, YAML- oder DB-Änderung bis zur konkreten Build-Ursache.

## REF-DATA-006 – Synology-Erstellen erreicht Pull/Warning-Phase 02.10.2026
Status: **BUILDVORGANG LÄUFT / NOCH KEIN FEHLERNACHWEIS**
- Neuer Screenshot beim `Erstellen` zeigt für `worker`, `monitor`, `migrate`, `api` zunächst `Pulling` und danach jeweils `Warning`.
- Das ist nach bewusst entferntem lokalen Image erwartbar: Compose versucht zuerst das benannte Image `tb-helfer-server-v1:local` zu ziehen; für ein rein lokales Image kann dieser Pull scheitern/warnen. Entscheidend ist, ob anschließend der lokale Dockerfile-Build startet.
- Noch **keine** belastbare Fehlermeldung im Screenshot. Projektanzeige zeigt laufenden Zustand; daher Vorgang nicht abbrechen und auf weitere Build-Zeilen/Abschluss warten.
- Keine weitere Löschung/YAML-/DB-Änderung vor finalem Build-Output.

## REF-DATA-006 – Altes Server-Image als Rollback gesichert 02.10.2026
Status: **ROLLBACK-MÖGLICHKEIT VORHANDEN**
- Nutzer hat das alte funktionierende Server-Image vor dem pyproj-Umbau separat gesichert.
- Wichtig: Die aktive Synology-Compose bindet `tbserver`/Templates/Migrationen live vom NAS read-only in den Container ein. Das alte Image allein reicht daher nicht als vollständiger Rollback, solange auf dem NAS der neue Source mit `from pyproj import Transformer` liegt.
- Für einen sicheren Rücksprung müssen Image **und** der dazu passende Source-Stand vor REF-DATA-006 zusammen verwendet werden; danach Runtime zuerst wieder stabilisieren, bevor die Deutschlandübersicht erneut angegangen wird.
- Gesichertes Alt-Image nicht überschreiben oder löschen.

## REF-DATA-006 – API-Crash nach Alt-Image-Rückweg erneut reproduziert 02.10.2026
Status: **RUNTIME DEFEKT / URSACHE BESTÄTIGT**
- Exportiertes API-Protokoll vom 02.10.2026 zeigt weiterhin wiederholt `ModuleNotFoundError: No module named 'pyproj'`.
- Der Trace endet in `/app/tbserver/survey_catalog.py`, Zeile 14, bei `from pyproj import Transformer`.
- Damit ist bestätigt: das gesicherte alte Image wird von der aktuell live vom NAS gemounteten neuen Source-Datei `survey_catalog.py` überlagert. Das alte Image allein kann den Server nicht stabilisieren.
- Sofortmaßnahme: REF-DATA-006-Source auf den letzten Stand vor pyproj/Deutschlandübersicht zurücksetzen; keine DB-/Migration-/Datenänderung. Danach Alt-Image + alter Source gemeinsam starten und Runtime zuerst stabilisieren.

## REF-DATA-006 – Source-Rollback auf pre-pyproj abgeschlossen 02.10.2026
Status: **QUELLSTAND STABILISIERT / SYNLOGY-DEPLOYMENT OFFEN**
- Zur Wiederherstellung der Server-Runtime wurde ausschließlich der REF-DATA-006-Code auf den letzten Stand vor der pyproj/Deutschlandübersicht zurückgesetzt.
- Wiederhergestellt aus Commit `aef0624f848ec3b5a6fca5cbc747b5004597fcd8`: `server/requirements.txt`, `server/tbserver/survey_catalog.py`, `server/tbserver/admin_web.py`, `server/templates/v1_base.html`, `server/templates/v1_admin_dashboard.html`.
- Neu eingeführte Dateien `server/templates/v1_admin_survey_overview.html` und `server/checks/117_verify_survey_coverage_overview_source.py` wurden entfernt.
- Damit enthält der aktive GitHub-Source keinen `pyproj`-Import mehr. REF-DATA-005 und vorherige Survey-Funktionen bleiben erhalten, weil der Rücksprung genau auf den bereits bestätigten Stand unmittelbar vor REF-DATA-006 erfolgt.
- Nächster Schritt auf Synology: diese fünf Runtime-Dateien bzw. vollständigen Server-Source aus GitHub übernehmen und das gesicherte alte Image verwenden; danach API/Worker/Migrate/Monitor starten und Health/Migrationen prüfen.
- Deutschlandübersicht ist bewusst zurückgestellt und wird erst nach stabiler Runtime mit einer deployment-sicheren Lösung neu gebaut.

## REF-DATA-006 – Runtime nach Rollback wieder gestartet / Migrationen 19/19 02.10.2026
Status: **RUNTIME TEILWEISE WIEDERHERGESTELLT**
- Nutzer-Screenshot aus dem API-Terminal zeigt erfolgreich ausgeführten Migrationsstatus nach Rückkehr auf pre-pyproj Source + altem Image.
- Ergebnis: `known=19`, `applied=19`, `pending=[]`, `latest_known=19`, `latest_applied=19`.
- Damit ist bestätigt: API-Container läuft wieder soweit, dass Python/tbserver importierbar ist und DB/Migrationen konsistent 19/19 sind.
- Noch offen vor vollständiger Stabilitätsfreigabe: `/health/ready`, Worker/Monitor-Laufstatus und kurzer Admin/Portal-Aufruf.

## REF-DATA-006 – Runtime nach Rollback wieder gestartet / Migrationen 19/19 02.10.2026
Status: **RUNTIME TEILWEISE WIEDERHERGESTELLT**
- API-Terminal ist nach Rückkehr auf pre-pyproj Source + altem Image wieder erreichbar.
- Migrationsstatus bestätigt: known=19, applied=19, pending=[], latest_known=19, latest_applied=19.
- Damit sind Quellstand und DB-Migrationen wieder konsistent.
- Offen vor vollständiger Stabilitätsfreigabe: /health/ready, Worker/Monitor-Laufstatus und kurzer Admin/Portal-Aufruf.

## REF-DATA-006 – Server-Runtime nach Rollback BESTÄTIGT 02.10.2026
Status: **RUNTIME WIEDER STABIL / DEUTSCHLANDÜBERSICHT ZURÜCKGESTELLT**
- `/health/ready` liefert wieder `status=ok`, `database=up`, `migrations=ok`, `server_version=1.0.0-dev`.
- Zuvor bereits bestätigt: Migrationen 19/19, pending=[] und API-Terminal erreichbar.
- Nutzer bestätigt zusätzlich, dass die übrigen Serverdienste/Portal-Funktionen wieder laufen.
- Damit ist die Rollback-Recovery praktisch bestätigt. Kein vollständiger REF-DATA-006-E2E, da die Deutschlandübersicht bewusst aus dem aktiven Source entfernt wurde.

## REF-DATA-006 – Server-Runtime nach Rollback BESTÄTIGT 02.10.2026
Status: **RUNTIME WIEDER STABIL / DEUTSCHLANDÜBERSICHT ZURÜCKGESTELLT**
- /health/ready liefert wieder status=ok, database=up, migrations=ok, server_version=1.0.0-dev.
- Zuvor bereits bestätigt: Migrationen 19/19, pending=[] und API-Terminal erreichbar.
- Nutzer bestätigt zusätzlich, dass die übrigen Serverdienste/Portal-Funktionen wieder laufen.
- Damit ist die Rollback-Recovery praktisch bestätigt. Kein vollständiger REF-DATA-006-E2E, da die Deutschlandübersicht bewusst aus dem aktiven Source entfernt wurde.

## REF-DATA-006 – Designphase nach Rollback 02.10.2026
Status: **ARCHITEKTURVORSCHLÄGE / NOCH NICHT BAUEN**
- Server-Runtime ist wieder stabil und geschützt.
- Vor Neuimplementierung der Deutschlandübersicht werden Alternativen verglichen; bevorzugt wird eine Lösung ohne neue Python-/Image-Abhängigkeit.
- Bestehendes PostGIS 17-3.5 ist als möglicher Transformationspfad zu prüfen, da es bereits Teil der laufenden DB-Runtime ist.

## REF-DATA-006 – Variante A bestätigt 02.10.2026
Status: **POSTGIS-ARCHITEKTUR GEWÄHLT / RUNTIME-PROBE VOR BAU**
- Kein pyproj-Neuversuch.
- Bereits vorhandenes PostGIS soll die CRS-Transformation serverseitig/read-only übernehmen; Python bleibt bei den bestehenden Abhängigkeiten.
- Nächster Gate: SRID-/ST_Transform-Probe auf der laufenden Synology. Bei Erfolg folgt Implementierung der Coverage-API und Admin-Gesamtansicht.

## REF-DATA-006 – PostGIS-Transformation Runtime BESTÄTIGT 02.10.2026
Status: **BAU FREIGEGEBEN**
- PostGIS 3.5.2 aktiv; SRIDs 4326/5682/5683/5684/5685 vorhanden.
- ST_Transform liefert für MGHN ca. lon=10.699081, lat=48.333140 und für MPB ca. lon=11.431539, lat=48.153690.
- Keine neue Dependency, kein requirements-/Image-Umbau nötig.
- Nächster Schritt: Coverage-API + Admin-Seite mit Deutschlandübersicht, Suche und Detail-Nachladen implementieren.

## REF-DATA-006 – PostGIS-Gesamtansicht SOURCE FERTIG 02.10.2026
Status: **SOURCE FERTIG / DEPLOYMENT + RUNTIME OFFEN**
- server/tbserver/survey_catalog.py: neue read-only Endpunkte GET /api/v1/survey-catalog/coverage und GET /api/v1/survey-catalog/coverage/{axis_id}/geometry.
- DB_REF-Zonen 2–5 werden über vorhandenes PostGIS (EPSG 5682–5685) nach EPSG:4326 transformiert; keine neue Python-Abhängigkeit.
- server/tbserver/admin_web.py: Admin-Seite /admin/v1/survey-overview.
- server/templates/v1_admin_survey_overview.html: Deutschlandansicht mit Suche, Betriebsstellenmarkern, Achstreffern, Qualitätsfarben, Mausrad-Zoom, Verschieben und Detail-Nachladen.
- v1_base.html + v1_admin_dashboard.html: Navigation/Schnellzugriff ergänzt.
- Sourcecheck 117 neu aufgebaut; aktueller GitHub-Quellstand statisch geprüft: kein pyproj in requirements/imports, PostGIS-Endpunkte/Route/UI/Navigation vorhanden.
- Kein DB-Schema-/Migrationswechsel und kein Image-Neubau nötig.

## REF-DATA-006 – Runtime-Sichtprüfung: Gesamtansicht zeigt zu wenig 02.10.2026
Status: **FEHLER/ANFORDERUNG BESTÄTIGT – KORREKTUR SOURCE NÖTIG**
- Screenshot der neuen Deutschlandübersicht zeigt im Gesamtzoom nur wenige Punkte (u. a. MPB), obwohl deutlich mehr Trassen/Achsen im Bestand vorhanden sind.
- Ursache im aktuellen Source: Coverage liefert pro Achse nur WGS84-BBox/Center; UI rendert in der Übersicht überwiegend Center-/Stationsmarker. Die tatsächlichen Achslinien werden erst nach Einzel-Auswahl geladen.
- Gewünschtes Verhalten: alle aktiven Trassen in der Deutschlandansicht als vereinfachte Linien zeigen; Details weiterhin lazy-load.

## REF-DATA-006 – Gesamtansicht rendert jetzt alle aktiven Achsen SOURCE 02.10.2026
Status: **KORREKTUR SOURCE FERTIG / RUNTIME OFFEN**
- Nach Benutzer-Sichtprüfung wurde die Punktdarstellung korrigiert: Coverage liefert nun pro aktivem Segment eine stark vereinfachte WGS84-Linie (`overview_segments`) zusätzlich zur BBox.
- Admin-UI zeichnet diese vereinfachten Linien für **alle aktiven GLOBAL-Achsen** bereits in der Deutschlandübersicht. Bahnhofsmarker bleiben nur als Beschriftung/Navigation und ersetzen die Geometrie nicht mehr.
- Übersicht verwendet max. 28 Punkte pro Segment; vollständige Detailgeometrie bleibt Lazy-Load bei Auswahl.
- Fallback-Punkt bleibt nur für Achsen, deren Übersichtslinie nicht erzeugt werden kann.
- Keine neue Dependency, keine Migration, kein Image-Neubau.

## REF-DATA-006 – Runtime nach „alle Achsen“-Korrektur nur teilweise passend 02.10.2026
Status: **RUNTIME TEILWEISE FUNKTIONAL / DARSTELLUNG NOCH NICHT BESTÄTIGT**
- Benutzer meldet nach Deploy der vereinfachten Achslinien: „funktioniert irgendwie, aber nicht so richtig“.
- Damit ist bestätigt: der neue Pfad läuft grundsätzlich, die Darstellung/Bedienung entspricht aber noch nicht zuverlässig der gewünschten Gesamtübersicht.
- Keine weitere Codeänderung auf Verdacht. Vor dem nächsten Patch müssen Screenshot/Gesamtansicht und die konkret falschen Punkte (Linien, Deutschlandumriss, Zoom, Beschriftung, Suche, Farben oder Auswahl) eingegrenzt werden.

## REF-DATA-006 – Coverage-API Runtimefehler nach Linien-Patch 02.10.2026
Status: **REPRODUZIERBARER FEHLER / NOCH KEINE CODEÄNDERUNG**
- Neuer Screenshot zeigt auf /admin/v1/survey-overview: `Gesamtansicht konnte nicht geladen werden: NetworkError when attempting to fetch resource.`
- KPI-Kacheln bleiben leer; damit kommt die Summary-Response von `GET /api/v1/survey-catalog/coverage` nicht beim Browser an.
- HTML/Admin-Seite selbst lädt, daher ist die Fehlerzone auf Coverage-Request/API-Runtime eingegrenzt.
- Keine weitere Änderung auf Verdacht. Nächster Schritt: Coverage-Funktion im API-Terminal direkt read-only ausführen und Trace/Ergebnis erfassen; erst danach gezielt korrigieren.


## REF-DATA-006 – Coverage Direktlauf erfolgreich / HTTP-Pfad weiter offen 02.10.2026
Status: **PYTHON-FUNKTION BESTÄTIGT / HTTP-BROWSERPFAD NOCH FEHLERHAFT**
- Direkte read-only Ausführung von `coverage({})` im API-Container läuft erfolgreich und liefert: `axis_count=39`, `station_count=2`, `route_count=7`, `unsupported_axis_count=0`.
- Damit ist ein Fehler in der eigentlichen Coverage-Berechnung für diesen Lauf nicht reproduzierbar.
- Der zuvor dokumentierte `NameError: _normalize_object_type is not defined` war für den aktuellen Nutzerlauf falsch und wird hiermit korrigiert.
- Die Admin-Seite lädt weiterhin, während der Browser beim Coverage-Fetch `NetworkError when attempting to fetch resource` meldet.
- Fehlerzone ist deshalb jetzt auf den HTTP-/Routing-/Response-/Browserpfad von `GET /api/v1/survey-catalog/coverage` eingegrenzt.
- Schutzregel: noch keine Codeänderung. Nächster Schritt ist ein direkter HTTP-Aufruf des Coverage-Endpunkts aus dem API-Container bzw. gegen die laufende API, um Statuscode, Response und eventuellen Server-Trace zu erfassen.


## REF-DATA-006 – Coverage JSON-Serialisierung Runtime bestätigt 02.10.2026
Status: **PYTHON + JSON-SERIALISIERUNG BESTÄTIGT / HTTP-BROWSERPFAD NOCH OFFEN**
- Direkter Runtime-Test im API-Container mit FastAPI `jsonable_encoder` und `json.dumps(..., allow_nan=False)` erfolgreich.
- Ergebnis: `JSON_OK`, Response-Größe `15795` Bytes, `AXES 39`.
- Damit ist für den aktuellen Coverage-Datensatz auch ein Fehler zwischen Python-Rückgabewert und normaler JSON-Serialisierung ausgeschlossen.
- Keine Codeänderung auf Verdacht.
- Nächster Diagnose-Schritt: echten HTTP-Endpunkt gegen die laufende API aufrufen und Statuscode/Header/Body prüfen; danach Browser-/Proxy-/Sessionpfad eingrenzen.


## REF-DATA-007 – Trassenkennzahlen + Lazy-Übersicht SOURCE 02.10.2026
Status: **SOURCE FERTIG / NICHT DEPLOYT / SYNLOGY-RUNTIME OFFEN**
- Android↔Server-Vertrag für fertige manuelle KM-Abschnitte ist in `05_ANDROID_SERVER_VERTRAG.md` festgelegt: kanonischer Pfad = vorhandenes `POST /api/v1/survey-catalog/manual-segments` mit `MANUAL_TRA_SECTION`; Roh-TRA bleibt unverändert.
- Neuer read-only Admin-Endpunkt `GET /api/v1/survey-catalog/coverage/summary`.
- Summary liefert: gesamte aktive Trassen-/Gleislänge, belastbar kilometrierte Länge, automatisch/grün, manuell/orange, davon ausdrücklich `origin=ANDROID`, Länge ohne belastbare KM, Bahnhöfe/Betriebsstellen, Strecken, Gleise, Weichen und WVB.
- Längenbasis ist aktive Segment-/Stations-/Geometrielänge; parallele Gleise zählen als eigene physische Gleislänge. `MANUAL_TRA_SECTION` ist als KM-Overlay auf seinem TRA-Original vorgesehen und soll die physische Länge nicht doppelt addieren.
- `GET /coverage` ist standardmäßig leichter: keine vereinfachten Linien und keine komplette KM-Qualitätsberechnung beim Einstieg; vollständige Geometrie + KM-Details werden erst nach Achsauswahl über `/coverage/{axis_id}/geometry` geladen. Alte optionale Übersichtslinien bleiben über `include_overview=true` verfügbar.
- Trassendatenübersicht zeigt neue Fach-KPIs; Dashboard `Bestand` zeigt zusätzlich Trassenlänge, KM-Abdeckung, Handy-KM, Bahnhöfe, Gleise und Strecken.
- Keine Migration, keine neue Python-Abhängigkeit, kein Image-Rebuild.
- Source-Markercheck gegen aktuellen GitHub-Stand: 16/16 PASS. Das ist kein Runtime-/E2E-Nachweis.
- Bestehender `manual-segments`-Merge enthält einen auffälligen SUPERSEDED-Pfad bei OVERLAP mit ORIGINAL_TRA; mangels Runtime-Reproduktion **nicht** auf Verdacht geändert. Vor Android-E2E gezielt testen.
- Nächster Gate: drei Runtime-Dateien auf Synology übernehmen, API neu starten, `coverage/summary` bzw. Helper read-only prüfen und Dashboard/Trassendatenübersicht im Browser testen.


## REF-DATA-007 – Synology-Runtime Summary bestätigt 02.10.2026
Status: **DEPLOYT / SUMMARY-RUNTIME BESTÄTIGT / BROWSER-E2E OFFEN**
- Read-only Lauf direkt im API-Container mit `_coverage_summary_data()` erfolgreich.
- Runtime-Werte:
  - Achsen: 39
  - aktive Segmente: 40
  - Betriebsstellen: 2
  - Strecken: 7
  - Gleise: 14
  - Weichen: 4
  - WVB: 7
  - Trassenlänge gesamt: 128911.129 m
  - belastbare KM: 116578.85 m
  - automatisch: 116578.85 m
  - manuell: 0 m
  - davon Handy: 0 m
  - ohne belastbare KM: 12332.28 m
  - unplausibel: 0 m
  - Segmentqualität: 12 AUTOMATISCH, 28 FEHLT
- Ergebnis: Summary-Helper läuft auf Synology ohne Exception. Kein Hinweis auf DB-/PostGIS-/Migrationsfehler.
- Noch offen: Browserprüfung Dashboard + Trassendatenübersicht inkl. Ladezeit/Lazy-Detail.


## REF-DATA-007 – Dashboard Summary HTTP-Fehler 02.10.2026
Status: **HELPER-RUNTIME PASS / DASHBOARD-HTTP FEHLER OFFEN / NOCH KEIN CODE-FIX**
- Synology-Helper `_coverage_summary_data()` liefert gültige Werte.
- Browser-Dashboard zeigt im neuen Bereich `Trassenbestand`: **„Trassenkennzahlen konnten nicht geladen werden.“**
- Damit ist Berechnung/DB nicht der aktuell sichtbare Fehler; offen ist der Pfad `GET /api/v1/survey-catalog/coverage/summary` über FastAPI/Admin-Session/HTTP-Browser.
- Keine Änderung auf Verdacht. Nächster Schritt: exakten HTTP-Endpunkt im API-Container via TestClient prüfen und Status/Body ausgeben.


## REF-DATA-007 – HTTP-Diagnose TestClient blockiert 02.10.2026
Status: **SUMMARY-HELPER PASS / TESTCLIENT NICHT AUSFÜHRBAR / HTTP-FEHLER WEITER OFFEN**
- Versuch, den neuen Summary-Endpunkt im API-Container mit FastAPI/Starlette `TestClient` zu prüfen, ist **vor dem Request** abgebrochen.
- Ursache: Runtime-Image enthält kein Python-Paket `httpx`; Starlette meldet `RuntimeError: The starlette.testclient module requires the httpx package to be installed`.
- Dieser Befund ist **kein Fehlernachweis des Endpunkts** und kein Grund, `httpx` produktiv nachzuinstallieren.
- Keine Container-/Requirements-Änderung auf Verdacht.
- Nächster Diagnoseweg: Route/Router-Reihenfolge im aktuellen GitHub-Stand prüfen und HTTP-Pfad mit vorhandenen Runtime-Mitteln bzw. ASGI ohne Zusatzabhängigkeit testen.


## REF-DATA-007 – Browser-Timeout 524 am 02.10.2026
- Benutzerbeleg: /api/v1/survey-catalog/coverage/summary zeigt Cloudflare 524, Zeit 15:54:39 UTC / 17:54:39 Europe/Berlin.
- Status: **HTTP-/BROWSER-E2E FAIL / ROOT CAUSE OFFEN**. Browser und Cloudflare laut Fehlerseite erreichbar; Origin-Antwort nicht rechtzeitig erhalten. Tatsächliche Requestdauer nicht gemessen.
- Bisheriger Summary-Helper-PASS bleibt Funktionsnachweis, ist ohne Zeitmessung kein Performance-PASS.
- Nächster Nachweis: read-only Summary im API-Container mit perf_counter und faulthandler (30-Sekunden-Grenze für separaten Diagnoseprozess). Danach Befund-abhängig Berechnung/DB-Wartezeit oder HTTP/Auth/Proxy weiter eingrenzen.
- Keine Code-/NAS-/Cloudflare-/Android-Änderung und keine neue erfolgreiche Laufzeitmessung in diesem Chatblock.


## REF-DATA-007 – GVP-Projektionsengpass behoben im Quellstand 02.10.2026
Status: **QUELLSTAND FERTIG / NICHT DEPLOYT / E2E OFFEN**
- Nutzer-Synology-Stack bei 30-s-Abbruch: survey_catalog.py:638 _project_xy_station → :801 _segment_km_quality → :2614 _coverage_summary_data. Langsame GVP-Zuordnung im direkten Helper reproduziert; nicht bloß Browserfehler.
- Implementierung in server/tbserver/survey_catalog.py: einmaliger request-lokaler Grid-Index je Katalogsegment; Kandidaten nach räumlichem 120-m-Suchfenster; exakte Projektion auf unveränderte Liniensegmente. Vorverarbeitung numerischer Koordinaten einmalig statt bei jedem GVP.
- Erhält erste Segmentwahl bei Abstandsgleichheit, Stationierung, GRA-/manuelle Priorität, >=4 robuste Treffer, 120-m-Grenze, Median/±80-m-Filter und Qualitätsfarben. Sehr lange/diagonale Segmente erhalten einen speicherbegrenzten Fallback statt unbeschränkter Grid-Belegung.
- Bei weniger als vier GVP entfällt unnötiges Laden/Verdichten der Geometrie; Ergebnis bleibt FEHLT bzw. UNPLAUSIBEL gemäß bisherigen Regeln.
- Kein langlebiger Cache, keine Migration, keine neue Runtime-Abhängigkeit, keine RAM-/Cloudflare-Änderung und kein app/** geändert.
- Tests: server/checks/118_verify_gvp_projection_performance.py; elf Tests PASS, 1.200 deterministische Zufallsabfragen plus Grenz-/Prioritätsfälle. AST-/Syntaxprüfung PASS; übrige vorhandene Funktionen unverändert.
- Lokaler Benchmark inklusive Indexaufbau: 20.001 Achspunkte, 200 GVP; Vollscan 4,876853 s, Index 0,053314 s, ca. 91,5x; Ergebnisse exakt gleich. Synthetischer lokaler Nachweis, kein NAS-/API-/Browsernachweis.
- Deployment: GitHub Desktop → server/tbserver/survey_catalog.py per File Station nach /volume1/docker/tb-helfer-v1/tbserver/survey_catalog.py → API-Container neu starten. Kein Image-Rebuild; keine .env-/DB-/Originaldateiänderung.
- Danach bestehenden begrenzten Helper-Test wiederholen: Laufzeit muss ausgegeben werden, Kennzahlen gegen bisherigen Bestand vergleichen (39 Achsen/40 Segmente, 128911,129 m gesamt, 116578,85 m automatisch, 12332,28 m ohne KM, 0 m manuell bei unverändertem Bestand). Danach /coverage/summary und Dashboard/Übersicht mit Admin-Session prüfen.


## REF-DATA-007 – Summary und Dashboard im Benutzer-Nachtest bestätigt (02.10.2026)
- Benutzer meldet „kam ca. nach 2 sek“ und zeigt den erfolgreichen authentifizierten Browseraufruf von `/api/v1/survey-catalog/coverage/summary` sowie die befüllten Dashboard-Kacheln. Zuvor gemeldete Deployment-Durchführung wird damit durch Runtime-/Browser-Ergebnis ergänzt.
- Summary unverändert: 39 Achsen, 40 aktive Segmente, 2 Betriebsstellen, 7 Strecken, 14 Gleise, 4 Weichen, 7 WVB; total_length_m=128911.129, known_km_length_m=116578.85, automatic_km_length_m=116578.85, manual_km_length_m=0, manual_mobile_length_m=0, without_km_length_m=12332.28, unplausible_length_m=0; FEHLT=28, AUTOMATISCH=12. Parallele Gleise zählen separat.
- Dashboard zeigt dazu passend 128,91 km gesamt / 116,58 km belastbar / 116,58 km automatisch / 0,00 km manuell / 12,33 km ohne belastbare KM sowie 2 Betriebsstellen / 14 Gleise / 7 Strecken.
- Status dieses Teilblocks: GVP-Quellfix 687f77a33daaa100e10556300761e91ac717480f auf Synology im HTTP-/Browser-Nachtest erfolgreich; kein 524 im gezeigten Aufruf. Etwa 2 Sekunden sind eine Benutzerbeobachtung, keine instrumentierte Messung oder Lasttest. Die früheren offenen Summary-/Dashboard-Gates sind durch diesen Nachweis erledigt.
- Weiter offen: `/admin/v1/survey-overview` mit tatsächlichem Einstieg und nachgeladener Achsgeometrie prüfen. Der Screenshot zeigt das Dashboard, nicht diesen Kartenablauf. Fachliche KM-Plausibilisierung je Achse und umfangreicher Last-/E2E-Test bleiben separat offen.
- Quellprüfung der aktuellen Übersicht: Deutschlandgrenzen fest vorgegeben; Standard-Coverage lädt Übersichtspunkte, ausgewählte Achse lädt Geometrie separat. Bestandszoom, tiefer Detailzoom, überlagerndes Detailfenster und vereinbarte Verwaltung bleiben der nächste freigegebene Umbau. Noch keine neue Runtime-Änderung in diesem Nachtestblock; Portal danach, Android zuletzt.


## REF-DATA-007 – Kartenabruf vom Benutzer bestätigt, 02.10.2026 18:31 Europe/Berlin
- Auf die Nachtestfrage zum Öffnen von „Details“/Trassendatenübersicht und anschließender Achsauswahl antwortet der Benutzer: „auch so 2sek oder weniger“.
- Als erfolgreicher Benutzer-Nachtest des Karten-/Detailabrufs mit beobachteten ca. 2 Sekunden oder weniger dokumentiert. Keine getrennten instrumentierten Laufzeiten, kein neuer Screenshot, kein Lasttest; keine pauschale fachliche Prüfung sämtlicher KM-Daten daraus ableiten.
- Zusammen mit dem vorherigen Screenshotnachweis sind Summary, Dashboard und der angefragte Karten-/Detail-Ladeablauf für den aktuellen Bestand praktisch bestätigt. Timeout-Fehlerbehebung dieses Teilblocks abgeschlossen; bestätigten GVP-Fix schützen. Kein erneuter identischer Diagnosetest nötig.
- Nächster bereits vereinbarter Serverblock: bestandsbegrenzte Karte, erkennbarer Tiefzoom, Detailfenster für Bahnhof/Strecke/Achse; anschließend Bearbeiten (inkl. manuelle KM ohne GPS) und Archiv/Wiederherstellen. Originalgeometrie bleibt geschützt. Portal danach, Android zuletzt. Gesamtprüfung/Lastziel und fachliche Einzelachsenprüfung bleiben separat offen.


## REF-DATA-008 – Bestandskarte und lesende Detailfenster source-seitig fertig (02.10.2026)
- Freigabe ad5f68a7e0372df722068cff9c85d28926d45c13 umgesetzt. Runtime-Dateien: `server/templates/v1_admin_survey_overview.html`, `server/tbserver/survey_catalog.py`.
- Karte ohne Deutschlandumriss/-grenzen: Start/Maximalzoom aus vorhandenen WGS84-Boundingboxen plus Rand, Seitenverhältnis mit Breitenkorrektur, begrenztes Verschieben, Tiefzoom bis ca. 5 m sichtbare Höhe. Suche/Listen für Achsen, Bahnhöfe und Strecken; Gesamter Bestand/Auswahl einpassen, Mehrfachtrefferwahl, native modale Detailfenster. Kartenposition bleibt beim Schließen erhalten. Responsive CSS vorhanden, visuell noch nicht abgenommen.
- Details: Übersicht, zugeordnete Achsabschnitte, direkte Abschnitts-Originaldateien (Name/Typ/Größe/SHA), feldweise Änderungshistorie mit 50er-Seiten. Gruppen führen zur einzelnen Achse; keine Behauptung, dass die Quellenliste sämtliche fachlich benachbarten GRA/GVP-Dateien umfasst. Offene Konflikte als Hinweis. Noch keine neuen Schreib-/Archiv-Schaltflächen.
- Additiver Admin-GET `/api/v1/survey-catalog/coverage/{axis_id}/details`: GLOBAL/ungearchivierte Achse, Metadaten/Originalnamen und indexgestützte Historie; keine Rohgeometrie-/Originaldatei-Ladevorgänge oder Mutation. Pagination `history_before`, `history_next_before`.
- Additives `fast_overview=true` bei `coverage?include_overview=true`: vereinfachte Linien ohne zusätzliche globale KM-Qualitätsberechnung. Vorhandenes Default-/Legacy-Verhalten bleibt erhalten; unbekannte Qualität wird ausdrücklich neutral angezeigt. Detailgeometrie/KM bei Auswahl und im Nahbereich nachladen; maximal zwei gleichzeitige Geometrieabrufe, automatische Pakete bis 12 sichtbare Achsen, weitere per Schaltfläche; Browsercache bis 64 Einträge je Typ, mit Aktualisieren geleert.
- Alle UI-Abrufe verwenden AbortController mit 12-Sekunden-Frist, getrennten Fehlermeldungen/Wiederholen und Generation-/Auswahlschutz. Browser-Abbruch ist keine Zusage, dass bereits gestartete Serverberechnung sofort endet. Summary-Fehler blockiert die Karte nicht. Keine Cloudflare-/NAS-RAM-/Dependency-/Migrationsänderung.
- Prüfung: Check117 aktualisiert und PASS; elf bestehende GVP-Tests PASS; fünf neue Detail-/Fast-Overview-Vertragstests PASS; ausführbarer Node-Test der tatsächlichen Template-Logik PASS (Bounds/Tiefzoom/Seitenverhältnis, Mehrfachtreffer, Null-KM, Escaping, Historienpaging inkl. Retry, Timeout, verspätete Auswahlantwort, getrennte Fehler, Leerbestand). Python-Syntax und HTML-Struktur PASS. AST-Vergleich: ausschließlich `coverage` geändert und `coverage_axis_details` ergänzt; GVP-/Summary-/Schreibfunktionen unverändert.
- Testgrenzen: Node nutzt DOM/API-Harness, Python nutzt Cursor-Doubles; kein echter HTTP-/PostgreSQL-/visueller Browser-/Last-/Synology-Test des neuen Blocks. Chromium fehlt; Download durch Umgebung mit HTTP 403 blockiert, Jinja2 nicht verfügbar. Keine Browser-Abnahme daraus ableiten. Bisherige ca. 2 Sekunden beziehen sich auf den vorherigen, vom Benutzer bestätigten Stand.
- Übernahme: GitHub Desktop SERVER-Branch pullen; per File Station beide Runtime-Dateien in `/volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html` bzw. `/volume1/docker/tb-helfer-v1/tbserver/survey_catalog.py` ersetzen; API-Container neu starten; Browser hart aktualisieren.
- NAS-Nachtest: Einstieg/Liniennachladen mit Zeit beobachten, Ganzbestand/Zoomgrenze und Tiefzoom, Bahnhof/Strecke/Achse öffnen, Mehrfachtreffer und Zurück/Schließen, Originale/Historie sowie schneller Auswahlwechsel; Kennzahlen müssen gleich bleiben. Screenshot und Ladezeit für visuelle/Runtime-Abnahme erfragen.
- Danach wie beauftragt: Bearbeiten/Zuordnungen/manuelle KM ohne GPS, Archiv/Wiederherstellen ohne stilles Mitlöschen; manuellen OVERLAP→SUPERSEDED-Pfad vorher reproduzieren. Server-Gesamtprüfung bleibt offen; Portal danach, Android zuletzt und unverändert.


## REF-DATA-008 – Screenshot-Nachtest zeigt weiterhin alte Oberfläche (02.10.2026 19:36 Europe/Berlin)
- Benutzer nennt ca. 2 Sekunden und liefert drei Screenshots der Trassendatenübersicht. Ladezeit ist als Beobachtung gesichert, jedoch kein Nachweis für die neue Kartenoberfläche aus a8f383aba24710f6901f5ad38d3feaa8c4a91873.
- Sichtbar: alte Überschrift „Vorhandene Daten in Deutschland“, Deutschlandumriss und rechte „Treffer & Auswahl“-Spalte mit Inline-Details. Neue UI besitzt diese Überschrift nicht, verwendet coverageMap/coverageDialog und eine linke Liste mit Achsen/Bahnhöfe/Strecken. Im ersten/zweiten Bild starke grafische Überlagerungen bzw. unbrauchbare Rasterdarstellung; im zweiten Bild FEHLT bei Gl 4 mit irreführendem 0.000–0.000-km-Bereich des alten Templates.
- Live-GitHub: SERVER-HEAD a8f383aba24710f6901f5ad38d3feaa8c4a91873 bestätigt; neue Template-Marker vorhanden, alte Überschrift fehlt. ANDROID-HEAD 423f37892915cb3308a4b1ea9e4d196faa872326 unverändert.
- Status: neue UI auf Synology noch NICHT bestätigt. Ursache für alte ausgelieferte Darstellung offen (Dateiübernahme/Mount/laufender Container/Browser- oder Proxycache); keine Ursache ohne Runtime-Nachweis festlegen und keinen spekulativen Grafikpatch erstellen.
- Quellprüfung Deployment: admin_web lädt Templates relativ zu tbserver unter /app/templates. Synology-staging-Compose bindet ./templates bzw. TB_V1_SOURCE_TEMPLATES_DIR nach /app/templates; reguläres docker-compose.v1.yml bindet Templates nicht separat und verwendet die im Image kopierten Dateien. Tatsächliche laufende Konfiguration unbekannt; daher nicht pauschal weiteren Neustart oder Neubau behaupten.
- Nächster Schritt: kurzer read-only Dateimarkercheck im API-Terminal für /app/templates/v1_admin_survey_overview.html (coverageMap, coverageDialog, alte Überschrift) und /app/tbserver/survey_catalog.py (coverage_axis_details). Erst anhand des Ergebnisses aktive Dateien/Cache/Mount gezielt korrigieren. Neue Karten-/Detailfenster-Abnahme sowie Bearbeiten/Archiv-Folgeblock bleiben offen.


## REF-DATA-008 – Kartenabnahme und Weichenklärung 02.10.2026
- Benutzer bestätigt neue Ansicht und schnelle Ladezeit anhand drei Screenshots (Gesamtbestand, MPB-Nahbereich, W:107W). Dateimarker nach Templateübernahme True/True/False/True; vorher False/False/True/True. Gezeigte Darstellung/Ladeverhalten BESTÄTIGT; Historie/Originaldialoge/Mehrfachtreffer/Lasttest nicht pauschal abgenommen.
- Codebefund: survey_parser._axis_meta_from_name übernimmt bei Weichennamen Ziffern plus optionalen Buchstaben; daraus W:<Kennung>. survey_catalog._axis_fields nimmt vorrangig metadata.object_key, sonst object_key_from_name. W:107W kann daher aus einem Namen wie W107W.tra entstehen; tatsächlicher Originalname/Nummernbedeutung dieser NAS-Achse noch nicht gelesen. Letztes W nicht still entfernen.
- Karte zeichnet zugeordnete aktive Achssegmente aus TRA/abgeleiteter Geometrie. Stationsgrenzen/Segmentlänge sind keine belegten baulichen Weichengrenzen. Kein belegtes Stammgleis-/Abzweigmodell im geprüften Katalogpfad; grüne KM-Farbe bestätigt keine Topologie.
- Kleine Templateergänzung: Karten-/Untertitel lesbar als „Weiche 107W“ bzw. „WVB …“; gespeicherte Objektkennung weiterhin im Dialog und in der Suche. Weichen-/WVB-Dialog erklärt Grenzen der Achsgeometrie und fehlende fachliche Stamm-/Abzweigzuordnung. Daten/Parser/Kennungen/Geometrie/API unverändert.
- QUELLSTAND FERTIG: node server/checks/119_verify_coverage_map_logic.js PASS gegen angepasstes Template; DOM/API-Harness, kein visueller Browser-/Synology-Nachtest. Diese neue Text-/Labelergänzung noch NICHT DEPLOYT.
- NAS-Übernahme für diese Ergänzung: ausschließlich server/templates/v1_admin_survey_overview.html über GitHub Desktop → File Station in den bestehenden Templatepfad übernehmen; danach Browser hart aktualisieren und Weichendialog nachtesten.
- Weiter offen: Originalquelle zu 107W prüfen (Dateiname und tatsächliche Geometrie/Weichenmarkierungen); Stammgleis/Abzweig/bauliche Grenzen nur aus belastbarer Quelle oder ausdrücklicher fachlicher Zuordnung. Bereits beauftragtes Bearbeiten/Zuordnen/manuelle KM/Archiv-Wiederherstellen bleibt Folgeblock; insbesondere manuellen OVERLAP→SUPERSEDED-Pfad vor Freigabe reproduzieren. Keine Umsetzung dieses gesamten Folgeblocks behaupten.


## REF-DATA-009 – Achsen bearbeiten / Archiv / Wiederherstellen (02.10.2026)
- Freigaben/Nachweise zuerst committed: 4e50f535e3d5a7fc88347d1580d815fb246a1362 (Screenshots/Auftrag), d7095662a8908dd6284aa59c2543fc2476d06807 (Admin-Vertrag).
- W:107W-Quelle durch Screenshot belegt: SW_107W_GGP_x1_ER0.TRA, 624 Bytes, ein aktiver Originalabschnitt 0..55.840975 m, Achsversion 3. Detailübersicht, Abschnittsquelle und Historie in diesem Fall geladen. Rohdateiinhalt/Weichentopologie nicht geprüft; Stationsspanne ist kein Nachweis baulicher Weichengrenzen. Screenshot noch ohne Zusatzhinweis aus 24d94b9.
- Neuer Quellstand: Achsdetail → Bearbeiten (Name/Betriebsstelle/Strecke/Gleis/Objektkennung/Richtung/Typ), bewusste Archivbestätigung, eigener Archivdialog mit Suche/50er-Seiten und Wiederherstellen. Nur Achsen, kein stilles Mitarchivieren von Nachbarobjekten/Originalen/Abschnitten. Archivierte Eingaben gesperrt bis Wiederherstellung. Unsicherer Schreibausgang/409 verlangt Neuladen; keine automatische Schreibwiederholung.
- Additive Admin-only GET/PATCH/POST management/axes-Routen mit Admin-CSRF, row lock, base_version, identity_key-Konsistenz, 409 bei Identitätskollision (auch mit Archiv), Audit und Historie. Archiv speichert vorherigen Status; Wiederherstellen berücksichtigt vorheriges REVIEW/offene Konflikte. Kein physisches Löschen/keine Geometrieänderung. Admin-Detail-GET liest auch archivierte Achsen; aktive Karte/Geometrie schließt sie weiter aus.
- Konkurrenzschutz: _row sperrt gelesene Achszeile für nachfolgende Mutation; _axis_review liest aktuellen gesperrten Stand und bewahrt ARCHIVED, statt aus einem alten Achs-Snapshot wieder REVIEW zu machen. Wiederherstellungs-/Metadatenänderungen versioniert. Bestehende API ohne management-Präfix im Übrigen unverändert.
- Tests lokal: 12 Management-Funktionstests PASS, 6 Detail/Fast-Overview-Tests PASS, Node-Karten-/Management-Harness PASS inkl. Admin-Scope/CSRF-Header, Versionsfehler, doppelte Betätigung, Schreibtimeout ohne Auto-Retry, Archivpaging/Escaping. Python-Syntax PASS. Tatsächliche Produktionsfunktionen mit Cursor-Doubles bzw. DOM/API-Harness; keine PostgreSQL-/FastAPI-HTTP-/Postman-/visuelle Browser-/Synology-Abnahme. Postman-CLI und lokale FastAPI/Psycopg-Runtime hier nicht vorhanden; kein Produktivschreibtest durchgeführt.
- Status: QUELLSTAND FERTIG / NICHT DEPLOYT / SYNOLOGY-E2E OFFEN. Runtime-Dateien: server/tbserver/survey_catalog.py und server/templates/v1_admin_survey_overview.html. Keine Migration/Dependencyänderung. GitHub Desktop → File Station in bestehende Mountpfade übernehmen, API neu starten, Browser hart aktualisieren.
- NAS-Gate: Details öffnen → Name bewusst ändern → Historie/Zuordnung prüfen; freigegebene Testachse archivieren → verschwindet aus Karte → im Archiv auffindbar → wiederherstellen → Original/Abschnitte vorhanden. Zweiter offener Editor muss bei stale Version 409 zeigen. Nur Nutzer wählt reale Testdaten und führt bewusste Schreibaktionen aus.
- Weiter: manueller KM-Editor noch NICHT freigeschaltet. Lokale Reproduktion der echten create_manual_segment-Funktion mit Doubles bestätigt OVERLAP_CONFIRMED gegen ORIGINAL_TRA → Rückgabe MERGED, gespeicherter Kandidatenstatus SUPERSEDED. Kein Synology-Nachweis; eigener fachlicher Fix/Regression vor UI. Stammgleis/Abzweig/bauliche Weichengrenzen benötigen Originaldaten/Belege, werden nicht aus Dateinamen geraten. Server/Portal zuerst, Android unverändert.


## REF-DATA-009 – Benutzer-Nachtest Bearbeiten bestätigt (02.10.2026)
- Benutzer bestätigt ausdrücklich „bearbeiten klappt“. Achsbearbeitung damit im Benutzer-Nachtest bestätigt; keine pauschale Abnahme von Archiv/Wiederherstellen, Versionskonflikten oder sämtlichen Schreibfeldern. Diese übrigen Runtime-Gates bleiben offen.
- Screenshot zeigt lesbare Kartenbezeichnung „Weiche 107W“. Daraus keine Bestätigung der vollständigen Weichenhinweise oder fachlichen Topologie ableiten.
- Rückfrage zur Spanne 0..55.840975 m: derzeit ein Originalachsabschnitt aus SW_107W_GGP_x1_ER0.TRA. Gemeinsamer Nullpunkt von Stamm-/Zweiggleis muss fachlich belegt sein; er bedeutet keine automatisch identischen Weglängen oder Endstationen. Getrennte Geometrien/Weichenenden sind für 107W noch nicht nachgewiesen. Keine Stationsspanne auf einen zweiten Ast kopieren.
- Nur Dokumentationsnachtrag; keine Runtime-/Android-/Originaldatenänderung. Manueller KM-Editor und OVERLAP/SUPERSEDED-Fix bleiben separat offen.


## REF-DATA-010 – Weichengrenzen aus Originalen oder mobiler GPS-Erfassung (02.10.2026)
- Nutzerauftrag: Originalpaket 2026031_Gessertshausen.zip prüfen; falls Weichenzuordnung nicht plausibel belegbar, am Handy WA, WE und WEZ per GPS setzen können. SERVER/Portal zuerst, ANDROID danach; keine Android-Implementierung in diesem Block.
- Fachliches Ziel: eine Weichenidentität mit gemeinsamem WA, getrenntem WE im Stammgleis und WEZ im Zweiggleis; keine Gleichsetzung der beiden Weglängen. Bestätigte vorhandene Achsen können durch diese Punkte begrenzt/zugeordnet werden. Drei GPS-Punkte allein bestimmen keinen exakten Bogenverlauf; ohne belastbare Achse nur manuelle Näherung/weitere Aufnahme, keine behauptete exakte Trassierung.
- Mobile Ergänzung getrennt/versioniert, Originale unverändert. Punktrollen, Koordinaten, GPS-Genauigkeit, Aufnahmezeit, Herkunft und Weichen-/Achszuordnung erhalten; bei unklarer Zuordnung oder unzureichender Genauigkeit Prüfbedarf anzeigen statt automatisch auf ein Nachbargleis zuzuordnen. Manuelle Herkunft von KM-Qualität getrennt führen; GPS-Punkte erzeugen allein keine belastbare Streckenkilometrierung. API-/Schema-Ausarbeitung und Implementierung offen; bestehender MANUAL_TRA_SECTION-Vertrag nicht still umdeuten.
- Read-only Originalbefund: SW_107W_GGP_x1_ER0.TRA 624 Bytes, SHA256 ad36da35793bc6819f76ba4b78ff0768ca3e4eb649c621c0b830e855f96d4bcd entspricht Portalnachweis. Sechs Elemente/sieben Knoten, Stationen 0, 6.7, 16.7, 26.7, 30, 30.64, 55.840975 m. Elementlängen summieren sich auf 55.840975 m; Koordinatensehnen passen numerisch zu Stationsdifferenzen (6.699979/9.999932/9.999932/3.299977/0.640000/25.190609 m). Stationsspanne damit intern nachvollziehbar, keine automatische Bestätigung baulicher Weichengrenzen.
- Paket enthält Feldriss, drei Trassierungsplanblätter, TRA/GRA, Festpunkte und Koordinaten. Textsuche/Plan-Sichtung ergab bislang keine eindeutige vollständige Zuordnung WA/WE/WEZ zu 107W; nicht als bewiesenes Fehlen aller Hinweise im Plan ausgeben. Roh-TRA ist eine einzelne Achskette, keine explizite Drei-Punkt-/Zwei-Ast-Struktur. Keine Originaldatei verändert oder ins öffentliche Repository kopiert.
- Prüfstatus: lokale Binär-/Koordinatenprüfung und Plan-Sichtung; keine vollständige vermessungstechnische Abnahme, kein NAS-Import, keine Runtime-/Geräte-E2E. Bearbeiten bleibt benutzerbestätigt, Archiv/Wiederherstellung und manueller KM-Overlap-Fix separat offen.


## REF-DATA-011 – Manueller TRA-KM-Overlay: Quellfix und Regression (02.10.2026)
- Status: QUELLSTAND FERTIG / SYNOLOGY-DEPLOY UND HTTP-/DB-E2E OFFEN. Kein manueller KM-Editor in dieser Änderung freigeschaltet; dessen Eingabe-/Grenzen-/Berechtigungspfad bleibt nächster UI-Block. Neuanlage Weiche über WA/WE/WEZ→Nummer/Bahnhof als Zielablauf gesichert, noch nicht implementiert.
- create_manual_segment: MANUAL_TRA_SECTION bleibt bei bestätigter geometrischer Überlappung ACTIVE. Original und KM-Zuordnung bestehen nebeneinander. index_original_in_tx verdrängt bei Originalimport nur überdeckte MANUAL_GPS-Geometrie, keine MANUAL_TRA_SECTION-KM-Ergänzung. Bestehende GPS- und neuere-Original-Priorität unverändert. Kein Eingriff in alte SUPERSEDED-Datensätze.
- Summary: manuelle Abschnitte auf derselben aktiven Originalquelle werden innerhalb der Originalstationsgrenzen als Intervallvereinigung gezählt. Überlappungen/identische/verschachtelte Abschnitte zählen nicht mehrfach. Nur Qualität MANUELL darf Basislänge umklassifizieren; ohne belastbare Stationsüberlappung keine geschätzte Umklassifizierung. Unzugeordnete Abschnitte behalten ihre tatsächlich berechnete Qualitätsklasse statt pauschal MANUELL zu werden. Keine globale Deduplizierung fremder Originalquellen/Parallelgleise behaupten; widersprüchliche KM-Anker verschiedener Ergänzungen werden dadurch noch nicht fachlich aufgelöst.
- GRA-Kontext zählt MANUAL_TRA_SECTION nicht als zusätzliche Grundgeometrie; aktive KM-Overlays schalten die bestehende Einzeloriginal-GRA-Zuordnung nicht ab.
- Reproduktion vor Fix: Create und späterer Import deaktivieren jeweils TRA-KM-Overlay; Summary 0..100 + 50..150 ergibt 200 statt 150 m, FEHLT-Overlay ergibt 100 m manuell. Nach Fix: 16 Tests in server/checks/122_verify_manual_overlay.py PASS (echte Produktionsfunktionen per AST, Cursor-/Geometrie-Doubles; Status, Replay, Review, Quellen/KM-Erhalt, Original-/GPS-Priorität, Intervallgrenzen/Vereinigung, fehlende KM, Mobile-Zählung, GRA-Kontext). Bestehende 12 Management- und 6 Detailtests PASS; Python-Syntax PASS. Keine Behauptung eines echten HTTP-/PostgreSQL-/Postman-/NAS-/Geräte-E2E; kein neuer visueller UI-Test erforderlich, Template unverändert.
- Runtime-Datei nur server/tbserver/survey_catalog.py. Keine Migration/neue Abhängigkeit/app/**-Änderung. GitHub Desktop→File Station in bestehenden tbserver-Mount übernehmen, API-Container neu starten. Ohne aktive manuelle Ergänzungen müssen bestehende Summary-Zahlen unverändert bleiben. Vollständiger manueller Schreib-/Änderungs-/Lese-Nachtest folgt mit dem Editor; Originale geschützt.


## REF-DATA-012 – Admin-KM-Editor Quellstand fertig (04.10.2026)
- Vertrag vor Entwicklung: 7ef531771c4acfc62cc43282b0cc34c971b83704. GitHub-Ausgang SERVER 4ac9792fd4366c525e665dc9bdb07d9194acd213 / Draft-PR #6; ANDROID 423f37892915cb3308a4b1ea9e4d196faa872326 / offener PR #11 unverändert.
- Achsdetail → „KM manuell …“ öffnet Editor: aktiven ORIGINAL_TRA-Abschnitt auswählen, Achsposition Anfang/Ende in Metern, Strecken-km als Dezimalzahl (Komma/Punkt; keine Tausendertrennzeichen), Notiz; Vorschau vor Speicherung. Vorschau zeigt Verlauf mit A/E, Achslänge/KM-Differenz, steigende/fallende Richtung. KM werden zwischen Ankern linear zugeordnet. Abweichung > max(5 m, 5 % der Achslänge) verlangt ausdrückliche Bestätigung; dies ist eine Eingabeplausibilisierung, kein vermessungstechnischer Nachweis. Eingabeänderung verwirft Vorschau.
- Aktive manuelle TRA-KM-Abschnitte können geändert oder mit ausdrücklicher Bestätigung deaktiviert werden. Originale bleiben erhalten; Historie/Audit fortgeführt. Ursprung ANDROID bleibt beim Admin-Ändern erhalten, last_edited_via=ADMIN kommt hinzu. Originalquelle bei Änderung unveränderlich. Aktive Originalquelle muss weiterhin existieren; archivierte Achsen gesperrt.
- Additive Admin-CSRF-Routen preview/save/deactivate wie vertraglich gesichert. Direkte Bindung an ausgewählte Achse/Originalsegment ohne räumliches Raten. Endliche Werte, positive Stationsspanne innerhalb Quelle, verschiedene KM-Endwerte, Achs-/Quell-/Segmentversionen. Bestehende aktive manuelle Zuordnungen derselben Originalquelle werden an beiden Überlappungsgrenzen verglichen, Abweichung >0,01 m oder unvollständige bestehende KM blockiert mit 409. Keine Konfliktauflösung über fremde Originalstationierungen behaupten.
- Schreibtransaktion mit Achs-/Segmentsperren, Mutation-Receipt inklusive SHA256 des Requests; gleicher Request wiederholbar, anderweitig wiederverwendete ID 409. Keine automatische Schreibwiederholung im Browser. Unklarer Schreibausgang/Fehler fordert Aktualisieren des gespeicherten Stands. Nach Erfolg Karte/Details/Summary neu laden.
- Geometrie: _clip_station_points interpoliert Abschnittsgrenzen auf verdichteter Originalgeometrie; kleine Abschnitte unterhalb des bisherigen Abtastabstands funktionieren. Leerer/ungültiger beidseitig begrenzter Ausschnitt liefert keine Vollachse mehr. Unbegrenztes Laden bleibt unverändert. Die Interpolation ist kein Ersatz der Roh-TRA und keine neue vermessene Geometrie.
- Tests: 14 neue Tests server/checks/123_verify_admin_km_editor.py PASS mit echten Produktionsfunktionen/Pydantic und Speicherdoubles (Vorschau read-only, Grenzen, NaN/Inf, fallende KM, Bestätigung, Versionen, Archivschutz, Replay, Mutation-Reuse, widersprechende/konsistente Überlappung, Update/Deaktivierung/Originalschutz, Herkunft). Bestehende 16 Overlay-, 12 Management-, 6 Detailtests PASS; Node-Check119 erweitert PASS für Komma/Einheiten, Vorschauinvalidierung, Escaping, Admin-CSRF, Doppelklick, Konflikt und Timeout ohne erneutes Schreiben. Python-Syntax PASS.
- Testgrenzen: kein echter FastAPI-/PostgreSQL-/Postman-/Synology-/Geräte-E2E. Postman CLI, lokale FastAPI/Psycopg/httpx fehlen. Playwright verfügbar, aber Chromium fehlt; offizieller Browserdownload HTTP403 durch Netzwerkumgebung blockiert. Daher kein visueller Browser-/Screenshotnachweis. Kein Produktivschreibtest.
- Status: QUELLSTAND FERTIG / DEPLOY UND BENUTZER-NACHTEST OFFEN. Runtime-Dateien server/tbserver/survey_catalog.py + server/templates/v1_admin_survey_overview.html; keine Migration/Dependency-/Android-Änderung. Übernahme GitHub Desktop → File Station in bestehende Mountpfade → API neu starten → Browser hart aktualisieren.
- NAS-Nachtest: Achse → KM manuell → aktive Quelle bewusst auswählen → kurze gültige Stationsspanne und fachlich bekannte KM eingeben → Vorschau prüfen → speichern → orange Abschnittslinie/Details und Kennzahlen prüfen; Änderung und Deaktivierung testen, Original bleibt. Zweiter alter Editor muss Versionskonflikt abweisen; unpassende Grenzen und widersprechende Überlappung dürfen nicht speichern. Bestehende GVP-/Kartenabnahme bleibt geschützt.
- Weiter offen: Synology-Abnahme dieses Editors, Archiv/Wiederherstellung eigener Nachtest, Weichenneuanlage WA/WE/WEZ→Nummer/Bahnhof (Server-/Portalmodell zuerst, Android danach), übergreifende Server-/Portal-E2E.


## REF-DATA-013 – GVP-Editor und Achslabels: Quellstand (04.10.2026)
- Vertrag vor Code: d15b6c945212a6bbd4b2a73a6a09a51deb1455f4. Basis SERVER ea809ef1cf4b99ff49cddeeef2e4e6ff99622df4 / Draft-PR #6; ANDROID 423f37892915cb3308a4b1ea9e4d196faa872326 / PR #11 unverändert. STATUS: QUELLSTAND IMPLEMENTIERT UND LOKAL GEPRÜFT; NAS-DEPLOY/VISUELLER BROWSER-/ECHTER HTTP-/POSTGRESQL-E2E OFFEN.
- „KM manuell“ startet bei Neuanlage mit GVP-Auswahl. Originalabschnitt bewusst wählen → GVP und Achse laden → zwei Punkte per Karte oder beschrifteter Liste wählen → importierte KM prüfen/ändern (Komma/Punkt, Anzeige km, API Meter) → Berechnen und prüfen → Vorschau/Speichern. Karte zoombar/verschiebbar; gestrichelte Lotlinien, Lotabstand und Achspositionen sichtbar. „Achspositionen direkt eingeben“ erhält Zahleneditor; vorhandene Abschnitte dort änderbar/deaktivierbar. GVP-/KM-/Quellenwechsel verwirft Berechnung und Speichervorschau. Originalgeometrie bleibt unverändert.
- Additive Admin-Routen GET management/axes/{axis_id}/gvp?source_segment_id und CSRF-POST .../manual-km/gvp-calculate. Kandidaten nur aktive globale GVP/KF/Festpunkt-Assets in Quell-BBOX +100 m; höchstens 100 Assets/2000 Punkte, sichtbarer Begrenzungshinweis. Quelle/Punktname/Strecke angezeigt, keine automatische fachliche Zuordnung oder private Daten. Leere Auswahl fordert GVP-Import oder Zahleneditor. Vorschaugeometrie auf 1500 Punkte begrenzt; Berechnung auf vollständiger verdichteter Originalgeometrie.
- Lot serverseitig ohne Endpunktklemmung auf gewählter Originalachse; maximal 100 m Abstand. Ähnlich nahe unterschiedliche Lotfüße (<0,2 m Distanzunterschied, >1 m Stationsunterschied) abweisen, direkte fachliche Eingabe bleibt Alternative. Zwei verschiedene Punkte erforderlich, Stationen sortiert, Mindestabstand 20 m, KM-Differenz mindestens 1 m, Faktor-Betrag 0,5..1,5; fallende KM möglich. Linear nur zwischen Ankern; bestehende Warnbestätigung >max(5 m,5 %) bleibt. Dies bildet Android-Zweipunktkalibrierung ab, nicht zusätzlich dessen Einanker-/KM-Sprungmodell.
- Preview/Save lösen gvp_anchors erneut auf und prüfen Stations-/KM-Ergebnis, Quell-/Assetversion und aktive Zustände. Asset-Lesesperre beim Auflösen. Overlay-Metadaten speichern Referenz, Originalname/-ID/SHA, ursprünglichen/eingegebenen KM-Wert, Koordinaten, Lotfuß/Abstand; ursprüngliche Quellen unverändert. Direkte spätere Zahlenbearbeitung entfernt die aktuelle GVP-Bindung, vorheriger Stand bleibt in Historie. Vorhandene Rechte, Achsversionen, Overlap-Konflikte, Schreib-Receipt und keine automatische Schreibwiederholung unverändert. Keine Android-/Schema-/Abhängigkeitsänderung.
- Kartenlabels: tatsächliche Linien an Viewport clippen, Anker nahe sichtbarer Mitte, nach Zoom/Pan neu setzen. Kollisionsarme Alternativpositionen mit Verbindungslinie/Achspunkt; ausgewählte Achse und Gleisnamen priorisiert. Kein pauschales Ausblenden aller Labels bei >30 Achsen. Bei extremer Dichte bleiben kollidierende Namen unterdrückt; Trefferliste bleibt verfügbar. Ausgewählte Achse erhält links/rechts Fortsetzungspfeile an belegbaren Randkreuzungen. Keine topologische Aussage „nächste Weiche“ aus geometrischer Nähe erfinden.
- Tests: 10 neue echte Produktionsfunktions-/Pydantic-Tests 124 PASS (Lot, Grenzen/Abstand, gemeinsame Ecke, Mehrdeutigkeit, fallende/vertauschte Anker, Plausibilität, Quellversion/Scope, read-only begrenzte Liste, Preview/Save-Provenienz und Manipulationsschutz, Admin/CSRF). Bestehende 48 Backend-Tests 120–123 PASS. Node119 erweitert PASS: Kurvenanker, Verschieben, >30 Achsen, Randhinweis, GVP-Klickauswahl-Funktion/Listen, Einheiten, CSRF, Vorschau und Invalidierung, Escaping; DOM/Transport-Doubles. Python-Syntax PASS. Kein visueller Screenshotnachweis: Chromium lokal nicht verfügbar, voriger Download durch Umgebung HTTP403 blockiert. Kein echter API-/DB-/NAS-/Geräte-E2E.
- Deploy nur server/tbserver/survey_catalog.py + server/templates/v1_admin_survey_overview.html über GitHub Desktop→File Station in vorhandene Mounts; API neu starten, Browser hart aktualisieren. Keine Migration. NAS-Nachtest: Gl1/Gl4 beim Zoom/Pan mit Linie vergleichen; GVP-Quelle öffnen, zwei fachlich bekannte Punkte wählen, Lotfüße/KM prüfen, Vorschau und Speichern, orange Abschnitt/Details/Summary, dann Zahlenänderung/Deaktivierung. Alte Quellen/Versionskonflikte dürfen nicht still speichern.
- Weiter offen: dieser NAS-/Browser-/Schreibnachtest, Archiv/Wiederherstellen eigener E2E, Weichenneuanlage WA/WE/WEZ→Nummer/Bahnhof, Android-Anbindung und übergreifende Server-/Portal-E2E. Bestehende Benutzerabnahme schnelle Karte/Achsbearbeitung geschützt.


## REF-DATA-014 – Benutzerbefund und Vorschläge, noch kein Runtime-Umbau (04.10.2026)
- Nutzer bestätigt „funktioniert“ für GVP/Kartenstand und zeigt GVP-Auswahl, mitwandernde Gl302/303-Labels und orange Abschnittslinie. Diese Funktionsbestätigung festhalten; Versionskonflikt-/Archiv-/vollständiger Geräte-E2E nicht pauschal abgenommen.
- Read-only Prüfung des erneut gelieferten Original-ZIP: 5302S_ER0.TRA und 5340S_ER0.TRA vorhanden. Live-Parser ordnet S der Richtung 0 zu; L/R bei Strecke5302 werden aktuell pauschal track302/303 zugeordnet. Keine örtliche Begrenzung dieser Namenszuordnung vorhanden.
- Reproduktion mit Produktions-Parser, survey_geometry/survey_tra_chainage und _dense_payload am SERVER8ec77b3: 5302S erzeugt 16497 verdichtete Punkte, nach Zuschnitt0. Rohgrenzen4597.6468..37599.999993 m, normalisierte geometrische Grenzen4597.6468..37506.845793 m. Vier belegte Type6-Nullweg-Stationssprünge (+2.12,+73.66,+19.3963,-2.0221 m), Summe93.1542 m. _clip_station_points lehnt obere Rohgrenze außerhalb normalisierter Geometrie ab. Regression des strikten Zuschnitts; fehlende sichtbare 5302-0 damit am Original reproduziert, NAS-Katalog nicht ausgelesen. 5340S444→444, 5302L3470→3470, 5302R3545→3545. Original unverändert; nicht durch Reimport/Umbenennen kaschieren.
- Paket-/Planprüfung: TRA/GRA-Enden belegen keine Bahnhofsgrenzen. In textlicher Suche und Übersichtssichtung der äußeren Trassierungsplanblätter keine eindeutigen beidseitigen Bahnhofsgrenzen verifiziert. Pläne enthalten Umbauanfang/-ende; diese nicht als betriebliche Grenzen übernehmen. Keine vollständige vermessungstechnische/planerische Abnahme behaupten.
- Vorschlag, noch keine neue Implementierungsfreigabe: durchgehende Originalachse behalten, gesonderte bestätigte Abschnittszuordnung mit Betriebsstelle/Gleisname/Quelle/KM bzw. eindeutiger Achsposition. Innerhalb z.B. Gessertshausen Gl302/303, außerhalb Strecke5302 und bestätigte Richtung/Gleisbezeichnung. Fachliche Bahnhofsgrenze getrennt von frei gewähltem Arbeits-/Anzeigebereich führen, Grenzen je Achse/Zufahrt statt pauschaler Kartenbox.
- Priorität als Vorschlag: zuerst Stationssprung-/Zuschnittregression von5302-0 korrigieren und Originalfixture regressionsprüfen; dann Abschnittszuordnung/Bahnhofsbereich; danach bereits offene Weichenneuanlage WA/WE/WEZ→Nummer/Bahnhof. Archiv/Wiederherstellung und weitergehende E2E offen. Server/Portal zuerst, Android danach. In diesem Block ausschließlich Analyse/Dokumentation, keine Runtime-Datei geändert.


## REF-DATA-015 – Implementierter Serverblock, Live-Abnahme offen (04.10.2026)
- Implementiert in survey_catalog.py / neu survey_workspace.py / admin_survey_import.py / Übersichtstemplate / Migration020. Workspace-Records auf versionierter AXIS-Metadatenbasis; Admin+CSRF, explizite Quellversionen, Preview, Idempotenz und Historie.
- Quellgeometrie/Stationsgrenzen getrennt von ursprünglichen Rohgrenzen; komplette ORIGINAL_TRA wird bei Stationssprüngen vollständig dargestellt. Physische Originallänge normalisiert. Importwarnungen und Erstimport-Paketkennung für neue Originale.
- Optionaler WMS-Hintergrund BY/SN/BW, Anbieterwahl/Deckkraft/Attribution/Fehleranzeige. Keine Google-Kostenstelle. Reversibles quellselektives RESET/RESTORE; Neuindex respektiert Resetmarker, aktive Zuordnungen blockieren Reset. Ungewählte Quellen bleiben bestehen.
- 80 Backend-Tests + Node119 + Syntaxprüfung PASS; kein echter DB-/HTTP-/Browser-/NAS-E2E. API/Worker-Neustart nach Migration020 erforderlich.

Details, API-Vertrag, Quellen, Übernahme und Abnahmefälle: [REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md](uebergaben/REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md).



## REF-DATA-016 – Server-/Portal-Quellstand und Prüfgrenzen (04.10.2026)
- Anforderungs-/Vertragscommit vor Code: 4f44ba0951bebeaead458d1b7c82eb5df9e41bf7. Basis SERVER9740fb0 / PR6 offen Draft; ANDROID423f37892915cb3308a4b1ea9e4d196faa872326 / PR11 offen unverändert. Keine app/**-Änderung, kein NAS-Deployment.
- Neu: versioniertes GLOBAL-Anschlussnetz mit eigenen UUID-Knoten und Gleis-/Strecken-/Weichenobjekten; WA/WE/WEZ unabhängig auf Originalquelle oder manuell per Karte/GPS. Vorhandene Anschlüsse wiederverwendbar, beide Durchlaufrichtungen, gemeinsame Änderungen atomar und versionsgeprüft. Sidepanel neben Karte, Fortsetzen am Anschluss, gespeicherte Zuordnungen bearbeiten/deaktivieren. REF015-Zuordnungen bleiben separat erhalten, keine automatische Migration.
- KM-Kalibrierungen/Originale unverändert. Achsposition != Bahnkilometer. Manuell abgelesene streckenbezogene km_observations bleiben ungeprüfte Beobachtungen; GPS-Position/Genauigkeit/Höhe/Zeit werden erhalten. Ohne belegbaren Originalverlauf nur gestrichelte Näherung. Geänderte/archivierte Quellen → needs_review, keine alte Kurve als gültig anzeigen.
- Neues gemeinsames API /survey-catalog/network: preview, mutations, paginierte entities, Einzelobjekt/Anschlüsse, stationsbezogene changes. Device-Bearer und Portal/Admin-CSRF über bestehende Guards. Idempotenz, stabile IDs/Versionen, ein vollständiger Batch pro Feed-Ereignis; commit_cursor eines Uploads nie als Lesecursor übernehmen. Capability survey_connection_network_v1. Migration021 erstellt Netzbestand/History/Receipts. Original-/KM-Feed weiterhin zusätzlich verfolgen.
- Zusätzliche Prüfung/Korrektur: Workspace-Receipts verwenden AXIS/ASSET statt pauschal SEGMENT; aktive Netzreferenzen schützen Quellenreset und allgemeinen Testreset. Neue Receipts behindern Benutzer-Purge nicht. Veralteten Auth-Test28 auf tatsächlichen case-insensitiven Bearer-Guard korrigiert, kein Auth-Runtimefehler dabei nachgewiesen.
- 97 Python-Prüfungen120–127 PASS (80 bestehende +17 neue), Node119 tatsächliche Templatefunktionen im DOM/Transport-Doppel PASS, Auth-Quellchecks21/28/29 PASS, Python-Syntax PASS. Kein echter FastAPI-/DB-/PostGIS-/NAS-/Geräte-E2E und kein visueller Browsernachweis; Paketinstallation/Browserzugang in Arbeitsumgebung eingeschränkt.
- Luftbilder: zusätzlich NW, HE, TH und BB/BE anhand offizieller Metadaten eingebunden, WMS1.3-Lat/Lon-Reihenfolge geprüft. Insgesamt acht Länder konfiguriert, nur Bayern benutzerseitig als geladen belegt. Die übrigen acht Länder haben offizielle Links mit sichtbarem Prüfstatus; Einbettung ausdrücklich offen. Keine pauschale bundesweite Kosten-/Verfügbarkeitszusage.
- Benutzerbelege zum Vorgänger: Migration20/20, Bayern-Luftbild sichtbar, Gl4-Anfang/Ende funktioniert. Diese Abnahmen bleiben geschützt; neue Netz-/Handy-Abnahme offen.
- Weiter: GitHub Desktop→File Station sieben Runtime-Dateien übernehmen, Migration21/21, API/Worker-Neustart, StrgF5; gemeinsamer Punkt/umgekehrte Weiche/Gl303→freie Strecke und unveränderte GVP-KM auf NAS prüfen. Android-Outbox/Anzeige/Roundtrip erst im nachfolgenden Android-Block. Vollständiger Serveraudit/Last-/BTB-/Backup-E2E nicht durch diese gezielte Prüfung ersetzt.

Vertrag/Übernahme: [REF-DATA-016_ANSCHLUSSNETZ_SYNC.md](uebergaben/REF-DATA-016_ANSCHLUSSNETZ_SYNC.md). Anbieter: [REF-DATA-016_LUFTBILD_PRUEFSTAND.md](uebergaben/REF-DATA-016_LUFTBILD_PRUEFSTAND.md). Serverprüfung: [REF-DATA-016_SERVERPRUEFUNG.md](uebergaben/REF-DATA-016_SERVERPRUEFUNG.md).


## REF-DATA-017 – Punkteditor: Quellstand umgesetzt (04.10.2026)
- Freigabe vor Code: fa7b8c4198865e36dba21d75bf720539f774d7eb. SERVER-only, PR6 bleibt Draft; ANDROID423f378 unverändert. Runtime-Datei ausschließlich server/templates/v1_admin_survey_overview.html.
- Reproduzierbarer Fehlerpfad: bisherige nearestWorkspaceStation begrenzt Projektionen auf Endpunkte. Synthetischer 100-px-Linientest mit Klick 5px vor Beginn ergibt Station0. Neue Netzpunktwahl interpoliert nur innerhalb eines Liniensegments, verwirft zu weit entfernte, ungültige oder mehrdeutige Treffer. Das belegt den Fehlerpfad, nicht abschließend die Ursache aller NAS-Screenshotpositionen. Alter Legacy-/GVP-Editor unverändert.
- WA/WE/WEZ bzw. Anfang/Ende als feste kompakte Rollenleiste, nur aktive Rolle im Formular. Eigene Achs-/Quellen-/Kartenmodus-Auswahl je Rolle; beim Rollenwechsel wiederherstellen. Auswahl und Quelle sichtbar, gewählte Linie hervorgehoben; überlagerte Achsen ausdrücklich über Auswahl unterscheiden. Positionen entlang kompletter ausgewählter Quelle setzen, sofortiger Marker; keine automatische Verbindung aus Nähe.
- Marker per Pointer entlang ihrer eigenen Quelle verschieben; andere Rollen und Kartenansicht bleiben stehen. Abbrechen bzw. außerhalb der Linie loslassen stellt Ausgangspunkt/Prüfvorschau wieder her. Freie Kartenpunkte weiterhin möglich. ID, Basisversion und km_observations bleiben beim Verschieben erhalten. Tatsächlicher Speicherpfad/Versionsprüfung/gemeinsame Änderungen bleiben unverändert.
- Wiederholte Fortsetzungsbuttons durch Menü „Hier anschließen …“ ersetzt. Rollenleiste, Status und Prüfen/Speichern bleiben außerhalb des scrollenden Formularbereichs erreichbar. Achsposition ausdrücklich kein Bahn-km. Originalgeometrie und KM-Kalibrierung unverändert.
- Test: node server/checks/119_verify_coverage_map_logic.js PASS mit echten Templatefunktionen und DOM-/Transport-Doubles; erweitert um Endpoint-Clamp-Reproduktion, innere WA/WE-Positionen, unabhängige WEZ-Quelle, Rollenwechsel, ID-/KM-Erhalt, echte Pointerhandler, Abbruch/Vorschau-Restore, ungültiges Drop und überlagerte Stationen. Bestehende 119-Prüfungen PASS. Keine erneute Behauptung der 97 Backendtests; Backend unverändert.
- Kein visueller Browser-/NAS-/HTTP-/DB-/Geräte-E2E: Chromium lokal fehlt, Download durch Umgebung HTTP403 blockiert. Benutzerbild des Vorgängers belegt Migration21/21 ohne ausstehende Migrationen, keine fachliche Abnahme des neuen Editors.
- Übernahme: GitHub Desktop → File Station; server/templates/v1_admin_survey_overview.html nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html ersetzen, API über Container Manager neu starten, Browser Strg+F5. Keine neue Migration, weiterhin21/21, kein Reindex/Reset. NAS-Nachtest: WA/WE mitten auf Linie, WEZ andere Achse, Rollenwechsel, Verschieben/Abbrechen, Prüfen/Speichern/Neuladen; gemeinsamer Anschluss WE107→WA106 und GVP-KM unverändert. Erst danach fachlich bestätigen. Android-Anbindung weiterhin später.


## REF-DATA-018 – Kartenklick entscheidet die Punktachse (04.10.2026)
- Anforderung/Befund vor Code gesichert in f808800a60404c323d8eb16f5d70db8908a3e51b; Runtime-Basis3c38cf0. Benutzer meldet REF017 weiter fehlerhaft, keine fachliche Abnahme. Screenshot belegt WE-Klick auf Gl1/5340 bei noch fest ausgewählter Quelle W107W; WEZ gelingt auf dem Weichenast. Bisheriger strikter Picker hat nur die vorgewählte Quelle geprüft. Nur einen solchen Picker zu verbessern löste den tatsächlichen Bedienablauf nicht.
- Neue Standardbedienung: Rolle WA/WE/WEZ → „Linie wählen“ → Karte anklicken. Kandidaten aus tatsächlich getroffenen sichtbaren Achsen laden, je Originalquelle die Station am Klick bestimmen. Bei Gl1/Str5340 bzw. mehreren Quellen/Stationen explizite Trefferwahl direkt im Editor, ohne normalen Detaildialog/Objektwechsel. Kein automatisches Übernehmen des ersten überlagerten Treffers. Eindeutige vollständig geladene Quelle direkt übernehmen; bei Ladefehler eines Kandidaten Auswahl als unvollständig kennzeichnen, keine automatische Einzeltrefferentscheidung. Max24 Achsen/4 parallele Leseaufrufe, sonst Zoom-/Quellenauswahlhinweis.
- „Frei setzen“ fest neben Rollenwahl; jede Rolle auch vor/nach/außerhalb TRA als MAP-Position. Ohne Treffer kann der genaue Klick ausdrücklich frei übernommen werden. „Abstand …“: Quelle, vor Anfang/nach Ende, positive Meter bis10000 → manuelle tangentiale Näherung entlang Endrichtung (WGS84-Großkreisrechnung auf mittlerer Erdkugel). Diese Hilfsposition ist ausdrücklich keine vermessene Fortsetzung und keine Bahnkilometer-Extrapolation. Verlauf außerhalb Originaldaten bleibt manuelle Näherung.
- Network-Schema1/Backend unverändert: echte Quellenpunkte als binding; freie/verlängerte Punkte als position.method=MAP ohne binding. Abstand/Seite/Quellenname/-ID/-Version und Achs-ID/-Version als Herkunftsnotiz [TRA-HILFSPUNKT] im bestehenden note-Feld, nicht als aktive Quellbindung oder automatische Nachführung. Nach späterem freien Verschieben/Neubinden entfernt der Editor den dann überholten Hilfspunkt-Hinweis. Andere Notizen, IDs, Basisversionen und km_observations bleiben erhalten; Originale/KM-Kalibrierung unverändert.
- Mehrfachauswahl hält vorherige Punkte bis bewusster Auswahl, unterbindet Prüfen/Speichern während offener Entscheidung. Rollen-/Moduswechsel, Escape, Reload oder neuer Entwurf verwerfen Kandidaten; verspätete Antworten dürfen keinen Punkt ersetzen. Klickkoordinaten unabhängig von nachfolgendem Kartenpan/Zoom auflösen. Drag bleibt explizit an der Quelle des gezogenen Punktes; „Linie wählen“ erlaubt neuen Bezug. Quellen-Cache bei Stand-/Coverage-Neuladen leeren.
- Android423f378 read-only geprüft: ImportExportFragment.zeigeStreckenObjektEditor/GPS-Messung hält Rollen und Koordinaten unabhängig von passender TRA; TrassenGrafikView bietet Achstreffer samt Station und Mehrfachtreffer bei LongPress. Android-Abschnittslängenfunktion begrenzt dagegen auf vorhandene TRA. Keine Android-Extrapolation behaupten; keine app/**-Änderung/kein neuer APK-Build. Gemeinsame Netzwerk-Sync-Anbindung weiterhin später.
- Verifikation: node server/checks/119_verify_coverage_map_logic.js PASS, einschließlich bestehender Regressionen und neuer W107W/Gl1/5340-Situation mit zwei deckungsgleichen Hauptachsen/eigenen Stationen, unabhängiger WEZ-Astwahl, WA vor Weichenquelle auf vorhandener Hauptachse, freiem WA vor allen Daten, vor/nach Abstand (metrisch geprüft), ungültiger Abstände, Erhalt von ID/KM/Notiz, unvollständigen Quellen, abgebrochener asynchroner Auswahl, Schema1-Payload und Wiederöffnen mit Testantworten. Echte Templatefunktionen, DOM-/Transport-Doubles. Kein neuer Backendtestlauf, kein echter HTTP-/DB-/NAS-/Geräte-E2E, kein visueller Browsernachweis (Browser fehlt; vorheriger Download HTTP403 blockiert).
- QUELLSTAND IMPLEMENTIERT UND LOKAL GEPRÜFT; DEPLOYMENT/NAS-ABNAHME OFFEN. Nur Runtime-Datei server/templates/v1_admin_survey_overview.html über GitHub Desktop→File Station nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html ersetzen; API über Container Manager neu starten; Browser Strg+F5. Migration bleibt21/21, kein Reset/Reindex. NAS-Test: W107W öffnen, WE→Linie wählen→Gl1/5340 anklicken→Treffer ausdrücklich wählen; WA frei vor Quelle oder Abstand setzen; WEZ separat; Vorschau/Speichern/Neuladen und unveränderte GVP-KM prüfen. Keine Abnahme aus lokalen Tests ableiten.


## REF-DATA-019 – gemeinsamer Streckennetz-Arbeitsplatz (04.10.2026)
Status: QUELLSTAND IMPLEMENTIERT / LOKALE PRÜFUNGEN PASS / NAS-ABNAHME OFFEN.
- Freigabe vor Code: 137735f4bb605a483760b2e143f225825279123e. Serverbasis551722c, Android423f378 unverändert. PR6 bleibt Draft.
- Hauptmenü Streckennetz; bisherige Katalogfunktionen in Untermenü behalten. Karte/Bestand führt importierte Achsen, aktive Netzobjekte (auch ohne Originalachse) und Katalogobjekte zusammen. Bahnhöfe/Strecken auch aus Netz/Katalog ergänzen, Suche nach Gl999; Auswahl aus Liste/Kartenlinie, Mehrfachtreffer. Originalachsen bleiben als Quellen erkennbar.
- Stammdaten aus Bahnhof-/Streckendialog im eingebetteten bestehenden versionsgeprüften Editor; Katalogobjekte über stabile Katalog-ID bearbeiten oder ausdrücklich in Netzzuordnung übernehmen. Keine automatische Anlage beim Öffnen, kein Namens-/Geometrie-Automerge.
- catalog_link {id,version} optional am Netzobjekt: Typ/Betriebsstelle/Version und doppelte Verknüpfung serverseitig prüfen. Alte Clients ohne Feld erhalten vorhandene Verknüpfung; explizites null löst sie. Verknüpfte aktive Netzobjekte werden in gemeinsamer Objektliste nicht zusätzlich als Katalogobjekt dupliziert. Keine automatische Metadaten-/KM-Kopie zwischen Beständen; nach Katalogkorrektur Zuordnung erneut prüfen.
- Anzeige Originalachsen/Streckenachsen/Gleise/Weichen/Anschlüsse/Beschriftungen mit localStorage, ohne Datenmutation; aktive Editorpunkte bleiben sichtbar.
- Netzbestand global paginiert lesbar (station_code optional), beim Einstieg geladen. Fehlgeschlagener Refresh behält vorigen Bestand. Coverage ersetzt Bestand erst nach erfolgreicher Antwort; Kartenausschnitt bleibt. Übersichtslinien in Viererpaketen, höchstens zwei gleichzeitig, gezielte Wiederholung fehlgeschlagener Pakete. Neuer Admin-GET coverage/overview-lines, max8 UUIDs; gemessene Serverzeiten für Abfrage/Gesamt/Achsen, im Browser Paket-/Gesamtbeobachtung. Ursache des bisherigen NAS-12s-Abbruchs noch NICHT gemessen; keine garantierte NAS-Beschleunigung behaupten.
- Ungespeichert/Gespeichert/Versionskonflikt/Ladefehler sichtbar; Verwerfen ausdrücklich. Netzobjekte deaktivieren/wiederherstellen über vorhandene Preview/Versionsprüfung; Archiv zeigt auch deaktivierte Netzobjekte. Netzwerkhistorie enthält neutrale Feld-/Punktänderungen (neueste50, Begrenzungshinweis), keine fremden Bearbeiterdaten. Kataloghistorie zeigt Feldunterschiede; Rohdaten eingeklappt.
- Prüfungen: Node119 PASS (Produktions-JS, DOM/Transport-Doubles einschließlich neuer Such-/Filter-/Reload-/Linkfälle und aller WA/WE/WEZ-Regressionen); Python127 21 Tests PASS (Storage-/Transaktions-Doubles, Linkkonflikt/Alte-Clients/Legacy-Receipt-Fingerprint/Globalpaging/Archiv-Restore); Python128 2 Tests PASS (Produktions-Lesehelfer, SQL-Cursor-Doubles); Python-/JS-Syntax PASS. Kein Live-FastAPI/PostgreSQL/PostGIS, kein visueller Browser-/Jinja-Rendernachweis, kein NAS-/Geräte-E2E. Jinja lokal nicht installiert, Chromium weiterhin nicht vorhanden.
- Runtime7 Dateien: tbserver/survey_catalog.py, tbserver/survey_network.py; templates/v1_base.html, v1_admin_survey_overview.html, v1_admin_routes.html, v1_admin_stations.html, v1_catalog_history.html (jeweils unter server/). Keine neue Migration, weiterhin21/21. Übernahme gemeinsam über GitHub Desktop→File Station in vorhandene Mounts; API neu starten, Browser Strg+F5. Keine Originale/DB/.env überschreiben.
- NAS-Gates offen: Gl999 nach Aktualisieren/Sitzungsneustart in Bahnhof und Suche; WA/WE/WEZ frei/überlagert/vor TRA→Prüfen/Speichern/Wiederöffnen; Anzeige merken; Kataloglink/Stammdatenänderung/Versionskonflikt mit zweitem Browser; Archiv-Restore bei unveränderten Originalen/KM; Paketzeiten beobachten. Gesamt-Admin nicht fertig: Rechte-, Backup/Restore-, Worker-/DB-Stabilitäts- und Lastabnahme sowie Portal-BTB-/Android-Folgearbeiten separat.


## REF-DATA-020 – Quellstand umgesetzt und lokal geprüft (05.10.2026)
- Vorabvertrag/Bestätigungen: 8eb5cf69a17990933e8e81e5ae2dffdfab8958c2. Basis e5aa6cd; Android423f378 unverändert, keine app/**-Änderung, kein APK. Benutzerabnahmen REF019 (Gl999, Punkt-Speichern/Wiederöffnen, Anzeigewahl, Neustart/Datenbestand) bleiben geschützt.
- Befund: Netzobjekte hatten bisher keine berechneten Endpunkt-KM. Jetzt getrennte optionale versionierte km_reference und path_sections sowie position_status; Weglassen durch alte Clients erhält bestehende Werte, Legacy-Receipts bleiben gültig. Objektgrenzen begrenzen tatsächlichen Verlauf; mehrteilige Kurven mit expliziter Reihenfolge/Anschlussprüfung. Bei gemeinsam verschobenen Punkten auf gleicher versionierter Quelle folgen unveränderte Abschnittsgrenzen atomar; Quellenwechsel verlangt erneute Prüfung.
- Kilometer pro Rolle: vorhandene manuelle Teilkalibrierung (auch fallend), sonst robuste GVP-Zuordnung. Ausdrücklich gewählte andere Referenzachse möglich (Gl1 vs5340-0); kein beliebiger Überlagerungstreffer, keine Extrapolation, keine exakten Werte aus bloßen GRA-Min/Max. Konkrete Gründe bei fehlender/mehrdeutiger/veralteter Quelle. Lesepfad prüft aktuelle Kalibrierung; Endpunktwerte mit Herkunft/Version, keine Behauptung lückenloser Innenkilometrierung. Originale/Kalibrierungen unverändert.
- Ansicht Aktueller Bestand (neue Sitzungspräferenz ohne Originale), Mit Originalachsen, Originaldetails eingeklappt, Quelle zum aktiven Punktsetzen vorübergehend sichtbar; bestehende lokale Auswahl bleibt erhalten. KM/Positionsstatus in Liste/Editor/Punktlabels; offene Referenzwahl blockiert Speichern. Weichen-Auswahlfläche aus1,5m beidseitigen Korridoren als gemeinsame äußere Hülle, auch Innenbereich anklickbar. Ohne KM bleibt Weichenidentität sichtbar.
- Schutz: aktive neue Referenzen blockieren Quellenreset und allgemeinen Testreset. Erweiterte Feldhistorie. Androidberechnung read-only verglichen (ManuelleAbschnittKmLogik, Roh-KM, neutrale Originaldarstellung), keine Geräte-/Algorithmusgleichheit behauptet; neue Netz-/KM-Anbindung folgt separat.
- Verifiziert: Python12721 +1282 +12911 =34 Prüfungen PASS, Node119 alte/neue Template-Regressionen PASS, Python-Syntax PASS. Datenbank/CRS/Transport/DOM-Doubles; kein echter FastAPI-/PostgreSQL-/PostGIS-/Browser-/NAS-/Geräte-E2E. Pakete dafür lokal nicht verfügbar. Kein NAS-Deployment, keine gemessene neue NAS-Laufzeit.
- Übernahme4 Runtime-Dateien: server/tbserver/survey_network.py, server/tbserver/survey_workspace.py, server/tbserver/admin_purge.py, server/templates/v1_admin_survey_overview.html. GitHub Desktop→File Station→API-Neustart→StrgF5. Migration bleibt21/21; kein Reset/Reindex.
- Nächste NAS-Abnahme: Gl1→Kilometerbezug Str5340-0→Originalquelle ausdrücklich wählen→Prüfen→Werte/Grund bewerten→Speichern/Wiederöffnen→Anfang versetzen. Weichenfläche und mehrteilige/shared Verläufe prüfen. Gesamt-Admin/Backup/Worker/Rechte/Last und Android bleiben eigene offene Blöcke.

Vertrag, genaue Grenzen, Übernahme und Android-Folgeauftrag: [REF-DATA-020_BESTAND_KILOMETER_WEICHENFLAECHE.md](uebergaben/REF-DATA-020_BESTAND_KILOMETER_WEICHENFLAECHE.md).


## REF-DATA-021 – örtliche Referenzauswahl umgesetzt (05.10.2026)
- Benutzer bestätigt REF020-Kilometerbezug funktioniert; Beanstandung war die globale lange Liste. Keine vollständige weitere E2E-Abnahme ableiten. Basis SERVER e619a7c, Android423f378 unverändert; PR6 Draft, PR11 unverändert.
- Kilometer- und Verlaufsachslisten: aktueller Bahnhof plus Strecken im Objektumfeld (200m als Auswahlhilfe; vorhandene Linien bevorzugt, sonst Übersichtsbounds). Keine automatische fachliche Bahnhof-/KM-Zuordnung durch Nähe. Gemeinsame durchgehende Originalstrecken bleiben unverändert im globalen Bestand und werden nicht je Bahnhof dupliziert.
- Suche nach Name/Bahnhof/Strecke/Gleis, bewusste Erweiterung „Auch andere Bahnhöfe / Strecken suchen“. Max60 Treffer plus erhaltene ausgewählte Referenz; gespeicherte auch außerhalb Such-/Ortsfilter sichtbar. Änderungen an Bahnhof/Punkten aktualisieren Kandidaten. KM-Strecken im Umfeld oben priorisiert.
- Nach ausdrücklicher Achswahl eine einzige vorhandene Originalquelle direkt in Entwurf übernehmen; mehrere Quellen verlangen weiterhin bewusste Wahl, keine Quelle zeigt Hinweis. Prüf-/Speicher-/Versionspfad unverändert; keine automatischen Schreibzugriffe beim Öffnen/Filtern.
- Node119 PASS einschließlich bisheriger Regressionen und neuer Fälle: lokale/fremde Bahnhöfe, nahe/ferne Strecken, globale Suche, bestehende fremde Auswahl erhalten,100 zusätzliche Achsen/60er Begrenzung, einzelne/mehrere Quellen. Tatsächliches TemplateJS mit DOM-/Transport-Doubles; kein visueller Browser/NAS-E2E. Backend unverändert, kein neuer Backendtestlauf behauptet.
- Runtime ausschließlich server/templates/v1_admin_survey_overview.html. Über GitHub Desktop/File Station nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html; API neu starten, StrgF5. Keine neue Migration (21/21), kein Reset/Reindex. NAS-Praxistest der neuen Auswahlliste offen. Android bleibt Folgeblock.


## OPS-MONITOR-001 – Überwachungsprüfung und Quellkorrektur (05.10.2026)
- Vorabbefund f0b989b653ca59a5d37311e2d0e0d932c30394a8, Benutzerabnahmen9712fc6. Worker-Neustarterkennung verwendet jetzt started_at (normalisierte Zeit) statt nur PID, mit PID-Fallback für alte Heartbeats.
- read_monitor_snapshot erkennt fehlende/ungültige oder veraltete Zeitangaben sowie erhebliche Zukunftsabweichung. Grenzwert max(120s,3×Monitorintervall); overall ERROR und eigener Monitorcheck, historische Einzelmesswerte erhalten. Systemstatus benennt diese ausdrücklich als letzten bekannten Stand.
- Prüfung: 130_verify_monitor_liveness.py acht Verhaltenstests PASS mit echten AST-isolierten Funktionen und Datei-/Zeit-/Benachrichtigungs-Doubles (keine E-Mail versandt); Python-AST und JavaScript-Syntax PASS. Kein NAS-/HTTP-/DB-/visueller Browser-E2E. Historische Worker-Abbruchursache NICHT bestimmt; jobs.py hat bereits DB-Retries. Dafür Worker-/DB-Containerlogs und Ereigniszeit benötigt.
- Runtimeübernahme nur tbserver/monitor.py und templates/v1_system_status.html aus server/ nach /volume1/docker/tb-helfer-v1; API, Worker und Monitor über Container Manager neu starten (Worker importiert Monitor), Browser StrgF5. Keine Migration, keine Original-/DB-/Storage-/Secretsänderung, kein Android.
- Erste Source-Sichtung backup.py/restore.py: Prüfsummen, separates Restore-Test-DB-Ziel und Archivpfadschutz vorhanden. Kein vollständiger Backupkonsistenz-/Restore-Nachweis; aktuelle Backups praktisch prüfen. permissions.py gesichtet, noch keine vollständige Endpunkt-/Firmenisolationsprüfung. Diese Folgeblöcke sowie Portal/Last/Android bleiben offen.


## OPS-MONITOR-001 – NAS-Protokollauswertung (05.10.2026 15:42 CEST)
- Benutzerdateien tb-helfer-v1-db-1.html (SHA2562688bce8223d69c26cb7b33ef932b791d25235ad5b3c8177cd6ac8d2d556ac6d) und tb-helfer-v1-worker-1(3).html (SHA256678ff443c12d2afd0f9f241d570afc89740468ef555975dfe93e6169788fa286) vollständig textuell ausgewertet; keine Rohlogs/Netzadressen ins Repo kopiert.
- DB am05.10.:12:18:25.683 received fast shutdown request →12:18:30.410 shut down →12:19:00.907 ready. Zweiter Ablauf15:40:06.324 shutdown request →15:40:11.881 shut down →15:40:59.015 ready (CEST).
- Workerfehler12:18:27/32 und15:40:07–17 entsprechen dieser Nichterreichbarkeit (shutting down / Connection refused). Tracebacks werden durch worker_loop/scheduler behandelt; Log sagt ausdrücklich stays alive and retries. Kein unbehandelter Workerprozess-Absturz in diesem Export belegt. Kein Erfolgseintrag nach15:40:59 vorhanden; leeres neues Fehlerlog ist kein vollständiger Nachweis erfolgreicher Jobverarbeitung.
- DB-Export02.–05.10. zeigt weitere geordnete Shutdown/Startfolgen; kein PANIC/Crash-Recovery/Datenträger-voll/OOM-Beleg im vorliegenden Text. Ein Broken pipe / connection to client lost am04.10.13:53:59 zeigt verlorene Clientverbindung, nicht selbst einen DB-Absturz. logical replication launcher exited code1 steht im geordneten DB-Shutdown und bezeichnet keinen TB-Helfer-Worker.
- Einordnung: unmittelbare Ursache dieser Verbindungsfehler ist angeforderter DB-Stopp/Neustart. Auslöser (manueller Container-Manager-Projektneustart, NAS/Systemaktion etc.) aus diesen Logs nicht feststellbar. Ältere ursprüngliche Warnung nur bei gleicher Ereigniszeit damit erklärt; keine pauschale Stabilitäts-/Datenintegritätsabnahme.
- Keine weitere Runtimeänderung aus diesen Belegen nötig. Bei reinem Template-/Pythonupdate nur benötigte API/Worker/Monitor-Container neu starten, nicht DB/gesamtes Projekt, sofern keine andere Deploymentanforderung besteht. Für ungeplante Stopps DSM/Container-Manager-Ereignisse genau zu obigen Zeiten prüfen. Backup/Restore und Firmenrechte bleiben nächste offene Blöcke. Serverbasisdd1f935, Android423f378 unverändert.


## OPS-BACKUP-001 – Fehlerbehandlung korrigiert, NAS-Restore offen (05.10.2026)
- Vorabbefund264566316111202d2d4f421d290c5951bb3769c7. Runtime nur server/tbserver/backup.py und restore.py; Android unverändert, keine Migration/API-/Formatänderung.
- Fehlender/nicht-Verzeichnis-/Symlink-Storage-Root wird abgelehnt, tatsächlich leeres vorhandenes Verzeichnis bleibt zulässig. Links/Spezialdateien im Archiv werden mit Fehler beendet, statt später nicht wiederherstellbare Sicherung OK zu melden; .renditions unverändert ausgeschlossen.
- Fortschrittscallbacks in Backup/Restore sind best effort und loggen bei Ausfall nur den Exceptiontyp. Sie können erfolgreich abgeschlossene Sicherung/Restore nicht nachträglich scheitern lassen. Fertige Backupdateien bleiben bei Registrierungsfehler erhalten; unregistrierte Artefakte ggf.manuell prüfen. Keine automatische Bereinigung solcher Dateien. Unfertige temporäre Archive werden weiterhin entfernt.
- Restore-Test erfasst auch Fehler beim Anlegen seines temporären Ordners im Fehlerstatus.
- 131_verify_backup_failure_safety.py:11 Tests PASS, echte Archive/temp.Dateien mit Produktionsfunktionen, DB-/Fortschritts-Doubles. Fehlender/leerer Storage, Fortschritts-/Registrierungsfehler, Symlinks/Tempcleanup, FULL-Archiv-Roundtrip mit unverändertem Original und Cacheausschluss, Restorefortschritt/Scratchfehler, Live-DB-Zielschutz, Pfadtraversal und beschädigte Prüfsumme. Python-Syntax PASS. pg_dump war simuliert; kein PostgreSQL-/NAS-/HTTP-E2E, kein Nachweis vollständiger DB-Dateireferenzkonsistenz.
- Offene Grenze: FULL sichert DB und Dateien nacheinander, ohne globalen Schreibsnapshot; parallele Lösch-/Änderungsvorgänge können Konsistenzprüfung erfordern. Praktischen Vollbackup-/Restoretest in ruhigem Zeitfenster durchführen; nicht als produktiv vollständig abgenommen markieren. Wiederherstellung prüft aktuell Kernschema/Archive, nicht jeden Dateiverweis gegen DB. Weiterer Konsistenzblock bleibt offen.
- Übernahme via GitHub Desktop→File Station nach /volume1/docker/tb-helfer-v1/tbserver/backup.py und restore.py; API/Worker neu starten, DB/Monitor dafür nicht erforderlich. Systemstatus→Vollbackup, nach OK→Restore-Test (getrennte konfigurierte Testdatenbank). Fehlt Testkonfiguration, zuerst getrennte Umgebung einrichten, keine Live-DB als Ersatz verwenden. Nächster Nachweis: Status/Fehlercode des echten Laufs. Firmenrechte/Portal/Last/Android weiter offen.


## OPS-BACKUP-002 – UI umgesetzt
- Systemstatus: Button „Backup prüfen“, Backup-/Jobhistorie „Backup-Prüfung“, Dialog erklärt Wiederherstellung in getrennter Testumgebung ohne Änderung des laufenden Serverbestands. Vorhandene Backendbezeichnungen werden für RESTORE_TEST auch in der Jobliste übersetzt.
- Nur server/templates/v1_system_status.html geändert. Technische Typen/API/Funktion bleiben unverändert. JavaScript-Syntax PASS; keine neuen Backendtests für Textänderung. NAS-Abnahme des vorherigen Prüfablaufs oben dokumentiert; neue Beschriftung noch nicht auf NAS geprüft.
- Übernahme nach /volume1/docker/tb-helfer-v1/templates/v1_system_status.html; API neu starten, StrgF5. Kein Worker-/DB-Neustart erforderlich. Nächster eigener Serverblock: Firmen-/Benutzerrechte. Vollständige Backupkonsistenz und echter Produktiv-Restore bleiben getrennte offene Punkte.


## RIGHTS-001 – zentrale Rechteprüfung gehärtet (05.10.2026)
- Vorabbefunde vor Umsetzung gesichert. permissions.py: Nicht-Admin benötigt übereinstimmende nichtleere Firmen-ID unabhängig von Mitgliedschaft; normale Baustellenliste in worksites.py filtert zusätzlich Firma. ADMIN-Leserechte und CHEF-eigene-Firma bleiben erhalten. Einladungs-Firmencheck und Firmenwechsel-Historieguard waren bereits vorhanden.
- _merge_permissions erteilt Rechte nur für bool True; insbesondere String 'false' und Zahl1 erteilen kein Recht. Ungültige Overrides sperren jeweiliges Recht. Keine Datenmigration; reguläre JSON-Boolwerte unverändert.
- Nichtendliche/ungültige/Bool-KM-Werte markieren Bereich als ungültig und verweigern Zugriff; auch bei all-Flag kein Umgehen ungültiger Werte. Leerer Helper-Scope bleibt gesperrt, gültige fallende Grenzen/Labels funktionieren.
- 132_verify_permission_guards.py elf Testgruppen PASS: zentrale Rollen/Firmen, fehlende Firma, inaktive Mitgliedschaft, ungültige Boolwerte/KM, gültige Scopes, Listen-SQL-Firmenfilter; bestehende Portal-/Geräte-/Admin-Authsperren für disabled/deleted/expired (neun Kombinationen). Echte isolierte Produktionsfunktionen mit SQL-/Request-Doubles; Python-Syntax PASS. Kein Live-HTTP/PostgreSQL/NAS-/Mehrfirmen-E2E.
- Quellsichtung Dateien: Baustellen-Dateizugriff verwendet worksite_context, Dateien ohne Baustelle prüfen Firma; Detail-Bereichsprüfung bleibt vorhanden. Keine vollständige Prüfung sämtlicher Upload-/Link-/Feed-/Portalpfade behaupten. Firmenstatus-Deaktivierung, vollständige Endpunktmatrix und reale Session-/Gerätesperrtests verbleiben als Folgeprüfung. Gemeinsamer GLOBAL-Fachkatalog ist fachlich beabsichtigt firmenübergreifend und bleibt unverändert.
- Runtimeübernahme nur server/tbserver/permissions.py und worksites.py nach /volume1/docker/tb-helfer-v1/tbserver/; API neu starten. Keine Migration, kein Worker-/DB-Neustart, kein Android/Originaldatenumbau. Praxistest mit getrennten bestehenden Testfirmen: Liste und direkte fremde Baustellen-/Dateiadresse gesperrt; eigener Chef/Benutzerzugriff wie berechtigt; gesperrter/abgelaufener Testnutzer mit alter Session/Token abweisen. Produktive Nutzer nicht zu Testzwecken sperren.


## RIGHTS-002 – Admin-Baustellengrenze umgesetzt (05.10.2026)
- Firmenbereich zeigt für Baustellen nur Name/Status und Komplett löschen. Anlegen/Öffnen, Fachfelder und Anlege-JavaScript entfernt; Firmentexte angepasst. Abfrage lädt nur id/name/status. Bestehende Datensätze bleiben unverändert.
- Admin-Anlege-API antwortet 403. worksite_context verweigert ADMIN vor Datenabfrage; ADMIN_PERMISSIONS leer. Allgemeine Baustellenliste verweigert ADMIN. Alte Admin-Baustellen-/BTB-Seiten leiten zu Firmen um. Berechtigte CHEF-/Benutzerpfade bleiben erhalten. Baustellen-Dateipfad benutzt worksite_context; globale Fachkatalog-/Originalverwaltung bleibt unverändert.
- Bestehender Admin-Purge-Endpunkt verwendet: Dialog benennt Baustelle und betroffene Inhalte, verlangt ENDGUELTIG LOESCHEN; Server prüft Bestätigung plus Ziel-ID und Admin-CSRF. Keine reale Löschung ausgeführt. Kein neuer produktiver Restore oder Lösch-E2E behauptet.
- Lokale Prüfungen:132 elf Tests PASS,133 fünf Tests PASS (API-Sperre, Listen-Sperre ohne Abfrage, alte Seitenumleitung, Purge-Ziel/Bestätigung, UI-Vertrag). Produktionsfunktionen AST-isoliert mit Doubles. Python-Syntax und Template-JavaScript Node-Syntax PASS. Kein Live-HTTP/PostgreSQL/NAS-/Browser-E2E; vollständige Endpunktmatrix bleibt separater Auditpunkt.
- Runtime-Dateien:server/tbserver/admin.py,admin_web.py,permissions.py,worksites.py sowie server/templates/v1_company_branding.html,v1_admin_companies.html. Über GitHub Desktop/File Station in bestehende Ordner nach /volume1/docker/tb-helfer-v1 übernehmen, nur API neu starten und Strg+F5. Keine Migration/Androidänderung. Praxis: Firmen-Baustellenliste ohne Anlegen/Öffnen, alter Link→Firmen, Portalzugriff als berechtigter Benutzer weiterhin vorhanden; Löschdialog abbrechen bewahrt Daten. Reale Löschabnahme nur an ausdrücklich entbehrlicher Testbaustelle.


## RIGHTS-003 – Prüfergebnis Benutzer-/Sessionsperren (05.10.2026)
- Basis59dbed4, Android423f378 unverändert. Reiner Prüfauftrag; keine Runtime-/Benutzer-/NAS-Änderungen.
- Normale Portal-/Admin-/Geräteanfragen prüfen DB-Benutzerstatus, deleted_at und access_valid_until je Authentifizierung. Ablauf <= jetzt gesperrt, zukünftige/unbegrenzte Gültigkeit erlaubt. Geräte zusätzlich Status/Widerruf, Browser zusätzlich Sessionablauf und SQL-revoked_at-Filter. Tests mit gleichem Token und geändertem DB-Double bestätigt. Kein echter HTTP-/PostgreSQL-/Gerätetest.
- _valid_until_from_through:00:01 Folgetag Europe/Berlin, Sommer-/Winterzeit lokal geprüft, entspricht bestehender Vorgabe.
- OFFENER BEFUND: bestehender SSE-Stream /api/v1/sync/events prüft Benutzer/Session/Mitgliedschaft nur am Anfang. Lokaler Produktionsgenerator-Test weist nach anschließend simulierter Sperre weiterhin event:change nach; _changes_for_worksite enthält keine Authprüfung. Änderungsmeldungen enthalten IDs/Versionen/Metadaten, kein vollständiges BTB. Nachgelagerte normale Fachabfragen gesperrt. Laufender Longpoll /sync/wait verwendet ebenfalls alten Benutzer nach bis30s Warten (Default25s); Quellbefund, nicht Live-E2E. Bereits heruntergeladene Offline-Daten werden durch Serversperre nicht entfernt.
- 134_audit_session_revocation.py drei Testgruppen PASS (Authmatrix, Datumsgrenzen, Reproduktion bekannter SSE-Lücke). PASS des Reproduktionstests bedeutet Fehler nachgewiesen, NICHT Sicherheitsabnahme! Bestehende132 elf Tests PASS. Kein vollständiger Routen-/Transportaudit.
- Folgeschritt: offene SSE-/Longpoll-Verbindungen vor Ausgabe erneut authentifizieren und Baustellenrechte erneut prüfen; bei Sperre beenden; Tests müssen danach Nichtausgabe nachweisen. Noch nicht umgesetzt. Kein Deployment erforderlich für diesen reinen Prüfstand.


## RIGHTS-003 – Live-Sync-Sperren implementiert (05.10.2026)
- sync.py ergänzt Request-Injektion und _refresh_sync_user: bestehende aktuelle Portal-/Admin-/Geräteauth plus aktuelle Baustellenprüfung. Longpoll nach Hub-Warten auch bei leerem Timeout neu prüfen, bei Daten zusätzlich nach Abfrage. Authfehler als normale HTTP-Fehler.
- SSE prüft nach jedem Hub-Warten (15s Timeout) erneut und verwendet _visible_changes mit frischem Benutzer, anschließend erneute Auth-/Baustellenprüfung vor Ausgabe. 401/403/404 beendet Generator ohne weitere Meldung. Bounded200-Eventpaket wird zusammen ausgegeben, keine ungeprüften Pausen zwischen Einzelereignissen. Hub unverändert; zusätzliche Auth-/Berechtigungsabfragen sind bewusst notwendig. Kein Sofort-Push-Abbruch garantiert: ruhende Verbindung wird beim nächsten Check nach Warteende geschlossen; bereits versandte/gepufferte Daten werden nicht zurückgerufen. Keine atomare Garantie gegen Sperre unmittelbar nach letzter DB-Prüfung.
- Tests135 sieben Gruppen PASS: keine Daten/Keepalives bei Sperre,401/403/404, Sperre während Abfrage, gültiges Event dann Sperre, leerer Longpoll, Longpoll-Abfragesperre, gültige Cursor/Antworten, produktiver Refreshhelper mit Auth-/Baustellenprüfung. AST-isolierte Produktionsfunktionen mit Doubles.134 zwei Datums-/Authgruppen PASS;132 elf Rechte-/Authgruppen PASS. Python-Syntax PASS.134 früherer Defekt-Reproduktionstest durch135 negative Regression ersetzt. Kein Live-HTTP/PostgreSQL/NAS-/Geräte-/Lasttest.
- Nur Runtime server/tbserver/sync.py übernehmen nach /volume1/docker/tb-helfer-v1/tbserver/sync.py, API neu starten. Keine Migration, Android unverändert. NAS-Abnahme noch offen. Kein echter Benutzer gesperrt, keine Daten gelöscht.


## OPS-MONITOR-002 – umgesetzt/lokal geprüft (06.10.2026)
- Öffentlicher Check bleibt bei Einzel-ERROR rot/protokolliert; Warnmail erst beim dritten aufeinanderfolgenden ERROR. Nicht-ERROR setzt Zähler zurück. consecutive_errors und incident_confirmed im bestehenden Monitor-Snapshot gespeichert; Neustart kann vorhandenen Zähler übernehmen. Erholungsmail nur nach bestätigter Störung; WARN zwischen bestätigter Störung und OK erhält Incident. Vorhandene Mailfreigabe/Empfänger/Cooldown bleiben maßgeblich; keine Testmail gesendet. Andere Alarmarten unverändert.
- Öffentliche Problem-/Erholungsmail enthält aktuellen internen API-/DB-Status, Latenz und Detail. Netzwerkursache weiterhin offen; keine Heilung des öffentlichen Zugangs behauptet.
- Test136 sechs Gruppen PASS: kurze Störung ohne Mail, dritte Fehlmessung/einmalige Erholung, Reset durch WARN, persistierter/Legacy-Zähler, neuer Incident. Test130 acht Regressionen PASS; Python-Syntax PASS. Produktionsfunktionen mit Doubles, kein SMTP-/NAS-/Live-E2E. Runtime nur server/tbserver/monitor.py. Nach File-Station-Übernahme /volume1/docker/tb-helfer-v1/tbserver/monitor.py Monitor neu starten; für konsistent geladenen Stand auch API/Worker (importieren Monitor). DB nicht neu starten, keine Migration.
- Nächster Plan:1 Vollbackup-Konsistenz DB+Dateien und vollständige Dateireferenzprüfung;2 verbleibende Rechte-Endpunktmatrix und NAS-Abnahme Live-Sperre;3 Portal-BTB/Unterbaustellen/Einträge+Fotos umhängen/Wetter und tatsächliche Bildgrößen;4 Lastabnahme15x15;5 Androidanbindung gegen fertigen Serververtrag. Bestätigte Gleis-/Weichen-/Adminabläufe bleiben geschützt. Aktuelle Änderung noch nicht auf NAS abgenommen.



## SERVER-GATE-001 – Punkte 1+2+4 implementiert und CI-geprüft (06.10.2026)
- Runtimecommit 6d9fc82b6a9dc5fb0b8da55f5f2a37ebdd268ba9 auf feature/tb-server-1.0. PR6 bleibt Draft. Android 423f37892915cb3308a4b1ea9e4d196faa872326 unverändert; keine app/**-Änderung.
- Vollbackup: gemeinsamer exportierter REPEATABLE-READ-Snapshot für pg_dump und Inventar (aktive Dateien, Originale, Release-Binaries). Geprüfte temporäre Dateikopie verhindert Austausch zwischen Hashprüfung und Archivierung. Fehlende/abweichende Referenzdateien führen zu Fehler statt OK. FULL-Test und Zielrestore prüfen DB-Dateireferenzen. Neues Manifest kennzeichnet snapshot-reference-v1 und Upload-Neustart; offene Uploads nur im Restoreziel auf EXPIRED, Livezugänge unverändert. Alte Archive bleiben lesbar; getrennte DB-/Dateibackups haben keine gemeinsame Konsistenzgarantie.
- Rechte: Firmenstatus per JOIN in Portal-/Geräteauth/Login/Aktivierung, 403 COMPANY_DISABLED bei gesperrter/gelöschter/fehlender Firma. Systemadmin bleibt systemseitig nutzbar. Datei-Liste/Medien/Detail/Download/Vorschau verlangen files.view. Uploadsession gehört ausschließlich dem Ersteller; Schreiben benötigt weiterhin files.add. Gemeinsamer GLOBAL-Katalog unverändert.
- 78 lokale Tests (130–139) PASS, Python-Syntax PASS. Echte CI: Serverimage gebaut, Migrationen angewendet, PostgreSQL17 Snapshot/Dump/Restore samt parallelem Commit und negativen Dateiprüfungen PASS; echte FastAPI/PostGIS-Rechteprüfungen PASS. Vier laufende Longpoll-Sperrfälle (Nutzer/Firma/Mitgliedschaft/Gerät) und SSE nach Mitgliedschaftsentzug PASS.
- CI-Job https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37497843193/job/112386875101 SUCCESS. 3x255 echte HTTP-Syncclients (15 TB,225 Helfer,15 Portal), 0 Fehler; Median5,03–5,10s, p95 7,11–7,21s, max7,35s einschließlich2s Wartezeit. 15/15 Portal-Testsitzungen abgemeldet. CI-Kriterium10s Overhead bestanden; strengeres5s-Overhead-Beispiel nicht bestanden (bis5,21s). Kein NAS-/Cloudflare-/Foto-/BTB-Schreiblastnachweis.
- Gesamt-CI weiterhin rot durch ältere Prüfungen: foundation Check23 erwartet FILE_LINK_TARGET_NOT_FOUND, Runtime verwendet bereits in Baseline4cc7fef FILE_LINK_TARGET_NOT_VISIBLE. Android-CI scheitert an bestehendem Schema10-Quellmarker BtbVerlaufFragment.kt:MergeSafeBaustellenExport.normalisieren. Nicht abgeschaltet, keine pauschale Gesamtfreigabe.
- Übernahme8 Runtime-Dateien nach /volume1/docker/tb-helfer-v1/tbserver/: backup_inventory.py NEU, backup.py, restore.py, restore_target.py, permissions.py, portal.py, devices.py, files.py. API und Worker neu starten; keine Migration/DB-Neustart. Neuer Monitorfix aus vorigem Block separat beachten, falls noch nicht übernommen. Daten/.env/Storage/Backups behalten.
- Nächste echte NAS-Abnahme: neues Vollbackup und Backup prüfen; eigene/fremde Firmen-/Dateizugriffe und Uploads; Firmensperre/Livesync nur mit Testkonten; 255-Clientmessung auf isolierter NAS-Testinstanz. Portal-BTB/Bilder bleibt Punkt3, Android danach. Technische Details und ausführbare Befehle: server/checks/SERVER_GATE_001.md; Testtools backup_snapshot_gate.py, server_http_gate.py (nur CI/leer tb_gate_http), sync_mixed_loadtest.py.


## OPS-RESTORE-003 – Wiederherstellung implementiert und isoliert abgenommen (06.10.2026)
- Benutzerauftrag „okay dann baue sinnvoll“. NAS-Benutzerbestätigung: Vollbackup851MB und Backup-Prüfung OK19:55; Screenshot bestätigt diesen Lauf, keine tatsächliche NAS-Umschaltung/Codeinventur daraus ableiten.
- Runtime14d199222b7d5bc957b688e3f653471ce9505ad7, Serverbranch/PR6 Draft. Android423f37892915cb3308a4b1ea9e4d196faa872326 unverändert, keine app/**-Änderung.
- Systemstatus→Server wiederherstellen: FULL-Auswahl, Datum/Verlusthinweis, an Backup-ID gebundene Texteingabe, Admin+CSRF und Doppelauftragsperre. Worker erstellt Sicherheitsvollbackup, pinnt beide Archive außerhalb normaler Retention, stellt Quelle im frischen separaten Ziel wieder her und prüft Schema-Prüfsummen sowie Dateireferenzen. Status READY ist ausdrücklich noch NICHT aktiv. Fehler und Unterbrechungen werden nicht automatisch wiederholt; Teilziel bleibt zur Diagnose bestehen.
- Separate vollständige Synology-Compose-Dateien für Vorbereitung und tatsächlichen Cutover. API/Worker/Monitor/Migration müssen beim manuellen Container-Manager-Wechsel gemeinsam auf Ziel-DB+Speicher zeigen. TB_RECOVERY_ID prüft DB-/Dateimarkierung und Quellhash; falsche Kombinationen blockieren DB-Zugriffe. Bisherige Live-DB und Storage bleiben erhalten, Rückweg dokumentiert. Kein Docker-Socket, kein automatischer Live-Restore ausgeführt.
- Nur im Ziel: alle bisherigen Sitzungen/Gerätetokens/Einladungen/Aktivierungen widerrufen; andere Benutzer einschließlich anderer Admins zunächst deaktiviert, anfordernder aktiver Admin muss im Backup vorhanden sein. Passwort aus Backup gilt. Offene Uploads EXPIRED, wartende/laufende Jobs FAILED, Automatik/Retention/E-Mails ausgeschaltet. Rechte prüfen, Benutzer freigeben und Geräte bewusst reaktivieren/neu laden; keine Android-Recovery-Abnahme.
- Zusätzliche Schutzkorrektur: bisheriger CLI-Restore teilt Ziel-Advisory-Lock; Test-DB und Wiederherstellungsziel dürfen nicht denselben DB-Namen haben. Bestätigter normaler Backup-Prüfablauf bleibt erhalten.
- Lokale Python-/JS-Syntax und Compose-Paarprüfung PASS. CI85 Verhaltenstests130–140 PASS; echter PostgreSQL17/FastAPI-Recoverylauf inkl. HTTP-Admin/CSRF, Doppelauftrag, Dump/Restore, erhaltenem Livebestand, Zielzugangssperren, API-Neustart auf Ziel, falschem Storage/ID, Ziel-Lock und Stale/Fehler ohne Retry PASS. Run37510603840 Job112430437826 SUCCESS: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37510603840/job/112430437826 . 3x255 HTTP-Syncclients0Fehler; p956,23–6,37s inkl2sWartezeit; weiter keinNAS/Cloudflare/Fotolastnachweis.
- Gesamt-CI nicht grün: ältere foundation-Prüfung erwartet weiterhin FILE_LINK_TARGET_NOT_FOUND; Android-CI separat offen. Kein visueller Browser-/NAS-Container-Manager-E2E. Erster Gateversuch scheiterte ausschließlich am Testpasswort, Fixture korrigiert, danach zweimal Gate grün inklusive letzter Schutzergänzungen.
- Übernahme8 Runtime-Dateien: tbserver/recovery.py NEU, recovery_guard.py NEU, database.py, jobs.py, system_admin.py, restore.py, restore_target.py und templates/v1_system_status.html. API/Worker danach neu starten. Für tatsächliche Zielvorbereitung zusätzlich docker-compose.synology-recovery-prepare.v1.yml, für Umschaltung docker-compose.synology-recovery.v1.yml; Zielpasswort/Freigabe und später Wiederherstellungs-ID in vorhandener.env ergänzen, nichts ersetzen. Lokale Compose-Anpassungen vergleichen. Migration bleibt21. Anleitung: server/checks/OPS_RESTORE_003.md.
- Grenzen: einmaliger kontrollierter Wechsel auf frisches Ziel, weitere Wiederherstellung braucht neue passende Zielkonfiguration; nur exakt passendes Schema, kein automatischer Totalausfall-/Failoverdienst. Neues Sicherheitsbackup stammt aus Vorbereitung; spätere Änderungen verbleiben im alten Livebestand. Keine automatische Zusammenführung beim Rückweg. Keine NAS-Umschaltung ausgeführt; Übernahme/Praktikabilität auf NAS offen. Danach Portal-BTB/Bilder, Android erst anschließend; bestehende Rechte-/Last-NAS-Abnahmen bleiben getrennt offen.


## OPS-RESTORE-004 – Servervarianten implementiert (06.10.2026)
- Nutzerfreigabe22:02 nach Besprechung; Vorabvertrag775cc10. Runtime2fb6b8da607761e42610f0513c51e706fbd79d5e, Testergänzung2fad3251214291c91af3a62935a549596270f1cc. Androidbranch423f37892915cb3308a4b1ea9e4d196faa872326 unverändert, kein app/**.
- Neue Vorbereitungen: UPDATE Standard mit Benutzerstatus/Rechten/Ablauf, Firmen und Gerätebindungen/Tokens sowie Automatikpolicy aus Backup; SECURITY erhält bisherige Zugangssperren. Beide Modi widerrufen Portal-Sessions und verwerfen offene Jobs/Uploads. Zustände ohne mode bleiben SECURITY; vorhandenes NAS-READY-Ziel wird nicht geändert oder gelöscht.
- FULL-Manifeste enthalten nicht geheime Runtime-/Schemaidentität. UPDATE verlangt exakten Programmfingerabdruck und restaurierte Migrationschecksummen. Alte Backups ohne Metadaten für UPDATE abgewiesen, Backup-Prüfung/SECURITY weiterhin möglich mit passendem Schema. Serverpaket/Image muss separat gesichert werden; kein automatischer Code-/Schemadowngrade. Programmstand während Vorbereitung/Umschaltung nicht austauschen; spätere normale Updates nicht dauerhaft blockiert.
- capabilities liefert recovery_epoch_v1 + recovery_id (null vor tatsächlichem Cutover). Geräteauth prüft nach normalen Token-/Nutzer-/Firmenrechten X-TB-Recovery-ID; fehlend/falsch→409 SERVER_RECOVERY_REBASE_REQUIRED, passende Kennung erlaubt normalen Rechtepfad. Keine Umgehung durch öffentliche UUID; sie ersetzt keine Auth. Android muss Outbox schützen, Cursor verwerfen, Serverstand getrennt laden und Konflikte abgleichen, bevor normale Mutationen wieder starten. Aktuell unangepasste App bleibt nach Cutover bei Fachzugriff blockiert; daher noch kein fertiger Geräte-Rollback-E2E.
- NAS-Bestätigung Screenshot21:49: vorheriger OPS-RESTORE-003-Vorbereitungslauf READY; keine tatsächliche Umschaltung. Fehlende Bind-Mount-Ordner zuvor nachgewiesen; Anleitung/UI nennt jetzt vor Erststart beide leeren Ordner. Vorhandenes Ziel belegt; neuer Versuch braucht frisches getrenntes Ziel, kein blindes Löschen.
- Übernahme8 Runtime-Dateien aus server/: tbserver/recovery_version.py NEU, recovery.py, recovery_guard.py, system_admin.py, backup.py, devices.py, capabilities.py, templates/v1_system_status.html. API+Worker neu starten; StrgF5, Migration weiterhin21. Noch keine NAS-Übernahme dieser Änderung behauptet. Ablauf/Grenzen/Androidvertrag: server/checks/OPS_RESTORE_004.md.

- Nachweis:89 Verhaltenstests130–141 PASS; Python/JavaScript/YAML-Syntax PASS. Echter PostgreSQL17/FastAPI-Gate zweimal SUCCESS; letzter Run37524540497 Job112478085754: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37524540497/job/112478085754 . UPDATE und SECURITY jeweils echte Sicherung/Wiederherstellung/API-Neustart mit Portal-/Geräteprüfungen PASS; zusätzlicher Altzustand ohne mode bleibt SECURITY. Quellbestand erhalten, Zielfehler/Lock/Retry-Guards unverändert geprüft. 3x255Clients0Fehler,p95 6,13–6,16s inkl2sWait; keine NAS-/Android-/Foto-/BTB-Schreiblastabnahme.
- Gesamt-CI weiterhin nicht grün: foundation alter FILE_LINK_TARGET_NOT_FOUND-Marker, Android-CI alter Sourcevertrag. Nicht umgangen/abgeschaltet. Weitere Server-/Portal-Arbeit und Android-Recoveryintegration bleiben offen.


## OPS-RESTORE-005 – erster Implementierungsblock (07.10.2026)
Status: STEUERUNGS-/DATENBEREICHSBAUSTEINE IMPLEMENTIERT / GESAMTINTEGRATION OFFEN.
- Neue, noch NICHT an API/Worker angeschlossene Module recovery_control.py, backup_domains.py und backup_domain_snapshot.py. Bisherige produktive Backup-/Restorepfade und Compose-Dateien unverändert. Keine NAS-Übernahme für diesen Zwischenstand.
- Controller: privates persistentes Journal mit Datei-/Verzeichnis-fsync, exklusiver Prozesslock, getrennte Phasen für Wartung/Sicherheitskopie/Zielaufbau/Prüfung/Umschaltung/API-Probe und dauerhafte Commitentscheidung vor Schreibfreigabe. Vor Commit Rückweg, bei Rückwegfehler Wartung/NEEDS_ATTENTION; nach Commit kein automatischer Rückwechsel. Adaptervertrag beschrieben, echter Runtimeadapter noch NICHT implementiert.
- Datenbereichsplaner: vollständige explizite Tabellen-/Primärschlüsselliste für Migrationen001–021, GLOBAL/COMPANY-Zuordnung, baustellenbezogene track_data im Nutzerbereich, Dateilinks dem Fachziel zugeordnet; unbekannte Tabellen, unklare Historie und kollidierende Primärschlüssel blockieren. Kein stilles Nullsetzen historischer Autoren. Kombinieren bleibt reine Planung.
- Zusatzprüfungen: Fremdschlüssel einschließlich gefüllter nullable Quellen, bekannte Datei-/Netz-/Workspace-JSON-Referenzen; bereichsübergreifende Original-IDs gegen unveränderte SHA/Größe/Speicherpfad. Keine Behauptung vollständiger Fachvalidierung aller JSON-Inhalte.
- PostgreSQL-Leseadapter: eigene REPEATABLE READ/READ ONLY-Transaktion, tatsächliche Tabellen/PK/FK-Metadaten, begrenztes Planungsvolumen64MiB; Überschreitung expliziter Fehler. Noch kein Streaming-Archivschreiber und kein Zielimporter.
- Lokal tatsächlich ausgeführt: 142 zehn Tests PASS (echtes Journal-/Lock-I/O, echte Unterprozessabbrüche mit simuliertem Runtimeadapter, unsicherer Journal-Commit, Fehler-Rückweg, wiederholte Läufe, nach Freigabe angenommene Änderungen erhalten);143 vierzehn Tests PASS (Datenfixtures, Bereichstrennung, Querverweise, Originalidentität, JSON und Schema-Tabellenabgleich). Python-Kompilierung PASS.
- Neuer separater CI-Workflow server-recovery-control.yml und strikt auf leere CI-Testdatenbank tb_gate_domains begrenztes Tool backup_domain_gate.py. Echte PostgreSQL-Ausführung bis zum beobachteten CI-Ergebnis OFFEN. Vorhandene CI-Gates nicht entfernt oder abgeschaltet.
- Weiter nötig: Runtimeadapter/Containersteuerung und unabhängiger Dienst, vier echte Archivarten samt Zeitplan/Retention, Zielimport mit Historien-/Autor-/Sequenzbehandlung, vollständige Referenz-/Dateiprüfung, API/Adminoberfläche und Runtime-Positiv-/Fehler-/Abbruch-/Wiederholungstests. Aktuelle NAS-Compose für abschließenden Installationsabgleich weiter erforderlich. Android unverändert; dessen Recovery-Rebase-E2E bleibt offen.


### OPS-RESTORE-005 – tatsächlich ausgeführte PostgreSQL-CI (07.10.2026)
- Quellcommit: 96942b9f6db2a16aafc6f97569e971855ae16b54; PR6 bleibt Draft.
- Neuer Workflow Run37620515221, Job112789303061 SUCCESS mit ausgeführten Runner-Steps: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37620515221/job/112789303061 .
- Logs ausdrücklich geprüft: 142 zehn Tests und143 vierzehn Tests PASS; PostgreSQL17-Gate PASS für vollständigen Schema-/PK-/FK-Abgleich, Bereichszuordnung, fehlenden bereichsübergreifenden Quellenverweis, erhaltene Live-Testdaten sowie unbekannte Tabelle/Volumengrenze und Wiederholung nach Fehlern.
- Das ist ein echter PostgreSQL-Lese-/Planungsnachweis plus Dateijournal-/Prozessabbruchtest mit simuliertem Runtimeadapter. Kein echter neuer Archivrestore, kein automatischer Containerwechsel, kein Admin-/Browser-/NAS-/Android-E2E. Der bestehende separate Android-CI-Lauf37620515284 ist fehlgeschlagen; Ursache in diesem Block nicht erneut untersucht, keine pauschale Gesamt-CI-Freigabe.
- Gesamtauftrag OPS-RESTORE-005 bleibt OFFEN: Bausteine noch nicht in produktive API/Worker-/Composepfade integriert. Noch keine neue Upload- oder Löschliste für die NAS freigegeben; vorhandene DB-/Storage-/Backup-/Restorezielordner behalten.


### OPS-RESTORE-005 – Archiv und isolierter Zielimport implementiert (07.10.2026)
- Neue Module scoped_backup.py/scoped_restore.py: getrennte USER-/TRACK-Archive mit Datenbereich, referenzierten verifizierten Dateien, Runtime-Metadaten und minimalen externen Originalabhängigkeiten; Veröffentlichung ohne Überschreiben vorhandener Archive. Leser prüft Member, Grenzen, Hashes, Pfade und Bereichszugehörigkeit.
- PostgreSQL-Snapshot trägt Sequenzstände und kann ein Schema aus demselben exportierten Snapshot sichern. Zielimport nur separate frische DB/leerer getrennter Speicher; Quellen-/JSON-/FK-Vorprüfung, Pre-Data-Schema, vollständige Zeilen, echte Post-Data-Constraints, Sequenzfortsetzung, Zeilen-/Dateiverifikation. Fehlerziel bleibt bestehen; keine Aktivierung aus diesem Modul.
- Lokal ausgeführt: 142 zehn,143 vierzehn und144 neun Tests PASS; Python-Syntax PASS. Neuer echter PostgreSQL17-Archivrestore-Gate mit USER/TRACK, erhaltenem Gegenbereich/Betriebsdaten/Dateien, Sequenzen, belegtem Ziel, Quellenidentitätskonflikt, Unique-Constraint-Fehler, fehlender Datei und Wiederholung. CI-Ausführung zum Commitzeitpunkt noch OFFEN.
- Gesamtintegration weiter OFFEN: Runtime-/Containeradapter, Sicherheitskopie unter tatsächlicher Wartung, FULL-Einbindung, vier unabhängige Archivarten/Zeitpläne/Aufbewahrung, Adminoberfläche, reale automatische Umschaltung samt Fehler-/Abbruchtests. Keine NAS-Upload-/Löschfreigabe für diesen Zwischenstand; Android unverändert.


### OPS-RESTORE-005 – echter Teilarchiv-Restore nachgewiesen (07.10.2026)
- Implementierung562402979d49dc1e1febab83a18ffa86b3de9583. Run37665361944/Job112943078030 SUCCESS, Runner-Steps und Logs gelesen: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37665361944/job/112943078030 .
- Echte PostgreSQL17-USER/TRACK-Restores mit pg_dump/pg_restore, erhaltenem Gegenbereich/Betriebsdaten/Dateien und weiterlaufenden Sequenzen PASS. Belegtes Ziel, veränderte Originalidentität, echte Unique-Verletzung beim Post-Data-Import und fehlende Datei blockieren; erneuter Restore in frisches Ziel PASS; Quell-DB unverändert. 33 lokale/CI-Verhaltenstests142–144 PASS.
- Noch keine automatische Live-/Containerumschaltung und keine NAS-Freigabe. Separater Android-CI-Lauf37665361959 fehlgeschlagen, hier nicht erneut diagnostiziert.


### OPS-RESTORE-005 – automatischer Betriebsadapter implementiert, Laufzeittest ausstehend (07.10.2026)
- recovery_runtime.py überwacht feste API-/Worker-/Monitor-Prozessgruppen ohne Docker-Schreibzugriff; persistente Generation wählt DB/Storage/Recovery-ID gemeinsam. STOPPED/PROBE/OPEN, rollengetrennte Bestätigungen, fehlende/ungültige Auswahl sperrt. ASGI-Wartungsgate in main.py nur bei konfiguriertem neuen Betriebsaufbau aktiv; Legacy ohne Steuerungsverzeichnis bleibt unverändert.
- recovery_backend.py und recovery_data.py verbinden Controller mit gestoppten Schreibern/geleerten DB-Sitzungen, privater zusätzlicher FULL-Sicherheitskopie ohne Backupregistrierung/Retentionkopplung, frischem USER/TRACK/FULL-Ziel, Zugangs-/Jobbereinigung, Daten-/Dateiprüfung, realer API-Probe, durablem Commit und Freigabe bzw. Rückweg. Kein Datenbank-/Storage-/Container-Löschen. Controller-Auftragseingang/API/Oberfläche/Compose noch nicht integriert.
- Migration022 erlaubt neue USER/TRACK/SYSTEM-Arten zusätzlich zu alten Datensätzen. run_backup kann USER/TRACK-Archive tatsächlich registrieren; SYSTEM-Erstellung und neue Aufbewahrung/Zeitpläne noch offen. FULL-Publikation jetzt fsync und ohne Überschreiben; Sicherheitskopie direkt im geschützten Operationsordner.
- Lokal145 fünf Tests PASS mit echten Supervisor-/Kindprozessen und ASGI-Aufrufen: Pause, API-only-Probe, gesamtes DB/Storage/ID-Paar, Restart auf Ziel, korrupt/fehlend fail-closed, Fachzugriff/WebSocket gesperrt.144 neun und143 vierzehn Tests weiterhin PASS, Syntax PASS. Neuer echter PostgreSQL/API/Worker/Monitor-Gate für USER/TRACK/FULL-Umschaltung und Fehler-Rückweg angelegt; CI zu diesem Commit noch OFFEN.
- Weiter kein NAS-fertiges Paket. Insbesondere Auftragseingang, unabhängige Aufbewahrung, Systemimagepfad, Compose-Übernahme, neue Adminbedienung und Crash-Nachweis mit echtem Gesamtadapter offen; Android-Rebase separat unverändert.


### OPS-RESTORE-005 – Wiederholungsfehler korrigiert, erweiterter Realtest (07.10.2026)
- a29a72f-CI: erster automatischer USER-Restore tatsächlich PASS; nachfolgender TRACK korrekt zurückgerollt wegen zu spät gesetzter Snapshot-Isolation in Sicherheitskopie. Keine Gesamtfreigabe aus erstem Lauf.
- _capture_full_database prüft Recovery-ID nun im Autocommit und startet danach eigene REPEATABLE READ/READ ONLY-Snapshottransaktion. Weitere Guardprüfung bleibt erhalten. Nicht geheime Datenaktions-Fehlercodes werden privat zur Diagnose gespeichert.
- Echter CI-Gate erweitert: Fehler nach realer Zielprobe für USER/TRACK/FULL; neue Backups auf bereits restauriertem Stand; harte Controller-Prozessabbrüche nach Zielauswahl und nach Schreibfreigabe, einschließlich Erhalt einer danach angenommenen Änderung. Ausführung zum Fixcommit noch OFFEN.


### OPS-RESTORE-005 – Übernahme, Snapshot-Test und unabhängiger Archivkatalog (09.10.2026)
- Ausgang8e4e337f geprüft; Übernahme da599dce, Snapshot-Testfix38aaf088. Testdouble prüft autocommit-Guard vor expliziter Snapshottransaktion sowie deren Lebensdauer während pg_dump; 9 Tests lokal PASS. Produktionsguard unverändert.
- Neuer backup_catalog.py: dauerhafte getrennte USER/TRACK/FULL/SYSTEM-Metadaten im Backupverzeichnis, verifizierte Dateien/Hashes, Dateilock, atomare fsync-Publikation. Nur neue erfolgreiche Sicherung derselben Art löst Aufbewahrung vier aus; Import/Wiederholung/Fehler löschen nicht. Löschfehler behalten Eintrag/Datei, .recovery/Sicherheitskopien und nicht registrierte Archive ausgeschlossen. SYSTEM-Erzeuger weiterhin offen.
- run_backup registriert neue Datenarchive; Lookup nutzt Katalog vor restaurierter DB und importiert alte erfolgreiche Einträge ohne Löschen. Alter zeitbasierter Retentionpfad nur noch DATABASE/FILES. Restore kopiert ausgewählte Bytes unter gemeinsamem Kataloglock in geschützten Operationsordner. Katalogpublikationsfehler erhalten erfolgreiches Archiv/DB-Protokoll und werden im Ergebnis gemeldet.
- Tatsächlich lokal: 11 Backup-Sicherheits-,9 Snapshot-,10 Katalogtests PASS. Echter Integrations-CI für diese Änderung noch OFFEN; Baseline8e4e337f hatte Run37669109233/Job112955922326 mit echter USER/TRACK/FULL-Umschaltung, Rückweg und Prozessabbrüchen PASS. Kein NAS-/Browser-/Android-E2E.
- Gesamtauftrag weiter OFFEN: unabhängiger Dienst/Auftragseingang, UI/Zeitpläne, Systembackup und Betriebskonfiguration noch integrieren; Gesamt-CI weiterhin prüfen. Keine NAS-Upload-/Löschfreigabe, Android unverändert.


### OPS-RESTORE-005 – Betriebsintegration im Quellstand (09.10.2026)
- Katalog18c46765 tatsächlich im CI bestanden: Run37917187967/Job113776148334, Runnersteps/Logs gelesen, 48 Tests plus PostgreSQL17-Teilarchive und automatische Umschaltung/Rückweg/Abbrüche PASS. Zweiter Snapshot-Testadapter742aa9ce korrigiert; Gesamt-CI separat prüfen.
- Neuer separater recovery_service: dauerhafter IDs-only-Eingang, wiederholbare Request-ID ohne erneuten Restore nach Commit, eigener begrenzter GET-Fortschritt während API-Pause, Admin+CSRF/Bestätigung an neuer Execute-API. USER/TRACK/FULL über bestehende Controllerbausteine; SYSTEM nicht einspielbar. UI zeigt Bereiche, letzte4 und Datum/Verlusthinweis. Alte Vorbereitung bei konfiguriertem neuen Dienst blockiert, Legacy bleibt für alte Deployments erhalten.
- Vier unabhängige Zeitpläne USER/TRACK/FULL/SYSTEM; bestehende DATABASE/FILES-Zeiten/Deaktivierung nach USER/TRACK übernommen, SYSTEM initial aus. Neuer begrenzter system_backup-Exporter speichert Images samt Tags/IDs, Quellstand und Manifest, ohne .env/Fachdaten/Vollbackupkopplung. Nur Exportdienst hat Docker-Socket (GET-only im Code), eigenes internes Netz mit Worker. Docker-Socket-ro ist keine eigene API-Rechtebegrenzung.
- Opt-in docker-compose.synology-automatic.v1.yml + recovery-gateway.conf: separates Journal/Auswahl/Eingang und rollengetrennte Acks, API/Worker/Monitor-Supervision, Initialisierung ohne Rückfall bei verlorener Auswahl, Migration auf gewählter Generation. Keine laufende NAS-Konfiguration geändert. Cloudflare-Origin muss bei Übernahme gateway:8080 erreichen. Individuelle NAS-Konfiguration vor Installation noch abzugleichen.
- Lokal: Syntax geänderter Pythonmodule/JS und 6 Queue-,4 Systemarchiv-,2 Deployment/Policytests PASS. Echter neuer HTTP-Service-/Dockerexport-CI ist zu diesem Commit OFFEN. Erweitertes Gate prüft reale Admin/CSRF/Bestätigung/Doppelaufträge, unabhängigen Service/Fortschritt und weiterhin Umschaltung/Fehler/Abbrüche. Keine Quellstand-/NAS-Gesamtfreigabe vor diesen Ergebnissen; Android-Rebase weiterhin offen.


### OPS-RESTORE-005 – HTTP-/Browserkorrekturen und Nachweise (09.10.2026)
- 7fefd204 erhält im Katalog-Lookup den bestehenden UUID/Datetime-Vertrag; Regressionstest liest erfolgreich ohne Datenbankzugriff. Folgerestore-Test wartet auf echte API-Bereitschaft statt direkt nach Supervisorstart zuzugreifen. Unabhängige Sicherungsliste im Systemstatus und Auswahl bei Aktualisierung erhalten.704fc413 ergänzt sichere UUID-Erzeugung auch für NAS-LAN-HTTP.
- Lokal81 Verhaltenstests131/137/142–149 PASS, Python-Syntax PASS. Realer recovery-core Run37921144828/Job113789088886 SUCCESS, Runnersteps/Logs gelesen: USER/TRACK/FULL jeweils Admin/CSRF/Bestätigung/Doppelauftrag, unabhängiger Fortschritt, Sicherheitskopie/Zielprüfung/Umschaltung PASS; echter Rückweg nach Zielprobe aller3Arten, Supervisorneustart und harte Controllerabbrüche vor/nach Freigabe mit Erhalt neuer Schreibzugriffe PASS. Systemimage-Export/Load und Teilarchive ebenfalls PASS.
- Run37921144851/Job113789088892: realer Snapshot, HTTP-Rechtewiderruf,3x255Longpoll-Clients ohne Fehler (p95~5,95–6,04s inklWait), Legacy-UPDATE/SECURITY-Restore PASS. Neuester entsprechender Job113789810053 ebenfalls SUCCESS. Kein NAS-/Foto-/BTB-Schreiblastnachweis.
- Chromium-UI-Fixture Run37921366147/Job113789809902 SUCCESS mit ausgeführten Browsersteps: Bereichswahl, Bestätigung, alte Vorbereitung ausgeblendet, Einspielen, separater Fortschritt, Doppelauftragform gesperrt;390px-Ansicht ohne horizontalen Überlauf laut DOM-Messung. Antwortfixtures, kein Browser-Backend-E2E. Screenshotdownload in dieser Umgebung403, daher keine visuelle Screenshot-Abnahme behauptet.
- Zusätzlicher Compose-Gesamttest im selben Run noch laufend; abschließendes Ergebnis nachtragen. Monitor erkennt jetzt auch BACKUP_USER/TRACK/SYSTEM-Fehler. Vorbereitete NAS-Dateiliste/Betriebsablauf: server/checks/OPS_RESTORE_005_INSTALL.md. Aktive individuelle Compose/Tunnel-Origin für Übernahme noch erforderlich; keine NAS-Änderung, Löschliste leer.
- Excel-Aktualisierung blockiert: Repositorydatei TB-HELFER_STATUS.xlsx ist binär ungültig (14998Bytes, Git-Blob a56c44ccbd85ebe1499a27c4777bc77409eb9479 stimmt exakt; BadZipFile/FileContainsCorruptedData). Archivkopie identisch. Bestehende Datei erhalten; keine erfundene Ersatzhistorie.
- Gesamt-CI nicht grün: alter foundation-Quellmarker FILE_LINK_TARGET_NOT_FOUND und separater Android-Sourcevertrag weiterhin rot. Android app/** unverändert, Recovery-ID/Outbox-Rebase und NAS-/Cloudflare-Abnahme weiterhin separat offen. Gesamtauftrag nicht als NAS-fertig markieren.


### OPS-RESTORE-005 – abschließender isolierter Integrationsnachweis (09.10.2026)
**Quellstand für Tests: d8d28e2a9dd5fad6c271b345abc9f2dd789d29bf. SERVER-/BACKUP-INTEGRATION IMPLEMENTIERT UND ISOLIERT GEPRÜFT; NAS-ÜBERNAHME/ANDROID-ABNAHME WEITER OFFEN.**
- Restoreworkflow Run37922416404 vollständig SUCCESS. Job113793264141 und Browserjob113793263878: tatsächliche Runnersteps und vollständige maßgebliche Logs gelesen. https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37922416404 .
- Echter Docker/PostgreSQL17-Nachweis: vier unabhängige Backup-Erzeuger; Systemimage-Export/Load; USER/TRACK/FULL-Aufträge mit Admin/CSRF/Bestätigung und Doppelauftragsschutz; Sicherheitskopie, Zielprüfung, automatische Umschaltung und unabhängige Fortschrittsanzeige. Absichtliche Fehler nach jeder Zielprobe aktivieren bisherigen Stand; Controllerabbrüche vor/nach Freigabe halten dauerhafte Entscheidung ein und erhalten danach angenommene Änderungen.
- Tatsächliche Compose-Topologie mit separaten Containern und Gateway: Initialisierung, Migrationen001–022, alle4Erzeuger,3Restorearten über HTTP, Fortschritt und anschließender Containerneustart auf gewählter Generation PASS. nginx-Regexquotierung5d13793c korrigiert. Abschließender CI-Abbau nach Rückgabe der ausschließlich temporären Testdateirechte ebenfalls PASS; keine NAS-/Produktionsrechte geändert.
- Chromium-Browserfixture PASS: Auswahl/Bestätigung/Einspielen, Legacyform ausgeblendet, Fortschritt, Doppelauftragform gesperrt, mobile390px ohne gemessenen horizontalen Überlauf. Fixtureantworten, daher kein vollständiger Browser-Backend-/NAS-E2E. Vorheriger Bilddownload403; keine visuelle Screenshot-Abnahme behauptet.
- Serverworkflow Run37922416461: Job113793264588 Snapshot/Rechte/255Client-HTTP/Legacy-Restore SUCCESS, Logs geprüft. Gesamtprojekt-CI bleibt wegen unabhängigem altem foundation-Quellmarker und Android-Sourcevertrag rot; keine Tests abgeschaltet. Android app/** unverändert.
- Konkrete vorbereitete Übernahmeliste: server/checks/OPS_RESTORE_005_INSTALL.md. Ersetzen/ergänzen der Software aus einem Commit; neue persistente Recoveryordner; Gateway als tatsächlicher Tunnel-Origin. **Löschliste leer.** .env/DB/Storage/Backups/Zielbestände bleiben erhalten. Aktive NAS-Compose (ohne Geheimwerte) vor praktischer Übernahme noch mit Vorlage abgleichen. NAS-Installation, Systemimage-Wiederaufbau auf Synology und Android-Recovery-Rebase nicht bestätigt.
- MD-Steuerung aktuell; bestehende Exceldatei bleibt wegen nachgewiesener binärer Beschädigung unverändert/blockiert (Blob a56c44ccbd85ebe1499a27c4777bc77409eb9479). Intakte Vorlage erforderlich, keine historische Tabelle erfunden.


### OPS-RESTORE-005 – hochgeladene NAS-Compose abgeglichen und isoliert geprüft (09.10.2026, Abend)
- Geprüfter Quellstand: 9b1d848ede7f5aeb5a7c155a887a04ab588beca9. Angepasste Datei: server/compose.synology-reviewed.v1.yml, auf NAS später compose.yml. Originalupload bleibt unverändert; separate vorbereitete Datei wird zurückgegeben. Kein NAS-Zugriff, keine Installation, keine Löschung.
- Vorhandene db/restore-test-db/restore-target-db/cloudflared-Definitionen unverändert; Datenpfadvariablen, API-Speicherlimit und veröffentlichte Portbindung erhalten. Automatischer Worker nutzt neue Generationsziele; alte manuelle Worker-Zielparameter entfallen, alte Zielbestände bleiben bestehen. Systemexport bindet aktive compose.yml unter kanonischem Archivnamen.
- Restoreworkflow37965208092 SUCCESS: Corejob113937722251 und Browserjob113937722449, ausgeführte Schritte/Logs gelesen. https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37965208092
- Beide tatsächlichen Compose-Varianten PASS: Initialisierung/Migrationen, vier Backup-Erzeuger, USER/TRACK/FULL über Gateway mit unabhängigem Fortschritt und Neustart auf gewählter Generation. Zusatzvariante: Marker in alter Zieldatenbank unverändert, archivierte aktive Compose bytegleich. Tests ausschließlich in getrennten temporären CI-Projekten mit Testdaten.
- Reale PostgreSQL17-/Controller-Abbruch-/Fehlerrückweg-/Image-Save-Load-Tests erneut PASS. Browserfixture PASS einschließlich390px ohne gemessenen horizontalen Überlauf; weiterhin kein vollständiger Browser-Backend-/NAS-E2E.
- Serverworkflow37965208067: Snapshot/Rechte/3x255Clients/Legacy-Restore-Job113937721382 SUCCESS (Logs gelesen). Alter foundation-Quellmarker FILE_LINK_TARGET_NOT_FOUND weiterhin rot; Android-Vertragsgate ebenfalls weiterhin rot. Keine Behauptung einer vollständig grünen Gesamt-CI.
- Konkrete Übernahme: server/checks/OPS_RESTORE_005_NAS_COMPOSE.md. Bestehendes Projekt zuerst stoppen, dann gebundene Softwaredateien aus demselben geprüften Stand hochladen; ursprüngliche Compose separat behalten. Löschliste leer. Allgemeine Anleitung verlinkt die konkrete Variante.
- Nächster benötigter Input: tatsächliche Cloudflare-Service-/Origin-Ziele für www.tb-helfer.de und api-v1.tb-helfer.de (Screenshot/Text ohne Tokens/.env). Tokenverwaltete Compose enthält diese Ziele nicht. Vor diesem Abgleich kein Projektneustart. NAS-/Cloudflare-/Android-Recovery-ID/Outbox-Abnahme bleiben offen.
- Excel weiterhin wegen bereits nachgewiesener beschädigter Repositorydatei blockiert; unverändert erhalten, MD-Status fortgeführt.


### OPS-RESTORE-005 – Cloudflare-Screenshot und Freigabe zur Fertigstellung (09.10.2026, 19:54 Berlin)
- Nutzer liefert Screenshots: Tunnel tb-helfer-nas Status Fehlerfrei; www.tb-helfer.de und api-v1.tb-helfer.de jeweils Pfad * und Service http://192.168.178.3:8081. admin.tb-helfer.de (*), api.tb-helfer.de (^/api/) zeigen jeweils http://tb-helfer-api:8080. Legacy-Routen nicht ändern/löschen.
- Nutzerauftrag „bauen“: Übernahmedokumentation mit diesen bestätigten Zieladressen abschließen. Vorhandene getestete Compose übernimmt den identischen veröffentlichten Port vom API-Dienst auf Gateway; keine weitere Änderung der Cloudflare-Ziele erforderlich, sofern die bisherige wirksame NAS-Portbindung unverändert übernommen wird.
- Präzisierung zur vorherigen Chatantwort: Screenshot bestätigt die konfigurierten Ziele und den Tunnelstatus, nicht die Erreichbarkeit des neuen Gateways. TB_V1_API_BIND ist ohne vorhandenen Override standardmäßig127.0.0.1; für Zugriff auf192.168.178.3 muss die wirksame Bindung diese NAS-Adresse bedienen (z.B. bestehend192.168.178.3 oder0.0.0.0). Nicht blind Defaults verwenden, .env nicht hochladen/überschreiben. Laufende Portbindung vor Stopp im Container Manager ablesen.
- Keine Runtime-Codeänderung nötig. Getesteter Quellstand9b1d848e bleibt maßgeblich. NAS-/Cloudflare-Livetest und Android-Abnahme weiterhin offen. Keine NAS-Aktion, Löschung oder automatischer Tunnelupdate.

- Übernahmeanleitung server/checks/OPS_RESTORE_005_NAS_COMPOSE.md mit Screenshot-Zielen, Portbindungsprüfung vor Stopp und lokalen/öffentlichen Health-Prüfungen nach Start aktualisiert. Compose/Runtime unverändert gegenüber getestetem Quellstand9b1d848e; kein erneuter Lauf für reine Dokumentationsänderung. Noch keine NAS-Übernahme behauptet.


### OPS-RESTORE-005 – NAS-Portbindung bestätigt (09.10.2026, 20:18 Berlin)
- Nutzer bestätigt ausdrücklich TB_V1_API_BIND=0.0.0.0; TB_V1_API_PORT=8081 im Screenshot vollständig sichtbar. Container Manager zeigt API-Container8080/tcp auf Host8081,512MB und Projektordner /volume1/docker/tb-helfer-v1.
- Damit ist der ausstehende Konfigurationsabgleich geschlossen: vorhandene .env unverändert übernehmen, Cloudflare www/api-v1 weiter auf http://192.168.178.3:8081; neue Compose veröffentlicht diesen Port am Gateway. Keine weitere Rückfrage zum Bind-Wert.
- Nutzer „ja genau, bauen“: konkrete Datei-/Ordnerliste zur Übernahme bereitstellen. Runtime und Compose bereits im Quellstand9b1d848e gebaut und isoliert erfolgreich getestet (Restoreworkflow37965208092); jetzt ausschließlich Dokumentationsabschluss, keine erneute Codeänderung nötig.
- NAS-Installation weiterhin nicht ausgeführt. Vor Upload verifizierte Sicherung und alte Compose behalten, Projekt stoppen; erst dann zusammengehörige Software übernehmen und neu bauen. Nichts löschen. Erster Start/Health/Portal/Backup-Nachweis auf NAS offen, Datenrestore erst im vereinbarten Testfenster. Android-Recovery-ID/Outbox weiterhin separat offen.


## OPS-RESTORE-006 – NAS-Test und Folgekorrektur (09.10.2026, 21:58 Berlin)
- Nutzer hat geprüfte Compose installiert. Vier Backuparten erfolgreich laut NAS-Oberfläche; FULL 21:01:51 / 826 MB geprüft, SYSTEM 21:19:17 / 1,1 GB.
- Automatischer FULL-Restore meldet 21:37 Erfolg; erneute Anmeldung, 22/22 Migrationen und erreichbare Dienste nachgewiesen. Nutzer bestätigt 21:54 Testfirma, Baustellen, Bautagebuch und Bilder zugänglich. USER/TRACK-NAS-Restore und Android-Recovery bleiben offen.
- Diagnoseupload diagnostics-v1(1).zip: monitor-state 21:49:54 zeigt BACKUP_FULL FAILED / CANCELLED_AFTER_RECOVERY als einzigen aktuellen ERROR, interne/öffentliche API, DB und Worker OK. Vermutung: eigener laufender Backupauftrag im Snapshot wird beim Restore storniert. Codeprüfung und korrekte neutrale Darstellung erforderlich, echte Fehler dürfen nicht verborgen werden.
- Öffentliche API-Timeouts bestehen vor Restore (375 Ereignisse am 09.10. vor 21:35); Ursache nicht als Cloudflarefehler bewiesen. Codepfad/Timeoutdiagnose prüfen; keine blinde Timeout-Erhöhung oder NAS-Neuinstallation.
- Nutzerauftrag 21:58: beide Punkte untersuchen/korrigieren, Tests und Dokumentation. Keine NAS-/Cloudflare-/Androidänderung in diesem Block.


### OPS-RESTORE-006 – Korrektur und Diagnoseergänzung
- Ursache source-bestätigt: recovery._seal_target beendet restaurierte PENDING/RUNNING-Jobs mit FAILED/CANCELLED_AFTER_RECOVERY. DB-Status bleibt sicher terminal; Oberfläche zeigt diesen exakten Sonderfall neutral als durch Wiederherstellung beendet. Monitor überspringt ausschließlich diesen Sonderfall und bewertet weiterhin echte Fehler einschließlich NULL-last_error.
- Öffentliche HTTP-Diagnose ergänzt failure_phase (connect_or_headers/response_body), Timeoutbudget sowie zeitnahen internen API-/DB-Status. Keine Änderung an Timeout, Alarmgrenzen, Cloudflare oder NAS. Netzwerkursache weiterhin offen; ZIP enthält keine Gateway-/Tunnel-Requestlogs.
- Drei lokale Regressionstests PASS: UI-Abgrenzung, tatsächliche Monitor-SQL gegen SQLite-Testdaten, beide HTTP-Timeoutphasen. Keine vollständige PostgreSQL/Compose-CI oder NAS-Installation dieser Korrektur behauptet.
- NAS-Übernahme: nur tbserver/monitor.py, tbserver/system_admin.py, templates/v1_system_status.html; API und Monitor anschließend über Container Manager neu starten. Keine Compose/.env/Daten-/Backupänderung. Danach neutralen alten Job und Systemstatus prüfen; bei fortbestehendem Timeout neue Diagnose erfassen.


## Aktueller Vorrang – NAS-Abnahme und Fortsetzung (11.10.2026, 15:38 Berlin)
- BESTÄTIGT durch Nutzer: „backup funktionieren alle“, auf Rückfrage einschließlich Einspielen „ja alles funktioniert“. Backup-Praxisblock damit nutzerseitig abgenommen; keine Wiederholung der Backup-/Restoretests verlangen. Dies ersetzt die älteren offenen USER/TRACK-NAS-Abnahmehinweise. Kein neuer unabhängiger Systemimage-Wiederaufbau- oder Android-Recovery-Nachweis daraus ableiten.
- SERVER-Ausgangsstand600d070cf94f35d5ed28ada68d2c163a97501e55, PR6 offen/Draft; Android423f37892915cb3308a4b1ea9e4d196faa872326, PR11 offen/kein Draft. Android bleibt nach Server/Portal.
- Diagnoseauswertung11.10.: monitor-state15:14:33 Berlin interne API42,2ms, DB17,4ms, Workerheartbeat6,1s, BackupüberwachungOK (RetentionDONE), öffentlichWARN1708ms/HTTP200. Neue HTTP-Phasendiagnose aktiv. Keine neuen Gateway-Verbindungsfehler im Export nach9.10.22:05; alte Worker-/DB-Startfehler nicht als neue Störung werten.
- Öffentlich weiter offen: Ereigniszählung nach UTC-Kalendertag10.10.104 PUBLIC_API_ERROR,11.10.bisExport6; keine Ausfallquote aus Übergangsereignissen ableiten. 24erVergleich: intern0Fehler/max128ms, NASGateway0/max321ms, öffentlich1Timeout/max5040ms und5langsam. Laptop3/11Seiten leer; HandyMobilfunk14/14 erfolgreich, zeitlich getrennt. Ursache FRITZ!Box/Switch/Provider/Cloudflare nicht nachgewiesen.
- Nutzer kann großes API-Protokoll wegen NAS-Belastung nicht exportieren; nicht erneut anfordern. Netzwerkursachensuche auf Nutzerwunsch zurückgestellt, kein Timeout-/Tunnelumbau auf Verdacht.
- Nächster fachlicher Block gemäß bestehendem Plan: Portal-BTB/Unterbaustellen, Einträge und Fotos umhängen, Wetter und Bildgrößen. Bereits implementierte Rechte-/Last-CI nicht doppelt bauen; deren NAS-/Geräteabnahme bleibt separat. Zuerst bestehende Portalabläufe prüfen, bestätigte Funktionen schützen. Keine NAS-Datenmutation in diesem Dokumentationsblock.


### PORTAL-005 – geprüft und zur NAS-Übernahme vorbereitet (11.10.2026)
- Programmstand424b5d4701b603e35c51b38d5b32b55f707cf2c7. Portal-Neustrukturierung, Einstellungen/Kontowiederherstellung, öffentliche Aggregate inkl. Testbaustellen und Helfersperre implementiert. Migration023; neue Kontotabellen in Backup-Domänen erfasst, offene Kontolinks nach Restore entwertet.
- Echte Portal-CI38153731624 PASS: PostgreSQL/PostGIS17, Migration und Wiederholung, Passwort/E-Mail/Session-/Adminpfade, echte Helferentfernung/Rechte/Receipt-Replay, historischer Teilnehmerbestand, Schema-/Backupvertrag, Jinja; Chromium1440/390 mit simulierten Fach-API-Antworten PASS. Screenshots visuell geprüft. Kein NAS-E2E/Mailversand daraus ableiten.
- Snapshot/Rechte/255-Client-HTTP-Job38153731635 PASS. Restoreworkflow38153279846 beide Jobs PASS mit neuem Backupvertrag; spätere Änderungen nur Portalvorlagen/Browsercheck. Gesamtsuite nicht vollständig grün: Foundation stoppt an unverändertem FILE_LINK_TARGET_NOT_FOUND-Vertragsmarker, Android-CI ebenfalls nicht bestanden.
- Sichere Übernahme und genaue Ordnerliste: [PORTAL-005_NAS_UEBERNAHME.md](uebergaben/PORTAL-005_NAS_UEBERNAHME.md). Noch keine NAS-Installation und keine echte SMTP-Abnahme. Bereits bestätigte Backup-Praxisabnahme bleibt geschützt.
- Androidbranch423f37892915cb3308a4b1ea9e4d196faa872326/PR11 unverändert. ZIP-Original-/Kleinbildregel geschützt. PR6 offen/Draft; keine Merge-/Produktivfreigabe behauptet.

### PORTAL-006 – Bestandszahl korrigiert und geprüft (11.10.2026)
- NAS-Screenshots belegen PORTAL-005 sichtbar und Migration23/23/SystemOK. Kein umfassender Fach-/Mail-E2E daraus abgeleitet.
- Runtime bcee8df79a6c657f40f41ef705a464fe77970569: öffentliche Zahl verwendet dieselbe globale _coverage_summary_data wie Admin, projiziert ausschließlich Kilometer/Bahnhöfe/Strecken und separat Baustellenzahl. Keine sparsamen km_from_m/km_to_m-Intervalle mehr. Anzeige „Kilometer Trassenbestand“, zwei Nachkommastellen und Erklärung paralleler Gleise. Gleichbleibender Screenshotbestand sollte195,52km statt0,8km ergeben; realer NAS-Wert nach Installation noch offen.
- Portalworkflow38177184786 mit echten Steps PASS: Migration/PostgreSQL/API/Jinja und Chromium1440/390. Neuer Regressionstest:195520m→195,52km,4Stationen/9Strecken/1Testbaustelle, keine privaten Zusatzfelder und schreibgeschützter Rückgabecache; bestehende Tests bleiben grün.
- Übernahme nur server/tbserver/public_overview.py→tbserver/public_overview.py und server/templates/v1_public_home.html→templates/v1_public_home.html unter /volume1/docker/tb-helfer-v1. API über Container Manager stoppen, beide Dateien per File Station ersetzen, API starten, StrgF5. Migration23/23 unverändert; keine Reindexierung, kein Daten-/Backup-/Android-/ZIP-Umbau. Keine vollständige grüne Alt-Gesamtsuite behauptet.


### PORTAL-007 – öffentliche Zusatzkennzahl umgesetzt
- Nutzer bestätigt öffentliche195,52km per Screenshot; fordert zusätzliche Kachel123,98km mit belastbarer KM und Entfernung des Erklärungstextes. Admin bleibt unverändert.
- Runtime 3146c4dff297a26d68afdbab9a8c86e58f5b1824: dynamische Projektion known_km_length_m/1000, zwei Nachkommastellen, fünfte responsive Kachel, Erklärung entfernt. Testbaustellenregel erhalten.
- Portal-CI38178439695/Job114585681360 SUCCESS mit tatsächlich ausgeführten Integrations- und Chromium1440/390-Schritten. Bestehender Aggregat-/Datenschutztest um123,98 ergänzt. Keine komplette grüne Gesamt-CI behauptet.
- NAS-Übernahme dieser Änderung noch offen; Anleitung projektsteuerung/uebergaben/PORTAL-007_NAS_UEBERNAHME.md. Nur zwei Runtime-Dateien, keine Migration; Android/ZIP/Backup unverändert. PR6 bleibt Draft.


### PORTAL-007 – Nutzerbestätigung (12.10.2026, 00:03 Berlin)
- Nutzer bestätigt die öffentliche Seite mit „passt“. Änderung als nutzerseitig abgenommen festgehalten; keine unabhängige NAS-Installationsprüfung durch Agent behauptet.
- Bestätigte Darstellung schützen. Keine weiteren Runtime-Änderungen in diesem Bestätigungsblock.


### PORTAL-009 – begrenzte Galerie und Fotos auf Abruf (12.10.2026 Berlin)
- Getesteter Stand 06d223581c905ec72cc47581868513923beb9bd5; Runtime seit3ef1c5c7057c81274e235af5b109c895793792be unverändert, danach Browsercheck ergänzt/korrigiert. PR6 bleibt Draft.
- Neue separate GET /api/v1/worksites/{id}/media/page: 60 Standard/max100, Keysetcursor mit Filter-/Nutzerbindung, Berliner Aufnahmedatum, Art, Suchtext und optionale parent_id. Aktuelle Baustellen-/Team-/Bereichsrechte, deduplizierte Fotos, max960 Kandidaten pro Request, keine N+1-Rechteabfragen. Alter vollständiger /media-Vertrag unverändert.
- Galerie ersetzt Seiten statt anzuhängen (max60 Kacheln), Lazy-Thumbs, Preview, Suchabbruch/Generationsschutz, Live-Hinweis statt Seitensprung. Baustellendetails laden keine vollständige Medienliste mehr; je Mangel/Abnahme auf Klick max12 Vorschauen plus Link zur gefilterten Galerie. Uploadbedienung erhalten.
- Portal-CI38181633106/Job114595251272 SUCCESS, tatsächliche Steps148/150/149 ausgeführt. PostgreSQL10.000 Metadatensätze über167 Seiten ohne Lücke/Duplikat; erste Seite27701Bytes,2SQL-Abfragen,0,0579s, Gesamttraversierung4,447s auf CI. Tests für Mehrfachlinks, Filter/parent_id, Datum, Cursor, entzogenes Recht/Mitgliedschaft, fremdes Team/Firma, sparse Scanlimit. Keine NAS-Performancegarantie oder10.000 echte Bildtransfers daraus ableiten.
- Chromium1440/390: Seitennavigation, Suche bisEintrag9999, Live-Hinweis ohne Reload, max60 DOM-Kacheln, kein Originalabruf, Baustelle ohne eager Medienrequest,12 Bilder auf Klick und korrekter Fachgalerie-Link. Galerie-Screenshots visuell geprüft (vorangegangener erfolgreicher Lauf38181435938, identische Runtime).
- Snapshot/Rechte/255-Client-Gate38181469539 Job114594754607 PASS. Gesamt-CI weiterhin nicht vollständig grün: bestehender Foundation-Marker FILE_LINK_TARGET_NOT_FOUND und Android-CI offen. Keine Merge-/Produktivfreigabe behauptet.
- Übernahme: [PORTAL-009_NAS_UEBERNAHME.md](uebergaben/PORTAL-009_NAS_UEBERNAHME.md), vier Runtime-Dateien, keine Migration. NAS-Installation/Praxistest offen. Öffentliche Seite und bestätigte Backups schützen; keine app/**-Änderungen.
- Folgearbeiten: Foto-ID/Originalzuordnung/Manifest für ZIP und Excel im Android-Folgeauftrag; kein fertiger Originalabgleich oder nativer Excel-Test behauptet.


### PORTAL-010 – Beschriftung/GPS implementiert und geprüft (12.10.2026)
- Nutzer bestätigt PORTAL-009 mit „funktioniert“ und NAS-Screenshots; Galerie-Praxisfunktion damit nutzerseitig bestätigt. Ergänzende Forderung: verständliche/klickbare Beschriftungen und GPS.
- Runtime c7c707aea7d59f5abbed75050d11b5165030a7a1, getesteter Stand 7b3c5be991a2d7220885616439f614531fe7d404. Ursache: Galerie las short_text statt kanonischem kurztext und verwarf aufgelöste GPS-/Aufnahmeangaben.
- media_gallery liefert kanonischen kurztext und begrenzte Zusatzfelder, aufgelöste Aufnahmezeit/GPS; vorhandene BTBP1-/BTBPHOTO-Dekodierung wiederverwendet. Berechtigter Mangel-/Abnahmetitel als Fallback, keine BTB-Fachtexte. Kein Datenrewrite/Migration.
- Galerie: Beschriftung als aufklappbare Details mit Objekt/Bereich/km/Notiz/Infos und technischem Dateinamen; validierte numerische GPS-Koordinaten als OpenStreetMap-Link, sonst klare Fehlanzeige. DOM-textContent, keine untrusted HTML-/URL-Ausführung. 60er-Seiten und Thumbnail-/Previewregeln erhalten.
- Portal-CI38249400190, Job114811482912 SUCCESS mit tatsächlich ausgeführten Steps148/150/149. 10.000-Einträge-Pagination/Rechte plus gepackter Androidname, strukturierte Metadatenpriorität, GPS/Datum, title-Fallback, fehlendes GPS; Chromium1440/390 mit klickbaren Angaben/Kartenhref. Screenshots visuell geprüft. Erste Testfixture verwendete unzulässiges SQLNULL statt leerem JSON, korrigiert und vollständig erneut geprüft.
- Übernahme nur tbserver/media_gallery.py und templates/v1_portal_pictures.html, Anleitung [PORTAL-010_NAS_UEBERNAHME.md](uebergaben/PORTAL-010_NAS_UEBERNAHME.md). PORTAL-010 noch nicht auf NAS installiert/geprüft.
- Grenzen: nicht gespeicherte/decodierbare Angaben nicht rekonstruierbar. SQL-Text-/Datumsfilter indexieren nur gespeicherte Felder, keine neue Klartextsuche durch ausschließlich codierte Altmetadaten. Kein Android-/ZIP-/Backup-/Homepageumbau. Bestehende allgemeine Foundation-/Android-CI-Restfehler nicht durch Portaltest behoben.


### PORTAL-011 – Quellstand fertig, isoliert geprüft (13.10.2026)
- Getesteter Stand b2dd5f80968784af9a55cc907745196fe0953e2b; Runtime seit da8259fd3852558028c693087ae38110aba699ea unverändert. Galerie verwendet kanonische Datei-Metadaten plus bestehende merge_btb_photo_metadata inklusive expliziter Löschungen. Kurztext, BTB-Fotonummer/Datum, gespeicherte Dateigröße, Aufnahmezeit, Kilometer, Firma, Ort/Unterbaustelle/Objekt, Indikator und GPS sichtbar. Notizen/Dateiname aufklappbar.
- Nummern nach BTB-Reihenfolge captured_at NULLS LAST,created_at,id und SHA-/Name-/ID-Deduplikation, gesammelt nur für gewählte berechtigte BTBs. Benötigte Knoten über vorhandene external_key/server-ID-Regel nachgeladen; kein N+1 oder Vollkatalog im Browser. 60er-Seiten, Rechte, Thumb/Preview erhalten. API/HTML-Kennung PORTAL-011 und Hinweis bei Versionsunterschied.
- Portal-CI38391789511/Job115277108244 SUCCESS mit tatsächlich ausgeführten PostgreSQL17-/Jinja-/Integrations-/Chromium1440/390-Schritten148/150/149; Logs und beide Galerie-Screenshots geprüft. 10000 Metadatensätze über167 Seiten ohne Lücke/Duplikate, jede Nummer geprüft, Linkoverride/clear/Knoten/Rechte/Mehrfachlinks/Versionswechsel geprüft. Erste Seite36193Bytes/3SQL/0,1767s; gesamte Traversierung19,648s auf CI; mit Knoten höchstens4SQL. Kein NAS-Performance-/Bildtransfernachweis.
- Snapshot/Rechte/255Client-HTTP-Gate38391789515/Job115277108388 SUCCESS. Gesamt-CI nicht vollständig grün: bestehender Foundation-Marker FILE_LINK_TARGET_NOT_FOUND und Android-CI weiterhin rot. Keine Merge-/Produktivfreigabe behauptet; PR6 offen/Draft, Android423f37892915cb3308a4b1ea9e4d196faa872326/PR11 unverändert.
- Anfänglicher Testfehler durch fälschlich als DB-Spalte verwendeten client_node_key auf bestehende berechnete Schlüsselregel korrigiert. Alte Browsererwartung für Ortsangabe aus Details auf jetzt dauerhaft sichtbaren Bereich angepasst; voller Testlauf anschließend grün.
- NAS-Installation/Praxisabnahme offen. Genaue Zwei-Dateien-Übernahme mit Prüfsummen und read-only Ladepfadprüfung: [PORTAL-011_NAS_UEBERNAHME.md](uebergaben/PORTAL-011_NAS_UEBERNAHME.md). Keine Migration/Android-/ZIP-/Backup-/Homepage-/Composeänderung. Excel bleibt gemäß dokumentiertem binären Defekt blockiert; bestehende Datei nicht überschrieben.


### PORTAL-011 – Nutzerbestätigung (13.10.2026)
- Nutzer bestätigt mit „passt“ und Screenshots die sichtbaren Bildkarten. PORTAL-011 nutzerseitig bestätigt; kein unabhängiger NAS-Zugriff durch Agent. Bestehende Angaben schützen; kompaktere Abstände separat angefordert.

- PORTAL-012: kompaktere Karten und vollständige Text-/KM-Suche angefordert. Bildgrößen-/Gesamtlistennummerierung zunächst erläutern und Vorschläge geben; Nutzer verlangt vor Umsetzung Vorschläge. Details in02_AKTIVER_AUFTRAG.md. Kein Programmcode in diesem Block geändert.


### PDF-CHECK-013 – drei Nutzer-ZIPs read-only geprüft (13.10.2026)
- 22.09.2026: BTB-PDF975269Bytes/5A4-Seiten, ProducerSkia/PDFm148 (Android); bestätigte Referenz mit Fachblöcken, drei Fotokarten pro Zeile, Ort/Firma/Text/GVP-Angaben, sichtbarer Änderungskennzeichnung. PDF keine Linkannotationen. ZIP13Bilder648064–1007625Bytes,2040×1536/1536×2040; Originalupload unverändert.
- 24.09.2026: BTB-PDF8478648Bytes/18A4-Seiten, ProducerReportLab, Marker tb-server-btb-pdf-3, erzeugt26.09.2026. Generischer alter Serverbericht zeigt technische tb_helfer_android_v1-/Slotfelder, codierte BTBP1-Dateinamen, etwa zwei große Bilder pro Seite. ZIP27Bilder270361–1146742Bytes; keine Linkannotationen im PDF. Kein Beweis aktueller Serverlaufzeitversion aus historischem Dokument.
- 05.10.2026: BTB-PDF366329Bytes/3A4-Seiten, ProducerReportLab, Marker tb-server-btb-pdf-5, erzeugt05.10.2026. Bereits Fachlayout, jedoch anderer großer Firmenkopf/Logo-Bereich und Fotoanhang2Spalten/feste238pt-Karten gegenüber Android3Spalten; Orts-/GVP-Block fehlt im gezeigten Fotoanhang. ZIP1Bild288588Bytes/1536×2040; GPS als Text ohne anklickbare PDF-Linkannotation.
- Nachweis: Metadaten, alle Seitentexte/Bildanzahlen/Dimensionen, gerenderte Haupt-/Fortsetzungs-/Fotoanhangseiten verglichen. Keine Pixelparität oder Datenverlust durch Renderer behauptet; Dokumente haben unterschiedliche Fachinhalte.
- Aktueller GitHubserver53a266bc... verwendet weiterhin tb-server-btb-pdf-5 und btb_pdf_android_style._draw_photo_appendix mit2Spalten/fester238ptHöhe/details[:5], ohne Ort/GVP-Zeile und ohne GoogleMaps-Linkannotation. Android423f3789 BtbExportPaket.exportVerlauf exportiert vorhandene v.pdfDatei oder Finderdatei; keine Neurenderung beim ZIP-Packpfad. Daher frischer ZIP-Export kann alte Server-PDF3 enthalten.
- Ursache der nachgewiesenen Darstellungsunterschiede: unterschiedliche Android-/Serverrenderer und historische gespeicherte Abschluss-PDFs, keine Bildkompression als Ursache. Aktiver NASrenderer/konkrete Gerätepfadwahl nicht ferngeprüft. Großer Farbkreis im05.10.-Dokument ist eingebettetes Firmenlogo, kein Nachweis eines beschädigten Fotos.
- Vorschlag, noch nicht freigegeben:22.09.-Layout als Referenz für Serverkopf/Fachblöcke/Fotokarten3Spalten, dynamische Text-/Seitenhöhen, Ortsauflösung und vorhandene GVP-Metadaten, GPSGoogleMaps-Link; Nummern im PDF/Text/Export konsistent vergleichen. Kontrolliertes Neurendern aktueller Abschluss-PDFs nur ausdrücklich beauftragt, ersten Originalsnapshot/Änderungshistorie niemals ersetzen. Nicht alle Fotos auf Hauptbaustelle umhängen oder Ursprungsort/GVP erfinden.
- NutzerzielBildgröße1–2MB/max2MB; kleinere geeignete Bilder nicht vergrößern. ZIPs belegen viele brauchbare Dateien bereits unter1MB, Galeriegrößenbis4,19MB betreffen anderen gespeicherten Bestand. ZIP-Größe/PDF-Bildgröße nicht mit Serveruploadoriginal gleichsetzen. Portalverkleinerung vorUpload beschlossen; adaptive Qualität/Auflösung und serverseitige Grenzen vorschlagen, keine Bestandskompression/Löschung ohne Auftrag.
- Ausschließlich Prüfung/Dokumentation, kein Runtimecode, keine Migration/NAS-/Androidmutation. PORTAL-012kompakte Karten/Text-/KM-Suche unverändert geplant; GoogleMaps-Galeriewechsel und Kompressionsdetails weiterhin Vorschläge. Kein E2E-Lauf in diesem Prüfblock.


### SUBSITE-CHECK-014 – Foto27/Unterbaustellen read-only untersucht (13.10.2026)
- Neue Abschluss-PDF BTB_bd7bef76... ist BTB06.10.2026,3Seiten, erzeugt11.10.2026, ReportLab/tb-server-btb-pdf-5. Nutzer bestätigt Serverdarstellung als okay; nicht ungefragt Layoutumbau aus PDF-CHECK-013 durchführen. Neue PDF hat nurBild1, enthältFoto27 vom24.09. nicht. Sie beweist nicht Foto27-Zuordnung.
- Foto27Screenshots zeigen vollständig den vom aktuellen Modell gelieferten Kurztext „Fertigstellung Bü7“ und Firma „Peter Schaue“, OrtHauptbaustelle·5362_R_071. Galerie setzt summary.textContent ohne Ellipsis/Zeilenlimit, Textumbruch vorhanden; kein Quellbeleg für CSS-Abschneiden.
- Beleg aus unverändertem24.09.-ZIP: Bilderliste_gesamt.csv fürFoto2026-09-24_16-04-47(2).jpg/Zeit16:04/GPS47.596990,10.253655 enthält UnterbaustelleBÜ78,571, KurzbeschreibungFertigstellungBü78,571, FirmaPeterSchauerTiefbau und Aufnahmekilometer78,577. BÜ-Kilometer78,571 und Aufnahme-KM78,577 getrennte Angaben, nicht gleichsetzen.
- AlterServer-PDF3 Foto27Dateiname BTBP1 decodiert: epoch1790258692605,GPS47.5969887/10.2536554,KM78.5770035, FirmaPeterSchaue,KurztextFertigstellungBü7,Objekt5362_R_071,Objektbereichleer. Damit abgeschnittene Altmetadaten reproduzierbar, passendeOriginalexportzeile anhandZeit/GPS/KM belegt; unveränderlicheFoto-ID zur sicherenBestandsreparatur trotzdem prüfen.
- Android423f3789 BtbTeamMediaSync.encodedPhotoFilename/writeTinyUtf begrenzen Firma auf12UTF8Bytes,Kurztext20,Objekt10,Bereich8; diesesAltformat enthält keineworksiteNodeKey. Neue structuredPhotoMetadata enthält dagegen volleFelder und worksiteNodeKey. BekannteServerfotos werden nicht neuhochgeladen; localPhotoOverridePatch sendet Firma/Ort nurbeiContextoverride undKurztext nurbeiTextoverride. Bloßes normalesSynchronisieren repariert daher fehlendeAltgrundmetadaten nicht garantiert.
- Galerie nodeLabel liefert bei fehlendemworksiteNodeKey pauschalHauptbaustelle; unbekannterKnoten mit vorhandenemKey wird dagegenUnterbaustelle<Key>. DieserFallback unterscheidet nicht zwischen tatsächlicherHauptbaustelle und unbekannterAltzuordnung. „AlleUnterbaustellen fehlen“ fürgesamtenNASbestand nichtnachgewiesen, keinLiveAPI-/DB-Zugriff.
- Vorschlag vorUmsetzung: vollständige strukturierteMetadaten undBTBOverrides bevorzugen; fehlendeAltzuordnung alsOrt/Unterbaustelle nichtzugeordnet kennzeichnen, echteHauptbaustelle separatbelegen. BestehendeKnotenbezeichnungen auflösen(z.B.BÜ78,571), Aufnahmekilometer extraanzeigen. Altdaten ausschließlich überbelegtenID-/Zeit-/GPS-/Originalexportabgleich mitKonfliktprüfung undVorschau ergänzen, keine Zuordnung ausGPS-Nähe/Dateigröße oderbloßemKurztext. FürFoto27 Zielwerte ausCSV verfügbar, keineÄnderung jetzt.
- NeueBildübertragungen max2MB(2000000Bytes) verbindlich, vorhandene2,04MB-/größereBilder unangetastet. PortalvorUploadverkleinern undServerprüfung;Android2MB-Durchsetzung bleibt Folgeauftrag. KeinMindestvolumen.
- NurPrüfung undDokumentation; Runtime/App/DB/NAS/Originale unverändert. PORTAL-012Suche/kompakteKarten weiterhin ungebauteFolgearbeit, keineAbnahmebehauptung.


### PORTAL-012 – Quellstand fertig, isoliert geprüft (13.10.2026)
- Getesteter Stand 322f7b14094c0752eef07bd3bcfb659ef36c1d03. Mehrwortsuche als AND, unabhängig von Groß-/Kleinschreibung, über kanonisch decodierte Alttexte, strukturierte Metadaten, wirksame BTB-Linkoverrides inklusive clear und aufgelöste Ortsnamen. Suchtreffer nur über einen zugleich berechtigten Link; fremde Linktexte dürfen keine sichtbaren Dateien als Treffer offenlegen. Keine Suche nur auf der aktuellen Seite.
- KM13.000→13.000–13.999;13.200→13.100–13.299; Punkt/Komma/optional KM gleich. Andere nichtganzzahlige Werte100m davor bis99m danach; auf angezeigte Metergenauigkeit gerundet. Bereich bei Treffern sichtbar. Migration024 ergänzt nur zwei lesende Such-/Decodefunktionen, kein Bild-/Metadatenrewrite. PostgreSQL17-Byteoffset-/UTF8-Dokumentation über Context7 abgeglichen.
- Galerie kompakter, weiter60 Bilder und kleine Renditions, GoogleMaps-Link. Fehlender worksiteNodeKey liefert „Unterbaustelle nicht zugeordnet“ statt unbelegter Hauptbaustelle; vorhandener nicht auflösbarer Schlüssel gesondert. Vollständige vorhandene Felder werden nicht gekürzt. Aus bereits gekürztem Altdateinamen werden keine verlorenen Texte/Orte erfunden.
- Portal-BTB-/Mangel-/Abnahmefotos werden im Browser vor SHA/Chunkupload als JPEG vorbereitet: maximal2560px, Qualität90–75, gegebenenfalls kleinere Kante, maximal2000000Bytes, nicht hochskalieren. PC-Original unverändert, kein unkomprimierter Fallback. Kleine Dateien erlaubt. Serverseitige2-MB-Prüfung bei Uploadbeginn, kumulierten Chunks und tatsächlichem Abschluss; explizite PHOTO/DEFECT_PHOTO/ACCEPTANCE_PHOTO, Bild-MIME/Endungen und beim Abschluss erkannte verdeckte Bilddateien. Bestehende Dateien sowie größere PDF-/Dokumentuploads unverändert.
- Portalworkflow38417438535/Job115365514637 SUCCESS; echte Steps148/150/149 ausgeführt, Logs und Desktop1440/Mobil390-Screenshots geprüft. Migration24 und Wiederholung,10000 Datensätze über167 Seiten, Nummern/Rechte/Linkänderung/clear/Legacy-/Mehrwort-/Orts-/KM-Suche, fremde Linkmetadaten, Versionsunterschied, Browserverkleinerung/Originalerhalt geprüft. Erste Seite36239Bytes/3SQL/0,1308s; gesamte Traversierung14,792s auf CI. Echter2.000.000Byte-JPEG-Chunkupload/Abschluss bytegleich PASS;2.000.001 bei Beginn/Chunk/Altabschluss abgewiesen, kleine Bilder und größere PDF-Dateien erlaubt. Kein NAS-Performance-/E2E-Nachweis daraus.
- Snapshot-/Rechte-/255Client-HTTP-Gate38417438519/Job115365514320 SUCCESS mit tatsächlich ausgeführten Steps. Gesamt-CI weiterhin nicht vollständig grün: unveränderter Foundation-MarkerFILE_LINK_TARGET_NOT_FOUND, Android-CI rot. Androidbranch423f37892915cb3308a4b1ea9e4d196faa872326 unverändert, kein APK. PR6 bleibt Draft.
- NAS-Installation/Praxisabnahme offen. Acht Runtime-Dateien inklusive Migration024 und neuer Include-Vorlage: [PORTAL-012_NAS_UEBERNAHME.md](uebergaben/PORTAL-012_NAS_UEBERNAHME.md). Über GitHubDesktop→FileStation→ContainerManager Projekt neu erstellen/starten; nicht nurAPIneustarten, da Suchfunktionen fehlen würden. Migration24/24 undPORTAL-012 prüfen; nichts löschen/keine .env/Compose/Daten-/Backupänderung.
- Foto27-Korrekturvorschau mit belegten vollständigen Angaben in derselben Anleitung vorbereitet, noch keine Produktionszuordnung geändert. Kontrollierte Altmetadatennachlieferung/Android bleibt eigener Folgeauftrag. Kein PDF-Layoutumbau/Neurenderung; bestätigte Serverdarstellung erhalten. Neue 2-MB-Servergrenze gilt auch für alte Androidclients; größere neue Syncbilder werden verständlich abgewiesen, Androidverkleinerung nicht als implementiert behaupten.
- Bestehende Exceldatei gemäß dokumentiertem binären Defekt unverändert/blockiert; MD-Steuerung gesichert.


### PORTAL-015 – KM-Regel und Baumreihenfolge umgesetzt (18.10.2026)
- Neue volle-Kilometer-Regel: 13.000/13,000/13/KM13.000 → 12.000–14.000 einschließlich beider Grenzen; volle Kilometer allgemein ±1km. Nichtganzzahlig13.200 → 13.100–13.299 unverändert. Punkt/Komma und optionale KM-Eingabe bleiben gleich. Keine SQL-/Migrationsänderung.
- Baustellenansicht rendert nun Eltern vor ihren Kindern und deren gesamte Unterstruktur zusammenhängend. Bisherige flache nach parent_id sortierte API-Liste konnte die Anbindung vor SchachtV2 anzeigen. Eingabe-/API-Speicherung der parent_id bleibt unverändert; keine echten NAS-Zuordnungen auf Verdacht umgehängt. Fehlende Eltern/Zyklen bleiben sichtbar, Traversierung endet sicher und ist ohne rekursive Aufrufgrenze. Reihenfolge der Geschwister aus API erhalten.
- Tatsächlich lokal ausgeführt: checks/151_portal_km_tree_regression.py PASS, nutzt Produktions-KM-Parser und echte Produktionsfunktionen nodeTreeRows/loadNodes mit DOM-/API-Doubles; Randwerte11999/12000/14000/14001, unveränderte13.200-Grenzen, Null/negative volleKM, Screenshot-Struktur, Einrückung, keine Datenmutation, Zyklen/Orphans und5000 Ebenen. Python-AST und JavaScript-Syntax PASS. Erste Test-Doppel-Scope-Bindung korrigiert, danach vollständiger lokaler Lauf PASS. Bestehender PostgreSQL-Integrationstest150 um neue Grenzen angepasst, in diesem Block nicht ausgeführt.
- CI-Budgetschutz CI-COST-001: gebündelter Commit mit [skip ci], keine großen Actions-Läufe gestartet. Keine neue PostgreSQL-/Chromium-/Synology-E2E-Abnahme behauptet. Bestehende allgemeine CI-Restpunkte bleiben separat.
- NAS-Übernahme nur server/tbserver/media_gallery.py und server/templates/v1_worksite.html in entsprechende vorhandene Unterordner von /volume1/docker/tb-helfer-v1; API-Container neu starten, StrgF5. Keine neue Migration, nichts löschen, .env/Compose/Storage/Backups unverändert. Bisheriger PORTAL-012-Migrationsstand wird vorausgesetzt. Anleitung: [PORTAL-015_NAS_UEBERNAHME.md](uebergaben/PORTAL-015_NAS_UEBERNAHME.md).
- Ausgangsstände live geprüft: SERVER d4008086989d919b7a24aa088c54e5c0bdeff329/PR6 Draft; ANDROID6cec34423e34315c581b743668724abe17daa333/PR11 offen, kein Draft. app/** unverändert. Excel bleibt laut bestehendem dokumentiertem binären Defekt unverändert; keine Ersatzhistorie erzeugt.


### PORTAL-015 – Nutzer bestätigt Funktion (18.10.2026, 15:26 Berlin)
- Nutzer meldet ausdrücklich „funktioniert“ nach Übernahme. Unterbaustellen-Screenshot zeigt Eltern-vor-Kind-Reihenfolge mit mehreren Ebenen und Anbindung an Bestandsschacht unter SchachtV2. Galeriescreenshot zeigt Suche78.500 mit Bereich78.400–78.599 und30 Bildern.
- PORTAL-015 nutzerseitig bestätigt; bestätigte Baumdarstellung und KM-Suchregeln schützen. Ganzkilometerregel13.000→12.000–14.000 bleibt gemäß Auftrag/Quelltest verbindlich; kein zusätzlicher separater Grenzfoto-Nachweis aus Screenshot78.500 behauptet.
- Keine neue Runtimeänderung, kein neuer Test-/Actionslauf. Bestehende unzugeordnete/kürzere Altmetadaten sind separat offen und werden durch diese Bestätigung nicht als repariert gewertet.
