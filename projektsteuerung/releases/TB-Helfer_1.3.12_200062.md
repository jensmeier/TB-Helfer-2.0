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


NEU:
- Gesamt-/Zeitraum-ZIP enthält wieder `Bilderliste_gesamt.xlsx`
- Die Spalte `Bilddatei` ist in Excel anklickbar und öffnet das jeweilige Bild über einen relativen Pfad im entpackten ZIP-Ordner
- Für jeden enthaltenen BTB wird zusätzlich eine Schichtliste nach dem Muster `21.09.26_Bilderliste.xlsx` im jeweiligen BTB-Datumsordner erzeugt
- Der einzelne Schicht-/BTB-Export enthält ebenfalls die datumsbezogene anklickbare XLSX-Bilderliste
- CSV-Bilderlisten bleiben parallel als Roh-/Sicherungsliste erhalten

HINWEIS:
- Die Bildlinks sind bewusst relativ. ZIP vollständig entpacken und die Ordnerstruktur beibehalten; dann kann Windows/Excel aus der XLSX direkt zum Foto springen.


VERBESSERT:
- ZIP-Inhalt beginnt jetzt fest mit `Baustelle/<Baustellenname>/...`
- Dadurch kann unter Windows bei „Alle extrahieren“ immer derselbe vorhandene Ordner `TB-Helfer` als Ziel gewählt werden
- Ergebnis ist dauerhaft `TB-Helfer/Baustelle/<Baustellenname>/...`; keine wechselnden zusätzlichen Export-Unterordner im Ziel
- Relative XLSX-Bildlinks bleiben innerhalb des Baustellenordners gültig
