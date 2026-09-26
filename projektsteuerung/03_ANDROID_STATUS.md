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

Nächste Schritte: NET-001 klären → Haupt-TB-Handy ↔ Server/Portal E2E → Poco/HELPER E2E → danach IMG-001 implementieren → Build/Regression.

Historischer Tracker: `projektsteuerung/archiv/ANDROID_STATUS_ALT_2026-09-26.md`

## Praxisbefund Hauptgerät – DNS/Hostauflösung

Beim Anlegen einer Testbaustelle meldet die App:
`Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.

Nachtest auf demselben Hauptgerät: `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` sind im Browser erreichbar. Damit ist die öffentliche DNS/API-Erreichbarkeit grundsätzlich vorhanden. Nächster Schritt: App-Sync erneut auslösen; nur bei erneutem App-only `Unable to resolve host` den Android-Netzpfad gezielt untersuchen.


## IMG-001 Quellaudit
Status: **FEHLER BESTÄTIGT / IMPLEMENTIERUNG ZURÜCKGESTELLT BIS E2E-PRIO-001**

- `BtbTeamMediaSync` und `CollabLocalBridge` verwenden lokale Originaldateien als Uploadquelle.
- `Server1FileApi` überträgt exakt diese Datei und kennt aktuell keine Rendition-Downloadmethode.
- `synchronizeServer1Attachments()` lädt fehlende Bilder über `/content` vollständig.
- Lokales 512-px-Thumbnail vorhanden, aber nicht im Online-Sync genutzt.
- `FotoEntity` speichert keine GPS-Genauigkeit; BTB-Upload sendet deshalb `gpsAccuracyM = null`.
- Mangel-/Abnahmefoto-Uploads übergeben keine gleichwertigen strukturierten Foto-Metadaten.

Geplanter Android-Fix nach stabilem Hauptgerät-/Poco-E2E:
kleine Sync-JPEG-Datei → Upload; `preview/thumb` für mobile Downloads; Metadaten vollständig.

### Browser-Nachtest NET-001
Beide öffentlichen TB-Helfer-Seiten sind auf demselben Hauptgerät im Browser erreichbar. App-Retest ist der nächste Schritt.
