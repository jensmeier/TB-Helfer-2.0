TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.38
BUILD: 200088
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: Noch nicht veröffentlicht; APK und Begleitdatei werden gemeinsam als Buildartefakt bereitgestellt.

## Änderungen und Fehlerbehebungen
- Widget und BTB-Mikrofon öffnen die direkte Aufnahme für den aktiven BTB.
- Aufnahmeansicht mit Baustelle/Datum, erkanntem Text und großer Stopptaste; technische Optionen separat unter Einstellungen Spracheingabe.
- Nach Abschluss öffnet die Prüfung des lokalen Entwurfs mit editierbarem Text, Firma, BTB-Punkt und Unterbaustelle.
- Eindeutige Ortsnamen werden vorgeschlagen; mehrdeutige Zuordnung bleibt offen. Farbige Statusanzeigen enthalten auch Text.
- Übernahme ausschließlich nach ausdrücklicher Bestätigung über bestehende Rechte-, BTB-, Outbox- und Speicherschutzprüfungen.
- Später prüfen und Unterbrechung erhalten bereits gesicherte lokale Textentwürfe. Keine Roh-Audiospeicherung und kein stiller Onlinewechsel.
- Bestehende Mikrofon-Widgets erhalten beim App-Update das korrigierte Startziel.

## Prüfgrenzen
Quellbegleitdatei; Build ergänzt die ausgelieferte Kopie mit Commit, Manifest, SHA256 und Signaturprüfung.
Pixel-/Poco-Gerätetest und Android-Server-E2E offen. Kein Servercode und kein API-Vertrag geändert.
CI-Debug-Signatur muss zur installierten App passen; bei Abweichung nicht deinstallieren oder App-Daten löschen.
