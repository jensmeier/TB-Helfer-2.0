# TB-HELFER – GEMEINSAME KOMPATIBILITÄTSBASIS

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 20.11.2026, Auftrag KOMPAT-1120

## Geprüftes historisches Basispaar
Android: feature/tb-android-2.0, 1.3.46 / 200096, Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf; Room-Instrumentierung 680ef37e9781eb4e4b0b8da04b5db7e6b2d42340.
Server: feature/tb-server-1.0, 1.0.0-dev / API v1 / PDF 6, Fachcode b17530d305797837d42f3049681f10cd919ea4b5; Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607.
CI Android 42495975421, Room 42496070087, Server 42496224535 erfolgreich. Synology/Pixel/Poco und E01–E12 E2E OFFEN.

## Verbindlicher Prozess
- Vor jeder Weiterarbeit HEAD beider Branches prüfen. Nur tatsächlich getestete Commit-Paarungen als CI BESTANDEN melden; Builds, Installation und E2E getrennt.
- Fachcode Android ausschließlich im Androidbranch, Server ausschließlich im Serverbranch; gemeinsame 00–09-Projektsteuerung auf beiden Branches gleich pflegen. Keine blinden Merges, kein Zurücksetzen alter Historie.
- Bei jeder Fachcodeänderung Gegenbranch-Commit explizit im CI fixieren und Vertrag abgleichen; Versionsnamen und Tests nicht ohne Nachweis aktualisieren.
- Keine produktive Migration, NAS-Übernahme, APK-Installation oder neue kostenpflichtige Dienste ohne gesonderten Auftrag.
- Geschützt: Word, drei Fotos/Reihe, Fotoanzahl, Originalfotos/-dateien, BTB-Original, stabile IDs und historisch bestätigte Funktionen.
- Fehler niemals automatisch als erledigt markieren; E01–E12 bleiben bis zur echten Abnahme offen.

## Fehlerliste Audit 19.11.2026 (Quellcode-Befunde, E2E nicht ausgeführt)
F-01 KRITISCH SERVER: files.py Dateirückverschiebung nach bestätigtem DB-Commit bei fehlgeschlagener PDF-Bereinigung.
F-02 HOCH SERVER: track_data.py und survey_sections.py Schreibrechte ohne vollständige Bereichsprüfung.
F-03 MITTEL SERVER: sync.py Changefeed enthält potentiell team-/bereichsfremde Metadaten.
F-04 HOCH SERVER: survey_originals.py from-file ohne explizites Import-/Dateirecht.
F-05 HOCH SERVER: survey_originals.py firmenweiter Originaldownload auch bei engerer Baustellenfreigabe; fachliche Freigabe vorher beachten.
F-06 MITTEL SERVER: survey_sections.py LIMIT vor Bereichsfilter.
F-07 MITTEL ANDROID: SurveyOriginalOutbox.kt ein SHA-basierter Auftrag für mehrere Baustellenbindungen.
Bekannt offen: Room 20/21, Offline-16h, KM-Sprünge und GVP-/Mast-Bearbeitung, vollständiger Fachdelta/Globalfeed, E01–E12.

## Verifizierter CI-Nachtrag 20.11.2026 – PRUEF-1120-1BIS5
- SERVER: Commit `4fbc47c5d7d64afc82129c030cb13260148e8639` mit vier ausführbaren, im Original-Runnerlog namentlich belegten KOMPAT-Regressionstests (Bereichsschreibschutz, Nur-Lesen-Originalimport, >200 unsichtbare Survey-Treffer, Post-Commit-PDF-Cleanup-Fehler) im vollständigen GitHub Actions Run [44645761732](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44645761732) **CI BESTANDEN**. Foundation-Job samt Runtime-Regressions und Snapshot-/Rechte-/255-Client-Job erfolgreich. Die Fehlerinjektion protokolliert absichtlich eine Cleanup-Ausnahme, das Testergebnis ist trotzdem erfolgreich.
- ANDROID: Commit `1a4184b392d9fd4239fbce9fc67017232f03f495`, `SurveyOriginalOutboxTest.kt` (gleiche Quelle/verschiedene Baustellen, stabile Delivery-Keys, Alteinträge) im vollständigen GitHub Actions Run [44645898340](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44645898340) **CI BESTANDEN**: Gegenserver-Source-Contracts, Unit-Tests und Lint erfolgreich; APK-Schritt bewusst **SKIPPED** (kein neuer APK-Auftrag/Release).
- Kein neuer versionCode/versionName; das zuvor dokumentierte Release 1.3.46/200096 darf nicht als mit den Teständerungen gebaut oder installiert bezeichnet werden.
- F-03/F-05 bleiben OFFEN; erfordern erst eine sichere Abgrenzung der gemeinsamen GLOBAL-/COMPANY-Katalogsicht gegenüber privaten Baustellen-/Team-/KM-Rechten. Genaue Abnahmematrix: `projektsteuerung/uebergaben/PRUEF_1120_1_BIS_5.md`.
- Synology-Deploy, reale Pixel-/Poco-Installationssignatur, Datenbank 20/21 und E01–E12 weiterhin **OFFEN**. GitHub/CI ersetzt diese E2E-Nachweise nicht.
