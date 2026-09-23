# TB-Helfer – Prüfstand / Chat-Übergabe 2026-09-23

> Zweck: kurzer, belastbarer Übergabestand vor neuem Chat.  
> GitHub bleibt Source of Truth. Dieser Prüfstand ersetzt weder Projektregeln noch STATUS.md/.xlsx.

## 1. GitHub-Stand

- Repository: `jensmeier/TB-Helfer-2.0`
- SERVER-Branch: `feature/tb-server-1.0`
- SERVER-Quellfix-HEAD vor Dokuaktualisierung: `909d970797a0c74aa6d60d5fdd34406b5bf77b93`
- SERVER-Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- ANDROID-Branch: `feature/tb-android-2.0`
- ANDROID-HEAD: `3ea67aa1542a47f4057dca38014a5e73f1e6717f`
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

## 4. Aktueller Sync-/Portalfehlerstand

Der Android-Baustellenbaumfehler wurde reproduziert und serverseitig eingegrenzt:

- `POST /api/v1/worksites/{id}/nodes` -> HTTP 500;
- Trace: `psycopg.ProgrammingError: cannot adapt type 'dict'`;
- Ursache: JSONB-`payload` wurde in `worksites.py` als rohes Python-`dict` an Psycopg übergeben;
- Quellfix `3100c080…`, Regression-Check `2dcf83ad…`, auf Synology eingespielt;
- **Android-E2E noch offen:** erfolgreicher `POST .../nodes` muss im echten Handy-Sync noch bestätigt werden.

Zusätzlich wurde ein unabhängiger Portalfehler reproduziert:

- `GET /api/v1/worksites/{id}/media` -> HTTP 500 in `files.py`;
- Ursache: SQL verwendete dreimal `users.full_name`, tatsächlich existiert `display_name`;
- Quellfix `817c0b5d…`, Regression-Check `909d9707…`;
- **praktisch bestätigt:** Nach Deployment laden Struktur, Teams, Mitglieder und Mängel; beim Testmangel sind **8 Fotos** im Portal sichtbar. Dieser Medien-500er ist erledigt und geschützt.

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

**Aktualisiert:** `docs/TB-HELFER_AKTUELLER_STAND.md` und `projektstatus/TB-HELFER_STATUS.md` enthalten den neuen 23.09.-Fehler-/Fixstand.  
`STATUS.xlsx` wurde bewusst noch nicht überschrieben: Die aktuelle GitHub-XLSX ist neuer als die verfügbare bearbeitbare Library-Kopie; ein Blind-Overwrite wäre nach Projektregel unzulässig.

## 7. Nächster sinnvoller Schritt

1. Auf dem Android-Gerät erneut **Daten vom NAS -> Neu laden** ausführen.
2. Im API-Protokoll nach `nodes` filtern.
3. Prüfen, dass der bisherige `POST .../nodes -> 500` nicht mehr auftritt und Create-Mutationen erfolgreich (201) sind.
4. Wenn der Handy-Sync vollständig meldet: Baustellenbaum-Fix als praktisch bestätigt markieren.
5. Danach Pixel -> Poco gemeinsamen Baustellen-/Mängel-/Foto-Sync testen.
6. Erst anschließend `Letzter Sync`/`last_sync_at` als eigenen Anzeigepunkt korrigieren, falls weiterhin nötig.
7. `STATUS.xlsx` erst nach konfliktfreiem Zugriff auf den aktuellen GitHub-Binärstand konsolidieren.

## 8. Synology-Arbeitsweg bleibt verbindlich

- GitHub Desktop zum Holen des aktuellen Stands
- File Station für Datei-Uploads nach `/volume1/docker/tb-helfer-v1`
- Container Manager für Stop/Recreate/Start
- Container-Terminal nur für notwendige Diagnosebefehle
- kein NAS-SSH als Standardweg
- `.env`, Secrets, DB-/Storage-/Backup-Verzeichnisse niemals beiläufig überschreiben

---

**Checkpoint erstellt:** 23.09.2026  
**Aktueller nächster Schritt:** Android `Neu laden` und erfolgreichen Baustellenbaum-POST im API-Log bestätigen; Portal-Medienpfad ist bereits praktisch bestätigt.
