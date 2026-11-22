package com.v2ray.ang.handler.vpn5g

/**
 * VPN 5G constants: remote endpoints, links, and the free tier.
 *
 * Free tier (v1.1.2): after each sync/ping, the [FREE_SERVER_COUNT]
 * fastest-pinging configs are free; every other config is premium-locked.
 * The free set is computed from measured pings in
 * [Vpn5gSync.loadGroupedServers] — never by country, and never invented.
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

    const val SUBSCRIPTION_REMARKS = "VPN5G IRBOT"

    /** Free tier: this many fastest-pinging configs are free (v1.1.2). */
    const val FREE_SERVER_COUNT = 20

    /** Auto-disconnect after this long. */
    const val SESSION_LIMIT_MINUTES = 30L

    /** MMKV SETTING keys owned by VPN 5G. */
    const val KEY_APP_CONFIG_JSON = "vpn5g_app_config_json"
    const val KEY_CONNECTED_AT = "vpn5g_connected_at"
    /** JSON of the signed-in account session (email + tokens), see Vpn5gAuthService. */
    const val KEY_AUTH_SESSION_JSON = "vpn5g_auth_session_json"
    /** Redeemed promo codes, stored as a JSON array of code strings. */
    const val KEY_REDEEMED_CODES_JSON = "vpn5g_redeemed_codes_json"
    /** GUID of the manually picked free server ("" = auto mode, v1.1.5 §3). */
    const val KEY_MANUAL_SERVER_GUID = "vpn5g_manual_server_guid"
    /** GUIDs of servers whose tunnel check failed in this process run. */
    const val KEY_TUNNEL_FAILED_GUIDS = "vpn5g_tunnel_failed_guids"
    /** GUIDs of servers whose tunnel check failed, kept 6h (v1.1.4 §5). */
    const val KEY_FAILED_GUIDS_JSON = "vpn5g_failed_guids_json"
    /** How long a failed server stays out of the auto picker (v1.1.4 §5). */
    const val FAILED_GUID_TTL_MS = 6L * 60L * 60L * 1000L
    /** Selected protocol filter id ("auto", "vless", "vmess", "trojan", "ss"). */
    const val KEY_PROTOCOL_FILTER = "vpn5g_protocol_filter"
    /** Last successful app-update check, epoch millis (v1.1.3, at most daily). */
    const val KEY_UPDATE_LAST_CHECK_MS = "vpn5g_update_last_check_ms"

    /** Bundled fallback assets (assets/vpn5g/). */
    const val ASSET_FALLBACK_SERVERS = "vpn5g/fallback_servers.json"

    /** Max servers tried in one auto-failover chain after a tunnel check fails.
     * v1.1.5 refinement: a connect attempt must never give up after one
     * failure — it keeps switching to the next best server until a real
     * tunnel is verified, or the candidates run out. The bound is 8
     * servers per chain (each attempt has its own timeouts and the 6h
     * failed store keeps dead servers out), so the chain always ends:
     * no infinite loop, worst case ~8 attempts before FAILED. */
    const val MAX_TUNNEL_ATTEMPTS = 8

    /** Max reconnect attempts after an unexpected drop, per session. */
    const val MAX_AUTO_FAILOVER_ATTEMPTS = 2

    /**
     * Debounce after an unexpected drop before the drop detector fails
     * over to another server (v1.1.5 refinement: was a fixed 3,000 ms).
     * Intentional restarts are already excluded by suppressAutoFailover,
     * so this wait only covers a transient daemon hiccup that recovers
     * on its own; 800 ms switches servers fast while still letting a
     * self-recovering service cancel the failover. While waiting, the
     * UI stays in RETRYING (connecting family) — never Disconnected.
     */
    const val DROP_FAILOVER_DEBOUNCE_MS = 800L

    /** Dismiss key of the app announcement the user closed (\"\" = none). */
    const val KEY_ANNOUNCEMENT_DISMISSED = "vpn5g_announcement_dismissed"

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
}
