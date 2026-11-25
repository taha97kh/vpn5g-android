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
 * addAllowedApplication. The selection itself ("برنامه‌های بدون فیلتر")
 * still lives in PerAppProxyActivity and is untouched here.
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
