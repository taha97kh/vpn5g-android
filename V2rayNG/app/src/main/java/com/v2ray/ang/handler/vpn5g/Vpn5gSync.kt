package com.v2ray.ang.handler.vpn5g

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.ASSET_FALLBACK_SERVERS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CONFIG_SCHEMES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.FREE_SERVER_COUNT
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.LIVE_JSON
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUBSCRIPTION_REMARKS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.normalizeCountry
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * VPN5G IRBOT live-config sync.
 *
 * Fetches live_configs.json (+ sub.txt and any extra sources from
 * app_config.json), TCP-pings every candidate, and replaces the "VPN5G IRBOT"
 * group with only the servers that answered. Dead configs are never stored
 * or shown. Runs on Dispatchers.IO; never throws.
 */
object Vpn5gSync {

    private const val FETCH_TIMEOUT_MS = 10_000
    private const val PING_TIMEOUT_MS = 1500
    private const val MAX_CONCURRENT_PINGS = 8

    private data class Candidate(
        val uri: String,
        val country: String,
    )

    /**
     * Parse + validate + dedupe one candidate list (v1.1.16: shared by
     * the full refresh and the quick first-screen refresh). Returns the
     * unique (profile, candidate) pairs and the invalid count. Invalid
     * configs are dropped here — never pinged, never stored, never
     * shown (v1.1.4 §5). Dedupe is by server identity
     * (scheme://host:port:uuid-or-password), not by raw URI text.
     */
    private fun parseValidateCandidates(
        candidates: List<Candidate>,
        subId: String,
    ): Pair<List<Pair<ProfileItem, Candidate>>, Int> {
        // Parse first (local, cheap), then validate, then TCP-ping the
        // parsed host:port.
        val parsed = candidates.mapNotNull { candidate ->
            val profile = try {
                AngConfigManager.parseConfig(candidate.uri, subId, null)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gSync: parse failed", e)
                null
            } ?: return@mapNotNull null
            profile.country = candidate.country
            profile to candidate
        }

        var invalid = 0
        val validated = parsed.mapNotNull { (profile, candidate) ->
            val validation = Vpn5gServerValidator.validate(profile)
            if (!validation.isValid) {
                invalid++
                return@mapNotNull null
            }
            Vpn5gServerValidator.normalize(profile)
            profile to candidate
        }
        val unique = validated.distinctBy { (profile, _) ->
            Vpn5gServerValidator.dedupeKey(profile)
        }
        return unique to invalid
    }

    /**
     * TCP-pings the candidates (8-way concurrency, 1.5 s each) and
     * returns only the live ones. Shared by both refresh paths.
     */
    private suspend fun tcpPingCandidates(
        unique: List<Pair<ProfileItem, Candidate>>,
    ): List<Triple<ProfileItem, Long, String>> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_PINGS)
        val pinged = unique.map { (profile, _) ->
            async {
                semaphore.withPermit {
                    val host = profile.server.orEmpty()
                    val port = profile.serverPort?.toIntOrNull() ?: -1
                    val delay = if (host.isBlank() || port <= 0) {
                        -1L
                    } else {
                        SpeedtestManager.socketConnectTime(host, port, PING_TIMEOUT_MS)
                    }
                    Triple(profile, delay, profile.country)
                }
            }
        }.awaitAll()
        // HARD RULE: dead configs (ping < 0) are never stored or shown.
        pinged.filter { it.second >= 0 }
    }

    /**
     * Saves the TCP-live profiles, real-probes the fastest batch, and
     * returns (per-country counts, real-pinged count). Shared by both
     * refresh paths; [probeCount]/[probeBudgetMs] are the full-refresh
     * values (24 / 45 s) or the quick values (24 / 10 s, v1.1.18). The stored
     * ping of a verified server becomes the REAL through-the-proxy
     * delay; a server that fails the real test has its ping withdrawn
     * so it is never free or auto-picked this round.
     */
    private suspend fun publishLiveProfiles(
        context: Context,
        live: List<Triple<ProfileItem, Long, String>>,
        subId: String,
        skipRealProbe: Boolean,
        probeCount: Int,
        probeBudgetMs: Long,
        onProgress: ((Vpn5gStartupProgress.Stage, Float) -> Unit)?,
    ): Pair<Map<String, Int>, Int> {
        val profiles = linkedMapOf<String, ProfileItem>()
        val rawConfigs = mutableMapOf<String, String>()
        val perCountry = mutableMapOf<String, Int>()
        live.forEach { (profile, _, country) ->
            val guid = Utils.getUuid()
            profiles[guid] = profile
            rawConfigs[guid] = ""
            val key = normalizeCountry(country).ifEmpty { "بین‌الملل" }
            perCountry[key] = (perCountry[key] ?: 0) + 1
        }

        if (profiles.isNotEmpty()) {
            MmkvManager.saveServerProfiles(
                profiles = profiles,
                rawConfigs = rawConfigs,
                subscriptionId = subId,
                append = false,
            )
            // v1.1.20 (seed): live data replaces the seed — the seed
            // identities are dropped with the group they belonged to,
            // so a stale seed can never stay free-eligible.
            clearSeedKeys()
            // Record the measured ping for the home screen.
            live.forEachIndexed { index, (_, delay, _) ->
                val guid = profiles.keys.elementAt(index)
                MmkvManager.encodeServerTestDelayMillis(guid, delay)
            }
        }

        // v1.1.7 real ping: a TCP connect only proves a handshake
        // completed — on a filtered network a middlebox answers it
        // in-transit while the protocol handshake never completes, so
        // a dead server can show 1 ms. The fastest TCP-pinged
        // candidates are therefore put through a real through-the-proxy
        // test; only servers that pass it get a shown ping, the free
        // tier, or an auto pick. Candidates already proven in an
        // earlier refresh are not re-tested (evidence is keyed by
        // server identity, which survives a refresh).
        var realPingCount = 0
        if (profiles.isNotEmpty()) {
            onProgress?.invoke(Vpn5gStartupProgress.Stage.REAL_PROBE, 0f)
            if (!skipRealProbe) {
                val allCandidates = live.mapIndexed { index, (profile, _, _) ->
                    val guid = profiles.keys.elementAt(index)
                    Vpn5gRealPing.RealPingCandidate(
                        guid,
                        Vpn5gServerValidator.dedupeKey(profile),
                    )
                }
                val tcpDelays = live.mapIndexed { index, (_, delay, _) ->
                    profiles.keys.elementAt(index) to delay
                }.toMap()
                val probeCandidates = Vpn5gRealPing.selectCandidates(
                    allCandidates,
                    tcpDelays,
                    Vpn5gRealPing.loadRealPingKeys(),
                    probeCount,
                )
                if (probeCandidates.isNotEmpty()) {
                    val probe = Vpn5gRealPing.probeCandidates(
                        context, probeCandidates, probeBudgetMs
                    )
                    recordProbeHealth(probeCandidates, probe)
                    if (probe.verifiedKeys.isNotEmpty()) {
                        Vpn5gRealPing.addRealPingKeys(probe.verifiedKeys)
                    }
                    probe.realDelays.forEach { (guid, delay) ->
                        MmkvManager.encodeServerTestDelayMillis(guid, delay)
                    }
                    probe.failedGuids.forEach { guid ->
                        MmkvManager.encodeServerTestDelayMillis(guid, -1L)
                    }
                    LogUtil.i(
                        AppConfig.TAG,
                        "Vpn5gSync: real ping " +
                            "${probe.verifiedKeys.size}/${probe.tested} verified",
                    )
                    // v1.1.17 (standby pool > 10): the first probe
                    // batch may verify fewer than STANDBY_POOL_MIN
                    // servers on a slow connection — keep probing
                    // the next-fastest unproven candidates until
                    // the real-verified pool reaches STANDBY_POOL_MIN
                    // or the candidates run out.
                    topUpStandbyPool(context, allCandidates, tcpDelays, probeBudgetMs)
                }
            }
            val realKeys = Vpn5gRealPing.loadRealPingKeys()
            realPingCount = live.count { (profile, _, _) ->
                Vpn5gServerValidator.dedupeKey(profile) in realKeys
            }
            onProgress?.invoke(Vpn5gStartupProgress.Stage.REAL_PROBE, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.SAVING, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.DONE, 1f)
        } else {
            onProgress?.invoke(Vpn5gStartupProgress.Stage.DONE, 1f)
        }
        return perCountry to realPingCount
    }

    /**
     * Finds the "VPN5G IRBOT" subscription group, creating it on first use.
     */
    fun ensureSubscriptionId(): String {
        val existing = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url == LIVE_JSON }
        if (existing != null) return existing.guid

        val guid = Utils.getUuid()
        val item = SubscriptionItem(
            remarks = SUBSCRIPTION_REMARKS,
            url = LIVE_JSON,
            enabled = true,
        )
        MmkvManager.encodeSubscription(guid, item)
        return guid
    }

    /**
     * Refreshes the live server list from all sources (v1.1.6: 8 sources).
     * [skipRealProbe] is true while the VPN is running: a throwaway core
     * is never spawned next to the live one, and the real-ping evidence
     * is keyed by server identity so proven servers keep their real ping.
     * [onProgress] (v1.1.14) reports the REAL sync stages for the
     * startup screen's 0→100% bar — it is only called when provided
     * (startup); the 5-minute ticker and manual refresh pass none.
     */
    /**
     * Manual-refresh fetch timeout (v1.1.26): the <5 s refresh
     * budget Aref ordered — sources answer in parallel inside
     * this, the list shows, and real-ping verification continues
     * in the background (warm pool) without blocking the UI.
     */
    private const val FAST_FETCH_TIMEOUT_MS = 4_000

    suspend fun refresh(
        context: Context,
        skipRealProbe: Boolean = false,
        onProgress: ((Vpn5gStartupProgress.Stage, Float) -> Unit)? = null,
        fast: Boolean = false,
    ): Vpn5gSyncResult = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_CONFIG, 0f)
            // v1.1.26 (fast manual refresh): the config fetch gets
            // the same tight timeout so servers + app config land
            // inside the 5 s budget together.
            val remoteConfig = Vpn5gRemoteConfig.fetch(
                timeoutMs = if (fast) FAST_FETCH_TIMEOUT_MS else 10_000,
            )
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_CONFIG, 1f)

            val sources = linkedSetOf<String>()
            sources += Vpn5gConstants.DEFAULT_CONFIG_SOURCES
            remoteConfig.config_sources?.forEach { url ->
                if (!url.isNullOrBlank()) sources.add(url.trim())
            }

            val candidates = mutableListOf<Candidate>()
            var anyFetchOk = false
            val sourceList = sources.toList()
            // v1.1.24 (racing fetch, Lantern-inspired): the sources
            // are fetched IN PARALLEL instead of sequentially — one
            // slow source no longer delays all the others (8 × 10 s
            // worst case became ~10 s). Every source that answers
            // contributes; when none answers, the last-good stored
            // list stays untouched (offline return below). The
            // quick path already fetched in parallel; the full
            // refresh now matches it.
            val bodies = coroutineScope {
                sourceList.map { url ->
                    async {
                        url to try {
                            HttpUtil.getUrlContent(
                                UrlContentRequest(
                                    url = url,
                                    timeout = if (fast) FAST_FETCH_TIMEOUT_MS else FETCH_TIMEOUT_MS,
                                )
                            )
                        } catch (e: Exception) {
                            LogUtil.e(AppConfig.TAG, "Vpn5gSync: fetch failed for $url", e)
                            null
                        }
                    }
                }.awaitAll()
            }
            for ((url, body) in bodies) {
                if (body.isNullOrBlank()) continue
                anyFetchOk = true
                if (url == LIVE_JSON) {
                    // Small curated list: always fully ping-tested.
                    candidates += parseLiveJson(body)
                } else {
                    // Some sources publish one base64 blob instead of
                    // plain URI lines (MatinGhanbari super-sub, and our
                    // own HF sub.txt) — decode first, else use as-is.
                    val text = decodeSubscriptionBody(body)
                    if (Vpn5gConstants.isCappedSource(url)) {
                        // Large GitHub files: ping-test only a spread of 300 lines.
                        candidates += parseUriLines(
                            text,
                            Vpn5gConstants.GITHUB_SOURCE_PING_CAP,
                        )
                    } else {
                        candidates += parseUriLines(text, Int.MAX_VALUE)
                    }
                }
            }

            if (!anyFetchOk) {
                // Nothing fetched: keep the last good list untouched.
                return@withContext Vpn5gSyncResult(offline = true, notice = remoteConfig.notice)
            }
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_SOURCES, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.PARSE_VALIDATE, 0f)

            val subId = ensureSubscriptionId()

            val (unique, invalid) = parseValidateCandidates(candidates, subId)
            if (invalid > 0) {
                LogUtil.i(AppConfig.TAG, "Vpn5gSync: dropped $invalid invalid configs")
            }

            onProgress?.invoke(Vpn5gStartupProgress.Stage.PARSE_VALIDATE, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.TCP_PING, 0f)
            val live = tcpPingCandidates(unique)
            val dropped = unique.size - live.size
            onProgress?.invoke(Vpn5gStartupProgress.Stage.TCP_PING, 1f)

            val (perCountry, realPingCount) = publishLiveProfiles(
                context = context,
                live = live,
                subId = subId,
                // v1.1.26 (fast manual refresh): publish the
                // TCP-live list immediately; the real-ping probe
                // (45 s budget) must not hold the refresh UI past
                // the 5 s budget. Verification continues in the
                // background warm pool, which reloads the list.
                skipRealProbe = skipRealProbe || fast,
                probeCount = Vpn5gConstants.REAL_PING_CANDIDATES,
                probeBudgetMs = Vpn5gConstants.REAL_PING_BUDGET_MS,
                onProgress = onProgress,
            )

            Vpn5gSyncResult(
                fetched = candidates.size,
                live = live.size,
                dropped = dropped,
                invalid = invalid,
                realPingCount = realPingCount,
                perCountry = perCountry,
                offline = false,
                notice = remoteConfig.notice,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: refresh failed", e)
            Vpn5gSyncResult(offline = true)
        }
    }

    // v1.1.16 (first screen < 20 s): the quick first-screen refresh.
    // The full refresh fetches 8 sources SEQUENTIALLY (10 s timeout
    // each), TCP-pings everything and real-probes 24 candidates with a
    // 45 s budget — minutes on a fresh install, and the empty-cache
    // path blocked entry on it. The quick path fetches the sources IN
    // PARALLEL with 5 s timeouts, validates, TCP-pings a spread
    // sample, and real-probes the fastest 24 with a 10 s budget,
    // inside the ViewModel's 20 s first-screen deadline (see
    // STARTUP_DEADLINE_MS in Vpn5gMainActivity). Whatever verifies
    // becomes the free list immediately; the full refresh still runs
    // afterwards in the background and merges the rest.
    // v1.1.17: the quick path used to fetch ONLY the two small HF
    // sources (live_configs.json + sub.txt) — on a slow/filtered
    // connection those alone often yield too few candidates for the
    // probe to verify anything, so the first paint was empty and the
    // free list only arrived minutes later via the background
    // refresh. Now it fetches ALL sources in parallel (the full
    // refresh's source set), with a small per-source sample for the
    // large GitHub files.
    private const val QUICK_FETCH_TIMEOUT_MS = 5_000
    // v1.1.18: 24 candidates (was 16) so the launch list can hold the
    // full 20 verified free servers Aref ordered (2026-11-25 §1) —
    // a 16-candidate batch could verify at most 16, so the free
    // tier started short. Budget 10 s (was 8 s); the 15 s total
    // budget and the 20 s first-screen deadline are unchanged.
    private const val QUICK_PROBE_CANDIDATES = 24
    /** Large GitHub files: this many spread lines each, quick path. */
    private const val QUICK_CAPPED_SOURCE_LINES = 40
    /** Validated candidates TCP-pinged in the quick path, at most. */
    private const val QUICK_CANDIDATE_CAP = 150
    private const val QUICK_PROBE_BUDGET_MS = 10_000L
    /** The whole quick path must finish inside this budget. */
    const val QUICK_TOTAL_BUDGET_MS = 15_000L

    /**
     * The quick probe batch (pure): the fastest [QUICK_PROBE_CANDIDATES]
     * candidates by TCP delay whose identity has not already been proven
     * real. Covered by JVM unit tests.
     */
    fun selectQuickCandidates(
        candidates: List<Vpn5gRealPing.RealPingCandidate>,
        tcpDelays: Map<String, Long>,
        knownReal: Set<String>,
    ): List<Vpn5gRealPing.RealPingCandidate> {
        return Vpn5gRealPing.selectCandidates(
            candidates, tcpDelays, knownReal, QUICK_PROBE_CANDIDATES,
        )
    }

    /**
     * Evenly-spread sample of at most [cap] items (pure, v1.1.17):
     * when the list is longer, keeps every Nth item so the sample
     * covers the whole list instead of just its head — the quick
     * path uses it to bound the TCP stage inside the 20 s
     * first-screen deadline while still sampling every source.
     * Covered by JVM unit tests.
     */
    internal fun <T> spreadSample(items: List<T>, cap: Int): List<T> {
        if (items.size <= cap) return items
        return List(cap) { i -> items[(i.toLong() * items.size / cap).toInt()] }
    }

    /**
     * The standby top-up batch (pure, v1.1.17): when the
     * real-verified pool already holds
     * [Vpn5gConstants.STANDBY_POOL_MIN] or more servers, no batch is
     * needed; otherwise the fastest unproven candidates, up to one
     * probe batch. Covered by JVM unit tests.
     */
    fun standbyTopUpBatch(
        candidates: List<Vpn5gRealPing.RealPingCandidate>,
        tcpDelays: Map<String, Long>,
        knownReal: Set<String>,
        verifiedFreeCount: Int,
        batchSize: Int = Vpn5gConstants.REAL_PING_CANDIDATES,
    ): List<Vpn5gRealPing.RealPingCandidate> {
        if (verifiedFreeCount >= Vpn5gConstants.STANDBY_POOL_MIN) return emptyList()
        return Vpn5gRealPing.selectCandidates(candidates, tcpDelays, knownReal, batchSize)
    }

    /**
     * Standby top-up (v1.1.17): probes further batches of the
     * fastest unproven candidates until the real-verified pool
     * reaches [Vpn5gConstants.STANDBY_POOL_MIN] or the candidates
     * run out (at most [Vpn5gConstants.MAX_STANDBY_TOPUP_BATCHES]
     * extra batches). The batch decision is the pure
     * [standbyTopUpBatch]; this is the I/O driver. A batch that
     * verifies nothing and fails nothing ends the loop (no point
     * spinning on a dead pool). Never throws.
     */
    private suspend fun topUpStandbyPool(
        context: Context,
        candidates: List<Vpn5gRealPing.RealPingCandidate>,
        tcpDelays: Map<String, Long>,
        probeBudgetMs: Long,
    ) {
        repeat(Vpn5gConstants.MAX_STANDBY_TOPUP_BATCHES) {
            val knownReal = Vpn5gRealPing.loadRealPingKeys()
            val verifiedFree = candidates.count { it.dedupeKey in knownReal }
            val batch = standbyTopUpBatch(candidates, tcpDelays, knownReal, verifiedFree)
            if (batch.isEmpty()) return
            val probe = Vpn5gRealPing.probeCandidates(context, batch, probeBudgetMs)
            recordProbeHealth(batch, probe)
            if (probe.verifiedKeys.isNotEmpty()) {
                Vpn5gRealPing.addRealPingKeys(probe.verifiedKeys)
            }
            probe.realDelays.forEach { (guid, delay) ->
                MmkvManager.encodeServerTestDelayMillis(guid, delay)
            }
            probe.failedGuids.forEach { guid ->
                MmkvManager.encodeServerTestDelayMillis(guid, -1L)
            }
            LogUtil.i(
                AppConfig.TAG,
                "Vpn5gSync: standby top-up " +
                    "${probe.verifiedKeys.size}/${probe.tested} verified",
            )
            if (probe.verifiedKeys.isEmpty() && probe.failedGuids.isEmpty()) return
        }
    }

    /**
     * Quick first-screen refresh (v1.1.16, widened v1.1.17). Same
     * contract as [refresh] but bounded: ALL sources in parallel
     * with 5 s timeouts, a spread sample of at most 150 validated
     * candidates for the TCP stage, and a 24-candidate / 10 s real
     * probe (v1.1.18: was 16 / 8 s). When every source fails, the STORED profiles (bundled
     * fallback on a fresh install) are real-probed instead, so the
     * free list can still verify without any fetch succeeding.
     * Never throws.
     */
    suspend fun quickRefresh(
        context: Context,
        skipRealProbe: Boolean = false,
        onProgress: ((Vpn5gStartupProgress.Stage, Float) -> Unit)? = null,
    ): Vpn5gSyncResult = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_CONFIG, 0f)
            val remoteConfig = Vpn5gRemoteConfig.fetch()
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_CONFIG, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_SOURCES, 0f)
            // v1.1.17: fetch ALL sources in parallel (the full
            // refresh's source set), not just the two small ones —
            // on a slow/filtered connection the two small sources
            // alone often yield too few candidates for the probe to
            // verify anything. Large GitHub files are sampled at 40
            // lines each in the quick path (300 in the full refresh).
            val quickSources = linkedSetOf<String>()
            quickSources += Vpn5gConstants.DEFAULT_CONFIG_SOURCES
            remoteConfig.config_sources?.forEach { url ->
                if (!url.isNullOrBlank()) quickSources.add(url.trim())
            }
            val bodies = coroutineScope {
                quickSources.map { url ->
                    async {
                        url to runCatching {
                            HttpUtil.getUrlContent(
                                UrlContentRequest(url = url, timeout = QUICK_FETCH_TIMEOUT_MS)
                            )
                        }.getOrNull()
                    }
                }.awaitAll().toMap()
            }
            val candidates = mutableListOf<Candidate>()
            var anyFetchOk = false
            bodies.forEach { (url, body) ->
                if (body.isNullOrBlank()) return@forEach
                anyFetchOk = true
                if (url == LIVE_JSON) {
                    candidates += parseLiveJson(body)
                } else {
                    val text = decodeSubscriptionBody(body)
                    if (Vpn5gConstants.isCappedSource(url)) {
                        candidates += parseUriLines(text, QUICK_CAPPED_SOURCE_LINES)
                    } else {
                        candidates += parseUriLines(text, 100)
                    }
                }
            }
            onProgress?.invoke(Vpn5gStartupProgress.Stage.FETCH_SOURCES, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.PARSE_VALIDATE, 0f)
            val subId = ensureSubscriptionId()
            val (validated, invalid) = parseValidateCandidates(candidates, subId)
            if (invalid > 0) {
                LogUtil.i(AppConfig.TAG, "Vpn5gSync: quick dropped $invalid invalid configs")
            }
            // v1.1.17: the quick path TCP-pings at 8-way concurrency
            // with a 1.5 s timeout — hundreds of candidates would
            // blow the 20 s first-screen deadline before any probe
            // runs. An evenly-spread sample of 150 keeps the whole
            // quick path inside the deadline while still sampling
            // every source.
            val unique = spreadSample(validated, QUICK_CANDIDATE_CAP)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.PARSE_VALIDATE, 1f)
            onProgress?.invoke(Vpn5gStartupProgress.Stage.TCP_PING, 0f)
            val live = tcpPingCandidates(unique)
            val dropped = unique.size - live.size
            onProgress?.invoke(Vpn5gStartupProgress.Stage.TCP_PING, 1f)
            if (live.isEmpty()) {
                // Nothing usable from the network: verify the STORED
                // profiles (bundled fallback) instead — the real probe
                // IS the test there, no TCP stage needed.
                val storedCount = probeStoredProfiles(context, subId, skipRealProbe)
                onProgress?.invoke(Vpn5gStartupProgress.Stage.DONE, 1f)
                return@withContext Vpn5gSyncResult(
                    fetched = candidates.size,
                    live = 0,
                    dropped = dropped,
                    invalid = invalid,
                    realPingCount = storedCount,
                    offline = !anyFetchOk,
                    notice = remoteConfig.notice,
                )
            }
            val (perCountry, realPingCount) = publishLiveProfiles(
                context = context,
                live = live,
                subId = subId,
                skipRealProbe = skipRealProbe,
                probeCount = QUICK_PROBE_CANDIDATES,
                probeBudgetMs = QUICK_PROBE_BUDGET_MS,
                onProgress = onProgress,
            )
            Vpn5gSyncResult(
                fetched = candidates.size,
                live = live.size,
                dropped = dropped,
                invalid = invalid,
                realPingCount = realPingCount,
                perCountry = perCountry,
                offline = false,
                notice = remoteConfig.notice,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: quick refresh failed", e)
            Vpn5gSyncResult(offline = true)
        }
    }

    /**
     * Records probe outcomes into the health-score memory (v1.1.24):
     * verified identities gain, failed GUIDs (mapped back to their
     * identity) lose. Best effort; never throws.
     */
    private fun recordProbeHealth(
        candidates: List<Vpn5gRealPing.RealPingCandidate>,
        probe: Vpn5gRealPing.ProbeResult,
    ) {
        val nowMs = System.currentTimeMillis()
        probe.verifiedKeys.forEach { key ->
            Vpn5gHealthScore.recordOutcome(key, success = true, nowMs = nowMs)
        }
        val keyByGuid = candidates.associate { it.guid to it.dedupeKey }
        probe.failedGuids.forEach { guid ->
            keyByGuid[guid]?.let { key ->
                Vpn5gHealthScore.recordOutcome(key, success = false, nowMs = nowMs)
            }
        }
    }

    /**
     * Warm standby pool (v1.1.24, Lantern-inspired): while the VPN
     * is idle, real-probe a bounded batch of the stored profiles so
     * proven servers are already waiting at tap-time (the 1.1.14
     * feel — at least [Vpn5gConstants.STANDBY_POOL_MIN] verified
     * servers ready). Unproven identities go first; when everything
     * stored is already proven, a spread of proven ones is
     * re-probed to refresh their delays. Bounded by
     * [Vpn5gConstants.WARM_PROBE_BATCH] /
     * [Vpn5gConstants.WARM_PROBE_BUDGET_MS] and called on
     * [Vpn5gConstants.WARM_PROBE_INTERVAL_MS] from the ViewModel —
     * never while the VPN runs (no throwaway core next to the live
     * one). Returns the verified count. Never throws.
     */
    suspend fun warmProbeStoredProfiles(context: Context): Int {
        val subId = ensureSubscriptionId()
        return probeStoredProfiles(
            context, subId, skipRealProbe = false,
            batchSize = Vpn5gConstants.WARM_PROBE_BATCH,
            budgetMs = Vpn5gConstants.WARM_PROBE_BUDGET_MS,
            reprobeProven = true,
        )
    }

    /**
     * Real-probes the stored profiles (bundled fallback) directly: the
     * first [QUICK_PROBE_CANDIDATES] unproven identities, bounded by
     * [QUICK_PROBE_BUDGET_MS]. Verified identities join the real-ping
     * set (so the servers become free / auto-pickable); failed GUIDs
     * have their ping withdrawn. Returns the verified count. Never
     * throws.
     */
    private suspend fun probeStoredProfiles(
        context: Context,
        subId: String,
        skipRealProbe: Boolean,
        batchSize: Int = QUICK_PROBE_CANDIDATES,
        budgetMs: Long = QUICK_PROBE_BUDGET_MS,
        reprobeProven: Boolean = false,
    ): Int {
        if (skipRealProbe) return 0
        return try {
            val guids = MmkvManager.decodeServerList(subId)
            if (guids.isEmpty()) return 0
            val candidates = guids.mapNotNull { guid ->
                val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                Vpn5gRealPing.RealPingCandidate(
                    guid,
                    Vpn5gServerValidator.dedupeKey(profile),
                )
            }
            val knownReal = Vpn5gRealPing.loadRealPingKeys()
            var batch = Vpn5gRealPing.selectCandidates(
                candidates,
                emptyMap(),
                knownReal,
                batchSize,
            )
            if (batch.isEmpty() && reprobeProven) {
                // Warm pool (v1.1.24): everything stored is already
                // proven — re-probe a spread of proven servers to
                // refresh their real delays so the standby pool
                // stays fresh instead of aging out. The spread
                // rotates by time bucket so the same head of the
                // list is not re-probed (and re-bonused) every cycle.
                val proven = candidates.filter { it.dedupeKey in knownReal }
                val rotated = if (proven.isEmpty()) {
                    proven
                } else {
                    val offset = ((System.currentTimeMillis() /
                        Vpn5gConstants.WARM_PROBE_INTERVAL_MS) % proven.size).toInt()
                    proven.drop(offset) + proven.take(offset)
                }
                batch = spreadSample(rotated, batchSize)
            }
            if (batch.isEmpty()) return 0
            val probe = Vpn5gRealPing.probeCandidates(context, batch, budgetMs)
            recordProbeHealth(batch, probe)
            if (probe.verifiedKeys.isNotEmpty()) {
                Vpn5gRealPing.addRealPingKeys(probe.verifiedKeys)
            }
            probe.realDelays.forEach { (guid, delay) ->
                MmkvManager.encodeServerTestDelayMillis(guid, delay)
            }
            probe.failedGuids.forEach { guid ->
                MmkvManager.encodeServerTestDelayMillis(guid, -1L)
            }
            probe.verifiedKeys.size
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: stored probe failed", e)
            0
        }
    }

    /**
     * Decodes a subscription body that is published as one base64 blob
     * instead of plain config-URI lines (v1.1.6: MatinGhanbari super-sub,
     * and our own HF sub.txt — which contributed zero configs before this,
     * because parseUriLines found no scheme line in the blob). A body that
     * already carries a config-URI line is returned unchanged; a body that
     * is not valid base64, or decodes to text without any config-URI line,
     * is also returned unchanged. Pure — java.util.Base64 (desugared, so
     * it works on minSdk 24) and covered by JVM unit tests.
     */
    internal fun decodeSubscriptionBody(body: String): String {
        if (CONFIG_SCHEMES.any { scheme -> body.contains("$scheme://") }) return body
        val compact = body.filterNot { it.isWhitespace() }
        if (compact.isEmpty() || compact.length % 4 != 0) return body
        if (!compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) {
            return body
        }
        return try {
            val decoded = String(
                java.util.Base64.getDecoder().decode(compact),
                Charsets.UTF_8,
            )
            if (CONFIG_SCHEMES.any { scheme -> decoded.contains("$scheme://") }) decoded else body
        } catch (_: IllegalArgumentException) {
            body
        }
    }

    private fun parseLiveJson(body: String): List<Candidate> {
        val entries = try {
            JsonUtil.fromJsonSafe(body, Array<LiveConfigEntry>::class.java)?.toList()
                ?: return emptyList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: live_configs.json parse failed", e)
            return emptyList()
        }
        return entries.mapNotNull { entry ->
            val uri = entry.uri?.trim().orEmpty()
            if (uri.isEmpty()) return@mapNotNull null
            Candidate(
                uri = uri,
                country = entry.country?.trim().orEmpty().ifEmpty { "بین‌الملل" },
            )
        }
    }

    /**
     * Parses config-URI lines. When [maxLines] is smaller than the number of
     * URI lines, an evenly-spaced spread of [maxLines] lines is kept instead
     * of just the top of the file, so one refresh samples the whole source.
     */
    private fun parseUriLines(body: String, maxLines: Int): List<Candidate> {
        val lines = body.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            if (CONFIG_SCHEMES.none { trimmed.startsWith("$it://") }) return@mapNotNull null
            trimmed
        }
        val sampled = if (lines.size <= maxLines) {
            lines
        } else {
            // Evenly spaced spread across the whole file.
            List(maxLines) { i -> lines[(i.toLong() * lines.size / maxLines).toInt()] }
        }
        return sampled.map { Candidate(uri = it, country = "بین‌الملل") }
    }

    /**
     * Free-eligible servers, real-pinged first (v1.1.20): a server is
     * free-eligible with a REAL through-the-proxy ping, or as a seed
     * server (build-time verified, ping unknown — never a proven-dead
     * one, ping -1). Real-pinged servers sort first by delay; seeds
     * sort after them in list order (unknown ping, never a made-up
     * number). Pure, JVM-tested.
     */
    internal fun freeEligibleSorted(servers: List<Vpn5gServerUi>): List<Vpn5gServerUi> {
        return servers
            .filter {
                (it.hasRealPing && it.pingMillis > 0) ||
                    (it.isSeed && it.pingMillis >= 0)
            }
            .sortedWith(
                compareBy(
                    { !(it.hasRealPing && it.pingMillis > 0) },
                    { if (it.pingMillis <= 0L) Long.MAX_VALUE else it.pingMillis },
                ),
            )
    }

    /**
     * Free tier (v1.1.2, v1.1.7): the [FREE_SERVER_COUNT] fastest-pinging configs
     * are free; every other config is premium-locked. Pure function (no
     * Android APIs) so it is covered by JVM unit tests. Only servers with
     * a REAL through-the-proxy ping (hasRealPing and pingMillis > 0) can
     * be free — a TCP-only ping is never a free server and never tried
     * first, because a middlebox can fake a 1 ms TCP handshake.
     * v1.1.20: a build-time verified SEED server is free-eligible too
     * (unknown ping, sorted after the real-pinged ones) — otherwise a
     * fresh install shows no free server at all until the first real
     * probe finishes, and the first screen loses the user.
     */
    fun selectFreeGuids(
        servers: List<Vpn5gServerUi>,
        count: Int = FREE_SERVER_COUNT,
    ): Set<String> {
        return freeEligibleSorted(servers)
            .take(count)
            .map { it.guid }
            .toSet()
    }

    /**
     * Which servers are pickable (v1.1.11, gift tier v1.1.14):
     * subscribers get the whole powerful pool — every server with a
     * real through-the-proxy ping, fastest first, no 20-config cap;
     * the GIFT tier gets the [GIFT_SERVER_COUNT] fastest real-pinged
     * servers; free users get the top [FREE_SERVER_COUNT] only. Pure,
     * JVM-tested. The "free" flag is what the pickers, the manual
     * validation and the failover all gate on, so one flag carries the
     * whole tier benefit.
     * v1.1.20: seed servers (build-time verified) count in every tier
     * the same way as in [selectFreeGuids] — a fresh install's pool is
     * the seed until live data replaces it.
     * v1.1.22 (paid-tier fill): on a filtered network the verified
     * set can stay tiny (Aref's phone: 2 free servers) — then a gift
     * or subscription would unlock nothing new and the locks would
     * stay shut after a redeem. Paid tiers therefore FILL from the
     * fastest TCP-pinged servers (ping > 0, never real-verified) up
     * to the tier's count — verified servers always first, so the
     * auto picker still prefers them and the tunnel verification +
     * failover still guard a dead pick. Servers whose ping was
     * withdrawn (-1, failed the real test) are never filled in. The
     * FREE tier is unchanged: TCP-only servers are never free.
     */
    fun freeGuidsFor(
        servers: List<Vpn5gServerUi>,
        tier: Vpn5gSubscriptionStatus.Vpn5gTier,
    ): Set<String> {
        val eligible = freeEligibleSorted(servers)
        val eligibleGuids = eligible.map { it.guid }.toSet()
        // Paid-tier fill (v1.1.22): fastest TCP-pinged servers not
        // already eligible, so a gift/sub unlocks real countries even
        // when the verified set is tiny. Failed servers (ping -1)
        // are never filled in.
        val fill = servers
            .filter { it.guid !in eligibleGuids && !it.hasRealPing && it.pingMillis > 0 }
            .sortedBy { it.pingMillis }
        return when (tier) {
            Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER -> (eligible + fill)
                .map { it.guid }
                .toSet()
            Vpn5gSubscriptionStatus.Vpn5gTier.GIFT -> (eligible + fill)
                .take(Vpn5gConstants.GIFT_SERVER_COUNT)
                .map { it.guid }
                .toSet()
            Vpn5gSubscriptionStatus.Vpn5gTier.FREE -> eligible
                .take(FREE_SERVER_COUNT)
                .map { it.guid }
                .toSet()
        }
    }

    /** Boolean overload (v1.1.11): subscriber → full pool, else free tier. */
    fun freeGuidsFor(
        servers: List<Vpn5gServerUi>,
        subscriber: Boolean,
    ): Set<String> {
        return freeGuidsFor(
            servers,
            if (subscriber) {
                Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER
            } else {
                Vpn5gSubscriptionStatus.Vpn5gTier.FREE
            },
        )
    }

    /**
     * Reads the stored "VPN5G IRBOT" group and builds home-screen sections.
     * Free countries first (by server count), then locked ones. Each server
     * row carries the flag + Persian country name parsed from its remark
     * (free, no I/O); servers whose remark has no country are enriched later
     * by [enrichCountries] (DNS + bundled GeoIP table) so the first paint is
     * never blocked on DNS. The free tier is the 20 fastest REAL-pinging
     * servers ([selectFreeGuids], v1.1.7); a section is "free" when it holds
     * at least one free server. v1.1.11: a subscriber (cached subscription
     * active) gets the whole real-pinged pool instead — see [freeGuidsFor].
     *
     * v1.1.15 (anti-tamper, H1): [forceFreeTier] forces the FREE pool
     * even when the cached subscription says otherwise. The ViewModel
     * passes true when the startup signature check failed — a tampered
     * build gets the free tier on screen, never a cached premium one.
     * The cached tier is only a display hint anyway: every privileged
     * action re-checks the server state before it runs.
     */
    fun loadGroupedServers(
        forceFreeTier: Boolean = false,
        /** v1.1.22: when given, this tier decides the free flags
         * instead of the MMKV-cached subscription — the post-redeem
         * rebuild passes the MERGED tier (cached + just-granted), so
         * the locks open from the grant itself and never from a stale
         * cache read. Null = derive from the cache, as before. */
        tierOverride: Vpn5gSubscriptionStatus.Vpn5gTier? = null,
    ): List<Vpn5gCountryUi> {
        val subId = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url == LIVE_JSON }?.guid
            ?: return emptyList()
        val guids = MmkvManager.decodeServerList(subId)
        val selected = MmkvManager.getSelectServer()
        val realPingKeys = Vpn5gRealPing.loadRealPingKeys()
        // v1.1.20 (seed): the bundled snapshot's build-time verified
        // identities — free-eligible with an unknown ping until the
        // real probe measures them.
        val seedKeys = loadSeedKeys()
        val byCountry = linkedMapOf<String, MutableList<Vpn5gServerUi>>()
        val labels = mutableMapOf<String, String>()
        // v1.1.14: declared here, before the mapping below uses it —
        // servers verified on the current operator are marked so the
        // auto pick can prefer them (own history only).
        val operatorKey = Vpn5gOperator.currentKey()
        val operatorVerifiedKeys = operatorKey
            ?.let { Vpn5gOperator.verifiedKeysFor(it) }.orEmpty()
        guids.forEach { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val key = normalizeCountry(profile.country).ifEmpty { "بین‌الملل" }
            val ping = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            val dedupeKey = Vpn5gServerValidator.dedupeKey(profile)
            val label = profile.country?.trim().orEmpty().ifEmpty { key }
            labels.putIfAbsent(key, label)
            val remarkCountry = Vpn5gGeoIp.countryFromRemark(profile.remarks)
            byCountry.getOrPut(key) { mutableListOf() }.add(
                Vpn5gServerUi(
                    guid = guid,
                    name = profile.remarks.ifBlank { profile.server.orEmpty() },
                    country = key,
                    pingMillis = ping,
                    isSelected = guid == selected,
                    // v1.1.7: real ping is keyed by server identity, so it
                    // survives the GUID re-issue of every refresh.
                    hasRealPing = dedupeKey in realPingKeys,
                    // v1.1.20 (seed): a build-time verified seed server
                    // is free-eligible with an unknown ping; a seed the
                    // real probe already failed (ping withdrawn to -1)
                    // loses its seed trust at once.
                    isSeed = dedupeKey in seedKeys && ping >= 0L,
                    flag = remarkCountry?.flag ?: "🌐",
                    countryNameFa = remarkCountry?.nameFa ?: "",
                    countryCode = remarkCountry?.code ?: "",
                    host = profile.server.orEmpty(),
                    // EConfigType schemes carry a "://" suffix ("vless://").
                    protocol = profile.configType.protocolScheme
                        .removeSuffix("://").lowercase(),
                    operatorVerified = dedupeKey
                        in operatorVerifiedKeys,
                    dedupeKey = dedupeKey,
                )
            )
        }
        // v1.1.11: subscribers get the whole real-pinged pool (no
        // 20-config cap); free users get the top 20. The cached
        // subscription decides — it is refreshed on app start and on
        // connect, and a stale cache can only under-grant, never crash.
        // v1.1.14: the GIFT tier gets the top 50 instead. Servers that
        // verified on the CURRENT operator are marked (own history
        // only) so the auto pick can prefer them.
        // v1.1.18 (Model B): the tier is volume-aware — a grant whose
        // volume cap is reached is FREE here too, so the pool, the
        // country locks and the auto picker all follow the real
        // state, not just the subscription text.
        val tier = if (forceFreeTier) {
            Vpn5gSubscriptionStatus.Vpn5gTier.FREE
        } else if (tierOverride != null) {
            tierOverride
        } else {
            val nowSeconds = System.currentTimeMillis() / 1000
            Vpn5gSubscriptionStatus.tierOf(
                Vpn5gSubscriptionStatus.loadEffective(nowSeconds),
                nowSeconds,
                Vpn5gVolumeStore.consumedState(),
            )
        }
        val freeGuids = freeGuidsFor(byCountry.values.flatten(), tier)
        val sections = byCountry.map { (key, servers) ->
            val marked = servers
                .map { it.copy(isFree = it.guid in freeGuids) }
                .sortedBy { if (it.pingMillis <= 0L) Long.MAX_VALUE else it.pingMillis }
            Vpn5gCountryUi(
                label = labels[key] ?: key,
                isFree = marked.any { it.isFree },
                servers = marked,
            )
        }
        return sections.sortedWith(
            compareByDescending<Vpn5gCountryUi> { it.isFree }
                .thenByDescending { it.servers.size }
                .thenBy { it.label }
        )
    }

    /**
     * Background GeoIP enrichment: resolves the host of every server whose
     * remark carried no country and fills in its flag + Persian name from
     * the bundled table. Bounded: 32-host batches, 16-way concurrency, and
     * a global [budgetMs] so a huge list never stalls the UI — servers not
     * resolved in budget keep the globe and are retried on the next refresh
     * (DNS results are cached per host). Never throws.
     */
    suspend fun enrichCountries(
        context: Context,
        sections: List<Vpn5gCountryUi>,
        budgetMs: Long = 6_000L,
    ): List<Vpn5gCountryUi> = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + budgetMs
        val resolved = mutableMapOf<String, Vpn5gGeoIp.Country>()
        val unknown = sections.flatMap { it.servers }.filter { it.countryCode.isEmpty() }
        if (unknown.isEmpty()) return@withContext sections
        val semaphore = Semaphore(16)
        runCatching {
            unknown.chunked(32).forEach { batch ->
                if (System.currentTimeMillis() >= deadline) return@forEach
                val results = kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                    coroutineScope {
                        batch.map { server ->
                            async {
                                semaphore.withPermit {
                                    server.guid to Vpn5gGeoIp.countryForHost(context, server.host)
                                }
                            }
                        }.awaitAll()
                    }
                } ?: return@forEach
                results.forEach { (guid, country) ->
                    if (country.code.isNotEmpty()) resolved[guid] = country
                }
            }
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: GeoIP enrichment failed", e)
        }
        if (resolved.isEmpty()) return@withContext sections
        sections.map { section ->
            section.copy(
                servers = section.servers.map { server ->
                    val country = resolved[server.guid] ?: return@map server
                    server.copy(
                        flag = country.flag,
                        countryNameFa = country.nameFa,
                        countryCode = country.code,
                    )
                }
            )
        }
    }

    /**
     * The bundled seed's server identities (v1.1.20): the dedupe keys
     * of the servers imported from assets/vpn5g/fallback_servers.json.
     * Stored as a comma-separated list in MMKV, same pattern as the
     * real-ping keys. A seed server is free-eligible with an unknown
     * ping until the real probe measures it; the first live publish
     * clears the set, so the seed never outlives the live data that
     * replaces it.
     */
    fun loadSeedKeys(): Set<String> {
        val csv = MmkvManager.decodeSettingsString(Vpn5gConstants.KEY_SEED_SERVER_KEYS_CSV)
            .orEmpty()
        if (csv.isBlank()) return emptySet()
        return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Replaces the stored seed identities (called by the fallback import). */
    fun saveSeedKeys(keys: Collection<String>) {
        MmkvManager.encodeSettings(
            Vpn5gConstants.KEY_SEED_SERVER_KEYS_CSV,
            keys.filter { it.isNotBlank() }.joinToString(","),
        )
    }

    /** Drops the stored seed identities (called when live data replaces the seed). */
    fun clearSeedKeys() {
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_SEED_SERVER_KEYS_CSV, "")
    }

    /**
     * Bundled fallback snapshot (assets/vpn5g/fallback_servers.json).
     *
     * Used ONLY when the stored group is empty and a live refresh failed
     * (no internet, or the sources are unreachable from the national
     * network): the snapshot — taken from the live sources when v1.1 was
     * built — is imported so the list is never empty on first launch. The
     * entries are NOT ping-tested here (there is no network to test with);
     * they show unknown ping and the UI marks the list offline. The next
     * successful refresh replaces them with ping-tested servers. Returns
     * the number of servers imported.
     * v1.1.20: the snapshot is a fresh seed of >= 20 servers TCP-verified
     * at build time, and the import records their identities as seed keys
     * ([saveSeedKeys]) so the free list is visible immediately and
     * auto-connect fires on the best seed server at once.
     */
    suspend fun loadBundledFallback(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val subId = ensureSubscriptionId()
            if (MmkvManager.decodeServerList(subId).isNotEmpty()) return@withContext 0
            val body = context.applicationContext.assets.open(ASSET_FALLBACK_SERVERS)
                .bufferedReader().use { it.readText() }
            val entries = JsonUtil.fromJsonSafe(body, Array<FallbackEntry>::class.java)
                ?.toList().orEmpty()
            if (entries.isEmpty()) return@withContext 0
            val profiles = linkedMapOf<String, ProfileItem>()
            val rawConfigs = mutableMapOf<String, String>()
            val seenKeys = mutableSetOf<String>()
            var invalid = 0
            entries.forEach { entry ->
                val uri = entry.uri?.trim().orEmpty()
                if (uri.isEmpty()) return@forEach
                val profile = try {
                    AngConfigManager.parseConfig(uri, subId, null)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Vpn5gSync: fallback parse failed", e)
                    null
                } ?: return@forEach
                // Same validation as the live path (v1.1.4 §5): a broken
                // entry in the bundled snapshot is dropped, never shown.
                if (!Vpn5gServerValidator.validate(profile).isValid) {
                    invalid++
                    return@forEach
                }
                Vpn5gServerValidator.normalize(profile)
                if (!seenKeys.add(Vpn5gServerValidator.dedupeKey(profile))) return@forEach
                profile.country = entry.country?.trim().orEmpty().ifEmpty { "بین‌الملل" }
                val guid = Utils.getUuid()
                profiles[guid] = profile
                rawConfigs[guid] = ""
            }
            if (invalid > 0) {
                LogUtil.i(AppConfig.TAG, "Vpn5gSync: fallback dropped $invalid invalid entries")
            }
            if (profiles.isEmpty()) return@withContext 0
            MmkvManager.saveServerProfiles(
                profiles = profiles,
                rawConfigs = rawConfigs,
                subscriptionId = subId,
                append = false,
            )
            // v1.1.20 (seed): the imported identities are the seed —
            // free-eligible with an unknown ping until the real probe
            // measures them, so the first screen is never empty.
            saveSeedKeys(seenKeys)
            LogUtil.i(AppConfig.TAG, "Vpn5gSync: bundled fallback imported ${profiles.size} servers")
            profiles.size
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: bundled fallback failed", e)
            0
        }
    }

    /** Imports a single config URI (deep link) into the VPN 5G group. */
    /**
     * Imports one deep-linked config URI into the VPN 5G group. v1.1.7:
     * the config is validated with the encrypted-only filter before it
     * is stored — an unencrypted deep link is refused, so the red
     * "unencrypted node" dialog can never appear from an imported link.
     */
    fun importUri(uri: String): Boolean {
        return try {
            val subId = ensureSubscriptionId()
            val profile = AngConfigManager.parseConfig(uri.trim(), subId, null) ?: return false
            if (!Vpn5gServerValidator.validate(profile).isValid) {
                LogUtil.i(AppConfig.TAG, "Vpn5gSync: importUri rejected unencrypted/invalid config")
                return false
            }
            if (profile.country.isBlank()) profile.country = "بین‌الملل"
            val guid = Utils.getUuid()
            MmkvManager.saveServerProfiles(
                profiles = mapOf(guid to profile),
                rawConfigs = mapOf(guid to uri.trim()),
                subscriptionId = subId,
                append = true,
            )
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: importUri failed", e)
            false
        }
    }

    /** Imports pasted/file text (URIs and/or subscription lines) into the VPN 5G group. */
    fun importText(text: String): Pair<Int, Int> {
        return try {
            val subId = ensureSubscriptionId()
            AngConfigManager.importBatchConfig(text, subId, true)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: importText failed", e)
            0 to 0
        }
    }
}
