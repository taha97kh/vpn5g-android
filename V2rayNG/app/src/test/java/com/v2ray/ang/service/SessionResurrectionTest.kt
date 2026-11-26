package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the session-resurrection decision
 * ([SessionResurrection.shouldResurrect], v1.1.32): a killed
 * daemon is restarted ONLY for a persistent session with no sign
 * of life (stale heartbeat AND no core service running). A fresh
 * heartbeat (AUTO mode watchdog alive) or a running service
 * (manual mode, where no heartbeat is written) both mean the
 * tunnel is fine and must not be touched; a non-persistent
 * session (explicit user disconnect) must never come back.
 */
class SessionResurrectionTest {

    @Test
    fun test_resurrect_dead_persistent_session() {
        assertTrue(
            SessionResurrection.shouldResurrect(
                isPersistentConnection = true,
                heartbeatFresh = false,
                serviceRunning = false,
            ),
        )
    }

    @Test
    fun test_no_resurrect_after_explicit_disconnect() {
        assertFalse(
            SessionResurrection.shouldResurrect(
                isPersistentConnection = false,
                heartbeatFresh = false,
                serviceRunning = false,
            ),
        )
    }

    @Test
    fun test_no_resurrect_while_heartbeat_fresh() {
        assertFalse(
            SessionResurrection.shouldResurrect(
                isPersistentConnection = true,
                heartbeatFresh = true,
                serviceRunning = false,
            ),
        )
    }

    @Test
    fun test_no_resurrect_while_service_running() {
        assertFalse(
            SessionResurrection.shouldResurrect(
                isPersistentConnection = true,
                heartbeatFresh = false,
                serviceRunning = true,
            ),
        )
    }
}
