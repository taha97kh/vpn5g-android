package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the crash-report formatting ([Vpn5gCrashHandler.formatReport]).
 * The report is the only evidence a connect crash leaves behind, so its
 * content is pinned: version, time, thread, exception + message, and at
 * most [Vpn5gCrashHandler.MAX_STACK_FRAMES] frames — never personal
 * data (no email, no device id, no proxy URI, no token).
 */
class Vpn5gCrashHandlerTest {

    @Test
    fun test_report_contains_version_thread_and_exception() {
        val report = Vpn5gCrashHandler.formatReport(
            versionName = "1.1.16",
            versionCode = 18,
            timestampMs = 1_795_413_090_000L,
            threadName = "main",
            throwable = RuntimeException("boom"),
        )
        assertTrue(report.contains("version: 1.1.16 (18)"))
        assertTrue(report.contains("thread: main"))
        assertTrue(report.contains("java.lang.RuntimeException: boom"))
        assertTrue(report.contains("time: "))
    }

    @Test
    fun test_report_caps_stack_frames_at_30() {
        val throwable = RuntimeException("deep")
        throwable.stackTrace = Array(40) { i ->
            StackTraceElement("com.example.Foo", "bar", "Foo.kt", i)
        }
        val report = Vpn5gCrashHandler.formatReport(
            versionName = "1.1.16",
            versionCode = 18,
            timestampMs = 0L,
            threadName = "main",
            throwable = throwable,
        )
        val frameLines = report.lines().count { it.trim().startsWith("at ") }
        assertEquals(Vpn5gCrashHandler.MAX_STACK_FRAMES, frameLines)
        // Frames 0..29 kept (line numbers), frame 30 dropped.
        assertTrue(report.contains("(Foo.kt:29)"))
        assertFalse(report.contains("(Foo.kt:30)"))
    }

    @Test
    fun test_report_includes_cause() {
        val cause = IllegalStateException("root")
        val throwable = RuntimeException("outer", cause)
        val report = Vpn5gCrashHandler.formatReport(
            versionName = "1.1.16",
            versionCode = 18,
            timestampMs = 0L,
            threadName = "main",
            throwable = throwable,
        )
        assertTrue(report.contains("caused by: java.lang.IllegalStateException: root"))
    }
}
