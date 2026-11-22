package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** JVM tests for the pure verdict of [Vpn5gTunnelCheck.evaluateExitIp]:
 * a tunnel IP that differs from the baseline proves traffic flows
 * through the tunnel; the same IP, a blank IP, or no IP at all fails. */
class Vpn5gTunnelCheckTest {

    @Test
    fun test_different_ip_verifies() {
        val result = Vpn5gTunnelCheck.evaluateExitIp("1.2.3.4", "5.6.7.8")
        assertEquals(Vpn5gTunnelCheck.Verdict.VERIFIED, result.verdict)
        assertEquals("1.2.3.4", result.exitIp)
    }

    @Test
    fun test_missing_baseline_verifies_on_any_ip() {
        val result = Vpn5gTunnelCheck.evaluateExitIp("1.2.3.4", null)
        assertEquals(Vpn5gTunnelCheck.Verdict.VERIFIED, result.verdict)
        assertEquals("1.2.3.4", result.exitIp)
    }

    @Test
    fun test_unchanged_ip_fails() {
        val result = Vpn5gTunnelCheck.evaluateExitIp("5.6.7.8", "5.6.7.8")
        assertEquals(Vpn5gTunnelCheck.Verdict.FAILED, result.verdict)
        assertEquals("5.6.7.8", result.exitIp)
        assertEquals("exit IP unchanged", result.detail)
    }

    @Test
    fun test_blank_ip_fails() {
        val noIp = Vpn5gTunnelCheck.evaluateExitIp(null, "5.6.7.8")
        assertEquals(Vpn5gTunnelCheck.Verdict.FAILED, noIp.verdict)
        assertEquals("no reply through the tunnel", noIp.detail)

        val blankIp = Vpn5gTunnelCheck.evaluateExitIp("   ", "5.6.7.8")
        assertEquals(Vpn5gTunnelCheck.Verdict.FAILED, blankIp.verdict)
    }
}
