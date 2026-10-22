TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.27
BUILD: 200077
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Gemeinsame Bestandsübersicht bei Strecke/Bahnhof mit Suche, Objektarten und einzelnen KM-Abschnitten.
- Gezielte Objekt- und KM-Übernahme in Baustellenformulare; optionale Bahnhofsuche auf dem Server.
- Stabile Foto-Eintragszuordnung für Kamera und Galerie sowie bestehende serverseitige Fotolinks.
- Getrennte Zeitbindung REPORT/MANUAL für Personal und Maschinen.

VERBESSERT:
- Beginn und Ende von BTB-Einträgen einschließlich Nachtschichten und explizit gelöschter Endzeit.
- Effektive GPS-/Aufnahmezeit-Metadaten für Fotos, PDF und Export; unbekannte Werte bleiben unbekannt.
- Weichenverbindungen regulär auswählbar; Gleis/Weiche/WVB mit kompatibler Objektart im Sync.
- Freie Strecke übernimmt keinen Bahnhof; Kontextwechsel und wiederhergestellte Formulare sind geschützt.

BEHOBEN:
- Syncbilder bleiben unter der maximalen Uploadgröße von 2.000.000 Bytes.
- Gelöschte GPS-Werte werden nicht aus alten Dateinamen wiederhergestellt.
- WVB-Präfixe hinterlassen keinen VB-Rest in der Objektnummer.
- Reine äquivalente Darstellungs-/Metadatenübergänge sollen keine künstlichen Sync- oder PDF-Änderungen auslösen.

SERVERVERTRAG:
- Unveränderter Serverstand 0f82b42da7412a138bf99b6f80b93031e3b5373e.
- Bestehende Geräte-Bearer-, Katalog-, WorksiteNode-, BTB- und Foto-PATCH-Verträge.
- Keine server/**-Datei geändert, keine automatische Veröffentlichung.

TESTSTATUS:
- TESTBUILD IN VORBEREITUNG – noch kein erfolgreicher Gradle-/APK-Nachweis.
- Quellprüfungen und produktive JVM-Regexprüfungen der Implementierungsblöcke bestanden.
- Kotlin-Tests vorhanden; tatsächlicher CI-Lauf wird separat ausgewertet.
- Quellvertragsjob und Buildjob unabhängig, beide verpflichtend; Gesamt-CI kann trotz erfolgreichem Build durch einen Vertragsfehler rot bleiben.
- Installation und Pixel/Poco-/NAS-E2E offen.
- Release-APK-Signatur und Installierbarkeit vor Übergabe gesondert prüfen.

NÄCHSTER TEST:
- Unit-Tests, Debug/Release und Lint tatsächlich ausführen.
- Danach Foto-Uploadgröße/Zuordnung/GPS-Löschen, Eintrags-Endzeit, Firmenzeitbindung, Bestandsübernahme und freie Strecke auf Pixel/Poco und im Portal prüfen.
