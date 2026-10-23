# TB-HELFER 2.0 – ANDROID STATUS

Stand: 2026-09-27
Branch: `feature/tb-android-2.0`
Fachcode-HEAD vor aktueller Statussicherung: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`
Draft-PR: **kein offener Android-Draft-PR**
Version/Zielbuild: **1.3.26 / 200076**

## Aktuell
- Bildtransport source-seitig umgesetzt: Original bleibt lokal; Sync-JPEG max. 1920 px/JPEG82.
- Kleine Uploads: BTB, Mängel, Abnahmen, bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen unverändert.
- Mobile Serverbilder: `preview` statt automatischem `/content`.
- BTB-Foto-Metadaten-PATCH via `link_version` source-seitig integriert.
- Wetter: 0,0 mm bleibt numerisch erhalten; mm sichtbar in App/PDF/Druckmodell.
- Version/Release-MD: 1.3.26 / 200076.
- Sourcechecks: PASS; Build-Sourcegenerator 14/14 Anker PASS; Diff ohne `server/**`.
- **BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN.**

## Geschützt
- BTB-SYNC-002: **BESTÄTIGT**.
- BTB-UI-001: **BESTÄTIGT**.
- UPD-001: **BESTÄTIGT**.
- PORTAL-LIVE-001 ist serverseitig **BESTÄTIGT** und darf Android-seitig nicht als offener F5-Fehler behandelt werden.
- bestätigten Active-Pointer-/Dirty-/Update-Schutz nicht zurückbauen.

## Nächste Schritte
1. Git Pull.
2. Android Studio Build **1.3.26 / 200076**.
3. Pixel installieren.
4. E2E: kleine Bilder + preview, BTB-Foto-PATCH, Wetter 0,0 mm/tempC, Finalize Android→Portal sowie Portal→Android.
5. Poco/HELPER mit demselben erfolgreichen Build.
6. erst danach Gesamtlast/weitere offene Punkte.

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


## BTB-UI-001 PRAXIS BESTÄTIGT 1.3.24
- Pixel-Haupt-TB: PDF Entwurf + BTB Abschluss bleiben stabil sichtbar.
- Benutzer bestätigt: kein Springen mehr.
- Pixel-UI-E2E: BESTÄTIGT.
- Poco/HELPER-E2E bleibt offen.


## BTB-SYNC-002 – Praxis vollständig bestätigt
Status: **BESTÄTIGT**

- Pixel ↔ Portal Basissync: **BESTANDEN**.
- Parallel-Autosave während laufendem Upload: **BESTANDEN**.
- Neuere lokale Dirty-Änderung wird nicht durch älteren bestätigten Server-Snapshot verloren.
- Outbox-/Dirty-/Mutationslock-Fix ist praktisch bestätigt.
- Geschützt: nicht ohne neuen reproduzierbaren Fehler ändern.
- Nächster Android-Gerätepunkt: Poco/HELPER-E2E.


## BTB-EDIT-001 – Android-Audit und Zielmodell
Status: **DIAGNOSE ABGESCHLOSSEN / IMPLEMENTIERUNG OFFEN**

Quellbefunde:
- `SectionItem`: nur Zeit/Text/KM.
- `BautagebuchFragment`: technische `@ORT=<Long>`-Marker; `buildEntry()` entfernt sie bei Punkt 4–8.
- Firmenpräfix ist nicht strukturiert im Entry-Modell.
- `BtbPhotoContextOverrideStore`: lokale `ortId`, keine stabile Node-Referenz.
- `WorksiteStructureSync` / `SyncWorksiteNodeBindingEntity`: stabile `clientNodeKey`-Infrastruktur bereits vorhanden.
- `BtbEntrySlotStore`: stabile Server-Slots vorhanden, aber nur parallel zur lokalen Arrayposition.

Android-Ziel:
- Entry: `clientEntryKey`, `firmaId/firmaName`, `worksiteNodeKey`, Zeit, KM, Text; Punkt 4–8 über Zielsektion.
- Foto-Override/Sync: `worksiteNodeKey` zusätzlich/anstatt lokaler `ortId` als kanonische Referenz.
- bestehende IDs bleiben beim Umhängen erhalten.
- alter Local-ID-Pfad wird migriert, nicht als Mehrgerätevertrag weitergeführt.

Serververtrag jetzt source-seitig vorhanden: `cd9b18fc062a7a3d4354c611a949ed3d8408d709`.

## PORTAL-WEATHER-001 – Android-Diagnose 26.09.2026
Status: **TEILURSACHE BESTÄTIGT / tempC-VERLUST NOCH NICHT ANDROID-SEITIG NACHGEWIESEN**

- Androids `BautagebuchSpeicher` serialisiert `weatherSlots[].tempC` und `niederschlagMm`.
- `Server1BtbApi.HEADER_META_KEYS` enthält `weather` und `weatherSlots`; diese Daten sind damit grundsätzlich Teil des Android→Server-Headers.
- Open-Meteo liefert Temperatur und Niederschlag als echte Zahlen; Android rundet `tempC` und setzt es in `WetterSlot`.
- Für bekannten Niederschlag **0.0 mm** existiert jedoch ein konkreter Android-Fehler: Auto-Wetter verwendet `h.precipMm.takeIf { it > 0.0 }`. Dadurch wird genau 0.0 zu `null`.
- Fachregel: Bei erfolgreicher Wetterquelle muss 0.0 als **0.0 mm** erhalten bleiben.
- Dass `tempC` im aktuellen Portal-BTB fehlt, ist nach dem Quellaudit noch nicht als Android-Serialisierungsfehler belegt; hierfür muss der konkrete Server-Header-/Runtime-Payload mit Android-Ausgabe verglichen werden.
- Wetterfix bleibt getrennt von BTB-EDIT-001.


## Buildziel 1.3.25 / 200075
- Benutzer hat Build angefordert.
- Version **1.3.25 / 200075** reserviert.
- Source-Check und echter Build müssen noch durchgeführt werden.
- BUILD ERFOLGREICH erst nach tatsächlichem Gradle/CI-Nachweis.


## AND-BUILD-200075 – CI-Buildversuch
Status: **BUILD NICHT GESTARTET / CI-RUNNER NICHT ZUGEWIESEN**

- Ziel: **1.3.25 / 200075**.
- Temporärer mergebarer CI-PR #10 wurde ausschließlich für den echten Android-CI-Build angelegt.
- Android-CI Run: `36252246995`.
- Versuch 1 und Wiederholungsversuch 2 endeten jeweils nach ca. 3 Sekunden mit `failure`.
- Der Job `build` erhielt **keinen Runner** (`runner_name = ""`) und **keine Steps wurden gestartet** (`steps = []`).
- Damit wurden weder Checkout noch Source-Checks, Gradle, Tests, APK-Build oder Lint ausgeführt.
- Bewertung: **kein Android-Compilefehler nachgewiesen**; GitHub-Actions-Infrastruktur hat den Job vor Step 1 nicht gestartet.
- Deshalb Status weiterhin sauber getrennt: QUELLSTAND/VERSION VORBEREITET, BUILD OFFEN, INSTALLATION OFFEN, E2E OFFEN.
- Nächster valider Buildweg: Android Studio/lokaler Gradle-Build `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` oder funktionierender GitHub-Runner.


## AND-BUILD-200075 – lokaler Buildfehler generateBtb200043Sources
Status: **REPRODUZIERT / URSACHE EINGEGRENZT**

- Benutzer-Build in Android Studio am 26.09.2026 scheitert in `:app:generateBtb200043Sources`.
- Fehlermeldung: `Android 200047: Hauptbaustellen-Ortsmarker-Anker wurde nicht gefunden`.
- Damit ist jetzt erstmals ein echter lokaler Buildfehler für 1.3.25 / 200075 nachgewiesen.
- Ursache liegt **nicht** in Server/API/DB, sondern im Android-Buildgenerator `GenerateBtb200043Sources` in `app/build.gradle.kts`.
- Der Generator sucht noch nach dem alten 200047-Quellanker `if (effectiveOrtId != 0L) ...`; BTB-EDIT-001 hat den kanonischen Editor inzwischen auf `clientEntryKey/worksiteNodeKey` und eine neue `buildManualLine`-/Parserstruktur umgestellt.
- Fixziel: veralteten Build-time-Patch 200047 nicht mehr gegen den neuen Quelltext laufen lassen; die notwendige Hauptbaustellen-/Legacy-Ortsmarker-Semantik direkt im kanonischen `BautagebuchFragment.kt` erhalten.
- Version bleibt **1.3.25 / 200075**, weil der Build nicht erfolgreich erzeugt/installiert wurde.


## AND-BUILD-200075 – Sourcegenerator-Fix
Status: **SOURCE-FIX UMGESETZT / SOURCE-CHECK PASST / BUILD ERNEUT OFFEN**

- Fehler aus dem lokalen Android-Studio-Build war reproduzierbar: `:app:generateBtb200043Sources` brach am alten Android-200047-Ortsmarker-Anker ab.
- Fixcommit: `606aa3ad1f8ea438372484b056a0fc1264ae2aac`.
- Der veraltete Android-200047-Build-time-Patch wurde aus `GenerateBtb200043Sources` entfernt.
- Ortsmarker-Semantik liegt jetzt direkt im kanonischen `BautagebuchFragment.kt`:
  - jede moderne Zeile schreibt explizit `@ORT=<lokale UI-ID>`, Hauptbaustelle also `@ORT=0`;
  - `@NODE=<clientNodeKey>` bleibt die geräteübergreifende Sync-Identität;
  - Inline-`@ORT` wirkt nur auf die aktuelle Zeile;
  - alte Standalone-`@ORT`-Gruppen bleiben kompatibel.
- Source-Check bestätigt: alter Fehlertext/alte Patch-Anker sind aus `app/build.gradle.kts` entfernt; 1.3.25 / 200075 bleibt unverändert.
- **Noch kein neuer Buildnachweis.** Nächster Schritt: Git Pull → Android-Studio-Build 200075 erneut starten.

## Integrationsaudit 27.09.2026 – Bilder / Abschluss / Wetter
Status: **SOURCE-AUDIT PASS / ANDROID-ARBEITSPUNKTE IDENTIFIZIERT / KEIN CODE IN DIESEM BLOCK**

- Foto-Sync weicht vom Vertrag ab: Original wird hochgeladen, Serverbilder werden über `/content` statt Renditions geholt. Nächster Android-Block: kleine Sync-JPEG-Datei erzeugen + `preview/thumb` konsumieren.
- BTB-Finalisierung ist source-seitig bidirektional korrekt an den kanonischen Server-Lifecycle angebunden. Kein Umbau auf Verdacht; gezielter E2E beider Richtungen erforderlich.
- Wettermodell transportiert `tempC` und `niederschlagMm`; Android verwirft bei automatischem Wetter jedoch 0.0 mm über `takeIf { it > 0.0 }` und rendert mm weder in der App-Wetterzeile noch vollständig im Android-PDF.
- BTB-EDIT-001 Foto-PATCH bleibt zusätzlicher Android-Restpunkt.
- 1.3.25 / 200075 Buildstatus bleibt unverändert: **BUILD OFFEN**.

## Zielbuild 1.3.26 / 200076
Neuer Android-Codeblock Bilder/Wetter/Foto-PATCH. Buildnummer 200075 wird nicht wiederverwendet. Ziel: **1.3.26 / 200076**. BTB-Finalize-Lifecycle bleibt unverändert; separater E2E danach.

## Android 1.3.26 / 200076 – Bilder / Foto-PATCH / Wetter
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECK PASS / BUILD OFFEN**

- `MobileImageSync`: separate JPEG-Syncdatei im Cache, max. 1920 px, JPEG 82, EXIF-orientiert; Aufnahmeoriginal bleibt unangetastet.
- Kleine Uploads jetzt für BTB-Fotos, Mängelfotos, Abnahmefotos und bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen bleiben Originaldateien.
- Server→Android lädt bei Bild-MIME die vorhandene `preview`-Rendition; alte App-Cache-Vollbilder werden bei Bedarf durch mobile Previews ersetzt.
- BTB-Foto-Metadaten-PATCH ist Android-seitig angebunden und versioniert über `link_version`; Parallelkonflikte werden gemeldet.
- Wetter: 0,0 mm wird nicht mehr zu null; bekannte mm-Menge zählt als Wetterwert und wird pro Slot, im Android-PDF und im Druckmodell angezeigt.
- `versionName=1.3.26`, `versionCode=200076`; Release-MD vorhanden.
- BTB-FINAL-001 wurde **nicht** umgebaut; bestehender kanonischer Finalize-Pfad bleibt geschützt.

Prüfung:
- gezielte Source-Checks: PASS;
- Build-Sourcegenerator 14/14 Anker: PASS;
- Diff ohne `server/**`;
- Fachcode-HEAD vor Statussicherung: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`;
- GitHub Actions für diesen HEAD: kein Run;
- **kein echter Gradle-Build / keine Installation / kein Android-Geräte-E2E**.

## Android 1.3.26 / 200076 – Locale-Compilefix 27.09.2026
- Android-Studio-Build reproduzierbar in `:app:compileDebugKotlin` gescheitert: `Unresolved reference 'Locale'`.
- Ursache: fehlender `java.util.Locale`-Import für die neue mm-Formatierung.
- Source-Fix: `import java.util.Locale` ergänzt.
- Sourcecheck: `Locale.GERMANY` auflösbar; Generatoranker **14/14 PASS**.
- Status: **SOURCE-FIX UMGESETZT / BUILD ERNEUT OFFEN / INSTALLATION OFFEN / E2E OFFEN**.


## AND-VER-001 – zurückgenommen
- Keine zusätzliche Versions-/Buildzeile auf der Startseite.
- Von der nicht beauftragten Änderung betroffene UI-/Formatänderungen wurden auf den vorherigen Stand zurückgesetzt.
- Technischer Zielstand bleibt **1.3.26 / 200076**.


## Android-/Server-Abgleich 20.10.2026 – AND-SYNC-AUDIT-20261020
Status: **QUELLABGLEICH ABGESCHLOSSEN / IMPLEMENTIERUNG OFFEN / BUILD UND GERÄTE-E2E OFFEN**.

Auftrag: nach Statusauskunft vom Nutzer mit „okay“ bestätigter gezielter Vertragsabgleich; keine pauschale Implementierungs- oder Buildfreigabe daraus ableiten.
Geprüfte Ausgangsstände: ANDROID `6cec34423e34315c581b743668724abe17daa333`, SERVER `0f82b42da7412a138bf99b6f80b93031e3b5373e`. PR11 offen, kein Draft, PR6 offen/Draft; beide melden mergeable=false. Technische Androidversion unverändert 1.3.26/200076. Keine zusätzliche Versionszeile auf der Startseite.

| Priorität | Befund / Quellbeleg | Nächster Androidblock |
|---|---|---|
| P0 | Server files.py: PHOTO_MAX_BYTES=2_000_000. MobileImageSync.kt: 1920px/JPEG82, aber keine Byteobergrenze; auch Cachetreffer nur auf Länge>0 geprüft. Damit fehlt eine garantierte Einhaltung, kein konkreter Live-Uploadfehler behauptet. | Wiederholte Komprimierung/Verkleinerung mit Prüfung <=2.000.000Bytes, Cache ebenfalls prüfen; kein Originalfallback; Original erhalten. |
| P1 | SectionItem in BautagebuchModels.kt und Serializer/Parser in BautagebuchSpeicher.kt besitzen uhrzeit, aber kein uhrzeitBis. Neue Portal-Endzeiten werden dadurch nicht vollständig lokal modelliert/gespeichert. | Ende additiv durch Modell, Speicher, Editor, Sync und PDF führen; Alt-Einzelzeiten erhalten; Rundlauf testen. |
| P1 | Firmenzeitbindungen personalZeitbindung/maschinenZeitbindung fehlen im geprüften Modell/Speicher. Server führt REPORT/MANUAL ein. | Feld-Erhalt und automatische Zeitübernahme einschließlich manueller Ausnahmen gezielt prüfen/ergänzen; kein stilles Überschreiben. |
| P1 | Server UploadComplete hat btb_entry_id; Android Server1FileApi.uploadLinkedFile hat dieses Feld nicht. BtbTeamMediaSync hat keine btbEntryId-Behandlung. Serververtrag PORTAL-017: gebundener Fotokontext folgt dem Eintrag. | Stabile Server-Entry-ID gegen vorhandenen clientEntryKey/Slot abbilden; Foto-Bindung und Entkopplung umsetzen, keine Textheuristik. |
| P1 | FotoEntity hat keine GPS-Genauigkeit, BtbTeamMediaSync sendet gpsAccuracyM=null. Download mergedPhotoMetadata überspringt explizite null-Werte und ergänzt Legacy; Import ersetzt fehlende Koordinaten durch 0.0 und fehlende Zeit notfalls durch jetzt. Das passt nicht zuverlässig zu explizitem GPS-Löschen/fehlenden Metadaten des neuen Vertrags. | Effektive BTB-Link-GPS inklusive null-Semantik, Aufnahmezeit und Genauigkeit prüfen und verlustfrei modellieren; keine erfundenen Aufnahmedaten. Room-Migration erst nach gesondertem Implementierungsblock. |
| P2 | Nachtschicht-Grundlage bereits vorhanden: endeDatumIso samt abgeleitetem Enddatum im Modell; Server1BtbApi übernimmt Datum aus ended_at und sendet endeDatumIso zurück. | Kein kompletter Neubau. Portal->Android->Portal/PDF über Mitternacht, Monats-/Jahreswechsel und Zeitzone testen. |
| P2 | SyncCatalogApi nutzt GET /api/v1/catalog; in diesem Client keine Anbindung an /catalog/lookup oder /catalog/inventory. WorksiteNodeApi transportiert generisches payload und track_or_switch. | Neue Bestandsübersicht/Objektart als eigener UI-/Vertragsblock; generischen Payloadtransport nicht pauschal als Datenverlust werten. |

Prüfumfang: Live-Einstieg/Projektregeln/Steuerungsdateien, aktueller Serververtrag einschließlich PORTAL-012/016/017/019/020/021, Android-Gitbaum, MobileImageSync, Server1BtbApi, Server1FileApi, BtbTeamMediaSync, CollabLocalBridge, FotoEntity, WorksiteNodeApi, WorksiteStructureSync, WorksiteTrackDataSync, SyncCatalogApi, BautagebuchModels/BautagebuchSpeicher sowie Server files.py.
Grenzen: reine Quellprüfung; kein Gradle, Unit-Test, Lint, Emulator, API-/DB-/NAS- oder Geräte-E2E. Keine neue Source-Test-PASS-Aussage. HEAD-Abfrage lieferte keine PR-getriggerten Workflow-Runs; das ist kein Nachweis über sämtliche historischen/push/manuellen Runs. Direkter Git-Transport hier 403, GitHub-Connector lesbar. Keine Runtime-/Server-/Produktivdatenänderung und keine Versionsanhebung.
Bestehende Bestätigungen BTB-SYNC-002, BTB-UI-001, UPD-001 und PORTAL-LIVE-001 bleiben geschützt. Alte September-Kopfstände sind historisch; dieser datierte Abgleich ersetzt keine Geräteabnahme.


## AND-IMG-2MB – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / STATISCHE PRÜFUNG BESTANDEN / BUILD UND GERÄTE-E2E OFFEN**.
- Nutzerauftrag vor Implementierung gesichert: 7020891d4c26fb15d7d2ef5bdc41c408f68d81e0.
- MobileImageSync: 1..2.000.000Bytes als Cache-/Ausgabebedingung; JPEG82/75/68/60, bei Bedarf weitere Reduktion der Kante um20%, nie Hochskalierung. Bestehende1920px-Grenze und EXIF-Ausrichtung erhalten.
- Vorbereitung synchronisiert gegen gleichzeitige Schreiber derselben Cache-/Tempdatei. Tempdatei im finally entfernt; unvollständiger Zielcache nach Publikationsfehler gelöscht. Aufnahmedatei bleibt unverändert, kein Originalfallback.
- Aufrufer geprüft: BtbTeamMediaSync sowie CollabLocalBridge für Mangel-/Abnahmefoto und bildbasierte Abnahmeanlage verwenden gemeinsamen prepare-Pfad und brechen bei null mit Fehler ab. PDF-/Nichtbildanlagen unverändert.
- Context7: offizielle Android-Bitmap-Referenz zu JPEG-Qualität, compress-Rückgabewert und createScaledBitmap-Identität geprüft.
- Ausgeführt: neun statische Schutzprüfungen PASS; Dimensionsfortschritt für jede Kante1..1920 geprüft (max29 Reduktionsschritte). Dies ist keine Kotlin-Kompilierung und kein Bitmap-/Android-Laufzeittest.
- Umgebung: Java vorhanden, kein kotlinc/Gradle/Android-SDK/adb gefunden, direkter Git-Transport403; kein Build, Unit-Test, Lint oder Emulatornachweis. Kein APK erzeugt; Version bleibt1.3.26/200076. Vor nächster auslieferbarer APK neuer höherer versionCode und passende Release-MD erforderlich.
- Nächste praktische Prüfung: detailreiches/großes Bild, kleineres Bild, exakt2.000.000Bytes/Übergröße im Cache, EXIF-Drehung, Fehler bei Dateischreiben und erneuter Versuch, Originalhash unverändert; dann Upload für BTB/Mangel/Abnahme zum aktuellen Server. Aufnahme-/Metadatenverträge und bestätigte Sync-/Lifecycle-Pfade unverändert.
- Keine server/**-Änderung, keine produktiven Datenänderungen, keine Actions gestartet ([skip ci]).


## AND-ENTRY-END – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / KOTLIN-UNIT-TESTS ANGELEGT, NICHT AUSGEFÜHRT / BUILD UND E2E OFFEN**.

- Auftrag vor Implementierung: c42bc151817f431352e98029ba907950feb1af97; Schutzentscheidung zu Roh-JSON/PDF-Hash: fc98a8e88de51d95343878de2e8220777757906f. Ausgangscode fcd2985a84a3d51fe44331437ed428223fba327a; Server unverändert0f82b42da7412a138bf99b6f80b93031e3b5373e.
- SectionItem hat additives nullable uhrzeitBis am Ende der Parameterliste; Beginn bleibt uhrzeit. BtbEintragszeit liest getrennte und historische zusammengefasste Zeiten; gleicher Beginn/Ende wird nicht als24h interpretiert, Nachtspanne nicht umsortiert. Auch fehlender Beginn bei vorhandenem Ende bleibt transportierbar.
- BautagebuchSpeicher liest/schreibt Endzeit und unterscheidet fehlendes Ende von explizitem null. Bei semantisch unveränderten Zeiten bleibt vorhandene lokale Zeit-JSON-Form bestehen, damit allein die Formatmigration keinen alten Abschluss-Hash ändert. Zuordnung über stabilen clientEntryKey, ID-loser Legacy-Fallback nur gleicher Abschnitt/Index und Text/Firma/Node/KM. Gleicher Schutz direkt vor Server-Snapshot-Write innerhalb des bestehenden BTB-Mutationslocks. Echte Zeitänderungen bleiben unverändert als Änderung erkennbar; Hashalgorithmus/Originaldateien/Dirty-Reihenfolge nicht verändert.
- Server1BtbApi normalisiert ausgehende Abschnittspayloads für Punkt4–8 auf uhrzeit/uhrzeitBis; fehlendes gewünschtes Ende explizit JSONObject.NULL, damit bewusstes Löschen durch den Altclient-Feldschutz nicht unterdrückt wird. Bestehende Entry-ID/Slot/Node/Firma bleiben erhalten.
- Bestehender Zeitspannen-Editor wird weiterverwendet; Anzeige, PDF einschließlich Originalvergleich, Druckmodell, Text-/ZIP-Export verwenden gemeinsame Zeitdarstellung.
- Tatsächlich ausgeführt:14/14 Buildgenerator-Anker PASS;14 Fälle mit den direkt aus den produktiven Kotlin-Editoren extrahierten Java-Regexen auf JVM17 PASS (Einzelzeit, Spanne, Nacht, Legacy-Klammern, nur Ende, gleiche Zeiten, Text ohne Zeit). Statische Verdrahtung für alle fünf Abschnitte, explizites null und Save-/Apply-Schutz PASS.
- Sechs JUnit-Regressionsfälle in BtbEintragszeitTest.kt ergänzt (Legacy/modern, explizites null, Vorrang, Nacht/gleich, leer/Einzelzeit/nur Ende, Identität). **Nicht ausgeführt**: Kotlin-/JUnit-Lauf, Gradle-Build, Lint, Android-UI, Original-/Abschluss-PDF-Runtime, API-/NAS-/Pixel/Poco-E2E. Java-Regexprüfung ist kein Kotlin-/Android-Build. Kontext7-Abfrage zu LocalTime ohne Treffer; keine neue Bibliothek eingeführt.
- Nächste Prüfung bei verfügbarer Buildumgebung: Unit-Tests und Gradle/Lint, danach Portal-Endzeit->Android öffnen/ohne Änderung speichern->Portal/PDF; Ende ändern und löschen;22:00–06:00; bestehendes Abschluss-PDF bei reinem Formatübergang erhalten, echte Änderung ungültig/rot, Foto-/Entry-Identität unverändert; Pixel und Poco.
- Keine server/**-Datei, kein APK, keine Versionsanhebung; technische Version weiterhin1.3.26/200076. Nächste auslieferbare APK benötigt höheren Build und passende Release-MD. Keine Actions gestartet ([skip ci]).
- Noch offen: Firmenzeitbindung REPORT/MANUAL, Foto-Eintragsbindung/GPS, Katalog-Bestandsübersicht, gemeinsame Geräte-E2Es. Vorheriger AND-IMG-2MB-Block source-seitig gesichert; Bitmap-/Upload-E2E bleibt offen.


## AND-FIRM-TIME – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / UNIT-TESTS ANGELEGT, NICHT AUSGEFÜHRT / BUILD UND E2E OFFEN**.
- Auftrag38d19fb011fd0e04b17f973e96e468708a955c55, ergänzende Schutzentscheidung e5a2195ca9a05cddf6a8291184bc78c9713056d9. Ausgangs-Android841d18c0d3cdc9b96860270f05a10161cdbd7987; Server0f82b42da7412a138bf99b6f80b93031e3b5373e unverändert.
- FirmaBlock/Speicher erhalten personalZeitbindung und maschinenZeitbindung unabhängig. Null-Zeitwerte werden als null statt Zeichenfolge gelesen. Bestehender generischer Firmenpayload in Server1BtbApi transportiert Flags in beide Richtungen; kein neuer Serverendpunkt.
- FirmenZeitbindung: REPORT folgt Bericht, MANUAL bleibt bestehen; ohne Flag nur leere bzw. je vorhandenem Wert passende bisherige Berichtzeiten automatisch. Bekannte fremde/individuelle Zeiten und unbekannte zukünftige Kennzeichen werden konservativ erhalten. Nacht wird nicht umsortiert. Unveränderte Werte erhalten ihr Legacy-Flagformat.
- Beide PM-Einstiege erhalten geladenen Modus und leere Werte; neue Firmen starten mit Berichtzeiten statt Jetzt/+10h. Bewusste Firmenzeitwahl ergibt REPORT bei exakt dem Berichtspaar, sonst MANUAL. Laden/programmgesteuertes Nachführen unter pmApplying/pmSyncing-Guard, kein versehentliches Umstellen von MANUAL.
- Datum-/Schicht-Dialog führt automatische Firmenzeiten nach, auch beim Entfernen der Berichtzeiten und nach Ziel-Schicht-Laden. Vorhandener Autosave speichert Bericht+Firmenstand zusammen. Vortags-/Neuberichtsübernahme übernimmt passende automatische Zeiten, bewahrt manuelle Ausnahmen. AutoLinked-Maschinenplatzhalter bleiben ohne eigenen Inhalt leer.
- PM-Sammlung bewahrt vorhandene geladene Firma-ID sowie Teilsektionskennzeichen; Namens-Zusammenführung und bisherige ID-Regel für neue Firmen bleiben erhalten. Reine äquivalente Flag-Nachlieferung bei identischem Zeitpaar bewahrt bestehende JSON-Form (lokaler Save und Snapshot-Write); expliziter MANUAL<->REPORT-Wechsel bleibt echte Entscheidung. Keine Änderung an Originaldateien, Hashalgorithmus, Finalize oder Dirty-Reihenfolge.
- Tatsächlich ausgeführt: gezielte statische Modell-/Speicher-/UI-/Sync-/Guard-/Identitätsprüfungen PASS; bestehende14/14 Generator-Anker PASS,14/14 produktive JVM-Regexprüfungen PASS. Acht JUnit-Fälle für REPORT/MANUAL/Legacy/Null/Nacht/Identität/Unbekannt angelegt, **nicht ausgeführt**. Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E. Context7 abgefragt; keine neue Bibliothek eingeführt.
- Praktische Folgetests: neuer Bericht07–16 mit Personal REPORT und Maschinen MANUAL; Bericht08–17 => nur Personal folgt. MANUAL mit zufällig gleichem alten Zeitpaar bleibt unverändert. Nacht22–06, Entfernen, Vortag, Portal->Android->Portal, Öffnen/Speichern ohne Änderung (Identität/Flags/PDF gültig), echte Änderung/Moduswechsel, Pixel/Poco. Kotlin-Tests/Gradle/Lint zuerst bei verfügbarer Toolchain.
- Keine server/**-Änderung, kein APK, keine Versionsanhebung, keine Actions gestartet ([skip ci]); Version bleibt1.3.26/200076. Für nächste auslieferbare APK höherer versionCode + passende Release-MD.
- Nächste offene Integration: Foto-Eintragsbindung/GPS; danach Katalogübersicht. Vorherige Bild-/Endzeitblöcke bleiben SOURCE-fertig mit offenem Build und Geräte-E2E.

## AND-PHOTO-LINK – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Dauerhafte BTB-Metadatenschicht in SharedPreferences, FotoEntity-Anzeigeangaben mit Room Ignore. Bestehender Room-Konstruktor/DB-Version unverändert; Originaldateien und Aufnahmefelder werden durch Serverkorrekturen nicht überschrieben. Drei Foto-Flows reagieren auf Metadatenänderungen; Unterknotenfilter verwendet wirksamen Ort. Fotoauswahl gruppiert Abschnittswechsel neu und erhält gewählte IDs.
- Stabile clientEntryKey-Zuordnung statt Text-/GPS-Abgleich; bestehende und eigene Fotos übernehmen wirksame Servermetadaten über eine dauerhafte BTB-Korrekturschicht. GPS/Zeit/Genauigkeit können unbekannt bleiben. PDF, Detailansicht und Export verwenden effektive Werte. Originale bleiben erhalten.
- Bestätigte Bindungs-/Kontext-/Textänderungen werden nur bei unverändertem Pendingwert quittiert; spätere lokale Eingaben bleiben erhalten. Unbekannte Unterbaustellen werden nicht still auf Hauptbaustelle umgebogen. Read-only/Lifecycle-/Outbox-/Originalschutz bleibt auf bestehenden Pfaden.
- Grenzen: Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E-Nachweis. Kein APK/Versionssprung, keine Actions gestartet ([skip ci]), kein Servercode geändert. Technischer Stand 1.3.26/200076. Original-EXIF vor Galerie-Konvertierung gelesen; Context7 zu AndroidX ExifInterface ausgewertet, keine neue Bibliothek.

## AND-CATALOG-INVENTORY – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- In beiden Katalog-Aktionsmenüs neue Aktion Vorhandene Daten anzeigen. Dialog lädt bei Öffnen den ausgewählten Strecken- oder Bahnhofbestand, filtert nach Kennung/Objektart/Strecke/Bahnhof und zeigt zunächst 40 Treffer mit Weitere anzeigen. DialogFragment verwaltet Fenster-Lifecycle; Job wird bei Dismiss/onDestroyView abgebrochen, Ergebnis nur an sichtbaren Dialog gebunden. Token wird zur Laufzeit aus SecureTokenStore gelesen, nicht in Argumenten gespeichert.
- Bestand rein lesend aus gemeinsamem GLOBAL-Pool, eindeutige Kennungen, einzelne belastbare KM-Abschnitte; kein Zusammenziehen über Lücken. Fehler bleiben Fehler, kein falscher Leerbestand. Bahnhof ohne Kürzel wird nicht über Namen geraten. Keine Original-/Outbox-/Room-/Lifecycle-Fachänderung.
- Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/NAS-E2E-Lauf; adb/Gradle/Kotlin-Toolchain hier nicht vorhanden. Context7 zu AndroidX Lifecycle/DialogFragment ausgewertet. Keine neue Bibliothek, keine server/**-Änderung, keine Actions gestartet ([skip ci]), keine APK/Versionsänderung. Technischer Stand bleibt 1.3.26/200076; spätere APK braucht höheren Build und passende Release-MD.

## AND-CATALOG-FORM – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Baustellenformular besitzt Vorhandene Daten anzeigen bei freier Strecke/Bahnhof und Bahnhof auf Server suchen. Übernahme nur im unveränderten Formular-Kontext; Objekt setzt Radioart/Nummer und lässt KM unverändert, KM setzt nur den angeklickten belastbaren Abschnitt. Nutzer-Kontextwechsel räumt alte Bahnhof-/Objekt-/KM-Auswahl. Laden/Rotation unter Guards; wiederhergestellte Eingaben werden nicht erneut aus DB überschrieben. Lookup-Job/Dialogs bei View-Ende geschlossen, späte Antworten bei Kontext-/Lifecyclewechsel verworfen. WVB sichtbar, typbezogene Nummernhinweise; DKW bleibt.
- Stabile Knoten-/Objektidentität, keine Originaldatei-/Room-Schemaänderung, keine neue Bibliothek und kein Serverumbau. Keine automatische Bulk-Reparatur alter Baustellen. Vorhandene Fach-/Download-/Offlinepfade bleiben Grundlage.
- Kein Kotlin-/Gradle-/Lint-/Emulator-/Geräte-/NAS-E2E-Nachweis; diese Toolchain hier nicht verfügbar. Context7 zu FragmentResult/Lifecycle geprüft. Keine Actions gestartet ([skip ci]), keine APK und keine Versionsänderung. Technischer Stand weiterhin 1.3.26/200076; neue APK nur mit höherem Build und zugehöriger Release-MD.


## AND-BUILD-BUNDLE – Zwischenstand 22.10.2026
- Technischer Testbuild1.3.27/200077 samt passender Release-MD vorbereitet. Keine APK erzeugt/übergeben. Kein neuer sichtbarer Versionsbanner.
- CI39714741475 prüft Code a99e09474c09af33aaa30ee10712131216f44a15: Release-Metadaten/JDK21/offizielle Wrapper-JAR PASS; Gradle Unit/Debug/Release noch IN BEARBEITUNG. Kein Kotlin-/Unit-/APK-/Lint-Erfolg bestätigt. Laufende Joblogs sind über Connector nicht abrufbar.
- Reale CI-Blocker korrigiert: unveränderte offizielle gradle/gradle@v9.3.1-Wrapper-JAR (validierte SHA256 b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13) und gradlew-Modus100755. Ein zusätzlicher transienter Wrapper-Netzwerkfehler wurde beim erneuten tatsächlichen Versuch überwunden.
- Alle13 Quellprüfungen ausgeführt:8 PASS,5 FAIL (13,19,83,85,86).13 erwartet alte writeText-Form; die neuen Formatbewahrungs-Schritte haben den danach vorhandenen Sync-Hook nicht entfernt.19 erwartet wörtliches requestMethod=DELETE; tatsächlicher Aufruf reicht DELETE an requestMethod=method weiter.83/85/86 verlangen teils alte Versionen/Server-/UI-Texte. Diese Prüfungen nicht als PASS umgewertet, Gesamt-CI rot. Keine server/**-Änderung.
- Parallel hinzugekommene BTB-AUTO-016-Dokumentation aus fc9e07861a99f0cafc5b80ed8e0c937998d6c37a erhalten. Diese neue 16-Stunden-Automatik ist weiterhin NICHT implementiert und nicht Teil dieses Testbuild-Codes.
- Lauf: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39714741475 . Abschlussprotokoll und danach Geräte-/NAS-E2E offen.


## AND-BUILD-BUNDLE – echter Unit-/Debug-Nachweis 22.10.2026
- CI39716736906, Code36bebf0a24078f4a81313fe41d5b5f90ba48e9fb, Testversion1.3.27/200077: `:app:testDebugUnitTest :app:assembleDebug` am22.10.2026 03:35:39UTC erfolgreich beendet (Start03:26:31UTC). Kotlin-Kompilierung, Unit-Test-Task und Debug-Assemblierung damit tatsächlich PASS.
- Offizielle Wrapper-Validierung und Gradle-Bootstrap ebenfalls PASS. Der vorherige kombinierte Variantenlauf39714741475 wurde durch CI-Concurrency abgebrochen; daraus kein Compilerfehler abgeleitet. Aktueller Ablauf baut Debug und Release nacheinander.
- Release-Build IN BEARBEITUNG; Lint und Artefakt-Upload noch offen. Keine APK übergeben, Signatur/Updatefähigkeit und Installation/NAS-/Pixel-/Poco-E2E weiterhin ungeprüft.
- Separater Quellvertragsjob weiterhin FAIL (8PASS/5FAIL); Gesamtlauf deshalb nicht grün. Keine server/**-Änderung. BTB-AUTO-016 bleibt dokumentiert, aber nicht implementiert.
- Nachverfolgung: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906


## AND-BUILD-BUNDLE – Abschluss echter CI-Build 22.10.2026
- Testversion1.3.27/200077, geprüfter Code36bebf0a24078f4a81313fe41d5b5f90ba48e9fb. Buildjob119620793503 in Lauf39716736906 abgeschlossen: SUCCESS.
- `:app:testDebugUnitTest :app:assembleDebug` PASS (9m7s), `:app:assembleRelease` PASS (7m57s), `:app:lintDebug` PASS (1m40s). Kotlin-Haupt-/Testquellen tatsächlich kompiliert; keine reine Source-Prüfung. Einzeltestanzahl nicht archiviert, daher keine Anzahl behauptet. Compilerwarnungen vorhanden, kein fehlerfreier Warnungsstatus behauptet.
- Artefakt12400166655 `TB-Helfer-1.3.27-200077`, ZIP35111189Bytes, SHA256dd1b090d67d6ab551e81d4f4da5fa87572d78b0514800cbd74cd490112dee2f4. Original enthält APKs + damalige Release-MD; deren vorbereitender Teststatus durch diesen Nachweis überholt. Aktualisierte Release-MD im Repository und im gesonderten Debug-Testpaket.
- APK-Inhalte geprüft: Debug26566977Bytes, SHA256205ccbb826464a20ec7c3b72999c49546e24c4ca19c3d42d5506b216569169c4, Zertifikatsidentität Android Debug; Release11992339Bytes, SHA256ea53d3d90f45a3afd4a2cda26293f514dfb3a629f2068522efb58c23ac9dc96f, ausdrücklich UNSIGNIERT. Beide Manifestwerte de.tbhelfer /1.3.27/200077 bestätigt. Signaturblöcke/Zertifikat untersucht, keine Geräteinstallation oder vollständige apksigner-Verifikation möglich.
- Quellvertragsjob119620794013 weiterhin FAIL:8PASS/5FAIL (13,19,83,85,86). Gesamtlauf ROT, keine Releasefreigabe. Keine server/**-Änderung; bestehende historische Marker-/Versionsabweichungen bleiben offen.
- Debug-Testpaket enthält nur Debug-APK, passende aktualisierte Release-MD und Testhinweise. Debug-Zertifikat kann mit installierter App inkompatibel sein; kein Datenlöschen/Deinstallieren empfohlen. Pixel/Poco-/NAS-/Portal-E2E und Upgradefähigkeit OFFEN. BTB-AUTO-016 nicht implementiert, nicht Teil dieses Builds.
- Lauf/Artefakt: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906 ; https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906/artifacts/12400166655 (GitHub-Artefaktaufbewahrung bis05.11.2026).


## AND-AUDIT-023 – Übergabe Quellstand 23.10.2026
- 1.3.28/200078 für lokalen Android-Studio-Build vorbereitet; Übergabe über GitHub Desktop. Keine APK erzeugt, kein CI-Lauf ausgelöst ([skip ci]).
- Behoben im Quellstand: verdeckte Cache-/Abruffehler und falscher Nullbestand; ungültige Katalogantworten; pauschale 403->Tokenverlust-Zweige in Geräte-/Team-/Katalog-/Profil-/Legacy-Clients; nicht hochgeladene Helferänderungen bei FINISHED/NOT_PARTICIPATING werden nicht mehr als Applied quittiert.
- Ergänzt: reine Strecke-/Bahnhof-Serversuche über vorhandenes lookup/inventory; gegliederte direkte Einstellungen; Fotospeicherprüfung ohne Lösch-/Verschiebeoperationen; ungenutzten BtbEinzelZip-Code entfernt. Produktive Quellgenerator-/Patch-/Altformatpfade und Nutzerdaten behalten.
- Gesamtinventur main:281 Kotlin-/108 XML-Dateien im Ausgangsstand. XML-Parsing, geänderte Ressourcenverweise, Navigationszielklassen, lexikalische Kotlin-Struktur und gezielte Schutz-/Quellflussprüfungen PASS. Diese Checks ersetzen keinen Kotlin-/Gradle-Build. Vier neue DeviceAuthorizationTest-Methoden vorbereitet, nicht ausgeführt.
- OFFEN: lokaler Build/Unit/Lint und Pixel/Poco-/NAS-/Portal-E2E. Geräteaktivierung, Portal-Logout, Einladungen, Helfer-Schichtende/-fortsetzung und weiterer TB nach Prüfmatrix praktisch abnehmen. Die konkrete Ursache des Screenshot-Abruffehlers ist ohne Runtimefehler nicht bestätigt.
- Bedingte Gegenstellenlücke: aktives TB_RECOVERY_ID erfordert kontrollierten Rebase/X-TB-Recovery-ID; Android unterstützt diesen neuen Wiederherstellungsvertrag noch nicht. Neuer Hinweis blockiert, keine automatische Headerquittierung/Outbox-Wiedergabe. NAS-Konfiguration unbekannt. Server-HEAD a471996610a27b2d718f9835bf9c07930f29858f nur gelesen, keine server/**-Änderung.
- Vollständiger Befund und Abnahmereihenfolge: uebergaben/ANDROID_Pruefung_2026-10-23.md; Release-Datei releases/TB-Helfer_1.3.28_200078.md.
- Bekannte fünf historische Quellvertragsfehler separat offen; BTB-AUTO-016 weiterhin nicht implementiert.


## AND-RECOVERY-024 – Nutzergerät bestätigt Blocker 23.10.2026
- Nutzer-Screenshot zeigt nach Abrufversuch23.10.2026 12:37 den in200078 ausschließlich für HTTP409 SERVER_RECOVERY_REBASE_REQUIRED erzeugten Hinweis. Damit ist der zuvor nur bedingte aktive Wiederherstellungsschutz am tatsächlich angesprochenen NAS nun durch Nutzer-Runtimebeobachtung bestätigt.
- Neues Menü/Serversuche sichtbar, letzter erfolgreicher Synchronisierungsstand weiterhin26.09.; dies ist kein APK-Versionsnachweis. Keine erneute erfolgreiche Synchronisierung behauptet.
- Live-GitHub geprüft: Android5f3288cbafdb21f3b12ed34cf000e0cffd656e10, Servera471996610a27b2d718f9835bf9c07930f29858f. capabilities stellt recovery_epoch_v1/recovery_id bereit; Geräteguard blockiert Fachzugriffe vor normalem Abgleich bei fehlendem X-TB-Recovery-ID.
- Ursache ist die noch fehlende Android-Integration des dokumentierten Wiederherstellungs-/Rebasevertrags. Wiederholt Neu laden oder Geräte-Neuaktivierung allein vervollständigt ihn nicht. Keine App-Daten löschen, keinen Schutz deaktivieren oder Header blind quittieren.
- Erforderlicher Folgeblock: vor jedem erneuten Fachupload verbindlicher lokaler Schreib-/Sync-Barrier, dauerhafte Sicherung von Outbox/Basisständen und zugehörigen Dateien; danach neue Servergeneration getrennt einlesen, alte Cursor/Basisversionen generationstreu behandeln und lokale/serverseitige Änderungen sowie gelöschte Entitäten konfliktbewusst abgleichen; normale Mutationen erst nach abgeschlossenem Abgleich wieder freigeben. Abbruch/Neustart/erneute Wiederherstellung und ausstehende Helferfotos gehören zur Abnahme.
- In diesem Screenshot-Diagnoseblock nur Befund/Checkpoint gesichert. Kein neuer Runtimecode, keine APK, kein Server-/NAS-/Datenreset und kein erfolgreicher Wiederherstellungsabgleich behauptet.


## 2026-10-23 – aktueller Android-Folgeblock 1.3.29 / 200079

- Runtime-Quellstand: `d3df3108fd66920c7c1bc1bfc93d5817eacedf33`; Server-HEAD bleibt `a471996610a27b2d718f9835bf9c07930f29858f`. Keine server/**- oder NAS-Konfigurationsänderung.
- Wiederherstellungssperre vor tatsächlicher Fach-IO; private geprüfte Sicherung, getrennt lesender Serververgleich und ausdrückliche Auswahl einer neuen lokalen Generation implementiert. Prozessneustart erst NACH Sicherung und Auswahl. Alte Dateien/DBs/Einstellungen/Outbox bleiben erhalten; keine automatische Zusammenführung oder Upload-Quittierung alter Ergänzungen.
- CI-Nachweis: [Lauf 39947580253](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39947580253), Android-Compiler und `:app:testDebugUnitTest` erfolgreich: 88 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen. `:app:lintDebug` erfolgreich: 0 Fehler, 391 Warnungen; keine Warnung in neuen recovery-Dateien. JUnit-/Lint-XML aus dem zugehörigen Artefakt geprüft.
- Gesamt-CI weiterhin rot durch fünf bekannte historische Quellvertragsprüfungen (13/19/83/85/86); der Android-Compiler-/Test-/Lint-Job ist grün. Diese Prüfungen wurden nicht durch Versionskommentare oder deaktivierte Checks umgangen.
- Kein assemble, keine APK-Erzeugung/-Auslieferung. Kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis. Technische Validierung ist keine funktionale Gerätefreigabe.
- Übergabe/Prüfmatrix: `projektsteuerung/uebergaben/ANDROID_Wiederherstellung_200079.md`. Passende Release-Datei: `projektsteuerung/releases/TB-Helfer_1.3.29_200079.md`.

- RecoveryHttp deckt alle bisherigen direkten HttpURLConnection-Erzeugungen ab, einschließlich direkter Antwortstreams und Header. Worker prüfen die Sperre vor lokaler Facharbeit; neue Generationen verwenden getrennte Room-Dateien, appinterne/externe App-Dateien und Geschäftseinstellungen. Token und aktivierter Server bleiben global.
- Archiv enthält typisierte transaktionale DB-Exporte, Originaldateikopien und typisierte aktuelle Geschäftseinstellungen; commit wartet auf ausstehende apply-Schreibvorgänge. Verzeichnisfehler brechen ab. Ein fehlgeschlagener Versuch entfernt nur eigene neue Kopien/Manifestdateien.
- Wartung & Diagnose bietet „Gesicherter Handybestand“ zur anmeldegebundenen Einsicht und zum bewussten Export.


## AND-NODE-DELTA-023 – Quellstand / Validierung gestartet (23.10.2026)
Status: QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / BUILD UND E2E OFFEN.
- WORKSITE_NODE im schnellen Feedfilter; ausschließlich betroffene gebundene Baustellenstruktur vor BTB-/Foto-Ortsauflösung lesen. Cursor erst nach erfolgreichem Apply; bei lokalem Dirty-/Pending-/Konfliktstand kein Snapshot-Overwrite und kein Cursorfortschritt für diese Seite.
- Strukturänderungen vor Room-Schreiben persistent baustellenbezogen markieren. Gemeinsamer lokaler Mutex für Schreiben/Apply, Vergleich vor/nach HTTP, generationstreue Quittierung. Bestehende clientNodeKeys/Room-IDs bleiben erhalten. Neue Schlüssel nur für wirklich ungebundene Knoten. Vorhandene Mutation samt Basisversion/Inhalt bleibt bei Retry unverändert; keine automatische Konflikt-Rebase. Bestätigte Serverversionen separat merken; unbekannte/geänderte Basis konservativ blockieren statt raten. Keine Migration/Originaldatenänderung.
- Selektivworker verarbeitet geänderte Struktur zusätzlich, BTB und Medien weiterhin anhand exakt betroffener IDs. Fehlende Server-BTB-ID startet keinen baustellenweiten Medienlauf. Neuer Save während Worker-Abschluss erhält mit APPEND_OR_REPLACE einen Folgelauf. Offline-Scopes bleiben persistent, Recovery-Barriere bleibt aktiv.
- BTB-Status unterscheidet lokal gespeichert, Übertragung ausstehend/laufend, Konflikt/Fehler und generationstreue Serverbestätigung nach BTB+Medien+lokalem Apply. Download-Zeitstempel bestätigt keine lokale Mutation. Gemeinsamer Unterbaustellen-Formatter zeigt Bahnhof plus Gleis/Weiche plus optionalen Kurzname in BTB-Ortswegen/Fotos/Listen.
- Tatsächlich ausgeführt: 16/16 gezielte Quellfluss-/Schutzprüfungen PASS. Zehn JVM-Unit-Tests hinzugefügt; Kotlin-/Unit-/Lint-Ergebnisse noch ausstehend. Kein adb/Gradle/Kotlin lokal. Bestehende Android-CI enthält Unit/Lint ohne assemble/APK; genau einen Lauf mit dem Implementierungscommit anstoßen. Kein Versionssprung, keine APK/Veröffentlichung und kein NAS-Deployment.
- Server zwischenzeitlich6303e25d4e3401f4de4c4f8dba0c05c1b33e604e: Vergleich zum ursprünglichena4719966 geprüft; nur Portal-Anzeige/Tests/Dokumentation, kein API-Vertragsumbau. Android 1.3.29/200079 unverändert; kein neues auslieferbares Paket erstellt.
- Praktische Abnahme OFFEN: PC->Pixel/Poco-Strukturänderung ohne Vollsync, Bahnhof MM Gl4 versus Berchtesgaden Gl4, Kurzname, Weiche, neue/umbenannte/gelöschte Knoten, Offline-Save/Neustart, zweite Eingabe während Upload, parallele PC-Änderung/409, BTB+eigene Bilder ohne fremde Bilder, Wiederherstellungssperre. Fehlender Basisstand alter lokaler Änderungen erfordert bewusste Konfliktprüfung; kein automatisches Überschreiben.


## AND-NODE-DELTA-023 – Abschluss des Quellstands (23.10.2026)
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / KOTLIN-, UNIT- UND LINT-PRÜFUNG BESTANDEN / GERÄTE-E2E OFFEN**.
- Geprüfter finaler Runtimecode: `60e8dca5ae509bd6444302d13025996f9283f35b`. Letzter überprüfter Server-HEAD: `6303e25d4e3401f4de4c4f8dba0c05c1b33e604e`. Keine server/**- oder NAS-Konfigurationsänderung.
- Finale CI: [39996754175](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39996754175), Androidjob120562551153 SUCCESS. `:app:testDebugUnitTest` inklusive Kotlin-Kompilierung PASS (3m43s); `:app:lintDebug` PASS (1m27s). JUnit-/Lint-XML aus Artefakt12521180668 tatsächlich gelesen: **98 Tests,0 Fehler,0 Fehlschläge,0 übersprungen; zehn neue WorksiteDeltaPolicyTest-Methoden PASS; Lint0 Fehler/0 Fatal/391 Warnungen**. ZIP-SHA25677ce457c2b8dbc23f5e7696936ef035d8023c715289a0477782866ae99c42de5.
- Gesamt-CI bleibt ROT ausschließlich durch dieselben fünf dokumentierten Quellvertragsprüfungen13/19/83/85/86 (alte Schreib-/DELETE-Textmarker, alte Versionen/Server-/Update-Texte). Original-Logs des finalen Jobs120562550454 gelesen; Strukturprüfung18, Schema13/17 und weitere bestehende Schutzprüfungen PASS. Keine Prüfung deaktiviert/abgeschwächt und kein Fehler als Erfolg umgewertet.
- Neuer Vertrag im Client: WORKSITE_NODE im schnellen Feed; nur betroffene gebundene Baustellenstrukturen vor BTB-/Foto-Ortsauflösung; Cursor nach erfolgreichem Apply. Dirty/Pending/Konflikt/Paralleländerung blockieren Überschreiben. Nur LEAD darf eigene Struktur-Generation quittieren; vorgemerkter Helferbestand bleibt bei reinem Download erhalten. Bestehende Schlüssel/IDs und zusätzliche Serverpayloadfelder bleiben erhalten; bei bewusstem Bahnhofwechsel wird ein alter Bahnhofscode nicht weiter als neue Identität verwendet.
- Lokal speichern → dauerhafte selektive Vormerkung → betroffener BTB plus Bilder, zusätzlich geänderte Struktur. Explizite Statusgenerationen verhindern falsche Serverbestätigung durch Download oder älteren Upload. Bereits gespeicherter BTB-Konflikt blockiert den selektiven Export. Baustellenliste zeigt Strukturstatus/Probleme reaktiv. Unbekannte/abweichende Struktur-Basisversion konservativ erhalten statt automatisch rebasen.
- Reproduzierbarer lokaler16er Source-Check auf finalem Code erneut PASS. Keine APK/assemble, keine Versionsänderung/Release-Veröffentlichung; technischer Stand1.3.29/200079. CI-Validierung ist kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis.
- Übergabe und praktische Abnahme: [ANDROID_WORKSITE_NODE_023.md](uebergaben/ANDROID_WORKSITE_NODE_023.md). OFFEN: Portal->Pixel/Poco, Bahnhof/Gl4/Kurzname/Weiche, Struktur anlegen/ändern/löschen, Offline/Neustart, zweiter Save während Upload, parallel409/Basisstand unbekannt, Helfer, gezielte Bilder und Recovery-Barriere. Bekannte Basis-/Baumkonflikte erfordern bewusste Prüfung; kein automatischer Datenreset.
- Übernahme des Android-Quellstands über GitHub Desktop/Android Studio. **Für diesen Android-Auftrag keine NAS-Übernahme nötig; kein Deployment durchgeführt.** Separater Portalstand bleibt eigener Serverauftrag.


## AND-RELEASE-080 – konsistente Buildvorbereitung 23.10.2026
Status: QUELLSTAND VORBEREITET / METADATENPRÜFUNG BESTANDEN / APK-BUILD200080 UND GERÄTE-E2E OFFEN.
- Nutzerhinweis auf fehlende Release-MD behoben: app/build.gradle.kts jetzt1.3.30/200080 und projektsteuerung/releases/TB-Helfer_1.3.30_200080.md passend ergänzt. Dateiname, Versions-/Buildfelder und alle Pflichtfelder/Releaseabschnitte gezielt geprüft. Bisherige200079-Datei unverändert erhalten.
- Nur Versionsmetadaten und Begleitdokumentation geändert; Runtime-Fachcode unverändert. Die98 bestandenen Tests und Lint0 Fehler/391 Warnungen aus CI39996754175 beziehen sich ausdrücklich auf60e8dca unter200079. Keine neue200080-APK und kein neuer Gradle-/CI-Nachweis behauptet; keine CI ausgelöst ([skip ci]).
- Nächster lokaler Android-Studio-Build soll200080 verwenden. Vor Weitergabe APK-Manifest kontrollieren und ausschließlich die zugehörige Release-MD mit dieser APK verwenden. Kein alter APK-/neuer-MD-Mix. Keine neue sichtbare Buildanzeige, keine Veröffentlichung und kein NAS-Deployment.
