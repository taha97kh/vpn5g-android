package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.27 network profile: Wi-Fi MTU 1400 and the
 * QUIC (UDP 443) fast-reject ruleset transform. Pure Kotlin only —
 * no MMKV, no LogUtil, no Android calls.
 */
class Vpn5gNetworkProfileTest {

    private fun rule(
        outboundTag: String,
        network: String? = null,
        port: String? = null,
        enabled: Boolean = true,
        remarks: String = "",
    ): RulesetItem = RulesetItem(
        remarks = remarks,
        outboundTag = outboundTag,
        network = network,
        port = port,
        enabled = enabled,
    )

    // -- MTU --------------------------------------------------------------

    @Test
    fun test_mtu_wifi_is_1400() {
        assertEquals(1400, Vpn5gNetworkProfile.profileFor(Vpn5gNetworkProfile.Transport.WIFI).mtu)
        assertEquals(1400, Vpn5gNetworkProfile.MTU_WIFI)
    }

    @Test
    fun test_mtu_cellular_and_fallback_stay_1400() {
        assertEquals(1400, Vpn5gNetworkProfile.profileFor(Vpn5gNetworkProfile.Transport.CELLULAR).mtu)
        assertEquals(1400, Vpn5gNetworkProfile.profileFor(Vpn5gNetworkProfile.Transport.OTHER).mtu)
    }

    // -- Rule classification ----------------------------------------------

    @Test
    fun test_is_forced_quic_block() {
        assertTrue(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_BLOCKED, "udp", "443")))
        assertTrue(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_BLOCKED, "UDP", " 443 ")))
        // Disabled: user turned it off deliberately — not a forced block.
        assertFalse(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_BLOCKED, "udp", "443", enabled = false)))
        // A range that merely contains 443 is a user rule.
        assertFalse(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_BLOCKED, "udp", "443-444")))
        // TCP 443 is not QUIC.
        assertFalse(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_BLOCKED, "tcp", "443")))
        // Other outbound tags are not the block.
        assertFalse(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_PROXY, "udp", "443")))
        assertFalse(Vpn5gNetworkProfile.isForcedQuicBlock(rule(AppConfig.TAG_REJECT, "udp", "443")))
    }

    @Test
    fun test_is_quic_reject_rule() {
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(rule(AppConfig.TAG_REJECT, "udp", "443")))
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(Vpn5gNetworkProfile.quicRejectRule()))
        assertFalse(Vpn5gNetworkProfile.isQuicRejectRule(rule(AppConfig.TAG_REJECT, "udp", "443", enabled = false)))
        assertFalse(Vpn5gNetworkProfile.isQuicRejectRule(rule(AppConfig.TAG_REJECT, "udp", "53")))
        assertFalse(Vpn5gNetworkProfile.isQuicRejectRule(rule(AppConfig.TAG_REJECT, "tcp", "443")))
        assertFalse(Vpn5gNetworkProfile.isQuicRejectRule(rule(AppConfig.TAG_BLOCKED, "udp", "443")))
    }

    @Test
    fun test_quic_reject_rule_shape() {
        val reject = Vpn5gNetworkProfile.quicRejectRule()
        assertEquals(AppConfig.TAG_REJECT, reject.outboundTag)
        assertEquals("udp", reject.network)
        assertEquals("443", reject.port)
        assertTrue(reject.enabled)
        assertTrue(reject.ip.isNullOrEmpty())
        assertTrue(reject.domain.isNullOrEmpty())
    }

    // -- Ruleset transform --------------------------------------------------

    @Test
    fun test_with_quic_fast_reject_inserts_reject_first() {
        val google = rule(AppConfig.TAG_PROXY, remarks = "google")
        val priv = rule(AppConfig.TAG_DIRECT, remarks = "private")
        val result = Vpn5gNetworkProfile.withQuicFastReject(listOf(google, priv))

        assertEquals(3, result.size)
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(result[0]))
        // Existing rules keep their relative order below the reject rule.
        assertEquals(google, result[1])
        assertEquals(priv, result[2])
    }

    @Test
    fun test_with_quic_fast_reject_replaces_block_443() {
        // The seeded WHITE preset shape: block-udp443 first, then the rest.
        val block = rule(AppConfig.TAG_BLOCKED, "udp", "443", remarks = "block-udp443")
        val google = rule(AppConfig.TAG_PROXY, remarks = "google")
        val result = Vpn5gNetworkProfile.withQuicFastReject(listOf(block, google))

        assertEquals(2, result.size)
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(result[0]))
        assertEquals(google, result[1])
        assertTrue(result.none { Vpn5gNetworkProfile.isForcedQuicBlock(it) })
    }

    @Test
    fun test_with_quic_fast_reject_is_idempotent() {
        val rules = listOf(
            rule(AppConfig.TAG_BLOCKED, "udp", "443"),
            rule(AppConfig.TAG_PROXY, remarks = "google"),
            rule(AppConfig.TAG_DIRECT, remarks = "private"),
        )
        val once = Vpn5gNetworkProfile.withQuicFastReject(rules)
        val twice = Vpn5gNetworkProfile.withQuicFastReject(once)
        assertEquals(once, twice)
        assertEquals(1, twice.count { Vpn5gNetworkProfile.isQuicRejectRule(it) })
    }

    @Test
    fun test_with_quic_fast_reject_keeps_other_udp_and_tcp_rules() {
        val dns = rule(AppConfig.TAG_PROXY, "udp", "53", remarks = "dns")
        val tcp443 = rule(AppConfig.TAG_PROXY, "tcp", "443", remarks = "https")
        val voice = rule(AppConfig.TAG_DIRECT, "udp", "3478", remarks = "voice")
        val result = Vpn5gNetworkProfile.withQuicFastReject(listOf(dns, tcp443, voice))

        assertEquals(4, result.size)
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(result[0]))
        assertEquals(dns, result[1])
        assertEquals(tcp443, result[2])
        assertEquals(voice, result[3])
    }

    @Test
    fun test_with_quic_fast_reject_wins_over_user_proxy_udp443() {
        // A user rule proxying UDP 443 is kept (not deleted), but the
        // reject rule sits ahead of it, so QUIC still fast-rejects.
        val userProxy = rule(AppConfig.TAG_PROXY, "udp", "443", remarks = "user-quic")
        val result = Vpn5gNetworkProfile.withQuicFastReject(listOf(userProxy))

        assertEquals(2, result.size)
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(result[0]))
        assertEquals(userProxy, result[1])
    }

    @Test
    fun test_with_quic_fast_reject_keeps_disabled_rules() {
        val disabledBlock = rule(AppConfig.TAG_BLOCKED, "udp", "443", enabled = false, remarks = "off")
        val result = Vpn5gNetworkProfile.withQuicFastReject(listOf(disabledBlock))

        assertEquals(2, result.size)
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(result[0]))
        assertEquals(disabledBlock, result[1])
    }

    @Test
    fun test_with_quic_fast_reject_empty_ruleset() {
        val result = Vpn5gNetworkProfile.withQuicFastReject(emptyList())
        assertEquals(1, result.size)
        assertTrue(Vpn5gNetworkProfile.isQuicRejectRule(result[0]))
    }
}
