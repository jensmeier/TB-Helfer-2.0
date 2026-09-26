# TB-HELFER 2.0 – MASTERSTATUS

Stand: 2026-09-26

> Live-HEADs immer zuerst direkt in GitHub prüfen. Die SHAs unten sind der Fachstand vor diesem reinen Projektsteuerungs-Umbau.

- ANDROID: `feature/tb-android-2.0`, Fach-HEAD `d272f8a7586cce3ea1eda29100473f7af5d540bb`, Draft-PR #7
- SERVER: `feature/tb-server-1.0`, Fach-HEAD `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`, Draft-PR #6
- Android: **1.3.19 / 200069**
- Server: **1.0.0-dev**
- Server-Quellmigrationen: **001–015**

## Android
Pixel-UI-Flackern ACTIVE↔PDF source-seitig korrigiert. Build-Wiederholung 200069, Installation und Pixel/Poco/Server-E2E offen. Poco ist serverseitig HELPER im richtigen Team, bekommt auf dem Gerät aber weiterhin keine Daten.

## Server
Rollen-/Schichtvertrag und FINALIZED-Schutz verbindlich. Migration 014 praktisch bestätigt. Migration 015/Foto-Metadaten Deployment/Runtime-E2E offen. Delta-Wait/15×15-Last-E2E offen. Finalize-OOM/502-Fix praktisch bestätigt und geschützt.

## Integrationsrisiken
1. Poco-Sync.
2. Android 200069 Build/Installation/E2E.
3. IMG-001: Bildvertrag ist fest, tatsächlicher Android-Uploadpfad muss dagegen geprüft werden.
4. Frühere gemischte Tracker sind nur noch Archiv; ab jetzt getrennte Android-/Server-Statusdateien plus Masterstatus.

Alles mit `☑`, `BESTÄTIGT`, `ERLEDIGT`, `PASST`, `FUNKTIONIERT` oder `Nicht ohne Grund ändern = JA` bleibt geschützt.


## IMG-001 Quellaudit 26.09.2026
Status: **ABWEICHUNG BESTÄTIGT / IMPLEMENTIERUNG OFFEN**

Der aktuelle Android-Bildpfad entspricht dem festgelegten 15×15-Vertrag noch nicht:
- BTB-, Mangel- und Abnahmefotos werden aus dem lokalen Originalpfad an den Upload übergeben.
- `Server1FileApi.uploadLinkedFile()` setzt `expected_bytes = file.length()` und überträgt genau diese Datei.
- Android lädt sichtbare Serverbilder aktuell über `/api/v1/files/{id}/content` und damit die serverseitige Quelldatei; `thumb/preview` werden im Android-Dateiclient nicht ausgewertet.
- Der Server speichert die empfangenen Bytes unverändert, besitzt aber bereits geschützte JPEG-Renditions `thumb` (320 px) und `preview` (1280 px).
- Ein lokales 512-px-Thumbnail existiert bereits auf Android, wird vom Online-Sync aber nicht verwendet.
- BTB-Fotometadaten werden weitgehend strukturiert übertragen; `gps_accuracy_m` geht derzeit verloren. Mangel-/Abnahmefoto-Uploads übergeben aktuell keine gleichwertigen strukturierten Foto-Metadaten.

Detaudit: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.

Keine `app/**`- oder `server/**`-Fachänderung in diesem Auditblock.
