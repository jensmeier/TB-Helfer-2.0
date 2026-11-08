# INTEGRATION-1108 – Umsetzung und Nachweise, 08.11.2026

## Geprüfte und umgesetzte Stände
- Android: feature/tb-android-2.0; Ausgang b0198a30, Auftrag e680958d, Runtime 01a6614fec5f054e7484830c8bd91eb0ca80356f, Buildkonfiguration 3228978309495277aaad666045ecc7030ddf090f. Version 1.3.43 / 200093 vorbereitet, KEINE APK entstanden.
- Server: feature/tb-server-1.0; Ausgang 6ea0d987, Auftrag c5e56266, Runtime a5a0f862fcef25bb8fcae874bd1282a59d07cb9c. Serverversion weiterhin 1.0.0-dev; PDF_RENDERER_VERSION tb-server-btb-pdf-6. Folgender Abschlusscommit ändert nur Prüfskript/Dokumentation.
- PR11 Android offen; PR6 Server Draft. Keine Zusammenführung oder NAS-Übernahme.

## Android
- Verlauf: ZIP teilen und ZIP speichern für genau den ausgewählten lokalen BTB. Kein vorgeschalteter Sync oder Serverkatalog. Speichern über Android-Dateiauswahl; Teilen mit temporär freigegebener Datei. Rotation während Berechnung bzw. Freigabevorbereitung verliert das Ergebnis nicht; Prozessende während Berechnung bleibt ein Neustartfall.
- Lokales ZIP kennzeichnet Umfang und fehlende lokale Dateien. Vollständiger Baustellenexport bleibt separat mit Katalog-/Vollständigkeitsprüfung. Abschluss-PDF bleibt gespeicherter gültiger Abschluss, Word kennzeichnet aktuellen lokalen Bearbeitungsstand. Alte PDFs werden nicht neu geschrieben.
- Deduplizierung ausschließlich im Export anhand existierender Attachment-ID oder identischem lokalen Pfad. Verfügbares Original vor Vorschau; keine Datenbank-/Dateibereinigung oder erratene Bildidentität.
- Wetterort: gespeicherte BTB-Koordinaten, danach passende GPS-Fotos, aktueller Handystandort nur für heutigen Bericht. Zeitraum und Bearbeitungsstand vor Übernahme erneut prüfen; Fehler sichtbar und alte Werte erhalten. Fehlende Wetterwerte nicht als 0/trocken erfinden. Zusätzliche Portal-Wetterzeiten nach den drei Android-Feldern bleiben erhalten.
- PDF-Einstellung zeigt verbindlich drei Bilder pro Reihe. Renderer war bereits dreispaltig; wirkungslose Rasterauswahl entfernt, gespeicherter Altwert und Fotoanzahllimit nicht migriert.

## Server/Portal
- Kurz- und Langtext gemeinsam anzeigen. Gleiche Firma/Ort/Beschreibung in einer Fotogruppe über Zeitlücken zusammenfassen. Keine automatischen neuen Foto-/Eintragszuordnungen.
- Drei Fotospalten und Fotoanzahllimit bleiben erhalten. Dateigröße der verfügbaren Serverdatei anzeigen, nicht als Kameroriginalgröße ausgeben.
- Wetterangaben umbrechen statt abschneiden, Niederschlagsmenge einschließlich 0 mm anzeigen; Android-/Server-Bezeichnungen angleichen. Renderer-Version 6 kennzeichnet die geänderte Ausgabe.

## Nachweise
- QUELLCODE GEPRÜFT.
- Lokal: 22 bestehende Produktionsfunktions-Regressionen PASS; 3 neue Pythonregressionen PASS; Portaltest 7 Assertions PASS; PDF-Referenztest (physisch 3 Fotospalten, Anzahlbegrenzung, Null-KM und anklickbare GPS-Links) PASS. Wetterseite/Fotoseite visuell geprüft.
- Server-CI42472440493 / Runtime a5a0f862: Container BUILD BESTANDEN; Job128706168370 Snapshot/Restore, Rechteentzug, Long-Poll255 Clients in3 Runden (keine Fehler), Recovery UPDATE/SECURITY PASS. Testumgebung, nicht Synology. Lasttest nur Sync/Long-Poll, kein Fotoworkload.
- Portal-CI42472440534 / Job128706168476: PASS inklusive Recovery/Galerie10000 Fotos und Chromium Desktop/Mobil. Keine NAS-/Pixel-Abnahme.
- Foundationjob128706168036 scheiterte am neu veralteten exakten PDF-5-Marker. Check34 auf PDF-6 aktualisiert und neue Pythonregressionen eingebunden; vollständiger Check34 samt PDF-Smoke lokal PASS. Sicherheits-/Migrationsprüfungen nicht entfernt. Gesamt-CI bleibt ROT; keine Wiederholung bereits erfolgreicher Serverjobs allein wegen dieser Prüfskriptkorrektur.
- Android-CI42472214428: Erstlauf und gezielt angefragte Buildjob-Wiederholung scheitern vor Kompilierung am Gradle-Downloadtimeout10s. Wiederholungsantwort enthält auch erneut den Contractjob. CI42472373330 / Buildjob128705957089 scheitert auch mit60s Timeout. Keine weiteren identischen Versuche. Unit/Lint/Kompilierung/APK wurden nicht ausgeführt, keine Artefakte.
- Android-Source-Checks03/13/19/83/85/86 weiterhin rot wie vor Auftrag (Exportaufruf verlagert, alte Text-/Versionsannahmen); unverändert, nicht als grün ausgegeben.
- Keine belastbaren monatlichen Abrechnungsminuten vorhanden, keine Kosteneinsparung behauptet, Budget unverändert.

## Offen und nächster konkreter Schritt
1. ANDROID: 1.3.43/200093 in Android Studio mit vorhandenem Gradle/Netzzugang und bisheriger Signierung kompilieren, Unit-Tests/Lint ausführen. Neue Integration1108Test-Tests müssen bestehen. APK und exakt passende Release-MD erst mit tatsächlichem Buildnachweis ausliefern. Bei Signaturkonflikt nicht deinstallieren.
2. GERÄTE: Pixel/Poco offline genau einen BTB exportieren/speichern/teilen; zwei BTBs am selben Tag, fehlende Datei, Original plus bekannte Servervorschau, Rotation, Abbruch und erneuter Versuch prüfen. Ausgepackte Excel-Bildlinks öffnen. Originalanzahl und Dateigrößen vor/nachher unverändert.
3. WETTER: gespeicherter Ort, Foto-GPS, heutiger/historischer Bericht, Nacht über Mitternacht, unbekannte Werte, 0 mm, Netzfehler, manuelle Änderung während Abruf und zusätzliche Portalzeiten testen. Wetterdienst extern erreichbar zu haben ist kein Geräte-E2E-Nachweis.
4. SERVER: NAS-Übernahme nur nach gesondertem Auftrag; anschließend denselben BTB auf Handy/Portal/PDF vergleichen. Alte Abschlüsse bleiben unverändert. Bis dahin kann die NAS weiterhin die alte Ausgabe erzeugen.
5. GEMEINSAM: veraltete Quellvertragsprüfungen separat fachlich modernisieren, keine erwarteten Marker blind entfernen. Bestehende weitere offene Punkte (u.a. KM-Abgleich) bleiben außerhalb dieses konkreten Fixpakets.

## Schutz
Keine produktiven Daten verändert, keine Migration ausgeführt, kein NAS-SSH oder Deployment, keine Kameraoriginale gelöscht. IDs, Offline-Outbox, Recovery, Konflikt-/Rechteschutz unverändert. Verbesserungen im Quellcode sind noch kein bestätigtes Funktionieren auf dem Handy.
