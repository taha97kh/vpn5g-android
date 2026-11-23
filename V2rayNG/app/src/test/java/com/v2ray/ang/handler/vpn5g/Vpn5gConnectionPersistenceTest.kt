package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.12 persistent-connection decisions (pure Kotlin). */
class Vpn5gConnectionPersistenceTest {

    @Test
    fun test_unexpected_stop_reconnects() {
        assertTrue(
            Vpn5gConnectionPersistence.shouldAutoReconnect(
                wasConnected = true,
                manualStopRequested = false,
                timerProbablyEnded = false,
                tunnelFailedStop = false,
            )
        )
    }

    @Test
    fun test_manual_stop_timer_and_tunnel_stop_never_reconnect() {
        assertFalse(
            Vpn5gConnectionPersistence.shouldAutoReconnect(
                wasConnected = true,
                manualStopRequested = true,
                timerProbablyEnded = false,
                tunnelFailedStop = false,
            )
        )
        assertFalse(
            Vpn5gConnectionPersistence.shouldAutoReconnect(
                wasConnected = true,
                manualStopRequested = false,
                timerProbablyEnded = true,
                tunnelFailedStop = false,
            )
        )
        assertFalse(
            Vpn5gConnectionPersistence.shouldAutoReconnect(
                wasConnected = true,
                manualStopRequested = false,
                timerProbablyEnded = false,
                tunnelFailedStop = true,
            )
        )
        // No live session at all: nothing to reconnect.
        assertFalse(
            Vpn5gConnectionPersistence.shouldAutoReconnect(
                wasConnected = false,
                manualStopRequested = false,
                timerProbablyEnded = false,
                tunnelFailedStop = false,
            )
        )
    }

    @Test
    fun test_backoff_doubles_then_caps() {
        assertEquals(2_000L, Vpn5gConnectionPersistence.nextBackoffMs(0))
        assertEquals(4_000L, Vpn5gConnectionPersistence.nextBackoffMs(1))
        assertEquals(8_000L, Vpn5gConnectionPersistence.nextBackoffMs(2))
        assertEquals(16_000L, Vpn5gConnectionPersistence.nextBackoffMs(3))
        assertEquals(30_000L, Vpn5gConnectionPersistence.nextBackoffMs(4))
        assertEquals(30_000L, Vpn5gConnectionPersistence.nextBackoffMs(99))
        assertEquals(2_000L, Vpn5gConnectionPersistence.nextBackoffMs(-1))
    }

    @Test
    fun test_max_attempts() {
        assertEquals(5, Vpn5gConnectionPersistence.maxAttempts())
    }
}
