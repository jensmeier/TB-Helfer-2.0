package de.tbhelfer.ui.einstellungen.btbpdf

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.einstellungen.BtbPdfEinstellungenSpeicher
import de.tbhelfer.ui.einstellungen.nutzer.NutzerProfil
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Einstellungen für "BTB / PDF":
 * - Foto-Anhang Raster (z.B. 2x6)
 * - welche Metadaten im PDF pro Bild gedruckt werden
 */
class BtbPdfEinstellungenFragment : Fragment(R.layout.fragment_btb_pdf_einstellungen) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val acRaster = view.findViewById<MaterialAutoCompleteTextView>(R.id.acRaster)
        val etMaxFotosPdf = view.findViewById<TextInputEditText>(R.id.etMaxFotosPdf)

        val swDatum = view.findViewById<SwitchMaterial>(R.id.swDatum)
        val swUhrzeit = view.findViewById<SwitchMaterial>(R.id.swUhrzeit)
        val swKm = view.findViewById<SwitchMaterial>(R.id.swKm)
        val swGps = view.findViewById<SwitchMaterial>(R.id.swGps)
        val swGpsLink = view.findViewById<SwitchMaterial>(R.id.swGpsLink)
        val swBaustelle = view.findViewById<SwitchMaterial>(R.id.swBaustelle)
        val swKurztext = view.findViewById<SwitchMaterial>(R.id.swKurztext)
        val swNotiz = view.findViewById<SwitchMaterial>(R.id.swNotiz)
        val swFirma = view.findViewById<SwitchMaterial>(R.id.swFirma)
        val swIndikator = view.findViewById<SwitchMaterial>(R.id.swIndikator)
        val swDateiname = view.findViewById<SwitchMaterial>(R.id.swDateiname)

        // Raster Auswahl
        val rasterLabels = listOf(
            "Automatisch (Quer 2 / Hoch 3)",
            "1×6 (6 Bilder/Seite)",
            "3×3 (9 Bilder/Seite)",
            "1×1 (1 Bild/Seite)"
        )
        val rasterValues = listOf(
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_2x6,
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x6,
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_3x3,
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x1
        )

        acRaster.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, rasterLabels))
        acRaster.setOnClickListener { acRaster.showDropDown() }

        var maxFotosWirdGeladen = true

        // Laden
        viewLifecycleOwner.lifecycleScope.launch {
            val cfg = ServiceLocator.btbPdfEinstellungenSpeicher.einstellungen.first()

            val idx = rasterValues.indexOf(cfg.raster).coerceAtLeast(0)
            acRaster.setText(rasterLabels[idx], false)

            swDatum.isChecked = cfg.zeigeDatum
            swUhrzeit.isChecked = cfg.zeigeUhrzeit
            swKm.isChecked = cfg.zeigeKm
            swGps.isChecked = cfg.zeigeGps
            swGpsLink.isChecked = cfg.gpsAlsMapsLink
            swBaustelle.isChecked = cfg.zeigeBaustelle
            swKurztext.isChecked = cfg.zeigeKurztext
            swNotiz.isChecked = cfg.zeigeNotiz
            swFirma.isChecked = cfg.zeigeFirma
            swIndikator.isChecked = cfg.zeigeIndikator
            swDateiname.isChecked = cfg.zeigeDateiname

            val profil = NutzerSpeicher.laden(requireContext()) ?: NutzerProfil()
            etMaxFotosPdf.setText(profil.maxFotosInPdf.toString())
            maxFotosWirdGeladen = false
        }

        etMaxFotosPdf.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (maxFotosWirdGeladen) return
                val wert = s?.toString()?.trim()?.toIntOrNull()?.coerceIn(0, 500) ?: return
                val alt = NutzerSpeicher.laden(requireContext()) ?: NutzerProfil()
                NutzerSpeicher.speichern(requireContext(), alt.copy(maxFotosInPdf = wert))
            }
        })

        suspend fun speichern() {
            val rasterIdx = rasterLabels.indexOf(acRaster.text?.toString()).takeIf { it >= 0 } ?: 0
            val neu = BtbPdfEinstellungenSpeicher.Einstellungen(
                raster = rasterValues[rasterIdx],
                zeigeDatum = swDatum.isChecked,
                zeigeUhrzeit = swUhrzeit.isChecked,
                zeigeKm = swKm.isChecked,
                zeigeGps = swGps.isChecked,
                gpsAlsMapsLink = swGpsLink.isChecked,
                zeigeBaustelle = swBaustelle.isChecked,
                zeigeKurztext = swKurztext.isChecked,
                zeigeNotiz = swNotiz.isChecked,
                zeigeFirma = swFirma.isChecked,
                zeigeIndikator = swIndikator.isChecked,
                zeigeDateiname = swDateiname.isChecked
            )
            ServiceLocator.btbPdfEinstellungenSpeicher.speichern(neu)
        }

        val onChange = View.OnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { speichern() }
        }
        swDatum.setOnClickListener(onChange)
        swUhrzeit.setOnClickListener(onChange)
        swKm.setOnClickListener(onChange)
        swGps.setOnClickListener(onChange)
        swGpsLink.setOnClickListener(onChange)
        swBaustelle.setOnClickListener(onChange)
        swKurztext.setOnClickListener(onChange)
        swNotiz.setOnClickListener(onChange)
        swFirma.setOnClickListener(onChange)
        swIndikator.setOnClickListener(onChange)
        swDateiname.setOnClickListener(onChange)

        acRaster.setOnItemClickListener { _, _, _, _ ->
            viewLifecycleOwner.lifecycleScope.launch { speichern() }
        }
    }
}
