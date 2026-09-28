# TB-HELFER 2.0 – MASTERSTATUS

Stand: 2026-09-26

> Live-HEADs immer zuerst direkt in GitHub prüfen. Die SHAs unten sind der Fachstand vor diesem reinen Projektsteuerungs-Umbau.

- ANDROID: `feature/tb-android-2.0`, Fach-HEAD `d272f8a7586cce3ea1eda29100473f7af5d540bb`, Draft-PR #7
- SERVER: `feature/tb-server-1.0`, Fach-HEAD `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`, Draft-PR #6
- Android nächster Build: **1.3.20 / 200070**
- Server: **1.0.0-dev**
- Server-Quellmigrationen: **001–015**

## Android
Pixel-UI-Flackern ACTIVE↔PDF source-seitig korrigiert. Build 200070, Installation und Pixel/Poco/Server-E2E offen. Poco ist serverseitig HELPER im richtigen Team, bekommt auf dem Gerät aber weiterhin keine Daten.

## Server
Rollen-/Schichtvertrag und FINALIZED-Schutz verbindlich. Migration 014 praktisch bestätigt. Migration 015/Foto-Metadaten Deployment/Runtime-E2E offen. Delta-Wait/15×15-Last-E2E offen. Finalize-OOM/502-Fix praktisch bestätigt und geschützt.

## Integrationsrisiken
1. Poco-Sync.
2. Android 200070 Build/Installation/E2E.
3. IMG-001: Bildvertrag ist fest, tatsächlicher Android-Uploadpfad muss dagegen geprüft werden.
4. Frühere gemischte Tracker sind nur noch Archiv; ab jetzt getrennte Android-/Server-Statusdateien plus Masterstatus.

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


## IMG-PORTAL-001 – Server enthält 4 Fotos, Portal zeigt nur 1
- Neuer Praxisbefund am aktiven BTB 26.09: Auf dem Server/NAS liegen 4 Bilder dieses heutigen BTB, im Portal wird nur 1 Bild angezeigt.
- Noch offen ist, ob alle 4 Dateien korrekt dem BTB als `BTB_PHOTO` zugeordnet sind, ob Metadaten/parent_entity_id abweichen oder ob das Portal filtert.
- Vor Änderungen zuerst DB-/API-Zuordnung der vier Dateien und Portal-Renderpfad prüfen.


## IMG-PORTAL-001 präzisiert – Daten korrekt, Live-Refresh fehlt
- Nach manuellem Browser-Reload (F5) erscheinen die zuvor fehlenden BTB-Bilder vollständig im Portal.
- Damit ist die Bildzuordnung/Serverablage grundsätzlich vorhanden; der aktuelle Praxisfehler ist **fehlende Live-Aktualisierung der offenen Portalansicht**.
- Benutzeranforderung: Neue BTBs/Bilder/Fachdaten sollen in einer offenen Portalansicht automatisch sichtbar werden; F5 darf für normalen Betrieb nicht nötig sein.
- Bestehende Architekturregel gilt: offene Portalansichten dürfen live aktualisieren, aber nicht über 2-Sekunden-DB-Polling pro Browser. Vorhandener Change-Feed/SSE-/Long-Poll-Pfad ist zu verwenden.


## PORTAL-LIVE-001 Source-Fix
- Source-Fix Commit: `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- `v1_btb.html` und `v1_worksite.html` abonnieren jetzt den vorhandenen worksite-bezogenen SSE-Change-Feed `/api/v1/sync/events`.
- Relevante Änderungen werden entprellt und gezielt nachgeladen; auf der BTB-Seite insbesondere `BTB`, `BTB_ENTRY`, `BTB_PARTICIPANT` und `FILE`.
- Baustellenseite aktualisiert Teams/BTBs, Medien, Mängel, Abnahmen, Mitglieder und Baum abhängig vom Change-Typ.
- Kein 2-Sekunden-Polling; vorhandener gemeinsamer Change-Hub wird verwendet.
- Source-Assertions für EventSource/SSE-Pfad und gezielte Reload-Funktionen: **PASS**.
- Status bleibt: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.


## BTB-SYNC-002 – Pixel→Portal fehlt / Portal überschreibt Pixel-Eingabe
- Praxisbefund am aktiven BTB 26.09:
  - Eingabe/Änderung auf dem Pixel erscheint **nicht** im Portal.
  - Änderung im Portal kommt anschließend auf dem Pixel an.
  - Beim Eintreffen des Portalstands wird die zuvor auf dem Pixel eingegebene Änderung überschrieben/gelöscht.
- Das ist ein P0-Datenverlust-/Konfliktfehler. Bis zur Ursache keine weiteren produktiven BTB-Eingaben für diesen Testdatensatz erzwingen.
- Vor Änderung prüfen:
  1. ob die Pixel-Eingabe lokal bereits gespeichert ist;
  2. ob dafür eine Android-Outbox/BTB-Pending-Mutation entsteht;
  3. ob diese Mutation zum Server gesendet oder durch einen vorherigen Server-Snapshot verworfen wird;
  4. Server-BTB-Version/Entry-Versionen und Change-Feed vor/nach Pixel- und Portaländerung;
  5. Merge-/Conflict-Regel für dieselbe BTB-Sektion bzw. denselben Slot.


## BTB-SYNC-002 Source-Ursache gefunden
- **Source-Ursache gefunden im aktuellen Android-HEAD `ef7077923e2917ac615b9f3b5143ac2638fb92ac`:**
  - `BautagebuchSpeicher.speichern()` speichert die Pixel-Änderung lokal und markiert den BTB nur im `SelectiveSyncStore` als dirty.
  - `SelectiveSyncWorker` lädt bei bereits bekanntem Server-BTB **zuerst** `loadBtbAccess(...)`, speichert den Server-Snapshot und ruft **vor** `exportBtbChange(...)` bereits `bridge.applyServerStateToLocal(...)` auf.
  - Zu diesem Zeitpunkt existiert noch keine `collabPending`-Mutation für die neue Pixel-Änderung; der Guard in `applyServerStateToLocal()` kann sie daher nicht schützen.
  - Ergebnis: der ältere/andere Serverstand überschreibt die lokal gespeicherte Pixel-Änderung, erst danach wird exportiert. Damit wird der überschriebenen Serverstand statt der Pixel-Änderung hochgeladen.
- Das erklärt exakt den Praxisbefund: Pixel→Portal fehlt; Portal→Pixel kommt an und entfernt die Pixel-Eingabe.
- Server/API/DB sind für diesen Fehler nicht zu ändern.


## BTB-SYNC-002 – Integrationsstand 1.3.23 / 200073
Status: **PIXEL↔PORTAL BASIS-E2E BESTÄTIGT**

- Android-Branch steht bei 1.3.23 / 200073 mit Outbox-Datenverlustfix.
- Benutzer bestätigt den normalen Sync in beide Richtungen: Pixel → Portal und Portal → Pixel.
- Parallel-Autosave-/Conflict-Test bleibt separat offen.
- Nächster SERVER-Schritt: PORTAL-LIVE-001 deployen und ohne F5 testen.


## PORTAL-LIVE-001 – E2E bestätigt
Status: **BESTÄTIGT**

- Portal aktualisiert ohne F5.
- Praxistest: ca. 1–2 Sekunden bis zur sichtbaren Änderung.
- Erstellen und Löschen werden automatisch übernommen.
- Funktioniert in beide Richtungen.
- Live-/SSE-Pfad bleibt geschützt.

Separat offen:
- `PORTAL-BTB-UI-001`: BTB im Portal fachlich/optisch nacharbeiten.


## BTB-EDIT-001 – Integrationsstand
Status: **SERVER QUELLSTAND FERTIG / ANDROID ENTRY-UMSETZUNG LÄUFT / GEMEINSAMER E2E OFFEN**

SERVER:
- Entry-Abschnitt 4–8 versioniert änderbar;
- stabile `worksiteNodeKey`-Validierung;
- versionierte BTB-Foto-Link-Metadaten + Foto-PATCH;
- Portal-Unterbaustellen-/Firmen-/Punkt-Editor umgesetzt;
- Migration 016 neu;
- Source-Assertions + Portal-JS-Syntax PASS.

ANDROID live geprüft:
- HEAD `7c7cf7b502ec1f701f167c13780b10a014bba6d9`;
- `SectionItem` erweitert um `clientEntryKey`, `firmaId/firmaName`, `worksiteNodeKey`;
- stabile Node-Key-Auflösung und Abschnittsidentität werden source-seitig umgesetzt;
- Foto-Umhängepfad ist noch nicht vollständig auf den neuen Server-Foto-PATCH gehoben.

Gemeinsam offen:
1. Android Foto-Zuordnung fertigstellen;
2. Server Migration 016 + neuer Quellstand deployen;
3. Android passenden Build installieren;
4. Eintrag 4→5, Firmawechsel, BÜ-Wechsel sowie Foto-Umhängen in beide Richtungen E2E prüfen;
5. GPS/Aufnahmezeit müssen unverändert bleiben;
6. Live-Refresh weiterhin ohne F5 bestätigen.


## INT-001 – Android/Server Branch-Integration
Status: **KONFLIKT FESTGESTELLT / NOCH NICHT AUFGELÖST**

- Live-Prüfung nach paralleler BTB-EDIT-001-Entwicklung:
  - Server-Branch und Android-Branch sind divergiert.
  - Android Draft-PR #7 meldet aktuell `mergeable=false`.
- Android arbeitet gleichzeitig aktiv weiter; deshalb kein Merge/Rebase mitten in diesem Arbeitsblock.
- Android-Stand bis `7c7cf7b502ec1f701f167c13780b10a014bba6d9`:
  - Entry-/Node-Identität strukturiert;
  - lokale Foto-Korrektur enthält jetzt `firmaId`, `worksiteNodeKey`, `bildtyp`;
  - Server-Foto-PATCH-Nutzung/Sync ist noch separat zu bestätigen.
- Nach Abschluss des Android-Blocks ist SERVER als Integrator zuständig:
  1. aktuellen Serverstand in Android-Branch integrieren;
  2. Konflikte in gemeinsamen `projektsteuerung/**`-Dateien bewusst zusammenführen;
  3. `server/**` und `app/**` jeweils aus ihrem zuständigen Branch erhalten;
  4. erst danach gemeinsamen Deploy/Build/E2E starten.


## INT-001 – Android-Arbeitsblock beendet
**BENUTZER BESTÄTIGT:** Android-Arbeitsblock ist fertig; SERVER darf jetzt integrieren.

Aktueller Android-HEAD bei Übergabe: `a1b4667b110a993796e8242d4a21be3748c06d68`.
Wichtig: Android 1.3.25/200075 ist weiterhin **BUILD OFFEN**, weil der GitHub-CI-Job keinen Runner/keine Steps bekam. Das ist getrennt von der abgeschlossenen Quellarbeit zu behandeln.


## AND-BUILD-200075 – lokaler Buildfehler beim Integrationsabschluss
Status: **REPRODUZIERT / ANDROID-ZUSTÄNDIG**

Live-Android-HEAD: `57c960fc2f0b133f8c577ba5bcaf2176375d287d`.

- Android-Studio-Build 1.3.25 / 200075 erreicht `:app:generateBtb200043Sources` und scheitert am veralteten Android-200047-Ortsmarker-Anker.
- Ursache ist der Android-Buildgenerator in `app/build.gradle.kts`, nicht Server/API/DB.
- Kein erfolgreicher APK-Build, keine Installation, kein BTB-EDIT-Geräte-E2E.
- Benutzerbestätigung „Android ist fertig“ wird als Ende des Android-Arbeitsblocks verstanden, nicht als Buildfreigabe.


## AND-BUILD-200075 – Generatorfix 606aa3ad
Status: **SOURCE-FIX VORHANDEN / BUILD-RETEST OFFEN**

Android-Commit `606aa3ad1f8ea438372484b056a0fc1264ae2aac` entfernt den veralteten Android-200047 Build-time-Source-Patch und verlagert die Ortsmarker-Semantik in den kanonischen `BautagebuchFragment.kt`-Pfad.

Einordnung:
- konkrete Buildursache source-seitig korrigiert;
- noch kein erfolgreicher 1.3.25/200075 Build nach diesem Fix belegt;
- Installation/E2E weiterhin offen.


## INT-001 – Branch-Integration abgeschlossen
Status: **QUELLSTAND INTEGRIERT / BUILD-RETEST 200075 OFFEN**

- SERVER-Parent: `c7358cd223cbd3575146fc98a3477fc6993501d2`.
- ANDROID-Parent: `4de3350fa4bc3777a6f2f51514e85a145ca4a9c6`.
- `server/**` bleibt unverändert vom SERVER-Parent.
- `app/**` bleibt unverändert vom ANDROID-Parent.
- Generatorfix `606aa3ad...` und Android-Statuscheckpoint `4de3350f...` sind enthalten.
- Nach dem Generatorfix ist noch kein erfolgreicher 1.3.25/200075 Build belegt.
- Android-Clientnutzung des versionierten Server-Foto-PATCH (`link_version`) bleibt offen.


## INT-001 – gemeinsamer HEAD nach Merge
Status: **INTEGRATION ABGESCHLOSSEN**

- gemeinsamer Server-HEAD nach Android-Integration: `ee8358e0cb403347a17d389dcad562ac6858aff7`;
- Android Draft-PR #7 wurde von GitHub als **merged/closed** erkannt;
- `server/**` entspricht exakt dem vorherigen Server-Parent;
- `app/**` entspricht exakt dem finalen Android-Parent `4de3350fa4bc3777a6f2f51514e85a145ca4a9c6`;
- 10 echte gemeinsame Dokument-/Statuskonflikte wurden bewusst zusammengeführt;
- keine verbotenen alten Top-Level-Ordner wieder eingeführt.

CI auf dem Merge-HEAD:
- TB-Server 1.0 Run `36253773890`: failure, Job `foundation`, **0 Steps**;
- Android CI Run `36253773908`: failure, Job `build`, **0 Steps**;
- daher gemäß Projektregel **kein Testnachweis und kein Sourcefehler-Nachweis**.


## BTB-EDIT-001 – Migration 016 auf Synology angewendet
Status: **MIGRATION 016 BESTÄTIGT / RUNTIME-RESTART + SMOKE OFFEN**

Praxisnachweis 26.09.2026:
- im laufenden API-Container `python -m tbserver.migrations up` ausgeführt;
- sichtbare Ausgabe: `APPLIED 016_btb_photo_link_metadata.sql`;
- damit ist Migration 016 praktisch auf der Synology-Datenbank angewendet.

Noch offen:
- API und Worker mit dem hochgeladenen Quellstand neu starten;
- Runtime/Health prüfen;
- Portal-BTB Smoke-Test durchführen;
- Geräte-E2E bleibt separat offen.

## ADMIN-FINISH-001 – Server-Admin Quellstand 28.09.2026
Status: **QUELLSTAND FERTIG / DEPLOY + E2E OFFEN**

SERVER HEAD enthält jetzt:
- modernisierte Admin-Übersicht mit automatischer 5-Sekunden-Aktualisierung;
- reale NAS-/Host-/HTTP-Metriken und Firmen-Speicheraufschlüsselung;
- Firmenverwaltung mit Stammdaten, Benutzern, Geräten, Baustellen, Speicher und Logo/PDF;
- echte serverseitige PDF-Vorschau auf gemeinsamem Android/Server-Brandingvertrag;
- gruppierte Benutzerverwaltung;
- Bahnhof-/Betriebsstellen-Autofill auf vorhandener Android-Datenbasis;
- geschützten Trassierungsdaten-Adminimport;
- deutsch lokalisierte Hauptanzeigen und MB/GB-Speicherdarstellung.

ADMIN-STORAGE-001:
- Legacy-Doppelzählung gleicher physischer Vermessungsoriginale über `tb.files` + `tb.survey_originals` source-seitig behoben;
- Deduplizierung über gemeinsamen `storage_key`;
- keine produktiven Originaldaten verändert.

Noch offen:
- Synology-Deployment dieses neuen Admin-Blocks;
- API-Runtime/Browser-Smoke;
- tatsächliche Speicherwerte gegen NAS/DB prüfen;
- erst danach E2E-Status anheben.
