package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.WATCHDOG_DROP_SCORE

/**
 * Leaky-bucket watchdog failure score (v1.1.29).
 *
 * Before 1.1.29 the watchdog counted CONSECUTIVE failed probes and
 * any VERIFIED or UNKNOWN reset the count to zero — a flapping
 * server that answered one probe in three stayed "connected"
 * while unusable, and a clean death took ~60–81 s to detect
 * (20 s interval × 3). The score replaces the counter:
 *
 *  - FAILED adds [FAILED_WEIGHT] (1.0);
 *  - UNKNOWN adds [UNKNOWN_WEIGHT] (0.5) but the UNKNOWN-only
 *    score is capped at [UNKNOWN_CAP] (1.5), so UNKNOWN alone can
 *    never drop a tunnel — UNKNOWN means the check could not run
 *    (no local proxy port), and both IP APIs being down yields
 *    FAILED, not UNKNOWN, so this cap keeps the false-drop safety
 *    the old reset had while still letting repeated UNKNOWNs
 *    count as a soft failure next to a real FAILED;
 *  - VERIFIED decays [VERIFIED_DECAY] (1.0) — one success forgives
 *    one failure, not the whole history, so a flapping server
 *    still accumulates toward a drop.
 *
 * At [WATCHDOG_DROP_SCORE] (2.0) the tunnel drops. When the score
 * first reaches [CONFIRM_SCORE] (1.0) the caller runs a confirm
 * probe after a short delay instead of waiting for the next tick,
 * which is what brings worst-case detection to ~13 s.
 *
 * Pure, JVM-tested.
 */
object Vpn5gWatchdogScore {

    const val FAILED_WEIGHT = 1.0
    const val UNKNOWN_WEIGHT = 0.5
    const val UNKNOWN_CAP = 1.5
    const val VERIFIED_DECAY = 1.0
    /** Score at which a confirm probe should run immediately. */
    const val CONFIRM_SCORE = 1.0

    /** Next score after one probe verdict. Never negative, never above the drop score. */
    fun nextScore(current: Double, verdict: Vpn5gTunnelCheck.Verdict): Double {
        val next = when (verdict) {
            Vpn5gTunnelCheck.Verdict.FAILED -> current + FAILED_WEIGHT
            Vpn5gTunnelCheck.Verdict.UNKNOWN ->
                minOf(current + UNKNOWN_WEIGHT, UNKNOWN_CAP)
            Vpn5gTunnelCheck.Verdict.VERIFIED -> current - VERIFIED_DECAY
        }
        return next.coerceIn(0.0, WATCHDOG_DROP_SCORE)
    }

    /** True when the score says the tunnel is dead and must drop. */
    fun shouldDrop(score: Double): Boolean = score >= WATCHDOG_DROP_SCORE

    /**
     * True when a first failure landed and a confirm probe should
     * run right away (after the confirm delay) instead of waiting
     * for the next scheduled tick.
     */
    fun needsConfirm(score: Double): Boolean =
        score >= CONFIRM_SCORE && !shouldDrop(score)
}
