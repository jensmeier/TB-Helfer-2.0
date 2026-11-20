# TB-Helfer 2.0 – Fachprüfauftrag: Eingaben, Fotos, Exporte, Suche und Wetter
Stand: 20.11.2026 · Benutzerwünsche · Prüfung VOR Entwicklung

## Verbindliche Benutzeranforderungen (OFFEN, keine bestätigten Fehler ohne Reproduktion)
1. Texteingabe: Android-Tastatur-Rechtschreibung/Autokorrektur und Wortvorschläge für Kurz-/Langtext, Foto, BTB und Mängel prüfen. Falsche automatische Korrekturen dürfen fachliche Namen, Firmen, KM, GVP, Gleis, Weichen, Schweißarbeiten und bereits gespeicherte Texte nicht unbemerkt verändern; Benutzereingaben müssen manuell korrigierbar sein. Fachwörterbuch optional, ohne ungefragtes automatisches Umschreiben.
2. Foto nachträglich ändern: Firma, Kurztext, Langtext/Notiz, Bildtyp, Unterbaustelle, BTB-/Eintragszuordnung, KM, GPS/Metadaten und weitere fachliche Angaben auf ANDROID und SERVER/Portal prüfen und, soweit Rechte/Verträge erlauben, korrigierbar machen. Originalbild, Bild-ID, Änderungsverlauf und geteilte Verweise schützen. Falsche Firma muss wechselbar sein.
3. Kurztext/Langtext: gesamte Verarbeitung KAMERA → ROOM → Offline-Outbox → API → PostgreSQL → andere Geräte → Portal → PDF/Word/XLSX/ZIP prüfen; unvollständige/überschriebene Felder als reproduzierbare Fehler erfassen; explizite Leereingaben nicht mit fehlenden Werten verwechseln.
4. Gesamte Fotolisten: Android + Server, Gesamt-Fotoliste XLSX (auch nach Verschieben/Kopieren des ZIP-Ordners), Tag-/BTB-Listen, Fotoanzahl/-zuordnung, Mängelliste XLSX und Abnahmeliste XLSX prüfen; überflüssige Gesamt-CSV nicht wieder einführen. Ein Fotodatensatz/Datei darf nicht mehrfach in Listen erscheinen, ohne fachlichen Grund.
5. ZIP/Tagesabschluss: mit/ohne Bilder, großen Beständen, Foto-Metadaten, leeren Listen, wenig Speicherplatz, Prozessunterbrechung, erneutem Versuch, Exportziel und Entpacken prüfen; ZIP-Crash aus früheren Berichten nicht ohne aktuelle Reproduktion als erneut bestätigt ausgeben.
6. Suche Bilder/BTB: Android und Server/Portal, alle Filter (Baustelle, Unterbaustelle, Firma, Suchtext, KM-Bereich, Datum, Bildtyp, Mangel, Abnahme, BTB-Status), Sortierung, Löschung/Änderungen, Index/Aktualisierung und Rechte beachten. Vorhandene Daten dürfen durch einen Filter nicht gelöscht werden.
7. Wetter: Aktuelles Fehlerbild vom Benutzer: Temperatur, Regenmenge (mm) und trocken/heiter/bewölkt stimmen/erscheinen nicht zuverlässig. Beim Schichtbeginn Wetter abrufen, bei Schichtende aktualisieren und tatsächliche Zeitpunkte vergleichen; Quelle/Stand/Ort/Zeitzone, 0.0 mm != fehlend, Nacht über Mitternacht, Offline-Fehler und manuelle Angaben erhalten. Werte versioniert in BTB und Server speichern, korrekt in Word sowie PDF übernehmen; vorher referenzierter Word-Aufbau bleibt geschützt.
8. Spracheingabe aus dem vorigen Gespräch: nutzbare Deutsch-Offline-Erkennung, ausdrücklich freigegebene Online-Option, Fachwort- und Firmenerkennung, Zeit/KM/Unterbaustelle, bessere Aufnahme bei Pausen/Lärm, zuverlässige Entwürfe und explizite Bestätigung vor BTB-Änderung.
9. Einstellungen: Nur Telefonnummer/E-Mail des Benutzers am Handy editierbar; Name und Firmenstamm aus Serveridentität; Server-PDF ist verbindlich, 3 Bilder pro Reihe fest. Keine doppelten PDF-/Fotomenüs; gespeicherte Layout- und Profilwerte nicht löschen.

## Erste Quellcode-Anhaltspunkte, KEIN E2E-Nachweis
- Android `ui/bautagebuch/BautagebuchFragment.kt`: OpenMeteoClient direkt mit Stundenwerten; Wettervorschlag beim BTB-Start sowie späterer manueller/automatischer Abruf vorhanden. `BtbWeatherPolicy.kt`: Wolken/Niederschlagskategorien und 0,0 mm.
- Server `tbserver/btb.py` GET `/btbs/{btb_id}/weather/suggestion` verlangt `started_at` und `ended_at` (Zeilen 489–500), kann also für aktiven BTB ohne Ende keinen Schichtbeginnvorschlag über diesen Endpunkt liefern. Der Android-Weg verwendet direkt Open-Meteo. Vertrag und Ein-/Ausgabeprovenienz getrennt prüfen.
- Android `BtbPhotoMetadatenStore.kt` und Server `btb_photo_meta.py`: Firma, Kurztext und Notiz mit verschiedenen Metadatenschichten; `media_gallery.py` verwendet Basis und BTB-Link-Overrides. Das ist ein Prüfpfad für 'nicht übernommen', kein ohne Reproduktion bestätigter Fehler.
- Android `SucheViewModel.kt`: Foto-Volltext sucht in Kurztext, Notiz, Firma, Objektkennung, Indikator; BTB-Suche liest auch lokale Dateien/Ordner und ordnet über Ordnernamen zu. Lokale Suche ist nicht automatisch ein vollständiger Server-/Portalindex.
- Android `TagesZipAuftrag.kt`, `TagesZipViewModel.kt`, `BtbExportPaket.kt`: ZIP-Erstellung/Status/Exportpfad vorhanden; Regression in mehreren Szenarien offen.
- Vorhandene Tests/CI und bisher bestätigte Funktionen nicht als defekt deklarieren. Keine erfolgte reale Pixel-/Poco-/NAS-Abnahme.

## Testvorgehen (erst Quellcode, dann isoliert, dann E2E)
A. ANDROID: Inventar aller Editoren und Tastatureinstellungen; Fotoänderung lokal mit Dirty/Outbox; Offline-/Online-/Konflikt-Ketten; Suche/ZIP/Listen; Text- und Wettermodell.
B. SERVER: Endpunkt-/Rollen-/Bereichsrechte, PATCH/Versionskonflikt, Foto-Link vs. Foto-Original, Such-/Listen-/XLSX-/Word-/PDF-Ausgabe, Wetterquelle und Schichtdaten.
C. GEMEINSAM: Pixel → Poco → Server/Portal → Rücksync mit absichtlich falscher Firma und späteren Korrekturen; Wetterbeginn/-ende; Word/ZIP/XLSX auf einer referenzierten Testbaustelle vergleichen.
D. Gezielte Regressionen; alte Bestätigungen/Originalfotos/Word-Referenz, 3 Bilder pro Reihe, Fotoanzahl, ID- und Recovery-Schutz erhalten.
Status: ANFORDERUNG GESICHERT / QUELLCODE-TEILSICHTUNG; CODEÄNDERUNG NICHT AUSGEFÜHRT; CI/INSTALLATION/DEPLOYMENT/E2E für neue Anforderungen OFFEN.
