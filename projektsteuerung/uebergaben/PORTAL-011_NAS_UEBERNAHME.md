# PORTAL-011 – NAS-Übernahme

Serverbranch: `feature/tb-server-1.0`. Zusammengehörigen aktuellen GitHub-Stand über GitHub Desktop holen. Quellstand implementiert; NAS-Installation und Praxisabnahme separat offen.

## Genau diese zwei Dateien ersetzen

| Repositorydatei | Ziel auf der NAS |
|---|---|
| `server/tbserver/media_gallery.py` | `/volume1/docker/tb-helfer-v1/tbserver/media_gallery.py` |
| `server/templates/v1_portal_pictures.html` | `/volume1/docker/tb-helfer-v1/templates/v1_portal_pictures.html` |

Diese Dateien enthalten auch PORTAL-010. Voraussetzung ist der vorhandene PORTAL-009-Stand mit `/media/page` (bereits nutzerseitig bestätigt).

1. Über GitHub Desktop den Serverbranch aktualisieren.
2. API-Container im Synology Container Manager stoppen.
3. Beide vorhandenen Dateien als Rückweg auf dem PC sichern und anschließend über File Station durch die beiden neuen Dateien ersetzen.
4. API-Container wieder starten. Bei der bestehenden Ordnerbindung ist kein neues Image und keine Projekterstellung erforderlich.
5. Bilderübersicht öffnen und mit `Strg+F5` neu laden. Unter der Überschrift muss `PORTAL-011` stehen.

Keine weitere Datei hochladen; nichts löschen. Keine Migration, Migrationstand bleibt 23/23. `.env`, Compose, Datenbank, Storage und Backups unverändert lassen. Android bleibt eigener Folgeblock.

## Nur lesende Ladepfadprüfung

Im Terminal des gestarteten API-Containers:

```sh
python -c "import hashlib; from pathlib import Path; import tbserver.media_gallery as g; print('API:', g.GALLERY_VERSION, g.__file__); p=Path(g.__file__); print('API SHA256:', hashlib.sha256(p.read_bytes()).hexdigest()); t=p.parent.parent/'templates'/'v1_portal_pictures.html'; print('Template:', t, 'PORTAL-011' in t.read_text()); print('Template SHA256:', hashlib.sha256(t.read_bytes()).hexdigest())"
```

Erwartet: API `PORTAL-011`, Template `True` und folgende Prüfsummen:

- API: `a04b68aa66cc3aeaee67ddbdbf3b62c125fff012399bec5cb6cd5adeef062c51`
- Template: `04f555eaf20a71902c908c7ed933b29a290a9f72c16b53af003927a682d46aaa`

Bei anderer Prüfsumme tatsächlichen Pfad/Dateiübernahme prüfen. Bei unterschiedlichen API-/Templateversionen zeigt die neue Galerie einen Aktualisierungshinweis. Ein altes Template kann diesen Hinweis noch nicht anzeigen; deshalb zusätzlich sichtbare Kennung und Containerprüfung verwenden.

## Praxisprüfung

- Dasselbe Foto im BTB und in Bilderübersicht vergleichen: Beschriftung, BTB-Fotonummer und Datum, gespeicherte Dateigröße, Aufnahmezeit, Kilometer, Firma, Ort/Unterbaustelle/Objekt, Indikator und GPS-Link.
- Auch ein bereits im BTB bearbeitetes Foto vergleichen; ausdrücklich geleerte Felder dürfen nicht mit alten Dateiangaben wieder erscheinen.
- Bei mehreren BTB-Verknüpfungen gilt die Nummer im von der Galerie gewählten, berechtigten BTB. Über dessen gefilterte Galerie ist die Zuordnung eindeutig. Mangel-/Abnahmebilder erhalten keine erfundene BTB-Fotonummer.
- Weiterblättern: weiterhin maximal 60 Bilder pro Seite. GPS öffnet die Karte; Notizen und Dateiname stehen in aufklappbaren Details.
- Bei fehlenden Daten wird nichts rekonstruiert oder erfunden. Dateigröße bezeichnet die auf dem Server gespeicherte Datei, nicht das Handyoriginal.

Rückweg: API stoppen, beide gesicherten Dateien zusammen zurückkopieren, API starten und Browser neu laden. Es werden keine Daten umgeschrieben.

## Ausgeführte Prüfung

Getesteter Stand `b2dd5f80968784af9a55cc907745196fe0953e2b`. [Portal-CI 38391789511](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/38391789511), Job115277108244: SUCCESS mit ausgeführten PostgreSQL-/Jinja-/Chromiumschritten. 10.000 Metadatensätze, 167 Seiten, Nummernparität und Metadatenänderung/clear/Rechte; Desktop1440 und Mobil390 einschließlich Versionsunterschied geprüft. Screenshots visuell geprüft. Snapshot-/Rechte-/255Client-Gate ebenfalls SUCCESS; alte Foundation-/Android-Restfehler bleiben separat. Das ist keine Synology-Abnahme.
