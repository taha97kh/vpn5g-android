package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.24 health-score memory (pure Kotlin). */
class Vpn5gHealthScoreTest {

    private val now = 1_000_000_000L

    @Test
    fun test_record_success_and_failure() {
        var entries = Vpn5gHealthScore.record(emptyMap(), "k1", success = true, nowMs = now)
        assertEquals(1.0, entries.getValue("k1").score, 0.001)
        assertEquals(1, entries.getValue("k1").successes)
        assertEquals(0, entries.getValue("k1").failures)

        entries = Vpn5gHealthScore.record(entries, "k1", success = false, nowMs = now)
        // 1.0 + (-1.5) = -0.5 (failures weigh more).
        assertEquals(-0.5, entries.getValue("k1").score, 0.001)
        assertEquals(1, entries.getValue("k1").failures)
    }

    @Test
    fun test_record_blank_key_is_no_op() {
        val entries = Vpn5gHealthScore.record(emptyMap(), "  ", success = true, nowMs = now)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun test_score_clamped() {
        var entries = emptyMap<String, Vpn5gHealthScore.HealthEntry>()
        repeat(20) { entries = Vpn5gHealthScore.record(entries, "k", success = true, nowMs = now) }
        assertEquals(Vpn5gConstants.HEALTH_SCORE_MAX, entries.getValue("k").score, 0.001)
        repeat(20) { entries = Vpn5gHealthScore.record(entries, "k", success = false, nowMs = now) }
        assertEquals(Vpn5gConstants.HEALTH_SCORE_MIN, entries.getValue("k").score, 0.001)
    }

    @Test
    fun test_decay_halves_after_half_life() {
        val entry = Vpn5gHealthScore.HealthEntry(score = 4.0, updatedAtMs = now)
        val afterOneHalfLife = now + Vpn5gConstants.HEALTH_HALF_LIFE_MS
        assertEquals(2.0, Vpn5gHealthScore.effectiveScore(entry, afterOneHalfLife), 0.001)
        val afterTwo = now + 2 * Vpn5gConstants.HEALTH_HALF_LIFE_MS
        assertEquals(1.0, Vpn5gHealthScore.effectiveScore(entry, afterTwo), 0.001)
        // No decay at or before the update time.
        assertEquals(4.0, Vpn5gHealthScore.effectiveScore(entry, now), 0.001)
        assertEquals(4.0, Vpn5gHealthScore.effectiveScore(entry, now - 1_000L), 0.001)
    }

    @Test
    fun test_record_decays_before_adding() {
        val old = mapOf(
            "k" to Vpn5gHealthScore.HealthEntry(score = 4.0, successes = 4, updatedAtMs = now),
        )
        val later = now + Vpn5gConstants.HEALTH_HALF_LIFE_MS
        val entries = Vpn5gHealthScore.record(old, "k", success = true, nowMs = later)
        // Decayed 4.0 -> 2.0, then +1.0 = 3.0.
        assertEquals(3.0, entries.getValue("k").score, 0.001)
        assertEquals(5, entries.getValue("k").successes)
        assertEquals(later, entries.getValue("k").updatedAtMs)
    }

    @Test
    fun test_scores_at_decays_all() {
        val entries = mapOf(
            "a" to Vpn5gHealthScore.HealthEntry(score = 2.0, updatedAtMs = now),
            "b" to Vpn5gHealthScore.HealthEntry(score = -2.0, updatedAtMs = now),
        )
        val scores = Vpn5gHealthScore.scoresAt(entries, now + Vpn5gConstants.HEALTH_HALF_LIFE_MS)
        assertEquals(1.0, scores.getValue("a"), 0.001)
        assertEquals(-1.0, scores.getValue("b"), 0.001)
    }

    @Test
    fun test_adjusted_ping_bonus_and_penalty() {
        // Score 3 -> 120 ms bonus: 300 -> 180.
        assertEquals(180L, Vpn5gHealthScore.adjustedPing(300L, 3.0))
        // Score -3 -> 120 ms penalty: 300 -> 420.
        assertEquals(420L, Vpn5gHealthScore.adjustedPing(300L, -3.0))
        // No history: ping unchanged.
        assertEquals(300L, Vpn5gHealthScore.adjustedPing(300L, 0.0))
        // Never below 1 ms.
        assertEquals(1L, Vpn5gHealthScore.adjustedPing(50L, 5.0))
    }

    @Test
    fun test_prune_keeps_newest() {
        val entries = (1..(Vpn5gConstants.HEALTH_MAX_ENTRIES + 10)).associate { i ->
            "k$i" to Vpn5gHealthScore.HealthEntry(score = 1.0, updatedAtMs = now + i)
        }
        val pruned = Vpn5gHealthScore.prune(entries)
        assertEquals(Vpn5gConstants.HEALTH_MAX_ENTRIES, pruned.size)
        // The oldest-updated are dropped.
        assertTrue("k1" !in pruned)
        assertTrue("k${Vpn5gConstants.HEALTH_MAX_ENTRIES + 10}" in pruned)
    }
}
