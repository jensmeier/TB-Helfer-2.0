package de.tbhelfer.ui.bautagebuch

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import de.tbhelfer.R
import de.tbhelfer.datenbank.Bildtyp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Fotoimport direkt aus dem Dialog "Eintrag für <Firma>".
 *
 * Androids interne AlertDialog-Buttonleiste kuerzt auf schmalen Handys selbst bei gleicher
 * Gewichtung weiterhin Texte. Deshalb sitzen Bilder/Abbrechen/Hinzufuegen ab 200050 als eigene
 * feste Dreierzeile direkt im Dialoginhalt. Jeder Button erhaelt exakt ein Drittel der Breite und
 * 48 dp Hoehe. "Hinzufuegen" delegiert bewusst an den bereits vorhandenen positiven Dialogbutton,
 * damit dessen bestehende Validierungs-/Commitlogik unveraendert bleibt.
 *
 * Derselbe Binder wird fuer die BTB-Punkte 4 bis 8 verwendet.
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
        bildtyp: Bildtyp,
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
                    putString("filterBildtyp", bildtyp.name)
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

        val alert = entryDialog as? AlertDialog ?: return
        val density = context.resources.displayMetrics.density
        val buttonHeight = (48 * density).toInt()
        val gap = (4 * density).toInt()

        fun actionButton(text: String): MaterialButton = MaterialButton(context).apply {
            this.text = text
            isAllCaps = false
            isSingleLine = true
            textSize = 11.5f
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            minHeight = buttonHeight
            minimumHeight = buttonHeight
            insetTop = 0
            insetBottom = 0
            setPadding((3 * density).toInt(), 0, (3 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight, 1f)
        }

        val bilder = actionButton("Bilder").apply {
            contentDescription = "Bilder auswählen"
            setIconResource(R.drawable.ic_image_picker_24)
            iconPadding = (2 * density).toInt()
            setOnClickListener { launchImagePicker() }
        }
        val abbrechen = actionButton("Abbrechen").apply {
            setOnClickListener { entryDialog.dismiss() }
        }
        val hinzufuegen = actionButton("Hinzufügen").apply {
            setOnClickListener {
                // Die eigentliche Fachlogik bleibt an dem positiven AlertDialog-Button haengen.
                // performClick() ruft dessen vorhandenen Listener auf, auch wenn die Systemleiste
                // optisch ausgeblendet ist.
                alert.getButton(DialogInterface.BUTTON_POSITIVE)?.performClick()
            }
        }

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
            addView(bilder)
            addView(abbrechen, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
                leftMargin = gap
                rightMargin = gap
            })
            addView(hinzufuegen)
        }
        container.addView(
            actionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Die System-Buttonbar bleibt nur als unsichtbarer Traeger der bereits vorhandenen
        // Positiv-Listener bestehen. Dadurch gibt es optisch nur unsere stabile Dreierzeile.
        alert.window?.decorView?.post {
            val positive = alert.getButton(DialogInterface.BUTTON_POSITIVE)
            val negative = alert.getButton(DialogInterface.BUTTON_NEGATIVE)
            val neutral = alert.getButton(DialogInterface.BUTTON_NEUTRAL)
            val systemRow = positive?.parent as? View
            systemRow?.visibility = View.GONE
            negative?.visibility = View.GONE
            neutral?.visibility = View.GONE
            positive?.visibility = View.GONE
        }
    }
}
