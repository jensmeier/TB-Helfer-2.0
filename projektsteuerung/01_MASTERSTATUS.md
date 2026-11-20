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
