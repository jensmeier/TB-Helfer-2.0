# TB-HELFER – TEST- UND E2E-STATUS
Stand08.11.2026 – unabhängige Gesamtprüfung.
Geprüfter SERVER-Fachstand `b213bcbfe8d5fcf49c17e71108b8be04167f4d7f`; ANDROID-Fachstand `a8401775a3f607594df91468e728ee3f6d3a1ec4`.
Nachfolgende Dokumentationscommits verändern diese geprüften Fachstände nicht.
[Vollständiger Prüfbericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [Unveränderte vorherige Fassungen](archiv/GESAMTPRUEFUNG_2026-11-08/README.md).

Begriffe verbindlich: QUELLCODE GEPRÜFT / SOURCE-TEST BESTANDEN / BUILD BESTANDEN / CI BESTANDEN / INSTALLIERT / DEPLOYT / E2E OFFEN / E2E BESTÄTIGT / ERLEDIGT. Keine dieser Stufen ersetzt die nächste.
## Nachweise dieses Prüfblocks
160bestehende lokale Skripte:112PASS,41Assertion-/Markerfehler,1Syntaxfehler,1Harnessfehler,5Umgebungsblockaden.45Server-Unittests PASS und1Modulimportfehler;2PDF-Funktionstests PASS;4Java-Regressionsprogramme PASS. Zwei neue Fehler reproduziert (echte Produktionsfunktionen, ausdrücklich DB-/SQL-Doubles).
Android-Buildjob SUCCESS,233Unit-Tests,0Lintfehler/391Warnungen; Gesamt-CI FAILURE. Ältere Server-CI mit echten PostgreSQL/HTTP/Chromium-Teilnachweisen, nicht vollständiger aktueller Serverstand. 255Wait-Clients≠15×15Fachlast. Postman-Beispielsammlung≠Synology-E2E.
## Aktuelle Geräte-/NAS-Abnahme
Pixel: installierter Build/Signatur OFFEN. Poco: installierter Build/Signatur OFFEN. NAS: laufender Commit/aktueller Migrationsstand OFFEN. Historisch bestätigte Teilfunktionen bleiben geschützt; keine erneute Gesamtbestätigung behauptet.
Folgende Prüffälle aus Abschnitt13 des [Berichts](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) sind für das aktuelle Branchpaar E2E OFFEN:
| ID | Abnahme |
|---|---|
| E01 | Pixel-Neubaustelle ohne Helfer → Server → Poco, keine Doppelanlage |
| E02 | Portaländerungen und gleichzeitige Handyänderungen, sichtbare Konflikte |
| E03 | Einträge ändern/löschen/verschieben, Firmenwechsel, Schlagwort-/Fotozuordnung |
| E04 | Helfer offline/App-Kill/Netzrückkehr/Rechteentzug |
| E05 | Schichtende/Abschluss/Wiederöffnung beider Richtungen |
| E06 | Foto<=2MB, Originalhash, GPS/KM/Metadaten/Fotoanzahl |
| E07 | Mängel/Abnahmen/Anlagen/XLSX und Fachdelta |
| E08 | Original→Katalog→manuelleAnker→zweitesGerät, Konflikte/Quellenbindung |
| E09 | PDF/Word/ZIP/portableBildliste auf Referenzfällen |
| E10 | Signaturkompatibles Update ohne Datenverlust |
| E11 | Recovery/Restore in eigener Testumgebung |
| E12 | Gemischte255Clientlast mit Fotos/PDF/Fachänderungen |
Keine produktiven Testdaten/Container ohne Auftrag verändern. Alte Testnachweise stehen unverändert im [Archiv](archiv/GESAMTPRUEFUNG_2026-11-08/06_E2E_TESTSTATUS.md).
