# EXPORT-1108B – PDF-Unterbaustellen und XLSX

Auftrag 08.11.2026: „alles bauen“. **Word unverändert lassen.** PDF-Unterbaustellen müssen Bahnhof/Name und Gleis/Weiche eindeutig zeigen.

## Server
Basis 1b660341c67fdf19e92260f471357617f0c45b43; Auftragscommit 4b56cde8674c67ac4755529907140aa2523405a5. Der aktuelle PDF-Renderer enthält die vollständige Ortsanzeige bereits. Keine Server-Runtimeänderung nötig. Neuer Regressionstest erzeugt einen echten PDF-Bericht mit gleichem Kurzname MBG und zwei verschiedenen Weichen W 99W1/W 99W4 und prüft beide Texte. Lokal bestanden und Seite visuell geprüft. Bestehende Referenzprüfung für drei physische Fotospalten, Fotoanzahl, Null-KM und GPS sowie drei Integrationstests ebenfalls bestanden. Keine alten Abschlussdateien verändert.

## Android
In Arbeit: vollständige PDF-Ortsanzeige; kompakte Fotoliste mit separaten Details, Excel-Datum/Zeit, relative URL-kodierte Bildverweise, GPS, lesbare Zeilenhöhen und Druckkopfzeile. Mängelliste und Abnahmeliste als XLSX mit einer Zeile je Vorgang und separatem Fotoblatt; fehlende Exportdateien ausdrücklich markiert. Bilderliste_gesamt.csv entfällt; andere CSV und PDF bleiben. Word-Erzeugung unverändert. APK-Ziel 1.3.44/200094 mit passender Release-MD. Build-/Testabschluss folgt.

## Geschützt / Grenzen
IDs, Originalfotos, bestehende Abschluss-PDFs, Rollen, Sync, Offline-Einzel-BTB und strenge Archivprüfung unverändert. Keine Migration, kein NAS-SSH, kein Deployment. Praktischer Pixel-/Poco-/Windows-Excel-/NAS-E2E offen. „Ordner verschiebbar“ bedeutet: vollständiges entpacktes Paket mit gleicher innerer Ordnerstruktur. Excel-Änderungen synchronisieren nicht zurück.
