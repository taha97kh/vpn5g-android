package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the free tier: the 20 fastest-pinging configs (pure Kotlin). */
class Vpn5gTopFreeTest {

    private fun server(
        guid: String,
        pingMillis: Long,
        realPing: Boolean = true,
        isSeed: Boolean = false,
    ): Vpn5gServerUi {
        return Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = "c",
            pingMillis = pingMillis,
            isSelected = false,
            // v1.1.7: only a real through-the-proxy ping counts.
            hasRealPing = realPing,
            // v1.1.20: a build-time verified seed server.
            isSeed = isSeed,
        )
    }

    @Test
    fun test_free_count_is_20() {
        assertEquals(20, Vpn5gConstants.FREE_SERVER_COUNT)
    }

    @Test
    fun test_fastest_20_win() {
        // 60 servers, pings 600..10 (shuffled order in the list).
        val servers = (1..60).shuffled().map { i ->
            server(guid = "g$i", pingMillis = i * 10L)
        }
        val free = Vpn5gSync.selectFreeGuids(servers)
        assertEquals(20, free.size)
        // The 20 fastest are pings 10..200 (i = 1..20).
        assertEquals((1..20).map { "g$it" }.toSet(), free)
    }

    @Test
    fun test_no_ping_never_free() {
        val servers = listOf(
            server("dead-zero", 0L),
            server("dead-neg", -1L),
            server("fast", 10L),
            server("slow", 900L),
        )
        val free = Vpn5gSync.selectFreeGuids(servers)
        assertEquals(setOf("fast", "slow"), free)
    }

    @Test
    fun test_tcp_only_never_free() {
        // v1.1.7: a 1 ms TCP-only ping must never buy a free slot — only
        // servers with a real through-the-proxy ping can be free.
        val servers = listOf(
            server("fake-fast", 1L, realPing = false),
            server("real-slow", 900L, realPing = true),
        )
        assertEquals(setOf("real-slow"), Vpn5gSync.selectFreeGuids(servers))
    }

    @Test
    fun test_fewer_than_20_returns_all_with_ping() {
        val servers = listOf(
            server("a", 30L),
            server("b", 10L),
            server("c", 20L),
            server("dead", 0L),
        )
        assertEquals(setOf("a", "b", "c"), Vpn5gSync.selectFreeGuids(servers))
    }

    @Test
    fun test_empty_list_returns_empty() {
        assertTrue(Vpn5gSync.selectFreeGuids(emptyList()).isEmpty())
    }

    @Test
    fun test_ties_are_deterministic() {
        // All 60 servers ping 10ms: sortedBy is stable, so the first 20
        // in list order win — the same 20 on every run.
        val servers = (1..60).map { server("g$it", 10L) }
        val free = Vpn5gSync.selectFreeGuids(servers)
        assertEquals((1..20).map { "g$it" }.toSet(), free)
    }

    @Test
    fun test_custom_count_respected() {
        val servers = (1..10).map { server("g$it", it * 10L) }
        assertEquals(setOf("g1", "g2", "g3"), Vpn5gSync.selectFreeGuids(servers, count = 3))
    }

    // -- Standby pool (v1.1.17, raised v1.1.18): servers waiting to
    // switch must ALWAYS be at least 20 — the free tier's
    // real-verified pool, topped up by extra probe batches until it
    // reaches STANDBY_POOL_MIN.

    @Test
    fun test_standby_pool_min_is_20() {
        assertEquals(20, Vpn5gConstants.STANDBY_POOL_MIN)
        assertTrue(Vpn5gConstants.STANDBY_POOL_MIN >= 20)
    }

    @Test
    fun test_free_pool_meets_standby_min_when_servers_verify() {
        // 20 verified servers: the free pool is 20 — exactly the
        // 20-server standby minimum (v1.1.18, was 11).
        val servers = (1..20).map { server("g$it", it * 10L) }
        val free = Vpn5gSync.freeGuidsFor(
            servers, Vpn5gSubscriptionStatus.Vpn5gTier.FREE
        )
        assertEquals(20, free.size)
        assertTrue(free.size >= Vpn5gConstants.STANDBY_POOL_MIN)
    }

    @Test
    fun test_free_pool_below_standby_min_when_few_verify() {
        // Only 5 verified servers: the pool is 5 — the top-up must
        // keep probing (see Vpn5gSync.standbyTopUpBatch).
        val servers = (1..5).map { server("g$it", it * 10L) }
        val free = Vpn5gSync.freeGuidsFor(
            servers, Vpn5gSubscriptionStatus.Vpn5gTier.FREE
        )
        assertEquals(5, free.size)
        assertTrue(free.size < Vpn5gConstants.STANDBY_POOL_MIN)
    }

    // -- v1.1.20: seed servers (fresh-install free list) -----------------

    @Test
    fun test_seed_server_with_unknown_ping_is_free() {
        // A fresh install's seed (build-time verified, ping unknown)
        // is free-eligible — otherwise the first screen shows no free
        // server at all until the first real probe finishes.
        val servers = listOf(
            server("seed-a", 0L, realPing = false, isSeed = true),
            server("seed-b", 0L, realPing = false, isSeed = true),
        )
        assertEquals(setOf("seed-a", "seed-b"), Vpn5gSync.selectFreeGuids(servers))
    }

    @Test
    fun test_seed_sorts_after_real_pinged() {
        val servers = listOf(
            server("seed-a", 0L, realPing = false, isSeed = true),
            server("real", 900L),
            server("seed-b", 0L, realPing = false, isSeed = true),
        )
        val ordered = Vpn5gSync.freeEligibleSorted(servers)
        assertEquals(listOf("real", "seed-a", "seed-b"), ordered.map { it.guid })
    }

    @Test
    fun test_dead_seed_never_free() {
        // A seed the real probe failed (ping withdrawn to -1) loses
        // its seed trust at once.
        val servers = listOf(
            server("dead-seed", -1L, realPing = false, isSeed = true),
            server("live-seed", 0L, realPing = false, isSeed = true),
        )
        assertEquals(setOf("live-seed"), Vpn5gSync.selectFreeGuids(servers))
    }

    @Test
    fun test_tcp_only_non_seed_still_never_free() {
        // The seed exception is narrow: a TCP-only ping without seed
        // trust still buys nothing.
        val servers = listOf(
            server("fake-fast", 1L, realPing = false),
            server("seed", 0L, realPing = false, isSeed = true),
        )
        assertEquals(setOf("seed"), Vpn5gSync.selectFreeGuids(servers))
    }

    @Test
    fun test_seed_counts_in_every_tier() {
        val servers = (1..25).map {
            server("seed-$it", 0L, realPing = false, isSeed = true)
        }
        assertEquals(
            20,
            Vpn5gSync.freeGuidsFor(servers, Vpn5gSubscriptionStatus.Vpn5gTier.FREE).size,
        )
        assertEquals(
            25,
            Vpn5gSync.freeGuidsFor(servers, Vpn5gSubscriptionStatus.Vpn5gTier.GIFT).size,
        )
        assertEquals(
            25,
            Vpn5gSync.freeGuidsFor(servers, Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER).size,
        )
    }
    // -- v1.1.25: 20 free configs ready on entry ---------------------------
    // Aref's order: on entry every user must already have 20 free
    // configs ready. The bundled seed (32 build-time verified
    // entries) is that pool on a fresh install: seed servers are
    // free-eligible with an unknown ping, so the free tier is full
    // (20 pickable) before any live probe finishes.

    @Test
    fun test_seed_pool_gives_20_pickable_free_on_entry() {
        val servers = (1..32).map { i ->
            server(guid = "seed$i", pingMillis = 0L, realPing = false, isSeed = true)
        }
        assertEquals(20, Vpn5gSync.selectFreeGuids(servers).size)
        assertEquals(
            20,
            Vpn5gSync.freeGuidsFor(
                servers,
                Vpn5gSubscriptionStatus.Vpn5gTier.FREE,
            ).size,
        )
    }

    @Test
    fun test_seed_plus_real_pinged_still_caps_free_at_20() {
        val seeds = (1..32).map { i ->
            server(guid = "seed$i", pingMillis = 0L, realPing = false, isSeed = true)
        }
        val real = (1..10).map { i ->
            server(guid = "real$i", pingMillis = i * 10L, realPing = true)
        }
        val free = Vpn5gSync.selectFreeGuids(seeds + real)
        assertEquals(20, free.size)
        // Real-pinged servers sort first, so all 10 are inside the 20.
        assertTrue(free.containsAll((1..10).map { "real$it" }.toSet()))
    }

}
