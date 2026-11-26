package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM tests for the session-resurrection decision
 * ([SessionResurrection.resurrectAction], v1.1.33): the liveness
 * signal is the core-alive stamp (written by the daemon speed
 * job in every mode while the core answers), not the AUTO-only
 * watchdog heartbeat. A persistent session with a dead core is
 * STARTed when no service runs and RESTARTed when a stuck
 * service is up with a dead core (the manual-mode gap the 1.1.32
 * heartbeat decision could not see). A live core is never
 * touched; a non-persistent session (explicit user disconnect)
 * must never come back.
 */
class SessionResurrectionTest {

    @Test
    fun test_start_dead_persistent_session() {
        assertEquals(
            SessionResurrection.Action.START,
            SessionResurrection.resurrectAction(
                isPersistentConnection = true,
                coreAliveFresh = false,
                serviceRunning = false,
            ),
        )
    }

    @Test
    fun test_restart_stuck_service_with_dead_core() {
        assertEquals(
            SessionResurrection.Action.RESTART,
            SessionResurrection.resurrectAction(
                isPersistentConnection = true,
                coreAliveFresh = false,
                serviceRunning = true,
            ),
        )
    }

    @Test
    fun test_no_action_after_explicit_disconnect() {
        assertEquals(
            SessionResurrection.Action.NONE,
            SessionResurrection.resurrectAction(
                isPersistentConnection = false,
                coreAliveFresh = false,
                serviceRunning = false,
            ),
        )
        assertEquals(
            SessionResurrection.Action.NONE,
            SessionResurrection.resurrectAction(
                isPersistentConnection = false,
                coreAliveFresh = false,
                serviceRunning = true,
            ),
        )
    }

    @Test
    fun test_no_action_while_core_alive() {
        assertEquals(
            SessionResurrection.Action.NONE,
            SessionResurrection.resurrectAction(
                isPersistentConnection = true,
                coreAliveFresh = true,
                serviceRunning = true,
            ),
        )
        assertEquals(
            SessionResurrection.Action.NONE,
            SessionResurrection.resurrectAction(
                isPersistentConnection = true,
                coreAliveFresh = true,
                serviceRunning = false,
            ),
        )
    }
}
