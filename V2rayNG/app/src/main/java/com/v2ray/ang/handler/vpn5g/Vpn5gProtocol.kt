package com.v2ray.ang.handler.vpn5g

/**
 * Protocol filter (v1.1): the protocol picker lists ONLY the protocols
 * the V2Ray core in this app actually supports for the synced configs —
 * VLESS, VMess, Trojan, Shadowsocks — plus Auto (no filtering, best for
 * the network). Protocols the app cannot use are never listed.
 *
 * Pure Kotlin, no Android APIs, so it is covered by JVM unit tests
 * (Vpn5gProtocolTest). The server scheme comes from the stored profile's
 * config type (see Vpn5gSync.loadGroupedServers).
 */
object Vpn5gProtocol {

    const val AUTO = "auto"
    const val VLESS = "vless"
    const val VMESS = "vmess"
    const val TROJAN = "trojan"
    const val SHADOWSOCKS = "ss"

    /** Picker order. */
    val ALL = listOf(AUTO, VLESS, VMESS, TROJAN, SHADOWSOCKS)

    /** Unknown or blank ids fall back to Auto. Never throws. */
    fun normalize(id: String?): String = if (id in ALL) id!! else AUTO

    /**
     * A server matches the filter when the filter is Auto, or when the
     * server's scheme equals the filter. Servers whose protocol is unknown
     * only show under Auto.
     */
    fun matches(filterId: String, scheme: String): Boolean {
        val filter = normalize(filterId)
        if (filter == AUTO) return true
        return scheme.lowercase() == filter
    }

    /** Filters country sections to the servers matching [filterId]. */
    fun filterSections(
        sections: List<Vpn5gCountryUi>,
        filterId: String,
    ): List<Vpn5gCountryUi> {
        val filter = normalize(filterId)
        if (filter == AUTO) return sections
        return sections.mapNotNull { section ->
            val servers = section.servers.filter { matches(filter, it.protocol) }
            if (servers.isEmpty()) null else section.copy(servers = servers)
        }
    }
}
