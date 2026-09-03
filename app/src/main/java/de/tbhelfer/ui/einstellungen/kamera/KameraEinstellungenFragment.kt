package de.tbhelfer.ui.einstellungen.kamera

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.einstellungen.KameraEinstellungenSpeicher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Patch45m: Die Kamera-Seite zeigt nur noch die echte dauerhafte Foto-Einstellung.
 * Blitz, Nachtmodus und Zoom werden direkt in der Kamera bedient und bleiben intern
 * im bestehenden KameraEinstellungenSpeicher erhalten.
 */
class KameraEinstellungenFragment : Fragment(R.layout.fragment_kamera_einstellungen) {

    private val speicher: KameraEinstellungenSpeicher by lazy { ServiceLocator.kameraEinstellungenSpeicher }
    private var currentCfg: KameraEinstellungenSpeicher.Einstellungen? = null
    private var initialisiert = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rgQual = view.findViewById<RadioGroup>(R.id.rgQualitaet)

        viewLifecycleOwner.lifecycleScope.launch {
            val cfg = speicher.einstellungen.first()
            currentCfg = cfg

            when {
                cfg.jpegQualitaet <= 78 -> rgQual.check(R.id.rbQualNiedrig)
                cfg.jpegQualitaet <= 90 -> rgQual.check(R.id.rbQualMittel)
                else -> rgQual.check(R.id.rbQualHoch)
            }
            initialisiert = true
        }

        rgQual.setOnCheckedChangeListener { _, _ ->
            if (!initialisiert) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                val base = currentCfg ?: speicher.einstellungen.first()
                val qual = when (rgQual.checkedRadioButtonId) {
                    R.id.rbQualNiedrig -> 75
                    R.id.rbQualMittel -> 85
                    else -> 95
                }
                val updated = base.copy(jpegQualitaet = qual)
                speicher.speichern(updated)
                currentCfg = updated
            }
        }
    }
}
