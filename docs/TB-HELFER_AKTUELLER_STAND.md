# TB-Helfer – aktueller Projektstand

Stand: 2026-09-14

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen zusätzlich in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. offenen Draft-PR und neuesten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR und danach diesen Stand aktualisieren.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft.
- aktueller bestätigter Android-Gerätestand: **200038**, Source-Commit `8238d509c7914ed3cf192ce30f252d086a00e71c`.
- aktueller Entwicklungsstand im Branch: **Android 200039 – BTB/PDF Logo-/Vorschau-Block**, noch nicht als Gerätetest bestätigt.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.

## WICHTIG – Source-of-Truth wieder vollständig in GitHub
Der zuvor nur lokal vorhandene Android-Stand 200035 → 200038 wurde am 14.09.2026 vollständig in `feature/admin-offline-sync` committed und gepusht.

Damit gilt wieder der normale Entwicklungsablauf:
1. Änderungen direkt im Feature-Branch durchführen und versionieren.
2. Auf dem Windows-PC: GitHub Desktop `Fetch origin` → `Pull origin`.
3. Android Studio öffnen/builden/testen.
4. Keine PowerShell-Patch-Kette mehr als normaler Entwicklungsweg.

Die vorhandenen `.github/scripts/patch_200035...200038*.ps1` bleiben nur als historische/technische Hilfsmittel. Für neue Änderungen nicht darauf aufbauen, solange kein Sonderfall dies ausdrücklich erfordert.

Git-Zeilenenden lokal vereinheitlicht:
```powershell
git config --local core.autocrlf false
git config --local core.eol lf
```
Zusätzlich liegt `.gitattributes` im Branch; die frühere LF/CRLF-Warnung in GitHub Desktop ist beseitigt.

## Android 200039 – BTB/PDF Logo, Kontaktdaten und echte Vorschau
`app/build.gradle.kts` steht im Entwicklungsbranch auf `versionCode = 200039`, `versionName = 1.3`.

Umgesetzt/vorbereitet:
- Firmenlogo im realen BTB-PDF standardmäßig deutlich kleiner und proportional; Seitenverhältnis bleibt erhalten.
- gemeinsamer Renderer für reales BTB-PDF und Vorschau, damit Logo-Größe/Position und Kontaktdaten dieselben Regeln verwenden.
- Firmenname, Firmen-E-Mail und Firmentelefon können unter dem Logo ausgegeben werden.
- Schalter `Kontaktdaten bereits im Logo` unterdrückt die separaten Firmen-E-Mail-/Telefonzeilen.
- oben im PDF: verantwortlicher TB plus persönliche E-Mail und Mobilnummer aus dem persönlichen Nutzerprofil, soweit vorhanden.
- Fußzeile: `Erstellt von: <Nutzer> · <Datum>`.
- unter `Einstellungen → BTB & PDF`: Logo links/rechts/hoch/runter, kleiner/größer, auf Standard zurücksetzen.
- echte A4-PDF-Vorschau wird als PDF erzeugt und im bestehenden App-internen `BtbPdfViewerActivity` geöffnet.
- die Vorschau zeigt zusätzlich das aktuell konfigurierte Foto-Raster; das reale PDF nutzt weiterhin dieselben vorhandenen Rastereinstellungen.
- lokaler Offline-Cache `BtbPdfLogoLayoutSpeicher` sorgt dafür, dass PDF-Erstellung auch ohne Server funktioniert.
- Server-Schema 12 ist vorbereitet für ein firmenweit gespeichertes PDF-Logo-Profil mit Größe, X/Y-Position und den Kontakt-Schaltern.
- `CompanyProfileSync` lädt Schema-12-Einstellungen nur optional; produktive Server ohne Schema 12 behalten den lokalen PDF-Logo-Stand und der übrige Firmenprofil-Sync bleibt nutzbar.

Betroffene/neue Kern-Dateien 200039:
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

### 200039 Build-/CI-Status
Der GitHub-Actions-Lauf für 200039 liefert derzeit **keinen Android-Buildstatus**. Zwei Startversuche des Jobs endeten bereits vor dem ersten Workflow-Schritt: `runner_id=0`, `steps=[]`, kein Checkout, kein Python-Source-Check und kein Gradle-Aufruf. Die Actions-Anzeige `failure` ist deshalb aktuell ein Runner-/Actions-Startproblem und **kein nachgewiesener Quellcode-/Buildfehler**.

Unabhängig davon wurde bei der Source-Prüfung ein tatsächlich veralteter statischer Vertrag gefunden und korrigiert:
- `server/checks/03_verify_schema10_source.py` erwartete noch einen älteren BTB-Dateinamens-Literal.
- der Check erwartet jetzt `timeToken` + `btbToken` + den aktuellen kanonischen BTB-Dateinamen.
- Android-CI führt zusätzlich `server/checks/12_verify_schema12_source.py` aus, sobald ein Runner den Job wieder tatsächlich startet.

Da GitHub Actions derzeit nicht bis zu den Workflow-Schritten kommt, muss der belastbare Android-200039-Build als Nächstes lokal auf dem Windows-PC/Android Studio erfolgen. Bis dieser Build erfolgreich ist, bleibt **200038** der bestätigte Android-Gerätestand.

Noch erforderlich, bevor 200039 als fertig gilt:
- Windows-PC: `Fetch origin` → `Pull origin`, danach Debug-Build in Android Studio/Gradle.
- eventuelle Compiler-/Lintfehler direkt im Feature-Branch beheben.
- Android 200039 als Update installieren, ohne App-Daten zu löschen.
- echte PDF-Vorschau und reales BTB-PDF visuell vergleichen.
- Logo kleiner/größer/verschieben, Kontakt-Schalter und `Kontaktdaten bereits im Logo` prüfen.
- persönliche TB-E-Mail/Mobilnummer und Fußzeile prüfen.
- Foto-Raster in Vorschau und realem PDF gegen dieselbe Einstellung prüfen.
- bestehende Original-/Nachtrag-/Rot-Markierungslogik darf sich nicht ändern.

## Android 200038 – gebaut, installiert und praktisch bestätigt
### Build
Der 200038-Stand wurde lokal erfolgreich gebaut. Die bekannten Deprecated-/Compiler-Warnungen verhindern den Build nicht.

Debug-APK:
`app/build/outputs/apk/debug/app-debug.apk`

### Gerätetest bestätigt
Folgende Punkte wurden auf dem Gerät erfolgreich getestet:
- App läuft als Update ohne Löschen der App-Daten.
- Personal `von/bis` öffnet die grafische 24h-Uhr.
- Geräte/Maschinen `von/bis` öffnet die grafische 24h-Uhr.
- gleichnamige Fotos werden in der BTB-Arbeitenansicht gemeinsam dargestellt, z. B. `Bild 1+5–6`.
- Fotoauswahl zeigt jetzt wesentlich mehr Informationen statt nur Uhrzeit/Checkbox.
- Fotoauswahl zeigt Foto-Nr., Text, Uhrzeit, KM, Firma und vorhandene Zusatzinformationen.
- Checkbox bleibt nicht-destruktiv und steuert nur die Verwendung im aktuellen BTB/PDF.
- Papierkorb öffnet einen Sicherheitsdialog.
- Originalfoto des ersten Abschlusses kann über die BTB-Fotoauswahl nicht endgültig gelöscht werden; nur aus aktuellem BTB/PDF entfernt werden.
- Nachtrag/Nicht-Original kann zusätzlich bewusst dauerhaft gelöscht werden.
- Fotoauswahländerung nach einem Abschluss setzt das Dokument auf Nachbearbeitung/ungültigen Abschluss, ohne das unveränderliche erste Original anzutasten.
- Hauptbildschirm `Aktuelle Baustelle` → `BTB starten/öffnen` → alter BTB lässt sich wieder korrekt öffnen.
- der frühere Fehler `Dieser BTB ist bereits geöffnet`, obwohl fachlich kein BTB aktiv war, ist behoben.
- Dark-Mode-Darstellung der Fotoauswahl wurde verbessert.

## Android 200035–200038 – zusammengefasste fachliche Änderungen
### BTB-Abschluss / Original-Diff
- erster erfolgreicher BTB-Abschluss = unveränderliches Original.
- spätere fachliche Änderungen werden immer gegen dieses erste Original verglichen.
- neue/geänderte fachliche Inhalte rot.
- entfernte Originalinhalte rot als `(entfernt) ...`.
- Kopfdaten Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter bleiben in der PDF-Darstellung schwarz.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- neues Abschluss-PDF ersetzt nur das vorherige aktuelle PDF derselben `btbId`, nicht das Original.

### Fotos
- moderner BTB verwendet strikt Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` gelangen nicht nachträglich über Datum/Uhrzeit in moderne BTBs.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses gehört zum unveränderlichen Original.
- nachträglich hinzugefügte Fotos sind Nachträge und werden gegenüber dem ersten Original markiert.
- gleiche Foto-Texte im selben Firmen-/Ortskontext werden im PDF gemeinsam gruppiert; Zeit/KM als Bereich und Bildnummern als gemeinsame Liste, z. B. `[Bild 3+5]`.
- enthält eine gemeinsame Gruppe einen Nachtrag, bleibt die geänderte Gruppe gegenüber dem ersten Original erkennbar.
- entfernte Originalfoto-Referenzen werden im Folge-PDF nachvollziehbar als entfernt dargestellt.

### Export
- Zeitraum-/Gesamtexport findet moderne Abschluss-PDFs über den kanonischen Finder.
- Zeitraum-ZIP/Gesamt-Archiv enthalten den BTB-Abschluss-PDF.
- DOCX-Kopf enthält Firma, verantwortlichen TB, Schicht und `BTB-ID`.
- moderne BTB-Fotos im Export strikt nach gleicher `btbId`.
- stabile BTB-Token in PDF-/DOCX-Dateinamen verhindern Kollisionen mehrerer BTBs am selben Tag.
- Einzel-BTB-ZIP und MergeSafe-Export nutzen die stabilere BTB-Identität.

### Zeit-/Editor-UX
- neuer BTB: Start/Ende über grafischen 24h-TimePicker.
- Personal `von/bis` über grafischen 24h-TimePicker.
- Geräte/Maschinen `von/bis` über grafischen 24h-TimePicker.
- Zeitwahl bleibt auf 15-Minuten-Raster.

### Reaktivierung alter BTBs
- `inactive_after_completion` schlägt einen stale `.active_btb.json`-Pointer.
- `BtbActiveContext` und `BtbSchnellwechselInstaller` wenden dieselbe Regel an.
- bewusstes Öffnen reaktiviert exakt die gewählte stabile `btbId` und löscht den Inaktivmarker.

## Betroffene Kern-Dateien 200035–200038
Mindestens:
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExportPaket.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEinzelZip.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/MergeSafeBaustellenExport.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt`
- `app/src/main/res/layout/item_btb_pdf_photo_row.xml`
- `app/src/main/res/layout/dialog_schicht.xml`
- `app/src/main/res/layout/fragment_bautagebuch.xml`

## Bekannte Fehler / offene Prüfungen
Aktueller bestätigter Gerätstand 200038 hat keine bekannte Build-Blockade.

200039 ist noch Entwicklungs-/Prüfstand. Offene Punkte:
- lokalen Android-Build und anschließend Gerätetest durchführen.
- GitHub Actions startet aktuell keinen Runner; dies ist getrennt vom lokalen Build zu behandeln.
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos kontrollieren.
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren.
- Zeitraum-ZIP und Gesamt-Archiv nach 200038/200039 nochmals erzeugen und auf PDF/DOCX/Bilder prüfen.
- Zwei-Geräte-/Mehrhelfer-Synchronisation bleibt offen.
- produktive NAS-/Schema-11-/Schema-12-Migration bleibt offen.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; 72 OpenAPI-Pfade, Schema 10.
- in der aktuellen Android-BTB/PDF-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- Schema 12 für firmenweites PDF-Logo-Layout ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor produktiven Schema-11-/Schema-12-Schritten frisches verifiziertes DB-Backup erstellen.

## Verbindliches BTB-Kernmodell
### Identität
- ein fachlicher BTB = dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie Identität.
- mehrere BTBs desselben Teams am selben Kalendertag sind erlaubt.
- Kamera/Foto, BTB-Kopf, Abschluss und Verlauf verwenden denselben kanonischen aktiven `btbId`-Kontext.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- spätere fachliche Änderungen immer gegen dieses erste Original vergleichen.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter sind Kopfdaten und bleiben im PDF schwarz.
- fachliche neue/geänderte Inhalte rot; entfernte Originalinhalte rot als `(entfernt) ...`.
- neues PDF ersetzt das vorige PDF derselben `btbId`, nicht das Original.
- nach erfolgreichem Abschluss aktive `btbId` lösen; abgeschlossenen BTB nur bewusst über Verlauf/Schnellwechsel reaktivieren.
- nach erstem Abschluss Wetter nicht mehr automatisch aktualisieren; spätere Änderung nur bewusst/manuell.

### Fotos
- moderne BTB-Fotos erhalten beim Speichern die aktive `btbId`.
- moderner BTB sieht nur Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` gehören nie später durch Datum/Uhrzeit zu einem modernen BTB.
- Zeitfenster-Fallback nur für echte Legacy-BTBs ohne `btbId`.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses gehört zum unveränderlichen Original.
- jede tatsächliche Änderung der ausgewählten Foto-Menge nach Abschluss macht das aktuelle Abschluss-PDF ungültig/Nachbearbeitung.
- gleiche Foto-Texte im selben Firmen-/Ortskontext dürfen gemeinsam gruppiert werden.

## Teams / Baustellen / Sync – offen/teilweise vorbereitet
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs gemäß Projektregeln baustellenweit.
- Baustellenkarte soll Teamumfang und letzten Sync zeigen.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- Auto-Sync: manuell / stündlich / täglich plus Netzart `nur WLAN` / `nur Mobilfunk` / `beides`.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync bleibt offen.
- serverseitiges Entfernen/Verlassen und Zwei-Geräte-Konflikttests bleiben offen.

## Nutzerprofil / Firma
Praktisch bestätigt (200038):
- Benutzername wird angezeigt.
- persönliche Telefonnummer und persönliche E-Mail bleiben lokal erhalten.
- Firmenprofil bleibt getrennt und serverseitig zentral.
- Firmenlogo kommt zentral vom Server und wird für Geräte derselben Firma gecached.

Schema 11 vorbereitet, noch nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`

Schema 12 vorbereitet, noch nicht produktiv:
- `server/migrations/012_company_pdf_logo_layout.sql`
- `server/checks/11_verify_schema12.sql`
- `server/checks/12_verify_schema12_source.py`
- `server/app_parts/19_part.py`
- `GET /api/v1/company/profile/pdf-logo-layout`
- `PUT /api/v1/company/profile/pdf-logo-layout`

## Spätere Fachfunktionen aus Benutzerplanung
- SKV-Abnahme inkl. GPS.
- BTB auch unter Windows/Laptop bearbeiten; dort ebenfalls Abnahmen und Mängel.
- Spracheingabe.
- vereinfachte Mangelaufnahme: `Was? -> Wo? (aktueller km / km ansagen) -> Was ist los?`.
- `Mein Ordner`: selbst angelegte Ordner mit anderen Nutzern synchronisieren; `nur lesen` oder `lesen & bearbeiten`.
- externe Fotos importieren/einfügen.
- Wetterdarstellung um Niederschlagsmenge `l/m²`/mm ergänzen.
- Aktivierung/QR übernimmt vorhandene Benutzer-/Firmenstammdaten automatisch vom Server.
- Ablauf-/Lizenzhinweis wie `LÄUFT BALD AB – noch 2 Tage`.
- Support-/Gerätekennung und nur maskierten Schlüsselhinweis anzeigen; keine Geheimwerte.
- zentrales Firmenlogo für alle Nutzer derselben Firma.
- Streckendaten/Kilometrierung weiterführen; Originalvermessungsdaten unverändert lassen.

## Nächster sinnvoller Arbeitsschritt
1. Windows-PC: `Fetch origin` → `Pull origin`.
2. Android 200039 lokal bauen. Bei Buildfehlern direkt im Feature-Branch korrigieren und erneut bauen.
3. Nach erfolgreichem Build 200039 als Update installieren und PDF-Vorschau/reales BTB-PDF testen.
4. Erst nach erfolgreichem Gerätetest 200039 als bestätigten Android-Stand markieren.
5. GitHub-Actions-Runnerproblem getrennt klären; die aktuelle `failure`-Anzeige enthält keinen ausgeführten Workflow-Schritt.
6. Produktive Schema-11-/Schema-12-Migration weiterhin getrennt und nur nach frischem verifiziertem Backup.
