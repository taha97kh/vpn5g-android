package com.v2ray.ang.handler.vpn5g

/**
 * VPN 5G constants: remote endpoints, links, and the default free-country list.
 *
 * Free countries are the top-3 countries by live server count in live_configs.json,
 * with the tie at 1 server broken toward آلمان (live list: بین‌الملل 36، فرانسه 3،
 * آمریکا 1، آلمان 1). The list is overridable by app_config.json (see
 * Vpn5gRemoteConfig); the values here are only the fallback.
 */
object Vpn5gConstants {
    const val LIVE_JSON = "https://taha97kh-vpn5g-app.static.hf.space/live_configs.json"
    const val SUB_TXT = "https://taha97kh-vpn5g-app.static.hf.space/sub.txt"
    const val APP_CONFIG = "https://taha97kh-vpn5g-app.static.hf.space/app_config.json"

    /** Primary GitHub source: verified working nodes, auto-updates every 15 min. */
    const val GITHUB_SOURCE_1 =
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt"

    /** Fallback GitHub source: large unverified pool. */
    const val GITHUB_SOURCE_2 =
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt"

    /** Default config sources, in order. Extra sources may come from app_config.json. */
    val DEFAULT_CONFIG_SOURCES = listOf(
        LIVE_JSON,
        SUB_TXT,
        GITHUB_SOURCE_1,
        GITHUB_SOURCE_2,
    )

    /**
     * Ping-test cap per GitHub source per refresh. The GitHub files are large
     * (344 KB / 2.1 MB, thousands of lines); only a spread of at most this many
     * lines is ping-tested so a refresh stays fast. live_configs.json (41
     * entries) is always fully ping-tested.
     */
    const val GITHUB_SOURCE_PING_CAP = 300

    const val BOT_URL = "https://t.me/VPN5G_IRBot"
    const val CHANNEL_URL = "https://t.me/VPNCRACKNET"
    const val SUPPORT_URL = "https://t.me/Aref_5G"

    const val SUBSCRIPTION_REMARKS = "VPN 5G"

    /** Default free countries (normalized names, no emoji prefix). */
    val DEFAULT_FREE_COUNTRIES = listOf("بین‌الملل", "فرانسه", "آلمان")

    /** Auto-disconnect after this long. */
    const val SESSION_LIMIT_MINUTES = 30L

    /** MMKV SETTING keys owned by VPN 5G. */
    const val KEY_APP_CONFIG_JSON = "vpn5g_app_config_json"
    const val KEY_CONNECTED_AT = "vpn5g_connected_at"

    /** Unique WorkManager name for the 30-minute disconnect work. */
    const val DISCONNECT_WORK_NAME = "vpn5g_30min_disconnect"

    /** Schemes accepted as single-config deep links. */
    val CONFIG_SCHEMES = setOf("vmess", "vless", "trojan", "ss")

    /**
     * Normalizes a country label for comparison: the live list prefixes labels
     * with a flag emoji ("🌐 بین‌الملل", "🇫🇷 فرانسه"), which is stripped here.
     * The display label is kept as-is; only matching uses the normalized form.
     */
    fun normalizeCountry(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().replace(Regex("^[^\\p{L}]+"), "").trim()
    }

    fun isFreeCountry(country: String?, freeCountries: List<String>): Boolean {
        val normalized = normalizeCountry(country)
        if (normalized.isEmpty()) return false
        return freeCountries.any { normalizeCountry(it) == normalized }
    }
}
