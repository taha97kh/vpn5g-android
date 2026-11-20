package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 *  2. After the daemon reports running, [publicIpViaTunnel] — the public IP
 *     seen through the core's local HTTP inbound
 *     ([SpeedtestManager.getRemoteIPInfo]); that request can only succeed
 *     when the outbound actually carries traffic.
 *  3. VERIFIED when the tunnel fetch returns an IP that differs from the
 *     baseline (or when no baseline exists but the tunnel fetch succeeds);
 *     FAILED when the tunnel fetch fails or the exit IP is unchanged.
 * UNKNOWN when the local HTTP inbound is unavailable (port 0) — the check
 * cannot run and must not fail the connection on its own.
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
    private const val TUNNEL_TIMEOUT_MS = 10_000

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

    /** The public IP seen through the running core's local proxy, or null. */
    suspend fun publicIpViaTunnel(): String? = withContext(Dispatchers.IO) {
        try {
            SpeedtestManager.getRemoteIPInfo()?.ipAddress?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gTunnelCheck: tunnel IP fetch failed", e)
            null
        }
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
        // getRemoteIPInfo has its own 5s timeout; the caller bounds the call.
        val tunnelIp = try {
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs.toLong()) {
                    publicIpViaTunnel()
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gTunnelCheck: verify failed", e)
            null
        }
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
