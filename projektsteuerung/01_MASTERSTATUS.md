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

## Priorität ab 26.09.2026 – Funktions-E2E vor Last-E2E

Verbindliche Reihenfolge:
1. zuerst ein verantwortliches TB-Handy sauber gegen Server/Portal E2E zum Laufen bringen;
2. danach den HELPER-/Poco-Syncfehler gezielt lösen und Helfer-E2E bestätigen;
3. erst danach den vollständigen 15×15-Gesamtlasttest wiederholen.

Der bereits praktisch gelaufene Delta-Wait-Kapazitätstest mit 255 getrennten Portal-Sessions war erfolgreich (255/255, keine Request-Fehler). Er ist ein **Kapazitätsteiltest**, kein vollständiger 15×15-E2E. Nach dem geplanten Ausbau auf 8 GB RAM bzw. einem NAS-Wechsel wird der Last-/Kapazitätstest auf der dann maßgeblichen Hardware erneut ausgeführt.

Bis dahin gilt: Funktionsfehler zuerst reproduzierbar eingrenzen; keine Performance-Umbauten auf Verdacht.


## IMG-001 Quellaudit 26.09.2026
Status: **ABWEICHUNG BESTÄTIGT / IMPLEMENTIERUNG NACH E2E-PRIO-001**

Quellprüfung:
- Android übergibt BTB-, Mangel- und Abnahmefotos aktuell aus dem lokalen Originalpfad an den Upload.
- `Server1FileApi.uploadLinkedFile()` überträgt genau diese Datei (`expected_bytes = file.length()`).
- Android lädt Serverbilder aktuell über `/api/v1/files/{id}/content`; `thumb/preview` werden vom Android-Dateiclient nicht genutzt.
- Der Server speichert empfangene Bytes unverändert, besitzt aber bereits `thumb` (320 px) und `preview` (1280 px).
- Das lokale 512-px-Thumbnail ist vorhanden, wird aber nicht als Online-Sync-Artefakt verwendet.
- BTB-Metadaten sind teilweise strukturiert; GPS-Genauigkeit fehlt. Mangel-/Abnahmefoto-Metadaten sind im Uploadpfad unvollständig.

Detaudit: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.

Priorität bleibt gemäß E2E-PRIO-001:
1. NET-001/Hauptgerät-Erreichbarkeit,
2. Haupt-TB-Handy ↔ Server/Portal,
3. HELPER/Poco,
4. danach IMG-001-Implementierung.

## Praxisnachtrag 26.09.2026 – Pixel wieder online / Poco Erstabgleich
- Pixel hat nach den vorherigen App-DNS-Fehlern inzwischen wieder mit dem NAS synchronisiert. NET-001 ist damit aktuell **nicht mehr als dauerhafter Verbindungsblocker nachgewiesen**, Ursache des vorherigen Fehlers bleibt ungeklärt.
- Poco wurde neu installiert und erneut aktiviert. Der anschließende erste Datenabruf läuft sehr lange (>3 Minuten) und ist noch nicht als erfolgreich/fehlgeschlagen abgeschlossen.
- Priorität bleibt: Hauptgerät↔Portal-Fachkette bestätigen, danach Poco-Erstabgleich gezielt messen/eingrenzen.

## Praxisnachtrag – Poco-Erstabgleich fertig / neuer BTB 26.09 inkonsistent
- Poco-Erstabgleich ist abgeschlossen; Baustelle konnte erfolgreich heruntergeladen werden.
- Pixel hat einen neuen BTB für 26.09 angelegt.
- Portal zeigt weiterhin nur BTBs bis 25.09; der 26.09-BTB erscheint dort nicht.
- Poco zeigt den 26.09-BTB kurzzeitig und fällt etwa eine Sekunde später auf „kein BTB vorhanden“ zurück.
- Damit ist jetzt ein konkreter BTB-Sync-/Lifecycle-Fehler reproduziert. Vor Codeänderung Serverzustand, Team-`active_btb_id`, BTB-Status und Android-Snapshot-Anwendung prüfen.
