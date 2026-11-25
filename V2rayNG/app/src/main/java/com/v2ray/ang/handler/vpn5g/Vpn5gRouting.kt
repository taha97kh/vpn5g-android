package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/**
 * Routing mode (v1.1.21, Lantern-style routing screen).
 *
 * The engine itself is fully automatic — there is no per-rule routing
 * table anywhere in the app. The ONE real routing lever is the per-app
 * proxy that already exists upstream: [CoreVpnService.configurePerAppProxy]
 * reads [AppConfig.PREF_PER_APP_PROXY] (whether the app set applies)
 * plus [AppConfig.PREF_BYPASS_APPS] (true = the selected apps bypass the
 * tunnel and go direct, false = only the selected apps use the tunnel),
 * applied at tunnel start via addDisallowedApplication /
 * addAllowedApplication. The selection itself still lives in
 * PerAppProxyActivity; the one-time v1.1.29 default seed (Instagram +
 * Telegram use the VPN, allowlist) is [seedPerAppDefaultsIfNeeded].
 *
 * This object only owns the mode -> upstream-prefs mapping, so the
 * routing screen can be honest about what each choice really does:
 *
 * - SMART (مسیریابی هوشمند): the bypass list is honoured — the listed
 *   apps go direct. "ابتدا اتصال مستقیم" is the master switch for that
 *   list inside smart mode; with it OFF the list is ignored (the
 *   per-app proxy is disabled, so every app goes through the tunnel
 *   until the user turns the switch back on).
 * - FULL (تونل کامل): the per-app proxy is OFF — all traffic goes
 *   through the VPN and the bypass list is ignored.
 *
 * A change applies at the NEXT connect: the tunnel reads the prefs at
 * start, never mid-session. The mapping functions are pure and
 * JVM-tested; [loadMode]/[loadDirectFirst]/[apply] are the thin MMKV
 * layer, in the same shape as the other persisted settings.
 */
enum class Vpn5gRoutingMode { SMART, FULL }

object Vpn5gRouting {

    const val KEY_ROUTING_MODE = "vpn5g_routing_mode"
    const val KEY_DIRECT_FIRST = "vpn5g_direct_first"

    /**
     * v1.1.29 (Aref's order): the default per-app selection — the apps
     * that use the VPN by default — is Instagram + Telegram. Seeded
     * ONCE (this flag), only for packages actually installed, and
     * never over a user who already customized anything (non-empty
     * selection, per-app proxy already enabled, or a routing mode
     * already chosen). The seeded state is allowlist-style:
     * PREF_PER_APP_PROXY=true + PREF_BYPASS_APPS=false, i.e. only the
     * selected apps go through the tunnel (see
     * CoreVpnService.configurePerAppProxy). The user can change the
     * selection afterwards in the per-app screen; picking a routing
     * mode also re-maps the prefs as before.
     */
    const val KEY_PER_APP_SEEDED = "vpn5g_per_app_seeded"

    val DEFAULT_PER_APP_PACKAGES: List<String> = listOf(
        "com.instagram.android",
        "org.telegram.messenger",
    )

    /** Default selection = the default packages that are installed. */
    fun defaultPerAppSelection(installedPackages: Set<String>): Set<String> =
        DEFAULT_PER_APP_PACKAGES.filter { it in installedPackages }.toSet()

    /**
     * Whether the one-time default seed may run: never seeded before,
     * no existing selection, per-app proxy not already enabled, and
     * no routing mode chosen yet — any of those means the user (or an
     * earlier version) already set things up and must not be
     * overridden.
     */
    fun shouldSeedPerAppDefaults(
        seeded: Boolean,
        existingSelection: Set<String>,
        perAppEnabled: Boolean,
        routingChosen: Boolean,
    ): Boolean =
        !seeded && existingSelection.isEmpty() && !perAppEnabled && !routingChosen

    /**
     * Runs the one-time seed. [isInstalled] answers package presence
     * (PackageManager in the app, a plain set in tests). When the
     * user already customized, only the seeded flag is written so
     * the check never runs again. When neither default app is
     * installed the flag is left unset, so a later install can still
     * be seeded on a future launch.
     */
    fun seedPerAppDefaultsIfNeeded(isInstalled: (String) -> Boolean) {
        val seeded = MmkvManager.decodeSettingsBool(KEY_PER_APP_SEEDED, false)
        if (seeded) return
        val existing = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet() ?: emptySet()
        val perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        val routingChosen =
            MmkvManager.decodeSettingsString(KEY_ROUTING_MODE) != null
        if (!shouldSeedPerAppDefaults(seeded, existing, perAppEnabled, routingChosen)) {
            MmkvManager.encodeSettings(KEY_PER_APP_SEEDED, true)
            return
        }
        val installed = DEFAULT_PER_APP_PACKAGES.filter { isInstalled(it) }.toSet()
        val selection = defaultPerAppSelection(installed)
        if (selection.isEmpty()) return
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, selection.toMutableSet())
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, false)
        MmkvManager.encodeSettings(KEY_PER_APP_SEEDED, true)
    }

    const val MODE_SMART = "smart"
    const val MODE_FULL = "full"

    /** Stored value -> mode; anything unknown (incl. null) is SMART. */
    fun modeFromStored(value: String?): Vpn5gRoutingMode =
        if (value == MODE_FULL) Vpn5gRoutingMode.FULL else Vpn5gRoutingMode.SMART

    fun modeToStored(mode: Vpn5gRoutingMode): String =
        if (mode == Vpn5gRoutingMode.FULL) MODE_FULL else MODE_SMART

    /**
     * (mode, direct-first) -> (perAppProxyEnabled, bypassApps).
     * FULL never enables the per-app proxy, so the bypass list can
     * never leak traffic outside the tunnel in full mode.
     */
    fun resolvePerAppProxy(
        mode: Vpn5gRoutingMode,
        directFirst: Boolean,
    ): Pair<Boolean, Boolean> = when (mode) {
        Vpn5gRoutingMode.SMART -> directFirst to true
        Vpn5gRoutingMode.FULL -> false to true
    }

    /** Whether the bypass list is honoured right now. */
    fun isSplitTunnelActive(mode: Vpn5gRoutingMode, directFirst: Boolean): Boolean =
        mode == Vpn5gRoutingMode.SMART && directFirst

    fun loadMode(): Vpn5gRoutingMode =
        modeFromStored(MmkvManager.decodeSettingsString(KEY_ROUTING_MODE))

    fun loadDirectFirst(): Boolean =
        MmkvManager.decodeSettingsBool(KEY_DIRECT_FIRST, true)

    /** Persists the mode and applies it to the upstream prefs. */
    fun apply(mode: Vpn5gRoutingMode, directFirst: Boolean) {
        MmkvManager.encodeSettings(KEY_ROUTING_MODE, modeToStored(mode))
        MmkvManager.encodeSettings(KEY_DIRECT_FIRST, directFirst)
        val (perAppProxy, bypassApps) = resolvePerAppProxy(mode, directFirst)
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, perAppProxy)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, bypassApps)
    }
}
