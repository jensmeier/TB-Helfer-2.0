# PORTAL-007 – öffentliche Bestandskacheln

Stand: 11.10.2026. Runtime: 3146c4dff297a26d68afdbab9a8c86e58f5b1824 auf feature/tb-server-1.0.

Zusätzliche öffentliche Kennzahl „km mit belastbarer KM“ aus known_km_length_m; Screenshotbestand123,98km. Erklärung unter den Kacheln entfernt. Fünf Kacheln passen auf große Bildschirme und umbrechen auf schmalen. Admin unverändert; Testbaustellen zählen weiter.

## Übernahme
1. In GitHub Desktop feature/tb-server-1.0 aktualisieren.
2. API im Synology Container Manager stoppen.
3. Über File Station unter /volume1/docker/tb-helfer-v1 diese zwei Dateien ersetzen:
   - server/tbserver/public_overview.py → tbserver/public_overview.py
   - server/templates/v1_public_home.html → templates/v1_public_home.html
4. API starten und öffentliche Seite mit Strg+F5 aktualisieren.

Keine Migration erforderlich. .env, Daten, Storage und Backups unverändert lassen. Keine NAS-Installation durch Agent durchgeführt.

## Prüfung
Portal-CI [38178439695](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/38178439695) erfolgreich: PostgreSQL/API/Jinja und Chromium1440/390. NAS-Abnahme dieser Änderung offen.
