TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.12
BUILD: 200062
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

BEHOBEN:
- Abgeschlossene BTBs werden beim Speichern eines Mangels nicht mehr fälschlich ungültig, wenn die Unterbaustelle der zugeordneten Fotos tatsächlich unverändert geblieben ist
- Ein reines Speichern im Vollbild-Mangeleditor führt nicht mehr dazu, dass das vorhandene Abschluss-PDF eines älteren BTBs gelöscht wird
- Foto-Knoten-Updates invalidieren einen BTB nur noch bei einer fachlich echten Änderung des wirksamen Baustellenknotens

VERBESSERT:
- Der Mangel-Editor überspringt unnötige Foto-Knoten-Updates bereits vor dem Repository-Aufruf
- Die Repository-Logik prüft zusätzlich selbst den bisherigen wirksamen Knoten und schützt damit auch andere Aufrufer vor derselben No-op-Invalidierung
- Echte Unterbaustellenänderungen invalidieren den betroffenen Abschluss weiterhin wie fachlich vorgesehen
