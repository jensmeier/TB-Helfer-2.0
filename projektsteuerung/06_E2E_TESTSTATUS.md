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
