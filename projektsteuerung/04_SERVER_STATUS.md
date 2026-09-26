# TB-HELFER 2.0 – SERVER STATUS

Stand: 2026-09-26
Branch: `feature/tb-server-1.0`
Fach-HEAD vor Projektsteuerungscommit: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
Draft-PR: #6
Version: **1.0.0-dev**
Quellmigrationen: **001–015**

- Migration 014 Teilnehmer-Schichtstatus praktisch bestätigt.
- Migration 015 strukturierte Foto-Metadaten: Quellstand vorhanden, Deployment/Runtime-E2E offen.
- Rollen-/Schichtvertrag, FINALIZED-read-only und Teamlead-Finalize verbindlich.
- Delta-Wait/15×15-Quellstand vorhanden; Last-E2E offen.
- Finalize-OOM/502-Fix praktisch bestätigt und geschützt.

Bildpfad: Mobile Aufnahmen laut 15×15 nur kleine Sync-Version + Thumbnail + Metadaten. Portal-Uploads dürfen serverseitige Quelldatei bleiben. Vor Serveränderung zuerst Android-Upload auditieren.

Nächste Schritte: Hauptgerät↔Server/Portal E2E → HELPER/Poco → IMG-001 Android-Implementierung → Migration 015/Rendition-E2E; Gesamtlast später gemäß E2E-PRIO-001.

Historischer Tracker: `projektsteuerung/archiv/SERVER_STATUS_ALT_2026-09-26.md`

## Praxisnachtrag – Delta-Wait 255 / neue Testreihenfolge

Praktischer Staging-Kapazitätsteiltest für `/api/v1/sync/changes/wait`:
- 255 getrennte Portal-Sessions vorbereitet;
- 255/255 Requests erfolgreich, 0 Fehler;
- Wall 16,43 s, Median 14,28 s, Maximum 15,79 s;
- Testsession-Cleanup meldete zunächst 253/255; über gezielte DB-Kontrolle wurden 7 noch aktive Python-Test-Sessions gefunden und ausschließlich diese Test-Sessions auf `revoked_at` gesetzt;
- anschließende Kontrolle: 0 aktive Python-Test-Sessions.

Status: **KAPAZITÄTSTEILTEST BESTANDEN**, aber **kein vollständiger 15×15-E2E**.

Neue Server-Reihenfolge:
1. verantwortliches TB-Handy ↔ Server/Portal funktional E2E;
2. HELPER/Poco-Syncfehler reproduzierbar eingrenzen und beheben;
3. danach weitere gemeinsame Fach-E2Es;
4. vollständige Lastwiederholung nach 8-GB-RAM-Ausbau bzw. auf neuem NAS.

Keine Server-Performanceänderung aus diesem Test ableiten, solange kein reproduzierbarer funktionaler Engpass vorliegt.


## IMG-001 Quellaudit
Status: **SERVER-RENDITIONS VORHANDEN / ANDROID-NUTZUNG FEHLT**

- Upload-Complete speichert empfangene Bytes unverändert; kein heimlicher Server-Downscale von APP-Uploads.
- APP-Volloriginale auf dem NAS sind aktuell Folge des Android-Uploads.
- `image_renditions.py`: `thumb` 320×320/JPEG72 und `preview` 1280×1280/JPEG82, gecacht und speicherbegrenzt.
- Dateiliste und Metadaten liefern Rendition-URLs.
- Für den Kernfehler ist kein Server-Ersatzumbau vorgesehen. Android soll kleine Sync-Dateien senden und mobile Renditions nutzen.
- Migration 015/Rendition-/Metadaten-Runtime-E2E bleibt offen.

## BTB-SYNC-001 Praxisfehler 26.09
- Pixel hat neuen BTB 26.09 angelegt.
- Portal zeigt weiterhin nur bis 25.09.
- Poco sieht 26.09 kurz, anschließend „kein BTB vorhanden“.
- Serverdiagnose hat Vorrang: Team-`active_btb_id`, BTB-Status/Teamzuordnung, `client_entity_key`, Versionen, Change-Feed und Team-BTB-API prüfen. Keine DB-/API-Änderung auf Verdacht.

### BTB-SYNC-001 Diagnose – kein Serverfix vor Versionsgleichstand
DB-Diagnose zeigt Pixel mit Android-App `1.3.4` und Poco mit `1.3.19`. Change-Feed enthält mehrere Updates desselben BTB bis mindestens Version 19. Server-/DB-Änderungen bleiben gesperrt, bis der Pixel auf dem aktuellen Android-Zielstand getestet wird.

### Geräte-Metadaten app_version sind Aktivierungs-Snapshot
Quellprüfung:
- `/api/v1/device/login` schreibt `app_version` und `app_build` in `tb.devices`.
- `current_device_user` aktualisiert bei normalen Requests nur `last_seen_at`.
- Nach einem normalen APK-Update ohne Neuaktivierung bleibt daher die alte App-Version in `tb.devices`.

Folge: `tb.devices.app_version` darf aktuell nicht als Runtime-Versionsnachweis für E2E verwendet werden. Kein Serverfix daraus ohne separaten Auftrag.

### BTB-SYNC-001 – Server-Existenz bestätigt / Update-Serie
- 26.09-BTB `ba2144b7-9ede-4f53-abdc-1accd172e875` existiert serverseitig.
- Change-Feed zeigt in kurzer Folge Updates bis mindestens Version 19.
- Kein Nachweis eines fehlenden Pixel-Uploads; Ursache liegt nach aktuellem Stand hinter dem Server-Eingang bzw. in Lifecycle/Read-Modell.
- `active_btb_id`, Teamzuordnung, Status und Teilnehmerzustände müssen noch direkt für diesen BTB geprüft werden.

### BTB-SYNC-001 – DB-Lifecycle bestätigt

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

Kein Server-DB-Fix auf Verdacht.

### BTB-SYNC-001 Portal-Read bestätigt
Portal-Neuladen zeigt den 26.09-BTB korrekt als ACTIVE und verlinkt denselben `active_btb_id`. Kein weiterer Server-/Portal-Fix aus diesem Befund.
