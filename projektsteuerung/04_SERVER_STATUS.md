# TB-HELFER – SERVER STATUS

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

Server BUILD/CI BESTANDEN: vollständiger Lauf42496224535 am b924da3e9bbff603a78ca55753cefb439f1ac607 gegen Android cda78c2f42fd7b1acd76d60612ce42d882662edf. 66 Unit-Tests,100 Foundation-Prüfskripte,24Migrationen in frischer PostgreSQL-Testumgebung, Authentifizierungsablauf, Container-/Snapshot-/Restore-/Rechte-/Recoverygate sowie255Sync-Wait-Verbindungen erfolgreich. Kein produktiver NAS-Test.

Server1.0.0-dev/APIv1/PDF6; Servermigrationen001–024 unverändert vorhanden. Keine neue Servermigration.
AUDIT-1108-01/-02 im Code behoben: track_data und survey_sections erzwingen Leserecht/Bereich; Cleanup nach Commit rollt PDF nicht zurück. Tests prüfen erlaubte/gesperrte/unklare Bereiche und echte temporäre Dateien mit injizierten Fehlern; diese spezifischen Tests verwenden SQL-/DB-Doubles und sind kein NAS-E2E.
MANUAL_GPS bleibt bei Original-TRA-Überdeckung erhalten; object_key wird im manuellen Update geladen. Eindeutiger Originalsignatur-Resolver ergänzt bestehenden gemeinsamen KM-Vertrag.
Worker prüft16h-Frist für ACTIVE-BTBs und nutzt vorhandenen Abschluss-/PDF-Pfad, mit erneuter Status-/Fristprüfung unter Sperre. REWORK wird nicht automatisch finalisiert. Offline-Daten können erst nach Netzrückkehr bekannt werden und brauchen separate Abnahme.

Offen: gemeinsame Trassen-/KM-Löschabsichten und Konfliktoberfläche; KM-Sprünge und GVP-/Mast-Bearbeitung; vollständiger objektweiser Fachdelta-/globaler Katalogfeed; Offline-Android-Nachlauf der 16h-Frist; experimentelle Android-DB 20/21; alter Installationssignierschlüssel und reale NAS-/Pixel-/Poco-E2E. Kein NAS-Deployment, kein Merge, keine produktive Migration oder Datenänderung ausgeführt.

Geschützt und erhalten: Word, drei Bilder pro Reihe, gespeicherte Fotoanzahl, stabile IDs und Originale sowie historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT-Markierungen. Neue Tests sind keine erneute pauschale Praxisbestätigung. Ungeprüfte ältere Restaufträge bleiben im Archiv offen zur Einordnung.

## Nachweise und aktueller Arbeitsblock PRUEF-1120-1BIS5 (20.11.2026)
- Bereits abgeschlossen: Android CI 44643311256 BESTANDEN (Build, Lint, Unit und Serververtrag am gepinnten Codepartner); Server CI 44643317730 BESTANDEN (Foundation + Snapshot/255-Client-Gate).
- Server neue gezielte Tests: `server/tests/test_kompat_1120_regressions.py`, Commit `e49e9a4698539479b1198cd42c60140b4a081789`, CI 44645336085 BESTANDEN (KM-Scope, Originalimport, Paginierung).
- Zusätzlich: echter Datei-Upload-Pfad mit erzwungenem Post-Commit-Cleanup-Fehler, Testcommit `4fbc47c5d7d64afc82129c030cb13260148e8639`, CI-Lauf 44645761732 bei Anlage noch NICHT als bestanden bestätigt.
- Android: `SurveyOriginalOutboxTest.kt` für stabile pro-Baustelle-Originalauftragsschlüssel, Commit `1a4184b392d9fd4239fbce9fc67017232f03f495`, CI-Lauf 44645898340 bei Anlage noch NICHT als bestanden bestätigt.
- F-03 und F-05 unverändert OFFEN: globaler/firmeneigener Katalog und das team-/bereichsbezogene Rechtekonzept müssen gemeinsam geprüft werden; kein blindes Schließen der Datenzugriffe.
- Punkte 3–5: Geräte-Signatur und Room 20/21, Synology-Inventar/Deploymentvorbereitung sowie E01–E12 in `projektsteuerung/uebergaben/PRUEF_1120_1_BIS_5.md` detailliert dokumentiert. INSTALLIERT, DEPLOYT, E2E BESTÄTIGT: NICHT NACHGEWIESEN.
- Keine APK/NAS/Produktivdaten geändert, keine Migration ausgeführt. Nachfolgende CI-Ergebnisse nur nach Original-Runner-Steps bestätigen.

## Verifizierter CI-Nachtrag 20.11.2026 – PRUEF-1120-1BIS5
- SERVER: Commit `4fbc47c5d7d64afc82129c030cb13260148e8639` mit vier ausführbaren, im Original-Runnerlog namentlich belegten KOMPAT-Regressionstests (Bereichsschreibschutz, Nur-Lesen-Originalimport, >200 unsichtbare Survey-Treffer, Post-Commit-PDF-Cleanup-Fehler) im vollständigen GitHub Actions Run [44645761732](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44645761732) **CI BESTANDEN**. Foundation-Job samt Runtime-Regressions und Snapshot-/Rechte-/255-Client-Job erfolgreich. Die Fehlerinjektion protokolliert absichtlich eine Cleanup-Ausnahme, das Testergebnis ist trotzdem erfolgreich.
- ANDROID: Commit `1a4184b392d9fd4239fbce9fc67017232f03f495`, `SurveyOriginalOutboxTest.kt` (gleiche Quelle/verschiedene Baustellen, stabile Delivery-Keys, Alteinträge) im vollständigen GitHub Actions Run [44645898340](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44645898340) **CI BESTANDEN**: Gegenserver-Source-Contracts, Unit-Tests und Lint erfolgreich; APK-Schritt bewusst **SKIPPED** (kein neuer APK-Auftrag/Release).
- Kein neuer versionCode/versionName; das zuvor dokumentierte Release 1.3.46/200096 darf nicht als mit den Teständerungen gebaut oder installiert bezeichnet werden.
- F-03/F-05 bleiben OFFEN; erfordern erst eine sichere Abgrenzung der gemeinsamen GLOBAL-/COMPANY-Katalogsicht gegenüber privaten Baustellen-/Team-/KM-Rechten. Genaue Abnahmematrix: `projektsteuerung/uebergaben/PRUEF_1120_1_BIS_5.md`.
- Synology-Deploy, reale Pixel-/Poco-Installationssignatur, Datenbank 20/21 und E01–E12 weiterhin **OFFEN**. GitHub/CI ersetzt diese E2E-Nachweise nicht.

## ANDROID-STUDIO-TESTSTAND FACH-1120-UMSETZUNG (20.11.2026)
Android 1.3.47 / 200097, Codecommit `fe8e4f3f873837b06344386ccdc1a4c1cfc64e8f`. Server-Partner Quellcode `451c4310060b56a99ea631d144a6d2d40a75fb2d` (Server 1.0.0-dev, API v1, PDF 6). Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.47_200097.md`. **Noch keine signierte APK, Installation, NAS-Übernahme oder E2E-Abnahme.**

Umgesetzt im Android-Fachcode: Foto-Firma, Kurz- und Langtext bearbeiten; BTB-Overrides atomar speichern; alte firmaId beim Firmennamenwechsel verwerfen; Kurztext nur Fotoliste, Langtext für BTB/Word; Such-Refresh reparieren; ZIP-Einträge/CRC vor Weitergabe prüfen; Fototext-Rechtschreibvorschläge und unveränderte Fachfirmennamen; Spracheingabe-Zwischenspeicherung drosseln; Wetterzeitpunkte Schichtanfang/Mitte/Ende; Firmenfelder im Profil einklappen. Gezielt implementierte Tests: `TagesZipPruefungTest`, `BtbWeatherPolicyTest`, bisherige Foto-/XLSX-/ZIP-/Sprachtests erhalten.

**OFFEN und nicht als erledigt markieren:** erneuter tatsächlicher Wetterdienstabruf bei realem Schichtende mit Quellenvergleich (Startwert darf nicht überschrieben werden); vollständige Fotoänderung auf anderem Handy und im Portal einschließlich nicht BTB-gebundener Fotos, Typ/KM/Unterbaustelle und Schreibrechte; gesamte Suche mit Serverfilter; reale Excel/ZIP/Word/PDF-Referenzfälle; Pixel/Poco/Room20-21/Signatur und NAS E01–E12; separate SERVER-Sicherheitsbefunde. Testkette noch auf vollständigen CI-Gesamtnachweis und Geräte-Praxisprüfung warten. Keine Datenbankmigration hinzugefügt; Originalfotos, stabiler BTB/Word-Bestand und drei Fotos pro Reihe geschützt.


## SERVER-SEC-1121 aktueller Stand
Server-HEAD zum Fachcode: `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`; Haupt-CI 44897857974 BESTANDEN, quellenkorrigiert S-05 gemeinsames Vermessungsoriginal beim Purge, S-02 Release nach Commit, S-07 globale Katalogachsen / global referenzierte Segmente. S-01 reviewed Compose erfordert explizite TB_V1_ENVIRONMENT, kein NAS-Preflight. F-03/F-05/S-06, 16h-Lead-Randfall und Recovery/NAS-E2E OFFEN. Belege in `uebergaben/SERVER_AUDIT_2026-11-21.md`.

## Final verifizierter GitHub-CI-Stand – 21.11.2026 (ersetzt ältere "läuft/offen"-Notizen)
- SERVER-Fachcode `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44897857974; Jobs **foundation** und **Snapshot, rights and 255-client HTTP gate** beide abgeschlossen und erfolgreich. Neue Regressionen prüfen echte Handler/temporäre Dateien und isolierte SQL-Doubles, nicht Synology.
- RECOVERY-Workflow `79a98d6b528f409f2bf481ebeca038332a2ce2a9`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44897725728; **recovery-core** (einschließlich realem Docker-Systemimage-Export/Load) und **restore-ui** beide bestanden. Vorheriger ubuntu-latest/Playwright-Runnerfehler ist durch Pin auf ubuntu-24.04 korrigiert; Checks nicht deaktiviert. Diese Runnerumgebung ist nicht die produktive Synology.
- ANDROID 1.3.47/200097, Workflow-Pin auf SERVER-Fachcode `785c20e...`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44898057369; Jobs **Server and Android source contracts** und **build** erfolgreich. Noch kein Geräte-E2E/NAS-Deployment aus diesem Auftrag.
- Eingangs-HEADs vor serverseitigen Änderungen: SERVER `0eb98fa50532d7101d5dcdc7a3481a33899984a1`, ANDROID `a5d354ba386f7f76084b1f0d9094e0bca3a2110a`. Dokumentationscommits auf beiden Branches verändern weder Server-/Android-Fachcode noch die getesteten CI-SHAs.
- **OFFEN bleiben F-03/F-05/S-06, fachliche 16h-Lead-Randfälle, produktive TB_ENVIRONMENT-Werte und reale E01–E12.** S-01 ist bisher nur im reviewed-Compose fail-closed, nicht automatisch/auf NAS. Kein pauschales "alle Serverfehler beseitigt".

## SERVER-WEITER-1121: zusätzliche Absicherung (21.11.2026)
- SERVER-Codecommit `5be7408734a5df6b3d4c6c3b478426dd5f3121c2`: **beide** NAS-Compose-Vorlagen verlangen `TB_V1_ENVIRONMENT` ausdrücklich; kein stiller staging-Fallback mehr in diesen beiden Vorlagen. Unverändert laufende NAS und deren effektive `.env` sind nicht geprüft.
- SERVER-Codecommit `ffddf9d5d5032f43a5995f4553d6dcdef19d0494`: Baustellenbezogene Sync-Listen, Long-Poll-Neuprüfung und SSE-Einstieg verlangen `worksite.view` zusätzlich zur aktiven Mitgliedschaft. `test_sync_1121_scope.py` prüft den gesperrten und erlaubten Fall. **F-03 bleibt TEILWEISE OFFEN**: Team-/Fachobjekt-/KM-/Tombstone-Filter und Cursor/E2E sind nicht vollständig implementiert.
- SERVER-Testcommit `8c12c1c5c52a2b1a9cc6193484173da97ca64c10`: isolierten älteren Reauthentication-Check `135_verify_sync_reauthentication.py` an neue `require_permission`-Abhängigkeit angepasst, nicht die Sicherheitsprüfung entfernt. Vollständiger Server-Hauptworkflow 44910955648 BESTANDEN; initialer Lauf 44910713496 hatte diesen Prüfskriptfehler.
- SERVER-Codecommit `2fb91c592941c5efba6f02608343b6e3a7f09122`: interner Systemexport verlangt neuen gemeinsamen Schlüssel (`TB_V1_SYSTEM_EXPORT_TOKEN`, mindestens 32 Zeichen) in Worker und `system-export`; Export-Endpunkt verweigert fehlende/falsche Tokens mit 403. Beide NAS-Compose-Dateien verlangen Variable, Recovery-CI nutzt nur eine explizite disposable-Testkennung. Unit-Test `test_system_export_auth.py`. **S-06 bleibt TEILWEISE OFFEN**: Host-Docker-Socket selbst ist weiterhin privilegiert und verlangt einen eng begrenzten Proxy/Exportweg.
- SERVER-Haupt-CI https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911154233 **CI BESTANDEN**: beide Jobs foundation sowie Snapshot, rights and 255-client HTTP gate. Recovery-Gesamtlauf https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911154263 zum Dokumentationszeitpunkt noch nicht als Gesamt-ERFOLG bestätigen; `restore-ui` war erfolgreich.
- ANDROID 1.3.47/200097: CI-Gegenserverpin auf `2fb91c592941c5efba6f02608343b6e3a7f09122` angepasst, Android Workflow-Commit `3471687d7d80cda3b81ff14879a12e49a0f0e633`; zugehörigen CI-Gesamterfolg separat prüfen. Keine APK, keine NAS-Installation, E01–E12 OFFEN.
- **Zwingende Vorbereitung einer späteren NAS-Übernahme:** Zuerst tatsächliche bestehende `.env` prüfen (ohne Secrets an Chat/GitHub zu übertragen), `TB_V1_ENVIRONMENT=production` sowie einen individuell generierten geheimen `TB_V1_SYSTEM_EXPORT_TOKEN` mit >=32 Zeichen in der NAS-`.env` setzen; Worker und Exportdienst müssen denselben Wert sehen. Ohne diesen Preflight NICHT deployen/neustarten. Bestehende DB-/Files-/Recovery-Backups, Originale und Word/PDF-Bestätigungen schützen.
- OFFEN unverändert: F-05 Katalog/Raw-Original-Berechtigungen, verbleibende F-03 Fachobjektrechte, S-06 Docker-Socket-Härtung, BTB-16h bei deaktiviertem Lead, reale NAS-/Pixel-/Poco-E2E.
