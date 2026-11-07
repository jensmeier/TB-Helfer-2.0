TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.42
BUILD: 200092
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: NICHT_VERÖFFENTLICHT

NEU:
- Sichtbarer Tages-ZIP-Status und lokaler Fehlerprotokolleintrag bei Export-/Teilenfehlern.

VERBESSERT:
- Tages-ZIP läuft bei Bildschirmdrehung weiter; fertiges Ergebnis wartet auf den aktiven Verlauf.
- Mehrfachklicks starten keinen parallelen Tagesexport.

BEHOBEN:
- Bestätigter Absturz in BtbVerlaufFragment: kein requireContext mehr nach asynchronem Export oder in dessen Fehlerbehandlung.
- Coroutine-Abbruch wird weitergereicht und nicht als ZIP-Fehler behandelt.
- Verlaufsladen an Lebensdauer der Ansicht gebunden.

UNVERÄNDERT:
- ZIP-Inhalte, PDF, Fotozuordnungen, Originaldateien und Serververtrag.

TESTSTATUS:
- Sechs gezielte Coroutine-Regressionen hinzugefügt; tatsächliche Build-/Testergebnisse separat dokumentiert.
- Pixel/Poco-Geräteabnahme offen. Kein direkter Gerätezugriff.
- Bekannte Quellvertragsprüfungen 13/19/83/85/86 aus Altstand separat offen.

NÄCHSTER TEST:
- Datenbewahrendes Update mit bisheriger Signierung; bei Signaturkonflikt nicht deinstallieren.
- Tages-ZIP ohne/mit Fotos, Rotation während Export, Hintergrund/Rückkehr, Mehrfachklick, Fehler/erneuter Versuch und normales Teilen.
- Beim Verlassen des Verlaufs wird der Auftrag beendet; nach Prozessende Export neu starten.
- Debug-Testbuild, kein Serverkatalog-Release oder NAS-Deployment.
