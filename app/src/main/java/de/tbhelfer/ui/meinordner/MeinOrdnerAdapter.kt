package de.tbhelfer.ui.meinordner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R

class MeinOrdnerAdapter(
    private val onClick: (MeinOrdnerEintrag) -> Unit,
    private val onMore: (View, MeinOrdnerEintrag) -> Unit
) : ListAdapter<MeinOrdnerEintrag, MeinOrdnerAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mein_ordner, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onClick, onMore)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val typText: TextView = view.findViewById(R.id.tvType)
        private val nameText: TextView = view.findViewById(R.id.tvName)
        private val subText: TextView = view.findViewById(R.id.tvSubtitle)
        private val moreButton: ImageButton = view.findViewById(R.id.btnMore)

        fun bind(
            item: MeinOrdnerEintrag,
            onClick: (MeinOrdnerEintrag) -> Unit,
            onMore: (View, MeinOrdnerEintrag) -> Unit
        ) {
            typText.text = when (item.typ) {
                MeinOrdnerTyp.ORDNER -> "ORD"
                MeinOrdnerTyp.TEXT -> "TXT"
                MeinOrdnerTyp.BILD -> "IMG"
                MeinOrdnerTyp.PDF -> "PDF"
                MeinOrdnerTyp.SONSTIGES -> "DAT"
            }
            nameText.text = item.anzeigeName
            subText.text = buildString {
                item.zusatzText?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append("\n")
                }
                append(MeinOrdnerDateiManager.formatDatum(item.zuletztGeaendertMs))
            }
            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onMore(itemView, item)
                true
            }
            moreButton.setOnClickListener { onMore(it, item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MeinOrdnerEintrag>() {
        override fun areItemsTheSame(oldItem: MeinOrdnerEintrag, newItem: MeinOrdnerEintrag): Boolean =
            oldItem.relativePfad == newItem.relativePfad && oldItem.papierkorbWrapper == newItem.papierkorbWrapper

        override fun areContentsTheSame(oldItem: MeinOrdnerEintrag, newItem: MeinOrdnerEintrag): Boolean =
            oldItem == newItem
    }
}
