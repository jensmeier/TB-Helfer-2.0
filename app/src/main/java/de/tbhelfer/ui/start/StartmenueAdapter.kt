package de.tbhelfer.ui.start

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R

data class StartmenueItem(
    val titel: String,
    val ziel: StartmenueZiel
)

enum class StartmenueZiel {
    KAMERA_AKTUELLE_BAUSTELLE,
    BAUSTELLEN,
    STRECKENPUNKTE,
    BAUTAGEBUCH,
    SUCHE,
    MEIN_ORDNER,
    IMPORT_EXPORT,
    SERVER_SYNC,
    EINSTELLUNGEN
}

class StartmenueAdapter(
    private val onClick: (StartmenueZiel) -> Unit
) : ListAdapter<StartmenueItem, StartmenueAdapter.VH>(Diff) {

    companion object {
        private const val PAYLOAD_STYLE = "payload_style"

        private object Diff : DiffUtil.ItemCallback<StartmenueItem>() {
            override fun areItemsTheSame(oldItem: StartmenueItem, newItem: StartmenueItem): Boolean =
                oldItem.ziel == newItem.ziel

            override fun areContentsTheSame(oldItem: StartmenueItem, newItem: StartmenueItem): Boolean =
                oldItem == newItem
        }
    }

    private var buttonColor: Int = Color.parseColor("#0A72B0")
    private var textColor: Int = Color.parseColor("#FFFFFF")
    private var textSizeSp: Int = 16

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).ziel.ordinal.toLong()

    fun setzeItems(neu: List<StartmenueItem>) {
        submitList(neu)
    }

    fun setzeStyle(buttonColor: Int, textColor: Int, textSizeSp: Int) {
        this.buttonColor = buttonColor
        this.textColor = Color.WHITE
        this.textSizeSp = textSizeSp

        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STYLE)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_start_menu, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), buttonColor, textColor, textSizeSp, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_STYLE)) {
            holder.applyStyle(getItem(position), buttonColor, textColor, textSizeSp, onClick)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val btn: Button = view.findViewById(R.id.btnMenu)

        fun bind(
            item: StartmenueItem,
            buttonColor: Int,
            textColor: Int,
            textSizeSp: Int,
            onClick: (StartmenueZiel) -> Unit
        ) {
            btn.text = item.titel
            applyStyle(item, buttonColor, textColor, textSizeSp, onClick)
        }

        fun applyStyle(
            item: StartmenueItem,
            buttonColor: Int,
            textColor: Int,
            textSizeSp: Int,
            onClick: (StartmenueZiel) -> Unit
        ) {
            val isAktuelleBaustelle = item.ziel == StartmenueZiel.KAMERA_AKTUELLE_BAUSTELLE

            btn.setTextColor(textColor)
            btn.backgroundTintList = ColorStateList.valueOf(buttonColor)

            val targetHeightDp = if (isAktuelleBaustelle) 64 else 56
            btn.layoutParams = btn.layoutParams.apply {
                height = dp(btn, targetHeightDp)
            }

            btn.isSingleLine = false
            btn.maxLines = if (isAktuelleBaustelle) 3 else 2
            btn.includeFontPadding = false

            val minSp = 10
            val maxSp = textSizeSp.coerceIn(12, 28)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn,
                minSp,
                maxSp,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )

            btn.setOnClickListener { onClick(item.ziel) }
        }

        private fun dp(view: View, valueDp: Int): Int {
            val density = view.resources.displayMetrics.density
            return (valueDp * density).toInt()
        }
    }
}
