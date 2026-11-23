package com.v2ray.ang.handler.vpn5g

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    /** v1.1.13 probe fallback: the primary result wins and the fallback never runs. */
    @Test
    fun test_probeIp_primary_wins_and_fallback_never_runs() = runBlocking {
        var fallbackRan = false
        val ip = Vpn5gTunnelCheck.probeIp(
            primary = { "1.2.3.4" },
            fallback = { fallbackRan = true; "9.9.9.9" },
        )
        assertEquals("1.2.3.4", ip)
        assertFalse(fallbackRan)
    }

    /** v1.1.13: a null primary is saved by the fallback — the probe still verifies. */
    @Test
    fun test_probeIp_fallback_saves_probe_when_primary_yields_nothing() = runBlocking {
        val ip = Vpn5gTunnelCheck.probeIp(
            primary = { null },
            fallback = { "9.9.9.9" },
        )
        assertEquals("9.9.9.9", ip)
        val result = Vpn5gTunnelCheck.evaluateExitIp(ip, "5.6.7.8")
        assertEquals(Vpn5gTunnelCheck.Verdict.VERIFIED, result.verdict)
    }

    /** v1.1.13: a blank primary counts as nothing, so the fallback still runs. */
    @Test
    fun test_probeIp_blank_primary_falls_back() = runBlocking {
        val ip = Vpn5gTunnelCheck.probeIp(
            primary = { "   " },
            fallback = { "9.9.9.9" },
        )
        assertEquals("9.9.9.9", ip)
    }

    /** v1.1.13: both fetches yielding nothing is still a failed probe. */
    @Test
    fun test_probeIp_both_null_fails() = runBlocking {
        val ip = Vpn5gTunnelCheck.probeIp(
            primary = { null },
            fallback = { null },
        )
        assertNull(ip)
        val result = Vpn5gTunnelCheck.evaluateExitIp(ip, "5.6.7.8")
        assertEquals(Vpn5gTunnelCheck.Verdict.FAILED, result.verdict)
    }
}
