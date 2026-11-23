package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the pure restart-wait decision (v1.1.13). */
class CoreServiceManagerTest {

    @Test
    fun test_running_before_deadline_keeps_waiting() {
        assertTrue(shouldKeepWaitingForCoreStop(true, nowMs = 1_000L, deadlineMs = 3_000L))
    }

    @Test
    fun test_stopped_core_ends_wait_at_once() {
        assertFalse(shouldKeepWaitingForCoreStop(false, nowMs = 1_000L, deadlineMs = 3_000L))
    }

    @Test
    fun test_deadline_expiry_ends_wait_even_while_running() {
        assertFalse(shouldKeepWaitingForCoreStop(true, nowMs = 3_000L, deadlineMs = 3_000L))
        assertFalse(shouldKeepWaitingForCoreStop(true, nowMs = 3_001L, deadlineMs = 3_000L))
    }

    @Test
    fun test_stopped_core_after_deadline_ends_wait() {
        assertFalse(shouldKeepWaitingForCoreStop(false, nowMs = 9_999L, deadlineMs = 3_000L))
    }
}
