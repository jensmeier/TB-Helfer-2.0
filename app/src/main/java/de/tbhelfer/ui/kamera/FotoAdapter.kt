package de.tbhelfer.ui.kamera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.util.ThumbnailLoader
import java.io.File
import java.text.DateFormat
import java.util.Date

class FotoAdapter(
    private val onKlick: (FotoEntity) -> Unit
) : ListAdapter<FotoEntity, FotoAdapter.VH>(Diff) {

    companion object Diff : DiffUtil.ItemCallback<FotoEntity>() {
        override fun areItemsTheSame(oldItem: FotoEntity, newItem: FotoEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FotoEntity, newItem: FotoEntity): Boolean =
            oldItem == newItem
    }

    private val dateFormat = DateFormat.getDateTimeInstance()

    init {
        setHasStableIds(true)
    }

    fun setzen(neueDaten: List<FotoEntity>) {
        submitList(neueDaten)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val foto = getItem(position)
        val datei = File(foto.dateipfad)
        holder.textDateiname.text = datei.name
        holder.textDatum.text = dateFormat.format(Date(foto.erstelltAmEpochMs))

        ThumbnailLoader.loadInto(
            imageView = holder.imgVorschau,
            filePath = foto.dateipfad,
            placeholderResId = android.R.drawable.ic_menu_report_image,
            reqW = 144,
            reqH = 144
        )

        holder.itemView.setOnClickListener { onKlick(foto) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.imgVorschau.tag = null
        holder.imgVorschau.setImageDrawable(null)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgVorschau: ImageView = itemView.findViewById(R.id.imgVorschau)
        val textDateiname: TextView = itemView.findViewById(R.id.textDateiname)
        val textDatum: TextView = itemView.findViewById(R.id.textDatum)
    }
}
