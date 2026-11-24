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
    fun test_quick_batch_caps_at_12_fastest_unproven() {
        val candidates = (1..20).map { i -> candidate("g$i", "k$i") }
        val delays = (1..20).associate { i -> "g$i" to (i * 10L) }
        val batch = Vpn5gSync.selectQuickCandidates(candidates, delays, emptySet())
        assertEquals(12, batch.size)
        assertEquals((1..12).map { "g$it" }, batch.map { it.guid })
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
}
