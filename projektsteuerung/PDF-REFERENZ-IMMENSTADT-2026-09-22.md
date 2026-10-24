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
