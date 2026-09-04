import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.tasks.util.PatternFilterable

// TB-Helfer 2.0 / Teststand 010
// Bautagebuch-Bedienung ohne sichtbare Tag-/Nacht-Auswahl.
// Die alte Schichtkennung bleibt intern nur fuer bestehende Dateien/PDFs kompatibel.

val btb010OriginalFragment = file("src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt")
val btb010GeneratedDir = layout.buildDirectory.dir("generated/source/btb010").get().asFile
val btb010GeneratedFragment = file("${btb010GeneratedDir.path}/de/tbhelfer/ui/bautagebuch/BautagebuchFragment010.kt")

val generateBtb010BautagebuchFragment = tasks.register("generateBtb010BautagebuchFragment") {
    group = "build setup"
    description = "Erzeugt BautagebuchFragment fuer Teststand 010 ohne sichtbare Tag-/Nacht-Fuehrung."
    inputs.file(btb010OriginalFragment)
    outputs.file(btb010GeneratedFragment)

    doLast {
        val original = inputs.files.singleFile
        val generated = outputs.files.singleFile
        var text = original.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        fun replaceRequired010(old: String, new: String, label: String) {
            if (text.contains(new)) return
            check(text.contains(old)) { "010-BTB-Integration fehlgeschlagen: $label nicht gefunden" }
            text = text.replaceFirst(old, new)
        }

        fun replaceBetween010(startMarker: String, endMarker: String, replacement: String, label: String) {
            val start = text.indexOf(startMarker)
            val end = text.indexOf(endMarker, start.coerceAtLeast(0))
            check(start >= 0 && end > start) { "010-BTB-Integration fehlgeschlagen: $label nicht gefunden" }
            text = text.substring(0, start) + replacement + text.substring(end)
        }

        // Zusaetzlicher Zustand fuer dieselbe Maske: neuer BTB oder bestehende Kopfdaten bearbeiten.
        replaceRequired010(
            "private var newBtbPreparedWeatherMessage: String? = null",
            """private var newBtbPreparedWeatherMessage: String? = null
private var newBtbPreparedWeatherLat: Double? = null
private var newBtbPreparedWeatherLon: Double? = null
private var newBtbPreparedWeatherSource: WeatherSource = WeatherSource.MANUAL
private var newBtbEditingCurrent: Boolean = false""",
            "Setup-Zustand"
        )

        // Im laufenden BTB bleibt oben nur Zeitraum + Bearbeiten-Knopf. Status/Titel/Quick-Kamera
        // werden ausgeblendet; die fachlichen Punkte 4-8 bleiben unveraendert.
        replaceRequired010(
            """tvNewBtbWeatherLabel = view.requireViewByIdCompat(R.id.tvNewBtbWeatherLabel)

btnQuickCamera.setOnClickListener {
    findNavController().navigate(R.id.kameraFragment)
}
btnQuickNewBtb.setOnClickListener {
    openNewBtbStartMode()
}
btnNewBtbUseDateTime.setOnClickListener {
    newBtbUseDateTime = !newBtbUseDateTime
    renderNewBtbStartMode()
}
""",
            """tvNewBtbWeatherLabel = view.requireViewByIdCompat(R.id.tvNewBtbWeatherLabel)

// 010: kompakter Kopf. Kamera bleibt ueber die normale App-Navigation erreichbar.
view.findViewById<View>(R.id.tvTitleBaustelle)?.visibility = View.GONE
view.findViewById<View>(R.id.tvTitleBautagebuch)?.visibility = View.GONE
tvStatus.visibility = View.GONE
btnQuickCamera.visibility = View.GONE
tvCurrentReportLine2.visibility = View.GONE
tvCurrentReportLine1.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.MATCH_PARENT
)
tvCurrentReportLine1.gravity = android.view.Gravity.CENTER_VERTICAL

tvCurrentReportLine1.textSize = 16f

btnQuickNewBtb.setOnClickListener {
    openCurrentBtbKopfdatenMode()
}
btnNewBtbUseDateTime.setOnClickListener {
    showNewBtbDateTimeDialog()
}
""",
            "Kompakter Kopf und Datumsklick"
        )

        replaceRequired010(
            """btnNewBtbUseWeather.setOnClickListener {
    newBtbUseWeather = !newBtbUseWeather
    renderNewBtbStartMode()
}
""",
            """btnNewBtbUseWeather.setOnClickListener {
    showNewBtbWeatherEditorDialog()
}
""",
            "Wetter-Editor-Klick"
        )

        replaceRequired010(
            """bindNewBtbTap(tvNewBtbWeatherLabel) { showNewBtbWeatherPreviewDialog() }
bindNewBtbTap(tvNewBtbWeatherProposal) { showNewBtbWeatherPreviewDialog() }
""",
            """bindNewBtbTap(tvNewBtbWeatherLabel) { showNewBtbWeatherEditorDialog() }
bindNewBtbTap(tvNewBtbWeatherProposal) { showNewBtbWeatherEditorDialog() }
""",
            "Wetter-Zeilen-Klick"
        )

        replaceRequired010(
            """        setupPmUi(view)
        setTopFieldsCollapsed(true)
        // \"Zurück\"-Button wurde entfernt – Navigation läuft über System-Back bzw. Toolbar
        btnDateTime.setOnClickListener { showDateTimeDropdown() }
""",
            """        setupPmUi(view)
        setTopFieldsCollapsed(true)
        // 010: Im laufenden BTB keine zweite Detailleiste. Alles oben wird ueber + bearbeitet.
        topFieldsToggleRow.visibility = View.GONE
        // \"Zurück\"-Button wurde entfernt – Navigation läuft über System-Back bzw. Toolbar
        btnDateTime.setOnClickListener { openCurrentBtbKopfdatenMode() }
""",
            "Detailleiste ausblenden"
        )

        // Einheitliche Datumsdarstellung, z.B. 04/05.09.2026 · 20:00 – 05:00.
        replaceRequired010(
            "    private fun scheduleUpdateCurrentReportBanner() {",
            """    private fun formatBtbZeitraum(datum: LocalDate, start: String?, ende: String?): String {
        val s = start?.trim().orEmpty()
        val e = ende?.trim().orEmpty()
        val endDatum = if (spansMidnight(s.takeIf { it.isNotBlank() }, e.takeIf { it.isNotBlank() })) datum.plusDays(1) else datum
        val datumText = when {
            endDatum == datum -> datum.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            datum.year == endDatum.year && datum.monthValue == endDatum.monthValue ->
                String.format("%02d/%02d.%02d.%04d", datum.dayOfMonth, endDatum.dayOfMonth, datum.monthValue, datum.year)
            datum.year == endDatum.year ->
                String.format("%02d.%02d/%02d.%02d.%04d", datum.dayOfMonth, datum.monthValue, endDatum.dayOfMonth, endDatum.monthValue, datum.year)
            else -> datum.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "/" + endDatum.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }
        return if (s.isNotBlank() && e.isNotBlank()) datumText + " · " + s + " – " + e else datumText
    }

    private fun scheduleUpdateCurrentReportBanner() {""",
            "Zeitraum-Formatter"
        )

        replaceBetween010(
            "    private fun scheduleUpdateCurrentReportBanner() {",
            "    private fun buildCurrentReportLine2(",
            """    private fun scheduleUpdateCurrentReportBanner() {
        reportBannerJob?.cancel()
        reportBannerJob = lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                tvCurrentReportLine1.text = ""
                tvCurrentReportLine2.text = ""
                return@launch
            }
            tvCurrentReportLine1.text = formatBtbZeitraum(selectedDate, shiftStart, shiftEnd)
            tvCurrentReportLine2.text = ""
            tvCurrentReportLine2.visibility = View.GONE
        }
    }

""",
            "Kopf ohne Tag/Nacht"
        )

        // Datumswahl darf keine zweite Tag-/Nacht-Auswahl mehr oeffnen.
        replaceBetween010(
            "    private fun showDatePicker(onPicked: (() -> Unit)? = null) {",
            "        // Statusmodell für den 7-Tage-Dialog",
            """    private fun showDatePicker(onPicked: (() -> Unit)? = null) {
        val d = selectedDate
        BtbRangeCalendarDialog.show(this, d, 7) { picked ->
            lifecycleScope.launch {
                beginContextSwitch()
                selectedDate = picked
                updateDateTimeButton()
                ladeEintragFallsVorhanden()
                endContextSwitch()
                onPicked?.invoke()
            }
        }
    }

""",
            "Datumswahl ohne Schichtdialog"
        )

        // Bericht oeffnen: nur reale BTBs anzeigen, ein Tipp oeffnet direkt. Schichtkennung bleibt unsichtbar intern.
        replaceBetween010(
            "    private fun showBerichtOeffnenLetzte7TageDialog() {",
            "    private fun findRevisionCountFor(",
            """    private fun showBerichtOeffnenLetzte7TageDialog() {
        lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val eintraege = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.verlaufFuerBaustelle(requireContext(), ctx.id, ctx.name).take(20)
            }
            val density = resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(8))
            }
            val scroll = ScrollView(requireContext()).apply { addView(container) }
            val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Bautagebuch öffnen")
                .setView(scroll)
                .setNegativeButton("Abbrechen", null)
                .create()

            val neu = MaterialButton(requireContext()).apply {
                text = "+ Neues Bautagebuch"
                isAllCaps = false
                minHeight = dp(46)
                setOnClickListener {
                    dlg.dismiss()
                    openNewBtbStartMode()
                }
            }
            container.addView(neu)
            container.addView(Space(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
            })

            if (eintraege.isEmpty()) {
                container.addView(TextView(requireContext()).apply {
                    text = "Noch kein Bautagebuch vorhanden."
                    textSize = 14f
                    setPadding(0, dp(8), 0, dp(8))
                })
            }

            eintraege.forEach { v ->
                val status = when {
                    !v.baselineExists -> "In Bearbeitung"
                    v.entry.istFertig -> "Abgeschlossen"
                    else -> "Abgeschlossen"
                }
                val btn = MaterialButton(requireContext()).apply {
                    text = formatBtbZeitraum(v.datum, v.entry.startUhrzeit, v.entry.endeUhrzeit) + "\n" + status
                    isAllCaps = false
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    minHeight = dp(58)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        dlg.dismiss()
                        lifecycleScope.launch {
                            beginContextSwitch()
                            selectedDate = v.datum
                            selectedSchichtTyp = v.schicht
                            updateDateTimeButton()
                            ladeEintragFallsVorhanden()
                            endContextSwitch()
                        }
                    }
                }
                container.addView(btn)
                container.addView(Space(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
                })
            }
            dlg.show()
        }
    }

""",
            "Direkte BTB-Liste"
        )

        // Neuer BTB bekommt standardmaessig heutiges Datum und einen 10h-Zeitraum; Personal/Maschinen/Wetter
        // starten leer und koennen bewusst aus dem letzten BTB uebernommen werden.
        replaceBetween010(
            "        private fun openNewBtbStartMode() {",
            "        private fun closeNewBtbStartMode() {",
            """        private fun openNewBtbStartMode() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                newBtbEditingCurrent = false
                val start = suggestedNowQuarter()
                newBtbPreparedDate = LocalDate.now()
                newBtbPreparedStart = start
                newBtbPreparedEnd = plusHours(start, 10L)
                newBtbPreparedSchichtTyp = if (spansMidnight(newBtbPreparedStart, newBtbPreparedEnd)) SchichtTyp.NACHT else SchichtTyp.TAG
                newBtbUseDateTime = true
                newBtbUsePersonal = false
                newBtbUseMaschinen = false
                newBtbUseWeather = false
                newBtbPreparedFirmen = emptyList()
                newBtbPreparedWeatherSlots = emptyList()
                newBtbPreparedWeatherMessage = null
                newBtbPreparedWeatherLat = null
                newBtbPreparedWeatherLon = null
                newBtbPreparedWeatherSource = WeatherSource.MANUAL
                val proposal = withContext(Dispatchers.IO) { findNewBtbProposal(ctx) }
                newBtbProposal = proposal
                cardNewBtbStartMode.visibility = View.VISIBLE
                renderNewBtbStartMode()
                scrollRoot.post { (scrollRoot as? ScrollView)?.smoothScrollTo(0, 0) }
            }
        }

        private fun openCurrentBtbKopfdatenMode() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val existing = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                if (existing == null) {
                    openNewBtbStartMode()
                    return@launch
                }
                newBtbEditingCurrent = true
                newBtbPreparedDate = runCatching { LocalDate.parse(existing.datumIso, isoFmt) }.getOrDefault(selectedDate)
                newBtbPreparedStart = existing.startUhrzeit
                newBtbPreparedEnd = existing.endeUhrzeit
                newBtbPreparedSchichtTyp = existing.schicht
                newBtbUseDateTime = true
                newBtbPreparedFirmen = existing.firmen
                newBtbUsePersonal = existing.firmen.any { it.visibleInPersonal && !it.isDeleted }
                newBtbUseMaschinen = existing.firmen.any { it.visibleInMaschinen && !it.isDeleted }
                newBtbPreparedWeatherSlots = (existing.weather?.slots ?: existing.weatherSlots)
                newBtbUseWeather = !newBtbPreparedWeatherSlots.isNullOrEmpty()
                newBtbPreparedWeatherLat = existing.weather?.lat
                newBtbPreparedWeatherLon = existing.weather?.lon
                newBtbPreparedWeatherSource = existing.weather?.source ?: WeatherSource.MANUAL
                newBtbPreparedWeatherMessage = if (newBtbUseWeather) "Wetterwerte gespeichert" else null
                newBtbProposal = withContext(Dispatchers.IO) { findNewBtbProposal(ctx) }
                cardNewBtbStartMode.visibility = View.VISIBLE
                renderNewBtbStartMode()
                scrollRoot.post { (scrollRoot as? ScrollView)?.smoothScrollTo(0, 0) }
            }
        }

""",
            "Neue/aktuelle BTB-Kopfdaten"
        )

        replaceBetween010(
            "        private fun closeNewBtbStartMode() {",
            "        private fun renderNewBtbStartMode() {",
            """        private fun closeNewBtbStartMode() {
            cardNewBtbStartMode.visibility = View.GONE
            newBtbProposal = null
            newBtbUseDateTime = false
            newBtbUsePersonal = false
            newBtbUseMaschinen = false
            newBtbUseWeather = false
            newBtbPreparedDate = null
            newBtbPreparedStart = null
            newBtbPreparedEnd = null
            newBtbPreparedSchichtTyp = null
            newBtbPreparedFirmen = null
            newBtbPreparedWeatherSlots = null
            newBtbPreparedWeatherMessage = null
            newBtbPreparedWeatherLat = null
            newBtbPreparedWeatherLon = null
            newBtbPreparedWeatherSource = WeatherSource.MANUAL
            newBtbEditingCurrent = false
        }

""",
            "Setup schliessen"
        )

        replaceBetween010(
            "        private fun renderNewBtbStartMode() {",
            "        private fun computeAutoWeatherTimesFor(",
            """        private fun renderNewBtbStartMode() {
            val proposal = newBtbProposal
            val content = cardNewBtbStartMode.getChildAt(0) as? ViewGroup
            (content?.getChildAt(0) as? TextView)?.text = if (newBtbEditingCurrent) "BTB-Daten" else "Neues Bautagebuch"
            tvNewBtbHint.text = if (newBtbEditingCurrent) {
                "Datum/Zeit, Personal, Maschinen/Geräte und Wetter können jederzeit nachgetragen werden."
            } else {
                "Datum/Zeit festlegen. Personal, Maschinen/Geräte und Wetter können eingetragen oder aus dem letzten BTB übernommen werden."
            }

            val effectiveDate = newBtbPreparedDate ?: selectedDate
            val effectiveStart = newBtbPreparedStart
            val effectiveEnd = newBtbPreparedEnd
            tvNewBtbDateTimeProposal.text = formatBtbZeitraum(effectiveDate, effectiveStart, effectiveEnd)
            btnNewBtbUseDateTime.isEnabled = true
            btnNewBtbUseDateTime.text = "Bearbeiten"

            val firmen = newBtbPreparedFirmen.orEmpty()
            val personalText = summarizeFirmenForProposal(firmen, personal = true)
            val maschinenText = summarizeFirmenForProposal(firmen, personal = false)
            tvNewBtbPersonalProposal.text = if (personalText == "Kein Vorschlag" && proposal != null) "Noch nichts eingetragen · letzter BTB verfügbar" else personalText
            tvNewBtbMaschinenProposal.text = if (maschinenText == "Kein Vorschlag" && proposal != null) "Noch nichts eingetragen · letzter BTB verfügbar" else maschinenText
            btnNewBtbUsePersonal.isEnabled = true
            btnNewBtbUsePersonal.text = if (firmen.any { it.visibleInPersonal && !it.isDeleted }) "Bearbeiten" else "Eintragen"
            btnNewBtbUseMaschinen.isEnabled = true
            btnNewBtbUseMaschinen.text = if (firmen.any { it.visibleInMaschinen && !it.isDeleted }) "Bearbeiten" else "Eintragen"

            val slots = newBtbPreparedWeatherSlots.orEmpty()
            tvNewBtbWeatherProposal.text = when {
                newBtbPreparedWeatherMessage != null -> newBtbPreparedWeatherMessage!!
                slots.isNotEmpty() -> slots.joinToString(" · ") { it.uhrzeit }
                else -> "Noch nichts eingetragen"
            }
            btnNewBtbUseWeather.isEnabled = true
            btnNewBtbUseWeather.text = if (slots.isNotEmpty()) "Bearbeiten" else "Eintragen"
            btnNewBtbStart.text = if (newBtbEditingCurrent) "Änderungen übernehmen" else "Bautagebuch starten"
        }

""",
            "Startmaske rendern"
        )

        // Neuer BTB darf den letzten BTB nicht implizit kopieren; Uebernahme erfolgt nur ueber den Button im Editor.
        replaceRequired010(
            "        private fun currentNewBtbFirmen(): List<FirmaBlock> = newBtbPreparedFirmen ?: newBtbProposal?.firmen.orEmpty()",
            "        private fun currentNewBtbFirmen(): List<FirmaBlock> = newBtbPreparedFirmen.orEmpty()",
            "Personal/Maschinen explizit uebernehmen"
        )
        replaceRequired010(
            "            val baseFirmen = newBtbPreparedFirmen ?: newBtbProposal?.firmen ?: return emptyList()",
            "            val baseFirmen = newBtbPreparedFirmen ?: return emptyList()",
            "Keine automatische Firmenkopie"
        )

        // Wetter beim Anlegen/Bearbeiten: drei frei editierbare Messpunkte inkl. Niederschlagsmenge und GPS-Bezug.
        replaceBetween010(
            "        private fun showNewBtbWeatherPreviewDialog() {",
            "        private fun refreshNewBtbWeatherSuggestion() {",
            """        private fun showNewBtbWeatherEditorDialog() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val density = resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()
                val outer = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                }
                val gpsText = TextView(requireContext()).apply {
                    text = "GPS für Wetter: wird ermittelt…"
                    textSize = 13f
                    setPadding(0, 0, 0, dp(8))
                }
                outer.addView(gpsText)
                val btnGps = MaterialButton(requireContext()).apply {
                    text = "Standort aktualisieren"
                    isAllCaps = false
                }
                outer.addView(btnGps)

                data class WetterUi(
                    val zeit: MaterialAutoCompleteTextView,
                    val temp: TextInputEditText,
                    val bew: MaterialAutoCompleteTextView,
                    val nied: MaterialAutoCompleteTextView,
                    val mm: TextInputEditText
                )
                val rows = mutableListOf<WetterUi>()
                val baseTimes = computeAutoWeatherTimesFor(newBtbPreparedStart, newBtbPreparedEnd)
                val existing = newBtbPreparedWeatherSlots.orEmpty()
                val slots = (0..2).map { index ->
                    existing.getOrNull(index) ?: WetterSlot(
                        uhrzeit = baseTimes.getOrNull(index) ?: listOf("08:00", "12:00", "16:00")[index],
                        tempC = null,
                        bewoelkung = DEFAULT_BEWOELKUNG,
                        niederschlag = DEFAULT_NIEDERSCHLAG
                    )
                }
                val timeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, buildTimes15())
                val bewAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bewOptions)
                val niedAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, niedOptions)

                fun inputLayout(hint: String, child: View, weight: Float = 1f): com.google.android.material.textfield.TextInputLayout {
                    return com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                        this.hint = hint
                        addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply { marginEnd = dp(6) }
                    }
                }

                slots.forEachIndexed { index, slot ->
                    outer.addView(TextView(requireContext()).apply {
                        text = "Wetterwert " + (index + 1)
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, dp(12), 0, dp(4))
                    })
                    val zeit = MaterialAutoCompleteTextView(requireContext()).apply {
                        setAdapter(timeAdapter)
                        setText(slot.uhrzeit, false)
                        inputType = android.text.InputType.TYPE_NULL
                    }
                    val temp = TextInputEditText(requireContext()).apply {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                        setText(slot.tempC?.toString().orEmpty())
                    }
                    val row1 = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                    row1.addView(inputLayout("Uhrzeit", zeit))
                    row1.addView(inputLayout("Temperatur °C", temp))
                    outer.addView(row1)

                    val bew = MaterialAutoCompleteTextView(requireContext()).apply {
                        setAdapter(bewAdapter)
                        setText(slot.bewoelkung ?: DEFAULT_BEWOELKUNG, false)
                        inputType = android.text.InputType.TYPE_NULL
                    }
                    val nied = MaterialAutoCompleteTextView(requireContext()).apply {
                        setAdapter(niedAdapter)
                        setText(slot.niederschlag ?: DEFAULT_NIEDERSCHLAG, false)
                        inputType = android.text.InputType.TYPE_NULL
                    }
                    val row2 = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                    row2.addView(inputLayout("Bewölkung", bew))
                    row2.addView(inputLayout("Niederschlag", nied))
                    outer.addView(row2)

                    val mm = TextInputEditText(requireContext()).apply {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setText(slot.niederschlagMm?.let { String.format(java.util.Locale.GERMANY, "%.1f", it) }.orEmpty())
                    }
                    val mmLayout = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                        hint = "Niederschlagsmenge mm"
                        addView(mm, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                    outer.addView(mmLayout)
                    rows += WetterUi(zeit, temp, bew, nied, mm)
                }

                fun saveRows(source: WeatherSource) {
                    newBtbPreparedWeatherSlots = rows.mapNotNull { r ->
                        val zeit = r.zeit.text?.toString().orEmpty().trim()
                        if (zeit.isBlank()) return@mapNotNull null
                        WetterSlot(
                            uhrzeit = zeit,
                            tempC = r.temp.text?.toString()?.trim()?.toIntOrNull(),
                            bewoelkung = r.bew.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
                            niederschlag = r.nied.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
                            niederschlagMm = r.mm.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                        )
                    }
                    newBtbUseWeather = newBtbPreparedWeatherSlots?.isNotEmpty() == true
                    newBtbPreparedWeatherSource = source
                    newBtbPreparedWeatherMessage = if (newBtbUseWeather) "Wetterwerte gespeichert" else null
                    renderNewBtbStartMode()
                }

                suspend fun updateGpsText() {
                    val loc = withContext(Dispatchers.IO) { resolveWetterLocation(ctx) }
                    if (loc == null) {
                        gpsText.text = "GPS für Wetter: kein Standort verfügbar"
                    } else {
                        newBtbPreparedWeatherLat = loc.first
                        newBtbPreparedWeatherLon = loc.second
                        gpsText.text = "GPS für Wetter: " + String.format(java.util.Locale.US, "%.6f, %.6f", loc.first, loc.second)
                    }
                }
                updateGpsText()
                btnGps.setOnClickListener { lifecycleScope.launch { updateGpsText() } }

                val scroll = ScrollView(requireContext()).apply { addView(outer) }
                val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Wetter")
                    .setView(scroll)
                    .setNegativeButton("Abbrechen", null)
                    .setNeutralButton("Wetter automatisch laden", null)
                    .setPositiveButton("Übernehmen", null)
                    .create()
                dlg.setOnShowListener {
                    dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        saveRows(WeatherSource.MANUAL)
                        dlg.dismiss()
                    }
                    dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        saveRows(WeatherSource.AUTO)
                        newBtbPreparedWeatherMessage = "Wetter wird automatisch geladen…"
                        renderNewBtbStartMode()
                        dlg.dismiss()
                        refreshNewBtbWeatherSuggestion()
                    }
                }
                dlg.show()
            }
        }

""",
            "Wetter-Editor"
        )

        // Der Vorschlag zeigt keine sichtbare Schichtbezeichnung mehr.
        replaceRequired010(
            """            val sourceDate = runCatching { LocalDate.parse(entry.datumIso, isoFmt).format(deDateFmt) }.getOrElse { entry.datumIso }
            val sourceTyp = if (entry.schicht == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)
""",
            """            val sourceDate = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrDefault(selectedDate)
""",
            "Vorschlag ohne Schichttyp"
        )
        replaceRequired010(
            "                sourceLabel = \"$sourceDate · $sourceTyp\",",
            "                sourceLabel = formatBtbZeitraum(sourceDate, entry.startUhrzeit, entry.endeUhrzeit),",
            "Vorschlagslabel ohne Tag/Nacht"
        )
        replaceRequired010(
            "                val date = selectedDate.minusDays(offset)",
            "                val date = (newBtbPreparedDate ?: selectedDate).minusDays(offset)",
            "Vorschlagsbasisdatum"
        )

        // Start bzw. Bearbeitung sichern. Die interne TAG/NACHT-Kennung wird ausschliesslich aus den Zeiten abgeleitet.
        replaceBetween010(
            "        private fun startPreparedNewBtb() {",
            "        private fun showDateTimeDropdown() {",
            """        private fun startPreparedNewBtb() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val targetDate = newBtbPreparedDate ?: selectedDate
                val startValue = newBtbPreparedStart?.trim().takeUnless { it.isNullOrBlank() }
                val endValue = newBtbPreparedEnd?.trim().takeUnless { it.isNullOrBlank() }
                if (startValue == null || endValue == null) {
                    Snackbar.make(requireView(), "Bitte Datum, Beginn und Ende eintragen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val targetSchicht = if (spansMidnight(startValue, endValue)) SchichtTyp.NACHT else SchichtTyp.TAG
                val firmen = buildPreparedFirmenForNewBtb()
                val weatherSlots = buildPreparedWeatherSlotsForNewBtb()
                val fallbackLocation = if (newBtbUseWeather && (newBtbPreparedWeatherLat == null || newBtbPreparedWeatherLon == null)) {
                    withContext(Dispatchers.IO) { resolveWetterLocation(ctx) }
                } else null
                val weatherLat = newBtbPreparedWeatherLat ?: fallbackLocation?.first
                val weatherLon = newBtbPreparedWeatherLon ?: fallbackLocation?.second
                val now = System.currentTimeMillis()

                beginContextSwitch()
                try {
                    if (newBtbEditingCurrent) {
                        val oldDate = selectedDate
                        val oldSchicht = selectedSchichtTyp
                        val existing = withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, oldDate, oldSchicht)
                        }
                        if (existing == null) {
                            newBtbEditingCurrent = false
                            Snackbar.make(requireView(), "BTB nicht mehr gefunden – bitte neu starten", Snackbar.LENGTH_LONG).show()
                            return@launch
                        }
                        val oldWeather = existing.weather
                        val updated = existing.copy(
                            datumIso = targetDate.format(isoFmt),
                            schicht = targetSchicht,
                            startUhrzeit = startValue,
                            endeUhrzeit = endValue,
                            weather = (oldWeather ?: WeatherBlock()).copy(
                                standEpochMs = if (newBtbPreparedWeatherSource == WeatherSource.AUTO) now else oldWeather?.standEpochMs,
                                source = newBtbPreparedWeatherSource,
                                lat = weatherLat ?: oldWeather?.lat,
                                lon = weatherLon ?: oldWeather?.lon,
                                slots = weatherSlots
                            ),
                            weatherSlots = weatherSlots,
                            firmen = firmen,
                            personal = firmenToLegacyLines(firmen, PmSection.PERSONAL),
                            geraete = firmenToLegacyLines(firmen, PmSection.MASCHINEN),
                            geaendertAmEpochMs = now
                        )
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.speichern(requireContext(), updated)
                            if (oldDate != targetDate || oldSchicht != targetSchicht) {
                                BautagebuchSpeicher.loeschen(requireContext(), ctx.id, ctx.name, oldDate, oldSchicht)
                            }
                        }
                        selectedDate = targetDate
                        selectedSchichtTyp = targetSchicht
                        shiftStart = startValue
                        shiftEnd = endValue
                        currentBtbStartEpochMs = existing.erstelltAmEpochMs
                        closeNewBtbStartMode()
                        ladeEintragFallsVorhanden()
                        Snackbar.make(requireView(), "BTB-Daten aktualisiert", Snackbar.LENGTH_SHORT).show()
                    } else {
                        selectedDate = targetDate
                        selectedSchichtTyp = targetSchicht
                        currentBtbStartEpochMs = now
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.archivierenUndLeeren(requireContext(), ctx.id, ctx.name, targetDate, targetSchicht)
                            BtbPhotoSelectionStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                            BtbPhotoTextOverrideStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                            BtbPhotoStartOverrideStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                            BtbPhotoKmOverrideStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                            val entry = BautagebuchEintrag(
                                baustelleName = ctx.name,
                                baustelleId = ctx.id,
                                datumIso = targetDate.format(isoFmt),
                                schicht = targetSchicht,
                                strecke = ctx.strecke,
                                startUhrzeit = startValue,
                                endeUhrzeit = endValue,
                                weather = WeatherBlock(
                                    standEpochMs = if (newBtbPreparedWeatherSource == WeatherSource.AUTO) now else null,
                                    source = newBtbPreparedWeatherSource,
                                    lat = weatherLat,
                                    lon = weatherLon,
                                    slots = weatherSlots
                                ),
                                weatherSlots = weatherSlots,
                                firmen = firmen,
                                personal = firmenToLegacyLines(firmen, PmSection.PERSONAL),
                                geraete = firmenToLegacyLines(firmen, PmSection.MASCHINEN),
                                includeFotos = true,
                                includeBtbs = true,
                                maxFotosInPdf = currentMaxFotosInPdf,
                                erstelltAmEpochMs = now,
                                geaendertAmEpochMs = now
                            )
                            BautagebuchSpeicher.speichern(requireContext(), entry)
                        }
                        shiftStart = startValue
                        shiftEnd = endValue
                        closeNewBtbStartMode()
                        ladeEintragFallsVorhanden()
                        Snackbar.make(requireView(), "Bautagebuch gestartet", Snackbar.LENGTH_SHORT).show()
                    }
                } finally {
                    endContextSwitch()
                }
            }
        }

""",
            "BTB starten/bearbeiten"
        )

        generated.parentFile.mkdirs()
        generated.writeText(text, Charsets.UTF_8)
    }
}

pluginManager.withPlugin("com.android.application") {
    val android = extensions.getByType(ApplicationExtension::class.java)
    android.sourceSets.getByName("main") {
        (kotlin as PatternFilterable).exclude("de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt")
        kotlin.directories.add(btb010GeneratedDir.path)
    }

    // VersionCode nur auf Variantenebene ueberschreiben, damit app/build.gradle.kts nicht erneut
    // als grosse Generator-Datei angefasst werden muss.
    val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output -> output.versionCode.set(200010) }
    }
}

tasks.configureEach {
    if (
        name == "preBuild" ||
        name.startsWith("ksp") ||
        (name.startsWith("compile") && name.endsWith("Kotlin"))
    ) {
        dependsOn(generateBtb010BautagebuchFragment)
    }
}
