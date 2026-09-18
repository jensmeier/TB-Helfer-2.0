package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Selbstverwaltete Baustellen-Teams ab Server-Schema 10.
 *
 * Identitäten werden ausschließlich über Server-UUIDs behandelt. Sichtbare Namen dürfen
 * doppelt vorkommen; der Server liefert dafür bei Bedarf einen stabilen Anzeigenamens-Zusatz.
 */
class TeamManagementApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    private val capabilitiesApi = ServerCapabilitiesApi(baseUrl)

    data class TeamMember(
        val userId: String,
        val name: String?,
        val email: String?,
        val memberRole: String,
        val areaScopeJson: String
    )

    data class TeamGroup(
        val groupId: String,
        val label: String?,
        val leadUserId: String?,
        val leadNameSnapshot: String?,
        val leadNameOrdinal: Int,
        val leadDisplayName: String?,
        val members: List<TeamMember>
    )

    data class TeamOverview(
        val worksiteId: String,
        val worksiteName: String?,
        val worksiteDesignation: String?,
        val currentGroupId: String,
        val currentMemberRole: String,
        val currentAreaScopeJson: String,
        val groups: List<TeamGroup>
    )

    data class WorksiteContext(
        val worksiteId: String,
        val name: String?,
        val designation: String?,
        val status: String?,
        val groupId: String,
        val memberRole: String,
        val areaScopeJson: String,
        val groupLabel: String?,
        val leadUserId: String?,
        val leadNameSnapshot: String?
    )

    data class Invitation(
        val invitationId: String,
        val invitationType: String,
        val code: String,
        val deepLink: String?,
        val expiresAt: String?,
        val shareLink: String? = null
    )

    data class InvitationCandidate(
        val userId: String,
        val displayName: String,
        val email: String?,
        val phone: String?,
        val companyRole: String?
    )

    private data class ModernTeamMember(
        val userId: String,
        val name: String?,
        val email: String?,
        val worksiteRole: String,
        val areaScopeJson: String,
        val teamId: String?
    )

    data class RedeemedInvitation(
        val worksiteId: String,
        val worksiteName: String?,
        val groupId: String,
        val memberRole: String
    )

    data class CompanyProfile(
        val companyId: String,
        val name: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val postalCode: String?,
        val city: String?,
        val phone: String?,
        val email: String?,
        val website: String?,
        val hasLogo: Boolean,
        val logoMimeType: String?,
        val logoSha256: String?,
        val logoVersion: Long,
        val profileVersion: Long
    )

    data class BinaryFile(
        val bytes: ByteArray,
        val mimeType: String?
    )

    sealed interface WorksiteContextResult {
        data class Success(val items: List<WorksiteContext>) : WorksiteContextResult
        data object Unsupported : WorksiteContextResult
        data object SessionInvalid : WorksiteContextResult
        data class Error(val message: String) : WorksiteContextResult
    }

    sealed interface TeamResult {
        data class Success(val team: TeamOverview) : TeamResult
        data object Unsupported : TeamResult
        data object SessionInvalid : TeamResult
        data class Error(val message: String) : TeamResult
    }

    sealed interface InvitationResult {
        data class Success(val invitation: Invitation) : InvitationResult
        data object Unsupported : InvitationResult
        data object SessionInvalid : InvitationResult
        data class Error(val message: String) : InvitationResult
    }

    sealed interface InvitationCandidatesResult {
        data class Success(val candidates: List<InvitationCandidate>) : InvitationCandidatesResult
        data object Unsupported : InvitationCandidatesResult
        data object SessionInvalid : InvitationCandidatesResult
        data class Error(val message: String) : InvitationCandidatesResult
    }

    sealed interface RedeemResult {
        data class Success(val result: RedeemedInvitation) : RedeemResult
        data object Unsupported : RedeemResult
        data object SessionInvalid : RedeemResult
        data class Error(val message: String) : RedeemResult
    }

    sealed interface CompanyProfileResult {
        data class Success(val profile: CompanyProfile) : CompanyProfileResult
        data object Unsupported : CompanyProfileResult
        data object SessionInvalid : CompanyProfileResult
        data class Error(val message: String) : CompanyProfileResult
    }

    sealed interface LogoResult {
        data class Success(val file: BinaryFile) : LogoResult
        data object NotConfigured : LogoResult
        data object Unsupported : LogoResult
        data object SessionInvalid : LogoResult
        data class Error(val message: String) : LogoResult
    }

    fun loadWorksiteContexts(token: String): WorksiteContextResult {
        return when (val capabilities = capabilitiesApi.load()) {
            ServerCapabilitiesApi.Result.Unsupported -> WorksiteContextResult.Unsupported
            is ServerCapabilitiesApi.Result.Error -> WorksiteContextResult.Error(capabilities.message)
            is ServerCapabilitiesApi.Result.Success -> {
                val supportsContext =
                    capabilities.snapshot.supports("worksite_tree_v1") &&
                        capabilities.snapshot.supports("worksite_permissions_v1")
                if (!supportsContext) {
                    WorksiteContextResult.Unsupported
                } else {
                    when (val modern = loadServerWorksiteContexts(token)) {
                        WorksiteContextResult.Unsupported -> WorksiteContextResult.Error(
                            "Server meldet Worksite-/Berechtigungs-Capabilities, der Server-1.0-Kontextvertrag ist aber nicht erreichbar."
                        )
                        else -> modern
                    }
                }
            }
        }
    }

    private fun loadServerWorksiteContexts(token: String): WorksiteContextResult {
        return try {
            val (meCode, meBody) = getAuthorized(token, "/api/v1/device/me")
            when (meCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return WorksiteContextResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    return WorksiteContextResult.SessionInvalid
                HttpURLConnection.HTTP_OK -> Unit
                else -> return WorksiteContextResult.Error(errorMessage(meBody, meCode))
            }
            val currentUserId = runCatching { JSONObject(meBody) }
                .getOrNull()
                ?.optJSONObject("user")
                ?.optString("id")
                ?.trim()
                .orEmpty()
            if (currentUserId.isBlank()) {
                return WorksiteContextResult.Error("Server lieferte keine Geräteidentität.")
            }

            val (worksitesCode, worksitesBody) = getAuthorized(token, "/api/v1/worksites")
            when (worksitesCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return WorksiteContextResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    return WorksiteContextResult.SessionInvalid
                HttpURLConnection.HTTP_OK -> Unit
                else -> return WorksiteContextResult.Error(errorMessage(worksitesBody, worksitesCode))
            }
            val worksites = runCatching { JSONObject(worksitesBody) }
                .getOrNull()
                ?.optJSONArray("worksites")
                ?: return WorksiteContextResult.Error("Server-Baustellen konnten nicht gelesen werden.")

            val contexts = mutableListOf<WorksiteContext>()
            for (index in 0 until worksites.length()) {
                val worksite = worksites.optJSONObject(index) ?: continue
                val worksiteId = worksite.optString("id").trim()
                if (worksiteId.isBlank()) continue

                val (membersCode, membersBody) = getAuthorized(
                    token,
                    "/api/v1/worksites/${encode(worksiteId)}/members"
                )
                when (membersCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        return WorksiteContextResult.SessionInvalid
                    HttpURLConnection.HTTP_OK -> Unit
                    else -> return WorksiteContextResult.Error(errorMessage(membersBody, membersCode))
                }

                val members = runCatching { JSONObject(membersBody) }
                    .getOrNull()
                    ?.optJSONArray("members")
                    ?: JSONArray()
                var currentMember: JSONObject? = null
                for (memberIndex in 0 until members.length()) {
                    val member = members.optJSONObject(memberIndex) ?: continue
                    if (member.optString("user_id").trim() == currentUserId) {
                        currentMember = member
                        break
                    }
                }

                val rawRole = currentMember?.optString("worksite_role")
                    ?.trim()
                    ?.uppercase()
                    .orEmpty()
                val memberRole = when (rawRole) {
                    "RESPONSIBLE_TB", "TB" -> "LEAD"
                    "HELPER" -> "HELPER"
                    "READ_ONLY" -> "READ_ONLY"
                    else -> rawRole.ifBlank { "READ_ONLY" }
                }
                contexts += WorksiteContext(
                    worksiteId = worksiteId,
                    name = worksite.nullableString("name"),
                    designation = worksite.nullableString("designation"),
                    status = worksite.nullableString("status"),
                    groupId = currentMember?.nullableString("team_id").orEmpty(),
                    memberRole = memberRole,
                    areaScopeJson = currentMember
                        ?.optJSONObject("area_scope")
                        ?.toString()
                        ?: "{}",
                    groupLabel = currentMember?.nullableString("team_name"),
                    leadUserId = currentMember?.nullableString("lead_user_id"),
                    leadNameSnapshot = currentMember?.nullableString("lead_name")
                )
            }
            WorksiteContextResult.Success(contexts)
        } catch (error: Exception) {
            WorksiteContextResult.Error(
                error.message ?: "Server-1.0-Baustellenkontext konnte nicht geladen werden."
            )
        }
    }

    fun loadTeam(token: String, worksiteId: String): TeamResult {
        return when (val capabilities = capabilitiesApi.load()) {
            ServerCapabilitiesApi.Result.Unsupported -> loadLegacyTeam(token, worksiteId)
            is ServerCapabilitiesApi.Result.Error -> TeamResult.Error(capabilities.message)
            is ServerCapabilitiesApi.Result.Success -> {
                if (!capabilities.snapshot.supports("worksite_teams_v1")) {
                    loadLegacyTeam(token, worksiteId)
                } else {
                    when (val modern = loadServerTeam(token, worksiteId)) {
                        TeamResult.Unsupported -> TeamResult.Error(
                            "Server meldet worksite_teams_v1, der Server-1.0-Teamvertrag ist aber nicht erreichbar."
                        )
                        else -> modern
                    }
                }
            }
        }
    }

    private fun loadServerTeam(token: String, worksiteId: String): TeamResult {
        return try {
            val (worksiteCode, worksiteBody) = getAuthorized(
                token,
                "/api/v1/worksites/${encode(worksiteId)}"
            )
            when (worksiteCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return TeamResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> return TeamResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> return TeamResult.Error(
                    detailMessage(worksiteBody).ifBlank { "Kein Zugriff auf die Baustelle." }
                )
                HttpURLConnection.HTTP_OK -> Unit
                else -> return TeamResult.Error(errorMessage(worksiteBody, worksiteCode))
            }
            val worksiteRoot = runCatching { JSONObject(worksiteBody) }.getOrNull()
                ?: return TeamResult.Unsupported
            val worksite = worksiteRoot.optJSONObject("worksite")
                ?: return TeamResult.Unsupported

            val (meCode, meBody) = getAuthorized(token, "/api/v1/device/me")
            when (meCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return TeamResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    return TeamResult.SessionInvalid
                HttpURLConnection.HTTP_OK -> Unit
                else -> return TeamResult.Error(errorMessage(meBody, meCode))
            }
            val currentUserId = runCatching { JSONObject(meBody) }
                .getOrNull()
                ?.optJSONObject("user")
                ?.optString("id")
                ?.trim()
                .orEmpty()
            if (currentUserId.isBlank()) {
                return TeamResult.Error("Server lieferte keine Geräteidentität.")
            }

            val (membersCode, membersBody) = getAuthorized(
                token,
                "/api/v1/worksites/${encode(worksiteId)}/members"
            )
            if (membersCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return TeamResult.SessionInvalid
            }
            if (membersCode != HttpURLConnection.HTTP_OK) {
                return TeamResult.Error(errorMessage(membersBody, membersCode))
            }

            val (teamsCode, teamsBody) = getAuthorized(
                token,
                "/api/v1/worksites/${encode(worksiteId)}/teams"
            )
            if (teamsCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return TeamResult.SessionInvalid
            }
            if (teamsCode != HttpURLConnection.HTTP_OK) {
                return TeamResult.Error(errorMessage(teamsBody, teamsCode))
            }

            val membersArray = runCatching { JSONObject(membersBody) }
                .getOrNull()
                ?.optJSONArray("members")
                ?: JSONArray()
            val teamsArray = runCatching { JSONObject(teamsBody) }
                .getOrNull()
                ?.optJSONArray("teams")
                ?: JSONArray()

            val members = buildList {
                for (index in 0 until membersArray.length()) {
                    val item = membersArray.optJSONObject(index) ?: continue
                    val userId = item.optString("user_id").trim()
                    if (userId.isBlank()) continue
                    add(
                        ModernTeamMember(
                            userId = userId,
                            name = item.nullableString("display_name"),
                            email = item.nullableString("personal_email"),
                            worksiteRole = item.optString("worksite_role").uppercase(),
                            areaScopeJson = item.optJSONObject("area_scope")?.toString() ?: "{}",
                            teamId = item.nullableString("team_id")
                        )
                    )
                }
            }

            val currentMember = members.firstOrNull { it.userId == currentUserId }
            val groups = buildList {
                for (index in 0 until teamsArray.length()) {
                    val item = teamsArray.optJSONObject(index) ?: continue
                    val teamId = item.optString("id").trim()
                    if (teamId.isBlank()) continue
                    val leadUserId = item.nullableString("lead_user_id")
                    val leadName = item.nullableString("lead_name")
                    val teamMembers = members
                        .filter { it.teamId == teamId }
                        .map { member ->
                            TeamMember(
                                userId = member.userId,
                                name = member.name,
                                email = member.email,
                                memberRole = if (member.userId == leadUserId) "LEAD" else "HELPER",
                                areaScopeJson = member.areaScopeJson
                            )
                        }
                    add(
                        TeamGroup(
                            groupId = teamId,
                            label = item.nullableString("name"),
                            leadUserId = leadUserId,
                            leadNameSnapshot = leadName,
                            leadNameOrdinal = 0,
                            leadDisplayName = leadName,
                            members = teamMembers
                        )
                    )
                }
            }

            val currentRole = when (currentMember?.worksiteRole) {
                "RESPONSIBLE_TB", "TB" -> "LEAD"
                "HELPER" -> "HELPER"
                else -> currentMember?.worksiteRole ?: "READ_ONLY"
            }
            TeamResult.Success(
                TeamOverview(
                    worksiteId = worksite.optString("id").ifBlank { worksiteId },
                    worksiteName = worksite.nullableString("name"),
                    worksiteDesignation = worksite.nullableString("designation"),
                    currentGroupId = currentMember?.teamId.orEmpty(),
                    currentMemberRole = currentRole,
                    currentAreaScopeJson = currentMember?.areaScopeJson ?: "{}",
                    groups = groups
                )
            )
        } catch (error: Exception) {
            TeamResult.Error(error.message ?: "Server-1.0-Teamdaten konnten nicht geladen werden.")
        }
    }

    private fun loadLegacyTeam(token: String, worksiteId: String): TeamResult {
        val connection = openConnection(
            "/api/v1/collab/worksites/${encode(worksiteId)}/team",
            "GET"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseTeam(body)?.let(TeamResult::Success)
                    ?: TeamResult.Error("Teamdaten konnten nicht gelesen werden.")
                HttpURLConnection.HTTP_NOT_FOUND -> TeamResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> TeamResult.SessionInvalid
                else -> TeamResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            TeamResult.Error(error.message ?: "Teamdaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    /** Server 1.0: gezielte Kandidaten derselben Firma, die noch kein aktives Baustellenmitglied sind. */
    fun loadServerInvitationCandidates(
        token: String,
        worksiteId: String
    ): InvitationCandidatesResult {
        when (val capabilities = capabilitiesApi.load()) {
            ServerCapabilitiesApi.Result.Unsupported -> return InvitationCandidatesResult.Unsupported
            is ServerCapabilitiesApi.Result.Error ->
                return InvitationCandidatesResult.Error(capabilities.message)
            is ServerCapabilitiesApi.Result.Success -> {
                if (!capabilities.snapshot.supports("worksite_invitations_v1")) {
                    return InvitationCandidatesResult.Unsupported
                }
            }
        }
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/invite-candidates",
            "GET"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val items = root?.optJSONArray("candidates") ?: JSONArray()
                    val candidates = buildList {
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val id = item.optString("id").trim()
                            val name = item.optString("display_name").trim()
                            if (id.isBlank() || name.isBlank()) continue
                            add(
                                InvitationCandidate(
                                    userId = id,
                                    displayName = name,
                                    email = item.nullableString("personal_email"),
                                    phone = item.nullableString("personal_phone"),
                                    companyRole = item.nullableString("company_role")
                                )
                            )
                        }
                    }
                    InvitationCandidatesResult.Success(candidates)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> InvitationCandidatesResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> InvitationCandidatesResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> InvitationCandidatesResult.Error(
                    detailMessage(body).ifBlank { "Keine Berechtigung zum Einladen." }
                )
                else -> InvitationCandidatesResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            InvitationCandidatesResult.Error(
                error.message ?: "Einladungskandidaten konnten nicht geladen werden."
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Server 1.0: gezielte Einladung für einen konkreten Firmenbenutzer.
     * Der Einmal-Token wird nur in dieser ersten Antwort geliefert.
     */
    fun createServerInvitation(
        token: String,
        worksiteId: String,
        invitedUserId: String,
        worksiteRole: String,
        permissionProfile: String,
        areaScopeJson: String = "{}",
        expiresDays: Int = 7
    ): InvitationResult {
        when (val capabilities = capabilitiesApi.load()) {
            ServerCapabilitiesApi.Result.Unsupported -> return InvitationResult.Unsupported
            is ServerCapabilitiesApi.Result.Error -> return InvitationResult.Error(capabilities.message)
            is ServerCapabilitiesApi.Result.Success -> {
                if (!capabilities.snapshot.supports("worksite_invitations_v1")) {
                    return InvitationResult.Unsupported
                }
            }
        }
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/invitations",
            "POST"
        )
        return try {
            authorize(connection, token)
            connection.setRequestProperty(
                "X-Client-Change-Id",
                "android-worksite-invite-create-${UUID.randomUUID()}"
            )
            val request = JSONObject()
                .put("invited_user_id", invitedUserId)
                .put("worksite_role", worksiteRole.uppercase())
                .put("permission_profile", permissionProfile)
                .put("permissions_override", JSONObject())
                .put(
                    "area_scope",
                    runCatching { JSONObject(areaScopeJson) }.getOrElse { JSONObject() }
                )
                .put("expires_days", expiresDays.coerceIn(1, 30))
            writeJson(connection, request)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val id = root?.optString("id").orEmpty()
                    val inviteToken = root?.optString("invite_token").orEmpty()
                    if (id.isBlank()) {
                        InvitationResult.Error("Server lieferte keine Einladungs-ID.")
                    } else if (inviteToken.isBlank()) {
                        InvitationResult.Error(
                            "Einladung wurde verarbeitet, der Einmalcode kann aber nicht erneut angezeigt werden. Bitte Einladung widerrufen und neu erzeugen."
                        )
                    } else {
                        InvitationResult.Success(
                            Invitation(
                                invitationId = id,
                                invitationType = root?.optString("worksite_role").orEmpty()
                                    .ifBlank { worksiteRole.uppercase() },
                                code = inviteToken,
                                deepLink = root?.nullableString("invite_uri"),
                                expiresAt = root?.nullableString("expires_at"),
                                shareLink = root?.nullableString("invite_link")
                            )
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> InvitationResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> InvitationResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> InvitationResult.Error(
                    detailMessage(body).ifBlank { "Keine Berechtigung zum Einladen." }
                )
                else -> InvitationResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            InvitationResult.Error(error.message ?: "Einladung konnte nicht erstellt werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun createInvitation(
        token: String,
        worksiteId: String,
        invitationType: String,
        areaScopeJson: String = "{\"all\":true}",
        expiresDays: Int = 7
    ): InvitationResult {
        val connection = openConnection(
            "/api/v1/collab/worksites/${encode(worksiteId)}/invitations",
            "POST"
        )
        return try {
            authorize(connection, token)
            val request = JSONObject()
                .put("invitation_type", invitationType.uppercase())
                .put("expires_days", expiresDays.coerceIn(1, 30))
            if (invitationType.equals("HELPER", ignoreCase = true)) {
                request.put("area_scope", runCatching { JSONObject(areaScopeJson) }.getOrElse { JSONObject().put("all", true) })
            }
            writeJson(connection, request)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val id = root?.optString("invitation_id").orEmpty()
                    val inviteCode = root?.optString("code").orEmpty()
                    if (id.isBlank() || inviteCode.isBlank()) {
                        InvitationResult.Error("Server lieferte keine vollständige Einladung.")
                    } else {
                        InvitationResult.Success(
                            Invitation(
                                invitationId = id,
                                invitationType = root?.optString("invitation_type").orEmpty().ifBlank { invitationType.uppercase() },
                                code = inviteCode,
                                deepLink = root?.nullableString("deep_link"),
                                expiresAt = root?.nullableString("expires_at")
                            )
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> InvitationResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> InvitationResult.SessionInvalid
                else -> InvitationResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            InvitationResult.Error(error.message ?: "Einladung konnte nicht erstellt werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun redeemInvitation(token: String, codeValue: String): RedeemResult {
        val connection = openConnection("/api/v1/collab/invitations/redeem", "POST")
        return try {
            authorize(connection, token)
            writeJson(connection, JSONObject().put("code", codeValue.trim()))
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val worksiteId = root?.optString("worksite_id").orEmpty()
                    val groupId = root?.optString("group_id").orEmpty()
                    if (worksiteId.isBlank() || groupId.isBlank()) {
                        RedeemResult.Error("Server lieferte keine vollständige Teamzuordnung.")
                    } else {
                        RedeemResult.Success(
                            RedeemedInvitation(
                                worksiteId = worksiteId,
                                worksiteName = root?.nullableString("worksite_name"),
                                groupId = groupId,
                                memberRole = root?.optString("member_role").orEmpty()
                            )
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
                    if (detail.isBlank() || detail.equals("Not Found", ignoreCase = true)) RedeemResult.Unsupported
                    else RedeemResult.Error(detail)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> RedeemResult.SessionInvalid
                else -> RedeemResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            RedeemResult.Error(error.message ?: "Einladung konnte nicht eingelöst werden.")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * TB-Server 1.0: gezielte Baustelleneinladung mit case-sensitivem Einmal-Token.
     * Der Legacy-TBI-Pfad bleibt während der Serverumstellung separat erhalten.
     */
    fun redeemServerInvitation(token: String, invitationToken: String): RedeemResult {
        val rawToken = invitationToken.trim()
        if (rawToken.length !in 24..500) {
            return RedeemResult.Error("Einladungstoken ist ungültig.")
        }
        val connection = openConnection("/api/v1/worksites/invitations/redeem", "POST")
        return try {
            authorize(connection, token)
            connection.setRequestProperty(
                "X-Client-Change-Id",
                "android-worksite-invite-${UUID.randomUUID()}"
            )
            writeJson(connection, JSONObject().put("token", rawToken))
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val worksiteId = root?.optString("worksite_id").orEmpty()
                    val membership = root?.optJSONObject("membership")
                    if (worksiteId.isBlank() || membership == null) {
                        RedeemResult.Error("Server lieferte keine vollständige Baustellenzuordnung.")
                    } else {
                        RedeemResult.Success(
                            RedeemedInvitation(
                                worksiteId = worksiteId,
                                worksiteName = null,
                                groupId = root?.nullableString("team_id").orEmpty(),
                                memberRole = membership.optString("worksite_role")
                                    .ifBlank { "READ_ONLY" }
                            )
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(body)
                    if (detail.isBlank() || detail.equals("Not Found", ignoreCase = true)) {
                        RedeemResult.Unsupported
                    } else {
                        RedeemResult.Error(detail)
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> RedeemResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val detail = detailMessage(body)
                    if (detail.startsWith("WORKSITE_INVITATION_")) {
                        RedeemResult.Error(detail)
                    } else {
                        RedeemResult.SessionInvalid
                    }
                }
                else -> RedeemResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            RedeemResult.Error(error.message ?: "Baustelleneinladung konnte nicht eingelöst werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun loadCompanyProfile(token: String): CompanyProfileResult {
        val connection = openConnection("/api/v1/company/profile", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val profile = runCatching { JSONObject(body).optJSONObject("profile") }.getOrNull()
                    if (profile == null) CompanyProfileResult.Error("Firmenprofil konnte nicht gelesen werden.")
                    else CompanyProfileResult.Success(
                        CompanyProfile(
                            companyId = profile.optString("company_id"),
                            name = profile.nullableString("name"),
                            addressLine1 = profile.nullableString("address_line1"),
                            addressLine2 = profile.nullableString("address_line2"),
                            postalCode = profile.nullableString("postal_code"),
                            city = profile.nullableString("city"),
                            phone = profile.nullableString("phone"),
                            email = profile.nullableString("email"),
                            website = profile.nullableString("website"),
                            hasLogo = profile.optBoolean("has_logo", false),
                            logoMimeType = profile.nullableString("logo_mime_type"),
                            logoSha256 = profile.nullableString("logo_sha256"),
                            logoVersion = profile.optLong("logo_version", 0),
                            profileVersion = profile.optLong("profile_version", 1)
                        )
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> CompanyProfileResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> CompanyProfileResult.SessionInvalid
                else -> CompanyProfileResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            CompanyProfileResult.Error(error.message ?: "Firmenprofil konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun downloadCompanyLogo(token: String): LogoResult {
        val connection = openConnection("/api/v1/company/profile/logo", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> LogoResult.Success(
                    BinaryFile(
                        bytes = connection.inputStream.use { it.readBytes() },
                        mimeType = connection.contentType
                    )
                )
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val body = readBody(connection, code)
                    val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
                    if (detail.startsWith("COMPANY_LOGO_")) LogoResult.NotConfigured else LogoResult.Unsupported
                }
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> LogoResult.SessionInvalid
                else -> LogoResult.Error(errorMessage(readBody(connection, code), code))
            }
        } catch (error: Exception) {
            LogoResult.Error(error.message ?: "Firmenlogo konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTeam(body: String): TeamOverview? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val worksiteId = root.optString("worksite_id")
        val currentGroupId = root.optString("current_group_id")
        if (worksiteId.isBlank() || currentGroupId.isBlank()) return null
        val groupsJson = root.optJSONArray("groups") ?: JSONArray()
        val groups = buildList {
            for (groupIndex in 0 until groupsJson.length()) {
                val group = groupsJson.optJSONObject(groupIndex) ?: continue
                val groupId = group.optString("group_id")
                if (groupId.isBlank()) continue
                val memberJson = group.optJSONArray("members") ?: JSONArray()
                val members = buildList {
                    for (memberIndex in 0 until memberJson.length()) {
                        val member = memberJson.optJSONObject(memberIndex) ?: continue
                        val userId = member.optString("user_id")
                        if (userId.isBlank()) continue
                        add(
                            TeamMember(
                                userId = userId,
                                name = member.nullableString("name"),
                                email = member.nullableString("email"),
                                memberRole = member.optString("member_role").ifBlank { "HELPER" },
                                areaScopeJson = member.jsonText("area_scope") ?: "{}"
                            )
                        )
                    }
                }
                add(
                    TeamGroup(
                        groupId = groupId,
                        label = group.nullableString("label"),
                        leadUserId = group.nullableString("lead_user_id"),
                        leadNameSnapshot = group.nullableString("lead_name_snapshot"),
                        leadNameOrdinal = group.optInt("lead_name_ordinal", 1).coerceAtLeast(1),
                        leadDisplayName = group.nullableString("lead_display_name"),
                        members = members
                    )
                )
            }
        }
        return TeamOverview(
            worksiteId = worksiteId,
            worksiteName = root.nullableString("worksite_name"),
            worksiteDesignation = root.nullableString("worksite_designation"),
            currentGroupId = currentGroupId,
            currentMemberRole = root.optString("current_member_role").ifBlank { "HELPER" },
            currentAreaScopeJson = root.jsonText("current_area_scope") ?: "{}",
            groups = groups
        )
    }

    private fun getAuthorized(token: String, path: String): Pair<Int, String> {
        val connection = openConnection(path, "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            code to readBody(connection, code)
        } finally {
            connection.disconnect()
        }
    }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun writeJson(connection: HttpURLConnection, json: JSONObject) {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append(line)
                        line = reader.readLine()
                    }
                }
            }
        }
    }

    private fun detailMessage(body: String): String {
        val raw = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        return when (raw) {
            is String -> raw.trim()
            is JSONObject -> raw.optString("code").trim().ifBlank { raw.toString() }
            else -> raw?.toString()?.trim().orEmpty()
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        return detailMessage(body).ifBlank { "Serverfehler ($code)." }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.jsonText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()
    }
}
