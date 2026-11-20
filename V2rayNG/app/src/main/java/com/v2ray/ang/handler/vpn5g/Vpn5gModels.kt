package com.v2ray.ang.handler.vpn5g

/**
 * VPN 5G remote data models.
 *
 * All fields are nullable: Gson allocates these objects without calling the
 * Kotlin constructor, so a field absent from the JSON stays null instead of
 * taking its Kotlin default. Callers must treat null as "not provided".
 */

/** One entry of live_configs.json. */
data class LiveConfigEntry(
    val protocol: String? = null,
    val country: String? = null,
    val ping: String? = null,
    val raw_ping: Int? = null,
    val uri: String? = null,
    val name: String? = null,
    val host: String? = null,
    val port: Int? = null,
)

/** Remote app configuration (app_config.json). */
data class AppConfigRemote(
    /**
     * IGNORED since v1.1.1: the free tier is the 20 fastest-pinging configs
     * (see Vpn5gConstants.FREE_SERVER_COUNT), not a country list. Kept so
     * older published configs still parse.
     */
    val free_countries: List<String>? = null,
    val notice: String? = null,
    val config_sources: List<String>? = null,
    val min_version: String? = null,
    val subscription_status: SubscriptionStatusRemote? = null,
    /** Admin-managed promo codes (discount / gift). Null when the bot side has none. */
    val codes: List<PromoCodeRemote>? = null,
    /** Admin-managed subscription plans. Null = none published (no plan cards). */
    val plans: List<SubscriptionPlanRemote>? = null,
    /** Direct bank payment gateway settings. Null = not configured. */
    val payment_gateway: PaymentGatewayRemote? = null,
    /** Whether the free trial is offered (bot-managed flag). */
    val trial_enabled: Boolean? = null,
    /** Free-trial length in hours (24 per the product rule). */
    val trial_hours: Int? = null,
)

/**
 * One subscription plan, published by the bot side in app_config.json.
 * Prices are NEVER hardcoded in the app: when the bot publishes no plans,
 * the Premium screen shows no plan cards at all.
 */
data class SubscriptionPlanRemote(
    /** Stable id, e.g. "monthly", "yearly". */
    val id: String? = null,
    /** "monthly" or "yearly" (drives the card layout and badge). */
    val period: String? = null,
    /** Display name in Persian, e.g. "ماهانه". */
    val name_fa: String? = null,
    /** Price in Toman. Null = price not published (card hidden). */
    val price_toman: Long? = null,
)

/**
 * Direct bank payment gateway settings, published by the bot side in
 * app_config.json. The app NEVER hardcodes gateway credentials or test
 * values and never constructs payment requests: when the gateway is
 * configured it opens the published checkout URL in the browser; while
 * it is not configured the option is shown as coming soon and routes the
 * user to the bot to complete the purchase manually.
 */
data class PaymentGatewayRemote(
    /** True when the gateway is configured and usable. */
    val enabled: Boolean? = null,
    /** Gateway checkout URL the app opens in the browser. */
    val url: String? = null,
    /** Merchant id (informational; the app never sends it anywhere). */
    val merchant_id: String? = null,
    /** Callback URL the gateway returns to (informational; bot-owned). */
    val callback_url: String? = null,
    /** Display name, e.g. "درگاه پرداخت مستقیم بانکی". */
    val name_fa: String? = null,
)

/**
 * One admin-managed promo code, published by the bot side in app_config.json.
 * The bot is the authority: it creates the codes and decrements
 * [remaining_uses] when they are redeemed. The app only validates against
 * the fetched list and records its own redemptions locally.
 */
data class PromoCodeRemote(
    /** Code as the user types it (matched case-insensitively, trimmed). */
    val code: String? = null,
    /** "discount" or "gift". */
    val type: String? = null,
    /** Discount percent (0-100) when type is "discount". */
    val discount_percent: Int? = null,
    /** Gift duration in days when type is "gift". */
    val gift_days: Int? = null,
    /** Total allowed redemptions, or null for unlimited. */
    val max_uses: Int? = null,
    /** Redemptions left, as last published by the bot. Null = unknown/unlimited. */
    val remaining_uses: Int? = null,
)

/** Subscription status as published by the bot side in app_config.json. */
data class SubscriptionStatusRemote(
    val remaining_days: Int? = null,
    val remaining_volume_gb: Double? = null,
    val plan_name: String? = null,
)

/** Result of one [Vpn5gSync.refresh] run. */
data class Vpn5gSyncResult(
    val fetched: Int = 0,
    val live: Int = 0,
    val dropped: Int = 0,
    val perCountry: Map<String, Int> = emptyMap(),
    val offline: Boolean = false,
    val notice: String? = null,
)

/** One entry of the bundled fallback snapshot (assets/vpn5g/fallback_servers.json). */
data class FallbackEntry(
    val uri: String? = null,
    val country: String? = null,
)

/** One live server shown on the home screen. */
data class Vpn5gServerUi(
    val guid: String,
    val name: String,
    val country: String,
    val pingMillis: Long,
    val isSelected: Boolean,
    /** True when this server is in the free tier (top-20 fastest ping, v1.1.2). */
    val isFree: Boolean = false,
    /** Flag emoji derived from the remark or the server IP ("🌐" when unknown). */
    val flag: String = "🌐",
    /** Persian country name derived from the remark or the server IP. */
    val countryNameFa: String = "",
    /** ISO country code, or "" when unknown. */
    val countryCode: String = "",
    /** Server host (for GeoIP lookup only; not displayed). */
    val host: String = "",
    /** Config protocol scheme ("vless", "vmess", "trojan", "ss"), or "" when unknown. */
    val protocol: String = "",
)

/** One country section on the home screen. */
data class Vpn5gCountryUi(
    val label: String,
    val isFree: Boolean,
    val servers: List<Vpn5gServerUi>,
)
