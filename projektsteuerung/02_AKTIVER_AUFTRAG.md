# SERVER-GESAMTPRÜFUNG 2026-10-01

Status: **PRÜFUNG LÄUFT / KEINE CODEÄNDERUNG BEAUFTRAGT**

Benutzerauftrag: „wie ist der aktuelle Stand, wenn du das dann weißt prüfe mal alles vom Server“.
- Ausgangsstand SERVER: `0c6b381619cf149b5f6d0cb0d427f25a768e0c25`, Draft-PR #6.
- ANDROID: `423f37892915cb3308a4b1ea9e4d196faa872326`, offener PR #11 (kein Draft).
- Umfang: Projektstatus gegen Live-Code abgleichen; Servermodule, API-/Android-Verträge, Migrationen, Auth/Berechtigungen, Sync/BTB/Dateien, Survey-Import, Backup/Restore, Deployment und bestehende Checks prüfen.
- Lokale/source-basierte Nachweise getrennt von dokumentierten Synology-Praxisbestätigungen und noch offenen Runtime-/Geräte-E2E berichten.
- Keine fachlichen Codeänderungen, produktiven Mutationen, Testdaten-Resets oder Deployments aus diesem Prüfauftrag ableiten.
- Gefundene Probleme mit konkretem Beleg dokumentieren; Abschlussstatus/Checkpoint sichern.

---

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
Status: **SOURCE-UMSETZUNG FERTIG / SYNLOGY-RETEST OFFEN**
- Benutzer-Retest nach GVP-Kalibrierung zeigt drei neue/präzisierte Punkte:
  1. Tiefzoom reicht trotz ca. 160x noch nicht für gewünschte Detailkontrolle; weitere Zoomreserve gewünscht.
  2. Achsen sollen wie Vermessungspunkte interaktiv werden: Hover zeigt Kurzinfo, Klick zeigt strukturierte Achsendetails.
  3. KOR-Datei `2026031_Gessertshausen_5302_Koordinaten.kor` erzeugt Warnungen `Zeile N: zu wenig Spalten`; Ursache vor Parseränderung klären.
- Aktueller Server-KOR-Parser erwartet je Datenzeile mindestens 4 whitespace-getrennte Felder: `Punktname X Y Höhe`, optional 5. Feld Code.
- Jede nichtleere/nicht kommentierte Zeile mit weniger als 4 Tokens wird derzeit als `zu wenig Spalten` gewarnt, auch wenn die Datei Header-, Abschnitts-, Leer-/Steuer- oder Metazeilen enthält.
- Parseränderung erst nach Prüfung des tatsächlichen KOR-Zeilenformats; keine Warnungen auf Verdacht unterdrücken.
- Achsen-Hover/Klick soll dieselbe Auswahlstruktur nutzen wie Punkte und zusätzlich KM-Status/-Quelle, GVP-Diagnose und Quelldatei zeigen.
- Benutzerauftrag `bauen` bestätigt:
  - weiteren Tiefzoom über den bisherigen ca. 160x-Bereich hinaus;
  - breite unsichtbare Achsen-Trefferzone mit Hover-Kurzinfo und Klick-Detailkarte;
  - KOR-Parser nur nach tatsächlicher Formatprüfung anpassen; gültige Punkte dürfen nicht verworfen und reine Steuer-/Headerzeilen sollen nicht als Fehler gemeldet werden.
- Vor Code zuerst nach einer verfügbaren Original-`2026031_Gessertshausen_5302_Koordinaten.kor` suchen und deren beanstandete Zeilen prüfen.

### REF-DATA-002 – Achsen-/KOR Source-Fix
- Freigabecommit: `7bc47955ff0ceee4a1146da93834e11533ff517f`.
- Codecommit: `6299fca8232ce59dd65873ff733ad988b5db54da`.
- Admin-Grafik:
  - sichtbare Achslinie `pointer-events:none`;
  - separate transparente `axis-hit`-Polyline mit 16 px Trefferbreite;
  - Hover = Hervorhebung + Kurztooltip;
  - Klick = strukturierte Achsendetails inklusive GVP/GRA-KM-Diagnose;
  - Tiefzoomlimit `view.w=0.5` → theoretisch ca. 2000x.
- KOR-Parser:
  - Parser-Version 3;
  - bekannte Header/Metadaten/Trennzeilen werden ignoriert;
  - echte numerisch wirkende unvollständige Zeilen bleiben Warnungen;
  - Warnung zeigt Zeilenausschnitt, damit unbekannte KOR-Varianten reproduzierbar nachgerüstet werden können.
- Rohdateisuche in Conversation/Library fand keinen Original-Gessertshausen-KOR; nur Vermessungs-Prüfbericht und andere Referenzdateien. Daher keine spekulative Sonderbehandlung der beanstandeten Zeilennummern.
- Browser-JavaScript final via V8 kompiliert: **PASS**.
- Sourcecheck parst jetzt zusätzlich `survey_parser.py` per AST, sobald er tatsächlich auf einem Runner ausgeführt wird.
- GitHub Actions `36604836141`, `36604829585`, `36604836135`: `runner_name=""`, `steps=[]` → **kein Testnachweis**.
- Synology-Retest benötigt Admin-Template + `survey_parser.py`; API neu starten.

### REF-DATA-002 – Achsen/KOR/Tiefzoom abgenommen
Status: **BESTÄTIGT**
- Benutzer bestätigt den Synology-Runtime-Stand mit `okay,passt`.
- Bestätigt ist der zuletzt gebaute Teilblock aus Commit `6299fca8232ce59dd65873ff733ad988b5db54da`:
  - Achsen-Hover/Klick;
  - 16-px-Achsen-Trefferzone;
  - stark erweiterter Tiefzoom;
  - KOR-Parser v3 mit Metadaten-/Headerfilter und diagnostischen Restwarnungen.
- Keine weitere Änderung an diesem Teilblock ohne neuen Grund.

### REF-DATA-003 – Trassendatenübersicht + serverseitiger Datenkatalog
Status: **NEUE ANFORDERUNG / KONZEPTVORSCHLÄGE GEWÜNSCHT / NOCH KEIN CODE**
Benutzeranforderung 29.09.2026:
1. Neben `Trassierungsdaten` soll eine eigene Admin-Navigation `Trassendatenübersicht` entstehen.
   - Dort zentrale grafische Übersicht über alle auf dem Server vorhandenen Vermessungs-/Trassierungsbestände, z. B. Pasing, Gessertshausen, freie Strecken.
   - Gesamtansicht soll nach Strecke, Bahnhof/Betriebsstelle und Datenstatus filterbar werden.
2. Serverseitige Ablage/Indexierung muss so strukturiert sein, dass Android bei Eingabe/Suche einer Betriebsstelle oder Streckennummer erkennen kann:
   - Daten vorhanden / teilweise vorhanden / keine Daten;
   - welche Strecken/Bahnhöfe/Gleise/Achsen vorhanden sind;
   - welche Datenarten vorliegen (TRA, GRA/KM, GVP/KF, KOR usw.);
   - welcher Qualitäts-/KM-Status gilt.
3. Nutzer-Upload soll fachlich in denselben Serverkatalog einfließen, aber nicht unkontrolliert den globalen Referenzbestand verändern.
   - Vorschläge für Rollen/Freigabe, Duplikate, Projekt-/Firmenbestand vs. globaler Referenzbestand und Übernahmeprozess gewünscht.
4. Vor Umsetzung zuerst vorhandenes Server-Schema/API und Android-Verbrauchsweg prüfen und einen belastbaren Zielvorschlag vorlegen.

#### REF-DATA-003 – Zoomverbund, wachsende Abdeckung und manuelle GPS-Ergänzungen
Ergänzung Benutzeranforderung 29.09.2026:
- Die grafische `Trassendatenübersicht` soll mehrere Maßstabsebenen unterstützen:
  - im Detail einzelne Achsen/Gleise/Punkte;
  - beim Herauszoomen zusammenhängende Streckenabschnitte;
  - weiter außen erkennbare Betriebsstellen/Bahnhofsbereiche wie z. B. Pasing.
- Die Übersicht benötigt eine Suche mindestens nach **Streckennummer** und **Bahnhof/Betriebsstelle**.
- Ein Bahnhof/Betriebsstellenbereich ist kein statischer einmaliger Import. Wenn später zusätzliche passende Gleise/Achsen/Abschnitte hinzukommen, soll der fachliche Bereich automatisch mitwachsen.
- Streckenabdeckung wird abschnittsweise zusammengesetzt. Beispiel: vorhandener Bestand Strecke 1234 km 3,000–5,000 plus neuer Bestand km 6,000–9,000 wird unter derselben Strecke als gemeinsame, ggf. noch lückenhafte Abdeckung angezeigt; Originalquellen bleiben getrennt nachvollziehbar.
- Manuell auf dem Handy erzeugte Geometrie darf fehlende Originaldaten ergänzen. Beispiel Pasing: Gleis 28 fehlt in den Originaldaten; ein Nutzer erfasst Start/Verlauf/Ende per GPS. Diese Geometrie wird als **manuelle/abgeleitete Ergänzung** hochgeladen und steht ohne Admin-Freigabeschlange als gemeinsamer Katalogbeitrag für andere Nutzer zur Verfügung.
- Manuelle GPS-Ergänzungen überschreiben **niemals** TRA/GRA/KF/KOR-Originaldateien. Quelle, Ersteller, Version, Qualitätsstatus und Herkunft müssen getrennt nachvollziehbar bleiben.
- Wenn später neue Originaldaten für einen bislang manuell ergänzten Bereich eintreffen, werden beide Quellen zunächst getrennt gehalten und vergleichbar gemacht; keine automatische destruktive Ersetzung.
- Ziel ist ein fachlicher Verbund: Strecke ↔ Abschnitt/KM-Bereich ↔ Betriebsstelle ↔ Gleis/Achse. Neue passende Daten erweitern diesen Verbund automatisch, ohne Dateinamen zur Identität zu machen.

#### REF-DATA-003 – Automatischer Versionsverbund statt Admin-Freigabe
Entscheidung Benutzer 29.09.2026:
- **Keine Admin-Freigabe** für normale Katalog-/Geometrieergänzungen. Nutzer dürfen den gemeinsamen Fachbestand direkt ergänzen und korrigieren; Änderungen bleiben versioniert/auditiert.
- Bei neu hochgeladenen Vermessungs-/Absteckungsdaten prüft der Server automatisch, ob sie fachlich zu einem vorhandenen Bereich gehören.
- Hauptkriterien für automatische Zuordnung/Erweiterung:
  - gleiche/kompatible Strecke und Betriebsstelle, soweit vorhanden;
  - räumliche Übereinstimmung bzw. Anschluss über X/Y-Geometrie mit definierter Toleranz;
  - gleiche Gleis-/Achsenidentität, wenn diese belastbar aus den Daten hervorgeht;
  - KM-/Stationsüberdeckung bzw. anschließender KM-Bereich;
  - Aktualität/Version der Quelle.
- Sind neue Originaldaten räumlich plausibel und aktueller, erweitern/aktualisieren sie den fachlichen Verbund achsenweise. Es wird **nicht** der gesamte bisherige Bahnhof/Datensatz blind ersetzt.
- Beispiel Pasing: existiert ein manuell per GPS ergänztes `Gl 28` und ein späterer aktueller Originalbestand enthält dieses Gleis weiterhin nicht, bleibt `Gl 28` erhalten. Andere vorhandene Gleise/Achsen dürfen durch passendere/neue Originaldaten erweitert oder aktualisiert werden.
- Neue Originale ersetzen manuelle Ergänzungen nur dort, wo eine belastbare fachliche Entsprechung derselben Achse/Geometrie festgestellt wird. Fehlt diese Entsprechung, bleibt die manuelle Achse als eigener Ursprung erhalten.
- TRA-Achsen können bei passendem X/Y-Anschluss abschnittsweise erweitert werden. Ein neuer Teil kann an eine vorhandene Achse angefügt werden, ohne den vorhandenen Teil oder andere Quellen zu löschen.
- Überlappende, aber deutlich widersprüchliche Geometrien werden nicht still überschrieben; sie bleiben als Konflikt/Alternative nachvollziehbar.
- Originaldateien bleiben unverändert. Der sichtbare Gesamtbestand ist eine **zusammengesetzte fachliche Sicht** aus versionierten Original- und manuellen Quellen.

#### REF-DATA-003 – Konflikteskalation bei unsicherem Auto-Merge
Entscheidung/Anforderung Benutzer 29.09.2026:
- Normalfall bleibt **vollautomatische** Zuordnung, Erweiterung und Versionierung ohne Admin-Freigabe.
- Eine neue TRA darf eine vorhandene Achse abschnittsweise verlängern, wenn Anschluss/Überlappung fachlich eindeutig genug ist. Ein bloß nahe liegendes Ende allein genügt nicht; zusätzlich werden Richtung/Tangente, Strecke/Betriebsstelle, Gleis-/Achsenkennung, Stations-/KM-Bezug und Überlappungsfehler berücksichtigt.
- Der Server führt Originaldateien niemals physisch zusammen und überschreibt sie nicht; er erzeugt eine zusammengesetzte fachliche Sicht aus Quellsegmenten.
- Wenn die Zuordnung/Überlappung nicht eindeutig ist oder zwei Quellen widersprüchliche Geometrie für denselben Bereich liefern, darf der Server **nicht raten**.
- Solche Fälle werden als Konflikt/Prüffall gespeichert. Bestehende funktionierende Daten bleiben aktiv; der unsichere neue Anteil wird nicht still als bevorzugte Geometrie übernommen.
- Für einen neuen Konflikt/Prüffall wird der Administrator über den bestehenden Server-Mailweg informiert. Die E-Mail enthält keine Secrets, sondern mindestens Bereich/Strecke/Betriebsstelle, Konfliktart und einen Link/Hinweis auf den Admin-Prüfbereich.
- Im Admin-Prüfbereich soll eine Entscheidung möglich sein, z. B. `neue Quelle bevorzugen`, `bestehende Quelle behalten`, `als zusätzliche Achse führen` oder `Segmente bewusst verbinden`.
- Jede Adminentscheidung wird versioniert/auditiert und darf geschützte Originaldateien nicht verändern.
- Mobile Nutzer sollen beim Upload eine verständliche Rückmeldung bekommen: automatisch übernommen, als Ergänzung hinzugefügt oder wegen unklarer Zuordnung zur Prüfung vorgemerkt.

#### REF-DATA-003 – Bestandslücken Android-Sync festgestellt
Bestandsprüfung 29.09.2026:
- Aktueller Android-`WorksiteTrackDataSync` lädt lokale `track_data` nur für `memberRole == LEAD` zum Server. Andere Rollen laden den Serverstand lediglich herunter.
- Das widerspricht der neuen Fachanforderung, dass **jeder berechtigte Nutzer** Strecken-/Bahnhofs-/Geometrieergänzungen beitragen und korrigieren darf.
- Aktuelle `TrassenManuellerAbschnittEntity`-Daten werden im kanonischen Worksite-Track-Sync nicht als eigener Datensatz übertragen.
- Damit würde z. B. ein auf einer freien Roh-TRA manuell angelegtes `Gl 28` aktuell nicht als eigener versionierter Fachbeitrag serverweit verteilt.
- SERVER darf Android-Code nicht im SERVER-Projekt ändern. Erforderliche Android-Anpassung wird im Vertrag festgehalten.
- Zusätzlich fachlich wichtig: die heutige manuelle Abschnittsfunktion erzeugt **keine vollständig freie neue TRA-Geometrie aus einer GPS-Fahrt**, sondern legt einen fachlichen Abschnitt auf einer bereits vorhandenen Roh-TRA fest. Ein zukünftiger freier GPS-Geometrie-Recorder wäre eine getrennte neue Funktion.

#### REF-DATA-003 – verbindliche Umsetzungsreihenfolge
Entscheidung Benutzer 29.09.2026:
1. **SERVER zuerst fertigstellen**: Datenmodell/Migrationen, Katalog-/Verfügbarkeits-API, Segment-/Quellenmodell, automatische Zuordnung/Merge, Konflikt-/Reviewstatus, Admin-Maileskalation sowie stabile Änderungs-/Versionssemantik.
2. **Danach Portal**: `Trassendatenübersicht` mit Suche Strecke/Bahnhof, Zoom-/LOD-Gesamtansicht, Konflikt-/Quellenanzeige und Adminentscheidung für echte Prüffälle.
3. **Danach Android**: fertigen Serververtrag konsumieren; Verfügbarkeit/Download, Upload aller Berechtigten, manuelle Abschnitte und spätere freie GPS-Geometrie anbinden.
- Während SERVER-Block keine Portal-UI und kein `app/**` nebenbei ändern.
- SERVER-APIs werden so fertiggestellt, dass Portal und Android anschließend auf denselben stabilen Vertrag aufsetzen.

#### REF-DATA-003 – Source-Review nach erstem Serverbau
Fehlerfeststellung 29.09.2026 nach GitHub-Reread von Commit `0097a24b843d8c0610a9bee8971a782e9a5f4340`:
- `admin_survey_import._install_original(...)` enthält durch die Integrationsänderung einen Python-Syntaxfehler: Pflichtparameter `user` steht hinter optionalen Default-Parametern `station_code/station_name`.
- `PATCH /api/v1/survey-catalog/manual-segments/{segment_id}` prüft im ersten Stand noch nicht, ob ein Nicht-Admin den Globalbestand bzw. nur den Bestand seiner eigenen Firma bearbeiten darf. Fremde COMPANY-Segmente müssen serverseitig verboten werden.
- Beide Punkte sind vor jeglichem `SOURCE FERTIG` zu korrigieren und durch den neuen Source-Check abzudecken.


#### REF-DATA-003 – SERVER-Quellblock umgesetzt
Status: **QUELLSTAND IMPLEMENTIERT / SYNLOGY-DEPLOYMENT + E2E OFFEN**
- Serverbasis liegt bis Code-HEAD `a1ef0d991e2ccc06578feced7e9b5a89bdf5f611` vor.
- Enthalten: Migration 017, persistenter Katalog, Auto-Merge/Reviewfälle, versionierte Nutzerbeiträge/Achskorrekturen, Search/Availability/Geometry/Changes, Reindex, Konfliktmail und KM-Qualitätsvertrag.
- Zwischenfehler Python-Signatur + COMPANY-Scope-Sperre wurden reproduzierbar gefunden und in `a37d4003...` korrigiert.
- Sourcechecks 112/113 sind vorhanden, aber wegen `steps=[]` nicht ausgeführt; kein Testnachweis.
- Aktiver nächster Block bleibt SERVER-Runtime: Synology deployen, Migration 017 anwenden, API/Worker neu starten, Reindex und echte Requests testen.
- Portal und Android erst danach.

#### REF-DATA-003 – Source-Audit Teilabdeckung + Datenschutz 29.09.2026
Fehlerfeststellung auf aktuellem SERVER-HEAD `7d1441c092becbeefb3a00bc46993bed3255b441`:
- Der Katalogvertrag ist source-seitig weit fortgeschritten (Migration 017, Suche/Verfügbarkeit/Geometrie, manuelle Beiträge, Konflikte, Mailjob, versionierte Achskorrekturen, Check 112/113).
- **Teilabdeckungsfehler:** aktuelle Auto-Priorisierung kann ein bestehendes manuelles Segment bereits bei ca. 80 % und ein älteres Originalsegment bei ca. 90 % geometrischer Überdeckung komplett auf `SUPERSEDED` setzen. Dadurch könnte ein nicht vom neuen Bestand abgedeckter Rest verschwinden. Das widerspricht der festgelegten Regel „Teilverlängerung ergänzt/ersetzt nur den belegten Teil; fehlende Alt-/Manuell-Geometrie bleibt erhalten“.
- Korrekturziel: automatisches vollständiges Supersede nur, wenn die neue Geometrie den bestehenden Abschnitt tatsächlich vollständig bzw. mit enger geometrischer Vollabdeckung abdeckt. Sonst bleiben beide Quellsegmente aktiv und bilden gemeinsam die sichtbare Achse.
- **Datenschutzfehler:** globale Search-/Availability-/Geometry-/Changes-Antworten dürfen normalen Nutzern keine `created_by_user_id`/`created_by_company_id` bzw. fremde Bearbeiter-/Firmenidentität offenlegen. Admin darf die vollständigen Auditdaten weiterhin sehen.
- Rohdatei-Zugriffsschutz und geschützte Originale bleiben unverändert.

#### REF-DATA-003 – Upload-/Cursor-Audit 29.09.2026
Fehler-/Vollständigkeitsprüfung nach Teilabdeckungs-/Datenschutzfix:
- Handy-/Portal-Upload eines Vermessungsoriginals hängt fachlich an einer Baustelle. Wenn `route_number`, `station_code` oder `station_name` im Upload fehlen, soll der Server vorhandene Baustellenwerte als Fallback übernehmen. Der Client muss dieselben Stammdaten nicht fehleranfällig doppelt senden.
- Ein Firmen-Upload muss ein bytegleiches bereits vorhandenes **GLOBAL_REFERENCE**-Original (SHA-256 + Größe + Datenart) als vorhandenen Bestand erkennen. Private Originale anderer Firmen dürfen dabei weder sichtbar noch als Treffer offengelegt werden.
- Der neue Survey-Katalog-Change-Feed darf bei vielen für den Nutzer unsichtbaren COMPANY-Änderungen nicht stehenbleiben. Antwort benötigt einen fortschreitenden `next_after`/Scan-Cursor auch dann, wenn im Scanfenster keine sichtbaren Änderungen liegen.
- Für die Priorisierung „neuer Bestand“ braucht der Admin-Import zusätzlich ein explizites optionales `source_date`; Dateinamenerkennung bleibt Fallback.
- Diese Punkte sind SERVER-Vertrag; Portal/Android sollen später nur den stabilen Vertrag konsumieren.

#### REF-DATA-003 – Lifecycle-Kompatibilität Migration 017
Fehlerfeststellung 29.09.2026:
- Die neuen Katalog-Fremdschlüssel aus Migration 017 müssen mit dem bereits bestätigten Firmen-Lifecycle kompatibel bleiben.
- Ein zulässiger Staging/Test-Firmen-Purge darf nicht durch Katalogreferenzen auf Firmen-Originale oder COMPANY-Katalogobjekte scheitern.
- Der bestehende Produktionsschutz für Firmen mit Vermessungsoriginalen bleibt unverändert.
- Vor Sourceabschluss wird der Firmen-Purge deshalb um eine gezielte Katalogbereinigung der tatsächlich betroffenen Referenzen ergänzt; unabhängige globale Beiträge bleiben erhalten.

#### REF-DATA-003 – Check-112-Nachlauf
Fehlerfeststellung 29.09.2026 nach Lifecycle-Integration:
- Die alte Check-112-Assertion auf die _install_original-Signatur erwartet station_name direkt vor user.
- Seit dem dokumentierten source_date-Vertrag steht source_date dazwischen; die alte Assertion ist damit veraltet und würde auf einem echten Runner falsch fehlschlagen.
- Nur den Source-Check an die aktuelle verpflichtende Signatur anpassen; Fachcode unverändert lassen.

#### REF-DATA-003 – Capability-Vertrag
Abschlussprüfung 29.09.2026:
- Der neue Survey-Referenzkatalog muss im bestehenden /api/v1/capabilities-Vertrag explizit erkennbar sein.
- Separate Capability-Flags für Katalog-Lesen, versionierte Nutzerbeiträge und Survey-Katalog-Change-Feed ergänzen.
- Android soll später diese Flags prüfen statt Endpunkte auf Verdacht aufzurufen.

#### REF-DATA-003 – Geometrische Zuordnung bei unvollständiger Achskennung
Abschlussaudit 29.09.2026:
- Neue TRA-Dateien können Strecke/Betriebsstelle besitzen, aber keine belastbare Gleis-/Richtungskennung im Dateinamen.
- Solche Quellen dürfen nicht allein wegen fehlender Kennung von einem X/Y-Merge ausgeschlossen werden.
- Wenn Strecke/Betriebsstelle nicht widersprechen und die Bounding-Box räumlich eng an einer vorhandenen Achse liegt, darf der Server die echte Geometrie auf Überlappung/Endpunkt/Tangente prüfen.
- Genau eine eindeutige geometrische Achse darf automatisch verbunden werden; mehrere gleich plausible Achsen erzeugen REVIEW_REQUIRED.
- Widersprechende Strecke/Gleis/Richtung bleiben harte Ausschlussgründe.

#### REF-DATA-003 – Runtimefund fehlende Betriebsstellenzuordnung 30.09.2026
Fehlerfeststellung aus Synology-Katalogabfrage:
- Strecken/Achsen wurden erkannt (u. a. 5302, 5340, 5524, 5528, 5529), aber alle ausgegebenen Gruppen haben aktuell `station_code/station = NULL`.
- Damit wäre die geforderte Suche nach Bahnhof/Betriebsstelle für den bereits reindizierten Bestand nicht zuverlässig möglich.
- Das ist vor dem Portalblock serverseitig zu lösen; das Portal darf fehlende Betriebsstellenidentität nicht aus Dateinamen raten.
- Zuerst vorhandene Importmetadaten/Archivkontext und Bahnhof-/Betriebsstellenreferenz prüfen. Wenn daraus eine belastbare Zuordnung möglich ist, Server-Reindex/Backfill entsprechend ergänzen.
- Falls eine Zuordnung nicht eindeutig ableitbar ist, Daten weiter nach Strecke verfügbar halten und die fehlende Betriebsstellenzuordnung als fachlichen Prüf-/Ergänzungsbedarf kennzeichnen.

#### REF-DATA-003 – Ursache leere Betriebsstelle + Importregel
Benutzerklärung und Quellbestätigung 30.09.2026:
- Beim Android-Import wird fachlich angegeben, zu welchem Bahnhof/Betriebsstelle ein Datensatz gehört.
- Beim aktuellen Admin-Import existiert diese Eingabe in der HTML-Oberfläche nicht.
- Backend-`SurveyImportDecision` unterstützt `station_code`/`station_name` bereits; die Admin-HTML sammelt/sendet aktuell nur Scope, Baustelle, Strecke und Gleis/Achse.
- Daher sind die vorhandenen globalen Pasing-Achsen nach Reindex ohne Betriebsstellenzuordnung.
- Verbindliche UI-Regel:
  - Admin-Import erhält eine Betriebsstellen-/Bahnhofsauswahl auf Basis der vorhandenen Referenzsuche (RL100/Name);
  - ausgewählt werden stabil `station_code` + `station_name`, nicht nur Freitext;
  - bei Bahnhofsdaten kann ein Default für die gesamte Analyse/ZIP gesetzt und je fachlicher Gruppe überschrieben werden;
  - bei freier Strecke zwischen Betriebsstellen darf die Betriebsstelle bewusst leer bleiben;
  - keine automatische Bahnhofserfindung allein aus Dateiname oder räumlicher Nähe.
- Bereits importierte Bestände benötigen eine nachträgliche Zuordnungsmöglichkeit, ohne Originaldateien neu schreiben zu müssen.

#### REF-DATA-003 – Staging-Testbestand darf zurückgesetzt werden
Entscheidung Benutzer 30.09.2026:
- Die aktuell auf dem Staging-Server vorhandenen Trassierungs-/Vermessungsdaten sind Testbestand und dürfen für REF-DATA-003 vollständig gelöscht und anschließend aus den weiterhin vorhandenen Quelldateien neu importiert werden.
- Das gilt ausdrücklich nur für den aktuellen Staging/Testbestand, nicht als allgemeine Produktions-Löschregel.
- Reihenfolge:
  1. Admin-Import muss Bahnhof/Betriebsstelle korrekt erfassen können;
  2. bestehenden Vermessungs-Testbestand konsistent zurücksetzen;
  3. Daten neu einlesen und dabei Bahnhof/Betriebsstelle korrekt zuordnen;
  4. Reindex und Search/Availability/Geometry erneut testen.
- Löschung muss Original-Datenbankeinträge, Parserkatalog, Survey-Katalogreferenzen und gespeicherte Originaldateien konsistent behandeln; keine SQL-/Dateilöschung auf Verdacht.

#### REF-DATA-003 – Bedienvertrag Ändern/Löschen von Trassendaten
Neue Anforderung 30.09.2026:
- Im normalen Betrieb müssen einzelne Trassendaten im Portal gezielt geändert, deaktiviert oder – in zulässigen Fällen – gelöscht werden können. Ein kompletter Staging-Reset ist nur Testwerkzeug.
- Unveränderte Rohoriginale bleiben grundsätzlich unverändert und revisionssicher.
- Fachliche Korrekturen erfolgen auf Katalogebene versioniert:
  - Bahnhof/Betriebsstelle, Strecke, Gleis/Achse und Anzeigezuordnung ändern;
  - manuell erzeugte Geometrie ändern;
  - falsche/alte Segmente deaktivieren statt still überschreiben;
  - neue Originaldaten können ältere vollständig abgedeckte Bereiche automatisch superseden.
- Normales „Löschen“ bedeutet für produktiv genutzte Referenzdaten zunächst **deaktivieren/ausblenden** mit Historie und Rückholbarkeit.
- Physisches endgültiges Löschen von Original + Katalog ist ein gesonderter Admin-/Testvorgang mit Schutzprüfung; Staging-Komplettreset bleibt davon getrennt.
- Die spätere Trassendatenübersicht braucht pro Datensatz/Achse klare Aktionen: `Ändern`, `Deaktivieren/Ausblenden`, `Ersetzen/Neuimport`, sowie für zulässige Adminfälle `Endgültig löschen`.
- Nutzerbeiträge vom Handy folgen demselben versionierten Katalogprinzip; Originalquellen werden nicht überschrieben.

#### REF-DATA-003 – Runtimebefunde Strecke/Gleis/WVB/Überlagerung 30.09.2026
Neue Benutzerbeobachtungen nach sauberem Staging-Reset und Pasing-Neuimport:
- Gruppenüberschrift/Badges erkennen teils Strecke korrekt, aber das editierbare Feld `Strecke` bleibt leer. Sichtbare Beispiele:
  - `Gl 12 · Str 5529` / Badge `Strecke 5529`, Eingabefeld Strecke leer;
  - `Gl 714 · Str 5528` / Badge `Strecke 5528`, Eingabefeld Strecke leer.
- Andere Gruppen wie `Gl 21 · Str 5528` übernehmen die Strecke korrekt. Ursache daher nicht pauschal „Parser erkennt Strecke nicht“, sondern Inkonsistenz zwischen Analyse-/Gruppenerkennung und Decision-Vorbelegung.
- Android-Verhalten für Suche `Betriebsstelle MPB + Gleis 27` muss gegen aktuellen Quellstand geprüft werden. Fachziel: Bahnhof/Gleis darf nicht allein wegen fehlender/abweichender Streckennummer unsichtbar werden, sofern die Betriebsstellen-/Gleisidentität eindeutig ist.
- WVB/Weichenverbindungen werden als `WEICHENVERBINDUNG` erkannt, besitzen im gezeigten Fall aber keine Strecke und keine Gleis-/Achseingabe. Zusätzlich meldet Server für `WVB_14-22_ER0_GGP2026_02_16.TRA` einen Prüffall `NEARBY_GEOMETRY_NOT_MATCHING`.
- Zu klären: ob WVB aus Dateiname/Geometrie mit den beteiligten Weichen bzw. Anschlussachsen fachlich verknüpft werden kann und ob WVB ohne eigene Streckennummer trotzdem korrekt Bahnhof MPB zugeordnet und auffindbar bleibt.
- Android-Grafik zeigt zwei nahe/teilweise übereinanderliegende Trassen/Achsen gleichzeitig. Die Admin-Analyse muss diese Gesamtüberlagerung ebenfalls sichtbar machen, damit falsche Doppelachsen/Parallelachsen vor Import/Entscheidung erkannt werden können.
- Vor Änderungen Ursache in SERVER-Analyse/Decision-Vorbelegung und ANDROID-Such-/Darstellungslogik prüfen.

#### REF-DATA-003 – Audit-Ergebnis Strecke/Gleis/WVB/Überlagerung
Quellprüfung SERVER + ANDROID 30.09.2026:
- Android `TrassenKontextFilter` filtert `BAHNHOF_GLEIS` mit Bahnhof + Gleis; die Streckennummer ist in diesem bestehenden Kontext **keine Pflichtbedingung**.
- SERVER-Katalog verwendet `station_code + track_key` bereits als starke Achsidentität; `route_number` ist bei Bahnhofsgleisen zusätzlicher Kontext/Disambiguierung.
- Für die spätere Android-Survey-Katalogsuche ist derselbe Grundsatz verbindlich: `station_code + object/track key` zuerst; Strecke nur dann zusätzlich verlangen, wenn sonst Mehrdeutigkeit besteht.
- Sichtbarer UI-Widerspruch „Badge Strecke vorhanden, Eingabefeld leer“ ist kein Parserverlust:
  - aktuelles Template erzeugt Badge und Eingabewert aus demselben `route`;
  - Backend-`_install_original` fällt bei leerem Decision-Feld zusätzlich auf `inferred_route` aus dem Parser zurück.
  - Ursache liegt daher in nachgelagertem UI/Browserzustand und muss dort reproduziert/behoben werden.
- WEICHE/WVB:
  - Parser erkennt `WEICHE` bzw. `WEICHENVERBINDUNG`;
  - Analyse erkennt z. B. `WVB 14-22`;
  - Katalogmodell besitzt aktuell aber keinen stabilen `object_key` für Weiche/WVB. `track_key` ist dafür fachlich ungeeignet.
  - Folge: WVB derselben Betriebsstelle werden bei räumlicher Nähe als mögliche Kandidaten verglichen; unterschiedliche nahe WVB können dadurch `REVIEW_REQUIRED / NEARBY_GEOMETRY_NOT_MATCHING` auslösen.
  - Ziel: stabile Objektidentität, z. B. `object_type=WEICHENVERBINDUNG`, `object_key=WVB:14-22`; gleiche Objektidentität vergleichen, unterschiedliche WVB nicht als Konflikt behandeln.
- Überlagerte Achsen:
  - Android-`TrassenGrafikView` unterstützt Mehrfachtreffer bei überlagerten Achsen; Long-Press liefert mehrere Achsen und Dialog `Welche Roh-TRA?`.
  - Admin-SVG besitzt derzeit je Polyline einen einzelnen Hit-Layer. Bei deckungsgleichen/nahen Linien kann die oben gezeichnete Achse die andere verdecken bzw. allein den Klick erhalten.
  - Ziel Admin: Mehrfachtreffer/Overlap-Liste wie Android, plus klare visuelle Hervorhebung/Wechselmöglichkeit ohne Veränderung der bestätigten Zoom-/Hover-Grundfunktion.

#### REF-DATA-003 – Admin-Prüffall muss erklärbar und anklickbar sein
Neue Bedienanforderung 30.09.2026:
- Eine reine Warnung/E-Mail wie `NEARBY_GEOMETRY_NOT_MATCHING` reicht für den Admin nicht aus.
- Jeder offene Trassendaten-Prüffall braucht in der Admin-/Trassendatenoberfläche eine verständliche Aktion **„Problem ansehen“**.
- Beim Öffnen muss der Admin mindestens sehen:
  - betroffene neue Quelle/Datei;
  - vorhandene Vergleichsachse bzw. Vergleichsquelle;
  - Bahnhof/Betriebsstelle, Strecke, Gleis/Objekt/WVB soweit vorhanden;
  - konkreter maschinenlesbarer Konfliktgrund plus verständliche deutsche Erklärung;
  - relevante Messwerte wie nächster Abstand, Überlappung, Endpunktabstand, Richtungsabweichung;
  - beide Geometrien gemeinsam in einer Detailgrafik, unterschiedlich hervorgehoben;
  - problematische Stelle/Überlappungsbereich soweit ermittelbar.
- Von dort sollen die fachlich zulässigen Entscheidungen erreichbar sein, z. B.:
  - getrennt lassen / „sind verschiedene Achsen“;
  - derselben Achse zuordnen/verbinden, nur wenn fachlich zulässig;
  - Metadaten korrigieren (Bahnhof, Strecke, Gleis/Objekt);
  - Datensatz deaktivieren bzw. Import nicht übernehmen.
- Keine automatische Freigabe nur wegen Admin-Klick; Entscheidung muss versioniert/auditierbar gespeichert werden.
- E-Mail bleibt Benachrichtigung, verweist fachlich aber auf den konkreten Prüffall; die eigentliche Diagnose erfolgt im Portal.

#### REF-DATA-003 – Bauauftrag freigegeben 30.09.2026
Benutzerfreigabe: **BAUEN**.
Verbindlicher nächster SERVER/PORTAL-Block:
1. Strecke/Gleis-Vorbelegung stabilisieren, damit erkannte Werte sichtbar und zuverlässig im Decision-Feld stehen.
2. Weiche/WVB als echte fachliche Objektidentität ergänzen; keine Identifizierung nur über Gleisfeld oder räumliche Nähe.
3. Admin-Grafik mit Mehrfachtreffer/Overlap-Auswahl analog Android ergänzen, ohne bestätigten Tiefzoom/Hover/KOR-Grundblock zu verschlechtern.
4. Open-Conflict-Warnungen erhalten **„Problem ansehen“** mit verständlicher Diagnose, Quellen, Messwerten, gemeinsamer Geometriegrafik und versionierten Entscheidungsaktionen.
5. Danach Pasing auf Synology erneut importieren/testen.
6. Erst nach diesem Runtime-Gate die neue `Trassendatenübersicht` darauf aufbauen.
- In diesem Block kein Android-`app/**` ändern.

#### REF-DATA-003 – Source-Check-Nachlauf Parser-Version
Fehlerfeststellung 30.09.2026:
- Live-`survey_parser.py` verwendet bereits `PARSER_VERSION = "tb-survey-parser-3"`.
- `server/checks/30_verify_tb_server_1_survey_parser_source.py` erwartet noch den alten Marker `tb-survey-parser-2`.
- Ein echter Runner würde dadurch trotz aktuellem Parser falsch fehlschlagen.
- Nur den veralteten Check auf Parser v3 nachziehen; Parsercode selbst bleibt unverändert.

#### REF-DATA-003 – Buildblock Sourceabschluss 30.09.2026
Status: **SOURCE FERTIG / DEPLOYMENT OFFEN**
- Freigegebener Block Prefill + Weiche/WVB object_key + Multi-Hit + Problem-ansehen ist im SERVER/Portal-Quellstand umgesetzt.
- Migration 018 ist neu und muss vor Runtimeprüfung angewendet werden.
- Kein Android-Code geändert.
- Nächster Auftragsschritt gemäß Freigabe: Synology/Pasing-E2E; erst nach Bestätigung die eigenständige Trassendatenübersicht bauen.

#### REF-DATA-003 – Parser-Cache-Version nach object_key
Fehlerfeststellung im Deployment-Audit 30.09.2026:
- Der neue Quellstand ergänzt Parser-Payloadfelder `object_key_from_name`, `switch_a_from_name`, `switch_b_from_name`.
- `PARSER_VERSION` steht noch auf `tb-survey-parser-3`.
- Bereits mit dem alten v3-Code ausgewertete Pasing-Originale könnten dadurch als „Parserstand aktuell“ gelten, obwohl die neuen Objektfelder im gespeicherten Parse-Payload fehlen.
- Zwingende Korrektur vor Deployment: Parser-Version auf v4 erhöhen und Source-Checks entsprechend nachziehen. Dadurch kann der Server bestehende Parse-Caches eindeutig als alt erkennen/reparsen.

#### REF-DATA-003 – Reindex muss bestehende Achsen anreichern
Fehlerfeststellung im Upgrade-Audit 30.09.2026:
- `index_original_in_tx()` beendet bereits katalogisierte TRA aktuell mit `ALREADY_INDEXED`.
- Nach Parser-v4-Reparse würde dadurch ein neu erkannter `object_key` bei bereits vorhandenem Pasing-/WVB-Bestand nicht auf die bestehende Achse übernommen.
- Korrekturziel vor Deployment:
  - Reindex reparst fehlende/veraltete Parserstände;
  - bereits vorhandene Original-TRA-Segmente bleiben geometrisch unverändert;
  - deren vorhandene Achse wird **nur fehlende fachliche Identität** (insbesondere `object_key`) aus dem neu geparsten Original anreichern;
  - keine Admin-Korrektur überschreiben und keine Geometrie duplizieren.
- So funktioniert Migration 018 auch als Upgrade bestehender Bestände und nicht nur bei Neuimporten.

#### REF-DATA-003 – Deployment-Audit-Lücke geschlossen
Status: **SOURCE FERTIG**
- Parser v4 ist umgesetzt.
- Reindex reparst stale/missing Parse-Caches automatisch.
- Bereits katalogisierte Original-TRA werden nicht dupliziert; fehlender `object_key` wird fill-only auf der bestehenden Achse ergänzt.
- Admin-Korrekturen werden nicht überschrieben.
- Damit ist der zuvor dokumentierte Parser-v4-/Upgradebedarf source-seitig geschlossen.
- Noch offen: Synology-Deployment 18/18 + Pasing-Runtime-Gate.

#### REF-DATA-003 – Runtimefehler Reindex 3/40
- Synology 18/18 und Parser v4 sind bestätigt.
- Alle 40 Originale wurden im Lauf als stale erkannt und reparst.
- 18 bereits katalogisierte TRA: `ALREADY_INDEXED`; 19 Nicht-TRA/Assets: `INDEXED_ASSET`; 3 Exceptions.
- Keine Änderung auf Verdacht. Nächster Schritt: exakte Dateinamen/Original-IDs + Exceptiontypen der drei Fehler ermitteln.

#### REF-DATA-003 – Ursache Reindex failures=3 bestätigt
Runtime-Diagnose 30.09.2026:
- Alle drei Fehler sind `psycopg.errors.CheckViolation` auf `survey_catalog_history_operation_check`.
- Betroffene TRA:
  - `WVB_10-11_ER0_GGP2026_02_16.TRA`;
  - `WVB_14-22_ER0_GGP2026_02_16.TRA`;
  - `WVB_19-20_ER0_GGP2026_02_16.TRA`.
- Der neue Fill-only-Upgradepfad schreibt History-Operation `ENRICH`; die bestehende DB-Check-Constraint erlaubt diese Operation noch nicht.
- Ursache ist damit eindeutig DB-Vertrag/Migration, nicht Geometrie, Parser oder die drei WVB-Dateien.
- Fix nur an der History-Operation-Constraint; keine WVB-Geometrie oder Merge-Regel ändern.

#### REF-DATA-003 – ENRICH-Constraint Fix gebaut
- Migration 019 ist source-seitig fertig.
- Sie ändert ausschließlich die Check-Constraint der Survey-History und erlaubt zusätzlich `ENRICH`.
- Runtime noch offen: deployen/anwenden und Reindex wiederholen.

## REF-DATA-003 – Reindex nach ENRICH-Fix runtime-grün 30.09.2026
- Synology/API-Terminal: `reindex_all_originals()` erneut ausgeführt.
- Ergebnis: `original_count=40`, `parser_version=tb-survey-parser-4`, `reparsed=0`.
- Statusverteilung: `ALREADY_INDEXED=19`, `INDEXED_ASSET=19`, `ALREADY_INDEXED_ENRICHED=2`.
- `failures=0`: der zuvor reproduzierte ENRICH-Constraint-Fehler ist im laufenden DB-Pfad behoben.
- Die 40 Originale sind vollständig bilanziert; kein Reindex-Fehler mehr offen.
- Abweichung zur Voraberwartung: nur 2 statt 3 WVB wurden in diesem Lauf als `ALREADY_INDEXED_ENRICHED` gemeldet. Vor weiterer Fachfreigabe read-only feststellen, welcher der drei WVB-Fälle bereits als `ALREADY_INDEXED` läuft und welche `object_key` die drei vorhandenen Achsen tatsächlich tragen.
- Der vorliegende Screenshot zeigt nicht den Systemstatus `19/19`; Migration 019 deshalb in der Dokumentation erst nach separatem Systemstatus-/Migrationstabellen-Nachweis als 19/19 bestätigen.
- Nächster Runtime-Schritt: WVB 10-11 / 14-22 / 19-20 mit Originalname, Achse, `object_key` und Segmentzahl gegenüberstellen; danach Pasing-Prefill, Multi-Hit und „Problem ansehen“ praktisch testen.


## REF-DATA-006 – Deutschlandübersicht: erst Architektur entscheiden, dann bauen 02.10.2026
- Benutzerentscheidung: vor einem erneuten Implementierungsversuch zuerst Lösungsvorschläge/Architektur vergleichen; erst nach Auswahl wird gebaut.
- Ziel bleibt: grafische Deutschland-Gesamtansicht des vorhandenen Trassendatenbestands mit Suche nach Strecke, Bahnhof/Betriebsstelle, Gleis/Achse und W/WVB/Objekt.
- Harte Randbedingung nach Rollback: keine neue ungetestete Runtime-Abhängigkeit in den laufenden Python-Server einführen; bestehende stabile Server-Runtime bleibt geschützt.
- Zu prüfen: (A) vorhandenes PostGIS für DB_REF→WGS84 verwenden, (B) clientseitige lokale Projektion ohne Python-Abhängigkeit, (C) zunächst schematische Übersicht ohne echte Georeferenz. Empfehlung erst nach technischer Prüfung, noch keine Codeänderung.

## REF-DATA-006 – Architekturentscheidung A / PostGIS 02.10.2026
- Benutzer hat Variante **A** bestätigt.
- Deutschlandübersicht soll die bereits laufende PostGIS-Datenbank für DB_REF→WGS84 verwenden; keine neue Python-/Image-Abhängigkeit wie pyproj.
- Original- und Katalogdaten bleiben unverändert. Transformation erfolgt ausschließlich read-only für die Übersicht/API-Ausgabe.
- Umsetzung bleibt zweistufig: deutschlandweite kompakte Abdeckung/Suche zuerst; Detailgeometrie erst bei Treffer/Zoom nachladen, damit der Pfad bei wachsendem Bestand skalierbar bleibt.
- Vor Codeänderung ist ein read-only Runtime-Probe auf der Synology Pflicht: PostGIS-Version, vorhandene SRIDs 5682–5685/4326 und reale Transformation eines MGHN/MPB-Koordinatenpunkts prüfen. Erst bei plausibler Ausgabe bauen.

## REF-DATA-006 – PostGIS Runtime-Gate BESTANDEN 02.10.2026
- Variante A ist technisch freigegeben.
- Synology-Read-only-Probe bestätigt PostGIS 3.5.2; spatial_ref_sys enthält 4326 sowie DB_REF/GK-Zonen 5682, 5683, 5684 und 5685.
- Reale Transformationen sind plausibel: MGHN → ca. 10.69908 / 48.33314, MPB → ca. 11.43154 / 48.15369.
- Damit kann die Deutschlandübersicht ohne neue Python-/Image-Abhängigkeit gebaut werden. Originaldaten bleiben unverändert; ST_Transform wird nur read-only für die Anzeige verwendet.
- Umsetzung jetzt freigegeben: zweistufige Coverage-Zusammenfassung + Suche, Detailgeometrie erst bei Auswahl.

## REF-DATA-006 – Deutschlandübersicht soll ALLE vorhandenen Trassen zeigen 02.10.2026
- Neue Benutzeranforderung nach Runtime-Sichtprüfung: Die Deutschlandübersicht darf nicht nur wenige Punktmarker/Betriebsstellen zeigen. In der Gesamtansicht sollen **alle vorhandenen aktiven Achsen/Trassen** bereits als vereinfachte Linien sichtbar sein.
- Punkte/Bahnhofsmarker bleiben ergänzend; die eigentliche vorhandene Geometrie muss deutschlandweit erkennbar sein.
- Performance-Regel: Übersicht lädt pro Achse nur stark vereinfachte/decimierte Linien; vollständige Detailgeometrie weiterhin erst bei Auswahl/Zoom.
- Keine neue Python-/Image-Abhängigkeit; PostGIS-Variante A bleibt unverändert.


## REF-DATA-007 – Trassendaten-Zentrale, Kennzahlen und Android-Abgleich 02.10.2026
Status: **ANFORDERUNG GESICHERT / VORSCHLAGSPHASE / NOCH NICHT BAUEN**

Neue Benutzeranforderung:
- Trassendatenübersicht soll zur zentralen verständlichen Verwaltungsansicht für vorhandene Vermessungs-/Trassendaten werden.
- Dashboard/Übersicht soll zusätzlich fachliche Gesamtkennzahlen zeigen, insbesondere:
  - gesamte abgedeckte Streckenlänge in km;
  - davon Länge mit belastbarer KM-Angabe;
  - davon manuell am Handy ergänzte KM-Angaben getrennt sichtbar;
  - Anzahl Bahnhöfe/Betriebsstellen im vorhandenen Survey-Bestand;
  - Anzahl Gleise/Achsen im Gesamtbestand.
- Detailansichten sollen vorhandene Daten, Quelle/Originaldatei, Achse/Gleis/Weiche/WVB, KM-Qualität und Zuordnung nachvollziehbar machen sowie gezielte Korrektur/Umbenennung erlauben, ohne Originaldateien zu verändern.
- Bestehenden allgemeinen Fachkatalog (Streckendaten/Bahnhöfe) und Survey-Katalog nicht weiter als unverbundene Parallelwelten ausbauen; Verknüpfung/Verantwortung vor Implementierung festlegen.
- Katalog-Historie soll verständlich erklärt und nur dort prominent bleiben, wo sie für Nachvollziehbarkeit/Korrekturen sinnvoll ist.
- Vor Server-Umbau zwingend aktuelles Android-Verhalten prüfen: welche Strecken-/Bahnhofs-/Gleis-/Survey-/KM-Daten das Handy tatsächlich hochlädt, welche manuellen KM-Korrekturen/Anker synchronisiert werden und über welche API/Entity-Pfade.
- Keine Android-Codeänderung durch SERVER. Falls Android-Anpassung nötig wird, Bedarf zuerst in Projektsteuerung/Vertrag dokumentieren und an ANDROID geben.
- Performanceziel: Übersicht darf bei kleinem Bestand nicht lange laden; Summary/Kennzahlen und Geometrie sollen gestuft/lazy geladen werden statt vollständige Detailgeometrie beim Einstieg zu berechnen.
- Benutzer wünscht ausdrücklich: zuerst Vorschläge/Skizze und Datenfluss klären, erst danach bauen.


### REF-DATA-007 – Android-Streckendaten-Audit vor Design 02.10.2026
- Android `WorksiteTrackDataSync` synchronisiert für LEAD aktuell abgeleitete `AXIS`-Datensätze und `STRECKENPUNKTE`; Rohdateien werden ausdrücklich nicht hochgeladen.
- AXIS-Payload enthält pro Sample u. a. `station`, `km_meter`, `km_quelle`, DB_REF/GPS und Achsmetadaten.
- Die ältere manuelle 2-Punkt-Kalibrierung schreibt berechnete Sample-KM mit `kmQuelle=MANUELL_2PUNKT`; diese Werte können damit über den bestehenden AXIS-Payload zum Server gelangen.
- Die neuere lokale `TrassenManuellerAbschnittEntity` hält darüber hinaus Start/Ende, KM-Richtung, KM-Anker, GPS-/Qualitätsangaben und Status; `ManuelleAbschnittKmLogik` kann daraus `MANUELL_ABSCHNITT` berechnen.
- Diese manuellen Abschnitts-/Anker-Datensätze werden im aktuellen `WorksiteTrackDataSync` nicht als eigene Datensätze übertragen. Die Kamera-/Nähelogik kann `MANUELL_ABSCHNITT` lokal zur Laufzeit anwenden, ohne dass dadurch zwingend die persistierten Achs-Samples aktualisiert werden.
- Deshalb vor Kennzahlen/Serverübernahme nicht davon ausgehen, dass alle neueren manuellen Handy-KM vollständig auf dem Server vorliegen. Android↔Server-Vertrag für manuelle Abschnitts-KM muss vor Implementierung explizit festgelegt und anschließend auf beiden Seiten getrennt umgesetzt/getestet werden.
- SERVER ändert `app/**` nicht.


## SERVER-OPT-001 – Gesamtprüfung und Optimierung DS220+ / Vorschlagsphase 02.10.2026
Status: **ANFORDERUNG GESICHERT / ERST VORSCHLÄGE UND RÜCKFRAGEN / IMPLEMENTIERUNG NICHT FREIGEGEBEN**

- Neuer Benutzerauftrag: Server vollständig und bereichsübergreifend prüfen, für Synology DS220+ mit laut Benutzer 10 GB RAM optimieren, Administration optisch verbessern und Strecken-/Bahnhofs-/Gleis-/Weichen-/WVB-Daten verständlicher darstellen sowie Bearbeiten/Löschen ermöglichen.
- Verbindliche Reihenfolge: zuerst SERVER einschließlich Administration und Datenmodell, danach Benutzer-PORTAL, danach ANDROID. Android jetzt ausschließlich read-only mit Serverdaten/API/Verträgen vergleichen; kein app/** ändern.
- Vor Bauen konkrete Vorschläge und Fragen vorlegen. Diese Anforderungssicherung ist keine Freigabe für Code-, Runtime- oder Datenänderungen.
- Übergabe: Benutzeranhang „Eingefügter Text(10).txt“ / WORK-GESAMTAUFTRAG vom 02.10.2026. Seine Umsetzungsschritte werden durch die aktuelle ausdrückliche Vorschlagsphase begrenzt.
- Ausgangsstand live geprüft: SERVER d2cd589161ad6f3b6185c4bc5cc58ef397e27ef6 / PR #6 offen, Draft; ANDROID 423f37892915cb3308a4b1ea9e4d196faa872326 / PR #11 offen, kein Draft.
- Bestehende Regeln schützen Originaldateien und sehen im gemeinsamen Fachkatalog kein normales globales Löschen vor. Neue Löschanforderung daher vor Implementierung klären: Deaktivierung/Archiv mit Wiederherstellung, Umfang abhängiger Objekte, Berechtigung und ggf. getrenntes endgültiges Entfernen. Keine bestehende Schutzregel still aufheben.
- Prüfprogramm: API/Auth/Firmentrennung; DB/Migrationen/Transaktionen; Worker/Jobs/Backup/Restore; Ressourcen/Abfragen/Geometrie; Dateien/Bilder/PDF; BTB/Mängel/Abnahmen; Sync/Versionen/Löschweitergabe; Fachkatalog↔Survey↔Android-Zuordnung. Quellbefunde, lokale Tests und Synology-E2E strikt getrennt berichten.
- Erste read-only Quellbeobachtungen für Vorschläge, kein Runtime-Fehlernachweis:
  - Synology-Staging-Compose setzt API mem_limit über TB_V1_API_MEMORY_LIMIT mit Default 512m; tatsächliche NAS-Konfiguration unbekannt. Mehr Host-RAM hebt Containerlimit nicht automatisch an. Keine pauschalen RAM-/Worker-Einstellungen ohne Messung.
  - Survey-API enthält versioniertes PATCH axes sowie DELETE manual-segments als Deaktivierung; allgemeine vollständige Objektverwaltung ist damit noch nicht nachgewiesen.
  - Coverage-Standardantwort lädt weiterhin Achsmetadaten und je Achse aktive Segmente; Linien/Qualität sind standardmäßig aus. Übersichtstemplate ruft /coverage ohne include_overview auf. Darstellung aller vereinfachten Linien und Ladebudget gemeinsam klären.
  - Android WorksiteTrackDataSync (aktueller Android-HEAD) überträgt AXIS/STRECKENPUNKTE per LEAD-Snapshot; eigener TrassenManuellerAbschnittEntity-Datensatz fehlt in diesem Pfad. Kanonischen Survey-Vertrag und späteren Clientbedarf abgleichen.
- Bekannter erster Diagnosepunkt bleibt /api/v1/survey-catalog/coverage/summary: Helper laut gesichertem Runtime-Nachweis erfolgreich, Browser-/HTTP-Pfad offen. In dieser Sitzung kein neuer Synology-/HTTP-/E2E-Test ausgeführt.
- Nächster Schritt: Benutzer entscheidet Löschumfang, Bearbeitungsumfang und gewünschte Verwaltungsdarstellung; anschließend konkreten freigegebenen Arbeitsblock festhalten.


### SERVER-OPT-001 – Benutzerpräzisierung Bestandsansicht / Bearbeiten 02.10.2026
Status: **KONZEPT PRÄZISIERT / WEITER VORSCHLAGSPHASE / KEINE BAUFREIGABE**
- Benutzer stimmt Punkt 1 (Archiv/Papierkorb mit Wiederherstellung) mit „okay“ zu. Daraus keine ausdrückliche Freigabe für unwiderrufliches Löschen geschützter Originale ableiten; konkrete Löschoperationen sind nicht beauftragt.
- Bearbeiten gewünscht: Name, Bahnhof, Strecke, Gleis, Weichenkennung, Zuordnung und manuelle KM, fachlich wie Android, aber ohne GPS-Aufnahme am PC. Kein Auftrag zum freien Verschieben/Neuzeichnen der Originalgeometrie.
- Grafik muss Deutschland nicht wiedergeben. Gesamtansicht/Auszoomgrenze orientiert sich an den äußersten Punkten der vorhandenen Trassierung; sinnvollen Darstellungsrand berücksichtigen. Vorheriger Wunsch einer festen Deutschland-Gesamtansicht ist insoweit ersetzt.
- Beim Hineinzoomen sollen alle vorhandenen fachlichen Details erkennbar werden. Qualitätsfarben, Tiefzoom und eindeutige Auswahl überlagerter Achsen bleiben relevant.
- Klick auf Strecke oder Bahnhof soll ein überlagertes Fenster mit zugehörigen Daten und ggf. Bearbeitungsmöglichkeit öffnen. Benutzer bittet ausdrücklich um Einschätzung/weitere Vorschläge vor Umsetzung.
- Zur Diskussion vorzulegen: breite Bestandsgrafik mit kompakter Suche; „Gesamter Bestand“ und „Auswahl einpassen“; gestufte Details nach Zoom; Mehrfachtreffer-Auswahl; großes Detailfenster mit Übersicht/Objekten/Originalen/Problemen/Historie und bewusstem Bearbeitungsmodus; manuelle KM über Auswahl von Abschnittsgrenzen auf vorhandener TRA plus Eingabe der KM-Anker/Richtung.
- Vorschläge in voriger Zeile sind noch keine bestätigten Zusatzanforderungen. Keine Code-/NAS-/Android-Änderung; kein neuer Runtime-Test in diesem Abstimmungsblock.


### SERVER-OPT-001 – Ablauf bestätigt; echtes Laden und Ladezeit Pflicht 02.10.2026
- Benutzer bestätigt den zuletzt vorgeschlagenen Bedienablauf mit „okay“: bestandsbegrenzte Gesamtansicht, Zoomdetails, Auswahlfenster mit Bearbeitungsmodus und manuelle KM auf vorhandener Geometrie ohne GPS-Aufnahme.
- Zusätzliche verbindliche Anforderung: prüfen, dass die Daten tatsächlich geladen/angezeigt werden und nicht unangemessen lange brauchen. Ein Source-Markercheck oder erfolgreicher Helper-Aufruf genügt nicht.
- Erster Arbeitsblock: offenen Coverage-/Summary-HTTP-Pfad diagnostizieren; keine Fehlerursache erfinden. Danach bestätigtes Konzept schrittweise umsetzen, bestehende Schutz- und Zuständigkeitsregeln bleiben gültig.
- Abnahme muss Gesamtansicht, sichtbare Übersichtslinien, Zoomdetails und Detailfenster umfassen. Erstaufruf und erneuten Aufruf getrennt messen; HTTP-Dauer, Antwortgröße und Zeit bis zur nutzbaren Darstellung dokumentieren. Zielwerte sind noch keine gemessenen Ergebnisse.
- Fehler/Timeout/Sessionablauf müssen erkennbar sein; kein unbegrenztes Ladezeichen und keine scheinbar erfolgreiche leere Ansicht. Filter-/Auswahlwechsel dürfen keine veraltete Antwort anzeigen.
- Prüfen mit aktuellem realen Bestand; größere synthetische Bestände nur isoliert/lokal. Kein produktiver Lasttest durch diese Bestätigung autorisiert.
- Maßgeblicher Nachweis bleibt Synology plus Browser. Ohne authentifizierten Zugriff oder Messdaten bleibt dieser Teil ausdrücklich offen.


### REF-DATA-007 / SERVER-OPT-001 – Coverage Summary HTTP 524 reproduziert 02.10.2026
Status: **BROWSER-HTTP FAIL BELEGT / URSACHE INNERHALB ORIGIN-PFAD NOCH OFFEN**
- Benutzer-Screenshot: GET https://www.tb-helfer.de/api/v1/survey-catalog/coverage/summary endet mit Cloudflare „A timeout occurred“, Error code 524; Zeit im Screenshot 2026-10-02 15:54:39 UTC (=17:54:39 Europe/Berlin).
- Cloudflare zeigt Browser Working / Cloudflare Working / Host Error. Keine JSON-Antwort im Screenshot. Exakte Requestdauer nicht gemessen.
- HTTP-Pfad erreicht Cloudflares Origin-Verbindung, erhält aber nicht rechtzeitig eine Antwort. Das beweist weder eine bestimmte langsame SQL-Abfrage noch zu wenig RAM, defekte Authentifizierung oder eine konkrete Helper-Laufzeit.
- Vorheriger erfolgreicher direkter Summary-Helper-Aufruf bleibt gültiger Funktionsnachweis, ist ohne Zeitmessung aber kein Leistungsnachweis.
- Quellprüfung: Summary berechnet KM-Kontext pro Achse und Qualität pro Segment; GVP-Projektion kann sämtliche Samples je GVP durchlaufen. Datenbank/Auth/Request-Warteschlange und Geometrieberechnung bleiben zu unterscheiden.
- Nächster gezielter read-only Runtime-Schritt: Summary-Helper mit Laufzeit, begrenzter Ausführungsdauer, Stack-/Profiling-Ausgabe prüfen; dabei keine Zusatzpakete, keine DB-/Datenmutation, keine Änderung von Cloudflare-Timeout oder Container-RAM auf Verdacht.
- Weiterhin kein Codefix und kein erfolgreicher HTTP-/Browser-E2E behaupten.


### REF-DATA-007 – Runtime-Stack lokalisiert GVP-Projektion 02.10.2026
Status: **30-S-TIMEOUT IM DIREKTEN HELPER REPRODUZIERT / GEZIELTER PERFORMANCEFIX**
- Nutzer führt den begrenzten Diagnoseaufruf im Synology-API-Container aus. Nach 30 Sekunden Abbruch mit Stack: survey_catalog.py:638 _project_xy_station → :801 _segment_km_quality → :2614 _coverage_summary_data.
- Damit ist lange Berechnung im direkten Summary-Pfad reproduziert, unabhängig vom Browser/Cloudflare. Der Stack ist eine Stichprobe, kein vollständiges CPU-Profil; weitere Engpässe bleiben nach dem Fix zu messen.
- Gezielt optimieren: GVP-zu-Polylinie-Projektion ohne wiederholten Vollscan aller Liniensegmente; keine Geometrieausdünnung und keine Änderung der KM-/Qualitätsregeln (>=4 robuste Treffer, <=120 m, Median/±80 m).
- Neue Implementierung gegen bestehenden Vollscan auf identischen synthetischen Geometrien vergleichen (Grenzfälle, Überlagerung/Tie-Break, Stationierung, GRA-/manuelle Priorität, Farben/KM); lokalen Lastfall mit Zeitvergleich ausführen.
- Kein neues Runtime-Paket, keine Migration, keine Änderung von Cloudflare-Timeout, Container-RAM oder app/**. Reale Synology-/HTTP-/Browser-Abnahme nach Deployment bleibt Pflicht.


### REF-DATA-007 – gezielter Fix implementiert, Runtime-Gate ausstehend
- _GvpProjectionIndex im bestehenden survey_catalog.py gebaut; nur _segment_km_quality fachlich angebunden. Alle übrigen vorhandenen Funktionen AST-identisch.
- Check 118 enthält elf ausführbare Algorithmus-/Qualitätstests und optionalen lokalen Benchmark gegen den unveränderten Vollscan. PASS, Benchmark ca. 91,5-fach schneller im synthetischen lokalen Fall.
- Nächster Schritt ist das Synology-Runtime-Gate (Helper-Laufzeit/Kennzahlen + authentifizierter Summary-HTTP-Aufruf + Dashboard). Der restliche bestätigte Bestands-/Detailfenster-Umbau bleibt danach offen; nicht als fertig ausweisen.


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


## REF-DATA-008 – Neue Kartenansicht bestätigt / Weichenklärung (02.10.2026 20:35 Europe/Berlin)
- Nach Dateimarkercheck zunächst False/False/True/True, nach Templateübernahme True/True/False/True. Neue Ansicht jetzt in drei Screenshots sichtbar; Benutzer: „top, sieht super aus und lädt schnell“. Darstellung und subjektive Ladezeit des gezeigten Kartenablaufs BESTÄTIGT; keine pauschale Abnahme aller Dialoge/Historiendetails/Lastfälle.
- Benutzer fragt nach W:107W, Speicherung/Android-Transport, realem Weichenanfang/-ende und Stammgleis/Abzweig; Auftrag: Erklärung und weiter.
- Zuerst aktuellen Import-/Objektidentitäts-/Geometrie-/Android-Vertrag lesen; Quellachsenlänge nicht als nachgewiesene physische Weichenlänge darstellen. Keine Nummernkürzung oder Stamm-/Abzweigzuordnung ohne belastbare Originaldaten.
- Anschließend beauftragte Kartenverwaltung fortführen; Server/Portal zuerst, Android bleibt unverändert.


## REF-DATA-008 – Originaldialog belegt / Verwaltung fortsetzen (02.10.2026 20:48 Europe/Berlin)
- Benutzer liefert Übersicht, Abschnitte, Originaldateien und Historie für W:107W / MGHN. Quelle sichtbar: SW_107W_GGP_x1_ER0.TRA, TRA, 624 Bytes; ein aktiver Originalabschnitt, Achsposition 0 bis 55.840975 m, Achsversion 3. Historie zeigt Abschnittsanlage und spätere MGHN-/Gessertshausen-Zuordnung. Kein Rohdateiinhalt übergeben, keine Stamm-/Abzweig- oder baulichen Grenzbelege.
- Diese lesenden Detailtabs funktionieren im gezeigten Benutzerfall. Screenshot zeigt noch den Stand ohne neue Weichenhinweise; deren Deployment nicht behaupten.
- Benutzer „weiter“: vorhandenen freigegebenen Bearbeitungs-/Archivweg prüfen und weiter umsetzen. Originale, Geometrie und bestätigte Karte bleiben geschützt; Änderungen nur bewusst, mit Versionsschutz/Historie und ohne stilles Mitlöschen.


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


### REF-DATA-011 – reproduzierte Summary-Gates vor Freigabe
- Ausführung der echten _coverage_summary_data mit Cursor-Doubles: Original 0..1000 m + manuelle Abschnitte 0..100 und 50..150 ergibt falsch 200 m manuell statt 150 m. Ein manueller Abschnitt 0..100 mit Qualität FEHLT ergibt falsch 100 m manuell. Beide Fälle werden im aktuellen Block korrigiert/regressionsgeprüft: stationsbezogene Vereinigungsmenge auf derselben Originalquelle, nur fachlich MANUELL qualifizierte Abschnitte umklassifizieren; nicht auf andere Achsen/Originalstationierungen übertragen.


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


## REF-DATA-013 – GVP-Zweipunkteditor und sichtbare Achsbeschriftung (04.10.2026)
- Nutzerfreigabe: PC-Kilometrierung über zwei ausgewählte GVP mit eingegebenen Strecken-km, Lot auf ausdrücklich gewählte Originalachse, Berechnen/Vorschau/Speichern. Numerischer Editor bleibt Alternative. Screenshot bestätigt Öffnen des bisherigen Editors, nicht dessen vollständigen Schreib-E2E.
- Additiv: Admin-GET management/axes/{axis_id}/gvp?source_segment_id liefert begrenzte räumliche Kandidaten aus aktiven GLOBAL-GVP/KF-Assets plus Quellgeometrie in DBREF. Räumliche Nähe ist keine automatische fachliche Zuordnung; Quelle/Route/Punktname anzeigen, Benutzer wählt ausdrücklich. GET liest nur.
- Admin-CSRF POST .../manual-km/gvp-calculate löst zwei Referenzen (asset_id, asset_version, point_index, eingegebener km_meter) serverseitig auf und projiziert auf gewählte aktive ORIGINAL_TRA-Geometrie. Keine Endpunktklemmung; außerhalb/mehrdeutige Projektion ablehnen. Quell-/Achs-/Assetversion prüfen. Save/Preview prüfen optionale gvp_anchors erneut; Herkunft/Punktdaten/Lotfuß getrennt im manuellen Overlay speichern, Originale unverändert.
- Zweipunktrechnung zwischen aufsteigend geordneten Lotfußstationen; fallende KM erlaubt. Android-Zweipunktkalibrierung als Referenz: mindestens 20 m Stationsabstand, mindestens 1 m KM-Differenz, Betrag Faktor 0,5..1,5. Neuere Android-Einanker-/KM-Sprunglogik ist nicht Bestandteil dieses Blocks. Keine automatische Extrapolation oder gemeinsame Weichenast-Kilometrierung.
- Kartenfehler: Rechteckmitte durch Anker am tatsächlich sichtbaren Linienverlauf ersetzen; Gleisnamen beim Zoom/Pan mitführen. Kollisionsarme Beschriftung mit sichtbarer Zuordnung; keine pauschale Unterdrückung aller Namen ab 31 Achsen. Randhinweise nur geometrisch belegbare Fortsetzung der benannten Achse, keine behauptete topologische Reihenfolge kommender Weichen.
- SERVER-only, keine neue Migration/Dependency, geschützte automatische GVP-Summary unverändert. Quelltests und NAS-/Browser-/DB-E2E getrennt berichten.


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


## REF-DATA-015 – Gesamtfreigabe Server/Portal (04.10.2026)
- Nutzer: „okay machen wir so und den rest auch gleich mitmachen … lege los“. Umfang: 5302-0/Stationssprünge reparieren; Importprüfung verständlich ergänzen; amtliche OpenData-Luftbilder BY/SN/BW optional zuschalten; originale Achsen nicht skalieren; bestätigte Gleisabschnitte und Weichen WA/WE/WEZ per Kartenklick/Stationswert erfassen; gezieltes Testimport-Zurücksetzen mit Vorschau. Server/Portal zuerst; Android-Vertrag sichern, kein app/**-Umbau. Keine Ausführung einer Löschung des NAS-Bestands durch diesen Auftrag.
- Abschnittszuordnung getrennt von Original und offiziellen Bahnhofsgrenzen; Quelle/Bearbeiter/Version. Punkte beziehen sich auf explizite aktive Originalsegmente und geometrische Stationierung. Weiche: gemeinsame WA-Position, WE Stamm und WEZ Zweig mit eigenen Achsreferenzen; Geometrie bleibt unverändert. Aufnahme per Luftbild ist manuelle Interpretation, kein Vermessungsnachweis.
- Additive Admin-Workspace-Routen für Quellgeometrie, Lesen/Prüfen/Anlegen/Ändern/Deaktivieren der Abschnitts-/Weichenobjekte. Original-/Achsversionskontrolle, Admin+CSRF, transaktionale Historie und idempotente Mutationen. Fremde/private/archivierte Achsen ausgeschlossen; keine automatische Nachbargleiszuordnung. Persistenz auf versionierten Katalogachsen-Metadaten, damit bestehende AXIS-Historie/Changes erhalten bleiben; Android muss neue workspace_records später auswerten.
- Testreset: ausdrücklich ausgewählte GLOBAL-Originalquellen, vorab serverseitige Bestands-/Abhängigkeitsvorschau und Fingerprint; Bestätigung vor Mutation. Katalog-Assets/Abschnitte inaktiv, leere Achsen archivieren; keine Rohoriginale/Dateien/BTB löschen. Gemeinsam verwendete Daten und referenzierende Arbeitsobjekte nicht still löschen. Alte Importe ohne Paket-ID über bewusste Quelldateiauswahl. Reimport-Verhalten ausdrücklich rücksetzbar statt verborgenem Löschzustand. Additive Migration für Assetstatus INACTIVE falls benötigt.
- Luftbild: Anbieterregistrierung und explizites Einschalten mit Attribution, Netzfehler sichtbar, kein Google-API-Konto/Abrechnung. BY/SN/BW-Dienste anhand amtlicher Capabilities/Lizenz prüfen. Automatische Auswahl anhand abgedeckter Kartenposition; bei Grenz-/Mehrfachtreffern Auswahl ermöglichen. Dienstabhängigkeit und Aufnahmealter anzeigen, Offline nicht pauschal versprechen.
- Koordinaten: Stationssprünge nicht durch pauschales Clamping kaschieren. Originale mit vollständigen Rohstationsgrenzen gegen normalisierte Geometrie korrekt darstellen; manuelle geometrische Ausschnitte bleiben streng geprüft. Importdiagnose nennt Sprünge und Grenzen. Regression am bereitgestellten Originalpaket, keine Rohoriginale ins öffentliche Repo.


## REF-DATA-015 – Implementierter Serverblock, Live-Abnahme offen (04.10.2026)
- Freigegebener Server/Portal-Umfang als Code umgesetzt. Originale und NAS-Bestand in dieser Sitzung nicht geändert. Keine automatische Neuvermessung/Google-Einbindung.
- Gleisgrenzen sind bestätigte Arbeitszuordnungen, keine aus Dateiende/Luftbild abgeleiteten amtlichen Bahnhofsgrenzen. Weichen brauchen zwei passende bestehende Achsen, Stamm und Zweig besitzen eigene Längen.
- Für die Live-Übernahme sind fünf Laufzeitdateien einschließlich neuem survey_workspace.py und Migration020 nötig. Nicht nur das Template kopieren. Danach Admin-Workspace, Quellenreset/Restore und BY/SN/BW-Bilder live prüfen.

Details, API-Vertrag, Quellen, Übernahme und Abnahmefälle: [REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md](uebergaben/REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md).



## REF-DATA-016 – Freigegeben: Anschlussnetz, bidirektionaler Austausch und Serverprüfung (04.10.2026)
- Benutzer bestätigt Gl4→WA→WE→Gl4, Gl4→WE→WA→freie Strecke und Ketten aus mehreren Gleisen/Weichen einschließlich WEZ→WA. Punktrollen gehören zur jeweiligen Weiche; Betrachtungsrichtung ändert sie nicht. Ein gemeinsamer Anschlusspunkt kann mehrere Objektrollen tragen; physische Nähe allein verbindet nichts.
- WA/WE/WEZ unabhängig setzen: Originalachsenreferenz oder ausdrücklich manueller WGS84-Punkt. Gemeinsame stabile Knoten-IDs; versionierte Gleis-/Streckenabschnitte und Weichen; Anschluss wiederverwenden statt Koordinatenduplikat. Gleisabschnittsgrenze ist keine automatisch bestätigte Bahnhofsgrenze. Originale unverändert. Manuelle Verbindung ist eine Näherung, keine erfundene exakte Trassierung.
- KM getrennt von Topologie: Original-/GVP-/manuelle KM-Kalibrierung nie durch Namen, Richtung, Anschluss oder Abschnittsgrenze verändern. Achsposition ist nicht Bahnkilometer. Mehrere streckenbezogene Kilometerreferenzen an demselben Ort zulässig; ohne belegbare Referenz KM unbekannt. Stamm/Abzweig unabhängig.
- PC: kompakte Seitenleiste direkt an der Karte, Punktwahl und Prüfen/Speichern ohne Seitenscrollen; bestehende schnelle Übersicht, GVP-Kalibrierung, Farben und Originalquellen schützen.
- Handy→Server→Handy muss dasselbe versionierte Schema verwenden: clientseitig stabile UUIDs, client_change_id, erwartete Versionen, atomare Änderungen, Idempotenz und Cursor/Deltas einschließlich Deaktivierungen. Keine vollständigen Bestände aus altem Offline-Stand überschreiben. Server und Portal zuerst; keine app/**-Änderung in diesem Block. Android-Implementierung und echter Zwei-Geräte-Roundtrip ausdrücklich separat offen.
- Weitere Bundesländer: offizielle Luftbildanbieter einschließlich Nutzungsbedingungen prüfen; nur verifizierte Dienste anbieten, keine pauschale kostenlose/flächendeckende Zusage. Servercode zusätzlich gezielt auf Fehler prüfen; lokalen Test, echten DB-/HTTP-Test und NAS-Abnahme sauber unterscheiden.
- Benutzerbelege REF015: Migration20/20 ohne ausstehende Migrationen; Bayern-Luftbild sichtbar; Gl4-Anfang/Ende funktioniert. WE/WEZ-Bedienung bisher zu restriktiv. Kein vollständiger Geräte-/Reset-E2E dadurch nachgewiesen.


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


## REF-DATA-016 – Punktwahl: Benutzerbefund, zuerst Vorschläge (04.10.2026, 20:40)
- Benutzer: WA, WE und WEZ müssen unabhängig an beliebigen Positionen auf der jeweils gewählten Linie gesetzt werden können, nicht an den Beginn der Quelldaten gebunden. Aktuelle Bedienung funktioniert laut Nutzer nicht ausreichend. Ausdrücklich zuerst Vorschläge; keine Runtime-Implementierung freigegeben in diesem Folgeblock.
- Screenshots: WE und WEZ zeigen Originalachse 0.000 m; markierte Situation Weichen107/106. Das beweist noch keine technische Ursache. Systemstatus zeigt Migrationen21/21, ausstehend keine, System OK/0 offene Fehler; keine vollständige fachliche E2E-Abnahme.
- Livebasis SERVER3a87a8a / PR6 Draft, ANDROID423f378 / PR11 offen. Quellprüfung: networkMapClick nutzt eine gemeinsame Achs-/Quellenauswahl und nearestWorkspaceStation. Diese Funktion interpoliert entlang Liniensegmenten, begrenzt Projektion aber auf deren Enden. Eine generelle Pflicht zum Datenanfang steht nicht im geprüften Klickpfad. Falsche Quelle, Endpunkttreffer oder Positionsdaten müssen gezielt reproduziert werden; Ursache noch offen.
- Bedienvorschlag, noch nicht beschlossen: kompakte WA/WE/WEZ-Auswahl direkt an Karte, pro Rolle sichtbare eigene Achse, Klickposition entlang kompletter gewählter Linie, eindeutige Achswahl bei Überlagerung, sichtbare Vorschau vor Übernahme und nachträgliches Verschieben auf der Linie. Kein stilles Springen auf Quellanfang/-ende. Vorhandenen Anschluss ausdrücklich wiederverwenden (z.B. WE107=WA106 nur nach Benutzerzuordnung). KM-Kalibrierung/Originalgeometrie unverändert, Achsposition getrennt von Bahnkilometer anzeigen.
- Nur Benutzerbefund/Vorschlag dokumentiert; keine Runtime-/Android-/NAS-Änderung und keine Tests als erneut ausgeführt behauptet.


## REF-DATA-017 – Freigabe Punkteditor (04.10.2026, 20:49)
- Benutzer „okay“ gibt die zuvor vorgeschlagene Umsetzung frei: kompakte WA/WE/WEZ-Auswahl, eigene Achse pro Rolle, beliebiger Linienpunkt mit sofortiger Vorschau, explizite Auswahl bei Überlagerung, nachträgliches Verschieben entlang der Linie und Achswechsel, ausdrückliche gemeinsame Anschlüsse, Menü „Hier anschließen …“.
- Vor Fix Ursache reproduzieren; keine Vermutung als bestätigte Ursache. Originale/KM-Kalibrierung und API-Vertrag bleiben geschützt. Server-/Portalblock, kein Android-Umbau. Migration21/21 im Benutzerscreenshot belegt; neue Bedienung noch nicht abgenommen.


## REF-DATA-017 – Punkteditor: Quellstand umgesetzt (04.10.2026)
- Freigabe vor Code: fa7b8c4198865e36dba21d75bf720539f774d7eb. SERVER-only, PR6 bleibt Draft; ANDROID423f378 unverändert. Runtime-Datei ausschließlich server/templates/v1_admin_survey_overview.html.
- Reproduzierbarer Fehlerpfad: bisherige nearestWorkspaceStation begrenzt Projektionen auf Endpunkte. Synthetischer 100-px-Linientest mit Klick 5px vor Beginn ergibt Station0. Neue Netzpunktwahl interpoliert nur innerhalb eines Liniensegments, verwirft zu weit entfernte, ungültige oder mehrdeutige Treffer. Das belegt den Fehlerpfad, nicht abschließend die Ursache aller NAS-Screenshotpositionen. Alter Legacy-/GVP-Editor unverändert.
- WA/WE/WEZ bzw. Anfang/Ende als feste kompakte Rollenleiste, nur aktive Rolle im Formular. Eigene Achs-/Quellen-/Kartenmodus-Auswahl je Rolle; beim Rollenwechsel wiederherstellen. Auswahl und Quelle sichtbar, gewählte Linie hervorgehoben; überlagerte Achsen ausdrücklich über Auswahl unterscheiden. Positionen entlang kompletter ausgewählter Quelle setzen, sofortiger Marker; keine automatische Verbindung aus Nähe.
- Marker per Pointer entlang ihrer eigenen Quelle verschieben; andere Rollen und Kartenansicht bleiben stehen. Abbrechen bzw. außerhalb der Linie loslassen stellt Ausgangspunkt/Prüfvorschau wieder her. Freie Kartenpunkte weiterhin möglich. ID, Basisversion und km_observations bleiben beim Verschieben erhalten. Tatsächlicher Speicherpfad/Versionsprüfung/gemeinsame Änderungen bleiben unverändert.
- Wiederholte Fortsetzungsbuttons durch Menü „Hier anschließen …“ ersetzt. Rollenleiste, Status und Prüfen/Speichern bleiben außerhalb des scrollenden Formularbereichs erreichbar. Achsposition ausdrücklich kein Bahn-km. Originalgeometrie und KM-Kalibrierung unverändert.
- Test: node server/checks/119_verify_coverage_map_logic.js PASS mit echten Templatefunktionen und DOM-/Transport-Doubles; erweitert um Endpoint-Clamp-Reproduktion, innere WA/WE-Positionen, unabhängige WEZ-Quelle, Rollenwechsel, ID-/KM-Erhalt, echte Pointerhandler, Abbruch/Vorschau-Restore, ungültiges Drop und überlagerte Stationen. Bestehende 119-Prüfungen PASS. Keine erneute Behauptung der 97 Backendtests; Backend unverändert.
- Kein visueller Browser-/NAS-/HTTP-/DB-/Geräte-E2E: Chromium lokal fehlt, Download durch Umgebung HTTP403 blockiert. Benutzerbild des Vorgängers belegt Migration21/21 ohne ausstehende Migrationen, keine fachliche Abnahme des neuen Editors.
- Übernahme: GitHub Desktop → File Station; server/templates/v1_admin_survey_overview.html nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html ersetzen, API über Container Manager neu starten, Browser Strg+F5. Keine neue Migration, weiterhin21/21, kein Reindex/Reset. NAS-Nachtest: WA/WE mitten auf Linie, WEZ andere Achse, Rollenwechsel, Verschieben/Abbrechen, Prüfen/Speichern/Neuladen; gemeinsamer Anschluss WE107→WA106 und GVP-KM unverändert. Erst danach fachlich bestätigen. Android-Anbindung weiterhin später.


## REF-DATA-018 – Praxisfehler Punktwahl und WA vor TRA (04.10.2026, 21:21)
- Benutzer: WA weiterhin nicht frei setzbar und darf auch eine bestimmte Länge vor den TRA-Daten liegen; WEZ setzbar, WE auf überlagertem Gl1/Strecke5340 nicht setzbar. Screenshot zeigt bei WE weiterhin W107W/SW_107W_GGP_x1_ER0.TRA als Zielquelle und Ablehnung des Klicks auf Stammgleis. REF017 daher nicht fachlich abgenommen. Fehlerbehebung im bestehenden Server-/Portalauftrag; Android als Referenz lesen, app/** nicht ändern.
- Ziel: Rollenwahl → Kartenklick ermittelt tatsächlich getroffene Achsen/Quellen. Mehrfachtreffer Gl1/5340 ausdrücklich am Klick auswählen, ohne Objektwechsel oder Punktverlust; Weichenast darf WE nicht an seine Quelle binden. Freie Kartenposition (auch vor/nach Daten) direkt erreichbar. Explizite Längenposition vor/nach Quelle als manuelle tangentiale Näherung anbieten, nicht als extrapolierte Originalgeometrie/KM verkaufen. Quelle/Abstand/Herkunft nachvollziehbar, bestehende Originale und KM unverändert.
- Android-Livebasis423f378: ImportExportFragment.zeigeStreckenObjektEditor hält GPS/KM/Punktrolle unabhängig; GPS darf ohne passende TRA gespeichert werden. TrassenGrafikView liefert Treffer mit jeweiliger interpolierter Station und Mehrfachauswahl bei überlagerten Achsen. Abschnittslängenfunktion zeigeGrenzePerLaenge begrenzt dagegen auf Roh-TRA; keine vorhandene Android-Extrapolation behaupten. Diese fachliche Trennung im Portal übernehmen.
- Umsetzung vor allem Editor, bestehenden Network-Schema1 nutzen (Binding nur innerhalb echter Quelle, außerhalb manuelle MAP-Position mit Hinweis); IDs/Versionen/KM-Beobachtungen erhalten. Punkte außerhalb der TRA nicht auf Endpunkte klemmen und keine Original-KM erfinden. Konkrete Gl1/5340/W107W-Situation regressionsprüfen. Lokale Tests und NAS-/Browser-/Gerätetest getrennt ausweisen.


## REF-DATA-018 – Kartenklick entscheidet die Punktachse (04.10.2026)
- Anforderung/Befund vor Code gesichert in f808800a60404c323d8eb16f5d70db8908a3e51b; Runtime-Basis3c38cf0. Benutzer meldet REF017 weiter fehlerhaft, keine fachliche Abnahme. Screenshot belegt WE-Klick auf Gl1/5340 bei noch fest ausgewählter Quelle W107W; WEZ gelingt auf dem Weichenast. Bisheriger strikter Picker hat nur die vorgewählte Quelle geprüft. Nur einen solchen Picker zu verbessern löste den tatsächlichen Bedienablauf nicht.
- Neue Standardbedienung: Rolle WA/WE/WEZ → „Linie wählen“ → Karte anklicken. Kandidaten aus tatsächlich getroffenen sichtbaren Achsen laden, je Originalquelle die Station am Klick bestimmen. Bei Gl1/Str5340 bzw. mehreren Quellen/Stationen explizite Trefferwahl direkt im Editor, ohne normalen Detaildialog/Objektwechsel. Kein automatisches Übernehmen des ersten überlagerten Treffers. Eindeutige vollständig geladene Quelle direkt übernehmen; bei Ladefehler eines Kandidaten Auswahl als unvollständig kennzeichnen, keine automatische Einzeltrefferentscheidung. Max24 Achsen/4 parallele Leseaufrufe, sonst Zoom-/Quellenauswahlhinweis.
- „Frei setzen“ fest neben Rollenwahl; jede Rolle auch vor/nach/außerhalb TRA als MAP-Position. Ohne Treffer kann der genaue Klick ausdrücklich frei übernommen werden. „Abstand …“: Quelle, vor Anfang/nach Ende, positive Meter bis10000 → manuelle tangentiale Näherung entlang Endrichtung (WGS84-Großkreisrechnung auf mittlerer Erdkugel). Diese Hilfsposition ist ausdrücklich keine vermessene Fortsetzung und keine Bahnkilometer-Extrapolation. Verlauf außerhalb Originaldaten bleibt manuelle Näherung.
- Network-Schema1/Backend unverändert: echte Quellenpunkte als binding; freie/verlängerte Punkte als position.method=MAP ohne binding. Abstand/Seite/Quellenname/-ID/-Version und Achs-ID/-Version als Herkunftsnotiz [TRA-HILFSPUNKT] im bestehenden note-Feld, nicht als aktive Quellbindung oder automatische Nachführung. Nach späterem freien Verschieben/Neubinden entfernt der Editor den dann überholten Hilfspunkt-Hinweis. Andere Notizen, IDs, Basisversionen und km_observations bleiben erhalten; Originale/KM-Kalibrierung unverändert.
- Mehrfachauswahl hält vorherige Punkte bis bewusster Auswahl, unterbindet Prüfen/Speichern während offener Entscheidung. Rollen-/Moduswechsel, Escape, Reload oder neuer Entwurf verwerfen Kandidaten; verspätete Antworten dürfen keinen Punkt ersetzen. Klickkoordinaten unabhängig von nachfolgendem Kartenpan/Zoom auflösen. Drag bleibt explizit an der Quelle des gezogenen Punktes; „Linie wählen“ erlaubt neuen Bezug. Quellen-Cache bei Stand-/Coverage-Neuladen leeren.
- Android423f378 read-only geprüft: ImportExportFragment.zeigeStreckenObjektEditor/GPS-Messung hält Rollen und Koordinaten unabhängig von passender TRA; TrassenGrafikView bietet Achstreffer samt Station und Mehrfachtreffer bei LongPress. Android-Abschnittslängenfunktion begrenzt dagegen auf vorhandene TRA. Keine Android-Extrapolation behaupten; keine app/**-Änderung/kein neuer APK-Build. Gemeinsame Netzwerk-Sync-Anbindung weiterhin später.
- Verifikation: node server/checks/119_verify_coverage_map_logic.js PASS, einschließlich bestehender Regressionen und neuer W107W/Gl1/5340-Situation mit zwei deckungsgleichen Hauptachsen/eigenen Stationen, unabhängiger WEZ-Astwahl, WA vor Weichenquelle auf vorhandener Hauptachse, freiem WA vor allen Daten, vor/nach Abstand (metrisch geprüft), ungültiger Abstände, Erhalt von ID/KM/Notiz, unvollständigen Quellen, abgebrochener asynchroner Auswahl, Schema1-Payload und Wiederöffnen mit Testantworten. Echte Templatefunktionen, DOM-/Transport-Doubles. Kein neuer Backendtestlauf, kein echter HTTP-/DB-/NAS-/Geräte-E2E, kein visueller Browsernachweis (Browser fehlt; vorheriger Download HTTP403 blockiert).
- QUELLSTAND IMPLEMENTIERT UND LOKAL GEPRÜFT; DEPLOYMENT/NAS-ABNAHME OFFEN. Nur Runtime-Datei server/templates/v1_admin_survey_overview.html über GitHub Desktop→File Station nach /volume1/docker/tb-helfer-v1/templates/v1_admin_survey_overview.html ersetzen; API über Container Manager neu starten; Browser Strg+F5. Migration bleibt21/21, kein Reset/Reindex. NAS-Test: W107W öffnen, WE→Linie wählen→Gl1/5340 anklicken→Treffer ausdrücklich wählen; WA frei vor Quelle oder Abstand setzen; WEZ separat; Vorschau/Speichern/Neuladen und unveränderte GVP-KM prüfen. Keine Abnahme aus lokalen Tests ableiten.


## REF-DATA-019 – Gemeinsamer Streckennetz-Arbeitsplatz, Freigabe 04.10.2026 22:16
- Benutzer gibt Vorschlag ausdrücklich frei: gemeinsamer Bereich Streckennetz mit Karte, Bahnhöfen/Strecken, Import/Originalen und Änderungen/Prüffällen. Bestehende Katalogfunktionen behalten, in zusammenhängenden Bedienablauf integrieren.
- Priorität1: Gl999/Netzobjekte in Karte, Bahnhofsliste/Suche; zuverlässiges Nachladen mit altem sichtbarem Stand bei Fehler, Anzeigeauswahl speichern; WA/WE/WEZ einschließlich frei/überlagert/vor TRA/Speichern/Wiederöffnen praktisch abnehmen.
- Priorität2: eindeutige Verknüpfung Fachkatalog/Survey/Netz ohne Namens- oder Geometrie-Automerge; verständliche Bearbeitungszustände/Historie; Archiv/Wiederherstellen mit Referenzschutz prüfen.
- Bestehende IDs/Versionen/Originale/KM schützen, keine automatische Bestandsmigration. Server/Portal zuerst, app/** unverändert. NAS-/HTTP-/DB-/Gerätenachweise getrennt von lokalen Tests; kein Zugang zu NAS aus diesem Auftrag ableiten.
- Gesamt-Admin bleibt bis Betriebs-/Rechte-/Backup-Restore-/Worker-/Lastabnahme offen. Quellstand und praktische Abnahme getrennt dokumentieren.


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


## OPS-BACKUP-002 – NAS-Abnahme und verständliche Bezeichnung (05.10.2026)
- Screenshots: Vollbackup05.10.16:07:10 mit848MB OK; Restore-Test05.10.16:45:42 OK, Job16:45:39 Fertig. Getrennte restore-test-db sichtbar. Dieser konkrete NAS-Prüflauf ist bestätigt; keine produktive Rücksetzung und kein vollständiger Einzeldatei-/Snapshotkonsistenznachweis.
- Benutzerfreigabe16:52 „okay weiter“: UI nennt bisherige Restore-Test-Funktion „Backup prüfen“. Prüfung stellt in getrennte Testumgebung wieder her, laufender Bestand bleibt erhalten. Kein Live-Restore durch Umbenennung.
- Button, Vorgangs-/Historienbezeichnung und Bestätigungsdialog entsprechend ändern; RESTORE_TEST und API-Pfade unverändert. Servere423c8b, Android423f378. Originale/DB unverändert.


## RIGHTS-001 – Firmen-/Benutzerprüfung, Befunde vor Code (05.10.2026)
- Freigabe Benutzer16:56, Basis SERVERf6025af / PR6 Draft, Android423f378 / PR11 unverändert. Bestehende Rollen-/Firmentrennung erhalten, API-Engineer-Vertrag-zuerst angewandt; keine Postman-Cloudaktion.
- Reproduziert: _merge_permissions({}, {'files.view':'false'}) gibt True. Nur echtes bool True darf ein Recht erteilen; ungültige Overridewerte sperren statt Profilgrant zu übernehmen.
- Reproduziert: NaN-Endpunkte passieren area_scope_allows gegen begrenzten KM-Bereich. Ungültige/nichtendliche KM-Scopewerte führen zur Ablehnung, keine Wildcard durch Wegfall ungültiger Dimension.
- worksite_context sowie normale Benutzer-Baustellenliste vertrauen aktiver Mitgliedschaft ohne ausdrücklichen Firmengleichheitscheck. Normale Einladung prüft Firma und Admin-Firmenwechsel mit Historie ist bereits blockiert; zusätzliche zentrale Absicherung gegen inkonsistente Alt-/DB-Zuordnung, keine behauptete normale Cross-Firma-Einladungslücke.
- Umsetzung nur permissions.py/worksites.py mit gezielten Verhaltensfällen für Firmen/Rollen, bool-Rechte, Scope und bestehende Authsperren. Benutzer-Sperre/Expiry bei Portal/Gerät/Admin im Quellpfad vorhanden, vollständiger HTTP-/DB-/NAS-Rechtetest separat.


## RIGHTS-001 – zentrale Rechteprüfung gehärtet (05.10.2026)
- Vorabbefunde vor Umsetzung gesichert. permissions.py: Nicht-Admin benötigt übereinstimmende nichtleere Firmen-ID unabhängig von Mitgliedschaft; normale Baustellenliste in worksites.py filtert zusätzlich Firma. ADMIN-Leserechte und CHEF-eigene-Firma bleiben erhalten. Einladungs-Firmencheck und Firmenwechsel-Historieguard waren bereits vorhanden.
- _merge_permissions erteilt Rechte nur für bool True; insbesondere String 'false' und Zahl1 erteilen kein Recht. Ungültige Overrides sperren jeweiliges Recht. Keine Datenmigration; reguläre JSON-Boolwerte unverändert.
- Nichtendliche/ungültige/Bool-KM-Werte markieren Bereich als ungültig und verweigern Zugriff; auch bei all-Flag kein Umgehen ungültiger Werte. Leerer Helper-Scope bleibt gesperrt, gültige fallende Grenzen/Labels funktionieren.
- 132_verify_permission_guards.py elf Testgruppen PASS: zentrale Rollen/Firmen, fehlende Firma, inaktive Mitgliedschaft, ungültige Boolwerte/KM, gültige Scopes, Listen-SQL-Firmenfilter; bestehende Portal-/Geräte-/Admin-Authsperren für disabled/deleted/expired (neun Kombinationen). Echte isolierte Produktionsfunktionen mit SQL-/Request-Doubles; Python-Syntax PASS. Kein Live-HTTP/PostgreSQL/NAS-/Mehrfirmen-E2E.
- Quellsichtung Dateien: Baustellen-Dateizugriff verwendet worksite_context, Dateien ohne Baustelle prüfen Firma; Detail-Bereichsprüfung bleibt vorhanden. Keine vollständige Prüfung sämtlicher Upload-/Link-/Feed-/Portalpfade behaupten. Firmenstatus-Deaktivierung, vollständige Endpunktmatrix und reale Session-/Gerätesperrtests verbleiben als Folgeprüfung. Gemeinsamer GLOBAL-Fachkatalog ist fachlich beabsichtigt firmenübergreifend und bleibt unverändert.
- Runtimeübernahme nur server/tbserver/permissions.py und worksites.py nach /volume1/docker/tb-helfer-v1/tbserver/; API neu starten. Keine Migration, kein Worker-/DB-Neustart, kein Android/Originaldatenumbau. Praxistest mit getrennten bestehenden Testfirmen: Liste und direkte fremde Baustellen-/Dateiadresse gesperrt; eigener Chef/Benutzerzugriff wie berechtigt; gesperrter/abgelaufener Testnutzer mit alter Session/Token abweisen. Produktive Nutzer nicht zu Testzwecken sperren.


## RIGHTS-002 – Admin ohne Baustellen-Fachverwaltung (05.10.2026)
- Benutzerfreigabe 17:33/17:35: Baustellen werden von Firma/Nutzern über Portal bzw. App geführt und bleiben auf dem Server. Systemadmin legt keine Baustellen an, öffnet/bearbeitet keine Fachinhalte. Nur reduzierte Firmenliste zur vollständigen ausdrücklichen Löschung einer Baustelle bleibt.
- UI und direkte Admin-/Fach-API-Pfade entsprechend begrenzen; Portal-/Geräterechte berechtigter Firmenbenutzer und gemeinsamer Fachkatalog bleiben erhalten. Bestehende Daten werden durch den Umbau nicht gelöscht. Destruktive Ausführung nur nach Vorschau und ausdrücklicher Bestätigung, bestehende Purge-Infrastruktur prüfen.
- RIGHTS-001: Benutzer meldet 17:24 funktioniert. Keine zusätzliche Bestätigung für gesperrte/abgelaufene Sessions oder vollständige Endpunktmatrix daraus ableiten.
- Umsetzung/Tests/Deployment für RIGHTS-002 noch offen; Android bleibt unverändert.


## RIGHTS-002 – Admin-Baustellengrenze umgesetzt (05.10.2026)
- Firmenbereich zeigt für Baustellen nur Name/Status und Komplett löschen. Anlegen/Öffnen, Fachfelder und Anlege-JavaScript entfernt; Firmentexte angepasst. Abfrage lädt nur id/name/status. Bestehende Datensätze bleiben unverändert.
- Admin-Anlege-API antwortet 403. worksite_context verweigert ADMIN vor Datenabfrage; ADMIN_PERMISSIONS leer. Allgemeine Baustellenliste verweigert ADMIN. Alte Admin-Baustellen-/BTB-Seiten leiten zu Firmen um. Berechtigte CHEF-/Benutzerpfade bleiben erhalten. Baustellen-Dateipfad benutzt worksite_context; globale Fachkatalog-/Originalverwaltung bleibt unverändert.
- Bestehender Admin-Purge-Endpunkt verwendet: Dialog benennt Baustelle und betroffene Inhalte, verlangt ENDGUELTIG LOESCHEN; Server prüft Bestätigung plus Ziel-ID und Admin-CSRF. Keine reale Löschung ausgeführt. Kein neuer produktiver Restore oder Lösch-E2E behauptet.
- Lokale Prüfungen:132 elf Tests PASS,133 fünf Tests PASS (API-Sperre, Listen-Sperre ohne Abfrage, alte Seitenumleitung, Purge-Ziel/Bestätigung, UI-Vertrag). Produktionsfunktionen AST-isoliert mit Doubles. Python-Syntax und Template-JavaScript Node-Syntax PASS. Kein Live-HTTP/PostgreSQL/NAS-/Browser-E2E; vollständige Endpunktmatrix bleibt separater Auditpunkt.
- Runtime-Dateien:server/tbserver/admin.py,admin_web.py,permissions.py,worksites.py sowie server/templates/v1_company_branding.html,v1_admin_companies.html. Über GitHub Desktop/File Station in bestehende Ordner nach /volume1/docker/tb-helfer-v1 übernehmen, nur API neu starten und Strg+F5. Keine Migration/Androidänderung. Praxis: Firmen-Baustellenliste ohne Anlegen/Öffnen, alter Link→Firmen, Portalzugriff als berechtigter Benutzer weiterhin vorhanden; Löschdialog abbrechen bewahrt Daten. Reale Löschabnahme nur an ausdrücklich entbehrlicher Testbaustelle.


## RIGHTS-003 – Benutzer-/Sessionsperren prüfen (05.10.2026)
- Nutzer bestätigt RIGHTS-002 nach Übernahme mit passt (17:56): reduzierte Adminansicht/Portalzugriff; reale Komplettlöschung nicht bestätigt.
- Auftrag18:29: bestehende Portal-/Gerätezugänge bei Sperre/Ablauf überprüfen; keine echten Nutzer sperren, kein Livezugriff vorhanden.
- Quellbefund vor weiterem Test: normale Portal-/Admin-/Geräteauth lädt Benutzerstatus/Gültigkeit je Anfrage aus DB und verweigert disabled/deleted/expired. sync.live_events prüft nur beim Start und verwendet anschließend unbegrenzt _changes_for_worksite ohne erneute Benutzer-/Session-/Mitgliedschaftsprüfung. wait_changes verwendet nach bis25s Wartezeit den zuvor geladenen Benutzer. Gezielte lokale Nachweise folgen; noch kein Runtimefix.


## RIGHTS-003 – Fix freigegeben (05.10.2026 18:41)
- Benutzerauftrag dann bauen. SSE und Longpoll authentifizieren nach dem Warten und vor Ausgabe erneut über bestehende Portal-/Geräteauth, einschließlich aktueller Baustellenrechte. SSE endet bei Auth-/Zugriffsfehler ohne weitere Fachdaten; Longpoll liefert normalen Fehler. Keine Clientvertrags-/Androidänderung; bestehende Hub-Wartefunktion erhalten. Lokale Regressionen statt echter Benutzersperren.


## RIGHTS-003 – Live-Sync-Sperren implementiert (05.10.2026)
- sync.py ergänzt Request-Injektion und _refresh_sync_user: bestehende aktuelle Portal-/Admin-/Geräteauth plus aktuelle Baustellenprüfung. Longpoll nach Hub-Warten auch bei leerem Timeout neu prüfen, bei Daten zusätzlich nach Abfrage. Authfehler als normale HTTP-Fehler.
- SSE prüft nach jedem Hub-Warten (15s Timeout) erneut und verwendet _visible_changes mit frischem Benutzer, anschließend erneute Auth-/Baustellenprüfung vor Ausgabe. 401/403/404 beendet Generator ohne weitere Meldung. Bounded200-Eventpaket wird zusammen ausgegeben, keine ungeprüften Pausen zwischen Einzelereignissen. Hub unverändert; zusätzliche Auth-/Berechtigungsabfragen sind bewusst notwendig. Kein Sofort-Push-Abbruch garantiert: ruhende Verbindung wird beim nächsten Check nach Warteende geschlossen; bereits versandte/gepufferte Daten werden nicht zurückgerufen. Keine atomare Garantie gegen Sperre unmittelbar nach letzter DB-Prüfung.
- Tests135 sieben Gruppen PASS: keine Daten/Keepalives bei Sperre,401/403/404, Sperre während Abfrage, gültiges Event dann Sperre, leerer Longpoll, Longpoll-Abfragesperre, gültige Cursor/Antworten, produktiver Refreshhelper mit Auth-/Baustellenprüfung. AST-isolierte Produktionsfunktionen mit Doubles.134 zwei Datums-/Authgruppen PASS;132 elf Rechte-/Authgruppen PASS. Python-Syntax PASS.134 früherer Defekt-Reproduktionstest durch135 negative Regression ersetzt. Kein Live-HTTP/PostgreSQL/NAS-/Geräte-/Lasttest.
- Nur Runtime server/tbserver/sync.py übernehmen nach /volume1/docker/tb-helfer-v1/tbserver/sync.py, API neu starten. Keine Migration, Android unverändert. NAS-Abnahme noch offen. Kein echter Benutzer gesperrt, keine Daten gelöscht.


## OPS-MONITOR-002 – bestätigte öffentliche Fehler vor Warnmail (06.10.2026)
- Benutzerfreigabe17:56: öffentliche Warnmail erst nach drei aufeinanderfolgenden ERROR-Prüfungen; Einzelereignisse weiter sichtbar, Mail mit internem API-/DB-Zustand. Andere Alarmarten unverändert.
- Logs:05.10.21:54:40 lokal Timeout,5,6s später OK116,3ms;06.10.15:13:53 Timeout,15,5s später OK124,5ms. Keine gleichzeitigen internen/DB-Fehlerübergänge.06.10.17:46:54 interner Verbindungsfehler/öffentlicher502 passt API-Neustart. Öffentliche Netzwerkursache nicht bestimmt. Diese Änderung verbessert Alarme, nicht den Netzwerkpfad.
- Zähler im vorhandenen Monitorzustand, Reset bei Nicht-ERROR; Erholungsmail erst für bestätigte Störung. Keine echten E-Mails zu Testzwecken senden. Anschließend Server/Portalplan: Backupkonsistenz, verbleibende Rechte-/Lastabnahme, Portal-BTB/Bilder; Android danach.


## OPS-MONITOR-002 – umgesetzt/lokal geprüft (06.10.2026)
- Öffentlicher Check bleibt bei Einzel-ERROR rot/protokolliert; Warnmail erst beim dritten aufeinanderfolgenden ERROR. Nicht-ERROR setzt Zähler zurück. consecutive_errors und incident_confirmed im bestehenden Monitor-Snapshot gespeichert; Neustart kann vorhandenen Zähler übernehmen. Erholungsmail nur nach bestätigter Störung; WARN zwischen bestätigter Störung und OK erhält Incident. Vorhandene Mailfreigabe/Empfänger/Cooldown bleiben maßgeblich; keine Testmail gesendet. Andere Alarmarten unverändert.
- Öffentliche Problem-/Erholungsmail enthält aktuellen internen API-/DB-Status, Latenz und Detail. Netzwerkursache weiterhin offen; keine Heilung des öffentlichen Zugangs behauptet.
- Test136 sechs Gruppen PASS: kurze Störung ohne Mail, dritte Fehlmessung/einmalige Erholung, Reset durch WARN, persistierter/Legacy-Zähler, neuer Incident. Test130 acht Regressionen PASS; Python-Syntax PASS. Produktionsfunktionen mit Doubles, kein SMTP-/NAS-/Live-E2E. Runtime nur server/tbserver/monitor.py. Nach File-Station-Übernahme /volume1/docker/tb-helfer-v1/tbserver/monitor.py Monitor neu starten; für konsistent geladenen Stand auch API/Worker (importieren Monitor). DB nicht neu starten, keine Migration.
- Nächster Plan:1 Vollbackup-Konsistenz DB+Dateien und vollständige Dateireferenzprüfung;2 verbleibende Rechte-Endpunktmatrix und NAS-Abnahme Live-Sperre;3 Portal-BTB/Unterbaustellen/Einträge+Fotos umhängen/Wetter und tatsächliche Bildgrößen;4 Lastabnahme15x15;5 Androidanbindung gegen fertigen Serververtrag. Bestätigte Gleis-/Weichen-/Adminabläufe bleiben geschützt. Aktuelle Änderung noch nicht auf NAS abgenommen.


## SERVER-GATE-001 – Punkte1+2+4 freigegeben (06.10.2026 18:12)
- Benutzer beauftragt Backup-Konsistenz, verbleibende Rechteprüfung und Lasttest15x15 bauen und testen. Portal-UI/Android bleiben außerhalb dieses Blocks.
- Aktuellen Backup-/Dateischreib-/Rechte-/Syncpfad prüfen; konkrete Befunde und gewählten Konsistenzvertrag vor Laufzeitänderungen sichern. Isolierte Tests bevorzugt; echte NAS-/255-Clientlastabnahme nicht ohne passende Testumgebung behaupten. Keine produktiven Daten/Benutzer löschen oder sperren.


## SERVER-GATE-001 – Konsistenzvertrag vor Umsetzung (06.10.2026)
- Vollbackup: exportierter PostgreSQL-REPEATABLE-READ-Snapshot für pg_dump und Dateiinventar. Inventar umfasst aktive tb.files, alle survey_originals und Releases mit storage_key; identische Doppelreferenzen zusammenführen, widersprüchliche Größe/SHA verweigern. Nur bestätigte Dateiinhalte sichern, keine Cache-/Uploadreste. Fehlende, ersetzte oder beschädigte Referenzdatei führt zu FAILED, niemals OK. Parallele Schreibzugriffe bleiben möglich; Snapshotdateien müssen während Sicherung noch lesbar sein.
- Neuer Manifestzusatz kennzeichnet snapshot-reference-v1 und Uploadpolitik restart. Nicht abgeschlossene Uploads sind kein gesicherter Fachbestand; im wiederhergestellten Ziel werden OPEN-Sessions auf EXPIRED gesetzt. Live-Sessions unverändert. Bestehendes äußeres v1-Archivformat bleibt kompatibel. FULL-Prüfung und Zielrestore gleichen wiederhergestellte DB-Dateireferenzen gegen entpackte Dateien ab.
- Rechteaudit: Baustellen-Grenze, Funktionsrechte, Bereiche, Teams, Datei-/Uploadzugriff und laufende Syncs getrennt testen. Konkreter Befund: Datei-Detailpfad und Liste prüfen bisher nicht files.view; Uploadsession erlaubt jedem Baustellenmitglied mit files.add Änderung fremder laufender Uploads. Beides gezielt eingrenzen (Session nur Ersteller; lesende Dateioperationen verlangen files.view); GLOBAL-Fachkatalog bleibt offen gemäß Vertrag. Feed transportiert lediglich Änderungskennungen, Fachinhalte bleiben über geschützte Detailendpunkte zugänglich.
- Lastwerkzeug: 255 unabhängige Clients (15 TB +225 Helfer +15 Portal), mehrere Runden, Vertrags-/Cursorfehler zählen als Fehler, Sessionbereinigung auch bei Teilfehler. Ziel ausdrücklich als isolierte Testinstanz bestätigen; keine Live-NAS-Last aus dieser Umgebung. Lokale 255-Warte-/Semaphore-Tests prüfen echte Sync-Koordination mit DB-/Auth-Doubles; dies ersetzt keine NAS-Messung.
- Umgebung: kein PostgreSQL/pg_dump/FastAPI vorhanden, Paketinstallation nicht verfügbar. Reale PostgreSQL-/HTTP-/NAS-Gates müssen getrennt ausgewiesen werden.


### SERVER-GATE-001 – zusätzlicher Rechtebefund vor Fix
- Portal-/Geräteauth prüfen Benutzer- und Gerätesperre, bisher jedoch nicht den Status der zugehörigen Firma. Firmenstatus DISABLED bzw. deleted_at muss neue Anmeldung/Aktivierung und vorhandene Firmen-Sessions/Gerätetokens sperren. Firmenstatus per JOIN in bestehenden Authabfragen laden; zentral fail-closed prüfen. Systemadmin ohne Firmenbindung bleibt für Systemverwaltung verfügbar. Laufende Syncs übernehmen Prüfung durch vorhandene Reauthentifizierung. Keine reale Firmensperre zum Test.


### SERVER-GATE-001 – zusätzliche echte CI-Abnahme
- GitHub-Workflow TB-Server 1.0 besitzt bereits eine isolierte PostgreSQL/PostGIS-17-Testumgebung. Ergänzter unabhängiger Gate-Job soll das bestehende Server-Dockerimage bauen und neue Checks plus echten Snapshot-Dump/Restore und HTTP-Rechte-/255-Client-Syncprüfungen mit ausschließlich erzeugten Testdaten ausführen. Veraltete feste Migrationsprüfung19 auf vorhandenen Stand21 anpassen. Kein NAS-Deployment/Produktivlasttest. Ausführung und Erfolg erst anhand Workflow-Ergebnis bestätigen.



## SERVER-GATE-001 – Punkte 1+2+4 implementiert und CI-geprüft (06.10.2026)
- Runtimecommit 6d9fc82b6a9dc5fb0b8da55f5f2a37ebdd268ba9 auf feature/tb-server-1.0. PR6 bleibt Draft. Android 423f37892915cb3308a4b1ea9e4d196faa872326 unverändert; keine app/**-Änderung.
- Vollbackup: gemeinsamer exportierter REPEATABLE-READ-Snapshot für pg_dump und Inventar (aktive Dateien, Originale, Release-Binaries). Geprüfte temporäre Dateikopie verhindert Austausch zwischen Hashprüfung und Archivierung. Fehlende/abweichende Referenzdateien führen zu Fehler statt OK. FULL-Test und Zielrestore prüfen DB-Dateireferenzen. Neues Manifest kennzeichnet snapshot-reference-v1 und Upload-Neustart; offene Uploads nur im Restoreziel auf EXPIRED, Livezugänge unverändert. Alte Archive bleiben lesbar; getrennte DB-/Dateibackups haben keine gemeinsame Konsistenzgarantie.
- Rechte: Firmenstatus per JOIN in Portal-/Geräteauth/Login/Aktivierung, 403 COMPANY_DISABLED bei gesperrter/gelöschter/fehlender Firma. Systemadmin bleibt systemseitig nutzbar. Datei-Liste/Medien/Detail/Download/Vorschau verlangen files.view. Uploadsession gehört ausschließlich dem Ersteller; Schreiben benötigt weiterhin files.add. Gemeinsamer GLOBAL-Katalog unverändert.
- 78 lokale Tests (130–139) PASS, Python-Syntax PASS. Echte CI: Serverimage gebaut, Migrationen angewendet, PostgreSQL17 Snapshot/Dump/Restore samt parallelem Commit und negativen Dateiprüfungen PASS; echte FastAPI/PostGIS-Rechteprüfungen PASS. Vier laufende Longpoll-Sperrfälle (Nutzer/Firma/Mitgliedschaft/Gerät) und SSE nach Mitgliedschaftsentzug PASS.
- CI-Job https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37497843193/job/112386875101 SUCCESS. 3x255 echte HTTP-Syncclients (15 TB,225 Helfer,15 Portal), 0 Fehler; Median5,03–5,10s, p95 7,11–7,21s, max7,35s einschließlich2s Wartezeit. 15/15 Portal-Testsitzungen abgemeldet. CI-Kriterium10s Overhead bestanden; strengeres5s-Overhead-Beispiel nicht bestanden (bis5,21s). Kein NAS-/Cloudflare-/Foto-/BTB-Schreiblastnachweis.
- Gesamt-CI weiterhin rot durch ältere Prüfungen: foundation Check23 erwartet FILE_LINK_TARGET_NOT_FOUND, Runtime verwendet bereits in Baseline4cc7fef FILE_LINK_TARGET_NOT_VISIBLE. Android-CI scheitert an bestehendem Schema10-Quellmarker BtbVerlaufFragment.kt:MergeSafeBaustellenExport.normalisieren. Nicht abgeschaltet, keine pauschale Gesamtfreigabe.
- Übernahme8 Runtime-Dateien nach /volume1/docker/tb-helfer-v1/tbserver/: backup_inventory.py NEU, backup.py, restore.py, restore_target.py, permissions.py, portal.py, devices.py, files.py. API und Worker neu starten; keine Migration/DB-Neustart. Neuer Monitorfix aus vorigem Block separat beachten, falls noch nicht übernommen. Daten/.env/Storage/Backups behalten.
- Nächste echte NAS-Abnahme: neues Vollbackup und Backup prüfen; eigene/fremde Firmen-/Dateizugriffe und Uploads; Firmensperre/Livesync nur mit Testkonten; 255-Clientmessung auf isolierter NAS-Testinstanz. Portal-BTB/Bilder bleibt Punkt3, Android danach. Technische Details und ausführbare Befehle: server/checks/SERVER_GATE_001.md; Testtools backup_snapshot_gate.py, server_http_gate.py (nur CI/leer tb_gate_http), sync_mixed_loadtest.py.


## OPS-RESTORE-003 – echte Wiederherstellung freigegeben (06.10.2026)
- Benutzer bestätigt neues NAS-Vollbackup851MB und Backup-Prüfung OK (19:55). Nur isolierte Backupprüfung abgenommen, kein Produktiv-Cutover.
- Auftrag: sinnvolle bedienbare Serverwiederherstellung bauen. Kein realer Restore/keine Live-Umschaltung in diesem Entwicklungsblock.
- Ansatz: Admin-Assistent wählt erfolgreiches FULL-Backup, zeigt Datum/Identität und Verlust neuerer Änderungen. Vorbereitung im Worker: frisches Sicherheitsvollbackup, Restore ausgewählter Quelle in getrenntes frisches Ziel, Schema-/Dateireferenzprüfung. Alte Live-DB/Storage bleiben erhalten. Kein Docker-Socket in API und kein automatisches Überschreiben des Livebestands.
- Expliziter bestätigter Cutover über Synology Container Manager mit bereitgestellter Compose-Konfiguration: API/Worker/Monitor/Migration gemeinsam auf Ziel-DB und Zielspeicher. Alle Dienste während Umschaltung stoppen. Bestehende Konfiguration vorher aufbewahren. UI nennt Ziel lediglich bereit, niemals bereits aktiv. Rückweg auf alten Stand separat dokumentieren.
- Restoreziel sperrt alte Gerätezertifikate/Tokens und Browser-/Aktivierungssessions sowie wartende Jobs; verhindert unbemerkten Replay alter Clientcursor/Outbox und Wiederbelebung alter Berechtigungen. Nutzer nach Prüfung neu aktivieren; Offlineänderungen vorher sichern. Android unverändert.
- Fehlende Zielkonfiguration, belegtes Ziel, parallele Restorevorbereitung und Fehler dürfen nicht als Erfolg erscheinen. Bestätigungen an Backup-ID binden, Admin+CSRF. Vorbereitungs-/Ziel-/Backupnachweis anzeigen. Exakte technische Grenzen und Testnachweise folgen im Abschluss.

### OPS-RESTORE-003 – Schutz der getrennten Ziele
- Quellsichtung: vorhandener CLI-Zielrestore hat noch keinen gemeinsamen Ziel-Lock mit der neuen Vorbereitung. Außerdem schließen bisherige Konfigurationsprüfungen nur Live-DB aus, nicht eine versehentliche Gleichsetzung von Prüfdatenbank und Wiederherstellungsziel. Vor Umsetzung festgehalten: Zielrestore und Vorbereitung teilen PostgreSQL-Advisory-Lock; Test-/Zielkonfigurationen müssen unterschiedliche DB-Namen haben. Bestätigter regulärer Backup-Prüfablauf bleibt unverändert.


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


## OPS-RESTORE-004 – freigegeben vor Umsetzung (06.10.2026 22:02)
- Nutzerauftrag nach Besprechung: normale Update-Wiederherstellung standardmäßig mit Firmen, Benutzerstatus/Rechten und aktiven Gerätebindungen aus Backup; separate Variante SECURITY für Sicherheitsvorfall mit bisherigen Zugangssperren. Portal-Sessions in beiden Varianten widerrufen. Offene Uploads/Jobs nicht wieder abspielen. UPDATE bewahrt Automatikpolicy, SECURITY deaktiviert sie.
- POST /admin/system/recovery/prepare erhält mode UPDATE|SECURITY (neue Anfragen Default UPDATE); bestehende gespeicherte Zustände ohne mode bleiben SECURITY. Bereits READY-Ziele werden nicht verändert. Frisches Ziel, Sicherheitsbackup, manuelle Umschaltung und DB/Storage-Paarprüfung bleiben.
- Neue FULL-Manifeste erhalten nicht geheime Runtime-/Schema-Metadaten (Version, deterministischer Quellfingerprint, Migrationschecksummen). UPDATE verlangt passende Metadaten/Runtime und Schema; alte Archive ohne Metadaten bleiben für Backup-Prüfung/SECURITY nutzbar. Passendes Serverpaket/Containerimage muss separat vor Update erhalten bleiben; kein automatischer Programmdowngrade.
- Wiederherstellungskennung wird nach tatsächlichem Cutover über capabilities bereitgestellt. Geräte mit erhaltenem Token benötigen passende X-TB-Recovery-ID; fehlende/veraltete Kennung ergibt 409 SERVER_RECOVERY_REBASE_REQUIRED vor Fachzugriff. Kennung ist keine Berechtigung; normale Auth/Rechte bleiben. Android muss lokale Outbox sichern, Serverbestand neu laden und Konflikte abgleichen, bevor es normale Mutationen freigibt. Android hier unverändert, Geräte-E2E offen.
- NAS-Screenshot 21:49 bestätigt OPS-RESTORE-003 READY nach Anlegen fehlender Zielordner, ausdrücklich keine Umschaltung. Anleitung ergänzt Anlage beider leerer Zielordner. Bestehendes Ziel bleibt SECURITY und belegt; keine automatische Löschung/Neupräparation.
- Umfang Server/UI/Vertrag/Tests; keine echte NAS-Wiederherstellung oder E-Mail. Testziel beide Modi, Altzustand, Metadatenfehler, Gerätekennung und bestehende Ziel-/Zugangsschutztests.


## OPS-RESTORE-004 – Servervarianten implementiert (06.10.2026)
- Nutzerfreigabe22:02 nach Besprechung; Vorabvertrag775cc10. Runtime2fb6b8da607761e42610f0513c51e706fbd79d5e, Testergänzung2fad3251214291c91af3a62935a549596270f1cc. Androidbranch423f37892915cb3308a4b1ea9e4d196faa872326 unverändert, kein app/**.
- Neue Vorbereitungen: UPDATE Standard mit Benutzerstatus/Rechten/Ablauf, Firmen und Gerätebindungen/Tokens sowie Automatikpolicy aus Backup; SECURITY erhält bisherige Zugangssperren. Beide Modi widerrufen Portal-Sessions und verwerfen offene Jobs/Uploads. Zustände ohne mode bleiben SECURITY; vorhandenes NAS-READY-Ziel wird nicht geändert oder gelöscht.
- FULL-Manifeste enthalten nicht geheime Runtime-/Schemaidentität. UPDATE verlangt exakten Programmfingerabdruck und restaurierte Migrationschecksummen. Alte Backups ohne Metadaten für UPDATE abgewiesen, Backup-Prüfung/SECURITY weiterhin möglich mit passendem Schema. Serverpaket/Image muss separat gesichert werden; kein automatischer Code-/Schemadowngrade. Programmstand während Vorbereitung/Umschaltung nicht austauschen; spätere normale Updates nicht dauerhaft blockiert.
- capabilities liefert recovery_epoch_v1 + recovery_id (null vor tatsächlichem Cutover). Geräteauth prüft nach normalen Token-/Nutzer-/Firmenrechten X-TB-Recovery-ID; fehlend/falsch→409 SERVER_RECOVERY_REBASE_REQUIRED, passende Kennung erlaubt normalen Rechtepfad. Keine Umgehung durch öffentliche UUID; sie ersetzt keine Auth. Android muss Outbox schützen, Cursor verwerfen, Serverstand getrennt laden und Konflikte abgleichen, bevor normale Mutationen wieder starten. Aktuell unangepasste App bleibt nach Cutover bei Fachzugriff blockiert; daher noch kein fertiger Geräte-Rollback-E2E.
- NAS-Bestätigung Screenshot21:49: vorheriger OPS-RESTORE-003-Vorbereitungslauf READY; keine tatsächliche Umschaltung. Fehlende Bind-Mount-Ordner zuvor nachgewiesen; Anleitung/UI nennt jetzt vor Erststart beide leeren Ordner. Vorhandenes Ziel belegt; neuer Versuch braucht frisches getrenntes Ziel, kein blindes Löschen.
- Übernahme8 Runtime-Dateien aus server/: tbserver/recovery_version.py NEU, recovery.py, recovery_guard.py, system_admin.py, backup.py, devices.py, capabilities.py, templates/v1_system_status.html. API+Worker neu starten; StrgF5, Migration weiterhin21. Noch keine NAS-Übernahme dieser Änderung behauptet. Ablauf/Grenzen/Androidvertrag: server/checks/OPS_RESTORE_004.md.

- Nachweis:89 Verhaltenstests130–141 PASS; Python/JavaScript/YAML-Syntax PASS. Echter PostgreSQL17/FastAPI-Gate zweimal SUCCESS; letzter Run37524540497 Job112478085754: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37524540497/job/112478085754 . UPDATE und SECURITY jeweils echte Sicherung/Wiederherstellung/API-Neustart mit Portal-/Geräteprüfungen PASS; zusätzlicher Altzustand ohne mode bleibt SECURITY. Quellbestand erhalten, Zielfehler/Lock/Retry-Guards unverändert geprüft. 3x255Clients0Fehler,p95 6,13–6,16s inkl2sWait; keine NAS-/Android-/Foto-/BTB-Schreiblastabnahme.
- Gesamt-CI weiterhin nicht grün: foundation alter FILE_LINK_TARGET_NOT_FOUND-Marker, Android-CI alter Sourcevertrag. Nicht umgangen/abgeschaltet. Weitere Server-/Portal-Arbeit und Android-Recoveryintegration bleiben offen.


## OPS-RESTORE-005 – verbindlicher Folgeauftrag und Bestandsabgleich (07.10.2026)
Status: ANFORDERUNG GESICHERT / IMPLEMENTIERUNG OFFEN.
Benutzer hat den letzten Auftrag nach Chatlängenabbruch erneut übergeben und verlangt zuerst Abgleich mit GitHub statt Neubeginn.
Geprüfte Basis: SERVER af983f0e7fcc5ef79aec926ddbe837e0c24518be, PR #6 offen/Draft; ANDROID 423f37892915cb3308a4b1ea9e4d196faa872326, PR #11 offen, kein Draft.
Vorhandene Grundlage OPS-RESTORE-003/004 weiterverwenden: Sicherheitsvollbackup, getrenntes Restoreziel, Schema-/Dateireferenzprüfung, UPDATE/SECURITY und Recovery-Kennung. Code recovery.py erlaubt bisher nur FULL und endet bei READY; Umschaltung laut Template/Compose manuell. backup.py kennt DATABASE/FILES/FULL, bisherige Retention nach Tagen/Mindestanzahl. Vier neue fachliche Arten und automatische Umschaltung sind in diesem geprüften Stand noch nicht umgesetzt.

Verbindliches Ziel (ersetzt die normale manuelle Umschaltung aus OPS-RESTORE-003/004):
- Webseite: Backup einspielen → Nutzerbackup, Streckenbackup oder Vollbackup → eines der letzten vier erfolgreichen Backups → Datum und betroffenen Bereich prüfen/bestätigen → Einspielen.
- Nutzerbackup stellt Firmen-/Benutzer-/Baustellenbereich wieder her; gemeinsamer Streckenbestand bleibt erhalten. Streckenbackup stellt gemeinsamen Streckenbestand wieder her; Nutzerbereich bleibt erhalten. Vollbackup umfasst beide Datenbereiche. Exakte Tabellen-/Dateigrenzen und Querverweise vor Implementierung anhand Bestand bestimmen und prüfen.
- Prüfung, zusätzliche Sicherung des aktuellen Bestands, erforderliche Wartungspause, Wiederherstellung und tatsächliche Umschaltung automatisch. Keine Compose-Umbenennung und keine einzelnen Containerstopps durch den Nutzer im normalen Ablauf. Bei Fehler bisherigen Bestand erhalten bzw. wieder aktivieren; Wiederanlauf/Unterbrechung ausdrücklich testen.
- Begrenzten Wiederherstellungsdienst auf Basis der vorhandenen Containerkonfiguration entwerfen. Kein bloßes READY als erfolgreiche Wiederherstellung ausgeben.
- Image-/Systembackup: Erstellung und Zeitplan auf Webseite; Wiederherstellung ausschließlich über NAS/Container Manager. Kein Einspielbereich und keine Systemrestore-Anleitung in der normalen Webseite.
- Vier unabhängige Backuparten, standardmäßig jeweils letzte vier erfolgreiche Sicherungen. Erst nach erfolgreicher neuer Sicherung älteste derselben Art entfernen. Fehlgeschlagene Sicherung darf keine erfolgreiche verdrängen.
- Systembackup und Vollbackup strikt unabhängig: Systembackup erzeugt kein automatisches Vollbackup und hält kein zusätzliches Vollbackup fest. Software-/Schemainformationen im Vollbackup weiterhin zur Kompatibilitätsprüfung; keine Kopplung der Aufbewahrung.
- Sicherheitskopie eines laufenden Restorevorgangs gesondert gegen vorzeitiges Löschen schützen; Lebenszyklus beim Entwurf ausdrücklich festlegen, nicht als System-/Vollbackup-Kopplung verwenden.
- Langen technischen Rückwegtext in Notfallanleitung verschieben. Alte Restore-Bereiche erst nach Umsetzung und Tests der drei Datenarten einschließlich absichtlich fehlschlagender Wiederherstellungen aufräumen.
- Abschließend konkrete NAS-Dateiliste: neu hochladen/ersetzen, Zielpfade und tatsächlich entbehrliche Altdateien; keine Daten-/Backup-/Zielordner auf Verdacht löschen.
- Android app/** unverändert lassen. Bestehende offene Recovery-ID-/Outbox-Rebase-Anpassung bleibt gesonderte Android-Abhängigkeit; automatische Serverumschaltung ist kein Android-E2E-Nachweis.

Dieser Block sichert Anforderungen und Prüfergebnis, ändert keinen Runtimecode und bestätigt kein NAS-Deployment. Vor Fortsetzung GitHub erneut prüfen; anschließend Container-/Datenbereichsanalyse, Implementierung, reale isolierte Positiv-/Fehler-/Wiederholungsprüfungen, Statussicherung und NAS-Übernahmeliste. NAS-Abnahme getrennt offen halten.


### OPS-RESTORE-005 – Daten- und Betriebsgrenzen vor Implementierung
- Gemeinsame catalog_* / survey_network_* und GLOBAL-Originale/-Kataloge gehören zum Streckenbereich; worksite track_data einschließlich Quellen-/KM-Zuordnung sowie COMPANY-Originale/-Kataloge zum Nutzerbereich. Quellenverweise über die Grenze müssen im kombinierten Ziel weiter auflösbar sein; andernfalls Abbruch ohne Liveänderung. Nullable historische Autorzuordnungen benötigen explizite Behandlung, fachliche Fremdschlüssel werden niemals still entfernt.
- Vollbackup enthält vollständigen konsistenten Datenbank- und Dateistand. Systembackup enthält ausführbare Images und gebundene Software/Konfiguration, keine Fachdaten und keine automatische Vollbackup-Kopplung. Wiederaufbau benötigt Systembackup plus separat gewählten kompatiblen Datenstand.
- Entwicklung/Tests erfolgen isoliert; keine aktuelle NAS-Compose oder .env als hochgeladenes Original vorhanden. Abschließende Übernahme muss individuelle Pfade/Ports beibehalten; alte DB-/Storage-Verzeichnisse erst nach Nachweis ihrer Nichtverwendung entfernen.


### OPS-RESTORE-005 – Implementierungsfolge nach erneutem Weiterauftrag (07.10.2026)
- Benutzerauftrag weiter: Umsetzung auf Repository-Konfiguration fortsetzen; tatsächliche NAS-Compose erst für installationsgenauen Abgleich erforderlich. Die fehlende NAS-Datei blockiert isolierte Entwicklung nicht.
- Wiederherstellungssteuerung außerhalb des pausierten Workers. Persistentes Journal und exklusiver Prozesslock; Wartung vor Sicherheitskopie, danach frisches Ziel, Prüfung, Umschaltung bei weiterhin gesperrten Fachschreibern, Ziel-API-Prüfung, dauerhafte Erfolgsentscheidung, erst anschließend Schreibfreigabe.
- Vor dauerhafter Erfolgsentscheidung bei Fehler/Prozessabbruch bisherigen Stand unter Wartung wieder auswählen, prüfen und freigeben. Scheitert Rückweg, Wartung halten und klaren Eingriffsbedarf melden. Nach dauerhaftem Commit niemals automatisch auf alten Stand zurückwechseln, da neue Schreibzugriffe erfolgt sein könnten.
- Teilbackup-Zuordnung mit vollständiger expliziter Tabellenliste; unbekannte Tabellen oder nicht eindeutig zuordenbare Zeilen blockieren statt still Daten auszulassen. Fachliche Querverweise einschließlich JSON-Referenzen zusätzlich zur PostgreSQL-Prüfung berücksichtigen.
- Neue Bausteine zunächst ohne Aufruf aus produktiven API-/Workerpfaden entwickeln und isoliert testen. Das ist ein Implementierungsschritt, keine Freigabe des Gesamtumbaus; erst vollständige Integration samt Tests ersetzt die bisherigen Restore-Bereiche.


### OPS-RESTORE-005 – Archiv-/Zielimportvertrag vor Implementierung (07.10.2026, Fortsetzung)
- Nächster Baustein: echte getrennte USER-/TRACK-Archive mit ausgewählten Zeilen, passenden referenzierten Dateien, Prüfsummen, Software-/Schemainformationen und minimalen Metadaten externer Originalabhängigkeiten. Keine fremden Firmen-/Benutzer-/Credentialzeilen im Streckenarchiv.
- Teilrestore ausschließlich in nachweislich frische separate Datenbank und leeren getrennten Dateispeicher. Aktueller Gegenbereich und Betriebsdaten stammen aus dem aktuellen Snapshot; ausgewählter Bereich aus dem Archiv. Quelle wird nicht beschrieben.
- Schema aus der laufenden, kompatiblen Quelle; Import vor Post-Data-Constraints, danach echte PostgreSQL-Prüfung aller Schlüssel/Constraints. Sequenzstände mindestens auf vorhandene Zeilenmaxima und mitgeführte Zählerstände setzen. Programm-/Migrationsabweichungen blockieren.
- Unaufgelöste Autoren-/Fachverweise und kollidierende Historien-IDs bleiben vorerst explizite Abbruchfälle; keine stillen Umnummerierungen, Löschungen oder Nullsetzungen. Fehlerziel bleibt getrennt zur Diagnose; kein Live-Cutover aus diesem Baustein.
- Archiv-/Importpfad zuerst isoliert gegen PostgreSQL und echte Dateien prüfen. API-, Zeitplan-, Retention-, Systemimage- und Containerintegration folgen innerhalb desselben Gesamtauftrags; bisherigen produktiven Bedienpfad erst nach vollständiger Integration ersetzen.


### OPS-RESTORE-005 – Betriebsadapter-Vertrag vor Implementierung (07.10.2026)
- Automatische Datenumschaltung über einen eigenen Controller und überwachte API-/Worker-/Monitor-Prozesse innerhalb ihrer Container. Dafür keine Docker-Schreib-API und keine manuellen Compose-Wechsel im normalen Restoreablauf. Einmalige Container-Manager-Übernahme des neuen Betriebsaufbaus bleibt erforderlich.
- Controller schreibt eine persistente atomare Auswahl mit Generation und STOPPED/PROBE/OPEN. Datenbankname, Storagepfad und Recovery-ID werden gemeinsam aus der validierten Generation abgeleitet; keine frei übergebenen Befehle/Pfade/DB-Ziele. API/Worker lesen Steuerung nur read-only; Statusbestätigungen getrennt je Rolle. Fehlende/ungültige Steuerung sperrt statt auf initialen Bestand zurückzufallen.
- Wartung stoppt alle bekannten Schreiber einschließlich Prozessgruppen, wartet auf Bestätigung derselben Steuerungsrevision und auf beendete DB-Sitzungen. Zielprüfung startet nur API unter gesperrtem Fachzugriff; Worker/Monitor bleiben aus. Freigabe erst nach durablem Controllercommit. Neustarts laden die persistente Auswahl; alte Daten bleiben getrennt erhalten.
- Neue Datenbankgenerationen im eigenen PostgreSQL-Datenbanknamen und in eigenem Storage-Unterverzeichnis. Das ist logische Trennung im bestehenden PostgreSQL-Cluster, kein Schutz vor Ausfall des NAS/Clusters. Controller darf ausschließlich eigene neu angelegte Zielnamen verwenden, keine bestehende Datenbank löschen. Systemimage-Export bleibt ein separater lesender Docker/NAS-Aspekt, keine Kopplung an FULL.
- Integration schrittweise mit echten Supervisor-Unterprozessen und isolierter PostgreSQL/API-Prüfung; kein NAS-/Android-Nachweis aus diesen Tests ableiten.


### OPS-RESTORE-005 – Fehler im echten Wiederholungstest (07.10.2026)
- CI a29a72f: USER automatisch erfolgreich mit echter API/Worker/Monitor. Anschließendes TRACK wird korrekt ROLLED_BACK; PostgreSQL-Log meldet SET TRANSACTION ISOLATION LEVEL must be called before any query. Ursache: _capture_full_database öffnet connect() mit bereits transaktionsstartender Recovery-ID-Prüfung, bevor es REPEATABLE READ setzt. Sicherheitskopien auf bereits restauriertem Quellstand scheitern dadurch.
- Freigegebene Korrektur innerhalb Restoreauftrag: Guard auf autocommit-Verbindung prüfen, danach explizite eigene REPEATABLE READ/READ ONLY-Transaktion für Snapshot/pg_dump. Datenbank-/Storage-Paarprüfung bleibt erhalten. Gesamten automatischen Wiederholungslauf erneut ausführen. Fehlerdiagnose nur mit nicht geheimen Codes/SQLSTATE, keine SQL-Datensätze in API-Ausgaben.


### OPS-RESTORE-005 – Backupkatalog/Aufbewahrung vor Integration (07.10.2026)
- Die erfolgreiche Archivliste wird zusätzlich im Backupverzeichnis dauerhaft geführt, unabhängig vom wiederhergestellten Datenbankstand. Sonst würde ein FULL-Restore neuere erfolgreiche Backups aus seiner eigenen Auswahl verlieren. DB-Laufprotokolle bleiben als Betriebsprotokoll erhalten; Archivexistenz/Hash und der unabhängige Katalog entscheiden über Einspielbarkeit.
- USER/TRACK/FULL/SYSTEM werden getrennt geführt; Standard vier erfolgreiche Archive pro Art. Neue Sicherung erst nach vollständiger Datei-/Hashprüfung veröffentlichen. Nur die erfolgreiche neue Sicherung derselben Art löst deren Aufräumen aus. Fehlgeschlagene Sicherungen löschen keine ältere Sicherung. Bestehende erfolgreiche FULL-Archive können übernommen werden, ohne dabei allein durch die Übernahme zu löschen; alte DATABASE/FILES bleiben Legacy und werden nicht still umbenannt.
- Löschungen nur registrierter, sicher unter dem Backupverzeichnis liegender Archivdateien; Wiederherstellungs-Operationsordner/.recovery und zusätzliche Sicherheitskopien werden nie durch normale Aufbewahrung erfasst. Bei Löschfehlern Status melden und Dateien behalten; keine Verknüpfung SYSTEM↔FULL.
- Bestehende Zeitpläne beim Übergang fachlich zu USER/TRACK/FULL übernehmen; SYSTEM separat und initial deaktiviert, bis bewusst eingerichtet. Keine alten Restorebereiche entfernen, bevor neuer Auftragseingang, Betriebskonfiguration und Gesamttests fertig sind.


### OPS-RESTORE-005 – Übernahme nach gestopptem Work-Lauf (09.10.2026)
- Nutzer bestätigt alten Lauf gestoppt und Fortsetzung nach Prüfung. Ausgangs-HEAD 8e4e337f1c2f8746e662212ed3db080dba5cb268, PR6 Draft; Android423f37892915cb3308a4b1ea9e4d196faa872326 unverändert.
- Tatsächliche Runner-Steps und Logs geprüft: Run37669109233/Job112955922326 SUCCESS, 38 Verhaltenstests142–145 sowie PostgreSQL17-Teilrestore und automatische USER/TRACK/FULL-Umschaltung mit echter API/Worker/Monitor. Fehler nach Zielprobe, Neustart und Controllerabbrüche vor/nach Freigabe mit Erhalt angenommener Änderungen PASS. Kein NAS-/Browser-/Android-E2E.
- Run37669109228 weiterhin FAILURE: Check137 test_snapshot_export_is_alive_during_dump scheitert im Testdouble an unerwartetem autocommit-Argument; neuer Produktionspfad connect(autocommit=True) im echten Restore-Gate bestanden. Testdouble an explizite Transaktionsgrenze anpassen und Verhalten prüfen; Guard nicht entfernen. Foundation scheitert weiterhin am FILE_LINK_TARGET_NOT_FOUND-Quellmarker. Android-Sourcevertrag MergeSafeBaustellenExport.normalisieren ebenfalls rot, app/** hier unverändert.
- Letzter Commit8e4e337f beschreibt nur unabhängigen Archivkatalog/Aufbewahrung, keine Implementierung. Fortsetzung: vorhandene Bausteine übernehmen, Testkorrektur, Katalog/Retention, unabhängiger Auftragseingang, Systembackup, Zeitplan/UI/Betriebskonfiguration und isolierte Integrationsprüfung. Bestehende bestätigte Fachpfade schützen. Keine NAS-Änderung oder Löschfreigabe.


### OPS-RESTORE-005 – weiterer isolierter Testadapterbefund (09.10.2026)
- Nach Check137-Fix erreicht Servergate den echten Snapshotlauf, scheitert dort mit TypeError. Quellsichtung backup_snapshot_gate.py: backup.connect = lambda: psycopg.connect(...) akzeptiert ebenfalls kein autocommit. Testadapter muss das explizite Argument weiterreichen; Produktionspfad bleibt unverändert. Snapshot-/Parallelcommit-Prüfungen bleiben vollständig bestehen.


### OPS-RESTORE-005 – begrenzte Betriebsintegration (09.10.2026)
- Separater Restoreprozess liest dauerhaft genau einen IDs-only-Auftrag aus geteiltem Eingang; API benötigt Admin+CSRF und an Backup-ID gebundene Bestätigung. Privates Journal/Runtime-Auswahl sind für API/Worker nur lesbar. Gleiche Request-ID nach Abbruch darf nach erfolgter Umschaltung niemals neu einspielen. Separater GET-Fortschritt liefert über zufällige Request-ID nur Phase/Fehler, keine Daten/IDs/Secrets; Gateway erhält Fortschrittsanzeige während API-Pause.
- Neue explizite Synology-Betriebskonfiguration, keine automatische Änderung bestehender NAS-Compose. Einmalige Initialisierung darf verlorene Auswahl niemals auf initial zurücksetzen. Migrationen müssen auf die ausgewählte Generation laufen.
- Systemarchive: begrenzter interner Exportdienst, nur Worker erreicht den Dienst über eigenes Docker-Netz. Nur dieser Dienst erhält Docker-Socket für fest implementierte GET-Image-Inspect/Save-Aufrufe; niemals API/Worker/Restorecontroller. Ein ro-Socketmount allein beschränkt keine Docker-API-Rechte; keine frei übergebenen URLs, Image-Namen oder Befehle. Feste konfigurierte Images werden nach ID exportiert, laufende Programme/bind-gemounteter Quellstand separat archiviert. Kein Fachdatenbestand, keine .env oder Zugangsdaten; private Konfiguration muss beim NAS-Wiederaufbau separat erhalten bleiben. SYSTEM-Zeitplan initial aus. Normale Webseite erhält nur Erstellen/Zeitplan, kein System-Einspielen.


### OPS-RESTORE-005 – Legacy-Datentypvertrag aus CI (09.10.2026)
- Integrationslauf dc2e653: Systemimage Export/Load und Teilrestore PASS; zusätzlicher bestehender Restoregate erreicht prepare_recovery und scheitert an source.started_at.isoformat(), weil unabhängiger Katalog JSON-Zeitstempel liefert. backup_path_for_run muss seinen bestehenden Python-Vertrag (UUID/Datetime) auch für katalogbasierte Treffer erhalten; JSON-Katalog selbst bleibt serialisierbar. Korrektur im Lookup mit gezieltem Regressionstest, keine Abschaltung des alten Restoregates.


### OPS-RESTORE-005 – Fortsetzung und HTTP-Bereitschaft (09.10.2026)
- Nutzer bestätigt Fortsetzung, keine Löschung. SERVER28c89609 / PR6 Draft, Android423f3789 unverändert geprüft.
- Run37918799711/Job113781441070: echter USER-Auftrag mit Admin/CSRF, Doppelauftragsschutz, separatem Fortschritt und erfolgreicher Umschaltung PASS. Nächster TRACK-HTTP-Aufruf trifft direkt nach dauerhafter Freigabe auf noch startende API (ConnectionRefused). Test muss vor jedem neuen Auftrag tatsächliche HTTP-Bereitschaft abwarten; keine Wiederholung mutierender Requests. DONE bedeutet dauerhafte Freigabeentscheidung; Betrieb wird danach von Supervisoren gestartet.
- Bereits dokumentierten Katalog-Datentypvertrag reparieren. Bedienoberfläche zusätzlich mit isoliertem Browserfixture prüfen; das ersetzt kein NAS-/Browser-Backend-E2E. Katalogliste auch nach Datenrestore aus unabhängigem Index anzeigen und Auswahl bei Aktualisierung erhalten.


### OPS-RESTORE-005 – Browserbefund und Containerprüfung (09.10.2026)
- Erster echter Chromium-Fixturetest erreicht Auswahl/Bestätigung, scheitert beim Start über nicht als sicher geltende HTTP-Origin: crypto.randomUUID ist dort nicht verfügbar. NAS-LAN-HTTP muss ohne unsichere Zufallszahlen funktionieren; UUID aus crypto.getRandomValues als kompatiblen Fallback ergänzen. Test behält absichtlich HTTP-Origin.
- Zusätzlich tatsächliche Compose-Topologie in eindeutigem temporären CI-Projekt mit isolierten Verzeichnissen/DB/Netzen prüfen: Initialisierung/Migration, vier Erzeuger, drei Restoreaufträge durch Gateway und Neustart auf gewähltem Stand. Kein Cloudflare-Tunnel, keine NAS-/Bestandsdaten. Temporäre CI-Ressourcen am Ende ausschließlich über den eindeutigen Test-Projektnamen abbauen.


### OPS-RESTORE-005 – Status-/Dokumentationsabschluss (09.10.2026)
- Neue Backup-Jobs USER/TRACK/SYSTEM müssen in der vorhandenen Monitor-Fehlererkennung berücksichtigt werden; bisherige Abfrage enthielt nur DATABASE/FILES/FULL/RETENTION. Bestehende Alarmsemantik beibehalten, Arten ergänzen.
- Vorhandene TB-HELFER_STATUS.xlsx ist bereits binär beschädigt: exakt heruntergeladene14998Bytes entsprechen Git-Blob a56c44ccbd85ebe1499a27c4777bc77409eb9479, ZIP-Validierung BadZipFile und Artifact-Import FileContainsCorruptedData. Archiv SERVER_STATUS_ALT_2026-09-21.xlsx ist derselbe Blob; keine intakte Vorgängerversion unter aktuellem Pfad. Nicht durch erfundene Tabelle ersetzen; MD-Status bleibt verbindlich, Excel-Aktualisierung bis zu intakter Vorlage blockiert.
- NAS-Anleitung als überprüfbare Datei vorbereiten. Individuelle laufende Compose und tatsächlicher Tunnel-Origin weiterhin für letzten Abgleich erforderlich; keine aktuelle Installation oder Löschung.


### OPS-RESTORE-005 – echter Gateway-Startbefund (09.10.2026)
- Compose-Gate Run37921366147/Job113789810250: Migrationen001–022 und API starten tatsächlich; Gateway startet nicht, nginx meldet unknown directive 36}$. Regulärer Ausdruck der Fortschrittsroute enthält unquotierte geschweifte Klammern. Regex als nginx-String quotieren; der echte Compose-Gate bleibt Pflicht. Kein Fehler an produktiver NAS, keine Freigabe allein aus bisherigen Prozess-/Browserfixturetests.


### OPS-RESTORE-005 – Compose-Funktion bestanden, CI-Aufräumrecht (09.10.2026)
- Run37921968708/Job113791786445: tatsächliche Compose-Initialisierung/Migration/Gateway, alle4Backup-Erzeuger, USER/TRACK/FULL-Restore über Gateway und Neustart auf gewählter Generation PASS (Logs gelesen). Job wird erst beim temporären Testverzeichnis-Abbau rot: root-eigene Container-Zieldateien können vom Runner nicht entfernt werden. Nur isoliertes CI-Tempverzeichnis nach Compose-down an Runner-UID/GID zurückgeben; keine Produktionsrechte ändern, keine Tests abschalten.


### OPS-RESTORE-005 – Arbeitsabschluss vor NAS-Abgleich
- Umsetzung und isolierte Gesamtprüfung auf d8d28e2a abgeschlossen: Restoreworkflow37922416404 vollständig SUCCESS, einschließlich echter Compose-Topologie und Browserfixture. Nachweise in06/08.
- Nächster benötigter Nutzerinput ist die aktuell verwendete NAS-Compose ohne Secrets, nicht .env. Erst danach individuelle Installationsanleitung finalisieren. Weiter keine NAS-Änderung/Löschung; bestehende Android-Abhängigkeit bleibt offen.


### OPS-RESTORE-005 – reale Compose zum NAS-Abgleich eingegangen (09.10.2026, Abend)
- Nutzer lädt aktive compose.yml hoch. SERVERbb756f7a/PR6 Draft und Android423f3789 unverändert geprüft. Datei enthält Platzhalter, keine mitgelieferten Passwort-/Tokenwerte; .env nicht angefordert.
- Projektname tb-helfer-v1; DB/Restore-Test-DB verwenden postgis17-3.5. Zusätzlicher bisheriger restore-target-db samt eigenem Datenverzeichnis vorhanden, Worker bindet restore-target-storage-v1 und nutzt alte manuelle Zielparameter. API-Port über TB_V1_API_BIND/TB_V1_API_PORT (Default127.0.0.1:8081). Cloudflared ist tokenverwaltet, tatsächliche öffentlichen Hostname-Origins stehen nicht in der Datei.
- Angepasste Compose auf geprüftem automatischen Aufbau vorbereiten: vorhandene DB-/Backup-/Storage-/SMTP-/Domain-Variablen und Speicherlimit erhalten; alten restore-target-db unverändert als Bestandsdienst behalten, keine Zielordner löschen; automatischer Worker verwendet neue Generationsziele und benötigt alte Zielparameter/Mount/Abhängigkeit nicht mehr. Gateway übernimmt identische Portbindung. Aktive hochzuladende Datei bleibt compose.yml; Systemexport bindet genau diese Datei unter kanonischem Archivnamen.
- Angepasste Variante im Repository sichern und separaten echten CI-Compose-Test ausführen. Originalupload unverändert behalten; separate angepasste Datei zurückgeben und im Repository sichern. Installationsablauf konkretisieren; vor tatsächlichem Projektneustart weiterhin Cloudflare-Origin für Portal/API abgleichen. Auf NAS nichts ausführen, keine .env verändern.


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


### OPS-RESTORE-005 – NAS-Portbindung bestätigt (09.10.2026, 20:18 Berlin)
- Nutzer bestätigt ausdrücklich TB_V1_API_BIND=0.0.0.0; TB_V1_API_PORT=8081 im Screenshot vollständig sichtbar. Container Manager zeigt API-Container8080/tcp auf Host8081,512MB und Projektordner /volume1/docker/tb-helfer-v1.
- Damit ist der ausstehende Konfigurationsabgleich geschlossen: vorhandene .env unverändert übernehmen, Cloudflare www/api-v1 weiter auf http://192.168.178.3:8081; neue Compose veröffentlicht diesen Port am Gateway. Keine weitere Rückfrage zum Bind-Wert.
- Nutzer „ja genau, bauen“: konkrete Datei-/Ordnerliste zur Übernahme bereitstellen. Runtime und Compose bereits im Quellstand9b1d848e gebaut und isoliert erfolgreich getestet (Restoreworkflow37965208092); jetzt ausschließlich Dokumentationsabschluss, keine erneute Codeänderung nötig.
- NAS-Installation weiterhin nicht ausgeführt. Vor Upload verifizierte Sicherung und alte Compose behalten, Projekt stoppen; erst dann zusammengehörige Software übernehmen und neu bauen. Nichts löschen. Erster Start/Health/Portal/Backup-Nachweis auf NAS offen, Datenrestore erst im vereinbarten Testfenster. Android-Recovery-ID/Outbox weiterhin separat offen.


## OPS-RESTORE-006 – NAS-Test und Folgekorrektur (09.10.2026, 21:58 Berlin)
- Nutzer hat geprüfte Compose installiert. Vier Backuparten erfolgreich laut NAS-Oberfläche; FULL 21:01:51 / 826 MB geprüft, SYSTEM 21:19:17 / 1,1 GB.
- Automatischer FULL-Restore meldet 21:37 Erfolg; erneute Anmeldung, 22/22 Migrationen und erreichbare Dienste nachgewiesen. Nutzer bestätigt 21:54 Testfirma, Baustellen, Bautagebuch und Bilder zugänglich. USER/TRACK-NAS-Restore und Android-Recovery bleiben offen.
- Diagnoseupload diagnostics-v1(1).zip: monitor-state 21:49:54 zeigt BACKUP_FULL FAILED / CANCELLED_AFTER_RECOVERY als einzigen aktuellen ERROR, interne/öffentliche API, DB und Worker OK. Vermutung: eigener laufender Backupauftrag im Snapshot wird beim Restore storniert. Codeprüfung und korrekte neutrale Darstellung erforderlich, echte Fehler dürfen nicht verborgen werden.
- Öffentliche API-Timeouts bestehen vor Restore (375 Ereignisse am 09.10. vor 21:35); Ursache nicht als Cloudflarefehler bewiesen. Codepfad/Timeoutdiagnose prüfen; keine blinde Timeout-Erhöhung oder NAS-Neuinstallation.
- Nutzerauftrag 21:58: beide Punkte untersuchen/korrigieren, Tests und Dokumentation. Keine NAS-/Cloudflare-/Androidänderung in diesem Block.


## Aktueller Vorrang – NAS-Abnahme und Fortsetzung (11.10.2026, 15:38 Berlin)
- BESTÄTIGT durch Nutzer: „backup funktionieren alle“, auf Rückfrage einschließlich Einspielen „ja alles funktioniert“. Backup-Praxisblock damit nutzerseitig abgenommen; keine Wiederholung der Backup-/Restoretests verlangen. Dies ersetzt die älteren offenen USER/TRACK-NAS-Abnahmehinweise. Kein neuer unabhängiger Systemimage-Wiederaufbau- oder Android-Recovery-Nachweis daraus ableiten.
- SERVER-Ausgangsstand600d070cf94f35d5ed28ada68d2c163a97501e55, PR6 offen/Draft; Android423f37892915cb3308a4b1ea9e4d196faa872326, PR11 offen/kein Draft. Android bleibt nach Server/Portal.
- Diagnoseauswertung11.10.: monitor-state15:14:33 Berlin interne API42,2ms, DB17,4ms, Workerheartbeat6,1s, BackupüberwachungOK (RetentionDONE), öffentlichWARN1708ms/HTTP200. Neue HTTP-Phasendiagnose aktiv. Keine neuen Gateway-Verbindungsfehler im Export nach9.10.22:05; alte Worker-/DB-Startfehler nicht als neue Störung werten.
- Öffentlich weiter offen: Ereigniszählung nach UTC-Kalendertag10.10.104 PUBLIC_API_ERROR,11.10.bisExport6; keine Ausfallquote aus Übergangsereignissen ableiten. 24erVergleich: intern0Fehler/max128ms, NASGateway0/max321ms, öffentlich1Timeout/max5040ms und5langsam. Laptop3/11Seiten leer; HandyMobilfunk14/14 erfolgreich, zeitlich getrennt. Ursache FRITZ!Box/Switch/Provider/Cloudflare nicht nachgewiesen.
- Nutzer kann großes API-Protokoll wegen NAS-Belastung nicht exportieren; nicht erneut anfordern. Netzwerkursachensuche auf Nutzerwunsch zurückgestellt, kein Timeout-/Tunnelumbau auf Verdacht.
- Nächster fachlicher Block gemäß bestehendem Plan: Portal-BTB/Unterbaustellen, Einträge und Fotos umhängen, Wetter und Bildgrößen. Bereits implementierte Rechte-/Last-CI nicht doppelt bauen; deren NAS-/Geräteabnahme bleibt separat. Zuerst bestehende Portalabläufe prüfen, bestätigte Funktionen schützen. Keine NAS-Datenmutation in diesem Dokumentationsblock.


## PORTAL-RIGHTS-004 – Befund und freigegebene Korrektur (11.10.2026)
- Nutzer zeigt PERMISSION_REQUIRED:files.view auf Baustellenseite und markiert Admin-Link bei USER; bestätigt Korrektur mit okay.
- Quellbefund: v1_worksite.html lädt Medien bedingungslos vor weiteren Bereichen/Live-Aktualisierung. Fehlendes files.view unterbricht Initialisierung. Medien nur mit vorhandenem Recht laden; Backendprüfung unverändert, keine Rechte pauschal erteilen. Admin-Login-Link nicht für angemeldete USER/CHEF anzeigen.
- Android SERVER_RECOVERY_REBASE_REQUIRED separat dokumentiert: lokale Daten/Outbox erhalten, kein Reset/Neuaktivierung anweisen; Android-Implementierung außerhalb dieses Blocks.

### Umsetzung PORTAL-RIGHTS-004
- v1_worksite.html: Medienzustand vor Abruf leeren, ohne files.view keinen Medienrequest senden; verständlicher Hinweis statt Medienaktionen. Backendrechte unverändert. v1_base.html: Admin-Link für angemeldete USER/CHEF ausgeblendet, separater Adminlogin bleibt erreichbar.
- Lokaler Node-Verhaltenstest mit extrahierter Produktionsfunktion PASS: kein Request ohne Recht, Laden mit Recht, alte Mediendaten löschen, echte Netzwerkfehler weiterwerfen. Jinja-Render-/Browserprüfung nicht ausgeführt: Jinja2 lokal nicht verfügbar, Installationsversuch ohne verfügbare Distribution. Keine NAS-Abnahme behauptet.
- Übernahme nur server/templates/v1_base.html und server/templates/v1_worksite.html nach templates/; API neu starten, Browser aktualisieren. Keine Migration/DB-/Rechte-/Android-/Backupänderung.


## PORTAL-005 – freigegebener Gesamtauftrag (11.10.2026, 17:34 Berlin)
- Nutzerfreigabe: bauen. Portal nach Aufgaben mit Baustellen/Bilder/Einstellungen, Baustellenreitern Übersicht/BTB/Bilder/Unterbaustellen/Mängel und Abnahmen/Team, bedarfsweise Formularen, deutschen Statusnamen und ohne JSON-Bedienfelder umbauen.
- Öffentliche Startseite: Erklärung und aggregierte Streckenkilometer, Bahnhöfe mit Daten, Strecken und Baustellen. Testbaustellen ausdrücklich mitzählen. Keine Firmen-/Nutzer-/Standortdetails oder technischen Speicher-/Serverdaten öffentlich.
- Passwort vergessen mit bestätigter E-Mail, einmaligem befristetem Resetlink, neutraler Antwort und Missbrauchsbegrenzung; administrativer Ersatzweg ohne bestätigte E-Mail. Profil/E-Mail/Passwort in Einstellungen; Geräteaktivierung getrennt erhalten.
- Helfer laut Nutzer vor Backup in Portal und Handy entlassen, dennoch Mitglied und WORKING sichtbar. Source-Lücke: set_own_status überschreibt NOT_PARTICIPATING bei resume und finish; Entlassung darf nur Teamleitung aufheben. Konkreter NAS-Ablauf unbewiesen. Teammitgliedschaft und einzelne BTB-Teilnahme getrennt behandeln; Fotorechte untersuchen, keine pauschalen Rechte vergeben.
- ZIP-Regel BESTÄTIGT: auf exportierendem Handy vorhandene eigene Originale verwenden, Helfer-/Portalbilder verkleinert; Export vom Handy an PC. Vorhandene Implementierung schützen, keine Android-Änderungen in SERVER.
- PORTAL-RIGHTS-004 NAS-Screenshots zeigen Adminlink und files.view-Abbruch behoben; fehlendes Fotorecht selbst bleibt offen. Backupabnahme geschützt. Umsetzung und Tests des neuen Auftrags noch offen.


### PORTAL-005 – Implementierung zur Prüfung
- Navigation mit Einstellungen/Bilderübersicht, Baustellenbereiche als Reiter, eingeklappte Formulare, übersetzte Statuswerte, BTB-Datums-/Statusfilter, Bildergalerie mit vorhandenen thumb/preview-Renditions. Öffentliche Startseite zählt nur aggregiert; Testbaustellen eingeschlossen, Streckenintervalle je Strecke vereinigt.
- Helfer: eigene Statuswechsel aus NOT_PARTICIPATING gesperrt; abgeschlossene BTBs erhalten beim Lesen keine neuen Teamteilnehmer. Explizites Entfernen eines Helfers aus Baustelle mit Rechteprüfung, widerrufenen offenen Einladungen und Change Feed. Historische Beiträge erhalten.
- Migration023: fehlendes files.view nur im eingebauten RESPONSIBLE_TB-Profil ergänzen, individuelle false-Overrides bleiben; gehashte einmalige Accounttokens und Rate Limits; E-Mail-Änderung invalidiert Bestätigung/Links.
- Passwort-/E-Mail-Endpunkte und UI implementiert, Admin-Ersatzlink, fest konfigurierte HTTPS-Portalbasis, 30 Minuten, keine Geräteänderung. Kein echter Mailversand durch Agent. SMTP-Liveabnahme steht aus.
- Lokaler Python-AST-/JavaScript-Syntaxcheck PASS. Lokale Jinja/FastAPI/PostgreSQL-Abhängigkeiten fehlen; pip ohne verfügbare Distribution, Chromium-Binary fehlt. Separater PostgreSQL17-/Jinja-CI-Gate hinzugefügt und noch nicht ausgewertet. Keine NAS-Installation/Fertigabnahme behauptet.
