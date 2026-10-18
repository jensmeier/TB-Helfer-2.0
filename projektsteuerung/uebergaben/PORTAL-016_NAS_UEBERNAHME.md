# PORTAL-016 – BTB-Eingabe auf der NAS übernehmen

Stand 18.10.2026. Server/Portal; Android bleibt unverändert.
Voraussetzung: bisheriger funktionierender PORTAL-012/015-Stand (Migration 024).

## Genau diese sechs Runtime-Dateien

GitHub Desktop: Branch `feature/tb-server-1.0` aktualisieren. Über File Station in die entsprechenden vorhandenen Unterordner von `/volume1/docker/tb-helfer-v1` übertragen:

| Repository-Datei | NAS-Datei |
| --- | --- |
| `server/tbserver/btb.py` | `tbserver/btb.py` |
| `server/tbserver/btb_times.py` (neu) | `tbserver/btb_times.py` |
| `server/tbserver/btb_weather.py` (neu) | `tbserver/btb_weather.py` |
| `server/tbserver/btb_pdf_android_style.py` | `tbserver/btb_pdf_android_style.py` |
| `server/templates/v1_btb.html` | `templates/v1_btb.html` |
| `server/templates/v1_btb_times.html` (neu) | `templates/v1_btb_times.html` |

Danach im Container Manager den API-Container neu starten, Portal mit Strg+F5 neu laden.
Keine Migration, nichts löschen. Compose, .env, Storage, Originalbilder und Backups müssen für diesen Umbau nicht ersetzt werden. Die beiden neuen Pythonmodule und das neue Template unbedingt zusammen mit den geänderten Dateien übertragen.

Falls die Installation ausschließlich aus einem gebauten Image läuft und die Quellordner nicht eingebunden sind, das bestehende Projekt mit dem aktualisierten Quellstand neu erstellen; ein Neustart allein übernimmt dann keine Dateien ins Image.

## Praxistest

1. Neuen BTB öffnen, Arbeitstag festlegen. Unter Berichtszeit den Vorschlag des letzten BTB desselben Teams/der Baustelle übernehmen oder Beginn/Ende in 15-Minuten-Auswahl einstellen. Bei Nachtschicht das Enddatum auf den Folgetag setzen. Speichern.
2. Bei Personal eine Firma hinzufügen. Sie muss auch bei Maschinen erscheinen; beide Zeitpaare entsprechen der Berichtszeit.
3. Berichtszeit ändern und speichern: verknüpfte Zeiten folgen automatisch. Anschließend bei einer Firma bewusst abweichende Zeiten speichern, Berichtszeit erneut ändern: diese Abweichung bleibt erhalten. „Hier entfernen“ blendet nur den leeren jeweiligen Bereich aus; eine Firma mit vorhandenen Personal-/Gerätezeilen muss zuerst dort geleert werden. Ganze Firma löschen bleibt die getrennte bisherige Aktion mit Bestätigung.
4. Arbeit mit Von/Bis, Firma, Unterbaustelle und Text erstellen; direkt ein oder mehrere Fotos auswählen, speichern. Der Arbeitseintrag bleibt einmal vorhanden, Fotos erscheinen unten im BTB und in der Bilderübersicht mit übernommenen Angaben. Neue Fotos verwenden dieselbe vorhandene Vorbereitung auf maximal 2.000.000 Bytes. Eintrags-KM ist manuell übernommen, kein ausgedachtes GPS. Metadaten lassen sich im bestehenden Fotoeditor nachbearbeiten.
5. Bei Teilfehler Fotoauswahl im offenen Dialog behalten und nochmals Speichern: gespeicherter Eintrag wird nicht nochmals angelegt, bereits erfolgreich übertragene Fotos werden in diesem Dialog übersprungen. Nach Schließen/Neuladen zuerst den sichtbaren Bestand prüfen.
6. Wetter: Berichtszeit zuerst speichern. Ort/PLZ eingeben → Ort suchen (oder Enter) → Ort auswählen. Danach werden drei Schichtzeitpunkte wie im Handy (1/6,1/2,5/6, auf Viertelstunde aufgerundet) vorgeschlagen. Vorhandene Werte nur nach Nachfrage ersetzen. Werte prüfen und Speichern. Bei Fehler sind manuelle Angaben weiterhin möglich.
7. PDF erstellen: Von/Bis muss beim Arbeitseintrag erscheinen. Unveränderte alte Einzelzeiten und bestätigtes Layout bleiben erhalten. Bestehende Abschluss-PDFs werden durch das Update allein nicht neu erzeugt.

## Wetterdienst

Der Server benötigt ausgehendes HTTPS zu `geocoding-api.open-meteo.com`, `api.open-meteo.com` und `archive-api.open-meteo.com`. Die Werte sind Stundenmodellwerte, keine Baustellenmessung. Quelle wird im Portal genannt. Antworten werden 15 Minuten, begrenzt auf 128 Anfragen, im Arbeitsspeicher zwischengespeichert. Anfrage erst nach vorhandener BTB-/Team-/Baustellenberechtigung, keine freie URL-Eingabe. Abruf verändert noch keine BTB-Daten.

Wie im bestehenden Androidpfad wird Open-Meteo genutzt. Der freie Endpunkt ist für Evaluation/nichtkommerzielle Nutzung; für kommerziellen Betrieb ist nach Anbieterangaben eine passende Freigabe/API-Subscription erforderlich. Falls vorhanden, `TB_WEATHER_OPEN_METEO_API_KEY` ausschließlich in der Containerumgebung konfigurieren; dann verwendet der Code die festen `customer-`-Endpunkte. Kein Schlüssel wird in Browserantworten oder Dateien geschrieben. Es wurde keine Subscription gebucht.

Primärquellen zur Anbindung: https://open-meteo.com/en/docs/geocoding-api, https://open-meteo.com/en/docs/historical-weather-api, https://open-meteo.com/en/pricing.

## Teststand und Grenzen

Lokal tatsächlich ausgeführt: `python server/checks/152_portal_btb_input_regression.py` (aus Repositorywurzel) und bestehender Check151. Check152 führt echte Produktionsfunktionen aus: Server-Zeitvererbung, UTC/Berlin/Winterzeit, manuelle Zeiten, additive Endzeit/Altclient-Schutz, Header-Version/Events mit Cursor-Doppel, Wetternachtschicht/Archivzuordnung mit Dienst-Doppel, PDF-Abschnittskonvertierung, JavaScript-Viertelstunden/Datumsteuerung, gemeinsame Firma, Metadaten/Transportvorbereitung, Teilfehler und erneutes Speichern ohne doppelten Entry. Python-AST und beide JavaScript-Skripte syntaktisch geprüft. Einseitige PDF mit 22:00–01:00 tatsächlich erzeugt, Text geprüft, als PNG gerendert und visuell kontrolliert.

Kein echter PostgreSQL-/FastAPI-/Browser-/NAS-E2E-Test dieses neuen Blocks; benötigte lokale FastAPI/Jinja/Psycopg-Pakete und Browserbinary fehlen, Paketinstallation war hier nicht verfügbar. Wetterdienst wurde mit kontrollierten Antworten geprüft, kein echter NAS-Wetterabruf bestätigt. Keine teuren Actions gestartet ([skip ci], CI-COST-001). Allgemeine alte CI-Restpunkte bleiben separat.

Android muss `uhrzeitBis` und Zeitbindungsfelder später in Modell/Anzeige/Save/PDF übernehmen. Der Server-PATCH erhält die Endzeit, wenn ein älterer Client sie auslässt; bewusstes Löschen erfolgt mit null. Das ist noch keine vollständige Android-Roundtrip-Abnahme. Die Reparatur fehlender Unterbaustellen alter Fotos ist ein eigener Auftrag.
