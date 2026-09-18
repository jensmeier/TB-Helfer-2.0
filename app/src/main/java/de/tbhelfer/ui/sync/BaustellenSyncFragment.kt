package de.tbhelfer.ui.sync

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.common.Barcode
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BtbTeamMediaSync
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.SyncWorksiteContextEntity
import de.tbhelfer.datenbank.WorksiteBindingRepository
import de.tbhelfer.server.CollabBenachrichtigungen
import de.tbhelfer.server.CompanyProfileSync
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.server.TeamManagementApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Baustellen-Sync und Self-Service-Teamverwaltung.
 *
 * - lokale Hauptbaustelle wird einmal bewusst mit der Server-Baustelle verknüpft
 * - verantwortliche TBs und Helfer werden ohne tägliche Admin-Zuweisung verwaltet
 * - Einladung funktioniert als QR, Link oder manueller TBI-Code
 * - BTB bleibt strikt im eigenen verantwortlichen TB-Team; Mängel/Abnahmen bleiben baustellenweit
 *   innerhalb der individuellen Bereichsfreigabe
 */
class BaustellenSyncFragment : Fragment(R.layout.fragment_baustellen_sync) {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var container: LinearLayout
    private lateinit var refresh: View
    private lateinit var teamContainer: LinearLayout
    private lateinit var redeemInvitation: View
    private lateinit var scanInvitation: View

    private val collab get() = ServiceLocator.collabSyncRepository
    private val bridge get() = ServiceLocator.collabLocalBridge
    private val bindings get() = ServiceLocator.worksiteBindingRepository
    private val teamApi = TeamManagementApi()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        status = view.findViewById(R.id.txtBaustellenSyncStatus)
        progress = view.findViewById(R.id.progressBaustellenSync)
        container = view.findViewById(R.id.containerBaustellenSync)
        refresh = view.findViewById(R.id.btnBaustellenSyncRefresh)
        teamContainer = view.findViewById(R.id.containerCollabTeams)
        redeemInvitation = view.findViewById(R.id.btnCollabRedeemInvitation)
        scanInvitation = view.findViewById(R.id.btnCollabScanInvitation)

        refresh.setOnClickListener { synchronize() }
        redeemInvitation.setOnClickListener { showRedeemInvitationDialog() }
        scanInvitation.setOnClickListener { scanInvitationQr() }

        renderCached()
        teamContainer.removeAllViews()
        teamContainer.addView(muted(getString(R.string.collab_team_loading)))
        synchronize()
    }

    private fun renderCached() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { bindings.contextsWithBindings() }.getOrDefault(emptyList())
            }
            if (!isAdded) return@launch
            render(items)
        }
    }

    private fun synchronize() {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            status.text = getString(R.string.collab_not_activated)
            teamContainer.removeAllViews()
            teamContainer.addView(muted(getString(R.string.collab_not_activated)))
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
                    teamContainer.removeAllViews()
                    teamContainer.addView(muted(getString(R.string.collab_team_unavailable)))
                }
                SyncUiResult.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    status.text = getString(R.string.collab_session_invalid)
                    teamContainer.removeAllViews()
                    teamContainer.addView(muted(getString(R.string.collab_session_invalid)))
                }
                is SyncUiResult.Error -> {
                    status.text = getString(R.string.collab_sync_error, result.message)
                    renderCached()
                }
                is SyncUiResult.ContextOnly -> {
                    status.text = getString(
                        R.string.collab_server1_context_only,
                        result.worksites.size
                    )
                    render(result.worksites)
                    loadTeamAndCompanyProfile(token, result.worksites)
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
                    loadTeamAndCompanyProfile(token, result.worksites)
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
        data class ContextOnly(
            val worksites: List<WorksiteBindingRepository.BoundContext>
        ) : SyncUiResult
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
        when (val contextResult = teamApi.loadWorksiteContexts(token)) {
            is TeamManagementApi.WorksiteContextResult.Success -> {
                val now = System.currentTimeMillis()
                val contexts = contextResult.items.map { item ->
                    SyncWorksiteContextEntity(
                        worksiteServerId = item.worksiteId,
                        name = item.name,
                        designation = item.designation,
                        worksiteType = null,
                        status = item.status,
                        companyId = null,
                        groupId = item.groupId,
                        memberRole = item.memberRole,
                        areaScopeJson = item.areaScopeJson,
                        groupLabel = item.groupLabel,
                        leadUserId = item.leadUserId,
                        leadNameSnapshot = item.leadNameSnapshot,
                        updatedAtEpochMs = now
                    )
                }
                runCatching { collab.replaceWorksiteContexts(contexts) }
                    .getOrElse {
                        return SyncUiResult.Error(
                            it.message ?: "Server-1.0-Baustellenkontext konnte lokal nicht gespeichert werden."
                        )
                    }
                return SyncUiResult.ContextOnly(bindings.contextsWithBindings())
            }
            TeamManagementApi.WorksiteContextResult.Unsupported -> Unit
            TeamManagementApi.WorksiteContextResult.SessionInvalid ->
                return SyncUiResult.SessionInvalid
            is TeamManagementApi.WorksiteContextResult.Error ->
                return SyncUiResult.Error(contextResult.message)
        }

        val notifications = mutableListOf<CollabBenachrichtigungen.Ereignis>()
        var pulled = 0
        var uploaded = 0
        var pending = 0
        var conflicts = 0
        val btbMedia = BtbTeamMediaSync(requireContext().applicationContext)

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

        val firstMedia = when (val result = btbMedia.synchronize(token)) {
            BtbTeamMediaSync.Result.SessionInvalid -> return SyncUiResult.SessionInvalid
            BtbTeamMediaSync.Result.Unsupported -> null
            is BtbTeamMediaSync.Result.Error -> return SyncUiResult.Error(result.message)
            is BtbTeamMediaSync.Result.Success -> result.summary
        }
        uploaded += firstMedia?.uploaded ?: 0

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

        val secondMedia = when (val result = btbMedia.synchronize(token)) {
            BtbTeamMediaSync.Result.SessionInvalid -> return SyncUiResult.SessionInvalid
            BtbTeamMediaSync.Result.Unsupported -> null
            is BtbTeamMediaSync.Result.Error -> return SyncUiResult.Error(result.message)
            is BtbTeamMediaSync.Result.Success -> result.summary
        }
        uploaded += secondMedia?.uploaded ?: 0

        val attachmentUpload = when (val result = bridge.uploadLocalAttachments(token)) {
            CollabLocalBridge.AttachmentUploadResult.SessionInvalid -> return SyncUiResult.SessionInvalid
            CollabLocalBridge.AttachmentUploadResult.Unsupported -> null
            is CollabLocalBridge.AttachmentUploadResult.Error -> return SyncUiResult.Error(result.message)
            is CollabLocalBridge.AttachmentUploadResult.Success -> result.summary
        }
        uploaded += attachmentUpload?.uploaded ?: 0

        val anyMediaUploaded = (firstMedia?.uploaded ?: 0) + (secondMedia?.uploaded ?: 0) +
            (attachmentUpload?.uploaded ?: 0) > 0
        if (anyMediaUploaded) {
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
            when (val result = btbMedia.synchronize(token)) {
                BtbTeamMediaSync.Result.SessionInvalid -> return SyncUiResult.SessionInvalid
                is BtbTeamMediaSync.Result.Error -> return SyncUiResult.Error(result.message)
                else -> Unit
            }
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

    private data class TeamLoadSummary(
        val teams: List<TeamManagementApi.TeamOverview>,
        val unsupported: Boolean,
        val sessionInvalid: Boolean,
        val errors: List<String>,
        val companyProfileError: String?
    )

    private fun loadTeamAndCompanyProfile(
        token: String,
        worksites: List<WorksiteBindingRepository.BoundContext>
    ) {
        val appContext = requireContext().applicationContext
        teamContainer.removeAllViews()
        teamContainer.addView(muted(getString(R.string.collab_team_loading)))

        viewLifecycleOwner.lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val profileResult = CompanyProfileSync.synchronize(appContext, token, teamApi)
                var unsupported = false
                var sessionInvalid = profileResult == CompanyProfileSync.Result.SessionInvalid
                val errors = mutableListOf<String>()
                val teams = mutableListOf<TeamManagementApi.TeamOverview>()
                val profileError = (profileResult as? CompanyProfileSync.Result.Error)?.message

                worksites.forEach { worksite ->
                    if (sessionInvalid) return@forEach
                    when (val result = teamApi.loadTeam(token, worksite.context.worksiteServerId)) {
                        is TeamManagementApi.TeamResult.Success -> {
                            teams += result.team
                            TeamDisplayCache.remember(appContext, result.team)
                        }
                        TeamManagementApi.TeamResult.Unsupported -> unsupported = true
                        TeamManagementApi.TeamResult.SessionInvalid -> sessionInvalid = true
                        is TeamManagementApi.TeamResult.Error -> errors += result.message
                    }
                }
                TeamLoadSummary(
                    teams = teams,
                    unsupported = unsupported,
                    sessionInvalid = sessionInvalid,
                    errors = errors.distinct(),
                    companyProfileError = profileError
                )
            }
            if (!isAdded) return@launch
            if (summary.sessionInvalid) {
                SecureTokenStore(requireContext()).clear()
                teamContainer.removeAllViews()
                teamContainer.addView(muted(getString(R.string.collab_session_invalid)))
                return@launch
            }
            when {
                summary.teams.isNotEmpty() -> renderTeams(summary.teams)
                summary.unsupported -> {
                    teamContainer.removeAllViews()
                    teamContainer.addView(muted(getString(R.string.collab_team_unavailable)))
                }
                summary.errors.isNotEmpty() -> {
                    teamContainer.removeAllViews()
                    teamContainer.addView(muted(summary.errors.joinToString("\n")))
                }
                else -> {
                    teamContainer.removeAllViews()
                    teamContainer.addView(muted(getString(R.string.collab_no_worksites)))
                }
            }
            summary.companyProfileError?.let {
                Toast.makeText(requireContext(), getString(R.string.collab_company_profile_error, it), Toast.LENGTH_LONG).show()
            }
        }
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
            val responsible = server.leadNameSnapshot?.takeIf { it.isNotBlank() } ?: "TB"
            val role = roleLabel(server)
            val localText = local?.hauptbaustellenName?.let { getString(R.string.collab_bound_to, it) }
                ?: getString(R.string.collab_not_bound)
            val detail = "$responsible · $role\n$localText"
            container.addView(itemView(title, detail) { chooseLocalWorksite(item) })
        }
    }

    private fun renderTeams(teams: List<TeamManagementApi.TeamOverview>) {
        teamContainer.removeAllViews()
        teams.forEachIndexed { worksiteIndex, team ->
            if (worksiteIndex > 0) teamContainer.addView(separator())

            val worksiteTitle = team.worksiteName?.takeIf { it.isNotBlank() }
                ?: team.worksiteDesignation?.takeIf { it.isNotBlank() }
                ?: getString(R.string.collab_worksite_fallback)
            teamContainer.addView(TextView(requireContext()).apply {
                text = worksiteTitle
                setPadding(dp(12), dp(10), dp(12), dp(5))
                setTextAppearance(R.style.TextAppearance_TBHelfer_Body)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            })

            team.groups.forEach { group ->
                val lead = group.leadDisplayName?.takeIf { it.isNotBlank() }
                    ?: group.leadNameSnapshot?.takeIf { it.isNotBlank() }
                    ?: group.label?.takeIf { it.isNotBlank() }
                    ?: "TB"
                val helpers = group.members
                    .filterNot { it.memberRole.equals("LEAD", ignoreCase = true) }
                    .mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
                val helperText = if (helpers.isEmpty()) getString(R.string.collab_team_helpers_none) else helpers.joinToString(", ")
                val ownMarker = if (group.groupId == team.currentGroupId) " · ${getString(R.string.collab_team_own)}" else ""
                teamContainer.addView(
                    teamGroupView(
                        title = getString(R.string.collab_team_tb_prefix, lead) + ownMarker,
                        detail = getString(R.string.collab_team_helpers_prefix, helperText)
                    )
                )
            }

            if (team.currentMemberRole.equals("LEAD", ignoreCase = true)) {
                teamContainer.addView(actionButton(getString(R.string.collab_invite_helper)) {
                    showHelperScopeDialog(team)
                })
                teamContainer.addView(actionButton(getString(R.string.collab_invite_lead)) {
                    createInvitation(team, "LEAD", "{\"all\":true}")
                })
            }
        }
    }

    private fun showHelperScopeDialog(team: TeamManagementApi.TeamOverview) {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val all = CheckBox(requireContext()).apply {
            text = getString(R.string.collab_scope_all)
            isChecked = true
        }
        val labels = EditText(requireContext()).apply {
            hint = getString(R.string.collab_scope_labels_hint)
            isEnabled = false
        }
        val kmFrom = EditText(requireContext()).apply {
            hint = getString(R.string.collab_scope_km_from)
            isEnabled = false
        }
        val kmTo = EditText(requireContext()).apply {
            hint = getString(R.string.collab_scope_km_to)
            isEnabled = false
        }
        all.setOnCheckedChangeListener { _, checked ->
            labels.isEnabled = !checked
            kmFrom.isEnabled = !checked
            kmTo.isEnabled = !checked
        }
        wrapper.addView(all)
        wrapper.addView(labels)
        wrapper.addView(kmFrom)
        wrapper.addView(kmTo)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.collab_scope_title)
            .setView(wrapper)
            .setPositiveButton(R.string.collab_invite_create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val scope = if (all.isChecked) {
                    JSONObject().put("all", true)
                } else {
                    buildHelperScope(labels.text?.toString().orEmpty(), kmFrom.text?.toString().orEmpty(), kmTo.text?.toString().orEmpty())
                }
                if (scope == null) {
                    Toast.makeText(requireContext(), R.string.collab_scope_required, Toast.LENGTH_LONG).show()
                } else {
                    dialog.dismiss()
                    createInvitation(team, "HELPER", scope.toString())
                }
            }
        }
        dialog.show()
    }

    private fun buildHelperScope(labelsText: String, kmFromText: String, kmToText: String): JSONObject? {
        val labels = labelsText.split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val from = parseKmM(kmFromText)
        val to = parseKmM(kmToText)
        if (labels.isEmpty() && from == null && to == null) return null
        return JSONObject().apply {
            put("all", false)
            if (labels.isNotEmpty()) {
                put("labels", org.json.JSONArray(labels))
                put("label", labels.joinToString(", "))
            }
            if (from != null || to != null) {
                val a = from ?: to!!
                val b = to ?: from!!
                put("km_from_m", minOf(a, b))
                put("km_to_m", maxOf(a, b))
            }
        }
    }

    private fun parseKmM(raw: String): Double? {
        val text = raw.trim().replace(" ", "").replace(',', '.')
        if (text.isBlank()) return null
        return if ('+' in text) {
            val p = text.split('+', limit = 2)
            val km = p.getOrNull(0)?.toDoubleOrNull() ?: return null
            val meter = p.getOrNull(1)?.toDoubleOrNull() ?: return null
            km * 1000.0 + meter
        } else text.toDoubleOrNull()
    }

    private fun createInvitation(
        team: TeamManagementApi.TeamOverview,
        invitationType: String,
        areaScopeJson: String
    ) {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.collab_not_activated,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                teamApi.loadServerInvitationCandidates(token, team.worksiteId)
            }
            if (!isAdded) return@launch
            when (candidates) {
                is TeamManagementApi.InvitationCandidatesResult.Success -> {
                    if (candidates.candidates.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            R.string.collab_invite_no_candidates,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showServerInvitationCandidateDialog(
                            team = team,
                            invitationType = invitationType,
                            areaScopeJson = areaScopeJson,
                            token = token,
                            candidates = candidates.candidates
                        )
                    }
                }
                TeamManagementApi.InvitationCandidatesResult.Unsupported -> {
                    createLegacyInvitation(team, invitationType, areaScopeJson, token)
                }
                TeamManagementApi.InvitationCandidatesResult.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    Toast.makeText(
                        requireContext(),
                        R.string.collab_session_invalid,
                        Toast.LENGTH_LONG
                    ).show()
                }
                is TeamManagementApi.InvitationCandidatesResult.Error -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.collab_invite_error, candidates.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showServerInvitationCandidateDialog(
        team: TeamManagementApi.TeamOverview,
        invitationType: String,
        areaScopeJson: String,
        token: String,
        candidates: List<TeamManagementApi.InvitationCandidate>
    ) {
        val labels = candidates.map { candidate ->
            buildString {
                append(candidate.displayName)
                candidate.email?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
            }
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.collab_invite_choose_person)
            .setItems(labels) { _, which ->
                val candidate = candidates.getOrNull(which) ?: return@setItems
                createServerInvitation(
                    team = team,
                    invitationType = invitationType,
                    areaScopeJson = areaScopeJson,
                    token = token,
                    candidate = candidate
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createServerInvitation(
        team: TeamManagementApi.TeamOverview,
        invitationType: String,
        areaScopeJson: String,
        token: String,
        candidate: TeamManagementApi.InvitationCandidate
    ) {
        val helper = invitationType.equals("HELPER", ignoreCase = true)
        val role = if (helper) "HELPER" else "RESPONSIBLE_TB"
        val profile = if (helper) "HELPER_STANDARD" else "RESPONSIBLE_TB"

        Toast.makeText(
            requireContext(),
            R.string.collab_invite_creating,
            Toast.LENGTH_SHORT
        ).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                teamApi.createServerInvitation(
                    token = token,
                    worksiteId = team.worksiteId,
                    invitedUserId = candidate.userId,
                    worksiteRole = role,
                    permissionProfile = profile,
                    areaScopeJson = areaScopeJson
                )
            }
            if (!isAdded) return@launch
            handleInvitationResult(team, result)
        }
    }

    private fun createLegacyInvitation(
        team: TeamManagementApi.TeamOverview,
        invitationType: String,
        areaScopeJson: String,
        token: String
    ) {
        Toast.makeText(
            requireContext(),
            R.string.collab_invite_creating,
            Toast.LENGTH_SHORT
        ).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                teamApi.createInvitation(
                    token,
                    team.worksiteId,
                    invitationType,
                    areaScopeJson
                )
            }
            if (!isAdded) return@launch
            handleInvitationResult(team, result)
        }
    }

    private fun handleInvitationResult(
        team: TeamManagementApi.TeamOverview,
        result: TeamManagementApi.InvitationResult
    ) {
        when (result) {
            is TeamManagementApi.InvitationResult.Success ->
                showInvitationQr(team, result.invitation)
            TeamManagementApi.InvitationResult.Unsupported -> Toast.makeText(
                requireContext(),
                R.string.collab_team_unavailable,
                Toast.LENGTH_LONG
            ).show()
            TeamManagementApi.InvitationResult.SessionInvalid -> {
                SecureTokenStore(requireContext()).clear()
                Toast.makeText(
                    requireContext(),
                    R.string.collab_session_invalid,
                    Toast.LENGTH_LONG
                ).show()
            }
            is TeamManagementApi.InvitationResult.Error -> Toast.makeText(
                requireContext(),
                getString(R.string.collab_invite_error, result.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showInvitationQr(team: TeamManagementApi.TeamOverview, invitation: TeamManagementApi.Invitation) {
        val worksite = team.worksiteName?.takeIf { it.isNotBlank() }
            ?: team.worksiteDesignation?.takeIf { it.isNotBlank() }
            ?: getString(R.string.collab_worksite_fallback)
        val link = invitation.deepLink?.takeIf { it.isNotBlank() }
            ?: "tbhelfer://invite?code=${Uri.encode(invitation.code)}"
        val image = ImageView(requireContext()).apply {
            adjustViewBounds = true
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setImageBitmap(InvitationQr.bitmap(link, 720))
        }
        val code = TextView(requireContext()).apply {
            text = invitation.code
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextIsSelectable(true)
            setPadding(dp(8), dp(10), dp(8), dp(4))
        }
        val expiry = TextView(requireContext()).apply {
            text = invitation.expiresAt?.let { getString(R.string.collab_invite_expires, it) }
                ?: getString(R.string.collab_invite_single_use)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            alpha = 0.7f
        }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(6))
            addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330)))
            addView(code)
            addView(expiry)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (invitation.invitationType == "HELPER") R.string.collab_invite_helper_qr_title else R.string.collab_invite_lead_qr_title)
            .setMessage(worksite)
            .setView(box)
            .setPositiveButton(R.string.collab_invite_share) { _, _ -> shareInvitation(team, invitation) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareInvitation(team: TeamManagementApi.TeamOverview, invitation: TeamManagementApi.Invitation) {
        val worksite = team.worksiteName?.takeIf { it.isNotBlank() }
            ?: team.worksiteDesignation?.takeIf { it.isNotBlank() }
            ?: getString(R.string.collab_worksite_fallback)
        val link = invitation.shareLink?.takeIf { it.isNotBlank() }
            ?: invitation.deepLink?.takeIf { it.isNotBlank() }
            ?: "tbhelfer://invite?code=${Uri.encode(invitation.code)}"
        val text = getString(
            R.string.collab_invite_share_text,
            worksite,
            invitation.code,
            link
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.collab_invite_share_title))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.collab_invite_share_title)))
    }

    private fun scanInvitationQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(requireActivity(), options).startScan()
            .addOnSuccessListener { barcode ->
                val invitation = WorksiteInvitationLink.parse(barcode.rawValue.orEmpty())
                if (invitation == null) {
                    Toast.makeText(
                        requireContext(),
                        R.string.collab_invite_invalid_qr,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    redeemInvitation(invitation)
                }
            }
            .addOnCanceledListener { }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    R.string.collab_invite_scan_error,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showRedeemInvitationDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.collab_redeem_hint)
            isSingleLine = false
            maxLines = 4
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.collab_redeem_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val invitation = WorksiteInvitationLink.parse(
                    input.text?.toString().orEmpty()
                )
                if (invitation != null) {
                    redeemInvitation(invitation)
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.collab_invite_invalid_code,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun redeemInvitation(invitation: WorksiteInvitationLink.Credential) {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.collab_not_activated,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (invitation) {
                    is WorksiteInvitationLink.Credential.ServerToken ->
                        teamApi.redeemServerInvitation(token, invitation.value)
                    is WorksiteInvitationLink.Credential.LegacyCode ->
                        teamApi.redeemInvitation(token, invitation.value)
                }
            }
            if (!isAdded) return@launch
            when (result) {
                is TeamManagementApi.RedeemResult.Success -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.collab_redeem_success,
                        Toast.LENGTH_LONG
                    ).show()
                    synchronize()
                }
                TeamManagementApi.RedeemResult.Unsupported -> Toast.makeText(
                    requireContext(),
                    R.string.collab_team_unavailable,
                    Toast.LENGTH_LONG
                ).show()
                TeamManagementApi.RedeemResult.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    Toast.makeText(
                        requireContext(),
                        R.string.collab_session_invalid,
                        Toast.LENGTH_LONG
                    ).show()
                }
                is TeamManagementApi.RedeemResult.Error -> Toast.makeText(
                    requireContext(),
                    getString(R.string.collab_invite_error, result.message),
                    Toast.LENGTH_LONG
                ).show()
            }
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
                localItems.forEach { local -> add("${local.hauptbaustellenName} · lokal #${local.id}") }
            }.toTypedArray()
            val offset = if (item.binding != null) 1 else 0
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.collab_choose_local_title, displayName(item.context)))
                .setItems(choices) { _, which ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val action = withContext(Dispatchers.IO) {
                            runCatching {
                                if (offset == 1 && which == 0) bindings.unbindServer(item.context.worksiteServerId)
                                else bindings.bind(localItems[which - offset].id, item.context.worksiteServerId)
                            }
                        }
                        if (!isAdded) return@launch
                        action.onFailure {
                            Toast.makeText(requireContext(), it.message ?: getString(R.string.collab_binding_failed), Toast.LENGTH_LONG).show()
                        }
                        if (action.isSuccess) {
                            renderCached()
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
        } else getString(R.string.collab_role_helper)
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

    private fun teamGroupView(title: String, detail: String): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(9), dp(14), dp(9))
        addView(TextView(context).apply {
            text = title
            setTextAppearance(R.style.TextAppearance_TBHelfer_Body)
        })
        addView(TextView(context).apply {
            text = detail
            alpha = 0.72f
            setPadding(0, dp(2), 0, 0)
            setTextAppearance(R.style.TextAppearance_TBHelfer_Caption)
        })
    }

    private fun actionButton(label: String, onClick: () -> Unit): MaterialButton = MaterialButton(requireContext()).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(10); rightMargin = dp(10); topMargin = dp(6)
        }
        setOnClickListener { onClick() }
    }

    private fun separator(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        }
        alpha = 0.18f
        setBackgroundColor(Color.GRAY)
    }

    private fun muted(textValue: String): TextView = TextView(requireContext()).apply {
        text = textValue
        alpha = 0.7f
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        refresh.isEnabled = !loading
        redeemInvitation.isEnabled = !loading
        scanInvitation.isEnabled = !loading
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
