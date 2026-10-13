# PORTAL-012 – Server/Portal übernehmen

Quellstand: aktueller geprüfter `feature/tb-server-1.0`. NAS-Installation und Praxisabnahme bleiben bis zur Übernahme offen.

## Acht zusammengehörige Runtime-Dateien

Zielbasis: `/volume1/docker/tb-helfer-v1/`.

| Repositorydatei | Ziel relativ zur NAS-Zielbasis |
|---|---|
| `server/v1_migrations/024_gallery_photo_search.sql` | `v1_migrations/024_gallery_photo_search.sql` (neu) |
| `server/tbserver/media_gallery.py` | `tbserver/media_gallery.py` |
| `server/tbserver/files.py` | `tbserver/files.py` |
| `server/templates/v1_photo_upload.html` | `templates/v1_photo_upload.html` (neu) |
| `server/templates/v1_base.html` | `templates/v1_base.html` |
| `server/templates/v1_btb.html` | `templates/v1_btb.html` |
| `server/templates/v1_worksite.html` | `templates/v1_worksite.html` |
| `server/templates/v1_portal_pictures.html` | `templates/v1_portal_pictures.html` |

Die neue Include-Vorlage gehört zwingend dazu; sonst können auch andere Portal-/Adminseiten beim Rendern scheitern. Die Suchmigration gehört zwingend zur neuen API. Nicht nur die Galerievorlage ersetzen.

## Ablauf

1. Über GitHub Desktop den Serverbranch aktualisieren. Die vorhandenen Softwaredateien als Rückweg auf dem PC sichern.
2. Projekt im Container Manager stoppen. Alle acht Dateien über File Station in die vorhandenen Verzeichnisse übernehmen.
3. Projekt über Container Manager neu erstellen/starten, sodass der bestehende `migrate`-Dienst Migration024 ausführt. Eine reine API-Neustartaktion führt die neue Migration nicht automatisch aus.
4. Erst weiterprüfen, wenn Systemstatus **Migrationen24/24, keine ausstehend** zeigt. API/Portal wieder erreichbar prüfen, dann Browser mit Strg+F5 neu laden.
5. Unter Bilderübersicht muss **PORTAL-012** stehen.

Keine Compose-/`.env`-/Daten-/Storage-/Backupdateien ersetzen oder löschen. Keine Neuaktivierung von Geräten. Migration024 ergänzt ausschließlich zwei lesende Funktionen; sie verändert keine gespeicherten Bildmetadaten oder Originaldateien.

## Praxisprüfung

- Suche `strail defekt`: beide Wörter müssen in der wirksamen Beschriftung bzw. anderen Angaben vorkommen, Reihenfolge und Groß-/Kleinschreibung sind egal. Bereits gekürzte Texte lassen sich nicht allein durch die Suche wiederherstellen.
- `13.000` und `13,000` finden13.000–13.999. `13.200`, `13,200` und `KM 13.200` finden13.100–13.299. Der erkannte Bereich erscheint bei Treffern. Andere nichtganzzahlige Eingaben verwenden100m davor bis99m danach. Werte werden auf angezeigte Metergenauigkeit gerundet.
- Vorhandene BTB-Metadatenänderungen/geleerte Felder, Ortsnamen, Dateinamen und Notizen berücksichtigen. Keine fremden Team-/Bereichsdaten als Treffer anzeigen.
- Kartenabstände sind kompakter, Beschriftungen können umbrechen, maximal60 Bilder je Seite. GPS öffnet Google Maps.
- Ohne gespeicherte Unterbaustellenzuordnung steht ehrlich **Unterbaustelle nicht zugeordnet**; ein vorhandener, aber nicht auflösbarer Schlüssel wird gesondert angezeigt. Das ersetzt den unzutreffenden pauschalen Fallback Hauptbaustelle.
- Neues Foto im BTB und bei Mangel/Abnahme auswählen: Browser erzeugt vor dem Upload JPEG, längste Kante maximal2560px, Qualität90→75 und nötigenfalls kleinere Kante, niemals hochskalieren, maximal**2000000Bytes**. Kleinere Bilder bleiben kleiner, das ausgewählte PC-Original bleibt unverändert. Nicht unterstützte Bildformate werden verständlich abgewiesen, kein unkomprimierter Ausweichupload.
- Server prüft Fotoarten/MIME/Dateiendung bei Uploadbeginn, kumulierte Bytes bei Chunks und tatsächliche Datei beim Abschluss. HTTP413 PHOTO_TOO_LARGE erklärt die2-MB-Grenze. Größere PDF-/Dokumentuploads bleiben möglich. Alte Serverbilder über2MB bleiben lesbar und unverändert.
- Android empfängt dieselbe Servergrenze; alte Clients mit größeren neuen Syncbildern müssen vor dem Upload verkleinern. Android-Automatik dafür ist separater Folgeauftrag; kein aktualisiertes APK in diesem Block.

## Foto27 – vorbereitete Korrektur zur Prüfung, noch nicht angewendet

Quelle: Original-Bilderliste aus Nutzer-ZIP24.09., passende Zeile16:04/GPS47.596990,10.253655. Abgleich von BTB/Fotodatei-ID vor dem Speichern erforderlich; bloße Anzeige Foto27 ist keine dauerhafte Identität.

| Feld | Belegter Zielwert |
|---|---|
| Beschriftung | Fertigstellung Bü 78,571 |
| Firma | Peter Schauer Tiefbau |
| Unterbaustelle | vorhandenen Knoten BÜ 78,571 auswählen |
| Aufnahmekilometer |78,577 unverändert |

Im bestehenden BTB-Fotoeditor sind Text/Firma/Unterbaustelle bearbeitbar. Der Quellstand überschreibt diesen Bestand nicht automatisch. Falls der Knoten fehlt oder mehrere gleichnamige Knoten bestehen, erst Identität/Zuordnung prüfen. Kein Ort aus GPS-Nähe oder Bildtitel allein erfinden. Vollständige alte Grundmetadaten benötigen anschließend einen kontrollierten Reparatur-/Android-Nachlieferpfad; normales erneutes Synchronisieren garantiert das nicht.

## Lesende Versionskontrolle

Im API-Container-Terminal:

```sh
python -c "from tbserver.media_gallery import GALLERY_VERSION; from tbserver.files import PHOTO_MAX_BYTES; print('Galerie:',GALLERY_VERSION,'Bildgrenze:',PHOTO_MAX_BYTES)"
```

Erwartet `PORTAL-012` und `2000000`. Migrationstatus zusätzlich im Systemstatus prüfen.

Rückweg: Projekt stoppen, gesicherte Softwarevorlagen zusammen zurückspielen und Projekt starten. Die hinzugefügten Suchfunktionen dürfen in der Datenbank bleiben; keine Migration rückwärts löschen und keine Bilddaten zurücksetzen.

## Prüfstand

Getesteter Stand `322f7b14094c0752eef07bd3bcfb659ef36c1d03`. [Portalworkflow38417438535](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/38417438535), Job115365514637: SUCCESS mit tatsächlich ausgeführten PostgreSQL-/Jinja-/Chromiumschritten.10.000 Datensätze/167Seiten, Text-/KM-/Rechtetests und reale Uploadgrenzen einschließlich vollständigem2-MB-JPEG-Abschluss PASS; Browserverkleinerung und Desktop/Mobil visuell geprüft. Snapshot-/Rechte-/255Client-Gate ebenfalls PASS. Alte allgemeine Foundation-/Androidfehler separat; kein Synology-E2E behauptet.
