# TB-HELFER 2.0 – CHAT CHECKPOINT

Stand: 2026-09-26

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


## IMG-PORTAL-001 – 4 Serverbilder, 1 Portalbild
- Neuer Befund am aktiven BTB 26.09.
- Server/NAS: 4 zugehörig wirkende Bilder.
- Portal: nur 1 Bild sichtbar.
- Nächster Schritt: Attachment-Zuordnung/Metadaten und BTB-API prüfen; danach erst Portal-Renderpfad.


## PORTAL-LIVE-001 – F5 nicht akzeptabel
- Nach manuellem Browser-Reload (F5) erscheinen die zuvor fehlenden BTB-Bilder vollständig im Portal.
- Damit ist die Bildzuordnung/Serverablage grundsätzlich vorhanden; der aktuelle Praxisfehler ist **fehlende Live-Aktualisierung der offenen Portalansicht**.
- Benutzeranforderung: Neue BTBs/Bilder/Fachdaten sollen in einer offenen Portalansicht automatisch sichtbar werden; F5 darf für normalen Betrieb nicht nötig sein.
- Bestehende Architekturregel gilt: offene Portalansichten dürfen live aktualisieren, aber nicht über 2-Sekunden-DB-Polling pro Browser. Vorhandener Change-Feed/SSE-/Long-Poll-Pfad ist zu verwenden.
- Nächster Server-Arbeitsblock: bestehende Portal-Templates und `/api/v1/sync/events`/`changes/wait` prüfen und gezielten Live-Refresh implementieren.


## PORTAL-LIVE-001 – Source-Fix gesichert
- Source-Fix Commit: `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- `v1_btb.html` und `v1_worksite.html` abonnieren jetzt den vorhandenen worksite-bezogenen SSE-Change-Feed `/api/v1/sync/events`.
- Relevante Änderungen werden entprellt und gezielt nachgeladen; auf der BTB-Seite insbesondere `BTB`, `BTB_ENTRY`, `BTB_PARTICIPANT` und `FILE`.
- Baustellenseite aktualisiert Teams/BTBs, Medien, Mängel, Abnahmen, Mitglieder und Baum abhängig vom Change-Typ.
- Kein 2-Sekunden-Polling; vorhandener gemeinsamer Change-Hub wird verwendet.
- Source-Assertions für EventSource/SSE-Pfad und gezielte Reload-Funktionen: **PASS**.
- Status bleibt: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.
- Deployment nach Synology-Regel: GitHub Desktop → File Station → Container Manager.
- Danach E2E mit offenem Portal und neuem Handyfoto; F5 darf nicht mehr nötig sein.


## BTB-SYNC-002 – asymmetrischer Sync / lokale Eingabe verloren
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
- Nächster Schritt: aktuellen Android- und Server-Quellpfad diagnostizieren, bevor irgendein Fix gebaut wird.


## BTB-SYNC-002 – Source-Ursache bestätigt
- **Source-Ursache gefunden im aktuellen Android-HEAD `ef7077923e2917ac615b9f3b5143ac2638fb92ac`:**
  - `BautagebuchSpeicher.speichern()` speichert die Pixel-Änderung lokal und markiert den BTB nur im `SelectiveSyncStore` als dirty.
  - `SelectiveSyncWorker` lädt bei bereits bekanntem Server-BTB **zuerst** `loadBtbAccess(...)`, speichert den Server-Snapshot und ruft **vor** `exportBtbChange(...)` bereits `bridge.applyServerStateToLocal(...)` auf.
  - Zu diesem Zeitpunkt existiert noch keine `collabPending`-Mutation für die neue Pixel-Änderung; der Guard in `applyServerStateToLocal()` kann sie daher nicht schützen.
  - Ergebnis: der ältere/andere Serverstand überschreibt die lokal gespeicherte Pixel-Änderung, erst danach wird exportiert. Damit wird der überschriebenen Serverstand statt der Pixel-Änderung hochgeladen.
- Das erklärt exakt den Praxisbefund: Pixel→Portal fehlt; Portal→Pixel kommt an und entfernt die Pixel-Eingabe.
- Server/API/DB sind für diesen Fehler nicht zu ändern.
- Nächster Arbeitsblock gehört in ANDROID: SelectiveSyncWorker-Reihenfolge korrigieren, anschließend Build/Install und denselben 26.09-BTB mit Pixel↔Portal erneut testen.
- SERVER bleibt unverändert.


## PORTAL-LIVE-001 – nächster Serverblock 26.09.2026
- Benutzer bestätigt: Pixel↔Portal läuft aktuell in beide Richtungen.
- Jetzt gewünscht: Portal automatisch aktualisieren, damit F5 nicht mehr nötig ist.
- Source-Fix existiert bereits im Server-Branch, Commit `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- Kein neuer Fachcode nötig, bevor dieser Stand deployed und praktisch getestet wurde.
- Nach Deploy E2E: Portal geöffnet lassen → Pixel-Text oder Foto ändern → Portal muss ohne F5 nachziehen.


## PORTAL-LIVE-001 – Praxis bestätigt / Portal-BTB offen
- Benutzer bestätigt: kein F5 mehr nötig.
- Änderungen erscheinen nach ca. 1–2 Sekunden.
- Erstellen und Löschen werden automatisch übernommen.
- Funktioniert in beide Richtungen.
- PORTAL-LIVE-001 damit praktisch **BESTÄTIGT**.
- Separat offen: `PORTAL-BTB-UI-001` – BTB im Portal muss noch nachgearbeitet werden.
- Keine Portal-BTB-Nacharbeit auf Verdacht; konkreten Änderungsumfang im nächsten Arbeitsblock festlegen.


## BTB-EDIT-001 – Strukturvergleich gesichert
- Benutzer fordert vollständige Nachbearbeitung von BTB-Einträgen/Fotos: falsche Firma, falscher BÜ/Unterbaustelle, falscher Punkt 4–8 usw. muss korrigierbar sein.
- Android zeigt bereits eine hierarchische Unterbaustellen-Auswahl.
- Quellaudit: Android verwendet dort aktuell lokale `@ORT=<Long>`-Marker; diese sind nicht geräteübergreifend stabil.
- Bestehender Worksite-Struktur-Sync besitzt bereits stabile `clientNodeKey`-Bindings; BTB muss darauf aufsetzen.
- Portal-BTB hat aktuell keine Unterbaustellenauswahl; Entry-Editor ändert im Wesentlichen Zeit/KM/Text.
- Server-Entry-PATCH kann `section` aktuell nicht ändern.
- Bestehende Fotos können serverseitig aktuell nicht über einen allgemeinen Metadaten-PATCH fachlich umgehängt werden.
- Noch nichts gebaut.
- Nebenbefunde aus Benutzerannotation: aktuelle Ansicht soll gelöschte Helfer nicht unnötig zeigen; Wettertemperaturen fehlen im Praxisfall; 0-Niederschlag soll nicht als unbekannt/null verschwinden; GPS an allen Fotos aus Handy/Portal erhalten.


## BTB-EDIT-001 – Server-Quellblock fertig
- Benutzerauftrag: weiterbauen.
- Serververtrag vor Codeänderung committed.
- neue Migration: `server/v1_migrations/016_btb_photo_link_metadata.sql`.
- Server-API:
  - Entry `section` 4–8 PATCH bei gleicher ID;
  - `worksiteNodeKey`-Validierung;
  - BTB-Foto-PATCH mit `base_version` und linkbezogenen Metadaten.
- Portal:
  - gleicher Worksite-Node-Baum wie Android;
  - Firma/Allgemein + Unterbaustelle + Punkt 4–8 editierbar;
  - Zeit/KM/Text editierbar;
  - Foto-Zuordnung editierbar;
  - Android-nahe Unterbaustellenlabels;
  - Live-SSE unverändert.
- Quellprüfung 94–100 + Portal-JS-Syntax: PASS.
- Deploy/Migration016/E2E noch offen.
- Android parallel weiter: HEAD `623704990072c7006e6a48e611441d7aa08b989e`; Entry-/Node-Modell in Umsetzung, Foto-Pfad noch offen.
- Nächster Server-Schritt nach Android-Kompatibilitätsstand: Synology deployen, Migration016 prüfen, dann gemeinsamer E2E.


## INT-001 – parallele Branches aktuell nicht mergebar
- Nach BTB-EDIT-001 Serverbau Live-Prüfung von PR #7: `mergeable=false`.
- Branchvergleich: Android und Server stark divergiert; gemeinsame Projektsteuerungsdateien wurden parallel geändert.
- Android arbeitet aktuell weiter; deshalb **jetzt kein Merge/Rebase**.
- Android-HEAD bei Feststellung: `7c7cf7b502ec1f701f167c13780b10a014bba6d9`.
- Dieser Commit ergänzt lokale stabile Foto-Zuordnungsdaten (`firmaId`, `worksiteNodeKey`, `bildtyp`).
- Nächster Integrationsschritt nach Android-Abschluss: SERVER führt Branches gezielt zusammen und löst gemeinsame Status-/Vertragskonflikte bewusst.
- Neuer Serverstand bleibt bis dahin **QUELLSTAND FERTIG / NICHT DEPLOYT**.


## INT-001 – Android fertig, Integration freigegeben
- Benutzer bestätigt am 26.09.2026: **Android ist fertig**.
- Live-GitHub unmittelbar davor:
  - SERVER HEAD `18618bba0286e2a4137be75071efcfd17da34dc1`;
  - ANDROID HEAD `a1b4667b110a993796e8242d4a21be3748c06d68`;
  - PR #7 weiterhin `mergeable=false` gegen `feature/tb-server-1.0`.
- Android-Build 1.3.25/200075 ist nicht als erfolgreich belegt; CI hatte keinen Runner und keine Steps.
- SERVER darf jetzt den zuvor zurückgestellten Branch-Abgleich `INT-001` durchführen.


## AND-BUILD-200075 – Integrationsstopp wegen neuem Android-Status
- Während der vorbereiteten 3-Wege-Integration sprang Android-HEAD von `a1b4667b...` auf `57c960fc...`.
- Neuer Commit dokumentiert echten lokalen Buildfehler in `generateBtb200043Sources`.
- Kein Branch wurde bis dahin verschoben; nur unverknüpfte Git-Blobs aus der Mergevorbereitung entstanden.
- Fehler ist Android-Buildgenerator, nicht Server/API/DB.
- SERVER integriert erst den stabilen neuesten Android-HEAD und lässt den Generator-Fix bei ANDROID.


## AND-BUILD-200075 – Android Generatorfix nachgereicht
- Android-Commit `606aa3ad1f8ea438372484b056a0fc1264ae2aac` behebt den unmittelbar zuvor dokumentierten Buildgenerator-Ankerfehler.
- Kein Server/API/DB-Fix erforderlich.
- Build 200075 muss nach diesem Fix erneut tatsächlich ausgeführt werden.
- Branch-Integration bleibt bis zum stabilen finalen Android-HEAD angehalten; noch kein Ref wurde verschoben.


## INT-001 – Branch-Integration abgeschlossen
- Benutzer hat den Android-Arbeitsblock als fertig bestätigt.
- Integrierte Elternstände:
  - SERVER `c7358cd223cbd3575146fc98a3477fc6993501d2`
  - ANDROID `4de3350fa4bc3777a6f2f51514e85a145ca4a9c6`
- Generatorfix 606aa3ad ist Bestandteil des Android-Parents.
- `server/**` und `app/**` wurden jeweils unverändert aus ihrem zuständigen Parent übernommen.
- Build-Retest 1.3.25/200075 bleibt offen.
- Android-Foto-PATCH/`link_version` bleibt offener BTB-EDIT-Restpunkt.
- Server Migration016/Deploy und gemeinsamer E2E bleiben offen.


## INT-001 – Integration abgeschlossen, Deploy als Nächstes
- SERVER-Branch auf Merge-Commit `ee8358e0cb403347a17d389dcad562ac6858aff7` gesetzt.
- Android-Parent `4de3350fa4bc3777a6f2f51514e85a145ca4a9c6` ist im Server-HEAD enthalten.
- PR #7 jetzt merged/closed.
- Verifikation:
  - `server/**` exakt Server-Parent;
  - `app/**` exakt Android-Parent;
  - keine alten Top-Level-Ordner zurückgekehrt.
- GitHub Actions auf Merge-HEAD: Server + Android jeweils failure ohne Steps; kein Testnachweis.
- Nächster praktischer Block: Synology-Deploy, Migration016, Runtime-/Portal-Smoke.


## BTB-EDIT-001 – Upload auf NAS bestätigt
- Benutzer: „dateinoben“.
- Gewertet als: benötigte Server-/Portal-/Migration016-Dateien wurden auf die Synology hochgeladen.
- Noch nicht gewertet als deployed/E2E.
- Nächster Schritt: `migrate` erneut ausführen und Migration 016 bestätigen; danach API/Worker neu starten und Portal-Smoke.


## BTB-EDIT-001 – Migrationskommando OK
- Benutzer-Screenshot: API-Container, `python -m tbserver.migrations up` → `OK`.
- Kein sichtbares `APPLIED 016_btb_photo_link_metadata.sql`.
- Deshalb keine voreilige 016-Bestätigung.
- Nächster Schritt: Migrationsstatus prüfen; erst bei `latest_known=16`, `latest_applied=16`, `pending=[]` API/Worker neu starten.


## BTB-EDIT-001 – Migration 016 angewendet
- Benutzer-Screenshot zeigt im API-Container:
  - `python -m tbserver.migrations up`
  - `OK`
  - `APPLIED 016_btb_photo_link_metadata.sql`
- Migration 016 damit praktisch **BESTÄTIGT**.
- Nächster Schritt: API + Worker neu starten; danach Health und Portal-BTB Smoke.

## ADMIN-FINISH-001 – Checkpoint 28.09.2026
- SERVER-HEAD vor Wiederaufnahme: `724badfa1e8fbff8264c63ad4b5941249c28d867`.
- Android-HEAD geprüft: `423f37892915cb3308a4b1ea9e4d196faa872326`.
- Draft-PR #6 SERVER offen; Draft-PR #11 ANDROID offen.
- Timeout-Vorgängerblock wieder aufgenommen.
- Altbestand-Fall bestätigt: älterer `survey_originals/from-file`-Import lässt `tb.files` und `tb.survey_originals` auf denselben physischen `storage_key` zeigen.
- Fehler vor Codeänderung als `ADMIN-STORAGE-001` gesichert.
- Fix: globale Speicherbelegung zählt gemeinsamen Speicherpfad nur einmal; Firmen-Foto/PDF/Sonstiges schließen solche Legacy-Dateizeilen aus; Vermessungsoriginal bleibt unter Trassierungsdaten sichtbar.
- Keine Daten gelöscht oder migriert.
- Source-Assertions PASS.
- CI erneut ohne Runner-Steps; kein Testnachweis.
- Nächster praktischer Schritt: neuen Serverstand per GitHub Desktop/File Station auf Synology übernehmen, API neu starten, danach Admin-Smoke/E2E.

### ADMIN-RUNTIME-002 – Firmenverwaltung 500
- Benutzer hat direkten API-Container-Test ausgeführt.
- Traceback: `psycopg.ProgrammingError: only '%s', '%b', '%t' are allowed as placeholders, got '%'`.
- Ursache eindeutig in Firmen-Speicherquery mit `LIKE 'image/%'` + `%s`.
- Source-Fix in Commit `6cdccc9267302f8442819d02a884406ad00163a7`.
- Nächster praktischer Schritt: nur `server/tbserver/admin_web.py` auf NAS ersetzen, API neu starten, Firmenverwaltung erneut testen.

## ADMIN-STRUCTURE-004 – Checkpoint 28.09.2026
- Benutzer bestätigt firmenzentrierte Struktur vollständig:
  - Benutzer, Geräte und Baustellen aus globaler Hauptnavigation;
  - Geräte ausdrücklich vollständig in die Firma;
  - Firmenliste zuerst;
  - `Firma verwalten` mit linker Seitennavigation.
- Source umgesetzt:
  - `v1_base.html` Navigation verdichtet;
  - `v1_admin_dashboard.html` Schnellzugriff angepasst und sichtbaren 5-Sekunden-Hinweis entfernt;
  - `v1_admin_companies.html` moderne Firmenkarten + `+ Firma anlegen`;
  - `v1_company_branding.html` neuer Firmenworkspace mit Übersicht/Stammdaten/Benutzer/Geräte/Baustellen/Speicher/Logo-PDF;
  - `admin_web.py` liefert erweiterte firmenbezogene Daten;
  - `admin.py` enthält Admin-Baustellenanlage mit Membership/Team/Change/Audit.
- Benutzer können im Firmenworkspace angelegt/bearbeitet/aktiviert/gelöscht werden.
- Gebundene Geräte können dort blockiert, gelöst oder komplett gelöscht werden.
- Baustellen können dort direkt mit verantwortlichem TB angelegt werden.
- Source-JavaScript PASS; keine `app/**`-Änderung.
- CI erneut `steps = null`; kein Testnachweis.
- Nächster Schritt: geänderte Serverdateien auf Synology → API neu starten → Browser-Retest.
- MIB/Bahnhof-Autofill bleibt danach der nächste getrennte Fehlerblock.

## ADMIN-COMPANY-005 – Checkpoint 28.09.2026
- Praxisbefund: Aktivierungskarte führte über `Zurück zu Benutzer` auf die alte globale Benutzerseite.
- Benutzer wünscht sichtbare Restgültigkeit.
- Geräte zeigten 1.3.19/1.3.4, während Release 1.3.25 veröffentlicht ist.
- Source-Ursache Geräteversion bestätigt: Android meldet Version aktuell nur bei Geräteaktivierung; spätere `/device/me`-Checks senden keine Versionsdaten; Server aktualisierte bisher nur `last_seen_at`.
- SERVER-Fix:
  - Rücksprung in den Firmenworkspace;
  - globale Listenpfade redirecten zu Firmen;
  - Gültigkeit mit Restdauer;
  - Gerätewert als `zuletzt gemeldet` kennzeichnen und veröffentlichten Release daneben anzeigen;
  - Server akzeptiert künftig `X-TB-App-Version`/`X-TB-App-Build` bei Gerätekontakten.
- Android-Anpassung als DEVICE-INFO-001 in `05_ANDROID_SERVER_VERTRAG.md` dokumentiert; kein `app/**` geändert.
- Nächster Schritt: vier Serverdateien auf Synology ersetzen → API neu starten → Browser-Retest.

### ADMIN-COMPANY-005 – Gültigkeitsauswahl Benutzer
- Benutzer bestätigt den aktuellen Firmen-/Gerätebereich als passend.
- Letzte gewünschte Ergänzung: bei Benutzerbearbeitung dieselbe Gültigkeitsauswahl wie beim Anlegen.
- umgesetzt in `server/templates/v1_company_branding.html`: Kein Ablauf / 3 Monate / 6 Monate / 1 Jahr / Manuell + letzter gültiger Tag.
- JavaScript-Sourcecheck PASS.
- Nächster Schritt: nur das Template auf Synology ersetzen → API neu starten → Benutzerbearbeitung kurz testen.


## ADMIN-DASH-006 – Checkpoint 28.09.2026
- Benutzerauftrag: Übersicht schöner machen, MIB-/Bahnhof-Fehler reparieren, sichtbare Restfehler erkennen.
- GitHub zuerst geprüft: SERVER/ANDROID HEAD + PRs + Projektsteuerung.
- Exakte Referenzprüfung:
  - Android- und Server-Bahnhofsdaten sind identische Blobs;
  - `MIH` = `Ingolstadt Hbf`;
  - `MIB` existiert nicht als exakter Code.
- Ursache des bisherigen leeren Bahnhofsfelds: Synology-API hatte keinen `/app/data`-Mount.
- Source-Fix:
  - `./data:/app/data:ro` im API-Service;
  - Tippfehler-/Ähnlichkeitssuche mit offiziellem Code;
  - sichtbare Referenzdatenfehler;
  - Systemwarnung bei fehlenden Referenzdateien.
- Dashboard verfeinert:
  - Bestand + Firmenspeicher nebeneinander;
  - modernere KPI-/Quicklink-Karten;
  - Firmen-Speicherbalken;
  - Warnungen direkt quittierbar;
  - alte Auto-Refresh-Beschriftung entfernt;
  - `Streckendaten` konsistent benannt.
- Source-JS-Checks PASS.
- Wichtig: Compose-Volume erfordert Synology-Container-Recreate, nicht nur Restart.

- CI-Nachtrag auf HEAD `bf6bc9f4...`: Server `36480722916` + Android `36480722938`, beide failure ohne Runner-Steps; nicht als Sourcefehler werten.


## ADMIN-PDF-007 – Checkpoint 28.09.2026
- Benutzer bestätigt: Bahnhof funktioniert nach Recreate.
- Neuer Runtime-Fehler: PDF-Logo lässt sich in der Vorschau weder sichtbar vergrößern noch horizontal verschieben.
- Ursachen: Preview-Endpunkt erhielt nur gespeicherten DB-Stand; 48-mm-Serverheader komprimierte quadratische Logos trotz großer Breite.
- Source-Fix: aktuelle Reglerwerte als nicht persistente Preview-Query; 350-ms-Debounce-Livevorschau; Großansicht mit denselben Draft-Werten; explizites Speichern bleibt getrennt; Header 62 mm / Logo-Zone Android-nah bis 174 pt; Renderer `tb-server-btb-pdf-4`.
- Synology-Screenshot zeigte zusätzlich Compose-Warnung für POSTGRES_USER/POSTGRES_DB; Restore-Test-Healthcheck auf Container-Auswertung korrigiert.
- Source-Checks PASS; Runtime-Retest auf Synology offen.
