# TB-HELFER – ANDROID STATUS

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

Android BUILD BESTANDEN und gesamter CI-Lauf42495975421 BESTANDEN am Baucommit cda78c2f42fd7b1acd76d60612ce42d882662edf. 245 Unit-Tests in35Suiten,0Fehler/0übersprungen; Lint0Fehler/391Warnungen. APK1.3.46/200096 und exakt passende Release-MD heruntergeladen; Manifest/Commit/SHA256 gegengeprüft.

Umsetzung: WorksiteTrackDataSync/Api mit gespeichertem Ausgangsstand, Konfliktschutz ohne Rebase/implizite Löschung, vollständiger Validierung und atomarem Room-Empfang; Achs-IDs/Originalelemente bleiben erhalten. Journal liegt in derselben Hauptdatenbank, Schema19→22 additiv. Arbeitsaufträge dauerhaft über WorkManager, zusätzlich15min-Fallback. SharedKmSync über eindeutige Originalsignatur, Quellenversion, dauerhafte Beitrags-ID und exakte ausstehende Anfrage; pro Prozess serialisiert. Originaloutbox bewahrt neue Importbytes und nutzt WLAN, vorhandenen Datei-/Original-/Parservertrag. Fachfeed lädt betroffene Mängel-/Abnahmeliste; Trassenkonflikte werden protokolliert/persistent wiederholt. Logo40–400, authentifizierte Versionsheader und APK-Zertifikatsvergleich implementiert.

Die beigefügte APK ist ein DEBUG-Testbuild. APK_SHA256: 80b11549b88a10dffe84ade49557393ad47c149adcaa9ee571cafe7c2f351c7d; Größe: 27044724 Bytes. Signatur-Zertifikat SHA256: 9d90db043eb1a5658761a4cc9a2f2164d5ebcb73fd31f510f84c9c42b79f7d76.
Die CI-Prüfung bestätigt APK-Signatur und Manifest, aber nicht die Übereinstimmung mit der installierten Pixel-/Poco-App. Im Repository liegt kein vorhandener privater Signierschlüssel; ein kompatibles Update ist deshalb nicht belegt. Der Updateclient prüft die installierte Signatur und weist Abweichungen ab. Nicht deinstallieren, um eine Signaturabweichung zu umgehen. Für das reguläre Update den vorhandenen Installationsschlüssel verwenden; die CI-Anbindung dafür ist vorbereitet.
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
