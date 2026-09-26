# TB-HELFER 2.0 – ANDROID STATUS

Stand: 2026-09-26
Branch: `feature/tb-android-2.0`
Fach-HEAD vor Projektsteuerungscommit: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
Draft-PR: #7
Version: **1.3.23 / 200073**

- Server/Android-Vertrag 25.09 im Quellstand berücksichtigt.
- Pixel ACTIVE↔PDF-Flackern source-seitig korrigiert.
- Build-Wiederholung 200069: **OFFEN**
- Installation 200069: **OFFEN**
- Pixel/Poco-/Server-E2E: **OFFEN**
- Poco: korrekte HELPER-/Teamzuordnung serverseitig, aber keine Daten auf Gerät.

Geschützt: bestätigter BTB-Lifecycle-Fix 200063, bestätigter Server-App-Updateweg mit Datenerhalt, Geräteaktivierung, kein breiter Vollsync als normaler Autosave-Pfad.

Nächste Schritte: NET-001 klären → Haupt-TB-Handy ↔ Server/Portal E2E → Poco/HELPER E2E → danach IMG-001 implementieren → Build/Regression.

Historischer Tracker: `projektsteuerung/archiv/ANDROID_STATUS_ALT_2026-09-26.md`

## Praxisbefund Hauptgerät – DNS/Hostauflösung

Beim Anlegen einer Testbaustelle meldet die App:
`Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.

Nachtest auf demselben Hauptgerät: `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` sind im Browser erreichbar. Damit ist die öffentliche DNS/API-Erreichbarkeit grundsätzlich vorhanden. Nächster Schritt: App-Sync erneut auslösen; nur bei erneutem App-only `Unable to resolve host` den Android-Netzpfad gezielt untersuchen.


## IMG-001 Quellaudit
Status: **FEHLER BESTÄTIGT / IMPLEMENTIERUNG ZURÜCKGESTELLT BIS E2E-PRIO-001**

- `BtbTeamMediaSync` und `CollabLocalBridge` verwenden lokale Originaldateien als Uploadquelle.
- `Server1FileApi` überträgt exakt diese Datei und kennt aktuell keine Rendition-Downloadmethode.
- `synchronizeServer1Attachments()` lädt fehlende Bilder über `/content` vollständig.
- Lokales 512-px-Thumbnail vorhanden, aber nicht im Online-Sync genutzt.
- `FotoEntity` speichert keine GPS-Genauigkeit; BTB-Upload sendet deshalb `gpsAccuracyM = null`.
- Mangel-/Abnahmefoto-Uploads übergeben keine gleichwertigen strukturierten Foto-Metadaten.

Geplanter Android-Fix nach stabilem Hauptgerät-/Poco-E2E:
kleine Sync-JPEG-Datei → Upload; `preview/thumb` für mobile Downloads; Metadaten vollständig.

### Browser-Nachtest NET-001
Beide öffentlichen TB-Helfer-Seiten sind auf demselben Hauptgerät im Browser erreichbar. App-Retest ist der nächste Schritt.

### NET-001 reproduziert
Browserzugriff auf Portal und API-Health funktioniert auf demselben Gerät; `Neu laden` in der App liefert weiterhin `Unable to resolve host "api-v1.tb-helfer.de"`. Damit ist der Fehler app-/Android-netzpfadspezifisch reproduziert. Source-/Runtime-Diagnose erforderlich; keine Neuaktivierung oder Datenlöschung auf Verdacht.

### NET-001 nach Prozessneustart und Mobilfunk
Force-Stop/Neustart der App sowie WLAN aus / Mobilfunk ändern den Fehler nicht. `Unable to resolve host "api-v1.tb-helfer.de"` bleibt reproduzierbar. Als nächstes systemweite Android-DNS-/VPN-/Filtereinstellungen prüfen; App-Daten/Token nicht löschen.

### Praxisnachtrag – Pixel wieder synchron / Poco frisch aktiviert
- Pixel hat inzwischen wieder erfolgreich mit dem NAS synchronisiert; der zuvor reproduzierte DNS-Fehler blockiert den aktuellen Lauf nicht mehr.
- Poco: App gelöscht, neu installiert und neu aktiviert; erster Datenabruf läuft >3 Minuten.
- Nächster Schritt ist kein weiterer Reset, sondern Erstsync-Pipeline/Datenumfang prüfen und Laufzeitphase eingrenzen.

### BTB-SYNC-001 Praxisfehler 26.09
- Poco-Erstabgleich abgeschlossen; Baustelle erfolgreich geladen.
- Nach neuem Pixel-BTB 26.09 erscheint dieser auf Poco kurz und verschwindet danach wieder („kein BTB vorhanden“).
- Android-seitig daher besonders prüfen: Server-Snapshot ersetzt/entfernt lokalen Active-Pointer? FINALIZED-/Statuslogik? Team-/Group-Filter? Kein Fix ohne Serverzustand.

### BTB-SYNC-001 Versionsabweichung auf Testgeräten
Server-Geräteabfrage: Pixel meldet `1.3.4`, Poco `1.3.19`. Damit ist der aktuelle Geräte-E2E gemischt. Installation des aktuellen Zielstands `1.3.19 / 200069` auf Pixel ist vor weiterer Ursachenbewertung zwingend nachzuweisen.

### Korrektur Versionsdiagnose
Pixel läuft nach Benutzerbestätigung ebenfalls auf `1.3.19`. Der Serverwert `1.3.4` stammt aus der Geräteaktivierung und ist nach App-Updates stale. Kein Versionsgleichstandsfehler als Ursache von BTB-SYNC-001 bestätigt.

### BTB-SYNC-001 nach Portal-Bestätigung erneut reproduziert
Poco zeigt den serverseitig und im Portal bestätigten aktiven 26.09-BTB kurz und danach wieder „kein BTB vorhanden“. Android-seitige Ursache jetzt isoliert.

### BTB-SYNC-001 Source-Ursache
`BtbActiveContext.laden()` kann einen serverseitig bestätigten aktiven BTB lokal wieder deaktivieren, weil es den lokalen Dokumentstatus `ABGESCHLOSSEN` höher bewertet als `BtbServerStateStore.active=true`. Der UI-Watcher läuft ca. alle 600 ms; das erklärt das kurze Anzeigen und anschließende Verschwinden. Android-Fix erforderlich; noch kein Build/E2E.


## BTB-SYNC-001 – Active-Pointer-Fix
Status: **SOURCE-FIX COMMITTED / BUILD OFFEN**

- Vorheriger Fehler: `BtbActiveContext.laden()` konnte trotz `BtbServerStateStore.active=true` einen lokalen Abschlussstatus als maßgeblich behandeln und `nachAbschlussInaktivMerken()` auslösen.
- Fix: `f57c532863d2f92b81729b9af1466b57a22f8723`.
- Jetzt gilt für servergebundene BTBs:
  - Server `active=true` → Result bleibt aktiv, lokale Abschlussmarker werden nicht ausgewertet.
  - Server `FINALIZED && !active` → Pointer wird gelöst.
  - anderer vorhandener Serverstatus → kein lokales Abschlussartefakt darf den Server-Lifecycle überstimmen.
  - nur ohne Server-Snapshot gilt die bisherige lokale/Legacy-Abschlusslogik.
- Version: **1.3.20 / 200070**.
- Kein Servercode geändert.
- GitHub Actions: kein Workflow-Run für aktuellen HEAD entstanden, weil die verfügbaren PR-Basen aktuell nicht mergebar sind. Das ist **kein Buildnachweis**.
- Nächster Schritt: lokal in Android Studio 1.3.20 / 200070 bauen, danach Pixel und Poco installieren und BTB 26.09 erneut testen.


## Versionierung – nächster Build 1.3.20 / 200070
- Benutzerentscheidung 26.09.2026: neue APKs dürfen nicht erneut mit bereits verwendeter Buildnummer 200069 erzeugt/hochgeladen werden.
- BTB-SYNC-001 wird als **1.3.20 / 200070** gebaut.
- Build/Installation/E2E bleiben bis zum tatsächlichen Build offen.


### Release-Bump umgesetzt
- Commit `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- `versionName = 1.3.20`
- `versionCode = 200070`
- neue Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.20_200070.md`
- GitHub-Workflow-Artefakt auf `TB-Helfer-1.3.20-200070` umgestellt.
- Build noch nicht ausgeführt.


## UPD-001 – Updateanzeige stale nach Installation
Status: **REPRODUZIERT / URSACHE OFFEN**

- Updateprozess läuft durch, aber Updateprüfung zeigt weiterhin 1.3.19.
- Startseite zeigt weiterhin „Update verfügbar“.
- Zielstand ist 1.3.20 / 200070.
- Erst prüfen, ob UI/Updateclient die lokale Package-Version oder einen stale Server-/Aktivierungswert verwendet.
- Kein weiterer Versionssprung nur zur Kaschierung dieses Anzeige-/Vergleichsfehlers.


## UPD-001 – Binärdatei gegen Release prüfen
Status: **SOURCE-URSACHE EINGEGRENZT / FIX AUFTRAG**

- Lokale Versionsanzeige basiert auf `BuildConfig`; kein Anzeige-Cache.
- Updatecheck basiert auf dem lokalen `BuildConfig.VERSION_CODE`.
- Fehlerschutz fehlt: APK-Inhalt wird vor dem Installer nicht gegen Release-`versionName/buildNumber` validiert.
- Android-Schutzfix wird in **1.3.21 / 200071** umgesetzt.


## UPD-001 – Schutzfix committed
Status: **BESTÄTIGT**
- Commit: `19d688a4752c98209570e0ff69ccd2eae789541e`.
- `AppUpdateManager` blockiert APKs mit falscher Package-ID oder abweichendem `versionName/versionCode`.
- Zielbuild: **1.3.21 / 200071**.
- Build, Installation und Update-E2E für 1.3.21 / 200071 vom Benutzer bestätigt.


## REL-FILE-001 – Buildbegleitdatei Pflicht
- Ab sofort hat jeder neue Android-Build eine Release-MD unter `projektsteuerung/releases/`.
- Die aktuelle Zielversion 1.3.21 / 200071 besitzt bereits `projektsteuerung/releases/TB-Helfer_1.3.21_200071.md`.
- Bei jeder Übergabe wird diese Datei auch als Download mitgeliefert.
- CI soll VERSION/BUILD der MD gegen `app/build.gradle.kts` prüfen, damit Build und Metadaten nicht auseinanderlaufen.


### REL-FILE-001 CI-Schutz aktiv
- Commit `e76933cd7270da21d8cd251ece1cba87c7db0cf8`.
- Kein Android-CI-Build soll künftig ohne passende Release-MD durchlaufen.
- Artifact-Name und Release-Dateipfad werden aus dem aktuellen `versionName/versionCode` abgeleitet.
- Damit ist die Benutzeranforderung „bei jedem Build/GitHub-Stand Release-Datei mitgeben“ nicht nur dokumentiert, sondern im CI-Paket technisch abgesichert.


### Praxisbestätigung 1.3.21 / 200071
- Benutzer: **hat funktioniert**.
- Build: **ERFOLGREICH**.
- Installation/Update auf Gerät: **BESTÄTIGT**.
- Update-E2E: **BESTÄTIGT**.
- UPD-001 darf ohne neuen reproduzierbaren Fehler nicht wieder geöffnet werden.
- BTB-SYNC-001 / Pixel-Poco-Fach-E2E bleibt offen.


## BTB-SYNC-001 – Praxis 1.3.21 weiterhin fehlerhaft
Status: **REPRODUZIERT / SOURCE-FIX NICHT AUSREICHEND**
- Pixel: `BTB Abschluss` flackert weiterhin.
- Poco: aktiver 26.09-BTB verschwindet weiterhin nach sehr kurzer Anzeige.
- Installierter Stand: 1.3.21 / 200071, Updateweg bestätigt.
- Deshalb keine Versions-/Updateursache mehr.
- Nächste Quellprüfung: ServerState-Persistenz/ID-Mapping/Pointer-Schreiber/Lifecycle-Watcher.


## BTB-SYNC-001 – zweiter Guard-Fix 1.3.22
Status: **SOURCE-FIX COMMITTED / BUILD OFFEN**

- Commit: `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`.
- Ursache Poco: `BtbPatch200033Installer.startActivePointerGuard()` konnte serveraktiven Pointer alle 450 ms über stale Inaktiv-Marker löschen.
- Ursache Pixel-Rollen-/Buttonkonflikt: Patch200033 wurde nach dem modernen Lifecycle installiert und konnte dessen Abschlusslistener überschreiben.
- Fix: alter Guard server-aware + ModernLifecycleInstaller zuletzt binden.
- Version: **1.3.22 / 200072**.
- Direkt geprüfter Quellstand:
  - `BtbServerStateStore` Import vorhanden;
  - `serverState.active` schützt Pointer;
  - `serverFinalReadOnly` löst Pointer;
  - Gradle 1.3.22 / 200072;
  - passende Release-MD vorhanden.
- **Kein Buildnachweis**; GitHub Actions hat für den Fixcommit keinen Run erzeugt.


## BTB-SYNC-002 – P0 Outbox-Reihenfolge korrigiert
Status: **SOURCE-FIX UMGESETZT / BUILD OFFEN**

Ausgangsfehler:
- `SelectiveSyncWorker` konnte einen Server-Snapshot lokal anwenden, bevor der gerade gespeicherte Pixel-Stand in Pending/Outbox exportiert war.
- Dadurch war stiller Verlust lokaler BTB-Fachänderungen möglich.

Android-Fix:
- dirty BTB zuerst `exportBtbChange(...)`;
- kein vorgelagerter `loadBtbAccess(...)` + `applyServerStateToLocal(...)` mehr;
- Upload/Version/Conflict erst danach über den bestehenden Mutation-Pfad;
- `CollabLocalBridge` blockiert BTB-Snapshot-Apply bei Pending oder Dirty;
- lokaler Save und Server-Apply sind pro BTB serialisiert;
- Dirty-Generation verhindert, dass ein während des Uploads neuer Autosave als verarbeitet gilt;
- nach bestätigtem Upload wird der Snapshot nur dann lokal angewendet, wenn der verarbeitete Dirty-Stand noch aktuell ist.

Zielversion: **1.3.23 / 200073**.
Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.23_200073.md`.

Nicht geändert:
- kein `server/**`;
- Server-/DB-Vertrag unverändert;
- PORTAL-LIVE-001 bleibt SERVER: Source-Fix vorhanden, Deploy/E2E offen.

Nächster Schritt:
1. Build 200073;
2. Pixel installieren;
3. Pixeländerung → Portal;
4. Portaländerung → Pixel;
5. parallelen Autosave während Upload prüfen;
6. danach Poco.


## BTB-SYNC-002 – Gerätebestätigung 1.3.23 / 200073
Status: **PIXEL↔PORTAL BASIS-E2E BESTÄTIGT**

- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Der frühere Fehler „lokale Pixel-Eingabe erreicht Portal nicht und wird später vom Serverstand überschrieben“ ist im normalen Hin-und-zurück-Test nicht mehr reproduziert.
- Parallel-Autosave/Conflict-E2E bleibt separat offen.
- Poco/HELPER-E2E bleibt separat offen.


## BTB-UI-001 – Abschlussbuttons
Status: **REPRODUZIERT / SOURCE-FIX AUSSTEHEND**

- Pixel als Haupt-TB zeigt wechselnd 1 oder 2 Abschlussbuttons.
- Ursache im aktuellen Quellpfad: Lifecycle-Watcher setzt Buttons zunächst beide sichtbar und blendet `PDF Entwurf` im Haupt-TB-Zweig wieder aus.
- Ziel: Haupt-TB aktiv = stabil `PDF Entwurf` + `BTB Abschluss`.
- Helfer = kein PDF-Entwurf; Rollen-/Schichtaktion separat.
- Neuer Zielbuild: **1.3.24 / 200074**.


## BTB-UI-001 – Fix 1.3.24
Status: **SOURCE-FIX UMGESETZT / SOURCE-CHECK PASST / BUILD OFFEN**
- Haupt-TB-UI zeigt stabil zwei Buttons: `PDF Entwurf` + `BTB Abschluss`.
- Kein vorgelagertes Umschalten auf beide sichtbar und anschließendes Ausblenden mehr.
- HELPER bleibt ohne PDF-Entwurf mit eigener Schichtaktion.
- Version **1.3.24 / 200074**.
- Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.24_200074.md`.
