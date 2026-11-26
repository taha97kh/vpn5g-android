package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DAEMON_CORE_ALIVE_FRESH_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DAEMON_FAILOVER_FRESH_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DAEMON_HEARTBEAT_FRESH_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_DAEMON_CORE_ALIVE_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_DAEMON_FAILOVER_AT_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_DAEMON_WATCHDOG_HEARTBEAT_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_PENDING_FAILOVER_GUID

/**
 * Daemon-side failover candidate pick (v1.1.29).
 *
 * The daemon process has no UI state — no country groups, no
 * tier computation — so its pick works from what MMKV already
 * holds: the stored profiles of the VPN5G subscription group,
 * their stored test delays, the real-ping identity set, the
 * health scores and the 6-hour failed store. Ordering mirrors
 * the UI pick (real-pinged first, then health-adjusted delay),
 * with the same relaxation spirit: a server with no measured
 * delay still beats no server, and failed-store members come
 * last rather than never.
 *
 * [pickNext] is pure and JVM-tested; the daemon builds the
 * candidate list from MMKV and calls it.
 */
object Vpn5gDaemonPick {

    /** One daemon-side candidate, built from MMKV by the daemon. */
    data class Candidate(
        val guid: String,
        /** Stored test delay millis; <= 0 = unknown/never measured. */
        val delayMs: Long,
        /** True when the identity passed a real through-the-proxy ping. */
        val hasRealPing: Boolean,
        /** Health-adjusted delay used for ordering. */
        val adjustedDelayMs: Long,
        /**
         * ISO country code from the profile remark (v1.1.30), ""
         * when unknown. Drives the country-grouped order below —
         * the daemon exhausts one country before the next, in the
         * same priority as the UI pick.
         */
        val countryCode: String = "",
    )

    /**
     * The next daemon failover target, or null when every
     * candidate is excluded. [excluding] holds the dropped guid
     * plus everything already tried this failover; [failedGuids]
     * is the 6-hour failed store (deprioritized, not excluded —
     * the daemon must never strand a session for lack of a
     * candidate while the switch is ON).
     *
     * v1.1.30: within each tier the order is country-grouped
     * ([Vpn5gCountryGroups.priorityOf]: US → DE → CA →
     * sanction-bypassing → rest → unknown), then real-pinged
     * first, then health-adjusted delay — mirroring the UI pick.
     *
     * v1.1.33: the persisted standby queue ([standbyQueue], kept
     * fresh by the in-session liveness probes in BOTH processes)
     * is tier 0 — queue members that are not excluded and not in
     * the failed store, country-grouped within the queue (stable
     * sort keeps queue order inside one country), exactly like
     * the UI pick's tier 1. A just-probed-alive server beats a
     * stale stored delay; failed-store queue members fall through
     * to the tiers below.
     */
    fun pickNext(
        candidates: List<Candidate>,
        excluding: Set<String>,
        failedGuids: Set<String>,
        standbyQueue: List<String> = emptyList(),
    ): Candidate? {
        val eligible = candidates.filter { it.guid !in excluding }
        if (eligible.isEmpty()) return null
        val byGuid = eligible.associateBy { it.guid }
        val queued = standbyQueue.mapNotNull { byGuid[it] }
            .filter { it.guid !in failedGuids }
            .sortedBy { Vpn5gCountryGroups.priorityOf(it.countryCode) }
        if (queued.isNotEmpty()) return queued.first()
        val tiers = listOf(
            eligible.filter { it.delayMs > 0 && it.guid !in failedGuids },
            eligible.filter { it.delayMs > 0 },
            eligible.filter { it.guid !in failedGuids },
            eligible,
        )
        for (tier in tiers) {
            if (tier.isEmpty()) continue
            return tier.sortedWith(
                compareBy<Candidate>(
                    { Vpn5gCountryGroups.priorityOf(it.countryCode) },
                    { !it.hasRealPing },
                    { it.adjustedDelayMs },
                )
            ).first()
        }
        return null
    }
}

/**
 * Daemon↔UI watchdog coordination stamps (v1.1.29).
 *
 * The daemon writes a heartbeat on every watchdog probe and a
 * failover stamp when it starts a failover restart; the UI reads
 * both to decide whether the daemon owns drop detection. The
 * freshness decisions are pure and JVM-tested; the MMKV reads and
 * writes are the only storage touch points. MMKV is the proven
 * cross-process channel here (the daemon already publishes live
 * speeds through it, v1.1.15).
 */
object Vpn5gDaemonHeartbeat {

    /** True when [lastMs] is within [DAEMON_HEARTBEAT_FRESH_MS] of [nowMs]. Pure. */
    fun isHeartbeatFresh(lastMs: Long, nowMs: Long): Boolean =
        lastMs > 0L && nowMs - lastMs in 0..DAEMON_HEARTBEAT_FRESH_MS

    /** True when a daemon failover started within [DAEMON_FAILOVER_FRESH_MS]. Pure. */
    fun isFailoverFresh(atMs: Long, nowMs: Long): Boolean =
        atMs > 0L && nowMs - atMs in 0..DAEMON_FAILOVER_FRESH_MS

    fun writeHeartbeat(nowMs: Long = System.currentTimeMillis()) {
        MmkvManager.encodeSettings(KEY_DAEMON_WATCHDOG_HEARTBEAT_MS, nowMs)
    }

    fun heartbeatMs(): Long =
        MmkvManager.decodeSettingsLong(KEY_DAEMON_WATCHDOG_HEARTBEAT_MS, 0L)

    fun isHeartbeatFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        isHeartbeatFresh(heartbeatMs(), nowMs)

    fun writeFailoverStart(nowMs: Long = System.currentTimeMillis()) {
        MmkvManager.encodeSettings(KEY_DAEMON_FAILOVER_AT_MS, nowMs)
    }

    fun failoverAtMs(): Long =
        MmkvManager.decodeSettingsLong(KEY_DAEMON_FAILOVER_AT_MS, 0L)

    fun isFailoverFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        isFailoverFresh(failoverAtMs(), nowMs)

    /** True when [lastMs] is within [DAEMON_CORE_ALIVE_FRESH_MS] of [nowMs]. Pure. */
    fun isCoreAliveFresh(lastMs: Long, nowMs: Long): Boolean =
        lastMs > 0L && nowMs - lastMs in 0..DAEMON_CORE_ALIVE_FRESH_MS

    /**
     * Core-alive stamp (v1.1.33): written by the :daemon speed job
     * on every stats round while the core runs (every mode). The
     * session-resurrection backstop reads it to tell a live tunnel
     * from a stuck service whose core died — the watchdog
     * heartbeat above cannot serve that purpose (AUTO mode only,
     * and the UI stands its own watchdog down while it is fresh).
     */
    fun writeCoreAlive(nowMs: Long = System.currentTimeMillis()) {
        MmkvManager.encodeSettings(KEY_DAEMON_CORE_ALIVE_MS, nowMs)
    }

    fun coreAliveMs(): Long =
        MmkvManager.decodeSettingsLong(KEY_DAEMON_CORE_ALIVE_MS, 0L)

    fun isCoreAliveFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        isCoreAliveFresh(coreAliveMs(), nowMs)

    /** Records the guid a failover restart is heading to ("" clears). */
    fun writePendingFailoverGuid(guid: String) {
        MmkvManager.encodeSettings(KEY_PENDING_FAILOVER_GUID, guid)
    }

    fun pendingFailoverGuid(): String =
        MmkvManager.decodeSettingsString(KEY_PENDING_FAILOVER_GUID).orEmpty()

    fun clearPendingFailoverGuid() = writePendingFailoverGuid("")
}
