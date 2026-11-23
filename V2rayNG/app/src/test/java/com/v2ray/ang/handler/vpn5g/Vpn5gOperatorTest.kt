package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** JVM tests for the pure operator helpers ([Vpn5gOperator]). */
class Vpn5gOperatorTest {

    @Test
    fun test_carrier_labels() {
        assertEquals("ایرانسل", Vpn5gOperator.labelForCarrier("Irancell"))
        assertEquals("ایرانسل", Vpn5gOperator.labelForCarrier("MTN Irancell"))
        assertEquals("همراه اول", Vpn5gOperator.labelForCarrier("Hamrah Aval"))
        assertEquals("همراه اول", Vpn5gOperator.labelForCarrier("TCI"))
        assertEquals("رایتل", Vpn5gOperator.labelForCarrier("Rightel"))
        assertNull(Vpn5gOperator.labelForCarrier(null))
        assertNull(Vpn5gOperator.labelForCarrier("  "))
        // An unknown carrier is shown as-is, never relabelled.
        assertEquals("MyCarrier", Vpn5gOperator.labelForCarrier("MyCarrier"))
    }

    @Test
    fun test_carrier_keys() {
        assertEquals("irancell", Vpn5gOperator.carrierKey("Irancell"))
        assertEquals("mci", Vpn5gOperator.carrierKey("Hamrah Aval"))
        assertEquals("rightel", Vpn5gOperator.carrierKey("Rightel"))
        assertNull(Vpn5gOperator.carrierKey(null))
    }

    @Test
    fun test_operator_info_labels_and_keys() {
        assertEquals("وای‌فای", Vpn5gOperator.OperatorInfo(Vpn5gOperator.Kind.WIFI).labelFa)
        assertEquals("wifi", Vpn5gOperator.OperatorInfo(Vpn5gOperator.Kind.WIFI).key)
        val cell = Vpn5gOperator.OperatorInfo(Vpn5gOperator.Kind.CELLULAR, "Irancell")
        assertEquals("ایرانسل", cell.labelFa)
        assertEquals("cell:irancell", cell.key)
        // No carrier name (permission denied): just "mobile".
        val unknown = Vpn5gOperator.OperatorInfo(Vpn5gOperator.Kind.CELLULAR, null)
        assertEquals("موبایل", unknown.labelFa)
        assertEquals("cell:unknown", unknown.key)
    }

    @Test
    fun test_operator_verified_servers_sort_first() {
        val servers = listOf(
            Vpn5gServerUi("slow", "s", "c", 500, false, hasRealPing = true),
            Vpn5gServerUi("fast", "f", "c", 50, false, hasRealPing = true),
            Vpn5gServerUi("verified", "v", "c", 300, false, hasRealPing = true),
        )
        val ranked = Vpn5gOperator.preferOperatorVerified(
            servers, setOf("vless://host:443:verified")
        ) { server ->
            // Dedupe-key stub: the test key is the guid.
            "vless://host:443:${server.guid}"
        }
        assertEquals(listOf("verified", "fast", "slow"), ranked.map { it.guid })
    }

    @Test
    fun test_no_history_keeps_ping_order() {
        val servers = listOf(
            Vpn5gServerUi("slow", "s", "c", 500, false, hasRealPing = true),
            Vpn5gServerUi("fast", "f", "c", 50, false, hasRealPing = true),
        )
        val ranked = Vpn5gOperator.preferOperatorVerified(servers, emptySet()) { it.guid }
        assertEquals(listOf("slow", "fast"), ranked.map { it.guid })
    }
}
