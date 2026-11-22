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
        network: String? = "tcp",
        path: String? = null,
        serviceName: String? = null,
        security: String? = null,
        sni: String? = null,
        publicKey: String? = null,
        shortId: String? = null,
    ) = ProfileItem(
        configType = type,
        server = server,
        serverPort = port,
        password = password,
        network = network,
        path = path,
        serviceName = serviceName,
        security = security,
        sni = sni,
        publicKey = publicKey,
        shortId = shortId,
    )

    private fun reason(p: ProfileItem) = Vpn5gServerValidator.validate(p).reason

    @Test
    fun test_valid_vless_tcp() {
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile()),
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
            reason(profile(type = EConfigType.SHADOWSOCKS, password = "secret")),
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
            reason(profile(network = "grpc", serviceName = "gun-service")),
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
        // Well-formed literals pass.
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "2001:db8::1")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "::1")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "::")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "fe80::1%eth0")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "::ffff:192.0.2.1")),
        )
        assertEquals(
            Vpn5gServerValidator.Reason.OK,
            reason(profile(server = "[2001:db8::1]")),
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
}
