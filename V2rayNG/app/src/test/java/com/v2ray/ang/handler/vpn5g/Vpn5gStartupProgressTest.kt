package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the pure startup-progress mapping ([Vpn5gStartupProgress]). */
class Vpn5gStartupProgressTest {

    @Test
    fun test_stages_are_monotonic() {
        val stages = Vpn5gStartupProgress.Stage.entries
        var previous = 0f
        stages.forEach { stage ->
            val atStart = Vpn5gStartupProgress.fraction(stage, 0f)
            val atEnd = Vpn5gStartupProgress.fraction(stage, 1f)
            assertTrue(atStart >= previous, "$stage starts below the previous stage end")
            assertTrue(atEnd >= atStart, "$stage does not increase within the stage")
            previous = atEnd
        }
        assertEquals(1f, Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.DONE, 1f))
        assertEquals(0f, Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.LOADING_CACHE, 0f))
    }

    @Test
    fun test_stage_boundaries() {
        // Cache 0–0.10, config 0.10–0.15, sources 0.15–0.45, parse
        // 0.45–0.55, TCP ping 0.55–0.80, real probe 0.80–0.95, saving
        // 0.95–1.00.
        assertEquals(
            0.10f,
            Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.LOADING_CACHE, 1f)
        )
        assertEquals(
            0.30f,
            Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.FETCH_SOURCES, 0.5f),
            0.001f
        )
        assertEquals(
            0.80f,
            Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.TCP_PING, 1f)
        )
    }

    @Test
    fun test_gate_holds_until_min_display_and_work_done() {
        // Work done instantly (cached list): the bar ramps with time
        // and reaches 100% only at the minimum-display boundary.
        assertEquals(0f, Vpn5gStartupProgress.gatedFraction(0f, 0L, workDone = true))
        assertEquals(
            0.5f,
            Vpn5gStartupProgress.gatedFraction(0.10f, 4_000L, workDone = true),
            0.001f,
        )
        assertEquals(
            Vpn5gStartupProgress.MAX_BEFORE_DONE,
            Vpn5gStartupProgress.gatedFraction(1f, 7_999L, workDone = true),
        )
        assertEquals(
            1f,
            Vpn5gStartupProgress.gatedFraction(1f, 8_000L, workDone = true),
        )
        // Work still running past the window: never 100% yet.
        assertEquals(
            Vpn5gStartupProgress.MAX_BEFORE_DONE,
            Vpn5gStartupProgress.gatedFraction(1f, 30_000L, workDone = false),
        )
        // Real progress ahead of the ramp wins.
        assertEquals(
            0.80f,
            Vpn5gStartupProgress.gatedFraction(0.80f, 1_000L, workDone = false),
            0.001f,
        )
    }

    @Test
    fun test_gate_is_monotonic_in_elapsed() {
        var previous = 0f
        var elapsed = 0L
        while (elapsed <= 12_000L) {
            val shown = Vpn5gStartupProgress.gatedFraction(0.10f, elapsed, workDone = true)
            assertTrue(shown >= previous, "gate went backwards at ${elapsed}ms")
            previous = shown
            elapsed += 250L
        }
        assertEquals(1f, previous)
    }

    @Test
    fun test_gate_open_needs_both_conditions() {
        assertTrue(!Vpn5gStartupProgress.gateOpen(8_000L, workDone = false))
        assertTrue(!Vpn5gStartupProgress.gateOpen(7_999L, workDone = true))
        assertTrue(Vpn5gStartupProgress.gateOpen(8_000L, workDone = true))
        assertTrue(Vpn5gStartupProgress.gateOpen(60_000L, workDone = true))
    }

    @Test
    fun test_within_is_clamped() {
        // Negative within clamps to the stage START (FETCH_CONFIG starts
        // at 0.10, not 0), over-range within clamps to the stage END.
        assertEquals(
            0.10f,
            Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.FETCH_CONFIG, -1f)
        )
        assertEquals(
            0.15f,
            Vpn5gStartupProgress.fraction(Vpn5gStartupProgress.Stage.FETCH_CONFIG, 2f)
        )
    }
}
