package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.29 failover pick: relaxation tiers must
 * never strand a session — standby queue first, then measured
 * free servers, then any measured server, then unknown-ping free
 * servers, and failed-store members last (never excluded).
 */
class Vpn5gFailoverPickTest {

    private fun server(
        guid: String,
        ping: Long,
        free: Boolean = true,
        realPing: Boolean = true,
        country: String = "DE",
        dedupeKey: String = guid,
    ) = Vpn5gServerUi(
        guid = guid,
        name = guid,
        country = country,
        pingMillis = ping,
        isSelected = false,
        isFree = free,
        hasRealPing = realPing,
        dedupeKey = dedupeKey,
    )

    @Test
    fun test_standby_queue_comes_first_in_queue_order() {
        val servers = listOf(
            server("fast", 50),
            server("queued1", 300),
            server("queued2", 200),
        )
        val ranked = Vpn5gFailoverPick.ranked(
            servers, emptySet(), emptySet(),
            standbyQueue = listOf("queued1", "queued2"),
        )
        assertEquals(listOf("queued1", "queued2", "fast"), ranked.map { it.guid })
    }

    @Test
    fun test_excluded_never_picked() {
        val servers = listOf(server("dropped", 10), server("next", 100))
        val pick = Vpn5gFailoverPick.pick(servers, setOf("dropped"), emptySet())
        assertEquals("next", pick?.guid)
        assertNull(
            Vpn5gFailoverPick.pick(servers, setOf("dropped", "next"), emptySet())
        )
    }

    @Test
    fun test_free_measured_before_paid_measured() {
        val servers = listOf(
            server("paid", 10, free = false),
            server("free", 500, free = true),
        )
        val ranked = Vpn5gFailoverPick.ranked(servers, emptySet(), emptySet())
        assertEquals(listOf("free", "paid"), ranked.map { it.guid })
    }

    @Test
    fun test_unknown_ping_free_is_fallback_not_dead_end() {
        // The pre-1.1.29 dead end: every measured server is in the
        // failed store; only an unknown-ping free server remains.
        val servers = listOf(
            server("dead", 100),
            server("seed", 0),
        )
        val pick = Vpn5gFailoverPick.pick(servers, emptySet(), setOf("dead"))
        assertEquals("seed", pick?.guid)
    }

    @Test
    fun test_failed_store_members_come_last_not_never() {
        val servers = listOf(
            server("failed", 50),
            server("fresh", 900),
        )
        val ranked = Vpn5gFailoverPick.ranked(
            servers, emptySet(), setOf("failed"),
        )
        assertEquals(listOf("fresh", "failed"), ranked.map { it.guid })
        // And when ONLY failed-store members remain, one is still picked.
        val onlyFailed = Vpn5gFailoverPick.pick(
            listOf(server("failed", 50)), emptySet(), setOf("failed"),
        )
        assertEquals("failed", onlyFailed?.guid)
    }

    @Test
    fun test_real_ping_first_then_fastest() {
        val servers = listOf(
            server("tcpOnly", 10, realPing = false),
            server("realSlow", 400, realPing = true),
            server("realFast", 100, realPing = true),
        )
        val ranked = Vpn5gFailoverPick.ranked(servers, emptySet(), emptySet())
        assertEquals(listOf("realFast", "realSlow", "tcpOnly"), ranked.map { it.guid })
    }

    @Test
    fun test_health_score_breaks_ping_order() {
        val servers = listOf(
            server("a", 300, dedupeKey = "ka"),
            server("b", 250, dedupeKey = "kb"),
        )
        // A healthy score on "a" adjusts its ping below b's raw ping.
        val ranked = Vpn5gFailoverPick.ranked(
            servers, emptySet(), emptySet(),
            healthScores = mapOf("ka" to 5.0),
        )
        assertEquals(listOf("a", "b"), ranked.map { it.guid })
    }

    @Test
    fun test_pick_prefers_different_country() {
        val servers = listOf(
            server("sameCountry", 50, country = "DE"),
            server("otherCountry", 400, country = "NL"),
        )
        val pick = Vpn5gFailoverPick.pick(
            servers, emptySet(), emptySet(), differentCountryThan = "DE",
        )
        assertEquals("otherCountry", pick?.guid)
        // Falls back to the same country when nothing else is left.
        val fallback = Vpn5gFailoverPick.pick(
            listOf(server("sameCountry", 50, country = "DE")),
            emptySet(), emptySet(), differentCountryThan = "DE",
        )
        assertEquals("sameCountry", fallback?.guid)
    }

    @Test
    fun test_ranked_has_no_duplicates() {
        val servers = listOf(server("a", 100), server("b", 200))
        val ranked = Vpn5gFailoverPick.ranked(
            servers, emptySet(), emptySet(), standbyQueue = listOf("a", "b"),
        )
        assertEquals(listOf("a", "b"), ranked.map { it.guid })
    }
}
