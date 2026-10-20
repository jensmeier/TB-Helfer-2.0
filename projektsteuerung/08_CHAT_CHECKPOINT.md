# TB-HELFER 2.0 – CHAT CHECKPOINT

Stand: 2026-09-27

Letzter gesicherter Arbeitsblock: **Projektsteuerung vereinheitlicht und verifiziert**.

- Zentrale: `projektsteuerung/`
- alte Top-Level-Ordner `projektstatus/`, `releases/`, `uebergaben/` entfernt
- branch-spezifische Projektregeln nach dem Umzug kontrolliert und auf denselben Einstieg korrigiert
- kein Android-/Server-Fachcode in diesem Schritt geändert
- Android-Fachstand vor Steuerungsumbau: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
- Server-Fachstand vor Steuerungsumbau: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
- Android 1.3.19 / 200069: Build/Install/E2E offen
- Server 1.0.0-dev: Migration 015 Runtime-E2E offen
- nächster gemeinsamer Technikpunkt: IMG-001 Bildtransport
- weiterer P0: Poco bekommt keine Daten

Neuer Chat: direkt `projektsteuerung/00_START_HIER.md` lesen und Live-HEADs prüfen.

## Arbeitsentscheidung / Testnachtrag 26.09.2026

- Delta-Wait-Kapazitätsteiltest praktisch: 255 getrennte Portal-Sessions, 255/255 erfolgreich, 0 Fehler.
- Testsession-Reste kontrolliert bereinigt; abschließend 0 aktive Python-Test-Sessions.
- Das ist **nicht** der vollständige 15×15-E2E.
- Neue verbindliche Reihenfolge: zuerst Haupt-TB-Handy ↔ Server/Portal, danach HELPER/Poco, danach weitere Fach-E2Es.
- Vollständigen Lasttest erst nach funktionaler Stabilität und nach geplantem 8-GB-RAM-Ausbau bzw. NAS-Wechsel erneut durchführen.
- Keine Performanceänderungen auf Verdacht.

## Gerätebefund NET-001 – Testbaustelle

- Hauptgerät: Testbaustelle angelegt.
- Sync scheitert unmittelbar mit `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Fachsync ist damit in diesem Lauf noch nicht geprüft.
- Nächster Schritt: öffentliche Domain/API-Erreichbarkeit und danach Geräte-DNS/Netz prüfen.
- Keine Codeänderung auf Verdacht.


## IMG-001 Quellaudit 26.09.2026

- Quellaudit abgeschlossen; kein Fachcode geändert.
- Android lädt BTB-/Mangel-/Abnahmefotos aktuell aus dem lokalen Originalpfad hoch.
- Server speichert empfangene Bytes unverändert; APP-Volloriginal auf NAS ist daher Folge des Android-Uploads.
- Server-Renditions existieren: `thumb` 320 px / `preview` 1280 px.
- Android-Dateiclient nutzt diese Renditions nicht und lädt fehlende Bilder über `/content`.
- Lokales Android-Thumbnail 512 px/Qualität 82 existiert bereits, ist aber nicht in den Online-Sync eingebunden.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.
- Detailnachweis: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.
- E2E-PRIO-001 bleibt maßgeblich: NET-001 → Hauptgerät↔Portal → HELPER/Poco → danach IMG-001-Implementierung.

## NET-001 Nachtest – Browser erreichbar
- Auf demselben Hauptgerät sind `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` im Browser erreichbar.
- Öffentliche DNS/API-Erreichbarkeit ist damit grundsätzlich vorhanden.
- Der vorherige App-Fehler kann transient oder app-/prozessspezifisch gewesen sein.
- Nächster Schritt: in der App denselben Sync mit `Neu laden` erneut auslösen und die genaue Ausgabe prüfen.
- Keine Codeänderung auf Verdacht.

## NET-001 Reproduktion – App-only DNS-Fehler
- Browser auf demselben Hauptgerät: Portal + API-Health erreichbar.
- App `Neu laden`: gleicher Fehler `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Damit allgemeine öffentliche DNS-/Cloudflare-Störung als alleinige Ursache ausgeschlossen.
- Nächster Schritt: Android-Netzpfad/Serverbasis/HTTP-Client prüfen; Server/DB nicht auf Verdacht ändern.

## NET-001 – Force-Stop + Mobilfunk ohne Änderung
- App vollständig gestoppt und neu gestartet: gleicher DNS-Fehler.
- WLAN aus, Mobilfunk aktiv: gleicher DNS-Fehler.
- WLAN-spezifische Ursache dadurch unwahrscheinlich.
- Nächster Schritt: Android `Privates DNS`, VPN und DNS-/Adblock-Filter prüfen.
- Keine App-Daten löschen, keine Neuaktivierung, keine Serveränderung auf Verdacht.

## Praxisnachtrag – Pixel synchronisiert, Poco Erstabgleich läuft
- Pixel hat inzwischen wieder mit dem NAS synchronisiert.
- Frühere App-DNS-Störung daher aktuell kein dauerhafter Blocker; Ursache weiterhin unbekannt.
- Poco-App gelöscht, neu installiert und neu aktiviert.
- Poco-Erstabgleich läuft nach >3 Minuten weiter.
- Nächster Schritt: anhand Android-Quellpfad klären, was beim Erstabgleich alles geladen wird und welche Phase die lange Laufzeit verursacht; keine weiteren Resets auf Verdacht.

## BTB-SYNC-001 – 26.09 fehlt/verschwindet
- Poco-Erstabgleich abgeschlossen; Baustelle herunterladen erfolgreich.
- Pixel: neuer BTB 26.09 angelegt.
- Portal: zeigt weiterhin BTBs 23./24./25.09, 26.09 fehlt.
- Poco: 26.09 erscheint kurz, dann „kein BTB vorhanden“.
- Nächster Schritt: zuerst Serverzustand/DB/API/Change-Feed prüfen, danach Android-Snapshot/Active-Pointer. Keine Änderung auf Verdacht.

## BTB-SYNC-001 Diagnose – Pixel 1.3.4 vs Poco 1.3.19
- Server-Geräteabfrage zeigt:
  - Pixel / zinkel.jens: TB-Helfer 1.3.4
  - Poco / poco: TB-Helfer 1.3.19
- Change-Feed zeigt für denselben BTB am 26.09 mehrere UPDATEs bis mindestens Version 19.
- Aktueller Android-Zielstand laut Projektsteuerung: 1.3.19 / 200069.
- Nächster Schritt: Pixel-Version angleichen bzw. 200069-Installation nachweisen; erst danach BTB-SYNC-001 erneut testen.

## Korrektur – Pixel-Version
- Benutzer bestätigt Pixel ebenfalls auf TB-Helfer 1.3.19.
- Serverwert 1.3.4 ist stale: `app_version/app_build` werden nur bei Geräteaktivierung geschrieben; normale Requests aktualisieren nur `last_seen_at`.
- Vorherige Schlussfolgerung „gemischte Android-Versionen“ verworfen.
- BTB-SYNC-001 weiter diagnostizieren: 26.09-BTB + Team-`active_btb_id` + API-Snapshot.

## BTB-SYNC-001 – Servereingang 26.09 bestätigt
- BTB-ID: `ba2144b7-9ede-4f53-abdc-1accd172e875`
- work_date: 2026-09-26
- Change-Feed: wiederholte UPDATEs, sichtbar mindestens Version 7–19.
- Pixel→Server ist damit nicht der fehlende Schritt.
- Nächster Diagnosepunkt: Team/`active_btb_id`/Status/Teilnehmer für exakt diesen BTB, danach Portal- und Poco-Leseweg.

## BTB-SYNC-001 – Server-Lifecycle korrekt

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

## BTB-SYNC-001 – Portal nach Reload korrekt
- Portal zeigt nach vollständigem Reload Team `pixeljens`, aktiven BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`, Status ACTIVE, Datum 26.09.
- Pixel→Server→Portal ist für diesen BTB damit praktisch bestätigt.
- Restfehler ist Android/Poco: BTB kurz sichtbar, danach kein aktiver BTB.
- Nächster Schritt: Android Snapshot-/Active-Pointer-/UI-Pfad diagnostizieren.

## BTB-SYNC-001 – Poco erneut reproduziert nach Portal-Bestätigung
- Portal 26.09 ACTIVE bestätigt.
- Poco zeigt 26.09 kurz und verliert ihn unmittelbar wieder.
- Restursache ist damit auf Android/Poco eingegrenzt.
- Nächster Schritt: Active-Pointer/Marker/UI-Pfad im aktuellen Android-HEAD analysieren.

## BTB-SYNC-001 – Source-Ursache Active-Pointer
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.
- Nächster Zuständigkeitswechsel: ANDROID muss `BtbActiveContext` korrigieren und danach Build/Install/Poco-E2E durchführen.
- SERVER-Seite bleibt unverändert; Server-Lifecycle/Portal sind bestätigt.


## BTB-SYNC-001 – Android-Fix umgesetzt
- Vertrags-/Diagnosestand `e71585cb21fa13c01f184036337d3ea68725f37e` vor Implementierung in Android-Projektsteuerung übernommen.
- Android-Codefix: `f57c532863d2f92b81729b9af1466b57a22f8723`.
- `BtbActiveContext` respektiert `BtbServerStateStore.active=true` vor lokalen PDF-/Baseline-/Abschlusszuständen.
- Pointer wird in diesem servergebundenen Leseweg erst bei `FINALIZED && !active` gelöst; lokale/Legacy-Logik gilt nur ohne Server-Snapshot.
- Keine Server-/NAS-/Poco-Resetänderung.
- Version bleibt 1.3.19 / 200069.
- Build: **OFFEN**. GitHub Actions erzeugte für den aktuellen HEAD keinen Run; kein Buildnachweis.
- Nächster Nutzer-/Geräteblock: Android Studio bauen, APK auf Pixel + Poco installieren, denselben BTB 26.09 erneut prüfen.


## REL-001 – Version hochgezählt
- Benutzerhinweis bestätigt: erneute Verwendung von 1.3.19 / 200069 für eine neue APK würde den Updatepfad nicht sauber unterscheiden.
- Verbindlicher nächster Android-Zielstand: **1.3.20 / 200070**.
- 1.3.19 / 200069 bleibt historischer vorheriger Stand.
- Erst nach Dokumentationscommit werden Builddateien/Release-MD angepasst.


## 1.3.20 / 200070 vorbereitet
- Benutzerhinweis zur wiederverwendeten Buildnummer umgesetzt.
- Dokumentationsentscheidung: `32f57241a9717a6748d427af53eae3a593ad656b`.
- Build-/Release-Quelländerung: `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- Aktueller Android-Zielstand ist **1.3.20 / 200070**.
- Release-MD liegt unter `projektsteuerung/releases/TB-Helfer_1.3.20_200070.md`.
- Build/Installation/E2E weiterhin offen.


## UPD-001 – Praxisbefund nach Update
- Benutzer meldet: Updatevorgang durchgelaufen.
- Danach zeigt „Nach Update suchen“ weiterhin installierte Version 1.3.19.
- Hauptansicht zeigt weiterhin „Update verfügbar“.
- Zielrelease ist 1.3.20 / 200070.
- Fehler zuerst dokumentiert; nächster Schritt ist Quellprüfung der Updateanzeige und Vergleichslogik.
- Keine Neuaktivierung, kein Löschen von App-Daten, kein Server-/NAS-Umbau auf Verdacht.


## UPD-001 – Quellprüfung abgeschlossen
- `currentVersionLabel()` zeigt `BuildConfig.VERSION_NAME/VERSION_CODE`; daher ist 1.3.19 nach Installer kein stale UI-Wert.
- Startmenü prüft Update beim Resume erneut, Update-HTTP nutzt `useCaches=false`.
- Serverrelease kann aktuell eine Binärdatei erhalten, deren internes APK-`versionName/versionCode` nicht zu den Releasefeldern passt.
- Android prüft aktuell nur SHA-256 und Dateigröße.
- Schutzfix: APK-Package + eingebettete Version/Build vor Installer validieren.
- 200070 ist bereits benutzt; nächster auslieferbarer Build: **1.3.21 / 200071**.


## UPD-001 – Android-Schutzfix
- Commit `19d688a4752c98209570e0ff69ccd2eae789541e`.
- Version **1.3.21 / 200071**.
- heruntergeladene APK wird vor Installer auf Package-ID + eingebettete Version/Build geprüft.
- falsches/altes APK wird verworfen statt installiert.
- Server-Releaseverwaltung sollte denselben Abgleich später zusätzlich serverseitig erzwingen; kein Servercode in diesem Android-Block geändert.
- Build/Installation/Update-E2E offen.


## REL-FILE-001 – dauerhaft merken
- Benutzeranforderung: Bei jedem neuen Android-Build/GitHub-Übergabestand eine Release-MD im bekannten TB-HELFER-RELEASE-Format mitführen und zusätzlich als Download bereitstellen.
- Referenzformat bestätigt durch die bereitgestellten Dateien 1.3.16/200066, 1.3.17/200067 und 1.3.18/200068.
- Die Datei enthält die Release-Metadaten für den Server-Draft, ersetzt aber nicht die APK-interne Version.
- Release-MD + APK müssen immer aus exakt demselben Build stammen.


## REL-FILE-001 – CI abgesichert
- Dauerregel bereits in Projektregeln/Projektsteuerung gesichert.
- CI-Commit: `e76933cd7270da21d8cd251ece1cba87c7db0cf8`.
- CI prüft passende Release-MD gegen Android-`versionName/versionCode`.
- CI-Artefakt enthält automatisch APK + passende Release-MD.
- Zusätzlich wird die jeweilige Release-MD im Chat als Download bereitgestellt.


## UPD-001 – Praxis bestätigt
- Benutzer bestätigt den Updateweg **1.3.21 / 200071** als funktionierend.
- Build und Installation sind damit praktisch nachgewiesen.
- Release-MD/APK-Paarung funktioniert.
- UPD-001: **BESTÄTIGT**.
- Nächster Arbeitsblock: BTB-SYNC-001 mit Pixel und Poco auf dem aktuellen Build erneut testen.


## BTB-SYNC-001 – 1.3.21 E2E fehlgeschlagen
- Update auf 1.3.21 / 200071 funktioniert und ist bestätigt.
- Fach-E2E bleibt jedoch fehlerhaft:
  - Pixel Haupt-TB: `BTB Abschluss` flackert weiterhin.
  - Poco HELPER: 26.09 erscheint kurz und verschwindet sofort wieder.
- Damit ist `f57c532...` nicht ausreichend.
- Nächster Android-Diagnoseblock: `BtbServerStateStore` Persistenz/Keys, lokale/serverseitige BTB-ID-Zuordnung, Pointer-Schreiber und Lifecycle-Watcher.
- Keine App-Daten löschen, keine Neuaktivierung, kein NAS-/Server-Umbau.


## BTB-SYNC-001 – zweiter konkurrierender Guard gefunden
- 1.3.21-Gerätetest blieb fehlerhaft.
- Quellprüfung hat den bisher übersehenen `BtbPatch200033Installer.startActivePointerGuard()` bestätigt.
- Guard läuft alle 450 ms und löste bei stale Inaktiv-Marker den Pointer, ohne den ServerState zu berücksichtigen.
- Parallel überschreibt Patch200033 aufgrund bisheriger Installationsreihenfolge den modernen Abschlusslistener.
- Fixcommit: `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`.
- Neuer Zielstand: **1.3.22 / 200072**.
- Source-Check passt; Build/Installation/Praxis-E2E offen.
- Nichts am Poco löschen, nicht neu aktivieren, nichts am NAS ändern.


## BTB-SYNC-002 – 1.3.23 Source-Fix gesichert
- Benutzerkorrektur übernommen: Ursache ist Android-Reihenfolge, nicht Server/DB-Vertrag.
- P0-Fehler: lokaler Pixel-Stand konnte vor Outbox-Export durch Server-Snapshot überschrieben werden.
- Projektsteuerung vor Codeänderung aktualisiert/committed.
- Android-Fix:
  - Dirty-BTB zuerst `exportBtbChange`;
  - kein Server-Snapshot-Apply vor Outbox;
  - Pending/Dirty sperrt `applyServerStateToLocal`;
  - lokaler Save + Dirty-Markierung und Server-Apply pro BTB serialisiert;
  - neue Dirty-Generation während Upload bleibt erhalten und erzwingt Retry.
- Version angehoben auf **1.3.23 / 200073**.
- Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.23_200073.md`.
- Kein `server/**` geändert.
- PORTAL-LIVE-001 bleibt SERVER-seitig: Source-Fix vorhanden, Deploy/E2E offen; bis Deployment kann F5 auf dem NAS weiter nötig sein.
- Build konnte in diesem Arbeitsblock nicht ausgeführt werden: GitHub Actions erzeugte für die aktuellen PR-Commits keinen Run; Container hat keinen externen GitHub-Netzzugriff.
- Daher Status sauber getrennt: **SOURCE-FIX UMGESETZT / BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN**.
- Nächster Test nach Build: Pixel `ABC` → Portal `ABC` → Portal `XYZ` → Pixel `XYZ`; Parallel-Autosave darf nicht verschwinden. Danach Poco.


## BTB-SYNC-002 – bidirektionaler Basistest 1.3.23
- Benutzer bestätigt den gerade getesteten BTB-Sync: **funktioniert in beide Richtungen**.
- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Damit ist BTB-SYNC-002 für den normalen bidirektionalen Pfad praktisch bestätigt.
- Noch nicht als bestätigt markieren: gezielter Parallel-Autosave-/Conflict-Fall während laufendem Upload.
- Poco/HELPER bleibt separater E2E.
- Server/API/DB unverändert; PORTAL-LIVE-001 bleibt weiterhin eigener SERVER-Deploy/E2E-Punkt.


## BTB-UI-001 – neuer Praxisbefund
- Pixel ist Haupt-TB; Poco/Helfer ist aus der aktiven Helferrolle entlassen.
- Screenshot zeigt Wechsel zwischen:
  - nur `BTB Abschluss`;
  - `PDF Entwurf` + `BTB Abschluss`.
- Gewünschter Haupt-TB-Zustand ist stabil **PDF Entwurf + BTB Abschluss**.
- Quellursache im `BtbModernLifecycleInstaller` nachvollziehbar: pauschal beide Buttons VISIBLE, danach Haupt-TB-Zweig `btnPdf=GONE`.
- Vor Codeänderung dokumentiert. Nächster Zielbuild 1.3.24 / 200074.


## BTB-UI-001 – Source-Fix gesichert
- Praxisbefund: Pixel Haupt-TB wechselte unten zwischen 1 und 2 Buttons.
- Quellursache: Lifecycle-Watcher setzte zunächst beide Buttons sichtbar und blendete im Haupt-TB-Zweig `PDF Entwurf` wieder aus.
- Fixcommit `7fdb4dad2e72515dcda958c00bc2065c38d9f5da`.
- Versions-/Releasecommit `446312c8c09a99ed71950fe3b08adc53070ddc91`.
- Neuer Zielstand **1.3.24 / 200074**.
- Haupt-TB: stabil `PDF Entwurf` + `BTB Abschluss`.
- HELPER: kein PDF-Entwurf; rollenbezogene Schichtaktion.
- Source-Check passt; Build/Installation/E2E offen.


## BTB-UI-001 bestätigt 1.3.24
- Benutzer bestätigt: Pixel springt unten nicht mehr.
- Haupt-TB zeigt stabil PDF Entwurf + BTB Abschluss.
- Punkt geschützt: nur bei neuem reproduzierbaren Fehler wieder öffnen.
- Poco/HELPER-E2E bleibt offen.


## BTB-SYNC-002 – vollständig bestätigt
- Benutzer bestätigt auch den zuvor noch offenen Parallel-Autosave-/Conflict-Test.
- Pixel → Portal: BESTANDEN.
- Portal → Pixel: BESTANDEN.
- neuerer lokaler Pixel-Stand während laufendem Upload bleibt erhalten: BESTANDEN.
- BTB-SYNC-002 ist damit **BESTÄTIGT** und geschützt.
- Nächster Geräteblock: Poco/HELPER-E2E.


## BTB-EDIT-001 – Diagnosecheckpoint
- Anforderung vor Implementierung in Projektsteuerung gesichert: `fb790f782e9097314d6c5f41d75c10c3bdbb1c6b`.
- Quellaudit bestätigt: `@ORT=<Long>`/Foto-`ortId` sind lokale Identitäten; Punkt 4–8 verlieren den Ort beim aktuellen `SectionItem`-Build.
- stabiles Worksite-Node-Binding mit `clientNodeKey` existiert bereits.
- stabiles Entry-Slot-Konzept existiert bereits über `BtbEntrySlotStore`.
- Android-Zielmodell festgelegt: `clientEntryKey + firmaId/firmaName + worksiteNodeKey + Zeit/KM/Text`.
- Server-HEAD `cd9b18fc062a7a3d4354c611a949ed3d8408d709` hat den zuvor fehlenden section-PATCH und BTB-Foto-Metadaten-PATCH inzwischen implementiert.
- Nächster Block: Android-Modell/Serializer/Server1BtbApi/Editor/Foto-Metadaten gezielt anpassen; kein Delete/Create.


## PORTAL-WEATHER-001 – Diagnosecheckpoint
- Android-Serializer trägt `tempC` und `niederschlagMm`.
- konkreter Android-Bug: Auto-Wetter macht aus bekanntem `0.0 mm` ein `null`.
- tempC-Verlust im Portal ist noch nicht source-seitig lokalisiert.
- Wetter bleibt separater Block, nicht Teil von BTB-EDIT-001.


## Buildauftrag 1.3.25 / 200075
- Benutzer: **bauen**.
- Neuer Build muss höher als 200074 sein; daher **1.3.25 / 200075**.
- Vor Build Source-Check der BTB-EDIT-001-Änderungen.
- Release-MD zwingend gemeinsam mit APK.
- Noch kein Buildstatus behaupten, bis CI/Gradle tatsächlich erfolgreich durchgelaufen ist.


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

## Checkpoint 27.09.2026 – Integrationsaudit Bilder / BTB-Abschluss / Wetter
- Neue Anforderungen vor Prüfung in 02/05/07 gesichert.
- Foto-Ist: Android lädt lokales Original hoch; Server speichert es unverändert. Server hat `preview` 1280/JPEG82 und `thumb` 320/JPEG72; Android lädt bislang `/content`. Android-Fix nötig.
- BTB-Finalisierung: Android und Portal benutzen denselben Server-Endpunkt. Server FINALIZED + active_btb_id=NULL + BTB/FILE-Change; Portal SSE und Android Delta übernehmen. Source-Pfad beidseitig passend, gezielter Finalize-E2E offen.
- Wetter: Android/Server/Portal führen `tempC` und `niederschlagMm`. Portal zeigt 0 mm korrekt. Android macht automatisches 0.0 mm an zwei Stellen zu null und zeigt mm in UI/Android-PDF nicht vollständig. Android-Fix nötig.
- Kein `app/**`- oder `server/**`-Code in diesem Audit geändert.
- Android Build 1.3.25/200075 bleibt **OFFEN**.

## Checkpoint 27.09.2026 – Android 1.3.26 / 200076
- Anforderungen Bilder/BTB-Abschluss/Wetter vor Implementierung in Projektsteuerung gesichert.
- Android-Fachcode umgesetzt:
  - kleine Sync-JPEGs max. 1920 px / Qualität 82; Handyoriginal unverändert;
  - BTB-, Mängel-, Abnahmebilder + bildbasierte Abnahme-Anlagen klein zum Server;
  - mobile Serverbilder über `preview`, nicht automatisches `/content`;
  - BTB-Foto-Link-Metadaten per versioniertem PATCH;
  - 0,0-mm-Wetterwert erhalten und mm in App/PDF/Druckmodell sichtbar.
- BTB-Finalize-Lifecycle **nicht verändert**; Android/Portal verwenden weiterhin denselben Server-Endpunkt.
- Version: **1.3.26 / 200076**, Release-MD vorhanden.
- Sourcechecks: gezielte Prüfungen PASS; Generatoranker 14/14 PASS; Diff ohne `server/**`.
- Fachcode-HEAD vor Statuscommits: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`.
- Build: **OFFEN**; kein Workflow-Run für diesen Code-HEAD, lokaler Checkout/Gradle hier nicht verfügbar.
- ADB/Emulator: nicht verfügbar; kein realer Android-Test.
- Nächster praktischer Schritt: Git Pull → Android Studio Build 200076 → Pixel → E2E Foto/Wetter/Finalize → Poco.


## Abschluss Arbeitsblock 27.09.2026 – 1.3.26 / 200076
- Android-Fachcode für kleinen Bildsync, mobile preview-Renditions, BTB-Foto-PATCH und Wetter-mm umgesetzt.
- Kleine Bilduploads gelten jetzt auch für Mängel-/Abnahmefotos und bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen bleiben unverändert.
- BTB-Finalize-Lifecycle wurde nicht geändert.
- Quellprüfung: gezielte Sourcechecks PASS; Generatoranker 14/14 PASS; Diff ohne `server/**`.
- Fachcode-HEAD: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`.
- Projektsteuerungs-Kopfstände 01/03/06/07 auf 27.09. und Zielbuild 1.3.26/200076 bereinigt; alte bestätigte Punkte werden nicht mehr als offen geführt.
- PORTAL-LIVE-001, BTB-SYNC-002, BTB-UI-001 und UPD-001 bleiben **BESTÄTIGT/geschützt**.
- Build 200076: **OFFEN**. Kein Workflow-Run für den Fachcode-HEAD; in dieser Chatumgebung kein lokaler Git-Checkout/Gradle und kein ADB/Emulator.
- Installation: **OFFEN**.
- E2E: **OFFEN** für Bildgrößen/Renditions, Foto-PATCH, Wetter 0,0 mm/tempC, Finalize beide Richtungen und Poco/HELPER.
- Nächster praktischer Schritt: Git Pull → Android Studio Build 200076 → Pixel installieren → E2E-Matrix → Poco.

## Checkpoint 27.09.2026 – Build 200076 Locale-Fix
- Benutzer-Build in Android Studio: `:app:compileDebugKotlin` fehlgeschlagen, `Unresolved reference 'Locale'`.
- Ursache: `BautagebuchFragment.kt` nutzte `Locale.GERMANY` ohne `java.util.Locale`-Import.
- Neue Fehlerfeststellung vor Codeänderung in Projektsteuerung gesichert.
- Fixcommit: `199df4b149e210194b22b1c84cc1700ac54435c2`.
- Codeänderung: genau ein Import; keine Fachlogik, kein Server, kein Finalize-/Sync-Umbau.
- Generatoranker nach Fix 14/14 PASS.
- Version bleibt **1.3.26 / 200076**.
- Buildstatus: **ERNEUT OFFEN**; keine Installation/E2E-Bestätigung.
- Nächster Schritt lokal: Git Pull → Build 200076 erneut.


## Checkpoint 27.09.2026 – AND-VER-001 korrigiert
- Die sichtbare Versions-/Buildzeile war eine nicht beauftragte Assistentenänderung.
- Benutzer hat ausdrücklich entschieden, dass diese Anzeige **nicht** gewünscht ist.
- Startseitenzeile und die damit verbundene Formatänderung wurden vollständig zurückgenommen.
- Technische Version bleibt **1.3.26 / 200076**; Build 200076 bleibt nach Locale-Fix erneut zu bauen.


## Checkpoint 27.09.2026 – Branchbereinigung
- Benutzerziel: Branchliste auf `main`, `feature/tb-android-2.0`, `feature/tb-server-1.0` reduzieren.
- Sicherheitsvergleich gegen aktive Android-/Serverbranches durchgeführt.
- PR #1 und #5 geschlossen.
- Server-Draft-PR #6 auf Basis `main` umgestellt; bleibt offen.
- Altbranches sind fachlich überholt; `feature/device-activation` und `kilometrierung-v1` haben eigene historische Commits, ihre Funktionen sind im aktuellen Projektstand weiterentwickelt/ersetzt.
- Branch-Refs konnten über die verfügbare GitHub-Schnittstelle nicht direkt gelöscht werden; manuelle Löschung der sieben dokumentierten Altbranches bleibt offen.
- Keine Android-/Server-Fachcodeänderung.


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
