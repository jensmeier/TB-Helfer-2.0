package de.tbhelfer.ui.einstellungen

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.einstellungen.SyncEinstellungenSpeicher
import de.tbhelfer.server.SyncStatusSpeicher
import de.tbhelfer.server.SyncWorker

/**
 * Aufgeräumte Einstellungen mit den häufig benötigten Hauptbereichen.
 * Baustellen-Team/QR gehört direkt in die Baustellenliste, nicht in die Einstellungen.
 */
class EinstellungenFragment : Fragment(R.layout.fragment_einstellungen) {

    private lateinit var btnSynchronisation: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnNutzerFirma).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_nutzer)
        }

        btnSynchronisation = view.findViewById(R.id.btnSynchronisation)
        btnSynchronisation.setOnClickListener { zeigeSyncEinstellungen() }
        aktualisiereSyncButton()

        view.findViewById<View>(R.id.btnBenachrichtigungen).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_benachrichtigungen)
        }

        view.findViewById<View>(R.id.btnAnzeigeOverlay).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_anzeige_overlay)
        }

        view.findViewById<View>(R.id.btnKameraFoto).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_kamera)
        }

        view.findViewById<View>(R.id.btnBtbPdf).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_btb_pdf)
        }

        view.findViewById<View>(R.id.btnWidget).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_widget)
        }

        view.findViewById<View>(R.id.btnWartungDiagnose).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_wartung_diagnose)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btnSynchronisation.isInitialized) aktualisiereSyncButton()
    }

    private fun aktualisiereSyncButton() {
        val cfg = SyncEinstellungenSpeicher.laden(requireContext())
        val letzte = SyncStatusSpeicher.anzeige(requireContext())
        btnSynchronisation.text = buildString {
            append("Synchronisation\n")
            append(SyncEinstellungenSpeicher.zeitplanLabel(cfg.zeitplan))
            append(" · ")
            append(SyncEinstellungenSpeicher.netzLabel(cfg.netz))
            append(" · zuletzt ")
            append(letzte)
        }
    }

    private fun zeigeSyncEinstellungen() {
        val current = SyncEinstellungenSpeicher.laden(requireContext())
        val padding = (20 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()

        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, gap, padding, gap)
        }

        fun sectionTitle(text: String): TextView = TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, gap, 0, gap / 2)
        }

        val zeitplan = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
        val zeitplanButtons = SyncEinstellungenSpeicher.Zeitplan.entries.associateWith { value ->
            RadioButton(requireContext()).apply {
                text = SyncEinstellungenSpeicher.zeitplanLabel(value)
                isChecked = value == current.zeitplan
                id = View.generateViewId()
                zeitplan.addView(this)
            }
        }

        val netz = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
        val netzButtons = SyncEinstellungenSpeicher.Netz.entries.associateWith { value ->
            RadioButton(requireContext()).apply {
                text = SyncEinstellungenSpeicher.netzLabel(value)
                isChecked = value == current.netz
                id = View.generateViewId()
                netz.addView(this)
            }
        }

        box.addView(sectionTitle("Automatischer Sync"))
        box.addView(zeitplan)
        box.addView(sectionTitle("Erlaubtes Netz"))
        box.addView(netz)
        box.addView(TextView(requireContext()).apply {
            text = "Manuell ausgelöste Synchronisation bleibt bewusst möglich; die Netzregel steuert vor allem den automatischen Hintergrund-Sync."
            textSize = 12f
            alpha = 0.72f
            setPadding(0, gap, 0, 0)
        })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Synchronisation")
            .setView(box)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                val selectedZeitplan = zeitplanButtons.entries.firstOrNull { it.value.isChecked }?.key
                    ?: current.zeitplan
                val selectedNetz = netzButtons.entries.firstOrNull { it.value.isChecked }?.key
                    ?: current.netz
                SyncEinstellungenSpeicher.speichern(
                    requireContext(),
                    SyncEinstellungenSpeicher.Einstellungen(selectedZeitplan, selectedNetz)
                )
                SyncWorker.schedule(requireContext())
                aktualisiereSyncButton()
            }
            .show()
    }
}
