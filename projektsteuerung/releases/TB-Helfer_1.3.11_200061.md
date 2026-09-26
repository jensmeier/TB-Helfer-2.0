TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.11
BUILD: 200061
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- In „Vorhandenes Foto auswählen“ gibt es zusätzlich den Button „Mediathek“
- „Mediathek“ öffnet die Bilder auf dem Handy über den Android-Datei-/Medienpicker
- Ein gewähltes Handy-Bild wird in den TB-Helfer-Fotoordner übernommen und dem Mangel zugeordnet
- EXIF-GPS des importierten Bildes wird übernommen, wenn vorhanden

VERBESSERT:
- Mängel-PDF bettet Fotos nicht mehr unnötig in Mehr-Megapixel-Auflösung ein
- PDF-Fotos werden für die 164-pt-Anzeige auf maximal 480 px passend skaliert
- Die bestätigte Bildgröße, die 6er-Paginierung und anklickbare GPS-Links bleiben erhalten
- GVP/Mast wird im Mangelkopf/PDF-Kopf nicht mehr wiederholt; GVP/Mast bleibt ausschließlich bei den einzelnen Fotos
- „Abbrechen“ und „Speichern“ im Vollbild-Mangeleditor haben jetzt dieselbe abgerundete TB-Helfer-Form
- „Ort / KM / Bereich“ bleibt echter Freitext; „Bahnsteig 2“ oder „Weiche 12“ werden nicht versehentlich als KM 2/12 interpretiert

BEHOBEN:
- Das getestete Mängel-PDF mit 9 Fotos war rund 30 MiB groß, weil die eingebetteten Bilder noch etwa 1536 x 2040 Pixel hatten
- GVP/Mast wurde im Mangelkopf zusätzlich zu den fotoeigenen GVP-Angaben angezeigt
- Unterschiedliche Buttonformen bei Abbrechen/Speichern im Vollbild-Mangeleditor
