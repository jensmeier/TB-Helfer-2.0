# OPS-RESTORE-003 – Server wiederherstellen

## Was der neue Ablauf tut

Systemstatus → **Server wiederherstellen**. Ein erfolgreiches Vollbackup wählen,
Datum prüfen und `WIEDERHERSTELLEN <Backup-ID>` eingeben. Der Worker erstellt eine
zusätzliche Sicherung des aktuellen Bestands und baut aus dem gewählten Backup
ein getrenntes Wiederherstellungsziel auf. Datenbank, Migrationsprüfsummen und
sämtliche dauerhaften Dateireferenzen werden geprüft.

**„Ziel geprüft und bereit“ bedeutet noch keine Umschaltung.** Der nächste Schritt
ist eine bewusste Konfigurationsänderung im Synology Container Manager. Die API
bekommt keinen Docker-Socket und ersetzt weder sich selbst noch die Live-DB.

Quell- und Sicherheitsbackup werden zusätzlich unter
`backups-v1/.recovery/<Wiederherstellungs-ID>/` aufbewahrt. Normale Retention
entfernt diese Kopien nicht. Dort liegt auch `recovery.json`. Diese Kopien benötigen
zusätzlichen Speicher; erst nach abgeschlossener Abnahme und ausreichender
anderweitiger Sicherung manuell aufräumen. Beim Platzmangel bleibt Vorbereitung
fehlgeschlagen; ein teilweise gefülltes Ziel wird nicht automatisch gelöscht.

Die Sicherheitskopie bildet den Zeitpunkt der **Vorbereitung** ab. Spätere
Änderungen bleiben im bisherigen Livebestand erhalten, sind aber nicht im
vorbereiteten Ziel und nicht zwingend in dieser Sicherheitskopie enthalten.
Deshalb Vorbereitung und Umschaltung in einem vereinbarten Wartungsfenster
durchführen. Keine automatische Zusammenführung alter und neuer Daten.

## Einmalige Übernahme auf Synology

Aktuellen Branch `feature/tb-server-1.0` mit GitHub Desktop holen. Über File Station
nach `/volume1/docker/tb-helfer-v1/` übernehmen:

| Repositorydatei unter `server/` | Ziel relativ zum Projektordner |
| --- | --- |
| `tbserver/recovery.py` (neu) | `tbserver/recovery.py` |
| `tbserver/recovery_guard.py` (neu) | `tbserver/recovery_guard.py` |
| `tbserver/database.py` | `tbserver/database.py` |
| `tbserver/jobs.py` | `tbserver/jobs.py` |
| `tbserver/system_admin.py` | `tbserver/system_admin.py` |
| `tbserver/restore.py` | `tbserver/restore.py` |
| `tbserver/restore_target.py` | `tbserver/restore_target.py` |
| `templates/v1_system_status.html` | `templates/v1_system_status.html` |
| `docker-compose.synology-recovery-prepare.v1.yml` (neu) | gleichnamige Datei |
| `docker-compose.synology-recovery.v1.yml` (neu) | gleichnamige Datei |

Voraussetzung ist der vorherige SERVER-GATE-001-Stand inklusive
`backup_inventory.py`, `backup.py`, `restore.py` und `restore_target.py`.
Keine neue Migration (weiterhin21). Für die UI und den bestehenden Betrieb reichen
die acht Python-/Templatedateien, danach API und Worker neu starten und Strg+F5.
Die bisherige Compose-Datei bleibt unverändert.

**Vor einer tatsächlichen Vorbereitung** die zusätzliche Zielumgebung einrichten:

1. Aktuelle Compose-Konfiguration und `.env` separat als Rückweg sichern.
2. In der vorhandenen `.env` ergänzen, niemals deren übrigen Inhalt ersetzen:
   - `TB_V1_RESTORE_TARGET_DB_PASSWORD=<neues langes eigenes Passwort>`
   - `TB_V1_ALLOW_RESTORE_TARGET=YES`
3. Standardziel: Host `restore-target-db`, Datenbank
   `tb_helfer_v1_restore_target`, Benutzer `tb_helfer_restore_target`, NAS-Ordner
   `restore-target-database-v1` und `restore-target-storage-v1`.
   **Beide Ordner dürfen keinen bisherigen Bestand enthalten.** Bereits verwendete
   Ziele nicht leeren; für einen neuen Versuch andere frische Zielordner und bei
   Bedarf DB-Namen konfigurieren. Die Variablen heißen
   `TB_V1_RESTORE_TARGET_DB_DATA_DIR`, `TB_V1_RESTORE_TARGET_STORAGE_DIR`,
   `TB_V1_RESTORE_TARGET_DB_NAME`, `TB_V1_RESTORE_TARGET_DB_USER`.
4. Die neue vollständige Konfiguration
   `docker-compose.synology-recovery-prepare.v1.yml` mit der bisherigen vergleichen.
   Bestehende individuelle Ports, Mounts, Ressourcenlimits, Tunnel- und
   Umgebungsanpassungen übernehmen. Sie basiert auf der versionierten
   Synology-Staging-Konfiguration; lokale NAS-Abweichungen kennt GitHub nicht.
5. Im **bestehenden Projekt** über Container Manager die Konfiguration übernehmen
   und neu erstellen/starten. Keine parallele zweite API mit demselben Port starten.
   Weder Volumes noch Datenordner löschen. Live-API/Worker/Monitor/Migration zeigen
   in dieser Vorbereitungskonfiguration weiterhin auf `db` und `storage-v1`.
6. Systemstatus → Server wiederherstellen → Backup wählen und bestätigen.
   Ein bereits belegtes Ziel, falsche Konfiguration oder eine fehlende Datei
   blockiert die Vorbereitung. Nicht einfach wiederholt starten oder Ziele leeren.

## Tatsächliche Umschaltung

Nur nach **„Ziel geprüft und bereit“**, im vereinbarten Wartungsfenster:

1. Benutzer informieren; unsynchronisierte Handyänderungen sichern. Hintergrundjobs
   fertig werden lassen. API, Worker und Monitor im Container Manager stoppen.
2. Die im Assistenten angezeigte Zeile `TB_V1_RECOVERY_ID=<UUID>` in die bestehende
   `.env` aufnehmen. Keine IDs raten. Das Ziel aus der Anzeige muss zu den oben
   konfigurierten Zielvariablen passen.
3. `docker-compose.synology-recovery.v1.yml` mit denselben individuellen
   Anpassungen verwenden. Im bestehenden Projekt neu erstellen/starten, nicht
   bloß die alten Container neu starten. Die Konfiguration schaltet **API, Worker,
   Monitor und Migration** gemeinsam auf Ziel-DB und Zielspeicher. Alte DB und
   alter Speicher bleiben als Rückweg erhalten.
4. Der Verbindungscheck vergleicht Wiederherstellungs-ID und Backup-Prüfsumme in
   Ziel-DB und Zielspeicher. Falsche Kombinationen verweigern den DB-Zugriff.
   Die ID bleibt dauerhaft in dieser aktiven Konfiguration stehen.
5. Admin mit dem **Passwort aus dem gewählten Backup** neu anmelden. Das anfordernde
   Adminkonto muss im Backup aktiv und vorhanden sein, sonst wird die Vorbereitung
   abgewiesen. Bei unbekanntem damaligem Passwort zunächst den dokumentierten
   bestehenden Admin-Zugangsweg klären, nicht blind umschalten.
6. Systemstatus meldet jetzt **„Wiederhergestellter Serverstand ist aktiv“**.
   Migrationen, Firmen, Streckennetz, Dateien sowie Portalzugriff mit bewusst
   freigegebenen Testbenutzern prüfen.
7. Andere Benutzer sind im Ziel zunächst deaktiviert, einschließlich anderer
   Administratoren. Alle Browser-Sitzungen, Geräte-Tokens, Einladungen und offenen
   Aktivierungen sind widerrufen, offene Uploads abgelaufen und alte wartende Jobs
   beendet. Nur das anfordernde Adminkonto bleibt aktiv. Berechtigungen gegen den
   heutigen Stand prüfen, benötigte Benutzer freigeben, Geräte neu aktivieren und
   den wiederhergestellten Serverbestand neu laden. Lokale Offline-Outbox niemals
   ungeprüft wieder auf diesen älteren Stand schreiben lassen. Android wurde nicht
   geändert; keine automatische Geräte-Recovery behaupten.
8. Automatische Backups, Retention und E-Mails sind im Ziel ausgeschaltet. Nach
   Abnahme gewünschte Automatik wieder einstellen und ein neues Vollbackup prüfen.

## Rückweg und Grenzen

Bei Problemen API/Worker/Monitor stoppen und die zuvor gesicherte ursprüngliche
Compose-Konfiguration aktivieren. Diese enthält `TB_RECOVERY_ID` nicht und zeigt
auf die unveränderte ursprüngliche DB und den ursprünglichen Speicher. Neu auf
dem Ziel entstandene Änderungen vorher separat sichern; der Rückweg überträgt
sie nicht automatisch. Keine Datenordner löschen.

Der Assistent führt einen **kontrollierten einmaligen Wechsel auf ein frisches
Ziel** durch. Nach Aktivierung blockiert er eine weitere Vorbereitung gegen
dasselbe konfigurierte Ziel. Eine erneute Wiederherstellung benötigt eine neue
frische Zielumgebung und eine entsprechend angepasste Deploymentkonfiguration;
die alte Vorbereitungskonfiguration darf dann nicht blind wiederverwendet werden,
weil sie auf die frühere Quell-DB zeigt. Dies ist kein automatischer Failoverdienst.

Nur FULL-Backups mit exakt zum aktuellen Code passenden Migrationsnamen und
Prüfsummen werden zum Umschalten freigegeben. Ältere Schema-Versionen benötigen
einen gesonderten geprüften Migrationsweg. Bei Totalausfall der Quell-DB ist der
Admin-Assistent nicht erreichbar; vorhandene Offline-Archive und der bestehende
separate Restore-Target-Weg bleiben der Notfallweg. Kein Browserbutton kann einen
ausgefallenen NAS selbst starten.

Unterbrochene Vorbereitungsjobs werden **nicht automatisch wiederholt**. Der
Adminstatus verlangt Zielprüfung. Ein bereits beschriebenes Ziel wird niemals
automatisch entfernt oder erneut überschrieben. Temporäre Fehler können deshalb
einen bewusst neu eingerichteten Zielordner/DB erfordern.

## Prüfungen

- Lokaler Guard-Test140: sieben Verhaltenstests (richtige Paarung, falsche DB,
  falscher Speicher, Teilzustand, fehlende/verlinkte Markierung, unveränderter
  Normalbetrieb).
- Python- und JavaScript-Syntax geprüft. Beide Compose-Dateien lokal geparst;
  alle vier umgeschalteten Dienste benötigen die ID und denselben Zielspeicher.
- `tools/recovery_gate.py`: echte FastAPI-HTTP-Anmeldung, Admin-/CSRF-Grenzen,
  falsche Bestätigung und Doppelauftrag; PostgreSQL-Dump/Restore mit voller
  Migration21, Dateiabgleich, unveränderte Live-Daten, gesperrte Zielzugänge und
  Jobs/Automatik; tatsächlicher API-Neustart auf Ziel, Admin-Login, abgewiesener
  alter Nutzer, falsche Speicherpaarung und ID.
- Gemeinsamer Ziel-Lock mit dem bisherigen CLI-Restore; identische Test-/Ziel-DB
  wird abgewiesen. Belegtes Ziel sowie Wiederholung und automatische Wiederholung
  nach Fehler/Worker-Unterbrechung werden geprüft.
- Gate ist nur unter `CI=true`, `TB_ENVIRONMENT=test`, DB `tb_gate_recovery`
  zulässig. Es wird in der bestehenden isolierten CI zusätzlich ausgeführt.
- Konkretes CI-Ergebnis wird nach Lauf ergänzt. Kein NAS-Cutover und kein
  Android-Reaktivierungs-E2E ausgeführt. Die NAS-Bestätigung vom06.10.2026 betrifft
  ausschließlich das bereits vorhandene Vollbackup851MB und Backup-Prüfung OK.
