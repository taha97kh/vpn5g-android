package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** JVM tests for the recovery deep-link parser (pure Kotlin, v1.1.8). */
class Vpn5gRecoveryLinkTest {

    private val valid =
        "vpn5g://auth/callback#access_token=eyJhbGciOiJIUzI1NiJ9" +
            "&refresh_token=refresh123&expires_in=3600&token_type=bearer&type=recovery"

    @Test
    fun test_parse_valid_recovery_link() {
        val parsed = Vpn5gRecoveryLink.parse(valid)
        assertEquals("eyJhbGciOiJIUzI1NiJ9", parsed?.accessToken)
        assertEquals("refresh123", parsed?.refreshToken)
        assertEquals(3600L, parsed?.expiresInSeconds)
    }

    @Test
    fun test_parse_link_without_refresh_token() {
        val parsed = Vpn5gRecoveryLink.parse(
            "vpn5g://auth/callback#access_token=tok123&type=recovery"
        )
        assertEquals("tok123", parsed?.accessToken)
        assertEquals(null, parsed?.refreshToken)
        assertEquals(null, parsed?.expiresInSeconds)
    }

    @Test
    fun test_parse_url_encoded_token() {
        val parsed = Vpn5gRecoveryLink.parse(
            "vpn5g://auth/callback#access_token=tok%2Babc%2Fdef&type=recovery"
        )
        assertEquals("tok+abc/def", parsed?.accessToken)
    }

    @Test
    fun test_parse_rejects_other_type() {
        // A signup confirmation link is not a recovery link.
        assertNull(
            Vpn5gRecoveryLink.parse(
                "vpn5g://auth/callback#access_token=tok123&type=signup"
            )
        )
    }

    @Test
    fun test_parse_rejects_missing_token() {
        assertNull(
            Vpn5gRecoveryLink.parse(
                "vpn5g://auth/callback#refresh_token=r1&type=recovery"
            )
        )
        assertNull(
            Vpn5gRecoveryLink.parse("vpn5g://auth/callback#type=recovery")
        )
    }

    @Test
    fun test_parse_rejects_query_not_fragment() {
        // GoTrue puts the tokens in the fragment, never the query.
        assertNull(
            Vpn5gRecoveryLink.parse(
                "vpn5g://auth/callback?access_token=tok123&type=recovery"
            )
        )
    }

    @Test
    fun test_parse_rejects_other_scheme_and_host() {
        assertNull(
            Vpn5gRecoveryLink.parse(
                "https://example.com/auth/callback#access_token=tok123&type=recovery"
            )
        )
        assertNull(
            Vpn5gRecoveryLink.parse(
                "vpn5g://other/callback#access_token=tok123&type=recovery"
            )
        )
        assertNull(Vpn5gRecoveryLink.parse(""))
        assertNull(Vpn5gRecoveryLink.parse("not a uri"))
    }
}
