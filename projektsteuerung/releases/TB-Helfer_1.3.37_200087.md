TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.37
BUILD: 200087
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: Noch nicht veröffentlicht; APK und Begleitdatei werden gemeinsam als Buildartefakt bereitgestellt.

## Änderungen und Fehlerbehebungen
- Verlauf und Baustellen-ZIP nutzen denselben vollständigen Exportweg außerhalb des UI-Threads.
- Gesamtbilder und Listen verwenden konsistente Dateinamen; jeder Excel-Bildlink wird vor dem Teilen gegen die fertige ZIP geprüft.
- Persistierte Downloadpfade ergänzen die Original-/Sync-Zuordnung; keine Löschung lokaler Fotos.
- Schichtlisten erhalten BTB-Identität, damit mehrere gleichzeitige Berichte einander nicht überschreiben.
- Vorab-Sync erfolgt vor der Dokumenterzeugung. Gespeichertes gültiges Abschluss-PDF bleibt erhalten; der aktuelle lokale Word-Stand wird im Exporthinweis unterschieden.
- Keine erfundenen Fortschrittsprozente beim ZIP-Export.

## Grenzen und Buildnachweis
Server-Gruppierung, Kurz-/Langtext und Wetterdarstellung benötigen die dokumentierte SERVER-Korrektur. Alte Abschluss-PDFs werden nicht stillschweigend ersetzt. Nicht eindeutig zuordenbare Altkopien werden erhalten.
Gesamtmedien umfassen den verfügbaren Baustellenbestand; die Datumswahl grenzt die BTB-Dokumente ein. Angemeldeter Offline-Gesamtexport mit fehlendem Katalog ist noch offen.
Quellbegleitdatei, zunächst kein Build-/Installationsnachweis. Der erfolgreiche Build ergänzt die ausgelieferte Kopie mit Commit, APK-SHA256, Manifest und Signaturprüfung. Geräte-/NAS-E2E bleibt offen.
