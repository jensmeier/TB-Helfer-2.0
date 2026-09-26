# IMG-001 – Bildtransport Quellaudit

Stand: 2026-09-26

Repository: `jensmeier/TB-Helfer-2.0`

Geprüfte Live-Stände vor Auditdokumentation:
- ANDROID `feature/tb-android-2.0` @ `57f5e18639aff0d49c0d46086855487e0a7353c0`
- SERVER `feature/tb-server-1.0` @ `b5ceb6f455028d8ebeef6766cb523fd1ef7e76fd`

Verbindliche Grundlage:
- `projektsteuerung/05_ANDROID_SERVER_VERTRAG.md`
- `projektsteuerung/vertraege/TB-SYNC_15x15_ZIELMODELL.md`
- `projektsteuerung/vertraege/TB-SERVER_ANDROID_SYNC_VERTRAG_2026-09-25.md`

## Ergebnis

**IMG-001 ist im aktuellen Quellstand NICHT vertragskonform.**

Kein `app/**`- oder `server/**`-Fachcode wurde bei diesem Audit geändert.

## 1. Android Handy → Server

### BTB-Fotos
`BtbTeamMediaSync.kt`:
- bildet die Uploaddatei direkt mit `File(foto.dateipfad)`;
- übergibt genau diese Datei an `CollabSyncRepository.uploadAttachment(... file = file ...)`.

Damit wird keine separate 1920-px-Sync-Datei erzeugt.

### Mangel-/Abnahmefotos
`CollabLocalBridge.kt`:
- Mangel: `File(mf.foto.dateipfad)`;
- Abnahmefoto: `File(foto.dateipfad)`;
- diese Dateien gehen ebenfalls direkt an `uploadAttachment()`.

### Tatsächlicher Upload
`CollabSyncRepository.kt`:
- SHA-256 wird über die übergebene Datei gebildet;
- bei Server 1.0 wird dieselbe Datei an `Server1FileApi.uploadLinkedFile()` weitergereicht;
- Legacy-Fallback liest sogar `file.readBytes()`.

`Server1FileApi.kt`:
- `expected_bytes = file.length()`;
- `expected_sha256 = sha256`;
- `uploadRemainingChunks(... file = file ...)`;
- `FileInputStream(file)` überträgt genau die übergebene Datei.

**Befund:** Das lokale Foto-Original ist derzeit die Online-Uploaddatei.

## 2. Bereits vorhandene lokale Android-Thumbnails

`FotoSpeicher.kt` erzeugt beim Speichern bereits:
- Thumbnail neben dem Original;
- max. 512 px;
- JPEG Qualität 82.

`FotoErfassenFragment.kt` ruft `FotoSpeicher.erstelleThumbnailWennMoeglich(File(dateipfad))` auf.

**Befund:** Eine passende kleine lokale Bildableitung existiert bereits, ist aber nicht in den Online-Sync eingebunden. Eine separate ca. 1920-px-Sync-Datei existiert im geprüften Uploadpfad nicht.

## 3. Server-Empfang und Storage

`server/tbserver/files.py`:
- nach vollständigem Upload wird die temporäre Datei per `os.replace(temp_path, final_path)` unverändert in den Storage verschoben;
- `byte_size` und SHA-256 entsprechen der tatsächlich empfangenen Datei;
- `source = APP` für Geräteuploads, `PORTAL` für Portaluploads.

**Befund:** Der Server vergrößert/ersetzt die Datei nicht und komprimiert APP-Uploads nicht automatisch. Wenn Android das Volloriginal sendet, liegt deshalb auch dieses Volloriginal auf dem Server. Wenn Android künftig nur die kleine Sync-Datei sendet, speichert der Server entsprechend nur diese APP-Sync-Datei.

## 4. Server-Renditions

`server/tbserver/image_renditions.py` ist vorhanden:
- `thumb`: max. 320×320, JPEG Qualität 72;
- `preview`: max. 1280×1280, JPEG Qualität 82;
- EXIF-Orientierung wird berücksichtigt;
- Rendern ist auf zwei gleichzeitige Dekodierungen begrenzt;
- Renditions werden gecacht.

`server/tbserver/files.py` liefert:
- in Dateilisten und Dateimetadaten `renditions`;
- `GET /api/v1/files/{file_id}/rendition/thumb`;
- `GET /api/v1/files/{file_id}/rendition/preview`.

**Befund:** Die notwendige serverseitige kleine Auslieferung ist bereits vorhanden.

## 5. Server → Android / Handy → anderes Handy

`Server1FileApi.kt`:
- `LinkedFile` enthält keine Rendition-URLs;
- im Dateiclient gibt es keine Rendition-Downloadmethode;
- `downloadContent()` ruft ausschließlich `/api/v1/files/{id}/content` auf.

`CollabSyncRepository.synchronizeServer1Attachments()`:
- lädt fehlende sichtbare Attachments unmittelbar;
- nutzt dafür `downloadContent()`;
- speichert die vollständige Antwortdatei lokal.

`Server1AndroidSyncCoordinator` ruft diesen Attachment-Sync beim normalen Server-1.0-Abgleich auf.

**Befund:** Android lädt aktuell die serverseitige Quelldatei statt `preview/thumb`. Bei heutigen APP-Uploads bedeutet das sogar das zuvor hochgeladene Volloriginal; bei Portal-Uploads kann es die volle Portal-Quelldatei sein.

## 6. Metadaten

### BTB
`BtbTeamMediaSync` sendet:
- Aufnahmezeit;
- GPS Lat/Lon;
- strukturierte `photoMetadata`.

Aber:
- `gpsAccuracyM = null`;
- `FotoEntity` besitzt kein Feld für GPS-Genauigkeit.

### Mangel/Abnahme
Die geprüften Uploadaufrufe in `CollabLocalBridge` übergeben Datei/MIME/API, aber keine gleichwertigen Werte für:
- Aufnahmezeit;
- GPS Lat/Lon/Genauigkeit;
- strukturierte Foto-Metadaten.

**Befund:** Metadatenvertrag ist nur teilweise erfüllt.

## 7. SOLL/IST

| Teil | SOLL | IST | Status |
|---|---|---|---|
| Handy-Original | bleibt nur am Aufnahmegerät | wird aktuell hochgeladen | FEHLER |
| Handy→Server | kleine Sync-Datei | lokale Originaldatei | FEHLER |
| Thumbnail | kleine Ableitung verfügbar | lokal 512 px vorhanden, Sync nutzt sie nicht | TEILWEISE |
| Server-Storage APP | nur mobile Sync-Datei nötig | speichert unverändert das von Android gesendete Original | FOLGEFEHLER |
| Server-Renditions | kleine Auslieferung | thumb/preview vorhanden | VORHANDEN |
| Portal→Handy | Rendition | Android lädt /content | FEHLER |
| Handy→anderes Handy | kleine Version/Rendition | Android lädt /content | FEHLER |
| BTB-Metadaten | strukturiert | größtenteils vorhanden, GPS-Genauigkeit fehlt | TEILWEISE |
| Mangel/Abnahme-Metadaten | strukturiert | im Uploadpfad unvollständig | FEHLER |

## 8. Getrennte Zuständigkeit

### ANDROID – erforderlich
1. Vor Foto-Upload eine separate Sync-JPEG-Datei erzeugen:
   - längste Kante Richtwert ca. 1920 px;
   - JPEG ca. 80–82 %;
   - nie hochskalieren;
   - temporär/cachebar, niemals das Original überschreiben.
2. Upload-Hash/Größe auf diese Sync-Datei beziehen.
3. Für mobile Downloads:
   - `preview` als normale Bilddatei;
   - `thumb` für kleine Listen-/Galerieansichten;
   - `/content` nicht als automatischen Standard für Bilder.
4. Metadaten vollständig halten; insbesondere GPS-Genauigkeit persistent machen.
5. Mangel-/Abnahmefoto-Uploads mit strukturierten Metadaten ausstatten.
6. Bestehende PDF-/Nichtbild-Dateien weiterhin über `/content` behandeln.

### SERVER – aktuell kein Ersatzumbau
1. Bestehende Rendition-Endpunkte und Migration 015 praktisch deployen/verifizieren.
2. Sicherstellen, dass APP-Syncdatei + Metadaten sauber gespeichert/ausgeliefert werden.
3. Portal-Quelldateien weiterhin erhalten; mobile Geräte nutzen Renditions.
4. Nur wenn der Android-Vertrag damit nicht erfüllbar ist, einen separaten Serverauftrag dokumentieren.

## 9. E2E nach Implementierung

Mit realen Dateien messen:
1. Pixel Original: Pixelabmessungen + Bytegröße vor Upload.
2. Server APP-Datei: muss deutlich kleiner sein und max. Zielkante einhalten.
3. Server thumb/preview: Abmessungen + Bytegröße prüfen.
4. Poco: empfangene Datei darf nicht das Pixel-Volloriginal sein.
5. Portal-Upload: Poco/Pixel erhalten Rendition, nicht volle Portalquelle.
6. Metadaten: Aufnahmezeit, GPS, Genauigkeit, Typ, Firma, KM/Objekt prüfen.
7. Originaldatei auf Pixel per SHA-256 vor/nach Sync identisch.
