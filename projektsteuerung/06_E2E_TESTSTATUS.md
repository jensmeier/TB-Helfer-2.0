# TB-HELFER – TEST- UND E2E-STATUS

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

Android BUILD BESTANDEN und gesamter CI-Lauf42495975421 BESTANDEN am Baucommit cda78c2f42fd7b1acd76d60612ce42d882662edf. 245 Unit-Tests in35Suiten,0Fehler/0übersprungen; Lint0Fehler/391Warnungen. APK1.3.46/200096 und exakt passende Release-MD heruntergeladen; Manifest/Commit/SHA256 gegengeprüft.
Server BUILD/CI BESTANDEN: vollständiger Lauf42496224535 am b924da3e9bbff603a78ca55753cefb439f1ac607 gegen Android cda78c2f42fd7b1acd76d60612ce42d882662edf. 66 Unit-Tests,100 Foundation-Prüfskripte,24Migrationen in frischer PostgreSQL-Testumgebung, Authentifizierungsablauf, Container-/Snapshot-/Restore-/Rechte-/Recoverygate sowie255Sync-Wait-Verbindungen erfolgreich. Kein produktiver NAS-Test.
Room INSTRUMENTIERUNG BESTANDEN: Lauf42496070087 am680ef37e9781eb4e4b0b8da04b5db7e6b2d42340, Android15/API35-Emulator,2/2Tests. Echtes Room/SQLite: additive Migration19→22 erhält Achse; Geometrie+Journal rollen bei Ausnahme zusammen zurück und werden bei Erfolg gemeinsam bestätigt; Journale sind nach Owner getrennt. Kein Pixel-/Poco-/NAS-Netzwerktest.
Begriffe: QUELLCODE GEPRÜFT / SOURCE-TEST BESTANDEN / BUILD BESTANDEN / CI BESTANDEN / INSTALLIERT / DEPLOYT / E2E OFFEN / E2E BESTÄTIGT / ERLEDIGT bleiben getrennt.

Pixel, Poco und Synology: aktueller Installations-/Deploymentstand und E01–E12 E2E OFFEN. Die Room-Instrumentierung ist ein echter Emulator-Datenbanktest, aber kein Zwei-Geräte-/NAS-Netzwerktest. Spezifische PDF-/Handlerprüfungen mit Doubles bleiben als solche gekennzeichnet.

| ID | Abnahme |
|---|---|
| E01 | Pixel-Neubaustelle ohne Helfer → Server → Poco, keine Doppelanlage |
| E02 | Portaländerungen und gleichzeitige Handyänderungen, sichtbare Konflikte |
| E03 | Einträge ändern/löschen/verschieben, Firmenwechsel, Schlagwort-/Fotozuordnung |
| E04 | Helfer offline/App-Kill/Netzrückkehr/Rechteentzug |
| E05 | Schichtende/Abschluss/Wiederöffnung beider Richtungen |
| E06 | Foto<=2MB, Originalhash, GPS/KM/Metadaten/Fotoanzahl |
| E07 | Mängel/Abnahmen/Anlagen/XLSX und Fachdelta |
| E08 | Original→Katalog→manuelleAnker→zweitesGerät, Konflikte/Quellenbindung |
| E09 | PDF/Word/ZIP/portableBildliste auf Referenzfällen |
| E10 | Signaturkompatibles Update ohne Datenverlust |
| E11 | Recovery/Restore in eigener Testumgebung |
| E12 | Gemischte255Clientlast mit Fotos/PDF/Fachänderungen |

Für die10zugeordneten CI-Läufe wurden20Jobversuche mit zusammen53,23Minuten gemessener Joblaufzeit erfasst, einschließlich Fehlern/Wiederholungen. Das sind Laufzeitdaten, keine Abrechnung. Tatsächlich berechnete Minuten, Monatsverbrauch und Freiminutenrest bleiben mangels Billingzugriff OFFEN. Zusatzbudget0USD unverändert; keine Einsparung behauptet.
Geschützt und erhalten: Word, drei Bilder pro Reihe, gespeicherte Fotoanzahl, stabile IDs und Originale sowie historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT-Markierungen. Neue Tests sind keine erneute pauschale Praxisbestätigung. Ungeprüfte ältere Restaufträge bleiben im Archiv offen zur Einordnung.
