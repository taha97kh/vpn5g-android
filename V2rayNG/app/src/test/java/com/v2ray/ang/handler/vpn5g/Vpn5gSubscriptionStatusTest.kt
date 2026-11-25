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

    // -- v1.1.20: volume columns + expiry date ---------------------------

    @Test
    fun test_parse_row_volume_columns() {
        val sub = Vpn5gSubscriptionStatus.parseRow(
            """[{"subscription_status":"active","subscription_expires_at":"2027-01-25T23:30:08+00:00","subscription_plan":"gb_50","subscription_gb_total":50,"subscription_gb_used_mb":1024.5}]"""
        )
        assertEquals("gb_50", sub?.plan)
        // Decimal GB, like Vpn5gVolume.GB_BYTES.
        assertEquals(50L * 1_000_000_000L, sub?.volumeCapBytes)
        assertEquals(1_024_500_000L, sub?.volumeUsedServerBytes)
    }

    @Test
    fun test_parse_row_without_volume_columns_is_null_not_zero() {
        val sub = Vpn5gSubscriptionStatus.parseRow(
            """[{"subscription_status":"active","subscription_expires_at":"2027-11-18T00:00:00+00:00","subscription_plan":"sub_12"}]"""
        )
        assertNull(sub?.volumeCapBytes)
        assertNull(sub?.volumeUsedServerBytes)
    }

    @Test
    fun test_format_expiry_date() {
        // 2027-06-15T12:00:00Z — midday UTC, so every timezone from
        // UTC-11 to UTC+11 renders the same calendar date.
        val noon = Vpn5gSubscriptionStatus.parseIsoToEpoch("2027-06-15T12:00:00+00:00")!!
        assertEquals("2027/06/15", Vpn5gSubscriptionStatus.formatExpiryDate(noon))
        assertEquals("2027/06/16", Vpn5gSubscriptionStatus.formatExpiryDate(noon + 86_400L))
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

    // v1.1.12: the effective subscription merges the bot row with a redeemed gift code.

    @Test
    fun test_effective_redeemed_survives_no_row() {
        val redeemed = Vpn5gUserSubscription("active", future, "sub_12")
        assertEquals(
            redeemed,
            Vpn5gSubscriptionStatus.effectiveSubscription(
                Vpn5gUserSubscription("none", null, null), redeemed, now
            ),
        )
        assertEquals(
            redeemed,
            Vpn5gSubscriptionStatus.effectiveSubscription(null, redeemed, now),
        )
    }

    @Test
    fun test_effective_later_expiry_wins() {
        val row = Vpn5gUserSubscription("active", future, "sub_12")
        val redeemedSooner = Vpn5gUserSubscription("active", now + 10 * 86_400L, "sub_1")
        assertEquals(
            row,
            Vpn5gSubscriptionStatus.effectiveSubscription(row, redeemedSooner, now),
        )
        val redeemedLater = Vpn5gUserSubscription("active", future + 30 * 86_400L, "sub_12")
        assertEquals(
            redeemedLater,
            Vpn5gSubscriptionStatus.effectiveSubscription(row, redeemedLater, now),
        )
    }

    @Test
    fun test_effective_expired_row_falls_back_to_redeemed() {
        val expiredRow = Vpn5gUserSubscription("active", past, "sub_1")
        val redeemed = Vpn5gUserSubscription("active", future, "sub_12")
        assertEquals(
            redeemed,
            Vpn5gSubscriptionStatus.effectiveSubscription(expiredRow, redeemed, now),
        )
    }

    @Test
    fun test_effective_neither_active_returns_row() {
        val expiredRow = Vpn5gUserSubscription("active", past, "sub_1")
        val expiredRedeemed = Vpn5gUserSubscription("active", past, "sub_12")
        assertEquals(
            expiredRow,
            Vpn5gSubscriptionStatus.effectiveSubscription(expiredRow, expiredRedeemed, now),
        )
        assertNull(Vpn5gSubscriptionStatus.effectiveSubscription(null, null, now))
    }

    // v1.1.14: the GIFT tier sits between free and VIP.

    @Test
    fun test_gift_plan_is_not_a_full_subscriber() {
        val gift = Vpn5gUserSubscription("active", future, "gift")
        assertFalse(Vpn5gSubscriptionStatus.isSubscriber(gift, now))
        assertTrue(Vpn5gSubscriptionStatus.isGiftTier(gift, now))
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.GIFT,
            Vpn5gSubscriptionStatus.tierOf(gift, now),
        )
    }

    @Test
    fun test_expired_gift_is_free_tier() {
        val gift = Vpn5gUserSubscription("active", past, "gift")
        assertFalse(Vpn5gSubscriptionStatus.isGiftTier(gift, now))
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.FREE,
            Vpn5gSubscriptionStatus.tierOf(gift, now),
        )
        assertEquals(0L, Vpn5gSubscriptionStatus.giftRemainingHours(gift, now))
    }

    @Test
    fun test_gift_remaining_hours() {
        val gift = Vpn5gUserSubscription("active", now + 5 * 3_600L + 1_800L, "gift")
        assertEquals(5L, Vpn5gSubscriptionStatus.giftRemainingHours(gift, now))
    }

    @Test
    fun test_effective_subscriber_beats_longer_gift() {
        // A longer gift must never demote a subscriber to the top-100 pool.
        val row = Vpn5gUserSubscription("active", future, "sub_12")
        val longerGift = Vpn5gUserSubscription("active", future + 30 * 86_400L, "gift")
        assertEquals(
            row,
            Vpn5gSubscriptionStatus.effectiveSubscription(row, longerGift, now),
        )
    }

    @Test
    fun test_effective_gift_alone_is_effective() {
        val gift = Vpn5gUserSubscription("active", future, "gift")
        assertEquals(
            gift,
            Vpn5gSubscriptionStatus.effectiveSubscription(null, gift, now),
        )
    }

    // -- Volume (v1.1.18, Model B): volume or time, whichever first --------

    private val cap20Gb = 20L * Vpn5gVolume.GB_BYTES

    private fun volumeStateFor(
        sub: Vpn5gUserSubscription,
        bytes: Long,
    ): Vpn5gVolume.ConsumedState {
        return Vpn5gVolume.ConsumedState(Vpn5gVolume.grantKeyFor(sub), bytes)
    }

    @Test
    fun test_volume_exhausted_subscriber_is_free() {
        val sub = Vpn5gUserSubscription(
            "active", future, "gb_20", volumeCapBytes = cap20Gb
        )
        val volume = volumeStateFor(sub, cap20Gb)
        assertTrue(Vpn5gSubscriptionStatus.isVolumeExhausted(sub, volume))
        assertFalse(Vpn5gSubscriptionStatus.isSubscriber(sub, now, volume))
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.FREE,
            Vpn5gSubscriptionStatus.tierOf(sub, now, volume),
        )
    }

    @Test
    fun test_volume_below_cap_stays_subscriber() {
        val sub = Vpn5gUserSubscription(
            "active", future, "gb_20", volumeCapBytes = cap20Gb
        )
        val volume = volumeStateFor(sub, cap20Gb - 1L)
        assertFalse(Vpn5gSubscriptionStatus.isVolumeExhausted(sub, volume))
        assertTrue(Vpn5gSubscriptionStatus.isSubscriber(sub, now, volume))
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER,
            Vpn5gSubscriptionStatus.tierOf(sub, now, volume),
        )
    }

    @Test
    fun test_volume_unknown_consumption_keeps_time_only() {
        val sub = Vpn5gUserSubscription(
            "active", future, "gb_20", volumeCapBytes = cap20Gb
        )
        // No volume state at all: time-only, still a subscriber.
        assertTrue(Vpn5gSubscriptionStatus.isSubscriber(sub, now, null))
        // Bytes measured under ANOTHER grant never end this one.
        val other = Vpn5gVolume.ConsumedState("sub_1|$future", cap20Gb)
        assertTrue(Vpn5gSubscriptionStatus.isSubscriber(sub, now, other))
        assertFalse(Vpn5gSubscriptionStatus.isVolumeExhausted(sub, other))
    }

    @Test
    fun test_volume_exhausted_gift_is_free_and_hours_zero() {
        val gift = Vpn5gUserSubscription(
            "active", future, "gift", volumeCapBytes = cap20Gb
        )
        val volume = volumeStateFor(gift, cap20Gb + 1L)
        assertFalse(Vpn5gSubscriptionStatus.isGiftTier(gift, now, volume))
        assertEquals(0L, Vpn5gSubscriptionStatus.giftRemainingHours(gift, now, volume))
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.FREE,
            Vpn5gSubscriptionStatus.tierOf(gift, now, volume),
        )
    }

    @Test
    fun test_effective_live_grant_beats_exhausted_later_grant() {
        // An exhausted gb_20 with a LATER expiry must not hide a live
        // sub_1 with an earlier expiry: volume exhaustion is not
        // "active" for the merge.
        val row = Vpn5gUserSubscription(
            "active", future + 30 * 86_400L, "gb_20", volumeCapBytes = cap20Gb
        )
        val live = Vpn5gUserSubscription("active", future, "sub_1")
        val volume = volumeStateFor(row, cap20Gb)
        assertEquals(
            live,
            Vpn5gSubscriptionStatus.effectiveSubscription(row, live, now, volume),
        )
    }

    @Test
    fun test_effective_exhausted_grant_still_returned_when_nothing_live() {
        // Neither grant is active: the fallback still returns the row
        // so the UI can show WHY it ended (volume finished).
        val row = Vpn5gUserSubscription(
            "active", future, "gb_20", volumeCapBytes = cap20Gb
        )
        val volume = volumeStateFor(row, cap20Gb)
        assertEquals(
            row,
            Vpn5gSubscriptionStatus.effectiveSubscription(row, null, now, volume),
        )
    }

    // -- Redeemed grant survives a failed / "none" row read (v1.1.21) -----
    // The 1.1.20 failure: the row re-read is 403 (missing GRANT SELECT
    // for `authenticated`), so the merge must never let a failed or
    // "none" row demote a just-redeemed grant — otherwise the status
    // section stays empty AND the country locks stay closed.
    @Test
    fun test_redeemed_gift_survives_none_row() {
        val redeemed = Vpn5gUserSubscription("active", future, "gift")
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(
            Vpn5gUserSubscription("none", null, null), redeemed, now
        )
        assertEquals(redeemed, merged)
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.GIFT,
            Vpn5gSubscriptionStatus.tierOf(merged, now),
        )
        assertTrue(Vpn5gSubscriptionStatus.isGiftTier(merged, now))
        assertFalse(Vpn5gSubscriptionStatus.isSubscriber(merged, now))
    }

    @Test
    fun test_redeemed_sub_survives_missing_row() {
        val redeemed = Vpn5gUserSubscription("active", future, "sub_1")
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(null, redeemed, now)
        assertEquals(redeemed, merged)
        assertEquals(
            Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER,
            Vpn5gSubscriptionStatus.tierOf(merged, now),
        )
        assertTrue(Vpn5gSubscriptionStatus.isSubscriber(merged, now))
    }

    @Test
    fun test_expired_row_never_demotes_redeemed_gift() {
        val row = Vpn5gUserSubscription("active", past, "sub_1")
        val redeemed = Vpn5gUserSubscription("active", future, "gift")
        assertEquals(
            redeemed,
            Vpn5gSubscriptionStatus.effectiveSubscription(row, redeemed, now),
        )
    }
}
