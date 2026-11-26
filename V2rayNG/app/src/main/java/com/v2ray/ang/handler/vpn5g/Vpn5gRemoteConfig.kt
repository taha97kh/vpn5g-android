package com.v2ray.ang.handler.vpn5g

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.APP_CONFIG
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_APP_CONFIG_JSON
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import java.lang.reflect.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches and caches the remote app configuration (app_config.json).
 *
 * The file does not exist on the server yet: a 404 or any fetch failure is
 * normal in v1 and falls back to safe defaults (cached JSON when available,
 * otherwise no notice and no remote values). Never throws.
 */
object Vpn5gRemoteConfig {

    fun defaults(): AppConfigRemote = AppConfigRemote(
        free_countries = null,
        notice = null,
        config_sources = emptyList(),
        min_version = null,
        subscription_status = null,
        announcement = null,
    )

    fun cached(): AppConfigRemote {
        val json = MmkvManager.decodeSettingsString(KEY_APP_CONFIG_JSON)
        if (json.isNullOrBlank()) return defaults()
        return parse(json) ?: defaults()
    }

    suspend fun fetch(timeoutMs: Int = 10_000): AppConfigRemote =
        fetchWithStatus(timeoutMs).first

    /**
     * Like [fetch] but also reports whether the network fetch
     * actually succeeded (v1.1.26, contract v1 rule: on fetch
     * failure the app continues with the last cached config and
     * executes NO new remote commands). The boolean is true only
     * when a fresh, parsable body was downloaded and cached.
     */
    suspend fun fetchWithStatus(
        timeoutMs: Int = 10_000,
    ): Pair<AppConfigRemote, Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = HttpUtil.getUrlContent(
                UrlContentRequest(url = APP_CONFIG, timeout = timeoutMs)
            )
            if (body.isNullOrBlank()) {
                LogUtil.w(AppConfig.TAG, "Vpn5gRemoteConfig: empty response, using cache/defaults")
                return@withContext cached() to false
            }
            val parsed = parse(body)
            if (parsed == null) {
                LogUtil.w(AppConfig.TAG, "Vpn5gRemoteConfig: unparsable response, using cache/defaults")
                return@withContext cached() to false
            }
            MmkvManager.encodeSettings(KEY_APP_CONFIG_JSON, body)
            parsed to true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gRemoteConfig: fetch failed, using cache/defaults", e)
            cached() to false
        }
    }

    /**
     * Gson for the app config ONLY, with a tolerant [AdsRemote]
     * deserializer (v1.1.30): the bot publishes `ads` in two shapes —
     * the original object ({interstitial, user_ads, pricing}) and a
     * flat ARRAY of ad entries ({"enabled", "id", "text",
     * "image_url", "link_url", "duration_seconds", ...}). Plain Gson
     * throws on the array shape and the WHOLE config (plans, codes,
     * announcement) would fall back to defaults — the array shape is
     * therefore mapped onto [AdsRemote] here: the first enabled
     * entry becomes the interstitial, every entry becomes a
     * user ad (approved as published). The object shape parses as
     * before. Unknown shapes read as no ads, never as a failed
     * config. Pure mapping, JVM-tested in Vpn5gRemoteConfigTest.
     */
    internal val configGson: Gson = GsonBuilder()
        .registerTypeAdapter(AdsRemote::class.java, AdsRemoteDeserializer)
        .create()

    private object AdsRemoteDeserializer : JsonDeserializer<AdsRemote> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?,
        ): AdsRemote? {
            if (json == null || json.isJsonNull) return null
            return when {
                json.isJsonObject -> runCatching {
                    Gson().fromJson(json, AdsRemote::class.java)
                }.getOrNull()
                json.isJsonArray -> adsFromArray(json)
                else -> null
            }
        }

        /** Maps the flat ad-entry array onto [AdsRemote]. */
        private fun adsFromArray(array: JsonElement): AdsRemote {
            val entries = array.asJsonArray.mapNotNull { element ->
                runCatching { element.asJsonObject }.getOrNull()
            }
            fun str(obj: com.google.gson.JsonObject, key: String): String? =
                obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            fun bool(obj: com.google.gson.JsonObject, key: String): Boolean? =
                obj.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean
            fun int(obj: com.google.gson.JsonObject, key: String): Int? =
                obj.get(key)?.takeIf { it.isJsonPrimitive }?.asInt

            val userAds = entries.map { entry ->
                UserAdRemote(
                    id = str(entry, "id"),
                    text = str(entry, "text"),
                    image_url = str(entry, "image_url"),
                    link_url = str(entry, "link_url"),
                    model = str(entry, "type"),
                    enabled = bool(entry, "enabled"),
                    // The flat array is admin-published content (the
                    // bot only publishes approved ads here), so an
                    // entry counts as approved unless it says false.
                    approved = bool(entry, "approved") ?: true,
                )
            }
            val firstEnabled = entries.firstOrNull { bool(it, "enabled") == true }
            val interstitial = firstEnabled?.let { entry ->
                InterstitialAdRemote(
                    enabled = true,
                    duration_seconds = int(entry, "duration_seconds"),
                    text = str(entry, "text"),
                    image_url = str(entry, "image_url"),
                    link_url = str(entry, "link_url"),
                    id = str(entry, "id"),
                )
            }
            return AdsRemote(interstitial = interstitial, user_ads = userAds)
        }
    }

    private fun parse(json: String): AppConfigRemote? {
        return try {
            configGson.fromJson(json, AppConfigRemote::class.java)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gRemoteConfig: parse failed", e)
            null
        }
    }

    /**
     * Parses a config body with the app's exact parser (v1.1.30):
     * the ship probes and tests use this so a shape the app cannot
     * read is caught before release, never after.
     */
    internal fun parseForTest(json: String): AppConfigRemote? = parse(json)
}
