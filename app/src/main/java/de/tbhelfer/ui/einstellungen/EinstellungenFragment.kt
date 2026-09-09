package de.tbhelfer.ui.einstellungen

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R

/**
 * Aufgeräumte Einstellungen mit den häufig benötigten Hauptbereichen.
 */
class EinstellungenFragment : Fragment(R.layout.fragment_einstellungen) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnNutzerFirma).setOnClickListener {
            findNavController().navigate(R.id.action_einstellungen_to_nutzer)
        }

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
}
