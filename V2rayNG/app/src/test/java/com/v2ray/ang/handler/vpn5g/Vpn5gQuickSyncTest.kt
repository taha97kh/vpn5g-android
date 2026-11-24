package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.ui.vpn5g.Vpn5gHomeViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the quick first-screen refresh batch
 * ([Vpn5gSync.selectQuickCandidates]): the first screen must be
 * usable within 20 s, so the quick path real-probes only the fastest
 * few unproven candidates — never the whole pool.
 */
class Vpn5gQuickSyncTest {

    private fun candidate(guid: String, key: String) =
        Vpn5gRealPing.RealPingCandidate(guid, key)

    @Test
    fun test_quick_batch_caps_at_16_fastest_unproven() {
        val candidates = (1..20).map { i -> candidate("g$i", "k$i") }
        val delays = (1..20).associate { i -> "g$i" to (i * 10L) }
        val batch = Vpn5gSync.selectQuickCandidates(candidates, delays, emptySet())
        assertEquals(16, batch.size)
        assertEquals((1..16).map { "g$it" }, batch.map { it.guid })
    }

    @Test
    fun test_quick_batch_skips_already_proven() {
        val candidates = (1..5).map { i -> candidate("g$i", "k$i") }
        val delays = (1..5).associate { i -> "g$i" to (i * 10L) }
        val batch = Vpn5gSync.selectQuickCandidates(candidates, delays, setOf("k1", "k2"))
        assertEquals(listOf("g3", "g4", "g5"), batch.map { it.guid })
    }

    @Test
    fun test_quick_batch_empty_when_nothing_to_probe() {
        assertTrue(
            Vpn5gSync.selectQuickCandidates(emptyList(), emptyMap(), emptySet()).isEmpty()
        )
        val candidates = listOf(candidate("g1", "k1"))
        assertTrue(
            Vpn5gSync.selectQuickCandidates(
                candidates, mapOf("g1" to 10L), setOf("k1")
            ).isEmpty()
        )
    }

    @Test
    fun test_quick_total_budget_is_within_startup_deadline() {
        // The quick path (15 s) must fit inside the 20 s first-screen bound.
        assertTrue(Vpn5gSync.QUICK_TOTAL_BUDGET_MS <= Vpn5gHomeViewModel.STARTUP_DEADLINE_MS)
    }

    // -- Spread sample (v1.1.17) ------------------------------------------

    @Test
    fun test_spread_sample_returns_all_when_under_cap() {
        val items = (1..5).toList()
        assertEquals(items, Vpn5gSync.spreadSample(items, 150))
        assertEquals(items, Vpn5gSync.spreadSample(items, 5))
    }

    @Test
    fun test_spread_sample_covers_the_whole_list() {
        // 10 items, cap 4: evenly spaced — indices 0, 2, 5, 7.
        val items = (1..10).toList()
        assertEquals(listOf(1, 3, 6, 8), Vpn5gSync.spreadSample(items, 4))
        assertTrue(Vpn5gSync.spreadSample(emptyList<Int>(), 4).isEmpty())
    }

    // -- Standby top-up batch (v1.1.17) ------------------------------------

    @Test
    fun test_standby_topup_empty_when_pool_already_above_10() {
        val candidates = (1..20).map { i -> candidate("g$i", "k$i") }
        val delays = (1..20).associate { i -> "g$i" to (i * 10L) }
        // 11 verified already: the pool is big enough, no batch.
        assertTrue(
            Vpn5gSync.standbyTopUpBatch(candidates, delays, emptySet(), 11).isEmpty()
        )
        assertTrue(
            Vpn5gSync.standbyTopUpBatch(candidates, delays, emptySet(), 20).isEmpty()
        )
    }

    @Test
    fun test_standby_topup_returns_fastest_unproven_below_10() {
        val candidates = (1..30).map { i -> candidate("g$i", "k$i") }
        val delays = (1..30).associate { i -> "g$i" to (i * 10L) }
        // 5 verified, k1/k2 already proven: the batch is the fastest
        // unproven candidates (g3 first), up to one probe batch.
        val batch = Vpn5gSync.standbyTopUpBatch(
            candidates, delays, setOf("k1", "k2"), 5
        )
        assertEquals(24, batch.size)
        assertEquals("g3", batch.first().guid)
        assertTrue(batch.none { it.guid == "g1" || it.guid == "g2" })
    }

    @Test
    fun test_standby_topup_empty_when_nothing_left_to_probe() {
        val candidates = (1..5).map { i -> candidate("g$i", "k$i") }
        val delays = (1..5).associate { i -> "g$i" to (i * 10L) }
        assertTrue(
            Vpn5gSync.standbyTopUpBatch(
                candidates, delays, (1..5).map { "k$it" }.toSet(), 0
            ).isEmpty()
        )
    }
}
