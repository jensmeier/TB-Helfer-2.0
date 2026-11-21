package de.tbhelfer.ui.baustelle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.streckenobjekte.BaustellenObjektArt
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Detailansicht einer Hauptbaustelle als „clean“ Accordion:
 * - Tippen auf Unterbaustelle klappt auf/zu
 * - Zähler: Unterpunkte / Gesamt
 * - Keine "Hoch"-Navigation, kein "Baustellenbaum"-Header
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BaustellenKnotenFragment : Fragment() {

    private val repo = ServiceLocator.baustellenBaumRepository
    private val speicher = ServiceLocator.aktuelleBaustelleSpeicher

    private lateinit var txtTitel: TextView
    private lateinit var chipUnterHaupt: TextView
    private lateinit var chipGesamtHaupt: TextView
    private lateinit var txtAktuellerKnoten: TextView
    private lateinit var btnNeu: Button
    private lateinit var recycler: RecyclerView

    private lateinit var adapter: BaustellenKnotenAdapter

    private var knotenId: Long = -1
    private var aktuellerRoot: BaustellenKnotenEntity? = null
    private var letzteAlleKnoten: List<BaustellenKnotenEntity> = emptyList()
    private var letzterAktuellId: Long? = null

    /** IDs, die im Accordion aufgeklappt sind. */
    private val expandedIds = mutableSetOf<Long>()

    /** Hilfsmaps aus dem letzten Build (für Tap-Handling). */
    private var childrenByParent: Map<Long, List<BaustellenKnotenEntity>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        knotenId = arguments?.getLong("knotenId", -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_baustellen_knoten, container, false)

        // Insets (Notch/Statusbar): Header immer sichtbar (bestehendes Padding behalten).
        val baseTop = v.paddingTop
        val baseBottom = v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }

        txtTitel = v.findViewById(R.id.txtTitel)
        chipUnterHaupt = v.findViewById(R.id.chipUnterHaupt)
        chipGesamtHaupt = v.findViewById(R.id.chipGesamtHaupt)
        txtAktuellerKnoten = v.findViewById(R.id.txtAktuellerKnoten)
        btnNeu = v.findViewById(R.id.btnNeuerUnterknoten)
        recycler = v.findViewById(R.id.recycler)

        adapter = BaustellenKnotenAdapter(
            mode = BaustellenKnotenAdapterMode.ACCORDION,
            onKlick = { knoten -> onItemClicked(knoten) },
            onLangKlick = { knoten -> zeigeKontextMenue(knoten) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        btnNeu.setOnClickListener {
            val parent = aktuellerRoot?.id ?: knotenId
            val bundle = Bundle().apply { putLong("parentId", parent) }
            findNavController().navigate(R.id.baustelleNeuFragment, bundle)
        }

        txtTitel.setOnLongClickListener {
            aktuellerRoot?.let { navigiereZurBearbeitung(it) }
            true
        }

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (knotenId <= 0) {
            txtTitel.text = getString(R.string.ui_error_knoten_nicht_gefunden)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repo.knotenMitIdFlow(knotenId)
                        .filterNotNull()
                        .flatMapLatest { root ->
                            repo.alleKnotenZuRoot(root.rootId)
                                .combine(speicher.aktuellerKnotenId) { alle, aktuellId ->
                                    Triple(root, alle, aktuellId)
                                }
                        }
                        .collect { (root, alle, aktuellId) ->
                            aktuellerRoot = root
                            letzteAlleKnoten = alle
                            letzterAktuellId = aktuellId

                            updateHeader(root, alle, aktuellId)
                            adapter.submitList(buildVisibleList(root, alle, aktuellId))
                        }
                }
            }
        }
    }

    private fun onItemClicked(knoten: BaustellenKnotenEntity) {
        val hasChildren = (childrenByParent[knoten.id]?.isNotEmpty() == true)

        if (hasChildren) {
            if (expandedIds.contains(knoten.id)) expandedIds.remove(knoten.id) else expandedIds.add(knoten.id)
            val root = aktuellerRoot ?: return
            adapter.submitList(buildVisibleList(root, letzteAlleKnoten, letzterAktuellId))
        } else {
            // Leaf: direkt vollständig bearbeiten.
            navigiereZurBearbeitung(knoten)
        }
    }

    private fun updateHeader(root: BaustellenKnotenEntity, alle: List<BaustellenKnotenEntity>, aktuellId: Long?) {
        txtTitel.text = root.hauptbaustellenName

        val unterbaustellen = alle.count { it.parentId == root.id }
        val gesamt = (alle.count { it.rootId == root.id } - 1).coerceAtLeast(0)

        chipUnterHaupt.text = getString(R.string.ui_chip_unterbaustellen, unterbaustellen)
        chipGesamtHaupt.text = getString(R.string.ui_chip_gesamt, gesamt)

        txtAktuellerKnoten.text = buildAktuellText(root, alle, aktuellId)
    }

    private fun buildAktuellText(root: BaustellenKnotenEntity, alle: List<BaustellenKnotenEntity>, aktuellId: Long?): String {
        if (aktuellId == null) return getString(R.string.ui_aktuell_keine)

        // Nur anzeigen, wenn der aktuelle Knoten zu dieser Hauptbaustelle gehört
        val aktuell = alle.firstOrNull { it.id == aktuellId } ?: return getString(R.string.ui_aktuell_keine)
        if (aktuell.rootId != root.id) return getString(R.string.ui_aktuell_andere)

        val byId = alle.associateBy { it.id }
        val parts = mutableListOf<String>()
        var cur: BaustellenKnotenEntity? = aktuell
        while (cur != null && cur.id != root.id) {
            parts.add(0, cur.knotenAnzeigeName())
            cur = cur.parentId?.let { byId[it] }
        }

        val pfad = (listOf(root.hauptbaustellenName) + parts).joinToString(" > ")
        return getString(R.string.ui_aktuell_pfad, pfad)
    }

    private fun buildVisibleList(
        root: BaustellenKnotenEntity,
        alle: List<BaustellenKnotenEntity>,
        aktuellId: Long?
    ): List<BaustellenKnotenUiModell> {
        // children map (nur für diese Root)
        val inRoot = alle.filter { it.rootId == root.id }
        childrenByParent = inRoot
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }

        // Descendant counts via DFS memo
        val memoDesc = mutableMapOf<Long, Int>()
        fun descendantsCount(id: Long): Int {
            memoDesc[id]?.let { return it }
            val kids = childrenByParent[id].orEmpty()
            var sum = kids.size
            for (c in kids) sum += descendantsCount(c.id)
            memoDesc[id] = sum
            return sum
        }

        val out = mutableListOf<BaustellenKnotenUiModell>()

        fun addChildren(parentId: Long, level: Int) {
            val kids = childrenByParent[parentId].orEmpty()
            for (k in kids) {
                val direct = childrenByParent[k.id].orEmpty().size
                val total = descendantsCount(k.id)
                val aufklappbar = direct > 0
                val aufgeklappt = expandedIds.contains(k.id)

                out.add(
                    BaustellenKnotenUiModell(
                        knoten = k,
                        // In der Unterbaustellen-Ansicht kein „AKTUELL“-Badge pro Zeile (verwirrt).
                        istAktuell = false,
                        ebene = level,
                        unterCount = direct,
                        gesamtCount = total,
                        unterLabel = UnterLabel.UNTERPUNKTE,
                        istAufklappbar = aufklappbar,
                        istAufgeklappt = aufgeklappt
                    )
                )

                if (aufklappbar && aufgeklappt) {
                    addChildren(k.id, level + 1)
                }
            }
        }

        // Wurzel ist nicht als Zeile sichtbar – wir beginnen mit ihren Kindern.
        addChildren(root.id, 0)
        return out
    }

    private fun zeigeKontextMenue(k: BaustellenKnotenEntity) {
        val aktionen = mutableListOf<Pair<String, () -> Unit>>()
        aktionen += getString(R.string.action_edit) to { navigiereZurBearbeitung(k) }

        val art = BaustellenObjektLogik.art(k)
        if (art == BaustellenObjektArt.WEICHE || art == BaustellenObjektArt.DKW_KREUZUNG) {
            aktionen += "Streckendaten optional" to { oeffneStreckendaten(k, art) }
        }

        aktionen += getString(R.string.action_add_child) to {
            val bundle = Bundle().apply { putLong("parentId", k.id) }
            findNavController().navigate(R.id.baustelleNeuFragment, bundle)
        }
        aktionen += getString(R.string.action_delete) to {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_delete_title))
                .setMessage(getString(R.string.dialog_delete_message, k.knotenAnzeigeName()))
                .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                    lifecycleScope.launch {
                        val aktuellerKnotenId = speicher.aktuellerKnotenId.first()
                        val root = aktuellerRoot
                        if (aktuellerKnotenId != null && root != null) {
                            val pfad = repo.pfadVonRootBis(aktuellerKnotenId)
                            val loeschtAktuellenPfad = pfad.any { it.id == k.id }
                            if (loeschtAktuellenPfad) {
                                speicher.setzenNurHaupt(root.id, root.altBaustelleId)
                            }
                        }
                        repo.loescheTeilbaum(k.id)
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_action_title))
            .setItems(aktionen.map { it.first }.toTypedArray()) { _, which ->
                aktionen.getOrNull(which)?.second?.invoke()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun oeffneStreckendaten(k: BaustellenKnotenEntity, art: BaustellenObjektArt) {
        val objektTyp = if (art == BaustellenObjektArt.WEICHE) "WEICHE" else "DKW_KREUZUNG"
        val args = Bundle().apply {
            putBoolean("autoOpenStreckenObjektEditor", true)
            putString("prefillBetriebsstelle", k.bahnhofName.orEmpty())
            putString("prefillObjekt", k.gleisOderWeiche ?: k.knotenName.orEmpty())
            putString("prefillObjektTyp", objektTyp)
        }
        findNavController().navigate(R.id.importExportFragment, args)
    }


    private fun navigiereZurBearbeitung(k: BaustellenKnotenEntity) {
        val bundle = Bundle().apply {
            putLong("parentId", k.parentId ?: -1L)
            putLong("editKnotenId", k.id)
        }
        findNavController().navigate(R.id.baustelleNeuFragment, bundle)
    }

    private fun BaustellenKnotenEntity.knotenAnzeigeName(): String {
        return when (typ) {
            BaustellenKnotenTyp.HAUPT -> hauptbaustellenName
            BaustellenKnotenTyp.FREIE_STRECKE -> knotenName?.takeIf { it.isNotBlank() }
                ?: listOf("Freie Strecke", streckenkennung).filter { !it.isNullOrBlank() }.joinToString(" ")
            BaustellenKnotenTyp.BAHNHOF -> knotenName?.takeIf { it.isNotBlank() }
                ?: listOf("Bahnhof", bahnhofName).filter { !it.isNullOrBlank() }.joinToString(" ")
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> knotenName ?: "Objekt"
        }
    }
}
