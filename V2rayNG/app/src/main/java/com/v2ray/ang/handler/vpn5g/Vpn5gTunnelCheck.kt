package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Post-connect tunnel verification.
 *
 * v1 showed "connected" as soon as the daemon reported running — but the
 * daemon stays "running" even when the outbound is dead (the local VPN
 * interface is up, the core just cannot reach its server). On filtered
 * networks the sync TCP-ping also passes for dead servers: the handshake is
 * answered in-transit while the real protocol handshake never completes.
 * The result is a false "connected" with no traffic tunneled.
 *
 * Verification (runs on Dispatchers.IO, never on the main thread):
 *  1. [publicIpDirect] — the device's public IP with no proxy (baseline,
 *     captured in the background while disconnected).
 *  2. After the daemon reports running, the public IP seen through the
 *     core's local HTTP inbound — that request can only succeed when the
 *     outbound actually carries traffic. The fetch goes through the
 *     local HTTP proxy on purpose: the app's own process is excluded
 *     from the VPN tunnel (CoreVpnService disallows its own package),
 *     so a direct fetch from the app would bypass the tunnel and prove
 *     nothing; the loopback HTTP inbound is the only path whose traffic
 *     the core carries.
 *  3. VERIFIED when the tunnel fetch returns an IP that differs from the
 *     baseline (or when no baseline exists but the tunnel fetch succeeds);
 *     FAILED when the tunnel fetch fails or the exit IP is unchanged.
 * UNKNOWN when the local HTTP inbound is unavailable (port 0) — the check
 * cannot run and must not fail the connection on its own.
 *
 * One slow first fetch must not kill a healthy server: a cold core on
 * mobile data routinely needs several seconds for the WS/TLS handshake
 * plus the probe round-trip, so [verify] retries the fetch (primary IP
 * API, then the ipify fallback) in up to [MAX_VERIFY_ROUNDS] rounds
 * inside a bounded [TUNNEL_TIMEOUT_MS] budget. A truly dead server
 * fails every round and still ends FAILED — the budget, not an
 * unbounded loop, bounds the cost.
 *
 * Never throws; every failure mode is a [TunnelResult].
 */
object Vpn5gTunnelCheck {

    enum class Verdict { VERIFIED, FAILED, UNKNOWN }

    data class TunnelResult(
        val verdict: Verdict,
        val exitIp: String? = null,
        val detail: String? = null,
    )

    private const val DIRECT_TIMEOUT_MS = 8_000
    /** Overall budget for one verify() call, covering all fetch rounds. */
    private const val TUNNEL_TIMEOUT_MS = 25_000
    /** Per-fetch timeout through the local HTTP proxy. */
    private const val TUNNEL_FETCH_TIMEOUT_MS = 7_000
    /**
     * Per-probe timeout for the light watchdog probe (v1.1.11;
     * 3 s since v1.1.29; 1.5 s since v1.1.34 — the probe now runs
     * every 2 s with an immediate confirm probe, so a long
     * per-probe budget would eat the sub-2-second failover budget
     * Aref ordered; a healthy tunnel answers an exit-IP fetch
     * well inside 1.5 s, and a slow answer is itself a failure
     * signal the leaky bucket weighs).
     */
    private const val WATCHDOG_PROBE_TIMEOUT_MS = 1_500
    /** How many fetch rounds verify() tries before giving up on a server. */
    private const val MAX_VERIFY_ROUNDS = 3
    /** Pause between fetch rounds. */
    private const val VERIFY_RETRY_DELAY_MS = 500L
    /** Fallback probe when the primary IP API yields nothing. */
    private const val FALLBACK_IP_URL = "https://api.ipify.org?format=json"

    /** The device's public IP fetched directly (no proxy), or null. */
    suspend fun publicIpDirect(timeoutMs: Int = DIRECT_TIMEOUT_MS): String? =
        withContext(Dispatchers.IO) {
            try {
                val body = HttpUtil.getUrlContent(
                    UrlContentRequest(url = AppConfig.IP_API_URL, timeout = timeoutMs)
                ) ?: return@withContext null
                parseIp(body)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gTunnelCheck: direct IP fetch failed", e)
                null
            }
        }

    /**
     * One exit-IP fetch through the running core's local HTTP inbound,
     * or null when it yields nothing. The proxy is the loopback HTTP
     * inbound (port from [SettingsManager.getHttpPort]); the request
     * only succeeds when the core's outbound carries it.
     */
    private suspend fun fetchExitIp(url: String, timeoutMs: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                val httpPort = SettingsManager.getHttpPort()
                if (httpPort == 0) return@withContext null
                val body = HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = url,
                        timeout = timeoutMs,
                        httpPort = httpPort,
                        proxyUsername = SettingsManager.getSocksUsername(),
                        proxyPassword = SettingsManager.getSocksPassword(),
                    )
                ) ?: return@withContext null
                parseIp(body)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gTunnelCheck: tunnel IP fetch failed", e)
                null
            }
        }

    /** Pure verdict from a fetched tunnel IP against the baseline. */
    fun evaluateExitIp(tunnelIp: String?, baselineIp: String?): TunnelResult =
        when {
            tunnelIp.isNullOrBlank() -> TunnelResult(
                Verdict.FAILED,
                detail = "no reply through the tunnel",
            )
            baselineIp != null && tunnelIp == baselineIp -> TunnelResult(
                Verdict.FAILED,
                exitIp = tunnelIp,
                detail = "exit IP unchanged",
            )
            else -> TunnelResult(Verdict.VERIFIED, exitIp = tunnelIp)
        }

    /** Full verification against a previously captured [baselineIp]. */
    suspend fun verify(
        baselineIp: String?,
        timeoutMs: Int = TUNNEL_TIMEOUT_MS,
    ): TunnelResult = withContext(Dispatchers.IO) {
        if (SettingsManager.getHttpPort() == 0) {
            return@withContext TunnelResult(
                Verdict.UNKNOWN,
                detail = "no local proxy port",
            )
        }
        val tunnelIp = withTimeoutOrNull(timeoutMs.toLong()) {
            var attempt = 0
            var ip: String? = null
            while (attempt < MAX_VERIFY_ROUNDS && ip.isNullOrBlank()) {
                attempt++
                ip = fetchExitIp(AppConfig.IP_API_URL, TUNNEL_FETCH_TIMEOUT_MS)
                    ?: fetchExitIp(FALLBACK_IP_URL, TUNNEL_FETCH_TIMEOUT_MS)
                if (ip.isNullOrBlank() && attempt < MAX_VERIFY_ROUNDS) {
                    delay(VERIFY_RETRY_DELAY_MS)
                }
            }
            ip
        }
        evaluateExitIp(tunnelIp, baselineIp)
    }

    /**
     * Which exit IP one probe reports from its primary and fallback
     * fetch results (v1.1.13): the primary result wins; the fallback
     * fetch runs only when the primary yields nothing, and only its
     * result can still save the probe. Pure apart from the fetch
     * lambdas it is given, so JVM tests drive it with fixed values.
     * A blank result counts as nothing — a fetch returning an empty
     * body must not mask a working fallback.
     */
    suspend fun probeIp(
        primary: suspend () -> String?,
        fallback: suspend () -> String?,
    ): String? = primary()?.takeIf { it.isNotBlank() }
        ?: fallback()?.takeIf { it.isNotBlank() }

    /**
     * Light in-session watchdog probe (v1.1.11): ONE exit-IP probe
     * through the tunnel — the primary IP API first, then the ipify
     * fallback when the primary yields nothing (v1.1.13; the same
     * fallback [verify] already had) — bounded by [timeoutMs]. The
     * ViewModel runs this every 5 s while connected (v1.1.29) and
     * feeds the verdict into the leaky-bucket score
     * ([Vpn5gWatchdogScore]); at the drop score the tunnel drops so
     * the drop detector can fail over. A single failed probe never
     * kills a session on its own — a transient blip is not a drop.
     * UNKNOWN when the local HTTP inbound is unavailable (the check
     * cannot run); the score treats it as a capped soft failure,
     * never enough on its own to drop. Never throws.
     */
    suspend fun probeOnce(
        baselineIp: String?,
        timeoutMs: Int = WATCHDOG_PROBE_TIMEOUT_MS,
    ): TunnelResult = withContext(Dispatchers.IO) {
        if (SettingsManager.getHttpPort() == 0) {
            return@withContext TunnelResult(
                Verdict.UNKNOWN,
                detail = "no local proxy port",
            )
        }
        val tunnelIp = withTimeoutOrNull(timeoutMs.toLong()) {
            probeIp(
                { fetchExitIp(AppConfig.IP_API_URL, timeoutMs) },
                { fetchExitIp(FALLBACK_IP_URL, timeoutMs) },
            )
        }
        evaluateExitIp(tunnelIp, baselineIp)
    }

    private fun parseIp(json: String): String? {
        return try {
            val info = JsonUtil.fromJsonSafe(json, IPAPIInfo::class.java) ?: return null
            listOf(info.ip, info.clientIp, info.ip_addr, info.query)
                .firstOrNull { !it.isNullOrBlank() }?.trim()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gTunnelCheck: IP parse failed", e)
            null
        }
    }
}
