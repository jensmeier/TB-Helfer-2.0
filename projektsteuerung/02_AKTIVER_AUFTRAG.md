# TB-HELFER 2.0 – AKTIVER AUFTRAG

Stand: 2026-09-26

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
