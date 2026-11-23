package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.7 real-ping probe batch selection (pure Kotlin). */
class Vpn5gRealPingTest {

    private fun candidate(guid: String, key: String) =
        Vpn5gRealPing.RealPingCandidate(guid = guid, dedupeKey = key)

    @Test
    fun test_fastest_unproven_win() {
        val candidates = listOf(
            candidate("g1", "k1"),
            candidate("g2", "k2"),
            candidate("g3", "k3"),
        )
        val delays = mapOf("g1" to 900L, "g2" to 10L, "g3" to 300L)
        val picked = Vpn5gRealPing.selectCandidates(candidates, delays, emptySet(), count = 2)
        assertEquals(listOf("g2", "g3"), picked.map { it.guid })
    }

    @Test
    fun test_already_proven_are_not_retested() {
        val candidates = listOf(
            candidate("g1", "k1"),
            candidate("g2", "k2"),
            candidate("g3", "k3"),
        )
        val delays = mapOf("g1" to 10L, "g2" to 20L, "g3" to 30L)
        val picked = Vpn5gRealPing.selectCandidates(
            candidates, delays, setOf("k1", "k2"), count = 24
        )
        assertEquals(listOf("g3"), picked.map { it.guid })
    }

    @Test
    fun test_empty_in_empty_out() {
        assertTrue(
            Vpn5gRealPing.selectCandidates(emptyList(), emptyMap(), emptySet()).isEmpty()
        )
    }

    @Test
    fun test_all_proven_returns_empty() {
        val candidates = listOf(candidate("g1", "k1"), candidate("g2", "k2"))
        val delays = mapOf("g1" to 10L, "g2" to 20L)
        assertTrue(
            Vpn5gRealPing.selectCandidates(
                candidates, delays, setOf("k1", "k2")
            ).isEmpty()
        )
    }

    @Test
    fun test_missing_delay_sorts_last() {
        val candidates = listOf(candidate("g1", "k1"), candidate("g2", "k2"))
        val delays = mapOf("g2" to 10L)
        val picked = Vpn5gRealPing.selectCandidates(candidates, delays, emptySet(), count = 2)
        assertEquals(listOf("g2", "g1"), picked.map { it.guid })
    }
}
