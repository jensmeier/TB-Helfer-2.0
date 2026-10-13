package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import de.tbhelfer.R

/**
 * Kompatibilitäts-View für den historischen Datum/Schicht-Dialog.
 *
 * Der Dialogcode kennt noch den alten Status "Neu (leer)". Ein solcher Eintrag darf im neuen
 * Multi-BTB-Modell nicht als auswählbarer Bericht erscheinen, weil neue BTBs ausschließlich über
 * den grünen Plus-Ablauf entstehen. Deshalb wird die Schaltfläche komplett ausgeblendet, sobald
 * der Dialog sie als nicht vorhandenen BTB kennzeichnet.
 */
class ExistingBtbShiftButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    override fun setText(text: CharSequence?, type: TextView.BufferType?) {
        super.setText(text, type)
        val missingLabel = runCatching { context.getString(R.string.btb_status_new_empty) }.getOrNull()
        val missing = !missingLabel.isNullOrBlank() && text?.contains(missingLabel, ignoreCase = true) == true
        visibility = if (missing) View.GONE else View.VISIBLE
        isEnabled = !missing
    }
}
