# Excel-Bildsuche fuer TB-Helfer

Optionales VBA-Modul fuer Excel Desktop unter Windows. Quelldatei: TBHelferBildsuche.bas.

## Einmal einrichten
1. Eine Kopie von Bilderliste_gesamt.xlsx in Excel oeffnen und als Excel-Arbeitsmappe mit Makros (.xlsm) speichern. Das Original behalten.
2. Alt+F11 oeffnet den VBA-Editor. Datei → Datei importieren → TBHelferBildsuche.bas auswaehlen. Den Editor schliessen.
3. Im Blatt der Bilderliste Alt+F8 → SuchknopfAnlegen → Ausfuehren. Der Knopf erscheint rechts neben der Tabelle. Alternativ BilderSuchen direkt ueber Alt+F8 starten.
4. Als .xlsm speichern. Wenn die Datei spaeter umbenannt wird, das Makro BilderSuchen ueber Alt+F8 starten und den Knopf bei Bedarf neu zuweisen.

Nur eigene/gepruefte Makros entsprechend der vorhandenen Firmenrichtlinie ausfuehren. Keine pauschale Freigabe aller Makros und keinen Zugriff auf das VBA-Projektmodell aktivieren. Bei einer Firmensperre bleiben die normalen relativen Links ohne Makro nutzbar.

## Verwenden
- ZIPs zuerst entpacken. Die Excel-Datei und die Bilder-Unterordner zusammen aufbewahren.
- Bilderlistenblatt auswaehlen, Suchknopf anklicken, gemeinsamen entpackten Baustellenordner waehlen.
- Das Modul indexiert Bilddateinamen einmal. Unterordner werden durchsucht, Ordnerverknuepfungen/Junctions ausgelassen. Bei einem Lesefehler wird vor der Linkbearbeitung abgebrochen.
- Nur genau ein Treffer mit exakt passendem Dateinamen (Windows: ohne Gross-/Kleinschreibung) wird verknuepft. Fehlende/mehrdeutige Treffer und Formelzellen bleiben unveraendert.
- Neues Blatt Bildsuche_N protokolliert bisherige und neue Links. Relative Links werden auf demselben Laufwerk verwendet. Andere Laufwerke ergeben absolute Links und sind deshalb nicht zusammen mit dem Ordner portabel.
- Bilder werden weder kopiert noch geloescht oder ueberschrieben. Das Werkzeug speichert die Arbeitsmappe nicht automatisch. Ergebnis pruefen und als Kopie speichern; bei einem Abbruch ohne Speichern schliessen, wenn Teilaenderungen verworfen werden sollen.

## Grenzen und Stand
- Gepruefter Nutzeranhang: 27 Zeilen,27 eindeutige relative Links, Bilddatei inM,14Spalten. Der aktuelle Android-Generator hat Bilddatei inN,15Spalten. Das Modul sucht die Ueberschrift Bilddatei in Zeile1.
- Das Modul erkennt KEIN Original allein anhand von Dateigroesse oder Dateinamen. Original und Kleinversion koennen im derzeitigen Android-Export verschiedene SHA256-basierte Namen tragen. Dafuer ist eine gemeinsame unveraenderliche Bild-ID samt Manifest notwendig; siehe EXPORT-008 in projektsteuerung/05_ANDROID_SERVER_VERTRAG.md.
- Ohne Originaldateien oder Exportmanifest ist die Zuordnung unterschiedlicher Namen nicht beweisbar. Es werden keine vermeintlichen Bild-IDs aus Hashsuffixen erfunden.
- VBA-Quelle vorbereitet und statisch gelesen; kein nativer Excel-/VBA-Lauf in dieser Linux-Umgebung. Keine eingebettete XLSM erzeugt. Excel-Praxistest steht aus.
- Android app/** und NAS-Runtime bleiben unveraendert. Die automatische Beigabe in kuenftigen Handy-ZIPs gehoert zum Android-Folgeauftrag.
