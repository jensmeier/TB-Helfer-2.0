package de.tbhelfer.ui.kamera

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter

/**
 * Kleine Gruppierung fuer die Weichen-Fotobereiche. Die Trennzeile ist nur
 * optisch und kann nicht als Bereich ausgewaehlt oder gespeichert werden.
 */
class ObjektBereichSpinnerAdapter(
    context: Context,
    items: List<String>,
    private val trenner: String
) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun isEnabled(position: Int): Boolean = getItem(position) != trenner

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return super.getDropDownView(position, convertView, parent).apply {
            isEnabled = this@ObjektBereichSpinnerAdapter.isEnabled(position)
            alpha = if (isEnabled) 1.0f else 0.5f
        }
    }
}
