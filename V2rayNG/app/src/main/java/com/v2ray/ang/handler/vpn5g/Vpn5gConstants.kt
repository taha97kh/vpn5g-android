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
    /**
     * Online-support deep link (v1.1.14): opens the bot with a support
     * start-param so the user lands on support directly. The bot must
     * handle `start=support` (otherwise it lands on the main menu,
     * where پشتیبانی is one tap away). Bot membership cannot be
     * verified app-side — the app has no Telegram identity — so the
     * button opens the bot and the bot handles membership.
     */
    const val SUPPORT_BOT_URL = "https://t.me/VPN5G_IRBot?start=support"

    /**
     * Buy deep link for a plan (v1.1.15, unique-amount contract): the
     * app passes the selected plan id to the bot
     * (`start=buy_sub_1` … `start=buy_sub_12`); the bot generates the
     * UNIQUE payable amount for that order (non-round: base price +
     * unique suffix) and tells the user — the app never shows a round
     * plan price as the amount to pay in a payment context, and never
     * generates a unique amount itself (uniqueness must be
     * coordinated by the bot to avoid collisions).
     */
    fun buyUrl(planId: String): String = "$BOT_URL?start=buy_${planId.trim()}"

    const val SUBSCRIPTION_REMARKS = "VPN5G IRBOT"

    /** Free tier: this many fastest-pinging configs are free (v1.1.2). */
    const val FREE_SERVER_COUNT = 20

    /**
     * Standby pool (v1.1.17, raised v1.1.18): servers waiting to
     * switch must ALWAYS be at least 20 — Aref's 2026-11-25 order
     * §1 (≥20 ready free servers; the old ">10" requirement is
     * superseded). The free tier's real-verified pool is topped up
     * (extra probe batches) until at least this many servers are
     * verified and ready, whenever the sources can supply them.
     */
    const val STANDBY_POOL_MIN = 20

    /** Extra probe batches the standby top-up runs at most (v1.1.17). */
    const val MAX_STANDBY_TOPUP_BATCHES = 2

    /**
     * GIFT tier (v1.1.14): a redeemed gift code grants this many
     * fastest-pinging configs — more than the 20 free ones, not the
     * full VIP pool — plus the countries those servers belong to, and
     * no 30-minute cutoff while the gift lasts. The gift expires when
     * its duration ends (stored at redeem time).
     */
    const val GIFT_SERVER_COUNT = 100

    /** Plan id the app stores for a gift-tier redemption (v1.1.14). */
    const val GIFT_TIER_PLAN_ID = "gift"

    /** Auto-disconnect after this long. */
    const val SESSION_LIMIT_MINUTES = 30L

    /** MMKV SETTING keys owned by VPN 5G. */
    const val KEY_APP_CONFIG_JSON = "vpn5g_app_config_json"
    const val KEY_CONNECTED_AT = "vpn5g_connected_at"
    /** JSON of the signed-in account session (email + tokens), see Vpn5gAuthService. */
    const val KEY_AUTH_SESSION_JSON = "vpn5g_auth_session_json"
    /** Redeemed promo codes, stored as a JSON array of code strings. */
    const val KEY_REDEEMED_CODES_JSON = "vpn5g_redeemed_codes_json"
    /**
     * The signed-in user's own subscription row, cached as JSON (v1.1.11):
     * status/expires_at/plan from vpn5g_registrations. A failed fetch never
     * clears it; sign-out does.
     */
    const val KEY_USER_SUBSCRIPTION_JSON = "vpn5g_user_subscription_json"
    /**
     * A subscription granted by a redeemed gift code (v1.1.12), cached as
     * JSON in the same shape: status "active", the plan id, and the expiry
     * computed from the code's gift_days at redemption time. It is the
     * LOCAL truth for a code activation; the row cache above is the
     * SERVER truth (bot-activated plans). The effective subscription is
     * the later-expiring of the two — see
     * [Vpn5gSubscriptionStatus.effectiveSubscription]. Sign-out clears
     * both; a failed row fetch never clears either.
     */
    const val KEY_REDEEMED_SUBSCRIPTION_JSON = "vpn5g_redeemed_subscription_json"
    /**
     * True while a VPN session is live (v1.1.12, "اتصال پایدار"): set when
     * the service starts, cleared only by a manual disconnect or by the
     * 30-minute timer ending a free session. BootReceiver restarts the
     * service after boot / an in-app update while this is set, so only
     * those two events — never another app — can end the connection.
     */
    const val KEY_PERSISTENT_CONNECTION = "vpn5g_persistent_connection"
    /** True once the battery-optimization exemption was asked for (v1.1.12). */
    const val KEY_BATTERY_OPT_PROMPTED = "vpn5g_battery_opt_prompted"
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
    /** Per-operator verification history, JSON object operatorKey -> CSV of server dedupe keys (v1.1.14). */
    const val KEY_OPERATOR_HISTORY_JSON = "vpn5g_operator_history_json"
    /** Last detected operator key (v1.1.14): "wifi", "cell:irancell", "cell:mci", "cell:rightel", "cell:unknown", "other". */
    const val KEY_OPERATOR_LAST = "vpn5g_operator_last"

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
     * MMKV SETTING key holding the dedupe keys (scheme://host:port:identity)
     * of the bundled seed snapshot (v1.1.20): servers from
     * assets/vpn5g/fallback_servers.json that were TCP-verified at build
     * time. A seed server is free-eligible with an unknown ping until the
     * real probe measures it (see Vpn5gSync) — the seed is a starting
     * point, never proof: a seed server the real probe fails loses its
     * seed trust at once, and the first live publish clears the set.
     * Stored as a comma-separated list, keyed by server identity like
     * the real-ping keys.
     */
    const val KEY_SEED_SERVER_KEYS_CSV = "vpn5g_seed_server_keys"

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

    /**
     * In-session tunnel watchdog (v1.1.11): while CONNECTED, a light
     * single-fetch exit-IP probe runs every [WATCHDOG_PROBE_INTERVAL_TICKS]
     * seconds (the 1-second ticker counts them). One failed probe is a
     * blip, not a drop; [WATCHDOG_MAX_CONSECUTIVE_FAILURES] failed
     * probes in a row drop the tunnel so the drop detector can fail over
     * to another server. The full 60-second re-verification still runs
     * alongside this as the deeper check.
     */
    const val WATCHDOG_PROBE_INTERVAL_TICKS = 20
    const val WATCHDOG_MAX_CONSECUTIVE_FAILURES = 3

    /**
     * Drop failover bound (v1.1.11): how many no-candidate refresh
     * rounds (empty pool, 60 s backoff each) the drop failover runs
     * before giving up with a clear error instead of looping forever
     * on a dead network. This bounds only the "no server at all"
     * case; when candidates exist the chain keeps trying until a
     * tunnel verifies (v1.1.7).
     */
    const val MAX_DROP_FAILOVER_ROUNDS = 3

    /**
     * Persistent-connection reconnect (v1.1.12): when the service stops
     * unexpectedly — not a manual disconnect, not the 30-minute timer,
     * not a tunnel-check stop — the app re-runs the connect chain with
     * exponential backoff, at most [MAX_PERSISTENT_RECONNECT_ATTEMPTS]
     * times, then gives up with a clear error instead of looping
     * forever on a dead network. A manual disconnect cancels the
     * pending reconnect immediately.
     */
    const val MAX_PERSISTENT_RECONNECT_ATTEMPTS = 5
    const val PERSISTENT_RECONNECT_BASE_BACKOFF_MS = 2_000L
    const val PERSISTENT_RECONNECT_MAX_BACKOFF_MS = 30_000L

    /** Dismiss key of the app announcement the user closed (\"\" = none). */
    const val KEY_ANNOUNCEMENT_DISMISSED = "vpn5g_announcement_dismissed"

    /**
     * Terms & Conditions acceptance (v1.1.21): true once the user
     * ticked the checkbox on the startup notice's last page. Persisted
     * in MMKV — the user is never asked to re-tick on later launches;
     * the notice itself still shows on every entry.
     */
    const val KEY_TERMS_ACCEPTED = "vpn5g_terms_accepted"

    /**
     * Live tunnel speeds, published once a second by the :daemon
     * notification job (v1.1.15, speeds root-cause fix): the UI process
     * cannot query the core itself (different process), so the daemon
     * — the single querier — publishes the rates here and the home
     * screen reads them. [KEY_SPEED_UPDATED_AT_MS] is the publish time
     * (epoch millis); a value older than 3 s means the tunnel is gone.
     */
    const val KEY_SPEED_DOWN_BPS = "vpn5g_speed_down_bps"
    const val KEY_SPEED_UP_BPS = "vpn5g_speed_up_bps"
    const val KEY_SPEED_UPDATED_AT_MS = "vpn5g_speed_updated_at_ms"

    /** Expanded locked-country groups in Premium, persisted (v1.1.15). */
    const val KEY_EXPANDED_COUNTRY_KEYS = "vpn5g_expanded_country_keys"

    /** Per-ad impression counters, JSON {adId: {total, day, today}} (v1.1.15). */
    const val KEY_AD_IMPRESSIONS_JSON = "vpn5g_ad_impressions_json"

    /**
     * On-device volume metering (v1.1.18, Model B): the grant key
     * (plan + expiry, see [Vpn5gVolume.grantKeyFor]) the consumption
     * counter belongs to, and the cumulative real tunnel bytes for
     * that grant. The :daemon speed job writes the counter; the UI
     * process reads it and resets it (via [Vpn5gVolumeStore]) only
     * when a new grant starts — never on app restart.
     */
    const val KEY_VOLUME_GRANT_KEY = "vpn5g_volume_grant_key"
    const val KEY_VOLUME_CONSUMED_BYTES = "vpn5g_volume_consumed_bytes"

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
