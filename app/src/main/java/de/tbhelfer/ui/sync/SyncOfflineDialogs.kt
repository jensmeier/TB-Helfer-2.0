package de.tbhelfer.ui.sync

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.datenbank.OfflineSyncRepository
import de.tbhelfer.datenbank.SyncOfflineEditor
import de.tbhelfer.datenbank.SyncObjectEntity
import de.tbhelfer.datenbank.SyncPendingChangeEntity
import de.tbhelfer.datenbank.SyncSectionEntity
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-Helfer für bereits heruntergeladene Daten. Änderungen bleiben zuerst lokal und werden
 * über SyncOfflineEditor in die Upload-Queue gestellt.
 */
class SyncOfflineDialogs(
    private val fragment: Fragment,
    private val repository: OfflineSyncRepository,
    private val editor: SyncOfflineEditor
) {
    fun showSections(items: List<SyncSectionEntity>, onChanged: () -> Unit) {
        if (items.isEmpty()) return
        val labels = items.map(::sectionLabel).toTypedArray()
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.sync_edit_offline)
            .setItems(labels) { _, which -> showSectionEdit(items[which], onChanged) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showObjects(items: List<SyncObjectEntity>, onChanged: () -> Unit) {
        if (items.isEmpty()) return
        val labels = items.map(::objectLabel).toTypedArray()
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.sync_edit_offline)
            .setItems(labels) { _, which -> showObjectEdit(items[which], onChanged) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showConflicts(onChanged: () -> Unit) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val conflicts = withContext(Dispatchers.IO) { editor.conflicts() }
            if (!fragment.isAdded || conflicts.isEmpty()) return@launch
            val labels = conflicts.map { conflict ->
                val name = conflict.serverId?.take(8) ?: "neu"
                "${conflict.objectKind} · $name"
            }.toTypedArray()
            AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.sync_conflict_title)
                .setMessage(R.string.sync_conflict_hint)
                .setItems(labels) { _, which -> showConflictAction(conflicts[which], onChanged) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showSectionEdit(item: SyncSectionEntity, onChanged: () -> Unit) {
        val context = fragment.requireContext()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val designation = editText(item.designation.orEmpty(), context.getString(R.string.sync_edit_designation))
        val kmFrom = editText(formatKmInput(item.kmFromM), context.getString(R.string.sync_edit_km_from), decimal = true)
        val kmTo = editText(formatKmInput(item.kmToM), context.getString(R.string.sync_edit_km_to), decimal = true)
        box.addView(designation); box.addView(kmFrom); box.addView(kmTo)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.sync_edit_section_title)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val from = parseKmInput(kmFrom.text?.toString())
                val to = parseKmInput(kmTo.text?.toString())
                if (from is KmParse.Invalid || to is KmParse.Invalid) {
                    Toast.makeText(context, R.string.sync_invalid_km, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        editor.updateSection(
                            serverId = item.serverId,
                            designation = designation.text?.toString(),
                            kmFromM = (from as KmParse.Value).meters,
                            kmToM = (to as KmParse.Value).meters
                        )
                    }
                    if (fragment.isAdded) {
                        Toast.makeText(context, R.string.sync_local_change_queued, Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        onChanged()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showObjectEdit(item: SyncObjectEntity, onChanged: () -> Unit) {
        val context = fragment.requireContext()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val designation = editText(item.designation.orEmpty(), context.getString(R.string.sync_edit_designation))
        val km = editText(formatKmInput(item.kmM), context.getString(R.string.sync_edit_km), decimal = true)
        box.addView(designation); box.addView(km)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.sync_edit_object_title)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = designation.text?.toString()?.trim().orEmpty()
                val parsedKm = parseKmInput(km.text?.toString())
                if (name.isBlank() || parsedKm is KmParse.Invalid) {
                    Toast.makeText(context, if (name.isBlank()) R.string.sync_edit_designation else R.string.sync_invalid_km, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        editor.updateObject(
                            serverId = item.serverId,
                            designation = name,
                            kmM = (parsedKm as KmParse.Value).meters
                        )
                    }
                    if (fragment.isAdded) {
                        Toast.makeText(context, R.string.sync_local_change_queued, Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        onChanged()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showConflictAction(conflict: SyncPendingChangeEntity, onChanged: () -> Unit) {
        val context = fragment.requireContext()
        AlertDialog.Builder(context)
            .setTitle(R.string.sync_conflict_title)
            .setMessage("${conflict.objectKind} · ${conflict.serverId ?: "neu"}")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sync_take_server) { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    val done = withContext(Dispatchers.IO) { editor.acceptServerVersion(conflict.id) }
                    if (done && fragment.isAdded) {
                        Toast.makeText(context, R.string.sync_conflict_action_done, Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                }
            }
            .setNeutralButton(R.string.sync_retry_local) { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    val done = withContext(Dispatchers.IO) { editor.keepLocalAndRetry(conflict.id) }
                    if (done && fragment.isAdded) {
                        Toast.makeText(context, R.string.sync_conflict_action_done, Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                }
            }
            .show()
    }

    private fun editText(value: String, hint: String, decimal: Boolean = false): EditText =
        EditText(fragment.requireContext()).apply {
            setText(value)
            this.hint = hint
            inputType = if (decimal) {
                InputType.TYPE_CLASS_TEXT
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }

    private sealed interface KmParse {
        data class Value(val meters: Double?) : KmParse
        data object Invalid : KmParse
    }

    private fun parseKmInput(raw: String?): KmParse {
        val text = raw.orEmpty().trim().lowercase(Locale.GERMANY).removePrefix("km").trim()
        if (text.isBlank()) return KmParse.Value(null)
        val normalized = text.replace(" ", "").replace(',', '.')
        return try {
            val meters = if ('+' in normalized) {
                val parts = normalized.split('+', limit = 2)
                if (parts.size != 2) return KmParse.Invalid
                parts[0].toDouble() * 1000.0 + parts[1].toDouble()
            } else {
                normalized.toDouble()
            }
            KmParse.Value(meters)
        } catch (_: NumberFormatException) {
            KmParse.Invalid
        }
    }

    private fun formatKmInput(meters: Double?): String {
        if (meters == null) return ""
        val km = kotlin.math.floor(meters / 1000.0).toInt()
        val rest = meters - km * 1000.0
        return String.format(Locale.GERMANY, "%d+%07.3f", km, rest)
    }

    private fun sectionLabel(item: SyncSectionEntity): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: "Abschnitt")
        item.kmFromM?.let { append(" · ").append(formatKmInput(it)) }
        item.kmToM?.takeIf { it != item.kmFromM }?.let { append("–").append(formatKmInput(it)) }
    }

    private fun objectLabel(item: SyncObjectEntity): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: item.objectType)
        append(" · ").append(item.objectType)
        item.kmM?.let { append(" · ").append(formatKmInput(it)) }
    }

    private fun dp(value: Int): Int = (value * fragment.resources.displayMetrics.density).toInt()
}
