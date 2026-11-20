package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.APP_CONFIG
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DEFAULT_FREE_COUNTRIES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_APP_CONFIG_JSON
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches and caches the remote app configuration (app_config.json).
 *
 * The file does not exist on the server yet: a 404 or any fetch failure is
 * normal in v1 and falls back to safe defaults (cached JSON when available,
 * otherwise [DEFAULT_FREE_COUNTRIES] and no notice). Never throws.
 */
object Vpn5gRemoteConfig {

    fun defaults(): AppConfigRemote = AppConfigRemote(
        free_countries = DEFAULT_FREE_COUNTRIES,
        notice = null,
        config_sources = emptyList(),
        min_version = null,
        subscription_status = null,
    )

    fun cached(): AppConfigRemote {
        val json = MmkvManager.decodeSettingsString(KEY_APP_CONFIG_JSON)
        if (json.isNullOrBlank()) return defaults()
        return parse(json) ?: defaults()
    }

    suspend fun fetch(): AppConfigRemote = withContext(Dispatchers.IO) {
        try {
            val body = HttpUtil.getUrlContent(
                UrlContentRequest(url = APP_CONFIG, timeout = 10_000)
            )
            if (body.isNullOrBlank()) {
                LogUtil.w(AppConfig.TAG, "Vpn5gRemoteConfig: empty response, using cache/defaults")
                return@withContext cached()
            }
            val parsed = parse(body)
            if (parsed == null) {
                LogUtil.w(AppConfig.TAG, "Vpn5gRemoteConfig: unparsable response, using cache/defaults")
                return@withContext cached()
            }
            MmkvManager.encodeSettings(KEY_APP_CONFIG_JSON, body)
            parsed
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gRemoteConfig: fetch failed, using cache/defaults", e)
            cached()
        }
    }

    private fun parse(json: String): AppConfigRemote? {
        return try {
            JsonUtil.fromJsonSafe(json, AppConfigRemote::class.java)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gRemoteConfig: parse failed", e)
            null
        }
    }
}
