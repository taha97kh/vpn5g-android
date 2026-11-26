package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.29 failover pick: relaxation tiers must
 * never strand a session — standby queue first, then measured
 * free servers, then any measured server, then unknown-ping free
 * servers, and failed-store members last (never excluded).
 *
 * v1.1.30: within each tier the order is country-grouped (US →
 * DE → CA → sanction-bypassing → rest → unknown) — the chain
 * exhausts one country before jumping to the next.
 */
class Vpn5gFailoverPickTest {

    private fun server(
        guid: String,
        ping: Long,
        free: Boolean = true,
        realPing: Boolean = true,
        country: String = "DE",
        countryCode: String = "",
        dedupeKey: String = guid,
    ) = Vpn5gServerUi(
        guid = guid,
        name = guid,
        country = country,
        pingMillis = ping,
        isSelected = false,
        isFree = free,
        hasRealPing = realPing,
        countryCode = countryCode,
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
    fun test_country_grouped_order_exhausts_one_country_first() {
        // v1.1.30 (Aref's order): country priority beats raw ping —
        // US first, then ALL German servers, then Canada — even
        // though the German servers are faster than the US one.
        val servers = listOf(
            server("ca1", 100, countryCode = "CA"),
            server("deSlow", 900, countryCode = "DE"),
            server("us1", 400, countryCode = "US"),
            server("deFast", 50, countryCode = "DE"),
        )
        val ranked = Vpn5gFailoverPick.ranked(servers, emptySet(), emptySet())
        assertEquals(
            listOf("us1", "deFast", "deSlow", "ca1"),
            ranked.map { it.guid },
        )
        // The pick follows the grouped order: US first; with the US
        // server excluded, the German country is exhausted before
        // Canada is tried.
        assertEquals(
            "us1",
            Vpn5gFailoverPick.pick(servers, emptySet(), emptySet())?.guid,
        )
        assertEquals(
            "deFast",
            Vpn5gFailoverPick.pick(servers, setOf("us1"), emptySet())?.guid,
        )
        assertEquals(
            "deSlow",
            Vpn5gFailoverPick.pick(
                servers, setOf("us1", "deFast"), emptySet()
            )?.guid,
        )
        assertEquals(
            "ca1",
            Vpn5gFailoverPick.pick(
                servers, setOf("us1", "deFast", "deSlow"), emptySet()
            )?.guid,
        )
    }

    @Test
    fun test_country_priority_sanction_bypass_before_rest() {
        // NL is sanction-bypassing (priority 3); IR is not (4);
        // unknown country code is last (5).
        val servers = listOf(
            server("unknown", 10, countryCode = ""),
            server("iran", 20, countryCode = "IR"),
            server("netherlands", 900, countryCode = "NL"),
        )
        val ranked = Vpn5gFailoverPick.ranked(servers, emptySet(), emptySet())
        assertEquals(
            listOf("netherlands", "iran", "unknown"),
            ranked.map { it.guid },
        )
    }

    @Test
    fun test_standby_queue_grouped_by_country_stable_within_country() {
        val servers = listOf(
            server("de1", 100, countryCode = "DE"),
            server("us1", 100, countryCode = "US"),
            server("de2", 100, countryCode = "DE"),
        )
        val ranked = Vpn5gFailoverPick.ranked(
            servers, emptySet(), emptySet(),
            standbyQueue = listOf("de1", "us1", "de2"),
        )
        // US jumps ahead of the German pair; queue order is kept
        // inside Germany.
        assertEquals(listOf("us1", "de1", "de2"), ranked.map { it.guid })
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
