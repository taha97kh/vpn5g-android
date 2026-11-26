package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.29 daemon-side failover pick and the
 * daemon↔UI heartbeat/failover freshness decisions (pure parts).
 */
class Vpn5gDaemonPickTest {

    private fun candidate(
        guid: String,
        delay: Long,
        realPing: Boolean = true,
        adjusted: Long = delay,
        countryCode: String = "",
    ) = Vpn5gDaemonPick.Candidate(
        guid = guid,
        delayMs = delay,
        hasRealPing = realPing,
        adjustedDelayMs = adjusted,
        countryCode = countryCode,
    )

    @Test
    fun test_measured_not_failed_first() {
        val candidates = listOf(
            candidate("unknownDelay", 0),
            candidate("measured", 500),
            candidate("failedFast", 50),
        )
        val pick = Vpn5gDaemonPick.pickNext(
            candidates, emptySet(), setOf("failedFast"),
        )
        assertEquals("measured", pick?.guid)
    }

    @Test
    fun test_excluding_always_respected() {
        val candidates = listOf(candidate("a", 100), candidate("b", 200))
        assertEquals(
            "b",
            Vpn5gDaemonPick.pickNext(candidates, setOf("a"), emptySet())?.guid,
        )
        assertNull(
            Vpn5gDaemonPick.pickNext(candidates, setOf("a", "b"), emptySet())
        )
        assertNull(Vpn5gDaemonPick.pickNext(emptyList(), emptySet(), emptySet()))
    }

    @Test
    fun test_failed_store_deprioritized_not_excluded() {
        // Only failed-store candidates left: still picks one.
        val candidates = listOf(candidate("failed", 100))
        assertEquals(
            "failed",
            Vpn5gDaemonPick.pickNext(candidates, emptySet(), setOf("failed"))?.guid,
        )
        // Tier order: a measured (delay > 0) failed-store server
        // still beats a never-measured fresh one — a known-fast
        // server that failed hours ago is a better bet than an
        // unknown quantity.
        val mixed = listOf(
            candidate("failedMeasured", 50),
            candidate("freshUnknown", 0),
        )
        assertEquals(
            "failedMeasured",
            Vpn5gDaemonPick.pickNext(mixed, emptySet(), setOf("failedMeasured"))?.guid,
        )
        // With no measured candidate at all, not-failed wins.
        val unmeasured = listOf(
            candidate("failedUnknown", 0),
            candidate("freshUnknown", 0),
        )
        assertEquals(
            "freshUnknown",
            Vpn5gDaemonPick.pickNext(unmeasured, emptySet(), setOf("failedUnknown"))?.guid,
        )
    }

    @Test
    fun test_real_ping_first_then_adjusted_delay() {
        val candidates = listOf(
            candidate("tcpOnly", 10, realPing = false),
            candidate("realSlow", 400),
            candidate("realFast", 100),
        )
        assertEquals(
            "realFast",
            Vpn5gDaemonPick.pickNext(candidates, emptySet(), emptySet())?.guid,
        )
    }

    @Test
    fun test_heartbeat_freshness_window() {
        val now = 1_000_000L
        assertTrue(Vpn5gDaemonHeartbeat.isHeartbeatFresh(now, now))
        assertTrue(
            Vpn5gDaemonHeartbeat.isHeartbeatFresh(
                now - Vpn5gConstants.DAEMON_HEARTBEAT_FRESH_MS, now,
            )
        )
        assertFalse(
            Vpn5gDaemonHeartbeat.isHeartbeatFresh(
                now - Vpn5gConstants.DAEMON_HEARTBEAT_FRESH_MS - 1, now,
            )
        )
        assertFalse(Vpn5gDaemonHeartbeat.isHeartbeatFresh(0L, now))
        // A stamp from the future (clock skew) is not fresh.
        assertFalse(Vpn5gDaemonHeartbeat.isHeartbeatFresh(now + 1, now))
    }

    @Test
    fun test_failover_freshness_window() {
        val now = 2_000_000L
        assertTrue(Vpn5gDaemonHeartbeat.isFailoverFresh(now, now))
        assertTrue(
            Vpn5gDaemonHeartbeat.isFailoverFresh(
                now - Vpn5gConstants.DAEMON_FAILOVER_FRESH_MS, now,
            )
        )
        assertFalse(
            Vpn5gDaemonHeartbeat.isFailoverFresh(
                now - Vpn5gConstants.DAEMON_FAILOVER_FRESH_MS - 1, now,
            )
        )
        assertFalse(Vpn5gDaemonHeartbeat.isFailoverFresh(0L, now))
    }

    @Test
    fun test_country_grouped_order_us_de_ca() {
        // v1.1.30: country priority beats delay — the daemon
        // exhausts one country before the next (US → DE → CA),
        // mirroring the UI pick.
        val candidates = listOf(
            candidate("caFast", 50, countryCode = "CA"),
            candidate("deFast", 60, countryCode = "DE"),
            candidate("usSlow", 900, countryCode = "US"),
        )
        assertEquals(
            "usSlow",
            Vpn5gDaemonPick.pickNext(candidates, emptySet(), emptySet())?.guid,
        )
        assertEquals(
            "deFast",
            Vpn5gDaemonPick.pickNext(candidates, setOf("usSlow"), emptySet())?.guid,
        )
        assertEquals(
            "caFast",
            Vpn5gDaemonPick.pickNext(
                candidates, setOf("usSlow", "deFast"), emptySet()
            )?.guid,
        )
    }

    // v1.1.33: the persisted standby queue is tier 0 — a
    // just-probed-alive server beats a faster stale delay.
    @Test
    fun test_standby_queue_beats_stale_delay() {
        val candidates = listOf(
            candidate("staleFast", 50),
            candidate("queuedAlive", 800),
        )
        val pick = Vpn5gDaemonPick.pickNext(
            candidates, emptySet(), emptySet(),
            standbyQueue = listOf("queuedAlive"),
        )
        assertEquals("queuedAlive", pick?.guid)
    }

    @Test
    fun test_standby_queue_skips_failed_and_excluded() {
        val candidates = listOf(
            candidate("queuedFailed", 100),
            candidate("queuedAlive", 200),
            candidate("plain", 300),
        )
        // First queue member is in the failed store → second wins.
        assertEquals(
            "queuedAlive",
            Vpn5gDaemonPick.pickNext(
                candidates, emptySet(), setOf("queuedFailed"),
                standbyQueue = listOf("queuedFailed", "queuedAlive"),
            )?.guid,
        )
        // Both queue members unusable → falls through to the tiers.
        assertEquals(
            "plain",
            Vpn5gDaemonPick.pickNext(
                candidates, setOf("queuedAlive"), setOf("queuedFailed"),
                standbyQueue = listOf("queuedFailed", "queuedAlive"),
            )?.guid,
        )
        // Queue guid not among candidates → tiers still work.
        assertEquals(
            "plain",
            Vpn5gDaemonPick.pickNext(
                candidates, setOf("queuedAlive"), setOf("queuedFailed"),
                standbyQueue = listOf("gone"),
            )?.guid,
        )
    }

    @Test
    fun test_standby_queue_country_grouped_within_queue() {
        val candidates = listOf(
            candidate("deFirst", 100, countryCode = "DE"),
            candidate("usSecond", 900, countryCode = "US"),
        )
        // US exhausts before DE even when DE is earlier in the queue.
        assertEquals(
            "usSecond",
            Vpn5gDaemonPick.pickNext(
                candidates, emptySet(), emptySet(),
                standbyQueue = listOf("deFirst", "usSecond"),
            )?.guid,
        )
    }

    // v1.1.33: core-alive freshness (resurrection backstop signal).
    @Test
    fun test_core_alive_freshness() {
        assertTrue(Vpn5gDaemonHeartbeat.isCoreAliveFresh(1_000L, 2_000L))
        assertTrue(Vpn5gDaemonHeartbeat.isCoreAliveFresh(1_000L, 31_000L))
        assertFalse(Vpn5gDaemonHeartbeat.isCoreAliveFresh(1_000L, 31_001L))
        assertFalse(Vpn5gDaemonHeartbeat.isCoreAliveFresh(0L, 1_000L))
        assertFalse(Vpn5gDaemonHeartbeat.isCoreAliveFresh(5_000L, 4_000L))
    }
}
