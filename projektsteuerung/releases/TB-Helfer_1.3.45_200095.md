TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.45
BUILD: 200095
MINIMUM_BUILD: 200050
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Dauerhaftes Streckendatenjournal; Empfang und Ausgangsstand werden gemeinsam in Room gespeichert.
- Unveränderte neu importierte Vermessungsoriginale bleiben lokal erhalten und werden über WLAN zur zugeordneten Baustelle übertragen und serverseitig geparst.
- Gemeinsame lineare KM-Abschnitte werden über eindeutige Originalsignaturen zugeordnet; dauerhafte Mutations-IDs schützen Wiederholungen.

BEHOBEN:
- Kein stilles Überschreiben nach Versionskonflikt und kein Löschen fremder Streckendaten durch unvollständige lokale Bestände.
- Empfang erhält lokale Achs-IDs und originale Achselemente; ungültige oder mehrdeutige Punktlisten werden abgewiesen.
- Mängel, Abnahmen und Streckendaten werden im Änderungsfeed berücksichtigt.
- Logo-Breite bis 400 pt; App-Version/-Build in authentifizierten API-Anfragen.
- Updateprüfung vergleicht zusätzlich das Signaturzertifikat mit der installierten App.

VORAUSSETZUNGEN UND GRENZEN:
- Server-Gegenstück GESAMT-1108 erforderlich; kein automatisches NAS-Deployment.
- Lokale Datenbank 19 -> 22: ausschließlich zusätzliche Journal-Tabelle. Experimentelle Datenbanken 20/21 werden nicht destruktiv zurückgesetzt; deren Übernahme bleibt vor Installation separat zu klären.
- Alte Importoriginale können nicht aus abgeleiteten Stützpunkten rekonstruiert werden und müssen bei Bedarf erneut importiert werden.
- KM-Sprünge sowie unbekannte GPS-Genauigkeit werden nicht in eine scheinbar gültige gemeinsame lineare Kalibrierung umgewandelt.
- Explizite gemeinsame Löschung und Konfliktauflösung von Trassen/KM-Beiträgen bleiben gesondert offen; lokale Fassungen bleiben erhalten.
- Gespeicherte Originale mit nicht unterstütztem Format sind kein erfolgreicher fachlicher Import.
- Vorhandener Signaturschlüssel muss für ein kompatibles Update verwendet werden. Ohne passenden Schlüssel ist diese APK nur ein Testbuild. Nicht deinstallieren.

PRÜFSTATUS:
- Build-/Testnachweis und APK-Prüfsumme werden aus demselben CI-Build ergänzt.
- Installation, Room-Migration auf Pixel/Poco und Synology-E2E sind offen.
- Word, Drei-Fotospalten und Fotoauswahl unverändert.
