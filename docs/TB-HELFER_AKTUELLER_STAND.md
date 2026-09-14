# TB-Helfer – aktueller Projektstand

Stand: 2026-09-14

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor Änderungen immer zuerst prüfen: Projektregeln, diesen Stand, aktiven Branch, Draft-PR und neuesten Commit. `main` bleibt stabil.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- Android **200039** ist jetzt gebaut, als Update installiert und auf dem Handy praktisch bestätigt.
- 200039-Feature-Commit: `c612eff8af2c928cd4896664f878fa1562e8d99f` – `feat: add BTB PDF logo layout and preview`.
- CI-/Doku-Folgecommits: `6ebc20e564f263db771879fa0aad46d2eba80643`, `a5c6567003a59b129d26ec133bc2d4ff1a19c907`.
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test und den offenen Sync-/Serverpunkten.

## Entwicklungsablauf
Der Source of Truth liegt vollständig in GitHub. Normaler Ablauf:
1. Änderungen direkt im Feature-Branch versionieren.
2. Windows: GitHub Desktop `Fetch origin` → `Pull origin`.
3. Android Studio öffnen/builden/testen.
4. Keine PowerShell-Patch-Kette als normaler Entwicklungsweg.

Die vorhandenen `.github/scripts/patch_200035...200038*.ps1` bleiben nur historische/technische Hilfsmittel.

## Android 200039 – BTB/PDF Logo, Kontaktdaten und Vorschau
`app/build.gradle.kts`: `versionCode = 200039`, `versionName = 1.3`.

### Umgesetzt
- Firmenlogo im realen BTB-PDF deutlich kleiner und proportional; Seitenverhältnis bleibt erhalten.
- Gemeinsamer Header-Renderer für reales BTB-PDF und PDF-Vorschau.
- Firmenname, Firmen-E-Mail und Firmentelefon unter dem Logo optional.
- Schalter `Kontaktdaten sind bereits im Logo enthalten` verhindert doppelte Firmen-E-Mail-/Telefon-Ausgabe.
- oben im PDF: verantwortlicher TB sowie persönliche E-Mail und Mobilnummer, soweit im persönlichen Profil vorhanden.
- Fußzeile: `Erstellt von: <Nutzer> · <Datum>` plus Seitenzahl.
- `Einstellungen → BTB & PDF`: Logo links/rechts/hoch/runter verschieben, kleiner/größer, Standard wiederherstellen.
- echte A4-PDF-Vorschau im vorhandenen App-internen PDF-Viewer.
- Foto-Raster wird in der Vorschau dargestellt und folgt der aktuellen Rastereinstellung.
- lokaler Offline-Cache `BtbPdfLogoLayoutSpeicher` für identische PDF-Erzeugung ohne Netz.
- Server-Schema 12 + API für ein später firmenweit gespeichertes PDF-Logo-Profil vorbereitet.
- `CompanyProfileSync` behandelt Schema 12 optional; produktive ältere Server blockieren den restlichen Firmenprofil-Sync nicht.

### Gerätetest 200039 – bestätigt
Vom Benutzer auf dem Handy bestätigt:
- Android 200039 ist installiert und läuft als Update.
- `Einstellungen → BTB & PDF` funktioniert.
- `PDF-Vorschau öffnen` funktioniert im internen PDF-Viewer.
- Logo kleiner/größer sowie links/rechts/hoch/runter wirkt in der Vorschau korrekt.
- Firmenname/Firmen-E-Mail/Firmentelefon werden wie konfiguriert dargestellt.
- `Kontaktdaten sind bereits im Logo enthalten` verhindert die doppelte Kontaktanzeige wie vorgesehen.
- TB-Name, persönliche E-Mail und Mobilnummer im PDF-Kopf passen.
- Fußzeile `Erstellt von: <Nutzer> · Datum` und Seitenzahl passen.
- Foto-Raster-Änderung wird in der PDF-Vorschau korrekt sichtbar.
- reales BTB-PDF und PDF-Vorschau passen hinsichtlich Logo/Kopf/Kontaktdaten zusammen.

### Betroffene Kern-Dateien 200039
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/einstellungen/BtbPdfLogoLayoutSpeicher.kt`
- `app/src/main/java/de/tbhelfer/server/CompanyPdfLogoApi.kt`
- `app/src/main/java/de/tbhelfer/server/CompanyProfileSync.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfHeaderRenderer.kt`
- `app/src/main/java/de/tbhelfer/ui/einstellungen/btbpdf/BtbPdfEinstellungenFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/einstellungen/btbpdf/BtbPdfLayoutVorschau.kt`
- `app/src/main/res/layout/fragment_btb_pdf_einstellungen.xml`
- `server/app_parts/19_part.py`
- `server/migrations/012_company_pdf_logo_layout.sql`
- `server/checks/11_verify_schema12.sql`
- `server/checks/12_verify_schema12_source.py`
- `.github/workflows/android.yml`
- `server/checks/03_verify_schema10_source.py`

## CI-Status
GitHub Actions liefert aktuell weiterhin keinen belastbaren Android-Buildlauf: die letzten Jobs endeten vor dem ersten Workflow-Schritt mit `runner_id=0` und `steps=[]`. Damit wurden weder Checkout noch Source-Checks noch Gradle gestartet. Das ist kein nachgewiesener Android-Codefehler.

Unabhängig davon wurde ein veralteter statischer Schema-10-Vertrag auf die aktuelle `timeToken`-/`btbToken`-Dokumentidentität angepasst und Schema-12-Source-Check in den Workflow aufgenommen.

Der lokale Android-Build/Installation und der praktische Gerätetest von 200039 sind erfolgreich und damit für den aktuellen App-Stand maßgeblich.

## Android 200035–200038 – weiter gültige Kernregeln
### BTB / Original
- ein fachlicher BTB = stabile `btbId`; Datum/Uhrzeit/Tag/Nacht sind Attribute.
- erster erfolgreicher Abschluss = unveränderliches Original.
- spätere fachliche Abweichungen zum ersten Original rot; entfernte Originalinhalte rot als `(entfernt) ...`.
- Kopfdaten wie Datum/Arbeitszeit, Personal, Geräte/Maschinen und Wetter bleiben im PDF schwarz.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF; neues aktuelles PDF ersetzt nicht das Original.
- erfolgreicher Abschluss löst die aktive `btbId`; Wiederöffnung nur bewusst über Verlauf/Schnellwechsel.

### Fotos
- moderner BTB verwendet strikt Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` rutschen nicht später über Datum/Uhrzeit in moderne BTBs.
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original.
- Änderungen der ausgewählten Foto-Menge nach Abschluss invalidieren das aktuelle Abschlussdokument und setzen Nachbearbeitung.
- Originalfoto des ersten Abschlusses kann in der BTB-Fotoauswahl nicht endgültig gelöscht werden; nur aus aktuellem BTB/PDF entfernt werden.
- Nicht-Original/Nachtrag kann bewusst dauerhaft als lokaler Foto-Datensatz gelöscht werden.
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert dargestellt werden.

## Noch offene Prüfungen im bestehenden BTB-Bereich
- Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals gezielt kontrollieren.
- Rot-Markierung bei gemischter Original-/Nachtrag-Fotogruppe kontrollieren.
- Zeitraum-ZIP und Gesamt-Archiv nach 200039 nochmals auf PDF/DOCX/Bilder prüfen.
- Zwei-Geräte-/Mehrhelfer-Synchronisation bleibt offen.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth für produktive Projektdaten.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- in der Android-200039-Runde wurden **keine produktiven NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber nicht produktiv migriert.
- Schema 12 für firmenweites PDF-Logo-Layout ist im Branch vorbereitet, aber nicht produktiv migriert.
- vor produktiven Schema-11-/Schema-12-Schritten frisches verifiziertes DB-Backup erstellen.

## Nutzerprofil / Firma
- Firmenstammdaten und Firmenlogo sind zentral/firmenbezogen; Logo wird auf Geräten gecacht.
- persönliche Telefonnummer und persönliche E-Mail bleiben getrennt von Firmenkontakten.
- persönliche Kontaktdaten sind lokal speicherbar; Schema 11 soll später denselben Stand serverseitig synchronisieren.
- PDF-Logo-Layout ist in 200039 lokal voll nutzbar; Schema 12 soll es später firmenweit synchronisieren.

## Nächster sinnvoller Arbeitsschritt
1. **200039 als bestätigten Android-Stand beibehalten.**
2. Nächster Handy-Block: externe Fotos in aktiven BTB importieren sowie Foto-Vorschau/-Bearbeitung/-Entfernen weiter verbessern.
3. Danach vorhandenes eingebranntes Foto-Overlay (Baustelle/Strecke/Gleis/KM/GPS/Datum/Uhrzeit/Bereich) praktisch prüfen und optisch verbessern.
4. Anschließend Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Gruppierung prüfen/aufräumen.
5. Wetter um Niederschlagsmenge mm/l/m² ergänzen und vereinfachte Mängelaufnahme weiterführen.
6. Produktive Schema-11-/Schema-12-Migration getrennt und nur nach frischem verifiziertem Backup durchführen.
