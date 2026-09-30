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
