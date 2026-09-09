package de.tbhelfer.ui.sync

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.SyncWorksiteContextEntity
import de.tbhelfer.datenbank.WorksiteBindingRepository
import de.tbhelfer.server.CollabBenachrichtigungen
import de.tbhelfer.server.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gerätespezifische Zuordnung zwischen den bereits vorhandenen lokalen Hauptbaustellen und
 * den Server-Baustellen, denen der aktivierte Nutzer zugeordnet ist.
 *
 * Es gibt bewusst KEIN automatisches Matching über den Baustellennamen. Die Verknüpfung wird
 * einmal eindeutig ausgewählt und bleibt danach in der separaten Sync-Datenbank erhalten.
 */
class BaustellenSyncFragment : Fragment(R.layout.fragment_baustellen_sync) {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var container: LinearLayout
    private lateinit var refresh: View

    private val collab get() = ServiceLocator.collabSyncRepository
    private val bridge get() = ServiceLocator.collabLocalBridge
    private val bindings get() = ServiceLocator.worksiteBindingRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        status = view.findViewById(R.id.txtBaustellenSyncStatus)
        progress = view.findViewById(R.id.progressBaustellenSync)
        container = view.findViewById(R.id.containerBaustellenSync)
        refresh = view.findViewById(R.id.btnBaustellenSyncRefresh)
        refresh.setOnClickListener { synchronize() }

        // Erst den lokal bekannten Stand anzeigen; danach kontrolliert mit dem Server abgleichen.
        renderCached()
        synchronize()
    }

    private fun renderCached() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { runCatching { bindings.contextsWithBindings() }.getOrDefault(emptyList()) }
            if (!isAdded) return@launch
            render(items)
        }
    }

    private fun synchronize() {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            status.text = getString(R.string.collab_not_activated)
            return
        }

        setLoading(true)
        status.text = getString(R.string.collab_sync_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { synchronizeInternal(token) }
            if (!isAdded) return@launch
            setLoading(false)

            when (result) {
                SyncUiResult.Unsupported -> {
                    status.text = getString(R.string.collab_server_not_ready)
                    renderCached()
                }
                SyncUiResult.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    status.text = getString(R.string.collab_session_invalid)
                }
                is SyncUiResult.Error -> {
                    status.text = getString(R.string.collab_sync_error, result.message)
                    renderCached()
                }
                is SyncUiResult.Success -> {
                    status.text = getString(
                        R.string.collab_sync_ok,
                        result.worksites.size,
                        result.pulled,
                        result.uploaded,
                        result.pending,
                        result.conflicts
                    )
                    render(result.worksites)
                    result.notifications
                        .distinctBy { "${it.entityType}|${it.changedByName.orEmpty()}" }
                        .forEach { CollabBenachrichtigungen.anzeigen(requireContext(), it) }
                }
            }
        }
    }

    private sealed interface SyncUiResult {
        data object Unsupported : SyncUiResult
        data object SessionInvalid : SyncUiResult
        data class Error(val message: String) : SyncUiResult
        data class Success(
            val worksites: List<WorksiteBindingRepository.BoundContext>,
            val pulled: Int,
            val uploaded: Int,
            val pending: Int,
            val conflicts: Int,
            val notifications: List<CollabBenachrichtigungen.Ereignis>
        ) : SyncUiResult
    }

    private suspend fun synchronizeInternal(token: String): SyncUiResult {
        val notifications = mutableListOf<CollabBenachrichtigungen.Ereignis>()
        var pulled = 0
        var uploaded = 0
        var pending = 0
        var conflicts = 0

        val first = when (val result = collab.synchronizeAll(token)) {
            CollabSyncRepository.FullSyncResult.SessionInvalid -> return SyncUiResult.SessionInvalid
            is CollabSyncRepository.FullSyncResult.Error -> return SyncUiResult.Error(result.message)
            is CollabSyncRepository.FullSyncResult.Success -> result.summary
        }
        if (!first.supported) return SyncUiResult.Unsupported
        pulled += first.pulledChanges
        uploaded += first.uploadedChanges
        pending = first.pendingChanges
        conflicts = first.conflicts
        notifications += first.notifications

        runCatching { bridge.applyServerStateToLocal() }
            .getOrElse { return SyncUiResult.Error(it.message ?: "Serverdaten konnten lokal nicht übernommen werden.") }

        val exported = runCatching { bridge.exportLocalChanges() }
            .getOrElse { return SyncUiResult.Error(it.message ?: "Lokale Baustellendaten konnten nicht vorbereitet werden.") }

        if (exported.queuedOrUpdated > 0) {
            val second = when (val result = collab.synchronizeAll(token)) {
                CollabSyncRepository.FullSyncResult.SessionInvalid -> return SyncUiResult.SessionInvalid
                is CollabSyncRepository.FullSyncResult.Error -> return SyncUiResult.Error(result.message)
                is CollabSyncRepository.FullSyncResult.Success -> result.summary
            }
            pulled += second.pulledChanges
            uploaded += second.uploadedChanges
            pending = second.pendingChanges
            conflicts = second.conflicts
            notifications += second.notifications
            runCatching { bridge.applyServerStateToLocal() }
                .getOrElse { return SyncUiResult.Error(it.message ?: "Bestätigte Serverdaten konnten lokal nicht übernommen werden.") }
        }

        // Genau wie der Hintergrund-Sync muss auch ein manueller Sync Bilder und Abnahme-Anlagen
        // sofort versuchen. Der Upload läuft bei jedem Durchlauf, damit ein zuvor unterbrochenes
        // Bild nicht bis zum nächsten periodischen Worker warten muss.
        val attachmentUpload = when (val result = bridge.uploadLocalAttachments(token)) {
            CollabLocalBridge.AttachmentUploadResult.SessionInvalid -> return SyncUiResult.SessionInvalid
            CollabLocalBridge.AttachmentUploadResult.Unsupported -> null
            is CollabLocalBridge.AttachmentUploadResult.Error -> return SyncUiResult.Error(result.message)
            is CollabLocalBridge.AttachmentUploadResult.Success -> result.summary
        }
        uploaded += attachmentUpload?.uploaded ?: 0

        // Neue Attachment-Changes unmittelbar wieder einlesen/ACKen. Das eigene Gerät lädt die
        // Datei nicht doppelt, weil Repository + Bridge die Server-ID schon lokal verknüpft haben.
        if ((attachmentUpload?.uploaded ?: 0) > 0) {
            val third = when (val result = collab.synchronizeAll(token)) {
                CollabSyncRepository.FullSyncResult.SessionInvalid -> return SyncUiResult.SessionInvalid
                is CollabSyncRepository.FullSyncResult.Error -> return SyncUiResult.Error(result.message)
                is CollabSyncRepository.FullSyncResult.Success -> result.summary
            }
            pulled += third.pulledChanges
            uploaded += third.uploadedChanges
            pending = third.pendingChanges
            conflicts = third.conflicts
            notifications += third.notifications
            runCatching { bridge.applyServerStateToLocal() }
                .getOrElse { return SyncUiResult.Error(it.message ?: "Bild-/Anlagendaten konnten lokal nicht bestätigt werden.") }
        }

        return SyncUiResult.Success(
            worksites = bindings.contextsWithBindings(),
            pulled = pulled,
            uploaded = uploaded,
            pending = pending,
            conflicts = conflicts,
            notifications = notifications
        )
    }

    private fun render(items: List<WorksiteBindingRepository.BoundContext>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(muted(getString(R.string.collab_no_worksites)))
            return
        }

        items.forEach { item ->
            val server = item.context
            val local = item.localWorksite
            val title = server.name?.takeIf { it.isNotBlank() }
                ?: server.designation?.takeIf { it.isNotBlank() }
                ?: getString(R.string.collab_worksite_fallback)
            val group = server.groupLabel?.takeIf { it.isNotBlank() } ?: "TB-Gruppe"
            val role = roleLabel(server)
            val localText = local?.hauptbaustellenName?.let { getString(R.string.collab_bound_to, it) }
                ?: getString(R.string.collab_not_bound)
            val detail = "$group · $role\n$localText"
            container.addView(itemView(title, detail) { chooseLocalWorksite(item) })
        }
    }

    private fun chooseLocalWorksite(item: WorksiteBindingRepository.BoundContext) {
        viewLifecycleOwner.lifecycleScope.launch {
            val localItems = withContext(Dispatchers.IO) { bindings.localMainWorksites() }
            if (!isAdded) return@launch
            if (localItems.isEmpty()) {
                Toast.makeText(requireContext(), R.string.collab_no_local_worksites, Toast.LENGTH_LONG).show()
                return@launch
            }

            val choices = buildList {
                if (item.binding != null) add(getString(R.string.collab_unbind))
                localItems.forEach { local ->
                    add("${local.hauptbaustellenName} · lokal #${local.id}")
                }
            }.toTypedArray()
            val offset = if (item.binding != null) 1 else 0

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.collab_choose_local_title, displayName(item.context)))
                .setItems(choices) { _, which ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val action = withContext(Dispatchers.IO) {
                            runCatching {
                                if (offset == 1 && which == 0) {
                                    bindings.unbindServer(item.context.worksiteServerId)
                                } else {
                                    val selected = localItems[which - offset]
                                    bindings.bind(selected.id, item.context.worksiteServerId)
                                }
                            }
                        }
                        if (!isAdded) return@launch
                        action.onFailure {
                            Toast.makeText(requireContext(), it.message ?: getString(R.string.collab_binding_failed), Toast.LENGTH_LONG).show()
                        }
                        if (action.isSuccess) {
                            renderCached()
                            // Nach einer neuen Bindung lokale BTB/Mängel/Abnahmen direkt für den nächsten
                            // Upload erfassen, statt erst auf den periodischen Hintergrundlauf zu warten.
                            synchronize()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun roleLabel(context: SyncWorksiteContextEntity): String {
        val raw = context.memberRole.uppercase()
        return if (raw.contains("LEAD") || raw == "TB" || raw.contains("VERANTWORT")) {
            getString(R.string.collab_role_tb)
        } else {
            getString(R.string.collab_role_helper)
        }
    }

    private fun displayName(context: SyncWorksiteContextEntity): String =
        context.name?.takeIf { it.isNotBlank() }
            ?: context.designation?.takeIf { it.isNotBlank() }
            ?: getString(R.string.collab_worksite_fallback)

    private fun itemView(title: String, detail: String, onClick: () -> Unit): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val value = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            setBackgroundResource(value.resourceId)
            addView(TextView(context).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_TBHelfer_Body)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            })
            addView(TextView(context).apply {
                text = detail
                alpha = 0.72f
                setPadding(0, dp(3), 0, 0)
                setTextAppearance(R.style.TextAppearance_TBHelfer_Caption)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun muted(textValue: String): TextView = TextView(requireContext()).apply {
        text = textValue
        alpha = 0.7f
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        refresh.isEnabled = !loading
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
