package de.tbhelfer.ui.einstellungen.ansicht

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.einstellungen.AnsichtEinstellungenSpeicher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnsichtFragment : Fragment(R.layout.fragment_ansicht) {

    private val speicher: AnsichtEinstellungenSpeicher by lazy { ServiceLocator.ansichtEinstellungenSpeicher }

    private var currentCfg: AnsichtEinstellungenSpeicher.Einstellungen? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rgGrid = view.findViewById<RadioGroup>(R.id.rgSucheGrid)
        val rb1 = view.findViewById<RadioButton>(R.id.rbGrid1)
        val rb2 = view.findViewById<RadioButton>(R.id.rbGrid2)
        val rb3 = view.findViewById<RadioButton>(R.id.rbGrid3)

        val rgNight = view.findViewById<RadioGroup>(R.id.rgNightMode)
        val rbNightSystem = view.findViewById<RadioButton>(R.id.rbNightSystem)
        val rbNightLight = view.findViewById<RadioButton>(R.id.rbNightLight)
        val rbNightDark = view.findViewById<RadioButton>(R.id.rbNightDark)

        viewLifecycleOwner.lifecycleScope.launch {
            val cfg = speicher.einstellungen.first()
            currentCfg = cfg

            when (cfg.sucheGridSpalten) {
                1 -> rb1.isChecked = true
                3 -> rb3.isChecked = true
                else -> rb2.isChecked = true
            }

            when (cfg.nightMode) {
                AnsichtEinstellungenSpeicher.NightMode.LIGHT -> rbNightLight.isChecked = true
                AnsichtEinstellungenSpeicher.NightMode.DARK -> rbNightDark.isChecked = true
                else -> rbNightSystem.isChecked = true
            }
        }

        fun applyNightMode(night: AnsichtEinstellungenSpeicher.NightMode) {
            val mode = when (night) {
                AnsichtEinstellungenSpeicher.NightMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AnsichtEinstellungenSpeicher.NightMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                AnsichtEinstellungenSpeicher.NightMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        fun applyAndSave() {
            val prev = currentCfg

            val grid = when (rgGrid.checkedRadioButtonId) {
                R.id.rbGrid1 -> 1
                R.id.rbGrid3 -> 3
                else -> 2
            }

            val night = when (rgNight.checkedRadioButtonId) {
                R.id.rbNightLight -> AnsichtEinstellungenSpeicher.NightMode.LIGHT
                R.id.rbNightDark -> AnsichtEinstellungenSpeicher.NightMode.DARK
                else -> AnsichtEinstellungenSpeicher.NightMode.SYSTEM
            }

            applyNightMode(night)

            viewLifecycleOwner.lifecycleScope.launch {
                val base = prev ?: speicher.einstellungen.first()

                val updated = base.copy(
                    // Farben sind fix: Buttons TB-Blau, normale Texte via Theme
                    menuButtonColor = Color.parseColor("#2F7BFF"),
                    menuTextColor = Color.BLACK, // wird nicht mehr global angewendet
                    menuTextSizeSp = 16,         // Slider entfernt -> Standard
                    sucheGridSpalten = grid,
                    nightMode = night
                )
                speicher.speichern(updated)
                currentCfg = updated
            }
        }

        rgGrid.setOnCheckedChangeListener { _, _ -> applyAndSave() }
        rgNight.setOnCheckedChangeListener { _, _ -> applyAndSave() }

        applyAndSave()
    }
}
