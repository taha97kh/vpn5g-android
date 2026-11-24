package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the free tier: the 20 fastest-pinging configs (pure Kotlin). */
class Vpn5gTopFreeTest {

    private fun server(guid: String, pingMillis: Long, realPing: Boolean = true): Vpn5gServerUi {
        return Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = "c",
            pingMillis = pingMillis,
            isSelected = false,
            // v1.1.7: only a real through-the-proxy ping counts.
            hasRealPing = realPing,
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

    // -- Standby pool (v1.1.17): servers waiting to switch must ALWAYS -----
    // be more than 10 — the free tier's real-verified pool, topped up
    // by extra probe batches until it reaches STANDBY_POOL_MIN.

    @Test
    fun test_standby_pool_min_is_more_than_10() {
        assertEquals(11, Vpn5gConstants.STANDBY_POOL_MIN)
        assertTrue(Vpn5gConstants.STANDBY_POOL_MIN > 10)
    }

    @Test
    fun test_free_pool_exceeds_standby_min_when_servers_verify() {
        // 20 verified servers: the free pool is 20 — well above the
        // 11-server standby minimum.
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
}
