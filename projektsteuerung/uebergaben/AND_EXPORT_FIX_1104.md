# AND-EXPORT-FIX-1104

Nutzerauftrag: „bauen und ändern“. Anforderungen vor Code in 318897e und Server-Darstellungsvertrag in d8eab0d gesichert.
Umsetzung: 9b0571cf5e745179421e987d35a622544e4064c6, Android 1.3.37 / 200087. Server bleibt 6ea0d987bebc67c346fca24714e4e1b9a8c852ea.

## Änderungen
- Öffentlicher BtbExportPaket.erstellenBaustellenExport führt auf Dispatchers.IO Vorab-Sync, Roh-Dokumenterzeugung, MergeSafe/GesamtMedien und finale Linkprüfung aus. Verlauf und Baustellenliste benutzen denselben Einstieg; der Roh-Export ist privat. Keine doppelte Normalisierung. Fehlende/mehrdeutige Hauptknotenzuordnung wird explizit behandelt: kein Hauptknoten = lokaler Altbestand, mehrere = Abbruch.
- Vorab-Sync vor Word/Verlauf/Fotolisten statt erst nach Dokumenterzeugung. Das ergibt keinen atomaren Live-Serversnapshot; lokale Änderungen und bereits abgeschlossene PDFs können weiterhin verschiedene Stände darstellen.
- Roh-Fallback: eine gemeinsame Fotodateiliste für Gesamt-XLSX, CSV und Binärdateien. Alle verfügbaren Baustellenbilder werden mitgeliefert, BTB-Dokumente bleiben im gewählten Zeitraum. Fehlende lokale Dateien werden nicht als vorhandene Excel-Bildlinks ausgegeben. CSV verwendet Exportnamen. Bei nachfolgendem GesamtMedien-Aufbau entfällt die nutzlose doppelte Komprimierung der normalen Roh-Bilder.
- GesamtMedien nutzt zusätzlich persistierte Attachment-Downloadpfade zur Zuordnung alter Vorschauen. Identifizierte Original-/Vorschaudateien erhalten denselben Exportschlüssel; ein verfügbares Original wird gegenüber der mobilen Vorschau bevorzugt. Keine visuelle Ähnlichkeit, Aufnahmezeit oder GPS als Identitätsbeweis. Keine Änderung/Löschung der lokalen Fotodatensätze.
- Schichtlisten-Dateinamen enthalten den BTB-Token, damit mehrere BTBs mit gleichem Datum/Start nicht dieselbe Liste überschreiben.
- ExportArchivPruefung kontrolliert die relativen XLSX-Hyperlinks am fertigen ZIP. Fehlende Ziele stoppen das Teilen mit Fehlermeldung. Externe Kartenlinks werden separat erkannt; Pfade außerhalb des Archivs abgewiesen.
- Nur aktuell gültiges Abschluss-PDF exportieren. Ein wieder geöffneter BTB darf kein altes PDF als aktuellen Abschluss exportieren. EXPORT_HINWEISE.txt erläutert Umfang und Unterschied zwischen gespeichertem PDF und aktuellem lokalen Word.
- Zeitgesteuerte Scheinprozentanzeige entfernt. Bildanzahl wird aus dem fertigen Archiv gezählt.
- APK-Erzeugung erfolgt im bestehenden Android-CI-Job nach Unit/Lint nur bei explizitem [apk]-Commitmarker. Bestehende Quellvertragsprüfungen bleiben unverändert; kein zusätzlicher Server-Gesamtlauf und kein paralleler APK-Workflow.

## Lokale Nachweise
- Acht Archiv-Regressionsfälle gegen den kompilierten Produktions-Java-Prüfer im Standalone-Harness PASS: Tagespfad, fehlerhafte Gesamtnummer, fehlendes Bild außerhalb Zeitraum, kodierter Umlaut/Leerzeichen, literales Plus/Prozent, externer GPS-Link, Verlassen des Archivs, gemeinsames Bildziel.
- Originale Nutzer-ZIP mit 83 fehlenden Gesamtlinks wird tatsächlich abgewiesen. Original unverändert.
- Beide Android-Aufrufstellen und Reihenfolge Sync → Rohdaten → Finalisierung → Prüfung statisch kontrolliert. Kein Geräteausführungstest.
- Workflow-YAML geparst; Release-Version/Build und unverändertes MINIMUM_BUILD 200067 abgeglichen.

## Offene Grenzen / Serverauftrag
- Serverdarstellung bleibt separat offen: Fünf-Minuten-Gruppierung, Notiz statt Kurz+Lang, Wetterellipse, Dateigröße und eindeutiger Rendererstand. Verbindliche Vorgabe unter 05_ANDROID_SERVER_VERTRAG.md. Keine server/**-Änderung und kein NAS-Deployment in diesem Block.
- Alte gespeicherte PDFs werden nicht nachträglich umgeschrieben. Keine Zusage, dass das vorhandene Berchtesgaden-PDF bereits anders aussieht.
- Nicht eindeutig zuordenbare Altkopien (etwa ohne erhaltene Attachmentbindung) bleiben erhalten. Die Zahl 18 → 9 ist auf dem Nutzergerät noch nicht nachgewiesen.
- Angemeldeter Offline-Gesamtexport mit nicht verfügbarem Katalog bleibt offen; Vollständigkeit wird nicht vorgetäuscht. Lokaler Altbestand ohne Serverbindung bleibt exportierbar.
- Pixel/Poco-Update, Export aus beiden Menüs, ZIP-Entpacken/Excel, Original/Preview/Altkopie, parallele BTBs und Server-E2E sind offen. APK-Build ist kein E2E-Nachweis.

## CI / Auslieferung
Lauf: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/41794414368
Buildjob 126455006956: SUCCESS. Unit/Kotlin/KSP/Java 6m30s, Lint 1m52s, assembleDebug 3m25s. Gesamt-CI bleibt wegen des getrennten Quellvertragsjobs FAILURE.
Quellvertragsjob 126455006766: FAILURE; Original-Logs gelesen. Bestehende Checks 13/19/83/85/86 melden alte Schreibpfad-/DELETE-Marker, feste Versionen 200051/200052 bzw. alte Updatertexte. Nicht deaktiviert; Gesamt-CI deshalb nicht grün melden.

### Tatsächlich gelesene Validierungsberichte
- Validierungsartefakt 13305391520 heruntergeladen; Archiv-SHA256 5351e3fc52861c5b03cd7f5fd303c4ab95572dceaa4e179df71a53c8409a605d stimmt mit GitHub überein.
- JUnit-XML: 151 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen. Darunter alle 8 ExportArchivPruefungTest-Fälle PASS.
- Lint-XML: 0 Error/0 Fatal, 388 Warning. Keine Issue verweist auf die geänderten Export-Dateien.

### APK und Begleitdatei
- Artefakt13305058156, ZIP-SHA2569a9ac3ec56d967bc8aa549de92ef998100b895ba160e7418283766eab96b5e5e lokal mit GitHub-Metadaten abgeglichen.
- APK26878939Bytes, SHA256a215370d825f99f112c4f386ca52ee508f3e807b5fb8624d9ea0a702477db0bf. Heruntergeladene Bytes stimmen mit der Begleitdatei desselben Builds überein.
- SDK-apkanalyzer im Build bestätigt de.tbhelfer /1.3.37/200087. apksigner verify erfolgreich, v1/v2 gültig. Debug-Zertifikat SHA256f8020cb09804c6fe1ae275a5938d5dc2bba56ca20d0385c1191693598ab4baf3.
- Diese CI-Signatur unterscheidet sich von der früher ausgelieferten CI-APK1.3.35/200085 (cac77b36f13253f39b4feecd74ebcc8442361467f3eaefaef8f41a734b5a7b76). Kein direktes Update über jene APK möglich. Tatsächlich installierte Signatur nicht ausgelesen. Für bestehende Handyinstallation wie bisher lokal mit derselben Android-Studio-Signierung bauen; keine Deinstallation/Datenlöschung.
- APK und originale Build-Release-MD gemeinsam bereitgestellt. Keine Veröffentlichung im Serverkatalog, keine Installation, kein Geräte-/NAS-E2E.

Status: ANDROID-QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / APK-BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN / SERVERKORREKTUREN OFFEN.
