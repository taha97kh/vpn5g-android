package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.4 §5 server validator (pure Kotlin). */
class Vpn5gServerValidatorTest {

    private val uuid = "3786e67a-41eb-4b92-b31e-417e89493b93"

    private fun profile(
        type: EConfigType = EConfigType.VLESS,
        server: String? = "1.2.3.4",
        port: String? = "443",
        password: String? = uuid,
        method: String? = null,
        flow: String? = null,
        network: String? = "tcp",
        path: String? = null,
        serviceName: String? = null,
        security: String? = null,
        sni: String? = null,
        insecure: Boolean? = null,
        pinnedCA256: String? = null,
        publicKey: String? = null,
        shortId: String? = null,
    ) = ProfileItem(
        configType = type,
        server = server,
        serverPort = port,
        password = password,
        method = method,
        flow = flow,
        network = network,
        path = path,
        serviceName = serviceName,
        security = security,
        sni = sni,
        insecure = insecure,
        pinnedCA256 = pinnedCA256,
        publicKey = publicKey,
        shortId = shortId,
    )

    private fun reason(p: ProfileItem) = Vpn5gServerValidator.validate(p).reason

    @Test
    fun test_valid_vless_tcp() {
        // v1.1.7: a plain VLESS/TCP with no TLS is UNENCRYPTED, not valid —
        // the valid TCP case is VLESS + TLS.
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(security = "tls", sni = "example.com")),
        )
    }

    @Test
    fun test_valid_vmess_ws_tls() {
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    type = EConfigType.VMESS,
                    network = "ws",
                    path = "/speedtest",
                    security = "tls",
                    sni = "example.com",
                )
            ),
        )
    }

    @Test
    fun test_valid_trojan_and_ss() {
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    type = EConfigType.TROJAN,
                    password = "secret",
                    security = "tls",
                    sni = "example.com",
                )
            ),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    type = EConfigType.SHADOWSOCKS,
                    password = "secret",
                    method = "aes-256-gcm",
                )
            ),
        )
    }

    @Test
    fun test_valid_reality_with_key_and_sid() {
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    security = "reality",
                    sni = "example.com",
                    publicKey = "abc123",
                    shortId = "a1b2",
                )
            ),
        )
    }

    @Test
    fun test_valid_grpc_with_service_name() {
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    network = "grpc",
                    serviceName = "gun-service",
                    security = "tls",
                    sni = "example.com",
                )
            ),
        )
    }

    @Test
    fun test_unsupported_scheme() {
        assertEquals(
            Vpn5gServerValidator.Reason.UNSUPPORTED_SCHEME,
            reason(profile(type = EConfigType.SOCKS)),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.UNSUPPORTED_SCHEME,
            reason(profile(type = EConfigType.WIREGUARD)),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.UNSUPPORTED_SCHEME,
            reason(profile(type = EConfigType.HTTP)),
        )
    }

    @Test
    fun test_empty_host() {
        assertEquals(
            Vpn5gServerValidator.Reason.EMPTY_HOST,
            reason(profile(server = "")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.EMPTY_HOST,
            reason(profile(server = null)),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.EMPTY_HOST,
            reason(profile(server = "   ")),
        )
    }

    @Test
    fun test_bad_port() {
        assertEquals(Vpn5gServerValidator.Reason.BAD_PORT, reason(profile(port = "0")))
        assertEquals(Vpn5gServerValidator.Reason.BAD_PORT, reason(profile(port = "65536")))
        assertEquals(Vpn5gServerValidator.Reason.BAD_PORT, reason(profile(port = "abc")))
        assertEquals(Vpn5gServerValidator.Reason.BAD_PORT, reason(profile(port = null)))
        assertEquals(Vpn5gServerValidator.Reason.BAD_PORT, reason(profile(port = "-1")))
    }

    @Test
    fun test_ipv6_literals() {
        // Well-formed literals pass (v1.1.7: with TLS, since a plain
        // VLESS with no transport encryption is now UNENCRYPTED).
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "2001:db8::1", security = "tls", sni = "example.com")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "::1", security = "tls", sni = "example.com")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "::", security = "tls", sni = "example.com")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "fe80::1%eth0", security = "tls", sni = "example.com")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "::ffff:192.0.2.1", security = "tls", sni = "example.com")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "[2001:db8::1]", security = "tls", sni = "example.com")),
        )
        // Malformed literals fail.
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_IPV6,
            reason(profile(server = "2001:db8:::1")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_IPV6,
            reason(profile(server = "gggg::1")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_IPV6,
            reason(profile(server = "2001:db8::1::2")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_IPV6,
            reason(profile(server = "2001:db8:1:2:3:4:5:6:7")),
        )
    }

    @Test
    fun test_bad_uuid() {
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_UUID,
            reason(profile(password = "not-a-uuid")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_UUID,
            reason(profile(password = "")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.BAD_UUID,
            reason(profile(type = EConfigType.VMESS, password = "Vip_fragment")),
        )
    }

    @Test
    fun test_empty_password_trojan_ss() {
        assertEquals(
            Vpn5gServerValidator.Reason.EMPTY_PASSWORD,
            reason(profile(type = EConfigType.TROJAN, password = "")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.EMPTY_PASSWORD,
            reason(profile(type = EConfigType.SHADOWSOCKS, password = null)),
        )
    }

    @Test
    fun test_tls_requires_sni() {
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_SNI,
            reason(profile(security = "tls", sni = null)),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_SNI,
            reason(profile(security = "tls", sni = "  ")),
        )
    }

    @Test
    fun test_reality_requires_key_and_sid() {
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_REALITY_KEY,
            reason(profile(security = "reality", publicKey = null, shortId = "a1")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_REALITY_KEY,
            reason(profile(security = "reality", publicKey = "k", shortId = null)),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_REALITY_KEY,
            reason(profile(security = "reality", publicKey = "k", shortId = "")),
        )
    }

    @Test
    fun test_grpc_requires_service_name() {
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_GRPC_SERVICE,
            reason(profile(network = "grpc", serviceName = null)),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.MISSING_GRPC_SERVICE,
            reason(profile(network = "grpc", serviceName = "")),
        )
    }

    @Test
    fun test_normalize_ws_path_defaults_to_slash() {
        val ws = profile(network = "ws", path = null)
        Vpn5gServerValidator.normalize(ws)
        assertEquals("/", ws.path)

        val xhttp = profile(network = "xhttp", path = "")
        Vpn5gServerValidator.normalize(xhttp)
        assertEquals("/", xhttp.path)

        val kept = profile(network = "ws", path = "/speedtest")
        Vpn5gServerValidator.normalize(kept)
        assertEquals("/speedtest", kept.path)

        val tcp = profile(network = "tcp", path = null)
        Vpn5gServerValidator.normalize(tcp)
        assertEquals(null, tcp.path)
    }

    @Test
    fun test_dedupe_key_identity() {
        val a = profile(server = "Example.COM", port = "443")
        val b = profile(server = "example.com", port = "443")
        assertEquals(
            Vpn5gServerValidator.dedupeKey(a),
            Vpn5gServerValidator.dedupeKey(b),
        )
        val otherPort = profile(server = "example.com", port = "8443")
        assertNotEquals(
            Vpn5gServerValidator.dedupeKey(a),
            Vpn5gServerValidator.dedupeKey(otherPort),
        )
        val otherUuid = profile(server = "example.com", password = "11111111-2222-3333-4444-555555555555")
        assertNotEquals(
            Vpn5gServerValidator.dedupeKey(a),
            Vpn5gServerValidator.dedupeKey(otherUuid),
        )
    }

    @Test
    fun test_is_uuid() {
        assertTrue(Vpn5gServerValidator.isUuid(uuid))
        assertTrue(Vpn5gServerValidator.isUuid(uuid.uppercase()))
        assertFalse(Vpn5gServerValidator.isUuid("not-a-uuid"))
        assertFalse(Vpn5gServerValidator.isUuid(""))
    }

    // -- v1.1.7 encrypted-only filter ----------------------------------------

    @Test
    fun test_unencrypted_vless_no_tls() {
        // Plain VLESS with no TLS/REALITY and encryption "none" is the
        // node the red dialog warns about.
        assertEquals(
            Vpn5gServerValidator.Reason.UNENCRYPTED,
            reason(profile(method = "none")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.UNENCRYPTED,
            reason(profile()),
        )
    }

    @Test
    fun test_vless_pq_encryption_accepted() {
        // A post-quantum `encryption` value encrypts without TLS
        // (the core's bypass for the VLESS-no-TLS prohibition).
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(method = "mlkem768x25519")),
        )
    }

    @Test
    fun test_unencrypted_vmess_scy_none() {
        // VMess scy "none" with no TLS/REALITY is plaintext.
        assertEquals(
            Vpn5gServerValidator.Reason.UNENCRYPTED,
            reason(profile(type = EConfigType.VMESS, method = "none")),
        )
        // With TLS the transport encrypts even when scy is "none".
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    type = EConfigType.VMESS,
                    method = "none",
                    security = "tls",
                    sni = "example.com",
                )
            ),
        )
        // VMess AEAD (scy "auto") without TLS is encrypted by itself.
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(type = EConfigType.VMESS, method = "auto")),
        )
    }

    @Test
    fun test_unencrypted_trojan_no_tls() {
        assertEquals(
            Vpn5gServerValidator.Reason.UNENCRYPTED,
            reason(
                profile(
                    type = EConfigType.TROJAN,
                    password = "secret",
                    security = "none",
                )
            ),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    type = EConfigType.TROJAN,
                    password = "secret",
                    security = "reality",
                    publicKey = "abc123",
                    shortId = "a1b2",
                )
            ),
        )
    }

    @Test
    fun test_trojan_flow_rejected() {
        assertEquals(
            Vpn5gServerValidator.Reason.TROJAN_FLOW,
            reason(
                profile(
                    type = EConfigType.TROJAN,
                    password = "secret",
                    security = "tls",
                    sni = "example.com",
                    flow = "xtls-rprx-vision",
                )
            ),
        )
    }

    @Test
    fun test_insecure_tls_rejected() {
        // insecure=1 with no pinned CA is exactly the red-dialog node.
        assertEquals(
            Vpn5gServerValidator.Reason.INSECURE_TLS,
            reason(
                profile(
                    security = "tls",
                    sni = "example.com",
                    insecure = true,
                )
            ),
        )
        // A pinned CA (`pcs`) is the core's replacement: accepted.
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(
                profile(
                    security = "tls",
                    sni = "example.com",
                    insecure = true,
                    pinnedCA256 = "abc123",
                )
            ),
        )
    }

    @Test
    fun test_weak_ss_ciphers() {
        assertEquals(
            Vpn5gServerValidator.Reason.WEAK_SS_CIPHER,
            reason(profile(type = EConfigType.SHADOWSOCKS, password = "secret")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.WEAK_SS_CIPHER,
            reason(
                profile(
                    type = EConfigType.SHADOWSOCKS,
                    password = "secret",
                    method = "aes-256-cfb",
                )
            ),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.WEAK_SS_CIPHER,
            reason(
                profile(
                    type = EConfigType.SHADOWSOCKS,
                    password = "secret",
                    method = "rc4-md5",
                )
            ),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.WEAK_SS_CIPHER,
            reason(
                profile(
                    type = EConfigType.SHADOWSOCKS,
                    password = "secret",
                    method = "none",
                )
            ),
        )
    }

    @Test
    fun test_aead_ss_ciphers_accepted() {
        listOf(
            "aes-128-gcm",
            "aes-256-gcm",
            "chacha20-poly1305",
            "chacha20-ietf-poly1305",
            "xchacha20-poly1305",
            "2022-blake3-aes-128-gcm",
            "2022-blake3-aes-256-gcm",
            "2022-blake3-chacha20-poly1305",
        ).forEach { cipher ->
            assertEquals(
                Vpn5gServerValidator.Reason.OK,
                reason(
                    profile(
                        type = EConfigType.SHADOWSOCKS,
                        password = "secret",
                        method = cipher,
                    )
                ),
                "cipher $cipher",
            )
        }
        assertTrue(Vpn5gServerValidator.isAeadCipher("AES-256-GCM"))
        assertFalse(Vpn5gServerValidator.isAeadCipher("aes-256-cfb"))
        assertFalse(Vpn5gServerValidator.isAeadCipher(null))
    }
}
