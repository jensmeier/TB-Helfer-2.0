# EXPORT-QUERPRUEFUNG-1104 – Android gegen Server und Nutzerexport

Status: QUELLPRÜFUNG ABGESCHLOSSEN / FEHLER BELEGT / KORREKTUR OFFEN / GERÄTE- UND NAS-E2E OFFEN.
Auftrag am 04.11.2026 vor Prüfung in 70783252a8b1aee0bc294194a704da445fc55655 gesichert. Nutzer: „androidcode prüfen,aber diesmal richtig und prüfe alles quer auch gegen den server“. Dieser Block verändert keine Runtime, keine Version und keine produktiven Daten.

## Verbindlicher Prüfstand
- Android feature/tb-android-2.0: cc1fa7e5d571424d959dfd58277786b08bb671b0, Quellversion 1.3.36 / 200086.
- Server feature/tb-server-1.0: 6ea0d987bebc67c346fca24714e4e1b9a8c852ea.
- PR11 Android offen, nicht Draft; PR6 Server offen, Draft. Kein Merge.
- Einstieg 00_START_HIER, Projektregeln, Statusdateien 01–08 und Android-/Serververtrag gelesen. GitHub ist Quellnachweis, kein Nachweis des installierten Handy-/NAS-Stands.
- Nachverfolgt: Verlauf-/Baustellenexport, Roh-ZIP, Normalisierung/Gesamtarchiv, Foto-Repository/Zuordnung/Import, BTB-Druckmodell, CSV/XLSX, Word, Abschluss-PDF-Sync, Serverabschluss/gespeichertes PDF, Renderer/Metadaten/Fotoeinträge.
- Konkreter BTB: 838e1e0c-0760-4888-8bd9-48823f1714d6; Datum 03.11.2026. Nutzer-ZIP endet auf 1793775285705. Original unverändert geprüft.

## Belegte Befunde

### A1 – Kritisch: Korrektur umgeht den tatsächlich vorhandenen Verlauf-Export
Android BtbVerlaufFragment.kt:235–247 ruft erstellenBaustellenExport und danach shareZip direkt auf. BaustellenFragment.kt:271–294 ruft zusätzlich MergeSafeBaustellenExport.normalisieren auf; erst dieses ruft GesamtMedienArchiv.ergaenzen auf (MergeSafeBaustellenExport.kt:107).
Die Original-/Sync-Identitätsbereinigung in GesamtMedienArchiv.kt:145ff erreicht den ersten Bedienweg nicht. Deshalb genügte die frühere Änderung dort nicht, um beide Exporte zu reparieren.
BtbExportPaket.kt:1446–1474 filtert lokale Fotodatensätze nach Baustelle/Datum, nicht nach Aufnahmeidentität. Im Originalarchiv stehen 18 Dateien für visuell 9 Motive: überwiegend Original plus mobile_preview; 08:49 dreifach einschließlich btb_175.jpg. Unterschiedliche Pixelgrößen bedeuten unterschiedliche Bytes, also reicht Datei-SHA allein nicht.
Die Archivstruktur Baustelle/... passt zum Roh-Export; welcher Knopf am Handy tatsächlich gedrückt wurde, ist aus dem Archiv allein nicht beweisbar.

### A2 – Kritisch: Gesamt-XLSX verlinkt falsche/nicht mitgelieferte Dateien
Tatsächlich alle externen Bildlinks relativ zum XLSX-Ort gegen ZIP-Einträge aufgelöst:
- Tagesliste: 9 Links, 9 vorhanden.
- Gesamtbilderliste: 83 Links, 0 vorhanden.
- Davon 18 vom ausgewählten 03.11.: Liste referenziert B066–B083, ZIP enthält B001–B018.
- Weitere 65 liegen außerhalb des Tageszeitraums und sind in dieser ZIP nicht enthalten.
Ursache BtbExportPaket.kt:1499–1502: exportFotos und gesamtExportFotos werden getrennt mit mapIndexed nummeriert. Global-XLSX verwendet anschließend Gesamtindizes (1589–1614), ZIP die Zeitraumindizes. Das ist ein konkreter Referenzfehler, nicht nur eine abweichende Darstellung.
Zusätzlich schreibt einfacheBilderlisteCsv (1507–1528) den internen Quelldateinamen statt des exportierten B-Namens. B-Namen selbst hängen von Listenindex und änderbaren Metadaten ab (1203–1221); dadurch sind sie bei veränderter Auswahl kein stabiler Dateischlüssel.

### A3 – Hoch: Word und PDF verwenden verschiedene Daten-/Erzeugungsstände
BtbExportPaket.kt:1533–1543 kopiert ein bereits vorhandenes Abschluss-PDF; 1555–1572 erzeugt Word frisch aus lokalen Daten. Es gibt in diesem Export keinen gemeinsamen unveränderlichen Export-Snapshot.
BtbTeamMediaSync.kt:406–478 importiert das kanonische Server-PDF unter FINALIZED-/Berechtigungsgates, verwendet Version/SHA als Importmarker und prüft bei vorhandenem erwarteten SHA die heruntergeladenen Bytes. Diese Prüfungen sind vorhanden und dürfen nicht entfernt werden.
Server btb.py:1016–1037 liefert das gespeicherte Abschluss-PDF, ohne es bei GET neu zu rendern. Ein Serverupdate ändert bestehende abgeschlossene Dokumente daher nicht automatisch.
Artefakt: PDF erstellt 03.11.2026 17:51:43 +01:00, ZIP am Folgetag. PDF hat 5 Seiten, 9 Bilder, zwei Bildspalten und 0 Link-Annotationen. Word enthält zusammengefasste Zeiten und detailliertere Ortsbezeichnungen.
Nicht bewiesen: dass NAS aktuell alten Code ausführt. Das PDF kann ein früher gespeicherter Abschluss sein. Unveränderliches Original nicht stillschweigend überschreiben.

### A4 – Hoch: Gruppierungsregel bleibt auch im aktuellen Quellstand verschieden
Android BtbDruckModellBuilder.kt:628–675 gruppiert Fotoereignisse nach Firma/Ort/Text über die gesamte Gruppe und bildet Zeit-/KM-Bereiche.
Server btb_pdf_android_style.py:452–523 trennt zusätzlich bei Zeitabständen über fünf Minuten (483–490).
Direkter Aufruf der unveränderten Serverfunktion mit 08:00, 08:49, 09:56, 16:30 und gleichem Ort/Text ergab vier Zeilen; Androidcode bildet eine Gruppe mit Bereich 08:00–16:30. Das ist weiterhin ein Vertragsunterschied, kein alleiniger Alt-PDF-Effekt.
Server priorisiert notiz vor kurztext. Reproduktion: kurztext=Einschotter, notiz=Langer Zusatz ergibt nur Langer Zusatz in der Arbeitszeile. Android kombiniert über BtbPhotoDescription.forBtb beide Texte.
Foto-/BTB-Bildnummern und chronologische Exportdateinummern sind im Androidcode ausdrücklich getrennt vorgesehen. Unterschiedliche Zahlen allein beweisen deshalb keinen falschen Bildinhalt; die tatsächlich kaputten Gesamt-XLSX-Ziele aus A2 sind davon zu unterscheiden.

### A5 – Mittel: Wettertext wird im aktuellen Serverrenderer absichtlich abgeschnitten
btb_pdf_android_style.py:916–981 verwendet eine halbe Seitenbreite, feste Spalten und kürzt Werte auf Spaltenbreite mit Ellipse. „sonnig / wolkenlos / klar“ wird deshalb unvollständig dargestellt.
Die Screenshots belegen gleiche manuelle Wetterwerte/Zeitpunkte, aber abweichende Darstellung. Temperatur „-“ bedeutet hier kein nachgewiesenes verlorenes Messdatum; die übermittelten Originaldaten wurden nicht aus der Live-Datenbank gelesen.

### A6 – Positiver Gegencheck: aktueller Serverrenderer kann drei Spalten/GPS
Bestehender test_btb_pdf_reference.py wurde mit unveränderter Produktionsfunktion direkt ausgeführt: PASS. Drei ausgewählte Bilder in einer physischen Reihe, Auswahlgrenze, KM 0,000 sowie drei Google-Maps-Linkannotationen wurden geprüft.
Der aktuelle Code enthält außerdem ausführlichere Ortszusammensetzung (_node_label:274–305). Die fehlenden Details im älteren Nutzer-PDF belegen nicht, dass dieser Quellstand sie grundsätzlich auslässt; konkrete Snapshotdaten und Deployment fehlen.
Rendererkennung tb-server-btb-pdf-5 ist weiterhin konstant (btb_pdf.py:19). Sie unterscheidet diese Layoutstände nicht verlässlich.
Dateigröße wird in den aktuellen Fotokarten nicht ausgegeben; das ist separat gegen die gewünschte Kartenspezifikation umzusetzen, nicht als getesteter Erfolg zu melden.

### A7 – Weitere offene Android-Risiken im angrenzenden Export
- GesamtMedienArchiv.kt:128–141 fängt Vorab-Syncfehler ab, wirft bei nachfolgendem CatalogResult.Error aber einen Exportfehler. Ein angemeldeter Offline-Nutzer erhält damit nicht zwingend den im Kommentar angekündigten Offline-Export. Kein Gerätetest durchgeführt.
- Gesamtarchiv lädt absichtlich alle Baustellenfotos; der Zeitraum gilt nicht überall gleich. Gesamtlisten und Zeitraum-Paket brauchen eindeutig definierten Umfang.
- Die Identitätsbrücke berücksichtigt BtbTeamMediaSync-Importzuordnungen; daneben existieren CollabLocalBridge-Zuordnungen. Alle Importwege und Mehrfachverwendungen benötigen einen gemeinsamen Aufnahmeschlüssel. Keine pauschale Bildlöschung aufgrund ähnlicher Motive.
- BaustellenFragment.kt:261–268 zeigt einen zeitgesteuerten 100-bis-0-Countdown unabhängig vom tatsächlichen Arbeitsfortschritt. Er ist kein belastbarer Exportfortschritt.

## Markierte Dateinamen
- 838e1e0c-0760-4888 ist dokumentToken aus gekürzter BTB-ID (BtbDokumentVersionierung.kt:40ff), für PDF und DOCX desselben BTB gleich. Nicht automatisch ein Fehler und nicht Ursache der Fotodubletten. Mehrere BTBs gleicher Schicht/Datum müssen unterscheidbar bleiben.
- 1793775285705 kommt aus System.currentTimeMillis im ZIP-Namen (BtbExportPaket.kt:1493). Jeder Export erhält dadurch einen neuen Archivnamen. Falls ein sprechender/stabiler Benutzername gewünscht ist, interne BTB-Identität dennoch erhalten.

## Tatsächlich ausgeführte Prüfungen
1. ZIP-Inventar, Bildgrößen/visueller Motivvergleich, DOCX-XML, XLSX-Relationshipziele und PDF-Text/Metadaten/Links.
2. PYTHONPATH=<Serverquellen>/server python server/tests/test_btb_integration_031.py: 22 Tests PASS. Kontrollierte DB-Grenzen/AST-Funktionsprüfungen, kein laufender Server. Deckt Fotoeintragsbindung, Rework-/Rollenregeln und Metadatenregressionen ab.
3. test_reference_photo_cards_and_gps via runpy mit TemporaryDirectory direkt aufgerufen: 1 Referenztest PASS. pytest war nicht installiert; kein pytest-Lauf als bestanden ausgegeben.
4. Direkte Produktionsfunktions-Reproduktionen für Vier-Zeilen-Gruppierung und notiz-Vorrang: Verhalten bestätigt, gewünschte Android-/Server-Gleichheit nicht erfüllt.
5. Android-Aufrufketten und Datentransport statisch gegengeprüft. Kein Kotlin-/Gradle-/Lint-/APK-Lauf dieses Blocks; SDK/adb/Gradle nicht verfügbar. Historische CI-Ergebnisse nicht als neue Tests übernommen.
6. Keine Installation, kein Pixel/Poco-Test, keine Live-API-/NAS-Abfrage, kein Deployment. E2E OFFEN. Diese Prüfung ist keine Zusage, sämtliche App-Funktionen dynamisch getestet zu haben.

## Priorisierte Folgearbeit / Zuständigkeit
1. ANDROID: Beide sichtbaren Exportwege an denselben identitätsbewussten Exportkern anbinden. Tests müssen die Bedien-Einstiegspunkte einschließen.
2. ANDROID: Einen verbindlichen Dateimanifest-Schlüssel pro Aufnahme verwenden; alle CSV-/XLSX-Verweise aus demselben Manifest erzeugen. Zeitraum-/Gesamtumfang explizit regeln; jeder mitgelieferte Link muss auf einen vorhandenen Eintrag zeigen.
3. ANDROID + SERVER-Vertrag: verbindliche Gruppierung, Kurz-/Langtext, Ortsnamen und Datenstand für PDF/Word festlegen. Serverumsetzung ausschließlich im SERVER-Projekt.
4. SERVER: Wetter umbrechen, Rendererstand eindeutig kennzeichnen, Kartenspezifikation prüfen. Bestehendes Original erhalten; Vergleichs-/Neuausgabe nachvollziehbar behandeln.
5. Geräte-E2E: identischer BTB über Verlauf und Baustellenexport; Original+Preview, drei Kopien, Mehrfachzuordnung, zwei BTBs am selben Tag, Offline/Online, wiederholter Export nach Nachbearbeitung. Danach alle Links und PDF-/Wordinhalte erneut prüfen.

Kein Fehler als behoben markiert. Kein neuer APK-Build, kein Versionssprung, kein Servercode geändert.
