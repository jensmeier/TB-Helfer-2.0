# GESAMT-1108 – Implementierungskandidat

Auftrag: Benutzer „okay alles bauen“, 08.11.2026. Keine NAS-Übernahme.

Server: Leserechte und KM-Bereich für Trassendaten/Abschnitte; PDF-Nachbereitung kann einen bereits bestätigten Abschluss nicht mehr zurückrollen; manuelle GPS-Beiträge bleiben neben Original-TRA aktiv; Originalsignatur-Resolver; 16h-Abschluss über vorhandenen Abschluss-/PDF-Pfad im Worker (nur ACTIVE, nicht REWORK).

Android-Gegenstück: 9c1354eb163fd4d65caed1304a485821c016d7aa, 1.3.45/200095. Atomarer Room-Empfang mit Journal, Dreiwegevergleich ohne Konflikt-Rebase und ohne implizite Löschungen; Original-Outbox/WLAN und Parseraufruf; gemeinsame lineare KM-Abschnitte; Fach-/Track-Delta; Versionsheader; 400-pt-Logo; Zertifikatsvergleich vor Update.

Lokale neue Serverregressionen: 17 bestanden (echte Produktionsfunktionen, SQL-Doubles, echte temporäre PDF-Dateien; kein PostgreSQL/NAS-E2E). Portal-JS-Prüfungen 152/157 bestanden. Android-Build steht aus.

CI prüft die tatsächliche Gegenstelle an einer festen Commit-ID. Alle bisherigen Foundation-Prüfungen laufen weiter. Ein fehlerhafter Source-Block bleibt am Ende verpflichtend rot, verhindert aber nicht mehr die nachfolgenden isolierten Datenbank-/Auth-Prüfungen. Sicherheits-, Recovery- und Restore-Gates bleiben erhalten.

Offen: ältere Source-Prüfungen teilweise veraltet, Gesamtlauf noch nicht grün; echte Installation/Migration/Pixel/Poco/NAS-E2E; bisheriger APK-Signaturschlüssel; experimentelle Android-DB 20/21; UI für Trassenkonflikte und gemeinsame Löschungen; KM-Sprünge/GVP-Sonderfälle; vollständiger objektweiser Fach-Deltaabgleich (derzeit betroffene Fachliste einer Baustelle); Originale aus älteren Importen nicht rekonstruierbar.

Geschützt: IDs/Originale, bestehendes Word, Drei-Fotospalten und Fotoauswahl. Bestehende Tests werden nicht für grüne Ergebnisse entfernt.
