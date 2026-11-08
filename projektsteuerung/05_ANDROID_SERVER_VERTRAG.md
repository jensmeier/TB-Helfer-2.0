# TB-HELFER – ANDROID/SERVER VERTRAG
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
