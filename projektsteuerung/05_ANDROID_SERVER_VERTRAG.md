# TB-HELFER – ANDROID/SERVER VERTRAG

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

Vorhandene Fachverträge unter `vertraege/` bleiben gültig; kein paralleles Datenmodell.

| Bereich | Aktuelle Umsetzung | Grenze |
|---|---|---|
| Version/Identität | Ursprüngliche Basisversion, persistente Beitrags-/Mutations-ID; kein stilles409-Rebase | Konfliktbedienung offen |
| Trassen | Haupt-TB sendet/empfängt, Helfer empfängt; Dirty-Daten werden geschützt; kein Löschen wegen fehlender lokaler Kopie | Explizite Löschabsicht nicht vollständig angebunden |
| Atomarer Empfang | Validierung, Geometrie und Syncjournal in derselben Room-Transaktion | Reale Pixel-/Poco-Abnahme offen |
| Bereich/Rechte | worksite.view und vollständiger Datenbereich; unbekannter Bereich nicht pauschal für Helfer freigeben | NAS-Rechteentzugsmatrix offen |
| Gemeinsame KM | GET /survey-catalog/km-sync/resolve per Originalhash; vorhandenes /axes/{id}/km-sync mit Quelle/Version/Ankern | linear; KM-Sprünge/GVP-/Mast-Bearbeitung/Deaktivierung noch offen |
| Originale | unveränderte neue Importbytes, WLAN-Outbox → Datei → from-file → Parser; exakte Quellenbindung | ältere Originale reimportieren, mehrdeutige Zuordnung nicht raten |
| Delta | DEFECT/ACCEPTANCE/TRACK_DATA eingebunden; fachlistenbezogener Empfang; persistente Track-Wiederholung | objektweise Fachupdates und globaler Katalogfeed offen |
| Helfer/BTB | Schichtende nach Datenbestätigung; Haupt-TB finalisiert; Server16h-Frist | offline Helfer/verspätete Daten E2E offen |
| PDF | Cleanup nach DB-Commit kann bestätigte Datei nicht zurückrollen | echtes NAS-Dateisystem noch abnehmen |
| Fotos/Listen | Original lokal, kleine Sync-Versionen, drei Bilder/Reihe, Fotoanzahl/Word erhalten | reale Transfer-/Referenzexportabnahme offen |
| Logo/Version |40–400pt; authentifizierte App-Version/Buildheader | NAS-Anzeige/Übernahme offen |
| Update | Manifest/Hash und installiertes Signaturzertifikat vergleichen | privater vorhandener Schlüssel fehlt; kein belegtes kompatibles Update |

Ein Fehler beim Trassenabgleich darf nicht unbestätigt verschwinden: Journal/Fehlerprotokoll/persistenter Arbeitsauftrag bleiben. 15min-WorkManager ist ein verschiebbarer Fallback, keine Sofortzustellgarantie. Erfolgreicher Originalupload allein ist noch keine erfolgreiche fachliche Dekodierung.
Geschützt und erhalten: Word, drei Bilder pro Reihe, gespeicherte Fotoanzahl, stabile IDs und Originale sowie historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT-Markierungen. Neue Tests sind keine erneute pauschale Praxisbestätigung. Ungeprüfte ältere Restaufträge bleiben im Archiv offen zur Einordnung.


## KOMPAT-1120 – gemeinsamer Entwicklungsauftrag (20.11.2026)
Benutzer hat GitHub-Bereinigung ohne Verlust historischer Nachweise, synchrone Android-/Server-Statusführung und gezielte Fehlerkorrekturen freigegeben. Basis: Android 1.3.46/200096 Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf, Server 1.0.0-dev/API v1/PDF 6 Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Basispaarung ist keine reale Pixel/Poco/NAS-Abnahme. Neue fachliche Commits gesondert ausweisen. E01–E12 bleiben offen. Keine NAS-/Produktivdatenänderung. Neue Fehler F-01 bis F-07 in 09_KOMPATIBILITAETSBASIS.md, Altbestätigungen und Archiv behalten.


### PRUEF-1120-1BIS5 – Rechte- und Datenflussabgrenzung
Vor F-03/F-05-Korrekturen betroffene Objekt-/Team-/Bereichsrechte einschließlich Cursor, Tombstones und GLOBAL/COMPANY-Streckenkatalog verifizieren. Keine pauschale Sperre gemeinsamer Originale oder stiller Cursorverlust. Ausführliches Rechte- und Testkonzept: `uebergaben/PRUEF_1120_1_BIS_5.md`. E2E weiterhin offen.


## FACH-1120 – Prüfvertrag Foto/Text/Wetter/Export (OFFEN)
Sichtbare Kette: Kamera/BTB-Editor→Android Room/Offline-Outbox→Server API/DB→Portal und zweites Handy→Word/PDF/XLSX/ZIP. Nachträgliche Foto-Korrekturen müssen Firma, Kurz-/Langtext, Bildtyp, KM, Zuordnung und autorisierte Metadaten ohne Bildoriginal-/ID-Verlust versions- und konfliktgeschützt übernehmen. Wetterwerte inklusive Zeit/Ort/Quelle/0,0 mm zu Schichtbeginn und Schichtende getrennt speichern und in BTB/Word synchron halten. Detaillierte Prüfung in `projektsteuerung/uebergaben/FACH_1120_TEXT_FOTO_ZIP_WETTER.md`, E2E OFFEN.

## FOTO-KURZTEXT / BTB-LANGTEXT – Klarstellung 20.11.2026
Verbindliche Fachentscheidung: **Beim Foto selbst und seiner Bildunterschrift ausschließlich Kurztext anzeigen.** Ein optionaler Langtext (bislang `FotoEntity.notiz` / Server `photo_metadata.notiz`) ist **für die ausführliche BTB-Beschreibung und Word-Ausgabe** bestimmt, nicht als zweite sichtbare Bildbeschriftung. Beide Texte bleiben einzeln nachträglich bearbeitbar; Originalfoto, Bild-ID und ursprüngliche Aufnahmedaten unverändert. Gemeinsamer BTB/PDF/Word-Referenzfall, insbesondere drei Fotos pro Reihe, bleibt geschützt. Gesamte Fotolisten und ZIP sollen Kurztext als Fotobeschriftung führen; technische Langtext-Speicherung für BTB/Word nicht versehentlich löschen. Codeänderungen nur mit gezieltem Regressionstest als abgeschlossen markieren.
