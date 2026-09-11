package de.tbhelfer.ui.baustelle

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.server.TeamManagementApi
import de.tbhelfer.server.WorksiteProvisioningApi
import de.tbhelfer.ui.sync.InvitationQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direkter Baustellen-Teamablauf ohne zusätzliche Sync-Bedienseite.
 *
 * - "Nutzer hinzufügen" bleibt in der Baustellenliste und erzeugt dort den QR.
 * - "QR scannen" tritt direkt einer Baustelle bei; der QR entscheidet LEAD/HELPER.
 * - Nach erfolgreichem Beitritt wird synchronisiert und die Baustelle direkt als aktuell gesetzt.
 *
 * Die separate BaustellenSyncFragment-Ansicht bleibt vorerst technisch erhalten, ist aber kein
 * normaler Benutzereinstieg mehr. So können Diagnose-/Altpfade weiter funktionieren, ohne den
 * Arbeitsablauf mit zusätzlichen Menüs zu belasten.
 */
object BaustellenTeamDirekt {
    private val teamApi = TeamManagementApi()

    fun oeffneEinladen(fragment: Fragment, haupt: BaustellenKnotenEntity) {
        val tokenStore = SecureTokenStore(fragment.requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), "Gerät ist noch nicht für den Server aktiviert.", Toast.LENGTH_LONG).show()
            return
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(fragment.requireContext(), "Baustellenteam wird vorbereitet …", Toast.LENGTH_SHORT).show()
            val outcome = withContext(Dispatchers.IO) {
                provisionAndLoadTeam(fragment, token, haupt)
            }
            if (!fragment.isAdded) return@launch

            when (outcome) {
                ProvisionOutcome.SessionInvalid -> {
                    tokenStore.clear()
                    Toast.makeText(fragment.requireContext(), "Gerätefreigabe ist nicht mehr gültig.", Toast.LENGTH_LONG).show()
                }
                is ProvisionOutcome.Error -> Toast.makeText(
                    fragment.requireContext(),
                    "Baustellenteam konnte nicht geöffnet werden: ${outcome.message}",
                    Toast.LENGTH_LONG
                ).show()
                is ProvisionOutcome.Success -> {
                    if (outcome.created) {
                        Toast.makeText(fragment.requireContext(), "Baustelle wurde mit dem Server verbunden.", Toast.LENGTH_SHORT).show()
                    }
                    showInviteChoice(fragment, outcome.team)
                }
            }
        }
    }

    fun qrScannen(fragment: Fragment) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(fragment.requireActivity(), options).startScan()
            .addOnSuccessListener { barcode ->
                val code = extractInvitationCode(barcode.rawValue.orEmpty())
                if (code == null) {
                    Toast.makeText(fragment.requireContext(), "Kein gültiger TB-Helfer-Baustellen-QR.", Toast.LENGTH_LONG).show()
                } else {
                    redeemInvitationCode(fragment, code)
                }
            }
            .addOnFailureListener {
                Toast.makeText(fragment.requireContext(), "QR-Code konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
            }
    }

    private suspend fun provisionAndLoadTeam(
        fragment: Fragment,
        token: String,
        haupt: BaustellenKnotenEntity
    ): ProvisionOutcome {
        return when (val created = WorksiteProvisioningApi().create(token, haupt)) {
            WorksiteProvisioningApi.Result.SessionInvalid -> ProvisionOutcome.SessionInvalid
            WorksiteProvisioningApi.Result.Unsupported -> ProvisionOutcome.Error("Dieser Serverstand unterstützt die Selbstanlage noch nicht.")
            is WorksiteProvisioningApi.Result.Error -> ProvisionOutcome.Error(created.message)
            is WorksiteProvisioningApi.Result.Success -> {
                when (val sync = ServiceLocator.collabSyncRepository.synchronizeAll(token)) {
                    CollabSyncRepository.FullSyncResult.SessionInvalid -> ProvisionOutcome.SessionInvalid
                    is CollabSyncRepository.FullSyncResult.Error -> ProvisionOutcome.Error(sync.message)
                    is CollabSyncRepository.FullSyncResult.Success -> {
                        runCatching {
                            ServiceLocator.worksiteBindingRepository.bind(haupt.id, created.value.worksiteId)
                        }.fold(
                            onFailure = { ProvisionOutcome.Error(it.message ?: "Lokale Verknüpfung fehlgeschlagen.") },
                            onSuccess = {
                                when (val team = teamApi.loadTeam(token, created.value.worksiteId)) {
                                    TeamManagementApi.TeamResult.SessionInvalid -> ProvisionOutcome.SessionInvalid
                                    TeamManagementApi.TeamResult.Unsupported -> ProvisionOutcome.Error("Teamverwaltung wird vom Server nicht unterstützt.")
                                    is TeamManagementApi.TeamResult.Error -> ProvisionOutcome.Error(team.message)
                                    is TeamManagementApi.TeamResult.Success -> {
                                        TeamDisplayCache.remember(fragment.requireContext().applicationContext, team.team)
                                        ProvisionOutcome.Success(created.value.wasCreated, team.team)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun showInviteChoice(fragment: Fragment, team: TeamManagementApi.TeamOverview) {
        if (!team.currentMemberRole.equals("LEAD", ignoreCase = true)) {
            Toast.makeText(fragment.requireContext(), "Nur ein verantwortlicher TB kann Nutzer oder Helfer einladen.", Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(team.worksiteName ?: "Baustellenteam")
            .setItems(arrayOf("Nutzer (verantwortlichen TB) hinzufügen", "Helfer hinzufügen")) { _, which ->
                when (which) {
                    0 -> createInvitation(fragment, team, "LEAD", "{\"all\":true}")
                    1 -> showHelperScopeDialog(fragment, team)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showHelperScopeDialog(fragment: Fragment, team: TeamManagementApi.TeamOverview) {
        val wrapper = LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(fragment, 20), dp(fragment, 4), dp(fragment, 20), 0)
        }
        val all = CheckBox(fragment.requireContext()).apply {
            text = "Gesamte Baustelle"
            isChecked = true
        }
        val labels = EditText(fragment.requireContext()).apply {
            hint = "Bereiche, z. B. Gleis 1–4"
            isEnabled = false
        }
        val kmFrom = EditText(fragment.requireContext()).apply {
            hint = "km von, z. B. 70+500"
            isEnabled = false
        }
        val kmTo = EditText(fragment.requireContext()).apply {
            hint = "km bis, z. B. 81+600"
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

        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Helfer-Bereich")
            .setView(wrapper)
            .setPositiveButton("QR erzeugen", null)
            .setNegativeButton("Abbrechen", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val scope = if (all.isChecked) {
                    JSONObject().put("all", true)
                } else {
                    buildHelperScope(labels.text?.toString().orEmpty(), kmFrom.text?.toString().orEmpty(), kmTo.text?.toString().orEmpty())
                }
                if (scope == null) {
                    Toast.makeText(fragment.requireContext(), "Bitte einen Bereich oder km-Bereich angeben.", Toast.LENGTH_LONG).show()
                } else {
                    dialog.dismiss()
                    createInvitation(fragment, team, "HELPER", scope.toString())
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
                put("labels", JSONArray(labels))
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
            val parts = text.split('+', limit = 2)
            val km = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
            val meter = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
            km * 1000.0 + meter
        } else {
            text.toDoubleOrNull()
        }
    }

    private fun createInvitation(
        fragment: Fragment,
        team: TeamManagementApi.TeamOverview,
        invitationType: String,
        areaScopeJson: String
    ) {
        val tokenStore = SecureTokenStore(fragment.requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), "Gerät ist noch nicht für den Server aktiviert.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(fragment.requireContext(), "QR wird erzeugt …", Toast.LENGTH_SHORT).show()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                teamApi.createInvitation(token, team.worksiteId, invitationType, areaScopeJson)
            }
            if (!fragment.isAdded) return@launch
            when (result) {
                TeamManagementApi.InvitationResult.SessionInvalid -> {
                    tokenStore.clear()
                    Toast.makeText(fragment.requireContext(), "Gerätefreigabe ist nicht mehr gültig.", Toast.LENGTH_LONG).show()
                }
                TeamManagementApi.InvitationResult.Unsupported -> Toast.makeText(fragment.requireContext(), "Einladungen werden vom Server nicht unterstützt.", Toast.LENGTH_LONG).show()
                is TeamManagementApi.InvitationResult.Error -> Toast.makeText(fragment.requireContext(), result.message, Toast.LENGTH_LONG).show()
                is TeamManagementApi.InvitationResult.Success -> showInvitationQr(fragment, team, result.invitation)
            }
        }
    }

    private fun showInvitationQr(
        fragment: Fragment,
        team: TeamManagementApi.TeamOverview,
        invitation: TeamManagementApi.Invitation
    ) {
        val worksite = team.worksiteName?.takeIf { it.isNotBlank() }
            ?: team.worksiteDesignation?.takeIf { it.isNotBlank() }
            ?: "Baustelle"
        val link = invitation.deepLink?.takeIf { it.isNotBlank() }
            ?: "tbhelfer://invite?code=${Uri.encode(invitation.code)}"
        val image = ImageView(fragment.requireContext()).apply {
            adjustViewBounds = true
            setBackgroundColor(Color.WHITE)
            setPadding(dp(fragment, 10), dp(fragment, 10), dp(fragment, 10), dp(fragment, 10))
            setImageBitmap(InvitationQr.bitmap(link, 720))
        }
        val code = TextView(fragment.requireContext()).apply {
            text = invitation.code
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextIsSelectable(true)
            setPadding(dp(fragment, 8), dp(fragment, 10), dp(fragment, 8), dp(fragment, 4))
        }
        val role = TextView(fragment.requireContext()).apply {
            text = if (invitation.invitationType == "HELPER") "Helfer" else "Nutzer / verantwortlicher TB"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val expiry = TextView(fragment.requireContext()).apply {
            text = invitation.expiresAt?.let { "Einmalig · gültig bis $it" } ?: "Einmalig verwendbar"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            alpha = 0.7f
        }
        val box = LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(fragment, 18), dp(fragment, 8), dp(fragment, 18), dp(fragment, 6))
            addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(fragment, 330)))
            addView(role)
            addView(code)
            addView(expiry)
        }

        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Baustellen-QR")
            .setMessage(worksite)
            .setView(box)
            .setPositiveButton("Teilen / senden") { _, _ -> shareInvitation(fragment, team, invitation) }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun shareInvitation(
        fragment: Fragment,
        team: TeamManagementApi.TeamOverview,
        invitation: TeamManagementApi.Invitation
    ) {
        val worksite = team.worksiteName?.takeIf { it.isNotBlank() }
            ?: team.worksiteDesignation?.takeIf { it.isNotBlank() }
            ?: "Baustelle"
        val link = invitation.deepLink?.takeIf { it.isNotBlank() }
            ?: "tbhelfer://invite?code=${Uri.encode(invitation.code)}"
        val role = if (invitation.invitationType == "HELPER") "Helfer" else "verantwortlicher TB"
        val text = "TB-Helfer Einladung für $worksite ($role)\n\nCode: ${invitation.code}\n$link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "TB-Helfer Baustelleneinladung")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        fragment.startActivity(Intent.createChooser(intent, "Einladung teilen / senden"))
    }

    private fun extractInvitationCode(raw: String): String? {
        val text = raw.trim()
        if (text.startsWith("TBI-", ignoreCase = true)) return text.uppercase()
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("tbhelfer", ignoreCase = true) || !uri.host.equals("invite", ignoreCase = true)) return null
        return uri.getQueryParameter("code")?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    }

    private fun redeemInvitationCode(fragment: Fragment, code: String) {
        val tokenStore = SecureTokenStore(fragment.requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), "Gerät ist noch nicht für den Server aktiviert.", Toast.LENGTH_LONG).show()
            return
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(fragment.requireContext(), "Baustelle wird übernommen …", Toast.LENGTH_SHORT).show()
            val redeem = withContext(Dispatchers.IO) { teamApi.redeemInvitation(token, code) }
            if (!fragment.isAdded) return@launch

            when (redeem) {
                TeamManagementApi.RedeemResult.SessionInvalid -> {
                    tokenStore.clear()
                    Toast.makeText(fragment.requireContext(), "Gerätefreigabe ist nicht mehr gültig.", Toast.LENGTH_LONG).show()
                }
                TeamManagementApi.RedeemResult.Unsupported -> Toast.makeText(fragment.requireContext(), "Baustellen-Einladungen werden vom Server nicht unterstützt.", Toast.LENGTH_LONG).show()
                is TeamManagementApi.RedeemResult.Error -> Toast.makeText(fragment.requireContext(), redeem.message, Toast.LENGTH_LONG).show()
                is TeamManagementApi.RedeemResult.Success -> {
                    val sync = withContext(Dispatchers.IO) { syncJoinedWorksite(token, redeem.result) }
                    if (!fragment.isAdded) return@launch
                    when (sync) {
                        JoinOutcome.SessionInvalid -> {
                            tokenStore.clear()
                            Toast.makeText(fragment.requireContext(), "Gerätefreigabe ist nicht mehr gültig.", Toast.LENGTH_LONG).show()
                        }
                        is JoinOutcome.Error -> Toast.makeText(
                            fragment.requireContext(),
                            "Beitritt war erfolgreich, Synchronisierung aber noch nicht vollständig: ${sync.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        is JoinOutcome.Success -> {
                            val local = sync.localWorksite
                            if (local != null) {
                                ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(local.id, local.altBaustelleId)
                                val role = if (redeem.result.memberRole.equals("HELPER", true)) "Helfer" else "verantwortlicher TB"
                                Toast.makeText(
                                    fragment.requireContext(),
                                    "${redeem.result.worksiteName ?: local.hauptbaustellenName} übernommen · $role",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                showBindingChoice(fragment, redeem.result)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun syncJoinedWorksite(
        token: String,
        redeemed: TeamManagementApi.RedeemedInvitation
    ): JoinOutcome {
        return when (val sync = ServiceLocator.collabSyncRepository.synchronizeAll(token)) {
            CollabSyncRepository.FullSyncResult.SessionInvalid -> JoinOutcome.SessionInvalid
            is CollabSyncRepository.FullSyncResult.Error -> JoinOutcome.Error(sync.message)
            is CollabSyncRepository.FullSyncResult.Success -> {
                runCatching {
                    val contexts = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
                    ServiceLocator.collabLocalBridge.applyServerStateToLocal()
                    val item = contexts.firstOrNull { it.context.worksiteServerId == redeemed.worksiteId }
                    JoinOutcome.Success(item?.localWorksite)
                }.getOrElse { JoinOutcome.Error(it.message ?: "Lokale Baustelle konnte nicht aufgebaut werden.") }
            }
        }
    }

    private fun showBindingChoice(fragment: Fragment, redeemed: TeamManagementApi.RedeemedInvitation) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val roots = withContext(Dispatchers.IO) { ServiceLocator.worksiteBindingRepository.localMainWorksites() }
            if (!fragment.isAdded) return@launch
            if (roots.isEmpty()) {
                Toast.makeText(fragment.requireContext(), "Beitritt erfolgreich, aber keine lokale Baustelle zum Verknüpfen gefunden.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val labels = roots.map { "${it.hauptbaustellenName} · lokal #${it.id}" }.toTypedArray()
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Lokale Baustelle für „${redeemed.worksiteName ?: "Server-Baustelle"}“")
                .setItems(labels) { _, which ->
                    val selected = roots.getOrNull(which) ?: return@setItems
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        val error = withContext(Dispatchers.IO) {
                            runCatching {
                                ServiceLocator.worksiteBindingRepository.bind(selected.id, redeemed.worksiteId)
                                ServiceLocator.collabLocalBridge.applyServerStateToLocal()
                            }.exceptionOrNull()?.message
                        }
                        if (!fragment.isAdded) return@launch
                        if (error == null) {
                            ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(selected.id, selected.altBaustelleId)
                            Toast.makeText(fragment.requireContext(), "Baustelle verbunden und als aktuell gesetzt.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(fragment.requireContext(), "Verknüpfung fehlgeschlagen: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private sealed interface ProvisionOutcome {
        data class Success(val created: Boolean, val team: TeamManagementApi.TeamOverview) : ProvisionOutcome
        data object SessionInvalid : ProvisionOutcome
        data class Error(val message: String) : ProvisionOutcome
    }

    private sealed interface JoinOutcome {
        data class Success(val localWorksite: BaustellenKnotenEntity?) : JoinOutcome
        data object SessionInvalid : JoinOutcome
        data class Error(val message: String) : JoinOutcome
    }

    private fun dp(fragment: Fragment, value: Int): Int =
        (value * fragment.resources.displayMetrics.density).toInt()
}
