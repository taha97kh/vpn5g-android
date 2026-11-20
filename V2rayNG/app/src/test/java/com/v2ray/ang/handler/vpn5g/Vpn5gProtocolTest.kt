package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the protocol filter (pure Kotlin). */
class Vpn5gProtocolTest {

    private fun server(guid: String, protocol: String) = Vpn5gServerUi(
        guid = guid,
        name = guid,
        country = "بین‌الملل",
        pingMillis = 10L,
        isSelected = false,
        protocol = protocol,
    )

    private fun section(servers: List<Vpn5gServerUi>) = Vpn5gCountryUi(
        label = "بین‌الملل",
        isFree = true,
        servers = servers,
    )

    @Test
    fun test_normalize_unknown_falls_back_to_auto() {
        assertEquals(Vpn5gProtocol.AUTO, Vpn5gProtocol.normalize(null))
        assertEquals(Vpn5gProtocol.AUTO, Vpn5gProtocol.normalize(""))
        assertEquals(Vpn5gProtocol.AUTO, Vpn5gProtocol.normalize("wireguard"))
        assertEquals(Vpn5gProtocol.VLESS, Vpn5gProtocol.normalize("vless"))
    }

    @Test
    fun test_auto_matches_everything() {
        assertTrue(Vpn5gProtocol.matches(Vpn5gProtocol.AUTO, "vless"))
        assertTrue(Vpn5gProtocol.matches(Vpn5gProtocol.AUTO, ""))
        assertTrue(Vpn5gProtocol.matches(Vpn5gProtocol.AUTO, "wireguard"))
    }

    @Test
    fun test_specific_protocol_matches_only_its_scheme() {
        assertTrue(Vpn5gProtocol.matches(Vpn5gProtocol.VLESS, "vless"))
        assertTrue(Vpn5gProtocol.matches(Vpn5gProtocol.VLESS, "VLESS"))
        assertFalse(Vpn5gProtocol.matches(Vpn5gProtocol.VLESS, "vmess"))
        assertFalse(Vpn5gProtocol.matches(Vpn5gProtocol.VLESS, ""))
        assertFalse(Vpn5gProtocol.matches(Vpn5gProtocol.TROJAN, "ss"))
        assertTrue(Vpn5gProtocol.matches(Vpn5gProtocol.SHADOWSOCKS, "ss"))
    }

    @Test
    fun test_filter_sections_keeps_only_matching_servers() {
        val sections = listOf(
            section(
                listOf(
                    server("a", "vless"),
                    server("b", "vmess"),
                    server("c", ""),
                )
            )
        )
        val filtered = Vpn5gProtocol.filterSections(sections, Vpn5gProtocol.VMESS)
        assertEquals(1, filtered.size)
        assertEquals(listOf("b"), filtered[0].servers.map { it.guid })
    }

    @Test
    fun test_filter_sections_drops_empty_sections() {
        val sections = listOf(section(listOf(server("a", "vless"))))
        assertTrue(Vpn5gProtocol.filterSections(sections, Vpn5gProtocol.TROJAN).isEmpty())
        assertEquals(1, Vpn5gProtocol.filterSections(sections, Vpn5gProtocol.AUTO).size)
    }
}
