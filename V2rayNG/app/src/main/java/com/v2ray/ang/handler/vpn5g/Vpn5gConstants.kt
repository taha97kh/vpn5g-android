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

    /**
     * GitHub source (v1.1.6): bundle for censored networks — REALITY /
     * XTLS-Vision first. Plain text, ~2,800 configs. Verified live
     * 2026-11-23: 2,817 of 3,000 lines parse, 15/15 sampled TCP connects.
     */
    const val GITHUB_SOURCE_3 =
        "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt"

    /**
     * GitHub source (v1.1.6): best-scored bundle — only from sources
     * scoring 85+, every proxy proved with a real TLS tunnel. Plain
     * text, 2,000 configs, generated 2026-11-22. Verified live
     * 2026-11-23: 1,998 of 2,000 lines parse, 15/15 sampled TCP connects.
     */
    const val GITHUB_SOURCE_4 =
        "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt"

    /**
     * GitHub source (v1.1.6): 2,000 configs, refreshed every 30 minutes.
     * Plain text. Verified live 2026-11-23: 1,986 of 2,000 lines parse,
     * 15/15 sampled TCP connects.
     */
    const val GITHUB_SOURCE_5 =
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/config.txt"

    /**
     * GitHub source (v1.1.6): best configs by test results, 200 entries.
     * Base64-wrapped — decoded by [Vpn5gSync.decodeSubscriptionBody].
     * Verified live 2026-11-23: 200 of 200 lines parse after decoding,
     * 15/15 sampled TCP connects.
     */
    const val GITHUB_SOURCE_6 =
        "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/super-sub.txt"

    /** Default config sources, in order. Extra sources may come from app_config.json. */
    val DEFAULT_CONFIG_SOURCES = listOf(
        LIVE_JSON,
        SUB_TXT,
        GITHUB_SOURCE_1,
        GITHUB_SOURCE_2,
        GITHUB_SOURCE_3,
        GITHUB_SOURCE_4,
        GITHUB_SOURCE_5,
        GITHUB_SOURCE_6,
    )

    /**
     * Large GitHub files: only a spread of [GITHUB_SOURCE_PING_CAP]
     * lines is ping-tested per refresh (see Vpn5gSync.refresh).
     * The HF lists are small and always fully ping-tested.
     */
    val CAPPED_SOURCES = setOf(
        GITHUB_SOURCE_1,
        GITHUB_SOURCE_2,
        GITHUB_SOURCE_3,
        GITHUB_SOURCE_4,
        GITHUB_SOURCE_5,
        GITHUB_SOURCE_6,
    )

    /** True when [url] is a large file whose lines are sampled, not fully ping-tested. Pure. */
    fun isCappedSource(url: String): Boolean = url in CAPPED_SOURCES

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

    /**
     * v1.1.7: the auto chain has NO fixed attempt cap any more (the
     * 1.1.5 bound of 8 attempts is removed at Aref's order: "تا زمانی که
     * وصل نشده کانفینگ اتومات تمام نشود" — auto must not end until a
     * tunnel is verified). The chain keeps trying the next pinged free
     * server until one VERIFIES as a real tunnel or the user disconnects.
     * It is bounded sanely, not by a count: one pass over the whole
     * pinged free list, then a list re-refresh and continue; when a
     * refresh yields no untried candidate the chain waits with backoff
     * (see [EMPTY_POOL_BACKOFF_MS]) and refreshes again — never a hard
     * stop while untried pinged servers remain, never a FAILED state
     * while the user has not disconnected. Per-attempt timeouts, the 6h
     * failed-server exclusion and the 30-minute session timer still apply.
     */
    /** Delay between re-refreshes while the pool has no untried candidate. */
    const val EMPTY_POOL_BACKOFF_MS = 60_000L

    /**
     * Real-ping probe (v1.1.7): how many of the fastest TCP-pinged
     * candidates are put through a real through-the-proxy delay test per
     * refresh. Slightly more than [FREE_SERVER_COUNT] so the free tier
     * always has a full 20 real-pinged servers when the pool allows.
     */
    const val REAL_PING_CANDIDATES = 24

    /** Overall budget for one real-ping probe batch (v1.1.7). */
    const val REAL_PING_BUDGET_MS = 45_000L

    /**
     * MMKV SETTING key holding the dedupe keys (scheme://host:port:identity)
     * of servers that passed a real through-the-proxy ping test (v1.1.7).
     * Stored as a comma-separated list. Keyed by server identity, not by
     * GUID, so the evidence survives a refresh (GUIDs are re-issued every
     * refresh while the identity stays the same).
     */
    const val KEY_REAL_PING_KEYS_CSV = "vpn5g_real_ping_keys"

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
