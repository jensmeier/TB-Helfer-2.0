# TB-HELFER 2.0 – CHAT CHECKPOINT

Stand: 2026-09-26

Letzter gesicherter Arbeitsblock: **IMG-001 Bildtransport vollständig im Quellstand auditiert**.

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


## IMG-001 Auditbefund
- Android-Live-HEAD vor Auditdokumentation: `57f5e18639aff0d49c0d46086855487e0a7353c0`.
- Server-Live-HEAD bei Audit: `b5ceb6f455028d8ebeef6766cb523fd1ef7e76fd`.
- Android lädt BTB-/Mangel-/Abnahmefotos aktuell aus dem lokalen Originalpfad hoch.
- Server speichert empfangene Bytes unverändert; APP-Volloriginal auf NAS ist daher Folge des Android-Uploads.
- Server-Renditions existieren: `thumb` 320 px / `preview` 1280 px.
- Android-Dateiclient kennt/nutzt die Renditions nicht und lädt fehlende Bilder über `/content`.
- Lokales Android-Thumbnail 512 px/Qualität 82 existiert bereits, ist aber nicht in den Online-Sync eingebunden.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.
- Detailnachweis: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.
- Keine Fachcodeänderung in diesem Arbeitsblock.
- Nächster Fachblock: Android-seitige IMG-001-Korrektur erst auf Basis dieses gesicherten Vertrags; anschließend Build und realer Größen-/Pixel-/Poco-/Portal-E2E.
