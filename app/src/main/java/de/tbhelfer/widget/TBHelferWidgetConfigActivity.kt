package de.tbhelfer.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
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
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gemeinsames Formular für das konfigurierbare Baustellen-Widget. */
abstract class TBHelferWidgetEditorBaseActivity : AppCompatActivity() {

    protected lateinit var klickSpinner: Spinner
    protected lateinit var countSpinner: Spinner
    protected lateinit var p4: CheckBox
    protected lateinit var p5: CheckBox
    protected lateinit var p6: CheckBox
    protected lateinit var p7: CheckBox
    protected lateinit var p8: CheckBox
    protected lateinit var buttons: CheckBox
    protected lateinit var button1: Spinner
    protected lateinit var button2: Spinner
    protected lateinit var camera: CheckBox
    protected lateinit var themeGroup: RadioGroup

    protected val klicks = listOf(
        "Aktuellen BTB" to WidgetPreferences.HauptKlick.AKTUELLER_BTB,
        "Aktuelle Baustelle" to WidgetPreferences.HauptKlick.AKTUELLE_BAUSTELLE,
        "Kamera" to WidgetPreferences.HauptKlick.KAMERA,
        "Abnahmeliste" to WidgetPreferences.HauptKlick.ABNAHMEN,
        "Mängelliste" to WidgetPreferences.HauptKlick.MAENGEL,
        "App-Startseite" to WidgetPreferences.HauptKlick.STARTSEITE
    )

    protected val aktionen = listOf(
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

    protected fun showEditor(title: String, subtitle: String, cfg: WidgetPreferences.Config) {
        val pad = dp(16)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(resolveColor(android.R.attr.textColorPrimary))
        })
        root.addView(TextView(this).apply {
            text = subtitle
            setPadding(0, dp(4), 0, dp(14))
        })

        root.sectionTitle("Antippen öffnet")
        klickSpinner = spinner(klicks.map { it.first }, klicks.indexOfFirst { it.second == cfg.hauptKlick })
        root.addView(klickSpinner)

        root.sectionTitle("Letzte Einträge")
        countSpinner = spinner((1..5).map(Int::toString), cfg.letzteEintraege - 1)
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
        root.addView(TextView(this).apply { text = "Button 1" })
        button1 = spinner(aktionen.map { it.first }, aktionen.indexOfFirst { it.second == cfg.button1 })
        root.addView(button1)
        root.addView(TextView(this).apply { text = "Button 2"; setPadding(0, dp(8), 0, 0) })
        button2 = spinner(aktionen.map { it.first }, aktionen.indexOfFirst { it.second == cfg.button2 })
        root.addView(button2)

        root.sectionTitle("Kamera")
        camera = check("Kamera-Symbol anzeigen", cfg.kameraAnzeigen)
        root.addView(camera)

        root.sectionTitle("Darstellung")
        themeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf(
            "Automatisch" to WidgetPreferences.Darstellung.AUTOMATISCH,
            "Hell" to WidgetPreferences.Darstellung.HELL,
            "Dunkel" to WidgetPreferences.Darstellung.DUNKEL,
            "Transparent" to WidgetPreferences.Darstellung.TRANSPARENT
        ).forEach { (label, mode) ->
            themeGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = mode.name
                text = label
                isChecked = mode == cfg.darstellung
            })
        }
        root.addView(themeGroup)

        root.addView(Button(this).apply {
            text = "Speichern"
            setOnClickListener { saveConfig(readConfig()) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54))
            lp.topMargin = dp(22)
            layoutParams = lp
        })
        setContentView(scroll)
    }

    protected fun readConfig(): WidgetPreferences.Config {
        val selectedTheme = themeGroup.findViewById<RadioButton>(themeGroup.checkedRadioButtonId)?.tag?.toString()
        return WidgetPreferences.Config(
            // Das Baustellen-Widget zeigt immer die letzten Einträge; Größe ergänzt automatisch Statuszeilen.
            ansicht = WidgetPreferences.Ansicht.LETZTE_EINTRAEGE,
            hauptKlick = klicks.getOrNull(klickSpinner.selectedItemPosition)?.second
                ?: WidgetPreferences.HauptKlick.AKTUELLER_BTB,
            letzteEintraege = (countSpinner.selectedItemPosition + 1).coerceIn(1, 5),
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

    protected abstract fun saveConfig(config: WidgetPreferences.Config)

    /** Fängt Widget-Einstellungsfehler ohne Logcat ab und legt eine kleine Diagnose-Datei ab. */
    protected fun failWidgetSettings(area: String, error: Throwable) {
        runCatching {
            val base = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val dir = File(base, "TB-Helfer/Diagnose").apply { mkdirs() }
            val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())
            File(dir, "Widget_Fehler.txt").writeText(
                buildString {
                    appendLine("TB-Helfer Widget-Diagnose")
                    appendLine("Zeit: $stamp")
                    appendLine("Bereich: $area")
                    appendLine()
                    append(Log.getStackTraceString(error))
                },
                Charsets.UTF_8
            )
        }
        Toast.makeText(
            this,
            "Widget-Einstellungen konnten nicht geöffnet werden. Diagnose wurde gespeichert.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun LinearLayout.sectionTitle(textValue: String) {
        addView(TextView(this@TBHelferWidgetEditorBaseActivity).apply {
            text = textValue
            textSize = 17f
            setPadding(0, dp(16), 0, dp(5))
        })
    }

    private fun spinner(items: List<String>, selected: Int): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@TBHelferWidgetEditorBaseActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected.coerceAtLeast(0))
    }

    private fun check(label: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        text = label
        isChecked = checked
    }

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun resolveColor(attr: Int): Int {
        val a = theme.obtainStyledAttributes(intArrayOf(attr))
        return try { a.getColor(0, 0xff000000.toInt()) } finally { a.recycle() }
    }
}

/** Konfiguration, die Android beim Platzieren des Baustellen-Widgets öffnet. */
class TBHelferWidgetConfigActivity : TBHelferWidgetEditorBaseActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            appWidgetId = intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            setResult(
                RESULT_CANCELED,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                Toast.makeText(this, "Widget konnte nicht eingerichtet werden.", Toast.LENGTH_LONG).show()
                finish()
                return@runCatching
            }

            val manager = AppWidgetManager.getInstance(this)
            val otherIds = manager.getAppWidgetIds(ComponentName(this, CurrentSiteWidgetProvider::class.java))
                .filter { it != appWidgetId }
            if (otherIds.isNotEmpty()) {
                Toast.makeText(this, "Es kann nur ein TB-Helfer-Baustellen-Widget angelegt werden.", Toast.LENGTH_LONG).show()
                finish()
                return@runCatching
            }

            showEditor(
                title = "TB-Helfer Baustelle",
                subtitle = "Das Widget passt die Anzahl der sichtbaren Einträge automatisch an seine Größe an.",
                cfg = WidgetPreferences.load(this, appWidgetId)
            )
        }.onFailure { failWidgetSettings("Baustellen-Widget einrichten", it) }
    }

    override fun saveConfig(config: WidgetPreferences.Config) {
        WidgetPreferences.save(this, appWidgetId, config)
        val manager = AppWidgetManager.getInstance(this)
        CurrentSiteWidgetProvider.updateWidgets(this, manager, intArrayOf(appWidgetId))
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
    }
}

/** Normale Einstellungsseite innerhalb der App – unabhängig vom Android-Launcher. */
class TBHelferWidgetSettingsActivity : TBHelferWidgetEditorBaseActivity() {
    private var existingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val manager = AppWidgetManager.getInstance(this)
            existingWidgetId = manager.getAppWidgetIds(ComponentName(this, CurrentSiteWidgetProvider::class.java))
                .firstOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
            val cfg = WidgetPreferences.load(this, existingWidgetId)
            showEditor(
                title = "Startbildschirm-Widget",
                subtitle = if (existingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
                    "Diese Einstellungen gelten als Standard, sobald das Baustellen-Widget hinzugefügt wird."
                else "Die Änderungen werden auf das vorhandene Baustellen-Widget angewendet.",
                cfg = cfg
            )
        }.onFailure { failWidgetSettings("Einstellungen > Startbildschirm-Widget", it) }
    }

    override fun saveConfig(config: WidgetPreferences.Config) {
        WidgetPreferences.save(this, AppWidgetManager.INVALID_APPWIDGET_ID, config)
        if (existingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetPreferences.save(this, existingWidgetId, config)
            val manager = AppWidgetManager.getInstance(this)
            CurrentSiteWidgetProvider.updateWidgets(this, manager, intArrayOf(existingWidgetId))
        }
        Toast.makeText(this, "Widget-Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
        finish()
    }
}
