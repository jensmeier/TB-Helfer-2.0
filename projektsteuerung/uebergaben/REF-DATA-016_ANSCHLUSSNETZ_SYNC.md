# REF-DATA-016 – Anschlussnetz, Karteneditor und gemeinsamer Austausch

Stand: 04.10.2026. SERVER/Portal zuerst, Android danach.
Anforderungscommit vor Code: `4f44ba0951bebeaead458d1b7c82eb5df9e41bf7`.

## Bedienung

Achse öffnen → **Gleis / Weiche zuordnen …** öffnet eine Seitenleiste direkt neben der Karte. Auf schmalen Bildschirmen steht sie unter der Karte mit eigenem Scrollbereich; Prüfen/Speichern bleibt im Fuß erreichbar.

- Objekttyp Gleisabschnitt, freie Strecke oder Weiche; Bezeichnung und Betriebsstellenkürzel. Freie Strecke benötigt eine Streckennummer.
- Für jeden Punkt die Eingabe wählen: auf einer ausdrücklich gewählten Originalachse oder frei auf Karte/Luftbild. Die ausgewählte Originalachse darf für jeden Punkt wechseln.
- WA/WE/WEZ bzw. Anfang/Ende einzeln setzen. Originalstationen sind geometrische Achspositionen in Metern, keine Bahnkilometer.
- Vorhandene Anschlüsse lassen sich über die Auswahlliste übernehmen. Nach Speichern an einem Anschluss mit „Gleis hier beginnen“, „Freie Strecke hier beginnen“ oder „Weiche hier beginnen“ weiterarbeiten.
- Gl4→WA→WE→Gl4 und Gl4→WE→WA→freie Strecke sind gleichermaßen zulässig. Die Rollen der jeweiligen Weiche bleiben fest, die Betrachtungsrichtung ist unabhängig.
- WEZ Weiche108 und WA Weiche109 können dieselbe Knoten-ID verwenden. Bloße Nähe verbindet keine Punkte.
- Setzen an einem bestehenden Punkt ändert diesen gemeinsamen Punkt; die Vorschau nennt die Zahl betroffener Objekte. Alle angeschlossenen aktiven Objekte werden atomar mitgeführt und versionsgeprüft. „Neuer Punkt“ löst diese Rolle bewusst vom bisherigen Anschluss.
- „Prüfen“ vor Speichern. Änderungen an Eingaben verwerfen die Vorschau. Kein automatisches Wiederholen eines unklar ausgegangenen Schreibaufrufs.
- Gespeicherte Objekte bearbeiten/deaktivieren/reaktivieren; Versionshistorie bleibt. Kein globales Hartlöschen.
- Bisherige REF015-Achszuordnungen bleiben erhalten und sind über „Bisherige Zuordnungen“ erreichbar. Keine stillschweigende automatische Migration oder Zusammenführung mit dem neuen Netz. Die bestehenden Originalachsen bleiben sichtbar.

## Geometrie und Kilometer

Originale TRA/GRA/GVP und bestehende manuelle KM-Overlays werden durch dieses Modul niemals beschrieben.

Knoten haben entweder einen geprüften Verweis auf eine aktive globale Originalachse (axis_id, axis_version, segment_id, segment_version, station_m) oder eine manuelle WGS84-Position mit Herkunft MAP/GPS, optional Genauigkeit/Höhe/Aufnahmezeit. Der Server löst Achspositionen erneut auf und übernimmt keine vom Client erfundenen abgeleiteten Koordinaten.

Bei zwei Punkten derselben Originalquelle wird der genaue Stationsausschnitt verwendet, auch bei fallender Betrachtungsrichtung. Für WA→WEZ kann ein anhand der ausdrücklich referenzierten Abzweigachse belegbarer Anschluss verwendet werden. Ist der Verlauf nicht belegbar, bleibt die Verbindung eine deutlich gestrichelte **manuelle Näherung**; ihre Länge ist kein vermessungstechnischer Nachweis. Drei Punkte beweisen keinen Bogenverlauf.

Weichen werden blau dargestellt; Netzknoten und Rollen sind beim Hineinzoomen sichtbar. Originale behalten ihre bestätigten KM-Farben. Stammlänge und Abzweiglänge werden unabhängig geführt. Fachliche Gleisgrenzen ändern keine Bahnkilometer oder Kilometersprünge. Streckengleiszuordnung ist keine automatisch nachgewiesene Bahnhofsgrenze.

`km_observations` erhält manuell abgelesene Werte (stabile Beobachtungs-ID, Streckennummer, km_m in Metern, Zeitpunkt, MANUAL_READING). Dies sind ungeprüfte Beobachtungen, keine Kalibrierung. Ein gemeinsamer Punkt darf mehrere streckenbezogene Beobachtungen haben. Die bestehende Original-/GVP-/MANUAL_TRA_SECTION-Kilometrierung bleibt ihre eigene Referenz. Ein Wert ohne Referenz wird nicht als belastbare KM ausgegeben.

Bei archivierten, inaktiven oder versionsgeänderten Quellsegmenten markiert die aktuelle Netzabfrage `needs_review` und liefert für betroffene Objekte keine alte vermeintlich gültige Kurve. Historische Change-Snapshots bleiben unverändert. Quellreset und der allgemeine Vermessungs-Testreset werden durch aktive referenzierende Netzobjekte blockiert (beim allgemeinen Reset: SURVEY_TEST_RESET_NETWORK_REFERENCES_ACTIVE); nach deren Deaktivierung dürfen Testquellen zurückgesetzt werden, Originaldateien bleiben.

## API-Vertrag, Schema 1

Basis `/api/v1/survey-catalog/network`. Capability `survey_connection_network_v1` im bestehenden `/api/v1/capabilities`.

| Methode/Pfad | Vertrag |
|---|---|
| POST `/preview` | Mutation nur prüfen, abgeleitete Geometrie/Warnungen/preview_token liefern; keine Fachänderung |
| POST `/mutations` | Dieselbe Mutation mit preview_token atomar speichern |
| GET `/entities?station_code=MGHN&limit=200&after_id=...` | Aktueller globaler Netzbestand einer Betriebsstelle, einschließlich inaktiver Datensätze; UUID-Paginierung |
| GET `/entities/{id}` | Einzelobjekt/Knoten und bei Knoten alle angeschlossenen aktiven Objekte |
| GET `/changes?station_code=MGHN&after=0&limit=50` | Dauerhafter Cursor-Feed pro Betriebsstelle; ein Ereignis enthält die vollständige atomare Mutation |

Authentifizierung: vorhandenes `current_portal_user`/`require_csrf`. Admin-/Portal-Cookies verlangen ihre bestehende CSRF-Prüfung; Geräte verwenden den bestehenden Bearer-Gerätetoken. Keine neuen Tokens, keine Absenkung der Aktivierungs-/Benutzerprüfung. Wie der gemeinsame Katalog ist das Netz GLOBAL: authentifizierte Benutzer dürfen ergänzen/korrigieren, Originalquellenzugriff ausschließlich GLOBAL. Actor-ID bleibt intern in History, öffentliche Antworten enthalten keine Bearbeiteridentität.

Anfrage: `schema_version=1`, stabile `client_change_id`, `nodes[]` (max32), `objects[]` (max32), `affected_objects` (Objekt-ID→erwartete Version), `preview_token` nur für Save. UUIDs werden beim Offline-Anlegen erzeugt und später nicht ersetzt. `base_version=0` bedeutet Neuanlage; bestehende Einträge verlangen exakt die aktuelle Version. Unbekannte Eingabefelder werden abgewiesen.

Knoten: `id`, `base_version`, `entity_type=NODE`, `station_code`, `active`, genau eines aus `position` und `binding`, `km_observations`, `note`.

Objekt: `id`, `base_version`, `entity_type=OBJECT`, `station_code`, `active`, `kind=TRACK|LINE|SWITCH`, `name`, optionale `route_number`, `track_key`, `owner_axis_id`, `note`, `endpoints`. Rollen exakt START/END oder WA/WE/WEZ; jede Rolle verweist auf einen anderen Knoten innerhalb desselben Objekts. Ein Knoten darf in verschiedenen Objekten beliebig wiederverwendet werden. `node_version` ist die erwartete **resultierende** Knotenversion, falls derselbe Batch den Knoten ändert.

Änderung gemeinsamer Knoten verlangt die vollständige Liste aktiver betroffener Objektversionen und alle betroffenen Objekte im selben Batch. Dies verhindert versteckte Seiteneffekte. Betriebsstellenkennung bestehender Datensätze bleibt unveränderlich, damit gefilterte Clients keine Datensätze unbemerkt verlieren. Eine zukünftige betriebsstellenübergreifende Umhängung braucht einen gesonderten Vertrag; keine Umbenennung als stiller Datenverlust.

Antwort: `schema_version`, `entities[]` mit stabilen IDs/Versionen und serverseitig abgeleiteten Positionen/Paths, `warnings[]`; Save zusätzlich `commit_cursor`. Genau dieselben kanonischen Entities werden im Change-Ereignis gespeichert. Read-Antworten ergänzen `needs_review`; dieses Feld, abgeleitete `paths`, `km_status`, `version` und berechnete Positionen nicht ungefiltert in Eingabemodelle zurückschicken.

Idempotenz: gleiche Benutzer-ID + client_change_id + identischer kanonischer Input liefert gespeicherte Antwort, ohne erneut zu schreiben. Abweichender Input unter derselben Kennung wird 409. Alle Netz-/Quellreset-Schreibwege verwenden denselben Transaktionslock. Veraltete Versionen, nicht verfügbare Knoten/Quellen, unvollständige gemeinsame Änderungen und fehlende Vorschau werden abgewiesen.

### Android-Outbox und Rückweg (Implementierung auf Android noch offen)

1. Offline-Entwurf und UUIDs dauerhaft lokal speichern; serverbestätigten Stand und ausstehende lokale Änderung getrennt halten.
2. Aktuelle benötigte Betriebsstelle laden. Beim Erstabgleich zuerst den Feed-Wasserstand merken, anschließend alle Entity-Seiten lesen, danach Änderungen ab diesem Wasserstand nachziehen. Erst dann den lokalen Lesecursor bestätigen; bei Neustart wiederholbar.
3. Outbox-Mutation mit stabiler client_change_id gegen `/preview` prüfen; bei Konflikt lokale Eingabe behalten und Auflösung anbieten. Kein automatisches Ersetzen durch Serverdaten.
4. Exakt geprüften Input + Token nach `/mutations` senden. Unsicheres Ergebnis: dieselbe gespeicherte Mutation wiederverwenden, niemals eine neue ID als vermeintliche Wiederholung erzeugen.
5. Empfangene Change-Ereignisse jeweils als lokale Transaktion anwenden. `next_after` erst nach erfolgreicher lokaler Speicherung bestätigen. Ein Ereignis enthält alle zusammengehörigen Knoten/Objekte, Pagination trennt es nicht.
6. `commit_cursor` eines eigenen Uploads **nicht** als Lesecursor übernehmen: sonst könnten fremde vorherige Änderungen übersprungen werden. Feed pro benötigter Betriebsstelle weiter ab dem bisherigen Lesecursor abholen.
7. Änderungen an Originalquellen/KM über den bestehenden Survey-Catalog-Feed mitverfolgen und betroffene Netzobjekte neu lesen (`needs_review`); der Netzfeed allein ersetzt keine Quell-/KM-Synchronisation. Inaktivierungen ebenfalls übernehmen; lokale unsynchronisierte Entwürfe behalten. Neuinstallation/Reaktivierung über vollständigen Abgleich.
8. Bestehende Android-StreckenObjektSpeicher/WA/WE/WEZ-Daten müssen bewusst auf dieses Schema und die bestehenden KM-Endpunkte abgebildet werden. Der aktuelle Android-HEAD kann dies noch nicht. Keine Geräte-E2E-Abnahme behaupten.

Maschinenlesbare Mutation-Schemata: `projektsteuerung/vertraege/REF-DATA-016_NETWORK_SCHEMA.json`.

## Übernahme auf Synology

GitHub Desktop → `feature/tb-server-1.0` → File Station. Aus `server/` nach `/volume1/docker/tb-helfer-v1/`:

- `tbserver/survey_catalog.py` ersetzen
- `tbserver/survey_workspace.py` ersetzen
- `tbserver/capabilities.py` ersetzen
- `tbserver/admin_purge.py` ersetzen
- `tbserver/survey_network.py` neu
- `templates/v1_admin_survey_overview.html` ersetzen
- `v1_migrations/021_survey_network.sql` neu

Bestehende Dateien aus REF015 einschließlich Migration020 müssen bereits vorhanden sein. Keine Originaldaten, .env oder Storage-/DB-Verzeichnisse überschreiben.

Im API-Container:

```bash
python -m tbserver.migrations up
python -m tbserver.migrations status
```

Erwartet Migrationen21/21, pending[]. API und Worker neu starten, Browser Strg+F5. Keine Reindexierung, kein Zurücksetzen notwendig.

## Prüfung und Grenzen

- 80 vorhandene Python-Regressionsprüfungen120–126; neue produktive Funktions-/Pydantic-/Transaktionsdouble-Prüfungen127; tatsächliches Template im Node119-DOM-/Transport-Doppel. Neue Tests decken Roundtrip, Replay, stale writes, WEZ→WA, umgekehrten Abschnitt, gemeinsame Punktverschiebung, atomare stationsbezogene Feed-Seiten, Deaktivierung, manuelle KM-Beobachtung, Original-/KM-Erhalt und ungültige/private oder geänderte Quellreferenzen ab.
- Kein echter FastAPI/PostgreSQL/PostGIS-/NAS-/Handy-Test in dieser Umgebung. FastAPI-/Psycopg-Installation war mangels erreichbarer Paketquelle nicht möglich. Voriger Chromium-Download blockiert; kein visueller Browsernachweis. Diese Grenzen nicht als bestandenen E2E ausgeben.
- NAS-Praxistest: Gl4→WA→WE→Gl4, umgekehrte Weiche, WEZ→WA der nächsten Weiche, Gl303-Ende→freie Strecke, Bearbeitung gemeinsam genutzter Punkte und Konflikt in zweitem alten Editor. GVP-KM vor/nach Abschnittsänderung vergleichen. Gespeicherte Objekte nach Neuladen wieder öffnen.
- Gezielte Serverprüfung dieses Blocks betrifft Vermessung, Quelle/Reset, Auth-Anbindung, Konflikt-/Receipt-/Cursorpfad. Kein pauschales Urteil über alle Servermodule oder die laufende NAS-Installation.
