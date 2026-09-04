package de.tbhelfer.ui.einstellungen.overlay

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OverlayEinstellungenFragment : Fragment(R.layout.fragment_overlay_einstellungen) {

    private var initialisiert: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swAktiv = view.findViewById<SwitchMaterial>(R.id.swOverlayAktiv)
        val swAutoKontrast = view.findViewById<SwitchMaterial>(R.id.swAutoKontrast)
        val rgDarstellung = view.findViewById<RadioGroup>(R.id.rgOverlayDarstellung)
        val rgPos = view.findViewById<RadioGroup>(R.id.rgOverlayPosition)
        val swAutoPosSmart = view.findViewById<SwitchMaterial>(R.id.swAutoPositionSmart)

        val sbAlpha = view.findViewById<SeekBar>(R.id.sbOverlayAlpha)
        val sbText = view.findViewById<SeekBar>(R.id.sbOverlayTextSize)

        val swBaustelle = view.findViewById<SwitchMaterial>(R.id.swShowBaustelle)
        val swUnter = view.findViewById<SwitchMaterial>(R.id.swShowUnter)
        val swStrecke = view.findViewById<SwitchMaterial>(R.id.swShowStrecke)
        val swGleis = view.findViewById<SwitchMaterial>(R.id.swShowGleis)
        val swKm = view.findViewById<SwitchMaterial>(R.id.swShowKm)
        val swGps = view.findViewById<SwitchMaterial>(R.id.swShowGps)
        val swDt = view.findViewById<SwitchMaterial>(R.id.swShowDatumZeit)

        fun aktuellePosition(): OverlayEinstellungenSpeicher.Position {
            return when (rgPos.checkedRadioButtonId) {
                R.id.rbPosLo -> OverlayEinstellungenSpeicher.Position.LINKS_OBEN
                R.id.rbPosRo -> OverlayEinstellungenSpeicher.Position.RECHTS_OBEN
                R.id.rbPosLu -> OverlayEinstellungenSpeicher.Position.LINKS_UNTEN
                R.id.rbPosRu -> OverlayEinstellungenSpeicher.Position.RECHTS_UNTEN
                else -> OverlayEinstellungenSpeicher.Position.AUTO
            }
        }

        fun aktuelleDarstellung(): OverlayEinstellungenSpeicher.Darstellung {
            return when (rgDarstellung.checkedRadioButtonId) {
                R.id.rbDarstellungKompakt -> OverlayEinstellungenSpeicher.Darstellung.KOMPAKT_2ZEILIG
                else -> OverlayEinstellungenSpeicher.Darstellung.NORMAL
            }
        }

        suspend fun speichern() {
            if (!initialisiert) return
            val neu = OverlayEinstellungenSpeicher.Einstellungen(
                aktiv = swAktiv.isChecked,
                position = aktuellePosition(),
                darstellung = aktuelleDarstellung(),
                autoPositionSmart = swAutoPosSmart.isChecked,
                alpha = (sbAlpha.progress.coerceIn(10, 90) / 100f),
                autoKontrast = swAutoKontrast.isChecked,
                textSizeSp = sbText.progress.coerceIn(10, 22),
                zeigeBaustelle = swBaustelle.isChecked,
                zeigeUnterbaustelle = swUnter.isChecked,
                zeigeStreckeBahnhof = swStrecke.isChecked,
                zeigeGleisWeicheObjekt = swGleis.isChecked,
                zeigeKm = swKm.isChecked,
                zeigeGps = swGps.isChecked,
                zeigeDatumZeit = swDt.isChecked
            )
            ServiceLocator.overlayEinstellungenSpeicher.speichern(neu)
        }

        val onChange = View.OnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { speichern() }
        }

        swAktiv.setOnClickListener(onChange)
        swAutoKontrast.setOnClickListener(onChange)
        swAutoPosSmart.setOnClickListener(onChange)
        swBaustelle.setOnClickListener(onChange)
        swUnter.setOnClickListener(onChange)
        swStrecke.setOnClickListener(onChange)
        swGleis.setOnClickListener(onChange)
        swKm.setOnClickListener(onChange)
        swGps.setOnClickListener(onChange)
        swDt.setOnClickListener(onChange)

        rgPos.setOnCheckedChangeListener { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch { speichern() }
        }

        rgDarstellung.setOnCheckedChangeListener { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch { speichern() }
        }

        sbAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewLifecycleOwner.lifecycleScope.launch { speichern() }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbText.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewLifecycleOwner.lifecycleScope.launch { speichern() }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Erst alle UI-Werte laden, danach Speichern durch Nutzerinteraktion erlauben.
        // RadioGroup.check() kann sonst beim Öffnen der Seite bereits Listener auslösen.
        viewLifecycleOwner.lifecycleScope.launch {
            val cfg = ServiceLocator.overlayEinstellungenSpeicher.einstellungen.first()
            swAktiv.isChecked = cfg.aktiv
            swAutoKontrast.isChecked = cfg.autoKontrast

            when (cfg.darstellung) {
                OverlayEinstellungenSpeicher.Darstellung.NORMAL -> rgDarstellung.check(R.id.rbDarstellungNormal)
                OverlayEinstellungenSpeicher.Darstellung.KOMPAKT_2ZEILIG -> rgDarstellung.check(R.id.rbDarstellungKompakt)
            }

            when (cfg.position) {
                OverlayEinstellungenSpeicher.Position.AUTO -> rgPos.check(R.id.rbPosAuto)
                OverlayEinstellungenSpeicher.Position.LINKS_OBEN -> rgPos.check(R.id.rbPosLo)
                OverlayEinstellungenSpeicher.Position.RECHTS_OBEN -> rgPos.check(R.id.rbPosRo)
                OverlayEinstellungenSpeicher.Position.LINKS_UNTEN -> rgPos.check(R.id.rbPosLu)
                OverlayEinstellungenSpeicher.Position.RECHTS_UNTEN -> rgPos.check(R.id.rbPosRu)
            }

            swAutoPosSmart.isChecked = cfg.autoPositionSmart
            sbAlpha.progress = (cfg.alpha * 100).toInt().coerceIn(10, 90)
            sbText.progress = cfg.textSizeSp.coerceIn(10, 22)
            swBaustelle.isChecked = cfg.zeigeBaustelle
            swUnter.isChecked = cfg.zeigeUnterbaustelle
            swStrecke.isChecked = cfg.zeigeStreckeBahnhof
            swGleis.isChecked = cfg.zeigeGleisWeicheObjekt
            swKm.isChecked = cfg.zeigeKm
            swGps.isChecked = cfg.zeigeGps
            swDt.isChecked = cfg.zeigeDatumZeit
            initialisiert = true
        }
    }
}
