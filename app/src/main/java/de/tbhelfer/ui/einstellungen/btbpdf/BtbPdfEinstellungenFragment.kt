package de.tbhelfer.ui.einstellungen.btbpdf

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.einstellungen.BtbPdfEinstellungenSpeicher
import de.tbhelfer.einstellungen.BtbPdfLogoLayoutSpeicher
import de.tbhelfer.server.CompanyPdfLogoApi
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.ui.bautagebuch.BtbPdfViewerActivity
import de.tbhelfer.ui.einstellungen.nutzer.NutzerProfil
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Einstellungen fuer BTB/PDF inklusive echter PDF-Logo-Vorschau. */
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

        val tvLogoStatus = view.findViewById<TextView>(R.id.tvLogoLayoutStatus)
        val tvLogoServerStatus = view.findViewById<TextView>(R.id.tvLogoServerStatus)
        val swLogoShowName = view.findViewById<SwitchMaterial>(R.id.swLogoShowName)
        val swLogoShowEmail = view.findViewById<SwitchMaterial>(R.id.swLogoShowEmail)
        val swLogoShowPhone = view.findViewById<SwitchMaterial>(R.id.swLogoShowPhone)
        val swLogoContactsInImage = view.findViewById<SwitchMaterial>(R.id.swLogoContactsInImage)
        val cardLogoPreview = view.findViewById<MaterialCardView>(R.id.cardLogoPreview)
        val imgLogoPreview = view.findViewById<ImageView>(R.id.imgLogoPreview)
        val tvLogoPreviewHint = view.findViewById<TextView>(R.id.tvLogoPreviewHint)

        // Fixed by the shared BTB contract; preserve historical stored values without offering ineffective choices.
        acRaster.setText("3 Bilder pro Reihe", false)
        acRaster.isEnabled = false
        var gespeichertesRaster = BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_3x3

        var maxFotosWirdGeladen = true
        viewLifecycleOwner.lifecycleScope.launch {
            val cfg = ServiceLocator.btbPdfEinstellungenSpeicher.einstellungen.first()
            gespeichertesRaster = cfg.raster
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

        suspend fun speichernPdfOptionen() {
            ServiceLocator.btbPdfEinstellungenSpeicher.speichern(
                BtbPdfEinstellungenSpeicher.Einstellungen(
                    raster = gespeichertesRaster,
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
            )
        }

        val onMetaChange = View.OnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { speichernPdfOptionen() }
        }
        listOf(swDatum, swUhrzeit, swKm, swGps, swGpsLink, swBaustelle, swKurztext, swNotiz, swFirma, swIndikator, swDateiname)
            .forEach { it.setOnClickListener(onMetaChange) }

        fun refreshLogoPreview() {
            val profil = NutzerSpeicher.laden(requireContext())
            val logoFile = profil?.logoDateiname
                ?.let { File(NutzerSpeicher.ordner(requireContext()), it) }
                ?.takeIf { it.exists() && it.isFile }
            val bmp = logoFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
            if (bmp != null) {
                imgLogoPreview.visibility = View.VISIBLE
                imgLogoPreview.setImageBitmap(bmp)
                tvLogoPreviewHint.text = "Logo antippen → echte PDF-Vorschau"
            } else {
                imgLogoPreview.setImageDrawable(null)
                imgLogoPreview.visibility = View.GONE
                tvLogoPreviewHint.text = "Kein Firmenlogo hinterlegt · antippen für PDF-Vorschau mit weißem Logobereich"
            }
        }

        fun applyLogoUi(cfg: BtbPdfLogoLayoutSpeicher.Einstellungen) {
            tvLogoStatus.text = "Logo: ${cfg.logoWidthPt} pt · X ${cfg.offsetXPt.withSign()} · Y ${cfg.offsetYPt.withSign()}"
            swLogoShowName.isChecked = cfg.showCompanyName
            swLogoShowEmail.isChecked = cfg.showCompanyEmail
            swLogoShowPhone.isChecked = cfg.showCompanyPhone
            swLogoContactsInImage.isChecked = cfg.contactsInImage
            swLogoShowEmail.isEnabled = !cfg.contactsInImage
            swLogoShowPhone.isEnabled = !cfg.contactsInImage
        }

        fun mutateLogo(block: (BtbPdfLogoLayoutSpeicher.Einstellungen) -> BtbPdfLogoLayoutSpeicher.Einstellungen) {
            val current = BtbPdfLogoLayoutSpeicher.laden(requireContext())
            val next = BtbPdfLogoLayoutSpeicher.normalize(block(current))
            BtbPdfLogoLayoutSpeicher.speichern(requireContext(), next)
            applyLogoUi(next)
            tvLogoServerStatus.text = "Lokal geändert · für andere Geräte noch firmenweit speichern"
        }

        fun openPdfPreview() {
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { BtbPdfLayoutVorschau.erstellen(appContext) }
                }.onSuccess { file ->
                    startActivity(
                        Intent(requireContext(), BtbPdfViewerActivity::class.java)
                            .putExtra(BtbPdfViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                            .putExtra(BtbPdfViewerActivity.EXTRA_TITLE, "BTB/PDF Vorschau")
                    )
                }.onFailure {
                    tvLogoServerStatus.text = "PDF-Vorschau konnte nicht erstellt werden: ${it.message.orEmpty()}"
                }
            }
        }

        refreshLogoPreview()
        applyLogoUi(BtbPdfLogoLayoutSpeicher.laden(requireContext()))
        cardLogoPreview.setOnClickListener { openPdfPreview() }
        view.findViewById<MaterialButton>(R.id.btnLogoLeft).setOnClickListener { mutateLogo { it.copy(offsetXPt = it.offsetXPt - 5) } }
        view.findViewById<MaterialButton>(R.id.btnLogoRight).setOnClickListener { mutateLogo { it.copy(offsetXPt = it.offsetXPt + 5) } }
        view.findViewById<MaterialButton>(R.id.btnLogoUp).setOnClickListener { mutateLogo { it.copy(offsetYPt = it.offsetYPt - 5) } }
        view.findViewById<MaterialButton>(R.id.btnLogoDown).setOnClickListener { mutateLogo { it.copy(offsetYPt = it.offsetYPt + 5) } }
        view.findViewById<MaterialButton>(R.id.btnLogoSmaller).setOnClickListener { mutateLogo { it.copy(logoWidthPt = it.logoWidthPt - 5) } }
        view.findViewById<MaterialButton>(R.id.btnLogoLarger).setOnClickListener { mutateLogo { it.copy(logoWidthPt = it.logoWidthPt + 5) } }
        view.findViewById<MaterialButton>(R.id.btnLogoReset).setOnClickListener {
            BtbPdfLogoLayoutSpeicher.zuruecksetzen(requireContext())
            applyLogoUi(BtbPdfLogoLayoutSpeicher.laden(requireContext()))
            tvLogoServerStatus.text = "Standard lokal wiederhergestellt · noch nicht firmenweit gespeichert"
        }

        swLogoShowName.setOnClickListener { mutateLogo { it.copy(showCompanyName = swLogoShowName.isChecked) } }
        swLogoShowEmail.setOnClickListener { mutateLogo { it.copy(showCompanyEmail = swLogoShowEmail.isChecked) } }
        swLogoShowPhone.setOnClickListener { mutateLogo { it.copy(showCompanyPhone = swLogoShowPhone.isChecked) } }
        swLogoContactsInImage.setOnClickListener { mutateLogo { it.copy(contactsInImage = swLogoContactsInImage.isChecked) } }

        view.findViewById<MaterialButton>(R.id.btnLogoServerSave).setOnClickListener {
            val appContext = requireContext().applicationContext
            val local = BtbPdfLogoLayoutSpeicher.laden(appContext)
            val token = SecureTokenStore(appContext).load()
            if (token.isNullOrBlank()) {
                tvLogoServerStatus.text = "Nicht aktiviert · Layout bleibt lokal gespeichert"
                return@setOnClickListener
            }
            tvLogoServerStatus.text = "Firmenweites Layout wird gespeichert …"
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { CompanyPdfLogoApi().save(token, local) }
                when (result) {
                    is CompanyPdfLogoApi.Result.Success -> {
                        BtbPdfLogoLayoutSpeicher.speichern(appContext, result.layout)
                        applyLogoUi(result.layout)
                        tvLogoServerStatus.text = "Firmenweit gespeichert · Version ${result.layout.serverVersion}"
                    }
                    CompanyPdfLogoApi.Result.SchemaRequired ->
                        tvLogoServerStatus.text = "Server-Erweiterung noch nicht aktiv · lokal gespeichert"
                    CompanyPdfLogoApi.Result.SessionInvalid ->
                        tvLogoServerStatus.text = "Geräte-Sitzung ungültig · lokal gespeichert"
                    is CompanyPdfLogoApi.Result.Error ->
                        tvLogoServerStatus.text = "Server: ${result.message} · lokal gespeichert"
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnPdfVorschau).setOnClickListener { openPdfPreview() }

        // Beim Oeffnen vorhandenes firmenweites Layout nachziehen. Ein Server ohne Schema 12
        // ist kein Fehler; dann bleibt die lokale Offline-Einstellung aktiv.
        val appContext = requireContext().applicationContext
        val token = SecureTokenStore(appContext).load()
        if (token.isNullOrBlank()) {
            tvLogoServerStatus.text = "PDF-Logo-Layout lokal · Gerät nicht mit Server aktiviert"
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = withContext(Dispatchers.IO) { CompanyPdfLogoApi().load(token) }) {
                    is CompanyPdfLogoApi.Result.Success -> {
                        BtbPdfLogoLayoutSpeicher.speichern(appContext, result.layout)
                        applyLogoUi(result.layout)
                        tvLogoServerStatus.text = "Firmenweites Layout geladen · Version ${result.layout.serverVersion}"
                    }
                    CompanyPdfLogoApi.Result.SchemaRequired ->
                        tvLogoServerStatus.text = "Server-Erweiterung noch nicht aktiv · lokales Layout wird verwendet"
                    CompanyPdfLogoApi.Result.SessionInvalid ->
                        tvLogoServerStatus.text = "Geräte-Sitzung ungültig · lokales Layout wird verwendet"
                    is CompanyPdfLogoApi.Result.Error ->
                        tvLogoServerStatus.text = "Offline/lokal · ${result.message}"
                }
            }
        }
    }

    private fun Int.withSign(): String = if (this >= 0) "+$this" else toString()
}

