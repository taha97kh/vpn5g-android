package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.29 leaky-bucket watchdog score: FAILED
 * accumulates, VERIFIED decays (never fully resets), UNKNOWN is a
 * capped soft failure that alone can never drop the tunnel.
 */
class Vpn5gWatchdogScoreTest {

    private val failed = Vpn5gTunnelCheck.Verdict.FAILED
    private val unknown = Vpn5gTunnelCheck.Verdict.UNKNOWN
    private val verified = Vpn5gTunnelCheck.Verdict.VERIFIED

    @Test
    fun test_single_failure_needs_confirm_but_no_drop() {
        val score = Vpn5gWatchdogScore.nextScore(0.0, failed)
        assertEquals(1.0, score)
        assertTrue(Vpn5gWatchdogScore.needsConfirm(score))
        assertFalse(Vpn5gWatchdogScore.shouldDrop(score))
    }

    @Test
    fun test_two_failures_drop() {
        var score = Vpn5gWatchdogScore.nextScore(0.0, failed)
        score = Vpn5gWatchdogScore.nextScore(score, failed)
        assertEquals(2.0, score)
        assertTrue(Vpn5gWatchdogScore.shouldDrop(score))
        assertFalse(Vpn5gWatchdogScore.needsConfirm(score))
    }

    @Test
    fun test_verified_decays_one_failure_not_whole_history() {
        // F, F would drop; F, V, F must NOT reset to a clean slate —
        // the score after F,V is 0, then F is 1 again (confirm, no drop).
        var score = Vpn5gWatchdogScore.nextScore(0.0, failed)
        score = Vpn5gWatchdogScore.nextScore(score, verified)
        assertEquals(0.0, score)
        score = Vpn5gWatchdogScore.nextScore(score, failed)
        assertEquals(1.0, score)
        assertFalse(Vpn5gWatchdogScore.shouldDrop(score))
    }

    @Test
    fun test_flapping_server_still_accumulates_to_drop() {
        // F, F is a drop; a flap pattern F,V,F,F: after F,V score 0,
        // F -> 1, F -> 2 drop. The old consecutive counter reset on
        // the single V and never dropped a 2-in-3 flapping server
        // when the V landed between the Fs — here F,F after one V
        // still drops, and F,V,F,V,F,F drops on the final F.
        var score = 0.0
        listOf(failed, verified, failed, verified, failed, failed).forEach {
            score = Vpn5gWatchdogScore.nextScore(score, it)
        }
        assertTrue(Vpn5gWatchdogScore.shouldDrop(score))
    }

    @Test
    fun test_unknown_alone_never_drops() {
        var score = 0.0
        repeat(20) { score = Vpn5gWatchdogScore.nextScore(score, unknown) }
        assertEquals(Vpn5gWatchdogScore.UNKNOWN_CAP, score)
        assertFalse(Vpn5gWatchdogScore.shouldDrop(score))
    }

    @Test
    fun test_unknown_soft_failure_plus_real_failure_drops() {
        var score = 0.0
        repeat(3) { score = Vpn5gWatchdogScore.nextScore(score, unknown) }
        assertEquals(1.5, score)
        score = Vpn5gWatchdogScore.nextScore(score, failed)
        assertTrue(Vpn5gWatchdogScore.shouldDrop(score))
    }

    @Test
    fun test_score_never_negative_and_never_above_drop() {
        assertEquals(0.0, Vpn5gWatchdogScore.nextScore(0.0, verified))
        assertEquals(2.0, Vpn5gWatchdogScore.nextScore(2.0, failed))
        assertEquals(0.0, Vpn5gWatchdogScore.nextScore(0.4, verified))
    }

    @Test
    fun test_needs_confirm_window() {
        assertFalse(Vpn5gWatchdogScore.needsConfirm(0.0))
        assertFalse(Vpn5gWatchdogScore.needsConfirm(0.5))
        assertTrue(Vpn5gWatchdogScore.needsConfirm(1.0))
        assertTrue(Vpn5gWatchdogScore.needsConfirm(1.5))
        assertFalse(Vpn5gWatchdogScore.needsConfirm(2.0))
    }
}
