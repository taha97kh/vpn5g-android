package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
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
 *
 * v1.1.32 adds [TaskRemovedRestart.chooseAlarmKind]: an exact
 * alarm whenever the platform allows it (no permission needed
 * before API 31; grant required from API 31), because the
 * v1.1.30 inexact alarm could be deferred indefinitely after a
 * recents swipe and its foreground-service start silently
 * dropped on Android 12+.
 */
class TaskRemovedRestartTest {

    @Test
    fun test_restart_only_while_persistent() {
        assertTrue(TaskRemovedRestart.shouldSchedule(isPersistentConnection = true))
        assertFalse(TaskRemovedRestart.shouldSchedule(isPersistentConnection = false))
    }

    @Test
    fun test_exact_alarm_before_api31_needs_no_grant() {
        assertEquals(
            TaskRemovedRestart.AlarmKind.EXACT,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 30, canScheduleExactAlarms = false),
        )
        assertEquals(
            TaskRemovedRestart.AlarmKind.EXACT,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 23, canScheduleExactAlarms = false),
        )
    }

    @Test
    fun test_exact_alarm_from_api31_needs_grant() {
        assertEquals(
            TaskRemovedRestart.AlarmKind.EXACT,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 31, canScheduleExactAlarms = true),
        )
        assertEquals(
            TaskRemovedRestart.AlarmKind.EXACT,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 34, canScheduleExactAlarms = true),
        )
        assertEquals(
            TaskRemovedRestart.AlarmKind.ALLOW_WHILE_IDLE,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 31, canScheduleExactAlarms = false),
        )
        assertEquals(
            TaskRemovedRestart.AlarmKind.ALLOW_WHILE_IDLE,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 34, canScheduleExactAlarms = false),
        )
    }

    @Test
    fun test_below_api23_falls_back() {
        assertEquals(
            TaskRemovedRestart.AlarmKind.ALLOW_WHILE_IDLE,
            TaskRemovedRestart.chooseAlarmKind(sdkInt = 22, canScheduleExactAlarms = true),
        )
    }
}
