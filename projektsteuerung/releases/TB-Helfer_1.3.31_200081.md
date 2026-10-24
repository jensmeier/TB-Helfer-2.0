TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.31
BUILD: 200081
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Schichtende, bewusstes Fortsetzen und BTB-Abschluss werden dauerhaft auf dem Handy vorgemerkt und bei Verbindung nachgeholt.

VERBESSERT:
- Abschluss zeigt Vormerkung, laufenden Serverabgleich und ausstehende Bestätigung bzw. Teilnehmerblocker.
- Betroffene Einträge, Bilder und geänderte Struktur werden vor dem Abschluss abgeglichen.

BEHOBEN:
- Wiederholtes Tippen löst keine unbeabsichtigte Schichtfortsetzung aus.
- Wiederholungen verwenden dieselbe Auftragskennung; Serverstatus wird vor erneutem Senden geprüft.

SERVERVERTRAG:
- Bestehende shift-finish/shift-resume/finalize-Endpunkte und Delta-Feed. Keine neue Servermigration, kein NAS-Deployment.
- Helfer beenden nur ihre eigene Schicht. Trotzdem-Abschluss nur nach ausdrücklicher Bestätigung der Teamleitung.
- Offlinegeräte übernehmen den Serverstand erst beim nächsten erfolgreichen Abruf. 16-Stunden-Autoabschluss ist ein separater offener Auftrag.

TESTSTATUS:
- Finaler Runtimecode2570b4c222fa5fb9338e4471a22421425a99e8c1: CI40108708246, Androidjob120915704503 SUCCESS. Kotlin-Kompilierung und108 JVM-Tests bestanden (0 Fehler/Fehlschläge/übersprungen;10 neue Lifecycle-Tests). Lint0 Fehler/0 Fatal/391 Warnungen; XML geprüft.
-18 gezielte Sourcechecks bestanden. Gesamt-CI weiterhin rot durch fünf bekannte historische Quellvertragsprüfungen13/19/83/85/86; keine Tests abgeschwächt.
- Keine APK erzeugt oder veröffentlicht; Installation und Pixel/Poco/Portal-E2E offen. Offline-/Neustart-/HTTP-Abbruch auf echten Geräten nicht durch Policy-Unit-Tests bewiesen. Übergabe: projektsteuerung/uebergaben/ANDROID_BTBSCHICHT_ABSCHLUSS_200081.md.

NÄCHSTER TEST:
- Helfer offline Schicht Ende, Neustart, Verbindung wiederherstellen; TB sieht beendet, schließt ab; beide Handys und Portal gleichen Zustand prüfen.
- Doppelklick, Bildfehler, paralleler Save, Versionskonflikt und POST-Erfolg mit fehlgeschlagener Statusabfrage prüfen.
