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

Nächste Schritte: IMG-001 Audit → Migration 015 Runtime-E2E → 15×15 Lasttest.

Historischer Tracker: `projektsteuerung/archiv/SERVER_STATUS_ALT_2026-09-26.md`


## IMG-001 Quellaudit
Status: **SERVER-RENDITIONS VORHANDEN / ANDROID-NUTZUNG FEHLT / RUNTIME-E2E OFFEN**

- Upload-Complete verschiebt die empfangene Datei unverändert in den Storage (`os.replace(temp_path, final_path)`); der Server komprimiert einen APP-Upload nicht heimlich.
- Damit speichert der Server heute ein Volloriginal nur deshalb, weil Android aktuell das Volloriginal sendet.
- `image_renditions.py` stellt bereits speicherbegrenzt `thumb` (320×320, JPEG 72) und `preview` (1280×1280, JPEG 82) bereit.
- `/api/v1/worksites/{id}/files` und `GET /api/v1/files/{id}` liefern die Rendition-URLs.
- Portal-Quelldateien dürfen gemäß Vertrag erhalten bleiben; mobile Geräte sollen daraus Renditions beziehen.
- Für den Kernfehler ist nach Quellaudit keine Server-Ersatzlösung vorgesehen. Zuerst Android-Upload/Download korrigieren.
- Serverseitig bleiben Migration 015 und reale Rendition-/Metadaten-E2E offen.
