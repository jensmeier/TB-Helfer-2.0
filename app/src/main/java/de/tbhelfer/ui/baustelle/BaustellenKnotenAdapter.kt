package de.tbhelfer.ui.baustelle

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import de.tbhelfer.R
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.server.TeamDisplayCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class BaustellenKnotenAdapterMode {
    HAUPTLISTE,
    ACCORDION
}

class BaustellenKnotenAdapter(
    private val mode: BaustellenKnotenAdapterMode,
    private val onKlick: (BaustellenKnotenEntity) -> Unit,
    private val onLangKlick: (BaustellenKnotenEntity) -> Unit,
    private val onMaengelKlick: ((BaustellenKnotenEntity) -> Unit)? = null,
    private val onAbnahmenKlick: ((BaustellenKnotenEntity, String) -> Unit)? = null
) : ListAdapter<BaustellenKnotenUiModell, BaustellenKnotenAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<BaustellenKnotenUiModell>() {
        override fun areItemsTheSame(oldItem: BaustellenKnotenUiModell, newItem: BaustellenKnotenUiModell): Boolean =
            oldItem.knoten.id == newItem.knoten.id

        override fun areContentsTheSame(oldItem: BaustellenKnotenUiModell, newItem: BaustellenKnotenUiModell): Boolean =
            oldItem == newItem
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowRoot: View = itemView.findViewById(R.id.rowRoot)
        val titel: TextView = itemView.findViewById(R.id.txtTitel)
        val details: TextView = itemView.findViewById(R.id.txtDetails)
        val aktuell: TextView = itemView.findViewById(R.id.txtAktuell)
        val chipUnter: TextView = itemView.findViewById(R.id.chipUnter)
        val chipGesamt: TextView = itemView.findViewById(R.id.chipGesamt)
        val chipBilder: TextView = itemView.findViewById(R.id.chipBilder)
        val chipBtb: TextView = itemView.findViewById(R.id.chipBtb)
        val rowMaengelStatus: View = itemView.findViewById(R.id.rowMaengelStatus)
        val chipMangelOffen: TextView = itemView.findViewById(R.id.chipMangelOffen)
        val chipMangelArbeit: TextView = itemView.findViewById(R.id.chipMangelArbeit)
        val chipMangelErledigt: TextView = itemView.findViewById(R.id.chipMangelErledigt)
        val chipMangelAbgelehnt: TextView = itemView.findViewById(R.id.chipMangelAbgelehnt)
        val rowAbnahmeStatus: View = itemView.findViewById(R.id.rowAbnahmeStatus)
        val chipAbnahmeJa: TextView = itemView.findViewById(R.id.chipAbnahmeJa)
        val chipAbnahmeNein: TextView = itemView.findViewById(R.id.chipAbnahmeNein)
        val chipAbnahmePruefung: TextView = itemView.findViewById(R.id.chipAbnahmePruefung)
        val indentIcon: ImageView = itemView.findViewById(R.id.imgIndent)
        val chevron: ImageView = itemView.findViewById(R.id.imgChevron)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_baustellen_knoten, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val k = item.knoten

        // Einrückung (clean): je Ebene etwas mehr Start-Margin.
        val baseStartDp = 12
        val baseEndDp = 12
        // Einrückung soll "leicht" sein (User-Wunsch)
        val indentDp = 14
        val startPx = dpToPx(holder.itemView, baseStartDp + (item.ebene * indentDp))
        val endPx = dpToPx(holder.itemView, baseEndDp)
        (holder.rowRoot.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.marginStart = startPx
            lp.marginEnd = endPx
            holder.rowRoot.layoutParams = lp
        }

        // Aktuell: Card leicht hervorheben
        (holder.rowRoot as? MaterialCardView)?.let { card ->
            if (item.istAktuell) {
                card.strokeWidth = dpToPx(holder.itemView, 2)
                // Achtung: colorPrimary ist in vielen Projekten über AppCompat definiert.
                // Damit es in allen Varianten sicher kompiliert, greifen wir auf AppCompat-Attr zu.
                card.setStrokeColor(MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary))
            } else {
                // Standard-Rahmen (blau) kommt aus XML. Nicht überschreiben.
            }
        }

        val kmBereich: String? = if (k.kmVon != null) {
            val von = "Km ${formatKm(k.kmVon)}"
            val bis = k.kmBis?.let { "Km ${formatKm(it)}" }
            if (bis != null && bis != von) "$von–$bis" else von
        } else {
            null
        }

        val titelBasis = when (k.typ) {
            BaustellenKnotenTyp.HAUPT -> {
                // Optik: Baustelle ohne Unterpunkte leicht markieren (wie "*...*")
                if (item.gesamtCount == 0) "★ ${k.hauptbaustellenName}" else k.hauptbaustellenName
            }
            BaustellenKnotenTyp.FREIE_STRECKE -> k.knotenName?.takeIf { it.isNotBlank() }
                ?: (k.streckenkennung ?: "Freie Strecke")
            BaustellenKnotenTyp.BAHNHOF -> de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(k).orEmpty()
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> k.knotenName?.takeIf { it.isNotBlank() } ?: "Allgemein / Objekt"
        }

        // Pfeil-Icon + leichte Einrückung (clean)
        holder.indentIcon.visibility = if (item.ebene > 0) View.VISIBLE else View.GONE
        holder.titel.text = titelBasis

        val detailBasis = when (k.typ) {
            BaustellenKnotenTyp.HAUPT -> "Tippen: aufklappen / öffnen"
            BaustellenKnotenTyp.FREIE_STRECKE -> {
                listOf("Freie Strecke", kmBereich).filter { !it.isNullOrBlank() }.joinToString(" · ")
            }
            BaustellenKnotenTyp.BAHNHOF -> listOf(run {
                val bhf = k.bahnhofName?.trim().orEmpty()
                val glw = k.gleisOderWeiche?.trim().orEmpty()
                when {
                    bhf.isNotBlank() && glw.isNotBlank() -> "Bahnhof $bhf – $glw"
                    bhf.isNotBlank() -> "Bahnhof $bhf"
                    else -> k.knotenName?.takeIf { it.isNotBlank() } ?: "Bahnhof"
                }
            }, kmBereich)
                .filter { !it.isNullOrBlank() }
                .joinToString(" · ")
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> listOf("Allgemein / Objekt", kmBereich)
                .filter { !it.isNullOrBlank() }
                .joinToString(" · ")
        }
        val serverStatus = if (mode == BaustellenKnotenAdapterMode.HAUPTLISTE && k.typ == BaustellenKnotenTyp.HAUPT) {
            formatServerStatus(holder.itemView, k.id)
        } else null
        holder.details.text = listOfNotNull(detailBasis.takeIf { it.isNotBlank() }, serverStatus)
            .joinToString("\n")

        holder.aktuell.visibility = if (item.istAktuell) View.VISIBLE else View.GONE

        // Counts
        val ctx = holder.itemView.context
        val unterText = when (item.unterLabel) {
            UnterLabel.UNTERBAUSTELLEN -> ctx.getString(R.string.ui_chip_unterbaustellen, item.unterCount)
            UnterLabel.UNTERPUNKTE -> ctx.getString(R.string.ui_chip_unterpunkte, item.unterCount)
        }
        val gesamtText = ctx.getString(R.string.ui_chip_gesamt, item.gesamtCount)
        holder.chipUnter.text = unterText
        holder.chipGesamt.text = gesamtText
        holder.chipBilder.text = ctx.getString(R.string.ui_chip_bilder, item.bilderCount)
        holder.chipBtb.text = ctx.getString(R.string.ui_chip_btb, item.btbCount)
        bindStatusChip(
            holder.chipMangelOffen,
            label = "Offen",
            count = item.mangelOffenCount,
            activeColor = Color.parseColor("#C62828")
        )
        bindStatusChip(
            holder.chipMangelArbeit,
            label = "Arbeit",
            count = item.mangelInArbeitCount,
            activeColor = Color.parseColor("#F9A825")
        )
        bindStatusChip(
            holder.chipMangelErledigt,
            label = "Erledigt",
            count = item.mangelErledigtCount,
            activeColor = Color.parseColor("#2E7D32")
        )
        bindStatusChip(
            holder.chipMangelAbgelehnt,
            label = "Abgel.",
            count = item.mangelAbgelehntCount,
            activeColor = Color.parseColor("#616161")
        )
        val maengelClick = View.OnClickListener { onMaengelKlick?.invoke(k) }
        holder.chipMangelOffen.setOnClickListener(maengelClick)
        holder.chipMangelArbeit.setOnClickListener(maengelClick)
        holder.chipMangelErledigt.setOnClickListener(maengelClick)
        holder.chipMangelAbgelehnt.setOnClickListener(maengelClick)

        bindStatusChip(
            holder.chipAbnahmeJa,
            label = "Ja",
            count = item.abnahmeJaCount,
            activeColor = Color.parseColor("#2E7D32")
        )
        bindStatusChip(
            holder.chipAbnahmeNein,
            label = "Nein",
            count = item.abnahmeNeinCount,
            activeColor = Color.parseColor("#C62828")
        )
        bindStatusChip(
            holder.chipAbnahmePruefung,
            label = "Prüfung",
            count = item.abnahmePruefungCount,
            activeColor = Color.parseColor("#F9A825")
        )
        holder.chipAbnahmeJa.setOnClickListener { onAbnahmenKlick?.invoke(k, "JA") }
        holder.chipAbnahmeNein.setOnClickListener { onAbnahmenKlick?.invoke(k, "NEIN") }
        holder.chipAbnahmePruefung.setOnClickListener { onAbnahmenKlick?.invoke(k, "PRUEFUNG") }

        val showHauptCounts = mode == BaustellenKnotenAdapterMode.HAUPTLISTE
        holder.chipBilder.visibility = if (showHauptCounts) View.VISIBLE else View.GONE
        holder.chipBtb.visibility = if (showHauptCounts) View.VISIBLE else View.GONE
        holder.rowMaengelStatus.visibility = if (showHauptCounts) View.VISIBLE else View.GONE
        holder.rowAbnahmeStatus.visibility = if (showHauptCounts) View.VISIBLE else View.GONE

        // Chevron
        val showChevron = (mode == BaustellenKnotenAdapterMode.ACCORDION) && item.istAufklappbar
        holder.chevron.visibility = if (showChevron) View.VISIBLE else View.GONE
        if (showChevron) {
            holder.chevron.setImageResource(
                if (item.istAufgeklappt) R.drawable.ic_arrow_drop_up_24 else R.drawable.ic_arrow_drop_down_24
            )
        }

        holder.itemView.setOnClickListener { onKlick(k) }
        holder.itemView.setOnLongClickListener {
            onLangKlick(k)
            true
        }
    }

    private fun formatServerStatus(view: View, localRootId: Long): String {
        val status = TeamDisplayCache.worksiteCardStatus(view.context, localRootId)
            ?: return "Noch nicht mit Server synchronisiert"
        val team = if (status.responsibleTbCount > 0 || status.helperCount > 0) {
            "${status.responsibleTbCount} TB · ${status.helperCount} Helfer"
        } else {
            "Teamdaten noch nicht geladen"
        }
        val sync = status.lastSuccessfulSyncEpochMs?.let(::formatSyncTime)
            ?: "noch nicht"
        return "$team · Zuletzt synchronisiert: $sync"
    }

    private fun formatSyncTime(epochMs: Long): String {
        val zone = ZoneId.systemDefault()
        val value = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = LocalDate.now(zone)
        val dateLabel = when (value.toLocalDate()) {
            today -> "heute"
            today.minusDays(1) -> "gestern"
            else -> value.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }
        return "$dateLabel ${value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    private fun bindStatusChip(
        chip: TextView,
        label: String,
        count: Int,
        activeColor: Int
    ) {
        chip.text = "$label $count"
        val surface = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface)
        val fill = ColorUtils.blendARGB(onSurface, surface, 0.91f)
        val border = if (count > 0) {
            activeColor
        } else {
            ColorUtils.blendARGB(activeColor, surface, 0.68f)
        }
        chip.backgroundTintList = null
        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(chip, 14).toFloat()
            setColor(fill)
            setStroke(dpToPx(chip, 1), border)
        }
        chip.setTextColor(onSurface)
    }

    private fun dpToPx(v: View, dp: Int): Int {
        val d = v.resources.displayMetrics.density
        return (dp * d).toInt()
    }

    private fun formatKm(km: Double): String {
        // 1.233 bleibt 1.233, 99.0 wird 99.000
        return String.format(java.util.Locale.US, "%.3f", km)
    }

    private fun BaustellenKnotenEntity.typLabel(): String {
        return when (typ) {
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> "Allgemein/Objekt"
            BaustellenKnotenTyp.FREIE_STRECKE -> "Freie Strecke"
            BaustellenKnotenTyp.BAHNHOF -> "Bahnhof"
            BaustellenKnotenTyp.HAUPT -> "Hauptbaustelle"
        }
    }
}

