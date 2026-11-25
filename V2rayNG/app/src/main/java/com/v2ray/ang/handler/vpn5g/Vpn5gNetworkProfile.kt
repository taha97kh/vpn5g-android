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
 *   network: Wi-Fi and cellular both use 1400 (v1.1.27 — Wi-Fi was 1500 until
 *   1.1.26; TUN 1500 plus proxy encapsulation pushes outer packets past 1500,
 *   and where PMTUD/ICMP is filtered the large packets blackhole silently:
 *   small packets pass, video stalls). The value is written to the same
 *   setting the engine already reads ([SettingsManager.getVpnMtu] →
 *   `AppConfig.PREF_VPN_MTU`), so the core applies it to the TUN inbound
 *   with no core change.
 * - DNS is in-tunnel only: the VPN runs in Global mode, so every DNS query
 *   leaves through the tunnel; there is no split-DNS path and no system-DNS
 *   fallback while connected. This is a property of the Global TUN routing,
 *   not a separate setting — it is documented here so a future reader does
 *   not "fix" it by adding one.
 * - QUIC (UDP 443) is fast-rejected (v1.1.27): Meta apps stream video over
 *   QUIC, but the public servers' UDP-over-TCP relay dies under sustained
 *   QUIC load (device-verified 2026-11-25: Instagram video plays ~2 s then
 *   stalls forever, on Wi-Fi and mobile; TCP video in Telegram is stable).
 *   [ensureQuicFastReject] puts a UDP-443 → `reject` rule first in the
 *   stored ruleset before connect. The `reject` outbound is a SOCKS outbound
 *   to a closed loopback port (127.0.0.1:9), so every QUIC session fails
 *   immediately (connection refused) and the app falls back to TCP/TLS at
 *   once — unlike the upstream preset's "Block udp443" rule, whose
 *   blackhole outbound silently swallows UDP and leaves the client to its
 *   slow retransmission timeouts. Any stored block-UDP-443 rule is removed
 *   (migration); disabled rules are left alone (the user turned them off
 *   deliberately). All other UDP (DNS, voice, gaming) is untouched.
 */
object Vpn5gNetworkProfile {

    enum class Transport { WIFI, CELLULAR, OTHER }

    data class Profile(val mtu: Int, val transport: Transport)

    const val MTU_WIFI = 1400
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
     * range; only the exact single-port 443 block counts — a range that
     * merely contains 443 is a user rule and stays.
     */
    fun isForcedQuicBlock(rule: RulesetItem): Boolean {
        return rule.enabled &&
            rule.outboundTag == AppConfig.TAG_BLOCKED &&
            rule.network?.trim()?.lowercase() == "udp" &&
            rule.port?.trim() == "443"
    }

    /**
     * Pure check: is this stored rule a QUIC fast-reject? An enabled rule
     * that sends UDP port 443 to the `reject` outbound. Same exact-port
     * rule as [isForcedQuicBlock].
     */
    fun isQuicRejectRule(rule: RulesetItem): Boolean {
        return rule.enabled &&
            rule.outboundTag == AppConfig.TAG_REJECT &&
            rule.network?.trim()?.lowercase() == "udp" &&
            rule.port?.trim() == "443"
    }

    /** The VPN5G QUIC fast-reject rule (UDP 443 → `reject` outbound). */
    fun quicRejectRule(): RulesetItem {
        return RulesetItem(
            id = "vpn5g-quic-fast-reject",
            remarks = "VPN5G: QUIC fast-reject (UDP 443)",
            outboundTag = AppConfig.TAG_REJECT,
            port = "443",
            network = "udp",
            enabled = true,
        )
    }

    /**
     * Pure ruleset transform: drop every enabled block-UDP-443 rule
     * (migration from the pre-1.1.27 policy) and every existing
     * reject-UDP-443 rule (dedupe), then put the fast-reject rule first so
     * it wins over any proxy/direct rule below it. All other rules keep
     * their relative order; disabled rules are never touched. Idempotent:
     * applying it to its own output returns an equal list.
     */
    fun withQuicFastReject(rules: List<RulesetItem>): List<RulesetItem> {
        val kept = rules.filterNot { isForcedQuicBlock(it) || isQuicRejectRule(it) }
        return listOf(quicRejectRule()) + kept
    }

    /**
     * Make the stored routing ruleset carry the QUIC fast-reject rule
     * before the tunnel is built. Returns 1 when the stored ruleset was
     * rewritten, 0 when it was already correct, absent, or on failure.
     * Never throws.
     */
    fun ensureQuicFastReject(): Int {
        return try {
            val rulesets = MmkvManager.decodeRoutingRulesets() ?: return 0
            val updated = withQuicFastReject(rulesets)
            if (updated == rulesets) return 0
            MmkvManager.encodeRoutingRulesets(updated.toMutableList())
            LogUtil.i("Vpn5gNetworkProfile", "Stored routing ruleset now fast-rejects UDP 443 (QUIC)")
            1
        } catch (e: Exception) {
            LogUtil.e("Vpn5gNetworkProfile", "Failed to apply QUIC fast-reject to routing ruleset", e)
            0
        }
    }
}
