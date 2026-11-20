# TB-HELFER – UMSETZUNG UND OFFENE PUNKTE

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

| ID | Zuständig | Status | Umsetzung | Restaufgabe |
|---|---|---|---|---|
| AUDIT-1108-01 | SERVER | IMPLEMENTIERT / gezielte Regression bestanden | Trassendaten und Vermessungsabschnitte prüfen Leserecht und vollständigen KM-Bereich; unbekannte Geometrie wird für eingeschränkte Helfer gesperrt. | Reale Helfer-/Rechteentzugsmatrix auf NAS offen. |
| AUDIT-1108-02 | SERVER | IMPLEMENTIERT / Fehlerinjektion bestanden | PDF-Cleanup nach bestätigtem DB-Commit kann die bestätigte Datei nicht mehr zurückrollen; vorherige Fehler rollen zurück. | NAS-Dateisystem-/DB-Abnahme offen. |
| TRACK_KM_1103 | ANDROID | IMPLEMENTIERT / Build und Dreiwege-Tests | Persistentes Journal, ursprüngliche Basisversion, kein stilles 409-Rebase und keine Löschung nur wegen lokal fehlendem Bestand. Empfang validiert und atomar; Achs-IDs/Originalelemente bleiben. | Explizite Löschabsichten und bedienbare Konfliktauflösung offen. |
| SHARED-KM-1103 | ANDROID + SERVER | TEILWEISE IMPLEMENTIERT | Originalhash-Resolver, dauerhafte Beitrags-ID und exakte Request-Wiederholung; lineare Abschnitte/GPS-Anker mit Genauigkeit; Empfang über vorhandenen Katalog. | KM-Sprünge, GVP-/Mast-Bearbeitung, Deaktivierung und Konflikt-UI offen. GVP-/Mast-Herkunft wird bei Änderung geschützt. |
| ORIGINAL-TRANSPORT | ANDROID | IMPLEMENTIERT / Geräte-E2E offen | Neu importierte Rohdateien bleiben unverändert lokal; WLAN-Outbox lädt sie zur eindeutig gebundenen Baustelle hoch, registriert Original und ruft Parser auf. | Alte Originale erneut importieren; Mehrdeutigkeiten und fachlich nicht unterstützte Formate separat prüfen. |
| AUD-20261002-01 / -02 | SERVER | IMPLEMENTIERT | Manuelle GPS-Teilabschnitte werden nicht blind vom Original verdrängt; Objektkennung bleibt im Updatepfad erhalten. | Reale W/WVB-/Teilüberdeckungsfälle abnehmen. |
| ADMIN-PDF-011 | ANDROID | IMPLEMENTIERT | Logo-Vertrag 40–400 pt übernommen. | Praktischer Geräte-/Portalvergleich offen. |
| DEVICE-INFO-001 (= DEV-META-001) | ANDROID | IMPLEMENTIERT | Versions-/Buildheader bei authentifizierten API-Anfragen. | Anzeige auf tatsächlich aktualisierter NAS prüfen. |
| Selektiver Fachsync | ANDROID | TEILWEISE IMPLEMENTIERT | DEFECT, ACCEPTANCE und TRACK_DATA im Feed behandelt. Bei Fachänderung wird die betroffene Fachliste der Baustelle aktualisiert. Trackkonflikt blockiert andere Feedtypen nicht dauerhaft; persistente Wiederholung. | Objektweise Fach-Deltas und globaler Katalog-Feed offen; derzeit zusätzlicher periodischer KM-Abgleich. |
| EXPORT-1108B Signierung | ANDROID | TEILWEISE IMPLEMENTIERT / INSTALLATION BLOCKIERT BIS ZERTIFIKATABGLEICH | APK-Prüfung vergleicht Signatur mit installierter App; CI kann vorhandenen Schlüssel aus Secrets verwenden. | Vorhandener privater Installationsschlüssel nicht verfügbar; Debug-Testbuild ist kein belegtes kompatibles Update. Nicht deinstallieren. |
| BTB-AUTO-016 | SERVER + ANDROID | SERVER IMPLEMENTIERT | Worker beendet aktive BTBs nach 16 h über vorhandenen PDF-/Abschlusspfad; Status/Frist unter Sperre erneut geprüft. REWORK ausgeschlossen. | Offline-Android-Nachlauf/Anzeige und verspätete Helferdaten praktisch prüfen; endgültiger Abschluss kann ohne Verbindung lokale Daten nicht kennen. |
| AUDIT-1108-03 / CI-Verträge | GEMEINSAM | IMPLEMENTIERT | Querchecks verwenden explizite Gegenbranch-SHAs. Veraltete Text-/Payload-Erwartungen fachlich korrigiert; Sicherheits-/Recovery-/Migrationsprüfungen erhalten. | Jeder neue Fachstand braucht aktualisierte Paarprüfung. Kein blinder Branchmerge. |
| NAS-/Geräteabnahme | GEMEINSAM | E2E OFFEN | Keine Änderung von NAS-Containern, Produktivdaten oder installierten Pixel-/Poco-Versionen. | Versionen/Signatur feststellen, dann separate Übernahme und E01–E12. |
| SYNC-001 | GEMEINSAM | TEILTEST BESTANDEN | 255 gleichzeitige Sync-Wait-Verbindungen in echter CI-Testumgebung. | Gemischte 15×15 Fach-/Foto-/Portal-Last und NAS-Dauerbetrieb offen. |
| Legacy-Restbacklog | JEWEILIGER BEREICH | OFFEN ZUR EINORDNUNG | Frühere nicht einzeln neu belegte Punkte bleiben im Archiv erhalten. | Keine automatische Erledigung oder pauschale Neuöffnung geschützter Funktionen. |

Geschützt und erhalten: Word, drei Bilder pro Reihe, gespeicherte Fotoanzahl, stabile IDs und Originale sowie historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT-Markierungen. Neue Tests sind keine erneute pauschale Praxisbestätigung. Ungeprüfte ältere Restaufträge bleiben im Archiv offen zur Einordnung.

Bereits vorhandene Export-/Metadaten-/Wiederöffnungs-/Live-/Rechtefunktionen nicht aufgrund veralteter Marker erneut als fehlend anlegen. Ältere Restaufgaben: [Auditregister vor Umsetzung](archiv/UMSETZUNG_2026-11-08_VORHER/07_OFFENE_PUNKTE.md) und [historischer Restbestand](archiv/GESAMTPRUEFUNG_2026-11-08/07_OFFENE_PUNKTE.md).


## KOMPAT-1120 – gemeinsamer Entwicklungsauftrag (20.11.2026)
Benutzer hat GitHub-Bereinigung ohne Verlust historischer Nachweise, synchrone Android-/Server-Statusführung und gezielte Fehlerkorrekturen freigegeben. Basis: Android 1.3.46/200096 Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf, Server 1.0.0-dev/API v1/PDF 6 Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Basispaarung ist keine reale Pixel/Poco/NAS-Abnahme. Neue fachliche Commits gesondert ausweisen. E01–E12 bleiben offen. Keine NAS-/Produktivdatenänderung. Neue Fehler F-01 bis F-07 in 09_KOMPATIBILITAETSBASIS.md, Altbestätigungen und Archiv behalten.


### PRUEF-1120-1BIS5 – neue Arbeitsblöcke
- F-03 SERVER: Change-Feed fachobjekt-/team-/bereichssicher machen, Cursor/Wait/SSE/Löschungen und Rückfall testen; weiterhin OFFEN.
- F-05 SERVER: Originale GLOBAL vs. COMPANY vs. baustellengebunden sauber abgrenzen, bisher geteilte Fachkatalogreferenzen erhalten; weiterhin OFFEN.
- F-01/F-02/F-04/F-06 SERVER und F-07 ANDROID: Korrekturcode bereits vorhanden, gezielte neue Regressionen prüfen, nicht allein CI-Summary vertrauen.
- Android 20/21→22 und vorhandene App-Signatur: BLOCKIERT bis realer Testbestand vorliegt. Keine Deinstallation als Abkürzung.
- Synology-Deployment/Pixel/Poco-E01–E12: NICHT AUSGEFÜHRT. Vorbereitungs- und Nachweisliste in `uebergaben/PRUEF_1120_1_BIS_5.md`.

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


## FACH-1120 – neu beauftragte Prüfungen (nicht automatisch Fehler!)
- ANDROID: Texteingabe/Tastatur-Korrekturen und Fachwörter, Spracheingabe/Entwurfsbestätigung, Fotoeditor und lokale Änderungsvormerkung.
- ANDROID + SERVER: korrigierbare Foto-Firma, Kurztext/Langtext, Zuordnung und Metadaten über alle Geräte; Schreib-/Leserechte, Konfliktschutz, PDF/Word/XLSX/ZIP.
- ANDROID + SERVER: Tages-/Gesamt-Bildlisten XLSX, Mängel-/Abnahmeliste, Dateipfade nach Ordnerkopie, keine unerwünschte Gesamt-CSV; ZIP-Erstellung und Suche Bilder/BTB vollständig prüfen.
- ANDROID + SERVER: Wetter bei Schichtbeginn UND Schichtende; Temperatur, Niederschlagsmenge einschließlich 0,0 mm und Wetterlage; Unterschiede zwischen Android Direktabruf und Servervorschlag; Übernahme nach BTB/Word. Benutzer meldet derzeit Wetterfehler, genaue Ursache OFFEN.
- E2E-Pixel/Poco/Synology sowie isolierte Regressionsnachweise ausstehend; bekannte gelöste Fehler nicht erneut als offen behandeln. Vollständiger Auftrag unter `projektsteuerung/uebergaben/FACH_1120_TEXT_FOTO_ZIP_WETTER.md`.
