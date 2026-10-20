# TB-HELFER 2.0 – AKTIVER AUFTRAG

Stand: 2026-09-27

## PS-001 – Projektsteuerung zentralisieren
Status: **UMGESETZT IM QUELLSTAND DIESER STEUERUNGSÄNDERUNG**

Ein gemeinsamer Einstieg unter `projektsteuerung/`; Android/Server-Codebereiche bleiben getrennt; alte Top-Level-Steuerungsordner entfallen; Chat-Sicherung erfolgt ereignisbasiert. Kein `app/**`- oder `server/**`-Fachcode wird durch PS-001 geändert.

## IMG-001 – Bildtransport / Serverlast
Status: **QUELLAUDIT ABGESCHLOSSEN / ABWEICHUNGEN BESTÄTIGT / IMPLEMENTIERUNG NACH E2E-PRIO-001**

Verbindlich:
- Handyoriginal bleibt auf dem Aufnahme-Handy.
- Handy → Server/Portal: verkleinerte/komprimierte Sync-Version + Thumbnail + Metadaten.
- Portal → Handy: kleine Version/Thumbnail.
- Handy → anderes Handy: kleine Version/Thumbnail über Server.
- Portal-Upload darf die empfangene Quelldatei serverseitig behalten; mobile Nutzung über Renditions.

Diese Regel steht bereits in `projektsteuerung/vertraege/TB-SYNC_15x15_ZIELMODELL.md`.

Nächster Schritt:
1. Android-Fotoupload prüfen: Original oder Sync-Version?
2. Server-Empfang/Storage/Renditions prüfen.
3. Handy→Handy-Download prüfen.
4. Abweichungen dokumentieren.
5. erst dann getrennte Android-/Server-Aufträge.
6. E2E mit realen Dateigrößen.

### IMG-001 Audit-Ergebnis 26.09.2026
- Android lädt aktuell das lokale Foto-Original hoch, nicht eine separate kleine Sync-Version.
- Server speichert die empfangene APP-Datei unverändert.
- Server-Renditions `thumb`/ `preview` existieren bereits.
- Android ignoriert diese Renditions und lädt über `/content`.
- Lokales 512-px-Thumbnail existiert, wird im Online-Sync aber nicht genutzt.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.

Umsetzung ist dokumentiert, aber gemäß `E2E-PRIO-001` erst nach stabiler Hauptgerät-/Poco-Fachkette dran.

## SYNC-DEVICE-001 – Poco erhält keine Daten
Status: **OFFEN**

Serverseitig HELPER im richtigen Team bestätigt; Gerät erhält trotzdem keine Daten. Keine Neu-Anlage von Benutzer/Einladung auf Verdacht. Binding/Sync/API/Logs gezielt eingrenzen.

## E2E-PRIO-001 – Hauptgerät/Portal zuerst, Helfer danach
Status: **VERBINDLICHE ARBEITSREIHENFOLGE**

Neue Prioritätsentscheidung:
1. Ein verantwortliches TB-Handy muss zuerst mit Server und Portal in der echten Fachkette sauber funktionieren.
2. Danach wird `SYNC-DEVICE-001` / Poco als HELPER gezielt bis zum bestandenen Geräte-E2E abgearbeitet.
3. Weitere breite 15×15-Lastreihen werden vorerst zurückgestellt.
4. Nach geplantem RAM-Ausbau auf 8 GB bzw. nach Wechsel auf ein neues NAS wird der vollständige Last-/Kapazitätstest auf der dann maßgeblichen Hardware wiederholt.
5. Bis dahin werden Leistungswerte nur als Diagnose verwendet; funktionale Server-/Sync-Verträge werden nicht aufgrund eines einzelnen Lastwerts auf Verdacht umgebaut.

Bereits praktisch beobachteter Kapazitätsteiltest:
- `/api/v1/sync/changes/wait`
- 255 getrennte Portal-Sessions
- `success=255`, `failure=0`
- Wall 16,43 s, Median 14,28 s, Maximum 15,79 s
- verbliebene Python-Testsessions anschließend kontrolliert von 7 auf 0 bereinigt

Bewertung: **DELTA-WAIT-KAPAZITÄTSTEILTEST BESTANDEN / VOLLSTÄNDIGER 15×15-E2E WEITER OFFEN**.

## NET-001 – Hauptgerät kann api-v1.tb-helfer.de nicht auflösen
Status: **ÖFFENTLICHE DNS/API ERREICHBAR / APP-SEITIGE URSACHE OFFEN**

Praktischer Befund beim Anlegen einer Testbaustelle auf dem Hauptgerät:
- App meldet `Synchronisation unvollständig`;
- konkrete Ursache: `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`;
- damit ist der aktuelle Hauptgerät↔Server/Portal-E2E bereits vor der Fachsync-Prüfung durch DNS/Netzauflösung blockiert.

Vorgehen:
1. öffentliche Erreichbarkeit/DNS von `api-v1.tb-helfer.de` prüfen;
2. bei öffentlich funktionierender Domain Geräte-DNS/Netz/Private-DNS/VPN eingrenzen;
3. erst nach erfolgreicher Namensauflösung Fachsync und Portal-Konvergenz prüfen;
4. keine Sync-/DB-/API-Codeänderung auf Verdacht.

Browser-Nachtest auf demselben Hauptgerät:
- `https://www.tb-helfer.de` erreichbar;
- `https://api-v1.tb-helfer.de/health/live` erreichbar;
- öffentliche Domain/Cloudflare/DNS sind damit grundsätzlich erreichbar;
- nächster Schritt: denselben App-Sync erneut auslösen;
- nur bei erneutem App-only `Unable to resolve host` Android-Netzpfad/Prozess/Private-DNS/VPN/HTTP-Client gezielt eingrenzen;
- keine Sync-/DB-/API-Codeänderung auf Verdacht.

### NET-001 Reproduktion nach Browser-Nachweis
- Auf demselben Hauptgerät sind `www.tb-helfer.de` und `api-v1.tb-helfer.de/health/live` im Browser erreichbar.
- Unmittelbarer erneuter App-Test mit `Neu laden` reproduziert weiterhin exakt `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Der Fehler ist damit **app-/Android-Netzpfad-spezifisch reproduziert** und nicht durch eine allgemeine öffentliche DNS-/Cloudflare-Störung erklärt.
- Nächster Schritt: Android-Netzpfad, gespeicherte Serverbasis, HTTP-Client und App-Prozess gezielt gegen den Browserpfad prüfen.
- Keine Server-/DB-Änderung auf Verdacht.

### NET-001 Netzwechsel-Reproduktion
- App vollständig gestoppt/neu gestartet: Fehler bleibt identisch.
- WLAN deaktiviert und erneuter App-Test über Mobilfunk: Fehler bleibt identisch.
- Damit ist ein nur im aktuellen WLAN auftretender DNS-Fehler unwahrscheinlich.
- Browserzugriff auf Portal/API war auf demselben Gerät zuvor erfolgreich.
- Nächster Diagnosepunkt: Android systemweite DNS-/VPN-/Filter-Ebene (Privates DNS, VPN/Adblock/NextDNS o. ä.) gegen den Browserpfad prüfen.
- Keine Server-/DB-/Sync-Codeänderung auf Verdacht.

### NET-001 / SYNC-DEVICE-001 Praxisnachtrag – Pixel synchronisiert, Poco Erstabgleich langsam
- Pixel synchronisiert inzwischen wieder mit dem NAS. Der frühere `Unable to resolve host`-Fehler ist aktuell nicht mehr reproduzierbar als dauerhafter Blocker; Ursache bleibt offen.
- Poco wurde deinstalliert, neu installiert und neu aktiviert.
- Poco zeigt beim anschließenden ersten Datenabruf nach >3 Minuten weiterhin laufenden Abruf.
- Das ist noch kein bestätigter Sync-Erfolg und noch kein bestätigter Fehler. Vor Änderungen zuerst klären, welche Erstsync-Phasen/Datenmengen tatsächlich laufen und an welcher Phase Zeit verloren geht.
- Keine Neu-Anlage von Firma/Benutzer/Team/Baustelle auf Verdacht.

## BTB-SYNC-001 – Neuer BTB 26.09 fehlt im Portal und verschwindet auf Helfer
Status: **SOURCE-FIX COMMITTED / BUILD + INSTALLATION + E2E OFFEN**

Praktischer Ablauf:
1. Poco nach Neuinstallation/Neuaktivierung: Erstabgleich abgeschlossen, Baustelle erfolgreich heruntergeladen.
2. Pixel: neuer BTB 26.09 angelegt.
3. Portal: weiterhin letzter sichtbarer BTB 25.09, 26.09 fehlt.
4. Poco: 26.09-BTB erscheint kurz, danach nach ca. einer Sekunde „kein BTB vorhanden“.

Zwingende Diagnose vor Änderung:
- betroffene Firma/Baustelle/Team und `active_btb_id`;
- Server-BTB-Datensatz 26.09: ID, Status, Team, client_key, Version, created/updated;
- Change-Feed-Einträge für diesen BTB;
- API-Antwort für Team-BTB-Liste;
- Android-Snapshot/Active-Pointer-Anwendung auf Poco;
- keine Neu-Anlage von Benutzer/Team/Baustelle und kein Lifecycle-Workaround auf Verdacht.

### BTB-SYNC-001 Diagnosefund – Pixel läuft noch 1.3.4
- Gerätestatus aus der Server-DB:
  - Pixel: App-Version `1.3.4`
  - Poco: App-Version `1.3.19`
- Projektziel/aktueller Android-Fachstand: `1.3.19 / 200069`.
- Change-Feed zeigt wiederholte Updates desselben BTB bis mindestens Version 19.
- Bewertung: erst Versionsgleichstand herstellen/prüfen. Kein Server- oder DB-Fix, solange das Hauptgerät noch mit einem älteren Android-Syncvertrag arbeitet.

### Korrektur zu BTB-SYNC-001 Diagnosefund
- Pixel ist laut Geräteanzeige tatsächlich auf `1.3.19`.
- `tb.devices.app_version` ist nur der bei Aktivierung gemeldete Stand und wird bei normalem Sync nicht aktualisiert.
- Die Schlussfolgerung „Pixel läuft noch 1.3.4“ war falsch und wird nicht weiterverfolgt.
- Nächster Schritt bleibt die eigentliche BTB-Diagnose: Team-`active_btb_id`, 26.09-BTB-Datensatz, Change-Feed und API-Snapshot.

### BTB-SYNC-001 Diagnosefund – Server empfängt 26.09-BTB
- Change-Feed bestätigt den BTB `ba2144b7-9ede-4f53-abdc-1accd172e875` mit `work_date=2026-09-26`.
- Mehrere aufeinanderfolgende `UPDATE`-Changes in wenigen Sekunden, sichtbar mindestens Version 7–19.
- Pixel→Server-Transport funktioniert daher grundsätzlich.
- Nächste Abgrenzung: kompakten DB-Check nur für diesen BTB ausführen:
  - Worksite/Team/Lead
  - `teams.active_btb_id`
  - BTB-Status
  - Client-BTB-ID
  - aktueller Header/Version
  - Teilnehmerstatus Pixel/Poco
- Erst danach Portal-/Android-Leseweg bewerten.

### BTB-SYNC-001 Diagnosefund – Server-Lifecycle vollständig korrekt

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

Nächster Schritt: Portal-Team-BTB-Leseweg und Android-Snapshot/Active-Pointer gezielt prüfen.

### BTB-SYNC-001 Portal bestätigt
Nach Seitenneuladen zeigt das Portal den aktiven 26.09-BTB korrekt. Damit ist die Kette Pixel → Server → Portal für diesen BTB bestätigt.

Weiter offen:
- Poco sieht denselben aktiven BTB kurz und fällt danach auf „kein BTB vorhanden“ zurück.
- Nächster Diagnosepunkt ausschließlich Android: Server-Snapshot → lokaler BTB-Cache → Active-Pointer → UI-Auswahl.

### BTB-SYNC-001 Poco erneut reproduziert nach Portal-Bestätigung
- Portal zeigt den 26.09-BTB nach Reload korrekt als ACTIVE.
- Poco zeigt denselben 26.09-BTB weiterhin nur kurz und springt danach auf „kein BTB vorhanden“.
- Damit ist der Fehler jetzt eindeutig Android/Poco-seitig reproduziert, nachdem Server-Lifecycle und Portal-Read bestätigt sind.
- Nächster Schritt: Android Active-Pointer-/Marker-/UI-Auswahllogik gezielt im Quellstand prüfen; kein Serverfix auf Verdacht.

### BTB-SYNC-001 Source-Ursache eingegrenzt
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.

Benötigte Android-Anpassung: `BtbActiveContext` muss bei vorhandenem ServerState mit `active=true` den kanonischen Server-Lifecycle respektieren und darf den Pointer nicht aufgrund eines lokalen Abschlussstatus lösen. SERVER ändert dafür keinen DB-/API-Vertrag.


### BTB-SYNC-001 Android-Umsetzung
- Projektsteuerungs-/Vertragsstand aus `e71585cb21fa13c01f184036337d3ea68725f37e` vor Codeänderung in den Android-Branch übernommen.
- Codefix `f57c532863d2f92b81729b9af1466b57a22f8723`.
- `BtbActiveContext` prüft den Server-Snapshot jetzt vor lokalen Abschlussmarkern.
- Server `active=true` hat Vorrang vor lokalen PDF-/Baseline-/Abschlussartefakten.
- Pointerlösung durch diesen Leseweg nur bei `FINALIZED + !active`; ohne Server-Snapshot bleibt Legacy-Verhalten erhalten.
- Keine Poco-Daten gelöscht, keine Neuaktivierung, keine NAS-/Serveränderung.
- Nächster Schritt: Android Studio Build 1.3.19 / 200069 → Pixel/Poco installieren → denselben BTB vom 26.09 erneut prüfen.


## REL-001 – Nächster Android-Build
Status: **VERBINDLICHE BENUTZERENTSCHEIDUNG**

- Der nach BTB-SYNC-001 erzeugte APK-Stand wird **1.3.20 / 200070**.
- `1.3.19 / 200069` bleibt der vorherige Stand und wird nicht für eine neue APK wiederverwendet.
- Für jeden weiteren auslieferbaren Test-/Release-Build muss der `versionCode` erneut steigen.
- Nächster Schritt nach dieser Sicherung: Android-Quellstand und Release-MD auf 1.3.20 / 200070 anheben, dann bauen.


## UPD-001 – Nach Update weiter alte Version / Update verfügbar
Status: **FEHLER REPRODUZIERT / DIAGNOSE VOR FIX**

Benutzerbefund:
- Update wurde ausgeführt.
- „Nach Update suchen“ zeigt danach weiterhin **1.3.19** als installierten Stand.
- Hauptansicht zeigt weiterhin **Update verfügbar**.

Verbindliches Soll:
- Installierte Version/Build kommen aus dem aktuell installierten Android-Package (`PackageManager`/BuildConfig), nicht aus einem stale Geräte-Aktivierungswert.
- Releasekatalog/Serverantwort beschreibt das verfügbare Release.
- Updatehinweis nur, wenn verfügbarer `versionCode` **größer** als der tatsächlich installierte `versionCode` ist.
- Nach erfolgreicher Installation 1.3.20 / 200070 darf 200070 nicht erneut angeboten werden.

Vor Codeänderung prüfen:
1. Wartung/Update-UI: Quelle für „installierte Version“.
2. Startseiten-Banner: Vergleichsquelle und Cache.
3. Server Release-API: published version/build.
4. App-Prozess/PackageManager nach Installer-Rückkehr.


### UPD-001 Ursache / Schutzfix
- Anzeigequelle ist nicht stale: `AppUpdateManager.currentVersionLabel()` nutzt den eingebauten Android-`BuildConfig`.
- Startseite prüft bei `onResume()` erneut; `HttpURLConnection.useCaches=false`.
- Symptom „nach Update weiterhin 1.3.19 + Update verfügbar“ bedeutet deshalb: installierte APK ist weiterhin Build 200069 bzw. das neue Release enthielt nicht die erwartete neue APK.
- Server-Upload validiert APK-Inhalt gegen Release-Metadaten derzeit nicht.
- Android lädt/verifiziert SHA/Größe, aber validiert vor Installation weder Package-ID noch eingebettetes `versionName/versionCode`.

Android-Auftrag:
1. heruntergeladene APK vor Installer mit `PackageManager.getPackageArchiveInfo()` prüfen;
2. Package-ID muss `de.tbhelfer` entsprechen;
3. eingebettete Version/Build müssen exakt dem Server-Release entsprechen;
4. bei Abweichung Installation blockieren und klare Fehlermeldung anzeigen;
5. nächster Build **1.3.21 / 200071**.

Serverbedarf separat dokumentiert: auch der Release-Upload sollte künftig APK-Metadaten gegen Draft-Felder validieren. Keine `server/**`-Änderung im Android-Projekt.


### UPD-001 Android-Fix umgesetzt
- Commit `19d688a4752c98209570e0ff69ccd2eae789541e`.
- `AppUpdateManager.downloadApk()` validiert nach SHA/Größe zusätzlich die interne APK-Metadaten.
- Package muss `de.tbhelfer` entsprechen.
- APK-`versionName/versionCode` müssen exakt dem Release entsprechen.
- Bei Mismatch: Datei löschen, verständliche Fehlermeldung, kein Installerstart.
- Version auf **1.3.21 / 200071** angehoben.
- Nächster Schritt: frisch bauen, exakt diese APK im Portal zu 1.3.21/200071 hochladen und Update-E2E durchführen.


## REL-FILE-001 – Release-MD bei jedem Android-Build
Status: **VERBINDLICHE BENUTZERANFORDERUNG**

- Zu jedem neuen Android-Build gehört zwingend eine passende `.md`-Release-Datei wie bei 1.3.16/200066, 1.3.17/200067 und 1.3.18/200068.
- Ablage im Repository: `projektsteuerung/releases/`.
- Die Datei wird bei jeder Build-/GitHub-Übergabe mitgeführt und dem Benutzer zusätzlich zum Download bereitgestellt.
- `VERSION` und `BUILD` müssen exakt mit `app/build.gradle.kts` und der APK übereinstimmen.
- `PFLICHTUPDATE`, `MINIMUM_BUILD`, `DOWNLOAD_URL` sowie Neu/Verbessert/Behoben werden aus dieser Datei in den Release-Draft übernommen.
- Die Datei kann Server-Metadaten korrekt setzen, ändert aber nicht die in der APK eingebettete Version.
- Deshalb dürfen eine alte APK und eine neue Release-MD niemals gemeinsam veröffentlicht werden.


### UPD-001 Praxisbestätigung
- Benutzer bestätigt: Update mit **1.3.21 / 200071** hat funktioniert.
- Release-MD/APK-Paarung und Android-Schutzfix sind praktisch wirksam.
- UPD-001: **BESTÄTIGT**.
- Nächster fachlicher Test bleibt BTB-SYNC-001 auf Pixel/Poco mit demselben 26.09-BTB.


### BTB-SYNC-001 E2E-Nachtest 1.3.21 fehlgeschlagen
- Benutzer testet 1.3.21 / 200071.
- Pixel: weiterhin Flackern der unteren `BTB Abschluss`-Anzeige trotz Haupt-TB-Rolle.
- Poco: 26.09 erscheint nur kurz, danach wieder `kein BTB vorhanden`.
- Bewertung: vorhandener Active-Pointer-Fix deckt den tatsächlichen Laufzeitpfad noch nicht vollständig ab.
- Keine Poco-Daten löschen, keine Neuaktivierung, keine NAS-/Serveränderung.
- Diagnoseauftrag ANDROID:
  1. alle Schreib-/Löschstellen von `BtbServerStateStore` prüfen;
  2. Mapping lokale `btbId` ↔ Server-BTB-ID ↔ Client-BTB-ID prüfen;
  3. alle Stellen prüfen, die Active-Pointer setzen/lösen;
  4. Lifecycle-Watcher/Bottom-Button auf Wechsel zwischen ServerState und Legacy-Fallback prüfen;
  5. erst nach reproduzierbarer Source-Ursache korrigieren.


### BTB-SYNC-001 zweite Runtime-Ursache und Fix
- Zweiter Pointer-Schreiber gefunden: historischer `BtbPatch200033Installer.startActivePointerGuard()`.
- Dieser Guard läuft alle 450 ms und hatte die Server-vor-Lokal-Vorrangregel noch nicht.
- Er konnte den gerade vom Server gesetzten 26.09-Pointer anhand des lokalen `.inactive_after_completion`-Markers wieder löschen.
- Gleichzeitig überschrieb Patch200033 durch Installationsreihenfolge den modernen Rollen-/Finalize-Click-Handler.

Umgesetzt in `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`:
1. servergebundener Guard wertet `BtbServerStateStore` aus;
2. Server `active=true` → Pointer behalten, stale Inaktiv-Marker löschen;
3. Server `FINALIZED && !active` → Pointer lösen erlaubt;
4. ohne ServerState bleibt Legacy-Verhalten;
5. ModernLifecycleInstaller wird in `HauptActivity` zuletzt installiert.

Neuer Build zwingend: **1.3.22 / 200072**.


### BTB-SYNC-001 1.3.22 Praxisfehler / Serverabgleich
- Benutzer bestätigt: **1.3.22 / 200072 behebt den Fehler nicht**.
- Pixel: `BTB Abschluss` flackert weiterhin trotz Haupt-TB-Rolle.
- Poco: Datum `26.09` erscheint kurz und fällt danach wieder auf `kein BTB vorhanden`.
- Die bisher gefundenen Android-Pointer-/Listener-Konflikte waren real, aber nicht die vollständige Ursache.
- Ab jetzt **kein weiterer Android-Fix auf Verdacht**.
- Nächster Diagnoseauftrag ist der direkte Android↔Server-Abgleich:
  1. Team-/Worksite-Kontext und `active_btb_id`;
  2. Team-BTB-Liste;
  3. BTB-Detailantwort;
  4. Delta-/Change-Feed;
  5. `status`, `active`, `client_btb_id`, `team_id`, `current_user` und Teilnehmerstatus;
  6. Android-Parser/Snapshot-Replacement gegen diese Werte.
- Keine Poco-Daten löschen, keine Neuaktivierung, keine NAS-/Serveränderung auf Verdacht.


## BTB-SYNC-002 – Android-Datenverlust vor Outbox
Status: **P0 / URSACHE BESTÄTIGT / ANDROID-FIX JETZT**

Korrektur zum vorherigen Diagnoseauftrag:
- Der Fehler ist **nicht** zuerst serverseitig zu suchen.
- `SelectiveSyncWorker` lädt bei einem bereits bekannten dirty BTB aktuell zuerst den Serverstand, speichert ihn und ruft `bridge.applyServerStateToLocal(...)` auf.
- Erst **danach** wird `bridge.exportBtbChange(...)` ausgeführt.
- Dadurch kann eine gerade lokal auf dem Pixel gespeicherte Änderung durch den Server-Snapshot überschrieben werden, bevor sie überhaupt als Pending/Outbox-Mutation existiert.
- Praxisbild: Pixel schreibt `ABC` → Portal erhält `ABC` nicht → Portal schreibt `XYZ` → Pixel übernimmt `XYZ` → `ABC` verschwindet.
- Server/API/DB-Vertrag ist für BTB-SYNC-002 **nicht die Ursache**.

Verbindlicher Android-Fix:
1. dirty BTB zuerst aus dem lokalen Fachstand in Pending/Outbox exportieren;
2. danach kanonischen Serverstand/Version laden bzw. innerhalb des bestehenden Mutation-/Konfliktpfads prüfen;
3. solange für denselben `clientBtbId` eine unbestätigte lokale Mutation bzw. ein neuer Dirty-Stand existiert, keinen eingehenden Server-Snapshot still lokal anwenden;
4. bei Versionskonflikt Pending erhalten und explizit den bestehenden Conflict-/Rebase-Pfad verwenden;
5. Server-Snapshot erst nach erfolgreichem Upload/Bestätigung anwenden und dabei einen während des Uploads entstandenen neueren Dirty-Stand schützen.

Neuer Android-Build nach dem Fix: **1.3.23 / 200073**.
1.3.22 / 200072 bleibt der getestete, fehlerhafte Vorgängerstand.

## PORTAL-LIVE-001 – Zuständigkeitsabgrenzung
- Der Portal-Source-Fix über SSE/Change-Feed existiert bereits serverseitig.
- Status bleibt **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.
- Dass auf dem aktuell laufenden NAS noch F5 nötig ist, wird nicht durch einen weiteren Android-Fix behoben.
- ANDROID ändert dafür keine `server/**`-Datei.


### BTB-SYNC-002 Android-Umsetzung 1.3.23
Status: **SOURCE-FIX UMGESETZT / BUILD OFFEN**

Umgesetzt auf `feature/tb-android-2.0`:
- `SelectiveSyncWorker` wendet für einen dirty BTB keinen Server-Snapshot mehr vor dem lokalen Export an.
- Lokaler BTB wird zuerst mit `exportBtbChange(...)` in Pending/Outbox gesichert.
- Serverversion und Konflikte werden danach im bestehenden `synchronizeServer1BtbPending(...)` / `Server1BtbApi.applyPending(...)`-Pfad geprüft.
- Erst nach konfliktfrei bestätigtem Upload wird der bestätigte Server-Snapshot lokal angewendet.
- `CollabLocalBridge.applyServerStateToLocal()` überspringt BTB-Datei-Apply bei Pending **oder Dirty**.
- Lokaler BTB-Save und Server-Apply sind pro BTB über einen Mutationslock serialisiert.
- `SelectiveSyncStore` schützt Dirty-Generation; ein während des Uploads neu entstandener Autosave bleibt erhalten und erzwingt einen weiteren Worker-Lauf.
- Keine `server/**`-Datei geändert.

Quellstand nach Fachfix/Versionierung: `3038d5107bac8cf53cb59b55353628d95653932a` plus Release-MD-Stand im selben Branch.
Zielbuild: **1.3.23 / 200073**.
Build, Installation und Pixel↔Portal-/Poco-E2E bleiben offen.


## BTB-UI-001 – Pixel Buttonleiste flackert
Status: **URSACHE IM ANDROID-UI-PFAD EINGEGRENZT / FIX NÄCHSTER SCHRITT**

Benutzerbefund:
- Pixel ist Haupt-TB und aktuell allein im aktiven BTB.
- Die Abschlussleiste wechselt zwischen nur `BTB Abschluss` und `PDF Entwurf + BTB Abschluss`.

Quellbefund:
- `BtbModernLifecycleInstaller.startLifecycleWatcher()` setzt in jedem 600-ms-Zyklus zunächst `btnPdf` und `btnPdfFinal` beide auf VISIBLE.
- Im servergebundenen Haupt-TB-Zweig wird `btnPdf` anschließend wieder auf GONE gesetzt.
- Damit widerspricht der Haupt-TB-Zweig dem gewünschten UI-Vertrag und erzeugt konkurrierende Sichtbarkeitszustände.

Verbindliches Soll:
- Haupt-TB + aktiver BTB: `PDF Entwurf` sichtbar + `BTB Abschluss` sichtbar, stabil.
- HELPER: kein PDF-Entwurf; nur rollenbezogene Schichtaktion.
- FINALIZED/read-only: nur erlaubte PDF-Ansicht gemäß Serverrecht.
- Die Sichtbarkeit wird pro Zyklus nur aus genau einem Rollen-/Lifecycle-Zweig gesetzt; keine vorgelagerte pauschale VISIBLE-Schaltung.

Nächster Build nach Fix: **1.3.24 / 200074**.


### BTB-UI-001 Android-Fix umgesetzt
Status: **SOURCE-FIX FERTIG / BUILD OFFEN**
- `BtbModernLifecycleInstaller`: keine pauschale Button-Sichtbarkeit mehr vor der Rollenentscheidung.
- Haupt-TB + aktiver BTB: `btnPdf=VISIBLE`, `btnPdfFinal=VISIBLE`, Text `BTB Abschluss`.
- HELPER: `btnPdf=GONE`, nur Schichtaktion.
- FINALIZED/read-only unverändert nach Serverrecht.
- Commit `7fdb4dad2e72515dcda958c00bc2065c38d9f5da`.
- Version **1.3.24 / 200074**, Release-MD vorhanden.


### BTB-UI-001 abgeschlossen
- Benutzer bestätigt 1.3.24 / 200074 auf dem Pixel: kein Springen mehr.
- Haupt-TB-Ansicht mit PDF Entwurf + BTB Abschluss ist praktisch bestätigt.
- Nächster Gerätepunkt: Poco/HELPER-E2E.


### BTB-SYNC-002 Praxis vollständig bestätigt
- Benutzer bestätigt den Pflichttest vollständig:
  - lokale Pixeländerung wird zum Portal übertragen;
  - Portaländerung kommt zum Pixel zurück;
  - erneute Pixeländerung während laufendem Upload bleibt erhalten.
- BTB-SYNC-002: **BESTÄTIGT**.
- Der P0-Datenverlustpfad ist damit geschlossen und geschützt.
- Nächster Arbeitsblock: Poco wieder regulär als HELPER in den aktiven Team-/BTB-Kontext nehmen und HELPER-E2E prüfen.
- Keine App-Daten löschen, keine Neuaktivierung und kein NAS-Umbau auf Verdacht.


## BTB-EDIT-001 – Android-Auftrag
Status: **NEUE FACHANFORDERUNG / VOR IMPLEMENTIERUNG GESICHERT**

Ziel:
- Ein noch bearbeitbarer BTB-Eintrag oder ein BTB-Foto muss nachträglich vollständig fachlich korrigiert werden können, **ohne Löschen+Neuanlegen als Standardweg**.
- Die bestehende Android-Unterbaustellenwahl bleibt fachlich erhalten:
  **Hauptbaustelle → Freie Strecke → BÜ / weitere Unterbaustellen**.
- Gerätespezifische lokale IDs dürfen nicht die geräteübergreifende Sync-Identität sein.

Mindestens nachträglich änderbar:
- BTB-Punkt **4 / 5 / 6 / 7 / 8**;
- **Firma / Allgemein**;
- **Unterbaustelle/Bauobjekt**;
- Uhrzeit;
- KM;
- Text/Kurztext;
- Foto-Zuordnung.

Verbindliche Android-Regeln:
1. bestehende Einträge/Fotos behalten ihre fachliche Identität;
2. Punkt-/Firmen-/Unterbaustellenwechsel ist eine Mutation desselben Objekts, kein Delete/Create;
3. lokale Marker wie `@ORT=<Room-Long-ID>` und lokale `ortId` sind nur Gerätehilfswerte und keine Sync-Identität;
4. für geräteübergreifende Unterbaustellenzuordnung wird die bereits vorhandene stabile Baustellenbaum-Identität `clientNodeKey` bzw. das vorhandene Worksite-Node-Binding verwendet;
5. Zielstruktur:
   `BTB-Punkt → Firma/Allgemein → Unterbaustelle/Bauobjekt → Eintrag/Foto`;
6. Pixel, Poco und Portal müssen dieselbe fachliche Zuordnung sehen;
7. GPS, Aufnahmezeit und Originalfoto bleiben beim Umhängen eines Fotos unverändert erhalten;
8. FINALIZED/abgeschlossene BTBs bleiben read-only;
9. bestätigter BTB-SYNC-002 Outbox-/Dirty-Schutz bleibt geschützt und darf nicht durch Edit-Mutationen umgangen werden.

Android-Diagnose vor Codeänderung:
- Fundstellen von `@ORT=` und lokalen `ortId`-Overrides;
- Speicher-/Lesemodell Punkt 4 gegenüber Punkt 5–8;
- vorhandene stabile `clientNodeKey`-/Node-Bindings;
- Entry-/Foto-Identitäten und vorhandene Edit-/Sync-Mutationen;
- erst danach gemeinsames strukturiertes Android-Modell festlegen.

Servergrenze:
- Android baut keinen Delete/Create-Workaround.
- Der aktuelle Server kann `section` eines bestehenden `btb_entry` noch nicht per PATCH ändern.
- Für bestehende Fotos fehlt noch ein allgemeiner Metadaten-PATCH zum fachlichen Umhängen.
- Android dokumentiert den exakt benötigten Vertrag; SERVER implementiert die API anschließend im Serverprojekt.

Separat und nicht mit BTB-EDIT vermischen:
- `PORTAL-WEATHER-001`: prüfen, warum `tempC` im aktuellen Portal-BTB fehlt;
- fachlich eindeutiger „kein Niederschlag“-Wert bleibt **0 mm** und darf nicht zu `null` werden.


### BTB-EDIT-001 Diagnose abgeschlossen / Serververtrag jetzt vorhanden
- Android-Istmodell vollständig geprüft.
- Kritische Lücke: `@ORT=<lokale ID>` wird in allen Punkten 4–8 vor dem strukturierten `SectionItem` entfernt; stabile Node-Zuordnung fehlt im lokalen Modell.
- `SectionItem` bekommt als Zielmodell `clientEntryKey`, `firmaId/firmaName`, `worksiteNodeKey` zusätzlich zu Zeit/KM/Text.
- Server-HEAD `cd9b18fc062a7a3d4354c611a949ed3d8408d709` enthält inzwischen Entry-`section`-PATCH und versionierten BTB-Foto-Metadaten-PATCH.
- Der zuvor dokumentierte „Serververtrag noch offen“-Stand ist damit überholt. Kein Delete/Create-Workaround nötig.
- Android-Implementierung darf jetzt auf Identitätserhalt + stabile Node-Keys ausgerichtet werden.
- FINALIZED bleibt read-only; BTB-SYNC-002 bleibt geschützt.


### PORTAL-WEATHER-001 Android-Teilbefund
- Android serialisiert `tempC` grundsätzlich bis in `weatherSlots`/Header-Meta.
- konkrete Android-Teilursache für Niederschlag gefunden: `0.0` wird durch `takeIf { it > 0.0 }` zu `null`.
- 0-mm-Erhalt wird in einem separaten Wetter-Fix korrigiert.
- tempC-Fehlstelle erst mit konkretem Runtime-Payload weiter eingrenzen; nicht mit BTB-EDIT vermischen.


### BTB-EDIT-001 Buildziel 1.3.25 / 200075
Status: **BUILD ANGEFORDERT / VERSION RESERVIERT**

- Benutzerauftrag: **bauen**.
- Letzter bestätigter installierter Build: 1.3.24 / 200074.
- Nächster auslieferbarer Android-Build gemäß REL-001: **1.3.25 / 200075**.
- Enthalten: aktueller BTB-EDIT-001-Android-Quellstand mit stabiler Entry-Identität, `worksiteNodeKey`, Firma/Allgemein und Punkt-4–8-Verschiebung ohne Delete/Create.
- Geschützte Fixes BTB-UI-001 und BTB-SYNC-002 bleiben enthalten.
- Wetterpunkt PORTAL-WEATHER-001 wird bewusst **nicht** in diesen BTB-EDIT-Build hineingemischt.


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


## INTEGRATION-AUDIT-001 – Bilder, BTB-Abschluss, Wetter
Status: **NEUE ANFORDERUNG GESICHERT / QUELLVERGLEICH ANDROID↔SERVER↔PORTAL AUSSTEHEND**

Benutzeranforderung 27.09.2026:
1. **Fotos:** Das Aufnahme-Original bleibt auf dem jeweiligen Handy. Für Server/Portal und andere Handys wird nur eine verkleinerte/komprimierte Sync-Version übertragen; andere Handys laden ebenfalls nur kleine Versionen/Renditions.
2. **BTB-Abschluss bidirektional:** Wird ein bearbeitbarer BTB am verantwortlichen Handy abgeschlossen/finalisiert, muss der kanonische Abschluss automatisch im Portal und auf den anderen Teamgeräten ankommen. Wird der BTB zulässig im Portal abgeschlossen/finalisiert, muss derselbe kanonische Lifecycle automatisch auf Android übernommen werden. Keine voneinander abweichenden lokalen Abschlusszustände.
3. **Wetter:** Im BTB müssen Temperatur **und Regen-/Niederschlagsmenge in mm** erhalten und angezeigt werden. Ein bekannter Wert von **0,0 mm** bleibt 0,0 mm und darf nicht als unbekannt/null verschwinden.

Prüfauftrag vor jeder Codeänderung:
- Server-/Portal-API und Datenmodell gegen Android-Erwartung vergleichen;
- tatsächlichen Foto-Upload/Download und Rendition-Pfad prüfen;
- Finalize-/Change-Feed-/Delta-Apply-Pfade Android→Server→Portal sowie Portal→Server→Android prüfen;
- Wetterfelder Header/Payload/Portal-Renderer/Android-Renderer einschließlich 0-mm-Semantik vergleichen;
- Abweichungen dokumentieren; danach Zuständigkeit ANDROID bzw. SERVER getrennt festlegen.

## INTEGRATION-AUDIT-001 – Quellvergleich 27.09.2026
Status: **AUDIT ABGESCHLOSSEN / ANDROID-ABWEICHUNGEN BESTÄTIGT / FINALIZE-VERTRAG SOURCE-SEITIG PASSEND**

### INT-PHOTO-001
- Server besitzt bereits Bildrenditions: `thumb` 320 px/JPEG72 und `preview` 1280 px/JPEG82.
- Server speichert APP-Uploadbytes unverändert; deshalb muss Android selbst eine kleine Sync-Datei liefern.
- Android lädt aktuell `FotoEntity.dateipfad` direkt hoch; das ist das lokale Original.
- Android lädt fremde Serverbilder aktuell über `/api/v1/files/{id}/content`; `preview/thumb` werden nicht verwendet.
- `Server1FileApi.LinkedFile` übernimmt die vom Server angebotenen Rendition-URLs derzeit nicht.
- Lokales 512-px-Thumbnail existiert, ist aber nur UI-Performanceartefakt und keine geeignete alleinige Sync-Version.
- Zuständigkeit: **ANDROID** erzeugt separate kleine Sync-Datei (Zielvertrag ca. 1920 px, JPEG 80–82 %, keine Hochskalierung) und nutzt für Server→Handy bevorzugt `preview`, für Listen `thumb`.
- Kein Server-Ersatzdownscale für falsch hochgeladene Handy-Originale bauen.
- Separat bleibt BTB-EDIT-001 Foto-Metadaten-PATCH: Server-Endpunkt existiert, Android muss ihn noch verwenden.

### BTB-FINAL-001
- Android-Haupt-TB finalisiert über `POST /api/v1/btbs/{id}/finalize`.
- Portal finalisiert über **denselben** Server-Endpunkt.
- Server setzt `FINALIZED`, löscht `teams.active_btb_id`, erzeugt Abschluss-PDF und emittiert BTB-/FILE-Changes.
- Portal hört auf den bestätigten SSE-Change-Feed und lädt bei BTB/FILE-Changes neu.
- Android hört über Server1-Delta/Worker/Poller; ein BTB-FINALIZE-Event lädt genau den BTB neu, übernimmt `FINALIZED && !active` und macht ihn lokal read-only/inaktiv.
- **Kein Source-Vertragsbruch gefunden.** PORTAL-LIVE-001 bleibt geschützt.
- Offen: gezielter Praxis-E2E A) Android-Abschluss → offenes Portal + anderes Handy, B) Portal-Abschluss → Android + anderes Handy, jeweils inkl. Abschluss-PDF.

### WEATHER-001
- Android-Datenmodell/JSON enthält `tempC`, `niederschlag` und `niederschlagMm`; Deserialisierung erhält vorhandenes numerisches 0.0 korrekt.
- Android→Server transportiert `weather` und `weatherSlots` im BTB-Header.
- Portal liest dieselben Felder, kann Temperatur + `Menge mm` bearbeiten und zeigt bekannte `0 mm` korrekt an.
- Android-Fehler bestätigt: automatische Wetterübernahme verwendet an zwei Stellen `precipMm.takeIf { it > 0.0 }`; damit wird bekannter Wert 0.0 zu `null`.
- Android-Wetter-UI zeigt aktuell nur Zeit/Temperatur/Bewölkung/Niederschlagsart, nicht die numerische mm-Menge.
- Android-PDF/BtbDruckModell lässt `niederschlagMm` ebenfalls aus der Wetterdarstellung weg.
- Zuständigkeit: **ANDROID** muss 0.0 erhalten und mm sichtbar machen. Server/Portal-Vertrag benötigt dafür source-seitig keine neue Felddefinition.
- Alter Praxispunkt `tempC` im Portal bleibt Runtime-Probe: Quellseitig ist keine Verluststelle zwischen Android-Serialisierung und Portalrenderer sichtbar.

## ANDROID 1.3.26 / 200076 – Integrationsfix Bilder/Wetter
Status: **ZIELVERSION GESICHERT / IMPLEMENTIERUNG JETZT**

- Neuer Android-Codeblock für INT-PHOTO-001, BTB-EDIT-001 Foto-PATCH und WEATHER-001.
- Vorheriger Zielbuild 1.3.25 / 200075 hatte keinen erfolgreichen Buildnachweis und bleibt historisch offen/fehlgeschlagen; er wird nicht erneut verwendet.
- Nächster eindeutiger Android-Build ist **1.3.26 / 200076**.
- BTB-FINAL-001 Lifecycle-Code bleibt in diesem Block unverändert; Abschluss wird anschließend gezielt E2E getestet.


## ANDROID 1.3.26 / 200076 – Implementierungsstand 27.09.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECK PASS / BUILD OFFEN**

Umgesetzt:
- gemeinsamer Android-Bildencoder: separate Sync-JPEGs, max. 1920 px, JPEG 82; lokale Originale bleiben unverändert;
- kleine Uploads für BTB-, Mängel- und Abnahmefotos sowie bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen unverändert;
- Server→Android-Bildsync verwendet `preview` statt automatischem `/content`-Vollbild;
- bestehende Foto-Mappings schützen beim Umstieg gegen unnötige Dubletten;
- Android-BTB-Foto-PATCH gegen `link_version` implementiert; Konflikt wird nicht still überschrieben;
- Wetter: bekannter Wert 0,0 mm bleibt numerisch erhalten; mm-Anzeige in Android-Wetterzeilen sowie PDF/Druckmodell;
- Version auf **1.3.26 / 200076** angehoben; Release-MD vorhanden.

Nicht geändert:
- BTB-FINAL-001 Lifecycle-Code; Android und Portal bleiben am selben kanonischen Server-Finalize-Pfad;
- keine `server/**`-Datei.

Source-Prüfung:
- 19 gezielte Bild-/Wetter-/Versionsprüfungen: PASS;
- Build-Sourcegenerator-Anker: 14/14 PASS;
- Diff ab Audit-Basis enthält nur erwartete `app/**`- und Projektsteuerungsdateien;
- aktueller Fachcode-HEAD vor Statussicherung: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`;
- für diesen HEAD existiert kein GitHub-Actions-Run;
- kein echter Gradle-Build, keine Installation, kein Geräte-E2E in diesem Chat.

Nächster Schritt:
1. Git Pull;
2. Android Studio: Build 1.3.26 / 200076;
3. APK auf Pixel installieren;
4. Foto-Größe/Rendition, Wetter 0 mm, Foto-PATCH und BTB-Finalize Android→Portal sowie Portal→Android praktisch testen;
5. danach Poco/HELPER.

## AND-BUILD-200076 – lokaler Compilefehler Locale
Status: **REPRODUZIERT / URSACHE EINDEUTIG**

- Benutzer-Build in Android Studio am 27.09.2026 scheitert in `:app:compileDebugKotlin`.
- Fehler: `BautagebuchFragment.kt:1214 Unresolved reference 'Locale'`.
- Betroffen ist die neue Wetter-mm-Formatierung `String.format(Locale.GERMANY, "%.1f mm", it)`.
- Ursache: im kanonischen `BautagebuchFragment.kt` fehlt `import java.util.Locale`; der Buildgenerator kopiert diesen Quellstand korrekt in `build/generated/source/btb200043`, daher erscheint der Fehler dort ebenfalls.
- Kein Server-/API-/DB-Fehler.
- Fixziel: ausschließlich fehlenden Kotlin-Import ergänzen; Wetterlogik und geschützte Sync-/Finalize-Pfade nicht verändern.
- Version bleibt **1.3.26 / 200076**, da kein erfolgreicher APK-Build/keine Installation vorliegt.

## AND-BUILD-200076 – Locale-Compilefix
Status: **SOURCE-FIX UMGESETZT / BUILD ERNEUT OFFEN**

- Fixcommit: `199df4b149e210194b22b1c84cc1700ac54435c2`.
- Ausschließliche Codeänderung: `import java.util.Locale` in `BautagebuchFragment.kt`.
- `Locale.GERMANY` ist damit im kanonischen Quelltext auflösbar; der Buildgenerator übernimmt den Import beim Kopieren in `build/generated/source/btb200043`.
- Build-Sourcegenerator-Anker nach Fix: **14/14 PASS**.
- Diff gegen vorherigen Checkpoint enthält im App-Code nur diese eine Importzeile.
- Kein `server/**`, kein Sync-/Finalize-/Wetter-Fachverhalten geändert.
- Version bleibt **1.3.26 / 200076**, weil der vorherige Versuch keinen erfolgreichen APK-Build erzeugt hat.
- Nächster Schritt: Git Pull → Android Studio → Build 200076 erneut.

## AND-VER-001 – Benutzerkorrektur
Status: **ZURÜCKZUNEHMEN / NICHT GEWÜNSCHT**

Benutzerentscheidung 27.09.2026:
- Die zusätzliche sichtbare Versions-/Buildzeile auf dem Android-Startbildschirm war **keine Benutzeranforderung** und soll **nicht** eingebaut werden.
- AND-VER-001 wurde vom Assistenten eigenständig ergänzt und wird vollständig zurückgenommen.
- Auch die dabei geänderte Formatierung von `AppUpdateManager.currentVersionLabel()` wird auf den vorherigen Stand zurückgesetzt.
- Die technische App-Version bleibt unverändert **versionName 1.3.26 / versionCode 200076**.
- Release-/Buildnummern bleiben intern für Build, Updateprüfung und Release-MD erhalten; daraus folgt **keine zusätzliche Startseitenanzeige**.


## BRANCH-CLEANUP-001 – GitHub-Branches bereinigen
Status: **PR-BEREINIGUNG ERLEDIGT / BRANCHLÖSCHUNG OFFEN**

Benutzerentscheidung 27.09.2026:
- Die laufende Repository-Struktur soll auf die aktiven Arbeitszweige reduziert werden.
- Dauerhaft benötigt werden als Branches:
  - `main` als Default-/Basisbranch;
  - `feature/tb-android-2.0` für Android;
  - `feature/tb-server-1.0` für Server.
- Veraltete Entwicklungsbranches sollen entfernt werden, sobald keine aktive PR-/Basisabhängigkeit mehr besteht.
- Alter Draft-PR #1 (`kilometrierung-v1`) und alter Draft-PR #5 (`feature/admin-offline-sync`) werden geschlossen.
- Server-Draft-PR #6 wird vor Entfernung von `feature/admin-offline-sync` auf `main` als Basis umgestellt.
- Vollständig überholte Branches: `feature/admin-offline-sync`, `feature/btb-pdf-identity`, `feature/cloudflare-api`, `feature/server-sync-catalog`, `ci/android-1.3.25-base-200075`.
- `feature/device-activation` und `kilometrierung-v1` besitzen historisch eigene Commits; ihre heutigen Funktionen sind im aktuellen Projektstand abgelöst/weiterentwickelt. Vor Branchlöschung bleiben die bekannten Head-SHAs in der GitHub-/PR-Historie nachvollziehbar.
- Keine Android-/Server-Fachcodeänderung durch diesen Aufräumblock.


### BRANCH-CLEANUP-001 Ergebnis 27.09.2026
- Draft-PR #1 (`kilometrierung-v1`) geschlossen.
- Draft-PR #5 (`feature/admin-offline-sync`) geschlossen.
- Server-Draft-PR #6 von `feature/admin-offline-sync` auf `main` umgestellt; bleibt Draft/offen.
- Aktuell einziger offener PR: #6 SERVER.
- Zu löschende Altbranches nach Sicherheitsprüfung:
  - `ci/android-1.3.25-base-200075`
  - `feature/admin-offline-sync`
  - `feature/btb-pdf-identity`
  - `feature/cloudflare-api`
  - `feature/device-activation`
  - `feature/server-sync-catalog`
  - `kilometrierung-v1`
- Sollzustand Branchliste: `main`, `feature/tb-android-2.0`, `feature/tb-server-1.0`.
- Die verfügbare GitHub-Connector-Schnittstelle kann Branch-Refs nicht löschen; die Branchlöschung selbst ist daher noch manuell in GitHub/GitHub Desktop auszuführen.


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

## AND-IMG-2MB – Umsetzungsauftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer bestätigt ersten Umsetzungsblock mit „okay“.
MobileImageSync muss ausschließlich JPEG-Dateien mit 1..2.000.000Bytes zurückgeben, auch bei Cachetreffern. Bei Übergröße Qualität schrittweise senken und danach Abmessungen reduzieren; nie hochskalieren, Original nicht ändern, kein Originalfallback. Bestehende EXIF-Ausrichtung erhalten. Fehler dürfen keine teilweise Datei als gültigen Cache hinterlassen. Quellprüfung/gezielte Regression erforderlich. Keine Serveränderung, keine Versionsanhebung und kein APK-Buildauftrag.
