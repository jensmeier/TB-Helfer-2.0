package de.tbhelfer.ui.streckenpunkte

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R
import de.tbhelfer.datenbank.StreckenpunktEntity

class StreckenpunkteAdapter(
    private val onClick: (StreckenpunktEntity) -> Unit,
    private val onLongClick: ((StreckenpunktEntity) -> Unit)? = null
) : ListAdapter<StreckenpunktListenEintrag, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_PUNKT = 1
    }

    object Diff : DiffUtil.ItemCallback<StreckenpunktListenEintrag>() {
        override fun areItemsTheSame(oldItem: StreckenpunktListenEintrag, newItem: StreckenpunktListenEintrag): Boolean {
            return when {
                oldItem is StreckenpunktListenEintrag.Header && newItem is StreckenpunktListenEintrag.Header -> oldItem.key == newItem.key
                oldItem is StreckenpunktListenEintrag.Punkt && newItem is StreckenpunktListenEintrag.Punkt -> oldItem.item.punkt.id == newItem.item.punkt.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: StreckenpunktListenEintrag, newItem: StreckenpunktListenEintrag): Boolean =
            oldItem == newItem
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is StreckenpunktListenEintrag.Header -> item.key.hashCode().toLong()
            is StreckenpunktListenEintrag.Punkt -> item.item.punkt.id
        }
    }

    fun setzen(neu: List<StreckenpunktListenEintrag>) {
        submitList(neu)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is StreckenpunktListenEintrag.Header -> VIEW_HEADER
            is StreckenpunktListenEintrag.Punkt -> VIEW_PUNKT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HEADER -> HeaderVH(inflater.inflate(R.layout.item_streckenpunkt_header, parent, false))
            else -> PunktVH(inflater.inflate(R.layout.item_streckenpunkt, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StreckenpunktListenEintrag.Header -> (holder as HeaderVH).bind(item)
            is StreckenpunktListenEintrag.Punkt -> (holder as PunktVH).bind(item.item, onClick, onLongClick)
        }
    }

    class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtHeader: TextView = itemView.findViewById(R.id.txtHeader)

        fun bind(item: StreckenpunktListenEintrag.Header) {
            txtHeader.text = item.text
        }
    }

    class PunktVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtKm: TextView = itemView.findViewById(R.id.txtKm)
        private val txtKmHinweis: TextView = itemView.findViewById(R.id.txtKmHinweis)
        private val txtTyp: TextView = itemView.findViewById(R.id.txtTyp)
        private val txtBezeichnung: TextView = itemView.findViewById(R.id.txtBezeichnung)
        private val txtMeta: TextView = itemView.findViewById(R.id.txtMeta)
        private val txtNotiz: TextView = itemView.findViewById(R.id.txtNotiz)

        fun bind(
            item: StreckenpunktListenItem,
            onClick: (StreckenpunktEntity) -> Unit,
            onLongClick: ((StreckenpunktEntity) -> Unit)?
        ) {
            val punkt = item.punkt
            txtKm.text = item.kmText
            txtTyp.text = if (punkt.typ == de.tbhelfer.datenbank.StreckenpunktTyp.HILFS && punkt.typFreitext == "Trassenpunkt") "Punkt" else item.typText
            txtBezeichnung.text = item.kontextText
            txtKmHinweis.visibility = if (item.kmHinweisText.isNullOrBlank()) View.GONE else View.VISIBLE
            txtKmHinweis.text = item.kmHinweisText.orEmpty()

            val meta = buildList {
                punkt.objektKennung?.takeIf { it.isNotBlank() }?.let { add(it) }
                add(if (hatGps(punkt)) "GPS ok" else "ohne GPS")
            }.joinToString(" · ")
            txtMeta.text = meta

            if (punkt.notiz.isNullOrBlank()) {
                txtNotiz.visibility = View.GONE
            } else {
                txtNotiz.visibility = View.VISIBLE
                txtNotiz.text = punkt.notiz
            }

            itemView.setOnClickListener { onClick(punkt) }
            if (onLongClick != null) {
                itemView.setOnLongClickListener {
                    onLongClick.invoke(punkt)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }

        private fun hatGps(punkt: StreckenpunktEntity): Boolean = punkt.gpsLat != 0.0 || punkt.gpsLon != 0.0
    }
}
