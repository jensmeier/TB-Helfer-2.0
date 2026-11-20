TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.47
BUILD: 200097
MINIMUM_BUILD: 200050
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Im Fotodetail vorhandene Firma, Kurztext und ausführlichen BTB-/Word-Text korrigieren, ohne Bilddatei, GPS oder Foto-ID zu ersetzen.
- Serververwaltete Firmendaten im Profil bei Bedarf ein- oder ausklappen.
- Wetterzeitpunkte im Android-BTB: Schichtbeginn, Schichtmitte und Schichtende statt drei reiner Zwischenzeitpunkte.

VERBESSERT:
- Korrigierte Fototexte bleiben getrennt: Foto/Foto-XLSX zeigt nur Kurztext, Langtext gehört in BTB und Word.
- Für Kurz-/Langtexte Tastatur-Rechtschreibvorschläge freigeben; für Firmennamen automatische Wortkorrektur deaktivieren.
- Spracherkennung speichert Zwischentexte gedrosselt, das vollständige Endergebnis weiterhin explizit.
- BTB-Suche wird beim Neu-Laden tatsächlich erneut ausgewertet.
- Tagesabschluss-ZIP wird vor Weitergabe gestreamt und auf Einträge/CRC kontrolliert.
- BTB-Foto-Text- und Kontextkorrekturen werden atomar auf dem Handy gesichert.
- Alte Firmen-ID nach Änderung des Firmennamens nicht weiterverwenden.

BEHOBEN:
- BTB-Suchaktualisierung über gleichen StateFlow-Wert löste keinen erneuten Suchlauf aus.
- Nichtatomisches Löschen der alten Override-Datei vor dem endgültigen Schreiben.
- Weitere Foto-Freitextkorrektur auf dem Handy war aus der Bilddetailansicht nicht zugänglich.

UNVERÄNDERT:
- Kameraoriginal und Foto-ID, PDF-Grundlayout, Word-Aufbau, drei Fotos pro Zeile, geschützte Fotoanzahl und alle bestätigten Funktionen.
- Kein erzwungener Android-DB-Reset. Room Schema22, Migrationspfade 20/21 weiterhin gesondert zu prüfen.

SERVERVERTRAG:
- Server feature/tb-server-1.0 Fachcode 451c4310060b56a99ea631d144a6d2d40a75fb2d; API v1 / PDF 6.
- Foto-Korrekturen in BTB verwenden die bestehenden versionsgeschützten PATCH- und Offline-Sync-Wege.
- Allgemeine (nicht BTB-gebundene) Fotos und Portal-Fotoeditor sind noch nicht als vollständig geräteübergreifend korrigierbar abgenommen.

TESTSTATUS:
- GitHub Android CI für diesen Fachstand sowie Geräteinstallation und echter Pixel/Poco/Synology-E2E sind erst nach tatsächlichem Test als BESTANDEN zu melden.
- Noch keine APK mit diesem Build erzeugt, signiert, installiert oder auf dem Server als Update veröffentlicht.
- Bei Signaturabweichung bestehende App NICHT deinstallieren, um lokale Daten nicht zu verlieren.

NÄCHSTER TEST:
- Android Studio: Gradle Sync, testDebugUnitTest, assembleDebug (mit passendem bestehendem Signierschlüssel), Emulator.
- Foto im BTB aufnehmen; Firma, Kurztext, Langtext ändern; ZIP/XLSX/Word vergleichen.
- ZIP ohne Fotos, ZIP mit vielen Bildern und beschädigten Einträgen testen.
- Suchliste nach neuem BTB aktualisieren; BTB-Wetter Schichtstart/Schichtende samt 0,0 mm überprüfen.
- Pixel/Poco/Portal und Synology weiterhin E2E OFFEN.
