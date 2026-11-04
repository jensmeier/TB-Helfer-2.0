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
import de.tbhelfer.server.Schema13Worker
import de.tbhelfer.server.SelectiveSyncWorker
import de.tbhelfer.server.SyncStatusSpeicher
import de.tbhelfer.server.SyncWorker

/**
 * Aufgeräumte Einstellungen mit den häufig benötigten Hauptbereichen.
 * Baustellen-Team/QR gehört direkt in die Baustellenliste, nicht in die Einstellungen.
 */
class EinstellungenFragment : Fragment(R.layout.fragment_einstellungen) {

    private lateinit var btnSynchronisation: MaterialButton
    private var weitereGeoeffnet = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        weitereGeoeffnet = savedInstanceState?.getBoolean("settings_extras_open") ?: weitereGeoeffnet
        val weitere = view.findViewById<View>(R.id.weitereEinstellungen)
        val weitereButton = view.findViewById<MaterialButton>(R.id.btnWeitereEinstellungen)
        fun zeigeWeitere() {
            weitere.visibility = if (weitereGeoeffnet) View.VISIBLE else View.GONE
            weitereButton.setText(if (weitereGeoeffnet) R.string.settings_extras_hide else R.string.settings_extras_show)
        }
        zeigeWeitere()
        weitereButton.setOnClickListener {
            weitereGeoeffnet = !weitereGeoeffnet
            zeigeWeitere()
        }

        view.findViewById<View>(R.id.btnNutzerFirma).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_nutzer)
        }

        view.findViewById<View>(R.id.btnSpracheingabe).setOnClickListener {
            startActivity(android.content.Intent(requireContext(), de.tbhelfer.speech.SpeechCaptureActivity::class.java))
        }

        btnSynchronisation = view.findViewById(R.id.btnSynchronisation)
        btnSynchronisation.setOnClickListener { zeigeSyncEinstellungen() }
        aktualisiereSyncButton()

        view.findViewById<View>(R.id.btnBenachrichtigungen).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_benachrichtigungen)
        }

        view.findViewById<View>(R.id.btnAnzeigeOverlay).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_ansicht)
        }

        view.findViewById<View>(R.id.btnFotoBeschriftung).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_overlay)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("settings_extras_open", weitereGeoeffnet)
    }

    private fun aktualisiereSyncButton() {
        val cfg = SyncEinstellungenSpeicher.laden(requireContext())
        val syncStand = if (SyncStatusSpeicher.letzterErfolgEpochMs(requireContext()) == null) {
            "Noch nicht synchronisiert"
        } else {
            "zuletzt ${SyncStatusSpeicher.anzeige(requireContext())}"
        }
        btnSynchronisation.text = buildString {
            append("Synchronisierung\n")
            append("Automatisch")
            append(" · ")
            append(SyncEinstellungenSpeicher.netzLabel(cfg.netz))
            append(" · ")
            append(syncStand)
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

        val netz = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
        val netzButtons = SyncEinstellungenSpeicher.Netz.entries.associateWith { value ->
            RadioButton(requireContext()).apply {
                text = SyncEinstellungenSpeicher.netzLabel(value)
                isChecked = value == current.netz
                id = View.generateViewId()
                netz.addView(this)
            }
        }

        box.addView(sectionTitle("Automatischer Abgleich"))
        box.addView(TextView(requireContext()).apply {
            text = "Änderungen werden automatisch mit dem Server abgeglichen. Hier legst du fest, welches Netz die App dafür verwenden darf."
            textSize = 12f
            alpha = 0.78f
            setPadding(0, 0, 0, gap)
        })
        box.addView(sectionTitle("Erlaubtes Netz"))
        box.addView(netz)
        box.addView(TextView(requireContext()).apply {
            text = "Ohne passende Verbindung bleiben Änderungen auf dem Handy gespeichert. Sobald die Verbindung wieder passt, kann der automatische Abgleich fortgesetzt werden."
            textSize = 12f
            alpha = 0.72f
            setPadding(0, gap, 0, 0)
        })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Synchronisation")
            .setView(box)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                val selectedNetz = netzButtons.entries.firstOrNull { it.value.isChecked }?.key
                    ?: current.netz
                SyncEinstellungenSpeicher.speichern(
                    requireContext(),
                    SyncEinstellungenSpeicher.Einstellungen(selectedNetz)
                )
                // Alte periodische Vollsync-Einträge sicher entfernen; Dirty-Scopes bleiben erhalten.
                SyncWorker.schedule(requireContext())
                Schema13Worker.schedule(requireContext())
                SelectiveSyncWorker.enqueueIfPending(requireContext())
                aktualisiereSyncButton()
            }
            .show()
    }
}



