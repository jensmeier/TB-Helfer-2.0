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

/**
 * Selbstverwaltete Baustellen-Teams ab Server-Schema 10.
 *
 * Identitäten werden ausschließlich über Server-UUIDs behandelt. Sichtbare Namen dürfen
 * doppelt vorkommen; der Server liefert dafür bei Bedarf einen stabilen Anzeigenamens-Zusatz.
 */
class TeamManagementApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
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

    data class Invitation(
        val invitationId: String,
        val invitationType: String,
        val code: String,
        val deepLink: String?,
        val expiresAt: String?
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

    fun loadTeam(token: String, worksiteId: String): TeamResult {
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

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
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
