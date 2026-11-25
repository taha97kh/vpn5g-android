package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The eleven states the home screen ever shows (v1.1.5 §2 — the seven
 * user-visible connect states from the spec, plus IDLE, CONNECTED, the
 * transient RETRYING, and DISCONNECTING v1.1.18). The home text comes
 * from these states only, and the precise failure reason lives in the
 * ViewModel's lastError, never in a technical error string.
 *
 * CONNECTED is reachable only from VERIFYING_TUNNEL on VERIFIED — never
 * from "the service is running" or "the ping came back". The service
 * running is not a tunnel; a ping is not a tunnel.
 */
enum class Vpn5gConnectState {
    IDLE,
    CHECKING_INTERNET,
    FINDING_SERVER,
    CONNECTING,
    CREATING_TUNNEL,
    /** Tunnel up; the exit-IP / DNS check from inside the VPN runs here. */
    VERIFYING_TUNNEL,
    /** Intentional restart to the next server (failover / manual switch). */
    SWITCHING_SERVER,
    CONNECTED,
    RETRYING,
    /** The chain gave up: precise error shown, still Disconnected. */
    FAILED,
    /**
     * The user asked to disconnect and the daemon is still stopping
     * (v1.1.18): the button must not flip to "Disconnected" instantly
     * while the service may still be running. Lands in IDLE when the
     * service reports stopped, or after [Vpn5gAutoConnect.DISCONNECT_TIMEOUT_MS]
     * if it never does. A restart racing the disconnect (server tap)
     * moves it back to CREATING_TUNNEL on SERVICE_STARTED.
     */
    DISCONNECTING,
}

enum class Vpn5gConnectEvent {
    START,
    INTERNET_OK,
    INTERNET_FAILED,
    SERVER_FOUND,
    SERVER_MISSING,
    SERVICE_STARTED,
    SERVICE_FAILED,
    /** The daemon is up; tunnel verification (exit IP + DNS) starts. */
    TUNNEL_READY,
    /** An intentional restart (server switch / failover): re-verify. */
    RESTARTING,
    /** The chain is moving to the next server after a failed check. */
    SWITCHING,
    VERIFIED,
    VERIFY_FAILED,
    GIVE_UP,
    USER_DISCONNECT,
}

/** Manual vs automatic server selection (v1.1.5 §3). */
enum class Vpn5gConnectMode { AUTO, MANUAL }

object Vpn5gAutoConnect {

    /**
     * Fallback to IDLE when the service never reports stopped after a
     * user disconnect (v1.1.18): the stop broadcast can be lost if the
     * process dies mid-stop, and the home must never sit in
     * DISCONNECTING forever. 5 s is long past a normal stop (~1 s)
     * and short enough that a stuck button is never mistaken for a
     * live tunnel.
     */
    const val DISCONNECT_TIMEOUT_MS = 5_000L

    /**
     * Pure state machine. An event that makes no sense in the current state
     * leaves the state unchanged — except USER_DISCONNECT, which lands in
     * DISCONNECTING from any live state (IDLE / FAILED / DISCONNECTING
     * stay), and GIVE_UP, which lands in FAILED (the precise error is
     * shown and the app stays Disconnected) except from CONNECTED, where
     * a stop is a clean end (30-minute timer / service stop) and lands in
     * IDLE, and from DISCONNECTING, where the stop is still in flight
     * and the timeout resolves it. CONNECTED is only reachable from
     * VERIFYING_TUNNEL on VERIFIED.
     */
    fun transition(from: Vpn5gConnectState, event: Vpn5gConnectEvent): Vpn5gConnectState {
        if (event == Vpn5gConnectEvent.USER_DISCONNECT) {
            // v1.1.18: the button flips to DISCONNECTING, not IDLE —
            // the daemon may still be stopping. From IDLE / FAILED
            // there is nothing to stop, and a repeated tap while
            // already disconnecting stays there.
            return when (from) {
                Vpn5gConnectState.IDLE,
                Vpn5gConnectState.FAILED,
                Vpn5gConnectState.DISCONNECTING -> from
                else -> Vpn5gConnectState.DISCONNECTING
            }
        }
        if (event == Vpn5gConnectEvent.GIVE_UP) {
            return when (from) {
                Vpn5gConnectState.IDLE -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.CONNECTED -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.FAILED -> Vpn5gConnectState.FAILED
                Vpn5gConnectState.DISCONNECTING -> Vpn5gConnectState.DISCONNECTING
                else -> Vpn5gConnectState.FAILED
            }
        }
        return when (from) {
            Vpn5gConnectState.IDLE -> when (event) {
                Vpn5gConnectEvent.START -> Vpn5gConnectState.CHECKING_INTERNET
                else -> from
            }
            Vpn5gConnectState.CHECKING_INTERNET -> when (event) {
                Vpn5gConnectEvent.INTERNET_OK -> Vpn5gConnectState.FINDING_SERVER
                Vpn5gConnectEvent.INTERNET_FAILED -> Vpn5gConnectState.RETRYING
                else -> from
            }
            Vpn5gConnectState.FINDING_SERVER -> when (event) {
                Vpn5gConnectEvent.SERVER_FOUND -> Vpn5gConnectState.CONNECTING
                Vpn5gConnectEvent.SERVER_MISSING -> Vpn5gConnectState.RETRYING
                else -> from
            }
            Vpn5gConnectState.CONNECTING -> when (event) {
                Vpn5gConnectEvent.SERVICE_STARTED -> Vpn5gConnectState.CREATING_TUNNEL
                Vpn5gConnectEvent.SERVICE_FAILED -> Vpn5gConnectState.RETRYING
                Vpn5gConnectEvent.RESTARTING,
                Vpn5gConnectEvent.SWITCHING -> Vpn5gConnectState.SWITCHING_SERVER
                else -> from
            }
            Vpn5gConnectState.CREATING_TUNNEL -> when (event) {
                Vpn5gConnectEvent.TUNNEL_READY -> Vpn5gConnectState.VERIFYING_TUNNEL
                Vpn5gConnectEvent.VERIFY_FAILED -> Vpn5gConnectState.SWITCHING_SERVER
                Vpn5gConnectEvent.RESTARTING,
                Vpn5gConnectEvent.SWITCHING -> Vpn5gConnectState.SWITCHING_SERVER
                else -> from
            }
            Vpn5gConnectState.VERIFYING_TUNNEL -> when (event) {
                Vpn5gConnectEvent.VERIFIED -> Vpn5gConnectState.CONNECTED
                Vpn5gConnectEvent.VERIFY_FAILED -> Vpn5gConnectState.SWITCHING_SERVER
                Vpn5gConnectEvent.TUNNEL_READY -> from
                Vpn5gConnectEvent.RESTARTING,
                Vpn5gConnectEvent.SWITCHING -> Vpn5gConnectState.SWITCHING_SERVER
                else -> from
            }
            Vpn5gConnectState.SWITCHING_SERVER -> when (event) {
                Vpn5gConnectEvent.SERVICE_STARTED -> Vpn5gConnectState.CREATING_TUNNEL
                Vpn5gConnectEvent.SERVICE_FAILED -> Vpn5gConnectState.RETRYING
                Vpn5gConnectEvent.RESTARTING,
                Vpn5gConnectEvent.SWITCHING,
                Vpn5gConnectEvent.VERIFY_FAILED -> from
                else -> from
            }
            Vpn5gConnectState.CONNECTED -> when (event) {
                Vpn5gConnectEvent.VERIFY_FAILED -> Vpn5gConnectState.RETRYING
                Vpn5gConnectEvent.RESTARTING,
                Vpn5gConnectEvent.SWITCHING -> Vpn5gConnectState.SWITCHING_SERVER
                else -> from
            }
            Vpn5gConnectState.RETRYING -> when (event) {
                Vpn5gConnectEvent.START -> Vpn5gConnectState.CHECKING_INTERNET
                Vpn5gConnectEvent.SERVICE_STARTED -> Vpn5gConnectState.CREATING_TUNNEL
                Vpn5gConnectEvent.SERVICE_FAILED -> Vpn5gConnectState.RETRYING
                Vpn5gConnectEvent.RESTARTING,
                Vpn5gConnectEvent.SWITCHING -> Vpn5gConnectState.SWITCHING_SERVER
                else -> from
            }
            Vpn5gConnectState.FAILED -> when (event) {
                Vpn5gConnectEvent.START -> Vpn5gConnectState.CHECKING_INTERNET
                else -> from
            }
            Vpn5gConnectState.DISCONNECTING -> when (event) {
                // A restart racing the disconnect (server tap while
                // stopping): the new core started, so the chain
                // resumes as a tunnel being created — it will be
                // verified like any other start. Everything else is
                // absorbed until the stop lands or the timeout fires.
                Vpn5gConnectEvent.SERVICE_STARTED -> Vpn5gConnectState.CREATING_TUNNEL
                else -> from
            }
        }
    }

    /**
     * Real internet check: the device must have an active network with the
     * INTERNET capability, AND a short direct HTTPS request must succeed.
     * A captive portal or a dead uplink fails the probe, so the app never
     * starts a tunnel against a connection that cannot carry traffic.
     * Runs on Dispatchers.IO; never throws.
     */
    suspend fun hasInternet(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@withContext false
            val network = manager.activeNetwork ?: return@withContext false
            val capabilities = manager.getNetworkCapabilities(network) ?: return@withContext false
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@withContext false
            }
            val body = HttpUtil.getUrlContent(
                UrlContentRequest(url = AppConfig.IP_API_URL, timeout = 5000, httpPort = 0)
            )
            body != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The best server in the user's tier pool, excluding the given
     * GUIDs (failed this session / failed within the TTL). Pure — the
     * caller supplies the current section list.
     *
     * The [Vpn5gServerUi.isFree] flag IS the tier pool (see
     * Vpn5gSync.freeGuidsFor): top-20 for FREE, top-100 for GIFT,
     * the whole pinged pool for SUBSCRIBER — so a subscriber's
     * unlocked servers carry isFree = true and must be pickable.
     *
     * Pick order (v1.1.23):
     *  1. Real through-the-proxy ping inside the tier pool, fastest
     *     first (v1.1.7 rule — a TCP-only ping can be faked by a
     *     middlebox, so a verified server always wins).
     *  2. A build-time verified seed server in the pool (v1.1.20) —
     *     a starting point, never proof; a seed the real probe
     *     failed (ping -1) is dead and never picked.
     *  3. TCP-pinged servers inside the tier pool, fastest first
     *     (v1.1.23). This is the paid-tier fill pool: before 1.1.23
     *     the locks opened after a redeem but the picker ignored
     *     every fill server (no real ping), so a subscriber got
     *     «اول یک سرور انتخاب کنید» with hundreds of unlocked
     *     servers on screen. The tunnel verification + failover
     *     still guard a dead pick.
     *  4. Provisional entry fallback (v1.1.23): the fastest
     *     TCP-pinged server overall, only when the tier pool has
     *     nothing pickable yet — e.g. a free user right after
     *     entry, before the real probe has verified anything and
     *     with no seed trust (upgraders). 1.1.14 connected by
     *     itself to the best server; without this step the switch
     *     sat in FINDING_SERVER («در حال آزمایش سرور…») with
     *     «هنوز سروری نیست» while 688 servers were listed. The
     *     pick is provisional — verification judges it, and as
     *     soon as a verified pool server exists step 1 wins.
     */
    fun pickBestServer(
        sections: List<Vpn5gCountryUi>,
        excluding: Set<String> = emptySet(),
        healthScores: Map<String, Double> = emptyMap(),
    ): Vpn5gServerUi? {
        return rankCandidates(sections, excluding, healthScores).firstOrNull()
    }

    /**
     * The auto-pick order as a ranked list (v1.1.24): the same four
     * tier steps as [pickBestServer], each step ordered by
     * health-adjusted ping ([Vpn5gHealthScore.adjustedPing]) instead
     * of raw ping alone. With no health history (empty map) the
     * order is exactly the 1.1.23 order. The connect chain tries
     * candidates serially in this order with short verify timeouts
     * (see [verifyTimeoutMsForAttempt]) — the single Xray core
     * cannot run two tunnels at once, so true parallel attempts
     * are impossible; fast serial failover over a pre-ranked list
     * is the honest equivalent.
     */
    fun rankCandidates(
        sections: List<Vpn5gCountryUi>,
        excluding: Set<String> = emptySet(),
        healthScores: Map<String, Double> = emptyMap(),
    ): List<Vpn5gServerUi> {
        val all = sections.flatMap { it.servers }
        fun adjusted(server: Vpn5gServerUi): Long =
            Vpn5gHealthScore.adjustedPing(server.pingMillis, healthScores[server.dedupeKey] ?: 0.0)
        val real = all
            .filter { it.isFree && it.hasRealPing && it.pingMillis > 0 && it.guid !in excluding }
            .sortedBy { adjusted(it) }
        if (real.isNotEmpty()) return real
        val seed = all
            .filter { it.isFree && it.isSeed && it.pingMillis >= 0 && it.guid !in excluding }
        if (seed.isNotEmpty()) return seed
        val tcpInPool = all
            .filter { it.isFree && it.pingMillis > 0 && it.guid !in excluding }
            .sortedBy { adjusted(it) }
        if (tcpInPool.isNotEmpty()) return tcpInPool
        return all
            .filter { it.pingMillis > 0 && it.guid !in excluding }
            .sortedBy { adjusted(it) }
    }

    /**
     * Verify budget per attempt (v1.1.24, pure): the first three
     * attempts of a connect chain get a short 15 s budget so a dead
     * server is rejected fast and the next ranked candidate starts
     * sooner; later attempts keep the full 25 s budget (a slow but
     * real tunnel on a bad network still gets its chance). The
     * single Xray core makes true parallel attempts unsafe — this
     * short-timeout serial order is the fast-failover equivalent.
     */
    fun verifyTimeoutMsForAttempt(attemptIndex: Int): Int =
        if (attemptIndex < 3) FAST_VERIFY_TIMEOUT_MS else FULL_VERIFY_TIMEOUT_MS

    const val FAST_VERIFY_TIMEOUT_MS = 15_000
    const val FULL_VERIFY_TIMEOUT_MS = 25_000

    /**
     * True while a tap on the connect switch must mean "turn OFF"
     * (v1.1.23, pure): the daemon is running OR the connect chain
     * is in any live state. Before 1.1.23 the switch only
     * disconnected when the daemon was already running — a tap
     * while FINDING_SERVER / CONNECTING (daemon not started yet)
     * restarted the chain instead of cancelling it, so the switch
     * got stuck in CONNECTING and could never be turned off.
     * IDLE and FAILED are the only states where a tap means
     * "connect".
     */
    fun isConnectActive(
        connectState: Vpn5gConnectState,
        isRunning: Boolean,
    ): Boolean {
        if (isRunning) return true
        return connectState != Vpn5gConnectState.IDLE &&
            connectState != Vpn5gConnectState.FAILED
    }

    // v1.1.25: the v1.1.17 startup auto-connect decision was REMOVED
    // (Aref's order: the app must not connect by itself on entry —
    // connection happens only when the user taps the switch).
    // Auto-failover/reconnect AFTER a user-initiated connect stays.
}

/**
 * Manual server selection (v1.1.5 §3): the server the user picked in the
 * free-servers dropdown, validated exactly like the auto picker — it must
 * still be in the free list, have a measured ping, and not have failed
 * tunnel verification within the last 6 hours. Pure; the caller supplies
 * the current section list and the pruned failed-server map.
 */
object Vpn5gManualServer {

    enum class Problem {
        /** The picked server is no longer in the synced list. */
        NOT_FOUND,
        /** The picked server is no longer in the free tier. */
        NOT_FREE,
        /** The picked server has no measured ping. */
        NO_PING,
        /** The picked server failed tunnel verification within the TTL. */
        FAILED_RECENTLY,
    }

    data class Resolution(val server: Vpn5gServerUi?, val problem: Problem?)

    fun resolve(
        sections: List<Vpn5gCountryUi>,
        manualGuid: String?,
        failedGuids: Map<String, Long>,
        nowMs: Long,
    ): Resolution {
        if (manualGuid.isNullOrBlank()) return Resolution(null, Problem.NOT_FOUND)
        val server = sections.flatMap { it.servers }.firstOrNull { it.guid == manualGuid }
            ?: return Resolution(null, Problem.NOT_FOUND)
        if (!server.isFree) return Resolution(null, Problem.NOT_FREE)
        // v1.1.20 (seed): a seed server has no measured ping yet, but
        // it is free-eligible (build-time verified) and the connect
        // chain verifies the tunnel — a manual tap on it must work
        // like a tap on a real-pinged server.
        if (server.pingMillis <= 0 && !server.isSeed) return Resolution(null, Problem.NO_PING)
        if (Vpn5gFailedServers.isFailed(failedGuids, manualGuid, nowMs)) {
            return Resolution(null, Problem.FAILED_RECENTLY)
        }
        return Resolution(server, null)
    }
}

/**
 * Servers that failed tunnel verification, kept for 6 hours so the auto
 * picker does not keep re-trying a dead server across sessions (v1.1.4 §5).
 * Stored as a small JSON map guid → failure epoch millis in MMKV under
 * [Vpn5gConstants.KEY_FAILED_GUIDS_JSON]. The pure functions are unit
 * tested; [load]/[save] are the only MMKV touch points.
 */
object Vpn5gFailedServers {

    val TTL_MS: Long = Vpn5gConstants.FAILED_GUID_TTL_MS

    /** Drop entries older than the TTL. Pure. */
    fun prune(entries: Map<String, Long>, nowMs: Long): Map<String, Long> {
        return entries.filterValues { it > 0L && nowMs - it < TTL_MS }
    }

    /** True when the GUID failed within the TTL. Pure. */
    fun isFailed(entries: Map<String, Long>, guid: String, nowMs: Long): Boolean {
        val failedAt = entries[guid] ?: return false
        return failedAt > 0L && nowMs - failedAt < TTL_MS
    }

    /** Record a failure at [nowMs]. Pure. */
    fun mark(entries: Map<String, Long>, guid: String, nowMs: Long): Map<String, Long> {
        return entries + (guid to nowMs)
    }

    fun load(): Map<String, Long> {
        return try {
            val raw = MmkvManager.decodeSettingsString(Vpn5gConstants.KEY_FAILED_GUIDS_JSON)
                ?: return emptyMap()
            @Suppress("UNCHECKED_CAST")
            (JsonUtil.fromJson(raw, Map::class.java) as? Map<String, Any>)
                ?.mapValues { (_, value) -> (value as? Number)?.toLong() ?: 0L }
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(entries: Map<String, Long>) {
        try {
            MmkvManager.encodeSettings(
                Vpn5gConstants.KEY_FAILED_GUIDS_JSON,
                JsonUtil.toJson(entries),
            )
        } catch (e: Exception) {
            LogUtil.e("Vpn5gFailedServers", "Failed to persist failed-server store", e)
        }
    }
}
