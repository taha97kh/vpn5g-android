package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import java.util.UUID

/**
 * Pure server validation for the VPN5G config pipeline (v1.1.4 §5).
 *
 * Runs on every config that enters the app — the live sync sources and the
 * bundled fallback — before ping or display. A config that fails validation
 * is dropped silently (only the reason count is logged, never the URI).
 *
 * Rules (spec §5):
 * - only VLESS / VMess / Trojan / Shadowsocks are supported;
 * - the host must be non-empty, and an IPv6 literal must be well-formed;
 * - the port must be in 1..65535;
 * - VLESS / VMess passwords must be UUIDs; Trojan / Shadowsocks passwords
 *   must be non-empty;
 * - TLS requires an SNI; REALITY requires a public key and a short id;
 * - gRPC requires a service name;
 * - a WebSocket / XHTTP path defaults to "/" when missing.
 *
 * Everything here is pure Kotlin (no Android APIs, no I/O) so it is covered
 * by JVM unit tests in Vpn5gServerValidatorTest.
 */
object Vpn5gServerValidator {

    enum class Reason {
        OK,
        UNSUPPORTED_SCHEME,
        EMPTY_HOST,
        BAD_PORT,
        BAD_IPV6,
        BAD_UUID,
        EMPTY_PASSWORD,
        MISSING_SNI,
        MISSING_REALITY_KEY,
        MISSING_GRPC_SERVICE,
    }

    data class Validation(val reason: Reason) {
        val isValid: Boolean get() = reason == Reason.OK
    }

    private val SUPPORTED = setOf(
        EConfigType.VLESS,
        EConfigType.VMESS,
        EConfigType.TROJAN,
        EConfigType.SHADOWSOCKS,
    )

    fun validate(profile: ProfileItem): Validation {
        if (profile.configType !in SUPPORTED) return Validation(Reason.UNSUPPORTED_SCHEME)
        val host = profile.server?.trim().orEmpty()
        if (host.isEmpty()) return Validation(Reason.EMPTY_HOST)
        val port = profile.serverPort?.toIntOrNull()
        if (port == null || port !in 1..65535) return Validation(Reason.BAD_PORT)
        if (host.contains(':') && !isValidIpv6Literal(host)) return Validation(Reason.BAD_IPV6)
        when (profile.configType) {
            EConfigType.VLESS, EConfigType.VMESS -> {
                val uuid = profile.password
                if (uuid.isNullOrBlank() || !isUuid(uuid)) return Validation(Reason.BAD_UUID)
            }
            EConfigType.TROJAN, EConfigType.SHADOWSOCKS -> {
                if (profile.password.isNullOrBlank()) return Validation(Reason.EMPTY_PASSWORD)
            }
            else -> return Validation(Reason.UNSUPPORTED_SCHEME)
        }
        when (profile.security) {
            AppConfig.TLS -> {
                if (profile.sni.isNullOrBlank()) return Validation(Reason.MISSING_SNI)
            }
            AppConfig.REALITY -> {
                if (profile.publicKey.isNullOrBlank() || profile.shortId.isNullOrBlank()) {
                    return Validation(Reason.MISSING_REALITY_KEY)
                }
            }
        }
        if (profile.network == "grpc" && profile.serviceName.isNullOrBlank()) {
            return Validation(Reason.MISSING_GRPC_SERVICE)
        }
        return Validation(Reason.OK)
    }

    /**
     * Fill in defaults that are safe to infer. Only the WebSocket / XHTTP
     * path is defaulted ("/"); everything else is a hard validation error
     * and is never guessed.
     */
    fun normalize(profile: ProfileItem) {
        if ((profile.network == "ws" || profile.network == "xhttp") &&
            profile.path.isNullOrBlank()
        ) {
            profile.path = "/"
        }
    }

    /**
     * Stable identity for dedupe: scheme://host:port:identity. The identity
     * is the UUID (VLESS/VMess) or password (Trojan/Shadowsocks). This key is
     * only ever used as an in-memory map key — it is never logged.
     */
    fun dedupeKey(profile: ProfileItem): String {
        val scheme = profile.configType.name.lowercase()
        val host = profile.server?.trim().orEmpty().lowercase()
        val port = profile.serverPort?.trim().orEmpty()
        val identity = profile.password.orEmpty()
        return "$scheme://$host:$port:$identity"
    }

    fun isUuid(value: String): Boolean {
        return try {
            UUID.fromString(value.trim())
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /**
     * Validate an IPv6 literal (RFC 4291), with optional brackets and an
     * optional "%zone" suffix, and an optional embedded IPv4 tail.
     * Accepts "::", "::1", "2001:db8::1", "fe80::1%eth0",
     * "::ffff:192.0.2.1". Rejects ":::" , "2001:db8:::1", "gggg::1",
     * "2001:db8::1::2", and a bare host name containing a colon.
     */
    fun isValidIpv6Literal(host: String): Boolean {
        var s = host.trim().removePrefix("[").removeSuffix("]")
        val zoneIndex = s.indexOf('%')
        if (zoneIndex >= 0) s = s.substring(0, zoneIndex)
        if (s.isEmpty()) return false
        if (s.contains("::")) {
            // "::" may appear at most once.
            if (s.indexOf("::") != s.lastIndexOf("::")) return false
            val parts = s.split("::", limit = 2)
            val left = parts[0].split(":").filter { it.isNotEmpty() }
            val right = parts[1].split(":").filter { it.isNotEmpty() }
            val leftGroups = countGroups(left) ?: return false
            val rightGroups = countGroups(right) ?: return false
            // "::" must compress at least one group, so the explicit groups
            // (an embedded IPv4 tail counts as two) total at most 7.
            return leftGroups + rightGroups <= 7
        }
        val groups = s.split(":")
        val count = countGroups(groups) ?: return false
        return count == 8
    }

    /**
     * Count the 16-bit groups in a colon-split list. A token is one group
     * when it is 1-4 hex digits, or two groups when it is a valid IPv4
     * dotted quad (only allowed as the last token). Returns null when any
     * token is invalid.
     */
    private fun countGroups(tokens: List<String>): Int? {
        var count = 0
        for ((index, token) in tokens.withIndex()) {
            if (index == tokens.lastIndex && isValidIpv4(token)) {
                count += 2
            } else if (token.length in 1..4 && token.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                count += 1
            } else {
                return null
            }
        }
        return count
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
