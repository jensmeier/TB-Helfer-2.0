package de.tbhelfer.ui.suche

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R
import java.text.DateFormat
import java.util.Date
import java.util.LinkedHashSet

class BtbAdapter(
    private val onClick: (BtbDatei) -> Unit,
    private val onLongClick: (BtbDatei) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<BtbAdapter.VH>() {

    companion object {
        private const val PAYLOAD_SELECTION = "payload_selection"
    }

    private val items = mutableListOf<BtbDatei>()
    private val indexByPath = HashMap<String, Int>(128)

    private val dateFormat = DateFormat.getDateTimeInstance()

    private var selectionMode: Boolean = false
    private val selectedPaths: LinkedHashSet<String> = LinkedHashSet()

    val selectedCount: Int get() = selectedPaths.size
    val totalCount: Int get() = items.size

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        items[position].file.absolutePath.hashCode().toLong()

    fun getSelectedFiles(): List<java.io.File> =
        items.filter { selectedPaths.contains(it.file.absolutePath) }.map { it.file }

    fun getAllPaths(): List<String> = items.map { it.file.absolutePath }

    fun setSelectedPathsFromViewModel(paths: Set<String>) {
        selectedPaths.clear()
        selectedPaths.addAll(paths)
        selectionMode = selectedPaths.isNotEmpty()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    fun submitList(list: List<BtbDatei>) {
        val old = items.toList()

        items.clear()
        items.addAll(list)
        rebuildIndex()

        // ungültige Selections entfernen
        val valid = items.map { it.file.absolutePath }.toHashSet()
        val oldSelCount = selectedCount
        selectedPaths.retainAll(valid)
        selectionMode = selectionMode && selectedPaths.isNotEmpty()

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = items.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = old[oldItemPosition]
                val n = items[newItemPosition]
                return o.file.absolutePath == n.file.absolutePath
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = old[oldItemPosition]
                val n = items[newItemPosition]
                return o == n && o.file.lastModified() == n.file.lastModified() && o.file.length() == n.file.length()
            }
        })

        diff.dispatchUpdatesTo(this)

        // Selection-State ggf. aktualisieren
        if (oldSelCount != selectedCount) {
            onSelectionChanged(selectedCount)
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedPaths.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    fun selectAll() {
        if (items.isEmpty()) return
        selectionMode = true
        selectedPaths.clear()
        selectedPaths.addAll(items.map { it.file.absolutePath })
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    fun invertSelection() {
        if (items.isEmpty()) return
        selectionMode = true
        val all = items.map { it.file.absolutePath }
        val newSel = all.filter { !selectedPaths.contains(it) }
        selectedPaths.clear()
        selectedPaths.addAll(newSel)
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(selectedCount)
    }

    private fun toggleSelected(path: String) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path) else selectedPaths.add(path)
        selectionMode = selectedPaths.isNotEmpty() || selectionMode

        val idx = indexByPath[path]
        if (idx != null) {
            notifyItemChanged(idx, PAYLOAD_SELECTION)
        } else {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
        onSelectionChanged(selectedCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_btb, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bindContent(item, dateFormat)
        holder.bindSelection(selectionMode, selectedPaths.contains(item.file.absolutePath))

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelected(item.file.absolutePath)
            } else {
                onClick(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
            }
            toggleSelected(item.file.absolutePath)
            onLongClick(item)
            true
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val item = items[position]
            holder.bindSelection(selectionMode, selectedPaths.contains(item.file.absolutePath))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun rebuildIndex() {
        indexByPath.clear()
        for (i in items.indices) {
            indexByPath[items[i].file.absolutePath] = i
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.textTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val date: TextView = itemView.findViewById(R.id.textDate)
        private val ivSelected: ImageView = itemView.findViewById(R.id.ivSelectedBtb)

        fun bindContent(item: BtbDatei, dateFormat: DateFormat) {
            title.text = item.file.name

            val info = buildString {
                append(item.baustellenName)
                if (!item.streckennummer.isNullOrBlank()) {
                    append(" • Strecke ")
                    append(item.streckennummer)
                }
            }
            subtitle.text = info
            date.text = dateFormat.format(Date(item.file.lastModified()))
        }

        fun bindSelection(selectionMode: Boolean, isSelected: Boolean) {
            ivSelected.visibility = if (selectionMode && isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 0.85f else 1.0f
        }
    }
}
