package de.tbhelfer.ui.einstellungen

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import de.tbhelfer.widget.CurrentSiteWidgetProvider
import de.tbhelfer.widget.WidgetPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Widget-Einstellungen innerhalb der bereits registrierten HauptActivity.
 * Dadurch wird keine zusätzliche Activity im AndroidManifest benötigt.
 */
class WidgetEinstellungenFragment : Fragment() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var launcherMode: Boolean = false

    private lateinit var klickSpinner: Spinner
    private lateinit var countSpinner: Spinner
    private lateinit var p4: CheckBox
    private lateinit var p5: CheckBox
    private lateinit var p6: CheckBox
    private lateinit var p7: CheckBox
    private lateinit var p8: CheckBox
    private lateinit var buttons: CheckBox
    private lateinit var button1: Spinner
    private lateinit var button2: Spinner
    private lateinit var camera: CheckBox
    private lateinit var themeGroup: RadioGroup

    private val klicks = listOf(
        "Aktuellen BTB" to WidgetPreferences.HauptKlick.AKTUELLER_BTB,
        "Aktuelle Baustelle" to WidgetPreferences.HauptKlick.AKTUELLE_BAUSTELLE,
        "Kamera" to WidgetPreferences.HauptKlick.KAMERA,
        "Abnahmeliste" to WidgetPreferences.HauptKlick.ABNAHMEN,
        "Mängelliste" to WidgetPreferences.HauptKlick.MAENGEL,
        "App-Startseite" to WidgetPreferences.HauptKlick.STARTSEITE
    )

    private val aktionen = listOf(
        "Punkt 4 – neuer Eintrag" to WidgetPreferences.ButtonAktion.PUNKT_4,
        "Punkt 5 – neuer Eintrag" to WidgetPreferences.ButtonAktion.PUNKT_5,
        "Punkt 6 – neuer Eintrag" to WidgetPreferences.ButtonAktion.PUNKT_6,
        "Punkt 7 – neuer Eintrag" to WidgetPreferences.ButtonAktion.PUNKT_7,
        "Punkt 8 – neuer Eintrag" to WidgetPreferences.ButtonAktion.PUNKT_8,
        "Neue Abnahme" to WidgetPreferences.ButtonAktion.NEUE_ABNAHME,
        "Kamera" to WidgetPreferences.ButtonAktion.KAMERA,
        "Aktuellen BTB öffnen" to WidgetPreferences.ButtonAktion.AKTUELLER_BTB,
        "Abnahmeliste" to WidgetPreferences.ButtonAktion.ABNAHMEN,
        "Mängelliste" to WidgetPreferences.ButtonAktion.MAENGEL
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = arguments?.getInt("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        launcherMode = arguments?.getBoolean("launcherMode", false) ?: false

        if (launcherMode) {
            requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().finish()
                }
            })
        }
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return runCatching { buildEditor() }.getOrElse {
            fail("Widget-Einstellungen öffnen", it)
            TextView(requireContext()).apply { text = "Widget-Einstellungen konnten nicht geöffnet werden." }
        }
    }

    private fun buildEditor(): View {
        val manager = AppWidgetManager.getInstance(requireContext())
        val existingId = manager.getAppWidgetIds(ComponentName(requireContext(), CurrentSiteWidgetProvider::class.java))
            .firstOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val effectiveId = if (launcherMode) appWidgetId else existingId
        val cfg = WidgetPreferences.load(requireContext(), effectiveId)

        val scroll = ScrollView(requireContext())
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(root)

        root.addView(TextView(requireContext()).apply {
            text = if (launcherMode) "TB-Helfer Baustelle" else "Startbildschirm-Widget"
            textSize = 24f
            setTextColor(resolveColor(android.R.attr.textColorPrimary))
        })
        root.addView(TextView(requireContext()).apply {
            text = if (launcherMode) {
                "Widget einrichten. Die Anzahl sichtbarer Einträge passt sich automatisch an die Größe an."
            } else if (existingId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                "Diese Einstellungen gelten als Standard für das Baustellen-Widget."
            } else {
                "Die Änderungen werden auf das vorhandene Baustellen-Widget angewendet."
            }
            setPadding(0, dp(4), 0, dp(14))
        })

        root.sectionTitle("Antippen öffnet")
        klickSpinner = spinner(klicks.map { it.first }, klicks.indexOfFirst { it.second == cfg.hauptKlick })
        root.addView(klickSpinner)

        root.sectionTitle("Letzte Einträge")
        countSpinner = spinner((1..10).map(Int::toString), cfg.letzteEintraege - 1)
        root.addView(countSpinner)

        root.sectionTitle("Einträge aus")
        p4 = check("Ausgeführte Arbeiten", cfg.punkt4)
        p5 = check("Abnahmen", cfg.punkt5)
        p6 = check("Einweisung / Unterweisung", cfg.punkt6)
        p7 = check("Besonderheiten", cfg.punkt7)
        p8 = check("Mängel / Schäden", cfg.punkt8)
        listOf(p4, p5, p6, p7, p8).forEach(root::addView)

        root.sectionTitle("Zusätzliche Buttons")
        buttons = check("Anzeigen", cfg.buttonsAnzeigen)
        root.addView(buttons)
        root.addView(TextView(requireContext()).apply { text = "Button 1" })
        button1 = spinner(aktionen.map { it.first }, aktionen.indexOfFirst { it.second == cfg.button1 })
        root.addView(button1)
        root.addView(TextView(requireContext()).apply {
            text = "Button 2"
            setPadding(0, dp(8), 0, 0)
        })
        button2 = spinner(aktionen.map { it.first }, aktionen.indexOfFirst { it.second == cfg.button2 })
        root.addView(button2)

        root.sectionTitle("Kamera")
        camera = check("Kamera-Symbol anzeigen", cfg.kameraAnzeigen)
        root.addView(camera)
        root.addView(TextView(requireContext()).apply {
            text = "Ist das Kamera-Symbol aktiv, wird ein zusätzlich als Kamera belegter Textbutton automatisch ausgeblendet."
            textSize = 12f
            setPadding(0, 0, 0, dp(4))
        })

        root.sectionTitle("Darstellung")
        themeGroup = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
        listOf(
            "Automatisch" to WidgetPreferences.Darstellung.AUTOMATISCH,
            "Hell" to WidgetPreferences.Darstellung.HELL,
            "Dunkel" to WidgetPreferences.Darstellung.DUNKEL,
            "Transparent" to WidgetPreferences.Darstellung.TRANSPARENT
        ).forEach { (label, mode) ->
            themeGroup.addView(RadioButton(requireContext()).apply {
                id = View.generateViewId()
                tag = mode.name
                text = label
                isChecked = mode == cfg.darstellung
            })
        }
        root.addView(themeGroup)

        root.addView(Button(requireContext()).apply {
            text = "Speichern"
            setOnClickListener { save() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                topMargin = dp(22)
            }
        })
        return scroll
    }

    private fun readConfig(): WidgetPreferences.Config {
        val selectedTheme = themeGroup.findViewById<RadioButton>(themeGroup.checkedRadioButtonId)?.tag?.toString()
        return WidgetPreferences.Config(
            ansicht = WidgetPreferences.Ansicht.LETZTE_EINTRAEGE,
            hauptKlick = klicks.getOrNull(klickSpinner.selectedItemPosition)?.second
                ?: WidgetPreferences.HauptKlick.AKTUELLER_BTB,
            letzteEintraege = (countSpinner.selectedItemPosition + 1).coerceIn(1, 10),
            punkt4 = p4.isChecked,
            punkt5 = p5.isChecked,
            punkt6 = p6.isChecked,
            punkt7 = p7.isChecked,
            punkt8 = p8.isChecked,
            buttonsAnzeigen = buttons.isChecked,
            button1 = aktionen.getOrNull(button1.selectedItemPosition)?.second
                ?: WidgetPreferences.ButtonAktion.PUNKT_4,
            button2 = aktionen.getOrNull(button2.selectedItemPosition)?.second
                ?: WidgetPreferences.ButtonAktion.NEUE_ABNAHME,
            kameraAnzeigen = camera.isChecked,
            positionModus = WidgetPreferences.PositionModus.LETZTE_APP_POSITION,
            darstellung = runCatching {
                WidgetPreferences.Darstellung.valueOf(selectedTheme.orEmpty())
            }.getOrDefault(WidgetPreferences.Darstellung.AUTOMATISCH)
        )
    }

    private fun save() {
        runCatching {
            val context = requireContext()
            val config = readConfig()
            val manager = AppWidgetManager.getInstance(context)

            // Immer auch als Standard speichern.
            WidgetPreferences.save(context, AppWidgetManager.INVALID_APPWIDGET_ID, config)

            if (launcherMode) {
                WidgetPreferences.save(context, appWidgetId, config)
                CurrentSiteWidgetProvider.updateWidgets(context, manager, intArrayOf(appWidgetId))
                requireActivity().setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                requireActivity().finish()
            } else {
                val existingIds = manager.getAppWidgetIds(ComponentName(context, CurrentSiteWidgetProvider::class.java))
                existingIds.firstOrNull()?.let { id ->
                    WidgetPreferences.save(context, id, config)
                    CurrentSiteWidgetProvider.updateWidgets(context, manager, intArrayOf(id))
                }
                Toast.makeText(context, "Widget-Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }.onFailure { fail("Widget-Einstellungen speichern", it) }
    }

    private fun LinearLayout.sectionTitle(value: String) {
        addView(TextView(requireContext()).apply {
            text = value
            textSize = 17f
            setPadding(0, dp(16), 0, dp(5))
        })
    }

    private fun spinner(items: List<String>, selected: Int): Spinner = Spinner(requireContext()).apply {
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected.coerceAtLeast(0))
    }

    private fun check(label: String, checked: Boolean): CheckBox = CheckBox(requireContext()).apply {
        text = label
        isChecked = checked
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun resolveColor(attr: Int): Int {
        val a = requireContext().theme.obtainStyledAttributes(intArrayOf(attr))
        return try { a.getColor(0, 0xff000000.toInt()) } finally { a.recycle() }
    }

    private fun fail(area: String, error: Throwable) {
        runCatching {
            val base = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: requireContext().filesDir
            val dir = File(base, "TB-Helfer/Diagnose").apply { mkdirs() }
            val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())
            File(dir, "Widget_Fehler.txt").writeText(
                "TB-Helfer Widget-Diagnose\nZeit: $stamp\nBereich: $area\n\n${Log.getStackTraceString(error)}",
                Charsets.UTF_8
            )
        }
        Toast.makeText(requireContext(), "Widget-Einstellungen konnten nicht geöffnet werden.", Toast.LENGTH_LONG).show()
        if (launcherMode) requireActivity().finish() else findNavController().navigateUp()
    }
}
