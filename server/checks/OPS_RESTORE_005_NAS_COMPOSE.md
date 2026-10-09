# NAS-Abgleich der hochgeladenen compose.yml

Stand: 09.10.2026. Ausgang: vom Nutzer gelieferte `compose.yml`, Projekt `tb-helfer-v1`. Angepasste Datei im Repository: `server/compose.synology-reviewed.v1.yml`; auf der NAS heißt sie wieder **compose.yml**. Originalupload bleibt unverändert. Noch keine NAS-Installation ausgeführt.

## Ergebnis des Dateiabgleichs

| Bereich | Angepasster Aufbau |
| --- | --- |
| Hauptdatenbank `db` | Definition unverändert: PostgreSQL/PostGIS17-3.5, gleiche DB-/Benutzervariablen und Datenbindung |
| Datenbank `restore-test-db` | Definition unverändert |
| Bisherige Zieldatenbank `restore-target-db` | Definition unverändert und als eigener Dienst erhalten; automatischer Restore verwendet sie nicht |
| `storage-v1`, `backups-v1`, `diagnostics-v1` | Identische vorhandene Variablen und Bindungen |
| Host-Port | Unverändert `${TB_V1_API_BIND:-127.0.0.1}:${TB_V1_API_PORT:-8081}`; liegt künftig am Gateway |
| API-Speicherlimit | Unverändert `${TB_V1_API_MEMORY_LIMIT:-512m}` |
| SMTP, Domain, Zeitzone | Bisherige Variablen erhalten; `.env` bleibt vor Ort unverändert |
| Cloudflared | Dienstdefinition unverändert; öffentlich konfigurierte Weiterleitungsziele sind nicht in Compose enthalten |
| Worker | Neue Prozesssteuerung; alte manuelle Zielparameter/-bindung/-abhängigkeit entfallen. Alter Zielbestand wird nicht entfernt |
| Systemexport | Bindet die aktive `compose.yml`; im Systemarchiv trägt sie den kanonischen Namen `docker-compose.synology-automatic.v1.yml` |

Die tatsächlichen Werte aus `.env` wurden nicht gelesen. Weil dieselben Variablen beibehalten werden, gelten vorhandene eigene Pfade/Ports weiter. Die Verzeichnisnamen hier sind die Defaults, keine Behauptung über davon abweichende NAS-Werte.

## Noch vor Neustart prüfen: Cloudflare-Ziele

Im verwendeten Tunnel die Service-/Zieladressen für **www.tb-helfer.de** und **api-v1.tb-helfer.de** prüfen:

- Zeigt ein Ziel direkt auf `http://api:8080`, muss es für den neuen Aufbau `http://gateway:8080` heißen.
- Zeigt es bereits auf den veröffentlichten NAS-Port8081 oder auf einen vorgeschalteten Reverse-Proxy, prüfen, ob dieser weiterhin den unveränderten veröffentlichten Port erreicht. Dann ist nicht automatisch eine Änderung erforderlich.
- Andere individuelle Ziele zuerst zuordnen. Aus dem Tunnel-Token allein lassen sie sich nicht ableiten.

Benötigt wird nur ein Screenshot dieser Hostnamen/Ziele oder deren Text. **Keine Tokens, Passwörter oder `.env`.** Solange das unbekannt ist, das laufende NAS-Projekt noch nicht ersetzen/neustarten. Die Dateien können bereits auf den PC geladen werden.

## Übernahme nach dem Zielabgleich

1. Mit GitHub Desktop `feature/tb-server-1.0` aktualisieren. Alle Softwaredateien aus demselben geprüften Stand verwenden. Aktuellen vollständigen Daten-/Dateibestand verifiziert sichern und bisherige Compose separat aufbewahren. Kein Restoreauftrag darf laufen oder warten.
2. Im Container Manager das bestehende Projekt `tb-helfer-v1` stoppen. Während des Softwareuploads bleibt es gestoppt, damit keine Mischung alter und neuer Dateien läuft.
3. In File Station im **bisherigen Projektordner** arbeiten (bisheriger Standard `/volume1/docker/tb-helfer-v1`). Vorhandene Compose zusätzlich auf den PC sichern. `.env` unverändert behalten.
4. Folgende Inhalte aus dem Repository kopieren; vorhandene Softwaredateien ersetzen/ergänzen, keine Datenordner entfernen:

| Repositoryquelle | Ziel im bisherigen Projektordner |
| --- | --- |
| `server/tbserver/` | `tbserver/` vollständig |
| `server/templates/` | `templates/` vollständig |
| `server/v1_migrations/` | `v1_migrations/`, alte Migrationen behalten,022 ergänzen |
| `server/tools/` | `tools/` |
| `server/data/` | `data/` – mitgelieferte Referenzdateien, nicht NAS-Fachdaten |
| `server/requirements.txt` | `requirements.txt` |
| `server/Dockerfile.v1` | `Dockerfile.v1` |
| `server/recovery-gateway.conf` | `recovery-gateway.conf` |
| `server/compose.synology-reviewed.v1.yml` | **compose.yml** |

5. Neue persistente Ordner anlegen, sofern noch nicht vorhanden: `recovery-control-v1`, `recovery-journal-v1`, `recovery-queue-v1`, `recovery-storage-v1` sowie `recovery-acks-v1/api`, `recovery-acks-v1/worker`, `recovery-acks-v1/monitor`. Bereits vorhandene Inhalte nicht leeren. Falls die entsprechenden neuen `TB_V1_RECOVERY_*_DIR`-Variablen vor Ort bereits gesetzt sind, deren Verzeichnisse benutzen.
6. Erforderliche Cloudflare-Zieländerungen entsprechend obigem Abgleich vornehmen. Projekt mit der angepassten **compose.yml** im Container Manager neu erstellen und dabei das Serverimage neu bauen. Bestehendes Projekt beibehalten; keine Volumes/Datenverzeichnisse löschen.
7. Erwarteter Start: `recovery-init` endet erfolgreich, danach `migrate`; anschließend API, Worker, Monitor, Recovery, Gateway und Systemexport. Ein erfolgreich beendeter Initialisierungs-/Migrationscontainer muss nicht dauerhaft laufen. Bestehende DB-/Hilfsdienste bleiben erhalten.
8. Systemstatus mit Strg+F5 neu laden. Migrationen001–022 müssen angewendet sein. Nutzer-, Strecken-, Voll- und Systembackup prüfen; „Backup einspielen“ muss die automatische Oberfläche zeigen. Normale Anmeldung/Portal-/API-Erreichbarkeit zunächst ohne Restore prüfen.
9. Erst danach einen ausdrücklich vereinbarten NAS-Restoretest mit nachvollziehbarem Ausgangsbestand durchführen. Android-Recovery-ID/Outbox-Abgleich ist weiterhin nicht umgesetzt/abgenommen; ein echter Datenrestore ist deshalb noch keine Freigabe für den vollständigen Mehrgerätebetrieb.

## Was löschen?

**Nichts.** Insbesondere behalten: `.env`, `database-v1`, `storage-v1`, `backups-v1`, `restore-test-database-v1`, `restore-target-database-v1`, `restore-target-storage-v1`, `diagnostics-v1` sowie neue Recoveryordner. Das gilt auch für abweichende eigene Verzeichnisnamen. Bereits erzeugte Zielbestände werden nicht für einen neuen Versuch geleert.

Systembackup und Vollbackup bleiben unabhängig. Eine zusätzliche Restore-Sicherheitskopie unter `.recovery` zählt nicht zu den vier regulären Vollbackups und wird nicht automatisch gelöscht. Nach bereits angenommenen Änderungen auf einem restaurierten Ziel die alte Compose niemals blind als Datenrückweg aktivieren.

## Testeinordnung

Der bisherige automatische Aufbau ist mit echten PostgreSQL-/Docker-/HTTP-/Abbruchtests und einem Browserfixture geprüft. Die angepasste Variante erhält zusätzlich denselben Compose-Ablauf, einen unveränderten Marker in der alten Zieldatenbank sowie einen Bytevergleich der im Systemarchiv gespeicherten aktiven Compose. Konkretes Ergebnis siehe `projektsteuerung/06_E2E_TESTSTATUS.md`. Eine tatsächliche NAS-/Cloudflare-/Android-Abnahme bleibt davon getrennt.

Prüfnachweis: Quellstand `9b1d848ede7f5aeb5a7c155a887a04ab588beca9`, [Restoreworkflow37965208092](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37965208092) vollständig erfolgreich; Corejob113937722251 einschließlich beider Compose-Varianten und Browserjob113937722449. Erhalt der alten Zieldatenbank und Bytegleichheit der archivierten aktiven Compose ausdrücklich bestanden. Gesamtprojekt-CI hat weiterhin die separat dokumentierten alten foundation-/Android-Fehler.
