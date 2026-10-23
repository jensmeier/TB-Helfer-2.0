TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.29
BUILD: 200079
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Kontrollierter Bestandswechsel nach einer Serverwiederherstellung: Handybestand privat sichern, aktuellen Server lesend vergleichen und ausdrücklich getrennt neu einlesen.
- Gesicherte Handybestände einschließlich vorgemerkter Änderungen unter Wartung und Diagnose ansehen und bewusst exportieren.

VERBESSERT:
- Gemeinsame HTTP-Sperre vor Übertragungen; eine Wiederherstellungskennung wird nur beim lesenden Vergleich oder im ausdrücklich ausgewählten neuen lokalen Bestand gesendet.
- Originaldateien, alte Datenbanken, Einstellungen und vorgemerkte Änderungen bleiben im vorherigen Bestand erhalten. Geräteanmeldung und aktivierte Serveradresse bleiben bestehen.
- CI führt JVM-Tests und Android-Lint ohne assemble oder APK-Erzeugung aus.

BEHOBEN:
- Wiederherstellungssperre hat jetzt einen geführten Android-Ablauf statt einer bloßen Fehlermeldung.
- Alte Uploads und Synchronisierungsmarken werden nicht ungeprüft in den wiederhergestellten Serverbestand übernommen.

GRENZEN:
- Keine automatische Zusammenführung alter lokaler Ergänzungen. Gesicherte Ergänzungen müssen bewusst geprüft und später übernommen werden.
- Vergleich liest zugängliche Baustellen, eigene TB-Gruppen-BTBs, Baum, Mängel, Abnahmen und gemeinsamen Strecken-/Bahnhofskatalog; Dateien und Vermessungsdetails erst im anschließenden normalen Sync.
- Keine server/**-Änderungen und keine NAS-Konfigurationsänderungen.
- Kein APK-Paket aus dieser Arbeit. Installation erfolgt über den bestehenden Android-Studio-Ablauf.

TESTSTATUS:
- Android-Compiler und testDebugUnitTest bestanden: 88 Tests, 0 Fehler/Fehlschläge/übersprungen.
- lintDebug bestanden: 0 Fehler, 391 Warnungen.
- CI-Lauf 39947580253, Runtime-Quellstand d3df3108fd66920c7c1bc1bfc93d5817eacedf33.
- Fünf historische Quellvertragsprüfungen bleiben offen; der Android-Compiler-/Test-/Lint-Job ist grün.
- Vollständiger Nachweis und Geräteprüfmatrix: projektsteuerung/uebergaben/ANDROID_Wiederherstellung_200079.md.
- Kein Pixel-/Poco-/NAS-Livetest aus dieser Umgebung. Funktionale Freigabe erst nach Geräteprüfung.
