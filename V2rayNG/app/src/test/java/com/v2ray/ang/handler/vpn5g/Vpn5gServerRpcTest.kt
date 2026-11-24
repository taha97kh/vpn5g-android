package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        assertNull(validated.applied)
    }

    // -- v1.1.19: the live function burns the code first, then grants
    // only IF a registrations row exists for the email. With no row
    // it answers ok:true + applied:false with expires_at stripped
    // (242593 / 490013 showed VALID with no plan granted), and its
    // real reason spellings are "already_redeemed" / "invalid_email"
    // (there is NO "already_used" reason server-side).

    @Test
    fun test_parse_code_validated_ok_shape_plan_no_expiry() {
        // The 1.1.18 case: plan present, expires_at stripped.
        val result = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":true,"plan":"sub_1"}"""
        )
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertEquals("sub_1", validated.plan)
        assertNull(validated.expiresAtEpochSeconds)
        assertNull(validated.applied)
    }

    @Test
    fun test_parse_code_validated_applied_false() {
        val result = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":true,"plan":"sub_1","applied":false}"""
        )
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertEquals("sub_1", validated.plan)
        assertNull(validated.expiresAtEpochSeconds)
        assertEquals(false, validated.applied)
    }

    @Test
    fun test_parse_code_validated_applied_true() {
        val result = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":true,"plan":"sub_1","expires_at":"$expiresIso","applied":true}"""
        )
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertEquals("sub_1", validated.plan)
        assertEquals(
            Vpn5gSubscriptionStatus.parseIsoToEpoch(expiresIso),
            validated.expiresAtEpochSeconds,
        )
        assertEquals(true, validated.applied)
    }

    @Test
    fun test_parse_code_validated_numeric_epoch_expires_at() {
        // Bare numeric epoch (unquoted digits) as a fallback.
        val result = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":true,"plan":"sub_1","expires_at":1793000000}"""
        )
        assertTrue(result is Vpn5gAuthService.CodeServerResult.Validated)
        val validated = result as Vpn5gAuthService.CodeServerResult.Validated
        assertEquals(1793000000L, validated.expiresAtEpochSeconds)
        // A quoted all-digits value parses as an epoch too.
        val quoted = Vpn5gAuthService.parseCodeServerResult(
            """{"ok":true,"plan":"sub_1","expires_at":"1793000000"}"""
        )
        assertEquals(
            1793000000L,
            (quoted as Vpn5gAuthService.CodeServerResult.Validated).expiresAtEpochSeconds,
        )
    }

    @Test
    fun test_parse_code_rejected_already_redeemed_maps_to_already_used() {
        // The live reason spelling; the app's recovery path keys off
        // "already_used", so the parser maps it (literal
        // "already_used" still accepted too).
        for (body in listOf(
            """{"ok":false,"reason":"already_redeemed"}""",
            """{"status":"already_redeemed"}""",
            """{"ok":false,"reason":"already_used"}""",
        )) {
            val result = Vpn5gAuthService.parseCodeServerResult(body)
            assertTrue(result is Vpn5gAuthService.CodeServerResult.Rejected, body)
            assertEquals(
                "already_used",
                (result as Vpn5gAuthService.CodeServerResult.Rejected).status,
                body,
            )
        }
    }

    @Test
    fun test_parse_code_rejected_invalid_email_distinct_status() {
        for (body in listOf(
            """{"ok":false,"reason":"invalid_email"}""",
            """{"status":"invalid_email"}""",
        )) {
            val result = Vpn5gAuthService.parseCodeServerResult(body)
            assertTrue(result is Vpn5gAuthService.CodeServerResult.Rejected, body)
            assertEquals(
                "invalid_email",
                (result as Vpn5gAuthService.CodeServerResult.Rejected).status,
                body,
            )
        }
    }

    // -- v1.1.19: the derive-from-entry helper (pure) ----------------
    // Used to recover a code the server validated but did not grant
    // (applied:false / stripped expires_at): the grant is re-derived
    // from the published entry instead of showing VALID.

    @Test
    fun test_derive_grant_from_entry_plan_code_grants_plan_days() {
        // The live 242593 case: type gift, plan sub_1 → 30 days.
        val now = 1_800_000_000L
        val derived = Vpn5gPromoCodes.deriveGrantFromEntry(
            PromoCodeRemote(code = "242593", type = "gift", plan = "sub_1"),
            now,
        )
        assertNotNull(derived)
        assertEquals("sub_1", derived?.plan)
        assertEquals(now + 30L * 86_400L, derived?.expiresAtEpochSeconds)
        assertEquals("active", derived?.status)
    }

    @Test
    fun test_derive_grant_from_entry_gift_hours_grants_hours() {
        // The live 490013 case: type gift, gift_hours 24 → 24 hours,
        // middle GIFT tier.
        val now = 1_800_000_000L
        val derived = Vpn5gPromoCodes.deriveGrantFromEntry(
            PromoCodeRemote(code = "490013", type = "gift", gift_hours = 24),
            now,
        )
        assertNotNull(derived)
        assertEquals(Vpn5gConstants.GIFT_TIER_PLAN_ID, derived?.plan)
        assertEquals(now + 24L * 3_600L, derived?.expiresAtEpochSeconds)
    }

    @Test
    fun test_derive_grant_from_entry_discount_derives_nothing() {
        // A discount code has no grant to derive — it goes through
        // the bot, never through recovery.
        assertNull(
            Vpn5gPromoCodes.deriveGrantFromEntry(
                PromoCodeRemote(code = "DISC10", type = "discount", discount_percent = 10),
                1_800_000_000L,
            )
        )
        assertNull(
            Vpn5gPromoCodes.deriveGrantFromEntry(
                PromoCodeRemote(code = "NODUR", type = "gift"),
                1_800_000_000L,
            )
        )
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
