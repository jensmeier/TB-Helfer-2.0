@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package de.tbhelfer.ui.baustelle

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import de.tbhelfer.einstellungen.AktiveBaustelleValidator
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class AktuelleBaustelleFragment : Fragment(R.layout.fragment_aktuelle_baustelle) {

    private val repo = ServiceLocator.baustellenBaumRepository
    private val speicher = ServiceLocator.aktuelleBaustelleSpeicher

    private var aktuelleHauptId: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txt = view.findViewById<TextView>(R.id.txtAktuelleBaustelle)

        viewLifecycleOwner.lifecycleScope.launch {
            AktiveBaustelleValidator.bereinigeWennUngueltig(
                speicher = speicher,
                baumRepo = repo,
                baustellenRepo = ServiceLocator.baustellenRepository
            )
        }

        // Aktuellen Knoten anzeigen:
        // - Hauptbaustelle ist immer vorhanden (aktuelleHaupt...)
        // - Unterbaustelle ist optional (aktuellerKnotenId)
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                speicher.aktuellerKnotenId,
                speicher.aktuelleHauptbaustelleKnotenId
            ) { unterId, hauptId ->
                unterId ?: hauptId
            }
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else repo.knotenMitIdFlow(id)
                }
                .collectLatest { knoten ->
                    if (knoten == null) {
                        txt.text = "Keine aktuelle Baustelle gewählt."
                        aktuelleHauptId = null
                    } else {
                        aktuelleHauptId = knoten.rootId

                        val titel = "Hauptbaustelle: ${knoten.hauptbaustellenName}"
                        val details = when (knoten.typ) {
                            BaustellenKnotenTyp.FREIE_STRECKE -> {
                                val k = knoten.streckenkennung ?: "—"
                                val von = knoten.kmVon?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "—"
                                val bis = knoten.kmBis?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "—"
                                "Freie Strecke $k\nKm $von bis Km $bis"
                            }
                            BaustellenKnotenTyp.BAHNHOF -> {
                                val b = knoten.bahnhofName ?: "—"
                                val g = knoten.gleisOderWeiche ?: "—"
                                "Bahnhof $b\nGleis/Weiche: $g"
                            }
                            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
                                val name = knoten.knotenName ?: "Objekt"
                                val von = knoten.kmVon?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "—"
                                val bis = knoten.kmBis?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "—"
                                "Objekt: $name\nKm $von bis Km $bis"
                            }
                            BaustellenKnotenTyp.HAUPT -> "(Hauptbaustelle)"
                        }

                        txt.text = "$titel\n\n$details"
                    }
                }
        }

        view.findViewById<View>(R.id.btnZurBaustellenliste).apply {
            setOnClickListener {
                val id = aktuelleHauptId
                if (id == null) {
                    findNavController().navigate(R.id.baustellenFragment)
                } else {
                    val b = Bundle().apply { putLong("knotenId", id) }
                    findNavController().navigate(R.id.baustellenKnotenFragment, b)
                }
            }
        }

        // Back-Button ist optional (kann aus Layout entfernt werden)
        view.findViewById<View?>(R.id.btnZurueck)?.setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
