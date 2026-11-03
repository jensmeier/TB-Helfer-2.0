# TRACK-KM-1103 – Handy → Server, Iststand und Vorschlag

Stand03.11.2026. Quellvergleich Server7a82525/Android981fb134 (Streckendaten-Code gegenüber Basis b6fb187 unverändert). Nutzer möchte ausdrücklich auch nachträglich eingegebene KM und Änderungen übertragen. Dieser Bericht ist der Vorschlag; noch kein neuer Streckendaten-Upload implementiert.

## Iststand
| Bereich | Vorhanden | Lücke |
|---|---|---|
| Baustellen-Achsen/Samplepunkte | WorksiteTrackDataSync.buildLocalDatasets überträgt AXIS mit samples.station/km_meter/km_quelle. WorksiteTrackDataApi nutzt GET/POST/PATCH/DELETE auf /api/v1/.../track-data; Version+Idempotenz vorhanden. | Nur LEAD sendet, andere Kontexte empfangen. Im schnellen BTB-Worker/Deltaempfänger kein Streckendatenlauf. |
| Ältere2-Punkt-KM-Kalibrierung | TrassenRepository.speichereManuellenKmAnker speichert lokale Anker; bei zwei plausiblen Punkten werden Samplewerte mit MANUELL_2PUNKT neu berechnet. | Berechnete Samples können bei späterem größeren LEAD-Sync im AXIS-Payload mitgehen, aber Rohanker, GPSqualität und Änderung/Löschung werden nicht als gemeinsame versionierte Kalibrierung übertragen. Kein direkter Trigger im Repository. |
| Heutige manuelle betriebliche Abschnitte | ImportExportFragment speichert TrassenManuellerAbschnittEntity und TrassenAbschnittKmPunktEntity lokal. Richtung, Start/Ende, Anker und GPS-/Qualitätsfelder vorhanden. | WorksiteTrackDataSync serialisiert diese Tabellen nicht. Ein am Handy sichtbarer manuell kilometrierter Abschnitt muss deshalb NICHT im Serverbestand ankommen. ImportExportFragment enthält keinen Server-/Syncaufruf für diese Speicherwege. |
| Gemeinsamer Server-Achsenbestand | survey-catalog/manual-segments mit create/update/deactivate, Quellenreferenz, Version, Idempotenz und eigenem /survey-catalog/changes; außerdem Netzobjekte. | Android-Worksite-Datasets sind ein anderer Bestand, kein automatischer Schreibweg in denselben gemeinsamen Achsen-/Netzdatensatz. Mobile stabile axis_id/segment_id-Zuordnung fehlt im betrachteten lokalen Modell. |
| Admin-KM-Editor | /management/axes/{axis_id}/manual-km mit Quelle/Version/Grenzen/Überlappungsprüfung/GVP-Projektion vorhanden. | Admin-Endpunkt. Nicht einfach Adminrechte für Handys öffnen. Gemeinsame Prüf-/Berechnungslogik unter dem passenden vorhandenen Nutzer-/Gerätevertrag wiederverwenden. |

### Zusätzlicher Schutzbedarf vor mehr Upload
WorksiteTrackDataApi.reconcileCanonical betrachtet den lokalen LEAD-Bestand als gewünschten Gesamtbestand:
- neue lokale Schlüssel→POST, abweichende Payloads→PATCH gegen gerade gelesene Serverversion;
- jeder im Serverbestand vorhandene, lokal aber fehlende Dataset-Schlüssel→DELETE;
- bei409 wird der Serverstand erneut gelesen und die lokale Sollfassung erneut abgeglichen.
Damit schützen Versionsnummern allein nicht vor inhaltlichem Überschreiben durch einen veralteten zweiten LEAD oder vor Löschen serverseitig importierter/nichtlokaler Datensätze. Es fehlt eine dauerhafte gemeinsame Änderungsbasis/Eigentumsabgrenzung. Dataset-Schlüssel enthalten u.a. Name/Quelle/Gleis; Umbenennen kann Identität wechseln. Kein echter Löschfall auf NAS ausgeführt. Originaldateien selbst werden hier nicht hochgeladen oder gelöscht; abgeleitete Dataset-Datensätze können betroffen sein.

## Empfohlener Weg
1. **Eine bestehende Achse eindeutig binden.** Persistente Server-Achs-/Segment-UUID plus Quellen-UUID und exakter Originalhash/Quellversion. Lokale Room-ID oder Dateiname/Gleisname allein ist keine gemeinsame Identität. Mehrdeutige Quellen/gleiche Namen ausdrücklich auflösen, nicht anhand räumlicher Nähe blind zusammenführen.
2. **Manuelle KM als eigene Änderung speichern.** Stabile mobile Änderungs-/Kalibrierungs-UUID; Start/Ende als interne geometrische Achsstation, bekannte Bahn-KM in Metern, Richtung, beliebig innerhalb des Abschnitts liegende Anker, Quelle GPS/GVP/Mast/Grafik, Messgenauigkeit/Achsabstand und Bearbeitungsstatus. GVP/Mast rechtwinklig auf die ausdrücklich gewählte Achse projizieren. Interne Station und Bahn-KM getrennt halten.
3. **Sofort dauerhaft vormerken.** Vor Netzwerkaufruf eine bereichsbezogene Offlineoutbox. Anzeige „Lokal gespeichert“, „Übertragung ausstehend“, „Server bestätigt“, „Konflikt prüfen“. Ein unvollständiger erster Anker wird als Entwurf gesichert, ohne bereits belastbare KM zu behaupten.
4. **Gezielt/versioniert senden.** Nur diese Kalibrierung/Abschnittsänderung gegen die beim Bearbeiten bekannte Achs-/Quell-/Segmentversion. Bestehende manual-segments und gemeinsame KM-Validierung erweitern/wiederverwenden; explizite Zielachse statt automatischer zweiter Achsneuanlage. Idempotenter Retry derselben Operation. Keine Gesamtbestand-PUT-/automatischen „fehlend→löschen“-Aufrufe.
5. **Server prüft und bestätigt denselben fachlichen Datensatz.** Quellenbindung, endliche/echte Nullwerte, Grenzen, Richtung, Überlappung, GPS/GVP-Abstände und Versionen. Bei Widerspruch/409 lokale Fassung behalten, alte/neue Werte anzeigen; nicht automatisch mit neu gelesener Basis darüber speichern. Kein unbelegtes Extrapolieren über den festgelegten/prüfbaren Bereich.
6. **Änderungen an alle Berechtigten verteilen.** Globalen gemeinsamen Achsen-/Netzbestand und zugehörigen Feed verwenden, getrennt von Firmen-BTB/Fotos. /survey-catalog/changes und gegebenenfalls Netzfeed mit eigenem dauerhaftem Cursor/Bootstrap berücksichtigen; nicht nur TRACK_DATA zum BTB-Filter hinzufügen. Originale bleiben unverändert.
7. **Bestehende lokale Eingaben übernehmen.** Bestehende manuelle Abschnitte/KM-Anker einmal inventarisieren, exakt auf Quellen/Serverobjekte abbilden und dann versioniert übertragen. Unklare Fälle zur Auswahl stellen. Keine Daten löschen, keine lokale neue Geometrie aus zwei KM-Werten erfinden. Änderung und Deaktivierung später über dieselbe stabile UUID; ungesendete Änderungen anderer Geräte schützen.

### Erwartetes Nutzerverhalten
Du tippst eine Achse ohne KM an, setzt die vorhandene manuelle KM-Zuordnung am Handy und speicherst. Offline bleibt die Änderung vollständig vorgemerkt; online sendet die App genau diese Zuordnung. Nach Serverbestätigung zeigt das Portal denselben Abschnitt mit denselben Werten. Manuell bleibt Orange, importierte belastbare KM Grün; Entwurf bleibt kenntlich. Ein anderes berechtigtes Handy übernimmt dieselbe Serverrevision. Änderst du später einen Anker, folgt eine neue Revision derselben Kalibrierung. Eine parallele Änderung wird als Konflikt sichtbar.

## Abnahme vor Freigabe
- Originalimport/Dateiduplikat und zwei gleichnamige Achsen an unterschiedlichen Bahnhöfen.
- KM fehlen→ein Anker/Entwurf→vollständig plausible Kalibrierung→Portal→zweites Gerät; echte KM0, fallende KM, überlagerte Gleise, GVP/Mast rechtwinklig.
- Änderung/Löschung eines Ankers/Abschnitts, Quellenneuversion/Reimport, Neuaktivierung, Offline/Neustart/Netzrückkehr.
- Zwei Geräte bearbeiten dieselbe Revision; kein Last-Writer-Wins und kein ungefragter Abgleich anderer Achsen.
- Haupt-TB/Helfer/Nutzerrechte, gemeinsame Globaldaten versus private Firmen-/Baustellendaten.
- Teilübertragung/Timeout/409, Cursor erst nach Anwendung, Feed-Konkurrenz mit echten Transaktionen.
- Aktuelle3D-/GPS-/KM-Ergebnisse und bestätigte PDF-/Foto-/BTB-Funktionen unverändert prüfen.

Empfehlung: zuerst den destruktiven Baustellen-Gesamtvergleich für diesen Anwendungsfall ausschließen, dann Quellenbindung + dauerhafte manuelle KM-Outbox + explizite versionierte Serveränderung bauen. Keine dritte parallele Streckendatenablage.
