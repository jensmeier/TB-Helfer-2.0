TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.6
BUILD: 200056
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Fotoerfassung mit getrennter Kurzbeschreibung und Langtext / BTB-Text
- Bilderliste pro BTB/Schicht als XLSX mit anklickbaren Bildpfaden
- Bilderliste zeigt Kurzbeschreibung und Langtext getrennt

VERBESSERT:
- Kurzbeschreibung ist auf 80 Zeichen begrenzt und bleibt die knappe Bildbezeichnung
- BTB und PDF verwenden den Langtext, wenn vorhanden, sonst die Kurzbeschreibung
- Exportierte Bilddateinamen sind deutlich kürzer und für Windows-Pfade ausgelegt
- JPEG-Suchmetadaten und die Bilderliste halten Baustelle, KM, Firma und Beschreibung weiterhin auffindbar
- Freie Info1-Info3-Eingaben wurden aus der normalen Fotomaske entfernt; bestehende Altwerte bleiben erhalten

BEHOBEN:
- Risiko des Windows-Fehlers 0x80010135 "Pfad zu lang" beim Entpacken von Fotoexporten deutlich reduziert
- Lange Beschreibungstexte werden nicht mehr für exportierte Bilddateinamen verwendet
