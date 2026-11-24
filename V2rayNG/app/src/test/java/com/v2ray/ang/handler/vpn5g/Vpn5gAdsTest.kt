package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.15 ad decisions (pure Kotlin): the forced-ad
 * rule, the monotonic-clock countdown, the user-ad schedule/caps, and
 * the impression ledger. */
class Vpn5gAdsTest {

    private fun interstitial(
        enabled: Boolean? = true,
        duration: Int? = 10,
        from: Int? = 2,
        freeOnly: Boolean? = true,
        text: String? = "تبلیغ",
        imageUrl: String? = null,
        linkUrl: String? = null,
        id: String? = "ad-1",
    ) = InterstitialAdRemote(
        enabled = enabled,
        duration_seconds = duration,
        show_from_connect_number = from,
        applies_to_free_only = freeOnly,
        text = text,
        image_url = imageUrl,
        link_url = linkUrl,
        id = id,
    )

    private fun ads(
        interstitial: InterstitialAdRemote? = interstitial(),
        userAds: List<UserAdRemote>? = null,
    ) = AdsRemote(
        interstitial = interstitial,
        user_ads = userAds,
        pricing = null,
    )

    private fun userAd(
        id: String? = "ad-user-1",
        max: Int? = 10,
        perDay: Int? = null,
        windows: List<List<Int>>? = null,
        enabled: Boolean? = true,
        approved: Boolean? = true,
        text: String? = "تبلیغ کاربر",
    ) = UserAdRemote(
        id = id,
        text = text,
        image_url = null,
        link_url = null,
        model = "ten_times",
        max_impressions = max,
        impressions_per_day = perDay,
        hour_windows = windows,
        price_toman = 50000,
        enabled = enabled,
        approved = approved,
    )

    @Test
    fun test_clamp_duration_bounds() {
        assertEquals(10, Vpn5gAds.clampDuration(null))
        assertEquals(1, Vpn5gAds.clampDuration(0))
        assertEquals(1, Vpn5gAds.clampDuration(1))
        assertEquals(20, Vpn5gAds.clampDuration(20))
        assertEquals(20, Vpn5gAds.clampDuration(21))
        assertEquals(20, Vpn5gAds.clampDuration(60))
        assertEquals(1, Vpn5gAds.clampDuration(-5))
    }

    @Test
    fun test_first_connect_never_shows_ad() {
        // Connect #1 (0 sessions started) never shows the forced ad.
        assertFalse(Vpn5gAds.shouldShowInterstitial(ads(), 0, false, false))
        // Connect #2 (1 session started) does.
        assertTrue(Vpn5gAds.shouldShowInterstitial(ads(), 1, false, false))
        // Later connects do too.
        assertTrue(Vpn5gAds.shouldShowInterstitial(ads(), 5, false, false))
    }

    @Test
    fun test_show_from_connect_number_is_tunable() {
        // Admin-set from=1 would show from the first connect…
        assertTrue(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(from = 1)), 0, false, false)
        )
        // …and from=3 waits for the third.
        assertFalse(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(from = 3)), 1, false, false)
        )
        assertTrue(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(from = 3)), 2, false, false)
        )
        // A from below 1 is clamped to 1.
        assertTrue(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(from = 0)), 0, false, false)
        )
    }

    @Test
    fun test_no_ad_when_config_absent_or_disabled() {
        assertFalse(Vpn5gAds.shouldShowInterstitial(null, 1, false, false))
        assertFalse(Vpn5gAds.shouldShowInterstitial(ads(interstitial = null), 1, false, false))
        assertFalse(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(enabled = false)), 1, false, false)
        )
        assertFalse(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(enabled = null)), 1, false, false)
        )
    }

    @Test
    fun test_subscribers_skip_unless_config_says_otherwise() {
        assertFalse(Vpn5gAds.shouldShowInterstitial(ads(), 1, true, false))
        assertFalse(Vpn5gAds.shouldShowInterstitial(ads(), 1, false, true))
        assertTrue(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(freeOnly = false)), 1, true, false)
        )
        assertTrue(
            Vpn5gAds.shouldShowInterstitial(ads(interstitial(freeOnly = false)), 1, false, true)
        )
    }

    @Test
    fun test_remaining_seconds_uses_ceiling_and_zero_floor() {
        assertEquals(10L, Vpn5gAds.remainingSeconds(10_000L, 0L, 10))
        assertEquals(5L, Vpn5gAds.remainingSeconds(10_000L, 5_000L, 10))
        // Ceiling: 1 ms left still reads 1 second.
        assertEquals(1L, Vpn5gAds.remainingSeconds(10_000L, 9_999L, 10))
        assertEquals(2L, Vpn5gAds.remainingSeconds(10_000L, 8_999L, 10))
        assertEquals(0L, Vpn5gAds.remainingSeconds(10_000L, 10_000L, 10))
        assertEquals(0L, Vpn5gAds.remainingSeconds(10_000L, 11_000L, 10))
    }

    @Test
    fun test_user_ad_due_requires_approval_and_caps() {
        assertTrue(Vpn5gAds.isUserAdDue(userAd(), Vpn5gAds.AdImpressions(), 10))
        assertFalse(Vpn5gAds.isUserAdDue(userAd(enabled = false), Vpn5gAds.AdImpressions(), 10))
        assertFalse(Vpn5gAds.isUserAdDue(userAd(approved = false), Vpn5gAds.AdImpressions(), 10))
        // Total cap reached.
        assertFalse(
            Vpn5gAds.isUserAdDue(userAd(max = 10), Vpn5gAds.AdImpressions(total = 10), 10)
        )
        // Under the cap.
        assertTrue(
            Vpn5gAds.isUserAdDue(userAd(max = 10), Vpn5gAds.AdImpressions(total = 9), 10)
        )
        // Per-day cap reached.
        assertFalse(
            Vpn5gAds.isUserAdDue(
                userAd(perDay = 5),
                Vpn5gAds.AdImpressions(total = 1, day = "2026-11-24", today = 5),
                10,
            )
        )
        // Per-day count rolls over on a new day.
        assertTrue(
            Vpn5gAds.isUserAdDue(
                userAd(perDay = 5),
                Vpn5gAds.AdImpressions(total = 5, day = "2026-11-23", today = 5),
                10,
            )
        )
    }

    @Test
    fun test_user_ad_hour_windows_are_end_exclusive() {
        val windows = listOf(listOf(9, 12), listOf(18, 21))
        assertTrue(Vpn5gAds.isUserAdDue(userAd(windows = windows), Vpn5gAds.AdImpressions(), 9))
        assertTrue(Vpn5gAds.isUserAdDue(userAd(windows = windows), Vpn5gAds.AdImpressions(), 11))
        // End is exclusive: 12 is out of [9, 12).
        assertFalse(Vpn5gAds.isUserAdDue(userAd(windows = windows), Vpn5gAds.AdImpressions(), 12))
        assertFalse(Vpn5gAds.isUserAdDue(userAd(windows = windows), Vpn5gAds.AdImpressions(), 15))
        assertTrue(Vpn5gAds.isUserAdDue(userAd(windows = windows), Vpn5gAds.AdImpressions(), 20))
        // No windows = any hour.
        assertTrue(Vpn5gAds.isUserAdDue(userAd(windows = null), Vpn5gAds.AdImpressions(), 3))
        assertTrue(Vpn5gAds.isUserAdDue(userAd(windows = emptyList()), Vpn5gAds.AdImpressions(), 3))
    }

    @Test
    fun test_pick_interstitial_prefers_due_user_ad() {
        val picked = Vpn5gAds.pickInterstitial(
            ads(userAds = listOf(userAd())),
            emptyMap(),
            10,
        )
        assertTrue(picked?.isUserAd == true)
        assertEquals("ad-user-1", picked?.id)
        // The admin duration applies to user ads too.
        assertEquals(10, picked?.durationSeconds)
    }

    @Test
    fun test_pick_interstitial_falls_back_to_admin_ad() {
        // User ad capped out -> the admin interstitial.
        val picked = Vpn5gAds.pickInterstitial(
            ads(userAds = listOf(userAd(max = 1))),
            mapOf("ad-user-1" to Vpn5gAds.AdImpressions(total = 1)),
            10,
        )
        assertTrue(picked?.isUserAd == false)
        assertEquals("ad-1", picked?.id)
    }

    @Test
    fun test_pick_interstitial_null_when_nothing_due() {
        assertNull(Vpn5gAds.pickInterstitial(null, emptyMap(), 10))
        assertNull(
            Vpn5gAds.pickInterstitial(ads(interstitial(enabled = false)), emptyMap(), 10)
        )
        // Enabled but no content at all: no ad, never a blank screen.
        assertNull(
            Vpn5gAds.pickInterstitial(
                ads(interstitial(text = null, imageUrl = null)),
                emptyMap(),
                10,
            )
        )
        // A duration above the max is clamped.
        val picked = Vpn5gAds.pickInterstitial(
            ads(interstitial(duration = 60)),
            emptyMap(),
            10,
        )
        assertEquals(20, picked?.durationSeconds)
        // A missing id gets a stable fallback id.
        val noId = Vpn5gAds.pickInterstitial(
            ads(interstitial(id = null)),
            emptyMap(),
            10,
        )
        assertEquals("admin-interstitial", noId?.id)
    }

    @Test
    fun test_record_impression_rolls_per_day_over() {
        val empty = Vpn5gAds.AdImpressionStore()
        val once = Vpn5gAds.recordImpression(empty, "ad-1", "2026-11-24")
        assertEquals(1, once.entries["ad-1"]?.total)
        assertEquals("2026-11-24", once.entries["ad-1"]?.day)
        assertEquals(1, once.entries["ad-1"]?.today)
        val twice = Vpn5gAds.recordImpression(once, "ad-1", "2026-11-24")
        assertEquals(2, twice.entries["ad-1"]?.total)
        assertEquals(2, twice.entries["ad-1"]?.today)
        val nextDay = Vpn5gAds.recordImpression(twice, "ad-1", "2026-11-25")
        assertEquals(3, nextDay.entries["ad-1"]?.total)
        assertEquals("2026-11-25", nextDay.entries["ad-1"]?.day)
        assertEquals(1, nextDay.entries["ad-1"]?.today)
        // A blank id records nothing.
        assertEquals(empty, Vpn5gAds.recordImpression(empty, "  ", "2026-11-24"))
        // Other ads are untouched.
        val other = Vpn5gAds.recordImpression(once, "ad-2", "2026-11-24")
        assertEquals(1, other.entries["ad-1"]?.total)
        assertEquals(1, other.entries["ad-2"]?.total)
    }
}
