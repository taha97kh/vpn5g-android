package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.MmkvManager

/**
 * «حالت امن اینستاگرام» — Instagram Safe Mode (v1.1.34, Aref's
 * order 2026-11-26).
 *
 * Instagram treats a mid-session exit-country change as impossible
 * travel and answers with checkpoints and temporary locks. With
 * this opt-in mode ON, the tunnel's country is LOCKED to one
 * user-chosen country: the initial connect pick, every failover /
 * auto-switch (UI and daemon) and the standby replacement queues
 * only ever consider servers of that country, so the exit country
 * can never change while the mode is on. When every server of the
 * pinned country is down, the existing never-give-up chains keep
 * retrying INSIDE the country (refresh + backoff) — they never
 * silently jump to another country.
 *
 * Default OFF; with the mode off (or enabled with no country
 * stored yet) every pick behaves exactly as before. The setting
 * persists in MMKV (toggle + ISO country code) so both processes
 * read the same truth. An explicit MANUAL server pick is the
 * user's own choice and is never overridden by the pin — the pin
 * governs automatic picks only, the same boundary manual mode
 * already has.
 *
 * The filter/default decisions are pure and JVM-tested; the
 * load/save functions are the thin MMKV layer, in the same shape
 * as [Vpn5gRouting].
 */
object Vpn5gInstagramSafeMode {

    const val KEY_ENABLED = "vpn5g_ig_safe_enabled"
    const val KEY_COUNTRY = "vpn5g_ig_safe_country"

    /** Normalized ISO code (trimmed, upper-case); "" when blank. Pure. */
    fun normalizeCode(countryCode: String?): String =
        countryCode?.trim()?.uppercase().orEmpty()

    /**
     * The active pin, or null when the mode must not filter: mode
     * off, or no country chosen yet. A null pin means every pick
     * behaves exactly as it did before v1.1.34. Pure.
     */
    fun pinnedCountry(enabled: Boolean, countryCode: String?): String? {
        if (!enabled) return null
        return normalizeCode(countryCode).ifBlank { null }
    }

    /** True when [serverCountryCode] passes the [pinned] filter. Pure. */
    fun matchesPinned(serverCountryCode: String?, pinned: String?): Boolean =
        pinned == null || normalizeCode(serverCountryCode) == pinned

    /** UI servers restricted to the pinned country (all when null). Pure. */
    fun filterUiServers(
        servers: List<Vpn5gServerUi>,
        pinned: String?,
    ): List<Vpn5gServerUi> =
        if (pinned == null) servers
        else servers.filter { matchesPinned(it.countryCode, pinned) }

    /** Daemon candidates restricted to the pinned country. Pure. */
    fun filterDaemonCandidates(
        candidates: List<Vpn5gDaemonPick.Candidate>,
        pinned: String?,
    ): List<Vpn5gDaemonPick.Candidate> =
        if (pinned == null) candidates
        else candidates.filter { matchesPinned(it.countryCode, pinned) }

    /**
     * The country to pin when the user turns the mode on without
     * having chosen one (pure): the CURRENT server's country when
     * connected (the live session's country must not change), else
     * the fastest real-pinged server's country, else the fastest
     * pinged server's, else the first server that carries a code.
     * Null when no server carries a country code yet.
     */
    fun defaultCountryCode(
        servers: List<Vpn5gServerUi>,
        currentGuid: String? = null,
    ): String? {
        val coded = servers.filter { normalizeCode(it.countryCode).isNotBlank() }
        if (coded.isEmpty()) return null
        if (currentGuid != null) {
            coded.firstOrNull { it.guid == currentGuid }
                ?.let { return normalizeCode(it.countryCode) }
        }
        val fastestReal = coded
            .filter { it.hasRealPing && it.pingMillis > 0 }
            .minByOrNull { it.pingMillis }
        if (fastestReal != null) return normalizeCode(fastestReal.countryCode)
        val fastest = coded
            .filter { it.pingMillis > 0 }
            .minByOrNull { it.pingMillis }
        if (fastest != null) return normalizeCode(fastest.countryCode)
        return normalizeCode(coded.first().countryCode)
    }

    // -- MMKV layer ----------------------------------------------------

    fun loadEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(KEY_ENABLED, false)

    fun loadCountryCode(): String =
        normalizeCode(MmkvManager.decodeSettingsString(KEY_COUNTRY))

    /** The pin both processes enforce right now (null = no filter). */
    fun loadPinnedCountry(): String? =
        pinnedCountry(loadEnabled(), loadCountryCode())

    /** Persists toggle + country together. */
    fun save(enabled: Boolean, countryCode: String) {
        MmkvManager.encodeSettings(KEY_ENABLED, enabled)
        MmkvManager.encodeSettings(KEY_COUNTRY, normalizeCode(countryCode))
    }
}
