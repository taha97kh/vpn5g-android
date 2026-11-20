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
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.LIVE_JSON
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUBSCRIPTION_REMARKS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.isFreeCountry
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
 * VPN 5G live-config sync.
 *
 * Fetches live_configs.json (+ sub.txt and any extra sources from
 * app_config.json), TCP-pings every candidate, and replaces the "VPN 5G"
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
     * Finds the "VPN 5G" subscription group, creating it on first use.
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
                } else if (url == Vpn5gConstants.GITHUB_SOURCE_1 ||
                    url == Vpn5gConstants.GITHUB_SOURCE_2
                ) {
                    // Large GitHub files: ping-test only a spread of 300 lines.
                    candidates += parseUriLines(
                        body,
                        Vpn5gConstants.GITHUB_SOURCE_PING_CAP,
                    )
                } else {
                    candidates += parseUriLines(body, Int.MAX_VALUE)
                }
            }

            if (!anyFetchOk) {
                // Nothing fetched: keep the last good list untouched.
                return@withContext Vpn5gSyncResult(offline = true, notice = remoteConfig.notice)
            }

            val unique = candidates.distinctBy { it.uri }
            val subId = ensureSubscriptionId()

            // Parse first (local, cheap), then TCP-ping the parsed host:port.
            val parsed = unique.mapNotNull { candidate ->
                val profile = try {
                    AngConfigManager.parseConfig(candidate.uri, subId, null)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Vpn5gSync: parse failed", e)
                    null
                } ?: return@mapNotNull null
                profile.country = candidate.country
                profile to candidate
            }

            val semaphore = Semaphore(MAX_CONCURRENT_PINGS)
            val pinged = parsed.map { (profile, _) ->
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
                fetched = unique.size,
                live = live.size,
                dropped = dropped,
                perCountry = perCountry,
                offline = false,
                notice = remoteConfig.notice,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSync: refresh failed", e)
            Vpn5gSyncResult(offline = true)
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
     * Reads the stored "VPN5G IRBOT" group and builds home-screen sections.
     * Free countries first (by server count), then locked ones. Each server
     * row carries the flag + Persian country name parsed from its remark
     * (free, no I/O); servers whose remark has no country are enriched later
     * by [enrichCountries] (DNS + bundled GeoIP table) so the first paint is
     * never blocked on DNS.
     */
    fun loadGroupedServers(freeCountries: List<String>): List<Vpn5gCountryUi> {
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
                )
            )
        }
        val sections = byCountry.map { (key, servers) ->
            Vpn5gCountryUi(
                label = labels[key] ?: key,
                isFree = isFreeCountry(key, freeCountries),
                servers = servers.sortedBy { if (it.pingMillis <= 0L) Long.MAX_VALUE else it.pingMillis },
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
            entries.forEach { entry ->
                val uri = entry.uri?.trim().orEmpty()
                if (uri.isEmpty()) return@forEach
                val profile = try {
                    AngConfigManager.parseConfig(uri, subId, null)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Vpn5gSync: fallback parse failed", e)
                    null
                } ?: return@forEach
                profile.country = entry.country?.trim().orEmpty().ifEmpty { "بین‌الملل" }
                val guid = Utils.getUuid()
                profiles[guid] = profile
                rawConfigs[guid] = ""
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
