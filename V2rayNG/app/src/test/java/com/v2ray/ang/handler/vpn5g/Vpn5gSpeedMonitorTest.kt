package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.15 speed-monitor staleness rule (pure Kotlin):
 * a publish older than 3 s means the daemon stopped publishing, and the
 * home shows dashes — never a made-up number. The MMKV read itself is a
 * device concern (the daemon publishes, the UI reads); it is NOT
 * unit-tested here. */
class Vpn5gSpeedMonitorTest {

    @Test
    fun test_fresh_publish_is_not_stale() {
        assertFalse(Vpn5gSpeedMonitor.isStale(10_000L, 10_000L))
        assertFalse(Vpn5gSpeedMonitor.isStale(10_000L, 11_000L))
        // Exactly at the 3 s boundary: still fresh.
        assertFalse(Vpn5gSpeedMonitor.isStale(10_000L, 13_000L))
    }

    @Test
    fun test_old_publish_is_stale() {
        assertTrue(Vpn5gSpeedMonitor.isStale(10_000L, 13_001L))
        assertTrue(Vpn5gSpeedMonitor.isStale(10_000L, 20_000L))
    }

    @Test
    fun test_no_publish_is_stale() {
        assertTrue(Vpn5gSpeedMonitor.isStale(0L, 10_000L))
        assertTrue(Vpn5gSpeedMonitor.isStale(-1L, 10_000L))
    }

    @Test
    fun test_future_publish_is_not_stale() {
        // A publish timestamped slightly ahead (clock skew between the
        // two processes) must not read as stale.
        assertFalse(Vpn5gSpeedMonitor.isStale(11_000L, 10_000L))
    }
}
