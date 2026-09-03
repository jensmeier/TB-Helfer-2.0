[PDF] # TB-Helfer – Bautagebuch (BTB) & PDF – Doku / Änderungsprotokoll

Stand: 1.5.x (Fortlaufende Anpassungen an PDF-Layout + Foto-Logik + BTB-Abschnitte)

---

## 0) Zielbild (was das System am Ende können soll)

1) **PDF optisch sauber**:
- Wetter kompakt, keine überlappenden Linien, gleichmäßige Rahmenabstände.
- Personal + Maschinen immer 2-spaltig.
- Rahmenabstände überall gleich.
- Unterschriftblock (Ort, Datum, Nutzer + Linie).

2) **Wetter**:
- Niederschlag im PDF als: `Schneefall mäßig 1,4 mm/m²`
- Bei 0: nur `trocken` (kein „kein Niederschlag“ Text).
- Tabelle enger und bei langen Niederschlags-Texten: **Auto-kleiner nur in der Nied-Zelle** (keine „…“ Kürzung).

3) **Fotos → BTB-Abschnitte**:
- Foto hat Bildtyp: `ABNAHMEN`, `EINWEISUNG_UNTERWEISUNG`, `BESONDERHEITEN`, `MAENGEL_SCHAEDEN`, `FOTODOKUMENTATION`.
- Im PDF und im BTB-Abschnitt erscheint Foto-Text **im passenden Abschnitt**.
- Text soll **editierbar** sein: Änderung im Abschnitt führt zu `FotoEntity.notiz` Update (Notiz überschreibt Kurztext).

4) **Bildzähler wieder sichtbar**:
- Im PDF: `Bild 1` oder `Bild 3–7`
- In Abschnittszeilen: `(... (Bild 1–3))` bzw. bei nicht zusammenhängend `Bild 1+3`
- Cluster-Regel: gleicher Text wird nur dann zusammengefasst, wenn Zeitabstand <= **5 Minuten**.

5) **Foto-Anhang**:
- Bilder passen in Rahmen (Crop/Clip).
- Lange URLs (Maps-Link) **müssen umbrechen** (sonst läuft es aus dem Rahmen).

6) **Kartenübersicht pro Abschnitt** (geplant):
- Wenn z.B. 30 Abnahmen-Fotos existieren: auf **letzter Seite** von „Abnahmen“ unten eine **Übersichtskarte** mit allen Punkten.
- Ohne API-Key: schematische Karte (Punkte/Boundingbox) ODER (optional) StaticMap mit Key.

---

## 1) Datenmodelle (wichtig)

### 1.1 BTB JSON Modell
**Datei:** `BautagebuchModels.kt`

- `BautagebuchEintrag`
  - Kopf: Baustelle, Datum, Strecke, Zeitfenster
  - Wetter:
    - alt: `wetter: String?` (legacy)
    - neu: `weather: WeatherBlock?` + `weatherSlots: List<WetterSlot>`
  - Firmen: `firmen: List<FirmaBlock>`
  - Abschnitte:
    - 4 „Ausgeführte Arbeiten“: `notizen: String?` (legacy Multiline)
    - 5–8: `SectionBlock` (items mit optionaler Uhrzeit)
  - PDF-Optionen:
    - `includeFotos`, `includeBtbs`, `maxFotosInPdf`, `fotos` (FotoRef)

### 1.2 Foto-DB Modell
**Datei:** `FotoEntity.kt`

- Pflichtfelder: `gpsLat`, `gpsLon`, `firma`, `bildtyp`, `kurztext`
- Optional: `notiz` (überschreibt Kurztext im PDF/BTB)
- `erstelltAmEpochMs` (Zeitfenster + Sortierung)

### 1.3 Neuer Wetterwert: Regenmenge
**Ziel:** mm/m² im PDF anzeigen  
- In `WetterSlot` existiert/kommt zusätzlich: `niederschlagMm: Double?`
- Logik:
  - wenn `mm <= 0`: nur `trocken`
  - sonst: `"<Niederschlagstext> <mm> mm/m²"`

---

## 2) Speicherung / Laden

### 2.1 BTB JSON
**Datei:** `BautagebuchSpeicher.kt`
- Speichert `BautagebuchEintrag` als JSON
- Muss `niederschlagMm` rückwärtskompatibel lesen (optional)

### 2.2 Foto-Auswahl (Exclude-Liste)
**Datei:** `BtbPhotoSelectionStore.kt`
- Default: alle Fotos ausgewählt
- Persistiert: nur **abgewählte IDs** pro Bildtyp (excludedByType)

---

## 3) PDF-Generator (Hauptlogik)

**Datei:** `BautagebuchPdf.kt`

### 3.1 Foto-Set in der Schicht
1) Alle Fotos der Baustelle laden
2) nach Schichtzeit filtern (inkl. über Mitternacht)
3) Excludes aus `BtbPhotoSelectionStore` anwenden
4) Optional: wenn `entry.fotos` gesetzt → nur diese IDs
5) `take(maxFotosInPdf)` anwenden (für Appendix-Nummerierung)

### 3.2 Bildnummern / Bildrange
- `selectedFotosOrdered` = sortiert nach `erstelltAmEpochMs`
- `fotoNum[id] = laufende Nummer (1..n)`
- Für Zeilen in Abschnitt:
  - Numbers sortieren
  - zusammenhängend: `1–3`
  - sonst: `1+3`
  - mehrere Segmente: `1–2+4+6–7`

### 3.3 Foto-Text (BTB) aus Foto
- `btbTextOfFoto(f) = firstNonBlank(f.notiz, f.kurztext)`
- Notiz überschreibt Kurztext

### 3.4 5-Minuten-Cluster (Dein Wunsch)
- Gruppierung zuerst nach `(firma, text)`
- Dann zeitlich sortieren
- Cluster splitten, wenn Gap > **5 Minuten**
- Pro Cluster: eigene Zeile im Abschnitt + eigener Bildrange

### 3.5 Firmenüberschriften & „Allgemein“ entfernen
- Es gibt **keine** „Allgemein“ Überschrift mehr.
- Leere Firma = keine Überschrift, nur Bullets.
- Manuelle Zeilen:
  - können per `Firma:` Prefix zugeordnet werden.
  - oder Firm-only Zeile (z.B. `bbg`) setzt Kontext.

---

## 4) BTB-UI (Abschnitts-Editor)

**Datei:** `BautagebuchFragment.kt`
**Layout:** `dialog_section_editor_full.xml`

### 4.1 Foto-Einträge im Abschnitt (editierbar)
- Im Abschnitts-Dialog werden Foto-Einträge als **graue Pills** angezeigt.
- Klick auf Pill → Dialog „Eintrag ändern“
- Save → schreibt `FotoEntity.notiz` für alle IDs im Cluster.

### 4.2 Manuelle Einträge (A+B Regel)
- Manueller Bereich bleibt möglich (für „besondere Ereignisse“).
- Regel:
  - `bbg: Text ...` → wird im PDF unter Firma bbg einsortiert
  - Ohne Prefix → bleibt ohne Firma
- Hinweis im Dialog:
  - „Tipp: Firma: Text …“

---

## 5) Foto-Anhang im PDF

### 5.1 Bilddarstellung
- Center-Crop + Clip, damit Bild immer in Rahmen passt.

### 5.2 Metadaten & Umbruch
- Text wird innerhalb der Zelle geclippt.
- Wichtig: URLs (Google-Maps Link) sind ein „langes Wort“ → brauchen **Hard-Wrap** (Zeichenweise), sonst laufen sie raus.
- Ergebnis: `GPS:` kann über mehrere Zeilen laufen.

---

## 6) Karten / Maps (geplant)

### 6.1 Was du willst
- Pro Abschnitt (z.B. Abnahmen) auf der **letzten Seite unten** eine Übersicht:
  - alle GPS-Punkte dieses Abschnitts
  - nebeneinander / als Karte sichtbar

### 6.2 Technische Realität
- Eine echte Kartenkachel braucht i.d.R. API (Google Static Maps, Mapbox etc.)
- Ohne API-Key:
  - **Schematische Karte** (Bounding Box + Punkte + Bildnummern) ist möglich und legal/offline.
- Du kannst bei jedem Foto schon jetzt:
  - per Link `https://maps.google.com/?q=lat,lon` direkt in Google Maps sehen, wo das Foto gemacht wurde.

### 6.3 Umsetzungsvorschlag
- Default (ohne Key): **Schematische Übersichtskarte** im PDF
- Optional (mit Key): Static Map hinterlegen

---

## 7) Checkliste: erledigt vs. offen

### ✅ Erledigt (Stand “passt wieder”)
- PDF Box-Layout (Rahmen, Abstände, 2-Spalten Personal/Maschinen)
- Wetter: mm/m² Anzeige + `trocken` Regel
- Auto-Fit für Niederschlagstext (nur Nied-Zelle kleiner, keine „…“)
- Bildnummern im Appendix („Bild 1, Bild 2…“)
- Foto-Anhang: Crop/Clip (Bild passt in Rahmen)

### 🔜 Offen / als nächstes
- 5-Minuten-Cluster + Bildrange (1–3 vs 1+3) überall konsequent in PDF
- „Allgemein“ komplett entfernen (auch in Standard-Sections)
- URL Hard-Wrap (Zeichenweise) im Foto-Anhang
- Kartenübersicht pro Abschnitt unten auf letzter Seite (schematisch / optional API)

---

## 8) Quick-Hinweis für Entwickler
Kotlin Regex:
- `Regex("\\s+")` (nicht `"\\s+"`, sonst unsupported escape)
