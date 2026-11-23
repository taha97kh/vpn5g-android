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
