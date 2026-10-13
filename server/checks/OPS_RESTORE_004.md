# OPS-RESTORE-004 – Rückweg nach einem Update

## Bedeutung der Varianten

| Modus | Benutzer/Geräte | Automatik |
|---|---|---|
| UPDATE (Standard neuer Anfragen) | Status, Rechte, Ablaufdaten, Gerätebindung und Token aus Backup bleiben erhalten | Gesicherte Einstellungen bleiben erhalten |
| SECURITY | Alle anderen Benutzer gesperrt; Geräte und offene Aktivierungen/Einladungen widerrufen | Ausgeschaltet |

Firmen, Baustellen, BTBs, Dateien, Originale und Fachrechte stammen in beiden Fällen aus dem gewählten Vollbackup. Nach dem Backup angelegte Benutzer/Geräte existieren darin nicht. Damals bereits gesperrte oder abgelaufene Zugänge werden durch UPDATE nicht freigegeben. Sperren/Passwortänderungen nach dem Backup sind ebenfalls zurückgesetzt: bei Verdacht auf kompromittierte Zugänge SECURITY wählen.

Beide Modi widerrufen alte Portal-Sessions, lassen nur einen im Backup vorhandenen aktiven anfordernden Admin zu, verwerfen offene Uploadsessions und stoppen alte wartende/laufende Jobs. UPDATE übernimmt offene Einladungs-/Aktivierungstokens aus dem Backup mit ihren damaligen Ablauf-/Widerrufsdaten. SECURITY widerruft diese zusätzlich. Laufende Aufträge werden nicht erneut abgespielt. Gesicherte Automatikeinstellungen sind nach UPDATE wieder wirksam; nach SECURITY müssen sie bewusst eingeschaltet werden.

## Vor einer Erweiterung

1. Änderungen auf Geräten synchronisieren, ungesendete lokale Arbeiten zusätzlich sichern.
2. Aktuelles Serverpaket bzw. Container-Image **außerhalb** des auszutauschenden Projektordners erhalten. Lokale Compose-Datei und .env geschützt sichern; nicht öffentlich ablegen.
3. Systemstatus → Vollbackup. Erst bei OK fortfahren; zusätzlich Backup prüfen.
4. Neue FULL-Archive enthalten `runtime`: Serverversion, SHA-256 über Pythonquellen/Templates/Migrationsdateien und vorhandene requirements.txt/Dockerfile.v1/app.py sowie Migrationschecksummen. Keine .env, Passwörter oder Dateninhalte im Runtime-Metadatensatz. Der Fingerabdruck ist eine Kompatibilitätsprüfung, kein Ersatz für das separat aufbewahrte Serverpaket/Image.
5. Erweiterung einspielen und prüfen.

## Falls das Update zurückgenommen werden muss

Den passenden alten Programmstand für die Wiederherstellung verwenden. **Nicht einfach alte Software gegen eine bereits migrierte Live-Datenbank starten.** Bei Schemawechsel benötigt man eine isolierte Wiederherstellungsumgebung mit passendem Programmstand; der neue Livebestand bleibt unangetastet. Dieser Ablauf ist kein automatischer Programmdowngrade.

UPDATE verlangt beim Vorbereiten exakt gleiche Runtime-Metadaten; `_seal_target` prüft zusätzlich alle tatsächlich restaurierten Migrationsversionen/-namen/-checksummen. Fehlende Metadaten: `RECOVERY_RUNTIME_METADATA_MISSING`; abweichender Programmstand: `RECOVERY_RUNTIME_VERSION_MISMATCH`; abweichendes Schema: `RECOVERY_SCHEMA_VERSION_MISMATCH`. Version prüfen/installieren, nicht durch beliebige Umbenennung eines Backups umgehen. Alte Backups bleiben für Backup-Prüfung und SECURITY verwendbar, bei weiterhin erforderlichem passendem Schema.

Nach erfolgreicher Vorbereitung READY gilt weiterhin: **noch nicht umgeschaltet**. Anleitung OPS_RESTORE_003.md zur manuellen gemeinsamen Umschaltung von API/Worker/Monitor/Migration auf Ziel-DB + Zielspeicher befolgen. Programmstand zwischen Vorbereitung und Umschaltung nicht austauschen. Danach sind normale zukünftige Updates möglich; der historische Fingerabdruck wird nicht als dauerhafte Updatesperre verwendet.

## Synology – Übernahme dieser Änderung

GitHub Desktop → File Station `/volume1/docker/tb-helfer-v1`, folgende Dateien aus `server/` an dieselben relativen Ziele übernehmen:

| Datei | Änderung |
|---|---|
| tbserver/recovery_version.py | Neu: Programm-/Schemaidentität |
| tbserver/recovery.py | Varianten und Versionsprüfung |
| tbserver/recovery_guard.py | Variantenpaar und Gerätekennung |
| tbserver/system_admin.py | Modus in Auftrag/Status |
| tbserver/backup.py | Metadaten neuer Vollbackups |
| tbserver/devices.py | Kennungsprüfung nach normaler Geräteauth |
| tbserver/capabilities.py | Wiederherstellungskennung |
| templates/v1_system_status.html | Auswahl und passende Hinweise |

API und Worker neu starten; Browser Strg+F5. Keine neue Migration (weiter 21), keine Änderung an bestehenden Datenordnern/.env nötig für diese reine Codeübernahme. Voraussetzung ist vollständiger OPS-RESTORE-003-Stand.

Bei erstmaliger Zielvorbereitung in File Station **vor Projektstart** die leeren Ordner `restore-target-database-v1` und `restore-target-storage-v1` unter dem Projektordner anlegen. Sonst meldet Synology `Bind mount failed ... does not exist`.

Bereits QUEUED/RUNNING/READY-Zustände ohne `mode` bleiben SECURITY. Ihre Daten und Sperren werden durch die Codeübernahme nicht verändert. Ein belegtes Ziel wird nicht gelöscht/überschrieben. Für eine neue Vorbereitung neue getrennte Datenbank-/Storage-Ziele konfigurieren und deren leere Ordner anlegen; den bestehenden Restorestand behalten. Kein automatischer zweiter Restore auf das aktive Ziel.

## Android-Vertrag (Implementierung in Android noch offen)

- `GET /api/v1/capabilities` liefert `capabilities.recovery_epoch_v1=true` und `recovery_id=null` vor Cutover, danach die UUID aus `TB_RECOVERY_ID`. READY auf dem alten Server erzeugt keine neue aktive Kennung.
- Nach Cutover verlangt jede authentifizierte Geräte-Fachanfrage den Header `X-TB-Recovery-ID` mit dieser UUID. Fehlende oder andere Kennung: HTTP409, `detail={code:SERVER_RECOVERY_REBASE_REQUIRED,recovery_id:<UUID>}` sowie gleichnamiger Responseheader. Ungültige/gesperrte Tokens scheitern weiterhin zuerst mit normalem 401/403; die UUID ist kein Zugangstoken.
- App bei Start/Netzrückkehr und bei409: normale Syncmutationen anhalten, ausstehende lokale Änderungen/Fotos/Outbox unverändert sichern, alten Cursor nicht weiterverwenden. Für kontrollierten Download aktuelle Kennung senden, Serverbestand separat vollständig laden; erst danach bestätigten lokalen Epochstand speichern. Lokale ungesendete Arbeiten nicht blind überschreiben oder erneut hochladen; gegen Serverversionen prüfen und Konflikte bewusst bearbeiten.
- Das Senden des Headers ist eine Client-Protokollbestätigung, kein serverseitiger Nachweis einer vollständigen lokalen Konfliktauflösung. Android muss die Reihenfolge verlässlich implementieren.
- Aktuelle unangepasste Android-App bleibt nach Cutover beim Datenzugriff gesperrt. Die Geräteaktivierung bleibt im UPDATE-Ziel erhalten; **nicht** Appdaten löschen oder als vermeintlichen Fix alles neu aktivieren. Vollständige praktische Abnahme erst nach Androidänderung mit zwei Geräten und ungesendeten Arbeiten.

## Tests

`141_verify_recovery_modes.py`: reale Produktionsfunktionen für exakte/fehlende/abweichende Metadaten, Fingerabdruckänderung ohne Secrets, Epochheader vor/nach Cutover, DB/Storage-Modus-/Metadatenpaar.

`tools/recovery_gate.py`: zwei getrennte CI-Läufe UPDATE und SECURITY in ausschließlich kurzlebigen Testdatenbanken. Echte FULL-Sicherung, pg_restore, Datei-/Schemaprüfung, unveränderter Quellbestand, erhaltene bzw. gesperrte Nutzer/Gerätetokens, gesperrter Nutzer bleibt gesperrt, Automatik je Modus, verworfene Uploads/Jobs, tatsächlicher API-Neustart auf Ziel, Portal-Login und Gerätezugriff ohne/falscher/passender Kennung. Alte SECURITY-Zustände ohne mode separat geprüft. Kein NAS-/Android-E2E daraus ableiten.


- Nachweis:89 Verhaltenstests130–141 PASS; Python/JavaScript/YAML-Syntax PASS. Echter PostgreSQL17/FastAPI-Gate zweimal SUCCESS; letzter Run37524540497 Job112478085754: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37524540497/job/112478085754 . UPDATE und SECURITY jeweils echte Sicherung/Wiederherstellung/API-Neustart mit Portal-/Geräteprüfungen PASS; zusätzlicher Altzustand ohne mode bleibt SECURITY. Quellbestand erhalten, Zielfehler/Lock/Retry-Guards unverändert geprüft. 3x255Clients0Fehler,p95 6,13–6,16s inkl2sWait; keine NAS-/Android-/Foto-/BTB-Schreiblastabnahme.
- Gesamt-CI weiterhin nicht grün: foundation alter FILE_LINK_TARGET_NOT_FOUND-Marker, Android-CI alter Sourcevertrag. Nicht umgangen/abgeschaltet. Weitere Server-/Portal-Arbeit und Android-Recoveryintegration bleiben offen.
