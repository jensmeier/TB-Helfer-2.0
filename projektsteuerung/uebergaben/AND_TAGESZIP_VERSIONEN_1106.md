# Tages-ZIP: Versionsvergleich 1.3.35 bis 1.3.41 (06.11.2026)

## Nutzerbefund
1.3.41/200091 installiert. BTB-Verlauf → ZIP teilen: „Tagesabschluss-ZIP wird erstellt“, anschließend keine Auswahl und App schließt. Installation durch Nutzer bestätigt; E2E fehlgeschlagen. Kein aktueller Crashtrace. 1.3.35 ist Vergleichsbasis, nicht als auf demselben Bestand funktionierend bestätigt.

## GitHub-Basis
Android acebd8643ab166b8dd15ac5a9d689258957ebc54; Server 6ea0d987bebc67c346fca24714e4e1b9a8c852ea. PR11 offen, PR6 Draft; beide gegenüber main nicht automatisch mergebar. Auftrag vor Auswertung dokumentiert.

| Version | Build | Vergleichscommit | Tages-ZIP |
|---|---|---|---|
|1.3.35|200085|510779453c85a25cefa2c1c0d886844ba1a41c4d|Direkter Zeitraumexport, Tagesfilter für normale Fotobinaries; JPG-Exportkopie normalerweise max.2048px/JPEG85, Originalfallback bei Fehler|
|1.3.36|200086|603ba28f1d9935ccf36406ce9e14b205d91bf28c|Exportkern und Verlauf identisch mit1.3.35; Quellstandvergleich, kein neuer APK-Nachweis|
|1.3.37|200087|9b0571cf5e745179421e987d35a622544e4064c6|Neuer gemeinsamer Exportkern: IO, Vorab-Sync, Roharchiv, Normalisierung/Gesamtmedienarchiv, XLSX-Linkprüfung|
|1.3.38|200088|35241401a698744fd4b6d0d557df844687fc615b|Exportkern, Verlauf, Normalisierung, Gesamtmedienarchiv identisch mit1.3.37|
|1.3.39|200089|fff93819607d313bc664d0c8dc62939e4cec86dc|Dieselben vier Exportdateien identisch|
|1.3.40|200090|48d3e7bf38b61e5b8d076418221a76378785c005|Dieselben vier Exportdateien identisch|
|1.3.41|200091|76f2d5beac8665d2d95b83ba3f7a85df55c2e526|Dieselben vier Exportdateien identisch; gemeinsame Foto-Sync-/Application-/Share-Änderungen separat|

## Belegte Quellbefunde
- BtbVerlaufFragment Blob1b42fd9b895b543033ea3610196429ab2c611806 in allen sieben Ständen identisch. Zeilen235–250: Starttoast, erstellenBaustellenExport mit gleichem Von-/Bisdatum, danach shareZip, catch(Throwable) mit Fehlertoast. Kein „abgeschlossen“-/Read-only-Gate für Teilen. Keine Fortschrittsphasen oder Mehrfachklicksperre.
- BtbExportPaket Blob50d1bbb1434203f9a1e41758c3c2d3ba48345b3d in1.3.35/36; Blob51e6fb5223f81e056dc0f31d3a3b1f1ca8acbd14 in1.3.37–41.
- Ab1.3.37 Zeilen1450–1480: zugehörigen Hauptknoten ermitteln, bei vorhandenem Knoten GesamtMedienArchiv.vorbereiten (Server1AndroidSyncCoordinator.synchronize), Roharchiv, MergeSafeBaustellenExport.normalisieren, ExportArchivPruefung, Bildzählung. Ohne Hauptknoten entfällt die Normalisierung, aber Roharchiv nimmt jetzt alle lokal vorhandenen Baustellenfotos auf.
- Zuvor normale Bildbinaries nach Foto.erstelltAmEpochMs auf Zeitraum gefiltert; Gesamtbilderliste enthielt bereits weiteren Bestand. Umbau sollte reale defekte XLSX-Verweise und Doppelmedien im Export beheben. Pauschales Zurücksetzen würde diese Probleme wieder einführen.
- Seit1.3.37 includeImageBinaries=false im Roharchiv bei vorhandenem Hauptknoten; GesamtMedienArchiv schreibt danach vorhandene lokale Fotodateien direkt (localFile.readBytes), ohne addFotoFileKomprimiert. Das kann Originalgröße statt verkleinerter Exportkopie bedeuten, sofern der lokale Pfad das Kameraoriginal enthält. Originale auf dem Handy bleiben unverändert.
- GesamtMedienArchiv: lokale Dateien zum Hashen vollständig lesen (Zeile164), teilweise erneut in rowsFromLocal (724), zum ZIP-Schreiben erneut (341). Normalisierer liest einzelne ZIP-Einträge vollständig (80). Dies sind einzelne vollständige Dateiallokationen, NICHT Nachweis, dass alle Fotos gleichzeitig im RAM liegen.
- GesamtMedienArchiv erzeugt shiftLists aus dem gesamten Baustellenverlauf (Zeilen267ff.), ohne Von-/Bisdatum; alle erzeugten XLSX-Bytearrays bleiben bis zum Schreiben in einer Liste. Zusätzlicher Aufwand über gewählten Tag hinaus.
- Vorab-Sync und Medienkatalog sind neu im Verlaufpfad; Katalog-/Downloadfehler blockieren vollständigen Export. Die Anzeige zeigt weder Phase noch echten Fortschritt.
- shareZip selbst bleibt unverändert und ruft FileProvider direkt; der neue ShareFiles-Pfad von1.3.41 ist für Einzel-/Mehrfachfotos, nicht dieser ZIP-Aufruf. Ein normaler synchroner FileProvider-Fehler liegt hier im try/catch und sollte einen Fehlertoast liefern. Das schließt einen Fehler in Freigabe/Lifecycle nicht aus.
- Eindeutige ZIP-Pfade bzw. written-Guards vorhanden. Zwei ähnlich aussehende Fotos beweisen weder ZipException noch Absturzursache.

## Bewertung und nächster Nachweis
Der wesentliche Änderungspunkt im Tages-ZIP ist1.3.37, nicht1.3.41. Mehr Daten, größere lokale Bildbytes, wiederholte Ganzdateiallokationen und zusätzlicher Sync sind belegte Unterschiede; Speichermangel/ANR/native Crash/anderer asynchroner Fehler sind ohne neues Log nicht bewiesen. 1.3.41 verändert gemeinsamen Sync-/Application-Code, daher ist ein indirekter Einfluss trotz unverändertem Export nicht ausgeschlossen.

Nächster Schritt: am Pixel mit200091 genau einmal Tages-ZIP auslösen und unmittelbar Android-Studio-Logcat samt Zeitpunkt sichern. Bei Prozessabbruch möglichst nicht auf alten PID filtern. FATAL EXCEPTION, OutOfMemoryError, ANR oder Prozessende prüfen. Vorhandenes älteres Log zeigte Share-/Receiver-Crashes, keinen eindeutigen Verlauf-ZIP-Trace und ersetzt den neuen Nachweis nicht.

Gezielte Korrekturrichtung nach Eingrenzung: Hash-/Binärdateien streamen; erforderliche Exportbildgröße konsistent halten; unnötige Tagesexport-Gesamtarbeiten prüfen; sichtbare Phase/Fehlerdiagnose und parallele Exporte sperren. Keine bestehenden Gesamtlisten-/Medienverträge still ändern. Kein pauschaler Rückbau.

## Prüfstatus
Live-Datei-/Blobvergleich aller sieben Stände durchgeführt; identische Dateien über SHA geprüft. Version1.3.36 in Gradle200086/1.3.36 nachgelesen. Kein adb verfügbar: kein Pixel-/Emulator-/Speicherlast-/Laufzeitnachweis, kein neuer Unit-/Gradle-/CI-Lauf. Keine Runtime-, Server-, Daten- oder Versionsänderung; keine APK gebaut. Status QUELLVERGLEICH ABGESCHLOSSEN / KONKRETE ABSTURZURSACHE OFFEN.
