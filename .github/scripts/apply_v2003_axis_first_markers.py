from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: erwartet genau 1 Treffer, gefunden {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# ImportExportFragment: Achse zuerst + GVP/TRA-Anschluss/GPS + sichtbare Marker.
# -----------------------------------------------------------------------------
fragment_path = Path("app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
text = fragment_path.read_text(encoding="utf-8")

if "import de.tbhelfer.vermessung.TrassenAnschlussLogik" not in text:
    text = replace_once(
        text,
        "import de.tbhelfer.vermessung.TrassenNaheBerechner\n",
        "import de.tbhelfer.vermessung.TrassenNaheBerechner\nimport de.tbhelfer.vermessung.TrassenAnschlussLogik\n",
        "Import TrassenAnschlussLogik"
    )

# Grenze 1/2 in der Auswahlgrafik schon auf der Roh-TRA anzeigen.
text = replace_once(
    text,
    "            val darstellung = rawAlle.map { a -> if (a.achseId == zielId) a.copy(label = entwurf.bezeichnung) else a }\n",
    "            val darstellung = rawAlle.map { a ->\n                if (a.achseId == zielId) a.copy(\n                    label = entwurf.bezeichnung,\n                    abschnittStartStation = entwurf.stationStart,\n                    abschnittEndeStation = entwurf.stationEnde\n                ) else a\n            }\n",
    "Grenzmarker auf Zielachse"
)

# Nur Punkte im sichtbaren Achsumfeld in die Bounds aufnehmen.
text = replace_once(
    text,
    "                setAchsen(darstellung); setFestpunkte(festpunkte); setFestpunkteSichtbar(true)\n                setSichtbareTypen(darstellung.map { it.achsTyp }.toSet()); setSichtbareAchseIds(sichtbar.toSet())\n",
    "                setAchsen(darstellung)\n                setFestpunkte(grafikPunkteImUmfeld(festpunkte, darstellung, sichtbar.toSet()))\n                setFestpunkteSichtbar(true)\n                setSichtbareTypen(darstellung.map { it.achsTyp }.toSet()); setSichtbareAchseIds(sichtbar.toSet())\n",
    "lokale Grenz-Festpunkte"
)

text = replace_once(
    text,
    "                        grafik.setSichtbareAchseIds(sichtbar.toSet())\n",
    "                        grafik.setSichtbareAchseIds(sichtbar.toSet())\n                        grafik.setFestpunkte(grafikPunkteImUmfeld(festpunkte, darstellung, sichtbar.toSet()))\n",
    "Grenz-Anzeige aktualisiert lokale Festpunkte"
)

# Status fuer echten TRA-Anschluss von Vermarkung unterscheiden.
text = replace_once(
    text,
    '''            p.achseId != null && p.station != null -> buildString {
                append("Vermarkung ✓")
                p.vermarkungName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
''',
    '''            p.achseId != null && p.station != null -> buildString {
                if (p.vermarkungArt == "TRA_ANSCHLUSS") append("TRA-Anschluss ✓") else append("Vermarkung ✓")
                p.vermarkungName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
''',
    "Status TRA-Anschluss"
)

text = replace_once(
    text,
    '                    punkt.vermarkungName?.takeIf { it.isNotBlank() }?.let { append(" GVP:$it") }\n',
    '''                    punkt.vermarkungName?.takeIf { it.isNotBlank() }?.let {
                        if (punkt.vermarkungArt == "TRA_ANSCHLUSS") append(" Anschluss:$it") else append(" GVP:$it")
                    }
''',
    "Gesamtstatus GVP/Anschluss"
)

# Alte automatische Achszuordnung von GPS/GVP komplett durch Achse-zuerst-Workflow ersetzen.
helper_start = text.find("        fun gpsMessungUebernehmen(rolle: String, messung: AbschnittGpsMessung) {")
if helper_start < 0:
    raise SystemExit("Start gpsMessungUebernehmen nicht gefunden")
helper_end = text.find("\n\n        rollen.forEach { rolle ->", helper_start)
if helper_end < 0:
    raise SystemExit("Ende vor rollen.forEach nicht gefunden")

new_helpers = r'''        fun achsenName(achse: TrassenGrafikAchse): String =
            achse.label.takeIf { it.isNotBlank() } ?: achse.name

        fun punktAufAchse(achse: TrassenGrafikAchse, station: Double): TrassenGrafikPunkt? {
            val ps = achse.punkte.sortedBy { it.station }
            if (ps.isEmpty()) return null
            ps.minByOrNull { kotlin.math.abs(it.station - station) }
                ?.takeIf { kotlin.math.abs(it.station - station) <= 0.01 }
                ?.let { return it }
            for (i in 0 until ps.lastIndex) {
                val a = ps[i]
                val b = ps[i + 1]
                val von = minOf(a.station, b.station)
                val bis = maxOf(a.station, b.station)
                if (station < von || station > bis) continue
                val ds = b.station - a.station
                if (kotlin.math.abs(ds) < 1e-9) return a
                val t = ((station - a.station) / ds).coerceIn(0.0, 1.0).toFloat()
                val km = if (a.kmMeter != null && b.kmMeter != null) {
                    a.kmMeter + (b.kmMeter - a.kmMeter) * t.toDouble()
                } else a.kmMeter ?: b.kmMeter
                return TrassenGrafikPunkt(
                    station = station,
                    x = a.x + (b.x - a.x) * t,
                    y = a.y + (b.y - a.y) * t,
                    kmMeter = km
                )
            }
            return ps.minByOrNull { kotlin.math.abs(it.station - station) }
        }

        fun objektMarker(achsen: List<TrassenGrafikAchse>, aktiveRolle: String?): List<TrassenGrafikMarker> {
            val byId = achsen.associateBy { it.achseId }
            return punkte.mapNotNull { (rolle, punkt) ->
                val id = punkt.achseId ?: return@mapNotNull null
                val station = punkt.station ?: return@mapNotNull null
                val achse = byId[id] ?: return@mapNotNull null
                val p = punktAufAchse(achse, station) ?: return@mapNotNull null
                TrassenGrafikMarker(
                    label = rolle,
                    x = p.x,
                    y = p.y,
                    achseId = id,
                    aktiv = rolle == aktiveRolle
                )
            }
        }

        fun anschluesseBerechnen(achsen: List<TrassenGrafikAchse>): List<TrassenAnschlussLogik.Anschluss> =
            TrassenAnschlussLogik.findeAnschluesse(
                achsen.filter { it.punkte.isNotEmpty() }.map { achse ->
                    TrassenAnschlussLogik.Achse(
                        id = achse.achseId,
                        name = achsenName(achse),
                        achsTyp = achse.achsTyp,
                        punkte = achse.punkte.map { p ->
                            TrassenAnschlussLogik.Punkt(p.station, p.x.toDouble(), p.y.toDouble())
                        }
                    )
                },
                toleranzM = 2.5
            )

        fun direkteAchsenIds(
            zielId: Long,
            anschluesse: List<TrassenAnschlussLogik.Anschluss>
        ): Set<Long> = buildSet {
            add(zielId)
            TrassenAnschlussLogik.anschluesseFuerAchse(zielId, anschluesse).forEach { a ->
                a.partnerFuer(zielId)?.achseId?.let(::add)
            }
        }

        fun zeigeAnzeigeFuerObjektGrafik(
            grafik: TrassenGrafikView,
            alleAchsen: List<TrassenGrafikAchse>,
            alleFestpunkte: List<TrassenGrafikFestpunkt>,
            sichtbareIds: MutableSet<Long>,
            sichtbareArten: MutableSet<String>,
            festeAchseId: Long? = null
        ) {
            val liste = alleAchsen.sortedWith(compareBy<TrassenGrafikAchse> { it.label }.thenBy { it.name })
            val labels = liste.map { achse ->
                if (achse.achseId == festeAchseId) "✓ Ziel: ${achsenName(achse)}" else achse.toGrafikAuswahlText(liste)
            }.toMutableList()
            val punktKonfiguration = listOf(
                "GVP" to "GVP/Festpunkte",
                "FESTPUNKT" to "Festpunkte",
                "AP" to "Absteckpunkte",
                "WEICHENPUNKT" to "Weichenpunkte"
            ).filter { (art, _) -> alleFestpunkte.any { it.art.equals(art, true) } }
            val punktIndex = mutableMapOf<Int, String>()
            punktKonfiguration.forEach { (art, label) ->
                punktIndex[labels.size] = art
                labels += "$label (${alleFestpunkte.count { it.art.equals(art, true) }})"
            }
            val tempIds = sichtbareIds.toMutableSet()
            val tempArten = sichtbareArten.toMutableSet()
            val checked = BooleanArray(labels.size) { idx ->
                if (idx < liste.size) liste[idx].achseId in tempIds
                else punktIndex[idx]?.let { art -> tempArten.any { it.equals(art, true) } } == true
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("Anzeige ändern")
                .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                    if (which < liste.size) {
                        val id = liste[which].achseId
                        if (id == festeAchseId) tempIds.add(id)
                        else if (isChecked) tempIds.add(id) else tempIds.remove(id)
                    } else {
                        punktIndex[which]?.let { art ->
                            if (isChecked) tempArten.add(art) else tempArten.removeAll { it.equals(art, true) }
                        }
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen") { _, _ ->
                    festeAchseId?.let(tempIds::add)
                    sichtbareIds.clear(); sichtbareIds.addAll(tempIds)
                    sichtbareArten.clear(); sichtbareArten.addAll(tempArten)
                    grafik.setSichtbareAchseIds(sichtbareIds.toSet())
                    grafik.setSichtbarePunktArten(sichtbareArten.toSet())
                    grafik.setFestpunkteSichtbar(sichtbareArten.isNotEmpty())
                    grafik.setFestpunkte(grafikPunkteImUmfeld(alleFestpunkte, alleAchsen, sichtbareIds.toSet()))
                }
                .show()
        }

        fun gpsAufFesterAchseSetzen(rolle: String, zielAchse: TrassenGrafikAchse) {
            mitStandortBerechtigung {
                sammleAbschnittGpsMessung("$rolle per GPS auf ${achsenName(zielAchse)} setzen") { messung ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val kandidat = withContext(Dispatchers.IO) {
                            ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(
                                messung.latitude, messung.longitude, 80.0, 20, false
                            ).firstOrNull { it.achseId == zielAchse.achseId }
                        }
                        if (kandidat == null || kandidat.abstandM > 20.0) {
                            Toast.makeText(
                                context,
                                "GPS-Punkt liegt nicht sicher auf der gewählten TRA ${achsenName(zielAchse)}.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val alt = punkte[rolle]
                        punktUebernehmen(
                            rolle,
                            StreckenObjektSpeicher.Punkt(
                                rolle = rolle,
                                lat = messung.latitude,
                                lon = messung.longitude,
                                accuracyM = messung.accuracyM,
                                km = alt?.km?.takeIf { alt.kmManuell } ?: kandidat.kmMeter,
                                kmManuell = alt?.kmManuell == true,
                                achseId = zielAchse.achseId,
                                achseName = achsenName(zielAchse),
                                station = kandidat.station,
                                achsabstandM = kandidat.abstandM
                            )
                        )
                    }
                }
            }
        }

        fun traAnschlussAufFesterAchseSetzen(
            rolle: String,
            zielAchse: TrassenGrafikAchse,
            anschluesse: List<TrassenAnschlussLogik.Anschluss>
        ) {
            val kandidaten = TrassenAnschlussLogik.anschluesseFuerAchse(zielAchse.achseId, anschluesse)
            if (kandidaten.isEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Kein TRA-Anschluss gefunden")
                    .setMessage("Auf ${achsenName(zielAchse)} wurde innerhalb 2,5 m kein echter Achsende-/TRA-Anschluss erkannt. Bitte GVP oder GPS verwenden oder eine andere Achse wählen.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            fun uebernehmen(anschluss: TrassenAnschlussLogik.Anschluss) {
                val pos = anschluss.positionFuer(zielAchse.achseId) ?: return
                val partner = anschluss.partnerFuer(zielAchse.achseId) ?: return
                val km = punktAufAchse(zielAchse, pos.station)?.kmMeter
                punktUebernehmen(
                    rolle,
                    StreckenObjektSpeicher.Punkt(
                        rolle = rolle,
                        km = km,
                        kmManuell = false,
                        achseId = zielAchse.achseId,
                        achseName = achsenName(zielAchse),
                        station = pos.station,
                        achsabstandM = anschluss.abstandM,
                        vermarkungName = "an ${partner.achseName}",
                        vermarkungArt = "TRA_ANSCHLUSS"
                    )
                )
                Toast.makeText(
                    context,
                    "$rolle = TRA-Anschluss ${achsenName(zielAchse)} ↔ ${partner.achseName}",
                    Toast.LENGTH_LONG
                ).show()
            }

            if (kandidaten.size == 1) {
                val a = kandidaten.first()
                val partner = a.partnerFuer(zielAchse.achseId) ?: return
                val pos = a.positionFuer(zielAchse.achseId) ?: return
                MaterialAlertDialogBuilder(context)
                    .setTitle("$rolle · TRA-Anschluss übernehmen?")
                    .setMessage("${achsenName(zielAchse)} ↔ ${partner.achseName}\nStation ${format1(pos.station)} m · Anschlussabstand ${format1(a.abstandM)} m")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen") { _, _ -> uebernehmen(a) }
                    .show()
            } else {
                MaterialAlertDialogBuilder(context)
                    .setTitle("$rolle · Welcher TRA-Anschluss?")
                    .setItems(kandidaten.map { a ->
                        val partner = a.partnerFuer(zielAchse.achseId)
                        val pos = a.positionFuer(zielAchse.achseId)
                        "${partner?.achseName ?: "Partner"} · Station ${pos?.station?.let(::format1) ?: "?"} m · ${format1(a.abstandM)} m"
                    }.toTypedArray()) { _, which -> kandidaten.getOrNull(which)?.let(::uebernehmen) }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }

        fun gvpAufFesterAchseWaehlen(
            rolle: String,
            zielAchse: TrassenGrafikAchse,
            alleAchsen: List<TrassenGrafikAchse>,
            alleFestpunkte: List<TrassenGrafikFestpunkt>,
            anschluesse: List<TrassenAnschlussLogik.Anschluss>
        ) {
            val gvpAlle = alleFestpunkte.filter {
                it.art.equals("GVP", true) || it.art.equals("FESTPUNKT", true)
            }
            if (gvpAlle.isEmpty()) {
                Toast.makeText(context, "Keine GVP/Festpunkte vorhanden.", Toast.LENGTH_LONG).show()
                return
            }
            val sichtbareIds = direkteAchsenIds(zielAchse.achseId, anschluesse).toMutableSet()
            val sichtbareArten = mutableSetOf("GVP", "FESTPUNKT")
            val lokaleGvp = grafikPunkteImUmfeld(gvpAlle, alleAchsen, sichtbareIds.toSet())
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val kopf = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            val zurueck = MaterialButton(context).apply { text = "Zurück"; minWidth = 0; minimumWidth = 0 }
            val titel = TextView(context).apply {
                text = "$rolle · GVP auf ${achsenName(zielAchse)}"
                textSize = 16f; textStyleBold(); gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val anzeige = MaterialButton(context).apply { text = "Anzeige"; minWidth = 0; minimumWidth = 0 }
            kopf.addView(zurueck); kopf.addView(titel); kopf.addView(anzeige); container.addView(kopf)
            val status = TextView(context).apply {
                text = "GVP/Festpunkt antippen. Er wird ausschließlich auf die vorher gewählte TRA ${achsenName(zielAchse)} projiziert."
                textSize = 12f; setPadding(dp(8), 0, dp(8), dp(4))
            }
            container.addView(status)
            val grafik = TrassenGrafikView(context).apply {
                setAchsen(alleAchsen)
                setFestpunkte(lokaleGvp)
                setFestpunkteSichtbar(true)
                setSichtbareTypen(alleAchsen.map { it.achsTyp }.toSet())
                setSichtbareAchseIds(sichtbareIds.toSet())
                setSichtbarePunktArten(sichtbareArten.toSet())
                setAktiveAchseId(zielAchse.achseId)
                setObjektMarker(objektMarker(alleAchsen, rolle))
                setLegendeSichtbar(false)
            }
            container.addView(grafik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            lateinit var dialog: android.app.Dialog
            var gewaehlt: TrassenGrafikFestpunkt? = null
            var projektion: VermarkungsProjektion? = null
            var uebernehmenButton: Button? = null

            fun punktName(p: TrassenGrafikFestpunkt) = p.label.ifBlank { p.punktId ?: "GVP/Festpunkt" }

            fun punktWaehlen(punkt: TrassenGrafikFestpunkt) {
                val pr = projiziereVermessungspunktAufAchse(punkt, zielAchse)
                if (pr == null) {
                    Toast.makeText(context, "GVP konnte nicht auf ${achsenName(zielAchse)} projiziert werden.", Toast.LENGTH_LONG).show()
                    return
                }
                gewaehlt = punkt
                projektion = pr
                grafik.setAktiverFestpunkt(punkt)
                grafik.setAnkerProjektion(TrassenGrafikProjektion(punkt.x, punkt.y, pr.projX, pr.projY))
                status.text = buildString {
                    appendLine("$rolle = ${punktName(punkt)} · ${punkt.kmMeter?.let { "km ${formatKmMeter(it)}" } ?: "ohne Datei-km"}")
                    append("Ziel-TRA: ${achsenName(zielAchse)} · Station ${format1(pr.station)} m · Abstand ${format1(pr.abstandM)} m")
                }
                uebernehmenButton?.isEnabled = true
            }

            grafik.setOnFestpunkteInfoRequested { kandidaten ->
                val eindeutig = kandidaten
                    .filter { it.art.equals("GVP", true) || it.art.equals("FESTPUNKT", true) }
                    .distinctBy { p ->
                        val xCm = kotlin.math.round(p.x * 100f).toInt()
                        val yCm = kotlin.math.round(p.y * 100f).toInt()
                        "${punktName(p).uppercase(Locale.GERMANY)}|$xCm|$yCm"
                    }
                when (eindeutig.size) {
                    0 -> Unit
                    1 -> punktWaehlen(eindeutig.first())
                    else -> MaterialAlertDialogBuilder(context)
                        .setTitle("Welchen GVP?")
                        .setItems(eindeutig.map { "${punktName(it)} · ${it.kmMeter?.let(::formatKmMeter) ?: "kein km"}" }.toTypedArray()) { _, which ->
                            eindeutig.getOrNull(which)?.let(::punktWaehlen)
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }

            dialog = android.app.Dialog(context).apply {
                requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                setContentView(container)
                setCancelable(true)
            }
            zurueck.setOnClickListener { dialog.dismiss() }
            anzeige.setOnClickListener {
                zeigeAnzeigeFuerObjektGrafik(
                    grafik, alleAchsen, gvpAlle, sichtbareIds, sichtbareArten, zielAchse.achseId
                )
            }
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                grafik.post { grafik.fitToContent() }
                insets
            }
            dialog.setOnShowListener {
                dialog.window?.let { w ->
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                uebernehmenButton = MaterialButton(context).apply {
                    text = "Übernehmen"
                    isEnabled = false
                    setOnClickListener {
                        val p = gewaehlt ?: return@setOnClickListener
                        val pr = projektion ?: return@setOnClickListener
                        punktUebernehmen(
                            rolle,
                            StreckenObjektSpeicher.Punkt(
                                rolle = rolle,
                                km = p.kmMeter,
                                kmManuell = false,
                                achseId = zielAchse.achseId,
                                achseName = achsenName(zielAchse),
                                station = pr.station,
                                achsabstandM = pr.abstandM,
                                vermarkungName = punktName(p),
                                vermarkungArt = p.art,
                                vermarkungQuelleDatei = p.quelleDatei,
                                vermarkungX = p.x.toDouble(),
                                vermarkungY = p.y.toDouble()
                            )
                        )
                        dialog.dismiss()
                    }
                }
                container.addView(uebernehmenButton)
                ViewCompat.requestApplyInsets(container)
                grafik.post { grafik.fitToContent() }
            }
            dialog.show()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        fun quelleAufFesterAchseWaehlen(
            rolle: String,
            zielAchse: TrassenGrafikAchse,
            alleAchsen: List<TrassenGrafikAchse>,
            alleFestpunkte: List<TrassenGrafikFestpunkt>,
            anschluesse: List<TrassenAnschlussLogik.Anschluss>,
            nurGps: Boolean,
            onAndereAchse: () -> Unit
        ) {
            if (nurGps) {
                gpsAufFesterAchseSetzen(rolle, zielAchse)
                return
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("$rolle · Punkt auf ${achsenName(zielAchse)} festlegen")
                .setItems(arrayOf(
                    "GVP / Festpunkt auswählen",
                    "Echten TRA-Anschluss verwenden",
                    "GPS auf dieser TRA setzen",
                    "Andere Achse wählen"
                )) { _, which ->
                    when (which) {
                        0 -> gvpAufFesterAchseWaehlen(rolle, zielAchse, alleAchsen, alleFestpunkte, anschluesse)
                        1 -> traAnschlussAufFesterAchseSetzen(rolle, zielAchse, anschluesse)
                        2 -> gpsAufFesterAchseSetzen(rolle, zielAchse)
                        3 -> onAndereAchse()
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        fun achseUndPunktWaehlen(rolle: String, nurGps: Boolean = false) {
            viewLifecycleOwner.lifecycleScope.launch {
                val (alleAchsen, alleFestpunkte) = withContext(Dispatchers.IO) {
                    ladeRohGrafikAchsen() to ladeGrafikVermessungspunkte()
                }
                if (alleAchsen.isEmpty()) {
                    Toast.makeText(context, "Keine TRA-Geometrie vorhanden.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val anschluesse = withContext(Dispatchers.Default) { anschluesseBerechnen(alleAchsen) }
                val bestehendeIds = punkte.values.mapNotNull { it.achseId }.toSet()
                val rollenId = punkte[rolle]?.achseId
                val sichtbareIds = when {
                    rollenId != null -> direkteAchsenIds(rollenId, anschluesse).toMutableSet()
                    bestehendeIds.isNotEmpty() -> bestehendeIds.flatMap { direkteAchsenIds(it, anschluesse) }.toMutableSet()
                    else -> alleAchsen.filter { it.achsTyp != TrassenAchseEntity.ACHSTYP_STRECKE }
                        .map { it.achseId }.toMutableSet()
                }.ifEmpty { alleAchsen.map { it.achseId }.toMutableSet() }
                val sichtbareArten = if (bestehendeIds.isNotEmpty()) mutableSetOf("GVP", "FESTPUNKT") else mutableSetOf()
                val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val kopf = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                }
                val zurueck = MaterialButton(context).apply { text = "Zurück"; minWidth = 0; minimumWidth = 0 }
                val titel = TextView(context).apply {
                    text = "$rolle · zuerst TRA wählen"
                    textSize = 17f; textStyleBold(); gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val anzeige = MaterialButton(context).apply { text = "Anzeige"; minWidth = 0; minimumWidth = 0 }
                kopf.addView(zurueck); kopf.addView(titel); kopf.addView(anzeige); container.addView(kopf)
                val status = TextView(context).apply {
                    text = "TRA antippen. Bei übereinanderliegenden Achsen lange drücken. Bereits gesetzte Punkte dieses Objekts bleiben sichtbar."
                    textSize = 12f; setPadding(dp(8), 0, dp(8), dp(4))
                }
                container.addView(status)
                val grafik = TrassenGrafikView(context).apply {
                    setAchsen(alleAchsen)
                    setFestpunkte(grafikPunkteImUmfeld(alleFestpunkte, alleAchsen, sichtbareIds.toSet()))
                    setFestpunkteSichtbar(sichtbareArten.isNotEmpty())
                    setSichtbareTypen(alleAchsen.map { it.achsTyp }.toSet())
                    setSichtbareAchseIds(sichtbareIds.toSet())
                    setSichtbarePunktArten(sichtbareArten.toSet())
                    setObjektMarker(objektMarker(alleAchsen, rolle))
                    setAktiveAchseId(rollenId)
                    setLegendeSichtbar(false)
                }
                container.addView(grafik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                lateinit var dialog: android.app.Dialog

                fun achseBestaetigen(achse: TrassenGrafikAchse) {
                    grafik.setAktiveAchseId(achse.achseId)
                    status.text = "$rolle · gewählte TRA: ${achsenName(achse)}"
                    MaterialAlertDialogBuilder(context)
                        .setTitle("TRA für $rolle verwenden?")
                        .setMessage("${achsenName(achse)}\n\nDanach wählst du GVP, echten TRA-Anschluss oder GPS.")
                        .setNegativeButton("Andere Achse", null)
                        .setPositiveButton("Weiter") { _, _ ->
                            dialog.dismiss()
                            quelleAufFesterAchseWaehlen(
                                rolle, achse, alleAchsen, alleFestpunkte, anschluesse, nurGps
                            ) { achseUndPunktWaehlen(rolle, nurGps) }
                        }
                        .show()
                }

                grafik.setOnAchseInfoRequested(::achseBestaetigen)
                grafik.setOnAchsenLongPressRequested { kandidaten ->
                    if (kandidaten.size == 1) achseBestaetigen(kandidaten.first())
                    else if (kandidaten.isNotEmpty()) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Welche TRA für $rolle?")
                            .setItems(kandidaten.map(::achsenName).toTypedArray()) { _, which ->
                                kandidaten.getOrNull(which)?.let(::achseBestaetigen)
                            }
                            .setNegativeButton("Abbrechen", null)
                            .show()
                    }
                }

                dialog = android.app.Dialog(context).apply {
                    requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                    setContentView(container)
                    setCancelable(true)
                }
                zurueck.setOnClickListener { dialog.dismiss() }
                anzeige.setOnClickListener {
                    zeigeAnzeigeFuerObjektGrafik(
                        grafik, alleAchsen, alleFestpunkte, sichtbareIds, sichtbareArten, null
                    )
                }
                ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    grafik.post { grafik.fitToContent() }
                    insets
                }
                dialog.setOnShowListener {
                    dialog.window?.let { w ->
                        WindowCompat.setDecorFitsSystemWindows(w, false)
                        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                    ViewCompat.requestApplyInsets(container)
                    grafik.post { grafik.fitToContent() }
                }
                dialog.show()
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }'''

text = text[:helper_start] + new_helpers + text[helper_end:]

# Rollen-UI: GPS ebenfalls Achse zuerst; Vermarkung wird zu Vermarkung/Anschluss.
text = replace_once(
    text,
    '                text = "Vermarkung wählen"\n',
    '                text = "Vermarkung / Anschluss"\n',
    "Button Vermarkung/Anschluss"
)

roles_start = text.find("\n\n        rollen.forEach { rolle ->", helper_start)
status_start = text.find("\n\n        val statusGesamt", roles_start)
if roles_start < 0 or status_start < 0:
    raise SystemExit("Rollenbereich nicht gefunden")
segment = text[roles_start:status_start]
gps_start = segment.find("            gps.setOnClickListener {")
verm_line = "            vermarkung?.setOnClickListener { vermarkungWaehlen(rolle) }\n"
verm_pos = segment.find(verm_line, gps_start)
if gps_start < 0 or verm_pos < 0:
    raise SystemExit("GPS/Vermarkung Listener im Rollenbereich nicht gefunden")
verm_end = verm_pos + len(verm_line)
new_listener = '''            gps.setOnClickListener { achseUndPunktWaehlen(rolle, nurGps = true) }
            vermarkung?.setOnClickListener { achseUndPunktWaehlen(rolle, nurGps = false) }
'''
segment = segment[:gps_start] + new_listener + segment[verm_end:]
text = text[:roles_start] + segment + text[status_start:]

# Fachhinweis im Editor an den neuen Ablauf anpassen.
text = replace_once(
    text,
    '            text = "Weichenpunkte WA/WE/WEZ und DKW-Punkte A/B/C/D können per Vermarkungspunkt oder GPS gesetzt werden. km bleibt optional; vorhandener Datei-km wird übernommen."\n',
    '            text = "Für WA/WE/WEZ und DKW A/B/C/D zuerst die Ziel-TRA wählen. Danach GVP/Festpunkt, echten TRA-Anschluss oder GPS verwenden. Ein GVP selbst gehört nicht fest zu einer Achse; vorhandener Datei-km wird übernommen."\n',
    "Editor-Hinweis Achse zuerst"
)

# Wiederverwendbarer Umgebungsfilter vor der Projektionsfunktion.
proj_marker = "    private fun projiziereVermessungspunktAufAchse(\n"
proj_index = text.find(proj_marker)
if proj_index < 0:
    raise SystemExit("Projektionsfunktion nicht gefunden")
if "private fun grafikPunkteImUmfeld(" not in text:
    helper = r'''    private fun grafikPunkteImUmfeld(
        punkte: List<TrassenGrafikFestpunkt>,
        achsen: List<TrassenGrafikAchse>,
        sichtbareIds: Set<Long>,
        randM: Float = 120f
    ): List<TrassenGrafikFestpunkt> {
        val achsPunkte = achsen
            .filter { it.achseId in sichtbareIds }
            .flatMap { it.punkte }
        if (achsPunkte.isEmpty()) return emptyList()
        val minX = achsPunkte.minOf { it.x } - randM
        val maxX = achsPunkte.maxOf { it.x } + randM
        val minY = achsPunkte.minOf { it.y } - randM
        val maxY = achsPunkte.maxOf { it.y } + randM
        return punkte.filter { it.x in minX..maxX && it.y in minY..maxY }
    }

'''
    text = text[:proj_index] + helper + text[proj_index:]

fragment_path.write_text(text, encoding="utf-8")


# -----------------------------------------------------------------------------
# TrassenGrafikView: groessere G1/G2 + Marker fuer WA/WE/WEZ/A/B/C/D.
# -----------------------------------------------------------------------------
grafik_path = Path("app/src/main/java/de/tbhelfer/ui/importexport/TrassenGrafikView.kt")
gtext = grafik_path.read_text(encoding="utf-8")

if "private var objektMarker: List<TrassenGrafikMarker>" not in gtext:
    gtext = replace_once(
        gtext,
        "    private var aktiverFestpunkt: TrassenGrafikFestpunkt? = null\n",
        "    private var aktiverFestpunkt: TrassenGrafikFestpunkt? = null\n    private var objektMarker: List<TrassenGrafikMarker> = emptyList()\n",
        "Objektmarker Property"
    )

if "fun setObjektMarker(marker: List<TrassenGrafikMarker>)" not in gtext:
    gtext = replace_once(
        gtext,
        '''    fun setAktiverFestpunkt(punkt: TrassenGrafikFestpunkt?) {
        aktiverFestpunkt = punkt
        invalidate()
    }

    fun fitToContent() {
''',
        '''    fun setAktiverFestpunkt(punkt: TrassenGrafikFestpunkt?) {
        aktiverFestpunkt = punkt
        invalidate()
    }

    /** Sichtbare Fachpunkte des aktuell bearbeiteten Objekts, z. B. WA/WE/WEZ oder A/B/C/D. */
    fun setObjektMarker(marker: List<TrassenGrafikMarker>) {
        objektMarker = marker
        invalidate()
    }

    fun fitToContent() {
''',
        "Setter Objektmarker"
    )

# Marker nach GVP zeichnen, damit sie sichtbar oben liegen.
gtext = replace_once(
    gtext,
    '''        zeichneAchsendenUndKnoten(canvas, b, isDark)
        zeichneFestpunkte(canvas, b, isDark)
        zeichneAnkerProjektion(canvas, b, isDark)
''',
    '''        zeichneAchsendenUndKnoten(canvas, b, isDark)
        zeichneFestpunkte(canvas, b, isDark)
        zeichneObjektMarker(canvas, b, isDark)
        zeichneAnkerProjektion(canvas, b, isDark)
''',
    "Objektmarker zeichnen"
)

# G1/G2 exakt 1,5x bisheriger Radius.
gtext = replace_once(
    gtext,
    '''            val radius = dpF(8f)
            punktPaint.color = if (isDark) Color.WHITE else Color.rgb(35, 35, 35)
''',
    '''            val radius = dpF(12f)
            punktPaint.color = if (isDark) Color.WHITE else Color.rgb(35, 35, 35)
''',
    "Grenzmarker Radius"
)

gtext = replace_once(
    gtext,
    '''            textPaint.textSize = sp(11f)
            textPaint.color = if (isDark) Color.BLACK else Color.WHITE
''',
    '''            textPaint.textSize = sp(10f)
            textPaint.color = if (isDark) Color.BLACK else Color.WHITE
''',
    "Grenzmarker Textgroesse"
)

gtext = replace_once(
    gtext,
    '''        zeichne(achse.abschnittStartStation, "1")
        zeichne(achse.abschnittEndeStation, "2")
    }

    private fun zeichneKmBeschriftungen''',
    '''        zeichne(achse.abschnittStartStation, "G1")
        zeichne(achse.abschnittEndeStation, "G2")
    }

    private fun zeichneObjektMarker(canvas: Canvas, b: RectF, isDark: Boolean) {
        val sichtbarIds = sichtbareAchseIds
        objektMarker
            .filter { marker -> sichtbarIds == null || marker.achseId == null || marker.achseId in sichtbarIds }
            .forEach { marker ->
                val x = sx(marker.x, b)
                val y = sy(marker.y, b)
                if (x < -40f || x > width + 40f || y < -40f || y > height + 40f) return@forEach
                val radius = dpF(if (marker.aktiv) 11f else 9f)
                punktPaint.color = if (marker.aktiv) {
                    if (isDark) Color.rgb(100, 181, 246) else Color.rgb(25, 118, 210)
                } else {
                    if (isDark) Color.rgb(238, 238, 238) else Color.rgb(55, 55, 55)
                }
                punktRandPaint.color = if (isDark) Color.BLACK else Color.WHITE
                punktRandPaint.strokeWidth = dpF(1.5f)
                canvas.drawCircle(x, y, radius, punktPaint)
                canvas.drawCircle(x, y, radius, punktRandPaint)
                punktRandPaint.strokeWidth = 2f

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = sp(12f)
                textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
                val label = marker.label
                val fm = textPaint.fontMetrics
                val padX = dpF(5f)
                val padY = dpF(3f)
                val w = textPaint.measureText(label) + padX * 2f
                val h = fm.descent - fm.ascent + padY * 2f
                val left = (x + radius + dpF(4f)).coerceAtMost((width - w - dpF(2f)).coerceAtLeast(dpF(2f)))
                val top = (y - h / 2f).coerceIn(dpF(2f), (height - h - dpF(2f)).coerceAtLeast(dpF(2f)))
                labelBgPaint.color = if (isDark) Color.argb(205, 0, 0, 0) else Color.argb(225, 255, 255, 255)
                canvas.drawRoundRect(left, top, left + w, top + h, dpF(5f), dpF(5f), labelBgPaint)
                canvas.drawText(label, left + padX, top + padY - fm.ascent, textPaint)
            }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun zeichneKmBeschriftungen''',
    "Objektmarker Zeichenfunktion"
)

if "data class TrassenGrafikMarker(" not in gtext:
    gtext = replace_once(
        gtext,
        '''data class TrassenGrafikProjektion(
    val punktX: Float,
    val punktY: Float,
    val projX: Float,
    val projY: Float
)

data class TrassenGrafikFestpunkt''',
        '''data class TrassenGrafikProjektion(
    val punktX: Float,
    val punktY: Float,
    val projX: Float,
    val projY: Float
)

data class TrassenGrafikMarker(
    val label: String,
    val x: Float,
    val y: Float,
    val achseId: Long? = null,
    val aktiv: Boolean = false
)

data class TrassenGrafikFestpunkt''',
        "Objektmarker Data Class"
    )

grafik_path.write_text(gtext, encoding="utf-8")
