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

    suspend fun refresh(): Vpn5gSyncResult = withContext(Dispatchers.IO) {
        try {
            val remoteConfig = Vpn5gRemoteConfig.fetch()

            val sources = linkedSetOf<String>()
            sources += Vpn5gConstants.DEFAULT_CONFIG_SOURCES
            remoteConfig.config_sources?.forEach { url ->
                if (!url.isNullOrBlank()) sources.add(url.trim())
            }

            val candidates = mutableListOf<Candidate>()
            var anyFetchOk = false
            for (url in sources) {
                val body = try {
                    HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = FETCH_TIMEOUT_MS))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Vpn5gSync: fetch failed for $url", e)
                    null
                }
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

            val subId = ensureSubscriptionId()

            // Parse first (local, cheap), then validate, then TCP-ping the
            // parsed host:port. Invalid configs are dropped here — never
            // pinged, never stored, never shown (v1.1.4 §5).
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
            // Dedupe by server identity (scheme://host:port:uuid-or-password),
            // not by raw URI text — the same server under a different remark
            // is one server.
            val unique = validated.distinctBy { (profile, _) ->
                Vpn5gServerValidator.dedupeKey(profile)
            }
            if (invalid > 0) {
                LogUtil.i(AppConfig.TAG, "Vpn5gSync: dropped $invalid invalid configs")
            }

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
            val live = pinged.filter { it.second >= 0 }
            val dropped = pinged.size - live.size

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
                // Record the measured ping for the home screen.
                live.forEachIndexed { index, (_, delay, _) ->
                    val guid = profiles.keys.elementAt(index)
                    MmkvManager.encodeServerTestDelayMillis(guid, delay)
                }
            }

            Vpn5gSyncResult(
                fetched = candidates.size,
                live = live.size,
                dropped = dropped,
                invalid = invalid,
                perCountry = perCountry,
                offline = false,
                notice = remoteConfig.notice,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: refresh failed", e)
            Vpn5gSyncResult(offline = true)
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
     * Free tier (v1.1.2): the [FREE_SERVER_COUNT] fastest-pinging configs
     * are free; every other config is premium-locked. Pure function (no
     * Android APIs) so it is covered by JVM unit tests. Only servers with
     * a measured ping (pingMillis > 0) can be free — a config with no ping
     * result is never free and never tried first.
     */
    fun selectFreeGuids(
        servers: List<Vpn5gServerUi>,
        count: Int = FREE_SERVER_COUNT,
    ): Set<String> {
        return servers
            .filter { it.pingMillis > 0 }
            .sortedBy { it.pingMillis }
            .take(count)
            .map { it.guid }
            .toSet()
    }

    /**
     * Reads the stored "VPN5G IRBOT" group and builds home-screen sections.
     * Free countries first (by server count), then locked ones. Each server
     * row carries the flag + Persian country name parsed from its remark
     * (free, no I/O); servers whose remark has no country are enriched later
     * by [enrichCountries] (DNS + bundled GeoIP table) so the first paint is
     * never blocked on DNS. The free tier is the 20 fastest-pinging servers
     * ([selectFreeGuids]); a section is "free" when it holds at least one
     * free server.
     */
    fun loadGroupedServers(): List<Vpn5gCountryUi> {
        val subId = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url == LIVE_JSON }?.guid
            ?: return emptyList()
        val guids = MmkvManager.decodeServerList(subId)
        val selected = MmkvManager.getSelectServer()
        val byCountry = linkedMapOf<String, MutableList<Vpn5gServerUi>>()
        val labels = mutableMapOf<String, String>()
        guids.forEach { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val key = normalizeCountry(profile.country).ifEmpty { "بین‌الملل" }
            val ping = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
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
                    flag = remarkCountry?.flag ?: "🌐",
                    countryNameFa = remarkCountry?.nameFa ?: "",
                    countryCode = remarkCountry?.code ?: "",
                    host = profile.server.orEmpty(),
                    // EConfigType schemes carry a "://" suffix ("vless://").
                    protocol = profile.configType.protocolScheme
                        .removeSuffix("://").lowercase(),
                )
            )
        }
        val freeGuids = selectFreeGuids(byCountry.values.flatten())
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
            LogUtil.i(AppConfig.TAG, "Vpn5gSync: bundled fallback imported ${profiles.size} servers")
            profiles.size
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: bundled fallback failed", e)
            0
        }
    }

    /** Imports a single config URI (deep link) into the VPN 5G group. */
    fun importUri(uri: String): Boolean {
        return try {
            val subId = ensureSubscriptionId()
            val profile = AngConfigManager.parseConfig(uri.trim(), subId, null) ?: return false
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
