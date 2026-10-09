# OPS-RESTORE-005 – automatische Wiederherstellung / NAS-Übernahme

Stand: 09.10.2026. **Übernahme vorbereitet, individuelle NAS-Konfiguration noch abzugleichen. Nicht allein anhand dieser Vorlage das laufende Projekt ersetzen.**

## Bedienung nach abgeschlossener Übernahme

Im Systemstatus stehen vier unabhängige Erzeuger und Zeitpläne: Nutzerbackup (USER), Streckenbackup (TRACK), Vollbackup (FULL) und Systembackup (SYSTEM). Jede Art bewahrt die letzten vier neu erfolgreich veröffentlichten Sicherungen auf. Eine fehlgeschlagene Sicherung verdrängt keine erfolgreiche. Importierte Altsicherungen werden beim bloßen Lesen nicht gelöscht; die nächste neue Sicherung bereinigt nur ihre eigene Art. Systembackup erzeugt kein Vollbackup und hält keines zusätzlich fest.

„Daten wiederherstellen“ zeigt für USER/TRACK/FULL die letzten vier verfügbaren Sicherungen mit Datum. Bereich wählen, Sicherung wählen, angezeigte Bestätigung eingeben und „Einspielen“ drücken. Danach übernimmt der unabhängige Wiederherstellungsdienst: Vorprüfung, Wartung, zusätzliche geschützte Sicherheitskopie, frisches Ziel, Prüfung, Umschaltung, API-Probe und Freigabe. Die Fortschrittsanzeige läuft über das Gateway auch während der API-Pause. Abschließend erneut anmelden. Wiederholtes Klicken startet keinen zweiten Auftrag.

USER setzt Firmen/Benutzer/Baustellen zurück und erhält den gemeinsamen Streckenbereich; TRACK setzt den gemeinsamen Streckenbereich zurück und erhält Firmen/Benutzer/Baustellen. Nicht auflösbare Quellen-/Historienverweise blockieren den Vorgang. FULL setzt beide Datenbereiche zurück. Geschützte Vermessungsoriginale werden nicht überschrieben; das Wiederherstellungsziel ist getrennt.

Vor endgültiger Erfolgsentscheidung führt ein Fehler automatisch zum vorherigen Stand zurück. Ist dieser Rückweg nicht sicher möglich, bleibt Wartung aktiv und Eingriffsbedarf sichtbar. Nach endgültiger Entscheidung erfolgt kein automatischer Rückwechsel, der bereits neue Änderungen verlieren könnte. Zusätzliche Sicherheitskopien unter `backups-v1/.recovery/` und alte Zielbestände werden **nicht automatisch gelöscht**; sie benötigen eine spätere bewusste, nachgewiesene Freigabe und ausreichend Speicherplatz.

SYSTEM besitzt bewusst keinen Einspielbereich auf der Webseite. Es sichert Docker-Images mit Tags/IDs, gebundenen Softwarequellstand und Manifest; keine Fachdaten, keine `.env` und keine Zugangsdaten. Private Konfiguration separat geschützt aufbewahren. Der Exportdienst verwendet einen eigenen internen Netzwerkzugang und als einziger Dienst den Docker-Socket. Der Code ruft ausschließlich feste GET-Image-Endpunkte auf; ein `:ro`-Socketmount ist für sich allein keine Docker-API-Berechtigungsgrenze.

## Vor dem ersten NAS-Umbau

1. Tatsächlich verwendete Compose-YAML vom Container-Manager-Projekt mit dieser Vorlage abgleichen. Geheimwerte vor Weitergabe entfernen; `.env` nicht hochladen. Bestehende Pfade, Ports, Speicherlimits, SMTP und Domainwerte erhalten.
2. Tatsächlichen Cloudflare-Origin für API- und Portal-Host prüfen. Im neuen Aufbau müssen beide über `http://gateway:8080` laufen. Ein bisheriger direkter Origin `http://api:8080` umgeht die unabhängige Fortschrittsanzeige. Bei einem NAS-Reverse-Proxy muss dessen Ziel der veröffentlichte Gateway-Port bleiben.
3. Aktuellen verifizierten Daten-/Dateistand und bisherige Compose separat behalten. Keine Wiederherstellung gleichzeitig mit Softwareupdate oder Backupdatei-Upload ausführen. Ausreichend freien Platz für Sicherheitskopie, frisches Ziel, Systemimageexport und erhaltene alte Bestände prüfen.
4. Android ist unverändert. Der offene Recovery-ID-/Outbox-Abgleich der App ist weiterhin eine gesonderte Voraussetzung für den vollständig abgenommenen Mehrgerätebetrieb. Ein Server-Test beweist diesen Android-Ablauf nicht.

## Vorbereitete Dateiliste

Aus **einem identischen geprüften Commit** auf `feature/tb-server-1.0` mit GitHub Desktop holen. Zielbasis: `/volume1/docker/tb-helfer-v1`, sofern die tatsächliche NAS-Compose diesen Pfad verwendet. Quelle ist jeweils der Inhalt von `server/`.

| Quelle im Repository | Ziel relativ zur Basis | Aktion |
| --- | --- | --- |
| `server/tbserver/` | `tbserver/` | Softwaredateien ersetzen/ergänzen, vollständig aus demselben Commit |
| `server/templates/` | `templates/` | Vorlagen ersetzen/ergänzen |
| `server/v1_migrations/` | `v1_migrations/` | Migrationen ergänzen, insbesondere022; alte Migrationen behalten |
| `server/tools/` | `tools/` | Betriebswerkzeuge ersetzen/ergänzen |
| `server/data/` | `data/` | Mitgelieferte Referenzdateien; nur Repository-Softwaredaten, keine NAS-Fachdaten |
| `server/requirements.txt` | `requirements.txt` | ersetzen |
| `server/Dockerfile.v1` | `Dockerfile.v1` | ersetzen / Image neu bauen |
| `server/docker-compose.synology-automatic.v1.yml` | `docker-compose.synology-automatic.v1.yml` | neue Vorlage; erst nach individuellem Abgleich als Projektdefinition verwenden |
| `server/recovery-gateway.conf` | `recovery-gateway.conf` | neu; neben Compose-Datei |

Keine einzelnen neuen Pythonmodule mit älterem restlichem Quellstand mischen. Das Systembackup bindet und archiviert dieselben Softwareordner, die auch API, Worker, Monitor und Recovery sehen.

### Neue persistente Ordner

Vor dem Projektstart per File Station anlegen, soweit noch nicht vorhanden; vorhandene Inhalte niemals leeren:

- `recovery-control-v1/`
- `recovery-journal-v1/`
- `recovery-queue-v1/`
- `recovery-acks-v1/api/`, `recovery-acks-v1/worker/`, `recovery-acks-v1/monitor/`
- `recovery-storage-v1/`

Vorhandene `storage-v1/`, `backups-v1/`, `database-v1/`, `restore-test-database-v1/`, `diagnostics-v1/` und alle bisherigen Wiederherstellungszielordner behalten. Alle projektspezifisch abweichenden Verzeichnisse aus der aktiven Compose ebenfalls erhalten. Die Initialisierung darf nur einen erstmaligen leeren Steuerungszustand anlegen; fehlt später die Auswahl, wird blockiert statt auf alte Daten zurückzufallen.

**Löschliste: keine Dateien oder Datenordner zur Löschung freigegeben.** Auch die bisherige Compose als Notfallreferenz behalten. Ihr späterer Einsatz als Datenrückweg muss zur bereits gewählten Generation passen; nach neuen Schreibzugriffen niemals blind auf den Anfangsbestand zurückwechseln.

### Geplante Übernahme nach Abgleich

Über File Station Software hochladen. Über Container Manager das betroffene Projekt stoppen, die geprüfte Compose übernehmen und das Serverimage neu bauen/Projekt neu erstellen. Keine Volumes oder Datenverzeichnisse löschen. `recovery-init` muss erfolgreich enden, danach `migrate`; API/Worker/Monitor, Recovery, Gateway und Systemexport müssen starten. Keine NAS-SSH-Befehle erforderlich.

Erst wenn das Projekt gesund ist: Systemstatus öffnen, Migrationen022 prüfen, vier Backuparten sichtbar prüfen, je eine Testsicherung erstellen. Neue SYSTEM-Datei muss unabhängig von FULL entstehen. Wiederherstellung auf der NAS erst in einem vereinbarten Testfenster mit nachvollziehbarem Ausgangsstand durchführen; Browseranzeige, Login, Datenbereiche, Dateien und Geräteverhalten danach gesondert abnehmen.

## Systemwiederaufbau ohne Webseite

Über File Station ein vertrauenswürdiges Systemarchiv in einen separaten Arbeitsordner entpacken. Manifest/Prüfsummen der enthaltenen `images.tar` und `source.tar.gz` prüfen. `images.tar` über Container Manager als Images importieren; die Tags/IDs müssen zum Manifest passen. Software aus `source.tar.gz` passend wiederherstellen. Die eigene geschützte `.env` und die individuell geprüfte Konfiguration separat beibehalten. Ein kompatibler Datenstand kommt aus einem unabhängig gewählten Datenbackup oder dem vorhandenen Bestand. Datenbankmajorversion und Migrationen müssen zum wiederhergestellten System passen; niemals ein älteres Image blind gegen eine neuere Datenbank starten. Kein automatischer Systemrollback und kein NAS-Systemrestore-E2E in diesem Auftrag behauptet.

## Nachweise und verbleibende Grenzen

Siehe `projektsteuerung/06_E2E_TESTSTATUS.md` und `08_CHAT_CHECKPOINT.md` für genaue Commits/CI-Läufe. Isolierte PostgreSQL-/Prozess-/HTTP-Tests und ein Chromium-UI-Fixture sind von echter NAS-/Cloudflare-/Android-Abnahme getrennt. Der Chromium-Test verwendet Antwortfixtures, keinen vollständigen Browser-Backend-E2E.
