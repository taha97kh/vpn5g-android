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
 * The ten states the home screen ever shows (v1.1.5 §2 — the seven
 * user-visible connect states from the spec, plus IDLE, CONNECTED and the
 * transient RETRYING). The home text comes from these states only, and
 * the precise failure reason lives in the ViewModel's lastError, never in
 * a technical error string.
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
     * Pure state machine. An event that makes no sense in the current state
     * leaves the state unchanged — except USER_DISCONNECT, which always
     * lands in IDLE, and GIVE_UP, which lands in FAILED (the precise error
     * is shown and the app stays Disconnected) except from CONNECTED, where
     * a stop is a clean end (30-minute timer / service stop) and lands in
     * IDLE. CONNECTED is only reachable from VERIFYING_TUNNEL on VERIFIED.
     */
    fun transition(from: Vpn5gConnectState, event: Vpn5gConnectEvent): Vpn5gConnectState {
        if (event == Vpn5gConnectEvent.USER_DISCONNECT) {
            return Vpn5gConnectState.IDLE
        }
        if (event == Vpn5gConnectEvent.GIVE_UP) {
            return when (from) {
                Vpn5gConnectState.IDLE -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.CONNECTED -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.FAILED -> Vpn5gConnectState.FAILED
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
     * The fastest free server with a measured ping, excluding the given
     * GUIDs (failed this session / failed within the TTL). Pure — the
     * caller supplies the current section list.
     */
    fun pickBestServer(
        sections: List<Vpn5gCountryUi>,
        excluding: Set<String> = emptySet(),
    ): Vpn5gServerUi? {
        return sections
            .flatMap { it.servers }
            .filter { it.isFree && it.pingMillis > 0 && it.guid !in excluding }
            .minByOrNull { it.pingMillis }
    }
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
        if (server.pingMillis <= 0) return Resolution(null, Problem.NO_PING)
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
