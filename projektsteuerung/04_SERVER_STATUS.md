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
