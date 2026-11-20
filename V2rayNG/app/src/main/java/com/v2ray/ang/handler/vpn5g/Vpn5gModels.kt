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
    val free_countries: List<String>? = null,
    val notice: String? = null,
    val config_sources: List<String>? = null,
    val min_version: String? = null,
    val subscription_status: SubscriptionStatusRemote? = null,
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

/** One live server shown on the home screen. */
data class Vpn5gServerUi(
    val guid: String,
    val name: String,
    val country: String,
    val pingMillis: Long,
    val isSelected: Boolean,
)

/** One country section on the home screen. */
data class Vpn5gCountryUi(
    val label: String,
    val isFree: Boolean,
    val servers: List<Vpn5gServerUi>,
)
