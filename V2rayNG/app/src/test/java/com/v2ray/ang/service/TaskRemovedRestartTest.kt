package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the recents-removal survival decision
 * ([TaskRemovedRestart.shouldSchedule], v1.1.30): the tunnel
 * restarts itself after a recents swipe ONLY while a persistent
 * session is on — after an explicit disconnect nothing is
 * scheduled, so the app stays closed as the exit confirmation
 * promises.
 */
class TaskRemovedRestartTest {

    @Test
    fun test_restart_only_while_persistent() {
        assertTrue(TaskRemovedRestart.shouldSchedule(isPersistentConnection = true))
        assertFalse(TaskRemovedRestart.shouldSchedule(isPersistentConnection = false))
    }
}
