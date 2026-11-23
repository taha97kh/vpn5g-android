package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
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
 * Live tunnel speed monitor (v1.1.14).
 *
 * Polls the core's per-outbound traffic counters about once a second
 * while the tunnel is running and exposes TUNNEL-ONLY (proxy) rates —
 * bypassed (direct) traffic is never counted. The counters are
 * cumulative, so the rate is the delta since the previous poll divided
 * by the elapsed time; when the counters are unavailable (core not
 * running) the speeds are zero and the UI shows dashes, never a
 * made-up number. The notification's own speed job
 * ([com.v2ray.ang.handler.NotificationManager]) renders the same
 * tunnel-only numbers with Persian labels.
 */
object Vpn5gSpeedMonitor {

    data class Speed(
        /** Download bytes per second through the tunnel. */
        val downBps: Long = 0L,
        /** Upload bytes per second through the tunnel. */
        val upBps: Long = 0L,
    )

    private const val POLL_INTERVAL_MS = 1_000L

    private val _speeds = MutableStateFlow(Speed())
    val speeds: StateFlow<Speed> = _speeds.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Starts the 1-second poll (idempotent). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var lastDown = -1L
            var lastUp = -1L
            var lastTime = 0L
            while (isActive) {
                try {
                    val (down, up) = readTunnelTotals()
                    val now = System.currentTimeMillis()
                    if (down >= 0 && up >= 0 && lastDown >= 0 && lastTime > 0) {
                        val elapsedSec = (now - lastTime) / 1000.0
                        if (elapsedSec > 0) {
                            _speeds.value = Speed(
                                downBps = ((down - lastDown) / elapsedSec)
                                    .toLong().coerceAtLeast(0L),
                                upBps = ((up - lastUp) / elapsedSec)
                                    .toLong().coerceAtLeast(0L),
                            )
                        }
                    } else if (down < 0) {
                        _speeds.value = Speed()
                    }
                    lastDown = down
                    lastUp = up
                    lastTime = now
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Vpn5gSpeedMonitor: poll failed", e)
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
     * Cumulative tunnel (proxy) totals, or (-1, -1) when the core is
     * not running. Direct (bypassed) traffic is excluded by tag.
     */
    private fun readTunnelTotals(): Pair<Long, Long> {
        var down = 0L
        var up = 0L
        var any = false
        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            if (stat.tag == AppConfig.TAG_DIRECT || stat.tag == AppConfig.TAG_BLOCKED) return@forEach
            any = true
            when (stat.direction) {
                AppConfig.UPLINK -> up += stat.value
                AppConfig.DOWNLINK -> down += stat.value
            }
        }
        if (!any && !CoreServiceManager.isRunning()) return -1L to -1L
        return down to up
    }
}
