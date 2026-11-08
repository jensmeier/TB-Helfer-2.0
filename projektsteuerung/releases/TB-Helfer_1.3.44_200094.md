TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.44
BUILD: 200094
MINIMUM_BUILD: 200050
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Mängelliste_Gesamt und Abnahmeliste_Gesamt als XLSX im Baustellenexport: ein Vorgang pro Zeile, separates Fotoblatt mit relativen Bildverweisen und Kennzeichnung fehlender Exportdateien.

VERBESSERT:
- Fotolisten mit echten Datum-/Zeitwerten, automatisch angepasster Zeilenhöhe, gut sichtbarem Bildlink, anklickbarem GPS und wiederholter Druckkopfzeile im Querformat.
- Relative Links schützen Leerzeichen, Umlaute, Plus-, Prozent- und Nummernzeichen. XLSX und Bilderordner gemeinsam verschieben; Ordnerstruktur erhalten und ZIP vollständig entpacken.

BEHOBEN:
- Neu erzeugte Android-PDFs zeigen Bahnhof und Gleis/Weiche auch bei vorhandenem Kurzname. Bestehende abgeschlossene PDF-Dateien werden nicht überschrieben.
- Bilderliste_gesamt.csv wird nicht mehr ausgegeben. Andere CSV/PDF-Ausgaben bleiben erhalten.

GESCHÜTZT:
- Word-Erzeugung unverändert. Drei Fotospalten, Fotoauswahl, Offline-Einzel-BTB-Export, IDs, Originalfotos, Rechte, Sync und Recovery bleiben erhalten.
- Excel-Ausgaben sind exportierte Stände; Excel-Bearbeitung synchronisiert nicht zurück.

PRÜFSTATUS:
- Build-/Testnachweis wird im APK-Build ergänzt. Installation und E2E auf Pixel/Poco, Windows-Excel und Synology sind separat offen.
- Debug-APK nur bei passender Signatur als Update installieren; bei Signaturkonflikt nicht deinstallieren.
