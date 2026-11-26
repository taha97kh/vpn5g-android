package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the pure restart-wait decision (v1.1.13) and the
 * pure self-heal decision (v1.1.34, recents-removal zombie fix).
 */
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

    // -- v1.1.34 self-heal (zombie fix) ------------------------------------

    @Test
    fun test_self_heal_answered_core_never_acts() {
        assertEquals(
            SelfHealAction.NONE,
            selfHealAction(
                isRunning = true, coreAnswered = true,
                consecutiveMisses = 99, isReloading = false,
                stopping = false, hasVpnInterface = true,
            ),
        )
        assertEquals(
            SelfHealAction.NONE,
            selfHealAction(
                isRunning = false, coreAnswered = true,
                consecutiveMisses = 99, isReloading = false,
                stopping = false, hasVpnInterface = true,
            ),
        )
    }

    @Test
    fun test_self_heal_waits_for_threshold() {
        // Fewer than SELF_HEAL_MISS_ROUNDS silent rounds: no action
        // (a restart in flight legitimately has silent rounds).
        assertEquals(
            SelfHealAction.NONE,
            selfHealAction(
                isRunning = true, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS - 1,
                isReloading = false, stopping = false,
                hasVpnInterface = true,
            ),
        )
    }

    @Test
    fun test_self_heal_reloads_wedged_running_core() {
        assertEquals(
            SelfHealAction.RELOAD,
            selfHealAction(
                isRunning = true, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS,
                isReloading = false, stopping = false,
                hasVpnInterface = true,
            ),
        )
        assertEquals(
            SelfHealAction.RELOAD,
            selfHealAction(
                isRunning = true, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS + 5,
                isReloading = false, stopping = false,
                hasVpnInterface = true,
            ),
        )
    }

    @Test
    fun test_self_heal_starts_dead_core_on_held_tun() {
        // Core dead but the service still holds the TUN (the
        // recents-removal zombie): start it in place.
        assertEquals(
            SelfHealAction.START,
            selfHealAction(
                isRunning = false, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS,
                isReloading = false, stopping = false,
                hasVpnInterface = true,
            ),
        )
        // No TUN to restart onto: the resurrection backstop owns it.
        assertEquals(
            SelfHealAction.NONE,
            selfHealAction(
                isRunning = false, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS,
                isReloading = false, stopping = false,
                hasVpnInterface = false,
            ),
        )
    }

    @Test
    fun test_self_heal_never_fights_stop_or_reload() {
        // Intentional stop in progress.
        assertEquals(
            SelfHealAction.NONE,
            selfHealAction(
                isRunning = true, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS + 10,
                isReloading = false, stopping = true,
                hasVpnInterface = true,
            ),
        )
        // A reload is already in flight.
        assertEquals(
            SelfHealAction.NONE,
            selfHealAction(
                isRunning = false, coreAnswered = false,
                consecutiveMisses = SELF_HEAL_MISS_ROUNDS + 10,
                isReloading = true, stopping = false,
                hasVpnInterface = true,
            ),
        )
    }
}
