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
