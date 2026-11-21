# TB-HELFER – MASTERSTATUS
Stand 20.11.2026 · Auftrag KOMPAT-1120 · GITHUB AKTUELLER ARBEITSSTAND

## Verbindliches vorangegangenes CI-Basispaar (nicht mit den neuen Commits verwechseln)
- ANDROID: 1.3.46 / 200096, Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf; Room-Instrumentierung 680ef37e9781eb4e4b0b8da04b5db7e6b2d42340. Android CI 42495975421 / Room CI 42496070087 BESTANDEN.
- SERVER: 1.0.0-dev / API v1 / PDF 6, Fachcode b17530d305797837d42f3049681f10cd919ea4b5, Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Server CI 42496224535 BESTANDEN.
- Keine neue APK/versionCode, keine Migration oder NAS-Installation aus diesem Auftrag; E01–E12 unverändert E2E OFFEN.

## Neue Entwicklungscommits, Prüfstatus getrennt
- SERVER: 06191e1315d36d319f68705220c3488c55565276 (F-01/F-02/F-04 Codeänderung), 4b4065658d744a9773ca06d2b4af53c737056c02 (F-06 und Freigabekompatibilität), Workflow-Partnerpin 94fe87251c936c42807f2e60ee6c2c808b45d5a8 gegen Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd.
- ANDROID: 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd (F-07 Codeänderung), Workflow-Partnerpin 6e321e7e8aad4464dd6768376e65943b9cda26f1 gegen Server 4b4065658d744a9773ca06d2b4af53c737056c02.
- Die revisionsgenauen CI-Läufe für diese finale Codepaarung wurden gestartet; im Zeitpunkt dieser Statussicherung noch NICHT als BESTANDEN bestätigt. Kein E2E.
- F-01/F-02/F-04/F-06/F-07: QUELLCODE GEÄNDERT, Regression/CI/Gerätebestätigung noch getrennt nachzuweisen. F-03 (Changefeed-Metadaten) und F-05 (firmenweite Rohoriginalsichtbarkeit): OFFEN, nicht als erledigt deklarieren.
- Bekannte offene Punkte bleiben geschützt: Room 20/21, KM-Sprünge, Konflikt-UI/GVP-Mast, Offline-16h, Originale älterer Geräte und fehlende Signaturkontinuität.

## Regeln und Freigaben
Die beiden Arbeitsbranches bleiben getrennt. Fachänderungen und CI-Paarungs-SHAs sind exakt zu vergleichen. GitHub != NAS != Pixel/Poco. Word, drei Fotos pro Zeile, Anzahl-Einstellung, stabile IDs, lokale Originale und historisch BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT sind geschützt. Keine NAS-Änderung, keine produktiven Daten verändert.
Verbindliche Details: 00_START_HIER.md, 02_AKTIVER_AUFTRAG.md, 05_ANDROID_SERVER_VERTRAG.md, 06_E2E_TESTSTATUS.md, 07_OFFENE_PUNKTE.md, 09_KOMPATIBILITAETSBASIS.md. Ältere Stände bleiben im Git-Verlauf, dem Archiv und der Übergabe vom 08.11. nachvollziehbar.

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


## Nachtrag SERVER-SEC-1121 (21.11.2026)
Server-Korrekturstand `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`: S-05/S-02/S-07 quellenkorrigiert und Server-Haupt-CI Run 44897857974 bestanden; S-01 Synology-Compose fail-closed vorbereitet, noch nicht auf NAS. F-03/F-05/S-06, BTB-16h-Randfall und echte Synology-/Android-E2E offen. Android 1.3.47/200097, Gegenpartner-Pin `92a884f03255cea335ac16d2cf0f91380f20ce92` noch separat per CI prüfen. Recovery-UI auf Ubuntu 24.04 grün, Gesamt-Recovery-Run 44897725728 nicht vorschnell freigeben. Vollständige Liste `uebergaben/SERVER_AUDIT_2026-11-21.md`.

## Final verifizierter GitHub-CI-Stand – 21.11.2026 (ersetzt ältere "läuft/offen"-Notizen)
- SERVER-Fachcode `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44897857974; Jobs **foundation** und **Snapshot, rights and 255-client HTTP gate** beide abgeschlossen und erfolgreich. Neue Regressionen prüfen echte Handler/temporäre Dateien und isolierte SQL-Doubles, nicht Synology.
- RECOVERY-Workflow `79a98d6b528f409f2bf481ebeca038332a2ce2a9`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44897725728; **recovery-core** (einschließlich realem Docker-Systemimage-Export/Load) und **restore-ui** beide bestanden. Vorheriger ubuntu-latest/Playwright-Runnerfehler ist durch Pin auf ubuntu-24.04 korrigiert; Checks nicht deaktiviert. Diese Runnerumgebung ist nicht die produktive Synology.
- ANDROID 1.3.47/200097, Workflow-Pin auf SERVER-Fachcode `785c20e...`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44898057369; Jobs **Server and Android source contracts** und **build** erfolgreich. Noch kein Geräte-E2E/NAS-Deployment aus diesem Auftrag.
- Eingangs-HEADs vor serverseitigen Änderungen: SERVER `0eb98fa50532d7101d5dcdc7a3481a33899984a1`, ANDROID `a5d354ba386f7f76084b1f0d9094e0bca3a2110a`. Dokumentationscommits auf beiden Branches verändern weder Server-/Android-Fachcode noch die getesteten CI-SHAs.
- **OFFEN bleiben F-03/F-05/S-06, fachliche 16h-Lead-Randfälle, produktive TB_ENVIRONMENT-Werte und reale E01–E12.** S-01 ist bisher nur im reviewed-Compose fail-closed, nicht automatisch/auf NAS. Kein pauschales "alle Serverfehler beseitigt".

## Verifizierter Abschluss der CI-Teilprüfung 21.11.2026
- `44911154233` SERVER https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911154233: **CI BESTANDEN**, Jobs `foundation` und `Snapshot, rights and 255-client HTTP gate` jeweils vollständig erfolgreich. Fachcode `2fb91c592941c5efba6f02608343b6e3a7f09122`.
- `44911154263` RECOVERY https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911154263: **CI BESTANDEN**, Jobs `recovery-core` und `restore-ui` erfolgreich; isolierte Recovery- und Compose-Gates wurden nicht entfernt.
- `44911363072` ANDROID https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911363072: **CI BESTANDEN**, Jobs `build` und `Server and Android source contracts` erfolgreich. Android 1.3.47/200097, Gegenserver-Codepin `2fb91c592941c5efba6f02608343b6e3a7f09122`.
- Diese Bestätigung **schließt weder** F-03 vollständig **noch** F-05/S-06 (Docker-Socket), 16h-Lead-Randfall oder echte NAS/Pixel/Poco-E2E. Es wurde nichts auf der NAS installiert. Vor einer späteren NAS-Übernahme sind die neuen verbindlichen Umgebungsvariablen `TB_V1_ENVIRONMENT` und `TB_V1_SYSTEM_EXPORT_TOKEN` zu beachten; Secrets nicht in GitHub/Chats speichern.

## ANDROID-WARN-1121 – Bereinigung 19 lokaler Warnungen (21.11.2026)
Ausgang: Nutzer-Build-Screenshot `BUILD SUCCESSFUL`, 19 Kotlin-Warnungen (17 Hauptquelle, zwei Export-Tests), plus separate KSP/IntelliJ-Ausnahme. CI-Ausgangspunkt Android 1.3.47/200097, Branch `feature/tb-android-2.0`. Quellcodekorrekturen `f201fc2818e997ee310e5a965b59d4287a424ae0` und `4aa3df71304f4162dc39a521035910a2083a5b36` (kein neuer versionCode, keine APK).
- Typ-/Nullsicherheit: Spinneradaptersuche ohne unchecked cast, BTB-Historie prüft tatsächlich nullable External-Files-Root bevor Dateipfad erzeugt wird, Test-Parentverzeichnisse nullsicher.
- Android-API/Anzeige: Vibrator via getSystemService(Klasse), Schriftgrößen über TypedValue statt scaledDensity in Overlay und DKW-Schema, Systemleisten-/IME-Alt-APIs nur dort mit **lokalem** DEPRECATION-Hinweis bewahrt, wo eine direkte Umstellung Tastatur/Full-Screen/Kompatibilität verändern würde.
- Kotlin: Explizites Opt-in zur vorhandenen flatMapLatest-API, erschöpfende Bildtyp-Wenn-Ausdrücke, redundante Nullprüfung beim BTB-Lead entfernt.
- Geocoder: Synchrone Legacy-Methode für alte Geräte auf IO belassen, Deprecation lokal begründet; keine ungeprüfte asynchrone Umstellung der Wetter-Standortlogik.
- Verbindlicher Quellcode-/CI-Nachweis: erster Korrekturcommit `f201fc2` -> GitHub Actions 44919530745 vollständig BESTANDEN; **nur drei** Kotlin-Warnungen im Original-Runnerlog (BautagebuchFragment historischer Nullcheck, DkwKreuzungSchemaView 2× scaledDensity). Zweiter Commit `4aa3df71304f4162dc39a521035910a2083a5b36` korrigiert exakt diese drei. Das Endergebnis des neuen CI-Run 44920144238 muss vor BESTANDEN/NULL WARNUNGEN ausdrücklich im Original-Log verifiziert werden.
- Kein Update auf Pixel/Poco, keine Synology-Aktion, keine Room-/API-/DB-Veränderung, drei Foto-Spalten, Kurztext/Langtext und alle historisch geschützten Funktionen bleiben erhalten. Rote KSP-IDE-NullPointerException gehört nicht zu den 19 gelben Compilerwarnungen und ist separat zu prüfen, falls reproduzierbar.
