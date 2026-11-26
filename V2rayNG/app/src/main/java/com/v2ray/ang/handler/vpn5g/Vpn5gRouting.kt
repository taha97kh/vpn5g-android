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

    /**
     * v1.1.34: the v1.1.29 default (Instagram + Telegram) is kept
     * verbatim so users still on that untouched default can be
     * migrated to the expanded default below. Migration runs once
     * (this flag) and only when the stored selection still equals
     * the old default intersected with installed packages and the
     * prefs are still the seeded allowlist (per-app on, bypass
     * off) — any customization at all leaves the user untouched.
     */
    const val KEY_PER_APP_EXPANDED_SEEDED = "vpn5g_per_app_expanded_seeded"

    val OLD_DEFAULT_PER_APP_PACKAGES: List<String> = listOf(
        "com.instagram.android",
        "org.telegram.messenger",
    )

    /**
     * v1.1.34 (Aref's order): «تلگرام، اینستا، جمینای، چت‌جی‌پی‌تی،
     * گوگل فلو، کروم و ... همیشه پیش‌فعال باشند» — the default
     * per-app selection is expanded to the apps that need the
     * tunnel by default. Package names verified 2026-11-26:
     * Gemini = com.google.android.apps.bard (Google Play listing,
     * legacy Bard package), ChatGPT = com.openai.chatgpt
     * (APKMirror/OpenAI), Google Flow = com.google.android.apps.
     * labs.whisk (Google Labs Play listing — Flow DOES have a
     * dedicated Android app, so it is listed directly; Chrome
     * covers the web version too). Chrome/WhatsApp/YouTube/X/
     * Facebook are the canonical Play package names.
     */
    val DEFAULT_PER_APP_PACKAGES: List<String> = listOf(
        "com.instagram.android",
        "org.telegram.messenger",
        "com.google.android.apps.bard",
        "com.openai.chatgpt",
        "com.android.chrome",
        "com.google.android.apps.labs.whisk",
        "com.whatsapp",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.facebook.katana",
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
     * Whether a user seeded with the OLD default (v1.1.29) may be
     * migrated to the expanded default (v1.1.34): the stored
     * selection must still be exactly the old default intersected
     * with installed packages and the prefs must still be the
     * seeded allowlist. Any customization — a different set, or
     * bypass mode — means untouched.
     */
    fun shouldMigrateToExpandedDefault(
        expandedSeeded: Boolean,
        existingSelection: Set<String>,
        installedOldDefault: Set<String>,
        perAppEnabled: Boolean,
        bypassApps: Boolean,
    ): Boolean =
        !expandedSeeded && perAppEnabled && !bypassApps &&
            existingSelection == installedOldDefault

    /**
     * Runs the one-time seed. [isInstalled] answers package presence
     * (PackageManager in the app, a plain set in tests). When the
     * user already customized, only the seeded flag is written so
     * the check never runs again. When neither default app is
     * installed the flag is left unset, so a later install can still
     * be seeded on a future launch.
     *
     * v1.1.34: an already-seeded user still on the untouched old
     * default is migrated to the expanded default once (see
     * [shouldMigrateToExpandedDefault]); the expanded flag is set
     * either way so the check never runs twice.
     */
    fun seedPerAppDefaultsIfNeeded(isInstalled: (String) -> Boolean) {
        val seeded = MmkvManager.decodeSettingsBool(KEY_PER_APP_SEEDED, false)
        if (seeded) {
            migrateToExpandedDefaultIfNeeded(isInstalled)
            return
        }
        val existing = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet() ?: emptySet()
        val perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        val routingChosen =
            MmkvManager.decodeSettingsString(KEY_ROUTING_MODE) != null
        if (!shouldSeedPerAppDefaults(seeded, existing, perAppEnabled, routingChosen)) {
            MmkvManager.encodeSettings(KEY_PER_APP_SEEDED, true)
            MmkvManager.encodeSettings(KEY_PER_APP_EXPANDED_SEEDED, true)
            return
        }
        val installed = DEFAULT_PER_APP_PACKAGES.filter { isInstalled(it) }.toSet()
        val selection = defaultPerAppSelection(installed)
        if (selection.isEmpty()) return
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, selection.toMutableSet())
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, false)
        MmkvManager.encodeSettings(KEY_PER_APP_SEEDED, true)
        MmkvManager.encodeSettings(KEY_PER_APP_EXPANDED_SEEDED, true)
    }

    private fun migrateToExpandedDefaultIfNeeded(isInstalled: (String) -> Boolean) {
        val expandedSeeded =
            MmkvManager.decodeSettingsBool(KEY_PER_APP_EXPANDED_SEEDED, false)
        if (expandedSeeded) return
        val existing = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet() ?: emptySet()
        val perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
        val installedOldDefault =
            OLD_DEFAULT_PER_APP_PACKAGES.filter { isInstalled(it) }.toSet()
        if (shouldMigrateToExpandedDefault(
                expandedSeeded, existing, installedOldDefault, perAppEnabled, bypassApps,
            )
        ) {
            val installed = DEFAULT_PER_APP_PACKAGES.filter { isInstalled(it) }.toSet()
            val selection = defaultPerAppSelection(installed)
            if (selection.isNotEmpty()) {
                MmkvManager.encodeSettings(
                    AppConfig.PREF_PER_APP_PROXY_SET, selection.toMutableSet(),
                )
            }
        }
        MmkvManager.encodeSettings(KEY_PER_APP_EXPANDED_SEEDED, true)
    }

    /**
     * v1.1.34 (Aref's order): «پیش‌فرض اپ‌های ایران رو خاموش کن تا
     * از تونل استفاده نکنن» — Iranian apps must NOT use the tunnel
     * by default. This separate set is seeded ONCE (this flag)
     * with every well-known Iranian package below, installed or
     * not (apply-time NameNotFoundException is caught in
     * CoreVpnService.configurePerAppProxy), so apps installed
     * later are covered too. It is never re-seeded over: once the
     * flag is set the stored set stands as the user left it.
     *
     * How it applies (see [perAppDecision]): in allowlist mode
     * Iranian apps bypass by simply not being ticked — ticking one
     * is the user's explicit choice to tunnel it. In bypass mode
     * and in all-apps mode (per-app off) the set is disallowed at
     * tunnel start, so Iranian apps always go direct. Android's
     * VpnService.Builder cannot mix addAllowedApplication with
     * addDisallowedApplication, which is why the set is a union in
     * the disallow branches and a no-op in the allow branch.
     *
     * Package names verified 2026-11-26 (Cafe Bazaar app pages,
     * Uptodown, AppBrain, Google Play listings): Eitaa, Rubika,
     * Soroush Plus, Snapp, Tapsi, Digikala, Divar, Aparat,
     * Namava, MyIrancell, MyMCI, Bam, Iva, Mellat, Saderat,
     * Tejarat, Pasargad, Wepod, BluBank, Mobillet (Saman),
     * Parsian, Refah, Sepah, Omid Bank, Ap (Asan Pardakht), Top,
     * Paypod. Bale (ir.nasim.ha), Filimo (com.filimo) and Rightel
     * (ir.rightel) are the best-known candidates from APK
     * listings; a wrong name is harmless (it never matches an
     * installed app).
     */
    const val KEY_IRANIAN_BYPASS_SET = "vpn5g_iranian_bypass_set"
    const val KEY_IRANIAN_BYPASS_SEEDED = "vpn5g_iranian_bypass_seeded"

    val IRANIAN_BYPASS_PACKAGES: List<String> = listOf(
        // Messengers
        "ir.eitaa.messenger",
        "ir.resaneh1.iptv",
        "mobi.mmdt.ottplus",
        "mobi.mmdt.ott",
        "ir.nasim.ha",
        // Ride-hailing, commerce, classifieds
        "cab.snapp.passenger",
        "taxi.tapsi.passenger",
        "com.digikala",
        "ir.divar",
        // Video / streaming
        "com.aparat",
        "com.shatelland.namava.mobile",
        "com.filimo",
        // Telecom
        "com.myirancell",
        "ir.mci.ecareapp",
        "ir.rightel",
        // Banks and payment apps
        "ir.bmi.bam.nativeweb",
        "com.sadadpsp.eva",
        "com.pmb.mobile",
        "com.isc.bsinew",
        "ir.tejaratbank.tata.mobile.android.tejarat",
        "mob.banking.android.pasargad",
        "com.dotin.wepod",
        "com.samanpr.blu",
        "ir.mobillet.app",
        "com.parsmobapp",
        "com.refahbank.dpi.android",
        "com.refah.superapp",
        "com.tosan.dara.sepah",
        "ir.omidbank",
        "com.sibche.aspardproject.app",
        "ir.tgbs.peccharge",
        "com.etick.mobilemancard",
    )

    /** Whether the one-time Iranian bypass seed may run. */
    fun shouldSeedIranianBypass(seeded: Boolean): Boolean = !seeded

    /** Runs the one-time Iranian bypass seed (see above). */
    fun seedIranianBypassIfNeeded() {
        val seeded = MmkvManager.decodeSettingsBool(KEY_IRANIAN_BYPASS_SEEDED, false)
        if (!shouldSeedIranianBypass(seeded)) return
        MmkvManager.encodeSettings(
            KEY_IRANIAN_BYPASS_SET, IRANIAN_BYPASS_PACKAGES.toMutableSet(),
        )
        MmkvManager.encodeSettings(KEY_IRANIAN_BYPASS_SEEDED, true)
    }

    /** The stored Iranian bypass set (empty until seeded). */
    fun loadIranianBypassSet(): Set<String> =
        MmkvManager.decodeSettingsStringSet(KEY_IRANIAN_BYPASS_SET)?.toSet()
            ?: emptySet()

    /** How the per-app selection is applied to the VPN builder. */
    enum class PerAppApply { ALLOW_ONLY, DISALLOW }

    data class PerAppDecision(val apply: PerAppApply, val packages: Set<String>)

    /**
     * The one deterministic per-app decision (pure, JVM-tested),
     * applied by CoreVpnService.configurePerAppProxy at tunnel
     * start:
     *
     * - per-app off (all-apps / FULL): disallow self + Iranian
     *   set — everything else tunnels.
     * - bypass mode: disallow user set + Iranian set + self —
     *   everything else tunnels.
     * - allowlist with an empty user set: disallow self + Iranian
     *   set (the upstream fallback: everything tunnels except
     *   self; the Iranian set keeps Aref's rule in this state too).
     * - allowlist with a selection: allow ONLY the user set minus
     *   self; Iranian apps go direct unless the user ticked one.
     */
    fun perAppDecision(
        perAppEnabled: Boolean,
        bypassApps: Boolean,
        userSet: Set<String>,
        iranianSet: Set<String>,
        selfPackage: String,
    ): PerAppDecision = when {
        !perAppEnabled ->
            PerAppDecision(PerAppApply.DISALLOW, iranianSet + selfPackage)
        bypassApps ->
            PerAppDecision(PerAppApply.DISALLOW, userSet + iranianSet + selfPackage)
        userSet.isEmpty() ->
            PerAppDecision(PerAppApply.DISALLOW, iranianSet + selfPackage)
        else ->
            PerAppDecision(PerAppApply.ALLOW_ONLY, userSet - selfPackage)
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
