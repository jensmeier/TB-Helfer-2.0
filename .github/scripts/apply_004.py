from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one occurrence, got {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Version 2.0 (004)
# ---------------------------------------------------------------------------
replace_once(
    "app/build.gradle.kts",
    "        // TB-Helfer 2.0: 200003 -> sichtbare Teststand-Nummer 003.\n"
    "        // Jeder neue auf dem Handy testbare Stand erhöht diesen Wert um 1.\n"
    "        versionCode = 200003\n",
    "        // TB-Helfer 2.0: 200004 -> sichtbare Teststand-Nummer 004.\n"
    "        // Jeder neue auf dem Handy testbare Stand erhöht diesen Wert um 1.\n"
    "        versionCode = 200004\n",
)

history = read("VERSION_HISTORY.md")
anchor = "- Git bleibt die technische Detailhistorie; diese Datei beschreibt die fachlichen Unterschiede zwischen den Testständen.\n\n"
if anchor not in history:
    raise SystemExit("VERSION_HISTORY.md: rule anchor missing")
block004 = """## 2.0 (004)\n\nAndroid versionCode: `200004`\n\nPersistente Weichen-/DKW-Darstellung und Baustellen-Verknüpfung:\n\n- Vollständig gespeicherte Weichen werden in der grafischen Übersicht als türkis gefülltes Dreieck aus WA/WE/WEZ dargestellt.\n- Vollständig gespeicherte DKW/Kreuzungen werden als türkis gefülltes Viereck aus A/B/C/D dargestellt.\n- Die Eckpunkte werden mit ihrer Rolle beschriftet; Blau bleibt ausschließlich temporäre Auswahl/Markierung.\n- Manuell gespeicherte Weichen/DKW werden bei einer neuen Baustelle über Betriebsstelle + Bezeichnung als vorhandene Streckendaten erkannt und als Vorschlag angeboten.\n- Einzelnes Objekt-Löschen löscht weiterhin nur genau diese Weiche/DKW.\n- `Vermessungsdaten löschen` entfernt jetzt zusätzlich alle manuell gespeicherten Weichen/DKW und damit den kompletten Vermessungsstand.\n- Keine Room-Migration.\n\nStatus: in Entwicklung / CI-Prüfung vor Handytest.\n\n"""
history = history.replace(anchor, anchor + block004, 1)
history = history.replace(
    "Status: in Entwicklung / CI-Prüfung vor Handytest.\n\n## 2.0 (002)",
    "Status: CI vollständig grün; Handytest zeigte fehlende dauerhafte Weichen-/DKW-Flächen und fehlende Baustellen-Erkennung.\n\n## 2.0 (002)",
    1,
)
write("VERSION_HISTORY.md", history)


# ---------------------------------------------------------------------------
# StreckenObjektSpeicher: Betriebsstellen-Liste + kompletter Reset
# ---------------------------------------------------------------------------
path = "app/src/main/java/de/tbhelfer/streckenobjekte/StreckenObjektSpeicher.kt"
text = read(path)
old = """    fun hatVerwendbareKilometrierung(\n        context: Context,\n        betriebsstelle: String?,\n        bezeichnung: String?,\n        typ: Typ\n    ): Boolean = findePassend(context, betriebsstelle, bezeichnung, typ)?.traHatKm == true\n"""
new = """    fun fuerBetriebsstelle(\n        context: Context,\n        betriebsstelle: String?,\n        typ: Typ? = null\n    ): List<Objekt> {\n        val b = normalizeBetriebsstelle(betriebsstelle)\n        return alle(context)\n            .filter { typ == null || it.typ == typ }\n            .filter { b.isBlank() || normalizeBetriebsstelle(it.betriebsstelle) == b }\n    }\n\n    fun hatVerwendbareKilometrierung(\n        context: Context,\n        betriebsstelle: String?,\n        bezeichnung: String?,\n        typ: Typ\n    ): Boolean = findePassend(context, betriebsstelle, bezeichnung, typ)?.traHatKm == true\n"""
if text.count(old) != 1:
    raise SystemExit("StreckenObjektSpeicher: hatVerwendbareKilometrierung anchor mismatch")
text = text.replace(old, new, 1)
old = """    fun loeschen(context: Context, id: String) = synchronized(lock) {\n        schreibe(context, alle(context).filterNot { it.id == id })\n    }\n"""
new = """    fun loeschen(context: Context, id: String) = synchronized(lock) {\n        schreibe(context, alle(context).filterNot { it.id == id })\n    }\n\n    /** Kompletter Vermessungsreset: alle manuell gespeicherten Weichen/DKW entfernen. */\n    fun leeren(context: Context) = synchronized(lock) {\n        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n            .edit()\n            .remove(KEY_JSON)\n            .apply()\n    }\n"""
if text.count(old) != 1:
    raise SystemExit("StreckenObjektSpeicher: loeschen anchor mismatch")
text = text.replace(old, new, 1)
write(path, text)


# ---------------------------------------------------------------------------
# BaustelleNeuFragment: manuelle Weichen/DKW anbieten und als Daten erkennen
# ---------------------------------------------------------------------------
path = "app/src/main/java/de/tbhelfer/ui/baustelle/BaustelleNeuFragment.kt"
text = read(path)
old = "import de.tbhelfer.streckenobjekte.BaustellenObjektLogik\n"
new = old + "import de.tbhelfer.streckenobjekte.StreckenObjektSpeicher\n"
if text.count(old) != 1:
    raise SystemExit("BaustelleNeuFragment: import anchor mismatch")
text = text.replace(old, new, 1)

old = """        edtGleisWeicheNr.setOnFocusChangeListener { _, hasFocus ->\n            if (hasFocus && rgGleisWeiche.checkedRadioButtonId == R.id.rbGleis) {\n                aktualisiereGleisVorschlaege(dropdownOeffnen = true)\n            }\n        }\n        edtGleisWeicheNr.setOnClickListener {\n            if (rgGleisWeiche.checkedRadioButtonId == R.id.rbGleis) aktualisiereGleisVorschlaege(dropdownOeffnen = true)\n        }\n"""
new = """        edtGleisWeicheNr.setOnFocusChangeListener { _, hasFocus ->\n            if (hasFocus) aktualisiereGleisVorschlaege(dropdownOeffnen = true)\n        }\n        edtGleisWeicheNr.setOnClickListener {\n            aktualisiereGleisVorschlaege(dropdownOeffnen = true)\n        }\n"""
if text.count(old) != 1:
    raise SystemExit("BaustelleNeuFragment: focus/click anchor mismatch")
text = text.replace(old, new, 1)

pattern = re.compile(
    r"    private fun aktualisiereGleisVorschlaege\(dropdownOeffnen: Boolean = false\) \{.*?\n    \}\n\n    private suspend fun ladeGleisVorschlaegeFuerBetriebsstelle",
    re.S,
)
replacement = """    private fun aktualisiereGleisVorschlaege(dropdownOeffnen: Boolean = false) {\n        gleisVorschlagJob?.cancel()\n        if (!::edtGleisWeicheNr.isInitialized) return\n        val raw = edtBahnhof.text?.toString().orEmpty().trim()\n        if (raw.isBlank()) return\n        val code = extrahiereBahnhofCode(raw, findeBahnhofVorschlag(raw)).ifBlank { raw.uppercase(Locale.GERMANY) }\n        val modus = bahnhofObjektModus()\n        gleisVorschlagJob = viewLifecycleOwner.lifecycleScope.launch {\n            val vorschlaege = withContext(Dispatchers.IO) {\n                when (modus) {\n                    \"GLEIS\" -> ladeGleisVorschlaegeFuerBetriebsstelle(code)\n                    \"WEICHE\" -> StreckenObjektSpeicher\n                        .fuerBetriebsstelle(requireContext().applicationContext, code, StreckenObjektSpeicher.Typ.WEICHE)\n                        .map { it.bezeichnung.trim() }\n                        .filter { it.isNotBlank() }\n                        .distinct()\n                        .sortedWith(compareBy({ BaustellenObjektLogik.normalisierteObjektKennung(it).toIntOrNull() ?: Int.MAX_VALUE }, { it }))\n                    \"DKW_KREUZUNG\" -> StreckenObjektSpeicher\n                        .fuerBetriebsstelle(requireContext().applicationContext, code, StreckenObjektSpeicher.Typ.DKW_KREUZUNG)\n                        .map { it.bezeichnung.trim() }\n                        .filter { it.isNotBlank() }\n                        .distinct()\n                        .sorted()\n                    else -> emptyList()\n                }\n            }\n            if (!isAdded || !::edtGleisWeicheNr.isInitialized) return@launch\n            edtGleisWeicheNr.setAdapter(\n                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vorschlaege)\n            )\n            if (dropdownOeffnen && edtGleisWeicheNr.hasFocus() && vorschlaege.isNotEmpty()) {\n                edtGleisWeicheNr.post { edtGleisWeicheNr.showDropDown() }\n            }\n        }\n    }\n\n    private suspend fun ladeGleisVorschlaegeFuerBetriebsstelle"""
text2, n = pattern.subn(replacement, text, count=1)
if n != 1:
    raise SystemExit(f"BaustelleNeuFragment: suggestion function replacement count={n}")
text = text2

old = """        val objektModus = bahnhofObjektModus()\n        if (objektModus == \"DKW_KREUZUNG\") {\n"""
new = """        val objektModus = bahnhofObjektModus()\n        if (objektModus == \"WEICHE\" || objektModus == \"DKW_KREUZUNG\") {\n            val speicherTyp = if (objektModus == \"DKW_KREUZUNG\") {\n                StreckenObjektSpeicher.Typ.DKW_KREUZUNG\n            } else {\n                StreckenObjektSpeicher.Typ.WEICHE\n            }\n            val manuell = StreckenObjektSpeicher.findePassend(\n                requireContext(), zielCode, nrRaw, speicherTyp\n            )\n            if (manuell != null) {\n                val rollen = if (speicherTyp == StreckenObjektSpeicher.Typ.WEICHE) {\n                    listOf(\"WA\", \"WE\", \"WEZ\")\n                } else {\n                    listOf(\"A\", \"B\", \"C\", \"D\")\n                }\n                val byRole = manuell.punkte.associateBy { it.rolle.uppercase(Locale.GERMANY) }\n                val vollstaendig = rollen.all { rolle ->\n                    val p = byRole[rolle]\n                    p != null && p.achseId != null && p.station != null\n                }\n                val typText = if (speicherTyp == StreckenObjektSpeicher.Typ.WEICHE) \"Weiche\" else \"DKW/Kreuzung\"\n                val rollenText = rollen.filter { it in byRole.keys }.joinToString(\"/\")\n                return DatenStatus(\n                    vollstaendig,\n                    if (vollstaendig) {\n                        \"✓ $zielCode: $typText ${manuell.bezeichnung} manuell vorhanden · $rollenText\"\n                    } else {\n                        \"⚠ $zielCode: $typText ${manuell.bezeichnung} gespeichert, aber Punkte unvollständig · $rollenText\"\n                    }\n                )\n            }\n        }\n        if (objektModus == \"DKW_KREUZUNG\") {\n"""
if text.count(old) != 1:
    raise SystemExit("BaustelleNeuFragment: objektModus anchor mismatch")
text = text.replace(old, new, 1)
write(path, text)


# ---------------------------------------------------------------------------
# TrassenGrafikView: türkis gefüllte gespeicherte Objektflächen
# ---------------------------------------------------------------------------
path = "app/src/main/java/de/tbhelfer/ui/importexport/TrassenGrafikView.kt"
text = read(path)
old = "    private var objektMarker: List<TrassenGrafikMarker> = emptyList()\n"
new = old + "    private var objektFlaechen: List<TrassenGrafikObjektFlaeche> = emptyList()\n"
if text.count(old) != 1:
    raise SystemExit("TrassenGrafikView: objektMarker field anchor mismatch")
text = text.replace(old, new, 1)

old = "    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }\n"
new = old + """    private val objektFlaechePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }\n    private val objektFlaecheRandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {\n        style = Paint.Style.STROKE\n        strokeJoin = Paint.Join.ROUND\n        strokeCap = Paint.Cap.ROUND\n    }\n    private val objektFlaechePunktPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }\n"""
if text.count(old) != 1:
    raise SystemExit("TrassenGrafikView: paint anchor mismatch")
text = text.replace(old, new, 1)

old = """    fun setObjektMarker(marker: List<TrassenGrafikMarker>) {\n        objektMarker = marker\n        invalidate()\n    }\n"""
new = old + """\n    /** Dauerhaft gespeicherte Weichen/DKW. Türkis ist Objektgeometrie; Blau bleibt Auswahl. */\n    fun setObjektFlaechen(flaechen: List<TrassenGrafikObjektFlaeche>) {\n        objektFlaechen = flaechen\n        invalidate()\n    }\n"""
if text.count(old) != 1:
    raise SystemExit("TrassenGrafikView: setObjektMarker anchor mismatch")
text = text.replace(old, new, 1)

old = """        zeichneAchsendenUndKnoten(canvas, b, isDark)\n        zeichneFestpunkte(canvas, b, isDark)\n        zeichneObjektMarker(canvas, b, isDark)\n"""
new = """        zeichneAchsendenUndKnoten(canvas, b, isDark)\n        zeichneObjektFlaechen(canvas, b, isDark)\n        zeichneFestpunkte(canvas, b, isDark)\n        zeichneObjektMarker(canvas, b, isDark)\n"""
if text.count(old) != 1:
    raise SystemExit("TrassenGrafikView: onDraw anchor mismatch")
text = text.replace(old, new, 1)

anchor = "    private fun zeichneObjektMarker(canvas: Canvas, b: RectF, isDark: Boolean) {\n"
if text.count(anchor) != 1:
    raise SystemExit("TrassenGrafikView: zeichneObjektMarker anchor mismatch")
func = """    private fun zeichneObjektFlaechen(canvas: Canvas, b: RectF, isDark: Boolean) {\n        if (objektFlaechen.isEmpty()) return\n        val tuerkis = Color.rgb(0, 188, 180)\n        objektFlaechePaint.color = Color.argb(if (isDark) 105 else 90, 0, 188, 180)\n        objektFlaecheRandPaint.color = tuerkis\n        objektFlaecheRandPaint.strokeWidth = dpF(3f)\n        objektFlaechePunktPaint.color = tuerkis\n\n        objektFlaechen.forEach { flaeche ->\n            if (flaeche.punkte.size < 3) return@forEach\n            val path = Path()\n            flaeche.punkte.forEachIndexed { index, punkt ->\n                val x = sx(punkt.x, b)\n                val y = sy(punkt.y, b)\n                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)\n            }\n            path.close()\n            canvas.drawPath(path, objektFlaechePaint)\n            canvas.drawPath(path, objektFlaecheRandPaint)\n\n            flaeche.punkte.forEach { punkt ->\n                val x = sx(punkt.x, b)\n                val y = sy(punkt.y, b)\n                canvas.drawCircle(x, y, dpF(5f), objektFlaechePunktPaint)\n                textPaint.textAlign = Paint.Align.LEFT\n                textPaint.textSize = sp(11f)\n                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD\n                textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)\n                canvas.drawText(punkt.rolle, x + dpF(7f), y - dpF(7f), textPaint)\n            }\n        }\n    }\n\n"""
text = text.replace(anchor, func + anchor, 1)

marker_anchor = "data class TrassenGrafikMarker(\n"
if text.count(marker_anchor) != 1:
    raise SystemExit("TrassenGrafikView: data class marker anchor mismatch")
models = """data class TrassenGrafikObjektFlaeche(\n    val id: String,\n    val label: String,\n    val punkte: List<TrassenGrafikObjektPunkt>\n)\n\ndata class TrassenGrafikObjektPunkt(\n    val rolle: String,\n    val x: Float,\n    val y: Float\n)\n\n"""
text = text.replace(marker_anchor, models + marker_anchor, 1)
write(path, text)


# ---------------------------------------------------------------------------
# ImportExportFragment: kompletter Reset + Flächen aus gespeicherten Objekten
# ---------------------------------------------------------------------------
path = "app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt"
text = read(path)
old = "                        val dbErgebnis = ServiceLocator.trassenRepository.streckendatenKomplettZuruecksetzen()\n"
new = old + "                        StreckenObjektSpeicher.leeren(appContext)\n"
if text.count(old) != 1:
    raise SystemExit(f"ImportExportFragment: reset anchor count={text.count(old)}")
text = text.replace(old, new, 1)

anchor = "    private fun TextView.textStyleBold() {\n"
if text.count(anchor) != 1:
    raise SystemExit("ImportExportFragment: textStyleBold anchor mismatch")
helper = """    private fun streckenObjektFlaechen(\n        objekte: List<StreckenObjektSpeicher.Objekt>,\n        achsen: List<TrassenGrafikAchse>\n    ): List<TrassenGrafikObjektFlaeche> {\n        val achsenById = achsen.groupBy { it.achseId }\n\n        fun punktAufAchse(achse: TrassenGrafikAchse, station: Double): Pair<Float, Float>? {\n            val ps = achse.punkte.sortedBy { it.station }\n            if (ps.isEmpty()) return null\n            if (ps.size == 1) return ps.first().x to ps.first().y\n            if (station <= ps.first().station) return ps.first().x to ps.first().y\n            if (station >= ps.last().station) return ps.last().x to ps.last().y\n            for (i in 0 until ps.lastIndex) {\n                val a = ps[i]\n                val z = ps[i + 1]\n                if (station + 1e-9 < a.station || station - 1e-9 > z.station) continue\n                val ds = z.station - a.station\n                val t = if (kotlin.math.abs(ds) < 1e-9) 0.0 else ((station - a.station) / ds).coerceIn(0.0, 1.0)\n                return (a.x + (z.x - a.x) * t.toFloat()) to\n                    (a.y + (z.y - a.y) * t.toFloat())\n            }\n            return ps.minByOrNull { kotlin.math.abs(it.station - station) }?.let { it.x to it.y }\n        }\n\n        fun koordinaten(p: StreckenObjektSpeicher.Punkt): Pair<Float, Float>? {\n            val vx = p.vermarkungX\n            val vy = p.vermarkungY\n            if (vx != null && vy != null && vx.isFinite() && vy.isFinite()) {\n                return vx.toFloat() to vy.toFloat()\n            }\n            val achseId = p.achseId ?: return null\n            val station = p.station ?: return null\n            val kandidaten = achsenById[achseId].orEmpty()\n            return kandidaten.asSequence().mapNotNull { punktAufAchse(it, station) }.firstOrNull()\n        }\n\n        return objekte.mapNotNull { objekt ->\n            val rollen = if (objekt.typ == StreckenObjektSpeicher.Typ.WEICHE) {\n                listOf(\"WA\", \"WE\", \"WEZ\")\n            } else {\n                listOf(\"A\", \"B\", \"C\", \"D\")\n            }\n            val byRole = objekt.punkte.associateBy { it.rolle.uppercase(Locale.GERMANY) }\n            val roh = rollen.mapNotNull { rolle ->\n                val p = byRole[rolle] ?: return@mapNotNull null\n                val xy = koordinaten(p) ?: return@mapNotNull null\n                TrassenGrafikObjektPunkt(rolle = rolle, x = xy.first, y = xy.second)\n            }\n            if (roh.size != rollen.size) return@mapNotNull null\n\n            val cx = roh.map { it.x.toDouble() }.average()\n            val cy = roh.map { it.y.toDouble() }.average()\n            val geordnet = roh.sortedBy { p ->\n                kotlin.math.atan2(p.y.toDouble() - cy, p.x.toDouble() - cx)\n            }\n            TrassenGrafikObjektFlaeche(\n                id = objekt.id,\n                label = objekt.bezeichnung,\n                punkte = geordnet\n            )\n        }\n    }\n\n"""
text = text.replace(anchor, helper + anchor, 1)

old = """                val context = requireContext()\n                val anzeigeAchsen = grafikAchsen.distinctBy { it.achseId }\n                val alleAchseIds = anzeigeAchsen.map { it.achseId }.toMutableSet()\n"""
new = """                val context = requireContext()\n                val anzeigeAchsen = grafikAchsen.distinctBy { it.achseId }\n                val objektFlaechen = streckenObjektFlaechen(StreckenObjektSpeicher.alle(context), anzeigeAchsen)\n                val alleAchseIds = anzeigeAchsen.map { it.achseId }.toMutableSet()\n"""
if text.count(old) != 1:
    raise SystemExit(f"ImportExportFragment: anzeigeAchsen anchor count={text.count(old)}")
text = text.replace(old, new, 1)

old = """                val grafik = TrassenGrafikView(context).apply {\n                    setAchsen(grafikAchsen)\n                    setFestpunkte(festpunkte)\n                    setFestpunkteSichtbar(true)\n"""
new = """                val grafik = TrassenGrafikView(context).apply {\n                    setAchsen(grafikAchsen)\n                    setFestpunkte(festpunkte)\n                    setObjektFlaechen(objektFlaechen)\n                    setFestpunkteSichtbar(true)\n"""
if text.count(old) != 1:
    raise SystemExit(f"ImportExportFragment: main grafik anchor count={text.count(old)}")
text = text.replace(old, new, 1)
write(path, text)

print("TB-Helfer 2.0 (004) patch applied successfully")
