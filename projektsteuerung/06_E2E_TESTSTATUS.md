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
