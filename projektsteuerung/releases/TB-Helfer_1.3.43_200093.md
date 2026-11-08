TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.43
BUILD: 200093
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: NICHT_VERÖFFENTLICHT

INTEGRATION-1108:
- BTB-Verlauf: ZIP teilen und ZIP speichern exportieren genau den gewählten lokalen BTB ohne vorgeschalteten Serverkatalog oder Synchronisierung.
- Inhaltshinweis nennt lokalen Umfang, fehlende Bilddateien und Unterschied zwischen gespeichertem Abschluss-PDF und aktuellem Word-Stand. Vollständiger Baustellenexport bleibt separat mit Katalogprüfung.
- Bilddateien werden im lokalen Export nur anhand gespeicherter Attachment-ID bzw. identischem Dateipfad zusammengefasst; vorhandenes Original vor Vorschau. Keine Datenbankbereinigung.
- Wetter verwendet gespeicherten BTB-Ort, danach passende GPS-Fotos; aktueller Handystandort nur für heutige Berichte. Fehler werden angezeigt, vorhandene Werte bei Abruffehlern bewahrt. Nullwerte bleiben unbekannt, 0 mm ist trocken.
- Wetterabruf prüft Ort, Zeitraum und unveränderten Bearbeitungsstand; weitere vom Portal gespeicherte Wetterzeiten bleiben erhalten.
- PDF-Einstellung zeigt verbindlich drei Bilder pro Reihe; bestehende Fotoanzahl und Originaldateien bleiben erhalten.

TESTSTATUS:
- Regressionen für Exportidentitäten und Wettergrenzen ergänzt; tatsächliches Build/Lint/Unit-Ergebnis separat dokumentiert.
- Bekannte Source-Contract-Probleme des Ausgangsstands bleiben separat offen; kein grüner Gesamtworkflow behauptet.
- Pixel/Poco: E2E OFFEN. ZIP offline, Speichern/Teilen, Rotation und Vergleich mit Server nach dessen Übernahme erforderlich.
- Keine NAS-Installation, keine Migration, kein produktiver Schreibtest.
- Debug-Testbuild. Update mit bestehender Signierung erforderlich; bei Signaturkonflikt NICHT deinstallieren.

BAUSTATUS 08.11.2026:
KEINE APK ERZEUGT. CI42472214428 und42472373330 scheiterten vor Kompilierung am Gradle-Downloadtimeout. Unit/Lint/Manifest/Signatur offen. Diese Datei ist die vorbereitete Releasebeschreibung, kein Nachweis einer installierbaren APK.
