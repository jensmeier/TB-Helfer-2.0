# TB-HELFER 2.0 – MASTERSTATUS

Stand: 2026-09-26

> Live-HEADs immer zuerst direkt in GitHub prüfen. Die SHAs unten sind der Fachstand vor diesem reinen Projektsteuerungs-Umbau.

- ANDROID: `feature/tb-android-2.0`, Fach-HEAD `d272f8a7586cce3ea1eda29100473f7af5d540bb`, Draft-PR #7
- SERVER: `feature/tb-server-1.0`, Fach-HEAD `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`, Draft-PR #6
- Android nächster Build: **1.3.20 / 200070**
- Server: **1.0.0-dev**
- Server-Quellmigrationen: **001–017** (017 Quellstand; Synology-Runtime offen)

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

## ADMIN-STRUCTURE-004 – Firmenzentrierter Admin
Status: **QUELLSTAND FERTIG / DEPLOY OFFEN**
- Firma ist künftig der zentrale Organisationscontainer im Admin;
- Benutzer, Geräte und Baustellen werden nicht mehr als gleichrangige globale Hauptnavigation geführt;
- Verwaltung liegt gesammelt in `Firma verwalten` mit linker Navigation;
- Firmenliste und Dashboard-Schnellzugriff wurden an diese Struktur angepasst;
- Geräteverwaltung ist ausdrücklich Bestandteil der Firma;
- Benutzer und Baustellen können dort direkt angelegt werden;
- Speicher sowie Logo/PDF bleiben im selben Firmenworkspace;
- sichtbarer Auto-Refresh-Hinweis entfernt, Hintergrundaktualisierung bleibt.

Noch offen:
- Synology-Upload + API-Restart;
- Runtime-/Browser-Retest des neuen Firmenworkspaces;
- separater MIB/Bahnhof-Autofill-Fix;
- globaler Referenzdaten-/Streckendaten-Umbau danach.

## ADMIN-COMPANY-005 – Firmenworkspace Nacharbeit
Status: **SOURCE FERTIG / DEPLOY OFFEN**
- Aktivierungskarte bleibt jetzt im firmenzentrierten Adminfluss;
- Gültigkeit wird mit Datum und verbleibender Zeit angezeigt;
- alte globale Organisationslisten werden nicht mehr als eigene Verwaltung geöffnet;
- Geräteversionen werden bis zum Android-Vertragsupdate nicht mehr als sicher aktuelle Installation dargestellt;
- veröffentlichter Android-Release und vom Gerät zuletzt gemeldeter Stand werden getrennt angezeigt;
- SERVER ist für laufende Versionsmeldungen vorbereitet; Android-Clientanpassung DEVICE-INFO-001 bleibt separat offen.


## ADMIN-DASH-006 – Dashboard/Bahnhof Nacharbeit
Status: **SOURCE FERTIG / SYNLOGY-RECREATE OFFEN**
- Übersicht weiter modernisiert und auf großen Monitoren kompakter angeordnet;
- Warnungsquittierung direkt im Dashboard;
- Bahnhof-Referenzdaten werden im API-Container explizit read-only gemountet;
- Systemstatus erkennt fehlende Bahnhofsdaten;
- MIH/Ingolstadt-Hbf-Vertrag gegen die identischen Android-/Server-Referenzdateien geprüft;
- MIB wird als ungültiger Code nicht gespeichert, ähnliche offizielle Codes werden vorgeschlagen;
- keine Android-Codeänderung.

Nächster Schritt:
- geänderte Serverdateien + Compose auf Synology übernehmen;
- API-Service/Projekt recreaten;
- Dashboard und Bahnhofssuche praktisch testen.


## ADMIN-PDF-007 – PDF-Logo-Layout Nacharbeit
Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Bahnhofsdaten-Mount/MIH-Suche praktisch bestätigt.
- PDF-Logo-Vorschaufehler source-seitig korrigiert: Live-/Draft-Vorschau mit aktuellen Reglerwerten, größerer Android-naher Firmenkopf, Renderer v4 für Vorschau und finalen BTB-PDF.
- Synology Restore-Test-Compose-Warnung zu POSTGRES_USER/POSTGRES_DB ebenfalls korrigiert.
- keine DB-/Android-Änderung.

## ADMIN-DASH-008 – Festplatten-Temperaturen in °C
Status: **SOURCE FERTIG / SYNOLOGY-RECREATE + RUNTIME-RETEST OFFEN**
- Admin-Übersicht zeigt künftig echte Synology-Laufwerkstemperaturen getrennt je erkanntem physischem Laufwerk und primär in °C.
- Server liest die read-only eingebundene Synology-Laufwerksstatusquelle `/run/synostorage/disks/*/temperature` über `/host/synostorage/disks`.
- Fehlende/nicht lesbare Hostdaten führen sauber zu `Nicht verfügbar`; keine Demo-/Fallbacktemperatur wird erfunden.
- Bestehende generische Systemtemperatur bleibt getrennt erhalten.
- Keine DB-Migration und keine Android-Änderung.
- Wegen neuem Host-Volume-Mount ist auf Synology ein **Recreate** des API-Service/Projekts erforderlich; danach Werte gegen DSM Laufwerk 1/2 vergleichen.

### ADMIN-DASH-008 – CI-Nachtrag
- GitHub Actions auf Code-HEAD `051c7d87b3b5cc849bb4faff54259d9ffee639ca`:
  - TB-Server Runs `36488381133` und `36488374650`: `failure`, Job `foundation`, `runner_name = ""`, `steps = []`;
  - Android Run `36488381240`: `failure`, Job `build`, `runner_name = ""`, `steps = []`.
- Bewertung gemäß Projektregel: **keine Runner-Steps ausgeführt → kein Build-/Testnachweis und kein Sourcefehler-Nachweis**.
- ADMIN-DASH-008 bleibt **SOURCE FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**.

## PDF-RUNTIME-009 – Android-naher Server-Abschlussrenderer
Status: **SOURCE-FIX FERTIG / SYNLOGY-UPLOAD + VISUAL-RETEST OFFEN**
- Ursache des PDF-Layoutsprungs bestätigt: 22.09 Android/Skia korrekt, 25./26.09 generischer Server/ReportLab-Renderer abweichend.
- Neuer Server-Renderer `tb-server-btb-pdf-5` bildet die etablierte Android-BTB-Struktur nach: A4/Margen, Firmenkopf, Wetter, Personal/Maschinen zweispaltig, Punkte 4–8 gerahmt, Unterbaustellen/KM, Signatur und Fotoanhang.
- Bestehender Finalize-/Original-/Rework-Vertrag bleibt erhalten; geänderte/entfernte Einträge und Fotos bleiben im Nachbearbeitungsfall markiert.
- Server-PDF-Kontext enthält jetzt zusätzlich verantwortlichen TB mit E-Mail/Mobil sowie den aktuellen Baustellenknotenbaum für die Android-nahe Abschnittsdarstellung.
- Keine DB-Migration, keine API-Vertragsänderung und keine Android-`app/**`-Änderung.
- Lokaler synthetischer Visual-Smoke der Rendererlogik erzeugte ein gültiges A4-PDF im 22.09-/Android-Zielbild; produktiver Synology-Retest ist noch offen.
- GitHub Actions auf Code-HEAD `d2c3ca9e...` liefen erneut ohne Runner-Steps (`runner_name=""`, `steps=[]`) und sind deshalb kein Testnachweis.

## ADMIN-PDF-010 – Admin-Vorschau auf BTB-Zielbild
Status: **SOURCE-FIX FERTIG / SYNLOGY-RETEST OFFEN**
- Benutzer bestätigt Bild 2 als verbindliches BTB-PDF-Zielbild und Bild 1 als falsche/irreführende Admin-Vorschau.
- Admin-PDF-Vorschau verwendet jetzt denselben Android-nahen Renderer wie der serverseitige BTB-Abschluss statt des alten generischen Vorschau-Layouts.
- Beispielinhalt bleibt synthetisch, aber Seitenstruktur, Firmenkopf/Logo, Wetter, Personal/Maschinen und Punkte 4–8 folgen nun dem finalen Renderer.
- Personal- und Maschinenbox dürfen wieder unterschiedlich hoch sein wie im bestätigten Zielbild.
- keine DB-/Compose-/Android-Änderung.
- Synology-Retest nach Dateiübernahme + API-Neustart offen.

## ADMIN-PDF-011 – Logo 400 pt / Y-Versatz
Status: **SERVER SOURCE-FIX FERTIG / SYNLOGY-RETEST OFFEN / ANDROID-ANPASSUNG SEPARAT**
- Admin-/Serververtrag akzeptiert `logo_width_pt` jetzt 40..400 statt 40..250.
- Y-Versatzfehler im Android-nahen Serverrenderer behoben: bisher kollabierten die oberen/unteren Grenzen bei hohen/quadratischen Logos praktisch auf denselben Y-Wert.
- Neuer Renderer verwendet einen echten Y-Anker plus `offset_y_pt`; nur A4-Seitengrenzen begrenzen.
- Große Logos dürfen tatsächlich breiter als die frühere rechte 250-pt-Zone werden; Dokumentinhalt beginnt bei Bedarf unter dem Firmenkopf, damit keine vertikale Überlagerung entsteht.
- Admin-Slider/Nummer/Preview/Save auf 400 erweitert.
- Android bleibt unverändert und begrenzt aktuell weiterhin auf 250; Vertragsbedarf ist in `05_ANDROID_SERVER_VERTRAG.md` dokumentiert.
- keine DB-Migration, kein Compose.

## ADMIN-PDF-012 – Tabellenposition unabhängig vom Logo
Status: **SERVER SOURCE-FIX FERTIG / SYNLOGY-RETEST OFFEN**
- Logo-Breite sowie X/Y dürfen das BTB-Fachlayout nicht nach unten verschieben.
- Serverrenderer entkoppelt Firmenlogo und Fachlayout wieder vollständig.
- Wetter, Personal/Maschinen und Punkte 4–8 starten unabhängig von Logo-Größe/-Position an ihren festen Zielpositionen.
- Extrem große Logos dürfen optisch in den Inhaltsbereich hineinragen; der Renderer verschiebt dafür aber keine Tabellen.
- keine Android-/DB-/Compose-Änderung.

## ADMIN-PDF-013 – BAUTAGEBUCH-Titel über Logo
Status: **SERVER SOURCE-FIX FERTIG / SYNLOGY-RETEST OFFEN**
- Nutzer bestätigt Layout nach 400-pt-/Y-/Tabellenfix als fast fertig; Restfehler: `BAUTAGEBUCH` wird bei sehr großem/verschobenem Logo abgeschnitten.
- Ursache: Titel wurde vor dem Logo gezeichnet; der weiße Logo-Hintergrund konnte den rechten Titelteil überdecken.
- Fix: Firmenlogo/Kontaktblock zuerst, danach Titel als oberste Ebene mit eigenem weißen Hintergrund rendern.
- Tabellen/Fachlayout bleiben unverändert/fest.
- keine Android-/DB-/Compose-Änderung.

## PDF-ADMIN-Abnahme 29.09.2026
Status: **BESTÄTIGT / SERVER-ADMIN-PDF ABGENOMMEN**
- Benutzer bestätigt nach dem letzten Synology-Runtime-Test ausdrücklich: **`passt und abgenommen`**.
- Damit sind für den SERVER-Admin-PDF-Pfad praktisch bestätigt:
  - Android-nahes Admin-PDF-Zielbild;
  - Logo-Breite bis 400 pt;
  - wirksamer X-/Y-Versatz;
  - feste Wetter-/Tabellenposition unabhängig von Logo-Größe/-Position;
  - vollständig sichtbarer `BAUTAGEBUCH`-Titel auch bei großem Logo.
- ADMIN-PDF-007, ADMIN-PDF-010, ADMIN-PDF-012 und ADMIN-PDF-013 sind SERVER-seitig **BESTÄTIGT / ERLEDIGT**.
- ADMIN-PDF-011 ist SERVER-seitig **BESTÄTIGT**; offen bleibt ausschließlich die bereits dokumentierte Android-Anpassung 250→400 pt.
- PDF-RUNTIME-009 bleibt separat offen, solange kein neuer echter serverseitiger BTB-Finalize-Abschluss im vollständigen Praxisablauf bestätigt wurde.

## REF-DATA-001 – Globale Referenzdaten Phase 1
Status: **SOURCE FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**
- Globaler Survey-Scope ist source-seitig umgesetzt ohne DB-Migration:
  - `company_id IS NULL` = `GLOBAL_REFERENCE`;
  - `company_id IS NOT NULL` = `COMPANY_WORKSITE_DATA`.
- Admin-Trassierungsimport kann jetzt ausdrücklich `GLOBAL_REFERENCE` oder Firma/Baustelle wählen.
- Globale Originale werden serverweit nach unveränderter Dateiidentität dedupliziert und bleiben immutable.
- Neue read-only Geräte/Portal-API:
  - `GET /api/v1/reference/survey-originals` = Manifest;
  - `GET /api/v1/reference/survey-originals/{id}/parsed` = fachlich geparster Einzelbestand;
  - bestehender `/api/v1/survey-originals/{id}/content` = Originalbytes.
- Manifest liefert `sha256`, Parser-Version, `parsed_at` und `cache_revision` für späteres Android-Caching.
- Bestehende `survey-sections` akzeptieren globale Originalquellen bereits, weil nur fremde nicht-null `company_id` abgewiesen werden.
- Capabilities ergänzt: `global_survey_reference_v1`.
- Keine Android-Codeänderung und keine DB-Migration.

## REF-DATA-001/002 – Datei-zuerst-Analyse + Grafik Phase A
Status: **SOURCE FERTIG / NICHT DEPLOYT / SYNLOGY-RETEST OFFEN**
- Admin-Import fragt vor Dateianalyse keine Strecke/Gleis/Scope mehr ab.
- Upload wird zunächst nur in einer zeitlich begrenzten Analyse-Session (6 h) abgelegt; noch kein DB-Import.
- TRA/GRA/KF/KV/GVP/KOR/CSV sowie ZIP-Inhalte werden dateibezogen analysiert.
- Erkannte Strecke/Gleis/Achse, Parserformat, Punktzahl, KM-/Stationsbereiche und Warnungen werden vor Import angezeigt.
- Grafische Lagevorschau zeigt TRA-Geometrie und XY-Punkte gemeinsam; Zoom/Pan und Elementdetails sind vorgesehen.
- KM-/Stationsbereiche werden zusätzlich als Bereichsübersicht dargestellt.
- Erst nach Analyse bestätigt der Benutzer je Datei `GLOBAL_REFERENCE` oder Firma/Baustelle sowie ggf. fehlende Angaben.
- Bestätigungsimport arbeitet mit der zuvor analysierten Originaldatei; Originale bleiben immutable.
- Capability: `survey_import_preview_v1`.
- Keine DB-Migration, kein Compose und keine Android-Datei.

## REF-DATA-002 – Android-nahe Servergrafik Korrektur
Status: **SOURCE KORRIGIERT / SYNLOGY-RETEST OFFEN**
- Erste Servergrafik wurde im Runtime-Test vom Benutzer zu Recht verworfen.
- Konkreter SVG-Fehler behoben: Polylinien können nicht mehr durch Farbklassen gefüllt werden (`fill:none!important`).
- Serverpreview folgt jetzt der vorhandenen Android-Semantik statt beliebiger Dateifarben:
  - Strecke/Streckengleis;
  - Bahnhofsgleis;
  - Weichenverbindung;
  - Festpunkte/KF;
  - Absteck-/Weichenpunkte.
- TRA + passende GRA werden grün dargestellt; TRA ohne passende GRA rot; WVB blau/gestrichelt; Vermessungspunkte klein separat.
- Achsen erhalten fachliche Labels wie `Gl 21 · Str 5528`, `Str 5524-1 · Quelle L`, `WVB 10-11`.
- Neue Anzeigeauswahl blendet Achsen/Punktgruppen gezielt ein/aus; Raster, Zoom/Pan und Klickdetails bleiben.
- Importkarten werden nach fachlicher Achse/Objektgruppe zusammengefasst: TRA+GRA derselben Achse erscheinen als eine Gruppe.
- ZIP-Zusammenfassung zeigt Strecken, Dateitypen, Bahnhofsgleise und WVB vor dem Import.
- Pasing-Testbestand: 41 Dateien, TRA 21, GRA 17, KF/KF.TXT 1/1, PDF 1, Strecken 5524/5528/5529, 10 Bahnhofsgleise, 3 WVB.
- keine DB-Migration / kein Compose / keine Android-Änderung.

## REF-DATA-002 – Großansicht/Punktlabels
Status: **SOURCE KORRIGIERT / SYNLOGY-RETEST OFFEN**
- Zweiter Pasing-Runtime-Screenshot: Linienstruktur ist deutlich besser, aber Achsenlabels überlagern sich stark; gelbe Fest-/Vermessungspunkte zeigen im Server keine sichtbaren Punktdaten wie Android.
- Servergrafik erweitert um echte Großansicht sowie zoomabhängige Achsen-/Punktlabels nach Android-Prinzip.
- Punktlabels: ab Zoomstufe sichtbar, begrenzte Labelanzahl je Zoom; bei höherem Zoom zusätzlich KM.
- Punkt-Klickdetail enthält soweit vorhanden Bezeichnung, Art, KM, Strecke, Gleis, Höhe, Punkt-ID, Koordinatensystem und Quelldatei.
- Preview-Schema liefert dafür zusätzlich `height_m`, `point_id`, `point_code`, `coordinate_system`, `source_name`.
- Achsenlabels werden verkleinert, zoomabhängig kurz/lang dargestellt und kollisionsarm begrenzt.
- keine DB-/Compose-/Android-Änderung.

## REF-DATA-002 – Android-ähnliches Tiefzoom/LOD
Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Admin-Grafik trennt nun Weltgeometrie von Screen-Space-Punktmarkern/Labels.
- Tiefzoom bis ca. 30x statt bisher ca. 12,5x.
- Punktmarker behalten bildschirmbezogene Größe und werden bei starkem Zoom zusätzlich kleiner statt mitzuwachsen.
- Punkt-Klickziel bleibt separat größer, obwohl sichtbarer Marker klein wird.
- Achsen- und Punktlabels werden bei tiefem Zoom kleiner und stärker begrenzt; Geometrie hat Vorrang vor Text.
- KM/Station rechts wurde fachlich nachgeschärft:
  - nur belastbare Bereiche werden als Balken dargestellt;
  - GRA/KM-Quelle wird für KM bevorzugt;
  - 0+000–0+000 / Nullspanne gilt nicht als belastbarer Bereich;
  - Station wird nur getrennt als technischer Ersatz angezeigt;
  - fehlende KM-Bereiche werden kompakt gezählt statt mit wertlosen Nullbalken gefüllt.
- keine DB-/Compose-/Android-Änderung.

## REF-DATA-002 – Screen-Space-Punktinteraktion
Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Vermessungspunkte und Beschriftungen werden nicht mehr als SVG-Weltobjekte skaliert, sondern in einem echten Screen-Space-Overlay gerendert.
- Sichtbare Marker bleiben beim Tiefzoom in stabiler Bildschirmgröße; Geometrie kann unabhängig bis ca. 30x gezoomt werden.
- Hover zeigt eine kurze Punktinfo.
- Klick zeigt strukturierte Punktdetails im Bereich `Auswahl`; in Großansicht zusätzlich als schwebende Detailkarte.
- Klick-/Trefferlogik nutzt eine ca. 24-px-Trefferzone nach Android-Prinzip.
- Bei mehreren Punkten im Trefferbereich wird eine Auswahlliste angeboten.
- Permanente Punktlabels werden bei tiefem Zoom stark reduziert; oberhalb hoher Zoomstufe stehen Geometrie + Hover/Klick vor Text.
- keine DB-/Compose-/Android-Änderung.

## REF-DATA-002 – GVP-Kalibrierung freie Strecken/Gleise
Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Server-Preview übernimmt jetzt die Android-Semantik: GVP/Festpunkte sind eine eigenständige automatische KM-Quelle.
- Grün bedeutet nicht mehr nur passende GRA, sondern belastbare automatische KM aus **GRA oder GVP**.
- GVP-Kalibrierung je TRA-Achse:
  - gleiche Strecke;
  - mindestens 4 GVP mit KM;
  - mindestens 4 Achssamplepunkte;
  - GVP maximal 120 m von der Achse;
  - Median von `KM - Station`, Ausreißerfilter ±80 m;
  - mindestens 4 robuste Treffer.
- Erfolgreiche GVP-Kalibrierung → GRÜN; widersprüchliche GVP → GRAU; keine belastbare KM → ROT.
- Explizite KM-GRA wird wie Android anhand `KM####`, `KMLINIE`, `KM_LINIE` erkannt; normale Höhen-GRA ist keine automatische KM-Quelle.
- Tiefzoom auf Android-Maximum ca. 160x erweitert.
- Punktdetail zeigt `KM aus Datei` (Dezimal-km) plus `Bahn-KM`; doppelte KF/KF.TXT-Punkte werden in der Preview konservativ zusammengeführt.
- keine DB-/Compose-/Android-Änderung.

## REF-DATA-002 – Achsen-Hover/Tiefzoom/KOR-Metadaten
Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Achsen haben jetzt eine unsichtbare 16-px-Trefferlinie; sichtbare Linie bleibt dünn.
- Hover hebt die Achse hervor und zeigt Kurzinfo mit Objekt, KM-Status und Quelle; Klick öffnet die bereits strukturierte Achsendetailkarte.
- Tiefzoom von ca. 160x auf bis zu ca. 2000x erweitert; Screen-Space-Punkte bleiben unabhängig davon stabil.
- KOR-Parser auf Version `tb-survey-parser-3` angehoben.
- Offensichtliche KOR-Metadaten/Header/Trennzeilen werden nicht mehr als fehlerhafte Datenzeile gemeldet.
- Potenziell echte, aber unvollständige KOR-Datenzeilen bleiben als Warnung sichtbar und enthalten jetzt einen Textausschnitt zur Diagnose.
- Original `2026031_Gessertshausen_5302_Koordinaten.kor` war in Projekt/Library nicht als Rohdatei verfügbar; deshalb keine formatspezifische Sonderregel auf Verdacht.
- keine DB-/Compose-/Android-Änderung.

## REF-DATA-002 – Achsen/KOR/Tiefzoom praktisch bestätigt
Status: **BESTÄTIGT**
- Benutzer bestätigt den zuletzt deployten Achsen-/KOR-/Tiefzoom-Stand mit `okay,passt`.
- Damit ist der Teilblock `Achsen-Hover/Klick + ca. 2000x Tiefzoom + KOR-Parser v3` praktisch auf der Synology bestätigt.
- Schutzregel greift: diesen bestätigten Teilblock nicht ohne reproduzierbaren Fehler, neue Fachanforderung oder ausdrücklichen Auftrag verändern.
- Diese Bestätigung schließt nicht automatisch alle übrigen REF-DATA-002-Import-/Android-E2E-Punkte.

## REF-DATA-003 – Reihenfolge festgelegt 29.09.2026
Status: **SERVER AKTIV**
- Umsetzung erfolgt verbindlich in drei Stufen: **SERVER → Portal → Android**.
- Serververtrag und Datenmodell werden zuerst vollständig und versionierbar abgeschlossen.
- Portal und Android werden erst danach gegen diesen stabilen Vertrag gebaut.


## REF-DATA-003 – Server-Katalog Quellblock 29.09.2026
Status: **QUELLSTAND IMPLEMENTIERT / NICHT DEPLOYT / E2E OFFEN**
- Code-HEAD des Blocks: `a1ef0d991e2ccc06578feced7e9b5a89bdf5f611`.
- Migration 017 + versionierter Survey-Katalog für Assets/Achsen/Segmente/Konflikte/Historie sind source-seitig vorhanden.
- Search/Availability/Geometry/Changes, Nutzerbeiträge, Achskorrekturen, Auto-Merge/REVIEW_REQUIRED, Konfliktmail und Reindex sind serverseitig implementiert.
- Originaldateien bleiben unverändert; private Firmen-Rohdatei kann nur abgeleitete Geometrie ausdrücklich GLOBAL teilen.
- KM-Semantik bleibt GRÜN automatisch / ORANGE manuell / GRAU unplausibel / ROT fehlt.
- Aktive Baustellenmitglieder dürfen Survey-Beiträge senden; Firmen-/Baustellenisolation bleibt erhalten.
- Portal und Android wurden nicht geändert.
- GitHub Runs `36618626903` + `36618619207`: `steps=[]` → **kein Testnachweis**.
- Nächster Schritt: Synology Migration 017 + API/Worker deployen → Reindex → Runtime/Postman-E2E. Erst danach Portal.

## REF-DATA-003 – SERVER-Sourceabschluss 29.09.2026
Status: **SOURCE FERTIG / NICHT DEPLOYT / SYNLOGY-E2E OFFEN**
- Maßgeblicher SERVER-HEAD: `782e72e18790570066b40da08a80471fa14d759e`.
- Persistenter Survey-Katalog mit Migration 017 ist source-seitig abgeschlossen: Assets, fachliche Achsen, Quellsegmente, Konflikte und versionierte Historie.
- Serververtrag umfasst Suche nach Strecke/Betriebsstelle, Availability, ausgedünnte Geometrie, KM-Qualität, versionierte Nutzerbeiträge, Achskorrekturen, Change-Feed mit `next_after`, Reindex und Konfliktentscheidung.
- Auto-Merge schützt Teilabdeckungen: alte/manuelle Restgeometrie bleibt aktiv; vollständiges Supersede nur bei nachgewiesener geometrischer Vollabdeckung.
- TRA mit unvollständiger Gleis-/Richtungskennung darf bei kompatibler Strecke/Betriebsstelle und engem X/Y-Kandidaten in den Geometrievergleich; Mehrdeutigkeit bleibt `REVIEW_REQUIRED`.
- Normale Nutzer erhalten keine fremden Bearbeiter-/Firmenidentitäten aus globalen Katalogantworten; Admin-Audit bleibt vollständig.
- Mobile Originaluploads erben bei Bedarf Strecke/Betriebsstelle aus der Baustelle; globale SHA-Duplikate werden erkannt, private Fremdfirmendaten nicht offengelegt.
- Firmen-Lifecycle/Purge berücksichtigt Migration-017-Abhängigkeiten; bestätigter Produktionsschutz für Vermessungsoriginale bleibt bestehen.
- Capability-Flags: `survey_reference_catalog_v1`, `survey_reference_contributions_v1`, `survey_reference_change_feed_v1`.
- Abschließender GitHub-Source-Audit gegen HEAD: alle 25 geprüften Vertrags-/Integrationsmarker **PASS**.
- GitHub Actions Runs `36621416472` und `36621409361`: Foundation-Job jeweils `steps=[]`; daher ausdrücklich **kein CI-Testnachweis**.
- Portal und Android wurden in diesem Serverblock nicht geändert.
- Nächster Pflichtschritt vor Portal: Synology deployen, Migration 017 anwenden, API+Worker neu starten/recreaten, Reindex und Runtime-/Postman-E2E durchführen.

## REF-DATA-003 – Synology Runtimebasis 30.09.2026
Status: **DEPLOYT / RUNTIMEBASIS BESTÄTIGT / E2E OFFEN**
Benutzernachweis per Admin-Systemstatus:
- API: `1.0.0-dev`, Umgebung `staging`;
- Migrationen: **17 / 17**, `latest 17 / 17`;
- Ausstehend: **keine**;
- Datenbank: **OK**;
- interne API: **OK**;
- Worker: **OK**;
- Cloudflare / öffentliche API: **OK**.
- Damit ist Migration 017 praktisch angewendet und API/Worker laufen auf der Synology.
- Noch **nicht** bestätigt: Reindex, Survey-Katalog-Endpunkte, Merge-/Reviewfälle, Konfliktmail, Change-Feed und Lifecycle-E2E.
- Nächster Schritt bleibt Runtime-/Postman-E2E des Survey-Katalogs; Portal erst danach.

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

## REF-DATA-003 – Staging-Reset + Bahnhofsauswahl source-seitig fertig 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT OFFEN**
- Sicherer Survey-Testdaten-Reset ergänzt:
  - nur development/dev/test/testing/staging;
  - Produktion hart gesperrt;
  - Preview-Endpunkt;
  - expliziter Bestätigungstext `VERMESSUNG TESTDATEN LOESCHEN`;
  - FK-sichere Bereinigung von Survey-Katalog, Parserkatalog, Survey-Sections und Originalen;
  - zugehörige Originaldateien erst nach DB-Commit entfernt;
  - andere Firmen-/Baustellen-/BTB-Daten bleiben unangetastet.
- Bestehender Admin-Trassierungsimport ergänzt:
  - Bahnhof/Betriebsstelle über vorhandene Referenzsuche/RL100;
  - stabiler `station_code + station_name`-Payload;
  - Default für gesamten Import + Override je Gruppe;
  - freie Strecke darf bewusst ohne Betriebsstelle bleiben;
  - Worksite-Bahnhof kann als Default übernommen werden;
  - letzte Originale zeigen Betriebsstellenzuordnung.
- Protected REF-DATA-002-Grafik/Zoom/GVP/KOR bleibt unverändert.
- Source-Audit der neuen UI-/Vertragsmarker: 10/10 PASS.
- Maßgebliche Commits: `45bc19bf...` Reset, `085d134a...` Bahnhofsauswahl.
- Noch nicht deployt/runtimegetestet.

## REF-DATA-003 – Bahnhofsauswahl Runtime bestätigt 30.09.2026
Status: **IMPORT-STATION RUNTIME BESTÄTIGT / RESET NOCH OFFEN**
Benutzernachweis per Synology-Admin-UI:
- Referenzsuche findet `MPB – München-Pasing Bbf`;
- Auswahl kann mit „Auf alle Gruppen anwenden“ auf die analysierten Fachgruppen übertragen werden;
- Gruppen zeigen danach `Zugeordnet: MPB – München-Pasing Bbf`;
- neu gespeicherte Originale zeigen im Importergebnis die Betriebsstelle;
- Importlauf: **6 importiert · 34 Duplikate · 0 übersprungen · 0 Fehler**.
Bewertung:
- station_code/name-Payload funktioniert für neue Importe;
- die 34 Duplikate stammen aus dem bereits vorhandenen unveränderten Testbestand und werden bewusst nicht nachträglich umgeschrieben;
- für einen konsistenten Pasing-Bestand ist der bereits freigegebene Staging-Reset mit anschließendem Komplett-Neuimport jetzt der richtige Weg.
Zusätzlich sichtbar: BlueMail-Eingang `TB-Helfer: Trassendaten-Prüffall MPB – Pasing Bbf`; damit ist der Konflikt-Mailversand praktisch nachgewiesen.

## REF-DATA-003 – Staging-Survey-Reset BESTÄTIGT 30.09.2026
- Geschützter Survey-Testreset wurde auf `staging` tatsächlich ausgeführt.
- Ergebnis: `status = RESET`, `removed_storage_keys = 60`.
- Vorher: 60 Originale / 60 Parser / 60 Assets / 33 Achsen / 35 Segmente / 1 Konflikt / 164 History.
- Nachher: alle ausgewiesenen Survey-Zähler = 0.
- Damit ist der Staging-Vermessungs-Testbestand vollständig und konsistent zurückgesetzt.
- Andere Fachbereiche waren nicht Teil dieses Resetpfads.

## REF-DATA-003 – Diagnose-/Objektidentitätsblock SOURCE FERTIG 30.09.2026
Status: **SOURCE FERTIG / MIGRATION 018 NICHT DEPLOYT / RUNTIME-E2E OFFEN**
Maßgeblicher HEAD: `5604ece1bcedf1c055933cfe11f2f62a084677a3`.

Umgesetzt:
- Migration `018_survey_object_identity.sql` mit `survey_catalog_axes.object_key`;
- stabile Weichenidentität `W:<Nr>` und WVB-Identität `WVB:<A>-<B>`;
- unterschiedliche bekannte WVB werden nicht mehr allein wegen räumlicher Nähe gegeneinander gemerged/geprüft;
- Admin-Import transportiert `object_key` bis in Originalmetadaten/Katalog;
- sichtbares Route/Gleis/Objekt-Prefill wird nach DOM-Aufbau aus den erkannten Analysewerten abgesichert;
- Admin-Grafik bietet Mehrfachtreffer bei überlagerten/nahen Achsen und hebt die gewählte Achse hervor;
- offene Prüffälle erscheinen mit `Problem ansehen`;
- Konflikt-Detail-API liefert verständliche Erklärung, Quellen, Messwerte und beide Vergleichsgeometrien;
- Entscheidungen `KEEP_BOTH / KEEP_EXISTING / ACCEPT_CANDIDATE / CONNECT` bleiben versioniert/auditierbar;
- bestätigter REF-DATA-002-Tiefzoom/Hover/KOR/GVP-Grundblock blieb erhalten;
- Android-`app/**` unverändert.

Source-Nachweis:
- finaler GitHub-Strukturaudit auf HEAD: **25/25 PASS**;
- Parser-Check-Drift auf v3 korrigiert;
- Actions Runs zum HEAD liefern erneut `steps=[]`; daher **kein CI-Testnachweis**.

Nächster Gate:
Synology auf Migration 18/18 bringen → Pasing/MPB neu testen → Prefill, W/WVB object_key, Multi-Hit und Problemfenster praktisch bestätigen. Erst danach eigenständige Trassendatenübersicht.

## REF-DATA-003 – Parser-v4-/Reindex-Upgrade SOURCE FERTIG 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT + RUNTIME-E2E OFFEN**
Maßgeblicher Code-HEAD vor Statuscommit: `31c1c6f6a066a76b694aef56de7719457750a146`.

Nach Deployment-Audit zusätzlich umgesetzt:
- Parser-Cache-Version auf `tb-survey-parser-4` erhöht;
- interner Reparse-Pfad für fehlende/veraltete Parse-Caches;
- Reindex arbeitet über `LEFT JOIN` auch bei fehlendem Parse-Catalog;
- Parser-Version und `source_sha256` bestimmen, ob neu geparst werden muss;
- bestehende `ORIGINAL_TRA`-Segmente werden bei Reindex nicht dupliziert;
- bestehende Achse erhält fehlenden `object_key` ausschließlich fill-only;
- vorhandene Admin-/Objektidentität wird nicht überschrieben;
- Achstyp-Widerspruch verhindert automatische Anreicherung;
- Status `ALREADY_INDEXED_ENRICHED` + History/Audit dokumentieren den Upgradefall.

Source-Audit auf Code-HEAD: **23/23 PASS**.
GitHub Actions zu diesem HEAD: Jobs erneut mit `steps=[]` → **kein CI-Testnachweis**.

Nächster Runtime-Gate:
Synology aktualisieren → Migration 18/18 → Reindex ausführen → `reparsed`/`ALREADY_INDEXED_ENRICHED` prüfen → Pasing/MPB-Prefill, W/WVB object_key, Multi-Hit und Problemfenster praktisch testen.

## REF-DATA-003 – Migration18/Parser-v4 Runtimebasis bestätigt 30.09.2026
Status: **DEPLOYT / RUNTIMEBASIS TEILBESTÄTIGT / REINDEX FEHLERHAFT**
- Systemstatus Synology: Migrationen **18 / 18**, latest **18 / 18**, ausstehend: keine.
- API staging läuft; Datenbank, interne API, Worker und öffentliche API zeigen OK.
- Containerprüfung: `PARSER_VERSION = tb-survey-parser-4`.
- Reindex-Lauf: `original_count=40`, `reparsed=40`, `statuses={ALREADY_INDEXED:18, INDEXED_ASSET:19}`, `failures=3`.
- Damit sind Migration 018 und Parser-v4-Deployment praktisch bestätigt.
- Das Reindex-/Upgrade-Gate ist **nicht bestanden**, weil 3 Originale mit Exception ausfallen.
- Vor Codeänderung müssen die drei betroffenen Originale und exakten Exceptions ermittelt werden.

## REF-DATA-003 – History-ENRICH-Constraint Fix SOURCE FERTIG 30.09.2026
Status: **SOURCE FERTIG / MIGRATION 019 NICHT DEPLOYT**
- Reindex failures=3 waren eindeutig die drei WVB 10-11, 14-22, 19-20.
- Ursache: `survey_catalog_history_operation_check` erlaubte die neue versionierte History-Operation `ENRICH` nicht.
- Neue Migration `019_survey_catalog_history_enrich.sql` erweitert die erlaubte Liste um `ENRICH`; bestehende Operationen bleiben erhalten.
- Keine Änderung an WVB-Geometrie, Parser, Merge oder object_key-Logik.
- Source-Audit des Fixes: 7/7 PASS.
- Nächster Schritt: Migration 019 auf Synology anwenden → Systemstatus 19/19 → Reindex erneut, Ziel `failures=0` und 3x `ALREADY_INDEXED_ENRICHED`.

## REF-DATA-003 – Reindex nach ENRICH-Fix runtime-grün 30.09.2026
- Synology/API-Terminal: `reindex_all_originals()` erneut ausgeführt.
- Ergebnis: `original_count=40`, `parser_version=tb-survey-parser-4`, `reparsed=0`.
- Statusverteilung: `ALREADY_INDEXED=19`, `INDEXED_ASSET=19`, `ALREADY_INDEXED_ENRICHED=2`.
- `failures=0`: der zuvor reproduzierte ENRICH-Constraint-Fehler ist im laufenden DB-Pfad behoben.
- Die 40 Originale sind vollständig bilanziert; kein Reindex-Fehler mehr offen.
- Abweichung zur Voraberwartung: nur 2 statt 3 WVB wurden in diesem Lauf als `ALREADY_INDEXED_ENRICHED` gemeldet. Vor weiterer Fachfreigabe read-only feststellen, welcher der drei WVB-Fälle bereits als `ALREADY_INDEXED` läuft und welche `object_key` die drei vorhandenen Achsen tatsächlich tragen.
- Der vorliegende Screenshot zeigt nicht den Systemstatus `19/19`; Migration 019 deshalb in der Dokumentation erst nach separatem Systemstatus-/Migrationstabellen-Nachweis als 19/19 bestätigen.
- Nächster Runtime-Schritt: WVB 10-11 / 14-22 / 19-20 mit Originalname, Achse, `object_key` und Segmentzahl gegenüberstellen; danach Pasing-Prefill, Multi-Hit und „Problem ansehen“ praktisch testen.


## REF-DATA-003 – Legacy-WVB-Identitätsreparatur SOURCE 30.09.2026
Status: **SOURCE KORRIGIERT / NICHT DEPLOYT / RUNTIME-RETEST OFFEN**
- Migration 019 ist auf Synology mit **19/19** und pending=[] bestätigt; Reindex vor diesem Fix lief mit failures=0.
- Read-only-Nachweis hat den verbliebenen Altfehler eindeutig gezeigt: `WVB_19-20...` hing noch an derselben Achse wie `WVB_14-22...` und erbte dadurch fälschlich `object_key=WVB:14-22`.
- Ursache ist ein Legacy-Merge aus der Zeit vor stabiler WVB-object_key-Identität; die aktuelle Kandidatenlogik verhindert neue Merges unterschiedlicher bekannter object_keys bereits.
- Sourcefix in `server/tbserver/survey_catalog.py`: bestehende ORIGINAL_TRA-Segmente dürfen beim Reindex nur dann auf eine getrennte object_key-Achse umgesetzt werden, wenn ein zweites Original auf der alten Achse deren bisherigen object_key unabhängig bestätigt. Offene Konflikte blockieren die automatische Reparatur.
- Segment-ID und Raw-Original bleiben unverändert; Umhängen wird versioniert als SEGMENT-UPDATE historisiert und mit `SURVEY_CATALOG_SEGMENT_IDENTITY_REASSIGN` auditiert.
- Neuer Reindex-Status für diesen Altfall: `ALREADY_INDEXED_REASSIGNED`.
- Code-/Check-Commits: `a7149047...`, `523450f8...`, `fcd2c024...`.
- GitHub-Workflow zum Code-HEAD meldet failure, der Foundation-Job liefert aber erneut `steps=[]`; deshalb **kein ausführbarer CI-Testnachweis** und keine Aussage über einen fachlichen Testfehler.
- Nächster Gate: nur `survey_catalog.py` auf Synology übernehmen → API neu erstellen/starten → Reindex → WVB 10-11/14-22/19-20 read-only prüfen. Erst danach Prefill/Multi-Hit/„Problem ansehen“ und Search/Availability/Geometry.

## REF-DATA-003 – Legacy-WVB-Identitätsreparatur RUNTIME BESTÄTIGT 30.09.2026
Status: **DEPLOYT / REINDEX GRÜN / WVB-IDENTITÄT BESTÄTIGT**
- Synology-Reindex nach Deploy des Reparaturpfads: `original_count=64`, `parser_version=tb-survey-parser-4`, `reparsed=0`, `ALREADY_INDEXED=36`, `INDEXED_ASSET=27`, `ALREADY_INDEXED_REASSIGNED=1`, `failures=0`.
- Die 64 Originale sind vollständig bilanziert (36 + 27 + 1 = 64); gegenüber dem vorherigen 40er Lauf ist der Runtimebestand inzwischen größer. Das wird als Bestandsänderung dokumentiert, nicht als Fehler gewertet.
- WVB 10-11: eigene Achse, `object_key=WVB:10-11`, `axis_segment_count=1`.
- WVB 14-22: eigene Achse, `object_key=WVB:14-22`, `axis_segment_count=1`.
- WVB 19-20: nach Reparatur eigene Achse, `object_key=WVB:19-20`, `axis_segment_count=1`.
- Damit ist der zuvor reproduzierte Legacy-Merge WVB 19-20 → WVB 14-22 praktisch behoben. Raw-Originale blieben unangetastet; keine Segmentduplikation sichtbar.
- Nächster Pasing-Gate: Browser-Prefill Strecke/Gleis/Objekt → Multi-Hit bei überlagerten Achsen → „Problem ansehen“ → anschließend Search/Availability/Geometry.

## REF-DATA-003 – Import-UI verständlicher + geschützter Reset im Portal SOURCE 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT OFFEN**
- `Ziel nach Analyse` wurde in der Importanalyse zu `Speicherziel` umbenannt; GLOBAL heißt sichtbar jetzt `Gemeinsamer Referenzbestand`.
- Bei GLOBAL wird das Feld `Firma / Baustelle` vollständig ausgeblendet statt nur deaktiviert. Bei Firmen-/Baustellenziel erscheint es weiterhin.
- Auf der Trassierungsdaten-Seite ist der bereits vorhandene geschützte Survey-Testreset jetzt als Admin-UI verfügbar: Preview/Zählwerte, exakter Bestätigungstext `VERMESSUNG TESTDATEN LOESCHEN`, zweite Browserbestätigung, danach DELETE über den bestehenden CSRF-geschützten Endpunkt.
- Reset-Karte erscheint nur, wenn der Backend-Endpunkt in der aktuellen Umgebung erlaubt ist; Produktion bleibt serverseitig hart gesperrt.
- Reset löscht nur Vermessungs-/Trassierungs-Testbestand und daraus abgeleiteten Survey-Katalog; Firmen, Benutzer, Baustellen und BTB bleiben erhalten.
- Code: `server/templates/v1_admin_survey_import.html`; Check 115 um die neuen UI-/Reset-Marker erweitert.
- Commits: `321be671...`, `0069fd0f...`, `6eb60ecb...`, `5735fa31...`.

## REF-DATA-003 – Zentraler Admin-Import + Terminal-Reset SOURCE 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT + RESET OFFEN**
- Admin-`Trassierungsdaten` ist jetzt source-seitig eindeutig **zentral/global**: keine Speicherziel-Auswahl und keine Firma/Baustelle-Auswahl mehr; Importentscheidungen senden fest `scope=GLOBAL_REFERENCE`, `worksite_id=null`.
- Firmen-/Baustellenbeiträge bleiben ein separater Contribution-/Android-Fachpfad und werden auf dieser Admin-Seite nicht mehr angeboten.
- Dashboard `Speicher nach Firma`: feste Kategorie `Trassierungsdaten` entfernt; angezeigt werden nur Fotos, PDFs und sonstige firmeneigene Dateien.
- Geschützter Survey-Testreset ist zusätzlich als explizite API-Container-Terminalfunktion verfügbar: `survey_test_data_preview_from_terminal()` und `reset_survey_test_data_from_terminal(confirm_text)`.
- Schutz bleibt erhalten: nur dev/test/staging, exakter Bestätigungstext, FK-sichere DB-Reihenfolge, Storage-Dateien erst nach Commit entfernen, Audit source=`API_CONTAINER_TERMINAL`.
- Keine Migration. Runtime-Dateien für diesen Block: `server/templates/v1_admin_survey_import.html`, `server/templates/v1_admin_dashboard.html`, `server/tbserver/admin_purge.py`.
- Checks 86/114/115 an den neuen Vertrag angepasst.

## REF-DATA-003 – Survey-Staging-Reset RUNTIME BESTÄTIGT 30.09.2026
Status: **RESET BESTÄTIGT / SAUBERER NEUIMPORT BEREIT**
- API-Container-Terminal Preview vor Reset: environment=`staging`, allowed=true.
- Bestand vor Reset: 64 Survey-Originale, 64 Parser-Auswertungen, 64 Katalog-Assets, 36 Achsen, 37 Segmente, 1 Prüffall, 325 History-Einträge; Survey-Sections/Track-Source-Links/Import-File-Rows jeweils 0.
- Terminal-Reset mit exaktem Bestätigungstext ausgeführt: `status=RESET`, `removed_storage_keys=64`.
- Direkt danach meldet der Reset selbst alle Survey-Zähler 0; separate Preview bestätigt nochmals alle Survey-Zähler 0.
- Damit ist der Vermessungs-/Trassierungs-Testbestand vollständig aus DB/Katalog und Storage entfernt. Nächster Schritt: Pasing/MPB vollständig frisch hochladen und den Gesamtpfad erneut prüfen.

## REF-DATA-003 – Frischer Pasing/MPB-Neuimport RUNTIME BESTÄTIGT 30.09.2026
Status: **IMPORT GRÜN / 40 VON 40 / KEINE FEHLER**
- Nach vollständig leerem Survey-Testbestand wurden die Pasing/MPB-Daten frisch über die zentrale Admin-Seite importiert.
- Importergebnis im Browser: **40 importiert · 0 Duplikate · 0 übersprungen · 0 Fehler**.
- Importzeilen zeigen durchgehend `GLOBAL`, `MPB – München-Pasing Bbf` und `fachlich ausgewertet`.
- WVB 19-20 TRA/GRA sowie WVB 14-22 und WVB 10-11 sind im Importergebnis enthalten; keine offenen Trassendaten-Prüffälle nach dem Import.
- GVP/KF wurde ebenfalls importiert; Hinweise zu 259 Punkten ohne eigenen Strecken-/Stationsbezug bleiben Analysehinweise, der Import selbst ist fehlerfrei.
- Nach Listenaktualisierung erscheinen die frisch gespeicherten Originale im Portal; WVB-Originale stehen GLOBAL + MPB, Strecke bei WVB leer wie fachlich vorgesehen.
- Nächster Gate: read-only Katalogcheck der drei WVB-object_keys/Achsen nach **frischem** Import sowie anschließend Search/Availability/Geometry und Multi-Hit.

## REF-DATA-003 – Frischer WVB-Identitätscheck VOLLSTÄNDIG BESTÄTIGT 30.09.2026
Status: **BESTÄTIGT / KEINE NACHREPARATUR ERFORDERLICH**
- Kompakte read-only-Abfrage direkt nach dem sauberen 40/40-Neuimport zeigt genau drei WVB-TRA-Achsen.
- WVB 10-11 → `object_key=WVB:10-11`, `station_code=MPB`, eigene `axis_id=e91193d2-c26b-4cf5-b367-720774f90bf6`, `axis_segment_count=1`.
- WVB 14-22 → `object_key=WVB:14-22`, `station_code=MPB`, eigene `axis_id=d2a4a7c9-eebc-44d8-8a08-5ad85f67d764`, `axis_segment_count=1`.
- WVB 19-20 → `object_key=WVB:19-20`, `station_code=MPB`, eigene `axis_id=86f6282f-29ca-4f91-901c-bea316488300`, `axis_segment_count=1`.
- Alle drei axis_id sind verschieden. Der frühere 14-22/19-20-Legacy-Merge tritt beim Neuimport nicht mehr auf.
- Dieser Nachweis erfolgte **ohne Reindex-/Reassign-Nachreparatur**. Der neue Importpfad erzeugt WVB-Identitäten direkt korrekt.
- Nächster Gate: Runtime-Funktionspfade Search / Availability / Geometry für MPB/5528 sowie Portal-Multi-Hit.

## REF-DATA-003 – Search/Availability/Geometry MPB RUNTIME 30.09.2026
Status: **API-FUNKTIONSPFADE ERREICHBAR / KM-QUALITÄT NOCH NICHT VOLLSTÄNDIG**
- `search_catalog(q='MPB')`: 20 Achsen; Station MPB = München-Pasing Bbf; `asset_count=40`; Datenarten GRA/GVP/TRA. Die Achsenliste enthält Bahnhofsgleise, Streckenachsen und WVB 10-11/14-22/19-20 mit korrektem object_key.
- `availability(station_code='MPB')`: `status=AVAILABLE`, `axis_count=20`, `active_segment_count=21`, `review_segment_count=0`.
- Verfügbare Assets: GRA=17, GVP=2, TRA=21; Source-Kind ORIGINAL_TRA=21.
- KM-Qualität: **17 FEHLT / 4 AUTOMATISCH**. Damit ist die API selbst funktionsfähig, aber die fachliche KM-/Grün-Abdeckung ist noch nicht vollständig und bleibt ein offener Diagnosepunkt.
- `geometry(station_code='MPB')`: `count=21`; Geometrien für Bahnhofsgleise, Streckenachsen und alle drei WVB werden geliefert. WVB-Geometrien tragen passende object_keys und Punktlisten.
- 20 Achsen vs. 21 aktive Segmente ist erklärbar durch mindestens eine Achse mit zwei aktiven Segmenten; im Geometry-Output erscheint `Str 5524-1` mit derselben axis_id zweimal. Das ist zunächst Beobachtung, kein Fehlernachweis.
- Nächster Gate: KM-Qualität pro Achse auflösen (welche 4 grün / welche 17 rot und warum) und danach Portal-Multi-Hit praktisch prüfen.

## REF-DATA-005 – Importkorrektur statt Reset bei vergessener Zuordnung 30.09.2026
- Neuer Zielvertrag: gleicher Dateiinhalt + gleiche Zuordnung = Duplikat ohne Änderung; gleicher Dateiinhalt + abweichende fachliche Zuordnung = Warnung mit bewusster Aktion **vorhandene Zuordnung aktualisieren**.
- Dabei keine neue Rohdatei anlegen. Bestehendes Original bleibt erhalten; nur Route/Gleis/Betriebsstelle/object_key/source_date und der abgeleitete Survey-Katalog werden kontrolliert aktualisiert.
- Aktueller Testbestand darf auf ausdrücklichen Benutzerwunsch nochmals vollständig per geschütztem staging-Terminalreset geleert werden.

## REF-DATA-005 – Duplicate-Korrekturworkflow SOURCE 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT + RUNTIME OFFEN**
- Admin-Import erkennt jetzt bei identischem SHA256/Dateigröße den Unterschied zwischen echter identischer Zuordnung und abweichender fachlicher Zuordnung.
- Identisch + gleiche Zuordnung bleibt `DUPLIKAT` ohne Änderung.
- Identisch + abweichende Strecke/Gleis/Betriebsstelle/object_key liefert zunächst `VORHANDEN_ABWEICHEND` mit Vorher→Nachher-Anzeige; Analyse-Session bleibt für die bewusste zweite Bestätigung erhalten.
- UI-Schalter: `Vorhandene identische Dateien bei abweichender Zuordnung aktualisieren`; zusätzlich Browser-Sicherheitsabfrage.
- Bestätigte Korrektur behält Original-ID, SHA256 und Raw-Datei unverändert; aktualisiert Original-Metadaten + Asset/Achsen-Zuordnung und liefert `AKTUALISIERT`.
- Katalogkorrektur schützt offene Konflikte, Mehrfach-Source-Segmente, Identity-Kollisionen und destruktive Änderung geteilter Achsen; einfache Fill-ins wie fehlende station_code sind auch auf geteilter Achse möglich.
- Audit: `ADMIN_SURVEY_ORIGINAL_ASSIGNMENT_UPDATE` + `SURVEY_CATALOG_ASSIGNMENT_UPDATE`; Axis-History wird versioniert als UPDATE geschrieben.
- Keine Migration erforderlich.

## REF-DATA-006 – Deutschlandweite Trassendaten-Gesamtansicht geplant 02.10.2026
Status: **ANFORDERUNG GESICHERT / NACH MGHN-KM-GATE**
- Neue Admin-Übersicht soll deutschlandweit zeigen, wo bereits gemeinsame Trassierungs-/Vermessungsdaten vorhanden sind.
- Suche mindestens nach Strecke, Bahnhof/Betriebsstelle, Gleis/Achse und Weiche/WVB/Objekt.
- Treffer sollen die Gesamtgrafik fokussieren und in eine vorhandene Detail-/Katalogansicht führen.
- Farbregeln bleiben fachlich verbindlich: GRÜN automatisch belastbare KM, ORANGE manuell ergänzt, ROT Geometrie ohne belastbare KM, GRAU Objekt/Bestand ohne nutzbare Geometrie bzw. unplausibel.
- Technische Vorprüfung: Survey-Katalog speichert Bounding-Box/Geometrie in X/Y, aber aktuell keinen expliziten EPSG-/Lat/Lon-Vertrag. Vor echter Deutschlandkarte Koordinatenreferenz anhand realer MPB/MGHN-Daten prüfen; keine scheinpräzise Karte aus unbekanntem CRS bauen.

## REF-DATA-006 – Gesamtansicht Deutschland Umsetzung freigegeben 02.10.2026
- Gessertshausen: die 2 automatischen KM-Achsen sind Gl 302 und Gl 303, Quelle jeweils `GRA_KM`.
- MPB/MGHN liegen in DB_REF2016 Zone 4; EPSG:5684 ist als horizontales Projektionssystem verifiziert.
- Deutschlandübersicht kann nun als echte WGS84-basierte Übersicht umgesetzt werden. Für bundesweite Zukunftsdaten Zonen 2/3/4/5 berücksichtigen.

## REF-DATA-006 – Deutschlandweite Trassendatenübersicht SOURCE 02.10.2026
Status: **SOURCE FERTIG / DEPLOYMENT + RUNTIME OFFEN**
- Neue Admin-Seite `/admin/v1/survey-overview` und Navigation `Trassendatenübersicht` gebaut.
- Neue read-only API `GET /api/v1/survey-catalog/coverage` liefert aktive GLOBAL-Segmente, Suchmetadaten, KM-Qualität und für die Übersicht nach WGS84 transformierte Linien.
- DB_REF-Zonen 2–5 werden anhand des Rechtswert-Millionenstreifens auf EPSG 5682/5683/5684/5685 abgebildet; Originalkoordinaten in DB/Katalog bleiben unverändert.
- UI: Deutschlandumriss, Farblogik GRÜN/ORANGE/ROT/GRAU, Suche nach Strecke/Bahnhof/Gleis/Objekt, Trefferliste, Klick-Fokus/Zoom, Betriebsstellenmarker und Detailbox.
- Deutschlandumriss wird lokal als SVG gerendert; kein externer Kartendienst zur Laufzeit erforderlich.
- Neue Abhängigkeit `pyproj==3.7.2`; Container-Image muss deshalb neu gebaut werden.

## REF-DATA-006 – Deutschlandübersicht vorerst zurückgerollt 02.10.2026
- Wegen reproduzierbarem API-Startabbruch durch fehlendes pyproj wurde die neue Deutschlandübersicht vollständig aus dem aktiven Runtime-Source zurückgenommen.
- Server-Source steht wieder auf dem bestätigten pre-pyproj-Stand; Datenbank/Migrationen/Survey-Daten wurden nicht verändert.
- REF-DATA-006 bleibt fachlich gewünscht, aber Umsetzung erst nach stabiler Server-Runtime und neuem Deploy-Konzept.

## Server-Runtime nach REF-DATA-006-Rollback 02.10.2026
- API-Terminal wieder funktionsfähig; Migrationen 19/19, keine pending Migrationen.
- Vollständige Stabilitätsfreigabe folgt nach Health-/Worker-/Monitor-/Portal-Kurztest.

## Server-Runtime nach REF-DATA-006-Rollback 02.10.2026
- API-Terminal wieder funktionsfähig; Migrationen 19/19, keine pending Migrationen.
- Vollständige Stabilitätsfreigabe folgt nach Health-/Worker-/Monitor-/Portal-Kurztest.

## Server-Runtime wieder stabil 02.10.2026
- Nach Rücknahme der pyproj-Deutschlandübersicht läuft der Server wieder.
- Health: status=ok, database=up, migrations=ok, server_version=1.0.0-dev.
- Migrationen 19/19 ohne pending; Nutzer bestätigt die übrigen Dienste/Portal als laufend.
- REF-DATA-006 bleibt als fachlicher Wunsch offen und wird später deployment-sicher neu umgesetzt.

## Server-Runtime wieder stabil 02.10.2026
- Nach Rücknahme der pyproj-Deutschlandübersicht läuft der Server wieder.
- Health: status=ok, database=up, migrations=ok, server_version=1.0.0-dev.
- Migrationen 19/19 ohne pending; Nutzer bestätigt die übrigen Dienste/Portal als laufend.
- REF-DATA-006 bleibt als fachlicher Wunsch offen und wird später deployment-sicher neu umgesetzt.

## REF-DATA-006 – Deutschlandübersicht Variante A SOURCE FERTIG 02.10.2026
- PostGIS-Variante ist source-seitig umgesetzt, ohne neue Python-/Image-Abhängigkeit.
- Neue Admin-Seite: /admin/v1/survey-overview.
- Coverage-API liefert zunächst nur kompakte Achsen-/Betriebsstellenübersicht in WGS84; Detailgeometrie wird erst bei Auswahl nachgeladen.
- Suche: Strecke, Bahnhof/Betriebsstelle, Gleis/Achse und W/WVB/Objekt.
- Darstellung: Deutschlandübersicht, Marker, Zoom/Pan, Trefferliste und Qualitätsfarben GRÜN/ORANGE/ROT/GRAU.
- Original-/Katalogkoordinaten bleiben unverändert; ST_Transform arbeitet nur read-only in der Ausgabe.
- requirements.txt bleibt unverändert und enthält kein pyproj.
- Runtime/Synology-Test noch offen.


## 02.10.2026 – REF-DATA-007 Trassenkennzahlen / Lazy-Übersicht
- SOURCE: neuer Survey-Summary-Pfad + Dashboard-/Übersicht-Kennzahlen fertig.
- Zielwerte: Gesamt-Trassenlänge, KM bekannt, automatisch, manuell, Handy-manuell, KM fehlt, Bahnhöfe, Gleise, Strecken.
- Deutschlandübersicht lädt Details künftig achsweise statt vollständige Linien/KM beim Einstieg.
- Android-Vertrag für fertige manuelle KM-Abschnitte ist fest; Android-Implementierung bleibt eigener späterer Arbeitsblock.
- Status: **SOURCE FERTIG / DEPLOYMENT + SYNLOGY-RUNTIME OFFEN**.


## 02.10.2026 – REF-DATA-007 Runtime-Gate 1 bestanden
- Synology-Summary läuft: 39 Achsen, 40 aktive Segmente, 128.911 km Trassenlänge.
- Davon 116.579 km mit belastbarer automatischer KM; 12.332 km ohne belastbare KM.
- Manuelle/Handy-KM aktuell 0 km – erwartbar, da Android-First-Class-Sync noch nicht umgesetzt ist.
- Status jetzt: **DEPLOYT + SUMMARY-RUNTIME BESTÄTIGT / BROWSER-E2E OFFEN**.


## REF-DATA-007 – Browser-Timeout 524 am 02.10.2026
- Benutzerbeleg: /api/v1/survey-catalog/coverage/summary zeigt Cloudflare 524, Zeit 15:54:39 UTC / 17:54:39 Europe/Berlin.
- Status: **HTTP-/BROWSER-E2E FAIL / ROOT CAUSE OFFEN**. Browser und Cloudflare laut Fehlerseite erreichbar; Origin-Antwort nicht rechtzeitig erhalten. Tatsächliche Requestdauer nicht gemessen.
- Bisheriger Summary-Helper-PASS bleibt Funktionsnachweis, ist ohne Zeitmessung kein Performance-PASS.
- Nächster Nachweis: read-only Summary im API-Container mit perf_counter und faulthandler (30-Sekunden-Grenze für separaten Diagnoseprozess). Danach Befund-abhängig Berechnung/DB-Wartezeit oder HTTP/Auth/Proxy weiter eingrenzen.
- Keine Code-/NAS-/Cloudflare-/Android-Änderung und keine neue erfolgreiche Laufzeitmessung in diesem Chatblock.


## REF-DATA-007 – GVP-Performancefix SOURCE 02.10.2026
- Synology-Diagnose: direkter Summary-Aufruf nach 30 s noch in _project_xy_station → _segment_km_quality; Browser vorher Cloudflare 524.
- Gezielter Fix: exakter räumlicher Segmentindex für GVP-Zuordnung, ohne Geometrievereinfachung/neue Abhängigkeit/Migration. Alte KM-/Farbregeln bleiben bestehen.
- Lokal: 11/11 Tests PASS; synthetisch 20.001 Samples / 200 GVP: 4,876853 s → 0,053314 s inklusive Indexaufbau, exakt gleiche Ergebnisse. Keine NAS-Leistung daraus ableiten.
- Status: **QUELLSTAND FERTIG / NICHT DEPLOYT / SYNOLOGY-HTTP-BROWSER-E2E OFFEN**. NAS: nur server/tbserver/survey_catalog.py übernehmen und API neu starten; dann Laufzeit und unveränderte Kennzahlen sowie Browseranzeige prüfen.


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
- Auf Grundlage der Nutzer-Gesamtfreigabe und Vertragscommit c4e7524 umgesetzt: vollständige5302-0 trotz Stationssprüngen; verständliche Importwarnungen/Erstimport-Provenienz; opt-in amtliche Luftbilder BY/SN/BW; bestätigte Gleisabschnitte und WA/WE/WEZ auf vorhandenen Achsen; Bearbeiten/Deaktivieren; reversibles Testquellen-Reset mit serverseitiger Vorschau.
- Originale bleiben unverändert. Migration020 erweitert Assetstatus um INACTIVE. Keine NAS-Mutation, kein Android-Code. Vorige Benutzerbestätigungen bleiben geschützt.
- 80 Backend-Funktionstests/Pydantic mit Storage-Doubles, Node119 mit DOM/Transport/Image-Doubles und Syntaxprüfung PASS. Privat-ZIP5302S jetzt16497 Punkte. Echter HTTP/DB/PostGIS-/visueller Browser-/Luftbild-/NAS-E2E bleibt offen.
- Nächster Schritt: dokumentierte Dateiübernahme, Migration020, API/Worker-Neustart und gezielte Live-Abnahme. Android konsumiert workspace_records erst nach eigener Umsetzung.

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
- Prüfungen: Node119 PASS (Produktions-JS, DOM/Transport-Doubles einschließlich neuer Such-/Filter-/Reload-/Linkfälle und aller WA/WE/WEZ-Regressionen); Python127 21 Tests PASS (Storage-/Transaktions-Doubles, Linkkonflikt/Alte-Clients/Globalpaging/Archiv-Restore); Python128 2 Tests PASS (Produktions-Lesehelfer, SQL-Cursor-Doubles); Python-/JS-Syntax PASS. Kein Live-FastAPI/PostgreSQL/PostGIS, kein visueller Browser-/Jinja-Rendernachweis, kein NAS-/Geräte-E2E. Jinja lokal nicht installiert, Chromium weiterhin nicht vorhanden.
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

### PORTAL-016 – Server/Portal umgesetzt, NAS-Abnahme offen (18.10.2026)
- Viertelstunden-Auswahllisten (96 Werte; alte Minuten unverändert sichtbar), Vorschlag aus letztem BTB derselben Baustelle/Team mit Nachtwechsel. Neue Firma in Personal und Maschinen unter derselben Identität; getrenntes Ausblenden leerer Bereiche. Berichtszeit wird serverseitig innerhalb des versionierten Header-PATCH automatisch in verknüpfte Firmenzeiten übernommen; individuelle Abweichungen geschützt, Entry-Versionen/Events mitgeführt.
- Arbeitseinträge uhrzeit + additives uhrzeitBis, Portal/PDF zeigen Zeitraum. Bewusstes Löschen mit null, Server-PATCH schützt ausgelassene Endzeit älterer Clients. Bindungen REPORT/MANUAL bleiben erhalten bzw. werden bei Altclient-Zeitänderung angepasst. app/** unverändert, Androidanzeige/Save/PDF später, keine APK.
- Direkte Mehrfach-Fotoauswahl im Arbeitseintrag übernimmt typ/Firma/firmaId/Unterbaustelle/Text/manuelles Eintrags-KM und nutzt vorhandene 2MB-Vorbereitung/Upload. Ein Bildbestand, keine erneute Eingabe unten. Teilfehler hält gespeicherte Entry-ID und im offenen Dialog bereits erfolgreiche Fotos fest; Retry überspringt unveränderten Entry-PATCH (NO_CHANGES) und erzeugt keinen zweiten Arbeitseintrag. Unveränderter separater Fotoupload nutzt denselben Transport.
- Wetter-Ortssuche/Ortsauswahl und lesender Abruf nach BTB-/Teamrechten über feste Open-Meteo-Endpunkte; Vorschlag für gespeicherte Schicht mit drei Zeitpunkten wie Android (1/6,1/2,5/6 auf Viertelstunde gerundet), Archive für ältere Tage, Stundenmodellwerte gekennzeichnet, 15min begrenzter Cache/8s Timeout. Bestehende Werte nur nach Nachfrage ersetzen, Speichern über normalen versionierten Header. Optionaler API-Key nur Containerumgebung; kommerzielle Nutzung entsprechend Anbieterfreigabe, kein Abo gebucht.
- Lokal PASS: neuer Check152 (Produktions-Python/JS mit Cursor-/API-/DOM-/Wetter-Doubles; Vererbung/Abweichungen/Winterzeit/Altclient/Version/Events, Nachtwetter, PDF-Zeile, 15min/Datum/Firmen/Fotoübernahme/Transportvorbereitung/Teilfehler/erneutes Speichern); Check151 unverändert PASS. Python-AST und JS-Syntax PASS. PDF 22:00–01:00 tatsächlich erzeugt, Textprüfung und PNG-Sichtkontrolle PASS. Kein echter PostgreSQL/FastAPI/Browser/NAS-E2E oder echter NAS-Wetterabruf dieses Blocks; lokale Pakete/Browserbinary fehlen, Paketinstallation nicht verfügbar. Keine Actions gestartet [skip ci] (CI-COST-001), allgemeine CI-Restpunkte unverändert.
- Sechs Runtime-Dateien: server/tbserver/btb.py, btb_times.py(neu),btb_weather.py(neu),btb_pdf_android_style.py; server/templates/v1_btb.html,v1_btb_times.html(neu). API-Neustart und StrgF5 bei bestehender Quellordnereinbindung, sonst Image aus aktualisiertem Stand neu erstellen. Keine Migration, nichts löschen, vorhandene Fotos/Abschluss-PDFs nicht pauschal neu erzeugen. Genaue Übernahme/Praxistests: projektsteuerung/uebergaben/PORTAL-016_NAS_UEBERNAHME.md.
- Bestätigter PORTAL-015-Baum und KM-Suche geschützt; Altbild-Unterbaustellenreparatur bleibt separat. Exceldatei wegen bekanntem binärem Defekt unverändert; keine Ersatzhistorie. PR6 bleibt Draft. Basis SERVER071075e21bcabc0bdd829bc05083d71cd5e5e58b; ANDROID6cec34423e34315c581b743668724abe17daa333.

### PORTAL-017 – Eintragsfotos/GPS/Nachtschicht umgesetzt, NAS-Abnahme offen (18.10.2026)
- Fotos sind über btb_entry_id / BTB-Linkmetadaten btbEntryId fest an konkreten Eintrag gebunden; Eintrag desselben BTB/Abschnitt und Schreibrecht geprüft. Vorschau am Eintrag und im Bearbeitungsdialog; weitere Fotos, Fotoeditor mit Verschieben/Zuordnen, KM/GPS/Koordinatenpaar und Original-EXIF-Nachlesen. Aus Eintrag entfernen löst nur Bindung, Foto bleibt im BTB; bestehendes Entfernen aus BTB bleibt separat ohne Dateilöschung. Zusätzliche Fotozuordnungsfelder beim Eintrag entfallen; optionaler Standalone-Upload eingeklappt. Ungebundene Altfotos nicht anhand Text/GPS automatisch verbunden.
- Abschnitt/Firma/Unterbaustelle/Text folgen serverseitig bei Entry-/Firmenänderungen, Datei-/Linkversionen und FILE-Events in bestehender BTB-Transaktion. Entry löschen löst Bindungen, keine Bildlöschung. Upload sperrt Eltern-BTB vor Eintrag (Lockreihenfolge), vorhandener Teilnehmer-/Finalisierungsschutz bleibt. KM/GPS bleiben fotoeigene Angaben; globale Datei/andere BTB-Links unverändert.
- GPS-Overrides lat/lon endlich, Bereich/Paar geprüft, null löscht; effektive Koordinaten in BTB/Galerie/Snapshot/PDF verhindern Rückfall auf alte Basis-GPS. EXIF vor Canvas-Verkleinerung lokal lesen (max4MiB Header; TIFF/JPEG/PNG/WebP, begrenzte Counts/Offsets), GPS/Aufnahmezeit separat übertragen; neue Fotos weiterhin max2.000.000Bytes. Keine Dateibearbeitungszeit als Aufnahmezeit. Ohne EXIF-Offset wird Europe/Berlin angenommen; unbekannte Metadaten leer.
- Berichtszeit nur Arbeitstag und zwei Viertelstunden-Uhrzeiten. Endzeit<Beginn→Folgetag, Anzeige Portal/PDF18/19.10.26; Monats-/Jahreswechsel korrekt. Echte UTC-Zeitpunkte mit Europe/Berlin-Konvertierung; gleiche Uhrzeiten und nicht existente Zeitumstellungszeiten nicht still umdeuten. Bestehende >24h-Zeiträume geschützt. Personal/Maschinenvererbung und Wetter verwenden gespeicherten Zeitraum. PDF gebundene Fotos mit Bildreferenz am richtigen Eintrag statt zweitem künstlichen Text.
- Lokal tatsächlich PASS: neuer Check153 Python/Node (echte Produktionsfunktionen mit kontrollierten Cursor-/API-/DOM-Doubles; Referenzisolation, Versionskonflikt, GPS ändern/löschen/Snapshot, EXIF Endian/JPEG/PNG/WebP/defekte Daten, Koordinatenpaar/Maps, Nacht/Monat/Jahr, Bildreferenz); bestehende152/151 PASS. Check152 auf nun reine Enduhrzeit angepasst. Python-/JS-Syntax PASS. Zweitseitige PDF neu gerendert, Text/Nachtdatum/Zeit/Bildnummer/GPS geprüft und PNG-Sichtprüfung durchgeführt.
- Kein echter PostgreSQL-/FastAPI-/Chromium-/NAS-E2E dieses Blocks, keine Android-Abnahme/APK. app/** unverändert. CI-COST-001: [skip ci], keine teuren Actions gestartet; allgemeine bestehende CI-Restpunkte separat.
- Elf Runtime-Dateien, davon neu btb_entry_photos.py/v1_photo_exif.html. Genaue Liste/Praxistests: projektsteuerung/uebergaben/PORTAL-017_NAS_UEBERNAHME.md. API-Neustart+StrgF5 bei Quellordnereinbindung, sonst Image neu erstellen. Keine Migration/nichts löschen/.env/Compose/Storage/Backups erhalten. Voraussetzung PORTAL-016; bestätigter PORTAL-015-Baum/KM-Suche geschützt, alte fehlende Unterbaustellenmetadaten separat offen. Excel bekannter binärer Defekt unverändert, keine Ersatzhistorie. PR6 bleibt Draft, Android6cec344 unverändert.


### PORTAL-018 – PDF-Kopf umgesetzt / Kataloganbindung geprüft (18.10.2026)
- Explizite Schichtzeile aus server/tbserver/btb_pdf_android_style.py entfernt. Datum/Nachtzeitraum und Zeit bleiben. Gespeicherte Metadaten und Android unverändert.
- Produktionsrenderer lokal ausgeführt: zweitseitiges PDF, kein Schicht:, Datum18/19.10.26, Zeit19:00–07:00, Bildreferenz/GPS weiterhin geprüft; erste Seite als PNG gesichtet. Kein NAS-E2E/DB-Test, keine Actions gestartet.
- Ist-Prüfung: v1_worksite.html nodeRoute/nodeStation sind freie Textfelder, canonicalNodePayload speichert route_key/station_name ohne Katalogabfrage. worksite-Nodemutationen prüfen keine Katalogexistenz. survey_catalog.py /search sucht bereits case-insensitiv route_number/station_code/station_name und /availability liefert verfügbare Daten. Ob5501 bzw.MHO/Holzkirchen auf NAS vorhanden ist, wurde nicht live abgefragt.
- Vorschlag, noch NICHT implementiert: Autovervollständigung für Strecke und Bahnhof (Kürzel+Name), Datenverfügbarkeit anzeigen, unbekannte Eingabe deutlich kennzeichnen und manuell zulassen. Keine Zusage vollständiger bundesweiter Bahnhofsstammdaten allein aus Vermessungskatalog.
- NAS nur server/tbserver/btb_pdf_android_style.py nach /volume1/docker/tb-helfer-v1/tbserver/btb_pdf_android_style.py kopieren (vorhandene Quellordnerstruktur beachten), API neu starten und PDF neu erzeugen. Bereits gespeicherte PDFs bleiben alte Dateien. Keine Migration. Excel-Defekt bleibt unverändert.


### PORTAL-019 – Nutzerlöschen und Katalogsuche umgesetzt (18.10.2026)
- Korrektur früherer Auskunft: versionierter Nutzer-DELETE /worksites/{id} existiert bereits (RESPONSIBLE_TB, TRASHED/deleted_at, Tombstone, Changefeed, Audit), nur Portalbutton fehlte. Liste liefert additive version/can_delete; Button nur verantwortlicher aktiver TB, Bestätigung ohne Namenseingabe, bestehende API mit base_version. Keine permanente Admin-Purge-Freigabe für Nutzer, keine echten Baustellen im Test gelöscht.
- Name bei Erstellung Haupt-/Unterbaustelle optional. Server leitet Hauptnamen aus Bezeichnung, Bahnhof oder Strecke ab; Unterbaustelle aus Payloadname/Bahnhof/Strecke/Gleis. Fachlich leere Eingaben HTTP400. Bestehende benannte Datensätze bleiben gleich.
- /api/v1/catalog/lookup kombiniert vorhandenen allgemeinen Katalog, Bahnhofreferenzdateien (wie Android) und nur sichtbare survey_catalog-Suchergebnisse. Route/Kürzel/Name case-insensitiv, genaue Kürzel zuerst, Treffer auswählbar; Kürzel+Bahnhof synchron, Unterbaustelle speichert station_code zusätzlich im Payload. Anzeige Katalog vs vorhandene Vermessungsdaten. Keine Treffer sind kein Nachweis, dass eine Bahnstrecke nicht existiert. Fehlende Referenzdateien klar als Warnung, API-Fehler sichtbar, manuelle Eingabe bleibt möglich. Kein Hardcoding MHO/MIH. Referenzdateien >1MB über Contents-Connector ohne Inhalt geliefert; MHO-Bestand nicht als live getestet ausgeben.
- Produktionsteile lokal mit Check154 Python/Node geprüft: Namensableitung/Leereingabe, reale Listen-SQL-Platzhalter, Löschrollen/Fremdfirma/409/erfolgreicher Softdelete+Tombstone ohne physischen DELETE, Katalogzusammenführung mit Userweitergabe und Referenzfehler, UI Kürzel/Name übernehmen, veraltetes Kürzel/Antwort nach Reset, keine Treffer/API-Fehler, Löschabbruch/Bestätigung/Version. Kontrollierte DB/Auth/DOM-Doubles, kein echter PostgreSQL-/Browser-/NAS-E2E. Bestehender151 Baum/KM PASS und Python/JS Syntax PASS. CI-Budget: keine Actions gestartet, [skip ci]. Android unverändert. Bekannter Excel-Defekt unverändert.
- Fünf Runtime-Dateien, genaue NAS-Liste/Voraussetzungen siehe uebergaben/PORTAL-019_NAS_UEBERNAHME.md; nach Kopie API neu starten, StrgF5. Keine Migration, keine Daten löschen. Vorheriger PDF-Fix22005026 separat falls noch nicht übernommen. GitHub Desktop im Serverbranch Fetch/Pull.
