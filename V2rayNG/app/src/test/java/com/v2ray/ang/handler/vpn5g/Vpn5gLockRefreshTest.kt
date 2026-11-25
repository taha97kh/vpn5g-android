package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus.Vpn5gTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the post-redeem lock refresh (v1.1.22, fix #2): after
 * a successful redeem the locked servers/countries must open at once
 * — from the MERGED subscription (cached + just-granted) — even when
 * the verified server set is tiny, which is the state Aref's phone
 * is in (2 free servers on 1.1.18).
 */
class Vpn5gLockRefreshTest {

    private fun server(guid: String, ping: Long, realPing: Boolean): Vpn5gServerUi {
        return Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = "c",
            pingMillis = ping,
            isSelected = false,
            hasRealPing = realPing,
        )
    }

    @Test
    fun test_gift_fills_from_tcp_pinged_when_verified_set_is_tiny() {
        // 2 verified + 50 TCP-pinged: a gift must open more than the
        // 2 verified — the fill adds the fastest TCP-pinged first.
        val servers = listOf(
            server("v1", 40L, true),
            server("v2", 80L, true),
        ) + (1..50).map { server("t$it", it * 10L, false) }
        val guids = Vpn5gSync.freeGuidsFor(servers, Vpn5gTier.GIFT)
        assertEquals(52, guids.size)
        assertTrue("v1" in guids && "v2" in guids)
        assertTrue("t1" in guids)
    }

    @Test
    fun test_subscriber_fills_whole_tcp_pool() {
        val servers = listOf(server("v1", 40L, true)) +
            (1..10).map { server("t$it", it * 10L, false) }
        assertEquals(11, Vpn5gSync.freeGuidsFor(servers, Vpn5gTier.SUBSCRIBER).size)
    }

    @Test
    fun test_gift_fill_is_capped_at_100() {
        // 2 verified + 150 TCP-pinged: the gift tier stays 100.
        val servers = listOf(
            server("v1", 40L, true),
            server("v2", 80L, true),
        ) + (1..150).map { server("t$it", it * 10L, false) }
        assertEquals(100, Vpn5gSync.freeGuidsFor(servers, Vpn5gTier.GIFT).size)
    }

    @Test
    fun test_free_tier_never_fills() {
        val servers = listOf(server("v1", 40L, true)) +
            (1..10).map { server("t$it", it * 10L, false) }
        assertEquals(setOf("v1"), Vpn5gSync.freeGuidsFor(servers, Vpn5gTier.FREE))
    }

    @Test
    fun test_failed_servers_never_filled() {
        // Ping -1 (failed the real test) and 0 (no ping) are never
        // filled in, in any paid tier.
        val servers = listOf(
            server("v1", 40L, true),
            server("dead", -1L, false),
            server("zero", 0L, false),
        )
        assertEquals(setOf("v1"), Vpn5gSync.freeGuidsFor(servers, Vpn5gTier.SUBSCRIBER))
        assertEquals(setOf("v1"), Vpn5gSync.freeGuidsFor(servers, Vpn5gTier.GIFT))
    }

    @Test
    fun test_merged_tier_keeps_better_grant_after_redeem() {
        // Cached sub + redeemed gift: the merged tier is SUBSCRIBER,
        // so a gift redeem never demotes a subscription.
        val now = 1_800_000_000L
        val cached = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            "active", now + 30 * 86_400L, "sub_1"
        )
        val redeemed = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            "active", now + 86_400L, "gift"
        )
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(cached, redeemed, now)
        assertEquals(Vpn5gTier.SUBSCRIBER, Vpn5gSubscriptionStatus.tierOf(merged, now))
    }

    @Test
    fun test_merged_tier_gift_beats_stale_none() {
        // No cached subscription + redeemed gift: the merged tier is
        // GIFT — the 1.1.20 symptom (locks shut after a redeem)
        // cannot come back through a stale "none".
        val now = 1_800_000_000L
        val redeemed = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            "active", now + 86_400L, "gift"
        )
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(null, redeemed, now)
        assertEquals(Vpn5gTier.GIFT, Vpn5gSubscriptionStatus.tierOf(merged, now))
    }
}
