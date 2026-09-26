TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.8
BUILD: 200058
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- GPS-Koordinaten unter den Fotos der Mängelliste sind jetzt auch im exportierten PDF echte anklickbare Links
- Klick auf GPS im PDF öffnet Google Maps über einen HTTPS-Link
- Mängelliste kann direkt in der App bearbeitet werden
- Baustelle / Unterbaustelle, KM, Firma, Beschreibung und Kommentar sind im Mangel-Editor änderbar
- GPS unter den Mangel-Fotos ist auch in der Android-Mängelliste antippbar und öffnet Google Maps bzw. den Browser
- GVP/Mast wird in der Mängelliste und im PDF angezeigt, wenn vorhanden
- Fotoerfassung mit getrennter Kurzbeschreibung und Langtext / BTB-Text
- Bilderliste pro BTB/Schicht als XLSX mit anklickbaren Bildpfaden

VERBESSERT:
- Die GPS-Zeile im PDF wird blau/unterstrichen dargestellt, damit die Klickfunktion erkennbar ist
- Der vorhandene Android-PDF-Renderer bleibt erhalten; nur die GPS-Linkbereiche werden nach dem Rendern ergänzt
- Im Mängel-PDF heißt das bisherige Feld Bereich jetzt Baustelle und zeigt Haupt- bzw. Unterbaustelle
- Die allgemeine GPS-Zeile im Mangelkopf wurde entfernt
- Unter jedem Mangel-Foto stehen Foto-KM und GPS; GVP/Mast wird bei vorhandenen Daten ergänzt
- Mangel-Fotovorschauen in der App sind größer und zeigen KM/GPS direkt
- Nachträglich bearbeitete Mangel-Metadaten werden über den bestehenden Server-1.0-Payload synchronisiert
- Kurzbeschreibung ist auf 80 Zeichen begrenzt und bleibt die knappe Bildbezeichnung
- BTB und PDF verwenden den Langtext, wenn vorhanden, sonst die Kurzbeschreibung
- Exportierte Bilddateinamen sind deutlich kürzer und für Windows-Pfade ausgelegt
- Bilderliste zeigt Kurzbeschreibung und Langtext getrennt
- Freie Info1-Info3-Eingaben wurden aus der normalen Fotomaske entfernt; bestehende Altwerte bleiben erhalten

BEHOBEN:
- PDF-GPS war bisher nur Text und konnte im PDF-Reader nicht zuverlässig geöffnet werden
- Falsche bzw. veraltete KM-/Baustellenanzeige kann im Mangel korrigiert werden
- Kommentar ist nicht mehr nur beim Erledigt-Markieren erfassbar, sondern auch über Bearbeiten
- Risiko des Windows-Fehlers 0x80010135 "Pfad zu lang" beim Entpacken von Fotoexporten deutlich reduziert
- Lange Beschreibungstexte werden nicht mehr für exportierte Bilddateinamen verwendet
