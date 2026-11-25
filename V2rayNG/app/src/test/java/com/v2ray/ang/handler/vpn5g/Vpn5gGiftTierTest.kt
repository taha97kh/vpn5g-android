package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus.Vpn5gTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** JVM tests for the tiered server pool (v1.1.14): free 20 / gift 100 / VIP all. */
class Vpn5gGiftTierTest {

    private fun servers(count: Int): List<Vpn5gServerUi> {
        return (1..count).map { i ->
            Vpn5gServerUi(
                guid = "g$i",
                name = "g$i",
                country = "c",
                pingMillis = i.toLong(),
                isSelected = false,
                hasRealPing = true,
            )
        }
    }

    @Test
    fun test_free_tier_is_20() {
        assertEquals(20, Vpn5gSync.freeGuidsFor(servers(60), Vpn5gTier.FREE).size)
    }

    @Test
    fun test_gift_tier_is_100() {
        val guids = Vpn5gSync.freeGuidsFor(servers(150), Vpn5gTier.GIFT)
        assertEquals(100, guids.size)
        // The 100 fastest (pings 1..100).
        assertEquals((1..100).map { "g$it" }.toSet(), guids)
    }

    @Test
    fun test_subscriber_tier_is_all() {
        assertEquals(60, Vpn5gSync.freeGuidsFor(servers(60), Vpn5gTier.SUBSCRIBER).size)
    }

    @Test
    fun test_gift_tier_never_exceeds_pool() {
        assertEquals(30, Vpn5gSync.freeGuidsFor(servers(30), Vpn5gTier.GIFT).size)
    }

    @Test
    fun test_tcp_only_servers_never_in_any_tier() {
        val mixed = servers(25).mapIndexed { index, server ->
            if (index < 5) server.copy(hasRealPing = false, pingMillis = -1L) else server
        }
        assertEquals(20, Vpn5gSync.freeGuidsFor(mixed, Vpn5gTier.FREE).size)
        assertEquals(20, Vpn5gSync.freeGuidsFor(mixed, Vpn5gTier.GIFT).size)
    }

    // -- A redeem opens the locks (v1.1.21) --------------------------------
    // The 1.1.20 symptom (Aref 2026-11-25 ~05:56): after registering a
    // key the paid/gift servers stayed LOCKED in Premium countries AND
    // in All Locations — the same root cause (no grant was ever saved,
    // so the tier stayed FREE). Once the grant IS saved, the merged
    // subscription's tier must open the matching pool: gift → top-100,
    // sub → the full pool.
    @Test
    fun test_redeemed_gift_opens_top_100() {
        val now = 1_800_000_000L
        val redeemed = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            "active", now + 86_400L, "gift"
        )
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(null, redeemed, now)
        val tier = Vpn5gSubscriptionStatus.tierOf(merged, now)
        assertEquals(Vpn5gTier.GIFT, tier)
        assertEquals(100, Vpn5gSync.freeGuidsFor(servers(150), tier).size)
    }

    @Test
    fun test_redeemed_sub_opens_full_pool() {
        val now = 1_800_000_000L
        val redeemed = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            "active", now + 30 * 86_400L, "sub_1"
        )
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(null, redeemed, now)
        val tier = Vpn5gSubscriptionStatus.tierOf(merged, now)
        assertEquals(Vpn5gTier.SUBSCRIBER, tier)
        assertEquals(60, Vpn5gSync.freeGuidsFor(servers(60), tier).size)
    }
}
