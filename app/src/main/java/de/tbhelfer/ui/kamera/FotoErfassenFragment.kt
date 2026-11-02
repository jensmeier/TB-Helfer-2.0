package de.tbhelfer.ui.kamera

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.util.TypedValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.datenbank.Indikator
import de.tbhelfer.streckenobjekte.BaustellenObjektArt
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import de.tbhelfer.streckenobjekte.StreckenObjektSpeicher
import de.tbhelfer.einstellungen.dataStore
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BautagebuchEintrag
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import de.tbhelfer.ui.bautagebuch.FirmaBlock
import de.tbhelfer.ui.KeyboardAutoScroll
import de.tbhelfer.ui.overlay.OverlayRenderer
import de.tbhelfer.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.util.ImageExifUtil
import de.tbhelfer.ui.baustelle.MangelStatusSpeicher
import de.tbhelfer.ui.baustelle.MaengellisteExport
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher

class FotoErfassenFragment : Fragment(R.layout.fragment_foto_erfassen) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Patch45h: zentrale IME-/Tastaturhilfe auch fuer diese lange Foto-Maske sicher aktivieren.
        KeyboardAutoScroll.install(requireActivity())

        val args = requireArguments()
        var dateipfad = args.getString(ARG_DATEIPFAD) ?: run {
            Toast.makeText(requireContext(), "Dateipfad fehlt", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        val baustelleId = args.getLong(ARG_BAUSTELLE_ID)
        val knotenId = args.getLong(ARG_KNOTEN_ID, -1L).takeIf { it > 0 }
        val lat = args.getDouble(ARG_LAT)
        val lon = args.getDouble(ARG_LON)
        val gpsAlt = if (args.containsKey(ARG_ALT)) args.getDouble(ARG_ALT) else null
        val kmVorschlag = args.getDouble(ARG_KM, Double.NaN).takeIf { !it.isNaN() }
        // kmSuffix (-0/-1/-2) ist in der Foto-Maske nicht mehr sichtbar/auswählbar.
        // Wenn der Wert aus den Args kommt, übernehmen wir ihn – sonst Standard: 0.
        val suffixIndex = when (val v = args.get(ARG_KM_SUFFIX)) {
            is Int -> v
            is Double -> v.toInt()
            is Float -> v.toInt()
            is Long -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }
        val achsabstand = args.getDouble(ARG_ACHsA, Double.NaN).takeIf { !it.isNaN() }
        val indikator = Indikator.fromString(args.getString(ARG_INDIKATOR)) ?: Indikator.ROT
        val nachweisMode = args.getBoolean(ARG_NACHWEIS_MODE, false)
        val nachweisMangelKey = args.getString(ARG_NACHWEIS_MANGEL_KEY).orEmpty()
        val nachweisStatusName = args.getString(ARG_NACHWEIS_STATUS).orEmpty()
        val mangelFotoArtName = args.getString(ARG_MANGEL_FOTO_ART).orEmpty()
        val abnahmeMode = args.getBoolean(ARG_ABNAHME_MODE, false)
        val abnahmeId = args.getString(ARG_ABNAHME_ID).orEmpty()
        val abnahmeBildArtName = args.getString(ARG_ABNAHME_BILD_ART).orEmpty()
        val abnahmeBildArt = runCatching { AbnahmeSpeicher.BildArt.valueOf(abnahmeBildArtName) }.getOrDefault(AbnahmeSpeicher.BildArt.SONSTIGES)
        var zielMangelKey = nachweisMangelKey
        var zielFotoArt = runCatching { MangelStatusSpeicher.FotoArt.valueOf(mangelFotoArtName) }.getOrElse {
            when (runCatching { MangelStatusSpeicher.Status.valueOf(nachweisStatusName) }.getOrNull()) {
                MangelStatusSpeicher.Status.IN_ARBEIT -> MangelStatusSpeicher.FotoArt.IN_ARBEIT
                MangelStatusSpeicher.Status.ERLEDIGT -> MangelStatusSpeicher.FotoArt.ERLEDIGT
                else -> MangelStatusSpeicher.FotoArt.MANGEL
            }
        }
        var maengelAuswahlSchonGezeigt = false
        val prefillFirma = args.getString(ARG_PREFILL_FIRMA).orEmpty()
        val prefillKurztext = args.getString(ARG_PREFILL_KURZTEXT).orEmpty()
        val prefillNotiz = args.getString(ARG_PREFILL_NOTIZ).orEmpty()
        val trassenObjektKennung = args.getString(ARG_OBJEKT_KENNUNG).orEmpty().trim()
        val gvpKurz = args.getString(ARG_GVP_KURZ).orEmpty().trim()
        val gvpMastVorschlag = args.getString(ARG_GVP_MAST).orEmpty().trim()
        val apKorVorschlag = args.getString(ARG_AP_KOR).orEmpty().trim()

        val img = view.findViewById<ImageView>(R.id.imgVorschau)
        val txtInfo = view.findViewById<TextView>(R.id.txtInfo)
        val txtDatumZeit = view.findViewById<TextView>(R.id.txtDatumZeit)
        val txtWarnKm = view.findViewById<TextView>(R.id.txtWarnKm)
        val edtKm = view.findViewById<EditText>(R.id.edtKm)
        val txtGvpMastLabel = view.findViewById<TextView>(R.id.txtGvpMastLabel)
        val edtGvpMastValue = view.findViewById<EditText>(R.id.edtGvpMastValue)
        val txtApKorLabel = view.findViewById<TextView>(R.id.txtApKorLabel)
        val edtApKorValue = view.findViewById<EditText>(R.id.edtApKorValue)
        val actFirma = view.findViewById<AutoCompleteTextView>(R.id.actFirma)
        val spBildtyp = view.findViewById<Spinner>(R.id.spBildtyp)
        val layoutObjektBereich = view.findViewById<View>(R.id.layoutObjektBereich)
        val spObjektBereich = view.findViewById<Spinner>(R.id.spObjektBereich)
        val objektBereichTrenner = "────────────"
        var objektBereichOptionen: List<String> = listOf("Allgemein")
        val actKurz = view.findViewById<AutoCompleteTextView>(R.id.actKurztext)
        val edtNotiz = view.findViewById<EditText>(R.id.edtNotiz)
        val scrollView = view.findViewById<ScrollView>(R.id.scrollFotoErfassen)

        val btnAbbrechen = view.findViewById<Button>(R.id.btnAbbrechen)
        val btnSpeichern = view.findViewById<Button>(R.id.btnSpeichern)

        val baseScrollPaddingLeft = scrollView.paddingLeft
        val baseScrollPaddingTop = scrollView.paddingTop
        val baseScrollPaddingRight = scrollView.paddingRight
        val baseScrollPaddingBottom = scrollView.paddingBottom
        scrollView.clipToPadding = false
        requireActivity().window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        var selectedBildtypForUi: Bildtyp = Bildtyp.FOTODOKUMENTATION

        fun themeColor(attr: Int, fallback: Int): Int {
            val tv = TypedValue()
            val ok = requireContext().theme.resolveAttribute(attr, tv, true)
            if (!ok) return fallback
            return if (tv.resourceId != 0) runCatching { requireContext().getColor(tv.resourceId) }.getOrDefault(fallback) else tv.data
        }
        fun surfaceColor(): Int = themeColor(com.google.android.material.R.attr.colorSurface, Color.WHITE)
        fun backgroundColor(): Int = themeColor(android.R.attr.colorBackground, Color.WHITE)
        fun onSurfaceColor(): Int = themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        fun secondaryTextColor(): Int = runCatching { requireContext().getColor(R.color.tb_text_secondary) }.getOrDefault(Color.DKGRAY)

        fun aktuellerBildtyp(): Bildtyp = when {
            nachweisMode -> Bildtyp.MAENGEL_SCHAEDEN
            abnahmeMode -> Bildtyp.ABNAHMEN
            else -> selectedBildtypForUi
        }

        fun isMangelBildtyp(): Boolean = aktuellerBildtyp() == Bildtyp.MAENGEL_SCHAEDEN

        fun updateFotokommentarVisibility() {
            // 200056: notiz ist der sichtbare Langtext/BTB-Text für jedes Foto.
            // Für Mängel/Abnahmen bleibt derselbe Datenwert erhalten; nur der Hinweis wird angepasst.
            edtNotiz.visibility = View.VISIBLE
            edtNotiz.hint = when {
                abnahmeMode -> "Bemerkung / Langtext (optional)"
                isMangelBildtyp() -> "Mangelbeschreibung / Langtext (optional)"
                else -> "Langtext / BTB-Text (optional)"
            }
        }

        fun scrollFieldIntoView(target: View) {
            fun reveal() {
                val rect = android.graphics.Rect()
                target.getDrawingRect(rect)
                runCatching { scrollView.offsetDescendantRectToMyCoords(target, rect) }
                val y = (rect.bottom - scrollView.height / 3).coerceAtLeast(0)
                scrollView.smoothScrollTo(0, y)
            }
            scrollView.post { reveal() }
            scrollView.postDelayed({ reveal() }, 220L)
            scrollView.postDelayed({ reveal() }, 480L)
        }

        // Zusaetzlicher lokaler Fallback fuer die unteren Eingabefelder der Foto-Maske.
        // Die zentrale KeyboardAutoScroll-Logik bleibt die Hauptloesung; dieser Fallback
        // hilft insbesondere auf Geraeten, die die Tastatur als Overlay anzeigen.
        listOf<View>(actKurz, edtNotiz, edtGvpMastValue, edtApKorValue)
            .forEach { target ->
                target.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) scrollFieldIntoView(target)
                }
            }

        viewLifecycleOwner.lifecycleScope.launch {
            val letzterKurztext = runCatching {
                requireContext().dataStore.data.first()[KEY_LETZTE_KURZBESCHREIBUNG]
            }.getOrNull().orEmpty().trim()
            if (letzterKurztext.isNotBlank() && actKurz.text?.toString().isNullOrBlank()) {
                actKurz.setText(letzterKurztext, false)
                actKurz.setSelection(letzterKurztext.length)
            }
        }

        // Firmen-Quelle: 1) aktuelles BTB (heutiges Datum) + 2) bisherige Fotos (fallback)
        // Ziel: beim Tippen/Antippen eine Dropdown-Liste der im BTB angelegten Firmen zeigen.
        val btbFirmenLower = mutableSetOf<String>()
        val btbFirmenDisplay = linkedSetOf<String>()
        val fotoFirmenDisplay = linkedSetOf<String>()
        var btbEntryAktuell: BautagebuchEintrag? = null
        var btbBaustellenName: String? = null

        fun istBtbJetztAktiv(entry: BautagebuchEintrag, now: LocalDateTime = LocalDateTime.now()): Boolean {
            val datum = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull() ?: return false
            val hm = DateTimeFormatter.ofPattern("H:mm", Locale.GERMANY)
            val start = runCatching { java.time.LocalTime.parse(entry.startUhrzeit?.trim().orEmpty(), hm) }.getOrNull() ?: return false
            val ende = runCatching { java.time.LocalTime.parse(entry.endeUhrzeit?.trim().orEmpty(), hm) }.getOrNull() ?: return false
            val startDt = LocalDateTime.of(datum, start)
            val endeDatum = if (ende.isAfter(start)) datum else datum.plusDays(1)
            val endeDt = LocalDateTime.of(endeDatum, ende)
            return !now.isBefore(startDt) && !now.isAfter(endeDt)
        }

        // Preview: downsampled, damit keine OOM/Slowdowns bei großen Fotos auftreten
        ThumbnailLoader.loadInto(
            imageView = img,
            filePath = dateipfad,
            placeholderResId = android.R.drawable.ic_menu_report_image,
            reqW = 960,
            reqH = 960
        )

        // Datum/Zeit anzeigen (wird so auch im Overlay genutzt)
        val dtNow = LocalDateTime.now()
        val dtFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm", Locale.GERMANY)
        val datumZeitText = dtNow.format(dtFmt)
        txtDatumZeit.text = "Datum/Zeit: $datumZeitText"

        // Warnung: wenn Achsabstand > 5m => km muss als "manuell" gelten
        if (achsabstand != null && achsabstand > 5.0) {
            txtWarnKm.visibility = View.VISIBLE
            txtWarnKm.text = "Achtung: Abstand zur berechneten Achse > 3 m (%.1f m). km bitte prüfen/ggf. manuell eintragen.".format(Locale.GERMANY, achsabstand)
        }

        val bildtypen = listOf(
            Bildtyp.ABNAHMEN,
            Bildtyp.EINWEISUNG_UNTERWEISUNG,
            Bildtyp.BESONDERHEITEN,
            Bildtyp.MAENGEL_SCHAEDEN,
            Bildtyp.FOTODOKUMENTATION
        )

        // ✅ Wichtig: Adapter direkt mit Enum befüllen (kein anzeigeText)
        val bildtypAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            bildtypen
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spBildtyp.adapter = bildtypAdapter
        spBildtyp.setSelection(bildtypen.indexOf(Bildtyp.FOTODOKUMENTATION).coerceAtLeast(0))
        if (nachweisMode) {
            selectedBildtypForUi = Bildtyp.MAENGEL_SCHAEDEN
            spBildtyp.setSelection(bildtypen.indexOf(Bildtyp.MAENGEL_SCHAEDEN).coerceAtLeast(0))
            spBildtyp.isEnabled = false
        } else if (abnahmeMode) {
            selectedBildtypForUi = Bildtyp.ABNAHMEN
            spBildtyp.setSelection(bildtypen.indexOf(Bildtyp.ABNAHMEN).coerceAtLeast(0))
            spBildtyp.isEnabled = false
        } else {
            selectedBildtypForUi = Bildtyp.FOTODOKUMENTATION
        }
        updateFotokommentarVisibility()

        // Patch 31: Vorschlags-km aus Kamera/Trasse exakt mit 3 Nachkommastellen übernehmen.
        // Nicht auf 10 m runden, damit die Foto-Maske genau den Wert aus dem Kamera-Overlay anzeigt.
        val initialKmText = kmVorschlag?.let { String.format(Locale.US, "%.3f", it) }
        initialKmText?.let {
            edtKm.setText(it)
            edtKm.setSelection(it.length)
        }

        if (gvpMastVorschlag.isNotBlank()) {
            txtGvpMastLabel.visibility = View.VISIBLE
            edtGvpMastValue.visibility = View.VISIBLE
            edtGvpMastValue.setText(gvpMastVorschlag)
        }
        if (apKorVorschlag.isNotBlank()) {
            txtApKorLabel.visibility = View.VISIBLE
            edtApKorValue.visibility = View.VISIBLE
            edtApKorValue.setText(apKorVorschlag)
        }

        if (nachweisMode || abnahmeMode) {
            if (prefillFirma.isNotBlank()) {
                actFirma.setText(prefillFirma, false)
                actFirma.setSelection(prefillFirma.length)
            }
            if (prefillKurztext.isNotBlank()) {
                actKurz.setText(prefillKurztext, false)
                actKurz.setSelection(prefillKurztext.length)
            }
            if (prefillNotiz.isNotBlank()) edtNotiz.setText(prefillNotiz)
        }

        // kmManuell: wenn kein Vorschlag oder zu weit weg -> manuell
        var kmManuell = (kmVorschlag == null) || (achsabstand != null && achsabstand > 5.0)
        edtKm.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollFieldIntoView(edtKm)
            } else {
                // wenn Nutzer was geändert hat, als manuell markieren
                val now = edtKm.text.toString().trim()
                if (initialKmText != null && now.isNotBlank() && now != initialKmText) kmManuell = true
                if (initialKmText == null && now.isNotBlank()) kmManuell = true
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Autovervollständigung aus vorhandenen Fotos
            runCatching {
                val firmen = ServiceLocator.fotoRepository.alleFirmen()
                if (firmen.isNotEmpty()) {
                    firmen
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach {
                            fotoFirmenDisplay.add(it)
                        }
                }
            }

            // Patch45g: Firmen ausschließlich aus dem aktuell passenden BTB laden.
            // Berücksichtigt auch eine Nachtschicht, die am Vortag begonnen hat.
            runCatching {
                val b = withContext(Dispatchers.IO) {
                    ServiceLocator.baustellenRepository.baustelleMitId(baustelleId).first()
                }
                val name = b?.baustellenname?.trim().orEmpty()
                if (name.isNotBlank()) {
                    btbBaustellenName = name
                    val heute = LocalDate.now()
                    val gestern = heute.minusDays(1)

                    suspend fun ladeBtb(datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? =
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.laden(requireContext(), baustelleId, name, datum, schicht)
                                ?: BautagebuchSpeicher.laden(requireContext(), name, datum, schicht)
                        }

                    val kandidaten = listOfNotNull(
                        ladeBtb(heute, SchichtTyp.TAG),
                        ladeBtb(heute, SchichtTyp.NACHT),
                        ladeBtb(gestern, SchichtTyp.NACHT)
                    ).distinctBy { "${it.datumIso}|${it.schicht.name}" }

                    btbEntryAktuell = kandidaten
                        .filter { istBtbJetztAktiv(it) }
                        .maxByOrNull { it.geaendertAmEpochMs }
                        ?: kandidaten
                            .filter { it.datumIso == heute.toString() }
                            .maxByOrNull { it.geaendertAmEpochMs }
                        ?: kandidaten.maxByOrNull { it.geaendertAmEpochMs }

                    btbEntryAktuell?.firmen.orEmpty()
                        .filter { !it.isDeleted }
                        .map { it.name.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase(Locale.ROOT) }
                        .forEach { firmaName ->
                            val lower = firmaName.lowercase(Locale.ROOT)
                            btbFirmenLower.add(lower)
                            btbFirmenDisplay.add(firmaName)
                        }
                }
            }

            // Adapter final setzen:
            // - primär BTB-Firmen (heute)
            // - falls leer: Foto-Firmen (global)
            runCatching {
                val list = (if (btbEntryAktuell != null) btbFirmenDisplay else fotoFirmenDisplay)
                    .toList()
                    .distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                val a = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)
                actFirma.setAdapter(a)
                actFirma.threshold = 0

                val letzteFirma = runCatching {
                    requireContext().dataStore.data.first()[KEY_LETZTE_FIRMA]
                }.getOrNull().orEmpty().trim()

                if (!nachweisMode) {
                    // Aktuelles BTB hat Vorrang: alte Firmen niemals still weiterverwenden.
                    val aktuellerText = actFirma.text?.toString().orEmpty().trim()
                    val gueltigerAktuellerText = list.firstOrNull { it.equals(aktuellerText, ignoreCase = true) }
                    val gueltigeLetzteFirma = list.firstOrNull { it.equals(letzteFirma, ignoreCase = true) }
                    val auswahl = when {
                        gueltigerAktuellerText != null -> gueltigerAktuellerText
                        gueltigeLetzteFirma != null -> gueltigeLetzteFirma
                        btbFirmenDisplay.size == 1 -> btbFirmenDisplay.first()
                        btbFirmenDisplay.isNotEmpty() -> ""
                        btbEntryAktuell != null -> ""
                        else -> letzteFirma.takeIf { it.isNotBlank() } ?: ""
                    }
                    actFirma.setText(auswahl, false)
                    if (auswahl.isNotBlank()) actFirma.setSelection(auswahl.length)
                    if (btbEntryAktuell != null && auswahl.isBlank()) {
                        actFirma.hint = if (btbFirmenDisplay.isNotEmpty()) {
                            "Firma aus aktuellem BTB auswählen"
                        } else {
                            "Firma für aktuelles BTB eingeben"
                        }
                    }
                }

                // UX: beim Antippen direkt Dropdown öffnen
                actFirma.setOnClickListener { actFirma.showDropDown() }
                actFirma.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        actFirma.showDropDown()
                        scrollFieldIntoView(actFirma)
                    }
                }


                // Visuell hervorheben, wenn Firmen aus dem BTB (heute) verfügbar sind
                if (btbFirmenDisplay.isNotEmpty()) {
                    ViewCompat.setBackgroundTintList(
                        actFirma,
                        ColorStateList.valueOf(Color.parseColor("#C8E6C9"))
                    )
                    actFirma.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_24, 0)
                } else {
                    // zurücksetzen (Standard)
                    ViewCompat.setBackgroundTintList(actFirma, null)
                    actFirma.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }

            runCatching {
                val kurz = ServiceLocator.fotoRepository.alleKurztexte()
                if (kurz.isNotEmpty()) {
                    val a = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, kurz)
                    actKurz.setAdapter(a)
                    actKurz.threshold = 1
                }
            }

            val b = ServiceLocator.baustellenRepository.baustelleMitId(baustelleId).first()
            val streckeOderBahnhof = b?.streckennummer?.takeIf { it.isNotBlank() } ?: b?.bahnhof ?: "-"
            val gleisOderWeiche = b?.weiche?.takeIf { it.isNotBlank() } ?: b?.gleis

            // Unterbaustelle / Objekt (falls Knoten vorhanden)
            val knoten = knotenId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
            val untertitel = BaustellenObjektLogik.unterbaustellenName(knoten)
            val objektArt = BaustellenObjektLogik.art(knoten)
            objektBereichOptionen = when (objektArt) {
                BaustellenObjektArt.WEICHE -> {
                    val hatKm = StreckenObjektSpeicher.hatVerwendbareKilometrierung(
                        requireContext(), knoten?.bahnhofName, knoten?.gleisOderWeiche ?: knoten?.knotenName,
                        StreckenObjektSpeicher.Typ.WEICHE
                    )
                    buildList {
                        addAll(listOf("Allgemein", "Zungenbereich", "Mittelteil", "Herzstückbereich", "Endteil"))
                        if (!hatKm) {
                            add(objektBereichTrenner)
                            addAll(listOf("60 m vor WA", "60 m nach WE", "60 m nach WEZ"))
                        }
                    }
                }
                BaustellenObjektArt.DKW_KREUZUNG -> listOf("Allgemein", "Mitte", "Endteil")
                else -> listOf("Allgemein")
            }
            if (objektArt == BaustellenObjektArt.WEICHE || objektArt == BaustellenObjektArt.DKW_KREUZUNG) {
                layoutObjektBereich.visibility = View.VISIBLE
                spObjektBereich.adapter = ObjektBereichSpinnerAdapter(
                    requireContext(), objektBereichOptionen, objektBereichTrenner
                )
                spObjektBereich.setSelection(0)
            } else {
                layoutObjektBereich.visibility = View.GONE
            }


            txtInfo.text = buildString {
                // Hauptbaustelle oben
                append("Baustelle: ${b?.baustellenname ?: baustelleId}\n")
                // Unterbaustelle in eigener Zeile (nicht in Klammern)
                if (!untertitel.isNullOrBlank()) append("Unterbaustelle: $untertitel\n")
                append("Strecke/Bahnhof: $streckeOderBahnhof\n")
                if (!gleisOderWeiche.isNullOrBlank()) append("Gleis/Weiche (Vorgabe): $gleisOderWeiche\n")
                append("GPS: ${String.format(Locale.US, "%.6f", lat)}, ${String.format(Locale.US, "%.6f", lon)}\n")
                achsabstand?.let { append("Achsabstand: ${String.format(Locale.US, "%.1f", it)} m\n") }
                append("Indikator: ${indikator.name}")
            }
        }

        fun fotoArtText(art: MangelStatusSpeicher.FotoArt): String = when (art) {
            MangelStatusSpeicher.FotoArt.MANGEL -> "Mangel"
            MangelStatusSpeicher.FotoArt.IN_ARBEIT -> "In Arbeit"
            MangelStatusSpeicher.FotoArt.ERLEDIGT -> "Erledigt"
        }

        fun frageFotoArt(title: String, callback: (MangelStatusSpeicher.FotoArt) -> Unit) {
            val werte = arrayOf("Mangel / weitere Ansicht", "In Arbeit", "Erledigt / abgestellt")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setItems(werte) { _, which ->
                    val art = when (which) {
                        1 -> MangelStatusSpeicher.FotoArt.IN_ARBEIT
                        2 -> MangelStatusSpeicher.FotoArt.ERLEDIGT
                        else -> MangelStatusSpeicher.FotoArt.MANGEL
                    }
                    callback(art)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        fun zeigeMangelAuswahlMitVorschau(items: List<Pair<Int, MaengellisteExport.MangelItem>>) {
            val ctx = requireContext()
            lateinit var dialog: androidx.appcompat.app.AlertDialog

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(10))
                background = android.graphics.drawable.ColorDrawable(backgroundColor())
            }
            root.addView(TextView(ctx).apply {
                text = "Vorhandene Mängel gefunden"
                textSize = 21f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurfaceColor())
            })
            root.addView(TextView(ctx).apply {
                text = "Gleiche Baustelle + gleiche Unterbaustelle. Wähle einen vorhandenen Mangel oder erstelle einen neuen Mangel."
                textSize = 14f
                setTextColor(secondaryTextColor())
                setPadding(0, dp(4), 0, dp(10))
            })

            val scroll = ScrollView(ctx).apply {
                isFillViewport = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            val liste = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(10))
            }
            scroll.addView(liste)
            root.addView(scroll)

            fun nummerText(nr: Int) = nr.toString().padStart(3, '0')

            items.forEach { (nr, item) ->
                val status = MangelStatusSpeicher.laden(ctx, item.key).status
                val card = MaterialCardView(ctx).apply {
                    radius = dp(14).toFloat()
                    strokeWidth = dp(2)
                    setStrokeColor(MaengellisteExport.statusColor(status))
                    setCardBackgroundColor(surfaceColor())
                    useCompatPadding = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(12) }
                    setOnClickListener {
                        dialog.dismiss()
                        frageFotoArt("Foto zu Mangel ${nummerText(nr)} hinzufügen") { art ->
                            zielMangelKey = item.key
                            zielFotoArt = art
                            if (actFirma.text.isNullOrBlank() && item.firma != "-") actFirma.setText(item.firma, false)
                            if (actKurz.text.isNullOrBlank()) actKurz.setText(item.beschreibung.take(90), false)
                            item.hauptFoto?.km?.let { kmWert ->
                                if (edtKm.text.isNullOrBlank()) edtKm.setText(String.format(Locale.US, "%.3f", kmWert))
                            }
                            Toast.makeText(ctx, "Foto wird Mangel ${nummerText(nr)} zugeordnet", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                val content = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }

                val thumb = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(190)
                    ).apply { bottomMargin = dp(10) }
                }
                val haupt = item.hauptFoto
                if (haupt != null) ThumbnailLoader.loadInto(thumb, haupt.dateipfad, android.R.drawable.ic_menu_report_image, 720, 520)
                else thumb.setImageResource(android.R.drawable.ic_menu_report_image)
                content.addView(thumb)

                content.addView(TextView(ctx).apply {
                    text = "Mangel ${nummerText(nr)} · ${MaengellisteExport.statusText(status)}"
                    textSize = 17f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(onSurfaceColor())
                })
                content.addView(TextView(ctx).apply {
                    text = item.beschreibung
                    textSize = 15f
                    setTextColor(onSurfaceColor())
                    setPadding(0, dp(2), 0, dp(2))
                })
                content.addView(TextView(ctx).apply {
                    text = "Firma: ${item.firma}\nBereich: ${item.bereich}\nFotos: Mangel ${MaengellisteExport.countArt(item, MangelStatusSpeicher.FotoArt.MANGEL)} · in Arbeit ${MaengellisteExport.countArt(item, MangelStatusSpeicher.FotoArt.IN_ARBEIT)} · erledigt ${MaengellisteExport.countArt(item, MangelStatusSpeicher.FotoArt.ERLEDIGT)}"
                    textSize = 13f
                    setTextColor(secondaryTextColor())
                })
                content.addView(TextView(ctx).apply {
                    text = "Antippen zum Zuordnen"
                    textSize = 13f
                    setTextColor(Color.rgb(25, 95, 210))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(8), 0, 0)
                })

                card.addView(content)
                liste.addView(card)
            }

            val buttons = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                setPadding(0, dp(8), 0, 0)
            }
            val btnCancel = MaterialButton(ctx).apply {
                text = "Abbrechen"
                textSize = 13f
                setOnClickListener { dialog.dismiss() }
            }
            val btnNew = MaterialButton(ctx).apply {
                text = "Neuer Mangel"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dp(10) }
                setOnClickListener {
                    zielMangelKey = ""
                    zielFotoArt = MangelStatusSpeicher.FotoArt.MANGEL
                    Toast.makeText(ctx, "Neuer Mangel wird erstellt", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            buttons.addView(btnCancel)
            buttons.addView(btnNew)
            root.addView(buttons)

            dialog = MaterialAlertDialogBuilder(ctx)
                .setView(root)
                .create()
            dialog.setOnShowListener {
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor()))
                dialog.window?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
            }
            dialog.show()
        }

        fun pruefeVorhandeneMaengel() {
            if (nachweisMode || maengelAuswahlSchonGezeigt) return
            maengelAuswahlSchonGezeigt = true
            viewLifecycleOwner.lifecycleScope.launch {
                val alle = withContext(Dispatchers.IO) { MaengellisteExport.ladeItems(requireContext().applicationContext, baustelleId, btbBaustellenName ?: "Baustelle") }
                val passend = alle.mapIndexed { index, item -> (index + 1) to item }
                    .filter { (_, item) ->
                        val status = MangelStatusSpeicher.laden(requireContext(), item.key).status
                        val sameKnoten = item.hauptFoto?.baustellenKnotenId == knotenId
                        sameKnoten && (status == MangelStatusSpeicher.Status.OFFEN || status == MangelStatusSpeicher.Status.IN_ARBEIT)
                    }
                if (passend.isNotEmpty()) zeigeMangelAuswahlMitVorschau(passend)
            }
        }

        spBildtyp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val typ = bildtypen.getOrNull(position)
                selectedBildtypForUi = typ ?: Bildtyp.FOTODOKUMENTATION
                updateFotokommentarVisibility()
                if (typ == Bildtyp.MAENGEL_SCHAEDEN) pruefeVorhandeneMaengel()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnAbbrechen.setOnClickListener {
            runCatching { File(dateipfad).delete() }
            findNavController().navigateUp()
        }

        fun saveFoto(
            km: Double?,
            firma: String,
            kurz: String,
            notiz: String?,
            objektKennung: String?,
            objektBereich: String?,
            bildtyp: Bildtyp,
            kmManuell: Boolean,
            optionalLabels: List<String>,
            optionalValues: List<String?>,
            andMaybeAddFirmaToBtb: ((String) -> Unit)? = null
        ) {
            // GVP/Mast und AP/KOR sind feste Fachfelder und bleiben erhalten.
            // Die frühere normalizeOptionalValue-Hilfe gehörte zum entfernten freien Optional-Block.
            val gvpMastWert: String? = edtGvpMastValue.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val apKorWert: String? = edtApKorValue.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val alleOptionalLabels = listOf("GVP / Mast", "AP / KOR") + optionalLabels
            val alleOptionalValues: List<String?> = listOf(gvpMastWert, apKorWert) + optionalValues

            viewLifecycleOwner.lifecycleScope.launch {
                // Doppelklick verhindern
                btnSpeichern.isEnabled = false

                // requireContext() nur einmal auf Main holen
                val ctx = requireContext()

                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        // Overlay + EXIF schreiben
                        val overlaySettings = ServiceLocator.overlayEinstellungenSpeicher.einstellungen.first()
                        val b = ServiceLocator.baustellenRepository.baustelleMitId(baustelleId).first()
                        val streckeOderBahnhof = b?.streckennummer?.takeIf { it.isNotBlank() } ?: b?.bahnhof ?: "-"
                        val gleisOderWeiche = b?.weiche?.takeIf { it.isNotBlank() } ?: b?.gleis
                        val fotoKnoten = knotenId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
                        val unterName = BaustellenObjektLogik.unterbaustellenName(fotoKnoten)
                        val kmAnzeige = km?.let { String.format(Locale.US, "%.3f", rundeKmAuf10m(it)) }
                        val kmOverlayText = kmAnzeige?.let { kmText ->
                            listOfNotNull(kmText, gvpKurz.takeIf { it.isNotBlank() }).joinToString(" · ")
                        }
                        val overlayObjekt = objektKennung?.takeIf { it.isNotBlank() }
                            ?: fotoKnoten?.gleisOderWeiche?.takeIf { it.isNotBlank() }
                            ?: gleisOderWeiche
                        // Patch 33: keine "- · Gl ..." Zeile mehr. Wenn der Baustellen-Datensatz
                        // keinen Bahnhof liefert, wird MGHN/Gleis aus der Unterbaustelle abgeleitet.
                        val overlayBahnhof = overlayBahnhofName(streckeOderBahnhof, unterName, overlayObjekt)
                        val metaTextBlock = buildString {
                            appendLine("TBHelfer")
                            appendLine("Baustelle=${b?.baustellenname ?: "Baustelle"}")
                            unterName?.takeIf { it.isNotBlank() }?.let { appendLine("Unterbaustelle=$it") }
                            objektBereich?.takeIf { it.isNotBlank() }?.let { appendLine("Bereich=$it") }
                            appendLine("DatumZeit=$datumZeitText")
                            kmAnzeige?.let { appendLine("KM=$it") }
                            appendLine("Firma=$firma")
                            appendLine("Bildtyp=${bildtyp.name}")
                            appendLine("Kurzbeschreibung=$kurz")
                            notiz?.takeIf { it.isNotBlank() }?.let { appendLine("Notiz=$it") }
                            alleOptionalLabels.forEachIndexed { index, label ->
                                val value = alleOptionalValues.getOrNull(index)?.trim().orEmpty()
                                if (value.isNotBlank()) {
                                    appendLine("Info${index + 1}Label=$label")
                                    appendLine("Info${index + 1}Wert=$value")
                                }
                            }
                        }.trim()

                        val effektiveOverlaySettings = if (!objektBereich.isNullOrBlank()) {
                            overlaySettings.copy(aktiv = true)
                        } else overlaySettings
                        val overlayInDateiGeschrieben = OverlayRenderer.renderOverlayWennAktiv(
                            ctx,
                            File(dateipfad),
                            OverlayRenderer.Daten(
                                baustellenName = b?.baustellenname ?: "Baustelle",
                                unterbaustellenName = unterName,
                                streckeOderBahnhof = overlayBahnhof,
                                gleisOderWeiche = overlayObjekt,
                                kmText = kmOverlayText,
                                gpsText = String.format(Locale.US, "%.6f, %.6f", lat, lon),
                                bereichText = objektBereich,
                                datumZeitText = datumZeitText
                            ),
                            effektiveOverlaySettings
                        )

                        // Datei in Ziel-Ordner verschieben (Bilder/Mängel/Abnahmen nach Datum)
                        val datumIso = LocalDate.now().toString() // yyyy-MM-dd
                        val srcFile = File(dateipfad)
                        val baustellenName = b?.baustellenname ?: "UnbekannteBaustelle"
                        val targetFile = if (abnahmeMode) {
                            AbnahmeSpeicher.laden(ctx, abnahmeId)?.let { AbnahmeSpeicher.neueFotoDatei(ctx, it) }
                                ?: FotoSpeicher.zielDateiFuerBildtyp(ctx, baustellenName, bildtyp, datumIso, srcFile.name)
                        } else {
                            FotoSpeicher.zielDateiFuerBildtyp(ctx, baustellenName, bildtyp, datumIso, srcFile.name)
                        }
                        if (srcFile.exists() && srcFile.absolutePath != targetFile.absolutePath) {
                            runCatching {
                                targetFile.parentFile?.mkdirs()

                                // Schnellpfad: rename (innerhalb gleicher Partition)
                                val moved = srcFile.renameTo(targetFile)

                                if (!moved) {
                                    // Fallback: robust kopieren + löschen
                                    srcFile.inputStream().buffered(64 * 1024).use { input ->
                                        targetFile.outputStream().buffered(64 * 1024).use { output ->
                                            input.copyTo(output, 64 * 1024)
                                        }
                                    }
                                    // Wenn Kopie ok, Quelle löschen
                                    srcFile.delete()
                                }

                                if (targetFile.exists()) {
                                    dateipfad = targetFile.absolutePath
                                }
                            }
                        }

                        ImageExifUtil.writeFotoMetadata(
                            file = File(dateipfad),
                            lat = lat,
                            lon = lon,
                            metaTextBlock = metaTextBlock,
                            // Nur normalisieren, wenn das Foto tatsächlich EXIF-korrigiert
                            // eingelesen und mit eingebranntem Overlay neu geschrieben wurde.
                            forceOrientationNormal = overlayInDateiGeschrieben
                        )

                        // Thumbnail neben dem finalen Zielbild erzeugen (Performance in Listen/Details)
                        FotoSpeicher.erstelleThumbnailWennMoeglich(File(dateipfad))

                        val entity = FotoEntity(
                            baustelleId = baustelleId,
                            baustellenKnotenId = knotenId,
                            dateipfad = dateipfad,
                            gpsLat = lat,
                            gpsLon = lon,
                            gpsAltM = gpsAlt,
                            km = km,
                            kmSuffix = suffixIndex,
                            kmManuell = (km != null) && kmManuell,
                            achsabstandM = achsabstand,
                            indikator = indikator,
                            firma = firma,
                            bildtyp = bildtyp,
                            kurztext = kurz,
                            notiz = notiz,
                            objektKennung = overlayObjekt,
                            objektBereich = objektBereich,
                            info1Wert = alleOptionalValues.getOrNull(0),
                            info2Wert = alleOptionalValues.getOrNull(1),
                            info3Wert = alleOptionalValues.getOrNull(2),
                            info4Wert = alleOptionalValues.getOrNull(3),
                            info5Wert = alleOptionalValues.getOrNull(4),
                            erstelltAmEpochMs = Instant.now().toEpochMilli()
                        ).apply {
                            btbMetadaten = de.tbhelfer.datenbank.AufnahmeMetadaten(
                                lat, lon, erstelltAmEpochMs,
                                args.getDouble("gpsAccuracyM", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
                            )
                        }

                        val targetBtbId = args.getString("btbPhotoTargetId")?.takeIf { it.isNotBlank() }
                        val neueFotoId = if (targetBtbId != null) {
                            ServiceLocator.fotoRepository.einfuegenFuerBtb(entity, targetBtbId,
                                clientEntryKey = args.getString("btbPhotoEntryKey"))
                        } else ServiceLocator.fotoRepository.einfuegen(entity)
                        if (abnahmeMode && abnahmeId.isNotBlank()) {
                            AbnahmeSpeicher.fotoZuordnen(ctx, abnahmeId, neueFotoId, abnahmeBildArt, notiz.orEmpty())
                        } else if (zielMangelKey.isNotBlank()) {
                            MangelStatusSpeicher.fotoZuordnen(ctx, zielMangelKey, neueFotoId, zielFotoArt)
                        } else if (bildtyp == Bildtyp.MAENGEL_SCHAEDEN) {
                            // Neuer Mangel: eigenes erstes Foto bildet den Mangel-Vorgang.
                            MangelStatusSpeicher.fotoZuordnen(ctx, "foto:$neueFotoId", neueFotoId, MangelStatusSpeicher.FotoArt.MANGEL)
                        }
                    }.isSuccess
                }

                if (ok) {
                    runCatching {
                        ctx.dataStore.edit { pref ->
                            pref[KEY_LETZTE_KURZBESCHREIBUNG] = kurz
                            pref[KEY_LETZTE_FIRMA] = firma
                            optionalLabels.forEachIndexed { index, label ->
                                pref[FotoOptionalFelderSpeicher.key(baustelleId, index + 1)] = label
                            }
                            pref.remove(FotoOptionalFelderSpeicher.key(baustelleId, 4))
                            pref.remove(FotoOptionalFelderSpeicher.key(baustelleId, 5))
                        }
                    }
                    if (!nachweisMode) {
                        andMaybeAddFirmaToBtb?.invoke(firma)
                    }

                    if (abnahmeMode) {
                        runCatching {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Gespeichert")
                                .setMessage("Foto wurde der Abnahme als ${AbnahmeSpeicher.bildArtLabel(abnahmeBildArt)} zugeordnet.")
                                .setPositiveButton("Weiteres Foto") { _, _ ->
                                    findNavController().popBackStack(R.id.kameraFragment, false)
                                }
                                .setNegativeButton("Fertig") { _, _ ->
                                    if (!findNavController().popBackStack(R.id.abnahmeEditorFragment, false)) {
                                        findNavController().navigateUp()
                                    }
                                }
                                .show()
                        }.getOrElse {
                            Toast.makeText(ctx, "Abnahme-Foto gespeichert", Toast.LENGTH_SHORT).show()
                            if (!findNavController().popBackStack(R.id.abnahmeEditorFragment, false)) findNavController().navigateUp()
                        }
                        return@launch
                    }

                    if (nachweisMode) {
                        runCatching {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Gespeichert")
                                .setMessage("Mangel-Foto gespeichert und zugeordnet.\nWas möchtest du als Nächstes tun?")
                                .setPositiveButton("Neues Foto") { _, _ ->
                                    findNavController().popBackStack(R.id.kameraFragment, false)
                                }
                                .setNeutralButton("Zur Fotoliste") { _, _ ->
                                    findNavController().popBackStack(R.id.kameraFragment, false)
                                    view?.post { runCatching { findNavController().navigate(R.id.action_kamera_to_fotoListe) } }
                                }
                                .setNegativeButton("Fertig") { _, _ ->
                                    runCatching { findNavController().popBackStack(R.id.maengellisteFragment, false) }
                                        .onFailure { findNavController().navigateUp() }
                                }
                                .show()
                        }.getOrElse {
                            Toast.makeText(ctx, "Mangel-Foto gespeichert und zugeordnet", Toast.LENGTH_LONG).show()
                            runCatching { findNavController().popBackStack(R.id.maengellisteFragment, false) }
                                .onFailure { findNavController().navigateUp() }
                        }
                        return@launch
                    }

                    // UX: schneller Serien-Flow
                    runCatching {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Gespeichert")
                            .setMessage("Was möchtest du als Nächstes tun?")
                            .setPositiveButton("Neues Foto") { _, _ ->
                                // Zurück zur Kamera
                                findNavController().popBackStack(R.id.kameraFragment, false)
                            }
                            .setNeutralButton("Zur Fotoliste") { _, _ ->
                                // Erst zurück zur Kamera, dann zur Liste
                                findNavController().popBackStack(R.id.kameraFragment, false)
                                view?.post {
                                    runCatching { findNavController().navigate(R.id.action_kamera_to_fotoListe) }
                                }
                            }
                            .setNegativeButton("Fertig") { _, _ ->
                                findNavController().navigateUp()
                            }
                            .show()
                    }.getOrElse {
                        Toast.makeText(ctx, "Gespeichert", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                } else {
                    btnSpeichern.isEnabled = true
                    Toast.makeText(ctx, "Fehler beim Speichern", Toast.LENGTH_LONG).show()
                }
            }
        }

        var confirmedDescription: Pair<String, String>? = null
        var shortCaptionDialogOpen = false
        btnSpeichern.setOnClickListener {
            // GPS ist Pflicht
            if (lat == 0.0 && lon == 0.0) {
                Toast.makeText(
                    requireContext(),
                    "GPS ist Pflicht – bitte auf Standort warten.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val kmText = edtKm.text.toString().trim()
            val km = kmText
                .takeIf { it.isNotBlank() }
                ?.replace(',', '.')
                ?.toDoubleOrNull()

            if (kmText.isNotBlank() && km == null) {
                Toast.makeText(requireContext(), "km ungültig (z.B. 20.274)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val firma = actFirma.text.toString().trim()
            if (firma.isBlank()) {
                Toast.makeText(requireContext(), "Bitte Firma eingeben", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val kurz = actKurz.text.toString().trim()
            val notiz = edtNotiz.text.toString().trim().takeIf { it.isNotBlank() }
            if (notiz != null && confirmedDescription != (notiz to kurz)) {
                if (shortCaptionDialogOpen) return@setOnClickListener
                val caption = android.widget.EditText(requireContext()).apply {
                    hint = "Kurztext für Bild und BTB"
                    setText(kurz)
                    isSingleLine = true
                }
                val captionDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Wie soll der Kurztext lauten?")
                    .setMessage("Der Langtext bleibt im BTB. Der Kurztext erscheint im Bild und BTB.")
                    .setView(caption)
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen", null)
                    .create()
                shortCaptionDialogOpen = true
                captionDialog.setOnDismissListener { shortCaptionDialogOpen = false }
                captionDialog.setOnShowListener {
                    captionDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        val chosen = caption.text.toString().trim()
                        if (chosen.isBlank()) {
                            caption.error = "Bitte Kurztext eingeben"
                        } else {
                            actKurz.setText(chosen, false)
                            confirmedDescription = notiz to chosen
                            captionDialog.dismiss()
                            btnSpeichern.performClick()
                        }
                    }
                }
                captionDialog.show()
                return@setOnClickListener
            }
            if (kurz.isBlank()) {
                Toast.makeText(requireContext(), "Bitte Kurzbeschreibung eingeben", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val objektKennung: String? = trassenObjektKennung.takeIf { it.isNotBlank() }
            val objektBereich = objektBereichOptionen.getOrNull(spObjektBereich.selectedItemPosition)
                ?.takeUnless { it.equals("Allgemein", ignoreCase = true) || it == objektBereichTrenner }
            // Alte Info1-3-Felder bleiben aus Kompatibilitätsgründen in FotoEntity erhalten,
            // werden ab 200056 aber nicht mehr in der normalen Fotomaske neu befüllt.
            val optionalLabels = emptyList<String>()
            val optionalValues = emptyList<String?>()

            // UI-Suffix entfernt: wir übernehmen den Suffix aus den Args (Standard: -0)
            val bildtyp = when {
                nachweisMode -> Bildtyp.MAENGEL_SCHAEDEN
                abnahmeMode -> Bildtyp.ABNAHMEN
                else -> bildtypen.getOrNull(spBildtyp.selectedItemPosition) ?: Bildtyp.FOTODOKUMENTATION
            }

            val firmaLower = firma.lowercase(Locale.ROOT)
            val canWriteToBtb = !nachweisMode && !abnahmeMode && btbEntryAktuell != null && !btbBaustellenName.isNullOrBlank()
            val fehltImAktuellenBtb = canWriteToBtb && !btbFirmenLower.contains(firmaLower)

            if (fehltImAktuellenBtb) {
                // Patch45g: Auch eine aus alten Fotos bekannte Firma gilt hier als neu,
                // wenn sie im AKTUELLEN Bautagebuch nicht vorkommt.
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Firma nicht im aktuellen BTB")
                    .setMessage("\"$firma\" steht nicht im aktuell passenden Bautagebuch. Wohin übernehmen?")
                    .setPositiveButton("Personal") { _, _ ->
                        saveFoto(km, firma, kurz, notiz, objektKennung, objektBereich, bildtyp, kmManuell, optionalLabels, optionalValues) { name ->
                            addFirmaToBtb(name, targetPersonal = true, targetMaschinen = false, btbEntryAktuell, btbBaustellenName) { updated ->
                                btbEntryAktuell = updated
                                val lower = name.lowercase(Locale.ROOT)
                                btbFirmenLower.add(lower)
                                btbFirmenDisplay.add(name)
                            }
                        }
                    }
                    .setNegativeButton("Geräte/Maschinen") { _, _ ->
                        saveFoto(km, firma, kurz, notiz, objektKennung, objektBereich, bildtyp, kmManuell, optionalLabels, optionalValues) { name ->
                            addFirmaToBtb(name, targetPersonal = false, targetMaschinen = true, btbEntryAktuell, btbBaustellenName) { updated ->
                                btbEntryAktuell = updated
                                val lower = name.lowercase(Locale.ROOT)
                                btbFirmenLower.add(lower)
                                btbFirmenDisplay.add(name)
                            }
                        }
                    }
                    .setNeutralButton("Nur fürs Foto") { _, _ ->
                        saveFoto(km, firma, kurz, notiz, objektKennung, objektBereich, bildtyp, kmManuell, optionalLabels, optionalValues)
                    }
                    .show()
            } else {
                saveFoto(km, firma, kurz, notiz, objektKennung, objektBereich, bildtyp, kmManuell, optionalLabels, optionalValues)
            }
        }


    }

    private fun rundeKmAuf10m(km: Double): Double = kotlin.math.round(km * 100.0) / 100.0

    private fun kurzerBahnhofName(raw: String): String {
        val s = raw.trim()
        val inKlammern = Regex("\\(([^)]+)\\)").find(s)?.groupValues?.getOrNull(1)?.trim()
        return inKlammern?.takeIf { it.isNotBlank() } ?: s
    }

    private fun overlayBahnhofName(streckeOderBahnhof: String, unterbaustellenName: String?, objektKennung: String?): String {
        fun clean(v: String?): String = v?.trim().orEmpty()
        fun valid(v: String): Boolean {
            if (v.isBlank()) return false
            if (v == "-") return false
            if (v.equals("null", ignoreCase = true)) return false
            if (v.equals("Gl", ignoreCase = true) || v.startsWith("Gl ", ignoreCase = true)) return false
            if (objektKennung != null && v.equals(objektKennung.trim(), ignoreCase = true)) return false
            return true
        }

        fun extractCode(raw: String): String? {
            val s = raw.trim()
            Regex("\\(([A-Za-z0-9ÄÖÜäöü_-]{2,12})\\)").find(s)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (valid(it)) return it
            }
            Regex("(?i)\\bBahnhof\\s+([A-Za-z0-9ÄÖÜäöü_-]{2,12})\\b").find(s)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (valid(it)) return it
            }
            Regex("(?i)^\\s*([A-Za-z0-9ÄÖÜäöü_-]{2,12})\\s*(?:[–-]|\\bGl\\b|\\bW\\b)").find(s)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (valid(it)) return it
            }
            return null
        }

        val ausUnterbaustelle = extractCode(clean(unterbaustellenName))
        if (ausUnterbaustelle != null) return ausUnterbaustelle

        val raw = clean(streckeOderBahnhof)
        val kurz = kurzerBahnhofName(raw)
        if (valid(kurz)) return kurz

        return "Bahnhof"
    }

    companion object {
        const val ARG_DATEIPFAD = "dateipfad"
        const val ARG_BAUSTELLE_ID = "baustelleId"
        const val ARG_KNOTEN_ID = "knotenId"
        const val ARG_LAT = "lat"
        const val ARG_LON = "lon"
        const val ARG_ALT = "alt"
        const val ARG_KM = "km"
        const val ARG_KM_SUFFIX = "kmSuffix"
        const val ARG_ACHsA = "achsabstand"
        const val ARG_INDIKATOR = "indikator"
        const val ARG_NACHWEIS_MODE = "nachweisMode"
        const val ARG_NACHWEIS_MANGEL_KEY = "nachweisMangelKey"
        const val ARG_NACHWEIS_STATUS = "nachweisStatus"
        const val ARG_MANGEL_FOTO_ART = "mangelFotoArt"
        const val ARG_PREFILL_FIRMA = "prefillFirma"
        const val ARG_PREFILL_KURZTEXT = "prefillKurztext"
        const val ARG_PREFILL_NOTIZ = "prefillNotiz"
        const val ARG_OBJEKT_KENNUNG = "objektKennung"
        const val ARG_GVP_KURZ = "gvpKurz"
        const val ARG_GVP_MAST = "gvpMast"
        const val ARG_AP_KOR = "apKor"
        const val ARG_ABNAHME_MODE = "abnahmeMode"
        const val ARG_ABNAHME_ID = "abnahmeId"
        const val ARG_ABNAHME_BILD_ART = "abnahmeBildArt"
        val KEY_LETZTE_KURZBESCHREIBUNG = stringPreferencesKey("letzte_kurzbeschreibung")
        val KEY_LETZTE_FIRMA = stringPreferencesKey("letzte_firma")
    }

private fun addFirmaToBtb(
    firmaName: String,
    targetPersonal: Boolean,
    targetMaschinen: Boolean,
    entry: BautagebuchEintrag?,
    baustellenName: String?,
    onDone: ((BautagebuchEintrag) -> Unit)? = null
) {
    val e = entry ?: return
    if (baustellenName.isNullOrBlank()) return

    val cleanName = firmaName.trim()
    val existingIndex = e.firmen.indexOfFirst { it.name.trim().equals(cleanName, ignoreCase = true) }

    val updated = if (existingIndex >= 0) {
        val old = e.firmen[existingIndex]
        val revived = old.copy(
            name = cleanName,
            visibleInPersonal = old.visibleInPersonal || targetPersonal,
            visibleInMaschinen = old.visibleInMaschinen || targetMaschinen,
            isDeleted = false
        )
        e.copy(firmen = e.firmen.toMutableList().also { it[existingIndex] = revived })
    } else {
        val newFirm = FirmaBlock(
            firmaId = UUID.randomUUID().toString(),
            name = cleanName,
            visibleInPersonal = targetPersonal,
            visibleInMaschinen = targetMaschinen,
            isDeleted = false,
            personal = emptyList(),
            geraete = emptyList(),
            leistungen = emptyList()
        )
        e.copy(firmen = e.firmen + newFirm)
    }

    viewLifecycleOwner.lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            BautagebuchSpeicher.speichern(requireContext(), updated)
        }
        onDone?.invoke(updated)
    }
}

}


