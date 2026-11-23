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
 * Encrypted-only filter (v1.1.7): the bundled Xray core (v26.11.9)
 * refuses unencrypted nodes — a config with `allowInsecure` fails to load
 * (`The feature "allowInsecure" has been removed`, core >= 26.2.6) and
 * VLESS without TLS/REALITY/post-quantum encryption is rejected outright
 * (core >= 26.7.11). Upstream v2rayNG shows the red "unencrypted node"
 * toast for `insecure == true` with no pinned CA (see
 * LauncherManager.startContextService) and the service then fails to
 * start. So a config that is not provably encrypted never reaches the
 * core: it is dropped at sync/import and skipped at connect time.
 * - `insecure=1` without a pinned CA (`pcs`) is rejected;
 * - VLESS needs TLS, REALITY, or a non-"none" post-quantum `encryption`;
 * - VMess needs a real AEAD cipher (scy "auto"/"aes-128-gcm"/...) — only
 *   scy "none" without TLS/REALITY is rejected (with TLS the transport
 *   encrypts even when scy is "none");
 * - Trojan needs TLS or REALITY and an empty `flow`;
 * - Shadowsocks needs an AEAD cipher.
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
        /**
         * v1.1.7: TLS with certificate verification skipped and no pinned CA
         * (`insecure=1` without `pcs`). This is exactly the node the red
         * "unencrypted" dialog warns about: the core refuses to load such
         * a config (`allowInsecure` removed in Xray-core 26.2.6).
         */
        INSECURE_TLS,
        /**
         * v1.1.7: no transport encryption — VLESS without TLS/REALITY or a
         * post-quantum `encryption` value, VMess with scy "none" and no
         * TLS/REALITY, or Trojan without TLS/REALITY.
         */
        UNENCRYPTED,
        /**
         * v1.1.7: Shadowsocks cipher is not AEAD (none / plain / stream
         * cipher such as aes-*-cfb or rc4-md5).
         */
        WEAK_SS_CIPHER,
        /** v1.1.7: Trojan with a non-empty `flow` (a hard core error). */
        TROJAN_FLOW,
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
        // v1.1.7 encrypted-only filter (see the class KDoc): a config that
        // is not provably encrypted never reaches the core, so the red
        // "unencrypted node" dialog can never appear. These checks run
        // after the structural ones so existing reasons are unchanged.
        if (profile.insecure == true && profile.pinnedCA256.isNullOrBlank()) {
            return Validation(Reason.INSECURE_TLS)
        }
        val hasTlsTransport = profile.security == AppConfig.TLS ||
            profile.security == AppConfig.REALITY
        when (profile.configType) {
            EConfigType.VLESS -> {
                // VLESS encrypts via TLS, REALITY, or a post-quantum
                // `encryption` value (stored in profile.method); "none"
                // with no TLS/REALITY is plaintext and the core rejects it.
                val encryption = profile.method.orEmpty()
                val hasPqEncryption = encryption.isNotBlank() &&
                    !encryption.equals("none", ignoreCase = true)
                if (!hasTlsTransport && !hasPqEncryption) {
                    return Validation(Reason.UNENCRYPTED)
                }
            }
            EConfigType.VMESS -> {
                // VMess AEAD (scy "auto"/"aes-128-gcm"/"chacha20-...") is
                // encryption by itself; only scy "none" without a TLS or
                // REALITY transport is plaintext.
                val scyNone = profile.method.orEmpty().equals("none", ignoreCase = true)
                if (scyNone && !hasTlsTransport) {
                    return Validation(Reason.UNENCRYPTED)
                }
            }
            EConfigType.TROJAN -> {
                // Trojan with a `flow` is a hard core error; a Trojan
                // without TLS/REALITY has no encryption at all.
                if (!profile.flow.isNullOrBlank()) {
                    return Validation(Reason.TROJAN_FLOW)
                }
                if (!hasTlsTransport) {
                    return Validation(Reason.UNENCRYPTED)
                }
            }
            EConfigType.SHADOWSOCKS -> {
                // Only AEAD ciphers are real encryption; stream ciphers
                // (aes-*-cfb, rc4-md5) and "none"/plain are rejected.
                if (!isAeadCipher(profile.method)) {
                    return Validation(Reason.WEAK_SS_CIPHER)
                }
            }
            else -> {
                // Other types were already rejected as UNSUPPORTED_SCHEME.
            }
        }
        return Validation(Reason.OK)
    }

    /** Shadowsocks ciphers that are real AEAD encryption (v1.1.7). */
    private val AEAD_CIPHERS = setOf(
        "aes-128-gcm",
        "aes-256-gcm",
        "chacha20-poly1305",
        "chacha20-ietf-poly1305",
        "xchacha20-poly1305",
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
    )

    /** True when [method] is a Shadowsocks AEAD cipher (case-insensitive). */
    fun isAeadCipher(method: String?): Boolean =
        method?.trim()?.lowercase() in AEAD_CIPHERS

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
