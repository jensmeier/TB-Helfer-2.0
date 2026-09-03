package de.tbhelfer.ui.baustelle

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Liste der Hauptbaustellen.
 * Tippen öffnet den Baustellenbaum (Unterbaustellen).
 */
class BaustellenFragment : Fragment(R.layout.fragment_baustellen) {

    private val repo = ServiceLocator.baustellenBaumRepository
    private val fotoRepo = ServiceLocator.fotoRepository
    private val speicher = ServiceLocator.aktuelleBaustelleSpeicher

    private lateinit var adapter: BaustellenKnotenAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerBaustellen)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter = BaustellenKnotenAdapter(
            mode = BaustellenKnotenAdapterMode.HAUPTLISTE,
            onKlick = { haupt ->
                val bundle = Bundle().apply { putLong("knotenId", haupt.id) }
                findNavController().navigate(R.id.baustellenKnotenFragment, bundle)
            },
            onLangKlick = { haupt ->
                val items = arrayOf(
                    getString(R.string.action_edit),
                    getString(R.string.action_set_as_current_haupt),
                    getString(R.string.action_btb_history),
                    getString(R.string.action_maengel_list),
                    "Abnahmeliste öffnen",
                    "ZIP von-bis Datum teilen",
                    "ZIP komplette Baustelle teilen",
                    getString(R.string.btn_delete)
                )

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(haupt.hauptbaustellenName)
                    .setItems(items) { _, which ->
                        when (which) {
                            0 -> {
                                val bundle = Bundle().apply { putLong("editKnotenId", haupt.id) }
                                findNavController().navigate(R.id.baustelleNeuFragment, bundle)
                            }
                            1 -> viewLifecycleOwner.lifecycleScope.launch {
                                speicher.setzenNurHaupt(
                                    hauptKnotenId = haupt.id,
                                    altBaustelleId = haupt.altBaustelleId
                                )
                            }
                            2 -> {
                                val bundle = Bundle().apply {
                                    putLong("hauptKnotenId", haupt.id)
                                    putLong("altBaustelleId", haupt.altBaustelleId)
                                    putString("baustelleName", haupt.hauptbaustellenName)
                                }
                                findNavController().navigate(R.id.btbVerlaufFragment, bundle)
                            }
                            3 -> {
                                val bundle = Bundle().apply {
                                    putLong("hauptKnotenId", haupt.id)
                                    putLong("altBaustelleId", haupt.altBaustelleId)
                                    putString("baustelleName", haupt.hauptbaustellenName)
                                }
                                findNavController().navigate(R.id.maengellisteFragment, bundle)
                            }
                            4 -> {
                                val bundle = Bundle().apply {
                                    putLong("baustelleId", haupt.altBaustelleId)
                                    putString("baustelleName", haupt.hauptbaustellenName)
                                    putLong("hauptKnotenId", haupt.id)
                                    putString("filter", "ALLE")
                                }
                                findNavController().navigate(R.id.abnahmelisteFragment, bundle)
                            }
                            5 -> zeigeZipVonBisDialog(haupt)
                            6 -> exportiereBaustellenZip(haupt, null, null)
                            7 -> zeigeKomplettLoeschenDialog(haupt)
                        }
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            },
            onMaengelKlick = { haupt ->
                val bundle = Bundle().apply {
                    putLong("hauptKnotenId", haupt.id)
                    putLong("altBaustelleId", haupt.altBaustelleId)
                    putString("baustelleName", haupt.hauptbaustellenName)
                }
                findNavController().navigate(R.id.maengellisteFragment, bundle)
            },
            onAbnahmenKlick = { haupt, filter ->
                val bundle = Bundle().apply {
                    putLong("baustelleId", haupt.altBaustelleId)
                    putString("baustelleName", haupt.hauptbaustellenName)
                    putLong("hauptKnotenId", haupt.id)
                    putString("filter", filter)
                }
                findNavController().navigate(R.id.abnahmelisteFragment, bundle)
            }
        )
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btnNeueBaustelle).setOnClickListener {
            findNavController().navigate(R.id.baustelleNeuFragment)
        }

        view.findViewById<View>(R.id.btnAktuelleAnzeigen).setOnClickListener {
            findNavController().navigate(R.id.aktuelleBaustelleFragment)
        }

        // Liste + "aktuell" markieren
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repo.alleHauptbaustellen(),
                    repo.alleKnotenFlow(),
                    speicher.aktuelleHauptbaustelleKnotenId,
                    fotoRepo.alleFotos()
                ) { hauptListe, alleKnoten, aktuelleHauptId, alleFotos ->
                    val countByRoot: Map<Long, Pair<Int, Int>> = hauptListe.associate { root ->
                        val unter = alleKnoten.count { it.parentId == root.id }
                        val gesamt = (alleKnoten.count { it.rootId == root.id } - 1).coerceAtLeast(0)
                        root.id to (unter to gesamt)
                    }

                    val bilderByBaustelleId = alleFotos.groupingBy { it.baustelleId }.eachCount()
                    val appContext = requireContext().applicationContext
                    val btbByRoot = withContext(Dispatchers.IO) {
                        hauptListe.associate { root ->
                            val count = BautagebuchSpeicher.anzahlBtbsFuerBaustelle(
                                appContext,
                                root.altBaustelleId,
                                root.hauptbaustellenName
                            )
                            root.id to count
                        }
                    }
                    val maengelByRoot = withContext(Dispatchers.IO) {
                        hauptListe.associate { root ->
                            root.id to MangelFachlogik.zaehlerFuerBaustelle(
                                appContext,
                                root.altBaustelleId,
                                root.hauptbaustellenName
                            )
                        }
                    }
                    val abnahmenByRoot = withContext(Dispatchers.IO) {
                        hauptListe.associate { root ->
                            root.id to AbnahmeSpeicher.zaehler(appContext, root.altBaustelleId)
                        }
                    }

                    hauptListe.map { knoten ->
                        val (unter, gesamt) = countByRoot[knoten.id] ?: (0 to 0)
                        val altId = knoten.altBaustelleId
                        val mz = maengelByRoot[knoten.id] ?: MangelFachlogik.Zaehler()
                        val az = abnahmenByRoot[knoten.id] ?: AbnahmeSpeicher.Zaehler()
                        BaustellenKnotenUiModell(
                            knoten = knoten,
                            istAktuell = (aktuelleHauptId == knoten.id),
                            ebene = 0,
                            unterCount = unter,
                            gesamtCount = gesamt,
                            bilderCount = bilderByBaustelleId[altId] ?: 0,
                            btbCount = btbByRoot[knoten.id] ?: 0,
                            mangelOffenCount = mz.offen,
                            mangelInArbeitCount = mz.inArbeit,
                            mangelErledigtCount = mz.erledigt,
                            mangelAbgelehntCount = mz.abgelehnt,
                            abnahmeJaCount = az.ja,
                            abnahmeNeinCount = az.nein,
                            abnahmePruefungCount = az.inPruefung,
                            unterLabel = UnterLabel.UNTERBAUSTELLEN,
                            istAufklappbar = false,
                            istAufgeklappt = false
                        )
                    }
                }.collect { uiListe ->
                    adapter.submitList(uiListe)
                }
            }
        }
    }


    private val exportDateFmtDe: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun parseExportDatum(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isBlank()) return null
        return runCatching { LocalDate.parse(s) }.getOrNull()
            ?: runCatching { LocalDate.parse(s, exportDateFmtDe) }.getOrNull()
    }

    private fun zeigeZipVonBisDialog(haupt: BaustellenKnotenEntity) {
        val heute = LocalDate.now()
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }
        val von = EditText(requireContext()).apply {
            hint = "von Datum, z.B. ${heute.format(exportDateFmtDe)}"
            setSingleLine(true)
        }
        val bis = EditText(requireContext()).apply {
            hint = "bis Datum, z.B. ${heute.format(exportDateFmtDe)}"
            setSingleLine(true)
        }
        box.addView(TextView(requireContext()).apply { text = "Datum leer lassen = offen. Format: TT.MM.JJJJ oder JJJJ-MM-TT" })
        box.addView(von)
        box.addView(bis)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ZIP von-bis Datum")
            .setView(box)
            .setPositiveButton("ZIP teilen") { _, _ ->
                val v = parseExportDatum(von.text?.toString().orEmpty())
                val b = parseExportDatum(bis.text?.toString().orEmpty())
                if (von.text?.toString()?.trim().orEmpty().isNotBlank() && v == null) {
                    Toast.makeText(requireContext(), "Von-Datum ungültig", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (bis.text?.toString()?.trim().orEmpty().isNotBlank() && b == null) {
                    Toast.makeText(requireContext(), "Bis-Datum ungültig", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                exportiereBaustellenZip(haupt, v, b)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun zeigeExportFortschritt(): Pair<androidx.appcompat.app.AlertDialog, TextView> {
        val tv = TextView(requireContext()).apply {
            text = "Rest: 100 %"
            textSize = 18f
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle("ZIP wird erstellt")
            .setMessage("Export läuft ...")
            .setView(tv)
            .setCancelable(false)
            .create()
        dlg.show()
        return dlg to tv
    }

    private fun starteCountdown(tv: TextView): Job = lifecycleScope.launch {
        for (p in 100 downTo 0) {
            tv.text = "Rest: $p %"
            delay(85)
        }
    }

    private fun exportiereBaustellenZip(haupt: BaustellenKnotenEntity, von: LocalDate?, bis: LocalDate?) {
        lifecycleScope.launch {
            val (dlg, tv) = zeigeExportFortschritt()
            val countdown = starteCountdown(tv)
            try {
                val result = withContext(Dispatchers.IO) {
                    BtbExportPaket.erstellenBaustellenExport(
                        context = requireContext().applicationContext,
                        baustelleId = haupt.altBaustelleId,
                        baustelleName = haupt.hauptbaustellenName,
                        vonDatum = von,
                        bisDatum = bis
                    )
                }
                countdown.cancel()
                tv.text = "Rest: 0 %"
                dlg.dismiss()
                Toast.makeText(requireContext(), "ZIP erstellt: ${result.fotosExportiert} Bilder", Toast.LENGTH_SHORT).show()
                BtbExportPaket.shareZip(requireContext(), result.zipFile)
            } catch (t: Throwable) {
                countdown.cancel()
                dlg.dismiss()
                Toast.makeText(requireContext(), "ZIP konnte nicht erstellt werden: ${t.message ?: "Fehler"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun zeigeKomplettLoeschenDialog(haupt: BaustellenKnotenEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_project_full_title))
            .setMessage(getString(R.string.dialog_delete_project_full_message, haupt.hauptbaustellenName))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                zeigeLetzteLoeschWarnung(haupt)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun zeigeLetzteLoeschWarnung(haupt: BaustellenKnotenEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_project_final_title))
            .setMessage(getString(R.string.dialog_delete_project_final_message, haupt.hauptbaustellenName))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    loescheBaustelleKomplett(haupt)
                    Toast.makeText(requireContext(), getString(R.string.dialog_delete_project_done), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private suspend fun loescheBaustelleKomplett(haupt: BaustellenKnotenEntity) {
        val appContext = requireContext().applicationContext
        val altId = haupt.altBaustelleId

        withContext(Dispatchers.IO) {
            val fotos = fotoRepo.fotosFuerBaustelleOnce(altId)
            fotos.forEach { foto ->
                runCatching { File(foto.dateipfad).delete() }
            }

            BautagebuchSpeicher.loescheAlleBtbsFuerBaustelle(
                appContext,
                altId,
                haupt.hauptbaustellenName
            )

            ServiceLocator.baustellenRepository.baustelleMitIdOnce(altId)?.let { altBaustelle ->
                ServiceLocator.baustellenRepository.loeschen(altBaustelle)
            }

            repo.loescheTeilbaum(haupt.id)
        }

        val aktuelleHauptId = speicher.aktuelleHauptbaustelleKnotenId.first()
        if (aktuelleHauptId == haupt.id) {
            speicher.loeschen()
        }
    }


    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

}
