# PORTAL-015 – NAS-Übernahme

Neue KM-Suche: `13.000` findet **12.000–14.000 einschließlich beider Grenzen**. `13.200` findet weiterhin **13.100–13.299**. Punkt und Komma gleich.

Die Unterbaustellenliste zeigt Eltern vor ihren Kindern. Beispiel: Gleisquerung → SchachtV2 → Anbindung an Bestandsschacht. Gespeicherte Zuordnungen werden nicht geändert.

## Genau zwei Dateien ersetzen

| Repositorydatei | NAS-Ziel |
|---|---|
| `server/tbserver/media_gallery.py` | `/volume1/docker/tb-helfer-v1/tbserver/media_gallery.py` |
| `server/templates/v1_worksite.html` | `/volume1/docker/tb-helfer-v1/templates/v1_worksite.html` |

Über GitHub Desktop den Serverbranch aktualisieren, bisherige zwei Dateien auf dem PC sichern, API im Container Manager stoppen, beide Dateien per File Station ersetzen, API starten und Browser mit Strg+F5 neu laden. Keine neue Migration und keine Projekterstellung erforderlich; PORTAL-012 mit seiner bestehenden Migration wird vorausgesetzt. Nichts löschen; .env, Compose, Datenbank, Storage und Backups unverändert lassen.

## Prüfung nach Übernahme

- `13.000` bzw. `13,000`: Kilometerbereich12.000–14.000; beide Grenzwerte gehören dazu. Werte11.999/14.001 gehören nicht dazu.
- `13.200`: weiter13.100–13.299. Ein Foto13.300 gehört nicht dazu.
- Bestehenden Baum neu laden: Anbindung steht unmittelbar im Teilbaum von SchachtV2. Falls sie danach tatsächlich unter einem anderen Knoten steht, konkrete parent_id/Elternidentität prüfen; nicht blind neu anlegen oder umhängen.

## Nachweis und Grenzen

Gezielter lokaler Regressionstest151 mit Produktionsfunktionen PASS: KM-Grenzen, Rendererreihenfolge, keine Datenmutation, Orphans/Zyklen,5000 Ebenen; Python-/JS-Syntax PASS. Kein echter Browser-/PostgreSQL-/NAS-E2E in diesem Block. Aus Actions-Budgetgründen bewusst kein großer CI-Lauf; vorhandener Integrationstest150 für die neuen Grenzen aktualisiert.

Rückweg: API stoppen, die beiden gesicherten Softwaredateien zurückkopieren, API starten, Strg+F5. Keine Datenrücksetzung nötig.
