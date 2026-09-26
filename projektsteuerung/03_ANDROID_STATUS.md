# TB-HELFER 2.0 – ANDROID STATUS

Stand: 2026-09-26
Branch: `feature/tb-android-2.0`
Fach-HEAD vor Projektsteuerungscommit: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
Draft-PR: #7
Version: **1.3.19 / 200069**

- Server/Android-Vertrag 25.09 im Quellstand berücksichtigt.
- Pixel ACTIVE↔PDF-Flackern source-seitig korrigiert.
- Build-Wiederholung 200069: **OFFEN**
- Installation 200069: **OFFEN**
- Pixel/Poco-/Server-E2E: **OFFEN**
- Poco: korrekte HELPER-/Teamzuordnung serverseitig, aber keine Daten auf Gerät.

Geschützt: bestätigter BTB-Lifecycle-Fix 200063, bestätigter Server-App-Updateweg mit Datenerhalt, Geräteaktivierung, kein breiter Vollsync als normaler Autosave-Pfad.

Nächste Schritte: IMG-001 Uploadpfad prüfen → 200069 bauen → Pixel/Poco testen → gemeinsamer E2E.

Historischer Tracker: `projektsteuerung/archiv/ANDROID_STATUS_ALT_2026-09-26.md`


## IMG-001 Quellaudit
Status: **FEHLER BESTÄTIGT / SOURCE-UMSETZUNG OFFEN**

- `BtbTeamMediaSync` verwendet `File(foto.dateipfad)` direkt als Uploadquelle.
- `CollabLocalBridge` macht dasselbe für Mangel- und Abnahmefotos.
- `CollabSyncRepository.uploadAttachment()` hasht und übergibt diese Originaldatei unverändert an `Server1FileApi.uploadLinkedFile()`.
- `Server1FileApi` überträgt exakt `file.length()`/dieselbe Datei und kennt aktuell keine Rendition-Downloadmethode.
- `synchronizeServer1Attachments()` lädt fehlende Dateien über `downloadContent()` = `/content` vollständig herunter.
- Das bestehende lokale 512-px-Thumbnail aus `FotoSpeicher.erstelleThumbnailWennMoeglich()` wird im Online-Sync nicht verwendet.
- `FotoEntity` speichert keine GPS-Genauigkeit; BTB-Upload sendet daher `gpsAccuracyM = null`.
- Mangel-/Abnahmefoto-Aufrufe liefern derzeit keine strukturierten Aufnahme-/GPS-/Foto-Metadaten an `uploadAttachment()`.

Nächster Android-Fachblock erst nach diesem gesicherten Audit:
1. Sync-JPEG temporär/ableitbar erzeugen (max. ca. 1920 px, JPEG ca. 80–82 %, nie hochskalieren).
2. Volloriginal unverändert am Aufnahmegerät lassen.
3. Serverbilder mobil über `preview`, Listen/kleine Ansichten über `thumb`; Original-`/content` nicht als normaler Bildsync.
4. Metadaten vollständig mitführen.
5. Danach Build 200069/folgender Build und E2E.
