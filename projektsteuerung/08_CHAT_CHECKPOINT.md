# TB-HELFER 2.0 – CHAT CHECKPOINT

Stand: 2026-09-26

Letzter gesicherter Arbeitsblock: **Projektsteuerung vereinheitlicht und verifiziert**.

- Zentrale: `projektsteuerung/`
- alte Top-Level-Ordner `projektstatus/`, `releases/`, `uebergaben/` entfernt
- branch-spezifische Projektregeln nach dem Umzug kontrolliert und auf denselben Einstieg korrigiert
- kein Android-/Server-Fachcode in diesem Schritt geändert
- Android-Fachstand vor Steuerungsumbau: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
- Server-Fachstand vor Steuerungsumbau: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
- Android 1.3.19 / 200069: Build/Install/E2E offen
- Server 1.0.0-dev: Migration 015 Runtime-E2E offen
- nächster gemeinsamer Technikpunkt: IMG-001 Bildtransport
- weiterer P0: Poco bekommt keine Daten

Neuer Chat: direkt `projektsteuerung/00_START_HIER.md` lesen und Live-HEADs prüfen.

## Arbeitsentscheidung / Testnachtrag 26.09.2026

- Delta-Wait-Kapazitätsteiltest praktisch: 255 getrennte Portal-Sessions, 255/255 erfolgreich, 0 Fehler.
- Testsession-Reste kontrolliert bereinigt; abschließend 0 aktive Python-Test-Sessions.
- Das ist **nicht** der vollständige 15×15-E2E.
- Neue verbindliche Reihenfolge: zuerst Haupt-TB-Handy ↔ Server/Portal, danach HELPER/Poco, danach weitere Fach-E2Es.
- Vollständigen Lasttest erst nach funktionaler Stabilität und nach geplantem 8-GB-RAM-Ausbau bzw. NAS-Wechsel erneut durchführen.
- Keine Performanceänderungen auf Verdacht.
