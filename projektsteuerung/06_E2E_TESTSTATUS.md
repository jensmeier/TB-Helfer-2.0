# TB-HELFER 2.0 – E2E TESTSTATUS

Stand: 2026-09-26

| Bereich | Status | Nächster Test |
|---|---|---|
| Android 1.3.25 / 200075 Build | SOURCE-FIX 606aa3ad / BUILD-RETEST OFFEN | lokalen/Android-Studio-Build erneut ausführen; erst danach APK-Artefakt bewerten |
| Android 1.3.24 / 200074 Build | ERFOLGREICH / PRAXIS NACHGEWIESEN | installiert auf Pixel |
| Android 1.3.24 / 200074 Installation | PIXEL BESTÄTIGT | Poco/HELPER separat |
| BTB-UI-001 Abschlussleiste | SOURCE-FIX UMGESETZT / BUILD OFFEN | 1.3.24/200074 bauen/installieren; Haupt-TB 2 Buttons stabil prüfen |
| Android 1.3.23 / 200073 Build | SOURCE-FIX UMGESETZT / BUILD OFFEN | Android Studio/CI Build 200073 |
| Android 1.3.23 / 200073 Installation | OFFEN | zuerst Pixel, danach Poco |
| BTB-SYNC-002 Pixel↔Portal Datenverlust | BESTÄTIGT | Bidirektional + Parallel-Autosave bestanden; geschützt |
| Android 1.3.21 / 200071 Build | VERSION + SOURCE-FIX FERTIG / BUILD OFFEN | Android Studio Build 200070 |
| Android 1.3.22 / 200072 Build | SOURCE-CHECK PASST / BUILD OFFEN | Android Studio Build 200072 |
| Android 1.3.22 / 200072 Installation | OFFEN | Pixel + Poco nach Build |
| Android 1.3.21 / 200071 Installation | BESTÄTIGT | installiert / Updateweg funktioniert |
| UPD-001 Updateprüfung 1.3.21 / 200071 | BESTÄTIGT | geschlossen; nur bei neuer Reproduktion wieder öffnen |
| Pixel/Poco Versionsgleichstand | BENUTZER BESTÄTIGT: BEIDE 1.3.19 | Server-DB-Appversion ist Aktivierungs-Snapshot und kein Runtime-Nachweis |
| Pixel ACTIVE↔PDF-Flackern | 1.3.21 FEHLER REPRODUZIERT / 1.3.22 SOURCE-FIX | 200072 bauen/installieren/praktisch prüfen |
| Haupt-TB-Handy ↔ Server/Portal | PRIORITÄT / E2E OFFEN | echte Fachkette zuerst sauber bestätigen |
| BTB 26.09 Pixel → Server/Portal/Poco | PIXEL→SERVER→PORTAL BESTÄTIGT / POCO FEHLER OFFEN | Portal nach Reload korrekt; Poco Active-Pointer/UI-Apply prüfen |
| Haupt-TB-Handy DNS/API-Erreichbarkeit | PIXEL SYNCHRONISIERT WIEDER / URSACHE ALTFEHLER OFFEN | Fachkette Pixel↔Server↔Portal weiterprüfen; DNS-Altfehler nur bei Wiederholung vertiefen |
| Poco Team-/BTB-Daten | ZWEITER POINTER-GUARD ALS URSACHE BESTÄTIGT / 1.3.22 SOURCE-FIX | 200072 bauen/installieren, BTB 26.09 erneut prüfen |
| Helfer Schicht Ende → Server FINISHED → Pixel | E2E OFFEN | echter Geräte-/Server-Lauf |
| Haupt-TB Finalize + PDF | TEILWEISE BESTÄTIGT | Android-Gesamtkette offen |
| IMG-001 Handyfoto → Server | QUELLAUDIT FEHLER BESTÄTIGT / FIX SPÄTER | Nach E2E-PRIO-001 Android-Fix; dann Original- vs Sync-Dateigröße messen |
| IMG-001 Portal → Handy | QUELLAUDIT FEHLER BESTÄTIGT / FIX SPÄTER | Nach E2E-PRIO-001 Android auf preview/thumb; dann Quelle/Rendition/Gerät vergleichen |
| IMG-001 Handy → anderes Handy | QUELLAUDIT FEHLER BESTÄTIGT / FIX SPÄTER | Nach E2E-PRIO-001 Pixel/Poco Abmessungen/Größe vergleichen |
| Migration 015 | DEPLOY/E2E OFFEN | Runtime-Probe |
| 15×15 Delta-/Lasttest | KAPAZITÄTSTEILTEST 255/255 BESTANDEN / GESAMT-E2E OFFEN | Erst Hauptgerät↔Portal, dann HELPER/Poco; Gesamtlast nach RAM-/NAS-Ausbau wiederholen |

Source-Check ≠ Build ≠ Installation ≠ E2E.

### Kapazitätsteiltest 26.09.2026

`sync/changes/wait` mit 255 getrennten Portal-Sessions: 255 erfolgreich, 0 Fehler; Wall 16,43 s, Median 14,28 s, Max 15,79 s. Testsession-Reste anschließend kontrolliert auf 0 bereinigt.

Dieser Nachweis bestätigt nur den Delta-Wait-Kapazitätsteil. 15 Teams, echte Geräteidentitäten, BTB-Fanout, Offline/Online, Medien, Konflikte und Recovery bleiben Bestandteile des offenen Gesamt-E2E.

### Gerätebefund NET-001

Beim Hauptgerät nach Anlage einer Testbaustelle:
`Synchronisation unvollständig: Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.

Nachtest: beide öffentlichen Seiten sind auf demselben Hauptgerät im Browser erreichbar. Öffentliche DNS/API-Erreichbarkeit ist damit nachgewiesen; der App-Sync muss jetzt erneut getestet werden.

### NET-001 Browser-Nachtest
`www.tb-helfer.de` und `api-v1.tb-helfer.de/health/live` sind auf demselben Hauptgerät im Browser erreichbar. App-Retest offen.


### BTB-SYNC-001 Source-Fix
- Commit `f57c532863d2f92b81729b9af1466b57a22f8723`.
- Server-`active=true` überstimmt jetzt lokale Abschluss-/PDF-/Baseline-Zustände in `BtbActiveContext`.
- Server-`FINALIZED && !active` darf den Pointer lösen.
- Build wurde noch nicht ausgeführt; GitHub Actions lieferte für den aktuellen HEAD keinen Workflow-Run. Kein Build-/Installations-/E2E-Nachweis.


### Version 1.3.20 / 200070
- Quellwerte, Release-MD und CI-Artefaktname auf 1.3.20 / 200070 angehoben.
- Commit `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- GitHub Actions hat für diesen HEAD keinen Workflow-Run geliefert; daher weiterhin **BUILD OFFEN**.


### UPD-001 Diagnose
- Wartungsanzeige = eingebauter `BuildConfig`-Stand.
- Startbanner = Serververgleich gegen eingebauten `BuildConfig.VERSION_CODE`.
- Keine relevante Cacheursache im geprüften Pfad.
- APK-Metadaten werden weder beim Serverupload noch im Android-Installerpfad gegen Releasefelder validiert.
- Nächster Build für Schutzfix: 1.3.21 / 200071.


### UPD-001 Source-Fix 1.3.21 / 200071
- APK-Metadatenvalidierung source-seitig umgesetzt.
- Noch kein Buildnachweis.
- E2E-Erwartung: nach erfolgreicher 200071-Installation zeigt Wartung 1.3.21 (200071), Startseiten-Updatehinweis verschwindet.
- Negativtest: absichtlich falsche/alte APK unter 200071-Metadaten muss vor Installer blockiert werden.


### UPD-001 Praxis-E2E bestätigt
- Benutzer bestätigt 1.3.21 / 200071 Updatepfad als funktionierend.
- Release-MD + APK desselben Builds verwendet.
- Build/Installation/Update-E2E: **BESTÄTIGT**.
- BTB-SYNC-001 bleibt separater nächster Geräte-E2E.


### BTB-SYNC-001 E2E 1.3.21
- Pixel: Abschlussbutton flackert weiterhin.
- Poco: 26.09 kurz sichtbar, danach kein BTB vorhanden.
- Ergebnis: **NICHT BESTANDEN**.
- Update-E2E bleibt separat BESTÄTIGT.


### BTB-SYNC-002 Source-Fix 1.3.23
- Dirty-BTB wird jetzt vor jedem Server-Snapshot-Apply zuerst in Pending/Outbox exportiert.
- Vorgelagertes Snapshot-Apply im `SelectiveSyncWorker` entfernt.
- `CollabLocalBridge` blockiert BTB-Datei-Apply bei Pending oder Dirty.
- Lokaler Save und Server-Apply sind pro BTB serialisiert.
- Neuer Autosave während Upload bleibt dirty und erzwingt einen Folgelauf.
- Version **1.3.23 / 200073**.
- Status: **SOURCE-FIX UMGESETZT / BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN**.

Pflichttest:
1. Pixel speichert eindeutigen Text `ABC`;
2. Portal erhält `ABC`;
3. Portal ändert auf `XYZ`;
4. Pixel erhält `XYZ`;
5. zusätzlicher Pixel-Autosave während laufendem Upload darf nicht still verschwinden.


## BTB-SYNC-002 – bidirektionaler Basistest 1.3.23
**BENUTZER BESTÄTIGT:** Der BTB-Sync funktioniert im aktuellen Test in beide Richtungen:
- Pixel → Portal: **BESTANDEN**
- Portal → Pixel: **BESTANDEN**

Damit ist der normale bidirektionale Datenweg des BTB-SYNC-002-Fixes praktisch bestätigt. Nicht durch diese Aussage abgedeckt ist der zusätzliche Parallel-Autosave-/Konflikttest während eines laufenden Uploads; dieser bleibt offen. Poco/HELPER bleibt ebenfalls ein eigener E2E.


### BTB-UI-001 1.3.24 Praxis
- Pixel: **BESTANDEN**.
- PDF Entwurf + BTB Abschluss bleiben stabil sichtbar.
- Pixel-UI-E2E: **BESTÄTIGT**.
- Poco/HELPER: **OFFEN**.


### BTB-SYNC-002 Abschluss-E2E
- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Parallel-Autosave-/Dirty-Schutz: **BESTANDEN**.
- Gesamtstatus BTB-SYNC-002: **BESTÄTIGT**.
- Nächster E2E: Poco/HELPER.


### BTB-SYNC-002 Datenverlust-E2E
**FEHLER REPRODUZIERT:** Pixeländerung erscheint nicht im Portal; Portaländerung kommt auf Pixel an und überschreibt die Pixeländerung. E2E gilt bis zur Konflikt-/Outbox-Klärung als fehlgeschlagen.


### BTB-SYNC-002 Source-Diagnose
Source-Ursache bestätigt: `SelectiveSyncWorker` wendet bei bekanntem BTB einen Server-Snapshot lokal an, bevor die dirty Pixel-Änderung mit `exportBtbChange()` in Pending/Outbox gesichert wird. Status: **SOURCE-URSACHE BESTÄTIGT / ANDROID-FIX + BUILD + INSTALL + E2E OFFEN**.


### BTB-SYNC-002 bidirektionaler Basistest 1.3.23
**BENUTZER BESTÄTIGT:** Pixel → Portal und Portal → Pixel funktionieren im aktuellen Test. Der normale bidirektionale Datenweg ist damit praktisch bestanden. Der zusätzliche Parallel-Autosave-/Conflict-Test bleibt separat offen.


### PORTAL-LIVE-001 Praxis-E2E
**BENUTZER BESTÄTIGT:** Portal aktualisiert ohne F5 nach ca. 1–2 Sekunden. Erstellen und Löschen werden automatisch sichtbar; der getestete Datenweg funktioniert in beide Richtungen. Status: **BESTÄTIGT**.

Die notwendige Nacharbeit am BTB-Portal wird separat als `PORTAL-BTB-UI-001` geführt und ändert diesen E2E-Nachweis nicht.


### BTB-EDIT-001 Source-/E2E-Status
**SOURCE-PRÜFUNG:** PASS für den Server-Quellstand:
- bestehende Portal-BTB Source-Checks 94–99 gegen den aktuellen Inhalt geprüft;
- neuer Check 100 für strukturiertes Entry-/Foto-Umhängen vorhanden und Assertions erfüllt;
- kompletter Portal-JavaScript-Block syntaktisch erfolgreich geparst;
- SSE-Live-Refresh weiterhin vorhanden;
- GPS/Lat/Lon/Epoch sind keine erlaubten BTB-Link-Override-Felder.

**NICHT E2E:** Migration 016 wurde auf der Synology noch nicht praktisch angewendet; neuer API-/Portalstand nicht deployed; kein Geräte-E2E mit Android für BTB-EDIT-001.

GitHub Actions am Server-HEAD lieferte einen fehlgeschlagenen Job ohne ausführbare/gelieferte Runner-Schritte; gemäß Projektregel kein Testnachweis.


### Android 1.3.25 / 200075 – echter lokaler Buildfehler
**BUILD FEHLGESCHLAGEN:** `:app:generateBtb200043Sources` findet den alten Android-200047-Ortsmarker-Anker nicht mehr.

Dies ist erstmals ein echter ausgeführter lokaler Buildpfad; anders als der frühere CI-Lauf ohne Runner ist dieser Fehler reproduziert. Installation und BTB-EDIT-001-Geräte-E2E bleiben offen.

### INT-001 Integrationsnachweis
**SOURCE-INTEGRATION:** Android-Parent `4de3350fa4bc3777a6f2f51514e85a145ca4a9c6` und Server-Parent `c7358cd223cbd3575146fc98a3477fc6993501d2` konfliktbewusst zusammengeführt.

Dies ist **kein Build-/Installations-/Geräte-E2E-Nachweis**. Android 1.3.25/200075 Build-Retest, Server Migration016/Deploy und BTB-EDIT-001 E2E bleiben offen.


### Gemeinsamer Merge-HEAD ee8358e0 – CI
- TB-Server 1.0 Run `36253773890`: `failure`, Job `foundation`, `steps=[]`.
- Android CI Run `36253773908`: `failure`, Job `build`, `steps=[]`.
- Bewertung: **keine Runner-Schritte ausgeführt → kein Testnachweis**.
- Server-Deploy/Migration016, Android-Build-Retest und Geräte-E2E bleiben offen.


### BTB-EDIT-001 Migration 016 – Praxisnachweis
**BESTÄTIGT:** Synology/API-Terminal meldet `APPLIED 016_btb_photo_link_metadata.sql`.

Dies bestätigt ausschließlich den DB-Migrationsschritt. API-/Worker-Runtime und Portal-/Geräte-E2E sind noch getrennt zu prüfen.

### ADMIN-FINISH-001 / ADMIN-STORAGE-001 – 28.09.2026
- Admin-Quellstand inkl. Speicher-Deduplizierung vorhanden.
- Legacy-Fall `tb.files.storage_key == tb.survey_originals.storage_key` wird source-seitig nur einmal als physischer Speicher gezählt.
- Source-Assertions: **PASS**.
- GitHub Actions Server Run `36408724428` und Android Run `36408724459`: `failure`, Jobs mit `steps = null`.
- Bewertung nach Projektregel: **kein Build-/Testnachweis**.
- Status: **SOURCE FERTIG / DEPLOY OFFEN / RUNTIME-SMOKE OFFEN / E2E OFFEN**.
- Pflichttest nach Deploy:
  1. Übersicht lädt ohne Fehler;
  2. TB-Helfer-Gesamtspeicher plausibel;
  3. Firma zeigt Fotos/PDF/Trassierungsdaten/Sonstiges;
  4. Legacy-Vermessungsoriginal mit gemeinsamem `storage_key` zählt nicht doppelt;
  5. PDF-Vorschau öffnet;
  6. Bahnhofssuche und Trassierungsimport smoke-testen.

### ADMIN-RUNTIME-002 – Praxis 28.09.2026
- Deployment des neuen Admin-Stands praktisch sichtbar: **JA**.
- Übersicht/Auto-Refresh: **LÄUFT**.
- Trassierungsdaten-Upload: **LÄUFT**; Originale werden angezeigt/ausgewertet.
- Bahnhof `MIB`-Autofill: **NICHT BESTANDEN**.
- `Firma verwalten`: **NICHT BESTANDEN**.
- Gesamtstatus: **DEPLOYT / TEIL-E2E / NACHARBEIT OFFEN**.

### ADMIN-RUNTIME-002 – Firmenverwaltung 500
- Laufzeitfehler praktisch reproduziert: `psycopg.ProgrammingError` wegen `%` in parametrierter LIKE-Query.
- Source-Fix Commit `6cdccc9267302f8442819d02a884406ad00163a7` vorhanden.
- Source-Assertion: PASS.
- Noch offen: korrigierte `server/tbserver/admin_web.py` auf Synology übernehmen, API neu laden und `Firma verwalten` erneut öffnen.
- Status: **RUNTIME-FEHLER REPRODUZIERT / SOURCE-FIX FERTIG / RETEST OFFEN**.


### ADMIN-RUNTIME-002 – Firmenverwaltung Retest
- `Firma verwalten` nach Source-Fix praktisch geöffnet: **BESTANDEN**.
- HTTP-500 aus der Speicherabfrage für diesen Pfad nicht mehr reproduziert.
- Andere Admin-Teilpfade (MIB, globale Referenzdaten, neuer UX-Aufbau) bleiben separat offen.

### ADMIN-STRUCTURE-004 – Source-Status 28.09.2026
- Firmenzentrierte Admin-Struktur source-seitig umgesetzt.
- Template-JavaScript für Firmenworkspace, Firmenliste und Dashboard: **PASS**.
- Keine Android-Codeänderung.
- GitHub Actions auf HEAD: Server/Android `failure`, jeweils `steps = null` → **kein Testnachweis**.
- Status: **SOURCE FERTIG / DEPLOY OFFEN / RUNTIME-E2E OFFEN**.
- Retest nach Deploy:
  1. Firmenliste öffnen und neue Firma anlegen;
  2. Firma verwalten mit linker Navigation;
  3. Benutzer innerhalb Firma anlegen/bearbeiten;
  4. Aktivierungskarte/Portalaktivierung prüfen;
  5. gebundenes Gerät anzeigen und eine ungefährliche Statusaktion prüfen;
  6. Baustelle innerhalb Firma anlegen;
  7. Speicher + Logo/PDF-Vorschau öffnen.

### ADMIN-COMPANY-005 – Source-Status 28.09.2026
- Aktivierungskarte-Rücksprung: Source-Fix vorhanden.
- Gültigkeitsanzeige Datum + Restdauer: Source-Fix vorhanden.
- alte globale Benutzer/Geräte/Baustellen-Listen: Redirect zur Firmenverwaltung vorhanden.
- Geräteversion: UI trennt jetzt `zuletzt gemeldet` von `veröffentlichtem Server-Release`.
- Server kann neue Versionsheader bei Gerätekontakt speichern; Android sendet sie noch nicht.
- Template-JavaScript: **PASS**.
- GitHub Actions ohne Runner-Steps (`steps = null`): **kein Testnachweis**.
- Status: **SOURCE FERTIG / DEPLOY OFFEN / RETEST OFFEN**.
- Retest nach Deploy: Aktivierungskarte öffnen → zurück → Firmen-Benutzer; Gültigkeitslabel prüfen; Geräteansicht mit Server-Release 1.3.25 prüfen.

### ADMIN-COMPANY-005 – Praxis + Gültigkeitsnachtrag
- Benutzer bestätigt Firmen-/Geräteansicht: **PASST**.
- Anzeige `zuletzt vom Gerät gemeldet` + `Server-Release 1.3.25 / 200075` praktisch sichtbar.
- Neuer Nachtrag `Zugriff gültig` im Benutzer-Bearbeitungsformular ist source-seitig umgesetzt.
- Nach Upload noch prüfen: Kein Ablauf, 3 Monate, 6 Monate, 1 Jahr und Manuell speichern korrekt.
- Status Nachtrag: **SOURCE PASS / DEPLOY-RETEST OFFEN**.


### ADMIN-DASH-006 – Source-Status 28.09.2026
- Dashboard-/Bahnhof-Source-Block: **PASS**.
- Offizieller Referenzdatentest:
  - `MIH` liefert exakt `Ingolstadt Hbf`;
  - `MIB` ist kein exakter Referenzcode und wird nicht automatisch gespeichert.
- API-Data-Mount source-seitig vorhanden.
- Runtime noch offen, da Compose-Volume erst nach Container-Recreate aktiv wird.
- Pflichttest nach Deploy:
  1. Übersicht lädt und aktualisiert sich;
  2. Warnung `Bahnhof-Referenzdaten fehlen` darf bei korrektem Mount nicht erscheinen;
  3. `MIH` füllt `Ingolstadt Hbf`;
  4. `MIB` zeigt ähnliche offizielle Vorschläge, darunter `MIH – Ingolstadt Hbf`;
  5. Namenssuche `Ingolstadt` liefert passende Betriebsstellen;
  6. Warnungsquittierung im Dashboard funktioniert.
- Status: **SOURCE FERTIG / DEPLOY-RECREATE OFFEN / E2E OFFEN**.

- GitHub Actions auf HEAD `bf6bc9f4...`: TB-Server Run `36480722916` und Android Run `36480722938` jeweils `failure`, Jobs jeweils `steps = null`; damit weiterhin **kein Testnachweis**.


### ADMIN-DASH-006 – Bahnhof Runtime-Retest
- Benutzer bestätigt am 28.09.2026 nach Synology-Recreate: **Bahnhofssuche FUNKTIONIERT**.
- `MIH` füllt `Ingolstadt Hbf`; Vorschlagsliste wird praktisch angezeigt.
- Dieser Bahnhof-/Datenmount-Teil ist damit **BESTANDEN**.

### ADMIN-PDF-007 – PDF-Logo Runtime
- Vor Fix praktisch **NICHT BESTANDEN**: Breite/X/Y im Admin veränderbar, PDF-Vorschau optisch unverändert.
- Source-Ursachen behoben: aktuelle Reglerwerte gehen in Draft-Vorschau; Firmenkopf hat größere Android-nahe Logo-Zone.
- Source-JavaScript/Assertions: **PASS**.
- Runtime nach Deploy offen: Breite 40→250, X -120→+120, Y bei kleiner/mittlerer Breite, Draft-Vorschau ohne Speichern, Persistenz nach Speichern und Großansicht prüfen.
- Status: **SOURCE FERTIG / DEPLOY-RETEST OFFEN**.

- ADMIN-PDF-007 CI auf Code-/Vertrags-HEAD `379c869d...`:
  - TB-Server Run `36487263863`: failure, Job `foundation`, `steps = null`;
  - Android Run `36487263880`: failure, Job `build`, `steps = null`;
  - gemäß Projektregel **kein Testnachweis und kein Sourcefehler-Nachweis**.

### ADMIN-DASH-008 – Festplatten-Temperaturen Source-Status
- Source-Implementierung vorhanden: `host.disk_temperatures[]` + Dashboardanzeige je Laufwerk in °C.
- Synology-Compose enthält read-only Mount `/run/synostorage/disks -> /host/synostorage/disks`.
- Commit-Diff/Source-Marker geprüft: Reader, API-Payload, Dashboard-DOM/Renderer und Compose-Mount vorhanden.
- Das ist **kein Runtime-/E2E-Nachweis**.
- Pflichttest nach Recreate:
  1. Dashboard zeigt je physischem Laufwerk einen °C-Wert;
  2. Werte plausibel gegen DSM Speicher-Manager vergleichen;
  3. bei nicht verfügbarer Hostquelle muss die UI sauber `Nicht verfügbar` statt eines erfundenen Werts zeigen.
- Status: **SOURCE FERTIG / DEPLOY-RECREATE OFFEN / E2E OFFEN**.

### ADMIN-DASH-008 – CI-Nachtrag
- GitHub Actions auf Code-HEAD `051c7d87b3b5cc849bb4faff54259d9ffee639ca`:
  - TB-Server Runs `36488381133` und `36488374650`: `failure`, Job `foundation`, `runner_name = ""`, `steps = []`;
  - Android Run `36488381240`: `failure`, Job `build`, `runner_name = ""`, `steps = []`.
- Bewertung gemäß Projektregel: **keine Runner-Steps ausgeführt → kein Build-/Testnachweis und kein Sourcefehler-Nachweis**.
- ADMIN-DASH-008 bleibt **SOURCE FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**.

### PDF-RUNTIME-009 – Vergleich 22/25/26
- 22.09: Android/Skia-PDF, optisches BTB-Layout korrekt.
- 25.09: ReportLab Server-PDF, `tb-server-btb-pdf-3`, optisch abweichend.
- 26.09: ReportLab Server-PDF, `tb-server-btb-pdf-3`, optisch abweichend.
- Damit ist die Regression reproduzierbar auf den Wechsel des PDF-Renderpfads eingegrenzt.
- Aktueller Android-Entwurf laut Benutzer weiterhin korrekt.
- Status: **URSACHE BESTÄTIGT / SERVER-FIX + RUNTIME-VISUAL-E2E OFFEN**.

### PDF-RUNTIME-009 – Source-Fix v5
- Code-HEAD `d2c3ca9e046abdc3f176e16a96148dfda2260dca` enthält den Android-nahen Serverrenderer `tb-server-btb-pdf-5`.
- Lokaler synthetischer Visual-Smoke der Rendererlogik: gültiges A4-PDF, Android-nahe Hauptstruktur sichtbar.
- GitHub Actions: TB-Server `36551663700`/`36551656529` und Android `36551663628` jeweils ohne Runner-Schritte (`steps=[]`) → **kein CI-Testnachweis**.
- Kein Synology-Runtime-/E2E-Nachweis für Renderer v5 vorhanden.
- Pflichttest nach Upload/API-Neustart:
  1. neuen Test-/Tages-BTB serverseitig abschließen;
  2. Seite 1 gegen 22.09 und aktuellen Handy-Entwurf vergleichen;
  3. Wetter, Personal/Maschinen, Punkte 4–8 und Unterbaustellen/KM prüfen;
  4. Fotoanhang und Bildnummern prüfen;
  5. Logo-Größe/X/Y sowie Speichern/Großansicht zusammen mit ADMIN-PDF-007 prüfen;
  6. erst nach sichtbarer Praxisbestätigung auf BESTÄTIGT/ERLEDIGT anheben.
- Status: **SOURCE-FIX FERTIG / DEPLOY + VISUAL-E2E OFFEN**.

### ADMIN-PDF-010 – Source-Status
- Admin-Preview und Finalrenderer teilen jetzt denselben Android-nahen Renderpfad.
- Source-Diff geprüft: Preview erzeugt `preview_snapshot` und ruft `render_android_style_btb_pdf()` auf.
- Personal/Maschinen-Höhen getrennt source-seitig umgesetzt.
- GitHub Actions auf `972017bf...` ohne Runner-Schritte → **kein CI-Testnachweis**.
- Pflichttest auf Synology:
  1. Admin → Firma → Logo/PDF öffnen;
  2. Vorschau muss strukturell Bild 2 entsprechen;
  3. Logo-Breite/X/Y ändern und Vorschau beobachten;
  4. Speichern, Großansicht und finalen BTB-Abschluss vergleichen.
- Status: **SOURCE-FIX FERTIG / RUNTIME OFFEN**.

### ADMIN-PDF-011 – Source-Status
- Screenshotfehler `Y-Versatz wirkt nicht` source-seitig auf die alte Geometrie-Klemmung eingegrenzt und korrigiert.
- Server/API/Admin-Maxwert 400 pt source-seitig vorhanden.
- GitHub Actions ohne Runner-Steps → **kein CI-Testnachweis**.
- Runtime-Pflichttest:
  1. Breite 250 und 400 vergleichen;
  2. bei identischer Breite Y = -60, 0, +60 prüfen;
  3. X weiterhin prüfen;
  4. `PDF-Layout speichern`, Seite neu laden und Persistenz prüfen;
  5. Großansicht und serverseitigen Abschluss prüfen.
- Status: **SOURCE-FIX FERTIG / SYNLOGY-E2E OFFEN**.

### ADMIN-PDF-012 – Source-Status
- Source-Diff bestätigt: dynamisches Nach-unten-Schieben über `company_header_bottom` entfernt.
- Sourcecheck enthält Marker `Firmenlogo und Fachlayout sind absichtlich entkoppelt`.
- GitHub Actions `36557039970` ohne Runner/Steps → **kein CI-Testnachweis**.
- Runtime-Pflichttest auf Synology:
  1. Vorschau mit Breite 90/250/400 öffnen;
  2. jeweils Y -60/0/+60 testen;
  3. Wetteroberkante und Tabellenposition müssen unverändert bleiben;
  4. finalen BTB-Abschluss ebenfalls prüfen.
- Status: **SOURCE-FIX FERTIG / SYNLOGY-E2E OFFEN**.

### ADMIN-PDF-013 – Source-Status
- Source-Diff bestätigt: Titel-Layer wird nach Logo gerendert.
- Sourcecheck enthält Marker `Der Dokumenttitel liegt absichtlich als oberste Ebene`.
- GitHub Actions `36558072863` ohne Runner/Steps → **kein CI-Testnachweis**.
- Runtime-Pflichttest:
  1. Logo 400 pt;
  2. X/Y so einstellen, dass Logo in Titelbereich hineinragt;
  3. `BAUTAGEBUCH` muss vollständig sichtbar bleiben;
  4. Wetter/Tabellenposition muss unverändert bleiben.
- Status: **SOURCE-FIX FERTIG / SYNLOGY-E2E OFFEN**.

### PDF-ADMIN – Synology-Abnahme 29.09.2026
- Tatsächlicher Runtime-Test im Synology-Admin-PDF-Pfad wurde durch Benutzer durchgeführt.
- Praktisch bestätigt: Android-nahe Vorschau, 400-pt-Logo, X/Y, feste Tabellenposition, Titel-Schutzlayer.
- Benutzerurteil: **`passt und abgenommen`**.
- ADMIN-PDF-007 / -010 / -012 / -013: **BESTÄTIGT / ERLEDIGT**.
- ADMIN-PDF-011 SERVER-Anteil: **BESTÄTIGT**; Android 250→400 bleibt offen.
- Dieser Nachweis gilt für Admin-PDF/Preview/Logo-Layout; er ist **nicht** automatisch ein vollständiger Finalize-/Download-E2E für PDF-RUNTIME-009.

### REF-DATA-001 – Phase-1 Source-/E2E-Status
- Source vorhanden: Global-/Worksite-Scope, globaler Admin-Import, Manifest, Parsed-Endpoint, Capability.
- DB-Migration nicht erforderlich; bestehende nullable Company-ID wird als Scope benutzt.
- Sourcecheck wurde erweitert, aber mangels tatsächlich ausgeführtem Runner nicht als bestanden gewertet.
- GitHub Actions `36561890727` / `36561884228` / `36561890760`: `failure` mit `runner_name=""`, `steps=[]` → **kein Testnachweis**.
- Runtime-Pflichttest nach Deployment:
  1. Admin → Trassierungsdaten: Scope-Auswahl sichtbar;
  2. kleine TRA/KF-Testdatei als `GLOBAL_REFERENCE` importieren;
  3. Liste zeigt `GLOBAL`, Parserstatus plausibel;
  4. derselbe unveränderte Upload wird als globales Duplikat erkannt;
  5. Firma/Baustelle-Import bleibt weiterhin firmenspezifisch;
  6. `GET /api/v1/reference/survey-originals` liefert das globale Manifest;
  7. `/parsed` liefert Parserpayload; fremde firmenspezifische Originale bleiben geschützt.
- Status: **SOURCE FERTIG / SYNLOGY-RUNTIME-E2E OFFEN**.

### REF-DATA-001/002 – Phase-A Source-/E2E-Status
- Source vorhanden: staged Analyze-API, Confirm-Import, normalisierte Geometrie, SVG-Vorschau, KM-/Stationsbereiche, per-Datei-Zielzuweisung.
- Sourcecheck enthält AST-Prüfung für `admin_survey_import.py` und `survey_preview.py` sowie UI/API-Marker.
- GitHub Actions `36576031521` / `36576022515` / `36576031503`: failure ohne Runner (`runner_name=""`, `steps=[]`) → **kein CI-Testnachweis**.
- Runtime-Pflichttest:
  1. Admin → Trassierungsdaten: Seite beginnt mit `Dateien auswählen`, kein Importziel davor;
  2. TRA auswählen → `Dateien analysieren` → Linie muss grafisch erscheinen;
  3. KF/KOR/CSV mit XY → Punkte müssen in derselben Grafik erscheinen;
  4. Strecke/KM/Punktzahl/Warnungen plausibel prüfen;
  5. vor Import Scope auf Global/Firma wechselbar und Baustelle erst dann relevant;
  6. Import starten → Ergebnis + Liste prüfen;
  7. ZIP-Test: Einträge getrennt analysiert und getrennt bestätigbar;
  8. Analyse ohne Import darf kein Original in DB/Liste erzeugen.
- Status: **SOURCE FERTIG / SYNLOGY-RUNTIME-E2E OFFEN**.

### REF-DATA-002 – Pasing-Reteststatus
- Erster Phase-A-Runtime-Test: **FEHLER BESTÄTIGT**; Darstellung mit gefüllten Flächen/unstrukturierter Dateifarben-Legende verworfen.
- Ursache source-seitig bestimmt und korrigiert.
- V8-Syntaxprüfung des finalen Admin-JavaScripts: **PASS** (Sourceprüfung, kein Synology-E2E).
- Neuer Runtime-Pflichttest mit `Pasing (1).zip`:
  1. ZIP analysieren, noch nicht importieren;
  2. Summary muss Strecken 5524/5528/5529 zeigen;
  3. Bahnhofsgleise 12/21/23/25/26/27/713/714/715/725 logisch gruppiert;
  4. WVB 10-11/14-22/19-20 logisch gruppiert;
  5. Grafik darf keine gefüllten Dreiecke/Flächen enthalten;
  6. Achsen sind dünne Linien mit Labels; TRA+GRA grün, TRA ohne GRA rot, WVB blau gestrichelt;
  7. Festpunkte klein separat; über `Anzeige` ein-/ausblendbar;
  8. TRA+GRA derselben Achse erscheinen als eine Importgruppe.
- Status: **SOURCE KORRIGIERT / SYNLOGY-RETEST OFFEN**.

### REF-DATA-002 – Großansicht/Punktlabel Retest
- Vorheriger Pasing-Retest bestätigt:
  - Linien/Objektgruppen grundsätzlich richtig;
  - Achsenlabels zu groß/überlagert;
  - gelbe Punktmarker ohne sichtbare Punktdaten.
- Sourcefix vorhanden; Browser-JS Syntaxprüfung PASS.
- Synology-Pflichttest:
  1. Pasing.zip analysieren;
  2. `Großansicht` öffnen;
  3. bei Gesamtansicht nur begrenzte, lesbare Achsenlabels;
  4. hineinzoomen: gelbe Punkte erhalten Punktbezeichnung; stärker hineinzoomen: zusätzlich KM;
  5. Punkt anklicken: Strecke/Gleis/Höhe/ID/Quelle soweit vorhanden prüfen;
  6. weiterhin keine gefüllten Flächen.
- GitHub CI `36586687636` ohne Runner/Steps → **kein Testnachweis**.
- Status: **SOURCE KORRIGIERT / SYNLOGY-RETEST OFFEN**.

### REF-DATA-002 – Tiefzoom/KM-Retest
- Sourceprüfung: Browser-JavaScript V8 = PASS.
- GitHub Actions auf Code-HEAD ohne Runner/Steps → **kein CI-Testnachweis**.
- Synology-Pflichttest:
  1. Pasing oder 5300 analysieren;
  2. Großansicht öffnen und bis nahe an einzelne Gleislinie hineinzoomen;
  3. gelbe Marker dürfen nicht größer werden; bei tiefem Zoom müssen sie kleiner/ruhiger wirken;
  4. Punkttexte dürfen Geometrie nicht als Textwolke verdecken;
  5. Punkt anklicken → Detailanzeige bleibt nutzbar;
  6. 5300: echter Bereich `54+354.296–60+855.530` bleibt sichtbar;
  7. `0+000–0+000` darf nicht als gültiger Balken erscheinen.
- Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**.

### REF-DATA-002 – Screen-Space-Punktinteraktion Retest
- Sourceprüfung: finales Admin-JavaScript V8 = PASS.
- GitHub Actions ohne Runner/Steps → kein CI-Testnachweis.
- Synology-Pflichttest mit Pasing:
  1. ZIP analysieren, Großansicht öffnen;
  2. bis an einzelne Gleislinie tief hineinzoomen;
  3. gelbe Vermessungspunkte müssen sichtbar klein/stabil bleiben;
  4. bei sehr tiefem Zoom darf keine Textwolke die Linien verdecken;
  5. Maus über gelben Punkt → Kurztooltip;
  6. Klick auf Punkt → strukturierte Detaildaten;
  7. nahe/überlagerte Punkte → Auswahlliste;
  8. Punktdetail in Großansicht als schwebende Karte sichtbar.
- Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**.

### REF-DATA-002 – GVP-Freistrecke Retest
- Source: finales Admin-JavaScript kompiliert in V8 = PASS.
- GitHub Actions `36599344826`, `36599336974`, `36599344887`: `runner_name=""`, `steps=[]` → **kein CI-Testnachweis**.
- Synology-Pflichttest:
  1. Pasing/5300/Gessertshausen analysieren;
  2. freie TRA-Achse mit ausreichenden GVP derselben Strecke muss GRÜN werden, auch ohne explizite KM-GRA;
  3. Achse anklicken → `KM-Quelle: GVP/Festpunkte` + Trefferdiagnose prüfen;
  4. unzureichende GVP (<4 oder >120 m) bleiben ROT;
  5. widersprüchliche Offsets (<4 robust nach ±80 m) werden GRAU;
  6. explizite KM-GRA bleibt GRÜN und hat Vorrang;
  7. Punkt anklicken → `KM aus Datei` wie Android + Bahn-KM;
  8. bis ca. 160x hineinzoomen und einzelne Linien weiter kontrollieren.
- Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**.

### REF-DATA-002 – Achsen/KOR-Retest
- Source: Browser-JS V8 = PASS.
- GitHub Actions ohne Runner/Steps → kein CI-Testnachweis.
- Synology-Pflichttest:
  1. Gessertshausen analysieren;
  2. Maus über rote/grüne/blaue Achse → Kurztooltip + sichtbare Hervorhebung;
  3. Achse anklicken → strukturierte Achsendetails;
  4. bis weit über 160x hineinzoomen und einzelne Parallelachsen trennen;
  5. Punkte bleiben Screen-Space-stabil;
  6. KOR-Warnungen prüfen: reine Header-/Steuerzeilen dürfen nicht mehr als Datenfehler erscheinen;
  7. verbleibende KOR-Warnung muss jetzt den betreffenden Zeilentext zeigen und darf nicht blind unterdrückt werden.
- Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**.

### REF-DATA-002 – Achsen/KOR-Retest bestätigt
- Synology-Runtime vom Benutzer am 29.09.2026 mit `okay,passt` bestätigt.
- Status des Teilblocks: **BESTÄTIGT**.
- Sourcecheck bleibt zusätzlich PASS; GitHub-Actions ohne Runner/Steps bleiben weiterhin kein eigener Testnachweis.
- Kein Anspruch, dass damit sämtliche übrigen REF-DATA-002-E2E-Fälle abgeschlossen sind.

### REF-DATA-003 – Survey-Katalog Server-E2E
Source-Stand: **FERTIG** auf `782e72e18790570066b40da08a80471fa14d759e`.  
Deployment: **OFFEN**.  
Synology-E2E: **OFFEN**.

Sourceprüfung:
- abschließender GitHub-Strukturaudit: 25/25 geprüfte Vertrags-/Integrationsmarker PASS;
- Check 112/113 sind im Quellstand verdrahtet;
- Actions Runs `36621416472` + `36621409361`: `steps=[]` → **kein Testnachweis**.

Pflicht-Runtimefälle vor Portal:
1. Migration 017 erfolgreich angewendet und Migrationsstatus sauber.
2. API + Worker starten mit neuem Katalogmodul/Capabilities.
3. Reindex vorhandener geparster Originale läuft ohne Fehler.
4. Suche nach Strecke und Betriebsstelle liefert Katalogbestand.
5. Availability zeigt Datenarten, Teilbestand und KM-Qualität korrekt.
6. Geometry liefert aktive zusammengesetzte Segmente und redigiert fremde Bearbeiter/Firma für Normalnutzer.
7. Neue TRA mit eindeutiger Überlappung verlängert/ergänzt vorhandene Achse.
8. Teilüberdeckung lässt nicht abgedeckten Alt-/Manuellbereich aktiv.
9. TRA ohne sichere Gleis-/Richtungskennung kann bei eindeutigem X/Y-Match zugeordnet werden.
10. Mehrdeutige/abweichende Geometrie erzeugt `REVIEW_REQUIRED`, bestehende aktive Geometrie bleibt nutzbar.
11. Konflikt erzeugt Background-Mailjob; Mailweg bzw. definierter Skip-Status wird dokumentiert.
12. Adminentscheidung `KEEP_EXISTING / ACCEPT_CANDIDATE / KEEP_BOTH / CONNECT` versioniert den Katalog nachvollziehbar.
13. Mobile/Portal-ähnlicher Originalupload übernimmt fehlende Strecke/Betriebsstelle aus Baustelle.
14. Bytegleiches GLOBAL-Original wird als bereits verfügbar erkannt, ohne Fremdfirmen-Leak.
15. Change-Feed-`next_after` schreitet auch über unsichtbare COMPANY-Änderungen fort.
16. Staging/Test-Firmen-Purge bleibt mit Migration 017 lauffähig; Produktionsschutz für Originale bleibt bestehen.

Erst nach diesen Runtimefällen darf REF-DATA-003 als **DEPLOYT/E2E BESTÄTIGT** hochgestuft und der Portalblock begonnen werden.

### REF-DATA-003 – Runtimebasis bestätigt 30.09.2026
Benutzer liefert Admin-Systemstatus als Synology-Runtimebeleg:
- Migration 017 angewendet: **17/17**, keine ausstehenden Migrationen;
- DB OK;
- interne API OK;
- Worker OK;
- öffentliche API/Cloudflare OK.

Status:
- **DEPLOYT: JA** für Serverbasis/Migration017/API/Worker.
- **E2E BESTÄTIGT: NEIN** für Survey-Katalogfachpfade.
- Noch offen: Reindex + Runtimefälle 4–16 aus dem REF-DATA-003-Pflichttest.

### REF-DATA-003 – Runtimefall 3 Reindex BESTÄTIGT
Ausgabe auf Synology:
`{'original_count': 54, 'statuses': {'ADDED': 31, 'INDEXED_ASSET': 22, 'MERGED': 1}, 'failures': 0}`

Bewertung:
- Runtimefall 3 **BESTÄTIGT**;
- 54 Bestandsoriginale verarbeitet;
- 0 Fehler;
- 31 hinzugefügt, 22 als Asset katalogisiert, 1 automatisch gemerged.
- Der einzelne Merge ist noch inhaltlich zu identifizieren und fachlich zu plausibilisieren.

Offen bleiben Runtimefälle 4–16.

### REF-DATA-003 – Auto-Merge Runtimefund
Read-only-DB-Abfrage:
- 31 `PRIMARY/ACTIVE`;
- 1 `OVERLAP_CONFIRMED/ACTIVE`;
- Merge betrifft `Str 5524-1`, Quelle `5524_SL_ER0_Gp2026_02_16.TRA`, Station ca. 1400.0–6121.93415.
Status:
- technische Merge-Funktion in Runtime **nachgewiesen**;
- fachliche Merge-Richtigkeit **noch offen**, bis alle Segmente derselben Achse inkl. X/Y-End-/Überlappungspunkten verglichen sind.

### REF-DATA-003 – Mergefall 5524-1 geometrisch bestätigt
Synology-Gegenvergleich der beiden Segmente derselben Achse:
- Quelle A: `5524_L_ER0_GGP2026_02_16.TRA`, PRIMARY/ACTIVE;
- Quelle B: `5524_SL_ER0_GGP2026_02_16.TRA`, OVERLAP_CONFIRMED/ACTIVE;
- beide Bestandsdatum 16.02.2026;
- `match_count = 1111`;
- Überlappung ca. `1958.686 m`;
- Medianabstand ca. `0.0135 m`;
- maximaler Abstand ca. `0.728 m`.

Bewertung: Der konkrete Auto-Merge `Str 5524-1` ist geometrisch eindeutig plausibel und gilt für den Merge-Mechanismus als **BESTÄTIGT**. Das bestätigt nicht automatisch alle zukünftigen Mergefälle; Mehrdeutigkeit/Review bleibt separat zu testen.

Zusätzlicher Befund: Bahnhofssuche kann für den aktuellen Pasing-Bestand noch nicht bestätigt werden, weil Admin-Import bisher keine Betriebsstellenzuordnung übermittelt.

### REF-DATA-003 – Testdaten-Neuimport vorgesehen
- Aktueller Reindex-/Merge-Nachweis bleibt als technischer Runtimebeleg dokumentiert.
- Für den abschließenden Fach-E2E darf der aktuelle Staging-Vermessungsbestand zurückgesetzt und mit korrekter Bahnhof/Betriebsstellen-Zuordnung neu importiert werden.
- Reset selbst ist noch nicht ausgeführt.

### REF-DATA-003 – Reset/Station-Import Runtime offen
Neue Sourcepfade:
- Survey-Testreset Preview/Delete;
- Admin-Bahnhofsauswahl und station_code/name-Payload.

Pflichttest nach Deployment:
1. Importseite zeigt Referenzsuche nach Bahnhof/RL100.
2. Auswahl Pasing liefert offiziellen Code + Namen und kann auf alle Gruppen angewendet werden.
3. Einzelgruppe kann abweichend zugeordnet bzw. bewusst ohne Bahnhof gelassen werden.
4. Reset-Preview zeigt erwartete Survey-Zählwerte, ohne etwas zu löschen.
5. Reset ist auf staging erlaubt und in production gesperrt.
6. Nach explizitem Reset sind Survey-Original/Katalog/Parserbestände leer, andere Fachbestände unverändert.
7. Neuimport Pasing speichert station_code/name in Originalmetadaten und Katalog.
8. Search/Availability nach Bahnhof liefert Pasing-Bestand.

### REF-DATA-003 – Runtimefälle Bahnhofsauswahl + Mail BESTÄTIGT
Praktischer UI-Nachweis:
1. Bahnhof/RL100-Suche: **BESTÄTIGT** mit `MPB – München-Pasing Bbf`.
2. Bulk-Zuordnung auf Fachgruppen: **BESTÄTIGT**.
3. station_code/name bei neu importierten Originalen: **BESTÄTIGT**.
4. Importlauf: **6 importiert, 34 Duplikate, 0 Fehler**.
5. Konflikt-Mailweg: **BESTÄTIGT** durch sichtbaren Eingang `TB-Helfer: Trassendaten-Prüffall MPB – Pasing Bbf`.

Nicht als Fehler zu werten:
- Die 34 Duplikate behalten ihre alten Metadaten, weil Originale unverändert bleiben.

Nächster E2E-Schritt:
- Reset-Preview;
- Staging-Survey-Testreset;
- vollständiger Neuimport Pasing mit MPB;
- danach Search / Availability / Geometry / Changes erneut prüfen.

### REF-DATA-003 – Reset-Preview BESTÄTIGT
Staging-Vorschau praktisch ausgeführt:
`allowed=True`, Bestätigungstext korrekt.
Zähler: 60 Originale / 60 Parser / 60 Assets / 33 Achsen / 35 Segmente / 1 Konflikt / 164 History / 0 Survey-Sections / 0 Track-Source-Links / 0 Import-File-Rows.
Status: Preview **BESTÄTIGT**, eigentlicher Reset **NOCH NICHT AUSGEFÜHRT**.

### REF-DATA-003 – Reset Runtime BESTÄTIGT
Praktischer Synology-Nachweis:
- `status=RESET`
- `removed_storage_keys=60`
- alle Survey-Zähler unter `after` = 0.

Damit ist der geschützte Staging-Reset-Endpunkt **BESTÄTIGT**.

### REF-DATA-003 – Prefill/WVB/Multi-Hit/Problemfenster Runtime-Gate
Source: **FERTIG** auf `5604ece1...`. Deployment/E2E: **OFFEN**.

Pflichttests nach Deployment:
1. Systemstatus zeigt Migration **18/18**, keine ausstehenden Migrationen.
2. Pasing-Analyse: erkannte Strecke/Gleis bleiben sichtbar im Decision-Feld.
3. W 27 wird als `W:27`, WVB 14-22 als `WVB:14-22` erkannt.
4. WVB 10-11, 14-22, 19-20 bleiben bei gleichem Bahnhof fachlich getrennte Objekte; keine falsche Conflict-Mail nur wegen Nähe.
5. Echte widersprüchliche Daten desselben object_key erzeugen weiterhin REVIEW_REQUIRED.
6. Zwei überlagerte TRA an derselben Klickstelle erzeugen Multi-Hit-Auswahl und beide Achsen sind auswählbar.
7. Offener Prüffall erscheint in „Offene Trassendaten-Prüffälle“.
8. „Problem ansehen“ zeigt neue/vorhandene Quelle, Erklärung, Messwerte und beide Geometrien.
9. Eine Test-Resolution wird versioniert gespeichert und der offene Prüffall verschwindet entsprechend.
10. Danach Search/Availability/Geometry für MPB erneut prüfen.

Erst nach diesem Gate: eigenständige `Trassendatenübersicht` bauen.

### REF-DATA-003 – Parser-Cache-Gate
Vor Runtime-E2E muss der deployte Parser `tb-survey-parser-4` melden. Bestehende v3-Parse-Caches mit gleichem SHA gelten für object_key als Altbestand und müssen neu ausgewertet/reindiziert werden.

### REF-DATA-003 – Upgrade-E2E zusätzlich
Nach Deployment muss ein bereits vor v4 importiertes WVB-Original durch Reindex auf Parser v4 gebracht werden und seine bestehende Achse den passenden `object_key` erhalten, **ohne** ein zweites Segment zu erzeugen.

### REF-DATA-003 – Parser-v4-/Upgrade-Gate nach Deployment
Zusätzlich zu den bestehenden 10 Pasing-Fällen prüfen:
1. deployter Parser meldet `tb-survey-parser-4`;
2. ein alter v3-Parsebestand wird beim Reindex tatsächlich neu ausgewertet (`reparsed > 0`, sofern Altbestand vorhanden);
3. ein bereits katalogisiertes WVB-Original erzeugt **kein zweites Segment**;
4. die bestehende Achse erhält bei fehlender Identität `object_key`, Ergebnis `ALREADY_INDEXED_ENRICHED`;
5. vorhandener object_key/Admin-Korrektur bleibt unverändert;
6. Reindex endet mit `failures = 0`.

Diese Punkte sind Source-verifiziert, aber erst nach Synology-Lauf ein Runtime-/E2E-Nachweis.

### REF-DATA-003 – Runtimebasis 18/18 + v4 bestätigt, Reindex noch ROT
- Migration 18/18: **BESTÄTIGT**.
- Parser `tb-survey-parser-4`: **BESTÄTIGT**.
- Reparse alter Bestände: **BESTÄTIGT** (`reparsed=40`).
- Reindex `failures=0`: **NICHT BESTÄTIGT**; aktuell `failures=3`.
- Deshalb noch keine Freigabe für Pasing-Fach-E2E/Trassendatenübersicht.
- Nächster Test: drei fehlgeschlagene Originale mit konkreter Exception identifizieren.

### REF-DATA-003 – Reindex failure Ursache BESTÄTIGT
- 3/3 Fehler reproduziert und identisch.
- Exception: `CheckViolation`.
- Constraint: `survey_catalog_history_operation_check`.
- Auslöser: History-Operation `ENRICH`.
- Betroffene Dateien: WVB 10-11, WVB 14-22, WVB 19-20.
- Damit ist die Ursache **BESTÄTIGT**; Runtime-Fix noch offen.

### REF-DATA-003 – Migration019 Runtime-Gate
Nach Deployment prüfen:
1. Systemstatus Migrationen **19 / 19**, pending leer;
2. Reindex erneut ausführen;
3. Ziel: `failures = 0`;
4. für die drei alten WVB-Achsen erwartbar: `ALREADY_INDEXED_ENRICHED`;
5. danach WVB object_keys und Pasing-Fachtests fortsetzen.

## REF-DATA-003 – Reindex nach ENRICH-Fix runtime-grün 30.09.2026
- Synology/API-Terminal: `reindex_all_originals()` erneut ausgeführt.
- Ergebnis: `original_count=40`, `parser_version=tb-survey-parser-4`, `reparsed=0`.
- Statusverteilung: `ALREADY_INDEXED=19`, `INDEXED_ASSET=19`, `ALREADY_INDEXED_ENRICHED=2`.
- `failures=0`: der zuvor reproduzierte ENRICH-Constraint-Fehler ist im laufenden DB-Pfad behoben.
- Die 40 Originale sind vollständig bilanziert; kein Reindex-Fehler mehr offen.
- Abweichung zur Voraberwartung: nur 2 statt 3 WVB wurden in diesem Lauf als `ALREADY_INDEXED_ENRICHED` gemeldet. Vor weiterer Fachfreigabe read-only feststellen, welcher der drei WVB-Fälle bereits als `ALREADY_INDEXED` läuft und welche `object_key` die drei vorhandenen Achsen tatsächlich tragen.
- Der vorliegende Screenshot zeigt nicht den Systemstatus `19/19`; Migration 019 deshalb in der Dokumentation erst nach separatem Systemstatus-/Migrationstabellen-Nachweis als 19/19 bestätigen.
- Nächster Runtime-Schritt: WVB 10-11 / 14-22 / 19-20 mit Originalname, Achse, `object_key` und Segmentzahl gegenüberstellen; danach Pasing-Prefill, Multi-Hit und „Problem ansehen“ praktisch testen.


### REF-DATA-003 – Legacy-WVB-Identitätsreparatur Runtime-Gate 30.09.2026
Sourcefix: **VORHANDEN**, Runtime/E2E: **OFFEN**.

Bereits praktisch bestätigt:
- Migration 019: **19/19**, pending=[];
- Reindex vor Reparatur: failures=0;
- WVB 10-11 = `WVB:10-11`;
- WVB 14-22 = `WVB:14-22`;
- Altfehler reproduziert: WVB 19-20-Segment liegt fälschlich auf derselben Achse wie WVB 14-22.

Pflichttest nach Source-Deploy:
1. Reindex endet mit `original_count=40`, Parser v4 und `failures=0`.
2. Erwarteter Reparaturstatus enthält für den Altfall `ALREADY_INDEXED_REASSIGNED`.
3. WVB 10-11, 14-22 und 19-20 haben anschließend jeweils ihren passenden `object_key` und getrennte Achsen-IDs.
4. Für WVB 19-20 bleibt die bestehende Segment-ID erhalten; kein zweites ORIGINAL_TRA-Segment entsteht.
5. Raw-Originale bleiben unverändert.
6. Falls die Reparatur wegen eines OPEN-Conflicts bewusst nicht ausführt, zuerst diesen Konflikt read-only nachweisen; Schutz nicht auf Verdacht lockern.
7. Erst nach erfolgreichem WVB-Gate Pasing-Prefill, Multi-Hit, „Problem ansehen“ sowie Search/Availability/Geometry weiterprüfen.

GitHub Actions zum Code-HEAD: Foundation-Job mit `steps=[]`; damit **kein Testnachweis** trotz Workflow-conclusion=failure.

### REF-DATA-003 – Source-Check Reparaturguard
- Isolierter Guard-Logiktest: **4/4 PASS**.
- Einstufung: **SOURCE-CHECK**, ausdrücklich **kein Runtime-/DB-/Synology-E2E**. Das oben definierte Runtime-Gate bleibt vollständig offen.

### REF-DATA-003 – Legacy-WVB-Identitätsreparatur Runtime-Gate BESTANDEN 30.09.2026
- Reindex: `original_count=64`, Parser v4, `reparsed=0`, `ALREADY_INDEXED_REASSIGNED=1`, `failures=0`.
- WVB 10-11 / 14-22 / 19-20 besitzen danach getrennte Achsen und jeweils den passenden object_key; jede der drei TRA-Achsen hat `axis_segment_count=1`.
- Der konkrete Legacy-Mergefehler gilt damit als **BESTÄTIGT BEHOBEN**.
- Hinweis: aktueller Runtimebestand umfasst 64 Originale statt 40; die Bilanz ist vollständig und fehlerfrei. Keine Rückstufung des Gates.
- Offen im REF-DATA-003-Gesamtgate: Browser-Prefill, Multi-Hit, „Problem ansehen“/Resolution sowie Search/Availability/Geometry für MPB/Pasing.

### REF-DATA-003 – Import-UI/Reset Runtime-Gate
Source: **FERTIG**, Runtime: **OFFEN**.
Nach Deploy von `v1_admin_survey_import.html` prüfen:
1. Label heißt `Speicherziel` und GLOBAL heißt `Gemeinsamer Referenzbestand`.
2. Bei GLOBAL ist `Firma / Baustelle` nicht sichtbar; beim Umschalten auf Firmenziel erscheint das Feld wieder.
3. Auf staging erscheint die Reset-Karte mit aktuellen Survey-Zählwerten.
4. Ohne exakten Bestätigungstext bleibt Löschen gesperrt.
5. Reset liefert `status=RESET`; danach alle Survey-Reset-Zähler 0.
6. Firmen, Benutzer, Baustellen und BTB bleiben unverändert.
7. Anschließend Pasing-Daten frisch hochladen und den kompletten Analyse-/Import-/Katalogpfad erneut testen.

### REF-DATA-003 – Zentralimport + Terminal-Reset Runtime-Gate
Source: **FERTIG**. Runtime: **OFFEN**.
Pflichttest:
1. Drei Runtime-Dateien deployen und API neu erstellen/starten.
2. Terminal-Preview zeigt staging/test und aktuellen Survey-Bestand.
3. Terminal-Reset mit exaktem Text liefert `status=RESET`; danach alle Survey-Zähler 0.
4. Separater Strecken-/Bahnhofs-Referenzkatalog, Firmen, Benutzer, Geräte, Baustellen und BTB bleiben bestehen.
5. Admin-Trassierungsanalyse zeigt keine Speicherziel-/Firma-Auswahl mehr.
6. Dashboard zeigt unter `Speicher nach Firma` keine Trassierungsdaten-Kategorie mehr.
7. Pasing/MPB vollständig frisch hochladen; danach Prefill, W/WVB object_key, Multi-Hit, Prüffallfenster sowie Search/Availability/Geometry erneut prüfen.

### REF-DATA-003 – Zentralimport + Terminal-Reset Runtime-Gate TEILWEISE BESTANDEN 30.09.2026
**Bestanden:**
- Terminal-Helper läuft in `staging`.
- Preview vor Reset zeigte den aktuellen Bestand vollständig.
- Reset mit exaktem Bestätigungstext erfolgreich: `status=RESET`, 64 Storage-Originale entfernt.
- Alle Survey-Zähler nach Reset = 0, durch zweite Preview separat bestätigt.

**Noch offen:**
- Admin-Trassierungsseite visuell prüfen: keine Speicherziel-/Firma-Auswahl mehr.
- Dashboard visuell prüfen: keine Trassierungsdaten-Kategorie unter `Speicher nach Firma` mehr.
- Pasing/MPB vollständig frisch importieren und danach Prefill, W/WVB object_key, Multi-Hit, Prüffallfenster sowie Search/Availability/Geometry erneut E2E prüfen.

### REF-DATA-003 – Zentralimport UI-Runtime 30.09.2026
**BESTANDEN:**
- Speicherziel/Firma-Auswahl auf der zentralen Admin-Trassierungsseite nicht mehr vorhanden.
- Frische Analyse nach Reset zeigt erkannte Strecke/Gleis-Vorbelegung (u. a. 5528/714, 5528/715, 5528/725).
- `Zuletzt gespeicherte Originale` ist vor dem Neuimport leer.

**Noch offen:**
- MPB/Betriebsstelle auf die Pasing-Gruppen anwenden.
- Import tatsächlich ausführen und Resultate prüfen.
- Danach W/WVB object_key, Multi-Hit, ggf. Prüffälle/Problemfenster sowie Search/Availability/Geometry E2E prüfen.

### REF-DATA-003 – Frischer Pasing/MPB-Import Gate BESTANDEN 30.09.2026
- Vorbedingung: Survey-Testbestand vollständig 0.
- Danach frischer Import derselben Pasing-Daten: **40/40 importiert, 0 Duplikate, 0 übersprungen, 0 Fehler**.
- GLOBAL + MPB-Zuordnung sichtbar bestätigt.
- WVB 10-11 / 14-22 / 19-20 im Import enthalten.
- Aktuell 0 offene Trassendaten-Prüffälle.
- Originaltabelle zeigt die neuen Datensätze nach Listenaktualisierung.

Noch offen:
1. WVB 10-11 / 14-22 / 19-20 nach frischem Import read-only auf getrennte axis_id + passenden object_key + segment_count=1 prüfen.
2. Search nach MPB/5528/WVB und Availability für MPB/Route testen.
3. Geometry für MPB/5528 laden und Multi-Hit praktisch prüfen.
4. Problemfenster nur dann E2E testen, wenn ein echter REVIEW_REQUIRED-Fall entsteht; aktuell kein offener Prüffall vorhanden.

### REF-DATA-003 – Frischer WVB-Identitätscheck 30.09.2026
- WVB 10-11: **BESTANDEN** – eigener axis_id, `WVB:10-11`, MPB, Segmentzahl 1.
- WVB 14-22: **BESTANDEN** – eigener axis_id, `WVB:14-22`, MPB, Segmentzahl 1.
- WVB 19-20: **NOCH OFFEN**, weil die betreffende Zeile im Screenshot nicht sichtbar ist.
- Wichtig: Dieser Nachweis stammt direkt aus dem frischen Importzustand, ohne Reindex-Nachreparatur.

### REF-DATA-003 – Frischer WVB-Identitätscheck VOLLSTÄNDIG BESTANDEN 30.09.2026
- WVB 10-11: PASS – eigener axis_id, `WVB:10-11`, MPB, Segmentzahl 1.
- WVB 14-22: PASS – eigener axis_id, `WVB:14-22`, MPB, Segmentzahl 1.
- WVB 19-20: PASS – eigener axis_id, `WVB:19-20`, MPB, Segmentzahl 1.
- Alle drei Achsen-IDs sind verschieden.
- Nachweis direkt nach frischem Import, ohne Reindex/Reassign.
- WVB-Neuimport-Gate damit abgeschlossen. Offen bleiben Search/Availability/Geometry und Portal-Multi-Hit; Problemfenster nur bei realem REVIEW_REQUIRED-Fall.

### REF-DATA-003 – MPB Catalog API Gate 30.09.2026
**BESTANDEN:**
- Search MPB: 20 Achsen, Station MPB, 40 Assets, WVB sichtbar.
- Availability MPB: AVAILABLE, 20 Achsen, 21 aktive Segmente, 0 Review.
- Geometry MPB: 21 Segmente mit Punktgeometrie, einschließlich WVB 10-11/14-22/19-20.

**NICHT ALS FACHLICH FERTIG WERTEN:**
- KM-Qualität: 17 Segmente `FEHLT`, nur 4 `AUTOMATISCH`.
- 20 Achsen / 21 Segmente: mindestens eine Achse besitzt zwei aktive Segmente; zunächst read-only Ursache/Quellen prüfen.
- Portal-Multi-Hit nach frischem Import noch praktisch offen.

### REF-DATA-003 – Pasing-KM-Gate fachlich angepasst 30.09.2026
- `km_quality=FEHLT` bei Pasing ist **erwartet**, solange der Benutzer noch keinen manuellen Start-/Endabschnitt mit KM-Ankern gesetzt hat.
- Ein grüner Automatikstatus ist für diese Daten **kein Pflichtkriterium**.
- E2E für Pasing-KM wird erst nach Android-Manuellabschnitt geprüft: Abschnitt anlegen → synchronisieren → serverseitig als MANUELL/ORANGE sichtbar → auf anderem Gerät abrufbar.
- Aktueller Search/Availability/Geometry-Test bleibt deshalb trotz 17× FEHLT gültig; der KM-Teil ist fachlich offen, nicht technisch fehlgeschlagen.

### REF-DATA-005 – Korrektur-Reimport E2E-Gate
Noch offen. Soll nach Umsetzung bestätigen:
- identische Datei + identische Zuordnung → `DUPLIKAT`, keine Änderung;
- identische Datei + geänderte Betriebsstelle/Strecke/Gleis/object_key → deutliche Warnung, keine automatische Änderung;
- bestätigte Aktualisierung ändert genau das bestehende Original, erzeugt kein zweites Original und baut die abgeleitete Katalogzuordnung konsistent neu auf;
- Raw-Datei/sha256 bleiben unverändert;
- Audit/History nachvollziehbar;
- Search/Availability/Geometry zeigen danach nur die neue fachliche Zuordnung.

### REF-DATA-005 – Duplicate-Korrektur Runtime-Gate
Source: **FERTIG**, Runtime: **OFFEN**.
Pflichttest nach Deploy:
1. sauberen Testbestand importieren;
2. dieselbe ZIP unverändert mit identischer Zuordnung erneut senden → nur DUPLIKAT, keine Änderung;
3. dieselbe ZIP mit geänderter/ergänzter Betriebsstelle erneut senden → zuerst VORHANDEN_ABWEICHEND + Vorher→Nachher, noch keine DB-Änderung;
4. Checkbox setzen + Sicherheitsdialog bestätigen → AKTUALISIERT;
5. Original-ID, SHA256 und Raw-Datei bleiben gleich; kein zweites Original;
6. Originaltabelle + Search/Availability/Geometry zeigen die neue Zuordnung;
7. History/Audit vorhanden; keine offenen Konflikte erzeugt.

### REF-DATA-005 – Duplicate-Korrektur Runtime TEILBESTANDEN 02.10.2026
**Bestanden:**
- Abweichende vorhandene Zuordnung wird zuerst als `VORHANDEN_ABWEICHEND` angezeigt; noch keine stille Überschreibung.
- Nach bewusst gesetztem Haken + Bestätigung werden betroffene Dateien als `AKTUALISIERT` gemeldet.
- Vorher→Nachher zeigt fehlende Betriebsstelle/Bahnhof → `MGHN / Gessertshausen`.
- Mindestens eine unverändert passende Datei (`5340S_ER0.TRA`) bleibt im selben Lauf `DUPLIKAT`.

**Noch offen:**
- dieselbe Original-ID und SHA256 vor/nachher nachweisen;
- kein zweites Original erzeugt;
- Originaltabelle + Search/Availability/Geometry zeigen nur die neue Stationszuordnung;
- Audit/History für die Korrektur vorhanden;
- Warnbox-Rotrahmen nach letztem Template-Deploy visuell bestätigen.

### REF-DATA-005 – Audit-Subgate BESTANDEN 02.10.2026
- `ADMIN_SURVEY_ORIGINAL_ASSIGNMENT_UPDATE` mit Quelle `ADMIN_WEB_DUPLICATE_CORRECTION` sichtbar.
- Änderungen auf `MGHN / Gessertshausen` im Audit nachvollziehbar.
- `SURVEY_CATALOG_ASSIGNMENT_UPDATE` ebenfalls sichtbar.
- Offen bleiben nur: gleiche Original-ID/SHA + keine zweite Rohdatei nachweisen sowie MGHN Search/Availability/Geometry.

### REF-DATA-005 – Originalidentität/Eindeutigkeit BESTANDEN 02.10.2026
- `updates=16`
- `ids_exist=true`
- `sha_same=true`
- `raw_unique=true`
- `station_mghn=true`
Damit ist bestätigt: keine zweite Rohdatei/Originalzeile, bestehende IDs bleiben gültig und SHA256 bleibt unverändert.

MGHN-Geometrie liefert 12 Segmente; Availability zeigt 0 Review-Segmente sowie KM-Qualität 10× FEHLT / 2× AUTOMATISCH. Vollständige Search-/Availability-Kopfwerte noch kompakt nachreichen.

### REF-DATA-005 – Funktionales Runtime-Gate BESTANDEN 02.10.2026
- Search: 12 MGHN-Achsen, Station Gessertshausen, 16 Assets.
- Availability: AVAILABLE, 12 Achsen, 12 aktive Segmente, 0 Review.
- Geometry: 12 Segmente.
- In-place: IDs gültig, SHA gleich, Rohdatei eindeutig, station_mghn=true.
- Audit/History: Original- und Katalog-Aktualisierung nachgewiesen.
- Fachfunktion damit **BESTANDEN**.
- Nur kosmetischer Runtime-Nachweis des roten Warnrahmens bleibt offen; keine fachliche Blockade.

### REF-DATA-006 – Deutschlandübersicht Runtime-Gate
Source: **FERTIG**, Runtime: **OFFEN**.
Nach Image-Neubau/Deploy prüfen:
1. `/admin/v1/survey-overview` öffnet ohne Fehler;
2. Coverage-API liefert MPB und MGHN als WGS84-Geometrie;
3. Pasing und Gessertshausen erscheinen geografisch plausibel an unterschiedlichen Positionen in Bayern;
4. Suche nach `5302`, `Gessertshausen`, `MPB`, `302`, `WVB 14-22` findet/fokussiert Treffer;
5. Gl 302/303 in Gessertshausen erscheinen GRÜN, W/WVB ohne KM ROT; Pasing-TRA ohne belastbare KM ROT;
6. Deutschland-Gesamtbutton setzt Zoom zurück;
7. keine Original-/Katalogdaten werden durch die Übersicht verändert;
8. unsupported_coordinate_count wird bei nicht transformierbaren Koordinaten sichtbar gemeldet.

### REF-DATA-006 – Deployment BLOCKIERT 02.10.2026
- API startet nach aktuellem Synology-Versuch nicht: `ModuleNotFoundError: pyproj`.
- Quellstand/Dockerfile sind korrekt auf `pyproj==3.7.2` vorbereitet; Runtime-Image wurde noch nicht mit der neuen requirements-Datei gebaut.
- Deutschlandübersicht-E2E bleibt **OFFEN**. Kein Testnachweis, solange API nicht mit neu gebautem Image läuft.

### REF-DATA-006 – Zweiter Deploymentversuch weiterhin BLOCKIERT 02.10.2026
- NAS requirements enthält `pyproj==3.7.2`.
- Projekt Stop → Erstellen beendet Compose mit Exit Code 0, API stirbt dennoch wieder an `ModuleNotFoundError: pyproj`.
- Schlussfolgerung: vorhandenes lokales Image wurde wiederverwendet; noch kein echter Image-Neubau.
- E2E bleibt offen bis Image `tb-helfer-server-v1:local` gelöscht/neu gebaut und `import pyproj` im API-Container erfolgreich ist.

### REF-DATA-006 – Deployment weiterhin BLOCKIERT: Projektdefinition prüfen
- Nach Bereinigung fehlen api/worker/migrate/monitor vollständig aus der Synology-Projektansicht.
- Vor jedem weiteren Build muss die aktive Synology-YAML gegen GitHub `server/docker-compose.v1.yml` geprüft werden.
- Keine Runtime-/E2E-Wertung möglich.

### REF-DATA-006 – Rollback jetzt erforderlich 02.10.2026
- API-Crash erneut reproduziert: `survey_catalog.py` importiert pyproj, Runtime-Image enthält pyproj nicht.
- Deutschlandübersicht-E2E abgebrochen. Vor weiteren Arbeiten muss der Server auf den letzten pyproj-freien Source-Stand zurück und erneut gestartet werden.

### REF-DATA-006 – Rollback-Quellstand hergestellt / Runtime offen
- pyproj-abhängiger Source entfernt; kein Runtime-Testnachweis daraus ableiten.
- Jetzt Synology mit pre-pyproj Source + gesichertem Alt-Image starten und zuerst Health, Migrationen, Admin/Portal und Survey-Bestand prüfen.

### REF-DATA-006 – Rollback-Recovery Teiltest BESTANDEN 02.10.2026
- API-Terminal wieder erreichbar.
- Migrationsstatus: 19/19, keine ausstehenden Migrationen.
- Das bestätigt Quellstand/DB-Konsistenz, ist aber noch kein vollständiger Runtime-/E2E-Nachweis.
- Offen: health/ready + Worker/Monitor + Admin/Portal-Kurztest.

### REF-DATA-006 – Rollback-Recovery Teiltest BESTANDEN 02.10.2026
- API-Terminal wieder erreichbar.
- Migrationsstatus: 19/19, keine ausstehenden Migrationen.
- Das bestätigt Quellstand/DB-Konsistenz, ist aber noch kein vollständiger Runtime-/E2E-Nachweis.
- Offen: health/ready + Worker/Monitor + Admin/Portal-Kurztest.

### REF-DATA-006 – Rollback-Recovery BESTÄTIGT 02.10.2026
- API-Terminal erreichbar.
- Migrationen 19/19, pending=[].
- Health-Check: status=ok, database=up, migrations=ok, server_version=1.0.0-dev.
- Nutzer bestätigt übrige Dienste/Portal als laufend.
- Ergebnis: **Server-Runtime nach Rollback BESTÄTIGT**.
- Nicht getestet/noch offen: die zurückgerollte Deutschlandübersicht selbst; sie ist derzeit nicht Teil des aktiven Source.

### REF-DATA-006 – PostGIS-Deutschlandübersicht Runtime-Gate
Source: **FERTIG**, Runtime: **OFFEN**.
Nach Deploy der Source-Dateien prüfen:
1. /health/ready bleibt status=ok/database=up/migrations=ok;
2. /admin/v1/survey-overview öffnet ohne Fehler;
3. Coverage-API zeigt MPB/Pasing und MGHN/Gessertshausen geografisch plausibel;
4. Suche nach 5302, Gessertshausen, MPB, 302 und WVB 14-22 findet passende Achsen;
5. Auswahl lädt erst dann Detailgeometrie nach;
6. Gl 302/303 MGHN erscheinen GRÜN, W/WVB ohne KM ROT; manuelle KM später ORANGE;
7. Zoom/Pan und Deutschland-Reset funktionieren;
8. keine Original-/Katalogdaten werden verändert.
Sourcecheck ist kein Runtime-/E2E-Nachweis.

### REF-DATA-006 – Korrektur „alle Achsen sichtbar“ Runtime offen
- Source korrigiert: Gesamtansicht zeigt nicht mehr nur Center-/Stationspunkte, sondern vereinfachte Linien aller aktiven Achsen.
- Nach Deploy prüfen: Pasing/MGHN-Achsen sind bereits ohne Einzelklick als Linien vorhanden; beim Zoomen trennen sich überlagerte Gleise/W/WVB soweit geometrisch möglich.
- Detail-Lazy-Load bei Klick bleibt separat zu prüfen.

### REF-DATA-006 – Runtime-Status nach Linien-Patch: TEILWEISE
- Nutzer bestätigt grundsätzlich funktionierende Darstellung, aber noch nicht korrekt/genügend.
- Kein PASS. Nächster Schritt ist visuelle/bedienseitige Fehlerabgrenzung; erst danach gezielter Patch.

### REF-DATA-006 – Coverage Runtime FAIL 02.10.2026
- Admin-Seite lädt, Coverage-Request endet im Browser mit NetworkError; KPI bleiben leer.
- Runtime-Gate ist **NICHT BESTANDEN**.
- Vor Patch direkte read-only Coverage-Ausführung im API-Terminal nötig, um Trace/Ursache zu sichern.


## REF-DATA-007 – Trassenkennzahlen / Lazy-Übersicht
Status: **SOURCE-CHECK PASS / SYNLOGY-E2E OFFEN**
- GitHub-Source-Markercheck: 16/16 PASS für Summary-Endpunkt, Lazy-Flags, Detailgeometrie, neue KPIs und Dashboard-Loader.
- Kein Synology-Runtime-Nachweis für `/api/v1/survey-catalog/coverage/summary` vorhanden.
- Kein Browser-E2E der neuen Ladezeit/Kennzahlen vorhanden.
- Kein Handy→Server-E2E für `MANUAL_TRA_SECTION` vorhanden; Android setzt den neuen Vertrag noch nicht als First-Class-Sync um.
- E2E-Gate: Summary read-only gegen laufende Synology → Dashboard/Übersicht → später Android FERTIG-Abschnitt → Server orange/Handy-KM → zweites Gerät.


## REF-DATA-007 – Summary-Runtime PASS 02.10.2026
- Synology/API-Terminal read-only: **PASS**.
- `_coverage_summary_data()` liefert 39 Achsen / 40 aktive Segmente / 2 Betriebsstellen / 7 Strecken.
- Längen: 128.911 km gesamt, 116.579 km belastbar/automatisch, 12.332 km ohne belastbare KM, 0 km manuell/Handy.
- Segmentqualität: 12 AUTOMATISCH / 28 FEHLT / 0 UNPLAUSIBEL.
- Dies bestätigt den Summary-Runtimepfad, aber noch **nicht** Dashboard-/Browser-E2E und noch **nicht** Android→Server-`MANUAL_TRA_SECTION`.


## REF-DATA-007 – HTTP-Test mit TestClient: NICHT AUSGEFÜHRT
- FastAPI/Starlette `TestClient` konnte im Synology-API-Container nicht gestartet werden, weil `httpx` im Runtime-Image nicht installiert ist.
- Es wurde dadurch **kein HTTP-Request** gegen `/api/v1/survey-catalog/coverage/summary` ausgeführt.
- Ergebnis daher weder PASS noch FAIL für den Endpunkt.
- Keine produktive Abhängigkeit nur für Diagnose nachinstallieren.


## REF-DATA-007 – Browser-Timeout 524 am 02.10.2026
- Benutzerbeleg: /api/v1/survey-catalog/coverage/summary zeigt Cloudflare 524, Zeit 15:54:39 UTC / 17:54:39 Europe/Berlin.
- Status: **HTTP-/BROWSER-E2E FAIL / ROOT CAUSE OFFEN**. Browser und Cloudflare laut Fehlerseite erreichbar; Origin-Antwort nicht rechtzeitig erhalten. Tatsächliche Requestdauer nicht gemessen.
- Bisheriger Summary-Helper-PASS bleibt Funktionsnachweis, ist ohne Zeitmessung kein Performance-PASS.
- Nächster Nachweis: read-only Summary im API-Container mit perf_counter und faulthandler (30-Sekunden-Grenze für separaten Diagnoseprozess). Danach Befund-abhängig Berechnung/DB-Wartezeit oder HTTP/Auth/Proxy weiter eingrenzen.
- Keine Code-/NAS-/Cloudflare-/Android-Änderung und keine neue erfolgreiche Laufzeitmessung in diesem Chatblock.


## REF-DATA-007 – GVP-Performance Regressionstest 02.10.2026
- **Synology VOR Fix: FAIL** – 30-s-Diagnoseprozess endet in _project_xy_station / _segment_km_quality / _coverage_summary_data. Summary-HTTP zuvor 524.
- **Lokal NACH Fix: PASS** – Python 3.12.14, python server/checks/118_verify_gvp_projection_performance.py --benchmark: 11/11 Tests, Exit 0. Tests führen die echten reinen Produktionshelfer via AST aus, ohne Router-/DB-Import; kein kompletter API-Integrationstest.
- Vergleich gegen unveränderten vollständigen Projektionsscan: leere/ungültige/degenerierte Geometrie, Datenlücken, positive/negative/GK-Koordinaten, Zell- und 120-m-Grenzen, Überlagerungen/erste Treffer, lange Diagonalen, fallende Stationierung/Sprünge, 1.200 reproduzierbare Zufallsabfragen.
- Fachtests: GVP-Median/Ausreißer, Mindesttreffer, GRÜN/GRAU/ROT/ORANGE, GRA-/manuelle Priorität und unnötige Geometrieladung. Exakte Ergebnisgleichheit statt nur Source-Marker geprüft.
- Benchmark: 40-km-Polylinie mit 20.001 Samples und 200 GVP; alt 4,876853 s, neu inklusive Aufbau 0,053314 s (ca. 91,5x). Dieser Messwert gilt nur für diesen lokalen synthetischen Test.
- **Nach Deployment OFFEN:** realer Helper mit Zeit/Kennzahlen; authentifiziertes Summary-HTTP; sichtbare Kennzahlen im Browser; Trassendetail/Ladezeit; keine verdeckte Qualitäts-/KM-Änderung.
- Kein Synology-PASS, kein Postman-/HTTP-PASS und keine Gesamtfreigabe behaupten.


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
- Checks120–124:58 vorhandene Produktionsfunktions-/Pydantic-Tests PASS. Neu125:16 Workspace-/Reset-/Versions-/Idempotenz-/Rechte-Tests PASS; neu126:6 Originalgrenzen-/Stationssprung-/Reindex-Tests PASS. Zusammen80. Storage-Doubles, keine echte DB.
- Node119 erweitert PASS: WMS-BBOX/Opt-in/Retry, Workspace-Quellen/Anker/Vorschau/Invalidierung/CSRF/Doppelklickschutz, Resetvorschau ohne Mutation/409. DOM/Transport/Image-Doubles, keine visuellen Screenshots. Python-Syntax PASS.
- Original-ZIP read-only:5302S16497 Punkte,5302L3470,5302R3545,5340S444. 5302S war vorher leer. Keine Originale im Commit.
- Noch offen: Migration020 auf NAS; reales FastAPI/PostgreSQL/PostGIS, echte Bilder BY/SN/BW, visueller Zoom/Pan, Schreiben/Ändern/Deaktivieren mit zweitem Browser, gezieltes Reset/Restore bei unverändertem Originalhash. Chromium fehlte; Direktabrufe Landesdienste Timeout/403. Nicht als live bestätigt markieren.

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


## REF-DATA-020 – aktueller Bestand, Kilometerbezug und Weichenfläche (05.10.2026)
- Benutzerfreigabe „okay bauen“ nach fachlichem Vorschlag. Basis SERVER e5aa6cde14048c28f44fb5fe23cd7808f5465b31 / PR6 offen Draft; ANDROID423f378 / PR11 offen, unverändert. Server/Portal zuerst, Android danach.
- Benutzer bestätigt REF019: Gl999 finden, WA/WE speichern und wieder öffnen, Anzeigeauswahl behalten; außerdem Neustart und Datenbestand vorhanden. Keine pauschale Abnahme von Konflikt/Archiv/Last/Android daraus ableiten.
- Aktuelles Gleis ist das stabile Netzobjekt mit begrenztem Verlauf, Anfang/Ende und eigener Kilometerzuordnung. Originalachsen bleiben unverändert als zuschaltbare Grundlage. Bestand erhält Auswahlvorrang. Verlauf über mehrere explizite Originalabschnitte möglich, keine unbelegte Kurve aus zwei Punkten behaupten.
- Optionaler ausdrücklicher Kilometerbezug auf versionierte Originalquelle; ohne Auswahl eigene Punktbindungen verwenden, niemals aus überlagerten Achsen beliebig wählen. Bestehende manuelle Kalibrierungen und belastbare GVP-Zuordnung nutzen, reine GRA-Min/Max ohne punktbezogene Abbildung nicht als exakte Endpunktwerte ausgeben. Außerhalb belegbarer Referenz keine Extrapolation. Verschieben berechnet Werte neu; fehlende/mehrdeutige/veraltete Grundlagen sichtbar statt alter KM am neuen Punkt.
- Optional versionierte Verlaufabschnitte und Positionsstatus; ausgelassene neue Felder durch alte Clients erhalten, explizites null/leer entsprechend dokumentieren. IDs, optimistische Versionen, atomare gemeinsame Punkte, Idempotenz, Historie und Original-/KM-Schutz bleiben.
- Weichen-Auswahlfläche aus beiden Wegen WA→WE und WA→WEZ, jeweils 1,5m seitlicher Breite; gemeinsame äußere Fläche mit WA-Kante3m und aufgeweitetem Endbereich. Darstellung/Erkennung, keine betriebliche Sicherheitsgrenze. W xyz bleibt ohne KM nutzbar.
- Handy-Ziel: Originalimport→gemeinsames Gleis/Weiche→versionierter Deltaabgleich→anderer Nutzer erhält Grenzen/Verlauf/KM samt Herkunft. Keine Android-Implementierung in diesem Serverblock; vorhandene Android-Berechnung read-only vergleichen, später echte Geräte-Abnahme.


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


## REF-DATA-021 – örtliche Referenzauswahl vereinfachen (05.10.2026)
- Benutzer bestätigt REF020-Kilometerbezug funktioniert, meldet aber umständliche lange globale Achsliste. Screenshot enthält unpassende fremde Bahnhöfe. Weichenfläche sichtbar; keine pauschale weitere E2E-Abnahme.
- Auftrag: Referenz-/Verlaufsachslisten standardmäßig auf aktuellen Bahnhof und örtlich passende Strecken begrenzen; bestehende gespeicherte Bezüge bleiben auffindbar. Suchfeld und bewusste Erweiterung auf andere Bahnhöfe/Strecken, begrenzte Ergebnisliste. Örtliche Nähe ist nur Auswahlhilfe, keine automatische fachliche Verknüpfung. Durchgehende Originalstrecken bleiben einmal im gemeinsamen Bestand und dürfen mehreren Bahnhöfen dienen.
- Nach ausdrücklicher Achswahl genau eine vorhandene Originalquelle direkt in den Entwurf übernehmen, mehrere Quellen weiterhin ausdrücklich wählen. Vorschau/Speichern/Versionsschutz bleiben. Nur Servertemplate, kein Android-/API-/DB-Umbau.


## REF-DATA-021 – örtliche Referenzauswahl umgesetzt (05.10.2026)
- Benutzer bestätigt REF020-Kilometerbezug funktioniert; Beanstandung war die globale lange Liste. Keine vollständige weitere E2E-Abnahme ableiten. Basis SERVER e619a7c, Android423f378 unverändert; PR6 Draft, PR11 unverändert.
- Kilometer- und Verlaufsachslisten: aktueller Bahnhof plus Strecken im Objektumfeld (200m als Auswahlhilfe; vorhandene Linien bevorzugt, sonst Übersichtsbounds). Keine automatische fachliche Bahnhof-/KM-Zuordnung durch Nähe. Gemeinsame durchgehende Originalstrecken bleiben unverändert im globalen Bestand und werden nicht je Bahnhof dupliziert.
- Suche nach Name/Bahnhof/Strecke/Gleis, bewusste Erweiterung „Auch andere Bahnhöfe / Strecken suchen“. Max60 Treffer plus erhaltene ausgewählte Referenz; gespeicherte auch außerhalb Such-/Ortsfilter sichtbar. Änderungen an Bahnhof/Punkten aktualisieren Kandidaten. KM-Strecken im Umfeld oben priorisiert.
- Nach ausdrücklicher Achswahl eine einzige vorhandene Originalquelle direkt in Entwurf übernehmen; mehrere Quellen verlangen weiterhin bewusste Wahl, keine Quelle zeigt Hinweis. Prüf-/Speicher-/Versionspfad unverändert; keine automatischen Schreibzugriffe beim Öffnen/Filtern.
- Node119 PASS einschließlich bisheriger Regressionen und neuer Fälle: lokale/fremde Bahnhöfe, nahe/ferne Strecken, globale Suche, bestehende fremde Auswahl erhalten,100 zusätzliche Achsen/60er Begrenzung, einzelne/mehrere Quellen. Tatsächliches TemplateJS mit DOM-/Transport-Doubles; kein visueller Browser/NAS-E2E. Backend unverändert, kein neuer Backendtestlauf behauptet.
- Runtime ausschließlich server/templates/v1_admin_survey_overview.html. Über GitHub Desktop/File Station nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html; API neu starten, StrgF5. Keine neue Migration (21/21), kein Reset/Reindex. NAS-Praxistest der neuen Auswahlliste offen. Android bleibt Folgeblock.


## Betriebsprüfung – Benutzerabnahmen und Folgeauftrag (05.10.2026)
- REF-DATA-021 örtliche Referenzauswahl: Benutzer bestätigt „okay klappt“.
- Netzwerk-Versionskonflikt im Zwei-Fenster-Test: Benutzer bestätigt „funktioniert“; Screenshot zeigt „Zwischenzeitlich geändert. Bitte neu laden und deine Änderung erneut prüfen.“ Keine pauschale Abnahme anderer Konfliktpfade.
- Netzwerk-Deaktivieren/Wiederherstellen einschließlich Anschlüssen/Kilometerbezug nach vorgeschlagenem Test: Benutzer bestätigt am 05.10.2026 14:59 „funktioniert“. Benutzerbericht, kein eigener DB-/HTTP-Nachweis; kein vollständiger Backup-Restore daraus ableiten.
- Folgeauftrag 15:07 „okay“: offene Server-Betriebspunkte prüfen, zuerst Worker-/DB-Stabilität, dann Backup/Wiederherstellung und Firmen-/Benutzerrechte; Portal vor Android. Bestehende Abnahmen schützen.
- Ausgang: Server8e43e1007a66064aaa6e615b55fd861d0a0319e7 / PR6 offen Draft; Android423f37892915cb3308a4b1ea9e4d196faa872326 / PR11 offen. Keine app/**-Änderung, kein NAS-Zugriff vorausgesetzt. Historische Fehlermeldungen benötigen reale Logs zur Ursachenbestimmung.


## OPS-MONITOR-001 – Überwachungsprüfung und Quellkorrektur (05.10.2026)
- Vorabbefund f0b989b653ca59a5d37311e2d0e0d932c30394a8, Benutzerabnahmen9712fc6. Worker-Neustarterkennung verwendet jetzt started_at (normalisierte Zeit) statt nur PID, mit PID-Fallback für alte Heartbeats.
- read_monitor_snapshot erkennt fehlende/ungültige oder veraltete Zeitangaben sowie erhebliche Zukunftsabweichung. Grenzwert max(120s,3×Monitorintervall); overall ERROR und eigener Monitorcheck, historische Einzelmesswerte erhalten. Systemstatus benennt diese ausdrücklich als letzten bekannten Stand.
- Prüfung: 130_verify_monitor_liveness.py acht Verhaltenstests PASS mit echten AST-isolierten Funktionen und Datei-/Zeit-/Benachrichtigungs-Doubles (keine E-Mail versandt); Python-AST und JavaScript-Syntax PASS. Kein NAS-/HTTP-/DB-/visueller Browser-E2E. Historische Worker-Abbruchursache NICHT bestimmt; jobs.py hat bereits DB-Retries. Dafür Worker-/DB-Containerlogs und Ereigniszeit benötigt.
- Runtimeübernahme nur tbserver/monitor.py und templates/v1_system_status.html aus server/ nach /volume1/docker/tb-helfer-v1; API, Worker und Monitor über Container Manager neu starten (Worker importiert Monitor), Browser StrgF5. Keine Migration, keine Original-/DB-/Storage-/Secretsänderung, kein Android.
- Erste Source-Sichtung backup.py/restore.py: Prüfsummen, separates Restore-Test-DB-Ziel und Archivpfadschutz vorhanden. Kein vollständiger Backupkonsistenz-/Restore-Nachweis; aktuelle Backups praktisch prüfen. permissions.py gesichtet, noch keine vollständige Endpunkt-/Firmenisolationsprüfung. Diese Folgeblöcke sowie Portal/Last/Android bleiben offen.
