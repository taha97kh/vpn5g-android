package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the free tier: the 50 fastest-pinging configs (pure Kotlin). */
class Vpn5gTopFreeTest {

    private fun server(guid: String, pingMillis: Long): Vpn5gServerUi {
        return Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = "c",
            pingMillis = pingMillis,
            isSelected = false,
        )
    }

    @Test
    fun test_free_count_is_50() {
        assertEquals(50, Vpn5gConstants.FREE_SERVER_COUNT)
    }

    @Test
    fun test_fastest_50_win() {
        // 60 servers, pings 600..10 (shuffled order in the list).
        val servers = (1..60).shuffled().map { i ->
            server(guid = "g$i", pingMillis = i * 10L)
        }
        val free = Vpn5gSync.selectFreeGuids(servers)
        assertEquals(50, free.size)
        // The 50 fastest are pings 10..500 (i = 1..50).
        assertEquals((1..50).map { "g$it" }.toSet(), free)
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
    fun test_fewer_than_50_returns_all_with_ping() {
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
        // All 60 servers ping 10ms: sortedBy is stable, so the first 50
        // in list order win — the same 50 on every run.
        val servers = (1..60).map { server("g$it", 10L) }
        val free = Vpn5gSync.selectFreeGuids(servers)
        assertEquals((1..50).map { "g$it" }.toSet(), free)
    }

    @Test
    fun test_custom_count_respected() {
        val servers = (1..10).map { server("g$it", it * 10L) }
        assertEquals(setOf("g1", "g2", "g3"), Vpn5gSync.selectFreeGuids(servers, count = 3))
    }
}
