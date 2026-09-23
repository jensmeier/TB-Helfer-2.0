# TB-Helfer – Prüfstand / Chat-Übergabe 2026-09-23

> Zweck: kurzer, belastbarer Übergabestand vor neuem Chat.  
> GitHub bleibt Source of Truth. Dieser Prüfstand ersetzt weder Projektregeln noch STATUS.md/.xlsx.

## 1. GitHub-Stand

- Repository: `jensmeier/TB-Helfer-2.0`
- SERVER-Branch: `feature/tb-server-1.0`
- SERVER-HEAD: `3ffa65b1dbc27ed122ef9ac91eb84c9caedcbabb`
- SERVER-Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- ANDROID-Branch: `feature/tb-android-2.0`
- ANDROID-HEAD: `8230d4fce7486b758af49499f82b4f8748836b80`
- ANDROID-Draft-PR: **#7 – Android 1.3.15 / 200065 – BTB-Verschiebeziele sichtbar + Bilder nachtragen**
- Android-Quellstand laut PR-Titel: **1.3.15 / 200065**
- Weitere ältere offene PRs existieren (#5, #1); für den aktuellen Server-/Android-Stand sind #6/#7 maßgeblich.

## 2. SERVER – praktisch bestätigter aktueller Betriebsstand

Auf der Synology läuft nur noch der neue Serververbund `tb-helfer-v1`.

Aktiver Zielaufbau:
- `tb-helfer-v1-api-1` – API/Admin
- `tb-helfer-v1-worker-1` – Background-Jobs, Backups, E-Mail
- `tb-helfer-v1-monitor-1` – Serverüberwachung/Diagnose
- `tb-helfer-v1-db-1` – produktive PostGIS-Datenbank
- `tb-helfer-v1-cloudflared-1` – öffentlicher Cloudflare-Zugang
- `tb-helfer-v1-migrate-1` – beendet; im Normalzustand korrekt
- `tb-helfer-v1-restore-test-db-1` – **bewusst dauerhaft beibehalten (Variante A)** für Restore-Tests

Praktisch bestätigt:
- `api-v1.tb-helfer.de` bleibt über den neuen `tb-helfer-v1-cloudflared-1` erreichbar.
- Der alte Serververbund `tb-helfer` ist entfernt; alter API-/DB-/Cloudflare-Betrieb wird nicht mehr benötigt.
- Cloudflare ist im neuen Server-Compose integriert.
- Restore-Target-Container und zugehörige Target-Verzeichnisse wurden entfernt.
- Das alte einmalige Runtime-Fix-Skript wurde entfernt.
- Datenbank-/Dateibackups laufen; Restore-Test-Funktion wurde zuvor praktisch bestätigt.
- Systemmonitor, SMTP-Testmail und Alarmmail wurden praktisch bestätigt.
- Systemstatus/Auditansicht ist kompakt; Auditliste scrollt wie die Backupliste.
- Baustellen-Adminansicht ist nach Firmen gruppiert und filterbar.
- Geräte-Adminansicht ist nach Firmen gruppiert und filterbar.
- Pixel und Poco werden im neuen Server als aktive Geräte geführt.

## 3. Geschützte / nicht beiläufig ändern

Folgende bereits bestätigte Funktionen/Pfade nicht ohne reproduzierbaren Fehler oder ausdrücklichen Auftrag umbauen:

- Geräteaktivierung / Gerätebindung Server 1.0
- Server-App-Updateweg
- FULL-/DB-/Datei-Backup
- getrennter Restore-Test
- Backup-Retention
- Cloudflare-Cutover auf `tb-helfer-v1`
- Servermonitor + SMTP-Test-/Alarmmail
- Admin-Komplett-Löschen
- Baustellen-/Geräte-Gruppierung nach Firma
- Audit-Nachweis mit Scrollbereich
- `restore-test-db` bleibt vorerst dauerhaft bestehen

Source-Check, Build, Installation und GitHub Actions ohne ausgeführte Steps sind **kein E2E-Nachweis**.

## 4. Aktuell neuer offener Fehler – höchste Priorität

Beim realen Android-Sync erscheint auf dem Handy:

> **Synchronisation unvollständig: Baustellenbaum-Änderungen konnten nicht vollständig übertragen werden.**

Zusätzlich zeigt die Sync-Seite aktuell **Strecken (0)**.

Dieser Fehler wurde erst nach dem Server-Aufräumen/Cutover sichtbar und ist der nächste Prüfpunkt. Nicht vorschnell Server- oder Android-Code ändern: zuerst reproduzieren und anhand Server-/Android-Logs den konkret fehlgeschlagenen Baustellenbaum-Endpunkt bestimmen.

## 5. Sync-Anzeige im Systemstatus

Der Systemstatus zeigt derzeit bei **„Letzter Sync“** noch `—`.

Quellprüfung:
- Systemstatus liest `tb.devices.last_sync_at`.
- Der aktuelle Katalog-`/ack`-Pfad bestätigt nur die Sequenz und setzt `last_sync_at` nicht.
- Deshalb ist `—` aktuell **kein sicherer Beweis für fehlgeschlagenen Sync**.

Erst nach dem realen Baustellenbaum-Syncfehler entscheiden, ob `last_sync_at` serverseitig sauber gepflegt werden soll.

## 6. Dokumentations-Widerspruch

`docs/TB-HELFER_AKTUELLER_STAND.md` und `projektstatus/TB-HELFER_STATUS.md` enthalten noch mehrere ältere Angaben, u. a.:
- System-Automatik/SMTP/Audit noch als E2E offen
- Systemstatus Live/Fehler/Archiv noch als E2E offen
- alter Server 0.7 noch als später abzuschalten

Diese Angaben sind gegenüber dem inzwischen praktisch bestätigten Live-Stand teilweise veraltet.

**Noch nicht geändert:** STATUS.md und STATUS.xlsx wurden in diesem Sicherungsschritt absichtlich nicht aktualisiert.  
Die XLSX-Datei konnte über den GitHub-Connector wegen Binärformat nicht inhaltlich geprüft werden; Konsistenz MD/XLSX ist daher derzeit nicht bestätigt.

## 7. Nächster sinnvoller Schritt im neuen Chat

1. GitHub erneut prüfen (#6/#7 + HEADs).
2. Den Android-Syncfehler **„Baustellenbaum-Änderungen konnten nicht vollständig übertragen werden“** reproduzieren.
3. Zeitgleich Server-API-Protokoll prüfen und den fehlgeschlagenen Request/HTTP-Status identifizieren.
4. Android-Log/Syncstatus danebenlegen.
5. Erst danach entscheiden: Serververtrag, Android-Client oder Datenzustand.
6. Danach Pixel + Poco gemeinsamen E2E-Sync vollständig abhaken.
7. Erst nach erfolgreichem realen Sync die veralteten STATUS-/AKTUELLER_STAND-Angaben mit ausdrücklichem Benutzerauftrag nachziehen.

## 8. Synology-Arbeitsweg bleibt verbindlich

- GitHub Desktop zum Holen des aktuellen Stands
- File Station für Datei-Uploads nach `/volume1/docker/tb-helfer-v1`
- Container Manager für Stop/Recreate/Start
- Container-Terminal nur für notwendige Diagnosebefehle
- kein NAS-SSH als Standardweg
- `.env`, Secrets, DB-/Storage-/Backup-Verzeichnisse niemals beiläufig überschreiben

---

**Checkpoint erstellt:** 23.09.2026  
**Nächster Chat startet bei:** Android-Syncfehler Baustellenbaum nach erfolgreichem Server-/Cloudflare-Cutover.
