# TB‑Helfer – BTB & PDF Doku (Datenfluss, Mapping, PDF‑Logik)

Stand: 2026‑02‑20 (TB‑Helfer 1.5 Patch)  
Scope: **BTB (Bautagebuch) + PDF Export + Foto‑Anhänge + Foto‑Texte in Abschnitten**

---

## 1) Speicherorte (App‑Files)

### 1.1 Fotos (Original + Thumbnail)
Aus `FotoSpeicher.kt` (extern app-spezifisch):

`/Android/data/<paket>/files/Pictures/TB-Helfer/<Baustelle>/`

Unterordner:
- `Bilder/<yyyy-MM-dd>/...jpg`
- `Abnahmen/<yyyy-MM-dd>/...jpg`
- `Mängel/<yyyy-MM-dd>/...jpg`

Thumbnail neben Original:
`...jpg` → `..._thumb.jpg`

### 1.2 BTB JSON (Bautagebuch)
Aus `BautagebuchSpeicher.kt`:

`/Android/data/<paket>/files/Pictures/TB-Helfer/<Baustelle>/Bautagebuch/<yyyy-MM-dd>/`

- `bautagebuch.json` (aktuell)
- `bautagebuch_original.json` (Snapshot beim **ersten** PDF‑Export → Vergleich für `_geaendert_`)

### 1.3 PDF Ausgabe
Aus `BtbSpeicher.kt`:

`/Android/data/<paket>/files/Pictures/TB-Helfer/<Baustelle>/BTB/<yyyy-MM-dd>/`

Dateiname:
- `<Baustelle>_<schichtKey>.pdf`
- `<Baustelle>_geaendert_<schichtKey>.pdf`

`schichtKey`:
- Tag: `YYYY_MM_DD`
- Nacht: `YYYY_MM_DD-DD` (Folgetag 2‑stellig)

### 1.4 Foto‑Auswahl fürs PDF (Exclude‑Store)
**Wichtig:** Default = alle Fotos sind drin. Persistiert werden nur **abgewählte** IDs.

Datei (intern, private app files):
`<filesDir>/btb_photo_selection/sel_<baustelleId>_<datumIso>_<startKey>_<endKey>.json`

Struktur: `excludedByType` (Map: BildtypName → Set<Long>)

---

## 2) Datenmodelle (BTB)

Quelle: `BautagebuchModels.kt`

### 2.1 Wetter
`WetterSlot(uhrzeit, tempC, bewoelkung, niederschlag, niederschlagMm?)`  
`WeatherBlock(standEpochMs, source, locationName/stationName/lat/lon, slots)`

PDF‑Darstellung (Niederschlag):
- `trocken` (wenn mm <= 0)
- sonst: z.B. `Regen (leicht) 2mm/m²` (einzeilig)

### 2.2 Firmen / Personal / Maschinen
BTB hält Firmenblöcke (Personal/Maschinen + Leistungen).
Firmen‑Sichtbarkeit via Flags:
- `visibleInPersonal`
- `visibleInMaschinen`

### 2.3 Abschnitte 4–8
- Abschnitt 4: `notizen` (Multiline, Bullets)
- Abschnitte 5–8: `SectionBlock(items: List<SectionItem(uhrzeit?, text)>)`

Konvertierung:
- `sectionFromMultiline(raw)` / `sectionToMultiline(sectionBlock)`

### 2.4 FotoEntity (Room) – Quelle für Foto‑Texte in BTB/PDF
Quelle: `FotoEntity.kt`

Wichtige Felder:
- `id`
- `baustelleId`, optional `baustellenKnotenId`
- `bildtyp` (FOTODOKU, ABNAHMEN, EINWEISUNG..., BESONDERHEITEN, MAENGEL...)
- `firma`
- `kurztext` (Pflicht)
- `notiz` (optional; überschreibt `kurztext` für BTB/PDF)
- `erstelltAmEpochMs`
- `gpsLat/gpsLon`, optional `km`

---

## 3) UI: Abschnitt‑Editor + Foto‑Einträge (editierbar)

Quelle: `BautagebuchFragment.kt` + `res/layout/dialog_section_editor_full.xml`

Beim Öffnen eines Abschnitts (4–8):
- Textfeld bleibt wie bisher (Bullets / „eine Zeile pro Eintrag“)
- zusätzlich werden passende **Foto‑Einträge** aus der aktuellen Schicht angezeigt (oben im Dialog)

### 3.1 Auswahl & Nummerierung wie im PDF
- Fotos werden wie im PDF gefiltert:
  - Schicht‑Zeitfenster (inkl. über Mitternacht)
  - Exclude‑Store (`BtbPhotoSelectionStore`)
  - Max‑Limit `maxFotosInPdf`
- Nummerierung folgt der **Anhang‑Reihenfolge**:
  1) Abnahmen
  2) Einweisung/Unterweisung
  3) Besonderheiten
  4) Mängel/Schäden
  5) Fotodokumentation

### 3.2 Zusammenfassen (5‑Min‑Cluster)
Gleicher Text + gleiche Firma wird zusammengefasst **nur**, wenn die Fotos max. **5 Minuten** auseinander liegen.
Sonst entstehen mehrere Zeilen.

### 3.3 Bild‑Label (1–3 vs 1+3)
- zusammenhängend: `Bild 1–3`
- nicht zusammenhängend: `Bild 1+3` (bzw. `1–2+4–5+7`)

### 3.4 Editieren
Tippen auf Foto‑Zeile → „Eintrag ändern“ → schreibt `FotoEntity.notiz` für alle IDs der Gruppe.
(leer = Notiz zurücksetzen → `kurztext` gilt wieder)

---

## 4) PDF Generator

Quelle: `BautagebuchPdf.kt`

### 4.1 Foto‑Filter
- Fotos in Schicht laden (Repo)
- Exclude‑Store anwenden
- MaxFotosLimit nur für Anhang/Nummerierung

### 4.2 Bildzähler in Abschnitten
In Abschnitt 4–8 werden Foto‑Gruppen mit Bild‑Referenz ausgegeben, z.B.:
- `15:44–15:45 Einbau schotter [Bild 1+3]`

Regeln:
- Gruppierung: Firma + Text
- Zeit‑Cluster: max. 5 Minuten
- Bild‑Label: zusammenhängend `1–3`, sonst `1+3`

### 4.3 Foto‑Anhang
- Raster je Einstellung (3x3 / 2x6 / 1x6 / 1x1)
- **GPS‑Link**: `https://maps.google.com/?q=lat,lon&z=19` (≈ 15m Zoom)
- **Zeilenumbruch** im Meta‑Block: lange URLs werden an sinnvollen Stellen (`/ ? & = - _ ,`) umgebrochen.
  - Kein „…“ Kürzen; Zeichnen stoppt, wenn der Zellenraum voll ist.

---

## 5) Call‑Chains (Kurz)

### 5.1 Speichern BTB
`BautagebuchFragment.buildEntry()`  
→ `BautagebuchSpeicher.speichern(...)`  
→ `.../Bautagebuch/<datum>/bautagebuch.json`

### 5.2 PDF erstellen
`BautagebuchFragment.pdfErstellen()`  
→ `BtbPhotoSelectionStore.loadExcluded(...)`  
→ Fotos filtern (Schicht)  
→ `BautagebuchPdf.erstellenPdf(entry)`  
→ `.../BTB/<datum>/...pdf`

### 5.3 Foto‑Text ändern aus Abschnitt‑Editor
`openSectionEditor(..., fotoType=...)`  
→ Liste Foto‑Gruppen  
→ Tap → Dialog „Eintrag ändern“  
→ `FotoRepository.updateNotizMitIds(ids, notiz)`  
→ PDF/BTB übernimmt Notiz automatisch
