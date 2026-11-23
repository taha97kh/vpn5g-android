package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus.Vpn5gTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** JVM tests for the tiered server pool (v1.1.14): free 20 / gift 50 / VIP all. */
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
    fun test_gift_tier_is_50() {
        val guids = Vpn5gSync.freeGuidsFor(servers(60), Vpn5gTier.GIFT)
        assertEquals(50, guids.size)
        // The 50 fastest (pings 1..50).
        assertEquals((1..50).map { "g$it" }.toSet(), guids)
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
}
