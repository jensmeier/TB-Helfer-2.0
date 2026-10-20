# TB-HELFER 2.0 – MASTERSTATUS

Stand: 2026-09-27

> Live-HEADs immer zuerst direkt in GitHub prüfen. GitHub-Code und Branches haben Vorrang vor historischen Abschnitten weiter unten.

- ANDROID: `feature/tb-android-2.0`, Fachcode-HEAD vor aktueller Statussicherung `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`, aktuell kein offener Android-Draft-PR
- SERVER: `feature/tb-server-1.0`, zuletzt geprüft `069d371b4b5a151dd32b400b1896dd249dbe3afc`, Draft-PR #6
- Android Zielbuild: **1.3.26 / 200076**
- Server: **1.0.0-dev**
- Server-Migration 016: **praktisch angewendet/bestätigt**

## Android – aktueller Arbeitsstand
- 1.3.26 / 200076: Bildtransport, BTB-Foto-PATCH und Wetter-mm source-seitig umgesetzt.
- Handy-Volloriginal bleibt lokal; mobile Sync-JPEGs max. 1920 px/JPEG82.
- Server→Android-Bilder verwenden `preview`; BTB-/Mangel-/Abnahmebilder und Bild-Anlagen sind im kleinen Uploadpfad.
- Wetter erhält bekannten 0,0-mm-Wert und zeigt mm in App/PDF/Druckmodell.
- Sourcechecks + 14/14 Generatoranker PASS.
- **BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN.**

## Server – aktueller Arbeitsstand
- Rollen-/Schichtvertrag, FINALIZED-read-only und Teamlead-Finalize verbindlich.
- Migration 016 für BTB-Foto-Link-Metadaten praktisch bestätigt.
- PORTAL-LIVE-001 ist **BESTÄTIGT** und geschützt; kein F5 im bestätigten Livepfad.
- Server-Renditions vorhanden: preview 1280/JPEG82, thumb 320/JPEG72.
- Kein Serverumbau für Android-Bildkompression nötig.

## Geschützt / bestätigt
- **BTB-SYNC-002 BESTÄTIGT**: Pixel↔Portal bidirektional inkl. Parallel-Autosave-/Dirty-Schutz.
- **BTB-UI-001 BESTÄTIGT**: stabile Haupt-TB-Abschlussleiste.
- **PORTAL-LIVE-001 BESTÄTIGT**: offene Portalansichten aktualisieren ohne F5.
- **UPD-001 BESTÄTIGT**: Updatepfad/Versionsschutz.
- Diese Punkte nicht ohne reproduzierbaren neuen Fehler wieder öffnen.

## Aktuelle Integrationsrisiken
1. Build/Installation 1.3.26 / 200076.
2. HELPER/Poco-E2E mit aktuellem Build.
3. Foto-E2E: kleine Uploaddatei, preview auf anderem Handy, Foto-Metadaten-PATCH.
4. Wetter-E2E: tempC + 0,0 mm Android↔Server↔Portal↔PDF.
5. BTB-Finalize-E2E in beide Richtungen Android↔Portal/zweites Handy.
6. vollständiger 15×15-Gesamtlasttest bleibt nach funktionalem E2E bzw. Hardwareausbau offen.

Alles mit `☑`, `BESTÄTIGT`, `ERLEDIGT`, `PASST`, `FUNKTIONIERT` oder `Nicht ohne Grund ändern = JA` bleibt geschützt.

## Priorität ab 26.09.2026 – Funktions-E2E vor Last-E2E

Verbindliche Reihenfolge:
1. zuerst ein verantwortliches TB-Handy sauber gegen Server/Portal E2E zum Laufen bringen;
2. danach den HELPER-/Poco-Syncfehler gezielt lösen und Helfer-E2E bestätigen;
3. erst danach den vollständigen 15×15-Gesamtlasttest wiederholen.

Der bereits praktisch gelaufene Delta-Wait-Kapazitätstest mit 255 getrennten Portal-Sessions war erfolgreich (255/255, keine Request-Fehler). Er ist ein **Kapazitätsteiltest**, kein vollständiger 15×15-E2E. Nach dem geplanten Ausbau auf 8 GB RAM bzw. einem NAS-Wechsel wird der Last-/Kapazitätstest auf der dann maßgeblichen Hardware erneut ausgeführt.

Bis dahin gilt: Funktionsfehler zuerst reproduzierbar eingrenzen; keine Performance-Umbauten auf Verdacht.


## IMG-001 Quellaudit 26.09.2026
Status: **ABWEICHUNG BESTÄTIGT / IMPLEMENTIERUNG NACH E2E-PRIO-001**

Quellprüfung:
- Android übergibt BTB-, Mangel- und Abnahmefotos aktuell aus dem lokalen Originalpfad an den Upload.
- `Server1FileApi.uploadLinkedFile()` überträgt genau diese Datei (`expected_bytes = file.length()`).
- Android lädt Serverbilder aktuell über `/api/v1/files/{id}/content`; `thumb/preview` werden vom Android-Dateiclient nicht genutzt.
- Der Server speichert empfangene Bytes unverändert, besitzt aber bereits `thumb` (320 px) und `preview` (1280 px).
- Das lokale 512-px-Thumbnail ist vorhanden, wird aber nicht als Online-Sync-Artefakt verwendet.
- BTB-Metadaten sind teilweise strukturiert; GPS-Genauigkeit fehlt. Mangel-/Abnahmefoto-Metadaten sind im Uploadpfad unvollständig.

Detaudit: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.

Priorität bleibt gemäß E2E-PRIO-001:
1. NET-001/Hauptgerät-Erreichbarkeit,
2. Haupt-TB-Handy ↔ Server/Portal,
3. HELPER/Poco,
4. danach IMG-001-Implementierung.

## Praxisnachtrag 26.09.2026 – Pixel wieder online / Poco Erstabgleich
- Pixel hat nach den vorherigen App-DNS-Fehlern inzwischen wieder mit dem NAS synchronisiert. NET-001 ist damit aktuell **nicht mehr als dauerhafter Verbindungsblocker nachgewiesen**, Ursache des vorherigen Fehlers bleibt ungeklärt.
- Poco wurde neu installiert und erneut aktiviert. Der anschließende erste Datenabruf läuft sehr lange (>3 Minuten) und ist noch nicht als erfolgreich/fehlgeschlagen abgeschlossen.
- Priorität bleibt: Hauptgerät↔Portal-Fachkette bestätigen, danach Poco-Erstabgleich gezielt messen/eingrenzen.

## Praxisnachtrag – Poco-Erstabgleich fertig / neuer BTB 26.09 inkonsistent
- Poco-Erstabgleich ist abgeschlossen; Baustelle konnte erfolgreich heruntergeladen werden.
- Pixel hat einen neuen BTB für 26.09 angelegt.
- Portal zeigt weiterhin nur BTBs bis 25.09; der 26.09-BTB erscheint dort nicht.
- Poco zeigt den 26.09-BTB kurzzeitig und fällt etwa eine Sekunde später auf „kein BTB vorhanden“ zurück.
- Damit ist jetzt ein konkreter BTB-Sync-/Lifecycle-Fehler reproduziert. Vor Codeänderung Serverzustand, Team-`active_btb_id`, BTB-Status und Android-Snapshot-Anwendung prüfen.

## BTB-SYNC-001 Diagnose – gemischte Android-Versionen erkannt
- Server-Diagnose zeigt aktive Geräte mit unterschiedlichen Appständen:
  - Pixel / `zinkel.jens`: **TB-Helfer 1.3.4**
  - Poco / `poco`: **TB-Helfer 1.3.19**
- Aktueller Android-Zielstand ist 1.3.19 / Build 200069.
- Im Change-Feed sind am 26.09. mehrere aufeinanderfolgende `UPDATE`-Einträge für denselben BTB sichtbar (Versionen mindestens 14–19).
- Dadurch ist der aktuelle Pixel↔Poco-E2E nicht versionsrein. Vor Codeänderung Pixel auf den aktuellen bestätigten Android-Zielstand bringen bzw. Installation 200069 nachweisen.

## Korrektur – Pixel ist tatsächlich 1.3.19
- Benutzer bestätigt: Pixel läuft ebenfalls auf **1.3.19**.
- Die Server-DB-Anzeige `tb.devices.app_version = 1.3.4` ist **kein verlässlicher Runtime-Versionsnachweis**.
- Quellprüfung Server: `app_version/app_build` werden beim `/api/v1/device/login` gespeichert; normale authentisierte Requests aktualisieren nur `last_seen_at`.
- Pixel wurde nach einem späteren App-Update nicht neu aktiviert, daher bleibt der alte Aktivierungswert in `tb.devices` stehen.
- Die zuvor angenommene Versionsabweichung als Ursache von BTB-SYNC-001 ist damit **verworfen**. BTB-SYNC-001 bleibt unverändert offen.

## BTB-SYNC-001 Diagnose – 26.09 ist auf dem Server vorhanden
- Der 26.09-BTB ist in `tb.btbs` vorhanden und erzeugt Change-Feed-Einträge.
- Sichtbarer BTB: `ba2144b7-9ede-4f53-abdc-1accd172e875`.
- Am 26.09 wurden für genau diesen BTB wiederholt `UPDATE`-Changes beobachtet, mindestens Version 7 bis 19 in kurzer Folge.
- Damit ist „Pixel lädt den 26.09-BTB gar nicht zum Server“ ausgeschlossen.
- Offen bleiben Team/`active_btb_id`/Status und warum Portal/Poco den serverseitig vorhandenen BTB nicht stabil anzeigen.

## BTB-SYNC-001 Diagnose – Server-Lifecycle korrekt

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

## BTB-SYNC-001 Portal-Nachtest – 26.09 sichtbar
- Nach vollständigem Neuladen der Portal-Baustellenseite wird der 26.09-BTB korrekt angezeigt:
  - Team `pixeljens`
  - aktiver BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`
  - Status `ACTIVE`
  - Datum `2026-09-26`
- Der vorherige Portalbefund „nur bis 25.09“ war daher ein nicht aktualisierter Seitenstand, kein fehlerhafter Server-/Portal-Datensatz.
- Offen bleibt nur noch der Poco-/Android-Lese-/Apply-Pfad, bei dem der 26.09-BTB kurz erscheint und danach lokal verschwindet.

## BTB-SYNC-001 Source-Diagnose – Active-Pointer wird lokal wieder gelöst
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.


## BTB-SYNC-001 Android-Fix 26.09.2026
Status: **SOURCE-FIX COMMITTED / BUILD + INSTALLATION + E2E OFFEN**

- Verbindliche Server-vor-Lokal-Vorrangregel aus Server-Commit `e71585cb21fa13c01f184036337d3ea68725f37e` wurde vor der Implementierung in den Android-Branch gespiegelt.
- Android-Codefix: `f57c532863d2f92b81729b9af1466b57a22f8723`.
- `BtbActiveContext.laden()` behandelt vorhandenen `BtbServerStateStore` jetzt kanonisch:
  - `active=true` → aktiver Pointer bleibt bestehen; lokaler PDF-/Baseline-/ABGESCHLOSSEN-Status wird nicht ausgewertet.
  - `FINALIZED && active=false` → Pointer darf gelöst/inaktiv markiert werden.
  - sonstiger vorhandener Serverstatus → lokaler Abschlussstatus darf den Server-Lifecycle nicht überstimmen.
  - rein lokale/Legacy-BTBs ohne Server-Snapshot behalten die bisherige Abschlusslogik.
- Keine `server/**`-Datei geändert.
- Version ist jetzt `1.3.20 / 200070`.
- GitHub-CI konnte für den aktuellen HEAD nicht als Buildnachweis gestartet werden; regulärer PR #7 und temporärer Build-PR waren nicht mergebar, daher kein Workflow-Run. Lokaler Android-Studio-Build bleibt erforderlich.


## REL-001 – Buildnummern strikt fortlaufend
Status: **VERBINDLICH**

- Ein neuer installierbarer/testbarer Android-Stand erhält immer einen höheren `versionCode` als der vorherige Build.
- Der BTB-SYNC-001-Fix wird daher **nicht** erneut als 1.3.19 / 200069 ausgeliefert.
- Neuer Zielstand: **1.3.20 / 200070**.
- Bereits verwendete/veröffentlichte Buildnummern werden nicht wiederverwendet.
- Hintergrund: Updatekatalog und Android-Updateerkennung müssen einen eindeutig neueren Build erkennen können.


### 1.3.20 / 200070 Quellstand
- Buildversions-Quellstand `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- `app/build.gradle.kts`: `versionName=1.3.20`, `versionCode=200070`.
- CI-Artefaktname und Release-Datei wurden ebenfalls auf 1.3.20 / 200070 umgestellt.
- Source-Check der Versionswerte: **PASST**.
- Build selbst: **OFFEN**.


## UPD-001 – Update installiert, UI meldet weiter Update
Status: **FEHLER REPRODUZIERT / URSACHE OFFEN**

Praxisbefund nach Update auf den neuen Android-Stand:
- Updatevorgang ist durchgelaufen.
- Unter „Nach Update suchen“ wird weiterhin die alte installierte Version **1.3.19** angezeigt.
- Auf der Hauptansicht bleibt „Update verfügbar“ sichtbar.
- Erwartung: Nach erfolgreicher Installation von **1.3.20 / 200070** muss die App ihre **tatsächlich installierte Package-Version** anzeigen und darf 200070 nicht erneut als Update anbieten.

Nächster Diagnoseblock: Android-Updateanzeige, lokale/Server-Versionsquelle, Cache und Vergleichslogik prüfen. Keine Neuinstallation/Neuaktivierung auf Verdacht.


## UPD-001 Quellursache
Status: **URSACHE IM UPDATEPFAD EINGEGRENZT / SCHUTZFIX NÄCHSTER BUILD**

Quellprüfung:
- Wartung und Startseite zeigen die lokale Version aus `BuildConfig.VERSION_NAME/VERSION_CODE`.
- `AppUpdateManager.check()` sendet genau diesen lokalen Build an den Server; HTTP-Caches sind deaktiviert.
- Wenn nach dem Installer weiter 1.3.19 angezeigt wird, läuft auf dem Gerät weiterhin ein APK mit 1.3.19/200069; das ist kein UI-Cache.
- Server-Release-Metadaten (z. B. 1.3.20/200070) und hochgeladene APK werden serverseitig aktuell **nicht** auf interne APK-Version/Package-ID gegengeprüft.
- Android prüft die heruntergeladene APK derzeit nur auf SHA-256/Bytegröße, nicht auf internes `versionName/versionCode`.

Damit kann eine alte APK unter einem neuen Release-Metadatensatz veröffentlicht werden und anschließend in einer Update-Schleife landen.

Nächster auslieferbarer Schutzfix: **1.3.21 / 200071**.


### UPD-001 Android-Schutzfix committed
- Codecommit: `19d688a4752c98209570e0ff69ccd2eae789541e`.
- Neuer Zielstand: **1.3.21 / 200071**.
- Android liest die heruntergeladene APK jetzt vor Installation mit `PackageManager.getPackageArchiveInfo()`.
- Geprüft werden Package-ID, `versionName` und `versionCode` gegen den Server-Releaseeintrag.
- Bei Abweichung wird die APK gelöscht und der Installer nicht gestartet.
- Damit kann ein versehentlich unter falschen Release-Metadaten hochgeladenes altes APK nicht mehr unbemerkt in eine Update-Schleife führen.
- Build/Installation/Update-E2E bleiben offen.


### REL-FILE-001 CI-Schutz
- Commit `e76933cd7270da21d8cd251ece1cba87c7db0cf8`.
- Android-CI ermittelt künftig `versionName/versionCode` direkt aus `app/build.gradle.kts`.
- Für genau diesen Stand muss `projektsteuerung/releases/TB-Helfer_<version>_<build>.md` existieren.
- Pflichtfelder der Release-MD werden gegen Version/Build geprüft.
- Das Build-Artefakt enthält automatisch APK(s) plus die zugehörige Release-MD desselben Builds.


## UPD-001 bestätigt 26.09.2026
Status: **BESTÄTIGT**

- Benutzer bestätigt den Updateweg mit **1.3.21 / 200071** als funktionierend.
- Passende Release-MD + frisch gebaute APK wurden gemeinsam verwendet.
- Updateinstallation lief erfolgreich.
- Der vorherige Fehlerzustand „weiter 1.3.19 / Update verfügbar“ ist damit für diesen Testlauf nicht mehr vorhanden.
- UPD-001 gilt als praktisch bestätigt.
- Pixel/Poco-BTB-E2E bleibt davon getrennt weiterhin offen.


## BTB-SYNC-001 E2E 1.3.21 weiterhin fehlerhaft
Status: **FEHLER REPRODUZIERT / SOURCE-FIX NICHT AUSREICHEND**

Praxis auf **1.3.21 / 200071**:
- Pixel ist der verantwortliche TB, aber die untere Aktion **„BTB Abschluss“** springt weiterhin sichtbar hin und her.
- Poco zeigt den aktiven BTB **26.09** nur sehr kurz und fällt direkt wieder auf **„kein BTB vorhanden“** zurück.
- Damit ist der Fix `f57c532...` source-seitig zwar vorhanden, praktisch aber **nicht ausreichend**.
- Server/Portal-Lifecycle für denselben 26.09-BTB war zuvor bestätigt ACTIVE; kein neuer Server-/NAS-Workaround auf Verdacht.
- Nächster Schritt: Android Runtime-/Sourcepfade um `BtbServerStateStore`, Active-Pointer und Lifecycle-Watcher vollständig verfolgen; insbesondere prüfen, ob ServerState zeitweise fehlt/gelöscht wird oder unter einer anderen BTB-ID gespeichert wird.


## BTB-SYNC-001 zweite Source-Ursache 1.3.22
Status: **URSACHE BESTÄTIGT / SOURCE-FIX COMMITTED / BUILD OFFEN**

Nach fehlgeschlagenem 1.3.21-Geräte-E2E wurde ein zweiter konkurrierender Lifecycle-Pfad gefunden:
- `BtbPatch200033Installer.startActivePointerGuard()` läuft parallel zum modernen Lifecycle-Watcher alle **450 ms**.
- Der alte Guard konnte bei `activeId == blockedId` den Active-Pointer direkt lösen, ohne `BtbServerStateStore` zu beachten.
- Das erklärt den Poco-Ablauf: Server-Sync aktiviert 26.09 → alter Guard sieht stale Abschlussmarker → löscht Pointer → „kein BTB vorhanden“.
- Zusätzlich wurde `BtbPatch200033Installer` nach `BtbModernLifecycleInstaller` installiert und überschrieb dadurch den modernen server-/rollenbewussten Abschluss-Click-Handler.

Fixcommit: `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`.
- alter Pointer-Guard ist für servergebundene BTBs jetzt serverkanonisch;
- `active=true` behält Pointer und räumt stale Inaktiv-Marker weg;
- `FINALIZED && !active` darf Pointer lösen;
- Legacy ohne ServerState behält historisches Verhalten;
- Installer-Reihenfolge geändert: `BtbModernLifecycleInstaller` wird zuletzt gebunden.

Zielbuild: **1.3.22 / 200072**.
Source-Check: **PASST**. Build/Installation/E2E: **OFFEN**.


## BTB-SYNC-002 – Android Source-Fix 1.3.23
Status: **SOURCE-FIX UMGESETZT / BUILD + INSTALL + E2E OFFEN**

- P0-Datenverlustursache war Android-seitig: Dirty-Pixelstand konnte vor Outbox-Export durch einen Server-Snapshot überschrieben werden.
- `SelectiveSyncWorker` exportiert jetzt zuerst lokal → Pending/Outbox und prüft Serverversion/Conflict erst danach.
- `CollabLocalBridge.applyServerStateToLocal()` überschreibt keinen BTB mit Pending/Dirty-Stand.
- Lokaler Save und Server-Apply sind pro BTB serialisiert; neuere Autosaves während Upload bleiben dirty.
- Zielbuild: **1.3.23 / 200073**.
- Keine `server/**`-Änderung; Server-/DB-Vertrag bleibt unverändert.
- PORTAL-LIVE-001 bleibt separat SERVER-seitig: Source-Fix vorhanden, Deploy/E2E offen.

Nächster E2E:
Pixel `ABC` → Portal muss `ABC` sehen → Portal `XYZ` → Pixel erhält `XYZ`, ohne einen parallel neueren lokalen Stand still zu verlieren. Danach Poco.


## BTB-SYNC-002 – Praxisbestätigung 1.3.23 / 200073
Status: **BIDIREKTIONALER BASIS-E2E BESTÄTIGT / PARALLEL-AUTOSAVE NOCH OFFEN**

- Benutzer bestätigt: Pixel → Portal funktioniert.
- Benutzer bestätigt: Portal → Pixel funktioniert.
- Der ursprüngliche asymmetrische Datenverlustpfad ist damit im normalen Hin-und-zurück-Test nicht mehr reproduziert.
- Offen bleibt der gezielte Parallel-Autosave-/Conflict-Test während eines laufenden Uploads sowie danach der separate Poco/HELPER-E2E.
- Server/API/DB unverändert.


## BTB-UI-001 – Abschlussleiste nach Rollenwechsel
Status: **FEHLER REPRODUZIERT / SOLL VERBINDLICH**

Praxis 26.09.2026 auf Pixel:
- Pixel ist aktuell verantwortlicher TB/Haupt-TB.
- Poco/Helfer wurde aus der aktiven Helferrolle entlassen.
- Untere BTB-Aktionsleiste springt sichtbar zwischen:
  - nur `BTB Abschluss`
  - `PDF Entwurf` + `BTB Abschluss`
- Für den verantwortlichen TB im aktiven BTB ist der korrekte stabile Zustand:
  **`PDF Entwurf` + `BTB Abschluss`**.
- Rollenabhängige Helferansicht bleibt ohne PDF-Entwurf und mit eigener Schichtaktion gemäß Serverrolle.
- Kein periodisches Umschalten der Button-Sichtbarkeit zulässig.


### BTB-UI-001 Source-Fix 1.3.24
- Codefix: `7fdb4dad2e72515dcda958c00bc2065c38d9f5da`.
- Versionierung/Release: `446312c8c09a99ed71950fe3b08adc53070ddc91`.
- Haupt-TB-Zweig zeigt jetzt stabil `PDF Entwurf` + `BTB Abschluss`.
- Vorgelagerte pauschale VISIBLE-Schaltung von `btnPdf/btnPdfFinal` entfernt.
- HELPER bleibt ohne PDF-Entwurf mit rollenbezogener Schichtaktion.
- Zielbuild **1.3.24 / 200074**.
- Source-Check der geänderten Stellen: **PASST**.
- Build/Installation/Praxis-E2E offen.


## BTB-UI-001 Praxisbestätigung 1.3.24
Status: **BESTÄTIGT**
- Pixel-Haupt-TB zeigt stabil PDF Entwurf + BTB Abschluss.
- Kein Springen mehr zwischen Ein- und Zwei-Button-Zustand.
- Poco/HELPER-E2E bleibt separat offen.


## BTB-SYNC-002 vollständig bestätigt
Status: **BESTÄTIGT / GESCHÜTZT**

Praxis auf Pixel mit aktuellem 1.3.24 / 200074:
- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Parallel-Autosave-/Conflict-Schutz während laufendem Upload: **BESTANDEN**.
- Der zuletzt lokal gespeicherte Pixel-Stand ging nicht verloren.
- Ein bestätigter älterer Server-Snapshot hat keinen neueren lokalen Dirty-Stand überschrieben.
- Damit ist der P0-Datenverlustfehler BTB-SYNC-002 praktisch vollständig bestätigt.

BTB-SYNC-002 ohne neuen reproduzierbaren Fehler nicht erneut ändern.
Nächster Geräte-E2E: **Poco/HELPER**.

## BTB-EDIT-001 – Android-Istmodell-Audit 26.09.2026
Status: **DIAGNOSE ABGESCHLOSSEN / MODELL FESTGELEGT / IMPLEMENTIERUNG NÄCHSTER SCHRITT**

Bestätigte Android-Istlage:
- `SectionItem` enthält aktuell nur `uhrzeit`, `text`, `kmVon`, `kmBis`; keine stabile Eintrags-ID, keine Firma und keine stabile Unterbaustellenreferenz.
- Punkt 4–8 werden lokal als getrennte Listen `arbeiten`, `abnahmen`, `einweisungUnterweisung`, `besonderheiten`, `maengelSchaeden` gespeichert.
- Der aktuelle Editor kodiert die Unterbaustelle in technischen Rohzeilen als `@ORT=<lokale Room-Long-ID>`.
- `buildEntry()` ruft für **alle Punkte 4–8** `sectionFromMultiline(stripOrtMarkers(...))` auf. Damit geht die Unterbaustelleninformation beim Übergang in das strukturierte `SectionItem` verloren.
- Firmenzuordnung wird im manuellen Editor überwiegend als sichtbares Präfix `Firma: Text` behandelt; sie ist im `SectionItem` nicht strukturiert.
- `BtbPhotoContextOverrideStore` speichert aktuell `ortId: Long?` und setzt damit lokal `baustellenKnotenId`; das ist gerätespezifisch und keine dauerhafte Sync-Identität.
- Der Worksite-Tree-Sync besitzt bereits die richtige stabile Infrastruktur: `SyncWorksiteNodeBindingEntity.clientNodeKey` mit Mapping `localKnotenId ↔ worksiteServerId ↔ clientNodeKey`.
- Serverseitig erhalten Android-Einträge bereits stabile Slots über `BtbEntrySlotStore` / `_tb_android_slot`. Diese Identität wird aktuell aber parallel zur Arrayposition geführt und ist noch nicht Bestandteil des lokalen `SectionItem`.
- Damit würde ein Abschnittswechsel über heutige Arrays die Identität nicht zuverlässig beibehalten.

Festgelegtes Android-Zielmodell für einen BTB-Facheintrag:
- `clientEntryKey`: stabile Eintragsidentität/Server-Slot; bleibt bei Punktwechsel erhalten;
- Abschnitt bleibt fachlich Punkt 4–8 und wird beim Editieren als Zielabschnitt gewählt;
- `firmaId` + `firmaName` bzw. Allgemein;
- `worksiteNodeKey`: kanonischer stabiler Node-Key; null = Hauptbaustelle/keine Unterzuordnung;
- `uhrzeit`, `kmVon`, `kmBis`, `text`;
- lokale Room-`Long`-ID darf nur als UI-/Cache-Hilfe existieren, nicht als Syncfeld.

Migration:
- bestehende Servereinträge übernehmen ihren vorhandenen `_tb_android_slot` als `clientEntryKey`;
- neue Android-Einträge erhalten einen stabilen lokalen Entry-Key und behalten ihn bei späteren Punkt-/Firmen-/Node-Änderungen;
- alte `@ORT=<Long>`-Daten werden beim Bearbeiten/Save über das Worksite-Node-Binding auf `worksiteNodeKey` migriert; der lokale Marker bleibt höchstens Legacy-/Editorhilfe.

Foto-Zielmodell:
- bestehende Fotoidentität bleibt unverändert;
- BTB-Fachmetadaten verwenden `worksiteNodeKey` statt lokaler `ortId` als Syncidentität;
- lokale `ortId` darf als Cache/Legacy erhalten bleiben;
- GPS, Aufnahmezeit und Originaldatei werden beim fachlichen Umhängen niemals verändert.

Aktueller Serverstand neu geprüft:
- Server-HEAD `cd9b18fc062a7a3d4354c611a949ed3d8408d709` implementiert inzwischen den benötigten Vertrag.
- `PATCH /api/v1/btbs/{btb_id}/entries/{entry_id}` akzeptiert `section` und erlaubt Wechsel zwischen ARBEIT/ABNAHME/EINWEISUNG_UNTERWEISUNG/BESONDERHEIT/MANGEL_SCHADEN bei gleicher Entry-ID.
- Entry-Payload nutzt `worksiteNodeKey` und validiert ihn gegen die aktive Baustellenstruktur.
- `PATCH /api/v1/btbs/{btb_id}/photos/{file_id}` aktualisiert versionierte linkbezogene `photo_metadata`; Foto-Datei, GPS und Aufnahmezeit bleiben unangetastet.
- Server-Source ist damit vorhanden; Android darf jetzt gegen diesen Vertrag implementieren. Server-Deploy/E2E ist separat zu bestätigen.


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

## Integrationsaudit 27.09.2026
- **Bilder:** Server-Renditions sind vorhanden; Android verletzt den Zielvertrag noch durch Originalupload und `/content`-Download. Android-Fix erforderlich.
- **BTB-Abschluss:** Android und Portal verwenden denselben Server-Finalize-Lifecycle; Change-Feed/SSE/Delta-Kette ist source-seitig geschlossen. Spezifischer bidirektionaler Finalize-E2E bleibt offen.
- **Wetter:** Portal/Server unterstützen `tempC` + `niederschlagMm` inkl. 0 mm. Android macht automatische 0.0 mm derzeit zu null und zeigt mm nicht vollständig an. Android-Fix erforderlich.
- Bestätigter `PORTAL-LIVE-001`-SSE-Pfad bleibt geschützt.

## Android 1.3.26 / 200076 – Integrationsblock 27.09.2026
- **Foto-Transport source-seitig umgesetzt:** Handyoriginal bleibt lokal; Android erzeugt max. 1920-px/JPEG82-Syncdatei. Gilt für BTB-, Mängel-, Abnahmebilder und Bild-Anlagen. Server→Android verwendet `preview`.
- **BTB-Foto-PATCH source-seitig umgesetzt:** bestehende Foto-ID bleibt, Link-Metadaten werden versioniert gepatcht; GPS/Aufnahmezeit/Datei werden nicht durch den Patch geändert.
- **Wetter source-seitig umgesetzt:** 0,0 mm bleibt bekanntes 0,0; mm wird in Android-UI und PDF/Druckmodell angezeigt.
- **BTB-Abschluss:** Source-Vertrag bereits passend und in diesem Block nicht geändert; bidirektionaler Praxis-E2E bleibt offen.
- Zielstand Android: **1.3.26 / 200076**.
- Status: **QUELLSTAND UMGESETZT / SOURCE-CHECK PASS / BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN**.

## Build 200076 – Compilefix
- Lokaler Android-Studio-Build scheiterte an fehlendem `java.util.Locale`-Import in `BautagebuchFragment.kt`.
- Ursache eindeutig Android-Compilefehler, kein Server-/API-/DB-Thema.
- Import-Fix source-seitig umgesetzt; Fachlogik unverändert.
- **1.3.26 / 200076 bleibt Zielbuild**, da der fehlgeschlagene Versuch keine auslieferbare APK erzeugte.
- Nächster Statusschritt erst nach erneutem echtem Android-Studio-Build.


## Korrektur AND-VER-001
- Die zusätzliche sichtbare Versions-/Buildzeile auf der Startseite war nicht beauftragt und wurde vollständig zurückgenommen.
- Technischer Zielbuild bleibt **1.3.26 / 200076**; keine zusätzliche Startseitenanzeige daraus ableiten.


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
- Foto-Eintragsbindung/GPS ist source-seitig ergänzt. Nächste fachliche Integration: Katalog-Bestandsübersicht; gemeinsame Build-/Gerätetests bleiben separat offen.
- Stabile clientEntryKey-Zuordnung statt Text-/GPS-Abgleich; bestehende und eigene Fotos übernehmen wirksame Servermetadaten über eine dauerhafte BTB-Korrekturschicht. GPS/Zeit/Genauigkeit können unbekannt bleiben. PDF, Detailansicht und Export verwenden effektive Werte. Originale bleiben erhalten.
- Bestätigte Bindungs-/Kontext-/Textänderungen werden nur bei unverändertem Pendingwert quittiert; spätere lokale Eingaben bleiben erhalten. Unbekannte Unterbaustellen werden nicht still auf Hauptbaustelle umgebogen. Read-only/Lifecycle-/Outbox-/Originalschutz bleibt auf bestehenden Pfaden.
- Grenzen: Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E-Nachweis. Kein APK/Versionssprung, keine Actions gestartet ([skip ci]), kein Servercode geändert. Technischer Stand 1.3.26/200076. Original-EXIF vor Galerie-Konvertierung gelesen; Context7 zu AndroidX ExifInterface ausgewertet, keine neue Bibliothek.

## AND-CATALOG-INVENTORY – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Gemeinsame Katalog-Bestandsübersicht source-seitig angebunden: Katalog -> Strecke/Bahnhof -> Vorhandene Daten anzeigen. Read-only, kein Datenimport/keine Baustellenzuweisung. Bestehende Katalogdownloads/Offline-Bearbeitung unverändert. Android-Baustellenformular/Lookup und explizite Objektart-Übernahme gemäß PORTAL-021 sind damit noch nicht umgesetzt.
- Bestand rein lesend aus gemeinsamem GLOBAL-Pool, eindeutige Kennungen, einzelne belastbare KM-Abschnitte; kein Zusammenziehen über Lücken. Fehler bleiben Fehler, kein falscher Leerbestand. Bahnhof ohne Kürzel wird nicht über Namen geraten. Keine Original-/Outbox-/Room-/Lifecycle-Fachänderung.
- Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/NAS-E2E-Lauf; adb/Gradle/Kotlin-Toolchain hier nicht vorhanden. Context7 zu AndroidX Lifecycle/DialogFragment ausgewertet. Keine neue Bibliothek, keine server/**-Änderung, keine Actions gestartet ([skip ci]), keine APK/Versionsänderung. Technischer Stand bleibt 1.3.26/200076; spätere APK braucht höheren Build und passende Release-MD.
