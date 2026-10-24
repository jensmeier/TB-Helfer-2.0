# PDF-REFERENZ-IMMENSTADT – Server-Auftrag 24.10.2026

Status: **IN ARBEIT / REFERENZVERGLEICH UND IMPLEMENTIERUNG OFFEN**.

## Benutzerentscheidung
Bestätigte Referenz ist der Immenstadt-BTB vom **22.09.2026**. Der Berchtesgaden-BTB vom **23.10.2026** weicht nach Benutzerbefund ab. Verbindlich sind kompakter PDF-Kopf, **drei Fotokarten nebeneinander**, Gliederung nach Abschnitt und Unterbaustelle, vollständige **vorhandene** Angaben zu Ort/Bahnhof/Gleis bzw. Weiche, KM, Uhrzeit, Firma, Text und GVP/Mast. Änderungskennzeichnung wie Referenz beibehalten. Fehlende Felder nicht erfinden. Foto-IDs, Nummerierung, Abschnitts- und Knotenbindungen unverändert erhalten. Abgeschlossene PDF-Dateien und Originalsnapshots niemals überschreiben.

## Abgleich mit bisherigen Aufträgen
- PDF-CHECK-013: Referenzlayout 22.09. war bereits als Vorschlag dokumentiert, aber nicht allgemein freigegeben. Dieser Auftrag erteilt jetzt die ausdrückliche Freigabe für **separaten Vergleich**, nicht zum Überschreiben bestehender Abschluss-PDFs.
- PORTAL-017: Eintragsfotos und originalgetreue Bildreferenzen sowie Nachtschichtzeiten implementiert; Funktion schützen.
- PORTAL-018: Schichtzeile im Server-PDF-Kopf entfernt, Benutzerbestätigung des bisherigen PDF-Kopfverhaltens schützen, nur referenzbelegte Abweichungen korrigieren.
- PORTAL-019/Anzeige: Bahnhof, Gleis/Weiche und Null-KM-Anzeige im Portal korrigiert; diese reine Anzeigeänderung ist nicht automatisch PDF-Renderer-Korrektur.

## Quellbefund vor Änderung
Im geprüften SERVER-Branch verwendet server/tbserver/btb_pdf.py den Renderer server/tbserver/btb_pdf_android_style.py. Dort stellt `_draw_photo_appendix` derzeit **2 Spalten** (`columns=2`, `cell_height=238.0`) dar; `_node_label` bevorzugt Kurzbezeichnung vor Bahnhof/Gleis. `_number` akzeptiert Boolean-Werte als Zahl. Die PDF-Metadatenzeilen werden beschränkt. Diese Stellen gegen tatsächliche Referenz und unveränderte Testdaten prüfen; nicht blind umbauen.

## Verbindliche Tests / Abnahme
1. 22.09.-Immenstadt-Referenz und 23.10.-Berchtesgaden-Originalexport samt Fotos aus unverändertem Testbestand zugänglich machen.
2. Dateien unverändert entpacken; Seiten und Metadaten prüfen; Ausgangs-PDFs rendern.
3. Für **denselben** Test-Snapshot mit **derselben** Bildmenge zuerst aktuellen Renderer, dann Korrekturversion rendern, jeweils unter **neuem Vergleichs-Dateinamen** in getrenntem Ausgabeverzeichnis.
4. Visuell alle Seiten auf Kopf, Raster 3 Karten, Zeilenumbrüche, Abschnitt/Unterbaustelle, vorhandenen Bahnhof/Gleis/Weiche, KM, Zeit, Firma, Beschreibung, GVP/Mast und Änderungsmarker vergleichen.
5. Automatisiert prüfen: stabile Datei-IDs, Bildnummern, Knoten-/Abschnittszuordnung, Null-KM vs fehlende KM, Unicode und Seitenumbrüche. Keine unbelegten Werte ergänzen.
6. Source-Check und PDF-Seitensichtprüfung nachweisen; CI/NAS-E2E getrennt kennzeichnen.
7. Erst danach konkrete geänderte Runtime-Dateien für GitHub-Desktop → File Station → Container Manager benennen. **Kein Deployment ohne Benutzerauftrag.**

## Schutz
Keine Android-Dateien verändern, keine produktive NAS-, DB- oder Backup-Mutation; gespeicherte Abschluss-PDFs und Originalstände bleiben unberührt. Keine automatische Neugenerierung historischer PDFs. Freigabe zunächst ausschließlich für separates Vergleichs-PDF.

## Aktuelle Blockade
Die im früheren Chat genannten ZIP-Exporte liegen im aktuellen Lauf nicht als programmatisch verfügbare Dateien vor. Ein echter Render-/Bildvergleich mit dem Originalbestand und ein seriöses Vergleichs-PDF sind daher noch nicht durchgeführt. Keine Freigabe oder Test-PASS behaupten.

## Ergänzung 24.10.2026 – Anklickbare GPS-Koordinaten im erzeugten PDF

Benutzeranforderung: Die GPS-Koordinaten jeder Fotokarte sollen **auch im fertig erzeugten und heruntergeladenen PDF anklickbar** sein. Anklicken öffnet eine Kartenansicht (Google Maps) der **gespeicherten effektiven** Foto-Koordinaten. Der PDF-Hyperlink ist eine echte PDF-Linkannotation (nicht nur blau formatierter Text), damit er in üblichen PDF-Readern erhalten bleibt.

Umsetzungsvorgaben: sichtbare, eindeutig beschriftete GPS-Zeile in jeder Karte; Link nur, wenn sowohl Breiten- als auch Längengrad gültig und im zulässigen Wertebereich sind. Fehlende/ungültige Koordinaten weder erfinden noch durch andere Bilder, Anlagen- oder Standortkoordinaten ersetzen. Gespeicherte Foto-ID, Nummerierung, Metadaten und bestehende Overrides unverändert erhalten. PDF-Ausgabe lokal mit PyMuPDF/PDF-Linkannotationen auf tatsächliche URI prüfen, zusätzlich im Reader auf Anklickbarkeit testen. Keine externen Kartenabfragen beim PDF-Erzeugen, kein automatisches Öffnen. Bereits abgeschlossene PDFs nicht überschreiben.

Quellbefund: server/tbserver/btb_pdf_android_style.py stellt bisher `GPS: <lat>, <lon>` als gewöhnlichen Text dar, aber legt an dieser Stelle keine PDF-Linkannotation an. Ergänzung zusammen mit dem bestätigten Referenzlayout und der Firmen-/Unterbaustellen-Gruppierung prüfen. Status: **ANFORDERUNG DOKUMENTIERT / IMPLEMENTIERUNG + TEST OFFEN**.

## Teilblock 24.10.2026 – erste Renderer-Korrektur

Status: **QUELLSTAND FERTIG (TEILBLOCK) / SOURCE-TEST OFFEN / RENDER-E2E OFFEN**.

- GitHub-Commit `586af17ee7e387f223144ed3e2a08318442c5415` ändert ausschließlich `server/tbserver/btb_pdf_android_style.py`.
- Fotoanhang vorläufig auf drei Spalten umgestellt; Kartenhöhe angepasst. Ortszeile verwendet bei vorhandener `worksiteNodeKey` den vorhandenen Knoten, sonst nur ausdrücklich gespeicherte Ortsfelder. GVP/Mast nur anzeigen, wenn entsprechendes Feld vorhanden ist. Metadaten werden nicht mehr pauschal auf fünf Einträge gekürzt.
- Bei gültigem Latitude/Longitude-Paar wird echte Google-Maps-`linkURL`-Annotation im PDF erzeugt; keine Ersatzkoordinaten und keine Kartenabfrage während des Renderns.
- ZIPs Immenstadt 22.09. und Berchtesgaden 23.10. liegen jetzt vor. Original-Abschluss-PDFs aus ZIP textuell untersucht: Immenstadt 5 Seiten, Berchtesgaden 6 Seiten, die jeweiligen vorhandenen PDFs enthalten 0 URI-Linkannotationen. Berchtesgaden-PDF zeigt zwei statt drei Bildspalten und fehlende Orts-/GVP-Angaben in Fotokarten.
- Separates lokales `PDF_Vergleich_Originale_Immenstadt_Berchtesgaden.pdf` mit nebeneinander gestellten **Originalseiten** erzeugt. Es ist **kein** neues Produktionsrenderer-PDF und kein Nachweis für den korrigierten Renderer.
- **Noch offen:** identischen Server-Snapshot im aktuellen Renderer rendern, PDF-Linkannotationen und Zeilenumbrüche automatisiert prüfen, visuell gegen Referenz vergleichen; Firmen-/Unterbaustellen-Sammelregel Portal und PDF nach stabiler ID, Name+Bahnhof+Gleis ohne Verlust implementieren; genaue Feldschlüssel der GVP-Daten prüfen. Keine CI-, NAS- oder End-to-End-Erfolgsaussage.
- Keine Original-PDFs ersetzt, keine Synology- oder Android-Änderung, kein Deployment. Bis zum Vergleich keine NAS-Freigabe für den Quellstand.

## Nachtrag 24.10.2026 – PDF-Ortslabels und Vorschauprüfung
- Commit `938cfb49a5c0651aaa1d81bfd03486f0edd97338`: PDF-Knotenlabel zeigt zusätzlich vorhandene Bahnhof- und Gleisangaben, statt sie bei Kurzname zu unterdrücken. Bestehende IDs und Zuordnungen bleiben unverändert. Source-Test und produktiver Renderer-E2E stehen aus.
- ZIP-basierte separate Berchtesgaden-Fotovorschau lokal mit PyMuPDF überprüft: 3 Seiten; 20 echte externe PDF-URI-Links (9/9/2 je Seite). Die Vorschau beweist nicht den Produktionsrenderer und ersetzt kein abgeschlossenes PDF.
- Firmen-/Unterbaustellengruppierung im Portal sowie Produktionsrender mit demselben Server-Snapshot weiterhin offen. Keine NAS-Freigabe, kein Deployment.

## Teilblock Portal-Fotoereignisse 24.10.2026
- Commit `7605a6fed92d39573d3b1adac975f18c273f099c`: Nicht an einen Arbeitseintrag gebundene Fotoereignisse in `server/templates/v1_btb.html` nach *gespeicherter* Firmen-ID und `worksiteNodeKey` gruppiert, statt als flache Gesamtliste. Unterschiedliche Firmen am selben Gleis bleiben getrennt; unbekannte Orts-ID wird ausdrücklich nicht automatisch auf Hauptbaustelle gesetzt. Bereits gebundene Fotos verbleiben beim Eintrag und werden nicht doppelt angezeigt.
- Isolierter JavaScript-Funktionstest per echter aus GitHub gelesener Funktion **PASS** (zwei Firmen am gleichen Knoten, mehrere Fotos derselben Firma, fehlender Knoten, bereits gebundene Datei ausgeschlossen). Dies ist kein kompletter Browser-/API- oder NAS-Test.
- Bestehende Arbeitseinträge wurden bereits in `renderContentGrouped` per Firma und `renderContentNodeGroups` per Knoten angezeigt; diese bestätigte Gruppierung nicht doppelt umgebaut. Noch offen: reale Foto-Metadatenfeldnamen/Legacyverhalten, Bildnummerierung, Produktions-PDF-Rendertest und vollständige visuelle Referenzabnahme; kein NAS-Deployment.

## Nachweis ZIP-Inventur 24.10.2026 – Originaldateien unverändert
- Referenz Immenstadt ZIP: 26 Einträge, BTB-Abschluss-PDF fünf Seiten, null PDF-URI-Links. `Bilderliste_gesamt.csv` hat **114 Datensätze** (enthält auch andere Tage); deshalb ist CSV-Gesamtzahl nicht gleich Bildzahl des BTB vom 22.09.
- Berchtesgaden ZIP: 33 Einträge, BTB-Abschluss-PDF sechs Seiten, null PDF-URI-Links. `Bilderliste_gesamt.csv` hat **20 Datensätze**, dessen PDF abweichende Fotoauswahl. Die Bildanzahl-Abweichung ist aus dem Export allein **kein nachgewiesener Datenverlust**.
- Separates Berchtesgaden-Preview: drei Seiten, per PyMuPDF geprüfte PDF-URI-Anzahl [9,9,2] = 20. **Vorschau/Export-Zählung, kein Produktionsrenderer- oder Snapshot-Paritätstest.**
- CSV-Felder: Datum, Zeit, Baustelle, Unterbaustelle, Bereich, KM, Firma, Bildtyp, Kurzbeschreibung, Bilddatei, GPS. CSV enthält kein verlässliches serverseitiges `worksiteNodeKey`, kein `btbEntryId` und keine kanonische PDF-Fotonummer; keine Rezuordnung auf bloßen Textvergleich.
- Nächster notwendiger Test: unveränderter Server-Snapshot (Knoten, Firma, Entries, Fotometadaten und effektive Bildnummerierung) durch produktiven Renderer; Original-/Korrektur-Seiten visuell und URI-Linkannotationen vergleichen. NAS-Übernahme nicht freigegeben.

## Benutzerhinweis – Fotoanzahl im Handy einstellbar (24.10.2026)
- Im aktuellen Server-PDF-Renderer `_photo_numbering` wird die Android-BTB-Kopfeinstellung `maxFotosInPdf` gelesen; ohne gesetzten Wert ist das Server-Fallback **60**. Der PDF-Renderer begrenzt seine Fotoliste über `ordered[:max_count]` und nummeriert erst danach. Immenstadt/Berchtesgaden dürfen daher nicht allein aufgrund einer Differenz zur gesamten ZIP-Bilderliste als fehlerhaft bewertet werden.
- Ausstehende Prüfung: Android-Oberfläche/Speicherung der Einstellung im Live-Android-Branch, tatsächlicher Wert im Berchtesgaden-Snapshot, Reihenfolge der Sortierung, parity zu bereits archivierten PDFs. Benutzerwert erhalten; **keine** automatische Umstellung auf alle Fotos oder Änderung der Einstellung.

## Prüfung 24.10.2026 – Anzahl der PDF-Bilder
- Tatsächliche Originale aus unveränderten ZIPs programmgesteuert mit PyMuPDF geprüft: Immenstadt-PDF enthält Bildnummern **1–13**, Berchtesgaden-PDF **1–15**. Mehrfache Vorkommen im Text sind Querverweise, nicht zusätzliche Fotokarten. Die ZIP-Datei Berchtesgaden enthält Bilddateien B001–B020, deren Gesamtzahl von der PDF-Auswahl abweichen darf.
- Der aktuelle SERVER-Renderer nutzt die optionale Android-Kopfeinstellung `tb_helfer_android_v1.maxFotosInPdf` bzw. ein flaches `maxFotosInPdf` aus dem Header, `int(_number(...) or 60)`, anschließend `ordered[:max_count]`. Maximalwert 15 könnte den Befund erklären, **ist aber im konkreten Berchtesgaden-Snapshot nicht bestätigt**. Der ZIP-Export enthält keine als Live-Snapshot verwendbare vollständige Header-/Knoten-/Fotonummerierungsinformation.
- Android-Quellprüfung konnte in diesem Arbeitsblock die UI- und Speicherstelle für `maxFotosInPdf` noch nicht sicher lokalisieren; kein Android-Funktions- oder End-to-End-PASS und **keine Android-Änderung**. Eine durch Benutzer eingestellte Auswahl nicht automatisch auf 20/60 ändern.

## Teilblock 24.10.2026 – Firmenblöcke im PDF zusammenhalten
- Bestätigte Fachregel: Pro Abschnitt erst Firma, dann dieselbe Unterbaustelle/Gleis als einmaliger Block; Firma 2 am selben Gleis separat. Bisher konnte die PDF-Renderer-Gruppierung bei ursprünglich verschachtelter Reihenfolge Firma 1 / Firma 2 / Firma 1 die Firma 1 ein zweites Mal drucken.
- Commit `ea3531ed1d02d1dd11904c98a347462311e46602` hält nun Firmen je Abschnitt zusammen und erhält die vorhandene Reihenfolge ihrer Knoten und zugehöriger Einträge. Kein Daten-/ID-Update, nur Ausgabereihenfolge.
- Source-Regressionstest, echter PDF-Rendertest und visuelle Abnahme mit Server-Snapshot weiterhin **offen**. Insbesondere der Android-Schlüssel `maxFotosInPdf` ist noch nicht durch Android-UI/Speicherpfad belegt. Keine NAS-Freigabe oder Deployment.

## Quellkontrolle 24.10.2026 – offener Foto-Kartenüberlauf
- Lesend den aktuellen PDF- und Portal-Quellstand verifiziert: 3 Spalten, PDF-Linkannotation, Android-Limit `maxFotosInPdf`, firm-first PDF-Gruppierung und firmen-/knotenbezogene Portal-Fotoereignisse vorhanden.
- **Neue bestätigte Quellrisikostelle:** Die Fotokarte hat weiterhin feste Höhe `cell_height = 300.0`. Metadaten-Schleife bricht ab, sobald `meta_y > top + cell_height - 8.0`; bei vielen/längeren Zeilen können Text und insbesondere die zuletzt angehängte GPS-Zeile fehlen. Damit ist die Forderung nach vollständigen vorhandenen Angaben noch **nicht erfüllt/nicht abgenommen**. Für die endgültige Lösung dynamische Kartenhöhe pro Reihe (maximaler Zellinhalt, automatische Seite), keine willkürliche Feldkürzung; erst dann produktiven Renderer und URI-Links visuell testen.
- Diese Quellkontrolle ist **kein Laufzeittest**. Im Testbestand fehlen kanonische Server-Snapshot-IDs/Fotoauswahl; ZIP-CSV allein ersetzt das nicht. Keine NAS-Freigabe und kein Deployment.
