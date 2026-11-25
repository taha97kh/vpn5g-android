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
    fun test_quick_batch_caps_at_24_fastest_unproven() {
        // v1.1.18: 24 candidates (was 16) so the launch list can hold
        // the full 20 verified free servers.
        val candidates = (1..30).map { i -> candidate("g$i", "k$i") }
        val delays = (1..30).associate { i -> "g$i" to (i * 10L) }
        val batch = Vpn5gSync.selectQuickCandidates(candidates, delays, emptySet())
        assertEquals(24, batch.size)
        assertEquals((1..24).map { "g$it" }, batch.map { it.guid })
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
    fun test_standby_topup_empty_when_pool_already_at_20() {
        val candidates = (1..30).map { i -> candidate("g$i", "k$i") }
        val delays = (1..30).associate { i -> "g$i" to (i * 10L) }
        // v1.1.18: 20 verified already (was 11) — the pool is big
        // enough, no batch.
        assertTrue(
            Vpn5gSync.standbyTopUpBatch(candidates, delays, emptySet(), 20).isEmpty()
        )
        assertTrue(
            Vpn5gSync.standbyTopUpBatch(candidates, delays, emptySet(), 25).isEmpty()
        )
        // 19 verified: one short of the minimum — a batch is needed.
        assertTrue(
            Vpn5gSync.standbyTopUpBatch(candidates, delays, emptySet(), 19).isNotEmpty()
        )
    }

    @Test
    fun test_standby_topup_returns_fastest_unproven_below_20() {
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

    // -- v1.1.29 guid stability across republishes ---------------------------
    // publishLiveProfiles used to mint fresh UUIDs on every publish,
    // killing every in-memory guid (failover candidates, tried sets,
    // the selected server) -> «کانفیگ معتبر نیست». assignGuids must
    // reuse the existing guid for a known dedupeKey.

    @Test
    fun test_assign_guids_reuses_existing_guid_by_key() {
        var minted = 0
        val assigned = Vpn5gSync.assignGuids(
            keys = listOf("k1", "k2", "k3"),
            existingByKey = mapOf("k1" to "guid-1", "k3" to "guid-3"),
        ) { "new-${++minted}" }
        assertEquals(listOf("guid-1", "new-1", "guid-3"), assigned)
        assertEquals(1, minted)
    }

    @Test
    fun test_assign_guids_same_key_same_guid_within_one_publish() {
        var minted = 0
        val assigned = Vpn5gSync.assignGuids(
            keys = listOf("k1", "k1"),
            existingByKey = emptyMap(),
        ) { "new-${++minted}" }
        assertEquals(listOf("new-1", "new-1"), assigned)
        assertEquals(1, minted)
    }

    @Test
    fun test_assign_guids_empty_keys() {
        assertTrue(
            Vpn5gSync.assignGuids(emptyList(), mapOf("k" to "g")) { "x" }.isEmpty()
        )
    }
}
