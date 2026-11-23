package com.v2ray.ang.handler.vpn5g

import java.net.URLDecoder

/**
 * Password-recovery deep link (v1.1.8).
 *
 * Supabase `resetPasswordForEmail` sends a LINK. With the redirect URL
 * `vpn5g://auth/callback` allow-listed in the dashboard (Authentication
 * → URL Configuration → Redirect URLs), GoTrue redirects the browser to
 * a URL of this shape after the user taps the link:
 *
 *   vpn5g://auth/callback#access_token=<jwt>&refresh_token=<jwt>
 *       &expires_in=3600&token_type=bearer&type=recovery
 *
 * The tokens live in the FRAGMENT (after `#`), never in the query — a
 * plain query parser finds nothing. The `access_token` is already a
 * live session token: after the new password is set with
 * `PUT /auth/v1/user` (Authorization: Bearer <access_token>), the app
 * persists a session built from these exact tokens.
 *
 * Pure Kotlin, no Android APIs, covered by JVM unit tests
 * ([Vpn5gRecoveryLinkTest]). Never logs the tokens.
 */
object Vpn5gRecoveryLink {

    /** Parsed recovery callback. Tokens are never logged. */
    data class Parsed(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long?,
    )

    /**
     * Parses a recovery deep link. Returns null unless the URI is
     * `vpn5g://auth/callback`, its fragment carries `type=recovery`,
     * and a non-blank `access_token` is present. A link of another
     * type (e.g. `type=signup`) or a config deep link is not a
     * recovery link and is rejected here, never treated as one.
     */
    fun parse(uri: String): Parsed? {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return null
        val schemeSplit = trimmed.split("://", limit = 2)
        if (schemeSplit.size != 2) return null
        if (!schemeSplit[0].equals("vpn5g", ignoreCase = true)) return null
        val afterScheme = schemeSplit[1]
        val host = afterScheme.substringBefore('/').substringBefore('#').substringBefore('?')
        if (!host.equals("auth", ignoreCase = true)) return null
        val fragment = trimmed.substringAfter('#', "")
        if (fragment.isBlank()) return null
        val params = parseParams(fragment)
        if (!params["type"].equals("recovery", ignoreCase = true)) return null
        val accessToken = params["access_token"].orEmpty()
        if (accessToken.isBlank()) return null
        return Parsed(
            accessToken = accessToken,
            refreshToken = params["refresh_token"],
            expiresInSeconds = params["expires_in"]?.toLongOrNull(),
        )
    }

    private fun parseParams(fragment: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (pair in fragment.split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val key = decode(pair.substring(0, eq))
            val value = decode(pair.substring(eq + 1))
            if (key.isNotBlank()) out[key] = value
        }
        return out
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }
}
