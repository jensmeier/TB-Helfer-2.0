# TB-Helfer – Prüfstand / Chat-Übergabe 2026-09-23

> Zweck: kurzer, belastbarer Übergabestand vor neuem Chat.  
> GitHub bleibt Source of Truth. Dieser Prüfstand ersetzt weder Projektregeln noch STATUS.md/.xlsx.

## 1. GitHub-Stand

- Repository: `jensmeier/TB-Helfer-2.0`
- SERVER-Branch: `feature/tb-server-1.0`
- SERVER-Quellstand vor dieser Prüfstand-Aktualisierung: `3045b965651794abbd4f83fdee375c81116774dc`
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

## 4a. Neuer Laufzeitfehler nach Synology-Neuerstellung

- **Worker startet nicht:** Synology meldet `Bind mount failed: .../restore-target-storage-v1 does not exist`. Ursache ist der veraltete Restore-Target-Mount im normalen Synology-Compose nach bewusstem Entfernen des Restore-Targets.
- Quellfix: Restore-Target vollständig aus normalem Worker entfernt; `restore-test-db` bleibt ausdrücklich als persistenter eigener Service bestehen. Restore-to-Target nur noch optionales Wartungs-Overlay.
- **cloudflared startet nicht selbstständig:** Quellfix `restart: always`, kein API-Health-`depends_on` mehr. Tunnel startet unabhängig und retryt den Origin.
- Deployment/E2E dieser beiden Fixes ist noch offen. DB, Storage, Backups und Secrets nicht verändern/löschen.

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

## 5. Neuer Arbeitsblock 1 → 2 → 4 → 3

- **1 Benutzer-Gültigkeit:** Serverstand ist auf Synology deployt; Presets 3/6/12 Monate + manuelles Datum, kein Uhrzeitfeld, Europe/Berlin Folgetag 00:01; fachlicher E2E noch offen.
- **2 Portal:** Synology-.env mit `TB_V1_PORTAL_PUBLIC_BASE_URL=https://www.tb-helfer.de` ist gesetzt und API mit aktualisierter Compose neu erstellt. App/API bleibt `api-v1.tb-helfer.de`. Als Nächstes Cloudflare-Public-Hostname `www.tb-helfer.de` praktisch anlegen und extern testen.
- **4 QR-Einladung:** Ursache im Android-Code nachgewiesen. Direkter Baustellen-Scanner verarbeitet nur Legacy-`tbhelfer://invite?code=TBI-...`; moderner Server liefert `tbhelfer://worksite-invite?token=...`. Fix gehört ins ANDROID-Projekt; SERVER-`app/**` unverändert.
- **3 Strecken/Bahnhöfe:** neue Adminseiten auf vorhandenem globalen Katalog sind auf Synology deployt; fachlicher E2E offen; keine neue Datenbank/Migration.

`STATUS.xlsx` bleibt bis zu einem konfliktfreien Zugriff auf den aktuellen GitHub-Binärstand unverändert.

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

1. **ERLEDIGT:** aktuellen SERVER-Stand auf Synology übernommen.
2. **ERLEDIGT:** lokale `.env` enthält `TB_V1_PORTAL_PUBLIC_BASE_URL=https://www.tb-helfer.de`.
3. **ERLEDIGT:** API-Container mit aktualisierter Compose neu erstellt.
4. Benutzer-Gültigkeit praktisch testen (3/6/12 Monate + manuell).
5. Im Cloudflare-Tunnel `tb-helfer-nas` Public Hostname `www.tb-helfer.de` auf denselben internen Dienst wie die funktionierende `api-v1`-Route legen; `https://www.tb-helfer.de/` und Portal-Aktivierungslink testen.
6. QR-Fix im ANDROID-Projekt umsetzen und modernen Baustellen-QR praktisch scannen/einlösen.
7. Danach Strecken-/Bahnhöfe-Admin praktisch prüfen.
8. Anschließend den noch offenen Baustellenbaum-Sync (`POST .../nodes`) sowie Pixel↔Poco-E2E fortsetzen.
9. `STATUS.xlsx` erst nach konfliktfreiem Zugriff auf den aktuellen GitHub-Binärstand konsolidieren.

## 8. Synology-Arbeitsweg bleibt verbindlich

- GitHub Desktop zum Holen des aktuellen Stands
- File Station für Datei-Uploads nach `/volume1/docker/tb-helfer-v1`
- Container Manager für Stop/Recreate/Start
- Container-Terminal nur für notwendige Diagnosebefehle
- kein NAS-SSH als Standardweg
- `.env`, Secrets, DB-/Storage-/Backup-Verzeichnisse niemals beiläufig überschreiben

---

**Checkpoint erstellt:** 23.09.2026  
**Aktueller nächster Schritt:** aktualisierte Synology-Compose mit Worker-/cloudflared-Fix übernehmen, Worker + cloudflared neu erstellen/starten und Laufzeit prüfen; erst danach `www.tb-helfer.de` weiter testen.
