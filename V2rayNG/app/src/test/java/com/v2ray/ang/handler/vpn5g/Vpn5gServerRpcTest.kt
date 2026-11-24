package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.15 server-side RPC response parsers (pure
 * Kotlin): the code-validation result and the subscription-status
 * result. The network calls themselves are best-effort and device
 * concerns; only the parsing is unit-tested here. */
class Vpn5gServerRpcTest {

    private val expiresIso = "2027-11-18T19:14:59Z"

    @Test
    fun test_parse_code_validated_with_grant() {
        val result = Vpn5gAuthService.parseCodeServerResult(
            """{"status":"ok","plan":"sub_12","expires_at":"$expiresIso"}"""
        )
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertEquals("sub_12", validated.plan)
        assertEquals(
            Vpn5gSubscriptionStatus.parseIsoToEpoch(expiresIso),
            validated.expiresAtEpochSeconds,
        )
    }

    @Test
    fun test_parse_code_validated_status_aliases() {
        for (status in listOf("ok", "valid", "validated", "OK", "Ok")) {
            val result = Vpn5gAuthService.parseCodeServerResult(
                """{"status":"$status"}"""
            )
            assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated, status)
            val validated = result as Vpn5gAuthService.CodeServerResult.Validated
            assertNull(validated.plan)
            assertNull(validated.expiresAtEpochSeconds)
        }
    }

    @Test
    fun test_parse_code_rejected_statuses() {
        for (status in listOf("unknown", "already_used", "expired", "exhausted", "not_yours")) {
            val result = Vpn5gAuthService.parseCodeServerResult(
                """{"status":"$status"}"""
            )
            assertTrue(result is Vpn5gAuthService.CodeServerResult.Rejected, status)
            assertEquals(
                status,
                (result as Vpn5gAuthService.CodeServerResult.Rejected).status,
            )
        }
    }

    // -- The REAL server shape (v1.1.17): {"ok": bool, "reason": ...} ------
    // Live-probed 2026-11-24: the real validate_and_redeem_code returns
    // {"ok":true,"plan":...,"expires_at":...} / {"ok":false,"reason":
    // "not_found"} — never the documented {"status":...} shape. The
    // 1.1.15/1.1.16 parser only understood {"status":...}, so every
    // real answer parsed as Unavailable and the code was consumed
    // with no grant in-app.

    @Test
    fun test_parse_code_validated_ok_shape_with_grant() {
        val result = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":true,"plan":"sub_1","expires_at":"$expiresIso"}"""
        )
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertEquals("sub_1", validated.plan)
        assertEquals(
            Vpn5gSubscriptionStatus.parseIsoToEpoch(expiresIso),
            validated.expiresAtEpochSeconds,
        )
    }

    @Test
    fun test_parse_code_validated_ok_shape_without_grant() {
        val result = Vpn5gAuthService.parseCodeServerResult("""{"ok":true}""")
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertNull(validated.plan)
        assertNull(validated.expiresAtEpochSeconds)
    }

    @Test
    fun test_parse_code_rejected_ok_shape_reasons() {
        // not_found maps to the app's "unknown" status.
        val notFound = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":false,"reason":"not_found"}"""
        )
        assertTrue(notFound is Vpn5gAuthService.CodeServerResult.Rejected)
        assertEquals(
            "unknown",
            (notFound as Vpn5gAuthService.CodeServerResult.Rejected).status,
        )
        for (reason in listOf("already_used", "expired", "exhausted", "not_yours")) {
            val result = Vpn5gAuthService.parseCodeServerResult(
                """{"ok":false,"reason":"$reason"}"""
            )
            assertTrue(result is Vpn5gAuthService.CodeServerResult.Rejected, reason)
            assertEquals(
                reason,
                (result as Vpn5gAuthService.CodeServerResult.Rejected).status,
            )
        }
    }

    @Test
    fun test_parse_code_unavailable_on_ok_shape_garbage() {
        // No reason: unparsable, never a false rejection.
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult("""{"ok":false}""") is
                Vpn5gAuthService.CodeServerResult.Unavailable
        )
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult(
                """{"ok":false,"reason":"weird"}"""
            ) is Vpn5gAuthService.CodeServerResult.Unavailable
        )
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult("""{"ok":"yes"}""") is
                Vpn5gAuthService.CodeServerResult.Unavailable
        )
    }

    @Test
    fun test_parse_code_unavailable_on_garbage() {
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult("") is
                Vpn5gAuthService.CodeServerResult.Unavailable
        )
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult("not json") is
                Vpn5gAuthService.CodeServerResult.Unavailable
        )
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult("""{"status":"weird"}""") is
                Vpn5gAuthService.CodeServerResult.Unavailable
        )
        assertTrue(
            Vpn5gAuthService.parseCodeServerResult("""{"plan":"sub_12"}""") is
                Vpn5gAuthService.CodeServerResult.Unavailable
        )
    }

    @Test
    fun test_parse_subscription_none() {
        for (body in listOf("", "[]", "null", """{"status":"none"}""", """{"status":"inactive"}""", """{"status":"cancelled"}""")) {
            assertTrue(
                Vpn5gAuthService.parseServerSubscription(body) is
                    Vpn5gAuthService.ServerSubscriptionResult.None,
                body,
            )
        }
    }

    @Test
    fun test_parse_subscription_found() {
        val result = Vpn5gAuthService.parseServerSubscription(
            """{"status":"active","plan":"sub_1","expires_at":"$expiresIso"}"""
        )
        assertTrue(result is Vpn5gAuthService.ServerSubscriptionResult.Found)
        val found = result as Vpn5gAuthService.ServerSubscriptionResult.Found
        assertEquals("active", found.subscription.status)
        assertEquals("sub_1", found.subscription.plan)
        assertEquals(
            Vpn5gSubscriptionStatus.parseIsoToEpoch(expiresIso),
            found.subscription.expiresAtEpochSeconds,
        )
    }

    @Test
    fun test_parse_subscription_unavailable_without_status() {
        assertTrue(
            Vpn5gAuthService.parseServerSubscription("garbage") is
                Vpn5gAuthService.ServerSubscriptionResult.Unavailable
        )
        assertTrue(
            Vpn5gAuthService.parseServerSubscription("""{"plan":"sub_1"}""") is
                Vpn5gAuthService.ServerSubscriptionResult.Unavailable
        )
    }
}
