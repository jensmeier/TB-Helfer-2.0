# Server-Audit und Korrekturstand – 21.11.2026

## Quelle und Geltungsbereich
GitHub-Serverbranch feature/tb-server-1.0, geprüfter korrigierter Fach-HEAD `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`. Android feature/tb-android-2.0 Version 1.3.47/200097; Workflow-Gegenpartner auf diesen Serverstand umgestellt (`92a884f03255cea335ac16d2cf0f91380f20ce92`). Ist-Synology/Pixel/Poco **nicht** geprüft oder verändert. Keine Aussage über uneingeschränkte Fehlerfreiheit.

## Bekannte Serverfehler – nach erneuter Code- und CI-Prüfung
| ID | Einstufung | Status | Quellcodebeleg, Korrektur und Abnahme |
|---|---|---|---|
| S-05 | KRITISCH Datenverlust | QUELLCODE KORRIGIERT, CI BESTANDEN | `server/tbserver/admin_purge.py` Baustellen-Purge selektiert physisch zu löschende `tb.files`-Pfade jetzt **nur ohne** weiterhin vorhandenes `tb.survey_originals.storage_key`; `test_server_sec_1121.py` prüft denselben Datei-/Originalpfad. Reale NAS-Referenzfälle offen. |
| S-02 | HOCH Datenbank/Datei | QUELLCODE KORRIGIERT, CI BESTANDEN | `server/tbserver/releases.py` trennt Fehler VOR Commit (Rollback) von Cleanup NACH Commit (OSError protokolliert, bestätigte APK bleibt). Tests führen Handler mit temporären echten Dateien und beiden Fehlerarten aus. |
| S-07 | MITTEL globaler Datenkatalog | QUELLCODE KORRIGIERT, CI BESTANDEN | `admin_purge.py` Firmen-Purge löscht nicht mehr unabhängig von Firmenzuordnung alle leeren Katalogachsen; zusätzlich HTTP 409, wenn zu löschende Firmenoriginale noch GLOBAL veröffentlichte Segmente liefern. Test der SQL-Purge-Grenze. |
| S-01 | HOCH Fehlkonfiguration | QUELLCODE TEILWEISE GEHÄRTET, NAS OFFEN | `server/compose.synology-reviewed.v1.yml` verlangt `TB_V1_ENVIRONMENT` in der vorhandenen .env explizit, statt still `staging` zu verwenden; vor **jeder** Synology-Übernahme müssen tatsächliche Runtime und `production` rein lesend nachgewiesen werden. `server/docker-compose.synology-automatic.v1.yml` hat noch eigenen staging-Fallback: gesonderte sichere Umstellung/Recovery-Prüfung nötig. |
| F-03 | MITTEL Metadatenrechte | OFFEN | `server/tbserver/sync.py` Changefeed: aktive Baustellenmitgliedschaft allein unterscheidet nicht Team-/Fachobjekt-/KM-Sicht; zu sichere Filterung braucht auch BTB_ENTRY, BTB_PARTICIPANT, FILE, Tombstones, Cursor, Long-Poll/SSE. Keine ungetestete Änderung an Client-Cursorn. |
| F-05 | HOCH, Freigabepolitik zu klären | OFFEN | `server/tbserver/survey_originals.py` Firmen-Rohoriginale für Firmenmitglieder sichtbar; GLOBAL/COMPANY-Katalogreferenzen und tatsächliche Baustellenoriginale getrennt autorisieren, absichtlich geteilte Referenzen bewahren. |
| S-06 | HOCH Container-Härtung | OFFEN | `server/compose.synology-reviewed.v1.yml` system-export hat host Docker-Socket `:ro` (socket nicht wirklich read-only), internen HTTP-POST ohne separate Authentifizierung. Ersetzen durch autorisierten, eng beschränkten Exportzugang ohne Verlust des Vollbackups, erst nach isoliertem Recovery-Test. |
| BTB-16H | Fachlogik | VERDACHT OFFEN | `server/tbserver/btb_deadlines.py` nimmt nur aktive Firmen/Teams/Leads in 16h-Lauf auf; Status bei deaktiviertem/abgelaufenem Lead und verspäteten Offline-Helferdaten fachlich klären/testen. |
| FACH-1120 | fachliche Integration | OFFEN | Android/Server Foto-Firma, Kurztext und BTB-/Word-Langtext, Gesamt-XLSX, ZIP, Wetter Beginn/Ende, Suche/Portal nach Prüfliste `FACH_1120_TEXT_FOTO_ZIP_WETTER.md` in echter Zwei-Geräte-/NAS-Abnahme prüfen. |

## Bereits behoben – nicht erneut als neu offen führen
F-01 normaler Upload-PDF-Cleanup nach Commit; F-02 Track-/KM-Schreibscope; F-04 Originalimport- und Dateirechte; F-06 Survey-LIMIT/Filter-Paginierung; AUDIT-1108-01/-02. Fachcode und zugehörige Regressionstests im GitHub bereits bestanden; NAS-E2E weiterhin offen.

## Commit- und Testbelege
- `c3253f8de81619aca2e822a26cd4485c161981cb`: Korrektur S-05/S-02/S-07.
- `c738adb06b87848c4d7cbe53b8b160fdd77abb0b`: ausführbare DB-Fakes plus tatsächliche temporäre Release-Datei-Regression.
- `7f9874ea399084062c72065e3ab2aecbf5530998`: Synology Compose verlangt explizite Umgebung.
- `a2824e7bf4355fc0314bd6e89bd85965545a33d1`: nach Original-Logs präzisierter Release-Regressionstest (eine lesende DRAFT-Prüftransaktion vor dem eigentlichen Schreibcommit).
- `79a98d6b528f409f2bf481ebeca038332a2ce2a9`: Recovery-Workflow auf unterstützten ubuntu-24.04-Runner fixiert; Sicherheit/Migration/Recovery-Jobs bleiben aktiv.
- `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`: blockiert außerdem Firmen-Purge, wenn GLOBAL-Streckengeometrie aus Firmenoriginalen gelöscht würde.
- **Server-Haupt-CI** `44897857974`: vollständiger Workflow mit foundation und Snapshot/Rechte/255-Client-Gate BESTANDEN für den korrigierten Servercode.
- **Recovery-Workflow** `44897725728`: Restore-UI auf 24.04 bereits bestanden; vollständiges Recovery-core Ergebnis erst bei Original-Runnerabschluss bestätigen.
- **Android-Gegencheck** `44898057369`: revisionsgenauer neuer Serverpartner; erst nach Gesamtworkflow BESTANDEN melden.
- Vorheriger Recovery-Lauf `44897507608` FEHLER: Playwright-Chromium auf ubuntu26.04 nicht unterstützt und reales Docker-Systemimage nicht vollständig validiert. Tests **nicht** abgeschwächt/abgeschaltet; Pin 24.04 behebt im neueren Runner die Browserinstallation und der Systemimage-Roundtrip hat seinen Step bestanden. Restliches Recovery noch offen.

## Sicherheitsgrenzen für Übernahme
Nichts auf NAS geändert, keine produktiven Daten migriert, kein Release publiziert, keine APK installiert. Vor Deployment unbedingt nachweisen: `TB_V1_ENVIRONMENT=production` in effektiver Synology .env, intakte Datenbank/File-Backups, 001–024 Migrationen, gültiger Runtime/Portal/PDF Stand, Restore-Gate und Pixel/Poco E01–E12. Keine GitHub-CI als tatsächliches E2E ausgeben. Alte bestätigte BTB-/Word-/PDF-/Foto-Original-/3-pro-Zeile-Funktionen geschützt.
