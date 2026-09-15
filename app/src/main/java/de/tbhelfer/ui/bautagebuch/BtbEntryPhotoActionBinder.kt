package de.tbhelfer.ui.bautagebuch

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import de.tbhelfer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Fotoimport direkt aus dem Dialog "Eintrag für <Firma>".
 *
 * Android 200045: Der Dialog zeigt nur noch ein kompaktes Bildsymbol. Ein Tipp darauf
 * öffnet direkt den vorhandenen Android-Bildwähler. Der bestehende BTB-/Fotoimportpfad
 * bleibt erhalten, damit Firma, Ort, Arbeitstext, KM und stabile btbId übernommen werden.
 */
object BtbEntryPhotoActionBinder {

    data class Draft(
        val text: String,
        val time: String?,
        val kmVon: Double?,
        val kmBis: Double?,
        val ortId: Long,
        val ortLabel: String
    )

    fun bind(
        fragment: Fragment,
        container: LinearLayout,
        entryDialog: Dialog,
        sectionDialog: Dialog,
        firma: String?,
        textField: EditText,
        textLayout: TextInputLayout,
        timeField: EditText,
        timeLayout: TextInputLayout,
        kmVonField: EditText,
        kmVonLayout: TextInputLayout,
        kmBisField: EditText,
        kmBisLayout: TextInputLayout,
        ortProvider: () -> Pair<Long, String>,
        onCommitManualEntry: (Draft) -> Unit,
        snapshotProvider: suspend () -> BautagebuchEintrag?
    ) {
        val context = fragment.requireContext()
        val density = fragment.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val iconRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        val btnImage = AppCompatImageButton(context).apply {
            setImageResource(R.drawable.ic_image_picker_24)
            imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Bild auswählen"
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minimumWidth = 0
            minimumHeight = 0

            val selectableBackground = TypedValue()
            if (
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    selectableBackground,
                    true
                ) && selectableBackground.resourceId != 0
            ) {
                setBackgroundResource(selectableBackground.resourceId)
            }
        }
        iconRow.addView(btnImage, LinearLayout.LayoutParams(dp(44), dp(44)))
        container.addView(iconRow)

        fun normalizeHm(raw: String): String? {
            val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(raw.trim()) ?: return null
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
        }

        fun normalizeTimeOrSpan(raw: String): String? {
            val value = raw.trim()
            if (value.isBlank()) return null
            normalizeHm(value)?.let { return it }
            val match = Regex("^(\\d{1,2}:\\d{2})\\s*[-–]\\s*\\(?\\s*(\\d{1,2}:\\d{2})\\s*\\)?$")
                .matchEntire(value) ?: return null
            val start = normalizeHm(match.groupValues[1]) ?: return null
            val end = normalizeHm(match.groupValues[2]) ?: return null
            return "$start–$end"
        }

        fun parseKm(raw: String): Double? {
            val value = raw.trim().replace(',', '.')
            if (value.isBlank()) return null
            return value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it < 10000.0 }
        }

        fun readDraft(): Draft? {
            textLayout.error = null
            timeLayout.error = null
            kmVonLayout.error = null
            kmBisLayout.error = null

            val text = textField.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                textLayout.error = "Eintrag fehlt"
                return null
            }

            val timeRaw = timeField.text?.toString().orEmpty().trim()
            val time = if (timeRaw.isBlank()) null else normalizeTimeOrSpan(timeRaw)
            if (timeRaw.isNotBlank() && time == null) {
                timeLayout.error = fragment.getString(R.string.btb_entry_time_error)
                return null
            }

            val kmVonRaw = kmVonField.text?.toString().orEmpty().trim()
            val kmBisRaw = kmBisField.text?.toString().orEmpty().trim()
            val kmVon = parseKm(kmVonRaw)
            val kmBis = parseKm(kmBisRaw)
            if (kmVonRaw.isNotBlank() && kmVon == null) {
                kmVonLayout.error = "KM prüfen"
                return null
            }
            if (kmBisRaw.isNotBlank() && kmBis == null) {
                kmBisLayout.error = "KM prüfen"
                return null
            }

            val (ortId, ortLabel) = ortProvider()
            return Draft(
                text = text,
                time = time,
                kmVon = kmVon,
                kmBis = kmBis,
                ortId = ortId,
                ortLabel = ortLabel
            )
        }

        fun launchImagePicker() {
            val draft = readDraft() ?: return
            onCommitManualEntry(draft)
            entryDialog.dismiss()
            sectionDialog.dismiss()

            fragment.lifecycleScope.launch {
                val snapshot = snapshotProvider()
                if (snapshot == null) {
                    Toast.makeText(context, "BTB konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val appContext = context.applicationContext
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(appContext, snapshot)
                }

                val canonicalBtbId = snapshot.btbId?.trim().takeUnless { it.isNullOrBlank() }
                    ?: withContext(Dispatchers.IO) {
                        BtbActiveContext.laden(appContext, snapshot.baustelleId)?.btbId
                    }

                val args = Bundle().apply {
                    putLong("baustelleId", snapshot.baustelleId)
                    putString("baustelleName", snapshot.baustelleName)
                    putString("datumIso", snapshot.datumIso)
                    putString("shiftStart", snapshot.startUhrzeit)
                    putString("shiftEnd", snapshot.endeUhrzeit)
                    putString("schichtTyp", snapshot.schicht.name)
                    putString("filterBildtyp", de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION.name)
                    canonicalBtbId?.let { putString("btbId", it) }
                    putString("autoPhotoSource", "gallery")
                    putString("autoPhotoFirma", firma?.trim().orEmpty())
                    putLong("autoPhotoOrtId", draft.ortId)
                    putString("autoPhotoOrtLabel", draft.ortLabel)
                    putString("autoPhotoText", draft.text)
                    draft.time?.let { putString("autoPhotoTime", it) }
                    draft.kmVon?.let { putDouble("autoPhotoKmVon", it) }
                    draft.kmBis?.let { putDouble("autoPhotoKmBis", it) }
                }

                runCatching {
                    fragment.findNavController().navigate(R.id.btbPdfAuswahlFragment, args)
                }.onFailure {
                    Toast.makeText(context, "Bildauswahl konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnImage.setOnClickListener { launchImagePicker() }
    }
}
