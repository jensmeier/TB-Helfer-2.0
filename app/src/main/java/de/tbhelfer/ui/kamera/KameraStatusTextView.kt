package de.tbhelfer.ui.kamera

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Kamera-Status mit hervorgehobener Bahn-km-Angabe.
 * Die vorhandene adaptive Textfarbe/Schattierung bleibt unangetastet; nur Groesse und Fettung
 * des Tokens "km 75,007" werden vergroessert.
 */
class KameraStatusTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun setText(text: CharSequence?, type: BufferType?) {
        val raw = text?.toString().orEmpty()
        if (raw.isBlank()) {
            super.setText(text, type)
            return
        }
        val styled = SpannableString(raw)
        KM_REGEX.findAll(raw).forEach { match ->
            styled.setSpan(
                RelativeSizeSpan(1.65f),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            styled.setSpan(
                StyleSpan(Typeface.BOLD),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        super.setText(styled, type)
    }

    companion object {
        private val KM_REGEX = Regex("(?i)\\bkm\\s+\\d{1,4}[.,]\\d{3}\\b")
    }
}
