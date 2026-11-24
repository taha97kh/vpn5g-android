package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_SPEED_DOWN_BPS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_SPEED_UPDATED_AT_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_SPEED_UP_BPS
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live tunnel speed monitor (v1.1.15 rewrite).
 *
 * ROOT CAUSE of the 1.1.14 "speeds always 0" bug — three parts, all
 * in how the numbers were produced, none in the UI row:
 *
 *  1. Config gate: the running core only gets a `stats` block when
 *     PREF_SPEED_ENABLED is true. That setting defaults to false and
 *     nothing in the VPN5G flow ever set it — so the core ran with NO
 *     stats block, `statsManager` was nil, and
 *     QueryAllOutboundTrafficStats() returned "". Every rate computed
 *     to 0. Fixed in Vpn5gHomeViewModel init: the VPN5G flow turns
 *     the setting on once (persisted in MMKV, read by the :daemon
 *     process when the config is built).
 *  2. Process boundary: this monitor runs in the MAIN process, but
 *     the core (and the native stats) live in the :daemon process. A
 *     per-process CoreServiceManager in the UI process never starts a
 *     core, so its query returned empty forever. The monitor can
 *     NEVER read the core from here — no UI-process core query may
 *     decide anything (service guide).
 *  3. Counter-stealing race (latent): the native query RESETS its
 *     counters on every call, so two pollers would split/steal each
 *     other's bytes — and the old monitor's delta math assumed
 *     cumulative counters, which is wrong under reset semantics.
 *
 * The fix: exactly ONE querier — the notification speed job in the
 * :daemon process ([com.v2ray.ang.handler.NotificationManager]) —
 * publishes the computed tunnel-only rates to MMKV once a second, and
 * this monitor only READS those values (MMKV is multi-process, so the
 * cross-process read works). No core query, no delta math, no second
 * querier. A stale publish (older than [STALE_AFTER_MS]) means the
 * tunnel is gone, and the UI shows dashes — never a made-up number.
 */
object Vpn5gSpeedMonitor {

    data class Speed(
        /** Download bytes per second through the tunnel. */
        val downBps: Long = 0L,
        /** Upload bytes per second through the tunnel. */
        val upBps: Long = 0L,
    )

    private const val POLL_INTERVAL_MS = 1_000L

    /** A publish older than this means the daemon stopped publishing. */
    const val STALE_AFTER_MS = 3_000L

    private val _speeds = MutableStateFlow(Speed())
    val speeds: StateFlow<Speed> = _speeds.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Starts the 1-second MMKV read (idempotent). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    _speeds.value = readMmkvSpeeds()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Vpn5gSpeedMonitor: read failed", e)
                    _speeds.value = Speed()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Stops the poll and resets the speeds to zero. */
    fun stop() {
        job?.cancel()
        job = null
        _speeds.value = Speed()
    }

    /**
     * Pure staleness rule (JVM-tested): no publish within the window
     * means no data — the UI shows dashes, never the last numbers.
     */
    internal fun isStale(updatedAtMs: Long, nowMs: Long): Boolean {
        if (updatedAtMs <= 0L) return true
        return nowMs - updatedAtMs > STALE_AFTER_MS
    }

    /** Tunnel-only rates as last published by the daemon, or zero when stale. */
    private fun readMmkvSpeeds(): Speed {
        val updatedAt = MmkvManager.decodeSettingsLong(KEY_SPEED_UPDATED_AT_MS, 0L)
        if (isStale(updatedAt, System.currentTimeMillis())) return Speed()
        return Speed(
            downBps = MmkvManager.decodeSettingsLong(KEY_SPEED_DOWN_BPS, 0L)
                .coerceAtLeast(0L),
            upBps = MmkvManager.decodeSettingsLong(KEY_SPEED_UP_BPS, 0L)
                .coerceAtLeast(0L),
        )
    }
}
