package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.29 other-VPN detection decision (pure). */
class Vpn5gOtherVpnTest {

    @Test
    fun test_other_vpn_when_transport_active_and_ours_not_running() {
        assertTrue(Vpn5gOtherVpn.isOtherVpnActive(true, false))
    }

    @Test
    fun test_our_own_vpn_is_not_an_other_vpn() {
        assertFalse(Vpn5gOtherVpn.isOtherVpnActive(true, true))
    }

    @Test
    fun test_no_vpn_transport_no_notice() {
        assertFalse(Vpn5gOtherVpn.isOtherVpnActive(false, false))
        assertFalse(Vpn5gOtherVpn.isOtherVpnActive(false, true))
    }
}
