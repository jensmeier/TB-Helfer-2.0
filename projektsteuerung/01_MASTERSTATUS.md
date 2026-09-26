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
