package de.tbhelfer.ui.einstellungen.anzeige

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R

class AnzeigeOverlayFragment : Fragment(R.layout.fragment_anzeige_overlay) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnAnzeigeApp).setOnClickListener {
            findNavController().navigate(R.id.action_anzeige_overlay_to_ansicht)
        }
    }
}
