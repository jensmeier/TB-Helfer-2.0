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
- letzter relevanter Branch-Commit vor dieser Doku-Aktualisierung: `289c4b61c93e88efcf97a780e0ad6c78faa50439` (`fix: invalidate BTB closure on photo selection changes`).
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.

## WICHTIG – lokaler Windows-PC-Stand
Auf dem Windows-PC liegen weiterhin bewusst **nicht gepushte Sourceänderungen** aus 200035 → 200036 → 200037. Nicht verwerfen/resetten und noch nicht committen/pushen, bis der aktuelle Gerätetest abgeschlossen ist.

Bekannte lokal geänderte Kern-Dateien umfassen mindestens:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExportPaket.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEinzelZip.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/MergeSafeBaustellenExport.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt`
- `app/src/main/res/layout/dialog_schicht.xml`
- `app/src/main/res/layout/fragment_bautagebuch.xml`
- `app/build.gradle.kts`

Lokale Git-Einstellung wurde auf LF vereinheitlicht:
```powershell
git config --local core.autocrlf false
git config --local core.eol lf
```
Zusätzlich liegt `.gitattributes` im Branch. Die frühere LF/CRLF-Warnung in GitHub Desktop ist damit beseitigt.

## Android 200036 – gebaut und wesentliche Exportkorrekturen bestätigt
Verwendete Skripte:
- `.github/scripts/patch_200036.ps1`
- `.github/scripts/patch_200036_fix.ps1`
- `.github/scripts/patch_200036_continue.ps1`

Erfolgreicher Build:
- `BUILD SUCCESSFUL in 2m 13s`
- 38 Tasks ausgeführt.
- Debug-APK: `app\build\outputs\apk\debug\app-debug.apk`
- bekannte Compiler-/Deprecated-Warnungen verhindern den Build nicht.

200036 korrigiert/ergänzt:
- moderner Abschluss-PDF-Finder über `BtbAbschlussPdfFinder`;
- Zeitraum-/Gesamtexport enthält Abschluss-PDF;
- DOCX-Kopf enthält Firma, verantwortlichen TB, Schicht und `BTB-ID`;
- moderne BTB-Fotos im Export strikt nach gleicher `btbId`;
- stabile BTB-Token in PDF-/DOCX-Dateinamen für mehrere BTBs am selben Tag;
- Einzel-BTB-ZIP mit vollständigerem Dokumentkopf;
- MergeSafe-Export mit stabilem BTB-Suffix;
- Start/Ende beim neuen BTB über grafischen `TimePickerDialog`;
- Abschlussdialog erklärt unveränderliches Original und rote Nachbearbeitung;
- bessere Dunkelmodus-Lesbarkeit der Karte `Neuen BTB starten`.

Gerätetest/Export bestätigt:
- Abschluss-PDF ist im Export vorhanden.
- DOCX enthält Firma, verantwortlichen TB, Schicht und BTB-ID.
- rote fachliche Änderungen und Foto-Nachträge sind im PDF sichtbar.

## Android 200037 – Patch erfolgreich angewendet, Build/Installation praktisch erreicht, Gerätetest noch nicht komplett
Der robuste lokale Patch ist:
- `.github/scripts/patch_200037_v2.ps1`

Erfolgreicher Patchlauf auf dem Windows-PC:
```text
200037 v2: local 200036/200037 base confirmed
PATCH_200037_V2_FERTIG
Changed: BautagebuchFragment.kt, BautagebuchPdf.kt, build.gradle.kts
```

200037 ändert:
- Personal `von/bis` über grafische 24h-Uhr;
- Geräte/Maschinen `von/bis` über grafische 24h-Uhr;
- Auswahl bleibt auf 15-Minuten-Raster;
- gilt beim Start eines neuen BTB und in den Personal-/Maschinen-Sektionen des laufenden BTB;
- Fotos mit gleichem Text/Firma/Ort werden im PDF gemeinsam gruppiert, auch wenn eines erst nach dem ersten Abschluss hinzukommt;
- gemeinsame Bildnummern als Bereich/Liste, z. B. `[Bild 3+5]` oder `[Bild 1–3]`;
- enthält eine Gruppe einen Nachtrag, wird die gemeinsame geänderte Fotozeile rot;
- lokale Version `versionCode = 200037`.

Praktisch auf Gerät sichtbar:
- die BTB-Arbeitenansicht gruppiert gleichnamige Fotos bereits kompakt, z. B. `Test1.0 Bild 1+5–6`;
- der 200037-Stand ist auf dem Gerät lauffähig genug für weitere Gerätetests.

Noch offen für 200037:
- grafische Uhr bei Personal und Geräte/Maschinen explizit abhaken;
- neues PDF nach 200037 erzeugen und die gemeinsame Bildreferenz kontrollieren;
- Export-PDF/DOCX nochmals gegen 200036-Regeln prüfen.

## Neue Befunde aus Gerätetest 14.09.2026
### Fotoauswahl ist zu informationsarm
Die bisherige `BtbPdfAuswahlFragment`-Zeile zeigt nur:
- Vorschaubild,
- Uhrzeit,
- Checkbox.

Für reale Baustellen ist das zu wenig. Beim aktuellen Test gibt es z. B. mehrere Fotos mit `Test1.0`; im Export ist ein konkretes Foto über Uhrzeit, KM, Firma und Text eindeutig zuordenbar. Deshalb muss die Fotoauswahl mindestens anzeigen:
- laufende Foto-Nummer innerhalb der Auswahl,
- BTB/PDF-Text bzw. Foto-Kurztext,
- Uhrzeit,
- KM bzw. KM-Bereich,
- Firma,
- soweit vorhanden Objekt/Bereich,
- nach erstem Abschluss Kennzeichnung `Originalfoto` oder `Nachtrag`.

### Foto entfernen/löschen – sichere Semantik
Die vorhandene Checkbox bleibt **nicht-destruktiv**: sie bestimmt nur, ob ein Foto im aktuellen BTB/PDF verwendet wird.

Für eine Lösch-Aktion gilt:
- Sicherheitsdialog ist Pflicht.
- Foto aus dem unveränderlichen ersten Abschluss: **kein permanentes Löschen über die BTB-Fotoauswahl**; nur `Aus BTB entfernen`. So bleibt das erste Original nachvollziehbar.
- Foto/Nachtrag außerhalb des ersten Originals: `Nur aus BTB/PDF` oder bewusst `Foto löschen`.
- Änderung der ausgewählten Foto-Menge nach einem Abschluss macht das aktuelle Abschlussdokument ungültig und setzt den BTB auf Nachbearbeitung; das erste Original bleibt unverändert.

### Alten BTB aus `BTB starten/öffnen` öffnen – Fehlerursache gefunden
Reproduzierter Fehler:
- Hauptbildschirm/`Aktuelle Baustelle` → `BTB starten/öffnen` → alten BTB wählen;
- obwohl BTB-Seite `Noch kein BTB` zeigt, erscheint `Dieser BTB ist bereits geöffnet`.

Ursache im Bestand:
- `BtbSchnellwechselInstaller` vergleicht den gewählten BTB mit dem rohen `.active_btb.json`-Pointer;
- nach erfolgreichem Abschluss existiert zusätzlich der fachlich maßgebliche `inactive_after_completion`-Marker;
- ein alter/staler aktiver Pointer kann deshalb fälschlich als wirklich aktiver BTB gelten.

Lösung:
- `inactive_after_completion` schlägt einen stale active pointer;
- `BtbSchnellwechselInstaller` behandelt diesen BTB dann nicht als aktuell;
- `BtbActiveContext` wendet dieselbe Regel zentral an;
- bewusstes Öffnen aktiviert danach exakt die gewählte `btbId` und löscht den Inaktivmarker wie bisher vorgesehen.

## Android 200038 – vorbereitet, noch lokal anzuwenden/builden/testen
Versionierte Skripte:
- `.github/scripts/patch_200038.ps1` – Basisfunktion;
- `.github/scripts/patch_200038_v2.ps1` – **zu verwendender finaler Einstieg**, führt die Basis aus und ergänzt sichere Abschluss-Invalidierung bei Fotoauswahländerungen.

200038 soll ändern:
- `BtbPdfAuswahlFragment.kt`: reichhaltige Fotozeile mit Foto-Nr., Text, Zeit, KM, Firma, Objekt/Bereich, Original/Nachtrag;
- Fotoauswahl im Dunkelmodus ohne hart erzwungene schwarze Abschnittsüberschrift;
- Checkbox bleibt `im BTB/PDF verwenden`;
- Papierkorb öffnet Sicherheitsdialog;
- Originalfoto kann nur aus dem aktuellen BTB/PDF entfernt werden;
- Nicht-Original/Nachtrag kann wahlweise nur aus BTB/PDF entfernt oder über den bestehenden `FotoRepository`-Löschweg gelöscht werden;
- Text-/Zeit-/KM-Overrides werden nach echter Nicht-Original-Fotolöschung bereinigt;
- `BtbDokumentVersionierung`: neue externe Invalidierungsfunktion für Fotoauswahländerungen, weil diese Auswahl außerhalb der BTB-JSON gespeichert wird;
- eine Fotoauswahländerung nach erstem Abschluss löscht das aktuelle Abschlussdokument, setzt Abschlussmetadaten auf ungültig/Nachbearbeitung, **ohne das erste Original anzutasten**;
- `BtbSchnellwechselInstaller`: stale active pointer nach Abschluss nicht mehr als `bereits geöffnet` behandeln;
- `BtbActiveContext`: `inactive_after_completion` schlägt stale pointer zentral;
- `item_btb_pdf_photo_row.xml`: neue Metadatenzeilen + Papierkorb;
- `app/build.gradle.kts`: lokal auf `versionCode = 200038`.

Schutz im Patch:
- 200038 setzt `versionCode 200037/200038` voraus;
- Ziel-Dateien, die nicht zu den bisherigen lokalen Patch-Dateien gehören, werden bei unerwarteten lokalen Änderungen nicht blind überschrieben;
- PowerShell-Textvergleiche sind literal-sicher; kein `.Split`-Fehler wie beim frühen 200037-Versuch.

## Nächster verbindlicher Schritt: Android 200038
1. GitHub Desktop: `Fetch origin`, danach falls angeboten `Pull origin`; lokale Sourceänderungen behalten.
2. **Nur den finalen 200038-v2-Patch ausführen:**
```powershell
powershell -ExecutionPolicy Bypass -File .\.github\scripts\patch_200038_v2.ps1
```
3. Erwartet:
```text
PATCH_200038_FERTIG
...
PATCH_200038_V2_FERTIG
```
4. Bauen:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat :app:assembleDebug --rerun-tasks
```
5. APK als **Update** installieren; App-Daten nicht löschen.
6. Gerätetest:
   - Fotoauswahl zeigt Foto-Nummer, Text, Uhrzeit, KM, Firma und Zusatzinfos;
   - Checkbox entfernt Foto nur aus aktuellem BTB/PDF;
   - Originalfoto-Papierkorb bietet nur sichere Entfernung aus aktuellem BTB/PDF;
   - Nachtrag/Nicht-Original bietet zusätzlich echte Löschung;
   - Fotoauswahländerung nach Abschluss macht alten Abschluss ungültig/Nachbearbeitung;
   - `Aktuelle Baustelle` → `BTB starten/öffnen` → alter BTB lässt sich wirklich öffnen, ohne falsches `bereits geöffnet`;
   - danach PDF-Gruppierung/Rot-Markierung und Tages-ZIP erneut prüfen.
7. Erst bei erfolgreichem Test lokale Sourceänderungen committen/pushen.

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

## Praktisch bestätigte Grundlage Android 200034
- Kamera-Guard ohne aktiven BTB funktioniert.
- `Nur Baustellenfoto` / `BTB starten/öffnen` funktioniert grundsätzlich.
- allgemeines Baustellenfoto ohne `btbId` läuft nicht in einen modernen BTB hinein.
- abgeschlossenen BTB über Verlauf erneut bearbeiten funktioniert grundsätzlich; 200038 korrigiert zusätzlich den stale-Pointer-Fehler im Startmenü-Schnellweg.
- Nachtmodus `Dunkel/Hell/System` bleibt gespeichert.
- nach erfolgreichem `BTB Abschluss` wird die aktive `btbId` fachlich gelöst.
- bewusstes `BTB bearbeiten` reaktiviert exakt dieselbe `btbId`.
- moderne BTB-Fotos werden strikt über dieselbe `btbId` zugeordnet.
- Nutzerprofil lokal mit persönlicher Telefonnummer/E-Mail funktioniert.
- App-interner PDF-Viewer funktioniert.

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
- nachträglich hinzugefügte BTB-Fotos werden im Anhang und in fachlichen Fotozeilen als Nachtrag markiert.
- jede tatsächliche Änderung der ausgewählten Foto-Menge nach Abschluss macht das aktuelle Abschluss-PDF ungültig/Nachbearbeitung.
- gleiche Foto-Texte im selben Firmen-/Ortskontext dürfen im PDF gemeinsam gruppiert werden; Zeit/KM als Bereich und Bildnummern als gemeinsame Liste.

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
Android 200038-v2 lokal anwenden, bauen und als Update installieren. Danach Fotoauswahl/Löschschutz, Abschluss-Invalidierung, altes-BTB-Öffnen sowie PDF-/ZIP-Regeln testen. Erst nach erfolgreichem Gerätetest lokale Sourceänderungen committen/pushen; produktiven NAS weiterhin nicht anfassen.
