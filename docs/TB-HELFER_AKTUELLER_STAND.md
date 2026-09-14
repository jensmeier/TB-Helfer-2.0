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
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable.
- aktueller bestätigter Android-Source-Commit: `8238d509c7914ed3cf192ce30f252d086a00e71c` – `Android 200038: BTB-Fotoauswahl und Wiederöffnung`.
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

## Android 200038 – gebaut, installiert und praktisch bestätigt
`app/build.gradle.kts` steht auf `versionCode = 200038`, `versionName = 1.3`.

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
Aktuell keine bekannte Build-Blockade in Android 200038.

Noch gezielt erneut zu prüfen, bevor die BTB-Runde als vollständig abgeschlossen gilt:
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach 200038 nochmals erzeugen und auf PDF/DOCX/Bilder prüfen;
- Zwei-Geräte-/Mehrhelfer-Synchronisation bleibt offen;
- produktive NAS-/Schema-11-Migration bleibt offen.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; 72 OpenAPI-Pfade, Schema 10.
- in der aktuellen Android-BTB-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor produktivem Schema-11-Schritt frisches verifiziertes DB-Backup erstellen.

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
Praktisch bestätigt:
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
1. 200038 bleibt aktueller bestätigter Android-Stand.
2. Noch PDF-Gruppierung/Rot-Markierung und Zeitraum-/Gesamt-ZIP abschließend prüfen.
3. Danach nächste gewünschte App-Funktion direkt im Source des Feature-Branches umsetzen – ohne normale Patch-Skript-Kette.
4. Bei größeren Sync-/Serveränderungen weiterhin zuerst Bestand/Projektregeln prüfen; produktiven NAS ohne Backup/Freigabe nicht anfassen.
