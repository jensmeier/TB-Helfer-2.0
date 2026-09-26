# IMG-001 – Bildtransport Quellaudit

Stand: 2026-09-26

Geprüfte Stände vor Audit:
- ANDROID `feature/tb-android-2.0` @ `57f5e18639aff0d49c0d46086855487e0a7353c0`
- SERVER `feature/tb-server-1.0` @ `b5ceb6f455028d8ebeef6766cb523fd1ef7e76fd`

## Ergebnis
**IMG-001 ist im aktuellen Quellstand nicht vertragskonform.**

### Handy → Server
- BTB: `BtbTeamMediaSync` nutzt `File(foto.dateipfad)`.
- Mangel/Abnahme: `CollabLocalBridge` nutzt ebenfalls die lokale Foto-Datei.
- `CollabSyncRepository.uploadAttachment()` hasht und übergibt dieselbe Datei.
- `Server1FileApi.uploadLinkedFile()` setzt `expected_bytes = file.length()` und streamt diese Datei.

Damit wird derzeit das Handy-Original hochgeladen.

### Lokale Android-Ableitung
`FotoSpeicher.erstelleThumbnailWennMoeglich()` erzeugt bereits ein lokales 512-px-JPEG mit Qualität 82. Dieses Thumbnail ist nicht in den Online-Sync eingebunden. Eine separate ca. 1920-px-Sync-Datei wird im geprüften Uploadpfad nicht erzeugt.

### Server
`server/tbserver/files.py` verschiebt die empfangene Datei unverändert in den Storage. Der Server speichert also genau das, was Android sendet.

Vorhandene Renditions:
- `thumb`: max. 320×320, JPEG 72
- `preview`: max. 1280×1280, JPEG 82

### Server → Android
`Server1FileApi.downloadContent()` nutzt `/api/v1/files/{id}/content`.
Der Android-Dateiclient modelliert/nutzt die Server-Renditions nicht.
`CollabSyncRepository.synchronizeServer1Attachments()` lädt fehlende sichtbare Attachments über diesen Vollinhaltspfad.

### Metadaten
BTB sendet Aufnahmezeit, GPS Lat/Lon und strukturierte Foto-Metadaten, aber `gpsAccuracyM = null`; `FotoEntity` besitzt kein Feld für GPS-Genauigkeit.
Mangel-/Abnahmefoto-Uploads übergeben im geprüften Pfad keine gleichwertigen strukturierten Aufnahme-/GPS-/Foto-Metadaten.

## Umsetzungsziel ANDROID
1. separate Sync-JPEG-Datei vor Upload, Richtwert max. ca. 1920 px / JPEG 80–82 %, nie hochskalieren;
2. lokales Volloriginal unverändert lassen;
3. Upload-Hash/Größe auf Sync-Datei beziehen;
4. `preview` für normale mobile Bildnutzung, `thumb` für kleine Ansichten;
5. `/content` nicht als automatischen Bildstandard verwenden;
6. GPS-Genauigkeit und übrige Metadaten vollständig führen;
7. Mangel-/Abnahmefoto-Uploads strukturiert ergänzen.

## Server
Kein Ersatzumbau für weiter hochgeladene Handy-Volloriginale. Bestehende Renditions und Migration 015 praktisch verifizieren. Portal-Quelldateien dürfen erhalten bleiben.

## Priorität
Nach dem Audit wurde `E2E-PRIO-001` und `NET-001` dokumentiert:
1. Hauptgerät-DNS/API-Erreichbarkeit,
2. Haupt-TB-Handy ↔ Server/Portal,
3. HELPER/Poco,
4. danach IMG-001-Implementierung.

Kein Fachcode wurde bei diesem Audit geändert.
