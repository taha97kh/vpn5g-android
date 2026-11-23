package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus.Vpn5gUserSubscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.11 per-user subscription status (pure Kotlin). */
class Vpn5gSubscriptionStatusTest {

    private val now = 1_800_000_000L // ~2027-01-15
    private val future = now + 30 * 86_400L
    private val past = now - 86_400L

    @Test
    fun test_active_future_is_subscriber() {
        assertTrue(
            Vpn5gSubscriptionStatus.isSubscriber(
                Vpn5gUserSubscription("active", future, "sub_12"), now
            )
        )
    }

    @Test
    fun test_active_expired_is_not_subscriber() {
        assertFalse(
            Vpn5gSubscriptionStatus.isSubscriber(
                Vpn5gUserSubscription("active", past, "sub_1"), now
            )
        )
    }

    @Test
    fun test_active_at_exact_expiry_is_not_subscriber() {
        assertFalse(
            Vpn5gSubscriptionStatus.isSubscriber(
                Vpn5gUserSubscription("active", now, "sub_1"), now
            )
        )
    }

    @Test
    fun test_cancelled_none_and_null_are_not_subscribers() {
        assertFalse(
            Vpn5gSubscriptionStatus.isSubscriber(
                Vpn5gUserSubscription("cancelled", future, "sub_12"), now
            )
        )
        assertFalse(
            Vpn5gSubscriptionStatus.isSubscriber(
                Vpn5gUserSubscription("none", future, null), now
            )
        )
        assertFalse(Vpn5gSubscriptionStatus.isSubscriber(null, now))
        assertFalse(
            Vpn5gSubscriptionStatus.isSubscriber(
                Vpn5gUserSubscription("active", null, "sub_1"), now
            )
        )
    }

    @Test
    fun test_remaining_days() {
        assertEquals(
            30L,
            Vpn5gSubscriptionStatus.remainingDays(
                Vpn5gUserSubscription("active", future, "sub_12"), now
            )
        )
        assertEquals(
            0L,
            Vpn5gSubscriptionStatus.remainingDays(
                Vpn5gUserSubscription("active", past, "sub_1"), now
            )
        )
        assertEquals(0L, Vpn5gSubscriptionStatus.remainingDays(null, now))
    }

    @Test
    fun test_parse_row() {
        val sub = Vpn5gSubscriptionStatus.parseRow(
            """[{"subscription_status":"active","subscription_expires_at":"2027-11-18T00:00:00+00:00","subscription_plan":"sub_12"}]"""
        )
        assertEquals("active", sub?.status)
        assertEquals("sub_12", sub?.plan)
        assertEquals(1_826_496_000L, sub?.expiresAtEpochSeconds)
    }

    @Test
    fun test_parse_row_empty_and_malformed() {
        assertNull(Vpn5gSubscriptionStatus.parseRow("[]"))
        assertNull(Vpn5gSubscriptionStatus.parseRow(""))
        assertNull(Vpn5gSubscriptionStatus.parseRow("not json"))
        assertNull(Vpn5gSubscriptionStatus.parseRow("""[{"foo":"bar"}]"""))
    }

    @Test
    fun test_parse_row_missing_expiry_is_not_subscriber() {
        val sub = Vpn5gSubscriptionStatus.parseRow(
            """[{"subscription_status":"active","subscription_expires_at":null,"subscription_plan":"sub_1"}]"""
        )
        assertEquals("active", sub?.status)
        assertNull(sub?.expiresAtEpochSeconds)
        assertFalse(Vpn5gSubscriptionStatus.isSubscriber(sub, now))
    }

    @Test
    fun test_parse_iso_variants() {
        // Zulu and offset forms parse to the same instant.
        assertEquals(
            1_826_496_000L,
            Vpn5gSubscriptionStatus.parseIsoToEpoch("2027-11-18T00:00:00Z")
        )
        assertEquals(
            1_826_496_000L,
            Vpn5gSubscriptionStatus.parseIsoToEpoch("2027-11-18T03:30:00+03:30")
        )
        assertEquals(
            1_826_496_000L,
            Vpn5gSubscriptionStatus.parseIsoToEpoch("2027-11-18T00:00:00.000+00:00")
        )
        assertNull(Vpn5gSubscriptionStatus.parseIsoToEpoch("not a date"))
        assertNull(Vpn5gSubscriptionStatus.parseIsoToEpoch(""))
    }

    @Test
    fun test_free_guids_for_subscriber_is_whole_pool() {
        val servers = listOf(
            Vpn5gServerUi("g1", "a", "FR", 50L, false, isFree = false, hasRealPing = true),
            Vpn5gServerUi("g2", "b", "DE", 120L, false, isFree = false, hasRealPing = true),
            Vpn5gServerUi("g3", "c", "NL", 300L, false, isFree = false, hasRealPing = true),
            // No real ping: never pickable, even for a subscriber.
            Vpn5gServerUi("g4", "d", "TR", 10L, false, isFree = false, hasRealPing = false),
            Vpn5gServerUi("g5", "e", "US", 0L, false, isFree = false, hasRealPing = true),
        )
        assertEquals(
            setOf("g1", "g2", "g3"),
            Vpn5gSync.freeGuidsFor(servers, subscriber = true),
        )
        // Free tier with a cap of 2: only the 2 fastest real-pinged
        // servers (the production cap is 20 — same rule, bigger N).
        assertEquals(
            setOf("g1", "g2"),
            Vpn5gSync.selectFreeGuids(servers, count = 2),
        )
    }

    @Test
    fun test_parse_user_id() {
        val body = """{"access_token":"tok","token_type":"bearer","expires_in":3600,"refresh_token":"ref","user":{"id":"123e4567-e89b-12d3-a456-426614174000","aud":"authenticated","role":"authenticated","email":"a@gmail.com"}}"""
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000",
            Vpn5gAuthService.parseUserId(body),
        )
        assertNull(Vpn5gAuthService.parseUserId("""{"access_token":"tok"}"""))
        assertNull(Vpn5gAuthService.parseUserId("""{"user":{}}"""))
    }
}
