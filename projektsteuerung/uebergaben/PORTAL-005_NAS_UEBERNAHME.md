# PORTAL-005 – Portalumbau und Synology-Übernahme
Stand: 11.10.2026. Geprüfter Programmstand: `424b5d4701b603e35c51b38d5b32b55f707cf2c7`.
Branch: `feature/tb-server-1.0`, PR #6 bleibt Draft.

## Enthalten
- Navigation: Baustellen, Bilderübersicht, Einstellungen; Portal-Einstieg führt zu den Baustellen.
- Baustelle: Übersicht, Bautagesberichte mit Datum-/Statusfilter, Unterbaustellen, Mängel und Abnahmen, Team und Einladungen sowie Link zu den Baustellenbildern. Formulare öffnen sich bei Bedarf. JSON-Bedienfelder und technische Rohdaten sind ausgeblendet; vorhandene Zusatzdaten bleiben beim Bearbeiten erhalten.
- Einladungen: Bereich ausdrücklich als gesamte Baustelle oder begrenzten Bereich/Kilometer auswählen. Rechte werden weiterhin serverseitig geprüft.
- Bilderübersicht: Auswahl der Baustelle, Datum, Bereich und Suche; kleine Vorschaubilder, größere verkleinerte Ansicht beim Öffnen, automatische Aktualisierung für die ausgewählte Baustelle. Auch Mangel-/Abnahmebilder öffnen die Preview.
- Einstellungen: Profil, Firmenkontakte, E-Mail-Bestätigung und Passwortänderung.
- Passwort vergessen: bestätigte E-Mail, neutraler Antworttext, gehashte einmalige 30-Minuten-Links und Anfragelimits. E-Mail-Änderungen entwerten Bestätigung und offene Links. Passwortwechsel widerruft Portalsitzungen. Administrator kann für ein aktives vorhandenes Portalkonto einen Ersatzlink erstellen; kein automatisches Verschicken dieses Adminlinks.
- Öffentliche Startseite: aggregierte Streckenkilometer, Bahnhöfe mit Daten, Strecken und Baustellen. Testbaustellen und Archiv zählen mit, gelöschte Baustellen nicht. Kilometerüberlappungen je Strecke zählen einmal. Keine Firmen-, Nutzer-, Standort- oder Speicherinformationen öffentlich. Zahlen werden höchstens alle 15 Minuten neu berechnet.
- Helfer: Aus einem Bericht ausgeschlossene Helfer können ihre Teilnahme nicht selbst reaktivieren. Aus der Baustelle entfernen sperrt Mitgliedschaft und Teamzugriff, widerruft offene persönliche Einladungen und beendet Teilnahme an offenen Berichten. Historische Beiträge bleiben erhalten. Abgeschlossene Berichte bekommen beim Lesen keine neuen Teilnehmer.
- Migration 023 ergänzt ein fehlendes files.view im Standardprofil RESPONSIBLE_TB; explizite individuelle Verbote bleiben maßgeblich.
- Neue Kontotabellen sind im Backup-Domänenvertrag berücksichtigt. Offene Kontolinks werden beim Wiederherstellen entwertet.
- Android und die bestätigte ZIP-Regel sind unverändert: eigene lokale Originale, Helfer-/Portalbilder verkleinert, ZIP vom Handy zum PC.

## Nachweise und Grenzen
- [Portal-Integration und Chromium-Prüfung](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/38153731624): PASS mit tatsächlich ausgeführten Steps. PostgreSQL/PostGIS 17, Migration 001–023 und wiederholter Migrationslauf, Kontolinks/Passwort/Sessions, Admin-Ersatz, Helferentfernung mit echter Rechteprüfung und Idempotenz, historische Teilnehmer, Backup-Tabellenvertrag, Testbaustellenzählung und Jinja-Rendering.
- Chromium mit gerenderten Produktionsvorlagen und simulierten API-Antworten bei 1440 und 390 Pixeln: Reiter, Filter, Formulare, Einladungseingaben, Bilderfilter/Deduplizierung/Preview, Einstellungen, öffentliche Seite; keine JavaScript-Fehler oder automatische Originalbildabfrage. Browserbilder wurden zusätzlich visuell geprüft. Dies ist kein NAS-Livetest.
- [Snapshot-/Rechte-/255-Client-HTTP-Gate](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/38153731635): entsprechender Job PASS. Der separate Foundation-Job stoppt am unveränderten Datei-/Chunkupload-Marker FILE_LINK_TARGET_NOT_FOUND; keine pauschal grüne Gesamtsuite behaupten.
- [Restore-Controller und Datengrenzen](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/38153279846): beide Jobs PASS mit dem neuen Kontotabellenvertrag. Spätere Änderungen betreffen ausschließlich Portalvorlagen/Browsercheck.
- SMTP-Versand im Test ersetzt, keine echten Nachrichten gesendet. Tatsächlicher Mailempfang und NAS-Installation stehen aus. Android-CI nicht als bestanden werten, Androidbranch unverändert.
- Die vom Nutzer bestätigte NAS-Backupabnahme bleibt bestehen; keine Wiederholung des gesamten Restore-Testblocks angefordert.

## Übernahme über GitHub Desktop / File Station
1. In GitHub Desktop den Branch `feature/tb-server-1.0` aktualisieren (Fetch/Pull).
2. Den bisherigen Softwarestand passend zu vorhandenen Sicherungen behalten. Während keiner laufenden Wiederherstellung aktualisieren.
3. Im Synology Container Manager das Projekt `tb-helfer-v1` stoppen.
4. Über File Station die folgenden drei Quellordner aus dem lokalen Repository in den bestehenden Projektordner übernehmen (Dateien ersetzen/ergänzen, nichts löschen):

| PC im Repository | NAS-Ziel |
| --- | --- |
| `server/tbserver/` | `/volume1/docker/tb-helfer-v1/tbserver/` |
| `server/templates/` | `/volume1/docker/tb-helfer-v1/templates/` |
| `server/v1_migrations/` | `/volume1/docker/tb-helfer-v1/v1_migrations/` |

Nur diese Softwareordner übertragen. `.env`, Compose, Datenbank-, Storage-, Recovery- und Backupverzeichnisse nicht überschreiben oder löschen. Keine neue Abhängigkeit im Serverimage, keine Composeänderung für diesen Block erforderlich.

5. Projekt über Container Manager neu erstellen und starten, damit der Dienst `migrate` erneut ausgeführt wird. Danach müssen Migrationen **23 / 23**, letzte Migration **23 / 23**, ausstehend **keine** erscheinen.
6. Browser mit Strg+F5 neu laden. Unter `https://www.tb-helfer.de/` liegt die öffentliche Seite, unter `/portal/v1` das Benutzerportal.
7. In Einstellungen eigene E-Mail speichern und bestätigen. Dafür müssen die bereits vorhandenen SMTP-Einstellungen stimmen und `TB_V1_PORTAL_PUBLIC_BASE_URL` die HTTPS-Portalbasis `https://www.tb-helfer.de` enthalten. Keine Geheimnisse hier veröffentlichen. Ohne gültige Konfiguration zeigt die Wiederherstellung einen Hinweis.

## Kurze fachliche Abnahme nach Installation
- Einloggen → Baustellen → Reiter/Filter öffnen; Bilder prüfen.
- Öffentliche Seite: reale Gesamtzahlen, inklusive Testbaustellen, keine persönlichen Angaben.
- E-Mail bestätigen und Passwort vergessen mit dem eigenen Testkonto prüfen; Link nur einmal nutzbar.
- Den noch aktiven Helfer bei Bedarf bewusst über „Aus Baustellenteam entfernen“ entfernen. Der Fix entfernt keine bestehende Person automatisch und kann frühere Offline-/Restore-Ursachen nicht rückwirkend rekonstruieren.

Migration 023 ist eine echte Schemaerweiterung. Ältere Backups benötigen weiterhin den zu ihnen passenden Software-/Schemastand; kein stiller Downgrade durch Kopieren alter Programmdateien. Vorhandene Archive bleiben unberührt. Die neue Software ist noch nicht auf dem NAS installiert.
