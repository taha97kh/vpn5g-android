package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_OPERATOR_HISTORY_JSON
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_OPERATOR_LAST
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil

/**
 * Per-operator adaptation (v1.1.14).
 *
 * The app detects the current network — Wi-Fi vs mobile, and the mobile
 * carrier name when the system hands it over — shows it on the home
 * screen, and prefers servers that already proved themselves on THIS
 * operator. The preference is built ONLY from the app's own verification
 * history: every time a tunnel verifies, the server's identity is
 * recorded under the current operator key. With no history for an
 * operator the order is exactly today's (fastest real ping first).
 *
 * Permissions: NONE (v1.1.25 — the phone-state permission and its
 * runtime request were removed by Aref's order, so Android no
 * longer lists a Phone permission for the app). The carrier name
 * read (`TelephonyManager.networkOperatorName`) stays best-effort
 * inside try/catch: without the permission the system returns null
 * (or throws SecurityException) and the operator is just "mobile" —
 * nothing else changes. Wi-Fi vs mobile needs no permission.
 */
object Vpn5gOperator {

    enum class Kind { WIFI, CELLULAR, OTHER }

    data class OperatorInfo(
        val kind: Kind,
        /** Raw carrier name from the system, or null (Wi-Fi / unknown / denied). */
        val carrierRaw: String? = null,
    ) {
        /** Persian label shown in the UI. */
        val labelFa: String
            get() = when (kind) {
                Kind.WIFI -> "وای‌فای"
                Kind.CELLULAR -> labelForCarrier(carrierRaw) ?: "موبایل"
                Kind.OTHER -> "نامشخص"
            }

        /** Stable key for the verification history ("wifi", "cell:irancell", "cell:unknown"). */
        val key: String
            get() = when (kind) {
                Kind.WIFI -> "wifi"
                Kind.CELLULAR -> "cell:" + (carrierKey(carrierRaw) ?: "unknown")
                Kind.OTHER -> "other"
            }
    }

    /** Persian carrier label for a raw system carrier name, or null when unknown. Pure. */
    fun labelForCarrier(raw: String?): String? {
        val name = raw?.trim().orEmpty()
        if (name.isEmpty()) return null
        val lower = name.lowercase()
        return when {
            "irancell" in lower || lower.startsWith("mtn") -> "ایرانسل"
            "rightel" in lower || "righTel" in lower -> "رایتل"
            "hamrah" in lower || "mci" in lower || lower == "tci" || "tci" in lower -> "همراه اول"
            "shatel" in lower -> "شاتل موبایل"
            "aptel" in lower -> "آپتل"
            "taliya" in lower -> "تالیا"
            else -> name
        }
    }

    /** Stable latin key for a raw carrier name, or null when unknown. Pure. */
    fun carrierKey(raw: String?): String? {
        val label = labelForCarrier(raw) ?: return null
        return when (label) {
            "ایرانسل" -> "irancell"
            "رایتل" -> "rightel"
            "همراه اول" -> "mci"
            "شاتل موبایل" -> "shatel"
            "آپتل" -> "aptel"
            "تالیا" -> "taliya"
            else -> raw?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")
                ?.trim('-')?.takeIf { it.isNotEmpty() }
        }
    }

    /** Detects the current network. Never throws; never crashes on a denied permission. */
    fun detect(context: Context): OperatorInfo {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
            val network = manager?.activeNetwork
            val caps = network?.let { manager.getNetworkCapabilities(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            when {
                isWifi -> OperatorInfo(Kind.WIFI)
                isCellular -> {
                    val carrier = try {
                        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE)
                            as? TelephonyManager
                        telephony?.networkOperatorName?.takeIf { it.isNotBlank() }
                    } catch (_: SecurityException) {
                        // No phone permission (v1.1.25): degrade to "mobile".
                        null
                    } catch (_: Exception) {
                        null
                    }
                    OperatorInfo(Kind.CELLULAR, carrier)
                }
                else -> OperatorInfo(Kind.OTHER)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gOperator: detect failed", e)
            OperatorInfo(Kind.OTHER)
        }
    }

    /** The last detected operator key (written by the ViewModel on detect). */
    fun currentKey(): String? {
        return MmkvManager.decodeSettingsString(KEY_OPERATOR_LAST)?.takeIf { it.isNotBlank() }
    }

    fun storeCurrentKey(key: String) {
        MmkvManager.encodeSettings(KEY_OPERATOR_LAST, key)
    }

    /** Server dedupe keys that verified on [operatorKey] (this device's own history). */
    fun verifiedKeysFor(operatorKey: String): Set<String> {
        return loadHistory()[operatorKey].orEmpty()
    }

    /**
     * Records that [dedupeKey] verified on [operatorKey]. Best effort;
     * at most [MAX_KEYS_PER_OPERATOR] keys per operator are kept.
     */
    fun recordVerified(operatorKey: String, dedupeKey: String) {
        if (operatorKey.isBlank() || dedupeKey.isBlank()) return
        try {
            val history = loadHistory().toMutableMap()
            val keys = history[operatorKey].orEmpty().toMutableSet()
            if (!keys.add(dedupeKey)) return
            val trimmed = if (keys.size > MAX_KEYS_PER_OPERATOR) {
                keys.drop(keys.size - MAX_KEYS_PER_OPERATOR).toSet()
            } else {
                keys
            }
            history[operatorKey] = trimmed
            saveHistory(history)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gOperator: record failed", e)
        }
    }

    /**
     * Pure preference sort: servers that verified on the current
     * operator first, then fastest real ping first (ping <= 0 last).
     * With no history for the operator this is exactly the ping order.
     */
    fun preferOperatorVerified(
        servers: List<Vpn5gServerUi>,
        verifiedKeys: Set<String>,
        dedupeKeyOf: (Vpn5gServerUi) -> String,
    ): List<Vpn5gServerUi> {
        if (verifiedKeys.isEmpty()) return servers
        return servers.sortedWith(
            compareByDescending<Vpn5gServerUi> { dedupeKeyOf(it) in verifiedKeys }
                .thenBy { if (it.pingMillis <= 0L) Long.MAX_VALUE else it.pingMillis }
        )
    }

    private const val MAX_KEYS_PER_OPERATOR = 200

    @Suppress("UNCHECKED_CAST")
    private fun loadHistory(): Map<String, Set<String>> {
        val json = MmkvManager.decodeSettingsString(KEY_OPERATOR_HISTORY_JSON)
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val raw = JsonUtil.fromJsonSafe(json, Map::class.java) as? Map<*, *>
                ?: return emptyMap()
            raw.entries.mapNotNull { (key, value) ->
                val k = key?.toString() ?: return@mapNotNull null
                val v = value?.toString().orEmpty()
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                k to v
            }.toMap()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gOperator: history parse failed", e)
            emptyMap()
        }
    }

    private fun saveHistory(history: Map<String, Set<String>>) {
        val json = history.entries.joinToString(
            prefix = "{", postfix = "}"
        ) { (key, keys) ->
            "\"$key\":\"${keys.joinToString(",")}\""
        }
        MmkvManager.encodeSettings(KEY_OPERATOR_HISTORY_JSON, json)
    }
}
