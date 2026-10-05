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

## ADMIN-DASH-008 – Checkpoint 28.09.2026
- Benutzer fordert deutlich sichtbare Festplatten-Temperaturen in **°C** und verweist auf DSM mit getrennten Werten für Laufwerk 1/2.
- GitHub vor Arbeitsblock geprüft: SERVER-HEAD `b944273006d7f5b16055328986e61a0f021e8c4a`, ANDROID-HEAD `423f37892915cb3308a4b1ea9e4d196faa872326`, Draft-PR #6/#11.
- Anforderung vor Code in `02_AKTIVER_AUFTRAG.md` gesichert, Commit `6ab985b6ba78f001e65ae4a47b48d14bd7c355e7`.
- Diagnose: bisher nur generische `/sys/class/thermal`-Systemtemperatur, keine physische Disktemperatur.
- Source-Fix Commit `051c7d87b3b5cc849bb4faff54259d9ffee639ca`:
  - reale Synology-Disktemperaturen aus `/run/synostorage/disks/*/temperature`;
  - read-only Host-Mount in API;
  - `host.disk_temperatures[]` im Systemstatus;
  - Dashboard zeigt `Laufwerk N: <Wert> °C`;
  - sauberes `Nicht verfügbar` bei fehlender Hostquelle;
  - Sourcecheck erweitert.
- Keine DB-Migration, keine Android-Änderung.
- Source-Marker und Commit-Diff nach Branch-Update geprüft.
- Status: **SOURCE FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**.
- Nächster praktischer Schritt: GitHub Desktop → File Station → aktualisiertes Compose → Container Manager Recreate → Dashboardwerte gegen DSM prüfen.

### ADMIN-DASH-008 – CI-Nachtrag
- GitHub Actions auf Code-HEAD `051c7d87b3b5cc849bb4faff54259d9ffee639ca`:
  - TB-Server Runs `36488381133` und `36488374650`: `failure`, Job `foundation`, `runner_name = ""`, `steps = []`;
  - Android Run `36488381240`: `failure`, Job `build`, `runner_name = ""`, `steps = []`.
- Bewertung gemäß Projektregel: **keine Runner-Steps ausgeführt → kein Build-/Testnachweis und kein Sourcefehler-Nachweis**.
- ADMIN-DASH-008 bleibt **SOURCE FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**.

## PDF-RUNTIME-009 – Diagnosecheckpoint 29.09.2026
- Benutzer meldet: 22.09-PDF war noch in Ordnung; spätere PDFs sind verschoben/anders; aktueller Handy-`PDF Entwurf` sieht gut aus.
- Vergleichs-ZIPs 22.09/25.09/26.09 geöffnet und BTB-PDFs gerendert.
- Harte technische Trennung bestätigt:
  - 22.09 Producer `Skia/PDF m148` = Android-Renderer;
  - 25.09/26.09 Producer `ReportLab PDF Library`, Footer `tb-server-btb-pdf-3` = Server-Renderer.
- 25/26 zeigen nicht nur einen Logo-Offset, sondern ein vollständig anderes generisches Layout (Meta-/Kopfdaten, flache Facheinträge, anderer Fotoanhang).
- Aktueller Servercode `server/tbserver/btb_pdf.py` bestätigt diese Ursache; Renderer v4 hat nur Header/Logo verbessert, nicht die Dokumentparität.
- Android-`app/**` bleibt unverändert.
- Nächster Schritt: SERVER-Renderer gegen Android-`BautagebuchPdf.kt`/22.09-Referenz angleichen und danach visuell + auf Synology testen.

## PDF-RUNTIME-009 – Source-Checkpoint 29.09.2026
- Benutzer hat nach bestätigter Ursache die SERVER-Umsetzung freigegeben.
- Arbeitsblock startete nach erneutem GitHub-Check; vor Code wurde die Freigabe in `02_AKTIVER_AUFTRAG.md` gesichert.
- Source-Fix Commit `d2c3ca9e046abdc3f176e16a96148dfda2260dca`.
- Neu: `server/tbserver/btb_pdf_android_style.py` als Android-naher A4-Abschlussrenderer.
- `server/tbserver/btb_pdf.py`: Renderer v5 delegiert normalen Abschluss an den neuen Pfad; alter generischer Renderer nicht mehr autoritativ.
- `server/tbserver/btb.py`: PDF-Kontext um Lead-E-Mail/Mobil und vorhandene Worksite-Nodes ergänzt.
- `server/checks/34_verify_tb_server_1_btb_pdf_source.py`: v5-/Android-Layout-Marker ergänzt.
- Keine Android-Datei, keine DB-Migration, kein Compose geändert.
- Lokaler synthetischer Visual-Smoke der Rendererlogik gegen das 22.09-/Android-Zielbild sah strukturell passend aus; dies ist **kein Synology-E2E**.
- GitHub Actions Code-HEAD: Server `36551663700`/`36551656529`, Android `36551663628`, alle ohne Runner-Steps; kein Testnachweis.
- Nächster praktischer Schritt: GitHub-Stand auf NAS übernehmen, API neu starten, neuen BTB serverseitig finalisieren und PDF visuell mit 22.09 + Handy-Entwurf vergleichen.
- Status: **SOURCE-FIX FERTIG / NICHT DEPLOYT / VISUAL-RUNTIME-RETEST OFFEN**.

## ADMIN-PDF-010 – Checkpoint 29.09.2026
- Benutzer klärt: Bild 2 = verbindliches PDF-Zielbild; Bild 1 = aktuelle Admin-Vorschau.
- Neue Anforderung vor Code in `02_AKTIVER_AUFTRAG.md` gesichert.
- Source-Fix Commit `972017bf893c1dc348a7f52d39863c4c9fbeda02`.
- Admin-Vorschau benutzt nun denselben `render_android_style_btb_pdf()`-Pfad wie der Serverabschluss.
- Beispielvorschau bildet Wetter, Personal/Maschinen, Unterbaustelle/KM und Punkte 4–8 im Zielaufbau ab.
- Personal und Maschinen wieder mit unabhängiger Boxhöhe.
- Keine Android-/DB-/Compose-Änderung.
- CI erneut ohne Runner/Steps; kein Testnachweis.
- Nächster Schritt: zwei Python-Dateien auf Synology → API neu starten → Screenshot der neuen Admin-Vorschau.

## ADMIN-PDF-011 – Checkpoint 29.09.2026
- Benutzer-Screenshot: Admin-Zielbild nun strukturell passend; bei `250 / X=70 / Y=-60` bleibt Logo-Y sichtbar unverändert.
- Neue Anforderung: Breite bis 400 pt und funktionierender Y-Versatz.
- Vor Code in `02_AKTIVER_AUFTRAG.md` + `05_ANDROID_SERVER_VERTRAG.md` gesichert.
- Ursache: v5-Renderer setzte max. Logo-Höhe 88 pt und klemmte `top_y` anschließend so, dass bei hohen/quadratischen Logos kein nutzbarer Y-Bereich übrig blieb.
- Fix `de7374dc392959131326815c30ebdb2733eea705`: Server/API/UI 400 pt; echter Y-Anker; A4-Schutzgrenzen; nachfolgender Inhalt berücksichtigt Logo-Unterkante.
- Android nicht geändert; aktueller Android-Clamp 250 bleibt separates Vertrags-To-do.
- CI erneut ohne Runner/Steps; kein Testnachweis.
- Nächster Schritt: 4 Runtime-Dateien auf Synology, API neu starten, Regler praktisch prüfen.

## ADMIN-PDF-012 – Checkpoint 29.09.2026
- Benutzer bestätigt 400-pt-/Y-Funktion praktisch, meldet aber neues Verhalten: Tabellen wandern mit großem/verschobenem Logo nach unten.
- Benutzer fordert ausdrücklich zuerst Antwort, dann Umsetzung; Vorgabe bestätigt und danach Arbeit gestartet.
- Neue Anforderung vor Code in `02_AKTIVER_AUFTRAG.md` gesichert, Commit `e2b373e51c4320ba9134ff06c9358f65c5937ceb`.
- Source-Fix Commit `be196108f77192bc4ea676d6dcb32092076f1151`: dynamische Header-Unterkante beeinflusst Fachlayout nicht mehr.
- Nur Serverrenderer + Sourcecheck geändert; Android/DB/Compose unverändert.
- CI Run `36557039970` ohne Runner-Schritte; kein Testnachweis.
- Nächster Schritt: eine Runtime-Datei auf Synology, API neu starten, Tabellenposition bei mehreren Logo-Werten vergleichen.

## ADMIN-PDF-013 – Checkpoint 29.09.2026
- Benutzer meldet nach ADMIN-PDF-012: `99% fertig`; Tabellen bleiben stehen, aber `BAUTAGEBUCH` ist abgeschnitten.
- Screenshot zeigt, dass das sehr große Logo bzw. dessen weißer Hintergrund den rechten Titelteil überdeckt.
- Anforderung zuerst in `02_AKTIVER_AUFTRAG.md` gesichert.
- Fix Commit `42de28d2422506d4fb594b19c38f8e404d598a22`: Titel wird als oberste Ebene mit weißem Schutzfeld nach dem Logo gezeichnet.
- Keine Android-/DB-/Compose-Änderung.
- CI Run `36558072863` ohne Runner-Schritte; kein Testnachweis.
- Nächster Schritt: eine Runtime-Datei auf Synology, API neu starten, identischen Logo-Test wiederholen.

## PDF-ADMIN – Abnahmecheckpoint 29.09.2026 13:08
- Nach ADMIN-PDF-013 wurde die letzte Runtime-Datei auf Synology übernommen und der API-Container neu gestartet.
- Benutzer bestätigt den sichtbaren Endstand ausdrücklich mit **`passt und abgenommen`**.
- Damit SERVER-seitig geschützt/bestätigt:
  - Admin-Vorschau im Android-nahen BTB-Zielbild;
  - Logo 40..400 pt;
  - X-/Y-Versatz;
  - Tabellen/Fachlayout bleiben bei Logoänderungen fest;
  - `BAUTAGEBUCH` bleibt vollständig sichtbar.
- ADMIN-PDF-007 / -010 / -012 / -013 geschlossen.
- ADMIN-PDF-011 nur noch ANDROID-seitig offen (aktueller Clamp 250 pt).
- PDF-RUNTIME-009 bleibt getrennt offen bis zum echten neuen serverseitigen BTB-Finalize-/Download-Praxistest.
- Diese Abnahme ist gemäß Schutzregel nicht ohne Grund zurückzubauen.

## REF-DATA-001 – Checkpoint 29.09.2026
- Nach abgenommenem PDF-Adminblock neuen Arbeitsblock vorschriftsgemäß mit Live-GitHub gestartet.
- SERVER-HEAD zu Beginn: `ab1817aad1bfcc54f849a9458a10b06d3f43683e`; ANDROID-HEAD `423f37892915cb3308a4b1ea9e4d196faa872326`; PR #6 Draft, PR #11 offen.
- Vor Code technischen Phase-1-Vertrag für globale Referenzdaten in `02_AKTIVER_AUFTRAG.md` + `05_ANDROID_SERVER_VERTRAG.md` gesichert.
- Phase 1 umgesetzt ohne Schemaänderung:
  - `NULL company_id` als `GLOBAL_REFERENCE`;
  - Admin-Scope-Auswahl global vs. Firma/Baustelle;
  - globale Duplikaterkennung;
  - read-only Manifest + Parsed-API;
  - Cache-Revision aus SHA/Parser-Version/parsed_at;
  - bestehende Worksite-Quellenkompatibilität bestätigt.
- Codecommits: `6440f538...`, Templatefix `d69b71e0...`, Nachschärfung `a0701329...`.
- Keine DB-Migration, kein Compose, kein Android-Code.
- GitHub CI erneut ohne Runner-Schritte; kein Testnachweis.
- Status: **SOURCE FERTIG / NICHT DEPLOYT / SYNLOGY-E2E OFFEN**.
- Nächster praktischer Schritt: 4 Runtime-Dateien via File Station übernehmen → API neu starten → Globalimport testen.

## REF-DATA-001/002 – Phase-A Checkpoint 29.09.2026
- Benutzer bestätigt Datei-zuerst + grafische Importvorschau als nächsten Bauweg.
- Vor Code Freigabe in `02_AKTIVER_AUFTRAG.md` gesichert.
- Neuer Serverpfad trennt Analyse und Import vollständig.
- Voranalyse liefert erkannte Dateimetadaten und kompaktes normalisiertes Grafikmodell.
- Admin rendert TRA-Linien + XY-Punkte gemeinsam, unterstützt Zoom/Pan/Klickdetails und KM-/Stationsbänder.
- Importziel erscheint erst nach Analyse und ist je Datei/ZIP-Eintrag änderbar; Default Global.
- Sessions verfallen nach 6 h und werden bei erfolgreichem Import entfernt.
- Sourcecommits: `fc4d001b...`, `430c427d...`, `7da65428...`, `b7dcca93...`.
- Keine DB-Migration, kein Compose, kein Android-Code.
- CI ohne Runner-Schritte; kein Testnachweis.
- Status: **SOURCE FERTIG / NICHT DEPLOYT / SYNLOGY-E2E OFFEN**.
- Nächster Schritt: vier Runtime-Dateien auf NAS → API neu starten → echte TRA/KF-Voranalyse testen.

## REF-DATA-002 – Pasing-Korrekturcheckpoint 29.09.2026
- Benutzer zeigt Serverpreview und mehrere Android-Screenshots und beanstandet Serverdarstellung als unlogisch.
- Fehler bestätigt; Android-Darstellung ist fachliche Referenz.
- Pasing-ZIP direkt geprüft: 41 Dateien; TRA 21; GRA 17; KF 1; KF.TXT 1; PDF 1; Strecken 5524/5528/5529; Bahnhofsgleise 10; WVB 3.
- Hauptfehler der Servergrafik: SVG-Linien wurden durch spätere Farbklasse gefüllt und dadurch zu großen Dreiecksflächen.
- Zusätzlich Konzept korrigiert: keine Dateifarben-Legende mehr, sondern fachliche Achsen/Objektgruppen wie Android.
- Sourcefixes `364f412c...`, `2c36c106...`, `a51cd9a2...`.
- Finales Browser-JS syntaktisch via V8 geprüft: PASS.
- Kein Synology-Retest nach Korrektur; Status bleibt **SOURCE KORRIGIERT / RUNTIME OFFEN**.

## REF-DATA-002 – Punktlabel-Checkpoint 29.09.2026
- Benutzer liefert zweiten Server-Screenshot plus Android-Referenz: gelbe Vermessungspunkte sind sichtbar, aber deren fachliche Beschriftung fehlt; Server-Achsenlabels überlagern sich massiv.
- Android-Code erneut gezielt geprüft: `zeichneFestpunkte()` blendet Punktlabels ab Zoom 2.2 begrenzt ein und ergänzt ab Zoom 5.5 KM; Detailmodell enthält Strecke/Gleis/Höhe/Typ/Quelle.
- Anforderung vor Code dokumentiert.
- Serverfix: Preview-Metadaten erweitert, Großansicht, zoomabhängige Punktlabels, kollisionsarme Achsenlabels, vollständigeres Klickdetail.
- Commits `54de23bc...`, `00d56f54...`, `1363c285...`.
- Browser-JS via V8: PASS.
- GitHub Actions ohne Runner-Schritte; kein Testnachweis.
- Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**.

## REF-DATA-002 – Tiefzoom/KM-Checkpoint 29.09.2026
- Nach Benutzererklärung Zoom-/LOD-Konzept umgesetzt.
- Fix `69096bd7e615c3fd049b50d0a290e5941400e3da`: Screen-Space-Punktmarker, separates Klickziel, ca. 30x Tiefzoom, reduzierte Labeldichte/-größe.
- Rechter KM/Stationsbereich präzisiert: echte KM-Spannen bleiben, Nullspannen werden verworfen, GRA wird bevorzugt, technische Station getrennt.
- Finales JS syntaktisch geprüft: PASS.
- GitHub CI ohne Runner-Schritte; kein Testnachweis.
- Status: **SOURCE FERTIG / SYNLOGY-RUNTIME-RETEST OFFEN**.

## REF-DATA-002 – Screen-Space-Checkpoint 29.09.2026
- Benutzer bestätigt Hover/Klick-Punktinteraktion als nächsten Umbau.
- Vor Code Freigabe in Projektsteuerung gesichert.
- Code `c217e4ea...`: Punkte/Labels aus SVG-Weltkoordinaten in HTML-Screen-Space-Overlay verschoben.
- Hover, strukturierter Klickdetail, 24-px-Trefferzone und Mehrfachtrefferliste umgesetzt.
- Großansicht erhält schwebende Auswahlkarte.
- Finales Browser-JS V8: PASS.
- GitHub Actions ohne Runner/Steps; kein Testnachweis.
- Status: **SOURCE FERTIG / SYNLOGY-RUNTIME-RETEST OFFEN**.

## REF-DATA-002 – GVP-Kalibrierungscheckpoint 29.09.2026
- Benutzer fordert nach Vergleich Server/Android: alle fachlich vermarkten Strecken/Gleise sollen bei belastbarer GVP-KM automatisch grün werden; freie Strecke war im Server noch rot.
- Android-Repository geprüft: `TrassenRepository.graMitAchspunktenVerknuepfen()` und `kmMitGvpFestpunktenKalibrieren()` sind Referenz.
- Serverpreview übernimmt Kriterien: gleiche Strecke, >=4 GVP mit KM, >=4 Samples, ≤120 m, Medianoffset, ±80 m robust, >=4 Treffer.
- Normale Höhen-GRA wird nicht mehr automatisch als KM-GRA gewertet; Android-Namensregel für explizite KM-GRA übernommen.
- GVP-Achse → grün + GVP-KM-Bereich + Diagnose; unplausibel → grau; keine Quelle → rot.
- Punktdetail: `KM aus Datei` + Bahn-KM; KF/KF.TXT-Dubletten in Preview reduziert.
- Zoom auf ca. 160x erhöht.
- Codecommits `34090632...`, `0292ca9f...`, `13b2a0be...`.
- Browser-JS PASS; GitHub Actions ohne Runner/Steps.
- Status: **SOURCE FERTIG / SYNLOGY-RUNTIME-RETEST OFFEN**.

## REF-DATA-002 – Achsen/KOR-Checkpoint 29.09.2026
- Benutzerauftrag `bauen` nach Erklärung bestätigt.
- Sourcefix `6299fca8...`: Achsen-Hover/Klick, 16-px-Hitline, ca. 2000x Zoom, KOR-Metadatenfilter + diagnostische Restwarnungen.
- Original Gessertshausen-KOR nicht als Rohdatei in Conversation/Library gefunden; keine erfundene Spezialbehandlung.
- Browser-JS V8 PASS.
- CI ohne Runner-Schritte; kein Testnachweis.
- Status: **SOURCE FERTIG / SYNLOGY-RUNTIME-RETEST OFFEN**.

## REF-DATA-002 – Bestätigung Achsen/KOR/Tiefzoom 29.09.2026
- Benutzerantwort nach Synology-Retest: `okay,passt`.
- Der Teilblock aus Codecommit `6299fca8232ce59dd65873ff733ad988b5db54da` gilt damit als **BESTÄTIGT**.
- Bestätigte Inhalte: Achsen-Hover/Klick, breite unsichtbare Trefferzone, erweiterter Tiefzoom, KOR-Parser v3.
- Schutzregel: nicht ohne Grund ändern.
- Übrige REF-DATA-002-Themen bleiben davon getrennt.

## REF-DATA-003 – SERVER-Sourcecheckpoint 29.09.2026
- Verbindliche Reihenfolge bleibt **SERVER → Portal → Android**.
- SERVER-HEAD: `782e72e18790570066b40da08a80471fa14d759e`.
- Sourceblock ist nach wiederholtem GitHub-Audit **SOURCE FERTIG**.
- Kern: Migration 017, Katalog Assets/Achsen/Segmente/Konflikte/History, Suche/Availability/Geometry, versionierter `next_after`-Feed, Beiträge/Korrekturen, Reindex, Konfliktmail.
- Teilüberdeckung löscht keinen nicht abgedeckten Alt-/Manuellrest; Vollsupersede nur bei geometrischer Vollabdeckung.
- Schwache Achskennung darf über kompatible Strecke/Betriebsstelle + engen X/Y-Kandidaten geometrisch geprüft werden; Mehrdeutigkeit = REVIEW_REQUIRED.
- Rohoriginale bleiben unverändert; globale abgeleitete Sicht kann von privater Rohdatei getrennt sein.
- Normale Nutzer sehen keine fremden Bearbeiter-/Firmenidentitäten im globalen Katalog.
- Worksite-Fallback, GLOBAL-Duplikaterkennung, source_date, Lifecycle/Purge und Capability-Flags abgeschlossen.
- Finaler GitHub-Source-Audit: 25/25 Marker PASS.
- Actions `36621416472`, `36621409361`: `steps=[]`, also kein CI-Testbeweis.
- Status ausdrücklich: **SOURCE FERTIG / NICHT DEPLOYT / SYNLOGY-E2E OFFEN**.
- Nächster Arbeitsblock: Synology-Deployment + Migration017 + API/Worker + Reindex + dokumentierter Runtime-/Postman-E2E. Portal erst danach.

## REF-DATA-003 – Synology-Runtimecheckpoint 30.09.2026
- Benutzer hat den neuen Serverstand auf Synology übernommen.
- Admin-Systemstatus zeigt: API 1.0.0-dev staging, Migrationen 17/17, keine ausstehenden Migrationen, DB OK, interne API OK, Worker OK, Cloudflare/öffentliche API OK.
- Damit Status: **DEPLOYT / RUNTIMEBASIS BESTÄTIGT / SURVEY-FACH-E2E OFFEN**.
- Keine neue HTML-Seite war beim Deployment zu übernehmen; Portalblock wurde bewusst noch nicht gebaut.
- Nächster Schritt: Survey-Katalog reindexen und Runtime-Endpunkte/Fachfälle prüfen. Erst danach Portal `Trassendatenübersicht`.

## REF-DATA-003 – Reindex-Checkpoint 30.09.2026
- Synology API-Container: `reindex_all_originals()` erfolgreich.
- Ergebnis: 54 Originale, 31 ADDED, 22 INDEXED_ASSET, 1 MERGED, 0 failures.
- Runtimefall Reindex ist praktisch bestätigt.
- Als Nächstes den Katalogbestand und speziell den einen MERGED-Fall fachlich prüfen.
- Portal bleibt bis zum Server-Fach-E2E zurückgestellt.

## REF-DATA-003 – Merge-Runtimecheckpoint 30.09.2026
- Katalogabfrage: Strecken 5302/5340/5524/5528/5529 erkannt; zusätzlich 10 Achsen ohne Streckenzuordnung.
- Genau ein Merge: `Str 5524-1`, `ORIGINAL_TRA`, `OVERLAP_CONFIRMED`, `ACTIVE`, Quelle `5524_SL_ER0_Gp2026_02_16.TRA`.
- Segmentstatus gesamt: 31 PRIMARY/ACTIVE + 1 OVERLAP_CONFIRMED/ACTIVE.
- Nächster Test: alle Segmente der Achse 5524-1 mit Quellnamen, Station und X/Y-Endpunkten gegenüberstellen.
- Keine Fachbestätigung des Merges, bevor diese Gegenprüfung erfolgt.

## REF-DATA-003 – Betriebsstellen-/Merge-Checkpoint 30.09.2026
- 5524-1-Gegenvergleich: 1111 Geometrietreffer, ca. 1958.686 m Überlappung, Medianabstand ca. 0.0135 m, max. ca. 0.728 m → konkreter Auto-Merge praktisch bestätigt.
- Ursache der leeren Betriebsstelle ist bestätigt: Admin-Backend kann station_code/name, aktuelle Admin-HTML bietet/sendet diese Felder aber nicht.
- Android-Grundprinzip bleibt Referenz: Importeur ordnet Bahnhof/Betriebsstelle fachlich zu.
- Ziel Admin: Referenzsuche RL100/Name, Default für Upload/ZIP, Override je Gruppe; freie Strecke darf ohne Betriebsstelle bleiben.
- Bereits importierte Katalogachsen müssen nachträglich zuordenbar sein, ohne Originale anzufassen.

## REF-DATA-003 – Testdaten-Resetentscheidung 30.09.2026
- Benutzer erlaubt ausdrücklich den vollständigen Reset des aktuellen Vermessungs-/Trassendaten-Testbestands auf Staging.
- Quelldaten sind weiterhin vorhanden und können danach neu importiert werden.
- Noch nicht löschen, bevor der sichere Server-Löschpfad geprüft und die Bahnhof/Betriebsstellen-Eingabe im Import ergänzt ist.
- Produktionsdaten/-schutz sind von dieser Erlaubnis nicht umfasst.

## REF-DATA-003 – Reset/Import-Checkpoint 30.09.2026
- Benutzer erlaubt Reset des aktuellen Staging-Vermessungsbestands.
- Sicherer Reset-Endpunkt ist source-seitig vorhanden und produktionsgesperrt.
- Admin-Import kann source-seitig jetzt Bahnhof/Betriebsstelle über Referenzsuche auswählen und station_code/name senden.
- Bulk-Default + Gruppenoverride; freie Strecke kann leer bleiben.
- Bestehende bestätigte Grafik/Zoom/KOR/GVP-Funktion nicht verändert.
- Noch nichts auf Synology gelöscht.
- Nächster Schritt: deployen, UI prüfen, Preview-Zählwerte kontrollieren; erst danach Reset ausführen.

## REF-DATA-003 – Runtimecheckpoint Bahnhofsauswahl 30.09.2026
- Synology-UI findet MPB – München-Pasing Bbf aus Referenzdaten.
- Bulk „Auf alle Gruppen anwenden“ funktioniert; Gruppen zeigen MPB-Zuordnung.
- Importergebnis: 6 importiert, 34 Duplikate, 0 übersprungen, 0 Fehler.
- Neu importierte Dateien zeigen MPB; alte Duplikate bleiben immutable und behalten alte Metadaten.
- BlueMail zeigt Trassendaten-Prüffall MPB – Pasing Bbf: Konflikt-Mailweg praktisch bestätigt.
- Deshalb jetzt nicht weiter über Duplikate importieren, sondern freigegebenen Staging-Reset durchführen und anschließend vollständigen Pasing-Neuimport.

## REF-DATA-003 – Reset-Preview-Checkpoint 30.09.2026
- Synology staging: Reset-Preview allowed=True.
- Bestand vor Reset: 60 Originale, 60 Parser, 60 Assets, 33 Achsen, 35 Segmente, 1 Konflikt, 164 History.
- 0 Survey-Sections, 0 Track-Source-Links, 0 Import-File-Rows.
- Noch keine Löschung ausgeführt.
- Physische Originale liegen unter storage-v1/reference/survey-originals/...; direkte manuelle Datei-/DB-Löschung bleibt verboten, weil DB/Katalog/Dateisystem gemeinsam konsistent entfernt werden müssen.

## REF-DATA-003 – Reset-BESTÄTIGT-Checkpoint 30.09.2026
- Geschützter Staging-Survey-Reset tatsächlich ausgeführt.
- Vorher 60 Originale; nachher alle Survey-Zähler 0; 60 Storage-Keys entfernt.
- Testbestand ist damit sauber leer.
- Nächster Schritt: kompletter Neuimport Pasing mit MPB.
- Für späteren Normalbetrieb gilt nicht Komplettreset, sondern versioniertes Ändern/Deaktivieren/Ersetzen und geschütztes Einzel-Löschen.

## REF-DATA-003 – Auditcheckpoint Strecke/Gleis/WVB/Überlagerung 30.09.2026
- Sichtbare Beispiele: Gl 12 / Str 5529 und Gl 714 / Str 5528 werden erkannt, aber Streckenfeld bleibt leer; Gl 21/23 übernehmen 5528 korrekt.
- WVB 10-11/14-22 sind als Weichenverbindung erkannt, aber Strecke/Gleis fehlen; Mail-Prüffall WVB_14-22 = NEARBY_GEOMETRY_NOT_MATCHING.
- Android zeigt nahe/überlagerte Trassen gleichzeitig; Admin-Analyse soll das ebenfalls eindeutig zeigen.
- Android-Suche MPB + Gl 27 wird gegen Live-Quellstand geprüft; keine Android-Änderung im SERVER-Block ohne dokumentierten Bedarf.

## REF-DATA-003 – Auditabschluss 30.09.2026
- Android live geprüft: Bahnhof/Gleis-Filter ohne Streckenpflicht; Bahnhof/Weiche analog.
- Server strong identity: station_code + track_key bereits vor route/direction.
- Route-Badge und Route-Input stammen im Template aus demselben Wert; Backend hat zusätzlich inferred_route-Fallback. Sichtbares leeres Input = UI-State-Problem.
- WVB 14-22: Parser/Analyse erkennt WVB, Katalog hat aber keinen stabilen WVB object_key; nahe andere WVB können dadurch Reviewmail erzeugen.
- Android-Grafik kann überlagerte Achsen per Multi-Hit/Long-Press unterscheiden; Admin derzeit nicht.
- Noch kein Code nach diesem Audit; auf Benutzerfreigabe warten.

## REF-DATA-003 – Konflikt-UI Anforderung 30.09.2026
- Warnung allein ist nicht ausreichend.
- Portal braucht „Problem ansehen“ pro Prüffall.
- Detail muss beide betroffenen Geometrien/Quellen, verständliche Ursache, Messwerte und mögliche fachliche Aktionen zeigen.
- E-Mail bleibt nur Benachrichtigung.
- Noch kein Code; zuerst vorhandene Conflict-API/-Daten prüfen.

## REF-DATA-003 – Build-Start 30.09.2026
- Explizite Benutzerfreigabe zum Bauen.
- Arbeitsreihenfolge: Route/Gleis-Prefill → Weiche/WVB object_key → Admin Multi-Hit → Konflikt-Detailfenster.
- Danach Pasing Runtime-E2E; Trassendatenübersicht folgt erst anschließend.
- Android app/** bleibt unberührt.

## REF-DATA-003 – Diagnoseblock Sourcecheckpoint 30.09.2026
- HEAD `5604ece1bcedf1c055933cfe11f2f62a084677a3`.
- Source fertig: Route/Gleis/Objekt-Prefill, Weiche/WVB object_key, Migration018, Achsen-Multi-Hit, Conflict-Detail-API und „Problem ansehen“-Dialog.
- Weiche: `W:<Nr>`; WVB: `WVB:<A>-<B>`.
- Verschiedene bekannte WVB-object_keys werden nicht mehr nur aufgrund räumlicher Nähe als dieselbe Achse behandelt.
- Problemfenster zeigt beide Geometrien, Quellen, verständliche Ursache, Messwerte und vier versionierte Entscheidungen.
- Protected REF-DATA-002 Zoom/Hover/KOR/GVP-Grundfunktion bleibt vorhanden.
- Finaler Source-Audit: 25/25 PASS.
- Actions: `steps=[]`, kein CI-Testnachweis.
- Status: **SOURCE FERTIG / MIGRATION018 NICHT DEPLOYT / RUNTIME-E2E OFFEN**.
- Nächster Schritt: Synology deployen, 18/18 prüfen, Pasing Gate testen; danach Trassendatenübersicht.

## REF-DATA-003 – Parser-v4-/Upgrade-Checkpoint 30.09.2026
- Code-HEAD vor Statuscommit: `31c1c6f6a066a76b694aef56de7719457750a146`.
- Parser jetzt `tb-survey-parser-4`.
- Reindex erkennt missing/stale Parse-Caches über Parser-Version + source_sha256 und reparst Raw-Originale intern.
- Bereits vorhandenes ORIGINAL_TRA-Segment bleibt bestehen.
- Fehlender object_key wird auf bestehender Achse fill-only ergänzt (`ALREADY_INDEXED_ENRICHED`).
- Vorhandener object_key und Achstyp-Konflikte werden nicht überschrieben.
- History/Audit für Enrichment vorhanden.
- Source-Audit: 23/23 PASS.
- Actions erneut ohne Runner-Steps (`steps=[]`) → kein Testnachweis.
- Nächster Schritt bleibt Synology: deployen, 18/18, Reindex, Pasing-Gate; keine Trassendatenübersicht vor diesem Runtime-Gate.

## REF-DATA-003 – Synology Runtime 18/18 + Reindexfehler 30.09.2026
- User-Screenshot bestätigt Systemstatus 18/18, keine ausstehenden Migrationen.
- API staging/DB/interne API/Worker/öffentliche API OK.
- Terminal bestätigt Parser `tb-survey-parser-4`.
- Reindex-Ausgabe: `original_count=40`, `reparsed=40`, `ALREADY_INDEXED=18`, `INDEXED_ASSET=19`, `failures=3`.
- Runtimebasis damit teilweise bestätigt; Upgrade-E2E bleibt wegen 3 Fehlern offen.
- Keine Spekulation/Codeänderung, bis Dateinamen und Exceptiontexte der drei Fehler vorliegen.

## REF-DATA-003 – Reindexfehler Diagnoseabschluss 30.09.2026
- Drei Fehler exakt identifiziert: WVB 10-11, 14-22, 19-20.
- Alle: CheckViolation auf `survey_catalog_history_operation_check` bei Operation `ENRICH`.
- Kein Daten-/Geometriefehler.
- Nächster Codefix ist ausschließlich eine DB-Migration zur Erweiterung der erlaubten History-Operationen.

## REF-DATA-003 – ENRICH-Constraint Fix Checkpoint 30.09.2026
- Commit `56969e788f6e4266d017505f965321c1c9afb167`.
- Migration 019 erweitert `survey_catalog_history_operation_check` um ENRICH.
- Drei reproduzierte WVB-Fehler werden damit an der tatsächlichen DB-Ursache behoben.
- Source-Audit 7/7 PASS.
- Status: SOURCE FERTIG / SYNLOGY 019 OFFEN.

## REF-DATA-003 – Reindex nach ENRICH-Fix runtime-grün 30.09.2026
- Synology/API-Terminal: `reindex_all_originals()` erneut ausgeführt.
- Ergebnis: `original_count=40`, `parser_version=tb-survey-parser-4`, `reparsed=0`.
- Statusverteilung: `ALREADY_INDEXED=19`, `INDEXED_ASSET=19`, `ALREADY_INDEXED_ENRICHED=2`.
- `failures=0`: der zuvor reproduzierte ENRICH-Constraint-Fehler ist im laufenden DB-Pfad behoben.
- Die 40 Originale sind vollständig bilanziert; kein Reindex-Fehler mehr offen.
- Abweichung zur Voraberwartung: nur 2 statt 3 WVB wurden in diesem Lauf als `ALREADY_INDEXED_ENRICHED` gemeldet. Vor weiterer Fachfreigabe read-only feststellen, welcher der drei WVB-Fälle bereits als `ALREADY_INDEXED` läuft und welche `object_key` die drei vorhandenen Achsen tatsächlich tragen.
- Der vorliegende Screenshot zeigt nicht den Systemstatus `19/19`; Migration 019 deshalb in der Dokumentation erst nach separatem Systemstatus-/Migrationstabellen-Nachweis als 19/19 bestätigen.
- Nächster Runtime-Schritt: WVB 10-11 / 14-22 / 19-20 mit Originalname, Achse, `object_key` und Segmentzahl gegenüberstellen; danach Pasing-Prefill, Multi-Hit und „Problem ansehen“ praktisch testen.


## REF-DATA-003 – Legacy-WVB-Reparatur Checkpoint 30.09.2026
- Live-GitHub zu Beginn geprüft: SERVER-PR #6 Draft; ANDROID-PR #11 offen; Android-HEAD unverändert `423f3789...`.
- Synology-Nachweis: Migration 019 = 19/19, pending=[]; Reindex failures=0.
- WVB-Read-only-Nachweis: 10-11 und 14-22 korrekt, WVB 19-20 fälschlich auf der WVB-14-22-Achse mit `axis_segment_count=2`.
- Ursache: Legacy-Merge vor object_key-Einführung; aktuelle neue-Merge-Kompatibilität ist für unterschiedliche bekannte object_keys bereits korrekt.
- Vor Code Fehlerstatus in `04_SERVER_STATUS.md` gesichert (Commit `253c7486...`).
- Sourcefix: bestehendes Segment wird nur bei eindeutig belegtem Legacy-Mismatch auf passende object_key-Achse umgehängt; Raw-Original und Segment-ID bleiben erhalten; offene Konflikte blockieren Auto-Reparatur.
- Code-/Check-Commits: `a7149047...`, `523450f8...`, `fcd2c024...`.
- Workflow Run 36728982421: conclusion=failure, Foundation jedoch `steps=[]`; kein CI-Testnachweis.
- Status: **SOURCE KORRIGIERT / NICHT DEPLOYT / SYNLOGY-RUNTIME-RETEST OFFEN**.
- Nächster Schritt: nur `server/tbserver/survey_catalog.py` auf NAS ersetzen, API neu erstellen/starten, Reindex ausführen, danach dieselbe WVB-read-only-Abfrage wiederholen.

### Sourcecheck vor Übergabe an Synology
- Reparaturguard isoliert gegen 4 Schutzfälle ausgeführt: **4/4 PASS**.
- Kein DB-/Runtime-Nachweis; Synology-Reindex bleibt nächster maßgeblicher Test.

## REF-DATA-003 – WVB-Runtime-Gate bestanden 30.09.2026
- Nutzer-Screenshots zeigen nach Deploy des Reparaturpfads: `original_count=64`, `reparsed=0`, `ALREADY_INDEXED=36`, `INDEXED_ASSET=27`, `ALREADY_INDEXED_REASSIGNED=1`, `failures=0`.
- WVB-Abfrage danach: 10-11, 14-22 und 19-20 jeweils auf eigener Achse, jeweils korrekter object_key und `axis_segment_count=1`.
- Legacy-WVB-Mergeproblem ist damit praktisch geschlossen und nach Schutzregel nicht ohne neuen reproduzierbaren Grund wieder anzufassen.
- Nächster Block: Pasing-UI- und API-Gate (Prefill, Multi-Hit, Problemfenster/Resolution, Search/Availability/Geometry).

## REF-DATA-003 – UI/Reset-Nacharbeit Checkpoint 30.09.2026
- Nutzer meldet: `Ziel nach Analyse` unverständlich; deaktiviertes Firma/Baustelle-Feld bei GLOBAL weiterhin störend; erneuter kompletter Staging-Neuimport gewünscht.
- Anforderung vor Code in SERVER_STATUS gesichert (Commit `5b2ea155...`).
- Template angepasst: `Speicherziel`, `Gemeinsamer Referenzbestand`, Firmenfeld bei GLOBAL verborgen.
- Geschützter bestehender Survey-Testreset direkt in Trassierungsdaten-Seite eingebaut; keine neue Backend-Löschlogik.
- Dashboard `Trassierungsdaten 0 B`: nur feste Firmen-Speicherkategorie; 0 B = keine Firmendaten, globale Referenzoriginale zählen dort nicht hinein.
- Nächster Schritt: Template auf Synology deployen → Reset über neue UI → Zähler 0 bestätigen → Pasing frisch importieren.

## REF-DATA-003 – Zentralimport/Terminal-Reset Checkpoint 30.09.2026
- Benutzerentscheidung: Auf der zentralen Admin-Trassierungsseite sollen Daten nicht zwischen GLOBAL und Firma/Baustelle unterschiedlich gespeichert werden. Diese Seite ist ausschließlich gemeinsamer Referenzimport.
- Umsetzung: Speicherziel-/Firma-Auswahl entfernt; fest `GLOBAL_REFERENCE`.
- `Trassierungsdaten` aus `Speicher nach Firma` entfernt.
- Benutzer möchte Reset ausdrücklich im API-Container-Terminal. Dafür geschützte Terminal-Helfer auf Basis desselben Resetkerns gebaut; keine freie SQL-Löschung.
- Nach Deploy zuerst Preview, dann Reset mit `VERMESSUNG TESTDATEN LOESCHEN`, danach Zähler 0 prüfen und Daten frisch hochladen.

## REF-DATA-003 – Sauberer Staging-Reset Checkpoint 30.09.2026
- Nutzer-Screenshot bestätigt Preview vor Reset: staging, 64 Originale, 64 Parse-Einträge, 64 Assets, 36 Achsen, 37 Segmente, 1 Konflikt, 325 History-Einträge.
- Terminal-Reset erfolgreich: `status=RESET`, `removed_storage_keys=64`.
- Reset-`after` und separate Preview danach zeigen überall 0.
- Survey-/Trassierungs-Testbestand ist damit sauber leer. Geschützten Resetpfad nicht weiter ändern.
- Nächster Schritt: Daten frisch hochladen; dabei zuerst neue zentrale Admin-UI visuell prüfen, dann Pasing/MPB-Import und Fach-E2E durchführen.

## REF-DATA-003 – Frische Analyse nach Reset 30.09.2026
- Screenshots bestätigen den neuen zentralen UI-Vertrag praktisch: keine Speicherziel-/Firma-Auswahl mehr.
- Frischer Analysezustand erkennt u. a. 5528/714, 5528/715 und 5528/725 korrekt vor.
- Originaltabelle bleibt vor Import leer.
- GVP/KF zeigt 5524 auf Gruppenebene und 259 punktbezogene Hinweise ohne eigenen Strecken-/Stationsbezug; beim Import/anschließenden Katalogtest gezielt beobachten, nicht vorab als Fehler umbauen.
- Nächster Klickpfad: MPB setzen → auf Gruppen übernehmen → Import starten → Ergebnis-Screenshots sichern.

## REF-DATA-003 – Frischer Pasing/MPB-Import Checkpoint 30.09.2026
- Nach komplettem Survey-Reset wurden die Daten frisch importiert.
- Ergebnis: 40 importiert, 0 Duplikate, 0 übersprungen, 0 Fehler.
- GLOBAL + MPB – München-Pasing Bbf ist in den Importergebnissen sichtbar.
- WVB 10-11, 14-22 und 19-20 sind enthalten; keine offenen Prüffälle.
- Originaltabelle zeigt Daten nach `Liste aktualisieren`; unmittelbare Leerdarstellung nach Import ist nur noch als UI-Refresh-Beobachtung offen.
- Nächster Schritt: im API-Terminal WVB-object_key/axis_id/Segmentzahl des frischen Imports read-only prüfen; danach API-Suche/Verfügbarkeit/Geometrie.

## REF-DATA-003 – Frischer WVB-Readback Checkpoint 30.09.2026
- Read-only-Abfrage nach frischem 40/40-Import bestätigt 10-11 und 14-22 direkt korrekt getrennt (je eigene Achse, passender object_key, MPB, Segmentzahl 1).
- 19-20 ist im Screenshot nicht sichtbar; daher bewusst noch nicht als bestätigt markieren.
- Nächster Terminalschritt: kompakte Query nur auf WVB-TRA, damit alle drei Fälle in drei Zeilen sichtbar sind.

## REF-DATA-003 – WVB-Neuimport vollständig bestätigt 30.09.2026
- Kompakte 3-Zeilen-Abfrage zeigt 10-11, 14-22 und 19-20 jeweils mit unterschiedlicher axis_id, passendem object_key, MPB und axis_segment_count=1.
- Frischer Neuimport ist damit ohne Nachreparatur WVB-sauber.
- Nächster Schritt: Search/Availability/Geometry kompakt im API-Container testen; danach Multi-Hit im Portal.

## REF-DATA-003 – MPB API-Runtime Checkpoint 30.09.2026
- Search MPB: 20 Achsen, 40 Assets, GRA/GVP/TRA; WVB korrekt sichtbar.
- Availability MPB: AVAILABLE, 20 Achsen, 21 aktive Segmente, 0 Review; KM-Qualität 17 FEHLT / 4 AUTOMATISCH.
- Geometry MPB: 21 Segmente erfolgreich, inklusive aller WVB.
- Str 5524-1 erscheint in Geometry zweimal mit derselben axis_id und erklärt mindestens einen Teil der Achse/Segment-Differenz; erst Quellen read-only prüfen, nicht umbauen.
- Nächster Schritt: kompakte KM-Qualitätsausgabe pro Achse + Mehrsegment-Quellen; danach Portal-Multi-Hit.

## REF-DATA-003 – Pasing-KM-Fachentscheidung 30.09.2026
- Nutzer stellt klar: Pasing besitzt keine richtige/belastbare Kilometrierung.
- Deshalb sind rote/FEHLT-KM-Segmente dort korrekt, bis das Handy den gültigen Start-/Endbereich und die KM-Werte manuell festlegt.
- Keine automatische Server-KM-Reparatur für diesen Bestand bauen.
- Nächster Server-/Portal-Schritt bleibt Multi-Hit; manueller KM-Sync ist danach ein Android↔Server-Folgeschritt.

## REF-DATA-004 – MGHN in Analyse gesetzt 30.09.2026
- Screenshot bestätigt `MGHN – Gessertshausen` korrekt in den Analysegruppen.
- 5302L/302 und 5302R/303 sind sichtbar vorbelegt.
- Bestehender fehlzugeordneter Import bleibt vorerst unangetastet; kein erneuter Duplikatimport.
- Nächster Schritt: read-only Null-Station-Bestand im API-Terminal auflisten, danach gezielte Korrektur nur für Gessertshausen planen.

## REF-DATA-005 – Entscheidung Vollreset + Korrekturworkflow 30.09.2026
- Nutzer möchte jetzt nochmals alle Survey-Testdaten löschen.
- Für die Zukunft soll ein vergessenes Importfeld nicht mehr zum globalen Reset zwingen.
- Ziel: vorhandene identische Datei erkennen, fachliche Abweichung anzeigen und nach ausdrücklicher Bestätigung die vorhandene Zuordnung aktualisieren statt ein zweites Original anzulegen.

## REF-DATA-005 – Duplicate-Korrektur Sourcecheckpoint 30.09.2026
- Fachentscheidung zuerst dokumentiert: aktueller Bestand darf komplett resettiert werden; künftig kein globaler Reset nur wegen vergessenem Bahnhof.
- Umsetzung gebaut: Warnung bei gleicher Raw-Datei + anderer Zuordnung; bewusster Checkbox-/Bestätigungsweg aktualisiert bestehendes Original in-place.
- Raw-Datei/SHA/Original-ID bleiben unverändert; Katalogachse/Asset werden versioniert nachgezogen.
- Runtime noch offen. Nach Deploy gezielt erst Warnlauf ohne Haken, danach bestätigten Korrekturlauf testen.

## REF-DATA-005 – Warnbox-Sichtbarkeit verbessert 02.10.2026
- Nutzerwunsch umgesetzt: Bestätigungsbereich für vorhandene/abweichende Daten jetzt mit deutlich rotem Rahmen und Warnhintergrund.
- Keine Änderung an Overwrite-Logik; nur Sichtbarkeit/Bedienbarkeit verbessert.
- Runtime nach Template-Deploy noch offen.

## REF-DATA-005 – Korrekturlauf Runtime 02.10.2026
- Screenshot nach Bestätigung zeigt mehrere `AKTUALISIERT`-Ergebnisse; vorhandenes Original beibehalten, Zuordnung auf MGHN – Gessertshausen aktualisiert.
- Im selben Lauf bleibt `5340S_ER0.TRA` `DUPLIKAT`, also keine unnötige Änderung bei identischer Zuordnung.
- Vollständiger Nachweis noch offen: ID/SHA unverändert, keine zweite Originalzeile, MGHN in Search/Availability/Geometry, Audit/History.

## REF-DATA-005 – Auditnachweis Checkpoint 02.10.2026
- API-Terminal-Screenshot bestätigt Original- und Katalog-Audit der Korrektur (None → MGHN/Gessertshausen, source ADMIN_WEB_DUPLICATE_CORRECTION).
- Einzigartigkeitsaussage noch nicht aus sichtbarem Screenshot ableiten; dazu folgt eine kompakte Originalabfrage.

## REF-DATA-005 – In-place-Korrektur technisch bestätigt 02.10.2026
- Runtimequery: 16 Korrekturupdates; IDs existieren weiter, SHA gleich, Rohoriginal eindeutig, station_code überall MGHN.
- MGHN Geometry liefert 12 Segmente; Availability sichtbar mit 0 Review und 10 FEHLT / 2 AUTOMATISCH.
- Search-/Availability-Gesamtkopf im Screenshot abgeschnitten; nur noch kompakte Abschlussabfrage nötig.

## REF-DATA-005 – MGHN API-Abschluss 02.10.2026
- Search MGHN: 12 Achsen, Station Gessertshausen, 16 Assets (GRA/TRA).
- Availability: AVAILABLE, 12 aktive von 12 Achsen, 0 Review, KM 10 FEHLT / 2 AUTOMATISCH.
- Geometry zuvor 12 Segmente.
- Funktionaler Korrekturworkflow damit bestätigt; nur Warnrahmen-Sichtbarkeit als kosmetischer Restpunkt offen.

## REF-DATA-006 – Gesamtansicht Deutschland 02.10.2026
- Nutzerwunsch gesichert: nach dem aktuellen Gessertshausen-KM-Test eine grafische Gesamtansicht mit Suche bauen, damit sichtbar wird, welche Strecken/Bahnhöfe in Deutschland bereits Daten haben.
- Vor echter Kartenpositionierung zuerst Koordinatenreferenz der vorhandenen Survey-X/Y-Werte verifizieren.

## REF-DATA-006 – MGHN KM-Teilcheck 02.10.2026
- Sichtbare W/WVB-Zeilen sind erwartungsgemäß ROT/FEHLT ohne KM-Quelle.
- Die 2 AUTOMATISCH-Zeilen sind im Screenshot abgeschnitten; nächste Query filtert nur diese beiden.
- Danach CRS/BBox MPB↔MGHN prüfen und erst dann Deutschland-Gesamtgrafik bauen.

## REF-DATA-006 – Deutschland-Gesamtansicht Sourcecheckpoint 02.10.2026
- MGHN Gl 302/303 als AUTOMATISCH/GRÜN/GRA_KM bestätigt; MPB/MGHN DB_REF2016 Zone 4 bestätigt.
- Neue Admin-Gesamtansicht implementiert: lokale Deutschland-SVG, Coverage-API, DB_REF-Zonen 2–5 → WGS84, Suche und Qualitätsfarben.
- Neue Python-Abhängigkeit pyproj; deshalb Synology-Image neu bauen, nicht nur Template kopieren.
- Runtime/E2E noch offen.

## REF-DATA-006 – Deploymentfehler pyproj 02.10.2026
- API-Protokoll reproduziert `ModuleNotFoundError: No module named 'pyproj'`.
- GitHub requirements + Dockerfile sind korrekt; Runtime läuft noch mit altem Image.
- Nichts am Code/DB ändern. Auf Synology requirements prüfen und Image/Projekt neu bauen, nicht nur Container neu starten.

## REF-DATA-006 – Synology Image-Reuse bestätigt 02.10.2026
- requirements auf NAS korrekt mit pyproj 3.7.2.
- Stop + Projekt Erstellen reicht nicht; API startet kurz und endet erneut mit fehlendem pyproj.
- Nächster Weg im Container Manager: Projekt stoppen → lokales Image `tb-helfer-server-v1:local` entfernen → Projekt Erstellen, damit Image wirklich neu gebaut wird.

## REF-DATA-006 – Synology-Projekt nach Bereinigung 02.10.2026
- Sichtbar sind nur cloudflared, db und restore-test; api/worker/migrate/monitor fehlen.
- GitHub-Compose passt dazu nicht. Nächster sicherer Schritt: Tab `YAML-Konfigurationen` öffnen und aktive Projekt-YAML prüfen, bevor noch etwas gelöscht/geändert wird.

## REF-DATA-006 – Aktive Synology-YAML bekannt 02.10.2026
- Synology-Projekt nutzt eine erweiterte Compose-Datei mit restore-test-db, cloudflared, Live-Source-Mounts und API-Memory-Limit.
- Diese Erweiterungen fehlen im aktuellen GitHub-Compose; nicht überschreiben.
- Nur db/cloudflared/restore-test-db wurden nach Erstellen angelegt. Nächster sicherer Schritt: Build-/Projektprotokoll des Erstellvorgangs lesen; keine weiteren Löschungen.

## REF-DATA-006 – Buildfenster nach Image-Löschung 02.10.2026
- worker/monitor/migrate/api: Pulling → Warning sichtbar.
- Noch kein echter Buildfehler; bei lokalem Image ist Pull-Warnung plausibel. Erst weitere Zeilen/Endstatus auswerten.
- Vorgang laufen lassen, nichts weiter löschen.

## REF-DATA-006 – Rollbackreserve vorhanden 02.10.2026
- Nutzer hat das alte funktionierende Server-Image gesichert.
- Wegen Live-Source-Mounts ist für echten Rollback zusätzlich der passende alte Server-Source nötig; altes Image allein würde mit aktuellem pyproj-importierenden Source erneut scheitern.
- Alt-Image unbedingt unverändert aufbewahren.

## REF-DATA-006 – API stürzt erneut ab 02.10.2026
- Log bestätigt erneut `ModuleNotFoundError: pyproj` aus live gemountetem `survey_catalog.py`.
- Alt-Image allein reicht wegen Live-Source-Mounts nicht.
- Nächster Schritt: nur REF-DATA-006-Source auf pre-pyproj zurückrollen, DB unangetastet lassen, dann Runtime wieder hochfahren.

## REF-DATA-006 – Rollback-Checkpoint 02.10.2026
- API-Crash durch live gemounteten pyproj-Import bestätigt.
- REF-DATA-006-Code in GitHub vollständig auf Commit aef0624 (pre-pyproj) zurückgesetzt; neue Overview-Datei/Check entfernt.
- DB und Survey-Daten nicht verändert.
- Jetzt Synology-Source aktualisieren + gesichertes Alt-Image verwenden und zuerst Server-Runtime stabilisieren.

## REF-DATA-006 – Rollback-Runtime Teilwiederherstellung 02.10.2026
- API-Terminal wieder erreichbar.
- Migrationen 19/19, pending=[] bestätigt.
- Nächste Checks: health/ready, Worker/Monitor, Admin/Portal.

## REF-DATA-006 – Rollback abgeschlossen / Server wieder stabil 02.10.2026
- Health ready: status ok, database up, migrations ok, Server 1.0.0-dev.
- Migrationen 19/19, pending=[].
- Nutzer bestätigt: alles läuft wieder.
- Deutschlandübersicht bleibt bewusst zurückgerollt und wird später neu angegangen.

## REF-DATA-006 – PostGIS-Deutschlandübersicht Sourcecheckpoint 02.10.2026
- Runtime-Probe PostGIS/SRIDs/Transformation war grün.
- Variante A jetzt source-seitig gebaut: Summary-Coverage + Suche + Detail-Lazy-Load, lokale Deutschlandgrafik, Qualitätsfarben.
- Keine requirements-/Image-/Migrationsänderung, kein pyproj.
- Runtime auf Synology noch offen; zuerst Health schützen, dann Übersicht testen.

## REF-DATA-006 – Gesamtansicht „alles zeigen“ 02.10.2026
- Nutzer meldete: Deutschlandansicht zeigte nur wenige Punkte statt des gesamten vorhandenen Bestands.
- Ursache: Summary lieferte nur Achsen-Center/BBox, Detailgeometrie erst nach Klick.
- Korrigiert: alle aktiven Achsen bekommen vereinfachte `overview_segments` und werden als Linien gerendert; Detail bleibt lazy.
- Runtime-Bestätigung noch offen.

## REF-DATA-006 – Runtime nur teilweise passend 02.10.2026
- Alle-Achsen-Patch läuft grundsätzlich, Ergebnis laut Nutzer aber „nicht so richtig“.
- Nichts weiter umbauen, bis die konkrete visuelle/UX-Abweichung per Screenshot eingegrenzt ist.

## REF-DATA-006 – Coverage NetworkError 02.10.2026
- Seite selbst lädt, aber Gesamtansicht/KPI nicht: Browser meldet NetworkError beim Coverage-Fetch.
- Fehler jetzt auf /api/v1/survey-catalog/coverage bzw. dessen Runtime eingegrenzt.
- Nächster Schritt: direkte read-only Funktionsausführung im API-Terminal, keine weitere Änderung auf Verdacht.


## Chat-Checkpoint 02.10.2026 – REF-DATA-007
- Benutzer hat Zielbild bestätigt: Trassendatenübersicht als Zentrale; Dashboard mit Gesamt-km/KM-Abdeckung/Bahnhöfen/Gleisen; Handy-KM ausdrücklich berücksichtigen.
- Android-Quellaudit: AXIS-Sync enthält Sample-`km_meter/km_quelle`; alte `MANUELL_2PUNKT`-Werte können darin laufen. Neuere manuelle Abschnittsobjekte werden bisher nicht als First-Class-Objekte synchronisiert.
- Vertrag fest: fertige mobile Abschnitte → bestehender Survey-`manual-segments`-Pfad, Raw-Original unverändert.
- Server SOURCE gebaut: Summary-Endpunkt, neue Dashboard-/Übersicht-KPIs, Lazy-Initialload; Source-Markercheck 16/16 PASS.
- Noch nicht deployt, keine neuen Synology-/Browser-/Handy-E2Es.
- Für nächsten Chat/Block zuerst GitHub prüfen. Dann Synology-Gate dieses Blocks durchführen, bevor Detailverwaltung weitergebaut wird.


## Chat-Checkpoint 02.10.2026 – Summary-Runtime
- Synology/API-Container läuft mit neuem Summary-Quellstand.
- Read-only Helper erfolgreich: 39 Achsen, 40 aktive Segmente, 2 Betriebsstellen, 7 Strecken, 14 Gleise, 4 Weichen, 7 WVB.
- 128.911 km Gesamt-Trassenlänge; 116.579 km belastbar/automatisch; 12.332 km ohne belastbare KM; manuell/Handy 0 km.
- Nächster Schritt: Browser-E2E Dashboard + Trassendatenübersicht und Ladezeit prüfen.


## SERVER-OPT-001 – Ladeprüfung verbindlich / Diagnose begonnen 02.10.2026
- Benutzer bestätigt Bestandsansicht/Detailfenster/Bearbeiten und fordert ausdrücklich tatsächliches Laden ohne lange Wartezeit. Auftrag gesichert in Commit c79733e7cfe9d0cd9fa489fc36f6dbbd5921b147.
- Read-only Quellprüfung: Summary-Route steht vor Coverage-Detailroute; require_admin verwendet separate Admin-Session; tbApi sendet same-origin-Credentials und Admin-Scope. Kein belegter Routing-/Cookie-Fix aus diesen Befunden ableitbar.
- _coverage_summary_data berechnet Kennzahlen pro Anfrage über Achsen/Segmente; tbApi enthält keinen eigenen Abort-/Timeout-Mechanismus. Das sind Optimierungs-/Robustheitsprüfpunkte, keine nachgewiesene Ursache des Browserfehlers.
- Keine Code-/NAS-/Android-Änderung. Keine neue authentifizierte HTTP-Messung oder Synology-/Browser-Abnahme.
- Lokales Postman-CLI nicht vorhanden; npm-Installationsversuch ohne Abschluss abgebrochen. Kein API-Request ausgeführt, keine Postman-Testergebnisse behaupten.
- Nächster notwendiger Runtime-Nachweis: /api/v1/survey-catalog/coverage/summary im Browser mit bestehender Admin-Anmeldung auf www.tb-helfer.de aufrufen; Status/Response/Ladezeit und bei Fehler korrespondierenden Serverlog erfassen. Keine Passwörter/Cookies/Tokens in den Chat kopieren.


## REF-DATA-007 – Browser-Timeout 524 am 02.10.2026
- Benutzerbeleg: /api/v1/survey-catalog/coverage/summary zeigt Cloudflare 524, Zeit 15:54:39 UTC / 17:54:39 Europe/Berlin.
- Status: **HTTP-/BROWSER-E2E FAIL / ROOT CAUSE OFFEN**. Browser und Cloudflare laut Fehlerseite erreichbar; Origin-Antwort nicht rechtzeitig erhalten. Tatsächliche Requestdauer nicht gemessen.
- Bisheriger Summary-Helper-PASS bleibt Funktionsnachweis, ist ohne Zeitmessung kein Performance-PASS.
- Nächster Nachweis: read-only Summary im API-Container mit perf_counter und faulthandler (30-Sekunden-Grenze für separaten Diagnoseprozess). Danach Befund-abhängig Berechnung/DB-Wartezeit oder HTTP/Auth/Proxy weiter eingrenzen.
- Keine Code-/NAS-/Cloudflare-/Android-Änderung und keine neue erfolgreiche Laufzeitmessung in diesem Chatblock.

Diagnoseaufruf im API-Container (keine Zusatzpakete, keine Datenänderung):

```bash
python -u - <<'PY'
import faulthandler
from time import perf_counter

faulthandler.dump_traceback_later(30, exit=True)
print("Diagnose startet; nach 30 Sekunden ggf. Stack und Abbruch.", flush=True)
from tbserver.survey_catalog import _coverage_summary_data
start = perf_counter()
try:
    result = _coverage_summary_data()
    print("SUMMARY_SEKUNDEN:", round(perf_counter() - start, 3))
    print({k: result.get(k) for k in ("axis_count", "active_segment_count", "total_length_m")})
finally:
    faulthandler.cancel_dump_traceback_later()
PY
```

Die 30-Sekunden-Grenze beendet nur den separat gestarteten Diagnose-Pythonprozess, nicht den laufenden API-Dienst. Bei erfolgreichem schnellen Helper weiterhin HTTP-Pfad separat untersuchen; dieser Aufruf ist selbst kein HTTP-/Browser-E2E.


## REF-DATA-007 – GVP-Index fertig im Quellstand / Deploy als Nächstes 02.10.2026
- Ausgangs-SERVER 16ebdcc5fe8371f4aaf96bb05b74941025d71434; Android 423f37892915cb3308a4b1ea9e4d196faa872326, unverändert.
- Runtime-Befund vor Implementierung in 5847bcc3440c029e1e57c24e7a383c093b975617 gesichert: 30-s-Abbruch direkt in GVP-Projektion.
- Runtime-Datei geändert: server/tbserver/survey_catalog.py. Neuer Test: server/checks/118_verify_gvp_projection_performance.py.
- Index erhält exakte Projektion/KM-/Farblogik und beschleunigt Kandidatensuche; kein Cache veralteter Geometrie, keine neue Abhängigkeit/Migration.
- Lokal elf Tests PASS; Benchmark 20.001 Samples/200 GVP: 4,876853 s → 0,053314 s inkl. Aufbau, exakt gleiche Ergebnisse. Syntax und AST-Abgrenzung PASS.
- Noch nicht auf Synology deployed. Nur survey_catalog.py per GitHub Desktop/File Station übernehmen, API neu starten; denselben begrenzten Helper-Test wiederholen, dann HTTP-/Browserprüfung. Kein erneutes httpx-/pyproj-Installieren.
- Restlicher UI-/Bearbeiten-/Archiv-Umbau noch offen. Server→Portal→Android-Reihenfolge bleibt maßgeblich.


## REF-DATA-007 – Benutzerrückmeldung zum Deployment 02.10.2026, 18:19 Europe/Berlin
- Benutzer meldet nach der Anleitung für GitHub-Pull, Dateiübernahme, API-Neustart und Diagnosenachtest: „okay erledigt“.
- Als Rückmeldung zur Durchführung gesichert; konkrete Schritte nicht einzeln belegt. Keine Terminalausgabe/Laufzeit und keine Browserantwort mitgeliefert.
- Quellfix 687f77a33daaa100e10556300761e91ac717480f bleibt maßgeblich. Runtime-Datei: server/tbserver/survey_catalog.py.
- Status: Durchführung vom Benutzer gemeldet / Runtime-Messwerte und HTTP-/Browser-Abnahme weiter OFFEN; kein Leistungs-PASS allein aus „erledigt“ ableiten.
- Als Nächstes Ausgabe des bereits bereitgestellten Diagnosebefehls (SUMMARY_SEKUNDEN und Kennzahlen) sowie authentifizierten /coverage/summary-Aufruf prüfen.


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


## REF-DATA-008 – Karten-/Detailverwaltung freigegeben (02.10.2026 18:45 Europe/Berlin)
- Benutzer bestätigt den vorgeschlagenen Umbau mit „okay“: große bestandsbegrenzte Karte, progressiver Detailzoom, Suche und Einpassen; überlagernde Details für Bahnhof/Strecke/Achse mit Mehrfachtrefferauswahl; danach bewusstes Bearbeiten und Papierkorb/Wiederherstellung ohne stilles Mitlöschen zugeordneter Objekte.
- Erster atomarer Implementierungs-/Deploymentblock: Karte und lesende Detailfenster inkl. begrenzter Ladezeit, Wiederholen, Schutz vor verspäteten Antworten. Verfügbare Original-/Historieninformationen über bestehende API laden. Noch keine schreibenden UI-Aktionen vortäuschen.
- Bestehender GVP-Fix und bestätigte ca. 2 Sekunden Runtime bleiben Vergleichsbasis. Originale und Android-Code unverändert. Nach Quell-/Browserprüfung ist ein separater Synology-Nachtest erforderlich; Bearbeiten/Archiv bleiben anschließend beauftragte Folgeblöcke.


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


## REF-DATA-008 – Android-Transportaudit zur Weichenklärung (02.10.2026)
- Read-only geprüft: Android-HEAD 423f37892915cb3308a4b1ea9e4d196faa872326, PR #11 offen, nicht Draft; SERVER-Ausgang d1399a4e5bdd143b5373e25253692407e5eb044e, Draft-PR #6.
- WorksiteTrackDataSync: LEAD baut AXIS-/STRECKENPUNKTE-Datensätze aus lokalen Fachdaten, Helfer lesen baustellenbezogenen Server-Snapshot. AXIS enthält name, achs_typ, quelle_datei, datei_signatur, station_von/bis, Samples mit station/km_meter/km_quelle/x_db_ref/y_db_ref/lat/lon/hoehe_m/richtung_deg. Kein eigener object_key in diesem AXIS-Serializer und kein Stamm-/Abzweig-/Weichengrenzenmodell in TrassenAchseEntity.
- WorksiteTrackDataApi: GET/POST /api/v1/worksites/{id}/track-data und PATCH/DELETE /api/v1/track-data/{id}, mit Versions-/Mutation-ID-Abgleich. Dieser Pfad überträgt abgeleitete Daten, keine Roh-TRA.
- Der neue globale Survey-Katalog mit object_key/axis_id/Segmentversionen ist ein gesonderter Vertrag und durch die neue Admin-Karte nicht automatisch als Android-Integration fertig. Globaler Manifest-/Parsed-/Such-/Geometry-/Changes-Vertrag bereits serverseitig vorhanden; vollständige mobile Kataloganbindung bleibt ANDROID-Aufgabe. Keine neue Geräte-E2E-Bestätigung.
- Bestehender manueller Abschnittsvertrag MANUAL_TRA_SECTION bleibt unverändert. Für eine spätere Weichentopologie wären explizite, belegte Stamm-/Abzweigzuordnung und Grenzen erforderlich; kein neuer API-Vertrag oder Migration in diesem Block festgelegt.


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


## REF-DATA-010/011 – Präzisierung und nächster Serverblock (02.10.2026)
- Nutzer bestätigt Neuanlageablauf am Beispiel MPB/Weiche 239: zuerst WA, WE und WEZ auf vorhandenen Achsen setzen, danach gemeinsam als neue Weiche benennen/zuordnen. Keine vorher vorhandene Weichenidentität voraussetzen. WA→WE Stammgleis, WA→WEZ Zweiggleis; bei Mehrfachtreffern bewusste Achsauswahl. Ohne eindeutige Achsen bleiben Punkte als manuelle Erfassung mit offener Zuordnung erhalten. Server/Portal zuerst, Android später.
- Nächster freigegebener Serverblock: nachgewiesenen OVERLAP→SUPERSEDED-Fehler für MANUAL_TRA_SECTION korrigieren und Regression prüfen, bevor der manuelle KM-Editor freigeschaltet wird. Manuelle TRA-Ergänzung ist eine zusätzliche KM-Zuordnung auf unveränderter Originalgeometrie, kein zu verdrängender GPS-Geometrieersatz. Auch späterer Originalimport darf diese Ergänzung nicht allein wegen geometrischer Überdeckung deaktivieren. MANUAL_GPS-Priorität bleibt erhalten.
- Vor UI-Freigabe auch Summary-/Überlappungszählung und gültige Abschnittsgrenzen prüfen; keine Freigabe allein aufgrund einer Statuskorrektur. Keine Altbestandsreaktivierung auf Verdacht. Vertrags-/Runtime-Status nach Prüfung getrennt dokumentieren.


## REF-DATA-011 – Manueller TRA-KM-Overlay: Quellfix und Regression (02.10.2026)
- Status: QUELLSTAND FERTIG / SYNOLOGY-DEPLOY UND HTTP-/DB-E2E OFFEN. Kein manueller KM-Editor in dieser Änderung freigeschaltet; dessen Eingabe-/Grenzen-/Berechtigungspfad bleibt nächster UI-Block. Neuanlage Weiche über WA/WE/WEZ→Nummer/Bahnhof als Zielablauf gesichert, noch nicht implementiert.
- create_manual_segment: MANUAL_TRA_SECTION bleibt bei bestätigter geometrischer Überlappung ACTIVE. Original und KM-Zuordnung bestehen nebeneinander. index_original_in_tx verdrängt bei Originalimport nur überdeckte MANUAL_GPS-Geometrie, keine MANUAL_TRA_SECTION-KM-Ergänzung. Bestehende GPS- und neuere-Original-Priorität unverändert. Kein Eingriff in alte SUPERSEDED-Datensätze.
- Summary: manuelle Abschnitte auf derselben aktiven Originalquelle werden innerhalb der Originalstationsgrenzen als Intervallvereinigung gezählt. Überlappungen/identische/verschachtelte Abschnitte zählen nicht mehrfach. Nur Qualität MANUELL darf Basislänge umklassifizieren; ohne belastbare Stationsüberlappung keine geschätzte Umklassifizierung. Unzugeordnete Abschnitte behalten ihre tatsächlich berechnete Qualitätsklasse statt pauschal MANUELL zu werden. Keine globale Deduplizierung fremder Originalquellen/Parallelgleise behaupten; widersprüchliche KM-Anker verschiedener Ergänzungen werden dadurch noch nicht fachlich aufgelöst.
- GRA-Kontext zählt MANUAL_TRA_SECTION nicht als zusätzliche Grundgeometrie; aktive KM-Overlays schalten die bestehende Einzeloriginal-GRA-Zuordnung nicht ab.
- Reproduktion vor Fix: Create und späterer Import deaktivieren jeweils TRA-KM-Overlay; Summary 0..100 + 50..150 ergibt 200 statt 150 m, FEHLT-Overlay ergibt 100 m manuell. Nach Fix: 16 Tests in server/checks/122_verify_manual_overlay.py PASS (echte Produktionsfunktionen per AST, Cursor-/Geometrie-Doubles; Status, Replay, Review, Quellen/KM-Erhalt, Original-/GPS-Priorität, Intervallgrenzen/Vereinigung, fehlende KM, Mobile-Zählung, GRA-Kontext). Bestehende 12 Management- und 6 Detailtests PASS; Python-Syntax PASS. Keine Behauptung eines echten HTTP-/PostgreSQL-/Postman-/NAS-/Geräte-E2E; kein neuer visueller UI-Test erforderlich, Template unverändert.
- Runtime-Datei nur server/tbserver/survey_catalog.py. Keine Migration/neue Abhängigkeit/app/**-Änderung. GitHub Desktop→File Station in bestehenden tbserver-Mount übernehmen, API-Container neu starten. Ohne aktive manuelle Ergänzungen müssen bestehende Summary-Zahlen unverändert bleiben. Vollständiger manueller Schreib-/Änderungs-/Lese-Nachtest folgt mit dem Editor; Originale geschützt.


## REF-DATA-012 – Admin-Editor für manuelle KM-Abschnitte (04.10.2026)
- Auftrag: nächsten offenen Block umsetzen. Achsdetail erhält manuellen KM-Editor auf einem ausdrücklich gewählten aktiven ORIGINAL_TRA-Abschnitt. Eingaben: Achsposition Anfang/Ende in Metern, zugehörige Strecken-KM, Notiz; fallende KM zulässig, Achspositionen aufsteigend. Vorschau vor Speichern; Rohoriginale unverändert. Gespeicherte manuelle Abschnitte bearbeitbar/deaktivierbar, Historie bleibt.
- Additiver Admin-Vertrag: POST /api/v1/survey-catalog/management/axes/{axis_id}/manual-km/preview (nur prüfen), POST .../manual-km (anlegen/ändern) und POST .../manual-km/{segment_id}/deactivate. Admin-Session + CSRF. Payload mit Achs-base_version, source_segment_id/source_version, station_from_m/station_to_m, km_from_m/km_to_m, optional segment_id/segment_version für Änderung, note und client_change_id beim Speichern. Keine geometrische Neuzuordnung anhand Nähe; persistiert MANUAL_TRA_SECTION auf ausgewählter Achse/Originalquelle.
- Grenzen: endliche Werte, positive Abschnittslänge innerhalb der Quellstationierung, unterschiedliche KM-Endwerte. Vorschau zeigt Länge und KM-Differenz; auffällige Abweichung wird vor Speicherung sichtbar und erfordert bewusste Bestätigung. Widersprechende aktive manuelle KM-Zuordnungen derselben Originalquelle in gemeinsamer Stationsspanne blockieren (409); konsistente Überlappung bleibt zulässig. Sperren/Versionsprüfung und wiederholbare Mutation über Receipt; bei unsicherem Browser-Schreibausgang keine automatische Wiederholung.
- Geometrie des Abschnitts muss an exakten Stationsgrenzen aus verdichteter Originalachse interpoliert werden; ungültiger/leerer Ausschnitt darf nicht auf Vollachse zurückfallen. Originale bleiben unverändert. Map/Details/Summary nach Erfolg neu laden. Keine Android-Dateien, keine neue Weichenerfassung in diesem Block.


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
- Rolle SERVER. Livebasis vor Umsetzung: SERVERc4e7524 (vertraglicher Vorabcommit, PR6 offen/draft); ANDROID423f378 (PR11 offen/nicht draft). Keine app/**-Änderung, kein Merge/Deployment/NAS-Zugriff.
- REF-DATA-015-Gesamtauftrag im vorliegenden Runtime-/Dokumentationscommit umgesetzt. Neu: survey_workspace.py, Migration020, Checks125/126, Übergabedokument. Geändert: survey_catalog.py, admin_survey_import.py, Übersichtstemplate, Node119 und Projektsteuerung01/02/04/05/06/07/08.
- 80 Backend-Tests + Node119 + Syntax PASS. Original5302S zeigt16497 Punkte, kein vollständiges Wegfallen mehr. Runtime nicht auf NAS installiert, keine echten Landesdienstbilder/Browser-/DB-E2E. Bestehende Benutzerabnahmen schützen.
- Nächster Arbeitsschritt nach GitHub Desktop/File Station: Migration020 im API-Container, API/Worker-Neustart, Strg+F5 und dokumentierter Praxistest. Originaldateien, .env und Datenverzeichnisse nicht überschreiben. Keine Wiederholung des allgemeinen Testdaten-Löschers.

Details, API-Vertrag, Quellen, Übernahme und Abnahmefälle: [REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md](uebergaben/REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md).


### GitHub-Sicherung REF-DATA-015 verifiziert
- Runtime-/Dokumentationscommit: `11eb92076c21d422723d8d9c58d110202f822d18` auf `feature/tb-server-1.0`; PR6 weiterhin offen und Draft, nicht gemergt.
- Neu geladenes survey_workspace.py, Übersichtstemplate und Migration020 stimmen byteinhaltlich mit dem geprüften lokalen Stand überein. ANDROID bleibt `423f37892915cb3308a4b1ea9e4d196faa872326`, PR11 offen/nicht Draft.
- Diese nachfolgende Ergänzung ist nur ein Checkpoint. Keine neue NAS-/DB-/Browser-Abnahme, keine weitere Runtime-Änderung.


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


### REF-DATA-016 GitHub-Sicherung verifiziert
- Runtime-/Dokumentationscommit: `356a876f41e2e48256ed73d468f25f8686a34674`. Neue Netzdatei, Template und Migration021 remote byteinhaltlich mit geprüftem lokalen Stand verglichen: identisch.
- PR6 weiterhin offen/Draft, kein Merge. Android HEAD423f37892915cb3308a4b1ea9e4d196faa872326 und PR11 unverändert. Keine NAS-Änderung, keine behauptete Geräte-/DB-/Browser-E2E.
- Dieser anschließende Commit ergänzt ausschließlich den Checkpoint. Übergabe umfasst sieben Runtime-Dateien und Migration21; siehe REF-DATA-016_ANSCHLUSSNETZ_SYNC.md.


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
- Prüfungen: Node119 PASS (Produktions-JS, DOM/Transport-Doubles einschließlich neuer Such-/Filter-/Reload-/Linkfälle und aller WA/WE/WEZ-Regressionen); Python127 21 Tests PASS (Storage-/Transaktions-Doubles, Linkkonflikt/Alte-Clients/Legacy-Receipt-Fingerprint/Globalpaging/Archiv-Restore); Python128 2 Tests PASS (Produktions-Lesehelfer, SQL-Cursor-Doubles); Python-/JS-Syntax PASS. Kein Live-FastAPI/PostgreSQL/PostGIS, kein visueller Browser-/Jinja-Rendernachweis, kein NAS-/Geräte-E2E. Jinja lokal nicht installiert, Chromium weiterhin nicht vorhanden.
- Runtime7 Dateien: tbserver/survey_catalog.py, tbserver/survey_network.py; templates/v1_base.html, v1_admin_survey_overview.html, v1_admin_routes.html, v1_admin_stations.html, v1_catalog_history.html (jeweils unter server/). Keine neue Migration, weiterhin21/21. Übernahme gemeinsam über GitHub Desktop→File Station in vorhandene Mounts; API neu starten, Browser Strg+F5. Keine Originale/DB/.env überschreiben.
- NAS-Gates offen: Gl999 nach Aktualisieren/Sitzungsneustart in Bahnhof und Suche; WA/WE/WEZ frei/überlagert/vor TRA→Prüfen/Speichern/Wiederöffnen; Anzeige merken; Kataloglink/Stammdatenänderung/Versionskonflikt mit zweitem Browser; Archiv-Restore bei unveränderten Originalen/KM; Paketzeiten beobachten. Gesamt-Admin nicht fertig: Rechte-, Backup/Restore-, Worker-/DB-Stabilitäts- und Lastabnahme sowie Portal-BTB-/Android-Folgearbeiten separat.


## REF-DATA-020 – aktueller Bestand, Kilometerbezug und Weichenfläche (05.10.2026)
- Benutzerfreigabe „okay bauen“ nach fachlichem Vorschlag. Basis SERVER e5aa6cde14048c28f44fb5fe23cd7808f5465b31 / PR6 offen Draft; ANDROID423f378 / PR11 offen, unverändert. Server/Portal zuerst, Android danach.
- Benutzer bestätigt REF019: Gl999 finden, WA/WE speichern und wieder öffnen, Anzeigeauswahl behalten; außerdem Neustart und Datenbestand vorhanden. Keine pauschale Abnahme von Konflikt/Archiv/Last/Android daraus ableiten.
- Aktuelles Gleis ist das stabile Netzobjekt mit begrenztem Verlauf, Anfang/Ende und eigener Kilometerzuordnung. Originalachsen bleiben unverändert als zuschaltbare Grundlage. Bestand erhält Auswahlvorrang. Verlauf über mehrere explizite Originalabschnitte möglich, keine unbelegte Kurve aus zwei Punkten behaupten.
- Optionaler ausdrücklicher Kilometerbezug auf versionierte Originalquelle; ohne Auswahl eigene Punktbindungen verwenden, niemals aus überlagerten Achsen beliebig wählen. Bestehende manuelle Kalibrierungen und belastbare GVP-Zuordnung nutzen, reine GRA-Min/Max ohne punktbezogene Abbildung nicht als exakte Endpunktwerte ausgeben. Außerhalb belegbarer Referenz keine Extrapolation. Verschieben berechnet Werte neu; fehlende/mehrdeutige/veraltete Grundlagen sichtbar statt alter KM am neuen Punkt.
- Optional versionierte Verlaufabschnitte und Positionsstatus; ausgelassene neue Felder durch alte Clients erhalten, explizites null/leer entsprechend dokumentieren. IDs, optimistische Versionen, atomare gemeinsame Punkte, Idempotenz, Historie und Original-/KM-Schutz bleiben.
- Weichen-Auswahlfläche aus beiden Wegen WA→WE und WA→WEZ, jeweils 1,5m seitlicher Breite; gemeinsame äußere Fläche mit WA-Kante3m und aufgeweitetem Endbereich. Darstellung/Erkennung, keine betriebliche Sicherheitsgrenze. W xyz bleibt ohne KM nutzbar.
- Handy-Ziel: Originalimport→gemeinsames Gleis/Weiche→versionierter Deltaabgleich→anderer Nutzer erhält Grenzen/Verlauf/KM samt Herkunft. Keine Android-Implementierung in diesem Serverblock; vorhandene Android-Berechnung read-only vergleichen, später echte Geräte-Abnahme.


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


## REF-DATA-021 – örtliche Referenzauswahl vereinfachen (05.10.2026)
- Benutzer bestätigt REF020-Kilometerbezug funktioniert, meldet aber umständliche lange globale Achsliste. Screenshot enthält unpassende fremde Bahnhöfe. Weichenfläche sichtbar; keine pauschale weitere E2E-Abnahme.
- Auftrag: Referenz-/Verlaufsachslisten standardmäßig auf aktuellen Bahnhof und örtlich passende Strecken begrenzen; bestehende gespeicherte Bezüge bleiben auffindbar. Suchfeld und bewusste Erweiterung auf andere Bahnhöfe/Strecken, begrenzte Ergebnisliste. Örtliche Nähe ist nur Auswahlhilfe, keine automatische fachliche Verknüpfung. Durchgehende Originalstrecken bleiben einmal im gemeinsamen Bestand und dürfen mehreren Bahnhöfen dienen.
- Nach ausdrücklicher Achswahl genau eine vorhandene Originalquelle direkt in den Entwurf übernehmen, mehrere Quellen weiterhin ausdrücklich wählen. Vorschau/Speichern/Versionsschutz bleiben. Nur Servertemplate, kein Android-/API-/DB-Umbau.


## REF-DATA-021 – örtliche Referenzauswahl umgesetzt (05.10.2026)
- Benutzer bestätigt REF020-Kilometerbezug funktioniert; Beanstandung war die globale lange Liste. Keine vollständige weitere E2E-Abnahme ableiten. Basis SERVER e619a7c, Android423f378 unverändert; PR6 Draft, PR11 unverändert.
- Kilometer- und Verlaufsachslisten: aktueller Bahnhof plus Strecken im Objektumfeld (200m als Auswahlhilfe; vorhandene Linien bevorzugt, sonst Übersichtsbounds). Keine automatische fachliche Bahnhof-/KM-Zuordnung durch Nähe. Gemeinsame durchgehende Originalstrecken bleiben unverändert im globalen Bestand und werden nicht je Bahnhof dupliziert.
- Suche nach Name/Bahnhof/Strecke/Gleis, bewusste Erweiterung „Auch andere Bahnhöfe / Strecken suchen“. Max60 Treffer plus erhaltene ausgewählte Referenz; gespeicherte auch außerhalb Such-/Ortsfilter sichtbar. Änderungen an Bahnhof/Punkten aktualisieren Kandidaten. KM-Strecken im Umfeld oben priorisiert.
- Nach ausdrücklicher Achswahl eine einzige vorhandene Originalquelle direkt in Entwurf übernehmen; mehrere Quellen verlangen weiterhin bewusste Wahl, keine Quelle zeigt Hinweis. Prüf-/Speicher-/Versionspfad unverändert; keine automatischen Schreibzugriffe beim Öffnen/Filtern.
- Node119 PASS einschließlich bisheriger Regressionen und neuer Fälle: lokale/fremde Bahnhöfe, nahe/ferne Strecken, globale Suche, bestehende fremde Auswahl erhalten,100 zusätzliche Achsen/60er Begrenzung, einzelne/mehrere Quellen. Tatsächliches TemplateJS mit DOM-/Transport-Doubles; kein visueller Browser/NAS-E2E. Backend unverändert, kein neuer Backendtestlauf behauptet.
- Runtime ausschließlich server/templates/v1_admin_survey_overview.html. Über GitHub Desktop/File Station nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html; API neu starten, StrgF5. Keine neue Migration (21/21), kein Reset/Reindex. NAS-Praxistest der neuen Auswahlliste offen. Android bleibt Folgeblock.


## Betriebsprüfung – Benutzerabnahmen und Folgeauftrag (05.10.2026)
- REF-DATA-021 örtliche Referenzauswahl: Benutzer bestätigt „okay klappt“.
- Netzwerk-Versionskonflikt im Zwei-Fenster-Test: Benutzer bestätigt „funktioniert“; Screenshot zeigt „Zwischenzeitlich geändert. Bitte neu laden und deine Änderung erneut prüfen.“ Keine pauschale Abnahme anderer Konfliktpfade.
- Netzwerk-Deaktivieren/Wiederherstellen einschließlich Anschlüssen/Kilometerbezug nach vorgeschlagenem Test: Benutzer bestätigt am 05.10.2026 14:59 „funktioniert“. Benutzerbericht, kein eigener DB-/HTTP-Nachweis; kein vollständiger Backup-Restore daraus ableiten.
- Folgeauftrag 15:07 „okay“: offene Server-Betriebspunkte prüfen, zuerst Worker-/DB-Stabilität, dann Backup/Wiederherstellung und Firmen-/Benutzerrechte; Portal vor Android. Bestehende Abnahmen schützen.
- Ausgang: Server8e43e1007a66064aaa6e615b55fd861d0a0319e7 / PR6 offen Draft; Android423f37892915cb3308a4b1ea9e4d196faa872326 / PR11 offen. Keine app/**-Änderung, kein NAS-Zugriff vorausgesetzt. Historische Fehlermeldungen benötigen reale Logs zur Ursachenbestimmung.


## OPS-MONITOR-001 – Befund und begrenzter Korrekturauftrag (05.10.2026)
- Source-Audit auf9712fc6: jobs.py enthält bereits Startup-/Loop-/Status-Retries bei DB-Ausfällen. Historischer NAS-Worker-Abbruch weiterhin ohne Laufzeitlogs nicht ursächlich geklärt.
- Reproduzierbarer Monitor-Befund: _record_transitions erkennt Neustart nur an geänderter PID; Container können dieselbe PID wiederverwenden, obwohl started_at wechselt. read_monitor_snapshot bewertet das Alter seines gespeicherten Zustands nicht und kann bei stehendem Monitor altes OK anzeigen.
- Korrektur: Startzeitwechsel als Neustart erkennen, PID-Fallback für alte Heartbeats behalten; veraltete/ungültig datierte Snapshots ausdrücklich als Überwachungsausfall kennzeichnen, Rohprüfwerte erhalten. Keine automatischen Neustarts, keine Datenänderung, keine neue E-Mail-Konfiguration.
- Gezielte verhaltensbasierte Tests mit echten isolierten Monitorfunktionen und Datei-/Zeit-Doubles; NAS-Nachweis getrennt. Backup/Restore und vollständige Rechteabnahme bleiben weitere Blöcke.


## OPS-MONITOR-001 – Überwachungsprüfung und Quellkorrektur (05.10.2026)
- Vorabbefund f0b989b653ca59a5d37311e2d0e0d932c30394a8, Benutzerabnahmen9712fc6. Worker-Neustarterkennung verwendet jetzt started_at (normalisierte Zeit) statt nur PID, mit PID-Fallback für alte Heartbeats.
- read_monitor_snapshot erkennt fehlende/ungültige oder veraltete Zeitangaben sowie erhebliche Zukunftsabweichung. Grenzwert max(120s,3×Monitorintervall); overall ERROR und eigener Monitorcheck, historische Einzelmesswerte erhalten. Systemstatus benennt diese ausdrücklich als letzten bekannten Stand.
- Prüfung: 130_verify_monitor_liveness.py acht Verhaltenstests PASS mit echten AST-isolierten Funktionen und Datei-/Zeit-/Benachrichtigungs-Doubles (keine E-Mail versandt); Python-AST und JavaScript-Syntax PASS. Kein NAS-/HTTP-/DB-/visueller Browser-E2E. Historische Worker-Abbruchursache NICHT bestimmt; jobs.py hat bereits DB-Retries. Dafür Worker-/DB-Containerlogs und Ereigniszeit benötigt.
- Runtimeübernahme nur tbserver/monitor.py und templates/v1_system_status.html aus server/ nach /volume1/docker/tb-helfer-v1; API, Worker und Monitor über Container Manager neu starten (Worker importiert Monitor), Browser StrgF5. Keine Migration, keine Original-/DB-/Storage-/Secretsänderung, kein Android.
- Erste Source-Sichtung backup.py/restore.py: Prüfsummen, separates Restore-Test-DB-Ziel und Archivpfadschutz vorhanden. Kein vollständiger Backupkonsistenz-/Restore-Nachweis; aktuelle Backups praktisch prüfen. permissions.py gesichtet, noch keine vollständige Endpunkt-/Firmenisolationsprüfung. Diese Folgeblöcke sowie Portal/Last/Android bleiben offen.


## OPS-MONITOR-001 – NAS-Protokollauswertung (05.10.2026 15:42 CEST)
- Benutzerdateien tb-helfer-v1-db-1.html (SHA2562688bce8223d69c26cb7b33ef932b791d25235ad5b3c8177cd6ac8d2d556ac6d) und tb-helfer-v1-worker-1(3).html (SHA256678ff443c12d2afd0f9f241d570afc89740468ef555975dfe93e6169788fa286) vollständig textuell ausgewertet; keine Rohlogs/Netzadressen ins Repo kopiert.
- DB am05.10.:12:18:25.683 received fast shutdown request →12:18:30.410 shut down →12:19:00.907 ready. Zweiter Ablauf15:40:06.324 shutdown request →15:40:11.881 shut down →15:40:59.015 ready (CEST).
- Workerfehler12:18:27/32 und15:40:07–17 entsprechen dieser Nichterreichbarkeit (shutting down / Connection refused). Tracebacks werden durch worker_loop/scheduler behandelt; Log sagt ausdrücklich stays alive and retries. Kein unbehandelter Workerprozess-Absturz in diesem Export belegt. Kein Erfolgseintrag nach15:40:59 vorhanden; leeres neues Fehlerlog ist kein vollständiger Nachweis erfolgreicher Jobverarbeitung.
- DB-Export02.–05.10. zeigt weitere geordnete Shutdown/Startfolgen; kein PANIC/Crash-Recovery/Datenträger-voll/OOM-Beleg im vorliegenden Text. Ein Broken pipe / connection to client lost am04.10.13:53:59 zeigt verlorene Clientverbindung, nicht selbst einen DB-Absturz. logical replication launcher exited code1 steht im geordneten DB-Shutdown und bezeichnet keinen TB-Helfer-Worker.
- Einordnung: unmittelbare Ursache dieser Verbindungsfehler ist angeforderter DB-Stopp/Neustart. Auslöser (manueller Container-Manager-Projektneustart, NAS/Systemaktion etc.) aus diesen Logs nicht feststellbar. Ältere ursprüngliche Warnung nur bei gleicher Ereigniszeit damit erklärt; keine pauschale Stabilitäts-/Datenintegritätsabnahme.
- Keine weitere Runtimeänderung aus diesen Belegen nötig. Bei reinem Template-/Pythonupdate nur benötigte API/Worker/Monitor-Container neu starten, nicht DB/gesamtes Projekt, sofern keine andere Deploymentanforderung besteht. Für ungeplante Stopps DSM/Container-Manager-Ereignisse genau zu obigen Zeiten prüfen. Backup/Restore und Firmenrechte bleiben nächste offene Blöcke. Serverbasisdd1f935, Android423f378 unverändert.


## OPS-BACKUP-001 – Befunde vor Umsetzung (05.10.2026)
- Auftrag Benutzer „okay weiter“15:45: Backup-/Restoreprüfung; Basisfb59d3c, Android423f378 unverändert.
- _archive_storage erzeugt bei fehlendem STORAGE_ROOT ein leeres gültiges Archiv: fehlender Mount kann als erfolgreich erscheinen. Künftig fehlenden/nicht-Verzeichnis-Root ablehnen, tatsächlich leeres vorhandenes Verzeichnis zulassen.
- run_backup löscht im Fehlerhandler final_path auch bei Fehler nach _finish_run_ok (z.B.Fortschritts-DB-Ausfall). Fertiges Backup darf durch nachgelagerte Fortschrittsanzeige nicht verloren gehen. Fortschritt in Backup/Restore best effort; bereits fertig veröffentlichte Artefakte bei Registrierungsfehler erhalten (ggf.unregistrierte Datei statt Verlust).
- Backupfilter nimmt Links/Spezialdateien an, Restore lehnt sie ab. Künftig nicht unterstützte Einträge beim Sichern explizit ablehnen; .renditions weiterhin ausschließen.
- Restore-Test scratch-mkdir bisher vor try, kann RUNNING ohne Fehlerabschluss hinterlassen; in Fehlerbehandlung einbeziehen.
- Gezielte Tests mit echten temporären Archiven/Produktionsfunktionen und DB-/Fortschritts-Doubles. Keine Live-Restoreausführung. DB+Dateien werden sequenziell gesichert; vollständiger konsistenter Live-Snapshot und physische Restore-/Firmenrechte-Abnahme dadurch nicht behauptet.


## OPS-BACKUP-001 – Fehlerbehandlung korrigiert, NAS-Restore offen (05.10.2026)
- Vorabbefund264566316111202d2d4f421d290c5951bb3769c7. Runtime nur server/tbserver/backup.py und restore.py; Android unverändert, keine Migration/API-/Formatänderung.
- Fehlender/nicht-Verzeichnis-/Symlink-Storage-Root wird abgelehnt, tatsächlich leeres vorhandenes Verzeichnis bleibt zulässig. Links/Spezialdateien im Archiv werden mit Fehler beendet, statt später nicht wiederherstellbare Sicherung OK zu melden; .renditions unverändert ausgeschlossen.
- Fortschrittscallbacks in Backup/Restore sind best effort und loggen bei Ausfall nur den Exceptiontyp. Sie können erfolgreich abgeschlossene Sicherung/Restore nicht nachträglich scheitern lassen. Fertige Backupdateien bleiben bei Registrierungsfehler erhalten; unregistrierte Artefakte ggf.manuell prüfen. Keine automatische Bereinigung solcher Dateien. Unfertige temporäre Archive werden weiterhin entfernt.
- Restore-Test erfasst auch Fehler beim Anlegen seines temporären Ordners im Fehlerstatus.
- 131_verify_backup_failure_safety.py:11 Tests PASS, echte Archive/temp.Dateien mit Produktionsfunktionen, DB-/Fortschritts-Doubles. Fehlender/leerer Storage, Fortschritts-/Registrierungsfehler, Symlinks/Tempcleanup, FULL-Archiv-Roundtrip mit unverändertem Original und Cacheausschluss, Restorefortschritt/Scratchfehler, Live-DB-Zielschutz, Pfadtraversal und beschädigte Prüfsumme. Python-Syntax PASS. pg_dump war simuliert; kein PostgreSQL-/NAS-/HTTP-E2E, kein Nachweis vollständiger DB-Dateireferenzkonsistenz.
- Offene Grenze: FULL sichert DB und Dateien nacheinander, ohne globalen Schreibsnapshot; parallele Lösch-/Änderungsvorgänge können Konsistenzprüfung erfordern. Praktischen Vollbackup-/Restoretest in ruhigem Zeitfenster durchführen; nicht als produktiv vollständig abgenommen markieren. Wiederherstellung prüft aktuell Kernschema/Archive, nicht jeden Dateiverweis gegen DB. Weiterer Konsistenzblock bleibt offen.
- Übernahme via GitHub Desktop→File Station nach /volume1/docker/tb-helfer-v1/tbserver/backup.py und restore.py; API/Worker neu starten, DB/Monitor dafür nicht erforderlich. Systemstatus→Vollbackup, nach OK→Restore-Test (getrennte konfigurierte Testdatenbank). Fehlt Testkonfiguration, zuerst getrennte Umgebung einrichten, keine Live-DB als Ersatz verwenden. Nächster Nachweis: Status/Fehlercode des echten Laufs. Firmenrechte/Portal/Last/Android weiter offen.
