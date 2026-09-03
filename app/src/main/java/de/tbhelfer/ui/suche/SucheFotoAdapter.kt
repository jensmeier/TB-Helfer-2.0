package de.tbhelfer.ui.suche

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R
import de.tbhelfer.util.ThumbnailLoader
import java.io.File
import java.util.LinkedHashSet

class SucheFotoAdapter(
    private val onClick: (SucheFotoItem) -> Unit,
    private val onLongClick: (SucheFotoItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<SucheFotoAdapter.VH>() {

    companion object {
        private const val PAYLOAD_SELECTION = "payload_selection"
    }

    private val items = mutableListOf<SucheFotoItem>()
    private val indexById = HashMap<Long, Int>(256)

    private var selectionMode: Boolean = false
    private val selectedIds: LinkedHashSet<Long> = LinkedHashSet()

    val selectedCount: Int get() = selectedIds.size
    val totalCount: Int get() = items.size

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].fotoId

    fun getSelectedIds(): List<Long> = selectedIds.toList()

    fun getAllIds(): List<Long> = items.map { it.fotoId }

    fun setSelectedIdsFromViewModel(ids: Set<Long>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        selectionMode = selectedIds.isNotEmpty()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    fun selectAll() {
        if (items.isEmpty()) return
        selectionMode = true
        selectedIds.clear()
        selectedIds.addAll(items.map { it.fotoId })
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    fun invertSelection() {
        if (items.isEmpty()) return
        selectionMode = true
        val all = items.map { it.fotoId }
        val newSel = all.filter { !selectedIds.contains(it) }
        selectedIds.clear()
        selectedIds.addAll(newSel)
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    fun clearSelection() {
        selectedIds.clear()
        onSelectionChanged(0)
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    private fun toggleSelected(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        selectionMode = selectedIds.isNotEmpty() || selectionMode

        val idx = indexById[id]
        if (idx != null) {
            notifyItemChanged(idx, PAYLOAD_SELECTION)
        } else {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
        onSelectionChanged(selectedCount)
    }

    fun submitList(list: List<SucheFotoItem>) {
        val old = items.toList()

        items.clear()
        items.addAll(list)
        rebuildIndex()

        // Falls DB gelöscht wurde, ungültige Selections entfernen
        val valid = items.map { it.fotoId }.toHashSet()
        val oldSelCount = selectedCount
        selectedIds.retainAll(valid)
        selectionMode = selectionMode && selectedIds.isNotEmpty()

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = items.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition].fotoId == items[newItemPosition].fotoId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition] == items[newItemPosition]
            }
        })

        diff.dispatchUpdatesTo(this)

        if (oldSelCount != selectedCount) {
            onSelectionChanged(selectedCount)
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suche_foto, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bindContent(item)
        holder.bindSelection(selectionMode, selectedIds.contains(item.fotoId))

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelected(item.fotoId)
            } else {
                onClick(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
            }
            toggleSelected(item.fotoId)
            onLongClick(item)
            true
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val item = items[position]
            holder.bindSelection(selectionMode, selectedIds.contains(item.fotoId))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.clearThumb()
    }

    private fun rebuildIndex() {
        indexById.clear()
        for (i in items.indices) {
            indexById[items[i].fotoId] = i
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb = itemView.findViewById<ImageView>(R.id.ivThumb)
        private val ivSelected = itemView.findViewById<ImageView>(R.id.ivSelected)
        private val tvTitel = itemView.findViewById<TextView>(R.id.tvTitel)
        private val tvMeta = itemView.findViewById<TextView>(R.id.tvMeta)

        fun bindContent(item: SucheFotoItem) {
            tvTitel.text = item.titel
            val z1 = item.zeile1.trim()
            val z2 = item.zeile2.trim()
            tvMeta.text = when {
                z1.isNotBlank() && z2.isNotBlank() -> "$z1 • $z2"
                z1.isNotBlank() -> z1
                z2.isNotBlank() -> z2
                else -> item.bildtypLabel
            }

            val file = File(item.dateiPfad)
            if (file.exists()) {
                ThumbnailLoader.loadInto(
                    imageView = ivThumb,
                    filePath = item.dateiPfad,
                    placeholderResId = android.R.drawable.ic_menu_report_image,
                    reqW = 160,
                    reqH = 160
                )
            } else {
                ivThumb.tag = null
                ivThumb.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }

        fun bindSelection(selectionMode: Boolean, isSelected: Boolean) {
            ivSelected.visibility = if (selectionMode && isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 0.85f else 1.0f
        }

        fun clearThumb() {
            ivThumb.tag = null
            ivThumb.setImageDrawable(null)
        }
    }
}
