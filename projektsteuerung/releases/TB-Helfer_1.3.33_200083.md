TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.33
BUILD: 200083
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Live-Diagnose auf „Daten vom NAS“: Serverprüfung mit Zeitpunkt/Alter, laufende Sende-/Empfangs-/Vollsync-Schritte und gemessene Dauer.

VERBESSERT:
- Normales Warten auf Serveränderungen wird als normaler Empfangsabruf erklärt.
- Bekannte vorgemerkte BTB-/Strukturaufträge werden angezeigt; Fehler- und Konflikttexte bleiben erhalten.
- Keine unzuverlässige Prozent-/Restzeit: Bei unbekannter Datenmenge steht ausdrücklich „Restzeit: noch nicht berechenbar“.

BEHOBEN:
- Laufende Synchronisation ist nicht mehr nur durch einen allgemeinen Wartehinweis erkennbar.
- Parallele Sende-/Empfangsläufe besitzen getrennte Status- und Zeitangaben; Abbruch/Beendigung entfernt den jeweiligen Lauf.

UNVERÄNDERT:
- Enthält die Baustellen-/Team-Syncänderungen aus200082. Keine neue Serveränderung, kein neuer Schreibendpunkt, keine DB-Migration.
- Retry, Recovery, Cursor, Rollen, Daten und Originale werden durch die Diagnose nicht verändert.

SERVERVERTRAG:
- Zusätzlich zur bestehenden Synchronisation nur sichtbarkeitsgebundene lesende Prüfung von GET /api/v1/capabilities; Connect-/Read-Timeout je4s. Nach einer Antwort30s bis zur nächsten Prüfung.
- JSON-Capabilityantwort wird geprüft. Erreichbarkeit beweist weder Nutzerberechtigung noch vollständig übertragene Daten.

TESTSTATUS:
- Reiner Java-Monitor lokal mit Java17 kompiliert;13 Regressionsprüfungen einschließlich800 paralleler Lauf-Lebenszyklen bestanden.
- XML geparst, strukturelle Kotlin-Prüfung durchgeführt. KEIN erfolgreicher Android-/Gradle-/Kotlin-/KSP-/Lint-/APK-Build; Android-SDK/Gradle-Abhängigkeiten fehlen.
- Kein HTTP-/Emulator-/Pixel-/Poco-E2E, keine Installation oder Veröffentlichung. Keine APK vorhanden; DOWNLOAD_URL bleibt leer.
- Kein Actions-Lauf/NAS-Deployment. Diese MD später nur mit APK aus genau1.3.33/200083 verwenden.

NÄCHSTER TEST:
- Android Studio/Gradle: compileDebugKotlin, testDebugUnitTest, lintDebug; bei Erfolg assembleDebug.
- Online/Offline, falscher DNS/Timeout/HTTP-Fehler, langsam laufender Bildabgleich, paralleler Empfang und Senden, Seitenwechsel und Neustart auf Pixel/Poco prüfen. Laufzeit muss real wachsen, fertige/abgebrochene Läufe verschwinden; keine ausgedachte Restzeit.
