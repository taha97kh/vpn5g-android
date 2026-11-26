package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus.Vpn5gUserSubscription
import com.v2ray.ang.handler.vpn5g.Vpn5gVolume.ConsumedState
import com.v2ray.ang.handler.vpn5g.Vpn5gVolume.VolumeCounter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** JVM tests for the v1.1.18 volume model (Model B): cap parsing, the
 * exhausted decision, grant-keyed accumulation and GB formatting —
 * all pure Kotlin. */
class Vpn5gVolumeTest {

    private val now = 1_800_000_000L // ~2027-01-15
    private val future = now + 30 * 86_400L
    private val cap20Gb = 20L * Vpn5gVolume.GB_BYTES

    private fun plans() = listOf(
        SubscriptionPlanRemote(id = "sub_1", days = 30, price_toman = 50_000L),
        SubscriptionPlanRemote(id = "gb_20", days = 30, price_toman = 50_000L, gb = 20.0),
        SubscriptionPlanRemote(id = "gb_50", days = 30, price_toman = 100_000L, volume_gb = 50.0, gb = 20.0),
    )

    // -- Cap parsing ----------------------------------------------------------

    @Test
    fun test_cap_bytes_from_gigabytes() {
        assertEquals(20_000_000_000L, Vpn5gVolume.capBytes(20.0))
        assertEquals(500_000_000L, Vpn5gVolume.capBytes(0.5))
        assertNull(Vpn5gVolume.capBytes(null))
        assertNull(Vpn5gVolume.capBytes(0.0))
        assertNull(Vpn5gVolume.capBytes(-5.0))
        assertNull(Vpn5gVolume.capBytes(Double.NaN))
        assertNull(Vpn5gVolume.capBytes(Double.POSITIVE_INFINITY))
    }

    @Test
    fun test_cap_bytes_for_plan_id_volume_gb_wins_over_gb() {
        // gb_50 publishes both: volume_gb (50) wins over the gb alias (20).
        assertEquals(50_000_000_000L, Vpn5gVolume.capBytesForPlanId("gb_50", plans()))
        // gb_20 publishes only the gb alias (the live config shape).
        assertEquals(20_000_000_000L, Vpn5gVolume.capBytesForPlanId("gb_20", plans()))
        // A time-only plan has no cap: never an invented number.
        assertNull(Vpn5gVolume.capBytesForPlanId("sub_1", plans()))
        // Unknown plan / no plans / blank id: no cap.
        assertNull(Vpn5gVolume.capBytesForPlanId("nope", plans()))
        assertNull(Vpn5gVolume.capBytesForPlanId("gb_20", null))
        assertNull(Vpn5gVolume.capBytesForPlanId(null, plans()))
        assertNull(Vpn5gVolume.capBytesForPlanId("  ", plans()))
        // Plan ids match case-insensitively.
        assertEquals(20_000_000_000L, Vpn5gVolume.capBytesForPlanId("GB_20", plans()))
    }

    // -- Exhausted decision ---------------------------------------------------

    @Test
    fun test_is_exhausted_at_and_above_cap() {
        assertTrue(Vpn5gVolume.isExhausted(cap20Gb, cap20Gb))
        assertTrue(Vpn5gVolume.isExhausted(cap20Gb + 1L, cap20Gb))
        assertFalse(Vpn5gVolume.isExhausted(cap20Gb - 1L, cap20Gb))
        assertFalse(Vpn5gVolume.isExhausted(0L, cap20Gb))
    }

    @Test
    fun test_is_exhausted_never_without_cap_or_consumption() {
        // No cap = unlimited, whatever was consumed.
        assertFalse(Vpn5gVolume.isExhausted(Long.MAX_VALUE, null))
        // Unknown consumption never ends a subscription.
        assertFalse(Vpn5gVolume.isExhausted(null, cap20Gb))
        assertFalse(Vpn5gVolume.isExhausted(null, null))
    }

    @Test
    fun test_remaining_bytes_and_fraction() {
        assertEquals(5_000_000_000L, Vpn5gVolume.remainingBytes(15_000_000_000L, cap20Gb))
        assertEquals(0L, Vpn5gVolume.remainingBytes(cap20Gb, cap20Gb))
        assertEquals(0L, Vpn5gVolume.remainingBytes(cap20Gb + 1L, cap20Gb))
        assertNull(Vpn5gVolume.remainingBytes(1L, null))
        assertEquals(0.5f, Vpn5gVolume.usageFraction(10_000_000_000L, cap20Gb))
        assertEquals(1f, Vpn5gVolume.usageFraction(cap20Gb + 1L, cap20Gb))
        assertEquals(0f, Vpn5gVolume.usageFraction(0L, cap20Gb))
        assertNull(Vpn5gVolume.usageFraction(1L, null))
    }

    // -- Grant keys and attribution -------------------------------------------

    @Test
    fun test_grant_key_for_active_grant() {
        val sub = Vpn5gUserSubscription("active", future, "gb_20")
        assertEquals("gb_20|$future", Vpn5gVolume.grantKeyFor(sub))
    }

    @Test
    fun test_grant_key_null_without_active_grant() {
        assertNull(Vpn5gVolume.grantKeyFor(null))
        assertNull(Vpn5gVolume.grantKeyFor(Vpn5gUserSubscription("none", future, "gb_20")))
        assertNull(Vpn5gVolume.grantKeyFor(Vpn5gUserSubscription("cancelled", future, "gb_20")))
        assertNull(Vpn5gVolume.grantKeyFor(Vpn5gUserSubscription("active", future, null)))
        assertNull(Vpn5gVolume.grantKeyFor(Vpn5gUserSubscription("active", future, "  ")))
        assertNull(Vpn5gVolume.grantKeyFor(Vpn5gUserSubscription("active", null, "gb_20")))
    }

    @Test
    fun test_bytes_for_attributes_by_grant_key() {
        val sub = Vpn5gUserSubscription("active", future, "gb_20")
        val state = ConsumedState("gb_20|$future", 15_000_000_000L)
        assertEquals(15_000_000_000L, Vpn5gVolume.bytesFor(sub, state))
        // Bytes measured under another grant never count here.
        assertNull(Vpn5gVolume.bytesFor(sub, ConsumedState("sub_1|$future", 1L)))
        assertNull(Vpn5gVolume.bytesFor(sub, ConsumedState("gb_20|${future + 1}", 1L)))
        assertNull(Vpn5gVolume.bytesFor(sub, null))
        assertNull(Vpn5gVolume.bytesFor(null, state))
    }

    // -- Accumulation -----------------------------------------------------------

    @Test
    fun test_accumulate_adds_under_same_grant() {
        val counter = VolumeCounter("gb_20|$future", 1_000L)
        val next = Vpn5gVolume.accumulate(counter, "gb_20|$future", 500L)
        assertEquals(VolumeCounter("gb_20|$future", 1_500L), next)
    }

    @Test
    fun test_accumulate_resets_on_new_grant() {
        val counter = VolumeCounter("gb_20|$future", 19_000_000_000L)
        val next = Vpn5gVolume.accumulate(counter, "gb_50|${future + 1}", 700L)
        assertEquals(VolumeCounter("gb_50|${future + 1}", 700L), next)
    }

    @Test
    fun test_accumulate_no_grant_is_no_op() {
        val counter = VolumeCounter("gb_20|$future", 1_000L)
        assertEquals(counter, Vpn5gVolume.accumulate(counter, null, 500L))
        // A fresh counter with no grant stays empty.
        assertEquals(
            VolumeCounter(null, 0L),
            Vpn5gVolume.accumulate(VolumeCounter(null, 0L), null, 500L),
        )
    }

    @Test
    fun test_accumulate_never_goes_negative_or_wraps() {
        val counter = VolumeCounter("gb_20|$future", 1_000L)
        assertEquals(1_000L, Vpn5gVolume.accumulate(counter, "gb_20|$future", -50L).consumedBytes)
        val huge = Vpn5gVolume.accumulate(
            VolumeCounter("gb_20|$future", Long.MAX_VALUE - 10L),
            "gb_20|$future",
            100L,
        )
        assertEquals(Long.MAX_VALUE, huge.consumedBytes)
    }

    // -- Formatting ---------------------------------------------------------------

    @Test
    fun test_format_gb_one_decimal() {
        assertEquals("20.0", Vpn5gVolume.formatGb(20_000_000_000L, Locale.US))
        assertEquals("0.5", Vpn5gVolume.formatGb(500_000_000L, Locale.US))
        assertEquals("0.0", Vpn5gVolume.formatGb(0L, Locale.US))
        assertEquals("0.0", Vpn5gVolume.formatGb(-10L, Locale.US))
    }

    // v1.1.33: grant-independent session accumulation (real bytes
    // for every tier, free included).
    @Test
    fun test_accumulate_session() {
        assertEquals(0L, Vpn5gVolume.accumulateSession(0L, 0L))
        assertEquals(1_500L, Vpn5gVolume.accumulateSession(1_000L, 500L))
        // Negative inputs clamp to 0, never subtract.
        assertEquals(1_000L, Vpn5gVolume.accumulateSession(1_000L, -5L))
        assertEquals(500L, Vpn5gVolume.accumulateSession(-1_000L, 500L))
        // Saturates instead of wrapping.
        assertEquals(
            Long.MAX_VALUE,
            Vpn5gVolume.accumulateSession(Long.MAX_VALUE - 10L, 100L),
        )
    }
}
