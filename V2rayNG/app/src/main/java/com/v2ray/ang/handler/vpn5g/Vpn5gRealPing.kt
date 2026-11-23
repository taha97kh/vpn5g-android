package com.v2ray.ang.handler.vpn5g

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.service.RealPingWorkerService
import com.v2ray.ang.util.LogUtil
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real (through-the-proxy) ping measurement (v1.1.7).
 *
 * Why: the TCP-connect ping (`SpeedtestManager.socketConnectTime`) only
 * proves a TCP handshake completed. On a filtered network a middlebox
 * answers the handshake in-transit while the real protocol handshake
 * never reaches the server — so a dead server can show a 1 ms ping and
 * the free tier (fastest by TCP time) fills with servers that can never
 * tunnel. A ping shown in the app must therefore be a real measurement:
 * a request sent through the proxy itself.
 *
 * How: the probe reuses upstream's own delay-test worker
 * ([RealPingWorkerService], the same worker the `:tasks` services use for
 * URL tests) with `onlyTcp = false`. For each candidate the worker builds
 * a throwaway core from the stored config and measures a real request to
 * the delay-test URL through it (`CoreNativeManager.measureOutboundDelay`);
 * a server whose proxy handshake fails returns -1. The probe is a
 * measurement only: it never starts, stops or restarts the live core,
 * and it is skipped entirely while the VPN is running. Loading the native
 * library from the UI process for a measurement has upstream precedent
 * (AboutActivity / CheckUpdateActivity call CoreNativeManager); the
 * service guide's prohibition is about deciding the core lifecycle,
 * which this never does.
 *
 * Evidence is keyed by server identity
 * ([Vpn5gServerValidator.dedupeKey], scheme://host:port:identity), not
 * by GUID: GUIDs are re-issued on every refresh while the identity is
 * stable, so a server that proved itself keeps its real ping across
 * refreshes and the probe only re-tests new candidates. A server that
 * fails the real test is NOT added to the store — it stays TCP-only and
 * is never free or auto-picked.
 */
object Vpn5gRealPing {

    /** One probe candidate: the stored GUID plus its server identity. */
    data class RealPingCandidate(val guid: String, val dedupeKey: String)

    /** Result of one probe batch. */
    data class ProbeResult(
        /** How many candidates were actually tested. */
        val tested: Int = 0,
        /** Dedupe keys that passed the real test. */
        val verifiedKeys: Set<String> = emptySet(),
        /** GUIDs that failed the real test (their TCP ping is withdrawn). */
        val failedGuids: Set<String> = emptySet(),
        /** Real through-the-proxy delay per verified GUID (ms). */
        val realDelays: Map<String, Long> = emptyMap(),
    )

    /**
     * Picks the probe batch (pure): the fastest [count] candidates by TCP
     * delay whose identity has not already been proven real. Pure Kotlin
     * so it is covered by JVM unit tests in Vpn5gRealPingTest.
     */
    fun selectCandidates(
        candidates: List<RealPingCandidate>,
        tcpDelays: Map<String, Long>,
        knownReal: Set<String>,
        count: Int = Vpn5gConstants.REAL_PING_CANDIDATES,
    ): List<RealPingCandidate> {
        return candidates
            .filter { it.dedupeKey !in knownReal }
            .sortedBy { tcpDelays[it.guid] ?: Long.MAX_VALUE }
            .take(count)
    }

    /** Reads the stored set of real-pinged server identities. */
    fun loadRealPingKeys(): Set<String> {
        val csv = MmkvManager.decodeSettingsString(Vpn5gConstants.KEY_REAL_PING_KEYS_CSV)
            .orEmpty()
        if (csv.isBlank()) return emptySet()
        return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Merges [keys] into the stored set of real-pinged server identities. */
    fun addRealPingKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val merged = loadRealPingKeys() + keys.filter { it.isNotBlank() }
        MmkvManager.encodeSettingsString(
            Vpn5gConstants.KEY_REAL_PING_KEYS_CSV,
            merged.joinToString(","),
        )
    }

    /**
     * Runs a real through-the-proxy delay test for [candidates] and
     * returns which identities verified and which GUIDs failed. Bounded
     * by [budgetMs]: whatever has not finished by then is cancelled and
     * simply stays unproven (never shown as a ping). Never throws.
     */
    suspend fun probeCandidates(
        context: Context,
        candidates: List<RealPingCandidate>,
        budgetMs: Long = Vpn5gConstants.REAL_PING_BUDGET_MS,
    ): ProbeResult {
        if (candidates.isEmpty()) return ProbeResult()
        return runCatching {
            val byGuid = candidates.associateBy { it.guid }
            val verified = Collections.synchronizedSet(mutableSetOf<String>())
            val failed = Collections.synchronizedSet(mutableSetOf<String>())
            val delays = Collections.synchronizedMap(mutableMapOf<String, Long>())
            val done = CompletableDeferred<Unit>()
            val worker = RealPingWorkerService(
                context,
                candidates.map { it.guid },
                onlyTcp = false,
            ) { event ->
                when (event) {
                    is RealPingEvent.Result -> {
                        val key = byGuid[event.guid]?.dedupeKey ?: return@RealPingWorkerService
                        if (event.delayMillis > 0) {
                            verified.add(key)
                            delays[event.guid] = event.delayMillis
                        } else {
                            failed.add(event.guid)
                        }
                    }
                    is RealPingEvent.Finish -> done.complete(Unit)
                    else -> {
                        // Progress events are ignored.
                    }
                }
            }
            worker.start()
            withTimeoutOrNull(budgetMs) { done.await() }
            worker.cancel()
            ProbeResult(
                tested = candidates.size,
                verifiedKeys = verified.toSet(),
                failedGuids = failed.toSet(),
                realDelays = delays.toMap(),
            )
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "Vpn5gRealPing: probe failed", e)
        }.getOrDefault(ProbeResult(tested = candidates.size))
    }
}
