package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * JVM tests for the pure referral helpers ([Vpn5gReferral], v1.1.30):
 * code shape, link building and incoming-link parsing. MMKV storage
 * (ownCode / pending referrer) is not exercised here.
 */
class Vpn5gReferralTest {

    @Test
    fun test_valid_code_shape() {
        assertTrue(Vpn5gReferral.isValidCode("ABCD2345"))
        assertTrue(Vpn5gReferral.isValidCode("abcd2345")) // case-insensitive
        assertTrue(Vpn5gReferral.isValidCode("  ABCD2345  ")) // trimmed
        // Wrong length.
        assertFalse(Vpn5gReferral.isValidCode("ABC2345"))
        assertFalse(Vpn5gReferral.isValidCode("ABCD23456"))
        // Ambiguous chars are not in the alphabet (0, O, 1, I).
        assertFalse(Vpn5gReferral.isValidCode("ABCD2340"))
        assertFalse(Vpn5gReferral.isValidCode("ABCD234O"))
        assertFalse(Vpn5gReferral.isValidCode("ABCD2341"))
        assertFalse(Vpn5gReferral.isValidCode("ABCD234I"))
        assertFalse(Vpn5gReferral.isValidCode(null))
        assertFalse(Vpn5gReferral.isValidCode(""))
    }

    @Test
    fun test_normalize_code() {
        assertEquals("ABCD2345", Vpn5gReferral.normalizeCode(" abcd2345 "))
        assertNull(Vpn5gReferral.normalizeCode("nope"))
        assertNull(Vpn5gReferral.normalizeCode(null))
    }

    @Test
    fun test_generated_codes_are_valid() {
        val random = Random(42)
        repeat(50) {
            val code = Vpn5gReferral.generateCode(random)
            assertTrue(Vpn5gReferral.isValidCode(code), "generated: $code")
        }
        assertEquals(8, Vpn5gReferral.CODE_LENGTH)
        assertEquals(1, Vpn5gReferral.GIFT_GB)
    }

    @Test
    fun test_share_and_app_links() {
        assertEquals(
            "https://t.me/VPN5G_IRBot?start=ref_ABCD2345",
            Vpn5gReferral.shareLink("ABCD2345"),
        )
        assertEquals(
            "vpn5g://ref/ABCD2345",
            Vpn5gReferral.appLink("ABCD2345"),
        )
    }

    @Test
    fun test_parse_incoming_link_app_scheme() {
        assertEquals(
            "ABCD2345",
            Vpn5gReferral.parseIncomingLink("vpn5g://ref/ABCD2345"),
        )
        assertEquals(
            "ABCD2345",
            Vpn5gReferral.parseIncomingLink("vpn5g://ref/abcd2345"),
        )
    }

    @Test
    fun test_parse_incoming_link_bot_start_param() {
        assertEquals(
            "ABCD2345",
            Vpn5gReferral.parseIncomingLink(
                "https://t.me/VPN5G_IRBot?start=ref_ABCD2345"
            ),
        )
        assertEquals(
            "ABCD2345",
            Vpn5gReferral.parseIncomingLink("?start=ref_abcd2345&x=1"),
        )
    }

    @Test
    fun test_parse_incoming_link_rejects_garbage() {
        assertNull(Vpn5gReferral.parseIncomingLink(null))
        assertNull(Vpn5gReferral.parseIncomingLink(""))
        assertNull(Vpn5gReferral.parseIncomingLink("https://example.com"))
        // Invalid code shape (ambiguous chars / wrong length).
        assertNull(Vpn5gReferral.parseIncomingLink("vpn5g://ref/ABC"))
        assertNull(Vpn5gReferral.parseIncomingLink("vpn5g://ref/ABCD2340"))
        // A recovery link is not a referral link.
        assertNull(Vpn5gReferral.parseIncomingLink("vpn5g://recover/ABCD2345"))
    }

    @Test
    fun test_share_link_round_trips_through_parser() {
        val code = Vpn5gReferral.generateCode(Random(7))
        assertEquals(code, Vpn5gReferral.parseIncomingLink(Vpn5gReferral.shareLink(code)))
        assertEquals(code, Vpn5gReferral.parseIncomingLink(Vpn5gReferral.appLink(code)))
    }
}
