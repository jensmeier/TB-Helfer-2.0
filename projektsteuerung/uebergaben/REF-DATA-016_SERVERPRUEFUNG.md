# Gezielte Serverprüfung REF-DATA-016 – 04.10.2026

## Umfang und Ergebnis

Geprüft wurden die betroffenen Vermessungs-/Netz-/Resetpfade, die Einbindung in vorhandene Admin-/Portal-/Geräteauthentifizierung, Mutation-Receipts und die neue Cursorübertragung. Dies ist kein vollständiger Audit sämtlicher Serverfunktionen und kein Zugriff auf den laufenden NAS-Server.

1. **Bestehender Fehler korrigiert:** `_save_receipt` schrieb pauschal SURVEY_CATALOG_SEGMENT, auch für REF015-Achszuordnung und Quellreset. Der Helper erhält nun einen optionalen Typ; alte Segmentaufrufe bleiben unverändert, Workspace schreibt AXIS bzw. ASSET. Produktionsfunktion mit Parameterprüfung getestet.
2. **Neue Referenzen geschützt:** Aktive Netzobjekte blockieren Quellreset und allgemeinen Vermessungs-Testreset, bevor Originalreferenzen verloren gehen. Allgemeiner Reset teilt den Transaktionslock des Workspace/Netzes. Auch dessen Vorschau meldet die Sperre. Keine Löschung/Reset auf NAS ausgeführt.
3. **Sync abgesichert:** Knoten- und Objektänderungen atomar, erwartete Versionen, doppelte Request-ID nur bei identischem Input, Feed pro Betriebsstelle mit einer vollständigen Mutation pro Ereignis. Quell-/KM-Daten werden nicht geschrieben. Eigenen commit_cursor nicht als fremde Änderungen überspringenden Lesecursor verwenden.
4. **Purge-Kompatibilität:** Neue Netzreceipts löschen sich bei autorisiertem Benutzer-Purge mit dem Benutzer; fachliche Netzobjekte bleiben GLOBAL, Actor in History wird anonymisiert (ON DELETE SET NULL). Kein neuer RESTRICT-Fremdschlüssel blockiert den bestehenden Benutzer-Purge.
5. **Veralteten Quelltest korrigiert:** Check28 erwartete wörtlich „Bearer“ im Source. Die produktive Prüfung normalisiert das Scheme schon mit `scheme.lower() != "bearer"`. Kein Tokenfehler nachgewiesen; Test prüft jetzt den tatsächlichen Guard statt Großschreibung.
6. **Stale Geometrie:** Aktuelle Netzabfrage zeigt Prüfbedarf statt alte Kurve, wenn ein Originalsegment geändert/inaktiv/archiviert ist. Historische Snapshots bleiben unverändert.

## Nachweise

- Checks120–126: 80 Python-Prüfungen bestanden.
- Check127: 17 neue Prüfungen bestanden, Produktionsfunktionen per AST/Pydantic mit transaktionalem Speicherdoppel. Keine echte PostgreSQL-Transaktion oder FastAPI-HTTP-Anfrage damit behaupten.
- Check119: tatsächliches Template-JavaScript im Node-DOM-/Transport-Doppel bestanden, einschließlich neuer Punkt-/Anschluss-/Fehler-/Doppelklick-/CSRF- und WMS1.3-BBOX-Prüfungen.
- Auth-Quellchecks21/28/29 bestanden; dies sind Vertragsmarker-/Syntaxprüfungen, keine Penetrationstests oder Geräteauth-E2E.
- Python-Syntax bestanden. Schema aus produktiven Pydantic-Modellen erzeugt.

## Offene Abnahme

- Echte HTTP-/PostgreSQL-/PostGIS-Pfade, Migration21 auf NAS, visuelle Seitenleiste und gemeinsame Punktänderung im Browser.
- Zwei Handygeräte mit neuer Android-Anbindung: Offline-Outbox, Konflikt, Retry, Download, Kilometerbeobachtung und bestehende KM-Kalibrierung.
- Weitere acht Luftbildländer nur mit offiziellen Links/Prüfstand; keine Behauptung fertiger Einbettung.
- Vollständiger Server-/Portal-E2E einschließlich BTB, Bilder, Worker, Backup/Restore und Lasttest ist durch diesen Block nicht ersetzt.

In dieser Umgebung fehlen FastAPI/Psycopg; Installation war mangels erreichbarer Paketquelle nicht möglich. Der bekannte Chromium-Download ist blockiert. Keine NAS-Zugangsdaten abgefragt, keine Originaldateien oder privaten Testpakete veröffentlicht.
