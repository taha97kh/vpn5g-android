package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * In-session standby liveness probes (v1.1.29).
 *
 * The warm standby pool (v1.1.24) real-probes stored profiles only
 * while the VPN is idle — a throwaway core must never run next to
 * the live one — so during a session the standby list ages: by
 * drop time the failover was burning full verify budgets on
 * servers that had died minutes earlier.
 *
 * This probe needs no core: the app's own process is excluded
 * from the VPN tunnel (CoreVpnService disallows its own package),
 * so a plain socket from the app measures the standby server
 * directly from the device network — exactly the path a failover
 * restart would take. TCP connect proves reachability; for
 * TLS/REALITY profiles a TLS handshake with the profile's SNI is
 * attempted as a stronger signal (a REALITY server answers a
 * standard TLS handshake with its borrowed cert, so a completed
 * handshake means the front door is alive; a failed handshake
 * with a good TCP connect still counts as reachable, just ranked
 * lower — some fronts reject plain TLS probes).
 *
 * Honest limit: only a through-the-proxy real ping proves the
 * protocol handshake end-to-end, and that needs a second core
 * instance, which the single-core design cannot run next to the
 * live tunnel. TCP+TLS liveness plus the health score is the
 * practical proxy; the failover verify still judges the pick.
 */
object Vpn5gStandbyLiveness {

    /** One standby probe outcome. [tcpDelayMs] < 0 = unreachable. */
    data class Result(
        val guid: String,
        val tcpDelayMs: Long,
        val tlsOk: Boolean,
    ) {
        val alive: Boolean get() = tcpDelayMs >= 0
    }

    /**
     * Probes one standby profile: TCP connect, then a TLS
     * handshake when the profile carries TLS/REALITY security.
     * Never throws; an unreachable server is [Result.tcpDelayMs] < 0.
     */
    suspend fun probe(
        guid: String,
        profile: ProfileItem,
        timeoutMs: Int = Vpn5gConstants.STANDBY_PROBE_TIMEOUT_MS,
    ): Result = withContext(Dispatchers.IO) {
        val host = profile.server.orEmpty()
        val port = profile.serverPort?.toIntOrNull() ?: -1
        if (host.isBlank() || port <= 0) {
            return@withContext Result(guid, -1L, false)
        }
        val delay = SpeedtestManager.socketConnectTime(host, port, timeoutMs)
        if (delay < 0) return@withContext Result(guid, delay, false)
        val security = profile.security.orEmpty()
        val tlsOk = if (security.equals("tls", ignoreCase = true) ||
            security.equals("reality", ignoreCase = true)
        ) {
            tlsHandshake(host, port, profile.sni, timeoutMs)
        } else {
            false
        }
        Result(guid, delay, tlsOk)
    }

    /**
     * One TLS handshake against [host]:[port] with [sni] (the host
     * itself when no SNI is configured). True when the handshake
     * completes inside [timeoutMs]. Never throws.
     */
    private fun tlsHandshake(
        host: String,
        port: Int,
        sni: String?,
        timeoutMs: Int,
    ): Boolean {
        var socket: SSLSocket? = null
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val params = socket.sslParameters
            val serverName = sni?.trim()?.takeIf { it.isNotBlank() } ?: host
            params.serverNames = listOf(SNIHostName(serverName))
            socket.sslParameters = params
            socket.startHandshake()
            true
        } catch (e: Exception) {
            LogUtil.i(AppConfig.TAG, "Vpn5gStandbyLiveness: TLS probe failed for $host:$port: ${e.message}")
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    /**
     * The replacement queue from one liveness round (pure): alive
     * results only, TLS-proven first, then fastest TCP delay, at
     * most [limit] guids. Dead results never enter the queue.
     */
    fun rankQueue(results: List<Result>, limit: Int = Vpn5gConstants.STANDBY_QUEUE_SIZE): List<String> =
        results.filter { it.alive }
            .sortedWith(compareBy({ !it.tlsOk }, { it.tcpDelayMs }))
            .take(limit)
            .map { it.guid }
}
