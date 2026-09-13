package de.tbhelfer.ui.bautagebuch

import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ui.kamera.FotoErfassenFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.WeakHashMap

/**
 * Folgepatch 200032:
 * - Details anzeigen = reine, kurze, schreibgeschuetzte Zusammenfassung.
 * - Datum/Zeit wird sofort sichtbar neu geladen, ohne erst die Seite verlassen zu muessen.
 * - Foto-Maske liest Firmen aus exakt der aktiven btbId statt aus einem Zeitfenster-Kandidaten.
 */
object BtbPatch200032Installer {

    private val boundViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun install(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    when (f) {
                        is BautagebuchFragment -> bindBtb(f, v)
                        is FotoErfassenFragment -> bindFoto(f, v)
                    }
                }
            },
            true
        )
        activity.supportFragmentManager.fragments.forEach(::bindRecursively)
    }

    private fun bindRecursively(fragment: Fragment) {
        fragment.view?.let { view ->
            when (fragment) {
                is BautagebuchFragment -> bindBtb(fragment, view)
                is FotoErfassenFragment -> bindFoto(fragment, view)
            }
        }
        runCatching { fragment.childFragmentManager.fragments }
            .getOrDefault(emptyList())
            .forEach(::bindRecursively)
    }

    private fun bindBtb(fragment: BautagebuchFragment, root: View) {
        if (!boundViews.add(root)) return
        bindReadonlyDetails(fragment, root)
        bindStableTimeEditor(fragment, root)
    }

    private fun bindReadonlyDetails(fragment: BautagebuchFragment, root: View) {
        val row = root.findViewById<View>(R.id.topFieldsToggleRow)
        val text = root.findViewById<TextView>(R.id.tvTopFieldsToggle)
        val click = View.OnClickListener { anchor -> showReadonlyDetails(fragment, root, anchor) }
        row?.setOnClickListener(click)
        text?.setOnClickListener(click)
        text?.text = "Details anzeigen"
    }

    private fun showReadonlyDetails(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val current = BtbUiSaveBridge.aktuell(fragment)
            if (current == null) {
                Snackbar.make(anchor, "Noch kein aktueller BTB vorhanden.", Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val e = current.entry
            val ctx = fragment.requireContext()
            val density = root.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(4), dp(18), dp(4))
            }

            fun addBlock(title: String, value: String) {
                content.addView(TextView(ctx).apply {
                    text = title
                    textSize = 13f
                    setTextColor(Color.BLACK)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(6), 0, dp(1))
                })
                content.addView(TextView(ctx).apply {
                    text = value.ifBlank { "Keine Angaben" }
                    textSize = 12f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, 0, 0, dp(3))
                })
            }

            val dateTime = buildString {
                append(e.datumIso)
                append(" · ")
                append(e.startUhrzeit ?: "--:--")
                append("–")
                append(e.endeUhrzeit ?: "--:--")
            }
            val personal = e.firmen
                .filter { it.visibleInPersonal && !it.isDeleted }
                .mapNotNull { f ->
                    val values = f.personal.mapNotNull { p ->
                        val s = listOfNotNull(
                            p.anzahl?.toString(),
                            p.rolle.takeIf { it.isNotBlank() }
                        ).joinToString(" ").trim()
                        s.takeIf { it.isNotBlank() }
                    }
                    if (f.name.isBlank() && values.isEmpty()) null
                    else listOf(f.name.takeIf { it.isNotBlank() }, values.joinToString(", ").takeIf { it.isNotBlank() })
                        .filterNotNull().joinToString(": ")
                }
                .joinToString("\n")
                .ifBlank { e.personal.joinToString("\n") }
            val machines = e.firmen
                .filter { it.visibleInMaschinen && !it.isDeleted }
                .mapNotNull { f ->
                    val values = f.geraete.mapNotNull { g ->
                        val s = listOfNotNull(
                            g.anzahl?.toString(),
                            g.bezeichnung.takeIf { it.isNotBlank() }
                        ).joinToString(" ").trim()
                        s.takeIf { it.isNotBlank() }
                    }
                    if (f.name.isBlank() && values.isEmpty()) null
                    else listOf(f.name.takeIf { it.isNotBlank() }, values.joinToString(", ").takeIf { it.isNotBlank() })
                        .filterNotNull().joinToString(": ")
                }
                .joinToString("\n")
                .ifBlank { e.geraete.joinToString("\n") }
            val weatherSlots = (e.weather?.slots?.takeIf { it.isNotEmpty() } ?: e.weatherSlots)
            val weather = weatherSlots.joinToString("\n") { slot ->
                listOfNotNull(
                    slot.uhrzeit.takeIf { it.isNotBlank() },
                    slot.tempC?.let { "$it °C" },
                    slot.bewoelkung?.takeIf { it.isNotBlank() },
                    slot.niederschlag?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            }.ifBlank { e.wetter.orEmpty() }

            addBlock("Datum/Zeit", dateTime)
            addBlock("Personal", personal)
            addBlock("Geräte/Maschinen", machines)
            addBlock("Wetter", weather)

            MaterialAlertDialogBuilder(ctx)
                .setTitle("BTB-Details")
                .setView(content)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /** 200031-Zeitdialog, aber mit einem echten sofortigen Fragment-Reload in zwei Transaktionen. */
    private fun bindStableTimeEditor(fragment: BautagebuchFragment, root: View) {
        val dateTime = root.findViewById<View>(R.id.btnDateTime) ?: return
        dateTime.setOnClickListener { anchor -> showTimeDialog(fragment, root, anchor) }
    }

    private fun showTimeDialog(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val current = BtbUiSaveBridge.aktuell(fragment)
            if (current == null) {
                Snackbar.make(anchor, "Noch kein aktueller BTB vorhanden.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val entry = current.entry
            var start = parseTime(entry.startUhrzeit) ?: LocalTime.now().withSecond(0).withNano(0)
            var end = parseTime(entry.endeUhrzeit) ?: start.plusHours(8)
            val ctx = fragment.requireContext()
            val density = root.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(4), dp(20), dp(4))
            }
            content.addView(TextView(ctx).apply {
                text = "Datum: ${entry.datumIso}\nDie btbId bleibt unverändert."
                textSize = 12f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, 0, dp(8))
            })

            fun row(label: String, initial: LocalTime, onChanged: (LocalTime) -> Unit): MaterialButton {
                val line = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                line.addView(TextView(ctx).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                val btn = MaterialButton(ctx).apply {
                    text = initial.format(timeFmt)
                    isAllCaps = false
                }
                btn.setOnClickListener {
                    val now = parseTime(btn.text?.toString()) ?: initial
                    TimePickerDialog(ctx, { _, h, m ->
                        val selected = LocalTime.of(h, m)
                        btn.text = selected.format(timeFmt)
                        onChanged(selected)
                    }, now.hour, now.minute, true).show()
                }
                line.addView(btn)
                content.addView(line)
                return btn
            }

            val startButton = row("Beginn", start) { start = it }
            val endButton = row("Ende", end) { end = it }

            val dialog = MaterialAlertDialogBuilder(ctx)
                .setTitle("Datum/Zeit bearbeiten")
                .setView(content)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    start = parseTime(startButton.text?.toString()) ?: start
                    end = parseTime(endButton.text?.toString()) ?: end
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        val baseDate = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull()
                        val endDate = baseDate?.let { if (!end.isAfter(start)) it.plusDays(1) else it }
                        val updated = entry.copy(
                            startUhrzeit = start.format(timeFmt),
                            endeUhrzeit = end.format(timeFmt),
                            endeDatumIso = endDate?.toString(),
                            geaendertAmEpochMs = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.speichern(ctx.applicationContext, updated)
                            BtbDokumentVersionierung.pflegeNachAutosave(ctx.applicationContext, updated)
                        }
                        dialog.dismiss()
                        if (!fragment.isAdded) return@launch

                        // Sofort sichtbare Rueckmeldung, danach echter View-Neuaufbau. Detach+Attach
                        // in EINER Transaktion kann sich gegenseitig aufheben; daher zwei commitNow.
                        root.findViewById<TextView>(R.id.tvCurrentReportLine1)?.text = buildString {
                            append(if (entry.schicht == SchichtTyp.NACHT) "Nacht" else "Tag")
                            append(" · ")
                            append(start.format(timeFmt))
                            append("–")
                            append(end.format(timeFmt))
                            append(" · ")
                            append(entry.datumIso)
                        }
                        Snackbar.make(anchor, "Arbeitszeit gespeichert", Snackbar.LENGTH_SHORT).show()
                        val fm = fragment.parentFragmentManager
                        runCatching {
                            fm.beginTransaction().detach(fragment).commitNowAllowingStateLoss()
                            fm.beginTransaction().attach(fragment).commitNowAllowingStateLoss()
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    private fun bindFoto(fragment: FotoErfassenFragment, root: View) {
        if (!boundViews.add(root)) return
        val firma = root.findViewById<AutoCompleteTextView>(R.id.actFirma) ?: return
        val baustelleId = fragment.arguments?.getLong(FotoErfassenFragment.ARG_BAUSTELLE_ID, -1L) ?: -1L
        if (baustelleId <= 0L) return

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastSignature: String? = null
                while (isActive) {
                    val active = withContext(Dispatchers.IO) {
                        runCatching { BtbActiveContext.laden(fragment.requireContext(), baustelleId) }.getOrNull()
                    }
                    val names = active?.entry?.firmen.orEmpty()
                        .filter { !it.isDeleted }
                        .map { it.name.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                    val signature = "${active?.btbId.orEmpty()}|${names.joinToString("|")}"
                    if (signature != lastSignature) {
                        lastSignature = signature
                        val currentText = firma.text?.toString().orEmpty().trim()
                        firma.setAdapter(ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, names))
                        firma.threshold = 0
                        if (currentText.isBlank() && names.size == 1) {
                            firma.setText(names.first(), false)
                            firma.setSelection(names.first().length)
                        }
                        firma.hint = when {
                            active == null -> "Firma"
                            names.isEmpty() -> "Keine Firma im aktiven BTB"
                            else -> "Firma aus aktivem BTB"
                        }
                        firma.setOnClickListener { firma.showDropDown() }
                    }
                    delay(600L)
                }
            }
        }
    }

    private fun parseTime(value: String?): LocalTime? = value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
}
