# PORTAL-022 – NAS-Übernahme

Status: QUELLSTAND FERTIG / SOURCE-TEST BESTANDEN. DEPLOY und NAS-E2E OFFEN. Kein Deployment erfolgt.

Voraussetzung: bestehender zuletzt bestätigter PORTAL-021-Stand. Keine Migration oder neue Bibliothek.

Erst nach Deploymentauftrag: über GitHub Desktop den Branch feature/tb-server-1.0 holen. Vorhandenes Backup prüfen. Folgende Dateien über File Station in die gleichnamigen Unterordner des bestehenden Serverordners /volume1/docker/tb-helfer-v1 übernehmen (Repositorypräfix server/ entfällt bei dieser bekannten NAS-Ordnerstruktur):

| GitHub | NAS relativ zum Serverordner |
|---|---|
| server/templates/v1_node_labels.html (neu, zwingend mitnehmen) | templates/v1_node_labels.html |
| server/templates/v1_worksite.html | templates/v1_worksite.html |
| server/templates/v1_btb.html | templates/v1_btb.html |
| server/templates/v1_portal_pictures.html | templates/v1_portal_pictures.html |

Bei vorhandener Quellordnereinbindung API über Container Manager neu starten, danach Browser Strg+F5. Falls Templates im Image liegen, das API-Image aus dem aktualisierten Quellstand neu erstellen und API darüber neu erstellen/starten. Kein Worker-/DB-Umbau. Nichts löschen; .env, Compose, DB-/Storage-/Backupverzeichnisse unverändert lassen. Kein SSH erforderlich.

Praxistest im vorhandenen Testbestand:
1. Portal Unterbaustellen, Elternauswahl, Mangel-/Abnahmeauswahl und deren Anzeige prüfen: vorhandener Kurzname plus Bahnhof/Name bzw. Kürzel und Gleis/Weiche. G4 in zwei verschiedenen Bahnhöfen muss unterscheidbar sein.
2. Dieselben Knoten im BTB-Editor, Eintrag und Fotoangaben sowie Galerie prüfen. Vorhandene Zuordnungen/Schlüssel müssen erhalten sein.
3. Knoten ohne KM: kein künstliches KM 0–0; echte gespeicherte Grenzen 0/0: KM 0–0; einseitige Grenze 0: KM 0. Keine Testwerte in echte Betriebsdaten schreiben.
4. Foto ohne Aufnahme-KM: BTB ohne KM, Galerie Nicht vorhanden; echtes Foto-KM 0: 0,000.
5. Freie Strecke ohne Bahnhof, bestätigte Baumreihenfolge und bestehende Katalogauswahl weiter prüfen. Bei fehlenden gespeicherten Fachangaben kann die Anzeige diese nicht ergänzen; keine automatische Datenreparatur.

Bereits gespeicherte PDFs werden nicht geändert oder neu erzeugt. Diese Korrektur betrifft Browseranzeigen. Bei Darstellungsproblemen die drei bisherigen Templates wiederherstellen und API neu starten; Datenbank bleibt unangetastet.

Nachweis lokal: 157 Node, 151 Python/Node, 153/154/155/156 JS PASS; JS-Syntax nach lokaler Include-Auflösung PASS. Isolierte Produktionsfunktionen mit DOM/API-Doubles, kein Browser-/HTTP-/DB-/NAS-E2E. Keine CI gestartet (Kostenregel). Genauer Befund in 04_SERVER_STATUS.md.
