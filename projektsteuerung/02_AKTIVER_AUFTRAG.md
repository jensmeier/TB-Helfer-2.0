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


## IMG-PORTAL-001 – 4 Serverbilder, 1 Portalbild
Status: **FEHLER FESTGESTELLT / URSACHE OFFEN**

Praxis:
- aktiver BTB 26.09;
- auf Server/NAS liegen 4 zugehörig wirkende Bilder;
- Portal zeigt nur 1 Bild.

Diagnose vor Änderung:
1. Attachment-Datensätze der vier Dateien prüfen;
2. `parent_entity_type`, `parent_entity_id`, `purpose`, MIME, Dateiname und Metadaten vergleichen;
3. prüfen, welche Fotos der BTB-Detail/API tatsächlich zurückliefert;
4. Portal-Template/-JS nur ändern, wenn API alle 4 liefert, UI aber filtert;
5. keine Dateien oder DB-Zuordnungen auf Verdacht ändern.


## PORTAL-LIVE-001 – Portal ohne F5 automatisch aktualisieren
Status: **NEUE ANFORDERUNG / SOURCE-UMSETZUNG OFFEN**

- Nach manuellem Browser-Reload (F5) erscheinen die zuvor fehlenden BTB-Bilder vollständig im Portal.
- Damit ist die Bildzuordnung/Serverablage grundsätzlich vorhanden; der aktuelle Praxisfehler ist **fehlende Live-Aktualisierung der offenen Portalansicht**.
- Benutzeranforderung: Neue BTBs/Bilder/Fachdaten sollen in einer offenen Portalansicht automatisch sichtbar werden; F5 darf für normalen Betrieb nicht nötig sein.
- Bestehende Architekturregel gilt: offene Portalansichten dürfen live aktualisieren, aber nicht über 2-Sekunden-DB-Polling pro Browser. Vorhandener Change-Feed/SSE-/Long-Poll-Pfad ist zu verwenden.

Umfang zuerst:
1. offene Baustellen-/BTB-Portalansicht an Change-Feed anbinden;
2. bei relevanten Änderungen nur die betroffenen Portalbereiche nachladen (Teams/BTBs, Medien, Mängel, Abnahmen);
3. keine vollständige Seitennavigation und kein permanentes Kurzintervall-Polling;
4. Reconnect/Fehler robust behandeln; F5 bleibt nur Recovery.

### PORTAL-LIVE-001 Source-Fix umgesetzt
- Source-Fix Commit: `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- `v1_btb.html` und `v1_worksite.html` abonnieren jetzt den vorhandenen worksite-bezogenen SSE-Change-Feed `/api/v1/sync/events`.
- Relevante Änderungen werden entprellt und gezielt nachgeladen; auf der BTB-Seite insbesondere `BTB`, `BTB_ENTRY`, `BTB_PARTICIPANT` und `FILE`.
- Baustellenseite aktualisiert Teams/BTBs, Medien, Mängel, Abnahmen, Mitglieder und Baum abhängig vom Change-Typ.
- Kein 2-Sekunden-Polling; vorhandener gemeinsamer Change-Hub wird verwendet.
- Source-Assertions für EventSource/SSE-Pfad und gezielte Reload-Funktionen: **PASS**.
- Status bleibt: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.
Nächster Schritt: Serverstand deployen und real prüfen, dass neue Handy-Bilder/BTB-Änderungen ohne F5 in offener Portalansicht erscheinen.


## BTB-SYNC-002 – asymmetrischer BTB-Sync mit Datenverlust
Status: **P0 FEHLER REPRODUZIERT / URSACHE OFFEN**

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

Diagnose-Reihenfolge:
- zuerst Android lokales Save + Pending/Outbox;
- dann Server-API/Version/Change-Feed;
- dann Portal-Mutation/Version;
- erst danach Fix in genau einer verantwortlichen Schicht.

### BTB-SYNC-002 Source-Ursache – SelectiveSyncWorker überschreibt vor Export
- **Source-Ursache gefunden im aktuellen Android-HEAD `ef7077923e2917ac615b9f3b5143ac2638fb92ac`:**
  - `BautagebuchSpeicher.speichern()` speichert die Pixel-Änderung lokal und markiert den BTB nur im `SelectiveSyncStore` als dirty.
  - `SelectiveSyncWorker` lädt bei bereits bekanntem Server-BTB **zuerst** `loadBtbAccess(...)`, speichert den Server-Snapshot und ruft **vor** `exportBtbChange(...)` bereits `bridge.applyServerStateToLocal(...)` auf.
  - Zu diesem Zeitpunkt existiert noch keine `collabPending`-Mutation für die neue Pixel-Änderung; der Guard in `applyServerStateToLocal()` kann sie daher nicht schützen.
  - Ergebnis: der ältere/andere Serverstand überschreibt die lokal gespeicherte Pixel-Änderung, erst danach wird exportiert. Damit wird der überschriebenen Serverstand statt der Pixel-Änderung hochgeladen.
- Das erklärt exakt den Praxisbefund: Pixel→Portal fehlt; Portal→Pixel kommt an und entfernt die Pixel-Eingabe.
- Server/API/DB sind für diesen Fehler nicht zu ändern.

Benötigte Android-Korrektur:
1. bei dirty BTB zuerst den lokalen Stand in die Outbox/exportieren bzw. gezielt sichern;
2. erst danach Serverzugriff/Version prüfen und konfliktbewusst hochladen;
3. eingehenden Snapshot nicht lokal anwenden, solange für denselben `clientBtbId` ein Dirty-/Pending-Stand existiert;
4. bei Versionskonflikt kein stilles Überschreiben, sondern Rebase/Conflict-Pfad.


## PORTAL-LIVE-001 – Deploy jetzt
Status: **BENUTZER BESTÄTIGT / SERVER-DEPLOY JETZT PRIORITÄT**

- Der bidirektionale Pixel↔Portal-Basissync ist auf Android 1.3.23 / 200073 praktisch bestätigt.
- Benutzerauftrag: als nächstes den bereits vorhandenen Portal-Live-Refresh deployen, damit im normalen Portalbetrieb kein F5 mehr nötig ist.
- Source-Fix ist bereits vorhanden: Commit `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- Betroffene Portaldateien: `server/templates/v1_btb.html` und `server/templates/v1_worksite.html`.
- Vorhandener SSE-/Change-Feed `/api/v1/sync/events` wird genutzt; kein 2-Sekunden-DB-Polling.
- Nächster Schritt: Synology-Deploy nach Projektregel und anschließend E2E mit offenem Portal + Handyänderung/Foto ohne F5.


## PORTAL-LIVE-001 – Praxis bestätigt / BTB-Portal-Nacharbeit separat
Status: **PORTAL-LIVE-001 BESTÄTIGT / PORTAL-BTB-UI-001 OFFEN**

Praktische Benutzerbestätigung:
- offene Portalansicht aktualisiert sich ohne F5;
- Änderungen erscheinen nach ca. 1–2 Sekunden;
- Erstellen und Löschen werden automatisch sichtbar;
- Synchronisation funktioniert in beide Richtungen.

Damit ist PORTAL-LIVE-001 praktisch bestätigt. Der Live-/SSE-Pfad darf nicht ohne neuen reproduzierbaren Fehler geändert werden.

Neue getrennte Anforderung:
- Der BTB im Portal muss fachlich/optisch noch nachgearbeitet werden.
- Diese Nacharbeit wird als `PORTAL-BTB-UI-001` getrennt vom bestätigten Live-Sync geführt.
- Noch keine UI-/Fachänderung auf Verdacht; konkrete gewünschte Portal-BTB-Anpassungen werden separat festgelegt.


## BTB-EDIT-001 – Einträge/Fotos vollständig nachträglich umhängen
Status: **NEUE FACHANFORDERUNG / VOR UMSETZUNG**

Benutzeranforderung:
- Jeder noch bearbeitbare BTB-Eintrag und jedes Foto muss nachträglich fachlich korrigierbar sein, ohne Löschen/Neuanlegen zu erzwingen.
- Änderbar sein müssen mindestens: BTB-Punkt 4–8, Firma, Unterbaustelle/Bauobjekt (z. B. freie Strecke oder BÜ), Text, Zeit/KM und Foto-Zuordnung.
- Beispiel: Ein Eintrag wurde versehentlich dem falschen BÜ, der falschen Firma oder Punkt 4 statt Punkt 5 „Abnahmen“ zugeordnet; diese Zuordnung muss später geändert werden können.
- Dasselbe gilt für Fotos: Foto muss zwischen Punkt 4–8 und zwischen Unterbaustellen/Bauobjekten umgehängt werden können.
- Portal und Android müssen dasselbe strukturierte Schema verwenden; keine voneinander abweichenden Freitext-Sonderwege.
- Unterbaustellen/Bauobjekte müssen im Portal sichtbar und auswählbar sein wie auf Android (Hauptbaustelle, freie Strecke, BÜ usw.), soweit sie im Baustellenbaum vorhanden sind.
- FINALIZED/historische BTBs bleiben gemäß bestehendem Vertrag geschützt/read-only; die Anforderung gilt für bearbeitbare Zustände.
- Bestehender bestätigter Live-Sync bleibt geschützt.

Vor Implementierung zuerst Istvergleich Android↔Portal/API/DB durchführen und fehlende strukturierte Felder/Mutationsendpunkte bestimmen.


### BTB-EDIT-001 Istvergleich Android ↔ Portal 26.09.2026
Status: **STRUKTURLÜCKE BESTÄTIGT / NOCH NICHT IMPLEMENTIERT**

Quellvergleich:
- Android bietet bei Punkt-4-Einträgen bereits „Ort / Unterbaustelle“ mit hierarchischer Auswahl aus dem Baustellenbaum.
- Android speichert diese Auswahl im aktuellen Editor jedoch teilweise als lokalen Marker `@ORT=<lokale Room-Long-ID>`; diese ID ist gerätespezifisch und kein geeigneter Server-/Mehrgeräte-Schlüssel.
- Beim Umwandeln in strukturierte `SectionItem`-Daten werden `@ORT`-Marker teilweise entfernt; `SectionItem` besitzt selbst kein Unterbaustellenfeld.
- Androids stabiler Baustellenstruktur-Sync kennt bereits `clientNodeKey` für Unterknoten. Für BTB-Zuordnungen muss künftig dieser/stabiler äquivalenter Schlüssel verwendet werden, nicht die lokale Long-ID.
- Portal zeigt aktuell keine Unterbaustellenhierarchie im BTB-Editor und lädt für den BTB-Editor keinen auswählbaren Baustellenbaum.
- Portal-Entry-Editor kann aktuell Zeit, KM und Text ändern; Abschnitt 4–8, Firma und Unterbaustelle sind nicht vollständig umhängbar.
- Server `BtbEntryUpdate` erlaubt aktuell nur `sort_order` und `payload`; `section` kann per PATCH nicht geändert werden. Ein echtes Verschieben 4↔5↔6↔7↔8 fehlt daher im API-Vertrag.
- Bei Fotos existiert Upload + Zuordnung + Detach, aber kein allgemeiner PATCH-Pfad zum nachträglichen Ändern von Foto-Metadaten/Fachzuordnung. Android hat lokale Foto-Kontext-Overrides inkl. `ortId`, überträgt diesen Ort jedoch nicht als stabilen Node-Key in `structuredPhotoMetadata`.
- Ergebnis: die gewünschte Nachbearbeitung erfordert einen gemeinsamen strukturierten Android↔Server-Vertrag, nicht nur eine Portal-UI-Änderung.

Zusätzliche Benutzerhinweise für denselben Portal-BTB-Arbeitsblock:
- gelöschte/nicht mehr aktive Helfer sollen in der aktuellen BTB-Ansicht nicht unnötig weiter angezeigt werden; historische Nachweise dürfen nicht physisch gelöscht werden;
- Wetter: Temperaturen fehlen in der Praxisansicht. Der Portalrenderer kann `tempC` anzeigen, daher muss geprüft werden, ob der aktuelle BTB-Header die Temperaturwerte überhaupt liefert;
- Niederschlagsmenge soll bei fachlich bekanntem „kein Niederschlag“ als 0 statt als fehlender/null-Wert behandelt/angezeigt werden, sofern die Quelle dies eindeutig liefert;
- alle BTB-Fotos sollen ihre GPS-Daten aus Handy- und Portalaufnahme behalten und anklickbar darstellen.


### BTB-EDIT-001 Server-Umsetzung freigegeben
Benutzerauftrag: **weiterbauen**.

Server-Arbeitsumfang dieses Blocks:
1. Entry-PATCH um versionierten Abschnittswechsel 4–8 und `worksiteNodeKey`-Validierung erweitern.
2. BTB-Fotoverknüpfung um versionierte linkbezogene Fachmetadaten erweitern; Original-/Aufnahmedaten nicht verändern.
3. Portal-BTB auf dieselbe Baustellenbaumstruktur anbinden und Bearbeiten von Punkt, Firma, Unterbaustelle, Zeit/KM/Text ermöglichen.
4. Bestehenden bestätigten SSE-Live-Refresh nicht umbauen.
5. Source-Checks ausführen; Deployment/E2E bleibt danach getrennt offen.
6. Android-`app/**` wird in diesem SERVER-Block nicht geändert.


### BTB-EDIT-001 – Server-Quellblock umgesetzt
Status: **QUELLSTAND FERTIG / DEPLOY + MIGRATION 016 + E2E OFFEN**

Server-Umsetzung:
- `PATCH /btbs/{btb_id}/entries/{entry_id}` kann denselben Eintrag versioniert zwischen Punkt 4–8 verschieben.
- `worksiteNodeKey` wird gegen den kanonischen Worksite-Node-Baum validiert.
- Migration `016_btb_photo_link_metadata.sql` ergänzt versionierte BTB-spezifische Foto-Verknüpfungsmetadaten.
- `PATCH /btbs/{btb_id}/photos/{file_id}` ändert die fachliche Foto-Zuordnung, ohne Foto-Datei, Aufnahmezeit oder GPS umzuschreiben.
- BTB-Detail liefert effektive Foto-Metadaten + `link_version`.
- Portal lädt denselben Worksite-Baum wie Android und kann bei Punkt 4–8 Firma/Allgemein, Unterbaustelle, Zeit, KM und Text bearbeiten.
- Fotos können im Portal nachträglich Punkt/Firma/Unterbaustelle/Text/KM zugeordnet werden.
- Unterbaustellen werden hierarchisch und Android-nah dargestellt, z. B. freie Strecke mit KM-Bereich.
- bestätigter SSE-Live-Refresh bleibt erhalten.

Quellprüfung:
- Portal-JavaScript Syntax: **PASS**.
- Source-Assertions 94–100: **PASS**.
- Capture-Schutz für GPS/Aufnahmezeit: **PASS**.
- GitHub Actions aktueller HEAD: kein Testnachweis, da keine Runner-Schritte geliefert wurden.

Parallelstand ANDROID:
- aktueller Android-HEAD `7c7cf7b502ec1f701f167c13780b10a014bba6d9`;
- Entry-Modell/Node-Key/Entry-Identität bereits source-seitig in Umsetzung;
- Android-Foto-Umhängen mit stabilem `worksiteNodeKey`/Foto-PATCH noch offen.

Noch nicht behaupten:
- nicht deployed;
- Migration 016 nicht praktisch angewendet;
- kein Server-Runtime-/Geräte-E2E für BTB-EDIT-001.


### INT-001 – Android-Arbeitsblock vom Benutzer als fertig bestätigt
Benutzerbestätigung am 26.09.2026: **„Android ist fertig“**.

Einordnung gemäß Testregeln:
- die Bestätigung beendet den laufenden Android-Entwicklungsblock und gibt die Branch-Integration an SERVER frei;
- sie ist **keine** nachträgliche Behauptung eines erfolgreichen Builds 1.3.25/200075;
- laut aktuellem Android-HEAD `a1b4667b110a993796e8242d4a21be3748c06d68` wurde der CI-Runner nicht zugewiesen und keine Gradle-Step ausgeführt;
- Build/Installation/BTB-EDIT-001-Geräte-E2E bleiben deshalb getrennt offen.

Nächster Arbeitsblock: SERVER löst `INT-001`, integriert Server- und Android-Branch konfliktbewusst und schützt dabei `server/**` bzw. `app/**`.


### AND-BUILD-200075 – Integrationshinweis
Vor Branch-Integration live festgestellt:
- Android-HEAD `57c960fc2f0b133f8c577ba5bcaf2176375d287d`;
- lokaler Build scheitert reproduzierbar in `generateBtb200043Sources`;
- alter Android-200047-Ortsmarker-Anker passt nicht mehr zur neuen BTB-EDIT-Quelle.

SERVER integriert den Quellstand konfliktbewusst, ändert den Android-Buildgenerator aber nicht. Build 200075 bleibt danach offen und geht zurück an ANDROID.


### AND-BUILD-200075 – Generatorfix vorhanden
Android-HEAD enthält mit `606aa3ad1f8ea438372484b056a0fc1264ae2aac` den Fix für den reproduzierten `generateBtb200043Sources`-/Android-200047-Ankerfehler.

SERVER ändert diesen Android-Code nicht. Vor endgültiger Integration wird nur noch geprüft, ob der Android-HEAD stabil bleibt und ob ein erfolgreicher Buildnachweis nachgereicht wurde.

### INT-001 – Branch-Integration ausgeführt
- Android-Arbeitsblock wurde vom Benutzer als fertig bestätigt.
- Integrierter Android-Parent: `4de3350fa4bc3777a6f2f51514e85a145ca4a9c6`.
- Generatorfix 606aa3ad + Statuscheckpoint 4de3350f sind enthalten; Build-Retest 200075 bleibt offen.
- Build/Installation/E2E werden durch die Integration nicht bestätigt.
- Android-Foto-PATCH-Restpunkt bleibt separat offen.


## NÄCHSTER SERVERBLOCK – Deploy BTB-EDIT-001 / Migration 016
Status: **BEREIT ZUM PRAKTISCHEN SERVER-DEPLOY**

Nach abgeschlossener Branch-Integration:
1. aktuellen `feature/tb-server-1.0` per GitHub Desktop auf den PC holen;
2. Serverstand per File Station nach `/volume1/docker/tb-helfer-v1` übernehmen, ohne `.env`, Datenbank, Storage oder Backups zu überschreiben;
3. Container über Container Manager neu erstellen/starten;
4. Migrationen im API-Container prüfen/anwenden; Ziel neu: Migration 016;
5. Runtime-Status prüfen;
6. Portal-BTB öffnen und BTB-EDIT-001 Entry-/Foto-Editor smoke-testen;
7. erst danach gemeinsamer Geräte-E2E.

Android 1.3.25/200075 bleibt Build-Retest offen; das blockiert den Server-Deploy nicht, aber den vollständigen Geräte-E2E.


## BTB-EDIT-001 – Dateien auf Synology hochgeladen
Status: **DATEIEN OBEN / MIGRATION 016 NOCH NICHT AUSGEFÜHRT**

Benutzer bestätigt am 26.09.2026: die für BTB-EDIT-001 benötigten Serverdateien wurden nach `/volume1/docker/tb-helfer-v1` hochgeladen.

Jetzt ausdrücklich noch offen:
- Migration 016 ausführen;
- API/Worker mit neuem Stand neu starten;
- Runtime-/Portal-Smoke-Test;
- noch kein Deploy-/E2E-Erfolg behaupten.


## BTB-EDIT-001 – Migrationslauf OK, 016-Verifikation offen
Praxis am 26.09.2026:
- im API-Container ausgeführt: `python -m tbserver.migrations up`;
- Ausgabe: `OK`;
- kein `APPLIED 016_btb_photo_link_metadata.sql` in der sichtbaren Ausgabe.

Einordnung:
- Migrationsrunner lief ohne Fehler;
- daraus allein folgt noch nicht, ob 016 bereits vorher angewendet war oder ob der Container die neue Migrationsdatei nicht sieht;
- vor Neustart von API/Worker deshalb `python -m tbserver.migrations status` ausführen und `latest_known/latest_applied/pending` prüfen.


## BTB-EDIT-001 – Migration 016 bestätigt
Status: **MIGRATION ANGEWENDET / API+WORKER NEUSTART ALS NÄCHSTES**

Benutzer-Screenshot zeigt eindeutig:
`APPLIED 016_btb_photo_link_metadata.sql`.

Damit ist der DB-Schritt abgeschlossen. Nächster praktischer Ablauf:
1. API-Container neu starten;
2. Worker-Container neu starten;
3. API-Health/Portal prüfen;
4. BTB-EDIT-001 Smoke-Test im Portal.


## ADMIN-FINISH-001 – Admin-Seite fertigstellen vor Portal
Status: **ANFORDERUNG GESICHERT / IMPLEMENTIERUNG NOCH NICHT GESTARTET**

Benutzerentscheidungen vom 28.09.2026:
- gesamte Admin-Oberfläche und alle sichtbaren Systembegriffe auf **Deutsch**;
- verständliche Speicher-/Datenmengen primär als **MB / GB** anzeigen; technische MiB/GiB-Bezeichnungen nicht als normale Hauptanzeige verwenden;
- Admin-Übersicht aktualisiert sich automatisch im Zielrhythmus **ca. alle 5 Sekunden**; fachliche Live-Ereignisse dürfen weiterhin den vorhandenen Change-/Live-Pfad nutzen;
- Speicherübersicht zeigt getrennt:
  - NAS gesamt/belegt/frei;
  - TB-Helfer-Gesamtbelegung;
  - Belegung pro Firma;
  - je Firma zusätzlich Aufteilung nach **Fotos / PDFs / Vermessungs- bzw. Trassierungsdaten / sonstige Dateien**, soweit serverseitig eindeutig klassifizierbar;
- NAS-Hardware zunächst nur als **Gesamtstatus**; keine Pflicht für einzelne HDD-Temperaturen;
- Firmen bleiben eigener Hauptmenüpunkt; eine Firmenansicht bündelt mindestens **Übersicht / Benutzer / Geräte / Baustellen / Speicher / Logo-PDF**;
- Benutzerliste standardmäßig **nach Firma gruppieren**, innerhalb der Firma alphabetisch;
- Bahnhof/Betriebsstelle: bidirektionale Suche nach Kürzel oder Name; Auswahl übernimmt automatisch **Kürzel + vollständigen Namen**;
- Admin-Datenimport zunächst für **Trassierungs-/Vermessungsdaten** und fachlich so nah wie möglich am vorhandenen Android-Importablauf; keine blinde Überschreibung von Originaldaten;
- bestehender TB-Helfer-Dunkelblau/Hellblau-Stil bleibt, wird aber moderner, kompakter und informativer mit Statuspunkten, Karten, kleinen Diagrammen und besserer Flächennutzung;
- wichtige Admin-Inhalte sollen auf üblichen Laptop-Auflösungen möglichst ohne unnötige lange Scrollstrecken erfassbar sein; Detailbereiche dürfen einklappbar bzw. per Drill-down erreichbar sein.

### Firma / Logo / PDF
- Firmenstammdaten müssen administrativ bearbeitbar sein.
- Firmenlogo muss hochladbar/ersetzbar sein.
- PDF-Darstellung darf nicht separat vom Android-PDF-Vertrag erfunden werden.
- Vor Server-PDF-/Branding-Änderung wird der aktuelle Android-PDF-/Logo-Pfad geprüft und der gemeinsame Vertrag in `05_ANDROID_SERVER_VERTRAG.md` festgelegt.
- Gewünscht ist zusätzlich ein **kleiner PDF-Viewer / eine PDF-Vorschau** im Admin, damit Logo und PDF-Ausgabe vor Verwendung sichtbar geprüft werden können.
- SERVER ändert `app/**` nicht nebenbei; falls Android-Anpassung nötig ist, wird sie nur als Vertrag dokumentiert und dem ANDROID-Arbeitsbereich überlassen.

Reihenfolge:
1. Projektvertrag sichern;
2. Android-PDF-/Logo-Bestand prüfen;
3. Admin-UI/Statusdaten gezielt planen;
4. Admin fertigstellen;
5. danach erst Portal-Nacharbeit.


### ADMIN-FINISH-001 – PDF/Android-Abgleich abgeschlossen
- Android-HEAD `423f3789...` geprüft.
- Zentrale Firmenstammdaten, Firmenlogo und PDF-Logo-Layout werden bereits gemeinsam mit Server 1.0 verwendet.
- Android besitzt bereits echte PDF-Vorschau + internen PDF-Viewer.
- Für die Admin-Fertigstellung ist deshalb **keine Android-Codeänderung nötig**.
- Server-Admin bekommt auf derselben Datenbasis eine echte PDF-Vorschau/kleinen Viewer.
- Nächster Schritt: Admin-UI und Systemdaten implementieren.


## ADMIN-STORAGE-001 – Legacy-Vermessungsoriginale dürfen nicht doppelt zählen
Status: **SOURCE-FIX UMGESETZT / DEPLOY + RUNTIME-PRÜFUNG OFFEN**

Befund vom 28.09.2026 bei der Admin-Speicherprüfung:
- Der bestehende ältere Importpfad `survey_originals/from-file` verschiebt den physischen Inhalt auf einen `reference/survey-originals/...`-Speicherpfad.
- Der ursprüngliche Datensatz in `tb.files` bleibt dabei bestehen und erhält denselben neuen `storage_key`.
- Zusätzlich wird für dasselbe physische Original ein Datensatz in `tb.survey_originals` angelegt.
- Damit können `tb.files` und `tb.survey_originals` auf **denselben physischen Speicherpfad** zeigen.
- Die neue Admin-Speicheranzeige darf diese Altbestände nicht als zwei Dateien bzw. doppelte Bytes zählen.

Verbindliche Korrektur:
1. physischer TB-Helfer-Speicher wird nach eindeutigem `storage_key` gezählt;
2. ein `tb.files`-Datensatz, dessen `storage_key` bereits als `tb.survey_originals.storage_key` geführt wird, wird in der normalen Datei-/Foto-/PDF-/Sonstiges-Summe nicht erneut gezählt;
3. derselbe Inhalt erscheint fachlich ausschließlich unter **Trassierungsdaten/Vermessungsoriginale**;
4. keine Altbestandsdaten löschen, verschieben oder migrieren nur für die Statistik;
5. DB-/Originalschutz bleibt unverändert.

Zielstatus dieses Blocks:
- Quellstand korrigieren;
- Source-Prüfung;
- noch **kein** Runtime-/E2E-Nachweis ohne Deployment.

### ADMIN-STORAGE-001 – Source-Fix 28.09.2026
- `system_admin.py`: physische TB-Helfer-Gesamtbelegung dedupliziert nach `storage_key`.
- `system_admin.py`: Firmenkategorien Fotos/PDF/Sonstiges schließen `tb.files`-Zeilen aus, deren `storage_key` bereits als Vermessungsoriginal geführt wird.
- `admin_web.py`: Firmen-Detailansicht verwendet dieselbe Deduplizierung.
- `tb.survey_originals` bleibt bei gemeinsamem Speicherpfad die fachliche Kategorie **Trassierungsdaten**.
- Keine Datenmigration, kein Löschen, kein Verschieben von Altbeständen.
- Source-Assertions: PASS.
- GitHub Actions auf HEAD: Server/Android `failure`, aber `steps = null`; deshalb **kein Testnachweis**.
- Status: **QUELLSTAND FERTIG / NICHT DEPLOYT / RUNTIME-E2E OFFEN**.

## ADMIN-RUNTIME-002 – Praxistest 28.09.2026 / nächste Nacharbeit
Status: **DEPLOYT / TEILWEISE FUNKTIONIERT / FEHLER + UX-NACHARBEIT OFFEN**

Benutzerbefund nach Synology-Deployment des Admin-Blocks:
- neue Admin-Übersicht läuft praktisch und aktualisiert sich;
- sichtbare Anzeige `Auto-Aktualisierung · 5 Sek.` soll entfernt werden; die Funktion selbst soll weiter automatisch arbeiten;
- Bahnhof/Betriebsstelle: Eingabe `MIB` füllt den Namen nicht automatisch / Suche funktioniert im Praxistest nicht;
- `Firma verwalten` funktioniert im Praxistest nicht; Ursache vor Änderung gezielt eingrenzen;
- Trassierungs-/Vermessungsdaten wurden erfolgreich hochgeladen und erscheinen im Bestand;
- aktueller Import ist an Firma/Baustelle gebunden; Benutzer fordert, dass solche gemeinsamen Referenz-/Trassierungsdaten **allen Firmen bzw. berechtigten Nutzern** zur Verfügung stehen und nicht nur GTU Mobility;
- es fehlt eine klare Ansicht, wo hochgeladene Trassierungs-/Referenzdaten vollständig angesehen, gefiltert, verwaltet und – soweit fachlich erlaubt – korrigiert/zugeordnet werden können;
- Import-/Datenverwaltung soll moderner und besser flächenausgenutzt werden, insbesondere mit sinnvoller linker/rechter Aufteilung statt langer Einspalten-Seite.

Vor weiterer Codeänderung fachlich zu klären:
1. gemeinsamer globaler Referenzdatenbestand vs. firmen-/baustellenspezifische Projektdateien sauber trennen;
2. welche Daten global editierbar sind und welche Originale unveränderlich bleiben;
3. gewünschte Detailansicht für hochgeladene Dateien/ausgelesene Punkte/Trassen;
4. Laufzeitfehler `MIB` und `Firma verwalten` zuerst reproduzieren/diagnostizieren, nicht auf Verdacht umbauen.

Bis zur Klärung keine weitere Admin-Codeänderung.

## ADMIN-DATA-003 – Globaler Referenzdatenbestand + moderner Dateneditor
Status: **ANFORDERUNG BESTÄTIGT / VOR UMSETZUNG FEHLERDIAGNOSE**

Benutzerentscheidung vom 28.09.2026:
- Trassierungs-/Vermessungsdaten, die ein Administrator bzw. später ein berechtigter Nutzer hochlädt, sollen als **globaler gemeinsamer TB-Helfer-Referenzdatenbestand** allen Firmen/berechtigten Nutzern zur Verfügung stehen.
- Das gilt insbesondere für TRA/GRA/KF/KF.TXT/KV/GVP/KOR/CSV und daraus abgeleitete Strecken-/Festpunktdaten.
- Upload durch Firma A darf nicht zu einer privaten Kopie für Firma A führen; Firma B soll denselben zentralen Datenbestand verwenden können.
- Unveränderte Originaldatei bleibt geschützt und nachvollziehbar.
- Korrekturen/Bearbeitung erfolgen daneben an einer **bearbeitbaren Arbeits-/Katalogebene**, nicht durch stilles Umschreiben des Originals.
- Darstellung und Bedienung sollen sich fachlich an der bestehenden Android-Streckendatenansicht orientieren:
  - grafische Streckenübersicht;
  - ein-/ausblendbare Trassen/Quellen;
  - Festpunkte/Masten;
  - Zoom/Detailansicht;
  - Datei-/Quellinformationen;
  - bearbeitbare Zuordnung/Katalogdaten, soweit fachlich erlaubt.
- Admin-Datenverwaltung soll deutlich moderner werden: ca. 30–35 % links für Suche/Filter/Import/Quellen und 65–70 % rechts für grafische Übersicht, Details und Bearbeitung.
- Lange Formularstapel sollen reduziert werden.
- sichtbare `Auto-Aktualisierung · 5 Sek.`-Anzeige auf der Übersicht entfernen; automatische Aktualisierung bleibt aktiv.

Zusätzliche bestätigte Runtimefehler:
- `Firma verwalten` liefert HTTP 500 / `Internal Server Error`.
- Bahnhof-Autofill: Eingabe `MIB` zeigt keine Vorschläge und übernimmt keinen Namen.

Reihenfolge:
1. 500-Fehler Firmenverwaltung diagnostizieren und beheben;
2. MIB-/Bahnhofsdatenpfad diagnostizieren und beheben;
3. globalen Referenzdatenvertrag + DB-/API-Zielmodell festlegen;
4. Datenverwaltung zweispaltig/grafisch neu gestalten;
5. danach Runtime-/E2E prüfen.

### ADMIN-RUNTIME-002 – Firmenverwaltung 500 reproduziert
Status: **URSACHE EINGEGRENZT / FIX JETZT**

Praxisnachweis 28.09.2026 im laufenden API-Container:
- direkter Aufruf von `admin_company_branding_page(...)` reproduziert den Fehler;
- Traceback endet in `psycopg.ProgrammingError: only '%s', '%b', '%t' are allowed as placeholders, got '%'`;
- betroffene Abfrage in `server/tbserver/admin_web.py` enthält parametriertes SQL mit `LIKE 'image/%'` bzw. `NOT LIKE 'image/%'` und gleichzeitig `%s` für `company_id`;
- Psycopg interpretiert das literale `%` im LIKE-Muster als Platzhalter-Syntax.

Fixziel:
- LIKE-Prüfung ohne literales `%` in der parametrisierten Query formulieren;
- keine DB-/Datenänderung;
- danach denselben direkten Container-Test wiederholen.

## ADMIN-STRUCTURE-004 – Firmenzentrierte Admin-Verwaltung
Status: **NEUE STRUKTURANFORDERUNG GESICHERT / NOCH NICHT UMGESETZT**

Benutzerwunsch vom 28.09.2026 nach erfolgreichem Retest der Firmenverwaltung:
- Doppelungen in der Admin-Navigation reduzieren.
- Primärer Verwaltungsablauf soll firmenzentriert sein:
  1. Firma anlegen;
  2. `Firma verwalten` öffnen;
  3. innerhalb der Firma Benutzer hinzufügen/bearbeiten;
  4. innerhalb der Firma gebundene Geräte sehen/verwalten;
  5. innerhalb der Firma Baustellen, Speicher und Logo/PDF verwalten.
- Nutzer, Geräte, Baustellen und Speicher sollen damit nicht parallel als gleichrangige globale Hauptverwaltung und zusätzlich nochmals innerhalb einer Firma auftreten.
- Ziel: deutlich übersichtlichere Firmenverwaltung als zentraler Einstiegspunkt.

Vor Umsetzung fachlich zu klären:
- Welche globalen Hauptmenüpunkte bleiben zusätzlich bestehen (z. B. Übersicht, Strecken/Bahnhöfe, Trassierungsdaten, Katalog-Historie, App-Versionen, Systemstatus)?
- Sollen `Benutzer`, `Geräte`, `Baustellen` aus der Hauptnavigation komplett verschwinden oder als globale Such-/Gesamtübersicht unter `Firmen` erreichbar bleiben?
- Soll `Firma verwalten` Tabs oder eine linke Seitennavigation verwenden?
- Soll das Anlegen eines Benutzers direkt innerhalb der Firma erfolgen und die Firma automatisch vorausgewählt/fest sein?
- Geräte werden nicht manuell einer Firma zugewiesen, sondern bleiben über den Benutzer gebunden; die Firmenansicht zeigt/verwaltert diese Bindungen.

### ADMIN-STRUCTURE-004 – Benutzerentscheidungen bestätigt
Status: **STRUKTUR FESTGELEGT / UMSETZUNG FREIGEGEBEN**

Bestätigung vom 28.09.2026:
- `Benutzer`, `Geräte` und `Baustellen` werden aus der globalen Hauptnavigation entfernt.
- `Geräte` gehören vollständig in die Firmenverwaltung; keine gleichrangige globale Geräte-Hauptseite im normalen Admin-Menü.
- Firmenverwaltung erhält eine **linke Seitennavigation** statt der bisherigen horizontalen Reiter.
- Klick auf `Firmen` öffnet zuerst die Firmenliste; dort `+ Firma anlegen` und `Firma verwalten`.
- Innerhalb `Firma verwalten` werden mindestens gebündelt:
  - Übersicht;
  - Stammdaten;
  - Benutzer;
  - gebundene Geräte;
  - Baustellen;
  - Speicher;
  - Logo / PDF.
- Benutzer werden innerhalb der geöffneten Firma angelegt; Firma ist dabei fest vorausgewählt und muss nicht nochmals gewählt werden.
- Geräte bleiben technisch an Benutzer gebunden, werden administrativ aber vollständig im Firmenbereich angezeigt und verwaltet.
- Ziel-Hauptnavigation: Übersicht | Streckendaten | Bahnhöfe | Firmen | Trassierungs-/Referenzdaten bzw. später zusammengeführte Streckendaten | Katalog-Historie | App-Versionen | Systemstatus.

Keine Änderung an Android `app/**` erforderlich.

### ADMIN-STRUCTURE-004 – Source-Umsetzung 28.09.2026
Status: **QUELLSTAND FERTIG / SYNLOGY-UPLOAD + RUNTIME-RETEST OFFEN**

Umgesetzt:
- globale Hauptnavigation ohne separate Punkte `Benutzer`, `Geräte`, `Baustellen`;
- Dashboard-Schnellzugriff ebenfalls ohne diese Doppelpfade;
- `Firmen` ist der zentrale Organisations-Einstieg;
- Firmenliste modernisiert mit Firmenkarten und Kennzahlen Benutzer/Geräte/Baustellen;
- `Firma verwalten` verwendet linke Seitennavigation statt horizontaler Tabs;
- innerhalb der Firma: Übersicht, Stammdaten, Benutzer, Geräte, Baustellen, Speicher, Logo/PDF;
- Benutzer direkt in der geöffneten Firma anlegen/bearbeiten/aktivieren/löschen;
- gebundene Geräte direkt in der Firma blockieren, Bindung lösen oder komplett löschen;
- Baustellen direkt in der Firma anlegen; verantwortlicher TB wird aus den Firmenbenutzern gewählt;
- Auto-Aktualisierungs-Hinweis `5 Sek.` entfernt; automatische Aktualisierung bleibt aktiv.

Source-Prüfung:
- JavaScript-Blöcke Firmenworkspace/Firmenliste/Dashboard: PASS;
- neue Admin-Worksite-Route genau einmal vorhanden; Membership/Team/Change/Audit-Pfad vorhanden;
- keine Änderungen unter `app/**`;
- GitHub Actions erneut ohne Runner-Schritte (`steps = null`) → kein Testnachweis.

## ADMIN-COMPANY-005 – Aktivierungskarte/Gültigkeit/Geräteversion
Status: **FEHLER BESTÄTIGT / SOURCE-FIX JETZT**

Praxisbefund 28.09.2026:
- aus `Firma verwalten → Benutzer → Aktivierungskarte` führt `Zurück zu Benutzer` noch auf die alte globale `/admin/v1/users`-Seite;
- Aktivierungskarte/Firmen-Benutzeransicht soll die Zugriffs-Gültigkeit klar anzeigen, inklusive verbleibender Zeit bzw. `ohne Ablauf`;
- Firmen-Geräteansicht zeigt z. B. `1.3.19`/`1.3.4`, obwohl Release 1.3.25 veröffentlicht ist.

Ursache Geräteversion:
- `tb.devices.app_version/app_build` werden beim Geräte-Login/Aktivierung geschrieben;
- der normale authentifizierte Gerätepfad aktualisiert danach nur `last_seen_at`;
- nach App-Update bleibt deshalb die bei Aktivierung gemeldete Version als veralteter Stand stehen.

Verbindlicher Fix:
1. Aktivierungskarte erhält Firmenkontext und führt zurück zu `/admin/v1/companies/{company_id}/branding#users`;
2. Gültigkeit wird als Datum plus Restdauer (`noch X Tage`, `läuft heute ab`, `abgelaufen`, `ohne Ablauf`) sichtbar;
3. Geräteansicht kennzeichnet den vorhandenen DB-Wert bis zum Android-Vertragsupdate ausdrücklich als `zuletzt gemeldet`, statt ihn als sicher aktuelle installierte Version darzustellen;
4. aktuelle veröffentlichte Android-Version wird daneben als Server-Release angezeigt;
5. Android↔Server-Vertrag erweitern: aktuelle `app_version/app_build` müssen bei normalen Geräte-Kontakten aktualisiert werden; SERVER ändert dafür kein `app/**`.

### ADMIN-COMPANY-005 – Benutzerbearbeitung Gültigkeit
Status: **SOURCE-FIX FERTIG / DEPLOY-RETEST OFFEN**

Benutzerbestätigung 28.09.2026:
- neue Firmen-/Geräteansicht passt;
- im Benutzerbereich fehlt beim **Bearbeiten** noch dieselbe komfortable Gültigkeitsauswahl wie beim Anlegen;
- ergänzen: `Zugriff gültig` mit `Kein Ablauf`, `3 Monate`, `6 Monate`, `1 Jahr`, `Manuell`;
- `Letzter gültiger Tag` bleibt sichtbar; bei 3/6/12 Monaten wird das Datum automatisch gesetzt, bei `Manuell` frei gewählt, bei `Kein Ablauf` geleert;
- bestehende Gültigkeit wird beim Öffnen der Bearbeitung korrekt vorbefüllt.

Nur Server-Template/JavaScript; keine API-/DB-/Android-Vertragsänderung nötig.

Source-Umsetzung:
- Bearbeitungsformular enthält jetzt dieselbe Auswahl `Kein Ablauf / 3 Monate / 6 Monate / 1 Jahr / Manuell`;
- bestehende befristete Zugänge öffnen als `Manuell` mit vorhandenem Datum; unbefristete als `Kein Ablauf`;
- 3/6/12 Monate setzen den letzten gültigen Tag automatisch;
- `Kein Ablauf` leert und deaktiviert das Datum;
- `Manuell` verlangt einen gewählten letzten gültigen Tag;
- Template-JavaScript-Check: PASS.
- Status: **SOURCE-FIX FERTIG / SYNLOGY-UPLOAD + RETEST OFFEN**.

## ADMIN-DASH-006 – Übersicht verfeinern + Bahnhof/MIB reparieren
Status: **SOURCE FERTIG / SYNLOGY-DEPLOY + RUNTIME-RETEST OFFEN**

Benutzerauftrag vom 28.09.2026:
- Admin-Übersicht optisch weiter verbessern: moderner, ruhiger, informationsstärker und besser gegliedert;
- MIB-/Bahnhof-Autofill reparieren;
- weitere sichtbare Admin-Unstimmigkeiten aus dem aktuellen Praxisstand erkennen und bereinigen, ohne geschützte funktionierende Bereiche unnötig umzubauen.

Bereits eingegrenzter MIB-Fehler:
- `station_reference.py` erwartet standardmäßig `/app/data/...`;
- auf der Synology existiert der NAS-Ordner `data`, aber der aktuelle API-Service bindet diesen Ordner nicht in den Container ein;
- damit kann der Bahnhof-Referenzendpunkt die vorhandenen JSON-Dateien im Container nicht lesen.

Zusätzliche sichtbare Nacharbeit:
- verbliebene sichtbare Auto-Refresh-Beschriftung `automatisch aktualisiert` in der Bestandskarte entfernen; Hintergrundaktualisierung bleibt;
- Dashboard-Warnungs-/Statusdarstellung klarer machen, ohne historische Fehler still zu löschen;
- Bahnhofssuche soll einen echten Lade-/Datenfehler sichtbar melden statt ihn im Browser still zu verschlucken;
- Synology-/Containerpfad für Bahnhofsdaten explizit und read-only anbinden.

Keine Android-Codeänderung.

### ADMIN-DASH-006 – Bahnhofsdaten exakt geprüft
- Server- und Android-Referenzdateien sind blob-identisch.
- offizieller Eintrag: `MIH` = `Ingolstadt Hbf`.
- ein exakter Code `MIB` existiert in beiden Referenzdateien **nicht**.
- daher kein hart codierter falscher Alias `MIB` speichern.
- UI soll bei Tippfehlern/ähnlichen Kürzeln passende offizielle Vorschläge liefern; Auswahl übernimmt immer den offiziellen Code.
- gültiger Code `MIH` soll weiterhin sofort den Namen `Ingolstadt Hbf` füllen.


### ADMIN-DASH-006 – Source-Umsetzung
- Synology-API bindet `./data` künftig read-only als `/app/data` ein.
- Systemstatus erkennt fehlende Bahnhof-Referenzdateien selbst und meldet sie als Warnung.
- Bahnhofsuche:
  - offizielles `MIH` = `Ingolstadt Hbf` wird exakt priorisiert;
  - ungültiges `MIB` wird nicht gespeichert;
  - ähnliche offizielle Kürzel mit passendem Präfix werden als Tippfehlerhilfe vorgeschlagen;
  - Referenzdatenfehler werden im Browser sichtbar statt still verschluckt.
- Übersicht:
  - Bestand + Firmenspeicher auf breiten Displays nebeneinander;
  - modernere KPI-Karten, Schnellzugriff und Speicherbalken;
  - historische Warnungen können nach Prüfung direkt quittiert werden;
  - verbliebene sichtbare Auto-Refresh-Beschriftung entfernt;
  - fehlende Synchronisierung/Temperatur verständlicher beschriftet;
  - Hauptnavigation verwendet konsistent `Streckendaten`.
- JavaScript Source-Checks Dashboard + Bahnhof: PASS.
- Keine Android-Codeänderung.
- Compose-Änderung benötigt auf Synology **Container-Neuerstellung/Recreate**, ein reiner Restart reicht für den neuen `/app/data`-Mount nicht.


## ADMIN-PDF-007 – PDF-Logo-Layout reagiert nicht
Status: **RUNTIME-FEHLER BESTÄTIGT / SOURCE-FIX FERTIG / RETEST OFFEN**

Praxisstand 28.09.2026:
- Bahnhof-/Betriebsstellensuche nach Synology-Recreate praktisch bestätigt: **FUNKTIONIERT**; `MIH` füllt `Ingolstadt Hbf` und Vorschläge werden angezeigt.
- PDF-Logo-Layout dagegen **NICHT BESTANDEN**:
  - Logo-Breite lässt sich im Formular verändern;
  - X-Versatz lässt sich verändern;
  - Y-Versatz lässt sich verändern;
  - die echte PDF-Vorschau bleibt optisch unverändert.
- Fehler betrifft damit nicht den Slider selbst, sondern Speichern/Laden oder die Übergabe der Layoutwerte an den serverseitigen PDF-Renderer.
- Keine Änderung auf Verdacht: zuerst Request/Persistenz/Renderer-Pfad und Android↔Server-PDF-Vertrag prüfen.
- Geschützte funktionierende Bahnhofssuche nicht wieder ändern.

### ADMIN-PDF-007 – Synology-Compose-Warnung mit erkannt
Praxisbefund 28.09.2026:
- beim Synology-Recreate erscheinen Warnungen: `POSTGRES_USER variable is not set` und `POSTGRES_DB variable is not set`;
- Ursache im Quellstand bestätigt: Healthcheck von `restore-test-db` verwendet `${POSTGRES_USER}` / `${POSTGRES_DB}` statt der innerhalb des Containers auszuwertenden Form `$${POSTGRES_USER}` / `$${POSTGRES_DB}`;
- Korrektur ist rein Compose-seitig und ändert keine Zugangsdaten oder produktiven DB-Werte.


### ADMIN-PDF-007 – Source-Fix
Status: **SOURCE-FIX FERTIG / SYNLOGY-RETEST OFFEN**

Ursachen bestätigt:
1. `Vorschau aktualisieren` renderte ausschließlich den bereits gespeicherten DB-Stand; aktuelle, noch nicht gespeicherte Reglerwerte wurden nicht an den Preview-Endpunkt übergeben.
2. Der Server-Firmenkopf reservierte nur 48 mm Höhe. Besonders quadratische Logos wurden deshalb selbst bei großer `logo_width_pt`-Einstellung wieder stark verkleinert.

Umgesetzt:
- Admin-PDF-Vorschau akzeptiert aktuelle, nicht gespeicherte Layoutwerte als reine GET-Vorschau; Persistenz bleibt ausschließlich beim bewussten `PDF-Layout speichern`.
- Regler/Nummernfelder aktualisieren die Vorschau nach kurzer Verzögerung automatisch.
- `Vorschau aktualisieren` und `Groß öffnen` verwenden die aktuellen Reglerwerte.
- negative X/Y-Werte können im Zahlenfeld sauber eingegeben werden.
- Server-`CompanyHeaderFlowable` verwendet jetzt 62 mm Headerhöhe und eine Android-nahe Logo-Zone bis ca. 174 pt.
- PDF-Renderer-Version auf `tb-server-btb-pdf-4` angehoben.
- finaler BTB-PDF-Pfad nutzt denselben korrigierten Firmenkopf.
- Synology-`restore-test-db`-Healthcheck wertet POSTGRES_USER/POSTGRES_DB jetzt im Container aus.
- keine Migration, keine DB-Datenänderung, keine Android-Codeänderung.
- Template-JavaScript-Check und Source-Assertions: PASS.

## ADMIN-DASH-008 – Festplatten-Temperaturen in °C
Status: **SOURCE FERTIG / SYNOLOGY-RECREATE + RUNTIME-RETEST OFFEN**

Benutzeranforderung vom 28.09.2026:
- die Admin-Übersicht/Systemanzeige soll die Temperaturen der physischen Synology-Festplatten deutlich sichtbar anzeigen;
- Anzeige primär in **°C**, je erkanntem Laufwerk getrennt (z. B. Laufwerk 1 / Laufwerk 2);
- die Werte müssen aus realen Host-/NAS-Metriken stammen, keine statischen Demo-Werte;
- bestehende funktionierende Dashboard-/Bahnhof-/PDF-Pfade nicht auf Verdacht ändern.

Vor Implementierung prüfen:
1. aktuellen Systemmetrik-Pfad und vorhandene Disk-/SMART-Datenquelle im Server-HEAD;
2. Verhalten wenn Temperaturdaten auf einem Host/Container nicht verfügbar sind;
3. Synology-Runtime nach Deployment gegen die DSM-Laufwerkswerte vergleichen.

### ADMIN-DASH-008 – Source-Umsetzung
- Bestehender Serverstand hatte nur eine generische Systemtemperatur aus `/sys/class/thermal`; keine Laufwerkstemperaturen.
- SERVER liest jetzt je Synology-Disk die reale Temperaturdatei unter dem read-only Hostpfad `/run/synostorage/disks/<device>/temperature`.
- Compose bindet diese Quelle ausschließlich read-only nach `/host/synostorage/disks` in den API-Container ein.
- API-Status liefert `host.disk_temperatures[]` mit Laufwerkslabel, technischer Device-Bezeichnung und `temperature_c`.
- Dashboard zeigt z. B. `Laufwerk 1: 36 °C · Laufwerk 2: 37 °C`; ohne Hostdaten `Nicht verfügbar`.
- Source-Marker/Commit-Diff geprüft; keine DB-/Android-Änderung.
- Runtime/E2E noch offen: Synology-Projekt/API mit aktualisiertem Compose recreaten und Werte direkt gegen DSM vergleichen.
## PDF-RUNTIME-009 – Server-PDF seit 22.09. sichtbar verschoben
Status: **SOURCE-FIX FERTIG / SYNLOGY-UPLOAD + VISUAL-RETEST OFFEN**

Benutzerbefund 29.09.2026:
- PDF vom **22.09.** war noch optisch in Ordnung.
- spätere serverseitig erzeugte/ausgegebene PDFs sind sichtbar verschoben bzw. im Layout verändert.
- der aktuelle **Android-PDF-Entwurf auf dem Handy** sieht dagegen korrekt aus.
- Verdacht liegt damit auf dem **serverseitigen PDF-Render-/Exportpfad**, ist aber noch nicht als Ursache bestätigt.
- Benutzer hat Vergleichsarchive für 22.09., 25.09. und 26.09. bereitgestellt.

Verbindliche Diagnose vor Fix:
1. Vergleichsarchive vollständig öffnen und enthaltene PDFs/Metadaten gegenüberstellen;
2. 22.09-Serverstand bzw. relevante PDF-Renderer-Änderungen bis heute in GitHub nachvollziehen;
3. Android-Renderer und Server-Renderer auf Seitengröße, Ränder, Headerhöhe, Skalierung, Tabellen-/Flowable-Breiten und Logo-Offsets vergleichen;
4. feststellen, ob nur Firmenkopf/Logo oder das gesamte PDF-Layout verschoben ist;
5. keine Android-Änderung, solange der aktuelle Android-PDF-Entwurf korrekt bleibt;
6. erst nach reproduzierbarer Serverursache gezielt korrigieren und mit den historischen PDFs visuell gegenprüfen.

### PDF-RUNTIME-009 – Diagnose 29.09.2026
- Drei vom Benutzer bereitgestellte Tagesexporte wurden direkt verglichen: 22.09., 25.09. und 26.09.
- 22.09-BTB-PDF:
  - Producer: `Skia/PDF m148`;
  - 595 × 842 pt A4;
  - entspricht dem Android-`PdfDocument`-/`BautagebuchPdf`-Layout mit Wetterbox, Personal/Maschinen zweispaltig, gerahmten Punkten 4–8 und Android-Fotoanhang.
- 25.09- und 26.09-BTB-PDF:
  - Producer: `ReportLab PDF Library`;
  - Footer: `PDF-Renderer: tb-server-btb-pdf-3`;
  - enthalten den generischen Server-Renderer mit Meta-/Kopfdaten-Tabellen, flach ausgegebenen Payloads und einfachem Fotoanhang.
- Der aktuelle Handy-`PDF Entwurf` wird weiterhin mit dem Android-Renderer erzeugt und sieht laut Benutzer korrekt aus.
- Damit ist der sichtbare Layoutsprung **nicht** durch die Android-PDF-Logik verursacht. Der Serverabschluss verwendet einen zweiten, optisch nicht paritätischen Renderer.
- Der aktuelle Server-`btb_pdf.py` ist auch in Renderer v4 fachlich weiterhin ein eigenständiges ReportLab-Layout; die Änderung v4 betrifft Firmenkopf/Logo, nicht die fehlende Android-Layoutparität des Hauptdokuments.
- Korrekturziel: serverseitigen Abschluss-PDF-Renderer an das bestehende Android-PDF-Zielbild angleichen; Android nicht ändern, solange der aktuelle Entwurf korrekt bleibt.

## TOOLING-001 – Verbindliche Entwicklungs-Plugins SERVER
Status: **VERBINDLICHE ARBEITSWEISE**

Für das SERVER-/Integrationsprojekt sind aktuell verbunden und freigegeben:
- **GitHub** – verbindliche Quelle für Repository, Branches, HEAD, PRs, Commits, CI und Projektdateien.
- **Context7** – für aktuelle Framework-/Bibliotheksdokumentation, insbesondere FastAPI/Python/Pydantic/PostgreSQL/Psycopg und weitere verwendete Abhängigkeiten.
- **Postman** – für API-Discovery, API-Tests, Dokumentation, Mocking, Monitoring, Performance- und CI-nahe API-Prüfungen.

Regeln:
1. GitHub bleibt Projektgedächtnis und Wahrheit. Context7/Postman ersetzen niemals den vorgeschriebenen GitHub-Einstieg oder die Projektsteuerung.
2. Context7 wird bei versionsabhängigen Framework-/Bibliotheksfragen bevorzugt für aktuelle Hersteller-/Bibliotheksdokumentation genutzt. Repository-Code und dokumentierter TB-Helfer-Vertrag haben bei projektspezifischen Entscheidungen Vorrang.
3. Postman darf zur reproduzierbaren Prüfung der TB-Helfer-API genutzt werden, insbesondere für Health, Auth/Geräte, Firmen/Benutzer, Baustellen/Teams, BTB, Dateien/Bilder, Sync/Change-Feed, Mängel, Abnahmen und Releases.
4. Postman-Source-/API-Tests sind nur dann ein Runtime-/E2E-Nachweis, wenn sie tatsächlich gegen den maßgeblichen deployten Server mit dokumentiertem Testablauf ausgeführt wurden. Collection-/Mock-/Schema-Erfolg allein ist kein Synology-E2E.
5. Keine produktiven Secrets, Geräte-Tokens, Passwörter oder sonstigen Zugangsdaten in Collections, Doku, Repository oder Chat fest eintragen. Für Tests Platzhalter/Umgebungsvariablen und wenn möglich Staging-/Testdaten verwenden.
6. Schreibende oder sicherheitsrelevante Aktionen gegen produktive Daten/Server nur mit ausdrücklichem Benutzerauftrag. Keine DB-, Release-, Benutzer-, Geräte-, Datei- oder Deployment-Änderung auf Verdacht.
7. Plugins ersetzen den festgelegten Synology-Arbeitsweg nicht. File Station + Container Manager + Container-Terminal bleiben Standard; NAS-SSH nur auf ausdrücklichen Benutzerwunsch.
8. Weitere Plugins gelten erst nach ausdrücklicher Entscheidung/Verbindung als Teil der verbindlichen SERVER-Werkzeugkette.

Aktive SERVER-Werkzeugkette: **GitHub + Context7 + Postman**.
### PDF-RUNTIME-009 – Benutzerfreigabe Umsetzung
Status: **SERVER-UMSETZUNG FREIGEGEBEN**
- Benutzer bestätigt am 29.09.2026 mit `okay`, dass der eingegrenzte Server-PDF-Renderer-Fehler jetzt behoben werden soll.
- Referenz bleibt das optisch korrekte Android/Skia-PDF vom 22.09. sowie der aktuell korrekte Handy-PDF-Entwurf.
- Android `app/**` nicht ändern; Fix ausschließlich im Server-PDF-Pfad, anschließend Source- und Visual-Regressionstest.

### PDF-RUNTIME-009 – Source-Umsetzung v5
- Code-Commit: `d2c3ca9e046abdc3f176e16a96148dfda2260dca` (`fix(pdf): align server BTB PDF with Android layout`).
- Neuer Renderer: `server/tbserver/btb_pdf_android_style.py`.
- `server/tbserver/btb_pdf.py` verwendet für den autoritativen Abschluss jetzt `tb-server-btb-pdf-5`; der alte generische ReportLab-Renderer bleibt nur als Legacy-Implementierung im Quelltext und wird nicht mehr vom normalen `render_btb_pdf()`-Pfad aufgerufen.
- Layoutziel aus `BautagebuchPdf.kt`/22.09 übernommen: A4 595×842, Android-nahe Ränder, Firmenkopf rechts, Wetter kompakt, Personal/Maschinen zweispaltig, Punkte 4–8 als Rahmen, Signatur, Fotoanhang.
- Fachliche Serverdaten werden Android-nah aufbereitet: FIRMA/PERSONAL/GERAET/ARBEIT/ABNAHME/EINWEISUNG/BESONDERHEIT/MANGEL, `worksiteNodeKey`, KM, Foto-Metadaten und Bildnummern.
- Nachbearbeitungsmodell bleibt erhalten: neue/geänderte bzw. entfernte Inhalte werden weiterhin kenntlich gemacht; Original-Snapshot wird nicht verändert.
- `_pdf_context()` liefert zusätzlich Lead-E-Mail/Mobil und die vorhandenen Worksite-Nodes read-only; keine Migration.
- Android `app/**` unverändert.
- Lokaler synthetischer Renderer-Smoke: gültiges A4-PDF erzeugt und visuell gegen das 22.09-Zielbild geprüft.
- CI auf Code-HEAD: TB-Server Runs `36551663700` und `36551656529`, Android Run `36551663628`; jeweils `failure` ohne Runner (`runner_name=""`, `steps=[]`) → **kein Testnachweis / kein Sourcefehler-Nachweis**.
- Praktischer nächster Schritt: neue/änderte Server-Python-Dateien auf Synology übernehmen, API neu starten und einen neuen Test-/Tagesabschluss erzeugen; das erzeugte Server-PDF direkt mit Handy-Entwurf und 22.09-Referenz vergleichen.
### PDF-RUNTIME-009 – Screenshot-Runtime 29.09.2026
Status: **ADMIN-PREVIEW UND ANDROID-NAHES BTB-PDF SICHTBAR UNTERSCHIEDLICH / QUELLE DES ZWEITEN BILDS NOCH BESTÄTIGEN**
- Benutzer zeigt zwei aktuelle Screenshots.
- Bild 1 ist eindeutig die serverseitige Admin-`PDF-Vorschau`: sehr großes Firmenlogo und generische Beispielinhalte.
- Bild 2 zeigt das Android-nahe BTB-Zielbild mit Wetterbox, Personal/Maschinen zweispaltig, Punkten 4–8 und Unterbaustellen/KM.
- Noch **nicht bestätigt** ist, ob Bild 2 bereits der neue serverseitige Renderer-v5-Abschluss oder der weiterhin korrekte Handy-`PDF Entwurf` ist. Vor weiterer Codeänderung diese Herkunft klären.
- Falls Bild 2 der Serverabschluss ist, ist das Hauptlayout deutlich verbessert; Firmenkopf/Logo weichen aber sichtbar von der Admin-Vorschau ab.
- Falls Bild 2 der Handy-Entwurf ist, ist für Renderer v5 noch kein visueller Synology-Nachweis erbracht.
- Rote Überschriften/Inhalte und `Änderungen (entfernt)` können zum bestehenden Rework-/Original-Snapshot-Diff gehören; nicht ohne bestätigten Testfall entfernen.
- Nächste Diagnose nach Quellenbestätigung: Preview-/Final-Header-Parität und ggf. Rework-Diff gezielt prüfen.
### PDF-RUNTIME-009 – Zielbild bestätigt
Status: **ZIELBILD VERBINDLICH / ADMIN-PREVIEW MUSS ANGEPASST WERDEN**
- Benutzer bestätigt am 29.09.2026 ausdrücklich:
  - **Bild 2 ist das gewünschte PDF-Zielbild**;
  - **Bild 1 ist die aktuelle Admin-PDF-Vorschau**.
- Damit ist die Admin-Vorschau aktuell fachlich/optisch irreführend und muss denselben Android-nahen BTB-Aufbau zeigen wie das bestätigte Zielbild.
- Ziel für die Admin-Vorschau: gleicher Firmenkopf/Logo-Mechanismus und gleiche Hauptstruktur wie der serverseitige Abschlussrenderer v5; Beispielinhalt darf synthetisch sein, die Seitenstruktur darf nicht mehr das alte generische Tabellenlayout verwenden.
- Android `app/**` bleibt unverändert.

### PDF-RUNTIME-009 – Admin-Preview Source-Fix
- Benutzer hat Bild 2 als Zielbild und Bild 1 als Admin-Vorschau bestätigt.
- Commit `972017bf893c1dc348a7f52d39863c4c9fbeda02` stellt die Admin-Vorschau auf denselben Android-nahen Renderer wie den finalen BTB-Abschluss um.
- `render_company_preview_pdf()` erzeugt nun ein synthetisches BTB-Snapshot und rendert es über `render_android_style_btb_pdf()`.
- Logo-Reglerwerte und Firmenkopf laufen dadurch in Preview und Final durch dieselbe Zeichenlogik.
- Personal/Maschinen werden mit unabhängigen Boxhöhen gerendert; nächster Abschnitt startet unter der höheren der beiden Boxen.
- Android `app/**` unverändert; keine Migration, kein Compose.
- GitHub Actions auf Code-HEAD `972017bf...`: Server Runs `36554111617`/`36554106396`, Android `36554111626`, jeweils ohne Runner (`runner_name=""`, `steps=[]`) → kein Testnachweis.
- Nächster Schritt: `btb_pdf.py` + `btb_pdf_android_style.py` auf Synology übernehmen, API neu starten und Admin-PDF-Vorschau erneut öffnen.
### ADMIN-PDF-011 – Logo bis 400 pt + Y-Versatz Runtimefehler
Status: **ANFORDERUNG GESICHERT / URSACHE EINGEGRENZT**
- Benutzer bestätigt in der aktuellen Synology-Admin-Vorschau:
  - Logo-Breite soll von bisher maximal **250 pt auf 400 pt** erweitert werden;
  - **Y-Versatz wirkt aktuell nicht sichtbar** und muss funktionieren.
- Screenshot-Teststand: `logo_width_pt=250`, `offset_x_pt=70`, `offset_y_pt=-60`.
- Source-Diagnose: der v5-Serverrenderer begrenzt quadratische/hohe Logos aktuell auf `max_height=88 pt`; anschließend wird `top_y` so geklemmt, dass bei einem auf diese Maximalhöhe skalierten Logo der Y-Spielraum praktisch auf einen festen Wert zusammenfällt. Das erklärt den nicht wirksamen Y-Regler.
- SERVER-Ziel: `logo_width_pt` 40..400 akzeptieren/speichern/rendern und Y-Versatz sichtbar anwenden, jeweils mit sicheren Seitengrenzen.
- Weil `logo_width_pt` ein gemeinsames Android↔Server-Feld ist, muss die Erweiterung 250→400 im Vertrag dokumentiert werden; SERVER ändert kein `app/**`.

### ADMIN-PDF-011 – Source-Fix
- Commit `de7374dc392959131326815c30ebdb2733eea705`.
- `company.py`: Pydantic-/Preview-/Normalisierungsgrenze auf 400 pt.
- `v1_company_branding.html`: Slider, Zahlenfeld, Live-Preview und Speichern auf 40..400.
- `btb_pdf_android_style.py`: 400-pt-Renderbereich; Y wird aus `base_top + offset_y_pt` berechnet und nur an realen A4-Grenzen geklemmt.
- Bei sehr großem Logo wird der nachfolgende BTB-Inhalt unter den tatsächlichen Firmenkopf geschoben statt ihn vertikal zu überdecken.
- `btb_pdf.py` Legacy-Clamp ebenfalls auf 400 angehoben.
- Source-Marker geprüft: alle Server-Clamps/HTML-Maxwerte auf 400 vorhanden.
- GitHub Actions auf Code-HEAD: Server `36555229698`/`36555222572`, Android `36555229687`; alle ohne Runner (`runner_name=""`, `steps=[]`) → kein Testnachweis.
- Synology-Retest: 4 Runtime-Dateien übernehmen, API neu starten, Breite 250→400 und Y -60/0/+60 sichtbar vergleichen.
### ADMIN-PDF-012 – Logo unabhängig vom Tabellenlayout
Status: **ANFORDERUNG GESICHERT / SERVER-FIX FREIGEGEBEN**
- Benutzer bestätigt den 400-pt-/Y-Fix praktisch als funktionierend.
- Neue verbindliche Vorgabe: **Logo-Größe sowie X/Y-Verschiebung dürfen die Position der BTB-Tabellen/Fachblöcke nicht verändern.**
- Wetter, Personal/Maschinen sowie Punkte 4–8 müssen auf ihrer festen Zielposition bleiben, auch wenn das Logo vergrößert oder verschoben wird.
- Das Logo bleibt ein unabhängig positionierter Firmenkopf-Layer. Bei extremen Einstellungen darf es optisch in den Inhaltsbereich hineinragen; der Renderer darf dafür aber nicht das gesamte Fachlayout nach unten schieben.
- Android `app/**` nicht ändern; Korrektur nur im Serverrenderer/der Admin-Vorschau.

### ADMIN-PDF-012 – Source-Fix
- Benutzer bestätigt: 400-pt-Breite/Y-Versatz funktionieren, aber Tabellen dürfen beim Vergrößern/Verschieben des Logos nicht nach unten rutschen.
- Code-Commit `be196108f77192bc4ea676d6dcb32092076f1151`.
- `btb_pdf_android_style.py`: Rückkopplung `company_header_bottom -> writer.y` entfernt.
- Dokumentkopf beginnt wieder fest bei `MARGIN_TOP + 48.0`; danach folgt der normale feste Abstand.
- Admin-Vorschau und finaler Serverabschluss verwenden denselben korrigierten Renderer.
- Sourcecheck erweitert um Marker für entkoppeltes Firmenlogo/Fachlayout.
- GitHub Actions Run `36557039970`: `failure`, `runner_name=""`, `steps=[]` → kein Testnachweis.
- Synology-Retest: nur `btb_pdf_android_style.py` übernehmen, API neu starten, Logo-Breite/Y ändern und prüfen, dass Wetter/Tabellen pixelgleich stehen bleiben.
### ADMIN-PDF-013 – BAUTAGEBUCH-Titel bleibt sichtbar
Status: **RUNTIME-FEHLER BESTÄTIGT / SERVER-FIX FREIGEGEBEN**
- Benutzer bestätigt ADMIN-PDF-012 praktisch weitgehend: Tabellen/Fachblöcke bleiben nun fest.
- Neuer sichtbarer Restfehler bei sehr großem/verschobenem Logo: der Titel **`BAUTAGEBUCH` wird vom Logo bzw. dessen weißem Hintergrund überdeckt/abgeschnitten**.
- Ziel: `BAUTAGEBUCH` muss unabhängig von Logo-Breite/X/Y vollständig lesbar bleiben.
- Fachlayout/Tabellenposition darf dabei unverändert bleiben.
- SERVER-Lösung: Firmenlogo zuerst zeichnen; den linken Titelbereich anschließend als eigene oberste Ebene mit weißem Hintergrund + Titeltext rendern.
- Android `app/**` nicht ändern.

### ADMIN-PDF-013 – Source-Fix
- Anforderung vor Code gesichert.
- Code-Commit `42de28d2422506d4fb594b19c38f8e404d598a22`.
- `btb_pdf_android_style.py`: `BAUTAGEBUCH` wird nicht mehr vor dem Logo gezeichnet, sondern nach Logo + Firmenkontakt als oberste Ebene.
- Hinter dem Titel wird ein kleiner weißer Schutzbereich gezeichnet; dadurch kann der transparente/weiße Logo-Hintergrund keine Buchstaben mehr abschneiden.
- Keine Änderung an festen Wetter-/Tabellenpositionen.
- Sourcecheck um Titel-Layer-Marker ergänzt.
- GitHub Actions Run `36558072863`: `failure`, `runner_name=""`, `steps=[]` → kein Testnachweis.
- Nächster Schritt: nur `btb_pdf_android_style.py` auf Synology ersetzen, API neu starten, denselben 400-pt-/X/Y-Test erneut öffnen.

### PDF-ADMIN – Benutzerabnahme 29.09.2026
Status: **BESTÄTIGT / ABGENOMMEN**
- Benutzer meldet nach Upload/API-Neustart und praktischem Sichttest: **`passt und abgenommen`**.
- Abgenommen ist der aktuelle SERVER-Admin-PDF-/Logo-Layoutpfad inklusive 400 pt, X/Y, fester Tabellenposition und Titel-Schutzlayer.
- Geschützte Punkte: diese bestätigten SERVER-PDF-Anpassungen nicht ohne ausdrücklichen Auftrag, reproduzierbaren Fehler oder zwingende neue Fachanforderung ändern.
- ADMIN-PDF-007 / -010 / -012 / -013: **ERLEDIGT**.
- ADMIN-PDF-011: SERVER **ERLEDIGT/BESTÄTIGT**, Android-Clamp 250→400 bleibt als separater Android-Vertragspunkt offen.
- PDF-RUNTIME-009 bleibt fachlich getrennt: finaler serverseitiger Tagesabschluss-PDF-E2E noch nicht durch diese Admin-Abnahme ersetzt.
## REF-DATA-001 – SERVER-Umsetzung globaler Strecken-/Vermessungsreferenzen
Status: **PHASE 1 SOURCE FERTIG / SYNLOGY-DEPLOY + RUNTIME-TEST OFFEN**

Grundlage ist die bereits bestätigte Fachanforderung REF-DATA-001.

Phase-1-Vertrag SERVER:
- bestehende `tb.survey_originals` werden ohne Schema-Bruch genutzt:
  - `company_id IS NULL` = `GLOBAL_REFERENCE`;
  - `company_id IS NOT NULL` = `COMPANY_WORKSITE_DATA`;
- globale Originale bleiben **unveränderbar**; kein PATCH/PUT/DELETE und keine stille Überschreibung;
- globale Duplikaterkennung erfolgt über unveränderte Dateiidentität (`sha256` + Bytegröße);
- globaler Upload erfolgt zunächst ausschließlich über den bestehenden ADMIN-Bereich und benötigt keinen Firmen-/Baustellenbesitz;
- firmen-/baustellenbezogener Import bleibt unverändert getrennt und benötigt weiterhin Baustellenkontext;
- alle authentifizierten Nutzer dürfen globale Referenzoriginale lesen; fremde firmenbezogene Originale bleiben gesperrt;
- globaler Manifest-/Syncvertrag:
  - `GET /api/v1/reference/survey-originals` liefert nur Metadaten + Parserstatus, keine großen Parserpayloads;
  - `GET /api/v1/reference/survey-originals/{id}/parsed` liefert den fachlich geparsten Inhalt;
  - unveränderte Originalbytes bleiben über den bestehenden Content-Endpunkt abrufbar;
  - Manifest enthält Datei-SHA, Parser-Version und `parsed_at`, damit Android später gezielt nachladen/cachen kann;
- bestehende Worksite-`track_data`/`survey-sections` bleiben projektbezogene Arbeitsdaten; sie dürfen globale Originale bereits als Quelle referenzieren;
- der globale bearbeitbare Strecken-/Bahnhofs-/Objektkatalog bleibt separat in `tb.catalog_*` mit Versionen/History; diese Phase ändert dessen Schreibrechte nicht und globales Löschen bleibt verboten;
- SERVER ändert kein Android-`app/**`; Android-Nachlade-/Cachepfad wird nur im Vertrag beschrieben.

Phase-1-Umsetzung:
1. Admin-Trassierungsimport um Ziel `GLOBAL_REFERENCE` vs. `COMPANY_WORKSITE_DATA` erweitern;
2. globale Referenzmanifest-/Parsed-API ergänzen;
3. Scope in bestehender Original-Liste sichtbar machen;
4. Capabilities/Sourcechecks ergänzen;
5. anschließend Sourceprüfung und Synology-Runtime-Test vorbereiten.

### REF-DATA-001 – Phase-1-Sourceblock
- Vertragscommit: `498b5a8b0fae3ce137cc3bc297daa1566088735c`.
- Hauptimplementierung: `6440f538268f28857b4d9557a19a03e8d287ed72`.
- Templatekorrektur: `d69b71e0a86d46f7dfb789907881cb4324318506`.
- Manifest-/Scope-Nachschärfung: `a0701329b8b09c25efc67ac5a0f679abe822903a`.
- Geändert:
  - `server/tbserver/admin_survey_import.py`;
  - `server/tbserver/survey_originals.py`;
  - `server/templates/v1_admin_survey_import.html`;
  - `server/tbserver/capabilities.py`;
  - Sourcecheck `25_verify_tb_server_1_track_survey_source.py`.
- Keine Migration erforderlich: vorhandenes nullable `survey_originals.company_id` ist bewusst nutzbar und bestehende Source-Validierung erlaubt globale (`NULL`) Originale.
- Globale Importe erzeugen absichtlich keinen firmenbezogenen `change_feed`; Android-Nachladen läuft später über das neue globale Manifest.
- Sourcecode/Template nach Commit-Diff erneut geprüft; Sourcecheck ist erweitert, aber auf GitHub nicht ausgeführt worden.
- GitHub Actions auf Code-HEAD `a0701329...`: Server Runs `36561890727` und `36561884228`, Android Run `36561890760`; jeweils `runner_name=""`, `steps=[]` → **kein Testnachweis**.
- Nächster Schritt: vier Runtime-Dateien auf Synology übernehmen, API neu starten und Global-/Worksite-Import + Manifest praktisch prüfen.

### REF-DATA-001 – Importablauf korrigiert: Datei zuerst
Status: **NEUE FACHANFORDERUNG GESICHERT / NOCH NICHT UMGESETZT**
- Benutzer lehnt den aktuellen Ablauf `Importziel/Scope + Firma/Baustelle + Strecke/Gleis zuerst` als fachlich verkehrt ab.
- Verbindlicher Zielablauf für ADMIN und später normalen Nutzerupload:
  1. zuerst Datei(en) auswählen bzw. ablegen;
  2. Server analysiert Dateityp und fachlich erkennbare Inhalte vor dem eigentlichen Import;
  3. pro Datei werden erkannte Angaben angezeigt, insbesondere Format/Typ sowie soweit vorhanden Strecke, Gleis/Achse und weitere fachliche Zuordnung;
  4. bereits in Datei/Inhalt eindeutig vorhandene Angaben werden automatisch übernommen und nicht unnötig erneut abgefragt;
  5. nur fehlende oder mehrdeutige Pflichtangaben werden vom Benutzer ergänzt/korrigiert;
  6. erst danach `Import starten`;
  7. nach Import zeigt die Oberfläche eindeutig, was erkannt, ergänzt, importiert, übersprungen oder als Duplikat erkannt wurde.
- Mehrfach-/ZIP-Upload muss dateibezogen funktionieren: jede erkannte Datei kann eigene erkannte/fehlende Metadaten haben.
- Der gleiche Grundablauf soll später auch für normale Nutzer gelten; SERVER ändert Android `app/**` nicht.
- Noch fachlich zu klären: wie `GLOBAL_REFERENCE` gegenüber `COMPANY_WORKSITE_DATA` im neuen Datei-zuerst-Ablauf entschieden/angezeigt wird. Diese Entscheidung wird vor Umbau der Importlogik bestätigt.

### REF-DATA-002 – Grafische Übersicht der Strecken-/Vermessungsdaten
Status: **NEUE FACHANFORDERUNG GESICHERT / KONZEPTION VOR CODE**
- Zusätzlich zum Datei-zuerst-Import braucht TB-Helfer eine **grafische Übersicht der erkannten und importierten Daten**.
- Die Grafik ist kein dekoratives Extra, sondern Teil der fachlichen Prüfung:
  - vorhandene TRA-/Gleisgeometrie sichtbar machen;
  - erkannte Kilometerbereiche und Kilometeranker anzeigen;
  - KF-/Fest-/Absteckpunkte mit Punktart und Position darstellen;
  - Bahnhöfe, Gleise, Weichen/Kreuzungen und weitere Referenzobjekte soweit vorhanden einblenden;
  - Datenherkunft je Element unterscheidbar machen (Original/Referenz vs. manuell/bearbeitet);
  - fehlende, widersprüchliche oder nicht zuordenbare Daten sichtbar markieren.
- Es werden zwei Ansichten benötigt:
  1. **Import-Vorschau vor dem Import** direkt nach der Dateianalyse, damit der Benutzer erkennt, was der Server aus den Dateien gelesen hat;
  2. **dauerhafte Referenzdaten-Übersicht nach dem Import** für den gesamten vorhandenen Bestand mit Filtern nach Strecke, Gleis/Achse, Datenart, Quelle und Kilometerbereich.
- Die Übersicht muss Zoom/Pan und Auswahl einzelner Elemente unterstützen; Klick auf ein Element zeigt Metadaten/Quelle, ändert aber keine Originaldaten.
- Originaldaten bleiben immutable. Bearbeitung erfolgt nur in getrennten Arbeits-/Katalogdaten.
- SERVER baut zunächst Datenmodell/API/Admin-Visualisierung; Android-Visualisierung bleibt eigener ANDROID-Arbeitsblock.

### REF-DATA-002 – Benutzerfreigabe Datei-zuerst + Grafik
Status: **PHASE A SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Benutzer bestätigt am 29.09.2026 den kombinierten Zielablauf aus Datei-zuerst-Analyse und grafischer Übersicht mit `okay`.
- Für die erste Serverumsetzung gilt:
  - Datei auswählen/ablegen;
  - Voranalyse ohne Datenbankimport;
  - erkannte Metadaten + grafische Lage-/Punktvorschau anzeigen;
  - Importziel und nur fehlende/unklare Angaben **nach** der Analyse bestätigen;
  - erst danach endgültig importieren.
- Für echte Strecken-/Vermessungsreferenzformate darf die UI nach der Analyse `GLOBAL_REFERENCE` als Vorschlag vorbelegen; der Benutzer kann vor dem Import bewusst auf Firma/Baustelle umstellen.
- Die grafische Importvorschau muss read-only sein und aus denselben normalisierten Parserdaten stammen, die später auch der dauerhaften Referenzübersicht/Android dienen.
- Phase A jetzt: Admin-Dateianalyse + Vorschaugrafik + Importbestätigung. Dauerhafte Gesamtübersicht folgt als nächster REF-DATA-002-Block.

### REF-DATA-001/002 – Phase-A-Sourceblock
- Freigabecommit: `0acbbe72c516c11e2a784776bad5f326d0281f15`.
- Analyse-/Preview-API: `fc4d001bcc5c681fbcdf11ae4165ff86481ccc1d`.
- Datei-zuerst-UI-Grundaufbau: `430c427d5fd4187d6702deb66f32e98850dd533c`.
- Grafik-/Bestätigungsworkflow: `7da65428c781e5e0a9e86771401b1a4a6ca97251`.
- Sourcecheck-Nachschärfung: `b7dcca937692ff96b08cd3391b5f977a7b3f61cc`.
- Neu: `server/tbserver/survey_preview.py` normalisiert Parserdaten für Lagegrafik, Punktdarstellung und KM-/Stationsbereiche.
- Neue ADMIN-Endpoints:
  - `POST /api/v1/admin/survey-import/analyze` = Voranalyse/staging ohne DB-Import;
  - `POST /api/v1/admin/survey-import/analysis/{analysis_id}/import` = bestätigter Import mit Entscheidungen je Datei/ZIP-Eintrag.
- Analyse-Sessions liegen nur temporär unter Server-Storage und werden nach 6 h verworfen; erfolgreicher Import entfernt die Session sofort.
- ZIP wird pro enthaltenem Original analysiert; Importentscheidung/Metadaten sind pro erkanntem ZIP-Eintrag getrennt.
- UI-Vorschau: native SVG-Lageansicht, Zoom/Pan, Klickdetails, TRA-Polylinie, KF/KOR/CSV-Punkte und KM-/Stationsbereichsbalken.
- Default nach Analyse ist `GLOBAL_REFERENCE`; bewusstes Umschalten auf Firma/Baustelle bleibt möglich.
- Keine DB-/Compose-/Android-Änderung.
- GitHub Actions auf Code-HEAD: Server Runs `36576031521` und `36576022515`, Android Run `36576031503`; `runner_name=""`, `steps=[]` → **kein Testnachweis**.
- Nächster Schritt: 4 Runtime-Dateien auf Synology übernehmen, API neu starten und echte TRA/KF-Testanalyse vor Import durchführen.

### REF-DATA-002 – Servergrafik Phase A verworfen, Android-Fachlogik ist Referenz
Status: **RUNTIME-FEHLER BESTÄTIGT / KORREKTUR FREIGEGEBEN**
- Benutzer-Runtime-Screenshot zeigt: aktuelle Servergrafik ist fachlich/unübersichtlich und wird **nicht** als Zielbild akzeptiert.
- Vergleich mit dem bestätigten Android-Bestand zeigt den richtigen Aufbau:
  - Achsen/Gleise als dünne Linien statt gefüllter Flächen;
  - fachliche Labels wie `Gl 21`, `Gl 713`, Strecke/Richtung, WVB;
  - Fest-/Vermessungspunkte separat und klein;
  - Raster + Zoom/Pan;
  - Anzeigeauswahl zum Ein-/Ausblenden von Achsen und Punktarten;
  - ZIP-Vorschau fasst zuerst logisch zusammen: gefundene Strecken, Streckenachsen/Richtungen, Dateizahlen und Bahnhofsobjekte;
  - Auswahl/Zuordnung geschieht fachlich nach Gleis/WVB/Objekt, nicht als unstrukturierte Dateifarben-Legende.
- Konkreter Darstellungsfehler im Server: gemeinsame CSS-Farbklasse setzt bei SVG-Polylinien `fill`, dadurch entstehen große farbige Dreiecke/Flächen zwischen Linienpunkten. Linien müssen immer `fill:none` behalten.
- Hochgeladener Pasing-Testbestand bestätigt die Android-Zusammenfassung:
  - 41 Dateien gesamt;
  - TRA 21;
  - GRA 17;
  - rohe KF 1 + KF.TXT 1;
  - PDF 1;
  - Strecken 5524, 5528, 5529;
  - 10 Bahnhofsgleise: 12, 21, 23, 25, 26, 27, 713, 714, 715, 725;
  - 3 Weichenverbindungen: WVB 10-11, 14-22, 19-20.
- SERVER-Korrekturziel: Android-Semantik im Admin nachbilden, ohne Android-Code zu ändern oder zwei unterschiedliche Fachmodelle zu erfinden.

### REF-DATA-002 – Korrekturblock nach Pasing-Runtime-Test
Status: **SOURCE-KORREKTUR FERTIG / SYNLOGY-RETEST OFFEN**
- Fehlerfeststellung vor Code in Projektsteuerung gesichert, Commit `0f042b9682582f2ec702518a53ce4981118c45df`.
- Android-Branch `feature/tb-android-2.0` als Referenz geprüft:
  - `TrassenGrafikView.kt` zeichnet Achsen als STROKE, Raster, KM-Statusfarben, Labels und Punktgruppen;
  - `ImportExportFragment.kt` gruppiert ZIP fachlich nach Strecken, Gleisen, WVB und Anzeigeauswahl.
- Hochgeladene `Pasing (1).zip` direkt geprüft: Struktur/Anzahlen entsprechen den Android-Screenshots.
- Backend-Semantik ergänzt, Commit `364f412cb728c784e0b063b4c8c9466514325363`.
- Adminvisualisierung/-Gruppierung korrigiert, Commit `2c36c106008ce2f04625c994707628b6af683a8e`.
- Richtungsachsen werden vor Bahnhofsgleislogik klassifiziert, Commit `a51cd9a2f6053365dd43f5ba7369404f72380b1a`.
- Browser-JavaScript aus dem finalen Template in V8 kompiliert: **PASS**.
- Marker geprüft: alter Vorab-Scope entfernt; Datei-zuerst, ZIP-Summary, Anzeigeauswahl, Raster, `fill:none!important` und Semantik vorhanden.
- Nächster Schritt: `admin_survey_import.py` + `v1_admin_survey_import.html` auf Synology ersetzen; vorhandene `survey_preview.py` bleibt aus Phase A; API neu starten; Pasing-ZIP erneut nur analysieren.

### REF-DATA-002 – Großansicht + Punktdatenlabels
Status: **NEUER RUNTIME-BEFUND GESICHERT / SERVER-KORREKTUR FREIGEGEBEN**
- Benutzer bestätigt den korrigierten Linienaufbau grundsätzlich, meldet aber zwei Restpunkte:
  1. grafische Übersicht braucht eine echte **Großansicht**;
  2. die gelben Fest-/Vermessungspunkte zeigen im Server nicht die fachlichen Daten/Labels wie Android.
- Android-Referenz geprüft: `TrassenGrafikView.zeichneFestpunkte()` blendet Punktlabels zoomabhängig ein und ergänzt bei höherem Zoom `· km x,xxx`; Labelanzahl wird je Zoom begrenzt, damit die Grafik lesbar bleibt.
- SERVER-Ziel:
  - Button `Großansicht` für die Grafik;
  - Punktlabels erst ab sinnvoller Zoomstufe, mit Begrenzung/Entzerrung wie Android;
  - sichtbarer Punkttext mindestens Punktbezeichnung/-klasse und bei höherem Zoom KM;
  - Klickdetail zusätzlich Strecke, Gleis, Höhe, Punktart/ID und Quelldatei soweit Parserdaten vorhanden;
  - Achsenlabels verkleinern/entzerren, damit sie sich nicht massiv überlagern.
- Originaldaten/Importlogik unverändert; nur Previewdaten + Admin-Rendering korrigieren.

### REF-DATA-002 – Großansicht/Punktlabel Source-Fix
Status: **SOURCE FERTIG / SYNLOGY-RETEST OFFEN**
- Anforderung vor Code gesichert: Commit `8ba3960706386ae15f4e1852527b0c4f75a4fc30`.
- Preview-Metadaten erweitert: Commit `54de23bc7ed84c06b9340c0a762e5ae94818d0d0`.
- Großansicht + zoomabhängige Achsen-/Punktlabels + Punktdetails: Commit `00d56f54c84e459a512e426f2c3eda88081fffd6`.
- Sourcecheck erweitert: Commit `1363c2850a64cb54848cb57d4a0dfa4826686166`.
- Finales Browser-JavaScript via V8 kompiliert: **PASS**.
- GitHub Action `36586687636`: `runner_name=""`, `steps=[]` → **kein Testnachweis**.
- Für Synology-Retest müssen nur `survey_preview.py` und `v1_admin_survey_import.html` ersetzt werden; `admin_survey_import.py` aus vorigem Fix bleibt gültig.
### REF-DATA-002 – Zoommodell muss Android entsprechen
Status: **SOURCE-KORREKTUR FERTIG / SYNLOGY-RETEST OFFEN**
- Benutzer-Runtime-Test zeigt: Großansicht ist vorhanden, aber tiefes Hineinzoomen ist noch unbrauchbar.
- Fehlerbild:
  - Punktkreise wachsen beim SVG-Zoom mit und werden zu groß;
  - Punkt-/Achsenbeschriftungen verdecken Geometrie;
  - Benutzer kann nicht bis auf einzelne Linien/Details hineinzoomen wie in Android;
  - rechte `KM / Station`-Spalte ist in diesem Grafikmodus nicht hilfreich und soll die eigentliche Grafik nicht einengen.
- Verbindliches Ziel:
  - Weltgeometrie (TRA/Gleislinien) skaliert mit dem Zoom;
  - UI-Symbole/Punktmarker bleiben bildschirmbezogen klein bzw. werden relativ zur vergrößerten Geometrie kleiner;
  - Schrift bleibt bildschirmbezogen lesbar und darf bei sehr tiefem Zoom zusätzlich reduziert/ausgedünnt werden;
  - Labels werden nur im sichtbaren Viewport und kollisionsarm gezeichnet;
  - tiefes Zoomen bis auf einzelne Linien/Anschlussdetails muss möglich sein;
  - Punktdetail primär per Klick/Tap, nicht durch dauerhafte Textwolke;
  - Android-`TrassenGrafikView` ist Referenz für dieses Zoom-/LOD-Verhalten.
- Vor weiterer Änderung zuerst Zoom-/LOD-Konzept sauber festlegen und Benutzer erklären; kein weiterer Code auf Verdacht.
### REF-DATA-002 – KM/Station nur bei belastbarem Bereich anzeigen
Status: **RUNTIME-BEFUND GESICHERT / KONZEPT PRÄZISIERT**
- Neuer Screenshot mit Strecke 5300 zeigt: der rechte KM-/Stationsbereich ist **grundsätzlich fachlich sinnvoll**, wenn ein echter Bereich erkannt wurde.
- Beispiel bestätigt:
  - `Str 5300-2 · Quelle R` besitzt einen plausiblen Bereich `54+354.296 – 60+855.530`;
  - `Str 5300-0 · Quelle S` zeigt dagegen `0+000.000 – 0+000.000` und liefert damit keinen sinnvollen Informationswert.
- Korrektur zur vorherigen Annahme: KM/Station nicht pauschal entfernen.
- Ziel:
  - nur Achsen/Objekte mit belastbarem KM-/Stationsbereich als Balken darstellen;
  - `0+000 – 0+000`, fehlende oder offensichtlich nicht belastbare Bereiche nicht als normal gültigen Balken zeigen;
  - stattdessen bei Bedarf `kein KM-Bereich erkannt`/`KM fehlt` kennzeichnen;
  - reale Bereiche aus GRA/KM-Quelle bevorzugen;
  - Station nur als separaten technischen Ersatz anzeigen, wenn kein echter KM-Bezug vorhanden ist;
  - in Großansicht darf dieser Bereich einklappbar sein, damit die Grafik maximalen Platz bekommt.
- Diese Präzisierung betrifft nur Darstellung/Qualitätsstatus, nicht die Originaldaten.

### REF-DATA-002 – Tiefzoom/LOD Source-Fix
- Code-Commit `69096bd7e615c3fd049b50d0a290e5941400e3da`.
- `v1_admin_survey_import.html`: sichtbare Punktmarker werden bei jedem ViewBox-Zoom auf Screen-Space-Größe zurückgerechnet.
- Separates unsichtbares Klickziel bleibt ausreichend groß, damit Punktdetails trotz kleiner Marker anklickbar bleiben.
- Zoomgrenze von `view.w >= 80` auf `view.w >= 33.333` erweitert → ungefähr 30x Tiefzoom analog zur Android-Referenz.
- LOD angepasst: bei sehr tiefem Zoom werden sichtbare Punkt-/Achsentexte kleiner und die maximale Labelanzahl reduziert.
- KM/Station-Renderer validiert Bereiche und bevorzugt GRA für echten KM-Bezug; Nullspannen erscheinen nicht mehr als normale Balken.
- Finales Browser-JavaScript über V8 kompiliert: **PASS**.
- Sourcecheck ergänzt.
- GitHub Actions `36590313120`, `36590306663`, `36590313016`: jeweils `runner_name=""`, `steps=[]` → kein Testnachweis.
- Synology-Retest benötigt nur das Admin-Template; keine Migration und kein Recreate.
### REF-DATA-002 – Tiefzoom weiterhin unbrauchbar / Punktinteraktion präzisiert
Status: **RUNTIME-FEHLER BESTÄTIGT / VOR NÄCHSTEM CODE GESICHERT**
- Benutzer-Retest nach Commit `69096bd7...` zeigt: Tiefzoom ist weiterhin nicht brauchbar.
- Sichtbarer Fehler:
  - Achs-/Punkttexte werden im tiefen Zoom weiterhin viel zu dominant und verdecken die Geometrie;
  - gelbe Vermessungs-/Festpunktmarker werden im tiefen Zoom zu klein bzw. praktisch unsichtbar;
  - einzelne Gleislinien können dadurch nicht sauber kontrolliert werden.
- Android-Referenz erneut geprüft:
  - Punktmarker bleiben bildschirmbezogen sichtbar (ca. 3.5–6 dp je Zoomstufe);
  - ein Tap sucht Punkte in einer bildschirmbezogenen Trefferzone (24 dp);
  - bei einem Treffer wird Punktinfo angefordert; bei mehreren Treffern kann eine Mehrfachauswahl angezeigt werden;
  - Labels werden zoomabhängig begrenzt, Punktdetail ist aber nicht von permanent sichtbarer Beschriftung abhängig.
- Verbindliches SERVER-Ziel für Desktop/Admin:
  - Punktmarker bleiben im Screen-Space deutlich sichtbar; sie dürfen bei Tiefzoom nicht verschwinden;
  - permanente Textlabels im Tiefzoom stark reduzieren: Geometrie zuerst, nur wenige lokale Labels;
  - Hover über einen Punkt: Punkt hervorheben + kleines Kurz-Tooltip (Bezeichnung, optional KM);
  - Klick auf einen Punkt: Auswahl fixieren und rechts strukturierte Punktdetails zeigen;
  - Punktdetail soweit vorhanden: Bezeichnung/ID, Punktart, Strecke, Gleis, KM, Höhe, X/Y, Koordinatensystem, Quelldatei;
  - liegen mehrere Punkte in der Trefferzone, Auswahl der Treffer statt willkürlich nur einen zu öffnen;
  - sichtbarer Marker und Klick-/Hover-Trefferzone bleiben getrennt.
- Nächster Codeblock soll diesen Punktinteraktions-/LOD-Ansatz umsetzen; kein weiterer Versuch mit schrumpfenden Markern auf Verdacht.
### REF-DATA-002 – Hover/Klick-Punktinteraktion freigegeben
Status: **SOURCE-UMSETZUNG FERTIG / SYNLOGY-RETEST OFFEN**
- Benutzer bestätigt den beschriebenen nächsten Umbau mit `okay`.
- Umsetzung jetzt verbindlich:
  - Geometrie bleibt im SVG/Weltkoordinatenraum;
  - sichtbare Vermessungspunkte und Texte werden als echte Screen-Space-Overlayelemente gerendert, damit sie beim Tiefzoom weder explodieren noch verschwinden;
  - Hover: Punkt hervorheben + kompaktes Tooltip;
  - Klick: Punkt fixieren und strukturierte Detaildaten im Feld `Auswahl` anzeigen;
  - mehrere Punkte innerhalb der Trefferzone: Trefferliste statt zufälliger Einzelwahl;
  - permanente Punkt-/Achsenlabels werden bei tiefem Zoom stark ausgedünnt;
  - Grafik bleibt bis auf einzelne Linien hineinzoombar.
- Keine Android-Datei ändern; Android bleibt fachliche Referenz.

### REF-DATA-002 – Screen-Space-Overlay Source-Fix
- Freigabe vor Code gesichert: `287093c1ab3f41c9f4d05d370d88133b215d2034`.
- Code-Fix: `c217e4ea26ac93e6b7106ac31b92671a6083fa14`.
- `v1_admin_survey_import.html` trennt nun:
  - SVG = Weltgeometrie/Raster/TRA-Linien;
  - HTML-Overlay = Vermessungspunktmarker, Hovertooltip, Achsen-/Punktlabels, Auswahl.
- Punktmarker sind echte CSS-Pixel-Elemente und können dadurch beim ViewBox-Tiefzoom nicht mehr riesig werden oder verschwinden.
- Hover: Kurzinfo aus Punktlabel/ID + KM.
- Klick: strukturierte Detailkarte mit ID, Art, Strecke, Gleis, KM, Höhe, X/Y, System, Quelle.
- Mehrfachtreffer: Auswahl bis zu 12 nahe Punkten innerhalb 24 px.
- Klick nahe eines Punkts hat Vorrang vor Achsenklick; Drag wird von Klick unterschieden.
- Overlay-Rendering wird per `requestAnimationFrame` gedrosselt und per `ResizeObserver` bei Großansicht/Resize neu berechnet.
- Finales Browser-JavaScript über V8 kompiliert: **PASS**.
- GitHub Actions `36595173582`, `36595165137`, `36595173431`: `runner_name=""`, `steps=[]` → **kein Testnachweis**.
### REF-DATA-002 – GVP muss freie Strecken/Gleise grün kalibrieren
Status: **SOURCE-UMSETZUNG FERTIG / SYNLOGY-RETEST OFFEN**
- Benutzer meldet: Bahnhofsgleise/Strecken mit Vermarkung/GVP sollen wie in Android grün werden; bei freien Strecken klappt das im Server-Preview noch nicht.
- Android-Referenz `TrassenRepository.graMitAchspunktenVerknuepfen()` + `kmMitGvpFestpunktenKalibrieren()` geprüft:
  - GVP/Festpunkte sind eine eigenständige automatische KM-Quelle; fehlende GRA darf GVP-Kalibrierung nicht verhindern;
  - berücksichtigt werden Achstyp `GLEIS` und `STRECKE`;
  - GVP müssen einen KM-Wert und eine passende Strecke besitzen;
  - mindestens 4 GVP derselben Strecke und mindestens 4 Achssamplepunkte;
  - nächster Achspunkt je GVP max. 120 m entfernt;
  - robuste Offset-Kalibrierung: Median `km - station`, nur Offsets innerhalb ±80 m vom Median, mindestens 4 robuste Treffer;
  - bei Erfolg: alle Samples der Achse erhalten `kmMeter = station + offset`, `kmQuelle = GVP`;
  - `TrassenKmStatusLogik` bewertet diese automatische, plausible KM-Kalibrierung als GRÜN.
- Ursache im aktuellen Server-Preview: Grün wird bislang im Wesentlichen über `TRA + passende GRA` bestimmt; GVP-Kalibrierung der freien Strecke wird noch nicht fachlich nach Android nachgebildet.
- Wichtig: nicht `irgendein GVP vorhanden = grün`; grün erst bei belastbarer achsbezogener GVP-Kalibrierung. Sonst ROT/ggf. GRAU mit Grund.
- Benutzer verlangt vor Implementierung zunächst Vorschläge für die Serverübernahme dieser Android-Logik und zusätzlichen Tiefzoom.
- Benutzerauftrag `baue` bestätigt die Umsetzung der vorgeschlagenen Android-nahen GVP-Kalibrierung + erweiterten Tiefzoom.
- Umsetzung soll die Android-Kriterien nachbilden: passende Strecke, >=4 GVP mit KM, >=4 Achssamples, max. 120 m Achsabstand, robuster Medianoffset mit ±80 m Ausreißerfilter.
- Grün darf nur bei erfolgreicher GRA- oder GVP-Kalibrierung gesetzt werden; Diagnosegrund sichtbar machen.
- Zoomgrenze über ca. 30x hinaus erweitern; Screen-Space-Marker/Labels beibehalten.

### REF-DATA-002 – GVP-Kalibrierung Source-Fix
- Freigabecommit: `b5b26d1e82e3a014b32246bbbfc5e636c27e4709`.
- Hauptfix: `340906324ac85321c80d681c4bd03b1fd967720a`.
- Android-nahe Unterscheidung explizite KM-GRA vs. normale GRA: `0292ca9f3179eeb386e3d2b2403edc34687cb46a`.
- Sourcecheck erweitert: `13b2a0be86161489b9b8d0ead4aad46a52ff8473`.
- Preview berechnet pro sichtbarer TRA-Achse einen KM-Status aus GRA oder GVP/Festpunkten.
- GVP-Diagnose erscheint beim Achsenklick: GVP mit KM, Treffer ≤120 m, robuste Treffer, Quelle und Grund.
- Aus GVP kalibrierte Achsen liefern auch rechts einen belastbaren KM-Bereich statt nur technische Station.
- Zoomminimum `view.w=6.25` entspricht ca. 160x und damit dem Android-MaxScale.
- Punktinfo zeigt Android-konform `KM aus Datei: x,xxx` und zusätzlich Bahnformat `x+xxx.xxx`.
- Preview-Dubletten zwischen roher KF und KF.TXT werden über Punkt-ID + cm-Koordinate konservativ unterdrückt.
- Browser-JavaScript nach finalem Source-Fix: **PASS**.
- GitHub Actions auf Code-HEAD ohne Runner/Steps → kein Testnachweis.
- Synology-Retest: nur Admin-Template aktualisieren, API neu starten.
### REF-DATA-002 – Restpunkte Achseninteraktion / Tiefzoom / KOR-Warnungen
Status: **NEUER RUNTIME-BEFUND GESICHERT / ERST ERKLÄREN, NOCH KEIN CODE**
- Benutzer-Retest nach GVP-Kalibrierung zeigt drei neue/präzisierte Punkte:
  1. Tiefzoom reicht trotz ca. 160x noch nicht für gewünschte Detailkontrolle; weitere Zoomreserve gewünscht.
  2. Achsen sollen wie Vermessungspunkte interaktiv werden: Hover zeigt Kurzinfo, Klick zeigt strukturierte Achsendetails.
  3. KOR-Datei `2026031_Gessertshausen_5302_Koordinaten.kor` erzeugt Warnungen `Zeile N: zu wenig Spalten`; Ursache vor Parseränderung klären.
- Aktueller Server-KOR-Parser erwartet je Datenzeile mindestens 4 whitespace-getrennte Felder: `Punktname X Y Höhe`, optional 5. Feld Code.
- Jede nichtleere/nicht kommentierte Zeile mit weniger als 4 Tokens wird derzeit als `zu wenig Spalten` gewarnt, auch wenn die Datei Header-, Abschnitts-, Leer-/Steuer- oder Metazeilen enthält.
- Parseränderung erst nach Prüfung des tatsächlichen KOR-Zeilenformats; keine Warnungen auf Verdacht unterdrücken.
- Achsen-Hover/Klick soll dieselbe Auswahlstruktur nutzen wie Punkte und zusätzlich KM-Status/-Quelle, GVP-Diagnose und Quelldatei zeigen.
