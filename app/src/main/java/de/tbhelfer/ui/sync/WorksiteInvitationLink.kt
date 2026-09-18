package de.tbhelfer.ui.sync

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parser für Baustelleneinladungen während der Server-1.0-Umstellung.
 *
 * Unterstützt:
 * - Server 1.0: tbhelfer://worksite-invite?token=<case-sensitive token>
 * - Server 1.0 Browser-Link: /portal/v1/invitations/redeem#token=<token>
 * - manueller Server-1.0-Token
 * - Legacy 0.7: TBI-* bzw. tbhelfer://invite?code=TBI-*
 *
 * Moderne Tokens sind case-sensitive und werden ausdrücklich niemals uppercased.
 */
object WorksiteInvitationLink {
    sealed interface Credential {
        val value: String

        data class ServerToken(override val value: String) : Credential
        data class LegacyCode(override val value: String) : Credential
    }

    private const val SERVER_SCHEME = "tbhelfer"
    private const val SERVER_HOST = "worksite-invite"
    private const val LEGACY_HOST = "invite"
    private const val BROWSER_REDEEM_PATH = "/portal/v1/invitations/redeem"

    fun parse(raw: String): Credential? {
        val text = raw.trim()
        if (text.isBlank()) return null

        if (text.startsWith("TBI-", ignoreCase = true)) {
            return Credential.LegacyCode(text.uppercase())
        }
        if (looksLikeRawServerToken(text)) {
            return Credential.ServerToken(text)
        }

        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()

        if (scheme == SERVER_SCHEME && uri.host.equals(SERVER_HOST, ignoreCase = true)) {
            return queryValue(uri.rawQuery, "token")
                ?.takeIf(::looksLikeRawServerToken)
                ?.let { Credential.ServerToken(it) }
        }

        if (scheme == SERVER_SCHEME && uri.host.equals(LEGACY_HOST, ignoreCase = true)) {
            return queryValue(uri.rawQuery, "code")
                ?.takeIf { it.startsWith("TBI-", ignoreCase = true) }
                ?.uppercase()
                ?.let { Credential.LegacyCode(it) }
        }

        if (
            (scheme == "https" || scheme == "http") &&
            uri.path?.trimEnd('/') == BROWSER_REDEEM_PATH
        ) {
            return queryValue(uri.rawFragment, "token")
                ?.takeIf(::looksLikeRawServerToken)
                ?.let(Credential::ServerToken)
        }

        return null
    }

    private fun queryValue(raw: String?, key: String): String? {
        if (raw.isNullOrBlank()) return null
        return raw.split('&')
            .asSequence()
            .mapNotNull { part ->
                val index = part.indexOf('=')
                val rawKey = if (index >= 0) part.substring(0, index) else part
                if (!decode(rawKey).equals(key, ignoreCase = true)) return@mapNotNull null
                val rawValue = if (index >= 0) part.substring(index + 1) else ""
                decode(rawValue).trim().takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    private fun decode(value: String): String =
        runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
        }.getOrDefault(value)

    private fun looksLikeRawServerToken(value: String): Boolean {
        if (value.length !in 24..500) return false
        if (value.startsWith("TBH-", ignoreCase = true)) return false
        if (value.any(Char::isWhitespace)) return false
        return value.all { ch ->
            ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~'
        }
    }
}
