package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil

/**
 * Network profile for the VPN5G connect path (v1.1.4 §6).
 *
 * - MTU is derived from the active transport instead of forcing 1500 on every
 *   network: Wi-Fi keeps 1500, cellular uses 1400 (mobile carriers commonly
 *   fragment at 1500). The value is written to the same setting the engine
 *   already reads ([SettingsManager.getVpnMtu] → `AppConfig.PREF_VPN_MTU`),
 *   so the core applies it to the TUN inbound with no core change.
 * - DNS is in-tunnel only: the VPN runs in Global mode, so every DNS query
 *   leaves through the tunnel; there is no split-DNS path and no system-DNS
 *   fallback while connected. This is a property of the Global TUN routing,
 *   not a separate setting — it is documented here so a future reader does
 *   not "fix" it by adding one.
 * - UDP is never blocked by VPN5G itself: the upstream routing presets ship
 *   a "Block udp443" rule (outboundTag `block`, network `udp`, port `443`)
 *   that force-blocks QUIC/HTTP3 on the default WHITE preset. The VPN5G
 *   app never imports those presets, but a stored ruleset (from an earlier
 *   version or from the upstream routing screen) can still carry the rule
 *   into the generated config. [removeForcedQuicBlock] strips enabled
 *   block-UDP-443 rules from the stored ruleset before connect; disabled
 *   rules are left alone (the user turned them off deliberately).
 */
object Vpn5gNetworkProfile {

    enum class Transport { WIFI, CELLULAR, OTHER }

    data class Profile(val mtu: Int, val transport: Transport)

    const val MTU_WIFI = 1500
    const val MTU_CELLULAR = 1400
    const val MTU_FALLBACK = 1400

    fun profileFor(transport: Transport): Profile {
        return when (transport) {
            Transport.WIFI -> Profile(MTU_WIFI, Transport.WIFI)
            Transport.CELLULAR -> Profile(MTU_CELLULAR, Transport.CELLULAR)
            Transport.OTHER -> Profile(MTU_FALLBACK, Transport.OTHER)
        }
    }

    fun transportOf(capabilities: NetworkCapabilities?): Transport {
        if (capabilities == null) return Transport.OTHER
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            else -> Transport.OTHER
        }
    }

    fun currentTransport(context: Context): Transport {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = manager?.activeNetwork ?: return Transport.OTHER
            transportOf(manager.getNetworkCapabilities(network))
        } catch (_: Exception) {
            Transport.OTHER
        }
    }

    /**
     * Write the transport-derived MTU to the setting the engine reads and
     * return the value. Never throws; falls back to [MTU_FALLBACK].
     */
    fun applyMtu(context: Context): Int {
        val mtu = try {
            profileFor(currentTransport(context)).mtu
        } catch (_: Exception) {
            MTU_FALLBACK
        }
        return try {
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_MTU, mtu.toString())
            mtu
        } catch (_: Exception) {
            MTU_FALLBACK
        }
    }

    /**
     * Pure check: is this stored rule a forced QUIC block? An enabled rule
     * that blocks UDP port 443. `port` may be a single port ("443") or a
     * range; only the exact single-port 443 block is stripped — a range
     * that merely contains 443 is a user rule and stays.
     */
    fun isForcedQuicBlock(rule: RulesetItem): Boolean {
        return rule.enabled &&
            rule.outboundTag == AppConfig.TAG_BLOCKED &&
            rule.network?.trim()?.lowercase() == "udp" &&
            rule.port?.trim() == "443"
    }

    /**
     * Strip enabled block-UDP-443 rules from the stored routing ruleset.
     * Returns how many rules were removed (0 when there was no stored
     * ruleset or nothing to strip). Never throws.
     */
    fun removeForcedQuicBlock(): Int {
        return try {
            val rulesets = MmkvManager.decodeRoutingRulesets() ?: return 0
            val before = rulesets.size
            val kept = rulesets.filterNot { isForcedQuicBlock(it) }
            if (kept.size == before) return 0
            MmkvManager.encodeRoutingRulesets(kept.toMutableList())
            val removed = before - kept.size
            LogUtil.i("Vpn5gNetworkProfile", "Removed $removed forced QUIC block rule(s) from stored routing ruleset")
            removed
        } catch (e: Exception) {
            LogUtil.e("Vpn5gNetworkProfile", "Failed to sanitize routing ruleset", e)
            0
        }
    }
}
