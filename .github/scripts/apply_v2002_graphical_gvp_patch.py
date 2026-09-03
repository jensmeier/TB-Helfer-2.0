from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: erwartet genau 1 Treffer, gefunden {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# ImportExportFragment: GVP-Auswahl direkt in der Grafik.
# -----------------------------------------------------------------------------
fragment_path = Path("app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
text = fragment_path.read_text(encoding="utf-8")

if "import de.tbhelfer.vermessung.VermarkungsAchsenAuswahlLogik" not in text:
    text = replace_once(
        text,
        "import de.tbhelfer.vermessung.ManuelleAbschnittKmLogik\n",
        "import de.tbhelfer.vermessung.ManuelleAbschnittKmLogik\nimport de.tbhelfer.vermessung.VermarkungsAchsenAuswahlLogik\n",
        "Import VermarkungsAchsenAuswahlLogik"
    )

if 'p.vermarkungName?.takeIf { it.isNotBlank() }' not in text:
    text = replace_once(
        text,
        '''            p.achseId != null && p.station != null -> buildString {
                append("Vermarkung ✓")
                p.achseName?.let { append(" · TRA $it") }
''',
        '''            p.achseId != null && p.station != null -> buildString {
                append("Vermarkung ✓")
                p.vermarkungName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                p.achseName?.let { append(" · TRA $it") }
''',
        "Vermarkungsname im Punktstatus"
    )

if 'punkt.vermarkungName?.takeIf { it.isNotBlank() }?.let { append(" GVP:$it") }' not in text:
    text = replace_once(
        text,
        '''                    if (punkt.lat != null && punkt.lon != null) append(" GPS✓")
                    if (punkt.km != null) append(" km✓")
''',
        '''                    if (punkt.lat != null && punkt.lon != null) append(" GPS✓")
                    punkt.vermarkungName?.takeIf { it.isNotBlank() }?.let { append(" GVP:$it") }
                    if (punkt.km != null) append(" km✓")
''',
        "GVP im Gesamtstatus"
    )

start_marker = "        fun vermarkungWaehlen(rolle: String) {"
end_marker = "\n\n        rollen.forEach { rolle ->"
start = text.find(start_marker)
if start < 0:
    raise SystemExit("vermarkungWaehlen: Startmarker nicht gefunden")
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("vermarkungWaehlen: Endmarker nicht gefunden")

new_function = r'''        fun vermarkungWaehlen(rolle: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                val (allePunkte, achsen) = withContext(Dispatchers.IO) {
                    ladeGrafikVermessungspunkte() to ladeRohGrafikAchsen()
                }
                val gvpPunkte = allePunkte.filter {
                    it.art.equals("GVP", ignoreCase = true) ||
                        it.art.equals("FESTPUNKT", ignoreCase = true)
                }
                if (gvpPunkte.isEmpty()) {
                    Toast.makeText(context, "Keine GVP/Festpunkte für die grafische Auswahl vorhanden.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val statusAuswahl = TextView(context).apply {
                    text = "$rolle: GVP/Festpunkt in der Grafik antippen. Zoomen und Verschieben ist möglich."
                    textSize = 13f
                    setPadding(0, 0, 0, dp(8))
                }
                val grafik = TrassenGrafikView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.heightPixels * 0.62f).toInt()
                            .coerceIn(dp(320), dp(620))
                    )
                    setAchsen(achsen)
                    setFestpunkte(gvpPunkte)
                    setFestpunkteSichtbar(true)
                    setSichtbarePunktArten(setOf("GVP", "FESTPUNKT"))
                    setLegendeSichtbar(false)
                }
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(4))
                    addView(statusAuswahl)
                    addView(grafik)
                }

                var ausgewaehlterPunkt: TrassenGrafikFestpunkt? = null
                var ausgewaehlteAchse: TrassenGrafikAchse? = null
                var ausgewaehlteProjektion: VermarkungsProjektion? = null
                var uebernehmenButton: Button? = null

                fun punktName(punkt: TrassenGrafikFestpunkt): String =
                    punkt.label.ifBlank { punkt.punktId ?: "GVP/Festpunkt" }

                fun kmText(punkt: TrassenGrafikFestpunkt): String =
                    punkt.kmMeter?.let { "km ${formatKmMeter(it)}" } ?: "ohne Datei-km"

                fun achsenName(achse: TrassenGrafikAchse): String =
                    achse.label.takeIf { it.isNotBlank() } ?: achse.name

                fun auswahlSetzen(
                    punkt: TrassenGrafikFestpunkt,
                    achse: TrassenGrafikAchse,
                    projektion: VermarkungsProjektion,
                    automatisch: Boolean
                ) {
                    ausgewaehlterPunkt = punkt
                    ausgewaehlteAchse = achse
                    ausgewaehlteProjektion = projektion
                    grafik.setAktiverFestpunkt(punkt)
                    grafik.setAktiveAchseId(achse.achseId)
                    grafik.setAnkerProjektion(
                        TrassenGrafikProjektion(punkt.x, punkt.y, projektion.projX, projektion.projY)
                    )
                    statusAuswahl.text = buildString {
                        appendLine("$rolle = ${punktName(punkt)} · ${kmText(punkt)}")
                        append("TRA: ${achsenName(achse)} · Station ${format1(projektion.station)} m · Abstand ${format1(projektion.abstandM)} m")
                        if (automatisch) append(" · eindeutig vorgeschlagen")
                    }
                    uebernehmenButton?.isEnabled = true
                }

                fun achseFuerPunktWaehlen(punkt: TrassenGrafikFestpunkt) {
                    ausgewaehlterPunkt = punkt
                    ausgewaehlteAchse = null
                    ausgewaehlteProjektion = null
                    uebernehmenButton?.isEnabled = false
                    grafik.setAktiverFestpunkt(punkt)
                    grafik.setAktiveAchseId(null)
                    grafik.setAnkerProjektion(null)

                    val alleTreffer = achsen.mapNotNull { achse ->
                        projiziereVermessungspunktAufAchse(punkt, achse)?.let { projektion -> achse to projektion }
                    }.sortedBy { it.second.abstandM }

                    // Konkrete Gleis-/Weichenachsen haben Vorrang vor einer allgemeinen
                    // Streckenachse, solange sie innerhalb des fachlichen Suchbereichs liegen.
                    val spezifischeTreffer = alleTreffer.filter { (achse, projektion) ->
                        achse.achsTyp != TrassenAchseEntity.ACHSTYP_STRECKE && projektion.abstandM <= 50.0
                    }
                    val bewertungsTreffer = if (spezifischeTreffer.isNotEmpty()) spezifischeTreffer else alleTreffer
                    val bewertung = VermarkungsAchsenAuswahlLogik.bewerte(
                        bewertungsTreffer.map { (achse, projektion) ->
                            VermarkungsAchsenAuswahlLogik.Kandidat(achse.achseId, projektion.abstandM)
                        }
                    )
                    if (bewertung.relevanteKandidaten.isEmpty()) {
                        statusAuswahl.text = "$rolle = ${punktName(punkt)} · ${kmText(punkt)}\nKeine TRA innerhalb 50 m gefunden. Bitte anderen GVP wählen."
                        return
                    }

                    val relevanteTreffer = bewertung.relevanteKandidaten.mapNotNull { kandidat ->
                        bewertungsTreffer.firstOrNull { it.first.achseId == kandidat.achseId }
                    }
                    val autoId = bewertung.automatischeAchseId
                    if (autoId != null) {
                        val treffer = relevanteTreffer.firstOrNull { it.first.achseId == autoId }
                        if (treffer != null) {
                            auswahlSetzen(punkt, treffer.first, treffer.second, automatisch = true)
                            return
                        }
                    }

                    // Mehrdeutiger Knoten / parallele Gleise / weiter entfernter Punkt:
                    // Benutzer entscheidet bewusst, keine heimliche Achszuordnung.
                    MaterialAlertDialogBuilder(context)
                        .setTitle("TRA für $rolle · ${punktName(punkt)} wählen")
                        .setItems(relevanteTreffer.map { (achse, projektion) ->
                            "${achsenName(achse)} · Station ${format1(projektion.station)} m · Abstand ${format1(projektion.abstandM)} m"
                        }.toTypedArray()) { _, which ->
                            relevanteTreffer.getOrNull(which)?.let { treffer ->
                                auswahlSetzen(punkt, treffer.first, treffer.second, automatisch = false)
                            }
                        }
                        .setNegativeButton("Anderen GVP wählen", null)
                        .show()
                }

                fun punktTrefferVerarbeiten(kandidaten: List<TrassenGrafikFestpunkt>) {
                    val eindeutig = kandidaten
                        .filter { it.art.equals("GVP", true) || it.art.equals("FESTPUNKT", true) }
                        .distinctBy { p ->
                            val xCm = kotlin.math.round(p.x * 100f).toInt()
                            val yCm = kotlin.math.round(p.y * 100f).toInt()
                            "${punktName(p).uppercase(Locale.GERMANY)}|$xCm|$yCm"
                        }
                    when (eindeutig.size) {
                        0 -> Unit
                        1 -> achseFuerPunktWaehlen(eindeutig.first())
                        else -> MaterialAlertDialogBuilder(context)
                            .setTitle("Mehrere GVP an dieser Stelle")
                            .setItems(eindeutig.map { "${punktName(it)} · ${kmText(it)}" }.toTypedArray()) { _, which ->
                                eindeutig.getOrNull(which)?.let(::achseFuerPunktWaehlen)
                            }
                            .setNegativeButton("Abbrechen", null)
                            .show()
                    }
                }

                grafik.setOnFestpunkteInfoRequested(::punktTrefferVerarbeiten)

                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle("$rolle · GVP in Grafik wählen")
                    .setView(container)
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen", null)
                    .create()
                dialog.setOnShowListener {
                    uebernehmenButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).apply {
                        isEnabled = false
                        setOnClickListener {
                            val punkt = ausgewaehlterPunkt ?: return@setOnClickListener
                            val achse = ausgewaehlteAchse ?: return@setOnClickListener
                            val projektion = ausgewaehlteProjektion ?: return@setOnClickListener
                            val objektPunkt = StreckenObjektSpeicher.Punkt(
                                rolle = rolle,
                                km = punkt.kmMeter,
                                kmManuell = false,
                                achseId = achse.achseId,
                                achseName = achsenName(achse),
                                station = projektion.station,
                                achsabstandM = projektion.abstandM,
                                vermarkungName = punktName(punkt),
                                vermarkungArt = punkt.art,
                                vermarkungQuelleDatei = punkt.quelleDatei,
                                vermarkungX = punkt.x.toDouble(),
                                vermarkungY = punkt.y.toDouble()
                            )
                            punktUebernehmen(rolle, objektPunkt)
                            dialog.dismiss()
                        }
                    }
                    grafik.post { grafik.fitToContent() }
                }
                dialog.show()
            }
        }'''

text = text[:start] + new_function + text[end:]
fragment_path.write_text(text, encoding="utf-8")


# -----------------------------------------------------------------------------
# TrassenGrafikView: den gerade gewaehlten GVP temporaer blau markieren.
# -----------------------------------------------------------------------------
grafik_path = Path("app/src/main/java/de/tbhelfer/ui/importexport/TrassenGrafikView.kt")
gtext = grafik_path.read_text(encoding="utf-8")

if "private var aktiverFestpunkt: TrassenGrafikFestpunkt? = null" not in gtext:
    gtext = replace_once(
        gtext,
        "    private var ankerProjektion: TrassenGrafikProjektion? = null\n",
        "    private var ankerProjektion: TrassenGrafikProjektion? = null\n    private var aktiverFestpunkt: TrassenGrafikFestpunkt? = null\n",
        "aktiver Festpunkt Property"
    )

if "fun setAktiverFestpunkt(punkt: TrassenGrafikFestpunkt?)" not in gtext:
    gtext = replace_once(
        gtext,
        '''    fun setAnkerProjektion(projektion: TrassenGrafikProjektion?) {
        ankerProjektion = projektion
        invalidate()
    }

    fun fitToContent() {
''',
        '''    fun setAnkerProjektion(projektion: TrassenGrafikProjektion?) {
        ankerProjektion = projektion
        invalidate()
    }

    /** Temporäre Auswahlmarkierung; Blau ist ausschließlich Auswahl/Markierung. */
    fun setAktiverFestpunkt(punkt: TrassenGrafikFestpunkt?) {
        aktiverFestpunkt = punkt
        invalidate()
    }

    fun fitToContent() {
''',
        "Setter aktiver Festpunkt"
    )

if "val basisPunktFarbe = punktPaint.color" not in gtext:
    gtext = replace_once(
        gtext,
        '''            val radius = when {
                zoom >= 6f -> dpF(6f)
                zoom >= 3f -> dpF(5f)
                else -> dpF(3.5f)
            }
            canvas.drawCircle(x, y, radius, punktPaint)
            canvas.drawCircle(x, y, radius, punktRandPaint)
''',
        '''            val radius = when {
                zoom >= 6f -> dpF(6f)
                zoom >= 3f -> dpF(5f)
                else -> dpF(3.5f)
            }
            val basisPunktFarbe = punktPaint.color
            if (punkt == aktiverFestpunkt) {
                punktPaint.color = if (isDark) Color.rgb(100, 181, 246) else Color.rgb(25, 118, 210)
                canvas.drawCircle(x, y, radius + dpF(4.5f), punktPaint)
                punktPaint.color = basisPunktFarbe
            }
            canvas.drawCircle(x, y, radius, punktPaint)
            canvas.drawCircle(x, y, radius, punktRandPaint)
''',
        "blaue GVP Auswahlmarkierung"
    )

grafik_path.write_text(gtext, encoding="utf-8")


# -----------------------------------------------------------------------------
# StreckenObjektSpeicher: GVP-Quelle backward-kompatibel mit dem Punkt speichern.
# Keine Room-Migration; alte JSON-Daten bleiben lesbar.
# -----------------------------------------------------------------------------
speicher_path = Path("app/src/main/java/de/tbhelfer/streckenobjekte/StreckenObjektSpeicher.kt")
stext = speicher_path.read_text(encoding="utf-8")

if "val vermarkungName: String? = null" not in stext:
    stext = replace_once(
        stext,
        '''        val achseName: String? = null,
        val station: Double? = null,
        val achsabstandM: Double? = null
    )
''',
        '''        val achseName: String? = null,
        val station: Double? = null,
        val achsabstandM: Double? = null,
        val vermarkungName: String? = null,
        val vermarkungArt: String? = null,
        val vermarkungQuelleDatei: String? = null,
        val vermarkungX: Double? = null,
        val vermarkungY: Double? = null
    )
''',
        "GVP Felder im Punkt"
    )

if 'put("vermarkungName", vermarkungName)' not in stext:
    stext = replace_once(
        stext,
        '''        put("achseName", achseName)
        put("station", station)
        put("achsabstandM", achsabstandM)
    }
''',
        '''        put("achseName", achseName)
        put("station", station)
        put("achsabstandM", achsabstandM)
        put("vermarkungName", vermarkungName)
        put("vermarkungArt", vermarkungArt)
        put("vermarkungQuelleDatei", vermarkungQuelleDatei)
        put("vermarkungX", vermarkungX)
        put("vermarkungY", vermarkungY)
    }
''',
        "GVP Felder JSON schreiben"
    )

if 'vermarkungName = optNullableString("vermarkungName")' not in stext:
    stext = replace_once(
        stext,
        '''            achseName = optNullableString("achseName"),
            station = optNullableDouble("station"),
            achsabstandM = optNullableDouble("achsabstandM")
        )
''',
        '''            achseName = optNullableString("achseName"),
            station = optNullableDouble("station"),
            achsabstandM = optNullableDouble("achsabstandM"),
            vermarkungName = optNullableString("vermarkungName"),
            vermarkungArt = optNullableString("vermarkungArt"),
            vermarkungQuelleDatei = optNullableString("vermarkungQuelleDatei"),
            vermarkungX = optNullableDouble("vermarkungX"),
            vermarkungY = optNullableDouble("vermarkungY")
        )
''',
        "GVP Felder JSON lesen"
    )

speicher_path.write_text(stext, encoding="utf-8")
