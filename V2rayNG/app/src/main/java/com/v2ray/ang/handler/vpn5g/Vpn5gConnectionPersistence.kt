package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_PERSISTENT_CONNECTION
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.MAX_PERSISTENT_RECONNECT_ATTEMPTS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.PERSISTENT_RECONNECT_BASE_BACKOFF_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.PERSISTENT_RECONNECT_MAX_BACKOFF_MS

/**
 * Persistent connection ("اتصال پایدار", v1.1.12).
 *
 * The rule, from Aref: as long as the user has NOT disconnected the app
 * by hand, nothing else may cut the connection — except an in-app
 * update (after which the app reconnects itself). The mechanisms:
 *
 *  - the VPN service is a foreground service that returns START_STICKY
 *    (see CoreVpnService: a system restart with a null intent
 *    reconstructs the tunnel from the selected server);
 *  - the battery-optimization exemption (see Vpn5gBatteryOptimization)
 *    keeps cleaner / battery-saver apps from killing the service;
 *  - this flag records that a session is live: set when the service
 *    starts, cleared ONLY by a manual disconnect or by the 30-minute
 *    timer ending a free session. BootReceiver restarts the service
 *    after boot or an in-app update while it is set;
 *  - while the app process is alive, an unexpected stop re-runs the
 *    connect chain with exponential backoff ([shouldAutoReconnect] /
 *    [nextBackoffMs]), bounded so a dead network cannot loop forever.
 *    A manual disconnect cancels the pending reconnect immediately.
 *
 * Honest limit (also in VPN5G_CHANGES.md and the user-visible strings):
 * Android itself can still kill the service in extreme cases — the user
 * pressing Force Stop, or severe memory pressure. No app can prevent
 * the OS. What this file guarantees is that no OTHER app and no
 * ordinary system cleanup ends the session.
 */
object Vpn5gConnectionPersistence {

    /**
     * Whether an unexpected stop should re-run the connect chain.
     * Pure, JVM-tested. An unexpected stop is a live session that ended
     * without a manual disconnect, without the 30-minute timer ending
     * it, and without a tunnel-check stop (the tunnel-check stop has
     * its own FAILED state + retry button and must not be fought).
     */
    fun shouldAutoReconnect(
        wasConnected: Boolean,
        manualStopRequested: Boolean,
        timerProbablyEnded: Boolean,
        tunnelFailedStop: Boolean,
    ): Boolean {
        return wasConnected &&
            !manualStopRequested &&
            !timerProbablyEnded &&
            !tunnelFailedStop
    }

    /**
     * Backoff before reconnect attempt [attempt] (0-based): 2 s, 4 s,
     * 8 s, 16 s, then capped at 30 s. Pure, JVM-tested.
     */
    fun nextBackoffMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 4)
        val backoff = PERSISTENT_RECONNECT_BASE_BACKOFF_MS * (1L shl shift)
        return minOf(PERSISTENT_RECONNECT_MAX_BACKOFF_MS, backoff)
    }

    /** How many reconnect attempts run before giving up with a clear error. */
    fun maxAttempts(): Int = MAX_PERSISTENT_RECONNECT_ATTEMPTS

    /** True while a session is live and only a manual disconnect may end it. */
    fun isPersistentConnection(): Boolean =
        MmkvManager.decodeSettingsBool(KEY_PERSISTENT_CONNECTION, false)

    fun setPersistentConnection(value: Boolean) {
        MmkvManager.encodeSettings(KEY_PERSISTENT_CONNECTION, value)
    }
}
