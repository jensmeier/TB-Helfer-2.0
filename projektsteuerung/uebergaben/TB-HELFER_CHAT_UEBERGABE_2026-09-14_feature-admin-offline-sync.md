# TB-Helfer – Chat-Übergabe 2026-09-14

Diese Datei ist eine zusätzliche Übergabehilfe. Maßgeblich bleiben GitHub-Code, `docs/TB-HELFER_PROJEKTREGELN.md` und `docs/TB-HELFER_AKTUELLER_STAND.md`.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- aktiver Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- PR: offen, Draft, mergeable
- `main` stabil; keine aktuelle Android-BTB-Arbeit direkt auf `main`.
- letzter Branch-Commit vor dieser Übergabe-Datei: `fae6994e2210be02440acb6b9f4eb289890ccf81` (`docs: hand off local Android 200035 build state`).

## Wichtigster Übergabepunkt: lokaler PC-Stand weicht bewusst vom Remote-Code ab
Auf dem Benutzer-PC wurde der versionierte Patch `.github/scripts/patch_200035.ps1` erfolgreich ausgeführt.

Lokale uncommittete Dateien in GitHub Desktop:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt`
- `app/build.gradle.kts`

Lokaler Stand:
- `versionCode = 200035`
- `versionName = 1.3`
- Patchausgabe: `BautagebuchPdf.kt: PATCH_200035_OK`, `build.gradle.kts: versionCode 200035 gesetzt`, `PATCH_200035_FERTIG`.
- erneuter Patchlauf ist idempotent und meldet `200035 bereits angewendet`.

Der Remote-Branch enthält die reproduzierbaren Patchskripte:
- `.github/scripts/patch_200035.py`
- `.github/scripts/patch_200035.ps1`

Die gepatchte Kotlin-Datei selbst war beim Chatwechsel noch **nicht** per GitHub Desktop committet/gepusht.

## Lokaler Build 200035 – bestätigt
Android Studios `Generate APKs` hatte zunächst keine neue Debug-APK erzeugt; vorhandene APK blieb auf Zeitstempel 12:00:57.

Daraufhin direkter Build im Android-Studio-Terminal:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat :app:assembleDebug --rerun-tasks
```

Ergebnis:
- `BUILD SUCCESSFUL in 2m 3s`
- 38 Tasks executed
- APK: `app\build\outputs\apk\debug\app-debug.apk`
- neuer Zeitstempel: `14.09.2026 12:48:19`
- Buildwarnungen vorhanden (bestehende Deprecated-/Kotlin-Warnungen), kein Buildfehler.

**Noch nicht bestätigt:** Installation/Gerätetest genau dieser 12:48:19-APK.

## Warum 200035 gebaut wurde
200034 war praktisch erfolgreich, aber zwei Original-Diff-Probleme blieben:
1. nach erstem Abschluss neu hinzugefügtes Foto war im Fotoanhang rot, die dynamische Fotozeile/Bildreferenz unter Punkt 4 jedoch schwarz;
2. aus dem Original entfernte Foto-/fachliche Inhalte konnten in dynamischen Fotozeilen lautlos verschwinden statt rot `(entfernt) ...` dokumentiert zu werden.

200035 patcht `BautagebuchPdf.kt` dafür:
- Fotoauswahl moderner BTBs strikt nach `btbId`;
- Foto-ID-Menge des ersten Abschlusses als Originalbasis;
- neue Fotozeilen `forceRed`;
- Original-/Nachtragsgruppen bei gleichem Text getrennt;
- fachliche entfernte Zeilen rot;
- entfernte Original-Fotozuordnungen als rote `(entfernt) ...`-Änderungen.

## Achtung zu letzten Screenshots im alten Chat
Screenshots, die nach dem Anwenden des Source-Patches, aber vor dem erfolgreichen 12:48-Build aufgenommen wurden, liefen noch mit der **alten APK von 12:00**. Sie dürfen nicht als echter 200035-Gerätetest gewertet werden.

## 200034 praktisch bestätigt
- Startmenü-Kamera-Guard ohne aktiven BTB funktioniert.
- Auswahl `Nur Baustellenfoto` / `BTB starten/öffnen` funktioniert.
- allgemeines Baustellenfoto ohne `btbId` läuft nicht in modernen BTB hinein.
- BTB aus Verlauf wieder öffnen funktioniert.
- Nachtmodus `Dunkel/Hell/System` bleibt gespeichert.
- nach erfolgreichem Abschluss wird aktive `btbId` gelöst -> `Noch kein BTB`.
- erneut geöffneter BTB behält dieselbe `btbId` und kann wieder abgeschlossen werden.
- moderne Fotos werden nach `btbId` zugeordnet.
- Nutzerprofil lokal funktioniert.
- interner PDF-Viewer funktioniert.

## Aktuelles verbindliches BTB-Modell
- ein BTB = stabile dauerhafte `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, keine Identität.
- erster erfolgreicher Abschluss = unveränderliches Original.
- spätere fachliche Änderungen immer gegen dieses Original vergleichen.
- Kopfdaten Datum/Arbeitszeit, Personal/Firmen, Geräte/Maschinen, Wetter bleiben im PDF schwarz.
- nach erstem Abschluss Wetter nicht mehr automatisch aktualisieren.
- fachliche neue/geänderte Inhalte rot.
- gelöschte Originalinhalte rot `(entfernt) ...`.
- neues Abschluss-PDF ersetzt nur das vorherige PDF, nie das Original.
- nach Abschluss aktiven BTB lösen.
- Nachbearbeitung nur durch bewusstes Öffnen desselben BTB aus Verlauf.
- moderne BTB-Fotos nur mit exakt derselben `btbId`; allgemeines Baustellenfoto bleibt außerhalb.
- Foto-ID-Menge des ersten Abschlusses ist Teil des Originals.

## Letzte neue Anforderungen aus dem alten Chat – noch offen
Diese Punkte kamen direkt vor dem Chatwechsel hinzu und sollen nach echtem 200035-Gerätetest als nächster kleiner Patch bearbeitet werden:

### 1. Nachtmodus – Dialog `Neuen BTB starten`
Im Dunkelmodus sind Beschriftungen/Untertexte teilweise kaum lesbar. Nur Kontrast/Theme-Farben korrigieren, kein großes Redesign.

### 2. Hinweis nach erstem `BTB Abschluss`
Benutzer möchte einen klaren Hinweis, sinngemäß:
- erster Abschluss ist jetzt das Original;
- spätere fachliche Änderungen werden im Folge-PDF rot dokumentiert.
Zusätzlich wurde vorgeschlagen, dort ggf. direkt eine ZIP-Senden-Auswahl anzubieten. Das ist als gewünschte UI-Idee festzuhalten; genaue Gestaltung kann im nächsten Chat kompakt entschieden/umgesetzt werden.

### 3. Foto-Gruppierung im PDF
Wenn mehrere Fotos dieselben fachlichen Daten haben, gruppiert der Abschnittseditor bereits sinnvoll, z. B.:
`12:13–12:16 ... [Bild 2–3]`.
Im PDF werden solche Fotos noch unnötig getrennt dargestellt. Das PDF soll dieselbe Gruppierung (Zeitspanne + Bildbereich) verwenden.

### 4. Uhrzeit beim neuen BTB
Beim Dialog `Neuen BTB starten` soll Start-/Endzeit über die bekannte grafische Uhr/TimePicker-Auswahl gesetzt werden, nicht über die alte Dropdown-Auswahl.

### 5. Weitere bekannte PDF-/Export-Restpunkte
- Strukturüberschriften sollen schwarz bleiben; nur echte Abweichungen rot.
- Einzel-BTB-DOCX braucht langfristig dieselbe Original-/Rotlogik wie PDF.

## Nächster Gerätetest – zuerst ausführen
1. Im neuen Chat zuerst GitHub/Regeln/aktuellen Stand/PR prüfen.
2. Dann diese Übergabe nur ergänzend lesen.
3. APK von `12:48:19` als Update installieren; **App-Daten nicht löschen**.
4. Echten 200035-Test möglichst mit neuer Testbaustelle:
   - neuen BTB starten;
   - Text + mindestens 2 Fotos;
   - ersten `BTB Abschluss`;
   - zurück -> `Noch kein BTB`;
   - denselben BTB aus Verlauf wieder öffnen;
   - weiteres Foto/Text hinzufügen;
   - erneut abschließen;
   - neues Foto im Anhang rot UND zugehörige Fotozeile im Hauptteil rot;
   - Originaleintrag entfernen -> im Folge-PDF rot `(entfernt) ...`;
   - ggf. Originalfotozuordnung entfernen -> rote Entfernt-Dokumentation statt Verschwinden.
5. Wenn Gerätetest passt: in GitHub Desktop die zwei lokalen Dateien committen und pushen.
6. Danach `docs/TB-HELFER_AKTUELLER_STAND.md` auf `200035 getestet` aktualisieren.
7. Erst dann nächsten UI-Patch beginnen.

## GitHub-Desktop-Hinweis
Beim Chatwechsel zeigte GitHub Desktop genau **2 changed files** (lokaler 200035-Patch). Nicht versehentlich verwerfen/resetten. Diese Änderungen entsprechen dem erfolgreich gebauten 12:48-Stand.

## Server / NAS
- produktiv unverändert: API 0.7.0, PostgreSQL Schema 10.
- Schema 11 vorbereitet, nicht produktiv.
- keine NAS-/DB-Änderung in der aktuellen BTB-Runde.
- vor produktiver Schema-Migration frisches verifiziertes Backup.

## Spätere große offene Blöcke
- vollständiger Baustellen-/Unterbaustellen-Sync NAS <-> Geräte.
- Mehrgeräte/Helfer/Team-Sync und Konflikte.
- serverseitiges Entfernen/Verlassen.
- Mängel/Abnahmen-Sync.
- Streckendaten/Kilometrierung/berechnete Abschnitte.
- Windows/Laptop-BTB-/Mängel-/Abnahmenbearbeitung.
- SKV-Abnahme inkl. GPS.
- Spracheingabe.
- vereinfachte Mangelaufnahme `Was? -> Wo? -> Was ist los?`.
- `Mein Ordner` mit Freigaben lesen / lesen+bearbeiten.
- externe Fotos importieren.
- Niederschlagsmenge mm/l/m².
- Ablauf-/Lizenzhinweise, maskierter Support-Key-Hinweis.

## Sicherheit
Keine Secrets, `.env`-Inhalte, vollständigen Keys, Tokens oder private Zertifikate in GitHub/Chat/Übergaben übernehmen.
